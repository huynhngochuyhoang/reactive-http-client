# reactive-http-client

[![Maven Central](https://img.shields.io/maven-central/v/io.github.huynhngochuyhoang/reactive-http-client-starter.svg)](https://search.maven.org/artifact/io.github.huynhngochuyhoang/reactive-http-client-starter)
[![CI](https://github.com/huynhngochuyhoang/reactive-http-client/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/huynhngochuyhoang/reactive-http-client/actions/workflows/ci.yml)

A **Spring Boot Starter** that provides a declarative, annotation-driven HTTP client layer for Spring WebFlux (reactive) applications, with built-in **Resilience4j** support for circuit-breaking, retries, bulkheads and timeouts.

---

## Modules

| Module | Description |
|---|---|
| `reactive-http-client-starter` | Spring Boot auto-configuration, annotations and core proxy engine |

---

## Features

- **Declarative client interfaces** – define HTTP calls with simple annotations (`@GET`, `@POST`, `@PUT`, `@DELETE`).
- **Automatic parameter extraction** – `@PathVar`, `@QueryParam`, `@HeaderParam`, `@Body` are resolved automatically via reflection.
- **Resilience4j integration** – circuit-breaker, retry, bulkhead with starter-level timeout support and per-API override.
- **Auto-configuration** – register client beans with a single `@EnableReactiveHttpClients` annotation; no boilerplate `@Bean` methods.
- **IDE autowire support** – `@ReactiveHttpClient` carries `@Component` as a meta-annotation so IntelliJ IDEA's Spring plugin detects injected client beans without false-positive warnings.
- **Observability (Micrometer)** – automatic metrics and trace spans for every HTTP call when `micrometer-core` is on the classpath.
- **Correlation ID propagation** – `X-Correlation-Id` from MDC is forwarded automatically.
- **Error decoding** – 4xx → `HttpClientException`, 5xx → `RemoteServiceException`.

---

## Quick Start: Integrating the Starter into Your Project

### 1. Add the dependency

```xml
<dependency>
    <groupId>io.github.huynhngochuyhoang</groupId>
    <artifactId>reactive-http-client-starter</artifactId>
    <version>1.1.0</version>
</dependency>
```

> Make sure `spring-boot-starter-webflux` is also on the classpath (it usually is in a WebFlux application).

### 2. Enable client scanning

```java
@SpringBootApplication
@EnableReactiveHttpClients(basePackages = "com.myapp.client")
public class MyApp {
    public static void main(String[] args) { SpringApplication.run(MyApp.class, args); }
}
```

### 3. Define a client interface

```java
@ReactiveHttpClient(name = "user-service")
public interface UserApiClient {

    @GET("/users/{id}")
    @ApiName("user.getById")
    @TimeoutMs(8000) // override default read-timeout-ms for this API only
    @LogHttpExchange
    Mono<UserDto> getUser(
        @PathVar("id") String id,
        @QueryParam("expand") String expand   // null → omitted from URL
    );

    @GET("/users")
    Flux<UserDto> listUsers(@QueryParam("role") String role);

    @POST("/users")
    Mono<UserDto> createUser(
        @Body CreateUserRequest body,
        @HeaderParam("X-Tenant") String tenant
    );

    @GET("/users/{id}")
    Mono<UserDto> getUserWithHeaders(
        @PathVar("id") String id,
        @HeaderParam Map<String, String> headers
    );

    @PUT("/users/{id}")
    Mono<UserDto> updateUser(@PathVar("id") String id, @Body UserDto body);

    @DELETE("/users/{id}")
    Mono<Void> deleteUser(@PathVar("id") String id);
}
```

### 4. Configure in `application.yml`

```yaml
reactive:
  http:
    clients:
      user-service:                        # matches @ReactiveHttpClient(name = "user-service")
        base-url: https://api.example.com
        connect-timeout-ms: 2000
        read-timeout-ms: 5000
        codec-max-in-memory-size-mb: 2      # default 2MB; <=0 falls back to default
        compression-enabled: false          # default false
        log-body: false                    # set true to log response status (caution: PII)
        resilience:
          enabled: false                  # default false (opt-in at client level)
          circuit-breaker: user-service    # Resilience4j instance name
          retry: user-service
          bulkhead: user-service
          timeout-ms: 0                    # default 0 (disabled)

# Optional: Resilience4j fine-tuning
resilience4j:
  circuitbreaker:
    instances:
      user-service:
        sliding-window-size: 20
        failure-rate-threshold: 50
        wait-duration-in-open-state: 10s
  retry:
    instances:
      user-service:
        max-attempts: 3
        wait-duration: 200ms
  bulkhead:
    instances:
      user-service:
        max-concurrent-calls: 20
```

### 5. Inject and use

```java
@Service
public class UserService {

    private final UserApiClient userApiClient;

    public UserService(UserApiClient userApiClient) {
        this.userApiClient = userApiClient;
    }

    public Mono<UserDto> getUser(String id) {
        return userApiClient.getUser(id, null);
    }
}
```

---

## Build and Test

### Prerequisites

- JDK 17+
- Maven 3.8+

### Build the entire project (from the root directory)

```bash
mvn clean install -DskipTests
```

### Run the tests

```bash
mvn test
```

This command runs tests for all modules in the repository.

---

## Publish to Maven Central (Current Central Portal Flow)

This project is configured for **Sonatype Central Portal** publishing (modern flow), not the legacy OSSRH/Nexus staging endpoints.

### Central Portal vs Legacy OSSRH (What changed)

| Topic | Legacy OSSRH / Nexus Staging | Central Portal (current) |
|---|---|---|
| Primary endpoint model | `oss.sonatype.org` / `s01.oss.sonatype.org` staging repos | `central.sonatype.com` portal + publisher API |
| Maven publishing approach | `maven-deploy-plugin` + `nexus-staging-maven-plugin` | `org.sonatype.central:central-publishing-maven-plugin` |
| Credentials | OSSRH user/password (or token) | Central Portal user token (`username` + `token`) |
| First-time setup | JIRA ticket workflow (historically common) | Namespace claim in Central Portal UI |

> GitHub Actions docs now explicitly warn older examples are OSSRH legacy and point to Central Portal publishing guidance.

### One-time setup in Sonatype Central Portal

1. Create/sign in to your account in Sonatype Central Portal.
2. Claim/verify your namespace (groupId ownership).  
   For this repo, `io.github.huynhngochuyhoang` should map to your GitHub identity/namespace claim in the portal.
3. Create a **user token** in the portal (token username + token password).
4. Create/export a GPG key pair used for artifact signing.

### GitHub repository secrets required

Add these repository secrets:

- `MAVEN_CENTRAL_USERNAME` (Central Portal token username)
- `MAVEN_CENTRAL_TOKEN` (Central Portal token password)
- `MAVEN_GPG_PRIVATE_KEY` (ASCII-armored private key, including BEGIN/END lines)
- `MAVEN_GPG_PASSPHRASE` (passphrase for the private key)

### Release workflow

Publishing is implemented in:

`.github/workflows/publish-maven-central.yml`

Behavior:

1. Trigger on GitHub Release `published` (or manual `workflow_dispatch`).
2. Run `mvn verify`.
3. Import GPG key via `actions/setup-java`.
4. Run `mvn -Prelease deploy` using the Central Portal publishing plugin.

### Local release dry-run checklist

```bash
# from repository root
mvn clean verify
mvn -Prelease -DskipTests package
```

If those pass, publish by creating a GitHub Release for a non-`-SNAPSHOT` version tag.

### Maven requirements this project satisfies

- Sources JAR and Javadoc JAR are attached.
- Artifacts are signed with GPG in `release` profile.
- Required POM metadata is present: `name`, `description`, `url`, `licenses`, `developers`, `scm`.

### Security note (modern GPG practice)

Do not commit keys/passphrases or place plaintext secrets in `pom.xml`.  
Use CI secrets + ephemeral import (as done by `actions/setup-java`) and keep local secrets in your secure keychain / `gpg-agent`.

---

## Annotation Reference

| Annotation | Target | Description |
|---|---|---|
| `@ReactiveHttpClient(name, baseUrl)` | Interface | Declares a reactive HTTP client interface |
| `@ApiName("...")` | Method | Optional logical name for observability tag `api.name` (default: Java method name) |
| `@TimeoutMs(1500)` | Method | Override timeout (ms) for a single API method (`0` disables timeout for that method) |
| `@LogHttpExchange(logger = ...)` | Method | Logs request/response; allows custom logger implementation |
| `@GET(path)` | Method | HTTP GET |
| `@POST(path)` | Method | HTTP POST |
| `@PUT(path)` | Method | HTTP PUT |
| `@DELETE(path)` | Method | HTTP DELETE |
| `@PathVar(name)` | Parameter | Substituted into the path template |
| `@QueryParam(name)` | Parameter | Appended to the query string (null → omitted) |
| `@HeaderParam(name)` | Parameter | Added as a request header (null → omitted). For `Map` parameters, each map entry is added as a header (`value` optional). |
| `@Body` | Parameter | Serialised as the JSON request body |

### Custom request/response logging hook

`@LogHttpExchange` uses `DefaultHttpExchangeLogger` by default.  
To inspect upstream headers, collect metrics, or forward observability events, provide a custom logger:

```java
public class UpstreamMetricsLogger implements HttpExchangeLogger {
    @Override
    public void log(HttpExchangeLogContext context) {
        // use context.responseHeaders(), context.responseStatus(), context.durationMs(), ...
    }
}

@ReactiveHttpClient(name = "user-service")
public interface UserApiClient {
    @GET("/users/{id}")
    @LogHttpExchange(logger = UpstreamMetricsLogger.class)
    Mono<UserDto> getUser(@PathVar("id") String id, @QueryParam("expand") String expand);
}
```

---

## Error Handling

Every HTTP error is translated by `DefaultErrorDecoder` into a typed exception with a consistent contract.

### Exception types and `ErrorCategory`

| HTTP Status | Exception | `ErrorCategory` | Description |
|---|---|---|---|
| 429 | `HttpClientException` | `RATE_LIMITED` | Too many requests; back off and retry |
| Other 4xx | `HttpClientException` | `CLIENT_ERROR` | Request problem (bad input, not found, unauthorized, …) |
| 5xx | `RemoteServiceException` | `SERVER_ERROR` | Transient or persistent upstream failure |
| Timeout | `java.util.concurrent.TimeoutException` | – | No response within configured timeout |

Both `HttpClientException` and `RemoteServiceException` expose:

| Method | Description |
|---|---|
| `getStatusCode()` | Raw HTTP status code (e.g., `404`, `503`) |
| `getResponseBody()` | Raw response body string (may be empty) |
| `getErrorCategory()` | High-level `ErrorCategory` enum value |
| `getMessage()` | Human-readable summary (`"HTTP client error 404: …"`) |
| `getCause()` | Underlying cause if set (available via constructors that accept `Throwable`) |

### Handling specific categories

```java
// Handle by category (recommended – avoids hard-coding status codes)
userApiClient.getUser(id, null)
    .onErrorResume(HttpClientException.class, ex -> switch (ex.getErrorCategory()) {
        case RATE_LIMITED -> {
            log.warn("Rate limited – backing off");
            yield Mono.error(new RateLimitException(ex));
        }
        case CLIENT_ERROR -> {
            log.warn("Client error {}: {}", ex.getStatusCode(), ex.getResponseBody());
            yield Mono.empty();
        }
        default -> Mono.error(ex);
    })
    .onErrorResume(RemoteServiceException.class, ex -> {
        log.error("Remote service {} error: {}", ex.getStatusCode(), ex.getResponseBody());
        return fallback();
    });
```

### Handling timeouts

```java
userApiClient.getUser(id, null)
    .onErrorResume(java.util.concurrent.TimeoutException.class, ex -> {
        log.warn("Request timed out for user {}", id);
        return Mono.empty();
    });
```

### Handling cancellation

```java
// Reactor propagates CancellationException; subscribe-side cancellation via dispose()
Disposable subscription = userApiClient.getUser(id, null)
    .subscribe(
        user -> process(user),
        err  -> handleError(err)
    );

// Cancel from another thread/signal
subscription.dispose();
```

---

## Advanced Usage Patterns

### Timeout configuration (three levels of precedence)

Timeout resolution priority (highest → lowest):

1. **`@TimeoutMs` on the method** – overrides everything for that single API method.
2. **`read-timeout-ms` per client** (in `application.yml`).
3. **`resilience.timeout-ms`** per client (used when `read-timeout-ms` is `0`).

```java
@ReactiveHttpClient(name = "payment-service")
public interface PaymentApiClient {

    // Uses client-level read-timeout-ms (default 5 000 ms)
    @GET("/payments/{id}")
    Mono<PaymentDto> getPayment(@PathVar("id") String id);

    // Explicit 2 000 ms override for this critical path
    @GET("/payments/status/{id}")
    @TimeoutMs(2000)
    Mono<PaymentStatusDto> getPaymentStatus(@PathVar("id") String id);

    // Disable timeout for this long-running export (0 = no timeout)
    @GET("/payments/export")
    @TimeoutMs(0)
    Flux<PaymentDto> exportPayments();
}
```

### Retry with back-off (Resilience4j)

```java
// application.yml – enable retry only for specific client
reactive:
  http:
    clients:
      payment-service:
        base-url: https://payments.example.com
        read-timeout-ms: 5000
        resilience:
          enabled: true
          retry: payment-service

resilience4j:
  retry:
    instances:
      payment-service:
        max-attempts: 3
        wait-duration: 300ms
        retry-exceptions:
          - java.util.concurrent.TimeoutException
          - io.github.huynhngochuyhoang.httpstarter.exception.RemoteServiceException
```

> **Note:** By default, retries are applied only to `GET` and `HEAD` requests to avoid
> accidentally re-submitting non-idempotent writes. Use service-layer `@Retry` annotations
> for POST/PUT/DELETE.

### Error recovery and fallback

```java
@Service
public class ProductService {

    private final ProductApiClient client;

    public Mono<ProductDto> getProductWithFallback(String id) {
        return client.getProduct(id)
            .onErrorResume(RemoteServiceException.class, ex ->
                // Serve stale data from cache on 5xx
                cache.get(id).defaultIfEmpty(ProductDto.unknown(id)))
            .onErrorResume(HttpClientException.class, ex ->
                ex.getErrorCategory() == ErrorCategory.RATE_LIMITED
                    ? Mono.error(new RateLimitException("Product service rate-limited"))
                    : Mono.error(ex));
    }
}
```

### Propagating context (correlation IDs)

The starter automatically forwards `X-Correlation-Id` from MDC.  
To propagate additional context headers dynamically, use `@HeaderParam Map<String, String>`:

```java
@ReactiveHttpClient(name = "order-service")
public interface OrderApiClient {

    @POST("/orders")
    Mono<OrderDto> createOrder(
        @Body CreateOrderRequest body,
        @HeaderParam Map<String, String> contextHeaders  // forwarded as-is
    );
}

// In your service:
Map<String, String> headers = Map.of(
    "X-Tenant-Id",   tenantId,
    "X-Request-Id",  requestId
);
orderApiClient.createOrder(request, headers);
```

## Project Structure

```text
reactive-http-client/
├── CHANGELOG.md
├── pom.xml                                          # root multi-module POM
├── reactive-http-client-starter/
│   ├── pom.xml
│   └── src/main/java/io/github/huynhngochuyhoang/httpstarter/
│       ├── annotation/       # @ReactiveHttpClient, @GET, @POST, @PUT, @DELETE,
│       │                     #   @PathVar, @QueryParam, @HeaderParam, @Body
│       ├── enable/           # @EnableReactiveHttpClients
│       ├── config/           # AutoConfiguration, Properties, Registrar
│       ├── core/             # FactoryBean, InvocationHandler, MetadataCache,
│       │                     #   ArgumentResolver, UriTemplateExpander, ErrorDecoder
│       ├── observability/    # HttpClientObserver, MicrometerHttpClientObserver
│       └── exception/        # HttpClientException, RemoteServiceException,
│                             #   ErrorCategory
```

---

## Resilience4j Integration Guide

The starter integrates **Resilience4j** in the reactive client layer using Reactor operators.
Each HTTP call can be wrapped by a circuit-breaker, retry policy and/or bulkhead.

### How It Works

Resilience is applied at the **client proxy level** — inside `ReactiveClientInvocationHandler` — using
`transformDeferred` Reactor operators. This means resilience is active for every call made through the proxy,
regardless of which service class calls it.

The pipeline order (innermost to outermost) is:

```
raw WebClient call
  → circuit-breaker (fail-fast if open)
  → retry (for GET/HEAD by default)
  → bulkhead (limit concurrent calls)
  → timeout (method-level `@TimeoutMs`, else client read-timeout-ms, else resilience timeout-ms)
```

### Starter-level Configuration (Recommended)

Configure resilience **per client** in `application.yml`.
This keeps all policy settings in one place and avoids scattered annotations.

```yaml
reactive:
  http:
    clients:
      user-service:
        base-url: https://api.example.com
        connect-timeout-ms: 2000
        read-timeout-ms: 5000
        resilience:
          enabled: true                   # opt-in when you want client-level resilience
          circuit-breaker: user-service   # Resilience4j instance name
          retry: user-service             # null or "default" to use default instance
          bulkhead: user-service
          timeout-ms: 4000               # 0 = disabled

resilience4j:
  circuitbreaker:
    instances:
      user-service:
        sliding-window-type: COUNT_BASED
        sliding-window-size: 20
        failure-rate-threshold: 50        # open when ≥ 50 % of last 20 calls fail
        wait-duration-in-open-state: 10s
        permitted-number-of-calls-in-half-open-state: 5
      default:
        sliding-window-size: 10
        failure-rate-threshold: 50
  retry:
    instances:
      user-service:
        max-attempts: 3
        wait-duration: 200ms             # fixed backoff; use exponential-backoff-multiplier for exp
      default:
        max-attempts: 3
        wait-duration: 500ms
  bulkhead:
    instances:
      user-service:
        max-concurrent-calls: 20
        max-wait-duration: 500ms         # how long to queue before rejecting
      default:
        max-concurrent-calls: 10
  timelimiter:
    instances:
      user-service:
        timeout-duration: 4s
```

> **Note for WebFlux/Reactor:** Add `resilience4j-reactor` to your application dependencies.
> The starter declares it as `optional`; it is not transitively provided.

```xml
<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-spring-boot3</artifactId>
</dependency>
<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-reactor</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-aop</artifactId>
</dependency>
```

### Retry: Idempotent Methods Only

By default the starter applies the retry operator **only for GET and HEAD** requests, because POST/PUT/DELETE may
not be safe to retry without idempotency guarantees.  To enable retry for non-idempotent methods, override
the retry policy at the service layer (see below).

### Service-layer Annotations (Method-level)

Use Resilience4j annotations on the service class when you need:
- **Different policies per method** (e.g., stricter timeout for a critical read vs. a background sync).
- **Fallback logic** — service-layer annotations support `fallbackMethod`.
- **Retry for non-idempotent methods** (opt-in).

```java
@Service
@RequiredArgsConstructor
public class UserGateway {

    private final UserApiClient userApiClient;

    // Starter-level resilience already applied inside the proxy.
    // Add service-level annotations to layer extra policies or provide fallbacks.

    @CircuitBreaker(name = "user-service-read", fallbackMethod = "fallbackGetUser")
    @Retry(name = "user-service-read")
    @TimeLimiter(name = "user-service-read")
    public Mono<UserDto> getUser(String id) {
        return userApiClient.getUser(id, null);
    }

    private Mono<UserDto> fallbackGetUser(String id, Throwable ex) {
        log.warn("Fallback triggered for getUser({}): {}", id, ex.getMessage());
        return Mono.just(new UserDto(id, "fallback-user", ""));
    }

    // POST: opt-in to retry at service layer
    @Retry(name = "user-service-write")
    public Mono<UserDto> createUser(CreateUserRequest req, String tenant) {
        return userApiClient.createUser(req, tenant);
    }
}
```

```yaml
resilience4j:
  circuitbreaker:
    instances:
      user-service-read:
        sliding-window-size: 20
        failure-rate-threshold: 40
        wait-duration-in-open-state: 15s
      user-service-write:
        sliding-window-size: 10
        failure-rate-threshold: 60
  retry:
    instances:
      user-service-read:
        max-attempts: 3
        wait-duration: 200ms
      user-service-write:
        max-attempts: 2           # conservative for writes
        wait-duration: 500ms
```

### When to Use Starter-level vs. Service-layer

| Requirement | Starter-level config | Service-layer annotation |
|---|---|---|
| Apply policy to **all methods of a client** | ✅ Optional | Repetitive |
| **Different policy per method** | ✗ Not supported | ✅ Required |
| **Fallback / default value** | ✗ | ✅ `fallbackMethod` |
| Retry **non-idempotent** methods | ✗ (GET/HEAD only) | ✅ Opt-in |
| **Central config** without code changes | ✅ `application.yml` | ✗ Annotations |
| **Avoid AOP overhead** | ✅ | Adds CGLIB proxy |

**Recommendation:** prefer service-layer annotations (or per-API policy) as default.
Use starter-level (client-wide) resilience only when every method in that client should share the same policy.

---

## Observability: Micrometer Metrics & Tracing

The starter integrates **Micrometer** to automatically record metrics for every HTTP call.
No code changes are needed — just add `spring-boot-starter-actuator` (or `micrometer-core`) to your
application and the metrics are emitted automatically.

### Auto-configuration

When `micrometer-core` is on the classpath and a `MeterRegistry` bean is present, the starter
registers a `MicrometerHttpClientObserver` bean that fires after each request.

```xml
<!-- Brings MeterRegistry, Prometheus endpoint, health, etc. -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
<!-- Optional: Prometheus registry -->
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

### Metrics Produced

| Metric name (default) | Type | Description |
|---|---|---|
| `http.client.requests` | `Timer` | Duration of each HTTP call (also provides `count` and `sum`) |

**Tags on every data point:**

| Tag | Example values | Description |
|---|---|---|
| `client.name` | `user-service` | Logical client name from `@ReactiveHttpClient(name = ...)` |
| `api.name` | `user.getById`, `listUsers` | Method-level logical API name from `@ApiName`, fallback to Java method name |
| `http.method` | `GET`, `POST` | HTTP verb |
| `uri` | `/users/{id}` | Path template (configurable, see below) |
| `http.status_code` | `200`, `404`, `500` | HTTP response status, or `CLIENT_ERROR` / `UNKNOWN` on network failure |
| `outcome` | `SUCCESS`, `CLIENT_ERROR`, `SERVER_ERROR`, `UNKNOWN` | Derived from status code |
| `exception` | `none`, `HttpClientException` | Simple class name of the error, or `none` |
| `error.category` | `none`, `RATE_LIMITED`, `TIMEOUT`, `SERVER_ERROR` | Normalized failure category from `ErrorCategory` (`none` for success) |

### Configuration Reference

```yaml
reactive:
  http:
    observability:
      enabled: true              # master switch (default: true)
      metric-name: http.client.requests  # timer name (default)
      include-url-path: true     # add 'uri' tag with path template (default: true)
                                 # set false to reduce cardinality for high-volume endpoints
      log-request-body: false    # include request body in span events (caution: PII)
      log-response-body: false   # include response body in span events (caution: PII)
```

> **Cardinality warning:** Keep `include-url-path: true` (default) as long as your path templates are
> bounded (e.g. `/users/{id}` is fine). Do **not** use the expanded URL with actual IDs as a tag —
> this is a cardinality explosion. The starter uses the template, not the expanded path.

### Enabling Prometheus / Grafana

1. Add the Prometheus registry:
   ```xml
   <dependency>
       <groupId>io.micrometer</groupId>
       <artifactId>micrometer-registry-prometheus</artifactId>
   </dependency>
   ```

2. Expose the `/actuator/prometheus` endpoint:
   ```yaml
   management:
     endpoints:
       web:
         exposure:
           include: health, prometheus, metrics
     metrics:
       export:
         prometheus:
           enabled: true
   ```

3. Configure Prometheus to scrape your app:
   ```yaml
   # prometheus.yml
   scrape_configs:
     - job_name: 'my-service'
       scrape_interval: 15s
       static_configs:
         - targets: ['localhost:8080']
       metrics_path: /actuator/prometheus
   ```

4. Import the provided Grafana dashboard or create panels using the following PromQL examples:

   ```promql
   # Request rate per client and method
   rate(http_client_requests_seconds_count{client_name="user-service"}[1m])

   # P99 latency
   histogram_quantile(0.99,
     rate(http_client_requests_seconds_bucket{client_name="user-service"}[5m]))

   # Error rate
   sum(rate(http_client_requests_seconds_count{outcome!="SUCCESS",client_name="user-service"}[1m]))
   / sum(rate(http_client_requests_seconds_count{client_name="user-service"}[1m]))
   ```

### Custom Observer

To replace or extend the default Micrometer observer, register your own `HttpClientObserver` bean:

```java
@Bean
public HttpClientObserver myObserver(MeterRegistry registry) {
    return event -> {
        // Standard metrics
        Timer.builder("my.http.calls")
             .tag("client", event.getClientName())
             .tag("method", event.getHttpMethod())
             .register(registry)
             .record(event.getDurationMs(), TimeUnit.MILLISECONDS);

        // Custom logic: forward to distributed tracing
        if (event.isError()) {
            tracer.currentSpan().tag("error", event.getError().getMessage());
        }
    };
}
```

The custom bean overrides the auto-configured `MicrometerHttpClientObserver`
(due to `@ConditionalOnMissingBean(HttpClientObserver.class)`).

### Distributed Tracing with Micrometer Tracing

For distributed tracing (OpenTelemetry / Brave), add the appropriate bridge and configure your
`ObservationRegistry`:

```xml
<!-- OpenTelemetry bridge -->
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-tracing-bridge-otel</artifactId>
</dependency>
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-exporter-otlp</artifactId>
</dependency>
```

```yaml
management:
  tracing:
    sampling:
      probability: 1.0   # sample all requests in dev; reduce in production
  otlp:
    tracing:
      endpoint: http://localhost:4318/v1/traces
```

With Spring Boot 3's auto-configured `ObservationRegistry`, WebClient requests will automatically
carry trace context. Spans will be exported to your backend (Jaeger, Zipkin, OTLP collector, etc.).

---
