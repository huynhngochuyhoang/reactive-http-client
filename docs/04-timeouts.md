# Timeouts

The starter has transport safety nets, an optional per-attempt response timeout,
and an optional end-to-end logical-call budget. Understanding which boundary
fired is critical to avoiding hard-to-debug incidents.

---

## Timeout layers

| Layer | Property / annotation | Default | Scope | Fires when |
|---|---|---|---|---|
| Logical-call budget | `reactive.http.clients.<name>.logical-call-timeout-ms` | disabled | One caller subscription | Total wall-clock time from subscription through resilience admission, serialization, auth, pool acquisition, redirects, retries, and starter-owned response consumption reaches the budget |
| TCP connect timeout | `reactive.http.network.connect-timeout-ms` | 2 000 ms | TCP handshake only | A new connection cannot be established within the limit |
| Per-request response timeout | `@TimeoutMs(ms)` (method), `@ApiRef timeout-ms`, or `request-timeout-ms` (client) | disabled | Per attempt | No response headers or body data arrive within the configured read interval; retries each install their own timeout |
| Safety-net read timeout | `reactive.http.network.network-read-timeout-ms` | 60 000 ms | Per pooled connection | No inbound bytes for this duration — catches stuck sockets |
| Safety-net write timeout | `reactive.http.network.network-write-timeout-ms` | 60 000 ms | Per pooled connection | No outbound bytes accepted for this duration |

---

## Precedence rules

1. `@TimeoutMs(ms)` on the method takes highest precedence.
2. `@ApiRef timeout-ms` applies to API-map methods when no method annotation is present.
3. `request-timeout-ms` on the client config applies when neither method nor API-map timeout is present.
4. Deprecated `resilience.timeout-ms` is accepted as a compatibility alias only when `request-timeout-ms` is not configured.
5. Safety-net timeouts (`network-read-timeout-ms` / `network-write-timeout-ms`) are independent of the per-request timeout and act as absolute upper bounds on socket inactivity.

`logical-call-timeout-ms` does not participate in that precedence chain. It is
an independent outer deadline for the concrete client. `0` disables it. When it
is enabled together with connect, pool-acquire, request-write, response, method,
or Resilience4j limits, the first failure wins. A retry gets a fresh per-attempt
response timeout, but it does not get a fresh logical-call budget.

For inherited endpoint methods, the method metadata comes from the parent interface,
but the client-level `request-timeout-ms` comes from the concrete
`@ReactiveHttpClient` child being invoked. Two child clients can therefore share
the same parent method and still use different client-level timeout policies.

**Rule of thumb:** set the safety-net timeouts well above the largest `@TimeoutMs`, `@ApiRef timeout-ms`, or `request-timeout-ms` you use. This ensures the per-request timeout always fires first, so retries behave predictably. If the safety net fires instead, no retry is attempted — the socket is dropped.

## End-to-end logical-call budget

The logical-call budget is opt-in and subscription-local. Calling a client
method still creates a cold publisher. Every independent subscription starts a
fresh monotonic deadline; retry subscriptions, Reactor Netty redirects, and the
auth filter's one-time `401` refresh stay inside that same deadline.

```yaml
reactive:
  http:
    clients:
      user-service:
        logical-call-timeout-ms: 7000
        request-timeout-ms: 2500
```

This example allows no more than 7 seconds for the complete logical call while
retaining the existing 2.5-second response timeout on every dispatched attempt.
The logical budget can expire before dispatch while waiting for auth,
Resilience4j admission, or pool capacity; during an attempt; or between retries.
It raises `LogicalCallTimeoutException`, retains `ErrorCategory.TIMEOUT`, and
includes `HttpClientFailureStage.RESPONSE_BODY` only when the final attempt has
observed response status. Before status, the starter cannot distinguish a pool
wait from a dispatched request waiting for headers, so the stage stays unknown
rather than inventing a transport phase.

For direct `Flux<T>` responses, the budget remains active until that stream
terminates. For `Mono<ResponseEntity<Flux<DataBuffer>>>`, the budget governs only
acquisition of the response envelope. Once the outer `Mono` succeeds, the caller
owns the inner body and its later consumption is not subject to this budget.

## Proven timeout phases

`ErrorCategory` remains the stable coarse signal. The optional
`HttpClientFailureStage` adds a phase only when concrete transport state proves it:

| Stage | Proven evidence | Status/headers |
|---|---|---|
| `DNS_RESOLUTION` | `UnknownHostException` from the business-request transport | No HTTP status or response headers |
| `PROXY_CONNECT` | Netty proxy connection or tunnel failure | No downstream HTTP status or response headers |
| `CONNECT` | Connection refusal or Netty connect timeout | No HTTP status or response headers |
| `TLS_HANDSHAKE` | `SSLException` during handshake or certificate validation | No HTTP status or response headers |
| `POOL_ACQUIRE` | Reactor Pool acquire timeout, pending limit, or shutdown while waiting | No HTTP status or response headers |
| `REQUEST_WRITE` | Netty write timeout after the final request was dispatched | No response status or headers |
| `RESPONSE_HEADERS` | Netty read timeout after final request dispatch but before status was observed | No HTTP status or response headers |
| `RESPONSE_BODY` | Netty read timeout after status was observed | Status is retained; exchange logs also retain response headers |

Dispatch evidence is reset for every resilience retry and hidden 401 auth refresh,
so terminal attribution describes the final attempt. Direct concrete DNS, proxy, connect, TLS, pool-acquire, and write failures remain
attributable without URL evidence. Auth-provider failures are a hard boundary; arbitrary
pre-dispatch filter wrappers remain unknown unless final-request dispatch was observed. A generic `TimeoutException` also does not prove a transport phase. Cancellation is reported as `ErrorCategory.CANCELLED`,
not as a timeout.
DNS retains `UNKNOWN_HOST`, TLS retains `TLS_ERROR`, and connect failures retain
`CONNECT_ERROR`. Proxy failures preserve the category selected from the existing outer
exception chain, which can be `CONNECT_ERROR` or `TLS_ERROR` for an HTTPS tunnel. Read,
write, and pool-acquire timeouts retain `TIMEOUT`.

Timeouts after headers, including failures while decoding a `Mono<T>` body or
consuming a direct streaming `Flux<T>` body, preserve the observed status in
lifecycle hooks, exchange logs, and observer events. Response headers are retained
for exchange logging, but lifecycle hooks and observer events do not expose
response-header maps. Streaming responses are not buffered; already emitted items
stay visible before the timeout error.

For `Mono<ResponseEntity<Flux<DataBuffer>>>`, terminal reporting measures only the
response envelope. A later inner-body timeout is delivered to the inner subscriber
and does not rewrite the successful envelope lifecycle/observer/exchange-log record.
See [Production Support Bundles](26-support-bundles.md) for a safe timeout incident
bundle.

---

## Disabling the per-request timeout for one method

`@TimeoutMs(0)` explicitly disables the per-request timeout for a single method without affecting any other method or the safety-net timeouts:

```java
@POST("/batch-import")
@TimeoutMs(0)
Mono<ImportReceipt> batchImport(@Body ImportRequest request);
```

---

## Configuration example

```yaml
reactive:
  http:
    network:
      connect-timeout-ms: 2000
      network-read-timeout-ms: 60000   # safety net — set above all business timeouts
      network-write-timeout-ms: 60000
    clients:
      user-service:
        base-url: https://api.example.com
        logical-call-timeout-ms: 12000 # total budget across all attempts
        request-timeout-ms: 5000   # per-request default for this client
```

```java
@ReactiveHttpClient(name = "user-service")
public interface UserApiClient {

    @GET("/users/{id}")
    @TimeoutMs(3000)      // overrides request-timeout-ms for this method
    Mono<User> getUser(@PathVar("id") long id);

    @POST("/users")       // inherits request-timeout-ms = 5000
    Mono<User> createUser(@Body NewUser body);

    @GET("/users/export")
    @TimeoutMs(0)         // no per-request timeout for long exports
    Flux<User> exportAll();
}
```

---

## Deprecated property aliases

The legacy network property names `read-timeout-ms` and `write-timeout-ms` still bind for backwards compatibility but are deprecated. IDEs will flag them. Prefer `network-read-timeout-ms` and `network-write-timeout-ms`.

```yaml
# deprecated — will be removed in a future major release
reactive.http.network.read-timeout-ms: 30000

# preferred
reactive.http.network.network-read-timeout-ms: 30000
```

## Deprecated request-timeout alias

`reactive.http.clients.<name>.resilience.timeout-ms` still binds for one compatibility cycle, but new configuration should use `request-timeout-ms`. Despite its location, this property is a compatibility alias for the same native Reactor Netty per-request response timeout; the starter does not install a Resilience4j `TimeLimiter`. When both are configured, `request-timeout-ms` wins, including `0` to disable the per-request timeout.

```yaml
reactive:
  http:
    clients:
      user-service:
        request-timeout-ms: 5000
```
