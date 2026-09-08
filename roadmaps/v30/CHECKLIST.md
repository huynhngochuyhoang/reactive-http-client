# Reactive HTTP Client - Roadmap V30 Execution Checklist

Companion to [`ROADMAP.md`](ROADMAP.md). Execute priorities in order unless a
confirmed correctness or release blocker requires reordering. Check an item only
after implementation and verification evidence is recorded under its priority.
Adopting this checklist starts V30 execution planning; it does not complete a
priority or select the final release scope.

Keep generated evidence under `target/release-evidence/v30/priority<N>/`.
Record commands, actual test totals, Java/Boot versions, commit, dirty/clean
state, fixture settings, and report paths. Native and release-quality benchmark
evidence additionally require exact source provenance and artifact hashes.
Do not reuse a pre-fix binary or a report from an unreachable intermediate
commit as completion evidence. Preserve partial reports when a command fails.

## Execution Gates

- Priority 2 characterizes active owners before production changes. V29's
  stored-byte bound is not a concurrency bound or proof of a process-memory leak.
- Priority 3 freezes the contract. Keep preparatory representations internal
  until Priorities 4-6 enforce every selected dimension. Do not expose a
  bindable/public setting, generated metadata, or copyable configuration that
  silently passes through an unenforced limit.
- Priority 6 closes the integrated enforcement gate before composition, public
  telemetry, or operations evidence is treated as complete.
- Priority 9 supplies the exported signals used by Priority 11. Internal test
  counters are not substitutes for an operator-visible signal.
- Priority 12.3 provides manual release-benchmark commands and remains open
  until the resulting reports have been reviewed.
- Public baselines stay on `4.2.0`; reactor fixtures stay on `4.3.0-SNAPSHOT`
  until Priority 13 selects a final cut. Publication and baseline movement are
  separate from implementation and require fresh Central verification.

## Required Invariants

| Owner | Scope and reservation | Saturation and release |
|---|---|---|
| Foreground caller | One selected factory/policy; before freezing, serialization, or pre-lookup auth; includes hits and waiters | Reject before preparation at capacity; release the caller's reservation exactly once at its terminal boundary |
| Foreground load | One independent miss or shared source, including all its retries/replays | Join an existing flight without another slot; reject a new load at capacity; release at source terminal, independently of its first caller |
| Hidden refresh | One admitted current-entry refresh, counted separately from foreground loads | Return a still-valid stale hit and skip at capacity; no queue or TTL extension; release at refresh terminal |

Limits apply per selected client factory and policy, not per API, process, or
cluster. APIs sharing a policy share its capacity. The caller limit also bounds
attached waiters; a separate per-key waiter setting is outside this scope.
Absent limits preserve `4.2.0` behavior. Count limits do not estimate retained
heap or impose an implicit foreground timeout.

---

## Priority 1 - Post-`4.2.0` Baseline and V30 Scope Integrity

### [x] 1.1 Align the development and published lanes

- [x] Verify root/modules, benchmarks, native-smoke, and current-consumer
      coordinates are `4.3.0-SNAPSHOT`.
- [x] Keep public dependency snippets, `latest.published.version`, strict API,
      published consumer, and benchmark baselines at `4.2.0`.
- [x] Verify current published-baseline benchmark commands include V29 weighted
      rows; preserve version-specific exclusions only for historical runs.
- [x] Preserve V1-V29 release records and make V30 the sole active execution
      roadmap with an adopted checklist and an unselected final release scope.

### [x] 1.2 Reprove the published baseline

- [x] Resolve the parent POM and starter/test-helper/OTel POM, binary, source,
      and Javadoc artifacts from a previously absent Central-only repository;
      record all 13 hashes and remote markers.
- [x] Run an assembled consumer against published `4.2.0` only; preserve its
      effective POM, classpath, dependency tree, actual totals, and provenance.
- [x] Run strict root and starter-module source/binary comparisons against
      independent fresh `4.2.0` repositories.
- [x] Run published-baseline/API fixture guards for same-version comparison,
      contamination, missing attachments, and inconsistent project versions.

### [x] 1.3 Verify roadmap and readiness state

- [x] Generate readiness with active roadmap `v30`, development
      `4.3.0-SNAPSHOT`, published/API baseline `4.2.0`, and an unselected lane.
- [x] Keep the candidate deferred and unpublished; selecting an execution
      checklist must not imply completed work, a final release, or publication.
- [x] Retain exact active-status checks for V30 and all V1-V29 completion
      checks in the roadmap archive contract.
- [x] Run Maven validation, `DocumentationReleaseArtifactTest`, and
      `git diff --check`; record evidence for this priority.

Evidence executed on 2026-09-07 and reviewed on 2026-09-08 from clean commit
`c5ddccffb60e61a33d8b6c7c3d7dff5275a25c09`, before this checklist-only update:

- Root/module and benchmark versions and both native/current-consumer starter
  coordinates remain `4.3.0-SNAPSHOT`. Public dependency snippets and
  published/API/consumer/benchmark baselines remain `4.2.0`. The existing
  version, benchmark-profile, and archive guards passed without changes:
  current baseline commands include V29 weighted rows, historical exclusions
  remain explicitly selected profiles, and V1-V29 release records are unchanged.
  This is a baseline/profile check, not new performance evidence.
- `scripts/verify-published-release-artifacts.sh 4.2.0` resolved all 13 release
  artifacts from a previously absent Central-only repository. Declared POM and
  embedded JAR versions, source/Javadoc attachments, SHA-256 values, and remote
  markers passed. Evidence is under
  `target/release-evidence/v30/priority1/published-baseline/`.
- `scripts/verify-published-consumer.sh 4.2.0` passed 4 tests with zero failures,
  errors, or skips using only published artifacts. Its effective POMs,
  dependency tree, classpath, Surefire XML, 7 artifact hashes, and clean-source
  provenance are under `target/release-evidence/v30/priority1/published-consumer/`.
  No reactor output directories were present on the consumer classpath.
- Strict root and starter-only `-Papi-compatibility -DskipTests verify` builds
  passed with both source and binary incompatibility failures enabled. They
  used independent, previously absent Central-only repositories named
  `v30-priority1-api-root-4.2.0` and `v30-priority1-api-starter-4.2.0` under
  `target/published-baseline-repositories/`. Reports contain no public API
  delta; 7 root and 2 starter baseline hashes and remote markers are under
  `target/release-evidence/v30/priority1/api-root/` and `api-starter/`.
- `bash scripts/verify-published-baseline-fixtures.sh` passed local
  contamination, mixed-version, missing-attachment, inconsistent project/parent
  POM and embedded JAR version, and root/module self-comparison rejection
  checks. `bash scripts/verify-api-compatibility-fixtures.sh` accepted additive
  and defaulted-annotation changes and rejected source-only checked exceptions,
  removed constructors, nested public methods, and enum constants. Fixture
  reports are retained under this priority's `baseline-fixtures/` and
  `api-fixtures/` directories.
- Generated readiness remains development `4.3.0-SNAPSHOT`, published/API
  baseline `4.2.0`, active roadmap `v30`, and release lane `unselected`. The
  `4.3.0` candidate is deferred and unpublished with no selected final scope;
  release-quality compatibility, consumer, benchmark, AOT, native, and
  publication gates remain pending. The generated JSON and benchmark evidence
  snippet are under `target/release-evidence/v30/priority1/readiness/`.
- `mvn -B -ntp -s .mvn/maven-central-settings.xml validate` passed all four
  reactor modules. The same Maven/settings invocation with
  `-pl reactive-http-client-starter -Dtest=DocumentationReleaseArtifactTest test`
  passed 46 tests with zero failures, errors, or skips. `git diff --check`
  passed. Toolchain: Maven `3.9.9`, GraalVM JDK `25.0.3`, Java `21` compilation
  target, and Spring Boot `4.0.0`.
- Exact commands, stage exit statuses, logs, toolchain, clean-source provenance,
  and reports are under `target/release-evidence/v30/priority1/`. Earlier local
  artifact/consumer evidence and repositories were preserved separately under
  `target/v30-priority1-previous-state/`, not reused as fresh-run evidence.
  The completed runner survived the interruption; all saved artifact hashes
  were rechecked successfully during the 2026-09-08 review.

---

## Priority 2 - Active-Work Characterization

### [x] 2.1 Build deterministic burst workloads

- [x] Cover cache-disabled calls, distinct blocked misses, independent duplicate
      misses, one flight with many callers, slow pre-lookup auth, and many
      simultaneously stale keys.
- [x] Fix payloads, key cardinality, caller count, operation count, policy
      bounds, and observation checkpoints; keep inputs synthetic and bounded.
- [x] Gate preparation, subscriber attachment, server dispatch, and source
      terminal signals explicitly; a delay alone must not prove overlap.
- [x] Exercise `GET` and body-bearing semantic `POST`, count-only and weighted
      policies, and isolated contexts where prior state would bias evidence.

### [x] 2.2 Separate live work from stored responses and memory

- [x] Record preparing/active callers, independent loads, shared flights,
      attached members/waiters, refreshes, and generation owners with precise
      units; do not equate flights with every foreground load.
- [x] Record stored entries/bytes and protocol-aware pool state separately from
      active work, Java heap, direct memory, threads, and process RSS.
- [x] Compare explicit Bulkhead and pool-acquisition limits with their absent
      configurations to identify which stages they actually protect.
- [x] Capture admitted, saturated, traffic-stopped, source-terminal, and
      post-close checkpoints; unavailable measurements remain unknown.

### [x] 2.3 Record the capacity finding

- [x] Classify each observation as capacity exposure, expected caller-owned
      retention, confirmed defect, or inconclusive; attach owner-path evidence.
- [x] Identify work that survives its first caller and work that remains
      application-owned after close; a nonzero RSS is not a leak finding.
- [x] Record the limits of cancellation for non-cooperative application hooks
      and distinguish subscription counts from arbitrary external task counts.
- [x] Keep raw JFR/heap evidence private and target-only; use bounded structural
      checks and reference paths rather than absolute GC/RSS pass thresholds.

Priority 2 evidence (2026-09-08):

- [Active-work characterization](ACTIVE-WORK-CHARACTERIZATION.md) records the
  fixed workload, counter units, owner paths, observations, and limits.
  `ResponseCacheActiveWorkTest` adds 35 gated cases using real invocation,
  auth, codec, Resilience4j, cache, and loopback HTTP/1.1 paths. The source
  base is reachable commit `0b969f089709be67fcf0a1f35d6ad4b16ea60ce1`, with
  test/report additions in the working tree; this is not clean-commit native
  or isolated published-binary evidence. Starter production sources are
  unchanged from `v4.2.0`; no production or V1-V29 files are changed.
- Twelve held distinct misses retain twelve foreground tokens before any
  entry is stored; independent duplicate misses retain twelve tokens but no
  shared flight. Twelve same-key shared callers retain one flight and eleven
  waiters. Eight stale keys retain eight refreshes after their callers finish.
  Bulkhead permits and constrained pool capacity act after all twelve
  pre-lookup auth subscriptions (and POST serializations). These are capacity
  exposures, not evidence of a newly introduced leak.
- The first shared caller can cancel while eleven members keep the source
  alive. Independent loads retain external caller ownership after manager
  close with transport deliberately left open; their eventual terminals
  release tokens without publication. A non-cooperative serializer remains
  executing after caller cancellation until its application gate is released.
  Stored generation bookkeeping, live owners, pool state, and memory domains
  are reported separately. HTTP/2 streams and unavailable memory limits remain
  unknown; there are no GC/RSS pass thresholds or public live-work meters.
- Five final runs of `mvn -B -ntp -pl reactive-http-client-starter
  -Dtest=ResponseCacheActiveWorkTest test` passed 35 cases each
  (175 total, zero failures/errors/skips). Their per-run JSON, Surefire XML,
  text reports, and Maven logs are preserved under
  `target/release-evidence/v30/priority2/final-stress/run-1/` through `run-5/`.
- The final related regression ran `ResponseCacheActiveWorkTest`,
  `ResponseCacheMemoryWorkloadTest`, `ResponseCacheRetentionOwnershipTest`,
  `SemanticReadSingleFlightRefreshContractTest`,
  `BoundedLocalResponseCacheContractTest`, and `DocumentationReleaseArtifactTest`
  with the same Maven invocation and a comma-separated `-Dtest` selection:
  149 tests passed, zero failures/errors/skips. Reports and the exact Maven
  log are preserved under `target/release-evidence/v30/priority2/final-regression/`.
- After the checklist/report update, `DocumentationReleaseArtifactTest` passed
  all 46 tests again. Reactor `mvn -B -ntp validate` passed all four modules;
  tracked and new-file whitespace checks passed. Final logs are
  `target/release-evidence/v30/priority2/documentation-final.log` and
  `target/release-evidence/v30/priority2/validate-final.log`.
- Toolchain: Maven `3.9.9`, GraalVM JDK `25.0.3`, Java `21` compilation target,
  Spring Boot `4.0.0`, Reactor Netty `1.3.0`, and Linux `amd64` with epoll.
  Source copies and hashes are retained under
  `target/release-evidence/v30/priority2/source/`. No JFR or heap dump was
  generated; any follow-up raw capture stays private and target-only.
  Priority 3's explicit admission contract and later release gates remain open.

---

## Priority 3 - Effective Work-Limit and Admission Contract

### [x] 3.1 Freeze configuration and ownership semantics

- [x] Define the smallest optional per-policy contract for positive caller and
      foreground-load limits, plus a positive refresh limit when refresh is
      enabled; freeze names, numeric ranges, and overflow handling.
- [x] Define absence, partial selections, invalid zero/negative values, and a
      refresh limit without refresh selection; no value silently enables work.
- [x] Define capacity sharing across APIs and isolation across policies,
      factories, and pods; document possible hot-key saturation without promising
      tenant fairness or another waiter quota.
- [x] Freeze startup immutability/mutation behavior so changing bounds cannot
      create another live limiter or reset occupied capacity.
- [x] Record when each reservation begins and ends, including synchronous
      callback execution still unwinding after timeout/cancellation; do not
      claim that arbitrary application work stopped from a terminal signal alone.

### [x] 3.2 Resolve one effective selection

- [x] Preserve client/method policy precedence, `@CacheDisabled`, concrete
      inherited methods, `@ApiRef`, and replacement metadata caches.
- [x] Use one normalized decision across validation, runtime, contracts,
      diagnostics, mocks, and AOT without instantiating lazy optional beans.
- [x] Prove omitted limits and inert policy definitions preserve the existing
      path without new work owners, timers, or optional dependencies.
- [x] Keep contract representations/tests internal until complete runtime
      enforcement exists; basic effective-policy export must accompany eventual
      public configuration, not lag behind it.

### [x] 3.3 Define saturation and local errors

- [x] Specify fixed caller-capacity/load-capacity rejection reasons and a
      distinct refresh-capacity skip; no fallback uncached dispatch or queue.
- [x] Define the error's public shape, cache outcome, category, and failure-stage
      behavior without conflating local admission with pool, transport,
      Resilience4j rejection, or response-byte storage bypass.
- [x] Specify zero attempt/dispatch evidence and one terminal callback per
      rejected caller; bound text and exclude keys, values, targets, and identity.
- [x] Place local foreground rejection outside starter loader Retry; document
      that application-side resubscription creates a new admission attempt.

Priority 3 evidence (2026-09-08):

- [Work-limit and admission contract](WORK-LIMIT-ADMISSION-CONTRACT.md)
  freezes the future work.maximum-concurrent-callers/loads/refreshes names,
  integer range 1-1,000,000, all-or-none selection rules, named-policy/factory
  ownership, startup mutation rejection, callback-unwind release boundaries,
  saturation decisions, and structural local errors. This is the internal
  contract gate, not public configuration or production enforcement.
- The test-only `CacheWorkLimitContract` consumes the existing
  `EffectiveCachePolicy.Decision` through the supplied metadata cache; it
  contains no production counters, timers, optional bean lookup, or admission
  implementation. `CacheWorkLimitContractTest` passes 45 cases covering
  normalization/binding overflow, inert/absent selections, refresh conditions,
  concrete inheritance, method/client precedence, `@CacheDisabled`, `@ApiRef`,
  immutable selection/mutation, policy/factory isolation, decision tables,
  and fixed zero-attempt/dispatch rejection facts.
- Existing-path checks exercise the handler/cache manager with work omitted,
  exact per-method exporter parity, no manager/Caffeine inspection for an
  unselected policy, and replacement metadata in AOT and provider snapshots
  without instantiating lazy RetryRegistry/AuthProviderFactory definitions.
  The contract maps runtime, mocks, diagnostics, and AOT to that same
  normalized decision when enforcement lands; no bounded-work export or
  native admission behavior is claimed today.
- Related regression passed 327 tests (264 starter, 63 mock helper), zero
  failures/errors/skips, using `mvn -B -ntp -pl reactive-http-client-test -am`
  with `-Dsurefire.failIfNoSpecifiedTests=false test` and a comma-separated
  `-Dtest` selection of `CacheWorkLimitContractTest`,
  `DeclarativeCachePolicyTest`, `ResponseCacheActiveWorkTest`,
  `ResponseCacheRetentionOwnershipTest`, `ReactiveHttpClientAotSmokeTest`,
  `ReactiveHttpClientDiagnosticsProviderTest`, `EffectiveHttpClientContractExporterTest`,
  `DocumentationReleaseArtifactTest`, `MockReactiveHttpClientTest`, and
  `Boot4MockReactiveHttpClientTest`. The exact log is
  `target/release-evidence/v30/priority3/regression.log`; per-module Surefire
  reports are retained under `target/release-evidence/v30/priority3/regression/`.
- Final contract/documentation verification passed 91 tests (45 contract,
  46 documentation), zero failures/errors/skips, using the starter module
  with `-Dtest=CacheWorkLimitContractTest,DocumentationReleaseArtifactTest`.
  Reactor `mvn -B -ntp validate` passed all four modules; tracked and
  new-file whitespace checks passed. Logs are
  `target/release-evidence/v30/priority3/final-contract-docs.log` and
  `target/release-evidence/v30/priority3/validate.log`.
- Source base: reachable commit `44502219b071a73a37cc0ff7301eafb3d3a68caf`,
  with test-only specification and V30 document additions in the working tree.
  Maven `3.9.9`, GraalVM JDK `25.0.3`, Java `21` compilation target, Boot
  `4.0.0`. Production sources, public properties/types/enums, metadata,
  dependencies, and historical V1-V29 evidence are unchanged.
  Source copies/hashes are under `target/release-evidence/v30/priority3/source/`.
  Priorities 4-6 must enforce the contract and complete 6.3 before public
  binding/basic effective output; later terminal-delivery/native gates remain open.

---

## Priority 4 - Admission Before Request Preparation

### [x] 4.1 Reserve each caller before materialization

- [x] Acquire inside each cold subscription before argument freezing, selected
      body serialization, context snapshots, or pre-lookup authorization.
- [x] Reject N+1 while N calls are deliberately held in preparation and prove
      no serializer, auth provider, customizer, flight, or transport invocation
      occurs for the rejected call.
- [x] Cover repeated subscriptions to one publisher and concurrent different
      APIs sharing a policy; maintain separate capacity for another policy.
- [x] Keep rejection reporting bounded and avoid retaining a full prepared
      request solely to report a local admission error.

### [x] 4.2 Preserve hit authorization and request identity

- [x] Count fresh/stale hits and waiting callers in the caller allowance;
      saturation must not bypass authorization to inspect or return a hit.
- [x] Run admitted calls through finalized-request probes and the existing
      frozen key/body/context contract; no new snapshot can alter wire identity.
- [x] Cover auth failure/empty auth on a warm hit and header/URI mutations from
      classified customizations on `GET` and semantic `POST`.
- [x] Preserve one logical-call budget from subscription through preparation,
      auth, lookup, waiting, and load; do not layer an unattributed timeout.

### [x] 4.3 Release preparation ownership on every terminal path

- [x] Cover success, empty completion, synchronous throw, serialization failure,
      auth error, timeout, and cancellation before source attachment.
- [x] Order release for immediate terminal resubscription; a delayed cleanup
      callback cannot release another subscription's reservation.
- [x] Verify no later dispatch or publication follows preparation cancellation,
      including when an application callback returns after cancellation.
- [x] Prove capacity reuse and released argument/context/auth ownership with
      the manager still open; close must not mask a release defect.

Priority 4 evidence (2026-09-08):

- [Internal caller enforcement](WORK-LIMIT-ADMISSION-CONTRACT.md#priority-4-internal-enforcement)
  is implemented by package-private `CacheCallerAdmission`, owned by the
  cache manager and consumed before the handler's cold preparation pipeline.
  Only the package-private fixture overload can select caller limits today.
  Ordinary construction remains unselected; public binding, the public
  rejection type/category/outcomes, and effective exports remain gated on
  complete caller/load/refresh enforcement in Priorities 5-6 and 6.3.
- `CacheCallerAdmissionContractTest` passes 41 cases: N+1 rejection while
  auth or serializers are gated; no rejected argument/context reads, auth,
  customizations, flight, or transport work; repeated cold subscriptions;
  shared cross-API capacity, different policy/manager isolation, and 64
  competing acquisitions. Rejected terminal records contain no prepared
  body, inbound/request/response headers, URL, status, or attempt evidence.
- Real loopback GET/semantic POST cases use explicitly SAFE Boot/per-client
  customizers. They verify exact JSON bytes, frozen context despite caller
  mutation, final header/URI partitioning, warm-hit auth errors, and existing
  empty-auth behavior. Hits/stale hits/waiters reserve caller capacity;
  detached refresh work has no caller reservation.
- Gated freeze, codec, auth, default-request, filter, and synchronous returned
  publisher subscription frames retain capacity through cancellation until
  exit, with no late dispatch/publication. Success, empty/error terminals,
  immediate repeat/retry, cancellation before attachment, waiter timeout,
  and response-body timeout reuse capacity without delayed cleanup races.
  Timeout checks advance virtual time only after explicit phase entry; the
  production logical-call deadline is armed before entering preparation.
  Weak references to arguments, context, and auth state clear after success
  and cancellation while the cache manager stays open. Nested unbounded
  callers do not inherit another logical call's reservation.
- Six lookup-subscription cases cover cancellation and timeout inside fresh-hit,
  expired-miss, and flight-start frames; capacity remains reserved until the
  synchronous frame exits. Two reporting-setup cases cover non-Map inbound
  context on GET/semantic POST: errors before source subscription release
  capacity, immediate retries do not leak it, and rejection cannot release
  another admitted caller's reservation. The reporting leak was reproduced
  before the fix in `target/release-evidence/v30/priority4/reporting-cleanup/reproduced.log`.
- Eight asynchronous filter/auth/exchange-frame cases cover cancellation and
  timeout: terminated continuations cannot call `next.exchange`, while an
  already-entered exchange construction/subscription frame retains capacity
  until exit. Two handler cases prove the final identity probe cannot run
  after termination, create a flight, publish, or affect a replacement caller.
  Six initial reproductions failed before guarding the continuation boundary;
  log: `target/release-evidence/v30/priority4/async-continuations/reproduced.log`.
- Final related regression: 409 tests (346 starter, 63 mock helper), zero
  failures/errors/skips, using `mvn -B -ntp -pl reactive-http-client-test -am`
  with `-Dsurefire.failIfNoSpecifiedTests=false test` and these `-Dtest` names:
  `CacheCallerAdmissionContractTest`, `CacheWorkLimitContractTest`,
  `ResponseCacheActiveWorkTest`, `ResponseCacheRetentionOwnershipTest`,
  `BoundedLocalResponseCacheContractTest`, `CacheKeyContractTest`,
  `LogicalCallTimeoutBudgetContractTest`, `LocalResponseCacheObservabilityTest`,
  `SemanticReadLocalCacheContractTest`, `SemanticReadSingleFlightRefreshContractTest`,
  `MockReactiveHttpClientTest`, `Boot4MockReactiveHttpClientTest`,
  `DocumentationReleaseArtifactTest`, and `ReactiveHttpClientAotSmokeTest`.
  Log: `target/release-evidence/v30/priority4/async-continuations/regression.log`;
  matching Surefire XML/text:
  `target/release-evidence/v30/priority4/async-continuations/regression/`.
  These reruns validate the lookup subscription guard, deadline ordering,
  reporting-setup cleanup, and asynchronous continuation guard together,
  superseding the earlier final-run
  evidence rather than reusing its reports.
- Five additional isolated runs of
  `mvn -B -ntp -pl reactive-http-client-starter -Dtest=CacheCallerAdmissionContractTest test`
  passed all 41 cases each (205 executions), zero failures/errors/skips.
  Logs/reports are under
  `target/release-evidence/v30/priority4/async-continuations/stress-1` through `stress-5`
  (logs use the `.log` suffix). Reactor `mvn -B -ntp validate` and
  `git diff --check` passed; the validate log is
  `target/release-evidence/v30/priority4/async-continuations/validate.log`.
- Source base: reachable commit `3c31ea6610dfa68ce37835edde52d5473518759b`
  plus the working-tree continuation guard, ten regression cases, and the
  contract/evidence update. Maven
  `3.9.9`, GraalVM JDK `25.0.3`, Java `21` target, Boot `4.0.0`,
  reactor `4.3.0-SNAPSHOT`, published/API baseline `4.2.0`.
  Source copies/hashes and the working-tree patch are under
  `target/release-evidence/v30/priority4/async-continuations/source/`.
  No public configuration, dependency, historical roadmap, native-build,
  or benchmark evidence is changed or claimed by this priority.

---

## Priority 5 - Foreground Load and Single-Flight Capacity

### [x] 5.1 Bound every foreground source

- [x] Reserve before loader assembly for independent misses and new shared
      flights; count one source across retry/backoff, redirects, auth replay,
      decoding, and cache publication.
- [x] Return one local error for a new miss at load capacity without invoking
      the loader or creating a deferred fallback request.
- [x] Keep hits independent of load capacity after caller admission; same-key
      independent misses each require their own load slot.
- [x] Test successful storage, unknown/over-budget byte bypass, empty/error
      outcomes, and cancellation releasing their source slot exactly once.

### [x] 5.2 Make lookup, join, and reservation consistent

- [x] Join a current flight at load saturation when caller capacity is available;
      the waiter does not acquire another load reservation.
- [x] Recheck a stale miss decision after concurrent cache publication before
      installing another flight or rejecting for load saturation.
- [x] Make last-member cancellation, flight removal, and delayed attachment
      unable to reconnect an abandoned untracked source.
- [x] Clean every rejected provisional token/member/generation and verify active
      load counts never exceed the policy maximum under many-key contention.

### [x] 5.3 Separate caller and source terminal ownership

- [x] Let the first caller timeout/cancel while a waiter remains; release its
      caller state while the shared source retains exactly one load slot.
- [x] Release one cancelled waiter independently and cancel the shared source
      when no interested caller remains, including before source attachment.
- [x] Prevent transport evidence and later retry hooks from being written into
      an already-terminal caller or a coalesced waiter's local terminal record.
- [x] Test immediate completion/error and immediate resubscription without
      stale release callbacks or duplicate source starts.

### Implementation and Verification (2026-09-08)

- Internal `CacheLoadAdmission` reserves one foreground source per policy name
  before loader assembly; `CacheWorkAdmission` shares the existing frame-aware
  reservation implementation with the independent caller gate. Selection is
  package-private fixture input only. Public properties, exceptions/outcomes,
  normalization, and effective output remain gated by Priority 6.3.
- Lookup/recheck, flight joining, load reservation, and bounded publication use
  the flight registry's coordination boundary. Rejected provisional tokens are
  finished without loader assembly. Hits and existing-flight joins remain
  available at load saturation; independent misses each reserve a slot.
- Source ownership covers assembly, subscription, retry construction/backoff,
  auth replay, redirect dispatches, decoding, metadata, and publication. Terminal
  cleanup precedes delivery; cancellation retains occupied capacity until an
  entered source/cancellation frame exits. Independent external loads can
  survive manager close and retain their slot until their own terminal signal.
- Flight members attach cancellation before source startup. Detached publishers
  cannot restart a removed source; late waiters cannot inherit a departed
  diagnostic owner's transport facts. Later source retries do not emit hooks
  for the terminated caller. The cancelled-lookup fixture now proves a miss
  cannot attach a source, rather than requiring a subscribe-then-cancel cycle.
- `CacheLoadAdmissionContractTest`: 29 cases, including both independent and
  shared modes, all terminal/byte-bypass outcomes, four blocked frame types,
  delayed attachment, publication-before-saturation recheck, 64-key contention,
  policy/factory isolation, immediate repeat/retry, close ownership, and stale-hit
  refresh isolation at foreground saturation.
  `CacheCallerAdmissionContractTest`: 44 cases, including three added
  handler retry/deadline/source-frame cases. `BoundedLocalResponseCacheContractTest`:
  51 cases, including bounded and legacy redirect and 401/retry paths.
- Red evidence: five initial capacity cases failed before enforcement
  (`target/release-evidence/v30/priority5/reproduced.log`); the retry-hook frame
  reproduced premature release before its guard
  (`target/release-evidence/v30/priority5/retry-frame-reproduced.log`).
- Final related regression passed 493 tests (430 starter, 63 mock helper), zero
  failures/errors/skips. Command: `mvn -B -ntp -pl reactive-http-client-test -am`
  with `-Dsurefire.failIfNoSpecifiedTests=false test` and `-Dtest=`:
  `CacheLoadAdmissionContractTest,CacheCallerAdmissionContractTest,CacheWorkLimitContractTest,ResponseCacheActiveWorkTest,ResponseCacheRetentionOwnershipTest,BoundedLocalResponseCacheContractTest,CacheKeyContractTest,LogicalCallTimeoutBudgetContractTest,LocalResponseCacheObservabilityTest,SemanticReadLocalCacheContractTest,SemanticReadSingleFlightRefreshContractTest,MockReactiveHttpClientTest,Boot4MockReactiveHttpClientTest,DocumentationReleaseArtifactTest,ReactiveHttpClientAotSmokeTest,SubscriptionReportingStateTest,SubscriptionLocalReportingStateTest,ExchangeLogSubscriptionAttemptCountTest,ResilienceOperatorCompositionContractTest,DiagnosticContextContractTest,OutboundAuthFilterTest`.
  Log: `target/release-evidence/v30/priority5/final-regression.log`;
  matching XML/text reports: `target/release-evidence/v30/priority5/final-regression/`.
- Five isolated runs of `mvn -B -ntp -pl reactive-http-client-starter`
  `-Dtest=CacheLoadAdmissionContractTest,CacheCallerAdmissionContractTest test`
  passed 73 cases each (365 executions), zero failures/errors/skips.
  Logs and exact XML/text reports: `target/release-evidence/v30/priority5/final-stress-1`
  through `final-stress-5` (logs append `.log`). These final runs include the
  stale-hit cases and supersede the earlier pre-final reports.
- Complete starter/mock verification: `mvn -B -ntp -pl reactive-http-client-test -am test`
  passed 1,515 tests (1,450 starter, 65 mock helper), zero failures/errors/skips.
  Log: `target/release-evidence/v30/priority5/final-complete-tests.log`;
  XML/text reports: `target/release-evidence/v30/priority5/final-complete-tests/`.
  All four reactor modules passed `mvn -B -ntp validate`
  (`target/release-evidence/v30/priority5/validate.log`).
- Source base: reachable commit `4e4b7149d8c819404915fb51e8bfe63b2866cc0c`
  plus this working-tree implementation/tests/contract update. Maven `3.9.9`,
  GraalVM JDK `25.0.3`, Java `21` target, Boot `4.0.0`,
  reactor `4.3.0-SNAPSHOT`, published/API baseline `4.2.0`.
  Source copies/hashes, the reachable base, and the complete working-tree patch
  (including new files) are preserved under
  `target/release-evidence/v30/priority5/source/`. Final documentation verification
  is recorded in `target/release-evidence/v30/priority5/documentation.log`;
  `git diff --check` output is preserved in `priority5/diff-check.log`.
  No native, benchmark, public API, dependency, or historical-roadmap evidence
  is changed or claimed by this priority.

---

## Priority 6 - Refresh Capacity and Foreground Isolation

### [ ] 6.1 Admit only current-entry refresh work

- [ ] Acquire separate refresh capacity before hidden preparation/loader
      assembly and combine it with generation checks and duplicate suppression.
- [ ] Start at most one refresh for the current entry and no more than the
      configured refresh maximum across all APIs sharing the policy.
- [ ] Release provisional state when expiry, eviction, close, or another refresh
      wins before source subscription attaches.
- [ ] Retain the earlier of refresh timeout and hard expiry; work limits do not
      activate a scheduler, recurring refresh, or a new implicit timeout.

### [ ] 6.2 Skip saturated refresh without extending freshness

- [ ] Return the authorized still-valid stale value when refresh capacity is
      full; do not queue a trigger or retain its context for later execution.
- [ ] Leave stored value, byte weight, publication age, and hard-expiry deadline
      unchanged; a skip is not a terminal refresh load.
- [ ] Allow a later access to attempt refresh after capacity is released; test
      failure, empty completion, hard expiry, and cancellation paths.
- [ ] Prove foreground slots remain available at refresh saturation while
      documenting contention in separately shared pools/auth/resilience services.

### [ ] 6.3 Complete the public enforcement gate

- [ ] Prove caller/load/refresh limits together under stale-hit, expired-miss,
      single-flight, independent-load, and shutdown transitions.
- [ ] Expose validated properties and basic effective-policy output only once
      every selected bound is enforced; update generated metadata concurrently.
- [ ] Reject unsupported partial or mutated selections consistently in startup,
      invocation, mock, diagnostic-contract, and AOT validation paths.
- [ ] Verify existing policies without work limits retain published behavior;
      no work limit enables refresh, caching, or metrics implicitly.
- [ ] Record focused integration evidence before proceeding to public telemetry
      and operations claims.

---

## Priority 7 - Resilience, Auth, Redirect, and Deadline Composition

### [ ] 7.1 Preserve resilience selection and attempt counts

- [ ] Preserve explicit operator selection/order and unsafe retry/body-repeatability
      rules; cache work limits cannot imply an operator or replay permission.
- [ ] Verify local reservation rejection does not subscribe to the business
      loader's Retry, CircuitBreaker, Bulkhead, or RateLimiter pipeline.
- [ ] Verify open-circuit and other real guard rejections release admitted cache
      reservations without inventing transport evidence.
- [ ] Retain one source reservation through retry delays and hidden dispatches;
      distinguish loader terminal work from downstream request counts.

### [ ] 7.2 Preserve auth, redirect, and key isolation

- [ ] Cover warm-hit auth rejection, `401` invalidation, refreshed identity, and
      pre-resolved auth consumption across outer retries.
- [ ] Keep auth-visible bytes isolated from serialized body identity; request
      changes after retry/redirect must obey publication revalidation.
- [ ] Exercise body-preserving redirect and semantic `POST` without duplicate
      subscriptions introduced by admission bookkeeping.
- [ ] Assert the final observer/lifecycle/log error, URL, status, headers, stage,
      and dispatch evidence; do not rely on only downstream exception assertions.

### [ ] 7.3 Preserve independent deadlines

- [ ] Test early waiter timeout, first-caller timeout with a live later waiter,
      timeout during Retry backoff, and timeout after response headers/body start.
- [ ] Keep logical deadlines per caller and request timeouts inside the source;
      a first-caller deadline cannot terminate a source still owned by others.
- [ ] Verify timeout/cancellation releases only its owner's capacity and records
      the correct timeout or cancellation terminal event exactly once.
- [ ] Prove capacity remains occupied by deliberately hung admitted work until
      its actual terminal boundary; document required application timeout choices.

---

## Priority 8 - Cancellation, Eviction, and Shutdown Ownership

### [ ] 8.1 Stress ownership invariants

- [ ] Assert nonnegative current counts at or below each configured maximum at
      synchronized checkpoints under both same-key and many-key contention.
- [ ] Cover every terminal type, synchronous assembly exceptions, cancellation
      before attachment, immediate retries, and simultaneous terminal signals.
- [ ] Count acquisitions, releases, and terminal callbacks rather than retaining
      only the last observed record; reconcile counts with active owners.
- [ ] Prove rejected/skipped work retains no flight, waiter, key token, task, or
      loader closure and cannot repopulate a cache after invalidation.

### [ ] 8.2 Prove collection independently of storage cleanup

- [ ] Use bounded reference-queue/weak-reference evidence for caller context,
      arguments, prepared bytes, auth state, callbacks, and source state.
- [ ] Verify detached callers become collectible while another caller keeps
      a flight alive, and rejected callers become collectible while saturated
      admitted work remains active.
- [ ] Verify explicit eviction releases ordinary entries before manager close;
      a still-running independent load retains its own slot until terminal.
- [ ] Keep diagnostic GC a test aid only; do not infer heap ownership from RSS
      or add runtime GC, weak-cache, or heap-walking behavior.

### [ ] 8.3 Preserve factory lifecycle boundaries

- [ ] Atomically stop new reservations during close; no race can create a new
      live cache, limiter, flight, or refresh afterward.
- [ ] Terminate registered shared/refresh work within the established shutdown
      bound; test with normal request deadlines beyond the observation window.
- [ ] Preserve independent caller-owned loads after manager close until their
      own terminal boundary, while invalidating all late publication rights.
- [ ] Verify late releases from a closed manager cannot affect a recreated
      factory's capacity, entries, diagnostics, or metric registrations.
- [ ] Record before-close and post-close owner evidence with explicit absent
      meter semantics after deregistration.

---

## Priority 9 - Live Metrics and Terminal Diagnostics

### [ ] 9.1 Freeze and implement bounded work telemetry

- [ ] Define current/maximum caller, foreground-load, and refresh count meters
      for policies selecting limits under existing explicit cache observability.
- [ ] Define fixed local-rejection and refresh-skip reasons; preserve existing
      meter names/tag sets and terminal caller/load/refresh meanings.
- [ ] Specify scopes and overlapping counts so operators cannot add callers,
      flights, and loads as disjoint work or confuse loads with wire dispatch.
- [ ] Test cache disabled, limits absent, cache metrics disabled, master
      observability disabled, missing MeterRegistry, and enabled zero series.
- [ ] Keep skips out of refresh terminal totals and rejection out of downstream
      request timers/health; do not export keys, request variants, or identities.

### [ ] 9.2 Preserve terminal observer and tracing parity

- [ ] Deliver one structural local-rejection event through enabled observer,
      lifecycle, exchange-log, and OTel surfaces, including compatibility APIs.
- [ ] Preserve zero attempts/dispatch and cleared response evidence for local
      rejection without losing configuration-enabled cache outcomes when no
      MeterRegistry bean exists.
- [ ] Keep shared-source evidence separate from waiter outcomes and hidden
      refresh diagnostics; do not create a detached refresh span.
- [ ] Verify downstream health excludes all cache-served and local-rejection
      outcomes without diluting real downstream failures.

### [ ] 9.3 Extend diagnostics schema V1 additively

- [ ] Export normalized work-limit selections and bounded runtime counts with
      distinct absent, unknown, mixed-policy, and closed interpretations.
- [ ] Preserve summary-only/replacement-factory nulls and inspect only already
      created cache owners; do not initialize lazy components to prove a limit.
- [ ] Define per-policy facts or explicitly labeled aggregates without exposing
      prohibited key, tenant, request, or response material.
- [ ] Validate counts against their configured bounds when known, list limits,
      UTF-16 text bounds, and rendered UTF-8 byte bounds on map/JSON/Markdown paths.

### [ ] 9.4 Verify metric ownership across live factories

- [ ] Coordinate owners sharing a registry and identical tags; define consistent
      aggregation of current counts, maxima, and cumulative history.
- [ ] Prove gauge suppliers survive GC and closing one owner leaves the other
      live owners' meters accurate and registered.
- [ ] Remove meters at the last owner; reject late registration/increments from
      closed owners and test context restart with the registry kept alive.
- [ ] Record tests for unweighted and weighted policies and differing limits
      during overlapping factory replacement.

---

## Priority 10 - Mock, Consumer, AOT, and Native Parity

### [ ] 10.1 Extend deterministic mock ownership controls

- [ ] Provide bounded admission/release snapshots and gates through supported
      test-helper APIs without exporting cache keys or production internals.
- [ ] Cover real-time and deterministic-time mocks, published constructors,
      custom auth/appliers, and cleanup after failed builder validation.
- [ ] Preserve cumulative terminal/rejection/skip evidence after close while
      distinguishing unavailable live state from active work still externally owned.
- [ ] Verify unselected cache/work features do not require optional runtime
      infrastructure or create a cache manager.

### [ ] 10.2 Exercise assembled consumer parity

- [ ] Add consumer coverage for selected `GET` and semantic `POST`, count-only
      and weighted storage, caller/load saturation, single flight, and refresh.
- [ ] Retain a cache-disabled assembled consumer with no Caffeine on its classpath.
- [ ] Verify existing `4.2.0` consumer/test-helper usage with no work limits;
      keep current reactor evidence separate from the published baseline.
- [ ] Preserve fresh per-stage Surefire/provenance artifacts on verifier failure
      without copying stale reports from an unstarted later stage.

### [ ] 10.3 Verify AOT and application overrides

- [ ] Validate effective work policies with configured properties and replacement
      metadata beans, including primary and factory-method registrations.
- [ ] Keep foreign factory definitions outside starter-only grammar and retain
      unknown lazy diagnostics without instantiation.
- [ ] Add only required hints; do not traverse arbitrary request object graphs
      or introduce record/generic recursion for capacity accounting.
- [ ] Exercise valid/invalid and selected/unselected policies through startup,
      AOT, and generated effective-contract tests.

### [ ] 10.4 Record native and shutdown evidence

- [ ] Extend smoke with gated caller/load saturation, refresh skip, released-slot
      reuse, independent caller deadlines, and factory close.
- [ ] Count every server request, including rejected/unmatched routes, and
      synchronize no-dispatch assertions against delayed event-loop work.
- [ ] Compile and run from one clean reachable commit after all fixture fixes;
      record Java/Boot/GraalVM versions, commands, binary SHA-256, and output.
- [ ] Preserve the existing shutdown observation bound and do not let normal
      request/acquire expiry satisfy disposal assertions.
- [ ] Leave native completion open whenever the binary predates the tested
      fixture or runtime revision.

---

## Priority 11 - Operations and Support-Bundle Evidence

### [ ] 11.1 Publish practical saturation and recovery guidance

- [ ] Explain entry, decoded-byte, caller, load, refresh, pool, and resilience
      limits with their scopes; selected limits require explicit endpoint-owner
      sizing and do not guarantee heap/RSS or cluster capacity.
- [ ] Document caller rejection even on warm hits, load rejection with no
      fallback dispatch, refresh skip, and ordinary admission after hard expiry.
- [ ] Document logical/request deadlines and caller resubscription choices;
      the starter does not queue or automatically retry local overload.
- [ ] Build recipes from Priority 9 exports, preserving instance/target labels,
      zero-versus-absent series, and separate foreground/refresh saturation.

### [ ] 11.2 Add a coherent sanitized support fixture

- [ ] Capture bounded client/process identifiers, API-to-policy mapping, selected
      limits, metrics selection, and one timestamped capture window.
- [ ] Include live caller/load/refresh samples and terminal/rejection/skip deltas
      at matching pre-close boundaries; state overlapping units explicitly.
- [ ] Reconcile successes, failures, cancellations, rejected work, skipped
      refreshes, and active owners without equating window totals to instantaneous
      occupancy or omitted counters to zero.
- [ ] Tie entry/byte occupancy, TTL/refresh timing, connection/stream gauges,
      factory start/close, and meter ownership to consistent checkpoints.
- [ ] Include one structural affected-caller terminal record without keys,
      payloads, headers, targets, identity, or arbitrary exception messages.

### [ ] 11.3 Preserve capture and schema safeguards

- [ ] Version-scope new fields while accepting valid published `4.2.0` captures;
      preserve documented unknown/null facts and optional-field boundaries.
- [ ] Keep downloads private, byte/time bounded, and quarantined; require
      successful transfer, acceptable HTTP status, and exactly one JSON document
      before publishing sanitized endpoint evidence.
- [ ] Validate leaf types, numeric/string/list bounds, fixed reasons, and
      configured/current-count relationships before retaining values.
- [ ] Add negative fixture cases for sensitive field names, embedded request
      lines/targets, arbitrary URI schemes, identities, and contradictory timing
      or accounting; avoid text-coercion assertions for numeric fields.
- [ ] Run documentation, metadata, local-link, placeholder, and fixture guards
      against the actual copyable examples and capture path.

---

## Priority 12 - Performance and Allocation Evidence

### [ ] 12.1 Extend production-representative benchmarks

- [ ] Include cache-disabled invocation, existing policies without limits,
      selected-limit hits/misses, and weighted publication.
- [ ] Add caller/load rejection, same-key join, refresh start/skip, release/reuse,
      and contended admission rows with bounded workload dimensions.
- [ ] Exercise production API names, caller/load reporting states, and metrics
      selection in metered rows rather than convenience defaults.
- [ ] Gate subscribers and server responses so a supposed waiter cannot become
      a hit unnoticed; assert workload identity and terminal ownership counts.
- [ ] Keep published `4.2.0` rows comparable; label genuinely new rows as lacking
      a baseline instead of silently dropping required results.

### [ ] 12.2 Separate allocation from retained ownership

- [ ] Inspect disabled/unselected paths for new state or work caused by the
      optional feature; record any justified overhead explicitly.
- [ ] Measure transient reservation/rejection allocation separately from retained
      request/source graphs and cache entry occupancy.
- [ ] Prove repeated saturation does not accumulate rejected owners or pending
      tasks, and that admission uses no blocking waits or unbounded scans.
- [ ] Record bounded JFR/retention evidence and explain measurement limits;
      no byte/count estimate is promoted as exact process-memory sizing.

### [ ] 12.3 Prepare and review manual release benchmarks

- [ ] Pass benchmark packaging, harness tests, and smoke/discovery coverage before
      providing release-run commands.
- [ ] Supply exact clean-commit commands for current, fresh published `4.2.0`,
      and comparison/report generation, with required profiles, output paths,
      machine/toolchain metadata, and expected scenario counts.
- [ ] Hand release-quality execution to the user when requested and leave this
      item open until both runs and comparison artifacts are available.
- [ ] Review row coverage, gates, commit cleanliness, hashes, units, allocation,
      and comparability; a benchmark-path correction invalidates earlier numbers.
- [ ] Record either supported versioned performance evidence or an explicit
      no-public-performance-claim disposition; smoke alone cannot complete it.

---

## Priority 13 - Public API, Documentation, and Release Go/No-Go

### [ ] 13.1 Freeze the supported surface and guidance

- [ ] Freeze additive properties, local-admission error/outcome, mock helpers,
      effective contracts, diagnostics, and metric schemas after enforcement.
- [ ] Update metadata/reference generation, caching, observability, timeouts,
      resilience, test-helper, native, operations, support, and migration guides
      with one vocabulary and copyable startup-valid examples.
- [ ] Preserve published `4.2.0` defaults and existing source/binary contracts;
      any incompatible change requires explicit deferral or a revised release lane.
- [ ] Remove placeholder/unenforced public settings and keep the new capacity
      behavior explicitly selected and distinguishable from storage admission.

### [ ] 13.2 Assemble immutable release evidence

- [ ] Pass the complete reactor, package/generation guards, supported dependency
      matrix, strict root/module API checks, current/published consumers,
      AOT/native, shutdown, and documentation contracts from a reviewed clean commit.
- [ ] Preserve source- and binary-incompatibility failures and isolated report
      provenance for each supported matrix row; keep evidence after failures.
- [ ] Include Priority 12's reviewed benchmark disposition and rerun smoke on the
      final source; do not relabel stale/manual evidence as current.
- [ ] Record commands, actual totals, toolchains, clean-tree state, hashes,
      Central markers, reachable commits, remaining risks, and evidence paths.
- [ ] Verify generated readiness names unresolved release steps without claiming
      publication; target-only evidence remains uncommitted unless a sanitized
      version-matched report is deliberately promoted.

### [ ] 13.3 Select release scope and candidate version

- [ ] Record one explicit go/no-go decision with date, reviewed commit, scope,
      benchmark disposition, and remaining risk.
- [ ] Select `4.3.0` only for the enforced additive opt-in work-bound contract;
      otherwise document a compatible patch scope or a no-go with deferred items.
- [ ] On go, prepare version-matched final artifacts and rerun release packaging,
      generation, signing, and readiness checks before publication.
- [ ] Keep public/API/consumer/benchmark baselines on `4.2.0` until the new
      published artifacts have passed Central verification.

### [ ] 13.4 Publish, verify, and archive V30

- [ ] Publish only from the reviewed clean final commit/tag with successful
      signing and package evidence.
- [ ] Resolve all parent/module POM/JAR/source/Javadoc artifacts from fresh
      Central-only repositories and verify hashes, remote markers, and versions.
- [ ] Run a published assembled consumer before moving public snippets and
      API/consumer/benchmark baselines to the verified release.
- [ ] Archive V30, update exact roadmap/readiness status, and select the next
      snapshot only after publication evidence; on no-go, record the disposition
      without marking an unpublished version released.

---

## Completion Criteria

- [ ] Active-work characterization explains the protected owners without claiming
      that stored bytes or process RSS prove a complete memory bound.
- [ ] Selected caller/load/refresh limits are enforced before their owned work;
      APIs share policy capacity and omitted limits preserve published behavior.
- [ ] Saturation causes one local foreground error or a non-queued refresh skip,
      with no hidden dispatch, stale owner, or TTL extension.
- [ ] Cancellation, deadlines, retries, auth, redirects, eviction, and shutdown
      preserve one release per owner and independent caller/source lifetimes.
- [ ] Metrics, terminal diagnostics, health, and support evidence are bounded,
      truthful, explicitly selected, and free of request/response/key/identity data.
- [ ] Mock, consumer, source/binary API, AOT/native, lifecycle, performance, and
      documentation evidence covers the final reviewed source.
- [ ] A go/no-go decision is recorded and the selected publication/archive path
      is complete; a no-go leaves no misleading public configuration behind.
