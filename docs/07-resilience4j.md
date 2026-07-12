# Resilience4j Integration

The starter provides opt-in Resilience4j support per client: retry, rate limiter, circuit breaker, and bulkhead. Individual methods can override the client-level instance names.

---

## Dependencies

Resilience4j is optional. Add the generation-specific Spring Boot integration
and Reactor adapter to the consuming application; the starter does not pull
them transitively.

Spring Boot 3.5 maintenance applications use the managed `2.2.x` line:

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

Spring Boot 4 applications use the published Resilience4j `2.4.x` Boot 4 line:

```xml
<dependency>
  <groupId>io.github.resilience4j</groupId>
  <artifactId>resilience4j-spring-boot4</artifactId>
</dependency>
<dependency>
  <groupId>io.github.resilience4j</groupId>
  <artifactId>resilience4j-reactor</artifactId>
</dependency>
```

Keep all Resilience4j modules on one BOM-managed version. The V19 Boot 4 build
uses `2.4.0`; the Boot 3.5 maintenance build remains on `2.2.0`.

---

## Enabling resilience for a client

```yaml
reactive:
  http:
    clients:
      user-service:
        resilience:
          enabled: true
          circuit-breaker: user-service   # Resilience4j instance name
          retry: user-service
          rate-limiter: user-service
          bulkhead: user-service
          retry-methods: [GET, HEAD]      # only these verbs are retried
        request-timeout-ms: 5000          # per-request timeout (0 = disabled)
```

When `enabled: false` (the default), no Resilience4j operators are applied regardless of other settings. `request-timeout-ms` is independent of this switch and still applies when configured.

---

## Retry

### Configurable retry methods

Only idempotent-safe methods are retried by default: `GET` and `HEAD`. Override via `retry-methods`:

```yaml
resilience:
  retry-methods: [GET, HEAD, PUT]   # PUT added for idempotent writes
```

Values are normalized (trimmed and uppercased). If you add methods such as `POST` or `PATCH`, the starter preserves compatibility and still applies retry, but it logs a warning when a retry-enabled unsafe method has no explicit `Idempotency-Key` header. The warning includes the client name, Java method, HTTP method, retry instance, and configured `retry-methods` so the risky call is easy to find.

A retry-enabled method is classified as:

| Classification | Meaning | Diagnostic behavior |
|---|---|---|
| `SAFE_METHOD` | `GET`, `HEAD`, `PUT`, `DELETE`, `OPTIONS`, or `TRACE` | No unsafe retry warning |
| `EXPLICIT_IDEMPOTENCY_KEY` | The method invocation has an idempotency key from `@IdempotencyKey`, `@HeaderParam`, a header map, request context, or client default headers | No unsafe retry warning |
| `UNSAFE_RETRY` | Retry is enabled for another HTTP method without an idempotency key | Warn once per client method and keep existing retry behavior |

`@IdempotencyKey` can be used on a parameter to pass the key explicitly, or on a method to generate one key per invocation. `RequestContext.withIdempotencyKey(ctx, value)` can supply the key through Reactor context for one subscribed call. The starter does not provide downstream idempotency storage. The header is only a signal that your downstream service can use to make duplicate attempts safe.

### Strict unsafe-retry validation

The default behavior is warning-only: retry-enabled unsafe methods still run, and
the starter logs when a subscribed call has no `Idempotency-Key` on the wire. For
teams that prefer startup failure, enable strict validation per client:

```yaml
reactive:
  http:
    clients:
      payment-service:
        resilience:
          enabled: true
          retry: payment-service
          retry-methods: [GET, HEAD, POST]
          strict-unsafe-retry-validation: true
```

Strict mode fails proxy construction only when the Retry operator is actually
available, the resolved Retry instance can make more than one attempt, and a
method is both retryable and unsafe. `GET`, `HEAD`, `PUT`, `DELETE`, `OPTIONS`,
and `TRACE` are treated as safe. Unsafe methods are allowed when startup can
prove an idempotency key will be sent, either from a configured default
`Idempotency-Key` header that the method cannot override dynamically or
method-level `@IdempotencyKey` generation.

Runtime-provided idempotency keys from `@HeaderParam`, `@IdempotencyKey`
parameters, header maps, or Reactor context can still be null or absent for a
given call, so strict startup validation does not treat them as proven-safe
contracts. Keep strict validation disabled for those dynamic contracts and rely
on the runtime unsafe-retry warning instead.

Choose the idempotency contract based on who owns the value:

| Contract owner | Recommended mechanism | Strict-mode result |
|---|---|---|
| Starter may generate a fresh key for every subscription | Method-level `@IdempotencyKey` | Startup-provable |
| One immutable deployment value is valid for every call from the client | Client `default-headers.Idempotency-Key`, with no method header parameter or header map that can override it | Startup-provable |
| Caller, Reactor context, or a dynamic header map owns the key | `@IdempotencyKey` parameter, `@HeaderParam`, header map, or `RequestContext` | Runtime-only; keep strict mode disabled and use the warning |

Failure details report the concrete client interface, the declaring method (so
inherited endpoints are identifiable), and `endpointSource` for either the HTTP
method annotation or resolved `@ApiRef` key. The per-method `reason` and
`remediation` fields identify default-header override conflicts separately from
a completely missing idempotency contract.

Incremental rollout pattern:

1. Enable resilience with the intended `retry-methods`, but leave
   `strict-unsafe-retry-validation` disabled.
2. Review startup resilience diagnostics, diagnostics snapshots, and runtime
   unsafe-retry warnings for one client at a time.
3. Convert retryable unsafe methods to idempotent HTTP methods, method-level
   `@IdempotencyKey` generation, or a configured default `Idempotency-Key`
   header that the method cannot override dynamically.
4. Leave strict validation disabled for methods that intentionally rely on
   Reactor context, header parameters, or header maps for idempotency keys; those
   are runtime contracts, not startup-provable contracts.
5. Enable `strict-unsafe-retry-validation: true` for the audited client, then
   repeat the same process for the next client.

### Request body repeatability

Retries re-subscribe to the outbound request. The starter does not buffer large
or streaming request bodies to make a retry possible.

| Body shape | Retry guidance |
|---|---|
| JSON objects, `String`, `byte[]`, form fields, multipart `byte[]`, and `FileAttachment` parts | Treated as repeatable by the starter |
| `Publisher` bodies and `DataBuffer` bodies | Treated as non-repeatable and logged as retry-risky |
| `Resource` bodies and multipart `Resource` parts | Application-owned; make sure the resource can be read again before enabling retry |

When retry is enabled for a method with a non-repeatable or application-owned
body, the starter logs a warning once per client method. Existing retry behavior
is preserved for compatibility.

Retries create multiple subscription attempts inside one logical client call.
Lifecycle hooks expose subscription-attempt boundaries. Observers and exchange
logging each emit one terminal record with the final subscription-attempt count.
The count records subscriptions, not guaranteed HTTP network sends. See
[Lifecycle Hooks](19-lifecycle-hooks.md).

### Resilience4j instance configuration

```yaml
resilience4j:
  retry:
    instances:
      user-service:
        max-attempts: 3
        wait-duration: 200ms
        retry-exceptions:
          - java.net.ConnectException
          - io.github.huynhngochuyhoang.httpstarter.exception.RemoteServiceException
```

---

## Circuit breaker

```yaml
resilience4j:
  circuit-breaker:
    instances:
      user-service:
        sliding-window-type: COUNT_BASED
        sliding-window-size: 10
        failure-rate-threshold: 50
        wait-duration-in-open-state: 10s
        permitted-number-of-calls-in-half-open-state: 3
```

---

## Bulkhead

```yaml
resilience4j:
  bulkhead:
    instances:
      user-service:
        max-concurrent-calls: 25
        max-wait-duration: 0
```

---

## Rate limiter

```yaml
resilience4j:
  ratelimiter:
    instances:
      user-service:
        limit-for-period: 50
        limit-refresh-period: 1s
        timeout-duration: 0
```

---

## Operator ordering

The starter applies the native request timeout first, then resilience operators in this order:

```text
retry -> rate-limiter -> circuit-breaker -> bulkhead
```

The same order appears in DEBUG startup diagnostics as `operatorOrder=retry -> rate-limiter -> circuit-breaker -> bulkhead`. With this ordering, the rate limiter wraps the retried operation at the starter layer instead of being treated as another timeout or bulkhead setting.

When DEBUG logging is enabled for `ReactiveHttpClientFactoryBean`, startup diagnostics also list per-method resilience decisions: resolved HTTP method, retry instance or disabled retry, rate limiter, circuit breaker, bulkhead, retry-safety classification, body-repeatability classification, and operator order.

---

## Per-method overrides

One client often fronts several endpoints with different resilience requirements. The `@Retry`, `@RateLimiter`, `@CircuitBreaker`, and `@Bulkhead` annotations let a single method opt into a different Resilience4j instance:

```java
@ReactiveHttpClient(name = "user-service")
public interface UserApi {

    @GET("/users/{id}")
    @Retry("user-read-retry")           // 5 attempts, 100 ms backoff
    @CircuitBreaker("user-read-cb")     // wide open, 50% failure threshold
    Mono<User> getUser(@PathVar("id") long id);

    @POST("/users")
    @RateLimiter("user-write-rate-limiter")
    @Bulkhead("user-write-bulkhead")    // limit concurrent writes
    Mono<User> createUser(@Body NewUser body);
}
```

Corresponding Resilience4j configuration:

```yaml
resilience4j:
  retry:
    instances:
      user-read-retry:
        max-attempts: 5
        wait-duration: 100ms
  circuit-breaker:
    instances:
      user-read-cb:
        failure-rate-threshold: 50
  bulkhead:
    instances:
      user-write-bulkhead:
        max-concurrent-calls: 10
  ratelimiter:
    instances:
      user-write-rate-limiter:
        limit-for-period: 25
        limit-refresh-period: 1s
        timeout-duration: 0
```

### Important: instance validation at startup

All instance names referenced by `@Retry`, `@RateLimiter`, `@CircuitBreaker`, and `@Bulkhead` are validated when the proxy is constructed. If any instance is missing, the starter fails fast with an `IllegalStateException` that lists every missing instance name. This prevents typos from silently falling back to a default-configured instance.

Per-method annotations are still gated on the client having `resilience.enabled: true`. Methods without an override inherit the client-level config.

---

## Resilience4j metrics

When both `micrometer-core` and `resilience4j-micrometer` are on the classpath, and the application registers any of `CircuitBreakerRegistry`, `RetryRegistry`, `BulkheadRegistry`, or `RateLimiterRegistry` as beans, the starter auto-binds Resilience4j metrics to the shared `MeterRegistry`:

| Metric prefix | Data exposed |
|---|---|
| `resilience4j.circuitbreaker.*` | State (open/half_open/closed), calls, failure rate |
| `resilience4j.retry.*` | Successful / failed attempts, with / without retry |
| `resilience4j.bulkhead.*` | Available concurrent calls, max concurrent calls |
| `resilience4j.ratelimiter.*` | Available permissions, waiting threads |

To disable the binding for a specific registry, declare your own `MeterBinder` bean named `reactiveHttpCircuitBreakerMeterBinder` (or the retry / bulkhead / rate-limiter equivalent).

---

## Deprecated timeout alias

`reactive.http.clients.<name>.resilience.timeout-ms` is retained as a deprecated alias for `request-timeout-ms` for one compatibility cycle. Prefer the client-level property in new config. If both are present, `request-timeout-ms` wins.
