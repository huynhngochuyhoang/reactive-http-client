package io.github.huynhngochuyhoang.httpstarter.config;

import io.github.huynhngochuyhoang.httpstarter.observability.ReactiveHttpClientDiagnosticsEndpoint;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.webclient.WebClientCustomizer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class Boot4AutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ReactiveHttpClientAutoConfiguration.class));

    @Test
    void loadsFocusedBoot4ModulesAndDiscoversOptionalActuatorContracts() {
        AtomicBoolean customized = new AtomicBoolean();
        contextRunner
                .withBean(SimpleMeterRegistry.class, SimpleMeterRegistry::new)
                .withBean(WebClientCustomizer.class,
                        () -> builder -> customized.set(true))
                .withPropertyValues("reactive.http.observability.diagnostics-endpoint.enabled=true")
                .run(context -> {
                    assertThat(context.getBeansOfType(WebClient.Builder.class)).hasSize(1);
                    context.getBean(WebClient.Builder.class);
                    assertThat(customized).isTrue();
                    assertThat(context.getBean("reactiveHttpClientHealthIndicator"))
                            .isInstanceOf(HealthIndicator.class);
                    ReactiveHttpClientDiagnosticsEndpoint endpoint =
                            context.getBean(ReactiveHttpClientDiagnosticsEndpoint.class);
                    assertThat(endpoint.getClass().getAnnotation(Endpoint.class).id())
                            .isEqualTo("rhttpclients");
                });
    }

    @Test
    void startsWhenOptionalActuatorModulesAreAbsent() {
        contextRunner
                .withClassLoader(new FilteredClassLoader(
                        "org.springframework.boot.health",
                        "org.springframework.boot.actuate"))
                .withPropertyValues("reactive.http.observability.diagnostics-endpoint.enabled=true")
                .run(context -> {
                    assertThat(context.getBeansOfType(WebClient.Builder.class)).hasSize(1);
                    assertThat(context).doesNotHaveBean("reactiveHttpClientHealthIndicator");
                    assertThat(context).doesNotHaveBean("reactiveHttpClientDiagnosticsEndpoint");
                });
    }

    @Test
    void packagedAutoConfigurationImportsStartAnApplication() {
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(Boot4Application.class)
                .web(WebApplicationType.NONE)
                .properties("spring.main.banner-mode=off")
                .run()) {
            assertThat(context.getBeansOfType(ReactiveHttpClientProperties.class)).hasSize(1);
            assertThat(context.getBeansOfType(WebClient.Builder.class)).hasSize(1);
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class Boot4Application {
    }

}
