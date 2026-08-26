# Production Checklist

Use this checklist before putting a `@ReactiveHttpClient` client on a production path.

## Client contract

- Define one Java interface per downstream service.
- Set `@ReactiveHttpClient(name = "...")` to match `reactive.http.clients.<name>`.
- Keep method paths, HTTP verbs, path variables, query parameters, and body types explicit.
- Use `@ApiRef` only when method/path/timeout must be controlled from configuration.

Use the canonical [request-parameter grammar](02-annotations.md#parameter-annotations)
for startup declaration checks and per-invocation value checks. For multipart
methods, apply the [wire-order and resource-ownership contract](10-multipart.md#wire-order-and-framing)
before enabling retry, redirect, or auth replay.

### Supported return shapes

| Return shape | Contract |
|---|---|
| `Mono<Void>` | Drain and release the body; expose no value. |
| `Mono<T>` | Decode one value with the configured codecs. |
| `Mono<ResponseEntity<T>>` | Decode one value and preserve status and headers. |
| `Flux<T>` | Decode a stream of values with the configured codecs. |
| `Flux<DataBuffer>` | Stream unaggregated, caller-owned buffers. |
| `Mono<ResponseEntity<Flux<DataBuffer>>>` | Preserve status and headers while the caller owns the unaggregated inner stream. |

Raw `Mono` and `Flux` remain compatibility shapes but erase response type
information. Nested publishers, nested or `Flux`-wrapped `ResponseEntity`
values, unresolved type variables, and concrete `DataBuffer` subtypes are not
supported. See the canonical [declarative return-type grammar](02-annotations.md#declarative-return-types)
and [streaming ownership contract](11-streaming.md#streaming-responses).

## Timeouts and pool

- Set `reactive.http.network.connect-timeout-ms`.
- Keep `network-read-timeout-ms` and `network-write-timeout-ms` above normal request timeouts.
- Use `@TimeoutMs` or `reactive.http.clients.<name>.request-timeout-ms` for per-request response limits.
- Use opt-in `logical-call-timeout-ms` when the complete call must have one budget across auth, admission, redirects, and retries.
- Tune `connection-pool.max-connections` and `pending-acquire-timeout-ms` for expected concurrency.
- Use per-client `pool` blocks only when one client needs different limits.

## Protocol and network

- Leave `http2-enabled: false` unless the downstream supports HTTP/2.
- Enable HTTP/2 per client:

```yaml
reactive:
  http:
    clients:
      inventory:
        base-url: https://inventory.example.invalid
        http2-enabled: true
```

- Configure proxy and TLS globally when most clients share the same network path.
- Use per-client proxy/TLS blocks when one downstream has different routing or trust material.
- Never use `tls.insecure-trust-all: true` outside local development.
- Leave `compression-enabled: false` unless response compression is required.
- When compression is enabled, do not add `Accept-Encoding` in application
  headers; Reactor Netty owns negotiation and incremental decompression.

Wire-contract checks:

| Feature | Production rule verified by the transport fixtures |
|---|---|
| HTTP proxy | `HTTP` uses `CONNECT` for HTTP and HTTPS targets; deprecated `HTTPS` is only an `HTTP` alias. `SOCKS4` and `SOCKS5` use their respective tunnels. |
| mTLS | Configure both trust and key stores. Missing or untrusted client identities fail during `TLS_HANDSHAKE` for HTTP/1.1 and TLS H2. |
| HTTP/2 retirement | Accepted streams at or below the peer GOAWAY boundary may finish, but new streams do not use the draining connection. Replacement can start immediately when physical pool capacity remains; a one-connection pool waits for capacity. GOAWAY alone never proves replay safety. |

Use the detailed [HTTP/2, proxy, and TLS/mTLS contract](12-proxy-tls.md) for
configuration and incident interpretation.

## Auth and request defaults

- Prefer an `AuthProvider` for credentials that rotate or require signing.
- Use built-in `auth` blocks for OAuth2 client credentials or AWS SigV4.
- Do not configure both `auth-provider` and `auth.type` unless you intentionally want the bean to win.
- Use `default-headers` only for static non-secret headers.
- Use `default-query-params` only for stable parameters that belong on every request.

## Resilience

- Enable resilience per client only after choosing the Resilience4j instances:

```yaml
reactive:
  http:
    clients:
      inventory:
        resilience:
          enabled: true
          retry: inventory
          circuit-breaker: inventory
          bulkhead: inventory
          rate-limiter: inventory
          retry-methods: [GET, HEAD]
```

- Keep retries limited to idempotent methods unless the downstream contract is explicitly safe.
- Use bulkheads and rate limiters for shared downstreams that can overload callers.

The fixed composition is one logical-call timeout around one bulkhead,
circuit-breaker, and rate-limiter admission, with retry and its request attempts
inside those operators. See [operator composition](07-resilience4j.md#operator-composition).

### Replay-safety decision path

Before enabling retry, automatic `307`/`308` redirect following, or one-time
`401` auth refresh for a request that carries a body:

1. **Can another dispatch occur?** If none of those mechanisms apply, no replay
   contract is needed.
2. **Is the operation duplicate-safe?** Supported declarative `GET`,
   `HEAD`, `PUT`, `DELETE`, and `OPTIONS` methods are classified as safe.
   For `POST`, `PATCH`, or another unsafe method, require a downstream
   idempotency contract and an actual nonblank `Idempotency-Key` on every call.
3. **Can the body produce the same bytes again?** Scalar bytes, strings, and
   concrete DTOs are repeatable. A publisher must be cold and replayable, and a
   `Resource` must reopen. Do not replay an eager `InputStream`, `Reader`,
   channel, direct `DataBuffer`, `Object`, or erased generic; redesign that
   method around a cold replayable publisher or reopenable resource first. The
   starter does not buffer these bodies to make them repeatable.
4. **Does auth signing support that body?** Built-in AWS SigV4 rejects body
   shapes whose stable bytes cannot be proven before dispatch.
5. **Can startup prove the policy?** Enable strict unsafe-retry or strict
   built-in signing validation only for contracts that do not depend on a
   nullable runtime header or unknown runtime body type.

Every resilience retry is a new subscription attempt; a body-preserving
redirect or hidden `401` refresh can add dispatches and body subscriptions
inside that attempt. A generated idempotency key stays stable within one outer
subscription. Use the canonical [retry/replay composition contract](07-resilience4j.md#retry-redirect-and-auth-replay)
and [request-body repeatability matrix](11-streaming.md#request-body-repeatability-matrix)
for the detailed ownership rules.

## Response caching (`4.0.0+`)

- Select a named policy explicitly at the client or method. A policy definition
  alone is inert, and `@CacheDisabled` excludes a method.
- Require finite `ttl-ms` and `maximum-size`. Keep TTL no longer than the
  business staleness budget, not merely the available heap.
- Partition every response-changing path, query, stable tenant/auth scope,
  locale, and other declared variant. Use `shared-response: true` only after
  reviewing the deliberate sharing boundary.
- Treat cached decoded values as immutable because hits return the retained
  object identity.
- Expect per-instance divergence during deployments. The cache is process-local,
  does not coordinate pods, and performs no automatic write invalidation.
- Keep unsupported non-GET, streaming, unresolved, and application-owned body
  contracts uncached. Empty completions and failures are never stored.
- Classify every applicable WebClient/customizer mutation before caching; a hit
  can bypass the downstream request pipeline.
- Do not enable `single-flight`, refresh, or cache telemetry implicitly. Each
  phase is separately opt-in:

| Selection | Additional behavior |
|---|---|
| Named policy with TTL/capacity only | Local bounded cache; concurrent misses may dispatch independently and the first successful fill wins. |
| `single-flight: true` | Same-key concurrent misses share one load; callers retain separate timeout/cancellation. |
| `refresh-after-ms` plus `refresh-timeout-ms` | A stale access returns the current value and may start one bounded hidden refresh before hard TTL. |
| `reactive.http.observability.cache.enabled: true` under the global gate | Bounded cache meters and terminal cache outcomes; it does not select caching. |

Use [Response Caching](32-response-caching.md) for the complete eligibility,
isolation, auth/customizer, and native contract. Use the
[cache dashboard recipes](08-observability.md#cache-hit-ratio-dimensionless)
and [response-cache support bundle](26-support-bundles.md#response-cache-incidents)
for production evidence.

## Observability

- Keep `reactive.http.observability.enabled: true`.
- Review metric tags before enabling high-cardinality fields such as resolved
  server address.
- Use the published error categories from [Error Handling](03-error-handling.md) in alerts and dashboards.
- Build Prometheus panels from the
  [unit-safe dashboard recipes](08-observability.md#dashboard-recipes): main
  timer durations are seconds, attempts are counts per logical call, and pool
  gauges are counts rather than percentages.
- Do not use attempts `_max` as p95/p99 or derive zero-attempt rejection from
  attempts count/sum. Use the starter timer's `error_category="RESILIENCE_ERROR"`
  recipe for every built-in admission rejection.
- Reserve Resilience4j counters for CircuitBreaker call history and Retry
  activity. Auto-bound RateLimiter and Bulkhead metrics are current-state gauges.
- Enable OpenTelemetry with the `reactive-http-client-otel` module when traces are required.
- For async handoff through sinks, queues, or callbacks, restore request context explicitly before outbound calls.
- Prefer explicit envelope fields for durable events: correlation ID, request ID, tenant-like low-cardinality keys, and trace context.
- Do not place large or sensitive inbound header snapshots in long-lived queues. Use full `RequestContextSnapshot` only for short-lived in-process handoff.

## Logging and testing

- Use [Production Support Bundles](26-support-bundles.md) when collecting safe incident evidence.
- Start incident diagnosis with [Operations Troubleshooting](30-operations-troubleshooting.md)
  and treat a missing failure stage as unknown.
- Keep exchange body logging disabled unless a production incident requires it.
- Keep `log-preset: metadata-only` by default; use `headers` or `bodies` only for targeted investigations.
- Redact sensitive headers in inbound snapshots and exchange logs.
- Cover client behavior with `reactive-http-client-test` mocks for status mapping, headers, query params, and auth.
- Run `mvn test`, `mvn -Prelease-smoke test`, and
  `mvn -Papi-compatibility -DskipTests verify` before release.
