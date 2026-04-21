package io.github.huynhngochuyhoang.httpstarter.observability;

import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import io.github.huynhngochuyhoang.httpstarter.exception.ErrorCategory;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class MicrometerHttpClientObserverTest {

    @Test
    void shouldRecordErrorCategoryTagWhenPresent() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        MicrometerHttpClientObserver observer = new MicrometerHttpClientObserver(
                meterRegistry,
                new ReactiveHttpClientProperties.ObservabilityConfig()
        );

        observer.record(new HttpClientObserverEvent(
                "user-service",
                "user.get",
                "GET",
                "/users/{id}",
                429,
                12,
                new RuntimeException("rate-limited"),
                ErrorCategory.RATE_LIMITED,
                null,
                null
        ));

        Timer timer = meterRegistry.find("http.client.requests")
                .tag("error.category", "RATE_LIMITED")
                .timer();
        assertNotNull(timer);
        assertEquals(1, timer.count(), 0.0d);
    }

    @Test
    void shouldRecordNoneErrorCategoryForSuccess() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        MicrometerHttpClientObserver observer = new MicrometerHttpClientObserver(
                meterRegistry,
                new ReactiveHttpClientProperties.ObservabilityConfig()
        );

        observer.record(new HttpClientObserverEvent(
                "user-service",
                "user.get",
                "GET",
                "/users/{id}",
                200,
                8,
                null,
                null,
                null,
                "ok"
        ));

        Timer timer = meterRegistry.find("http.client.requests")
                .tag("error.category", "none")
                .timer();
        assertNotNull(timer);
        assertEquals(1, timer.count(), 0.0d);
    }

    @Test
    void shouldUseNoneHttpStatusCodeWhenRequestFailsBeforeResponse() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        MicrometerHttpClientObserver observer = new MicrometerHttpClientObserver(
                meterRegistry,
                new ReactiveHttpClientProperties.ObservabilityConfig()
        );

        observer.record(new HttpClientObserverEvent(
                "user-service",
                "user.get",
                "GET",
                "/users/{id}",
                null,
                15,
                new RuntimeException("connect failed"),
                ErrorCategory.CONNECT_ERROR,
                null,
                null
        ));

        Timer timer = meterRegistry.find("http.client.requests")
                .tag("http.status_code", "NONE")
                .timer();
        assertNotNull(timer);
        assertEquals(1, timer.count(), 0.0d);
    }
}
