# Reactive HTTP Client - Roadmap V22

> **Status:** draft
> **Theme:** wire-level reliability and actionable failure diagnostics after
> the first two Spring Boot 4 releases
> **Development line:** `3.2.0-SNAPSHOT`
> **Published/API baseline:** `3.1.0`

## Current State

V21 published `3.1.0` and completed the post-release transition to
`3.2.0-SNAPSHOT`. Public consumer examples, strict API compatibility, and
published-baseline benchmarks now use the Maven Central `3.1.0` artifacts.
The normal reactor is Spring Boot 4, Spring Framework 7, Jackson 3, and Java 21;
GraalVM 25 is the native-image evidence lane.

The starter already has broad production coverage:

- declarative and inherited generic endpoints, configured `@ApiRef` mappings,
  repeated headers, multipart requests, and streaming responses;
- per-client pool, proxy, TLS/mTLS, compression, HTTP/2, timeout, redirect,
  auth, and Resilience4j policy;
- OAuth2 client credentials, AWS SigV4, lifecycle hooks, observers, exchange
  logging, health, Micrometer, OpenTelemetry, and sanitized diagnostics;
- mock helpers, assembled consumers, AOT/native fixtures, API compatibility
  guards, transport stress tests, and a repeatable benchmark harness.

V22 should deepen the behavior that is hardest to prove with synthetic
`ClientResponse` fixtures: negotiated protocols, compressed wire data, pool
saturation, cancellation and backpressure, token-refresh concurrency, and
failure attribution. It should not add another broad abstraction layer.

## Release Direction

The reactor version does not predetermine the release:

| Completed V22 scope | Release direction |
|---|---|
| Correctness fixes, tests, diagnostics accuracy, and documentation only | `3.1.x` |
| Backward-compatible public diagnostics/configuration capability | `3.2.0` |
| Binary/source-incompatible public change | Defer to a future major |

Keep `3.2.0-SNAPSHOT` and public snippets on released `3.1.0` until the final
scope and release evidence justify a candidate.

## Goals

1. Prove configured protocol and compression behavior against real servers and
   observed wire semantics.
2. Make pool saturation, timeout, cancellation, and transport failures easier
   to distinguish without changing existing error-category meanings.
3. Preserve correct resource ownership under streaming backpressure and
   concurrent subscriptions.
4. Revalidate OAuth2 refresh and invalidation under concurrency, cancellation,
   malformed responses, and transient token-service failures.
5. Keep runtime, lifecycle, observer, exchange-log, diagnostics, health, mock,
   and documentation contracts aligned.
6. Maintain strict `3.1.0` API and published-artifact provenance while the
   `3.2.0-SNAPSHOT` line evolves.
7. Base any optimization or performance claim on same-stack current-versus-
   published benchmark evidence.

## Non-Goals

- Do not add HTTP/3, WebSocket, or server-side HTTP functionality.
- Do not replace Reactor Netty or Spring `WebClient`.
- Do not enable HTTP/2, compression, redirects, retries, body logging,
  diagnostics endpoint exposure, or strict validation by default.
- Do not buffer streaming bodies to simplify logging, signing, retries, or
  metrics.
- Do not split existing stable error categories without a migration plan.
- Do not add secrets, full URLs, payloads, or unbounded values to diagnostics.
- Do not reintroduce Boot 3 adapters into the normal `3.x` artifacts.
- Do not publish benchmark numbers from smoke runs or dirty/local baselines.

---

## 1. Post-`3.1.0` Baseline Integrity

Treat published `3.1.0` as the single API, consumer, and benchmark baseline.
Remove any active command that still resolves `3.0.0` for the current line
while preserving explicitly labeled historical evidence.

**Acceptance:**

- Root and module-scoped strict API checks resolve `3.1.0` from fresh,
  Maven Central-only repositories.
- Starter, test-helper, OTel, parent POM, sources, and Javadoc artifacts have
  Central remote markers and recorded checksums.
- Public examples remain on `3.1.0`; reactor-only fixtures use
  `3.2.0-SNAPSHOT`.
- Generated release evidence reports the development, published, candidate, and
  baseline versions without conflating them.
- Self-baseline, local contamination, missing POM, and stale version fixtures
  continue to fail.

## 2. Real HTTP/2 and H2C Contract

Move beyond configuration-object assertions. Exercise real H2 over TLS with
ALPN and clear-text H2C using the starter proxy, bounded connection providers,
and the same response shapes applications use.

**Acceptance:**

- Tests prove the negotiated protocol for HTTPS H2 and HTTP H2C.
- Unary JSON, `ResponseEntity<T>`, direct streaming, and streaming-envelope
  responses work over HTTP/2.
- Concurrent streams do not get mistaken for HTTP/1.1 connection reuse.
- Cancellation, timeout, reset, 4xx/5xx mapping, and shutdown release stream and
  connection resources.
- TLS protocol/cipher configuration and HTTP/2 selection compose correctly.
- HTTP/1.1 remains the unchanged default.

## 3. Compression and Content-Encoding Correctness

Define what `compression-enabled` means at the request and response boundary,
then verify it against real compressed and uncompressed payloads.

**Acceptance:**

- Documentation distinguishes request negotiation, response decompression, and
  application-supplied `Content-Encoding`.
- Wire tests cover gzip response decoding, uncompressed fallback, empty bodies,
  errors, JSON, `ResponseEntity`, and streaming ownership.
- Observer and exchange-log byte fields state whether they represent encoded,
  decoded, advertised, or unknown sizes.
- Customizers cannot create duplicate or contradictory transport-owned
  compression headers silently.
- Compression remains opt-in and introduces no body aggregation.

## 4. Pool Saturation and Acquisition Diagnostics

Exercise a one-connection pool under queued demand, cancellation, timeout, and
shutdown. Improve attribution only where the underlying failure can be proven.

**Acceptance:**

- Real transport tests cover pending acquire success, pending acquire timeout,
  cancellation while queued, connection eviction, and factory destruction.
- No cancelled waiter consumes a later connection or leaves pending metrics
  nonzero.
- Existing `ErrorCategory` values remain behaviorally compatible.
- If an additive failure-stage signal is introduced, it is bounded, optional,
  documented, and identical across lifecycle, observer, exchange-log, health,
  and support snapshots where those surfaces can know it.
- Pool metrics and troubleshooting guidance identify saturation without
  exposing remote addresses by default.

## 5. Timeout Phase and Terminal-State Parity

Audit connect, pool-acquire, request-write, response-header, response-body,
method-level, and Resilience4j timeout behavior. Do not infer a phase from a
generic timeout when the runtime cannot prove it.

**Acceptance:**

- A real fixture covers timeout before connection, before headers, during a
  unary body, during a stream, and while waiting for the pool.
- Status, response headers, attempt count, duration, final request metadata,
  error category, and cancellation state agree across all reporting surfaces.
- `Mono<ResponseEntity<Flux<DataBuffer>>>` keeps envelope timing distinct from
  inner-stream consumption timing.
- Timeout precedence and the `0 = disabled` contract remain unchanged.
- Mock helpers can assert the stable public timeout semantics without claiming
  to emulate network timing.

## 6. Streaming Backpressure and Upload Ownership

Extend the V21 ownership suite from response cleanup to bidirectional
backpressure and repeatability boundaries.

**Acceptance:**

- Publisher request bodies are never subscribed before the returned client
  publisher is subscribed.
- Demand, cancellation, retry, redirect, auth signing, and serialization do not
  cause hidden duplicate subscriptions.
- Direct `Flux<DataBuffer>` and streaming envelopes release discarded buffers
  exactly once and preserve caller ownership after handoff.
- Repeatable and non-repeatable request bodies have one documented decision
  matrix shared by runtime validation, strict modes, diagnostics, and mocks.
- No feature buffers an unbounded publisher merely to make retry, redirect,
  signing, logging, or metrics convenient.

## 7. OAuth2 Refresh and Token-Service Reliability

Re-audit the client-credentials provider as a concurrent state machine rather
than only a token parser.

**Acceptance:**

- Concurrent callers share one refresh and all receive the same valid token.
- Cancellation of one waiter does not cancel a refresh still needed by other
  callers or poison the cache.
- Expiry leeway, missing expiry, 401 invalidation, repeated 401, refresh
  failure, and recovery are deterministic.
- Token endpoint 2xx/4xx/5xx, malformed, empty, oversized, and encoded error
  bodies preserve safe status/header diagnostics and never expose credentials.
- Custom WebClient status handlers and configured codecs retain their documented
  ownership.
- Lifecycle and observer output identifies the logical downstream client
  without recording token values or client credentials.

## 8. Failure Attribution Contract

Review the public error taxonomy against the real failures collected in
Priorities 2-7. Prefer preserving existing categories and adding evidence over
renaming established signals.

**Acceptance:**

- DNS, connect, TLS, response timeout, cancellation, decode, auth, resilience,
  HTTP 4xx/429/5xx, and unknown failures have real or faithful fixture coverage.
- Cause-chain traversal is bounded and does not hide the most actionable known
  category.
- Retry exhaustion preserves the terminal cause and attempt count without
  falsely implying that every subscription reached the network.
- Any additive public enum, field, or accessor is included in japicmp and has a
  compatibility fixture.
- Error docs and test-helper assertions use the same published names.

## 9. Diagnostics Schema V1 Evolution

Keep schema v1 additive, bounded, and sanitized while incorporating only
transport facts that can be reported accurately.

**Acceptance:**

- Provider, collection-backed snapshot, JSON, Markdown, Actuator, and native
  output remain schema-equivalent.
- A source-controlled fixture and schema test reject removal, rename, type
  change, secret-bearing fields, and unbounded cardinality.
- Map and rendered forms enforce the same client, endpoint, field, and UTF-8
  byte limits.
- Unknown, unavailable, disabled, false, and zero retain distinct meanings.
- New fields are nullable/additive in schema v1 or explicitly deferred to a
  future schema version.

## 10. Mock and Consumer Parity

Add helper coverage only for behavior that a mock can reproduce honestly, and
use assembled real-server consumers for transport-owned behavior.

**Acceptance:**

- Mock auth, retries, lifecycle, observers, exchange logging, inherited
  generics, repeated headers, and final request metadata stay aligned with the
  production handler.
- HTTP protocol negotiation, compression wire bytes, TLS, pool timing, and
  connection reuse are tested in assembled consumers rather than faked by the
  in-memory `ExchangeFunction`.
- Constructor-injected custom loggers and application JSON codecs remain
  injectable in the isolated helper context.
- Published-`3.1.0` and current-reactor consumers run from separate clean
  repositories and cannot see reactor artifacts accidentally.

## 11. Dependency, AOT, and Native Matrix

Revalidate the minimum Boot 4 row and the forward Boot 4 row after transport or
diagnostic changes. Keep Java 21 as the compilation baseline and GraalVM 25 as
native evidence.

**Acceptance:**

- Full reactor and assembled consumers pass on the supported Boot matrix.
- Framework, Reactor Netty, Netty, Jackson, Micrometer, OTel, and Resilience4j
  versions are captured as target-only evidence.
- Optional integrations back off cleanly when APIs, registries, exporters, or
  Actuator are absent.
- AOT discovers inherited clients and every runtime-reflected annotation/type
  without deprecated Framework 7 hint categories.
- The native executable covers at least one new V22 transport or diagnostics
  contract and records clean immutable provenance.

## 12. Benchmark and Allocation Re-Audit

Compare the current line with published `3.1.0` only after behavior is stable.
Use the benchmark harness to detect regressions, not to justify speculative
optimization.

**Acceptance:**

- Smoke validates benchmark discovery and classification.
- Release-quality current and published-baseline runs use equivalent stacks,
  clean commits, and isolated Central provenance.
- Existing success, JSON, `ResponseEntity`, error, diagnostics, lifecycle,
  observer, and argument-expansion rows remain comparable.
- Add a scenario only when it measures a V22 path with a fair baseline.
- Investigate material movement before optimizing; document accepted noise.
- Promote a versioned report only if release notes make a numerical claim.

## 13. Documentation and Operations Consolidation

Update operational guidance from the new real-wire evidence and remove stale
current-line instructions without rewriting historical release records.

**Acceptance:**

- Protocol, compression, pool saturation, timeout phase, streaming ownership,
  OAuth2 refresh, and failure-attribution troubleshooting are actionable.
- Support-bundle recipes capture the relevant sanitized evidence for each
  incident type.
- All configuration examples are metadata-validated and use reserved hosts or
  explicit placeholders.
- Public snippets remain on released `3.1.0` until another release resolves
  from Central.
- Generated docs, local Markdown links, and release-note evidence guards pass.

## 14. V22 Release Go/No-Go

Select patch versus minor from the delivered public contract, not from the
development version. Assemble one clean release record and publish only after
all companion artifacts are independently consumable.

**Acceptance:**

- The chosen version matches the actual API/configuration/behavior scope.
- Full reactor, strict root/module API checks, packaging, consumers, optional
  integrations, real transport tests, AOT/native, metadata, docs, and baseline
  provenance pass from one immutable candidate.
- Benchmark evidence is promoted or explicitly deferred according to claims.
- A go decision verifies Maven Central resolution, artifact checksums, tag,
  changelog date, and public snippets for the same commit/version.
- A no-go decision publishes nothing and records every blocker plus a
  reproduction command.
- The next snapshot and API/benchmark baseline move only after starter,
  test-helper, OTel, parent POM, sources, and Javadocs resolve publicly.

## Exit Criteria

V22 is complete when wire-level protocol, compression, pool, timeout, streaming,
and OAuth2 concurrency contracts are backed by real evidence; diagnostics and
test helpers report only what they can know; `3.1.0` remains a proven immutable
baseline; and Priority 14 records an evidence-backed release go or no-go.
