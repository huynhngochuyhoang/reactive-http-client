package com.acme.httpstarter.config;

import com.acme.httpstarter.core.DefaultErrorDecoder;
import com.acme.httpstarter.core.MethodMetadataCache;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Auto-configuration for the reactive HTTP client starter.
 * <p>
 * Registers core beans and exposes a customisable {@link WebClient.Builder}.
 * Individual client instances are created by
 * {@link com.acme.httpstarter.core.ReactiveHttpClientFactoryBean}.
 */
@AutoConfiguration
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
}
