package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.annotation.FormFile;
import io.github.huynhngochuyhoang.httpstarter.annotation.LogHttpExchange;
import io.github.huynhngochuyhoang.httpstarter.auth.AuthRequest;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import io.github.huynhngochuyhoang.httpstarter.exception.*;
import io.github.huynhngochuyhoang.httpstarter.filter.InboundHeadersWebFilter;
import io.github.huynhngochuyhoang.httpstarter.observability.CompositeHttpClientObserver;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientFailureStage;
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
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.*;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Signal;
import reactor.core.scheduler.Schedulers;
import reactor.netty.http.client.HttpClientRequest;
import reactor.util.context.ContextView;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.net.URI;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.Charset;
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
 *       (retry -> rate-limiter -> circuit-breaker -> bulkhead), then one
 *       end-to-end logical-call timeout budget</li>
 * </ol>
 */
public class ReactiveClientInvocationHandler implements InvocationHandler {

    private static final Logger log = LoggerFactory.getLogger(ReactiveClientInvocationHandler.class);
    static final String OBSERVED_REQUEST_URL_ATTRIBUTE =
            ReactiveClientInvocationHandler.class.getName() + ".observedRequestUrl";
    static final String FINAL_REQUEST_OBSERVATION_ATTRIBUTE =
            ReactiveClientInvocationHandler.class.getName() + ".finalRequestObservation";
    static final String RESILIENCE_OPERATOR_ORDER = "retry -> rate-limiter -> circuit-breaker -> bulkhead";
    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    private static final Object SUBSCRIPTION_STATE_CONTEXT_KEY = new Object();
    private static final int MAX_LOGGER_CACHE_SIZE = 256;
    private static final int MAX_RESILIENCE_WARNING_KEYS = 256;

    private final WebClient webClient;
    private final MethodMetadataCache metadataCache;
    private final RequestArgumentResolver argumentResolver;
    private final DefaultErrorDecoder errorDecoder;
    private final ReactiveHttpClientProperties.ClientConfig clientConfig;
    private final String clientName;
    private final Class<?> clientInterface;
    private final Map<Method, RequestPlan> requestPlanCache = new ConcurrentHashMap<>();
    private final ApplicationContext applicationContext;
    private final Map<Class<? extends HttpExchangeLogger>, HttpExchangeLogger> loggerCache = new ConcurrentHashMap<>();
    private final AtomicBoolean loggerCacheLimitWarningLogged = new AtomicBoolean(false);
    private final Set<String> resilienceWarningKeys = ConcurrentHashMap.newKeySet();
    private final Set<String> unsafeRetryWarningKeys = ConcurrentHashMap.newKeySet();
    private final Set<String> retryBodyWarningKeys = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean resilienceWarningKeysLimitWarningLogged = new AtomicBoolean(false);

    private final ResilienceOperatorApplier resilienceOperatorApplier;
    private final ReactiveHttpClientJsonCodec jsonCodec;

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
            ReactiveHttpClientJsonCodec jsonCodec,
            ReactiveHttpClientProperties.ObservabilityConfig observabilityConfig) {
        this(webClient, metadataCache, argumentResolver, errorDecoder, clientConfig, clientName, null,
                applicationContext, resilienceOperatorApplier, jsonCodec, observabilityConfig);
    }


    public static ReactiveClientInvocationHandler create(
            WebClient webClient,
            MethodMetadataCache metadataCache,
            RequestArgumentResolver argumentResolver,
            DefaultErrorDecoder errorDecoder,
            ReactiveHttpClientProperties.ClientConfig clientConfig,
            String clientName,
            Class<?> clientInterface,
            ApplicationContext applicationContext,
            ResilienceOperatorApplier resilienceOperatorApplier,
            ReactiveHttpClientJsonCodec jsonCodec,
            ReactiveHttpClientProperties.ObservabilityConfig observabilityConfig) {
        return new ReactiveClientInvocationHandler(webClient, metadataCache, argumentResolver, errorDecoder,
                clientConfig, clientName, clientInterface, applicationContext, resilienceOperatorApplier,
                jsonCodec, observabilityConfig);
    }

    public ReactiveClientInvocationHandler(
            WebClient webClient,
            MethodMetadataCache metadataCache,
            RequestArgumentResolver argumentResolver,
            DefaultErrorDecoder errorDecoder,
            ReactiveHttpClientProperties.ClientConfig clientConfig,
            String clientName,
            Class<?> clientInterface,
            ApplicationContext applicationContext,
            ResilienceOperatorApplier resilienceOperatorApplier,
            ReactiveHttpClientJsonCodec jsonCodec,
            ReactiveHttpClientProperties.ObservabilityConfig observabilityConfig) {
        this.webClient = webClient;
        this.metadataCache = metadataCache;
        this.argumentResolver = argumentResolver;
        this.errorDecoder = errorDecoder;
        this.clientConfig = Objects.requireNonNull(clientConfig, "clientConfig must not be null");
        this.clientName = clientName;
        this.clientInterface = clientInterface;
        this.applicationContext = applicationContext;
        this.resilienceOperatorApplier = resilienceOperatorApplier != null
                ? resilienceOperatorApplier
                : new NoopResilienceOperatorApplier();
        this.jsonCodec = jsonCodec;
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
        return finalRequestObservationFilter();
    }

    public static ExchangeFilterFunction finalRequestObservationFilter() {
        return (request, next) -> {
            request.attribute(OBSERVED_REQUEST_URL_ATTRIBUTE)
                    .filter(AtomicReference.class::isInstance)
                    .map(AtomicReference.class::cast)
                    .ifPresent(reference -> reference.set(request.url()));
            request.attribute(FINAL_REQUEST_OBSERVATION_ATTRIBUTE)
                    .filter(AtomicReference.class::isInstance)
                    .map(AtomicReference.class::cast)
                    .ifPresent(reference -> reference.set(FinalRequestObservation.from(request)));
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
        RequestPlan plan = requestPlan(method, meta);
        EffectiveApi effectiveApi = resolveEffectiveApi(plan);

        if (effectiveApi.httpMethod() == null) {
            throw new UnsupportedOperationException(
                    "Method " + method.getName() + " has no HTTP verb annotation (@GET, @POST, @PUT, @DELETE, @PATCH) or @ApiRef");
        }

        RequestArgumentResolver.ResolvedArgs resolved = applyDefaultHeaders(
                applyDefaultQueryParams(argumentResolver.resolve(plan, args)));
        long requestBytes = measureRequestBodyBytes(resolved.body());
        RequestBodyOwnership requestBodyOwnership = new RequestBodyOwnership(resolved.body());

        HttpExchangeLogger exchangeLogger = resolveExchangeLogger(proxy, method, meta);

        boolean hasAcceptHeader = resolved.headersIgnoreCase().containsKey(HttpHeaders.ACCEPT);
        String contentTypeHeader = resolved.headersIgnoreCase().get(HttpHeaders.CONTENT_TYPE);
        boolean hasContentTypeHeader = contentTypeHeader != null;

        long timeoutMs = resolveTimeoutMs(plan, effectiveApi.timeoutMs());
        long logicalCallTimeoutMs = clientConfig.getLogicalCallTimeoutMs();

        final MultiValueMap<String, HttpEntity<?>> multipartBody = plan.multipart()
                ? buildMultipartBody(plan, args)
                : null;

        // Resolve observer once per invocation to avoid repeated volatile reads
        HttpClientObserver observer = getObserver();
        List<ReactiveHttpClientLifecycleHook> lifecycleHooks = getLifecycleHooks();

        // Apply when: (a) caller set an explicit @TimeoutMs (including 0 to disable), or (b) a resilience timeout resolved to > 0.
        boolean shouldApplyResponseTimeout = plan.timeoutMs() != MethodMetadata.TIMEOUT_NOT_SET
                || effectiveApi.timeoutMs() != MethodMetadata.TIMEOUT_NOT_SET
                || isClientLevelRequestTimeoutConfigured()
                || timeoutMs > 0;
        boolean usesSubscriptionState = usesSubscriptionState(
                plan, exchangeLogger, observer, lifecycleHooks, logicalCallTimeoutMs);
        Mono<WebClient.RequestHeadersSpec<?>> requestHeadersSpecMono = usesSubscriptionState
                ? statefulRequestHeadersSpec(plan, effectiveApi, resolved, contentTypeHeader, hasAcceptHeader,
                hasContentTypeHeader, multipartBody, lifecycleHooks, exchangeLogger, timeoutMs, shouldApplyResponseTimeout, requestBodyOwnership)
                : statelessRequestHeadersSpec(plan, effectiveApi, resolved, hasAcceptHeader, hasContentTypeHeader,
                multipartBody, timeoutMs, shouldApplyResponseTimeout, requestBodyOwnership);

        if (plan.returnsFlux()) {
            Flux<?> flux = usesSubscriptionState
                    ? exchange(requestHeadersSpecMono, response -> buildFlux(response, plan.responseType()))
                    : exchangeStateless(requestHeadersSpecMono, response -> buildFlux(response, plan.responseType()));
            flux = applyResilienceFlux(flux, plan, effectiveApi.httpMethod(), resolved);
            flux = applyLogicalCallTimeoutFlux(flux, logicalCallTimeoutMs);
            if (exchangeLogger != null || observer != null || !lifecycleHooks.isEmpty()) {
                Flux<?> capturedFlux = flux;
                flux = Flux.deferContextual(ctx -> {
                    AtomicReference<Map<String, List<String>>> inboundHeadersRef = new AtomicReference<>(
                            ctx.hasKey(InboundHeadersWebFilter.INBOUND_HEADERS_CONTEXT_KEY)
                                    ? ctx.get(InboundHeadersWebFilter.INBOUND_HEADERS_CONTEXT_KEY)
                                    : Map.of());
                    SubscriptionState state = subscriptionState(ctx);
                    AtomicBoolean reported = new AtomicBoolean(false);
                    return capturedFlux
                            .doOnComplete(() -> notifyLifecycleSuccess(lifecycleHooks, plan, effectiveApi, state.preparedResolved.get(), state.requestUrl.get(),
                                    state.responseStatus.get(), state.attemptCount.get()))
                            .doOnError(state.terminalError::set)
                            .doOnError(error -> notifyLifecycleError(lifecycleHooks, plan, effectiveApi, state.preparedResolved.get(), finalRequestUrl(state),
                                    state.responseStatus.get(), error, state.attemptCount.get()))
                            .doOnTerminate(() -> {
                                if (reported.compareAndSet(false, true))
                                    reportExchange(exchangeLogger, observer, plan, effectiveApi.httpMethod(), effectiveApi.pathTemplate(), state.preparedResolved.get(), state.requestUrl.get(), state.finalRequestObservation.get(), state.start.get(),
                                            state.responseStatus.get(), state.responseHeaders.get(), null, state.terminalError.get(), inboundHeadersRef.get(), state.attemptCount.get(), requestBytes);
                            })
                            .doOnCancel(() -> {
                                CancellationException cancellation = new CancellationException("Request was cancelled");
                                notifyLifecycleAttemptFallbackIfNeeded(lifecycleHooks, plan, effectiveApi, resolved, state, exchangeLogger);
                                notifyLifecycleCancel(lifecycleHooks, plan, effectiveApi, state.preparedResolved.get(), state.requestUrl.get(),
                                        state.responseStatus.get(), cancellation, state.attemptCount.get());
                                if (reported.compareAndSet(false, true))
                                    reportExchange(exchangeLogger, observer, plan, effectiveApi.httpMethod(), effectiveApi.pathTemplate(), state.preparedResolved.get(), state.requestUrl.get(), state.finalRequestObservation.get(), state.start.get(),
                                            state.responseStatus.get(), state.responseHeaders.get(), null, cancellation, inboundHeadersRef.get(), state.attemptCount.get(), requestBytes);
                            });
                });
            }
            flux = releaseRequestBodyOnTermination(flux, requestBodyOwnership);
            return usesSubscriptionState ? flux.contextWrite(context -> withSubscriptionState(context, resolved)) : flux;
        }

        Mono<?> mono = isResponseEntityOfFluxDataBuffer(plan.responseType())
                ? (usesSubscriptionState
                ? exchangeStreamingResponseEntity(requestHeadersSpecMono)
                : exchangeStreamingResponseEntityStateless(requestHeadersSpecMono))
                : (usesSubscriptionState
                ? exchange(requestHeadersSpecMono, response -> buildMono(response, plan.responseType()))
                : exchangeStateless(requestHeadersSpecMono, response -> buildMono(response, plan.responseType())))
                .next();
        mono = applyResilienceMono(mono, plan, effectiveApi.httpMethod(), resolved);
        mono = applyLogicalCallTimeoutMono(mono, logicalCallTimeoutMs);
        if (exchangeLogger != null || observer != null || !lifecycleHooks.isEmpty()) {
            Mono<?> capturedMono = mono;
            mono = Mono.deferContextual(ctx -> {
                AtomicReference<Map<String, List<String>>> inboundHeadersRef = new AtomicReference<>(
                        ctx.hasKey(InboundHeadersWebFilter.INBOUND_HEADERS_CONTEXT_KEY)
                                ? ctx.get(InboundHeadersWebFilter.INBOUND_HEADERS_CONTEXT_KEY)
                                : Map.of());
                SubscriptionState state = subscriptionState(ctx);
                AtomicReference<Object> terminalBody = new AtomicReference<>();
                AtomicBoolean reported = new AtomicBoolean(false);
                return capturedMono
                        .doOnSuccess(body -> {
                            terminalBody.set(body);
                            notifyLifecycleSuccess(lifecycleHooks, plan, effectiveApi, state.preparedResolved.get(), state.requestUrl.get(),
                                    state.responseStatus.get(), state.attemptCount.get());
                        })
                        .doOnError(state.terminalError::set)
                        .doOnError(error -> notifyLifecycleError(lifecycleHooks, plan, effectiveApi, state.preparedResolved.get(), finalRequestUrl(state),
                                state.responseStatus.get(), error, state.attemptCount.get()))
                        .doOnTerminate(() -> {
                            if (reported.compareAndSet(false, true))
                                reportExchange(exchangeLogger, observer, plan, effectiveApi.httpMethod(), effectiveApi.pathTemplate(), state.preparedResolved.get(), state.requestUrl.get(), state.finalRequestObservation.get(), state.start.get(),
                                        state.responseStatus.get(), state.responseHeaders.get(), terminalBody.get(), state.terminalError.get(), inboundHeadersRef.get(), state.attemptCount.get(), requestBytes);
                        })
                        .doOnCancel(() -> {
                            CancellationException cancellation = new CancellationException("Request was cancelled");
                            notifyLifecycleAttemptFallbackIfNeeded(lifecycleHooks, plan, effectiveApi, resolved, state, exchangeLogger);
                            notifyLifecycleCancel(lifecycleHooks, plan, effectiveApi, state.preparedResolved.get(), state.requestUrl.get(),
                                    state.responseStatus.get(), cancellation, state.attemptCount.get());
                            if (reported.compareAndSet(false, true))
                                reportExchange(exchangeLogger, observer, plan, effectiveApi.httpMethod(), effectiveApi.pathTemplate(), state.preparedResolved.get(), state.requestUrl.get(), state.finalRequestObservation.get(), state.start.get(),
                                        state.responseStatus.get(), state.responseHeaders.get(), null, cancellation, inboundHeadersRef.get(), state.attemptCount.get(), requestBytes);
                        });
            });
        }
        mono = releaseRequestBodyOnTermination(mono, requestBodyOwnership);
        return usesSubscriptionState ? mono.contextWrite(context -> withSubscriptionState(context, resolved)) : mono;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private RequestPlan requestPlan(Method method, MethodMetadata meta) {
        if (clientInterface == null) {
            return meta.getRequestPlan() != null ? meta.getRequestPlan() : RequestPlan.from(meta);
        }
        return requestPlanCache.computeIfAbsent(method, ignored -> RequestPlan.from(meta, clientInterface));
    }

    private boolean usesSubscriptionState(RequestPlan plan,
                                          HttpExchangeLogger exchangeLogger,
                                          HttpClientObserver observer,
                                          List<ReactiveHttpClientLifecycleHook> lifecycleHooks,
                                          long logicalCallTimeoutMs) {
        ReactiveHttpClientProperties.ResilienceConfig resilience = clientConfig.getResilience();
        return exchangeLogger != null
                || observer != null
                || !lifecycleHooks.isEmpty()
                || clientConfig.hasAuthConfigured()
                || (resilience != null && resilience.isEnabled())
                || logicalCallTimeoutMs > 0
                || StringUtils.hasText(plan.generatedIdempotencyKeyHeader());
    }

    private Mono<WebClient.RequestHeadersSpec<?>> statefulRequestHeadersSpec(
            RequestPlan plan,
            EffectiveApi effectiveApi,
            RequestArgumentResolver.ResolvedArgs resolved,
            String contentTypeHeader,
            boolean hasAcceptHeader,
            boolean hasContentTypeHeader,
            MultiValueMap<String, HttpEntity<?>> multipartBody,
            List<ReactiveHttpClientLifecycleHook> lifecycleHooks,
            HttpExchangeLogger exchangeLogger,
            long timeoutMs,
            boolean shouldApplyResponseTimeout,
            RequestBodyOwnership requestBodyOwnership) {
        // Cache the serialized body so retries reuse the bytes without re-serializing.
        Mono<SerializedRequestBody> serializedBodyMono = serializeRequestBodyForAuth(resolved.body(), contentTypeHeader).cache();
        return Mono.deferContextual(context -> {
            SubscriptionState state = subscriptionState(context);
            RequestArgumentResolver.ResolvedArgs preparedResolved = applyIdempotencyKey(
                    plan, resolved, context, state.generatedIdempotencyKey);
            state.preparedResolved.set(preparedResolved);
            int attempt = state.attemptCount.incrementAndGet();
            state.start.compareAndSet(0L, System.currentTimeMillis());
            state.resetAttemptEvidence();
            state.activeAttemptNumber.set(attempt);
            notifyLifecycleAttempt(lifecycleHooks, plan, effectiveApi, preparedResolved, state.requestUrl.get(), null, null, attempt);
            if (exchangeLogger == null && state.firstAttempt.compareAndSet(true, false)) {
                logRequest(effectiveApi.httpMethod(), effectiveApi.pathTemplate(), state.start.get());
            }

            return serializedBodyMono.map(serializedRequestBody -> buildRequestHeadersSpec(
                    plan,
                    effectiveApi,
                    preparedResolved,
                    serializedRequestBody,
                    hasAcceptHeader,
                    hasContentTypeHeader,
                    multipartBody,
                    timeoutMs,
                    shouldApplyResponseTimeout,
                    state.requestUrl,
                    state.finalRequestObservation,
                    state::resetAttemptEvidence,
                    requestBodyOwnership))
                    .doOnError(ignored -> state.activeAttemptNumber.compareAndSet(attempt, 0))
                    .doOnCancel(() -> state.activeAttemptNumber.compareAndSet(attempt, 0));
        });
    }

    private Mono<WebClient.RequestHeadersSpec<?>> statelessRequestHeadersSpec(
            RequestPlan plan,
            EffectiveApi effectiveApi,
            RequestArgumentResolver.ResolvedArgs resolved,
            boolean hasAcceptHeader,
            boolean hasContentTypeHeader,
            MultiValueMap<String, HttpEntity<?>> multipartBody,
            long timeoutMs,
            boolean shouldApplyResponseTimeout,
            RequestBodyOwnership requestBodyOwnership) {
        return Mono.deferContextual(context -> {
            if (log.isDebugEnabled()) {
                long startMs = System.currentTimeMillis();
                logRequest(effectiveApi.httpMethod(), effectiveApi.pathTemplate(), startMs);
            }
            RequestArgumentResolver.ResolvedArgs preparedResolved = applyContextIdempotencyKey(plan, resolved, context);
            SerializedRequestBody requestBody = new SerializedRequestBody(
                    preparedResolved.body(), preparedResolved.body(), null);
            return Mono.just(buildRequestHeadersSpec(
                    plan,
                    effectiveApi,
                    preparedResolved,
                    requestBody,
                    hasAcceptHeader,
                    hasContentTypeHeader,
                    multipartBody,
                    timeoutMs,
                    shouldApplyResponseTimeout,
                    null,
                    null,
                    null,
                    requestBodyOwnership));
        });
    }

    private WebClient.RequestHeadersSpec<?> buildRequestHeadersSpec(
            RequestPlan plan,
            EffectiveApi effectiveApi,
            RequestArgumentResolver.ResolvedArgs resolved,
            SerializedRequestBody serializedRequestBody,
            boolean hasAcceptHeader,
            boolean hasContentTypeHeader,
            MultiValueMap<String, HttpEntity<?>> multipartBody,
            long timeoutMs,
            boolean shouldApplyResponseTimeout,
            AtomicReference<URI> requestUrl,
            AtomicReference<FinalRequestObservation> finalRequestObservation,
            Runnable requestObservationReset,
            RequestBodyOwnership requestBodyOwnership) {
        WebClient.RequestBodySpec preparedRequestSpec = webClient
                .method(HttpMethod.valueOf(effectiveApi.httpMethod()))
                .uri(uriBuilder -> buildRequestUri(uriBuilder, effectiveApi.pathTemplate(), resolved));
        if (!hasAcceptHeader) {
            preparedRequestSpec = preparedRequestSpec.accept(MediaType.APPLICATION_JSON);
        }
        if (serializedRequestBody.originalBody() != null) {
            preparedRequestSpec = preparedRequestSpec.attribute(AuthRequest.REQUEST_BODY_ATTRIBUTE, serializedRequestBody.originalBody());
        }
        if (serializedRequestBody.rawBody() != null) {
            preparedRequestSpec = preparedRequestSpec.attribute(AuthRequest.REQUEST_RAW_BODY_ATTRIBUTE, serializedRequestBody.rawBody());
        }

        for (Map.Entry<String, List<String>> header : resolved.headers().entrySet()) {
            preparedRequestSpec.header(header.getKey(), header.getValue().toArray(String[]::new));
        }

        WebClient.RequestHeadersSpec<?> requestHeadersSpec;
        if (multipartBody != null) {
            preparedRequestSpec = preparedRequestSpec.attribute(AuthRequest.REQUEST_BODY_ATTRIBUTE, multipartBody);
            requestHeadersSpec = preparedRequestSpec.body(BodyInserters.fromMultipartData(multipartBody));
        } else if (serializedRequestBody.bodyToWrite() instanceof Publisher<?> publisher) {
            requestHeadersSpec = requestFromPublisher(preparedRequestSpec, publisher, plan.bodyType(), hasContentTypeHeader);
        } else if (serializedRequestBody.bodyToWrite() instanceof DataBuffer dataBuffer) {
            requestHeadersSpec = requestFromDataBuffers(
                    preparedRequestSpec, Mono.just(dataBuffer), hasContentTypeHeader, requestBodyOwnership);
        } else if (serializedRequestBody.bodyToWrite() instanceof InputStream inputStream) {
            Flux<DataBuffer> body = DataBufferUtils.readInputStream(
                    () -> requestBodyOwnership.nonClosing(inputStream), DefaultDataBufferFactory.sharedInstance, 8192)
                    .subscribeOn(Schedulers.boundedElastic());
            requestHeadersSpec = requestFromDataBuffers(preparedRequestSpec, body, hasContentTypeHeader, requestBodyOwnership);
        } else if (serializedRequestBody.bodyToWrite() instanceof ReadableByteChannel channel) {
            Flux<DataBuffer> body = DataBufferUtils.readByteChannel(
                    () -> requestBodyOwnership.nonClosing(channel), DefaultDataBufferFactory.sharedInstance, 8192)
                    .subscribeOn(Schedulers.boundedElastic());
            requestHeadersSpec = requestFromDataBuffers(preparedRequestSpec, body, hasContentTypeHeader, requestBodyOwnership);
        } else if (serializedRequestBody.bodyToWrite() instanceof Reader reader) {
            WebClient.RequestBodySpec requestWithBodySpec = preparedRequestSpec;
            if (!hasContentTypeHeader) {
                requestWithBodySpec = requestWithBodySpec.contentType(
                        new MediaType(MediaType.TEXT_PLAIN, StandardCharsets.UTF_8));
            }
            requestHeadersSpec = requestWithBodySpec.body(
                    BodyInserters.fromPublisher(readerBody(reader, requestBodyOwnership), String.class));
        } else if (serializedRequestBody.bodyToWrite() instanceof Resource resource) {
            WebClient.RequestBodySpec requestWithBodySpec = preparedRequestSpec;
            if (!hasContentTypeHeader) {
                requestWithBodySpec = requestWithBodySpec.contentType(MediaType.APPLICATION_OCTET_STREAM);
            }
            requestHeadersSpec = requestWithBodySpec.bodyValue(resource);
        } else if (serializedRequestBody.originalBody() != null) {
            WebClient.RequestBodySpec requestWithBodySpec = preparedRequestSpec;
            if (!hasContentTypeHeader) {
                requestWithBodySpec = requestWithBodySpec.contentType(MediaType.APPLICATION_JSON);
            }
            requestHeadersSpec = requestWithBodySpec.bodyValue(serializedRequestBody.bodyToWrite());
        } else {
            requestHeadersSpec = preparedRequestSpec;
        }
        if (requestUrl != null) {
            requestHeadersSpec = requestHeadersSpec.attribute(OBSERVED_REQUEST_URL_ATTRIBUTE, requestUrl);
        }
        if (finalRequestObservation != null) {
            requestHeadersSpec = requestHeadersSpec.attribute(FINAL_REQUEST_OBSERVATION_ATTRIBUTE, finalRequestObservation);
        }
        if (requestObservationReset != null) {
            requestHeadersSpec = requestHeadersSpec.attribute(
                    AuthRequest.REQUEST_OBSERVATION_RESET_ATTRIBUTE,
                    requestObservationReset);
        }
        return configureNativeRequest(requestHeadersSpec, timeoutMs, shouldApplyResponseTimeout, requestUrl);
    }

    private <T> Flux<T> exchange(
            Mono<WebClient.RequestHeadersSpec<?>> requestHeadersSpecMono,
            Function<ClientResponse, Publisher<T>> successResponseHandler) {
        return Flux.deferContextual(context -> {
            SubscriptionState state = subscriptionState(context);
            AtomicInteger attempt = new AtomicInteger();
            return requestHeadersSpecMono.flatMapMany(requestHeadersSpec -> {
                attempt.set(state.activeAttemptNumber.get());
                return requestHeadersSpec.exchangeToFlux(clientResponse -> {
                    state.responseStatus.set(clientResponse.statusCode());
                    state.responseHeaders.set(copyHeaders(clientResponse));

                    if (clientResponse.statusCode().isError()) {
                        return decodeErrorResponse(clientResponse).flatMapMany(Mono::error);
                    }
                    return Flux.from(successResponseHandler.apply(clientResponse));
                });
            }).doFinally(ignored -> state.activeAttemptNumber.compareAndSet(attempt.get(), 0));
        });
    }

    private <T> Flux<T> exchangeStateless(
            Mono<WebClient.RequestHeadersSpec<?>> requestHeadersSpecMono,
            Function<ClientResponse, Publisher<T>> successResponseHandler) {
        return requestHeadersSpecMono.flatMapMany(requestHeadersSpec -> requestHeadersSpec.exchangeToFlux(clientResponse -> {
            if (clientResponse.statusCode().isError()) {
                return decodeErrorResponse(clientResponse).flatMapMany(Mono::error);
            }
            return Flux.from(successResponseHandler.apply(clientResponse));
        }));
    }

    // exchangeToMono/exchangeToFlux release unconsumed bodies when their handler completes;
    // this envelope shape intentionally transfers the streaming body to the caller.
    @SuppressWarnings("deprecation")
    private Mono<ResponseEntity<Flux<DataBuffer>>> exchangeStreamingResponseEntity(
            Mono<WebClient.RequestHeadersSpec<?>> requestHeadersSpecMono) {
        return Mono.deferContextual(context -> {
            SubscriptionState state = subscriptionState(context);
            AtomicInteger attempt = new AtomicInteger();
            return requestHeadersSpecMono.flatMap(requestHeadersSpec -> {
                attempt.set(state.activeAttemptNumber.get());
                return requestHeadersSpec.retrieve()
                        .onStatus(HttpStatusCode::isError, response -> {
                            state.responseStatus.set(response.statusCode());
                            state.responseHeaders.set(copyHeaders(response));
                            return decodeErrorResponse(response);
                        })
                        .toEntityFlux(DataBuffer.class)
                        .map(this::withDiscardRelease)
                        .doOnNext(entity -> {
                            state.responseStatus.set(entity.getStatusCode());
                            state.responseHeaders.set(copyHeaders(entity.getHeaders()));
                        });
            }).doFinally(ignored -> state.activeAttemptNumber.compareAndSet(attempt.get(), 0));
        });
    }

    @SuppressWarnings("deprecation")
    private Mono<ResponseEntity<Flux<DataBuffer>>> exchangeStreamingResponseEntityStateless(
            Mono<WebClient.RequestHeadersSpec<?>> requestHeadersSpecMono) {
        return requestHeadersSpecMono.flatMap(requestHeadersSpec -> requestHeadersSpec.retrieve()
                .onStatus(HttpStatusCode::isError, this::decodeErrorResponse)
                .toEntityFlux(DataBuffer.class)
                .map(this::withDiscardRelease));
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
            return buildStreamingResponseEntity(response);
        }

        Type responseEntityBodyType = responseEntityBodyType(responseType);
        if (responseEntityBodyType != null) {
            return buildResponseEntityMono(response, responseEntityBodyType);
        }
        return bodyToMono(response, responseType);
    }

    private Mono<?> buildResponseEntityMono(ClientResponse response, Type bodyType) {
        if (Void.class.equals(bodyType) || void.class.equals(bodyType)) {
            return response.toBodilessEntity();
        }
        if (bodyType instanceof Class<?> responseClass) {
            return response.toEntity(responseClass);
        }
        return response.toEntity(ParameterizedTypeReference.forType(bodyType));
    }

    private Mono<?> bodyToMono(ClientResponse response, Type responseType) {
        if (responseType == null || Void.class.equals(responseType)) {
            return response.releaseBody();
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
        if (responseType instanceof Class<?> responseClass) {
            return response.bodyToMono(responseClass);
        }
        return response.bodyToMono(ParameterizedTypeReference.forType(responseType));
    }

    private ResponseEntity<Flux<DataBuffer>> withDiscardRelease(
            ResponseEntity<Flux<DataBuffer>> entity) {
        return ResponseEntity.status(entity.getStatusCode())
                .headers(entity.getHeaders())
                .body(entity.getBody().doOnDiscard(DataBuffer.class, DataBufferUtils::release));
    }

    private Flux<DataBuffer> streamingDataBuffers(ClientResponse response) {
        return response.bodyToFlux(DataBuffer.class)
                .doOnDiscard(DataBuffer.class, DataBufferUtils::release);
    }

    private Mono<ResponseEntity<Flux<DataBuffer>>> buildStreamingResponseEntity(ClientResponse response) {
        Flux<DataBuffer> streaming = streamingDataBuffers(response);
        return Mono.just(ResponseEntity.status(response.statusCode())
                .headers(response.headers().asHttpHeaders())
                .body(streaming));
    }

    private Flux<?> buildFlux(ClientResponse response, Type responseType) {
        if (responseType == null) {
            return response.bodyToFlux(Object.class);
        }
        if (responseType == DataBuffer.class) {
            // Streaming passthrough: bodyToFlux(DataBuffer.class) wires the identity
            // DataBufferDecoder, so the codec-max-in-memory-size limit does not apply
            // — buffers are emitted as they arrive.
            return streamingDataBuffers(response);
        }
        if (responseType instanceof Class<?> responseClass) {
            return response.bodyToFlux(responseClass);
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
            if (isRetryOperatorAvailable()) {
                Mono<?> retryCandidate = mono;
                mono = Mono.deferContextual(context -> {
                    RequestArgumentResolver.ResolvedArgs safetyResolved = applyContextIdempotencyKey(plan, resolved, context);
                    logUnsafeRetryIfNeeded(plan, resilience, httpMethod, safetyResolved, retryInstance);
                    logRetryBodyRiskIfNeeded(plan, httpMethod, retryInstance, resolved.body());
                    return retryCandidate;
                });
            }
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
            if (isRetryOperatorAvailable()) {
                Flux<?> retryCandidate = flux;
                flux = Flux.deferContextual(context -> {
                    RequestArgumentResolver.ResolvedArgs safetyResolved = applyContextIdempotencyKey(plan, resolved, context);
                    logUnsafeRetryIfNeeded(plan, resilience, httpMethod, safetyResolved, retryInstance);
                    logRetryBodyRiskIfNeeded(plan, httpMethod, retryInstance, resolved.body());
                    return retryCandidate;
                });
            }
            flux = applyRetryFlux(flux, retryInstance);
        }
        flux = applyRateLimiterFlux(flux, resolveResilienceInstanceName(plan.rateLimiterInstanceName(), resilience.getRateLimiter()));
        flux = applyCircuitBreakerFlux(flux, resolveResilienceInstanceName(plan.circuitBreakerInstanceName(), resilience.getCircuitBreaker()));
        flux = applyBulkheadFlux(flux, resolveResilienceInstanceName(plan.bulkheadInstanceName(), resilience.getBulkhead()));
        return flux;
    }

    private Mono<?> applyLogicalCallTimeoutMono(Mono<?> mono, long timeoutMs) {
        if (timeoutMs <= 0) {
            return mono;
        }
        return Mono.deferContextual(context -> {
            SubscriptionState state = subscriptionState(context);
            state.start.compareAndSet(0L, System.currentTimeMillis());
            @SuppressWarnings("unchecked")
            Mono<Signal<Object>> sourceSignal = ((Mono<Object>) mono).materialize();
            Mono<Signal<Object>> deadline = Mono.delay(Duration.ofMillis(timeoutMs))
                    .map(ignored -> Signal.error(logicalCallTimeout(state, timeoutMs)));
            return Mono.firstWithSignal(sourceSignal, deadline).dematerialize();
        });
    }

    private Flux<?> applyLogicalCallTimeoutFlux(Flux<?> flux, long timeoutMs) {
        if (timeoutMs <= 0) {
            return flux;
        }
        return Flux.deferContextual(context -> {
            SubscriptionState state = subscriptionState(context);
            state.start.compareAndSet(0L, System.currentTimeMillis());
            Mono<Void> deadline = Mono.delay(Duration.ofMillis(timeoutMs))
                    .flatMap(ignored -> Mono.error(logicalCallTimeout(state, timeoutMs)));
            return flux.takeUntilOther(deadline);
        });
    }

    private LogicalCallTimeoutException logicalCallTimeout(SubscriptionState state, long timeoutMs) {
        HttpClientFailureStage failureStage = null;
        int activeAttempt = state.activeAttemptNumber.get();
        if (activeAttempt > 0 && activeAttempt == state.attemptCount.get()) {
            if (state.responseStatus.get() != null) {
                failureStage = HttpClientFailureStage.RESPONSE_BODY;
            }
        } else {
            state.resetAttemptEvidence();
        }
        return new LogicalCallTimeoutException(timeoutMs, failureStage);
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
        String methodName = methodSignature(plan);
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

    private boolean isRetryOperatorAvailable() {
        return resilienceOperatorApplier.isOperatorAvailable(ResilienceOperatorApplier.InstanceType.RETRY);
    }

    private void logRetryBodyRiskIfNeeded(RequestPlan plan, String httpMethod, String retryInstance, Object body) {
        RequestBodyRepeatability repeatability = effectiveBodyRepeatability(plan, body);
        if (repeatability != RequestBodyRepeatability.NON_REPEATABLE
                && repeatability != RequestBodyRepeatability.APPLICATION_OWNED) {
            return;
        }
        String methodName = methodSignature(plan);
        String warningKey = clientName + ":body:" + methodName + ":" + httpMethod + ":" + retryInstance;
        if (retryBodyWarningKeys.add(warningKey)) {
            log.warn("Retry configured for reactive HTTP client [{}] method [{}] HTTP [{}] with {} request body [{}]. "
                            + "The starter does not buffer large or streaming bodies to make retry possible; ensure the body can be subscribed/read again or disable retry for this method.",
                    clientName,
                    methodName,
                    httpMethod,
                    repeatability == RequestBodyRepeatability.NON_REPEATABLE ? "non-repeatable" : "application-owned",
                    retryInstance);
        }
    }

    private RequestBodyRepeatability effectiveBodyRepeatability(RequestPlan plan, Object body) {
        if (body instanceof Publisher<?> || body instanceof DataBuffer) {
            return RequestBodyRepeatability.NON_REPEATABLE;
        }
        if (body instanceof Resource) {
            return RequestBodyRepeatability.APPLICATION_OWNED;
        }
        return plan.bodyRepeatability();
    }

    private String methodSignature(RequestPlan plan) {
        Method method = plan.method();
        if (method == null) {
            return plan.apiName();
        }
        String parameters = Arrays.stream(method.getParameterTypes())
                .map(type -> type.getCanonicalName() != null ? type.getCanonicalName() : type.getName())
                .collect(java.util.stream.Collectors.joining(","));
        return method.getDeclaringClass().getSimpleName() + "#" + method.getName() + "(" + parameters + ")";
    }

    private RetrySafetyClassification effectiveRetrySafety(RequestPlan plan,
                                                            String httpMethod,
                                                            RequestArgumentResolver.ResolvedArgs resolved) {
        if (isSafeRetryMethod(httpMethod) || plan.retrySafety() == RetrySafetyClassification.SAFE_METHOD) {
            return RetrySafetyClassification.SAFE_METHOD;
        }
        if (plan.retrySafety() == RetrySafetyClassification.EXPLICIT_IDEMPOTENCY_KEY
                && StringUtils.hasText(plan.generatedIdempotencyKeyHeader())) {
            return RetrySafetyClassification.EXPLICIT_IDEMPOTENCY_KEY;
        }
        if (hasIdempotencyKeyHeaderValue(plan, resolved)) {
            return RetrySafetyClassification.EXPLICIT_IDEMPOTENCY_KEY;
        }
        return RetrySafetyClassification.UNSAFE_RETRY;
    }


    static boolean isSafeRetryMethod(String httpMethod) {
        return httpMethod != null
                && Set.of("GET", "HEAD", "PUT", "DELETE", "OPTIONS", "TRACE")
                .contains(httpMethod.toUpperCase(Locale.ROOT));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private WebClient.RequestHeadersSpec<?> requestFromPublisher(WebClient.RequestBodySpec requestSpec,
                                                                 Publisher<?> publisher,
                                                                 Type bodyType,
                                                                 boolean hasContentTypeHeader) {
        WebClient.RequestBodySpec requestWithBodySpec = requestSpec;
        Class<?> elementClass = publisherElementClass(bodyType);
        if (!hasContentTypeHeader) {
            requestWithBodySpec = requestWithBodySpec.contentType(defaultPublisherContentType(elementClass));
        }
        Publisher<?> body = publisher;
        if (DataBuffer.class.isAssignableFrom(elementClass)) {
            body = Flux.from(publisher).doOnDiscard(DataBuffer.class, DataBufferUtils::release);
        }
        return requestWithBodySpec.body(BodyInserters.fromPublisher((Publisher) body, (Class) elementClass));
    }

    private WebClient.RequestHeadersSpec<?> requestFromDataBuffers(
            WebClient.RequestBodySpec requestSpec,
            Publisher<DataBuffer> body,
            boolean hasContentTypeHeader,
            RequestBodyOwnership requestBodyOwnership) {
        WebClient.RequestBodySpec requestWithBodySpec = requestSpec;
        if (!hasContentTypeHeader) {
            requestWithBodySpec = requestWithBodySpec.contentType(MediaType.APPLICATION_OCTET_STREAM);
        }
        Flux<DataBuffer> releasableBody = Flux.from(body)
                .doOnSubscribe(ignored -> requestBodyOwnership.transferDataBufferToWriter())
                .doOnDiscard(DataBuffer.class, DataBufferUtils::release);
        releasableBody = releasableBody.doFinally(ignored -> requestBodyOwnership.release());
        return requestWithBodySpec.body(BodyInserters.fromDataBuffers(releasableBody));
    }

    private Flux<String> readerBody(Reader reader, RequestBodyOwnership requestBodyOwnership) {
        return Flux.<String>generate(sink -> {
                    char[] characters = new char[4097];
                    try {
                        int read;
                        do {
                            read = reader.read(characters, 0, 4096);
                        } while (read == 0);
                        if (read < 0) {
                            sink.complete();
                            return;
                        }
                        if (Character.isHighSurrogate(characters[read - 1])) {
                            int trailing = reader.read();
                            if (trailing >= 0) {
                                characters[read++] = (char) trailing;
                            }
                        }
                        sink.next(new String(characters, 0, read));
                    } catch (IOException error) {
                        sink.error(error);
                    }
                })
                .subscribeOn(Schedulers.boundedElastic())
                .doFinally(ignored -> requestBodyOwnership.release());
    }

    private Flux<?> releaseRequestBodyOnTermination(
            Flux<?> flux, RequestBodyOwnership requestBodyOwnership) {
        return requestBodyOwnership.requiresCleanup()
                ? flux.doFinally(ignored -> requestBodyOwnership.release())
                : flux;
    }

    private Mono<?> releaseRequestBodyOnTermination(
            Mono<?> mono, RequestBodyOwnership requestBodyOwnership) {
        return requestBodyOwnership.requiresCleanup()
                ? mono.doFinally(ignored -> requestBodyOwnership.release())
                : mono;
    }

    private static boolean isRawOrStreamingBody(Object body) {
        return body instanceof Publisher<?>
                || body instanceof DataBuffer
                || body instanceof Resource
                || body instanceof InputStream
                || body instanceof Reader
                || body instanceof ReadableByteChannel;
    }

    private Class<?> publisherElementClass(Type bodyType) {
        if (bodyType instanceof java.lang.reflect.ParameterizedType parameterizedType) {
            Type[] args = parameterizedType.getActualTypeArguments();
            if (args.length == 1 && args[0] instanceof Class<?> clazz) {
                return clazz;
            }
        }
        return Object.class;
    }

    private MediaType defaultPublisherContentType(Class<?> elementClass) {
        if (DataBuffer.class.isAssignableFrom(elementClass) || byte[].class.equals(elementClass)) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        return MediaType.APPLICATION_JSON;
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
        return resolveEffectiveApi(requestPlan(method, meta));
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
                apiConfig.getMethod().trim().toUpperCase(Locale.ROOT),
                apiConfig.getPath(),
                configuredTimeoutMs);
    }

    private WebClient.RequestHeadersSpec<?> configureNativeRequest(
            WebClient.RequestHeadersSpec<?> requestHeadersSpec,
            long timeoutMs,
            boolean shouldApplyResponseTimeout,
            AtomicReference<URI> requestUrl) {
        if (!shouldApplyResponseTimeout && requestUrl == null) {
            return requestHeadersSpec;
        }
        return requestHeadersSpec.httpRequest(httpRequest -> {
            if (requestUrl != null) {
                requestUrl.set(httpRequest.getURI());
            }
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
        if (isRawOrStreamingBody(body)) {
            return Mono.just(new SerializedRequestBody(body, body, null));
        }
        if (body instanceof byte[] bytes) {
            return Mono.just(new SerializedRequestBody(body, bytes, bytes));
        }
        if (body instanceof String text) {
            return Mono.just(new SerializedRequestBody(body, text, text.getBytes(rawBodyCharset(contentTypeHeader))));
        }
        if (!shouldProvideJsonRawBody(contentTypeHeader) || jsonCodec == null) {
            return Mono.just(new SerializedRequestBody(body, body, null));
        }
        return Mono.fromCallable(() -> {
                    byte[] json = jsonCodec.write(body);
                    return new SerializedRequestBody(body, json, json);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorMap(e -> new RequestSerializationException(clientName, e));
    }

    private Charset rawBodyCharset(String contentTypeHeader) {
        if (contentTypeHeader == null || contentTypeHeader.isBlank()) {
            return StandardCharsets.UTF_8;
        }
        try {
            Charset charset = MediaType.parseMediaType(contentTypeHeader).getCharset();
            return charset != null ? charset : StandardCharsets.UTF_8;
        } catch (Exception ignored) {
            return StandardCharsets.UTF_8;
        }
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

    private void notifyLifecycleAttemptFallbackIfNeeded(
            List<ReactiveHttpClientLifecycleHook> hooks,
            RequestPlan plan,
            EffectiveApi effectiveApi,
            RequestArgumentResolver.ResolvedArgs resolved,
            SubscriptionState state,
            HttpExchangeLogger exchangeLogger) {
        if (!state.attemptCount.compareAndSet(0, 1)) {
            return;
        }
        state.start.compareAndSet(0L, System.currentTimeMillis());
        state.responseStatus.set(null);
        state.responseHeaders.set(Map.of());
        state.terminalError.set(null);
        notifyLifecycleAttempt(hooks, plan, effectiveApi, resolved, state.requestUrl.get(), null, null, 1);
        if (exchangeLogger == null && state.firstAttempt.compareAndSet(true, false)) {
            logRequest(effectiveApi.httpMethod(), effectiveApi.pathTemplate(), state.start.get());
        }
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

    private URI finalRequestUrl(SubscriptionState state) {
        FinalRequestObservation observation = state.finalRequestObservation.get();
        return observation != null ? observation.url() : state.requestUrl.get();
    }

    private void reportExchange(
            HttpExchangeLogger exchangeLogger,
            HttpClientObserver observer,
            RequestPlan plan,
            String httpMethod,
            String pathTemplate,
            RequestArgumentResolver.ResolvedArgs resolved,
            URI requestUrl,
            FinalRequestObservation finalRequestObservation,
            long startMs,
            HttpStatusCode statusCode,
            Map<String, List<String>> responseHeaders,
            Object responseBody,
            Throwable error,
            Map<String, List<String>> inboundHeaders,
            int attemptCount,
            long requestBytes) {
        if (exchangeLogger != null) {
            logExchange(exchangeLogger, httpMethod, pathTemplate, resolved, finalRequestObservation, startMs, statusCode, responseHeaders, responseBody, error, inboundHeaders, attemptCount);
        }
        if (observer != null) {
            long responseBytes = extractContentLengthBytes(responseHeaders);
            URI observedRequestUrl = finalRequestObservation != null ? finalRequestObservation.url() : requestUrl;
            notifyObserver(observer, plan, httpMethod, pathTemplate, resolved, observedRequestUrl, finalRequestObservation, startMs, statusCode, error, responseBody, attemptCount, requestBytes, responseBytes);
        }
    }

    private void logExchange(
            HttpExchangeLogger exchangeLogger,
            String httpMethod,
            String pathTemplate,
            RequestArgumentResolver.ResolvedArgs resolved,
            FinalRequestObservation finalRequestObservation,
            long startMs,
            HttpStatusCode statusCode,
            Map<String, List<String>> responseHeaders,
            Object responseBody,
            Throwable error,
            Map<String, List<String>> inboundHeaders,
            int subscriptionAttemptCount) {
        exchangeLogger.log(new HttpExchangeLogContext(
                clientName,
                httpMethod,
                pathTemplate,
                finalRequestObservation != null ? finalRequestObservation.url() : null,
                Map.copyOf(resolved.pathVars()),
                copyQueryParams(resolved.queryParams()),
                inboundHeaders,
                finalRequestObservation != null ? finalRequestObservation.headers() : resolved.flattenedHeaders(),
                resolved.body(),
                statusCode != null ? statusCode.value() : null,
                responseHeaders == null ? Map.of() : responseHeaders,
                responseBody,
                System.currentTimeMillis() - startMs,
                subscriptionAttemptCount,
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

    private Map<String, List<String>> copyHeaders(HttpHeaders headers) {
        Map<String, List<String>> copied = new LinkedHashMap<>();
        headers.forEach((key, values) -> copied.put(key, List.copyOf(values)));
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
            FinalRequestObservation finalRequestObservation,
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
                    resolveServerPort(requestUrl),
                    requestUrl != null ? requestUrl.toString() : null,
                    finalRequestObservation != null ? finalRequestObservation.headers() : Map.of()
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
     * Best-effort application request body size before transport content coding. Returns
     * the byte count for {@code byte[]} and {@code String} bodies, {@code 0} for {@code null}, and
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
     * Extracts the post-transport {@code Content-Length} header from the captured response
     * headers. Returns {@link HttpClientObserverEvent#UNKNOWN_SIZE} if the header is absent,
     * including chunked responses, network failures, and compressed responses whose encoded
     * length was removed by Reactor Netty during decompression. Streaming bodies are never
     * consumed to calculate this value.
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
        Map<String, List<String>> merged = new LinkedHashMap<>();
        clientConfig.getDefaultHeaders().forEach((name, value) -> {
            if (value != null) {
                merged.put(name, List.of(value));
            }
        });
        resolved.headers().forEach((name, values) -> {
            String existingName = findHeaderNameIgnoreCase(merged, name);
            if (existingName != null) {
                merged.remove(existingName);
            }
            merged.put(name, values);
        });
        return new RequestArgumentResolver.ResolvedArgs(
                resolved.pathVars(),
                resolved.queryParams(),
                merged,
                resolved.body());
    }

    private RequestArgumentResolver.ResolvedArgs applyIdempotencyKey(RequestPlan plan,
                                                                     RequestArgumentResolver.ResolvedArgs resolved,
                                                                     ContextView context,
                                                                     AtomicReference<String> generatedIdempotencyKey) {
        RequestArgumentResolver.ResolvedArgs contextResolved = applyContextIdempotencyKey(plan, resolved, context);
        if (contextResolved != resolved || hasIdempotencyKeyHeaderValue(plan, contextResolved)) {
            return contextResolved;
        }
        if (StringUtils.hasText(plan.generatedIdempotencyKeyHeader())) {
            return withHeader(resolved, plan.generatedIdempotencyKeyHeader(), generatedIdempotencyKey(generatedIdempotencyKey));
        }
        return resolved;
    }

    private static SubscriptionState subscriptionState(reactor.util.context.ContextView context) {
        return context.get(SUBSCRIPTION_STATE_CONTEXT_KEY);
    }

    private static reactor.util.context.Context withSubscriptionState(
            reactor.util.context.Context context,
            RequestArgumentResolver.ResolvedArgs resolved) {
        return context.put(SUBSCRIPTION_STATE_CONTEXT_KEY, new SubscriptionState(resolved));
    }

    private static String generatedIdempotencyKey(AtomicReference<String> generatedIdempotencyKey) {
        String existing = generatedIdempotencyKey.get();
        if (existing != null) {
            return existing;
        }
        String candidate = UUID.randomUUID().toString();
        return generatedIdempotencyKey.compareAndSet(null, candidate) ? candidate : generatedIdempotencyKey.get();
    }

    private RequestArgumentResolver.ResolvedArgs applyContextIdempotencyKey(RequestPlan plan,
                                                                            RequestArgumentResolver.ResolvedArgs resolved,
                                                                            reactor.util.context.ContextView context) {
        if (hasIdempotencyKeyHeaderValue(plan, resolved)) {
            return resolved;
        }
        String contextKey = RequestContext.idempotencyKey(context).orElse(null);
        if (StringUtils.hasText(contextKey)) {
            return withHeader(resolved, idempotencyHeaderName(plan), contextKey);
        }
        return resolved;
    }

    private static boolean hasIdempotencyKeyHeaderValue(RequestPlan plan, RequestArgumentResolver.ResolvedArgs resolved) {
        if (resolved.hasHeaderValueIgnoreCase(IDEMPOTENCY_KEY_HEADER)) {
            return true;
        }
        if (StringUtils.hasText(plan.generatedIdempotencyKeyHeader())
                && resolved.hasHeaderValueIgnoreCase(plan.generatedIdempotencyKeyHeader())) {
            return true;
        }
        return plan.idempotencyKeyParams().stream()
                .map(RequestPlan.NamedArgumentBinding::name)
                .anyMatch(resolved::hasHeaderValueIgnoreCase);
    }

    private static String idempotencyHeaderName(RequestPlan plan) {
        if (StringUtils.hasText(plan.generatedIdempotencyKeyHeader())) {
            return plan.generatedIdempotencyKeyHeader();
        }
        return plan.idempotencyKeyParams().stream()
                .map(RequestPlan.NamedArgumentBinding::name)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(IDEMPOTENCY_KEY_HEADER);
    }

    private RequestArgumentResolver.ResolvedArgs withHeader(RequestArgumentResolver.ResolvedArgs resolved,
                                                            String headerName,
                                                            String headerValue) {
        RequestArgumentResolver.validateHeaderName(headerName);
        RequestArgumentResolver.validateHeaderValue(headerName, headerValue);
        Map<String, List<String>> merged = new LinkedHashMap<>(resolved.headers());
        String existingName = findHeaderNameIgnoreCase(merged, headerName);
        if (existingName != null) {
            merged.remove(existingName);
        }
        merged.put(headerName, List.of(headerValue));
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

    private String findHeaderNameIgnoreCase(Map<String, ?> headers, String headerName) {
        for (String existingName : headers.keySet()) {
            if (existingName.equalsIgnoreCase(headerName)) {
                return existingName;
            }
        }
        return null;
    }

    private final class RequestBodyOwnership {
        private final Object body;
        private final AtomicBoolean released = new AtomicBoolean();

        private RequestBodyOwnership(Object body) {
            this.body = body;
        }

        private boolean requiresCleanup() {
            return body instanceof DataBuffer
                    || body instanceof InputStream
                    || body instanceof Reader
                    || body instanceof ReadableByteChannel;
        }

        private InputStream nonClosing(InputStream inputStream) {
            return new FilterInputStream(inputStream) {
                @Override
                public void close() {
                    // The logical-call ownership guard closes the source exactly once.
                }
            };
        }

        private ReadableByteChannel nonClosing(ReadableByteChannel channel) {
            return new ReadableByteChannel() {
                @Override
                public int read(java.nio.ByteBuffer destination) throws IOException {
                    return channel.read(destination);
                }

                @Override
                public boolean isOpen() {
                    return channel.isOpen();
                }

                @Override
                public void close() {
                    // The logical-call ownership guard closes the source exactly once.
                }
            };
        }

        private void transferDataBufferToWriter() {
            if (body instanceof DataBuffer) {
                released.compareAndSet(false, true);
            }
        }

        private void release() {
            if (!requiresCleanup() || !released.compareAndSet(false, true)) {
                return;
            }
            try {
                if (body instanceof DataBuffer dataBuffer) {
                    DataBufferUtils.release(dataBuffer);
                } else if (body instanceof InputStream inputStream) {
                    inputStream.close();
                } else if (body instanceof Reader reader) {
                    reader.close();
                } else if (body instanceof ReadableByteChannel channel) {
                    channel.close();
                }
            } catch (Exception error) {
                log.debug("Failed to release application-owned request body for client [{}]", clientName, error);
            }
        }
    }

    private static final class SubscriptionState {
        private final AtomicReference<String> generatedIdempotencyKey = new AtomicReference<>();
        private final AtomicReference<RequestArgumentResolver.ResolvedArgs> preparedResolved;
        private final AtomicInteger attemptCount = new AtomicInteger(0);
        private final AtomicReference<FinalRequestObservation> finalRequestObservation = new AtomicReference<>();
        private final AtomicLong start = new AtomicLong();
        private final AtomicBoolean firstAttempt = new AtomicBoolean(true);
        private final AtomicReference<URI> requestUrl = new AtomicReference<>();
        private final AtomicReference<HttpStatusCode> responseStatus = new AtomicReference<>();
        private final AtomicReference<Map<String, List<String>>> responseHeaders = new AtomicReference<>(Map.of());
        private final AtomicReference<Throwable> terminalError = new AtomicReference<>();
        private final AtomicInteger activeAttemptNumber = new AtomicInteger();

        private SubscriptionState(RequestArgumentResolver.ResolvedArgs resolved) {
            this.preparedResolved = new AtomicReference<>(resolved);
        }

        private void resetAttemptEvidence() {
            requestUrl.set(null);
            finalRequestObservation.set(null);
            responseStatus.set(null);
            responseHeaders.set(Map.of());
            terminalError.set(null);
        }
    }

    private record SerializedRequestBody(Object originalBody, Object bodyToWrite, byte[] rawBody) {}
    private record RequestUriTemplate(String path, String query) {}

    private record FinalRequestObservation(String httpMethod, URI url, Map<String, String> headers) {
        private static FinalRequestObservation from(ClientRequest request) {
            return new FinalRequestObservation(request.method().name(), request.url(), copyRequestHeaders(request.headers()));
        }
    }

    private static Map<String, String> copyRequestHeaders(HttpHeaders headers) {
        Map<String, String> copied = new LinkedHashMap<>();
        headers.forEach((name, values) -> copied.put(name, String.join(",", values)));
        return Map.copyOf(copied);
    }

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
