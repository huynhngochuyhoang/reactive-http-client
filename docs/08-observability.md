# Observability

The starter ships two observability back-ends: Micrometer (default) and OpenTelemetry (optional companion module). Both implement the `HttpClientObserver` extension point and can run together.

---

## Micrometer metrics

When a `MeterRegistry` bean is present, `MicrometerHttpClientObserver` records four meters per exchange.

### `reactive.http.client.requests` (Timer)

End-to-end duration from first attempt to final completion (after all retries).

| Tag | Values |
|---|---|
| `client.name` | Logical client name from `@ReactiveHttpClient(name)` |
| `api.name` | `@ApiName` value, `@ApiRef` value, or the Java method name |
| `http.method` | `GET`, `POST`, … |
| `http.status_code` | Numeric HTTP status code, or `NONE` on network failure |
| `outcome` | `SUCCESS`, `REDIRECTION`, `CLIENT_ERROR`, `SERVER_ERROR`, `UNKNOWN` |
| `exception` | Simple class name of the thrown exception, or `none` |
| `error.category` | `ErrorCategory` value — see [03-error-handling.md](03-error-handling.md) |
| `uri` | Path template (e.g. `/users/{id}`) when opted in, otherwise `NONE`; enable with `include-url-path: true` |
| `server.address` | Resolved upstream host; opt in with `include-server-address: true` |
| `server.port` | Resolved upstream port; opt in with `include-server-address: true` |

### `reactive.http.client.requests.attempts` (DistributionSummary)

Number of subscription attempts per invocation. `1` = succeeded on first try; `>1` = Resilience4j retry fired. A p95 above `1` signals degradation in a downstream service.

Tags: `client.name`, `api.name`, `http.method`, `uri`.

### `reactive.http.client.requests.request.size` (DistributionSummary)

Serialized request body bytes. Recorded only for cheaply measurable types: `byte[]`, `String`, or `null` (`0`). POJO bodies are not measured to avoid double-serialization cost.

Tags: `client.name`, `api.name`, `http.method`, `uri`.

### `reactive.http.client.requests.response.size` (DistributionSummary)

Response body bytes as advertised by `Content-Length`. Chunked responses and those without the header are skipped.

Tags: `client.name`, `api.name`, `http.method`, `uri`.

### `reactive.http.client.requests.latency` (Timer with SLO histogram) *(opt-in)*

A separate latency Timer configured with `serviceLevelObjectives(...)` buckets, enabling P99/SLO-style analysis in Prometheus/Grafana without tag-cardinality explosion.  Disabled by default — enable via configuration.

| Tag | Values |
|---|---|
| `client.name` | Logical client name from `@ReactiveHttpClient(name)` |
| `api.name` | `@ApiName` value, `@ApiRef` value, or the Java method name |
| `http.method` | `GET`, `POST`, … |
| `uri` | Path template (e.g. `/users/{id}`), or `NONE` |

> The histogram deliberately omits `http.status_code`, `outcome`, `exception`, and `error.category` to keep label-set cardinality low and avoid Prometheus time-series explosion.

---

## Observability configuration

```yaml
reactive:
  http:
    observability:
      enabled: true
      metric-name: reactive.http.client.requests   # custom timer/counter name
      include-url-path: false             # opt-in path template metric tags and span attributes
      include-server-address: false       # opt-in server.address/server.port tags and span attributes
      log-request-body: false             # include body in span events (PII risk)
      log-response-body: false
      histogram:
        enabled: false                    # opt-in latency histogram (SLO buckets)
        slo-boundaries-ms: [50, 100, 200, 500, 1000, 2000, 5000]
```

> **Production recommendation:** keep path, server address, and body dimensions disabled unless you have verified they are bounded. See [18-conflict-cardinality-guardrails.md](18-conflict-cardinality-guardrails.md).

---

## Actuator health indicator

When `spring-boot-starter-actuator` is on the classpath and a `MeterRegistry` bean is present, the starter auto-registers `HttpClientHealthIndicator`. It reads the `reactive.http.client.requests` timer and reports per-client error rates computed from probe-to-probe deltas.

```yaml
reactive:
  http:
    observability:
      health:
        enabled: true              # master switch (default true)
        error-rate-threshold: 0.5  # ratio above which a client reports DOWN
        min-samples: 10            # delta count required before evaluating a client
```

### Status logic

| Condition | Status |
|---|---|
| `delta-count < min-samples` | `INSUFFICIENT_SAMPLES` (no DOWN reported) |
| `errorRate <= error-rate-threshold` | `UP` |
| `errorRate > error-rate-threshold` | `DOWN` — overall indicator is `DOWN` |

Each client detail is keyed only by `client.name`. It contains bounded counters and policy context: `samples`/`sampleCount`, `errors`/`errorCount`, `minSamples`, `errorRateThreshold`, `status`, and `reason`. `errorRate` is present when the probe window has at least one sample. Reasons are `NO_SAMPLES`, `INSUFFICIENT_SAMPLES`, `ERROR_RATE_WITHIN_THRESHOLD`, or `ERROR_RATE_ABOVE_THRESHOLD`. The health indicator does not expose URLs, headers, auth configuration, proxy values, request bodies, or response bodies.

### Sample actuator response

```json
{
  "status": "DOWN",
  "details": {
    "user-service": {
      "samples": 10,
      "errors": 8,
      "sampleCount": 10,
      "errorCount": 8,
      "minSamples": 10,
      "errorRateThreshold": 0.5,
      "errorRate": 0.8,
      "status": "DOWN",
      "reason": "ERROR_RATE_ABOVE_THRESHOLD"
    },
    "partner-service": {
      "samples": 20,
      "errors": 1,
      "sampleCount": 20,
      "errorCount": 1,
      "minSamples": 10,
      "errorRateThreshold": 0.5,
      "errorRate": 0.05,
      "status": "UP",
      "reason": "ERROR_RATE_WITHIN_THRESHOLD"
    },
    "errorRateThreshold": 0.5,
    "minSamples": 10
  }
}
```

Use the health indicator for recent error-rate status, `ReactiveHttpClientDiagnosticsProvider` or
`ReactiveHttpClientDiagnosticsSnapshot` for sanitized configured-client summaries,
and exchange logging for per-call request/response metadata. These surfaces
intentionally expose different data. See [Production Support Bundles](26-support-bundles.md)
for safe incident evidence examples.

To override the indicator, register your own bean named `reactiveHttpClientHealthIndicator`.

---

## Actuator diagnostics endpoint

When `spring-boot-starter-actuator` is on the classpath, a sanitized configured-client diagnostics endpoint is available as an explicit opt-in. It is disabled by default and uses the endpoint id `rhttpclients`.

```yaml
reactive:
  http:
    observability:
      diagnostics-endpoint:
        enabled: true

management:
  endpoints:
    web:
      exposure:
        include: rhttpclients
```

The endpoint returns the same provider-backed JSON-safe fields as `ReactiveHttpClientDiagnosticsSnapshot`: project version, client count, endpoint count, inherited endpoint count, per-client policy summaries, and strict validation flags. Strict flags are true only when the corresponding validation path is active for the resolved client configuration. It does not expose concrete base URLs, auth secrets, header values, proxy credentials, auth-provider bean names, request bodies, or response bodies.

## Connection-pool metrics

Enable Reactor Netty pool gauges per client or globally:

```yaml
reactive:
  http:
    network:
      connection-pool:
        metrics-enabled: true   # global
    clients:
      user-service:
        pool:
          metrics-enabled: true   # per-client
```

Gauges published:

| Gauge | Description |
|---|---|
| `reactor.netty.connection.provider.total.connections` | All connections |
| `reactor.netty.connection.provider.active.connections` | Connections in use |
| `reactor.netty.connection.provider.idle.connections` | Available idle connections |
| `reactor.netty.connection.provider.pending.connections` | Callers waiting for a connection |

All gauges are tagged with `name = reactive-http-client-<clientName>`.

---

## OpenTelemetry tracing (`reactive-http-client-otel`)

The optional OTel companion records each outbound exchange as a span using the [HTTP client semantic conventions](https://opentelemetry.io/docs/specs/semconv/http/http-spans/).

### Add the dependency

```xml
<dependency>
  <groupId>io.github.huynhngochuyhoang</groupId>
  <artifactId>reactive-http-client-otel</artifactId>
  <version>${reactive-http-client.version}</version>
</dependency>
```

### Activation

When `opentelemetry-api` is on the classpath and an `OpenTelemetry` bean is present, the auto-configuration registers:

- `OpenTelemetryHttpClientObserver` for outbound client spans.
- `OpenTelemetryContextWebFilter` in reactive web applications, which extracts inbound OTel context from request headers and stores it in Reactor `Context`.
- A `WebClientCustomizer` that adds `OpenTelemetryContextExchangeFilter` to starter-built clients, injecting configured propagation headers onto outbound requests.

Disable without removing the dependency:

```yaml
reactive:
  http:
    observability:
      otel:
        enabled: false
```

`reactive.http.observability.otel.enabled=false` is the master switch and disables
all OTel beans from this module: span recording, inbound context extraction, and
outbound propagation. Leave the master switch enabled and use the child switches
when you need only one side:

```yaml
reactive:
  http:
    observability:
      otel:
        spans:
          enabled: false        # disables OpenTelemetryHttpClientObserver only
        propagation:
          enabled: false        # disables inbound/outbound propagation only
```

### Span fields

| Field | Source |
|---|---|
| Span name | `<METHOD> <api.name>` — e.g. `GET getUserById` |
| Span kind | `CLIENT` |
| `http.request.method` | HTTP verb |
| `http.response.status_code` | Response status code |
| `server.address` | Resolved upstream host; opt in with `include-server-address: true` |
| `server.port` | Resolved upstream port; opt in with `include-server-address: true` |
| `url.template` | Path template, e.g. `/users/{id}`; opt in with `include-url-path: true` |
| `error.type` | `ErrorCategory` name; falls back to the exception's simple class name |
| `rhttp.client.name` | Logical client name |
| `rhttp.api.name` | `@ApiName` value, `@ApiRef` value, or method name |
| `rhttp.attempt.count` | Total attempts (>1 = retried) |
| `rhttp.request.bytes` | Request body bytes (when measurable) |
| `rhttp.response.bytes` | Response body bytes (from `Content-Length`) |

Errors set `StatusCode.ERROR` and call `recordException(...)` so the exception event appears in the span.

### Trace context and baggage propagation

The OTel companion propagates whatever headers are produced by the application's configured `TextMapPropagator`.
With the standard W3C propagators, this means inbound `traceparent` and `baggage` headers are extracted once by the WebFilter, carried through Reactor `Context`, and injected onto downstream `@ReactiveHttpClient` calls.

```java
@Bean
OpenTelemetry openTelemetry() {
    return OpenTelemetrySdk.builder()
            .setPropagators(ContextPropagators.create(
                    TextMapPropagator.composite(
                            W3CTraceContextPropagator.getInstance(),
                            W3CBaggagePropagator.getInstance())))
            .build();
}
```

The outbound filter falls back to `io.opentelemetry.context.Context.current()` when no Reactor context entry exists, so calls made inside a manually scoped OTel context still propagate. Caller-supplied headers win: if a request already has `traceparent`, `baggage`, or another propagator header, the filter leaves that value untouched.

The outbound propagation filter is added through a Spring `WebClientCustomizer`
before starter per-client built-ins. Per-client `ReactiveHttpClientCustomizer`
filters run later, after correlation ID, auth, and exchange logging have been
wired.

### Running with Micrometer

`MicrometerHttpClientObserver` and `OpenTelemetryHttpClientObserver` are named built-in beans. When both modules are present, the invocation handler records through all available `HttpClientObserver` beans, so one exchange can produce both a Micrometer timer and an OTel `CLIENT` span.

Custom `HttpClientObserver` beans now run alongside the built-ins. To replace a built-in, register a bean with the same name: `micrometerHttpClientObserver` or `openTelemetryHttpClientObserver`. To take complete control over delegation, expose your own observer and exclude or override the built-in bean names.

Custom observers receive raw final outbound request headers when available, but
do not receive response-header maps. See
[Diagnostic Context Contracts](21-diagnostic-contexts.md) for the complete
extension-point capability matrix and redaction responsibilities.

For manual composition outside auto-configuration, use `CompositeHttpClientObserver`:

```java
@Bean
HttpClientObserver compositeObserver(
        MicrometerHttpClientObserver micrometer,
        OpenTelemetryHttpClientObserver otel) {
    return new CompositeHttpClientObserver(List.of(micrometer, otel));
}
```

---

## Resilience4j metrics

See [07-resilience4j.md](07-resilience4j.md) for details on the auto-bound Resilience4j meters.
