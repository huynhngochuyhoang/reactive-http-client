package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import org.springframework.util.StringUtils;

import java.util.Locale;

/** Internal, immutable resilience selection shared by every starter surface. */
record EffectiveResiliencePolicy(
        Operator retry,
        Operator rateLimiter,
        Operator circuitBreaker,
        Operator bulkhead
) {

    static EffectiveResiliencePolicy resolve(
            RequestPlan plan,
            String httpMethod,
            ReactiveHttpClientProperties.ResilienceConfig resilience,
            OperatorAvailability availability) {
        boolean enabled = resilience != null && resilience.isEnabled();
        return new EffectiveResiliencePolicy(
                operator(
                        ResilienceOperatorApplier.InstanceType.RETRY,
                        enabled && retryEligible(resilience, httpMethod),
                        plan.retryInstanceName(),
                        resilience != null ? resilience.getRetry() : null,
                        availability),
                operator(
                        ResilienceOperatorApplier.InstanceType.RATE_LIMITER,
                        enabled,
                        plan.rateLimiterInstanceName(),
                        resilience != null ? resilience.getRateLimiter() : null,
                        availability),
                operator(
                        ResilienceOperatorApplier.InstanceType.CIRCUIT_BREAKER,
                        enabled,
                        plan.circuitBreakerInstanceName(),
                        resilience != null ? resilience.getCircuitBreaker() : null,
                        availability),
                operator(
                        ResilienceOperatorApplier.InstanceType.BULKHEAD,
                        enabled,
                        plan.bulkheadInstanceName(),
                        resilience != null ? resilience.getBulkhead() : null,
                        availability));
    }

    static OperatorAvailability availability(ResilienceOperatorApplier applier) {
        return applier == null ? type -> true : applier::isOperatorAvailable;
    }

    private static Operator operator(
            ResilienceOperatorApplier.InstanceType type,
            boolean eligible,
            String methodLevel,
            String clientLevel,
            OperatorAvailability availability) {
        if (!eligible) {
            return Operator.disabled(type);
        }
        Source source;
        String instanceName;
        if (StringUtils.hasText(methodLevel)) {
            source = Source.METHOD;
            instanceName = methodLevel;
        } else if (StringUtils.hasText(clientLevel)) {
            source = Source.CLIENT;
            instanceName = clientLevel;
        } else {
            return Operator.disabled(type);
        }
        Boolean available = availability.isAvailable(type);
        State state = available == null
                ? State.UNKNOWN
                : available ? State.ACTIVE : State.SELECTED_UNAVAILABLE;
        return new Operator(type, instanceName, source, state);
    }

    private static boolean retryEligible(
            ReactiveHttpClientProperties.ResilienceConfig resilience,
            String httpMethod) {
        return resilience != null
                && httpMethod != null
                && resilience.getRetryMethods() != null
                && resilience.getRetryMethods().contains(httpMethod.toUpperCase(Locale.ROOT));
    }

    @FunctionalInterface
    interface OperatorAvailability {
        /** {@code null} means availability cannot be proven without creating a lazy component. */
        Boolean isAvailable(ResilienceOperatorApplier.InstanceType type);
    }

    enum Source {
        NONE,
        METHOD,
        CLIENT
    }

    enum State {
        DISABLED,
        SELECTED_UNAVAILABLE,
        ACTIVE,
        UNKNOWN
    }

    record Operator(
            ResilienceOperatorApplier.InstanceType type,
            String instanceName,
            Source source,
            State state
    ) {
        static Operator disabled(ResilienceOperatorApplier.InstanceType type) {
            return new Operator(type, null, Source.NONE, State.DISABLED);
        }

        boolean active() {
            return state == State.ACTIVE;
        }

        String diagnosticValue() {
            return switch (state) {
                case DISABLED -> "disabled";
                case SELECTED_UNAVAILABLE -> "unavailable";
                case ACTIVE -> instanceName;
                case UNKNOWN -> "unknown";
            };
        }
    }
}
