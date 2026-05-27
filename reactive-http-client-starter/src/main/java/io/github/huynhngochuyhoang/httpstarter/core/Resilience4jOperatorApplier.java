package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.reactor.bulkhead.operator.BulkheadOperator;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.github.resilience4j.reactor.retry.RetryOperator;
import io.github.resilience4j.retry.RetryRegistry;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Resilience4j-backed operator applier.
 */
public class Resilience4jOperatorApplier implements ResilienceOperatorApplier {

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RetryRegistry retryRegistry;
    private final BulkheadRegistry bulkheadRegistry;
    private final RateLimiterOperatorAdapter rateLimiterOperatorAdapter;

    public Resilience4jOperatorApplier(
            Object circuitBreakerRegistry,
            Object retryRegistry,
            Object bulkheadRegistry,
            Object rateLimiterRegistry) {
        this.circuitBreakerRegistry = circuitBreakerRegistry instanceof CircuitBreakerRegistry registry ? registry : null;
        this.retryRegistry = retryRegistry instanceof RetryRegistry registry ? registry : null;
        this.bulkheadRegistry = bulkheadRegistry instanceof BulkheadRegistry registry ? registry : null;
        this.rateLimiterOperatorAdapter = resolveRateLimiterOperatorAdapter(rateLimiterRegistry);
    }

    @Override
    public <T> Mono<T> applyCircuitBreaker(Mono<T> mono, String instanceName) {
        if (circuitBreakerRegistry == null) {
            return mono;
        }
        return mono.transformDeferred(CircuitBreakerOperator.of(circuitBreakerRegistry.circuitBreaker(instanceName)));
    }

    @Override
    public <T> Flux<T> applyCircuitBreaker(Flux<T> flux, String instanceName) {
        if (circuitBreakerRegistry == null) {
            return flux;
        }
        return flux.transformDeferred(CircuitBreakerOperator.of(circuitBreakerRegistry.circuitBreaker(instanceName)));
    }

    @Override
    public <T> Mono<T> applyRetry(Mono<T> mono, String instanceName) {
        if (retryRegistry == null) {
            return mono;
        }
        return mono.transformDeferred(RetryOperator.of(retryRegistry.retry(instanceName)));
    }

    @Override
    public <T> Flux<T> applyRetry(Flux<T> flux, String instanceName) {
        if (retryRegistry == null) {
            return flux;
        }
        return flux.transformDeferred(RetryOperator.of(retryRegistry.retry(instanceName)));
    }

    @Override
    public <T> Mono<T> applyBulkhead(Mono<T> mono, String instanceName) {
        if (bulkheadRegistry == null) {
            return mono;
        }
        return mono.transformDeferred(BulkheadOperator.of(bulkheadRegistry.bulkhead(instanceName)));
    }

    @Override
    public <T> Flux<T> applyBulkhead(Flux<T> flux, String instanceName) {
        if (bulkheadRegistry == null) {
            return flux;
        }
        return flux.transformDeferred(BulkheadOperator.of(bulkheadRegistry.bulkhead(instanceName)));
    }

    @Override
    public <T> Mono<T> applyRateLimiter(Mono<T> mono, String instanceName) {
        if (rateLimiterOperatorAdapter == null) {
            return mono;
        }
        return rateLimiterOperatorAdapter.apply(mono, instanceName);
    }

    @Override
    public <T> Flux<T> applyRateLimiter(Flux<T> flux, String instanceName) {
        if (rateLimiterOperatorAdapter == null) {
            return flux;
        }
        return rateLimiterOperatorAdapter.apply(flux, instanceName);
    }

    @Override
    public boolean isOperatorAvailable(InstanceType type) {
        return switch (type) {
            case RETRY -> retryRegistry != null;
            case CIRCUIT_BREAKER -> circuitBreakerRegistry != null;
            case BULKHEAD -> bulkheadRegistry != null;
            case RATE_LIMITER -> rateLimiterOperatorAdapter != null;
        };
    }

    @Override
    public boolean isInstanceConfigured(InstanceType type, String instanceName) {
        if (instanceName == null || instanceName.isBlank()) return true;
        return switch (type) {
            case RETRY -> retryRegistry == null || retryRegistry.find(instanceName).isPresent();
            case CIRCUIT_BREAKER ->
                    circuitBreakerRegistry == null || circuitBreakerRegistry.find(instanceName).isPresent();
            case BULKHEAD -> bulkheadRegistry == null || bulkheadRegistry.find(instanceName).isPresent();
            case RATE_LIMITER -> rateLimiterOperatorAdapter == null
                    || rateLimiterOperatorAdapter.isInstanceConfigured(instanceName);
        };
    }

    private RateLimiterOperatorAdapter resolveRateLimiterOperatorAdapter(Object rateLimiterRegistry) {
        if (rateLimiterRegistry == null) {
            return null;
        }
        try {
            Class<?> adapterClass = Class.forName(
                    "io.github.huynhngochuyhoang.httpstarter.core.Resilience4jRateLimiterOperatorAdapter");
            return (RateLimiterOperatorAdapter) adapterClass.getConstructor(Object.class).newInstance(rateLimiterRegistry);
        } catch (ReflectiveOperationException | LinkageError error) {
            return null;
        }
    }
}
