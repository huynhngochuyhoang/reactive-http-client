package io.github.huynhngochuyhoang.httpstarter.test;

import io.github.huynhngochuyhoang.httpstarter.annotation.*;
import io.github.huynhngochuyhoang.httpstarter.core.RequestContext;
import io.github.huynhngochuyhoang.httpstarter.core.RequestContextSnapshot;
import io.github.huynhngochuyhoang.httpstarter.exception.ErrorCategory;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end sanity tests for the test-helper module: verify
 * {@link MockReactiveHttpClient} drives a {@code @ReactiveHttpClient} proxy
 * through canned responses and {@link ErrorCategoryAssertions} interprets
 * library errors.
 */
class MockReactiveHttpClientTest {

    interface SampleClient {
        @GET("/users/{id}")
        Mono<String> getUser(@PathVar("id") long id);

        @POST("/users")
        Mono<String> createUser(@Body String json);

        @GET("/search")
        Mono<String> search(@QueryParam("tag") List<String> tags,
                            @QueryParam("page") int page,
                            @HeaderParam("Authorization") String authorization);

        @POST("/events")
        Mono<String> sendEvent(@Body String body);

        @POST("/payments")
        @IdempotencyKey
        Mono<String> createPayment(@Body String json);

        @POST("/payments/manual")
        Mono<String> createPaymentWithKey(@Body String json, @IdempotencyKey String idempotencyKey);
    }

    @Test
    void servesRegisteredResponseAndRecordsTheExchange() {
        MockReactiveHttpClient<SampleClient> mock = MockReactiveHttpClient.forClient(SampleClient.class)
                .baseUrl("http://mock.local")
                .respondTo(HttpMethod.GET, "/users/42",
                        ex -> MockReactiveHttpClient.json(200, "alice"))
                .build();

        StepVerifier.create(mock.proxy().getUser(42))
                .expectNext("alice")
                .verifyComplete();

        assertThat(mock.exchanges()).hasSize(1);
        RecordedExchange recorded = mock.lastExchange();
        RecordedExchangeAssertions.assertThat(recorded)
                .hasMethod(HttpMethod.GET)
                .hasPath("/users/42")
                .hasStatusCode(200);
    }

    @Test
    void recordsExchangeWhenHandlerThrows() {
        MockReactiveHttpClient<SampleClient> mock = MockReactiveHttpClient.forClient(SampleClient.class)
                .respondTo(HttpMethod.GET, "/users/42",
                        ex -> { throw new IllegalStateException("handler failed"); })
                .build();

        StepVerifier.create(mock.proxy().getUser(42))
                .expectErrorMessage("handler failed")
                .verify();

        assertThat(mock.exchanges()).hasSize(1);
        RecordedExchangeAssertions.assertThat(mock.lastExchange())
                .hasMethod(HttpMethod.GET)
                .hasPath("/users/42");
    }

    @Test
    void capturesPostBodyForAssertion() {
        MockReactiveHttpClient<SampleClient> mock = MockReactiveHttpClient.forClient(SampleClient.class)
                .respondTo(HttpMethod.POST, "/users",
                        ex -> MockReactiveHttpClient.json(201, "\"ok\""))
                .build();

        mock.proxy().createUser("{\"name\":\"alice\"}").block();

        RecordedExchangeAssertions.assertThat(mock.lastExchange())
                .bodyContains("\"name\":\"alice\"")
                .hasStatusCode(201);
    }

    @Test
    void recordedExchangeAssertionsCoverQueryHeadersAndRedactionMarker() {
        MockReactiveHttpClient<SampleClient> mock = MockReactiveHttpClient.forClient(SampleClient.class)
                .respondTo(HttpMethod.GET, "/search",
                        ex -> MockReactiveHttpClient.json(202, "\"ok\""))
                .build();

        mock.proxy().search(List.of("public", "stable"), 2, "[REDACTED]").block();

        RecordedExchangeAssertions.assertThat(mock.lastExchange())
                .hasMethod("GET")
                .hasPath("/search")
                .hasQueryParamValues("tag", "public", "stable")
                .hasQueryParam("page", "2")
                .doesNotHaveQueryParam("missing")
                .hasRedactedHeader("Authorization")
                .doesNotHaveHeader("X-Missing")
                .hasStatusCode(202);
    }

    @Test
    void recordsRestoredRequestContextAcrossSinkHandoff() {
        Sinks.Many<EventEnvelope> sink = Sinks.many().unicast().onBackpressureBuffer();
        MockReactiveHttpClient<SampleClient> mock = MockReactiveHttpClient.forClient(SampleClient.class)
                .respondTo(HttpMethod.POST, "/events",
                        ex -> MockReactiveHttpClient.json(202, "\"accepted\""))
                .build();

        StepVerifier.create(sink.asFlux()
                        .take(1)
                        .flatMap(envelope -> mock.proxy().sendEvent("created")
                                .contextWrite(envelope.context()::writeTo)))
                .then(() -> Mono.deferContextual(ctx -> {
                            sink.tryEmitNext(new EventEnvelope(RequestContextSnapshot.capture(ctx))).orThrow();
                            return Mono.empty();
                        })
                        .contextWrite(ctx -> RequestContext.withInboundHeaders(
                                RequestContext.withCorrelationId(ctx, "cid-7"),
                                Map.of(
                                        "X-Request-Id", List.of("req-7"),
                                        "Authorization", List.of("[REDACTED]"))))
                        .block())
                .expectNext("\"accepted\"")
                .verifyComplete();

        RecordedExchangeAssertions.assertThat(mock.lastExchange())
                .hasCapturedCorrelationId("cid-7")
                .hasInboundHeader("X-Request-Id", "req-7")
                .hasRedactedInboundHeader("Authorization")
                .doesNotHaveInboundHeader("X-Missing")
                .hasStatusCode(202);
    }

    @Test
    void recordedExchangeAssertionsCoverIdempotencyHeaders() {
        MockReactiveHttpClient<SampleClient> mock = MockReactiveHttpClient.forClient(SampleClient.class)
                .respondTo(HttpMethod.POST, "/payments",
                        ex -> MockReactiveHttpClient.json(201, "\"created\""))
                .respondTo(HttpMethod.POST, "/payments/manual",
                        ex -> MockReactiveHttpClient.json(201, "\"created\""))
                .respondTo(HttpMethod.POST, "/users",
                        ex -> MockReactiveHttpClient.json(201, "\"ok\""))
                .build();

        mock.proxy().createPayment("{\"amount\":10}").block();
        RecordedExchangeAssertions.assertThat(mock.lastExchange())
                .hasIdempotencyKey();

        mock.proxy().createPaymentWithKey("{\"amount\":20}", "idem-20").block();
        RecordedExchangeAssertions.assertThat(mock.lastExchange())
                .hasIdempotencyKey("idem-20");

        mock.proxy().createUser("{\"name\":\"alice\"}").block();
        RecordedExchangeAssertions.assertThat(mock.lastExchange())
                .doesNotHaveIdempotencyKey();
    }

    @Test
    void retryHelperRecordsAttemptsAndKeepsGeneratedIdempotencyKeyStable() {
        AtomicInteger served = new AtomicInteger();
        MockReactiveHttpClient<SampleClient> mock = MockReactiveHttpClient.forClient(SampleClient.class)
                .retry(2, "POST")
                .respondTo(HttpMethod.POST, "/payments", ex -> {
                    if (served.incrementAndGet() == 1) {
                        return MockReactiveHttpClient.json(503, "{\"error\":\"temporary\"}");
                    }
                    return MockReactiveHttpClient.json(201, "\"created\"");
                })
                .build();

        StepVerifier.create(mock.proxy().createPayment("{\"amount\":10}"))
                .expectNext("\"created\"")
                .verifyComplete();

        RecordedExchangeAssertions.assertThat(mock)
                .hasAttemptCount(2)
                .hasAttemptCount(HttpMethod.POST, "/payments", 2);
        RecordedExchangeAssertions.assertThat(mock.exchanges().get(0))
                .hasIdempotencyKey();
        RecordedExchangeAssertions.assertThat(mock.exchanges().get(1))
                .hasIdempotencyKey(mock.exchanges().get(0).idempotencyKey());
    }

    @Test
    void recordsEmptyContextWhenNoStarterContextIsPresent() {
        MockReactiveHttpClient<SampleClient> mock = MockReactiveHttpClient.forClient(SampleClient.class)
                .respondTo(HttpMethod.GET, "/users/42",
                        ex -> MockReactiveHttpClient.json(200, "alice"))
                .build();

        mock.proxy().getUser(42).block();

        RecordedExchangeAssertions.assertThat(mock.lastExchange())
                .doesNotHaveCapturedCorrelationId()
                .doesNotHaveInboundHeader("X-Request-Id");
    }

    @Test
    void unmatchedRequestFallsThroughToFallbackResponse() {
        MockReactiveHttpClient<SampleClient> mock = MockReactiveHttpClient.forClient(SampleClient.class).build();

        ErrorCategoryAssertions.assertThatFails(mock.proxy().getUser(99))
                .hasErrorCategory(ErrorCategory.CLIENT_ERROR)
                .hasStatusCode(404);

        assertThat(mock.exchanges()).hasSize(1);
    }

    @Test
    void errorCategoryAssertionRecognises429() {
        MockReactiveHttpClient<SampleClient> mock = MockReactiveHttpClient.forClient(SampleClient.class)
                .respondTo(HttpMethod.GET, "/users/7",
                        ex -> MockReactiveHttpClient.json(429, "{\"reason\":\"slow down\"}"))
                .build();

        ErrorCategoryAssertions.assertThatFails(mock.proxy().getUser(7))
                .hasStatusCode(429)
                .hasErrorCategory(ErrorCategory.RATE_LIMITED);
    }

    @Test
    void errorCategoryAssertionRecognises5xxAsServerError() {
        MockReactiveHttpClient<SampleClient> mock = MockReactiveHttpClient.forClient(SampleClient.class)
                .respondTo(HttpMethod.GET, "/users/8",
                        ex -> MockReactiveHttpClient.json(503, "{\"err\":\"down\"}"))
                .build();

        ErrorCategoryAssertions.assertThatFails(mock.proxy().getUser(8))
                .hasStatusCode(503)
                .hasErrorCategory(ErrorCategory.SERVER_ERROR);
    }

    private record EventEnvelope(RequestContextSnapshot context) {
    }
}
