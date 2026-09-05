package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.auth.AuthProvider;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.scheduler.Schedulers;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.LongSupplier;

/**
 * Bridge used by the test-helper artifact to install deterministic cache time
 * without exposing the starter's cache implementation.
 *
 * @hidden
 */
public final class MockResponseCacheSupport {

    private MockResponseCacheSupport() {
    }

    public static Session create(
            WebClient webClient,
            MethodMetadataCache metadataCache,
            ReactiveHttpClientProperties.ClientConfig clientConfig,
            String clientName,
            Class<?> clientInterface,
            ApplicationContext applicationContext,
            ResilienceOperatorApplier resilienceOperatorApplier,
            ReactiveHttpClientJsonCodec jsonCodec,
            ReactiveHttpClientProperties.ObservabilityConfig observabilityConfig,
            AuthProvider authProvider,
            String baseUrl,
            LongSupplier ticker) {
        ReactiveHttpClientFactoryBean.validateEffectiveResilienceContracts(
                clientInterface, metadataCache, clientConfig, resilienceOperatorApplier, clientName);
        boolean cacheObservabilityEnabled = observabilityConfig != null
                && observabilityConfig.isEnabled()
                && observabilityConfig.getCache() != null
                && observabilityConfig.getCache().isEnabled();
        RecordingMetrics metrics = new RecordingMetrics(cacheObservabilityEnabled);
        LocalResponseCacheManager manager = LocalResponseCacheManager.createForClient(
                clientInterface,
                clientName,
                metadataCache,
                clientConfig,
                applicationContext.getClassLoader(),
                ticker,
                Schedulers.parallel(),
                metrics);
        ReactiveClientInvocationHandler handler = new ReactiveClientInvocationHandler(
                webClient,
                metadataCache,
                new RequestArgumentResolver(),
                new DefaultErrorDecoder(),
                clientConfig,
                clientName,
                clientInterface,
                applicationContext,
                resilienceOperatorApplier,
                jsonCodec,
                observabilityConfig,
                manager,
                authProvider,
                baseUrl);
        return new Session(handler, new Control(manager, metrics));
    }

    public record Session(ReactiveClientInvocationHandler handler, Control control) {
    }

    public static final class Control implements AutoCloseable {
        private final LocalResponseCacheManager manager;
        private final RecordingMetrics metrics;

        private Control(LocalResponseCacheManager manager, RecordingMetrics metrics) {
            this.manager = manager;
            this.metrics = metrics;
        }

        public long entryCount() {
            return manager.snapshot().currentSize();
        }

        public void evictAll() {
            manager.evictAllForTesting();
        }

        public Snapshot snapshot() {
            LocalResponseCacheManager.WorkloadSnapshot workload = manager.workloadSnapshotForTesting();
            LocalResponseCacheManager.Snapshot cache = workload.cache();
            return new Snapshot(
                    cache.currentSize(),
                    cache.retainedDecodedResponseBytes(),
                    cache.evictions(),
                    workload.inFlightLoads(),
                    workload.coalescedWaiters(),
                    workload.inFlightRefreshes(),
                    metrics.counts(metrics.admissions),
                    metrics.counts(metrics.evictions),
                    metrics.counts(metrics.refreshes),
                    cache.closed());
        }

        @Override
        public void close() {
            manager.close();
        }
    }

    public record Snapshot(
            long entryCount,
            Long retainedDecodedResponseBytes,
            long evictionCount,
            int inFlightLoadCount,
            int coalescedWaiterCount,
            int inFlightRefreshCount,
            Map<String, Map<String, Long>> admissionCounts,
            Map<String, Map<String, Long>> evictionCounts,
            Map<String, Map<String, Long>> refreshCounts,
            boolean closed) {
    }

    private static final class RecordingMetrics extends LocalResponseCacheMetrics {
        private final boolean enabled;
        private final Map<String, Map<String, LongAdder>> admissions = new ConcurrentHashMap<>();
        private final Map<String, Map<String, LongAdder>> evictions = new ConcurrentHashMap<>();
        private final Map<String, Map<String, LongAdder>> refreshes = new ConcurrentHashMap<>();

        private RecordingMetrics(boolean enabled) {
            this.enabled = enabled;
        }

        @Override boolean enabled() { return enabled; }
        @Override void registerApi(String apiName) { }
        @Override void registerCache(String policyName, long maximumSize, LocalResponseCache cache) { }
        @Override void lookup(String apiName, String result) { }
        @Override void coalesced(String apiName) { }
        @Override void stale(String apiName) { }
        @Override void caller(String apiName,
                              io.github.huynhngochuyhoang.httpstarter.observability.HttpClientCacheOutcome outcome) { }
        @Override void load(String apiName, WorkOutcome outcome, long durationNanos) { }

        @Override
        void refresh(String apiName, WorkOutcome outcome, long durationNanos) {
            if (enabled) {
                increment(refreshes, apiName, outcome.tagValue());
            }
        }

        @Override
        void eviction(String policyName, LocalResponseCache.RemovalReason reason) {
            if (enabled) {
                increment(evictions, policyName, reason.tagValue());
            }
        }

        @Override
        void admission(String policyName, AdmissionOutcome outcome) {
            if (enabled) {
                increment(admissions, policyName, outcome.tagValue());
            }
        }

        @Override public void close() { }

        private void increment(
                Map<String, Map<String, LongAdder>> counters, String group, String outcome) {
            counters.computeIfAbsent(group, ignored -> new ConcurrentHashMap<>())
                    .computeIfAbsent(outcome, ignored -> new LongAdder())
                    .increment();
        }

        private Map<String, Map<String, Long>> counts(
                Map<String, Map<String, LongAdder>> counters) {
            Map<String, Map<String, Long>> snapshot = new LinkedHashMap<>();
            counters.forEach((group, outcomes) -> {
                Map<String, Long> outcomeSnapshot = new LinkedHashMap<>();
                outcomes.forEach((outcome, count) -> outcomeSnapshot.put(outcome, count.longValue()));
                snapshot.put(group, Map.copyOf(outcomeSnapshot));
            });
            return Map.copyOf(snapshot);
        }
    }
}
