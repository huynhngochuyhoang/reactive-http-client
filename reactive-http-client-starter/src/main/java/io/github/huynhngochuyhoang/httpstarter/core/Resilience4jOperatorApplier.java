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

    public Resilience4jOperatorApplier(
            Object circuitBreakerRegistry,
            Object retryRegistry,
            Object bulkheadRegistry) {
        this.circuitBreakerRegistry = circuitBreakerRegistry instanceof CircuitBreakerRegistry registry ? registry : null;
        this.retryRegistry = retryRegistry instanceof RetryRegistry registry ? registry : null;
        this.bulkheadRegistry = bulkheadRegistry instanceof BulkheadRegistry registry ? registry : null;
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
}
