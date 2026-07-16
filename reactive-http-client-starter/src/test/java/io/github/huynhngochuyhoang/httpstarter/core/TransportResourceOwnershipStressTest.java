package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.annotation.*;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import io.github.huynhngochuyhoang.httpstarter.exception.RemoteServiceException;
import io.netty.handler.timeout.ReadTimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.Disposable;
import reactor.core.Exceptions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;
import reactor.netty.resources.ConnectionProvider;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class TransportResourceOwnershipStressTest {

    private static final Duration CALL_TIMEOUT = Duration.ofSeconds(5);

    @Test
    void boundedPoolPreservesFramingAndReuseAcrossMixedResponses() {
        try (StressServer server = new StressServer(); ClientFixture fixture = ClientFixture.create(server, false)) {
            StressClient client = fixture.client();

            assertThat(client.create("{}".getBytes(StandardCharsets.UTF_8)).block(CALL_TIMEOUT)).isEqualTo("ok");
            assertThat(client.update("update".getBytes(StandardCharsets.UTF_8)).block(CALL_TIMEOUT)).isEqualTo("ok");
            assertThat(client.body().block(CALL_TIMEOUT)).isEqualTo("body");
            assertThat(client.noContent().block(CALL_TIMEOUT)).isNull();
            assertThat(client.unexpectedVoid().block(CALL_TIMEOUT)).isNull();
            assertThat(client.unexpectedVoidEntity().block(CALL_TIMEOUT)).satisfies(entity -> {
                assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(entity.getBody()).isNull();
            });
            assertThat(catchThrowable(() -> client.error().block(CALL_TIMEOUT)))
                    .isInstanceOf(RemoteServiceException.class);
            assertThat(client.probe().block(CALL_TIMEOUT)).isEqualTo("probe");

            List<RequestRecord> records = server.records();
            assertThat(records).hasSize(8);
            assertThat(records).extracting(RequestRecord::channelId)
                    .containsOnly(records.get(0).channelId());
            assertThat(records.get(0)).satisfies(request -> assertFraming(request, "POST", "/orders", "{}", "2"));
            assertThat(records.get(1)).satisfies(request -> assertFraming(request, "PUT", "/orders/1", "update", "6"));
            assertThat(records).allSatisfy(request -> assertThat(request.host()).isEqualTo("127.0.0.1:" + server.port()));
            assertThat(fixture.connectionProvider().maxConnections()).isEqualTo(1);

            assertThat(catchThrowable(() -> client.applicationContentLength("99").block(CALL_TIMEOUT)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("HTTP framing and authority headers are owned");
            assertThat(server.records()).hasSize(8);
        }
    }

    @Test
    void redirectFollowingIsOptInAndRemainsPoolBounded() {
        try (StressServer server = new StressServer()) {
            try (ClientFixture manual = ClientFixture.create(server, false)) {
                ResponseEntity<String> response = manual.client().redirect().block(CALL_TIMEOUT);
                assertThat(response).isNotNull();
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
                assertThat(manual.connectionProvider().maxConnections()).isEqualTo(1);
            }

            server.clearRecords();
            try (ClientFixture following = ClientFixture.create(server, true)) {
                ResponseEntity<String> response = following.client().redirect().block(CALL_TIMEOUT);
                assertThat(response).isNotNull();
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(response.getBody()).isEqualTo("body");
                assertThat(server.paths()).containsExactly("/redirect", "/body");
                assertThat(server.records()).extracting(RequestRecord::channelId)
                        .containsOnly(server.records().get(0).channelId());
                assertThat(following.connectionProvider().maxConnections()).isEqualTo(1);
            }
        }
    }

    @Test
    void timeoutCancellationAndConcurrentSubscriptionsKeepPoolBounded() {
        try (StressServer server = new StressServer(); ClientFixture fixture = ClientFixture.create(server, false)) {
            StressClient client = fixture.client();

            assertReadTimeout(catchThrowable(() -> client.timeoutBeforeHeaders().block(CALL_TIMEOUT)));
            server.awaitIdle();
            assertThat(client.probe().block(CALL_TIMEOUT)).isEqualTo("probe");

            AtomicInteger chunks = new AtomicInteger();
            Throwable afterHeaders = catchThrowable(() -> client.timeoutAfterHeaders()
                    .doOnNext(buffer -> {
                        chunks.incrementAndGet();
                        DataBufferUtils.release(buffer);
                    })
                    .blockLast(CALL_TIMEOUT));
            assertReadTimeout(afterHeaders);
            assertThat(chunks).hasValue(1);
            server.awaitIdle();
            assertThat(client.probe().block(CALL_TIMEOUT)).isEqualTo("probe");

            AtomicInteger cancelledChunks = new AtomicInteger();
            Disposable cancelledBeforeBody = client.cancellableStream().subscribe(buffer -> {
                cancelledChunks.incrementAndGet();
                DataBufferUtils.release(buffer);
            });
            server.awaitRecords("/cancel", 1);
            cancelledBeforeBody.dispose();
            server.awaitIdle();
            assertThat(cancelledChunks).hasValue(0);
            assertThat(client.probe().block(CALL_TIMEOUT)).isEqualTo("probe");

            String first = client.cancellableStream()
                    .take(1)
                    .map(TransportResourceOwnershipStressTest::readAndRelease)
                    .blockLast(CALL_TIMEOUT);
            assertThat(first).isEqualTo("chunk-0");
            server.awaitIdle();
            assertThat(client.probe().block(CALL_TIMEOUT)).isEqualTo("probe");

            server.resetConcurrencyEvidence();
            Mono<String> cold = client.slow();
            List<String> values = Mono.zip(cold, cold)
                    .map(tuple -> List.of(tuple.getT1(), tuple.getT2()))
                    .block(CALL_TIMEOUT);
            assertThat(values).containsExactly("slow", "slow");
            assertThat(server.maxActiveRequests()).isEqualTo(1);
            assertThat(server.recordsFor("/slow")).hasSize(2)
                    .extracting(RequestRecord::channelId)
                    .containsOnly(server.recordsFor("/slow").get(0).channelId());
            assertThat(fixture.connectionProvider().maxConnections()).isEqualTo(1);
        }
    }

    @Test
    void streamingEnvelopeRemainsCallerOwnedUntilDelayedConsumeOrCancel() {
        try (StressServer server = new StressServer(); ClientFixture fixture = ClientFixture.create(server, false)) {
            StressClient client = fixture.client();
            ResponseEntity<Flux<DataBuffer>> delayed = client.streamingEntity().block(CALL_TIMEOUT);

            assertThat(delayed).isNotNull();
            assertThat(delayed.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(delayed.getBody()).isNotNull();
            String body = Mono.delay(Duration.ofMillis(75))
                    .thenMany(delayed.getBody())
                    .map(TransportResourceOwnershipStressTest::readAndRelease)
                    .reduce("", String::concat)
                    .block(CALL_TIMEOUT);
            assertThat(body).isEqualTo("firstsecond");

            ResponseEntity<Flux<DataBuffer>> cancelled = client.streamingEntity().block(CALL_TIMEOUT);
            assertThat(cancelled).isNotNull();
            assertThat(cancelled.getBody()).isNotNull();
            assertThat(cancelled.getBody().take(1)
                    .map(TransportResourceOwnershipStressTest::readAndRelease)
                    .blockLast(CALL_TIMEOUT)).isEqualTo("first");
            server.awaitIdle();
            assertThat(client.probe().block(CALL_TIMEOUT)).isEqualTo("probe");
            assertThat(fixture.connectionProvider().maxConnections()).isEqualTo(1);
        }
    }

    @Test
    void factoryDestroyWaitsForConnectionProviderDisposal() {
        try (StressServer server = new StressServer()) {
            ClientFixture fixture = ClientFixture.create(server, false);
            ConnectionProvider provider = fixture.connectionProvider();
            assertThat(fixture.client().probe().block(CALL_TIMEOUT)).isEqualTo("probe");
            assertThat(provider.isDisposed()).isFalse();

            fixture.close();

            assertThat(provider.isDisposed()).isTrue();
        }
    }

    private static void assertFraming(RequestRecord request, String method, String path,
                                      String body, String contentLength) {
        assertThat(request.method()).isEqualTo(method);
        assertThat(request.path()).isEqualTo(path);
        assertThat(request.body()).isEqualTo(body);
        assertThat(request.contentLength()).isEqualTo(contentLength);
        assertThat(request.transferEncoding()).isNull();
    }

    private static void assertReadTimeout(Throwable error) {
        assertThat(error).isNotNull();
        Throwable root = Exceptions.unwrap(error);
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        assertThat(root).isInstanceOf(ReadTimeoutException.class);
    }

    private static String readAndRelease(DataBuffer buffer) {
        try {
            return buffer.toString(StandardCharsets.UTF_8);
        } finally {
            DataBufferUtils.release(buffer);
        }
    }

    @ReactiveHttpClient(name = "transport-stress")
    interface StressClient {
        @POST("/orders")
        Mono<String> create(@Body byte[] body);

        @PUT("/orders/1")
        Mono<String> update(@Body byte[] body);

        @GET("/body")
        Mono<String> body();

        @GET("/no-content")
        Mono<Void> noContent();

        @GET("/unexpected-void")
        Mono<Void> unexpectedVoid();

        @GET("/unexpected-void")
        Mono<ResponseEntity<Void>> unexpectedVoidEntity();

        @GET("/error")
        Mono<String> error();

        @GET("/probe")
        Mono<String> probe();

        @GET("/probe")
        Mono<String> applicationContentLength(@HeaderParam("Content-Length") String contentLength);

        @GET("/redirect")
        Mono<ResponseEntity<String>> redirect();

        @GET("/timeout-before")
        @TimeoutMs(75)
        Mono<String> timeoutBeforeHeaders();

        @GET("/timeout-after")
        @TimeoutMs(75)
        Flux<DataBuffer> timeoutAfterHeaders();

        @GET("/cancel")
        Flux<DataBuffer> cancellableStream();

        @GET("/slow")
        Mono<String> slow();

        @GET("/stream-entity")
        Mono<ResponseEntity<Flux<DataBuffer>>> streamingEntity();
    }

    private static final class ClientFixture implements AutoCloseable {
        private final StaticApplicationContext context;
        private final ReactiveHttpClientFactoryBean<StressClient> factory;
        private final StressClient client;
        private final ConnectionProvider connectionProvider;
        private boolean closed;

        private ClientFixture(StaticApplicationContext context,
                              ReactiveHttpClientFactoryBean<StressClient> factory,
                              StressClient client,
                              ConnectionProvider connectionProvider) {
            this.context = context;
            this.factory = factory;
            this.client = client;
            this.connectionProvider = connectionProvider;
        }

        static ClientFixture create(StressServer server, boolean followRedirects) {
            StaticApplicationContext context = new StaticApplicationContext();
            ReactiveHttpClientProperties properties = new ReactiveHttpClientProperties();
            ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();
            config.setBaseUrl("http://127.0.0.1:" + server.port());
            config.setFollowRedirects(followRedirects);
            ReactiveHttpClientProperties.ConnectionPoolConfig pool =
                    new ReactiveHttpClientProperties.ConnectionPoolConfig();
            pool.setMaxConnections(1);
            pool.setPendingAcquireTimeoutMs(2000);
            config.setPool(pool);
            properties.getClients().put("transport-stress", config);

            context.getBeanFactory().registerSingleton("reactiveHttpClientProperties", properties);
            context.getBeanFactory().registerSingleton("starterWebClientBuilder", WebClient.builder());
            context.getBeanFactory().registerSingleton("reactiveHttpClientJsonCodec", TestJsonCodecs.jsonCodec());

            ReactiveHttpClientFactoryBean<StressClient> factory = new ReactiveHttpClientFactoryBean<>();
            factory.setType(StressClient.class);
            factory.setApplicationContext(context);
            StressClient client = factory.getObject();
            return new ClientFixture(context, factory, client, connectionProvider(factory));
        }

        StressClient client() {
            return client;
        }

        ConnectionProvider connectionProvider() {
            return connectionProvider;
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

    private static final class StressServer implements AutoCloseable {
        private final List<RequestRecord> records = new CopyOnWriteArrayList<>();
        private final AtomicInteger activeRequests = new AtomicInteger();
        private final AtomicInteger maxActiveRequests = new AtomicInteger();
        private final DisposableServer server;

        private StressServer() {
            this.server = HttpServer.create()
                    .port(0)
                    .handle((request, response) -> {
                        int active = activeRequests.incrementAndGet();
                        maxActiveRequests.accumulateAndGet(active, Math::max);
                        return request.receive().aggregate().asString(StandardCharsets.UTF_8)
                                .defaultIfEmpty("")
                                .flatMap(body -> {
                                    request.withConnection(connection -> records.add(new RequestRecord(
                                            connection.channel().id().asLongText(),
                                            request.method().name(),
                                            request.uri(),
                                            body,
                                            request.requestHeaders().get(HttpHeaders.CONTENT_LENGTH),
                                            request.requestHeaders().get(HttpHeaders.TRANSFER_ENCODING),
                                            request.requestHeaders().get(HttpHeaders.HOST))));
                                    return respond(request.uri(), response);
                                })
                                .doFinally(signal -> activeRequests.decrementAndGet());
                    })
                    .bindNow();
        }

        private Mono<Void> respond(String path, reactor.netty.http.server.HttpServerResponse response) {
            return switch (path) {
                case "/orders", "/orders/1" -> response.sendString(Mono.just("ok")).then();
                case "/body" -> response.sendString(Mono.just("body")).then();
                case "/no-content" -> response.status(HttpStatus.NO_CONTENT.value()).send();
                case "/unexpected-void" -> response.sendString(Mono.just("unexpected")).then();
                case "/error" -> response.status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                        .sendString(Mono.just("failure-body")).then();
                case "/probe" -> response.sendString(Mono.just("probe")).then();
                case "/redirect" -> response.status(HttpStatus.FOUND.value())
                        .header(HttpHeaders.LOCATION, "/body").send();
                case "/timeout-before" -> Mono.delay(Duration.ofMillis(250))
                        .then(response.sendString(Mono.just("late")).then());
                case "/timeout-after" -> response.sendString(Flux.concat(
                        Mono.just("first"), Mono.delay(Duration.ofMillis(250)).thenReturn("second"))).then();
                case "/cancel" -> response.sendString(Flux.concat(
                        Mono.just(""), Flux.interval(Duration.ofMillis(150), Duration.ofMillis(20))
                                .map(index -> "chunk-" + index))).then();
                case "/slow" -> Mono.delay(Duration.ofMillis(60))
                        .then(response.sendString(Mono.just("slow")).then());
                case "/stream-entity" -> response.sendString(Flux.concat(
                        Mono.just("first"), Mono.delay(Duration.ofMillis(125)).thenReturn("second"))).then();
                default -> response.status(HttpStatus.NOT_FOUND.value()).send();
            };
        }

        int port() {
            return server.port();
        }

        List<RequestRecord> records() {
            return List.copyOf(records);
        }

        List<RequestRecord> recordsFor(String path) {
            return records.stream().filter(record -> record.path().equals(path)).toList();
        }

        List<String> paths() {
            return records.stream().map(RequestRecord::path).toList();
        }

        int maxActiveRequests() {
            return maxActiveRequests.get();
        }

        void clearRecords() {
            records.clear();
        }

        void resetConcurrencyEvidence() {
            awaitIdle();
            records.clear();
            maxActiveRequests.set(0);
        }

        void awaitRecords(String path, int expected) {
            long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
            while (recordsFor(path).size() < expected && System.nanoTime() < deadline) {
                try {
                    Thread.sleep(5);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(ex);
                }
            }
            assertThat(recordsFor(path)).hasSizeGreaterThanOrEqualTo(expected);
        }

        void awaitIdle() {
            long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
            while (activeRequests.get() != 0 && System.nanoTime() < deadline) {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(ex);
                }
            }
            assertThat(activeRequests).hasValue(0);
        }

        @Override
        public void close() {
            server.disposeNow(Duration.ofSeconds(5));
        }
    }

    private record RequestRecord(String channelId,
                                 String method,
                                 String path,
                                 String body,
                                 String contentLength,
                                 String transferEncoding,
                                 String host) {
    }
}
