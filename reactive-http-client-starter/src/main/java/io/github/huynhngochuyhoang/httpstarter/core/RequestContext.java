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

    private static final RequestContextContributor<String> CORRELATION_ID_CONTRIBUTOR =
            new RequestContextContributor<>() {
                @Override
                public String key() {
                    return CORRELATION_ID_CONTEXT_KEY;
                }

                @Override
                public int order() {
                    return 0;
                }

                @Override
                public Optional<String> capture(ContextView context) {
                    return correlationId(context);
                }

                @Override
                public Context restore(Context context, String value) {
                    return withCorrelationId(context, value);
                }
            };

    private static final RequestContextContributor<Map<String, List<String>>> INBOUND_HEADERS_CONTRIBUTOR =
            new RequestContextContributor<>() {
                @Override
                public String key() {
                    return INBOUND_HEADERS_CONTEXT_KEY;
                }

                @Override
                public int order() {
                    return 100;
                }

                @Override
                public Optional<Map<String, List<String>>> capture(ContextView context) {
                    Map<String, List<String>> headers = immutableHeaders(inboundHeaders(context));
                    return headers.isEmpty() ? Optional.empty() : Optional.of(headers);
                }

                @Override
                public Context restore(Context context, Map<String, List<String>> value) {
                    return withInboundHeaders(context, value);
                }
            };

    private RequestContext() {
    }

    /**
     * Built-in contributor for the starter correlation ID.
     */
    public static RequestContextContributor<String> correlationIdContributor() {
        return CORRELATION_ID_CONTRIBUTOR;
    }

    /**
     * Built-in contributor for the filtered inbound header snapshot.
     */
    public static RequestContextContributor<Map<String, List<String>>> inboundHeadersContributor() {
        return INBOUND_HEADERS_CONTRIBUTOR;
    }

    /**
     * Built-in contributors in deterministic restore order.
     */
    public static List<RequestContextContributor<?>> defaultContributors() {
        return List.of(CORRELATION_ID_CONTRIBUTOR, INBOUND_HEADERS_CONTRIBUTOR);
    }

    /**
     * Captures immutable values from all supplied contributors.
     */
    public static Map<String, Object> capture(ContextView context,
                                              Collection<? extends RequestContextContributor<?>> contributors) {
        Objects.requireNonNull(context, "context must not be null");
        if (contributors == null || contributors.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> snapshot = new LinkedHashMap<>();
        ordered(contributors).forEach(contributor -> captureOne(contributor, context, snapshot));
        return Collections.unmodifiableMap(snapshot);
    }

    /**
     * Restores captured values with contributors sorted by order, then key.
     */
    public static Context restore(Context context,
                                  Map<String, ?> snapshot,
                                  Collection<? extends RequestContextContributor<?>> contributors) {
        Objects.requireNonNull(context, "context must not be null");
        if (snapshot == null || snapshot.isEmpty() || contributors == null || contributors.isEmpty()) {
            return context;
        }
        Context updated = context;
        for (RequestContextContributor<?> contributor : ordered(contributors)) {
            if (snapshot.containsKey(contributor.key())) {
                updated = restoreOne(contributor, updated, snapshot.get(contributor.key()));
            }
        }
        return updated;
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

    private static List<RequestContextContributor<?>> ordered(
            Collection<? extends RequestContextContributor<?>> contributors) {
        List<RequestContextContributor<?>> ordered = new ArrayList<>();
        contributors.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingInt((RequestContextContributor<?> contributor) -> contributor.order())
                        .thenComparing(RequestContextContributor::key))
                .forEach(ordered::add);
        return ordered;
    }

    private static <T> void captureOne(RequestContextContributor<T> contributor,
                                       ContextView context,
                                       Map<String, Object> snapshot) {
        contributor.capture(context).ifPresent(value -> snapshot.put(contributor.key(), value));
    }

    @SuppressWarnings("unchecked")
    private static <T> Context restoreOne(RequestContextContributor<T> contributor, Context context, Object value) {
        return value != null ? contributor.restore(context, (T) value) : context;
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
