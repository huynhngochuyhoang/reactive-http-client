package io.github.huynhngochuyhoang.httpstarter.config;

import io.github.huynhngochuyhoang.httpstarter.config.smoke.AotSmokeClient;
import io.github.huynhngochuyhoang.httpstarter.enable.EnableReactiveHttpClients;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.webclient.WebClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

class ReleaseCompatibilitySmokeTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ReactiveHttpClientAutoConfiguration.class));

    @Test
    void minimalDeclarativeClientWorksWithMicrometerEnabled() {
        runner.withUserConfiguration(ClientScanConfig.class, StubHttpConfig.class, MetricsConfig.class)
                .run(context -> {
                    AotSmokeClient client = context.getBean(AotSmokeClient.class);
                    MeterRegistry meterRegistry = context.getBean(MeterRegistry.class);

                    assertThat(client.ping().block()).isEqualTo("pong");

                    assertThat(context).hasBean("micrometerHttpClientObserver");
                    assertThat(meterRegistry.find("reactive.http.client.requests").timer())
                            .isNotNull()
                            .extracting(timer -> timer.count())
                            .isEqualTo(1L);
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableReactiveHttpClients(basePackageClasses = AotSmokeClient.class)
    static class ClientScanConfig {
    }

    @Configuration(proxyBeanMethods = false)
    static class StubHttpConfig {
        @Bean
        WebClientCustomizer stubExchangeFunctionCustomizer() {
            return builder -> builder.exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.OK)
                    .body("pong")
                    .build()));
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class MetricsConfig {
        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }
}
