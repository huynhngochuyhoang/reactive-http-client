package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.annotation.GET;
import io.github.huynhngochuyhoang.httpstarter.annotation.LogHttpExchange;
import io.github.huynhngochuyhoang.httpstarter.annotation.ReactiveHttpClient;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientFailureStage;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientObserver;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientObserverEvent;
import io.netty.channel.Channel;
import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.Http2SslContextSpec;
import reactor.netty.http.HttpProtocol;
import reactor.netty.http.server.HttpServer;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class ProxyAndMtlsWireContractTest {

    private static final Duration CALL_TIMEOUT = Duration.ofSeconds(8);
    private static final String STORE_PASSWORD = "test-store-password";
    private static final String PROXY_USER = "wire-user";
    private static final String PROXY_PASSWORD = "wire-proxy-password";

    @Test
    void httpTargetUsesAuthenticatedConnectTunnelWithoutExposingProxyCredentials() throws Exception {
        try (PlainTargetServer target = PlainTargetServer.start();
             TunnelProxy proxy = TunnelProxy.http(PROXY_USER, PROXY_PASSWORD);
             ClientFixture fixture = ClientFixture.create(
                     target.baseUrl("127.0.0.1"), proxyConfig(proxy, type("HTTP"), true), null, false)) {

            assertThat(fixture.client().probe().block(CALL_TIMEOUT)).isEqualTo("target:/probe");

            assertThat(proxy.records()).singleElement().satisfies(record -> {
                assertThat(record.protocol()).isEqualTo("HTTP-CONNECT");
                assertThat(record.target()).isEqualTo("127.0.0.1:" + target.port());
                assertThat(record.authenticated()).isTrue();
            });
            assertThat(target.paths()).containsExactly("/probe");
            fixture.diagnostics().assertNoProxyCredentials(PROXY_USER, PROXY_PASSWORD);
        }
    }

    @Test
    void rejectedProxyAuthenticationDoesNotExposeConfiguredCredentials() throws Exception {
        String rejectedPassword = "rejected-proxy-password";
        try (PlainTargetServer target = PlainTargetServer.start();
             TunnelProxy proxy = TunnelProxy.http(PROXY_USER, PROXY_PASSWORD)) {
            ReactiveHttpClientProperties.ProxyConfig config = proxyConfig(proxy, type("HTTP"), false);
            config.setUsername(PROXY_USER);
            config.setPassword(rejectedPassword);
            try (ClientFixture fixture = ClientFixture.create(target.baseUrl("127.0.0.1"), config, null, false)) {
                Throwable failure = catchThrowable(() -> fixture.client().probe().block(CALL_TIMEOUT));

                assertThat(failure).isNotNull();
                assertThat(text(failure)).doesNotContain(PROXY_USER, rejectedPassword, PROXY_PASSWORD);
                fixture.diagnostics().assertFailureDoesNotContain(PROXY_USER, rejectedPassword, PROXY_PASSWORD);
                assertThat(target.paths()).isEmpty();
            }
        }
    }

    @Test
    void httpAndHttpsProxyTypesUseTheSameConnectTransportForHttpsTargets() throws Exception {
        SelfSignedCertificate serverIdentity = new SelfSignedCertificate("localhost");
        Path trustStore = writeTrustStore(serverIdentity.cert(), STORE_PASSWORD);
        DisposableServer target = startTlsTarget(serverIdentity);
        try (TunnelProxy proxy = TunnelProxy.http(null, null)) {
            for (ReactiveHttpClientProperties.ProxyConfig.Type proxyType : List.of(type("HTTP"), type("HTTPS"))) {
                ReactiveHttpClientProperties.TlsConfig tls = trustStore(trustStore);
                try (ClientFixture fixture = ClientFixture.create(
                        "https://localhost:" + target.port(), proxyConfig(proxy, proxyType, false), tls, false)) {
                    assertThat(fixture.client().probe().block(CALL_TIMEOUT)).isEqualTo("secure-target");
                }
            }

            assertThat(proxy.records())
                    .hasSize(2)
                    .allSatisfy(record -> {
                        assertThat(record.protocol()).isEqualTo("HTTP-CONNECT");
                        assertThat(record.target()).isEqualTo("localhost:" + target.port());
                    });
        } finally {
            target.disposeNow(CALL_TIMEOUT);
            Files.deleteIfExists(trustStore);
            serverIdentity.delete();
        }
    }

    @Test
    void nonProxyHostsUsesJavaRegexForBothBypassAndProxyPaths() throws Exception {
        try (PlainTargetServer target = PlainTargetServer.start();
             TunnelProxy proxy = TunnelProxy.http(null, null)) {
            ReactiveHttpClientProperties.ProxyConfig bypass = proxyConfig(proxy, type("HTTP"), false);
            bypass.setNonProxyHosts("localhost");
            try (ClientFixture fixture = ClientFixture.create(target.baseUrl("localhost"), bypass, null, false)) {
                assertThat(fixture.client().probe().block(CALL_TIMEOUT)).isEqualTo("target:/probe");
            }
            assertThat(proxy.records()).isEmpty();

            ReactiveHttpClientProperties.ProxyConfig routed = proxyConfig(proxy, type("HTTP"), false);
            routed.setNonProxyHosts("localhost");
            try (ClientFixture fixture = ClientFixture.create(target.baseUrl("127.0.0.1"), routed, null, false)) {
                assertThat(fixture.client().probe().block(CALL_TIMEOUT)).isEqualTo("target:/probe");
            }

            assertThat(proxy.records()).singleElement().satisfies(record -> {
                assertThat(record.protocol()).isEqualTo("HTTP-CONNECT");
                assertThat(record.target()).isEqualTo("127.0.0.1:" + target.port());
            });
            assertThat(target.paths()).containsExactly("/probe", "/probe");
        }
    }

    @Test
    void socks4AndSocks5ReachTheTargetThroughLocalTunnels() throws Exception {
        try (PlainTargetServer target = PlainTargetServer.start()) {
            for (ReactiveHttpClientProperties.ProxyConfig.Type proxyType :
                    List.of(type("SOCKS4"), type("SOCKS5"))) {
                try (TunnelProxy proxy = TunnelProxy.socks(proxyType);
                     ClientFixture fixture = ClientFixture.create(
                             target.baseUrl("127.0.0.1"), proxyConfig(proxy, proxyType, false), null, false)) {
                    assertThat(fixture.client().probe().block(CALL_TIMEOUT)).isEqualTo("target:/probe");
                    assertThat(proxy.records()).singleElement().satisfies(record -> {
                        assertThat(record.protocol()).isEqualTo(proxyType.name());
                        assertThat(record.target()).isEqualTo("127.0.0.1:" + target.port());
                    });
                }
            }
        }
    }

    @Test
    void configuredTrustedClientIdentitySucceedsOverHttp11AndTlsH2() throws Exception {
        SelfSignedCertificate serverIdentity = new SelfSignedCertificate("localhost");
        SelfSignedCertificate trustedClient = new SelfSignedCertificate("trusted-client");
        Path trustStore = writeTrustStore(serverIdentity.cert(), STORE_PASSWORD);
        Path keyStore = writeKeyStore(trustedClient.key(), trustedClient.cert(), STORE_PASSWORD);
        try {
            for (boolean http2 : List.of(false, true)) {
                try (MtlsServer server = MtlsServer.start(serverIdentity, trustedClient.cert(), http2);
                     ClientFixture fixture = ClientFixture.create(
                             server.baseUrl(), null, mtlsConfig(trustStore, keyStore), http2)) {
                    assertThat(fixture.client().probe().block(CALL_TIMEOUT)).isEqualTo("mutual-tls");
                    assertThat(server.peerPrincipals()).singleElement()
                            .asString()
                            .contains("CN=trusted-client");
                    assertThat(server.protocols()).singleElement()
                            .isEqualTo(http2 ? "HTTP/2.0" : "HTTP/1.1");
                }
            }
        } finally {
            Files.deleteIfExists(keyStore);
            Files.deleteIfExists(trustStore);
            trustedClient.delete();
            serverIdentity.delete();
        }
    }

    @Test
    void missingAndUntrustedClientIdentitiesFailAsTlsHandshakeWithoutSecretLeak() throws Exception {
        SelfSignedCertificate serverIdentity = new SelfSignedCertificate("localhost");
        SelfSignedCertificate trustedClient = new SelfSignedCertificate("trusted-client");
        SelfSignedCertificate untrustedClient = new SelfSignedCertificate("untrusted-client");
        Path trustStore = writeTrustStore(serverIdentity.cert(), STORE_PASSWORD);
        Path untrustedKeyStore = writeKeyStore(untrustedClient.key(), untrustedClient.cert(), STORE_PASSWORD);
        try (MtlsServer server = MtlsServer.start(serverIdentity, trustedClient.cert(), false)) {
            assertMtlsFailure(server.baseUrl(), trustStore, null);
            assertMtlsFailure(server.baseUrl(), trustStore, untrustedKeyStore);
            assertThat(server.peerPrincipals()).isEmpty();
        } finally {
            Files.deleteIfExists(untrustedKeyStore);
            Files.deleteIfExists(trustStore);
            untrustedClient.delete();
            trustedClient.delete();
            serverIdentity.delete();
        }
    }

    private static void assertMtlsFailure(String baseUrl, Path trustStore, Path keyStore) {
        ReactiveHttpClientProperties.TlsConfig tls = keyStore == null
                ? trustStore(trustStore)
                : mtlsConfig(trustStore, keyStore);
        try (ClientFixture fixture = ClientFixture.create(baseUrl, null, tls, false)) {
            Throwable failure = catchThrowable(() -> fixture.client().probe().block(CALL_TIMEOUT));

            assertThat(failure).isNotNull();
            assertThat(HttpClientFailureStage.from(failure, null, true))
                    .isEqualTo(HttpClientFailureStage.TLS_HANDSHAKE);
            assertThat(fixture.diagnostics().events).singleElement().satisfies(event -> {
                assertThat(event.getFailureStage()).isEqualTo(HttpClientFailureStage.TLS_HANDSHAKE);
                assertThat(event.getStatusCode()).isNull();
                assertThat(text(event.getError())).doesNotContain(STORE_PASSWORD);
            });
            assertThat(fixture.diagnostics().lifecycleErrors).singleElement().satisfies(context -> {
                assertThat(context.failureStage()).isEqualTo(HttpClientFailureStage.TLS_HANDSHAKE);
                assertThat(text(context.error())).doesNotContain(STORE_PASSWORD);
            });
            assertThat(fixture.diagnostics().exchangeLogs).singleElement().satisfies(log -> {
                assertThat(log.failureStage()).isEqualTo(HttpClientFailureStage.TLS_HANDSHAKE);
                assertThat(text(log.error())).doesNotContain(STORE_PASSWORD);
            });
        }
    }

    private static DisposableServer startTlsTarget(SelfSignedCertificate identity) throws Exception {
        return HttpServer.create()
                .host("localhost")
                .port(0)
                .secure(spec -> {
                    try {
                        spec.sslContext(SslContextBuilder.forServer(identity.key(), identity.cert()).build());
                    } catch (Exception error) {
                        throw new IllegalStateException(error);
                    }
                })
                .route(routes -> routes.get("/probe",
                        (request, response) -> response.sendString(Mono.just("secure-target"))))
                .bindNow();
    }

    private static ReactiveHttpClientProperties.ProxyConfig proxyConfig(
            TunnelProxy proxy,
            ReactiveHttpClientProperties.ProxyConfig.Type type,
            boolean authenticated) {
        ReactiveHttpClientProperties.ProxyConfig config = new ReactiveHttpClientProperties.ProxyConfig();
        config.setType(type);
        config.setHost("127.0.0.1");
        config.setPort(proxy.port());
        if (authenticated) {
            config.setUsername(PROXY_USER);
            config.setPassword(PROXY_PASSWORD);
        }
        return config;
    }

    private static ReactiveHttpClientProperties.ProxyConfig.Type type(String value) {
        return ReactiveHttpClientProperties.ProxyConfig.Type.valueOf(value);
    }

    private static ReactiveHttpClientProperties.TlsConfig trustStore(Path trustStore) {
        ReactiveHttpClientProperties.TlsConfig tls = new ReactiveHttpClientProperties.TlsConfig();
        tls.setTrustStore("file:" + trustStore.toAbsolutePath());
        tls.setTrustStorePassword(STORE_PASSWORD);
        tls.setTrustStoreType("PKCS12");
        return tls;
    }

    private static ReactiveHttpClientProperties.TlsConfig mtlsConfig(Path trustStore, Path keyStore) {
        ReactiveHttpClientProperties.TlsConfig tls = trustStore(trustStore);
        tls.setKeyStore("file:" + keyStore.toAbsolutePath());
        tls.setKeyStorePassword(STORE_PASSWORD);
        tls.setKeyStoreType("PKCS12");
        return tls;
    }

    private static Path writeTrustStore(Certificate certificate, String password) throws Exception {
        Path file = Files.createTempFile("reactive-http-client-wire-trust-", ".p12");
        KeyStore store = KeyStore.getInstance("PKCS12");
        store.load(null, null);
        store.setCertificateEntry("server", certificate);
        try (FileOutputStream output = new FileOutputStream(file.toFile())) {
            store.store(output, password.toCharArray());
        }
        return file;
    }

    private static Path writeKeyStore(PrivateKey key, Certificate certificate, String password) throws Exception {
        Path file = Files.createTempFile("reactive-http-client-wire-key-", ".p12");
        KeyStore store = KeyStore.getInstance("PKCS12");
        store.load(null, null);
        store.setKeyEntry("client", key, password.toCharArray(), new Certificate[]{certificate});
        try (FileOutputStream output = new FileOutputStream(file.toFile())) {
            store.store(output, password.toCharArray());
        }
        return file;
    }

    private static String text(Throwable error) {
        StringBuilder text = new StringBuilder();
        Throwable current = error;
        for (int depth = 0; current != null && depth < 16; depth++) {
            text.append(current).append('\n');
            current = current.getCause();
        }
        return text.toString();
    }

    @ReactiveHttpClient(name = "proxy-mtls-wire")
    @LogHttpExchange(logger = RecordingDiagnostics.class)
    interface WireClient {
        @GET("/probe")
        Mono<String> probe();
    }

    private static final class ClientFixture implements AutoCloseable {
        private final StaticApplicationContext context;
        private final ReactiveHttpClientFactoryBean<WireClient> factory;
        private final WireClient client;
        private final RecordingDiagnostics diagnostics;

        private ClientFixture(StaticApplicationContext context,
                              ReactiveHttpClientFactoryBean<WireClient> factory,
                              WireClient client,
                              RecordingDiagnostics diagnostics) {
            this.context = context;
            this.factory = factory;
            this.client = client;
            this.diagnostics = diagnostics;
        }

        static ClientFixture create(String baseUrl,
                                    ReactiveHttpClientProperties.ProxyConfig proxy,
                                    ReactiveHttpClientProperties.TlsConfig tls,
                                    boolean http2) {
            StaticApplicationContext context = new StaticApplicationContext();
            ReactiveHttpClientProperties properties = new ReactiveHttpClientProperties();
            ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();
            config.setBaseUrl(baseUrl);
            config.setProxy(proxy);
            config.setTls(tls);
            config.setHttp2Enabled(http2);
            properties.getClients().put("proxy-mtls-wire", config);
            RecordingDiagnostics diagnostics = new RecordingDiagnostics();

            context.getBeanFactory().registerSingleton("reactiveHttpClientProperties", properties);
            context.getBeanFactory().registerSingleton("starterWebClientBuilder", WebClient.builder());
            context.getBeanFactory().registerSingleton("reactiveHttpClientJsonCodec", TestJsonCodecs.jsonCodec());
            context.getBeanFactory().registerSingleton("wireDiagnostics", diagnostics);

            ReactiveHttpClientFactoryBean<WireClient> factory = new ReactiveHttpClientFactoryBean<>();
            factory.setType(WireClient.class);
            factory.setApplicationContext(context);
            return new ClientFixture(context, factory, factory.getObject(), diagnostics);
        }

        WireClient client() {
            return client;
        }

        RecordingDiagnostics diagnostics() {
            return diagnostics;
        }

        @Override
        public void close() {
            factory.destroy();
            context.close();
        }
    }

    private static final class RecordingDiagnostics implements HttpClientObserver,
            ReactiveHttpClientLifecycleHook, HttpExchangeLogger {
        private final List<HttpClientObserverEvent> events = new CopyOnWriteArrayList<>();
        private final List<ReactiveHttpClientLifecycleContext> lifecycleErrors = new CopyOnWriteArrayList<>();
        private final List<HttpExchangeLogContext> exchangeLogs = new CopyOnWriteArrayList<>();

        @Override
        public void record(HttpClientObserverEvent event) {
            events.add(event);
        }

        @Override
        public void onError(ReactiveHttpClientLifecycleContext context) {
            lifecycleErrors.add(context);
        }

        @Override
        public void log(HttpExchangeLogContext context) {
            exchangeLogs.add(context);
        }

        void assertNoProxyCredentials(String username, String password) {
            assertThat(events).singleElement().satisfies(event -> {
                assertThat(event.getRequestHeaders()).doesNotContainKey("Proxy-Authorization");
                assertThat(event.toString()).doesNotContain(username, password);
            });
            assertThat(exchangeLogs).singleElement().satisfies(log -> {
                assertThat(log.requestHeaders()).doesNotContainKey("Proxy-Authorization");
                assertThat(log.toString()).doesNotContain(username, password);
            });
        }

        void assertFailureDoesNotContain(String... secrets) {
            assertThat(events).singleElement().satisfies(event -> {
                assertThat(event.getRequestHeaders()).doesNotContainKey("Proxy-Authorization");
                assertThat(text(event.getError())).doesNotContain(secrets);
            });
            assertThat(lifecycleErrors).singleElement()
                    .satisfies(context -> assertThat(text(context.error())).doesNotContain(secrets));
            assertThat(exchangeLogs).singleElement().satisfies(log -> {
                assertThat(log.requestHeaders()).doesNotContainKey("Proxy-Authorization");
                assertThat(text(log.error())).doesNotContain(secrets);
            });
        }
    }

    private static final class PlainTargetServer implements AutoCloseable {
        private final List<String> paths = new CopyOnWriteArrayList<>();
        private final DisposableServer server;

        private PlainTargetServer() {
            server = HttpServer.create()
                    .port(0)
                    .handle((request, response) -> {
                        paths.add(request.uri());
                        return response.sendString(Mono.just("target:" + request.uri()));
                    })
                    .bindNow();
        }

        static PlainTargetServer start() {
            return new PlainTargetServer();
        }

        int port() {
            return server.port();
        }

        String baseUrl(String host) {
            return "http://" + host + ":" + port();
        }

        List<String> paths() {
            return List.copyOf(paths);
        }

        @Override
        public void close() {
            server.disposeNow(CALL_TIMEOUT);
        }
    }

    private static final class MtlsServer implements AutoCloseable {
        private final List<String> peerPrincipals = new CopyOnWriteArrayList<>();
        private final List<String> protocols = new CopyOnWriteArrayList<>();
        private final DisposableServer server;

        private MtlsServer(SelfSignedCertificate serverIdentity,
                           X509Certificate trustedClient,
                           boolean http2) throws Exception {
            HttpServer configured = HttpServer.create().host("localhost").port(0);
            if (http2) {
                Http2SslContextSpec ssl = Http2SslContextSpec.forServer(serverIdentity.key(), serverIdentity.cert())
                        .configure(builder -> builder
                                .trustManager(trustedClient)
                                .clientAuth(ClientAuth.REQUIRE));
                configured = configured.protocol(HttpProtocol.H2).secure(spec -> spec.sslContext(ssl));
            } else {
                configured = configured.secure(spec -> {
                    try {
                        spec.sslContext(SslContextBuilder
                                .forServer(serverIdentity.key(), serverIdentity.cert())
                                .trustManager(trustedClient)
                                .clientAuth(ClientAuth.REQUIRE)
                                .build());
                    } catch (Exception error) {
                        throw new IllegalStateException(error);
                    }
                });
            }
            server = configured.handle((request, response) -> {
                        protocols.add(request.protocol());
                        request.withConnection(connection -> peerPrincipals.add(peerPrincipal(connection.channel())));
                        return response.sendString(Mono.just("mutual-tls"));
                    })
                    .bindNow();
        }

        static MtlsServer start(SelfSignedCertificate serverIdentity,
                                X509Certificate trustedClient,
                                boolean http2) throws Exception {
            return new MtlsServer(serverIdentity, trustedClient, http2);
        }

        String baseUrl() {
            return "https://localhost:" + server.port();
        }

        List<String> peerPrincipals() {
            return List.copyOf(peerPrincipals);
        }

        List<String> protocols() {
            return List.copyOf(protocols);
        }

        @Override
        public void close() {
            server.disposeNow(CALL_TIMEOUT);
        }

        private static String peerPrincipal(Channel channel) {
            Channel current = channel;
            while (current != null) {
                SslHandler handler = current.pipeline().get(SslHandler.class);
                if (handler != null) {
                    try {
                        return handler.engine().getSession().getPeerPrincipal().getName();
                    } catch (Exception error) {
                        throw new IllegalStateException("Client identity unavailable after mTLS handshake", error);
                    }
                }
                current = current.parent();
            }
            throw new IllegalStateException("TLS handler not found for request channel");
        }
    }

    private static final class TunnelProxy implements AutoCloseable {
        private final ProxyProtocol protocol;
        private final String username;
        private final String password;
        private final ServerSocket serverSocket;
        private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        private final Set<Socket> sockets = ConcurrentHashMap.newKeySet();
        private final List<ProxyRecord> records = new CopyOnWriteArrayList<>();
        private volatile boolean closed;

        private TunnelProxy(ProxyProtocol protocol, String username, String password) throws IOException {
            this.protocol = protocol;
            this.username = username;
            this.password = password;
            this.serverSocket = new ServerSocket();
            serverSocket.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
            executor.submit(this::acceptLoop);
        }

        static TunnelProxy http(String username, String password) throws IOException {
            return new TunnelProxy(ProxyProtocol.HTTP_CONNECT, username, password);
        }

        static TunnelProxy socks(ReactiveHttpClientProperties.ProxyConfig.Type type) throws IOException {
            return new TunnelProxy(type == ReactiveHttpClientProperties.ProxyConfig.Type.SOCKS4
                    ? ProxyProtocol.SOCKS4 : ProxyProtocol.SOCKS5, null, null);
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        List<ProxyRecord> records() {
            return List.copyOf(records);
        }

        private void acceptLoop() {
            while (!closed) {
                try {
                    Socket socket = serverSocket.accept();
                    sockets.add(socket);
                    executor.submit(() -> handle(socket));
                } catch (IOException error) {
                    if (!closed) {
                        throw new IllegalStateException("Proxy accept failed", error);
                    }
                }
            }
        }

        private void handle(Socket client) {
            try (client) {
                switch (protocol) {
                    case HTTP_CONNECT -> handleHttpConnect(client);
                    case SOCKS4 -> handleSocks4(client);
                    case SOCKS5 -> handleSocks5(client);
                }
            } catch (IOException ignored) {
                // Client cancellation and fixture shutdown close active tunnel sockets.
            } finally {
                sockets.remove(client);
            }
        }

        private void handleHttpConnect(Socket client) throws IOException {
            InputStream input = new BufferedInputStream(client.getInputStream());
            OutputStream output = new BufferedOutputStream(client.getOutputStream());
            String requestLine = readAsciiLine(input);
            if (requestLine == null) {
                return;
            }
            String[] parts = requestLine.split(" ", 3);
            if (parts.length != 3 || !parts[0].equals("CONNECT")) {
                writeAscii(output, "HTTP/1.1 405 Method Not Allowed\r\nConnection: close\r\n\r\n");
                return;
            }
            Map<String, String> headers = readHeaders(input);
            boolean authenticated = username == null
                    || expectedProxyAuthorization().equals(headers.get("proxy-authorization"));
            if (username != null && !authenticated) {
                writeAscii(output, "HTTP/1.1 407 Proxy Authentication Required\r\n"
                        + "Proxy-Authenticate: Basic realm=\"wire\"\r\nConnection: close\r\n\r\n");
                return;
            }
            HostPort target = HostPort.parse(parts[1]);
            records.add(new ProxyRecord("HTTP-CONNECT", target.toString(), username == null || authenticated));
            try (Socket upstream = new Socket(target.host(), target.port())) {
                sockets.add(upstream);
                writeAscii(output, "HTTP/1.1 200 Connection Established\r\n\r\n");
                relay(client, upstream);
            } finally {
                sockets.removeIf(Socket::isClosed);
            }
        }

        private void handleSocks4(Socket client) throws IOException {
            DataInputStream input = new DataInputStream(new BufferedInputStream(client.getInputStream()));
            DataOutputStream output = new DataOutputStream(new BufferedOutputStream(client.getOutputStream()));
            int version = input.readUnsignedByte();
            int command = input.readUnsignedByte();
            int port = input.readUnsignedShort();
            byte[] address = input.readNBytes(4);
            readNullTerminated(input);
            if (version != 4 || command != 1 || address.length != 4) {
                throw new IOException("Unsupported SOCKS4 request");
            }
            String host;
            if (address[0] == 0 && address[1] == 0 && address[2] == 0 && address[3] != 0) {
                host = readNullTerminated(input);
            } else {
                host = InetAddress.getByAddress(address).getHostAddress();
            }
            records.add(new ProxyRecord("SOCKS4", host + ":" + port, true));
            try (Socket upstream = new Socket(host, port)) {
                sockets.add(upstream);
                output.write(new byte[]{0, 90, 0, 0, 0, 0, 0, 0});
                output.flush();
                relay(client, upstream);
            } finally {
                sockets.removeIf(Socket::isClosed);
            }
        }

        private void handleSocks5(Socket client) throws IOException {
            DataInputStream input = new DataInputStream(new BufferedInputStream(client.getInputStream()));
            DataOutputStream output = new DataOutputStream(new BufferedOutputStream(client.getOutputStream()));
            if (input.readUnsignedByte() != 5) {
                throw new IOException("Unsupported SOCKS5 greeting");
            }
            input.readNBytes(input.readUnsignedByte());
            output.write(new byte[]{5, 0});
            output.flush();

            int version = input.readUnsignedByte();
            int command = input.readUnsignedByte();
            input.readUnsignedByte();
            int addressType = input.readUnsignedByte();
            String host = switch (addressType) {
                case 1 -> InetAddress.getByAddress(input.readNBytes(4)).getHostAddress();
                case 3 -> new String(input.readNBytes(input.readUnsignedByte()), StandardCharsets.US_ASCII);
                case 4 -> InetAddress.getByAddress(input.readNBytes(16)).getHostAddress();
                default -> throw new IOException("Unsupported SOCKS5 address type " + addressType);
            };
            int port = input.readUnsignedShort();
            if (version != 5 || command != 1) {
                throw new IOException("Unsupported SOCKS5 request");
            }
            records.add(new ProxyRecord("SOCKS5", host + ":" + port, true));
            try (Socket upstream = new Socket(host, port)) {
                sockets.add(upstream);
                output.write(new byte[]{5, 0, 0, 1, 0, 0, 0, 0, 0, 0});
                output.flush();
                relay(client, upstream);
            } finally {
                sockets.removeIf(Socket::isClosed);
            }
        }

        private void relay(Socket client, Socket upstream) throws IOException {
            Future<?> clientToUpstream = executor.submit(() -> copy(client, upstream));
            copy(upstream, client);
            try {
                clientToUpstream.get(1, TimeUnit.SECONDS);
            } catch (Exception ignored) {
                clientToUpstream.cancel(true);
            }
        }

        private static void copy(Socket source, Socket target) {
            try {
                source.getInputStream().transferTo(target.getOutputStream());
                target.shutdownOutput();
            } catch (IOException ignored) {
                // The opposite tunnel direction owns terminal socket closure.
            }
        }

        private String expectedProxyAuthorization() {
            if (username == null) {
                return null;
            }
            return "Basic " + Base64.getEncoder().encodeToString(
                    (username + ":" + password).getBytes(StandardCharsets.ISO_8859_1));
        }

        @Override
        public void close() throws Exception {
            closed = true;
            serverSocket.close();
            sockets.forEach(socket -> {
                try {
                    socket.close();
                } catch (IOException ignored) {
                }
            });
            executor.shutdownNow();
            executor.awaitTermination(2, TimeUnit.SECONDS);
        }

        private static String readAsciiLine(InputStream input) throws IOException {
            List<Byte> bytes = new ArrayList<>();
            int previous = -1;
            int current;
            while ((current = input.read()) != -1) {
                if (previous == '\r' && current == '\n') {
                    bytes.remove(bytes.size() - 1);
                    byte[] line = new byte[bytes.size()];
                    for (int i = 0; i < bytes.size(); i++) {
                        line[i] = bytes.get(i);
                    }
                    return new String(line, StandardCharsets.US_ASCII);
                }
                bytes.add((byte) current);
                previous = current;
            }
            return bytes.isEmpty() ? null : throwEof();
        }

        private static String throwEof() throws EOFException {
            throw new EOFException("Unexpected end of proxy request line");
        }

        private static Map<String, String> readHeaders(InputStream input) throws IOException {
            Map<String, String> headers = new LinkedHashMap<>();
            String line;
            while ((line = readAsciiLine(input)) != null && !line.isEmpty()) {
                int separator = line.indexOf(':');
                if (separator > 0) {
                    headers.put(line.substring(0, separator).trim().toLowerCase(Locale.ROOT),
                            line.substring(separator + 1).trim());
                }
            }
            return headers;
        }

        private static String readNullTerminated(InputStream input) throws IOException {
            List<Byte> bytes = new ArrayList<>();
            int value;
            while ((value = input.read()) > 0) {
                bytes.add((byte) value);
            }
            if (value < 0) {
                throw new EOFException("Unexpected end of SOCKS string");
            }
            byte[] result = new byte[bytes.size()];
            for (int i = 0; i < bytes.size(); i++) {
                result[i] = bytes.get(i);
            }
            return new String(result, StandardCharsets.US_ASCII);
        }

        private static void writeAscii(OutputStream output, String value) throws IOException {
            output.write(value.getBytes(StandardCharsets.US_ASCII));
            output.flush();
        }
    }

    private record ProxyRecord(String protocol, String target, boolean authenticated) {
    }

    private record HostPort(String host, int port) {
        static HostPort parse(String authority) {
            URI uri = URI.create("http://" + authority);
            return new HostPort(uri.getHost(), uri.getPort());
        }

        @Override
        public String toString() {
            return host + ":" + port;
        }
    }

    private enum ProxyProtocol {
        HTTP_CONNECT,
        SOCKS4,
        SOCKS5
    }
}
