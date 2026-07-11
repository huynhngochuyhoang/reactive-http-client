package io.github.huynhngochuyhoang.httpstarter.config;

import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientHealthIndicator;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnClass({HealthIndicator.class, MeterRegistry.class})
@ConditionalOnBean(MeterRegistry.class)
@ConditionalOnProperty(
        prefix = "reactive.http.observability.health",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
class BootHealthIndicatorAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "reactiveHttpClientHealthIndicator")
    HttpClientHealthIndicator reactiveHttpClientHealthIndicator(
            MeterRegistry meterRegistry,
            ReactiveHttpClientProperties properties) {
        return new HttpClientHealthIndicator(meterRegistry, properties.getObservability());
    }
}
