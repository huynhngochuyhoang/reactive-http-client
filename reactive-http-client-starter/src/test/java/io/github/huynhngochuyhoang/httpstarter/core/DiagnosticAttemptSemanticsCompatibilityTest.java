package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import io.github.huynhngochuyhoang.httpstarter.exception.ErrorCategory;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientObserverEvent;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DiagnosticAttemptSemanticsCompatibilityTest {

    @SuppressWarnings("deprecation")
    @Test
    void observerCompatibilityConstructorsKeepEstablishedAttemptDefaults() {
        Throwable failure = new IllegalStateException("failure");
        HttpClientObserverEvent original = new HttpClientObserverEvent(
                "client", "api", "GET", "/items", 503, 12, failure, null, null);
        HttpClientObserverEvent categorized = new HttpClientObserverEvent(
                "client", "api", "GET", "/items", 503, 12, failure,
                ErrorCategory.SERVER_ERROR, null, null);
        HttpClientObserverEvent attempted = new HttpClientObserverEvent(
                "client", "api", "GET", "/items", 503, 12, failure,
                ErrorCategory.SERVER_ERROR, null, null, 3);
        HttpClientObserverEvent sized = new HttpClientObserverEvent(
                "client", "api", "GET", "/items", 503, 12, failure,
                ErrorCategory.SERVER_ERROR, null, null, 4, 10, 20);
        HttpClientObserverEvent addressed = new HttpClientObserverEvent(
                "client", "api", "GET", "/items", 503, 12, failure,
                ErrorCategory.SERVER_ERROR, null, null, 5, 10, 20,
                "example.test", 8443);

        assertThat(original.getAttemptCount()).isEqualTo(1);
        assertThat(original.getErrorCategory()).isNull();
        assertThat(categorized.getAttemptCount()).isEqualTo(1);
        assertThat(attempted.getAttemptCount()).isEqualTo(3);
        assertThat(attempted.getRequestBytes()).isEqualTo(HttpClientObserverEvent.UNKNOWN_SIZE);
        assertThat(attempted.getResponseBytes()).isEqualTo(HttpClientObserverEvent.UNKNOWN_SIZE);
        assertThat(sized.getAttemptCount()).isEqualTo(4);
        assertThat(sized.getServerAddress()).isNull();
        assertThat(sized.getServerPort()).isNull();
        assertThat(addressed.getAttemptCount()).isEqualTo(5);
        assertThat(addressed.getServerAddress()).isEqualTo("example.test");
        assertThat(addressed.getServerPort()).isEqualTo(8443);
        assertThat(addressed.getRequestUrl()).isNull();
        assertThat(addressed.getRequestHeaders()).isEmpty();
    }

    @Test
    void exchangeLogCompatibilityConstructorsKeepEstablishedAttemptDefaults() {
        Throwable failure = new IllegalStateException("failure");
        HttpExchangeLogContext withUrl = new HttpExchangeLogContext(
                "client", "GET", "/items", URI.create("https://example.test/items"),
                Map.of(), Map.of(), Map.of(), Map.of(), null, 503, Map.of(), null,
                12, failure, ReactiveHttpClientProperties.LogPreset.METADATA_ONLY);
        HttpExchangeLogContext withPreset = new HttpExchangeLogContext(
                "client", "GET", "/items", Map.of(), Map.of(), Map.of(), Map.of(),
                null, 503, Map.of(), null, 12, failure,
                ReactiveHttpClientProperties.LogPreset.HEADERS);
        HttpExchangeLogContext original = new HttpExchangeLogContext(
                "client", "GET", "/items", Map.of(), Map.of(), Map.of(), Map.of(),
                null, 503, Map.of(), null, 12, failure);
        HttpExchangeLogContext canonical = new HttpExchangeLogContext(
                "client", "GET", "/items", URI.create("https://example.test/items"),
                Map.of(), Map.of(), Map.of(), Map.of(), null, 503, Map.of(), null,
                12, 4, failure, ReactiveHttpClientProperties.LogPreset.METADATA_ONLY);

        assertThat(withUrl.subscriptionAttemptCount()).isEqualTo(1);
        assertThat(withUrl.requestUrl()).hasToString("https://example.test/items");
        assertThat(withPreset.subscriptionAttemptCount()).isEqualTo(1);
        assertThat(withPreset.requestUrl()).isNull();
        assertThat(withPreset.logPreset()).isEqualTo(ReactiveHttpClientProperties.LogPreset.HEADERS);
        assertThat(original.subscriptionAttemptCount()).isEqualTo(1);
        assertThat(original.logPreset()).isEqualTo(ReactiveHttpClientProperties.LogPreset.METADATA_ONLY);
        assertThat(canonical.subscriptionAttemptCount()).isEqualTo(4);
    }

    @Test
    void lifecycleAttemptNumberRemainsTheCurrentSubscriptionAttempt() {
        ReactiveHttpClientLifecycleContext context = new ReactiveHttpClientLifecycleContext(
                "client", "api", "GET", "/items", Map.of(), Map.of(),
                Map.of("X-Test", "value"), null, URI.create("https://example.test/items"),
                200, null, 3);

        assertThat(context.attemptNumber()).isEqualTo(3);
        assertThat(context.requestUrl()).hasToString("https://example.test/items");
        assertThat(context.headers()).containsEntry("X-Test", "value");
    }
}
