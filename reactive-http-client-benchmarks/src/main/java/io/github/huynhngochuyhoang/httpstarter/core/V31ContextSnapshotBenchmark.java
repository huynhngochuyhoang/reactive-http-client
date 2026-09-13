package io.github.huynhngochuyhoang.httpstarter.core;

import org.openjdk.jmh.annotations.*;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import reactor.util.context.Context;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Published 4.3 APIs only, for artifact-to-artifact comparisons. */
@State(Scope.Thread)
@BenchmarkMode({Mode.Throughput, Mode.AverageTime})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
public class V31ContextSnapshotBenchmark {
    @Param({"0", "8", "32"})
    public int headerCount;
    Context source;
    RequestContextSnapshot snapshot;
    private Scheduler worker;
    ScheduledExecutorService executor;

    @Setup(Level.Trial)
    public void setup() {
        source = context(headerCount);
        snapshot = RequestContextSnapshot.capture(source);
        executor = Executors.newSingleThreadScheduledExecutor();
        worker = Schedulers.fromExecutorService(executor);
    }

    static Context context(int count) {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        for (int index = 0; index < count; index++) {
            headers.put("x-fixture-" + index, index == 1 ? List.of("bounded-single")
                    : List.of("bounded-value-a", "bounded-value-b"));
        }
        return count == 0 ? Context.empty() : RequestContext.withInboundHeaders(Context.empty(), headers);
    }

    @Benchmark public RequestContextSnapshot contextV31SnapshotCapture() {
        return RequestContextSnapshot.capture(source);
    }

    @Benchmark public Context contextV31SnapshotRestore() {
        return snapshot.writeTo(Context.of("worker-local", "unrelated"));
    }

    @Benchmark public List<String> contextV31ExplicitHandoff() throws Exception {
        var first = new RequestContextSnapshot("caller-1", snapshot.inboundHeaders());
        var second = new RequestContextSnapshot("caller-2", snapshot.inboundHeaders());
        Sinks.One<Void> firstGate = Sinks.one();
        Sinks.One<Void> secondGate = Sinks.one();
        CountDownLatch attached = new CountDownLatch(2);
        CountDownLatch terminated = new CountDownLatch(2);
        CompletableFuture<String> a = handoff(first, firstGate, attached, terminated).toFuture();
        CompletableFuture<String> b = handoff(second, secondGate, attached, terminated).toFuture();
        try {
            require(attached.await(5, TimeUnit.SECONDS), "both handoff subscriptions attached");
            require(!a.isDone() && !b.isDone(), "both callers are gated");
            secondGate.tryEmitEmpty().orThrow();
            String secondValue = b.get(5, TimeUnit.SECONDS);
            require(!a.isDone(), "first caller remains gated after second completes");
            firstGate.tryEmitEmpty().orThrow();
            String firstValue = a.get(5, TimeUnit.SECONDS);
            require(terminated.await(5, TimeUnit.SECONDS), "terminal cleanup acknowledged");
            require("caller-1".equals(firstValue) && "caller-2".equals(secondValue), "isolated restoration");
            return List.of(firstValue, secondValue);
        } finally {
            a.cancel(true);
            b.cancel(true);
        }
    }

    private Mono<String> handoff(RequestContextSnapshot envelope, Sinks.One<Void> gate,
                                 CountDownLatch attached, CountDownLatch terminated) {
        return gate.asMono().doOnSubscribe(ignored -> attached.countDown())
                .publishOn(worker)
                .then(Mono.deferContextual(context -> {
                    require(RequestContext.inboundHeaders(context).size() == headerCount, "restored header count");
                    require(context.get("worker-local").equals("unrelated"), "unrelated target context retained");
                    return Mono.just(RequestContext.correlationId(context).orElseThrow());
                }))
                .contextWrite(envelope::writeTo)
                .contextWrite(Context.of("worker-local", "unrelated"))
                .doFinally(ignored -> terminated.countDown())
                .subscribeOn(worker);
    }

    private static void require(boolean condition, String description) {
        if (!condition) { throw new IllegalStateException(description); }
    }

    @TearDown(Level.Trial)
    public void close() throws InterruptedException {
        try {
            if (worker != null) { worker.dispose(); }
            if (executor != null) {
                executor.shutdownNow();
                require(executor.awaitTermination(5, TimeUnit.SECONDS), "worker terminated");
            }
        } finally {
            snapshot = null;
            source = null;
        }
    }
}
