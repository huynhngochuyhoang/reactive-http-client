package io.github.huynhngochuyhoang.httpstarter.observability;

import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.contributor.Status;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * {@link HealthIndicator} that reports on the reactive HTTP client's recent
 * outbound error rate, computed from probe-to-probe deltas on the
 * {@code reactive.http.client.requests} timer meters published by
 * {@link MicrometerHttpClientObserver}.
 *
 * <p>On each {@link #health()} call, the indicator snapshots every matching timer,
 * groups the counts by {@code client.name}, and compares the new snapshot against
 * the one from the previous invocation. A client reports DOWN when its delta
 * sample count meets {@link ReactiveHttpClientProperties.HealthConfig#getMinSamples()}
 * and the error ratio exceeds
 * {@link ReactiveHttpClientProperties.HealthConfig#getErrorRateThreshold()}; the
 * overall status is DOWN if any tracked client is DOWN. Between probes the
 * indicator holds no per-invocation state, so it does not interfere with the
 * existing {@link HttpClientObserver} override contract.
 *
 * <p>The delta window size equals the time between actuator health probes; set
 * the probe frequency accordingly (e.g. Kubernetes liveness on a 10 s interval
 * gives a rolling 10 s error-rate signal).
 */
public final class Boot4HttpClientHealthIndicator implements HealthIndicator {

    private static final String ERROR_CATEGORY_NONE = "none";
    private static final String POOL_ACQUIRE_STAGE = HttpClientFailureStage.POOL_ACQUIRE.name();

    private final MeterRegistry meterRegistry;
    private final ReactiveHttpClientProperties.ObservabilityConfig observability;
    private final ReactiveHttpClientProperties.HealthConfig config;
    private final AtomicReference<Map<String, ClientCounts>> lastSnapshot = new AtomicReference<>(Map.of());

    public Boot4HttpClientHealthIndicator(MeterRegistry meterRegistry,
                                     ReactiveHttpClientProperties.ObservabilityConfig observability) {
        this.meterRegistry = meterRegistry;
        this.observability = observability;
        this.config = observability.getHealth();
    }

    @Override
    public Health health() {
        Map<String, ClientCounts> currentSnapshot = snapshotCounts();
        Map<String, ClientCounts> previousSnapshot = lastSnapshot.getAndSet(currentSnapshot);

        Map<String, Object> details = new LinkedHashMap<>();
        boolean overallDown = false;

        for (Map.Entry<String, ClientCounts> entry : currentSnapshot.entrySet()) {
            String clientName = entry.getKey();
            ClientCounts current = entry.getValue();
            ClientCounts previous = previousSnapshot.getOrDefault(clientName, ClientCounts.ZERO);

            long deltaTotal = current.total - previous.total;
            long deltaErrors = current.errors - previous.errors;
            long deltaPoolAcquireFailures = current.poolAcquireFailures - previous.poolAcquireFailures;
            if (deltaTotal < 0 || deltaErrors < 0) {
                // Counters should only increase; a registry restart can reset them.
                // Treat as an inconclusive probe rather than reporting DOWN.
                deltaTotal = current.total;
                deltaErrors = current.errors;
            }
            if (deltaPoolAcquireFailures < 0) {
                deltaPoolAcquireFailures = current.poolAcquireFailures;
            }

            Map<String, Object> perClient = new LinkedHashMap<>();
            perClient.put("samples", deltaTotal);
            perClient.put("errors", deltaErrors);
            perClient.put("sampleCount", deltaTotal);
            perClient.put("errorCount", deltaErrors);
            perClient.put("poolAcquireFailureCount", deltaPoolAcquireFailures);
            perClient.put("minSamples", config.getMinSamples());
            perClient.put("errorRateThreshold", config.getErrorRateThreshold());

            if (deltaTotal == 0) {
                perClient.put("status", "INSUFFICIENT_SAMPLES");
                perClient.put("reason", "NO_SAMPLES");
            }
            else if (deltaTotal < config.getMinSamples()) {
                perClient.put("errorRate", (double) deltaErrors / (double) deltaTotal);
                perClient.put("status", "INSUFFICIENT_SAMPLES");
                perClient.put("reason", "INSUFFICIENT_SAMPLES");
            }
            else {
                double errorRate = (double) deltaErrors / (double) deltaTotal;
                perClient.put("errorRate", errorRate);
                if (errorRate > config.getErrorRateThreshold()) {
                    perClient.put("status", Status.DOWN.getCode());
                    perClient.put("reason", "ERROR_RATE_ABOVE_THRESHOLD");
                    overallDown = true;
                }
                else {
                    perClient.put("status", Status.UP.getCode());
                    perClient.put("reason", "ERROR_RATE_WITHIN_THRESHOLD");
                }
            }
            details.put(clientName, perClient);
        }

        details.put("errorRateThreshold", config.getErrorRateThreshold());
        details.put("minSamples", config.getMinSamples());

        Health.Builder builder = overallDown ? Health.down() : Health.up();
        return builder.withDetails(details).build();
    }

    private Map<String, ClientCounts> snapshotCounts() {
        Map<String, ClientCounts> snapshot = new HashMap<>();
        String metricName = observability.getMetricName();
        for (Timer timer : meterRegistry.find(metricName).timers()) {
            String clientName = timer.getId().getTag("client.name");
            if (clientName == null || clientName.isEmpty()) {
                continue;
            }
            String errorCategory = timer.getId().getTag("error.category");
            String failureStage = timer.getId().getTag("failure.stage");
            long count = timer.count();
            ClientCounts counts = snapshot.computeIfAbsent(clientName, n -> new ClientCounts());
            counts.total += count;
            if (errorCategory != null && !ERROR_CATEGORY_NONE.equals(errorCategory)) {
                counts.errors += count;
            }
            if (POOL_ACQUIRE_STAGE.equals(failureStage)) {
                counts.poolAcquireFailures += count;
            }
        }
        return snapshot;
    }

    private static final class ClientCounts {
        static final ClientCounts ZERO = new ClientCounts();
        long total = 0L;
        long errors = 0L;
        long poolAcquireFailures = 0L;
    }
}
