package io.github.huynhngochuyhoang.httpstarter.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import io.github.huynhngochuyhoang.httpstarter.auth.AuthProviderFactory;
import io.github.huynhngochuyhoang.httpstarter.auth.OAuth2ClientCredentialsAuthProviderFactory;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientHealthIndicator;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientObserver;
import io.github.huynhngochuyhoang.httpstarter.observability.MicrometerHttpClientObserver;
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
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.web.reactive.function.client.WebClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.web.reactive.function.client.WebClient;

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
                .run(context -> assertThat(context).doesNotHaveBean(HttpClientHealthIndicator.class));
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
