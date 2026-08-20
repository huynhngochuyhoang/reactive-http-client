package io.github.huynhngochuyhoang.httpstarter.observability;

import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.contributor.Status;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * {@link HealthIndicator} that reports on the reactive HTTP client's recent
 * outbound error rate, computed from probe-to-probe deltas on the
 * {@code reactive.http.client.requests} timer meters published by
 * {@link MicrometerHttpClientObserver}.
 *
 * <p>On each {@link #health()} call, the indicator snapshots every matching timer,
 * groups timer-count deltas by {@code client.name}, and classifies errors from the
 * {@code error.category} and {@code failure.stage} tags. Duration sums, maxima,
 * percentiles, and histogram buckets are not health inputs. Unchanged timer
 * instances are compared with the previous invocation; a removed and recreated
 * timer starts a new count baseline. A client reports DOWN when its delta sample
 * count meets {@link ReactiveHttpClientProperties.HealthConfig#getMinSamples()} and
 * the error ratio exceeds
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
    private static final int MAX_CLIENTS = 256;
    private static final int MAX_CLIENT_NAME_LENGTH = 512;

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

            ClientCounts delta = current.deltaFrom(previous);
            long deltaTotal = delta.total;
            long deltaErrors = delta.errors;
            long deltaPoolAcquireFailures = delta.poolAcquireFailures;

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
        Map<String, ClientCounts> snapshot = new TreeMap<>();
        String metricName = observability.getMetricName();
        for (Timer timer : meterRegistry.find(metricName).timers()) {
            String clientName = timer.getId().getTag("client.name");
            if (clientName == null || clientName.isEmpty()) {
                continue;
            }
            if (clientName.length() > MAX_CLIENT_NAME_LENGTH) {
                throw new IllegalStateException("Reactive HTTP client health detail exceeds the "
                        + MAX_CLIENT_NAME_LENGTH + " character client-name limit");
            }
            String errorCategory = timer.getId().getTag("error.category");
            String failureStage = timer.getId().getTag("failure.stage");
            long count = timer.count();
            ClientCounts counts = snapshot.computeIfAbsent(clientName, n -> new ClientCounts());
            counts.add(timer, count,
                    errorCategory != null && !ERROR_CATEGORY_NONE.equals(errorCategory),
                    POOL_ACQUIRE_STAGE.equals(failureStage));
        }
        if (snapshot.size() > MAX_CLIENTS) {
            throw new IllegalStateException("Reactive HTTP client health detail exceeds the "
                    + MAX_CLIENTS + " client limit");
        }
        return snapshot;
    }

    private static final class ClientCounts {
        static final ClientCounts ZERO = new ClientCounts();
        private final Map<Meter.Id, SeriesCounts> series = new LinkedHashMap<>();
        long total = 0L;
        long errors = 0L;
        long poolAcquireFailures = 0L;

        void add(Timer timer, long count, boolean error, boolean poolAcquireFailure) {
            long errorCount = error ? count : 0L;
            long poolAcquireFailureCount = poolAcquireFailure ? count : 0L;
            series.put(timer.getId(),
                    new SeriesCounts(timer, count, errorCount, poolAcquireFailureCount));
            total += count;
            errors += errorCount;
            poolAcquireFailures += poolAcquireFailureCount;
        }

        ClientCounts deltaFrom(ClientCounts previous) {
            ClientCounts delta = new ClientCounts();
            for (Map.Entry<Meter.Id, SeriesCounts> entry : series.entrySet()) {
                SeriesCounts current = entry.getValue();
                SeriesCounts prior = previous.series.get(entry.getKey());
                boolean sameMeter = prior != null && prior.timer == current.timer;
                delta.total += counterDelta(current.total, sameMeter ? prior.total : 0L);
                delta.errors += counterDelta(current.errors, sameMeter ? prior.errors : 0L);
                delta.poolAcquireFailures += counterDelta(
                        current.poolAcquireFailures,
                        sameMeter ? prior.poolAcquireFailures : 0L);
            }
            return delta;
        }

        private static long counterDelta(long current, long previous) {
            long delta = current - previous;
            return delta >= 0L ? delta : current;
        }
    }

    private static final class SeriesCounts {
        private final Timer timer;
        private final long total;
        private final long errors;
        private final long poolAcquireFailures;

        private SeriesCounts(Timer timer, long total, long errors, long poolAcquireFailures) {
            this.timer = timer;
            this.total = total;
            this.errors = errors;
            this.poolAcquireFailures = poolAcquireFailures;
        }
    }
}
