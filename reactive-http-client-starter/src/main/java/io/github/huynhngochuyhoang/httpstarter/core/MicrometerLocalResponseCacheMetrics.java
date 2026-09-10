package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.exception.CacheWorkRejectedException;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientCacheOutcome;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.Timer;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

/** Registry/tag scoped leases keep overlapping factories' meters alive until the last owner closes. */
final class MicrometerLocalResponseCacheMetrics extends LocalResponseCacheMetrics {
    private static final Object OWNERSHIP = new Object();
    private static final Map<MeterRegistry, Map<MeterKey, SharedMeter>> SHARED = new WeakHashMap<>();

    private final MeterRegistry registry;
    private final String clientName;
    private final Map<MeterKey, SharedMeter> leases = new HashMap<>();
    private boolean closed;

    private MicrometerLocalResponseCacheMetrics(MeterRegistry registry, String clientName) {
        this.registry = registry;
        this.clientName = normalize(clientName);
    }

    static LocalResponseCacheMetrics create(Object registry, String clientName) {
        return registry instanceof MeterRegistry meters
                ? new MicrometerLocalResponseCacheMetrics(meters, clientName) : disabled();
    }

    @Override synchronized boolean enabled() { return !closed; }

    @Override synchronized void registerApi(String apiName) {
        if (closed) { return; }
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

    @Override synchronized void registerCache(String policyName, long maximumSize, LocalResponseCache cache) {
        if (closed) { return; }
        Tags tags = policyTags(policyName);
        gauge(PREFIX + ".entries", tags, cache::estimatedSize, null);
        gauge(PREFIX + ".maximum.entries", tags, () -> maximumSize, null);
        Long bytes = cache.maximumDecodedResponseBytes();
        if (bytes != null) {
            gauge(PREFIX + ".retained.decoded.response.bytes", tags, cache::retainedDecodedResponseBytes,
                    "Current decoded response representation bytes retained by live policy caches");
            gauge(PREFIX + ".maximum.decoded.response.bytes", tags, () -> bytes,
                    "Configured maximum decoded response representation bytes across live policy caches");
            for (AdmissionOutcome outcome : AdmissionOutcome.values()) {
                counter(PREFIX + ".admissions", tags.and("outcome", outcome.tagValue()));
            }
        }
        for (LocalResponseCache.RemovalReason reason : LocalResponseCache.RemovalReason.values()) {
            if (reason != LocalResponseCache.RemovalReason.WEIGHT || bytes != null) {
                counter(PREFIX + ".evictions", tags.and("cause", reason.tagValue()));
            }
        }
    }

    @Override synchronized void registerWork(String policyName, CacheWorkPolicy.Limits limits,
                                             CacheWorkAdmission callers, CacheWorkAdmission loads,
                                             CacheWorkAdmission refreshes) {
        if (closed) { return; }
        workGauges(policyName, "callers", limits.maximumConcurrentCallers(), callers);
        workGauges(policyName, "loads", limits.maximumConcurrentLoads(), loads);
        for (CacheWorkRejectedException.Reason reason : CacheWorkRejectedException.Reason.values()) {
            counter(PREFIX + ".work.rejections", policyTags(policyName).and("reason", reasonTag(reason)));
        }
        if (limits.maximumConcurrentRefreshes() != null) {
            workGauges(policyName, "refreshes", limits.maximumConcurrentRefreshes(), refreshes);
            for (RefreshSkipReason reason : RefreshSkipReason.values()) {
                counter(PREFIX + ".refresh.skips", policyTags(policyName).and("reason", reason.tagValue()));
            }
        }
    }

    private void workGauges(String policyName, String dimension, int maximum, CacheWorkAdmission admission) {
        Tags tags = policyTags(policyName);
        gauge(PREFIX + ".work.active." + dimension, tags, () -> admission.active(policyName),
                "Current reserved " + dimension + " across live limited policy owners; overlapping work units");
        gauge(PREFIX + ".work.maximum." + dimension, tags, () -> maximum,
                "Configured " + dimension + " capacity across live limited policy owners");
    }

    @Override synchronized void rejection(String policyName, CacheWorkRejectedException.Reason reason) {
        incrementRegistered(PREFIX + ".work.rejections", policyTags(policyName).and("reason", reasonTag(reason)));
    }

    @Override synchronized void refreshSkipped(String policyName, RefreshSkipReason reason) {
        incrementRegistered(PREFIX + ".refresh.skips", policyTags(policyName).and("reason", reason.tagValue()));
    }

    @Override synchronized void lookup(String apiName, String result) {
        increment(PREFIX + ".lookups", callerTags(apiName).and("result", result));
    }

    @Override synchronized void coalesced(String apiName) { increment(PREFIX + ".coalesced", callerTags(apiName)); }
    @Override synchronized void stale(String apiName) { increment(PREFIX + ".stale", callerTags(apiName)); }

    @Override synchronized void caller(String apiName, HttpClientCacheOutcome outcome) {
        if (outcome != null) { increment(PREFIX + ".callers", callerTags(apiName).and("outcome", outcome.name())); }
    }

    @Override synchronized void load(String apiName, WorkOutcome outcome, long durationNanos) {
        work(".loads", ".load.duration", apiName, outcome, durationNanos);
    }

    @Override synchronized void refresh(String apiName, WorkOutcome outcome, long durationNanos) {
        work(".refreshes", ".refresh.duration", apiName, outcome, durationNanos);
    }

    @Override synchronized void eviction(String policyName, LocalResponseCache.RemovalReason reason) {
        increment(PREFIX + ".evictions", policyTags(policyName).and("cause", reason.tagValue()));
    }

    @Override synchronized void admission(String policyName, AdmissionOutcome outcome) {
        if (outcome != null) {
            incrementRegistered(PREFIX + ".admissions", policyTags(policyName).and("outcome", outcome.tagValue()));
        }
    }

    private void work(String counterName, String timerName, String apiName, WorkOutcome outcome, long durationNanos) {
        if (closed) { return; }
        Tags tags = callerTags(apiName).and("outcome", outcome.tagValue());
        counter(PREFIX + counterName, tags).increment();
        timer(PREFIX + timerName, tags).record(Math.max(0L, durationNanos), TimeUnit.NANOSECONDS);
    }

    private void increment(String name, Tags tags) {
        if (!closed) { counter(name, tags).increment(); }
    }

    private void incrementRegistered(String name, Tags tags) {
        if (!closed) {
            SharedMeter lease = leases.get(new MeterKey(name, tags));
            if (lease != null) { ((Counter) lease.meter).increment(); }
        }
    }

    private Counter counter(String name, Tags tags) {
        return (Counter) acquire(new MeterKey(name, tags),
                () -> Counter.builder(name).tags(tags).register(registry)).meter;
    }

    private Timer timer(String name, Tags tags) {
        return (Timer) acquire(new MeterKey(name, tags),
                () -> Timer.builder(name).tags(tags).register(registry)).meter;
    }

    private void gauge(String name, Tags tags, DoubleSupplier supplier, String description) {
        MeterKey key = new MeterKey(name, tags);
        if (leases.containsKey(key)) { return; }
        synchronized (OWNERSHIP) {
            Map<MeterKey, SharedMeter> meters = SHARED.computeIfAbsent(registry, ignored -> new HashMap<>());
            SharedMeter shared = meters.get(key);
            if (shared == null) {
                shared = new SharedMeter();
                shared.meter = Gauge.builder(name, shared, SharedMeter::value)
                        .strongReference(true).description(description).tags(tags).register(registry);
                meters.put(key, shared);
            }
            shared.add(this, supplier);
            leases.put(key, shared);
        }
    }

    private SharedMeter acquire(MeterKey key, Supplier<Meter> registration) {
        SharedMeter existing = leases.get(key);
        if (existing != null) { return existing; }
        synchronized (OWNERSHIP) {
            Map<MeterKey, SharedMeter> meters = SHARED.computeIfAbsent(registry, ignored -> new HashMap<>());
            SharedMeter shared = meters.computeIfAbsent(key, ignored -> {
                SharedMeter created = new SharedMeter();
                created.meter = registration.get();
                return created;
            });
            shared.add(this, null);
            leases.put(key, shared);
            return shared;
        }
    }

    @Override public synchronized void close() {
        if (closed) { return; }
        closed = true;
        synchronized (OWNERSHIP) {
            Map<MeterKey, SharedMeter> meters = SHARED.get(registry);
            leases.forEach((key, shared) -> {
                if (shared.remove(this)) {
                    registry.remove(shared.meter);
                    if (meters != null) { meters.remove(key, shared); }
                }
            });
            if (meters != null && meters.isEmpty()) { SHARED.remove(registry); }
            leases.clear();
        }
    }

    private Tags callerTags(String apiName) { return Tags.of("client.name", clientName, "api.name", normalize(apiName)); }
    private Tags policyTags(String policyName) { return Tags.of("client.name", clientName, "cache.policy", normalize(policyName)); }
    private static String normalize(String name) { return name != null ? name : "UNKNOWN"; }
    private static String reasonTag(CacheWorkRejectedException.Reason reason) { return reason.name().toLowerCase(Locale.ROOT); }

    private record MeterKey(String name, Tags tags) { }

    private static final class SharedMeter {
        private Meter meter;
        private final Map<Object, DoubleSupplier> owners = new IdentityHashMap<>();

        synchronized void add(Object owner, DoubleSupplier supplier) { owners.put(owner, supplier); }
        synchronized boolean remove(Object owner) { owners.remove(owner); return owners.isEmpty(); }

        double value() {
            List<DoubleSupplier> suppliers;
            synchronized (this) { suppliers = new ArrayList<>(owners.values()); }
            // Never hold registry ownership locks while sampling cache/admission lifecycle locks.
            double total = 0;
            for (DoubleSupplier supplier : suppliers) { total += supplier.getAsDouble(); }
            return total;
        }
    }
}
