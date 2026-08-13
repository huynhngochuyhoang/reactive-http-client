# Operations Troubleshooting

Use this page as the canonical current first-response index for an outbound
client incident. It connects the starter's bounded diagnostic signals to
the detailed operating guides; it does not replace transport traces, downstream
logs, or application metrics.

## Current release scope

Current consumer instructions apply to published starter `3.4.0` on Spring Boot
4. The repository may contain a newer snapshot while the next release is being
prepared. Use the published coordinates from the [Quick Start](01-quick-start.md)
for applications and reserve snapshot commands for the explicitly labeled
development and release workflows.

Historical benchmark reports, API reports, migration decisions, and changelog
sections describe the release named in each artifact. Do not update their
versions when the current development or published baseline changes. The
[Native Image and Release Compatibility](20-native-release-compatibility.md)
and [Benchmarks](22-benchmarks.md) guides label current commands separately from
historical evidence.

## First response

1. Capture the sanitized baseline bundle from
   [Production Support Bundles](26-support-bundles.md#baseline-bundle).
2. Identify the affected client, method, logical-call time window, exception
   type, `ErrorCategory`, optional `failure.stage`, status, and subscription
   attempt count.
3. Select the matching row below. A missing status or failure stage means
   unknown; do not infer a transport phase from elapsed time alone.
4. Correlate client evidence with the downstream, proxy, ingress, service mesh,
   DNS, and TLS path before assigning ownership.

| Symptom | First bounded evidence | Next check |
|---|---|---|
| Unexpected HTTP/1.1, H2, or H2C behavior; malformed-request warning | Client `http2-enabled` policy, downstream-observed protocol, complete decoder warning, ALPN/TLS mode, intermediary path | [Protocol diagnosis](#protocol-and-framing) |
| Gzip decode failure, unexpected encoded body, or response size unknown | Client `compression-enabled` policy, presence of negotiation/content-encoding headers, post-transport response headers, exception type | [Compression diagnosis](#compression) |
| Pending requests, acquire timeout, or connection churn | Effective pool policy, active/idle/total/pending gauges, `POOL_ACQUIRE`, health `poolAcquireFailureCount` | [Pool saturation](#pool-saturation) |
| Reset, peer close, or replacement after an otherwise healthy HTTP/1.1 call | Final call status/stage, one-call dispatch count, active/pending/idle/total gauges, downstream connection evidence | [Stale HTTP/1.1 connections](#stale-http11-connections) |
| DNS, proxy, connect, TLS, or certificate failure before status | Concrete exception chain, optional failure stage, sanitized resolver/proxy/TLS evidence | [Pre-response failures](#pre-response-transport-failures) |
| Timeout before or after status | Concrete exception, final status, final-attempt dispatch evidence, optional failure stage | [Timeout phases](#timeout-phases) |
| Upload stalls, cancellation, leaked buffers, or incomplete stream | Declared body/return shape, subscription and cancellation boundary, consumer release/forwarding path | [Streaming ownership](#streaming-ownership) |
| OAuth2 refresh storm, token endpoint failure, or downstream 401 | Logical client name, sanitized auth mode, token endpoint status and safe headers, refresh/cooldown timing | [OAuth2 refresh](#oauth2-refresh) |
| Category and stage appear inconsistent or stage is absent | Outermost exception plus bounded cause chain, category, stage, status, cancellation, final attempt | [Failure attribution](#failure-attribution) |

## Evidence boundary

Keep shared incident evidence bounded to one affected client, one logical-call
window, and the minimum samples needed to show the failure. Record configured
policy, counts, status, exception and cause types, optional `failure.stage`, and
header presence; do not collect request or response payloads by default.

Use `.example.invalid` hosts and `EXAMPLE_` placeholders in reviewable commands
and configuration. Before sharing output, redact credentials, tokens, cookies,
authorization and signed headers, idempotency-key values, internal addresses,
certificate subjects, and query strings. The
[Production Support Bundles](26-support-bundles.md#baseline-bundle) fixture is
the canonical reviewable layout.

## Protocol and framing

- `http2-enabled: false` uses HTTP/1.1. With it enabled, an HTTPS base URL uses
  H2 over TLS/ALPN and an HTTP base URL uses H2C; there is no silent HTTP/1.1
  fallback.
- Record the protocol observed by the downstream and every intermediary. Do not
  infer the wire protocol from the annotation or URL alone.
- Preserve the full first decoder exception when Reactor Netty logs synthetic
  `GET /bad-request HTTP/1.0`. That placeholder alone does not prove the starter
  sent such a request.
- Do not add `Content-Length`, `Transfer-Encoding`, `Connection`, `Expect`, or
  `Host`; framing and authority belong to the transport.
- A healthy unexpected body on `Mono<Void>` or `Mono<ResponseEntity<Void>>` is
  drained, so a later pooled request can reuse the connection. Invalid or
  conflicting `Content-Length`, invalid chunk framing, and truncated fixed-length
  bodies fail the affected exchange and remove that socket before reuse.
- A valid HTTP/1.1 close-delimited body can complete normally, but the connection
  closes by definition and later demand uses replacement capacity. Correlate the
  observed status with the concrete decoder or premature-close type; never infer
  a failure stage from parser message text.
- On the currently supported Reactor Netty stack, WebClient can expose a raw
  HTTP/1.1 `103 Early Hints` block instead of the later final response. Capture
  wire evidence before treating such metadata as the downstream's final status.

See [HTTP/2, HTTP Proxy, and TLS / mTLS](12-proxy-tls.md#http2) and its
[transport-owned request header](12-proxy-tls.md#transport-owned-request-headers)
contract.

## Compression

- `compression-enabled` negotiates and incrementally decompresses responses. It
  does not compress request bodies.
- When connector compression is enabled, application code must not also supply
  `Accept-Encoding` through defaults, method arguments, forwarding, or filters.
- Unary values and `ResponseEntity<T>` enforce `codec-max-in-memory-size-mb` on
  decoded bytes after decompression. Error retention uses separate decoded 4 KiB
  and 64 KiB caps; bodiless paths drain; `DataBuffer` streams remain incremental
  and caller-owned.
- Automatic decompression can remove the encoded `Content-Length` and
  `Content-Encoding`; response-size diagnostics are then unknown. Do not consume
  a stream merely to calculate a size.
- Compare encoded downstream bytes, decoded application bytes, any advertised
  post-transport length, and whether the body was actually consumed.
- Corrupt gzip closes the affected pooled connection. A framing-complete truncated
  gzip member can still produce partial decoded data, so use an application checksum
  or format-level completeness check when whole-payload integrity is required.

See [Response compression](12-proxy-tls.md#response-compression) and
[response-size semantics](08-observability.md#reactivehttpclientrequestsresponsesize-distributionsummary).

## Pool saturation

- For HTTP/1.1, compare
  `reactive.http.client.connection.pool.active.connections` with the configured
  maximum and inspect `.pending.connections` over the same time window.
- For HTTP/2, inspect physical connection gauges together with
  `reactive.http.client.connection.pool.active.streams` and `.pending.streams`;
  the peer-advertised stream limit is not exported.
- `POOL_ACQUIRE` is bounded evidence of an acquire timeout, pending limit, or
  pool shutdown, but not proof of connection versus stream pressure. It retains
  `ErrorCategory.TIMEOUT`; an absent stage is unknown.
- Check cancellation, idle/lifetime eviction, and factory shutdown before
  increasing limits. A larger queue can hide overload and increase latency.

See [Diagnosing saturation](05-connection-pool.md#diagnosing-saturation).

### Stale HTTP/1.1 connections

Distinguish normal retirement from a failed exchange. `Connection: close`, a
peer FIN after a complete response, and a peer closing an idle keep-alive socket
retire that physical connection; a later independent call should use replacement
capacity. A reset after dispatch or a close during response consumption fails the
affected call. The pool can remove the unusable channel and serve queued demand
without replaying the failed request.

Correlate one logical call with downstream request count and the pool gauges. A
replacement call should have its own URL, status, response headers, error, and
failure stage; it must not inherit facts from the stale call. Active and pending
gauges should return to zero after work completes, while total/idle reflect the
replacement connection. If the failed request appears more than once, verify an
explicit Resilience4j retry configuration and its method, idempotency-key, body
repeatability, and subscription-attempt evidence before calling that replay safe.
The starter disables Reactor Netty's separate one-time connection-reset retry, so
a reset before request headers are sent is still one transport dispatch unless an
explicit higher-level policy resubscribes.

Idle and lifetime eviction reduce exposure to known intermediary timeouts but do
not eliminate close-versus-reuse races. Capture a bounded packet trace or peer
connection log when the close ordering matters; do not infer it from a generic
timeout alone. See
[Stale connection retirement and replacement](05-connection-pool.md#stale-connection-retirement-and-replacement).

### HTTP/2 retirement versus connection failure

A peer `GOAWAY(NO_ERROR)` is graceful retirement, not by itself a request
failure. Preserve the error code and last-stream identifier when the peer or an
intermediary exposes them. Streams accepted at or below that identifier may
finish on the old socket; do not classify them as unprocessed or replay-safe.
New demand must not use the draining socket.

During retirement, correlate `total.connections`, `active.streams`, and
`pending.streams`. With `max-connections: 1`, the draining socket can occupy the
only physical slot until its accepted streams finish and the peer closes it, so
pending streams can rise temporarily before a replacement connection appears.
They must converge after replacement dispatch or bounded pool shutdown. A reset,
compression error, timeout, or premature close is a stream/connection failure
and should be diagnosed from its terminal error and failure stage; do not infer
one from a graceful GOAWAY alone. Factory shutdown applies the same bounded
five-second connection-provider disposal policy to draining and pending work.

See [GOAWAY and connection retirement](12-proxy-tls.md#goaway-and-connection-retirement).

## Pre-response transport failures

Use this bounded matrix; do not infer ownership from elapsed time or exception text alone.
The commands are out-of-process checks against approved diagnostic endpoints, not proof
that the starter made the same request. Replace every `EXAMPLE_` value locally and redact
hosts, certificate subjects, and proxy details before sharing output.

| Stage | Proven runtime evidence | Sanitized reproduction |
|---|---|---|
| `DNS_RESOLUTION` | `UnknownHostException` on the final business-request transport chain | Resolve an approved diagnostic name with `getent hosts "$EXAMPLE_DIAGNOSTIC_HOST"` and capture resolver configuration without search-domain secrets. |
| `PROXY_CONNECT` | Netty `ProxyConnectException`; an HTTPS tunnel rejection can have an outer TLS exception | Run `curl --proxy "http://$EXAMPLE_PROXY_HOST:$EXAMPLE_PROXY_PORT" --head --verbose "https://$EXAMPLE_SAFE_TARGET"`; never include proxy credentials. |
| `CONNECT` | Direct `ConnectException` or Netty connect timeout | Run `curl --connect-timeout 2 --head "http://$EXAMPLE_HOST:$EXAMPLE_PORT/health"` from the same network boundary. |
| `TLS_HANDSHAKE` | `SSLException`, including certificate validation | Run `openssl s_client -connect "$EXAMPLE_HOST:$EXAMPLE_PORT" -servername "$EXAMPLE_SERVER_NAME" -brief </dev/null`; share only approved certificate metadata. |
| `POOL_ACQUIRE` | Reactor Pool acquire timeout, pending limit, or shutdown | Reproduce only with the bounded load fixture described under [Pool saturation](#pool-saturation); it does not distinguish H1 connection from H2 stream pressure. |
| `REQUEST_WRITE` | Netty write timeout after transport dispatch | Use an approved non-secret bounded upload fixture and record cancellation plus bytes accepted by the fixture; do not replay production payloads. |
| `RESPONSE_HEADERS` | Read timeout after final dispatch with no observed status | Use an approved fixture that delays headers beyond the configured timeout and record that the fixture received the request. |
| `RESPONSE_BODY` | Read timeout after an observed status | Use an approved fixture that sends headers and one bounded chunk before delaying; release consumed buffers. |

`ErrorCategory` remains unchanged: DNS maps to `UNKNOWN_HOST`, TLS to `TLS_ERROR`,
and direct connect failures to `CONNECT_ERROR`. Proxy tunnel failures retain whichever
existing category the outer exception proves. A nested `AuthProviderException` is a hard
boundary. A custom filter that fails before the final request observation cannot promote
a nested transport exception into a business-request stage. Provider diagnostics and the
Actuator `rhttpclients` endpoint expose configuration, not request-scoped stages.

## Timeout phases

Use only a proven stage: `DNS_RESOLUTION`, `PROXY_CONNECT`, `CONNECT`,
`TLS_HANDSHAKE`, `POOL_ACQUIRE`, `REQUEST_WRITE`, `RESPONSE_HEADERS`, or
`RESPONSE_BODY`. `RESPONSE_HEADERS` requires final-attempt
dispatch evidence and no observed status. `RESPONSE_BODY` retains the observed
status. Auth-provider failures are a hard boundary, and arbitrary custom-filter wrappers
without final-request dispatch evidence stay unattributed.

For `LogicalCallTimeoutException`, compare `logical-call-timeout-ms` with the
per-attempt response timeout and the final subscription-attempt count. The logical
budget includes resilience admission, auth, pool waiting, redirects, retries, and
starter-owned response consumption without resetting between them. Expiry before
dispatch or between attempts intentionally has no transport stage.

For a streaming envelope, the outer terminal record covers envelope delivery;
a later inner-body timeout belongs to the body subscriber. See
[Proven timeout phases](04-timeouts.md#proven-timeout-phases).

## Streaming ownership

- A publisher upload is cold until the returned client publisher is subscribed.
  Each real retry, body-preserving redirect, or one-time 401 refresh creates a
  new request-body subscription.
- The application must make replayed publishers repeatable. The starter does
  not aggregate an unbounded publisher to make it reusable.
- A consumer owns emitted `DataBuffer` values. Release manually consumed or
  discarded buffers, or transfer them to a component that documents ownership.
- For HTTP/1.1, known-length resources use transport-generated
  `Content-Length`; publisher and application-stream bodies use chunked framing.
  HTTP/2 uses DATA frames without a wire `Transfer-Encoding` header; Reactor
  Netty server handlers can expose a synthetic chunked compatibility value after
  decoding.
- On an approved reproduction, record only body shape, framing headers, bounded
  bytes accepted, cancellation, and final failure stage. Never capture payloads.
- `REQUEST_WRITE` requires a write timeout after final request dispatch. An auth
  or custom-filter failure before dispatch remains at its owning boundary.
- `Mono<ResponseEntity<Flux<DataBuffer>>>` reports envelope completion, not
  inner-body completion.

See [Streaming Requests and Responses](11-streaming.md).

## OAuth2 refresh

- Record the logical downstream client name, auth mode, token endpoint status,
  safe response headers such as `Retry-After`, cause type, and refresh/cooldown
  timing.
- Shared refresh is single-flight. A cancelled waiter must not cancel refresh
  work needed by other clients, and each waiter must retain its own logical
  client name on failure.
- A downstream 401 can trigger one invalidation and refresh. Distinguish that
  hidden auth retry from configured resilience subscription attempts.
- Never collect token request metadata, bearer/refresh tokens, Basic
  credentials, client secrets, signed authorization headers, or raw token
  bodies.

See [Refreshing bearer auth](06-auth-providers.md#refreshingbearerauthprovider-cached-token-with-auto-refresh)
and [OAuth2 client credentials](06-auth-providers.md#oauth2clientcredentialstokenprovider-standard-oauth-20-client-credentials).

## Failure attribution

`ErrorCategory` describes the compatible public error class; `failure.stage`
describes an optional proven transport boundary. They are related but not
interchangeable. Retry wrappers preserve the final cause and logical
subscription-attempt count. Cancellation remains `CANCELLED`, and an absent
stage must stay unknown.

Exchange logs can retain sanitized post-transport response headers. Observer
events carry byte counters and final request metadata, while lifecycle contexts
do not expose response-header maps or byte counters. Provider diagnostics
describe configured clients; they do not describe a particular request.

See [Error Handling](03-error-handling.md),
[Diagnostic Context Contracts](21-diagnostic-contexts.md), and the
[failure-attribution bundle](26-support-bundles.md#failure-attribution-incidents).
