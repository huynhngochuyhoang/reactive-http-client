package io.github.huynhngochuyhoang.httpstarter.test;

import io.github.huynhngochuyhoang.httpstarter.annotation.*;
import io.github.huynhngochuyhoang.httpstarter.auth.AuthContext;
import io.github.huynhngochuyhoang.httpstarter.auth.AuthRequest;
import io.github.huynhngochuyhoang.httpstarter.auth.InvalidatableAuthProvider;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import io.github.huynhngochuyhoang.httpstarter.core.*;
import io.github.huynhngochuyhoang.httpstarter.exception.ErrorCategories;
import io.github.huynhngochuyhoang.httpstarter.exception.ErrorCategory;
import io.github.huynhngochuyhoang.httpstarter.exception.LogicalCallTimeoutException;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientFailureStage;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientObserverEvent;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.netty.handler.timeout.ReadTimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.ClientResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-end sanity tests for the test-helper module: verify
 * {@link MockReactiveHttpClient} drives a {@code @ReactiveHttpClient} proxy
 * through canned responses and {@link ErrorCategoryAssertions} interprets
 * library errors.
 */
class MockReactiveHttpClientTest {

    interface StrictUnsafeMockClient {
        @POST("/strict")
        Mono<String> create();
    }

    interface InvalidNestedResponseClient {
        @GET("/nested")
        Mono<ResponseEntity<Flux<String>>> nested();
    }

    interface InvalidParameterClient {
        @GET("/items")
        Mono<String> find(@QueryParam("item") @HeaderParam("X-Item") String item);
    }

    interface InvalidUriClient {
        @GET("https://user:secret-value@example.test/items")
        Mono<String> find();
    }

    interface ApiRefUriClient {
        @ApiRef("lookup")
        Mono<String> find(
                @PathVar("id") String id,
                @PathVar("template") String template,
                @QueryParam("omitted") String omitted);
    }

    interface SampleClient {
        @GET("/users/{id}")
        Mono<String> getUser(@PathVar("id") long id);

        @POST("/users")
        Mono<String> createUser(@Body String json);

        @POST("/signed-requests")
        Mono<String> createSignedRequest(@Body SignedRequest request);

        @GET("/search")
        Mono<String> search(@QueryParam("tag") List<String> tags,
                            @QueryParam("page") int page,
                            @HeaderParam("Authorization") String authorization);

        @POST("/events")
        Mono<String> sendEvent(@Body String body);

        @POST("/uploads")
        Mono<String> upload(@Body Flux<DataBuffer> body);

        @POST("/uploads/stream")
        Mono<String> uploadStream(@Body InputStream body);

        @POST("/payments")
        @IdempotencyKey
        Mono<String> createPayment(@Body String json);

        @POST("/payments/manual")
        Mono<String> createPaymentWithKey(@Body String json, @IdempotencyKey String idempotencyKey);

        @HEAD("/objects/{id}")
        Mono<Void> headObject(@PathVar("id") String id);

        @OPTIONS("/objects")
        Mono<String> optionsObjects();

        @GET("/headers")
        Mono<String> repeatedHeaders(@HeaderParam("X-Tag") List<String> tags,
                                     @HeaderParam("X-Mode") String[] modes,
                                     @HeaderParam Map<String, Object> extraHeaders);

        @DELETE("/sessions/{id}")
        Mono<Void> closeSession(@PathVar("id") String id);

        @GET("/headers/response")
        Mono<ResponseEntity<String>> responseWithHeaders();

        @GET("/bytes")
        Mono<byte[]> bytes();
    }

    interface MultipartClient {
        @POST("/multipart")
        @MultipartBody
        Mono<String> upload(
                @FormField("description") String description,
                @FormFile(value = "file", filename = "payload.txt", contentType = "text/plain") byte[] file,
                @FormField("tag") List<String> tags,
                @FormFile("attachment") FileAttachment attachment);
    }

    record SignedRequest(String orderId, int amount) {}

    @Test
    void logicalCallTimeoutUsesTheProductionBudgetOperator() {
        DefaultDataBufferFactory buffers = new DefaultDataBufferFactory();
        MockReactiveHttpClient<SampleClient> mock = MockReactiveHttpClient.forClient(SampleClient.class)
                .logicalCallTimeout(60)
                .respondTo(HttpMethod.GET, "/users/1", exchange -> ClientResponse.create(org.springframework.http.HttpStatus.OK)
                        .body(Flux.concat(
                                Mono.just(buffers.wrap("first".getBytes(StandardCharsets.UTF_8))),
                                Mono.delay(java.time.Duration.ofMillis(200))
                                        .thenReturn(buffers.wrap("second".getBytes(StandardCharsets.UTF_8)))))
                        .build())
                .build();

        StepVerifier.create(mock.proxy().getUser(1))
                .expectError(LogicalCallTimeoutException.class)
                .verify();
        assertThat(mock.exchanges()).hasSize(1);
    }

    @Test
    void publisherUploadIsColdAndMaterializedOncePerMockRetryAttempt() {
        AtomicInteger subscriptions = new AtomicInteger();
        AtomicInteger attempts = new AtomicInteger();
        DefaultDataBufferFactory buffers = new DefaultDataBufferFactory();
        Flux<DataBuffer> body = Flux.defer(() -> {
            subscriptions.incrementAndGet();
            return Flux.just(buffers.wrap("payload".getBytes(StandardCharsets.UTF_8)));
        });
        MockReactiveHttpClient<SampleClient> mock = MockReactiveHttpClient.forClient(SampleClient.class)
                .retry(2, "POST")
                .respondTo(HttpMethod.POST, "/uploads", exchange -> attempts.incrementAndGet() == 1
                        ? MockReactiveHttpClient.text(503, "retry")
                        : MockReactiveHttpClient.text(200, exchange.bodyAsString()))
                .build();

        Mono<String> response = mock.proxy().upload(body);

        assertThat(subscriptions).hasValue(0);
        assertThat(response.block()).isEqualTo("payload");
        assertThat(subscriptions).hasValue(2);
        assertThat(mock.exchanges()).hasSize(2)
                .allSatisfy(exchange -> assertThat(exchange.bodyAsString()).isEqualTo("payload"));
    }

    @Test
    void applicationOwnedInputStreamIsMaterializedAndClosedOnce() {
        AtomicInteger closes = new AtomicInteger();
        InputStream body = new FilterInputStream(new ByteArrayInputStream(
                "stream-payload".getBytes(StandardCharsets.UTF_8))) {
            @Override
            public void close() throws IOException {
                if (closes.compareAndSet(0, 1)) {
                    super.close();
                }
            }
        };
        MockReactiveHttpClient<SampleClient> mock = MockReactiveHttpClient.forClient(SampleClient.class)
                .respondTo(HttpMethod.POST, "/uploads/stream",
                        exchange -> MockReactiveHttpClient.text(200, exchange.bodyAsString()))
                .build();

        Mono<String> response = mock.proxy().uploadStream(body);

        assertThat(closes).hasValue(0);
        assertThat(response.block()).isEqualTo("stream-payload");
        assertThat(closes).hasValue(1);
        assertThat(mock.lastExchange().bodyAsString()).isEqualTo("stream-payload");
    }

    interface SharedCatalogOperations {
        @GET("/catalog/{id}")
        Mono<String> getCatalog(@PathVar("id") String id);
    }

    @ReactiveHttpClient(name = "inherited-named-client")
    interface InheritedNamedClient extends SharedCatalogOperations {
    }

    interface ApiOperators<T extends BaseResponse> {
        @GET("/api/order")
        Mono<T> getOrder(@QueryParam("orderId") String orderId);
    }

    @ReactiveHttpClient(name = "bus-api")
    interface BusApiOperators extends ApiOperators<BusResponse> {
    }

    @ReactiveHttpClient(name = "train-api")
    interface TrainApiOperators extends ApiOperators<TrainResponse> {
    }

    static class BaseResponse {
        public String code;
    }

    static class BusResponse extends BaseResponse {
        public String message;
    }

    static class TrainResponse extends BaseResponse {
        public String bookingCode;
    }

    @ReactiveHttpClient(name = "named-client")
    interface NamedClient {
        @GET("/items/{id}")
        Mono<String> getItem(@PathVar("id") long id, @HeaderParam("X-Trace") String trace);
    }

    @ReactiveHttpClient(name = "method-logged-client")
    interface MethodLoggedClient {
        @GET("/logged/first")
        @LogHttpExchange(logger = FirstInjectedExchangeLogger.class)
        Mono<String> first();

        @GET("/logged/second")
        @LogHttpExchange(logger = SecondInjectedExchangeLogger.class)
        Mono<String> second();
    }

    @ReactiveHttpClient(name = "interface-logged-client")
    @LogHttpExchange(logger = InterfaceInjectedExchangeLogger.class)
    interface InterfaceLoggedClient {
        @GET("/logged/interface")
        Mono<String> get();
    }

    private abstract static class RecordingExchangeLogger implements HttpExchangeLogger {
        final Object dependency;
        final List<HttpExchangeLogContext> contexts = new CopyOnWriteArrayList<>();

        private RecordingExchangeLogger(Object dependency) {
            this.dependency = dependency;
        }

        @Override
        public void log(HttpExchangeLogContext context) {
            contexts.add(context);
        }
    }

    private static final class FirstInjectedExchangeLogger extends RecordingExchangeLogger {
        private FirstInjectedExchangeLogger(Object dependency) {
            super(dependency);
        }
    }

    private static final class SecondInjectedExchangeLogger extends RecordingExchangeLogger {
        private SecondInjectedExchangeLogger(Object dependency) {
            super(dependency);
        }
    }

    private static final class InterfaceInjectedExchangeLogger extends RecordingExchangeLogger {
        private InterfaceInjectedExchangeLogger(Object dependency) {
            super(dependency);
        }
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
    void recordsHeadAndOptionsAnnotations() {
        List<HttpClientObserverEvent> observed = new CopyOnWriteArrayList<>();
        List<ReactiveHttpClientLifecycleContext> successes = new CopyOnWriteArrayList<>();
        MockReactiveHttpClient<SampleClient> mock = MockReactiveHttpClient.forClient(SampleClient.class)
                .withObserver(observed::add)
                .withLifecycleHook(new ReactiveHttpClientLifecycleHook() {
                    @Override
                    public void onSuccess(ReactiveHttpClientLifecycleContext context) {
                        successes.add(context);
                    }
                })
                .respondTo(HttpMethod.HEAD, "/objects/abc",
                        ex -> MockReactiveHttpClient.empty(204))
                .respondTo(HttpMethod.OPTIONS, "/objects",
                        ex -> MockReactiveHttpClient.json(200, "\"allowed\""))
                .build();

        StepVerifier.create(mock.proxy().headObject("abc"))
                .verifyComplete();
        RecordedExchangeAssertions.assertThat(mock.lastExchange())
                .hasMethod(HttpMethod.HEAD)
                .hasPath("/objects/abc")
                .hasStatusCode(204);

        StepVerifier.create(mock.proxy().optionsObjects())
                .expectNext("\"allowed\"")
                .verifyComplete();
        RecordedExchangeAssertions.assertThat(mock.lastExchange())
                .hasMethod(HttpMethod.OPTIONS)
                .hasPath("/objects")
                .hasStatusCode(200);

        assertThat(observed)
                .extracting(HttpClientObserverEvent::getHttpMethod)
                .containsExactly("HEAD", "OPTIONS");
        assertThat(successes)
                .extracting(ReactiveHttpClientLifecycleContext::httpMethod)
                .containsExactly("HEAD", "OPTIONS");
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
    void authProviderAddsAuthorizationHeaderBeforeExchangeRecording() {
        String token = "Bearer test-secret-token";
        MockReactiveHttpClient<SampleClient> mock = MockReactiveHttpClient.forClient(SampleClient.class)
                .withAuthProvider(request -> Mono.just(AuthContext.builder()
                        .header("Authorization", token)
                        .build()))
                .respondTo(HttpMethod.GET, "/users/42",
                        exchange -> MockReactiveHttpClient.text(200, "alice"))
                .build();

        StepVerifier.create(mock.proxy().getUser(42))
                .expectNext("alice")
                .verifyComplete();

        RecordedExchangeAssertions.assertThat(mock)
                .hasAttemptCount(HttpMethod.GET, "/users/42", 1);
        mock.exchanges().forEach(exchange ->
                RecordedExchangeAssertions.assertThat(exchange).hasAuthorizationHeader());

        assertThatThrownBy(() ->
                RecordedExchangeAssertions.assertThat(mock.lastExchange()).doesNotHaveAuthorizationHeader())
                .hasMessageContaining("Authorization", "[REDACTED]")
                .hasMessageNotContaining(token);
    }

    @Test
    void authProviderReceivesSerializedJsonBytesForDtoBodies() {
        AtomicReference<Object> capturedAuthBody = new AtomicReference<>();
        JsonMapper applicationObjectMapper = JsonMapper.builder()
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .build();
        MockReactiveHttpClient<SampleClient> mock = MockReactiveHttpClient.forClient(SampleClient.class)
                .jsonCodec(new Jackson3ReactiveHttpClientJsonCodec(applicationObjectMapper))
                .withAuthProvider(request -> {
                    capturedAuthBody.set(request.requestBody());
                    return Mono.just(AuthContext.empty());
                })
                .respondTo(HttpMethod.POST, "/signed-requests",
                        exchange -> MockReactiveHttpClient.text(200, "accepted"))
                .build();

        StepVerifier.create(mock.proxy().createSignedRequest(new SignedRequest("order-1", 10)))
                .expectNext("accepted")
                .verifyComplete();

        assertThat(capturedAuthBody.get()).isInstanceOf(byte[].class);
        String serializedBody = new String((byte[]) capturedAuthBody.get(), StandardCharsets.UTF_8);
        assertThat(serializedBody).contains("order_id", "order-1", "amount", "10").doesNotContain("orderId");
        assertThat(mock.lastExchange().bodyAsString()).isEqualTo(serializedBody);
        assertThat(mock.lastExchange().bodyAsString()).isEqualTo(serializedBody);
    }

    @Test
    void authReplayAndRetrySequenceRecordsInProcessAttemptsOnly() {
        AtomicInteger authCalls = new AtomicInteger();
        AtomicInteger invalidationCalls = new AtomicInteger();
        AtomicInteger responsesAfterUnauthorized = new AtomicInteger();
        List<String> lifecycleEvents = new CopyOnWriteArrayList<>();
        List<HttpClientObserverEvent> observed = new CopyOnWriteArrayList<>();
        InvalidatableAuthProvider authProvider = new InvalidatableAuthProvider() {
            @Override
            public Mono<AuthContext> getAuth(AuthRequest request) {
                return Mono.just(AuthContext.builder()
                        .header("Authorization", "Bearer sequence-" + authCalls.incrementAndGet())
                        .build());
            }

            @Override
            public Mono<Void> invalidate() {
                invalidationCalls.incrementAndGet();
                return Mono.empty();
            }
        };
        ReactiveHttpClientLifecycleHook lifecycleHook = new ReactiveHttpClientLifecycleHook() {
            @Override
            public void onStart(ReactiveHttpClientLifecycleContext context) {
                lifecycleEvents.add("start:" + context.attemptNumber());
            }

            @Override
            public void onRetryAttempt(ReactiveHttpClientLifecycleContext context) {
                lifecycleEvents.add("retry:" + context.attemptNumber());
            }

            @Override
            public void onSuccess(ReactiveHttpClientLifecycleContext context) {
                lifecycleEvents.add("success:" + context.attemptNumber());
            }
        };
        MockReactiveHttpClient<SampleClient> mock = MockReactiveHttpClient.forClient(SampleClient.class)
                .retry(2, "GET")
                .withAuthProvider(authProvider)
                .withLifecycleHook(lifecycleHook)
                .withObserver(observed::add)
                .respondTo(HttpMethod.GET, "/users/42",
                        MockReactiveHttpClient.unauthorizedOnceThen(exchange ->
                                responsesAfterUnauthorized.incrementAndGet() == 1
                                        ? MockReactiveHttpClient.text(503, "retry")
                                        : MockReactiveHttpClient.text(200, "alice")))
                .build();

        StepVerifier.create(mock.proxy().getUser(42))
                .expectNext("alice")
                .verifyComplete();

        assertThat(mock.exchanges()).extracting(exchange -> exchange.statusCode().value())
                .containsExactly(401, 503, 200);
        assertThat(authCalls).hasValue(3);
        assertThat(invalidationCalls).hasValue(1);
        assertThat(lifecycleEvents).containsExactly("start:1", "retry:2", "success:2");
        assertThat(observed).singleElement().satisfies(event -> {
            assertThat(event.getStatusCode()).isEqualTo(200);
            assertThat(event.getAttemptCount()).isEqualTo(2);
        });
    }

    @Test
    void configuredRedirectFollowingRemainsAConnectorBoundary() {
        ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();
        config.setFollowRedirects(true);
        MockReactiveHttpClient<SampleClient> mock = MockReactiveHttpClient.forClient(SampleClient.class)
                .clientConfig(config)
                .respondTo(HttpMethod.GET, "/users/42", exchange ->
                        MockReactiveHttpClient.response(
                                302, Map.of("Location", List.of("/users/43")), "visible-redirect"))
                .build();

        StepVerifier.create(mock.proxy().getUser(42))
                .expectNext("visible-redirect")
                .verifyComplete();

        RecordedExchangeAssertions.assertThat(mock)
                .hasAttemptCount(HttpMethod.GET, "/users/42", 1);
        RecordedExchangeAssertions.assertThat(mock.lastExchange())
                .hasStatusCode(302);
    }

    @Test
    void authorizationHeaderAbsenceCanBeAssertedAfterFilters() {
        MockReactiveHttpClient<SampleClient> mock = MockReactiveHttpClient.forClient(SampleClient.class)
                .respondTo(HttpMethod.GET, "/users/42",
                        exchange -> MockReactiveHttpClient.text(200, "alice"))
                .build();

        StepVerifier.create(mock.proxy().getUser(42))
                .expectNext("alice")
                .verifyComplete();

        RecordedExchangeAssertions.assertThat(mock.lastExchange())
                .doesNotHaveAuthorizationHeader();
    }

    @Test
    void unauthorizedOnceInvalidatesAuthAndRecordsBothAttempts() {
        AtomicInteger authCalls = new AtomicInteger();
        AtomicInteger invalidationCalls = new AtomicInteger();
        InvalidatableAuthProvider authProvider = new InvalidatableAuthProvider() {
            @Override
            public Mono<AuthContext> getAuth(AuthRequest request) {
                return Mono.just(AuthContext.builder()
                        .header("Authorization", "Bearer token-" + authCalls.incrementAndGet())
                        .build());
            }

            @Override
            public Mono<Void> invalidate() {
                invalidationCalls.incrementAndGet();
                return Mono.empty();
            }
        };
        MockReactiveHttpClient<SampleClient> mock = MockReactiveHttpClient.forClient(SampleClient.class)
                .withAuthProvider(authProvider)
                .respondTo(HttpMethod.GET, "/users/42",
                        MockReactiveHttpClient.unauthorizedOnceThen(
                                exchange -> MockReactiveHttpClient.text(200, "alice")))
                .build();

        StepVerifier.create(mock.proxy().getUser(42))
                .expectNext("alice")
                .verifyComplete();

        assertThat(authCalls).hasValue(2);
        assertThat(invalidationCalls).hasValue(1);
        RecordedExchangeAssertions.assertThat(mock)
                .hasAttemptCount(HttpMethod.GET, "/users/42", 2);
        RecordedExchangeAssertions.assertThat(mock.exchanges().get(0))
                .hasAuthorizationHeader()
                .hasStatusCode(401);
        RecordedExchangeAssertions.assertThat(mock.exchanges().get(1))
                .hasAuthorizationHeader()
                .hasStatusCode(200);
    }

    @Test
    void repeatedOutboundHeadersAndCustomResponseHeadersAreEasyToAssert() {
        MockReactiveHttpClient<SampleClient> mock = MockReactiveHttpClient.forClient(SampleClient.class)
                .respondTo(HttpMethod.GET, "/headers",
                        ex -> MockReactiveHttpClient.text(200, "ok"))
                .respondTo(HttpMethod.GET, "/headers/response",
                        ex -> MockReactiveHttpClient.text(200, "accepted",
                                Map.of("X-Trace", List.of("trace-1", "trace-2"))))
                .build();

        Map<String, Object> extraHeaders = new java.util.LinkedHashMap<>();
        extraHeaders.put("X-Map", List.of("one", "two"));
        extraHeaders.put("X-Array", new int[]{1, 2});

        StepVerifier.create(mock.proxy().repeatedHeaders(
                        List.of("alpha", "beta"), new String[]{"fast", "safe"}, extraHeaders))
                .expectNext("ok")
                .verifyComplete();

        RecordedExchangeAssertions.assertThat(mock.lastExchange())
                .hasHeaderValues("X-Tag", "alpha", "beta")
                .hasHeaderValues("X-Mode", "fast", "safe")
                .hasHeaderValues("X-Map", "one", "two")
                .hasHeaderValues("X-Array", "1", "2");

        StepVerifier.create(mock.proxy().responseWithHeaders())
                .assertNext(entity -> {
                    assertThat(entity.getBody()).isEqualTo("accepted");
                    assertThat(entity.getHeaders().get("X-Trace")).containsExactly("trace-1", "trace-2");
                })
                .verifyComplete();
    }

    @Test
    void rawTextUnexpectedBodyCanBeServedToVoidEndpoint() {
        MockReactiveHttpClient<SampleClient> mock = MockReactiveHttpClient.forClient(SampleClient.class)
                .respondTo(HttpMethod.DELETE, "/sessions/s-1",
                        ex -> MockReactiveHttpClient.text(200, "unexpected-body"))
                .build();

        StepVerifier.create(mock.proxy().closeSession("s-1"))
                .verifyComplete();

        RecordedExchangeAssertions.assertThat(mock.lastExchange())
                .hasMethod(HttpMethod.DELETE)
                .hasPath("/sessions/s-1")
                .hasStatusCode(200);
    }

    @Test
    void rawByteResponseHelperServesByteBodies() {
        MockReactiveHttpClient<SampleClient> mock = MockReactiveHttpClient.forClient(SampleClient.class)
                .respondTo(HttpMethod.GET, "/bytes",
                        ex -> MockReactiveHttpClient.bytes(200, "abc".getBytes(StandardCharsets.UTF_8),
                                Map.of("X-Checksum", List.of("sha-abc"))))
                .build();

        StepVerifier.create(mock.proxy().bytes())
                .assertNext(bytes -> assertThat(bytes).containsExactly("abc".getBytes(StandardCharsets.UTF_8)))
                .verifyComplete();
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
    void customNoopSubclassApplyOverrideRemainsActiveWithoutAvailabilityOverride() {
        ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();
        config.getResilience().setEnabled(true);
        config.getResilience().setRetry("legacy-custom");
        config.getResilience().setRetryMethods(Set.of("GET"));
        AtomicInteger served = new AtomicInteger();
        AtomicInteger applications = new AtomicInteger();
        ResilienceOperatorApplier custom = new NoopResilienceOperatorApplier() {
            @Override
            public <T> Mono<T> applyRetry(Mono<T> mono, String instanceName) {
                applications.incrementAndGet();
                return mono.retry(1);
            }
        };

        MockReactiveHttpClient<SampleClient> mock = MockReactiveHttpClient.forClient(SampleClient.class)
                .clientConfig(config)
                .resilienceOperatorApplier(custom)
                .respondTo(HttpMethod.GET, "/users/42", exchange -> served.incrementAndGet() == 1
                        ? MockReactiveHttpClient.text(503, "retry")
                        : MockReactiveHttpClient.text(200, "alice"))
                .build();

        StepVerifier.create(mock.proxy().getUser(42))
                .expectNext("alice")
                .verifyComplete();

        assertThat(applications).hasValue(1);
        RecordedExchangeAssertions.assertThat(mock).hasAttemptCount(HttpMethod.GET, "/users/42", 2);
    }

    @Test
    void mockBuildUsesProductionStrictUnsafeRetryValidation() {
        ReactiveHttpClientProperties.ClientConfig config = strictMockRetryConfig();

        assertThatThrownBy(() -> MockReactiveHttpClient.forClient(StrictUnsafeMockClient.class)
                .clientConfig(config)
                .resilienceOperatorApplier(new StrictRetryApplier(true))
                .build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("strict unsafe retry validation")
                .hasMessageContaining("StrictUnsafeMockClient#create")
                .hasMessageContaining("retry=mock");
    }

    @Test
    void mockBuildKeepsStrictValidationDormantForSingleAttemptRetry() {
        MockReactiveHttpClient<StrictUnsafeMockClient> mock = MockReactiveHttpClient
                .forClient(StrictUnsafeMockClient.class)
                .clientConfig(strictMockRetryConfig())
                .resilienceOperatorApplier(new StrictRetryApplier(false))
                .respondTo(HttpMethod.POST, "/strict", exchange -> MockReactiveHttpClient.text(200, "ok"))
                .build();

        assertThat(mock.proxy().create().block()).isEqualTo("ok");
        RecordedExchangeAssertions.assertThat(mock).hasAttemptCount(1);
    }

    @Test
    void observerReceivesOneTerminalEventForSuccessfulCall() {
        List<HttpClientObserverEvent> observed = new CopyOnWriteArrayList<>();
        MockReactiveHttpClient<SampleClient> mock = MockReactiveHttpClient.forClient(SampleClient.class)
                .withObserver(observed::add)
                .respondTo(HttpMethod.GET, "/users/42",
                        ex -> MockReactiveHttpClient.json(200, "alice"))
                .build();

        StepVerifier.create(mock.proxy().getUser(42))
                .expectNext("alice")
                .verifyComplete();

        assertThat(observed).singleElement().satisfies(event -> {
            assertThat(event.getClientName()).isEqualTo("mock-client");
            assertThat(event.getStatusCode()).isEqualTo(200);
            assertThat(event.getAttemptCount()).isEqualTo(1);
        });
    }

    @Test
    void openCircuitReportsOneNoNetworkTerminalAcrossMockDiagnostics() {
        CircuitBreaker circuitBreaker = CircuitBreaker.ofDefaults("mock-open");
        circuitBreaker.transitionToOpenState();
        CallNotPermittedException rejection =
                CallNotPermittedException.createCallNotPermittedException(circuitBreaker);
        ReactiveHttpClientProperties.ResilienceConfig resilience =
                new ReactiveHttpClientProperties.ResilienceConfig();
        resilience.setEnabled(true);
        resilience.setCircuitBreaker("mock-open");
        ReactiveHttpClientProperties.ClientConfig clientConfig =
                new ReactiveHttpClientProperties.ClientConfig();
        clientConfig.setResilience(resilience);

        List<HttpClientObserverEvent> observed = new CopyOnWriteArrayList<>();
        List<String> lifecycleSignals = new CopyOnWriteArrayList<>();
        List<ReactiveHttpClientLifecycleContext> lifecycleErrors = new CopyOnWriteArrayList<>();
        InterfaceInjectedExchangeLogger logger = new InterfaceInjectedExchangeLogger(new Object());
        MockReactiveHttpClient<InterfaceLoggedClient> mock = MockReactiveHttpClient
                .forClient(InterfaceLoggedClient.class)
                .clientConfig(clientConfig)
                .resilienceOperatorApplier(new OpenCircuitResilienceOperatorApplier(rejection))
                .withObserver(observed::add)
                .withLifecycleHook(new ReactiveHttpClientLifecycleHook() {
                    @Override
                    public void onStart(ReactiveHttpClientLifecycleContext context) {
                        lifecycleSignals.add("start");
                    }

                    @Override
                    public void onSuccess(ReactiveHttpClientLifecycleContext context) {
                        lifecycleSignals.add("success");
                    }

                    @Override
                    public void onError(ReactiveHttpClientLifecycleContext context) {
                        lifecycleSignals.add("error");
                        lifecycleErrors.add(context);
                    }
                })
                .withExchangeLogger(logger)
                .build();

        StepVerifier.create(mock.proxy().get())
                .expectError(CallNotPermittedException.class)
                .verify();

        assertThat(mock.exchanges()).as("no-network mock exchanges").isEmpty();
        assertThat(lifecycleSignals).containsExactly("error");
        assertThat(lifecycleErrors).singleElement().satisfies(context -> {
            assertThat(context.attemptNumber()).isZero();
            assertThat(context.statusCode()).isNull();
            assertThat(context.requestUrl()).isNull();
            assertThat(context.error()).isSameAs(rejection);
            assertThat(context.failureStage()).isNull();
        });
        assertThat(observed).singleElement().satisfies(event -> {
            assertThat(event.getAttemptCount()).isZero();
            assertThat(event.getStatusCode()).isNull();
            assertThat(event.getError()).isSameAs(rejection);
            assertThat(event.getErrorCategory()).isEqualTo(ErrorCategory.RESILIENCE_ERROR);
            assertThat(event.getFailureStage()).isNull();
            assertThat(event.getDurationMs()).isBetween(0L, 5_000L);
        });
        assertThat(logger.contexts).singleElement().satisfies(context -> {
            HttpClientObserverEvent event = observed.getFirst();
            assertThat(context.subscriptionAttemptCount()).isZero();
            assertThat(context.responseStatus()).isNull();
            assertThat(context.requestUrl()).isNull();
            assertThat(context.error()).isSameAs(rejection);
            assertThat(ErrorCategories.from(context.error())).isEqualTo(ErrorCategory.RESILIENCE_ERROR);
            assertThat(context.failureStage()).isNull();
            assertThat(context.durationMs()).isEqualTo(event.getDurationMs());
        });
    }

    @Test
    void annotatedClientNameAndFinalRequestMetadataMatchProductionBehavior() {
        List<HttpClientObserverEvent> observed = new CopyOnWriteArrayList<>();
        List<String> supportedClientNames = new CopyOnWriteArrayList<>();
        List<ReactiveHttpClientLifecycleContext> successes = new CopyOnWriteArrayList<>();
        ReactiveHttpClientLifecycleHook hook = new ReactiveHttpClientLifecycleHook() {
            @Override
            public boolean supports(String clientName) {
                supportedClientNames.add(clientName);
                return "named-client".equals(clientName);
            }

            @Override
            public void onSuccess(ReactiveHttpClientLifecycleContext context) {
                successes.add(context);
            }
        };
        MockReactiveHttpClient<NamedClient> mock = MockReactiveHttpClient.forClient(NamedClient.class)
                .baseUrl("http://named.mock.local:8081")
                .withObserver(observed::add)
                .withLifecycleHook(hook)
                .respondTo(HttpMethod.GET, "/items/42",
                        ex -> MockReactiveHttpClient.json(200, "item"))
                .build();

        StepVerifier.create(mock.proxy().getItem(42, "trace-42"))
                .expectNext("item")
                .verifyComplete();

        assertThat(supportedClientNames).containsExactly("named-client");
        assertThat(observed).singleElement().satisfies(event -> {
            assertThat(event.getClientName()).isEqualTo("named-client");
            assertThat(event.getRequestUrl()).isEqualTo("http://named.mock.local:8081/items/42");
            assertThat(event.getServerAddress()).isEqualTo("named.mock.local");
            assertThat(event.getServerPort()).isEqualTo(8081);
            assertThat(event.getRequestHeaders()).containsEntry("X-Trace", "trace-42");
        });
        assertThat(successes).singleElement().satisfies(context -> {
            assertThat(context.clientName()).isEqualTo("named-client");
            assertThat(context.requestUrl()).isEqualTo(URI.create("http://named.mock.local:8081/items/42"));
        });
    }

    @Test
    void constructorInjectedMethodLoggersResolveFromMockContext() {
        Object firstDependency = new Object();
        Object secondDependency = new Object();
        FirstInjectedExchangeLogger firstLogger = new FirstInjectedExchangeLogger(firstDependency);
        SecondInjectedExchangeLogger secondLogger = new SecondInjectedExchangeLogger(secondDependency);
        MockReactiveHttpClient<MethodLoggedClient> mock = MockReactiveHttpClient
                .forClient(MethodLoggedClient.class)
                .withExchangeLogger(firstLogger)
                .withExchangeLogger(secondLogger)
                .respondTo(HttpMethod.GET, "/logged/first",
                        ex -> MockReactiveHttpClient.text(200, "first"))
                .respondTo(HttpMethod.GET, "/logged/second",
                        ex -> MockReactiveHttpClient.text(200, "second"))
                .build();

        StepVerifier.create(mock.proxy().first()).expectNext("first").verifyComplete();
        StepVerifier.create(mock.proxy().second()).expectNext("second").verifyComplete();

        assertThat(firstLogger.dependency).isSameAs(firstDependency);
        assertThat(firstLogger.contexts).singleElement().satisfies(context -> {
            assertThat(context.clientName()).isEqualTo("method-logged-client");
            assertThat(context.pathTemplate()).isEqualTo("/logged/first");
            assertThat(context.responseStatus()).isEqualTo(200);
        });
        assertThat(secondLogger.dependency).isSameAs(secondDependency);
        assertThat(secondLogger.contexts).singleElement().satisfies(context ->
                assertThat(context.pathTemplate()).isEqualTo("/logged/second"));
    }

    @Test
    void constructorInjectedInterfaceLoggerResolvesFromMockContext() {
        Object dependency = new Object();
        InterfaceInjectedExchangeLogger logger = new InterfaceInjectedExchangeLogger(dependency);
        MockReactiveHttpClient<InterfaceLoggedClient> mock = MockReactiveHttpClient
                .forClient(InterfaceLoggedClient.class)
                .withExchangeLogger(logger)
                .respondTo(HttpMethod.GET, "/logged/interface",
                        ex -> MockReactiveHttpClient.text(200, "ok"))
                .build();

        StepVerifier.create(mock.proxy().get()).expectNext("ok").verifyComplete();

        assertThat(logger.dependency).isSameAs(dependency);
        assertThat(logger.contexts).singleElement().satisfies(context -> {
            assertThat(context.clientName()).isEqualTo("interface-logged-client");
            assertThat(context.pathTemplate()).isEqualTo("/logged/interface");
        });
    }

    @Test
    void duplicateExchangeLoggerClassIsRejected() {
        MockReactiveHttpClient.Builder<MethodLoggedClient> builder =
                MockReactiveHttpClient.forClient(MethodLoggedClient.class)
                        .withExchangeLogger(new FirstInjectedExchangeLogger(new Object()));

        assertThatThrownBy(() -> builder.withExchangeLogger(
                new FirstInjectedExchangeLogger(new Object())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("HttpExchangeLogger already registered")
                .hasMessageContaining(FirstInjectedExchangeLogger.class.getName());
    }

    @Test
    void inheritedAnnotatedClientRecordsMetadataAndReportsConcreteClientName() {
        List<HttpClientObserverEvent> observed = new CopyOnWriteArrayList<>();
        List<String> supportedClientNames = new CopyOnWriteArrayList<>();
        List<ReactiveHttpClientLifecycleContext> successes = new CopyOnWriteArrayList<>();
        ReactiveHttpClientLifecycleHook hook = new ReactiveHttpClientLifecycleHook() {
            @Override
            public boolean supports(String clientName) {
                supportedClientNames.add(clientName);
                return "inherited-named-client".equals(clientName);
            }

            @Override
            public void onSuccess(ReactiveHttpClientLifecycleContext context) {
                successes.add(context);
            }
        };

        MockReactiveHttpClient<InheritedNamedClient> mock = MockReactiveHttpClient
                .forClient(InheritedNamedClient.class)
                .baseUrl("http://inherited.mock.local:8082")
                .withObserver(observed::add)
                .withLifecycleHook(hook)
                .respondTo(HttpMethod.GET, "/catalog/42",
                        ex -> MockReactiveHttpClient.text(200, "catalog"))
                .build();

        StepVerifier.create(mock.proxy().getCatalog("42"))
                .expectNext("catalog")
                .verifyComplete();

        assertThat(supportedClientNames).containsExactly("inherited-named-client");
        RecordedExchangeAssertions.assertThat(mock.lastExchange())
                .hasMethod(HttpMethod.GET)
                .hasPath("/catalog/42")
                .hasStatusCode(200);
        assertThat(observed).singleElement().satisfies(event -> {
            assertThat(event.getClientName()).isEqualTo("inherited-named-client");
            assertThat(event.getHttpMethod()).isEqualTo("GET");
            assertThat(event.getUriPath()).isEqualTo("/catalog/{id}");
            assertThat(event.getRequestUrl()).isEqualTo("http://inherited.mock.local:8082/catalog/42");
            assertThat(event.getServerAddress()).isEqualTo("inherited.mock.local");
            assertThat(event.getServerPort()).isEqualTo(8082);
        });
        assertThat(successes).singleElement().satisfies(context -> {
            assertThat(context.clientName()).isEqualTo("inherited-named-client");
            assertThat(context.httpMethod()).isEqualTo("GET");
            assertThat(context.pathTemplate()).isEqualTo("/catalog/{id}");
            assertThat(context.requestUrl()).isEqualTo(URI.create("http://inherited.mock.local:8082/catalog/42"));
        });
    }

    @Test
    void inheritedGenericAnnotatedClientDecodesConcreteResponseType() {
        MockReactiveHttpClient<BusApiOperators> busMock = MockReactiveHttpClient
                .forClient(BusApiOperators.class)
                .respondTo(HttpMethod.GET, "/api/order",
                        ex -> MockReactiveHttpClient.json(200, "{\"code\":\"0\",\"message\":\"boarding\"}"))
                .build();
        MockReactiveHttpClient<TrainApiOperators> trainMock = MockReactiveHttpClient
                .forClient(TrainApiOperators.class)
                .respondTo(HttpMethod.GET, "/api/order",
                        ex -> MockReactiveHttpClient.json(200, "{\"code\":\"0\",\"bookingCode\":\"TR-9\"}"))
                .build();

        StepVerifier.create(busMock.proxy().getOrder("bus-1"))
                .assertNext(response -> {
                    assertThat(response).isInstanceOf(BusResponse.class);
                    assertThat(response.code).isEqualTo("0");
                    assertThat(response.message).isEqualTo("boarding");
                })
                .verifyComplete();
        StepVerifier.create(trainMock.proxy().getOrder("train-1"))
                .assertNext(response -> {
                    assertThat(response).isInstanceOf(TrainResponse.class);
                    assertThat(response.code).isEqualTo("0");
                    assertThat(response.bookingCode).isEqualTo("TR-9");
                })
                .verifyComplete();
    }

    @Test
    void retrySuccessNotifiesOneObserverEventAndOrderedLifecycleCallbacks() {
        AtomicInteger served = new AtomicInteger();
        List<HttpClientObserverEvent> observed = new CopyOnWriteArrayList<>();
        List<String> lifecycleEvents = new CopyOnWriteArrayList<>();
        MockReactiveHttpClient<SampleClient> mock = MockReactiveHttpClient.forClient(SampleClient.class)
                .retry(2, "POST")
                .withObserver(observed::add)
                .withLifecycleHook(new OrderedRecordingHook("second", 20, lifecycleEvents))
                .withLifecycleHook(new OrderedRecordingHook("first", 10, lifecycleEvents))
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

        assertThat(observed).singleElement().satisfies(event -> {
            assertThat(event.getStatusCode()).isEqualTo(201);
            assertThat(event.getAttemptCount()).isEqualTo(2);
        });
        assertThat(lifecycleEvents).containsExactly(
                "first:start:1",
                "second:start:1",
                "first:retry:2",
                "second:retry:2",
                "first:success:2",
                "second:success:2");
    }

    @Test
    void annotationOrderedLifecycleHooksUseProductionOrdering() {
        List<String> lifecycleEvents = new CopyOnWriteArrayList<>();
        MockReactiveHttpClient<SampleClient> mock = MockReactiveHttpClient.forClient(SampleClient.class)
                .withLifecycleHook(new SecondAnnotationOrderedHook(lifecycleEvents))
                .withLifecycleHook(new FirstAnnotationOrderedHook(lifecycleEvents))
                .respondTo(HttpMethod.GET, "/users/42",
                        ex -> MockReactiveHttpClient.json(200, "alice"))
                .build();

        StepVerifier.create(mock.proxy().getUser(42))
                .expectNext("alice")
                .verifyComplete();

        assertThat(lifecycleEvents).containsExactly("first:start", "second:start");
    }

    @Test
    void bodyErrorModelsStableTimeoutTerminalSemanticsWithoutNetworkTiming() {
        List<HttpClientObserverEvent> observed = new CopyOnWriteArrayList<>();
        AtomicReference<ReactiveHttpClientLifecycleContext> lifecycleError = new AtomicReference<>();
        MockReactiveHttpClient<SampleClient> mock = MockReactiveHttpClient.forClient(SampleClient.class)
                .withObserver(observed::add)
                .withLifecycleHook(new ReactiveHttpClientLifecycleHook() {
                    @Override
                    public void onError(ReactiveHttpClientLifecycleContext context) {
                        lifecycleError.set(context);
                    }
                })
                .respondTo(HttpMethod.GET, "/users/42", ex -> MockReactiveHttpClient.bodyError(
                        200, Map.of("X-Timeout-Phase", List.of("body")), ReadTimeoutException.INSTANCE))
                .build();

        StepVerifier.create(mock.proxy().getUser(42))
                .expectError(ReadTimeoutException.class)
                .verify();

        RecordedExchangeAssertions.assertThat(mock.lastExchange())
                .hasStatusCode(200);
        assertThat(observed).singleElement().satisfies(event -> {
            assertThat(event.getStatusCode()).isEqualTo(200);
            assertThat(event.getErrorCategory()).isEqualTo(ErrorCategory.TIMEOUT);
            assertThat(event.getFailureStage()).isEqualTo(HttpClientFailureStage.RESPONSE_BODY);
            assertThat(event.getAttemptCount()).isEqualTo(1);
        });
        assertThat(lifecycleError.get().statusCode()).isEqualTo(200);
        assertThat(lifecycleError.get().failureStage()).isEqualTo(HttpClientFailureStage.RESPONSE_BODY);
        assertThat(lifecycleError.get().attemptNumber()).isEqualTo(1);
    }

    @Test
    void connectorFailureModelsBoundedPreResponseStageWithoutTransportTiming() {
        List<HttpClientObserverEvent> observed = new CopyOnWriteArrayList<>();
        AtomicReference<ReactiveHttpClientLifecycleContext> lifecycleError = new AtomicReference<>();
        MockReactiveHttpClient<SampleClient> mock = MockReactiveHttpClient.forClient(SampleClient.class)
                .withObserver(observed::add)
                .withLifecycleHook(new ReactiveHttpClientLifecycleHook() {
                    @Override
                    public void onError(ReactiveHttpClientLifecycleContext context) {
                        lifecycleError.set(context);
                    }
                })
                .respondTo(HttpMethod.GET, "/users/42", exchange -> {
                    throw new RuntimeException(new UnknownHostException("missing.invalid"));
                })
                .build();

        StepVerifier.create(mock.proxy().getUser(42))
                .expectErrorMatches(error -> ErrorCategories.from(error) == ErrorCategory.UNKNOWN_HOST)
                .verify();

        assertThat(observed).singleElement().satisfies(event -> {
            assertThat(event.getErrorCategory()).isEqualTo(ErrorCategory.UNKNOWN_HOST);
            assertThat(event.getFailureStage()).isEqualTo(HttpClientFailureStage.DNS_RESOLUTION);
            assertThat(event.getStatusCode()).isNull();
        });
        assertThat(lifecycleError.get().failureStage()).isEqualTo(HttpClientFailureStage.DNS_RESOLUTION);
        assertThat(lifecycleError.get().statusCode()).isNull();
    }

    @Test
    void retryExhaustionNotifiesOneTerminalObserverEvent() {
        List<HttpClientObserverEvent> observed = new CopyOnWriteArrayList<>();
        MockReactiveHttpClient<SampleClient> mock = MockReactiveHttpClient.forClient(SampleClient.class)
                .retry(2, "POST")
                .withObserver(observed::add)
                .respondTo(HttpMethod.POST, "/payments",
                        ex -> MockReactiveHttpClient.json(503, "{\"error\":\"down\"}"))
                .build();

        ErrorCategoryAssertions.assertThatFails(mock.proxy().createPayment("{\"amount\":10}"))
                .hasStatusCode(503)
                .hasErrorCategory(ErrorCategory.SERVER_ERROR);

        assertThat(mock.exchanges()).hasSize(2);
        assertThat(observed).singleElement().satisfies(event -> {
            assertThat(event.getStatusCode()).isEqualTo(503);
            assertThat(event.getAttemptCount()).isEqualTo(2);
            assertThat(event.getError()).isNotNull();
        });
    }

    @Test
    void retryHelperRejectsEmptyRetryMethods() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        MockReactiveHttpClient.forClient(SampleClient.class).retry(2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("retryMethods");
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
    void materializesMultipartPartsForInProcessAssertionsOnly() {
        MockReactiveHttpClient<MultipartClient> mock = MockReactiveHttpClient.forClient(MultipartClient.class)
                .fallback(MockReactiveHttpClient.text(200, "ok"))
                .build();
        byte[] fileBody = new byte[]{0x00, 0x01, (byte) 0xff, 0x02};

        assertThat(mock.proxy().upload(
                "alpha",
                fileBody,
                List.of("one", "two"),
                FileAttachment.of("attachment-body".getBytes(StandardCharsets.UTF_8),
                        "dynamic.json", "application/json"))
                .block()).isEqualTo("ok");

        RecordedExchange exchange = mock.lastExchange();
        RecordedExchangeAssertions.assertThat(exchange)
                .hasMultipartPartNames("description", "file", "tag", "tag", "attachment")
                .hasMultipartPart(0, "description", "alpha".getBytes(StandardCharsets.UTF_8))
                .hasMultipartPart(1, "file", fileBody)
                .hasMultipartPartHeader(1, "Content-Type", "text/plain")
                .hasMultipartPart(2, "tag", "one".getBytes(StandardCharsets.UTF_8))
                .hasMultipartPart(3, "tag", "two".getBytes(StandardCharsets.UTF_8))
                .hasMultipartPart(4, "attachment", "attachment-body".getBytes(StandardCharsets.UTF_8))
                .hasMultipartPartHeader(4, "Content-Type", "application/json");
        assertThat(exchange.multipartParts().get(1).filename()).isEqualTo("payload.txt");
        assertThat(exchange.multipartParts().get(4).filename()).isEqualTo("dynamic.json");
    }

    @Test
    void usesCallerSuppliedMethodMetadataCache() {
        CountingMethodMetadataCache metadataCache = new CountingMethodMetadataCache();
        MockReactiveHttpClient<SampleClient> mock = MockReactiveHttpClient.forClient(SampleClient.class)
                .methodMetadataCache(metadataCache)
                .respondTo(HttpMethod.GET, "/users/42",
                        ex -> MockReactiveHttpClient.json(200, "alice"))
                .build();

        int validationCalls = metadataCache.getCalls;
        assertThat(mock.proxy().getUser(42).block()).isEqualTo("alice");
        assertThat(metadataCache.getCalls).isGreaterThan(validationCalls);
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
    void buildRejectsUnsupportedDeclarativeReturnTypeBeforeAnyExchange() {
        assertThatThrownBy(() -> MockReactiveHttpClient.forClient(InvalidNestedResponseClient.class).build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("reactive HTTP client 'mock-client'")
                .hasMessageContaining("concreteClient=" + InvalidNestedResponseClient.class.getName())
                .hasMessageContaining("ResponseEntity<reactor.core.publisher.Flux<java.lang.String>>")
                .hasMessageContaining("the only reactive ResponseEntity body supported is Flux<DataBuffer>");
    }

    @Test
    void buildRejectsInvalidRequestParameterGrammarBeforeAnyExchange() {
        assertThatThrownBy(() -> MockReactiveHttpClient.forClient(InvalidParameterClient.class).build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Reactive HTTP client 'mock-client'")
                .hasMessageContaining("concreteClient=" + InvalidParameterClient.class.getName())
                .hasMessageContaining("parameterIndex=0")
                .hasMessageContaining("conflicting request-binding roles");
    }

    @Test
    void buildRejectsAuthorityInDeclarativeUriBeforeAnyExchange() {
        assertThatThrownBy(() -> MockReactiveHttpClient.forClient(InvalidUriClient.class).build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("InvalidUriClient")
                .hasMessageContaining("scheme or authority")
                .hasMessageNotContaining("secret-value");
    }

    @Test
    void apiRefUriUsesProductionExpansionAuthPrecedenceAndFinalObservation() {
        ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();
        ReactiveHttpClientProperties.ApiConfig api = new ReactiveHttpClientProperties.ApiConfig();
        api.setMethod("GET");
        api.setPath("/items/{id}?literal=yes&template={template}&repeat=template");
        config.setApis(Map.of("lookup", api));
        java.util.LinkedHashMap<String, List<String>> defaults = new java.util.LinkedHashMap<>();
        defaults.put("configured", List.of("first", "second"));
        defaults.put("repeat", List.of("default"));
        config.setDefaultQueryParams(defaults);
        AtomicReference<HttpClientObserverEvent> observed = new AtomicReference<>();

        MockReactiveHttpClient<ApiRefUriClient> mock = MockReactiveHttpClient.forClient(ApiRefUriClient.class)
                .baseUrl("http://mock.local/base")
                .clientConfig(config)
                .withAuthProvider(request -> Mono.just(AuthContext.builder()
                        .queryParam("repeat", "auth one")
                        .build()))
                .withObserver(observed::set)
                .fallback(MockReactiveHttpClient.json(200, "ok"))
                .build();

        assertThat(mock.proxy().find("a/b", "a%2Fb", null).block()).isEqualTo("ok");
        assertThat(mock.lastExchange().uri().toASCIIString()).isEqualTo(
                "http://mock.local/base/items/a%2Fb?literal=yes&template=a%252Fb"
                        + "&configured=first&configured=second&repeat=auth%20one");
        assertThat(observed.get().getUriPath()).isEqualTo(
                "/items/{id}?literal=yes&template={template}&repeat=template");
        assertThat(observed.get().getRequestUrl()).isEqualTo(mock.lastExchange().uri().toASCIIString());
    }

    @Test
    void buildRejectsAuthorityInConfiguredApiRefUri() {
        ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();
        ReactiveHttpClientProperties.ApiConfig api = new ReactiveHttpClientProperties.ApiConfig();
        api.setMethod("GET");
        api.setPath("//user:secret-value@example.test/items/{id}?template={template}");
        config.setApis(Map.of("lookup", api));

        assertThatThrownBy(() -> MockReactiveHttpClient.forClient(ApiRefUriClient.class)
                .clientConfig(config)
                .build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("@ApiRef(\"lookup\")")
                .hasMessageContaining("scheme or authority")
                .hasMessageNotContaining("secret-value");
    }

    @Test
    void errorCategoryAssertionUsesPublishedResolverForNonHttpFailures() {
        ErrorCategoryAssertions.assertThatFails(
                        Mono.error(new RuntimeException(new UnknownHostException("missing.local"))))
                .hasErrorCategory(ErrorCategory.UNKNOWN_HOST);

        ErrorCategoryAssertions.assertThatFails(Mono.error(new RuntimeException(
                        new io.github.huynhngochuyhoang.httpstarter.exception.AuthProviderException(
                                "payments-client", new IllegalStateException("token unavailable")))))
                .hasErrorCategory(ErrorCategory.AUTH_PROVIDER_ERROR);
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

    private static ReactiveHttpClientProperties.ClientConfig strictMockRetryConfig() {
        ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();
        config.getResilience().setEnabled(true);
        config.getResilience().setRetry("mock");
        config.getResilience().setRetryMethods(Set.of("POST"));
        config.getResilience().setStrictUnsafeRetryValidation(true);
        return config;
    }

    @Order(10)
    private static final class FirstAnnotationOrderedHook implements ReactiveHttpClientLifecycleHook {
        private final List<String> events;

        private FirstAnnotationOrderedHook(List<String> events) {
            this.events = events;
        }

        @Override
        public void onStart(ReactiveHttpClientLifecycleContext context) {
            events.add("first:start");
        }
    }

    private static final class CountingMethodMetadataCache extends MethodMetadataCache {
        private int getCalls;

        @Override
        public MethodMetadata get(Method method) {
            getCalls++;
            return super.get(method);
        }
    }

    private static final class OpenCircuitResilienceOperatorApplier extends NoopResilienceOperatorApplier {
        private final RuntimeException rejection;

        private OpenCircuitResilienceOperatorApplier(RuntimeException rejection) {
            this.rejection = rejection;
        }

        @Override
        public <V> Mono<V> applyCircuitBreaker(Mono<V> mono, String instanceName) {
            return Mono.error(rejection);
        }

        @Override
        public boolean isOperatorAvailable(InstanceType type) {
            return type == InstanceType.CIRCUIT_BREAKER;
        }
    }

    private static final class StrictRetryApplier extends NoopResilienceOperatorApplier {
        private final boolean canRetryMoreThanOnce;

        private StrictRetryApplier(boolean canRetryMoreThanOnce) {
            this.canRetryMoreThanOnce = canRetryMoreThanOnce;
        }

        @Override
        public boolean isOperatorAvailable(InstanceType type) {
            return type == InstanceType.RETRY;
        }

        @Override
        public boolean canRetryMoreThanOnce(String instanceName) {
            return canRetryMoreThanOnce;
        }
    }

    @Order(20)
    private static final class SecondAnnotationOrderedHook implements ReactiveHttpClientLifecycleHook {
        private final List<String> events;

        private SecondAnnotationOrderedHook(List<String> events) {
            this.events = events;
        }

        @Override
        public void onStart(ReactiveHttpClientLifecycleContext context) {
            events.add("second:start");
        }
    }

    private static final class OrderedRecordingHook implements ReactiveHttpClientLifecycleHook, Ordered {
        private final String name;
        private final int order;
        private final List<String> events;

        private OrderedRecordingHook(String name, int order, List<String> events) {
            this.name = name;
            this.order = order;
            this.events = events;
        }

        @Override
        public int getOrder() {
            return order;
        }

        @Override
        public void onStart(ReactiveHttpClientLifecycleContext context) {
            events.add(name + ":start:" + context.attemptNumber());
        }

        @Override
        public void onRetryAttempt(ReactiveHttpClientLifecycleContext context) {
            events.add(name + ":retry:" + context.attemptNumber());
        }

        @Override
        public void onSuccess(ReactiveHttpClientLifecycleContext context) {
            events.add(name + ":success:" + context.attemptNumber());
        }
    }
}
