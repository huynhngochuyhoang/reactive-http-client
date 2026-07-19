package io.github.huynhngochuyhoang.httpstarter.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import io.github.huynhngochuyhoang.httpstarter.annotation.GET;
import io.github.huynhngochuyhoang.httpstarter.annotation.ReactiveHttpClient;
import io.github.huynhngochuyhoang.httpstarter.auth.AuthProviderFactory;
import io.github.huynhngochuyhoang.httpstarter.auth.OAuth2ClientCredentialsAuthProviderFactory;
import io.github.huynhngochuyhoang.httpstarter.core.ReactiveHttpClientDiagnosticsProvider;
import io.github.huynhngochuyhoang.httpstarter.core.ReactiveHttpClientDiagnosticsSnapshot;
import io.github.huynhngochuyhoang.httpstarter.core.ReactiveHttpClientFactoryBean;
import io.github.huynhngochuyhoang.httpstarter.observability.Boot4HttpClientHealthIndicator;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientObserver;
import io.github.huynhngochuyhoang.httpstarter.observability.MicrometerHttpClientObserver;
import io.github.huynhngochuyhoang.httpstarter.observability.ReactiveHttpClientDiagnosticsEndpoint;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.micrometer.tagged.TaggedBulkheadMetrics;
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import io.github.resilience4j.micrometer.tagged.TaggedRateLimiterMetrics;
import io.github.resilience4j.micrometer.tagged.TaggedRetryMetrics;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webclient.WebClientCustomizer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression tests for {@link ReactiveHttpClientAutoConfiguration}'s prototype-scoped
 * {@code starterWebClientBuilder} — specifically that registered
 * {@link WebClientCustomizer} beans are applied to every builder instance
 * (roadmap item 3.9), and that the prototype scope introduced in 1.8.1 still produces
 * distinct builder instances per pull (no filter state sharing).
 */
@ExtendWith(OutputCaptureExtension.class)
class ReactiveHttpClientAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ReactiveHttpClientAutoConfiguration.class));

    @Test
    void starterBuilderAppliesRegisteredCustomizer() {
        runner.withUserConfiguration(CountingCustomizerConfig.class)
                .run(context -> {
                    CountingCustomizer customizer = context.getBean(CountingCustomizer.class);

                    WebClient.Builder builder = context.getBean(WebClient.Builder.class);

                    assertThat(builder).isNotNull();
                    assertThat(customizer.invocationCount()).isEqualTo(1);
                    assertThat(customizer.customizedBuilders()).containsExactly(builder);
                });
    }

    @Test
    void customizerAppliedExactlyOncePerBuilderInstance() {
        runner.withUserConfiguration(CountingCustomizerConfig.class)
                .run(context -> {
                    CountingCustomizer customizer = context.getBean(CountingCustomizer.class);

                    WebClient.Builder first = context.getBean(WebClient.Builder.class);
                    WebClient.Builder second = context.getBean(WebClient.Builder.class);
                    WebClient.Builder third = context.getBean(WebClient.Builder.class);

                    assertThat(customizer.invocationCount())
                            .as("customizer must fire once per builder pulled from the prototype bean")
                            .isEqualTo(3);
                    assertThat(customizer.customizedBuilders())
                            .containsExactly(first, second, third);
                });
    }

    @Test
    void starterBuilderIsPrototypeScopedSoStateIsNotSharedAcrossClients() {
        runner.run(context -> {
            WebClient.Builder first = context.getBean(WebClient.Builder.class);
            WebClient.Builder second = context.getBean(WebClient.Builder.class);

            assertThat(first)
                    .as("prototype scope must hand out a distinct builder per pull — "
                            + "shared instance is the 1.8.1 auth-leak regression")
                    .isNotSameAs(second);
        });
    }

    @Test
    void diagnosticsProviderIsRegisteredWithoutActuatorEndpoint() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(ReactiveHttpClientDiagnosticsProvider.class);
            assertThat(context).doesNotHaveBean("reactiveHttpClientDiagnosticsEndpoint");
        });
    }

    @Test
    void diagnosticsEndpointRegisteredWhenExplicitlyEnabledAndReturnsSanitizedSnapshot() {
        runner.withInitializer(ReactiveHttpClientAutoConfigurationTest::registerDiagnosticEndpointClient)
                .withPropertyValues(
                        "reactive.http.observability.diagnostics-endpoint.enabled=true",
                        "reactive.http.clients.diagnostic-client.base-url=https://user:token@example.com",
                        "reactive.http.clients.diagnostic-client.auth-provider=secretAuthProviderBean",
                        "reactive.http.clients.diagnostic-client.default-headers.Authorization=Bearer secret-token",
                        "reactive.http.clients.diagnostic-client.follow-redirects=true",
                        "reactive.http.clients.diagnostic-client.request-timeout-ms=500",
                        "reactive.http.clients.diagnostic-client.resilience.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(ReactiveHttpClientDiagnosticsEndpoint.class);
                    assertThat(ReactiveHttpClientDiagnosticsEndpoint.class
                            .getAnnotation(org.springframework.boot.actuate.endpoint.annotation.Endpoint.class).id())
                            .isEqualTo("rhttpclients");

                    Map<String, Object> snapshot = context.getBean(ReactiveHttpClientDiagnosticsEndpoint.class)
                            .diagnostics();

                    assertThat(snapshot)
                            .containsEntry("schemaVersion", 1)
                            .containsEntry("clientCount", 1)
                            .containsEntry("endpointCount", 2)
                            .containsEntry("inheritedEndpointCount", 1)
                            .containsKey("projectVersion")
                            .containsOnlyKeys("schemaVersion", "projectVersion", "clientCount",
                                    "endpointCount", "inheritedEndpointCount", "clients");
                    assertThat(snapshot.get("schemaVersion")).isInstanceOf(Integer.class);
                    assertThat(snapshot.get("projectVersion")).isInstanceOf(String.class);
                    assertThat(snapshot.get("clientCount")).isInstanceOf(Integer.class);
                    assertThat(snapshot.get("endpointCount")).isInstanceOf(Integer.class);
                    assertThat(snapshot.get("inheritedEndpointCount")).isInstanceOf(Integer.class);
                    assertThat(snapshot.get("clients")).isInstanceOf(List.class);
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> clients = (List<Map<String, Object>>) snapshot.get("clients");
                    assertThat(clients).hasSize(1);
                    assertThat(clients.get(0))
                            .containsOnlyKeys("clientName", "clientInterface", "baseUrlSource",
                                    "poolSource", "poolMaxConnections", "poolPendingAcquireTimeoutMs",
                                    "poolMetricsEnabled", "timeoutSource", "timeoutMs",
                                    "resilienceConfigured", "retry", "rateLimiter", "circuitBreaker",
                                    "bulkhead", "strictUnsafeRetryValidation",
                                    "strictBodySigningValidation", "authMode", "followRedirects",
                                    "endpointCount", "inheritedEndpointCount");
                    assertThat(clients.get(0).get("poolMaxConnections")).isInstanceOf(Integer.class);
                    assertThat(clients.get(0).get("poolPendingAcquireTimeoutMs")).isInstanceOf(Long.class);
                    assertThat(clients.get(0).get("timeoutMs")).isInstanceOf(Long.class);
                    assertThat(clients.get(0).get("followRedirects")).isInstanceOf(Boolean.class);
                    assertThat(clients.get(0))
                            .containsEntry("clientName", "diagnostic-client")
                            .containsEntry("clientInterface", DiagnosticEndpointClient.class.getName())
                            .containsEntry("baseUrlSource", "property")
                            .containsEntry("timeoutSource", "client")
                            .containsEntry("timeoutMs", 500L)
                            .containsEntry("resilienceConfigured", true)
                            .containsEntry("authMode", "provider-bean")
                            .containsEntry("followRedirects", true)
                            .containsEntry("endpointCount", 2)
                            .containsEntry("inheritedEndpointCount", 1);
                    assertThat(snapshot.toString())
                            .doesNotContain("https://user:token@example.com")
                            .doesNotContain("user:token")
                            .doesNotContain("secretAuthProviderBean")
                            .doesNotContain("secret-token")
                            .doesNotContain("Authorization")
                            .doesNotContain("requestBody")
                            .doesNotContain("responseBody");
                });
    }

    @Test
    void diagnosticsEndpointSummarizesMultipleInheritedGenericAndStrictClients() {
        runner.withInitializer(context -> {
                    registerDiagnosticEndpointClient(
                            context, "diagnosticBusEndpointClient", DiagnosticEndpointBusClient.class);
                    registerDiagnosticEndpointClient(
                            context, "diagnosticTrainEndpointClient", DiagnosticEndpointTrainClient.class);
                    registerDiagnosticEndpointClient(
                            context, "diagnosticStrictEndpointClient", DiagnosticEndpointStrictClient.class);
                })
                .withUserConfiguration(Resilience4jRegistriesConfig.class)
                .withPropertyValues(
                        "reactive.http.observability.diagnostics-endpoint.enabled=true",
                        "reactive.http.clients.diagnostic-bus.base-url=https://bus.internal.example",
                        "reactive.http.clients.diagnostic-bus.request-timeout-ms=150",
                        "reactive.http.clients.diagnostic-bus.follow-redirects=true",
                        "reactive.http.clients.diagnostic-train.base-url=https://train.internal.example",
                        "reactive.http.clients.strict-diagnostic-client.base-url=https://strict.internal.example",
                        "reactive.http.clients.strict-diagnostic-client.resilience.enabled=true",
                        "reactive.http.clients.strict-diagnostic-client.resilience.retry=strict-retry",
                        "reactive.http.clients.strict-diagnostic-client.resilience.retry-methods[0]=GET",
                        "reactive.http.clients.strict-diagnostic-client.resilience.strict-unsafe-retry-validation=true",
                        "reactive.http.clients.strict-diagnostic-client.auth.type=aws-sigv4",
                        "reactive.http.clients.strict-diagnostic-client.auth.aws-sig-v4.access-key-id=AKIA_TEST",
                        "reactive.http.clients.strict-diagnostic-client.auth.aws-sig-v4.secret-access-key=super-secret",
                        "reactive.http.clients.strict-diagnostic-client.auth.aws-sig-v4.region=us-east-1",
                        "reactive.http.clients.strict-diagnostic-client.auth.aws-sig-v4.service=execute-api",
                        "reactive.http.clients.strict-diagnostic-client.auth.aws-sig-v4.strict-body-signing-validation=true")
                .run(context -> {
                    ReactiveHttpClientDiagnosticsProvider provider =
                            context.getBean(ReactiveHttpClientDiagnosticsProvider.class);
                    Map<String, Object> snapshot = context.getBean(ReactiveHttpClientDiagnosticsEndpoint.class)
                            .diagnostics();

                    assertThat(snapshot).isEqualTo(ReactiveHttpClientDiagnosticsSnapshot.toMap(provider));
                    assertThat(snapshot)
                            .containsEntry("schemaVersion", 1)
                            .containsEntry("clientCount", 3)
                            .containsEntry("endpointCount", 3)
                            .containsEntry("inheritedEndpointCount", 2);
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> clients = (List<Map<String, Object>>) snapshot.get("clients");
                    assertThat(clients)
                            .extracting(client -> client.get("clientName"))
                            .containsExactly("diagnostic-bus", "diagnostic-train", "strict-diagnostic-client");

                    assertThat(diagnosticClient(clients, "diagnostic-bus"))
                            .containsEntry("clientInterface", DiagnosticEndpointBusClient.class.getName())
                            .containsEntry("baseUrlSource", "property")
                            .containsEntry("timeoutSource", "client")
                            .containsEntry("timeoutMs", 150L)
                            .containsEntry("followRedirects", true)
                            .containsEntry("endpointCount", 1)
                            .containsEntry("inheritedEndpointCount", 1)
                            .containsEntry("strictUnsafeRetryValidation", false)
                            .containsEntry("strictBodySigningValidation", false);
                    assertThat(diagnosticClient(clients, "diagnostic-train"))
                            .containsEntry("clientInterface", DiagnosticEndpointTrainClient.class.getName())
                            .containsEntry("endpointCount", 1)
                            .containsEntry("inheritedEndpointCount", 1);
                    assertThat(diagnosticClient(clients, "strict-diagnostic-client"))
                            .containsEntry("clientInterface", DiagnosticEndpointStrictClient.class.getName())
                            .containsEntry("resilienceConfigured", true)
                            .containsEntry("retry", "strict-retry")
                            .containsEntry("authMode", "aws-sigv4")
                            .containsEntry("strictUnsafeRetryValidation", true)
                            .containsEntry("strictBodySigningValidation", true)
                            .containsEntry("endpointCount", 1)
                            .containsEntry("inheritedEndpointCount", 0);

                    assertThat(snapshot.toString())
                            .doesNotContain("bus.internal.example")
                            .doesNotContain("train.internal.example")
                            .doesNotContain("strict.internal.example")
                            .doesNotContain("AKIA_TEST")
                            .doesNotContain("super-secret")
                            .doesNotContain("requestBody")
                            .doesNotContain("responseBody");
                });
    }

    @Test
    void diagnosticsEndpointSkippedWhenActuatorEndpointClassesMissing() {
        runner.withClassLoader(new FilteredClassLoader("org.springframework.boot.actuate.endpoint.annotation"))
                .withPropertyValues("reactive.http.observability.diagnostics-endpoint.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean("reactiveHttpClientDiagnosticsEndpoint");
                });
    }


    @Test
    void userSuppliedWebClientBuilderOverridesStarterPrototypeBuilder() {
        runner.withUserConfiguration(UserWebClientBuilderConfig.class, CountingCustomizerConfig.class)
                .run(context -> {
                    CountingCustomizer customizer = context.getBean(CountingCustomizer.class);

                    assertThat(context).hasSingleBean(WebClient.Builder.class);
                    assertThat(context).hasBean("userWebClientBuilder");
                    assertThat(customizer.invocationCount())
                            .as("starter customizer application only happens on the starter-managed prototype builder")
                            .isZero();
                });
    }

    @Test
    void customizersAppliedInOrder() {
        runner.withUserConfiguration(OrderedCustomizersConfig.class)
                .run(context -> {
                    OrderRecorder recorder = context.getBean(OrderRecorder.class);

                    context.getBean(WebClient.Builder.class);

                    assertThat(recorder.order)
                            .as("WebClientCustomizer beans must be applied in @Order sequence")
                            .containsExactly("first", "second", "third");
                });
    }

    @Test
    void debugDiagnosticsListAppliedWebClientCustomizers(CapturedOutput output) {
        Logger logger = (Logger) LoggerFactory.getLogger(ReactiveHttpClientAutoConfiguration.class);
        Level previousLevel = logger.getLevel();
        logger.setLevel(Level.DEBUG);
        try {
            runner.withUserConfiguration(OrderedCustomizersConfig.class)
                    .run(context -> context.getBean(WebClient.Builder.class));

            assertThat(output.getOut())
                    .contains("Applying WebClientCustomizer")
                    .contains(OrderedCustomizersConfig.class.getName());
        } finally {
            logger.setLevel(previousLevel);
        }
    }

    private static void registerDiagnosticEndpointClient(ConfigurableApplicationContext context) {
        registerDiagnosticEndpointClient(context, "diagnosticEndpointClient", DiagnosticEndpointClient.class);
    }

    private static void registerDiagnosticEndpointClient(
            ConfigurableApplicationContext context, String beanName, Class<?> clientInterface) {
        GenericBeanDefinition definition = new GenericBeanDefinition();
        definition.setBeanClass(ReactiveHttpClientFactoryBean.class);
        definition.setAttribute(FactoryBean.OBJECT_TYPE_ATTRIBUTE, clientInterface);
        ((BeanDefinitionRegistry) context.getBeanFactory())
                .registerBeanDefinition(beanName, definition);
    }

    private static Map<String, Object> diagnosticClient(List<Map<String, Object>> clients, String clientName) {
        return clients.stream()
                .filter(client -> clientName.equals(client.get("clientName")))
                .findFirst()
                .orElseThrow();
    }

    interface DiagnosticEndpointSharedOperations {

        @GET("/shared")
        Mono<String> shared();
    }

    @ReactiveHttpClient(name = "diagnostic-client")
    interface DiagnosticEndpointClient extends DiagnosticEndpointSharedOperations {

        @GET("/direct")
        Mono<String> direct();
    }

    interface DiagnosticEndpointGenericOperations<T> {

        @GET("/generic-order")
        Mono<T> getOrder();
    }

    @ReactiveHttpClient(name = "diagnostic-bus")
    interface DiagnosticEndpointBusClient extends DiagnosticEndpointGenericOperations<DiagnosticEndpointBusResponse> {
    }

    @ReactiveHttpClient(name = "diagnostic-train")
    interface DiagnosticEndpointTrainClient extends DiagnosticEndpointGenericOperations<DiagnosticEndpointTrainResponse> {
    }

    @ReactiveHttpClient(name = "strict-diagnostic-client")
    interface DiagnosticEndpointStrictClient {

        @GET("/strict")
        Mono<String> strict();
    }

    static class DiagnosticEndpointBusResponse {
    }

    static class DiagnosticEndpointTrainResponse {
    }

    // -------------------------------------------------------------------------
    // Test fixtures
    // -------------------------------------------------------------------------

    @Configuration(proxyBeanMethods = false)
    static class CountingCustomizerConfig {
        @Bean
        CountingCustomizer countingCustomizer() {
            return new CountingCustomizer();
        }
    }

    static class CountingCustomizer implements WebClientCustomizer {
        private final AtomicInteger count = new AtomicInteger();
        private final List<WebClient.Builder> seen = new ArrayList<>();

        @Override
        public synchronized void customize(WebClient.Builder builder) {
            count.incrementAndGet();
            seen.add(builder);
        }

        int invocationCount() {
            return count.get();
        }

        synchronized List<WebClient.Builder> customizedBuilders() {
            return List.copyOf(seen);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class UserWebClientBuilderConfig {
        @Bean
        WebClient.Builder userWebClientBuilder() {
            return WebClient.builder();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class OrderedCustomizersConfig {
        @Bean
        OrderRecorder orderRecorder() {
            return new OrderRecorder();
        }

        @Bean
        @Order(1)
        WebClientCustomizer firstCustomizer(OrderRecorder recorder) {
            return builder -> recorder.order.add("first");
        }

        @Bean
        @Order(2)
        WebClientCustomizer secondCustomizer(OrderRecorder recorder) {
            return builder -> recorder.order.add("second");
        }

        @Bean
        @Order(3)
        WebClientCustomizer thirdCustomizer(OrderRecorder recorder) {
            return builder -> recorder.order.add("third");
        }
    }

    static class OrderRecorder {
        final List<String> order = new ArrayList<>();
    }

    // -------------------------------------------------------------------------
    // Resilience4j Micrometer binding tests (roadmap 2.1b)
    // -------------------------------------------------------------------------

    @Test
    void resilience4jMeterBindersRegisteredWhenRegistriesArePresent() {
        runner.withUserConfiguration(Resilience4jRegistriesConfig.class, SimpleMeterRegistryConfig.class)
                .run(context -> {
                    Map<String, MeterBinder> binders = context.getBeansOfType(MeterBinder.class);

                    assertThat(binders).containsKeys(
                            "reactiveHttpCircuitBreakerMeterBinder",
                            "reactiveHttpRetryMeterBinder",
                            "reactiveHttpBulkheadMeterBinder",
                            "reactiveHttpRateLimiterMeterBinder");
                    assertThat(binders.get("reactiveHttpCircuitBreakerMeterBinder"))
                            .isInstanceOf(TaggedCircuitBreakerMetrics.class);
                    assertThat(binders.get("reactiveHttpRetryMeterBinder"))
                            .isInstanceOf(TaggedRetryMetrics.class);
                    assertThat(binders.get("reactiveHttpBulkheadMeterBinder"))
                            .isInstanceOf(TaggedBulkheadMetrics.class);
                    assertThat(binders.get("reactiveHttpRateLimiterMeterBinder"))
                            .isInstanceOf(TaggedRateLimiterMetrics.class);
                });
    }

    @Test
    void resilience4jBindersSkippedWhenRegistryBeansMissing() {
        runner.withUserConfiguration(SimpleMeterRegistryConfig.class)
                .run(context -> {
                    Map<String, MeterBinder> binders = context.getBeansOfType(MeterBinder.class);

                    assertThat(binders).doesNotContainKeys(
                            "reactiveHttpCircuitBreakerMeterBinder",
                            "reactiveHttpRetryMeterBinder",
                            "reactiveHttpBulkheadMeterBinder",
                            "reactiveHttpRateLimiterMeterBinder");
                });
    }

    @Test
    void resilience4jBindersSkippedWhenMeterRegistryMissing() {
        runner.withUserConfiguration(Resilience4jRegistriesConfig.class)
                .run(context -> {
                    Map<String, MeterBinder> binders = context.getBeansOfType(MeterBinder.class);

                    assertThat(binders).isEmpty();
                });
    }

    @Test
    void resilience4jBindersSkippedWhenTaggedMetricsClassMissing() {
        runner.withClassLoader(new FilteredClassLoader(TaggedCircuitBreakerMetrics.class))
                .withUserConfiguration(Resilience4jRegistriesConfig.class, SimpleMeterRegistryConfig.class)
                .run(context -> assertThat(context.getBeansOfType(MeterBinder.class)).isEmpty());
    }

    @Test
    void starterContextLoadsWhenMicrometerMissing() {
        runner.withClassLoader(new FilteredClassLoader("io.micrometer"))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(WebClient.Builder.class);
                    assertThat(context).doesNotHaveBean("micrometerHttpClientObserver");
                });
    }

    @Test
    void userObserverDoesNotSuppressNamedMicrometerObserver() {
        runner.withUserConfiguration(SimpleMeterRegistryConfig.class, CustomObserverConfig.class)
                .run(context -> {
                    assertThat(context).hasBean("micrometerHttpClientObserver");
                    assertThat(context.getBean("micrometerHttpClientObserver"))
                            .isInstanceOf(MicrometerHttpClientObserver.class);
                    assertThat(context.getBeansOfType(HttpClientObserver.class))
                            .containsKeys("customHttpClientObserver", "micrometerHttpClientObserver");
                });
    }

    @Test
    void namedUserObserverOverridesBuiltInMicrometerObserver() {
        runner.withUserConfiguration(SimpleMeterRegistryConfig.class, NamedMicrometerObserverConfig.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(HttpClientObserver.class);
                    assertThat(context.getBean("micrometerHttpClientObserver"))
                            .isNotInstanceOf(MicrometerHttpClientObserver.class);
                });
    }

    @Test
    void micrometerObserverSkippedWhenObservabilityDisabled() {
        runner.withUserConfiguration(SimpleMeterRegistryConfig.class)
                .withPropertyValues("reactive.http.observability.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean("micrometerHttpClientObserver"));
    }

    @Test
    void healthIndicatorSkippedWhenHealthDisabled() {
        runner.withUserConfiguration(SimpleMeterRegistryConfig.class)
                .withPropertyValues("reactive.http.observability.health.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(Boot4HttpClientHealthIndicator.class));
    }

    @Test
    void userSuppliedOauth2AuthProviderFactoryOverridesBuiltInFactory() {
        runner.withUserConfiguration(UserOauthFactoryConfig.class)
                .run(context -> {
                    assertThat(context).hasBean("oauth2ClientCredentialsAuthProviderFactory");
                    assertThat(context.getBeansOfType(OAuth2ClientCredentialsAuthProviderFactory.class))
                            .containsOnlyKeys("oauth2ClientCredentialsAuthProviderFactory");
                    assertThat(context.getBeansOfType(AuthProviderFactory.class))
                            .containsKeys("oauth2ClientCredentialsAuthProviderFactory", "awsSigV4AuthProviderFactory");
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class Resilience4jRegistriesConfig {
        @Bean
        CircuitBreakerRegistry circuitBreakerRegistry() {
            return CircuitBreakerRegistry.ofDefaults();
        }

        @Bean
        RetryRegistry retryRegistry() {
            return RetryRegistry.ofDefaults();
        }

        @Bean
        BulkheadRegistry bulkheadRegistry() {
            return BulkheadRegistry.ofDefaults();
        }

        @Bean
        RateLimiterRegistry rateLimiterRegistry() {
            return RateLimiterRegistry.ofDefaults();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class SimpleMeterRegistryConfig {
        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
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
    static class NamedMicrometerObserverConfig {
        @Bean(name = "micrometerHttpClientObserver")
        HttpClientObserver micrometerHttpClientObserver() {
            return event -> { };
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class UserOauthFactoryConfig {
        @Bean
        OAuth2ClientCredentialsAuthProviderFactory oauth2ClientCredentialsAuthProviderFactory() {
            return new OAuth2ClientCredentialsAuthProviderFactory();
        }
    }
}
