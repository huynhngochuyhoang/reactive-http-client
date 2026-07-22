# Operations Troubleshooting

Use this page as the first-response index for an outbound client incident. It
connects the starter's bounded diagnostic signals to the detailed operating
guides; it does not replace transport traces, downstream logs, or application
metrics.

## Current release scope

Current consumer instructions apply to published starter `3.2.0` on Spring Boot
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
| Timeout before or after status | Concrete exception, final status, final-attempt dispatch evidence, optional failure stage | [Timeout phases](#timeout-phases) |
| Upload stalls, cancellation, leaked buffers, or incomplete stream | Declared body/return shape, subscription and cancellation boundary, consumer release/forwarding path | [Streaming ownership](#streaming-ownership) |
| OAuth2 refresh storm, token endpoint failure, or downstream 401 | Logical client name, sanitized auth mode, token endpoint status and safe headers, refresh/cooldown timing | [OAuth2 refresh](#oauth2-refresh) |
| Category and stage appear inconsistent or stage is absent | Outermost exception plus bounded cause chain, category, stage, status, cancellation, final attempt | [Failure attribution](#failure-attribution) |

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

See [HTTP/2, HTTP Proxy, and TLS / mTLS](12-proxy-tls.md#http2) and its
[transport-owned request header](12-proxy-tls.md#transport-owned-request-headers)
contract.

## Compression

- `compression-enabled` negotiates and incrementally decompresses responses. It
  does not compress request bodies.
- When connector compression is enabled, application code must not also supply
  `Accept-Encoding` through defaults, method arguments, forwarding, or filters.
- Automatic decompression can remove the encoded `Content-Length` and
  `Content-Encoding`; response-size diagnostics are then unknown. Do not consume
  a stream merely to calculate a size.
- Compare the downstream's encoded response with the post-transport headers and
  decoded data seen by the application.

See [Response compression](12-proxy-tls.md#response-compression) and
[response-size semantics](08-observability.md#reactivehttpclientrequestsresponsesize-distributionsummary).

## Pool saturation

- Compare active connections with the configured maximum and inspect pending
  connections over the same time window.
- `POOL_ACQUIRE` is bounded evidence of an acquire timeout, pending limit, or
  pool shutdown. It retains `ErrorCategory.TIMEOUT`; an absent stage is unknown.
- Check cancellation, idle/lifetime eviction, and factory shutdown before
  increasing limits. A larger queue can hide overload and increase latency.

See [Diagnosing saturation](05-connection-pool.md#diagnosing-saturation).

## Timeout phases

Use only a proven stage: `CONNECT`, `POOL_ACQUIRE`, `REQUEST_WRITE`,
`RESPONSE_HEADERS`, or `RESPONSE_BODY`. `RESPONSE_HEADERS` requires final-attempt
dispatch evidence and no observed status. `RESPONSE_BODY` retains the observed
status. Nested auth and other pre-dispatch read timeouts stay unattributed.

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
