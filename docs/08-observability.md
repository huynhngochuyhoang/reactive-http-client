# Observability

The starter ships two observability back-ends: Micrometer (default) and OpenTelemetry (optional companion module). Both implement the `HttpClientObserver` extension point and can run together.

Starter `3.x` uses Boot 4 Actuator health contributor packages while preserving
the existing metric names, diagnostics endpoint ID, and configuration keys. See
the [3.x migration guide](28-spring-boot-4-jackson-migration.md) for import and
native-image changes.

---

## Micrometer metrics

When a `MeterRegistry` bean is present, `MicrometerHttpClientObserver` always
records the main logical-call timer and attempts summary for each accepted
terminal event. Request/response size summaries are created only when the
corresponding size is known. The separate latency histogram timer is opt-in.

### `reactive.http.client.requests` (Timer)

Logical-call elapsed duration from subscription to terminal completion, measured
with a monotonic clock. It includes resilience admission and retry delays, auth
and request preparation, transport dispatch, and starter-owned response
consumption. A rejection or cancellation before the first attempt therefore has
a finite duration and an attempt count of `0`.

The public observer event carries milliseconds. A Prometheus registry exports
timer values in seconds as `reactive_http_client_requests_seconds_count`,
`reactive_http_client_requests_seconds_sum`, and
`reactive_http_client_requests_seconds_max`. Count and sum are cumulative. Max is
a Micrometer time-window maximum, not a lifetime maximum: it resets to `0` after
no observations remain in the configured expiry window. With the Prometheus
registry default, that window is the registry step multiplied by the distribution
buffer length (three slots by default); registry or meter-filter configuration can
change it.

| Tag | Values |
|---|---|
| `client.name` | Logical client name from `@ReactiveHttpClient(name)` |
| `api.name` | `@ApiName` value, `@ApiRef` value, or the Java method name |
| `http.method` | `GET`, `POST`, … |
| `http.status_code` | Numeric HTTP status code, or `NONE` when no response status is known |
| `outcome` | `SUCCESS`, `REDIRECTION`, `CLIENT_ERROR`, `SERVER_ERROR`, `UNKNOWN` |
| `exception` | Simple class name of the thrown exception, or `none` |
| `error.category` | `ErrorCategory` value — see [03-error-handling.md](03-error-handling.md) |
| `failure.stage` | Proven `DNS_RESOLUTION`, `PROXY_CONNECT`, `CONNECT`, `TLS_HANDSHAKE`, `POOL_ACQUIRE`, `REQUEST_WRITE`, `RESPONSE_HEADERS`, or `RESPONSE_BODY`; `none` when unknown |
| `uri` | Path template (e.g. `/users/{id}`) when opted in, otherwise `NONE`; enable with `include-url-path: true` |
| `server.address` | Resolved upstream host; opt in with `include-server-address: true` |
| `server.port` | Resolved upstream port; opt in with `include-server-address: true` |

### `reactive.http.client.requests.attempts` (DistributionSummary)

Number of subscriptions to the retryable request publisher within one logical
call:

- `0` means resilience rejected or admission was cancelled before the initial
  request subscription.
- `1` means one subscription attempt, regardless of success, HTTP error,
  transport error, or cancellation.
- Values greater than `1` mean Resilience4j retry resubscribed.

This is not a downstream request count. Redirects and one-time auth replay can
create additional wire requests inside one subscription attempt.

The default summary exports count, sum, and max statistics supported by the
registry. It does not enable `publishPercentiles(0.95, 0.99)` or a percentile
histogram, so no default p95/p99 attempts series exists. For a rolling mean
attempt count in Prometheus, use:

```promql
sum by (client_name, api_name) (
  rate(reactive_http_client_requests_attempts_sum[5m])
)
/
sum by (client_name, api_name) (
  rate(reactive_http_client_requests_attempts_count[5m])
)
```

This query is a mean, not a percentile. Use Resilience4j retry counters when the
question is whether Retry fired or exhausted; those operator meters are distinct
from this starter-owned logical-call summary.

Tags: `client.name`, `api.name`, `http.method`, `uri`; optional
`server.address` and `server.port` are added only when their explicit cardinality
gate is enabled.

### `reactive.http.client.requests.request.size` (DistributionSummary)

Application request body bytes before transport content coding. Recorded only
for cheaply measurable types: `byte[]` (exact array length), `String`, or `null`
(`0`). String measurement uses the charset declared by the effective outbound
`Content-Type`; an absent, invalid, or charset-free value falls back to UTF-8,
matching the standard String and auth raw-body path. Other `CharSequence`, POJO,
publisher, direct `DataBuffer`, resource, application stream, and multipart bodies
remain unknown. Observability never serializes, subscribes, consumes, reopens, or
aggregates a body solely to measure it. The starter `compression-enabled` option
does not compress request bodies.

Tags: `client.name`, `api.name`, `http.method`, `uri`; optional server-address
tags follow the same explicit gate.

### `reactive.http.client.requests.response.size` (DistributionSummary)

Response representation bytes advertised by `Content-Length` after transport
processing. Chunked responses and those without the header are skipped. Reactor
Netty removes the compressed representation length during automatic gzip
decompression, so those responses report unknown and are skipped rather than
being mislabeled as decoded bytes. Streaming bodies are never consumed to
calculate this metric. Here, **encoded** and **decoded** describe representation
boundaries, **advertised** is the surviving header value, **consumed** is actual
body demand, and **unknown** means no trustworthy advertised count exists. This
metric records only advertised bytes; it is not a decoded or consumed byte counter.
An advertised `0` is recorded as zero. A surviving valid length remains the
advertised value for HEAD, drained bodiless responses, `ResponseEntity`, and a
body that later fails or is cancelled; malformed framing with no trustworthy
surviving length remains unknown.

Tags: `client.name`, `api.name`, `http.method`, `uri`; optional server-address
tags follow the same explicit gate.

### `reactive.http.client.requests.latency` (Timer with SLO histogram) *(opt-in)*

A separate latency timer configured with `serviceLevelObjectives(...)` buckets.
Prometheus exports aggregable `_bucket`, `_count`, and `_sum` series suitable for
SLO analysis and `histogram_quantile`. It is disabled by default.

| Tag | Values |
|---|---|
| `client.name` | Logical client name from `@ReactiveHttpClient(name)` |
| `api.name` | `@ApiName` value, `@ApiRef` value, or the Java method name |
| `http.method` | `GET`, `POST`, … |
| `uri` | Path template (e.g. `/users/{id}`), or `NONE` |
| `server.address` | Resolved upstream host when explicitly enabled |
| `server.port` | Resolved upstream port when explicitly enabled |

> The histogram deliberately omits `http.status_code`, `outcome`, `exception`,
> `error.category`, and `failure.stage` to keep its label set bounded. Enabling
> server-address labels remains a separate explicit cardinality decision.

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

When `spring-boot-starter-actuator` is on the classpath and a `MeterRegistry`
bean is present, starter `3.x` auto-registers `Boot4HttpClientHealthIndicator`.
It reads the `reactive.http.client.requests` timer and reports per-client error
rates computed from probe-to-probe deltas. The bean name remains
`reactiveHttpClientHealthIndicator` across the migration.

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
        include: health,rhttpclients
  endpoint:
    health:
      show-details: when-authorized
```

The endpoint returns the same provider-backed JSON-safe fields as `ReactiveHttpClientDiagnosticsSnapshot`: project version, client count, endpoint count, inherited endpoint count, per-client policy summaries, and strict validation flags. Strict flags are true only when the corresponding validation path is active for the resolved client configuration. It does not expose concrete base URLs, auth secrets, header values, proxy credentials, auth-provider bean names, request bodies, or response bodies.

## Connection-pool metrics

Enable address-free starter aggregate pool gauges per client or globally:

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

Under the `reactive.http.client.connection.pool` namespace, common gauges are
`total.connections` and `idle.connections`. HTTP/1.1 adds `active.connections` and
`pending.connections`; HTTP/2 adds `active.streams` and `pending.streams` because
the public pool view does not prove how streams are distributed across
physical connections. The peer-advertised H2 stream limit is runtime state and
remains unknown in configured-client diagnostics.

These starter-owned names do not collide with Reactor Netty's built-in
`reactor.netty.connection.provider.*` families, whose tag set includes provider and
remote-address dimensions. All starter pool gauges carry only the bounded
`name = reactive-http-client-<clientName>-<interface>` tag. They do not export a
remote-address tag. See [Connection pool](05-connection-pool.md#connection-pool-metrics)
for the complete protocol-aware interpretation.

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
- A Boot-generation-specific `WebClientCustomizer` that adds `OpenTelemetryContextExchangeFilter` to starter-built clients, injecting configured propagation headers onto outbound requests.

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
| `rhttp.attempt.count` | Logical subscription attempts (`0` before request subscription; `>1` means Retry resubscribed). This is not a downstream dispatch count. |
| `rhttp.request.bytes` | Application request body bytes before transport content coding; `String` uses the effective declared charset, and opaque bodies are absent |
| `rhttp.response.bytes` | Post-transport advertised representation bytes from `Content-Length`; absent for automatically decompressed or chunked responses |
| `rhttp.failure.stage` | Proven `DNS_RESOLUTION`, `PROXY_CONNECT`, `CONNECT`, `TLS_HANDSHAKE`, `POOL_ACQUIRE`, `REQUEST_WRITE`, `RESPONSE_HEADERS`, or `RESPONSE_BODY`; absent when unknown |

`HttpClientObserverEvent` is the only reporting contract with byte counters.
`ReactiveHttpClientLifecycleContext` has neither response headers nor byte
fields. `HttpExchangeLogContext` exposes post-transport response headers but no
byte counter. Lifecycle hooks and exchange loggers must not consume streaming
bodies to infer encoded or decoded sizes.

Errors set `StatusCode.ERROR` and add one structural `exception` event containing
only `exception.type`. The built-in observer intentionally omits exception
messages and stack traces so arbitrary auth-provider or custom-filter payload
text is not exported. The structural `error.type` and `rhttp.failure.stage`
attributes retain the diagnostic classification.

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

The outbound propagation filter is added through Spring Boot's `WebClientCustomizer`
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
