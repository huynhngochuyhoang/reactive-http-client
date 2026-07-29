package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

public class Resilience4jRateLimiterOperatorAdapter implements RateLimiterOperatorAdapter {

    private final RateLimiterRegistry rateLimiterRegistry;
    private final ConcurrentHashMap<String, RateLimiter> rateLimiters = new ConcurrentHashMap<>();

    public Resilience4jRateLimiterOperatorAdapter(Object rateLimiterRegistry) {
        this.rateLimiterRegistry = rateLimiterRegistry instanceof RateLimiterRegistry registry ? registry : null;
    }

    @Override
    public <T> Mono<T> apply(Mono<T> mono, String instanceName) {
        if (rateLimiterRegistry == null) {
            return mono;
        }
        return Mono.defer(() -> {
            RateLimiter rateLimiter = rateLimiter(instanceName);
            long waitDuration = rateLimiter.reservePermission();
            if (waitDuration < 0) {
                return Mono.error(RequestNotPermitted.createRequestNotPermitted(rateLimiter));
            }
            Mono<T> admitted = waitDuration > 0
                    ? Mono.delay(Duration.ofNanos(waitDuration)).then(mono)
                    : mono;
            return admitted
                    .doOnNext(rateLimiter::onResult)
                    .doOnError(rateLimiter::onError);
        });
    }

    @Override
    public <T> Flux<T> apply(Flux<T> flux, String instanceName) {
        if (rateLimiterRegistry == null) {
            return flux;
        }
        return Flux.defer(() -> {
            RateLimiter rateLimiter = rateLimiter(instanceName);
            long waitDuration = rateLimiter.reservePermission();
            if (waitDuration < 0) {
                return Flux.error(RequestNotPermitted.createRequestNotPermitted(rateLimiter));
            }
            Flux<T> admitted = waitDuration > 0
                    ? Mono.delay(Duration.ofNanos(waitDuration)).thenMany(flux)
                    : flux;
            return admitted
                    .doOnNext(rateLimiter::onResult)
                    .doOnError(rateLimiter::onError);
        });
    }

    private RateLimiter rateLimiter(String instanceName) {
        return rateLimiters.computeIfAbsent(instanceName, rateLimiterRegistry::rateLimiter);
    }

    @Override
    public boolean isInstanceConfigured(String instanceName) {
        return rateLimiterRegistry == null || rateLimiterRegistry.find(instanceName).isPresent();
    }
}
