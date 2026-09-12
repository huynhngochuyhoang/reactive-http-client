package io.github.huynhngochuyhoang.httpstarter.filter;

import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import io.github.huynhngochuyhoang.httpstarter.core.RequestContext;
import io.github.huynhngochuyhoang.httpstarter.core.RequestContextSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.handler.DefaultWebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.util.context.Context;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.*;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InboundHeaderContextCharacterizationTest {

    private static final String CONTEXT_KEY = RequestContext.INBOUND_HEADERS_CONTEXT_KEY;
    private static final Duration VERIFY_TIMEOUT = Duration.ofSeconds(5);

    @ParameterizedTest
    @CsvSource({
            "x-fixture-data, X-Fixture-Data",
            "X-FiXtUrE-DaTa, x-fixture-data",
            "X-FIXTURE-DATA, X-Fixture-Data"
    })
    void exactMapLookupCanFailWhileServerHeaderAndContextEntryExist(String capturedName, String lookupName) {
        String json = "{\"fixture\":1}";
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/")
                .header(capturedName, json));
        var config = new ReactiveHttpClientProperties.InboundHeadersConfig();
        config.setAllowList(Set.of("X-Fixture-Data"));

        StepVerifier.create(new InboundHeadersWebFilter(config).filter(exchange, ex -> Mono.deferContextual(ctx -> {
            assertThat(ex.getRequest().getHeaders().getFirst(lookupName)).isEqualTo(json);
            assertThat(ctx.hasKey("inboundHeaders")).isTrue();
            assertThat(InboundHeadersWebFilter.INBOUND_HEADERS_CONTEXT_KEY).isEqualTo(CONTEXT_KEY);
            Map<String, List<String>> captured = RequestContext.inboundHeaders(ctx);
            assertThat(captured).containsOnlyKeys(capturedName);
            assertThat(captured.get(capturedName)).containsExactly(json);
            assertThat(captured.get(lookupName)).isNull();
            assertThatThrownBy(() -> captured.get(lookupName).getFirst())
                    .isInstanceOf(NullPointerException.class);
            assertThat(new JsonMapper().readTree(captured.get(capturedName).getFirst())
                    .get("fixture").asInt()).isEqualTo(1);

            Context restored = RequestContextSnapshot.capture(ctx).writeTo(Context.empty());
            assertThat(restored.hasKey(CONTEXT_KEY)).isTrue();
            assertThat(RequestContext.inboundHeaders(restored)).containsOnlyKeys(capturedName);
            assertThat(RequestContext.inboundHeaders(restored).get(lookupName)).isNull();
            return Mono.empty();
        }))).expectComplete().verify(VERIFY_TIMEOUT);
    }

    @Test
    void absentContextAndCapturedEmptyMapHaveDifferentKeyPresence() {
        assertThat(Context.empty().hasKey(CONTEXT_KEY)).isFalse();
        assertThat(RequestContext.inboundHeaders(Context.empty())).isEmpty();
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/"));
        StepVerifier.create(new InboundHeadersWebFilter().filter(exchange, ex -> Mono.deferContextual(ctx -> {
            assertThat(ctx.hasKey(CONTEXT_KEY)).isTrue();
            assertThat(RequestContext.inboundHeaders(ctx)).isEmpty();
            assertThat(RequestContextSnapshot.capture(ctx)).isSameAs(RequestContextSnapshot.empty());
            return Mono.empty();
        }))).expectComplete().verify(VERIFY_TIMEOUT);
    }

    @Test
    void captureDistinguishesEmptyListEmptyStringRepeatedValuesAndMalformedJson() {
        HttpHeaders headers = new HttpHeaders();
        headers.put("X-Empty-List", List.of());
        headers.put("X-Empty-String", List.of(""));
        headers.put("X-Multi", List.of("one", "one", "two"));
        headers.put("X-Invalid-Json", List.of("{invalid"));
        Map<String, List<String>> captured = new InboundHeadersWebFilter().filterHeaders(headers);

        assertThat(captured).containsKey("X-Empty-List");
        assertThat(captured.get("X-Empty-List")).isEmpty();
        assertThatThrownBy(() -> captured.get("X-Empty-List").getFirst())
                .isInstanceOf(NoSuchElementException.class);
        assertThat(captured.get("X-Empty-String")).containsExactly("");
        assertThat(captured.get("X-Empty-String").getFirst()).isEmpty();
        assertThat(captured.get("X-Multi")).containsExactly("one", "one", "two");
        assertThat(captured.get("X-Multi").getFirst()).isEqualTo("one");
        assertThat(captured.get("X-Invalid-Json")).containsExactly("{invalid");
        assertThatThrownBy(() -> new JsonMapper().readTree(captured.get("X-Invalid-Json").getFirst()))
                .isInstanceOf(JacksonException.class);
    }

    @Test
    void filteringSeparatesDroppedAndRedactedValuesFromMissingContext() {
        var config = new ReactiveHttpClientProperties.InboundHeadersConfig();
        config.setAllowList(Set.of("X-Selected", "X-Private"));
        config.setDenyList(Set.of("x-private", "x-dropped"));
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/")
                .header("x-selected", "fixture")
                .header("X-PRIVATE", "synthetic-private")
                .header("X-Dropped", "synthetic-dropped"));

        StepVerifier.create(new InboundHeadersWebFilter(config).filter(exchange, ex -> Mono.deferContextual(ctx -> {
            assertThat(ex.getRequest().getHeaders().containsHeader("X-Dropped")).isTrue();
            assertThat(ctx.hasKey(CONTEXT_KEY)).isTrue();
            Map<String, List<String>> captured = RequestContext.inboundHeaders(ctx);
            assertThat(captured).containsOnlyKeys("x-selected", "X-PRIVATE");
            assertThat(captured.get("X-PRIVATE")).containsExactly("[REDACTED]");
            assertThat(captured.get("X-Dropped")).isNull();
            var restored = RequestContextSnapshot.capture(ctx).writeTo(Context.empty());
            assertThat(RequestContext.inboundHeaders(restored)).isEqualTo(captured);
            return Mono.empty();
        }))).expectComplete().verify(VERIFY_TIMEOUT);
    }

    @Test
    void orderedApplicationAliasesRemainDistinctAndDefensivelyCopiedThroughRestore() {
        Map<String, List<String>> supplied = new LinkedHashMap<>();
        supplied.put("X-Fixture", new ArrayList<>(List.of("one", "one")));
        supplied.put("x-fixture", new ArrayList<>(List.of("two")));
        Context written = RequestContext.withInboundHeaders(Context.empty(), supplied);
        RequestContextSnapshot snapshot = RequestContextSnapshot.capture(written);
        supplied.get("X-Fixture").add("changed");
        supplied.put("X-FIXTURE", List.of("three"));

        Context target = RequestContext.withInboundHeaders(Context.of("unrelated", "retained"),
                Map.of("X-Old", List.of("old")));
        Context restored = snapshot.writeTo(target);
        Map<String, List<String>> captured = RequestContext.inboundHeaders(restored);
        assertThat(captured.keySet()).containsExactly("X-Fixture", "x-fixture");
        assertThat(captured.get("X-Fixture")).containsExactly("one", "one");
        assertThat(captured.get("x-fixture")).containsExactly("two");
        assertThat(captured.get("X-FIXTURE")).isNull();
        assertThat(restored.<String>get("unrelated")).isEqualTo("retained");
        assertThat(RequestContextSnapshot.empty().writeTo(target)).isSameAs(target);
        assertThat(RequestContext.inboundHeaders(RequestContextSnapshot.empty().writeTo(target)))
                .containsEntry("X-Old", List.of("old"));
        assertThatThrownBy(() -> captured.put("X-New", List.of("new")))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> captured.get("X-Fixture").add("new"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void manualContextWritesDoNotApplyIngressRedaction() {
        var config = new ReactiveHttpClientProperties.InboundHeadersConfig();
        config.setDenyList(Set.of("X-Private"));
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Private", "synthetic");
        assertThat(new InboundHeadersWebFilter(config).filterHeaders(headers))
                .containsEntry("X-Private", List.of("[REDACTED]"));
        Context manual = RequestContext.withInboundHeaders(Context.empty(), Map.of("X-Private", List.of("synthetic")));
        assertThat(RequestContextSnapshot.capture(manual).inboundHeaders())
                .containsEntry("X-Private", List.of("synthetic"));
    }

    @ParameterizedTest
    @MethodSource("nonMapValues")
    void wrongOuterContextTypeFailsAtTheBulkRead(Object supplied) {
        Context malformed = Context.of(CONTEXT_KEY, supplied);
        assertThat(malformed.hasKey(CONTEXT_KEY)).isTrue();
        StepVerifier.create(Mono.deferContextual(ctx -> Mono.just(RequestContext.inboundHeaders(ctx)))
                        .contextWrite(malformed))
                .expectError(ClassCastException.class).verify(VERIFY_TIMEOUT);
        assertThatThrownBy(() -> RequestContextSnapshot.capture(malformed))
                .isInstanceOf(ClassCastException.class);
    }

    static Stream<Object> nonMapValues() {
        return Stream.of("synthetic", List.of("synthetic"), 1);
    }

    @Test
    void wrongListTypeFailsAtEntryAccessAndSnapshotCapture() {
        Context malformed = Context.of(CONTEXT_KEY, Map.of("X-Fixture", "not-a-list"));
        Map<String, List<String>> captured = RequestContext.inboundHeaders(malformed);
        assertThat(captured).containsKey("X-Fixture");
        assertThatThrownBy(() -> captured.get("X-Fixture").getFirst())
                .isInstanceOf(ClassCastException.class);
        assertThatThrownBy(() -> RequestContextSnapshot.capture(malformed))
                .isInstanceOf(ClassCastException.class);
    }

    @Test
    void wrongElementTypeSurvivesSnapshotUntilUsedAsAString() {
        Context malformed = Context.of(CONTEXT_KEY, Map.of("X-Fixture", List.of(7)));
        Map<String, List<String>> captured = RequestContext.inboundHeaders(malformed);
        assertThat((Object) captured.get("X-Fixture").getFirst()).isEqualTo(7);
        assertThatThrownBy(() -> captured.get("X-Fixture").getFirst().length())
                .isInstanceOf(ClassCastException.class);
        var snapshot = RequestContextSnapshot.capture(malformed);
        assertThatThrownBy(() -> snapshot.inboundHeaders().get("X-Fixture").getFirst().length())
                .isInstanceOf(ClassCastException.class);
    }

    @Test
    void wrongKeyTypeIsInvisibleToNamedMapLookupButFailsSnapshotCapture() {
        Context malformed = Context.of(CONTEXT_KEY, Map.of(7, List.of("synthetic")));
        Map<String, List<String>> captured = RequestContext.inboundHeaders(malformed);
        assertThat(captured.size()).isEqualTo(1);
        assertThat(captured.get("X-Fixture")).isNull();
        assertThatThrownBy(() -> RequestContextSnapshot.capture(malformed))
                .isInstanceOf(ClassCastException.class);
    }

    @Test
    void nullListInRawMapDiffersFromDefensivelyWrittenEmptyList() {
        Map<String, List<String>> supplied = new LinkedHashMap<>();
        supplied.put("X-Fixture", null);
        Context raw = Context.of(CONTEXT_KEY, supplied);
        assertThat(RequestContext.inboundHeaders(raw)).containsKey("X-Fixture");
        assertThatThrownBy(() -> RequestContext.inboundHeaders(raw).get("X-Fixture").getFirst())
                .isInstanceOf(NullPointerException.class);
        assertThat(RequestContext.inboundHeaders(RequestContext.withInboundHeaders(Context.empty(), supplied))
                .get("X-Fixture")).isEmpty();
        assertThat(RequestContextSnapshot.capture(raw).inboundHeaders().get("X-Fixture")).isEmpty();
    }

    @Test
    void bulkReadOfRawApplicationMapDoesNotAddDefensiveCopying() {
        Map<String, List<String>> supplied = new LinkedHashMap<>();
        supplied.put("X-Fixture", new ArrayList<>(List.of("one")));
        Map<String, List<String>> captured = RequestContext.inboundHeaders(Context.of(CONTEXT_KEY, supplied));
        assertThat(captured).isSameAs(supplied);
        supplied.get("X-Fixture").add("two");
        assertThat(captured.get("X-Fixture")).containsExactly("one", "two");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void captureSeesOnlyRequestMutationsBeforeItsFilter(boolean mutateBeforeCapture) {
        WebFilter mutation = (exchange, chain) -> chain.filter(exchange.mutate()
                .request(request -> request.headers(headers -> headers.set("X-Fixture", "changed"))).build());
        WebFilter capture = new InboundHeadersWebFilter();
        List<WebFilter> filters = mutateBeforeCapture ? List.of(mutation, capture) : List.of(capture, mutation);
        var chain = new DefaultWebFilterChain(exchange -> Mono.deferContextual(ctx -> {
            assertThat(exchange.getRequest().getHeaders().getFirst("X-Fixture")).isEqualTo("changed");
            assertThat(ctx.hasKey(CONTEXT_KEY)).isTrue();
            assertThat(RequestContext.inboundHeaders(ctx).get("X-Fixture"))
                    .containsExactly(mutateBeforeCapture ? "changed" : "original");
            return Mono.empty();
        }), filters);

        StepVerifier.create(chain.filter(MockServerWebExchange.from(MockServerHttpRequest.get("/")
                        .header("X-Fixture", "original"))))
                .expectComplete().verify(VERIFY_TIMEOUT);
    }
}
