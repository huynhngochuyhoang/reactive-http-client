package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientCacheOutcome;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.Timer;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Micrometer implementation loaded only when cache metrics are explicitly enabled. */
final class MicrometerLocalResponseCacheMetrics extends LocalResponseCacheMetrics {

    private static final Object SHARED_WEIGHTED_MONITOR = new Object();
    private static final Map<MeterRegistry, Map<PolicyKey, SharedWeightedMeters>> SHARED_WEIGHTED_METERS =
            new WeakHashMap<>();

    private final MeterRegistry registry;
    private final String clientName;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Object weightedOwnershipMonitor = new Object();
    private final Set<Meter.Id> ownedMeters = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<MeterKey, Counter> counters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<MeterKey, Timer> timers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, SharedWeightedMeters> weightedMeters = new ConcurrentHashMap<>();
    private final List<WeightedMeterHandle> weightedMeterHandles = new ArrayList<>();

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
            WeightedMeterHandle handle = acquireWeightedMeters(
                    registry, clientName, policyName, cache, maximumDecodedResponseBytes);
            boolean release;
            synchronized (weightedOwnershipMonitor) {
                release = closed.get();
                if (!release) {
                    weightedMeterHandles.add(handle);
                    weightedMeters.put(normalize(policyName), handle.meters());
                }
            }
            if (release) {
                releaseWeightedMeters(registry, handle);
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
        if (enabled() && outcome != null) {
            SharedWeightedMeters shared = weightedMeters.get(normalize(policyName));
            if (shared != null) {
                shared.admission(outcome);
            }
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
                "cache.policy", normalize(policyName));
    }

    private static String normalize(String policyName) {
        return policyName != null ? policyName : "UNKNOWN";
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
        synchronized (weightedOwnershipMonitor) {
            weightedMeterHandles.forEach(handle -> releaseWeightedMeters(registry, handle));
            weightedMeterHandles.clear();
            weightedMeters.clear();
        }
        ownedMeters.forEach(registry::remove);
        ownedMeters.clear();
        counters.clear();
        timers.clear();
    }

    private static WeightedMeterHandle acquireWeightedMeters(
            MeterRegistry registry,
            String clientName,
            String policyName,
            LocalResponseCache cache,
            long maximumDecodedResponseBytes) {
        PolicyKey key = new PolicyKey(clientName, normalize(policyName));
        synchronized (SHARED_WEIGHTED_MONITOR) {
            Map<PolicyKey, SharedWeightedMeters> byPolicy =
                    SHARED_WEIGHTED_METERS.computeIfAbsent(registry, ignored -> new java.util.HashMap<>());
            SharedWeightedMeters meters = byPolicy.computeIfAbsent(
                    key, ignored -> SharedWeightedMeters.register(registry, key));
            meters.add(cache, maximumDecodedResponseBytes);
            return new WeightedMeterHandle(key, meters, cache);
        }
    }

    private static void releaseWeightedMeters(MeterRegistry registry, WeightedMeterHandle handle) {
        synchronized (SHARED_WEIGHTED_MONITOR) {
            if (!handle.meters().remove(handle.cache()) || !handle.meters().isEmpty()) {
                return;
            }
            Map<PolicyKey, SharedWeightedMeters> byPolicy = SHARED_WEIGHTED_METERS.get(registry);
            if (byPolicy == null || !byPolicy.remove(handle.key(), handle.meters())) {
                return;
            }
            handle.meters().removeFrom(registry);
            if (byPolicy.isEmpty()) {
                SHARED_WEIGHTED_METERS.remove(registry);
            }
        }
    }

    private record PolicyKey(String clientName, String policyName) {
    }

    private record WeightedMeterHandle(
            PolicyKey key, SharedWeightedMeters meters, LocalResponseCache cache) {
    }

    private static final class SharedWeightedMeters {
        private final Map<LocalResponseCache, WeightedOwner> owners = new IdentityHashMap<>();
        private final Map<AdmissionOutcome, Counter> admissionCounters = new EnumMap<>(AdmissionOutcome.class);
        private final Set<Meter.Id> meterIds = ConcurrentHashMap.newKeySet();

        private static SharedWeightedMeters register(MeterRegistry registry, PolicyKey key) {
            SharedWeightedMeters meters = new SharedWeightedMeters();
            Tags tags = Tags.of("client.name", key.clientName(), "cache.policy", key.policyName());
            meters.own(Gauge.builder(PREFIX + ".retained.decoded.response.bytes",
                            meters, SharedWeightedMeters::retainedDecodedResponseBytes)
                    .description("Current decoded response representation bytes retained by live policy caches")
                    .strongReference(true)
                    .tags(tags)
                    .register(registry));
            meters.own(Gauge.builder(PREFIX + ".maximum.decoded.response.bytes",
                            meters, SharedWeightedMeters::maximumDecodedResponseBytes)
                    .description("Configured maximum decoded response representation bytes across live policy caches")
                    .strongReference(true)
                    .tags(tags)
                    .register(registry));
            for (AdmissionOutcome outcome : AdmissionOutcome.values()) {
                Counter counter = Counter.builder(PREFIX + ".admissions")
                        .tags(tags.and("outcome", outcome.tagValue()))
                        .register(registry);
                meters.admissionCounters.put(outcome, counter);
                meters.own(counter);
            }
            return meters;
        }

        private synchronized void add(LocalResponseCache cache, long maximumBytes) {
            WeightedOwner owner = owners.get(cache);
            if (owner == null) {
                owners.put(cache, new WeightedOwner(maximumBytes));
            }
            else {
                owner.registrations++;
            }
        }

        private synchronized boolean remove(LocalResponseCache cache) {
            WeightedOwner owner = owners.get(cache);
            if (owner == null) {
                return false;
            }
            if (--owner.registrations == 0) {
                owners.remove(cache);
            }
            return true;
        }

        private synchronized boolean isEmpty() {
            return owners.isEmpty();
        }

        private double retainedDecodedResponseBytes() {
            List<LocalResponseCache> liveCaches;
            synchronized (this) {
                liveCaches = List.copyOf(owners.keySet());
            }
            long total = 0;
            for (LocalResponseCache cache : liveCaches) {
                total = saturatedAdd(total, cache.retainedDecodedResponseBytes());
            }
            return total;
        }

        private synchronized double maximumDecodedResponseBytes() {
            long total = 0;
            for (WeightedOwner owner : owners.values()) {
                total = saturatedAdd(total, owner.maximumBytes);
            }
            return total;
        }

        private synchronized void admission(AdmissionOutcome outcome) {
            Counter counter = admissionCounters.get(outcome);
            if (counter != null) {
                counter.increment();
            }
        }

        private void own(Meter meter) {
            meterIds.add(meter.getId());
        }

        private synchronized void removeFrom(MeterRegistry registry) {
            meterIds.forEach(registry::remove);
            meterIds.clear();
            admissionCounters.clear();
        }

        private static long saturatedAdd(long current, long added) {
            if (added <= 0) {
                return current;
            }
            return current > Long.MAX_VALUE - added ? Long.MAX_VALUE : current + added;
        }
    }

    private static final class WeightedOwner {
        private final long maximumBytes;
        private int registrations = 1;

        private WeightedOwner(long maximumBytes) {
            this.maximumBytes = maximumBytes;
        }
    }

    private record MeterKey(String name, Tags tags) {
    }
}
