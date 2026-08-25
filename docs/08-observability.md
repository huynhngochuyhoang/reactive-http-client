# Observability

The starter ships two observability back-ends: Micrometer (default) and OpenTelemetry (optional companion module). Both implement the `HttpClientObserver` extension point and can run together.

Starter `3.x` uses Boot 4 Actuator health contributor packages while preserving
the existing metric names, diagnostics endpoint ID, and configuration keys. See
the [3.x migration guide](28-spring-boot-4-jackson-migration.md) for import and
native-image changes.

---

## Micrometer metrics

When `reactive.http.observability.enabled=true` and a `MeterRegistry` bean is
present, `MicrometerHttpClientObserver` records one sample for each accepted
terminal logical-call event. Its meter availability is:

| Availability | Meters | Contract |
|---|---|---|
| Always recorded | `reactive.http.client.requests`, `reactive.http.client.requests.attempts` | One duration and one subscription-attempt sample per terminal logical call. |
| Conditionally recorded | `reactive.http.client.requests.request.size`, `reactive.http.client.requests.response.size` | Created and recorded only when the corresponding size is known without consuming or independently encoding a body. |
| Opt-in | `reactive.http.client.requests.latency` | Created only when `observability.histogram.enabled=true`; publishes configured SLO histogram buckets. |

Every `reactive.http.client.requests*` meter name follows a customized
`metric-name`. Protocol-aware `reactive.http.client.connection.pool.*` gauges
have an independent switch: global `network.connection-pool.metrics-enabled`
or per-client `pool.metrics-enabled`. They can remain active when
`reactive.http.observability.enabled=false`, and their namespace is fixed.
Resilience4j and Reactor Netty meter families are separate integrations, not
additional samples from `MicrometerHttpClientObserver`.

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
histogram, so no default p95/p99 attempts series exists. The
[average-attempt recipe](#average-subscription-attempts-attempts-per-logical-call)
is a mean, not a percentile. Use Resilience4j retry counters when the question is
whether Retry fired or exhausted; those operator meters are distinct from this
starter-owned logical-call summary.

Tags: `client.name`, `api.name`, `http.method`, `uri`; optional
`server.address` and `server.port` are added only when their explicit cardinality
gate is enabled.

### `reactive.http.client.requests.request.size` (DistributionSummary)

Application request body bytes before transport content coding. Recorded only
for cheaply measurable types: `byte[]` (exact array length), `String`, or `null`
(`0`). String measurement uses the charset declared by the final outbound
`Content-Type` after auth and client-customizer filters; an absent, invalid, or
charset-free value falls back to UTF-8, matching the standard WebClient String
writer behavior. Other `CharSequence`, POJO, publisher, direct `DataBuffer`,
resource, application stream, and multipart bodies remain unknown. Observability never serializes, subscribes, consumes, reopens, or
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

### Response-cache metrics *(separately opt-in)*

Cache meters require all three conditions: global observability is enabled, a
method explicitly selects a cache policy, and
`reactive.http.observability.cache.enabled=true`. The cache-observability switch
does not select caching and does not enable Caffeine's library statistics.

| Meter | Type | Tags | Meaning |
|---|---|---|---|
| `reactive.http.client.cache.lookups` | Counter | `client.name`, `api.name`, `result=hit|miss` | One cache lookup result per caller. Use only this meter for hit ratio. |
| `reactive.http.client.cache.callers` | Counter | `client.name`, `api.name`, `outcome=FRESH_HIT|MISS_LOADER|COALESCED_WAITER|STALE_HIT` | One bounded cache outcome per caller, aligned with lifecycle/log/observer/OTel. |
| `reactive.http.client.cache.coalesced` | Counter | `client.name`, `api.name` | Miss callers that joined an existing single flight. |
| `reactive.http.client.cache.stale` | Counter | `client.name`, `api.name` | Stale values returned while refresh was considered or started. |
| `reactive.http.client.cache.loads` | Counter | `client.name`, `api.name`, `outcome=success|failure|cancellation` | Terminal miss-load work. |
| `reactive.http.client.cache.load.duration` | Timer | same as `loads` | Miss-load work duration; Prometheus exports seconds. |
| `reactive.http.client.cache.refreshes` | Counter | `client.name`, `api.name`, `outcome=success|failure|cancellation` | Terminal hidden-refresh work. |
| `reactive.http.client.cache.refresh.duration` | Timer | same as `refreshes` | Hidden-refresh work duration; Prometheus exports seconds. |
| `reactive.http.client.cache.evictions` | Counter | `client.name`, `cache.policy`, `cause=ttl|size` | Automatic TTL or maximum-size removal. Replacement and shutdown are not eviction causes. |
| `reactive.http.client.cache.entries` | Gauge | `client.name`, `cache.policy` | Current estimated entries. |
| `reactive.http.client.cache.maximum.entries` | Gauge | `client.name`, `cache.policy` | Configured maximum entries. |

All result/outcome/cause values are fixed vocabularies. Selected APIs and
policies register zero-valued series so an idle cache is distinguishable from a
missing integration. Cache keys, values, arguments, headers, URLs, tenant values,
and credentials are never tags. Every meter is owned by the client factory and
removed on factory destruction; recreating the factory with identical tags binds
gauges only to the replacement cache.

Among cache-selected callers, only `MISS_LOADER` contributes to the ordinary
`reactive.http.client.requests` timer. Fresh hits, stale hits, and coalesced
waiters have no downstream dispatch of their own, so the built-in Micrometer
observer excludes them from that timer even when cache outcome fields are
disabled. Custom observers and OpenTelemetry still receive their logical caller
terminal through `HttpClientObserver.recordCacheServed(...)`.

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
      log-request-body: false             # expose request body to custom observer events
      log-response-body: false            # expose decoded success body to custom observer events
      cache:
        enabled: false                    # separately opt-in cache metrics/outcome fields
      histogram:
        enabled: false                    # opt-in latency histogram (SLO buckets)
        slo-boundaries-ms: [50, 100, 200, 500, 1000, 2000, 5000]
```

> **Production recommendation:** keep path and server-address dimensions disabled,
> and keep observer body fields disabled, unless the resulting values and custom
> observer handling are bounded and reviewed. See
> [18-conflict-cardinality-guardrails.md](18-conflict-cardinality-guardrails.md).

---

## Actuator health indicator

When `spring-boot-starter-actuator` is on the classpath and a `MeterRegistry`
bean is present, starter `3.x` auto-registers `Boot4HttpClientHealthIndicator`.
It reads the `reactive.http.client.requests` timer and reports per-client error
rates computed from probe-to-probe deltas. The bean name remains
`reactiveHttpClientHealthIndicator` across the migration.

Health reads only the configured main timer's count and its `error.category`
and `failure.stage` tags. Timer duration sum, maximum, percentiles, and
histogram buckets do not affect health status.
Cache hit ratio, refresh failures, evictions, and entry pressure are operational
signals only; no `reactive.http.client.cache.*` meter changes health status.
Cache-served callers also do not enter the main request timer, so cache traffic
cannot dilute the downstream error-rate denominator.

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
| `delta-count = 0` | `INSUFFICIENT_SAMPLES`, reason `NO_SAMPLES` |
| `0 < delta-count < min-samples` | `INSUFFICIENT_SAMPLES`, reason `INSUFFICIENT_SAMPLES` |
| `errorRate <= error-rate-threshold` | `UP` |
| `errorRate > error-rate-threshold` | `DOWN` — overall indicator is `DOWN` |

Each client detail is keyed only by `client.name`. It contains non-negative
integer `samples`/`sampleCount`, `errors`/`errorCount`, and
`poolAcquireFailureCount`; integer `minSamples`; numeric
`errorRateThreshold`; and string `status` and `reason`. `errorRate` is a
number from `0.0` to `1.0` and is present only when the probe window has at
least one sample. Reasons are `NO_SAMPLES`, `INSUFFICIENT_SAMPLES`,
`ERROR_RATE_WITHIN_THRESHOLD`, or `ERROR_RATE_ABOVE_THRESHOLD`. At most 256
clients with names up to 512 characters are rendered. Registry resets and meter
removal/recreation start a new count baseline instead of producing negative
deltas. The health indicator does not expose URLs, headers, auth configuration,
proxy values, request bodies, or response bodies.

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
      "poolAcquireFailureCount": 2,
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
      "poolAcquireFailureCount": 0,
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

## Dashboard recipes

The queries below use the default metric name and Prometheus' normalized label
names. If `metric-name` is customized, replace the
`reactive_http_client_requests` prefix. Keep the aggregation labels aligned
with the cardinality gates enabled in your deployment.

### Request rate (logical calls per second)

```promql
sum by (client_name, api_name) (
  rate(reactive_http_client_requests_seconds_count[5m])
)
```

The result is logical calls per second over five minutes. Each terminal logical
call contributes once, including zero-attempt resilience rejection and caller
cancellation. Retries, redirects, auth replay, and transport dispatches do not
increment this count independently.

### Error ratio (dimensionless)

```promql
(
  sum by (client_name, api_name) (
    rate(reactive_http_client_requests_seconds_count{error_category!="none"}[5m])
  )
  or
  (
    0 * sum by (client_name, api_name) (
      rate(reactive_http_client_requests_seconds_count[5m])
    )
  )
)
/
sum by (client_name, api_name) (
  rate(reactive_http_client_requests_seconds_count[5m])
)
```

The result is a ratio from `0` to `1`, not a percentage. It mirrors the
health indicator's error classification, but uses a Prometheus range rather than
health probe-to-probe deltas. The `or` branch supplies a zero numerator for
client/API groups that have calls but no error series, so healthy groups remain
visible. Protect dashboards against a zero denominator when there are no calls
in the selected window.

### Zero-attempt resilience rejection

The attempts summary cannot produce this count truthfully. Its exported
`_count` includes every logical call, while `_sum` combines zero, one, and
multiple-attempt values; for example, one zero-attempt call plus one two-attempt
call has the same count and sum as two one-attempt calls. Do not derive a
zero-attempt rate from those series.

Use the starter timer for built-in admission rejections:

```promql
sum by (client_name, api_name, exception) (
  rate(reactive_http_client_requests_seconds_count{error_category="RESILIENCE_ERROR"}[5m])
)
```

The result is rejected logical calls per second, with `exception`
distinguishing CircuitBreaker, RateLimiter, and Bulkhead rejection types.
Resilience4j CircuitBreaker call counters provide operator-specific call history,
and Retry counters show whether Retry fired or exhausted. The auto-bound
RateLimiter and Bulkhead meters expose current-state gauges, not rejection
counters, so do not use them as historical rejection counts.

### p95/p99 logical-call latency (seconds; histogram required)

These queries are valid only when
`reactive.http.observability.histogram.enabled=true`. They return seconds:

```promql
histogram_quantile(
  0.95,
  sum by (le, client_name, api_name) (
    rate(reactive_http_client_requests_latency_seconds_bucket[5m])
  )
)
```

```promql
histogram_quantile(
  0.99,
  sum by (le, client_name, api_name) (
    rate(reactive_http_client_requests_latency_seconds_bucket[5m])
  )
)
```

Configure a highest finite `slo-boundaries-ms` value above the latency range
whose quantiles you need to distinguish. Prometheus also exports a `+Inf`
bucket; when a requested quantile falls into it, `histogram_quantile` returns
the highest finite boundary rather than the actual tail. With the documented
5-second highest default boundary, p95 is pinned at 5 seconds when more than 5%
of calls exceed it, and p99 is pinned when more than 1% exceed it.

The default main timer does not publish percentile or histogram buckets. Its
`_seconds_max` is a time-window maximum and can reset to zero; it is neither
p95/p99 nor a lifetime maximum.

### Average subscription attempts (attempts per logical call)

```promql
sum by (client_name, api_name) (
  rate(reactive_http_client_requests_attempts_sum[5m])
)
/
sum by (client_name, api_name) (
  rate(reactive_http_client_requests_attempts_count[5m])
)
```

The result is a rolling arithmetic mean, not a percentile or retry-event rate.
Values can be below `1` when zero-attempt rejections occur and above `1` when
Retry resubscribes. Redirect and auth-replay dispatches do not increase it.

### Cache hit ratio (dimensionless)

```promql
(
  sum without (result) (
    rate(reactive_http_client_cache_lookups_total{result="hit"}[5m])
  )
  or
  (
    0 * sum without (result) (
      rate(reactive_http_client_cache_lookups_total[5m])
    )
  )
)
/
clamp_min(
  sum without (result) (
    rate(reactive_http_client_cache_lookups_total[5m])
  ),
  0.000000001
)
```

Use only lookup hit and miss series in this ratio. Coalesced waiters and stale
serving have separate counters and must not be added to either side. The
zero-valued branch keeps an idle selected cache or a cache with misses but no
hits visible as `0` instead of dropping the client/API group.

The hit, miss/load, coalescing, refresh, and eviction recipes below preserve
scrape-target labels by aggregating away only their fixed `result`, `outcome`,
or `cause` dimension. Labels such as `job`, `instance`, `pod`, and
deployment-specific target labels therefore remain on every result. Compare
those per-target series for local-cache divergence. Add a separately labeled
fleet aggregation only when a fleet view is intentional.

### Cache miss and load rate (events per second)

Miss callers:

```promql
(
  sum without (result) (
    rate(reactive_http_client_cache_lookups_total{result="miss"}[5m])
  )
  or
  (
    0 * sum without (result) (
      rate(reactive_http_client_cache_lookups_total[5m])
    )
  )
)
```

Terminal miss-load work:

```promql
(
  sum without (outcome) (
    rate(reactive_http_client_cache_loads_total[5m])
  )
  or
  (
    0 * sum without (result) (
      rate(reactive_http_client_cache_lookups_total[5m])
    )
  )
)
```

Both results are events per second. The first counts callers that observed a
miss. The second counts terminal miss-load work across success, failure, and
cancellation. It includes pre-dispatch admission failures and therefore does not
prove that a transport dispatch occurred. Use the ordinary request metrics or
request-dispatch diagnostics when measuring downstream traffic. With single
flight, several misses can correspond to one load.

### Cache coalescing ratio (dimensionless)

```promql
(
  rate(reactive_http_client_cache_coalesced_total[5m])
  or
  (
    0 * sum without (result) (
      rate(reactive_http_client_cache_lookups_total{result="miss"}[5m])
    )
  )
)
/
clamp_min(
  sum without (result) (
    rate(reactive_http_client_cache_lookups_total{result="miss"}[5m])
  ),
  0.000000001
)
```

This is the fraction of miss callers that joined an existing load, not a
downstream request reduction percentage. The zero branch retains miss groups
that have no coalesced waiters.

### Cache refresh failure rate (failures per second)

```promql
(
  sum without (outcome) (
    rate(reactive_http_client_cache_refreshes_total{outcome="failure"}[5m])
  )
  or
  (
    0 * sum without (outcome) (
      rate(reactive_http_client_cache_refreshes_total[5m])
    )
  )
)
```

Correlate this with stale-serving rate and hard-expiry misses. A refresh failure
is hidden from the stale caller and does not itself mark downstream health DOWN.
The zero branch retains every cache-selected API, including policies without
refresh. A zero series does not prove that refresh is configured or active.

### Cache eviction pressure (evictions per second)

Maximum-size evictions:

```promql
(
  sum without (cause) (
    rate(reactive_http_client_cache_evictions_total{cause="size"}[5m])
  )
  or
  (
    0 * reactive_http_client_cache_maximum_entries
  )
)
```

TTL evictions:

```promql
(
  sum without (cause) (
    rate(reactive_http_client_cache_evictions_total{cause="ttl"}[5m])
  )
  or
  (
    0 * reactive_http_client_cache_maximum_entries
  )
)
```

Both results are evictions per second. The zero-valued capacity branch keeps a
selected idle policy visible per target. Sustained size eviction together with
capacity near `1` suggests pressure; TTL eviction reflects expiry activity and
is not itself evidence that maximum size is too small.

### Cache capacity pressure (dimensionless)

```promql
max by (client_name, cache_policy) (
  reactive_http_client_cache_entries
  /
  clamp_min(
    reactive_http_client_cache_maximum_entries, 1
  )
)
```

Prometheus matches the two gauges per scrape target before aggregation, retaining
labels such as `job`, `instance`, or `pod` for the division. The outer
`max by (client_name, cache_policy)` then shows the most saturated instance.
Values near `1` indicate capacity pressure, not an error condition.

### Pool pressure (gauge counts, not utilization percentages)

For HTTP/1.1, inspect queued acquisitions over five minutes:

```promql
max by (name) (
  max_over_time(reactive_http_client_connection_pool_pending_connections[5m])
)
```

For HTTP/2, use pending streams:

```promql
max by (name) (
  max_over_time(reactive_http_client_connection_pool_pending_streams[5m])
)
```

Both results are counts. They are address-free aggregates and are not utilization
percentages: the configured HTTP/1.1 maximum and peer-advertised HTTP/2 stream
limit are not exported in these gauge series. Correlate pending work with
`active_connections` for HTTP/1.1 or `active_streams` plus
`total_connections` for HTTP/2, the configured pool policy, and
`POOL_ACQUIRE` terminal failures.

### Telemetry ownership

| Layer | Scope | Use it for | Do not infer |
|---|---|---|---|
| Starter logical-call Micrometer | One terminal timer/attempt sample per caller subscription; conditional size samples | User-visible duration, outcome, category, final attempt count, known sizes | Wire dispatch count or which resilience operator emitted an event |
| Starter cache Micrometer | One bounded lookup result per cache caller plus hidden load/refresh work and policy gauges | Hit ratio, coalescing, stale serving, refresh reliability, evictions, and capacity | Cache keys/values, downstream health, or a distinct transport dispatch for every caller |
| Resilience4j operator meters | CircuitBreaker call history, Retry events, and RateLimiter/Bulkhead current-state gauges | CircuitBreaker history, Retry execution/exhaustion, and current permission, waiter, or concurrency state | RateLimiter/Bulkhead rejection history; use the starter `RESILIENCE_ERROR` timer instead. Do not infer HTTP status/body ownership or downstream dispatch count. |
| Reactor Netty transport meters | Connection-provider and remote-address transport state | Connector-level connection/pool activity and transport diagnosis | Starter logical-call outcome or bounded client API identity |
| OpenTelemetry companion | One terminal `CLIENT` span per logical call plus inbound/outbound context propagation | Trace correlation and terminal logical-call attributes | Starter-owned child spans for retry, redirect, auth replay, or each dispatch |

---

## OpenTelemetry tracing (`reactive-http-client-otel`)

The optional OTel companion records one terminal `CLIENT` span per logical
client call using the [HTTP client semantic conventions](https://opentelemetry.io/docs/specs/semconv/http/http-spans/).
Retries, redirects, one-time auth replay, and transport dispatches remain inside
that span; they do not create starter-owned child spans.

When cache observability is enabled, caller spans add one bounded
`rhttp.cache.outcome` value: `FRESH_HIT`, `MISS_LOADER`, `COALESCED_WAITER`, or
`STALE_HIT`. Hidden refresh never creates a detached span; its work is represented
by cache refresh meters and a sanitized metadata-only debug log.

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
| `rhttp.request.bytes` | Application request body bytes before transport content coding; `String` uses the final outbound declared charset after auth/client-customizer filters; opaque bodies are absent |
| `rhttp.response.bytes` | Post-transport advertised representation bytes from `Content-Length`; absent for automatically decompressed or chunked responses |
| `rhttp.failure.stage` | Proven `DNS_RESOLUTION`, `PROXY_CONNECT`, `CONNECT`, `TLS_HANDSHAKE`, `POOL_ACQUIRE`, `REQUEST_WRITE`, `RESPONSE_HEADERS`, or `RESPONSE_BODY`; absent when unknown |

The span ends when the terminal `HttpClientObserverEvent` is reported. Its start
is derived from that event's monotonic logical-call duration, so Micrometer,
exchange logging, and OTel describe the same elapsed interval within one
millisecond of timestamp conversion granularity. Admission rejection before the
first request subscription produces one current, finite span with attempt `0`,
no invented HTTP status, and no invented transport failure stage. Retry delay
and resilience admission are included once in the logical duration.

For `Mono<ResponseEntity<Flux<DataBuffer>>>`, the span describes response-envelope
completion. Later subscription, completion, failure, or cancellation of the
caller-owned inner body does not extend or rewrite that span. Direct streaming
`Flux` methods retain their normal full-stream terminal boundary.

`HttpClientObserverEvent` is the only reporting contract with byte counters.
`ReactiveHttpClientLifecycleContext` has neither response headers nor byte
fields. `HttpExchangeLogContext` exposes post-transport response headers but no
byte counter. Lifecycle hooks and exchange loggers must not consume streaming
bodies to infer encoded or decoded sizes.

Errors set `StatusCode.ERROR` and add one structural `exception` event containing
only `exception.type`. The built-in observer intentionally omits exception
messages and stack traces so arbitrary auth-provider or custom-filter payload
text is not exported. It also omits request and response bodies, request and
response header values, and raw request URLs. The structural `error.type` and
`rhttp.failure.stage` attributes retain the diagnostic classification.

### Observer body gates

`reactive.http.observability.log-request-body` and
`reactive.http.observability.log-response-body` are terminal
`HttpClientObserverEvent` payload gates for custom observers. They do not create
OpenTelemetry span events:

- With the default `false`, the corresponding event body field is `null`.
- With `log-request-body: true`, every custom observer in the active composite
  can inspect the resolved request body object.
- With `log-response-body: true`, every custom observer in the active composite
  can inspect the decoded successful response body when one exists. It is not an
  error-body capture mechanism and does not consume streaming bodies.
- Built-in Micrometer and OpenTelemetry observers ignore both body fields. The
  OTel observer never emits raw body span attributes or events.

These settings are global rather than per-observer. Enabling either one transfers
redaction, bounding, retention, and asynchronous ownership responsibility to
each custom observer that reads the field. Keep both disabled unless that custom
observer contract has been reviewed for credentials, PII, large payloads, and
mutable or pooled body objects.

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

`MicrometerHttpClientObserver` and `OpenTelemetryHttpClientObserver` are named built-in beans. When both modules are present, the invocation handler records through all available `HttpClientObserver` beans, so one logical call can produce both a Micrometer timer and one OTel `CLIENT` span.

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
