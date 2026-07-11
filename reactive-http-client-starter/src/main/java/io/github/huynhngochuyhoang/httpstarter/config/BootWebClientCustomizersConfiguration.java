package io.github.huynhngochuyhoang.httpstarter.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.web.reactive.function.client.WebClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class BootWebClientCustomizersConfiguration {
    @Bean
    BootWebClientCustomizers bootWebClientCustomizers(ObjectProvider<WebClientCustomizer> customizers) {
        return new Boot3WebClientCustomizers(customizers);
    }
}
