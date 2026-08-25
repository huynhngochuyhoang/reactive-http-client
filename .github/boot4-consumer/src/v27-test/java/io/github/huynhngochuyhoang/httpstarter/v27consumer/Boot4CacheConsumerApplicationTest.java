package io.github.huynhngochuyhoang.httpstarter.v27consumer;

import io.github.huynhngochuyhoang.httpstarter.annotation.CacheDisabled;
import io.github.huynhngochuyhoang.httpstarter.annotation.CacheResponse;
import io.github.huynhngochuyhoang.httpstarter.annotation.GET;
import io.github.huynhngochuyhoang.httpstarter.annotation.PathVar;
import io.github.huynhngochuyhoang.httpstarter.annotation.ReactiveHttpClient;
import io.github.huynhngochuyhoang.httpstarter.enable.EnableReactiveHttpClients;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class Boot4CacheConsumerApplicationTest {

    @Test
    void assembledConsumerCoversCachePhasesAndExplicitResilienceSelection() throws Exception {
        ConcurrentHashMap<String, AtomicInteger> dispatches = new ConcurrentHashMap<>();
        Sinks.One<String> singleFlightBody = Sinks.one();
        DisposableServer server = HttpServer.create().port(0)
                .route(routes -> routes
                        .get("/method/{id}", (request, response) -> json(
                                response, "method-" + request.param("id") + "-"
                                        + increment(dispatches, "method-" + request.param("id"))))
                        .get("/uncached", (request, response) -> json(
                                response, "uncached-" + increment(dispatches, "uncached")))
                        .get("/wide/{id}", (request, response) -> json(
                                response, "wide-" + request.param("id") + "-"
                                        + increment(dispatches, "wide-" + request.param("id"))))
                        .get("/wide-disabled", (request, response) -> json(
                                response, "wide-disabled-" + increment(dispatches, "wide-disabled")))
                        .get("/single", (request, response) -> {
                            increment(dispatches, "single");
                            return response.header("Content-Type", "application/json")
                                    .sendString(singleFlightBody.asMono()).then();
                        })
                        .get("/refresh", (request, response) -> json(
                                response, "refresh-" + increment(dispatches, "refresh")))
                        .get("/retry", (request, response) -> {
                            int attempt = increment(dispatches, "retry");
                            if (attempt == 1) {
                                return response.status(503).send();
                            }
                            return json(response, "retry-" + attempt);
                        })
                        .get("/enabled-only", (request, response) -> json(
                                response, "enabled-" + increment(dispatches, "enabled-only"))))
                .bindNow(Duration.ofSeconds(5));

        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(CacheConsumerApplication.class)
                .web(WebApplicationType.NONE)
                .properties(properties(server.port()))
                .run()) {
            MethodCacheClient method = context.getBean(MethodCacheClient.class);
            assertThat(method.get("one").block()).isEqualTo(new ValueResponse("method-one-1"));
            assertThat(method.get("one").block()).isEqualTo(new ValueResponse("method-one-1"));
            assertThat(count(dispatches, "method-one")).isEqualTo(1);
            assertThat(method.uncached().block()).isEqualTo(new ValueResponse("uncached-1"));
            assertThat(method.uncached().block()).isEqualTo(new ValueResponse("uncached-2"));

            await().pollDelay(Duration.ofMillis(120)).atMost(Duration.ofSeconds(2)).untilAsserted(() ->
                    assertThat(method.get("one").block())
                            .isEqualTo(new ValueResponse("method-one-2")));
            assertThat(method.get("two").block()).isEqualTo(new ValueResponse("method-two-1"));
            assertThat(method.get("three").block()).isEqualTo(new ValueResponse("method-three-1"));
            assertThat(method.get("two").block()).isEqualTo(new ValueResponse("method-two-2"));

            ClientWideCacheClient wide = context.getBean(ClientWideCacheClient.class);
            assertThat(wide.get("one").block()).isEqualTo(new ValueResponse("wide-one-1"));
            assertThat(wide.get("one").block()).isEqualTo(new ValueResponse("wide-one-1"));
            assertThat(wide.excluded().block()).isEqualTo(new ValueResponse("wide-disabled-1"));
            assertThat(wide.excluded().block()).isEqualTo(new ValueResponse("wide-disabled-2"));

            PhasedCacheClient phased = context.getBean(PhasedCacheClient.class);
            CompletableFuture<ValueResponse> leader = phased.single().toFuture();
            CompletableFuture<ValueResponse> waiter = phased.single().toFuture();
            await().atMost(Duration.ofSeconds(2)).untilAsserted(() ->
                    assertThat(count(dispatches, "single")).isEqualTo(1));
            singleFlightBody.tryEmitValue("{\"value\":\"single-1\"}").orThrow();
            assertThat(leader.get(1, TimeUnit.SECONDS)).isEqualTo(new ValueResponse("single-1"));
            assertThat(waiter.get(1, TimeUnit.SECONDS)).isEqualTo(new ValueResponse("single-1"));

            assertThat(phased.refresh().block()).isEqualTo(new ValueResponse("refresh-1"));
            await().pollDelay(Duration.ofMillis(550)).atMost(Duration.ofSeconds(2)).untilAsserted(() ->
                    assertThat(phased.refresh().block()).isEqualTo(new ValueResponse("refresh-1")));
            await().atMost(Duration.ofSeconds(2)).untilAsserted(() ->
                    assertThat(count(dispatches, "refresh")).isEqualTo(2));
            await().atMost(Duration.ofSeconds(2)).untilAsserted(() ->
                    assertThat(phased.refresh().block()).isEqualTo(new ValueResponse("refresh-2")));

            assertThat(context.getBean(RetryOnlyClient.class).get().block())
                    .isEqualTo(new ValueResponse("retry-2"));
            assertThat(count(dispatches, "retry")).isEqualTo(2);
            assertThat(context.getBean(EnabledOnlyClient.class).get().block())
                    .isEqualTo(new ValueResponse("enabled-1"));
            assertThat(count(dispatches, "enabled-only")).isEqualTo(1);
        }
        finally {
            server.disposeNow(Duration.ofSeconds(5));
        }
    }

    private static String[] properties(int port) {
        String base = "http://127.0.0.1:" + port;
        return new String[] {
                "spring.main.banner-mode=off",
                "reactive.http.clients.method-cache.base-url=" + base,
                "reactive.http.clients.method-cache.cache.policies.method.ttl-ms=100",
                "reactive.http.clients.method-cache.cache.policies.method.maximum-size=1",
                "reactive.http.clients.method-cache.cache.policies.method.shared-response=true",
                "reactive.http.clients.method-cache.cache.customizations.exchangeStrategiesCustomizer=SAFE",
                "reactive.http.clients.method-cache.cache.customizations.webClientHttpConnectorCustomizer=SAFE",
                "reactive.http.clients.method-cache.cache.customizations.observationWebClientCustomizer=SAFE",
                "reactive.http.clients.method-cache.cache.customizations.webClientBuilder=SAFE",
                "reactive.http.clients.wide-cache.base-url=" + base,
                "reactive.http.clients.wide-cache.cache.policy=wide",
                "reactive.http.clients.wide-cache.cache.policies.wide.ttl-ms=5000",
                "reactive.http.clients.wide-cache.cache.policies.wide.maximum-size=10",
                "reactive.http.clients.wide-cache.cache.policies.wide.shared-response=true",
                "reactive.http.clients.wide-cache.cache.customizations.exchangeStrategiesCustomizer=SAFE",
                "reactive.http.clients.wide-cache.cache.customizations.webClientHttpConnectorCustomizer=SAFE",
                "reactive.http.clients.wide-cache.cache.customizations.observationWebClientCustomizer=SAFE",
                "reactive.http.clients.wide-cache.cache.customizations.webClientBuilder=SAFE",
                "reactive.http.clients.phased-cache.base-url=" + base,
                "reactive.http.clients.phased-cache.cache.policies.single.ttl-ms=5000",
                "reactive.http.clients.phased-cache.cache.policies.single.maximum-size=10",
                "reactive.http.clients.phased-cache.cache.policies.single.shared-response=true",
                "reactive.http.clients.phased-cache.cache.policies.single.single-flight=true",
                "reactive.http.clients.phased-cache.cache.policies.refresh.ttl-ms=5000",
                "reactive.http.clients.phased-cache.cache.policies.refresh.maximum-size=10",
                "reactive.http.clients.phased-cache.cache.policies.refresh.shared-response=true",
                "reactive.http.clients.phased-cache.cache.policies.refresh.refresh-after-ms=500",
                "reactive.http.clients.phased-cache.cache.policies.refresh.refresh-timeout-ms=1000",
                "reactive.http.clients.phased-cache.cache.customizations.exchangeStrategiesCustomizer=SAFE",
                "reactive.http.clients.phased-cache.cache.customizations.webClientHttpConnectorCustomizer=SAFE",
                "reactive.http.clients.phased-cache.cache.customizations.observationWebClientCustomizer=SAFE",
                "reactive.http.clients.phased-cache.cache.customizations.webClientBuilder=SAFE",
                "reactive.http.clients.retry-only.base-url=" + base,
                "reactive.http.clients.retry-only.resilience.enabled=true",
                "reactive.http.clients.retry-only.resilience.retry=consumer-retry",
                "reactive.http.clients.retry-only.resilience.retry-methods[0]=GET",
                "reactive.http.clients.enabled-only.base-url=" + base,
                "reactive.http.clients.enabled-only.resilience.enabled=true"
        };
    }

    private static Mono<Void> json(reactor.netty.http.server.HttpServerResponse response, String value) {
        return response.header("Content-Type", "application/json")
                .sendString(Mono.just("{\"value\":\"" + value + "\"}"))
                .then();
    }

    private static int increment(ConcurrentHashMap<String, AtomicInteger> counts, String key) {
        return counts.computeIfAbsent(key, ignored -> new AtomicInteger()).incrementAndGet();
    }

    private static int count(ConcurrentHashMap<String, AtomicInteger> counts, String key) {
        AtomicInteger value = counts.get(key);
        return value != null ? value.get() : 0;
    }

    @ReactiveHttpClient(name = "method-cache")
    interface MethodCacheClient {
        @GET("/method/{id}")
        @CacheResponse("method")
        Mono<ValueResponse> get(@PathVar("id") String id);

        @GET("/uncached")
        Mono<ValueResponse> uncached();
    }

    @ReactiveHttpClient(name = "wide-cache")
    interface ClientWideCacheClient {
        @GET("/wide/{id}")
        Mono<ValueResponse> get(@PathVar("id") String id);

        @GET("/wide-disabled")
        @CacheDisabled
        Mono<ValueResponse> excluded();
    }

    @ReactiveHttpClient(name = "phased-cache")
    interface PhasedCacheClient {
        @GET("/single")
        @CacheResponse("single")
        Mono<ValueResponse> single();

        @GET("/refresh")
        @CacheResponse("refresh")
        Mono<ValueResponse> refresh();
    }

    @ReactiveHttpClient(name = "retry-only")
    interface RetryOnlyClient {
        @GET("/retry")
        Mono<ValueResponse> get();
    }

    @ReactiveHttpClient(name = "enabled-only")
    interface EnabledOnlyClient {
        @GET("/enabled-only")
        Mono<ValueResponse> get();
    }

    record ValueResponse(String value) {
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EnableReactiveHttpClients(basePackageClasses = MethodCacheClient.class)
    static class CacheConsumerApplication {
        @Bean
        RetryRegistry retryRegistry() {
            return RetryRegistry.of(RetryConfig.custom()
                    .maxAttempts(2)
                    .waitDuration(Duration.ZERO)
                    .build());
        }
    }
}
