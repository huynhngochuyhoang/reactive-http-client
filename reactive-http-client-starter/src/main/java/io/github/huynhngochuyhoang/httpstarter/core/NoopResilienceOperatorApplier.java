package io.github.huynhngochuyhoang.httpstarter.core;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.EnumSet;
import java.util.Set;

/**
 * No-op resilience applier used when Resilience4j is unavailable.
 *
 * <p>The exact no-op implementation reports every operator as unavailable.
 * Subclasses that override an {@code apply*} method are treated as providing
 * that operator unless they override {@link #isOperatorAvailable(InstanceType)}
 * explicitly. This preserves lightweight custom appliers used by test helpers.
 */
public class NoopResilienceOperatorApplier implements ResilienceOperatorApplier {

    private static final ClassValue<Set<InstanceType>> OVERRIDDEN_OPERATORS = new ClassValue<>() {
        @Override
        protected Set<InstanceType> computeValue(Class<?> implementation) {
            EnumSet<InstanceType> operators = EnumSet.noneOf(InstanceType.class);
            for (InstanceType type : InstanceType.values()) {
                if (overridesApplyMethod(implementation, type, Mono.class)
                        || overridesApplyMethod(implementation, type, Flux.class)) {
                    operators.add(type);
                }
            }
            return operators;
        }
    };

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

    @Override
    public <T> Mono<T> applyRateLimiter(Mono<T> mono, String instanceName) {
        return mono;
    }

    @Override
    public <T> Flux<T> applyRateLimiter(Flux<T> flux, String instanceName) {
        return flux;
    }
    @Override
    public boolean isOperatorAvailable(InstanceType type) {
        return type != null
                && getClass() != NoopResilienceOperatorApplier.class
                && OVERRIDDEN_OPERATORS.get(getClass()).contains(type);
    }

    private static boolean overridesApplyMethod(
            Class<?> implementation, InstanceType type, Class<?> publisherType) {
        try {
            return implementation.getMethod(applyMethodName(type), publisherType, String.class)
                    .getDeclaringClass() != NoopResilienceOperatorApplier.class;
        } catch (NoSuchMethodException error) {
            throw new IllegalStateException("Resilience operator method contract is incomplete", error);
        }
    }

    private static String applyMethodName(InstanceType type) {
        return switch (type) {
            case RETRY -> "applyRetry";
            case CIRCUIT_BREAKER -> "applyCircuitBreaker";
            case BULKHEAD -> "applyBulkhead";
            case RATE_LIMITER -> "applyRateLimiter";
        };
    }
}
