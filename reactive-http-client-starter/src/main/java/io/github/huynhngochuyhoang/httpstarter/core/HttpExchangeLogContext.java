package io.github.huynhngochuyhoang.httpstarter.core;

import java.util.List;
import java.util.Map;

/**
 * Snapshot of one upstream HTTP exchange for logging/metrics use cases.
 *
 * <p>For {@code Flux<T>} responses, {@code responseBody} is {@code null} because
 * the stream may contain multiple elements; use status/headers/duration instead.
 *
 * <p>{@code inboundHeaders} contains the inbound request headers received from the
 * upstream caller (e.g. an internal service that triggered this outbound call). These
 * are populated automatically when
 * {@link io.github.huynhngochuyhoang.httpstarter.filter.InboundHeadersWebFilter} is
 * registered and the call originates within a WebFlux request context.
 */
public record HttpExchangeLogContext(
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
        Throwable error
) {}
