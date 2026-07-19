package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientFailureStage;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * Snapshot of one logical upstream client call for logging/metrics use cases.
 *
 * <p>For {@code Flux<T>} responses, {@code responseBody} is {@code null} because
 * the stream may contain multiple elements; use status/headers/duration instead.
 *
 * <p>{@code inboundHeaders} contains the inbound request headers received from the
 * upstream caller (e.g. an internal service that triggered this outbound call). These
 * are populated automatically when
 * {@link io.github.huynhngochuyhoang.httpstarter.filter.InboundHeadersWebFilter} is
 * registered and the call originates within a WebFlux request context.
 *
 * <p>{@code subscriptionAttemptCount} counts subscriptions within the logical
 * client call. It does not guarantee that each attempt sent an HTTP request.
 *
 * <p>This context does not expose request/response byte counters. Response headers are
 * captured after transport processing, so automatic decompression can remove encoded
 * {@code Content-Encoding} and {@code Content-Length} headers. Loggers must not consume a
 * streaming response body to infer a size.
 */
public record HttpExchangeLogContext(
        String clientName,
        String httpMethod,
        String pathTemplate,
        URI requestUrl,
        Map<String, Object> pathVariables,
        Map<String, List<Object>> queryParameters,
        Map<String, List<String>> inboundHeaders,
        Map<String, String> requestHeaders,
        Object requestBody,
        Integer responseStatus,
        Map<String, List<String>> responseHeaders,
        Object responseBody,
        long durationMs,
        int subscriptionAttemptCount,
        Throwable error,
        ReactiveHttpClientProperties.LogPreset logPreset
) {
    /** Proven transport failure stage, or {@code null} when it cannot be attributed safely. */
    public HttpClientFailureStage failureStage() {
        return HttpClientFailureStage.from(error, responseStatus, requestUrl != null);
    }

    public HttpExchangeLogContext(
            String clientName,
            String httpMethod,
            String pathTemplate,
            URI requestUrl,
            Map<String, Object> pathVariables,
            Map<String, List<Object>> queryParameters,
            Map<String, List<String>> inboundHeaders,
            Map<String, String> requestHeaders,
            Object requestBody,
            Integer responseStatus,
            Map<String, List<String>> responseHeaders,
            Object responseBody,
            long durationMs,
            Throwable error,
            ReactiveHttpClientProperties.LogPreset logPreset) {
        this(
                clientName,
                httpMethod,
                pathTemplate,
                requestUrl,
                pathVariables,
                queryParameters,
                inboundHeaders,
                requestHeaders,
                requestBody,
                responseStatus,
                responseHeaders,
                responseBody,
                durationMs,
                1,
                error,
                logPreset);
    }

    public HttpExchangeLogContext(
            String clientName,
            String httpMethod,
            String pathTemplate,
            Map<String, Object> pathVariables,
            Map<String, List<Object>> queryParameters,
            Map<String, List<String>> inboundHeaders,
            Map<String, String> requestHeaders,
            Object requestBody,
            Integer responseStatus,
            Map<String, List<String>> responseHeaders,
            Object responseBody,
            long durationMs,
            Throwable error,
            ReactiveHttpClientProperties.LogPreset logPreset) {
        this(
                clientName,
                httpMethod,
                pathTemplate,
                null,
                pathVariables,
                queryParameters,
                inboundHeaders,
                requestHeaders,
                requestBody,
                responseStatus,
                responseHeaders,
                responseBody,
                durationMs,
                1,
                error,
                logPreset);
    }

    public HttpExchangeLogContext(
            String clientName,
            String httpMethod,
            String pathTemplate,
            Map<String, Object> pathVariables,
            Map<String, List<Object>> queryParameters,
            Map<String, List<String>> inboundHeaders,
            Map<String, String> requestHeaders,
            Object requestBody,
            Integer responseStatus,
            Map<String, List<String>> responseHeaders,
            Object responseBody,
            long durationMs,
            Throwable error) {
        this(
                clientName,
                httpMethod,
                pathTemplate,
                null,
                pathVariables,
                queryParameters,
                inboundHeaders,
                requestHeaders,
                requestBody,
                responseStatus,
                responseHeaders,
                responseBody,
                durationMs,
                1,
                error,
                ReactiveHttpClientProperties.LogPreset.METADATA_ONLY);
    }
}
