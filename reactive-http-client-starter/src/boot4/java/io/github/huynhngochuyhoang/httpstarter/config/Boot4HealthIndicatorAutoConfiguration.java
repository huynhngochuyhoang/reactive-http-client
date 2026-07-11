package io.github.huynhngochuyhoang.httpstarter.config;

import io.github.huynhngochuyhoang.httpstarter.observability.Boot4HttpClientHealthIndicator;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.health.contributor.HealthIndicator;
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
    Boot4HttpClientHealthIndicator reactiveHttpClientHealthIndicator(
            MeterRegistry meterRegistry,
            ReactiveHttpClientProperties properties) {
        return new Boot4HttpClientHealthIndicator(meterRegistry, properties.getObservability());
    }
}
