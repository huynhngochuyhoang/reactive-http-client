# reactive-http-client

[![Maven Central](https://img.shields.io/maven-central/v/io.github.huynhngochuyhoang/reactive-http-client-starter.svg)](https://search.maven.org/artifact/io.github.huynhngochuyhoang/reactive-http-client-starter)
[![CI](https://github.com/huynhngochuyhoang/reactive-http-client/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/huynhngochuyhoang/reactive-http-client/actions/workflows/ci.yml)

A Spring Boot starter for building **declarative reactive HTTP clients** (annotation-driven WebFlux clients) with:
- request/response mapping
- timeout handling
- error decoding
- optional Resilience4j integration
- optional Micrometer observability
- correlation ID propagation
- outbound auth strategy per client (OAuth2/API key/HMAC/custom)

---

## Documentation

| Guide | Description |
|---|---|
| [Quick Start](docs/01-quick-start.md) | Add the dependency, declare a client, and inject it |
| [Annotation Reference](docs/02-annotations.md) | All annotations with examples |
| [Error Handling](docs/03-error-handling.md) | Exception hierarchy, error categories, and reactive operators |
| [Timeouts](docs/04-timeouts.md) | Timeout layers, precedence model, and per-method override |
| [Connection Pool](docs/05-connection-pool.md) | Pool tuning, per-client overrides, and pool metrics |
| [Outbound Auth Providers](docs/06-auth-providers.md) | Bearer, OAuth2, HMAC, API key, and custom providers |
| [Resilience4j Integration](docs/07-resilience4j.md) | Retry, circuit breaker, bulkhead, and per-method overrides |
| [Observability](docs/08-observability.md) | Micrometer metrics, health indicator, OpenTelemetry tracing |
| [Correlation ID](docs/09-correlation-id.md) | Inbound capture, outbound propagation, and inbound header filtering |
| [Multipart Uploads](docs/10-multipart.md) | `@MultipartBody`, `@FormField`, `@FormFile`, and `FileAttachment` |
| [Streaming Responses](docs/11-streaming.md) | `Flux<DataBuffer>` and `Mono<ResponseEntity<Flux<DataBuffer>>>` |
| [Proxy & TLS / mTLS](docs/12-proxy-tls.md) | HTTP proxy routing and custom TLS/mTLS configuration |
| [Exchange Logging](docs/13-exchange-logging.md) | `@LogHttpExchange`, `HttpExchangeLogger`, and custom loggers |
| [Test Helpers](docs/14-test-helpers.md) | `MockReactiveHttpClient`, `RecordedExchange`, `ErrorCategoryAssertions` |

---

## 1) Why reactive-http-client beats Spring `@HttpExchange`

### Overview

Spring's `@HttpExchange` gives you declarative HTTP mapping and nothing more. **reactive-http-client** starts where `@HttpExchange` stops: it adds a production-ready platform of cross-cutting concerns — resilience, auth strategy, structured observability, network policy, and test helpers — wired and opinionated from day one, so you write business logic instead of boilerplate infrastructure.

| Capability | This starter | Spring `@HttpExchange` |
|---|---|---|
| Declarative interface | `@ReactiveHttpClient` + `@GET/@POST/@PUT/@DELETE/@PATCH` | `@HttpExchange` + method annotations |
| Client-level config model | `reactive.http.clients.<name>` and global `reactive.http.network` | No equivalent built-in config namespace |
| Timeout precedence model | Method-level `@TimeoutMs` over config timeout | No built-in timeout precedence contract |
| Error classification contract | Built-in `HttpClientException` / `RemoteServiceException` + categories | No built-in domain error categorization |
| Resilience4j integration | Built-in opt-in (retry/circuit-breaker/bulkhead) per client | Not built-in; app wires resilience manually |
| Outbound auth strategy | Per-client `AuthProvider` + built-in token refresh provider | Not built-in; app implements filters/interceptors |
| Correlation ID propagation | Built-in support | App-level implementation |
| Micrometer observability contract | Built-in observer with stable tags | Basic instrumentation is app/framework-dependent |

### Practical conclusion

- Choose this **starter** when you want a ready-to-use platform for outbound HTTP governance with consistent policies, resilience, auth, and observability across every client — with zero boilerplate infrastructure.
- Choose **Spring `@HttpExchange`** when you prefer minimal abstraction and full manual control over cross-cutting concerns.

---

## 2) Quick Start

### 2.1 Requirements
- Java 21+
- Spring Boot 3.x
- Maven 3.8+

### 2.2 Add the starter dependency

```xml
<dependency>
  <groupId>io.github.huynhngochuyhoang</groupId>
  <artifactId>reactive-http-client-starter</artifactId>
  <version>1.10.1</version>
</dependency>
```

In WebFlux applications, also include `spring-boot-starter-webflux`.

### 2.3 Enable client scanning

```java
@SpringBootApplication
@EnableReactiveHttpClients(basePackages = "com.myapp.client")
public class MyApp {
    public static void main(String[] args) {
        SpringApplication.run(MyApp.class, args);
    }
}
```

### 2.4 Define a client interface

```java
@ReactiveHttpClient(name = "user-service")
public interface UserApiClient {

    @GET("/users/{id}")
    @ApiName("user.getById")
    @TimeoutMs(5000)
    Mono<UserDto> getUser(
            @PathVar("id") String id,
            @QueryParam("expand") String expand
    );

    @POST("/users")
    Mono<UserDto> createUser(
            @Body CreateUserRequest body,
            @HeaderParam("X-Tenant") String tenant
    );
}
```

### 2.5 Configure `application.yml`

```yaml
reactive:
  http:
    correlation-id:
      max-length: 128
      mdc-keys: [correlationId, X-Correlation-Id, traceId]   # MDC fallback order; empty list disables fallback
    network:
      connect-timeout-ms: 2000
      network-read-timeout-ms: 60000    # Netty ReadTimeoutHandler safety net
      network-write-timeout-ms: 60000   # Netty WriteTimeoutHandler safety net
      connection-pool:
        max-connections: 200
        pending-acquire-timeout-ms: 5000
        max-idle-time-ms: 30000         # evict idle connections after 30 s (0 = off)
        max-life-time-ms: 300000        # recycle pooled connections after 5 min (0 = unlimited)
        evict-in-background-ms: 60000   # background eviction sweep interval (0 = off)
      proxy:                             # optional outbound HTTP proxy
        type: HTTP                       # HTTP | HTTPS | SOCKS4 | SOCKS5 | NONE
        host: proxy.corp.example
        port: 3128
        # username, password, non-proxy-hosts (regex) optional
      tls:                               # optional custom SSL / mTLS
        trust-store: classpath:certs/truststore.p12
        trust-store-password: changeit
        key-store: classpath:certs/client.p12
        key-store-password: changeit
        # protocols, ciphers, insecure-trust-all optional
    clients:
      user-service:
        base-url: https://api.example.com
        auth-provider: userServiceAuthProvider
        codec-max-in-memory-size-mb: 2
        compression-enabled: false
        log-exchange: false
        pool:                            # optional per-client pool override
          max-connections: 500           # inherits the global pool when omitted
        proxy:                           # optional per-client proxy override (or `type: NONE` to bypass global)
          type: NONE
        tls:                             # optional per-client TLS override
          trust-store: classpath:certs/partner-ts.p12
          trust-store-password: ${PARTNER_TS_PWD}
        resilience:
          enabled: true
          circuit-breaker: user-service
          retry: user-service
          retry-methods: [GET, HEAD]
          bulkhead: user-service
          timeout-ms: 0
```

#### Timeout layers: which one fires first?

Two independent timeouts act on every outbound call. Pick the right layer for the right concern.

| Layer | Property / annotation | Default | Scope | Fires when |
|---|---|---|---|---|
| Connect timeout | `reactive.http.network.connect-timeout-ms` | 2 000 ms | TCP handshake only | New connection cannot be established |
| Per-request response timeout | `@TimeoutMs(...)` (method) > `resilience.timeout-ms` (client) | disabled | Per attempt | An attempt produces no response within the limit; retries each get their own budget |
| Safety-net read timeout | `reactive.http.network.network-read-timeout-ms` (alias: `read-timeout-ms`) | 60 000 ms | Per pooled connection | No inbound bytes for this duration — catches stuck sockets, not slow responses |
| Safety-net write timeout | `reactive.http.network.network-write-timeout-ms` (alias: `write-timeout-ms`) | 60 000 ms | Per pooled connection | No outbound bytes accepted for this duration |

**Rule of thumb:** set `network-read-timeout-ms` / `network-write-timeout-ms` well above the largest `@TimeoutMs` or `resilience.timeout-ms` value you expect. Whichever fires first wins; you want the per-request timeout to win so retries behave predictably. `@TimeoutMs(0)` disables the per-request timeout for one method without touching the safety nets.

The legacy property names `read-timeout-ms` / `write-timeout-ms` still bind for backwards compatibility but are deprecated — IDEs will flag them. Prefer the `network-*` names.

#### Connection-pool tuning

`reactive.http.network.connection-pool` configures the global defaults applied to every client. Any field may be overridden per client via `reactive.http.clients.<name>.pool.*` — when present, the client-level block replaces the global block wholesale (no field merging). Typical usage: bump `max-connections` for a hot internal service while leaving the default for low-volume partners.

`max-idle-time-ms` and `max-life-time-ms` are critical behind load balancers that silently drop long-idle sockets. When set, connections are evicted before a half-dead socket is handed out. `evict-in-background-ms` controls how often the provider sweeps for evictable entries; `0` (the default) disables the sweep and relies on acquire-time checks only.

### 2.5.1 Outbound auth provider (per client)

Each external client can map to its own `AuthProvider` bean via `auth-provider`.
The provider returns an `AuthContext` that can inject headers and query params automatically via WebClient filter.
For body-signing use cases (e.g. HMAC), providers should sign the raw payload bytes when available
(`request.request().attribute(AuthRequest.REQUEST_RAW_BODY_ATTRIBUTE)`), and only fall back to `request.requestBody()` when raw bytes are absent.
For bearer-token flows, use built-in `RefreshingBearerAuthProvider` + `AccessTokenProvider` to standardize token cache/rotation.

```java
@Bean("userServiceAuthProvider")
AuthProvider userServiceAuthProvider(TokenService tokenService) {
    return request -> tokenService.getAccessToken()
            .map(token -> AuthContext.builder()
                    .header("Authorization", "Bearer " + token)
                    .build());
}
```

```java
@Bean("oauthAuthProvider")
AuthProvider oauthAuthProvider(OAuthTokenClient tokenClient) {
    return new RefreshingBearerAuthProvider(
            () -> tokenClient.issueToken()
                    .map(resp -> new AccessToken(
                            resp.accessToken(),
                            Instant.now().plusSeconds(resp.expiresInSeconds())
                    )),
            Duration.ofSeconds(60) // refresh before expiry
    );
}
```

For standard OAuth 2.0 client-credentials flows, the starter ships
`OAuth2ClientCredentialsTokenProvider` so you don't need to hand-roll the
token-endpoint client. Compose it with `RefreshingBearerAuthProvider` for
caching + single-in-flight-refresh:

```java
@Bean("userServiceAuthProvider")
AuthProvider userServiceAuthProvider(WebClient.Builder builder) {
    OAuth2ClientCredentialsTokenProvider tokenProvider =
            OAuth2ClientCredentialsTokenProvider.builder(builder.build())
                    .tokenUri("https://auth.example.com/oauth/token")
                    .clientId("user-service")
                    .clientSecret("...")
                    .scope("read:users")
                    // .audience("https://api.example.com/")        // optional
                    // .authStyle(AuthStyle.FORM_POST)              // default is BASIC_AUTH
                    // .expiryLeeway(Duration.ofSeconds(30))        // refresh slightly early
                    .build();
    return new RefreshingBearerAuthProvider(tokenProvider);
}
```

Supports both standard client-authentication schemes (HTTP Basic by default;
opt into form-post via `authStyle(AuthStyle.FORM_POST)`), forwards optional
`scope` / `audience` parameters, and converts the server's `expires_in`
(seconds) into an `AccessToken.expiresAt()` minus the configurable
`expiryLeeway` so the cached token is refreshed slightly before expiry.

`RefreshingBearerAuthProvider` behavior:
- caches the latest token value
- refreshes when token enters the refresh window (`expiresAt - refreshSkew`)
- deduplicates concurrent refresh calls (single in-flight token fetch)
- supports cache invalidation (used by outbound auth filter to refresh and retry once on HTTP 401)
- supports non-expiring tokens by returning `expiresAt = null`

```java
@Bean("hmacAuthProvider")
AuthProvider hmacAuthProvider(HmacSigner signer) {
    return request -> Mono.fromSupplier(() -> {
        byte[] payload = request.request().attribute(AuthRequest.REQUEST_RAW_BODY_ATTRIBUTE)
                .map(byte[].class::cast)
                .orElseGet(() -> java.util.Objects.toString(request.requestBody(), "").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String signature = signer.sign(payload);
        return AuthContext.builder()
                .header("X-Signature", signature)
                .build();
    });
}
```

### 2.5.2 Multipart / form-data uploads

Annotate a method with `@MultipartBody` and supply parts via `@FormField`
(scalar) or `@FormFile` (file) parameters. The starter builds a
`multipart/form-data` body via Spring's `MultipartBodyBuilder`; the
`Content-Type` header (with the correct boundary) is generated for you.

```java
@ReactiveHttpClient(name = "user-service")
public interface UserService {

    @POST("/users/{id}/avatar")
    @MultipartBody
    Mono<Void> uploadAvatar(
            @PathVar("id") long userId,
            @FormField("description") String description,
            @FormFile(value = "avatar", filename = "fallback.bin",
                      contentType = "image/png") Resource avatar);

    @POST("/imports")
    @MultipartBody
    Mono<ImportReceipt> importCsv(
            @FormField("source") String source,
            @FormFile(value = "file", filename = "data.csv",
                      contentType = "text/csv") byte[] csvBytes);
}
```

`@FormFile` accepts `byte[]`, any `org.springframework.core.io.Resource`, or
the convenience record `io.github.huynhngochuyhoang.httpstarter.core.FileAttachment`
(carries bytes + filename + content-type, overriding the annotation defaults).
Combining `@MultipartBody` with `@Body` on the same method is rejected at
metadata-parse time — pick one.

### 2.5.3 HTTP proxy & TLS / mTLS

Both routing through an outbound proxy and presenting a client certificate
for mTLS are configured under `reactive.http.network.proxy.*` and
`reactive.http.network.tls.*` (global) — and can be overridden per client
under `reactive.http.clients.<name>.proxy.*` / `.tls.*`. When a per-client
override is present, the entire block replaces the global one (no
field-level merging).

Proxy types: `HTTP`, `HTTPS`, `SOCKS4`, `SOCKS5`, plus `NONE` to explicitly
disable an inherited global proxy on a single client.

```yaml
reactive:
  http:
    network:
      proxy:
        type: HTTP
        host: proxy.corp.example
        port: 3128
        username: ${PROXY_USER}
        password: ${PROXY_PASS}
        non-proxy-hosts: "localhost|.*\\.internal"   # Java regex (not glob)
      tls:
        trust-store: classpath:certs/truststore.p12
        trust-store-password: changeit
        key-store: classpath:certs/client.p12        # client cert for mTLS
        key-store-password: changeit
        protocols: [TLSv1.3, TLSv1.2]
        # ciphers: [...]
        # insecure-trust-all: true        # development only — emits a startup WARN
```

Truststore / keystore paths are resolved through Spring's
`DefaultResourceLoader`, so `classpath:`, `file:`, and absolute filesystem
paths all work. Setting `insecure-trust-all: true` disables certificate
validation; the starter logs a WARN at startup so it's never an accident.
`non-proxy-hosts` is a Java regex pattern (not a glob), pipe-separated for
alternatives — use `.*\.internal`, not `*.internal`.

### 2.5.4 Streaming responses

Methods declaring `Flux<DataBuffer>` or `Mono<ResponseEntity<Flux<DataBuffer>>>`
skip the in-memory codec entirely, so payloads larger than
`codec-max-in-memory-size-mb` are streamed without a `DataBufferLimitException`.
The `ResponseEntity` variant exposes the upstream status and headers
alongside the streaming body — useful for proxy / pass-through
implementations:

```java
@ReactiveHttpClient(name = "object-store")
public interface ObjectStoreClient {

    @GET("/objects/{key}")
    Flux<DataBuffer> download(@PathVar("key") String key);

    @GET("/objects/{key}")
    Mono<ResponseEntity<Flux<DataBuffer>>> downloadEntity(@PathVar("key") String key);
}
```

Buffers are released by Reactor Netty as the consumer drives the `Flux`,
so memory usage stays bounded regardless of payload size. Standard
observability (duration, response size from `Content-Length`) still applies.

### 2.6 Inject and use

```java
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserApiClient userApiClient;

    public Mono<UserDto> getUser(String id) {
        return userApiClient.getUser(id, null);
    }
}
```

---

## 3) Core call pipeline

Each proxy invocation follows this pipeline:

1. Parse method metadata from annotations.
2. Resolve arguments (`@PathVar`, `@QueryParam`, `@HeaderParam`, `@Body`).
3. Execute WebClient request.
4. Decode errors:
   - 4xx -> `HttpClientException`
   - 5xx -> `RemoteServiceException`
5. Apply timeout (priority: `@TimeoutMs` > `resilience.timeout-ms`) per attempt.
6. Apply resilience (if enabled): retry -> circuit-breaker -> bulkhead.
7. Emit observability event (if observer is configured).

---

## 4) Annotation reference

| Annotation | Target | Description |
|---|---|---|
| `@ReactiveHttpClient(name, baseUrl)` | Interface | Declares an HTTP client |
| `@GET/@POST/@PUT/@DELETE/@PATCH(path)` | Method | HTTP verb + path |
| `@PathVar(name)` | Parameter | Path variable |
| `@QueryParam(name)` | Parameter | Query parameter |
| `@HeaderParam(name)` | Parameter | Header parameter |
| `@HeaderParam Map<String, String>` | Parameter | Dynamic header map |
| `@Body` | Parameter | Request body |
| `@MultipartBody` | Method | Marks the request as `multipart/form-data` (incompatible with `@Body`) |
| `@FormField(name)` | Parameter | Scalar / multi-value text part of a `@MultipartBody` request |
| `@FormFile(name, filename, contentType)` | Parameter | File part of a `@MultipartBody` request — accepts `byte[]`, `Resource`, or `FileAttachment` |
| `@ApiName("...")` | Method | Logical API name for metrics/tracing |
| `@TimeoutMs(ms)` | Method | Method-level timeout override (`0` disables timeout for that method) |
| `@Retry("instance")` | Method | Per-method Resilience4j Retry instance — overrides `resilience.retry` |
| `@CircuitBreaker("instance")` | Method | Per-method Resilience4j CircuitBreaker instance — overrides `resilience.circuit-breaker` |
| `@Bulkhead("instance")` | Method | Per-method Resilience4j Bulkhead instance — overrides `resilience.bulkhead` |
| `@LogHttpExchange` | Method | Request/response log hook via `HttpExchangeLogger` |

---

## 5) Error handling contract

| Case | Exception | Category |
|---|---|---|
| 429 | `HttpClientException` | `RATE_LIMITED` |
| Other 4xx | `HttpClientException` | `CLIENT_ERROR` |
| 5xx | `RemoteServiceException` | `SERVER_ERROR` |
| 2xx but response decode/deserialization fails (`bodyToMono`/`bodyToFlux`) | Codec/decoding error | `RESPONSE_DECODE_ERROR` (observability) |
| Timeout | `TimeoutException` | `—` (normalized as `TIMEOUT` in observability) |
| Connect failure | `ConnectException` | `CONNECT_ERROR` (observability) |
| DNS resolution failure | `UnknownHostException` | `UNKNOWN_HOST` (observability) |

Both main exception types expose:
- `getStatusCode()`
- `getResponseBody()`
- `getErrorCategory()`

---

## 6) Resilience4j integration

The starter supports client-level Resilience4j configuration.
Retryable HTTP verbs are configurable via `reactive.http.clients.<name>.resilience.retry-methods`
(default: **GET/HEAD**).

```yaml
reactive:
  http:
    clients:
      user-service:
        resilience:
          enabled: true
          retry: user-service
          retry-methods: [GET, HEAD, PUT]
```

### Per-method overrides

One client typically fronts several endpoints with different sensitivity to retry / circuit-breaker / bulkhead policy — e.g. a hot read path vs. an expensive write. The `@Retry` / `@CircuitBreaker` / `@Bulkhead` annotations let a single method opt into a different Resilience4j instance:

```java
@ReactiveHttpClient(name = "user-service")
public interface UserApi {

    @GET("/users/{id}")
    @Retry("user-read-retry")              // 5 attempts, 100ms backoff
    @CircuitBreaker("user-read-cb")        // wide open, 50% failure threshold
    Mono<User> getUser(@PathVar("id") long id);

    @POST("/users")
    @Bulkhead("user-write-bulkhead")       // limit concurrent writes
    Mono<User> createUser(@Body NewUser body);
}
```

Per-method instance names take precedence over the client-level
`resilience.retry` / `.circuit-breaker` / `.bulkhead` settings. An instance referenced by an annotation **must** be configured (e.g. under `resilience4j.retry.instances.user-read-retry` in `application.yml`); the starter walks every annotated method at proxy-construction time and fails fast with a descriptive `IllegalStateException` listing every missing instance, so a typo can't silently fall back to a default-configured instance.

Per-method annotations are still gated on the client having `resilience.enabled = true`. Methods without an override inherit the client-level config, exactly as before.

### Common dependencies in consumer apps

```xml
<dependency>
  <groupId>io.github.resilience4j</groupId>
  <artifactId>resilience4j-spring-boot3</artifactId>
</dependency>
<dependency>
  <groupId>io.github.resilience4j</groupId>
  <artifactId>resilience4j-reactor</artifactId>
</dependency>
```

---

## 7) Observability (Micrometer)

When a `MeterRegistry` is present, the starter records four metrics per exchange:

**`http.client.requests`** (Timer) — duration from first attempt to final completion:

| Tag | Values |
|---|---|
| `client.name` | logical client name |
| `api.name` | `@ApiName` value or method name |
| `http.method` | `GET`, `POST`, … |
| `http.status_code` | numeric code or `NONE` |
| `outcome` | `SUCCESS`, `REDIRECTION`, `CLIENT_ERROR`, `SERVER_ERROR`, `UNKNOWN` |
| `exception` | simple class name or `none` |
| `error.category` | `RATE_LIMITED`, `CLIENT_ERROR`, `SERVER_ERROR`, `TIMEOUT`, `CANCELLED`, `AUTH_PROVIDER_ERROR`, `RESPONSE_DECODE_ERROR`, `UNKNOWN`, `none` |
| `uri` | path template or `NONE` (disable via `include-url-path: false`) |

**`http.client.requests.attempts`** (DistributionSummary) — number of subscription attempts per invocation (1 = succeeded on first try; >1 = Resilience4j retry fired). Tags: `client.name`, `api.name`, `http.method`, `uri`. A p95 > 1 is a signal that a downstream service is degraded.

**`http.client.requests.request.size`** (DistributionSummary) — serialised request body bytes. Recorded only when the body is measurable cheaply: `byte[]` (length), `String` / `CharSequence` (UTF-8 byte length), or `null` (`0`). Arbitrary objects (POJOs serialised by the codec) are **not** measured to avoid double-serialisation cost. Tags: `client.name`, `api.name`, `http.method`, `uri`.

**`http.client.requests.response.size`** (DistributionSummary) — response body bytes as advertised by the server via `Content-Length`. Chunked responses and responses without the header are skipped. Tags: `client.name`, `api.name`, `http.method`, `uri`.

### Connection-pool metrics (Reactor Netty)

When `reactive.http.network.connection-pool.metrics-enabled: true` (or the same key on a per-client `pool` block), the `ConnectionProvider` publishes Reactor Netty's built-in pool gauges to the global `MeterRegistry`:

- `reactor.netty.connection.provider.total.connections`
- `reactor.netty.connection.provider.active.connections`
- `reactor.netty.connection.provider.idle.connections`
- `reactor.netty.connection.provider.pending.connections`

All gauges are tagged with the pool name (`reactive-http-client-<clientName>`). Off by default — a small per-request overhead is paid when enabled.

### Actuator health indicator

When `spring-boot-starter-actuator` is on the classpath and a `MeterRegistry` bean is present, the starter auto-registers `HttpClientHealthIndicator`. It reads the `http.client.requests` timer meters and reports per-client error rates computed from probe-to-probe deltas — i.e. the window size is the interval between actuator health probes. No additional observation path is required (the indicator does not implement `HttpClientObserver`, so the `@ConditionalOnMissingBean(HttpClientObserver.class)` override contract still stands).

```yaml
reactive:
  http:
    observability:
      health:
        enabled: true              # master switch (default true)
        error-rate-threshold: 0.5  # ratio (0..1) above which a client reports DOWN
        min-samples: 10            # delta count required before evaluating a client
```

A probe for a registered client is tallied as:

- `INSUFFICIENT_SAMPLES` when `delta-count < min-samples` (noisy-quiet-window suppression).
- `UP` when `errorRate <= error-rate-threshold`.
- `DOWN` when `errorRate > error-rate-threshold` — overall indicator is DOWN if any client is DOWN.

Sample actuator payload:

```json
{
  "status": "DOWN",
  "details": {
    "user-service":     { "samples": 10, "errors": 8, "errorRate": 0.8, "status": "DOWN" },
    "partner-service":  { "samples": 20, "errors": 1, "errorRate": 0.05, "status": "UP" },
    "errorRateThreshold": 0.5,
    "minSamples": 10
  }
}
```

To override, register your own bean named `reactiveHttpClientHealthIndicator`.

### OpenTelemetry tracing (`reactive-http-client-otel`)

The optional `reactive-http-client-otel` companion artifact records each
outbound exchange as an OpenTelemetry span using the standard
[HTTP client semantic conventions](https://opentelemetry.io/docs/specs/semconv/http/http-spans/):

```xml
<dependency>
  <groupId>io.github.huynhngochuyhoang</groupId>
  <artifactId>reactive-http-client-otel</artifactId>
  <version>${reactive-http-client.version}</version>
</dependency>
```

Activation: when `opentelemetry-api` is on the classpath **and** an
`OpenTelemetry` bean is available in the context, the auto-configuration
registers `OpenTelemetryHttpClientObserver` under the property
`reactive.http.observability.otel.enabled` (default `true`). Set to `false`
to disable without removing the dependency.

| Span field | Source |
|---|---|
| Span name (low-cardinality) | `<METHOD> <api.name>` — e.g. `GET getUserById` |
| Span kind | `CLIENT` |
| `http.request.method` | event HTTP verb |
| `http.response.status_code` | event status code (omitted on network failure before response) |
| `url.template` | path template, e.g. `/users/{id}` |
| `error.type` | `ErrorCategory` name; falls back to the exception's simple class name when category is unset |
| `rhttp.client.name` / `rhttp.api.name` | starter-specific |
| `rhttp.attempt.count` | starter-specific (>1 = retried) |
| `rhttp.request.bytes` / `rhttp.response.bytes` | starter-specific (only set when measurable) |

Errors set `StatusCode.ERROR` and call `recordException(...)` so the
exception event lands in the span.

> ⚠️ The OTel observer registers as the only `HttpClientObserver` under
> `@ConditionalOnMissingBean(HttpClientObserver.class)` — pulling in the OTel
> module **disables the Micrometer observer** (and vice versa). To run both,
> register a custom `HttpClientObserver` bean that delegates to both
> implementations.

### Resilience4j metrics

When both `micrometer-core` and `io.github.resilience4j:resilience4j-micrometer` are on the classpath **and** the application registers any of `CircuitBreakerRegistry`, `RetryRegistry`, `BulkheadRegistry` as beans, the starter auto-binds Resilience4j's tagged metrics to the shared `MeterRegistry`:

- `resilience4j.circuitbreaker.*` — state (`open`/`half_open`/`closed`), calls, buffered calls, failure rate.
- `resilience4j.retry.*` — successful / failed attempts, with / without retry.
- `resilience4j.bulkhead.*` — available concurrent calls, max concurrent calls.

The bindings skip automatically if any of the three conditions isn't met. To disable for a specific registry, declare your own `MeterBinder` with the name `reactiveHttpCircuitBreakerMeterBinder` (or the retry / bulkhead equivalent).

### Observability configuration

```yaml
reactive:
  http:
    observability:
      enabled: true
      metric-name: http.client.requests
      include-url-path: true
      log-request-body: false
      log-response-body: false
    network:
      connection-pool:
        metrics-enabled: false   # flip to true to expose reactor.netty.connection.provider.* gauges
```

> Production recommendation: enable body logging only when truly required, and always apply PII masking.

---

## 8) Build & test

```bash
mvn -B -ntp verify
```

---

## 9) Module structure

```text
reactive-http-client/
├── pom.xml
├── CHANGELOG.md
├── reactive-http-client-starter/
│   ├── pom.xml
│   └── src/main/java/io/github/huynhngochuyhoang/httpstarter/
│       ├── annotation/
│       ├── auth/
│       ├── config/
│       ├── core/
│       ├── enable/
│       ├── exception/
│       ├── filter/
│       └── observability/
├── reactive-http-client-test/
│   ├── pom.xml
│   └── src/main/java/io/github/huynhngochuyhoang/httpstarter/test/
│       ├── ErrorCategoryAssertions.java
│       ├── MockReactiveHttpClient.java
│       └── RecordedExchange.java
└── reactive-http-client-otel/
    ├── pom.xml
    └── src/main/java/io/github/huynhngochuyhoang/httpstarter/otel/
        ├── OpenTelemetryHttpClientAutoConfiguration.java
        └── OpenTelemetryHttpClientObserver.java
```

---

## 9.1) Test helpers (`reactive-http-client-test`)

The starter ships a companion artifact for unit-testing
`@ReactiveHttpClient` interfaces without standing up a real HTTP server:

```xml
<dependency>
  <groupId>io.github.huynhngochuyhoang</groupId>
  <artifactId>reactive-http-client-test</artifactId>
  <version>${reactive-http-client.version}</version>
  <scope>test</scope>
</dependency>
```

`MockReactiveHttpClient` builds a real proxy backed by an in-process
`ExchangeFunction`, records every outbound exchange, and serves canned
responses based on registered matchers:

```java
MockReactiveHttpClient<UserService> mock = MockReactiveHttpClient.forClient(UserService.class)
        .baseUrl("http://mock.local")
        .respondTo(HttpMethod.GET, "/users/42",
                ex -> MockReactiveHttpClient.json(200, "{\"id\":42,\"name\":\"alice\"}"))
        .respondTo(HttpMethod.POST, "/users",
                ex -> MockReactiveHttpClient.json(201, "{\"id\":7}"))
        .build();

User user = mock.proxy().getUser(42).block();

RecordedExchange recorded = mock.lastExchange();
assertThat(recorded.method()).isEqualTo(HttpMethod.GET);
assertThat(recorded.uri().getPath()).isEqualTo("/users/42");
```

`ErrorCategoryAssertions` is a small fluent helper for asserting on the
library's error contract:

```java
ErrorCategoryAssertions.assertThatFails(mock.proxy().getUser(99))
        .hasErrorCategory(ErrorCategory.CLIENT_ERROR)
        .hasStatusCode(404);
```

Unmatched requests fall through to a configurable fallback response (HTTP
404 by default) so tests fail loudly instead of hanging on a missing
matcher.

---

## 10) License

Apache License 2.0.
