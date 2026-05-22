package io.github.huynhngochuyhoang.httpstarter.test;

import org.assertj.core.api.AbstractAssert;
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
}
