package io.github.huynhngochuyhoang.httpstarter.filter;

import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link InboundHeadersWebFilter}, focused on snapshot capture,
 * allow-list matching, and redaction behavior.
 */
class InboundHeadersWebFilterTest {

    @Test
    void redactsSensitiveHeadersByDefault() {
        InboundHeadersWebFilter filter = new InboundHeadersWebFilter();

        MockServerHttpRequest request = MockServerHttpRequest.get("/test")
                .header("Authorization", "Bearer super-secret")
                .header("Cookie", "session=abcd")
                .header("X-Api-Key", "key-123")
                .header("X-Request-Id", "req-7")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        Map<String, List<String>> captured = runFilter(filter, exchange);

        assertEquals(List.of("[REDACTED]"), captured.get("Authorization"));
        assertEquals(List.of("[REDACTED]"), captured.get("Cookie"));
        assertEquals(List.of("[REDACTED]"), captured.get("X-Api-Key"));
        assertEquals(List.of("req-7"), captured.get("X-Request-Id"));
    }

    @Test
    void redactionIsCaseInsensitive() {
        InboundHeadersWebFilter filter = new InboundHeadersWebFilter();

        MockServerHttpRequest request = MockServerHttpRequest.get("/test")
                .header("AUTHORIZATION", "Bearer x")
                .header("proxy-authorization", "Basic y")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        Map<String, List<String>> captured = runFilter(filter, exchange);

        assertEquals(List.of("[REDACTED]"), captured.get("AUTHORIZATION"));
        assertEquals(List.of("[REDACTED]"), captured.get("proxy-authorization"));
    }

    @Test
    void preservesOriginalHeaderCasingWhileMatchingCaseInsensitively() {
        ReactiveHttpClientProperties.InboundHeadersConfig config =
                new ReactiveHttpClientProperties.InboundHeadersConfig();
        config.setAllowList(Set.of("x-request-id", "authorization"));
        config.setDenyList(Set.of("authorization"));

        InboundHeadersWebFilter filter = new InboundHeadersWebFilter(config);

        MockServerHttpRequest request = MockServerHttpRequest.get("/test")
                .header("X-ReQuEsT-Id", "req-7")
                .header("AUTHORIZATION", "Bearer x")
                .header("X-Trace-Id", "trace-1")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        Map<String, List<String>> captured = runFilter(filter, exchange);

        assertEquals(List.of("req-7"), captured.get("X-ReQuEsT-Id"));
        assertEquals(List.of("[REDACTED]"), captured.get("AUTHORIZATION"));
        assertFalse(captured.containsKey("x-request-id"));
        assertFalse(captured.containsKey("authorization"));
        assertFalse(captured.containsKey("X-Trace-Id"));
    }

    @Test
    void snapshotIsDefensiveAndImmutable() {
        InboundHeadersWebFilter filter = new InboundHeadersWebFilter();
        HttpHeaders headers = new HttpHeaders();
        List<String> originalValues = new ArrayList<>(List.of("one", "two"));
        headers.put("X-Multi", originalValues);

        Map<String, List<String>> captured = filter.filterHeaders(headers);

        originalValues.add("three");
        headers.add("X-After", "after");

        assertEquals(List.of("one", "two"), captured.get("X-Multi"));
        assertFalse(captured.containsKey("X-After"));
        assertThrows(UnsupportedOperationException.class,
                () -> captured.put("X-New", List.of("new")));
        assertThrows(UnsupportedOperationException.class,
                () -> captured.get("X-Multi").add("three"));
    }

    @Test
    void allowListDropsEverythingNotListed() {
        ReactiveHttpClientProperties.InboundHeadersConfig config =
                new ReactiveHttpClientProperties.InboundHeadersConfig();
        config.setAllowList(Set.of("X-Request-Id", "X-User-Id"));

        InboundHeadersWebFilter filter = new InboundHeadersWebFilter(config);

        MockServerHttpRequest request = MockServerHttpRequest.get("/test")
                .header("X-Request-Id", "req-7")
                .header("X-User-Id", "user-9")
                .header("Authorization", "Bearer x")
                .header("X-Trace-Id", "trace-1")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        Map<String, List<String>> captured = runFilter(filter, exchange);

        assertEquals(List.of("req-7"), captured.get("X-Request-Id"));
        assertEquals(List.of("user-9"), captured.get("X-User-Id"));
        assertFalse(captured.containsKey("Authorization"),
                "allow-list should drop entries outside the list");
        assertFalse(captured.containsKey("X-Trace-Id"),
                "allow-list should drop entries outside the list");
    }

    @Test
    void emptyDenyListCapturesSensitiveHeadersVerbatim() {
        ReactiveHttpClientProperties.InboundHeadersConfig config =
                new ReactiveHttpClientProperties.InboundHeadersConfig();
        config.setDenyList(Set.of());

        InboundHeadersWebFilter filter = new InboundHeadersWebFilter(config);

        MockServerHttpRequest request = MockServerHttpRequest.get("/test")
                .header("Authorization", "Bearer super-secret")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        Map<String, List<String>> captured = runFilter(filter, exchange);

        assertTrue(captured.containsKey("Authorization"));
        assertEquals(List.of("Bearer super-secret"), captured.get("Authorization"));
    }

    @Test
    void allowListEntryStillRedactedWhenAlsoOnDenyList() {
        ReactiveHttpClientProperties.InboundHeadersConfig config =
                new ReactiveHttpClientProperties.InboundHeadersConfig();
        config.setAllowList(Set.of("Authorization", "X-Request-Id"));

        InboundHeadersWebFilter filter = new InboundHeadersWebFilter(config);

        MockServerHttpRequest request = MockServerHttpRequest.get("/test")
                .header("Authorization", "Bearer super-secret")
                .header("X-Request-Id", "req-7")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        Map<String, List<String>> captured = runFilter(filter, exchange);

        assertEquals(List.of("[REDACTED]"), captured.get("Authorization"));
        assertEquals(List.of("req-7"), captured.get("X-Request-Id"));
    }

    private static Map<String, List<String>> runFilter(InboundHeadersWebFilter filter,
                                                       MockServerWebExchange exchange) {
        AtomicReference<Map<String, List<String>>> ref = new AtomicReference<>(Map.of());

        WebFilterChain chain = ex -> Mono.deferContextual(ctx -> {
            @SuppressWarnings("unchecked")
            Map<String, List<String>> snapshot =
                    (Map<String, List<String>>) ctx.get(InboundHeadersWebFilter.INBOUND_HEADERS_CONTEXT_KEY);
            ref.set(snapshot);
            return Mono.empty();
        });

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
        return ref.get();
    }
}
