package io.github.huynhngochuyhoang.httpstarter.filter;

import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import io.github.huynhngochuyhoang.httpstarter.core.RequestContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * {@link WebFilter} that captures the inbound {@code X-Correlation-Id} header
 * and stores it in the Reactor {@link reactor.util.context.Context} so it can be
 * propagated to outbound reactive HTTP client calls made within the same request chain.
 *
 * <p>Values are rejected when they exceed the configured
 * {@link ReactiveHttpClientProperties.CorrelationIdConfig#getMaxLength() max-length}
 * or contain control characters (CR, LF, or any other ISO control character). A
 * rejected value is treated as absent; the filter logs the rejection at DEBUG.
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

    private static final Logger log = LoggerFactory.getLogger(CorrelationIdWebFilter.class);

    /** Reactor context key used to carry the correlation ID across reactive operator boundaries. */
    public static final String CORRELATION_ID_CONTEXT_KEY = RequestContext.CORRELATION_ID_CONTEXT_KEY;

    /** HTTP header name for the correlation ID. */
    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

    private final int maxLength;

    public CorrelationIdWebFilter() {
        this(new ReactiveHttpClientProperties.CorrelationIdConfig());
    }

    public CorrelationIdWebFilter(ReactiveHttpClientProperties.CorrelationIdConfig config) {
        ReactiveHttpClientProperties.CorrelationIdConfig resolved =
                config != null ? config : new ReactiveHttpClientProperties.CorrelationIdConfig();
        this.maxLength = resolved.getMaxLength();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String rawValue = exchange.getRequest().getHeaders().getFirst(CORRELATION_ID_HEADER);
        String sanitized = sanitize(rawValue, "inbound request");
        if (sanitized != null) {
            return chain.filter(exchange)
                    .contextWrite(ctx -> ctx.put(CORRELATION_ID_CONTEXT_KEY, sanitized));
        }
        return chain.filter(exchange);
    }

    /**
     * Returns a WebClient {@link ExchangeFilterFunction} that propagates the
     * {@code X-Correlation-Id} on every outbound request.
     *
     * <p>The correlation ID is read from the Reactor context first (placed there by
     * {@link CorrelationIdWebFilter}), then falls back to MDC for backward
     * compatibility with non-reactive or Brave-based integrations. Values that fail
     * validation ({@linkplain #isValid length / character set}) are silently dropped
     * with a DEBUG-level log.
     */
    public static ExchangeFilterFunction exchangeFilter() {
        return exchangeFilter(new ReactiveHttpClientProperties.CorrelationIdConfig());
    }

    /**
     * Variant of {@link #exchangeFilter()} that honours an explicit
     * {@link ReactiveHttpClientProperties.CorrelationIdConfig} — used by the starter
     * to pass the configured {@code max-length} down to the propagation path.
     */
    public static ExchangeFilterFunction exchangeFilter(ReactiveHttpClientProperties.CorrelationIdConfig config) {
        ReactiveHttpClientProperties.CorrelationIdConfig resolved =
                config != null ? config : new ReactiveHttpClientProperties.CorrelationIdConfig();
        int maxLen = resolved.getMaxLength();
        List<String> mdcKeys = List.copyOf(resolved.getMdcKeys());
        return (request, next) -> Mono.deferContextual(ctx -> {
            String correlationId = RequestContext.correlationId(ctx).orElse(null);
            if (correlationId == null) {
                correlationId = resolveFromMdc(mdcKeys);
            }
            String validated = isValid(correlationId, maxLen) ? correlationId : null;
            if (StringUtils.hasText(validated)) {
                if (StringUtils.hasText(request.headers().getFirst(CORRELATION_ID_HEADER))) {
                    return next.exchange(request);
                }
                String resolvedCorrelationId = validated;
                ClientRequest newRequest = ClientRequest.from(request)
                        .headers(headers -> headers.set(CORRELATION_ID_HEADER, resolvedCorrelationId))
                        .build();
                return next.exchange(newRequest);
            }
            return next.exchange(request);
        });
    }

    private String sanitize(String value, String source) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        if (value.length() > maxLength) {
            log.debug("Dropping correlation-id from {}: length {} exceeds max {}",
                    source, value.length(), maxLength);
            return null;
        }
        if (containsControlCharacter(value)) {
            log.debug("Dropping correlation-id from {}: contains control characters", source);
            return null;
        }
        return value;
    }

    private static String resolveFromMdc(List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return null;
        }
        for (String key : keys) {
            String value;
            try {
                value = MDC.get(key);
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private static boolean isValid(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        if (value.length() > maxLength) {
            return false;
        }
        return !containsControlCharacter(value);
    }

    private static boolean containsControlCharacter(String value) {
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch == '\r' || ch == '\n' || Character.isISOControl(ch)) {
                return true;
            }
        }
        return false;
    }
}
