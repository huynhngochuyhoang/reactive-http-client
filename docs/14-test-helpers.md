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

### Ownership boundary

Use the mock for starter-owned behavior: argument expansion, auth filters and one-time
`401` refresh, logical-call budgets and terminal failure attribution, application JSON
codec bytes, retry subscriptions, request-body materialization and ownership, lifecycle
hooks, observers, exchange logging, inherited generic response types, repeated headers,
and final resolved request metadata.

`MockReactiveHttpClient` uses the same URI-template validation, Spring URI
expansion, default/method/auth query precedence, and final-request observation
filter as the production proxy. Supply configured `@ApiRef` entries through
`clientConfig(...)`; invalid authority or fragment syntax then fails at mock
construction. Recorded URIs are in-process resolved request facts, not proof of
HTTP/1.1 request-line or HTTP/2 pseudo-header bytes.

When a test uses the documented replacement metadata-cache extension, pass that
same cache through `methodMetadataCache(...)`. Validation and invocation then use
the supplied parser instead of a helper-owned default.

The mock does not negotiate an HTTP protocol or TLS, compress wire bytes, acquire a
pooled connection, apply socket backpressure, measure wire or pool timing, or prove
connection reuse. Those behaviors belong to a real client connector and server. Use the
assembled-consumer and transport fixtures described in [Native and release
compatibility](20-native-release-compatibility.md#boot-4-assembled-consumer-fixture)
when a test depends on transport ownership.

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

### Terminal failure semantics

Use `bodyError(...)` when a unit test needs to assert stable behavior after response
status and headers exist, such as timeout category, `RESPONSE_BODY` failure stage,
attempt count, or lifecycle/observer callbacks:

```java
List<HttpClientObserverEvent> observed = new CopyOnWriteArrayList<>();
MockReactiveHttpClient<UserService> mock = MockReactiveHttpClient.forClient(UserService.class)
        .withObserver(observed::add)
        .respondTo(HttpMethod.GET, "/users/42", exchange ->
                MockReactiveHttpClient.bodyError(
                        200,
                        Map.of("X-Timeout-Phase", List.of("body")),
                        ReadTimeoutException.INSTANCE))
        .build();

StepVerifier.create(mock.proxy().getUser(42))
        .expectError(ReadTimeoutException.class)
        .verify();

assertThat(observed.getFirst().getErrorCategory()).isEqualTo(ErrorCategory.TIMEOUT);
assertThat(observed.getFirst().getFailureStage()).isEqualTo(HttpClientFailureStage.RESPONSE_BODY);
```

This helper creates an in-process `ClientResponse` body failure. It does not emulate
DNS, connection, pool acquisition, request writes, socket timing, or enforcement of
`@TimeoutMs`/`request-timeout-ms`. Use a real server and transport for those tests.

The mock can apply the starter-owned end-to-end budget when a unit test needs to
assert deadline cancellation and `LogicalCallTimeoutException` without claiming a
network phase:

```java
MockReactiveHttpClient<UserService> mock = MockReactiveHttpClient
        .forClient(UserService.class)
        .logicalCallTimeout(100)
        .respondTo(HttpMethod.GET, "/users/42", exchange ->
                ClientResponse.create(HttpStatus.OK)
                        .body(Flux.never())
                        .build())
        .build();

StepVerifier.create(mock.proxy().getUser(42))
        .expectErrorSatisfies(error ->
                assertThat(error).isInstanceOf(LogicalCallTimeoutException.class))
        .verify();
```

This proves starter-owned budget behavior only. Pool, connect, write, and response
phase attribution still require real transport fixtures.

### Inherited endpoint contracts

For shared contracts, build the mock for the concrete annotated child client, not
the plain parent interface. The proxy can still call inherited endpoint methods,
and observers/lifecycle hooks use the child client name.

```java
interface UserReadOperations {
    @GET("/users/{id}")
    Mono<UserDto> getUser(@PathVar("id") String id);
}

@ReactiveHttpClient(name = "partner-user-service")
interface PartnerUserClient extends UserReadOperations {
}

MockReactiveHttpClient<PartnerUserClient> mock = MockReactiveHttpClient
        .forClient(PartnerUserClient.class)
        .respondTo(HttpMethod.GET, "/users/42",
                ex -> MockReactiveHttpClient.json(200, "{\"id\":42}"))
        .build();

StepVerifier.create(mock.proxy().getUser("42"))
        .expectNextMatches(user -> user.id() == 42)
        .verifyComplete();

RecordedExchangeAssertions.assertThat(mock.lastExchange())
        .hasMethod(HttpMethod.GET)
        .hasPath("/users/42")
        .hasStatusCode(200);
```

Generic shared contracts are also resolved through the concrete child client. For
example, build `MockReactiveHttpClient<BusApiOperators>` from
`BusApiOperators.class` when `BusApiOperators extends ApiOperators<BusResponse>`;
that lets `Mono<T>` decode as `BusResponse`. A child declared as
`ApiOperators<BusResponse>` still has a bus response contract, so bind each child
to the DTO it actually returns.

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
| `multipartParts()` | `List<RecordedMultipartPart>` | Parsed names, headers, and exact bytes in encoded part order |
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

### Multipart part assertions

`multipartParts()` parses the already materialized `multipart/form-data` body
into immutable `RecordedMultipartPart` values in encoded order. Names,
filenames, read-only headers, and exact bytes are available without depending on
the generated boundary:

```java
RecordedExchange exchange = mock.lastExchange();

RecordedExchangeAssertions.assertThat(exchange)
        .hasMultipartPartNames("description", "file", "tag", "tag")
        .hasMultipartPart(0, "description", "alpha".getBytes(StandardCharsets.UTF_8))
        .hasMultipartPart(1, "file", expectedFileBytes)
        .hasMultipartPartHeader(1, "Content-Type", "application/octet-stream");

RecordedMultipartPart file = exchange.multipartParts().get(1);
assertThat(file.filename()).isEqualTo("payload.bin");
assertThat(file.bodyBytes()).containsExactly(expectedFileBytes);
```

This is an encoded in-process request record. It does not prove multipart wire
framing, resource backpressure, socket cancellation, or connection reuse.

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
| `hasAuthorizationHeader()` | Asserts a non-blank final `Authorization` header without exposing its value |
| `doesNotHaveAuthorizationHeader()` | Asserts the final `Authorization` header is absent; failures redact its value |
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
| `hasMultipartPartNames(String...)` | Asserts encoded multipart part names in order |
| `hasMultipartPart(int, String, byte[])` | Asserts one indexed part name and exact bytes |
| `hasMultipartPartHeader(int, String, String)` | Asserts one indexed part header |
| `RecordedExchangeAssertions.assertThat(mock).hasAttemptCount(int)` | Asserts total recorded attempts |
| `RecordedExchangeAssertions.assertThat(mock).hasAttemptCount(HttpMethod, String, int)` | Asserts attempts for one method/path |

---

### Auth assertions and 401 invalidation

Install an `AuthProvider` directly to inspect the materialized request after the
production outbound auth filter runs:

```java
MockReactiveHttpClient<UserService> mock = MockReactiveHttpClient
        .forClient(UserService.class)
        .withAuthProvider(request -> Mono.just(AuthContext.builder()
                .header("Authorization", "Bearer test-token")
                .build()))
        .respondTo(HttpMethod.GET, "/users/42",
                exchange -> MockReactiveHttpClient.json(200, "{\"id\":42}"))
        .build();

mock.proxy().getUser(42).block();
RecordedExchangeAssertions.assertThat(mock.lastExchange())
        .hasAuthorizationHeader();
```

Installing an auth provider also enables production-style request-body preparation.
Pass the application codec with `jsonCodec(reactiveHttpClientJsonCodec)` when DTOs
depend on custom Jackson modules or naming rules. Starter `3.x` no longer exposes
the Jackson 2 `objectMapper(...)` compatibility adapter.
For ordinary JSON DTO bodies, the provider receives raw `byte[]` matching the
serialized body sent by the mock, so signing providers can verify payload hashes.

`hasAuthorizationHeader()` checks only for a non-blank final
`Authorization` header. `doesNotHaveAuthorizationHeader()` checks absence.
Neither assertion includes the credential in failure output; unexpected values
are reported as `[REDACTED]`.

Use `unauthorizedOnceThen(...)` with any `InvalidatableAuthProvider` to test one
401 invalidation retry without starting an OAuth2 server:

```java
AtomicInteger invalidationCalls = new AtomicInteger();
InvalidatableAuthProvider authProvider = new InvalidatableAuthProvider() {
    @Override
    public Mono<AuthContext> getAuth(AuthRequest request) {
        return Mono.just(AuthContext.builder()
                .header("Authorization", "Bearer test-token")
                .build());
    }

    @Override
    public Mono<Void> invalidate() {
        invalidationCalls.incrementAndGet();
        return Mono.empty();
    }
};

MockReactiveHttpClient<UserService> mock = MockReactiveHttpClient
        .forClient(UserService.class)
        .withAuthProvider(authProvider)
        .respondTo(HttpMethod.GET, "/users/42",
                MockReactiveHttpClient.unauthorizedOnceThen(
                        exchange -> MockReactiveHttpClient.json(200, "{\"id\":42}")))
        .build();

mock.proxy().getUser(42).block();

assertThat(invalidationCalls).hasValue(1);
RecordedExchangeAssertions.assertThat(mock)
        .hasAttemptCount(HttpMethod.GET, "/users/42", 2);
mock.exchanges().forEach(exchange ->
        RecordedExchangeAssertions.assertThat(exchange).hasAuthorizationHeader());
```

The supplied provider can be an in-memory test double. The helper serves one
HTTP 401 and then delegates later requests; the production filter invalidates
auth and sends one more request. Both outbound attempts remain in
`exchanges()`. A composed `401`, `503`, `200` handler sequence produces three
recorded in-process exchanges but two outer retry subscription attempts. That
sequence is canned-response evidence only; it is not a socket-dispatch or
connection-count assertion.

Automatic redirect following belongs to the configured client connector. The mock
can serve and record a visible 3xx response, but `followRedirects=true` does not
create a second in-process exchange. Use a real connector fixture to test redirect
dispatch, replay, and transport timing.

---

### Response factories and repeated headers

`json(status, body)` and `empty(status)` remain the shortest helpers for common
responses. For raw payloads or response headers, use `text(...)`, `bytes(...)`,
or `response(...)`:

```java
MockReactiveHttpClient<ReportClient> mock = MockReactiveHttpClient
        .forClient(ReportClient.class)
        .respondTo(HttpMethod.GET, "/reports", ex ->
                MockReactiveHttpClient.text(200, "ok",
                        Map.of("X-Trace", List.of("trace-1", "trace-2"))))
        .build();
```

Repeated outbound request headers can be asserted in order:

```java
mock.proxy().getReports(List.of("finance", "q1")).block();

RecordedExchangeAssertions.assertThat(mock.lastExchange())
        .hasHeaderValues("X-Tag", "finance", "q1");
```

For bodiless endpoints that unexpectedly return content, serve a raw text body
and assert the call still completes:

```java
mock.respondTo(HttpMethod.DELETE, "/sessions/s-1",
        ex -> MockReactiveHttpClient.text(200, "unexpected-body"));

StepVerifier.create(mock.proxy().closeSession("s-1"))
        .verifyComplete();
```

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

The mock retry helper is intentionally small: it retries matching methods inside tests and records each outbound attempt. A publisher request body remains cold until the mock exchange runs and is materialized once for each recorded retry attempt. Application-owned `InputStream`, `Reader`, and `ReadableByteChannel` bodies are also materialized and closed by that in-process exchange. One-shot streams are not made replayable. The source must therefore be replayable, matching the runtime [request-body repeatability matrix](11-streaming.md#request-body-repeatability-matrix). The helper aggregates the body only to expose `RecordedExchange.bodyAsString()`; it does not emulate socket demand, connection-pool queues, transport cancellation, redirects, or network write ownership. Production retry semantics still come from your application Resilience4j configuration.

Multipart requests are likewise materialized in-process. Prefer
`multipartParts()`, `hasMultipartPartNames(...)`, `hasMultipartPart(...)`,
and `hasMultipartPartHeader(...)` for stable name, header, order, and exact-byte
assertions that do not depend on the generated boundary. These records do not
prove HTTP/1.1 framing, HTTP/2 DATA delivery, resource backpressure, socket
cancellation, pool reuse, redirect replay, or peer-reset cleanup; use an HTTP
server fixture for those transport contracts.

## Observer and lifecycle assertions

Attach a custom observer and one or more lifecycle hooks when a test needs to
assert logical-call telemetry or retry subscription boundaries:

```java
List<HttpClientObserverEvent> observed = new ArrayList<>();
List<Integer> attempts = new ArrayList<>();

ReactiveHttpClientLifecycleHook hook = new ReactiveHttpClientLifecycleHook() {
    @Override
    public void onStart(ReactiveHttpClientLifecycleContext context) {
        attempts.add(context.attemptNumber());
    }

    @Override
    public void onRetryAttempt(ReactiveHttpClientLifecycleContext context) {
        attempts.add(context.attemptNumber());
    }
};

MockReactiveHttpClient<PaymentClient> mock = MockReactiveHttpClient
        .forClient(PaymentClient.class)
        .retry(2, "POST")
        .withObserver(observed::add)
        .withLifecycleHook(hook)
        .respondTo(HttpMethod.POST, "/payments",
                ex -> MockReactiveHttpClient.json(201, "\"created\""))
        .build();
```

The observer receives one terminal event for the logical call, including the
final subscription-attempt count and the final outbound URL and headers. The mock
uses `@ReactiveHttpClient.name()` when the interface is annotated and falls back
to `mock-client` for the helper's supported unannotated interfaces. Lifecycle
hooks receive `onStart` for attempt `1` and `onRetryAttempt` for later retry
subscriptions. Repeated
`withLifecycleHook(...)` calls accumulate hooks; hooks implementing `Ordered` or
annotated with `@Order` run with the same Spring ordering semantics as starter
beans. The helper does not register Micrometer or OpenTelemetry observers unless
the test supplies one explicitly.

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

The helper delegates to `ErrorCategories.from(...)`, so wrapped HTTP, auth,
transport, cancellation, decode, and resilience failures use the same bounded
precedence contract as runtime diagnostics. `hasStatusCode(int)` remains limited to
HTTP exceptions that carry a status.

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

---

## `ReactiveHttpClientContractSnapshot`

`ReactiveHttpClientContractSnapshot` renders the effective declarative contract as a deterministic Markdown table. It is useful for approval-style tests around shared parent interfaces, per-client `@ApiRef` maps, timeout policy, redirect policy, and inherited endpoint metadata. It does not create a Spring context.

```java
@ReactiveHttpClient(name = "internal-users")
interface InternalUsersClient extends SharedUserOperations {}

@ReactiveHttpClient(name = "partner-users")
interface PartnerUsersClient extends SharedUserOperations {}

interface SharedUserOperations {
    @ApiRef("users.get")
    Mono<String> getUser(@PathVar("id") String id);
}

@Test
void sharedContractSnapshot() {
    ClientConfig internal = new ClientConfig();
    internal.setBaseUrl("https://internal.example");
    internal.setRequestTimeoutMs(1000);
    internal.setApis(Map.of("users.get", api("GET", "/internal/users/{id}")));

    ClientConfig partner = new ClientConfig();
    partner.setBaseUrl("https://partner.example");
    partner.setRequestTimeoutMs(2000);
    partner.setApis(Map.of("users.get", api("GET", "/partner/users/{id}")));

    String snapshot = ReactiveHttpClientContractSnapshot.markdown()
            .client(InternalUsersClient.class, internal)
            .client(PartnerUsersClient.class, partner)
            .filterMethod("getUser")
            .render();

    assertThat(snapshot).contains("| internal-users |");
    assertThat(snapshot).contains("/partner/users/{id}");
}

private static ApiConfig api(String method, String path) {
    ApiConfig api = new ApiConfig();
    api.setMethod(method);
    api.setPath(path);
    return api;
}
```

Rows are sorted by client name, declaring interface, and Java method signature. Use `filterClient("client-name")` and `filterMethod("methodName")` to keep snapshots focused. For inherited generic endpoints, the table includes the parent type-variable binding plus resolved response and request-body types, which makes an accidental declaration such as `TrainClient extends ApiOperators<BusResponse>` visible as `T=BusResponse`. The output is sanitized: it includes contract metadata such as base URL, timeout source, resilience operator names, resolved type names, and body repeatability, but not auth secrets, default header values, proxy credentials, or request bodies.
