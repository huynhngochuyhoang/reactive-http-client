package io.github.huynhngochuyhoang.httpstarter.nativesmoke;

import io.github.huynhngochuyhoang.httpstarter.exception.CacheWorkRejectedException;
import io.github.huynhngochuyhoang.httpstarter.exception.LogicalCallTimeoutException;
import io.micrometer.core.instrument.MeterRegistry;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

/** The same gated fixture is run as JVM/AOT smoke before its clean-commit native run. */
final class NativeCacheWorkScenario implements AutoCloseable {
    private final AtomicInteger requests = new AtomicInteger();
    private final ConcurrentMap<String, Sinks.One<String>> gates = new ConcurrentHashMap<>();
    private final CountDownLatch closingRequest = new CountDownLatch(1);
    private final CountDownLatch closingCancelled = new CountDownLatch(1);
    private volatile boolean refreshing;
    private CompletableFuture<String> closingCaller;
    private int beforeClose;
    private final DisposableServer server = HttpServer.create().host("127.0.0.1").port(0)
            .handle((request, response) -> {
                requests.incrementAndGet(); // Includes unmatched/rejected paths, not only successful handlers.
                return request.receive().aggregate().asString().defaultIfEmpty("").flatMap(body -> {
                    String key = request.method().name() + request.uri() + body;
                    boolean gated = key.contains("busy") || key.contains("deadline") || key.contains("closing")
                            || refreshing && key.contains("refresh");
                    Mono<String> source = gated ? gate(key).asMono() : Mono.just("value");
                    if (key.contains("closing")) {
                        source = source.doOnSubscribe(ignored -> closingRequest.countDown())
                                .doOnCancel(closingCancelled::countDown);
                    }
                    return response.header("Content-Type", "text/plain").sendString(source).then();
                });
            }).bindNow(Duration.ofSeconds(5));

    int port() { return server.port(); }
    private Sinks.One<String> gate(String key) { return gates.computeIfAbsent(key, ignored -> Sinks.one()); }

    void run(NativeWorkClient client, MeterRegistry registry) throws Exception {
        for (boolean post : new boolean[]{false, true}) {
            String policy = post ? "weighted" : "count";
            int before = requests.get();
            double joins = coalesced(registry);
            var leader = call(client, post, "busy").toFuture();
            await(() -> requests.get() == before + 1, "leader dispatch");
            reject(call(client, post, "other"), CacheWorkRejectedException.Reason.LOAD_CAPACITY);
            var waiter = call(client, post, "busy").toFuture();
            await(() -> coalesced(registry) == joins + 1, "waiter attachment");
            reject(call(client, post, "rejected"), CacheWorkRejectedException.Reason.CALLER_CAPACITY);
            Thread.sleep(200);
            require(requests.get() == before + 1, "local rejection dispatched");
            require(active(registry, policy, "callers") == 2 && active(registry, policy, "loads") == 1,
                    "single-flight capacity");
            gate(post ? "POST/searchbusy" : "GET/work/busy").tryEmitValue("loaded").orThrow();
            require("loaded".equals(leader.get(5, TimeUnit.SECONDS))
                    && "loaded".equals(waiter.get(5, TimeUnit.SECONDS)), "shared result");
            await(() -> active(registry, policy, "callers") == 0 && active(registry, policy, "loads") == 0,
                    "released capacity");
            require("loaded".equals(call(client, post, "busy").block()), "cached hit");
            require("value".equals(call(client, post, "next").block()), "slot reuse");
            require(requests.get() == before + 2, "hit dispatch count");
        }
        client.refresh("a").block();
        client.refresh("b").block();
        refreshing = true;
        Thread.sleep(120);
        require("value".equals(client.refresh("a").block()), "stale result");
        await(() -> active(registry, "refresh", "refreshes") == 1, "refresh reservation");
        require("value".equals(client.refresh("b").block()), "skipped refresh stale result");
        require(registry.get("reactive.http.client.cache.refresh.skips")
                .tags("client.name", "native-work", "cache.policy", "refresh", "reason", "capacity")
                .counter().count() == 1, "refresh skip count");
        gate("GET/refresh/a").tryEmitValue("new").orThrow();
        await(() -> active(registry, "refresh", "refreshes") == 0, "refresh release");

        long started = System.nanoTime();
        var early = client.get("deadline").toFuture();
        await(() -> gates.containsKey("GET/work/deadline"), "deadline dispatch");
        while (System.nanoTime() - started < Duration.ofSeconds(8).toNanos()) { Thread.sleep(10); }
        require(!early.isDone(), "leader expired before deadline fixture attached waiter");
        double joins = coalesced(registry);
        var later = client.get("deadline").toFuture();
        await(() -> coalesced(registry) == joins + 1, "deadline waiter attachment");
        try { early.get(5, TimeUnit.SECONDS); throw new IllegalStateException("leader did not time out"); }
        catch (ExecutionException expected) {
            require(expected.getCause() instanceof LogicalCallTimeoutException, "logical deadline error");
        }
        require(!later.isDone() && active(registry, "count", "loads") == 1, "leader deadline cancelled waiter source");
        gate("GET/work/deadline").tryEmitValue("later").orThrow();
        require("later".equals(later.get(5, TimeUnit.SECONDS)), "independent waiter deadline");
        System.out.println("V30 cache work: GET/POST capacity, refresh skip, slot reuse, independent deadlines passed");
    }

    void prepareClose(NativeWorkClient client) throws Exception {
        closingCaller = client.get("closing").toFuture();
        require(closingRequest.await(5, TimeUnit.SECONDS), "shutdown request attached");
        require(!closingCaller.isDone(), "shutdown caller terminated before close");
        beforeClose = requests.get();
    }

    void verifyClosed() throws Exception {
        require(closingCaller.isDone(), "shutdown caller survived");
        try { closingCaller.get(); throw new IllegalStateException("shutdown caller succeeded"); }
        catch (ExecutionException expected) {
            require(!(expected.getCause() instanceof LogicalCallTimeoutException),
                    "normal caller deadline satisfied shutdown assertion");
        }
        require(closingCancelled.await(3, TimeUnit.SECONDS), "shutdown source survived");
        Thread.sleep(200);
        require(requests.get() == beforeClose, "late post-close dispatch");
        System.out.println("V30 cache work: factory shutdown and no late dispatch passed");
    }
    private static Mono<String> call(NativeWorkClient client, boolean post, String key) {
        return post ? client.search(key) : client.get(key);
    }
    private static void reject(Mono<?> call, CacheWorkRejectedException.Reason reason) {
        try { call.block(Duration.ofSeconds(5)); throw new IllegalStateException("missing rejection"); }
        catch (CacheWorkRejectedException expected) { require(expected.getReason() == reason, "rejection reason"); }
    }
    private static double active(MeterRegistry registry, String policy, String dimension) {
        return registry.get("reactive.http.client.cache.work.active." + dimension)
                .tags("client.name", "native-work", "cache.policy", policy).gauge().value();
    }
    private static double coalesced(MeterRegistry registry) {
        return registry.find("reactive.http.client.cache.coalesced").tag("client.name", "native-work")
                .counters().stream().mapToDouble(c -> c.count()).sum();
    }
    private static void await(BooleanSupplier test, String message) throws Exception {
        long until = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (!test.getAsBoolean() && System.nanoTime() < until) { Thread.sleep(1); }
        require(test.getAsBoolean(), message);
    }
    private static void require(boolean value, String message) {
        if (!value) { throw new IllegalStateException("V30 native cache work: " + message); }
    }
    @Override public void close() { server.disposeNow(Duration.ofSeconds(5)); }
}
