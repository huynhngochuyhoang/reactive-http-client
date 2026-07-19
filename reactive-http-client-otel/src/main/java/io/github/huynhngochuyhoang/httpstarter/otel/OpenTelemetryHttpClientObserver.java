package io.github.huynhngochuyhoang.httpstarter.otel;

import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientObserver;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientObserverEvent;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * {@link HttpClientObserver} that records each outbound HTTP exchange as an
 * OpenTelemetry span using the
 * <a href="https://opentelemetry.io/docs/specs/semconv/http/http-spans/">HTTP
 * client semantic conventions</a>.
 *
 * <h3>Span shape</h3>
 * <ul>
 *   <li><b>Name:</b> {@code <METHOD> <api.name>} — e.g. {@code GET getUserById}.
 *       Matches OTel's recommendation that span names be low-cardinality.</li>
 *   <li><b>Kind:</b> {@link SpanKind#CLIENT}.</li>
 *   <li><b>Timestamps:</b> derived from the event — start = {@code now − durationMs},
 *       end = {@code now}. The starter only fires this observer on completion,
 *       so the span exists strictly to anchor metadata; consumers are expected
 *       to combine with their own server-side spans for full trace context.</li>
 * </ul>
 *
 * <h3>Standard attributes</h3>
 * <ul>
 *   <li>{@code http.request.method}</li>
 *   <li>{@code http.response.status_code} (when a response was received)</li>
 *   <li>{@code server.address} / {@code server.port} (when the resolved request URL is available)</li>
 *   <li>{@code url.template} — the path template (e.g. {@code /users/{id}})</li>
 *   <li>{@code error.type} — {@link io.github.huynhngochuyhoang.httpstarter.exception.ErrorCategory}
 *       name, or the exception class simple-name when category is unset</li>
 * </ul>
 *
 * <h3>Starter-specific attributes</h3>
 * <ul>
 *   <li>{@code rhttp.client.name} — logical name of the {@code @ReactiveHttpClient}</li>
 *   <li>{@code rhttp.api.name} — {@code @ApiName} value, {@code @ApiRef} value, or method name</li>
 *   <li>{@code rhttp.attempt.count} — total subscription attempts (>1 = retried)</li>
 *   <li>{@code rhttp.request.bytes} - application request bytes before transport content coding,
 *       when measurable</li>
 *   <li>{@code rhttp.response.bytes} - post-transport advertised {@code Content-Length};
 *       absent for automatically decompressed or chunked responses</li>
 *   <li>{@code rhttp.failure.stage} - concrete connection, pool, write, or response
 *       phase only when terminal evidence proves it</li>
 * </ul>
 */
public class OpenTelemetryHttpClientObserver implements HttpClientObserver {

    private static final Logger log = LoggerFactory.getLogger(OpenTelemetryHttpClientObserver.class);

    static final String INSTRUMENTATION_NAME = "io.github.huynhngochuyhoang.reactive-http-client";

    static final AttributeKey<String> ATTR_HTTP_METHOD = AttributeKey.stringKey("http.request.method");
    static final AttributeKey<Long> ATTR_HTTP_STATUS_CODE = AttributeKey.longKey("http.response.status_code");
    static final AttributeKey<String> ATTR_SERVER_ADDRESS = AttributeKey.stringKey("server.address");
    static final AttributeKey<Long> ATTR_SERVER_PORT = AttributeKey.longKey("server.port");
    static final AttributeKey<String> ATTR_URL_TEMPLATE = AttributeKey.stringKey("url.template");
    static final AttributeKey<String> ATTR_ERROR_TYPE = AttributeKey.stringKey("error.type");
    static final AttributeKey<String> ATTR_CLIENT_NAME = AttributeKey.stringKey("rhttp.client.name");
    static final AttributeKey<String> ATTR_API_NAME = AttributeKey.stringKey("rhttp.api.name");
    static final AttributeKey<Long> ATTR_ATTEMPT_COUNT = AttributeKey.longKey("rhttp.attempt.count");
    static final AttributeKey<Long> ATTR_REQUEST_BYTES = AttributeKey.longKey("rhttp.request.bytes");
    static final AttributeKey<Long> ATTR_RESPONSE_BYTES = AttributeKey.longKey("rhttp.response.bytes");
    static final AttributeKey<String> ATTR_FAILURE_STAGE = AttributeKey.stringKey("rhttp.failure.stage");

    private final Tracer tracer;
    private final ReactiveHttpClientProperties.ObservabilityConfig config;

    public OpenTelemetryHttpClientObserver(OpenTelemetry openTelemetry) {
        this(openTelemetry, new ReactiveHttpClientProperties.ObservabilityConfig());
    }

    public OpenTelemetryHttpClientObserver(OpenTelemetry openTelemetry,
                                           ReactiveHttpClientProperties.ObservabilityConfig config) {
        this.tracer = openTelemetry.getTracer(INSTRUMENTATION_NAME);
        this.config = config != null ? config : new ReactiveHttpClientProperties.ObservabilityConfig();
    }

    @Override
    public void record(HttpClientObserverEvent event) {
        try {
            String spanName = buildSpanName(event);
            Instant end = Instant.now();
            Instant start = end.minusMillis(Math.max(0L, event.getDurationMs()));

            SpanBuilder builder = tracer.spanBuilder(spanName)
                    .setSpanKind(SpanKind.CLIENT)
                    .setStartTimestamp(start)
                    .setAttribute(ATTR_HTTP_METHOD, nullToUnknown(event.getHttpMethod()))
                    .setAttribute(ATTR_CLIENT_NAME, nullToUnknown(event.getClientName()))
                    .setAttribute(ATTR_API_NAME, nullToUnknown(event.getApiName()))
                    .setAttribute(ATTR_ATTEMPT_COUNT, (long) event.getAttemptCount());

            if (config.isIncludeUrlPath() && event.getUriPath() != null) {
                builder.setAttribute(ATTR_URL_TEMPLATE, event.getUriPath());
            }
            if (event.getStatusCode() != null) {
                builder.setAttribute(ATTR_HTTP_STATUS_CODE, (long) event.getStatusCode());
            }
            if (config.isIncludeServerAddress() && event.getServerAddress() != null) {
                builder.setAttribute(ATTR_SERVER_ADDRESS, event.getServerAddress());
            }
            if (config.isIncludeServerAddress() && event.getServerPort() != null) {
                builder.setAttribute(ATTR_SERVER_PORT, (long) event.getServerPort());
            }
            if (event.getRequestBytes() >= 0) {
                builder.setAttribute(ATTR_REQUEST_BYTES, event.getRequestBytes());
            }
            if (event.getResponseBytes() >= 0) {
                builder.setAttribute(ATTR_RESPONSE_BYTES, event.getResponseBytes());
            }
            if (event.getFailureStage() != null) {
                builder.setAttribute(ATTR_FAILURE_STAGE, event.getFailureStage().name());
            }

            String errorType = resolveErrorType(event);
            if (errorType != null) {
                builder.setAttribute(ATTR_ERROR_TYPE, errorType);
            }

            Span span = builder.startSpan();
            try {
                if (event.isError()) {
                    span.setStatus(StatusCode.ERROR, event.getError().getMessage() != null
                            ? event.getError().getMessage()
                            : event.getError().getClass().getSimpleName());
                    span.recordException(event.getError());
                }
            } finally {
                span.end(end.toEpochMilli(), TimeUnit.MILLISECONDS);
            }
        } catch (Exception e) {
            log.warn("Failed to record OpenTelemetry span for HTTP client exchange: {}", e.getMessage());
        }
    }

    private static String buildSpanName(HttpClientObserverEvent event) {
        String method = event.getHttpMethod() != null ? event.getHttpMethod() : "HTTP";
        String name = event.getApiName() != null ? event.getApiName() : "request";
        return method + " " + name;
    }

    private static String resolveErrorType(HttpClientObserverEvent event) {
        if (event.getErrorCategory() != null) {
            return event.getErrorCategory().name();
        }
        if (event.getError() != null) {
            return event.getError().getClass().getSimpleName();
        }
        return null;
    }

    private static String nullToUnknown(String value) {
        return value != null ? value : "UNKNOWN";
    }
}
