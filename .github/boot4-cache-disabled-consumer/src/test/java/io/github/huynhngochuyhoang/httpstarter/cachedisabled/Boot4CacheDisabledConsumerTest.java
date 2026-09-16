package io.github.huynhngochuyhoang.httpstarter.cachedisabled;

import io.github.huynhngochuyhoang.httpstarter.annotation.GET;
import io.github.huynhngochuyhoang.httpstarter.annotation.ReactiveHttpClient;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import io.github.huynhngochuyhoang.httpstarter.enable.EnableReactiveHttpClients;
import io.netty.handler.codec.http.HttpMethod;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.util.ClassUtils;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class Boot4CacheDisabledConsumerTest {

    @Test
    void cacheDisabledConsumerRunsWithoutCaffeine() {
        for (String optionalType : new String[]{
                "com.github.benmanes.caffeine.cache.Caffeine",
                "io.github.resilience4j.retry.RetryRegistry",
                "io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry",
                "io.github.resilience4j.bulkhead.BulkheadRegistry",
                "io.github.resilience4j.ratelimiter.RateLimiterRegistry",
                "io.micrometer.core.instrument.MeterRegistry",
                "io.opentelemetry.api.OpenTelemetry",
                "io.github.huynhngochuyhoang.httpstarter.test.MockReactiveHttpClient"}) {
            assertThat(ClassUtils.isPresent(optionalType, getClass().getClassLoader()))
                    .as("optional integration absent: %s", optionalType).isFalse();
        }
        AtomicInteger requests = new AtomicInteger();
        DisposableServer server = HttpServer.create().port(0)
                .handle((request, response) -> {
                    requests.incrementAndGet();
                    return response.status(HttpMethod.GET.equals(request.method())
                                    && "/value".equals(request.uri()) ? 200 : 404)
                            .header("Content-Type", "text/plain").sendString(Mono.just("ok")).then();
                })
                .bindNow(Duration.ofSeconds(5));
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(CacheDisabledApplication.class)
                .web(WebApplicationType.NONE)
                .properties(
                        "spring.main.banner-mode=off",
                        "reactive.http.clients.cache-disabled.resilience.enabled=true",
                        "reactive.http.clients.cache-disabled.base-url=http://127.0.0.1:" + server.port())
                .run()) {
            var resilience = context.getBean(ReactiveHttpClientProperties.class)
                    .getClients().get("cache-disabled").getResilience();
            assertThat(resilience.isEnabled()).isTrue();
            assertThat(resilience.getRetry()).isNull();
            assertThat(resilience.getCircuitBreaker()).isNull();
            assertThat(resilience.getBulkhead()).isNull();
            assertThat(resilience.getRateLimiter()).isNull();
            CacheDisabledClient client = context.getBean(CacheDisabledClient.class);
            assertThat(client.get().block(Duration.ofSeconds(5))).isEqualTo("ok");
            assertThat(client.get().block(Duration.ofSeconds(5))).isEqualTo("ok");
            assertThat(requests).hasValue(2);
        }
        finally {
            server.disposeNow(Duration.ofSeconds(5));
        }
    }

    @ReactiveHttpClient(name = "cache-disabled")
    interface CacheDisabledClient {
        @GET("/value")
        Mono<String> get();
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EnableReactiveHttpClients(basePackageClasses = CacheDisabledClient.class)
    static class CacheDisabledApplication {
    }
}
