package io.github.huynhngochuyhoang.httpstarter.test;

import io.github.huynhngochuyhoang.httpstarter.annotation.*;
import io.github.huynhngochuyhoang.httpstarter.auth.AuthContext;
import io.github.huynhngochuyhoang.httpstarter.auth.AuthRequest;
import io.github.huynhngochuyhoang.httpstarter.auth.InvalidatableAuthProvider;
import io.github.huynhngochuyhoang.httpstarter.core.ReactiveHttpClientLifecycleContext;
import io.github.huynhngochuyhoang.httpstarter.core.ReactiveHttpClientLifecycleHook;
import io.github.huynhngochuyhoang.httpstarter.core.RequestContext;
import io.github.huynhngochuyhoang.httpstarter.core.RequestContextSnapshot;
import io.github.huynhngochuyhoang.httpstarter.exception.ErrorCategory;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientObserverEvent;
import org.junit.jupiter.api.Test;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
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

    record SignedRequest(String orderId, int amount) {}

    interface SharedCatalogOperations {
        @GET("/catalog/{id}")
        Mono<String> getCatalog(@PathVar("id") String id);
    }

    @ReactiveHttpClient(name = "inherited-named-client")
    interface InheritedNamedClient extends SharedCatalogOperations {
    }

    @ReactiveHttpClient(name = "named-client")
    interface NamedClient {
        @GET("/items/{id}")
        Mono<String> getItem(@PathVar("id") long id, @HeaderParam("X-Trace") String trace);
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
        MockReactiveHttpClient<SampleClient> mock = MockReactiveHttpClient.forClient(SampleClient.class)
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
        assertThat(serializedBody).contains("orderId", "order-1", "amount", "10");
        assertThat(mock.lastExchange().bodyAsString()).isEqualTo(serializedBody);
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
