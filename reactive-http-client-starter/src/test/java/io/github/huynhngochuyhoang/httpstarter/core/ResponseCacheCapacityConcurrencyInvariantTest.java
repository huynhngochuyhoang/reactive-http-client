package io.github.huynhngochuyhoang.httpstarter.core;

import com.github.benmanes.caffeine.cache.Cache;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import reactor.test.scheduler.VirtualTimeScheduler;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResponseCacheCapacityConcurrencyInvariantTest {

    private static final int METADATA_KEYS = Integer.getInteger("v29.cache.metadata.keys", 1_024);
    private static final int STRESS_ITERATIONS = Integer.getInteger("v29.cache.stress.iterations", 256);
    private static final long STRESS_SEED = Long.getLong("v29.cache.stress.seed", 0x29CA_C4E5L);

    @Test
    void capacityExpiryReplacementAndPolicyIsolationKeepExactTotals() throws Exception {
        AtomicLong ticker = new AtomicLong();
        CaffeineLocalResponseCache cache = new CaffeineLocalResponseCache(
                100, 3, 8L, ticker::get, null);

        publish(cache, key("small-one"), "small-one", 1);
        assertExactTotals(cache, 3, 8);
        publish(cache, key("small-two"), "small-two", 2);
        assertExactTotals(cache, 3, 8);
        publish(cache, key("large"), "large", 5);
        assertExactTotals(cache, 3, 8);

        LocalResponseCache.Lookup replacementSource = cache.lookup(key("small-one"));
        assertThat(replacementSource.hit()).isTrue();
        LocalResponseCache.RefreshToken replacement = cache.beginRefresh(replacementSource.entryToken());
        cache.publishRefresh(replacement, "replacement", 3);
        cache.finishRefresh(replacement);
        assertThat(value(cache, key("small-one"))).isEqualTo("replacement");
        assertExactTotals(cache, 3, 8);

        publish(cache, key("pressure-large"), "pressure-large", 7);
        assertThat(value(cache, key("pressure-large"))).isEqualTo("pressure-large");
        assertExactTotals(cache, 3, 8);
        for (int index = 0; index < 5; index++) {
            publish(cache, key("count-" + index), "count-" + index, 1);
            assertExactTotals(cache, 3, 8);
        }

        cache.invalidateAll();
        assertThat(audit(cache)).isEqualTo(new CacheAudit(0, 0, 0, 0));

        publish(cache, key("reload"), "before-expiry", 3);
        ticker.addAndGet(Duration.ofMillis(100).toNanos());
        assertThat(audit(cache)).isEqualTo(new CacheAudit(0, 0, 0, 0));
        publish(cache, key("reload"), "after-expiry", 4);
        assertThat(value(cache, key("reload"))).isEqualTo("after-expiry");
        assertThat(audit(cache)).isEqualTo(new CacheAudit(1, 4, 4, 1));
        cache.close();

        verifyPolicyAndClientIsolationOnOneRegistry(ticker);
    }

    @Test
    void terminalMissPathsAndFlightsReleaseMetadataIndependentlyOfKeyCardinality() throws Exception {
        CaffeineLocalResponseCache missOnly = new CaffeineLocalResponseCache(
                60_000, 8, 64L, System::nanoTime, null);
        for (int index = 0; index < METADATA_KEYS; index++) {
            LocalResponseCache.Lookup lookup = missOnly.lookup(key("miss-" + index));
            missOnly.finish(lookup.loadToken());
        }
        assertThat(audit(missOnly)).isEqualTo(new CacheAudit(0, 0, 0, 0));
        missOnly.close();

        LocalResponseCacheManager manager = LocalResponseCacheManager.testing(System::nanoTime);
        EffectiveCachePolicy.Selection failed = selection("failed", 60_000, 8, null, false);
        EffectiveCachePolicy.Selection rejected = selection("rejected", 60_000, 8, 4L, false);
        EffectiveCachePolicy.Selection cancelled = selection("cancelled", 60_000, 8, null, false);
        for (int index = 0; index < METADATA_KEYS; index++) {
            int keyIndex = index;
            assertThatThrownBy(() -> cached(
                    manager, failed, key("failed-" + keyIndex),
                    Mono.error(new IllegalStateException("failed-" + keyIndex))).block())
                    .isInstanceOf(IllegalStateException.class);

            assertThat(weightedCached(
                    manager, rejected, key("rejected-" + index),
                    Mono.just("not-stored"), 5).block()).isEqualTo("not-stored");

            Disposable subscription = cached(
                    manager, cancelled, key("cancelled-" + index), Mono.never()).subscribe();
            subscription.dispose();
        }
        assertThat(manager.snapshot().currentSize()).isZero();
        assertThat(manager.workloadSnapshotForTesting())
                .extracting(LocalResponseCacheManager.WorkloadSnapshot::inFlightLoads,
                        LocalResponseCacheManager.WorkloadSnapshot::coalescedWaiters,
                        LocalResponseCacheManager.WorkloadSnapshot::inFlightRefreshes)
                .containsExactly(0, 0, 0);
        assertAllGenerations(manager, 0);

        verifyFlightTerminalCleanup(manager);
        verifyEvictionInvalidatesPublicationWithoutCancellingCaller(manager);
        manager.close();
    }

    @Test
    void seededInterleavingsRemainBoundedAndLeaveTargetOnlyEvidence() throws Throwable {
        Random random = new Random(STRESS_SEED);
        AtomicLong ticker = new AtomicLong();
        CaffeineLocalResponseCache cache = new CaffeineLocalResponseCache(
                25, 16, 64L, ticker::get, null);
        int[] scenarios = new int[10];
        int completed = 0;
        String failure = "none";
        try {
            for (; completed < STRESS_ITERATIONS; completed++) {
                int scenario = completed < scenarios.length
                        ? completed
                        : random.nextInt(scenarios.length);
                scenarios[scenario]++;
                if (scenario < 6) {
                    runStorageScenario(cache, ticker, random, completed, scenario);
                }
                else {
                    runManagerScenario(completed, scenario);
                }
                assertExactTotals(cache, 16, 64);
            }
            cache.invalidateAll();
            assertThat(audit(cache)).isEqualTo(new CacheAudit(0, 0, 0, 0));
        }
        catch (Throwable error) {
            failure = error.getClass().getName() + ":" + String.valueOf(error.getMessage())
                    + " atIteration=" + completed;
            throw error;
        }
        finally {
            cache.close();
            writeStressEvidence(completed, scenarios, failure);
        }
    }

    @Test
    void admissionAndAccountingDoNotTraverseValuesOrUseBlockingReactorCalls() {
        Scheduler eventLoop = Schedulers.newSingle("v29-cache-event-loop", true);
        LocalResponseCacheManager manager = LocalResponseCacheManager.testing(System::nanoTime);
        EffectiveCachePolicy.Selection selection = selection("event-loop", 60_000, 4, 16L, false);
        AtomicInteger nonBlockingExecutions = new AtomicInteger();
        try {
            for (int index = 0; index < 64; index++) {
                OpaqueValue value = new OpaqueValue(index);
                Object result = weightedCached(
                        manager,
                        selection,
                        key("event-loop-" + index),
                        Mono.fromCallable(() -> {
                            assertThat(Schedulers.isInNonBlockingThread()).isTrue();
                            nonBlockingExecutions.incrementAndGet();
                            return value;
                        }).subscribeOn(eventLoop),
                        4).block();
                assertThat(result).isSameAs(value);
                assertThat(manager.snapshot().currentSize()).isLessThanOrEqualTo(4);
                assertThat(manager.retainedDecodedResponseBytesForTesting()).isLessThanOrEqualTo(16);
            }
            assertThat(nonBlockingExecutions).hasValue(64);
            assertAllGenerations(manager, Math.toIntExact(manager.snapshot().currentSize()));
        }
        catch (Exception error) {
            throw new AssertionError(error);
        }
        finally {
            manager.close();
            eventLoop.dispose();
        }
    }

    private static void verifyPolicyAndClientIsolationOnOneRegistry(AtomicLong ticker) throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        LocalResponseCacheManager first = LocalResponseCacheManager.testing(
                ticker::get, Schedulers.immediate(),
                LocalResponseCacheMetrics.enabled(registry, "first-client"), "first-client");
        LocalResponseCacheManager second = LocalResponseCacheManager.testing(
                ticker::get, Schedulers.immediate(),
                LocalResponseCacheMetrics.enabled(registry, "second-client"), "second-client");
        try {
            EffectiveCachePolicy.Selection compact = selection("compact", 1_000, 1, 3L, false);
            EffectiveCachePolicy.Selection broad = selection("broad", 1_000, 2, 10L, false);
            assertThat(weightedCached(first, compact, key("first-compact"), Mono.just("a"), 3).block())
                    .isEqualTo("a");
            assertThat(weightedCached(first, broad, key("first-broad-a"), Mono.just("b"), 4).block())
                    .isEqualTo("b");
            assertThat(weightedCached(first, broad, key("first-broad-b"), Mono.just("c"), 4).block())
                    .isEqualTo("c");
            assertThat(weightedCached(second, compact, key("second-compact"), Mono.just("d"), 2).block())
                    .isEqualTo("d");

            assertThat(first.snapshot()).isEqualTo(
                    new LocalResponseCacheManager.Snapshot(2, 3, 3, 0, false));
            assertThat(second.snapshot()).isEqualTo(
                    new LocalResponseCacheManager.Snapshot(1, 1, 1, 0, false));
            assertThat(first.retainedDecodedResponseBytesForTesting()).isEqualTo(11);
            assertThat(second.retainedDecodedResponseBytesForTesting()).isEqualTo(2);
            assertManagerTotals(first);
            assertManagerTotals(second);

            assertThat(registry.get(LocalResponseCacheMetrics.PREFIX + ".entries")
                    .tags("client.name", "first-client", "cache.policy", "compact")
                    .gauge().value()).isEqualTo(1);
            assertThat(registry.get(LocalResponseCacheMetrics.PREFIX + ".entries")
                    .tags("client.name", "second-client", "cache.policy", "compact")
                    .gauge().value()).isEqualTo(1);
            first.close();
            assertThat(registry.get(LocalResponseCacheMetrics.PREFIX + ".entries")
                    .tags("client.name", "second-client", "cache.policy", "compact")
                    .gauge().value()).isEqualTo(1);
        }
        finally {
            first.close();
            second.close();
            registry.close();
        }
    }

    private static void verifyFlightTerminalCleanup(LocalResponseCacheManager manager) throws Exception {
        EffectiveCachePolicy.Selection selection = selection("flight", 60_000, 8, null, true);
        Sinks.One<String> source = Sinks.one();
        Mono<String> call = cached(manager, selection, key("flight"), source.asMono());
        List<Disposable> subscriptions = new ArrayList<>();
        AtomicInteger received = new AtomicInteger();
        for (int index = 0; index < 32; index++) {
            subscriptions.add(call.subscribe(ignored -> received.incrementAndGet()));
        }
        assertThat(manager.workloadSnapshotForTesting())
                .extracting(LocalResponseCacheManager.WorkloadSnapshot::inFlightLoads,
                        LocalResponseCacheManager.WorkloadSnapshot::coalescedWaiters)
                .containsExactly(1, 31);

        Object flight = privateMap(manager, "inFlightLoads").values().iterator().next();
        @SuppressWarnings("unchecked")
        Set<Object> members = (Set<Object>) field(flight, "members");
        List<Object> reservedMembers = List.copyOf(members);
        for (int index = 16; index < subscriptions.size(); index++) {
            subscriptions.get(index).dispose();
        }
        assertThat(manager.workloadSnapshotForTesting().coalescedWaiters()).isEqualTo(15);
        source.tryEmitValue("shared").orThrow();

        assertThat(received).hasValue(16);
        assertThat(privateMap(manager, "inFlightLoads")).isEmpty();
        assertThat(members).isEmpty();
        assertThat(field(flight, "diagnosticOwner")).isNull();
        assertThat(reservedMembers).allSatisfy(member -> {
            AtomicReference<Boolean> released = new AtomicReference<>();
            try {
                released.set(((java.util.concurrent.atomic.AtomicBoolean) field(member, "released")).get());
            }
            catch (Exception error) {
                throw new AssertionError(error);
            }
            assertThat(released.get()).isTrue();
        });
    }

    private static void verifyEvictionInvalidatesPublicationWithoutCancellingCaller(
            LocalResponseCacheManager manager) throws Exception {
        EffectiveCachePolicy.Selection selection = selection("eviction", 60_000, 8, null, false);
        Sinks.One<String> source = Sinks.one();
        AtomicInteger cancellations = new AtomicInteger();
        CompletableFuture<String> result = cached(
                manager, selection, key("eviction"),
                source.asMono().doOnCancel(cancellations::incrementAndGet)).toFuture();

        manager.evictAllForTesting();
        source.tryEmitValue("caller-visible").orThrow();
        assertThat(result.get(1, TimeUnit.SECONDS)).isEqualTo("caller-visible");
        assertThat(cancellations).hasValue(0);
        assertThat(manager.snapshot().currentSize()).isZero();
        assertAllGenerations(manager, 0);
    }

    private static void runStorageScenario(CaffeineLocalResponseCache cache,
                                           AtomicLong ticker,
                                           Random random,
                                           int iteration,
                                           int scenario) {
        CacheKeyContract.OpaqueKey key = key("stress-" + iteration);
        switch (scenario) {
            case 0 -> {
                LocalResponseCache.Lookup first = cache.lookup(key);
                LocalResponseCache.Lookup second = cache.lookup(key);
                if (random.nextBoolean()) {
                    cache.publish(first.loadToken(), "first", 1 + random.nextInt(8));
                    cache.publish(second.loadToken(), "second", 1 + random.nextInt(8));
                }
                else {
                    cache.publish(second.loadToken(), "second", 1 + random.nextInt(8));
                    cache.publish(first.loadToken(), "first", 1 + random.nextInt(8));
                }
                cache.finish(first.loadToken());
                cache.finish(second.loadToken());
            }
            case 1 -> publish(cache, key, "immediate", 1 + random.nextInt(16));
            case 2 -> {
                publish(cache, key, "stale", 1 + random.nextInt(8));
                LocalResponseCache.Lookup hit = cache.lookup(key);
                if (hit.hit()) {
                    LocalResponseCache.RefreshToken refresh = cache.beginRefresh(hit.entryToken());
                    cache.publishRefresh(refresh, "refreshed", 1 + random.nextInt(16));
                    cache.finishRefresh(refresh);
                }
                else {
                    cache.finish(hit.loadToken());
                }
            }
            case 3 -> {
                LocalResponseCache.Lookup delayed = cache.lookup(key);
                cache.invalidateAll();
                cache.publish(delayed.loadToken(), "late", 4);
                cache.finish(delayed.loadToken());
            }
            case 4 -> {
                publish(cache, key, "expires", 2);
                ticker.addAndGet(Duration.ofMillis(25).toNanos());
                LocalResponseCache.Lookup expired = cache.lookup(key);
                if (!expired.hit()) {
                    cache.finish(expired.loadToken());
                }
            }
            case 5 -> cache.invalidateAll();
            default -> throw new IllegalArgumentException("Unknown stress scenario " + scenario);
        }
    }

    private static void runManagerScenario(int iteration, int scenario) throws Exception {
        LocalResponseCacheManager manager = LocalResponseCacheManager.testing(System::nanoTime);
        try {
            switch (scenario) {
                case 6 -> {
                    EffectiveCachePolicy.Selection selection =
                            selection("delayed-" + iteration, 60_000, 4, null, false);
                    Sinks.One<String> source = Sinks.one();
                    CompletableFuture<String> result = cached(
                            manager, selection, key("delayed-" + iteration), source.asMono()).toFuture();
                    assertThat(result).isNotDone();
                    source.tryEmitValue("delayed").orThrow();
                    assertThat(result.get(1, TimeUnit.SECONDS)).isEqualTo("delayed");
                    assertThat(manager.snapshot().currentSize()).isEqualTo(1);
                }
                case 7 -> {
                    EffectiveCachePolicy.Selection selection =
                            selection("timeout-" + iteration, 60_000, 4, null, false);
                    VirtualTimeScheduler scheduler = VirtualTimeScheduler.create();
                    try {
                        CompletableFuture<String> result = cached(
                                manager, selection, key("timeout-" + iteration), Mono.<String>never())
                                .timeout(Duration.ofMillis(1), scheduler)
                                .toFuture();
                        scheduler.advanceTimeBy(Duration.ofMillis(1));
                        assertThatThrownBy(result::join).hasCauseInstanceOf(TimeoutException.class);
                        assertThat(manager.snapshot().currentSize()).isZero();
                        assertAllGenerations(manager, 0);
                    }
                    finally {
                        scheduler.dispose();
                    }
                }
                case 8 -> {
                    EffectiveCachePolicy.Selection selection =
                            selection("cancel-" + iteration, 60_000, 4, null, true);
                    AtomicInteger cancellations = new AtomicInteger();
                    Mono<String> call = cached(
                            manager, selection, key("cancel-" + iteration),
                            Mono.<String>never().doOnCancel(cancellations::incrementAndGet));
                    Disposable leader = call.subscribe();
                    Disposable waiter = call.subscribe();
                    leader.dispose();
                    assertThat(cancellations).hasValue(0);
                    waiter.dispose();
                    assertThat(cancellations).hasValue(1);
                    assertThat(manager.workloadSnapshotForTesting().inFlightLoads()).isZero();
                    assertAllGenerations(manager, 0);
                }
                case 9 -> {
                    EffectiveCachePolicy.Selection selection =
                            selection("shutdown-" + iteration, 60_000, 4, null, true);
                    AtomicInteger cancellations = new AtomicInteger();
                    Mono<String> call = cached(
                            manager, selection, key("shutdown-" + iteration),
                            Mono.<String>never().doOnCancel(cancellations::incrementAndGet));
                    CompletableFuture<String> leader = call.toFuture();
                    CompletableFuture<String> waiter = call.toFuture();
                    manager.close();
                    assertThat(leader.get(1, TimeUnit.SECONDS)).isNull();
                    assertThat(waiter.get(1, TimeUnit.SECONDS)).isNull();
                    assertThat(cancellations).hasValue(1);
                    assertThat(manager.snapshot().closed()).isTrue();
                }
                default -> throw new IllegalArgumentException("Unknown manager stress scenario " + scenario);
            }
        }
        finally {
            manager.close();
        }
    }

    private static void assertExactTotals(
            CaffeineLocalResponseCache cache, long maximumSize, long maximumWeight) throws Exception {
        CacheAudit audit = audit(cache);
        assertThat(audit.entryCount()).isBetween(0L, maximumSize);
        assertThat(audit.reportedWeight()).isBetween(0L, maximumWeight);
        assertThat(audit.reportedWeight()).isEqualTo(audit.actualWeight());
        assertThat(audit.generationCount()).isEqualTo(Math.toIntExact(audit.entryCount()));
    }

    private static void assertManagerTotals(LocalResponseCacheManager manager) throws Exception {
        long entries = 0;
        long reportedWeight = 0;
        for (Object candidate : privateMap(manager, "caches").values()) {
            CacheAudit audit = audit((CaffeineLocalResponseCache) candidate);
            entries = Math.addExact(entries, audit.entryCount());
            reportedWeight = Math.addExact(reportedWeight, audit.reportedWeight());
            assertThat(audit.reportedWeight()).isEqualTo(audit.actualWeight());
        }
        assertThat(manager.snapshot().currentSize()).isEqualTo(entries);
        assertThat(manager.retainedDecodedResponseBytesForTesting()).isEqualTo(reportedWeight);
    }

    private static CacheAudit audit(CaffeineLocalResponseCache cache) throws Exception {
        long reportedWeight = cache.retainedDecodedResponseBytes();
        @SuppressWarnings("unchecked")
        Cache<Object, Object> storage = (Cache<Object, Object>) field(cache, "cache");
        long actualWeight = 0;
        for (Object stored : storage.asMap().values()) {
            Field weight = stored.getClass().getDeclaredField("weight");
            weight.setAccessible(true);
            long storedWeight = weight.getLong(stored);
            actualWeight = Math.addExact(actualWeight, storedWeight);
            if (stored.getClass().getSimpleName().equals("WeightedCachedEntry")) {
                Field accounted = stored.getClass().getDeclaredField("accounted");
                accounted.setAccessible(true);
                assertThat(accounted.getBoolean(stored)).isTrue();
            }
        }
        return new CacheAudit(
                storage.asMap().size(), reportedWeight, actualWeight,
                privateMap(cache, "generations").size());
    }

    private static void assertAllGenerations(LocalResponseCacheManager manager, int expected) throws Exception {
        for (Object candidate : privateMap(manager, "caches").values()) {
            assertThat(privateMap(candidate, "generations")).hasSize(expected);
        }
    }

    private static void publish(CaffeineLocalResponseCache cache,
                                CacheKeyContract.OpaqueKey key,
                                Object value,
                                long weight) {
        LocalResponseCache.Lookup lookup = cache.lookup(key);
        assertThat(lookup.hit()).isFalse();
        cache.publish(lookup.loadToken(), value, weight);
        cache.finish(lookup.loadToken());
    }

    private static Object value(CaffeineLocalResponseCache cache, CacheKeyContract.OpaqueKey key) {
        LocalResponseCache.Lookup lookup = cache.lookup(key);
        if (lookup.hit()) {
            return lookup.value();
        }
        cache.finish(lookup.loadToken());
        return null;
    }

    @SuppressWarnings("unchecked")
    private static <T> Mono<T> cached(LocalResponseCacheManager manager,
                                      EffectiveCachePolicy.Selection selection,
                                      CacheKeyContract.OpaqueKey key,
                                      Mono<T> loader) {
        return (Mono<T>) manager.getOrLoad(selection, key, () -> loader);
    }

    @SuppressWarnings("unchecked")
    private static <T> Mono<T> weightedCached(LocalResponseCacheManager manager,
                                              EffectiveCachePolicy.Selection selection,
                                              CacheKeyContract.OpaqueKey key,
                                              Mono<T> loader,
                                              long decodedResponseBytes) {
        return (Mono<T>) manager.getOrLoad(selection, key, () -> loader, () -> {
            LocalResponseCacheManager.DecodedResponseBytes measurement =
                    new LocalResponseCacheManager.DecodedResponseBytes(decodedResponseBytes);
            measurement.add(decodedResponseBytes);
            measurement.complete();
            return new LocalResponseCacheManager.ResponseMetadata(200, Map.of(), true, measurement);
        });
    }

    private static EffectiveCachePolicy.Selection selection(String name,
                                                            long ttlMs,
                                                            long maximumSize,
                                                            Long maximumWeight,
                                                            boolean singleFlight) {
        ReactiveHttpClientProperties.CachePolicyConfig policy =
                new ReactiveHttpClientProperties.CachePolicyConfig();
        policy.setTtlMs(ttlMs);
        policy.setMaximumSize(maximumSize);
        policy.setMaximumTotalDecodedResponseBytes(maximumWeight);
        policy.setSingleFlight(singleFlight);
        return new EffectiveCachePolicy.Selection(true, EffectiveCachePolicy.Source.CLIENT, name, policy);
    }

    private static CacheKeyContract.OpaqueKey key(String value) {
        return CacheKeyContract.OpaqueKey.from(value.getBytes(StandardCharsets.UTF_8));
    }

    @SuppressWarnings("unchecked")
    private static Map<Object, Object> privateMap(Object target, String fieldName) throws Exception {
        return (Map<Object, Object>) field(target, fieldName);
    }

    private static Object field(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void writeStressEvidence(int completed, int[] scenarios, String failure) throws Exception {
        Path report = projectRoot().resolve(
                "target/release-evidence/v29/priority6/cache-concurrency-invariants.properties");
        Files.createDirectories(report.getParent());
        StringBuilder contents = new StringBuilder()
                .append("format=v29-cache-capacity-concurrency-v1\n")
                .append("seed=").append(STRESS_SEED).append('\n')
                .append("configuredIterations=").append(STRESS_ITERATIONS).append('\n')
                .append("completedIterations=").append(completed).append('\n')
                .append("metadataKeys=").append(METADATA_KEYS).append('\n')
                .append("maximumEntries=16\n")
                .append("maximumDecodedResponseBytes=64\n")
                .append("failure=").append(failure.replace('\n', ' ').replace('\r', ' ')).append('\n');
        for (int index = 0; index < scenarios.length; index++) {
            contents.append("scenario.").append(index).append(".iterations=")
                    .append(scenarios[index]).append('\n');
        }
        Files.writeString(report, contents);
    }

    private static Path projectRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        return Files.exists(current.resolve("README.md")) ? current : current.getParent();
    }

    private record CacheAudit(
            long entryCount, long reportedWeight, long actualWeight, int generationCount) {
    }

    private static final class OpaqueValue {
        private final int id;

        private OpaqueValue(int id) {
            this.id = id;
        }

        @Override
        public boolean equals(Object other) {
            throw new AssertionError("cache accounting must not inspect response values");
        }

        @Override
        public int hashCode() {
            throw new AssertionError("cache accounting must not inspect response values");
        }

        @Override
        public String toString() {
            throw new AssertionError("cache accounting must not render response values");
        }
    }
}
