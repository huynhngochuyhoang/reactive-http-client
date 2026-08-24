# Diagnostic Context Contracts

The starter exposes several diagnostic extension points at different lifecycle
stages. They intentionally do not share one oversized context type. Use this
matrix when selecting an extension point or deciding whether a value is
available.

## Capability matrix

| Capability | `HttpExchangeLogContext` | `HttpClientObserverEvent` | `ReactiveHttpClientLifecycleContext` | `ErrorResponseContext` |
|---|---|---|---|---|
| Final request URL | `requestUrl()` after `WebClient` filters, when available | `getRequestUrl()` after `WebClient` filters, when available | `requestUrl()` when available; attempt callbacks may run before dispatch | No final-request snapshot; `requestUrl()` is read from the transport response when available |
| Final request headers | Raw `requestHeaders()` after `WebClient` filters, when available; otherwise prepared resolved headers | Raw `getRequestHeaders()` after `WebClient` filters, when available; otherwise empty | No. `headers()` contains prepared resolved headers before later `WebClient` filter mutation | No |
| Response status | `responseStatus()` when available | `getStatusCode()` when available | `statusCode()` when available | `statusCode()` for the decoded 4xx or 5xx response |
| Response headers | Raw `responseHeaders()` | No | No | Read-only `responseHeaders()` |
| Error body | No dedicated error-body field. `responseBody()` is the decoded success body and is `null` for error responses | No dedicated error-body field. `getResponseBody()` is the optionally retained decoded success body and is `null` for error responses | No | Bounded `responseBody()` plus `responseBodyTruncated()` and `retainedResponseBodyBytes()` |
| Duration | `durationMs()` for the logical call | `getDurationMs()` for the logical call | No | No |
| Subscription-attempt count | Terminal `subscriptionAttemptCount()` for the logical call | Terminal `getAttemptCount()` for the logical call | `attemptNumber()` identifies the current callback attempt; terminal callbacks receive the final attempt number | No |
| Cache outcome | Bounded `cacheOutcome()` when separately enabled | Bounded `getCacheOutcome()` when separately enabled | Bounded terminal `cacheOutcome()` when separately enabled | No |
| Proven failure stage | `failureStage()` | `getFailureStage()` | `failureStage()` | No |

## Call and dispatch boundaries

| Boundary | Meaning | Effect on `attemptCount` |
|---|---|---:|
| Java method invocation | Creates one cold `Mono` or `Flux`; no request is sent yet | None |
| Caller subscription | Starts one independent logical call with its own mutable reporting state | Remains `0` until the request publisher is subscribed |
| Initial request subscription | Starts the first request attempt; a resilience rejection can occur before this boundary | Becomes `1` |
| Resilience4j retry subscription | Re-subscribes to the request publisher inside the same logical call | Increments by `1` |
| Outbound dispatch | Passes the request through the final observation filter toward the connector | No direct effect |

Declarative argument validation can fail synchronously during Java method
invocation, before a cold publisher and logical call exist. That failure creates
no lifecycle, observer, exchange-log, metric, health, or OTel terminal record.
Failures after subscription but before dispatch, including URI construction and
auth-body serialization, create one terminal record with attempt `1`, no final
request URL, no response status or headers, and no inferred failure stage.

`attemptCount`, `subscriptionAttemptCount`, and lifecycle `attemptNumber` retain
their established subscription-attempt meaning. They are not wire-request
counters. A transport-followed redirect or the auth filter's one-time `401`
refresh can issue another HTTP request while the subscription-attempt count stays
`1`. The starter does not expose a dispatch count because Reactor Netty owns
redirect dispatches below the `WebClient` filter boundary, so one consistent
count cannot be proven across all supported paths.

Each caller subscription receives separate attempt, idempotency-key, duration,
logical-call deadline, request-observation, response, and terminal-error state, including concurrent
subscriptions to the same cold publisher. Resilience retries clear prior-attempt
evidence. The hidden auth replay also clears request and response evidence before
new auth is resolved, then records the replay only if it reaches the final
observation filter. Transport-owned redirects retain the original declarative
request snapshot and the final response status.

For an automatically followed redirect, `pathTemplate`/`uriPath` remains the
configured declarative endpoint and `requestUrl` remains the final request seen
at the starter's `WebClient` observation-filter boundary. Reactor Netty performs
later redirect dispatches below that boundary, so lifecycle, observer, and
exchange-log records do not claim that the redirect target is a configured
client URL. Use transport/server evidence when the exact redirected wire target
is required.

Subscription-attempt values count reactive subscriptions, not guaranteed HTTP
network sends. After retry exhaustion, terminal contexts retain the final emitted
throwable and final subscription-attempt count; earlier attempt failures are not
reported as the logical call terminal cause. Request-body serialization can fail after an attempt starts but
before dispatch. Terminal cancellation is represented by a `CancellationException`
and `ErrorCategory.CANCELLED`; it is not a timeout phase. `DNS_RESOLUTION`,
`PROXY_CONNECT`, `CONNECT`, `TLS_HANDSHAKE`, `POOL_ACQUIRE`, `REQUEST_WRITE`,
`RESPONSE_HEADERS`, and `RESPONSE_BODY` are reported only from
concrete transport evidence for the final outbound request of the current attempt.
Per-attempt evidence is reset on resilience retry and hidden 401 auth refresh;
auth-provider failures are a hard boundary, and arbitrary custom-filter wrappers
without final-request dispatch evidence remain stage-unknown. Direct concrete DNS,
proxy, connect, TLS, pool-acquire, and write exceptions do not require URL evidence.

An enabled `logical-call-timeout-ms` wraps the full caller subscription and does
not reset for resilience retries, redirects, or hidden auth refresh. Its
`LogicalCallTimeoutException` reports `RESPONSE_BODY` only when the current final
attempt observed response status. Expiry before status, including before dispatch,
in the pool queue, or between attempts, remains stage-unknown. Lifecycle hooks, observer events, and
exchange-log contexts receive the same terminal exception, status, attempt count,
and derived failure stage.

For `Mono<ResponseEntity<Flux<DataBuffer>>>`, terminal lifecycle, observer, and exchange-log records describe response-envelope completion. They do not indicate that the inner streamed body was subscribed or fully consumed. A later inner-body timeout or cancellation does not rewrite the already reported successful envelope terminal record. Direct `Flux<DataBuffer>` methods report terminal state when the stream itself completes, errors, or is cancelled.

When cache observability is separately enabled, each cache-selected caller has
one bounded terminal outcome: `FRESH_HIT`, `MISS_LOADER`,
`COALESCED_WAITER`, or `STALE_HIT`. Hits, waiters, and stale returns report
attempt `0`, no dispatch URL, status, server, failure stage, or wire sizes. A
miss loader retains its final HTTP evidence. Hidden refresh is not a caller and
does not create observer, lifecycle, exchange-log, or OTel terminal records; its
bounded work result is emitted through cache meters and a sanitized debug log.
If the original miss leader detaches while a coalesced waiter remains, the
waiter stays transport-empty rather than inheriting the shared load's retries,
status, URL, or headers. The load remains visible through bounded cache work
meters.

## Header handling

Custom `HttpExchangeLogger` implementations receive raw final outbound request
headers and raw response headers. `DefaultHttpExchangeLogger` applies its
sensitive-header deny-list only when formatting built-in log output. Its inbound
header snapshot is already filtered and redacted by `InboundHeadersWebFilter`.

Custom `HttpClientObserver` implementations receive raw final outbound request
headers. Observer events do not expose response headers. The built-in Micrometer
and OpenTelemetry observers do not log request-header values. Micrometer tags
only the exception class name; OpenTelemetry emits only a structural
exception-type event, without exception message or stack trace.

The `log-request-body` and `log-response-body` settings gate body fields on the
single terminal `HttpClientObserverEvent`; they do not add OpenTelemetry span
events. When enabled, every custom observer in the active composite can inspect
the resolved request body or decoded successful response body. The built-in
Micrometer and OTel observers ignore those fields. Response-body gating does not
capture bounded error bodies and does not consume a streaming body. Custom
observers own redaction, bounds, retention, and safe handling of mutable or
pooled objects.

Lifecycle hooks receive prepared resolved request headers before later
`WebClient` filters run. Error mappers receive read-only raw response headers.
Custom extension points that persist header values must apply their own
redaction policy.

## Health, diagnostics, and exchange logs

The Actuator health indicator is a status signal derived from Micrometer request
timer count deltas. It reports bounded per-client sample counts, error counts,
error rates, thresholds, and reasons for the latest health-probe window. Duration
sums, time-window maxima, percentiles, and histogram buckets are not health
inputs. It does not describe configured endpoints or per-call payload metadata.
Pool-acquire failures add a bounded `poolAcquireFailureCount` for the same probe window.
Cache hit ratio, refresh failure, eviction pressure, and entry count do not
change this health result.

Use exchange logging for per-call request/response metadata, and use the diagnostics provider when support output needs configured-client summaries.

## Runtime diagnostics provider

Applications can inject `ReactiveHttpClientDiagnosticsProvider` to inspect sanitized registered-client summaries at runtime. The provider reports the client name, client interface, base URL source, effective pool source/maximum/pending-acquire-timeout/metrics policy, configured pool protocol/capacity basis with an unknown peer stream limit, bounded cache phase/policy/TTL/refresh/single-flight/capacity/entry/eviction state, per-attempt response-timeout summary, logical-call budget, resilience summary, auth mode, redirect-following flag, endpoint count, and inherited endpoint count. It does not expose base URL values, cache entries or keys, header values, proxy credentials, auth-provider bean names, request bodies, or response bodies.

Use `ReactiveHttpClientDiagnosticsSnapshot` when a support bundle, startup log,
or local custom endpoint needs deterministic Markdown or JSON output from those
same sanitized summaries:

```java
String markdown = ReactiveHttpClientDiagnosticsSnapshot.toMarkdown(diagnostics);
String json = ReactiveHttpClientDiagnosticsSnapshot.toJson(diagnostics);
```

Provider-backed snapshots render schema version, project version, total client count, total endpoint
count, total inherited endpoint count, strict validation flags, and one row/object
per client. Summary-only collection overloads render strict validation flags and effective pool policy as
unknown because `ClientSummary` does not carry those provider-only values. The
helper sorts clients by name and interface for stable output. The helper is explicit: calling
it does not register an Actuator endpoint, controller, log line, or file writer.

### Diagnostics schema v1

JSON, map, and Markdown snapshots declare schema version `1`. Within the `3.x`
line, schema changes are additive: existing fields keep their names, types, and
meaning; a field is not removed, retyped, or reinterpreted in a minor release.
Consumers must ignore fields they do not recognize. The sanitized
[source-controlled current v1 fixture](fixtures/rhttpclients-schema-v1.json) is the
regression-review baseline. The immutable [published `3.2.0` v1
fixture](fixtures/rhttpclients-schema-v1-3.2.0.json) additionally prevents a `3.x`
minor from removing or retyping an already published field while allowing reviewed
additive fields.

Value semantics are explicit:

| Value | Meaning |
|---|---|
| `true` | The boolean policy or strict-validation path is active and was resolved by the provider-backed snapshot. |
| `false` | The provider-backed snapshot proved that policy or strict-validation path inactive. |
| `null` / `unknown` | Provider-only data is unavailable or cannot be proven without creating a lazy runtime component; it is not equivalent to `false`. |
| `disabled` | The timeout or resilience operator is not applied by effective client/method policy. A disabled timeout is `timeoutSource=disabled` and `timeoutMs=0`. |
| `unavailable` | Resilience is enabled for the effective contract, but the optional operator adapter/registry is unavailable at runtime. |
| `missing` | No annotation or property supplies the required base URL source; concrete URL values are never exported. |

The v1 root field set is `schemaVersion`, `projectVersion`, `clientCount`,
`endpointCount`, `inheritedEndpointCount`, and `clients`. Client entries retain
the reviewed field names and JSON value kinds in the source-controlled fixture.
Provider and collection map output, rendered JSON, Markdown, and the Actuator
endpoint use the same logical fields. Provider-backed entries include the configured
`compressionEnabled` policy and `codecMaxInMemorySizeMb` decoded unary aggregate
limit; collection overloads render those provider-only values as `null` in map/JSON
and `unknown` in Markdown. Collection-only pool and strict-validation facts retain
the same unknown semantics. Cache phase, policy count, minimum TTL and refresh
threshold, single-flight state, aggregate maximum size, entry count, eviction
count, and cache-metrics enablement follow the same provider/collection unknown
rules. No snapshot path enumerates cache entries. The fixture and JVM
and native checks reject removal, rename, type drift, or accidental secret-bearing
fields.

Request-scoped transport facts introduced in V22, including the actual negotiated
protocol and peer-advertised stream limit, content encoding, failure stage, error category, request/response headers, and
payload sizes, are intentionally not copied into this configured-client schema.
They belong to observer, lifecycle, or exchange-log records. Adding them here
without a stable configured value would collapse unknown and per-request states;
such fields are deferred until a future schema can define accurate semantics.

Provider-backed rendering continues to honor an overridden `clientSummaries()`
method, including through class-based Spring proxies. Such custom summaries use
the collection contract, so provider-only strict flags remain unknown. Diagnostics
do not instantiate client FactoryBeans, auth providers, lazy Resilience4j registry
beans, unresolved Resilience4j instances, proxy connections, or other network
resources. For a client with at least one method enabled by `retry-methods`, a
strict-retry flag stays unknown until its selected registry and Retry instance already
exist. If no method is retry-eligible, the flag is `false` without registry inspection.
Registry selection searches parent factories and follows Spring direct-type lookup
semantics for sole, primary, priority, fallback, and default candidates. An
already-cached singleton `FactoryBean` product can supply that evidence, while an
uncached or uninspectable `FactoryBean` product remains unknown and is not created by
diagnostics. An uninstantiated factory whose static product type is unknown or only a
supertype of the requested registry keeps registry-dependent facts unknown,
regardless of bean role or scope, unless bean-definition metadata proves selection
is unaffected: candidate filtering excludes it, or an existing registry is the
sole primary or non-fallback candidate.

Strict built-in body-signing status follows the same side-effect-free rule. If
selecting the effective `AuthProviderFactory` would instantiate a lazy or
otherwise unresolved factory, `strictBodySigningValidation` is `null`; a support
query never creates that factory merely to turn the value into `true` or `false`.

Snapshots fail explicitly instead of returning partial counts when they exceed
256 clients, 10,000 aggregate endpoints, 512 characters in an exported text
field, or 1 MiB of UTF-8 encoded JSON/Markdown. Map and Actuator output are
measured through the same JSON renderer before they are returned, so they cannot
bypass the UTF-8 byte limit. These limits bound support-output
size and client/interface cardinality; `clientCount`, `endpointCount`, and
`inheritedEndpointCount` therefore always describe every emitted client. Health
details use the same client and text bounds and deterministic client-name ordering;
their fixed per-client field set remains below the same UTF-8 output ceiling.

## Opt-in Actuator diagnostics endpoint

When `spring-boot-starter-actuator` is on the classpath, the starter can expose
the same sanitized JSON snapshot through a disabled-by-default Actuator endpoint.
Enable the bean with the starter property and expose the Actuator endpoint through
normal Spring Boot management endpoint configuration:

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

The endpoint id is `rhttpclients`. A read operation returns diagnostics schema v1 JSON with
`schemaVersion`, `projectVersion`, `clientCount`, `endpointCount`, `inheritedEndpointCount`, and
one `clients` entry per registered client. Each client entry includes client
name, interface, base URL source, effective pool source/maximum/pending-acquire-timeout/metrics policy, bounded aggregate cache policy/runtime state, timeout source/value, `logicalCallTimeoutMs`, configured compression, decoded aggregate limit, resilience summary, auth
mode, redirect-following flag, strict unsafe-retry and strict body-signing
validation flags, endpoint count, and inherited endpoint count. Strict flags are
true only when the corresponding validation path is active for the resolved
client configuration.

Resilience fields use the same effective policy as invocation and startup:
`disabled` means no effective selection, `unavailable` means selected without a
matching runtime operator, an instance name means active, and `unknown` means a
lazy registry or `FactoryBean` product cannot be inspected without side effects.
Reading diagnostics does not create those lazy components.

This endpoint is for support-safe configured-client diagnostics. It uses the
same sanitized fields as `ReactiveHttpClientDiagnosticsSnapshot`; it does not
include concrete base URL values, header values, auth-provider bean names, proxy
credentials, request bodies, or response bodies. It differs from health details,
which report recent Micrometer error-rate status, and from exchange logs, which
report per-call request/response metadata.

When DEBUG logging is enabled for `ReactiveHttpClientFactoryBean`, startup logs
include one sanitized `startup summary` line per client using the same summary
fields: client name, interface, endpoint count, inherited endpoint count, base
URL source, timeout summary, resilience summary, auth mode, redirect policy, and
observability state. The summary line does not include concrete base URL values,
header values, auth-provider bean names, proxy credentials, request bodies, or
response bodies. It is DEBUG-only; normal INFO startup logs do not include this
support summary.

See [Exchange Logging](13-exchange-logging.md), [Lifecycle Hooks](19-lifecycle-hooks.md),
[Error Handling](03-error-handling.md), and [Production Support Bundles](26-support-bundles.md)
for extension-point-specific guidance.
