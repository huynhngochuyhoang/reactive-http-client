package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.annotation.*;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientFailureStage;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientObserver;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientObserverEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.Exceptions;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.PrematureCloseException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class HttpResponseFramingContractTest {

    private static final Duration CALL_TIMEOUT = Duration.ofSeconds(5);

    @Test
    void informationalResponseUsesTheStatusExposedByReactorNetty() {
        FramingDiagnostics diagnostics = new FramingDiagnostics();
        try (RawHttpPeer peer = new RawHttpPeer();
             ClientFixture fixture = ClientFixture.create(peer.baseUrl(), diagnostics)) {
            ResponseEntity<String> response = fixture.client.informational().block(CALL_TIMEOUT);

            assertThat(response).isNotNull();
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.EARLY_HINTS);
            assertThat(response.getHeaders().getFirst("Link")).isEqualTo("</ignored>; rel=preload");
            assertThat(response.getHeaders().get("X-Final-Status")).isNull();
            assertThat(response.getBody()).isNull();
            assertThat(diagnostics.observerEvents).singleElement().satisfies(event -> {
                assertThat(event.getStatusCode()).isEqualTo(103);
                assertThat(event.getError()).isNull();
                assertThat(event.getFailureStage()).isNull();
            });
            assertThat(diagnostics.lifecycleContexts).singleElement().satisfies(context -> {
                assertThat(context.statusCode()).isEqualTo(103);
                assertThat(context.error()).isNull();
            });
            assertThat(diagnostics.exchangeLogs).singleElement().satisfies(log -> {
                assertThat(log.responseStatus()).isEqualTo(103);
                assertThat(log.responseHeaders()).containsEntry("Link", List.of("</ignored>; rel=preload"));
            });
        }
    }

    @Test
    void unexpectedBodilessBytesAreDrainedBeforeHeadAndProbeReuse() {
        FramingDiagnostics diagnostics = new FramingDiagnostics();
        try (RawHttpPeer peer = new RawHttpPeer();
             ClientFixture fixture = ClientFixture.create(peer.baseUrl(), diagnostics)) {
            fixture.client.unexpectedVoid().block(CALL_TIMEOUT);

            ResponseEntity<Void> head = fixture.client.head().block(CALL_TIMEOUT);
            assertThat(head).isNotNull();
            assertThat(head.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(head.getHeaders().getFirst("X-Head")).isEqualTo("yes");
            assertThat(head.getBody()).isNull();
            assertThat(fixture.client.probe().block(CALL_TIMEOUT)).isEqualTo("probe");

            assertThat(peer.requests()).extracting(WireRequest::path)
                    .containsExactly("/unexpected-void", "/head", "/probe");
            assertThat(peer.requests()).extracting(WireRequest::connectionId).containsOnly(1);
            assertThat(diagnostics.observerEvents).extracting(HttpClientObserverEvent::getResponseBytes)
                    .containsExactly(10L, 9L, 5L);
        }
    }

    @ParameterizedTest
    @EnumSource(MalformedFraming.class)
    void malformedFramingQuarantinesTheConnectionBeforeTheNextRequest(MalformedFraming framing) {
        FramingDiagnostics diagnostics = new FramingDiagnostics();
        try (RawHttpPeer peer = new RawHttpPeer();
             ClientFixture fixture = ClientFixture.create(peer.baseUrl(), diagnostics)) {
            Throwable failure = catchThrowable(() -> fixture.client.malformed(framing.path.substring(1))
                    .block(CALL_TIMEOUT));

            assertThat(failure).isNotNull();
            assertThat(fixture.client.probe().block(CALL_TIMEOUT)).isEqualTo("probe");
            assertThat(peer.requests()).extracting(WireRequest::path)
                    .containsExactly(framing.path, "/probe");
            assertThat(peer.requests().get(1).connectionId())
                    .isNotEqualTo(peer.requests().get(0).connectionId());

            assertThat(diagnostics.observerEvents).hasSize(2);
            HttpClientObserverEvent failed = diagnostics.observerEvents.get(0);
            Throwable terminalFailure = Exceptions.unwrap(failure);
            assertThat(failed.getStatusCode()).isEqualTo(framing.statusCode);
            assertThat(failed.getError()).isSameAs(terminalFailure);
            assertThat(containsCauseOfType(terminalFailure, framing.failureType)).isTrue();
            assertThat(failed.getFailureStage()).isEqualTo(framing.failureStage);
            assertThat(failed.getResponseBytes()).isEqualTo(framing.responseBytes);
            assertThat(diagnostics.lifecycleContexts.get(0).statusCode()).isEqualTo(framing.statusCode);
            assertThat(diagnostics.lifecycleContexts.get(0).error()).isSameAs(terminalFailure);
            assertThat(diagnostics.lifecycleContexts.get(0).failureStage()).isEqualTo(framing.failureStage);
            assertThat(diagnostics.exchangeLogs.get(0).responseStatus()).isEqualTo(framing.statusCode);
            assertThat(diagnostics.exchangeLogs.get(0).error()).isSameAs(terminalFailure);
            assertThat(diagnostics.exchangeLogs.get(0).failureStage()).isEqualTo(framing.failureStage);
        }
    }

    @Test
    void closeDelimitedBodyCompletesAndForcesReplacementCapacity() {
        try (RawHttpPeer peer = new RawHttpPeer();
             ClientFixture fixture = ClientFixture.create(peer.baseUrl(), new FramingDiagnostics())) {
            assertThat(fixture.client.closeDelimited().block(CALL_TIMEOUT)).isEqualTo("close-body");
            assertThat(fixture.client.probe().block(CALL_TIMEOUT)).isEqualTo("probe");

            assertThat(peer.requests()).extracting(WireRequest::path)
                    .containsExactly("/close-delimited", "/probe");
            assertThat(peer.requests().get(1).connectionId())
                    .isNotEqualTo(peer.requests().get(0).connectionId());
        }
    }

    private enum MalformedFraming {
        INVALID_CONTENT_LENGTH("/invalid-content-length", null, null, -1L, IllegalArgumentException.class),
        CONFLICTING_CONTENT_LENGTH("/conflicting-content-length", null, null, -1L, IllegalArgumentException.class),
        INVALID_CHUNK("/invalid-chunk", 200, null, -1L, NumberFormatException.class),
        TRUNCATED_CONTENT_LENGTH(
                "/truncated-content-length", 200,
                HttpClientFailureStage.RESPONSE_BODY, 12L, PrematureCloseException.class);

        private final String path;
        private final Integer statusCode;
        private final HttpClientFailureStage failureStage;
        private final long responseBytes;
        private final Class<? extends Throwable> failureType;

        MalformedFraming(String path,
                         Integer statusCode,
                         HttpClientFailureStage failureStage,
                         long responseBytes,
                         Class<? extends Throwable> failureType) {
            this.path = path;
            this.statusCode = statusCode;
            this.failureStage = failureStage;
            this.responseBytes = responseBytes;
            this.failureType = failureType;
        }
    }

    private static boolean containsCauseOfType(Throwable error, Class<? extends Throwable> type) {
        Throwable current = error;
        for (int depth = 0; current != null && depth < 16; depth++) {
            if (type.isInstance(current)) {
                return true;
            }
            Throwable cause = current.getCause();
            current = cause != current ? cause : null;
        }
        return false;
    }

    @LogHttpExchange(logger = FramingDiagnostics.class)
    @ReactiveHttpClient(name = "framing-client")
    interface FramingClient {
        @GET("/informational")
        Mono<ResponseEntity<String>> informational();

        @GET("/unexpected-void")
        Mono<Void> unexpectedVoid();

        @HEAD("/head")
        Mono<ResponseEntity<Void>> head();

        @GET("/probe")
        Mono<String> probe();

        @GET("/{path}")
        Mono<String> malformed(@PathVar("path") String path);

        @GET("/close-delimited")
        Mono<String> closeDelimited();
    }

    private static final class ClientFixture implements AutoCloseable {
        private final StaticApplicationContext context;
        private final ReactiveHttpClientFactoryBean<FramingClient> factory;
        private final FramingClient client;

        private ClientFixture(StaticApplicationContext context,
                              ReactiveHttpClientFactoryBean<FramingClient> factory,
                              FramingClient client) {
            this.context = context;
            this.factory = factory;
            this.client = client;
        }

        private static ClientFixture create(String baseUrl, FramingDiagnostics diagnostics) {
            StaticApplicationContext context = new StaticApplicationContext();
            ReactiveHttpClientProperties properties = new ReactiveHttpClientProperties();
            ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();
            config.setBaseUrl(baseUrl);
            config.setRequestTimeoutMs(2_000);
            ReactiveHttpClientProperties.ConnectionPoolConfig pool =
                    new ReactiveHttpClientProperties.ConnectionPoolConfig();
            pool.setMaxConnections(1);
            config.setPool(pool);
            properties.getClients().put("framing-client", config);

            context.getBeanFactory().registerSingleton("reactiveHttpClientProperties", properties);
            context.getBeanFactory().registerSingleton("starterWebClientBuilder", WebClient.builder());
            context.getBeanFactory().registerSingleton("reactiveHttpClientJsonCodec", TestJsonCodecs.jsonCodec());
            context.getBeanFactory().registerSingleton("framingDiagnostics", diagnostics);

            ReactiveHttpClientFactoryBean<FramingClient> factory = new ReactiveHttpClientFactoryBean<>();
            factory.setType(FramingClient.class);
            factory.setApplicationContext(context);
            return new ClientFixture(context, factory, factory.getObject());
        }

        @Override
        public void close() {
            factory.destroy();
            context.close();
        }
    }

    private static final class FramingDiagnostics
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
    }

    private static final class RawHttpPeer implements AutoCloseable {
        private final ServerSocket serverSocket;
        private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        private final AtomicBoolean running = new AtomicBoolean(true);
        private final AtomicInteger connectionSequence = new AtomicInteger();
        private final List<WireRequest> requests = new CopyOnWriteArrayList<>();

        private RawHttpPeer() {
            try {
                serverSocket = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"));
            } catch (IOException ex) {
                throw new IllegalStateException("Failed to bind raw HTTP peer", ex);
            }
            executor.submit(this::acceptConnections);
        }

        private String baseUrl() {
            return "http://127.0.0.1:" + serverSocket.getLocalPort();
        }

        private List<WireRequest> requests() {
            return List.copyOf(requests);
        }

        private void acceptConnections() {
            while (running.get()) {
                try {
                    Socket socket = serverSocket.accept();
                    int connectionId = connectionSequence.incrementAndGet();
                    executor.submit(() -> handleConnection(socket, connectionId));
                } catch (SocketException ex) {
                    if (running.get()) {
                        throw new IllegalStateException("Raw HTTP accept failed", ex);
                    }
                } catch (IOException ex) {
                    throw new IllegalStateException("Raw HTTP accept failed", ex);
                }
            }
        }

        private void handleConnection(Socket socket, int connectionId) {
            try (socket;
                 BufferedReader reader = new BufferedReader(
                         new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII))) {
                socket.setSoTimeout(5_000);
                OutputStream output = socket.getOutputStream();
                boolean keepAlive = true;
                while (keepAlive) {
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
                    requests.add(new WireRequest(connectionId, parts[0], parts[1]));
                    keepAlive = respond(parts[1], output);
                }
            } catch (IOException ignored) {
                // Client-side parser failures deliberately close malformed sockets.
            }
        }

        private boolean respond(String path, OutputStream output) throws IOException {
            return switch (path) {
                case "/informational" -> {
                    write(output,
                            "HTTP/1.1 103 Early Hints\r\n"
                                    + "Link: </ignored>; rel=preload\r\n\r\n"
                                    + "HTTP/1.1 200 OK\r\n"
                                    + "Content-Length: 5\r\n"
                                    + "X-Final-Status: 200\r\n\r\n"
                                    + "final");
                    yield true;
                }
                case "/unexpected-void" -> {
                    write(output, response("200 OK", "unexpected", "X-Bodiless: yes\r\n"));
                    yield true;
                }
                case "/head" -> {
                    write(output,
                            "HTTP/1.1 200 OK\r\n"
                                    + "Content-Length: 9\r\n"
                                    + "X-Head: yes\r\n\r\n");
                    yield true;
                }
                case "/probe" -> {
                    write(output, response("200 OK", "probe", "X-Probe: yes\r\n"));
                    yield true;
                }
                case "/invalid-content-length" -> {
                    write(output,
                            "HTTP/1.1 200 OK\r\n"
                                    + "Content-Length: nope\r\n\r\n"
                                    + "body");
                    yield false;
                }
                case "/conflicting-content-length" -> {
                    write(output,
                            "HTTP/1.1 200 OK\r\n"
                                    + "Content-Length: 4\r\n"
                                    + "Content-Length: 5\r\n\r\n"
                                    + "body!");
                    yield false;
                }
                case "/invalid-chunk" -> {
                    write(output,
                            "HTTP/1.1 200 OK\r\n"
                                    + "Transfer-Encoding: chunked\r\n\r\n"
                                    + "Z\r\nbad\r\n0\r\n\r\n");
                    yield false;
                }
                case "/truncated-content-length" -> {
                    write(output,
                            "HTTP/1.1 200 OK\r\n"
                                    + "Content-Length: 12\r\n\r\n"
                                    + "short");
                    yield false;
                }
                case "/close-delimited" -> {
                    write(output,
                            "HTTP/1.1 200 OK\r\n"
                                    + "Connection: close\r\n\r\n"
                                    + "close-body");
                    yield false;
                }
                default -> {
                    write(output, response("404 Not Found", "", ""));
                    yield true;
                }
            };
        }

        private static String response(String status, String body, String headers) {
            return "HTTP/1.1 " + status + "\r\n"
                    + "Content-Length: " + body.getBytes(StandardCharsets.US_ASCII).length + "\r\n"
                    + headers
                    + "\r\n"
                    + body;
        }

        private static void write(OutputStream output, String response) throws IOException {
            output.write(response.getBytes(StandardCharsets.US_ASCII));
            output.flush();
        }

        @Override
        public void close() {
            running.set(false);
            try {
                serverSocket.close();
            } catch (IOException ignored) {
                // Closing an already closed fixture is harmless.
            }
            executor.close();
        }
    }

    private record WireRequest(int connectionId, String method, String path) {
    }
}
