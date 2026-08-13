package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.annotation.GET;
import io.github.huynhngochuyhoang.httpstarter.annotation.LogHttpExchange;
import io.github.huynhngochuyhoang.httpstarter.annotation.ReactiveHttpClient;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientFailureStage;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientObserver;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientObserverEvent;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.Exceptions;
import reactor.core.publisher.Mono;
import reactor.netty.resources.ConnectionProvider;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class StalePooledConnectionRecoveryContractTest {

    private static final Duration CALL_TIMEOUT = Duration.ofSeconds(5);

    @Test
    void connectionCloseResponseRetiresTheSocketBeforeIndependentReuse() {
        try (RawHttpPeer peer = new RawHttpPeer();
             MeterFixture meters = new MeterFixture();
             ClientFixture fixture = ClientFixture.create(peer, false)) {
            assertThat(fixture.client().connectionClose().block(CALL_TIMEOUT)).isEqualTo("closing");
            assertThat(fixture.client().probe().block(CALL_TIMEOUT)).isEqualTo("probe");

            assertThat(peer.paths()).containsExactly("/connection-close", "/probe");
            assertThat(peer.records().get(1).connectionId())
                    .isNotEqualTo(peer.records().get(0).connectionId());
            meters.awaitConverged(fixture.poolName());
        }
    }

    @Test
    void peerFinAndIdleCloseAreRemovedBeforeLaterCallsAcquireCapacity() {
        try (RawHttpPeer peer = new RawHttpPeer();
             MeterFixture meters = new MeterFixture();
             ClientFixture fixture = ClientFixture.create(peer, false)) {
            assertThat(fixture.client().finAfterResponse().block(CALL_TIMEOUT)).isEqualTo("fin");
            assertThat(fixture.client().probe().block(CALL_TIMEOUT)).isEqualTo("probe");
            int replacementAfterFin = peer.records().get(1).connectionId();
            assertThat(replacementAfterFin).isNotEqualTo(peer.records().get(0).connectionId());

            assertThat(fixture.client().idle().block(CALL_TIMEOUT)).isEqualTo("idle");
            WireRequest idle = peer.records().get(2);
            assertThat(idle.connectionId()).isEqualTo(replacementAfterFin);
            peer.closeConnection(idle.connectionId());

            assertThat(fixture.client().probe().block(CALL_TIMEOUT)).isEqualTo("probe");
            assertThat(peer.paths()).containsExactly("/fin-after-response", "/probe", "/idle", "/probe");
            assertThat(peer.records().get(3).connectionId()).isNotEqualTo(idle.connectionId());
            meters.awaitConverged(fixture.poolName());
        }
    }

    @Test
    void resetDuringReuseFailsOnceAndQueuedDemandUsesReplacementCapacity() throws Exception {
        RecordingDiagnostics diagnostics = new RecordingDiagnostics();
        try (RawHttpPeer peer = new RawHttpPeer();
             MeterFixture meters = new MeterFixture();
             ClientFixture fixture = ClientFixture.create(peer, false, diagnostics)) {
            assertThat(fixture.client().idle().block(CALL_TIMEOUT)).isEqualTo("idle");
            int staleConnection = peer.records().getFirst().connectionId();
            diagnostics.clear();

            CompletableFuture<String> failed = fixture.client().resetOnReuse().toFuture();
            peer.awaitResetRequest();
            CompletableFuture<String> queued = fixture.client().probe().toFuture();
            meters.awaitGauge(ProtocolAwareConnectionPoolMeterRegistrar.PENDING_CONNECTIONS,
                    fixture.poolName(), 1);
            assertThat(peer.paths()).containsExactly("/idle", "/reset-on-reuse");

            peer.releaseReset();
            Throwable terminalFailure = terminalFailure(failed);
            assertThat(queued.get(5, TimeUnit.SECONDS)).isEqualTo("probe");

            assertThat(peer.paths()).containsExactly("/idle", "/reset-on-reuse", "/probe");
            assertThat(peer.records("/reset-on-reuse")).hasSize(1);
            assertThat(peer.records("/reset-on-reuse").getFirst().connectionId()).isEqualTo(staleConnection);
            assertThat(peer.records("/probe").getFirst().connectionId()).isNotEqualTo(staleConnection);
            meters.awaitConverged(fixture.poolName());

            assertTerminalFailure(diagnostics, "resetOnReuse", terminalFailure, null);
            assertTerminalSuccessWithoutStaleEvidence(diagnostics, "probe");
        }
    }

    @Test
    void closeDuringResponseConsumptionQuarantinesTheSocketAndDoesNotLeakTerminalFacts() {
        RecordingDiagnostics diagnostics = new RecordingDiagnostics();
        try (RawHttpPeer peer = new RawHttpPeer();
             MeterFixture meters = new MeterFixture();
             ClientFixture fixture = ClientFixture.create(peer, false, diagnostics)) {
            Throwable failure = catchThrowable(() -> fixture.client().partialBody().block(CALL_TIMEOUT));
            Throwable terminalFailure = Exceptions.unwrap(failure);

            assertThat(terminalFailure).isNotNull();
            assertThat(fixture.client().probe().block(CALL_TIMEOUT)).isEqualTo("probe");
            assertThat(peer.paths()).containsExactly("/partial-body", "/probe");
            assertThat(peer.records().get(1).connectionId())
                    .isNotEqualTo(peer.records().get(0).connectionId());
            meters.awaitConverged(fixture.poolName());

            assertTerminalFailure(
                    diagnostics, "partialBody", terminalFailure, HttpClientFailureStage.RESPONSE_BODY);
            HttpExchangeLogContext partialLog = diagnostics.exchangeLog("/partial-body");
            assertThat(partialLog.responseHeaders()).containsEntry("X-Stale-Response", List.of("partial"));
            assertTerminalSuccessWithoutStaleEvidence(diagnostics, "probe");
        }
    }

    @Test
    void configuredRetryResubscribesAfterStaleFailureWithoutTransportOwnedReplay() {
        RecordingDiagnostics diagnostics = new RecordingDiagnostics();
        try (RawHttpPeer peer = new RawHttpPeer();
             MeterFixture meters = new MeterFixture();
             ClientFixture fixture = ClientFixture.create(peer, true, diagnostics)) {
            assertThat(fixture.client().idle().block(CALL_TIMEOUT)).isEqualTo("idle");
            diagnostics.clear();

            assertThat(fixture.client().retryAfterReset().block(CALL_TIMEOUT)).isEqualTo("retry-ok");

            List<WireRequest> attempts = peer.records("/retry-after-reset");
            assertThat(attempts).hasSize(2);
            assertThat(attempts.get(1).connectionId()).isNotEqualTo(attempts.get(0).connectionId());
            assertThat(diagnostics.observerEvent("retryAfterReset")).satisfies(event -> {
                assertThat(event.getAttemptCount()).isEqualTo(2);
                assertThat(event.getStatusCode()).isEqualTo(200);
                assertThat(event.getError()).isNull();
            });
            assertThat(diagnostics.exchangeLog("/retry-after-reset")).satisfies(log -> {
                assertThat(log.subscriptionAttemptCount()).isEqualTo(2);
                assertThat(log.responseStatus()).isEqualTo(200);
                assertThat(log.error()).isNull();
            });
            meters.awaitConverged(fixture.poolName());
        }
    }

    @Test
    void resetBeforeRequestBytesDoesNotTriggerTransportOwnedRetry() {
        try (RawHttpPeer peer = new RawHttpPeer();
             ClientFixture fixture = ClientFixture.create(peer, false)) {
            peer.resetNextConnectionBeforeRequest();

            Throwable failure = catchThrowable(() -> fixture.client().probe().block(CALL_TIMEOUT));

            assertThat(failure).isNotNull();
            peer.awaitPreRequestReset();
            assertThat(peer.connectionCount()).isEqualTo(1);
            assertThat(peer.records()).isEmpty();
        }
    }

    @Test
    void shutdownTerminatesActiveAndPendingWorkWithinTheOwnedDisposalBound() throws Exception {
        try (RawHttpPeer peer = new RawHttpPeer(); MeterFixture meters = new MeterFixture()) {
            ClientFixture fixture = ClientFixture.create(peer, false);
            CompletableFuture<String> active = fixture.client().hold().toFuture();
            peer.awaitHoldRequest();
            CompletableFuture<String> pending = fixture.client().probe().toFuture();
            meters.awaitGauge(ProtocolAwareConnectionPoolMeterRegistrar.PENDING_CONNECTIONS,
                    fixture.poolName(), 1);

            long started = System.nanoTime();
            CompletableFuture<Void> shutdown = CompletableFuture.runAsync(fixture::close);
            shutdown.get(5, TimeUnit.SECONDS);

            await(() -> active.isDone() && pending.isDone(),
                    "active request and pending acquire should terminate during shutdown");
            Duration elapsed = Duration.ofNanos(System.nanoTime() - started);
            assertThat(elapsed).isLessThan(Duration.ofSeconds(5));
            assertThat(active).isCompletedExceptionally();
            assertThat(pending).isCompletedExceptionally();
            assertThat(fixture.connectionProvider().isDisposed()).isTrue();
            assertThat(peer.paths()).doesNotContain("/probe");
            peer.releaseHold();
        }
    }

    private static void assertTerminalFailure(RecordingDiagnostics diagnostics,
                                              String apiName,
                                              Throwable terminalFailure,
                                              HttpClientFailureStage expectedStage) {
        assertThat(diagnostics.observerEvent(apiName)).satisfies(event -> {
            assertThat(event.getError()).isSameAs(terminalFailure);
            assertThat(event.getFailureStage()).isEqualTo(expectedStage);
            assertThat(event.getRequestUrl()).endsWith("/" + kebabCase(apiName));
        });
        assertThat(diagnostics.lifecycleContext(apiName)).satisfies(context -> {
            assertThat(context.error()).isSameAs(terminalFailure);
            assertThat(context.failureStage()).isEqualTo(expectedStage);
            assertThat(context.requestUrl().getPath()).isEqualTo("/" + kebabCase(apiName));
        });
        assertThat(diagnostics.exchangeLog("/" + kebabCase(apiName))).satisfies(log -> {
            assertThat(log.error()).isSameAs(terminalFailure);
            assertThat(log.failureStage()).isEqualTo(expectedStage);
        });
    }

    private static void assertTerminalSuccessWithoutStaleEvidence(
            RecordingDiagnostics diagnostics, String apiName) {
        assertThat(diagnostics.observerEvent(apiName)).satisfies(event -> {
            assertThat(event.getStatusCode()).isEqualTo(200);
            assertThat(event.getError()).isNull();
            assertThat(event.getFailureStage()).isNull();
            assertThat(event.getRequestUrl()).endsWith("/" + kebabCase(apiName));
        });
        assertThat(diagnostics.lifecycleContext(apiName)).satisfies(context -> {
            assertThat(context.statusCode()).isEqualTo(200);
            assertThat(context.error()).isNull();
            assertThat(context.failureStage()).isNull();
            assertThat(context.requestUrl().getPath()).isEqualTo("/" + kebabCase(apiName));
        });
        assertThat(diagnostics.exchangeLog("/" + kebabCase(apiName))).satisfies(log -> {
            assertThat(log.responseStatus()).isEqualTo(200);
            assertThat(log.responseHeaders()).containsEntry("X-Probe", List.of("replacement"));
            assertThat(log.responseHeaders()).doesNotContainKey("X-Stale-Response");
            assertThat(log.error()).isNull();
            assertThat(log.failureStage()).isNull();
        });
    }

    private static String kebabCase(String methodName) {
        return methodName.replaceAll("([a-z])([A-Z])", "$1-$2").toLowerCase();
    }

    private static Throwable terminalFailure(CompletableFuture<?> future) {
        try {
            future.get(5, TimeUnit.SECONDS);
            throw new AssertionError("Expected the future to fail");
        } catch (ExecutionException error) {
            return Exceptions.unwrap(error.getCause());
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(error);
        } catch (TimeoutException error) {
            throw new AssertionError("Timed out waiting for terminal failure", error);
        }
    }

    private static void await(BooleanSupplier condition, String message) {
        long deadline = System.nanoTime() + Duration.ofSeconds(3).toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(error);
            }
        }
        assertThat(condition.getAsBoolean()).as(message).isTrue();
    }

    @LogHttpExchange(logger = RecordingDiagnostics.class)
    @ReactiveHttpClient(name = "stale-pool-client")
    interface StalePoolClient {
        @GET("/connection-close")
        Mono<String> connectionClose();

        @GET("/fin-after-response")
        Mono<String> finAfterResponse();

        @GET("/idle")
        Mono<String> idle();

        @GET("/reset-on-reuse")
        Mono<String> resetOnReuse();

        @GET("/partial-body")
        Mono<String> partialBody();

        @GET("/retry-after-reset")
        Mono<String> retryAfterReset();

        @GET("/hold")
        Mono<String> hold();

        @GET("/probe")
        Mono<String> probe();
    }

    private static final class RecordingDiagnostics
            implements HttpClientObserver, ReactiveHttpClientLifecycleHook, HttpExchangeLogger {
        private final List<HttpClientObserverEvent> observerEvents = new CopyOnWriteArrayList<>();
        private final List<ReactiveHttpClientLifecycleContext> lifecycleContexts = new CopyOnWriteArrayList<>();
        private final List<HttpExchangeLogContext> exchangeLogs = new CopyOnWriteArrayList<>();

        @Override
        public void record(HttpClientObserverEvent event) {
            observerEvents.add(event);
        }

        @Override
        public void onSuccess(ReactiveHttpClientLifecycleContext context) {
            lifecycleContexts.add(context);
        }

        @Override
        public void onError(ReactiveHttpClientLifecycleContext context) {
            lifecycleContexts.add(context);
        }

        @Override
        public void log(HttpExchangeLogContext context) {
            exchangeLogs.add(context);
        }

        HttpClientObserverEvent observerEvent(String apiName) {
            return observerEvents.stream()
                    .filter(event -> apiName.equals(event.getApiName()))
                    .findFirst()
                    .orElseThrow();
        }

        ReactiveHttpClientLifecycleContext lifecycleContext(String apiName) {
            return lifecycleContexts.stream()
                    .filter(context -> apiName.equals(context.apiName()))
                    .findFirst()
                    .orElseThrow();
        }

        HttpExchangeLogContext exchangeLog(String path) {
            return exchangeLogs.stream()
                    .filter(log -> path.equals(log.pathTemplate()))
                    .findFirst()
                    .orElseThrow();
        }

        void clear() {
            observerEvents.clear();
            lifecycleContexts.clear();
            exchangeLogs.clear();
        }
    }

    private static final class ClientFixture implements AutoCloseable {
        private static final Duration OPERATION_TIMEOUT = Duration.ofSeconds(30);

        private final StaticApplicationContext context;
        private final ReactiveHttpClientFactoryBean<StalePoolClient> factory;
        private final StalePoolClient client;
        private final ConnectionProvider connectionProvider;
        private boolean closed;

        private ClientFixture(StaticApplicationContext context,
                              ReactiveHttpClientFactoryBean<StalePoolClient> factory,
                              StalePoolClient client,
                              ConnectionProvider connectionProvider) {
            this.context = context;
            this.factory = factory;
            this.client = client;
            this.connectionProvider = connectionProvider;
        }

        static ClientFixture create(RawHttpPeer peer, boolean retryEnabled) {
            return create(peer, retryEnabled, new RecordingDiagnostics());
        }

        static ClientFixture create(RawHttpPeer peer,
                                    boolean retryEnabled,
                                    RecordingDiagnostics diagnostics) {
            StaticApplicationContext context = new StaticApplicationContext();
            ReactiveHttpClientProperties properties = new ReactiveHttpClientProperties();
            ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();
            config.setBaseUrl(peer.baseUrl());
            config.setRequestTimeoutMs(OPERATION_TIMEOUT.toMillis());
            ReactiveHttpClientProperties.ConnectionPoolConfig pool =
                    new ReactiveHttpClientProperties.ConnectionPoolConfig();
            pool.setMaxConnections(1);
            pool.setPendingAcquireTimeoutMs(OPERATION_TIMEOUT.toMillis());
            pool.setMetricsEnabled(true);
            config.setPool(pool);
            if (retryEnabled) {
                ReactiveHttpClientProperties.ResilienceConfig resilience = config.getResilience();
                resilience.setEnabled(true);
                resilience.setRetry("default");
                resilience.setRetryMethods(Set.of("GET"));
                RetryRegistry retryRegistry = RetryRegistry.of(
                        RetryConfig.custom().maxAttempts(2).waitDuration(Duration.ZERO).build());
                retryRegistry.retry("default");
                context.getBeanFactory().registerSingleton("retryRegistry", retryRegistry);
            }
            properties.getClients().put("stale-pool-client", config);

            context.getBeanFactory().registerSingleton("reactiveHttpClientProperties", properties);
            context.getBeanFactory().registerSingleton("starterWebClientBuilder", WebClient.builder());
            context.getBeanFactory().registerSingleton("reactiveHttpClientJsonCodec", TestJsonCodecs.jsonCodec());
            context.getBeanFactory().registerSingleton("stalePoolDiagnostics", diagnostics);

            ReactiveHttpClientFactoryBean<StalePoolClient> factory = new ReactiveHttpClientFactoryBean<>();
            factory.setType(StalePoolClient.class);
            factory.setApplicationContext(context);
            StalePoolClient client = factory.getObject();
            return new ClientFixture(context, factory, client, connectionProvider(factory));
        }

        StalePoolClient client() {
            return client;
        }

        ConnectionProvider connectionProvider() {
            return connectionProvider;
        }

        String poolName() {
            return connectionProvider.name();
        }

        @Override
        public void close() {
            if (!closed) {
                closed = true;
                factory.destroy();
                context.close();
            }
        }

        private static ConnectionProvider connectionProvider(ReactiveHttpClientFactoryBean<?> factory) {
            try {
                Field field = ReactiveHttpClientFactoryBean.class.getDeclaredField("connectionProvider");
                field.setAccessible(true);
                return (ConnectionProvider) field.get(factory);
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException(error);
            }
        }
    }

    private static final class MeterFixture implements AutoCloseable {
        private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

        private MeterFixture() {
            Metrics.addRegistry(registry);
        }

        void awaitGauge(String name, String poolName, double expected) {
            await(() -> {
                io.micrometer.core.instrument.Gauge gauge = registry.find(name)
                        .tag("name", poolName)
                        .gauge();
                return gauge != null && gauge.value() == expected;
            }, name + " should converge to " + expected);
        }

        void awaitConverged(String poolName) {
            awaitGauge(ProtocolAwareConnectionPoolMeterRegistrar.ACTIVE_CONNECTIONS, poolName, 0);
            awaitGauge(ProtocolAwareConnectionPoolMeterRegistrar.PENDING_CONNECTIONS, poolName, 0);
            awaitGauge(ProtocolAwareConnectionPoolMeterRegistrar.TOTAL_CONNECTIONS, poolName, 1);
            awaitGauge(ProtocolAwareConnectionPoolMeterRegistrar.IDLE_CONNECTIONS, poolName, 1);
        }

        @Override
        public void close() {
            Metrics.removeRegistry(registry);
            registry.close();
        }
    }

    private static final class RawHttpPeer implements AutoCloseable {
        private final ServerSocket serverSocket;
        private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        private final AtomicBoolean running = new AtomicBoolean(true);
        private final AtomicInteger connectionSequence = new AtomicInteger();
        private final AtomicInteger retryResetDispatches = new AtomicInteger();
        private final AtomicBoolean resetNextConnectionBeforeRequest = new AtomicBoolean();
        private final List<WireRequest> requests = new CopyOnWriteArrayList<>();
        private final Map<Integer, Socket> connections = new ConcurrentHashMap<>();
        private final CountDownLatch resetRequest = new CountDownLatch(1);
        private final CountDownLatch resetRelease = new CountDownLatch(1);
        private final CountDownLatch holdRequest = new CountDownLatch(1);
        private final CountDownLatch holdRelease = new CountDownLatch(1);
        private final CountDownLatch preRequestReset = new CountDownLatch(1);

        private RawHttpPeer() {
            try {
                serverSocket = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"));
            } catch (IOException error) {
                throw new IllegalStateException("Failed to bind raw HTTP peer", error);
            }
            executor.submit(this::acceptConnections);
        }

        String baseUrl() {
            return "http://127.0.0.1:" + serverSocket.getLocalPort();
        }

        List<WireRequest> records() {
            return List.copyOf(requests);
        }

        List<WireRequest> records(String path) {
            return requests.stream().filter(request -> path.equals(request.path())).toList();
        }

        List<String> paths() {
            return requests.stream().map(WireRequest::path).toList();
        }

        int connectionCount() {
            return connectionSequence.get();
        }

        void resetNextConnectionBeforeRequest() {
            resetNextConnectionBeforeRequest.set(true);
        }

        void awaitPreRequestReset() {
            awaitLatch(preRequestReset, "pre-request reset");
        }

        void awaitResetRequest() {
            awaitLatch(resetRequest, "reset request");
        }

        void releaseReset() {
            resetRelease.countDown();
        }

        void awaitHoldRequest() {
            awaitLatch(holdRequest, "held request");
        }

        void releaseHold() {
            holdRelease.countDown();
        }

        void closeConnection(int connectionId) {
            Socket socket = connections.get(connectionId);
            assertThat(socket).as("open raw connection " + connectionId).isNotNull();
            closeSocket(socket, false);
            await(() -> !connections.containsKey(connectionId),
                    "raw peer should remove closed connection " + connectionId);
        }

        private void acceptConnections() {
            while (running.get()) {
                try {
                    Socket socket = serverSocket.accept();
                    int connectionId = connectionSequence.incrementAndGet();
                    if (resetNextConnectionBeforeRequest.compareAndSet(true, false)) {
                        closeSocket(socket, true);
                        preRequestReset.countDown();
                        continue;
                    }
                    connections.put(connectionId, socket);
                    executor.submit(() -> handleConnection(socket, connectionId));
                } catch (SocketException error) {
                    if (running.get()) {
                        throw new IllegalStateException("Raw HTTP accept failed", error);
                    }
                } catch (IOException error) {
                    throw new IllegalStateException("Raw HTTP accept failed", error);
                }
            }
        }

        private void handleConnection(Socket socket, int connectionId) {
            try (socket;
                 BufferedReader reader = new BufferedReader(
                         new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII))) {
                socket.setSoTimeout(5_000);
                OutputStream output = socket.getOutputStream();
                while (running.get() && !socket.isClosed()) {
                    String requestLine = reader.readLine();
                    if (requestLine == null) {
                        return;
                    }
                    String[] parts = requestLine.split(" ", 3);
                    if (parts.length != 3) {
                        return;
                    }
                    String line;
                    while ((line = reader.readLine()) != null && !line.isEmpty()) {
                        // Requests in this fixture have no body.
                    }
                    String path = parts[1];
                    requests.add(new WireRequest(connectionId, path));
                    if (!respond(path, socket, output)) {
                        return;
                    }
                }
            } catch (IOException ignored) {
                // FIN/RST and client disposal are deliberate fixture outcomes.
            } finally {
                connections.remove(connectionId, socket);
            }
        }

        private boolean respond(String path, Socket socket, OutputStream output) throws IOException {
            return switch (path) {
                case "/connection-close" -> {
                    write(output, response("closing", "Connection: close\r\n"));
                    yield false;
                }
                case "/fin-after-response" -> {
                    write(output, response("fin", ""));
                    socket.shutdownOutput();
                    yield false;
                }
                case "/idle" -> {
                    write(output, response("idle", "X-Idle: yes\r\n"));
                    yield true;
                }
                case "/reset-on-reuse" -> {
                    resetRequest.countDown();
                    awaitLatch(resetRelease, "reset release");
                    closeSocket(socket, true);
                    yield false;
                }
                case "/partial-body" -> {
                    write(output,
                            "HTTP/1.1 200 OK\r\n"
                                    + "Content-Length: 12\r\n"
                                    + "X-Stale-Response: partial\r\n\r\n"
                                    + "short");
                    socket.shutdownOutput();
                    yield false;
                }
                case "/retry-after-reset" -> {
                    if (retryResetDispatches.incrementAndGet() == 1) {
                        closeSocket(socket, true);
                        yield false;
                    }
                    write(output, response("retry-ok", "X-Retry: replacement\r\n"));
                    yield true;
                }
                case "/hold" -> {
                    holdRequest.countDown();
                    awaitLatch(holdRelease, "hold release");
                    if (!socket.isClosed()) {
                        write(output, response("held", ""));
                    }
                    yield true;
                }
                case "/probe" -> {
                    write(output, response("probe", "X-Probe: replacement\r\n"));
                    yield true;
                }
                default -> {
                    write(output,
                            "HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\n\r\n");
                    yield true;
                }
            };
        }

        private static String response(String body, String headers) {
            return "HTTP/1.1 200 OK\r\n"
                    + "Content-Length: " + body.getBytes(StandardCharsets.US_ASCII).length + "\r\n"
                    + headers
                    + "\r\n"
                    + body;
        }

        private static void write(OutputStream output, String response) throws IOException {
            output.write(response.getBytes(StandardCharsets.US_ASCII));
            output.flush();
        }

        private static void closeSocket(Socket socket, boolean reset) {
            try {
                if (reset) {
                    socket.setSoLinger(true, 0);
                }
                socket.close();
            } catch (IOException ignored) {
                // Fixture cleanup is best effort after the intended close signal.
            }
        }

        private static void awaitLatch(CountDownLatch latch, String description) {
            try {
                assertThat(latch.await(5, TimeUnit.SECONDS)).as(description).isTrue();
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(error);
            }
        }

        @Override
        public void close() {
            running.set(false);
            resetRelease.countDown();
            holdRelease.countDown();
            connections.values().forEach(socket -> closeSocket(socket, false));
            try {
                serverSocket.close();
            } catch (IOException ignored) {
                // Closing an already closed fixture is harmless.
            }
            executor.close();
        }
    }

    private record WireRequest(int connectionId, String path) {
    }
}
