package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.annotation.*;
import io.github.huynhngochuyhoang.httpstarter.auth.*;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.netty.buffer.PooledByteBufAllocator;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.core.io.AbstractResource;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.core.io.buffer.NettyDataBuffer;
import org.springframework.core.io.buffer.NettyDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.HttpProtocol;
import reactor.netty.http.server.HttpServer;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class StreamingUploadOwnershipTest {

    private static final Duration CALL_TIMEOUT = Duration.ofSeconds(5);
    private static final DefaultDataBufferFactory BUFFER_FACTORY = new DefaultDataBufferFactory();

    @Test
    void publisherBodyStaysColdAndHasOneSubscriptionForOneTransportAttempt() {
        try (UploadServer server = new UploadServer(); ClientFixture fixture = ClientFixture.create(server)) {
            AtomicInteger subscriptions = new AtomicInteger();
            Flux<DataBuffer> body = requestBody("alpha", subscriptions);

            Mono<String> response = fixture.client().upload(body);

            assertThat(subscriptions).hasValue(0);
            assertThat(server.requests()).isEmpty();
            assertThat(response.block(CALL_TIMEOUT)).isEqualTo("alpha");
            assertThat(subscriptions).hasValue(1);
            assertThat(server.bodiesFor("/upload")).containsExactly("alpha");
        }
    }

    @Test
    void cancellationBeforeAcquireDoesNotSubscribeBodyAndInFlightCancellationStopsProducer() {
        try (UploadServer server = new UploadServer(); ClientFixture fixture = ClientFixture.create(server)) {
            Disposable held = fixture.client().hold().subscribe();
            await(server::holdStarted, "hold request did not start");

            AtomicInteger queuedSubscriptions = new AtomicInteger();
            Disposable queued = fixture.client().upload(requestBody("queued", queuedSubscriptions)).subscribe();
            sleep(Duration.ofMillis(100));
            assertThat(queuedSubscriptions).hasValue(0);
            queued.dispose();
            held.dispose();

            AtomicInteger subscriptions = new AtomicInteger();
            AtomicInteger emitted = new AtomicInteger();
            AtomicBoolean cancelled = new AtomicBoolean();
            AtomicLong largestDemand = new AtomicLong();
            Flux<DataBuffer> body = Flux.defer(() -> {
                subscriptions.incrementAndGet();
                return Flux.interval(Duration.ofMillis(5))
                        .take(1_000)
                        .map(index -> {
                            emitted.incrementAndGet();
                            return buffer("chunk-" + index + "\n");
                        })
                        .doOnRequest(requested -> largestDemand.accumulateAndGet(requested, Math::max))
                        .doOnCancel(() -> cancelled.set(true));
            });

            Disposable upload = fixture.client().slowUpload(body).subscribe();
            await(() -> emitted.get() >= 3, "streaming upload did not produce chunks");
            upload.dispose();
            await(cancelled::get, "streaming body did not receive cancellation");

            assertThat(subscriptions).hasValue(1);
            assertThat(emitted.get()).isLessThan(1_000);
            assertThat(largestDemand.get()).isPositive().isLessThan(Long.MAX_VALUE);
            assertThat(fixture.client().probe().block(CALL_TIMEOUT)).isEqualTo("probe");
        }
    }

    @Test
    void peerDisconnectAfterPartialWriteCancelsDemandAndReleasesPooledBuffers() {
        try (UploadServer server = new UploadServer(); ClientFixture fixture = ClientFixture.create(server)) {
            NettyDataBufferFactory buffers = new NettyDataBufferFactory(PooledByteBufAllocator.DEFAULT);
            List<NettyDataBuffer> emittedBuffers = new CopyOnWriteArrayList<>();
            AtomicInteger emitted = new AtomicInteger();
            AtomicBoolean cancelled = new AtomicBoolean();
            AtomicLong largestDemand = new AtomicLong();
            Flux<DataBuffer> body = Flux.interval(Duration.ofMillis(1))
                    .take(256)
                    .map(index -> {
                        NettyDataBuffer buffer = buffers.allocateBuffer(32 * 1024);
                        buffer.write(new byte[32 * 1024]);
                        emittedBuffers.add(buffer);
                        emitted.incrementAndGet();
                        return (DataBuffer) buffer;
                    })
                    .doOnRequest(requested -> largestDemand.accumulateAndGet(requested, Math::max))
                    .doOnCancel(() -> cancelled.set(true));

            Throwable failure = catchThrowable(() ->
                    fixture.client().disconnectDuringUpload(body).block(CALL_TIMEOUT));

            assertThat(failure).isNotNull();
            await(cancelled::get, "peer disconnect did not cancel the upload publisher");
            await(() -> server.partialBytes() > 0, "server did not receive a partial upload");
            await(() -> emittedBuffers.stream().allMatch(buffer -> buffer.getNativeBuffer().refCnt() == 0),
                    "outbound pooled buffers were not released");
            assertThat(emitted.get()).isPositive().isLessThan(256);
            assertThat(largestDemand.get()).isPositive().isLessThan(Long.MAX_VALUE);
            assertThat(fixture.client().probe().block(CALL_TIMEOUT)).isEqualTo("probe");
        }
    }

    @Test
    void retryAndRedirectResubscribeOnceForEachActualRequest() {
        try (UploadServer server = new UploadServer();
             ClientFixture retrying = ClientFixture.create(server, true, false, null)) {
            AtomicInteger subscriptions = new AtomicInteger();

            assertThat(retrying.client().retryUpload(requestBody("retry", subscriptions)).block(CALL_TIMEOUT))
                    .isEqualTo("retry");
            assertThat(subscriptions).hasValue(2);
            assertThat(server.bodiesFor("/retry")).containsExactly("retry", "retry");
        }

        try (UploadServer server = new UploadServer();
             ClientFixture redirecting = ClientFixture.create(server, false, true, null)) {
            AtomicInteger subscriptions = new AtomicInteger();

            assertThat(redirecting.client().redirectUpload(requestBody("redirect", subscriptions)).block(CALL_TIMEOUT))
                    .isEqualTo("redirect");
            assertThat(subscriptions).hasValue(2);
            assertThat(server.paths()).containsExactly("/redirect", "/redirect-target");
            assertThat(server.bodiesFor("/redirect")).containsExactly("redirect");
            assertThat(server.bodiesFor("/redirect-target")).containsExactly("redirect");
        }
    }

    @Test
    void unauthorizedRefreshResubscribesOnlyForTheSecondTransportRequest() {
        RefreshOnceAuthProvider authProvider = new RefreshOnceAuthProvider();
        try (UploadServer server = new UploadServer();
             ClientFixture fixture = ClientFixture.create(server, false, false, authProvider)) {
            AtomicInteger subscriptions = new AtomicInteger();

            assertThat(fixture.client().authUpload(requestBody("secured", subscriptions)).block(CALL_TIMEOUT))
                    .isEqualTo("secured");
            assertThat(subscriptions).hasValue(2);
            assertThat(server.bodiesFor("/auth")).containsExactly("secured", "secured");
            assertThat(authProvider.authCalls).hasValue(2);
            assertThat(authProvider.invalidations).hasValue(1);
        }
    }

    @Test
    void authenticatedRawBodyShapesBypassJsonSerialization() {
        RefreshOnceAuthProvider authProvider = new RefreshOnceAuthProvider();
        try (UploadServer server = new UploadServer();
             ClientFixture fixture = ClientFixture.create(server, false, false, authProvider)) {
            CountingResource resource = new CountingResource("authenticated-resource");
            CountingInputStream inputStream = new CountingInputStream("authenticated-input");
            CountingReader reader = new CountingReader("authenticated-reader");
            CountingChannel channel = new CountingChannel("authenticated-channel");

            NettyDataBufferFactory buffers = new NettyDataBufferFactory(PooledByteBufAllocator.DEFAULT);
            NettyDataBuffer directBuffer = buffers.allocateBuffer();
            directBuffer.write("authenticated-buffer".getBytes(StandardCharsets.UTF_8));
            assertThat(fixture.client().uploadDataBuffer(directBuffer).block(CALL_TIMEOUT))
                    .isEqualTo("authenticated-buffer");
            assertThat(directBuffer.getNativeBuffer().refCnt()).isZero();
            assertThat(fixture.client().uploadResource(resource).block(CALL_TIMEOUT))
                    .isEqualTo("authenticated-resource");
            assertThat(fixture.client().uploadInputStream(inputStream).block(CALL_TIMEOUT))
                    .isEqualTo("authenticated-input");
            assertThat(fixture.client().uploadReader(reader).block(CALL_TIMEOUT))
                    .isEqualTo("authenticated-reader");
            assertThat(fixture.client().uploadChannel(channel).block(CALL_TIMEOUT))
                    .isEqualTo("authenticated-channel");

            assertThat(resource.opens).hasValue(1);
            assertThat(resource.closes).hasValue(1);
            assertThat(inputStream.closes).hasValue(1);
            assertThat(reader.closes).hasValue(1);
            assertThat(channel.closes).hasValue(1);
        }
    }

    @Test
    void readerBodyPreservesWireBytesForCallerSuppliedContentType() {
        try (UploadServer server = new UploadServer(); ClientFixture fixture = ClientFixture.create(server)) {
            String payload = "{\"name\":\"" + "caf\u00e9".repeat(1_500) + "\"}";
            CountingReader reader = new CountingReader(payload);

            fixture.client().uploadReader(
                    "application/json;charset=UTF-16", reader).block(CALL_TIMEOUT);

            RequestRecord request = server.onlyRequest("/reader-content-type");
            assertThat(request.bodyBytes()).containsExactly(payload.getBytes(StandardCharsets.UTF_16));
            assertThat(request.contentType()).isEqualTo("application/json;charset=UTF-16");
            assertThat(reader.closes).hasValue(1);
        }
    }

    @Test
    void cancellationBeforeBodySubscriptionReleasesEagerStreamingBodies() {
        withHeldConnection(client -> {
            CountingInputStream inputStream = new CountingInputStream("queued-input");
            Disposable upload = client.uploadInputStream(inputStream)
                    .subscribe(ignored -> {}, ignored -> {});
            sleep(Duration.ofMillis(100));
            assertThat(inputStream.closes).hasValue(0);
            upload.dispose();
            await(() -> inputStream.closes.get() == 1, "queued input stream was not closed");
        });
        withHeldConnection(client -> {
            CountingReader reader = new CountingReader("queued-reader");
            Disposable upload = client.uploadReader(reader)
                    .subscribe(ignored -> {}, ignored -> {});
            sleep(Duration.ofMillis(100));
            assertThat(reader.closes).hasValue(0);
            upload.dispose();
            await(() -> reader.closes.get() == 1, "queued reader was not closed");
        });
        withHeldConnection(client -> {
            CountingChannel channel = new CountingChannel("queued-channel");
            Disposable upload = client.uploadChannel(channel)
                    .subscribe(ignored -> {}, ignored -> {});
            sleep(Duration.ofMillis(100));
            assertThat(channel.closes).hasValue(0);
            upload.dispose();
            await(() -> channel.closes.get() == 1, "queued channel was not closed");
        });
        withHeldConnection(client -> {
            NettyDataBufferFactory buffers = new NettyDataBufferFactory(PooledByteBufAllocator.DEFAULT);
            NettyDataBuffer directBuffer = buffers.allocateBuffer();
            directBuffer.write("queued-buffer".getBytes(StandardCharsets.UTF_8));
            Disposable upload = client.uploadDataBuffer(directBuffer)
                    .subscribe(ignored -> {}, ignored -> {});
            sleep(Duration.ofMillis(100));
            assertThat(directBuffer.getNativeBuffer().refCnt()).isOne();
            upload.dispose();
            await(() -> directBuffer.getNativeBuffer().refCnt() == 0, "queued direct buffer was not released");
        });
    }

    @Test
    void logicalTimeoutBeforeAuthCompletesClosesEagerBodyWithoutDispatch() {
        InvalidatableAuthProvider pendingAuth = new InvalidatableAuthProvider() {
            @Override
            public Mono<AuthContext> getAuth(AuthRequest request) {
                return Mono.never();
            }

            @Override
            public Mono<Void> invalidate() {
                return Mono.empty();
            }
        };
        try (UploadServer server = new UploadServer();
             ClientFixture fixture = ClientFixture.create(server, false, false, pendingAuth, 50)) {
            CountingInputStream inputStream = new CountingInputStream("timeout-before-write");

            Throwable failure = catchThrowable(() ->
                    fixture.client().uploadInputStream(inputStream).block(CALL_TIMEOUT));

            assertThat(failure).hasRootCauseInstanceOf(
                    io.github.huynhngochuyhoang.httpstarter.exception.LogicalCallTimeoutException.class);
            assertThat(inputStream.closes).hasValue(1);
            assertThat(server.requests()).isEmpty();
        }
    }

    @Test
    void supportedRawBodyShapesUseDeterministicHttp11FramingAndCloseStreams() {
        try (UploadServer server = new UploadServer(); ClientFixture fixture = ClientFixture.create(server)) {
            CountingResource resource = new CountingResource("resource-body");
            CountingInputStream inputStream = new CountingInputStream("input-stream-body");
            CountingReader reader = new CountingReader("reader-body-✓");
            CountingChannel channel = new CountingChannel("channel-body");

            assertThat(fixture.client().upload(requestBody("publisher-body", new AtomicInteger()))
                    .block(CALL_TIMEOUT)).isEqualTo("publisher-body");
            assertThat(fixture.client().uploadDataBuffer(buffer("data-buffer-body"))
                    .block(CALL_TIMEOUT)).isEqualTo("data-buffer-body");
            assertThat(fixture.client().uploadResource(resource)
                    .block(CALL_TIMEOUT)).isEqualTo("resource-body");
            assertThat(fixture.client().uploadInputStream(inputStream)
                    .block(CALL_TIMEOUT)).isEqualTo("input-stream-body");
            assertThat(fixture.client().uploadReader(reader)
                    .block(CALL_TIMEOUT)).isEqualTo("reader-body-✓");
            assertThat(fixture.client().uploadChannel(channel)
                    .block(CALL_TIMEOUT)).isEqualTo("channel-body");

            assertChunked(server.onlyRequest("/upload"), "publisher-body", "application/octet-stream");
            assertChunked(server.onlyRequest("/data-buffer"), "data-buffer-body", "application/octet-stream");
            assertThat(server.onlyRequest("/resource")).satisfies(request -> {
                assertThat(request.body()).isEqualTo("resource-body");
                assertThat(request.contentLength()).isEqualTo(String.valueOf("resource-body".getBytes(StandardCharsets.UTF_8).length));
                assertThat(request.transferEncoding()).isNull();
                assertThat(request.contentType()).isEqualTo("text/plain");
            });
            assertChunked(server.onlyRequest("/input-stream"), "input-stream-body", "application/octet-stream");
            assertChunked(server.onlyRequest("/reader"), "reader-body-✓", "text/plain;charset=UTF-8");
            assertChunked(server.onlyRequest("/channel"), "channel-body", "application/octet-stream");
            assertThat(resource.opens).hasValue(1);
            assertThat(resource.closes).hasValue(1);
            assertThat(inputStream.closes).hasValue(1);
            assertThat(reader.closes).hasValue(1);
            assertThat(channel.closes).hasValue(1);
        }
    }

    @Test
    void h2cStreamingDistinguishesWireProtocolFromServerCompatibilityHeaders() {
        try (UploadServer server = new UploadServer(true); ClientFixture fixture = ClientFixture.create(server)) {
            CountingResource resource = new CountingResource("h2-resource");

            assertThat(fixture.client().upload(requestBody("h2-stream", new AtomicInteger()))
                    .block(CALL_TIMEOUT)).isEqualTo("h2-stream");
            assertThat(fixture.client().uploadResource(resource).block(CALL_TIMEOUT))
                    .isEqualTo("h2-resource");

            assertThat(server.onlyRequest("/upload")).satisfies(request -> {
                assertThat(request.protocol()).isEqualTo("HTTP/2.0");
                assertThat(request.body()).isEqualTo("h2-stream");
                assertThat(request.contentLength()).isNull();
                assertThat(request.transferEncoding()).isEqualTo("chunked");
            });
            assertThat(server.onlyRequest("/resource")).satisfies(request -> {
                assertThat(request.protocol()).isEqualTo("HTTP/2.0");
                assertThat(request.body()).isEqualTo("h2-resource");
                assertThat(request.contentLength()).isEqualTo(String.valueOf(
                        "h2-resource".getBytes(StandardCharsets.UTF_8).length));
                assertThat(request.transferEncoding()).isNull();
            });
            assertThat(resource.opens).hasValue(1);
            assertThat(resource.closes).hasValue(1);
        }
    }

    private static void assertChunked(RequestRecord request, String body, String contentType) {
        assertThat(request.body()).isEqualTo(body);
        assertThat(request.contentLength()).isNull();
        assertThat(request.transferEncoding()).isEqualTo("chunked");
        assertThat(request.contentType()).isEqualTo(contentType);
    }

    @Test
    void builtInSigV4HashMatchesBytesObservedAtTheWire() {
        try (UploadServer server = new UploadServer(); SigV4ClientFixture fixture = SigV4ClientFixture.create(server)) {
            byte[] bytes = "signed-bytes".getBytes(StandardCharsets.UTF_8);

            assertThat(fixture.client().uploadBytes(bytes).block(CALL_TIMEOUT)).isEqualTo("signed-bytes");
            assertThat(fixture.client().uploadDto(new UploadDto("signed-json")).block(CALL_TIMEOUT))
                    .contains("signed-json");

            for (String path : List.of("/signed-bytes", "/signed-json")) {
                RequestRecord request = server.onlyRequest(path);
                assertThat(request.payloadHash()).isEqualTo(sha256Hex(
                        request.body().getBytes(StandardCharsets.UTF_8)));
            }
        }
    }

    private static String sha256Hex(byte[] body) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }

    @Test
    void publisherDtoUsesWebClientEncodingWithoutHiddenSubscription() {
        try (UploadServer server = new UploadServer(); ClientFixture fixture = ClientFixture.create(server)) {
            AtomicInteger subscriptions = new AtomicInteger();
            Flux<UploadDto> body = Flux.defer(() -> {
                subscriptions.incrementAndGet();
                return Flux.just(new UploadDto("one"), new UploadDto("two"));
            });

            Mono<String> response = fixture.client().uploadDtos(body);

            assertThat(subscriptions).hasValue(0);
            assertThat(response.block(CALL_TIMEOUT)).contains("one").contains("two");
            assertThat(subscriptions).hasValue(1);
            assertThat(server.bodiesFor("/dto")).singleElement()
                    .satisfies(value -> assertThat(value).contains("one").contains("two"));
        }
    }

    @Test
    void reusableMultipartResourceIsOpenedAndClosedPerRetryAttempt() {
        try (UploadServer server = new UploadServer();
             ClientFixture fixture = ClientFixture.create(server, true, false, null)) {
            CountingResource resource = new CountingResource("multipart-data");

            assertThat(fixture.client().multipart(resource).block(CALL_TIMEOUT)).isEqualTo("multipart");

            assertThat(resource.opens).hasValue(2);
            assertThat(resource.closes).hasValue(2);
            assertThat(server.bodiesFor("/multipart")).hasSize(2)
                    .allSatisfy(body -> assertThat(body).contains("multipart-data"));
        }
    }

    private static Flux<DataBuffer> requestBody(String value, AtomicInteger subscriptions) {
        return Flux.defer(() -> {
            subscriptions.incrementAndGet();
            return Flux.just(buffer(value));
        });
    }

    private static DataBuffer buffer(String value) {
        return BUFFER_FACTORY.wrap(value.getBytes(StandardCharsets.UTF_8));
    }

    private static void withHeldConnection(Consumer<UploadClient> assertion) {
        try (UploadServer server = new UploadServer(); ClientFixture fixture = ClientFixture.create(server)) {
            Disposable held = fixture.client().hold().subscribe();
            await(server::holdStarted, "hold request did not start");
            try {
                assertion.accept(fixture.client());
            } finally {
                held.dispose();
            }
        }
    }

    private static void await(BooleanSupplier condition, String message) {
        long deadline = System.nanoTime() + CALL_TIMEOUT.toNanos();
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            sleep(Duration.ofMillis(10));
        }
        assertThat(condition.getAsBoolean()).as(message).isTrue();
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }

    @ReactiveHttpClient(name = "streaming-upload")
    interface UploadClient {
        @POST("/upload")
        Mono<String> upload(@Body Flux<DataBuffer> body);

        @POST("/data-buffer")
        Mono<String> uploadDataBuffer(@Body DataBuffer body);

        @POST("/resource")
        Mono<String> uploadResource(@Body CountingResource body);

        @POST("/input-stream")
        Mono<String> uploadInputStream(@Body InputStream body);

        @POST("/reader")
        Mono<String> uploadReader(@Body Reader body);

        @POST("/reader-content-type")
        Mono<String> uploadReader(@HeaderParam("Content-Type") String contentType, @Body Reader body);

        @POST("/channel")
        Mono<String> uploadChannel(@Body ReadableByteChannel body);

        @POST("/slow-upload")
        Mono<String> slowUpload(@Body Flux<DataBuffer> body);

        @POST("/disconnect")
        Mono<String> disconnectDuringUpload(@Body Flux<DataBuffer> body);

        @POST("/retry")
        Mono<String> retryUpload(@Body Flux<DataBuffer> body);

        @POST("/redirect")
        Mono<String> redirectUpload(@Body Flux<DataBuffer> body);

        @POST("/auth")
        Mono<String> authUpload(@Body Flux<DataBuffer> body);

        @POST("/dto")
        Mono<String> uploadDtos(@Body Flux<UploadDto> body);

        @POST("/multipart")
        @MultipartBody
        Mono<String> multipart(@FormFile(value = "file", filename = "upload.txt") CountingResource resource);

        @POST("/hold")
        Mono<String> hold();

        @POST("/probe")
        Mono<String> probe();
    }

    @ReactiveHttpClient(name = "sigv4-upload")
    interface SigV4UploadClient {
        @POST("/signed-bytes")
        Mono<String> uploadBytes(@Body byte[] body);

        @POST("/signed-json")
        Mono<String> uploadDto(@Body UploadDto body);
    }

    record UploadDto(String name) {}

    private static final class ClientFixture implements AutoCloseable {
        private final StaticApplicationContext context;
        private final ReactiveHttpClientFactoryBean<UploadClient> factory;
        private final UploadClient client;

        private ClientFixture(StaticApplicationContext context,
                              ReactiveHttpClientFactoryBean<UploadClient> factory,
                              UploadClient client) {
            this.context = context;
            this.factory = factory;
            this.client = client;
        }

        static ClientFixture create(UploadServer server) {
            return create(server, false, false, null);
        }

        static ClientFixture create(UploadServer server,
                                    boolean retry,
                                    boolean followRedirects,
                                    InvalidatableAuthProvider authProvider) {
            return create(server, retry, followRedirects, authProvider, 0);
        }

        static ClientFixture create(UploadServer server,
                                    boolean retry,
                                    boolean followRedirects,
                                    InvalidatableAuthProvider authProvider,
                                    long logicalCallTimeoutMs) {
            StaticApplicationContext context = new StaticApplicationContext();
            ReactiveHttpClientProperties properties = new ReactiveHttpClientProperties();
            ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();
            config.setBaseUrl("http://127.0.0.1:" + server.port());
            config.setHttp2Enabled(server.http2());
            config.setFollowRedirects(followRedirects);
            config.setLogicalCallTimeoutMs(logicalCallTimeoutMs);
            ReactiveHttpClientProperties.ConnectionPoolConfig pool =
                    new ReactiveHttpClientProperties.ConnectionPoolConfig();
            pool.setMaxConnections(1);
            pool.setPendingAcquireTimeoutMs(2_000);
            config.setPool(pool);

            if (retry) {
                ReactiveHttpClientProperties.ResilienceConfig resilience =
                        new ReactiveHttpClientProperties.ResilienceConfig();
                resilience.setEnabled(true);
                resilience.setRetry("upload-retry");
                resilience.setRetryMethods(Set.of("POST"));
                config.setResilience(resilience);
                RetryConfig retryConfig = RetryConfig.custom()
                        .maxAttempts(2)
                        .waitDuration(Duration.ZERO)
                        .build();
                context.getBeanFactory().registerSingleton("retryRegistry", RetryRegistry.of(retryConfig));
            }
            if (authProvider != null) {
                config.setAuthProvider("refreshAuth");
                context.getBeanFactory().registerSingleton("refreshAuth", authProvider);
            }
            properties.getClients().put("streaming-upload", config);

            context.getBeanFactory().registerSingleton("reactiveHttpClientProperties", properties);
            context.getBeanFactory().registerSingleton("starterWebClientBuilder", WebClient.builder());
            context.getBeanFactory().registerSingleton("reactiveHttpClientJsonCodec", TestJsonCodecs.jsonCodec());

            ReactiveHttpClientFactoryBean<UploadClient> factory = new ReactiveHttpClientFactoryBean<>();
            factory.setType(UploadClient.class);
            factory.setApplicationContext(context);
            return new ClientFixture(context, factory, factory.getObject());
        }

        UploadClient client() {
            return client;
        }

        @Override
        public void close() {
            factory.destroy();
            context.close();
        }
    }

    private static final class SigV4ClientFixture implements AutoCloseable {
        private final StaticApplicationContext context;
        private final ReactiveHttpClientFactoryBean<SigV4UploadClient> factory;
        private final SigV4UploadClient client;

        private SigV4ClientFixture(StaticApplicationContext context,
                                   ReactiveHttpClientFactoryBean<SigV4UploadClient> factory,
                                   SigV4UploadClient client) {
            this.context = context;
            this.factory = factory;
            this.client = client;
        }

        static SigV4ClientFixture create(UploadServer server) {
            StaticApplicationContext context = new StaticApplicationContext();
            ReactiveHttpClientProperties properties = new ReactiveHttpClientProperties();
            ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();
            config.setBaseUrl("http://127.0.0.1:" + server.port());
            ReactiveHttpClientProperties.AuthConfig auth = new ReactiveHttpClientProperties.AuthConfig();
            auth.setType(AwsSigV4AuthProviderFactory.TYPE);
            auth.getAwsSigV4().setAccessKeyId("wire-test-access-key");
            auth.getAwsSigV4().setSecretAccessKey("wire-test-secret-key");
            auth.getAwsSigV4().setRegion("us-east-1");
            auth.getAwsSigV4().setService("execute-api");
            auth.getAwsSigV4().setStrictBodySigningValidation(true);
            config.setAuth(auth);
            properties.getClients().put("sigv4-upload", config);

            context.getBeanFactory().registerSingleton("reactiveHttpClientProperties", properties);
            context.getBeanFactory().registerSingleton("starterWebClientBuilder", WebClient.builder());
            context.getBeanFactory().registerSingleton("reactiveHttpClientJsonCodec", TestJsonCodecs.jsonCodec());
            context.getBeanFactory().registerSingleton(
                    "awsSigV4AuthProviderFactory", (AuthProviderFactory) new AwsSigV4AuthProviderFactory());

            ReactiveHttpClientFactoryBean<SigV4UploadClient> factory = new ReactiveHttpClientFactoryBean<>();
            factory.setType(SigV4UploadClient.class);
            factory.setApplicationContext(context);
            return new SigV4ClientFixture(context, factory, factory.getObject());
        }

        SigV4UploadClient client() {
            return client;
        }

        @Override
        public void close() {
            factory.destroy();
            context.close();
        }
    }

    private static final class RefreshOnceAuthProvider implements InvalidatableAuthProvider {
        private final AtomicInteger authCalls = new AtomicInteger();
        private final AtomicInteger invalidations = new AtomicInteger();

        @Override
        public Mono<AuthContext> getAuth(AuthRequest request) {
            int call = authCalls.incrementAndGet();
            return Mono.just(AuthContext.builder().header(HttpHeaders.AUTHORIZATION, "Bearer token-" + call).build());
        }

        @Override
        public Mono<Void> invalidate() {
            invalidations.incrementAndGet();
            return Mono.empty();
        }
    }

    private static final class CountingInputStream extends FilterInputStream {
        private final AtomicInteger closes = new AtomicInteger();

        private CountingInputStream(String value) {
            super(new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8)));
        }

        @Override
        public void close() throws IOException {
            closes.incrementAndGet();
            super.close();
        }
    }

    private static final class CountingReader extends Reader {
        private final StringReader delegate;
        private final AtomicInteger closes = new AtomicInteger();

        private CountingReader(String value) {
            this.delegate = new StringReader(value);
        }

        @Override
        public int read(char[] buffer, int offset, int length) throws IOException {
            return delegate.read(buffer, offset, length);
        }

        @Override
        public void close() {
            closes.incrementAndGet();
            delegate.close();
        }
    }

    private static final class CountingChannel implements ReadableByteChannel {
        private final ReadableByteChannel delegate;
        private final AtomicInteger closes = new AtomicInteger();

        private CountingChannel(String value) {
            this.delegate = Channels.newChannel(
                    new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8)));
        }

        @Override
        public int read(ByteBuffer destination) throws IOException {
            return delegate.read(destination);
        }

        @Override
        public boolean isOpen() {
            return delegate.isOpen();
        }

        @Override
        public void close() throws IOException {
            closes.incrementAndGet();
            delegate.close();
        }
    }

    private static final class CountingResource extends AbstractResource {
        private final byte[] bytes;
        private final AtomicInteger opens = new AtomicInteger();
        private final AtomicInteger closes = new AtomicInteger();

        private CountingResource(String value) {
            this.bytes = value.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public String getDescription() {
            return "counting upload resource";
        }

        @Override
        public String getFilename() {
            return "upload.txt";
        }

        @Override
        public long contentLength() {
            return bytes.length;
        }

        @Override
        public InputStream getInputStream() {
            opens.incrementAndGet();
            return new FilterInputStream(new ByteArrayInputStream(bytes)) {
                private boolean closed;

                @Override
                public void close() throws IOException {
                    if (!closed) {
                        closed = true;
                        closes.incrementAndGet();
                    }
                    super.close();
                }
            };
        }
    }

    private static final class UploadServer implements AutoCloseable {
        private final List<RequestRecord> requests = new CopyOnWriteArrayList<>();
        private final AtomicInteger retryRequests = new AtomicInteger();
        private final AtomicInteger multipartRequests = new AtomicInteger();
        private final AtomicBoolean holdStarted = new AtomicBoolean();
        private final AtomicLong partialBytes = new AtomicLong();
        private final boolean http2;
        private final DisposableServer server;

        private UploadServer() {
            this(false);
        }

        private UploadServer(boolean http2) {
            this.http2 = http2;
            HttpServer httpServer = HttpServer.create();
            if (http2) {
                httpServer = httpServer.protocol(HttpProtocol.H2C);
            }
            server = httpServer
                    .port(0)
                    .handle((request, response) -> {
                        String requestPath = request.path();
                        String path = requestPath.startsWith("/") ? requestPath : "/" + requestPath;
                        if ("/hold".equals(path)) {
                            holdStarted.set(true);
                            return Mono.never();
                        }
                        if ("/probe".equals(path)) {
                            return response.sendString(Mono.just("probe")).then();
                        }
                        if ("/disconnect".equals(path)) {
                            return request.receive()
                                    .take(1)
                                    .doOnNext(buffer -> partialBytes.addAndGet(buffer.readableBytes()))
                                    .then(Mono.fromRunnable(() ->
                                            request.withConnection(connection -> connection.dispose())));
                        }
                        return request.receive().aggregate().asByteArray()
                                .defaultIfEmpty(new byte[0])
                                .flatMap(bodyBytes -> {
                                    String body = new String(bodyBytes, StandardCharsets.UTF_8);
                                    requests.add(new RequestRecord(
                                            path,
                                            request.version().text(),
                                            body,
                                            bodyBytes,
                                            request.requestHeaders().get(HttpHeaders.CONTENT_LENGTH),
                                            request.requestHeaders().get(HttpHeaders.TRANSFER_ENCODING),
                                            request.requestHeaders().get(HttpHeaders.CONTENT_TYPE),
                                            request.requestHeaders().get("x-amz-content-sha256")));
                                    if ("/retry".equals(path) && retryRequests.incrementAndGet() == 1) {
                                        return response.status(HttpStatus.SERVICE_UNAVAILABLE.value())
                                                .sendString(Mono.just("retry")).then();
                                    }
                                    if ("/multipart".equals(path) && multipartRequests.incrementAndGet() == 1) {
                                        return response.status(HttpStatus.SERVICE_UNAVAILABLE.value())
                                                .sendString(Mono.just("retry")).then();
                                    }
                                    if ("/redirect".equals(path)) {
                                        return response.status(HttpStatus.TEMPORARY_REDIRECT.value())
                                                .header(HttpHeaders.LOCATION, "/redirect-target")
                                                .send();
                                    }
                                    if ("/auth".equals(path) && "Bearer token-1".equals(
                                            request.requestHeaders().get(HttpHeaders.AUTHORIZATION))) {
                                        return response.status(HttpStatus.UNAUTHORIZED.value()).send();
                                    }
                                    String responseBody = switch (path) {
                                        case "/dto", "/upload", "/data-buffer", "/resource", "/input-stream",
                                                "/reader", "/reader-content-type", "/channel", "/signed-bytes", "/signed-json", "/retry",
                                                "/redirect-target", "/auth" -> body;
                                        case "/multipart" -> "multipart";
                                        case "/slow-upload" -> "uploaded";
                                        default -> "ok";
                                    };
                                    return response.sendString(Mono.just(responseBody)).then();
                                });
                    })
                    .bindNow();
        }

        int port() {
            return server.port();
        }

        boolean http2() {
            return http2;
        }

        boolean holdStarted() {
            return holdStarted.get();
        }

        long partialBytes() {
            return partialBytes.get();
        }

        List<RequestRecord> requests() {
            return List.copyOf(requests);
        }

        List<String> paths() {
            return requests.stream().map(RequestRecord::path).toList();
        }

        RequestRecord onlyRequest(String path) {
            List<RequestRecord> matching = requests.stream()
                    .filter(request -> path.equals(request.path()))
                    .toList();
            assertThat(matching).hasSize(1);
            return matching.getFirst();
        }

        List<String> bodiesFor(String path) {
            return requests.stream()
                    .filter(request -> path.equals(request.path()))
                    .map(RequestRecord::body)
                    .toList();
        }

        @Override
        public void close() {
            server.disposeNow(CALL_TIMEOUT);
        }
    }

    private record RequestRecord(
            String path,
            String protocol,
            String body,
            byte[] bodyBytes,
            String contentLength,
            String transferEncoding,
            String contentType,
            String payloadHash) {}
}
