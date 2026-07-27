# Reactive HTTP Client - Roadmap V24

> **Status:** draft
> **Theme:** composition hardening and contract closure after the `3.3.0`
> release
> **Development line:** `3.4.0-SNAPSHOT`
> **Published/API baseline:** `3.3.0`
> **Working release direction:** `3.4.0`, subject to delivered public scope and
> release evidence

## Retrospective Audit

The project has moved through four broad stages:

1. V1-V3 established the declarative client, configuration, security, network,
   auth, resilience, observability, and test-helper foundations.
2. V4-V11 tightened extension points, inherited contracts, startup validation,
   response ownership, diagnostics, and API compatibility.
3. V12-V18 added repeatable benchmark evidence, operational support artifacts,
   strict production validation, metadata governance, and release automation.
4. V19-V23 migrated the main line to Spring Boot 4/Jackson 3 and added real-wire
   evidence for HTTP/1.1, H2/H2C, compression, pool pressure, timeout phases,
   streaming ownership, OAuth2 isolation, and failure attribution.

That foundation is broad. The next risk is not a missing transport stack or a
new annotation family; it is the behavior where existing features compose.
The audit found these concrete follow-ups:

- Before this audit, `roadmaps/README.md` stopped at V18 and several old
  roadmap/checklist status markers no longer described the releases that
  actually shipped. Historical
  roadmap acceptance boxes and execution checklists also use different
  completion conventions.
- Declarative parsing validates the outer `Mono` or `Flux`, but it does not yet
  define and enforce a complete return-type grammar. Unsupported nested reactive
  shapes can therefore survive startup and fail during response decoding.
- Resilience operators are covered independently, while their combined permit,
  retry, circuit-counting, timeout, and terminal-reporting semantics lack one
  deterministic composition contract.
- Retry, redirect, and OAuth2 `401` replay are individually covered, but a small
  pairwise composition matrix is still needed to prove dispatch counts,
  idempotency-key lifetime, body subscriptions, and final-attempt diagnostics.
- Proxy configuration has binding and failure-attribution coverage, but no
  successful real HTTP proxy/CONNECT contract. The public `HTTPS` proxy type is
  currently mapped to Reactor Netty's HTTP proxy mode and needs precise semantics.
- TLS has trusted and untrusted self-signed-peer coverage, but the advertised
  mTLS path lacks a successful client-certificate wire fixture.
- HTTP/2 covers ALPN, H2C, cancellation, stream reset, capacity pressure, and
  shutdown, but not peer `GOAWAY`, connection retirement, and subsequent dispatch
  on replacement capacity.

Intentional boundaries remain valid and should not be reopened without new
evidence: no HTTP/3 or gRPC abstraction, no buffering of caller-owned streams,
no default retries/redirects/compression/diagnostics, no request-scoped facts in
configured-client diagnostics schema v1, and no numeric performance claims from
smoke benchmarks.

## Goals

1. Make every accepted declarative method shape startup-valid and every rejected
   shape fail with a deterministic method-specific message.
2. Prove retry, rate limiter, circuit breaker, bulkhead, timeout, redirect, and
   auth replay composition without making operator ordering configurable.
3. Close the successful proxy and mTLS wire-evidence gaps and make proxy type
   wording match the connector that is actually configured.
4. Prove graceful HTTP/2 connection retirement without cross-stream corruption,
   lost demand, or stale pool diagnostics.
5. Keep lifecycle, observer, exchange-log, metrics, diagnostics, mock, and
   consumer output aligned with final-attempt facts.
6. Make the roadmap archive and release history mechanically navigable and
   unambiguous.
7. Preserve strict API, AOT/native, dependency, artifact-provenance, and
   benchmark discipline against published `3.3.0`.

## Non-Goals

- Do not add HTTP/3, WebSocket, gRPC, server-side HTTP, or another client
  transport.
- Do not add a configurable resilience-operator ordering surface in V24.
- Do not add a diagnostics schema v2 or copy request-scoped transport data into
  configured-client snapshots.
- Do not add new nested reactive response envelopes merely to avoid rejecting an
  ambiguous declaration. Direct `Flux<T>` and
  `Mono<ResponseEntity<Flux<DataBuffer>>>` remain the supported streaming paths
  unless implementation evidence justifies a separate public proposal.
- Do not buffer streaming bodies to make retry, redirect, auth replay, signing,
  logging, or tests easier.
- Do not silently reinterpret `HTTPS` proxy configuration. Clarify, deprecate,
  or migrate it with compatibility evidence.
- Do not promote benchmark output or publish numerical claims from a dirty tree,
  a smoke run, or a non-published baseline.

---

## 1. Post-`3.3.0` Baseline and Archive Integrity

Keep the published release, development reactor, roadmap archive, and generated
release evidence on one consistent timeline.

**Acceptance:**

- Parent, starter, test-helper, and OTel `3.3.0` artifacts resolve from fresh
  Central-only repositories with remote markers and checksums.
- Root and module-scoped japicmp compare `3.4.0-SNAPSHOT` against published
  `3.3.0`; self-resolution and mixed versions fail.
- Public consumer snippets stay on `3.3.0`; reactor-only fixtures stay on
  `3.4.0-SNAPSHOT`.
- `roadmaps/README.md` links V1 through V24 and records completed/no-go/draft
  status consistently.
- Historical unchecked alternatives are labeled as alternatives, deferred work,
  or superseded evidence rather than appearing as forgotten release tasks.
- A lightweight documentation test rejects a missing roadmap directory, sibling
  roadmap/checklist link, or completed status that conflicts with its completion
  record.

## 2. Declarative Return-Type Grammar

Define the response shapes the proxy can execute correctly before any client is
created or request is sent.

**Acceptance:**

- Startup validation explicitly covers raw and parameterized `Mono`/`Flux`,
  `Void`, `ResponseEntity<T>`, direct `Flux<T>`, `Flux<DataBuffer>`, and
  `Mono<ResponseEntity<Flux<DataBuffer>>>`.
- Nested publishers and unsupported envelopes such as
  `Mono<ResponseEntity<Flux<Dto>>>`, `Mono<Mono<T>>`, or `Flux<Flux<T>>` fail at
  startup unless a wire-correct implementation is deliberately added.
- Inherited and multi-level generic methods are validated after resolving the
  concrete child binding.
- Startup validation, effective-contract export, diagnostics, AOT reflection,
  and `MockReactiveHttpClient` use the same supported-shape decision.
- Error messages name the concrete client, declaring interface, full Java method
  signature, resolved response type, and supported alternative.
- Existing valid unary, typed `Flux<T>`, and raw streaming ownership tests remain
  unchanged.

## 3. Resilience Operator Composition Contract

Freeze the semantics of the existing operator chain rather than exposing more
configuration.

**Acceptance:**

- A deterministic fixture applies retry, rate limiter, circuit breaker, bulkhead,
  per-attempt timeout, and logical-call timeout together.
- Tests prove which operators acquire once per logical subscription and which
  observe each retry subscription.
- Retry exhaustion records the expected circuit-breaker outcomes, rate-limiter
  permissions, bulkhead occupancy, subscription-attempt count, and one terminal
  observer/log/lifecycle result.
- Cancellation and logical-budget expiry release every permit exactly once and
  leave no delayed retry subscription running.
- Missing or no-op optional operators remain reported as unavailable/disabled,
  not as active configured instances.
- Startup diagnostics and resilience documentation describe the actual wrapper
  semantics, not only the order in which transformation methods are called.

## 4. Retry, Redirect, and Auth Replay Composition

Use a bounded pairwise matrix to prove replay behavior without constructing an
unmaintainable all-combinations suite.

**Acceptance:**

- Cover retry plus `307`/`308`, retry plus OAuth2 `401` refresh, and redirect plus
  OAuth2 refresh on real local servers.
- Distinguish one logical subscription, resilience subscriptions, hidden auth
  replay, redirect dispatches, and actual request-body subscriptions.
- A method-generated idempotency key is fresh per outer subscription and stable
  across every replay belonging to that subscription.
- Repeatable bodies reproduce identical bytes; application-owned and
  non-repeatable bodies retain the documented warning/rejection behavior without
  hidden buffering.
- Authorization and other sensitive headers follow the documented same-authority
  and cross-authority redirect policy.
- Terminal status, final URL metadata, failure stage, attempt count, and
  cancellation describe the final visible outcome without prior-dispatch leakage.

## 5. Real Proxy and mTLS Wire Contracts

Replace configuration-only confidence with successful local wire evidence for
the network features already advertised publicly.

**Acceptance:**

- A local forward proxy proves successful HTTP absolute-form forwarding and HTTPS
  `CONNECT` tunneling through the starter proxy.
- Proxy authentication succeeds without exposing credentials in errors, logs,
  diagnostics, support snapshots, or test output.
- `non-proxy-hosts` proves both bypass and proxy paths with the documented Java
  regular-expression semantics.
- SOCKS support is either covered by a deterministic local fixture or explicitly
  narrowed in public support wording.
- The `HTTP` and `HTTPS` proxy enum values are documented according to actual
  Reactor Netty behavior; any deprecation is additive and compatibility-covered.
- A local mTLS server accepts a configured client certificate, rejects a missing
  or untrusted client identity, and preserves TLS failure attribution.
- HTTP/1.1 and TLS H2 are both covered where the local fixture supports them.

## 6. HTTP/2 GOAWAY and Connection Retirement

Prove that a peer can retire a connection without corrupting active streams or
stranding new work.

**Acceptance:**

- A real H2/H2C fixture sends `GOAWAY` while at least one stream is active.
- Streams accepted before the peer's last-stream identifier complete or fail
  according to protocol evidence; new calls use replacement capacity.
- Retry is not implied for a non-repeatable request body whose stream may have
  been processed by the peer.
- Pool active/pending stream gauges converge after retirement and never retain
  the removed connection's capacity.
- Cancellation, reset, compression, and streaming response ownership remain
  isolated to the affected stream.
- Factory shutdown during retirement terminates active and pending work within
  the existing bounded disposal policy.

## 7. Terminal Diagnostics Under Feature Composition

Keep one terminal truth across the composition fixtures added in V24.

**Acceptance:**

- Lifecycle hooks, observer events, exchange logs, Micrometer, OTel, and health
  agree on client, method, status, category, failure stage, duration, and
  subscription-attempt count where each contract exposes them.
- Prior-attempt URL, headers, status, and dispatch evidence cannot leak into a
  terminal pre-dispatch failure.
- Arbitrary custom-filter and auth-provider exceptions do not cause the default
  logger or support output to expose credentials or unbounded payload text.
- Diagnostics schema v1 remains additive, bounded, deterministic, and free of
  request-scoped facts.
- No diagnostics query instantiates a lazy client, auth provider, resilience
  instance, proxy connection, or network resource.

## 8. Mock and Assembled-Consumer Parity

Expose only stable starter-owned contracts in mocks and retain real transport
ownership in assembled consumers.

**Acceptance:**

- Mock validation rejects the same unsupported method shapes as production.
- Mock replay assertions distinguish subscription attempts from configured
  response sequencing without claiming socket dispatch behavior.
- Constructor-injected loggers, application codecs, auth providers, inherited
  generic clients, and lifecycle ordering remain production-compatible.
- Current `3.4.0-SNAPSHOT` and published `3.3.0` consumers run from separate
  repositories and reject reactor leakage.
- Failure evidence is copied incrementally and identifies the last completed
  verifier stage without reusing stale Surefire reports.

## 9. Dependency, API, AOT, and Native Evidence

Keep V24 compatibility proof proportional to the contracts it changes.

**Acceptance:**

- Minimum and forward Spring Boot 4 rows pass the full reactor and assembled
  consumer under Java 21 with resolved dependency provenance.
- Every public addition or deprecation is included in strict japicmp coverage;
  incompatible changes are deferred.
- AOT/runtime hints cover inherited generic validation and any newly inspected
  return-type metadata without deprecated Framework 7 categories.
- GraalVM 25 builds from a clean immutable commit and executes at least one V24
  validation or network-composition contract.
- Optional Actuator, Micrometer, OTel, Resilience4j, and auth integrations still
  back off independently when absent.

## 10. Benchmark and Allocation Re-Audit

Measure only paths changed by V24 and keep transport work equivalent.

**Acceptance:**

- Benchmark discovery and fairness guards pass before any measurement.
- The default unary success, JSON, `ResponseEntity`, resilience-composition, and
  diagnostics-disabled paths are re-audited only if their production code changes.
- Current and published `3.3.0` reports use distinct fresh repositories and
  equivalent Boot/transport work.
- Regressions are reviewed by named scenario; no normal CI hard gate or broad
  raw-WebClient parity claim is added.
- A promoted report is required only if `3.4.0` release notes make a numerical
  performance or allocation claim.

## 11. Documentation and Operations Consolidation

Make the mature feature set easier to use without expanding README into a full
manual.

**Acceptance:**

- Proxy type, mTLS, H2 retirement, resilience composition, replay, and return-type
  guidance match the tested contracts.
- The production checklist includes one concise compatibility table for supported
  return shapes and one replay-safety decision path.
- Historical migration, API-report, benchmark-report, and release-decision docs
  are clearly labeled as immutable evidence rather than current instructions.
- Current operations and support-bundle guides remain the canonical incident
  entry points and use sanitized placeholder values.
- Generated configuration metadata, example properties, anchors, local links,
  roadmap links, and public version snippets pass normal tests.

## 12. V24 Release Go/No-Go

Select the version from delivered scope and publish only reproducible evidence.

**Acceptance:**

- The final decision records whether V24 is documentation/correctness-only or a
  backward-compatible `3.4.0` public addition.
- Full reactor, strict root and module API compatibility, packaging, current and
  published consumers, supported matrix, transport fixtures, AOT/native, and
  documentation checks pass from one immutable commit.
- Complete parent, starter, test-helper, and OTel candidate artifacts are verified
  before publication.
- Changelog wording cites a clean promoted report or makes no numerical
  performance claim.
- On go, publish from the matching tag, verify every Central artifact, then move
  public/API/consumer/benchmark baselines and open the next snapshot line.
- On no-go, publish nothing and record each blocker with its reproduction and
  retained evidence path.

---

## Suggested Execution Order

1. Baseline/archive integrity and declarative return-type validation.
2. Resilience composition and replay composition.
3. Successful proxy/mTLS wire contracts and HTTP/2 retirement.
4. Diagnostics, mock, and assembled-consumer parity.
5. Dependency/API/AOT/native evidence and targeted benchmarks.
6. Documentation consolidation and release go/no-go.

## Completion Rule

V24 is complete only when each changed behavior has evidence at the layer that
owns it. Annotation and configuration unit tests are insufficient for proxy,
mTLS, HTTP/2 retirement, replay dispatch, pool capacity, or request-body
ownership claims. Synthetic transport evidence must not be described as a real
wire contract, and historical roadmap cleanup must not rewrite immutable release
evidence.
