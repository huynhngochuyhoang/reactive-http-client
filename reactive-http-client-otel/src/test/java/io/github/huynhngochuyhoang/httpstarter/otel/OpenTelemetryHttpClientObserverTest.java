package io.github.huynhngochuyhoang.httpstarter.otel;

import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import io.github.huynhngochuyhoang.httpstarter.exception.AuthProviderException;
import io.github.huynhngochuyhoang.httpstarter.exception.ErrorCategory;
import io.github.huynhngochuyhoang.httpstarter.observability.CompositeHttpClientObserver;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientObserverEvent;
import io.github.huynhngochuyhoang.httpstarter.observability.MicrometerHttpClientObserver;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.netty.handler.timeout.ReadTimeoutException;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link OpenTelemetryHttpClientObserver} using the OTel
 * SDK's in-memory exporter to capture finished spans.
 */
class OpenTelemetryHttpClientObserverTest {

    private InMemorySpanExporter exporter;
    private SdkTracerProvider tracerProvider;
    private OpenTelemetryHttpClientObserver observer;

    @BeforeEach
    void setUp() {
        exporter = InMemorySpanExporter.create();
        tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .build();
        OpenTelemetry openTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .build();
        observer = new OpenTelemetryHttpClientObserver(openTelemetry);
    }

    @AfterEach
    void tearDown() {
        tracerProvider.close();
    }

    @Test
    void successfulExchangeProducesClientSpanWithConservativeAttributesByDefault() {
        observer.record(new HttpClientObserverEvent(
                "user-service", "user.get", "GET", "/users/{id}",
                200, 12L, null, null, null, null,
                1, 0L, 256L, "api.example.com", 443
        ));

        SpanData span = onlySpan();
        assertThat(span.getName()).isEqualTo("GET user.get");
        assertThat(span.getKind().name()).isEqualTo("CLIENT");
        assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusData.unset().getStatusCode());

        assertThat(span.getAttributes().get(OpenTelemetryHttpClientObserver.ATTR_HTTP_METHOD)).isEqualTo("GET");
        assertThat(span.getAttributes().get(OpenTelemetryHttpClientObserver.ATTR_HTTP_STATUS_CODE)).isEqualTo(200L);
        assertThat(span.getAttributes().get(OpenTelemetryHttpClientObserver.ATTR_SERVER_ADDRESS)).isNull();
        assertThat(span.getAttributes().get(OpenTelemetryHttpClientObserver.ATTR_SERVER_PORT)).isNull();
        assertThat(span.getAttributes().get(OpenTelemetryHttpClientObserver.ATTR_URL_TEMPLATE)).isNull();
        assertThat(span.getAttributes().get(OpenTelemetryHttpClientObserver.ATTR_CLIENT_NAME)).isEqualTo("user-service");
        assertThat(span.getAttributes().get(OpenTelemetryHttpClientObserver.ATTR_API_NAME)).isEqualTo("user.get");
        assertThat(span.getAttributes().get(OpenTelemetryHttpClientObserver.ATTR_ATTEMPT_COUNT)).isEqualTo(1L);
        assertThat(span.getAttributes().get(OpenTelemetryHttpClientObserver.ATTR_RESPONSE_BYTES)).isEqualTo(256L);
    }

    @Test
    void urlTemplateAndServerAttributesAreRecordedWhenOptedIn() {
        ReactiveHttpClientProperties.ObservabilityConfig config =
                new ReactiveHttpClientProperties.ObservabilityConfig();
        config.setIncludeUrlPath(true);
        config.setIncludeServerAddress(true);
        observer = new OpenTelemetryHttpClientObserver(OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .build(), config);

        observer.record(new HttpClientObserverEvent(
                "user-service", "user.get", "GET", "/users/{id}",
                200, 12L, null, null, null, null,
                1, 0L, 256L, "api.example.com", 443
        ));

        SpanData span = onlySpan();
        assertThat(span.getAttributes().get(OpenTelemetryHttpClientObserver.ATTR_URL_TEMPLATE)).isEqualTo("/users/{id}");
        assertThat(span.getAttributes().get(OpenTelemetryHttpClientObserver.ATTR_SERVER_ADDRESS)).isEqualTo("api.example.com");
        assertThat(span.getAttributes().get(OpenTelemetryHttpClientObserver.ATTR_SERVER_PORT)).isEqualTo(443L);
    }

    @Test
    void compositeMicrometerAndOpenTelemetryObserversRecordOneExchange() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        CompositeHttpClientObserver composite = new CompositeHttpClientObserver(List.of(
                new MicrometerHttpClientObserver(meterRegistry, new ReactiveHttpClientProperties.ObservabilityConfig()),
                observer
        ));

        composite.record(new HttpClientObserverEvent(
                "user-service", "user.get", "GET", "/users/{id}",
                200, 12L, null, null, null, null,
                1, 0L, 256L, "api.example.com", 443
        ));

        Timer timer = meterRegistry.find("reactive.http.client.requests")
                .tag("client.name", "user-service")
                .timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(onlySpan().getKind().name()).isEqualTo("CLIENT");
    }

    @Test
    void errorEventTaggedWithErrorTypeAndStatusError() {
        observer.record(new HttpClientObserverEvent(
                "user-service", "user.get", "GET", "/users/{id}",
                429, 8L,
                new RuntimeException("rate limited"),
                ErrorCategory.RATE_LIMITED,
                null, null,
                3, HttpClientObserverEvent.UNKNOWN_SIZE, HttpClientObserverEvent.UNKNOWN_SIZE
        ));

        SpanData span = onlySpan();
        assertThat(span.getStatus().getStatusCode().name()).isEqualTo("ERROR");
        assertThat(span.getAttributes().get(OpenTelemetryHttpClientObserver.ATTR_ERROR_TYPE))
                .isEqualTo("RATE_LIMITED");
        assertThat(span.getAttributes().get(OpenTelemetryHttpClientObserver.ATTR_ATTEMPT_COUNT)).isEqualTo(3L);

        assertThat(span.getStatus().getDescription()).isEmpty();
        assertThat(span.getEvents()).singleElement().satisfies(exception -> {
            assertThat(exception.getName()).isEqualTo("exception");
            assertThat(exception.getAttributes().get(OpenTelemetryHttpClientObserver.ATTR_EXCEPTION_TYPE))
                    .isEqualTo(RuntimeException.class.getName());
            assertThat(exception.getAttributes().asMap().keySet())
                    .containsExactly(OpenTelemetryHttpClientObserver.ATTR_EXCEPTION_TYPE);
            assertThat(exception.getAttributes().toString()).doesNotContain("rate limited");
        });
    }

    @Test
    void composedPreDispatchAuthFailureExportsOnlyTerminalStructuralFacts() {
        String secret = "Bearer secret-token-" + "x".repeat(10_000);
        AuthProviderException failure = new AuthProviderException(
                "replay-composition", new IllegalStateException(secret));

        observer.record(new HttpClientObserverEvent(
                "replay-composition", "terminalAuthFailure", "POST", "/terminal-auth",
                null, 75L, failure, ErrorCategory.AUTH_PROVIDER_ERROR, null, null,
                2, HttpClientObserverEvent.UNKNOWN_SIZE, HttpClientObserverEvent.UNKNOWN_SIZE,
                null, null, null, Map.of()));

        SpanData span = onlySpan();
        assertThat(span.getName()).isEqualTo("POST terminalAuthFailure");
        assertThat(span.getStatus()).isEqualTo(StatusData.error());
        assertThat(span.getAttributes().get(OpenTelemetryHttpClientObserver.ATTR_CLIENT_NAME))
                .isEqualTo("replay-composition");
        assertThat(span.getAttributes().get(OpenTelemetryHttpClientObserver.ATTR_HTTP_METHOD))
                .isEqualTo("POST");
        assertThat(span.getAttributes().get(OpenTelemetryHttpClientObserver.ATTR_HTTP_STATUS_CODE)).isNull();
        assertThat(span.getAttributes().get(OpenTelemetryHttpClientObserver.ATTR_ERROR_TYPE))
                .isEqualTo(ErrorCategory.AUTH_PROVIDER_ERROR.name());
        assertThat(span.getAttributes().get(OpenTelemetryHttpClientObserver.ATTR_FAILURE_STAGE)).isNull();
        assertThat(span.getAttributes().get(OpenTelemetryHttpClientObserver.ATTR_ATTEMPT_COUNT)).isEqualTo(2L);
        assertThat(span.getEvents()).singleElement().satisfies(exception -> assertThat(
                exception.getAttributes().get(OpenTelemetryHttpClientObserver.ATTR_EXCEPTION_TYPE))
                .isEqualTo(AuthProviderException.class.getName()));
        assertThat(span.toString()).doesNotContain("secret-token", secret);
    }

    @Test
    void networkFailureBeforeResponseHasNoStatusCodeAndUsesExceptionClassWhenCategoryUnset() {
        observer.record(new HttpClientObserverEvent(
                "user-service", "user.get", "GET", "/users/{id}",
                null, 5L,
                new java.net.ConnectException("refused"),
                null, // no error category supplied
                null, null,
                1, HttpClientObserverEvent.UNKNOWN_SIZE, HttpClientObserverEvent.UNKNOWN_SIZE
        ));

        SpanData span = onlySpan();
        assertThat(span.getAttributes().get(OpenTelemetryHttpClientObserver.ATTR_HTTP_STATUS_CODE)).isNull();
        assertThat(span.getAttributes().get(OpenTelemetryHttpClientObserver.ATTR_ERROR_TYPE))
                .as("when ErrorCategory is null, fall back to the exception's simple name")
                .isEqualTo("ConnectException");
        assertThat(span.getStatus().getStatusCode().name()).isEqualTo("ERROR");
    }

    @ParameterizedTest
    @EnumSource(ErrorCategory.class)
    void errorCategoryNamesBecomeOpenTelemetryErrorTypeValues(ErrorCategory category) {
        observer.record(new HttpClientObserverEvent(
                "user-service", "user.get", "GET", "/users/{id}",
                null, 5L,
                new RuntimeException(category.name()),
                category,
                null, null,
                1, HttpClientObserverEvent.UNKNOWN_SIZE, HttpClientObserverEvent.UNKNOWN_SIZE
        ));

        SpanData span = onlySpan();
        assertThat(span.getAttributes().get(OpenTelemetryHttpClientObserver.ATTR_ERROR_TYPE))
                .isEqualTo(category.name());
    }

    @Test
    void unknownSizesAreOmittedFromSpanAttributes() {
        observer.record(new HttpClientObserverEvent(
                "user-service", "user.get", "GET", "/u",
                200, 5L, null, null, null, null,
                1, HttpClientObserverEvent.UNKNOWN_SIZE, HttpClientObserverEvent.UNKNOWN_SIZE
        ));

        SpanData span = onlySpan();
        assertThat(span.getAttributes().get(OpenTelemetryHttpClientObserver.ATTR_REQUEST_BYTES)).isNull();
        assertThat(span.getAttributes().get(OpenTelemetryHttpClientObserver.ATTR_RESPONSE_BYTES)).isNull();
    }

    @Test
    void missingMethodAndApiNameProduceLowCardinalityFallbacks() {
        observer.record(new HttpClientObserverEvent(
                null, null, null, null,
                200, 1L, null, null, null, null,
                1, HttpClientObserverEvent.UNKNOWN_SIZE, HttpClientObserverEvent.UNKNOWN_SIZE
        ));

        SpanData span = onlySpan();
        assertThat(span.getName()).isEqualTo("HTTP request");
        assertThat(span.getAttributes().get(OpenTelemetryHttpClientObserver.ATTR_HTTP_METHOD)).isEqualTo("UNKNOWN");
        assertThat(span.getAttributes().get(OpenTelemetryHttpClientObserver.ATTR_CLIENT_NAME)).isEqualTo("UNKNOWN");
        assertThat(span.getAttributes().get(OpenTelemetryHttpClientObserver.ATTR_API_NAME)).isEqualTo("UNKNOWN");
    }

    @Test
    void spanDurationReflectsEventDurationMs() {
        observer.record(new HttpClientObserverEvent(
                "user-service", "user.get", "GET", "/u",
                200, 1500L, null, null, null, null,
                1, HttpClientObserverEvent.UNKNOWN_SIZE, HttpClientObserverEvent.UNKNOWN_SIZE
        ));

        SpanData span = onlySpan();
        long durationNs = span.getEndEpochNanos() - span.getStartEpochNanos();
        assertThat(java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(durationNs))
                .as("span duration must match event.durationMs (within 1 ms tolerance for clock granularity)")
                .isBetween(1499L, 1501L);
    }

    @Test
    void recordsAdditiveDnsFailureStage() {
        observer.record(new HttpClientObserverEvent(
                "user-service", "user.get", "GET", "/users/{id}",
                null, 25L, new UnknownHostException("missing.invalid"),
                ErrorCategory.UNKNOWN_HOST, null, null));

        SpanData span = onlySpan();
        assertThat(span.getAttributes().get(OpenTelemetryHttpClientObserver.ATTR_FAILURE_STAGE))
                .isEqualTo("DNS_RESOLUTION");
    }

    @Test
    void recordsProvenPoolAcquireFailureStage() {
        observer.record(new HttpClientObserverEvent(
                "user-service", "user.get", "GET", "/users/{id}",
                null, 75L, poolAcquireTimeout(), ErrorCategory.TIMEOUT, null, null,
                1, HttpClientObserverEvent.UNKNOWN_SIZE, HttpClientObserverEvent.UNKNOWN_SIZE,
                null, null, "http://user-service/users/1", Map.of()));

        SpanData span = onlySpan();
        assertThat(span.getAttributes().get(OpenTelemetryHttpClientObserver.ATTR_FAILURE_STAGE))
                .isEqualTo("POOL_ACQUIRE");
        assertThat(span.getAttributes().get(OpenTelemetryHttpClientObserver.ATTR_SERVER_ADDRESS)).isNull();
    }

    @Test
    void recordsResponseBodyFailureStageFromObservedStatus() {
        observer.record(new HttpClientObserverEvent(
                "user-service", "user.get", "GET", "/users/{id}",
                200, 75L, ReadTimeoutException.INSTANCE, ErrorCategory.TIMEOUT, null, null));

        SpanData span = onlySpan();
        assertThat(span.getAttributes().get(OpenTelemetryHttpClientObserver.ATTR_FAILURE_STAGE))
                .isEqualTo("RESPONSE_BODY");
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

    private SpanData onlySpan() {
        List<SpanData> spans = exporter.getFinishedSpanItems();
        assertThat(spans).hasSize(1);
        return spans.get(0);
    }
}
