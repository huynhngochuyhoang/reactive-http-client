package io.github.huynhngochuyhoang.httpstarter.otel;

import io.opentelemetry.api.OpenTelemetry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.webclient.WebClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        prefix = "reactive.http.observability.otel.propagation",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
class BootOpenTelemetryWebClientCustomizerConfiguration {

    @Bean(name = "openTelemetryContextWebClientCustomizer")
    @ConditionalOnMissingBean(name = "openTelemetryContextWebClientCustomizer")
    WebClientCustomizer openTelemetryContextWebClientCustomizer(OpenTelemetry openTelemetry) {
        return builder -> builder.filter(OpenTelemetryContextExchangeFilter.create(openTelemetry));
    }
}
