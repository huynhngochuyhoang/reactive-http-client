# Annotation Reference

All annotations live in `io.github.huynhngochuyhoang.httpstarter.annotation`.
The annotation contract is unchanged in starter `3.x`; Boot 3.5 applications
remain on `2.14.1`, while Boot 4 applications follow the
[3.x migration guide](28-spring-boot-4-jackson-migration.md).

---

## Client declaration

### `@ReactiveHttpClient`

Marks an interface as a reactive HTTP client managed by the starter.

| Attribute | Type | Default | Description |
|---|---|---|---|
| `name` | `String` | `""` | Logical client name; must match a key in `reactive.http.clients` |
| `baseUrl` | `String` | `""` | Hard-coded base URL (overrides the config entry) |

Client names must match `[A-Za-z0-9][A-Za-z0-9._-]{0,127}`. This keeps property keys, pool names, metric tags, logs, spans, health output, and exception messages aligned.

Either `name` (resolved from config) or `baseUrl` (literal) must be supplied.

```java
// Name-based: base-url comes from reactive.http.clients.payment-service
@ReactiveHttpClient(name = "payment-service")
public interface PaymentClient { ... }

// URL-based: useful for tests or when you don't want a config entry
@ReactiveHttpClient(baseUrl = "http://localhost:8080")
public interface LocalApiClient { ... }
```

`@ReactiveHttpClient` interfaces may extend parent interfaces that declare
endpoint methods. Put `@ReactiveHttpClient` on each concrete downstream client
interface, not on the shared parent, so every child keeps its own client name,
base URL, auth, timeout, resilience, and observability configuration.

```java
public interface UserReadOperations {

    @GET("/users/{id}")
    Mono<UserDto> getUser(@PathVar("id") String id);
}

@ReactiveHttpClient(name = "internal-user-service")
public interface InternalUserClient extends UserReadOperations {
}

@ReactiveHttpClient(name = "partner-user-service")
public interface PartnerUserClient extends UserReadOperations {
}
```

```yaml
reactive:
  http:
    clients:
      internal-user-service:
        base-url: https://internal-users.example.invalid
        request-timeout-ms: 2000
      partner-user-service:
        base-url: https://partner-users.example.invalid
        request-timeout-ms: 8000
```

The starter validates inherited abstract endpoint methods when the proxy is
created. Inherited generic endpoint methods are resolved against each concrete
child interface, so a shared parent can still decode different downstream DTOs:

```java
public interface ApiOperators<T extends BaseResponse> {
    @GET("/api/order")
    Mono<T> getOrder(@QueryParam("orderId") String orderId);
}

@ReactiveHttpClient(name = "bus-api")
public interface BusApiOperators extends ApiOperators<BusResponse> {
}

@ReactiveHttpClient(name = "train-api")
public interface TrainApiOperators extends ApiOperators<TrainResponse> {
}
```

Each child must bind the type it really returns. For example, a train client
that extends `ApiOperators<BusResponse>` will still have a Java contract that
returns `BusResponse`, not `TrainResponse`. Startup DEBUG method-policy logs and
`ReactiveHttpClientContractSnapshot` include `genericBindings`, `responseType`,
and `bodyType` fields so this mismatch is visible without making a request.
Java default methods can still be used as local helper methods without HTTP annotations.

---

## Declarative return types

Endpoint methods must return exactly `Mono` or `Flux`. The supported response
shapes are:

| Return type | Behavior |
|---|---|
| `Mono<Void>` | Drains/releases the response body and completes without a value |
| `Mono<T>` | Decodes one value through the configured WebClient codecs |
| `Mono<ResponseEntity<T>>` | Decodes one value and exposes response status and headers |
| `Flux<T>` | Decodes a stream of values through the configured WebClient codecs |
| `Flux<DataBuffer>` | Streams caller-owned buffers without aggregation |
| `Mono<ResponseEntity<Flux<DataBuffer>>>` | Exposes status and headers with a caller-owned, unaggregated buffer stream |

Raw `Mono` and `Flux` declarations remain accepted for compatibility, but they
erase response type information. Prefer one of the typed forms above.

The starter resolves inherited and multi-level generic bindings against each
concrete `@ReactiveHttpClient` before proxy creation. Unbound type variables,
nested publishers such as `Mono<Mono<T>>` or `Flux<Flux<T>>`, and unsupported
envelopes such as `Flux<ResponseEntity<T>>` or
`Mono<ResponseEntity<Flux<Dto>>>` fail at startup. Use direct `Flux<Dto>` when
only decoded elements are needed, or
`Mono<ResponseEntity<Flux<DataBuffer>>>` when streaming status and headers are
also required.

---

## HTTP verb annotations

Each annotation accepts a single `value` attribute: the path template.
If you use `@ApiRef` on a method, do not add HTTP verb annotations to that method.

| Annotation | HTTP method |
|---|---|
| `@GET(path)` | GET |
| `@POST(path)` | POST |
| `@PUT(path)` | PUT |
| `@DELETE(path)` | DELETE |
| `@PATCH(path)` | PATCH |
| `@HEAD(path)` | HEAD |
| `@OPTIONS(path)` | OPTIONS |

Path templates support `{variable}` placeholders resolved from `@PathVar` parameters.

```java
@GET("/orders/{orderId}/items/{itemId}")
Mono<OrderItem> getItem(
        @PathVar("orderId") long orderId,
        @PathVar("itemId") long itemId);
```

---

## Response envelopes

Use `Mono<ResponseEntity<T>>` when application code needs the upstream status or
headers together with a decoded body:

```java
@POST("/orders")
Mono<ResponseEntity<OrderReceipt>> createOrder(@Body NewOrder request);
```

Use `Mono<ResponseEntity<Void>>` for endpoints where the response metadata is
important but no body is expected:

```java
@DELETE("/orders/{id}")
Mono<ResponseEntity<Void>> deleteOrder(@PathVar("id") long id);
```

For successful `Mono<Void>` and `Mono<ResponseEntity<Void>>` calls, any unexpected
response content is drained or released before completion so pooled connections
remain reusable.

The no-body status contract is the same over HTTP/1.1 and H2/H2C:

| Final response | `Mono<Void>` | `Mono<T>` | `Mono<ResponseEntity<Void>>` | `Mono<ResponseEntity<T>>` |
|---|---|---|---|---|
| `HEAD` | Completes after draining/releasing | Completes empty after draining/releasing | Preserves status and headers; body is null | Preserves status and headers; body is null |
| `204`, `205`, `304` | Completes | Completes empty | Preserves status and headers; body is null | Preserves status and headers; body is null |
| `OPTIONS` | Completes after draining/releasing | Decodes a body when present | Preserves status and headers | Preserves status, headers, and a body when present |

Reactor Netty owns HTTP informational-response parsing. With the currently
supported Reactor Netty line, a raw HTTP/1.1 `103 Early Hints` block
followed by `200` is exposed to WebClient as the `103` response; the starter has
no later `ClientResponse` from which it could recover the final `200`. Do not use
starter response metadata as proof of a later final response when an intermediary
or downstream emits `103`; capture the wire exchange when that distinction matters.

4xx and 5xx responses are still decoded through the configured
`DefaultErrorDecoder` and any registered `ErrorResponseMapper` beans before a
`ResponseEntity` is emitted. Visible 3xx responses remain normal response values.
For large streaming bodies, use `Mono<ResponseEntity<Flux<DataBuffer>>>`
as documented in [11-streaming.md](11-streaming.md).

---

## Parameter annotations

Each endpoint parameter may declare at most one request-binding role. The
starter resolves inherited generic parameter types against the concrete client
and validates this grammar before creating the proxy:

| Role | Annotation and accepted shape | Name policy | Null/value policy |
|---|---|---|---|
| Path | `@PathVar("name")` | Unique, case-sensitive | Required; a null argument fails before auth, body subscription, lifecycle attempts, or dispatch |
| Query | `@QueryParam("name")` | Unique, case-sensitive | Null omits the value; collections/arrays produce repeated values |
| Named header | `@HeaderParam("Name")` | Unique across named headers and parameter `@IdempotencyKey` bindings, case-insensitive | Null omits the header; collections/arrays preserve value order |
| Header map | `@HeaderParam Map<?, ?>` | Dynamic names are checked case-insensitively at invocation | Null entries are omitted; collisions with another method header fail instead of overwriting |
| Idempotency key | Parameter `@IdempotencyKey("Name")` | Shares the named-header namespace | Null omits the key |
| Body | `@Body T` | One body per method | The body type retains its documented scalar/JSON/streaming ownership |
| Form field | `@FormField("name")` on `@MultipartBody` | Repeated names are preserved as repeated parts in parameter/value order | Null omits the part; collections/arrays produce repeated parts |
| Form file | `@FormFile("name")` on `@MultipartBody`; `byte[]`, `Resource`, or `FileAttachment` | Repeated names are preserved as repeated parts | Null omits the part |

Conflicting roles on one parameter, duplicate path/query/named-header bindings,
and unsupported `@FormFile` types fail during startup with the concrete client,
declaring interface, method signature, resolved parameter type, and parameter
index. Method-level generated `@IdempotencyKey` may coexist with an explicit
same-name parameter header because generation runs only when no explicit value
is present.

An unannotated endpoint parameter remains ignored for compatibility with
published clients. Startup emits one warning per method parameter; add one of
the roles above when the value must contribute to the outbound request.

Startup validation checks the declaration: parameter roles, static names,
declared shapes, and URI-template bindings. It does not subscribe to a body or
dispatch a request. Per-invocation validation checks values unavailable at
startup, including a null required path variable, expanded header values, and
case-insensitive collisions introduced by a dynamic header map. Those failures
occur before auth, body subscription, lifecycle attempt callbacks, or transport
dispatch.

### `@PathVar`

Binds a method parameter to a `{name}` placeholder in the path template.

```java
@GET("/users/{id}")
Mono<User> getUser(@PathVar("id") String id);
```

### `@QueryParam`

Appends a query parameter to the request URL. `null` values are omitted.

```java
@GET("/users")
Mono<List<User>> listUsers(
        @QueryParam("page") int page,
        @QueryParam("size") int size,
        @QueryParam("role") String role);   // omitted when null
```

### URI encoding contract

Pass raw, unencoded values to `@PathVar` and `@QueryParam`. The starter delegates
URI construction to Spring's `UriBuilder`, which percent-encodes path variables
and query parameter values when the request URI is built.

An annotation or configured `@ApiRef` endpoint is a path plus an optional query
template. It cannot declare a scheme, authority, user-info, port, or fragment;
those forms fail startup with the client and method named but without echoing the
template. The configured client base URL is the only declarative authority.

Endpoint paths append to the base URL path exactly as Spring's structured URI
builder defines:

| Base URL | Endpoint | Result path |
|---|---|---|
| `https://api.example.invalid/base` | `""` | `/base` |
| `https://api.example.invalid/base` | `items` | `/baseitems` |
| `https://api.example.invalid/base` | `/items` | `/base/items` |
| `https://api.example.invalid/base/` | `items` | `/base/items` |

Use a leading slash on endpoint paths, or a trailing slash on a base path, when
a path-segment boundary is intended.

Examples:

- `@PathVar("key")` value `reports/2026 Q1+draft` is sent as
  `/reports%2F2026%20Q1%2Bdraft`; the slash remains part of the variable, not a
  path separator.
- `@QueryParam("q")` value `a b&c=1` is sent as `q=a%20b%26c%3D1`.
- Unicode values use UTF-8 percent encoding. Path/template-variable reserved
  characters are encoded as variable data. For method/default/auth query values,
  Spring retains query-safe `/`, `+`, and `?` while encoding spaces, `%`, `#`,
  `&`, and `=`; a literal `+` therefore remains `+` on the wire.
- Empty query values are retained as `name=`. `null` query values are omitted.
- Collection or array query values are sent as repeated parameters.

Do not pass pre-encoded values such as `a%2Fb`; the percent sign is treated as a
literal character and encoded again (`a%252Fb` on the wire).

Query entries are deterministic. Literal template entries are created first.
Configured `default-query-params` follow in configuration order. A non-null
method `@QueryParam` replaces a configured default with the same name; it does
not replace a literal template entry, and repeated values remain grouped at that
template name's first position. Auth-provider query values run after URI
expansion, replace all earlier values with the same name, and are appended in
provider order. The auth mutation preserves already encoded path/query bytes.

### `@HeaderParam`

Adds static or dynamic request headers. `null` method arguments are omitted. A
named header parameter accepts a scalar, collection, or array; collection and
array values are sent as repeated header values in caller-provided order. `null`
elements inside a collection or array are skipped. Every expanded value is
validated against CRLF and control characters before the request is built.

```java
// single header
@GET("/reports")
Mono<Report> getReport(@HeaderParam("X-Tenant") String tenant);

// repeated header values
@GET("/reports")
Mono<Report> getReport(@HeaderParam("X-Tag") List<String> tags);

// dynamic header map - each entry may be a scalar, collection, or array
@POST("/events")
Mono<Void> publish(
        @Body Event event,
        @HeaderParam Map<String, Object> extraHeaders);
```

Method `@HeaderParam` values override same-name client default headers
case-insensitively. For example, a method argument named `X-Tenant` replaces a
configured default named `x-tenant` rather than appending to it. Within method
arguments, named and dynamic headers may not collide case-insensitively.

### `@IdempotencyKey`

Adds an outbound idempotency key without spelling the raw header name at every call site. On a parameter, the non-null argument value is sent as `Idempotency-Key`. On a method, the starter generates one key per invocation, but only for that annotated method.

```java
@POST("/payments")
Mono<Payment> createPayment(@Body CreatePayment request, @IdempotencyKey String key);

@POST("/payments")
@IdempotencyKey
Mono<Payment> createPayment(@Body CreatePayment request);
```

The starter only sends the key. It does not provide downstream idempotency storage or duplicate-response replay.

### `@Body`

Marks the parameter that provides the request body. Only one `@Body` parameter is allowed per method; combining `@Body` with `@MultipartBody` on the same method is rejected at startup.

```java
@POST("/users")
Mono<User> createUser(@Body CreateUserRequest request);
```

### `@FormField`

Scalar or multi-value text part of a `@MultipartBody` request.

| Attribute | Type | Description |
|---|---|---|
| `value` | `String` | Part name |

```java
@FormField("description") String description
```

### `@FormFile`

File part of a `@MultipartBody` request.

| Attribute | Type | Default | Description |
|---|---|---|---|
| `value` | `String` | — | Part name |
| `filename` | `String` | `"file"` | Fallback filename sent in `Content-Disposition` |
| `contentType` | `String` | `"application/octet-stream"` | Fallback `Content-Type` |

Accepted parameter types: `byte[]`, any `org.springframework.core.io.Resource`,
or `FileAttachment` (carries bytes + filename + content-type, overriding the
annotation defaults). Unsupported declared types fail at startup, including
after inherited generic bindings are resolved.

See [10-multipart.md](10-multipart.md) for full examples.

---

## Method-level annotations

### `@ApiName`

Sets a human-readable logical name used as the `api.name` tag in metrics and as the span name in traces.

```java
@GET("/users/{id}")
@ApiName("user.getById")
Mono<User> getUser(@PathVar("id") long id);
```

When omitted, observability names fall back to the `@ApiRef` value when present, then to the Java method name.

### `@ApiRef`

References a named API definition from `reactive.http.clients.<client>.apis[<api-name>]`.
This enables dynamic per-client method/path/timeout registration in configuration.

```java
@ApiRef("user-get-by-id")
Mono<User> getUser(@PathVar("id") long id);
```

```yaml
reactive:
  http:
    clients:
      user-service:
        apis:
          user-get-by-id:
            method: GET
            path: /users/{id}
            timeout-ms: 3000
```

Prefer `-` in API keys (for example `user-get-by-id`).
If an API key contains `.`, use bracket notation in `.properties` (for example
`reactive.http.clients.user-service.apis[user.getById].method=GET`).

`.yaml` example with quotes around bracket notation:

```yaml
apis:
  "[user.getById]":
    method: GET
```

When `@ApiRef` is present, `method` and `path` are required in the map entry. Its value is also the observability API-name fallback when `@ApiName` is omitted; this does not change request routing.
`timeout-ms` is optional (`-1` means unset, `0` disables per-request timeout).

### `@TimeoutMs`

Per-method response timeout in milliseconds. Overrides `@ApiRef timeout-ms`, client `request-timeout-ms`, and the deprecated `resilience.timeout-ms` alias. `0` disables the per-request timeout for that method without touching the global safety-net timeouts.

```java
@GET("/users/{id}")
@TimeoutMs(3000)          // fail fast in 3 s
Mono<User> getUser(@PathVar("id") long id);

@POST("/batch-import")
@TimeoutMs(0)             // no per-request timeout; safety-net still applies
Mono<ImportReceipt> batchImport(@Body ImportRequest request);
```

See [04-timeouts.md](04-timeouts.md) for the full timeout-layer precedence model.

### `@MultipartBody`

Marks the method as a `multipart/form-data` request. Combine with `@FormField` and `@FormFile` parameters. See [10-multipart.md](10-multipart.md).

### `@LogHttpExchange`

Hooks request/response logging via an `HttpExchangeLogger` bean at method or client-interface level. Method-level annotation overrides client-level logger. See [13-exchange-logging.md](13-exchange-logging.md).

---

## Resilience overrides (method-level)

These annotations let a single method use a different Resilience4j instance than the client-level default. The client must still have `resilience.enabled: true`.

### `@Retry`

```java
@GET("/users/{id}")
@Retry("user-read-retry")     // must be configured under resilience4j.retry.instances
Mono<User> getUser(@PathVar("id") long id);
```

### `@CircuitBreaker`

```java
@GET("/users/{id}")
@CircuitBreaker("user-read-cb")
Mono<User> getUser(@PathVar("id") long id);
```

### `@Bulkhead`

```java
@POST("/users")
@Bulkhead("user-write-bulkhead")
Mono<User> createUser(@Body NewUser body);
```

### `@RateLimiter`

```java
@POST("/users")
@RateLimiter("user-write-rate-limiter")
Mono<User> createUser(@Body NewUser body);
```

Per-method annotations take precedence over `resilience.retry` /
`.rate-limiter` / `.circuit-breaker` / `.bulkhead`. At proxy construction, the
starter validates an effective method-level instance only when the master gate
is enabled and the matching operator is available. `@Retry` must also be
eligible for the resolved HTTP method. A missing active instance fails fast
with a descriptive `IllegalStateException`; an unavailable operator is reported
as `unavailable` and cannot validate its instance name.

See [07-resilience4j.md](07-resilience4j.md) for full usage and configuration.
