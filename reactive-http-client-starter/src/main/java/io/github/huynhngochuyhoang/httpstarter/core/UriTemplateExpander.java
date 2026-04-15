package io.github.huynhngochuyhoang.httpstarter.core;

import org.springframework.web.util.UriComponentsBuilder;

import java.lang.reflect.Array;
import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Expands a URI template by substituting path variables and appending query parameters.
 * <p>
 * Multi-value query parameters (passed as a {@link Collection} or array) are expanded into
 * repeated keys: {@code ?roles=admin&roles=user}.
 * <p>
 * Used as a utility when a fully-resolved {@link URI} is needed outside of
 * {@link org.springframework.web.reactive.function.client.WebClient}.
 */
public class UriTemplateExpander {

    /**
     * Builds a {@link URI} from the given components.
     *
     * @param baseUrl      service base URL (e.g. {@code https://api.example.com})
     * @param pathTemplate path template that may contain {@code {variable}} placeholders
     * @param pathVars     values for the path placeholders
     * @param queryParams  additional query parameters (nulls already filtered out);
     *                     values may be scalar, {@link Collection}, or array
     * @return fully expanded and encoded URI
     */
    public URI expand(String baseUrl, String pathTemplate,
                      Map<String, Object> pathVars, Map<String, Object> queryParams) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(baseUrl + pathTemplate);
        queryParams.forEach((k, v) -> toValueList(v).forEach(val -> builder.queryParam(k, val)));
        return builder.buildAndExpand(pathVars).encode().toUri();
    }

    private List<Object> toValueList(Object value) {
        if (value instanceof Collection<?> col) {
            return List.copyOf(col);
        }
        if (value != null && value.getClass().isArray()) {
            int len = Array.getLength(value);
            Object[] arr = new Object[len];
            for (int i = 0; i < len; i++) {
                arr[i] = Array.get(value, i);
            }
            return List.of(arr);
        }
        return List.of(value);
    }
}
