package io.github.huynhngochuyhoang.httpstarter.otel;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.context.propagation.TextMapSetter;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Outbound {@link ExchangeFilterFunction} that reads the
 * {@link OpenTelemetryContextWebFilter#OTEL_CONTEXT_KEY OTel Context} from the
 * Reactor {@link reactor.util.context.ContextView} (placed there by
 * {@link OpenTelemetryContextWebFilter}) and injects the propagated headers
 * onto every outbound request via the
 * {@link io.opentelemetry.context.propagation.TextMapPropagator} configured on
 * the supplied {@link OpenTelemetry}.
 *
 * <p>If no OTel context is present in the Reactor context (e.g. the call
 * originates outside a reactive web request chain), the filter falls back to
 * {@link Context#current()} so calls made within a thread-local OTel scope
 * still propagate.
 *
 * <p>Registered automatically by
 * {@link OpenTelemetryHttpClientAutoConfiguration} as a
 * a Spring Boot {@code WebClientCustomizer}, so both the starter prototype
 * builder and every {@code @ReactiveHttpClient}-built {@code WebClient} pick it up.
 */
public final class OpenTelemetryContextExchangeFilter {

    private static final TextMapSetter<Map<String, String>> MAP_SETTER = (carrier, key, value) -> {
        if (carrier != null && key != null && value != null) {
            carrier.put(key, value);
        }
    };

    private OpenTelemetryContextExchangeFilter() {}

    public static ExchangeFilterFunction create(OpenTelemetry openTelemetry) {
        Objects.requireNonNull(openTelemetry, "openTelemetry");
        TextMapPropagator propagator = openTelemetry.getPropagators().getTextMapPropagator();
        return (request, next) -> Mono.deferContextual(ctxView -> {
            Context otelContext = ctxView.getOrDefault(
                    OpenTelemetryContextWebFilter.OTEL_CONTEXT_KEY,
                    Context.current());
            if (otelContext == null) {
                return next.exchange(request);
            }

            Map<String, String> headers = new LinkedHashMap<>();
            propagator.inject(otelContext, headers, MAP_SETTER);
            if (headers.isEmpty()) {
                return next.exchange(request);
            }

            ClientRequest enriched = ClientRequest.from(request)
                    .headers(h -> headers.forEach((name, value) -> {
                        // Don't clobber values the caller already set explicitly.
                        if (h.get(name) == null) {
                            h.set(name, value);
                        }
                    }))
                    .build();
            return next.exchange(enriched);
        });
    }
}
