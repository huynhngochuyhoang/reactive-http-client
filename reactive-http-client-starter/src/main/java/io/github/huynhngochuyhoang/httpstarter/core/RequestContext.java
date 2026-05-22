package io.github.huynhngochuyhoang.httpstarter.core;

import reactor.util.context.Context;
import reactor.util.context.ContextView;

import java.util.*;

/**
 * Typed helpers for starter-owned Reactor context values.
 */
public final class RequestContext {

    /**
     * Reactor context key used to carry the correlation ID.
     */
    public static final String CORRELATION_ID_CONTEXT_KEY = "correlationId";

    /**
     * Reactor context key used to carry the filtered inbound header snapshot.
     */
    public static final String INBOUND_HEADERS_CONTEXT_KEY = "inboundHeaders";

    private RequestContext() {
    }

    /**
     * Reads the correlation ID from the Reactor context.
     */
    public static Optional<String> correlationId(ContextView context) {
        Objects.requireNonNull(context, "context must not be null");
        return Optional.ofNullable(context.getOrDefault(CORRELATION_ID_CONTEXT_KEY, null));
    }

    /**
     * Reads the filtered inbound header snapshot from the Reactor context.
     */
    public static Map<String, List<String>> inboundHeaders(ContextView context) {
        Objects.requireNonNull(context, "context must not be null");
        return context.getOrDefault(INBOUND_HEADERS_CONTEXT_KEY, Map.of());
    }

    /**
     * Writes the correlation ID to the Reactor context.
     */
    public static Context withCorrelationId(Context context, String correlationId) {
        Objects.requireNonNull(context, "context must not be null");
        return correlationId != null ? context.put(CORRELATION_ID_CONTEXT_KEY, correlationId) : context;
    }

    /**
     * Writes a defensive inbound header snapshot to the Reactor context.
     */
    public static Context withInboundHeaders(Context context, Map<String, List<String>> inboundHeaders) {
        Objects.requireNonNull(context, "context must not be null");
        Map<String, List<String>> snapshot = immutableHeaders(inboundHeaders);
        return !snapshot.isEmpty() ? context.put(INBOUND_HEADERS_CONTEXT_KEY, snapshot) : context;
    }

    static Map<String, List<String>> immutableHeaders(Map<String, List<String>> headers) {
        if (headers == null || headers.isEmpty()) {
            return Map.of();
        }
        Map<String, List<String>> copy = new LinkedHashMap<>();
        headers.forEach((name, values) -> copy.put(name, List.copyOf(values != null ? values : List.of())));
        return Collections.unmodifiableMap(copy);
    }
}
