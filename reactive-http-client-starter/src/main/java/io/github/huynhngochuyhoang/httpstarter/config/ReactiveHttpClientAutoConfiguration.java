package io.github.huynhngochuyhoang.httpstarter.config;

import io.github.huynhngochuyhoang.httpstarter.auth.AuthProviderFactory;
import io.github.huynhngochuyhoang.httpstarter.auth.AwsSigV4AuthProviderFactory;
import io.github.huynhngochuyhoang.httpstarter.auth.OAuth2ClientCredentialsAuthProviderFactory;
import io.github.huynhngochuyhoang.httpstarter.core.DefaultErrorDecoder;
import io.github.huynhngochuyhoang.httpstarter.core.ErrorResponseMapper;
import io.github.huynhngochuyhoang.httpstarter.core.MethodMetadataCache;
import io.github.huynhngochuyhoang.httpstarter.filter.CorrelationIdWebFilter;
import io.github.huynhngochuyhoang.httpstarter.filter.InboundHeadersWebFilter;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotProcessor;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.reactive.function.client.WebClientCustomizer;
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
 * <p>This class is ordered AFTER Micrometer's auto-configuration so that a
 * {@link MeterRegistry} bean is guaranteed to be present before the
 * {@link HttpClientObserver} condition is evaluated.
 */
@AutoConfiguration
@AutoConfigureAfter(name = {
        "org.springframework.boot.actuate.autoconfigure.metrics.MetricsAutoConfiguration",
        "org.springframework.boot.actuate.autoconfigure.metrics.CompositeMeterRegistryAutoConfiguration"
})
@EnableConfigurationProperties(ReactiveHttpClientProperties.class)
@ImportRuntimeHints(ReactiveHttpClientRuntimeHints.class)
public class ReactiveHttpClientAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ReactiveHttpClientAutoConfiguration.class);

    @Bean
    public static BeanFactoryInitializationAotProcessor reactiveHttpClientBeanFactoryInitializationAotProcessor() {
        return new ReactiveHttpClientBeanFactoryInitializationAotProcessor();
    }

    @Bean
    @Scope("prototype")
    @ConditionalOnMissingBean
    public WebClient.Builder starterWebClientBuilder(ObjectProvider<WebClientCustomizer> customizerProvider) {
        WebClient.Builder builder = WebClient.builder();
        customizerProvider.orderedStream().forEach(customizer -> {
            if (log.isDebugEnabled()) {
                log.debug("Applying WebClientCustomizer [{}] to starter WebClient.Builder",
                        customizer.getClass().getName());
            }
            customizer.customize(builder);
        });
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
    @Bean
    @ConditionalOnClass(MeterRegistry.class)
    @ConditionalOnBean(MeterRegistry.class)
    @ConditionalOnMissingBean(name = "micrometerHttpClientObserver")
    @ConditionalOnProperty(
            prefix = "reactive.http.observability",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    public MicrometerHttpClientObserver micrometerHttpClientObserver(MeterRegistry meterRegistry,
                                                                     ReactiveHttpClientProperties properties) {
        return new MicrometerHttpClientObserver(meterRegistry, properties.getObservability());
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
     * Registers {@link HttpClientHealthIndicator} when {@code spring-boot-actuator}
     * is on the classpath and a {@link MeterRegistry} bean is available. The
     * indicator reads the existing {@code reactive.http.client.requests} timer meters, so
     * no additional observation path is required and the user-override contract
     * on {@link HttpClientObserver} is preserved.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass({HealthIndicator.class, MeterRegistry.class})
    @ConditionalOnBean(MeterRegistry.class)
    @ConditionalOnProperty(
            prefix = "reactive.http.observability.health",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    static class HttpClientHealthIndicatorAutoConfiguration {

        @Bean
        @ConditionalOnMissingBean(name = "reactiveHttpClientHealthIndicator")
        public HttpClientHealthIndicator reactiveHttpClientHealthIndicator(
                MeterRegistry meterRegistry,
                ReactiveHttpClientProperties properties) {
            return new HttpClientHealthIndicator(meterRegistry, properties.getObservability());
        }
    }

}
