# Reactive HTTP Client - Roadmap V24 Execution Checklist

Companion to [`ROADMAP.md`](ROADMAP.md). Execute priorities in order unless a
confirmed correctness or release blocker requires reordering. Check an item only
after implementation and verification evidence is recorded under that priority.
Generated evidence belongs under `target/release-evidence/` unless a promoted,
versioned artifact is explicitly required.

---

## Priority 1 - Post-`3.3.0` Baseline and Archive Integrity

### [x] 1.1 Align the active development and published-release contracts

- [x] Keep root and module development coordinates on `3.4.0-SNAPSHOT`.
- [x] Keep public consumer snippets on published `3.3.0`.
- [x] Keep API compatibility, consumer, and benchmark baselines on published
      `3.3.0`.
- [x] Keep current-reactor consumer and native fixtures on `3.4.0-SNAPSHOT`.
- [x] Reject same-version API baselines in root and module-scoped builds.

### [x] 1.2 Prove published baseline provenance

- [x] Resolve the parent POM plus starter, test-helper, and OTel POM/JAR/source/
      Javadoc artifacts from a fresh Central-only repository.
- [x] Require Maven Central remote markers and record SHA-256 values for every
      required published artifact.
- [x] Run strict root japicmp against published `3.3.0` from a fresh repository.
- [x] Run strict starter-module japicmp against published `3.3.0` from a separate
      fresh repository.
- [x] Preserve the dynamic self-comparison guard and mixed-version rejection.

### [x] 1.3 Make the roadmap archive mechanically unambiguous

- [x] Link every V1-V24 roadmap and every existing execution checklist from
      `roadmaps/README.md`.
- [x] Record completed, released, no-go, and draft states consistently.
- [x] Explain that historical roadmap acceptance boxes are planning criteria and
      that execution checklists/changelog sections are completion records.
- [x] Label the intentionally absent V2 checklist and unchecked historical
      alternatives so they do not look like active release work.
- [x] Add a normal documentation test for contiguous roadmap directories, index
      links, sibling checklist links, and roadmap/index status consistency.
- [x] Run focused documentation tests and `git diff --check`.

Evidence:

- Root and all module coordinates remain `3.4.0-SNAPSHOT`; public dependency
  snippets, `latest.published.version`, API compatibility, consumer, and
  benchmark baselines remain published `3.3.0`. Existing release-documentation
  assertions cover these version lanes.
- `scripts/verify-published-release-artifacts.sh 3.3.0` passed from an absent
  Central-only repository. Evidence under
  `target/release-evidence/published-baselines/release-artifacts-3.3.0/`
  records the parent plus all module POM, binary, source, and Javadoc artifacts,
  their remote markers, and 13 SHA-256 values. Published binary SHA-256 values
  are starter
  `1b89b793ab95cba6bbd6e8c2043f566e382d7dbba82b8546afe5fab9f0c74fbb`,
  test helper
  `ca9de14f91b06c129c61c99de140578e2b3e47aaa0d9a6a26ef95166071bf275`,
  and OTel
  `55a60a06bf7ffd6dedb6c0a3a2f47fb1a3dddc9b9b7d3f5204c248f937f3b86c`.
- Strict root japicmp passed from the fresh
  `api-root-v24-3.3.0` repository, and strict starter-module japicmp passed
  from the separate fresh `api-starter-3.3.0` repository. Both lanes passed
  published-baseline provenance verification.
- Explicit root and module validation runs with
  `api.compatibility.baseline.version=3.4.0-SNAPSHOT` failed with the expected
  last-published-release guard. Published-baseline fixtures passed while proving
  that local markers, mixed versions, missing attachments, mismatched POM
  project versions, and mismatched embedded binary versions are rejected.
- The archive index now records V1-V24 in one status table, identifies V2's
  pre-checklist convention, and defines roadmap acceptance boxes as planning
  history. Stale draft/candidate headings and the V4 unchecked administrative
  block are labeled without rewriting historical execution evidence.
- `DocumentationReleaseArtifactTest` passed with the new contiguous-directory,
  index-link, sibling-link, and status-consistency guard. `git diff --check`
  passed.

---

## Priority 2 - Declarative Return-Type Grammar

### [x] 2.1 Define one supported response-shape grammar

- [x] Inventory raw and parameterized `Mono`/`Flux`, `Void`, `ResponseEntity<T>`,
      direct `Flux<T>`, `Flux<DataBuffer>`, and
      `Mono<ResponseEntity<Flux<DataBuffer>>>` behavior.
- [x] Centralize the supported-shape decision without adding a new public
      abstraction unless compatibility requires one.
- [x] Resolve inherited and multi-level generic return types against the concrete
      client before validation.
- [x] Preserve existing valid unary, typed `Flux<T>`, and raw streaming paths.

### [x] 2.2 Reject ambiguous nested reactive envelopes at startup

- [x] Reject `Mono<ResponseEntity<Flux<Dto>>>`, `Mono<Mono<T>>`,
      `Flux<Flux<T>>`, and equivalent unresolved shapes before proxy creation.
- [x] Include concrete client, declaring interface, full method signature,
      resolved response type, and a supported alternative in each error.
- [x] Apply the same decision in startup validation, effective-contract export,
      diagnostics, AOT metadata, and `MockReactiveHttpClient`.
- [x] Add direct, inherited, generic, `@ApiRef`, AOT, mock, and compatibility tests.

Evidence:

- Added the internal `DeclarativeReturnTypeGrammar` and exposed validation through
  the existing compatibility-covered `MethodMetadataCache` surface. The grammar
  resolves each `RequestPlan` against the concrete child client, preserves raw
  outer `Mono`/`Flux` compatibility, and rejects unresolved type variables,
  nested publishers, raw response envelopes, and typed publisher envelopes other
  than `Mono<ResponseEntity<Flux<DataBuffer>>>`.
- `ReactiveHttpClientFactoryBean`, effective-contract/diagnostics export, the
  bean-factory AOT processor, runtime `ResponseEntity` handling, and
  `MockReactiveHttpClient` now share that decision. Existing annotation and URI
  validation still retain their prior error precedence.
- `DeclarativeReturnTypeGrammarTest`,
  `ReactiveHttpClientFactoryBeanDiagnosticsTest`,
  `ReactiveHttpClientAotSmokeTest`, and `MockReactiveHttpClientTest` cover direct,
  inherited, multi-level generic, `@ApiRef`, factory, diagnostic/export, AOT, and
  mock paths. Focused tests and the full starter/test-helper reactor passed.
- Strict module-scoped japicmp against the provenance-verified published `3.3.0`
  baseline passed. The report records only the additive, binary/source-compatible
  `MethodMetadataCache.validateDeclarativeReturnTypes(Class<?>, String)` method
  for this priority. `git diff --check` passed.

---

## Priority 3 - Resilience Operator Composition Contract

### [x] 3.1 Freeze the existing wrapper semantics

- [x] Build a deterministic fixture with retry, rate limiter, circuit breaker,
      bulkhead, per-attempt timeout, and logical-call timeout enabled together.
- [x] Record which operators acquire once per logical subscription and which
      observe each retry subscription.
- [x] Prove retry exhaustion produces the expected circuit outcomes, permissions,
      occupancy, attempt count, and one terminal result.
- [x] Keep operator ordering internal and non-configurable.

### [x] 3.2 Prove permit and terminal cleanup

- [x] Cover cancellation and logical-budget expiry during admission, execution,
      retry delay, and response consumption.
- [x] Verify every permit is released once and no delayed retry remains active.
- [x] Report missing/no-op optional operators as unavailable, not active.
- [x] Align startup diagnostics, lifecycle, observer, exchange logging, metrics,
      docs, and test helpers with the proven semantics.

Evidence:

- `ResilienceOperatorCompositionContractTest` composes real Resilience4j Retry,
  RateLimiter, CircuitBreaker, and Bulkhead operators with native per-attempt and
  logical-call timeouts. Three timed-out request dispatches consume one rate
  permission and one bulkhead admission, produce one failed/open circuit result,
  and report three subscription attempts through one terminal lifecycle,
  observer, exchange-log, and Micrometer result.
- The frozen transform order remains
  `retry -> rate-limiter -> circuit-breaker -> bulkhead`; startup diagnostics and
  `docs/07-resilience4j.md` also record the reverse subscription boundary as
  `logical-call-timeout -> bulkhead -> circuit-breaker -> rate-limiter -> retry -> request-attempt`.
  No public ordering configuration was added.
- Admission timeout, retry-delay timeout, execution cancellation, and response
  consumption cancellation fixtures prove that bulkhead permits converge,
  circuit outcomes follow Resilience4j Reactor semantics, and no cancelled
  admission or delayed retry subscribes the request source later. Delayed
  rate-limiter admission is now part of the cancellable reactive chain rather
  than a detached timer.
- Startup summaries distinguish configured resilience from active operators;
  per-method diagnostics report missing registries/no-op adapters as
  `unavailable`. Existing no-op and mock-helper paths remain pass-through and use
  the same production invocation-handler composition.
- Focused composition, operator, timeout-budget, and diagnostics tests passed.
  The full starter suite and the starter-plus-test-helper reactor suite passed.
  Strict starter-module japicmp passed against published `3.3.0` from the fresh
  `target/api-starter-3.3.0-resilience-composition` repository. `git diff --check`
  passed.

---

## Priority 4 - Retry, Redirect, and Auth Replay Composition

### [x] 4.1 Add a bounded pairwise real-server matrix

- [x] Cover retry plus `307`/`308`.
- [x] Cover retry plus one-time OAuth2 `401` refresh.
- [x] Cover redirect plus OAuth2 refresh.
- [x] Distinguish outer subscription, resilience subscription, hidden auth replay,
      redirect dispatch, and body subscription counts.

### [x] 4.2 Preserve replay safety and final-attempt truth

- [x] Keep generated idempotency keys fresh per outer subscription and stable
      across every replay in that subscription.
- [x] Prove repeatable bodies reproduce identical bytes without buffering
      application-owned bodies.
- [x] Preserve documented warning/rejection behavior for non-repeatable bodies.
- [x] Enforce same-authority and cross-authority sensitive-header policy.
- [x] Prevent prior dispatch URL/header/status/failure evidence from leaking into
      the terminal visible result.

Evidence:

- `RetryRedirectAuthReplayCompositionContractTest` uses production client factories
  and real Reactor Netty servers for retry plus both `307` and `308`, retry plus
  built-in OAuth2 client-credentials `401` refresh, and redirect plus OAuth2
  refresh. It separately records outer subscriptions, lifecycle retry attempts,
  token refreshes, wire paths, and cold publisher-body subscriptions.
- Two subscriptions to one cold proxy publisher generate two distinct idempotency
  keys while all four retry/redirect dispatches inside each subscription retain its
  key. Repeatable UTF-8 text produces identical bytes on every dispatch. A gated,
  application-owned `Resource` withholds each replay remainder until the real server
  receives its first chunk; all four first chunks arrive before source completion, and
  every stream is reopened and closed once with identical wire bytes. The fixture fails
  if the starter aggregates the resource before dispatch.
  Cold non-repeatable publishers are subscribed once per dispatch. Existing warning
  and strict body-signing tests retain the documented non-repeatable/application-owned
  behavior.
- Same-authority redirect plus auth refresh preserves the bearer and caller headers.
  Cross-authority redirect evidence removes `Authorization`, `Cookie`, and
  `Proxy-Authorization` while preserving the non-sensitive idempotency key.
- Success fixtures produce one terminal observer/exchange-log result with resilience
  attempt counts that exclude redirect and auth replay dispatches. A final
  pre-dispatch auth failure after a sentinel-bearing `401` auth replay and a partial
  `200` response-body timeout reports attempt 2 with no stale URL, headers, status, or
  `RESPONSE_BODY` failure stage. Cancellation while the redirected target
  dispatch is in flight reports one visible attempt, one terminal cancellation,
  no response metadata from the preceding `307`, and no extra body subscription after
  transport cancellation.
- Focused replay, redirect, OAuth2, upload ownership, idempotency, retry-warning,
  and terminal-state tests passed. The full starter suite and the
  starter-plus-test-helper reactor suite passed. Strict starter-module japicmp
  passed against the provenance-verified published `3.3.0` baseline from
  `target/api-starter-3.3.0-resilience-composition`. `git diff --check` passed.

---

## Priority 5 - Real Proxy and mTLS Wire Contracts

### [x] 5.1 Prove successful proxy behavior

- [x] Add a local proxy that proves Reactor Netty uses HTTP `CONNECT` for HTTP
      and HTTPS targets; explicitly document that absolute-form forwarding is not
      emitted.
- [x] Add successful HTTPS `CONNECT` tunneling through the configured proxy.
- [x] Cover proxy authentication without exposing credentials in any diagnostic.
- [x] Prove both proxy and bypass paths for `non-proxy-hosts` Java regexes.
- [x] Cover SOCKS4 and SOCKS5 with deterministic local tunnel fixtures.
- [x] Align `HTTP` and `HTTPS` proxy enum wording with Reactor Netty behavior and
      compatibility evidence.

### [x] 5.2 Prove configured client-certificate mTLS

- [x] Add a local server that accepts the configured trusted client identity.
- [x] Reject missing and untrusted client certificates deterministically.
- [x] Preserve bounded TLS failure attribution and secret redaction.
- [x] Cover HTTP/1.1 and TLS H2 where the fixture supports both.

Evidence:

- `ProxyAndMtlsWireContractTest` drives real starter factories through a local
  authenticated HTTP tunnel. The proxy records `CONNECT` for plaintext HTTP and
  HTTPS targets, proving the Reactor Netty transport does not use absolute-form
  forwarding. Both `HTTP` and the deprecated `HTTPS` compatibility alias use the
  same plaintext proxy hop; target TLS remains end to end inside the tunnel.
- The wire fixture verifies successful Basic proxy authentication, deterministic
  `407` rejection, and absence of usernames, passwords, and
  `Proxy-Authorization` from observer, lifecycle, and exchange-log diagnostics.
  Existing startup-summary, effective-contract, and support-snapshot contracts
  retain redacted or omitted proxy credentials.
- The same fixture proves Java-regex `non-proxy-hosts` bypass and non-match routing,
  plus successful local SOCKS4 and SOCKS5 tunnels. Configuration metadata and
  `docs/12-proxy-tls.md` now describe the actual transport semantics.
- A client-auth-required TLS server accepts only the configured trusted identity.
  The starter presents that identity over HTTP/1.1 and ALPN-negotiated H2; missing
  and untrusted key stores fail before any application request with bounded
  `TLS_HANDSHAKE` attribution across observer, lifecycle, and exchange logging,
  without store-password leakage. Focused and full starter verification passed,
  strict starter-module japicmp passed against published `3.3.0`, and
  `git diff --check` passed.

---

## Priority 6 - HTTP/2 GOAWAY and Connection Retirement

### [x] 6.1 Add real retirement fixtures

- [x] Send H2/H2C `GOAWAY` while at least one stream is active.
- [x] Verify accepted streams follow the peer last-stream identifier semantics.
- [x] Verify later calls use replacement connection/stream capacity.
- [x] Do not imply retry for a possibly processed non-repeatable request.

### [x] 6.2 Prove pool and shutdown convergence

- [x] Verify active and pending stream gauges converge after retirement.
- [x] Keep cancellation, reset, compression, and response ownership stream-local.
- [x] Verify factory shutdown terminates active/pending work within the bounded
      disposal policy.
- [x] Add operations guidance for graceful retirement versus connection failure.

Evidence:

- `Http2GoAwayRetirementContractTest` sends a real H2C
  `GOAWAY(NO_ERROR)` with zero extra stream identifiers while two streams are
  active and the peer advertises spare stream capacity. The fixture captures the
  encoded frame and asserts its actual last-stream identifier: one accepted odd
  stream is below the boundary, one is at it, and later demand would require the
  next odd identifier above it. That later call remains undispatched while both
  accepted streams complete and throughout a bounded open-socket observation
  window; after the peer closes that draining socket, the call runs on a distinct
  replacement connection without exceeding `maxConnections=1`.
- A `Flux<DataBuffer>` upload is fully consumed and recorded by the peer before
  GOAWAY is flushed and the accepted stream is reset without a response. The
  client call fails with one source subscription, one processed body, and one
  server dispatch, proving that ambiguous failure adds no hidden replay.
- The same real transport gates its reset frame until GOAWAY has flushed,
  observes sibling cancellation at the server, and proves active-stream
  accounting drops while the draining socket remains open. The retained
  `ResponseEntity<Flux<DataBuffer>>` response records client gzip negotiation,
  sends explicit `Content-Encoding: gzip` wire bytes, decodes successfully, and
  releases emitted buffers before a replacement probe succeeds.
- Shutdown uses a 30-second pending-acquire timeout but completes draining active
  and queued futures exceptionally inside the factory's five-second disposal
  bound, disposes the provider, removes starter-owned stream gauges, and never
  dispatches the queued request. `docs/12-proxy-tls.md` and
  `docs/30-operations-troubleshooting.md` distinguish graceful GOAWAY from
  connection failure and document the one-connection retirement queue.

---

## Priority 7 - Terminal Diagnostics Under Feature Composition

### [x] 7.1 Keep one terminal fact model

- [x] Align lifecycle, observer, exchange log, Micrometer, OTel, and health facts
      across V24 composition fixtures.
- [x] Prevent prior-attempt URL, headers, status, and dispatch evidence from
      leaking into pre-dispatch terminal failures.
- [x] Keep arbitrary auth/custom-filter failures sanitized and bounded.
- [x] Preserve additive, deterministic diagnostics schema v1 output without
      request-scoped configured-client fields.

### [x] 7.2 Keep diagnostics side-effect free

- [x] Prove diagnostics do not instantiate lazy clients or auth providers.
- [x] Prove diagnostics do not create resilience instances or network resources.
- [x] Preserve bounded map, JSON, and Markdown snapshot limits.
- [x] Run support-bundle schema fixtures and compatibility tests.

Evidence:

- The real retry/redirect/auth composition fixture now carries its terminal
  pre-dispatch auth failure through lifecycle, observer, exchange-log, Micrometer,
  and health assertions. All exposed facts agree on client, API/method, null final
  status and dispatch evidence, `AUTH_PROVIDER_ERROR`, unknown failure stage,
  non-negative duration, and subscription-attempt count; prior response headers, URL,
  status, and `RESPONSE_BODY` evidence remain absent. The OTel companion maps the
  same observer fact model to one error span with the matching structural fields.
- `DefaultHttpExchangeLogger` now records only error type, category, and proven
  stage. The OTel observer emits a structural `exception.type` event without
  exception message or stack trace. Regression tests use a credential-bearing
  10,000-character custom-filter error and prove that payload is absent.
- Diagnostics registry discovery distinguishes absent registries from unresolved
  lazy candidates, honors the primary initialized registry, and reads an
  already-cached singleton `FactoryBean` product without creating an uncached one.
  Provider-backed rendering leaves lazy registries and client factories
  uninstantiated, does not create missing Retry instances or auth providers, and
  therefore cannot create connector, proxy, pool, or network resources. Unresolved
  strict validation remains `null` rather than being reported as inactive.
- Existing schema-v1 fixtures retain the exact root/client field sets and value
  kinds without request-scoped facts. Client, endpoint, text, and 1 MiB UTF-8 map,
  JSON, and Markdown limits all passed. Focused tests plus the full starter and
  OTel suites passed. Strict japicmp for both modules passed against published
  `3.3.0` with Central-only settings; `git diff --check` passed.

---

## Priority 8 - Mock and Assembled-Consumer Parity

### [ ] 8.1 Keep mock behavior within stable starter-owned boundaries

- [ ] Reject the same unsupported method shapes as production.
- [ ] Distinguish mock response sequencing from real socket dispatch semantics.
- [ ] Preserve constructor-injected logger, application codec, auth-provider,
      inherited generic, and ordered lifecycle behavior.
- [ ] Add focused replay and composition assertions without simulating transport
      facts the mock cannot prove.

### [ ] 8.2 Keep independent consumers isolated and reproducible

- [ ] Run current `3.4.0-SNAPSHOT` and published `3.3.0` consumers from separate
      repositories.
- [ ] Reject reactor/local-repository leakage in the published lane.
- [ ] Copy failure evidence incrementally and identify the last completed stage.
- [ ] Reject stale Surefire evidence from previous verifier runs.

---

## Priority 9 - Dependency, API, AOT, and Native Evidence

### [ ] 9.1 Revalidate the supported dependency matrix

- [ ] Run minimum and forward Spring Boot 4 rows under Java 21.
- [ ] Record resolved dependency provenance for each row.
- [ ] Verify optional Actuator, Micrometer, OTel, Resilience4j, and auth back-off.
- [ ] Run strict API compatibility with each row's managed classpath where
      dependency-linked public types require it.

### [ ] 9.2 Keep public and native contracts complete

- [ ] Include every V24 public addition/deprecation in strict japicmp coverage.
- [ ] Defer incompatible changes from the `3.x` minor line.
- [ ] Cover inherited generic and return-type reflection without deprecated
      Framework 7 member categories.
- [ ] Build and execute the GraalVM 25 fixture from a clean immutable commit.
- [ ] Exercise at least one V24 validation or network-composition contract natively.

---

## Priority 10 - Benchmark and Allocation Re-Audit

### [ ] 10.1 Keep benchmark comparison fair and scoped

- [ ] Pass discovery and fairness guards before measurements.
- [ ] Rerun only production paths changed by V24.
- [ ] Keep current and published `3.3.0` reports in distinct fresh repositories.
- [ ] Compare equivalent Boot, transport, codec, and optional-feature work.

### [ ] 10.2 Record review evidence without broad claims

- [ ] Review movement by named scenario and allocation profile.
- [ ] Keep smoke output out of public numerical claims.
- [ ] Keep normal CI free of hard numeric performance gates.
- [ ] Promote a clean report only if `3.4.0` notes make a numerical performance
      or allocation claim.

---

## Priority 11 - Documentation and Operations Consolidation

### [ ] 11.1 Align public contract guidance

- [ ] Add a concise supported return-shape table.
- [ ] Add one replay-safety decision path.
- [ ] Align resilience composition, proxy type, mTLS, and H2 retirement wording
      with real fixtures.
- [ ] Keep README concise and link to canonical detailed guidance.

### [ ] 11.2 Separate current instructions from historical evidence

- [ ] Label migration, API-report, benchmark-report, and release-decision docs as
      immutable historical evidence where applicable.
- [ ] Keep operations troubleshooting and support bundles as canonical incident
      entry points.
- [ ] Use sanitized `EXAMPLE_` and `.example.invalid` placeholders.
- [ ] Run generated metadata, example-property, anchor, local-link, roadmap-link,
      and public-version tests.

---

## Priority 12 - V24 Release Go/No-Go

### [ ] 12.1 Select release scope and assemble evidence

- [ ] Decide whether delivered scope is documentation/correctness-only or a
      backward-compatible `3.4.0` public addition.
- [ ] Run full reactor, strict root/module API, packaging, current/published
      consumer, supported matrix, transport, AOT/native, and documentation gates.
- [ ] Verify complete parent, starter, test-helper, and OTel candidate artifacts.
- [ ] Use one immutable commit for every reproducible release gate.
- [ ] Cite a clean promoted report or make no numerical performance claim.

### [ ] 12.2 Record the decision

- [ ] For go, publish from the matching tag and verify every Central artifact.
- [ ] After publication, move public/API/consumer/benchmark baselines and open
      the next snapshot line.
- [ ] For no-go, publish nothing and record each blocker, reproduction, and
      retained evidence path.
- [ ] Update roadmap/checklist status only after decision evidence exists.
- [ ] Run final release-document tests and `git diff --check`.

## Completion Rule

V24 is complete only when each changed behavior has evidence at the layer that
owns it. Annotation and configuration unit tests do not replace proxy, mTLS,
HTTP/2 retirement, replay dispatch, pool-capacity, or request-body ownership
fixtures. Synthetic transport evidence must not be described as a real wire
contract, and archive cleanup must not rewrite historical release evidence.
