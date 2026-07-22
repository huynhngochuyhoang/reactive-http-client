package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.annotation.Body;
import io.github.huynhngochuyhoang.httpstarter.annotation.GET;
import io.github.huynhngochuyhoang.httpstarter.annotation.POST;
import io.github.huynhngochuyhoang.httpstarter.annotation.ReactiveHttpClient;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import io.github.huynhngochuyhoang.httpstarter.exception.HttpClientException;
import io.github.huynhngochuyhoang.httpstarter.exception.RemoteServiceException;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientObserver;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientObserverEvent;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.GZIPOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class ReactiveHttpClientCompressionContractTest {

    private static final Duration CALL_TIMEOUT = Duration.ofSeconds(5);

    @Test
    void responseCompressionIsOptInAndIdentityFallbackStillDecodes() {
        try (CompressionServer server = new CompressionServer()) {
            try (ClientFixture disabled = ClientFixture.create(server, false, false, null)) {
                assertThat(disabled.client().json().block(CALL_TIMEOUT))
                        .isEqualTo(new Payload("json"));
                assertThat(server.lastRecord()).satisfies(record -> {
                    assertThat(record.acceptEncoding()).isNull();
                    assertThat(record.gzipResponse()).isFalse();
                });
            }

            server.clearRecords();
            try (ClientFixture enabled = ClientFixture.create(server, true, false, null)) {
                assertThat(enabled.client().json().block(CALL_TIMEOUT))
                        .isEqualTo(new Payload("json"));
                assertThat(server.lastRecord()).satisfies(record -> {
                    assertThat(record.acceptEncoding()).contains("gzip");
                    assertThat(record.gzipResponse()).isTrue();
                });

                assertThat(enabled.client().identity().block(CALL_TIMEOUT)).isEqualTo("identity");
                assertThat(server.lastRecord().gzipResponse()).isFalse();
            }
        }
    }

    @Test
    void enabledCompressionHandlesEmptyEntityAndStreamingResponses() {
        try (CompressionServer server = new CompressionServer();
             ClientFixture fixture = ClientFixture.create(server, true, false, null)) {
            CompressionClient client = fixture.client();

            assertThat(client.empty().block(CALL_TIMEOUT)).isNull();

            ResponseEntity<Payload> entity = client.entity().block(CALL_TIMEOUT);
            assertThat(entity).isNotNull();
            assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(entity.getBody()).isEqualTo(new Payload("entity"));

            String direct = client.directStream()
                    .map(ReactiveHttpClientCompressionContractTest::readAndRelease)
                    .reduce("", String::concat)
                    .block(CALL_TIMEOUT);
            assertThat(direct).isEqualTo("firstsecond");

            ResponseEntity<Flux<DataBuffer>> envelope = client.streamingEntity().block(CALL_TIMEOUT);
            assertThat(envelope).isNotNull();
            assertThat(envelope.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(envelope.getBody()).isNotNull();
            String envelopeBody = Mono.delay(Duration.ofMillis(50))
                    .thenMany(envelope.getBody())
                    .map(ReactiveHttpClientCompressionContractTest::readAndRelease)
                    .reduce("", String::concat)
                    .block(CALL_TIMEOUT);
            assertThat(envelopeBody).isEqualTo("firstsecond");

            ResponseEntity<Flux<DataBuffer>> cancelled = client.streamingEntity().block(CALL_TIMEOUT);
            assertThat(cancelled).isNotNull();
            assertThat(cancelled.getBody()).isNotNull();
            assertThat(cancelled.getBody().take(1)
                    .map(ReactiveHttpClientCompressionContractTest::readAndRelease)
                    .blockLast(CALL_TIMEOUT)).isNotEmpty();
            assertThat(client.probe().block(CALL_TIMEOUT)).isEqualTo("probe");
        }
    }

    @Test
    void compressedErrorsAreDecodedBeforeStatusMapping() {
        try (CompressionServer server = new CompressionServer();
             ClientFixture fixture = ClientFixture.create(server, true, false, null)) {
            CompressionClient client = fixture.client();

            assertThat(catchThrowable(() -> client.clientError().block(CALL_TIMEOUT)))
                    .isInstanceOf(HttpClientException.class)
                    .satisfies(error -> {
                        HttpClientException exception = (HttpClientException) error;
                        assertThat(exception.getStatusCode()).isEqualTo(422);
                        assertThat(exception.getResponseBody()).isEqualTo("invalid");
                    });
            assertThat(catchThrowable(() -> client.serverError().block(CALL_TIMEOUT)))
                    .isInstanceOf(RemoteServiceException.class)
                    .satisfies(error -> {
                        RemoteServiceException exception = (RemoteServiceException) error;
                        assertThat(exception.getStatusCode()).isEqualTo(503);
                        assertThat(exception.getResponseBody()).isEqualTo("unavailable");
                    });
            assertThat(client.probe().block(CALL_TIMEOUT)).isEqualTo("probe");
        }
    }

    @Test
    void decodedUnaryBodiesCannotBypassAggregateLimitThroughCompression() {
        try (CompressionServer server = new CompressionServer();
             ClientFixture fixture = ClientFixture.create(server, true, false, null, 1)) {
            CompressionClient client = fixture.client();

            assertThat(catchThrowable(() -> client.oversizedJson().block(CALL_TIMEOUT)))
                    .isInstanceOf(DataBufferLimitException.class);
            assertThat(catchThrowable(() -> client.oversizedEntity().block(CALL_TIMEOUT)))
                    .isInstanceOf(DataBufferLimitException.class);
            assertThat(server.records().stream()
                    .filter(record -> record.path().startsWith("/oversized-"))
                    .toList())
                    .hasSize(2)
                    .allSatisfy(record -> assertThat(record.wireBytes()).isLessThan(16 * 1024));
            assertThat(client.probe().block(CALL_TIMEOUT)).isEqualTo("probe");
        }
    }

    @Test
    void compressedErrorAndBodilessBodiesUseIndependentBoundedDrainPolicies() {
        try (CompressionServer server = new CompressionServer();
             ClientFixture fixture = ClientFixture.create(server, true, false, null, 1)) {
            CompressionClient client = fixture.client();

            assertThat(catchThrowable(() -> client.oversizedProblem().block(CALL_TIMEOUT)))
                    .isInstanceOf(RemoteServiceException.class);
            ErrorResponseContext captured = fixture.lastErrorResponse();
            assertThat(captured).isNotNull();
            assertThat(captured.responseBodyTruncated()).isTrue();
            assertThat(captured.retainedResponseBodyBytes()).isEqualTo(64 * 1024);
            String problemConnection = server.lastRecord().connectionId();
            assertThat(client.probe().block(CALL_TIMEOUT)).isEqualTo("probe");
            assertThat(server.lastRecord().connectionId()).isEqualTo(problemConnection);

            client.unexpectedBodiless().block(CALL_TIMEOUT);
            String bodilessConnection = server.lastRecord().connectionId();
            assertThat(client.probe().block(CALL_TIMEOUT)).isEqualTo("probe");
            assertThat(server.lastRecord().connectionId()).isEqualTo(bodilessConnection);
        }
    }

    @Test
    void compressedStreamingBodiesRemainIncrementalAndCallerOwned() {
        try (CompressionServer server = new CompressionServer();
             ClientFixture fixture = ClientFixture.create(server, true, false, null, 1)) {
            CompressionClient client = fixture.client();

            Integer decodedBytes = client.oversizedStream()
                    .map(buffer -> {
                        try {
                            return buffer.readableByteCount();
                        } finally {
                            DataBufferUtils.release(buffer);
                        }
                    })
                    .reduce(0, Integer::sum)
                    .block(CALL_TIMEOUT);
            assertThat(decodedBytes).isGreaterThan(1024 * 1024);

            ResponseEntity<Flux<DataBuffer>> envelope = client.oversizedStreamingEntity()
                    .block(CALL_TIMEOUT);
            assertThat(envelope).isNotNull();
            assertThat(envelope.getBody()).isNotNull();
            assertThat(envelope.getBody()
                    .take(1)
                    .map(ReactiveHttpClientCompressionContractTest::readAndRelease)
                    .blockLast(CALL_TIMEOUT)).isNotEmpty();
            assertThat(client.probe().block(CALL_TIMEOUT)).isEqualTo("probe");
        }
    }

    @Test
    void truncatedAndCorruptGzipTerminateCleanlyAndLaterRequestsStillComplete() {
        try (CompressionServer server = new CompressionServer();
             ClientFixture fixture = ClientFixture.create(server, true, false, null, 1)) {
            CompressionClient client = fixture.client();

            String truncated = client.truncatedGzip().block(CALL_TIMEOUT);
            assertThat(truncated != null ? truncated.length() : 0)
                    .isLessThan(CompressionServer.OVERSIZED_VALUE.length());
            assertThat(client.probe().block(CALL_TIMEOUT)).isEqualTo("probe");
            assertThat(catchThrowable(() -> client.corruptGzip().block(CALL_TIMEOUT))).isNotNull();
            assertThat(client.probe().block(CALL_TIMEOUT)).isEqualTo("probe");
        }
    }

    @Test
    void enabledCompressionRejectsApplicationAcceptEncodingBeforeTheWire() {
        try (CompressionServer server = new CompressionServer();
             ClientFixture fixture = ClientFixture.create(server, true, false, "br")) {
            assertThat(catchThrowable(() -> fixture.client().json().block(CALL_TIMEOUT)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Accept-Encoding")
                    .hasMessageContaining("compression-enabled=true");
            assertThat(server.records()).isEmpty();
        }
    }

    @Test
    void diagnosticsUseAdvertisedRepresentationLengthWithoutAggregatingStreams() {
        try (CompressionServer server = new CompressionServer();
             ClientFixture fixture = ClientFixture.create(server, true, true, null)) {
            CompressionClient client = fixture.client();
            RecordingDiagnostics diagnostics = fixture.diagnostics();

            assertThat(client.identity().block(CALL_TIMEOUT)).isEqualTo("identity");
            assertThat(diagnostics.lastObserverEvent().getResponseBytes()).isEqualTo(8L);
            assertThat(diagnostics.lastExchangeLog().responseHeaders()).containsEntry(
                    HttpHeaders.CONTENT_LENGTH, List.of("8"));

            diagnostics.clear();
            assertThat(client.echo("request-body").block(CALL_TIMEOUT)).isEqualTo("request-body");
            assertThat(server.lastRecord()).satisfies(record -> {
                assertThat(record.requestContentEncoding()).isNull();
                assertThat(record.requestBody()).isEqualTo("request-body");
            });
            assertThat(diagnostics.lastObserverEvent().getRequestBytes())
                    .isEqualTo("request-body".getBytes(StandardCharsets.UTF_8).length);

            diagnostics.clear();
            assertThat(client.entity().block(CALL_TIMEOUT)).isNotNull();
            RequestRecord entityRecord = server.lastRecord();
            HttpClientObserverEvent entityEvent = diagnostics.lastObserverEvent();
            HttpExchangeLogContext entityLog = diagnostics.lastExchangeLog();
            assertReportedLengthMatchesHeaders(entityEvent, entityLog.responseHeaders());
            assertThat(entityRecord.wireBytes()).isPositive();
            assertThat(entityEvent.getResponseBytes()).isEqualTo(HttpClientObserverEvent.UNKNOWN_SIZE);
            assertThat(entityLog.responseHeaders().keySet())
                    .noneMatch(HttpHeaders.CONTENT_LENGTH::equalsIgnoreCase);
            assertThat(entityLog.responseHeaders().keySet())
                    .noneMatch(HttpHeaders.CONTENT_ENCODING::equalsIgnoreCase);
            assertThat(entityEvent.getRequestBytes()).isZero();
            assertThat(diagnostics.lastLifecycleContext().statusCode()).isEqualTo(200);

            diagnostics.clear();
            String stream = client.chunkedStream()
                    .map(ReactiveHttpClientCompressionContractTest::readAndRelease)
                    .reduce("", String::concat)
                    .block(CALL_TIMEOUT);
            assertThat(stream).isEqualTo("firstsecond");
            assertThat(diagnostics.lastObserverEvent().getResponseBytes())
                    .isEqualTo(HttpClientObserverEvent.UNKNOWN_SIZE);
            assertThat(diagnostics.lastObserverEvent().getResponseBody()).isNull();
            assertThat(diagnostics.lastExchangeLog().responseBody()).isNull();
            assertThat(diagnostics.lastLifecycleContext().statusCode()).isEqualTo(200);
        }
    }

    private static void assertReportedLengthMatchesHeaders(
            HttpClientObserverEvent event,
            Map<String, List<String>> responseHeaders) {
        String contentLength = responseHeaders.entrySet().stream()
                .filter(entry -> HttpHeaders.CONTENT_LENGTH.equalsIgnoreCase(entry.getKey()))
                .map(Map.Entry::getValue)
                .filter(values -> values != null && !values.isEmpty())
                .map(values -> values.get(0))
                .findFirst()
                .orElse(null);
        if (contentLength == null) {
            assertThat(event.getResponseBytes()).isEqualTo(HttpClientObserverEvent.UNKNOWN_SIZE);
        } else {
            assertThat(event.getResponseBytes()).isEqualTo(Long.parseLong(contentLength));
        }
    }

    private static String readAndRelease(DataBuffer buffer) {
        try {
            return buffer.toString(StandardCharsets.UTF_8);
        } finally {
            DataBufferUtils.release(buffer);
        }
    }

    @ReactiveHttpClient(name = "compression-contract")
    interface CompressionClient {
        @GET("/json")
        Mono<Payload> json();

        @GET("/identity")
        Mono<String> identity();

        @POST("/echo")
        Mono<String> echo(@Body String body);

        @GET("/empty")
        Mono<Void> empty();

        @GET("/entity")
        Mono<ResponseEntity<Payload>> entity();

        @GET("/direct-stream")
        Flux<DataBuffer> directStream();

        @GET("/stream-entity")
        Mono<ResponseEntity<Flux<DataBuffer>>> streamingEntity();

        @GET("/chunked-stream")
        Flux<DataBuffer> chunkedStream();

        @GET("/client-error")
        Mono<String> clientError();

        @GET("/server-error")
        Mono<String> serverError();

        @GET("/oversized-json")
        Mono<Payload> oversizedJson();

        @GET("/oversized-entity")
        Mono<ResponseEntity<Payload>> oversizedEntity();

        @GET("/oversized-problem")
        Mono<String> oversizedProblem();

        @GET("/unexpected-bodiless")
        Mono<Void> unexpectedBodiless();

        @GET("/oversized-stream")
        Flux<DataBuffer> oversizedStream();

        @GET("/oversized-stream")
        Mono<ResponseEntity<Flux<DataBuffer>>> oversizedStreamingEntity();

        @GET("/truncated-gzip")
        Mono<String> truncatedGzip();

        @GET("/corrupt-gzip")
        Mono<String> corruptGzip();

        @GET("/probe")
        Mono<String> probe();
    }

    record Payload(String value) {
    }

    private static final class ClientFixture implements AutoCloseable {
        private final StaticApplicationContext context;
        private final ReactiveHttpClientFactoryBean<CompressionClient> factory;
        private final CompressionClient client;
        private final RecordingDiagnostics diagnostics;
        private final AtomicReference<ErrorResponseContext> lastErrorResponse;
        private boolean closed;

        private ClientFixture(StaticApplicationContext context,
                              ReactiveHttpClientFactoryBean<CompressionClient> factory,
                              CompressionClient client,
                              RecordingDiagnostics diagnostics,
                              AtomicReference<ErrorResponseContext> lastErrorResponse) {
            this.context = context;
            this.factory = factory;
            this.client = client;
            this.diagnostics = diagnostics;
            this.lastErrorResponse = lastErrorResponse;
        }

        static ClientFixture create(CompressionServer server,
                                    boolean compressionEnabled,
                                    boolean diagnosticsEnabled,
                                    String defaultAcceptEncoding) {
            return create(server, compressionEnabled, diagnosticsEnabled, defaultAcceptEncoding, 2);
        }

        static ClientFixture create(CompressionServer server,
                                    boolean compressionEnabled,
                                    boolean diagnosticsEnabled,
                                    String defaultAcceptEncoding,
                                    int codecMaxInMemorySizeMb) {
            StaticApplicationContext context = new StaticApplicationContext();
            ReactiveHttpClientProperties properties = new ReactiveHttpClientProperties();
            ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();
            config.setBaseUrl("http://127.0.0.1:" + server.port());
            config.setCompressionEnabled(compressionEnabled);
            config.setCodecMaxInMemorySizeMb(codecMaxInMemorySizeMb);
            ReactiveHttpClientProperties.ConnectionPoolConfig pool =
                    new ReactiveHttpClientProperties.ConnectionPoolConfig();
            pool.setMaxConnections(1);
            config.setPool(pool);
            if (defaultAcceptEncoding != null) {
                config.getDefaultHeaders().put(HttpHeaders.ACCEPT_ENCODING, defaultAcceptEncoding);
            }
            RecordingDiagnostics diagnostics = diagnosticsEnabled ? new RecordingDiagnostics() : null;
            if (diagnostics != null) {
                config.setLogExchange(true);
                context.getBeanFactory().registerSingleton("compressionObserver", diagnostics.observer());
                context.getBeanFactory().registerSingleton("compressionLifecycleHook", diagnostics.lifecycleHook());
                context.getBeanFactory().registerSingleton("compressionExchangeLogger", diagnostics.exchangeLogger());
            }
            AtomicReference<ErrorResponseContext> lastErrorResponse = new AtomicReference<>();
            ErrorResponseMapper capturingMapper = errorContext -> {
                lastErrorResponse.set(errorContext);
                return Optional.empty();
            };
            context.getBeanFactory().registerSingleton(
                    "defaultErrorDecoder", new DefaultErrorDecoder(null, List.of(capturingMapper)));
            properties.getClients().put("compression-contract", config);

            context.getBeanFactory().registerSingleton("reactiveHttpClientProperties", properties);
            context.getBeanFactory().registerSingleton("starterWebClientBuilder", WebClient.builder());
            context.getBeanFactory().registerSingleton("reactiveHttpClientJsonCodec", TestJsonCodecs.jsonCodec());

            ReactiveHttpClientFactoryBean<CompressionClient> factory = new ReactiveHttpClientFactoryBean<>();
            factory.setType(CompressionClient.class);
            factory.setApplicationContext(context);
            return new ClientFixture(
                    context, factory, factory.getObject(), diagnostics, lastErrorResponse);
        }

        CompressionClient client() {
            return client;
        }

        RecordingDiagnostics diagnostics() {
            return diagnostics;
        }

        ErrorResponseContext lastErrorResponse() {
            return lastErrorResponse.get();
        }

        @Override
        public void close() {
            if (!closed) {
                closed = true;
                factory.destroy();
                context.close();
            }
        }
    }

    private static final class RecordingDiagnostics {
        private final List<HttpClientObserverEvent> observerEvents = new CopyOnWriteArrayList<>();
        private final List<HttpExchangeLogContext> exchangeLogs = new CopyOnWriteArrayList<>();
        private final List<ReactiveHttpClientLifecycleContext> lifecycleContexts = new CopyOnWriteArrayList<>();
        private final RecordingExchangeLogger exchangeLogger = new RecordingExchangeLogger(exchangeLogs);
        private final HttpClientObserver observer = observerEvents::add;
        private final ReactiveHttpClientLifecycleHook lifecycleHook = new ReactiveHttpClientLifecycleHook() {
            @Override
            public void onSuccess(ReactiveHttpClientLifecycleContext context) {
                lifecycleContexts.add(context);
            }
        };

        HttpClientObserver observer() {
            return observer;
        }

        ReactiveHttpClientLifecycleHook lifecycleHook() {
            return lifecycleHook;
        }

        DefaultHttpExchangeLogger exchangeLogger() {
            return exchangeLogger;
        }

        HttpClientObserverEvent lastObserverEvent() {
            return observerEvents.get(observerEvents.size() - 1);
        }

        HttpExchangeLogContext lastExchangeLog() {
            return exchangeLogs.get(exchangeLogs.size() - 1);
        }

        ReactiveHttpClientLifecycleContext lastLifecycleContext() {
            return lifecycleContexts.get(lifecycleContexts.size() - 1);
        }

        void clear() {
            observerEvents.clear();
            exchangeLogs.clear();
            lifecycleContexts.clear();
        }
    }

    private static final class RecordingExchangeLogger extends DefaultHttpExchangeLogger {
        private final List<HttpExchangeLogContext> contexts;

        private RecordingExchangeLogger(List<HttpExchangeLogContext> contexts) {
            this.contexts = contexts;
        }

        @Override
        public void log(HttpExchangeLogContext context) {
            contexts.add(context);
        }
    }

    private static final class CompressionServer implements AutoCloseable {
        private static final byte[] STREAM_BODY = "firstsecond".getBytes(StandardCharsets.UTF_8);
        private static final String OVERSIZED_VALUE = "x".repeat(1024 * 1024 + 128);
        private static final byte[] OVERSIZED_BODY = OVERSIZED_VALUE.getBytes(StandardCharsets.UTF_8);
        private final List<RequestRecord> records = new CopyOnWriteArrayList<>();
        private final DisposableServer server;

        private CompressionServer() {
            this.server = HttpServer.create()
                    .port(0)
                    .handle((request, response) -> {
                        String path = request.path().startsWith("/")
                                ? request.path()
                                : "/" + request.path();
                        String acceptEncoding = request.requestHeaders().get(HttpHeaders.ACCEPT_ENCODING);
                        String requestContentEncoding = request.requestHeaders().get(HttpHeaders.CONTENT_ENCODING);
                        boolean acceptsGzip = acceptEncoding != null
                                && acceptEncoding.toLowerCase().contains("gzip");
                        AtomicReference<String> connectionId = new AtomicReference<>();
                        request.withConnection(connection ->
                                connectionId.set(connection.channel().id().asLongText()));
                        return request.receive().aggregate().asString(StandardCharsets.UTF_8)
                                .defaultIfEmpty("")
                                .flatMap(requestBody -> respond(path, acceptsGzip, requestBody, response)
                                        .doOnSubscribe(ignored -> records.add(new RequestRecord(
                                                path,
                                                acceptEncoding,
                                                requestContentEncoding,
                                                requestBody,
                                                usesGzip(path, acceptsGzip),
                                                wireBytes(path, acceptsGzip, requestBody),
                                                connectionId.get()))));
                    })
                    .bindNow();
        }

        int port() {
            return server.port();
        }

        List<RequestRecord> records() {
            return List.copyOf(records);
        }

        RequestRecord lastRecord() {
            return records.get(records.size() - 1);
        }

        void clearRecords() {
            records.clear();
        }

        @Override
        public void close() {
            server.disposeNow(CALL_TIMEOUT);
        }

        private Mono<Void> respond(String path,
                                   boolean acceptsGzip,
                                   String requestBody,
                                   reactor.netty.http.server.HttpServerResponse response) {
            return switch (path) {
                case "/json" -> send(response, json("json"), MediaType.APPLICATION_JSON_VALUE,
                        acceptsGzip, true);
                case "/identity" -> send(response, bytes("identity"), MediaType.TEXT_PLAIN_VALUE,
                        false, true);
                case "/echo" -> send(response, bytes(requestBody), MediaType.TEXT_PLAIN_VALUE,
                        acceptsGzip, true);
                case "/empty" -> response.status(HttpStatus.NO_CONTENT.value()).send();
                case "/entity" -> send(response, json("entity"), MediaType.APPLICATION_JSON_VALUE,
                        acceptsGzip, true);
                case "/direct-stream", "/stream-entity" -> send(response, STREAM_BODY,
                        MediaType.APPLICATION_OCTET_STREAM_VALUE, acceptsGzip, true);
                case "/chunked-stream" -> send(response, STREAM_BODY,
                        MediaType.APPLICATION_OCTET_STREAM_VALUE, acceptsGzip, false);
                case "/client-error" -> {
                    response.status(422);
                    yield send(response, bytes("invalid"), MediaType.TEXT_PLAIN_VALUE, acceptsGzip, true);
                }
                case "/server-error" -> {
                    response.status(503);
                    yield send(response, bytes("unavailable"), MediaType.TEXT_PLAIN_VALUE, acceptsGzip, true);
                }
                case "/oversized-json", "/oversized-entity" ->
                        send(response, json(OVERSIZED_VALUE), MediaType.APPLICATION_JSON_VALUE,
                                acceptsGzip, true);
                case "/oversized-problem" -> {
                    response.status(502);
                    yield send(response,
                            bytes("{\"title\":\"Large problem\",\"detail\":\""
                                    + OVERSIZED_VALUE + "\"}"),
                            MediaType.APPLICATION_PROBLEM_JSON_VALUE, acceptsGzip, true);
                }
                case "/unexpected-bodiless", "/oversized-stream" ->
                        send(response, OVERSIZED_BODY, MediaType.APPLICATION_OCTET_STREAM_VALUE,
                                acceptsGzip, true);
                case "/truncated-gzip" -> {
                    byte[] encoded = gzip(OVERSIZED_BODY);
                    yield sendGzip(response, Arrays.copyOf(encoded, Math.min(16, encoded.length)),
                            MediaType.TEXT_PLAIN_VALUE);
                }
                case "/corrupt-gzip" -> {
                    yield sendGzip(response, bytes("not-a-gzip-stream"), MediaType.TEXT_PLAIN_VALUE);
                }
                case "/probe" -> send(response, bytes("probe"), MediaType.TEXT_PLAIN_VALUE, false, true);
                default -> response.status(HttpStatus.NOT_FOUND.value()).send();
            };
        }

        private Mono<Void> send(reactor.netty.http.server.HttpServerResponse response,
                                byte[] body,
                                String contentType,
                                boolean gzip,
                                boolean contentLength) {
            byte[] wireBody = gzip ? gzip(body) : body;
            response.header(HttpHeaders.CONTENT_TYPE, contentType);
            if (gzip) {
                response.header(HttpHeaders.CONTENT_ENCODING, "gzip");
            }
            if (contentLength) {
                response.header(HttpHeaders.CONTENT_LENGTH, Integer.toString(wireBody.length));
            }
            int split = Math.max(1, wireBody.length / 2);
            Flux<byte[]> chunks = Flux.concat(
                    Mono.just(Arrays.copyOfRange(wireBody, 0, split)),
                    Mono.delay(Duration.ofMillis(25))
                            .thenReturn(Arrays.copyOfRange(wireBody, split, wireBody.length)));
            return response.sendByteArray(chunks).then();
        }

        private Mono<Void> sendGzip(reactor.netty.http.server.HttpServerResponse response,
                                    byte[] wireBody,
                                    String contentType) {
            response.header(HttpHeaders.CONTENT_TYPE, contentType);
            response.header(HttpHeaders.CONTENT_ENCODING, "gzip");
            response.header(HttpHeaders.CONTENT_LENGTH, Integer.toString(wireBody.length));
            int split = Math.max(1, wireBody.length / 2);
            return response.sendByteArray(Flux.concat(
                    Mono.just(Arrays.copyOfRange(wireBody, 0, split)),
                    Mono.delay(Duration.ofMillis(25))
                            .thenReturn(Arrays.copyOfRange(wireBody, split, wireBody.length))))
                    .then();
        }

        private boolean usesGzip(String path, boolean acceptsGzip) {
            return acceptsGzip && !path.equals("/identity") && !path.equals("/empty") && !path.equals("/probe");
        }

        private int wireBytes(String path, boolean acceptsGzip, String requestBody) {
            byte[] body = switch (path) {
                case "/json" -> json("json");
                case "/entity" -> json("entity");
                case "/direct-stream", "/stream-entity", "/chunked-stream" -> STREAM_BODY;
                case "/client-error" -> bytes("invalid");
                case "/server-error" -> bytes("unavailable");
                case "/oversized-json", "/oversized-entity" -> json(OVERSIZED_VALUE);
                case "/oversized-problem" -> bytes("{\"title\":\"Large problem\",\"detail\":\""
                        + OVERSIZED_VALUE + "\"}");
                case "/unexpected-bodiless", "/oversized-stream" -> OVERSIZED_BODY;
                case "/truncated-gzip" -> {
                    byte[] encoded = gzip(OVERSIZED_BODY);
                    yield Arrays.copyOf(encoded, Math.min(16, encoded.length));
                }
                case "/corrupt-gzip" -> bytes("not-a-gzip-stream");
                case "/identity" -> bytes("identity");
                case "/echo" -> bytes(requestBody);
                case "/probe" -> bytes("probe");
                default -> new byte[0];
            };
            if (path.equals("/truncated-gzip") || path.equals("/corrupt-gzip")) {
                return body.length;
            }
            return usesGzip(path, acceptsGzip) ? gzip(body).length : body.length;
        }

        private byte[] json(String value) {
            return bytes("{\"value\":\"" + value + "\"}");
        }

        private byte[] bytes(String value) {
            return value.getBytes(StandardCharsets.UTF_8);
        }

        private byte[] gzip(byte[] value) {
            try (ByteArrayOutputStream output = new ByteArrayOutputStream();
                 GZIPOutputStream gzip = new GZIPOutputStream(output)) {
                gzip.write(value);
                gzip.finish();
                return output.toByteArray();
            } catch (IOException ex) {
                throw new IllegalStateException("Failed to gzip test response", ex);
            }
        }
    }

    private record RequestRecord(String path,
                                 String acceptEncoding,
                                 String requestContentEncoding,
                                 String requestBody,
                                 boolean gzipResponse,
                                 int wireBytes,
                                 String connectionId) {
    }
}
