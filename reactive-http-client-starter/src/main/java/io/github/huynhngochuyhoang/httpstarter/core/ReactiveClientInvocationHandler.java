package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientObserver;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientObserverEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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
 *   <li>Optionally apply Resilience4j operators (circuit-breaker, retry, bulkhead, timeout)</li>
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

    // Resilience4j registries – may be null when resilience4j is not on the classpath
    private final Object circuitBreakerRegistry;
    private final Object retryRegistry;
    private final Object bulkheadRegistry;

    // Observability – resolved lazily on first request to avoid ordering issues during
    // context initialization (the observer bean may not yet exist when this handler is constructed)
    private final ReactiveHttpClientProperties.ObservabilityConfig observabilityConfig;
    private volatile HttpClientObserver resolvedObserver;
    private volatile boolean observerResolved = false;

    public ReactiveClientInvocationHandler(
            WebClient webClient,
            MethodMetadataCache metadataCache,
            RequestArgumentResolver argumentResolver,
            DefaultErrorDecoder errorDecoder,
            ReactiveHttpClientProperties.ClientConfig clientConfig,
            String clientName,
            ApplicationContext applicationContext,
            Object circuitBreakerRegistry,
            Object retryRegistry,
            Object bulkheadRegistry,
            HttpClientObserver httpClientObserver,
            ReactiveHttpClientProperties.ObservabilityConfig observabilityConfig) {
        this.webClient = webClient;
        this.metadataCache = metadataCache;
        this.argumentResolver = argumentResolver;
        this.errorDecoder = errorDecoder;
        this.clientConfig = clientConfig;
        this.clientName = clientName;
        this.applicationContext = applicationContext;
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.retryRegistry = retryRegistry;
        this.bulkheadRegistry = bulkheadRegistry;
        // Pre-populate if the observer is already available at construction time
        if (httpClientObserver != null) {
            this.resolvedObserver = httpClientObserver;
            this.observerResolved = true;
        }
        this.observabilityConfig = observabilityConfig;
    }

    /**
     * Returns the {@link HttpClientObserver} to use for this handler.
     * Resolved lazily from the {@link ApplicationContext} on first call so that
     * auto-configured observer beans that are initialised after this handler is
     * constructed are still picked up.
     */
    private HttpClientObserver getObserver() {
        if (!observerResolved) {
            synchronized (this) {
                if (!observerResolved) {
                    resolvedObserver = applicationContext
                            .getBeanProvider(HttpClientObserver.class)
                            .getIfAvailable();
                    observerResolved = true;
                }
            }
        }
        return resolvedObserver;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        // Delegate Object methods to the handler itself
        if (method.getDeclaringClass() == Object.class) {
            return method.invoke(this, args);
        }

        MethodMetadata meta = metadataCache.get(method);

        if (meta.getHttpMethod() == null) {
            throw new UnsupportedOperationException(
                    "Method " + method.getName() + " has no HTTP verb annotation (@GET, @POST, @PUT, @DELETE)");
        }

        RequestArgumentResolver.ResolvedArgs resolved = argumentResolver.resolve(meta, args);

        long start = System.currentTimeMillis();
        HttpExchangeLogger exchangeLogger = resolveExchangeLogger(meta);

        WebClient.RequestBodySpec requestSpec = webClient
                .method(HttpMethod.valueOf(meta.getHttpMethod()))
                .uri(uriBuilder -> {
                    var ub = uriBuilder.path(meta.getPathTemplate());
                    resolved.queryParams().forEach((k, values) ->
                            values.forEach(v -> ub.queryParam(k, String.valueOf(v))));
                    return ub.build(resolved.pathVars());
                })
                .accept(MediaType.APPLICATION_JSON);

        resolved.headers().forEach(requestSpec::header);

        WebClient.RequestHeadersSpec<?> requestHeadersSpec;
        if (resolved.body() != null) {
            requestHeadersSpec = requestSpec
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(resolved.body());
        } else {
            requestHeadersSpec = requestSpec;
        }

        AtomicReference<HttpStatusCode> responseStatus = new AtomicReference<>();
        AtomicReference<Map<String, List<String>>> responseHeaders = new AtomicReference<>(Map.of());

        // Resolve observer once per invocation to avoid repeated volatile reads
        HttpClientObserver observer = getObserver();

        if (meta.isReturnsFlux()) {
            Flux<?> flux = requestHeadersSpec.exchangeToFlux(clientResponse -> {
                responseStatus.set(clientResponse.statusCode());
                responseHeaders.set(copyHeaders(clientResponse));

                if (clientResponse.statusCode().isError()) {
                    return errorDecoder.decode(clientResponse).flatMapMany(Mono::error);
                }
                return buildFlux(clientResponse, meta.getResponseType());
            });
            flux = applyResilienceFlux(flux, meta);
            flux = applyTimeoutFlux(flux, meta);
            if (exchangeLogger != null) {
                flux = flux
                        .doOnComplete(() -> logExchange(
                                exchangeLogger, meta, resolved, start, responseStatus.get(), responseHeaders.get(), null, null))
                        .doOnError(error -> logExchange(
                                exchangeLogger, meta, resolved, start, responseStatus.get(), responseHeaders.get(), null, error));
            } else {
                logRequest(meta, start);
            }
            if (observer != null) {
                flux = flux
                        .doOnComplete(() -> notifyObserver(observer, meta, resolved, start, responseStatus.get(), null, null))
                        .doOnError(error -> notifyObserver(observer, meta, resolved, start, responseStatus.get(), error, null));
            }
            return flux;
        }

        Mono<?> mono = requestHeadersSpec.exchangeToMono(clientResponse -> {
            responseStatus.set(clientResponse.statusCode());
            responseHeaders.set(copyHeaders(clientResponse));

            if (clientResponse.statusCode().isError()) {
                return errorDecoder.decode(clientResponse).flatMap(Mono::error);
            }
            return buildMono(clientResponse, meta.getResponseType());
        });
        mono = applyResilienceMono(mono, meta);
        mono = applyTimeoutMono(mono, meta);
        if (exchangeLogger != null) {
            mono = mono
                    .doOnSuccess(body -> logExchange(
                            exchangeLogger, meta, resolved, start, responseStatus.get(), responseHeaders.get(), body, null))
                    .doOnError(error -> logExchange(
                            exchangeLogger, meta, resolved, start, responseStatus.get(), responseHeaders.get(), null, error));
        } else {
            logRequest(meta, start);
        }
        if (observer != null) {
            mono = mono
                    .doOnSuccess(body -> notifyObserver(observer, meta, resolved, start, responseStatus.get(), null, body))
                    .doOnError(error -> notifyObserver(observer, meta, resolved, start, responseStatus.get(), error, null));
        }
        return mono;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Mono<?> buildMono(ClientResponse response, Type responseType) {
        if (responseType == null || responseType == Void.class) {
            return response.bodyToMono(Void.class);
        }
        return response.bodyToMono(ParameterizedTypeReference.forType(responseType));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Flux<?> buildFlux(ClientResponse response, Type responseType) {
        if (responseType == null) {
            return response.bodyToFlux(Object.class);
        }
        return response.bodyToFlux(ParameterizedTypeReference.forType(responseType));
    }

    private Mono<?> applyResilienceMono(Mono<?> mono, MethodMetadata meta) {
        if (clientConfig == null) return mono;
        ReactiveHttpClientProperties.ResilienceConfig resilience = clientConfig.getResilience();
        if (resilience == null || !resilience.isEnabled()) return mono;

        mono = applyCircuitBreakerMono(mono, resilience);
        // Retry only idempotent methods by default; opt-in for others via config
        if ("GET".equals(meta.getHttpMethod()) || "HEAD".equals(meta.getHttpMethod())) {
            mono = applyRetryMono(mono, resilience);
        }
        mono = applyBulkheadMono(mono, resilience);
        return mono;
    }

    private Flux<?> applyResilienceFlux(Flux<?> flux, MethodMetadata meta) {
        if (clientConfig == null) return flux;
        ReactiveHttpClientProperties.ResilienceConfig resilience = clientConfig.getResilience();
        if (resilience == null || !resilience.isEnabled()) return flux;

        flux = applyCircuitBreakerFlux(flux, resilience);
        if ("GET".equals(meta.getHttpMethod())) {
            flux = applyRetryFlux(flux, resilience);
        }
        flux = applyBulkheadFlux(flux, resilience);
        return flux;
    }

    /**
     * Applies timeout when resolved timeout is {@code > 0}.
     * A resolved value of {@code 0} disables timeout (from method override or config).
     */
    private Mono<?> applyTimeoutMono(Mono<?> mono, MethodMetadata meta) {
        long timeoutMs = resolveTimeoutMs(meta);
        if (timeoutMs <= 0) {
            return mono;
        }
        return mono.timeout(Duration.ofMillis(timeoutMs));
    }

    /**
     * Applies timeout when resolved timeout is {@code > 0}.
     * A resolved value of {@code 0} disables timeout (from method override or config).
     */
    private Flux<?> applyTimeoutFlux(Flux<?> flux, MethodMetadata meta) {
        long timeoutMs = resolveTimeoutMs(meta);
        if (timeoutMs <= 0) {
            return flux;
        }
        return flux.timeout(Duration.ofMillis(timeoutMs));
    }

    private long resolveTimeoutMs(MethodMetadata meta) {
        // Method-level override has highest priority.
        // A method annotation value of 0 explicitly disables timeout for that API method.
        if (meta.getTimeoutMs() >= 0) {
            return meta.getTimeoutMs();
        }
        if (clientConfig == null) {
            return 0;
        }
        ReactiveHttpClientProperties.ResilienceConfig resilience = clientConfig.getResilience();
        if (resilience != null && resilience.isEnabled() && resilience.getTimeoutMs() > 0) {
            return resilience.getTimeoutMs();
        }
        return clientConfig.getReadTimeoutMs();
    }

    @SuppressWarnings("unchecked")
    private Mono<?> applyCircuitBreakerMono(Mono<?> mono, ReactiveHttpClientProperties.ResilienceConfig cfg) {
        if (circuitBreakerRegistry == null) return mono;
        try {
            Class<?> cbRegistryClass = Class.forName(
                    "io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry");
            Class<?> cbClass = Class.forName(
                    "io.github.resilience4j.circuitbreaker.CircuitBreaker");
            Class<?> operatorClass = Class.forName(
                    "io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator");

            Object cb = cbRegistryClass
                    .getMethod("circuitBreaker", String.class)
                    .invoke(circuitBreakerRegistry, cfg.getCircuitBreaker());
            Object operator = operatorClass
                    .getMethod("of", cbClass)
                    .invoke(null, cb);
            return (Mono<?>) mono.getClass()
                    .getMethod("transformDeferred", java.util.function.Function.class)
                    .invoke(mono, operator);
        } catch (Exception e) {
            log.debug("Circuit breaker not applied ({}): {}", cfg.getCircuitBreaker(), e.getMessage());
            return mono;
        }
    }

    @SuppressWarnings("unchecked")
    private Flux<?> applyCircuitBreakerFlux(Flux<?> flux, ReactiveHttpClientProperties.ResilienceConfig cfg) {
        if (circuitBreakerRegistry == null) return flux;
        try {
            Class<?> cbRegistryClass = Class.forName(
                    "io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry");
            Class<?> cbClass = Class.forName(
                    "io.github.resilience4j.circuitbreaker.CircuitBreaker");
            Class<?> operatorClass = Class.forName(
                    "io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator");

            Object cb = cbRegistryClass
                    .getMethod("circuitBreaker", String.class)
                    .invoke(circuitBreakerRegistry, cfg.getCircuitBreaker());
            Object operator = operatorClass
                    .getMethod("of", cbClass)
                    .invoke(null, cb);
            return (Flux<?>) flux.getClass()
                    .getMethod("transformDeferred", java.util.function.Function.class)
                    .invoke(flux, operator);
        } catch (Exception e) {
            log.debug("Circuit breaker not applied ({}): {}", cfg.getCircuitBreaker(), e.getMessage());
            return flux;
        }
    }

    @SuppressWarnings("unchecked")
    private Mono<?> applyRetryMono(Mono<?> mono, ReactiveHttpClientProperties.ResilienceConfig cfg) {
        if (retryRegistry == null) return mono;
        try {
            Class<?> retryRegistryClass = Class.forName(
                    "io.github.resilience4j.retry.RetryRegistry");
            Class<?> retryClass = Class.forName(
                    "io.github.resilience4j.retry.Retry");
            Class<?> operatorClass = Class.forName(
                    "io.github.resilience4j.reactor.retry.RetryOperator");

            Object retry = retryRegistryClass
                    .getMethod("retry", String.class)
                    .invoke(retryRegistry, cfg.getRetry());
            Object operator = operatorClass
                    .getMethod("of", retryClass)
                    .invoke(null, retry);
            return (Mono<?>) mono.getClass()
                    .getMethod("transformDeferred", java.util.function.Function.class)
                    .invoke(mono, operator);
        } catch (Exception e) {
            log.debug("Retry not applied ({}): {}", cfg.getRetry(), e.getMessage());
            return mono;
        }
    }

    @SuppressWarnings("unchecked")
    private Flux<?> applyRetryFlux(Flux<?> flux, ReactiveHttpClientProperties.ResilienceConfig cfg) {
        if (retryRegistry == null) return flux;
        try {
            Class<?> retryRegistryClass = Class.forName(
                    "io.github.resilience4j.retry.RetryRegistry");
            Class<?> retryClass = Class.forName(
                    "io.github.resilience4j.retry.Retry");
            Class<?> operatorClass = Class.forName(
                    "io.github.resilience4j.reactor.retry.RetryOperator");

            Object retry = retryRegistryClass
                    .getMethod("retry", String.class)
                    .invoke(retryRegistry, cfg.getRetry());
            Object operator = operatorClass
                    .getMethod("of", retryClass)
                    .invoke(null, retry);
            return (Flux<?>) flux.getClass()
                    .getMethod("transformDeferred", java.util.function.Function.class)
                    .invoke(flux, operator);
        } catch (Exception e) {
            log.debug("Retry not applied ({}): {}", cfg.getRetry(), e.getMessage());
            return flux;
        }
    }

    @SuppressWarnings("unchecked")
    private Mono<?> applyBulkheadMono(Mono<?> mono, ReactiveHttpClientProperties.ResilienceConfig cfg) {
        if (bulkheadRegistry == null) return mono;
        try {
            Class<?> bhRegistryClass = Class.forName(
                    "io.github.resilience4j.bulkhead.BulkheadRegistry");
            Class<?> bhClass = Class.forName(
                    "io.github.resilience4j.bulkhead.Bulkhead");
            Class<?> operatorClass = Class.forName(
                    "io.github.resilience4j.reactor.bulkhead.operator.BulkheadOperator");

            Object bh = bhRegistryClass
                    .getMethod("bulkhead", String.class)
                    .invoke(bulkheadRegistry, cfg.getBulkhead());
            Object operator = operatorClass
                    .getMethod("of", bhClass)
                    .invoke(null, bh);
            return (Mono<?>) mono.getClass()
                    .getMethod("transformDeferred", java.util.function.Function.class)
                    .invoke(mono, operator);
        } catch (Exception e) {
            log.debug("Bulkhead not applied ({}): {}", cfg.getBulkhead(), e.getMessage());
            return mono;
        }
    }

    @SuppressWarnings("unchecked")
    private Flux<?> applyBulkheadFlux(Flux<?> flux, ReactiveHttpClientProperties.ResilienceConfig cfg) {
        if (bulkheadRegistry == null) return flux;
        try {
            Class<?> bhRegistryClass = Class.forName(
                    "io.github.resilience4j.bulkhead.BulkheadRegistry");
            Class<?> bhClass = Class.forName(
                    "io.github.resilience4j.bulkhead.Bulkhead");
            Class<?> operatorClass = Class.forName(
                    "io.github.resilience4j.reactor.bulkhead.operator.BulkheadOperator");

            Object bh = bhRegistryClass
                    .getMethod("bulkhead", String.class)
                    .invoke(bulkheadRegistry, cfg.getBulkhead());
            Object operator = operatorClass
                    .getMethod("of", bhClass)
                    .invoke(null, bh);
            return (Flux<?>) flux.getClass()
                    .getMethod("transformDeferred", java.util.function.Function.class)
                    .invoke(flux, operator);
        } catch (Exception e) {
            log.debug("Bulkhead not applied ({}): {}", cfg.getBulkhead(), e.getMessage());
            return flux;
        }
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
                    logBody ? resolved.body() : null,
                    logRespBody ? responseBody : null
            ));
        } catch (Exception e) {
            log.warn("HttpClientObserver threw an exception – ignoring: {}", e.getMessage());
        }
    }

}
