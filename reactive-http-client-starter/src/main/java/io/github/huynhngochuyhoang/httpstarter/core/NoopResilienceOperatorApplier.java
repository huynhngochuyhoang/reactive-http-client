package io.github.huynhngochuyhoang.httpstarter.core;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * No-op resilience applier used when Resilience4j is unavailable.
 */
public class NoopResilienceOperatorApplier implements ResilienceOperatorApplier {

    @Override
    public <T> Mono<T> applyCircuitBreaker(Mono<T> mono, String instanceName) {
        return mono;
    }

    @Override
    public <T> Flux<T> applyCircuitBreaker(Flux<T> flux, String instanceName) {
        return flux;
    }

    @Override
    public <T> Mono<T> applyRetry(Mono<T> mono, String instanceName) {
        return mono;
    }

    @Override
    public <T> Flux<T> applyRetry(Flux<T> flux, String instanceName) {
        return flux;
    }

    @Override
    public <T> Mono<T> applyBulkhead(Mono<T> mono, String instanceName) {
        return mono;
    }

    @Override
    public <T> Flux<T> applyBulkhead(Flux<T> flux, String instanceName) {
        return flux;
    }
}
