package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.annotation.GET;
import io.github.huynhngochuyhoang.httpstarter.annotation.HeaderParam;
import io.github.huynhngochuyhoang.httpstarter.annotation.IdempotencyKey;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import io.github.huynhngochuyhoang.httpstarter.filter.CorrelationIdWebFilter;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientObserver;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientObserverEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import reactor.test.scheduler.VirtualTimeScheduler;
import reactor.util.context.Context;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AsyncHandoffOwnershipContractTest {

    @ParameterizedTest
    @EnumSource(Terminal.class)
    void terminalHandoffAcknowledgesCleanupBeforeApplicationOwnersAreCleared(Terminal terminal) throws Exception {
        ReferenceQueue<Object> queue = new ReferenceQueue<>();
        try (Fixture fixture = new Fixture()) {
            Ownership ownership = exerciseTerminal(fixture, terminal, queue);
            assertThat(ownership.envelopes()).hasSize(1);
            assertThat(ownership.snapshot().get()).isSameAs(ownership.envelopes().getFirst());
            assertThat(ownership.header().get()).isNotNull();
            ownership.envelopes().clear();
            assertThat(ownership.envelopes()).isEmpty();
            // A user logger deliberately retains its terminal record, not the snapshot wrapper or live context.
            assertThat(ownership.header().get()).isSameAs(
                    fixture.records.logs.getFirst().inboundHeaders().get("x-fixture").getFirst());
            fixture.records.clear();
            assertThat(fixture.records.starts).isEmpty();
            assertThat(fixture.records.terminals).isEmpty();
            assertThat(fixture.records.observers).isEmpty();
            assertThat(fixture.records.logs).isEmpty();
            assertThat(fixture.context.isActive()).isTrue();
            assertThat(fixture.executor.isShutdown()).isFalse();
            Reference.reachabilityFence(fixture.client);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void explicitOutboundHeadersWinOverRestoredCorrelationAndExplicitlyHandedOffIdempotency(boolean explicit) throws Exception {
        try (Fixture fixture = new Fixture()) {
            Context source = RequestContext.withIdempotencyKey(Context.empty(), "emitter-idempotency");
            source = RequestContext.withCorrelationId(source, "emitter-correlation");
            RequestContextSnapshot snapshot = RequestContextSnapshot.capture(source);
            String idempotency = RequestContext.idempotencyKey(source).orElseThrow();
            var result = fixture.client.read(explicit ? "outbound-correlation" : null, explicit ? "outbound-idempotency" : null)
                    .contextWrite(ctx -> snapshot.writeTo(RequestContext.withIdempotencyKey(ctx, idempotency)))
                    .subscribeOn(fixture.scheduler).toFuture();
            try {
                fixture.awaitAttachment();
                fixture.response.tryEmitValue(ok()).orThrow();
                assertThat(result.get(10, TimeUnit.SECONDS)).isEqualTo("ok");
                fixture.awaitWorker();
                assertThat(fixture.outbound).containsExactly(new Outbound(
                        explicit ? "outbound-correlation" : "emitter-correlation",
                        explicit ? "outbound-idempotency" : "emitter-idempotency"));
                assertThat(fixture.records.terminals).hasSize(1);
                assertThat(fixture.records.terminals.getFirst().headers())
                        .containsEntry("Idempotency-Key", explicit ? "outbound-idempotency" : "emitter-idempotency");
            } finally {
                result.cancel(true);
            }
        }
    }

    @Test
    void retainedSnapshotCopiesHeadersAndRestoresOnlySupportedFields() {
        ReferenceQueue<Object> queue = new ReferenceQueue<>();
        CopyOwnership ownership = captureMutableSource(queue);
        RequestContextSnapshot snapshot = ownership.envelopes().getFirst();
        assertThat(snapshot.inboundHeaders()).containsExactlyEntriesOf(Map.of("x-fixture", List.of("before")));
        assertThat(RequestContext.inboundHeader(snapshot.writeTo(Context.empty()), "X-Fixture")).contains("before");
        assertThatThrownBy(() -> snapshot.inboundHeaders().put("x-extra", List.of("changed")))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> snapshot.inboundHeaders().get("x-fixture").add("changed"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(RequestContext.idempotencyKey(snapshot.writeTo(Context.empty()))).isEmpty();
        assertThat(snapshot.writeTo(Context.empty()).stream().map(Map.Entry::getKey).toList())
                .containsExactlyInAnyOrder(RequestContext.CORRELATION_ID_CONTEXT_KEY, RequestContext.INBOUND_HEADERS_CONTEXT_KEY);
        Reference.reachabilityFence(ownership.envelopes());
        ownership.envelopes().clear();
    }

    // Called only by the opt-in, controlled-JVM reachability lane, never by normal @Test methods.
    static void probeTerminalReachability(Terminal terminal) throws Exception {
        ReferenceQueue<Object> queue = new ReferenceQueue<>();
        try (Fixture fixture = new Fixture()) {
            Ownership ownership = exerciseTerminal(fixture, terminal, queue);
            ownership.envelopes().clear();
            AsyncHandoffReachabilityIT.awaitCollected(queue, List.of(ownership.snapshot()));
            assertThat(ownership.header().get()).isSameAs(
                    fixture.records.logs.getFirst().inboundHeaders().get("x-fixture").getFirst());
            fixture.records.clear();
            AsyncHandoffReachabilityIT.awaitCollected(queue, List.of(ownership.header()));
            assertThat(fixture.context.isActive()).isTrue();
            assertThat(fixture.executor.isShutdown()).isFalse();
            Reference.reachabilityFence(fixture.client);
        }
    }

    static void probeSourceReachability() throws InterruptedException {
        ReferenceQueue<Object> queue = new ReferenceQueue<>();
        CopyOwnership ownership = captureMutableSource(queue);
        AsyncHandoffReachabilityIT.awaitCollected(queue, ownership.sourceObjects());
        assertThat(ownership.envelopes().getFirst().inboundHeaders())
                .containsExactlyEntriesOf(Map.of("x-fixture", List.of("before")));
        Reference.reachabilityFence(ownership.envelopes());
        ownership.envelopes().clear();
    }

    private static Ownership exerciseTerminal(Fixture fixture, Terminal terminal, ReferenceQueue<Object> queue) throws Exception {
        String header = new String("bounded-handoff-marker");
        RequestContextSnapshot snapshot = new RequestContextSnapshot("handoff-correlation", Map.of("x-fixture", List.of(header)));
        List<RequestContextSnapshot> envelopes = new ArrayList<>(List.of(snapshot));
        var ownership = new Ownership(envelopes, new WeakReference<>(snapshot, queue), new WeakReference<>(header, queue));
        CompletableFuture<SignalType> callerReleased = new CompletableFuture<>();
        AtomicInteger callerTerminals = new AtomicInteger();
        VirtualTimeScheduler clock = VirtualTimeScheduler.create();
        Mono<String> handedOff = fixture.client.read(null, null).contextWrite(snapshot::writeTo)
                .subscribeOn(fixture.scheduler);
        // This is an application handoff deadline: its timeout cancels the composed starter call.
        if (terminal == Terminal.TIMEOUT) {
            handedOff = handedOff.timeout(Duration.ofSeconds(1), clock);
        }
        CompletableFuture<String> result = handedOff.doFinally(signal -> {
            callerTerminals.incrementAndGet();
            callerReleased.complete(signal);
        }).toFuture();
        try {
            fixture.awaitAttachment();
            assertThat(fixture.records.starts).hasSize(1);
            assertThat(fixture.records.terminals).isEmpty();
            assertThat(fixture.records.observers).isEmpty();
            assertThat(fixture.records.logs).isEmpty();
            switch (terminal) {
                case COMPLETE -> {
                    fixture.response.tryEmitValue(ok()).orThrow();
                    assertThat(result.get(10, TimeUnit.SECONDS)).isEqualTo("ok");
                }
                case ERROR -> {
                    fixture.response.tryEmitError(new IllegalStateException("synthetic-handoff-failure")).orThrow();
                    assertThatThrownBy(() -> result.get(10, TimeUnit.SECONDS))
                            .hasRootCauseInstanceOf(IllegalStateException.class);
                }
                case TIMEOUT -> {
                    clock.advanceTimeBy(Duration.ofSeconds(1));
                    assertThatThrownBy(() -> result.get(10, TimeUnit.SECONDS)).hasCauseInstanceOf(TimeoutException.class);
                }
                case CANCEL -> assertThat(result.cancel(true)).isTrue();
            }
            SignalType callerSignal = callerReleased.get(10, TimeUnit.SECONDS);
            SignalType sourceSignal = fixture.sourceReleased.get(10, TimeUnit.SECONDS);
            fixture.awaitWorker();
            assertThat(callerTerminals).hasValue(1);
            assertThat(fixture.sourceTerminals).hasValue(1);
            assertThat(fixture.response.currentSubscriberCount()).isZero();
            assertThat(fixture.outbound).hasSize(1);
            assertThat(callerSignal).isEqualTo(switch (terminal) {
                case COMPLETE -> SignalType.ON_COMPLETE;
                case ERROR, TIMEOUT -> SignalType.ON_ERROR;
                case CANCEL -> SignalType.CANCEL;
            });
            assertThat(fixture.responseValues).hasValue(terminal == Terminal.COMPLETE ? 1 : 0);
            if (terminal == Terminal.COMPLETE) {
                // WebClient may cancel the envelope publisher after consuming its one ClientResponse.
                assertThat(sourceSignal).isIn(SignalType.ON_COMPLETE, SignalType.CANCEL);
            } else {
                assertThat(sourceSignal).isEqualTo(terminal == Terminal.ERROR ? SignalType.ON_ERROR : SignalType.CANCEL);
            }
            fixture.records.assertTerminal(terminal);
            return ownership;
        } finally {
            result.cancel(true);
            clock.dispose();
        }
    }

    private static CopyOwnership captureMutableSource(ReferenceQueue<Object> queue) {
        var request = MockServerHttpRequest.get("/").header("x-fixture", "before").build();
        var exchange = MockServerWebExchange.from(request);
        Map<String, List<String>> headers = new LinkedHashMap<>();
        List<String> values = new ArrayList<>(List.of("before"));
        headers.put("x-fixture", values);
        Map<String, Object> arbitrary = new LinkedHashMap<>(Map.of("exchange", exchange, "request", request));
        Context source = Context.of(RequestContext.INBOUND_HEADERS_CONTEXT_KEY, headers,
                RequestContext.CORRELATION_ID_CONTEXT_KEY, "source-correlation",
                RequestContext.IDEMPOTENCY_KEY_CONTEXT_KEY, "not-captured", "arbitrary", arbitrary);
        RequestContextSnapshot snapshot = RequestContextSnapshot.capture(source);
        List<WeakReference<Object>> references = new ArrayList<>();
        for (Object object : List.of(request, exchange, headers, values, arbitrary, source)) {
            references.add(new WeakReference<>(object, queue));
        }
        values.set(0, "after");
        headers.put("x-new", List.of("after"));
        return new CopyOwnership(new ArrayList<>(List.of(snapshot)), references);
    }

    private static ClientResponse ok() {
        return ClientResponse.create(HttpStatus.OK).header(HttpHeaders.CONTENT_TYPE, "text/plain").body("ok").build();
    }

    enum Terminal { COMPLETE, ERROR, TIMEOUT, CANCEL }

    private record Ownership(List<RequestContextSnapshot> envelopes, WeakReference<Object> snapshot,
                             WeakReference<Object> header) {
    }

    private record CopyOwnership(List<RequestContextSnapshot> envelopes, List<WeakReference<Object>> sourceObjects) {
    }

    private record Outbound(String correlation, String idempotency) {
    }

    interface HandoffClient {
        @GET("/fixture")
        @IdempotencyKey
        Mono<String> read(@HeaderParam("X-Correlation-Id") String correlation,
                          @HeaderParam("Idempotency-Key") String idempotency);
    }

    private static final class Records extends DefaultHttpExchangeLogger implements HttpClientObserver, ReactiveHttpClientLifecycleHook {
        private final List<ReactiveHttpClientLifecycleContext> starts = new CopyOnWriteArrayList<>();
        private final List<ReactiveHttpClientLifecycleContext> terminals = new CopyOnWriteArrayList<>();
        private final List<String> kinds = new CopyOnWriteArrayList<>();
        private final List<HttpClientObserverEvent> observers = new CopyOnWriteArrayList<>();
        private final List<HttpExchangeLogContext> logs = new CopyOnWriteArrayList<>();

        public void onStart(ReactiveHttpClientLifecycleContext context) { starts.add(context); }
        public void onSuccess(ReactiveHttpClientLifecycleContext context) { kinds.add("success"); terminals.add(context); }
        public void onError(ReactiveHttpClientLifecycleContext context) { kinds.add("error"); terminals.add(context); }
        public void onCancel(ReactiveHttpClientLifecycleContext context) { kinds.add("cancel"); terminals.add(context); }
        public void record(HttpClientObserverEvent event) { observers.add(event); }
        public void log(HttpExchangeLogContext context) { logs.add(context); }

        private void assertTerminal(Terminal terminal) {
            assertThat(starts).hasSize(1);
            assertThat(terminals).hasSize(1);
            assertThat(observers).hasSize(1);
            assertThat(logs).hasSize(1);
            assertThat(kinds).containsExactly(switch (terminal) {
                case COMPLETE -> "success";
                case ERROR -> "error";
                case TIMEOUT, CANCEL -> "cancel";
            });
            var hook = terminals.getFirst();
            var observer = observers.getFirst();
            var log = logs.getFirst();
            assertThat(hook.attemptNumber()).isEqualTo(1);
            assertThat(observer.getAttemptCount()).isEqualTo(1);
            assertThat(log.subscriptionAttemptCount()).isEqualTo(1);
            assertThat(log.inboundHeaders()).containsOnlyKeys("x-fixture");
            assertThat(log.inboundHeaders().get("x-fixture")).containsExactly("bounded-handoff-marker");
            assertThat(log.requestHeaders()).containsEntry("X-Correlation-Id", "handoff-correlation");
            assertThat(observer.getRequestHeaders()).containsEntry("X-Correlation-Id", "handoff-correlation");
            assertThat(observer.getError()).isSameAs(hook.error()).isSameAs(log.error());
            if (terminal == Terminal.COMPLETE) {
                assertThat(log.error()).isNull();
                assertThat(log.responseStatus()).isEqualTo(200);
            } else {
                assertThat(log.responseStatus()).isNull();
                if (terminal == Terminal.ERROR) {
                    assertThat(log.error()).isInstanceOf(IllegalStateException.class).hasMessage("synthetic-handoff-failure");
                } else {
                    assertThat(log.error()).isInstanceOf(CancellationException.class);
                }
            }
        }

        private void clear() {
            starts.clear();
            terminals.clear();
            kinds.clear();
            observers.clear();
            logs.clear();
        }
    }

    private static final class Fixture implements AutoCloseable {
        private final GenericApplicationContext context = new GenericApplicationContext();
        private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        private final Scheduler scheduler = Schedulers.fromExecutorService(executor);
        private final Records records = new Records();
        private final Sinks.One<ClientResponse> response = Sinks.one();
        private final CompletableFuture<Void> attached = new CompletableFuture<>();
        private final CompletableFuture<SignalType> sourceReleased = new CompletableFuture<>();
        private final AtomicInteger sourceTerminals = new AtomicInteger();
        private final AtomicInteger responseValues = new AtomicInteger();
        private final List<Outbound> outbound = new CopyOnWriteArrayList<>();
        private final LocalResponseCacheManager cacheManager = LocalResponseCacheManager.lazy(getClass().getClassLoader());
        private final HandoffClient client;

        private Fixture() {
            context.registerBean("handoff-records", Records.class, () -> records);
            context.refresh();
            var correlation = new ReactiveHttpClientProperties.CorrelationIdConfig();
            correlation.setMdcKeys(List.of());
            WebClient webClient = WebClient.builder().baseUrl("http://handoff.example.invalid")
                    .filter(CorrelationIdWebFilter.exchangeFilter(correlation))
                    .filter(ReactiveClientInvocationHandler.finalRequestObservationFilter())
                    .exchangeFunction(request -> {
                        outbound.add(new Outbound(request.headers().getFirst("X-Correlation-Id"),
                                request.headers().getFirst("Idempotency-Key")));
                        return response.asMono().doOnSubscribe(ignored -> attached.complete(null))
                                .doOnNext(ignored -> responseValues.incrementAndGet())
                                .doFinally(signal -> {
                                    sourceTerminals.incrementAndGet();
                                    sourceReleased.complete(signal);
                                });
                    }).build();
            var config = new ReactiveHttpClientProperties.ClientConfig();
            config.setExchangeLoggingEnabled(true);
            var handler = new ReactiveClientInvocationHandler(webClient, new MethodMetadataCache(),
                    new RequestArgumentResolver(), new DefaultErrorDecoder(), config, "handoff-fixture", HandoffClient.class,
                    context, new NoopResilienceOperatorApplier(), TestJsonCodecs.jsonCodec(),
                    new ReactiveHttpClientProperties.ObservabilityConfig(), cacheManager);
            client = (HandoffClient) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{HandoffClient.class}, handler);
        }

        private void awaitAttachment() throws Exception {
            attached.get(10, TimeUnit.SECONDS);
            awaitWorker();
            assertThat(response.currentSubscriberCount()).isEqualTo(1);
        }

        private void awaitWorker() throws Exception {
            executor.submit(() -> { }).get(10, TimeUnit.SECONDS);
        }

        public void close() throws InterruptedException {
            records.clear();
            cacheManager.close();
            context.close();
            scheduler.dispose();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
            assertThat(context.isActive()).isFalse();
        }
    }
}
