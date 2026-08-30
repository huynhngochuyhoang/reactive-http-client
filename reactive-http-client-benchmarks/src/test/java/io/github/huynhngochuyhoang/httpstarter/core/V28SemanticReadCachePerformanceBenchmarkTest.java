package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import org.junit.jupiter.api.Test;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class V28SemanticReadCachePerformanceBenchmarkTest {

    @Test
    void everySemanticPostBenchmarkRowExercisesItsDeclaredPath() throws Exception {
        V28SemanticReadCachePerformanceBenchmark benchmark =
                new V28SemanticReadCachePerformanceBenchmark();
        benchmark.setup();
        try {
            assertThat(benchmark.cacheKeySemanticPostJsonBody()).isNotNull();
            assertThat(benchmark.cacheSemanticPostNoNetworkMiss()).isNotNull();
            assertThat(benchmark.cacheSemanticPostNoNetworkHit()).isNotNull();
            assertThat(benchmark.cacheSemanticPostNoNetworkCoalescedWaiter()).hasSize(2);
            assertThat(benchmark.cacheSemanticPostNoNetworkCancelledFlight()).isTrue();
            assertThat(benchmark.cacheSemanticPostNoNetworkRefreshCleanup()).isNotNull();
            assertThat(benchmark.cacheLoopbackStarterSemanticPostMiss()).isNotNull();
            assertThat(benchmark.cacheLoopbackStarterSemanticPostHit()).isNotNull();
            assertThat(benchmark.cacheLoopbackStarterSemanticPostCoalescedWaiter()).hasSize(2);
            assertThat(benchmark.cacheLoopbackStarterSemanticPostRefreshOnAccess()).isNotNull();
        }
        finally {
            benchmark.tearDown();
        }
    }

    @Test
    void bodyAndInvocationStateAreReleasedAtEveryCacheOwnershipBoundary() throws Exception {
        AtomicLong ticker = new AtomicLong();
        LocalResponseCacheManager manager = LocalResponseCacheManager.testing(ticker::get);
        EffectiveCachePolicy.Selection selected = selection("retention", 10, 2, true);
        CacheKeyContract.OpaqueKey completionKey = key("completion");
        byte[] serializedBody = "{\"term\":\"completion\"}".getBytes(StandardCharsets.UTF_8);
        Object frozenArguments = new Object();
        Object authContext = new Object();
        Object callerState = new Object();
        Object response = new Object();

        Object completed = manager.getOrLoad(selected, completionKey, () -> Mono.deferContextual(context -> {
                    assertThat((Object) context.get("auth")).isSameAs(authContext);
                    assertThat(serializedBody).isNotEmpty();
                    assertThat(frozenArguments).isNotNull();
                    assertThat(callerState).isNotNull();
                    return Mono.just(response);
                }))
                .contextWrite(context -> context.put("auth", authContext))
                .block();

        assertThat(completed).isSameAs(response);
        assertThat(privateMap(manager, "inFlightLoads")).isEmpty();
        assertThat(privateMap(manager, "inFlightRefreshes")).isEmpty();
        assertStoredValue(manager, response);

        manager.evictAllForTesting();
        assertThat(manager.snapshot().currentSize()).isZero();

        Object expiringResponse = new Object();
        CacheKeyContract.OpaqueKey expiryKey = key("expiry");
        assertThat(manager.getOrLoad(selected, expiryKey, () -> Mono.just(expiringResponse)).block())
                .isSameAs(expiringResponse);
        ticker.addAndGet(Duration.ofMillis(10).toNanos());
        Object replacement = new Object();
        assertThat(manager.getOrLoad(selected, expiryKey, () -> Mono.just(replacement)).block())
                .isSameAs(replacement);
        assertStoredValue(manager, replacement);

        AtomicBoolean cancelled = new AtomicBoolean();
        Disposable cancellation = manager.getOrLoad(
                        selected,
                        key("cancel"),
                        () -> Mono.never().doOnCancel(() -> cancelled.set(true)))
                .subscribe();
        assertThat(manager.hasInFlightLoadWithMembersForTesting(1)).isTrue();
        cancellation.dispose();
        await(cancelled);
        assertThat(privateMap(manager, "inFlightLoads")).isEmpty();

        AtomicBoolean shutdownCancelled = new AtomicBoolean();
        manager.getOrLoad(
                        selected,
                        key("shutdown"),
                        () -> Mono.never().doOnCancel(() -> shutdownCancelled.set(true)))
                .subscribe();
        assertThat(manager.hasInFlightLoadWithMembersForTesting(1)).isTrue();
        manager.close();
        await(shutdownCancelled);
        assertThat(manager.snapshot()).isEqualTo(
                new LocalResponseCacheManager.Snapshot(0, 0, 0, true));
        assertThat(privateMap(manager, "inFlightLoads")).isEmpty();
        assertThat(privateMap(manager, "inFlightRefreshes")).isEmpty();
    }

    private static void assertStoredValue(LocalResponseCacheManager manager, Object expected)
            throws Exception {
        Map<?, ?> caches = privateMap(manager, "caches");
        assertThat(caches).hasSize(1);
        CaffeineLocalResponseCache cache =
                (CaffeineLocalResponseCache) caches.values().iterator().next();
        Field storageField = CaffeineLocalResponseCache.class.getDeclaredField("cache");
        storageField.setAccessible(true);
        com.github.benmanes.caffeine.cache.Cache<?, ?> storage =
                (com.github.benmanes.caffeine.cache.Cache<?, ?>) storageField.get(cache);
        storage.cleanUp();
        assertThat(storage.asMap()).hasSize(1);
        Object cachedEntry = storage.asMap().values().iterator().next();
        Field valueField = cachedEntry.getClass().getDeclaredField("value");
        valueField.setAccessible(true);
        assertThat(valueField.get(cachedEntry)).isSameAs(expected);
    }

    @SuppressWarnings("unchecked")
    private static Map<?, ?> privateMap(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return (Map<?, ?>) field.get(target);
    }

    private static void await(AtomicBoolean condition) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
        while (!condition.get() && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        assertThat(condition).isTrue();
    }

    private static CacheKeyContract.OpaqueKey key(String value) {
        return CacheKeyContract.OpaqueKey.from(value.getBytes(StandardCharsets.UTF_8));
    }

    private static EffectiveCachePolicy.Selection selection(
            String name, long ttlMs, long maximumSize, boolean singleFlight) {
        ReactiveHttpClientProperties.CachePolicyConfig policy =
                new ReactiveHttpClientProperties.CachePolicyConfig();
        policy.setTtlMs(ttlMs);
        policy.setMaximumSize(maximumSize);
        policy.setSingleFlight(singleFlight);
        policy.setSharedResponse(true);
        return new EffectiveCachePolicy.Selection(
                true, EffectiveCachePolicy.Source.CLIENT, name, policy);
    }
}
