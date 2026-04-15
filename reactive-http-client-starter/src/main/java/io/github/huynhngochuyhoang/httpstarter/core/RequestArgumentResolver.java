package io.github.huynhngochuyhoang.httpstarter.core;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves method invocation arguments into structured maps according to the annotations
 * captured in {@link MethodMetadata}.
 *
 * <p>Query parameters whose value is a {@link Collection} or an array are stored as a
 * {@code List} of individual elements so that the caller can generate proper multi-value
 * query strings (e.g. {@code ?roles=admin&roles=user}) rather than a stringified
 * representation (e.g. {@code ?roles=[admin, user]}).
 */
public class RequestArgumentResolver {

    public ResolvedArgs resolve(MethodMetadata meta, Object[] args) {
        Map<String, Object> pathVars = new LinkedHashMap<>();
        Map<String, List<Object>> queryParams = new LinkedHashMap<>();
        Map<String, String> headers = new LinkedHashMap<>();
        Object body = null;

        for (Map.Entry<Integer, String> entry : meta.getPathVars().entrySet()) {
            int idx = entry.getKey();
            if (args != null && idx < args.length && args[idx] != null) {
                pathVars.put(entry.getValue(), args[idx]);
            }
        }

        for (Map.Entry<Integer, String> entry : meta.getQueryParams().entrySet()) {
            int idx = entry.getKey();
            if (args != null && idx < args.length && args[idx] != null) {
                queryParams.put(entry.getValue(), toValueList(args[idx]));
            }
        }

        for (Map.Entry<Integer, String> entry : meta.getHeaderParams().entrySet()) {
            int idx = entry.getKey();
            if (args != null && idx < args.length && args[idx] != null) {
                headers.put(entry.getValue(), String.valueOf(args[idx]));
            }
        }
        for (Integer idx : meta.getHeaderMapParams()) {
            if (args != null && idx < args.length && args[idx] instanceof Map<?, ?> headerMap) {
                for (Map.Entry<?, ?> headerEntry : headerMap.entrySet()) {
                    if (headerEntry.getKey() != null && headerEntry.getValue() != null) {
                        String key = String.valueOf(headerEntry.getKey());
                        if (!key.isBlank()) {
                            headers.put(key, String.valueOf(headerEntry.getValue()));
                        }
                    }
                }
            }
        }

        if (meta.getBodyIndex() >= 0 && args != null && meta.getBodyIndex() < args.length) {
            body = args[meta.getBodyIndex()];
        }

        return new ResolvedArgs(pathVars, queryParams, headers, body);
    }

    /**
     * Converts a query-parameter value into a list of individual elements.
     * <ul>
     *   <li>{@link Collection} → each element becomes a separate value</li>
     *   <li>array → each element becomes a separate value</li>
     *   <li>scalar → wrapped in a single-element list</li>
     * </ul>
     */
    private List<Object> toValueList(Object value) {
        if (value instanceof Collection<?> col) {
            return new ArrayList<>(col);
        }
        if (value != null && value.getClass().isArray()) {
            int len = Array.getLength(value);
            List<Object> list = new ArrayList<>(len);
            for (int i = 0; i < len; i++) {
                list.add(Array.get(value, i));
            }
            return list;
        }
        return List.of(value);
    }

    /**
     * Container for the arguments extracted from a single method invocation.
     *
     * <p>{@code queryParams} maps each parameter name to a list of values to support
     * multi-value query parameters (e.g. {@code ?roles=admin&roles=user}).
     */
    public record ResolvedArgs(
            Map<String, Object> pathVars,
            Map<String, List<Object>> queryParams,
            Map<String, String> headers,
            Object body
    ) {}
}
