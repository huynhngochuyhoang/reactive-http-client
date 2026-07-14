package io.github.huynhngochuyhoang.httpstarter.boot4consumer;

import io.github.huynhngochuyhoang.httpstarter.annotation.ApiRef;
import io.github.huynhngochuyhoang.httpstarter.annotation.GET;
import io.github.huynhngochuyhoang.httpstarter.annotation.PathVar;
import io.github.huynhngochuyhoang.httpstarter.annotation.ReactiveHttpClient;
import io.github.huynhngochuyhoang.httpstarter.enable.EnableReactiveHttpClients;
import io.github.huynhngochuyhoang.httpstarter.core.Jackson3ReactiveHttpClientJsonCodec;
import io.github.huynhngochuyhoang.httpstarter.core.ProblemDetailErrorResponseMapper;
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
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import java.time.Duration;

import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class Boot4ConsumerApplicationTest {
    @Test
    void jackson3SafeApiCompilesWithoutDirectJackson2Dependency() {
        var jsonCodec = new Jackson3ReactiveHttpClientJsonCodec(new ObjectMapper());

        assertThat(new ProblemDetailErrorResponseMapper(jsonCodec)).isNotNull();
    }

    @Test
    void packagedConsumerRunsInheritedAndApiRefEndpointsWithOptionalIntegrations() {
        DisposableServer server = HttpServer.create().port(0)
                .route(routes -> routes
                        .get("/orders/42", (request, response) -> response
                                .header("Content-Type", "application/json")
                                .sendString(Mono.just("{\"code\":\"inherited\"}")).then())
                        .get("/configured/42", (request, response) -> response
                                .header("Content-Type", "application/json")
                                .sendString(Mono.just("{\"code\":\"api-ref\"}")).then()))
                .bindNow(Duration.ofSeconds(5));
        try (ConfigurableApplicationContext context =
                     new SpringApplicationBuilder(ConsumerApplication.class)
                             .web(WebApplicationType.NONE)
                             .properties(
                                     "spring.main.banner-mode=off",
                                     "reactive.http.clients.orders.base-url=http://127.0.0.1:" + server.port(),
                                     "reactive.http.clients.orders.apis.configured.method=GET",
                                     "reactive.http.clients.orders.apis.configured.path=/configured/{id}",
                                     "reactive.http.clients.orders.resilience.enabled=true",
                                     "reactive.http.clients.orders.resilience.strict-unsafe-retry-validation=true",
                                     "reactive.http.observability.diagnostics-endpoint.enabled=true")
                             .run()) {
            OrdersClient client = context.getBean(OrdersClient.class);
            assertThat(client.get("42").block(Duration.ofSeconds(5)))
                    .isEqualTo(new OrderResponse("inherited"));
            assertThat(client.configured("42").block(Duration.ofSeconds(5)))
                    .isEqualTo(new OrderResponse("api-ref"));
            assertThat(context.containsBean("reactiveHttpClientHealthIndicator")).isTrue();
            assertThat(context.containsBean("openTelemetryHttpClientObserver")).isTrue();
            assertThat(context.getBean(ReactiveHttpClientDiagnosticsEndpoint.class).diagnostics())
                    .containsEntry("clientCount", 1);
            assertThat(context.getBean(MeterRegistry.class)
                    .find("reactive.http.client.requests").timer()).isNotNull();
        } finally {
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
        RetryRegistry retryRegistry() {
            RetryRegistry registry = RetryRegistry.of(
                    RetryConfig.custom().maxAttempts(2).build());
            registry.retry("default");
            return registry;
        }
    }
}
