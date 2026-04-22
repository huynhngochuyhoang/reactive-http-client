package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.auth.AuthRequest;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import io.github.huynhngochuyhoang.httpstarter.exception.AuthProviderException;
import io.github.huynhngochuyhoang.httpstarter.exception.ErrorCategory;
import io.github.huynhngochuyhoang.httpstarter.exception.HttpClientException;
import io.github.huynhngochuyhoang.httpstarter.exception.RequestSerializationException;
import io.github.huynhngochuyhoang.httpstarter.exception.RemoteServiceException;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientObserver;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientObserverEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.codec.DecodingException;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;
import io.netty.handler.timeout.ReadTimeoutException;
import reactor.core.scheduler.Schedulers;
import reactor.netty.http.client.HttpClientRequest;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * JDK dynamic-proxy {@link InvocationHandler} that translates annotated interface
 * method calls into reactive WebClient requests.
 *
 * <p>The call pipeline is:
 * <ol>
 *   <li>Parse / retrieve cached {@link MethodMetadata}</li>
 *   <li>Resolve arguments via {@link RequestArgumentResolver}</li>
 *   <li>Build and execute a WebClient request</li>
 *   <li>Decode errors with {@link DefaultErrorDecoder}</li>
 *   <li>Optionally apply timeout + Resilience4j operators (retry, circuit-breaker, bulkhead)</li>
 * </ol>
 */
public class ReactiveClientInvocationHandler implements InvocationHandler {

    private static final Logger log = LoggerFactory.getLogger(ReactiveClientInvocationHandler.class);

    private final WebClient webClient;
    private final MethodMetadataCache metadataCache;
    private final RequestArgumentResolver argumentResolver;
    private final DefaultErrorDecoder errorDecoder;
    private final ReactiveHttpClientProperties.ClientConfig clientConfig;
    private final String clientName;
    private final ApplicationContext applicationContext;
    private final Map<Class<? extends HttpExchangeLogger>, HttpExchangeLogger> loggerCache = new ConcurrentHashMap<>();
    private final Set<String> resilienceWarningKeys = ConcurrentHashMap.newKeySet();

    private final ResilienceOperatorApplier resilienceOperatorApplier;
    private final ObjectMapper objectMapper;

    // Observability – resolved lazily on first request to avoid ordering issues during
    // context initialization (the observer bean may not yet exist when this handler is constructed)
    private final ReactiveHttpClientProperties.ObservabilityConfig observabilityConfig;
    private final org.springframework.beans.factory.ObjectProvider<HttpClientObserver> observerProvider;

    public ReactiveClientInvocationHandler(
            WebClient webClient,
            MethodMetadataCache metadataCache,
            RequestArgumentResolver argumentResolver,
            DefaultErrorDecoder errorDecoder,
            ReactiveHttpClientProperties.ClientConfig clientConfig,
            String clientName,
            ApplicationContext applicationContext,
            ResilienceOperatorApplier resilienceOperatorApplier,
            ObjectMapper objectMapper,
            ReactiveHttpClientProperties.ObservabilityConfig observabilityConfig) {
        this.webClient = webClient;
        this.metadataCache = metadataCache;
        this.argumentResolver = argumentResolver;
        this.errorDecoder = errorDecoder;
        this.clientConfig = Objects.requireNonNull(clientConfig, "clientConfig must not be null");
        this.clientName = clientName;
        this.applicationContext = applicationContext;
        this.resilienceOperatorApplier = resilienceOperatorApplier != null
                ? resilienceOperatorApplier
                : new NoopResilienceOperatorApplier();
        this.objectMapper = objectMapper;
        this.observerProvider = applicationContext.getBeanProvider(HttpClientObserver.class);
        this.observabilityConfig = observabilityConfig;
    }

    /**
     * Returns the {@link HttpClientObserver} to use for this handler.
     * The provider is queried for each invocation so late-registered observer beans
     * are still visible after this handler has been constructed.
     */
    private HttpClientObserver getObserver() {
        return observerProvider.getIfAvailable();
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (method.getDeclaringClass() == Object.class) {
            return switch (method.getName()) {
                case "equals" -> proxy == (args != null && args.length > 0 ? args[0] : null);
                case "hashCode" -> System.identityHashCode(proxy != null ? proxy : this);
                case "toString" -> "ReactiveHttpClientProxy(" + clientName + ")";
                default -> method.invoke(this, args);
            };
        }
        if (method.isDefault()) {
            return InvocationHandler.invokeDefault(proxy, method, args != null ? args : new Object[0]);
        }

        MethodMetadata meta = metadataCache.get(method);

        if (meta.getHttpMethod() == null) {
            throw new UnsupportedOperationException(
                    "Method " + method.getName() + " has no HTTP verb annotation (@GET, @POST, @PUT, @DELETE, @PATCH)");
        }

        RequestArgumentResolver.ResolvedArgs resolved = argumentResolver.resolve(meta, args);

        AtomicLong start = new AtomicLong();
        HttpExchangeLogger exchangeLogger = resolveExchangeLogger(meta);

        WebClient.RequestBodySpec requestSpec = webClient
                .method(HttpMethod.valueOf(meta.getHttpMethod()))
                .uri(uriBuilder -> {
                    var ub = uriBuilder.path(meta.getPathTemplate());
                    resolved.queryParams().forEach((k, values) ->
                            values.forEach(v -> ub.queryParam(k, String.valueOf(v))));
                    return ub.build(resolved.pathVars());
                });

        boolean hasAcceptHeader = hasHeaderIgnoreCase(resolved.headers(), HttpHeaders.ACCEPT);
        String contentTypeHeader = getHeaderIgnoreCase(resolved.headers(), HttpHeaders.CONTENT_TYPE);
        boolean hasContentTypeHeader = contentTypeHeader != null;
        if (!hasAcceptHeader) {
            requestSpec = requestSpec.accept(MediaType.APPLICATION_JSON);
        }
        WebClient.RequestBodySpec baseRequestSpec = requestSpec;

        long timeoutMs = resolveTimeoutMs(meta);
        Mono<WebClient.RequestHeadersSpec<?>> requestHeadersSpecMono = serializeRequestBodyForAuth(resolved.body(), contentTypeHeader)
                .map(serializedRequestBody -> {
                    WebClient.RequestBodySpec preparedRequestSpec = baseRequestSpec;
                    if (serializedRequestBody.originalBody() != null) {
                        preparedRequestSpec = preparedRequestSpec.attribute(AuthRequest.REQUEST_BODY_ATTRIBUTE, serializedRequestBody.originalBody());
                    }
                    if (serializedRequestBody.rawBody() != null) {
                        preparedRequestSpec = preparedRequestSpec.attribute(AuthRequest.REQUEST_RAW_BODY_ATTRIBUTE, serializedRequestBody.rawBody());
                    }

                    resolved.headers().forEach(preparedRequestSpec::header);

                    WebClient.RequestHeadersSpec<?> requestHeadersSpec;
                    if (serializedRequestBody.originalBody() != null) {
                        WebClient.RequestBodySpec requestWithBodySpec = preparedRequestSpec;
                        if (!hasContentTypeHeader) {
                            requestWithBodySpec = requestWithBodySpec.contentType(MediaType.APPLICATION_JSON);
                        }
                        requestHeadersSpec = requestWithBodySpec.bodyValue(serializedRequestBody.bodyToWrite());
                    } else {
                        requestHeadersSpec = preparedRequestSpec;
                    }
                    return applyRequestLevelResponseTimeout(requestHeadersSpec, timeoutMs);
                });

        AtomicReference<HttpStatusCode> responseStatus = new AtomicReference<>();
        AtomicReference<Map<String, List<String>>> responseHeaders = new AtomicReference<>(Map.of());
        AtomicReference<Throwable> terminalError = new AtomicReference<>();
        AtomicReference<Object> terminalBody = new AtomicReference<>();

        // Resolve observer once per invocation to avoid repeated volatile reads
        HttpClientObserver observer = getObserver();

        if (meta.isReturnsFlux()) {
            Flux<?> flux = requestHeadersSpecMono.flatMapMany(requestHeadersSpec -> requestHeadersSpec.exchangeToFlux(clientResponse -> {
                responseStatus.set(clientResponse.statusCode());
                responseHeaders.set(copyHeaders(clientResponse));

                if (clientResponse.statusCode().isError()) {
                    return decodeErrorResponse(clientResponse).flatMapMany(Mono::error);
                }
                return buildFlux(clientResponse, meta.getResponseType());
            })).doOnSubscribe(subscription -> {
                start.set(System.currentTimeMillis());
                responseStatus.set(null);
                responseHeaders.set(Map.of());
                terminalError.set(null);
                if (exchangeLogger == null) {
                    logRequest(meta, start.get());
                }
            });
            flux = applyResilienceFlux(flux, meta);
            if (exchangeLogger != null || observer != null) {
                flux = flux
                        .doOnError(terminalError::set)
                        .doFinally(signalType -> {
                            Throwable error = terminalErrorForSignal(signalType, terminalError.get());
                            if (exchangeLogger != null) {
                                logExchange(exchangeLogger, meta, resolved, start.get(),
                                        responseStatus.get(), responseHeaders.get(), null, error);
                            }
                            if (observer != null) {
                                notifyObserver(observer, meta, resolved, start.get(), responseStatus.get(), error, null);
                            }
                        });
            }
            return flux;
        }

        Mono<?> mono = requestHeadersSpecMono.flatMap(requestHeadersSpec -> requestHeadersSpec.exchangeToMono(clientResponse -> {
            responseStatus.set(clientResponse.statusCode());
            responseHeaders.set(copyHeaders(clientResponse));

            if (clientResponse.statusCode().isError()) {
                return decodeErrorResponse(clientResponse).flatMap(Mono::error);
            }
            return buildMono(clientResponse, meta.getResponseType());
        })).doOnSubscribe(subscription -> {
            start.set(System.currentTimeMillis());
            responseStatus.set(null);
            responseHeaders.set(Map.of());
            terminalError.set(null);
            terminalBody.set(null);
            if (exchangeLogger == null) {
                logRequest(meta, start.get());
            }
        });
        mono = applyResilienceMono(mono, meta);
        if (exchangeLogger != null || observer != null) {
            mono = mono
                    .doOnSuccess(terminalBody::set)
                    .doOnError(terminalError::set)
                    .doFinally(signalType -> {
                        Throwable error = terminalErrorForSignal(signalType, terminalError.get());
                        Object body = terminalBody.get();
                        if (exchangeLogger != null) {
                            logExchange(exchangeLogger, meta, resolved, start.get(),
                                    responseStatus.get(), responseHeaders.get(), body, error);
                        }
                        if (observer != null) {
                            notifyObserver(observer, meta, resolved, start.get(), responseStatus.get(), error, body);
                        }
                    });
        }
        return mono;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private Mono<?> buildMono(ClientResponse response, Type responseType) {
        if (responseType == null || Void.class.equals(responseType)) {
            return response.bodyToMono(Void.class);
        }
        if (responseType == String.class) {
            return response.bodyToMono(String.class);
        }
        if (responseType == byte[].class) {
            return response.bodyToMono(byte[].class);
        }
        return response.bodyToMono(ParameterizedTypeReference.forType(responseType));
    }

    private Flux<?> buildFlux(ClientResponse response, Type responseType) {
        if (responseType == null) {
            return response.bodyToFlux(Object.class);
        }
        return response.bodyToFlux(ParameterizedTypeReference.forType(responseType));
    }

    private Mono<?> applyResilienceMono(Mono<?> mono, MethodMetadata meta) {
        ReactiveHttpClientProperties.ResilienceConfig resilience = clientConfig.getResilience();
        if (resilience == null || !resilience.isEnabled()) return mono;

        if (isRetryableMethod(meta.getHttpMethod())) {
            mono = applyRetryMono(mono, resilience.getRetry());
        }
        mono = applyCircuitBreakerMono(mono, resilience.getCircuitBreaker());
        mono = applyBulkheadMono(mono, resilience.getBulkhead());
        return mono;
    }

    private Flux<?> applyResilienceFlux(Flux<?> flux, MethodMetadata meta) {
        ReactiveHttpClientProperties.ResilienceConfig resilience = clientConfig.getResilience();
        if (resilience == null || !resilience.isEnabled()) return flux;

        if (isRetryableMethod(meta.getHttpMethod())) {
            flux = applyRetryFlux(flux, resilience.getRetry());
        }
        flux = applyCircuitBreakerFlux(flux, resilience.getCircuitBreaker());
        flux = applyBulkheadFlux(flux, resilience.getBulkhead());
        return flux;
    }

    private long resolveTimeoutMs(MethodMetadata meta) {
        // Method-level override has highest priority.
        // A method annotation value of 0 explicitly disables timeout for that API method.
        if (meta.getTimeoutMs() != MethodMetadata.TIMEOUT_NOT_SET) {
            return meta.getTimeoutMs();
        }
        // Fall back to resilience timeout when method timeout is not configured.
        ReactiveHttpClientProperties.ResilienceConfig resilience = clientConfig.getResilience();
        if (resilience != null && resilience.isEnabled() && resilience.getTimeoutMs() > 0) {
            return resilience.getTimeoutMs();
        }
        return 0;
    }

    private WebClient.RequestHeadersSpec<?> applyRequestLevelResponseTimeout(
            WebClient.RequestHeadersSpec<?> requestHeadersSpec,
            long timeoutMs) {
        return requestHeadersSpec.httpRequest(httpRequest -> {
            Object nativeRequest = httpRequest.getNativeRequest();
            if (nativeRequest instanceof HttpClientRequest reactorRequest) {
                reactorRequest.responseTimeout(timeoutMs > 0 ? Duration.ofMillis(timeoutMs) : null);
            }
        });
    }

    private Mono<? extends Throwable> decodeErrorResponse(ClientResponse response) {
        return errorDecoder.decode(response)
                .onErrorResume(decodeError -> response.releaseBody()
                        .onErrorResume(releaseError -> Mono.empty())
                        .then(Mono.error(decodeError)));
    }

    @SuppressWarnings("unchecked")
    private Mono<?> applyCircuitBreakerMono(Mono<?> mono, String instanceName) {
        try {
            return resilienceOperatorApplier.applyCircuitBreaker((Mono<Object>) mono, instanceName);
        } catch (Exception e) {
            logResilienceOperatorFailure("circuitBreaker", instanceName, e);
            return mono;
        }
    }

    @SuppressWarnings("unchecked")
    private Flux<?> applyCircuitBreakerFlux(Flux<?> flux, String instanceName) {
        try {
            return resilienceOperatorApplier.applyCircuitBreaker((Flux<Object>) flux, instanceName);
        } catch (Exception e) {
            logResilienceOperatorFailure("circuitBreaker", instanceName, e);
            return flux;
        }
    }

    @SuppressWarnings("unchecked")
    private Mono<?> applyRetryMono(Mono<?> mono, String instanceName) {
        try {
            return resilienceOperatorApplier.applyRetry((Mono<Object>) mono, instanceName);
        } catch (Exception e) {
            logResilienceOperatorFailure("retry", instanceName, e);
            return mono;
        }
    }

    @SuppressWarnings("unchecked")
    private Flux<?> applyRetryFlux(Flux<?> flux, String instanceName) {
        try {
            return resilienceOperatorApplier.applyRetry((Flux<Object>) flux, instanceName);
        } catch (Exception e) {
            logResilienceOperatorFailure("retry", instanceName, e);
            return flux;
        }
    }

    @SuppressWarnings("unchecked")
    private Mono<?> applyBulkheadMono(Mono<?> mono, String instanceName) {
        try {
            return resilienceOperatorApplier.applyBulkhead((Mono<Object>) mono, instanceName);
        } catch (Exception e) {
            logResilienceOperatorFailure("bulkhead", instanceName, e);
            return mono;
        }
    }

    @SuppressWarnings("unchecked")
    private Flux<?> applyBulkheadFlux(Flux<?> flux, String instanceName) {
        try {
            return resilienceOperatorApplier.applyBulkhead((Flux<Object>) flux, instanceName);
        } catch (Exception e) {
            logResilienceOperatorFailure("bulkhead", instanceName, e);
            return flux;
        }
    }

    private boolean hasHeaderIgnoreCase(Map<String, String> headers, String headerName) {
        return getHeaderIgnoreCase(headers, headerName) != null;
    }

    private String getHeaderIgnoreCase(Map<String, String> headers, String headerName) {
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (headerName.equalsIgnoreCase(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private boolean isRetryableMethod(String method) {
        ReactiveHttpClientProperties.ResilienceConfig resilience = clientConfig.getResilience();
        if (resilience == null || resilience.getRetryMethods() == null || resilience.getRetryMethods().isEmpty()) {
            return false;
        }
        return method != null && resilience.getRetryMethods().contains(method.toUpperCase(Locale.ROOT));
    }

    private Mono<SerializedRequestBody> serializeRequestBodyForAuth(Object body, String contentTypeHeader) {
        if (body == null) {
            return Mono.just(new SerializedRequestBody(null, null, null));
        }
        if (!StringUtils.hasText(clientConfig.getAuthProvider())) {
            return Mono.just(new SerializedRequestBody(body, body, null));
        }
        if (body instanceof byte[] bytes) {
            return Mono.just(new SerializedRequestBody(body, bytes, bytes));
        }
        if (body instanceof String text) {
            return Mono.just(new SerializedRequestBody(body, text, text.getBytes(StandardCharsets.UTF_8)));
        }
        if (!shouldProvideJsonRawBody(contentTypeHeader) || objectMapper == null) {
            return Mono.just(new SerializedRequestBody(body, body, null));
        }
        return Mono.fromCallable(() -> {
                    byte[] json = objectMapper.writeValueAsBytes(body);
                    return new SerializedRequestBody(body, json, json);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorMap(JsonProcessingException.class, e -> new RequestSerializationException(clientName, e));
    }

    private boolean shouldProvideJsonRawBody(String contentTypeHeader) {
        if (contentTypeHeader == null || contentTypeHeader.isBlank()) {
            return true;
        }
        try {
            MediaType mediaType = MediaType.parseMediaType(contentTypeHeader);
            return MediaType.APPLICATION_JSON.isCompatibleWith(mediaType)
                    || mediaType.getSubtype().toLowerCase(Locale.ROOT).endsWith("+json");
        } catch (Exception ignored) {
            return false;
        }
    }

    private Throwable terminalErrorForSignal(SignalType signalType, Throwable terminalError) {
        if (terminalError != null) {
            return terminalError;
        }
        if (signalType == SignalType.CANCEL) {
            return new CancellationException("Request was cancelled");
        }
        return null;
    }

    private void logResilienceOperatorFailure(String operatorType, String instanceName, Exception error) {
        String key = operatorType + ":" + instanceName;
        if (resilienceWarningKeys.add(key)) {
            log.warn("Resilience4j {} operator could not be applied (instance='{}'). Requests will proceed without this protection. Cause: {}",
                    operatorType, instanceName, error.getMessage());
            return;
        }
        log.debug("Resilience4j {} operator not applied (instance='{}'): {}",
                operatorType, instanceName, error.getMessage());
    }

    private void logRequest(MethodMetadata meta, long startMs) {
        if (log.isDebugEnabled()) {
            log.debug("[{}] {} {} (resolved in {}ms)",
                    clientName, meta.getHttpMethod(), meta.getPathTemplate(),
                    System.currentTimeMillis() - startMs);
        }
    }

    private HttpExchangeLogger resolveExchangeLogger(MethodMetadata meta) {
        if (!meta.isHttpExchangeLoggingEnabled() || meta.getHttpExchangeLoggerClass() == null) {
            return null;
        }

        return loggerCache.computeIfAbsent(meta.getHttpExchangeLoggerClass(), clazz -> {
            HttpExchangeLogger bean = applicationContext.getBeanProvider(clazz).getIfAvailable();
            if (bean != null) {
                return bean;
            }
            try {
                return clazz.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                throw new IllegalStateException(
                        "Cannot instantiate HttpExchangeLogger: " + clazz.getName(), e);
            }
        });
    }

    private void logExchange(
            HttpExchangeLogger exchangeLogger,
            MethodMetadata meta,
            RequestArgumentResolver.ResolvedArgs resolved,
            long startMs,
            HttpStatusCode statusCode,
            Map<String, List<String>> responseHeaders,
            Object responseBody,
            Throwable error) {
        exchangeLogger.log(new HttpExchangeLogContext(
                clientName,
                meta.getHttpMethod(),
                meta.getPathTemplate(),
                Map.copyOf(resolved.pathVars()),
                copyQueryParams(resolved.queryParams()),
                Map.copyOf(resolved.headers()),
                resolved.body(),
                statusCode != null ? statusCode.value() : null,
                responseHeaders == null ? Map.of() : responseHeaders,
                responseBody,
                System.currentTimeMillis() - startMs,
                error
        ));
    }

    private Map<String, List<Object>> copyQueryParams(Map<String, List<Object>> source) {
        Map<String, List<Object>> copied = new LinkedHashMap<>();
        source.forEach((key, values) -> copied.put(key, List.copyOf(values)));
        return copied;
    }

    private Map<String, List<String>> copyHeaders(ClientResponse response) {
        Map<String, List<String>> copied = new LinkedHashMap<>();
        response.headers().asHttpHeaders().forEach((key, values) -> copied.put(key, List.copyOf(values)));
        return copied;
    }

    /**
     * Fires an {@link HttpClientObserverEvent} to the registered {@link HttpClientObserver}
     * (usually the Micrometer observer). Any exception thrown by the observer is swallowed
     * to ensure it never propagates to business logic.
     */
    private void notifyObserver(
            HttpClientObserver observer,
            MethodMetadata meta,
            RequestArgumentResolver.ResolvedArgs resolved,
            long startMs,
            HttpStatusCode statusCode,
            Throwable error,
            Object responseBody) {
        try {
            boolean logBody = observabilityConfig != null && observabilityConfig.isLogRequestBody();
            boolean logRespBody = observabilityConfig != null && observabilityConfig.isLogResponseBody();
            observer.record(new HttpClientObserverEvent(
                    clientName,
                    meta.getApiName(),
                    meta.getHttpMethod(),
                    meta.getPathTemplate(),
                    statusCode != null ? statusCode.value() : null,
                    System.currentTimeMillis() - startMs,
                    error,
                    resolveErrorCategory(statusCode, error),
                    logBody ? resolved.body() : null,
                    logRespBody ? responseBody : null
            ));
        } catch (Exception e) {
            log.warn("HttpClientObserver threw an exception – ignoring: {}", e.getMessage());
        }
    }

    private ErrorCategory resolveErrorCategory(HttpStatusCode statusCode, Throwable error) {
        if (error instanceof HttpClientException httpClientException) {
            return httpClientException.getErrorCategory();
        }
        if (error instanceof RemoteServiceException remoteServiceException) {
            return remoteServiceException.getErrorCategory();
        }
        if (error instanceof TimeoutException || error instanceof ReadTimeoutException) {
            return ErrorCategory.TIMEOUT;
        }
        if (error instanceof CancellationException) {
            return ErrorCategory.CANCELLED;
        }
        if (error instanceof AuthProviderException) {
            return ErrorCategory.AUTH_PROVIDER_ERROR;
        }
        Throwable rootCause = getRootCause(error);
        if (rootCause instanceof UnknownHostException) {
            return ErrorCategory.UNKNOWN_HOST;
        }
        if (rootCause instanceof ConnectException) {
            return ErrorCategory.CONNECT_ERROR;
        }
        if (isResponseDecodeError(statusCode, error)) {
            return ErrorCategory.RESPONSE_DECODE_ERROR;
        }
        if (statusCode != null) {
            int code = statusCode.value();
            if (code == 429) {
                return ErrorCategory.RATE_LIMITED;
            }
            if (code >= 400 && code < 500) {
                return ErrorCategory.CLIENT_ERROR;
            }
            if (code >= 500) {
                return ErrorCategory.SERVER_ERROR;
            }
        }
        return error != null ? ErrorCategory.UNKNOWN : null;
    }

    private boolean isResponseDecodeError(HttpStatusCode statusCode, Throwable error) {
        if (statusCode == null || statusCode.isError()) {
            return false;
        }
        Throwable current = error;
        while (current != null) {
            if (current instanceof DecodingException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private Throwable getRootCause(Throwable error) {
        Throwable current = error;
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        while (current != null && current.getCause() != null && visited.add(current)) {
            current = current.getCause();
        }
        return current;
    }

    private record SerializedRequestBody(Object originalBody, Object bodyToWrite, byte[] rawBody) {}

}
