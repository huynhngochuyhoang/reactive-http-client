package com.acme.httpstarter.core;

import com.acme.httpstarter.config.ReactiveHttpClientProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.time.Duration;

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

    // Resilience4j registries – may be null when resilience4j is not on the classpath
    private final Object circuitBreakerRegistry;
    private final Object retryRegistry;
    private final Object bulkheadRegistry;

    public ReactiveClientInvocationHandler(
            WebClient webClient,
            MethodMetadataCache metadataCache,
            RequestArgumentResolver argumentResolver,
            DefaultErrorDecoder errorDecoder,
            ReactiveHttpClientProperties.ClientConfig clientConfig,
            String clientName,
            Object circuitBreakerRegistry,
            Object retryRegistry,
            Object bulkheadRegistry) {
        this.webClient = webClient;
        this.metadataCache = metadataCache;
        this.argumentResolver = argumentResolver;
        this.errorDecoder = errorDecoder;
        this.clientConfig = clientConfig;
        this.clientName = clientName;
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.retryRegistry = retryRegistry;
        this.bulkheadRegistry = bulkheadRegistry;
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

        WebClient.ResponseSpec responseSpec;
        if (resolved.body() != null) {
            responseSpec = requestSpec
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(resolved.body())
                    .retrieve();
        } else {
            responseSpec = ((WebClient.RequestHeadersSpec<?>) requestSpec).retrieve();
        }

        responseSpec = responseSpec.onStatus(
                status -> status.isError(),
                clientResponse -> errorDecoder.decode(clientResponse)
                        .flatMap(Mono::error)
        );

        if (meta.isReturnsFlux()) {
            Flux<?> flux = buildFlux(responseSpec, meta.getResponseType());
            flux = applyResilienceFlux(flux, meta);
            logRequest(meta, start);
            return flux;
        }

        Mono<?> mono = buildMono(responseSpec, meta.getResponseType());
        mono = applyResilienceMono(mono, meta);
        logRequest(meta, start);
        return mono;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Mono<?> buildMono(WebClient.ResponseSpec responseSpec, Type responseType) {
        if (responseType == null || responseType == Void.class) {
            return responseSpec.bodyToMono(Void.class);
        }
        return responseSpec.bodyToMono(ParameterizedTypeReference.forType(responseType));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Flux<?> buildFlux(WebClient.ResponseSpec responseSpec, Type responseType) {
        if (responseType == null) {
            return responseSpec.bodyToFlux(Object.class);
        }
        return responseSpec.bodyToFlux(ParameterizedTypeReference.forType(responseType));
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
        if (resilience.getTimeoutMs() > 0) {
            mono = mono.timeout(Duration.ofMillis(resilience.getTimeoutMs()));
        }
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
        if (resilience.getTimeoutMs() > 0) {
            flux = flux.timeout(Duration.ofMillis(resilience.getTimeoutMs()));
        }
        return flux;
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
}
