package io.github.huynhngochuyhoang.httpstarter.observability;

import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import io.github.huynhngochuyhoang.httpstarter.exception.ErrorCategory;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.distribution.CountAtBucket;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.netty.handler.timeout.ReadTimeoutException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

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

        Timer timer = meterRegistry.find("reactive.http.client.requests")
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

        Timer timer = meterRegistry.find("reactive.http.client.requests")
                .tag("error.category", "none")
                .timer();
        assertNotNull(timer);
        assertEquals(1, timer.count(), 0.0d);
    }

    @Test
    void shouldRecordRedirectionOutcomeForVisibleRedirect() {
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
                302,
                8,
                null,
                null,
                null,
                null
        ));

        Timer timer = meterRegistry.find("reactive.http.client.requests")
                .tag("outcome", "REDIRECTION")
                .timer();
        assertNotNull(timer);
        assertEquals(1, timer.count(), 0.0d);
    }

    @ParameterizedTest
    @EnumSource(ErrorCategory.class)
    void shouldUsePublishedErrorCategoryNamesAsMetricTagValues(ErrorCategory category) {
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
                new RuntimeException(category.name()),
                category,
                null,
                null
        ));

        Timer timer = meterRegistry.find("reactive.http.client.requests")
                .tag("error.category", category.name())
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

        Timer timer = meterRegistry.find("reactive.http.client.requests")
                .tag("http.status_code", "NONE")
                .timer();
        assertNotNull(timer);
        assertEquals(1, timer.count(), 0.0d);
    }

    @Test
    void shouldDefaultClientNameTagToUnknownWhenNull() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        MicrometerHttpClientObserver observer = new MicrometerHttpClientObserver(
                meterRegistry,
                new ReactiveHttpClientProperties.ObservabilityConfig()
        );

        observer.record(new HttpClientObserverEvent(
                null,
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

        Timer timer = meterRegistry.find("reactive.http.client.requests")
                .tag("client.name", "UNKNOWN")
                .timer();
        assertNotNull(timer);
        assertEquals(1, timer.count(), 0.0d);
    }

    @Test
    void skipsServerAddressTagsByDefault() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        MicrometerHttpClientObserver observer = new MicrometerHttpClientObserver(
                meterRegistry,
                new ReactiveHttpClientProperties.ObservabilityConfig()
        );

        observer.record(new HttpClientObserverEvent(
                "user-service", "user.get", "GET", "/users/{id}",
                200, 8, null, null, null, "ok",
                1, 0L, 2L, "api.example.com", 443
        ));

        Timer timer = meterRegistry.find("reactive.http.client.requests").timer();
        assertNotNull(timer);
        assertNull(timer.getId().getTag("server.address"));
        assertNull(timer.getId().getTag("server.port"));
    }

    @Test
    void usesNoneUriTagByDefault() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        MicrometerHttpClientObserver observer = new MicrometerHttpClientObserver(
                meterRegistry,
                new ReactiveHttpClientProperties.ObservabilityConfig()
        );

        observer.record(new HttpClientObserverEvent(
                "user-service", "user.get", "GET", "/users/{id}",
                200, 8, null, null, null, "ok"
        ));

        Timer timer = meterRegistry.find("reactive.http.client.requests")
                .tag("uri", "NONE")
                .timer();
        assertNotNull(timer);
        assertEquals(1, timer.count(), 0.0d);
    }

    @Test
    void recordsUriTagWhenOptedIn() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        ReactiveHttpClientProperties.ObservabilityConfig config =
                new ReactiveHttpClientProperties.ObservabilityConfig();
        config.setIncludeUrlPath(true);
        MicrometerHttpClientObserver observer = new MicrometerHttpClientObserver(meterRegistry, config);

        observer.record(new HttpClientObserverEvent(
                "user-service", "user.get", "GET", "/users/{id}",
                200, 8, null, null, null, "ok"
        ));

        Timer timer = meterRegistry.find("reactive.http.client.requests")
                .tag("uri", "/users/{id}")
                .timer();
        assertNotNull(timer);
        assertEquals(1, timer.count(), 0.0d);
    }

    @Test
    void recordsServerAddressTagsWhenOptedIn() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        ReactiveHttpClientProperties.ObservabilityConfig config =
                new ReactiveHttpClientProperties.ObservabilityConfig();
        config.setIncludeServerAddress(true);
        MicrometerHttpClientObserver observer = new MicrometerHttpClientObserver(meterRegistry, config);

        observer.record(new HttpClientObserverEvent(
                "user-service", "user.get", "GET", "/users/{id}",
                200, 8, null, null, null, "ok",
                1, 0L, 2L, "api.example.com", 443
        ));

        Timer timer = meterRegistry.find("reactive.http.client.requests")
                .tag("server.address", "api.example.com")
                .tag("server.port", "443")
                .timer();
        assertNotNull(timer);
        assertEquals(1, timer.count(), 0.0d);
    }

    @Test
    void recordsRequestAndResponseSizeDistributionsWhenSizesAreKnown() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        MicrometerHttpClientObserver observer = new MicrometerHttpClientObserver(
                meterRegistry,
                new ReactiveHttpClientProperties.ObservabilityConfig()
        );

        observer.record(new HttpClientObserverEvent(
                "user-service", "user.get", "POST", "/users",
                201, 5, null, null, null, null,
                1, 128L, 256L
        ));

        DistributionSummary requestSize = meterRegistry.find("reactive.http.client.requests.request.size")
                .tag("client.name", "user-service")
                .summary();
        DistributionSummary responseSize = meterRegistry.find("reactive.http.client.requests.response.size")
                .tag("client.name", "user-service")
                .summary();

        assertNotNull(requestSize);
        assertEquals(1, requestSize.count());
        assertEquals(128.0d, requestSize.totalAmount(), 0.0d);

        assertNotNull(responseSize);
        assertEquals(1, responseSize.count());
        assertEquals(256.0d, responseSize.totalAmount(), 0.0d);
    }

    @Test
    void skipsSizeDistributionsWhenSizesAreUnknown() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        MicrometerHttpClientObserver observer = new MicrometerHttpClientObserver(
                meterRegistry,
                new ReactiveHttpClientProperties.ObservabilityConfig()
        );

        observer.record(new HttpClientObserverEvent(
                "user-service", "user.get", "GET", "/users/{id}",
                200, 5, null, null, null, null,
                1, HttpClientObserverEvent.UNKNOWN_SIZE, HttpClientObserverEvent.UNKNOWN_SIZE
        ));

        assertNull(meterRegistry.find("reactive.http.client.requests.request.size").summary(),
                "unknown request size must not create a distribution summary meter");
        assertNull(meterRegistry.find("reactive.http.client.requests.response.size").summary(),
                "unknown response size must not create a distribution summary meter");
    }

    @Test
    void recordsZeroSizedRequestBodyExplicitly() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        MicrometerHttpClientObserver observer = new MicrometerHttpClientObserver(
                meterRegistry,
                new ReactiveHttpClientProperties.ObservabilityConfig()
        );

        observer.record(new HttpClientObserverEvent(
                "user-service", "user.get", "GET", "/users/{id}",
                200, 5, null, null, null, null,
                1, 0L, 0L
        ));

        DistributionSummary requestSize = meterRegistry.find("reactive.http.client.requests.request.size").summary();
        DistributionSummary responseSize = meterRegistry.find("reactive.http.client.requests.response.size").summary();

        assertNotNull(requestSize);
        assertEquals(1, requestSize.count(),
                "null / empty bodies must still be recorded as 0 bytes, not dropped");
        assertNotNull(responseSize);
        assertEquals(1, responseSize.count());
    }

    // ---- histogram tests ----

    @Test
    void histogramNotRecordedWhenDisabled() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        ReactiveHttpClientProperties.ObservabilityConfig config =
                new ReactiveHttpClientProperties.ObservabilityConfig();
        // histogram.enabled defaults to false

        MicrometerHttpClientObserver observer = new MicrometerHttpClientObserver(meterRegistry, config);

        observer.record(new HttpClientObserverEvent(
                "user-service", "user.get", "GET", "/users/{id}",
                200, 50, null, null, null, null
        ));

        assertNull(meterRegistry.find("reactive.http.client.requests.latency").timer(),
                "no latency histogram should be registered when histogram is disabled");
    }

    @Test
    void histogramRecordedWhenEnabled() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        ReactiveHttpClientProperties.ObservabilityConfig config =
                new ReactiveHttpClientProperties.ObservabilityConfig();
        config.getHistogram().setEnabled(true);
        config.setIncludeUrlPath(true);

        MicrometerHttpClientObserver observer = new MicrometerHttpClientObserver(meterRegistry, config);

        observer.record(new HttpClientObserverEvent(
                "user-service", "user.get", "GET", "/users/{id}",
                200, 75, null, null, null, null
        ));

        Timer histogramTimer = meterRegistry.find("reactive.http.client.requests.latency")
                .tag("client.name", "user-service")
                .tag("api.name", "user.get")
                .tag("http.method", "GET")
                .tag("uri", "/users/{id}")
                .timer();
        assertNotNull(histogramTimer, "latency histogram timer must be registered when enabled");
        assertEquals(1, histogramTimer.count());
    }

    @Test
    void histogramUsesOnlyLowCardinalityTags() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        ReactiveHttpClientProperties.ObservabilityConfig config =
                new ReactiveHttpClientProperties.ObservabilityConfig();
        config.getHistogram().setEnabled(true);

        MicrometerHttpClientObserver observer = new MicrometerHttpClientObserver(meterRegistry, config);

        observer.record(new HttpClientObserverEvent(
                "svc", "api.op", "POST", "/items",
                500, 120, new RuntimeException("boom"), ErrorCategory.SERVER_ERROR, null, null
        ));

        // Histogram timer must exist with only the 4 low-cardinality tags
        Timer histogramTimer = meterRegistry.find("reactive.http.client.requests.latency")
                .tag("client.name", "svc")
                .tag("api.name", "api.op")
                .tag("http.method", "POST")
                .tag("uri", "NONE")
                .timer();
        assertNotNull(histogramTimer, "histogram timer must be present");

        // The histogram timer must NOT carry high-cardinality tags
        Meter.Id id = histogramTimer.getId();
        assertNull(id.getTag("http.status_code"), "histogram must not have http.status_code tag");
        assertNull(id.getTag("outcome"),           "histogram must not have outcome tag");
        assertNull(id.getTag("exception"),         "histogram must not have exception tag");
        assertNull(id.getTag("error.category"),    "histogram must not have error.category tag");
    }

    @Test
    void histogramDefaultSloBoundariesAreUsedWhenNotConfigured() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        ReactiveHttpClientProperties.ObservabilityConfig config =
                new ReactiveHttpClientProperties.ObservabilityConfig();
        config.getHistogram().setEnabled(true);
        // slo-boundaries-ms left at default: [50, 100, 200, 500, 1000, 2000, 5000]

        MicrometerHttpClientObserver observer = new MicrometerHttpClientObserver(meterRegistry, config);

        observer.record(new HttpClientObserverEvent(
                "svc", "api.op", "GET", "/ping",
                200, 75, null, null, null, null
        ));

        Timer histogramTimer = meterRegistry.find("reactive.http.client.requests.latency").timer();
        assertNotNull(histogramTimer, "latency histogram must be present");

        CountAtBucket[] buckets = histogramTimer.takeSnapshot().histogramCounts();
        assertEquals(7, buckets.length, "expected 7 default SLO histogram buckets");

        // Boundaries are stored in nanoseconds (50ms = 50_000_000 ns)
        double[] expectedNanos = {
            50_000_000.0, 100_000_000.0, 200_000_000.0, 500_000_000.0,
            1_000_000_000.0, 2_000_000_000.0, 5_000_000_000.0
        };
        for (int i = 0; i < expectedNanos.length; i++) {
            assertEquals(expectedNanos[i], buckets[i].bucket(), 0.0,
                    "bucket[" + i + "] boundary should be " + (long)(expectedNanos[i] / 1_000_000) + "ms");
        }
        // 75ms falls in the 100ms bucket (cumulative count = 1); not in the 50ms bucket (count = 0)
        assertEquals(0.0, buckets[0].count(), 0.0, "75ms should not be counted in the 50ms bucket");
        assertEquals(1.0, buckets[1].count(), 0.0, "75ms should be counted in the 100ms bucket");
    }

    @Test
    void histogramUsesCustomSloBoundaries() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        ReactiveHttpClientProperties.ObservabilityConfig config =
                new ReactiveHttpClientProperties.ObservabilityConfig();
        config.getHistogram().setEnabled(true);
        config.getHistogram().setSloBoundariesMs(Arrays.asList(10L, 25L, 100L));

        MicrometerHttpClientObserver observer = new MicrometerHttpClientObserver(meterRegistry, config);

        observer.record(new HttpClientObserverEvent(
                "svc", "api.op", "GET", "/ping",
                200, 15, null, null, null, null
        ));

        Timer histogramTimer = meterRegistry.find("reactive.http.client.requests.latency").timer();
        assertNotNull(histogramTimer, "latency histogram must be present with custom SLO buckets");
        assertEquals(1, histogramTimer.count());

        CountAtBucket[] buckets = histogramTimer.takeSnapshot().histogramCounts();
        assertEquals(3, buckets.length, "expected 3 custom SLO histogram buckets");
        assertEquals(10_000_000.0,  buckets[0].bucket(), 0.0, "first bucket should be 10ms");
        assertEquals(25_000_000.0,  buckets[1].bucket(), 0.0, "second bucket should be 25ms");
        assertEquals(100_000_000.0, buckets[2].bucket(), 0.0, "third bucket should be 100ms");
        // 15ms falls in the 25ms bucket (cumulative count = 1); not in the 10ms bucket (count = 0)
        assertEquals(0.0, buckets[0].count(), 0.0, "15ms should not be counted in the 10ms bucket");
        assertEquals(1.0, buckets[1].count(), 0.0, "15ms should be counted in the 25ms bucket");
    }

    @Test
    void histogramDoesNotAffectMainTimer() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        ReactiveHttpClientProperties.ObservabilityConfig config =
                new ReactiveHttpClientProperties.ObservabilityConfig();
        config.getHistogram().setEnabled(true);

        MicrometerHttpClientObserver observer = new MicrometerHttpClientObserver(meterRegistry, config);

        observer.record(new HttpClientObserverEvent(
                "svc", "api.op", "GET", "/health",
                200, 30, null, null, null, null
        ));

        // Main timer must still carry full tag set
        Timer mainTimer = meterRegistry.find("reactive.http.client.requests")
                .tag("http.status_code", "200")
                .tag("outcome", "SUCCESS")
                .tag("exception", "none")
                .tag("error.category", "none")
                .timer();
        assertNotNull(mainTimer, "main timer must still carry high-cardinality tags when histogram is enabled");
        assertEquals(1, mainTimer.count());
    }

    @Test
    void recordsAdditiveDnsFailureStageAsLowCardinalityTag() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        MicrometerHttpClientObserver observer = new MicrometerHttpClientObserver(
                meterRegistry, new ReactiveHttpClientProperties.ObservabilityConfig());

        observer.record(new HttpClientObserverEvent(
                "user-service", "user.get", "GET", "/users/{id}",
                null, 25, new UnknownHostException("missing.invalid"),
                ErrorCategory.UNKNOWN_HOST, null, null));

        Timer timer = meterRegistry.find("reactive.http.client.requests")
                .tag("failure.stage", HttpClientFailureStage.DNS_RESOLUTION.name())
                .timer();
        assertNotNull(timer);
        assertEquals(1, timer.count());
    }

    @Test
    void recordsOnlyProvenPoolAcquireFailureStageWithoutServerTags() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        MicrometerHttpClientObserver observer = new MicrometerHttpClientObserver(
                meterRegistry, new ReactiveHttpClientProperties.ObservabilityConfig());

        observer.record(new HttpClientObserverEvent(
                "user-service", "user.get", "GET", "/users/{id}",
                null, 75, poolAcquireTimeout(), ErrorCategory.TIMEOUT, null, null,
                1, HttpClientObserverEvent.UNKNOWN_SIZE, HttpClientObserverEvent.UNKNOWN_SIZE,
                null, null, "http://user-service/users/1", Map.of()));

        Timer timer = meterRegistry.find("reactive.http.client.requests")
                .tag("failure.stage", HttpClientFailureStage.POOL_ACQUIRE.name())
                .timer();
        assertNotNull(timer);
        assertNull(timer.getId().getTag("server.address"));
        assertNull(timer.getId().getTag("server.port"));
    }

    @Test
    void recordsResponseBodyFailureStageFromObservedStatus() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        MicrometerHttpClientObserver observer = new MicrometerHttpClientObserver(
                meterRegistry, new ReactiveHttpClientProperties.ObservabilityConfig());

        observer.record(new HttpClientObserverEvent(
                "user-service", "user.get", "GET", "/users/{id}",
                200, 75, ReadTimeoutException.INSTANCE, ErrorCategory.TIMEOUT, null, null));

        Timer timer = meterRegistry.find("reactive.http.client.requests")
                .tag("failure.stage", HttpClientFailureStage.RESPONSE_BODY.name())
                .timer();
        assertNotNull(timer);
    }

    private static Throwable poolAcquireTimeout() {
        try {
            Class<?> type = Class.forName(
                    "reactor.netty.internal.shaded.reactor.pool.PoolAcquireTimeoutException");
            return (Throwable) type.getConstructor(java.time.Duration.class)
                    .newInstance(java.time.Duration.ofMillis(75));
        }
        catch (ReflectiveOperationException ex) {
            throw new AssertionError(ex);
        }
    }
}
