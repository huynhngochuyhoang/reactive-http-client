package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.test.scheduler.VirtualTimeScheduler;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

@org.junit.jupiter.api.Timeout(30)
class CacheRefreshAdmissionContractTest {
    @org.junit.jupiter.api.Test
    void skippedTriggersDoNotProduceRefreshTerminalsOrChangeStoredWeight() {
        AtomicLong time = new AtomicLong();
        var scheduler = VirtualTimeScheduler.create();
        var config = CacheWorkPolicyEnforcementTest.config(true);
        config.getCache().getPolicies().get("work").setMaximumTotalDecodedResponseBytes(100L);
        var metadata = new MethodMetadataCache();
        var registry = new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
        try (var manager = LocalResponseCacheManager.createForClient(CacheWorkPolicyEnforcementTest.Client.class,
                     "work", metadata, config, getClass().getClassLoader(), time::get, scheduler,
                     LocalResponseCacheMetrics.enabled(registry, "work"), true)) {
            var selection = new EffectiveCachePolicy.Selection(true, EffectiveCachePolicy.Source.CLIENT,
                    "work", config.getCache().getPolicies().get("work"));
            call(manager, selection, "a", () -> Mono.just("a")).block();
            call(manager, selection, "b", () -> Mono.just("b")).block();
            var before = manager.snapshot();
            time.set(Duration.ofSeconds(2).toNanos());
            Sinks.One<String> source = Sinks.one();
            call(manager, selection, "a", source::asMono).block();
            AtomicInteger skipped = new AtomicInteger();
            for (int i = 0; i < 10; i++) {
                call(manager, selection, "b", () -> {
                    skipped.incrementAndGet(); return Mono.just("unexpected");
                }).block();
            }
            assertThat(skipped).hasValue(0);
            assertThat(manager.snapshot()).isEqualTo(before);
            assertThat(registry.find(LocalResponseCacheMetrics.PREFIX + ".refreshes").counters().stream()
                    .mapToDouble(io.micrometer.core.instrument.Counter::count).sum()).isZero();
            source.tryEmitEmpty().orThrow();
            assertThat(registry.find(LocalResponseCacheMetrics.PREFIX + ".refreshes").counters().stream()
                    .mapToDouble(io.micrometer.core.instrument.Counter::count).sum()).isEqualTo(1);
            assertThat(manager.snapshot()).isEqualTo(before);
            assertThat(manager.activeRefreshesForTesting("work")).isZero();
        } finally {
            registry.close();
            scheduler.dispose();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void timeoutAndCloseWhileAssemblingCannotStartLateSource(boolean close) throws Exception {
        AtomicLong time = new AtomicLong();
        var scheduler = VirtualTimeScheduler.create();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (var manager = LocalResponseCacheManager.testing(time::get, scheduler,
                Map.of("work", 8), Map.of("work", 1), Map.of("work", 1))) {
            var policy = policy(true);
            policy.policy().setRefreshTimeoutMs(2_000L);
            call(manager, policy, "a", () -> Mono.just("a")).block();
            time.set(Duration.ofSeconds(2).toNanos());
            AtomicInteger subscriptions = new AtomicInteger();
            Future<?> caller = executor.submit(() -> call(manager, policy, "a", () -> {
                entered.countDown();
                try {
                    if (!release.await(10, TimeUnit.SECONDS)) { throw new AssertionError("assembly gate"); }
                } catch (InterruptedException error) { throw new AssertionError(error); }
                return Mono.defer(() -> { subscriptions.incrementAndGet(); return Mono.just("late"); });
            }).block());
            assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
            if (close) {
                manager.close();
            } else {
                time.addAndGet(Duration.ofSeconds(2).toNanos());
                scheduler.advanceTimeBy(Duration.ofSeconds(2));
            }
            assertThat(manager.activeRefreshesForTesting("work")).isEqualTo(1);
            release.countDown();
            caller.get(10, TimeUnit.SECONDS);
            assertThat(subscriptions).hasValue(0);
            assertThat(manager.activeRefreshesForTesting("work")).isZero();
            assertThat(manager.workloadSnapshotForTesting().inFlightRefreshes()).isZero();
        } finally {
            release.countDown();
            executor.shutdownNow();
            scheduler.dispose();
        }
    }

    enum End { SUCCESS, EMPTY, FAILURE, ASSEMBLY_FAILURE, METADATA_FAILURE, TIMEOUT, HARD_EXPIRY, EVICTION, CLOSE }

    @ParameterizedTest
    @EnumSource(End.class)
    void everyTerminalReleasesOneRefreshAndLaterAccessCanRetry(End end) {
        AtomicLong time = new AtomicLong();
        var scheduler = VirtualTimeScheduler.create();
        try (var manager = LocalResponseCacheManager.testing(time::get, scheduler,
                Map.of("work", 8), Map.of("work", 1), Map.of("work", 1))) {
            var policy = policy(true);
            if (end == End.TIMEOUT) {
                policy.policy().setRefreshTimeoutMs(2_000L);
            }
            assertThat(call(manager, policy, "a", () -> Mono.just("old")).block()).isEqualTo("old");
            time.set(Duration.ofSeconds(2).toNanos());
            Sinks.One<String> source = Sinks.one();
            AtomicInteger assemblies = new AtomicInteger();
            Mono<?> hit = manager.getOrLoad(policy, CacheKeyContract.OpaqueKey.from(new byte[]{97}), () -> {
                assemblies.incrementAndGet();
                if (end == End.ASSEMBLY_FAILURE) {
                    throw new IllegalArgumentException("assembly");
                }
                return source.asMono();
            }, () -> {
                if (end == End.METADATA_FAILURE) {
                    throw new IllegalArgumentException("metadata");
                }
                return LocalResponseCacheManager.ResponseMetadata.successWithoutHeaders();
            });
            assertThat(hit.block()).isEqualTo("old");
            assertThat(assemblies).hasValue(1);
            switch (end) {
                case SUCCESS, METADATA_FAILURE -> source.tryEmitValue("new").orThrow();
                case EMPTY -> source.tryEmitEmpty().orThrow();
                case FAILURE -> source.tryEmitError(new IllegalStateException("load")).orThrow();
                case ASSEMBLY_FAILURE -> { }
                case TIMEOUT -> {
                    time.addAndGet(Duration.ofSeconds(2).toNanos());
                    scheduler.advanceTimeBy(Duration.ofSeconds(2));
                }
                case HARD_EXPIRY -> {
                    time.set(Duration.ofSeconds(10).toNanos());
                    scheduler.advanceTimeBy(Duration.ofSeconds(8));
                }
                case EVICTION -> manager.evictAllForTesting();
                case CLOSE -> manager.close();
            }
            assertThat(manager.activeRefreshesForTesting("work")).isZero();
            assertThat(manager.workloadSnapshotForTesting().inFlightRefreshes()).isZero();
            if (end != End.CLOSE && end != End.SUCCESS) {
                AtomicInteger replacement = new AtomicInteger();
                call(manager, policy, "a", () -> {
                    replacement.incrementAndGet();
                    return Mono.just("replacement");
                }).block();
                assertThat(replacement).hasValue(1);
                assertThat(call(manager, policy, "a", Mono::never).block()).isEqualTo("replacement");
                assertThat(manager.activeRefreshesForTesting("work")).isZero();
            }
        } finally {
            scheduler.dispose();
        }
    }

    enum Frame { ASSEMBLY, SUBSCRIPTION, PUBLICATION, CANCELLATION }

    @ParameterizedTest
    @EnumSource(Frame.class)
    void removalKeepsCapacityUntilEnteredFramesUnwind(Frame frame) throws Exception {
        AtomicLong time = new AtomicLong();
        var scheduler = VirtualTimeScheduler.create();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Runnable gate = () -> {
            entered.countDown();
            try {
                if (!release.await(10, TimeUnit.SECONDS)) {
                    throw new AssertionError("gate did not open");
                }
            } catch (InterruptedException error) {
                throw new AssertionError(error);
            }
        };
        try (var manager = LocalResponseCacheManager.testing(time::get, scheduler,
                Map.of("work", 8), Map.of("work", 1), Map.of("work", 1))) {
            var policy = policy(true);
            call(manager, policy, "a", () -> Mono.just("a")).block();
            call(manager, policy, "b", () -> Mono.just("b")).block();
            time.set(Duration.ofSeconds(2).toNanos());
            Sinks.One<String> source = Sinks.one();
            AtomicInteger subscriptions = new AtomicInteger();
            Mono<?> hit = manager.getOrLoad(policy, CacheKeyContract.OpaqueKey.from(new byte[]{97}), () -> {
                if (frame == Frame.ASSEMBLY) { gate.run(); }
                return Mono.defer(() -> {
                    subscriptions.incrementAndGet();
                    if (frame == Frame.SUBSCRIPTION) { gate.run(); }
                    return source.asMono().doOnCancel(() -> {
                        if (frame == Frame.CANCELLATION) { gate.run(); }
                    });
                });
            }, () -> {
                if (frame == Frame.PUBLICATION) { gate.run(); }
                return LocalResponseCacheManager.ResponseMetadata.successWithoutHeaders();
            });
            Future<?> task;
            if (frame == Frame.ASSEMBLY || frame == Frame.SUBSCRIPTION) {
                task = executor.submit(() -> hit.block());
            } else {
                assertThat(hit.block()).isEqualTo("a");
                task = executor.submit(frame == Frame.PUBLICATION
                        ? () -> source.tryEmitValue("new").orThrow()
                        : manager::close);
            }
            assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
            if (frame != Frame.CANCELLATION) { manager.evictAllForTesting(); }
            assertThat(manager.activeRefreshesForTesting("work")).isEqualTo(1);
            // Removal erased the old entries, but the refresh owner must not release early.
            if (frame != Frame.CANCELLATION) {
                call(manager, policy, "b", () -> Mono.just("b")).block();
                time.addAndGet(Duration.ofSeconds(2).toNanos());
                AtomicInteger skipped = new AtomicInteger();
                assertThat(call(manager, policy, "b", () -> {
                    skipped.incrementAndGet(); return Mono.just("unexpected");
                }).block()).isEqualTo("b");
                assertThat(skipped).hasValue(0);
            }
            release.countDown();
            task.get(10, TimeUnit.SECONDS);
            assertThat(manager.activeRefreshesForTesting("work")).isZero();
            if (frame == Frame.ASSEMBLY) { assertThat(subscriptions).hasValue(0); }
            if (frame != Frame.CANCELLATION) {
                assertThat(call(manager, policy, "a", () -> Mono.just("replacement")).block()).isEqualTo("replacement");
            }
        } finally {
            release.countDown();
            executor.shutdownNow();
            scheduler.dispose();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void manyKeysShareRefreshCapacityButOtherPoliciesAndFactoriesDoNot(boolean single) throws Exception {
        AtomicLong time = new AtomicLong();
        var scheduler = VirtualTimeScheduler.create();
        ExecutorService executor = Executors.newFixedThreadPool(16);
        try (var manager = LocalResponseCacheManager.testing(time::get, scheduler,
                Map.of("work", 128, "other", 128), Map.of("work", 1, "other", 1),
                Map.of("work", 3, "other", 1));
             var another = LocalResponseCacheManager.testing(time::get, scheduler,
                     Map.of("work", 128), Map.of("work", 1), Map.of("work", 1))) {
            var policy = policy(single);
            var other = new EffectiveCachePolicy.Selection(true, EffectiveCachePolicy.Source.METHOD,
                    "other", policy.policy());
            for (int i = 0; i < 64; i++) {
                String value = "v" + i;
                call(manager, policy, value, () -> Mono.just(value)).block();
            }
            call(manager, other, "other", () -> Mono.just("other")).block();
            call(another, policy, "another", () -> Mono.just("another")).block();
            time.set(Duration.ofSeconds(2).toNanos());
            AtomicInteger assemblies = new AtomicInteger();
            List<Future<?>> hits = new ArrayList<>();
            for (int i = 0; i < 64; i++) {
                String key = "v" + i;
                hits.add(executor.submit(() -> assertThat(call(manager, policy, key, () -> {
                    assemblies.incrementAndGet(); return Mono.never();
                }).block()).isEqualTo(key)));
            }
            for (Future<?> hit : hits) { hit.get(10, TimeUnit.SECONDS); }
            assertThat(assemblies).hasValue(3);
            assertThat(manager.activeRefreshesForTesting("work")).isEqualTo(3);
            call(manager, other, "other", Mono::never).block();
            call(another, policy, "another", Mono::never).block();
            assertThat(manager.activeRefreshesForTesting("other")).isEqualTo(1);
            assertThat(another.activeRefreshesForTesting("work")).isEqualTo(1);
            manager.close();
            assertThat(manager.activeRefreshesForTesting("work")).isZero();
            assertThat(manager.activeRefreshesForTesting("other")).isZero();
            assertThat(another.activeRefreshesForTesting("work")).isEqualTo(1);
        } finally {
            executor.shutdownNow();
            scheduler.dispose();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void saturationSkipsWithoutQueueingOrExtendingTtlAndLeavesForegroundCapacity(boolean single) {
        AtomicLong time = new AtomicLong();
        var scheduler = VirtualTimeScheduler.create();
        try (var manager = LocalResponseCacheManager.testing(time::get, scheduler,
                Map.of("work", 8), Map.of("work", 1), Map.of("work", 1))) {
            var selection = policy(single);
            assertThat(call(manager, selection, "a", () -> Mono.just("a")).block()).isEqualTo("a");
            assertThat(call(manager, selection, "b", () -> Mono.just("b")).block()).isEqualTo("b");
            time.set(Duration.ofSeconds(2).toNanos());
            Sinks.One<String> refresh = Sinks.one();
            assertThat(call(manager, selection, "a", refresh::asMono).block()).isEqualTo("a");
            AtomicInteger skipped = new AtomicInteger();
            Supplier<Mono<?>> later = () -> { skipped.incrementAndGet(); return Mono.empty(); };
            for (int i = 0; i < 10; i++) {
                assertThat(call(manager, selection, "b", later).block()).isEqualTo("b");
                assertThat(call(manager, selection, "a", later).block()).isEqualTo("a");
            }
            assertThat(skipped).hasValue(0);
            assertThat(manager.activeRefreshesForTesting("work")).isEqualTo(1);
            var foreground = call(manager, selection, "foreground", Mono::never).toFuture();
            assertThat(manager.activeLoadsForTesting("work")).isEqualTo(1);
            refresh.tryEmitError(new IllegalStateException("refresh")).orThrow();
            assertThat(manager.activeRefreshesForTesting("work")).isZero();
            assertThat(skipped).hasValue(0);
            assertThat(call(manager, selection, "b", later).block()).isEqualTo("b");
            assertThat(skipped).hasValue(1);
            assertThat(manager.activeRefreshesForTesting("work")).isZero();
            foreground.cancel(true);
            time.set(Duration.ofSeconds(10).toNanos());
            assertThat(call(manager, selection, "b", () -> Mono.just("new")).block()).isEqualTo("new");
            assertThat(manager.activeLoadsForTesting("work")).isZero();
        } finally {
            scheduler.dispose();
        }
    }

    static EffectiveCachePolicy.Selection policy(boolean single) {
        var policy = new ReactiveHttpClientProperties.CachePolicyConfig();
        policy.setTtlMs(10_000L);
        policy.setMaximumSize(100L);
        policy.setRefreshAfterMs(1_000L);
        policy.setRefreshTimeoutMs(20_000L);
        policy.setSingleFlight(single);
        return new EffectiveCachePolicy.Selection(true, EffectiveCachePolicy.Source.CLIENT, "work", policy);
    }

    static Mono<?> call(LocalResponseCacheManager manager, EffectiveCachePolicy.Selection policy,
                        String key, Supplier<Mono<?>> loader) {
        return manager.getOrLoad(policy, CacheKeyContract.OpaqueKey.from(key.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                loader, () -> {
                    if (policy.policy().getMaximumTotalDecodedResponseBytes() == null) {
                        return LocalResponseCacheManager.ResponseMetadata.successWithoutHeaders();
                    }
                    var bytes = new LocalResponseCacheManager.DecodedResponseBytes(100);
                    bytes.add(1);
                    bytes.complete();
                    return new LocalResponseCacheManager.ResponseMetadata(200, Map.of(), true, bytes);
                });
    }
}
