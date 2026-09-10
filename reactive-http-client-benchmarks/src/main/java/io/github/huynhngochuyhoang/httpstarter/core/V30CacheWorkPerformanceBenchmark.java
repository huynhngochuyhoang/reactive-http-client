package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.annotation.GET;
import io.github.huynhngochuyhoang.httpstarter.annotation.PathVar;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import io.github.huynhngochuyhoang.httpstarter.exception.CacheWorkRejectedException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.openjdk.jmh.annotations.*;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import reactor.netty.DisposableServer;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.server.HttpServer;
import reactor.netty.resources.ConnectionProvider;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.concurrent.locks.LockSupport;

@State(Scope.Thread)
@BenchmarkMode({Mode.Throughput, Mode.AverageTime})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
public class V30CacheWorkPerformanceBenchmark {
    static final String POLICY = "work";
    static final String API = "read";
    static final String VALUE = "value";
    static final Duration WAIT = Duration.ofSeconds(5);

    @Param({"false", "true"})
    public boolean metered;
    Fixture plain;
    Fixture weighted;
    Fixture refresh;
    Fixture loopback;

    @Setup(Level.Trial)
    public void setup() {
        plain = new Fixture(metered, false, false, false);
        weighted = new Fixture(metered, true, false, false);
        refresh = new Fixture(metered, true, true, false);
        loopback = new Fixture(metered, true, false, true);
        require(VALUE.equals(plain.client.read("warm").block(WAIT)), "warm value");
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        for (Fixture fixture : new Fixture[]{plain, weighted, refresh, loopback}) {
            if (fixture != null) { fixture.close(); }
        }
    }

    @Benchmark public Mono<String> cacheV30NoNetworkPublisherCreation() {
        return plain.client.read("warm");
    }

    @Benchmark public String cacheV30NoNetworkHit() {
        long before = plain.dispatches.get();
        String value = plain.client.read("warm").block(WAIT);
        plain.verify(before, 0);
        return value;
    }

    @Benchmark public String cacheV30NoNetworkMiss() { return miss(plain); }

    @Benchmark public String cacheV30NoNetworkWeightedPublication() { return miss(weighted); }

    @Benchmark public CacheWorkRejectedException.Reason cacheV30NoNetworkCallerRejection() {
        // Two real caller subscriptions occupy the caller bound, sharing one gated source.
        long before = plain.dispatches.get();
        Sinks.One<Void> gate = plain.hold();
        Mono<String> call = plain.client.read(plain.next());
        CompletableFuture<String> leader = call.toFuture();
        CompletableFuture<String> waiter = call.toFuture();
        try {
            plain.joined();
            CacheWorkRejectedException.Reason reason = rejected(plain.client.read("warm"));
            require(reason == CacheWorkRejectedException.Reason.CALLER_CAPACITY, "caller rejection");
            require(!leader.isDone() && !waiter.isDone(), "source must remain gated");
            return reason;
        } finally {
            leader.cancel(true);
            waiter.cancel(true);
            plain.release(gate);
            plain.verify(before, 1);
        }
    }

    @Benchmark public CacheWorkRejectedException.Reason cacheV30NoNetworkLoadRejection() {
        long before = plain.dispatches.get();
        Sinks.One<Void> gate = plain.hold();
        CompletableFuture<String> leader = plain.client.read(plain.next()).toFuture();
        try {
            CacheWorkRejectedException.Reason reason = rejected(plain.client.read(plain.next()));
            require(reason == CacheWorkRejectedException.Reason.LOAD_CAPACITY, "load rejection");
            require(!leader.isDone(), "source must remain gated");
            return reason;
        } finally {
            leader.cancel(true);
            plain.release(gate);
            plain.verify(before, 1);
        }
    }

    @Benchmark public List<String> cacheV30NoNetworkSingleFlightJoin() { return join(plain); }

    @Benchmark public List<String> cacheV30LoopbackSingleFlightJoin() { return join(loopback); }

    @Benchmark public String cacheV30NoNetworkReleaseAndReuse() {
        cacheV30NoNetworkLoadRejection();
        return miss(plain);
    }

    @Benchmark public long cacheV30NoNetworkRefreshStart() { return refresh(false); }

    @Benchmark public long cacheV30NoNetworkRefreshCapacitySkip() { return refresh(true); }

    private long refresh(boolean skip) {
        refresh.manager.evictAllForTesting();
        require(VALUE.equals(refresh.client.read("a").block(WAIT)), "seed a");
        require(VALUE.equals(refresh.client.read("b").block(WAIT)), "seed b");
        refresh.ticker.addAndGet(Duration.ofMillis(2).toNanos());
        long before = refresh.dispatches.get();
        double skipped = refresh.counter(".refresh.skips", "reason", "capacity");
        Sinks.One<Void> gate = refresh.hold();
        try {
            require(VALUE.equals(refresh.client.read("a").block(WAIT)), "stale a");
            await(() -> refresh.dispatches.get() == before + 1);
            require(refresh.manager.activeRefreshesForTesting(POLICY) == 1, "one refresh slot");
            if (skip) {
                require(VALUE.equals(refresh.client.read("b").block(WAIT)), "stale b");
                require(refresh.dispatches.get() == before + 1, "skip must not dispatch");
                if (metered) {
                    require(refresh.counter(".refresh.skips", "reason", "capacity") == skipped + 1, "skip metric");
                }
            }
        } finally {
            refresh.release(gate);
        }
        refresh.verify(before, 1);
        return refresh.manager.retainedDecodedResponseBytesForTesting();
    }

    private static String miss(Fixture fixture) {
        long before = fixture.dispatches.get();
        String value = fixture.client.read(fixture.next()).block(WAIT);
        require(VALUE.equals(value), "miss value");
        fixture.verify(before, 1);
        return value;
    }

    private static List<String> join(Fixture fixture) {
        long before = fixture.dispatches.get();
        Sinks.One<Void> gate = fixture.hold();
        Mono<String> call = fixture.client.read(fixture.next());
        CompletableFuture<String> leader = call.toFuture();
        CompletableFuture<String> waiter = call.toFuture();
        try {
            fixture.joined();
            await(() -> fixture.dispatches.get() == before + 1);
            require(!leader.isDone() && !waiter.isDone(), "both subscribers attach before response");
            fixture.release(gate);
            return List.of(leader.orTimeout(5, TimeUnit.SECONDS).join(),
                    waiter.orTimeout(5, TimeUnit.SECONDS).join());
        } finally {
            leader.cancel(true);
            waiter.cancel(true);
            fixture.release(gate);
            fixture.verify(before, 1);
        }
    }

    static CacheWorkRejectedException.Reason rejected(Mono<String> call) {
        try {
            call.block(WAIT);
            throw new IllegalStateException("Expected local work rejection");
        } catch (CacheWorkRejectedException rejected) {
            return rejected.getReason();
        }
    }

    static void require(boolean condition, String description) {
        if (!condition) { throw new IllegalStateException("V30 workload mismatch: " + description); }
    }

    static void await(BooleanSupplier condition) {
        long end = System.nanoTime() + WAIT.toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() >= end) { throw new IllegalStateException("V30 workload gate timed out"); }
            LockSupport.parkNanos(10_000);
        }
    }

    interface Client {
        @GET("/v30/{id}") Mono<String> read(@PathVar("id") String id);
    }

    static final class Fixture implements AutoCloseable {
        final AtomicLong ticker = new AtomicLong();
        final AtomicLong sequence = new AtomicLong();
        final AtomicLong dispatches = new AtomicLong();
        final AtomicReference<Sinks.One<Void>> gate = new AtomicReference<>();
        final SimpleMeterRegistry registry = new SimpleMeterRegistry();
        final GenericApplicationContext context = new GenericApplicationContext();
        final Scheduler scheduler = Schedulers.newSingle("v30-benchmark-refresh");
        final LocalResponseCacheManager manager;
        final Client client;
        final boolean metered;
        DisposableServer server;
        ConnectionProvider provider;

        Fixture(boolean metered, boolean weighted, boolean refresh, boolean wire) {
            this.metered = metered;
            var config = new ReactiveHttpClientProperties.ClientConfig();
            config.setBaseUrl("http://127.0.0.1");
            config.setRequestTimeoutMs(10_000);
            config.setLogicalCallTimeoutMs(10_000);
            config.getResilience().setEnabled(false);
            config.getCache().setPolicy(POLICY);
            var policy = new ReactiveHttpClientProperties.CachePolicyConfig();
            policy.setTtlMs(Duration.ofDays(1).toMillis());
            policy.setMaximumSize(32L);
            policy.setSharedResponse(true);
            policy.setSingleFlight(true);
            if (weighted) { policy.setMaximumTotalDecodedResponseBytes(65_536L); }
            var work = new ReactiveHttpClientProperties.CacheWorkConfig();
            work.setMaximumConcurrentCallers(2L);
            work.setMaximumConcurrentLoads(1L);
            if (refresh) {
                policy.setRefreshAfterMs(1L);
                policy.setRefreshTimeoutMs(10_000L);
                work.setMaximumConcurrentRefreshes(1L);
            }
            policy.setWork(work);
            config.getCache().getPolicies().put(POLICY, policy);
            var observability = new ReactiveHttpClientProperties.ObservabilityConfig();
            observability.setEnabled(metered);
            observability.getCache().setEnabled(metered);
            context.refresh();
            var metadata = new MethodMetadataCache();
            manager = LocalResponseCacheManager.createForClient(Client.class, "benchmark-v30", metadata,
                    config, getClass().getClassLoader(), observability, registry, ticker::get, scheduler);
            WebClient.Builder web = WebClient.builder();
            if (wire) {
                server = HttpServer.create().host("127.0.0.1").port(0)
                        .handle((request, response) -> {
                            dispatches.incrementAndGet();
                            return response.header("Content-Type", "text/plain").sendString(body()).then();
                        }).bindNow();
                provider = ConnectionProvider.create("v30-benchmark", 2);
                config.setBaseUrl("http://127.0.0.1:" + server.port());
                web.clientConnector(new ReactorClientHttpConnector(HttpClient.create(provider).disableRetry(true)));
            } else {
                web.exchangeFunction(request -> {
                    dispatches.incrementAndGet();
                    return body().map(value -> ClientResponse.create(HttpStatus.OK)
                            .header("Content-Type", "text/plain").body(value).build());
                });
            }
            var handler = new ReactiveClientInvocationHandler(
                    web.baseUrl(config.getBaseUrl())
                            .filter(ReactiveClientInvocationHandler.finalRequestObservationFilter()).build(),
                    metadata, new RequestArgumentResolver(), new DefaultErrorDecoder(), config,
                    "benchmark-v30", Client.class, context, new NoopResilienceOperatorApplier(),
                    null, observability, manager);
            client = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[]{Client.class}, handler);
        }

        private Mono<String> body() {
            Sinks.One<Void> held = gate.get();
            return held == null ? Mono.just(VALUE) : held.asMono().thenReturn(VALUE);
        }

        String next() { return "miss-" + sequence.getAndIncrement(); }

        Sinks.One<Void> hold() {
            Sinks.One<Void> held = Sinks.one();
            require(gate.compareAndSet(null, held), "no retained prior gate");
            return held;
        }

        void release(Sinks.One<Void> held) {
            held.tryEmitEmpty();
            gate.compareAndSet(held, null);
        }

        void joined() {
            await(() -> manager.hasInFlightLoadWithMembersForTesting(2));
            require(manager.callerAdmission().active(POLICY) == 2, "two callers");
            require(manager.activeLoadsForTesting(POLICY) == 1, "one shared source");
        }

        void verify(long before, long expected) {
            await(this::idle);
            require(dispatches.get() - before == expected, "dispatch delta");
            var snapshot = manager.workloadSnapshotForTesting();
            require(snapshot.inFlightLoads() == 0 && snapshot.coalescedWaiters() == 0
                    && snapshot.inFlightRefreshes() == 0, "no retained source membership");
            require(snapshot.cache().currentSize() <= 32, "bounded entry occupancy");
        }

        boolean idle() {
            return manager.callerAdmission().active(POLICY) == 0
                    && manager.activeLoadsForTesting(POLICY) == 0
                    && manager.activeRefreshesForTesting(POLICY) == 0;
        }

        double counter(String suffix, String tag, String value) {
            var counter = registry.find(LocalResponseCacheMetrics.PREFIX + suffix).tag(tag, value).counter();
            return counter == null ? 0 : counter.count();
        }

        @Override public void close() {
            try {
                require(idle(), "trial ends with no occupied reservations");
                if (metered) {
                    require(registry.find(LocalResponseCacheMetrics.PREFIX + ".callers").tag("api.name", API)
                            .counter() != null, "production API meters registered");
                    require(registry.find(LocalResponseCacheMetrics.PREFIX + ".callers").tag("api.name", "unknown")
                            .counter() == null, "no convenience API meters");
                }
            } finally {
                manager.close();
                context.close();
                if (provider != null) { provider.disposeLater().block(WAIT); }
                if (server != null) { server.disposeNow(WAIT); }
                scheduler.dispose();
                require(registry.getMeters().isEmpty(), "cache meters deregistered");
                registry.close();
            }
        }
    }

}
