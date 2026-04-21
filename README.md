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

## 1) Support level vs Spring `@HttpExchange` (current starter)

### Overall assessment

**Support level:** the starter is a **higher-level opinionated layer** on top of WebFlux, while Spring `@HttpExchange` is a **lower-level declarative HTTP interface**.  
**Current fit:** if you only need declarative HTTP mapping, `@HttpExchange` is enough; if you need built-in cross-cutting concerns (resilience, auth strategy, observability, network policy), this starter provides broader support out of the box.

| Capability | This starter | Spring `@HttpExchange` |
|---|---|---|
| Declarative interface | `@ReactiveHttpClient` + `@GET/@POST/...` | `@HttpExchange` + method annotations |
| Client-level config model | `reactive.http.clients.<name>` and global `reactive.http.network` | No equivalent built-in config namespace |
| Timeout precedence model | Method-level `@TimeoutMs` over config timeout | No built-in timeout precedence contract |
| Error classification contract | Built-in `HttpClientException` / `RemoteServiceException` + categories | No built-in domain error categorization |
| Resilience4j integration | Built-in opt-in (retry/circuit-breaker/bulkhead) per client | Not built-in; app wires resilience manually |
| Outbound auth strategy | Per-client `AuthProvider` + built-in token refresh provider | Not built-in; app implements filters/interceptors |
| Correlation ID propagation | Built-in support | App-level implementation |
| Micrometer observability contract | Built-in observer with stable tags | Basic instrumentation is app/framework-dependent |

### Practical conclusion

- Choose **Spring `@HttpExchange`** when you want minimal abstraction and full manual control.
- Choose this **starter** when you want a ready-to-use platform for outbound HTTP governance with consistent policies across clients.

---

## 2) Quick Start

### 2.1 Requirements
- Java 17+
- Spring Boot 3.x
- Maven 3.8+

### 2.2 Add the starter dependency

```xml
<dependency>
  <groupId>io.github.huynhngochuyhoang</groupId>
  <artifactId>reactive-http-client-starter</artifactId>
  <version>1.5.0</version>
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
    network:
      connect-timeout-ms: 2000
      read-timeout-ms: 5000
      write-timeout-ms: 5000
      connection-pool:
        max-connections: 200
        pending-acquire-timeout-ms: 5000
    clients:
      user-service:
        base-url: https://api.example.com
        auth-provider: userServiceAuthProvider
        codec-max-in-memory-size-mb: 2
        compression-enabled: false
        log-body: false
        resilience:
          enabled: true
          circuit-breaker: user-service
          retry: user-service
          retry-methods: [GET, HEAD]
          bulkhead: user-service
          timeout-ms: 0
```

`reactive.http.network.read-timeout-ms` configures Reactor Netty **response timeout** (request-level timeout), not channel idle-read timeout.
Method-level `@TimeoutMs` still has highest precedence and can override/disable this timeout per API.

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
| `@GET/@POST/@PUT/@DELETE(path)` | Method | HTTP verb + path |
| `@PathVar(name)` | Parameter | Path variable |
| `@QueryParam(name)` | Parameter | Query parameter |
| `@HeaderParam(name)` | Parameter | Header parameter |
| `@HeaderParam Map<String, String>` | Parameter | Dynamic header map |
| `@Body` | Parameter | Request body |
| `@ApiName("...")` | Method | Logical API name for metrics/tracing |
| `@TimeoutMs(ms)` | Method | Method-level timeout override (`0` disables timeout for that method) |
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

If you need per-business-method policies or fallback methods, add Resilience4j annotations at the service layer.

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

When a `MeterRegistry` is present, the starter records timer metrics (default: `http.client.requests`) with key tags:
- `client.name`
- `api.name`
- `http.method`
- `http.status_code`
- `outcome`
- `exception`
- `error.category`
- `uri` (can be disabled via `include-url-path`)

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
└── reactive-http-client-starter/
    ├── pom.xml
    └── src/main/java/io/github/huynhngochuyhoang/httpstarter/
        ├── annotation/
        ├── config/
        ├── core/
        ├── enable/
        ├── exception/
        ├── filter/
        └── observability/
```

---

## 10) License

Apache License 2.0.
