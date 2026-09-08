package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.annotation.*;
import io.github.huynhngochuyhoang.httpstarter.auth.AuthContext;
import io.github.huynhngochuyhoang.httpstarter.auth.AuthProvider;
import io.github.huynhngochuyhoang.httpstarter.auth.OutboundAuthFilter;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import io.github.huynhngochuyhoang.httpstarter.exception.AuthProviderException;
import io.github.huynhngochuyhoang.httpstarter.exception.LogicalCallTimeoutException;
import io.github.huynhngochuyhoang.httpstarter.exception.RequestSerializationException;
import io.github.huynhngochuyhoang.httpstarter.filter.InboundHeadersWebFilter;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientFailureStage;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientObserver;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientObserverEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.reactivestreams.Subscription;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.*;
import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;
import reactor.netty.DisposableServer;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.server.HttpServer;
import reactor.test.StepVerifier;
import reactor.util.context.Context;

import java.lang.ref.WeakReference;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.*;

class CacheCallerAdmissionContractTest {
    private static final Duration WAIT = Duration.ofSeconds(10);

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void rejectsBeforeAnyPreparationAndSharesCapacityAcrossApis(boolean post) throws Exception {
        try (Fixture f = new Fixture(2)) {
            Sinks.One<AuthContext> auth = Sinks.one();
            f.auth = () -> auth.asMono();
            Mono<String> cold = f.call(post, "one");
            var one = cold.toFuture();
            var two = f.client.other("two").contextWrite(Fixture.CONTEXT).toFuture();
            await(() -> f.authCalls.get() == 2, "two held authorization calls");
            assertThat(f.active()).isEqualTo(2);
            int serializers = f.serializations.get();
            int probes = f.probes.get();
            int defaults = f.defaults.get();
            AtomicInteger reads = new AtomicInteger();
            List<String> unread = new CountingList(reads, null);
            StepVerifier.create(f.client.get(unread).contextWrite(context -> context
                            .put("tenant", new CountingList(reads, null))
                            .put(InboundHeadersWebFilter.INBOUND_HEADERS_CONTEXT_KEY,
                                    Map.of("X-Secret", List.of("not-retained")))))
                    .expectError(CacheCallerAdmission.Rejected.class).verify(WAIT);
            StepVerifier.create(cold).expectError(CacheCallerAdmission.Rejected.class).verify(WAIT);
            assertThat(reads).hasValue(0);
            assertThat(f.serializations).hasValue(serializers);
            assertThat(f.authCalls).hasValue(2);
            assertThat(f.probes).hasValue(probes);
            assertThat(f.defaults).hasValue(defaults);
            assertThat(f.dispatches).hasValue(0);
            assertThat(f.manager.workloadSnapshotForTesting().inFlightLoads()).isZero();
            assertThat(f.manager.snapshot().currentSize()).isZero();
            f.assertRejections(2);
            // A different named policy and a cache-disabled endpoint have independent admission.
            f.auth = () -> Mono.just(AuthContext.empty());
            assertThat(f.client.spare().contextWrite(Fixture.CONTEXT).block(WAIT)).isEqualTo("response");
            assertThat(f.client.disabled().block(WAIT)).isEqualTo("response");
            assertThat(f.active()).isEqualTo(2);
            auth.tryEmitValue(AuthContext.empty()).orThrow();
            assertThat(one.get(10, TimeUnit.SECONDS)).isEqualTo("response");
            assertThat(two.get(10, TimeUnit.SECONDS)).isEqualTo("response");
            assertThat(f.active()).isZero();
        }
    }

    @Test
    void waitersAndWarmHitsRequireCallerSlotsButRefreshHasNoCallerOwner() throws Exception {
        try (Fixture f = new Fixture(2)) {
            Sinks.One<ClientResponse> response = Sinks.one();
            f.response = response::asMono;
            var first = f.call(false, "shared").toFuture();
            var waiter = f.call(false, "shared").toFuture();
            assertThat(f.manager.workloadSnapshotForTesting().coalescedWaiters()).isEqualTo(1);
            assertThat(f.active()).isEqualTo(2);
            StepVerifier.create(f.call(false, "shared"))
                    .expectError(CacheCallerAdmission.Rejected.class).verify(WAIT);
            assertThat(f.dispatches).hasValue(1);
            waiter.cancel(true);
            assertThat(f.active()).isEqualTo(1);
            response.tryEmitValue(Fixture.ok()).orThrow();
            assertThat(first.get(10, TimeUnit.SECONDS)).isEqualTo("response");
            assertThat(f.active()).isZero();

            Sinks.One<AuthContext> auth = Sinks.one();
            f.auth = () -> auth.asMono();
            var hit1 = f.call(false, "shared").toFuture();
            var hit2 = f.call(false, "shared").toFuture();
            int probes = f.probes.get();
            StepVerifier.create(f.call(false, "shared"))
                    .expectError(CacheCallerAdmission.Rejected.class).verify(WAIT);
            assertThat(f.probes).hasValue(probes);
            assertThat(f.dispatches).hasValue(1);
            hit1.cancel(true);
            hit2.cancel(true);
            assertThat(f.active()).isZero();

            f.auth = () -> Mono.just(AuthContext.empty());
            f.ticker.addAndGet(Duration.ofSeconds(2).toNanos());
            Sinks.One<ClientResponse> refresh = Sinks.one();
            f.response = refresh::asMono;
            assertThat(f.call(false, "shared").block(WAIT)).isEqualTo("response");
            assertThat(f.active()).isZero();
            assertThat(f.manager.workloadSnapshotForTesting().inFlightRefreshes()).isEqualTo(1);
            assertThat(f.call(false, "shared").block(WAIT)).isEqualTo("response");
            assertThat(f.active()).isZero();
            refresh.tryEmitValue(Fixture.ok()).orThrow();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void admittedWarmHitsStillAuthorizeAndPreserveFrozenFinalWireIdentity(boolean post) throws Exception {
        try (Fixture f = new Fixture(1, true)) {
            List<String> tenant = new ArrayList<>(List.of("a"));
            Sinks.One<AuthContext> auth = Sinks.one();
            f.auth = () -> auth.asMono();
            var first = f.publisher(post, "identity").contextWrite(context -> context.put("tenant", tenant)).toFuture();
            await(() -> f.authCalls.get() == 1, "probe authorization");
            tenant.set(0, "b");
            auth.tryEmitValue(AuthContext.empty()).orThrow();
            assertThat(first.get(10, TimeUnit.SECONDS)).isEqualTo("response");
            assertThat(f.wire).hasSize(1);
            assertThat(f.wire.getFirst().scope()).isEqualTo("a");
            assertThat(f.wire.getFirst().uri()).contains("/a");
            if (post) {
                assertThat(f.wire.getFirst().body()).isEqualTo("{\"text\":\"identity\"}");
            }
            f.auth = Mono::empty;
            assertThat(f.call(post, "identity").block(WAIT)).isEqualTo("response");
            assertThat(f.wire).hasSize(1);
            f.auth = () -> Mono.error(new IllegalStateException("auth-failed"));
            StepVerifier.create(f.call(post, "identity")).expectError(AuthProviderException.class).verify(WAIT);
            assertThat(f.active()).isZero();
            assertThat(f.wire).hasSize(1);
            f.auth = () -> Mono.just(AuthContext.empty());
            assertThat(f.publisher(post, "identity").contextWrite(context ->
                    context.put("tenant", List.of("b"))).block(WAIT)).isEqualTo("response");
            assertThat(f.wire).hasSize(2);
            assertThat(f.wire.getLast().scope()).isEqualTo("b");
            assertThat(f.wire.getLast().uri()).contains("/b");
            assertThat(f.authCalls).hasValue(4);
        }
    }

    enum Callback { FREEZE, SERIALIZER, AUTH, AUTH_SUBSCRIBE, FILTER, FILTER_SUBSCRIBE, DEFAULT_REQUEST }

    @ParameterizedTest
    @EnumSource(Callback.class)
    void cancellationKeepsCapacityUntilSynchronousCallbackUnwinds(Callback callback) throws Exception {
        try (Fixture f = new Fixture(1); Gate gate = new Gate()) {
            AtomicInteger reads = new AtomicInteger();
            Mono<String> call;
            switch (callback) {
                case FREEZE -> call = f.client.get(new CountingList(reads, gate));
                case SERIALIZER -> {
                    f.serializerGate = gate;
                    call = f.publisher(true, "blocked");
                }
                case AUTH -> {
                    f.auth = () -> { gate.run(); return Mono.just(AuthContext.empty()); };
                    call = f.publisher(false, "blocked");
                }
                case AUTH_SUBSCRIBE -> {
                    f.auth = () -> Mono.defer(() -> { gate.run(); return Mono.just(AuthContext.empty()); });
                    call = f.publisher(false, "blocked");
                }
                case FILTER -> {
                    f.filterGate = gate;
                    call = f.publisher(false, "blocked");
                }
                case FILTER_SUBSCRIBE -> {
                    f.filterSubscribeGate = gate;
                    call = f.publisher(false, "blocked");
                }
                case DEFAULT_REQUEST -> {
                    f.defaultGate = gate;
                    call = f.publisher(false, "blocked");
                }
                default -> throw new AssertionError(callback);
            }
            var future = call.contextWrite(Fixture.CONTEXT).subscribeOn(Schedulers.boundedElastic()).toFuture();
            gate.awaitEntered();
            assertThat(f.active()).isEqualTo(1);
            future.cancel(true);
            assertThat(f.active()).as("executing callback still owns admission").isEqualTo(1);
            StepVerifier.create(f.call(false, "replacement"))
                    .expectError(CacheCallerAdmission.Rejected.class).verify(WAIT);
            gate.close();
            await(() -> f.active() == 0, "callback exit releases original reservation");
            assertThat(f.dispatches).hasValue(0);
            assertThat(f.manager.snapshot().currentSize()).isZero();
            f.serializerGate = null;
            f.filterGate = null;
            f.filterSubscribeGate = null;
            f.defaultGate = null;
            f.auth = () -> Mono.just(AuthContext.empty());
            assertThat(f.call(false, "replacement").block(WAIT)).isEqualTo("response");
            assertThat(f.active()).isZero();
            assertThat(f.dispatches).hasValue(1);
        }
    }

    @Test
    void deadlineIsArmedBeforeSynchronousPreparationAndDoesNotReleaseExecutingFrame() throws Exception {
        try (Fixture f = new Fixture(1); Gate gate = new Gate()) {
            f.config.setLogicalCallTimeoutMs(60_000);
            f.auth = () -> { gate.run(); return Mono.just(AuthContext.empty()); };
            var worker = Schedulers.newSingle("admission-blocked-auth");
            try {
                StepVerifier.withVirtualTime(() -> f.call(false, "blocked").subscribeOn(worker))
                        .then(() -> assertThatCode(gate::awaitEntered).doesNotThrowAnyException())
                        .thenAwait(Duration.ofMinutes(2))
                        .expectError(LogicalCallTimeoutException.class).verify(WAIT);
            } finally {
                worker.dispose();
            }
            assertThat(f.active()).isEqualTo(1);
            assertThat(f.events).hasSize(1);
            assertThat(f.events.getFirst().getError()).isInstanceOf(LogicalCallTimeoutException.class);
            assertThat(f.events.getFirst().getFailureStage()).isNull();
            gate.close();
            await(() -> f.active() == 0, "timed-out auth frame exit");
            assertThat(f.dispatches).hasValue(0);
            assertThat(f.manager.snapshot().currentSize()).isZero();
        }
    }

    @Test
    void oneDeadlineKeepsResponseBodyAttributionAndTerminatesWaitingCallers() {
        try (Fixture f = new Fixture(2)) {
            assertThat(f.call(false, "warmup").block(WAIT)).isEqualTo("response");
            f.manager.evictAllForTesting();
            f.events.clear();
            f.lifecycle.clear();
            f.logger.records.clear();
            f.config.setLogicalCallTimeoutMs(60_000);
            f.response = () -> Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header("X-Phase", "body")
                    .body(Flux.never()).build());
            StepVerifier.withVirtualTime(() -> f.call(false, "body"))
                    .then(() -> assertThat(f.dispatches).hasValue(2))
                    .thenAwait(Duration.ofMinutes(2))
                    .expectError(LogicalCallTimeoutException.class).verify(WAIT);
            assertThat(f.active()).isZero();
            assertThat(f.events).hasSize(1);
            assertThat(f.events.getFirst().getError()).isInstanceOf(LogicalCallTimeoutException.class);
            assertThat(f.events.getFirst().getFailureStage()).isEqualTo(HttpClientFailureStage.RESPONSE_BODY);
            assertThat(f.lifecycle).hasSize(1);
            assertThat(f.lifecycle.getFirst().error()).isInstanceOf(LogicalCallTimeoutException.class);
            assertThat(f.logger.records).hasSize(1);
            assertThat(f.logger.records.getFirst().responseHeaders()).containsKey("X-Phase");
        }
    }

    @Test
    void aWaiterTimeoutReleasesOnlyItsCallerReservation() throws Exception {
        try (Fixture f = new Fixture(2)) {
            Sinks.One<ClientResponse> response = Sinks.one();
            f.response = response::asMono;
            var leader = f.call(false, "shared").toFuture();
            f.config.setLogicalCallTimeoutMs(60_000);
            StepVerifier.withVirtualTime(() -> f.call(false, "shared"))
                    .then(() -> assertThat(f.manager.workloadSnapshotForTesting().coalescedWaiters()).isEqualTo(1))
                    .thenAwait(Duration.ofMinutes(2))
                    .expectError(LogicalCallTimeoutException.class).verify(WAIT);
            assertThat(f.active()).isEqualTo(1);
            assertThat(leader).isNotDone();
            assertThat(f.dispatches).hasValue(1);
            response.tryEmitValue(Fixture.ok()).orThrow();
            assertThat(leader.get(10, TimeUnit.SECONDS)).isEqualTo("response");
            assertThat(f.active()).isZero();
        }
    }

    @Test
    void preparationFailuresEmptyCompletionAndImmediateResubscriptionsReleaseExactlyOnce() {
        try (Fixture f = new Fixture(1)) {
            f.serializationFailure = true;
            StepVerifier.create(f.call(true, "json")).expectError(RequestSerializationException.class).verify(WAIT);
            assertThat(f.active()).isZero();
            assertThat(f.authCalls).hasValue(0);
            f.serializationFailure = false;
            f.auth = () -> { throw new IllegalStateException("synchronous-auth"); };
            StepVerifier.create(f.call(false, "throw")).expectError().verify(WAIT);
            assertThat(f.active()).isZero();
            f.auth = () -> Mono.error(new IllegalArgumentException("async-auth"));
            StepVerifier.create(f.call(false, "retry").retry(20)).expectError(AuthProviderException.class).verify(WAIT);
            assertThat(f.active()).isZero();
            assertThat(f.events).noneSatisfy(event -> assertThat(event.getError())
                    .isInstanceOf(CacheCallerAdmission.Rejected.class));
            f.auth = () -> Mono.just(AuthContext.empty());
            f.response = () -> Mono.just(ClientResponse.create(HttpStatus.NO_CONTENT).build());
            StepVerifier.create(f.call(false, "empty").repeat(20)).verifyComplete();
            assertThat(f.active()).isZero();
            f.response = () -> Mono.just(Fixture.ok());
            assertThat(f.call(false, "hit").repeat(20).collectList().block(WAIT)).hasSize(21);
            assertThat(f.active()).isZero();
        }
    }

    @Test
    void cancelledBeforeSourceAttachmentDoesNotPrepareOrLeakCapacity() {
        try (Fixture f = new Fixture(1)) {
            f.call(true, "cancelled").subscribe(new BaseSubscriber<>() {
                @Override protected void hookOnSubscribe(Subscription subscription) { cancel(); }
            });
            assertThat(f.active()).isZero();
            assertThat(f.authCalls).hasValue(0);
            assertThat(f.serializations).hasValue(0);
            assertThat(f.dispatches).hasValue(0);
            assertThat(f.call(false, "next").block(WAIT)).isEqualTo("response");
        }
    }

    @Test
    void repeatedColdSubscriptionsAndParallelSerializersEachAcquireBeforePreparing() throws Exception {
        try (Fixture f = new Fixture(2); Gate gate = new Gate()) {
            f.serializerGate = gate;
            Mono<String> cold = f.call(true, "same");
            var first = cold.toFuture();
            var second = cold.toFuture();
            await(() -> f.serializations.get() == 2, "both subscriptions entered serialization");
            assertThat(f.active()).isEqualTo(2);
            StepVerifier.create(cold).expectError(CacheCallerAdmission.Rejected.class).verify(WAIT);
            assertThat(f.serializations).hasValue(2);
            assertThat(f.authCalls).hasValue(0);
            assertThat(f.probes).hasValue(0);
            assertThat(f.dispatches).hasValue(0);
            gate.close();
            assertThat(first.get(10, TimeUnit.SECONDS)).isEqualTo("response");
            assertThat(second.get(10, TimeUnit.SECONDS)).isEqualTo("response");
            assertThat(f.active()).isZero();
        }
    }

    @Test
    void concurrentAcquisitionsCannotExceedCapacity() throws Exception {
        CacheCallerAdmission admission = new CacheCallerAdmission(Map.of("work", 3));
        CountDownLatch start = new CountDownLatch(1);
        List<CacheCallerAdmission.Reservation> admitted = new CopyOnWriteArrayList<>();
        AtomicInteger rejected = new AtomicInteger();
        try (ExecutorService workers = Executors.newFixedThreadPool(8)) {
            List<Future<?>> tasks = new ArrayList<>();
            for (int i = 0; i < 64; i++) {
                tasks.add(workers.submit(() -> {
                    try {
                        start.await();
                        admitted.add(admission.acquire("work"));
                    } catch (CacheCallerAdmission.Rejected expected) {
                        rejected.incrementAndGet();
                    } catch (InterruptedException error) {
                        throw new AssertionError(error);
                    }
                }));
            }
            start.countDown();
            for (Future<?> task : tasks) { task.get(10, TimeUnit.SECONDS); }
            assertThat(admitted).hasSize(3);
            assertThat(rejected).hasValue(61);
            assertThat(admission.active("work")).isEqualTo(3);
            admitted.forEach(CacheCallerAdmission.Reservation::complete);
            assertThat(admission.active("work")).isZero();
        }
    }

    @Test
    void anUnselectedCallerCannotInheritAnotherLogicalCallsReservation() {
        CacheCallerAdmission parent = new CacheCallerAdmission(Map.of("parent", 1));
        var ended = parent.acquire("parent");
        ended.complete();
        try (Fixture f = new Fixture(Map.of(), false)) {
            assertThat(f.call(true, "unbounded").contextWrite(context ->
                    context.put(CacheCallerAdmission.CONTEXT_KEY, ended)).block(WAIT)).isEqualTo("response");
            assertThat(f.serializations).hasValue(1);
            assertThat(f.active()).isZero();
        }
    }

    @Test
    void capacitiesAreFactoryLocalAndNotResetByCloseOrRepeatedTerminalCleanup() {
        Map<String, Integer> input = new HashMap<>(Map.of("work", 1));
        CacheCallerAdmission admission = new CacheCallerAdmission(input);
        input.put("work", 10);
        var first = admission.acquire("work");
        assertThatThrownBy(() -> admission.acquire("work")).isInstanceOf(CacheCallerAdmission.Rejected.class);
        var other = new CacheCallerAdmission(input);
        other.acquire("work").complete();
        first.complete();
        var second = admission.acquire("work");
        first.complete();
        assertThat(admission.active("work")).isEqualTo(1);
        admission.close();
        assertThat(admission.active("work")).isEqualTo(1);
        assertThatThrownBy(() -> admission.acquire("work")).isInstanceOf(IllegalStateException.class);
        second.complete();
        assertThat(admission.active("work")).isZero();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void terminalPreparationReleasesArgumentsContextAndAuthWhileManagerStaysOpen(boolean success) throws Exception {
        try (Fixture f = new Fixture(1)) {
            List<WeakReference<?>> references = terminalReferences(f, success);
            f.events.clear();
            f.lifecycle.clear();
            f.logger.records.clear();
            for (int attempt = 0; attempt < 40 && references.stream().anyMatch(ref -> ref.get() != null); attempt++) {
                System.gc();
                Thread.sleep(25);
            }
            assertThat(references).allSatisfy(reference -> assertThat(reference.get()).isNull());
            assertThat(f.active()).isZero();
            f.auth = () -> Mono.just(AuthContext.empty());
            assertThat(f.call(false, "reuse").block(WAIT)).isEqualTo("response");
        }
    }

    private static List<WeakReference<?>> terminalReferences(Fixture f, boolean success) throws Exception {
        Query body = new Query(new String("not-retained"));
        List<String> tenant = new ArrayList<>(List.of("tenant"));
        Sinks.One<AuthContext> auth = Sinks.one();
        f.auth = () -> auth.asMono();
        var future = f.client.post(body).contextWrite(context -> context.put("tenant", tenant)).toFuture();
        await(() -> f.authCalls.get() == 1, "pending auth");
        AuthContext credential = new AuthContext(Map.of("X-Auth", "synthetic"), Map.of());
        if (success) {
            auth.tryEmitValue(credential).orThrow();
            assertThat(future.get(10, TimeUnit.SECONDS)).isEqualTo("response");
        } else {
            future.cancel(true);
        }
        f.auth = () -> Mono.just(AuthContext.empty());
        return List.of(new WeakReference<>(body), new WeakReference<>(tenant),
                new WeakReference<>(auth), new WeakReference<>(credential));
    }

    @LogHttpExchange(logger = RecordingLogger.class)
    interface Client {
        @GET("/get") Mono<String> get(@QueryParam("q") @CacheKey("body") List<String> values);
        @GET("/other") Mono<String> other(@QueryParam("other") @CacheKey("body") String value);
        @POST("/post") @CacheResponse(value = "work", semanticRead = true)
        Mono<String> post(@Body @CacheKey("body") Query body);
        @GET("/spare") @CacheResponse("spare") Mono<String> spare();
        @GET("/disabled") @CacheDisabled Mono<String> disabled();
    }

    record Query(String text) { }
    record Wire(String uri, String scope, String body) { }

    static final class RecordingLogger implements HttpExchangeLogger {
        final List<HttpExchangeLogContext> records = new CopyOnWriteArrayList<>();
        @Override public void log(HttpExchangeLogContext context) { records.add(context); }
    }

    private static final class CountingList extends AbstractList<String> {
        private final AtomicInteger reads;
        private final Gate gate;
        CountingList(AtomicInteger reads, Gate gate) { this.reads = reads; this.gate = gate; }
        @Override public int size() { return 1; }
        @Override public String get(int index) {
            reads.incrementAndGet();
            if (gate != null) { gate.run(); }
            return "value";
        }
    }

    private static final class Gate implements AutoCloseable {
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        void run() {
            entered.countDown();
            boolean interrupted = false;
            while (true) {
                try {
                    if (!release.await(10, TimeUnit.SECONDS)) { throw new AssertionError("Gate not released"); }
                    break;
                } catch (InterruptedException ignored) { interrupted = true; }
            }
            if (interrupted) { Thread.currentThread().interrupt(); }
        }
        void awaitEntered() throws Exception { assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue(); }
        @Override public void close() { release.countDown(); }
    }

    private static final class Fixture implements AutoCloseable {
        static final Context CONTEXT = Context.of("tenant", List.of("a"));
        final GenericApplicationContext context = new GenericApplicationContext();
        final AtomicLong ticker = new AtomicLong();
        final AtomicInteger serializations = new AtomicInteger();
        final AtomicInteger authCalls = new AtomicInteger();
        final AtomicInteger probes = new AtomicInteger();
        final AtomicInteger defaults = new AtomicInteger();
        final AtomicInteger dispatches = new AtomicInteger();
        final List<HttpClientObserverEvent> events = new CopyOnWriteArrayList<>();
        final List<ReactiveHttpClientLifecycleContext> lifecycle = new CopyOnWriteArrayList<>();
        final RecordingLogger logger = new RecordingLogger();
        final List<Wire> wire = new CopyOnWriteArrayList<>();
        final ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();
        final LocalResponseCacheManager manager;
        final Client client;
        final DisposableServer server;
        volatile Supplier<Mono<AuthContext>> auth = () -> Mono.just(AuthContext.empty());
        volatile Supplier<Mono<ClientResponse>> response = () -> Mono.just(ok());
        volatile Gate serializerGate;
        volatile Gate filterGate;
        volatile Gate filterSubscribeGate;
        volatile Gate defaultGate;
        volatile boolean serializationFailure;

        Fixture(int capacity) { this(capacity, false); }

        Fixture(int capacity, boolean realWire) {
            this(Map.of("work", capacity, "spare", capacity), realWire);
        }

        Fixture(Map<String, Integer> callerMaximums, boolean realWire) {
            context.registerBean("observer", HttpClientObserver.class, () -> events::add);
            context.registerBean("logger", RecordingLogger.class, () -> logger);
            context.registerBean("hook", ReactiveHttpClientLifecycleHook.class, () -> new ReactiveHttpClientLifecycleHook() {
                @Override public void onSuccess(ReactiveHttpClientLifecycleContext c) { lifecycle.add(c); }
                @Override public void onError(ReactiveHttpClientLifecycleContext c) { lifecycle.add(c); }
                @Override public void onCancel(ReactiveHttpClientLifecycleContext c) { lifecycle.add(c); }
            });
            context.refresh();
            server = realWire ? HttpServer.create().host("127.0.0.1").port(0).handle((request, response) ->
                    request.receive().aggregate().asString().defaultIfEmpty("").flatMap(body -> {
                        wire.add(new Wire(request.uri(), request.requestHeaders().get("X-Scope"), body));
                        return response.sendString(Mono.just("response")).then();
                    })).bindNow(WAIT) : null;
            String base = server != null ? "http://127.0.0.1:" + server.port() : "http://cache.example.invalid";
            config.setAuthProvider("auth");
            config.setRequestTimeoutMs(0);
            config.setLogicalCallTimeoutMs(0);
            config.getCache().setPolicy("work");
            config.setDefaultHeaders(Map.of("X-Scope", "default"));
            for (String name : List.of("work", "spare")) {
                var policy = new ReactiveHttpClientProperties.CachePolicyConfig();
                policy.setTtlMs(60_000L);
                policy.setMaximumSize(100L);
                policy.setSingleFlight(true);
                policy.setRefreshAfterMs(1_000L);
                policy.setRefreshTimeoutMs(30_000L);
                policy.setSharedResponse(true);
                policy.setVaryByContext(List.of("tenant"));
                policy.setVaryByHeaders(List.of("X-Scope"));
                config.getCache().getPolicies().put(name, policy);
            }
            config.getCache().getPolicies().get("work").setVaryByParameters(List.of("body"));
            MethodMetadataCache metadata = new MethodMetadataCache();
            AuthProvider provider = request -> {
                authCalls.incrementAndGet();
                return auth.get();
            };
            org.springframework.boot.webclient.WebClientCustomizer defaultsCustomizer =
                    builder -> builder.defaultRequest(request -> {
                        defaults.incrementAndGet();
                        if (defaultGate != null) { defaultGate.run(); }
                    });
            ReactiveHttpClientCustomizer requestCustomizer = builder -> builder.filter((request, next) -> {
                        probes.incrementAndGet();
                        if (filterGate != null) { filterGate.run(); }
                        return Mono.deferContextual(ctx -> {
                            if (filterSubscribeGate != null) { filterSubscribeGate.run(); }
                            String scope = ctx.<List<String>>getOrDefault("tenant", List.of("a")).getFirst();
                            URI uri = request.url();
                            String target = uri.getScheme() + "://" + uri.getRawAuthority() + uri.getRawPath() + "/" + scope
                                    + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
                            return next.exchange(ClientRequest.from(request).url(URI.create(target))
                                    .headers(headers -> headers.set("X-Scope", scope)).build());
                        });
                    });
            context.getBeanFactory().registerSingleton("defaultsCustomizer", defaultsCustomizer);
            context.getBeanFactory().registerSingleton("requestCustomizer", requestCustomizer);
            config.getCache().getCustomizations().put("defaultsCustomizer",
                    ReactiveHttpClientProperties.CacheCustomizationSafety.SAFE);
            config.getCache().getCustomizations().put("requestCustomizer",
                    ReactiveHttpClientProperties.CacheCustomizationSafety.SAFE);
            CacheCustomizationValidator.validate(context.getBeanFactory(), Client.class, "admission", metadata, config);
            WebClient.Builder builder = WebClient.builder().baseUrl(base);
            defaultsCustomizer.customize(builder);
            builder.filter(new OutboundAuthFilter("admission", provider));
            requestCustomizer.customize(builder);
            builder.filter(ReactiveClientInvocationHandler.finalRequestObservationFilter());
            ExchangeFunction exchange = realWire ? ExchangeFunctions.create(
                    new ReactorClientHttpConnector(HttpClient.create().disableRetry(true))) : request -> response.get();
            builder.exchangeFunction(request -> {
                dispatches.incrementAndGet();
                return exchange.exchange(request);
            });
            manager = LocalResponseCacheManager.testing(ticker::get, Schedulers.parallel(),
                    callerMaximums);
            ReactiveHttpClientJsonCodec codec = new ReactiveHttpClientJsonCodec() {
                @Override public byte[] write(Object value) throws Exception {
                    return TestJsonCodecs.jsonCodec().write(value);
                }
                @Override public byte[] writeBounded(Object value, int maximum) throws Exception {
                    serializations.incrementAndGet();
                    if (serializerGate != null) { serializerGate.run(); }
                    if (serializationFailure) { throw new IllegalArgumentException("serialization-failed"); }
                    return TestJsonCodecs.jsonCodec().writeBounded(value, maximum);
                }
                @Override public <T> T read(byte[] value, Class<T> type) throws Exception {
                    return TestJsonCodecs.jsonCodec().read(value, type);
                }
            };
            var handler = new ReactiveClientInvocationHandler(builder.build(), metadata, new RequestArgumentResolver(),
                    new DefaultErrorDecoder(), config, "admission", Client.class, context,
                    new NoopResilienceOperatorApplier(), codec, new ReactiveHttpClientProperties.ObservabilityConfig(),
                    manager, provider, base);
            client = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[]{Client.class}, handler);
        }

        Mono<String> publisher(boolean post, String key) {
            return post ? client.post(new Query(key)) : client.get(List.of(key));
        }
        Mono<String> call(boolean post, String key) { return publisher(post, key).contextWrite(CONTEXT); }
        int active() { return manager.callerAdmission().active("work"); }
        static ClientResponse ok() { return ClientResponse.create(HttpStatus.OK).body("response").build(); }

        void assertRejections(int count) {
            assertThat(events).hasSize(count).allSatisfy(event -> {
                assertThat(event.getError()).isInstanceOf(CacheCallerAdmission.Rejected.class);
                assertThat(event.getAttemptCount()).isZero();
                assertThat(event.getRequestUrl()).isNull();
                assertThat(event.getStatusCode()).isNull();
                assertThat(event.getFailureStage()).isNull();
            });
            assertThat(lifecycle).hasSize(count).allSatisfy(c -> {
                assertThat(c.error()).isInstanceOf(CacheCallerAdmission.Rejected.class);
                assertThat(c.attemptNumber()).isZero();
                assertThat(c.requestBody()).isNull();
                assertThat(c.headers()).isEmpty();
                assertThat(c.requestUrl()).isNull();
            });
            assertThat(logger.records).hasSize(count).allSatisfy(c -> {
                assertThat(c.error()).isInstanceOf(CacheCallerAdmission.Rejected.class);
                assertThat(c.requestBody()).isNull();
                assertThat(c.requestHeaders()).isEmpty();
                assertThat(c.inboundHeaders()).isEmpty();
                assertThat(c.responseHeaders()).isEmpty();
                assertThat(c.subscriptionAttemptCount()).isZero();
            });
        }

        @Override public void close() {
            manager.close();
            context.close();
            if (server != null) { server.disposeNow(WAIT); }
        }
    }

    private static void await(BooleanSupplier condition, String description) throws Exception {
        long deadline = System.nanoTime() + WAIT.toNanos();
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) { Thread.sleep(5); }
        assertThat(condition.getAsBoolean()).as(description).isTrue();
    }
}
