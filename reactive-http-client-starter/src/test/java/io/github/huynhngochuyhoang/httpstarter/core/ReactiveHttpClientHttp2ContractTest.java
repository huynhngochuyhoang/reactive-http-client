package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.annotation.GET;
import io.github.huynhngochuyhoang.httpstarter.annotation.ReactiveHttpClient;
import io.github.huynhngochuyhoang.httpstarter.annotation.TimeoutMs;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import io.github.huynhngochuyhoang.httpstarter.exception.HttpClientException;
import io.github.huynhngochuyhoang.httpstarter.exception.RemoteServiceException;
import io.netty.channel.Channel;
import io.netty.handler.codec.http2.DefaultHttp2ResetFrame;
import io.netty.handler.codec.http2.Http2Error;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.Http2SslContextSpec;
import reactor.netty.http.HttpProtocol;
import reactor.netty.http.server.HttpServer;
import reactor.netty.resources.ConnectionProvider;

import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class ReactiveHttpClientHttp2ContractTest {

    private static final Duration CALL_TIMEOUT = Duration.ofSeconds(5);
    private static final String TLS_PROTOCOL = "TLSv1.3";
    private static final String TLS_CIPHER = "TLS_AES_128_GCM_SHA256";

    @Test
    void starterProxyNegotiatesH2cForApplicationResponseShapes() {
        try (ProtocolServer server = ProtocolServer.h2c();
             ClientFixture fixture = ClientFixture.create(server.baseUrl(), true, null)) {
            assertApplicationResponseShapes(fixture.client(), "HTTP/2.0");
            assertThat(server.protocols()).containsOnly("HTTP/2.0");
            assertThat(fixture.connectionProvider().maxConnections()).isEqualTo(1);
        }
    }

    @Test
    void starterProxyNegotiatesTlsH2WithConfiguredProtocolAndCipher() throws Exception {
        SelfSignedCertificate certificate = new SelfSignedCertificate("localhost");
        Path trustStore = writeTrustStore(certificate.cert(), "changeit");
        try (ProtocolServer server = ProtocolServer.h2(certificate)) {
            ReactiveHttpClientProperties.TlsConfig tls = new ReactiveHttpClientProperties.TlsConfig();
            tls.setTrustStore("file:" + trustStore.toAbsolutePath());
            tls.setTrustStorePassword("changeit");
            tls.setTrustStoreType("PKCS12");
            tls.setProtocols(List.of(TLS_PROTOCOL));
            tls.setCiphers(List.of(TLS_CIPHER));

            try (ClientFixture fixture = ClientFixture.create(server.baseUrl(), true, tls)) {
                assertApplicationResponseShapes(fixture.client(), "HTTP/2.0");
                assertThat(server.protocols()).containsOnly("HTTP/2.0");
                assertThat(server.tlsProtocols()).containsOnly(TLS_PROTOCOL);
                assertThat(server.tlsCiphers()).containsOnly(TLS_CIPHER);
            }
        } finally {
            Files.deleteIfExists(trustStore);
            certificate.delete();
        }
    }

    @Test
    void starterProxyKeepsHttp11AsTheDefault() {
        try (ProtocolServer server = ProtocolServer.http11AndH2c();
             ClientFixture fixture = ClientFixture.create(server.baseUrl(), false, null)) {
            Payload payload = fixture.client().json().block(CALL_TIMEOUT);

            assertThat(payload).isEqualTo(new Payload("json", "HTTP/1.1"));
            assertThat(server.protocols()).containsOnly("HTTP/1.1");
        }
    }

    @Test
    void oneH2ConnectionKeepsConcurrentStreamsIndependentOnCancelAndReset() {
        try (ProtocolServer server = ProtocolServer.h2c();
             ClientFixture fixture = ClientFixture.create(server.baseUrl(), true, null)) {
            Http2ContractClient client = fixture.client();

            List<String> concurrent = Mono.zip(client.slow(), client.slow())
                    .map(values -> List.of(values.getT1(), values.getT2()))
                    .block(CALL_TIMEOUT);
            assertThat(concurrent).containsExactly("slow", "slow");
            assertThat(server.maxActiveRequests()).isGreaterThanOrEqualTo(2);
            assertThat(server.transportChannelIds()).hasSize(1);

            server.resetConcurrencyEvidence();
            List<String> cancelAndSlow = Mono.zip(
                            client.cancellableStream().take(1)
                                    .map(ReactiveHttpClientHttp2ContractTest::readAndRelease)
                                    .single(),
                            client.slow())
                    .map(values -> List.of(values.getT1(), values.getT2()))
                    .block(CALL_TIMEOUT);
            assertThat(cancelAndSlow).containsExactly("first", "slow");
            server.awaitIdle();

            server.resetConcurrencyEvidence();
            String resetOutcome = client.resetStream()
                    .map(ReactiveHttpClientHttp2ContractTest::readAndRelease)
                    .then(Mono.just("completed"))
                    .onErrorReturn("reset")
                    .zipWith(client.slow(), (reset, slow) -> reset + ":" + slow)
                    .block(CALL_TIMEOUT);
            assertThat(resetOutcome).isEqualTo("reset:slow");
            assertThat(server.paths()).contains("/reset", "/slow");
            assertThat(server.transportChannelIds()).hasSize(1);
            server.awaitIdle();

            assertThat(client.probe().block(CALL_TIMEOUT)).isEqualTo("probe");
            assertThat(fixture.connectionProvider().maxConnections()).isEqualTo(1);
        }
    }

    @Test
    void h2TimeoutAndHttpErrorsReleaseTheirStreamsForLaterCalls() {
        try (ProtocolServer server = ProtocolServer.h2c();
             ClientFixture fixture = ClientFixture.create(server.baseUrl(), true, null)) {
            Http2ContractClient client = fixture.client();

            assertThat(catchThrowable(() -> client.timeout().block(CALL_TIMEOUT))).isNotNull();
            server.awaitIdle();
            assertThat(client.probe().block(CALL_TIMEOUT)).isEqualTo("probe");

            assertThat(catchThrowable(() -> client.clientError().block(CALL_TIMEOUT)))
                    .isInstanceOf(HttpClientException.class)
                    .satisfies(error -> assertThat(((HttpClientException) error).getStatusCode()).isEqualTo(422));
            assertThat(catchThrowable(() -> client.serverError().block(CALL_TIMEOUT)))
                    .isInstanceOf(RemoteServiceException.class)
                    .satisfies(error -> assertThat(((RemoteServiceException) error).getStatusCode()).isEqualTo(503));

            assertThat(client.probe().block(CALL_TIMEOUT)).isEqualTo("probe");
            assertThat(server.protocols()).containsOnly("HTTP/2.0");
            assertThat(server.transportChannelIds()).hasSize(1);
        }
    }

    @Test
    void factoryShutdownWaitsForH2ConnectionProviderDisposal() {
        try (ProtocolServer server = ProtocolServer.h2c()) {
            ClientFixture fixture = ClientFixture.create(server.baseUrl(), true, null);
            ConnectionProvider provider = fixture.connectionProvider();
            assertThat(fixture.client().probe().block(CALL_TIMEOUT)).isEqualTo("probe");
            assertThat(provider.isDisposed()).isFalse();

            fixture.close();

            assertThat(provider.isDisposed()).isTrue();
        }
    }

    private static void assertApplicationResponseShapes(Http2ContractClient client, String protocol) {
        assertThat(client.json().block(CALL_TIMEOUT)).isEqualTo(new Payload("json", protocol));

        ResponseEntity<Payload> entity = client.entity().block(CALL_TIMEOUT);
        assertThat(entity).isNotNull();
        assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(entity.getHeaders().getFirst("X-Negotiated-Protocol")).isEqualTo(protocol);
        assertThat(entity.getBody()).isEqualTo(new Payload("entity", protocol));

        String direct = client.directStream()
                .map(ReactiveHttpClientHttp2ContractTest::readAndRelease)
                .reduce("", String::concat)
                .block(CALL_TIMEOUT);
        assertThat(direct).isEqualTo("firstsecond");

        ResponseEntity<Flux<DataBuffer>> envelope = client.streamingEntity().block(CALL_TIMEOUT);
        assertThat(envelope).isNotNull();
        assertThat(envelope.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(envelope.getBody()).isNotNull();
        String envelopeBody = Mono.delay(Duration.ofMillis(50))
                .thenMany(envelope.getBody())
                .map(ReactiveHttpClientHttp2ContractTest::readAndRelease)
                .reduce("", String::concat)
                .block(CALL_TIMEOUT);
        assertThat(envelopeBody).isEqualTo("firstsecond");
    }

    private static String readAndRelease(DataBuffer buffer) {
        try {
            return buffer.toString(StandardCharsets.UTF_8);
        } finally {
            DataBufferUtils.release(buffer);
        }
    }

    private static Path writeTrustStore(Certificate certificate, String password) throws Exception {
        Path file = Files.createTempFile("reactive-http-client-http2-truststore-", ".p12");
        KeyStore store = KeyStore.getInstance("PKCS12");
        store.load(null, null);
        store.setCertificateEntry("server", certificate);
        try (FileOutputStream output = new FileOutputStream(file.toFile())) {
            store.store(output, password.toCharArray());
        }
        return file;
    }

    @ReactiveHttpClient(name = "http2-contract")
    interface Http2ContractClient {
        @GET("/json")
        Mono<Payload> json();

        @GET("/entity")
        Mono<ResponseEntity<Payload>> entity();

        @GET("/direct-stream")
        Flux<DataBuffer> directStream();

        @GET("/stream-entity")
        Mono<ResponseEntity<Flux<DataBuffer>>> streamingEntity();

        @GET("/slow")
        Mono<String> slow();

        @GET("/cancel")
        Flux<DataBuffer> cancellableStream();

        @GET("/reset")
        Flux<DataBuffer> resetStream();

        @GET("/timeout")
        @TimeoutMs(75)
        Mono<String> timeout();

        @GET("/client-error")
        Mono<String> clientError();

        @GET("/server-error")
        Mono<String> serverError();

        @GET("/probe")
        Mono<String> probe();
    }

    record Payload(String value, String protocol) {
    }

    private static final class ClientFixture implements AutoCloseable {
        private final StaticApplicationContext context;
        private final ReactiveHttpClientFactoryBean<Http2ContractClient> factory;
        private final Http2ContractClient client;
        private final ConnectionProvider connectionProvider;
        private boolean closed;

        private ClientFixture(StaticApplicationContext context,
                              ReactiveHttpClientFactoryBean<Http2ContractClient> factory,
                              Http2ContractClient client,
                              ConnectionProvider connectionProvider) {
            this.context = context;
            this.factory = factory;
            this.client = client;
            this.connectionProvider = connectionProvider;
        }

        static ClientFixture create(String baseUrl,
                                    boolean http2Enabled,
                                    ReactiveHttpClientProperties.TlsConfig tls) {
            StaticApplicationContext context = new StaticApplicationContext();
            ReactiveHttpClientProperties properties = new ReactiveHttpClientProperties();
            ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();
            config.setBaseUrl(baseUrl);
            config.setHttp2Enabled(http2Enabled);
            config.setTls(tls);
            ReactiveHttpClientProperties.ConnectionPoolConfig pool =
                    new ReactiveHttpClientProperties.ConnectionPoolConfig();
            pool.setMaxConnections(1);
            pool.setPendingAcquireTimeoutMs(2000);
            config.setPool(pool);
            properties.getClients().put("http2-contract", config);

            context.getBeanFactory().registerSingleton("reactiveHttpClientProperties", properties);
            context.getBeanFactory().registerSingleton("starterWebClientBuilder", WebClient.builder());
            context.getBeanFactory().registerSingleton("reactiveHttpClientJsonCodec", TestJsonCodecs.jsonCodec());

            ReactiveHttpClientFactoryBean<Http2ContractClient> factory = new ReactiveHttpClientFactoryBean<>();
            factory.setType(Http2ContractClient.class);
            factory.setApplicationContext(context);
            Http2ContractClient client = factory.getObject();
            return new ClientFixture(context, factory, client, connectionProvider(factory));
        }

        Http2ContractClient client() {
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

    private static final class ProtocolServer implements AutoCloseable {
        private final List<RequestRecord> records = new CopyOnWriteArrayList<>();
        private final AtomicInteger activeRequests = new AtomicInteger();
        private final AtomicInteger maxActiveRequests = new AtomicInteger();
        private final DisposableServer server;
        private final boolean secure;

        private ProtocolServer(HttpServer server, boolean secure) {
            this.secure = secure;
            this.server = server
                    .port(0)
                    .handle((request, response) -> {
                        int active = activeRequests.incrementAndGet();
                        maxActiveRequests.accumulateAndGet(active, Math::max);
                        String path = request.path().startsWith("/")
                                ? request.path()
                                : "/" + request.path();
                        request.withConnection(connection -> records.add(capture(
                                connection.channel(), request.version().text(), path)));
                        return respond(path, request.version().text(), response)
                                .doFinally(signal -> activeRequests.decrementAndGet());
                    })
                    .bindNow();
        }

        static ProtocolServer h2c() {
            return new ProtocolServer(HttpServer.create().protocol(HttpProtocol.H2C), false);
        }

        static ProtocolServer http11AndH2c() {
            return new ProtocolServer(
                    HttpServer.create().protocol(HttpProtocol.HTTP11, HttpProtocol.H2C), false);
        }

        static ProtocolServer h2(SelfSignedCertificate certificate) {
            HttpServer server = HttpServer.create()
                    .protocol(HttpProtocol.H2)
                    .secure(spec -> spec.sslContext(Http2SslContextSpec.forServer(
                            certificate.certificate(), certificate.privateKey())));
            return new ProtocolServer(server, true);
        }

        String baseUrl() {
            return (secure ? "https://localhost:" : "http://127.0.0.1:") + server.port();
        }

        List<String> protocols() {
            return records.stream().map(RequestRecord::protocol).distinct().toList();
        }

        List<String> tlsProtocols() {
            return records.stream().map(RequestRecord::tlsProtocol).filter(value -> value != null).distinct().toList();
        }

        List<String> tlsCiphers() {
            return records.stream().map(RequestRecord::tlsCipher).filter(value -> value != null).distinct().toList();
        }

        List<String> transportChannelIds() {
            return records.stream().map(RequestRecord::transportChannelId).distinct().toList();
        }

        List<String> paths() {
            return records.stream().map(RequestRecord::path).toList();
        }

        int maxActiveRequests() {
            return maxActiveRequests.get();
        }

        void resetConcurrencyEvidence() {
            awaitIdle();
            records.clear();
            maxActiveRequests.set(0);
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

        private Mono<Void> respond(String path,
                                   String protocol,
                                   reactor.netty.http.server.HttpServerResponse response) {
            return switch (path) {
                case "/json" -> json(response, "json", protocol);
                case "/entity" -> {
                    response.header("X-Negotiated-Protocol", protocol);
                    yield json(response, "entity", protocol);
                }
                case "/direct-stream", "/stream-entity" -> response.sendString(Flux.concat(
                        Mono.just("first"), Mono.delay(Duration.ofMillis(75)).thenReturn("second"))).then();
                case "/slow" -> Mono.delay(Duration.ofMillis(150))
                        .then(response.sendString(Mono.just("slow")).then());
                case "/cancel" -> response.sendString(Flux.concat(
                        Mono.just("first"), Flux.interval(Duration.ofMillis(150), Duration.ofMillis(20))
                                .map(index -> "chunk-" + index))).then();
                case "/reset" -> Mono.from(response.sendHeaders()).then(Mono.fromRunnable(() ->
                        response.withConnection(connection -> connection.channel()
                                .writeAndFlush(new DefaultHttp2ResetFrame(Http2Error.CANCEL)))));
                case "/timeout" -> Mono.delay(Duration.ofMillis(250))
                        .then(response.sendString(Mono.just("late")).then());
                case "/client-error" -> response.status(422).sendString(Mono.just("invalid")).then();
                case "/server-error" -> response.status(503).sendString(Mono.just("unavailable")).then();
                case "/probe" -> response.sendString(Mono.just("probe")).then();
                default -> response.status(HttpStatus.NOT_FOUND.value()).send();
            };
        }

        private Mono<Void> json(reactor.netty.http.server.HttpServerResponse response,
                                String value,
                                String protocol) {
            response.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
            return response.sendString(Mono.just(
                    "{\"value\":\"" + value + "\",\"protocol\":\"" + protocol + "\"}"))
                    .then();
        }

        private RequestRecord capture(Channel requestChannel, String protocol, String path) {
            Channel transport = requestChannel;
            SslHandler sslHandler = null;
            for (Channel current = requestChannel; current != null; current = current.parent()) {
                SslHandler candidate = current.pipeline().get(SslHandler.class);
                if (candidate != null) {
                    sslHandler = candidate;
                }
                transport = current;
            }
            return new RequestRecord(
                    transport.id().asLongText(),
                    protocol,
                    path,
                    sslHandler != null ? sslHandler.engine().getSession().getProtocol() : null,
                    sslHandler != null ? sslHandler.engine().getSession().getCipherSuite() : null);
        }
    }

    private record RequestRecord(String transportChannelId,
                                 String protocol,
                                 String path,
                                 String tlsProtocol,
                                 String tlsCipher) {
    }
}
