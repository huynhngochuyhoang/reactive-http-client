package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.annotation.*;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import io.github.huynhngochuyhoang.httpstarter.exception.LogicalCallTimeoutException;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientCacheOutcome;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientObserverEvent;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.reactive.ClientHttpRequest;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;
import reactor.test.scheduler.VirtualTimeScheduler;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SemanticReadSingleFlightRefreshContractTest {

    @Test
    void samePostIdentitySharesOneDispatchAndOneBodySubscription() throws Exception {
        AtomicInteger dispatches = new AtomicInteger();
        AtomicInteger bodySubscriptions = new AtomicInteger();
        CountDownLatch bodyReceived = new CountDownLatch(1);
        Sinks.One<String> response = Sinks.one();
        DisposableServer server = HttpServer.create()
                .port(0)
                .handle((request, reply) -> request.receive().aggregate().asString()
                        .flatMap(body -> {
                            dispatches.incrementAndGet();
                            assertThat(body).isEqualTo("same-body");
                            bodyReceived.countDown();
                            return reply.header(HttpHeaders.CONTENT_TYPE, "text/plain")
                                    .sendString(response.asMono())
                                    .then();
                        }))
                .bindNow();
        try (AnnotationConfigApplicationContext context = context()) {
            WebClient webClient = WebClient.builder()
                    .baseUrl("http://127.0.0.1:" + server.port())
                    .clientConnector(new ReactorClientHttpConnector())
                    .filter((request, next) -> next.exchange(withCountedBody(request, bodySubscriptions)))
                    .build();
            LocalResponseCacheManager manager = LocalResponseCacheManager.testing(System::nanoTime);
            SemanticPostClient client = client(
                    SemanticPostClient.class, "semantic-flight-client", webClient,
                    config(0, true, null, null), context, manager, TestJsonCodecs.jsonCodec());

            Mono<String> call = post(client, "same", "same-body", "text/plain", "tenant-a", "first", "en");
            CompletableFuture<String> leader = call.toFuture();
            assertThat(bodyReceived.await(1, TimeUnit.SECONDS)).isTrue();
            CompletableFuture<String> waiter = call.toFuture();

            assertThat(dispatches).hasValue(1);
            assertThat(bodySubscriptions).hasValue(1);
            response.tryEmitValue("shared").orThrow();
            assertThat(leader.get(1, TimeUnit.SECONDS)).isEqualTo("shared");
            assertThat(waiter.get(1, TimeUnit.SECONDS)).isEqualTo("shared");
            assertThat(post(client, "same", "same-body", "text/plain", "tenant-a", "later", "en").block())
                    .isEqualTo("shared");
            assertThat(dispatches).hasValue(1);
            assertThat(bodySubscriptions).hasValue(1);
            manager.close();
        }
        finally {
            server.disposeNow();
        }
    }

    @Test
    void completeOpaqueIdentityKeepsPostFlightsIndependent() {
        AtomicInteger dispatches = new AtomicInteger();
        WebClient webClient = WebClient.builder()
                .baseUrl("http://semantic-flight.test")
                .exchangeFunction(request -> {
                    dispatches.incrementAndGet();
                    return Mono.never();
                })
                .build();

        try (AnnotationConfigApplicationContext context = context()) {
            LocalResponseCacheManager manager = LocalResponseCacheManager.testing(System::nanoTime);
            ReactiveHttpClientProperties.ClientConfig config = config(0, true, null, null);
            SemanticPostClient firstClient = client(
                    SemanticPostClient.class, "semantic-flight-client", webClient,
                    config, context, manager, TestJsonCodecs.jsonCodec());
            OtherSemanticPostClient secondClient = client(
                    OtherSemanticPostClient.class, "other-semantic-flight-client", webClient,
                    config, context, manager, TestJsonCodecs.jsonCodec());
            List<Disposable> calls = new ArrayList<>();

            calls.add(post(firstClient, "base", "body-a", "text/plain", "tenant-a", "one", "en").subscribe());
            calls.add(post(firstClient, "base", "body-a", "text/plain", "tenant-a", "two", "en").subscribe());
            calls.add(post(firstClient, "base", "body-b", "text/plain", "tenant-a", "one", "en").subscribe());
            calls.add(post(firstClient, "base", "body-a", "application/json", "tenant-a", "one", "en").subscribe());
            calls.add(post(firstClient, "base", "body-a", "text/plain", "tenant-b", "one", "en").subscribe());
            calls.add(post(firstClient, "base", "body-a", "text/plain", "tenant-a", "one", "fr").subscribe());
            calls.add(post(firstClient, "other", "body-a", "text/plain", "tenant-a", "one", "en").subscribe());
            calls.add(contextual(firstClient.alternate(
                    "base", "body-a", "text/plain", "tenant-a", "one"), "en").subscribe());
            calls.add(contextual(firstClient.otherPolicy(
                    "base", "body-a", "text/plain", "tenant-a", "one"), "en").subscribe());
            calls.add(contextual(secondClient.search(
                    "base", "body-a", "text/plain", "tenant-a", "one"), "en").subscribe());

            awaitValue(dispatches, 9);
            assertThat(dispatches).hasValue(9);
            calls.forEach(Disposable::dispose);
            manager.close();
        }
    }

    @Test
    void postCallersKeepIndependentDeadlinesAndTerminalEvidence() throws Exception {
        VirtualTimeScheduler scheduler = VirtualTimeScheduler.getOrSet();
        Map<String, Sinks.One<ClientResponse>> responses = new ConcurrentHashMap<>();
        Map<String, AtomicInteger> dispatches = new ConcurrentHashMap<>();
        WebClient webClient = WebClient.builder()
                .baseUrl("http://semantic-flight.test")
                .filter(ReactiveClientInvocationHandler.finalRequestObservationFilter())
                .exchangeFunction(request -> {
                    String path = request.url().getPath();
                    dispatches.computeIfAbsent(path, ignored -> new AtomicInteger()).incrementAndGet();
                    return responses.get(path).asMono();
                })
                .build();
        List<HttpClientObserverEvent> observed = new CopyOnWriteArrayList<>();
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ReactiveHttpClientProperties.ObservabilityConfig observability =
                new ReactiveHttpClientProperties.ObservabilityConfig();
        observability.getCache().setEnabled(true);

        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getBeanFactory().registerSingleton(
                    "semanticObserver",
                    (io.github.huynhngochuyhoang.httpstarter.observability.HttpClientObserver) observed::add);
            context.refresh();
            LocalResponseCacheManager manager = LocalResponseCacheManager.testing(
                    System::nanoTime,
                    reactor.core.scheduler.Schedulers.parallel(),
                    LocalResponseCacheMetrics.enabled(registry, "semantic-flight-client"),
                    "semantic-flight-client");
            SemanticPostClient shortBudget = client(
                    SemanticPostClient.class, "semantic-flight-client", webClient,
                    config(50, true, null, null), context, manager, TestJsonCodecs.jsonCodec(), observability);
            SemanticPostClient longBudget = client(
                    SemanticPostClient.class, "semantic-flight-client", webClient,
                    config(200, true, null, null), context, manager, TestJsonCodecs.jsonCodec(), observability);

            responses.put("/search/waiter-timeout", Sinks.one());
            CompletableFuture<String> leader = post(
                    longBudget, "waiter-timeout", "body", "text/plain", "tenant", "one", "en").toFuture();
            CompletableFuture<String> waiter = post(
                    shortBudget, "waiter-timeout", "body", "text/plain", "tenant", "two", "en").toFuture();
            scheduler.advanceTimeBy(Duration.ofMillis(50));
            assertLogicalTimeout(waiter);
            assertThat(leader).isNotDone();
            responses.get("/search/waiter-timeout").tryEmitValue(ok("leader-success")).orThrow();
            assertThat(leader.get(1, TimeUnit.SECONDS)).isEqualTo("leader-success");

            responses.put("/search/leader-timeout", Sinks.one());
            CompletableFuture<String> firstCaller = post(
                    shortBudget, "leader-timeout", "body", "text/plain", "tenant", "one", "en").toFuture();
            CompletableFuture<String> laterWaiter = post(
                    longBudget, "leader-timeout", "body", "text/plain", "tenant", "two", "en").toFuture();
            scheduler.advanceTimeBy(Duration.ofMillis(50));
            assertLogicalTimeout(firstCaller);
            assertThat(laterWaiter).isNotDone();
            responses.get("/search/leader-timeout").tryEmitValue(ok("waiter-success")).orThrow();
            assertThat(laterWaiter.get(1, TimeUnit.SECONDS)).isEqualTo("waiter-success");

            assertThat(dispatches.get("/search/waiter-timeout")).hasValue(1);
            assertThat(dispatches.get("/search/leader-timeout")).hasValue(1);
            assertThat(observed).hasSize(4);
            assertThat(observed).extracting(HttpClientObserverEvent::getHttpMethod).containsOnly("POST");
            observed.stream()
                    .filter(event -> event.getCacheOutcome() == HttpClientCacheOutcome.COALESCED_WAITER)
                    .forEach(SemanticReadSingleFlightRefreshContractTest::assertNoTransportEvidence);
            assertThat(observed.stream()
                    .filter(event -> event.getCacheOutcome() == HttpClientCacheOutcome.COALESCED_WAITER)
                    .count()).isEqualTo(2);
            manager.close();
        }
        finally {
            registry.close();
            VirtualTimeScheduler.reset();
        }
    }

    @Test
    void cancelledFailedEmptyAndSerializationFailedPostFlightsCanBeLoadedAgain() throws Exception {
        Map<String, AtomicInteger> dispatches = new ConcurrentHashMap<>();
        AtomicBoolean cancellationCanComplete = new AtomicBoolean();
        AtomicInteger sourceCancellations = new AtomicInteger();
        Sinks.One<ClientResponse> error = Sinks.one();
        Sinks.One<ClientResponse> empty = Sinks.one();
        WebClient webClient = WebClient.builder()
                .baseUrl("http://semantic-flight.test")
                .exchangeFunction(request -> {
                    String target = request.url().getPath();
                    int dispatch = dispatches.computeIfAbsent(target, ignored -> new AtomicInteger())
                            .incrementAndGet();
                    if (target.endsWith("/cancel") && !cancellationCanComplete.get()) {
                        return Mono.<ClientResponse>never().doOnCancel(sourceCancellations::incrementAndGet);
                    }
                    if (target.endsWith("/error") && dispatch == 1) {
                        return error.asMono();
                    }
                    if (target.endsWith("/empty") && dispatch == 1) {
                        return empty.asMono();
                    }
                    return Mono.just(ok("reloaded-" + target));
                })
                .build();
        ReactiveHttpClientJsonCodec failingCodec = failingCodec();

        try (AnnotationConfigApplicationContext context = context()) {
            LocalResponseCacheManager manager = LocalResponseCacheManager.testing(System::nanoTime);
            SemanticPostClient client = client(
                    SemanticPostClient.class, "semantic-flight-client", webClient,
                    config(0, true, null, null), context, manager, failingCodec);

            Mono<String> cancelledCall = post(
                    client, "cancel", "body", "text/plain", "tenant", "one", "en");
            Disposable leader = cancelledCall.subscribe();
            Disposable waiter = cancelledCall.subscribe();
            awaitValue(dispatches, "/search/cancel", 1);
            leader.dispose();
            assertThat(sourceCancellations).hasValue(0);
            waiter.dispose();
            awaitValue(sourceCancellations, 1);
            cancellationCanComplete.set(true);
            assertThat(post(client, "cancel", "body", "text/plain", "tenant", "two", "en").block())
                    .contains("reloaded");
            assertThat(dispatches.get("/search/cancel")).hasValue(2);

            Mono<String> failedCall = post(client, "error", "body", "text/plain", "tenant", "one", "en");
            CompletableFuture<String> failedLeader = failedCall.toFuture();
            CompletableFuture<String> failedWaiter = failedCall.toFuture();
            error.tryEmitError(new IllegalStateException("load failed")).orThrow();
            assertThatThrownBy(() -> failedLeader.get(1, TimeUnit.SECONDS)).hasRootCauseMessage("load failed");
            assertThatThrownBy(() -> failedWaiter.get(1, TimeUnit.SECONDS)).hasRootCauseMessage("load failed");
            assertThat(post(client, "error", "body", "text/plain", "tenant", "two", "en").block())
                    .contains("reloaded");
            assertThat(dispatches.get("/search/error")).hasValue(2);

            Mono<String> emptyCall = post(client, "empty", "body", "text/plain", "tenant", "one", "en");
            CompletableFuture<String> emptyLeader = emptyCall.toFuture();
            CompletableFuture<String> emptyWaiter = emptyCall.toFuture();
            empty.tryEmitValue(ClientResponse.create(HttpStatus.NO_CONTENT).build()).orThrow();
            assertThat(emptyLeader.get(1, TimeUnit.SECONDS)).isNull();
            assertThat(emptyWaiter.get(1, TimeUnit.SECONDS)).isNull();
            assertThat(post(client, "empty", "body", "text/plain", "tenant", "two", "en").block())
                    .contains("reloaded");
            assertThat(dispatches.get("/search/empty")).hasValue(2);

            assertThatThrownBy(() -> contextual(client.json(
                    "serialization", new JsonBody("value"), "application/json", "tenant", "one"), "en").block())
                    .hasCauseInstanceOf(IllegalStateException.class)
                    .hasRootCauseMessage("serialize selected body");
            assertThat(dispatches).doesNotContainKey("/json/serialization");
            assertThat(post(client, "serialization", "body", "text/plain", "tenant", "two", "en").block())
                    .contains("reloaded");
            manager.close();
        }
    }

    @Test
    void postRefreshUsesTheTriggeringRequestAndStartsOneRefreshPerKey() {
        AtomicLong ticker = new AtomicLong();
        AtomicInteger dispatches = new AtomicInteger();
        List<String> refreshNonces = new ArrayList<>();
        Sinks.One<ClientResponse> refresh = Sinks.one();
        WebClient webClient = WebClient.builder()
                .baseUrl("http://semantic-flight.test")
                .exchangeFunction(request -> {
                    int dispatch = dispatches.incrementAndGet();
                    refreshNonces.add(request.headers().getFirst("X-Refresh-Nonce"));
                    return dispatch == 1 ? Mono.just(ok("initial")) : refresh.asMono();
                })
                .build();

        try (AnnotationConfigApplicationContext context = context()) {
            LocalResponseCacheManager manager = LocalResponseCacheManager.testing(ticker::get);
            SemanticPostClient client = client(
                    SemanticPostClient.class, "semantic-flight-client", webClient,
                    config(0, true, 50L, 500L), context, manager, TestJsonCodecs.jsonCodec());

            assertThat(post(client, "refresh", "body", "text/plain", "tenant", "initial", "en").block())
                    .isEqualTo("initial");
            ticker.addAndGet(Duration.ofMillis(50).toNanos());
            assertThat(post(client, "refresh", "body", "text/plain", "tenant", "fresh", "en").block())
                    .isEqualTo("initial");
            assertThat(post(client, "refresh", "body", "text/plain", "tenant", "ignored", "en").block())
                    .isEqualTo("initial");

            assertThat(dispatches).hasValue(2);
            assertThat(refreshNonces).containsExactly("initial", "fresh");
            refresh.tryEmitValue(ok("refreshed")).orThrow();
            assertThat(post(client, "refresh", "body", "text/plain", "tenant", "later", "en").block())
                    .isEqualTo("refreshed");
            assertThat(dispatches).hasValue(2);
            manager.close();
        }
    }

    @Test
    void postRefreshFailureExpiryEvictionAndShutdownRejectLatePublication() {
        AtomicLong ticker = new AtomicLong();
        AtomicInteger dispatches = new AtomicInteger();
        AtomicInteger cancellations = new AtomicInteger();
        Sinks.One<ClientResponse> evictedRefresh = Sinks.one();
        Sinks.One<ClientResponse> shutdownRefresh = Sinks.one();
        WebClient webClient = WebClient.builder()
                .baseUrl("http://semantic-flight.test")
                .exchangeFunction(request -> switch (dispatches.incrementAndGet()) {
                    case 1 -> Mono.just(ok("initial"));
                    case 2 -> Mono.just(ClientResponse.create(HttpStatus.SERVICE_UNAVAILABLE)
                            .body("refresh failed").build());
                    case 3 -> Mono.just(ok("after-expiry"));
                    case 4 -> evictedRefresh.asMono().doOnCancel(cancellations::incrementAndGet);
                    case 5 -> Mono.just(ok("after-eviction"));
                    case 6 -> shutdownRefresh.asMono().doOnCancel(cancellations::incrementAndGet);
                    default -> Mono.error(new AssertionError("Unexpected dispatch"));
                })
                .build();

        try (AnnotationConfigApplicationContext context = context()) {
            LocalResponseCacheManager manager = LocalResponseCacheManager.testing(ticker::get);
            SemanticPostClient client = client(
                    SemanticPostClient.class, "semantic-flight-client", webClient,
                    config(0, true, 50L, 500L, 200L), context, manager, TestJsonCodecs.jsonCodec());

            assertThat(post(client, "races", "body", "text/plain", "tenant", "one", "en").block())
                    .isEqualTo("initial");
            ticker.addAndGet(Duration.ofMillis(50).toNanos());
            assertThat(post(client, "races", "body", "text/plain", "tenant", "two", "en").block())
                    .isEqualTo("initial");
            assertThat(dispatches).hasValue(2);

            ticker.addAndGet(Duration.ofMillis(150).toNanos());
            assertThat(post(client, "races", "body", "text/plain", "tenant", "three", "en").block())
                    .isEqualTo("after-expiry");
            ticker.addAndGet(Duration.ofMillis(50).toNanos());
            assertThat(post(client, "races", "body", "text/plain", "tenant", "four", "en").block())
                    .isEqualTo("after-expiry");
            manager.evictAllForTesting();
            assertThat(cancellations).hasValue(1);
            evictedRefresh.tryEmitValue(ok("late-evicted"));
            assertThat(manager.snapshot().currentSize()).isZero();

            assertThat(post(client, "races", "body", "text/plain", "tenant", "five", "en").block())
                    .isEqualTo("after-eviction");
            ticker.addAndGet(Duration.ofMillis(50).toNanos());
            assertThat(post(client, "races", "body", "text/plain", "tenant", "six", "en").block())
                    .isEqualTo("after-eviction");
            manager.close();
            assertThat(cancellations).hasValue(2);
            shutdownRefresh.tryEmitValue(ok("late-shutdown"));
            assertThat(manager.snapshot()).isEqualTo(
                    new LocalResponseCacheManager.Snapshot(0, 0, 0, true));
        }
    }

    private static ClientRequest withCountedBody(ClientRequest request, AtomicInteger subscriptions) {
        BodyInserter<Object, ClientHttpRequest> counted = (output, context) -> Mono.defer(() -> {
            subscriptions.incrementAndGet();
            return request.body().insert(output, context);
        });
        return ClientRequest.from(request).body(counted).build();
    }

    private static ReactiveHttpClientProperties.ClientConfig config(
            long logicalTimeoutMs,
            boolean singleFlight,
            Long refreshAfterMs,
            Long refreshTimeoutMs) {
        return config(logicalTimeoutMs, singleFlight, refreshAfterMs, refreshTimeoutMs, 1_000L);
    }

    private static ReactiveHttpClientProperties.ClientConfig config(
            long logicalTimeoutMs,
            boolean singleFlight,
            Long refreshAfterMs,
            Long refreshTimeoutMs,
            long ttlMs) {
        ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();
        config.setLogicalCallTimeoutMs(logicalTimeoutMs);
        config.getCache().getPolicies().put(
                "post-flight", policy(ttlMs, singleFlight, refreshAfterMs, refreshTimeoutMs));
        config.getCache().getPolicies().put(
                "post-other", policy(ttlMs, singleFlight, refreshAfterMs, refreshTimeoutMs));
        return config;
    }

    private static ReactiveHttpClientProperties.CachePolicyConfig policy(
            long ttlMs,
            boolean singleFlight,
            Long refreshAfterMs,
            Long refreshTimeoutMs) {
        ReactiveHttpClientProperties.CachePolicyConfig policy =
                new ReactiveHttpClientProperties.CachePolicyConfig();
        policy.setTtlMs(ttlMs);
        policy.setMaximumSize(100L);
        policy.setSingleFlight(singleFlight);
        policy.setRefreshAfterMs(refreshAfterMs);
        policy.setRefreshTimeoutMs(refreshTimeoutMs);
        policy.setSharedResponse(true);
        policy.setVaryByParameters(List.of("payload"));
        policy.setVaryByHeaders(List.of("X-Tenant"));
        policy.setVaryByContext(List.of("locale"));
        return policy;
    }

    private static <T> T client(
            Class<T> clientType,
            String clientName,
            WebClient webClient,
            ReactiveHttpClientProperties.ClientConfig config,
            AnnotationConfigApplicationContext context,
            LocalResponseCacheManager manager,
            ReactiveHttpClientJsonCodec jsonCodec) {
        return client(clientType, clientName, webClient, config, context, manager, jsonCodec,
                new ReactiveHttpClientProperties.ObservabilityConfig());
    }

    private static <T> T client(
            Class<T> clientType,
            String clientName,
            WebClient webClient,
            ReactiveHttpClientProperties.ClientConfig config,
            AnnotationConfigApplicationContext context,
            LocalResponseCacheManager manager,
            ReactiveHttpClientJsonCodec jsonCodec,
            ReactiveHttpClientProperties.ObservabilityConfig observability) {
        ReactiveClientInvocationHandler handler = new ReactiveClientInvocationHandler(
                webClient,
                new MethodMetadataCache(),
                new RequestArgumentResolver(),
                new DefaultErrorDecoder(),
                config,
                clientName,
                clientType,
                context,
                new NoopResilienceOperatorApplier(),
                jsonCodec,
                observability,
                manager);
        return clientType.cast(Proxy.newProxyInstance(
                SemanticReadSingleFlightRefreshContractTest.class.getClassLoader(),
                new Class<?>[]{clientType},
                handler));
    }

    private static Mono<String> post(
            SemanticPostClient client,
            String target,
            String body,
            String contentType,
            String tenant,
            String nonce,
            String locale) {
        return contextual(client.search(target, body, contentType, tenant, nonce), locale);
    }

    private static <T> Mono<T> contextual(Mono<T> call, String locale) {
        return call.contextWrite(context -> context.put("locale", locale));
    }

    private static ReactiveHttpClientJsonCodec failingCodec() {
        ReactiveHttpClientJsonCodec delegate = TestJsonCodecs.jsonCodec();
        return new ReactiveHttpClientJsonCodec() {
            @Override
            public byte[] write(Object value) throws Exception {
                return delegate.write(value);
            }

            @Override
            public byte[] writeBounded(Object value, int maximumBytes) throws Exception {
                if (value instanceof JsonBody) {
                    throw new IllegalStateException("serialize selected body");
                }
                return delegate.writeBounded(value, maximumBytes);
            }

            @Override
            public <T> T read(byte[] value, Class<T> type) throws Exception {
                return delegate.read(value, type);
            }
        };
    }

    private static AnnotationConfigApplicationContext context() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.refresh();
        return context;
    }

    private static ClientResponse ok(String value) {
        return ClientResponse.create(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_TYPE, "text/plain")
                .body(value)
                .build();
    }

    private static void assertLogicalTimeout(CompletableFuture<String> future) {
        assertThatThrownBy(() -> future.get(1, TimeUnit.SECONDS))
                .satisfies(error -> assertThat(hasCause(error, LogicalCallTimeoutException.class)).isTrue());
    }

    private static boolean hasCause(Throwable error, Class<? extends Throwable> type) {
        Throwable current = error;
        while (current != null) {
            if (type.isInstance(current)) {
                return true;
            }
            current = current.getCause() != current ? current.getCause() : null;
        }
        return false;
    }

    private static void assertNoTransportEvidence(HttpClientObserverEvent event) {
        assertThat(event.getAttemptCount()).isZero();
        assertThat(event.getRequestUrl()).isNull();
        assertThat(event.getStatusCode()).isNull();
        assertThat(event.getRequestHeaders()).isEmpty();
    }

    private static void awaitValue(AtomicInteger value, int expected) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
        while (value.get() != expected && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        assertThat(value).hasValue(expected);
    }

    private static void awaitValue(Map<String, AtomicInteger> values, String key, int expected) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
        while ((!values.containsKey(key) || values.get(key).get() != expected)
                && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        assertThat(values.get(key)).hasValue(expected);
    }

    record JsonBody(String value) {
    }

    @ReactiveHttpClient(name = "semantic-flight-client", baseUrl = "http://semantic-flight.test")
    interface SemanticPostClient {

        @POST("/search/{target}")
        @CacheResponse(value = "post-flight", semanticRead = true)
        Mono<String> search(
                @PathVar("target") String target,
                @Body @CacheKey("payload") String payload,
                @HeaderParam(HttpHeaders.CONTENT_TYPE) String contentType,
                @HeaderParam("X-Tenant") String tenant,
                @HeaderParam("X-Refresh-Nonce") String refreshNonce);

        @POST("/search/{target}")
        @CacheResponse(value = "post-flight", semanticRead = true)
        Mono<String> alternate(
                @PathVar("target") String target,
                @Body @CacheKey("payload") String payload,
                @HeaderParam(HttpHeaders.CONTENT_TYPE) String contentType,
                @HeaderParam("X-Tenant") String tenant,
                @HeaderParam("X-Refresh-Nonce") String refreshNonce);

        @POST("/search/{target}")
        @CacheResponse(value = "post-other", semanticRead = true)
        Mono<String> otherPolicy(
                @PathVar("target") String target,
                @Body @CacheKey("payload") String payload,
                @HeaderParam(HttpHeaders.CONTENT_TYPE) String contentType,
                @HeaderParam("X-Tenant") String tenant,
                @HeaderParam("X-Refresh-Nonce") String refreshNonce);

        @POST("/json/{target}")
        @CacheResponse(value = "post-flight", semanticRead = true)
        Mono<String> json(
                @PathVar("target") String target,
                @Body @CacheKey("payload") JsonBody payload,
                @HeaderParam(HttpHeaders.CONTENT_TYPE) String contentType,
                @HeaderParam("X-Tenant") String tenant,
                @HeaderParam("X-Refresh-Nonce") String refreshNonce);
    }

    @ReactiveHttpClient(name = "other-semantic-flight-client", baseUrl = "http://semantic-flight.test")
    interface OtherSemanticPostClient {

        @POST("/search/{target}")
        @CacheResponse(value = "post-flight", semanticRead = true)
        Mono<String> search(
                @PathVar("target") String target,
                @Body @CacheKey("payload") String payload,
                @HeaderParam(HttpHeaders.CONTENT_TYPE) String contentType,
                @HeaderParam("X-Tenant") String tenant,
                @HeaderParam("X-Refresh-Nonce") String refreshNonce);
    }
}
