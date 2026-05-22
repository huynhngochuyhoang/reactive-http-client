package io.github.huynhngochuyhoang.httpstarter.filter;

import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.*;

/**
 * {@link WebFilter} that captures a filtered snapshot of inbound request headers
 * and stores it in the Reactor {@link reactor.util.context.Context}.
 *
 * <p>The allow-list / deny-list from
 * {@link ReactiveHttpClientProperties.InboundHeadersConfig} are applied before the
 * snapshot is stored, so sensitive headers such as {@code Authorization} or
 * {@code Cookie} never reach downstream log / metrics consumers that read from
 * {@link io.github.huynhngochuyhoang.httpstarter.core.HttpExchangeLogContext#inboundHeaders()}.
 *
 * <p>Registered automatically by
 * {@link io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientAutoConfiguration}
 * when Spring WebFlux is present.
 */
public class InboundHeadersWebFilter implements WebFilter {

    /** Reactor context key under which the filtered inbound headers map is stored. */
    public static final String INBOUND_HEADERS_CONTEXT_KEY = "inboundHeaders";

    private static final List<String> REDACTED_VALUE = List.of("[REDACTED]");

    private final Set<String> allowList;
    private final Set<String> denyList;

    /** Default constructor uses an {@link ReactiveHttpClientProperties.InboundHeadersConfig} with defaults. */
    public InboundHeadersWebFilter() {
        this(new ReactiveHttpClientProperties.InboundHeadersConfig());
    }

    public InboundHeadersWebFilter(ReactiveHttpClientProperties.InboundHeadersConfig config) {
        ReactiveHttpClientProperties.InboundHeadersConfig resolved =
                config != null ? config : new ReactiveHttpClientProperties.InboundHeadersConfig();
        this.allowList = Set.copyOf(resolved.getAllowList());
        this.denyList = Set.copyOf(resolved.getDenyList());
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        Map<String, List<String>> snapshot = filterHeaders(exchange.getRequest().getHeaders());
        return chain.filter(exchange)
                .contextWrite(ctx -> ctx.put(INBOUND_HEADERS_CONTEXT_KEY, snapshot));
    }

    Map<String, List<String>> filterHeaders(HttpHeaders headers) {
        Map<String, List<String>> out = new LinkedHashMap<>();
        headers.forEach((name, values) -> {
            String lower = name.toLowerCase(Locale.ROOT);
            if (!allowList.isEmpty() && !allowList.contains(lower)) {
                return;
            }
            if (denyList.contains(lower)) {
                out.put(name, REDACTED_VALUE);
            } else {
                out.put(name, List.copyOf(values));
            }
        });
        return Collections.unmodifiableMap(out);
    }
}
