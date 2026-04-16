package io.github.huynhngochuyhoang.httpstarter.filter;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.util.context.Context;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Unit tests for {@link CorrelationIdWebFilter}.
 */
class CorrelationIdWebFilterTest {

    private final CorrelationIdWebFilter filter = new CorrelationIdWebFilter();

    @Test
    void shouldStoreCorrelationIdInReactorContextWhenHeaderPresent() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/test")
                .header(CorrelationIdWebFilter.CORRELATION_ID_HEADER, "abc-123")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicReference<String> capturedId = new AtomicReference<>();

        WebFilterChain chain = ex -> Mono.deferContextual(ctx -> {
            capturedId.set(ctx.getOrDefault(CorrelationIdWebFilter.CORRELATION_ID_CONTEXT_KEY, null));
            return Mono.empty();
        });

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertEquals("abc-123", capturedId.get());
    }

    @Test
    void shouldNotAddContextKeyWhenHeaderAbsent() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/test").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicReference<String> capturedId = new AtomicReference<>();

        WebFilterChain chain = ex -> Mono.deferContextual(ctx -> {
            capturedId.set(ctx.getOrDefault(CorrelationIdWebFilter.CORRELATION_ID_CONTEXT_KEY, null));
            return Mono.empty();
        });

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertNull(capturedId.get());
    }

    @Test
    void shouldInvokeChainWhenHeaderAbsent() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/test").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicReference<Boolean> chainInvoked = new AtomicReference<>(false);
        WebFilterChain chain = ex -> {
            chainInvoked.set(true);
            return Mono.empty();
        };

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertEquals(Boolean.TRUE, chainInvoked.get());
    }
}
