package io.github.huynhngochuyhoang.httpstarter.config;

import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientHealthIndicator;
import io.micrometer.core.instrument.MeterRegistry;

final class BootHealthIndicatorFactory {
    Object create(MeterRegistry meterRegistry, ReactiveHttpClientProperties properties) {
        return new HttpClientHealthIndicator(meterRegistry, properties.getObservability());
    }
}
