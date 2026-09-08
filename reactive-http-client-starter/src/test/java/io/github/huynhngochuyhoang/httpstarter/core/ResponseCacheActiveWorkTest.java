package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.annotation.*;
import io.github.huynhngochuyhoang.httpstarter.auth.AuthContext;
import io.github.huynhngochuyhoang.httpstarter.auth.AuthProvider;
import io.github.huynhngochuyhoang.httpstarter.auth.OutboundAuthFilter;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeFunctions;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import reactor.netty.DisposableServer;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.server.HttpServer;
import reactor.netty.resources.ConnectionPoolMetrics;
import reactor.netty.resources.ConnectionProvider;
import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.net.SocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** Test-only live-owner instrumentation. No keys, bodies, or request targets enter the report. */
class ResponseCacheActiveWorkTest {
    private static final int CALLERS = 12;
    private static final int MAX_ENTRIES = 8;
    private static final int PAYLOAD_BYTES = 256;
    private static final long BYTE_LIMIT = (long) MAX_ENTRIES * PAYLOAD_BYTES;
    private static final int DEFAULT_POOL_MAXIMUM =
            new ReactiveHttpClientProperties.ConnectionPoolConfig().getMaxConnections();
    private static final Duration AWAIT = Duration.ofSeconds(10);
    private static final long TTL_MS = 60_000;
    private static final long REFRESH_AFTER_MS = 1_000;
    private static final long REFRESH_TIMEOUT_MS = 60_000;

    enum Verb { GET, POST }
    enum Burst { DISABLED, DISTINCT, INDEPENDENT_DUPLICATES, SINGLE_FLIGHT }
    enum Limit { NONE, BULKHEAD, POOL }

    static Stream<Arguments> bursts() {
        return variants().flatMap(pair -> Arrays.stream(Burst.values())
                .map(burst -> Arguments.of(pair.get()[0], pair.get()[1], burst)));
    }

    static Stream<Arguments> variants() {
        return Arrays.stream(Verb.values()).flatMap(verb ->
                Stream.of(false, true).map(weighted -> Arguments.of(verb, weighted)));
    }

    static Stream<Arguments> limits() {
        return variants().flatMap(pair -> Arrays.stream(Limit.values())
                .map(limit -> Arguments.of(pair.get()[0], pair.get()[1], limit)));
    }

    @ParameterizedTest
    @MethodSource("bursts")
    void blockedBurstSeparatesCallersLoadsFlightsAndStorage(Verb verb, boolean weighted, Burst burst)
            throws Exception {
        boolean cached = burst != Burst.DISABLED;
        boolean singleFlight = burst != Burst.INDEPENDENT_DUPLICATES;
        try (Fixture f = new Fixture("burst-" + burst, verb, weighted, cached, singleFlight, false, Limit.NONE)) {
            f.checkpoint("admitted", false);
            List<CompletableFuture<byte[]>> calls = new ArrayList<>();
            for (int i = 0; i < CALLERS; i++) {
                calls.add(f.call(burst == Burst.SINGLE_FLIGHT || burst == Burst.INDEPENDENT_DUPLICATES ? 0 : i));
            }
            int loads = burst == Burst.SINGLE_FLIGHT ? 1 : CALLERS;
            await(() -> f.dispatches.get() == loads && f.httpActive.get() == loads
                    && f.authActive.get() == 0 && f.serializersActive.get() == 0
                    && f.manager.workloadSnapshotForTesting().coalescedWaiters()
                    == (burst == Burst.SINGLE_FLIGHT ? CALLERS - 1 : 0), "blocked loads and attached callers");
            Checkpoint saturated = f.checkpoint("saturated", false);
            assertThat(saturated.activeCallers()).isEqualTo(CALLERS);
            assertThat(saturated.activePreparationOwners()).isZero();
            assertThat(saturated.foregroundLoadTokens()).isEqualTo(cached ? loads : 0);
            assertThat(saturated.independentLoads()).isEqualTo(cached && !singleFlight ? loads : 0);
            assertThat(saturated.sharedFlights()).isEqualTo(cached && singleFlight ? loads : 0);
            assertThat(saturated.attachedMembers()).isEqualTo(cached && singleFlight ? CALLERS : 0);
            assertThat(saturated.coalescedWaiters()).isEqualTo(burst == Burst.SINGLE_FLIGHT ? CALLERS - 1 : 0);
            assertThat(saturated.generationMapOwners()).isEqualTo(!cached ? 0 :
                    burst == Burst.DISTINCT ? CALLERS : 1);
            assertThat(saturated.entries()).isZero();
            assertThat(saturated.pool().pendingAcquires()).isZero();
            assertThat(saturated.pool().activeConnections()).isEqualTo(loads);
            f.checkpoint("traffic-stopped", true);
            f.releaseResponses();
            for (CompletableFuture<byte[]> call : calls) {
                assertThat(call.get(AWAIT.toMillis(), TimeUnit.MILLISECONDS)).hasSize(PAYLOAD_BYTES);
            }
            f.awaitIdle();
            Checkpoint completed = f.checkpoint("source-terminal", true);
            assertThat(completed.entries()).isEqualTo(!cached ? 0 : burst == Burst.DISTINCT ? MAX_ENTRIES : 1);
            assertThat(completed.foregroundLoadTokens()).isZero();
            assertThat(completed.activeGenerationOwners()).isZero();
            assertThat(completed.generationMapOwners()).isEqualTo(completed.entries());
            if (cached && weighted) {
                assertThat(completed.retainedDecodedBytes()).isEqualTo(completed.entries() * PAYLOAD_BYTES);
            }
        }
    }

    @ParameterizedTest
    @MethodSource("limits")
    void bulkheadAndPoolLimitsDoNotBoundPreLookupAuthorization(Verb verb, boolean weighted, Limit limit)
            throws Exception {
        try (Fixture f = new Fixture("auth-" + limit, verb, weighted, true, true, false, limit)) {
            f.authGate = Sinks.one();
            f.checkpoint("admitted", false);
            for (int i = 0; i < CALLERS; i++) {
                f.call(i);
            }
            await(() -> f.authActive.get() == CALLERS, "pre-lookup auth subscriptions");
            Checkpoint preparing = f.checkpoint("preparation-saturated", false);
            assertThat(preparing.activeCallers()).isEqualTo(CALLERS);
            assertThat(preparing.activePreparationOwners()).isEqualTo(CALLERS);
            assertThat(preparing.foregroundLoadTokens()).isZero();
            assertThat(preparing.generationMapOwners()).isZero();
            assertThat(preparing.dispatches()).isZero();
            assertThat(preparing.pool().totalConnections()).isZero();
            assertThat(preparing.serializationCalls()).isEqualTo(verb == Verb.POST ? CALLERS : 0);
            assertThat(f.bulkheads.bulkhead("work").getMetrics().getAvailableConcurrentCalls()).isEqualTo(2);
            f.checkpoint("traffic-stopped-before-auth-release", true);

            f.authGate.tryEmitValue(AuthContext.empty()).orThrow();
            int active = limit == Limit.NONE ? CALLERS : limit == Limit.BULKHEAD ? 2 : 5;
            int dispatched = limit == Limit.POOL ? 2 : active;
            await(() -> f.authActive.get() == 0 && f.activeCallers.get() == active
                    && f.dispatches.get() == dispatched
                    && f.pool.snapshot(f.provider).pendingAcquires() == active - dispatched,
                    "settled admission and pool queue");
            Checkpoint saturated = f.checkpoint("load-saturated", true);
            assertThat(saturated.foregroundLoadTokens()).isEqualTo(active);
            assertThat(saturated.sharedFlights()).isEqualTo(active);
            assertThat(saturated.callerErrors()).isEqualTo(CALLERS - active);
            assertThat(saturated.entries()).isZero();
            assertThat(f.errors).allSatisfy(error -> {
                if (limit == Limit.BULKHEAD) {
                    assertThat(error).isInstanceOf(BulkheadFullException.class);
                }
                else {
                    assertThat(causeNames(error)).contains("PoolAcquirePendingLimitException");
                }
            });
            f.releaseResponses();
            f.awaitIdle();
            Checkpoint terminal = f.checkpoint("source-terminal", true);
            assertThat(terminal.foregroundLoadTokens()).isZero();
            assertThat(terminal.activeGenerationOwners()).isZero();
            assertThat(terminal.dispatches()).isEqualTo(active);
            assertThat(terminal.callerSuccesses()).isEqualTo(active);
        }
    }

    @ParameterizedTest
    @MethodSource("variants")
    void staleKeysRetainIndependentRefreshOwnersAfterCallersComplete(Verb verb, boolean weighted) throws Exception {
        try (Fixture f = new Fixture("refresh", verb, weighted, true, true, true, Limit.NONE)) {
            f.releaseResponses();
            for (int key = 0; key < MAX_ENTRIES; key++) {
                assertThat(f.call(key).get(AWAIT.toMillis(), TimeUnit.MILLISECONDS)).hasSize(PAYLOAD_BYTES);
            }
            f.awaitIdle();
            f.checkpoint("admitted", false);
            f.responses = Sinks.one();
            f.ticker.addAndGet(Duration.ofMillis(REFRESH_AFTER_MS).toNanos());
            for (int key = 0; key < MAX_ENTRIES; key++) {
                assertThat(f.call(key).get(AWAIT.toMillis(), TimeUnit.MILLISECONDS)).hasSize(PAYLOAD_BYTES);
            }
            await(() -> f.dispatches.get() == 2 * MAX_ENTRIES && f.activeCallers.get() == 0,
                    "all stale refreshes dispatched");
            Checkpoint saturated = f.checkpoint("saturated", false);
            assertThat(saturated.hiddenRefreshes()).isEqualTo(MAX_ENTRIES);
            assertThat(saturated.refreshTokens()).isEqualTo(MAX_ENTRIES);
            assertThat(saturated.activeGenerationOwners()).isEqualTo(MAX_ENTRIES);
            assertThat(saturated.foregroundLoadTokens()).isZero();
            assertThat(saturated.activeCallers()).isZero();
            assertThat(saturated.entries()).isEqualTo(MAX_ENTRIES);
            for (int key = 0; key < MAX_ENTRIES; key++) {
                f.call(key).get(AWAIT.toMillis(), TimeUnit.MILLISECONDS);
            }
            assertThat(f.checkpoint("traffic-stopped", true).hiddenRefreshes()).isEqualTo(MAX_ENTRIES);
            assertThat(f.dispatches).hasValue(2 * MAX_ENTRIES);
            f.releaseResponses();
            f.awaitIdle();
            assertThat(f.checkpoint("source-terminal", true).refreshTokens()).isZero();
        }
    }

    @Test
    void firstCallerCancellationDoesNotEndTheSharedSource() throws Exception {
        try (Fixture f = new Fixture("detached-leader", Verb.POST, true, true, true, false, Limit.NONE)) {
            List<CompletableFuture<byte[]>> calls = new ArrayList<>();
            for (int i = 0; i < CALLERS; i++) {
                calls.add(f.call(0));
            }
            await(() -> f.dispatches.get() == 1
                    && f.manager.workloadSnapshotForTesting().coalescedWaiters() == CALLERS - 1, "attached callers");
            f.checkpoint("saturated", false);
            assertThat(calls.getFirst().cancel(true)).isTrue();
            Checkpoint detached = f.checkpoint("first-caller-terminal", true);
            assertThat(detached.activeCallers()).isEqualTo(CALLERS - 1);
            assertThat(detached.attachedMembers()).isEqualTo(CALLERS - 1);
            assertThat(detached.foregroundLoadTokens()).isEqualTo(1);
            assertThat(detached.httpExchangesAwaitingHeaders()).isEqualTo(1);
            f.releaseResponses();
            for (CompletableFuture<byte[]> call : calls.subList(1, calls.size())) {
                assertThat(call.get(AWAIT.toMillis(), TimeUnit.MILLISECONDS)).hasSize(PAYLOAD_BYTES);
            }
            f.awaitIdle();
            assertThat(f.checkpoint("source-terminal", true).callerCancellations()).isEqualTo(1);
        }
    }

    @Test
    void independentLoadsKeepTheirExternalOwnersAfterCacheManagerClose() throws Exception {
        try (Fixture f = new Fixture("independent-close", Verb.GET, true, true, false, false, Limit.NONE)) {
            for (int i = 0; i < CALLERS; i++) {
                f.call(0);
            }
            await(() -> f.dispatches.get() == CALLERS, "independent dispatches");
            f.checkpoint("saturated", false);
            f.manager.close();
            Checkpoint closed = f.checkpoint("cache-manager-closed-transport-still-open", true);
            assertThat(closed.cacheClosed()).isTrue();
            assertThat(closed.activeCallers()).isEqualTo(CALLERS);
            assertThat(closed.foregroundLoadTokens()).isEqualTo(CALLERS);
            assertThat(closed.generationMapOwners()).isZero();
            assertThat(closed.activeGenerationOwners()).isEqualTo(1);
            assertThat(closed.entries()).isZero();
            f.releaseResponses();
            f.awaitIdle();
            Checkpoint terminal = f.checkpoint("external-source-terminal", true);
            assertThat(terminal.foregroundLoadTokens()).isZero();
            assertThat(terminal.callerSuccesses()).isEqualTo(CALLERS);
            assertThat(terminal.entries()).isZero();
        }
    }

    @Test
    void cancellationCannotStopANonCooperativeSerializerCallback() throws Exception {
        try (Fixture f = new Fixture("non-cooperative-preparation", Verb.POST, true, true, true, false, Limit.BULKHEAD)) {
            f.serializationGate = new CountDownLatch(1);
            Scheduler worker = Schedulers.newSingle("v30-blocked-codec");
            try {
                CompletableFuture<byte[]> call = f.publisher(0).subscribeOn(worker).toFuture();
                f.calls.add(call);
                await(() -> f.serializersActive.get() == 1, "serializer entry");
                Checkpoint preparing = f.checkpoint("preparation-saturated", false);
                assertThat(preparing.activeCallers()).isEqualTo(1);
                assertThat(preparing.authSubscriptions()).isZero();
                assertThat(preparing.foregroundLoadTokens()).isZero();
                call.cancel(true);
                await(() -> f.activeCallers.get() == 0, "caller cancellation");
                f.manager.close();
                Checkpoint cancelled = f.checkpoint("caller-and-manager-closed-callback-still-running", true);
                assertThat(cancelled.activeSerializers()).isEqualTo(1);
                assertThat(cancelled.activeCallers()).isZero();
                assertThat(cancelled.dispatches()).isZero();
                f.serializationGate.countDown();
                await(() -> f.serializersActive.get() == 0, "application callback exit");
                assertThat(f.checkpoint("callback-exited", true).foregroundLoadTokens()).isZero();
                assertThat(f.dispatches).hasValue(0);
            }
            finally {
                f.serializationGate.countDown();
                worker.dispose();
            }
        }
    }

    interface GetClient {
        @GET("/work/{key}")
        Mono<byte[]> get(@PathVar("key") String key);

        @GET("/work/{key}")
        @CacheDisabled
        Mono<byte[]> uncached(@PathVar("key") String key);
    }

    interface PostClient {
        @POST("/work/{key}")
        @CacheResponse(value = "work", semanticRead = true)
        Mono<byte[]> post(@PathVar("key") String key, @Body @CacheKey("body") Query body);

        @POST("/work/{key}")
        @CacheDisabled
        Mono<byte[]> uncached(@PathVar("key") String key, @Body Query body);
    }

    record Query(String text) { }

    private static final class Fixture implements AutoCloseable {
        final String id;
        final Verb verb;
        final boolean weighted;
        final boolean cached;
        final boolean singleFlight;
        final boolean refresh;
        final Limit limit;
        final AtomicLong ticker = new AtomicLong();
        final AtomicInteger activeCallers = new AtomicInteger();
        final AtomicInteger callerSubscriptions = new AtomicInteger();
        final AtomicInteger successes = new AtomicInteger();
        final AtomicInteger cancellations = new AtomicInteger();
        final List<Throwable> errors = new CopyOnWriteArrayList<>();
        final AtomicInteger authActive = new AtomicInteger();
        final AtomicInteger authSubscriptions = new AtomicInteger();
        final AtomicInteger serializersActive = new AtomicInteger();
        final AtomicInteger serializationCalls = new AtomicInteger();
        final AtomicInteger httpActive = new AtomicInteger();
        final AtomicInteger dispatches = new AtomicInteger();
        final AtomicInteger serverActive = new AtomicInteger();
        final List<CompletableFuture<byte[]>> calls = new ArrayList<>();
        final Set<Integer> keyOrdinals = ConcurrentHashMap.newKeySet();
        final List<Checkpoint> checkpoints = new ArrayList<>();
        final Map<Object, Object> generationMonitors = new IdentityHashMap<>();
        final Set<Object> knownCaches = Collections.newSetFromMap(new IdentityHashMap<>());
        volatile Sinks.One<byte[]> responses = Sinks.one();
        volatile Sinks.One<AuthContext> authGate;
        volatile CountDownLatch serializationGate;
        final PoolRecorder pool = new PoolRecorder();
        final Scheduler refreshScheduler = Schedulers.newSingle("v30-refresh");
        final LocalResponseCacheManager manager = LocalResponseCacheManager.testing(ticker::get, refreshScheduler);
        final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        final BulkheadRegistry bulkheads = BulkheadRegistry.of(BulkheadConfig.custom()
                .maxConcurrentCalls(2).maxWaitDuration(Duration.ZERO).build());
        final DisposableServer server;
        final ConnectionProvider provider;
        final Object client;
        final ResponseCacheMemoryDomains.Environment environment;

        Fixture(String scenario, Verb verb, boolean weighted, boolean cached, boolean singleFlight,
                boolean refresh, Limit limit) throws Exception {
            this.id = scenario.toLowerCase(Locale.ROOT) + "-" + verb.name().toLowerCase(Locale.ROOT)
                    + (weighted ? "-weighted" : "-count");
            this.verb = verb;
            this.weighted = weighted;
            this.cached = cached;
            this.singleFlight = singleFlight;
            this.refresh = refresh;
            this.limit = limit;
            context.refresh();
            server = HttpServer.create().host("127.0.0.1").port(0).handle((request, response) ->
                    request.receive().aggregate().asString().defaultIfEmpty("")
                            .flatMap(body -> {
                                if (request.method().name().equals("POST")) {
                                    assertThat(body).isEqualTo("{\"text\":\"synthetic\"}");
                                }
                                dispatches.incrementAndGet();
                                serverActive.incrementAndGet();
                                return responses.asMono().flatMap(bytes -> response
                                                .header(HttpHeaders.CONTENT_TYPE, "application/octet-stream")
                                                .sendByteArray(Mono.just(bytes)).then())
                                        .doFinally(signal -> serverActive.decrementAndGet());
                            })).bindNow(AWAIT);
            provider = ConnectionProvider.builder("v30-" + id)
                    .maxConnections(limit == Limit.POOL ? 2 : DEFAULT_POOL_MAXIMUM)
                    .pendingAcquireMaxCount(limit == Limit.POOL ? 3 : 2 * DEFAULT_POOL_MAXIMUM)
                    .pendingAcquireTimeout(Duration.ofMinutes(2))
                    .metrics(true, () -> pool).build();
            String baseUrl = "http://127.0.0.1:" + server.port();
            AuthProvider auth = request -> Mono.defer(() -> {
                authSubscriptions.incrementAndGet();
                authActive.incrementAndGet();
                return (authGate == null ? Mono.just(AuthContext.empty()) : authGate.asMono())
                        .doFinally(signal -> authActive.decrementAndGet());
            });
            var exchange = ExchangeFunctions.create(
                    new ReactorClientHttpConnector(HttpClient.create(provider).disableRetry(true)));
            WebClient webClient = WebClient.builder().baseUrl(baseUrl)
                    .filter(new OutboundAuthFilter("v30-work", auth))
                    .filter(ReactiveClientInvocationHandler.finalRequestObservationFilter())
                    .exchangeFunction(request -> Mono.defer(() -> {
                        httpActive.incrementAndGet();
                        return exchange.exchange(request).doFinally(signal -> httpActive.decrementAndGet());
                    })).build();
            ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();
            config.setAuthProvider("fixture-auth");
            config.setRequestTimeoutMs(0);
            config.setLogicalCallTimeoutMs(0);
            if (cached) {
                config.getCache().setPolicy("work");
            }
            var policy = new ReactiveHttpClientProperties.CachePolicyConfig();
            policy.setTtlMs(TTL_MS);
            policy.setMaximumSize((long) MAX_ENTRIES);
            policy.setMaximumTotalDecodedResponseBytes(weighted ? BYTE_LIMIT : null);
            policy.setSingleFlight(singleFlight);
            policy.setSharedResponse(true);
            policy.setVaryByParameters(verb == Verb.POST ? List.of("body") : List.of());
            if (refresh) {
                policy.setRefreshAfterMs(REFRESH_AFTER_MS);
                policy.setRefreshTimeoutMs(REFRESH_TIMEOUT_MS);
            }
            config.getCache().getPolicies().put("work", policy);
            if (limit == Limit.BULKHEAD) {
                config.getResilience().setEnabled(true);
                config.getResilience().setBulkhead("work");
            }
            Class<?> type = verb == Verb.GET ? GetClient.class : PostClient.class;
            MethodMetadataCache metadata = new MethodMetadataCache();
            metadata.validateDeclarativeCachePolicies(type, "v30-work", config);
            var handler = new ReactiveClientInvocationHandler(webClient, metadata, new RequestArgumentResolver(),
                    new DefaultErrorDecoder(), config, "v30-work", type, context,
                    new Resilience4jOperatorApplier(null, null, bulkheads, null), codec(),
                    new ReactiveHttpClientProperties.ObservabilityConfig(), manager, auth, baseUrl);
            client = Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
            environment = ResponseCacheMemoryDomains.environment(projectRoot(),
                    server.channel().getClass().getName(), server.channel().alloc().getClass().getName());
        }

        CompletableFuture<byte[]> call(int key) {
            CompletableFuture<byte[]> future = publisher(key).toFuture();
            calls.add(future);
            return future;
        }

        Mono<byte[]> publisher(int key) {
            return Mono.defer(() -> {
                keyOrdinals.add(key);
                callerSubscriptions.incrementAndGet();
                activeCallers.incrementAndGet();
                Mono<byte[]> call;
                if (verb == Verb.GET) {
                    call = cached ? ((GetClient) client).get(Integer.toString(key))
                            : ((GetClient) client).uncached(Integer.toString(key));
                }
                else {
                    call = cached ? ((PostClient) client).post(Integer.toString(key), new Query("synthetic"))
                            : ((PostClient) client).uncached(Integer.toString(key), new Query("synthetic"));
                }
                return call.doOnSuccess(value -> successes.incrementAndGet()).doOnError(errors::add)
                        .doOnCancel(cancellations::incrementAndGet)
                        .doFinally(signal -> activeCallers.decrementAndGet());
            });
        }

        ReactiveHttpClientJsonCodec codec() {
            ReactiveHttpClientJsonCodec delegate = TestJsonCodecs.jsonCodec();
            return new ReactiveHttpClientJsonCodec() {
                @Override public byte[] write(Object value) throws Exception { return delegate.write(value); }
                @Override public <T> T read(byte[] value, Class<T> type) throws Exception {
                    return delegate.read(value, type);
                }
                @Override public byte[] writeBounded(Object value, int maximumBytes) throws Exception {
                    serializationCalls.incrementAndGet();
                    serializersActive.incrementAndGet();
                    try {
                        CountDownLatch gate = serializationGate;
                        if (gate != null) {
                            // Simulate an application hook which does not honor cancellation interrupts.
                            boolean interrupted = false;
                            while (true) {
                                try {
                                    if (!gate.await(AWAIT.toMillis(), TimeUnit.MILLISECONDS)) {
                                        throw new IllegalStateException("Serializer gate was not released");
                                    }
                                    break;
                                }
                                catch (InterruptedException ignored) { interrupted = true; }
                            }
                            if (interrupted) { Thread.currentThread().interrupt(); }
                        }
                        return delegate.writeBounded(value, maximumBytes);
                    }
                    finally { serializersActive.decrementAndGet(); }
                }
            };
        }

        void releaseResponses() {
            responses.tryEmitValue(new byte[PAYLOAD_BYTES]).orThrow();
        }

        void awaitIdle() {
            await(() -> activeCallers.get() == 0 && httpActive.get() == 0 && serverActive.get() == 0
                    && manager.workloadSnapshotForTesting().inFlightLoads() == 0
                    && manager.workloadSnapshotForTesting().inFlightRefreshes() == 0
                    && tokens().foregroundLoads() == 0 && tokens().refreshes() == 0
                    && pool.snapshot(provider).activeConnections() == 0, "terminal owners");
        }

        TokenSnapshot tokens() {
            Map<?, ?> caches = field(manager, "caches", Map.class);
            synchronized (caches) { knownCaches.addAll(caches.values()); }
            int mapped = 0;
            for (Object cache : knownCaches) {
                Object monitor = field(cache, "lifecycleMonitor", Object.class);
                synchronized (monitor) {
                    Map<?, ?> generations = field(cache, "generations", Map.class);
                    mapped += generations.size();
                    generations.values().forEach(state -> generationMonitors.put(state, monitor));
                }
            }
            int loads = 0;
            int refreshes = 0;
            int owners = 0;
            for (Map.Entry<Object, Object> entry : generationMonitors.entrySet()) {
                synchronized (entry.getValue()) {
                    int activeLoads = field(entry.getKey(), "activeLoads", Integer.class);
                    int activeRefreshes = field(entry.getKey(), "activeRefreshes", Integer.class);
                    loads += activeLoads;
                    refreshes += activeRefreshes;
                    if (activeLoads + activeRefreshes > 0) { owners++; }
                }
            }
            return new TokenSnapshot(mapped, owners, loads, refreshes);
        }

        Checkpoint checkpoint(String phase, boolean trafficStopped) {
            var workload = manager.workloadSnapshotForTesting();
            var cache = workload.cache();
            TokenSnapshot tokens = tokens();
            int flights = workload.inFlightLoads();
            int waiters = workload.coalescedWaiters();
            long allocatorBytes = server.channel().alloc() instanceof io.netty.buffer.ByteBufAllocatorMetricProvider metric
                    ? metric.metric().usedDirectMemory() : ResponseCacheMemoryDomains.UNAVAILABLE;
            Checkpoint checkpoint = new Checkpoint(phase, trafficStopped, ticker.get(), callerSubscriptions.get(),
                    activeCallers.get(), authActive.get() + serializersActive.get(), serializersActive.get(),
                    authSubscriptions.get(), serializationCalls.get(), successes.get(), errors.size(), cancellations.get(),
                    tokens.foregroundLoads(), tokens.foregroundLoads() - flights, flights, flights + waiters, waiters,
                    workload.inFlightRefreshes(), tokens.refreshes(), tokens.mapped(), tokens.activeOwners(),
                    httpActive.get(), dispatches.get(), serverActive.get(), cache.currentSize(),
                    cache.retainedDecodedResponseBytes(), cache.closed(), pool.snapshot(provider),
                    ResponseCacheMemoryDomains.capture(phase, false, allocatorBytes));
            checkpoints.add(checkpoint);
            return checkpoint;
        }

        @Override public void close() throws Exception {
            if (serializationGate != null) { serializationGate.countDown(); }
            calls.forEach(call -> { if (!call.isDone()) { call.cancel(true); } });
            manager.close();
            provider.disposeLater().block(AWAIT);
            server.disposeNow(AWAIT);
            context.close();
            refreshScheduler.dispose();
            Checkpoint closed = checkpoint("post-close", true);
            assertThat(closed.activeCallers()).isZero();
            assertThat(closed.foregroundLoadTokens()).isZero();
            assertThat(closed.refreshTokens()).isZero();
            assertThat(closed.sharedFlights()).isZero();
            assertThat(closed.hiddenRefreshes()).isZero();
            assertThat(closed.entries()).isZero();
            assertThat(closed.pool().providerReportsDisposed()).isTrue();
            assertThat(closed.httpExchangesAwaitingHeaders()).isZero();
            assertThat(closed.pool().totalConnections()).isZero();
            assertThat(closed.pool().pendingAcquires()).isZero();
            Path report = projectRoot().resolve("target/release-evidence/v30/priority2/" + id + ".json");
            Files.createDirectories(report.getParent());
            Map<String, Object> evidence = new LinkedHashMap<>();
            evidence.put("format", "v30-active-work-v1");
            evidence.put("scenario", id);
            evidence.put("verb", verb.name());
            evidence.put("cached", cached);
            evidence.put("singleFlight", singleFlight);
            evidence.put("weighted", weighted);
            evidence.put("maximumEntries", MAX_ENTRIES);
            evidence.put("maximumDecodedBytes", weighted ? BYTE_LIMIT : null);
            evidence.put("payloadBytes", PAYLOAD_BYTES);
            evidence.put("keyCardinality", keyOrdinals.size());
            evidence.put("callerOperations", callerSubscriptions.get());
            evidence.put("warmupOperations", 0);
            evidence.put("ttlMs", TTL_MS);
            evidence.put("refreshAfterMs", refresh ? REFRESH_AFTER_MS : null);
            evidence.put("refreshTimeoutMs", refresh ? REFRESH_TIMEOUT_MS : null);
            evidence.put("limit", limit.name());
            evidence.put("bulkheadMaximum", limit == Limit.BULKHEAD ? 2 : null);
            evidence.put("poolMaximum", limit == Limit.POOL ? 2 : DEFAULT_POOL_MAXIMUM);
            evidence.put("poolPendingMaximum", limit == Limit.POOL ? 3 : 2 * DEFAULT_POOL_MAXIMUM);
            evidence.put("poolCapacitySelection", limit == Limit.POOL
                    ? "explicit-reactor-capacity" : "starter-default-capacity");
            evidence.put("pendingAcquireTimeoutMs", 120_000);
            evidence.put("requestTimeoutMs", 0);
            evidence.put("logicalCallTimeoutMs", 0);
            evidence.put("environment", environment);
            evidence.put("checkpoints", checkpoints);
            new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(report.toFile(), evidence);
            assertThat(Files.readString(report)).doesNotContain("127.0.0.1", "http://", "https://",
                    "Authorization", "{\\\"text\\\":", "opaqueKey");
        }
    }

    record TokenSnapshot(int mapped, int activeOwners, int foregroundLoads, int refreshes) { }
    record PoolSnapshot(String protocol, int totalConnections, int idleConnections, int activeConnections,
                        int pendingAcquires, Integer activeStreams, Integer pendingStreams,
                        boolean providerReportsDisposed) { }
    record Checkpoint(String phase, boolean trafficStopped, long tickerNanos, int callerSubscriptions,
                      int activeCallers, int activePreparationOwners, int activeSerializers, int authSubscriptions,
                      int serializationCalls, int callerSuccesses, int callerErrors, int callerCancellations,
                      int foregroundLoadTokens, int independentLoads, int sharedFlights, int attachedMembers,
                      int coalescedWaiters, int hiddenRefreshes, int refreshTokens, int generationMapOwners,
                      int activeGenerationOwners, int httpExchangesAwaitingHeaders, int dispatches,
                      int activeServerResponses, long entries, Long retainedDecodedBytes, boolean cacheClosed,
                      PoolSnapshot pool, ResponseCacheMemoryDomains.Snapshot memory) { }

    private static final class PoolRecorder implements ConnectionProvider.MeterRegistrar {
        final Map<String, ConnectionPoolMetrics> pools = new ConcurrentHashMap<>();
        @Override public void registerMetrics(String name, String id, SocketAddress address, ConnectionPoolMetrics metrics) {
            pools.put(id, metrics);
        }
        @Override public void deRegisterMetrics(String name, String id, SocketAddress address) { pools.remove(id); }
        PoolSnapshot snapshot(ConnectionProvider provider) {
            List<ConnectionPoolMetrics> current = List.copyOf(pools.values());
            return new PoolSnapshot("HTTP/1.1",
                    current.stream().mapToInt(ConnectionPoolMetrics::allocatedSize).sum(),
                    current.stream().mapToInt(ConnectionPoolMetrics::idleSize).sum(),
                    current.stream().mapToInt(ConnectionPoolMetrics::acquiredSize).sum(),
                    current.stream().mapToInt(ConnectionPoolMetrics::pendingAcquireSize).sum(),
                    null, null, provider.isDisposed());
        }
    }

    private static <T> T field(Object owner, String name, Class<T> type) {
        try {
            Field field = owner.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return type.cast(field.get(owner));
        }
        catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }

    private static List<String> causeNames(Throwable error) {
        List<String> names = new ArrayList<>();
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            names.add(cause.getClass().getSimpleName());
        }
        return names;
    }

    private static void await(BooleanSupplier condition, String description) {
        long deadline = System.nanoTime() + AWAIT.toNanos();
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            java.util.concurrent.locks.LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
        }
        assertThat(condition.getAsBoolean()).as(description).isTrue();
    }

    private static Path projectRoot() {
        Path cwd = Path.of("").toAbsolutePath().normalize();
        return Files.exists(cwd.resolve("README.md")) ? cwd : cwd.getParent();
    }
}
