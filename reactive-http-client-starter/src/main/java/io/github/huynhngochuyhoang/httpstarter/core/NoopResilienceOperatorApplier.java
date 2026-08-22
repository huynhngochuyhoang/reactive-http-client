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
 * that operator for the matching Mono or Flux publisher shape unless they
 * override {@link #isOperatorAvailable(InstanceType)} explicitly. This
 * preserves lightweight custom appliers used by test helpers without claiming
 * that a Mono-only override also applies to Flux. Inferred Retry availability
 * does not imply duplicate-attempt capacity; subclasses that model retries
 * must override {@link #canRetryMoreThanOnce(String)} explicitly.
 */
public class NoopResilienceOperatorApplier implements ResilienceOperatorApplier {

    private static final ClassValue<OverrideContract> OVERRIDE_CONTRACTS = new ClassValue<>() {
        @Override
        protected OverrideContract computeValue(Class<?> implementation) {
            EnumSet<InstanceType> monoOperators = EnumSet.noneOf(InstanceType.class);
            EnumSet<InstanceType> fluxOperators = EnumSet.noneOf(InstanceType.class);
            for (InstanceType type : InstanceType.values()) {
                if (overridesApplyMethod(implementation, type, Mono.class)) {
                    monoOperators.add(type);
                }
                if (overridesApplyMethod(implementation, type, Flux.class)) {
                    fluxOperators.add(type);
                }
            }
            return new OverrideContract(
                    overridesAvailabilityMethod(implementation), monoOperators, fluxOperators);
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
        if (type == null || getClass() == NoopResilienceOperatorApplier.class) {
            return false;
        }
        OverrideContract contract = OVERRIDE_CONTRACTS.get(getClass());
        return contract.monoOperators().contains(type) || contract.fluxOperators().contains(type);
    }

    @Override
    public boolean canRetryMoreThanOnce(String instanceName) {
        return false;
    }

    boolean isOperatorAvailable(
            InstanceType type,
            EffectiveResiliencePolicy.PublisherShape publisherShape) {
        if (type == null || publisherShape == null || getClass() == NoopResilienceOperatorApplier.class) {
            return false;
        }
        OverrideContract contract = OVERRIDE_CONTRACTS.get(getClass());
        if (contract.explicitAvailability()) {
            return isOperatorAvailable(type);
        }
        return contract.operators(publisherShape).contains(type);
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

    private static boolean overridesAvailabilityMethod(Class<?> implementation) {
        try {
            return implementation.getMethod("isOperatorAvailable", InstanceType.class)
                    .getDeclaringClass() != NoopResilienceOperatorApplier.class;
        } catch (NoSuchMethodException error) {
            throw new IllegalStateException("Resilience availability contract is incomplete", error);
        }
    }

    private record OverrideContract(
            boolean explicitAvailability,
            Set<InstanceType> monoOperators,
            Set<InstanceType> fluxOperators) {

        Set<InstanceType> operators(EffectiveResiliencePolicy.PublisherShape publisherShape) {
            return publisherShape == EffectiveResiliencePolicy.PublisherShape.FLUX
                    ? fluxOperators
                    : monoOperators;
        }
    }
}
