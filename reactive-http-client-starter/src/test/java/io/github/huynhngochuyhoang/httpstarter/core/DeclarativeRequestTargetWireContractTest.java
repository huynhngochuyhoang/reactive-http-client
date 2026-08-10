package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.annotation.*;
import io.github.huynhngochuyhoang.httpstarter.auth.AuthContext;
import io.github.huynhngochuyhoang.httpstarter.auth.AuthProvider;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientObserver;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientObserverEvent;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.HttpProtocol;
import reactor.netty.http.server.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

class DeclarativeRequestTargetWireContractTest {

    private static final Duration CALL_TIMEOUT = Duration.ofSeconds(5);
    private static final String ID = "raw /+%#?";
    private static final String TEMPLATE_VALUE = "a%2Fb";
    private static final List<String> METHOD_REPEAT = List.of("method one", "method/two");
    private static final String ANNOTATION_TARGET =
            "/base/items/raw%20%2F%2B%25%23%3F"
                    + "?literal=yes&repeat=template&repeat=method%20one&repeat=method/two"
                    + "&empty=&flag&template=a%252Fb&configured=first&configured=second";
    private static final String API_REF_TARGET =
            "/base/configured/raw%20%2F%2B%25%23%3F"
                    + "?literal=api&empty=&template=a%252Fb"
                    + "&configured=first&configured=second&repeat=default";

    @Test
    void http11PeerReceivesExactOriginFormRequestTargets() {
        try (RequestServer server = RequestServer.http11();
             ClientFixture fixture = ClientFixture.create(server.baseUrl() + "/base", false, null, null, false, null)) {
            assertDirectTargets(fixture.client(), server, "HTTP/1.1");
        }
    }

    @Test
    void h2cPeerReceivesExactPathAndQueryPseudoTarget() {
        try (RequestServer server = RequestServer.h2c();
             ClientFixture fixture = ClientFixture.create(server.baseUrl() + "/base", true, null, null, false, null)) {
            assertDirectTargets(fixture.client(), server, "HTTP/2.0");
        }
    }

    @Test
    void tlsPeerRetainsConfiguredAuthorityAndExactRequestTarget() throws Exception {
        SelfSignedCertificate certificate = new SelfSignedCertificate("localhost");
        Path trustStore = writeTrustStore(certificate.cert(), "changeit");
        ReactiveHttpClientProperties.TlsConfig tls = new ReactiveHttpClientProperties.TlsConfig();
        tls.setTrustStore("file:" + trustStore.toAbsolutePath());
        tls.setTrustStorePassword("changeit");
        tls.setTrustStoreType("PKCS12");
        try (RequestServer server = RequestServer.tls(certificate);
             ClientFixture fixture = ClientFixture.create(
                     server.baseUrl() + "/base", false, tls, null, false, null)) {
            assertThat(fixture.client().annotation(ID, TEMPLATE_VALUE, METHOD_REPEAT, null).block(CALL_TIMEOUT))
                    .isEqualTo("ok");
            assertThat(server.last()).satisfies(record -> {
                assertThat(record.target()).isEqualTo(ANNOTATION_TARGET);
                assertThat(record.authority()).isEqualTo("localhost:" + server.port());
                assertThat(record.protocol()).isEqualTo("HTTP/1.1");
            });
        } finally {
            Files.deleteIfExists(trustStore);
            certificate.delete();
        }
    }

    @Test
    void httpProxyUsesConfiguredConnectAuthorityAndPreservesTheOriginTarget() throws Exception {
        try (RequestServer origin = RequestServer.http11();
             HttpConnectProxy proxy = new HttpConnectProxy()) {
            ReactiveHttpClientProperties.ProxyConfig proxyConfig = new ReactiveHttpClientProperties.ProxyConfig();
            proxyConfig.setType(ReactiveHttpClientProperties.ProxyConfig.Type.HTTP);
            proxyConfig.setHost("127.0.0.1");
            proxyConfig.setPort(proxy.port());
            try (ClientFixture fixture = ClientFixture.create(
                    origin.baseUrl() + "/base", false, null, proxyConfig, false, null)) {
                assertThat(fixture.client().annotation(ID, TEMPLATE_VALUE, METHOD_REPEAT, null).block(CALL_TIMEOUT))
                        .isEqualTo("ok");

                assertThat(proxy.connectTargets()).containsExactly("127.0.0.1:" + origin.port());
                assertThat(origin.last().target()).isEqualTo(ANNOTATION_TARGET);
                assertThat(origin.last().authority()).isEqualTo("127.0.0.1:" + origin.port());
            }
        }
    }

    @Test
    void authQueryValuesReplaceEarlierValuesWithoutReencodingTheTarget() {
        AuthProvider authProvider = request -> Mono.just(AuthContext.builder()
                .queryParam("repeat", "auth one")
                .queryParam("repeat", "auth/two")
                .queryParam("auth", "raw /+%#?&=")
                .build());
        try (RequestServer server = RequestServer.http11();
             ClientFixture fixture = ClientFixture.create(
                     server.baseUrl() + "/base", false, null, null, false, authProvider)) {
            assertThat(fixture.client().annotation(ID, TEMPLATE_VALUE, METHOD_REPEAT, null).block(CALL_TIMEOUT))
                    .isEqualTo("ok");

            assertThat(server.last().target()).isEqualTo(
                    "/base/items/raw%20%2F%2B%25%23%3F"
                            + "?literal=yes&empty=&flag&template=a%252Fb"
                            + "&configured=first&configured=second"
                            + "&repeat=auth%20one&repeat=auth/two&auth=raw%20/+%25%23?%26%3D");
        }
    }

    @Test
    void redirectKeepsDeclarativeTemplateAndOriginalDispatchObservationDistinct() {
        RequestDiagnostics diagnostics = new RequestDiagnostics();
        try (RequestServer server = RequestServer.http11();
             ClientFixture fixture = ClientFixture.create(
                     server.baseUrl() + "/base", false, null, null, true, null, diagnostics)) {
            assertThat(fixture.client().redirect("a/b").block(CALL_TIMEOUT)).isEqualTo("ok");

            assertThat(server.records()).extracting(RequestRecord::target)
                    .containsExactly(
                            "/base/redirect/a%2Fb?configured=first&configured=second&repeat=default",
                            "/base/final?redirect=yes");
            String observedUrl = server.baseUrl()
                    + "/base/redirect/a%2Fb?configured=first&configured=second&repeat=default";
            assertThat(diagnostics.observerEvents).singleElement().satisfies(event -> {
                assertThat(event.getUriPath()).isEqualTo("/redirect/{id}");
                assertThat(event.getRequestUrl()).isEqualTo(observedUrl);
                assertThat(event.getStatusCode()).isEqualTo(HttpStatus.OK.value());
            });
            assertThat(diagnostics.successContexts).singleElement().satisfies(context -> {
                assertThat(context.pathTemplate()).isEqualTo("/redirect/{id}");
                assertThat(context.requestUrl()).hasToString(observedUrl);
                assertThat(context.statusCode()).isEqualTo(HttpStatus.OK.value());
            });
            assertThat(diagnostics.exchangeLogs).singleElement().satisfies(context -> {
                assertThat(context.pathTemplate()).isEqualTo("/redirect/{id}");
                assertThat(context.requestUrl()).hasToString(observedUrl);
                assertThat(context.responseStatus()).isEqualTo(HttpStatus.OK.value());
            });
        }
    }

    private static void assertDirectTargets(RequestTargetClient client,
                                            RequestServer server,
                                            String protocol) {
        assertThat(client.annotation(ID, TEMPLATE_VALUE, METHOD_REPEAT, null).block(CALL_TIMEOUT)).isEqualTo("ok");
        assertThat(client.configured(ID, TEMPLATE_VALUE, null).block(CALL_TIMEOUT)).isEqualTo("ok");

        assertThat(server.records()).extracting(RequestRecord::target)
                .containsExactly(ANNOTATION_TARGET, API_REF_TARGET);
        assertThat(server.records()).extracting(RequestRecord::protocol).containsOnly(protocol);
        assertThat(server.records()).extracting(RequestRecord::authority)
                .containsOnly("127.0.0.1:" + server.port());
    }

    private static Path writeTrustStore(Certificate certificate, String password) throws Exception {
        Path file = Files.createTempFile("request-target-truststore-", ".p12");
        KeyStore store = KeyStore.getInstance("PKCS12");
        store.load(null, null);
        store.setCertificateEntry("server", certificate);
        try (FileOutputStream output = new FileOutputStream(file.toFile())) {
            store.store(output, password.toCharArray());
        }
        return file;
    }

    @ReactiveHttpClient(name = "request-target")
    interface RequestTargetClient {
        @GET("/items/{id}?literal=yes&repeat=template&empty=&flag&template={template}")
        Mono<String> annotation(
                @PathVar("id") String id,
                @PathVar("template") String template,
                @QueryParam("repeat") List<String> repeat,
                @QueryParam("omitted") String omitted);

        @ApiRef("configured")
        Mono<String> configured(
                @PathVar("id") String id,
                @PathVar("template") String template,
                @QueryParam("omitted") String omitted);

        @LogHttpExchange(logger = RequestDiagnostics.class)
        @GET("/redirect/{id}")
        Mono<String> redirect(@PathVar("id") String id);
    }

    private static final class ClientFixture implements AutoCloseable {
        private final StaticApplicationContext context;
        private final ReactiveHttpClientFactoryBean<RequestTargetClient> factory;
        private final RequestTargetClient client;

        private ClientFixture(StaticApplicationContext context,
                              ReactiveHttpClientFactoryBean<RequestTargetClient> factory,
                              RequestTargetClient client) {
            this.context = context;
            this.factory = factory;
            this.client = client;
        }

        static ClientFixture create(String baseUrl,
                                    boolean http2,
                                    ReactiveHttpClientProperties.TlsConfig tls,
                                    ReactiveHttpClientProperties.ProxyConfig proxy,
                                    boolean followRedirects,
                                    AuthProvider authProvider) {
            return create(baseUrl, http2, tls, proxy, followRedirects, authProvider, null);
        }

        static ClientFixture create(String baseUrl,
                                    boolean http2,
                                    ReactiveHttpClientProperties.TlsConfig tls,
                                    ReactiveHttpClientProperties.ProxyConfig proxy,
                                    boolean followRedirects,
                                    AuthProvider authProvider,
                                    RequestDiagnostics diagnostics) {
            StaticApplicationContext context = new StaticApplicationContext();
            ReactiveHttpClientProperties properties = new ReactiveHttpClientProperties();
            ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();
            config.setBaseUrl(baseUrl);
            config.setHttp2Enabled(http2);
            config.setTls(tls);
            config.setProxy(proxy);
            config.setFollowRedirects(followRedirects);
            LinkedHashMap<String, List<String>> defaults = new LinkedHashMap<>();
            defaults.put("configured", List.of("first", "second"));
            defaults.put("repeat", List.of("default"));
            config.setDefaultQueryParams(defaults);
            ReactiveHttpClientProperties.ApiConfig api = new ReactiveHttpClientProperties.ApiConfig();
            api.setMethod("GET");
            api.setPath("/configured/{id}?literal=api&empty=&template={template}");
            config.setApis(java.util.Map.of("configured", api));
            if (authProvider != null) {
                config.setAuthProvider("requestTargetAuthProvider");
            }
            properties.getClients().put("request-target", config);
            context.getBeanFactory().registerSingleton("reactiveHttpClientProperties", properties);
            context.getBeanFactory().registerSingleton("starterWebClientBuilder", WebClient.builder());
            if (authProvider != null) {
                context.getBeanFactory().registerSingleton("requestTargetAuthProvider", authProvider);
            }
            if (diagnostics != null) {
                context.getBeanFactory().registerSingleton("requestTargetDiagnostics", diagnostics);
            }

            ReactiveHttpClientFactoryBean<RequestTargetClient> factory = new ReactiveHttpClientFactoryBean<>();
            factory.setType(RequestTargetClient.class);
            factory.setApplicationContext(context);
            return new ClientFixture(context, factory, factory.getObject());
        }

        RequestTargetClient client() {
            return client;
        }

        @Override
        public void close() {
            factory.destroy();
            context.close();
        }
    }

    private static final class RequestServer implements AutoCloseable {
        private final List<RequestRecord> records = new CopyOnWriteArrayList<>();
        private final DisposableServer server;
        private final boolean secure;

        private RequestServer(HttpServer httpServer, boolean secure) {
            this.secure = secure;
            this.server = httpServer.port(0)
                    .handle((request, response) -> {
                        records.add(new RequestRecord(
                                request.uri(),
                                request.requestHeaders().get(HttpHeaders.HOST),
                                request.version().text()));
                        if (request.uri().startsWith("/base/redirect/")) {
                            return response.status(HttpStatus.FOUND.value())
                                    .header(HttpHeaders.LOCATION, "/base/final?redirect=yes")
                                    .send();
                        }
                        return response.sendString(Mono.just("ok")).then();
                    })
                    .bindNow();
        }

        static RequestServer http11() {
            return new RequestServer(HttpServer.create().protocol(HttpProtocol.HTTP11), false);
        }

        static RequestServer h2c() {
            return new RequestServer(HttpServer.create().protocol(HttpProtocol.H2C), false);
        }

        static RequestServer tls(SelfSignedCertificate certificate) throws Exception {
            io.netty.handler.ssl.SslContext sslContext = io.netty.handler.ssl.SslContextBuilder.forServer(
                    certificate.certificate(), certificate.privateKey()).build();
            return new RequestServer(HttpServer.create().secure(spec -> spec.sslContext(sslContext)), true);
        }

        String baseUrl() {
            return (secure ? "https://localhost:" : "http://127.0.0.1:") + server.port();
        }

        int port() {
            return server.port();
        }

        List<RequestRecord> records() {
            return List.copyOf(records);
        }

        RequestRecord last() {
            return records.get(records.size() - 1);
        }

        @Override
        public void close() {
            server.disposeNow(CALL_TIMEOUT);
        }
    }

    private record RequestRecord(String target, String authority, String protocol) {
    }

    private static final class RequestDiagnostics
            implements HttpClientObserver, ReactiveHttpClientLifecycleHook, HttpExchangeLogger {
        private final List<HttpClientObserverEvent> observerEvents = new CopyOnWriteArrayList<>();
        private final List<ReactiveHttpClientLifecycleContext> successContexts = new CopyOnWriteArrayList<>();
        private final List<HttpExchangeLogContext> exchangeLogs = new CopyOnWriteArrayList<>();

        @Override
        public void record(HttpClientObserverEvent event) {
            observerEvents.add(event);
        }

        @Override
        public void onSuccess(ReactiveHttpClientLifecycleContext context) {
            successContexts.add(context);
        }

        @Override
        public void log(HttpExchangeLogContext context) {
            exchangeLogs.add(context);
        }
    }

    private static final class HttpConnectProxy implements AutoCloseable {
        private final ServerSocket serverSocket = new ServerSocket();
        private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        private final Set<Socket> sockets = ConcurrentHashMap.newKeySet();
        private final List<String> connectTargets = new CopyOnWriteArrayList<>();
        private volatile boolean closed;

        private HttpConnectProxy() throws IOException {
            serverSocket.bind(new InetSocketAddress("127.0.0.1", 0));
            executor.submit(this::acceptLoop);
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        List<String> connectTargets() {
            return List.copyOf(connectTargets);
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
                InputStream input = new BufferedInputStream(client.getInputStream());
                OutputStream output = new BufferedOutputStream(client.getOutputStream());
                String requestLine = readAsciiLine(input);
                String[] parts = requestLine != null ? requestLine.split(" ", 3) : new String[0];
                if (parts.length != 3 || !"CONNECT".equals(parts[0])) {
                    writeAscii(output, "HTTP/1.1 405 Method Not Allowed\r\nConnection: close\r\n\r\n");
                    return;
                }
                readHeaders(input);
                connectTargets.add(parts[1]);
                int separator = parts[1].lastIndexOf(':');
                try (Socket upstream = new Socket(
                        parts[1].substring(0, separator), Integer.parseInt(parts[1].substring(separator + 1)))) {
                    sockets.add(upstream);
                    writeAscii(output, "HTTP/1.1 200 Connection Established\r\n\r\n");
                    relay(client, upstream);
                }
            } catch (IOException ignored) {
                // Cancellation and fixture shutdown close active sockets.
            } finally {
                sockets.remove(client);
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
                // The opposite direction owns terminal socket closure.
            }
        }

        private static String readAsciiLine(InputStream input) throws IOException {
            List<Byte> bytes = new java.util.ArrayList<>();
            int previous = -1;
            int current;
            while ((current = input.read()) != -1) {
                if (previous == '\r' && current == '\n') {
                    bytes.remove(bytes.size() - 1);
                    byte[] line = new byte[bytes.size()];
                    for (int index = 0; index < bytes.size(); index++) {
                        line[index] = bytes.get(index);
                    }
                    return new String(line, java.nio.charset.StandardCharsets.US_ASCII);
                }
                bytes.add((byte) current);
                previous = current;
            }
            if (bytes.isEmpty()) {
                return null;
            }
            throw new EOFException("Unexpected end of proxy request line");
        }

        private static void readHeaders(InputStream input) throws IOException {
            String line;
            while ((line = readAsciiLine(input)) != null && !line.isEmpty()) {
                // Header content is not needed for this authority/request-target fixture.
            }
        }

        private static void writeAscii(OutputStream output, String value) throws IOException {
            output.write(value.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            output.flush();
        }

        @Override
        public void close() throws Exception {
            closed = true;
            serverSocket.close();
            for (Socket socket : sockets) {
                try {
                    socket.close();
                } catch (IOException ignored) {
                }
            }
            executor.shutdownNow();
            executor.awaitTermination(2, TimeUnit.SECONDS);
        }
    }
}
