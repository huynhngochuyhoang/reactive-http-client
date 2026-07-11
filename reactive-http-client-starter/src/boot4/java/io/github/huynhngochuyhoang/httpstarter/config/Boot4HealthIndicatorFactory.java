package io.github.huynhngochuyhoang.httpstarter.config;

import io.github.huynhngochuyhoang.httpstarter.observability.Boot4HttpClientHealthIndicator;
import io.micrometer.core.instrument.MeterRegistry;

final class BootHealthIndicatorFactory {
    Object create(MeterRegistry meterRegistry, ReactiveHttpClientProperties properties) {
        return new Boot4HttpClientHealthIndicator(meterRegistry, properties.getObservability());
    }
}
