package io.github.huynhngochuyhoang.httpstarter.core;

import org.junit.jupiter.api.Test;
import reactor.util.context.Context;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequestContextContributorTest {

    @Test
    void builtInContributorsCaptureImmutableValues() {
        Map<String, List<String>> inboundHeaders = new java.util.LinkedHashMap<>();
        inboundHeaders.put("X-Request-Id", new ArrayList<>(List.of("req-7")));

        Map<String, Object> snapshot = RequestContext.capture(
                Context.of(
                        RequestContext.CORRELATION_ID_CONTEXT_KEY, "cid-7",
                        RequestContext.INBOUND_HEADERS_CONTEXT_KEY, inboundHeaders),
                RequestContext.defaultContributors());

        inboundHeaders.get("X-Request-Id").add("mutated");
        inboundHeaders.put("X-After", List.of("after"));

        assertThat(snapshot).containsEntry(RequestContext.CORRELATION_ID_CONTEXT_KEY, "cid-7");
        @SuppressWarnings("unchecked")
        Map<String, List<String>> capturedHeaders =
                (Map<String, List<String>>) snapshot.get(RequestContext.INBOUND_HEADERS_CONTEXT_KEY);
        assertThat(capturedHeaders).containsOnlyKeys("X-Request-Id");
        assertThat(capturedHeaders.get("X-Request-Id")).containsExactly("req-7");
        assertThatThrownBy(() -> capturedHeaders.put("X-New", List.of("new")))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> capturedHeaders.get("X-Request-Id").add("mutated"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void restoreUsesDeterministicContributorOrder() {
        RequestContextContributor<String> second = orderedContributor("second", 20);
        RequestContextContributor<String> first = orderedContributor("first", 10);
        Map<String, Object> snapshot = Map.of("first", "ignored", "second", "ignored");

        Context restored = RequestContext.restore(Context.empty(), snapshot, List.of(second, first));

        assertThat(restored.<List<String>>get("order")).containsExactly("first", "second");
    }

    @Test
    void absentOptionalContributorsDoNotChangeBehavior() {
        assertThat(RequestContext.capture(Context.empty(), List.of())).isEmpty();
        assertThat(RequestContext.restore(Context.empty(), Map.of("missing", "value"), List.of()).isEmpty()).isTrue();
    }

    @Test
    void builtInContributorsRestoreOldPublicContextKeys() {
        Map<String, Object> snapshot = Map.of(
                RequestContext.CORRELATION_ID_CONTEXT_KEY, "cid-7",
                RequestContext.INBOUND_HEADERS_CONTEXT_KEY, Map.of("X-Request-Id", List.of("req-7")));

        Context restored = RequestContext.restore(Context.empty(), snapshot, RequestContext.defaultContributors());

        assertThat(RequestContext.correlationId(restored)).contains("cid-7");
        assertThat(RequestContext.inboundHeaders(restored))
                .containsEntry("X-Request-Id", List.of("req-7"));
    }

    private static RequestContextContributor<String> orderedContributor(String key, int order) {
        return new RequestContextContributor<>() {
            @Override
            public String key() {
                return key;
            }

            @Override
            public int order() {
                return order;
            }

            @Override
            public Optional<String> capture(reactor.util.context.ContextView context) {
                return Optional.empty();
            }

            @Override
            public Context restore(Context context, String value) {
                List<String> orderValues = context.getOrDefault("order", List.of());
                List<String> copy = new ArrayList<>(orderValues);
                copy.add(key);
                return context.put("order", List.copyOf(copy));
            }
        };
    }
}
