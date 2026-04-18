package io.github.huynhngochuyhoang.httpstarter.auth;

import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Authentication data to inject into outgoing requests.
 */
public final class AuthContext {

    private static final AuthContext EMPTY = new AuthContext(Map.of(), Map.of());

    private final Map<String, String> headers;
    private final Map<String, List<String>> queryParams;

    public AuthContext(Map<String, String> headers, Map<String, List<String>> queryParams) {
        this.headers = Collections.unmodifiableMap(copyHeaders(headers));
        this.queryParams = Collections.unmodifiableMap(copyQueryParams(queryParams));
    }

    public static AuthContext empty() {
        return EMPTY;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public Map<String, List<String>> getQueryParams() {
        return queryParams;
    }

    private static Map<String, String> copyHeaders(Map<String, String> source) {
        if (CollectionUtils.isEmpty(source)) {
            return Map.of();
        }
        Map<String, String> copied = new LinkedHashMap<>();
        source.forEach((name, value) -> {
            if (StringUtils.hasText(name) && value != null) {
                copied.put(name, value);
            }
        });
        return copied;
    }

    private static Map<String, List<String>> copyQueryParams(Map<String, List<String>> source) {
        if (CollectionUtils.isEmpty(source)) {
            return Map.of();
        }
        Map<String, List<String>> copied = new LinkedHashMap<>();
        source.forEach((name, values) -> {
            if (!StringUtils.hasText(name) || CollectionUtils.isEmpty(values)) {
                return;
            }
            List<String> copiedValues = new ArrayList<>();
            for (String value : values) {
                if (value != null) {
                    copiedValues.add(value);
                }
            }
            if (!copiedValues.isEmpty()) {
                copied.put(name, List.copyOf(copiedValues));
            }
        });
        return copied;
    }

    public static final class Builder {
        private final Map<String, String> headers = new LinkedHashMap<>();
        private final Map<String, List<String>> queryParams = new LinkedHashMap<>();

        private Builder() {}

        public Builder header(String name, String value) {
            headers.put(name, value);
            return this;
        }

        public Builder headers(Map<String, String> values) {
            if (!CollectionUtils.isEmpty(values)) {
                headers.putAll(values);
            }
            return this;
        }

        public Builder queryParam(String name, String value) {
            queryParams.computeIfAbsent(name, key -> new ArrayList<>()).add(value);
            return this;
        }

        public Builder queryParams(Map<String, ? extends Collection<String>> values) {
            if (!CollectionUtils.isEmpty(values)) {
                values.forEach((name, rawValues) -> {
                    if (!CollectionUtils.isEmpty(rawValues)) {
                        queryParams.computeIfAbsent(name, key -> new ArrayList<>()).addAll(rawValues);
                    }
                });
            }
            return this;
        }

        public AuthContext build() {
            return new AuthContext(headers, queryParams);
        }
    }
}
