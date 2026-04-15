package io.github.huynhngochuyhoang.httpstarter.core;

import java.util.List;
import java.util.Map;

/**
 * Snapshot of one upstream HTTP exchange for logging/metrics use cases.
 *
 * <p>For {@code Flux<T>} responses, {@code responseBody} is {@code null} because
 * the stream may contain multiple elements; use status/headers/duration instead.
 */
public record HttpExchangeLogContext(
        String clientName,
        String httpMethod,
        String pathTemplate,
        Map<String, Object> pathVariables,
        Map<String, List<Object>> queryParameters,
        Map<String, String> requestHeaders,
        Object requestBody,
        Integer responseStatus,
        Map<String, List<String>> responseHeaders,
        Object responseBody,
        long durationMs,
        Throwable error
) {}
