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

Subscription-attempt values count reactive subscriptions, not guaranteed HTTP
network sends. Request-body serialization can fail after an attempt starts but
before dispatch.

For `Mono<ResponseEntity<Flux<DataBuffer>>>`, terminal lifecycle, observer, and exchange-log records describe response-envelope completion. They do not indicate that the inner streamed body was subscribed or fully consumed. Direct `Flux<DataBuffer>` methods report terminal state when the stream itself completes, errors, or is cancelled.

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

See [Exchange Logging](13-exchange-logging.md), [Lifecycle Hooks](19-lifecycle-hooks.md),
and [Error Handling](03-error-handling.md) for extension-point-specific guidance.
