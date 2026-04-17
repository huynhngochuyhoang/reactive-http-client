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

---

## 1) Current production readiness

### Quick assessment

**Current level:** production-capable for many internal services.  
**Condition:** add app-level operational and security hardening; it is not fully enterprise-ready out of the box.

| Area | Status | Notes |
|---|---|---|
| Core client proxy + annotation model | ✅ Good | Clear proxy/metadata-cache architecture with test coverage |
| Timeout / error contract | ✅ Good | Error categorization and timeout precedence are defined |
| Resilience (CB/Retry/Bulkhead) | ✅ Good (opt-in) | Integrated; retry defaults to GET/HEAD |
| Metrics/tracing hooks | ✅ Good (opt-in) | Micrometer observer and stable tags are available |
| Enterprise security/auth | ⚠️ Partial gap | No built-in auth/token refresh/mTLS/proxy policy layer |
| Operational hardening (governance) | ⚠️ Partial gap | More production guardrails/runbook guidance needed |
| Integration/contract testing sample | ⚠️ Gap | Starter currently focuses on unit-level test coverage |

### Remaining gaps

#### Must be handled at the application level for production

1. **Outbound auth standardization**: shared OAuth2/JWT/API key strategy instead of per-service custom logic
2. **Network hardening policy**: clear proxy, SSL/mTLS, and connection pool tuning rules by environment
3. **PII-safe logging policy**: redaction/masking strategy when body logging is enabled
4. **Production runbook**: clear response playbook for rising errors/timeouts/circuit-open events

#### Recommended starter roadmap additions

5. **Integration sample**: reference app + mock upstream for validating retry/timeout/metrics behavior

> Note: these gaps do not block production usage, but they matter for large-scale and secure operations.

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
  <version>1.1.0</version>
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
    clients:
      user-service:
        base-url: https://api.example.com
        connect-timeout-ms: 2000
        read-timeout-ms: 5000
        codec-max-in-memory-size-mb: 2
        compression-enabled: false
        log-body: false
        resilience:
          enabled: true
          circuit-breaker: user-service
          retry: user-service
          bulkhead: user-service
          timeout-ms: 0
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
5. Apply resilience (if enabled): circuit-breaker -> retry -> bulkhead.
6. Apply timeout (priority: `@TimeoutMs` > `read-timeout-ms` > `resilience.timeout-ms`).
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
| Timeout | `TimeoutException` | `—` (normalized as `TIMEOUT` in observability) |

Both main exception types expose:
- `getStatusCode()`
- `getResponseBody()`
- `getErrorCategory()`

---

## 6) Resilience4j integration

The starter supports client-level Resilience4j configuration.
By default, retry is applied only to **GET/HEAD** (idempotent-safe default).

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

## 8) Production safety checklist

- [ ] Every client has explicit `base-url`, timeout, and resilience settings aligned with SLA.
- [ ] Retry policy is valid (no unsafe retry for non-idempotent writes).
- [ ] Dashboard + alerts are in place (latency, error rate, circuit-open, timeout).
- [ ] Correlation ID is propagated end-to-end.
- [ ] Outbound auth is standardized (including token rotation/refresh strategy).
- [ ] No PII/secret leakage in logs.
- [ ] Integration tests cover timeout, 4xx/5xx, retry, and fallback scenarios.
- [ ] Operational runbook exists for upstream degradation incidents.

---

## 9) Build & test

```bash
mvn -B -ntp verify
```

---

## 10) Module structure

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

## 11) License

Apache License 2.0.
