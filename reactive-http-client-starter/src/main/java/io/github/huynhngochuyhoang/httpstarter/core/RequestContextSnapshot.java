package io.github.huynhngochuyhoang.httpstarter.core;

import org.springframework.util.CollectionUtils;
import reactor.util.context.Context;
import reactor.util.context.ContextView;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable snapshot of starter-owned request context values that can be carried
 * explicitly across asynchronous boundaries such as sinks, queues, or callbacks.
 */
public record RequestContextSnapshot(String correlationId, Map<String, List<String>> inboundHeaders) {

    private static final RequestContextSnapshot EMPTY = new RequestContextSnapshot(null, Map.of());

    public RequestContextSnapshot {
        inboundHeaders = RequestContext.immutableHeaders(inboundHeaders);
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
        String correlationId = RequestContext.correlationId(context).orElse(null);
        Map<String, List<String>> inboundHeaders = RequestContext.inboundHeaders(context);
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
            updated = RequestContext.withCorrelationId(updated, correlationId);
        }
        if (!inboundHeaders.isEmpty()) {
            updated = RequestContext.withInboundHeaders(updated, inboundHeaders);
        }
        return updated;
    }

    /**
     * Returns {@code true} when this snapshot has no values to restore.
     */
    public boolean isEmpty() {
        return correlationId == null && inboundHeaders.isEmpty();
    }

}
