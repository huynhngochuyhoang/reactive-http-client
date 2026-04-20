package io.github.huynhngochuyhoang.httpstarter.core;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Applies resilience operators to Reactor publishers.
 */
public interface ResilienceOperatorApplier {

    <T> Mono<T> applyCircuitBreaker(Mono<T> mono, String instanceName);

    <T> Flux<T> applyCircuitBreaker(Flux<T> flux, String instanceName);

    <T> Mono<T> applyRetry(Mono<T> mono, String instanceName);

    <T> Flux<T> applyRetry(Flux<T> flux, String instanceName);

    <T> Mono<T> applyBulkhead(Mono<T> mono, String instanceName);

    <T> Flux<T> applyBulkhead(Flux<T> flux, String instanceName);
}
