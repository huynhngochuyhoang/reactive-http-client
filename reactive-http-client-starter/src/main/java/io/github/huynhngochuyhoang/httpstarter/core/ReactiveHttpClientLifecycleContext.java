package io.github.huynhngochuyhoang.httpstarter.core;

import java.net.URI;
import java.util.*;

/**
 * Immutable call state passed to {@link ReactiveHttpClientLifecycleHook} methods.
 * This context intentionally has no response-header or byte-size fields; lifecycle hooks
 * must not consume request or response publishers to infer encoded or decoded sizes.
 */
public record ReactiveHttpClientLifecycleContext(
        String clientName,
        String apiName,
        String httpMethod,
        String pathTemplate,
        Map<String, Object> pathVars,
        Map<String, List<Object>> queryParams,
        Map<String, String> headers,
        Object requestBody,
        URI requestUrl,
        Integer statusCode,
        Throwable error,
        int attemptNumber
) {

    public ReactiveHttpClientLifecycleContext {
        pathVars = pathVars != null ? Map.copyOf(pathVars) : Map.of();
        queryParams = copyQueryParams(queryParams);
        headers = headers != null ? Map.copyOf(headers) : Map.of();
    }

    static ReactiveHttpClientLifecycleContext from(
            String clientName,
            RequestPlan plan,
            String httpMethod,
            String pathTemplate,
            RequestArgumentResolver.ResolvedArgs resolved,
            URI requestUrl,
            Integer statusCode,
            Throwable error,
            int attemptNumber) {
        return new ReactiveHttpClientLifecycleContext(
                clientName,
                plan.apiName(),
                httpMethod,
                pathTemplate,
                resolved.pathVars(),
                resolved.queryParams(),
                resolved.flattenedHeaders(),
                resolved.body(),
                requestUrl,
                statusCode,
                error,
                attemptNumber);
    }

    private static Map<String, List<Object>> copyQueryParams(Map<String, List<Object>> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        Map<String, List<Object>> copied = new LinkedHashMap<>();
        source.forEach((key, values) -> copied.put(key, values != null ? copyNullableElements(values) : List.of()));
        return Map.copyOf(copied);
    }

    private static List<Object> copyNullableElements(List<Object> values) {
        return Collections.unmodifiableList(new ArrayList<>(values));
    }
}
