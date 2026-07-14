package io.github.huynhngochuyhoang.httpstarter.config;

import io.github.huynhngochuyhoang.httpstarter.auth.AuthProviderFactory;
import io.github.huynhngochuyhoang.httpstarter.auth.AwsSigV4AuthProviderFactory;
import io.github.huynhngochuyhoang.httpstarter.auth.OAuth2ClientCredentialsAuthProviderFactory;
import io.github.huynhngochuyhoang.httpstarter.core.DefaultErrorDecoder;
import io.github.huynhngochuyhoang.httpstarter.core.ErrorResponseMapper;
import io.github.huynhngochuyhoang.httpstarter.core.MethodMetadataCache;
import io.github.huynhngochuyhoang.httpstarter.core.ReactiveHttpClientDiagnosticsProvider;
import io.github.huynhngochuyhoang.httpstarter.filter.CorrelationIdWebFilter;
import io.github.huynhngochuyhoang.httpstarter.filter.InboundHeadersWebFilter;
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
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.context.annotation.Scope;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Auto-configuration for the reactive HTTP client starter.
 * <p>
 * Registers core beans and exposes a customisable {@link WebClient.Builder}.
 * Individual client instances are created by
 * {@link io.github.huynhngochuyhoang.httpstarter.core.ReactiveHttpClientFactoryBean}.
 *
 * <p>This class is ordered after Boot's WebClient, Jackson, Micrometer, and
 * health infrastructure so their managed beans are available before the
 * starter's nested integration conditions are evaluated.
 */
@AutoConfiguration
@AutoConfigureAfter(name = {
        "org.springframework.boot.webclient.autoconfigure.WebClientAutoConfiguration",
        "org.springframework.boot.micrometer.metrics.autoconfigure.MetricsAutoConfiguration",
        "org.springframework.boot.micrometer.metrics.autoconfigure.CompositeMeterRegistryAutoConfiguration",
        "org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration",
        "org.springframework.boot.health.autoconfigure.registry.HealthContributorRegistryAutoConfiguration"
})
@EnableConfigurationProperties(ReactiveHttpClientProperties.class)
@ImportRuntimeHints(ReactiveHttpClientRuntimeHints.class)
@org.springframework.context.annotation.Import({
        BootWebClientCustomizersConfiguration.class,
        BootHealthIndicatorAutoConfiguration.class,
        BootJsonCodecAutoConfiguration.class
})
public class ReactiveHttpClientAutoConfiguration {

    @Bean
    public static BeanFactoryInitializationAotProcessor reactiveHttpClientBeanFactoryInitializationAotProcessor() {
        return new ReactiveHttpClientBeanFactoryInitializationAotProcessor();
    }

    @Bean
    @Scope("prototype")
    @ConditionalOnMissingBean
    public WebClient.Builder starterWebClientBuilder(BootWebClientCustomizers customizers) {
        WebClient.Builder builder = WebClient.builder();
        customizers.customize(builder);
        return builder;
    }

    @Bean
    @ConditionalOnMissingBean(CorrelationIdWebFilter.class)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
    public CorrelationIdWebFilter correlationIdWebFilter(ReactiveHttpClientProperties properties) {
        return new CorrelationIdWebFilter(properties.getCorrelationId());
    }

    @Bean
    @ConditionalOnMissingBean(InboundHeadersWebFilter.class)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
    public InboundHeadersWebFilter inboundHeadersWebFilter(ReactiveHttpClientProperties properties) {
        return new InboundHeadersWebFilter(properties.getInboundHeaders());
    }

    @Bean
    @ConditionalOnMissingBean
    public DefaultErrorDecoder defaultErrorDecoder(ObjectProvider<ErrorResponseMapper> errorResponseMappers) {
        return new DefaultErrorDecoder(errorResponseMappers);
    }

    @Bean
    @ConditionalOnMissingBean
    public MethodMetadataCache methodMetadataCache() {
        return new MethodMetadataCache();
    }

    @Bean
    @ConditionalOnMissingBean
    public ReactiveHttpClientDiagnosticsProvider reactiveHttpClientDiagnosticsProvider(
            ConfigurableListableBeanFactory beanFactory,
            ReactiveHttpClientProperties properties,
            MethodMetadataCache metadataCache) {
        return new ReactiveHttpClientDiagnosticsProvider(beanFactory, properties, metadataCache);
    }

    @Bean
    @ConditionalOnMissingBean(OAuth2ClientCredentialsAuthProviderFactory.class)
    public AuthProviderFactory oauth2ClientCredentialsAuthProviderFactory() {
        return new OAuth2ClientCredentialsAuthProviderFactory();
    }

    @Bean
    @ConditionalOnMissingBean(AwsSigV4AuthProviderFactory.class)
    public AuthProviderFactory awsSigV4AuthProviderFactory() {
        return new AwsSigV4AuthProviderFactory();
    }

    /**
     * Registers the Micrometer-backed {@link HttpClientObserver} automatically when:
     * <ul>
     *   <li>{@code micrometer-core} is on the classpath ({@link MeterRegistry} present)</li>
     *   <li>A {@link MeterRegistry} bean is available in the application context</li>
     *   <li>{@code reactive.http.observability.enabled} is {@code true} (the default)</li>
     *   <li>No bean named {@code micrometerHttpClientObserver} has been registered</li>
     * </ul>
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(MeterRegistry.class)
    @ConditionalOnBean(MeterRegistry.class)
    static class MicrometerHttpClientObserverAutoConfiguration {

        @Bean
        @ConditionalOnMissingBean(name = "micrometerHttpClientObserver")
        @ConditionalOnProperty(
                prefix = "reactive.http.observability",
                name = "enabled",
                havingValue = "true",
                matchIfMissing = true)
        public MicrometerHttpClientObserver micrometerHttpClientObserver(
                MeterRegistry meterRegistry,
                ReactiveHttpClientProperties properties) {
            return new MicrometerHttpClientObserver(meterRegistry, properties.getObservability());
        }
    }

    /**
     * Binds Resilience4j's tagged metrics ({@code resilience4j.circuitbreaker.*},
     * {@code resilience4j.retry.*}, {@code resilience4j.bulkhead.*},
     * {@code resilience4j.ratelimiter.*}) to the shared
     * {@link MeterRegistry} when {@code resilience4j-micrometer} is on the classpath
     * and the corresponding Resilience4j registry bean is present in the context.
     *
     * <p>Each binding is declared as a {@link MeterBinder} bean; Spring Boot's
     * metrics infrastructure calls {@link MeterBinder#bindTo(MeterRegistry)}
     * automatically.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass({MeterRegistry.class, TaggedCircuitBreakerMetrics.class})
    @ConditionalOnBean(MeterRegistry.class)
    static class Resilience4jMetricsAutoConfiguration {

        @Bean
        @ConditionalOnBean(CircuitBreakerRegistry.class)
        @ConditionalOnMissingBean(name = "reactiveHttpCircuitBreakerMeterBinder")
        public MeterBinder reactiveHttpCircuitBreakerMeterBinder(CircuitBreakerRegistry registry) {
            return TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(registry);
        }

        @Bean
        @ConditionalOnBean(RetryRegistry.class)
        @ConditionalOnMissingBean(name = "reactiveHttpRetryMeterBinder")
        public MeterBinder reactiveHttpRetryMeterBinder(RetryRegistry registry) {
            return TaggedRetryMetrics.ofRetryRegistry(registry);
        }

        @Bean
        @ConditionalOnBean(BulkheadRegistry.class)
        @ConditionalOnMissingBean(name = "reactiveHttpBulkheadMeterBinder")
        public MeterBinder reactiveHttpBulkheadMeterBinder(BulkheadRegistry registry) {
            return TaggedBulkheadMetrics.ofBulkheadRegistry(registry);
        }

        @Bean
        @ConditionalOnBean(RateLimiterRegistry.class)
        @ConditionalOnMissingBean(name = "reactiveHttpRateLimiterMeterBinder")
        public MeterBinder reactiveHttpRateLimiterMeterBinder(RateLimiterRegistry registry) {
            return TaggedRateLimiterMetrics.ofRateLimiterRegistry(registry);
        }
    }

    /**
     * Registers the sanitized diagnostics Actuator endpoint only when Actuator
     * endpoint infrastructure is present and the endpoint is explicitly enabled.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.boot.actuate.endpoint.annotation.Endpoint")
    @ConditionalOnProperty(
            prefix = "reactive.http.observability",
            name = "diagnostics-endpoint.enabled",
            havingValue = "true")
    static class HttpClientDiagnosticsEndpointAutoConfiguration {

        @Bean
        @ConditionalOnMissingBean(name = "reactiveHttpClientDiagnosticsEndpoint")
        public ReactiveHttpClientDiagnosticsEndpoint reactiveHttpClientDiagnosticsEndpoint(
                ReactiveHttpClientDiagnosticsProvider diagnosticsProvider) {
            return new ReactiveHttpClientDiagnosticsEndpoint(diagnosticsProvider);
        }
    }



}
