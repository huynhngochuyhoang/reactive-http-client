package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.auth.AuthContext;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.reactivestreams.Subscription;
import reactor.core.CoreSubscriber;
import reactor.core.publisher.*;
import reactor.test.scheduler.VirtualTimeScheduler;
import reactor.util.context.Context;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Timeout(30)
class CacheWorkOwnershipContractTest {
    private static final String POLICY = "ownership";
    private static final String API = "ownership.read";

    @ParameterizedTest
    @CsvSource({"false,false", "true,false", "true,true"})
    void valuedSourceKeepsItsReservationUntilCompletionOrCancellationCleanup(
            boolean cancel, boolean cancelBeforeValue) throws Exception {
        var admission = new CacheWorkAdmission(Map.of(POLICY, 1), IllegalStateException::new);
        var reservation = admission.acquire(POLICY);
        List<SignalType> terminals = new CopyOnWriteArrayList<>();
        CountDownLatch valueDelivered = new CountDownLatch(1);
        CountDownLatch allowComplete = new CountDownLatch(1);
        CountDownLatch cancellationEntered = new CountDownLatch(1);
        CountDownLatch allowCancellation = new CountDownLatch(1);
        AtomicInteger cancellations = new AtomicInteger();
        AtomicInteger resource = new AtomicInteger(1);
        List<AtomicInteger> discarded = new CopyOnWriteArrayList<>();
        String hook = "cache-work-valued-terminal";
        // Gate the ownership subscriber's immediate upstream, after preparation has attached.
        Hooks.onEachOperator(hook, Operators.<Object, Object>lift((ignored, actual) -> {
            if (!(actual instanceof BaseSubscriber<?>)
                    || actual.getClass().getEnclosingClass() != CacheWorkAdmission.class) {
                return actual;
            }
            return new CoreSubscriber<>() {
                @Override public Context currentContext() { return actual.currentContext(); }
                @Override public void onSubscribe(Subscription subscription) {
                    actual.onSubscribe(new Subscription() {
                        @Override public void request(long n) { subscription.request(n); }
                        @Override public void cancel() {
                            cancellations.incrementAndGet();
                            cancellationEntered.countDown();
                            await(allowCancellation);
                            subscription.cancel();
                        }
                    });
                }
                @Override public void onNext(Object value) {
                    if (!cancelBeforeValue) { actual.onNext(value); }
                    valueDelivered.countDown();
                    await(allowComplete);
                    if (cancelBeforeValue) { actual.onNext(value); }
                }
                @Override public void onError(Throwable error) { actual.onError(error); }
                @Override public void onComplete() { actual.onComplete(); }
            };
        }));
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Sinks.One<AtomicInteger> source = Sinks.one();
            var result = CacheWorkAdmission.own(source.asMono(), reservation, terminals::add)
                    .doOnDiscard(AtomicInteger.class, value -> {
                        discarded.add(value);
                        value.decrementAndGet();
                    }).toFuture();
            var emission = executor.submit(() -> source.tryEmitValue(resource).orThrow());
            await(valueDelivered);
            assertThat(terminals).isEmpty();
            assertThat(result).isNotDone();
            assertThat(discarded).isEmpty();
            assertThat(admission.active(POLICY)).isEqualTo(1);
            assertThatThrownBy(() -> admission.acquire(POLICY)).isInstanceOf(IllegalStateException.class);
            if (cancel) {
                var cancellation = executor.submit(() -> result.cancel(true));
                await(cancellationEntered);
                assertThat(terminals).containsExactly(SignalType.CANCEL);
                assertThat(admission.active(POLICY)).isEqualTo(1);
                assertThatThrownBy(() -> admission.acquire(POLICY)).isInstanceOf(IllegalStateException.class);
                allowCancellation.countDown();
                assertThat(cancellation.get(5, TimeUnit.SECONDS)).isTrue();
            }
            allowComplete.countDown();
            emission.get(5, TimeUnit.SECONDS);
            if (cancel) {
                result.cancel(true);
                assertThat(discarded).containsExactly(resource);
                assertThat(resource).hasValue(0);
            } else {
                assertThat(result.get(5, TimeUnit.SECONDS)).isSameAs(resource);
                assertThat(discarded).isEmpty();
                assertThat(resource).hasValue(1);
            }
            assertThat(terminals).containsExactly(cancel ? SignalType.CANCEL : SignalType.ON_COMPLETE);
            assertThat(cancellations).hasValue(cancel ? 1 : 0);
            assertThat(admission.active(POLICY)).isZero();
            var replacement = admission.acquire(POLICY);
            reservation.complete();
            assertThat(admission.active(POLICY)).isEqualTo(1);
            replacement.complete();
            assertThat(admission.active(POLICY)).isZero();
        } finally {
            allowCancellation.countDown();
            allowComplete.countDown();
            executor.shutdown();
            try {
                assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
            } finally {
                Hooks.resetOnEachOperator(hook);
                reservation.complete();
                admission.close();
            }
        }
    }

    @ParameterizedTest
    @CsvSource({"false,false,false", "false,true,false", "true,false,false", "true,true,false",
            "false,false,true", "false,true,true", "true,false,true", "true,true,true"})
    void contentionAndRacingTerminalsReconcileOwnersWithoutLatePublication(
            boolean single, boolean sameKey, boolean failure)
            throws Exception {
        try (var executor = Executors.newFixedThreadPool(24)) {
            for (int wave = 0; wave < 10; wave++) {
                try (Fixture f = new Fixture(single, 12, 3)) {
                    CountDownLatch start = new CountDownLatch(1);
                    List<Sinks.One<Object>> sources = new CopyOnWriteArrayList<>();
                    AtomicInteger sourceTerminals = new AtomicInteger();
                    List<Future<CompletableFuture<?>>> tasks = new ArrayList<>();
                    for (int i = 0; i < 24; i++) {
                        String key = sameKey ? "same" : "key-" + i;
                        tasks.add(executor.submit(() -> {
                            await(start);
                            return f.call(key, () -> {
                                Sinks.One<Object> source = Sinks.one();
                                sources.add(source);
                                return source.asMono().doFinally(ignored -> sourceTerminals.incrementAndGet());
                            }).toFuture();
                        }));
                    }
                    start.countDown();
                    List<CompletableFuture<?>> callers = new ArrayList<>();
                    for (var task : tasks) { callers.add(task.get(10, TimeUnit.SECONDS)); }
                    int active = f.manager.callerAdmission().active(POLICY);
                    int loads = single && sameKey ? 1 : 3;
                    assertThat(active).isBetween(loads, 12);
                    assertThat(f.manager.activeLoadsForTesting(POLICY)).isEqualTo(loads);
                    assertThat(f.manager.activeRefreshesForTesting(POLICY)).isZero();
                    assertThat(sources).hasSize(loads);
                    assertThat(f.acquired.get() - f.released.get()).isEqualTo(active);
                    assertThat(f.terminals).hasValue(24 - active);
                    assertThat(tokens(f.manager)).isEqualTo(loads);
                    if (single) {
                        assertThat(f.manager.workloadSnapshotForTesting().inFlightLoads()).isEqualTo(loads);
                    }

                    f.manager.evictAllForTesting();
                    CountDownLatch terminalStart = new CountDownLatch(1);
                    List<Future<?>> endings = new ArrayList<>();
                    for (var caller : callers) {
                        endings.add(executor.submit(() -> { await(terminalStart); caller.cancel(true); }));
                    }
                    for (var source : sources) {
                        endings.add(executor.submit(() -> {
                            await(terminalStart);
                            if (failure) { source.tryEmitError(new IllegalStateException("terminal race")); }
                            else { source.tryEmitValue("invalidated"); }
                        }));
                    }
                    terminalStart.countDown();
                    for (var ending : endings) { ending.get(10, TimeUnit.SECONDS); }
                    f.counts(0, 0, 0);
                    assertThat(f.acquired).hasValue(f.released.get());
                    assertThat(f.terminals).hasValue(24);
                    assertThat(sourceTerminals).hasValue(loads);
                    assertThat(f.loadTerminals()).isEqualTo(loads);
                    assertThat(tokens(f.manager)).isZero();
                    assertThat(f.manager.snapshot().currentSize()).isZero();
                    assertThat(f.manager.workloadSnapshotForTesting().inFlightLoads()).isZero();
                    assertThat(f.manager.workloadSnapshotForTesting().coalescedWaiters()).isZero();
                    assertThat(f.call("reuse", () -> Mono.just("new")).block()).isEqualTo("new");
                    f.counts(0, 0, 0);
                }
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void cancellationBeforeAttachmentAndImmediateAssemblyRetriesReleaseExactlyOnce(boolean single) throws Exception {
        try (Fixture f = new Fixture(single, 1, 1)) {
            AtomicInteger assembled = new AtomicInteger();
            f.call("cancelled", () -> {
                assembled.incrementAndGet();
                return Mono.just("unexpected");
            }).subscribe(new BaseSubscriber<Object>() {
                @Override protected void hookOnSubscribe(org.reactivestreams.Subscription subscription) { cancel(); }
            });
            assertThat(assembled).hasValue(0);
            f.counts(0, 0, 0);
            assertThat(tokens(f.manager)).isZero();
            assertThat(f.terminals).hasValue(1);

            assertThat(f.call("retry", () -> {
                if (assembled.incrementAndGet() < 50) { throw new IllegalArgumentException("assembly"); }
                return Mono.just("recovered");
            }).retry(49).block()).isEqualTo("recovered");
            assertThat(assembled).hasValue(50);
            assertThat(f.acquired).hasValue(51);
            assertThat(f.released).hasValue(51);
            assertThat(f.terminals).hasValue(51);
            assertThat(f.loadTerminals()).isEqualTo(50);
            f.counts(0, 0, 0);
            assertThat(tokens(f.manager)).isZero();
        }
    }

    @Test
    void rejectedAndSkippedClosuresCollectWhileAdmittedOwnersRemainAlive() throws Exception {
        ReferenceQueue<Object> queue = new ReferenceQueue<>();
        try (Fixture f = new Fixture(true, 2, 1)) {
            f.call("refresh-a", () -> Mono.just("a")).block();
            f.call("refresh-b", () -> Mono.just("b")).block();
            f.time.set(Duration.ofSeconds(2).toNanos());
            Sinks.One<Object> refresh = Sinks.one();
            f.call("refresh-a", refresh::asMono).block();
            Sinks.One<Object> source = Sinks.one();
            var leader = f.call("busy", source::asMono).toFuture();
            List<WeakReference<Object>> references = rejectedOwners(f, "rejected-load", queue, false);
            references.addAll(rejectedOwners(f, "refresh-b", queue, true));
            var waiter = f.call("busy", () -> Mono.error(new AssertionError("waiter loader"))).toFuture();
            references.addAll(rejectedOwners(f, "rejected-caller", queue, false));
            f.counts(2, 1, 1);
            assertThat(f.manager.workloadSnapshotForTesting().inFlightLoads()).isEqualTo(1);
            assertThat(f.manager.workloadSnapshotForTesting().inFlightRefreshes()).isEqualTo(1);
            assertThat(tokens(f.manager)).isEqualTo(1);
            assertThat((java.util.Queue<?>) field(f.scheduler, "queue")).hasSize(1);
            collected(queue, references);
            assertThat(leader).isNotDone();
            assertThat(waiter).isNotDone();
            f.manager.evictAllForTesting();
            f.counts(2, 1, 0);
            source.tryEmitValue("late").orThrow();
            assertThat(leader.get(10, TimeUnit.SECONDS)).isEqualTo("late");
            assertThat(waiter.get(10, TimeUnit.SECONDS)).isEqualTo("late");
            assertThat(f.manager.snapshot().currentSize()).isZero();
            f.counts(0, 0, 0);
            assertThat((java.util.Queue<?>) field(f.scheduler, "queue")).isEmpty();
            assertThat(tokens(f.manager)).isZero();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void detachedCallerCollectsWhileSourceRetainsItsOwnState(boolean detachLeader) throws Exception {
        ReferenceQueue<Object> queue = new ReferenceQueue<>();
        try (Fixture f = new Fixture(true, 2, 1)) {
            Detached detached = detached(f, queue, detachLeader);
            f.counts(1, 1, 0);
            assertThat(f.manager.hasInFlightLoadWithMembersForTesting(1)).isTrue();
            collected(queue, detached.callerReferences());
            assertThat(detached.sourceReference().get()).isNotNull();
            assertThat(detached.sourceCancellations()).hasValue(0);
            detached.source().tryEmitEmpty().orThrow();
            assertThat(detached.survivor().get(10, TimeUnit.SECONDS)).isEqualTo("result");
            f.counts(0, 0, 0);
            collected(queue, List.of(detached.sourceReference()));
            assertThat(f.terminals).hasValue(2);
            assertThat(f.acquired).hasValue(2);
            assertThat(f.released).hasValue(2);
            assertThat(tokens(f.manager)).isZero();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void evictionReleasesValuesBeforeCloseWithoutReleasingRunningLoad(boolean single) throws Exception {
        ReferenceQueue<Object> queue = new ReferenceQueue<>();
        try (Fixture f = new Fixture(single, 2, 1)) {
            var value = cachedValue(f, queue);
            Sinks.One<Object> source = Sinks.one();
            var pending = f.call("pending", source::asMono).toFuture();
            f.manager.evictAllForTesting();
            assertThat(f.manager.snapshot().closed()).isFalse();
            assertThat(f.manager.snapshot().currentSize()).isZero();
            collected(queue, List.of(value));
            f.counts(1, 1, 0);
            assertThat(pending).isNotDone();
            assertThat(tokens(f.manager)).isEqualTo(1);
            source.tryEmitValue("late").orThrow();
            assertThat(pending.get(10, TimeUnit.SECONDS)).isEqualTo("late");
            f.counts(0, 0, 0);
            assertThat(tokens(f.manager)).isZero();
            assertThat(f.manager.snapshot().currentSize()).isZero();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void factoryCloseAndLateReleaseCannotChangeReplacementOwners(boolean single) throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        try (Fixture old = new Fixture(single, 3, 1, registry)) {
            old.call("stale", () -> Mono.just("old")).block();
            old.time.set(Duration.ofSeconds(2).toNanos());
            AtomicInteger refreshCancelled = new AtomicInteger();
            old.call("stale", () -> Mono.never().doOnCancel(refreshCancelled::incrementAndGet)).block();
            Sinks.One<Object> source = Sinks.one();
            AtomicInteger sourceCancelled = new AtomicInteger();
            var pending = old.call("pending", () -> source.asMono()
                    .doOnCancel(sourceCancelled::incrementAndGet)
                    .timeout(Duration.ofHours(1), old.scheduler)).toFuture();
            var waiter = single ? old.call("pending", Mono::never).toFuture() : null;
            old.counts(single ? 2 : 1, 1, 1);
            assertThat(registry.find(LocalResponseCacheMetrics.PREFIX + ".entries").gauge()).isNotNull();

            var factory = new ReactiveHttpClientFactoryBean<>();
            var cacheField = ReactiveHttpClientFactoryBean.class.getDeclaredField("responseCacheManager");
            cacheField.setAccessible(true);
            cacheField.set(factory, old.manager);
            try (var executor = Executors.newSingleThreadExecutor()) {
                executor.submit(factory::destroy).get(5, TimeUnit.SECONDS);
            }
            assertThat(old.scheduler.now(TimeUnit.NANOSECONDS)).isZero();
            assertThat(refreshCancelled).hasValue(1);
            assertThat(sourceCancelled).hasValue(single ? 1 : 0);
            old.counts(single ? 0 : 1, single ? 0 : 1, 0);
            if (single) {
                assertThat(pending).isDone();
                assertThat(waiter).isDone();
            } else {
                assertThat(pending).isNotDone();
            }
            assertThat(old.manager.snapshot().closed()).isTrue();
            assertThat(old.manager.snapshot().policyCount()).isZero();
            assertThat(registry.getMeters()).isEmpty();
            assertThatThrownBy(() -> old.manager.callerAdmission().acquire(POLICY))
                    .isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> old.manager.getOrLoad(old.policy, key("closed"), Mono::never).block())
                    .isInstanceOf(IllegalStateException.class);

            try (Fixture replacement = new Fixture(single, 3, 1, registry)) {
                replacement.call("new", () -> Mono.just("new")).block();
                Sinks.One<Object> replacementSource = Sinks.one();
                var replacementCall = replacement.call("busy", replacementSource::asMono).toFuture();
                var meters = List.copyOf(registry.getMeters());
                var snapshot = replacement.manager.snapshot();
                double loads = replacement.loadTerminals();
                source.tryEmitValue("old-late");
                pending.cancel(true);
                old.manager.close();
                old.counts(0, 0, 0);
                replacement.counts(1, 1, 0);
                assertThat(replacement.manager.snapshot()).isEqualTo(snapshot);
                assertThat(registry.getMeters()).containsExactlyInAnyOrderElementsOf(meters);
                assertThat(replacement.loadTerminals()).isEqualTo(loads);
                assertThat(registry.get(LocalResponseCacheMetrics.PREFIX + ".entries").gauge().value())
                        .isEqualTo(1);
                replacementSource.tryEmitValue("replacement").orThrow();
                assertThat(replacementCall.get(10, TimeUnit.SECONDS)).isEqualTo("replacement");
                replacement.counts(0, 0, 0);
            }
            assertThat(registry.getMeters()).isEmpty();
            assertThat((java.util.Queue<?>) field(old.scheduler, "queue")).isEmpty();
        } finally {
            registry.close();
        }
    }

    @Test
    void closeSharesTheWorkConfigurationBoundary() throws Exception {
        try (var manager = LocalResponseCacheManager.testing(System::nanoTime)) {
            CountDownLatch started = new CountDownLatch(1);
            Thread closing = new Thread(() -> { started.countDown(); manager.close(); });
            try {
                synchronized (manager) {
                    closing.start();
                    await(started);
                    eventually(() -> closing.getState() == Thread.State.BLOCKED || !closing.isAlive());
                    assertThat(manager.snapshot().closed()).isFalse();
                    manager.configureWork(new CacheWorkPolicy.Snapshot(Map.of(),
                            Map.of(POLICY, new CacheWorkPolicy.Limits(2, 1, 1))));
                }
            } finally {
                closing.join(5_000);
            }
            assertThat(closing.isAlive()).isFalse();
            assertThat(manager.snapshot().closed()).isTrue();
            assertThatThrownBy(() -> manager.callerAdmission().acquire(POLICY))
                    .isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> ((CacheWorkAdmission) field(manager, "loadAdmission")).acquire(POLICY))
                    .isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> ((CacheWorkAdmission) field(manager, "refreshAdmission")).acquire(POLICY))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void refreshTerminalRacesEvictionWithoutResurrection(boolean failure) throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            for (int wave = 0; wave < 20; wave++) {
                try (Fixture f = new Fixture(true, 2, 1)) {
                    f.call("stale", () -> Mono.just("old")).block();
                    f.time.set(Duration.ofSeconds(2).toNanos());
                    Sinks.One<Object> source = Sinks.one();
                    AtomicInteger terminals = new AtomicInteger();
                    f.call("stale", () -> source.asMono().doFinally(ignored -> terminals.incrementAndGet())).block();
                    f.counts(0, 0, 1);
                    CountDownLatch start = new CountDownLatch(1);
                    var eviction = executor.submit(() -> { await(start); f.manager.evictAllForTesting(); });
                    var terminal = executor.submit(() -> {
                        await(start);
                        if (failure) { source.tryEmitError(new IllegalStateException("refresh race")); }
                        else { source.tryEmitValue("replacement"); }
                    });
                    start.countDown();
                    eviction.get(5, TimeUnit.SECONDS);
                    terminal.get(5, TimeUnit.SECONDS);
                    f.counts(0, 0, 0);
                    assertThat(terminals).hasValue(1);
                    assertThat(f.registry.find(LocalResponseCacheMetrics.PREFIX + ".refreshes").counters()
                            .stream().mapToDouble(Counter::count).sum()).isEqualTo(1);
                    assertThat(f.manager.snapshot().currentSize()).isZero();
                    assertThat(f.manager.workloadSnapshotForTesting().inFlightRefreshes()).isZero();
                    assertThat(tokens(f.manager)).isZero();
                    assertThat((java.util.Queue<?>) field(f.scheduler, "queue")).isEmpty();
                    assertThat(f.call("stale", () -> Mono.just("new")).block()).isEqualTo("new");
                    f.counts(0, 0, 0);
                }
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void closeRacesFirstCacheCreationAndAttachmentsWithoutLeavingLiveOwners(boolean single) throws Exception {
        try (var executor = Executors.newFixedThreadPool(17)) {
            for (int wave = 0; wave < 20; wave++) {
                try (Fixture f = new Fixture(single, 16, 3)) {
                    CountDownLatch start = new CountDownLatch(1);
                    List<Future<CompletableFuture<?>>> tasks = new ArrayList<>();
                    AtomicInteger assemblies = new AtomicInteger();
                    for (int i = 0; i < 16; i++) {
                        String key = "close-" + (i % 4);
                        tasks.add(executor.submit(() -> {
                            await(start);
                            return f.call(key, () -> {
                                assemblies.incrementAndGet();
                                return Mono.never().timeout(Duration.ofHours(1), f.scheduler);
                            }).toFuture();
                        }));
                    }
                    Future<?> close = executor.submit(() -> { await(start); f.manager.close(); });
                    start.countDown();
                    close.get(5, TimeUnit.SECONDS);
                    List<CompletableFuture<?>> results = new ArrayList<>();
                    for (var task : tasks) { results.add(task.get(5, TimeUnit.SECONDS)); }
                    assertThat(f.manager.snapshot().closed()).isTrue();
                    assertThat(f.manager.snapshot().policyCount()).isZero();
                    assertThat(f.manager.workloadSnapshotForTesting().inFlightLoads()).isZero();
                    assertThat(f.manager.workloadSnapshotForTesting().inFlightRefreshes()).isZero();
                    assertThat(f.registry.getMeters()).isEmpty();
                    int pending = (int) results.stream().filter(result -> !result.isDone()).count();
                    if (single) { assertThat(pending).isZero(); }
                    f.counts(pending, pending, 0);
                    assertThat(pending).isBetween(0, 3);
                    assertThat(f.acquired.get() - f.released.get()).isEqualTo(pending);
                    assertThat(f.terminals).hasValue(16 - pending);
                    int before = assemblies.get();
                    assertThat(f.call("after-close", () -> {
                        assemblies.incrementAndGet(); return Mono.just("unexpected");
                    }).toFuture()).isCompletedExceptionally();
                    assertThat(assemblies).hasValue(before);
                    results.forEach(result -> result.cancel(true));
                    f.counts(0, 0, 0);
                    assertThat(f.acquired).hasValue(f.released.get());
                    assertThat(f.terminals).hasValue(17);
                    assertThat((java.util.Queue<?>) field(f.scheduler, "queue")).isEmpty();
                    assertThat(f.scheduler.now(TimeUnit.NANOSECONDS)).isZero();
                }
            }
        }
    }

    private static List<WeakReference<Object>> rejectedOwners(
            Fixture f, String name, ReferenceQueue<Object> queue, boolean stale) {
        Object argument = new Object();
        byte[] prepared = new byte[4096];
        AuthContext auth = new AuthContext(Map.of("X-Auth", "synthetic"), Map.of());
        Object context = new Object();
        Object callback = new Object();
        var key = key(name);
        var state = state(argument, prepared, auth);
        Supplier<Mono<?>> loader = () -> {
            callback.hashCode();
            throw new AssertionError("rejected or skipped loader");
        };
        var result = f.call(key, loader, state).contextWrite(Context.of("caller", context)).toFuture();
        if (stale) { assertThat(result.join()).isEqualTo("b"); }
        else { assertThat(result).isCompletedExceptionally(); }
        return new ArrayList<>(List.of(ref(argument, queue), ref(prepared, queue), ref(auth, queue),
                ref(context, queue), ref(callback, queue), ref(loader, queue), ref(state, queue), ref(key, queue)));
    }

    private static Detached detached(Fixture f, ReferenceQueue<Object> queue, boolean leader) {
        Sinks.Empty<Void> source = Sinks.empty();
        Object sourceOwner = new Object();
        var sourceRef = ref(sourceOwner, queue);
        AtomicInteger cancellations = new AtomicInteger();
        Supplier<Mono<?>> loader = () -> source.asMono().then(Mono.fromSupplier(() -> {
            sourceOwner.hashCode();
            return "result";
        })).doOnCancel(cancellations::incrementAndGet);
        CompletableFuture<?> survivor = leader ? null : f.call("shared", loader).toFuture();
        Object argument = new Object();
        byte[] prepared = new byte[4096];
        AuthContext auth = new AuthContext(Map.of("X-Auth", "synthetic"), Map.of());
        Object context = new Object();
        Object callback = new Object();
        var state = state(argument, prepared, auth);
        var caller = f.call(key("shared"), loader, state)
                .doOnNext(ignored -> callback.hashCode())
                .contextWrite(Context.of("caller", context)).toFuture();
        if (leader) { survivor = f.call("shared", Mono::never).toFuture(); }
        caller.cancel(true);
        return new Detached(source, survivor, cancellations, sourceRef,
                List.of(ref(argument, queue), ref(prepared, queue), ref(auth, queue),
                        ref(context, queue), ref(callback, queue), ref(state, queue)));
    }

    private record Detached(Sinks.Empty<Void> source, CompletableFuture<?> survivor,
                            AtomicInteger sourceCancellations, WeakReference<Object> sourceReference,
                            List<WeakReference<Object>> callerReferences) { }

    private static WeakReference<Object> cachedValue(Fixture f, ReferenceQueue<Object> queue) {
        Object value = new Object();
        assertThat(f.call("value", () -> Mono.just(value)).block()).isSameAs(value);
        return ref(value, queue);
    }

    private static WeakReference<Object> ref(Object value, ReferenceQueue<Object> queue) {
        return new WeakReference<>(value, queue);
    }

    private static void collected(ReferenceQueue<Object> queue, List<WeakReference<Object>> references)
            throws Exception {
        List<java.lang.ref.Reference<?>> enqueued = new ArrayList<>();
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (System.nanoTime() < deadline && enqueued.size() < references.size()) {
            System.gc();
            for (var reference = queue.poll(); reference != null; reference = queue.poll()) {
                if (references.contains(reference)) { enqueued.add(reference); }
            }
            if (enqueued.size() < references.size()) { Thread.sleep(25); }
        }
        assertThat(references).allSatisfy(reference -> assertThat(reference.get()).isNull());
        assertThat(enqueued).hasSize(references.size());
    }

    private static Object field(Object owner, String name) throws Exception {
        var field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }

    private static int tokens(LocalResponseCacheManager manager) throws Exception {
        int total = 0;
        for (Object cache : ((Map<?, ?>) field(manager, "caches")).values()) {
            for (Object generation : ((Map<?, ?>) field(cache, "generations")).values()) {
                total += (int) field(generation, "activeLoads");
            }
        }
        return total;
    }

    private static CacheKeyContract.OpaqueKey key(String name) {
        return CacheKeyContract.OpaqueKey.from(name.getBytes(StandardCharsets.UTF_8));
    }

    private static SubscriptionReportingState state(Object argument, byte[] body, AuthContext auth) {
        return new SubscriptionReportingState(new RequestArgumentResolver.ResolvedArgs(
                Map.of("argument", argument), Map.of(), Map.of(), List.of(body, auth)));
    }

    private static void await(CountDownLatch latch) {
        try { assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue(); }
        catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new AssertionError(error); }
    }

    private static void eventually(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) { Thread.sleep(1); }
        assertThat(condition.getAsBoolean()).isTrue();
    }

    private static final class Fixture implements AutoCloseable {
        final AtomicLong time = new AtomicLong();
        final VirtualTimeScheduler scheduler = VirtualTimeScheduler.create();
        final SimpleMeterRegistry registry;
        final boolean ownsRegistry;
        final LocalResponseCacheManager manager;
        final EffectiveCachePolicy.Selection policy;
        final AtomicInteger acquired = new AtomicInteger();
        final AtomicInteger released = new AtomicInteger();
        final AtomicInteger terminals = new AtomicInteger();

        Fixture(boolean single, int callers, int loads) { this(single, callers, loads, null); }

        Fixture(boolean single, int callers, int loads, SimpleMeterRegistry registry) {
            ownsRegistry = registry == null;
            this.registry = ownsRegistry ? new SimpleMeterRegistry() : registry;
            var config = new ReactiveHttpClientProperties.CachePolicyConfig();
            config.setTtlMs(60_000L);
            config.setMaximumSize(100L);
            config.setRefreshAfterMs(1_000L);
            config.setRefreshTimeoutMs(30_000L);
            config.setSingleFlight(single);
            config.setWork(new ReactiveHttpClientProperties.CacheWorkConfig());
            config.getWork().setMaximumConcurrentCallers((long) callers);
            config.getWork().setMaximumConcurrentLoads((long) loads);
            config.getWork().setMaximumConcurrentRefreshes(1L);
            policy = new EffectiveCachePolicy.Selection(true, EffectiveCachePolicy.Source.CLIENT, POLICY, config);
            var metrics = LocalResponseCacheMetrics.enabled(this.registry, "ownership-client");
            metrics.registerApi(API);
            manager = LocalResponseCacheManager.testing(time::get, scheduler, metrics, "ownership-client");
            manager.configureWork(new CacheWorkPolicy.Snapshot(Map.of(),
                    Map.of(POLICY, new CacheWorkPolicy.Limits(callers, loads, 1))));
        }

        Mono<?> call(String key, Supplier<Mono<?>> loader) { return call(key(key), loader, null); }

        Mono<?> call(CacheKeyContract.OpaqueKey key, Supplier<Mono<?>> loader, SubscriptionReportingState state) {
            return Mono.defer(() -> {
                var reservation = manager.callerAdmission().acquire(POLICY);
                acquired.incrementAndGet();
                return CacheWorkAdmission.own(manager.getOrLoad(policy, key, API, ignored -> loader.get(),
                                LocalResponseCacheManager.ResponseMetadata::successWithoutHeaders,
                                state, null, Context.empty()), reservation, ignored -> released.incrementAndGet());
            }).doFinally(ignored -> terminals.incrementAndGet());
        }

        void counts(int callers, int loads, int refreshes) {
            assertThat(manager.callerAdmission().active(POLICY)).isEqualTo(callers);
            assertThat(manager.activeLoadsForTesting(POLICY)).isEqualTo(loads);
            assertThat(manager.activeRefreshesForTesting(POLICY)).isEqualTo(refreshes);
        }

        double loadTerminals() {
            return registry.find(LocalResponseCacheMetrics.PREFIX + ".loads").counters().stream()
                    .mapToDouble(Counter::count).sum();
        }

        @Override public void close() {
            manager.close();
            scheduler.dispose();
            if (ownsRegistry) { registry.close(); }
        }
    }
}
