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
| Proven failure stage | `failureStage()` | `getFailureStage()` | `failureStage()` | No |

Subscription-attempt values count reactive subscriptions, not guaranteed HTTP
network sends. Request-body serialization can fail after an attempt starts but
before dispatch. Terminal cancellation is represented by a `CancellationException`
and `ErrorCategory.CANCELLED`; it is not a timeout phase. `CONNECT`, `POOL_ACQUIRE`,
`REQUEST_WRITE`, `RESPONSE_HEADERS`, and `RESPONSE_BODY` are reported only from
concrete transport evidence. Generic timeouts remain stage-unknown.

For `Mono<ResponseEntity<Flux<DataBuffer>>>`, terminal lifecycle, observer, and exchange-log records describe response-envelope completion. They do not indicate that the inner streamed body was subscribed or fully consumed. A later inner-body timeout or cancellation does not rewrite the already reported successful envelope terminal record. Direct `Flux<DataBuffer>` methods report terminal state when the stream itself completes, errors, or is cancelled.

## Header handling

Custom `HttpExchangeLogger` implementations receive raw final outbound request
headers and raw response headers. `DefaultHttpExchangeLogger` applies its
sensitive-header deny-list only when formatting built-in log output. Its inbound
header snapshot is already filtered and redacted by `InboundHeadersWebFilter`.

Custom `HttpClientObserver` implementations receive raw final outbound request
headers. Observer events do not expose response headers. The built-in Micrometer
and OpenTelemetry observers do not log request-header values.

Lifecycle hooks receive prepared resolved request headers before later
`WebClient` filters run. Error mappers receive read-only raw response headers.
Custom extension points that persist header values must apply their own
redaction policy.

## Health, diagnostics, and exchange logs

The Actuator health indicator is a status signal derived from Micrometer request timers. It reports bounded per-client sample counts, error counts, error rates, thresholds, and reasons for the latest health-probe window. It does not describe configured endpoints or per-call payload metadata.
Pool-acquire failures add a bounded `poolAcquireFailureCount` for the same probe window.

Use exchange logging for per-call request/response metadata, and use the diagnostics provider when support output needs configured-client summaries.

## Runtime diagnostics provider

Applications can inject `ReactiveHttpClientDiagnosticsProvider` to inspect sanitized registered-client summaries at runtime. The provider reports the client name, client interface, base URL source, effective pool source/maximum/pending-acquire-timeout/metrics policy, timeout summary, resilience summary, auth mode, redirect-following flag, endpoint count, and inherited endpoint count. It does not expose base URL values, header values, proxy credentials, auth-provider bean names, request bodies, or response bodies.

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
[source-controlled v1 fixture](fixtures/rhttpclients-schema-v1.json) is the
regression-review baseline.

Value semantics are explicit:

| Value | Meaning |
|---|---|
| `true` | The boolean policy or strict-validation path is active and was resolved by the provider-backed snapshot. |
| `false` | The provider-backed snapshot proved that policy or strict-validation path inactive. |
| `null` / `unknown` | Provider-only data is unavailable to a collection-backed JSON/map or Markdown snapshot; it is not equivalent to `false`. |
| `disabled` | The timeout or resilience operator is not applied by effective client/method policy. A disabled timeout is `timeoutSource=disabled` and `timeoutMs=0`. |
| `unavailable` | Resilience is enabled for the effective contract, but the optional operator adapter/registry is unavailable at runtime. |
| `missing` | No annotation or property supplies the required base URL source; concrete URL values are never exported. |

Provider-backed rendering continues to honor an overridden `clientSummaries()`
method, including through class-based Spring proxies. Such custom summaries use
the collection contract, so provider-only strict flags remain unknown.

Snapshots fail explicitly instead of returning partial counts when they exceed
256 clients, 10,000 aggregate endpoints, 512 characters in an exported text
field, or 1 MiB of UTF-8 encoded JSON/Markdown. These limits bound support-output
size and client/interface cardinality; `clientCount`, `endpointCount`, and
`inheritedEndpointCount` therefore always describe every emitted client.

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
name, interface, base URL source, effective pool source/maximum/pending-acquire-timeout/metrics policy, timeout source/value, resilience summary, auth
mode, redirect-following flag, strict unsafe-retry and strict body-signing
validation flags, endpoint count, and inherited endpoint count. Strict flags are
true only when the corresponding validation path is active for the resolved
client configuration.

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
