package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientCacheOutcome;
import io.micrometer.core.instrument.*;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Micrometer implementation loaded only when cache metrics are explicitly enabled. */
final class MicrometerLocalResponseCacheMetrics extends LocalResponseCacheMetrics {

    private final MeterRegistry registry;
    private final String clientName;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Set<Meter.Id> ownedMeters = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<MeterKey, Counter> counters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<MeterKey, Timer> timers = new ConcurrentHashMap<>();

    private MicrometerLocalResponseCacheMetrics(MeterRegistry registry, String clientName) {
        this.registry = registry;
        this.clientName = clientName != null ? clientName : "UNKNOWN";
    }

    static LocalResponseCacheMetrics create(Object registry, String clientName) {
        if (!(registry instanceof MeterRegistry meterRegistry)) {
            return LocalResponseCacheMetrics.disabled();
        }
        return new MicrometerLocalResponseCacheMetrics(meterRegistry, clientName);
    }

    @Override
    boolean enabled() {
        return !closed.get();
    }

    @Override
    void registerApi(String apiName) {
        if (!enabled()) {
            return;
        }
        counter(PREFIX + ".lookups", callerTags(apiName).and("result", "hit"));
        counter(PREFIX + ".lookups", callerTags(apiName).and("result", "miss"));
        counter(PREFIX + ".coalesced", callerTags(apiName));
        counter(PREFIX + ".stale", callerTags(apiName));
        for (HttpClientCacheOutcome outcome : HttpClientCacheOutcome.values()) {
            counter(PREFIX + ".callers", callerTags(apiName).and("outcome", outcome.name()));
        }
        for (WorkOutcome outcome : WorkOutcome.values()) {
            Tags tags = callerTags(apiName).and("outcome", outcome.tagValue());
            counter(PREFIX + ".loads", tags);
            timer(PREFIX + ".load.duration", tags);
            counter(PREFIX + ".refreshes", tags);
            timer(PREFIX + ".refresh.duration", tags);
        }
    }

    @Override
    void registerCache(String policyName, long maximumSize, LocalResponseCache cache) {
        if (!enabled()) {
            return;
        }
        Tags tags = policyTags(policyName);
        own(Gauge.builder(PREFIX + ".entries", cache, LocalResponseCache::estimatedSize)
                .strongReference(true)
                .tags(tags)
                .register(registry));
        own(Gauge.builder(PREFIX + ".maximum.entries", () -> maximumSize)
                .strongReference(true)
                .tags(tags)
                .register(registry));
        Long maximumDecodedResponseBytes = cache.maximumDecodedResponseBytes();
        if (maximumDecodedResponseBytes != null) {
            own(Gauge.builder(PREFIX + ".retained.decoded.response.bytes",
                            cache, LocalResponseCache::retainedDecodedResponseBytes)
                    .description("Current decoded response representation bytes retained by this policy cache")
                    .strongReference(true)
                    .tags(tags)
                    .register(registry));
            own(Gauge.builder(PREFIX + ".maximum.decoded.response.bytes", () -> maximumDecodedResponseBytes)
                    .description("Configured maximum decoded response representation bytes for this policy cache")
                    .strongReference(true)
                    .tags(tags)
                    .register(registry));
            for (AdmissionOutcome outcome : AdmissionOutcome.values()) {
                counter(PREFIX + ".admissions", tags.and("outcome", outcome.tagValue()));
            }
        }
        for (LocalResponseCache.RemovalReason reason : LocalResponseCache.RemovalReason.values()) {
            if (reason != LocalResponseCache.RemovalReason.WEIGHT
                    || cache.maximumDecodedResponseBytes() != null) {
                counter(PREFIX + ".evictions", tags.and("cause", reason.tagValue()));
            }
        }
    }

    @Override
    void lookup(String apiName, String result) {
        increment(PREFIX + ".lookups", callerTags(apiName).and("result", result));
    }

    @Override
    void coalesced(String apiName) {
        increment(PREFIX + ".coalesced", callerTags(apiName));
    }

    @Override
    void stale(String apiName) {
        increment(PREFIX + ".stale", callerTags(apiName));
    }

    @Override
    void caller(String apiName, HttpClientCacheOutcome outcome) {
        if (outcome != null) {
            increment(PREFIX + ".callers", callerTags(apiName).and("outcome", outcome.name()));
        }
    }

    @Override
    void load(String apiName, WorkOutcome outcome, long durationNanos) {
        work(PREFIX + ".loads", PREFIX + ".load.duration", apiName, outcome, durationNanos);
    }

    @Override
    void refresh(String apiName, WorkOutcome outcome, long durationNanos) {
        work(PREFIX + ".refreshes", PREFIX + ".refresh.duration", apiName, outcome, durationNanos);
    }

    @Override
    void eviction(String policyName, LocalResponseCache.RemovalReason reason) {
        increment(PREFIX + ".evictions", policyTags(policyName).and("cause", reason.tagValue()));
    }

    @Override
    void admission(String policyName, AdmissionOutcome outcome) {
        if (outcome != null) {
            increment(PREFIX + ".admissions", policyTags(policyName).and("outcome", outcome.tagValue()));
        }
    }

    private void work(String counterName,
                      String timerName,
                      String apiName,
                      WorkOutcome outcome,
                      long durationNanos) {
        if (!enabled()) {
            return;
        }
        Tags tags = callerTags(apiName).and("outcome", outcome.tagValue());
        increment(counterName, tags);
        timer(timerName, tags).record(Math.max(0L, durationNanos), TimeUnit.NANOSECONDS);
    }

    private void increment(String name, Tags tags) {
        if (enabled()) {
            counter(name, tags).increment();
        }
    }

    private Counter counter(String name, Tags tags) {
        MeterKey key = new MeterKey(name, tags);
        return counters.computeIfAbsent(key, ignored -> own(Counter.builder(name).tags(tags).register(registry)));
    }

    private Timer timer(String name, Tags tags) {
        MeterKey key = new MeterKey(name, tags);
        return timers.computeIfAbsent(key, ignored -> own(Timer.builder(name).tags(tags).register(registry)));
    }

    private Tags callerTags(String apiName) {
        return Tags.of(
                "client.name", clientName,
                "api.name", apiName != null ? apiName : "UNKNOWN");
    }

    private Tags policyTags(String policyName) {
        return Tags.of(
                "client.name", clientName,
                "cache.policy", policyName != null ? policyName : "UNKNOWN");
    }

    private <T extends Meter> T own(T meter) {
        if (closed.get()) {
            registry.remove(meter);
            return meter;
        }
        ownedMeters.add(meter.getId());
        if (closed.get() && ownedMeters.remove(meter.getId())) {
            registry.remove(meter);
        }
        return meter;
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        ownedMeters.forEach(registry::remove);
        ownedMeters.clear();
        counters.clear();
        timers.clear();
    }

    private record MeterKey(String name, Tags tags) {
    }
}
