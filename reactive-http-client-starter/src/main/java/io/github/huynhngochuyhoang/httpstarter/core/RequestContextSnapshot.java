package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.filter.CorrelationIdWebFilter;
import io.github.huynhngochuyhoang.httpstarter.filter.InboundHeadersWebFilter;
import org.springframework.util.CollectionUtils;
import reactor.util.context.Context;
import reactor.util.context.ContextView;

import java.util.*;

/**
 * Immutable snapshot of starter-owned request context values that can be carried
 * explicitly across asynchronous boundaries such as sinks, queues, or callbacks.
 */
public record RequestContextSnapshot(String correlationId, Map<String, List<String>> inboundHeaders) {

    private static final RequestContextSnapshot EMPTY = new RequestContextSnapshot(null, Map.of());

    public RequestContextSnapshot {
        inboundHeaders = immutableHeaders(inboundHeaders);
    }

    /**
     * Returns an empty snapshot with no correlation ID and no inbound headers.
     */
    public static RequestContextSnapshot empty() {
        return EMPTY;
    }

    /**
     * Captures starter-owned values from the current Reactor context.
     */
    public static RequestContextSnapshot capture(ContextView context) {
        Objects.requireNonNull(context, "context must not be null");
        String correlationId = context.getOrDefault(CorrelationIdWebFilter.CORRELATION_ID_CONTEXT_KEY, null);
        Map<String, List<String>> inboundHeaders = context.getOrDefault(
                InboundHeadersWebFilter.INBOUND_HEADERS_CONTEXT_KEY, Map.of());
        if (correlationId == null && CollectionUtils.isEmpty(inboundHeaders)) {
            return EMPTY;
        }
        return new RequestContextSnapshot(correlationId, inboundHeaders);
    }

    /**
     * Restores this snapshot into another Reactor context.
     *
     * <p>Intended for use as {@code publisher.contextWrite(snapshot::writeTo)}.
     */
    public Context writeTo(Context context) {
        Objects.requireNonNull(context, "context must not be null");
        Context updated = context;
        if (correlationId != null) {
            updated = updated.put(CorrelationIdWebFilter.CORRELATION_ID_CONTEXT_KEY, correlationId);
        }
        if (!inboundHeaders.isEmpty()) {
            updated = updated.put(InboundHeadersWebFilter.INBOUND_HEADERS_CONTEXT_KEY, inboundHeaders);
        }
        return updated;
    }

    /**
     * Returns {@code true} when this snapshot has no values to restore.
     */
    public boolean isEmpty() {
        return correlationId == null && inboundHeaders.isEmpty();
    }

    private static Map<String, List<String>> immutableHeaders(Map<String, List<String>> headers) {
        if (headers == null || headers.isEmpty()) {
            return Map.of();
        }
        Map<String, List<String>> copy = new LinkedHashMap<>();
        headers.forEach((name, values) -> copy.put(name, List.copyOf(values != null ? values : List.of())));
        return Collections.unmodifiableMap(copy);
    }
}
