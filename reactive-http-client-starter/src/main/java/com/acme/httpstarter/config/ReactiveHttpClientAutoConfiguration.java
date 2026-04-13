package com.acme.httpstarter.config;

import com.acme.httpstarter.core.DefaultErrorDecoder;
import com.acme.httpstarter.core.MethodMetadataCache;
import com.acme.httpstarter.observability.HttpClientObserver;
import com.acme.httpstarter.observability.MicrometerHttpClientObserver;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Auto-configuration for the reactive HTTP client starter.
 * <p>
 * Registers core beans and exposes a customisable {@link WebClient.Builder}.
 * Individual client instances are created by
 * {@link com.acme.httpstarter.core.ReactiveHttpClientFactoryBean}.
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
public class ReactiveHttpClientAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public WebClient.Builder starterWebClientBuilder() {
        return WebClient.builder();
    }

    @Bean
    @ConditionalOnMissingBean
    public DefaultErrorDecoder defaultErrorDecoder() {
        return new DefaultErrorDecoder();
    }

    @Bean
    @ConditionalOnMissingBean
    public MethodMetadataCache methodMetadataCache() {
        return new MethodMetadataCache();
    }

    /**
     * Registers the Micrometer-backed {@link HttpClientObserver} automatically when:
     * <ul>
     *   <li>{@code micrometer-core} is on the classpath ({@link MeterRegistry} present)</li>
     *   <li>A {@link MeterRegistry} bean is available in the application context</li>
     *   <li>{@code acme.http.observability.enabled} is {@code true} (the default)</li>
     *   <li>No custom {@link HttpClientObserver} bean has been registered</li>
     * </ul>
     */
    @Bean
    @ConditionalOnClass(MeterRegistry.class)
    @ConditionalOnBean(MeterRegistry.class)
    @ConditionalOnMissingBean(HttpClientObserver.class)
    @ConditionalOnProperty(
            prefix = "acme.http.observability",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    public HttpClientObserver micrometerHttpClientObserver(MeterRegistry meterRegistry,
                                                           ReactiveHttpClientProperties properties) {
        return new MicrometerHttpClientObserver(meterRegistry, properties.getObservability());
    }
}
