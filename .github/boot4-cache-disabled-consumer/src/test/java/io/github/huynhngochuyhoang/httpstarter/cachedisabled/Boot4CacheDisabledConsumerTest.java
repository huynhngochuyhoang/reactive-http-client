package io.github.huynhngochuyhoang.httpstarter.cachedisabled;

import io.github.huynhngochuyhoang.httpstarter.annotation.GET;
import io.github.huynhngochuyhoang.httpstarter.annotation.ReactiveHttpClient;
import io.github.huynhngochuyhoang.httpstarter.enable.EnableReactiveHttpClients;
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

import static org.assertj.core.api.Assertions.assertThat;

class Boot4CacheDisabledConsumerTest {

    @Test
    void cacheDisabledConsumerRunsWithoutCaffeine() {
        assertThat(ClassUtils.isPresent(
                "com.github.benmanes.caffeine.cache.Caffeine", getClass().getClassLoader())).isFalse();
        DisposableServer server = HttpServer.create().port(0)
                .route(routes -> routes.get("/value", (request, response) ->
                        response.header("Content-Type", "text/plain").sendString(Mono.just("ok")).then()))
                .bindNow(Duration.ofSeconds(5));
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(CacheDisabledApplication.class)
                .web(WebApplicationType.NONE)
                .properties(
                        "spring.main.banner-mode=off",
                        "reactive.http.clients.cache-disabled.base-url=http://127.0.0.1:" + server.port())
                .run()) {
            assertThat(context.getBean(CacheDisabledClient.class).get().block()).isEqualTo("ok");
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
