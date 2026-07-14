package io.github.huynhngochuyhoang.httpstarter.boot4consumer;

import io.github.huynhngochuyhoang.httpstarter.annotation.ApiRef;
import io.github.huynhngochuyhoang.httpstarter.annotation.GET;
import io.github.huynhngochuyhoang.httpstarter.annotation.HeaderParam;
import io.github.huynhngochuyhoang.httpstarter.annotation.PathVar;
import io.github.huynhngochuyhoang.httpstarter.annotation.ReactiveHttpClient;
import io.github.huynhngochuyhoang.httpstarter.annotation.TimeoutMs;
import io.github.huynhngochuyhoang.httpstarter.core.Jackson3ReactiveHttpClientJsonCodec;
import io.github.huynhngochuyhoang.httpstarter.core.ProblemDetailErrorResponseMapper;
import io.github.huynhngochuyhoang.httpstarter.core.ReactiveHttpClientJsonCodec;
import io.github.huynhngochuyhoang.httpstarter.core.ReactiveHttpClientLifecycleContext;
import io.github.huynhngochuyhoang.httpstarter.core.ReactiveHttpClientLifecycleHook;
import io.github.huynhngochuyhoang.httpstarter.enable.EnableReactiveHttpClients;
import io.github.huynhngochuyhoang.httpstarter.exception.ErrorCategory;
import io.github.huynhngochuyhoang.httpstarter.exception.ProblemDetailHttpClientException;
import io.github.huynhngochuyhoang.httpstarter.observability.Boot4HttpClientHealthIndicator;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientObserver;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientObserverEvent;
import io.github.huynhngochuyhoang.httpstarter.observability.ReactiveHttpClientDiagnosticsEndpoint;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.opentelemetry.api.OpenTelemetry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Boot4ConsumerApplicationTest {

    private static final List<HttpClientObserverEvent> OBSERVED = new CopyOnWriteArrayList<>();
    private static final List<ReactiveHttpClientLifecycleContext> SUCCEEDED = new CopyOnWriteArrayList<>();
    private static final List<ReactiveHttpClientLifecycleContext> FAILED = new CopyOnWriteArrayList<>();

    @Test
    void jackson3SafeApiCompilesWithoutDirectJackson2Dependency() {
        var jsonCodec = new Jackson3ReactiveHttpClientJsonCodec(new ObjectMapper());

        assertThat(new ProblemDetailErrorResponseMapper(jsonCodec)).isNotNull();
    }

    @Test
    void packagedConsumerCoversProtocolAndOptionalIntegrationContracts() {
        OBSERVED.clear();
        SUCCEEDED.clear();
        FAILED.clear();
        AtomicReference<List<String>> repeatedHeaders = new AtomicReference<>();
        DisposableServer server = HttpServer.create().port(0)
                .route(routes -> routes
                        .get("/orders/42", (request, response) -> response
                                .header("Content-Type", "application/json")
                                .sendString(Mono.just("{\"code\":\"inherited\"}")).then())
                        .get("/configured/42", (request, response) -> response
                                .header("Content-Type", "application/json")
                                .sendString(Mono.just("{\"code\":\"api-ref\"}")).then())
                        .get("/headers", (request, response) -> {
                            repeatedHeaders.set(request.requestHeaders().getAll("X-Tag"));
                            return response.status(204).send();
                        })
                        .get("/redirect", (request, response) -> response
                                .status(302).header("Location", "/entity").send())
                        .get("/entity", (request, response) -> response
                                .status(202)
                                .header("Content-Type", "application/json")
                                .header("X-Result", "accepted")
                                .sendString(Mono.just("{\"code\":\"entity\"}")).then())
                        .get("/bodiless", (request, response) -> response
                                .header("Content-Type", "text/plain")
                                .sendString(Mono.just("unexpected-body")).then())
                        .get("/stream", (request, response) -> response
                                .header("Content-Type", "application/octet-stream")
                                .sendString(Mono.just("stream-body")).then())
                        .get("/slow", (request, response) -> response
                                .sendString(Mono.delay(Duration.ofMillis(250)).map(ignored -> "late")).then())
                        .get("/problem", (request, response) -> response
                                .status(400)
                                .header("Content-Type", "application/problem+json")
                                .sendString(Mono.just("{\"type\":\"https://example.test/problems/order\","
                                        + "\"title\":\"Invalid order\",\"status\":400,\"detail\":\"bad id\"}"))
                                .then()))
                .bindNow(Duration.ofSeconds(5));
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(ConsumerApplication.class)
                .web(WebApplicationType.NONE)
                .properties(
                        "spring.main.banner-mode=off",
                        "reactive.http.clients.orders.base-url=http://127.0.0.1:" + server.port(),
                        "reactive.http.clients.orders.apis.configured.method=GET",
                        "reactive.http.clients.orders.apis.configured.path=/configured/{id}",
                        "reactive.http.clients.orders.follow-redirects=true",
                        "reactive.http.clients.orders.resilience.enabled=true",
                        "reactive.http.clients.orders.resilience.strict-unsafe-retry-validation=true",
                        "reactive.http.observability.diagnostics-endpoint.enabled=true")
                .run()) {
            OrdersClient client = context.getBean(OrdersClient.class);
            assertThat(client.get("42").block(Duration.ofSeconds(5)))
                    .isEqualTo(new OrderResponse("inherited"));
            assertThat(client.configured("42").block(Duration.ofSeconds(5)))
                    .isEqualTo(new OrderResponse("api-ref"));

            client.repeatedHeaders(List.of("first", "second")).block(Duration.ofSeconds(5));
            assertThat(repeatedHeaders.get()).containsExactly("first", "second");

            ResponseEntity<OrderResponse> redirected = client.redirect().block(Duration.ofSeconds(5));
            assertThat(redirected).isNotNull();
            assertThat(redirected.getStatusCode().value()).isEqualTo(202);
            assertThat(redirected.getHeaders().getFirst("X-Result")).isEqualTo("accepted");
            assertThat(redirected.getBody()).isEqualTo(new OrderResponse("entity"));

            client.bodiless().block(Duration.ofSeconds(5));

            ResponseEntity<Flux<DataBuffer>> streaming = client.streaming().block(Duration.ofSeconds(5));
            assertThat(streaming).isNotNull();
            String streamedBody = DataBufferUtils.join(streaming.getBody())
                    .map(buffer -> {
                        byte[] bytes = new byte[buffer.readableByteCount()];
                        buffer.read(bytes);
                        DataBufferUtils.release(buffer);
                        return new String(bytes, StandardCharsets.UTF_8);
                    })
                    .block(Duration.ofSeconds(5));
            assertThat(streamedBody).isEqualTo("stream-body");

            assertThatThrownBy(() -> client.problem().block(Duration.ofSeconds(5)))
                    .isInstanceOf(ProblemDetailHttpClientException.class)
                    .satisfies(error -> assertThat(((ProblemDetailHttpClientException) error)
                            .getProblemDetail().getTitle()).isEqualTo("Invalid order"));
            assertThatThrownBy(() -> client.timeout().block(Duration.ofSeconds(5)))
                    .isInstanceOf(RuntimeException.class);

            assertThat(context.containsBean("reactiveHttpClientHealthIndicator")).isTrue();
            assertThat(context.containsBean("openTelemetryHttpClientObserver")).isTrue();
            assertThat(context.getBean(ReactiveHttpClientDiagnosticsEndpoint.class).diagnostics())
                    .containsEntry("clientCount", 1);
            assertThat(context.getBean(MeterRegistry.class)
                    .find("reactive.http.client.requests").timer()).isNotNull();
            assertThat(context.getBean(Boot4HttpClientHealthIndicator.class).health().getDetails())
                    .containsKey("orders");
            assertThat(SUCCEEDED).anySatisfy(event -> {
                assertThat(event.clientName()).isEqualTo("orders");
                assertThat(event.requestUrl()).hasToString(
                        "http://127.0.0.1:" + server.port() + "/redirect");
                assertThat(event.statusCode()).isEqualTo(202);
            });
            assertThat(FAILED).anySatisfy(event -> {
                assertThat(event.pathTemplate()).isEqualTo("/problem");
                assertThat(event.statusCode()).isEqualTo(400);
            });
            assertThat(OBSERVED).anySatisfy(event -> {
                assertThat(event.getUriPath()).isEqualTo("/slow");
                assertThat(event.getErrorCategory()).isEqualTo(ErrorCategory.TIMEOUT);
            });
            assertThat(OBSERVED).anySatisfy(event -> {
                assertThat(event.getUriPath()).isEqualTo("/stream");
                assertThat(event.getStatusCode()).isEqualTo(200);
            });
        }
        finally {
            server.disposeNow(Duration.ofSeconds(5));
        }
    }

    interface SharedOrders<T> {
        @GET("/orders/{id}")
        Mono<T> get(@PathVar("id") String id);
    }

    @ReactiveHttpClient(name = "orders")
    interface OrdersClient extends SharedOrders<OrderResponse> {
        @ApiRef("configured")
        Mono<OrderResponse> configured(@PathVar("id") String id);

        @GET("/headers")
        Mono<Void> repeatedHeaders(@HeaderParam("X-Tag") List<String> values);

        @GET("/redirect")
        Mono<ResponseEntity<OrderResponse>> redirect();

        @GET("/bodiless")
        Mono<Void> bodiless();

        @GET("/stream")
        Mono<ResponseEntity<Flux<DataBuffer>>> streaming();

        @GET("/problem")
        Mono<OrderResponse> problem();

        @GET("/slow")
        @TimeoutMs(50)
        Mono<String> timeout();
    }

    record OrderResponse(String code) {
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EnableReactiveHttpClients(basePackageClasses = OrdersClient.class)
    static class ConsumerApplication {
        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }

        @Bean
        OpenTelemetry openTelemetry() {
            return OpenTelemetry.noop();
        }

        @Bean
        ProblemDetailErrorResponseMapper problemDetailErrorResponseMapper(
                ReactiveHttpClientJsonCodec jsonCodec) {
            return new ProblemDetailErrorResponseMapper(jsonCodec);
        }

        @Bean
        HttpClientObserver consumerObserver() {
            return OBSERVED::add;
        }

        @Bean
        ReactiveHttpClientLifecycleHook consumerLifecycleHook() {
            return new ReactiveHttpClientLifecycleHook() {
                @Override
                public void onSuccess(ReactiveHttpClientLifecycleContext context) {
                    SUCCEEDED.add(context);
                }

                @Override
                public void onError(ReactiveHttpClientLifecycleContext context) {
                    FAILED.add(context);
                }
            };
        }

        @Bean
        RetryRegistry retryRegistry() {
            RetryRegistry registry = RetryRegistry.of(
                    RetryConfig.custom().maxAttempts(2).build());
            registry.retry("default");
            return registry;
        }
    }
}
