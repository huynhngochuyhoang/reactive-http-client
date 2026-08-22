package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class ResilienceOperatorApplierTest {

    // -------------------------------------------------------------------------
    // NoopResilienceOperatorApplier
    // -------------------------------------------------------------------------

    @Nested
    class NoopApplier {

        private final ResilienceOperatorApplier applier = new NoopResilienceOperatorApplier();

        @Test
        void circuitBreakerMono_passesThroughValue() {
            StepVerifier.create(applier.applyCircuitBreaker(Mono.just("ok"), "cb"))
                    .expectNext("ok")
                    .verifyComplete();
        }

        @Test
        void circuitBreakerFlux_passesThroughValues() {
            StepVerifier.create(applier.applyCircuitBreaker(Flux.just(1, 2, 3), "cb"))
                    .expectNext(1, 2, 3)
                    .verifyComplete();
        }

        @Test
        void retryMono_passesThroughValue() {
            StepVerifier.create(applier.applyRetry(Mono.just("ok"), "retry"))
                    .expectNext("ok")
                    .verifyComplete();
        }

        @Test
        void retryFlux_passesThroughValues() {
            StepVerifier.create(applier.applyRetry(Flux.just("a", "b"), "retry"))
                    .expectNext("a", "b")
                    .verifyComplete();
        }

        @Test
        void bulkheadMono_passesThroughValue() {
            StepVerifier.create(applier.applyBulkhead(Mono.just("ok"), "bh"))
                    .expectNext("ok")
                    .verifyComplete();
        }

        @Test
        void bulkheadFlux_passesThroughValues() {
            StepVerifier.create(applier.applyBulkhead(Flux.just("x"), "bh"))
                    .expectNext("x")
                    .verifyComplete();
        }

        @Test
        void rateLimiterMono_passesThroughValue() {
            StepVerifier.create(applier.applyRateLimiter(Mono.just("ok"), "rl"))
                    .expectNext("ok")
                    .verifyComplete();
        }

        @Test
        void rateLimiterFlux_passesThroughValues() {
            StepVerifier.create(applier.applyRateLimiter(Flux.just("x"), "rl"))
                    .expectNext("x")
                    .verifyComplete();
        }

        @Test
        void circuitBreakerMono_propagatesError() {
            StepVerifier.create(applier.applyCircuitBreaker(Mono.error(new RuntimeException("boom")), "cb"))
                    .expectErrorMessage("boom")
                    .verify();
        }

        @Test
        void retryMono_propagatesError() {
            StepVerifier.create(applier.applyRetry(Mono.error(new RuntimeException("fail")), "retry"))
                    .expectErrorMessage("fail")
                    .verify();
        }

        @Test
        void bulkheadMono_propagatesError() {
            StepVerifier.create(applier.applyBulkhead(Mono.error(new RuntimeException("err")), "bh"))
                    .expectErrorMessage("err")
                    .verify();
        }

        @Test
        void rateLimiterMono_propagatesError() {
            StepVerifier.create(applier.applyRateLimiter(Mono.error(new RuntimeException("err")), "rl"))
                    .expectErrorMessage("err")
                    .verify();
        }

        @Test
        void exactNoopReportsEveryOperatorUnavailable() {
            assertThat(ResilienceOperatorApplier.InstanceType.values())
                    .allSatisfy(type -> assertThat(applier.isOperatorAvailable(type)).isFalse());
        }

        @Test
        void subclassApplyOverridesDeclareOnlyImplementedOperatorsAvailable() {
            ResilienceOperatorApplier custom = new NoopResilienceOperatorApplier() {
                @Override
                public <T> Mono<T> applyRetry(Mono<T> mono, String instanceName) {
                    return mono.retry(1);
                }

                @Override
                public <T> Flux<T> applyBulkhead(Flux<T> flux, String instanceName) {
                    return flux;
                }
            };

            assertThat(custom.isOperatorAvailable(ResilienceOperatorApplier.InstanceType.RETRY)).isTrue();
            assertThat(custom.isOperatorAvailable(ResilienceOperatorApplier.InstanceType.BULKHEAD)).isTrue();
            assertThat(custom.isOperatorAvailable(ResilienceOperatorApplier.InstanceType.CIRCUIT_BREAKER)).isFalse();
            assertThat(custom.isOperatorAvailable(ResilienceOperatorApplier.InstanceType.RATE_LIMITER)).isFalse();

            ResilienceOperatorApplier explicitlyUnavailable = new NoopResilienceOperatorApplier() {
                @Override
                public <T> Mono<T> applyRetry(Mono<T> mono, String instanceName) {
                    return mono.retry(1);
                }

                @Override
                public boolean isOperatorAvailable(InstanceType type) {
                    return false;
                }
            };
            assertThat(explicitlyUnavailable.isOperatorAvailable(
                    ResilienceOperatorApplier.InstanceType.RETRY)).isFalse();
        }
    }

    // -------------------------------------------------------------------------
    // Resilience4jOperatorApplier — with registries
    // -------------------------------------------------------------------------

    @Nested
    class Resilience4jApplier {

        private final CircuitBreakerRegistry cbRegistry =
                CircuitBreakerRegistry.of(CircuitBreakerConfig.ofDefaults());
        private final RetryRegistry retryRegistry =
                RetryRegistry.of(RetryConfig.custom().maxAttempts(1).build());
        private final BulkheadRegistry bulkheadRegistry =
                BulkheadRegistry.of(BulkheadConfig.ofDefaults());
        private final RateLimiterRegistry rateLimiterRegistry =
                RateLimiterRegistry.of(RateLimiterConfig.ofDefaults());

        private final ResilienceOperatorApplier applier =
                new Resilience4jOperatorApplier(cbRegistry, retryRegistry, bulkheadRegistry, rateLimiterRegistry);

        @Test
        void circuitBreakerMono_allowsSuccessfulCall() {
            StepVerifier.create(applier.applyCircuitBreaker(Mono.just("ok"), "test"))
                    .expectNext("ok")
                    .verifyComplete();
        }

        @Test
        void circuitBreakerFlux_allowsSuccessfulCall() {
            StepVerifier.create(applier.applyCircuitBreaker(Flux.just(1, 2), "test"))
                    .expectNext(1, 2)
                    .verifyComplete();
        }

        @Test
        void retryMono_allowsSuccessfulCall() {
            StepVerifier.create(applier.applyRetry(Mono.just("ok"), "test"))
                    .expectNext("ok")
                    .verifyComplete();
        }

        @Test
        void retryFlux_allowsSuccessfulCall() {
            StepVerifier.create(applier.applyRetry(Flux.just("a", "b"), "test"))
                    .expectNext("a", "b")
                    .verifyComplete();
        }

        @Test
        void bulkheadMono_allowsSuccessfulCall() {
            StepVerifier.create(applier.applyBulkhead(Mono.just("ok"), "test"))
                    .expectNext("ok")
                    .verifyComplete();
        }

        @Test
        void bulkheadFlux_allowsSuccessfulCall() {
            StepVerifier.create(applier.applyBulkhead(Flux.just("x"), "test"))
                    .expectNext("x")
                    .verifyComplete();
        }

        @Test
        void rateLimiterMono_allowsSuccessfulCall() {
            StepVerifier.create(applier.applyRateLimiter(Mono.just("ok"), "test"))
                    .expectNext("ok")
                    .verifyComplete();
        }

        @Test
        void rateLimiterFlux_allowsSuccessfulCall() {
            StepVerifier.create(applier.applyRateLimiter(Flux.just("x"), "test"))
                    .expectNext("x")
                    .verifyComplete();
        }

        @Test
        void circuitBreakerMono_propagatesErrorAndRecordsFailure() {
            StepVerifier.create(applier.applyCircuitBreaker(
                            Mono.error(new RuntimeException("downstream")), "test"))
                    .expectErrorMessage("downstream")
                    .verify();
        }

        @Test
        void circuitBreakerFlux_propagatesErrorAndRecordsFailure() {
            StepVerifier.create(applier.applyCircuitBreaker(
                            Flux.error(new RuntimeException("downstream")), "test"))
                    .expectErrorMessage("downstream")
                    .verify();
        }

        @Test
        void bulkheadMono_rejectsWhenFull() {
            BulkheadRegistry saturated = BulkheadRegistry.of(
                    BulkheadConfig.custom().maxConcurrentCalls(1).maxWaitDuration(Duration.ZERO).build());
            saturated.bulkhead("full").tryAcquirePermission();

            ResilienceOperatorApplier limited =
                    new Resilience4jOperatorApplier(null, null, saturated, null);

            StepVerifier.create(limited.applyBulkhead(Mono.just("x"), "full"))
                    .expectError(io.github.resilience4j.bulkhead.BulkheadFullException.class)
                    .verify();
        }

        @Test
        void rateLimiterRejectsWhenPermissionUnavailable() {
            RateLimiterRegistry limited = RateLimiterRegistry.of(RateLimiterConfig.custom()
                    .limitForPeriod(1)
                    .limitRefreshPeriod(Duration.ofSeconds(10))
                    .timeoutDuration(Duration.ZERO)
                    .build());
            limited.rateLimiter("limited").acquirePermission();

            ResilienceOperatorApplier applier =
                    new Resilience4jOperatorApplier(null, null, null, limited);

            StepVerifier.create(applier.applyRateLimiter(Mono.just("x"), "limited"))
                    .expectError(io.github.resilience4j.ratelimiter.RequestNotPermitted.class)
                    .verify();
        }
    }

    // -------------------------------------------------------------------------
    // Resilience4jOperatorApplier — null registries fall through
    // -------------------------------------------------------------------------

    @Nested
    class Resilience4jApplierNullRegistries {

        private final ResilienceOperatorApplier applier =
                new Resilience4jOperatorApplier(null, null, null, null);

        @Test
        void circuitBreakerMono_passesThroughWhenRegistryNull() {
            StepVerifier.create(applier.applyCircuitBreaker(Mono.just("ok"), "cb"))
                    .expectNext("ok")
                    .verifyComplete();
        }

        @Test
        void circuitBreakerFlux_passesThroughWhenRegistryNull() {
            StepVerifier.create(applier.applyCircuitBreaker(Flux.just(1), "cb"))
                    .expectNext(1)
                    .verifyComplete();
        }

        @Test
        void retryMono_passesThroughWhenRegistryNull() {
            StepVerifier.create(applier.applyRetry(Mono.just("ok"), "retry"))
                    .expectNext("ok")
                    .verifyComplete();
        }

        @Test
        void retryFlux_passesThroughWhenRegistryNull() {
            StepVerifier.create(applier.applyRetry(Flux.just("a"), "retry"))
                    .expectNext("a")
                    .verifyComplete();
        }

        @Test
        void bulkheadMono_passesThroughWhenRegistryNull() {
            StepVerifier.create(applier.applyBulkhead(Mono.just("ok"), "bh"))
                    .expectNext("ok")
                    .verifyComplete();
        }

        @Test
        void bulkheadFlux_passesThroughWhenRegistryNull() {
            StepVerifier.create(applier.applyBulkhead(Flux.just("x"), "bh"))
                    .expectNext("x")
                    .verifyComplete();
        }

        @Test
        void rateLimiterMono_passesThroughWhenRegistryNull() {
            StepVerifier.create(applier.applyRateLimiter(Mono.just("ok"), "rl"))
                    .expectNext("ok")
                    .verifyComplete();
        }

        @Test
        void rateLimiterFlux_passesThroughWhenRegistryNull() {
            StepVerifier.create(applier.applyRateLimiter(Flux.just("x"), "rl"))
                    .expectNext("x")
                    .verifyComplete();
        }

        @Test
        void nonResistance4jObjectsAreIgnored() {
            ResilienceOperatorApplier withGarbage =
                    new Resilience4jOperatorApplier("not-a-registry", 42, new Object(), "nope");

            StepVerifier.create(withGarbage.applyCircuitBreaker(Mono.just("ok"), "cb"))
                    .expectNext("ok")
                    .verifyComplete();
        }
    }
}
