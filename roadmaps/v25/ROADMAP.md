# Reactive HTTP Client - Roadmap V25

> **Status:** completed - released as `3.5.0` on 2026-08-16
> **Theme:** request-boundary correctness and recoverable execution after the
> `3.4.0` composition release
> **Delivered release:** `3.5.0`
> **Post-release development line:** `3.6.0-SNAPSHOT`
> **Published/API baseline:** `3.5.0`

## Completion Record

V25 shipped as `3.5.0` from immutable tag `v3.5.0` at commit
`b09bc2858801e40ae9d3560c9360eb2471d1b6fb`. The parent, starter, test-helper,
and OTel release bundle and the assembled Boot 4 consumer were verified from
Maven Central. Public coordinates and API, consumer, and benchmark baselines
moved to `3.5.0`; reactor-only development moved to `3.6.0-SNAPSHOT`. V25 made
no promoted numerical benchmark claim.

## Starting State

V24 shipped `3.4.0` and moved the reactor to `3.5.0-SNAPSHOT`. The published
parent, starter, test-helper, and OTel bundle plus an assembled Boot 4 consumer
have been verified from Maven Central. Public examples and API, consumer, and
benchmark baselines now use `3.4.0`.

The project has strong real-wire evidence for HTTP/1.1, H2/H2C, compression,
bounded pools, timeout phases, streaming ownership, OAuth2 isolation, proxy and
mTLS, replay composition, and HTTP/2 connection retirement. V24 also closed the
declarative response-type grammar and made runtime, diagnostics, AOT, and mocks
use the same decision.

The next gaps are narrower and mostly on the request side:

- Parameter parsing validates many individual annotations, but there is no one
  complete request-parameter grammar. A parameter can still carry conflicting
  roles, ignored parameters are not clearly governed, duplicate names need an
  explicit policy, and unsupported multipart file types fail at invocation
  rather than startup.
- URI encoding is documented and covered at the constructed `ClientRequest`
  level, but base-path joining, literal query templates, Unicode/reserved values,
  and authority containment are not frozen across real HTTP/1.1, H2, and proxy
  request targets.
- Multipart encoding is primarily verified by writing a body inserter into a
  mock request. Boundary framing, part headers, repeated fields, cancellation,
  and resource replay still need real-server evidence.
- Bodiless methods and common envelopes are covered, but there is no compact
  final-status/framing matrix for informational responses, `HEAD`, `204`, `205`,
  `304`, malformed framing, and connection reuse on both protocol generations.
- `ReactiveClientInvocationHandler` still carries separate stateful/stateless and
  `Mono`/`Flux` terminal paths. V24 proved their outcomes, but future fixes remain
  exposed to duplicated attempt cleanup and terminal-reporting logic.
- Pool tests cover saturation, cancellation, eviction, shutdown, and GOAWAY.
  Recovery after a peer closes an idle keep-alive connection, sends
  `Connection: close`, resets a reused socket, or disappears between calls is
  not yet one explicit starter contract.

V25 should close those contracts without adding another transport, annotation
family, or configurable state machine. Internal extraction is justified only
where characterization tests prove that it removes duplicated invariants while
preserving the diagnostics-disabled fast path.

## Release Direction

| Delivered V25 scope | Release direction |
|---|---|
| Internal correctness, tests, and documentation only | `3.4.x` |
| Backward-compatible public validation, diagnostics, or test-helper addition | `3.5.0` |
| Binary/source-incompatible API change or diagnostics schema break | Defer to a future major |

Keep the reactor on `3.5.0-SNAPSHOT` and all public consumer examples on
published `3.4.0` until release preparation selects a final version.

## Goals

1. Define every accepted request-parameter role and reject ambiguous declarations
   before proxy creation or request-body subscription.
2. Freeze URI-template expansion, encoding, base-path joining, and authority
   containment at the real request-target boundary.
3. Prove HTTP final-status, bodiless, multipart, and framing behavior without
   losing pooled connections or caller-owned resource semantics.
4. Reduce duplicated per-subscription attempt and terminal-state logic without
   changing established retry, redirect, auth replay, or observability semantics.
5. Make stale pooled-connection recovery deterministic and distinguish recovery
   from implicit request replay.
6. Keep lifecycle, observer, exchange-log, metrics, health, OTel, diagnostics,
   mock, consumer, AOT, and native evidence aligned.
7. Preserve strict API and benchmark discipline against published `3.4.0`.

## Non-Goals

- Do not add HTTP/3, WebSocket, gRPC, server-side HTTP, service discovery, or a
  second transport stack.
- Do not add dynamic per-invocation base URLs or allow endpoint templates to
  select a different authority.
- Do not add a new annotation merely to represent a case already expressible by
  `@PathVar`, `@QueryParam`, `@HeaderParam`, `@Body`, or multipart annotations.
- Do not add arbitrary multipart publisher/stream part shapes without a separate
  ownership and repeatability proposal.
- Do not buffer caller-owned streams or multipart resources to simplify retries,
  redirects, auth replay, signing, logging, or tests.
- Do not add automatic retries for stale connections, resets, malformed
  responses, or ambiguous writes outside the existing opt-in resilience policy.
- Do not expose raw URLs, headers, body data, credentials, exception messages,
  or remote addresses in low-cardinality telemetry or configured-client
  diagnostics.
- Do not break diagnostics schema v1 or change established `attemptCount`,
  `ErrorCategory`, or `HttpClientFailureStage` semantics silently.
- Do not promote benchmark numbers from smoke runs, dirty commits, or a local
  unpublished baseline.

---

## 1. Post-`3.4.0` Baseline and V25 Scope Integrity

Keep the current reactor, published baseline, roadmap archive, and generated
release evidence on one consistent timeline.

**Acceptance:**

- Parent, starter, test-helper, and OTel `3.4.0` artifacts resolve from fresh
  Central-only repositories with remote markers and checksums.
- Root and module-scoped japicmp compare the current snapshot against published
  `3.4.0`; same-version, mixed-version, and locally contaminated baselines fail.
- Public dependency snippets stay on `3.4.0`; reactor-only consumer and native
  fixtures stay on `3.5.0-SNAPSHOT`.
- `roadmaps/README.md` records V25 as the only active draft without rewriting
  completed V1-V24 evidence.
- The generated readiness manifest reports snapshot development, published
  baseline, deferred candidate version, and pending manual evidence accurately.

## 2. Declarative Request-Parameter Grammar

Give request declarations the same startup certainty V24 added to response
declarations.

**Acceptance:**

- Define the supported role for every endpoint parameter: path, query, header,
  header map, idempotency key, body, form field, or form file.
- Detect conflicting annotations on one parameter and ambiguous duplicate names
  case-insensitively where the runtime cannot preserve both values predictably.
- Resolve inherited and multi-level generic parameter types against the concrete
  client before body, multipart, repeatability, and strict-signing decisions.
- Validate supported `@FormFile` types at startup rather than on the first call.
- A missing/null required path variable fails before auth resolution, request-body
  subscription, or transport dispatch and names the concrete method and binding.
- Inventory currently ignored unannotated parameters before changing behavior.
  Preserve compatibility with a warning or opt-in strict check if unconditional
  rejection would break valid published clients.
- Startup, effective-contract export, diagnostics, AOT processing, and
  `MockReactiveHttpClient` use the same starter-owned decision while replacement
  metadata caches and foreign `FactoryBean` clients retain their documented
  boundaries.

## 3. URI Template, Request-Target, and Authority Contract

Prove exactly which bytes and authority the declarative URI model sends.

**Acceptance:**

- Real HTTP/1.1 and H2 fixtures cover base URLs with path prefixes, annotation and
  `@ApiRef` paths, leading/trailing slashes, query-template variables, literal
  query entries, repeated values, empty values, and omitted null values.
- Raw Unicode and reserved characters in path/query arguments follow one tested
  percent-encoding contract; pre-encoded input behavior is explicit and
  consistent with the documentation.
- Default, method, and auth-added query parameters preserve the documented
  precedence and deterministic repeated-value order.
- Endpoint templates containing an authority, user-info, or fragment are either
  rejected at startup or supported by explicit compatibility and security
  evidence. They cannot silently escape the configured client authority.
- HTTP proxy, direct HTTP/1.1, TLS, H2, redirects, final-request observation, and
  mocks agree on the declarative template versus resolved request-target facts
  each layer can truthfully expose.
- URI construction uses structured URI APIs; ad hoc parsing is retained only if
  tests prove all accepted syntax and failure messages.

## 4. HTTP Method, Final-Status, and Framing Semantics

Close protocol edge cases around responses that intentionally or illegally carry
no body.

**Acceptance:**

- Real HTTP/1.1 and H2 coverage includes `HEAD`, `OPTIONS`, final `204`, `205`,
  `304`, and informational responses followed by a final response where the
  transport exposes them.
- `Mono<Void>`, `Mono<T>`, `Mono<ResponseEntity<Void>>`, and
  `Mono<ResponseEntity<T>>` have deterministic empty-body behavior while
  preserving final status and headers on envelope paths.
- Final 3xx remains visible when redirect following is disabled; only final 4xx
  and 5xx responses enter error mapping.
- Illegal or unexpected bodies are drained only when the protocol exposes bytes
  safely; malformed `Content-Length`/transfer framing quarantines the connection
  rather than risking the next request.
- Final status, response headers, error category, failure stage, and connection
  reuse evidence agree across lifecycle, observer, exchange logging, metrics,
  and OTel.
- A pooled POST-then-PUT/HEAD sequence cannot be decoded as a synthetic
  `GET /bad-request HTTP/1.0` because of leftover request or response bytes.

## 5. Multipart and Form-Data Wire Ownership

Move multipart confidence from mock body insertion to a real peer.

**Acceptance:**

- A real server parses mixed text/file parts from `byte[]`, `FileAttachment`, and
  reopenable `Resource` inputs and verifies boundary, part order, repeated
  fields, content disposition, filename, content type, and exact bytes.
- Null scalar/file parts are omitted and collections/arrays retain caller order.
- HTTP/1.1 framing and H2 data delivery are recorded without assuming one fixed
  aggregate `Content-Length` where the writer legitimately streams.
- Cancellation before write, cancellation during a resource part, peer reset,
  timeout, retry, redirect, and auth replay close each opened resource once and
  do not subscribe/read extra parts after termination.
- Application-owned resources remain unbuffered and retain the existing replay
  warning/rejection policy. Built-in SigV4 still fails before dispatch when a
  stable multipart payload hash cannot be proven.
- Mocks assert encoded parts for test convenience but do not claim socket
  backpressure, framing, pool reuse, or transport cancellation.

## 6. Subscription and Terminal-State Invariant Consolidation

Reduce the chance of state leakage by centralizing only invariants already proved
by V23/V24 tests.

**Acceptance:**

- Characterization tests freeze the current stateless/stateful, `Mono`/`Flux`,
  streaming-envelope, retry, redirect, auth-replay, cancellation, and timeout
  outcomes before internal extraction.
- One subscription-local state owner controls generated idempotency key, prepared
  arguments, active subscription attempt, final request observation, response
  status/headers, terminal error, timing, and one-time reporting.
- Attempt cleanup is guarded by the attempt that installed it; a completed prior
  attempt cannot clear final-attempt dispatch or response evidence.
- Lifecycle, observer, exchange-log, Micrometer, health, and OTel terminal
  reporting derives from one immutable terminal snapshot where practical.
- Any extraction remains package-private and removes real duplication. Do not
  rewrite request construction or add a configurable state machine.
- The diagnostics-disabled unary path retains its allocation-sensitive fast path
  and does not allocate terminal-reporting state it cannot use.

## 7. Stale Pooled-Connection Recovery

Define what happens when a connection becomes unusable between otherwise valid
logical calls.

**Acceptance:**

- A bounded single-connection HTTP/1.1 fixture covers `Connection: close`, peer
  FIN after a response, idle close before reuse, reset on reuse, and close during
  response consumption.
- An independent later call obtains replacement capacity after a stale socket is
  removed; pending/active metrics converge and factory shutdown remains bounded.
- A failed request is not silently replayed. Explicit retry still follows method
  safety, idempotency-key, body-repeatability, and subscription-attempt rules.
- No stale channel, decoder, request URL, status, response header, or failure
  stage leaks into the replacement call.
- HTTP/2 GOAWAY behavior remains governed by the V24 retirement contract; V25
  adds only missing abrupt-close or replacement-capacity evidence.
- Operations guidance distinguishes normal idle retirement, graceful close,
  connection reset, malformed framing, pool-acquire pressure, and application
  timeout symptoms.

## 8. Terminal Diagnostics and Redaction Parity

Keep one bounded terminal truth for the new request-boundary and recovery cases.

**Acceptance:**

- Invocation validation, URI construction, serialization, auth, pool, write,
  response-header, response-body, and cancellation failures expose only stages
  proven by the final visible attempt.
- Pre-dispatch failures contain no stale URL/status/header evidence, and stale
  connection failures do not inherit metadata from the prior successful call.
- Arbitrary mapper, codec, auth, filter, multipart, and transport exception
  messages cannot enter default logs, spans, metrics, health details, or support
  snapshots without existing bounded sanitization.
- Diagnostics schema v1 remains additive, deterministic, UTF-8-size bounded, and
  free of request-scoped configured-client facts.
- Diagnostics queries do not instantiate lazy clients, auth providers,
  resilience registries/instances, multipart resources, pools, or connections.
- Support-bundle fixtures cover at least one validation failure and one stale
  connection recovery without secrets or machine-local paths.

## 9. Mock and Assembled-Consumer Parity

Keep in-process helpers honest and verify public behavior from consumer-owned
applications.

**Acceptance:**

- `MockReactiveHttpClient` applies the production request-parameter grammar and
  URI expansion rules while describing exchanges as in-process records, not wire
  dispatches.
- Mock multipart assertions expose stable part names, headers, and bytes without
  simulating transport framing or backpressure.
- Constructor-injected loggers, application JSON codecs, auth providers,
  inherited generics, custom metadata caches, and lifecycle ordering remain
  production-compatible.
- Current `3.5.0-SNAPSHOT` and published `3.4.0` consumers run from distinct fresh
  repositories and reject reactor/local-repository leakage.
- Verifiers preserve current-run Surefire and provenance evidence incrementally,
  identify the last completed stage, and never upload stale reports.

## 10. Dependency, API, AOT, and Native Evidence

Keep compatibility proof proportional to the contracts V25 changes.

**Acceptance:**

- Minimum and forward Spring Boot 4 rows pass the full Java 21 reactor and
  assembled consumer with resolved Framework, Reactor Netty, Netty, Jackson,
  Micrometer, OTel, Resilience4j, and test dependency provenance.
- Every public addition/deprecation is included in strict japicmp coverage
  against published `3.4.0`; incompatible changes are deferred.
- AOT/runtime hints cover concrete inherited request parameter types and all
  runtime-inspected parameter annotations without deprecated Framework 7 member
  categories.
- AOT validation remains limited to starter-owned factory beans and honors the
  documented replacement `MethodMetadataCache` boundary.
- GraalVM 25 builds from a clean immutable commit and executes at least one V25
  request-grammar, URI, multipart, or stale-connection contract feasible in the
  native fixture.
- Optional Actuator, Micrometer, OTel, Resilience4j, and auth integrations still
  back off independently when absent.

## 11. Targeted Benchmark and Allocation Re-Audit

Measure only hot paths changed by V25 and compare equivalent work.

**Acceptance:**

- Benchmark discovery and fairness guards pass before measurements.
- Request argument expansion, URI construction, default unary/JSON success,
  diagnostics-disabled execution, and terminal reporting are rerun only when
  their production paths change.
- Multipart or stale-recovery fixtures remain correctness tests unless a stable,
  equivalent benchmark scenario can be defined without measuring server setup or
  logging I/O.
- Current and published `3.4.0` reports use distinct fresh repositories with the
  same Boot, transport, codec, and optional-feature work.
- Review named latency and allocation movements without adding a normal-CI
  numeric gate or making a broad raw-`WebClient` parity claim.
- Promote a clean report only if the selected release notes make a numerical
  performance or allocation claim.

## 12. Documentation and Operations Consolidation

Make the request-side contract discoverable without expanding README into a
second manual.

**Acceptance:**

- Annotation and production guides include one concise parameter-role table and
  clearly distinguish startup validation from per-invocation null/value checks.
- URI encoding, authority containment, final-status/bodiless behavior,
  multipart ownership, and stale-connection recovery match real fixtures.
- Mock documentation separates encoded request records from real wire framing,
  backpressure, and connection reuse.
- Current operations and support-bundle guides remain the canonical incident
  entry points; historical migration, API, benchmark, and release evidence stays
  immutable and clearly labeled.
- Examples use sanitized `EXAMPLE_` and `.example.invalid` placeholders.
- Generated configuration metadata, example properties, local links, anchors,
  roadmap links, and public version snippets pass normal tests.

## 13. V25 Release Go/No-Go

Select the version from delivered scope and publish only reproducible evidence.

**Acceptance:**

- Record whether V25 is an internal `3.4.x` correctness release or a
  backward-compatible `3.5.0` public addition.
- Full reactor, strict root/module API, packaging, current/published consumers,
  supported matrix, real transport fixtures, AOT/native, and documentation gates
  pass from one immutable commit.
- Complete parent, starter, test-helper, and OTel candidate artifacts are
  verified before publication.
- Changelog wording cites a clean promoted benchmark report or makes no numerical
  performance claim.
- On go, publish from the matching tag, verify every Maven Central artifact, then
  move public/API/consumer/benchmark baselines and open the next snapshot line.
- On no-go, publish nothing and record each blocker with its reproduction and
  retained evidence path.

---

## Suggested Execution Order

1. Published baseline integrity and request-parameter grammar.
2. URI/request-target and HTTP final-status/framing contracts.
3. Multipart real-wire ownership and stale pooled-connection recovery.
4. Subscription-state consolidation and terminal diagnostics parity.
5. Mock/consumer, dependency/API, AOT/native, and targeted benchmark evidence.
6. Documentation consolidation and release go/no-go.

## Completion Rule

V25 is complete only when request declarations fail before side effects, wire
claims are proved by real protocol fixtures, and internal state consolidation is
behavior-neutral under characterization and allocation evidence. Mock requests
cannot prove framing, backpressure, pool reuse, stale-connection recovery, or
resource closure. A failed reused connection must not be described as safely
retried unless the existing resilience, idempotency, and repeatability contracts
prove an additional request was allowed.
