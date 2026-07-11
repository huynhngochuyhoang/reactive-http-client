package io.github.huynhngochuyhoang.httpstarter.otel;

import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import io.github.huynhngochuyhoang.httpstarter.core.ReactiveHttpClientCustomizer;
import io.opentelemetry.api.OpenTelemetry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Auto-configuration that registers an {@link OpenTelemetryHttpClientObserver}
 * when the OpenTelemetry API is on the classpath, an {@link OpenTelemetry}
 * bean is available in the context.
 *
 * <p>Activated under property {@code reactive.http.observability.otel.enabled}
 * (default {@code true} when the OTel API is present). Set to {@code false} to
 * disable all OTel observer and propagation beans without removing the dependency.
 * Use {@code reactive.http.observability.otel.spans.enabled=false} or
 * {@code reactive.http.observability.otel.propagation.enabled=false} to disable
 * either behavior independently while keeping the other one active.
 */
@AutoConfiguration
@ConditionalOnClass(OpenTelemetry.class)
@ConditionalOnBean(OpenTelemetry.class)
@ConditionalOnProperty(
        prefix = "reactive.http.observability.otel",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
@EnableConfigurationProperties(ReactiveHttpClientProperties.class)
public class OpenTelemetryHttpClientAutoConfiguration {

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(
            prefix = "reactive.http.observability.otel.spans",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    static class SpanConfiguration {

        @Bean(name = "openTelemetryHttpClientObserver")
        @ConditionalOnMissingBean(name = "openTelemetryHttpClientObserver")
        OpenTelemetryHttpClientObserver openTelemetryHttpClientObserver(
                OpenTelemetry openTelemetry,
                ReactiveHttpClientProperties properties) {
            return new OpenTelemetryHttpClientObserver(openTelemetry, properties.getObservability());
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(
            prefix = "reactive.http.observability.otel.propagation",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    static class PropagationConfiguration {

        /**
         * Server-side {@link OpenTelemetryContextWebFilter} that captures inbound
         * trace context + baggage into the Reactor {@link reactor.util.context.Context}.
         * Active only in reactive web applications.
         */
        @Bean
        @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
        @ConditionalOnMissingBean(OpenTelemetryContextWebFilter.class)
        OpenTelemetryContextWebFilter openTelemetryContextWebFilter(OpenTelemetry openTelemetry) {
            return new OpenTelemetryContextWebFilter(openTelemetry);
        }

        /**
         * Adds an outbound {@link OpenTelemetryContextExchangeFilter} to every
         * starter-built {@code WebClient} so the captured OTel context (or
         * {@link io.opentelemetry.context.Context#current()} as fallback) is
         * injected onto downstream requests as {@code traceparent} / {@code baggage}
         * / any other configured propagation headers.
         */
        @Bean(name = "openTelemetryContextWebClientCustomizer")
        @ConditionalOnMissingBean(name = "openTelemetryContextWebClientCustomizer")
        ReactiveHttpClientCustomizer openTelemetryContextWebClientCustomizer(OpenTelemetry openTelemetry) {
            return builder -> builder.filter(OpenTelemetryContextExchangeFilter.create(openTelemetry));
        }
    }
}
