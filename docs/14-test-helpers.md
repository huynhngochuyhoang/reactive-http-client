# Test Helpers (`reactive-http-client-test`)

The starter ships a companion artifact for unit-testing `@ReactiveHttpClient` interfaces without standing up a real HTTP server.

---

## Add the dependency

```xml
<dependency>
  <groupId>io.github.huynhngochuyhoang</groupId>
  <artifactId>reactive-http-client-test</artifactId>
  <version>${reactive-http-client.version}</version>
  <scope>test</scope>
</dependency>
```

---

## `MockReactiveHttpClient`

`MockReactiveHttpClient` builds a real proxy backed by an in-process `ExchangeFunction`, records every outbound exchange, and serves canned responses based on registered matchers.

### Basic setup

```java
MockReactiveHttpClient<UserService> mock = MockReactiveHttpClient.forClient(UserService.class)
        .baseUrl("http://mock.local")
        .respondTo(HttpMethod.GET, "/users/42",
                ex -> MockReactiveHttpClient.json(200, "{\"id\":42,\"name\":\"alice\"}"))
        .respondTo(HttpMethod.POST, "/users",
                ex -> MockReactiveHttpClient.json(201, "{\"id\":7}"))
        .build();
```

### Invoking and asserting

```java
User user = mock.proxy().getUser(42).block();
assertThat(user.getName()).isEqualTo("alice");

RecordedExchange recorded = mock.lastExchange();
RecordedExchangeAssertions.assertThat(recorded)
        .hasMethod(HttpMethod.GET)
        .hasPath("/users/42")
        .hasStatusCode(200);
```

### Unmatched requests

Requests that do not match any registered matcher fall through to a configurable fallback response (HTTP 404 by default), so tests fail loudly instead of hanging on a missing matcher.

---

## JUnit 5 `@MockHttpServer`

Annotate a `MockReactiveHttpClient<T>` field to get a fresh mock before each JUnit 5 test method:

```java
class UserServiceTest {

    @MockHttpServer
    MockReactiveHttpClient<UserService> mock;

    @Test
    void fetchesUser() {
        mock.respondTo(HttpMethod.GET, "/users/42",
                ex -> MockReactiveHttpClient.json(200, "{\"id\":42,\"name\":\"alice\"}"));

        User user = mock.proxy().getUser(42).block();

        assertThat(mock.lastExchange().uri().getPath()).isEqualTo("/users/42");
    }
}
```

The JUnit 5 API dependency is optional in the helper artifact; projects that do not use the extension are not forced to depend on JUnit.

---

## `RecordedExchange`

Every call through the mock proxy is recorded. `RecordedExchange` exposes:

| Method | Returns | Description |
|---|---|---|
| `method()` | `HttpMethod` | HTTP verb of the outbound request |
| `uri()` | `URI` | Full request URI including path and query |
| `headers()` | `HttpHeaders` | Request headers |
| `contentType()` | `MediaType` | `Content-Type` header of the request |
| `header(String)` | `String` | First value of a named header, or `null` |
| `idempotencyKey()` | `String` | First `Idempotency-Key` value, or `null` |
| `statusCode()` | `HttpStatusCode` | HTTP status selected by the mock response handler |
| `statusCodeValue()` | `int` | Numeric HTTP status selected by the mock response handler |
| `bodyAsString()` | `String` | UTF-8 decoded request body; empty string if no body was written |
| `requestContextSnapshot()` | `RequestContextSnapshot` | Starter-owned Reactor context captured by the mock exchange function |
| `correlationId()` | `String` | Captured correlation ID, or `null` if absent |
| `inboundHeaders()` | `Map<String, List<String>>` | Captured filtered inbound headers |
| `materialized()` | `MockClientHttpRequest` | Raw materialised request for low-level inspection |

```java
RecordedExchange exchange = mock.lastExchange();
assertThat(exchange.method()).isEqualTo(HttpMethod.POST);
assertThat(exchange.uri().getPath()).isEqualTo("/users");
assertThat(exchange.headers().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
assertThat(exchange.bodyAsString()).contains("\"name\":\"alice\"");
```

---

## `RecordedExchangeAssertions`

Use the fluent AssertJ bridge when tests need to assert the request and served response in one chain:

```java
RecordedExchangeAssertions.assertThat(mock.lastExchange())
        .hasMethod(HttpMethod.GET)
        .hasPath("/users")
        .hasQueryParamValues("tag", "public", "stable")
        .hasQueryParam("page", "2")
        .hasHeader("X-Tenant", "acme")
        .hasStatusCode(200);
```

For redacted header checks, assert the marker explicitly:

```java
RecordedExchangeAssertions.assertThat(mock.lastExchange())
        .hasRedactedHeader("Authorization");
```

Failure assertions report the expected field and the recorded value:

```java
RecordedExchangeAssertions.assertThat(mock.lastExchange())
        .hasPath("/users/42")
        .hasStatusCode(201);
```

Available assertion methods:

| Method | Description |
|---|---|
| `hasMethod(HttpMethod)` / `hasMethod(String)` | Asserts the HTTP verb |
| `hasPath(String)` | Asserts the URI path |
| `hasQueryParam(String, String)` | Asserts one query parameter value |
| `hasQueryParamValues(String, String...)` | Asserts repeated query parameter values in order |
| `doesNotHaveQueryParam(String)` | Asserts a query parameter is absent |
| `hasHeader(String, String)` | Asserts one request header value |
| `hasHeaderValues(String, String...)` | Asserts repeated request header values in order |
| `hasRedactedHeader(String)` | Asserts the header value is `[REDACTED]` |
| `hasIdempotencyKey()` | Asserts `Idempotency-Key` is present and non-blank |
| `hasIdempotencyKey(String)` | Asserts the `Idempotency-Key` value |
| `doesNotHaveIdempotencyKey()` | Asserts `Idempotency-Key` is absent |
| `doesNotHaveHeader(String)` | Asserts a request header is absent |
| `hasCapturedCorrelationId(String)` | Asserts the captured starter correlation ID |
| `doesNotHaveCapturedCorrelationId()` | Asserts no starter correlation ID was captured |
| `hasInboundHeader(String, String)` | Asserts one captured inbound header value |
| `hasInboundHeaderValues(String, String...)` | Asserts captured inbound header values in order |
| `hasRedactedInboundHeader(String)` | Asserts the captured inbound header value is `[REDACTED]` |
| `doesNotHaveInboundHeader(String)` | Asserts a captured inbound header is absent |
| `hasBody(String)` | Asserts the full UTF-8 request body |
| `bodyContains(String)` | Asserts a substring of the UTF-8 request body |
| `hasStatusCode(int)` | Asserts the served HTTP status |
| `RecordedExchangeAssertions.assertThat(mock).hasAttemptCount(int)` | Asserts total recorded attempts |
| `RecordedExchangeAssertions.assertThat(mock).hasAttemptCount(HttpMethod, String, int)` | Asserts attempts for one method/path |

---


## Retry and idempotency assertions

Use `@IdempotencyKey` plus the mock retry helper when a test needs to prove a transient downstream failure is retried with the same key:

```java
interface PaymentClient {
    @POST("/payments")
    @IdempotencyKey
    Mono<String> create(@Body String body);
}

AtomicInteger served = new AtomicInteger();
MockReactiveHttpClient<PaymentClient> mock = MockReactiveHttpClient
        .forClient(PaymentClient.class)
        .retry(2, "POST")
        .respondTo(HttpMethod.POST, "/payments", exchange -> {
            if (served.incrementAndGet() == 1) {
                return MockReactiveHttpClient.json(503, "{\"error\":\"temporary\"}");
            }
            return MockReactiveHttpClient.json(201, "\"created\"");
        })
        .build();

StepVerifier.create(mock.proxy().create("{\"amount\":10}"))
        .expectNext("\"created\"")
        .verifyComplete();

RecordedExchangeAssertions.assertThat(mock)
        .hasAttemptCount(2)
        .hasAttemptCount(HttpMethod.POST, "/payments", 2);

RecordedExchangeAssertions.assertThat(mock.exchanges().get(0))
        .hasIdempotencyKey();
RecordedExchangeAssertions.assertThat(mock.exchanges().get(1))
        .hasIdempotencyKey(mock.exchanges().get(0).idempotencyKey());
```

The mock retry helper is intentionally small: it retries matching methods inside tests and records each outbound attempt. Production retry semantics still come from your application Resilience4j configuration.

---

## Async context assertions

`MockReactiveHttpClient` captures the starter-owned Reactor context visible to its exchange function. This lets tests prove explicit async handoff before an outbound mock client call:

```java
record EventEnvelope<T>(T payload, RequestContextSnapshot context) {}

Sinks.Many<EventEnvelope<OrderCreated>> sink = Sinks.many().unicast().onBackpressureBuffer();
MockReactiveHttpClient<OrderEventsClient> mock = MockReactiveHttpClient
        .forClient(OrderEventsClient.class)
        .respondTo(HttpMethod.POST, "/events",
                ex -> MockReactiveHttpClient.json(202, "\"accepted\""))
        .build();

StepVerifier.create(sink.asFlux()
                .take(1)
                .flatMap(envelope -> mock.proxy().send(envelope.payload())
                        .contextWrite(envelope.context()::writeTo)))
        .then(() -> Mono.deferContextual(ctx -> {
                    sink.tryEmitNext(new EventEnvelope<>(event, RequestContextSnapshot.capture(ctx))).orThrow();
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
        .doesNotHaveInboundHeader("X-Missing");
```

The assertions read the filtered snapshot captured from Reactor context. They do not inspect raw inbound HTTP requests, so denied headers should be asserted as absent or redacted according to the configured snapshot behavior.

---

## `ErrorCategoryAssertions`

A fluent helper for asserting on the starter's error contract:

```java
ErrorCategoryAssertions.assertThatFails(mock.proxy().getUser(99))
        .hasErrorCategory(ErrorCategory.CLIENT_ERROR)
        .hasStatusCode(404);
```

Available assertion methods:

| Method | Description |
|---|---|
| `hasErrorCategory(ErrorCategory)` | Asserts the `ErrorCategory` of the thrown exception |
| `hasStatusCode(int)` | Asserts the HTTP status code |

The helper uses the same published category names documented in [Error Handling](03-error-handling.md), including transport and resilience categories such as `TLS_ERROR` and `RESILIENCE_ERROR`.

---

## Simulating error responses

```java
MockReactiveHttpClient<UserService> mock = MockReactiveHttpClient.forClient(UserService.class)
        .baseUrl("http://mock.local")
        .respondTo(HttpMethod.GET, "/users/99",
                ex -> MockReactiveHttpClient.json(404, "{\"error\":\"not found\"}"))
        .respondTo(HttpMethod.GET, "/users/1",
                ex -> MockReactiveHttpClient.json(500, "{\"error\":\"internal error\"}"))
        .build();

// Assert 404 -> CLIENT_ERROR
ErrorCategoryAssertions.assertThatFails(mock.proxy().getUser(99))
        .hasErrorCategory(ErrorCategory.CLIENT_ERROR)
        .hasStatusCode(404);

// Assert 500 -> SERVER_ERROR
ErrorCategoryAssertions.assertThatFails(mock.proxy().getUser(1))
        .hasErrorCategory(ErrorCategory.SERVER_ERROR)
        .hasStatusCode(500);
```

---

## Using `MockReactiveHttpClient` in a service unit test

The simplest approach is to build the mock, extract the proxy, and pass it directly to the service under test — no Spring context required:

```java
class UserServiceTest {

    @Test
    void delegatesToUserApiClient() {
        MockReactiveHttpClient<UserApiClient> mock = MockReactiveHttpClient
                .forClient(UserApiClient.class)
                .baseUrl("http://mock.local")
                .respondTo(HttpMethod.GET, "/users/42",
                        ex -> MockReactiveHttpClient.json(200, "{\"id\":42,\"name\":\"alice\"}"))
                .build();

        // Inject the mock proxy directly into the service under test
        UserService service = new UserService(mock.proxy());

        User user = service.getUser("42").block();
        assertThat(user.getId()).isEqualTo(42);

        RecordedExchangeAssertions.assertThat(mock.lastExchange())
                .hasMethod(HttpMethod.GET)
                .hasPath("/users/42")
                .hasStatusCode(200);
    }
}
```
