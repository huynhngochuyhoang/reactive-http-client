package io.github.huynhngochuyhoang.httpstarter.observability;

import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import io.github.huynhngochuyhoang.httpstarter.exception.ErrorCategory;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import io.netty.handler.timeout.ReadTimeoutException;
import io.prometheus.metrics.model.registry.PrometheusRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CancellationException;

import static org.assertj.core.api.Assertions.assertThat;

class MicrometerPrometheusExportContractTest {

    private static final String METRIC_NAME = "reactive.http.client.requests";
    private static final String PROMETHEUS_NAME = "reactive_http_client_requests";

    @Test
    void mainTimerScrapeUsesSecondsForEveryTerminalOutcome() {
        PrometheusMeterRegistry registry = registry(PrometheusConfig.DEFAULT, new MockClock());
        MicrometerHttpClientObserver observer = observer(registry, defaults());

        record(observer, "success", 200, 125, null, null, 1);
        record(observer, "http-error", 503, 250,
                new RuntimeException("server error"), ErrorCategory.SERVER_ERROR, 1);
        record(observer, "timeout", null, 400,
                ReadTimeoutException.INSTANCE, ErrorCategory.TIMEOUT, 1);
        record(observer, "cancelled", null, 10,
                new CancellationException("cancelled"), ErrorCategory.CANCELLED, 1);
        record(observer, "open-circuit", null, 2,
                CallNotPermittedException.createCallNotPermittedException(
                        CircuitBreaker.ofDefaults("prometheus-contract")),
                ErrorCategory.RESILIENCE_ERROR, 0);

        String scrape = registry.scrape();
        assertTimer(scrape, "success", "200", "SUCCESS", "none", "none", 0.125);
        assertTimer(scrape, "http-error", "503", "SERVER_ERROR", "RuntimeException", "SERVER_ERROR", 0.250);
        assertTimer(scrape, "timeout", "NONE", "UNKNOWN", "ReadTimeoutException", "TIMEOUT", 0.400);
        assertTimer(scrape, "cancelled", "NONE", "UNKNOWN", "CancellationException", "CANCELLED", 0.010);
        assertTimer(scrape, "open-circuit", "NONE", "UNKNOWN",
                "CallNotPermittedException", "RESILIENCE_ERROR", 0.002);

        assertThat(sample(scrape, PROMETHEUS_NAME + "_attempts_count", labels("open-circuit")))
                .isEqualTo(1.0);
        assertThat(sample(scrape, PROMETHEUS_NAME + "_attempts_sum", labels("open-circuit")))
                .isZero();
        assertThat(scrape).doesNotContain(PROMETHEUS_NAME + "_latency_seconds");
    }

    @Test
    void latencyHistogramExportsConfiguredBucketsWithOnlyCommonTags() {
        PrometheusMeterRegistry registry = registry(PrometheusConfig.DEFAULT, new MockClock());
        ReactiveHttpClientProperties.ObservabilityConfig config = defaults();
        config.getHistogram().setEnabled(true);
        config.getHistogram().setSloBoundariesMs(java.util.List.of(50L, 100L, 250L));
        MicrometerHttpClientObserver observer = observer(registry, config);

        record(observer, "histogram", 503, 75,
                new RuntimeException("server error"), ErrorCategory.SERVER_ERROR, 1);

        String scrape = registry.scrape();
        Map<String, String> labels = labels("histogram");
        assertThat(sample(scrape, PROMETHEUS_NAME + "_latency_seconds_bucket",
                withLabel(labels, "le", "0.05"))).isZero();
        assertThat(sample(scrape, PROMETHEUS_NAME + "_latency_seconds_bucket",
                withLabel(labels, "le", "0.1"))).isEqualTo(1.0);
        assertThat(sample(scrape, PROMETHEUS_NAME + "_latency_seconds_bucket",
                withLabel(labels, "le", "0.25"))).isEqualTo(1.0);
        assertThat(sample(scrape, PROMETHEUS_NAME + "_latency_seconds_count", labels))
                .isEqualTo(1.0);
        assertThat(sample(scrape, PROMETHEUS_NAME + "_latency_seconds_sum", labels))
                .isEqualTo(0.075);

        scrape.lines()
                .filter(line -> line.startsWith(PROMETHEUS_NAME + "_latency_seconds"))
                .forEach(line -> assertThat(line)
                        .doesNotContain("http_status_code=")
                        .doesNotContain("outcome=")
                        .doesNotContain("exception=")
                        .doesNotContain("error_category=")
                        .doesNotContain("failure_stage="));

        assertThat(sample(scrape, PROMETHEUS_NAME + "_seconds_count",
                withLabel(labels, "http_status_code", "503"))).isEqualTo(1.0);
    }

    @Test
    void timerMaximumExpiresWhileCumulativeCountAndSumRemain() {
        Duration step = Duration.ofSeconds(1);
        MockClock clock = new MockClock();
        PrometheusConfig prometheusConfig = new PrometheusConfig() {
            @Override
            public String get(String key) {
                return null;
            }

            @Override
            public Duration step() {
                return step;
            }
        };
        PrometheusMeterRegistry registry = registry(prometheusConfig, clock);
        MicrometerHttpClientObserver observer = observer(registry, defaults());

        record(observer, "expiring-max", 200, 2_000, null, null, 1);
        Map<String, String> labels = labels("expiring-max");
        assertThat(sample(registry.scrape(), PROMETHEUS_NAME + "_seconds_max", labels))
                .isEqualTo(2.0);

        clock.add(step.multipliedBy(3));
        String afterExpiry = registry.scrape();
        assertThat(sample(afterExpiry, PROMETHEUS_NAME + "_seconds_max", labels)).isZero();
        assertThat(sample(afterExpiry, PROMETHEUS_NAME + "_seconds_count", labels)).isEqualTo(1.0);
        assertThat(sample(afterExpiry, PROMETHEUS_NAME + "_seconds_sum", labels)).isEqualTo(2.0);
    }

    @Test
    void attemptsSummaryHasNoDefaultPercentilesOrHistogram() {
        PrometheusMeterRegistry registry = registry(PrometheusConfig.DEFAULT, new MockClock());
        MicrometerHttpClientObserver observer = observer(registry, defaults());

        record(observer, "attempts", null, 1,
                CallNotPermittedException.createCallNotPermittedException(
                        CircuitBreaker.ofDefaults("attempts-contract")),
                ErrorCategory.RESILIENCE_ERROR, 0);
        record(observer, "attempts", 200, 2, null, null, 1);
        record(observer, "attempts", 200, 3, null, null, 2);

        DistributionSummary attempts = registry.find(METRIC_NAME + ".attempts")
                .tag("api.name", "attempts")
                .summary();
        assertThat(attempts).isNotNull();
        assertThat(attempts.count()).isEqualTo(3);
        assertThat(attempts.totalAmount()).isEqualTo(3.0);
        assertThat(attempts.takeSnapshot().percentileValues()).isEmpty();
        assertThat(attempts.takeSnapshot().histogramCounts()).isEmpty();

        String scrape = registry.scrape();
        assertThat(sample(scrape, PROMETHEUS_NAME + "_attempts_count", labels("attempts")))
                .isEqualTo(3.0);
        assertThat(sample(scrape, PROMETHEUS_NAME + "_attempts_sum", labels("attempts")))
                .isEqualTo(3.0);
        assertThat(sample(scrape, PROMETHEUS_NAME + "_attempts_max", labels("attempts")))
                .isEqualTo(2.0);
        assertThat(scrape.lines()
                .filter(line -> line.startsWith(PROMETHEUS_NAME + "_attempts")))
                .noneMatch(line -> line.contains("quantile=") || line.contains("_bucket"));
    }

    @Test
    void conditionalMetersAndCustomMetricNameStayAlignedWithHealth() {
        PrometheusMeterRegistry registry = registry(PrometheusConfig.DEFAULT, new MockClock());
        ReactiveHttpClientProperties.ObservabilityConfig config = defaults();
        config.setMetricName("custom.client.calls");
        MicrometerHttpClientObserver observer = observer(registry, config);

        observer.record(event("custom", 200, 4, null, null, 1,
                HttpClientObserverEvent.UNKNOWN_SIZE, HttpClientObserverEvent.UNKNOWN_SIZE));

        assertThat(registry.find("custom.client.calls").timer()).isNotNull();
        assertThat(registry.find("custom.client.calls.attempts").summary()).isNotNull();
        assertThat(registry.find("custom.client.calls.request.size").summary()).isNull();
        assertThat(registry.find("custom.client.calls.response.size").summary()).isNull();
        assertThat(registry.find("custom.client.calls.latency").timer()).isNull();

        observer.record(event("custom", 200, 5, null, null, 1, 0, 0));
        assertThat(registry.find("custom.client.calls.request.size").summary().count()).isEqualTo(1);
        assertThat(registry.find("custom.client.calls.request.size").summary().totalAmount()).isZero();
        assertThat(registry.find("custom.client.calls.response.size").summary().count()).isEqualTo(1);
        assertThat(registry.find("custom.client.calls.response.size").summary().totalAmount()).isZero();

        assertThat(new Boot4HttpClientHealthIndicator(registry, config).health().getDetails())
                .containsKey("metrics-client");
    }

    private static void assertTimer(String scrape,
                                    String apiName,
                                    String status,
                                    String outcome,
                                    String exception,
                                    String category,
                                    double seconds) {
        Map<String, String> labels = withLabel(labels(apiName), "http_status_code", status);
        labels = withLabel(labels, "outcome", outcome);
        labels = withLabel(labels, "exception", exception);
        labels = withLabel(labels, "error_category", category);
        assertThat(sample(scrape, PROMETHEUS_NAME + "_seconds_count", labels)).isEqualTo(1.0);
        assertThat(sample(scrape, PROMETHEUS_NAME + "_seconds_sum", labels)).isEqualTo(seconds);
        assertThat(sample(scrape, PROMETHEUS_NAME + "_seconds_max", labels)).isEqualTo(seconds);
        assertThat(seconds).isBetween(0.0, 60.0);
    }

    private static void record(MicrometerHttpClientObserver observer,
                               String apiName,
                               Integer status,
                               long durationMs,
                               Throwable error,
                               ErrorCategory category,
                               int attempts) {
        observer.record(event(apiName, status, durationMs, error, category, attempts,
                HttpClientObserverEvent.UNKNOWN_SIZE, HttpClientObserverEvent.UNKNOWN_SIZE));
    }

    private static HttpClientObserverEvent event(String apiName,
                                                 Integer status,
                                                 long durationMs,
                                                 Throwable error,
                                                 ErrorCategory category,
                                                 int attempts,
                                                 long requestBytes,
                                                 long responseBytes) {
        return new HttpClientObserverEvent(
                "metrics-client", apiName, "GET", "/items/{id}",
                status, durationMs, error, category, null, null,
                attempts, requestBytes, responseBytes, null, null);
    }

    private static MicrometerHttpClientObserver observer(
            PrometheusMeterRegistry registry,
            ReactiveHttpClientProperties.ObservabilityConfig config) {
        return new MicrometerHttpClientObserver(registry, config);
    }

    private static ReactiveHttpClientProperties.ObservabilityConfig defaults() {
        return new ReactiveHttpClientProperties.ObservabilityConfig();
    }

    private static PrometheusMeterRegistry registry(PrometheusConfig config, MockClock clock) {
        return new PrometheusMeterRegistry(config, new PrometheusRegistry(), clock);
    }

    private static Map<String, String> labels(String apiName) {
        return Map.of(
                "api_name", apiName,
                "client_name", "metrics-client",
                "http_method", "GET",
                "uri", "NONE");
    }

    private static Map<String, String> withLabel(Map<String, String> labels, String key, String value) {
        java.util.LinkedHashMap<String, String> copy = new java.util.LinkedHashMap<>(labels);
        copy.put(key, value);
        return copy;
    }

    private static double sample(String scrape, String metricName, Map<String, String> labels) {
        return scrape.lines()
                .filter(line -> line.startsWith(metricName + "{"))
                .filter(line -> labels.entrySet().stream()
                        .allMatch(label -> line.contains(label.getKey() + "=\"" + label.getValue() + "\"")))
                .mapToDouble(line -> Double.parseDouble(line.substring(line.lastIndexOf("}") + 1).trim()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "Missing Prometheus sample " + metricName + " with labels " + labels + " in:\n" + scrape));
    }
}
