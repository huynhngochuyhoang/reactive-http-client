# Reactive HTTP Client - Roadmap V29 Execution Checklist

Companion to [`ROADMAP.md`](ROADMAP.md). Execute priorities in order unless a
confirmed correctness or release blocker requires reordering. Check an item only
after implementation and verification evidence is recorded under that priority.
Generated evidence belongs under `target/release-evidence/v29/` unless a
promoted, versioned artifact is explicitly required.

V29 begins as a diagnosis and ownership program, not as a commitment to a public
cache-weight API. Priority 4 is the decision gate. Do not add public weight
configuration, SPI, metrics, or diagnostics before that gate records a defensible
unit and a go decision. A no-go at that gate is a valid outcome; continue with
proven retention fixes and record weighted-admission work as explicitly deferred.

---

## Priority 1 - Post-`4.1.0` Baseline and V29 Scope Integrity

### [x] 1.1 Align development and published lanes

- [x] Keep root/module, benchmark, native-smoke, and current-consumer coordinates
      on `4.2.0-SNAPSHOT`.
- [x] Keep README, quick-start, and other public dependency snippets on published
      `4.1.0`; do not advertise the snapshot to consumers.
- [x] Keep `latest.published.version`, strict API compatibility, published
      consumer, and published benchmark baselines on `4.1.0`.
- [x] Keep V1-V28 as completed release records and V29 as the only active
      execution roadmap.
- [x] Keep `4.2.0` as a candidate direction only; generated readiness must report
      no selected release scope while the memory investigation is unresolved.

### [x] 1.2 Reprove the published `4.1.0` baseline

- [x] Resolve the parent POM plus starter, test-helper, and OTel POM/JAR/source/
      Javadoc artifacts from a previously absent Central-only repository.
- [x] Require Maven Central remote markers and record SHA-256 values for all 13
      release artifacts.
- [x] Run the assembled Boot 4 consumer using only published `4.1.0` artifacts.
- [x] Run strict root and starter-module japicmp against separate fresh `4.1.0`
      repositories and preserve binary and source failure behavior.
- [x] Run API and published-baseline fixtures for local contamination, mixed
      versions, missing attachments, mismatched POM/JAR versions, and
      self-comparison.

### [x] 1.3 Keep generated readiness honest

- [x] Report `4.2.0-SNAPSHOT` as development and `4.1.0` as the latest
      published/API/consumer/benchmark baseline.
- [x] Report V29 as active with an unselected release lane and a deferred,
      unpublished `4.2.0` candidate.
- [x] Keep compatibility, consumer, benchmark, AOT, native, publication, and
      release-scope work pending until their evidence exists.
- [x] Update the roadmap archive consistency guard for the V29 checklist without
      weakening V1-V28 completion checks.
- [x] Run `DocumentationReleaseArtifactTest`, Maven validation, and
      `git diff --check`; record commands, totals, commit state, and evidence
      paths under this priority.

Evidence recorded on 2026-08-30 from clean commit
`fc30a7f3a423b3d1cb387519e2cbf285377ea33d` before this checklist-only update:

- Root, starter, test-helper, OTel, benchmark, native-smoke, and current-consumer
  coordinates resolve to `4.2.0-SNAPSHOT`. README and quick-start dependency
  snippets remain on published `4.1.0`; `latest.published.version`, strict API,
  published-consumer, and benchmark baselines also remain `4.1.0`. The roadmap
  guard retains V1-V28 as completed records and V29 as the sole active roadmap.
- A previously absent Central-only repository resolved the parent POM and the
  starter, test-helper, and OTel POM/JAR/source/Javadoc artifacts. Central remote
  markers, declared/embedded versions, and all 13 SHA-256 records passed under
  `target/release-evidence/v29/priority1/published-baseline/`.
- `scripts/verify-published-consumer.sh 4.1.0` passed 4 tests with no failures,
  errors, or skips using published artifacts only. Effective POMs, dependency
  tree, classpath, Surefire reports, clean-commit provenance, and 7 artifact
  hashes are under `target/release-evidence/v29/priority1/published-consumer/`.
- Strict root and starter-only `api-compatibility` builds passed against the
  separate `v29-priority1-api-root-4.1.0` and
  `v29-priority1-api-starter-4.1.0` repositories. Reports plus 7 root and 2
  starter provenance hashes are under
  `target/release-evidence/v29/priority1/api-root/` and `api-starter/`.
  `scripts/verify-api-compatibility-fixtures.sh` proved source-only and binary
  incompatibilities still fail strict mode.
- `scripts/verify-published-baseline-fixtures.sh` rejected local-only artifacts,
  mixed candidate versions, missing source/Javadoc attachments, mismatched
  project/parent POM and embedded JAR versions, and root/module self-comparison.
- Generated readiness records `4.2.0-SNAPSHOT` development, published/API/
  consumer/benchmark baseline `4.1.0`, active roadmap `v29`, release lane
  `unselected`, and an unpublished, deferred `4.2.0` candidate with no selected
  scope. Release-scope, compatibility, consumer, benchmark, AOT, native, and
  publication work remain pending; the generated JSON and benchmark snippet are
  under `target/release-evidence/v29/priority1/readiness/`.
- `mvn -B -ntp -pl reactive-http-client-starter
  -Dtest=DocumentationReleaseArtifactTest test` passed 44 tests with no failures,
  errors, or skips. `mvn -B -ntp -s .mvn/maven-central-settings.xml validate`
  passed all four reactor modules, and `git diff --check` passed.

---

## Priority 2 - Production Memory Characterization

### [x] 2.1 Build a deterministic workload

- [x] Add a loopback fixture that can run cache-disabled, cold miss, warm hit,
      maximum-size pressure, TTL expiry, explicit eviction, single flight,
      refresh, cancellation, and factory-close scenarios.
- [x] Use fixed payload shapes, key cardinality, concurrency, warmup, operation
      count, and observation checkpoints so scenarios are comparable.
- [x] Isolate scenarios in fresh application contexts or forked JVMs where prior
      cache, allocator, or class-loading state would contaminate the result.
- [x] Make every hidden load, waiter, refresh, server dispatch, entry, and factory
      lifecycle observable through bounded structural counters; do not use sleeps
      as the only synchronization.
- [x] Keep request and response material synthetic and sanitized in all recorded
      evidence.

Evidence recorded on 2026-08-30:

- `ResponseCacheMemoryWorkload` runs the original ten scenarios against a fresh
  loopback server, application context, connection provider, cache manager,
  refresh scheduler, and factory owner per scenario. Priority 2.3 adds the
  duplicate-miss scenario under the same isolation contract. It fixes a 4 KiB synthetic byte-array
  payload, 8 keys, concurrency 8, 2 warmup calls, 8 measured calls, a 1 second
  TTL, and named structural checkpoints. Capacity pressure alone lowers maximum
  size from 8 to 4.
- Response gates and bounded latches make the shared load, seven coalesced
  waiters, hidden refresh, cancellation, and factory-close transitions
  deterministic. `LocalResponseCacheManager.WorkloadSnapshot` records entries,
  evictions, active loads, waiter count, refreshes, and closed state alongside
  caller/load/server and context/factory/server lifecycle counters. Ticker
  advancement drives expiry and refresh; no scenario uses a sleep as its proof.
- `mvn -B -ntp -pl reactive-http-client-starter
  -Dtest=ResponseCacheMemoryWorkloadTest,BoundedLocalResponseCacheContractTest,SemanticReadSingleFlightRefreshContractTest,LocalResponseCacheObservabilityTest
  test` passed 63 tests with no failures, errors, or skips. The workload also
  passed two standalone executions. Sanitized structural output is under
  `target/release-evidence/v29/priority2/deterministic-workload.properties` and
  contains no request target, cache key, authorization material, or payload.

### [x] 2.2 Separate memory domains

- [x] Capture Java heap usage, live-set evidence, direct-buffer usage, thread
      count, connection-provider resources, cache entry count, in-flight loads,
      refreshes, and application-owned payload allocation separately.
- [x] Record JVM flags, heap/direct-memory limits, container limit if available,
      Java version, OS, transport, allocator, starter commit, and cache policy
      with each run.
- [x] Treat process RSS, committed heap, used heap, direct memory, and cache
      occupancy as distinct signals; do not derive one from another.
- [x] Use explicit GC only at named diagnostic checkpoints and never as a
      production behavior or correctness dependency.
- [x] Bound profiling output and keep heap dumps/JFR files target-only because
      they may contain application data.

Evidence recorded on 2026-08-31:

- `ResponseCacheMemoryDomains` records used, committed, and maximum heap;
  process RSS when `/proc/self/status` is available; JDK direct-buffer count,
  memory, and capacity; Netty allocator direct memory; live/daemon/peak threads;
  explicit direct-memory and container limits when available; sanitized
  memory-relevant JVM flags; Java/OS/transport/allocator identity; and the
  starter commit/dirty state. Unavailable values remain `-1` with an explicit
  source instead of being inferred from another memory domain.
- The loopback fixture now installs a supported Reactor Netty pool meter
  registrar and records registered/total/active/idle/pending/max/disposed pool
  state separately from server dispatches. Decoded 4 KiB application payload
  allocations and bytes are cumulative counters independent of cache entries,
  in-flight loads, coalesced waiters, refreshes, heap, direct memory, and RSS.
  Each scenario also records its enabled, single-flight, refresh, TTL, and
  maximum-size policy facts in
  `target/release-evidence/v29/priority2/deterministic-workload.properties`.
- Explicit GC is requested only at the structurally named
  `baseline-after-explicit-gc` checkpoint. The sampler rejects an explicit-GC
  request at any checkpoint without `explicit-gc` in its name, and no memory
  value is used as a test correctness threshold.
- `scripts/run-v29-memory-profile.sh` confines JFR/heap-dump destinations to
  `target/release-evidence/v29/priority2/profiling/`, caps JFR at 64 MiB, and
  warns that profiles may contain application data. The recorded focused run
  passed 2 tests and produced a valid 973,336-byte JFR.
- The adjacent cache workload/contract/observability run passed 64 tests, the
  documentation plus workload run passed 46 tests, `bash -n` passed for the
  profiling script, and `git diff --check` passed. Memory-growth comparison and
  ownership classification remain intentionally open in Priority 2.3.

### [x] 2.3 Classify observed growth before changing production code

- [x] Compare cache-disabled control runs with cache-enabled fill and steady-state
      runs under identical transport and payload conditions.
- [x] Verify whether growth plateaus at `maximum-size`, falls after expiry/
      eviction, and returns toward the control live set after factory close.
- [x] Repeat refresh, cancellation, duplicate miss, and shutdown races enough to
      distinguish retained owners from one-time class/JIT/allocator growth.
- [x] Correlate any retained object class with a starter-owned reference path or
      explicitly classify it as application-, JVM-, or transport-owned.
- [x] Record one source-controlled finding before Priority 3 implementation:
      confirmed leak, expected bounded retention, accounting gap, or inconclusive
      external workload.

Evidence recorded on 2026-08-31:

- `scripts/run-v29-memory-characterization.sh` launched 55 fresh JVMs: five
  repetitions of the cache-disabled control and all ten cache-enabled/lifecycle
  scenarios. Every child used the same 4 KiB payload, eight-key workload,
  concurrency eight, one-connection epoll transport, adaptive Netty allocator,
  `-Xms128m -Xmx128m`, `-XX:MaxDirectMemorySize=64m`, and G1. Raw sanitized
  properties, logs, per-sample values, and aggregate means are target-only under
  `target/release-evidence/v29/priority2/characterization/`.
- The cache-disabled steady live-set change averaged 18.0 KiB. Eight-entry cold
  and warm fills averaged about 225 KiB; capacity pressure completed eight loads
  but remained at exactly four entries in every repetition. TTL expiry and
  explicit eviction reached zero entries and reduced mean GC-stable heap by
  37,008 and 34,557 bytes before reload. These are diagnostic observations, not
  correctness thresholds or exact retained-size claims.
- Duplicate miss produced eight subscriptions and eight dispatches but one
  winning entry. Refresh, cancellation, duplicate miss, single flight, and
  factory-close races each completed five isolated repetitions. Every final
  checkpoint reported zero entries, loads, refreshes, and pool connections with
  the factory/cache and pool disposed.
- Closed cache scenarios remained about 184-223 KiB of heap and 0.90-4.26 MiB
  of RSS above the cache-disabled mean. The bounded JFR's class-level candidates
  were classified as application payload, Netty transport/allocator state, or
  JVM/Reactor aggregate state; it did not prove a surviving starter reference
  path. `roadmaps/v29/MEMORY-CHARACTERIZATION.md` records the explicit owner
  paths, limitations, and the source-controlled finding: **expected bounded
  retention**, with RSS retained as a separate accounting gap rather than a
  confirmed starter leak.
- The focused workload/cache/refresh/observability run passed 65 tests with no
  failures, errors, or skips. `ResponseCacheMemoryWorkloadTest` separately
  passed 3 tests, including GC-stable release/close checkpoints and the
  duplicate-miss control. The documentation plus workload gate passed 47 tests;
  `bash -n scripts/run-v29-memory-characterization.sh` and `git diff --check`
  also passed. Priority 3 remains responsible for weak-reference and
  root-path collectability proof before any production retention fix.

---

## Priority 3 - Cache Retention Ownership Audit

### [x] 3.1 Inventory every strong owner

- [x] Trace ownership from `ReactiveHttpClientFactoryBean` through cache manager,
      per-policy caches, keys, entries, generation state, in-flight flights,
      refresh tokens, schedulers, subscriptions, meter suppliers, request
      snapshots, response metadata, auth contexts, and decoded values.
- [x] Record the creation, terminal transition, removal trigger, and shutdown
      owner for each retained object class.
- [x] Identify static collections, registry callbacks, scheduler tasks, meter
      suppliers, and application-context references that can outlive a factory.
- [x] Distinguish intentionally retained decoded values from metadata that should
      be released immediately after publication or caller termination.

### [x] 3.2 Prove terminal release paths

- [x] Add bounded reference-queue, weak-reference, heap-query, or owner-count
      tests for success, failure, empty completion, serialization failure, and
      cancellation.
- [x] Cover TTL expiry, size eviction, explicit eviction, refresh replacement,
      refresh failure/cancellation, and factory destruction.
- [x] Verify each waiter releases its context, arguments, and terminal state when
      it ends even while another caller keeps a shared load alive.
- [x] Verify auth contexts, prepared bodies, frozen request arguments, and
      response metadata are not retained after their final required owner ends.
- [x] Make GC-assisted tests bounded and diagnostic-rich; avoid a single sleep or
      one `System.gc()` call as proof.

### [x] 3.3 Close late-publication and external-owner gaps

- [x] Prove duplicate misses, detached shared publishers, and late completion
      callbacks cannot recreate entries or generation records after eviction or
      close.
- [x] Prove refresh callbacks cannot republish or retain state after hard expiry,
      explicit eviction, policy removal, or factory destruction.
- [x] Prove diagnostics and the Actuator endpoint inspect only existing managers
      and do not retain factories, cache values, keys, or application contexts.
- [x] Prove owned cache meters are removed on close and their suppliers cannot
      retain a dead manager.
- [x] Record every confirmed retention defect and its regression test before
      moving to the weight-design gate.

Evidence recorded on 2026-08-31 from commit
`fbaeefee89260d59d33daa4caab9401be4dadb66` plus this Priority 3 test and
documentation change:

- [`CACHE-RETENTION-OWNERSHIP.md`](CACHE-RETENTION-OWNERSHIP.md) records the
  strong path from the Spring bean factory through the client factory, handler,
  manager, policy caches, Caffeine entries/generation state, loads, waiters,
  refreshes, scheduler subscriptions, metrics, diagnostics, and external roots.
  Each row names its creation, terminal/removal transition, and shutdown owner;
  intentionally retained decoded values and representation headers are separated
  from subscription-local request/auth/response metadata.
- `ResponseCacheRetentionOwnershipTest` adds eight bounded `ReferenceQueue`,
  weak-reference, and owner-count proofs. Repeated diagnostic GC attempts run for
  at most five
  seconds with at most 1 MiB of bounded pressure per attempt. The tests keep the
  manager, active leader, or meter registry alive where required, avoiding a
  vacuous whole-fixture collection result.
- The ownership suite covers success, failure, empty completion, simulated
  serialization failure, cancellation, TTL expiry, capacity and explicit
  eviction, refresh replacement/failure/cancellation, and actual factory
  destruction. A detached waiter releases its context, arguments, caller state,
  and unused load state while the leader remains active. A real cache-selected
  request releases frozen body arguments, bounded serialized bytes, prepared
  context, auth context/header, final request identity, and response metadata
  after publication. Capacity and explicit eviction release ordinary survivors
  before manager close. An independent load remains caller-owned after close and
  releases its closure only at caller terminal.
- Late duplicate, detached-publisher, generation, hard-expiry, explicit-eviction,
  and shutdown publication behavior remains covered by the deterministic cache
  race tests listed in the audit. Runtime mutation of an already-created policy's
  TTL, maximum size, or refresh bounds is rejected before lookup, and the
  manager retains only the original policy cache. A retained Actuator map was
  proved not to own
  its provider, bean factory, client factory, manager, cache, or decoded value.
  Factory destruction removes all owned cache meters, and a retained registry
  observes only a same-tag replacement cache.
- One retention defect was confirmed and fixed: mutable runtime bounds could
  retain a distinct cache for each tuple under one policy name.
  `LocalResponseCacheManager` now rejects bounds mutation after the policy cache
  is created. Cache properties remain startup configuration. No other unbounded
  starter root was reproduced; the Priority 2 RSS delta remains a separate
  accounting gap.
- The focused ownership suite passed 8 tests. The ownership, cache race,
  observability, diagnostics, and memory workload run passed 126 tests with no
  failures, errors, or skips. The documentation plus ownership gate passed 52
  tests with no failures, errors, or skips; final diff checks also passed.

---

## Priority 4 - Retained-Weight Contract Spike

### [ ] 4.1 Evaluate candidate measurement boundaries

- [ ] Evaluate response-decode, cache-publication, starter-owned byte, and
      application-supplied weigher boundaries against the same supported value
      shapes.
- [ ] For each candidate, define what is measured, when it becomes known, its
      unit, deterministic cost, ownership, overflow behavior, and relationship
      to the retained decoded value.
- [ ] Include plain values, `ResponseEntity<T>` plus retained headers, empty
      completion, present empty values, refresh replacement, and unknown/custom
      value shapes.
- [ ] Demonstrate why `Content-Length`, compressed wire bytes, arbitrary JSON
      reserialization, reflection-based graph walking, and JVM
      `Instrumentation` are accepted or rejected.
- [ ] Reject candidates that require blocking, unbounded recursion, arbitrary
      reflection, full response duplication, or event-loop reserialization.

### [ ] 4.2 Define admission semantics before API design

- [ ] Define behavior for zero, negative, unknown, overflowing, and individually
      over-budget weights without assigning unknown values a silent constant.
- [ ] Define whether over-budget successful responses bypass storage, fail the
      call, or use a narrower supported-value contract; prefer successful
      uncached delivery unless evidence requires otherwise.
- [ ] Keep mandatory TTL and `maximum-size`; define weight as an additional bound,
      not a replacement or a heap/RSS estimate.
- [ ] Define atomic accounting for first fill, duplicate misses, replacement,
      refresh success/failure, expiry, eviction, and close.
- [ ] Define source/binary compatibility and no-op behavior for every existing
      `4.0.0`/`4.1.0` policy when no weight budget is selected.

### [ ] 4.3 Record the weight-contract decision

- [ ] Publish a source-controlled decision document containing alternatives,
      measurements, supported shapes, rejected designs, migration impact, and
      remaining uncertainty.
- [ ] Confirm no public property, SPI, meter, or diagnostics field was added
      before this decision.
Select exactly one outcome before continuing:
- [ ] **GO:** one deterministic, bounded, non-heap weight unit is defensible and
      Priority 5 may implement it.
- [ ] **NO-GO:** no general unit is defensible; defer Priority 5, retain entry
      bounds, and continue only with proven lifecycle/accounting fixes.
- [ ] Update generated readiness with the selected outcome without presenting a
      no-go as an unfinished release blocker.

---

## Priority 5 - Optional Weighted Admission and Eviction

> Execute implementation items only after Priority 4 records **GO**. On
> **NO-GO**, mark this priority explicitly deferred with the decision-document
> link; do not invent placeholder configuration or API.

### [ ] 5.1 Add one explicit bounded policy contract

- [ ] Add one clearly named per-policy aggregate weight limit using the unit
      selected by Priority 4.
- [ ] Reject invalid, zero/negative where unsupported, and overflowing values at
      startup with client/policy/method context.
- [ ] Keep absence of the new setting behaviorally identical to published
      `4.1.0`, including allocation and dependency behavior.
- [ ] Update configuration metadata, effective configuration, contract snapshots,
      diagnostics model, runtime hints, and public API filters only for types
      required by the selected design.
- [ ] Keep cache and cache-memory observability independently opt-in.

### [ ] 5.2 Make publication and accounting atomic

- [ ] Require every stored entry to satisfy TTL, `maximum-size`, and the selected
      weight limit.
- [ ] Make generation validation, admission, accounting, and publication one
      race-safe transition for first fills and duplicate loads.
- [ ] Prevent an older duplicate completion from replacing a newer value or
      restarting the full TTL/weight lifetime.
- [ ] Return an over-budget successful value to its caller without storing it when
      that is the selected contract.
- [ ] Prevent skipped/failed admission from leaving generation, key, meter, or
      load-token state behind.

### [ ] 5.3 Preserve replacement and refresh invariants

- [ ] Transfer weight exactly once on refresh replacement and ordinary
      replacement.
- [ ] Retain the stale entry and its original accounting when refresh fails,
      empties, is rejected, times out, or is cancelled.
- [ ] Remove weight exactly once on TTL expiry, size/weight eviction, explicit
      eviction, policy removal, and shutdown.
- [ ] Prove totals cannot become negative, overflow, or temporarily exceed the
      documented bound beyond explicitly documented atomic transition behavior.
- [ ] Verify `ResponseEntity<T>` accounting follows the selected body/header
      contract without retaining disallowed headers.

---

## Priority 6 - Capacity, Expiry, and Concurrency Invariants

### [ ] 6.1 Make capacity evidence deterministic

- [ ] Test small and large entries under entry-count pressure and, after a weight
      GO, under combined size/weight pressure.
- [ ] Assert observable admission/eviction invariants without depending on an
      unspecified victim order.
- [ ] Cover entry replacement, same-key reload after expiry, explicit eviction,
      policy isolation, and multiple clients sharing one registry.
- [ ] Verify current entry/weight totals agree with the actual retained entry set
      after every transition.

### [ ] 6.2 Bound metadata independently from values

- [ ] Stress high-cardinality miss-only, failed-load, rejected-admission, and
      cancelled-load keys.
- [ ] Prove generation/tombstone state is removed or bounded independently of
      cache entry count and TTL.
- [ ] Prove per-flight waiter collections, cancellation markers, and diagnostic
      ownership are removed at terminal transition.
- [ ] Verify explicit eviction invalidates outstanding publication tokens without
      unnecessarily cancelling caller-visible work.

### [ ] 6.3 Stress interleavings without blocking event loops

- [ ] Cover same-key and many-key immediate completion, delayed response body,
      timeout, cancellation, refresh, eviction, and shutdown interleavings.
- [ ] Gate races with latches/sinks/virtual time or server observations rather
      than timing-only sleeps.
- [ ] Detect blocking cleanup, unbounded serialization/accounting, or recursive
      traversal on event-loop threads.
- [ ] Record bounded stress iteration counts, seeds where applicable, and failure
      diagnostics in target-only evidence.

---

## Priority 7 - Single-Flight and Refresh Memory Boundaries

### [ ] 7.1 Release each caller independently

- [ ] Account for one shared load independently from the number of attached
      callers.
- [ ] Release a timed-out or cancelled leader's context, request arguments,
      prepared body, auth state, and diagnostics while waiters continue.
- [ ] Keep coalesced waiter terminal records free of transport attempt evidence
      unless that caller actually owns the dispatch.
- [ ] Prevent a detached/removed shared publisher from reconnecting and starting
      an untracked duplicate load.

### [ ] 7.2 Bound hidden refresh work

- [ ] Apply the configured refresh deadline and hard-expiry cancellation to every
      hidden refresh, including a source that never terminates.
- [ ] Release frozen request/auth/body state on refresh success, failure, empty,
      timeout, rejection, cancellation, eviction, and close.
- [ ] Keep one refresh per key and prevent stale callbacks from replacing a newer
      entry or recreating an evicted entry.
- [ ] Verify a stale-serving caller completes independently while refresh remains
      bounded and factory-owned.

### [ ] 7.3 Preserve composition behavior

- [ ] Cover auth resolution/refresh, Retry, RateLimiter, Bulkhead,
      CircuitBreaker, redirect, logical-call timeout, and request timeout around
      miss and refresh loads.
- [ ] Keep per-caller logical-call budgets independent from the shared load's
      lifetime while interested callers remain.
- [ ] Verify zero-attempt admission rejection creates no transport dispatch and
      retains no flight/refresh state.
- [ ] Verify shutdown terminates queued/active flights and refreshes within one
      documented factory deadline.

---

## Priority 8 - Metrics, Diagnostics, and Health Semantics

### [ ] 8.1 Keep telemetry explicit and bounded

- [ ] Register cache memory/accounting telemetry only when cache observability is
      explicitly selected.
- [ ] If weighted admission ships, expose current/maximum weight with a meter
      description that names the exact unit and an admission outcome using only
      stable low-cardinality tags.
- [ ] Keep cache-disabled and cache-enabled/metrics-disabled clients free of the
      new meters and meter suppliers.
- [ ] Never export keys, values, request targets, headers, bodies, identities,
      credentials, tenant data, exception messages, or derived variants.
- [ ] Remove every owned meter on factory destruction and prove destroy/recreate
      observes the replacement manager.

### [ ] 8.2 Evolve diagnostics schema V1 additively

- [ ] Add only nullable configured/runtime facts that can be derived without
      creating a cache manager, invoking a weigher, or traversing cached values.
- [ ] Preserve `null`/unknown for lazy, absent, uncreated, or uninspectable cache
      state instead of reporting false certainty.
- [ ] Keep existing collection-backed snapshot overloads accurate when new facts
      are unavailable.
- [ ] Enforce existing client, endpoint, field, rendered-byte, and sanitization
      limits for map, JSON, and Markdown outputs.
- [ ] Add compatibility fixtures for older schema consumers and unknown fields.

### [ ] 8.3 Preserve downstream health semantics

- [ ] Keep fresh/stale hits and coalesced waiters out of the ordinary downstream
      request timer used by health calculations.
- [ ] Keep miss/refresh loads that fail before transport distinguishable from
      dispatched downstream failures.
- [ ] Ensure local admission bypass/rejection does not dilute or inflate
      downstream health samples.
- [ ] Document which cache meters are occupancy signals and which are terminal
      event histories.

---

## Priority 9 - Operations and Support-Bundle Evidence

### [ ] 9.1 Add a cache-memory triage path

- [ ] Start with cache selection, policy count, `maximum-size`, TTL, occupancy,
      hit/miss/load/refresh/eviction activity, direct memory, connection pools,
      thread count, and deployment changes.
- [ ] Explain how expected bounded retained values differ from monotonically
      growing keys, generation records, flights, refreshes, meters, or transport
      resources.
- [ ] State that RSS is not Java heap and that decoded-object retention is not
      represented by response wire size.
- [ ] Keep published `4.1.0` instructions separate from `4.2.0-SNAPSHOT`/V29-only
      signals until release.

### [ ] 9.2 Define safe capture evidence

- [ ] Add a sanitized fixture with a bounded time window, Java/process/container
      memory summaries, direct-memory signal, cache occupancy/capacity,
      load/refresh/eviction/admission aggregates, and lifecycle events.
- [ ] Include configuration source and selected policy names only where they are
      safe and bounded; include no cache keys, values, headers, bodies, targets,
      identities, credentials, tenant data, or exception messages.
- [ ] Add recursive fixture guards for singular, plural, and compound sensitive
      field names plus relative request-target/query material.
- [ ] Document that heap dumps and JFR recordings can contain sensitive
      application data and must follow a separate secure handling process.
- [ ] Verify Docker/Kubernetes recipes preserve failure bodies, management-port
      placeholders, shell variables, and minimal-image compatibility.

---

## Priority 10 - Mock and Assembled-Consumer Parity

### [ ] 10.1 Extend deterministic mock controls

- [ ] Provide deterministic time and control surfaces for occupancy, TTL expiry,
      admission, eviction, refresh, and close without exposing production cache
      internals.
- [ ] Keep mock behavior aligned with production for duplicate misses,
      single-flight membership, refresh replacement, and late publication.
- [ ] Ensure `MockReactiveHttpClient.close()` owns and closes cache managers in
      deterministic and ordinary-time modes.
- [ ] Keep existing `4.1.0` test-helper source/binary usage working when no new
      budget is configured.

### [ ] 10.2 Revalidate assembled consumers

- [ ] Verify a cache-disabled Boot 4 consumer requires no optional Caffeine or
      new cache-accounting dependency.
- [ ] Verify the published `4.1.0` assembled consumer remains Central-only and
      unchanged by V29 APIs.
- [ ] If weighted admission ships, add a current-reactor consumer that declares
      required dependencies/configuration explicitly and exercises admission,
      hit, eviction/bypass, and shutdown.
- [ ] Preserve Surefire reports, effective POMs, dependency trees, classpaths,
      artifact hashes, completed stage, and exit status on success and failure.
- [ ] Reject reactor `target/classes`, stale report, and default-local-repository
      contamination in every consumer lane.

---

## Priority 11 - AOT, Native, and Shutdown Parity

### [ ] 11.1 Keep AOT hints narrow

- [ ] Register only configuration, record accessor, constructor, and SPI types
      required by the selected contract.
- [ ] Avoid broad package/type/member reflection and any runtime reflective graph
      traversal for weighing.
- [ ] Preserve replacement properties/cache beans during AOT analysis without
      eagerly creating lazy managers or factories.
- [ ] Add native-hint tests for selected and unselected cache-memory policies.

### [ ] 11.2 Extend native execution evidence

- [ ] Run cache-disabled and bounded cache fill/hit/expiry/shutdown paths in the
      native fixture.
- [ ] After a weight GO, cover over-budget successful non-publication and
      replacement/eviction accounting; after a no-go, omit nonexistent fields and
      record the decision.
- [ ] Prove diagnostics do not initialize lazy cache components in the native
      executable.
- [ ] Compile and run from a clean committed tree; record GraalVM/JDK version,
      command, commit, binary SHA-256, and executable result.

### [ ] 11.3 Stress shutdown and restart

- [ ] Close active/queued miss loads and hidden refreshes within the single
      documented factory shutdown deadline.
- [ ] Prevent connections, direct buffers, schedulers, meter suppliers, cache
      entries, or late callbacks from surviving context close.
- [ ] Recreate the application context/factory with the same client/policy/meter
      tags and prove new instances are observed.
- [ ] Run shutdown stress on JVM and native paths with disposal-driven terminal
      assertions rather than ordinary request/acquire timeouts.

---

## Priority 12 - Performance and Allocation Re-Audit

### [ ] 12.1 Keep disabled paths allocation-neutral

- [ ] Add or update JMH coverage for publisher creation and subscription when
      caching is unselected, selected without memory accounting, and selected
      with metrics disabled.
- [ ] Prove no manager, meter, weigher, value inspection, or accounting object is
      created for cache-disabled clients.
- [ ] Keep benchmark scenario discovery compatible with published `4.1.0` and
      record dependency/commit provenance.

### [ ] 12.2 Measure selected paths

- [ ] Measure hit, miss publication, rejected/bypassed admission, size/weight
      eviction, single-flight attachment, refresh replacement, and accounting in
      no-network and loopback modes.
- [ ] Separate throughput/latency, allocation per operation, retained live-set,
      and one-time setup costs.
- [ ] Use JFR/heap evidence to distinguish transient measurement allocation from
      retained entries and metadata.
- [ ] Record scenario completeness and missing published-baseline scenarios
      instead of comparing mismatched rows.

### [ ] 12.3 Classify public performance evidence

- [ ] Run current and published `4.1.0` release-quality benchmarks on the same
      clean machine only if request-path behavior changes or release wording
      makes a performance claim.
- [ ] Keep generated reports target-only unless a source-controlled promoted
      report is required for a public claim.
- [ ] Record a no-public-claim deferral when release notes make no performance,
      latency, percentile, throughput, allocation, or overhead movement claim.
- [ ] Run benchmark report-path/provenance guards and `git diff --check` before
      closing this priority.

---

## Priority 13 - Public API, Documentation, and Release Readiness

### [ ] 13.1 Freeze the supported surface

- [ ] Add public configuration or SPI only after the Priority 4 go decision and
      include every exposed nested/helper type in compatibility coverage.
- [ ] Audit public constructors, mutable models, annotation defaults, metadata,
      diagnostics schema, mock helpers, and replacement-bean contracts.
- [ ] Run strict root and starter-module japicmp against fresh published `4.1.0`
      repositories; classify every additive or incompatible row.
- [ ] Keep a no-go path free of placeholder public APIs and remove spike-only
      implementation before release review.

### [ ] 13.2 Consolidate documentation

- [ ] Update cache, observability, operations, support-bundle, effective
      configuration, native/release compatibility, testing, and migration guides
      from one vocabulary and one selected weight unit, if any.
- [ ] State clearly that `maximum-size` counts entries and no configured weight
      equals exact heap, direct memory, RSS, or container memory.
- [ ] Keep copyable examples startup-valid, dependency-complete, sanitized, and
      version-scoped to published versus snapshot behavior.
- [ ] Regenerate configuration reference/effective examples and run local-link,
      placeholder-domain, machine-path, secret-fixture, and benchmark-wording
      guards.

### [ ] 13.3 Assemble immutable release evidence

- [ ] Pass the complete reactor, package/generation guards, dependency matrix,
      current/published consumers, AOT, native, shutdown, documentation, API, and
      benchmark disposition from one reviewed clean commit.
- [ ] Record commands, Java/Boot/GraalVM versions, commit, clean-tree state, test
      totals, artifact checksums, Central markers, evidence paths, and remaining
      risk under `target/release-evidence/v29/`.
- [ ] Confirm target-only evidence is not committed and any promoted report is
      sanitized, source-controlled, and version-matched.
- [ ] Verify generated readiness lists every unresolved command and reflects the
      Priority 4 go/no-go outcome without contradictory scope/publication state.

### [ ] 13.4 Select release scope and close V29

- [ ] Select `4.2.0` only when the final scope is additive, bounded, measurable,
      and supported by immutable evidence.
- [ ] If only compatible retention defects are proven, choose an appropriate
      maintenance release path or record why the `4.2.0` feature scope is no-go.
- [ ] Record one explicit go/no-go decision with candidate version, commit, date,
      evidence paths, benchmark disposition, and remaining risk.
- [ ] On go, cut/publish only from the reviewed clean commit and run generation-
      packaging/signing checks.
- [ ] Verify parent/module POM/JAR/source/Javadoc artifacts and an assembled
      consumer from fresh Maven Central repositories before moving public/API/
      consumer/benchmark baselines.
- [ ] Archive V29 and start the next snapshot only after post-publication evidence;
      on no-go, keep unreleased coordinates private and document the blocker or
      narrowed maintenance scope.

---

## Completion Criteria

V29 is complete only when:

- [ ] Production memory growth has a reproducible classification with separated
      heap, direct-memory, transport, cache, and application evidence.
- [ ] Every starter-owned cache entry, key, generation record, flight, waiter,
      refresh, meter, request snapshot, auth context, and callback has a proven
      terminal owner and release path.
- [ ] Weighted admission either has one precise, bounded, non-heap unit and an
      explicit go decision, or is source-controlled as no-go with no placeholder
      public surface.
- [ ] Existing policies remain compatible and no new cache-memory behavior or
      telemetry activates without explicit selection.
- [ ] Mock, consumer, API, AOT, native, shutdown, performance, documentation, and
      operations evidence agree with the selected scope.
- [ ] Release publication and baseline movement occur only after fresh Central
      artifact and assembled-consumer verification.
