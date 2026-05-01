package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.annotation.FormFile;
import io.github.huynhngochuyhoang.httpstarter.auth.AuthRequest;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import io.github.huynhngochuyhoang.httpstarter.filter.InboundHeadersWebFilter;
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
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import io.netty.handler.timeout.ReadTimeoutException;
import reactor.core.scheduler.Schedulers;
import reactor.netty.http.client.HttpClientRequest;
import reactor.netty.http.client.PrematureCloseException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

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
    private static final int MAX_LOGGER_CACHE_SIZE = 256;

    private final WebClient webClient;
    private final MethodMetadataCache metadataCache;
    private final RequestArgumentResolver argumentResolver;
    private final DefaultErrorDecoder errorDecoder;
    private final ReactiveHttpClientProperties.ClientConfig clientConfig;
    private final String clientName;
    private final ApplicationContext applicationContext;
    private final Map<Class<? extends HttpExchangeLogger>, HttpExchangeLogger> loggerCache = new ConcurrentHashMap<>();
    private final AtomicBoolean loggerCacheLimitWarningLogged = new AtomicBoolean(false);
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
        long requestBytes = measureRequestBodyBytes(resolved.body());

        AtomicLong start = new AtomicLong();
        AtomicBoolean firstAttempt = new AtomicBoolean(true);
        AtomicInteger attemptCount = new AtomicInteger(0);
        HttpExchangeLogger exchangeLogger = resolveExchangeLogger(meta);

        WebClient.RequestBodySpec requestSpec = webClient
                .method(HttpMethod.valueOf(meta.getHttpMethod()))
                .uri(uriBuilder -> {
                    var ub = uriBuilder.path(meta.getPathTemplate());
                    resolved.queryParams().forEach((k, values) ->
                            values.forEach(v -> ub.queryParam(k, String.valueOf(v))));
                    return ub.build(resolved.pathVars());
                });

        boolean hasAcceptHeader = resolved.headersIgnoreCase().containsKey(HttpHeaders.ACCEPT);
        String contentTypeHeader = resolved.headersIgnoreCase().get(HttpHeaders.CONTENT_TYPE);
        boolean hasContentTypeHeader = contentTypeHeader != null;
        if (!hasAcceptHeader) {
            requestSpec = requestSpec.accept(MediaType.APPLICATION_JSON);
        }
        WebClient.RequestBodySpec baseRequestSpec = requestSpec;

        long timeoutMs = resolveTimeoutMs(meta);

        final MultiValueMap<String, HttpEntity<?>> multipartBody = meta.isMultipart()
                ? buildMultipartBody(meta, args)
                : null;

        // Cache the serialized body so retries reuse the bytes without re-serializing.
        Mono<SerializedRequestBody> serializedBodyMono = serializeRequestBodyForAuth(resolved.body(), contentTypeHeader).cache();
        Mono<WebClient.RequestHeadersSpec<?>> requestHeadersSpecMono = serializedBodyMono
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
                    if (multipartBody != null) {
                        requestHeadersSpec = preparedRequestSpec.body(BodyInserters.fromMultipartData(multipartBody));
                    } else if (serializedRequestBody.originalBody() != null) {
                        WebClient.RequestBodySpec requestWithBodySpec = preparedRequestSpec;
                        if (!hasContentTypeHeader) {
                            requestWithBodySpec = requestWithBodySpec.contentType(MediaType.APPLICATION_JSON);
                        }
                        requestHeadersSpec = requestWithBodySpec.bodyValue(serializedRequestBody.bodyToWrite());
                    } else {
                        requestHeadersSpec = preparedRequestSpec;
                    }
                    // Apply when: (a) caller set an explicit @TimeoutMs (including 0 to disable), or (b) a resilience timeout resolved to > 0.
                    return (meta.getTimeoutMs() != MethodMetadata.TIMEOUT_NOT_SET || timeoutMs > 0)
                            ? applyRequestLevelResponseTimeout(requestHeadersSpec, timeoutMs)
                            : requestHeadersSpec;
                });

        AtomicReference<HttpStatusCode> responseStatus = new AtomicReference<>();
        AtomicReference<Map<String, List<String>>> responseHeaders = new AtomicReference<>(Map.of());
        AtomicReference<Throwable> terminalError = new AtomicReference<>();

        // Resolve observer once per invocation to avoid repeated volatile reads
        HttpClientObserver observer = getObserver();

        if (meta.isReturnsFlux()) {
            Flux<?> flux = exchange(requestHeadersSpecMono, responseStatus, responseHeaders,
                    response -> buildFlux(response, meta.getResponseType()))
                    .doOnSubscribe(subscription -> {
                attemptCount.incrementAndGet();
                start.compareAndSet(0L, System.currentTimeMillis());
                responseStatus.set(null);
                responseHeaders.set(Map.of());
                terminalError.set(null);
                if (exchangeLogger == null && firstAttempt.compareAndSet(true, false)) {
                    logRequest(meta, start.get());
                }
                    });
            flux = applyResilienceFlux(flux, meta);
            if (exchangeLogger != null || observer != null) {
                AtomicReference<Map<String, List<String>>> inboundHeadersRef = new AtomicReference<>(Map.of());
                AtomicBoolean reported = new AtomicBoolean(false);
                Flux<?> capturedFlux = flux;
                flux = Flux.deferContextual(ctx -> {
                    inboundHeadersRef.set(ctx.hasKey(InboundHeadersWebFilter.INBOUND_HEADERS_CONTEXT_KEY)
                            ? ctx.get(InboundHeadersWebFilter.INBOUND_HEADERS_CONTEXT_KEY)
                            : Map.of());
                    return capturedFlux;
                })
                .doOnError(terminalError::set)
                .doOnTerminate(() -> {
                    if (reported.compareAndSet(false, true))
                        reportExchange(exchangeLogger, observer, meta, resolved, start.get(),
                                responseStatus.get(), responseHeaders.get(), null, terminalError.get(), inboundHeadersRef.get(), attemptCount.get(), requestBytes);
                })
                .doOnCancel(() -> {
                    if (reported.compareAndSet(false, true))
                        reportExchange(exchangeLogger, observer, meta, resolved, start.get(),
                                responseStatus.get(), responseHeaders.get(), null, new CancellationException("Request was cancelled"), inboundHeadersRef.get(), attemptCount.get(), requestBytes);
                });
            }
            return flux;
        }

        AtomicReference<Object> terminalBody = new AtomicReference<>();
        Mono<?> mono = exchange(requestHeadersSpecMono, responseStatus, responseHeaders,
                response -> buildMono(response, meta.getResponseType()))
                .next()
                .doOnSubscribe(subscription -> {
            attemptCount.incrementAndGet();
            start.compareAndSet(0L, System.currentTimeMillis());
            responseStatus.set(null);
            responseHeaders.set(Map.of());
            terminalError.set(null);
            terminalBody.set(null);
            if (exchangeLogger == null && firstAttempt.compareAndSet(true, false)) {
                logRequest(meta, start.get());
            }
                });
        mono = applyResilienceMono(mono, meta);
        if (exchangeLogger != null || observer != null) {
            AtomicReference<Map<String, List<String>>> inboundHeadersRef = new AtomicReference<>(Map.of());
            AtomicBoolean reported = new AtomicBoolean(false);
            Mono<?> capturedMono = mono;
            mono = Mono.deferContextual(ctx -> {
                inboundHeadersRef.set(ctx.hasKey(InboundHeadersWebFilter.INBOUND_HEADERS_CONTEXT_KEY)
                        ? ctx.get(InboundHeadersWebFilter.INBOUND_HEADERS_CONTEXT_KEY)
                        : Map.of());
                return capturedMono;
            })
            .doOnSuccess(terminalBody::set)
            .doOnError(terminalError::set)
            .doOnTerminate(() -> {
                if (reported.compareAndSet(false, true))
                    reportExchange(exchangeLogger, observer, meta, resolved, start.get(),
                            responseStatus.get(), responseHeaders.get(), terminalBody.get(), terminalError.get(), inboundHeadersRef.get(), attemptCount.get(), requestBytes);
            })
            .doOnCancel(() -> {
                if (reported.compareAndSet(false, true))
                    reportExchange(exchangeLogger, observer, meta, resolved, start.get(),
                            responseStatus.get(), responseHeaders.get(), null, new CancellationException("Request was cancelled"), inboundHeadersRef.get(), attemptCount.get(), requestBytes);
            });
        }
        return mono;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private <T> Flux<T> exchange(
            Mono<WebClient.RequestHeadersSpec<?>> requestHeadersSpecMono,
            AtomicReference<HttpStatusCode> responseStatus,
            AtomicReference<Map<String, List<String>>> responseHeaders,
            Function<ClientResponse, Publisher<T>> successResponseHandler) {
        return requestHeadersSpecMono.flatMapMany(requestHeadersSpec -> requestHeadersSpec.exchangeToFlux(clientResponse -> {
            responseStatus.set(clientResponse.statusCode());
            responseHeaders.set(copyHeaders(clientResponse));

            if (clientResponse.statusCode().isError()) {
                return decodeErrorResponse(clientResponse).flatMapMany(Mono::error);
            }
            return Flux.from(successResponseHandler.apply(clientResponse));
        }));
    }

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
        if (responseType == DataBuffer.class) {
            return response.bodyToMono(DataBuffer.class);
        }
        // Streaming passthrough for Mono<ResponseEntity<Flux<DataBuffer>>>: skip the
        // in-memory codec entirely so large payloads aren't bound by codec-max-in-memory-size.
        if (isResponseEntityOfFluxDataBuffer(responseType)) {
            Flux<DataBuffer> streaming = response.bodyToFlux(DataBuffer.class);
            return Mono.just(ResponseEntity.status(response.statusCode())
                    .headers(response.headers().asHttpHeaders())
                    .body(streaming));
        }
        return response.bodyToMono(ParameterizedTypeReference.forType(responseType));
    }

    private Flux<?> buildFlux(ClientResponse response, Type responseType) {
        if (responseType == null) {
            return response.bodyToFlux(Object.class);
        }
        if (responseType == DataBuffer.class) {
            // Streaming passthrough: bodyToFlux(DataBuffer.class) wires the identity
            // DataBufferDecoder, so the codec-max-in-memory-size limit does not apply
            // — buffers are emitted as they arrive.
            return response.bodyToFlux(DataBuffer.class);
        }
        return response.bodyToFlux(ParameterizedTypeReference.forType(responseType));
    }

    /** {@code true} when {@code responseType} is exactly {@code ResponseEntity<Flux<DataBuffer>>}. */
    private static boolean isResponseEntityOfFluxDataBuffer(Type responseType) {
        if (!(responseType instanceof java.lang.reflect.ParameterizedType outer)) return false;
        if (!(outer.getRawType() instanceof Class<?> outerRaw)) return false;
        if (!ResponseEntity.class.equals(outerRaw)) return false;
        Type[] outerArgs = outer.getActualTypeArguments();
        if (outerArgs.length != 1) return false;
        if (!(outerArgs[0] instanceof java.lang.reflect.ParameterizedType inner)) return false;
        if (!(inner.getRawType() instanceof Class<?> innerRaw)) return false;
        if (!Flux.class.equals(innerRaw)) return false;
        Type[] innerArgs = inner.getActualTypeArguments();
        return innerArgs.length == 1 && DataBuffer.class.equals(innerArgs[0]);
    }

    private Mono<?> applyResilienceMono(Mono<?> mono, MethodMetadata meta) {
        ReactiveHttpClientProperties.ResilienceConfig resilience = clientConfig.getResilience();
        if (resilience == null || !resilience.isEnabled()) return mono;

        if (isRetryableMethod(meta.getHttpMethod())) {
            mono = applyRetryMono(mono, resolveResilienceInstanceName(meta.getRetryInstanceName(), resilience.getRetry()));
        }
        mono = applyCircuitBreakerMono(mono, resolveResilienceInstanceName(meta.getCircuitBreakerInstanceName(), resilience.getCircuitBreaker()));
        mono = applyBulkheadMono(mono, resolveResilienceInstanceName(meta.getBulkheadInstanceName(), resilience.getBulkhead()));
        return mono;
    }

    private Flux<?> applyResilienceFlux(Flux<?> flux, MethodMetadata meta) {
        ReactiveHttpClientProperties.ResilienceConfig resilience = clientConfig.getResilience();
        if (resilience == null || !resilience.isEnabled()) return flux;

        if (isRetryableMethod(meta.getHttpMethod())) {
            flux = applyRetryFlux(flux, resolveResilienceInstanceName(meta.getRetryInstanceName(), resilience.getRetry()));
        }
        flux = applyCircuitBreakerFlux(flux, resolveResilienceInstanceName(meta.getCircuitBreakerInstanceName(), resilience.getCircuitBreaker()));
        flux = applyBulkheadFlux(flux, resolveResilienceInstanceName(meta.getBulkheadInstanceName(), resilience.getBulkhead()));
        return flux;
    }

    /** Per-method override wins; otherwise the client-level config applies. */
    private static String resolveResilienceInstanceName(String methodLevel, String clientLevel) {
        return (methodLevel != null && !methodLevel.isBlank()) ? methodLevel : clientLevel;
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
        // Fast path: already resolved for this method on a previous invocation.
        HttpExchangeLogger perMethodCached = meta.getResolvedExchangeLogger();
        if (perMethodCached != null) {
            return perMethodCached != MethodMetadata.noopExchangeLogger() ? perMethodCached : null;
        }

        // Slow path: first resolution for this method.
        HttpExchangeLogger resolved;
        if (!meta.isHttpExchangeLoggingEnabled() || meta.getHttpExchangeLoggerClass() == null) {
            resolved = null;
        } else {
            Class<? extends HttpExchangeLogger> loggerClass = meta.getHttpExchangeLoggerClass();
            HttpExchangeLogger cached = loggerCache.get(loggerClass);
            if (cached != null) {
                resolved = cached;
            } else {
                HttpExchangeLogger created = instantiateExchangeLogger(loggerClass);
                if (loggerCache.size() >= MAX_LOGGER_CACHE_SIZE) {
                    if (loggerCacheLimitWarningLogged.compareAndSet(false, true)) {
                        log.warn("HttpExchangeLogger cache reached configured limit ({}). New logger classes will not be cached.",
                                MAX_LOGGER_CACHE_SIZE);
                    }
                    resolved = created;
                } else {
                    HttpExchangeLogger existing = loggerCache.putIfAbsent(loggerClass, created);
                    resolved = existing != null ? existing : created;
                }
            }
        }

        // Store on the method metadata so subsequent invocations skip the slow path.
        meta.setResolvedExchangeLogger(resolved != null ? resolved : MethodMetadata.noopExchangeLogger());
        return resolved;
    }

    private HttpExchangeLogger instantiateExchangeLogger(Class<? extends HttpExchangeLogger> loggerClass) {
        HttpExchangeLogger bean = applicationContext.getBeanProvider(loggerClass).getIfAvailable();
        if (bean != null) {
            return bean;
        }
        try {
            return loggerClass.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Cannot instantiate HttpExchangeLogger: " + loggerClass.getName(), e);
        }
    }

    private void reportExchange(
            HttpExchangeLogger exchangeLogger,
            HttpClientObserver observer,
            MethodMetadata meta,
            RequestArgumentResolver.ResolvedArgs resolved,
            long startMs,
            HttpStatusCode statusCode,
            Map<String, List<String>> responseHeaders,
            Object responseBody,
            Throwable error,
            Map<String, List<String>> inboundHeaders,
            int attemptCount,
            long requestBytes) {
        if (exchangeLogger != null) {
            logExchange(exchangeLogger, meta, resolved, startMs, statusCode, responseHeaders, responseBody, error, inboundHeaders);
        }
        if (observer != null) {
            long responseBytes = extractContentLengthBytes(responseHeaders);
            notifyObserver(observer, meta, resolved, startMs, statusCode, error, responseBody, attemptCount, requestBytes, responseBytes);
        }
    }

    private void logExchange(
            HttpExchangeLogger exchangeLogger,
            MethodMetadata meta,
            RequestArgumentResolver.ResolvedArgs resolved,
            long startMs,
            HttpStatusCode statusCode,
            Map<String, List<String>> responseHeaders,
            Object responseBody,
            Throwable error,
            Map<String, List<String>> inboundHeaders) {
        exchangeLogger.log(new HttpExchangeLogContext(
                clientName,
                meta.getHttpMethod(),
                meta.getPathTemplate(),
                Map.copyOf(resolved.pathVars()),
                copyQueryParams(resolved.queryParams()),
                inboundHeaders,
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
            Object responseBody,
            int attemptCount,
            long requestBytes,
            long responseBytes) {
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
                    logRespBody ? responseBody : null,
                    attemptCount,
                    requestBytes,
                    responseBytes
            ));
        } catch (Exception e) {
            log.warn("HttpClientObserver threw an exception – ignoring: {}", e.getMessage());
        }
    }

    /**
     * Builds a {@link MultiValueMap} of multipart parts from {@code @FormField} /
     * {@code @FormFile} parameter values. Unsupported value types fail fast with a
     * descriptive {@link IllegalArgumentException}; null values skip the part.
     */
    private static MultiValueMap<String, HttpEntity<?>> buildMultipartBody(MethodMetadata meta, Object[] args) {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();

        meta.getFormFieldParams().forEach((idx, name) -> {
            if (args == null || idx >= args.length) return;
            Object value = args[idx];
            if (value == null) return;
            if (value instanceof java.util.Collection<?> collection) {
                for (Object item : collection) {
                    if (item != null) builder.part(name, String.valueOf(item));
                }
            } else if (value.getClass().isArray()) {
                int len = java.lang.reflect.Array.getLength(value);
                for (int i = 0; i < len; i++) {
                    Object item = java.lang.reflect.Array.get(value, i);
                    if (item != null) builder.part(name, String.valueOf(item));
                }
            } else {
                builder.part(name, String.valueOf(value));
            }
        });

        meta.getFormFileParams().forEach((idx, annotation) -> {
            if (args == null || idx >= args.length) return;
            Object value = args[idx];
            if (value == null) return;
            addFilePart(builder, annotation, value);
        });

        return builder.build();
    }

    private static void addFilePart(MultipartBodyBuilder builder, FormFile annotation, Object value) {
        String name = annotation.value();
        String fallbackFilename = StringUtils.hasText(annotation.filename()) ? annotation.filename() : name;
        MediaType fallbackContentType = parseMediaTypeOrOctetStream(annotation.contentType());

        if (value instanceof Resource resource) {
            MultipartBodyBuilder.PartBuilder part = builder.part(name, resource, fallbackContentType);
            if (resource.getFilename() == null) {
                part.headers(h -> h.setContentDisposition(ContentDisposition.formData()
                        .name(name)
                        .filename(fallbackFilename)
                        .build()));
            }
            return;
        }
        if (value instanceof byte[] bytes) {
            ByteArrayResource resource = new ByteArrayResource(bytes) {
                @Override
                public String getFilename() {
                    return fallbackFilename;
                }
            };
            builder.part(name, resource, fallbackContentType);
            return;
        }
        if (value instanceof FileAttachment attachment) {
            byte[] content = attachment.content() != null ? attachment.content() : new byte[0];
            String filename = StringUtils.hasText(attachment.filename()) ? attachment.filename() : fallbackFilename;
            MediaType contentType = StringUtils.hasText(attachment.contentType())
                    ? parseMediaTypeOrOctetStream(attachment.contentType())
                    : fallbackContentType;
            ByteArrayResource resource = new ByteArrayResource(content) {
                @Override
                public String getFilename() {
                    return filename;
                }
            };
            builder.part(name, resource, contentType);
            return;
        }
        throw new IllegalArgumentException(
                "@FormFile parameter '" + name + "' must be byte[], Resource, or FileAttachment; got "
                        + value.getClass().getName());
    }

    private static MediaType parseMediaTypeOrOctetStream(String value) {
        if (!StringUtils.hasText(value)) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            return MediaType.parseMediaType(value);
        } catch (Exception ignored) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    /**
     * Best-effort request body size measurement. Returns the byte count for
     * {@code byte[]} and {@code String} bodies, {@code 0} for {@code null}, and
     * {@link HttpClientObserverEvent#UNKNOWN_SIZE} for arbitrary objects whose
     * serialised form isn't materialised synchronously on the invocation path.
     */
    private static long measureRequestBodyBytes(Object body) {
        if (body == null) {
            return 0L;
        }
        if (body instanceof byte[] bytes) {
            return bytes.length;
        }
        if (body instanceof CharSequence charSequence) {
            return charSequence.toString().getBytes(StandardCharsets.UTF_8).length;
        }
        return HttpClientObserverEvent.UNKNOWN_SIZE;
    }

    /**
     * Extracts the {@code Content-Length} header value from the captured response
     * headers. Returns {@link HttpClientObserverEvent#UNKNOWN_SIZE} if the header is
     * absent (e.g. chunked transfer encoding, empty body, network failure before
     * response).
     */
    private static long extractContentLengthBytes(Map<String, List<String>> responseHeaders) {
        if (responseHeaders == null || responseHeaders.isEmpty()) {
            return HttpClientObserverEvent.UNKNOWN_SIZE;
        }
        for (Map.Entry<String, List<String>> entry : responseHeaders.entrySet()) {
            if (HttpHeaders.CONTENT_LENGTH.equalsIgnoreCase(entry.getKey())) {
                List<String> values = entry.getValue();
                if (values == null || values.isEmpty()) {
                    return HttpClientObserverEvent.UNKNOWN_SIZE;
                }
                try {
                    long parsed = Long.parseLong(values.get(0).trim());
                    return parsed >= 0 ? parsed : HttpClientObserverEvent.UNKNOWN_SIZE;
                } catch (NumberFormatException ignored) {
                    return HttpClientObserverEvent.UNKNOWN_SIZE;
                }
            }
        }
        return HttpClientObserverEvent.UNKNOWN_SIZE;
    }

    private ErrorCategory resolveErrorCategory(HttpStatusCode statusCode, Throwable error) {
        if (error instanceof HttpClientException httpClientException) {
            return httpClientException.getErrorCategory();
        }
        if (error instanceof RemoteServiceException remoteServiceException) {
            return remoteServiceException.getErrorCategory();
        }
        if (error instanceof TimeoutException || error instanceof ReadTimeoutException
                || error instanceof PrematureCloseException) {
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
        int depth = 0;
        while (current != null && current.getCause() != null && current.getCause() != current && depth < 16) {
            current = current.getCause();
            depth++;
        }
        return current;
    }

    private record SerializedRequestBody(Object originalBody, Object bodyToWrite, byte[] rawBody) {}

}
