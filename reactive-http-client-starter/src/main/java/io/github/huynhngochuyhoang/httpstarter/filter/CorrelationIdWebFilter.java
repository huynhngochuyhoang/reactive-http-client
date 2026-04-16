package io.github.huynhngochuyhoang.httpstarter.filter;

import org.slf4j.MDC;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * {@link WebFilter} that captures the inbound {@code X-Correlation-Id} header
 * and stores it in the Reactor {@link reactor.util.context.Context} so it can be
 * propagated to outbound reactive HTTP client calls made within the same request chain.
 *
 * <p>Registered automatically by
 * {@link io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientAutoConfiguration}
 * when Spring WebFlux is present. No extra configuration is required.
 *
 * <p>The companion {@link #exchangeFilter()} method returns a WebClient
 * {@link ExchangeFilterFunction} that reads the correlation ID from the Reactor context
 * (with MDC as a fallback) and forwards it on every outbound request.
 */
public class CorrelationIdWebFilter implements WebFilter {

    /** Reactor context key used to carry the correlation ID across reactive operator boundaries. */
    public static final String CORRELATION_ID_CONTEXT_KEY = "correlationId";

    /** HTTP header name for the correlation ID. */
    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String correlationId = exchange.getRequest().getHeaders().getFirst(CORRELATION_ID_HEADER);
        if (correlationId != null) {
            return chain.filter(exchange)
                    .contextWrite(ctx -> ctx.put(CORRELATION_ID_CONTEXT_KEY, correlationId));
        }
        return chain.filter(exchange);
    }

    /**
     * Returns a WebClient {@link ExchangeFilterFunction} that propagates the
     * {@code X-Correlation-Id} on every outbound request.
     *
     * <p>The correlation ID is read from the Reactor context first (placed there by
     * {@link CorrelationIdWebFilter}), then falls back to MDC for backward
     * compatibility with non-reactive or Brave-based integrations.
     */
    public static ExchangeFilterFunction exchangeFilter() {
        return (request, next) -> Mono.deferContextual(ctx -> {
            String correlationId = ctx.getOrDefault(CORRELATION_ID_CONTEXT_KEY, null);
            if (correlationId == null) {
                // Fall back to MDC for backward compatibility
                correlationId = MDC.get(CORRELATION_ID_CONTEXT_KEY);
            }
            if (StringUtils.hasText(correlationId)) {
                ClientRequest newRequest = ClientRequest.from(request)
                        .header(CORRELATION_ID_HEADER, correlationId)
                        .build();
                return next.exchange(newRequest);
            }
            return next.exchange(request);
        });
    }
}
