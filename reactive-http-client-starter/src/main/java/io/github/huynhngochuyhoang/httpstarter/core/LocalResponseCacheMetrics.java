package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientCacheOutcome;

/** Factory-owned, separately opt-in cache meter facade without a hard Micrometer dependency. */
abstract class LocalResponseCacheMetrics implements AutoCloseable {

    static final String PREFIX = "reactive.http.client.cache";
    private static final LocalResponseCacheMetrics DISABLED = new Disabled();

    static LocalResponseCacheMetrics disabled() {
        return DISABLED;
    }

    static LocalResponseCacheMetrics enabled(Object registry, String clientName) {
        return registry != null
                ? MicrometerLocalResponseCacheMetrics.create(registry, clientName)
                : disabled();
    }

    abstract boolean enabled();

    abstract void registerApi(String apiName);

    abstract void registerCache(String policyName, long maximumSize, LocalResponseCache cache);

    abstract void lookup(String apiName, String result);

    abstract void coalesced(String apiName);

    abstract void stale(String apiName);

    abstract void caller(String apiName, HttpClientCacheOutcome outcome);

    abstract void load(String apiName, WorkOutcome outcome, long durationNanos);

    abstract void refresh(String apiName, WorkOutcome outcome, long durationNanos);

    abstract void eviction(String policyName, LocalResponseCache.RemovalReason reason);

    abstract void admission(String policyName, AdmissionOutcome outcome);

    @Override
    public abstract void close();

    enum WorkOutcome {
        SUCCESS,
        FAILURE,
        CANCELLATION;

        String tagValue() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    enum AdmissionOutcome {
        ADMITTED,
        BYPASSED_UNKNOWN_SIZE,
        BYPASSED_OVER_BUDGET,
        BYPASSED_CAPACITY;

        String tagValue() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    private static final class Disabled extends LocalResponseCacheMetrics {

        @Override
        boolean enabled() {
            return false;
        }

        @Override
        void registerApi(String apiName) {
        }

        @Override
        void registerCache(String policyName, long maximumSize, LocalResponseCache cache) {
        }

        @Override
        void lookup(String apiName, String result) {
        }

        @Override
        void coalesced(String apiName) {
        }

        @Override
        void stale(String apiName) {
        }

        @Override
        void caller(String apiName, HttpClientCacheOutcome outcome) {
        }

        @Override
        void load(String apiName, WorkOutcome outcome, long durationNanos) {
        }

        @Override
        void refresh(String apiName, WorkOutcome outcome, long durationNanos) {
        }

        @Override
        void eviction(String policyName, LocalResponseCache.RemovalReason reason) {
        }

        @Override
        void admission(String policyName, AdmissionOutcome outcome) {
        }

        @Override
        public void close() {
        }
    }
}
