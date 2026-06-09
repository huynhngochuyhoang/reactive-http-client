package io.github.huynhngochuyhoang.httpstarter.core;

import java.lang.reflect.Array;
import java.util.*;

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
        return resolve(meta.getRequestPlan() != null ? meta.getRequestPlan() : RequestPlan.from(meta), args);
    }

    public ResolvedArgs resolve(RequestPlan plan, Object[] args) {
        Map<String, Object> pathVars = new LinkedHashMap<>();
        Map<String, List<Object>> queryParams = new LinkedHashMap<>();
        Map<String, List<String>> headers = new LinkedHashMap<>();
        Object body = null;

        for (RequestPlan.NamedArgumentBinding binding : plan.pathVars()) {
            int idx = binding.argumentIndex();
            if (args != null && idx < args.length && args[idx] != null) {
                pathVars.put(binding.name(), args[idx]);
            }
        }

        for (RequestPlan.NamedArgumentBinding binding : plan.queryParams()) {
            int idx = binding.argumentIndex();
            if (args != null && idx < args.length && args[idx] != null) {
                queryParams.put(binding.name(), toValueList(args[idx]));
            }
        }

        for (RequestPlan.NamedArgumentBinding binding : plan.headerParams()) {
            int idx = binding.argumentIndex();
            if (args != null && idx < args.length && args[idx] != null) {
                putHeaderValues(headers, binding.name(), args[idx]);
            }
        }
        for (RequestPlan.NamedArgumentBinding binding : plan.idempotencyKeyParams()) {
            int idx = binding.argumentIndex();
            if (args != null && idx < args.length && args[idx] != null) {
                putHeaderValues(headers, binding.name(), args[idx]);
            }
        }
        for (Integer idx : plan.headerMapParams()) {
            if (args != null && idx < args.length && args[idx] instanceof Map<?, ?> headerMap) {
                for (Map.Entry<?, ?> headerEntry : headerMap.entrySet()) {
                    if (headerEntry.getKey() != null && headerEntry.getValue() != null) {
                        String key = String.valueOf(headerEntry.getKey());
                        if (!key.isBlank()) {
                            putHeaderValues(headers, key, headerEntry.getValue());
                        }
                    }
                }
            }
        }

        if (plan.bodyIndex() >= 0 && args != null && plan.bodyIndex() < args.length) {
            body = args[plan.bodyIndex()];
        }

        return new ResolvedArgs(pathVars, queryParams, headers, body);
    }

    /**
     * Converts a query-parameter value into a list of individual elements.
     * <ul>
     *   <li>{@link Collection} -> each element becomes a separate value</li>
     *   <li>array -> each element becomes a separate value</li>
     *   <li>scalar -> wrapped in a single-element list</li>
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

    private void putHeaderValues(Map<String, List<String>> headers, String headerName, Object rawValue) {
        validateHeaderName(headerName);
        List<String> values = toHeaderValueList(headerName, rawValue);
        if (!values.isEmpty()) {
            headers.put(headerName, values);
        }
    }

    private List<String> toHeaderValueList(String headerName, Object value) {
        if (value instanceof Collection<?> collection) {
            List<String> values = new ArrayList<>();
            for (Object item : collection) {
                addHeaderValue(values, headerName, item);
            }
            return values;
        }
        if (value != null && value.getClass().isArray()) {
            int len = Array.getLength(value);
            List<String> values = new ArrayList<>(len);
            for (int i = 0; i < len; i++) {
                addHeaderValue(values, headerName, Array.get(value, i));
            }
            return values;
        }
        if (value == null) {
            return List.of();
        }
        String headerValue = String.valueOf(value);
        validateHeaderValue(headerName, headerValue);
        return List.of(headerValue);
    }

    private void addHeaderValue(List<String> values, String headerName, Object value) {
        if (value == null) {
            return;
        }
        String headerValue = String.valueOf(value);
        validateHeaderValue(headerName, headerValue);
        values.add(headerValue);
    }

    static void validateHeaderName(String headerName) {
        if (headerName == null || headerName.isBlank()) {
            throw new IllegalArgumentException("Header name must not be blank");
        }
        for (int i = 0; i < headerName.length(); i++) {
            char ch = headerName.charAt(i);
            if (ch <= 32 || ch >= 127 || "()<>@,;:\\\"/[]?={} \t".indexOf(ch) >= 0) {
                throw new IllegalArgumentException("Invalid header name '" + headerName + "'");
            }
        }
    }

    static void validateHeaderValue(String headerName, String value) {
        if (value == null) {
            return;
        }
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch == '\r' || ch == '\n' || Character.isISOControl(ch)) {
                throw new IllegalArgumentException("Invalid header value for '" + headerName
                        + "': CRLF and control characters are not allowed");
            }
        }
    }

    /**
     * Container for the arguments extracted from a single method invocation.
     *
     * <p>{@code queryParams} maps each parameter name to a list of values to support
     * multi-value query parameters (e.g. {@code ?roles=admin&roles=user}).
     *
     * <p>{@code headersIgnoreCase} is a pre-built case-insensitive view of
     * {@code headers}, built once per invocation so header name lookups
     * (e.g. for {@code Content-Type} or {@code Accept}) don't require iterating
     * the full header map on every check.
     */
    public record ResolvedArgs(
            Map<String, Object> pathVars,
            Map<String, List<Object>> queryParams,
            Map<String, List<String>> headers,
            Object body,
            Map<String, String> headersIgnoreCase
    ) {
        /**
         * Convenience factory — builds the case-insensitive view from {@code headers}.
         */
        public ResolvedArgs(
                Map<String, Object> pathVars,
                Map<String, List<Object>> queryParams,
                Map<String, List<String>> headers,
                Object body) {
            this(pathVars, queryParams, copyHeaders(headers), body, buildIgnoreCaseView(headers));
        }

        Map<String, String> flattenedHeaders() {
            if (headers == null || headers.isEmpty()) {
                return Map.of();
            }
            Map<String, String> flattened = new LinkedHashMap<>();
            headers.forEach((name, values) -> flattened.put(name, String.join(",", values)));
            return Map.copyOf(flattened);
        }

        boolean hasHeaderValueIgnoreCase(String headerName) {
            if (headers == null || headers.isEmpty()) {
                return false;
            }
            for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(headerName)
                        && entry.getValue().stream().anyMatch(value -> value != null && !value.isBlank())) {
                    return true;
                }
            }
            return false;
        }

        private static Map<String, List<String>> copyHeaders(Map<String, List<String>> headers) {
            if (headers == null || headers.isEmpty()) {
                return Map.of();
            }
            Map<String, List<String>> copied = new LinkedHashMap<>();
            headers.forEach((name, values) -> copied.put(name, values != null ? List.copyOf(values) : List.of()));
            return Map.copyOf(copied);
        }

        private static Map<String, String> buildIgnoreCaseView(Map<String, List<String>> headers) {
            if (headers == null || headers.isEmpty()) {
                return java.util.Collections.emptyMap();
            }
            TreeMap<String, String> map = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            headers.forEach((name, values) -> map.put(name,
                    values == null || values.isEmpty() ? null : values.get(0)));
            return map;
        }
    }
}
