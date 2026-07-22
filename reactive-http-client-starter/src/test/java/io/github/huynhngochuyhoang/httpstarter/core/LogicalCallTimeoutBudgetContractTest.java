package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.annotation.GET;
import io.github.huynhngochuyhoang.httpstarter.annotation.LogHttpExchange;
import io.github.huynhngochuyhoang.httpstarter.auth.AuthContext;
import io.github.huynhngochuyhoang.httpstarter.auth.InvalidatableAuthProvider;
import io.github.huynhngochuyhoang.httpstarter.auth.OutboundAuthFilter;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import io.github.huynhngochuyhoang.httpstarter.exception.ErrorCategories;
import io.github.huynhngochuyhoang.httpstarter.exception.ErrorCategory;
import io.github.huynhngochuyhoang.httpstarter.exception.LogicalCallTimeoutException;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientFailureStage;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientObserverEvent;
import io.netty.handler.timeout.ReadTimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.netty.DisposableServer;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.server.HttpServer;
import reactor.netty.resources.ConnectionProvider;
import reactor.util.retry.Retry;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class LogicalCallTimeoutBudgetContractTest {

    private static final Duration BLOCK_TIMEOUT = Duration.ofSeconds(5);

    @Test
    void zeroBudgetPreservesDisabledBehavior() {
        WebClient webClient = WebClient.builder()
                .baseUrl("http://budget.test")
                .exchangeFunction(request -> Mono.delay(Duration.ofMillis(100))
                        .thenReturn(ClientResponse.create(HttpStatus.OK).body("ok").build()))
                .build();

        try (ClientFixture fixture = client(webClient, budget(0),
                new NoopResilienceOperatorApplier(), new CopyOnWriteArrayList<>())) {
            assertThat(fixture.client().call().block(BLOCK_TIMEOUT)).isEqualTo("ok");
        }
    }

    @Test
    void budgetExhaustsBeforeDispatchWhileAuthIsPending() {
        AtomicInteger dispatches = new AtomicInteger();
        List<HttpClientObserverEvent> events = new CopyOnWriteArrayList<>();
        ReactiveHttpClientProperties.ClientConfig config = budget(80);
        config.setAuthProvider("test-auth");
        WebClient webClient = WebClient.builder()
                .baseUrl("http://budget.test")
                .exchangeFunction(request -> {
                    dispatches.incrementAndGet();
                    return Mono.just(ClientResponse.create(HttpStatus.OK).body("ok").build());
                })
                .filter(new OutboundAuthFilter("budget-client",
                        request -> Mono.delay(Duration.ofMillis(250)).thenReturn(AuthContext.empty())))
                .filter(ReactiveClientInvocationHandler.finalRequestObservationFilter())
                .build();

        try (ClientFixture fixture = client(webClient, config, new NoopResilienceOperatorApplier(), events)) {
            Throwable failure = catchThrowable(() -> fixture.client().call().block(BLOCK_TIMEOUT));

            assertBudgetFailure(failure, 80, null);
            assertThat(dispatches).hasValue(0);
            assertThat(events).singleElement().satisfies(event -> {
                assertThat(event.getAttemptCount()).isEqualTo(1);
                assertThat(event.getStatusCode()).isNull();
                assertThat(event.getRequestUrl()).isNull();
                assertThat(event.getFailureStage()).isNull();
            });
        }
    }

    @Test
    void retryBackoffDoesNotResetBudgetOrReusePriorAttemptEvidence() {
        AtomicInteger subscriptions = new AtomicInteger();
        List<HttpClientObserverEvent> events = new CopyOnWriteArrayList<>();
        ReactiveHttpClientProperties.ClientConfig config = budget(100);
        ReactiveHttpClientProperties.ResilienceConfig resilience = new ReactiveHttpClientProperties.ResilienceConfig();
        resilience.setEnabled(true);
        resilience.setRetryMethods(java.util.Set.of("GET"));
        config.setResilience(resilience);
        WebClient webClient = WebClient.builder()
                .baseUrl("http://budget.test")
                .exchangeFunction(request -> Mono.defer(() -> {
                    subscriptions.incrementAndGet();
                    return Mono.error(new IOException("retry"));
                }))
                .filter(ReactiveClientInvocationHandler.finalRequestObservationFilter())
                .build();

        try (ClientFixture fixture = client(webClient, config, new DelayedRetryApplier(), events)) {
            Throwable failure = catchThrowable(() -> fixture.client().call().block(BLOCK_TIMEOUT));

            assertBudgetFailure(failure, 100, null);
            assertThat(subscriptions).hasValue(1);
            assertThat(events).singleElement().satisfies(event -> {
                assertThat(event.getAttemptCount()).isEqualTo(1);
                assertThat(event.getStatusCode()).isNull();
                assertThat(event.getRequestUrl()).isNull();
                assertThat(event.getRequestHeaders()).isEmpty();
            });
        }
    }

    @Test
    void previousAttemptCleanupCannotClearAnImmediateRetryInProgress() {
        AtomicInteger subscriptions = new AtomicInteger();
        List<HttpClientObserverEvent> events = new CopyOnWriteArrayList<>();
        ReactiveHttpClientProperties.ClientConfig config = budget(100);
        ReactiveHttpClientProperties.ResilienceConfig resilience = new ReactiveHttpClientProperties.ResilienceConfig();
        resilience.setEnabled(true);
        resilience.setRetryMethods(java.util.Set.of("GET"));
        config.setResilience(resilience);
        DefaultDataBufferFactory buffers = new DefaultDataBufferFactory();
        WebClient webClient = WebClient.builder()
                .baseUrl("http://budget.test")
                .exchangeFunction(request -> Mono.defer(() -> {
                    if (subscriptions.incrementAndGet() == 1) {
                        return Mono.error(new IOException("retry immediately"));
                    }
                    return Mono.just(ClientResponse.create(HttpStatus.OK)
                            .body(Flux.concat(
                                    Mono.just(buffers.wrap("first".getBytes(java.nio.charset.StandardCharsets.UTF_8))),
                                    Mono.delay(Duration.ofMillis(250)).thenReturn(
                                            buffers.wrap("second".getBytes(java.nio.charset.StandardCharsets.UTF_8)))))
                            .build());
                }))
                .filter(ReactiveClientInvocationHandler.finalRequestObservationFilter())
                .build();

        try (ClientFixture fixture = client(webClient, config, new ImmediateRetryApplier(), events)) {
            Throwable failure = catchThrowable(() -> fixture.client().active().blockLast(BLOCK_TIMEOUT));

            assertBudgetFailure(failure, 100, HttpClientFailureStage.RESPONSE_BODY);
            assertThat(subscriptions).hasValue(2);
            assertThat(events).singleElement().satisfies(event -> {
                assertThat(event.getAttemptCount()).isEqualTo(2);
                assertThat(event.getStatusCode()).isEqualTo(200);
                assertThat(event.getFailureStage()).isEqualTo(HttpClientFailureStage.RESPONSE_BODY);
            });
        }
    }

    @Test
    void hiddenUnauthorizedRefreshDoesNotResetBudget() {
        AtomicInteger dispatches = new AtomicInteger();
        AtomicInteger authCalls = new AtomicInteger();
        List<HttpClientObserverEvent> events = new CopyOnWriteArrayList<>();
        ReactiveHttpClientProperties.ClientConfig config = budget(100);
        config.setAuthProvider("refreshing-auth");
        InvalidatableAuthProvider authProvider = new InvalidatableAuthProvider() {
            @Override
            public Mono<AuthContext> getAuth(io.github.huynhngochuyhoang.httpstarter.auth.AuthRequest request) {
                int call = authCalls.incrementAndGet();
                Mono<AuthContext> token = Mono.just(AuthContext.builder()
                        .header("Authorization", "Bearer token-" + call)
                        .build());
                return call == 1 ? token : Mono.delay(Duration.ofMillis(250)).then(token);
            }

            @Override
            public Mono<Void> invalidate() {
                return Mono.empty();
            }
        };
        WebClient webClient = WebClient.builder()
                .baseUrl("http://budget.test")
                .exchangeFunction(request -> Mono.fromSupplier(() -> {
                    int dispatch = dispatches.incrementAndGet();
                    return ClientResponse.create(dispatch == 1 ? HttpStatus.UNAUTHORIZED : HttpStatus.OK)
                            .body("ok")
                            .build();
                }))
                .filter(new OutboundAuthFilter("budget-client", authProvider))
                .filter(ReactiveClientInvocationHandler.finalRequestObservationFilter())
                .build();

        try (ClientFixture fixture = client(webClient, config, new NoopResilienceOperatorApplier(), events)) {
            Throwable failure = catchThrowable(() -> fixture.client().call().block(BLOCK_TIMEOUT));

            assertBudgetFailure(failure, 100, null);
            assertThat(authCalls).hasValue(2);
            assertThat(dispatches).hasValue(1);
            assertThat(events).singleElement().satisfies(event -> {
                assertThat(event.getAttemptCount()).isEqualTo(1);
                assertThat(event.getStatusCode()).isNull();
                assertThat(event.getRequestUrl()).isNull();
                assertThat(event.getRequestHeaders()).isEmpty();
            });
        }
    }

    @Test
    void budgetExhaustsWhileQueuedForARealPoolWithoutInventingPoolStage() {
        try (BudgetServer server = new BudgetServer()) {
            ConnectionProvider provider = ConnectionProvider.builder("logical-budget-pool")
                    .maxConnections(1)
                    .pendingAcquireMaxCount(2)
                    .pendingAcquireTimeout(Duration.ofSeconds(3))
                    .build();
            HttpClient httpClient = HttpClient.create(provider);
            ReactorClientHttpConnector connector = new ReactorClientHttpConnector(httpClient);
            WebClient raw = WebClient.builder()
                    .baseUrl(server.baseUrl())
                    .clientConnector(connector)
                    .build();
            Disposable holder = raw.get().uri("/hold").retrieve().bodyToMono(String.class).subscribe();
            server.awaitPath("/hold");
            List<HttpClientObserverEvent> events = new CopyOnWriteArrayList<>();
            WebClient starter = WebClient.builder()
                    .baseUrl(server.baseUrl())
                    .clientConnector(connector)
                    .filter(ReactiveClientInvocationHandler.finalRequestObservationFilter())
                    .build();

            try (ClientFixture fixture = client(starter, budget(100), new NoopResilienceOperatorApplier(), events)) {
                Throwable failure = catchThrowable(() -> fixture.client().call().block(BLOCK_TIMEOUT));

                assertBudgetFailure(failure, 100, null);
                assertThat(server.paths()).containsExactly("/hold");
                assertThat(events).singleElement().satisfies(event -> {
                    assertThat(event.getAttemptCount()).isEqualTo(1);
                    assertThat(event.getStatusCode()).isNull();
                    assertThat(event.getFailureStage()).isNull();
                });
            } finally {
                server.releaseHold();
                holder.dispose();
                provider.disposeLater().block(BLOCK_TIMEOUT);
            }
        }
    }

    @Test
    void budgetCapturesUnaryBodyPhaseFromTheCurrentAttempt() {
        try (BudgetServer server = new BudgetServer()) {
            List<HttpClientObserverEvent> events = new CopyOnWriteArrayList<>();
            try (ClientFixture fixture = client(server.webClient(false), budget(100),
                    new NoopResilienceOperatorApplier(), events)) {
                Throwable failure = catchThrowable(() -> fixture.client().unary().block(BLOCK_TIMEOUT));

                assertBudgetFailure(failure, 100, HttpClientFailureStage.RESPONSE_BODY);
                assertThat(events).singleElement().satisfies(event -> {
                    assertThat(event.getStatusCode()).isEqualTo(200);
                    assertThat(event.getFailureStage()).isEqualTo(HttpClientFailureStage.RESPONSE_BODY);
                    assertThat(event.getAttemptCount()).isEqualTo(1);
                });
                assertThat(fixture.diagnostics().lifecycleErrors).singleElement().satisfies(context -> {
                    assertThat(context.statusCode()).isEqualTo(200);
                    assertThat(context.failureStage()).isEqualTo(HttpClientFailureStage.RESPONSE_BODY);
                    assertThat(findCause(context.error(), LogicalCallTimeoutException.class)).isNotNull();
                });
                assertThat(fixture.diagnostics().exchangeLogs).singleElement().satisfies(context -> {
                    assertThat(context.responseStatus()).isEqualTo(200);
                    assertThat(context.failureStage()).isEqualTo(HttpClientFailureStage.RESPONSE_BODY);
                    assertThat(findCause(context.error(), LogicalCallTimeoutException.class)).isNotNull();
                });
            }
        }
    }

    @Test
    void existingPerAttemptResponseTimeoutStillWinsWhenItExpiresFirst() {
        try (BudgetServer server = new BudgetServer()) {
            ReactiveHttpClientProperties.ClientConfig config = budget(500);
            config.setRequestTimeoutMs(80);
            List<HttpClientObserverEvent> events = new CopyOnWriteArrayList<>();
            try (ClientFixture fixture = client(server.webClient(false), config,
                    new NoopResilienceOperatorApplier(), events)) {
                Throwable failure = catchThrowable(() -> fixture.client().unary().block(BLOCK_TIMEOUT));

                assertThat(findCause(failure, LogicalCallTimeoutException.class)).isNull();
                assertThat(findCause(failure, ReadTimeoutException.class)).isNotNull();
                assertThat(events).singleElement().satisfies(event -> {
                    assertThat(event.getStatusCode()).isEqualTo(200);
                    assertThat(event.getFailureStage()).isEqualTo(HttpClientFailureStage.RESPONSE_BODY);
                });
            }
        }
    }

    @Test
    void redirectDoesNotResetTheBudget() {
        try (BudgetServer server = new BudgetServer()) {
            List<HttpClientObserverEvent> events = new CopyOnWriteArrayList<>();
            try (ClientFixture fixture = client(server.webClient(true), budget(120),
                    new NoopResilienceOperatorApplier(), events)) {
                Throwable failure = catchThrowable(() -> fixture.client().redirect().block(BLOCK_TIMEOUT));

                assertBudgetFailure(failure, 120, null);
                assertThat(server.paths()).containsSubsequence("/redirect", "/redirect-final");
                assertThat(events).singleElement().satisfies(event -> {
                    assertThat(event.getAttemptCount()).isEqualTo(1);
                    assertThat(event.getStatusCode()).isNull();
                    assertThat(event.getFailureStage()).isNull();
                });
            }
        }
    }

    @Test
    void activeDirectStreamUsesOneAbsoluteBudgetInsteadOfAnInactivityTimeout() {
        try (BudgetServer server = new BudgetServer()) {
            List<HttpClientObserverEvent> events = new CopyOnWriteArrayList<>();
            AtomicInteger received = new AtomicInteger();
            try (ClientFixture fixture = client(server.webClient(false), budget(180),
                    new NoopResilienceOperatorApplier(), events)) {
                Throwable failure = catchThrowable(() -> fixture.client().active()
                        .doOnNext(buffer -> {
                            received.incrementAndGet();
                            DataBufferUtils.release(buffer);
                        })
                        .blockLast(BLOCK_TIMEOUT));

                assertBudgetFailure(failure, 180, HttpClientFailureStage.RESPONSE_BODY);
                assertThat(received).hasValueGreaterThanOrEqualTo(1);
                assertThat(events).singleElement().satisfies(event -> {
                    assertThat(event.getStatusCode()).isEqualTo(200);
                    assertThat(event.getFailureStage()).isEqualTo(HttpClientFailureStage.RESPONSE_BODY);
                });
            }
        }
    }

    @Test
    void streamingEnvelopeBudgetEndsWhenOwnershipTransfersToCaller() throws InterruptedException {
        try (BudgetServer server = new BudgetServer();
             ClientFixture fixture = client(server.webClient(false), budget(80),
                     new NoopResilienceOperatorApplier(), new CopyOnWriteArrayList<>())) {
            ResponseEntity<Flux<DataBuffer>> envelope =
                    fixture.client().envelope().block(BLOCK_TIMEOUT);

            assertThat(envelope).isNotNull();
            assertThat(envelope.getStatusCode().value()).isEqualTo(200);
            Thread.sleep(120);
            String body = envelope.getBody()
                    .map(buffer -> {
                        try {
                            return buffer.toString(java.nio.charset.StandardCharsets.UTF_8);
                        } finally {
                            DataBufferUtils.release(buffer);
                        }
                    })
                    .collectList()
                    .map(parts -> String.join("", parts))
                    .block(BLOCK_TIMEOUT);
            assertThat(body).isEqualTo("firstsecond");
        }
    }

    private static ReactiveHttpClientProperties.ClientConfig budget(long timeoutMs) {
        ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();
        config.setLogicalCallTimeoutMs(timeoutMs);
        return config;
    }

    private static void assertBudgetFailure(
            Throwable failure, long timeoutMs, HttpClientFailureStage failureStage) {
        LogicalCallTimeoutException timeout = findCause(failure, LogicalCallTimeoutException.class);
        assertThat(timeout).isNotNull();
        assertThat(timeout.getTimeoutMs()).isEqualTo(timeoutMs);
        assertThat(timeout.getFailureStage()).isEqualTo(failureStage);
        assertThat(ErrorCategories.from(timeout)).isEqualTo(ErrorCategory.TIMEOUT);
        assertThat(HttpClientFailureStage.from(timeout)).isEqualTo(failureStage);
    }

    private static <T extends Throwable> T findCause(Throwable error, Class<T> type) {
        Throwable current = error;
        while (current != null) {
            if (type.isInstance(current)) {
                return type.cast(current);
            }
            current = current.getCause() != current ? current.getCause() : null;
        }
        return null;
    }

    private static ClientFixture client(
            WebClient webClient,
            ReactiveHttpClientProperties.ClientConfig config,
            ResilienceOperatorApplier resilience,
            List<HttpClientObserverEvent> events) {
        StaticApplicationContext context = new StaticApplicationContext();
        BudgetDiagnostics diagnostics = new BudgetDiagnostics(events);
        context.getBeanFactory().registerSingleton("budgetDiagnostics", diagnostics);
        context.refresh();
        ReactiveClientInvocationHandler handler = ReactiveClientInvocationHandler.create(
                webClient,
                new MethodMetadataCache(),
                new RequestArgumentResolver(),
                new DefaultErrorDecoder(),
                config,
                "budget-client",
                BudgetClient.class,
                context,
                resilience,
                null,
                new ReactiveHttpClientProperties.ObservabilityConfig());
        BudgetClient client = (BudgetClient) Proxy.newProxyInstance(
                BudgetClient.class.getClassLoader(), new Class<?>[]{BudgetClient.class}, handler);
        return new ClientFixture(context, client, diagnostics);
    }

    private static void await(BooleanSupplier condition, String message) {
        long deadline = System.nanoTime() + Duration.ofSeconds(3).toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(error);
            }
        }
        assertThat(condition.getAsBoolean()).as(message).isTrue();
    }

    @LogHttpExchange(logger = BudgetDiagnostics.class)
    interface BudgetClient {
        @GET("/call")
        Mono<String> call();

        @GET("/unary")
        Mono<String> unary();

        @GET("/redirect")
        Mono<String> redirect();

        @GET("/active")
        Flux<DataBuffer> active();

        @GET("/envelope")
        Mono<ResponseEntity<Flux<DataBuffer>>> envelope();
    }

    private record ClientFixture(
            StaticApplicationContext context,
            BudgetClient client,
            BudgetDiagnostics diagnostics)
            implements AutoCloseable {
        @Override
        public void close() {
            context.close();
        }
    }

    private static final class BudgetDiagnostics implements
            io.github.huynhngochuyhoang.httpstarter.observability.HttpClientObserver,
            ReactiveHttpClientLifecycleHook,
            HttpExchangeLogger {
        private final List<HttpClientObserverEvent> observerEvents;
        private final List<ReactiveHttpClientLifecycleContext> lifecycleErrors = new CopyOnWriteArrayList<>();
        private final List<HttpExchangeLogContext> exchangeLogs = new CopyOnWriteArrayList<>();

        private BudgetDiagnostics(List<HttpClientObserverEvent> observerEvents) {
            this.observerEvents = observerEvents;
        }

        @Override
        public void record(HttpClientObserverEvent event) {
            observerEvents.add(event);
        }

        @Override
        public void onError(ReactiveHttpClientLifecycleContext context) {
            lifecycleErrors.add(context);
        }

        @Override
        public void log(HttpExchangeLogContext context) {
            exchangeLogs.add(context);
        }
    }

    private static final class DelayedRetryApplier extends NoopResilienceOperatorApplier {
        @Override
        public <T> Mono<T> applyRetry(Mono<T> mono, String instanceName) {
            return mono.retryWhen(Retry.fixedDelay(1, Duration.ofMillis(250)));
        }

        @Override
        public <T> Flux<T> applyRetry(Flux<T> flux, String instanceName) {
            return flux.retryWhen(Retry.fixedDelay(1, Duration.ofMillis(250)));
        }

        @Override
        public boolean isOperatorAvailable(InstanceType type) {
            return type == InstanceType.RETRY;
        }
    }

    private static final class ImmediateRetryApplier extends NoopResilienceOperatorApplier {
        @Override
        public <T> Mono<T> applyRetry(Mono<T> mono, String instanceName) {
            return mono.retry(1);
        }

        @Override
        public <T> Flux<T> applyRetry(Flux<T> flux, String instanceName) {
            return flux.retry(1);
        }

        @Override
        public boolean isOperatorAvailable(InstanceType type) {
            return type == InstanceType.RETRY;
        }
    }

    private static final class BudgetServer implements AutoCloseable {
        private final Sinks.One<Void> holdRelease = Sinks.one();
        private final List<String> paths = new CopyOnWriteArrayList<>();
        private final DisposableServer server = HttpServer.create()
                .port(0)
                .handle((request, response) -> {
                    String path = request.uri().split("\\?", 2)[0];
                    paths.add(path);
                    return switch (path) {
                        case "/hold" -> holdRelease.asMono()
                                .then(response.sendString(Mono.just("released")).then());
                        case "/call" -> response.sendString(Mono.just("ok")).then();
                        case "/unary" -> response.sendString(Flux.concat(
                                Mono.just("first"),
                                Mono.delay(Duration.ofMillis(250)).thenReturn("second"))).then();
                        case "/active" -> response.sendString(
                                Flux.interval(Duration.ofMillis(25)).take(20).map(index -> "x")).then();
                        case "/envelope" -> response.sendString(Flux.concat(
                                Mono.just("first"),
                                Mono.delay(Duration.ofMillis(180)).thenReturn("second"))).then();
                        case "/redirect" -> Mono.delay(Duration.ofMillis(60))
                                .then(response.status(HttpStatus.FOUND.value())
                                        .header("Location", "/redirect-final")
                                        .send()
                                        .then());
                        case "/redirect-final" -> Mono.delay(Duration.ofMillis(180))
                                .then(response.sendString(Mono.just("redirected")).then());
                        default -> response.status(HttpStatus.NOT_FOUND.value()).send();
                    };
                })
                .bindNow();

        String baseUrl() {
            return "http://127.0.0.1:" + server.port();
        }

        WebClient webClient(boolean followRedirects) {
            return WebClient.builder()
                    .baseUrl(baseUrl())
                    .clientConnector(new ReactorClientHttpConnector(
                            HttpClient.create().followRedirect(followRedirects)))
                    .filter(ReactiveClientInvocationHandler.finalRequestObservationFilter())
                    .build();
        }

        List<String> paths() {
            return List.copyOf(paths);
        }

        void releaseHold() {
            holdRelease.tryEmitEmpty();
        }

        void awaitPath(String path) {
            await(() -> paths.contains(path), "server should receive " + path);
        }

        @Override
        public void close() {
            holdRelease.tryEmitEmpty();
            server.disposeNow(BLOCK_TIMEOUT);
        }
    }
}
