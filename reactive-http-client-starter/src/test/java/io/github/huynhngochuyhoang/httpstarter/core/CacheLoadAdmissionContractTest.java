package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CacheLoadAdmissionContractTest {
    private static final Duration WAIT = Duration.ofSeconds(10);

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void rejectsBeforeAssemblyAndKeepsHitsAndJoinsIndependent(boolean singleFlight) {
        try (LocalResponseCacheManager manager = manager(1)) {
            var policy = policy("work", singleFlight);
            assertThat(call(manager, policy, "warm", () -> Mono.just("warm")).block(WAIT)).isEqualTo("warm");
            Sinks.One<String> source = Sinks.one();
            AtomicInteger assemblies = new AtomicInteger();
            Mono<?> cold = call(manager, policy, "busy", () -> {
                assemblies.incrementAndGet();
                return source.asMono();
            });
            var first = cold.toFuture();
            assertThat(manager.activeLoadsForTesting("work")).isEqualTo(1);
            StepVerifier.create(call(manager, policy, "rejected", () -> {
                        assemblies.incrementAndGet();
                        return Mono.just("unexpected");
                    }))
                    .expectError(CacheLoadAdmission.Rejected.class).verify(WAIT);
            assertThat(call(manager, policy, "warm", () -> Mono.error(new AssertionError("hit loaded")))
                    .block(WAIT)).isEqualTo("warm");
            var waiter = singleFlight ? cold.toFuture() : null;
            if (!singleFlight) {
                StepVerifier.create(cold).expectError(CacheLoadAdmission.Rejected.class).verify(WAIT);
            }
            assertThat(assemblies).hasValue(1);
            assertThat(manager.activeLoadsForTesting("work")).isEqualTo(1);
            source.tryEmitValue("value").orThrow();
            assertThat(first.join()).isEqualTo("value");
            if (waiter != null) {
                assertThat(waiter.join()).isEqualTo("value");
            }
            assertThat(manager.activeLoadsForTesting("work")).isZero();
            assertThat(call(manager, policy, "rejected", () -> Mono.just("replacement")).block(WAIT))
                    .isEqualTo("replacement");
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void terminalCleanupPrecedesImmediateResubscription(boolean singleFlight) {
        try (LocalResponseCacheManager manager = manager(1)) {
            var policy = policy("work", singleFlight);
            AtomicInteger assemblies = new AtomicInteger();
            Mono<?> cold = call(manager, policy, "empty", () -> {
                assemblies.incrementAndGet();
                assertThat(manager.activeLoadsForTesting("work")).isEqualTo(1);
                return Mono.empty();
            });
            StepVerifier.create(cold.repeat(99)).verifyComplete();
            assertThat(assemblies).hasValue(100);
            assertThat(manager.activeLoadsForTesting("work")).isZero();
            AtomicInteger failures = new AtomicInteger();
            StepVerifier.create(call(manager, policy, "error", () -> {
                        failures.incrementAndGet();
                        return Mono.error(new IllegalArgumentException("source"));
                    }).retry(99))
                    .expectErrorMessage("source").verify(WAIT);
            assertThat(failures).hasValue(100);
            assertThat(manager.activeLoadsForTesting("work")).isZero();
        }
    }

    @Test
    void cancellingTheLeaderDoesNotReleaseTheWaitersSourceSlot() {
        try (LocalResponseCacheManager manager = manager(1)) {
            var policy = policy("work", true);
            AtomicInteger cancellations = new AtomicInteger();
            Sinks.One<String> source = Sinks.one();
            Mono<?> cold = call(manager, policy, "shared",
                    () -> source.asMono().doOnCancel(cancellations::incrementAndGet));
            var first = cold.toFuture();
            var waiter = cold.toFuture();
            first.cancel(true);
            assertThat(manager.activeLoadsForTesting("work")).isEqualTo(1);
            assertThat(cancellations).hasValue(0);
            StepVerifier.create(call(manager, policy, "other", () -> Mono.just("unexpected")))
                    .expectError(CacheLoadAdmission.Rejected.class).verify(WAIT);
            waiter.cancel(true);
            assertThat(cancellations).hasValue(1);
            assertThat(manager.activeLoadsForTesting("work")).isZero();
            assertThat(manager.workloadSnapshotForTesting().inFlightLoads()).isZero();
            assertThat(call(manager, policy, "shared", () -> Mono.just("replacement")).block(WAIT))
                    .isEqualTo("replacement");
        }
    }

    enum Outcome { STORED, UNKNOWN_BYTES, OVER_BUDGET, EMPTY, ERROR, ASSEMBLY_ERROR, METADATA_ERROR, CANCEL }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void staleHitsAndHiddenRefreshDoNotConsumeForegroundCapacity(boolean single) {
        AtomicLong ticker = new AtomicLong();
        var scheduler = reactor.test.scheduler.VirtualTimeScheduler.create();
        try (var manager = LocalResponseCacheManager.testing(ticker::get, scheduler,
                Map.of("work", 8), Map.of("work", 1))) {
            var policy = policy("work", single);
            policy.policy().setRefreshAfterMs(1_000L);
            policy.policy().setRefreshTimeoutMs(5_000L);
            assertThat(call(manager, policy, "stale", () -> Mono.just("initial")).block(WAIT)).isEqualTo("initial");
            var busy = call(manager, policy, "busy", Mono::never).toFuture();
            ticker.set(Duration.ofSeconds(2).toNanos());
            Sinks.One<String> refreshed = Sinks.one();
            AtomicInteger refreshes = new AtomicInteger();
            Mono<?> hit = call(manager, policy, "stale", () -> {
                refreshes.incrementAndGet();
                return refreshed.asMono();
            });
            assertThat(hit.block(WAIT)).isEqualTo("initial");
            assertThat(hit.block(WAIT)).isEqualTo("initial");
            assertThat(refreshes).hasValue(1);
            assertThat(manager.workloadSnapshotForTesting().inFlightRefreshes()).isEqualTo(1);
            assertThat(manager.activeLoadsForTesting("work")).isEqualTo(1);
            refreshed.tryEmitValue("refreshed").orThrow();
            assertThat(hit.block(WAIT)).isEqualTo("refreshed");
            assertThat(refreshes).hasValue(1);
            assertThat(manager.activeLoadsForTesting("work")).isEqualTo(1);
            busy.cancel(true);
            assertThat(manager.activeLoadsForTesting("work")).isZero();
        } finally {
            scheduler.dispose();
        }
    }

    @ParameterizedTest
    @EnumSource(Outcome.class)
    void everySourceOutcomeReleasesExactlyOnce(Outcome outcome) throws Exception {
        for (boolean single : List.of(false, true)) {
            try (var manager = manager(1)) {
                var policy = policy("work", single);
                policy.policy().setMaximumTotalDecodedResponseBytes(8L);
                Sinks.One<String> result = Sinks.one();
                Mono<?> call = manager.getOrLoad(policy, key("outcome"), () -> {
                    assertThat(manager.activeLoadsForTesting("work")).isEqualTo(1);
                    if (outcome == Outcome.ASSEMBLY_ERROR) { throw new IllegalArgumentException("assembly"); }
                    return result.asMono();
                }, () -> {
                    assertThat(manager.activeLoadsForTesting("work")).isEqualTo(1);
                    if (outcome == Outcome.METADATA_ERROR) { throw new IllegalArgumentException("metadata"); }
                    return metadata(outcome);
                });
                var future = call.toFuture();
                if (outcome == Outcome.CANCEL) {
                    future.cancel(true);
                } else if (outcome != Outcome.ASSEMBLY_ERROR) {
                    if (outcome == Outcome.EMPTY) { result.tryEmitEmpty().orThrow(); }
                    else if (outcome == Outcome.ERROR) { result.tryEmitError(new IllegalArgumentException("source")).orThrow(); }
                    else { result.tryEmitValue("value").orThrow(); }
                }
                if (Set.of(Outcome.ERROR, Outcome.ASSEMBLY_ERROR, Outcome.METADATA_ERROR).contains(outcome)) {
                    assertThatThrownBy(future::join).hasCauseInstanceOf(IllegalArgumentException.class);
                } else if (outcome != Outcome.CANCEL) {
                    assertThat(future.join()).isEqualTo(outcome == Outcome.EMPTY ? null : "value");
                }
                assertThat(manager.activeLoadsForTesting("work")).isZero();
                assertThat(manager.snapshot().currentSize()).isEqualTo(outcome == Outcome.STORED ? 1 : 0);
                manager.evictAllForTesting();
                assertNoTokens(manager);
                var replacement = call(manager, policy, "replacement", Mono::never).toFuture();
                future.cancel(true);
                assertThat(manager.activeLoadsForTesting("work")).isEqualTo(1);
                replacement.cancel(true);
                assertThat(manager.activeLoadsForTesting("work")).isZero();
                assertNoTokens(manager);
            }
        }
    }

    enum Frame { ASSEMBLY, SUBSCRIPTION, PUBLICATION, CANCELLATION }

    @ParameterizedTest
    @EnumSource(Frame.class)
    void cancellationHoldsSourceCapacityUntilItsFrameUnwinds(Frame frame) throws Exception {
        for (boolean single : List.of(false, true)) {
            ExecutorService executor = Executors.newSingleThreadExecutor();
            try (var manager = manager(1); Gate gate = new Gate()) {
                var policy = policy("work", single);
                Sinks.One<String> result = Sinks.one();
                AtomicInteger subscriptions = new AtomicInteger();
                BaseSubscriber<Object> subscriber = new BaseSubscriber<>() { };
                Mono<?> source = manager.getOrLoad(policy, key("held"), () -> {
                    if (frame == Frame.ASSEMBLY) { gate.run(); }
                    return Mono.defer(() -> {
                        if (frame == Frame.SUBSCRIPTION) { gate.run(); }
                        subscriptions.incrementAndGet();
                        return result.asMono().doOnCancel(() -> {
                            if (frame == Frame.CANCELLATION) { gate.run(); }
                        });
                    });
                }, () -> {
                    if (frame == Frame.PUBLICATION) { gate.run(); }
                    return LocalResponseCacheManager.ResponseMetadata.successWithoutHeaders();
                });
                Future<?> blocked;
                if (frame == Frame.ASSEMBLY || frame == Frame.SUBSCRIPTION) {
                    blocked = executor.submit(() -> source.subscribe(subscriber));
                } else {
                    source.subscribe(subscriber);
                    blocked = executor.submit(() -> {
                        if (frame == Frame.PUBLICATION) { result.tryEmitValue("late").orThrow(); }
                        else { subscriber.cancel(); }
                    });
                }
                gate.awaitEntered();
                if (frame != Frame.CANCELLATION) { subscriber.cancel(); }
                assertThat(manager.activeLoadsForTesting("work")).isEqualTo(1);
                StepVerifier.create(call(manager, policy, "replacement", () -> Mono.just("unexpected")))
                        .expectError(CacheLoadAdmission.Rejected.class).verify(WAIT);
                gate.close();
                blocked.get(10, TimeUnit.SECONDS);
                assertThat(manager.activeLoadsForTesting("work")).isZero();
                assertThat(manager.snapshot().currentSize()).isZero();
                assertThat(manager.workloadSnapshotForTesting().inFlightLoads()).isZero();
                assertNoTokens(manager);
                if (frame == Frame.ASSEMBLY) { assertThat(subscriptions).hasValue(0); }
                assertThat(call(manager, policy, "held", () -> Mono.just("replacement")).block(WAIT))
                        .isEqualTo("replacement");
            } finally {
                executor.shutdownNow();
                assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
            }
        }
    }

    @Test
    void blockedAssemblyCanKeepAWaiterWithoutReusingTheOriginalCallerEvidence() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (var manager = manager(1); Gate gate = new Gate()) {
            var policy = policy("work", true);
            var firstState = state();
            var waiterState = state();
            var lateState = state();
            var loadState = state();
            Sinks.One<String> result = Sinks.one();
            BaseSubscriber<Object> first = new BaseSubscriber<>() { };
            Future<?> assembling = executor.submit(() -> manager.getOrLoad(policy, key("same"), ignored -> {
                gate.run();
                return result.asMono();
            }, LocalResponseCacheManager.ResponseMetadata::successWithoutHeaders, firstState, loadState).subscribe(first));
            gate.awaitEntered();
            var waiter = manager.getOrLoad(policy, key("same"), ignored -> {
                throw new AssertionError("waiter assembled");
            }, LocalResponseCacheManager.ResponseMetadata::successWithoutHeaders, waiterState, state()).toFuture();
            loadState.beginAttempt(new RequestArgumentResolver.ResolvedArgs(Map.of(), Map.of(), Map.of(), null))
                    .observeRequestUrl(java.net.URI.create("http://load.example.invalid/first"));
            first.cancel();
            var terminal = firstState.complete(SubscriptionReportingState.TerminalSignal.CANCEL, null, null);
            var late = manager.getOrLoad(policy, key("same"), ignored -> {
                throw new AssertionError("late waiter assembled");
            }, LocalResponseCacheManager.ResponseMetadata::successWithoutHeaders, lateState, state()).toFuture();
            assertThat(manager.activeLoadsForTesting("work")).isEqualTo(1);
            gate.close();
            assembling.get(10, TimeUnit.SECONDS);
            var retry = loadState.beginAttempt(new RequestArgumentResolver.ResolvedArgs(Map.of(), Map.of(), Map.of(), null));
            retry.observeRequestUrl(java.net.URI.create("http://load.example.invalid/retry"));
            retry.recordResponse(org.springframework.http.HttpStatus.OK, Map.of("X-Load", List.of("retry")));
            result.tryEmitValue("value").orThrow();
            assertThat(waiter.join()).isEqualTo("value");
            assertThat(late.join()).isEqualTo("value");
            assertThat(firstState.terminalSnapshot()).isSameAs(terminal);
            assertThat(terminal.attemptCount()).isEqualTo(1);
            assertThat(loadState.attemptCount()).isEqualTo(2);
            for (var state : List.of(waiterState, lateState)) {
                var local = state.complete(SubscriptionReportingState.TerminalSignal.SUCCESS, "value", null);
                assertThat(local.attemptCount()).isZero();
                assertThat(local.finalRequestUrl()).isNull();
                assertThat(local.responseStatus()).isNull();
                assertThat(local.responseHeaders()).isEmpty();
            }
            assertThat(manager.activeLoadsForTesting("work")).isZero();
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void manyKeysNeverExceedCapacityAndRejectedTokensAreRemoved(boolean single) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(16);
        try (var manager = manager(4)) {
            var policy = policy("work", single);
            CountDownLatch start = new CountDownLatch(1);
            AtomicInteger assemblies = new AtomicInteger();
            List<Future<CompletableFuture<?>>> contenders = new ArrayList<>();
            for (int i = 0; i < 64; i++) {
                String key = "contender-" + i;
                contenders.add(executor.submit(() -> {
                    assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                    return call(manager, policy, key, () -> {
                        assemblies.incrementAndGet();
                        assertThat(manager.activeLoadsForTesting("work")).isBetween(1, 4);
                        return Mono.never();
                    }).toFuture();
                }));
            }
            start.countDown();
            List<CompletableFuture<?>> admitted = new ArrayList<>();
            for (var contender : contenders) {
                var future = contender.get(10, TimeUnit.SECONDS);
                if (future.isDone()) {
                    assertThatThrownBy(future::join).hasCauseInstanceOf(CacheLoadAdmission.Rejected.class);
                } else {
                    admitted.add(future);
                }
            }
            assertThat(admitted).hasSize(4);
            assertThat(assemblies).hasValue(4);
            assertThat(manager.activeLoadsForTesting("work")).isEqualTo(4);
            assertThat(generationCount(manager)).isEqualTo(4);
            admitted.forEach(future -> future.cancel(true));
            assertThat(manager.activeLoadsForTesting("work")).isZero();
            assertNoTokens(manager);
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void policyNamesFactoriesAndIndependentDuplicatesOwnSeparateSlots(boolean single) {
        try (var manager = manager(2); var another = manager(1)) {
            var policy = policy("work", single);
            var first = call(manager, policy, "same", Mono::never).toFuture();
            var second = call(manager, policy, "same", Mono::never).toFuture();
            assertThat(manager.activeLoadsForTesting("work")).isEqualTo(single ? 1 : 2);
            var otherPolicy = call(manager, policy("other", single), "same", Mono::never).toFuture();
            var otherFactory = call(another, policy, "same", Mono::never).toFuture();
            assertThat(manager.activeLoadsForTesting("other")).isEqualTo(1);
            assertThat(another.activeLoadsForTesting("work")).isEqualTo(1);
            manager.evictAllForTesting();
            assertThat(manager.activeLoadsForTesting("work")).isEqualTo(single ? 1 : 2);
            first.cancel(true);
            second.cancel(true);
            otherPolicy.cancel(true);
            otherFactory.cancel(true);
            assertThat(manager.activeLoadsForTesting("work")).isZero();
            assertThat(manager.activeLoadsForTesting("other")).isZero();
            assertThat(another.activeLoadsForTesting("work")).isZero();
        }
    }

    private static LocalResponseCacheManager.ResponseMetadata metadata(Outcome outcome) {
        if (outcome == Outcome.UNKNOWN_BYTES) {
            return LocalResponseCacheManager.ResponseMetadata.successWithoutHeaders();
        }
        var bytes = new LocalResponseCacheManager.DecodedResponseBytes(8);
        bytes.add(outcome == Outcome.OVER_BUDGET ? 9 : 4);
        bytes.complete();
        return new LocalResponseCacheManager.ResponseMetadata(200, Map.of(), true, bytes);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void publicationIsRecheckedBeforeRejectingAtCapacity(boolean single) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (var manager = manager(1)) {
            var policy = policy("work", single);
            AtomicReference<Thread> thread = new AtomicReference<>();
            CountDownLatch entered = new CountDownLatch(1);
            CompletableFuture<?> saturation;
            Future<?> paused;
            synchronized (map(manager, "inFlightLoads")) {
                paused = executor.submit(() -> {
                    thread.set(Thread.currentThread());
                    entered.countDown();
                    return call(manager, policy, "same", () -> Mono.error(new AssertionError("stale miss loaded")))
                            .block(WAIT);
                });
                assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
                long deadline = System.nanoTime() + WAIT.toNanos();
                while (thread.get().getState() != Thread.State.BLOCKED && System.nanoTime() < deadline) {
                    Thread.sleep(1);
                }
                assertThat(thread.get().getState()).isEqualTo(Thread.State.BLOCKED);
                assertThat(call(manager, policy, "same", () -> Mono.just("winner")).block(WAIT)).isEqualTo("winner");
                saturation = call(manager, policy, "busy", Mono::never).toFuture();
                assertThat(manager.activeLoadsForTesting("work")).isEqualTo(1);
            }
            assertThat(paused.get(10, TimeUnit.SECONDS)).isEqualTo("winner");
            assertThat(manager.activeLoadsForTesting("work")).isEqualTo(1);
            saturation.cancel(true);
            assertThat(manager.activeLoadsForTesting("work")).isZero();
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void delayedMemberOfAnAbandonedFlightCannotReconnectOrAffectAReplacement() throws Exception {
        try (var manager = manager(1)) {
            var policy = policy("work", true);
            AtomicInteger sources = new AtomicInteger();
            var first = call(manager, policy, "same", () -> {
                sources.incrementAndGet();
                return Mono.never();
            }).toFuture();
            Object flight = map(manager, "inFlightLoads").values().iterator().next();
            var reserve = flight.getClass().getDeclaredMethod("reserve", SubscriptionReportingState.class);
            reserve.setAccessible(true);
            Object member = reserve.invoke(flight, state());
            var publisher = flight.getClass().getDeclaredMethod("publisher", member.getClass(), Runnable.class);
            publisher.setAccessible(true);
            Mono<?> delayed = (Mono<?>) publisher.invoke(flight, member, (Runnable) () -> { });
            var release = manager.getClass().getDeclaredMethod("releaseFlightMember", flight.getClass(), member.getClass());
            release.setAccessible(true);
            release.invoke(manager, flight, member);
            first.cancel(true);
            assertThat(manager.activeLoadsForTesting("work")).isZero();
            assertNoTokens(manager);
            Sinks.One<String> replacementValue = Sinks.one();
            var replacement = call(manager, policy, "same", () -> {
                sources.incrementAndGet();
                return replacementValue.asMono();
            }).toFuture();
            StepVerifier.create(delayed).verifyComplete();
            StepVerifier.create(delayed).verifyComplete();
            assertThat(sources).hasValue(2);
            assertThat(manager.activeLoadsForTesting("work")).isEqualTo(1);
            assertThat(replacement.isDone()).isFalse();
            replacementValue.tryEmitValue("replacement").orThrow();
            assertThat(replacement.join()).isEqualTo("replacement");
            assertThat(manager.activeLoadsForTesting("work")).isZero();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void closeDoesNotPretendAnExternalIndependentSourceHasTerminated(boolean single) throws Exception {
        try (var manager = manager(1)) {
            Sinks.One<String> source = Sinks.one();
            AtomicInteger cancelled = new AtomicInteger();
            var policy = policy("work", single);
            var pending = call(manager, policy, "pending",
                    () -> source.asMono().doOnCancel(cancelled::incrementAndGet)).toFuture();
            manager.close();
            assertThat(manager.activeLoadsForTesting("work")).isEqualTo(single ? 0 : 1);
            assertThat(cancelled).hasValue(single ? 1 : 0);
            if (!single) {
                source.tryEmitValue("late").orThrow();
                assertThat(pending.get(10, TimeUnit.SECONDS)).isEqualTo("late");
            } else {
                assertThat(pending.get(10, TimeUnit.SECONDS)).isNull();
            }
            assertThat(manager.activeLoadsForTesting("work")).isZero();
            assertThat(manager.snapshot().currentSize()).isZero();
            assertNoTokens(manager);
        }
    }

    private static SubscriptionReportingState state() {
        return new SubscriptionReportingState(new RequestArgumentResolver.ResolvedArgs(Map.of(), Map.of(), Map.of(), null));
    }

    private static int generationCount(LocalResponseCacheManager manager) throws Exception {
        int count = 0;
        for (Object cache : map(manager, "caches").values()) {
            for (Object generation : map(cache, "generations").values()) {
                var active = generation.getClass().getDeclaredField("activeLoads");
                active.setAccessible(true);
                assertThat(active.getInt(generation)).isPositive();
                count++;
            }
        }
        return count;
    }

    private static Map<?, ?> map(Object object, String name) throws Exception {
        var field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return (Map<?, ?>) field.get(object);
    }

    private static void assertNoTokens(LocalResponseCacheManager manager) throws Exception {
        assertThat(generationCount(manager)).isZero();
    }

    private static final class Gate implements AutoCloseable {
        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        void run() {
            entered.countDown();
            try {
                if (!release.await(10, TimeUnit.SECONDS)) { throw new AssertionError("Gate timed out"); }
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new AssertionError(error);
            }
        }
        void awaitEntered() throws InterruptedException { assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue(); }
        @Override public void close() { release.countDown(); }
    }

    private static LocalResponseCacheManager manager(int maximum) {
        return LocalResponseCacheManager.testing(System::nanoTime, Schedulers.parallel(),
                Map.of("work", 128, "other", 128), Map.of("work", maximum, "other", maximum));
    }

    private static EffectiveCachePolicy.Selection policy(String name, boolean singleFlight) {
        var policy = new ReactiveHttpClientProperties.CachePolicyConfig();
        policy.setTtlMs(60_000L);
        policy.setMaximumSize(100L);
        policy.setSingleFlight(singleFlight);
        return new EffectiveCachePolicy.Selection(true, EffectiveCachePolicy.Source.CLIENT, name, policy);
    }

    private static Mono<?> call(LocalResponseCacheManager manager, EffectiveCachePolicy.Selection policy,
                                String key, Supplier<Mono<?>> loader) {
        return manager.getOrLoad(policy, key(key), loader);
    }

    private static CacheKeyContract.OpaqueKey key(String value) {
        return CacheKeyContract.OpaqueKey.from(value.getBytes(StandardCharsets.UTF_8));
    }
}
