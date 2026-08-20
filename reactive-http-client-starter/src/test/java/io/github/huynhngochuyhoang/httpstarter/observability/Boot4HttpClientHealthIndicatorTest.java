package io.github.huynhngochuyhoang.httpstarter.observability;

import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import io.github.huynhngochuyhoang.httpstarter.exception.ErrorCategory;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;
import tools.jackson.databind.ObjectMapper;

import java.net.UnknownHostException;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
                .containsEntry("poolAcquireFailureCount", 0L)
                .containsEntry("minSamples", 5L)
                .containsEntry("errorRateThreshold", 0.5d)
                .containsEntry("errorRate", 0.7d)
                .containsEntry("reason", "ERROR_RATE_ABOVE_THRESHOLD");
        assertThat(clientDetails(health, "failing-client").keySet()).containsExactly(
                "samples", "errors", "sampleCount", "errorCount",
                "poolAcquireFailureCount", "minSamples", "errorRateThreshold",
                "errorRate", "status", "reason");
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
    void reportsUpWhenErrorRateEqualsThreshold() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ReactiveHttpClientProperties.ObservabilityConfig config = defaults();
        config.getHealth().setMinSamples(10);
        config.getHealth().setErrorRateThreshold(0.5);
        Boot4HttpClientHealthIndicator indicator = indicator(registry, config);

        record(registry, config, "threshold-client", 5, 5);

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(clientDetails(health, "threshold-client"))
                .containsEntry("sampleCount", 10L)
                .containsEntry("errorCount", 5L)
                .containsEntry("errorRate", 0.5d)
                .containsEntry("status", "UP")
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
    void recreatedMetersStartANewProbeBaseline() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ReactiveHttpClientProperties.ObservabilityConfig config = defaults();
        config.getHealth().setMinSamples(5);
        config.getHealth().setErrorRateThreshold(0.5);
        Boot4HttpClientHealthIndicator indicator = indicator(registry, config);

        record(registry, config, "recreated-client", 3, 7);
        indicator.health();
        registry.clear();
        record(registry, config, "recreated-client", 4, 8);
        Health recreated = indicator.health();

        assertThat(recreated.getStatus()).isEqualTo(Status.DOWN);
        assertThat(clientDetails(recreated, "recreated-client"))
                .containsEntry("sampleCount", 12L)
                .containsEntry("errorCount", 8L)
                .containsEntry("errorRate", 8.0d / 12.0d)
                .containsEntry("reason", "ERROR_RATE_ABOVE_THRESHOLD");
    }

    @Test
    void durationSumMaxAndHistogramDoNotChangeCountBasedHealth() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ReactiveHttpClientProperties.ObservabilityConfig config = defaults();
        config.getHealth().setMinSamples(1);
        Timer timer = Timer.builder(config.getMetricName())
                .tags(
                        "client.name", "slow-client",
                        "error.category", "none",
                        "failure.stage", "none")
                .publishPercentileHistogram()
                .register(registry);
        timer.record(Duration.ofDays(365));

        Health health = indicator(registry, config).health();

        assertThat(timer.count()).isEqualTo(1L);
        assertThat(timer.max(java.util.concurrent.TimeUnit.DAYS)).isEqualTo(365.0d);
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(clientDetails(health, "slow-client"))
                .containsEntry("sampleCount", 1L)
                .containsEntry("errorCount", 0L)
                .containsEntry("status", "UP");
    }

    @Test
    void aggregatesTaggedSeriesOnlyFromConfiguredMetricName() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ReactiveHttpClientProperties.ObservabilityConfig config = defaults();
        config.setMetricName("custom.client.calls");
        config.getHealth().setMinSamples(6);
        config.getHealth().setErrorRateThreshold(0.5);
        MicrometerHttpClientObserver observer = new MicrometerHttpClientObserver(registry, config);

        record(registry, config, "multi-series-client", 3, 3);
        observer.record(new HttpClientObserverEvent(
                "multi-series-client", "other-op", "POST", "/other",
                200, 2L, null, null, null, null));

        ReactiveHttpClientProperties.ObservabilityConfig defaultMetric = defaults();
        record(registry, defaultMetric, "multi-series-client", 0, 10);

        Health health = indicator(registry, config).health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(clientDetails(health, "multi-series-client"))
                .containsEntry("sampleCount", 7L)
                .containsEntry("errorCount", 3L)
                .containsEntry("errorRate", 3.0d / 7.0d)
                .containsEntry("reason", "ERROR_RATE_WITHIN_THRESHOLD");
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
                null, 75L, poolAcquireTimeout(), ErrorCategory.TIMEOUT, null, null,
                1, HttpClientObserverEvent.UNKNOWN_SIZE, HttpClientObserverEvent.UNKNOWN_SIZE,
                null, null, "http://saturated-client/p", Map.of()));

        Health health = indicator(registry, config).health();

        assertThat(clientDetails(health, "saturated-client"))
                .containsEntry("poolAcquireFailureCount", 1L);
        assertThat(health.getDetails().toString())
                .doesNotContain("server.address")
                .doesNotContain("127.0.0.1");
    }

    @Test
    void additivePreResponseStagesRemainGeneralHealthErrors() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ReactiveHttpClientProperties.ObservabilityConfig config = defaults();
        MicrometerHttpClientObserver observer = new MicrometerHttpClientObserver(registry, config);
        observer.record(new HttpClientObserverEvent(
                "dns-client", "op", "GET", "/p",
                null, 25L, new UnknownHostException("missing.invalid"),
                ErrorCategory.UNKNOWN_HOST, null, null));

        Health health = indicator(registry, config).health();

        assertThat(clientDetails(health, "dns-client"))
                .containsEntry("errors", 1L)
                .containsEntry("poolAcquireFailureCount", 0L);
    }

    @Test
    void rendersClientDetailsInDeterministicOrder() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ReactiveHttpClientProperties.ObservabilityConfig config = defaults();
        record(registry, config, "z-client", 1, 0);
        record(registry, config, "a-client", 1, 0);

        Health health = indicator(registry, config).health();

        assertThat(health.getDetails().keySet())
                .containsExactly("a-client", "z-client", "errorRateThreshold", "minSamples");
    }

    @Test
    void rejectsHealthDetailsBeyondDiagnosticsClientAndFieldBounds() {
        SimpleMeterRegistry tooManyClients = new SimpleMeterRegistry();
        ReactiveHttpClientProperties.ObservabilityConfig config = defaults();
        for (int i = 0; i < 257; i++) {
            record(tooManyClients, config, "client-" + i, 1, 0);
        }

        assertThatThrownBy(() -> indicator(tooManyClients, config).health())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("256 client limit");

        SimpleMeterRegistry oversizedName = new SimpleMeterRegistry();
        record(oversizedName, config, "x".repeat(513), 1, 0);

        assertThatThrownBy(() -> indicator(oversizedName, config).health())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("512 character client-name limit");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    @Test
    void maximumHealthDetailSetStaysUnderUtf8ByteLimit() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ReactiveHttpClientProperties.ObservabilityConfig config = defaults();
        for (int i = 0; i < 256; i++) {
            record(registry, config, "界".repeat(500) + String.format("%03d", i), 1, 0);
        }

        Health health = indicator(registry, config).health();

        assertThat(new ObjectMapper().writeValueAsBytes(health.getDetails()).length)
                .isLessThanOrEqualTo(1_048_576);
    }

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
