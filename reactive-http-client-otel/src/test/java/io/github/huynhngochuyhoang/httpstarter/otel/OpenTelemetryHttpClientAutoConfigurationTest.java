package io.github.huynhngochuyhoang.httpstarter.otel;

import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientAutoConfiguration;
import io.github.huynhngochuyhoang.httpstarter.core.ReactiveHttpClientCustomizer;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientObserver;
import io.github.huynhngochuyhoang.httpstarter.observability.MicrometerHttpClientObserver;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.opentelemetry.api.OpenTelemetry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class OpenTelemetryHttpClientAutoConfigurationTest {

    private final ReactiveWebApplicationContextRunner baseRunner = new ReactiveWebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(OpenTelemetryHttpClientAutoConfiguration.class));

    private final ReactiveWebApplicationContextRunner runner = baseRunner
            .withUserConfiguration(OpenTelemetryConfig.class);

    @Test
    void autoConfigurationBacksOffWithoutOpenTelemetryBean() {
        baseRunner.run(context -> {
            assertThat(context).doesNotHaveBean(HttpClientObserver.class);
            assertThat(context).doesNotHaveBean(OpenTelemetryContextWebFilter.class);
            assertThat(context).doesNotHaveBean("openTelemetryContextWebClientCustomizer");
        });
    }

    @Test
    void autoConfigurationBacksOffWithoutOpenTelemetryApi() {
        baseRunner.withClassLoader(new FilteredClassLoader("io.opentelemetry.api"))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(HttpClientObserver.class);
                    assertThat(context).doesNotHaveBean(OpenTelemetryContextWebFilter.class);
                    assertThat(context).doesNotHaveBean("openTelemetryContextWebClientCustomizer");
                });
    }

    @Test
    void defaultConfigurationRegistersObserverAndPropagation() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(HttpClientObserver.class);
            assertThat(context).hasSingleBean(OpenTelemetryContextWebFilter.class);
            assertThat(context).hasBean("openTelemetryContextWebClientCustomizer");
            assertThat(context.getBean("openTelemetryContextWebClientCustomizer"))
                    .isInstanceOf(ReactiveHttpClientCustomizer.class);
        });
    }

    @Test
    void masterSwitchDisablesObserverAndPropagation() {
        runner.withPropertyValues("reactive.http.observability.otel.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(HttpClientObserver.class);
                    assertThat(context).doesNotHaveBean(OpenTelemetryContextWebFilter.class);
                    assertThat(context).doesNotHaveBean("openTelemetryContextWebClientCustomizer");
                });
    }

    @Test
    void spansCanBeDisabledWhilePropagationStaysEnabled() {
        runner.withPropertyValues("reactive.http.observability.otel.spans.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(HttpClientObserver.class);
                    assertThat(context).hasSingleBean(OpenTelemetryContextWebFilter.class);
                    assertThat(context).hasBean("openTelemetryContextWebClientCustomizer");
                });
    }

    @Test
    void propagationCanBeDisabledWhileObserverStaysEnabled() {
        runner.withPropertyValues("reactive.http.observability.otel.propagation.enabled=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(HttpClientObserver.class);
                    assertThat(context).doesNotHaveBean(OpenTelemetryContextWebFilter.class);
                    assertThat(context).doesNotHaveBean("openTelemetryContextWebClientCustomizer");
                });
    }

    @Test
    void userObserverDoesNotSuppressNamedOpenTelemetryObserver() {
        runner.withUserConfiguration(CustomObserverConfig.class)
                .run(context -> {
                    assertThat(context).hasBean("openTelemetryHttpClientObserver");
                    assertThat(context.getBean("openTelemetryHttpClientObserver"))
                            .isInstanceOf(OpenTelemetryHttpClientObserver.class);
                    assertThat(context.getBeansOfType(HttpClientObserver.class))
                            .containsKeys("customHttpClientObserver", "openTelemetryHttpClientObserver");
                });
    }

    @Test
    void starterMicrometerAndOpenTelemetryObserversCanCoexist() {
        new ReactiveWebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        ReactiveHttpClientAutoConfiguration.class,
                        OpenTelemetryHttpClientAutoConfiguration.class))
                .withUserConfiguration(OpenTelemetryConfig.class, MeterRegistryConfig.class)
                .run(context -> {
                    assertThat(context.getBean("micrometerHttpClientObserver"))
                            .isInstanceOf(MicrometerHttpClientObserver.class);
                    assertThat(context.getBean("openTelemetryHttpClientObserver"))
                            .isInstanceOf(OpenTelemetryHttpClientObserver.class);
                    assertThat(context.getBeansOfType(HttpClientObserver.class))
                            .containsKeys("micrometerHttpClientObserver", "openTelemetryHttpClientObserver");
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class OpenTelemetryConfig {
        @Bean
        OpenTelemetry openTelemetry() {
            return OpenTelemetry.noop();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomObserverConfig {
        @Bean
        HttpClientObserver customHttpClientObserver() {
            return event -> { };
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class MeterRegistryConfig {
        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }
}
