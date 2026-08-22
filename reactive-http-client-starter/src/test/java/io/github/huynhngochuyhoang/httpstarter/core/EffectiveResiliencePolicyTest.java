package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.annotation.Bulkhead;
import io.github.huynhngochuyhoang.httpstarter.annotation.GET;
import io.github.huynhngochuyhoang.httpstarter.annotation.Retry;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class EffectiveResiliencePolicyTest {

    private final MethodMetadataCache metadataCache = new MethodMetadataCache();

    @Test
    void enabledOnlyResolvesEveryOperatorAsDisabledWithoutAvailabilityLookups() throws Exception {
        ReactiveHttpClientProperties.ResilienceConfig resilience = enabledResilience();
        List<ResilienceOperatorApplier.InstanceType> lookups = new ArrayList<>();

        EffectiveResiliencePolicy policy = resolve("plain", "GET", resilience, type -> {
            lookups.add(type);
            return true;
        });

        assertThat(policy.retry().state()).isEqualTo(EffectiveResiliencePolicy.State.DISABLED);
        assertThat(policy.rateLimiter().state()).isEqualTo(EffectiveResiliencePolicy.State.DISABLED);
        assertThat(policy.circuitBreaker().state()).isEqualTo(EffectiveResiliencePolicy.State.DISABLED);
        assertThat(policy.bulkhead().state()).isEqualTo(EffectiveResiliencePolicy.State.DISABLED);
        assertThat(lookups).isEmpty();
    }

    @Test
    void resolvesMethodPrecedenceBlankValuesRetryEligibilityAndAvailabilityOnce() throws Exception {
        ReactiveHttpClientProperties.ResilienceConfig resilience = enabledResilience();
        resilience.setRetry("client-retry");
        resilience.setRateLimiter(" ");
        resilience.setCircuitBreaker("client-circuit");
        resilience.setBulkhead("client-bulkhead");
        resilience.setRetryMethods(Set.of("GET"));
        List<ResilienceOperatorApplier.InstanceType> lookups = new ArrayList<>();

        EffectiveResiliencePolicy policy = resolve("overrides", "GET", resilience, type -> {
            lookups.add(type);
            return switch (type) {
                case RETRY, CIRCUIT_BREAKER -> true;
                case BULKHEAD -> null;
                case RATE_LIMITER -> false;
            };
        });

        assertThat(policy.retry()).satisfies(operator -> {
            assertThat(operator.instanceName()).isEqualTo("method-retry");
            assertThat(operator.source()).isEqualTo(EffectiveResiliencePolicy.Source.METHOD);
            assertThat(operator.state()).isEqualTo(EffectiveResiliencePolicy.State.ACTIVE);
        });
        assertThat(policy.rateLimiter().state()).isEqualTo(EffectiveResiliencePolicy.State.DISABLED);
        assertThat(policy.circuitBreaker()).satisfies(operator -> {
            assertThat(operator.instanceName()).isEqualTo("client-circuit");
            assertThat(operator.source()).isEqualTo(EffectiveResiliencePolicy.Source.CLIENT);
            assertThat(operator.state()).isEqualTo(EffectiveResiliencePolicy.State.ACTIVE);
        });
        assertThat(policy.bulkhead()).satisfies(operator -> {
            assertThat(operator.instanceName()).isEqualTo("method-bulkhead");
            assertThat(operator.state()).isEqualTo(EffectiveResiliencePolicy.State.UNKNOWN);
            assertThat(operator.diagnosticValue()).isEqualTo("unknown");
        });
        assertThat(lookups).containsExactly(
                ResilienceOperatorApplier.InstanceType.RETRY,
                ResilienceOperatorApplier.InstanceType.CIRCUIT_BREAKER,
                ResilienceOperatorApplier.InstanceType.BULKHEAD);

        EffectiveResiliencePolicy ineligible = resolve("overrides", "POST", resilience, type -> {
            if (type == ResilienceOperatorApplier.InstanceType.RETRY) {
                throw new AssertionError("ineligible Retry must not query availability");
            }
            return true;
        });
        assertThat(ineligible.retry().state()).isEqualTo(EffectiveResiliencePolicy.State.DISABLED);
    }

    private EffectiveResiliencePolicy resolve(
            String methodName,
            String httpMethod,
            ReactiveHttpClientProperties.ResilienceConfig resilience,
            EffectiveResiliencePolicy.OperatorAvailability availability) throws Exception {
        Method method = PolicyClient.class.getDeclaredMethod(methodName);
        RequestPlan plan = RequestPlan.from(metadataCache.get(method), PolicyClient.class);
        return EffectiveResiliencePolicy.resolve(plan, httpMethod, resilience, availability);
    }

    private static ReactiveHttpClientProperties.ResilienceConfig enabledResilience() {
        ReactiveHttpClientProperties.ResilienceConfig resilience =
                new ReactiveHttpClientProperties.ResilienceConfig();
        resilience.setEnabled(true);
        return resilience;
    }

    interface PolicyClient {
        @GET("/plain")
        Mono<String> plain();

        @GET("/overrides")
        @Retry("method-retry")
        @Bulkhead("method-bulkhead")
        Mono<String> overrides();
    }
}
