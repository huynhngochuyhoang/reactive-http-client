package io.github.huynhngochuyhoang.httpstarter.v29consumer;

import io.github.huynhngochuyhoang.httpstarter.annotation.CacheResponse;
import io.github.huynhngochuyhoang.httpstarter.annotation.GET;
import io.github.huynhngochuyhoang.httpstarter.annotation.PathVar;
import io.github.huynhngochuyhoang.httpstarter.annotation.ReactiveHttpClient;
import io.github.huynhngochuyhoang.httpstarter.enable.EnableReactiveHttpClients;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class Boot4WeightedCacheConsumerTest {

    private static final String CACHE_METRIC_PREFIX = "reactive.http.client.cache.";

    @Test
    void assembledConsumerCoversWeightedAdmissionHitEvictionBypassAndShutdown() {
        ConcurrentHashMap<String, AtomicInteger> dispatches = new ConcurrentHashMap<>();
        DisposableServer server = HttpServer.create().port(0)
                .route(routes -> routes.get("/weighted/{id}", (request, response) -> {
                    String id = request.param("id");
                    dispatches.computeIfAbsent(id, ignored -> new AtomicInteger()).incrementAndGet();
                    String body = switch (id) {
                        case "first" -> "four";
                        case "second" -> "abcde";
                        default -> "123456789";
                    };
                    return response.header("Content-Type", "text/plain").sendString(Mono.just(body)).then();
                }))
                .bindNow(Duration.ofSeconds(5));
        SimpleMeterRegistry registry = new SimpleMeterRegistry();

        try {
            try (ConfigurableApplicationContext context = new SpringApplicationBuilder(WeightedCacheApplication.class)
                    .web(WebApplicationType.NONE)
                    .initializers(application -> application.getBeanFactory()
                            .registerSingleton("meterRegistry", registry))
                    .properties(properties(server.port()))
                    .run()) {
                WeightedCacheClient client = context.getBean(WeightedCacheClient.class);
                assertThat(client.get("first").block()).isEqualTo("four");
                assertThat(client.get("first").block()).isEqualTo("four");
                assertThat(dispatches.get("first")).hasValue(1);

                assertThat(client.get("second").block()).isEqualTo("abcde");
                assertThat(client.get("over").block()).isEqualTo("123456789");
                assertThat(client.get("over").block()).isEqualTo("123456789");
                assertThat(dispatches.get("over")).hasValue(2);

                assertCounter(registry, "admissions", "outcome", "admitted", 2.0);
                assertCounter(registry, "admissions", "outcome", "bypassed_over_budget", 2.0);
                assertCounter(registry, "evictions", "cause", "weight", 1.0);
                assertThat(registry.get(CACHE_METRIC_PREFIX + "retained.decoded.response.bytes")
                        .tags("client.name", "weighted-cache", "cache.policy", "weighted")
                        .gauge().value()).isEqualTo(5.0);
            }

            assertThat(registry.getMeters())
                    .noneMatch(meter -> meter.getId().getName().startsWith(CACHE_METRIC_PREFIX));
        }
        finally {
            registry.close();
            server.disposeNow(Duration.ofSeconds(5));
        }
    }

    private static String[] properties(int port) {
        return new String[] {
                "spring.main.banner-mode=off",
                "reactive.http.observability.enabled=true",
                "reactive.http.observability.cache.enabled=true",
                "reactive.http.clients.weighted-cache.base-url=http://127.0.0.1:" + port,
                "reactive.http.clients.weighted-cache.cache.policies.weighted.ttl-ms=5000",
                "reactive.http.clients.weighted-cache.cache.policies.weighted.maximum-size=10",
                "reactive.http.clients.weighted-cache.cache.policies.weighted.maximum-total-decoded-response-bytes=8",
                "reactive.http.clients.weighted-cache.cache.policies.weighted.shared-response=true",
                "reactive.http.clients.weighted-cache.cache.customizations.exchangeStrategiesCustomizer=SAFE",
                "reactive.http.clients.weighted-cache.cache.customizations.webClientHttpConnectorCustomizer=SAFE",
                "reactive.http.clients.weighted-cache.cache.customizations.observationWebClientCustomizer=SAFE",
                "reactive.http.clients.weighted-cache.cache.customizations.webClientBuilder=SAFE"
        };
    }

    private static void assertCounter(
            MeterRegistry registry, String meter, String outcomeTag, String outcome, double expected) {
        assertThat(registry.get(CACHE_METRIC_PREFIX + meter)
                .tags("client.name", "weighted-cache", "cache.policy", "weighted", outcomeTag, outcome)
                .counter().count()).isEqualTo(expected);
    }

    @ReactiveHttpClient(name = "weighted-cache")
    interface WeightedCacheClient {
        @GET("/weighted/{id}")
        @CacheResponse("weighted")
        Mono<String> get(@PathVar("id") String id);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EnableReactiveHttpClients(basePackageClasses = WeightedCacheClient.class)
    static class WeightedCacheApplication {
    }
}
