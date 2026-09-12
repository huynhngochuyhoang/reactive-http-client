package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import io.github.huynhngochuyhoang.httpstarter.filter.InboundHeadersWebFilter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
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

class RequestContextHeaderAccessTest {

    @Test
    void absenceAndEmptyListsReturnImmutableEmptyResults() {
        for (Context context : List.of(Context.empty(), raw(Map.of()),
                raw(Map.of("Other", List.of("value"))), raw(Map.of("X-Data", List.of())),
                raw(Map.of("X-Data", List.of(), "x-data", List.of())))) {
            assertThat(RequestContext.inboundHeaderValues(context, "x-data")).isEmpty();
            assertThat(RequestContext.inboundHeader(context, "x-data")).isEmpty();
            assertThatThrownBy(() -> RequestContext.inboundHeaderValues(context, "x-data").add("new"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "[REDACTED]", "  Keep Case  ", "a,b", "not-json", "\u0130\u0131\u212a"})
    void oneValueIsReturnedVerbatim(String value) {
        Context context = raw(Map.of("X-DATA", List.of(value), "x-data", List.of()));
        assertThat(RequestContext.inboundHeaderValues(context, "X-Data")).containsExactly(value);
        assertThat(RequestContext.inboundHeader(context, "X-Data")).contains(value);
    }

    @Test
    void aliasOrderAndValueOrderArePreservedWithoutExactCasePreference() {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        headers.put("x-data", List.of("first", "first"));
        headers.put("Unrelated", List.of("ignored"));
        headers.put("X-Data", List.of("second", "third"));
        headers.put("X-DATA", List.of());
        Context context = raw(headers);
        assertThat(RequestContext.inboundHeaderValues(context, "X-Data"))
                .containsExactly("first", "first", "second", "third");
        assertAmbiguous(context);

        headers.remove("x-data");
        headers.put("x-data", List.of("third", "second"));
        assertThat(RequestContext.inboundHeaderValues(context, "X-Data"))
                .containsExactly("second", "third", "third", "second");
        assertAmbiguous(context);
    }

    @Test
    void equalDuplicatesAreAmbiguousWithinOneEntryAndAcrossAliases() {
        assertAmbiguous(raw(Map.of("X-Data", List.of("same", "same"))));
        assertAmbiguous(raw(Map.of("X-Data", List.of("same"), "x-data", List.of("same"))));
    }

    @Test
    void allAsciiTokenCharactersAreAcceptedAndSeparatorsRejected() {
        String legal = "!#$%&'*+-.^_`|~0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
        for (int code = 0; code < 128; code++) {
            String name = "X" + (char) code;
            if (legal.indexOf(code) >= 0) {
                Context context = raw(Map.of(name, List.of("value")));
                assertThat(RequestContext.inboundHeaderValues(context, name)).containsExactly("value");
                assertThat(RequestContext.inboundHeader(context, name)).contains("value");
            } else {
                assertInvalidName(name);
            }
        }
        assertThat(RequestContext.inboundHeader(raw(Map.of(legal, List.of("value"))), legal.toLowerCase(Locale.ROOT)))
                .contains("value");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " X-Data", "X-Data ", ":authority", "X\r\nData", "\u0130", "\u0131", "\u212a", "X-\ud83d\ude00"})
    void invalidNamesNeverBecomeAbsenceOrEchoInput(String name) {
        assertInvalidName(name);
    }

    @Test
    void nullRequiredArgumentsFailWithFixedMessages() {
        assertThatThrownBy(() -> RequestContext.inboundHeaderValues(null, "X-Data"))
                .isInstanceOf(NullPointerException.class).hasMessage("context must not be null");
        assertThatThrownBy(() -> RequestContext.inboundHeader(null, "X-Data"))
                .isInstanceOf(NullPointerException.class).hasMessage("context must not be null");
        assertThatThrownBy(() -> RequestContext.inboundHeaderValues(Context.empty(), null))
                .isInstanceOf(NullPointerException.class).hasMessage("name must not be null");
        assertThatThrownBy(() -> RequestContext.inboundHeader(Context.empty(), null))
                .isInstanceOf(NullPointerException.class).hasMessage("name must not be null");
    }

    @Test
    @ResourceLock("java.util.Locale.default")
    void matchingDoesNotDependOnTurkishLocaleOrConflateDistinctTokens() {
        Locale previous = Locale.getDefault();
        Locale display = Locale.getDefault(Locale.Category.DISPLAY);
        Locale format = Locale.getDefault(Locale.Category.FORMAT);
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            Context context = raw(Map.of("X-ID", List.of("Upper I"), "X_ID", List.of("underscore")));
            assertThat(RequestContext.inboundHeader(context, "x-id")).contains("Upper I");
            assertThat(RequestContext.inboundHeader(context, "x_id")).contains("underscore");
            assertInvalidName("x-\u0131d");
            assertInvalidName("x-\u0130d");
            assertMalformed(Map.of("X-\u0130D", List.of("value")), "Inbound header map contains an invalid field name");
        } finally {
            Locale.setDefault(previous);
            Locale.setDefault(Locale.Category.DISPLAY, display);
            Locale.setDefault(Locale.Category.FORMAT, format);
        }
    }

    @ParameterizedTest(name = "malformed input {index}")
    @MethodSource("malformedInputs")
    void malformedInputFailsEvenOutsideRequestedName(Object input, String message) {
        assertMalformed(input, message);
    }

    static Stream<Arguments> malformedInputs() {
        Object opaque = new Object() {
            @Override
            public String toString() {
                throw new AssertionError("Application values must never be stringified");
            }
        };
        return Stream.of(
                Arguments.of(opaque, "Inbound header context value must be a map"),
                Arguments.of("private fixture", "Inbound header context value must be a map"),
                Arguments.of(List.of("value"), "Inbound header context value must be a map"),
                Arguments.of(entry(null, List.of()), "Inbound header map contains an invalid field name"),
                Arguments.of(entry(opaque, List.of()), "Inbound header map contains an invalid field name"),
                Arguments.of(entry("", List.of()), "Inbound header map contains an invalid field name"),
                Arguments.of(entry("X-\u212a", List.of()), "Inbound header map contains an invalid field name"),
                Arguments.of(entry("invalid " + "private".repeat(1000), List.of()), "Inbound header map contains an invalid field name"),
                Arguments.of(entry("Other", null), "Inbound header values must be a list"),
                Arguments.of(entry("Other", opaque), "Inbound header values must be a list"),
                Arguments.of(entry("Other", new String[]{"value"}), "Inbound header values must be a list"),
                Arguments.of(entry("Other", Arrays.asList("value", null)), "Inbound header list elements must be strings"),
                Arguments.of(entry("Other", List.of(opaque)), "Inbound header list elements must be strings"),
                Arguments.of(entry("Other", List.of(7)), "Inbound header list elements must be strings"));
    }

    @Test
    void rawReadCopiesReturnedValuesButDoesNotSanitizeOrFreezeTheSource() {
        List<String> values = new ArrayList<>(List.of("fixture-private-value"));
        Map<String, List<String>> headers = new LinkedHashMap<>();
        headers.put("Authorization", values);
        Context context = raw(headers);
        List<String> captured = RequestContext.inboundHeaderValues(context, "authorization");
        assertThat(RequestContext.inboundHeaders(context)).isSameAs(headers);
        assertThat(RequestContext.inboundHeader(context, "AUTHORIZATION")).contains("fixture-private-value");
        values.clear();
        headers.clear();
        assertThat(captured).containsExactly("fixture-private-value");
        assertThat(RequestContext.inboundHeader(context, "authorization")).isEmpty();
        assertThatThrownBy(() -> captured.set(0, "changed")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void snapshotConstructorWriterAndContributorsKeepLegacyKeysAndMergeSemantics() {
        List<String> values = new ArrayList<>(List.of("captured"));
        Map<String, List<String>> headers = new LinkedHashMap<>();
        headers.put("x-data", values);
        headers.put("Authorization", List.of("fixture-manual-value"));
        Context written = RequestContext.withInboundHeaders(Context.empty(), headers);
        RequestContextSnapshot constructed = new RequestContextSnapshot("cid", headers);
        RequestContextSnapshot captured = RequestContextSnapshot.capture(written);
        Map<String, Object> contributed = RequestContext.capture(written, RequestContext.defaultContributors());
        values.add("late");
        headers.clear();
        Context target = raw(Map.of("X-Data", List.of("worker"))).put("unrelated", "retained");
        for (Context context : List.of(written, constructed.writeTo(target), captured.writeTo(target),
                RequestContext.restore(target, contributed, RequestContext.defaultContributors()))) {
            assertThat(RequestContext.inboundHeader(context, "X-Data")).contains("captured");
            assertThat(RequestContext.inboundHeaders(context).get("X-Data")).isNull();
            assertThat(RequestContext.inboundHeaders(context)).containsOnlyKeys("x-data", "Authorization");
            assertThat(RequestContext.inboundHeader(context, "authorization")).contains("fixture-manual-value");
            assertThat(context.hasKey("inboundHeaders")).isTrue();
            assertThatThrownBy(() -> RequestContext.inboundHeaderValues(context, "X-Data").clear())
                    .isInstanceOf(UnsupportedOperationException.class);
        }
        assertThat(InboundHeadersWebFilter.INBOUND_HEADERS_CONTEXT_KEY).isEqualTo("inboundHeaders")
                .isEqualTo(RequestContext.INBOUND_HEADERS_CONTEXT_KEY);
        assertThat(RequestContextSnapshot.empty().writeTo(target)).isSameAs(target);
        assertThat(constructed.writeTo(target).<String>get("unrelated")).isEqualTo("retained");
    }

    @Test
    void filteredValuesStayRedactedAndOmittedWithoutAnotherValueSource() {
        var config = new ReactiveHttpClientProperties.InboundHeadersConfig();
        config.setAllowList(Set.of("X-Data", "Authorization"));
        config.setDenyList(Set.of("Authorization"));
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/")
                .header("x-data", "captured")
                .header("authorization", "fixture-private-value")
                .header("X-Dropped", "fixture-dropped-value"));
        StepVerifier.create(new InboundHeadersWebFilter(config).filter(exchange, ignored -> Mono.deferContextual(ctx -> {
                    assertThat(RequestContext.inboundHeader(ctx, "X-Data")).contains("captured");
                    assertThat(RequestContext.inboundHeader(ctx, "AUTHORIZATION")).contains("[REDACTED]");
                    assertThat(RequestContext.inboundHeaderValues(ctx, "x-dropped")).isEmpty();
                    assertThat(exchange.getRequest().getHeaders().getFirst("X-Dropped")).isNotNull();
                    return Mono.empty();
                })))
                .expectComplete().verify(Duration.ofSeconds(5));
    }

    @Test
    void requiredHeaderAndJsonParsingRemainApplicationDecisionsAtSubscriptionTime() {
        Mono<String> required = Mono.deferContextual(ctx -> Mono.just(RequestContext.inboundHeader(ctx, "X-Data")
                .orElseThrow(() -> new IllegalArgumentException("Required fixture header is absent"))));
        StepVerifier.create(required).expectErrorMessage("Required fixture header is absent").verify(Duration.ofSeconds(5));
        for (String value : List.of("", "not-json", "{\"fixture\":1}")) {
            StepVerifier.create(required.contextWrite(raw(Map.of("x-data", List.of(value)))))
                    .expectNext(value).expectComplete().verify(Duration.ofSeconds(5));
        }
        var json = JsonMapper.builder().build();
        assertThatThrownBy(() -> json.readTree(RequestContext.inboundHeader(raw(Map.of("x-data", List.of("not-json"))), "X-Data")
                .orElseThrow())).isInstanceOf(JacksonException.class);
        assertThat(json.readTree(RequestContext.inboundHeader(raw(Map.of("x-data", List.of("{\"fixture\":1}"))), "X-Data")
                .orElseThrow()).get("fixture").asInt()).isEqualTo(1);
    }

    private static Map<Object, Object> entry(Object key, Object value) {
        Map<Object, Object> entries = new LinkedHashMap<>();
        entries.put(key, value);
        return entries;
    }

    private static void assertMalformed(Object input, String message) {
        for (String name : List.of("X-Data", "Other", "Missing")) {
            Context context = raw(input);
            assertThatThrownBy(() -> RequestContext.inboundHeaderValues(context, name))
                    .isInstanceOf(IllegalStateException.class).hasMessage(message).hasNoCause();
            assertThatThrownBy(() -> RequestContext.inboundHeader(context, name))
                    .isInstanceOf(IllegalStateException.class).hasMessage(message).hasNoCause();
            if (input instanceof Map<?, ?> malformed) {
                Map<Object, Object> lateMalformed = entry("X-Data", List.of("first", "second"));
                lateMalformed.putAll(malformed);
                Context lateContext = raw(lateMalformed);
                assertThatThrownBy(() -> RequestContext.inboundHeaderValues(lateContext, "X-Data"))
                        .isInstanceOf(IllegalStateException.class).hasMessage(message).hasNoCause();
                assertThatThrownBy(() -> RequestContext.inboundHeader(lateContext, "X-Data"))
                        .isInstanceOf(IllegalStateException.class).hasMessage(message).hasNoCause();
            }
        }
    }

    private static void assertInvalidName(String name) {
        assertThatThrownBy(() -> RequestContext.inboundHeaderValues(Context.empty(), name))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("name must be a nonempty ASCII HTTP field-name token");
        assertThatThrownBy(() -> RequestContext.inboundHeader(Context.empty(), name))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("name must be a nonempty ASCII HTTP field-name token");
    }

    private static void assertAmbiguous(Context context) {
        assertThatThrownBy(() -> RequestContext.inboundHeader(context, "X-Data"))
                .isInstanceOf(IllegalStateException.class).hasMessage("Inbound header has multiple values").hasNoCause();
    }

    private static Context raw(Object input) {
        return Context.of(RequestContext.INBOUND_HEADERS_CONTEXT_KEY, input);
    }
}
