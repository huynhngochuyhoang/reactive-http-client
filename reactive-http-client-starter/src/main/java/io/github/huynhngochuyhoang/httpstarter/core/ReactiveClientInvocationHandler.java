package io.github.huynhngochuyhoang.httpstarter.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.huynhngochuyhoang.httpstarter.annotation.FormFile;
import io.github.huynhngochuyhoang.httpstarter.annotation.LogHttpExchange;
import io.github.huynhngochuyhoang.httpstarter.auth.AuthRequest;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import io.github.huynhngochuyhoang.httpstarter.exception.*;
import io.github.huynhngochuyhoang.httpstarter.filter.InboundHeadersWebFilter;
import io.github.huynhngochuyhoang.httpstarter.observability.CompositeHttpClientObserver;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientObserver;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientObserverEvent;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.*;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.netty.http.client.HttpClientRequest;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
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
 *   <li>Optionally apply native request timeout, then Resilience4j operators
 *       (retry -> rate-limiter -> circuit-breaker -> bulkhead)</li>
 * </ol>
 */
public class ReactiveClientInvocationHandler implements InvocationHandler {

    private static final Logger log = LoggerFactory.getLogger(ReactiveClientInvocationHandler.class);
    static final String OBSERVED_REQUEST_URL_ATTRIBUTE =
            ReactiveClientInvocationHandler.class.getName() + ".observedRequestUrl";
    static final String RESILIENCE_OPERATOR_ORDER = "retry -> rate-limiter -> circuit-breaker -> bulkhead";
    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    private static final int MAX_LOGGER_CACHE_SIZE = 256;
    private static final int MAX_RESILIENCE_WARNING_KEYS = 256;

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
    private final Set<String> unsafeRetryWarningKeys = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean resilienceWarningKeysLimitWarningLogged = new AtomicBoolean(false);

    private final ResilienceOperatorApplier resilienceOperatorApplier;
    private final ObjectMapper objectMapper;

    // Observability – resolved lazily on first request to avoid ordering issues during
    // context initialization (the observer bean may not yet exist when this handler is constructed)
    private final ReactiveHttpClientProperties.ObservabilityConfig observabilityConfig;
    private final org.springframework.beans.factory.ObjectProvider<HttpClientObserver> observerProvider;
    private final org.springframework.beans.factory.ObjectProvider<ReactiveHttpClientLifecycleHook> lifecycleHookProvider;

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
        this.lifecycleHookProvider = applicationContext.getBeanProvider(ReactiveHttpClientLifecycleHook.class);
        this.observabilityConfig = observabilityConfig;
    }

    /**
     * Returns the {@link HttpClientObserver} to use for this handler.
     * The provider is queried for each invocation so late-registered observer beans
     * are still visible after this handler has been constructed.
     */
    private HttpClientObserver getObserver() {
        java.util.stream.Stream<HttpClientObserver> stream = observerProvider.orderedStream();
        if (stream == null) {
            return observerProvider.getIfAvailable();
        }
        List<HttpClientObserver> observers = stream.toList();
        if (observers.isEmpty()) {
            return observerProvider.getIfAvailable();
        }
        if (observers.size() == 1) {
            return observers.get(0);
        }
        return new CompositeHttpClientObserver(observers);
    }

    private List<ReactiveHttpClientLifecycleHook> getLifecycleHooks() {
        if (lifecycleHookProvider == null) {
            return List.of();
        }
        java.util.stream.Stream<ReactiveHttpClientLifecycleHook> stream = lifecycleHookProvider.orderedStream();
        if (stream == null) {
            return List.of();
        }
        return stream
                .filter(hook -> supportsLifecycleHook(hook, clientName))
                .toList();
    }

    private boolean supportsLifecycleHook(ReactiveHttpClientLifecycleHook hook, String clientName) {
        try {
            return hook.supports(clientName);
        } catch (Exception e) {
            log.warn("ReactiveHttpClientLifecycleHook [{}] supports() failed for client [{}] - skipping hook: {}",
                    hook.getClass().getName(), clientName, e.getMessage());
            return false;
        }
    }

    public static ExchangeFilterFunction requestUrlObservationFilter() {
        return (request, next) -> {
            request.attribute(OBSERVED_REQUEST_URL_ATTRIBUTE)
                    .filter(AtomicReference.class::isInstance)
                    .map(AtomicReference.class::cast)
                    .ifPresent(reference -> reference.set(request.url()));
            return next.exchange(request);
        };
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
        RequestPlan plan = meta.getRequestPlan();
        EffectiveApi effectiveApi = resolveEffectiveApi(plan);

        if (effectiveApi.httpMethod() == null) {
            throw new UnsupportedOperationException(
                    "Method " + method.getName() + " has no HTTP verb annotation (@GET, @POST, @PUT, @DELETE, @PATCH) or @ApiRef");
        }

        RequestArgumentResolver.ResolvedArgs resolved = applyDefaultHeaders(
                applyDefaultQueryParams(argumentResolver.resolve(plan, args)));
        long requestBytes = measureRequestBodyBytes(resolved.body());

        AtomicLong start = new AtomicLong();
        AtomicBoolean firstAttempt = new AtomicBoolean(true);
        AtomicInteger attemptCount = new AtomicInteger(0);
        AtomicReference<URI> requestUrl = new AtomicReference<>();
        HttpExchangeLogger exchangeLogger = resolveExchangeLogger(proxy, method, meta);

        WebClient.RequestBodySpec requestSpec = webClient
                .method(HttpMethod.valueOf(effectiveApi.httpMethod()))
                .uri(uriBuilder -> buildRequestUri(uriBuilder, effectiveApi.pathTemplate(), resolved));

        boolean hasAcceptHeader = resolved.headersIgnoreCase().containsKey(HttpHeaders.ACCEPT);
        String contentTypeHeader = resolved.headersIgnoreCase().get(HttpHeaders.CONTENT_TYPE);
        boolean hasContentTypeHeader = contentTypeHeader != null;
        if (!hasAcceptHeader) {
            requestSpec = requestSpec.accept(MediaType.APPLICATION_JSON);
        }
        WebClient.RequestBodySpec baseRequestSpec = requestSpec;

        long timeoutMs = resolveTimeoutMs(plan, effectiveApi.timeoutMs());

        final MultiValueMap<String, HttpEntity<?>> multipartBody = plan.multipart()
                ? buildMultipartBody(plan, args)
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
                    requestHeadersSpec = requestHeadersSpec.attribute(OBSERVED_REQUEST_URL_ATTRIBUTE, requestUrl);
                    // Apply when: (a) caller set an explicit @TimeoutMs (including 0 to disable), or (b) a resilience timeout resolved to > 0.
                    boolean shouldApplyResponseTimeout = plan.timeoutMs() != MethodMetadata.TIMEOUT_NOT_SET
                            || effectiveApi.timeoutMs() != MethodMetadata.TIMEOUT_NOT_SET
                            || isClientLevelRequestTimeoutConfigured()
                            || timeoutMs > 0;
                    return configureNativeRequest(requestHeadersSpec, timeoutMs, shouldApplyResponseTimeout, requestUrl);
                });

        AtomicReference<HttpStatusCode> responseStatus = new AtomicReference<>();
        AtomicReference<Map<String, List<String>>> responseHeaders = new AtomicReference<>(Map.of());
        AtomicReference<Throwable> terminalError = new AtomicReference<>();

        // Resolve observer once per invocation to avoid repeated volatile reads
        HttpClientObserver observer = getObserver();
        List<ReactiveHttpClientLifecycleHook> lifecycleHooks = getLifecycleHooks();

        if (plan.returnsFlux()) {
            Flux<?> flux = exchange(requestHeadersSpecMono, responseStatus, responseHeaders,
                    response -> buildFlux(response, plan.responseType()))
                    .doOnSubscribe(subscription -> {
                int attempt = attemptCount.incrementAndGet();
                start.compareAndSet(0L, System.currentTimeMillis());
                responseStatus.set(null);
                responseHeaders.set(Map.of());
                terminalError.set(null);
                notifyLifecycleAttempt(lifecycleHooks, plan, effectiveApi, resolved, requestUrl.get(),
                        responseStatus.get(), terminalError.get(), attempt);
                if (exchangeLogger == null && firstAttempt.compareAndSet(true, false)) {
                    logRequest(effectiveApi.httpMethod(), effectiveApi.pathTemplate(), start.get());
                }
                    });
            flux = applyResilienceFlux(flux, plan, effectiveApi.httpMethod(), resolved);
            if (exchangeLogger != null || observer != null || !lifecycleHooks.isEmpty()) {
                AtomicReference<Map<String, List<String>>> inboundHeadersRef = new AtomicReference<>(Map.of());
                AtomicBoolean reported = new AtomicBoolean(false);
                Flux<?> capturedFlux = flux;
                flux = Flux.deferContextual(ctx -> {
                    inboundHeadersRef.set(ctx.hasKey(InboundHeadersWebFilter.INBOUND_HEADERS_CONTEXT_KEY)
                            ? ctx.get(InboundHeadersWebFilter.INBOUND_HEADERS_CONTEXT_KEY)
                            : Map.of());
                    return capturedFlux;
                })
                .doOnComplete(() -> notifyLifecycleSuccess(lifecycleHooks, plan, effectiveApi, resolved, requestUrl.get(),
                        responseStatus.get(), attemptCount.get()))
                .doOnError(terminalError::set)
                .doOnError(error -> notifyLifecycleError(lifecycleHooks, plan, effectiveApi, resolved, requestUrl.get(),
                        responseStatus.get(), error, attemptCount.get()))
                .doOnTerminate(() -> {
                    if (reported.compareAndSet(false, true))
                        reportExchange(exchangeLogger, observer, plan, effectiveApi.httpMethod(), effectiveApi.pathTemplate(), resolved, requestUrl.get(), start.get(),
                                responseStatus.get(), responseHeaders.get(), null, terminalError.get(), inboundHeadersRef.get(), attemptCount.get(), requestBytes);
                })
                .doOnCancel(() -> {
                    CancellationException cancellation = new CancellationException("Request was cancelled");
                    notifyLifecycleCancel(lifecycleHooks, plan, effectiveApi, resolved, requestUrl.get(),
                            responseStatus.get(), cancellation, attemptCount.get());
                    if (reported.compareAndSet(false, true))
                        reportExchange(exchangeLogger, observer, plan, effectiveApi.httpMethod(), effectiveApi.pathTemplate(), resolved, requestUrl.get(), start.get(),
                                responseStatus.get(), responseHeaders.get(), null, cancellation, inboundHeadersRef.get(), attemptCount.get(), requestBytes);
                });
            }
            return flux;
        }

        AtomicReference<Object> terminalBody = new AtomicReference<>();
        Mono<?> mono = exchange(requestHeadersSpecMono, responseStatus, responseHeaders,
                response -> buildMono(response, plan.responseType()))
                .next()
                .doOnSubscribe(subscription -> {
            int attempt = attemptCount.incrementAndGet();
            start.compareAndSet(0L, System.currentTimeMillis());
            responseStatus.set(null);
            responseHeaders.set(Map.of());
            terminalError.set(null);
            terminalBody.set(null);
            notifyLifecycleAttempt(lifecycleHooks, plan, effectiveApi, resolved, requestUrl.get(),
                    responseStatus.get(), terminalError.get(), attempt);
            if (exchangeLogger == null && firstAttempt.compareAndSet(true, false)) {
                logRequest(effectiveApi.httpMethod(), effectiveApi.pathTemplate(), start.get());
            }
                });
        mono = applyResilienceMono(mono, plan, effectiveApi.httpMethod(), resolved);
        if (exchangeLogger != null || observer != null || !lifecycleHooks.isEmpty()) {
            AtomicReference<Map<String, List<String>>> inboundHeadersRef = new AtomicReference<>(Map.of());
            AtomicBoolean reported = new AtomicBoolean(false);
            Mono<?> capturedMono = mono;
            mono = Mono.deferContextual(ctx -> {
                inboundHeadersRef.set(ctx.hasKey(InboundHeadersWebFilter.INBOUND_HEADERS_CONTEXT_KEY)
                        ? ctx.get(InboundHeadersWebFilter.INBOUND_HEADERS_CONTEXT_KEY)
                        : Map.of());
                return capturedMono;
            })
            .doOnSuccess(body -> {
                terminalBody.set(body);
                notifyLifecycleSuccess(lifecycleHooks, plan, effectiveApi, resolved, requestUrl.get(),
                        responseStatus.get(), attemptCount.get());
            })
            .doOnError(terminalError::set)
            .doOnError(error -> notifyLifecycleError(lifecycleHooks, plan, effectiveApi, resolved, requestUrl.get(),
                    responseStatus.get(), error, attemptCount.get()))
            .doOnTerminate(() -> {
                if (reported.compareAndSet(false, true))
                    reportExchange(exchangeLogger, observer, plan, effectiveApi.httpMethod(), effectiveApi.pathTemplate(), resolved, requestUrl.get(), start.get(),
                            responseStatus.get(), responseHeaders.get(), terminalBody.get(), terminalError.get(), inboundHeadersRef.get(), attemptCount.get(), requestBytes);
            })
            .doOnCancel(() -> {
                CancellationException cancellation = new CancellationException("Request was cancelled");
                notifyLifecycleCancel(lifecycleHooks, plan, effectiveApi, resolved, requestUrl.get(),
                        responseStatus.get(), cancellation, attemptCount.get());
                if (reported.compareAndSet(false, true))
                    reportExchange(exchangeLogger, observer, plan, effectiveApi.httpMethod(), effectiveApi.pathTemplate(), resolved, requestUrl.get(), start.get(),
                            responseStatus.get(), responseHeaders.get(), null, cancellation, inboundHeadersRef.get(), attemptCount.get(), requestBytes);
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

    private URI buildRequestUri(
            UriBuilder uriBuilder,
            String pathTemplate,
            RequestArgumentResolver.ResolvedArgs resolved) {
        RequestUriTemplate requestUriTemplate = splitPathAndQuery(pathTemplate);
        UriBuilder builder = uriBuilder.path(requestUriTemplate.path());
        if (requestUriTemplate.query() != null) {
            builder = builder.query(requestUriTemplate.query());
        }
        UriBuilder requestBuilder = builder;
        resolved.queryParams().forEach((name, values) ->
                values.forEach(value -> requestBuilder.queryParam(name, String.valueOf(value))));
        return requestBuilder.build(resolved.pathVars());
    }

    private static RequestUriTemplate splitPathAndQuery(String pathTemplate) {
        if (pathTemplate == null) {
            return new RequestUriTemplate("", null);
        }
        int queryStart = pathTemplate.indexOf('?');
        if (queryStart < 0) {
            return new RequestUriTemplate(pathTemplate, null);
        }
        return new RequestUriTemplate(
                pathTemplate.substring(0, queryStart),
                pathTemplate.substring(queryStart + 1));
    }

    private Mono<?> buildMono(ClientResponse response, Type responseType) {
        // Streaming passthrough for Mono<ResponseEntity<Flux<DataBuffer>>>: skip the
        // in-memory codec entirely so large payloads aren't bound by codec-max-in-memory-size.
        if (isResponseEntityOfFluxDataBuffer(responseType)) {
            Flux<DataBuffer> streaming = response.bodyToFlux(DataBuffer.class);
            return Mono.just(ResponseEntity.status(response.statusCode())
                    .headers(response.headers().asHttpHeaders())
                    .body(streaming));
        }

        Type responseEntityBodyType = responseEntityBodyType(responseType);
        if (responseEntityBodyType != null) {
            return buildResponseEntityMono(response, responseEntityBodyType);
        }
        return bodyToMono(response, responseType);
    }

    private Mono<?> buildResponseEntityMono(ClientResponse response, Type bodyType) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(response.statusCode())
                .headers(response.headers().asHttpHeaders());
        if (Void.class.equals(bodyType) || void.class.equals(bodyType)) {
            return response.releaseBody().thenReturn(builder.build());
        }
        return bodyToMono(response, bodyType)
                .map(body -> builder.body(body))
                .switchIfEmpty(Mono.fromSupplier(builder::build));
    }

    private Mono<?> bodyToMono(ClientResponse response, Type responseType) {
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

    /** Returns the body type when {@code responseType} is {@code ResponseEntity<T>}. */
    private static Type responseEntityBodyType(Type responseType) {
        if (!(responseType instanceof java.lang.reflect.ParameterizedType outer)) return null;
        if (!(outer.getRawType() instanceof Class<?> outerRaw)) return null;
        if (!ResponseEntity.class.equals(outerRaw)) return null;
        Type[] outerArgs = outer.getActualTypeArguments();
        return outerArgs.length == 1 ? outerArgs[0] : null;
    }

    /** {@code true} when {@code responseType} is exactly {@code ResponseEntity<Flux<DataBuffer>>}. */
    private static boolean isResponseEntityOfFluxDataBuffer(Type responseType) {
        Type bodyType = responseEntityBodyType(responseType);
        if (!(bodyType instanceof java.lang.reflect.ParameterizedType inner)) return false;
        if (!(inner.getRawType() instanceof Class<?> innerRaw)) return false;
        if (!Flux.class.equals(innerRaw)) return false;
        Type[] innerArgs = inner.getActualTypeArguments();
        return innerArgs.length == 1 && DataBuffer.class.equals(innerArgs[0]);
    }

    private Mono<?> applyResilienceMono(Mono<?> mono,
                                         RequestPlan plan,
                                         String httpMethod,
                                         RequestArgumentResolver.ResolvedArgs resolved) {
        ReactiveHttpClientProperties.ResilienceConfig resilience = clientConfig.getResilience();
        if (resilience == null || !resilience.isEnabled()) return mono;

        if (isRetryableMethod(httpMethod)) {
            String retryInstance = resolveResilienceInstanceName(plan.retryInstanceName(), resilience.getRetry());
            logUnsafeRetryIfNeeded(plan, resilience, httpMethod, resolved, retryInstance);
            mono = applyRetryMono(mono, retryInstance);
        }
        mono = applyRateLimiterMono(mono, resolveResilienceInstanceName(plan.rateLimiterInstanceName(), resilience.getRateLimiter()));
        mono = applyCircuitBreakerMono(mono, resolveResilienceInstanceName(plan.circuitBreakerInstanceName(), resilience.getCircuitBreaker()));
        mono = applyBulkheadMono(mono, resolveResilienceInstanceName(plan.bulkheadInstanceName(), resilience.getBulkhead()));
        return mono;
    }

    private Flux<?> applyResilienceFlux(Flux<?> flux,
                                        RequestPlan plan,
                                        String httpMethod,
                                        RequestArgumentResolver.ResolvedArgs resolved) {
        ReactiveHttpClientProperties.ResilienceConfig resilience = clientConfig.getResilience();
        if (resilience == null || !resilience.isEnabled()) return flux;

        if (isRetryableMethod(httpMethod)) {
            String retryInstance = resolveResilienceInstanceName(plan.retryInstanceName(), resilience.getRetry());
            logUnsafeRetryIfNeeded(plan, resilience, httpMethod, resolved, retryInstance);
            flux = applyRetryFlux(flux, retryInstance);
        }
        flux = applyRateLimiterFlux(flux, resolveResilienceInstanceName(plan.rateLimiterInstanceName(), resilience.getRateLimiter()));
        flux = applyCircuitBreakerFlux(flux, resolveResilienceInstanceName(plan.circuitBreakerInstanceName(), resilience.getCircuitBreaker()));
        flux = applyBulkheadFlux(flux, resolveResilienceInstanceName(plan.bulkheadInstanceName(), resilience.getBulkhead()));
        return flux;
    }

    /** Per-method override wins; otherwise the client-level config applies. */
    private static String resolveResilienceInstanceName(String methodLevel, String clientLevel) {
        return (methodLevel != null && !methodLevel.isBlank()) ? methodLevel : clientLevel;
    }

    private void logUnsafeRetryIfNeeded(RequestPlan plan,
                                        ReactiveHttpClientProperties.ResilienceConfig resilience,
                                        String httpMethod,
                                        RequestArgumentResolver.ResolvedArgs resolved,
                                        String retryInstance) {
        if (effectiveRetrySafety(plan, httpMethod, resolved) != RetrySafetyClassification.UNSAFE_RETRY) {
            return;
        }
        String methodName = plan.method() != null
                ? plan.method().getDeclaringClass().getSimpleName() + "#" + plan.method().getName()
                : plan.apiName();
        String warningKey = clientName + ":" + methodName + ":" + httpMethod + ":" + retryInstance;
        if (unsafeRetryWarningKeys.add(warningKey)) {
            log.warn("Unsafe retry configured for reactive HTTP client [{}] method [{}] HTTP [{}]: "
                            + "retry instance [{}] from [{}] is enabled for retry-methods {} without an explicit {} header. "
                            + "Existing behavior is preserved; add an idempotency key or remove this method from retry-methods to avoid duplicate side effects.",
                    clientName,
                    methodName,
                    httpMethod,
                    retryInstance,
                    plan.retryInstanceName() != null ? "method-level @Retry" : "client resilience.retry",
                    resilience.getRetryMethods(),
                    IDEMPOTENCY_KEY_HEADER);
        }
    }

    private RetrySafetyClassification effectiveRetrySafety(RequestPlan plan,
                                                            String httpMethod,
                                                            RequestArgumentResolver.ResolvedArgs resolved) {
        if (isSafeRetryMethod(httpMethod) || plan.retrySafety() == RetrySafetyClassification.SAFE_METHOD) {
            return RetrySafetyClassification.SAFE_METHOD;
        }
        if (hasIdempotencyKeyHeaderValue(resolved.headersIgnoreCase())) {
            return RetrySafetyClassification.EXPLICIT_IDEMPOTENCY_KEY;
        }
        return RetrySafetyClassification.UNSAFE_RETRY;
    }

    private static boolean hasIdempotencyKeyHeaderValue(Map<String, String> headersIgnoreCase) {
        String value = headersIgnoreCase.get(IDEMPOTENCY_KEY_HEADER);
        return StringUtils.hasText(value);
    }

    static boolean isSafeRetryMethod(String httpMethod) {
        return httpMethod != null
                && Set.of("GET", "HEAD", "PUT", "DELETE", "OPTIONS", "TRACE")
                .contains(httpMethod.toUpperCase(Locale.ROOT));
    }

    private long resolveTimeoutMs(MethodMetadata meta) {
        return resolveTimeoutMs(meta, MethodMetadata.TIMEOUT_NOT_SET);
    }

    private long resolveTimeoutMs(MethodMetadata meta, long configuredApiTimeoutMs) {
        return resolveTimeoutMs(meta.getRequestPlan() != null ? meta.getRequestPlan() : RequestPlan.from(meta), configuredApiTimeoutMs);
    }

    private long resolveTimeoutMs(RequestPlan plan, long configuredApiTimeoutMs) {
        // Method-level override has highest priority.
        // A method annotation value of 0 explicitly disables timeout for that API method.
        if (plan.timeoutMs() != MethodMetadata.TIMEOUT_NOT_SET) {
            return plan.timeoutMs();
        }
        // API-map timeout (via @ApiRef) has second priority.
        if (configuredApiTimeoutMs != MethodMetadata.TIMEOUT_NOT_SET) {
            return configuredApiTimeoutMs;
        }
        // Canonical client-level timeout wins over the deprecated resilience alias.
        if (clientConfig.isRequestTimeoutMsConfigured()) {
            return clientConfig.getRequestTimeoutMs();
        }
        ReactiveHttpClientProperties.ResilienceConfig resilience = clientConfig.getResilience();
        if (resilience != null && resilience.isTimeoutMsConfigured()) {
            return resilience.getTimeoutMs();
        }
        return 0;
    }

    private boolean isClientLevelRequestTimeoutConfigured() {
        if (clientConfig.isRequestTimeoutMsConfigured()) {
            return true;
        }
        ReactiveHttpClientProperties.ResilienceConfig resilience = clientConfig.getResilience();
        return resilience != null && resilience.isTimeoutMsConfigured();
    }

    private EffectiveApi resolveEffectiveApi(Method method, MethodMetadata meta) {
        return resolveEffectiveApi(meta.getRequestPlan() != null ? meta.getRequestPlan() : RequestPlan.from(meta));
    }

    private EffectiveApi resolveEffectiveApi(RequestPlan plan) {
        if (plan.apiRefName() == null) {
            return plan.staticEffectiveApi();
        }

        ReactiveHttpClientProperties.ApiConfig apiConfig = clientConfig.getApis() != null
                ? clientConfig.getApis().get(plan.apiRefName())
                : null;
        String configPrefix = ApiRefValidationSupport.configPrefix(clientName, plan.apiRefName());
        String apiRefContext = ApiRefValidationSupport.apiRefContext(plan.method(), plan.apiRefName());
        ReactiveHttpClientFactoryBean.validateApiRef(apiConfig, configPrefix, apiRefContext);
        long configuredTimeoutMs = apiConfig.getTimeoutMs();

        return new EffectiveApi(
                apiConfig.getMethod().toUpperCase(Locale.ROOT),
                apiConfig.getPath(),
                configuredTimeoutMs);
    }

    private WebClient.RequestHeadersSpec<?> configureNativeRequest(
            WebClient.RequestHeadersSpec<?> requestHeadersSpec,
            long timeoutMs,
            boolean shouldApplyResponseTimeout,
            AtomicReference<URI> requestUrl) {
        return requestHeadersSpec.httpRequest(httpRequest -> {
            requestUrl.set(httpRequest.getURI());
            Object nativeRequest = httpRequest.getNativeRequest();
            if (shouldApplyResponseTimeout && nativeRequest instanceof HttpClientRequest reactorRequest) {
                reactorRequest.responseTimeout(timeoutMs > 0 ? Duration.ofMillis(timeoutMs) : null);
            }
        });
    }

    @SuppressWarnings("unchecked")
    private Mono<? extends Throwable> decodeErrorResponse(ClientResponse response) {
        int statusCode = response.statusCode().value();
        // Cast to Mono<Throwable> to avoid wildcard capture problems with onErrorResume.
        Mono<Throwable> decoded = (Mono<Throwable>) errorDecoder.decode(response);
        return decoded.onErrorResume(decodeError -> buildFallbackException(statusCode, decodeError, response));
    }

    /**
     * Builds a fallback domain exception when error-body decoding itself fails.
     * The original HTTP status is preserved so callers always see the correct error category.
     * The decoding failure is attached as the cause so operators can distinguish
     * "502 with unreadable body" from a clean 502 response.
     *
     * <p>The response body is released within the reactive chain so that cleanup participates
     * in the same backpressure/cancellation scope as the caller — no unmanaged subscriptions.
     */
    private Mono<Throwable> buildFallbackException(int statusCode, Throwable decodeError, ClientResponse response) {
        Throwable wrapped;
        if (statusCode >= 400 && statusCode < 500) {
            wrapped = new HttpClientException(statusCode, "", null, null, decodeError);
        } else {
            wrapped = new RemoteServiceException(statusCode, "", null, null, decodeError);
        }
        Throwable finalWrapped = wrapped;
        return response.releaseBody()
                .onErrorResume(releaseError -> Mono.empty())
                .thenReturn(finalWrapped);
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
    private Mono<?> applyRateLimiterMono(Mono<?> mono, String instanceName) {
        try {
            return resilienceOperatorApplier.applyRateLimiter((Mono<Object>) mono, instanceName);
        } catch (Exception e) {
            logResilienceOperatorFailure("rateLimiter", instanceName, e);
            return mono;
        }
    }

    @SuppressWarnings("unchecked")
    private Flux<?> applyRateLimiterFlux(Flux<?> flux, String instanceName) {
        try {
            return resilienceOperatorApplier.applyRateLimiter((Flux<Object>) flux, instanceName);
        } catch (Exception e) {
            logResilienceOperatorFailure("rateLimiter", instanceName, e);
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
        if (!clientConfig.hasAuthConfigured()) {
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
        // Guard the set against unbounded growth when dynamic instance names are used (e.g. per-tenant).
        // Once the cap is reached, log a one-time overflow warning and stop tracking new keys.
        if (resilienceWarningKeys.size() >= MAX_RESILIENCE_WARNING_KEYS) {
            if (resilienceWarningKeysLimitWarningLogged.compareAndSet(false, true)) {
                log.warn("Resilience4j warning-key set reached configured limit ({}). "
                        + "Subsequent resilience operator failures will be logged at DEBUG only.",
                        MAX_RESILIENCE_WARNING_KEYS);
            }
            log.debug("Resilience4j {} operator not applied (instance='{}'): {}",
                    operatorType, instanceName, error.getMessage());
            return;
        }
        if (resilienceWarningKeys.add(key)) {
            log.warn("Resilience4j {} operator could not be applied (instance='{}'). Requests will proceed without this protection. Cause: {}",
                    operatorType, instanceName, error.getMessage());
            return;
        }
        log.debug("Resilience4j {} operator not applied (instance='{}'): {}",
                operatorType, instanceName, error.getMessage());
    }

    private void notifyLifecycleAttempt(
            List<ReactiveHttpClientLifecycleHook> hooks,
            RequestPlan plan,
            EffectiveApi effectiveApi,
            RequestArgumentResolver.ResolvedArgs resolved,
            URI requestUrl,
            HttpStatusCode statusCode,
            Throwable error,
            int attemptNumber) {
        if (hooks.isEmpty()) {
            return;
        }
        ReactiveHttpClientLifecycleContext context = lifecycleContext(
                plan, effectiveApi, resolved, requestUrl, statusCode, error, attemptNumber);
        if (attemptNumber <= 1) {
            invokeLifecycleHooks(hooks, "onStart", hook -> hook.onStart(context));
        } else {
            invokeLifecycleHooks(hooks, "onRetryAttempt", hook -> hook.onRetryAttempt(context));
        }
    }

    private void notifyLifecycleSuccess(
            List<ReactiveHttpClientLifecycleHook> hooks,
            RequestPlan plan,
            EffectiveApi effectiveApi,
            RequestArgumentResolver.ResolvedArgs resolved,
            URI requestUrl,
            HttpStatusCode statusCode,
            int attemptNumber) {
        if (hooks.isEmpty()) {
            return;
        }
        ReactiveHttpClientLifecycleContext context = lifecycleContext(
                plan, effectiveApi, resolved, requestUrl, statusCode, null, attemptNumber);
        invokeLifecycleHooks(hooks, "onSuccess", hook -> hook.onSuccess(context));
    }

    private void notifyLifecycleError(
            List<ReactiveHttpClientLifecycleHook> hooks,
            RequestPlan plan,
            EffectiveApi effectiveApi,
            RequestArgumentResolver.ResolvedArgs resolved,
            URI requestUrl,
            HttpStatusCode statusCode,
            Throwable error,
            int attemptNumber) {
        if (hooks.isEmpty()) {
            return;
        }
        ReactiveHttpClientLifecycleContext context = lifecycleContext(
                plan, effectiveApi, resolved, requestUrl, statusCode, error, attemptNumber);
        invokeLifecycleHooks(hooks, "onError", hook -> hook.onError(context));
    }

    private void notifyLifecycleCancel(
            List<ReactiveHttpClientLifecycleHook> hooks,
            RequestPlan plan,
            EffectiveApi effectiveApi,
            RequestArgumentResolver.ResolvedArgs resolved,
            URI requestUrl,
            HttpStatusCode statusCode,
            Throwable error,
            int attemptNumber) {
        if (hooks.isEmpty()) {
            return;
        }
        ReactiveHttpClientLifecycleContext context = lifecycleContext(
                plan, effectiveApi, resolved, requestUrl, statusCode, error, attemptNumber);
        invokeLifecycleHooks(hooks, "onCancel", hook -> hook.onCancel(context));
    }

    private ReactiveHttpClientLifecycleContext lifecycleContext(
            RequestPlan plan,
            EffectiveApi effectiveApi,
            RequestArgumentResolver.ResolvedArgs resolved,
            URI requestUrl,
            HttpStatusCode statusCode,
            Throwable error,
            int attemptNumber) {
        return ReactiveHttpClientLifecycleContext.from(
                clientName,
                plan,
                effectiveApi.httpMethod(),
                effectiveApi.pathTemplate(),
                resolved,
                requestUrl,
                statusCode != null ? statusCode.value() : null,
                error,
                attemptNumber);
    }

    private void invokeLifecycleHooks(
            List<ReactiveHttpClientLifecycleHook> hooks,
            String callbackName,
            java.util.function.Consumer<ReactiveHttpClientLifecycleHook> callback) {
        for (ReactiveHttpClientLifecycleHook hook : hooks) {
            try {
                callback.accept(hook);
            } catch (Exception e) {
                log.warn("ReactiveHttpClientLifecycleHook [{}] {} failed for client [{}] - ignoring: {}",
                        hook.getClass().getName(), callbackName, clientName, e.getMessage());
            }
        }
    }

    private void logRequest(String httpMethod, String pathTemplate, long startMs) {
        if (log.isDebugEnabled()) {
            log.debug("[{}] {} {} (resolved in {}ms)",
                    clientName, httpMethod, pathTemplate,
                    System.currentTimeMillis() - startMs);
        }
    }

    private HttpExchangeLogger resolveExchangeLogger(Object proxy, Method method, MethodMetadata meta) {
        // Method-level annotation remains the highest precedence and can be safely
        // cached on MethodMetadata because it's tied to the Method itself.
        if (meta.isHttpExchangeLoggingEnabled() && meta.getHttpExchangeLoggerClass() != null) {
            HttpExchangeLogger perMethodCached = meta.getResolvedExchangeLogger();
            if (perMethodCached != null) {
                return perMethodCached != MethodMetadata.noopExchangeLogger() ? perMethodCached : null;
            }
            HttpExchangeLogger resolved = getOrCreateExchangeLogger(meta.getHttpExchangeLoggerClass());
            meta.setResolvedExchangeLogger(resolved != null ? resolved : MethodMetadata.noopExchangeLogger());
            return resolved;
        }

        LogHttpExchange interfaceLevelAnnotation = resolveInterfaceLevelLogAnnotation(proxy, method);
        if (interfaceLevelAnnotation != null) {
            return getOrCreateExchangeLogger(interfaceLevelAnnotation.logger());
        }
        if (clientConfig.isExchangeLoggingEnabled()) {
            return getOrCreateExchangeLogger(DefaultHttpExchangeLogger.class);
        }
        return null;
    }

    private LogHttpExchange resolveInterfaceLevelLogAnnotation(Object proxy, Method method) {
        if (proxy != null) {
            Class<?> declaringInterface = method.getDeclaringClass();
            for (Class<?> candidate : proxy.getClass().getInterfaces()) {
                if (declaringInterface.isAssignableFrom(candidate)) {
                    LogHttpExchange annotation = candidate.getAnnotation(LogHttpExchange.class);
                    if (annotation != null) {
                        return annotation;
                    }
                }
            }
        }
        // General fallback to the declaring interface annotation; this also covers
        // direct handler unit tests where `proxy` is intentionally null.
        return method.getDeclaringClass().getAnnotation(LogHttpExchange.class);
    }

    private HttpExchangeLogger getOrCreateExchangeLogger(Class<? extends HttpExchangeLogger> loggerClass) {
        HttpExchangeLogger cached = loggerCache.get(loggerClass);
        if (cached != null) {
            return cached;
        }
        HttpExchangeLogger created = instantiateExchangeLogger(loggerClass);
        if (loggerCache.size() >= MAX_LOGGER_CACHE_SIZE) {
            if (loggerCacheLimitWarningLogged.compareAndSet(false, true)) {
                log.warn("HttpExchangeLogger cache reached configured limit ({}). New logger classes will not be cached.",
                        MAX_LOGGER_CACHE_SIZE);
            }
            return created;
        }
        HttpExchangeLogger existing = loggerCache.putIfAbsent(loggerClass, created);
        return existing != null ? existing : created;
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
            RequestPlan plan,
            String httpMethod,
            String pathTemplate,
            RequestArgumentResolver.ResolvedArgs resolved,
            URI requestUrl,
            long startMs,
            HttpStatusCode statusCode,
            Map<String, List<String>> responseHeaders,
            Object responseBody,
            Throwable error,
            Map<String, List<String>> inboundHeaders,
            int attemptCount,
            long requestBytes) {
        if (exchangeLogger != null) {
            logExchange(exchangeLogger, httpMethod, pathTemplate, resolved, startMs, statusCode, responseHeaders, responseBody, error, inboundHeaders);
        }
        if (observer != null) {
            long responseBytes = extractContentLengthBytes(responseHeaders);
            notifyObserver(observer, plan, httpMethod, pathTemplate, resolved, requestUrl, startMs, statusCode, error, responseBody, attemptCount, requestBytes, responseBytes);
        }
    }

    private void logExchange(
            HttpExchangeLogger exchangeLogger,
            String httpMethod,
            String pathTemplate,
            RequestArgumentResolver.ResolvedArgs resolved,
            long startMs,
            HttpStatusCode statusCode,
            Map<String, List<String>> responseHeaders,
            Object responseBody,
            Throwable error,
            Map<String, List<String>> inboundHeaders) {
        exchangeLogger.log(new HttpExchangeLogContext(
                clientName,
                httpMethod,
                pathTemplate,
                Map.copyOf(resolved.pathVars()),
                copyQueryParams(resolved.queryParams()),
                inboundHeaders,
                Map.copyOf(resolved.headers()),
                resolved.body(),
                statusCode != null ? statusCode.value() : null,
                responseHeaders == null ? Map.of() : responseHeaders,
                responseBody,
                System.currentTimeMillis() - startMs,
                error,
                clientConfig.getLogPreset()
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
            RequestPlan plan,
            String httpMethod,
            String pathTemplate,
            RequestArgumentResolver.ResolvedArgs resolved,
            URI requestUrl,
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
                    plan.apiName(),
                    httpMethod,
                    pathTemplate,
                    statusCode != null ? statusCode.value() : null,
                    System.currentTimeMillis() - startMs,
                    error,
                    resolveErrorCategory(statusCode, error),
                    logBody ? resolved.body() : null,
                    logRespBody ? responseBody : null,
                    attemptCount,
                    requestBytes,
                    responseBytes,
                    requestUrl != null ? requestUrl.getHost() : null,
                    resolveServerPort(requestUrl)
            ));
        } catch (Exception e) {
            log.warn("HttpClientObserver threw an exception – ignoring: {}", e.getMessage());
        }
    }

    private Integer resolveServerPort(URI requestUrl) {
        if (requestUrl == null || requestUrl.getHost() == null) {
            return null;
        }
        if (requestUrl.getPort() >= 0) {
            return requestUrl.getPort();
        }
        String scheme = requestUrl.getScheme();
        if ("http".equalsIgnoreCase(scheme)) {
            return 80;
        }
        if ("https".equalsIgnoreCase(scheme)) {
            return 443;
        }
        return null;
    }

    /**
     * Builds a {@link MultiValueMap} of multipart parts from {@code @FormField} /
     * {@code @FormFile} parameter values. Unsupported value types fail fast with a
     * descriptive {@link IllegalArgumentException}; null values skip the part.
     */
    private static MultiValueMap<String, HttpEntity<?>> buildMultipartBody(RequestPlan plan, Object[] args) {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();

        plan.formFields().forEach(binding -> {
            int idx = binding.argumentIndex();
            String name = binding.name();
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

        plan.formFiles().forEach(binding -> {
            int idx = binding.argumentIndex();
            FormFile annotation = binding.annotation();
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
        return ErrorCategories.from(error, statusCode != null ? statusCode.value() : null);
    }

    private RequestArgumentResolver.ResolvedArgs applyDefaultHeaders(RequestArgumentResolver.ResolvedArgs resolved) {
        if (clientConfig.getDefaultHeaders() == null || clientConfig.getDefaultHeaders().isEmpty()) {
            return resolved;
        }
        Map<String, String> merged = new LinkedHashMap<>(clientConfig.getDefaultHeaders());
        resolved.headers().forEach((name, value) -> {
            String existingName = findHeaderNameIgnoreCase(merged, name);
            if (existingName != null) {
                merged.remove(existingName);
            }
            merged.put(name, value);
        });
        return new RequestArgumentResolver.ResolvedArgs(
                resolved.pathVars(),
                resolved.queryParams(),
                merged,
                resolved.body());
    }

    private RequestArgumentResolver.ResolvedArgs applyDefaultQueryParams(RequestArgumentResolver.ResolvedArgs resolved) {
        if (clientConfig.getDefaultQueryParams() == null || clientConfig.getDefaultQueryParams().isEmpty()) {
            return resolved;
        }
        Map<String, List<Object>> merged = new LinkedHashMap<>();
        clientConfig.getDefaultQueryParams().forEach((name, values) ->
                merged.put(name, values.stream().map(value -> (Object) value).toList()));
        resolved.queryParams().forEach((name, values) -> {
            merged.remove(name);
            merged.put(name, values);
        });
        return new RequestArgumentResolver.ResolvedArgs(
                resolved.pathVars(),
                merged,
                resolved.headers(),
                resolved.body());
    }

    private String findHeaderNameIgnoreCase(Map<String, String> headers, String headerName) {
        for (String existingName : headers.keySet()) {
            if (existingName.equalsIgnoreCase(headerName)) {
                return existingName;
            }
        }
        return null;
    }

    private record SerializedRequestBody(Object originalBody, Object bodyToWrite, byte[] rawBody) {}
    private record RequestUriTemplate(String path, String query) {}

    // -------------------------------------------------------------------------
    // Package-private accessors for unit tests
    // -------------------------------------------------------------------------

    /**
     * Exposes the {@link #logResilienceOperatorFailure} logic to tests in the same package
     * so they can drive the resilience-warning-key cap without needing a running WebClient.
     */
    void testOnlyLogResilienceOperatorFailure(String operatorType, String instanceName, Exception error) {
        logResilienceOperatorFailure(operatorType, instanceName, error);
    }

    /** Returns the current size of the resilience-warning-key deduplication set. */
    int testOnlyResilienceWarningKeysSize() {
        return resilienceWarningKeys.size();
    }

}
