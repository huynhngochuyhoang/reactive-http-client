package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.annotation.GET;
import io.github.huynhngochuyhoang.httpstarter.annotation.LogHttpExchange;
import io.github.huynhngochuyhoang.httpstarter.annotation.ReactiveHttpClient;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import io.github.huynhngochuyhoang.httpstarter.exception.ErrorCategories;
import io.github.huynhngochuyhoang.httpstarter.exception.ErrorCategory;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientFailureStage;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientObserver;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientObserverEvent;
import io.netty.channel.ChannelOption;
import io.netty.handler.proxy.ProxyConnectException;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.server.HttpServer;
import reactor.netty.transport.ProxyProvider;

import java.net.ServerSocket;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class PreResponseFailureAttributionContractTest {

    private static final Duration CALL_TIMEOUT = Duration.ofSeconds(5);

    @Test
    void dnsResolutionFailureIsAttributedWithoutResponseEvidence() {
        HttpClient transport = HttpClient.create()
                .resolver(spec -> spec.queryTimeout(Duration.ofMillis(250)).maxQueriesPerResolve(1));

        Throwable failure = request(transport, "http://failure-attribution.invalid/probe");

        assertFailure(failure, HttpClientFailureStage.DNS_RESOLUTION, ErrorCategory.UNKNOWN_HOST);
    }

    @Test
    void refusedLoopbackConnectionIsAttributedToConnect() throws Exception {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        HttpClient transport = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 500);

        Throwable failure = request(transport, "http://127.0.0.1:" + closedPort + "/probe");

        assertFailure(failure, HttpClientFailureStage.CONNECT, ErrorCategory.CONNECT_ERROR);
    }

    @Test
    void rejectedLocalProxyTunnelIsAttributedToProxyConnect() {
        AtomicReference<String> proxyMethod = new AtomicReference<>();
        DisposableServer proxy = HttpServer.create()
                .port(0)
                .handle((request, response) -> {
                    proxyMethod.set(request.method().name());
                    return response.status(407).send();
                })
                .bindNow();
        try {
            HttpClient transport = HttpClient.create().proxy(spec -> spec
                    .type(ProxyProvider.Proxy.HTTP)
                    .host("127.0.0.1")
                    .port(proxy.port()));

            Throwable failure = request(transport, "https://downstream.invalid/probe");

            assertThat(proxyMethod.get()).isEqualTo("CONNECT");
            assertThat(hasCause(failure, ProxyConnectException.class)).isTrue();
            assertFailure(failure, HttpClientFailureStage.PROXY_CONNECT, ErrorCategory.TLS_ERROR);
        }
        finally {
            proxy.disposeNow(CALL_TIMEOUT);
        }
    }

    @Test
    void plaintextPeerOnHttpsPortIsAttributedToTlsHandshake() {
        DisposableServer plaintext = HttpServer.create()
                .port(0)
                .handle((request, response) -> response.sendString(Mono.just("not-tls")))
                .bindNow();
        try {
            Throwable failure = request(
                    HttpClient.create(), "https://127.0.0.1:" + plaintext.port() + "/probe");

            assertFailure(failure, HttpClientFailureStage.TLS_HANDSHAKE, ErrorCategory.TLS_ERROR);
        }
        finally {
            plaintext.disposeNow(CALL_TIMEOUT);
        }
    }

    @Test
    void untrustedCertificateIsAttributedToTlsHandshake() throws Exception {
        SelfSignedCertificate certificate = new SelfSignedCertificate("localhost");
        DisposableServer tlsServer = HttpServer.create()
                .host("localhost")
                .port(0)
                .secure(spec -> {
                    try {
                        spec.sslContext(SslContextBuilder
                                .forServer(certificate.certificate(), certificate.privateKey())
                                .build());
                    }
                    catch (Exception error) {
                        throw new IllegalStateException(error);
                    }
                })
                .handle((request, response) -> response.sendString(Mono.just("secure")))
                .bindNow();
        try {
            Throwable failure = request(
                    HttpClient.create(), "https://localhost:" + tlsServer.port() + "/probe");

            assertFailure(failure, HttpClientFailureStage.TLS_HANDSHAKE, ErrorCategory.TLS_ERROR);
        }
        finally {
            tlsServer.disposeNow(CALL_TIMEOUT);
            certificate.delete();
        }
    }

    @Test
    void starterReportsConnectFailureConsistentlyAcrossTerminalSurfaces() throws Exception {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        StaticApplicationContext context = new StaticApplicationContext();
        ReactiveHttpClientProperties properties = new ReactiveHttpClientProperties();
        ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();
        config.setBaseUrl("http://127.0.0.1:" + closedPort);
        properties.getClients().put("pre-response-contract", config);
        RecordingDiagnostics recording = new RecordingDiagnostics();
        context.getBeanFactory().registerSingleton("reactiveHttpClientProperties", properties);
        context.getBeanFactory().registerSingleton("starterWebClientBuilder", WebClient.builder());
        context.getBeanFactory().registerSingleton("reactiveHttpClientJsonCodec", TestJsonCodecs.jsonCodec());
        context.getBeanFactory().registerSingleton("preResponseDiagnostics", recording);
        ReactiveHttpClientFactoryBean<PreResponseClient> factory = new ReactiveHttpClientFactoryBean<>();
        factory.setType(PreResponseClient.class);
        factory.setApplicationContext(context);
        try {
            Throwable failure = catchThrowable(() -> factory.getObject().probe().block(CALL_TIMEOUT));

            assertThat(failure).isNotNull();
            assertThat(recording.events).singleElement().satisfies(event -> {
                assertThat(event.getErrorCategory()).isEqualTo(ErrorCategory.CONNECT_ERROR);
                assertThat(event.getFailureStage()).isEqualTo(HttpClientFailureStage.CONNECT);
                assertThat(event.getStatusCode()).isNull();
            });
            assertThat(recording.lifecycleErrors).singleElement().satisfies(lifecycle -> {
                assertThat(lifecycle.failureStage()).isEqualTo(HttpClientFailureStage.CONNECT);
                assertThat(lifecycle.statusCode()).isNull();
            });
            assertThat(recording.exchangeLogs).singleElement().satisfies(exchange -> {
                assertThat(exchange.failureStage()).isEqualTo(HttpClientFailureStage.CONNECT);
                assertThat(exchange.responseStatus()).isNull();
            });
        }
        finally {
            factory.destroy();
            context.close();
        }
    }

    private static Throwable request(HttpClient transport, String baseUrl) {
        WebClient client = WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(transport))
                .build();
        Throwable failure = catchThrowable(() -> client.get()
                .retrieve()
                .bodyToMono(String.class)
                .block(CALL_TIMEOUT));
        assertThat(failure).isNotNull();
        return failure;
    }

    private static void assertFailure(
            Throwable failure,
            HttpClientFailureStage expectedStage,
            ErrorCategory expectedCategory) {
        assertThat(HttpClientFailureStage.from(failure, null, true)).isEqualTo(expectedStage);
        assertThat(ErrorCategories.from(failure)).isEqualTo(expectedCategory);
    }

    @ReactiveHttpClient(name = "pre-response-contract")
    @LogHttpExchange(logger = RecordingDiagnostics.class)
    interface PreResponseClient {
        @GET("/probe")
        Mono<String> probe();
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
    }

    private static boolean hasCause(Throwable failure, Class<? extends Throwable> expectedType) {
        Throwable current = failure;
        for (int depth = 0; current != null && depth < 16; depth++) {
            if (expectedType.isInstance(current)) {
                return true;
            }
            Throwable cause = current.getCause();
            current = cause != current ? cause : null;
        }
        return false;
    }
}
