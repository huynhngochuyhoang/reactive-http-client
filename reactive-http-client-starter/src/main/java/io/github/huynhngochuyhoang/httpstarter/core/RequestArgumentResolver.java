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
        Map<String, String> headers = new LinkedHashMap<>();
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
                String headerName = binding.name();
                String headerValue = String.valueOf(args[idx]);
                validateHeaderName(headerName);
                validateHeaderValue(headerName, headerValue);
                headers.put(headerName, headerValue);
            }
        }
        for (RequestPlan.NamedArgumentBinding binding : plan.idempotencyKeyParams()) {
            int idx = binding.argumentIndex();
            if (args != null && idx < args.length && args[idx] != null) {
                String headerName = binding.name();
                String headerValue = String.valueOf(args[idx]);
                validateHeaderName(headerName);
                validateHeaderValue(headerName, headerValue);
                headers.put(headerName, headerValue);
            }
        }
        for (Integer idx : plan.headerMapParams()) {
            if (args != null && idx < args.length && args[idx] instanceof Map<?, ?> headerMap) {
                for (Map.Entry<?, ?> headerEntry : headerMap.entrySet()) {
                    if (headerEntry.getKey() != null && headerEntry.getValue() != null) {
                        String key = String.valueOf(headerEntry.getKey());
                        if (!key.isBlank()) {
                            String value = String.valueOf(headerEntry.getValue());
                            validateHeaderName(key);
                            validateHeaderValue(key, value);
                            headers.put(key, value);
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
            Map<String, String> headers,
            Object body,
            Map<String, String> headersIgnoreCase
    ) {
        /**
         * Convenience factory — builds the case-insensitive view from {@code headers}.
         */
        public ResolvedArgs(
                Map<String, Object> pathVars,
                Map<String, List<Object>> queryParams,
                Map<String, String> headers,
                Object body) {
            this(pathVars, queryParams, headers, body, buildIgnoreCaseView(headers));
        }

        private static Map<String, String> buildIgnoreCaseView(Map<String, String> headers) {
            if (headers == null || headers.isEmpty()) {
                return java.util.Collections.emptyMap();
            }
            TreeMap<String, String> map = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            map.putAll(headers);
            return map;
        }
    }
}
