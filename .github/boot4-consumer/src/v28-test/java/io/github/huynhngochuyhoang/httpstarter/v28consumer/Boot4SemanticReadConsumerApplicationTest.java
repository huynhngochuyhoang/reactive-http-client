package io.github.huynhngochuyhoang.httpstarter.v28consumer;

import io.github.huynhngochuyhoang.httpstarter.annotation.Body;
import io.github.huynhngochuyhoang.httpstarter.annotation.CacheKey;
import io.github.huynhngochuyhoang.httpstarter.annotation.CacheResponse;
import io.github.huynhngochuyhoang.httpstarter.annotation.GET;
import io.github.huynhngochuyhoang.httpstarter.annotation.HeaderParam;
import io.github.huynhngochuyhoang.httpstarter.annotation.POST;
import io.github.huynhngochuyhoang.httpstarter.annotation.PathVar;
import io.github.huynhngochuyhoang.httpstarter.annotation.ReactiveHttpClient;
import io.github.huynhngochuyhoang.httpstarter.auth.AuthContext;
import io.github.huynhngochuyhoang.httpstarter.auth.AuthProvider;
import io.github.huynhngochuyhoang.httpstarter.enable.EnableReactiveHttpClients;
import io.github.huynhngochuyhoang.httpstarter.v28invalid.InvalidSemanticReadConsumerApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;
import reactor.netty.http.server.HttpServerResponse;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

class Boot4SemanticReadConsumerApplicationTest {

    @Test
    void assembledCurrentConsumerCoversSemanticReadCachePhasesAndIsolation() throws Exception {
        ConcurrentHashMap<String, AtomicInteger> dispatches = new ConcurrentHashMap<>();
        Sinks.One<String> singleFlightResponse = Sinks.one();
        DisposableServer server = HttpServer.create().port(0)
                .route(routes -> routes
                        .get("/catalog/{id}", (request, response) -> json(
                                response, "catalog-" + request.param("id") + "-"
                                        + increment(dispatches, "catalog")))
                        .post("/search", (request, response) -> request.receive().aggregate().asString()
                                .flatMap(body -> json(response, "search-"
                                        + increment(dispatches, "search"))))
                        .post("/write", (request, response) -> request.receive().aggregate().asString()
                                .flatMap(body -> json(response, "write-"
                                        + increment(dispatches, "write"))))
                        .post("/single", (request, response) -> request.receive().aggregate().asString()
                                .flatMap(body -> {
                                    increment(dispatches, "single");
                                    return response.header(HttpHeaders.CONTENT_TYPE, "application/json")
                                            .sendString(singleFlightResponse.asMono()).then();
                                }))
                        .post("/refresh", (request, response) -> request.receive().aggregate().asString()
                                .flatMap(body -> json(response, "refresh-"
                                        + increment(dispatches, "refresh"))))
                        .post("/partition", (request, response) -> request.receive().aggregate().asString()
                                .flatMap(body -> json(response,
                                        request.requestHeaders().get(HttpHeaders.AUTHORIZATION) + "-"
                                                + increment(dispatches, "partition")))))
                .bindNow(Duration.ofSeconds(5));

        try (ConfigurableApplicationContext context =
                     new SpringApplicationBuilder(SemanticReadConsumerApplication.class)
                             .web(WebApplicationType.NONE)
                             .properties(properties(server.port()))
                             .run()) {
            SemanticReadConsumerClient client = context.getBean(SemanticReadConsumerClient.class);
            QueryBody alpha = new QueryBody("alpha");
            QueryBody beta = new QueryBody("beta");

            assertThat(client.catalog("one").block()).isEqualTo(new ValueResponse("catalog-one-1"));
            assertThat(client.catalog("one").block()).isEqualTo(new ValueResponse("catalog-one-1"));
            assertThat(count(dispatches, "catalog")).isEqualTo(1);

            assertThat(client.search(alpha).block()).isEqualTo(new ValueResponse("search-1"));
            assertThat(client.search(alpha).block()).isEqualTo(new ValueResponse("search-1"));
            assertThat(client.search(beta).block()).isEqualTo(new ValueResponse("search-2"));
            assertThat(count(dispatches, "search")).isEqualTo(2);

            assertThat(client.write(alpha).block()).isEqualTo(new ValueResponse("write-1"));
            assertThat(client.write(alpha).block()).isEqualTo(new ValueResponse("write-2"));

            CompletableFuture<ValueResponse> leader = client.single(alpha).toFuture();
            CompletableFuture<ValueResponse> waiter = client.single(alpha).toFuture();
            await().atMost(Duration.ofSeconds(2)).untilAsserted(() ->
                    assertThat(count(dispatches, "single")).isEqualTo(1));
            singleFlightResponse.tryEmitValue("{\"value\":\"single-1\"}").orThrow();
            assertThat(leader.get(1, TimeUnit.SECONDS)).isEqualTo(new ValueResponse("single-1"));
            assertThat(waiter.get(1, TimeUnit.SECONDS)).isEqualTo(new ValueResponse("single-1"));

            assertThat(client.refresh(alpha).block()).isEqualTo(new ValueResponse("refresh-1"));
            await().pollDelay(Duration.ofMillis(120)).atMost(Duration.ofSeconds(2)).untilAsserted(() ->
                    assertThat(client.refresh(alpha).block()).isEqualTo(new ValueResponse("refresh-1")));
            await().atMost(Duration.ofSeconds(2)).untilAsserted(() ->
                    assertThat(count(dispatches, "refresh")).isEqualTo(2));
            await().atMost(Duration.ofSeconds(2)).untilAsserted(() ->
                    assertThat(client.refresh(alpha).block()).isEqualTo(new ValueResponse("refresh-2")));

            AuthSemanticReadConsumerClient auth =
                    context.getBean(AuthSemanticReadConsumerClient.class);
            assertThat(auth.partition("principal-a", alpha).block())
                    .isEqualTo(new ValueResponse("Bearer principal-a-1"));
            assertThat(auth.partition("principal-a", alpha).block())
                    .isEqualTo(new ValueResponse("Bearer principal-a-1"));
            assertThat(auth.partition("principal-b", alpha).block())
                    .isEqualTo(new ValueResponse("Bearer principal-b-2"));
            assertThat(count(dispatches, "partition")).isEqualTo(2);
        }
        finally {
            server.disposeNow(Duration.ofSeconds(5));
        }
    }

    @Test
    void assembledCurrentConsumerRejectsUnacknowledgedPostAtStartup() {
        assertThatThrownBy(() -> {
            try (ConfigurableApplicationContext context =
                         new SpringApplicationBuilder(InvalidSemanticReadConsumerApplication.class)
                                 .web(WebApplicationType.NONE)
                                 .properties(
                                         "spring.main.banner-mode=off",
                                         "reactive.http.clients.invalid-semantic.base-url=http://127.0.0.1:1",
                                         "reactive.http.clients.invalid-semantic.cache.policies.selected.ttl-ms=1000",
                                         "reactive.http.clients.invalid-semantic.cache.policies.selected.maximum-size=10")
                                 .run()) {
                context.getBean("io.github.huynhngochuyhoang.httpstarter.v28invalid.InvalidSemanticReadClient");
            }
        })
                .hasStackTraceContaining("semanticRead = true")
                .hasStackTraceContaining("resolvedHttpMethod=POST");
    }

    private static String[] properties(int port) {
        String base = "http://127.0.0.1:" + port;
        return new String[] {
                "spring.main.banner-mode=off",
                "reactive.http.clients.semantic-consumer.base-url=" + base,
                "reactive.http.clients.semantic-consumer.cache.policies.get.ttl-ms=5000",
                "reactive.http.clients.semantic-consumer.cache.policies.get.maximum-size=10",
                "reactive.http.clients.semantic-consumer.cache.policies.get.shared-response=true",
                "reactive.http.clients.semantic-consumer.cache.policies.post.ttl-ms=5000",
                "reactive.http.clients.semantic-consumer.cache.policies.post.maximum-size=10",
                "reactive.http.clients.semantic-consumer.cache.policies.post.shared-response=true",
                "reactive.http.clients.semantic-consumer.cache.policies.post.vary-by-parameters[0]=body",
                "reactive.http.clients.semantic-consumer.cache.policies.single.ttl-ms=5000",
                "reactive.http.clients.semantic-consumer.cache.policies.single.maximum-size=10",
                "reactive.http.clients.semantic-consumer.cache.policies.single.shared-response=true",
                "reactive.http.clients.semantic-consumer.cache.policies.single.single-flight=true",
                "reactive.http.clients.semantic-consumer.cache.policies.single.vary-by-parameters[0]=body",
                "reactive.http.clients.semantic-consumer.cache.policies.refresh.ttl-ms=5000",
                "reactive.http.clients.semantic-consumer.cache.policies.refresh.maximum-size=10",
                "reactive.http.clients.semantic-consumer.cache.policies.refresh.shared-response=true",
                "reactive.http.clients.semantic-consumer.cache.policies.refresh.refresh-after-ms=100",
                "reactive.http.clients.semantic-consumer.cache.policies.refresh.refresh-timeout-ms=1000",
                "reactive.http.clients.semantic-consumer.cache.policies.refresh.vary-by-parameters[0]=body",
                "reactive.http.clients.auth-semantic-consumer.base-url=" + base,
                "reactive.http.clients.auth-semantic-consumer.auth-provider=semanticAuthProvider",
                "reactive.http.clients.auth-semantic-consumer.cache.policies.partition.ttl-ms=5000",
                "reactive.http.clients.auth-semantic-consumer.cache.policies.partition.maximum-size=10",
                "reactive.http.clients.auth-semantic-consumer.cache.policies.partition.vary-by-parameters[0]=body",
                "reactive.http.clients.auth-semantic-consumer.cache.policies.partition.vary-by-headers[0]=Idempotency-Key",
                "reactive.http.clients.auth-semantic-consumer.cache.policies.partition.vary-by-headers[1]=X-Principal",
                "reactive.http.clients.semantic-consumer.cache.customizations.exchangeStrategiesCustomizer=SAFE",
                "reactive.http.clients.semantic-consumer.cache.customizations.webClientHttpConnectorCustomizer=SAFE",
                "reactive.http.clients.semantic-consumer.cache.customizations.observationWebClientCustomizer=SAFE",
                "reactive.http.clients.semantic-consumer.cache.customizations.webClientBuilder=SAFE",
                "reactive.http.clients.auth-semantic-consumer.cache.customizations.exchangeStrategiesCustomizer=SAFE",
                "reactive.http.clients.auth-semantic-consumer.cache.customizations.webClientHttpConnectorCustomizer=SAFE",
                "reactive.http.clients.auth-semantic-consumer.cache.customizations.observationWebClientCustomizer=SAFE",
                "reactive.http.clients.auth-semantic-consumer.cache.customizations.webClientBuilder=SAFE"
        };
    }

    private static int increment(ConcurrentHashMap<String, AtomicInteger> values, String name) {
        return values.computeIfAbsent(name, ignored -> new AtomicInteger()).incrementAndGet();
    }

    private static int count(ConcurrentHashMap<String, AtomicInteger> values, String name) {
        AtomicInteger value = values.get(name);
        return value != null ? value.get() : 0;
    }

    private static Mono<Void> json(HttpServerResponse response, String value) {
        return response.header(HttpHeaders.CONTENT_TYPE, "application/json")
                .sendString(Mono.just("{\"value\":\"" + value + "\"}"))
                .then();
    }

    @ReactiveHttpClient(name = "semantic-consumer")
    interface SemanticReadConsumerClient {
        @GET("/catalog/{id}")
        @CacheResponse("get")
        Mono<ValueResponse> catalog(@PathVar("id") String id);

        @POST("/search")
        @CacheResponse(value = "post", semanticRead = true)
        Mono<ValueResponse> search(@Body @CacheKey("body") QueryBody body);

        @POST("/write")
        Mono<ValueResponse> write(@Body QueryBody body);

        @POST("/single")
        @CacheResponse(value = "single", semanticRead = true)
        Mono<ValueResponse> single(@Body @CacheKey("body") QueryBody body);

        @POST("/refresh")
        @CacheResponse(value = "refresh", semanticRead = true)
        Mono<ValueResponse> refresh(@Body @CacheKey("body") QueryBody body);
    }

    @ReactiveHttpClient(name = "auth-semantic-consumer")
    interface AuthSemanticReadConsumerClient {
        @POST("/partition")
        @CacheResponse(value = "partition", semanticRead = true)
        Mono<ValueResponse> partition(
                @HeaderParam("X-Principal") String principal,
                @Body @CacheKey("body") QueryBody body);
    }

    record QueryBody(String term) {
    }

    record ValueResponse(String value) {
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EnableReactiveHttpClients(basePackageClasses = SemanticReadConsumerClient.class)
    static class SemanticReadConsumerApplication {
        @Bean
        AuthProvider semanticAuthProvider() {
            return request -> Mono.just(AuthContext.builder()
                    .header(HttpHeaders.AUTHORIZATION,
                            "Bearer " + request.request().headers().getFirst("X-Principal"))
                    .build());
        }
    }
}
