package io.github.huynhngochuyhoang.httpstarter.observability;

import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import io.github.huynhngochuyhoang.httpstarter.exception.ErrorCategory;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link Boot4HttpClientHealthIndicator}. Uses a {@link SimpleMeterRegistry}
 * plus the real {@link MicrometerHttpClientObserver} to seed realistic
 * {@code reactive.http.client.requests} meters, then probes {@code health()} and asserts
 * on the probe-to-probe delta semantics.
 */
class Boot4HttpClientHealthIndicatorTest {

    @Test
    void reportsUpAndNoSamplesOnFirstProbeWithEmptyRegistry() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        Boot4HttpClientHealthIndicator indicator = indicator(registry, defaults());

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails())
                .containsEntry("errorRateThreshold", 0.5d)
                .containsEntry("minSamples", 10L);
    }

    @Test
    void reportsDownWhenErrorRateExceedsThreshold() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ReactiveHttpClientProperties.ObservabilityConfig config = defaults();
        config.getHealth().setMinSamples(5);
        config.getHealth().setErrorRateThreshold(0.5);
        Boot4HttpClientHealthIndicator indicator = indicator(registry, config);

        // Seed 3 successes + 7 errors = 70% error rate
        record(registry, config, "failing-client", 3, 7);

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails().toString())
                .doesNotContain("/p")
                .doesNotContain("GET")
                .doesNotContain("boom");
        assertThat(clientDetails(health, "failing-client"))
                .containsEntry("status", "DOWN")
                .containsEntry("samples", 10L)
                .containsEntry("errors", 7L)
                .containsEntry("sampleCount", 10L)
                .containsEntry("errorCount", 7L)
                .containsEntry("minSamples", 5L)
                .containsEntry("errorRateThreshold", 0.5d)
                .containsEntry("errorRate", 0.7d)
                .containsEntry("reason", "ERROR_RATE_ABOVE_THRESHOLD");
    }

    @Test
    void reportsUpWhenErrorRateBelowThreshold() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ReactiveHttpClientProperties.ObservabilityConfig config = defaults();
        config.getHealth().setMinSamples(5);
        config.getHealth().setErrorRateThreshold(0.5);
        Boot4HttpClientHealthIndicator indicator = indicator(registry, config);

        record(registry, config, "healthy-client", 9, 1); // 10% error rate

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(clientDetails(health, "healthy-client"))
                .containsEntry("status", "UP")
                .containsEntry("samples", 10L)
                .containsEntry("errors", 1L)
                .containsEntry("sampleCount", 10L)
                .containsEntry("errorCount", 1L)
                .containsEntry("minSamples", 5L)
                .containsEntry("errorRateThreshold", 0.5d)
                .containsEntry("errorRate", 0.1d)
                .containsEntry("reason", "ERROR_RATE_WITHIN_THRESHOLD");
    }

    @Test
    void insufficientSamplesDoesNotFailHealth() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ReactiveHttpClientProperties.ObservabilityConfig config = defaults();
        config.getHealth().setMinSamples(10);
        Boot4HttpClientHealthIndicator indicator = indicator(registry, config);

        // 2 errors out of 2 invocations is 100%, but below minSamples
        record(registry, config, "quiet-client", 0, 2);

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(clientDetails(health, "quiet-client"))
                .containsEntry("status", "INSUFFICIENT_SAMPLES")
                .containsEntry("samples", 2L)
                .containsEntry("errors", 2L)
                .containsEntry("sampleCount", 2L)
                .containsEntry("errorCount", 2L)
                .containsEntry("minSamples", 10L)
                .containsEntry("errorRateThreshold", 0.5d)
                .containsEntry("errorRate", 1.0d)
                .containsEntry("reason", "INSUFFICIENT_SAMPLES");
    }

    @Test
    void reportsNoSamplesWhenClientHasNoDeltaSincePreviousProbe() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ReactiveHttpClientProperties.ObservabilityConfig config = defaults();
        config.getHealth().setMinSamples(5);
        Boot4HttpClientHealthIndicator indicator = indicator(registry, config);

        record(registry, config, "idle-client", 3, 0);
        indicator.health();

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(clientDetails(health, "idle-client"))
                .containsEntry("status", "INSUFFICIENT_SAMPLES")
                .containsEntry("samples", 0L)
                .containsEntry("errors", 0L)
                .containsEntry("sampleCount", 0L)
                .containsEntry("errorCount", 0L)
                .containsEntry("minSamples", 5L)
                .containsEntry("errorRateThreshold", 0.5d)
                .containsEntry("reason", "NO_SAMPLES")
                .doesNotContainKey("errorRate");
    }

    @Test
    void windowIsProbeToProbeDelta() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ReactiveHttpClientProperties.ObservabilityConfig config = defaults();
        config.getHealth().setMinSamples(5);
        config.getHealth().setErrorRateThreshold(0.5);
        Boot4HttpClientHealthIndicator indicator = indicator(registry, config);

        // Window 1: 10 successful calls — baseline that should NOT count toward probe #2
        record(registry, config, "flaky-client", 10, 0);
        Health first = indicator.health();
        assertThat(first.getStatus()).isEqualTo(Status.UP);

        // Window 2: 2 successes + 8 errors since last probe — rolling window is 80% errors
        record(registry, config, "flaky-client", 2, 8);
        Health second = indicator.health();

        assertThat(second.getStatus())
                .as("rolling window should consider only calls since the previous probe")
                .isEqualTo(Status.DOWN);
        assertThat(clientDetails(second, "flaky-client"))
                .containsEntry("samples", 10L)
                .containsEntry("errors", 8L)
                .containsEntry("errorRate", 0.8d)
                .containsEntry("reason", "ERROR_RATE_ABOVE_THRESHOLD");
    }

    @Test
    void degradedClientTurnsOverallStatusDownEvenIfOthersAreHealthy() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ReactiveHttpClientProperties.ObservabilityConfig config = defaults();
        config.getHealth().setMinSamples(5);
        config.getHealth().setErrorRateThreshold(0.5);
        Boot4HttpClientHealthIndicator indicator = indicator(registry, config);

        record(registry, config, "healthy-client", 10, 0);
        record(registry, config, "broken-client", 2, 8);

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(clientDetails(health, "healthy-client"))
                .containsEntry("status", "UP")
                .containsEntry("reason", "ERROR_RATE_WITHIN_THRESHOLD");
        assertThat(clientDetails(health, "broken-client"))
                .containsEntry("status", "DOWN")
                .containsEntry("reason", "ERROR_RATE_ABOVE_THRESHOLD");
    }

    @Test
    void exposesPoolAcquireFailureCountWithoutRemoteAddressDetails() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ReactiveHttpClientProperties.ObservabilityConfig config = defaults();
        MicrometerHttpClientObserver observer = new MicrometerHttpClientObserver(registry, config);
        observer.record(new HttpClientObserverEvent(
                "saturated-client", "op", "GET", "/p",
                null, 75L, poolAcquireTimeout(), ErrorCategory.TIMEOUT, null, null));

        Health health = indicator(registry, config).health();

        assertThat(clientDetails(health, "saturated-client"))
                .containsEntry("poolAcquireFailureCount", 1L);
        assertThat(health.getDetails().toString())
                .doesNotContain("server.address")
                .doesNotContain("127.0.0.1");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static Boot4HttpClientHealthIndicator indicator(
            SimpleMeterRegistry registry,
            ReactiveHttpClientProperties.ObservabilityConfig config) {
        return new Boot4HttpClientHealthIndicator(registry, config);
    }

    private static ReactiveHttpClientProperties.ObservabilityConfig defaults() {
        return new ReactiveHttpClientProperties.ObservabilityConfig();
    }

    private static void record(SimpleMeterRegistry registry,
                               ReactiveHttpClientProperties.ObservabilityConfig config,
                               String clientName,
                               int successCount,
                               int errorCount) {
        MicrometerHttpClientObserver observer = new MicrometerHttpClientObserver(registry, config);
        for (int i = 0; i < successCount; i++) {
            observer.record(new HttpClientObserverEvent(
                    clientName, "op", "GET", "/p",
                    200, 1L, null, null, null, null,
                    1, HttpClientObserverEvent.UNKNOWN_SIZE, HttpClientObserverEvent.UNKNOWN_SIZE));
        }
        for (int i = 0; i < errorCount; i++) {
            observer.record(new HttpClientObserverEvent(
                    clientName, "op", "GET", "/p",
                    500, 1L, new RuntimeException("boom"), ErrorCategory.SERVER_ERROR, null, null,
                    1, HttpClientObserverEvent.UNKNOWN_SIZE, HttpClientObserverEvent.UNKNOWN_SIZE));
        }
    }

    private static Throwable poolAcquireTimeout() {
        try {
            Class<?> type = Class.forName(
                    "reactor.netty.internal.shaded.reactor.pool.PoolAcquireTimeoutException");
            return (Throwable) type.getConstructor(java.time.Duration.class)
                    .newInstance(java.time.Duration.ofMillis(75));
        }
        catch (ReflectiveOperationException ex) {
            throw new AssertionError(ex);
        }
    }

    @SuppressWarnings("unchecked")
    private static java.util.Map<String, Object> clientDetails(Health health, String clientName) {
        Object details = health.getDetails().get(clientName);
        assertThat(details)
                .as("expected per-client details for " + clientName)
                .isInstanceOf(java.util.Map.class);
        return (java.util.Map<String, Object>) details;
    }
}
