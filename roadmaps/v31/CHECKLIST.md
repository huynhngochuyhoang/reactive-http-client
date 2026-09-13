# Reactive HTTP Client - Roadmap V31 Execution Checklist

> **Status:** active
> **Published/API baseline:** `4.3.0`
> **Development line:** `4.4.0-SNAPSHOT`
> **Release scope:** unselected

Companion to [`ROADMAP.md`](ROADMAP.md). Execute priorities in order unless a
confirmed correctness or release blocker requires reordering. Checklist adoption
starts execution planning; it does not complete Priority 1, implement a helper,
or select a release. Completion and evidence are recorded under each priority.

Check an item only after its implementation, verification and disposition are
recorded under that priority. Keep generated evidence under
`target/release-evidence/v31/priority<N>/`: commands, actual test totals,
toolchain versions, commit, dirty/clean state, settings and report paths. Preserve
failed runs and partial reports. Native and release-quality performance records
require exact clean source provenance and artifact/report hashes; a local
pre-squash hash must not stand in for a reachable reviewed revision.

## Execution Gates

- Priority 2 separates casing, filtering and subscription-boundary behavior
  before changing production code. The reported deployment is not proof of an
  Istio defect or starter context loss.
- Priority 3 freezes and implements the additive lookup contract. Keep existing
  bulk-map spelling, string keys, constructors and restoration semantics.
- Priorities 4-6 prove protocol, handoff and composition behavior before claiming
  safer application integration; a helper is not a forwarding or trust policy.
- Priority 7 requires real assembled-consumer and native evidence, not only mock
  tests. Optional manual mesh reproduction remains explicitly unverified or
  unnecessary with a recorded reason, never an assumed pass.
- Priority 8 uses observable structural evidence only. New public telemetry or
  configuration needs separate justification and review; it is not implied here.
- Priority 9 distinguishes harness smoke, allocation/retention evidence and
  release-quality comparison. Keep manual commands available for the user.
- Public/API/consumer/benchmark baselines stay at `4.3.0`. Priority 10 selects
  minor, patch, documentation-only or no-go scope; signing, publication and fresh
  Central verification are separate gates.

## Required Invariants

| Surface | Required behavior |
|---|---|
| Existing bulk context map | Preserve captured spelling, exact-key access and published snapshot semantics |
| New named lookup | ASCII case-insensitive field-name comparison; no value conversion or exact-case preference |
| Absence and empty list | Empty immutable values / empty optional; empty string remains a present value |
| Multiple matching values | Preserve multiplicity and exposed ordering; single-value access fails explicitly |
| Filtering and redaction | Do not recover dropped/redacted data or silently broaden capture defaults |
| Explicit snapshot restore | Present fields replace matching target keys; absent fields do not clear them |
| Caller and shared work | Independent caller snapshots; no implicit identity, authorization or cache partition |
| Terminal ownership | No stranded reservations, duplicate terminal callbacks or retained starter-owned context |
| Evidence | No arbitrary header names/values, credentials, payloads or identities in operational artifacts |

---

## Priority 1 - Post-`4.3.0` Baseline and V31 Scope Integrity

### [x] 1.1 Align published and development lanes

- [x] Verify root/modules, benchmark, native and current-consumer coordinates
      remain `4.4.0-SNAPSHOT`; do not bump them merely to adopt execution.
- [x] Keep README/quick-start coordinates, `latest.published.version`, strict
      API, published consumer and benchmark baselines at `4.3.0`.
- [x] Verify the current published-baseline benchmark profile includes V30
      work-limit rows; preserve explicitly selected historical exclusions.
- [x] Preserve V1-V30 completed records and V30 release/tag/native/performance
      provenance. The adopted V27 resilience proposal is not new V31 scope.

### [x] 1.2 Reprove the published baseline

- [x] Resolve all 13 parent/module POM, binary, source and Javadoc artifacts from
      a previously absent Central-only repository; record versions, hashes and
      remote repository markers.
- [x] Run the published `4.3.0` assembled consumer without reactor-output leakage;
      retain effective POM, dependency tree, classpath, actual totals and provenance.
- [x] Run independent strict root and starter-module source/binary comparisons
      against fresh `4.3.0` repositories.
- [x] Run API/published-baseline negative fixtures for self-comparison,
      contamination, missing attachments and inconsistent declared versions.

### [x] 1.3 Verify adopted execution state

- [x] Verify V31 is the sole active roadmap with this linked checklist and exact
      active-status matching; retain all V1-V30 completion checks.
- [x] Regenerate readiness with active roadmap `v31`, release lane `unselected`,
      snapshot development, no planned final release and an unpublished deferred
      candidate. Adoption must not imply completed implementation.
- [x] Run Maven validation, `DocumentationReleaseArtifactTest` and
      `git diff --check`; record actual commands and results for this priority.

Evidence executed on 2026-09-12 from clean commit
`2d51de60368e6715921ce8752b8e32132b884b18`, before this checklist-only
completion update:

- Root/module, benchmark, native-smoke and current-consumer coordinates remain
  `4.4.0-SNAPSHOT`. Public snippets plus published/API/consumer/benchmark
  baselines remain `4.3.0`. The current published-baseline benchmark profile has
  no source exclusions and includes V30 work-limit rows; V27-V30 exclusions
  remain confined to explicitly named historical profiles. V1-V30 completion
  and V30 release evidence remain unchanged.
- `scripts/verify-published-release-artifacts.sh 4.3.0` resolved all 13 required
  parent/module POM, binary, source and Javadoc artifacts from a previously
  absent Central-only repository. Declared POM and embedded JAR versions,
  SHA-256 values and four module remote-marker records passed. Evidence is under
  `target/release-evidence/v31/priority1/published-baseline/`.
- `scripts/verify-published-consumer.sh 4.3.0` passed 4 tests with zero failures,
  errors or skips using published artifacts only. Effective POMs, dependency
  tree, classpath, Surefire XML, seven artifact hashes and clean-source
  provenance are under `target/release-evidence/v31/priority1/published-consumer/`.
  No reactor output directory was present on the consumer classpath.
- Independent root and starter-only
  `-Papi-compatibility -DskipTests verify` builds passed with source and binary
  incompatibility failures enabled. Fresh repositories
  `v31-priority1-api-root-4.3.0` and
  `v31-priority1-api-starter-4.3.0` produced seven and two baseline artifact
  hashes respectively, with Central remote markers retained under this
  priority's `api-root/` and `api-starter/` evidence directories.
- `bash scripts/verify-published-baseline-fixtures.sh` rejected local
  contamination, root/module self-comparison, missing source/Javadoc attachments,
  mismatched parent/project POM versions and mismatched embedded JAR versions.
  `bash scripts/verify-api-compatibility-fixtures.sh` accepted additive and
  defaulted-annotation changes while rejecting source-only checked exceptions,
  removed constructors, nested methods and enum constants. Fixture outputs are
  retained under `baseline-fixtures/` and `api-fixtures/`.
- Generated readiness records development `4.4.0-SNAPSHOT`, published/API
  baseline `4.3.0`, active roadmap `v31`, release lane `unselected`, no planned
  final release, and a deferred unpublished `4.4.0` candidate with unselected
  scope. The generated manifest and benchmark snippet are under `readiness/`.
- `mvn -B -ntp -s .mvn/maven-central-settings.xml validate` passed all four
  reactor modules. The same settings with
  `-pl reactive-http-client-starter -Dtest=DocumentationReleaseArtifactTest test`
  passed 49 tests with zero failures, errors or skips. `git diff --check` passed.
  Toolchain: Maven `3.9.9`, GraalVM JDK `25.0.3`, Java `21` compilation target
  and Spring Boot `4.0.0`.
- Exact commands, stage exit statuses, logs, test reports, toolchain, generated
  readiness and clean-source provenance are under
  `target/release-evidence/v31/priority1/`. Existing post-publication `4.3.0`
  artifact and consumer evidence was preserved under
  `target/v31-priority1-previous-state/` and was not reused by these fresh runs.

## Priority 2 - Inbound Header and Context Characterization

### [x] 2.1 Reproduce the named-lookup failure independently of a mesh

- [x] Use fake bounded headers with mixed, lower and upper case; record that the
      Reactor key and matching captured entry exist while exact `Map.get` misses.
- [x] Distinguish the absent map entry that makes `getFirst()` dereference null
      from an empty list, empty string, redacted value and malformed JSON.
- [x] Reproduce against server `HttpHeaders`, ordered application maps and
      capture/restore snapshots; do not infer pre-proxy spelling from the map.
- [x] Keep production behavior unchanged while these baseline cases are recorded.

### [x] 2.2 Characterize independent missing-value causes

- [x] Test absent context key, outside-allow-list names, denied selected fields,
      empty lists, empty strings, multiple values and differently cased aliases.
- [x] Compare an ordinary composed subscription with an independent sink or
      callback subscription, with and without explicit snapshot restoration.
- [x] Characterize raw malformed values under the public context key separately
      from well-formed absence; record the actual failing boundary.
- [x] Record WebFilter registration, replacement and capture order relevant to
      the reproduction without changing security-filter ordering.

### [x] 2.3 Classify the evidence and narrow production work

- [x] Record each observation as verified defect, documented caller misuse,
      additive usability gap or unverified deployment hypothesis.
- [x] Inventory existing tests/docs that intentionally preserve spelling,
      string keys, immutable values and restore precedence.
- [x] Require a focused reproducer and compatibility review for any correction
      beyond the additive named-access API.
- [x] Record whether direct protocol tests suffice or a normalizing intermediary/
      manual mesh scenario is needed; do not claim Istio caused an unobserved change.

Evidence recorded on 2026-09-12 against base commit
`2d51de60368e6715921ce8752b8e32132b884b18` with the new tests and documentation
in a dirty working tree. The existing Priority 1 completion edits were retained.

- [Characterization and disposition](INBOUND-HEADER-CHARACTERIZATION.md) records
  the failure matrix, registration/order boundary, compatibility inventory and
  scope decision. Exact spelling can miss despite a present selected entry;
  null dereference, empty-list access and application parsing failures have
  separate reproducers. This confirms an additive lookup usability gap without
  establishing a production defect or a cause in the reported Istio deployment.
- `InboundHeaderContextCharacterizationTest`: 18 cases passed for lower/mixed/
  upper spelling, empty/absent/redacted/repeated values, malformed raw context,
  defensive snapshot restoration and both request-mutation orders.
  `InboundHeaderHandoffCharacterizationTest`: 6 cases passed for reads after
  composed scheduler switches and independent sink subscriptions with/without
  worker headers and explicit restore. `InboundHeadersAutoConfigurationTest`:
  4 cases passed for bound selection, reactive registration, non-web absence,
  replacement capture behavior and an unrelated filter.
- The focused three-suite command passed 28 tests. The combined regression
  command also included `InboundHeadersWebFilterTest`, `RequestContextSnapshotTest`,
  `RequestContextContributorTest`, `CorrelationIdWebFilterTest`,
  `CorrelationIdPropagationTest`, `ReactiveHttpClientAutoConfigurationTest`,
  `Boot4AutoConfigurationTest` and `DocumentationReleaseArtifactTest` and passed
  144 tests total, including 49 documentation tests. All runs had zero failures,
  errors or skips. Both use
  `mvn -B -ntp -s .mvn/maven-central-settings.xml -pl reactive-http-client-starter`
  with the recorded `-Dtest` suite lists and `test` goal.
- Production sources, public defaults and V1-V30 records are unchanged.
  Priority 3 retains the additive scope and requires focused reproduction plus
  compatibility review for any further correction. Direct HTTP/1.1 and HTTP/2
  verification is assigned to Priority 4; an intermediary is currently
  unnecessary, and manual mesh behavior remains unverified.
- Commands, logs, fresh Surefire XML, source copies/SHA-256 values, dirty-source
  provenance and generated readiness are under
  `target/release-evidence/v31/priority2/`. Toolchain: Maven `3.9.9`, GraalVM JDK
  `25.0.3`, Java `21` compilation target and Spring Boot `4.0.0`.
  `git diff --check` passed; the existing development, baseline and unselected
  release state remain in effect.

## Priority 3 - Case-Insensitive Header Access Contract

### [x] 3.1 Freeze the additive surface before implementation

- [x] Choose final method names/signatures for immutable all-values and optional
      single-value access on `RequestContext`; prefer existing local APIs.
- [x] Specify absence, empty-list, empty-string, redaction and ambiguity behavior
      exactly as the roadmap table, including duplicate equal values.
- [x] Specify ASCII field-name token validation, locale-independent matching,
      null arguments and bounded structural misuse errors with no raw value text.
- [x] Define malformed map/key/list/element validation, including entries outside
      the requested name, so malformed input cannot accidentally become absence.
- [x] Preserve existing bulk map, key aliases, snapshot record components/
      constructors and merge behavior. Do not introduce a global normalized map.

### [x] 3.2 Implement and test named access

- [x] Read only the captured context snapshot; do not access the exchange, MDC,
      live request, auth provider or alternative credentials.
- [x] Collect all case-insensitive matches in map-iteration and per-entry value
      order without exact-case preference, deduplication, joining or splitting.
- [x] Return empty values/optional for absence; retain a present empty string and
      fail single-value lookup when more than one value exists across aliases.
- [x] Preserve case and bytes of values; reject invalid names without underscore/
      hyphen substitution, Unicode case folding or locale-dependent matching.
- [x] Return defensive immutable values; test mutation of source maps/lists after
      capture and attempted mutation of returned collections.
- [x] Test wrong context types and malformed raw maps/lists with bounded
      structural errors; never stringify arbitrary application values for errors.

### [x] 3.3 Lock compatibility and application responsibility

- [x] Preserve legacy exact-key behavior for captured spellings and public string
      aliases; test against the existing snapshot and contributor contracts.
- [x] Cover reordered aliases/values, equal duplicates, unusual legal token
      characters, invalid names and a non-English default locale with restoration.
- [x] Demonstrate required-header validation and parsing as application-owned
      decisions; no default identity, automatic JSON codec or silent first-value
      selection is introduced.
- [x] Verify manual context writes are not falsely described as globally
      sanitized, immutable or authenticated simply because a reader helper exists.

Evidence recorded on 2026-09-12 against base commit
`6525f67c` with the additive implementation, tests and documentation in a dirty
working tree; full source revision and SHA-256 copies are retained locally.

- The [named-access contract](../../docs/09-correlation-id.md#named-inbound-header-access-440-development)
  freezes `inboundHeaderValues(ContextView, String): List<String>` and
  `inboundHeader(ContextView, String): Optional<String>`. The first returns
  immutable ordered values; the second rejects all multiplicity, including equal
  duplicates. Fixed structural errors distinguish invalid arguments, malformed
  input and ambiguity without including application names, values or descriptions.
- `RequestContextHeaderAccessTest` passed 39 cases covering all ASCII token
  characters/separators, Unicode rejection, Turkish locale with restoration,
  alias/value reordering, empty/redacted/repeated values, raw malformed
  map/key/list/element types (including unrelated and late entries), immutable
  results, source mutation, legacy key/snapshot/contributor behavior and
  application-owned required-header checks and JSON parsing. The initial red
  compile failed because the two methods did not yet exist; implementation then
  passed the focused suite. No production capture or restore path changed.
- The final combined context/filter/configuration/documentation regression
  command passed 183 tests with zero failures, errors or skips, including the
  39 named-access cases, the 28 Priority 2 characterization cases and 49
  `DocumentationReleaseArtifactTest` cases. Exact `-Dtest` arguments, settings,
  logs and fresh Surefire XML are under
  `target/release-evidence/v31/priority3/regression/`.
- Independent strict root and starter-only `-Papi-compatibility -DskipTests verify`
  builds passed against published `4.3.0`, reusing Priority 1's isolated Central
  baseline repositories with artifact hashes and remote markers revalidated.
  Japicmp reports exactly two new public methods on `RequestContext`, with no
  removed/changed public signatures; other modules have no public API delta.
- Source copies/hashes, dirty-tree diff, commands, toolchain, API reports and
  generated readiness are retained under this priority's evidence directory.
  Maven `3.9.9`, GraalVM JDK `25.0.3`, Java `21` target and Spring Boot
  `4.0.0` were used. `git diff --check` passed. After this completion update,
  the documentation suite was rerun (49 passing tests). Versions, published
  baselines, unselected release state and V1-V30 evidence remain unchanged.

## Priority 4 - WebFlux, Protocol, and Filter Boundaries

### [x] 4.1 Prove actual inbound wire behavior

- [x] Exercise real loopback WebFlux requests over HTTP/1.1 and HTTP/2 using fake
      bounded fields; assert negotiated protocol and actual captured field names.
- [x] Test lowercase names on both protocols and mixed-case HTTP/1.1 input;
      do not send invalid uppercase HTTP/2 field names to prove normal behavior.
- [x] Compare exact bulk-map access and new named access for the same request,
      including repeated values and a required-but-missing field.
- [x] Add a deterministic normalizing intermediary only if direct protocol
      evidence leaves a necessary boundary uncovered; use gates, not setup sleeps.

### [x] 4.2 Preserve filter and registration semantics

- [x] Test case-insensitive allow/deny matching, redaction precedence and
      outside-allow-list omission without widening existing defaults.
- [x] Test request mutation before and after capture; snapshots reflect only the
      request visible to the capture filter and remain immutable afterward.
- [x] Verify default and replacement WebFilter registrations without reordering
      application security filters or adding automatic outbound forwarding.
- [x] Cover the documented WebFlux application boundary; confirm a WebClient in
      an MVC application alone does not imply an inbound servlet capture bridge.

### [x] 4.3 Record protocol versus deployment evidence

- [x] Document tested client/server protocol hops and fixture versions; a
      simulated intermediary is not a certification of an Istio deployment.
- [x] For needed manual mesh reproduction, provide bounded commands and required
      version/protocol facts, with no exported header values or identity material.
- [x] Record manual evidence as verified, unverified, or not required with
      rationale. Never turn unavailable cluster access into a passing mesh claim.

Evidence recorded on 2026-09-12 against base commit
`e33148dc92d97e97ac7c72a43299de3c2e6e159d` plus the tests, test dependency and
documentation in this dirty working tree.

- [Inbound wire and filter boundary report](INBOUND-WIRE-BOUNDARIES.md) records
  the protocol/name matrix, direct-versus-deployment disposition, ownership and
  fixture versions. `InboundHeadersWireContractTest` passed 24 cases: lowercase
  and mixed-case HTTP/1.1, lowercase prior-knowledge H2C and lowercase TLS H2,
  each with selected/default filtering, both mutation orders, replacement
  capture and no automatic inbound-header forwarding to a real starter client.
- Every wire case asserts the protocol on both ends, H2 stream-channel identity
  where applicable, TLS ALPN `h2` for encrypted H2, and one inbound dispatch.
  Actual captured names, differently cased exact-map misses, named access,
  repeated/equal values, empty strings, required-field absence, redaction and
  exclusion are checked on the same request. Mutable request maps/lists cannot
  retroactively change captured or restored values.
- `InboundHeadersAutoConfigurationTest` passed six cases, including reactive
  default/replacement registration, unrelated filters, non-web context and
  servlet context with WebClient but no ingress bridge. It also checks that
  the capture type/bean method declares no explicit order. Jakarta Servlet
  `6.1.0` is added only in test scope, confirmed by `dependency:tree`.
  Production Java sources, public APIs, defaults and security-filter order are
  unchanged. Correlation propagation remains a separate outbound behavior.
- The completed focused command passed 30 tests (24 wire + 6 registration).
  The combined regression passed 209 tests with no failures, errors or skips,
  including all 39 named-access tests, the existing context/filter/configuration
  suites and 49 documentation tests. An independent wire rerun with
  `-DargLine=-Djava.net.preferIPv6Addresses=true` passed all 24 cases.
  Requests, assertion acknowledgements, server binding and owned-resource
  cleanup are bounded; no sleep determines a test outcome.
- Normalizing intermediary and manual mesh commands are **not required** for
  this priority: direct protocol evidence covers the chosen contract. The
  Istio/Envoy deployment hypothesis remains **unverified**, with bounded
  structural follow-up facts documented in the report, not a passing mesh claim.
- Exact commands, fresh Surefire XML for each final run, logs, source copies/
  SHA-256 values, dirty-source provenance, dependency versions and generated
  readiness are under `target/release-evidence/v31/priority4/`. An intermediate
  no-forwarding fixture failure and its synthetic-input correction are retained,
  not reported as production evidence. Maven `3.9.9`, GraalVM JDK `25.0.3`,
  Java `21` target, Spring Boot `4.0.0`, Framework `7.0.1`, Reactor Core
  `3.8.0`, Reactor Netty `1.3.0` and Netty `4.2.7.Final` were used.
  `git diff --check` and source-copy checksum verification passed. After this
  completion update, `DocumentationReleaseArtifactTest` passed again (49 tests).
  V1-V30 evidence, reactor/baseline versions and unselected release state remain
  unchanged.

## Priority 5 - Explicit Async Handoff and Caller Isolation

### [x] 5.1 Cover composed and independent subscriptions

- [x] Test `deferContextual`, `publishOn`, `subscribeOn` and nested composed
      publishers with context read at subscription time.
- [x] Show independent sink/executor subscribers do not acquire the emitter's
      context automatically, then demonstrate explicit snapshot handoff.
- [x] Use concurrent fake request envelopes and gates to verify a shared worker
      does not reuse another caller's headers or correlation ID.
- [x] Repeat subscriptions to the same cold publisher with distinct contexts;
      no eager method-invocation snapshot may override subscription-local values.

### [x] 5.2 Preserve capture/restore and contributor contracts

- [x] Verify present snapshot fields replace target values while missing fields
      leave target values intact; demonstrate isolated targets when reusing workers.
- [x] Preserve contributor order by order/key and existing correlation/idempotency
      precedence, including explicit outbound-header precedence.
- [x] Keep snapshots limited to correlation ID and inbound headers; idempotency
      or custom context needs explicit application handling, not automatic capture.
- [x] Test immutable copies without retaining live exchanges, request objects or
      arbitrary context maps in new helper state.

### [x] 5.3 Verify terminal release and external ownership

- [x] Exercise completion, error, timeout and cancellation around handoff and
      starter-owned callbacks; prove cleanup with controlled lifecycle evidence.
- [x] Use weak references/reference queues in a controlled JVM lane where
      justified to distinguish starter-owned retention from external owners;
      ordinary unit tests must not depend on the collector accepting `System.gc()`.
- [x] Close executors/subscriptions created by tests and record released owners;
      avoid exact GC deadlines or claims that RSS must immediately fall.
- [x] Require separate evidence and a scoped fix for any retention defect found.

Evidence recorded on 2026-09-12 against reachable base commit
`89b8785e724562b40c23e55453e165bc57443abb` plus the tests and documentation in
the original implementation working tree. These are pre-review results; the
GC portability and teardown-race corrections are recorded in the follow-up below.

- [Async handoff and ownership report](ASYNC-HANDOFF-OWNERSHIP.md) maps the
  subscription, restore, precedence and retention cases to explicit owners.
  `ExplicitAsyncHandoffContractTest` passed 13 cases covering nested composed
  scheduler switches, repeated subscriptions, independent sink/executor
  subscriptions, all partial snapshot combinations and contributor order/key
  ties. Twelve overlapping gated envelopes complete in reverse order on one
  shared worker without cross-caller header/correlation reuse; empty envelopes
  stay empty when restored into isolated inner targets.
- `AsyncHandoffOwnershipContractTest` passed seven cases: two outbound-header
  precedence cases, four terminal paths and one defensive-copy ownership case.
  Completion, error, application timeout and cancellation each produce exactly
  one lifecycle terminal, observer event and exchange log. Source/caller terminal
  acknowledgements and zero remaining sink subscribers establish cleanup.
  The application timeout is deliberately an inner starter cancellation, not
  a new logical-call-timeout attribution. Final-request observation uses the
  same filter as the factory; this gated WebClient fixture makes no wire claim.
- Weak-reference checks run while the proxy, application context and worker
  remain alive. Removing an application envelope releases its snapshot wrapper;
  a retained user log still owns its header String until that record is cleared.
  Original request/exchange/context/map/list objects are collectible while the
  copied snapshot remains retained. No retention defect was found, so there is
  no runtime correction or new helper-owned state. GC retries are diagnostic
  reachability evidence, not exact collection timing or an RSS guarantee.
- The combined regression passed **288 tests**, with zero failures, errors or
  skips, including context/filter/wire/configuration suites, idempotency and
  correlation precedence, lifecycle/diagnostics/reporting, logical-call timeout
  and 49 documentation tests. Five independent focused reruns passed all 20
  new cases each (**100 executions**); executors, schedulers, pending
  subscriptions, contexts and the unused fixture cache manager are closed.
- Exact commands, fresh per-run Surefire XML, source copies/SHA-256 values,
  generated readiness, Maven `3.9.9` / GraalVM JDK `25.0.3` toolchain and
  dirty-source provenance are under `target/release-evidence/v31/priority5/`.
  Two intermediate fixture failures are retained separately and explained in
  the report. `git diff --check` and source-copy checksum verification passed.
  A final `DocumentationReleaseArtifactTest` run after this completion update
  passed 49 tests. Production Java, public APIs, dependencies, Java `21` target,
  `4.4.0-SNAPSHOT`/`4.3.0` versions, V1-V30 evidence and release state are unchanged.

Review follow-up on 2026-09-12, base
`0ffaf2e7d94c16214b38baa7cbf5f32663d7d890` plus the current test/profile/docs diff:

- Removed collection-dependent assertions from the normal handoff tests.
  Lifecycle acknowledgements, subscriber release, defensive copies and explicit
  application-owner clearing remain asserted there. Five reachability probes
  now run only through `-Pv31-handoff-reachability`, outside default Surefire
  discovery, in a forked Serial-GC JVM with explicit GC enabled and a 128 MiB
  maximum heap. The lane verifies those effective preconditions before probing.
- The overlapping-envelope case deliberately pauses sink cancellation, observes
  the still-attached subscriber, releases cancellation and waits for the
  sink-side terminal acknowledgement before checking zero subscribers. It no
  longer treats the result future's completion as proof of upstream teardown.
- The final regression passed **288 tests** with `-XX:+DisableExplicitGC`.
  Five single-CPU focused reruns passed **100 executions** with explicit GC
  disabled; the non-collecting Epsilon JVM passed all **20 focused tests**.
  The controlled reachability lane passed **five probes**. A deliberately
  invalid direct probe invocation with explicit GC disabled failed its
  configuration precondition as expected, before any collection wait.
- Commands, fresh per-run XML, logs, source hashes and dirty-source provenance
  are under `target/release-evidence/v31/priority5/review-fixes/`. All positive
  runs had zero failures/errors/skips; the expected negative run is preserved
  separately. Source-checksum and whitespace checks passed. The completion/docs
  update was then checked by **49 passing documentation tests**. Production
  behavior, artifact versions and published baselines are unchanged.

## Priority 6 - Cache, Auth, Retry, and Terminal-State Composition

### [x] 6.1 Keep caller contexts separate from shared work

- [x] Exercise uncached calls and fresh/stale hits with distinct caller snapshots;
      per-call authorization still runs where required.
- [x] Gate same-key leader/waiter attachment and detach the first caller by
      cancellation and timeout; retain independent waiter terminal context.
- [x] Cover hidden refresh and shared-load continuation without transferring
      another caller's headers into a visible terminal record.
- [x] Use explicitly partitioned auth/header variants and final request identity
      checks; reading a header must not implicitly make it trusted or a cache key.

### [x] 6.2 Preserve retry/replay and deadline behavior

- [x] Cover selected Retry, auth invalidation/replay, redirects and logical-call
      deadlines without altering published selection, ordering or dispatch counts.
- [x] Verify prepared arguments, correlation/idempotency precedence and final
      attempt evidence remain consistent through resubscription.
- [x] Test auth rejection on a warm cache and identity changes after replay;
      named lookup must not bypass validation or cache isolation.
- [x] Use deterministic subscriptions, source gates and terminal acknowledgements;
      immediate local disposal alone is not proof of transport cleanup.

### [x] 6.3 Protect reporting and admission cleanup

- [x] Test malformed raw context with observer, lifecycle and exchange logging
      enabled, both with and without selected cache-work admission.
- [x] Prove capacity is released exactly once after setup/error/cancellation and
      no deferred continuation dispatches after a terminal rejection.
- [x] Count terminal callbacks rather than overwriting a last-event reference;
      assert final error, cancellation, attempts, URL/status/header evidence.
- [x] Preserve redaction and absence in existing terminal surfaces; no header
      values/names enter new metric tags, span attributes or support snapshots.

Evidence executed on 2026-09-12 from reachable base
`7b1344b42abd10d8331bdf5da80575a8d3c948fb` plus the recorded working-tree
test/docs changes. See [CONTEXT-COMPOSITION.md](CONTEXT-COMPOSITION.md) for
the boundary matrix and deliberately unchanged behavior.

- Added **22 starter composition cases** and **six OTel attribute cases**.
  Gated leader/waiter cancellation and deadlines preserve separate snapshots;
  subsequent retries and hidden refresh do not append visible terminal records
  to a completed caller. Explicit tenant/idempotency variants and successful
  final-request revalidation prevent reuse under a changed auth identity.
- Repeated cold subscriptions exercise explicit/restored correlation and
  idempotency precedence through real selected Retry plus 401 replay. The
  loopback redirect records two wire requests and no replay for the fresh hit.
  A subscribed response-body deadline retains RESPONSE_BODY evidence and
  acknowledges source cancellation.
- Warm-cache denial, absent/redacted/ambiguous/malformed header inputs and
  invalid auth headers fail without dispatch. Delayed auth cannot continue after
  cancellation/deadline. Existing preparation-frame, admission and shutdown
  regressions retain their exact-once capacity checks.
- Reporting-setup compatibility is explicit: a non-Map raw `inboundHeaders`
  value still fails before source subscription and produces **zero** terminal
  records, not a synthetic record. Retrying that setup failure does not strand
  capacity. Errors inside the reporting boundary produce exactly one record on
  each enabled observer/lifecycle/log surface.
- The combined **32-class starter regression passed 545 tests**, including
  default logging, diagnostics/support fixtures, wire capture and context
  helpers. Five isolated single-CPU runs with
  `-DargLine="-XX:+DisableExplicitGC -XX:ActiveProcessorCount=1"` passed
  **110 additional composition executions**. The four selected OTel suites
  passed **56 tests**, including all six new outcome/privacy cases.
  All these runs had zero failures, errors or skips.
- Maven `3.9.9`, GraalVM JDK `25.0.3`, Java target `21`; settings
  `.mvn/maven-central-settings.xml`. Exact commands, fresh per-run Surefire XML,
  source copies/SHA-256, logs and dirty-tree provenance are under
  `target/release-evidence/v31/priority6/verification/`. Checksums and
  `git diff --check` passed. Earlier fixture failures are retained separately
  in `first-tests.log`/`first-tests.xml`; the initial compile assertion mismatch
  was corrected before that run. These are not production regressions.
- No production/API/configuration/schema/dependency/version change was needed.
  Published `4.3.0`, development `4.4.0-SNAPSHOT`, later priorities and archived
  roadmap evidence remain unchanged; this is not manual Istio/native evidence.

## Priority 7 - Mock, Assembled-Consumer, AOT, and Native Parity

### [x] 7.1 Align mock access without silently changing assertions

- [x] Test lowercase, mixed-case, absent, empty, duplicate and redacted captured
      values through mock recordings and the new public helpers.
- [x] Preserve existing exact-spelling assertion behavior, or add explicitly named
      alternatives with tests and documentation rather than silently reinterpreting it.
- [x] Cover context snapshots/handoff, repeat subscriptions and closure with both
      deterministic controls and the normal mock construction path.

### [x] 7.2 Verify assembled-consumer and optional-integration parity

- [x] Add current-consumer cases for lowercase inbound capture, optional/all-values
      access, ambiguity, explicit handoff and required-header failure.
- [x] Verify assembled artifacts, effective POM, dependency tree and no reactor
      classpath leakage; preserve reports even when a stage fails.
- [x] Keep published `4.3.0` consumer sources independent of candidate-only APIs.
- [x] Preserve replacement beans, cache-disabled/no-Caffeine consumption and
      optional OTel absence; introduce no mandatory helper dependency.

### [x] 7.3 Reprove AOT and native behavior on the final fixture

- [x] Exercise the new public access from a real application context in JVM/AOT
      and native smoke, including capture and explicit restoration.
- [x] Preserve configured replacement metadata/properties/filter beans and avoid
      reflective header-DTO scanning or unrelated runtime hints.
- [x] Run clean-commit native compile and executable, recording toolchain, exact
      fixture commit, command, executable hash, output and test disposition.
- [x] Rerun after relevant fixture/runtime changes; do not close with a pre-fix
      binary. Keep unsupported optional integration coverage explicitly bounded.

Priority 7 implementation evidence (2026-09-12):

- [Parity evidence](PARITY-EVIDENCE.md) records the
  starting revision `cd75725dae3cab2b8b39d46672820180991d561f` plus the dirty
  fixture/test/documentation changes. No production behavior, public API,
  dependency baseline, version or runtime hint changed.
- Five new mock cases retain exact-spelling assertions and cover the named
  helpers, real filter capture, explicit restoration, repeated cold subscriptions
  and cancellation/close under both normal and deterministic cache construction.
- `scripts/verify-current-consumer.sh` passed from a fresh target-local repository:
  **74 mock tests, 11 assembled full-consumer tests, and 3 minimal-consumer tests**.
  Both consumer classpaths use assembled JARs. The minimal fixture has no
  Caffeine, OTel or test-helper artifact. Effective POMs, trees, classpaths,
  installed artifact hashes and `completedStage=evidence-verified`, `exitStatus=0`
  are retained under `target/release-evidence/current-consumer/current-4.4.0-SNAPSHOT/`.
  Candidate sources are selected only by `consumer.v31.parity=true`; published
  `4.3.0` sources/selection remain independent.
- The targeted reactor regression passed **227 tests** (153 starter, including
  27 AOT and 49 documentation cases, plus 74 mock tests). Native-fixture JVM
  tests passed **6 cases**. All these runs had zero failures, errors or skips.
- The packaged JVM application and regenerated Spring AOT JVM application both
  exited zero, emitted `V31 inbound context: capture=3 restored=2 rejected=1;
  parity passed`, and passed the existing cache/resilience/shutdown smoke.
  This checks the final native fixture on the JVM; it is not native-image evidence.
- Maven `3.9.9`, GraalVM JDK `25.0.3`, Java target `21`, Boot `4.0.0`; exact
  commands, per-stage logs/exit status, fresh Surefire XML and source evidence
  are under `target/release-evidence/v31/priority7/`. The two early compile
  fixture errors are retained separately from passing verification.

Priority 7 native completion (2026-09-13):

- Clean fixture commit `f9b94fd207e6c5af1fc36ee047fd2c491a6c0e30`, tree
  `da53a49e0bb646fa0709dced7e69a7626b154607`, remained unchanged through artifact
  installation, native compile and executable verification. These completion
  notes were added only after that clean run.
- Maven `3.9.9`, GraalVM/native-image `25.0.3`, Java target `21`, Boot `4.0.0`,
  Linux x86-64-v3 and GCC `13.3.0`. Maven used
  `MAVEN_OPTS='-Xmx512m -XX:ActiveProcessorCount=2'`; native compilation used
  `-H:+SharedArenaSupport,-J-Xmx4g,--parallelism=2` with
  `-Pnative clean native:compile`. Reactor installation skipped tests and
  Javadocs; the native build reran **6 JVM fixture tests**, all passing with
  zero failures, errors or skips, and regenerated Spring AOT output.
- Native compilation exited zero (Maven elapsed 8m18s). The new executable
  exited zero under a 180-second process bound and emitted
  `V31 inbound context: capture=3 restored=2 rejected=1; parity passed`.
  Existing cache/resilience/shutdown and context-recreation checks passed too.
- Executable SHA-256:
  `c074e8f5bc3a340f9a8d982136fcc5b752a74c1bf9952c7156e26bd9be1fa5ed`.
  Fixture source checksums and the executable checksum were revalidated after
  execution. Commands, logs, test XML, toolchain, clean-state provenance and
  source archive are in `target/release-evidence/v31/priority7/native-clean/`;
  final provenance is `completedStage=evidence-verified`, `exitStatus=0`.
- The memory preflight wait was stopped at the user's request before launching
  the compiler; its evidence is retained separately. The native compile/run
  needed no source fix or retry. This closes Priority 7 only, not later release
  gates or unsupported Istio, MVC, application header-DTO or native OTel coverage.

## Priority 8 - Documentation and Operations Guidance

### [x] 8.1 Consolidate safe public examples

- [x] Update the correlation/context, exchange logging, test-helper and production
      guides from the same frozen helper/filter/handoff contract.
- [x] Replace nullable map-value `getFirst()` examples with explicit absence and
      multiplicity checks before application parsing.
- [x] Preserve empty-string versus absent/redacted distinctions and application
      size/schema/trust validation; do not describe a redaction marker as identity.
- [x] Separate published `4.3.0` workarounds from candidate-only APIs and use fake
      bounded names/values plus reserved placeholder domains in copyable examples.
- [x] State WebFlux/MVC and scheduler/independent-subscription boundaries without
      implying automatic forwarding, MDC bridging or queue context propagation.

### [x] 8.2 Add a structural troubleshooting path

- [x] Distinguish case mismatch, filtered/redacted fields, wrong filter ordering,
      malformed context and independent-subscription loss using observable checks.
- [x] Describe how to compare the same request at capture/read boundaries without
      collecting raw header values or attributing an unobserved rewrite to Istio.
- [x] Define bounded support fields for versions, protocol hops, capture/read
      boundary, key/match presence and value counts using fake field aliases.
- [x] If a new fixture is needed, add recursive privacy and type/invariant guards;
      keep arbitrary header names, tokens, identities, request targets and payloads out.
- [x] Do not add meters or schema fields unless existing evidence is insufficient;
      record justification, bounded enums/types and privacy review before publishing.

### [x] 8.3 Verify documentation is version-correct

- [x] Run generated documentation, Markdown-link, archive-status and support
      validation tests with actual totals.
- [x] Retain the current snapshot/published baseline split and V30 archived claims.
- [x] Cross-check public examples against executable consumer tests; unsupported
      or optional manual mesh instructions must be labeled accurately.

Priority 8 evidence (2026-09-13): [documentation and operations review](DOCUMENTATION-OPERATIONS.md).
The final public examples, bounded fixture, generated docs/links/archive checks
and context regressions passed 175 starter cases; additional wire/registration
checks passed 30, mock parity passed 5, and the two assembled consumer fixtures
passed 2 each. Existing support sanitizer validation passed 9 Python tests.
The 29 new documentation cases compile and execute the two Markdown lookup
examples and reject recursive privacy, type and invariant violations. Evidence
is under `target/release-evidence/v31/priority8/`, based on committed revision
`83bb48ef14845e4986e1a53761452167924b0307` plus the uncommitted documentation/test
changes; it is not clean release/native/mesh certification. No runtime behavior,
public diagnostics schema, meter, configuration default or version changed;
published `4.3.0`, candidate `4.4.0-SNAPSHOT`, and archived V30 evidence remain
distinct. Priorities 9 and 10 remain open.

Priority 8 review follow-up (2026-09-13): the logging guide now distinguishes
singleton ambiguity rejection from unchanged plural values. Capture-policy
guards reject omitted names and denied values without one marker per captured
name, without imposing those rules on later reads or unknown observations.
The documentation contract now has 32 cases; the focused docs/helper/filter
rerun passed 127 cases. See the [review follow-up](DOCUMENTATION-OPERATIONS.md#review-follow-up)
for the separate failing and passing evidence.

Ordering/parser follow-up (2026-09-13): plural lookup is documented as exposed
map-iteration/per-list order, and strict duplicate-key parsing rejects hidden
first occurrences before fixture privacy validation. The documentation contract
now has 36 cases; 124 focused docs/helper cases passed. Separate logs are in
`target/release-evidence/v31/priority8/duplicate-key-review/`.

## Priority 9 - Compatibility and Targeted Performance Evidence

### [ ] 9.1 Revalidate the supported artifact surface

- [ ] Run strict root and starter-module source/binary checks against independent
      fresh Central `4.3.0` repositories, including context and mock public APIs.
- [ ] Run negative compatibility/provenance fixtures and package/generation guards;
      do not relax strict checks to approve an accidental behavior or API break.
- [ ] Run complete tests and the supported dependency matrix; record actual totals,
      commands and toolchains, preserving failures and retried evidence separately.
- [ ] Include current/published consumers and final composition/shutdown regressions;
      retain AOT/native evidence at its exact source revision.

### [ ] 9.2 Measure the changed path, not an unrelated substitute

- [ ] Add targeted lookup/capture/restore measurements at realistic bounded header
      counts and concurrent explicit handoffs, including absent and multi-value cases.
- [ ] Check transient allocation and post-terminal retention separately; avoid a
      full-context copy or global retained index for one named lookup.
- [ ] Compare unchanged cache-disabled/cached invocation paths against `4.3.0`;
      isolate any new-helper-only rows from shared baseline comparisons.
- [ ] Exercise the real public helper/production state in measured rows and prove
      handoff/subscriber attachment with gates rather than short sleep assumptions.

### [ ] 9.3 Review performance disposition explicitly

- [ ] Run harness tests and smoke to verify wiring; do not treat those numbers as
      release-quality performance claims.
- [ ] Provide reproducible current/baseline commands for a manual release-quality
      comparison when required, with separate output and fresh baseline repositories.
- [ ] Review clean-source reports, allocation data, unmatched rows and uncertainty,
      or document why no release-quality run/public claim is required for the scope.
- [ ] Keep manual work pending until results are supplied and validated; record a
      no-public-performance-claim disposition unless reviewed comparable evidence supports it.

## Priority 10 - Release Scope and Go/No-Go

### [ ] 10.1 Freeze the delivered public surface and guidance

- [ ] Freeze helper signatures, structural errors, mock assertions and examples
      together; verify legacy bulk-map, key and snapshot compatibility.
- [ ] Keep new metadata/configuration out unless explicitly approved and fully
      enforced; no automatic forwarding, propagation, trust or broad casing change.
- [ ] Reconcile implemented behavior with every accepted priority and record any
      narrowed/deferred work without presenting it as verified.
- [ ] Confirm release notes make no unverified mesh, memory or performance claim.

### [ ] 10.2 Assemble immutable release evidence

- [ ] Assemble full tests, strict API, dependency matrix, packaging, consumers,
      AOT/native, lifecycle and targeted benchmark disposition from reviewed source.
- [ ] Record commands, actual counts, clean state, reachable commit, toolchains,
      artifact/report hashes, Central markers, remaining risk and evidence paths.
- [ ] Re-run affected checks after fixes; preserve original failures and distinguish
      uncommitted release-preparation changes from clean final-source evidence.
- [ ] Generate readiness with truthful pending signing/tag/publication steps;
      target-only manifests do not certify unexecuted manual checks.

### [ ] 10.3 Select release scope and candidate version

- [ ] Record an explicit dated go/no-go decision with reviewed commit and scope.
- [ ] Select `4.4.0` only if additive public functionality ships; otherwise select
      a compatible patch/documentation-only disposition or no-go with rationale.
- [ ] On go, align final candidate coordinates and rerun affected readiness,
      generation and release-packaging checks before claiming a release cut.
- [ ] Keep public/API/consumer/benchmark baselines at `4.3.0` until successful
      publication and fresh verification; the snapshot name alone selects nothing.

### [ ] 10.4 Publish, verify, and archive V31

- [ ] Build/sign the reviewed clean final commit/tag; verify signatures, staged
      assembled consumption and generation packaging before deployment.
- [ ] Preserve credentialed signing/publication evidence; a scope GO or unsigned
      local build is not a signing pass.
- [ ] After publication, verify all parent/module attachments and an assembled
      consumer from fresh Central-only repositories before moving baselines.
- [ ] Archive V31 and select the next development coordinate only after verified
      publication; if the chosen path is no-go/no-publication, record that exact
      disposition instead of claiming an unpublished version was released.

## Completion Criteria

- [ ] Evidence distinguishes case mismatch, filtering/redaction and genuine
      context-boundary loss without an unsupported mesh diagnosis.
- [ ] Named access handles absence, multiplicity and malformed input while legacy
      map spelling, string aliases and snapshot restoration remain compatible.
- [ ] Filtering, handoff, auth/cache isolation and terminal cleanup remain correct
      through the tested protocol and feature-composition paths.
- [ ] Mock, consumer, compatibility, AOT/native, lifecycle and documentation checks
      cover final delivered code; performance claims match reviewed evidence.
- [ ] A release or no-go disposition is recorded and the corresponding archive
      path is complete, with no unimplemented public contract represented as shipped.
