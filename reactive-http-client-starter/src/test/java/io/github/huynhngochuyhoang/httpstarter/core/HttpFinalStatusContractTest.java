package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.annotation.*;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import io.github.huynhngochuyhoang.httpstarter.exception.ErrorCategory;
import io.github.huynhngochuyhoang.httpstarter.exception.HttpClientException;
import io.github.huynhngochuyhoang.httpstarter.exception.RemoteServiceException;
import io.github.huynhngochuyhoang.httpstarter.observability.Boot4HttpClientHealthIndicator;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientObserver;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientObserverEvent;
import io.github.huynhngochuyhoang.httpstarter.observability.MicrometerHttpClientObserver;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.HttpProtocol;
import reactor.netty.http.server.HttpServer;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpFinalStatusContractTest {

    private static final Duration CALL_TIMEOUT = Duration.ofSeconds(5);
    private static final String FINAL_HEADER = "X-Final-Status";

    @ParameterizedTest
    @EnumSource(WireProtocol.class)
    void finalStatusAndBodilessContractsRemainAligned(WireProtocol protocol) {
        AtomicInteger mapperCalls = new AtomicInteger();
        ErrorResponseMapper mapper = context -> {
            mapperCalls.incrementAndGet();
            return Optional.empty();
        };
        StatusDiagnostics diagnostics = new StatusDiagnostics();
        Boot4HttpClientHealthIndicator healthIndicator =
                new Boot4HttpClientHealthIndicator(diagnostics.registry, diagnostics.observability);
        healthIndicator.health();

        try (StatusServer server = new StatusServer(protocol);
             ClientFixture fixture = ClientFixture.create(server.baseUrl(), protocol, diagnostics, mapper)) {
            FinalStatusClient client = fixture.client;

            StepVerifier.create(client.headVoid()).verifyComplete();
            assertThat(client.headBody().blockOptional(CALL_TIMEOUT)).isEmpty();
            ResponseEntity<Void> head = client.head().block(CALL_TIMEOUT);
            assertThat(head).isNotNull();
            assertThat(head.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(head.getHeaders().getFirst(FINAL_HEADER)).isEqualTo("200");
            assertThat(head.getBody()).isNull();
            ResponseEntity<String> headBody = client.headEntityBody().block(CALL_TIMEOUT);
            assertThat(headBody).isNotNull();
            assertThat(headBody.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(headBody.getHeaders().getFirst(FINAL_HEADER)).isEqualTo("200");
            assertThat(headBody.getBody()).isNull();

            ResponseEntity<String> options = client.options().block(CALL_TIMEOUT);
            assertThat(options).isNotNull();
            assertThat(options.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(options.getHeaders().getFirst(HttpHeaders.ALLOW)).isEqualTo("GET,HEAD,OPTIONS");
            assertThat(options.getBody()).isEqualTo("options");

            for (int status : List.of(204, 205, 304)) {
                StepVerifier.create(client.statusVoid(status)).verifyComplete();
                assertThat(client.statusBody(status).blockOptional(CALL_TIMEOUT)).isEmpty();

                ResponseEntity<Void> voidEntity = client.statusVoidEntity(status).block(CALL_TIMEOUT);
                assertThat(voidEntity).isNotNull();
                assertThat(voidEntity.getStatusCode().value()).isEqualTo(status);
                assertThat(voidEntity.getHeaders().getFirst(FINAL_HEADER)).isEqualTo(String.valueOf(status));
                assertThat(voidEntity.getBody()).isNull();

                ResponseEntity<String> stringEntity = client.statusStringEntity(status).block(CALL_TIMEOUT);
                assertThat(stringEntity).isNotNull();
                assertThat(stringEntity.getStatusCode().value()).isEqualTo(status);
                assertThat(stringEntity.getHeaders().getFirst(FINAL_HEADER)).isEqualTo(String.valueOf(status));
                assertThat(stringEntity.getBody()).isNull();
            }

            ResponseEntity<String> redirect = client.visibleRedirect().block(CALL_TIMEOUT);
            assertThat(redirect).isNotNull();
            assertThat(redirect.getStatusCode()).isEqualTo(HttpStatus.FOUND);
            assertThat(redirect.getHeaders().getLocation()).hasToString("/final");
            assertThat(redirect.getBody()).isEqualTo("visible-redirect");
            assertThat(mapperCalls).hasValue(0);

            assertThatThrownBy(() -> client.clientError().block(CALL_TIMEOUT))
                    .isInstanceOf(HttpClientException.class)
                    .satisfies(error -> assertThat(((HttpClientException) error).getStatusCode()).isEqualTo(400));
            assertThatThrownBy(() -> client.serverError().block(CALL_TIMEOUT))
                    .isInstanceOf(RemoteServiceException.class)
                    .satisfies(error -> assertThat(((RemoteServiceException) error).getStatusCode()).isEqualTo(503));
            assertThat(mapperCalls).hasValue(2);

            diagnostics.assertTerminalFacts();
            assertMetrics(diagnostics.registry);
            assertHealth(healthIndicator.health(), diagnostics.observerEvents.size());
            assertThat(server.requests).hasSize(20);
            assertThat(server.requests).extracting(RequestRecord::protocol)
                    .containsOnly(protocol.responseVersion);
            assertThat(server.requests).anySatisfy(request -> {
                assertThat(request.method).isEqualTo("HEAD");
                assertThat(request.path).isEqualTo("/head");
            });
            assertThat(server.requests).anySatisfy(request -> {
                assertThat(request.method).isEqualTo("OPTIONS");
                assertThat(request.path).isEqualTo("/options");
            });
        }
    }

    private static void assertMetrics(SimpleMeterRegistry registry) {
        long redirectCount = registry.find("reactive.http.client.requests")
                .tag("http.status_code", "304")
                .tag("outcome", "REDIRECTION")
                .tag("error.category", "none")
                .timers()
                .stream()
                .mapToLong(Timer::count)
                .sum();
        assertThat(redirectCount).isEqualTo(4);

        Timer clientError = registry.find("reactive.http.client.requests")
                .tag("http.status_code", "400")
                .tag("outcome", "CLIENT_ERROR")
                .tag("error.category", ErrorCategory.CLIENT_ERROR.name())
                .timer();
        assertThat(clientError).isNotNull();
        assertThat(clientError.count()).isEqualTo(1);
    }

    @SuppressWarnings("unchecked")
    private static void assertHealth(Health health, int samples) {
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        Map<String, Object> client = (Map<String, Object>) health.getDetails().get("final-status");
        assertThat(client)
                .containsEntry("samples", (long) samples)
                .containsEntry("errors", 2L)
                .containsEntry("status", Status.UP.getCode());
    }

    private enum WireProtocol {
        HTTP11(HttpProtocol.HTTP11, "HTTP/1.1"),
        H2C(HttpProtocol.H2C, "HTTP/2.0");

        private final HttpProtocol nettyProtocol;
        private final String responseVersion;

        WireProtocol(HttpProtocol nettyProtocol, String responseVersion) {
            this.nettyProtocol = nettyProtocol;
            this.responseVersion = responseVersion;
        }
    }

    @LogHttpExchange(logger = StatusDiagnostics.class)
    @ReactiveHttpClient(name = "final-status")
    interface FinalStatusClient {
        @HEAD("/head")
        Mono<Void> headVoid();

        @HEAD("/head")
        Mono<String> headBody();

        @HEAD("/head")
        Mono<ResponseEntity<Void>> head();

        @HEAD("/head")
        Mono<ResponseEntity<String>> headEntityBody();

        @OPTIONS("/options")
        Mono<ResponseEntity<String>> options();

        @GET("/status/{status}/void")
        Mono<Void> statusVoid(@PathVar("status") int status);

        @GET("/status/{status}/body")
        Mono<String> statusBody(@PathVar("status") int status);

        @GET("/status/{status}/entity-void")
        Mono<ResponseEntity<Void>> statusVoidEntity(@PathVar("status") int status);

        @GET("/status/{status}/entity-string")
        Mono<ResponseEntity<String>> statusStringEntity(@PathVar("status") int status);

        @GET("/redirect")
        Mono<ResponseEntity<String>> visibleRedirect();

        @GET("/client-error")
        Mono<String> clientError();

        @GET("/server-error")
        Mono<String> serverError();
    }

    private static final class ClientFixture implements AutoCloseable {
        private final StaticApplicationContext context;
        private final ReactiveHttpClientFactoryBean<FinalStatusClient> factory;
        private final FinalStatusClient client;

        private ClientFixture(StaticApplicationContext context,
                              ReactiveHttpClientFactoryBean<FinalStatusClient> factory,
                              FinalStatusClient client) {
            this.context = context;
            this.factory = factory;
            this.client = client;
        }

        private static ClientFixture create(String baseUrl,
                                            WireProtocol protocol,
                                            StatusDiagnostics diagnostics,
                                            ErrorResponseMapper mapper) {
            StaticApplicationContext context = new StaticApplicationContext();
            ReactiveHttpClientProperties properties = new ReactiveHttpClientProperties();
            ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();
            config.setBaseUrl(baseUrl);
            config.setHttp2Enabled(protocol == WireProtocol.H2C);
            ReactiveHttpClientProperties.ConnectionPoolConfig pool =
                    new ReactiveHttpClientProperties.ConnectionPoolConfig();
            pool.setMaxConnections(1);
            config.setPool(pool);
            properties.getClients().put("final-status", config);

            context.getBeanFactory().registerSingleton("reactiveHttpClientProperties", properties);
            context.getBeanFactory().registerSingleton("starterWebClientBuilder", WebClient.builder());
            context.getBeanFactory().registerSingleton("reactiveHttpClientJsonCodec", TestJsonCodecs.jsonCodec());
            context.getBeanFactory().registerSingleton(
                    "defaultErrorDecoder", new DefaultErrorDecoder("final-status", List.of(mapper)));
            context.getBeanFactory().registerSingleton("statusDiagnostics", diagnostics);

            ReactiveHttpClientFactoryBean<FinalStatusClient> factory = new ReactiveHttpClientFactoryBean<>();
            factory.setType(FinalStatusClient.class);
            factory.setApplicationContext(context);
            return new ClientFixture(context, factory, factory.getObject());
        }

        @Override
        public void close() {
            factory.destroy();
            context.close();
        }
    }

    private static final class StatusServer implements AutoCloseable {
        private final List<RequestRecord> requests = new CopyOnWriteArrayList<>();
        private final DisposableServer server;

        private StatusServer(WireProtocol protocol) {
            server = HttpServer.create()
                    .protocol(protocol.nettyProtocol)
                    .port(0)
                    .handle((request, response) -> {
                        request.withConnection(connection -> requests.add(new RequestRecord(
                                request.method().name(),
                                normalizedPath(request.path()),
                                request.version().text(),
                                connection.channel().id().asLongText())));
                        String path = normalizedPath(request.path());
                        if ("/head".equals(path)) {
                            return response.status(200)
                                    .header(FINAL_HEADER, "200")
                                    .header(HttpHeaders.CONTENT_LENGTH, "9")
                                    .sendString(Mono.just("head-body"))
                                    .then();
                        }
                        if ("/options".equals(path)) {
                            return response.status(200)
                                    .header(FINAL_HEADER, "200")
                                    .header(HttpHeaders.ALLOW, "GET,HEAD,OPTIONS")
                                    .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_VALUE)
                                    .sendString(Mono.just("options"))
                                    .then();
                        }
                        if (path.startsWith("/status/")) {
                            int status = Integer.parseInt(path.split("/")[2]);
                            return response.status(status)
                                    .header(FINAL_HEADER, String.valueOf(status))
                                    .send()
                                    .then();
                        }
                        if ("/redirect".equals(path)) {
                            return response.status(302)
                                    .header(FINAL_HEADER, "302")
                                    .header(HttpHeaders.LOCATION, "/final")
                                    .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_VALUE)
                                    .sendString(Mono.just("visible-redirect"))
                                    .then();
                        }
                        if ("/client-error".equals(path)) {
                            return response.status(400)
                                    .header(FINAL_HEADER, "400")
                                    .sendString(Mono.just("client-error"))
                                    .then();
                        }
                        if ("/server-error".equals(path)) {
                            return response.status(503)
                                    .header(FINAL_HEADER, "503")
                                    .sendString(Mono.just("server-error"))
                                    .then();
                        }
                        return response.status(404).send();
                    })
                    .bindNow();
        }

        private String baseUrl() {
            return "http://127.0.0.1:" + server.port();
        }

        @Override
        public void close() {
            server.disposeNow(CALL_TIMEOUT);
        }
    }

    private static String normalizedPath(String path) {
        return path.startsWith("/") ? path : "/" + path;
    }

    private static final class StatusDiagnostics
            implements HttpClientObserver, ReactiveHttpClientLifecycleHook, HttpExchangeLogger {
        private final ReactiveHttpClientProperties.ObservabilityConfig observability =
                new ReactiveHttpClientProperties.ObservabilityConfig();
        private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
        private final MicrometerHttpClientObserver metrics =
                new MicrometerHttpClientObserver(registry, observability);
        private final List<HttpClientObserverEvent> observerEvents = new CopyOnWriteArrayList<>();
        private final List<ReactiveHttpClientLifecycleContext> successContexts = new CopyOnWriteArrayList<>();
        private final List<ReactiveHttpClientLifecycleContext> errorContexts = new CopyOnWriteArrayList<>();
        private final List<HttpExchangeLogContext> exchangeLogs = new CopyOnWriteArrayList<>();

        private StatusDiagnostics() {
            observability.getHealth().setMinSamples(1);
            observability.getHealth().setErrorRateThreshold(0.5);
        }

        @Override
        public void record(HttpClientObserverEvent event) {
            observerEvents.add(event);
            metrics.record(event);
        }

        @Override
        public void onSuccess(ReactiveHttpClientLifecycleContext context) {
            successContexts.add(context);
        }

        @Override
        public void onError(ReactiveHttpClientLifecycleContext context) {
            errorContexts.add(context);
        }

        @Override
        public void log(HttpExchangeLogContext context) {
            exchangeLogs.add(context);
        }

        private void assertTerminalFacts() {
            assertThat(observerEvents).hasSize(20);
            assertThat(successContexts).hasSize(18);
            assertThat(errorContexts).hasSize(2);
            assertThat(exchangeLogs).hasSize(20);

            assertThat(observerEvents).anySatisfy(event -> {
                assertThat(event.getUriPath()).isEqualTo("/status/{status}/entity-string");
                assertThat(event.getStatusCode()).isEqualTo(304);
                assertThat(event.getError()).isNull();
                assertThat(event.getErrorCategory()).isNull();
                assertThat(event.getFailureStage()).isNull();
            });
            assertThat(successContexts).anySatisfy(context -> {
                assertThat(context.pathTemplate()).isEqualTo("/status/{status}/entity-string");
                assertThat(context.statusCode()).isEqualTo(304);
                assertThat(context.error()).isNull();
                assertThat(context.failureStage()).isNull();
            });
            assertThat(exchangeLogs).anySatisfy(context -> {
                assertThat(context.pathTemplate()).isEqualTo("/status/{status}/entity-string");
                assertThat(context.responseStatus()).isEqualTo(304);
                assertThat(context.responseHeaders().entrySet()).anySatisfy(entry -> {
                    assertThat(entry.getKey()).isEqualToIgnoringCase(FINAL_HEADER);
                    assertThat(entry.getValue()).containsExactly("304");
                });
                assertThat(context.error()).isNull();
                assertThat(context.failureStage()).isNull();
            });
            assertThat(observerEvents).anySatisfy(event -> {
                assertThat(event.getStatusCode()).isEqualTo(400);
                assertThat(event.getErrorCategory()).isEqualTo(ErrorCategory.CLIENT_ERROR);
                assertThat(event.getFailureStage()).isNull();
            });
            assertThat(errorContexts).anySatisfy(context -> {
                assertThat(context.statusCode()).isEqualTo(503);
                assertThat(context.error()).isInstanceOf(RemoteServiceException.class);
                assertThat(context.failureStage()).isNull();
            });
        }
    }

    private record RequestRecord(String method, String path, String protocol, String channelId) {
    }
}
