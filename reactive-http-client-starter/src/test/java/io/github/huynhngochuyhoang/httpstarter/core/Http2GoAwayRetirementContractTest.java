package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.annotation.*;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.handler.codec.http2.DefaultHttp2GoAwayFrame;
import io.netty.handler.codec.http2.DefaultHttp2ResetFrame;
import io.netty.handler.codec.http2.Http2Error;
import io.netty.handler.codec.http2.Http2StreamChannel;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.netty.DisposableServer;
import reactor.netty.http.HttpProtocol;
import reactor.netty.http.server.HttpServer;
import reactor.netty.resources.ConnectionProvider;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.zip.GZIPOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Http2GoAwayRetirementContractTest {

    private static final Duration CALL_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration RETIREMENT_OBSERVATION = Duration.ofMillis(250);
    private static final long PENDING_ACQUIRE_TIMEOUT_MS = 30_000;
    private static final int HTTP2_FRAME_HEADER_LENGTH = 9;
    private static final int GOAWAY_PAYLOAD_LENGTH = 8;
    private static final int GOAWAY_FRAME_TYPE = 7;
    private static final int GOAWAY_EXTRA_STREAM_IDS = 0;

    @Test
    void goAwayLetsAcceptedStreamCompleteAndMovesPendingDemandToReplacementConnection() throws Exception {
        try (RetirementServer server = RetirementServer.create(8, false);
             MeterFixture meters = new MeterFixture();
             ClientFixture fixture = ClientFixture.create(server, true, false)) {
            CompletableFuture<String> acceptedLower = fixture.client().hold("accepted-lower").toFuture();
            server.awaitPath("/hold/accepted-lower");
            RequestRecord lowerRecord = server.record("/hold/accepted-lower");
            CompletableFuture<String> acceptedBoundary = fixture.client().hold("accepted-boundary").toFuture();
            server.awaitPath("/hold/accepted-boundary");
            RequestRecord boundaryRecord = server.record("/hold/accepted-boundary");
            assertThat(boundaryRecord.transportChannelId()).isEqualTo(lowerRecord.transportChannelId());
            meters.awaitGauge(ProtocolAwareConnectionPoolMeterRegistrar.ACTIVE_STREAMS,
                    fixture.poolName(), 2);

            GoAwayEvidence goAway = server.retire(boundaryRecord.transportChannelId());
            assertThat(goAway.errorCode()).isEqualTo(Http2Error.NO_ERROR.code());
            assertThat(lowerRecord.streamId()).isPositive().isOdd();
            assertThat(boundaryRecord.streamId()).isGreaterThan(lowerRecord.streamId()).isOdd();
            assertThat(lowerRecord.streamId()).isLessThan(goAway.lastStreamId());
            assertThat(boundaryRecord.streamId()).isEqualTo(goAway.lastStreamId());
            assertThat(boundaryRecord.streamId() + 2).isGreaterThan(goAway.lastStreamId());

            CompletableFuture<String> replacement = fixture.client().hold("replacement").toFuture();
            meters.awaitGauge(ProtocolAwareConnectionPoolMeterRegistrar.PENDING_STREAMS,
                    fixture.poolName(), 1);
            assertThat(acceptedLower).isNotDone();
            assertThat(acceptedBoundary).isNotDone();
            assertThat(server.paths()).doesNotContain("/hold/replacement");

            server.release("accepted-lower");
            server.release("accepted-boundary");
            assertThat(acceptedLower.get(5, TimeUnit.SECONDS)).isEqualTo("accepted-lower");
            assertThat(acceptedBoundary.get(5, TimeUnit.SECONDS)).isEqualTo("accepted-boundary");
            meters.awaitGaugeValue(ProtocolAwareConnectionPoolMeterRegistrar.ACTIVE_STREAMS,
                    fixture.poolName(), 0);
            assertThat(replacement).as("replacement call while GOAWAY socket remains open").isNotDone();
            assertThat(server.paths()).as("requests observed before retired socket close")
                    .doesNotContain("/hold/replacement");
            assertRemainsTrue(() -> !replacement.isDone()
                            && !server.paths().contains("/hold/replacement"),
                    RETIREMENT_OBSERVATION,
                    "GOAWAY connection should remain draining while its socket is open");

            server.closeTransport(boundaryRecord.transportChannelId());
            server.awaitPath("/hold/replacement");
            RequestRecord replacementRecord = server.record("/hold/replacement");
            assertThat(replacementRecord.transportChannelId())
                    .isNotEqualTo(boundaryRecord.transportChannelId());

            server.release("replacement");
            assertThat(replacement.get(5, TimeUnit.SECONDS)).isEqualTo("replacement");
            meters.awaitGaugeValue(ProtocolAwareConnectionPoolMeterRegistrar.ACTIVE_STREAMS,
                    fixture.poolName(), 0);
            meters.awaitGaugeValue(ProtocolAwareConnectionPoolMeterRegistrar.PENDING_STREAMS,
                    fixture.poolName(), 0);
            meters.awaitGaugeValue(ProtocolAwareConnectionPoolMeterRegistrar.TOTAL_CONNECTIONS,
                    fixture.poolName(), 1);
        }
    }

    @Test
    void goAwayDoesNotReplayAProcessedUploadAfterAcceptedStreamReset() throws Exception {
        try (RetirementServer server = RetirementServer.create(8, false);
             ClientFixture fixture = ClientFixture.create(server, false, false)) {
            AtomicInteger subscriptions = new AtomicInteger();
            Flux<DataBuffer> body = Flux.defer(() -> {
                subscriptions.incrementAndGet();
                return Flux.just(buffer("part-1"), buffer("-part-2"));
            });

            assertThatThrownBy(() -> fixture.client().uploadAndRetire(body).block(CALL_TIMEOUT))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageNotContaining("Timeout on blocking read");
            assertThat(subscriptions).hasValue(1);
            assertThat(server.processedUploads()).containsExactly("part-1-part-2");
            assertThat(server.records("/upload-retire")).hasSize(1);

            RequestRecord upload = server.record("/upload-retire");
            server.closeTransport(upload.transportChannelId());
            assertThat(fixture.client().probe().block(CALL_TIMEOUT)).isEqualTo("probe");
            assertThat(server.record("/probe").transportChannelId())
                    .isNotEqualTo(upload.transportChannelId());
        }
    }

    @Test
    void retirementKeepsCompressedStreamingCancellationAndResetStreamLocal() throws Exception {
        try (RetirementServer server = RetirementServer.create(8, true);
             MeterFixture meters = new MeterFixture();
             ClientFixture fixture = ClientFixture.create(server, true, true)) {
            ResponseEntity<Flux<DataBuffer>> entity = fixture.client().streamingEntity().block(CALL_TIMEOUT);
            assertThat(entity).isNotNull();
            assertThat(entity.getBody()).isNotNull();
            server.awaitPath("/stream-entity");

            AtomicInteger cancelledChunks = new AtomicInteger();
            Disposable cancelled = fixture.client().cancellableStream()
                    .doOnNext(buffer -> {
                        cancelledChunks.incrementAndGet();
                        DataBufferUtils.release(buffer);
                    })
                    .subscribe(ignored -> { }, ignored -> { });
            server.awaitPath("/cancel");
            await(() -> cancelledChunks.get() > 0, "cancellable stream should emit before GOAWAY");

            CompletableFuture<Boolean> reset = fixture.client().resetStream()
                    .doOnNext(DataBufferUtils::release)
                    .then(Mono.just(false))
                    .onErrorReturn(true)
                    .toFuture();
            server.awaitPath("/reset");

            RequestRecord retained = server.record("/stream-entity");
            assertThat(server.record("/cancel").transportChannelId())
                    .isEqualTo(retained.transportChannelId());
            assertThat(server.record("/reset").transportChannelId())
                    .isEqualTo(retained.transportChannelId());
            server.retire(retained.transportChannelId());

            server.releaseReset();
            assertThat(reset.get(5, TimeUnit.SECONDS)).isTrue();
            cancelled.dispose();
            server.awaitCancellation();
            meters.awaitGaugeValue(ProtocolAwareConnectionPoolMeterRegistrar.ACTIVE_STREAMS,
                    fixture.poolName(), 1);

            server.releaseStream();
            String streamed = entity.getBody()
                    .map(Http2GoAwayRetirementContractTest::readAndRelease)
                    .reduce("", String::concat)
                    .block(CALL_TIMEOUT);
            assertThat(streamed).isEqualTo(RetirementServer.FIRST_CHUNK + RetirementServer.SECOND_CHUNK);
            assertThat(cancelled.isDisposed()).isTrue();
            assertThat(cancelledChunks.get()).isPositive();
            assertThat(retained.acceptEncoding()).contains("gzip");
            assertThat(retained.wireContentEncoding()).isEqualTo("gzip");
            meters.awaitGaugeValue(ProtocolAwareConnectionPoolMeterRegistrar.ACTIVE_STREAMS,
                    fixture.poolName(), 0);
            meters.awaitGaugeValue(ProtocolAwareConnectionPoolMeterRegistrar.PENDING_STREAMS,
                    fixture.poolName(), 0);

            server.closeTransport(retained.transportChannelId());
            assertThat(fixture.client().probe().block(CALL_TIMEOUT)).isEqualTo("probe");
            assertThat(server.record("/probe").transportChannelId())
                    .isNotEqualTo(retained.transportChannelId());
        }
    }

    @Test
    void shutdownDuringRetirementTerminatesActiveAndPendingWorkWithinDisposalBound() throws Exception {
        try (RetirementServer server = RetirementServer.create(1, false);
             MeterFixture meters = new MeterFixture()) {
            ClientFixture fixture = ClientFixture.create(server, true, false);
            CompletableFuture<String> retiredActive = fixture.client().hold("retired-active").toFuture();
            server.awaitPath("/hold/retired-active");
            server.retire(server.record("/hold/retired-active").transportChannelId());

            CompletableFuture<String> pending = fixture.client().hold("pending").toFuture();
            meters.awaitGauge(ProtocolAwareConnectionPoolMeterRegistrar.PENDING_STREAMS,
                    fixture.poolName(), 1);

            long shutdownStarted = System.nanoTime();
            CompletableFuture<Void> shutdown = CompletableFuture.runAsync(fixture::close);
            shutdown.get(5, TimeUnit.SECONDS);
            Duration shutdownDuration = Duration.ofNanos(System.nanoTime() - shutdownStarted);

            await(retiredActive::isDone, "retired active stream should terminate during shutdown");
            await(pending::isDone, "pending stream should terminate during shutdown");
            assertThat(shutdownDuration).isLessThan(Duration.ofSeconds(5));
            assertThat(shutdownDuration.toMillis()).isLessThan(PENDING_ACQUIRE_TIMEOUT_MS);
            assertThat(retiredActive).isCompletedExceptionally();
            assertThat(pending).isCompletedExceptionally();
            assertThat(fixture.connectionProvider().isDisposed()).isTrue();
            assertThat(server.paths()).doesNotContain("/hold/pending");
            assertThat(meters.find(ProtocolAwareConnectionPoolMeterRegistrar.ACTIVE_STREAMS,
                    fixture.poolName())).isNull();
            assertThat(meters.find(ProtocolAwareConnectionPoolMeterRegistrar.PENDING_STREAMS,
                    fixture.poolName())).isNull();
        }
    }

    private static DataBuffer buffer(String value) {
        return DefaultDataBufferFactory.sharedInstance.wrap(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String readAndRelease(DataBuffer buffer) {
        try {
            return buffer.toString(StandardCharsets.UTF_8);
        } finally {
            DataBufferUtils.release(buffer);
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
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(ex);
            }
        }
        assertThat(condition.getAsBoolean()).as(message).isTrue();
    }

    private static void assertRemainsTrue(BooleanSupplier condition,
                                          Duration duration,
                                          String message) {
        long deadline = System.nanoTime() + duration.toNanos();
        while (System.nanoTime() < deadline) {
            assertThat(condition.getAsBoolean()).as(message).isTrue();
            try {
                Thread.sleep(10);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(ex);
            }
        }
    }

    @ReactiveHttpClient(name = "goaway-contract")
    interface RetirementClient {
        @GET("/hold/{id}")
        Mono<String> hold(@PathVar("id") String id);

        @POST("/upload-retire")
        Mono<String> uploadAndRetire(@Body Flux<DataBuffer> body);

        @GET("/stream-entity")
        Mono<ResponseEntity<Flux<DataBuffer>>> streamingEntity();

        @GET("/cancel")
        Flux<DataBuffer> cancellableStream();

        @GET("/reset")
        Flux<DataBuffer> resetStream();

        @GET("/probe")
        Mono<String> probe();
    }

    private static final class ClientFixture implements AutoCloseable {
        private final StaticApplicationContext context;
        private final ReactiveHttpClientFactoryBean<RetirementClient> factory;
        private final RetirementClient client;
        private final ConnectionProvider connectionProvider;
        private final String poolName;
        private boolean closed;

        private ClientFixture(StaticApplicationContext context,
                              ReactiveHttpClientFactoryBean<RetirementClient> factory,
                              RetirementClient client,
                              ConnectionProvider connectionProvider) {
            this.context = context;
            this.factory = factory;
            this.client = client;
            this.connectionProvider = connectionProvider;
            this.poolName = connectionProvider.name();
        }

        static ClientFixture create(RetirementServer server,
                                    boolean metricsEnabled,
                                    boolean compressionEnabled) {
            StaticApplicationContext context = new StaticApplicationContext();
            ReactiveHttpClientProperties properties = new ReactiveHttpClientProperties();
            ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();
            config.setBaseUrl(server.baseUrl());
            config.setHttp2Enabled(true);
            config.setCompressionEnabled(compressionEnabled);
            ReactiveHttpClientProperties.ConnectionPoolConfig pool =
                    new ReactiveHttpClientProperties.ConnectionPoolConfig();
            pool.setMaxConnections(1);
            pool.setPendingAcquireTimeoutMs(PENDING_ACQUIRE_TIMEOUT_MS);
            pool.setMetricsEnabled(metricsEnabled);
            config.setPool(pool);
            properties.getClients().put("goaway-contract", config);

            context.getBeanFactory().registerSingleton("reactiveHttpClientProperties", properties);
            context.getBeanFactory().registerSingleton("starterWebClientBuilder", WebClient.builder());
            context.getBeanFactory().registerSingleton("reactiveHttpClientJsonCodec", TestJsonCodecs.jsonCodec());

            ReactiveHttpClientFactoryBean<RetirementClient> factory = new ReactiveHttpClientFactoryBean<>();
            factory.setType(RetirementClient.class);
            factory.setApplicationContext(context);
            RetirementClient client = factory.getObject();
            return new ClientFixture(context, factory, client, connectionProvider(factory));
        }

        RetirementClient client() {
            return client;
        }

        ConnectionProvider connectionProvider() {
            return connectionProvider;
        }

        String poolName() {
            return poolName;
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
            } catch (ReflectiveOperationException ex) {
                throw new IllegalStateException(ex);
            }
        }
    }

    private static final class MeterFixture implements AutoCloseable {
        private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

        private MeterFixture() {
            Metrics.addRegistry(registry);
        }

        Meter find(String name, String poolName) {
            return registry.find(name).tag("name", poolName).meter();
        }

        void awaitGauge(String name, String poolName, double minimum) {
            await(() -> {
                io.micrometer.core.instrument.Gauge gauge = registry.find(name)
                        .tag("name", poolName)
                        .gauge();
                return gauge != null && gauge.value() >= minimum;
            }, name + " should reach " + minimum);
        }

        void awaitGaugeValue(String name, String poolName, double expected) {
            await(() -> gaugeValue(name, poolName) == expected,
                    name + " should converge to " + expected);
        }

        double gaugeValue(String name, String poolName) {
            io.micrometer.core.instrument.Gauge gauge = registry.find(name)
                    .tag("name", poolName)
                    .gauge();
            return gauge != null ? gauge.value() : Double.NaN;
        }

        @Override
        public void close() {
            Metrics.removeRegistry(registry);
            registry.close();
        }
    }

    private static final class RetirementServer implements AutoCloseable {
        private static final String FIRST_CHUNK = "first-" + "x".repeat(4096);
        private static final String SECOND_CHUNK = "-second-" + "y".repeat(4096);

        private final Map<String, Sinks.One<Void>> holds = new ConcurrentHashMap<>();
        private final Sinks.One<Void> streamRelease = Sinks.one();
        private final Sinks.One<Void> resetRelease = Sinks.one();
        private final Sinks.One<Void> cancellationObserved = Sinks.one();
        private final List<RequestRecord> records = new CopyOnWriteArrayList<>();
        private final List<String> processedUploads = new CopyOnWriteArrayList<>();
        private final Map<String, Channel> transports = new ConcurrentHashMap<>();
        private final AtomicInteger goAwayCaptureSequence = new AtomicInteger();
        private final boolean gzipResponses;
        private final DisposableServer server;

        private RetirementServer(int maxConcurrentStreams, boolean gzipResponses) {
            this.gzipResponses = gzipResponses;
            server = HttpServer.create()
                    .host("127.0.0.1")
                    .port(0)
                    .protocol(HttpProtocol.H2C)
                    .http2Settings(settings -> settings.maxConcurrentStreams(maxConcurrentStreams))
                    .handle((request, response) -> {
                        String path = request.path().startsWith("/") ? request.path() : "/" + request.path();
                        String acceptEncoding = request.requestHeaders().get(HttpHeaders.ACCEPT_ENCODING);
                        String wireContentEncoding = gzipResponses
                                && path.equals("/stream-entity")
                                && acceptEncoding != null
                                && acceptEncoding.toLowerCase(Locale.ROOT).contains("gzip")
                                ? "gzip"
                                : null;
                        AtomicReference<Channel> streamChannel = new AtomicReference<>();
                        request.withConnection(connection -> {
                            streamChannel.set(connection.channel());
                            RequestRecord record = record(path, connection.channel(),
                                    acceptEncoding, wireContentEncoding);
                            records.add(record);
                            transports.put(record.transportChannelId(), transportChannel(connection.channel()));
                        });
                        return respond(path, streamChannel, request, response, wireContentEncoding);
                    })
                    .bindNow();
        }

        static RetirementServer create(int maxConcurrentStreams, boolean gzipResponses) {
            return new RetirementServer(maxConcurrentStreams, gzipResponses);
        }

        String baseUrl() {
            return "http://127.0.0.1:" + server.port();
        }

        List<String> paths() {
            return records.stream().map(RequestRecord::path).toList();
        }

        List<RequestRecord> records(String path) {
            return records.stream().filter(record -> record.path().equals(path)).toList();
        }

        List<String> processedUploads() {
            return List.copyOf(processedUploads);
        }

        RequestRecord record(String path) {
            awaitPath(path);
            return records(path).get(0);
        }

        void awaitPath(String path) {
            await(() -> paths().contains(path), "server should receive " + path);
        }

        void release(String id) {
            holds.computeIfAbsent(id, ignored -> Sinks.one()).tryEmitEmpty();
        }

        void releaseStream() {
            streamRelease.tryEmitEmpty();
        }

        void releaseReset() {
            resetRelease.tryEmitEmpty();
        }

        void awaitCancellation() {
            cancellationObserved.asMono().block(CALL_TIMEOUT);
        }

        GoAwayEvidence retire(String transportChannelId) throws Exception {
            GoAwayWrite goAway = sendGoAway(transportChannelId);
            goAway.writeFuture().get(5, TimeUnit.SECONDS);
            return goAway.wireEvidence().get(5, TimeUnit.SECONDS);
        }

        Mono<Void> retireAsync(String transportChannelId) {
            return Mono.create(sink -> sendGoAway(transportChannelId).writeFuture().addListener(write -> {
                if (write.isSuccess()) {
                    sink.success();
                } else {
                    sink.error(write.cause());
                }
            }));
        }

        private GoAwayWrite sendGoAway(String transportChannelId) {
            Channel transport = transports.get(transportChannelId);
            assertThat(transport).as("captured H2 transport channel").isNotNull();
            CompletableFuture<GoAwayEvidence> wireEvidence = new CompletableFuture<>();
            String captureName = "goaway-wire-capture-" + goAwayCaptureSequence.incrementAndGet();
            transport.pipeline().addFirst(captureName, new ChannelOutboundHandlerAdapter() {
                @Override
                public void write(ChannelHandlerContext context, Object message, ChannelPromise promise) {
                    GoAwayEvidence evidence = decodeGoAway(message);
                    context.write(message, promise);
                    if (evidence != null) {
                        wireEvidence.complete(evidence);
                        context.pipeline().remove(this);
                    }
                }
            });

            DefaultHttp2GoAwayFrame frame = new DefaultHttp2GoAwayFrame(Http2Error.NO_ERROR);
            frame.setExtraStreamIds(GOAWAY_EXTRA_STREAM_IDS);
            ChannelFuture writeFuture = transport.writeAndFlush(frame);
            writeFuture.addListener(write -> {
                if (!write.isSuccess()) {
                    wireEvidence.completeExceptionally(write.cause());
                }
            });
            return new GoAwayWrite(writeFuture, wireEvidence);
        }

        private static GoAwayEvidence decodeGoAway(Object message) {
            if (!(message instanceof ByteBuf frame)
                    || frame.readableBytes() < HTTP2_FRAME_HEADER_LENGTH + GOAWAY_PAYLOAD_LENGTH) {
                return null;
            }
            int index = frame.readerIndex();
            int payloadLength = frame.getUnsignedMedium(index);
            int frameType = frame.getUnsignedByte(index + 3);
            int frameStreamId = frame.getInt(index + 5) & Integer.MAX_VALUE;
            if (payloadLength < GOAWAY_PAYLOAD_LENGTH
                    || frameType != GOAWAY_FRAME_TYPE
                    || frameStreamId != 0
                    || frame.readableBytes() < HTTP2_FRAME_HEADER_LENGTH + payloadLength) {
                return null;
            }
            int lastStreamId = frame.getInt(index + 9) & Integer.MAX_VALUE;
            long errorCode = frame.getUnsignedInt(index + 13);
            return new GoAwayEvidence(errorCode, lastStreamId);
        }

        void closeTransport(String transportChannelId) throws Exception {
            Channel transport = transports.get(transportChannelId);
            assertThat(transport).as("captured H2 transport channel").isNotNull();
            transport.close().get(5, TimeUnit.SECONDS);
        }

        @Override
        public void close() {
            holds.values().forEach(sink -> sink.tryEmitEmpty());
            streamRelease.tryEmitEmpty();
            resetRelease.tryEmitEmpty();
            server.disposeNow(Duration.ofSeconds(5));
        }

        private Mono<Void> respond(String path,
                                   AtomicReference<Channel> streamChannel,
                                   reactor.netty.http.server.HttpServerRequest request,
                                   reactor.netty.http.server.HttpServerResponse response,
                                   String wireContentEncoding) {
            if (path.startsWith("/hold/")) {
                String id = path.substring(path.lastIndexOf('/') + 1);
                return holds.computeIfAbsent(id, ignored -> Sinks.one()).asMono()
                        .then(response.sendString(Mono.just(id)).then());
            }
            return switch (path) {
                case "/upload-retire" -> request.receive().aggregate().asString(StandardCharsets.UTF_8)
                        .flatMap(body -> {
                            processedUploads.add(body);
                            return retireAsync(transportChannelId(streamChannel.get()))
                                    .then(resetWithoutResponse(streamChannel.get()));
                        });
                case "/stream-entity" -> streamingResponse(response, wireContentEncoding);
                case "/cancel" -> response.sendString(Flux.concat(
                                Mono.just("c".repeat(4096)),
                                Flux.interval(Duration.ofMillis(25)).map(index -> "cancel-" + index))
                        .doOnCancel(() -> cancellationObserved.tryEmitEmpty())).then();
                case "/reset" -> Mono.from(response.sendHeaders())
                        .then(resetRelease.asMono())
                        .then(Mono.fromRunnable(() -> streamChannel.get()
                                .writeAndFlush(new DefaultHttp2ResetFrame(Http2Error.CANCEL))));
                case "/probe" -> response.sendString(Mono.just("probe")).then();
                default -> response.status(404).send();
            };
        }

        private static Mono<Void> resetWithoutResponse(Channel streamChannel) {
            return Mono.<Void>create(sink -> streamChannel
                            .writeAndFlush(new DefaultHttp2ResetFrame(Http2Error.CANCEL))
                            .addListener(write -> {
                                if (write.isSuccess()) {
                                    sink.success();
                                } else {
                                    sink.error(write.cause());
                                }
                            }))
                    .then(Mono.never());
        }

        private Mono<Void> streamingResponse(
                reactor.netty.http.server.HttpServerResponse response,
                String wireContentEncoding) {
            byte[] body = (FIRST_CHUNK + SECOND_CHUNK).getBytes(StandardCharsets.UTF_8);
            byte[] wireBody = "gzip".equals(wireContentEncoding) ? gzip(body) : body;
            int split = Math.max(1, wireBody.length / 2);
            if (wireContentEncoding != null) {
                response.header(HttpHeaders.CONTENT_ENCODING, wireContentEncoding);
            }
            return response.sendByteArray(Flux.concat(
                    Mono.just(Arrays.copyOfRange(wireBody, 0, split)),
                    streamRelease.asMono().thenReturn(
                            Arrays.copyOfRange(wireBody, split, wireBody.length))))
                    .then();
        }

        private static byte[] gzip(byte[] value) {
            try (ByteArrayOutputStream output = new ByteArrayOutputStream();
                 GZIPOutputStream gzip = new GZIPOutputStream(output)) {
                gzip.write(value);
                gzip.finish();
                return output.toByteArray();
            } catch (IOException ex) {
                throw new IllegalStateException("Failed to gzip retirement response", ex);
            }
        }

        private static RequestRecord record(String path,
                                            Channel streamChannel,
                                            String acceptEncoding,
                                            String wireContentEncoding) {
            int streamId = streamChannel instanceof Http2StreamChannel http2
                    ? http2.stream().id()
                    : -1;
            return new RequestRecord(path, transportChannelId(streamChannel), streamId,
                    acceptEncoding, wireContentEncoding);
        }

        private static String transportChannelId(Channel channel) {
            return transportChannel(channel).id().asLongText();
        }

        private static Channel transportChannel(Channel channel) {
            return channel instanceof Http2StreamChannel ? channel.parent() : channel;
        }
    }

    private record RequestRecord(String path,
                                 String transportChannelId,
                                 int streamId,
                                 String acceptEncoding,
                                 String wireContentEncoding) {
    }

    private record GoAwayEvidence(long errorCode, int lastStreamId) {
    }

    private record GoAwayWrite(ChannelFuture writeFuture,
                               CompletableFuture<GoAwayEvidence> wireEvidence) {
    }
}
