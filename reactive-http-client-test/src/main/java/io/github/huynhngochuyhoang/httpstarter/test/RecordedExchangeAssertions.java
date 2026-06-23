package io.github.huynhngochuyhoang.httpstarter.test;

import org.assertj.core.api.AbstractAssert;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Arrays;
import java.util.List;

/**
 * Fluent AssertJ assertions for {@link RecordedExchange}.
 *
 * <pre>{@code
 * RecordedExchangeAssertions.assertThat(mock.lastExchange())
 *     .hasMethod(HttpMethod.GET)
 *     .hasPath("/users/42")
 *     .hasStatusCode(200);
 * }</pre>
 */
public final class RecordedExchangeAssertions {

    private static final String REDACTED = "[REDACTED]";

    private RecordedExchangeAssertions() {}

    public static RecordedExchangeAssert assertThat(RecordedExchange actual) {
        return new RecordedExchangeAssert(actual);
    }

    public static MockReactiveHttpClientAssert assertThat(MockReactiveHttpClient<?> actual) {
        return new MockReactiveHttpClientAssert(actual);
    }

    public static final class RecordedExchangeAssert
            extends AbstractAssert<RecordedExchangeAssert, RecordedExchange> {

        RecordedExchangeAssert(RecordedExchange actual) {
            super(actual, RecordedExchangeAssert.class);
        }

        public RecordedExchangeAssert hasMethod(HttpMethod expected) {
            isNotNull();
            if (!expected.equals(actual.method())) {
                failWithMessage("expected method <%s> but was <%s>", expected, actual.method());
            }
            return myself;
        }

        public RecordedExchangeAssert hasMethod(String expected) {
            return hasMethod(HttpMethod.valueOf(expected));
        }

        public RecordedExchangeAssert hasPath(String expected) {
            isNotNull();
            if (!expected.equals(actual.uri().getPath())) {
                failWithMessage("expected path <%s> but was <%s>", expected, actual.uri().getPath());
            }
            return myself;
        }

        public RecordedExchangeAssert hasQueryParam(String name, String value) {
            return hasQueryParamValues(name, value);
        }

        public RecordedExchangeAssert hasQueryParamValues(String name, String... values) {
            isNotNull();
            List<String> actualValues = queryValues(name);
            List<String> expected = Arrays.asList(values);
            if (!expected.equals(actualValues)) {
                failWithMessage("expected query parameter <%s> to have values <%s> but was <%s>",
                        name, expected, actualValues);
            }
            return myself;
        }

        public RecordedExchangeAssert doesNotHaveQueryParam(String name) {
            isNotNull();
            if (queryValues(name) != null) {
                failWithMessage("expected query parameter <%s> to be absent but was <%s>", name, queryValues(name));
            }
            return myself;
        }

        public RecordedExchangeAssert hasHeader(String name, String value) {
            return hasHeaderValues(name, value);
        }

        public RecordedExchangeAssert hasHeaderValues(String name, String... values) {
            isNotNull();
            List<String> actualValues = actual.headers().get(name);
            List<String> expected = Arrays.asList(values);
            if (!expected.equals(actualValues)) {
                failWithMessage("expected header <%s> to have values <%s> but was <%s>",
                        name, expected, actualValues);
            }
            return myself;
        }

        public RecordedExchangeAssert hasRedactedHeader(String name) {
            return hasHeaderValues(name, REDACTED);
        }

        /** Asserts Authorization is present without exposing its value on failure. */
        public RecordedExchangeAssert hasAuthorizationHeader() {
            isNotNull();
            List<String> values = actual.headers().get(HttpHeaders.AUTHORIZATION);
            if (values == null || values.stream().noneMatch(value -> value != null && !value.isBlank())) {
                failWithMessage("expected Authorization header to be present but was absent");
            }
            return myself;
        }

        /** Asserts Authorization is absent without exposing its value on failure. */
        public RecordedExchangeAssert doesNotHaveAuthorizationHeader() {
            isNotNull();
            if (actual.headers().containsKey(HttpHeaders.AUTHORIZATION)) {
                failWithMessage("expected Authorization header to be absent but was <%s>", REDACTED);
            }
            return myself;
        }

        public RecordedExchangeAssert hasIdempotencyKey() {
            isNotNull();
            if (actual.idempotencyKey() == null || actual.idempotencyKey().isBlank()) {
                failWithMessage("expected Idempotency-Key header to be present but was <%s>",
                        actual.headers().get("Idempotency-Key"));
            }
            return myself;
        }

        public RecordedExchangeAssert hasIdempotencyKey(String expected) {
            return hasHeader("Idempotency-Key", expected);
        }

        public RecordedExchangeAssert doesNotHaveIdempotencyKey() {
            return doesNotHaveHeader("Idempotency-Key");
        }

        public RecordedExchangeAssert hasCapturedCorrelationId(String expected) {
            isNotNull();
            if (!expected.equals(actual.correlationId())) {
                failWithMessage("expected captured correlation ID <%s> but was <%s>",
                        expected, actual.correlationId());
            }
            return myself;
        }

        public RecordedExchangeAssert doesNotHaveCapturedCorrelationId() {
            isNotNull();
            if (actual.correlationId() != null) {
                failWithMessage("expected captured correlation ID to be absent but was <%s>",
                        actual.correlationId());
            }
            return myself;
        }

        public RecordedExchangeAssert hasInboundHeader(String name, String value) {
            return hasInboundHeaderValues(name, value);
        }

        public RecordedExchangeAssert hasInboundHeaderValues(String name, String... values) {
            isNotNull();
            List<String> actualValues = actual.inboundHeaders().get(name);
            List<String> expected = Arrays.asList(values);
            if (!expected.equals(actualValues)) {
                failWithMessage("expected captured inbound header <%s> to have values <%s> but was <%s>",
                        name, expected, actualValues);
            }
            return myself;
        }

        public RecordedExchangeAssert hasRedactedInboundHeader(String name) {
            return hasInboundHeaderValues(name, REDACTED);
        }

        public RecordedExchangeAssert doesNotHaveInboundHeader(String name) {
            isNotNull();
            if (actual.inboundHeaders().containsKey(name)) {
                failWithMessage("expected captured inbound header <%s> to be absent but was <%s>",
                        name, actual.inboundHeaders().get(name));
            }
            return myself;
        }

        public RecordedExchangeAssert doesNotHaveHeader(String name) {
            isNotNull();
            if (actual.headers().containsKey(name)) {
                failWithMessage("expected header <%s> to be absent but was <%s>", name, actual.headers().get(name));
            }
            return myself;
        }

        public RecordedExchangeAssert hasBody(String expected) {
            isNotNull();
            if (!expected.equals(actual.bodyAsString())) {
                failWithMessage("expected body <%s> but was <%s>", expected, actual.bodyAsString());
            }
            return myself;
        }

        public RecordedExchangeAssert bodyContains(String expected) {
            isNotNull();
            if (!actual.bodyAsString().contains(expected)) {
                failWithMessage("expected body to contain <%s> but was <%s>", expected, actual.bodyAsString());
            }
            return myself;
        }

        public RecordedExchangeAssert hasStatusCode(int expected) {
            isNotNull();
            if (actual.statusCodeValue() != expected) {
                failWithMessage("expected status code <%s> but was <%s>", expected, actual.statusCodeValue());
            }
            return myself;
        }

        private List<String> queryValues(String name) {
            return UriComponentsBuilder.fromUri(actual.uri()).build().getQueryParams().get(name);
        }
    }

    public static final class MockReactiveHttpClientAssert
            extends AbstractAssert<MockReactiveHttpClientAssert, MockReactiveHttpClient<?>> {

        MockReactiveHttpClientAssert(MockReactiveHttpClient<?> actual) {
            super(actual, MockReactiveHttpClientAssert.class);
        }

        public MockReactiveHttpClientAssert hasAttemptCount(int expected) {
            isNotNull();
            int actualCount = actual.exchanges().size();
            if (actualCount != expected) {
                failWithMessage("expected attempt count <%s> but was <%s>", expected, actualCount);
            }
            return myself;
        }

        public MockReactiveHttpClientAssert hasAttemptCount(HttpMethod method, String path, int expected) {
            isNotNull();
            long actualCount = actual.exchanges().stream()
                    .filter(exchange -> method.equals(exchange.method()) && path.equals(exchange.uri().getPath()))
                    .count();
            if (actualCount != expected) {
                failWithMessage("expected attempt count for <%s %s> to be <%s> but was <%s>",
                        method, path, expected, actualCount);
            }
            return myself;
        }
    }

}
