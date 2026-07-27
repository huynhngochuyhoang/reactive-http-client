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

### [ ] 2.1 Define one supported response-shape grammar

- [ ] Inventory raw and parameterized `Mono`/`Flux`, `Void`, `ResponseEntity<T>`,
      direct `Flux<T>`, `Flux<DataBuffer>`, and
      `Mono<ResponseEntity<Flux<DataBuffer>>>` behavior.
- [ ] Centralize the supported-shape decision without adding a new public
      abstraction unless compatibility requires one.
- [ ] Resolve inherited and multi-level generic return types against the concrete
      client before validation.
- [ ] Preserve existing valid unary, typed `Flux<T>`, and raw streaming paths.

### [ ] 2.2 Reject ambiguous nested reactive envelopes at startup

- [ ] Reject `Mono<ResponseEntity<Flux<Dto>>>`, `Mono<Mono<T>>`,
      `Flux<Flux<T>>`, and equivalent unresolved shapes before proxy creation.
- [ ] Include concrete client, declaring interface, full method signature,
      resolved response type, and a supported alternative in each error.
- [ ] Apply the same decision in startup validation, effective-contract export,
      diagnostics, AOT metadata, and `MockReactiveHttpClient`.
- [ ] Add direct, inherited, generic, `@ApiRef`, AOT, mock, and compatibility tests.

---

## Priority 3 - Resilience Operator Composition Contract

### [ ] 3.1 Freeze the existing wrapper semantics

- [ ] Build a deterministic fixture with retry, rate limiter, circuit breaker,
      bulkhead, per-attempt timeout, and logical-call timeout enabled together.
- [ ] Record which operators acquire once per logical subscription and which
      observe each retry subscription.
- [ ] Prove retry exhaustion produces the expected circuit outcomes, permissions,
      occupancy, attempt count, and one terminal result.
- [ ] Keep operator ordering internal and non-configurable.

### [ ] 3.2 Prove permit and terminal cleanup

- [ ] Cover cancellation and logical-budget expiry during admission, execution,
      retry delay, and response consumption.
- [ ] Verify every permit is released once and no delayed retry remains active.
- [ ] Report missing/no-op optional operators as unavailable, not active.
- [ ] Align startup diagnostics, lifecycle, observer, exchange logging, metrics,
      docs, and test helpers with the proven semantics.

---

## Priority 4 - Retry, Redirect, and Auth Replay Composition

### [ ] 4.1 Add a bounded pairwise real-server matrix

- [ ] Cover retry plus `307`/`308`.
- [ ] Cover retry plus one-time OAuth2 `401` refresh.
- [ ] Cover redirect plus OAuth2 refresh.
- [ ] Distinguish outer subscription, resilience subscription, hidden auth replay,
      redirect dispatch, and body subscription counts.

### [ ] 4.2 Preserve replay safety and final-attempt truth

- [ ] Keep generated idempotency keys fresh per outer subscription and stable
      across every replay in that subscription.
- [ ] Prove repeatable bodies reproduce identical bytes without buffering
      application-owned bodies.
- [ ] Preserve documented warning/rejection behavior for non-repeatable bodies.
- [ ] Enforce same-authority and cross-authority sensitive-header policy.
- [ ] Prevent prior dispatch URL/header/status/failure evidence from leaking into
      the terminal visible result.

---

## Priority 5 - Real Proxy and mTLS Wire Contracts

### [ ] 5.1 Prove successful proxy behavior

- [ ] Add a local forward proxy for successful HTTP absolute-form forwarding.
- [ ] Add successful HTTPS `CONNECT` tunneling through the configured proxy.
- [ ] Cover proxy authentication without exposing credentials in any diagnostic.
- [ ] Prove both proxy and bypass paths for `non-proxy-hosts` Java regexes.
- [ ] Cover SOCKS locally or narrow public support wording explicitly.
- [ ] Align `HTTP` and `HTTPS` proxy enum wording with Reactor Netty behavior and
      compatibility evidence.

### [ ] 5.2 Prove configured client-certificate mTLS

- [ ] Add a local server that accepts the configured trusted client identity.
- [ ] Reject missing and untrusted client certificates deterministically.
- [ ] Preserve bounded TLS failure attribution and secret redaction.
- [ ] Cover HTTP/1.1 and TLS H2 where the fixture supports both.

---

## Priority 6 - HTTP/2 GOAWAY and Connection Retirement

### [ ] 6.1 Add real retirement fixtures

- [ ] Send H2/H2C `GOAWAY` while at least one stream is active.
- [ ] Verify accepted streams follow the peer last-stream identifier semantics.
- [ ] Verify later calls use replacement connection/stream capacity.
- [ ] Do not imply retry for a possibly processed non-repeatable request.

### [ ] 6.2 Prove pool and shutdown convergence

- [ ] Verify active and pending stream gauges converge after retirement.
- [ ] Keep cancellation, reset, compression, and response ownership stream-local.
- [ ] Verify factory shutdown terminates active/pending work within the bounded
      disposal policy.
- [ ] Add operations guidance for graceful retirement versus connection failure.

---

## Priority 7 - Terminal Diagnostics Under Feature Composition

### [ ] 7.1 Keep one terminal fact model

- [ ] Align lifecycle, observer, exchange log, Micrometer, OTel, and health facts
      across V24 composition fixtures.
- [ ] Prevent prior-attempt URL, headers, status, and dispatch evidence from
      leaking into pre-dispatch terminal failures.
- [ ] Keep arbitrary auth/custom-filter failures sanitized and bounded.
- [ ] Preserve additive, deterministic diagnostics schema v1 output without
      request-scoped configured-client fields.

### [ ] 7.2 Keep diagnostics side-effect free

- [ ] Prove diagnostics do not instantiate lazy clients or auth providers.
- [ ] Prove diagnostics do not create resilience instances or network resources.
- [ ] Preserve bounded map, JSON, and Markdown snapshot limits.
- [ ] Run support-bundle schema fixtures and compatibility tests.

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
