package io.github.huynhngochuyhoang.httpstarter.core;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Applies resilience operators to Reactor publishers.
 *
 * <p>This interface is public for diagnostics, contract snapshots, and test
 * helpers that need to model operator availability without depending directly
 * on Resilience4j registries. Implementations should preserve the default
 * validation methods unless they can answer from a real registry.
 */
public interface ResilienceOperatorApplier {

    /** Resilience component categories used by {@link #isInstanceConfigured(InstanceType, String)}. */
    enum InstanceType {
        RETRY, CIRCUIT_BREAKER, BULKHEAD, RATE_LIMITER
    }

    <T> Mono<T> applyCircuitBreaker(Mono<T> mono, String instanceName);

    <T> Flux<T> applyCircuitBreaker(Flux<T> flux, String instanceName);

    <T> Mono<T> applyRetry(Mono<T> mono, String instanceName);

    <T> Flux<T> applyRetry(Flux<T> flux, String instanceName);

    <T> Mono<T> applyBulkhead(Mono<T> mono, String instanceName);

    <T> Flux<T> applyBulkhead(Flux<T> flux, String instanceName);

    <T> Mono<T> applyRateLimiter(Mono<T> mono, String instanceName);

    <T> Flux<T> applyRateLimiter(Flux<T> flux, String instanceName);

    /**
     * {@code true} if this applier can actually attach the given operator type.
     */
    default boolean isOperatorAvailable(InstanceType type) {
        return true;
    }

    /**
     * {@code true} if the named instance is registered in the corresponding
     * Resilience4j registry. Used by the starter at proxy-construction time to
     * fail fast on a typo in a per-method {@code @Retry} / {@code @CircuitBreaker}
     * / {@code @Bulkhead} / {@code @RateLimiter} annotation. Implementations that have no registry (e.g.
     * {@link NoopResilienceOperatorApplier}) must return {@code true} to
     * effectively skip validation.
     */
    default boolean isInstanceConfigured(InstanceType type, String instanceName) {
        return true;
    }

    /**
     * true if the named Retry instance can make a duplicate attempt.
     */
    default boolean canRetryMoreThanOnce(String instanceName) {
        return isOperatorAvailable(InstanceType.RETRY);
    }
}
