package io.github.huynhngochuyhoang.httpstarter.otel;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import org.springframework.http.HttpHeaders;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.Objects;

/**
 * Server-side {@link WebFilter} that extracts an OpenTelemetry {@link Context}
 * from inbound HTTP headers using whatever
 * {@link io.opentelemetry.context.propagation.TextMapPropagator} the configured
 * {@link OpenTelemetry} exposes (typically a composite of W3C Trace Context +
 * W3C Baggage), and stores the extracted context in the Reactor
 * {@link reactor.util.context.ContextView} under
 * {@link #OTEL_CONTEXT_KEY}.
 *
 * <p>Companion filter
 * {@link OpenTelemetryContextExchangeFilter#create(OpenTelemetry)} reads from
 * the same key on the outbound side and injects the propagated headers onto
 * downstream WebClient requests, completing the {@code traceparent} /
 * {@code baggage} pass-through.
 *
 * <p>Auto-registered by
 * {@link OpenTelemetryHttpClientAutoConfiguration} when running in a
 * reactive web application context. No effect outside of a reactive web app.
 */
public class OpenTelemetryContextWebFilter implements WebFilter {

    /** Reactor {@link reactor.util.context.ContextView} key carrying the extracted OTel {@link Context}. */
    public static final String OTEL_CONTEXT_KEY = "otel.context";

    private static final TextMapGetter<HttpHeaders> HEADER_GETTER = new TextMapGetter<>() {
        @Override
        public Iterable<String> keys(HttpHeaders carrier) {
            if (carrier == null) {
                return java.util.List.of();
            }
            java.util.List<String> names = new java.util.ArrayList<>();
            carrier.forEach((name, values) -> names.add(name));
            return names;
        }

        @Override
        public String get(HttpHeaders carrier, String key) {
            return carrier == null ? null : carrier.getFirst(key);
        }
    };

    private final OpenTelemetry openTelemetry;

    public OpenTelemetryContextWebFilter(OpenTelemetry openTelemetry) {
        this.openTelemetry = Objects.requireNonNull(openTelemetry, "openTelemetry");
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        Context extracted = openTelemetry.getPropagators()
                .getTextMapPropagator()
                .extract(Context.root(), exchange.getRequest().getHeaders(), HEADER_GETTER);

        return chain.filter(exchange)
                .contextWrite(ctx -> ctx.put(OTEL_CONTEXT_KEY, extracted));
    }
}
