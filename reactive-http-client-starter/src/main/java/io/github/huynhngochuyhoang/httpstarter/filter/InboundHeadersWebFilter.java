package io.github.huynhngochuyhoang.httpstarter.filter;

import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link WebFilter} that captures a snapshot of all inbound request headers from
 * the upstream caller and stores them in the Reactor {@link reactor.util.context.Context}.
 *
 * <p>This makes the inbound headers available to reactive HTTP client calls made
 * within the same request chain. Custom {@link io.github.huynhngochuyhoang.httpstarter.core.HttpExchangeLogger}
 * implementations can then read them via
 * {@link io.github.huynhngochuyhoang.httpstarter.core.HttpExchangeLogContext#inboundHeaders()}
 * to include propagated headers (e.g. {@code X-Request-Id}, {@code X-User-Id}) in log records.
 *
 * <p>Registered automatically by
 * {@link io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientAutoConfiguration}
 * when Spring WebFlux is present.
 */
public class InboundHeadersWebFilter implements WebFilter {

    /** Reactor context key under which the inbound headers map is stored. */
    public static final String INBOUND_HEADERS_CONTEXT_KEY = "inboundHeaders";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        Map<String, List<String>> snapshot = new LinkedHashMap<>(exchange.getRequest().getHeaders());
        return chain.filter(exchange)
                .contextWrite(ctx -> ctx.put(INBOUND_HEADERS_CONTEXT_KEY, snapshot));
    }
}
