package io.github.huynhngochuyhoang.httpstarter.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import reactor.util.context.Context;
import reactor.util.context.ContextView;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ExplicitAsyncHandoffContractTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @ParameterizedTest
    @EnumSource(Switch.class)
    void sameColdPublisherReadsEachSubscribersContextAcrossNestedSchedulerBoundaries(Switch boundary) throws Exception {
        try (Worker worker = new Worker()) {
            AtomicInteger subscriptions = new AtomicInteger();
            Mono<Observation> cold = Mono.deferContextual(ctx -> {
                subscriptions.incrementAndGet();
                Observation before = Observation.read(ctx);
                Mono<Integer> trigger = Mono.just(1);
                trigger = switch (boundary) {
                    case PUBLISH_ON -> trigger.publishOn(worker.scheduler);
                    case SUBSCRIBE_ON -> trigger.subscribeOn(worker.scheduler);
                    case BOTH -> trigger.subscribeOn(worker.scheduler).publishOn(worker.scheduler);
                };
                return trigger.flatMap(ignored -> Mono.deferContextual(nested -> {
                    assertThat(Thread.currentThread().getName()).isEqualTo("v31-handoff-worker");
                    assertThat(Observation.read(nested)).isEqualTo(before);
                    return Mono.just(0).flatMap(value -> read());
                }));
            });
            assertThat(subscriptions).hasValue(0);
            for (Context context : List.of(caller("first"), caller("second"), Context.empty())) {
                assertThat(cold.contextWrite(context).block(TIMEOUT)).isEqualTo(Observation.read(context));
            }
            assertThat(subscriptions).hasValue(3);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void independentExecutorSubscriptionNeedsExplicitSnapshot(boolean restore) throws Exception {
        try (Worker worker = new Worker()) {
            CompletableFuture<Observation> result = new CompletableFuture<>();
            Mono.deferContextual(ctx -> {
                RequestContextSnapshot snapshot = RequestContextSnapshot.capture(ctx);
                worker.executor.execute(() -> {
                    try {
                        Mono<Observation> independent = read();
                        result.complete((restore ? independent.contextWrite(snapshot::writeTo) : independent)
                                .block(TIMEOUT));
                    } catch (Throwable error) {
                        result.completeExceptionally(error);
                    }
                });
                return Mono.empty();
            }).contextWrite(caller("emitter")).block(TIMEOUT);

            Context expected = restore ? snapshot("emitter").writeTo(Context.empty()) : Context.empty();
            assertThat(result.get(10, TimeUnit.SECONDS)).isEqualTo(Observation.read(expected));
            assertThat(worker.executor.submit(() -> read().block(TIMEOUT)).get(10, TimeUnit.SECONDS))
                    .isEqualTo(Observation.read(Context.empty()));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void independentSinkSubscriptionKeepsWorkerContextUnlessExplicitlyRestored(boolean restore) throws Exception {
        try (Worker worker = new Worker()) {
            Sinks.One<RequestContextSnapshot> sink = Sinks.one();
            Context workerContext = caller("worker");
            CompletableFuture<Observation> result = sink.asMono().publishOn(worker.scheduler)
                    .flatMap(snapshot -> restore ? read().contextWrite(snapshot::writeTo) : read())
                    .contextWrite(workerContext).toFuture();
            try {
                Mono.deferContextual(ctx -> {
                    sink.tryEmitValue(RequestContextSnapshot.capture(ctx)).orThrow();
                    return Mono.empty();
                }).contextWrite(caller("emitter")).block(TIMEOUT);

                Context expected = restore ? snapshot("emitter").writeTo(workerContext) : workerContext;
                Observation observed = result.get(10, TimeUnit.SECONDS);
                assertThat(observed).isEqualTo(Observation.read(expected));
                // Snapshot restore does not overwrite idempotency or arbitrary worker context.
                assertThat(observed.idempotency()).isEqualTo("worker-idempotency");
                assertThat(observed.custom()).isEqualTo("worker-custom");
            } finally {
                result.cancel(true);
            }
        }
    }

    @Test
    void overlappingEnvelopesOnOneWorkerRestoreIsolatedTargetsAndCompleteOutOfOrder() throws Exception {
        int count = 12;
        try (Worker worker = new Worker()) {
            CountDownLatch attached = new CountDownLatch(count);
            List<Envelope> envelopes = new ArrayList<>();
            List<Integer> completed = Collections.synchronizedList(new ArrayList<>());
            Sinks.Many<Envelope> queue = Sinks.many().unicast().onBackpressureBuffer();
            CompletableFuture<List<Observation>> consumed = queue.asFlux().publishOn(worker.scheduler)
                    .flatMap(envelope -> Mono.deferContextual(ctx -> {
                        Observation before = Observation.read(ctx);
                        return envelope.release().asMono().publishOn(worker.scheduler).then(read())
                                .doOnNext(after -> {
                                    assertThat(Thread.currentThread().getName()).isEqualTo("v31-handoff-worker");
                                    assertThat(after).isEqualTo(before);
                                    assertThat(after).isEqualTo(Observation.read(envelope.snapshot().writeTo(Context.empty())));
                                    completed.add(envelope.ordinal());
                                    envelope.observed().complete(after);
                                }).doOnSubscribe(ignored -> attached.countDown());
                    }).contextWrite(ctx -> envelope.snapshot().writeTo(isolatedTarget(ctx))), count)
                    .take(count).collectList().contextWrite(caller("worker")).toFuture();
            try {
                for (int i = 0; i < count; i++) {
                    int ordinal = i;
                    // Emitter subscriptions overlap at the consumer's gate; no request waits for another to finish.
                    Mono.deferContextual(ctx -> {
                        var envelope = new Envelope(ordinal, RequestContextSnapshot.capture(ctx),
                                Sinks.empty(), new CompletableFuture<>());
                        envelopes.add(envelope);
                        queue.tryEmitNext(envelope).orThrow();
                        return Mono.empty();
                    }).contextWrite(i % 3 == 2 ? Context.empty() : caller("caller-" + i)).block(TIMEOUT);
                }
                assertThat(attached.await(10, TimeUnit.SECONDS)).isTrue();
                assertThat(completed).isEmpty();
                for (int i = count - 1; i >= 0; i--) {
                    envelopes.get(i).release().tryEmitEmpty().orThrow();
                    envelopes.get(i).observed().get(10, TimeUnit.SECONDS);
                }
                assertThat(consumed.get(10, TimeUnit.SECONDS)).hasSize(count);
                assertThat(completed).containsExactly(11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1, 0);
                assertThat(queue.currentSubscriberCount()).isZero();
                assertThat(read().subscribeOn(worker.scheduler).block(TIMEOUT))
                        .isEqualTo(Observation.read(Context.empty()));
            } finally {
                consumed.cancel(true);
                queue.tryEmitComplete();
                envelopes.clear();
            }
        }
    }

    @ParameterizedTest
    @CsvSource({"false,false", "false,true", "true,false", "true,true"})
    void restoreReplacesOnlyPresentFieldsAndNeverClearsMissingWorkerFields(boolean correlation, boolean headers) {
        var snapshot = new RequestContextSnapshot(correlation ? "snapshot-correlation" : null,
                headers ? Map.of("x-fixture", List.of("snapshot-header")) : Map.of());
        Context target = caller("worker");
        Context restored = snapshot.writeTo(target);
        assertThat(RequestContext.correlationId(restored))
                .contains(correlation ? "snapshot-correlation" : "worker-correlation");
        assertThat(RequestContext.inboundHeader(restored, "X-FIXTURE"))
                .contains(headers ? "snapshot-header" : "worker-header");
        assertThat(RequestContext.idempotencyKey(restored)).contains("worker-idempotency");
        assertThat(restored.<String>get("custom")).isEqualTo("worker-custom");
        assertThat(Observation.read(target)).isEqualTo(Observation.read(caller("worker")));

        Context isolated = snapshot.writeTo(isolatedTarget(target));
        assertThat(RequestContext.correlationId(isolated))
                .isEqualTo(Optional.ofNullable(snapshot.correlationId()));
        assertThat(RequestContext.inboundHeaders(isolated)).isEqualTo(snapshot.inboundHeaders());
        assertThat(RequestContext.idempotencyKey(isolated)).isEmpty();
        assertThat(isolated.hasKey("custom")).isFalse();
    }

    @Test
    void contributorsUseOrderThenKeyAndExtraStateRequiresExplicitApplicationHandling() {
        List<String> captures = new ArrayList<>();
        List<String> restores = new ArrayList<>();
        var beta = contributor("beta", 10, captures, restores);
        var alpha = contributor("alpha", 10, captures, restores);
        var first = contributor("first", -1, captures, restores);
        List<RequestContextContributor<?>> contributors = List.of(beta, alpha, first);
        Context source = caller("emitter").put("first", "one").put("alpha", "two").put("beta", "three");
        Map<String, Object> values = RequestContext.capture(source, contributors);
        assertThat(captures).containsExactly("first", "alpha", "beta");
        assertThat(new ArrayList<>(values.keySet())).containsExactly("first", "alpha", "beta");
        Context restored = RequestContext.restore(Context.empty(), values, contributors);
        assertThat(restores).containsExactly("first", "alpha", "beta");
        assertThat(RequestContext.correlationId(restored)).contains("three");

        assertThat(RequestContext.capture(source, RequestContext.defaultContributors()))
                .containsOnlyKeys(RequestContext.CORRELATION_ID_CONTEXT_KEY, RequestContext.INBOUND_HEADERS_CONTEXT_KEY);
        Context standard = RequestContextSnapshot.capture(source).writeTo(Context.empty());
        assertThat(standard.stream().map(Map.Entry::getKey).toList())
                .containsExactlyInAnyOrder(RequestContext.CORRELATION_ID_CONTEXT_KEY, RequestContext.INBOUND_HEADERS_CONTEXT_KEY);
        assertThat(RequestContext.idempotencyKey(standard)).isEmpty();
        assertThat(standard.hasKey("custom")).isFalse();
        Context explicit = RequestContext.withIdempotencyKey(standard, RequestContext.idempotencyKey(source).orElseThrow())
                .put("custom", source.<String>get("custom"));
        assertThat(Observation.read(explicit)).isEqualTo(Observation.read(caller("emitter")));
    }

    private static RequestContextContributor<String> contributor(
            String key, int order, List<String> captures, List<String> restores) {
        return new RequestContextContributor<>() {
            public String key() { return key; }
            public int order() { return order; }
            public Optional<String> capture(ContextView context) {
                captures.add(key);
                return context.getOrEmpty(key);
            }
            public Context restore(Context context, String value) {
                restores.add(key);
                return RequestContext.withCorrelationId(context, value);
            }
        };
    }

    private static Context isolatedTarget(Context context) {
        return context.delete(RequestContext.CORRELATION_ID_CONTEXT_KEY)
                .delete(RequestContext.INBOUND_HEADERS_CONTEXT_KEY)
                .delete(RequestContext.IDEMPOTENCY_KEY_CONTEXT_KEY).delete("custom");
    }

    private static Mono<Observation> read() {
        return Mono.deferContextual(ctx -> Mono.just(Observation.read(ctx)));
    }

    private static Context caller(String id) {
        return RequestContext.withIdempotencyKey(snapshot(id).writeTo(Context.empty()), id + "-idempotency")
                .put("custom", id + "-custom");
    }

    private static RequestContextSnapshot snapshot(String id) {
        return new RequestContextSnapshot(id + "-correlation", Map.of("x-fixture", List.of(id + "-header")));
    }

    private record Observation(String correlation, List<String> headers, String idempotency, String custom) {
        static Observation read(ContextView context) {
            return new Observation(RequestContext.correlationId(context).orElse(null),
                    RequestContext.inboundHeaderValues(context, "X-FiXtUrE"),
                    RequestContext.idempotencyKey(context).orElse(null), context.getOrDefault("custom", null));
        }
    }

    private record Envelope(int ordinal, RequestContextSnapshot snapshot, Sinks.Empty<Void> release,
                            CompletableFuture<Observation> observed) {
    }

    private enum Switch { PUBLISH_ON, SUBSCRIBE_ON, BOTH }

    private static final class Worker implements AutoCloseable {
        private final ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1,
                task -> new Thread(task, "v31-handoff-worker"));
        private final Scheduler scheduler = Schedulers.fromExecutorService(executor);

        @Override
        public void close() throws InterruptedException {
            scheduler.dispose();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
            assertThat(scheduler.isDisposed()).isTrue();
        }
    }
}
