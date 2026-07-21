# Reactive HTTP Client - Roadmap V23

> **Status:** draft
> **Theme:** attempt-scoped correctness and bounded operations after the
> `3.2.0` wire-diagnostics release
> **Development line:** `3.3.0-SNAPSHOT`
> **Published/API baseline:** `3.2.0`

## Current State

V22 published `3.2.0` and moved development to `3.3.0-SNAPSHOT`. Public
consumer examples, strict API compatibility, assembled consumers, and benchmark
baselines now resolve the complete Maven Central `3.2.0` bundle. The normal
reactor remains Spring Boot 4, Spring Framework 7, Jackson 3, Java 21, and the
GraalVM 25 native evidence lane.

The starter now has real transport evidence for HTTP/1.1, TLS H2, H2C,
compression, bounded pools, timeout phases, streaming ownership, OAuth2 refresh,
failure-stage reporting, diagnostics schema v1, mock parity, and published
consumer provenance. The next work should tighten places where one logical call
can produce several subscriptions or wire dispatches, and where transport policy
is still difficult to diagnose without exposing high-cardinality or sensitive
data.

V23 should prefer contract alignment and bounded evidence over another broad
feature layer. Add public API or configuration only when runtime behavior cannot
be made correct and observable through existing contracts.

## Release Direction

| Completed V23 scope | Release direction |
|---|---|
| Correctness, tests, documentation, and internal diagnostics alignment only | `3.2.x` |
| Backward-compatible public policy or diagnostics additions | `3.3.0` |
| Binary/source-incompatible public change or diagnostics schema break | Defer to a future major |

Keep the reactor on `3.3.0-SNAPSHOT` and public coordinates on released `3.2.0`
until final scope and evidence select a release candidate.

## Goals

1. Distinguish logical subscriptions, retry subscriptions, and actual outbound
   dispatches without changing established attempt-count semantics silently.
2. Make total timeout budgets and per-attempt timeouts compose predictably across
   retry, redirect, auth refresh, pool acquisition, and streaming envelopes.
3. Improve protocol-aware pool and pre-response failure diagnosis using bounded,
   low-cardinality metadata.
4. Preserve streaming and compression ownership while enforcing configured
   aggregate limits at the correct encoded or decoded boundary.
5. Isolate OAuth2 token-service policy and failures from the business request
   while retaining sanitized, client-correct diagnostics.
6. Keep diagnostics, lifecycle, observer, exchange-log, health, mock, consumer,
   AOT/native, and documentation contracts aligned.
7. Measure changed request paths against published `3.2.0` before making any
   performance claim.

## Non-Goals

- Do not add HTTP/3, WebSocket, server-side HTTP, or a second transport stack.
- Do not enable retries, redirects, compression, diagnostics endpoints, strict
  validation, or deadline propagation by default.
- Do not buffer streaming request or response bodies to simplify diagnostics,
  replay, signing, or timeout handling.
- Do not expose raw URLs, addresses, headers, payloads, credentials, exception
  messages, or unbounded identifiers in metrics or support snapshots.
- Do not redefine existing `ErrorCategory`, `attemptCount`, or diagnostics v1
  fields without compatibility evidence and migration wording.
- Do not publish benchmark numbers from smoke runs, dirty commits, or local
  baseline artifacts.

---

## 1. Post-`3.2.0` Baseline Integrity

Treat published `3.2.0` as the single API, consumer, and benchmark baseline.
Keep parent-POM verification independent from the current reactor GAV.

**Acceptance:**

- Root and module-scoped japicmp resolve `3.2.0` from fresh Central-only
  repositories.
- Parent, starter, test-helper, and OTel POM/binary/source/Javadoc artifacts have
  Central markers and checksums.
- Public snippets use `3.2.0`; reactor-only fixtures use `3.3.0-SNAPSHOT`.
- Same-version reactor self-resolution, stale repositories, missing attachments,
  and mixed project versions fail provenance checks.

## 2. Logical Call, Subscription, and Dispatch Semantics

Audit the state machine across cold-publisher resubscription, Resilience4j retry,
redirect following, OAuth2 `401` refresh, filters, and terminal reporting.

**Acceptance:**

- Existing `attemptCount` semantics remain documented and compatible.
- Actual HTTP dispatch evidence is reset and recorded per attempt; prior attempts
  cannot influence terminal failure attribution.
- If an additive dispatch count is needed, it is bounded and identical across
  lifecycle, observer, exchange-log, diagnostics, and test helpers where known.
- Serialization/auth/filter failures before dispatch remain distinguishable from
  network attempts without implying that a request reached the server.
- Concurrent subscriptions keep all mutable reporting state subscription-local.

## 3. End-to-End Timeout Budget

Define how an optional logical-call budget composes with existing request,
connect, pool-acquire, response, method, and Resilience4j timeouts.

**Acceptance:**

- Existing timeout properties and `0 = disabled` behavior remain unchanged.
- Any new budget is opt-in, monotonic, subscription-local, and never reset by a
  retry, redirect, or auth refresh.
- Terminal metadata identifies only timeout phases proven by the final attempt.
- Streaming envelopes distinguish outer response acquisition from caller-owned
  body consumption.
- Real-clock integration tests cover budget exhaustion before dispatch, between
  retries, while queued for the pool, and during response consumption.

## 4. Protocol-Aware Pool Capacity

Align pool diagnostics with HTTP/1.1 connection occupancy and HTTP/2 concurrent
stream capacity instead of treating every saturation signal as identical.

**Acceptance:**

- Real HTTP/1.1 and H2 tests distinguish queued connection acquisition from
  stream-capacity pressure where Reactor Netty exposes proof.
- Cancellation and timeout remove waiters without consuming later capacity.
- Metrics and diagnostics remain bounded and do not expose remote addresses.
- Unsupported distinctions report unknown rather than inferred values.
- Factory destruction waits for owned resources and leaves no pending demand.

## 5. DNS, Proxy, TLS, and Connect Failure Attribution

Extend pre-response failure evidence without classifying wrapped auth or custom
filter failures as business-request transport failures.

**Acceptance:**

- Real fixtures cover DNS resolution, proxy connection/tunnel, TLS handshake,
  connect timeout, and certificate failures where deterministic locally.
- Concrete pre-response stages do not require response URL/status evidence.
- Cause-chain inspection cannot promote nested token-service or filter failures
  into a business-request stage.
- Error category remains backward compatible; additive stage values are bounded.
- Support guidance includes sanitized reproduction evidence for every stage.

## 6. Compression and Aggregate-Limit Boundaries

Document and test where configured limits apply to encoded bytes, decoded bytes,
and application aggregation without consuming caller-owned streams.

**Acceptance:**

- Unary JSON, Problem Detail, bodiless, `ResponseEntity`, and streaming paths
  state which limit owns encoded and decoded data.
- Compressed payloads cannot bypass configured aggregate safeguards for decoded
  unary bodies.
- Streaming remains incremental and caller-owned; no hidden full-body buffering
  is introduced.
- Byte diagnostics label advertised, encoded, decoded, consumed, and unknown
  values accurately.
- Gzip truncation, corruption, cancellation, and oversized payloads release
  buffers and pooled connections exactly once.

## 7. OAuth2 Token-Service Transport Isolation

Audit token acquisition as a separate outbound dependency with explicit,
sanitized policy and terminal behavior.

**Acceptance:**

- Token-service timeout, proxy/TLS, retry, and connection ownership are explicit
  and do not silently inherit incompatible business-request assumptions.
- Single-flight refresh, waiter cancellation, invalidation, and one-time `401`
  replay remain race-safe across logical clients.
- Business-client names remain correct while token-service failure attribution
  stays distinct.
- HTTP status, safe headers, typed sanitized body decoding, and causes remain
  available without credential leakage.
- No token, Basic credential, client secret, or encoded/escaped variant appears
  in top-level messages, causes, observers, logs, or support snapshots.

## 8. Streaming Upload Wire Contract

Re-audit publisher, `DataBuffer`, resource, and application-stream bodies at the
actual wire boundary.

**Acceptance:**

- Content length versus chunked transfer is deterministic for supported body
  shapes and protocols.
- Cancellation, write timeout, retry, redirect, and auth do not introduce hidden
  duplicate subscriptions or reads.
- Non-repeatable bodies remain rejected or warned according to strict/runtime
  policy before unsafe replay.
- SigV4 signs only byte representations proven identical to outbound bytes.
- Real server tests verify partial writes, disconnects, backpressure, and buffer
  release ownership.

## 9. Diagnostics Schema V1 Compatibility

Evolve support output additively while preserving exact bounds, redaction, and
collection/provider parity.

**Acceptance:**

- Existing v1 keys and value types remain frozen by source-controlled fixtures.
- New fields are nullable/unknown when runtime proof is unavailable.
- JSON, map, Markdown, Actuator, health, and native output agree on semantics.
- UTF-8 byte, client, endpoint, and field limits apply to every rendering path.
- Custom provider/proxy behavior remains supported without instantiating lazy
  clients, auth providers, or resilience instances from diagnostics.

## 10. Mock and Assembled-Consumer Parity

Keep starter-owned tests fast while proving transport-owned behavior with real
servers and published artifacts.

**Acceptance:**

- Mock helpers cover any stable V23 timeout, dispatch, OAuth2, upload, and
  diagnostics additions without pretending to emulate wire timing.
- Current `3.3.0-SNAPSHOT` and published `3.2.0` consumers run from separate
  repositories and reject reactor leakage.
- Failure paths preserve Surefire and provenance evidence even when a later
  verifier stage fails.
- Constructor-injected custom loggers and application JSON codecs retain
  production parity.

## 11. Dependency, API, AOT, Native, and Benchmark Evidence

Keep compatibility and performance evidence proportional to changed contracts.

**Acceptance:**

- Minimum and forward Boot 4 rows record Framework, Reactor Netty, Netty,
  Jackson, Micrometer, OTel, and Resilience4j versions under Java 21.
- Public additions are covered by japicmp; incompatible changes are deferred.
- AOT/runtime hints cover any new public configuration or reflection path without
  deprecated Framework 7 categories.
- GraalVM 25 compiles and executes at least one V23 contract from a clean commit.
- Benchmark discovery/smoke pass; changed hot paths are paired with published
  `3.2.0` on equivalent stacks before optimization or public claims.

## 12. Documentation and Release Readiness

Keep operational guidance and release evidence aligned with actual runtime
ownership and the selected semantic version.

**Acceptance:**

- Protocol, pool, timeout, OAuth2, compression, streaming, and failure guidance
  uses bounded sanitized examples.
- Generated configuration metadata, examples, links, and support fixtures pass.
- Changelog performance wording cites a promoted report or makes no numerical
  claim.
- Release readiness resolves the complete published baseline and records one
  target-only manifest.
- The final go/no-go decision selects patch versus minor from delivered public
  scope, verifies one immutable commit, and moves the next baseline only after
  all companion artifacts resolve publicly.

---

## Completion Rule

V23 is complete only when every implemented contract has real ownership evidence
at the layer that controls it, strict compatibility is proven against published
`3.2.0`, and the release decision records either a verified publication or
explicit blockers. Synthetic `ClientResponse` tests alone are not sufficient for
dispatch count, timeout budget, protocol capacity, compression, streaming, or
token-service transport claims.
