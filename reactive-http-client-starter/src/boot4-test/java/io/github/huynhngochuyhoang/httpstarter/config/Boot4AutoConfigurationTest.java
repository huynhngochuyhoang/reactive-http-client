package io.github.huynhngochuyhoang.httpstarter.config;

import io.github.huynhngochuyhoang.httpstarter.core.Jackson3ReactiveHttpClientJsonCodec;
import io.github.huynhngochuyhoang.httpstarter.core.ReactiveHttpClientJsonCodec;
import io.github.huynhngochuyhoang.httpstarter.observability.ReactiveHttpClientDiagnosticsEndpoint;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.webclient.WebClientCustomizer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.ProblemDetail;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.StdSerializer;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class Boot4AutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ReactiveHttpClientAutoConfiguration.class));

    @Test
    void ordersAfterBoot4MetricsAutoConfigurations() {
        AutoConfigureAfter ordering =
                ReactiveHttpClientAutoConfiguration.class.getAnnotation(AutoConfigureAfter.class);

        assertThat(ordering.name()).contains(
                "org.springframework.boot.micrometer.metrics.autoconfigure.MetricsAutoConfiguration",
                "org.springframework.boot.micrometer.metrics.autoconfigure.CompositeMeterRegistryAutoConfiguration");
    }

    @Test
    void jackson3CodecWorksWithJackson2Hidden() {
        contextRunner
                .withClassLoader(new FilteredClassLoader("com.fasterxml.jackson"))
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .run(context -> {
                    ReactiveHttpClientJsonCodec codec = context.getBean(ReactiveHttpClientJsonCodec.class);
                    assertThat(codec).isInstanceOf(Jackson3ReactiveHttpClientJsonCodec.class);
                    ProblemDetail detail = codec.read(
                            "{\"status\":400,\"title\":\"invalid\"}"
                                    .getBytes(StandardCharsets.UTF_8),
                            ProblemDetail.class);
                    assertThat(detail.getStatus()).isEqualTo(400);
                    assertThat(detail.getTitle()).isEqualTo("invalid");
                });
    }

    @Test
    void jackson3CodecHonorsApplicationMapperConfiguration() throws Exception {
        ObjectMapper objectMapper = JsonMapper.builder()
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
        Jackson3ReactiveHttpClientJsonCodec codec =
                new Jackson3ReactiveHttpClientJsonCodec(objectMapper);

        byte[] encoded = codec.write(new CodecPayload("order-1", LocalDate.of(2026, 7, 11)));
        assertThat(new String(encoded, StandardCharsets.UTF_8))
                .contains("order_id", "2026-07-11")
                .doesNotContain("orderId");

        CodecPayload decoded = codec.read(
                "{\"order_id\":\"order-2\",\"created_on\":\"2026-07-12\",\"ignored\":true}"
                        .getBytes(StandardCharsets.UTF_8),
                CodecPayload.class);
        assertThat(decoded.orderId()).isEqualTo("order-2");
        assertThat(decoded.createdOn()).isEqualTo(LocalDate.of(2026, 7, 12));
    }

    @Test
    void jackson3CodecHonorsCustomSerializers() throws Exception {
        SimpleModule module = new SimpleModule()
                .addSerializer(CustomValue.class, new CustomValueSerializer());
        Jackson3ReactiveHttpClientJsonCodec codec = new Jackson3ReactiveHttpClientJsonCodec(
                JsonMapper.builder().addModule(module).build());

        byte[] encoded = codec.write(new CustomPayload(new CustomValue("configured")));

        assertThat(new String(encoded, StandardCharsets.UTF_8))
                .isEqualTo("{\"value\":\"CUSTOM:configured\"}");
    }

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
                    assertThat(context.getBeansOfType(HealthIndicator.class))
                            .containsKey("reactiveHttpClientHealthIndicator");
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

    record CodecPayload(String orderId, LocalDate createdOn) {
    }

    record CustomPayload(CustomValue value) {
    }

    record CustomValue(String value) {
    }

    static final class CustomValueSerializer extends StdSerializer<CustomValue> {
        CustomValueSerializer() {
            super(CustomValue.class);
        }

        @Override
        public void serialize(
                CustomValue value,
                JsonGenerator generator,
                SerializationContext context) throws JacksonException {
            generator.writeString("CUSTOM:" + value.value());
        }
    }

}
