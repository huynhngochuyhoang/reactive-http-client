# Reactive HTTP Client - Roadmap V29

> **Status:** active
> **Theme:** response-cache retention budgets and production memory evidence
> **Candidate release direction:** `4.2.0`, subject to an additive and measurable contract
> **Starting development line:** `4.2.0-SNAPSHOT`
> **Published/API baseline:** `4.1.0`

## Starting State

V27 introduced explicit bounded local response caching and V28 extended the
same contract to method-specific semantic reads. Published `4.1.0` now supports
entry-count limits, TTL, single flight, access-driven refresh, terminal cache
outcomes, metrics, diagnostics, mocks, AOT, and native execution.

The remaining production question is retained memory. `maximum-size` bounds the
number of entries, not the heap retained by each decoded value. Cached values
are returned by identity, so the cache intentionally retains the decoded object
graph until expiry, eviction, replacement, or shutdown. A small number of large
DTO graphs can therefore consume more memory than operators infer from the entry
gauge. Conversely, a pod memory increase does not by itself prove a leak: Netty
direct memory, connection pools, JVM ergonomics, application allocation, and
cache policy/cardinality must be separated with evidence.

V29 starts with that diagnosis. It may add an explicit retained-weight admission
contract only after the project can name what is measured, where the measurement
comes from, and which cacheable values are unsupported. It must not label wire
bytes, serialized bytes, or a user estimate as exact JVM heap size.

## Goals

1. Reproduce and classify memory growth with caching disabled, enabled, full,
   expired, refreshed, cancelled, evicted, and destroyed.
2. Prove that keys, generation records, flights, refreshes, meters, decoded
   values, request snapshots, and auth state are released by their owning
   lifecycle.
3. Add an optional aggregate cache-weight budget only if admission and eviction
   use one deterministic, bounded value known before publication.
4. Keep existing `4.0.0`/`4.1.0` policies source-, binary-, and behavior-
   compatible when the new budget is absent.
5. Give operators enough bounded metrics and diagnostics to distinguish normal
   occupancy from retained-state defects without exposing keys or values.

## Non-Goals

- Exact JVM object-graph sizing or a claim that cache weight equals RSS/heap.
- Distributed caching, cross-pod coherence, write-through/write-behind, or
  automatic invalidation after writes.
- Weak/soft-reference caching, unbounded adaptive sizing, or GC-driven policy
  semantics.
- Changing the identity-return contract for decoded cached values.
- Inferring a safe memory budget from container limits without application
  configuration.
- Treating a high-memory report as proof that the starter is responsible before
  cache selection and retained-owner evidence are established.

## Core Decisions to Freeze

### Entry count is not retained size

`maximum-size` remains a mandatory entry-count guard. A future weight setting is
an additional admission/eviction bound, not a replacement and not an estimate of
complete process memory.

### One explicit weight unit

Before implementation, V29 must choose and document one unit. Acceptable designs
include a deterministic retained-weight estimate supplied by a narrowly scoped
SPI, or exact bytes captured from a starter-owned representation that remains
available through publication. `Content-Length` alone is insufficient because it
can be absent, compressed, or inconsistent with the retained decoded graph.
Re-serializing arbitrary response DTOs solely to estimate size is also
insufficient because it changes allocation and can diverge from decoding.

If no defensible general weight exists, V29 narrows weighted admission to proven
value shapes or records a no-go. It does not silently assign unknown objects a
zero or constant weight.

### No hidden activation

Existing policies with only TTL and maximum size retain published behavior. New
weight metrics, diagnostics, admission rejection, or eviction behavior activate
only when the new budget is explicitly selected. Cache-disabled clients must not
allocate a manager, register cache meters, inspect values, or execute a weigher.

### Lifecycle ownership remains exact

A factory owns its cache manager, entries, flights, refreshes, and meters. Close
must prevent late publication, terminate hidden work, deregister owned meters,
and make all retained state collectible without waiting for TTL. Diagnostics may
inspect an already-created manager but must not create one.

## Priorities

## 1. Post-`4.1.0` Baseline and V29 Scope Integrity

- Verify all 13 parent/module artifacts and the assembled consumer from fresh
  Maven Central repositories.
- Move public, API, consumer, and benchmark baselines to `4.1.0`; keep reactor-
  only coordinates on `4.2.0-SNAPSHOT`.
- Preserve V1-V28 as completed release evidence and make V29 the only active
  draft.
- Keep the candidate deferred until memory evidence selects an additive scope.

## 2. Production Memory Characterization

- Build a deterministic loopback workload for cache disabled, miss, hit,
  capacity pressure, TTL expiry, refresh, single flight, cancellation, and
  shutdown.
- Separate Java heap, direct memory, thread count, connection-pool resources,
  cache occupancy, and application-owned allocations.
- Capture GC-stable retained-object evidence at fixed workload checkpoints,
  including after explicit GC only as diagnostic evidence rather than runtime
  behavior.
- Prove whether growth plateaus at configured bounds and whether factory close
  returns retained starter state to baseline.
- Record a finding before changing production code: confirmed leak, expected
  bounded retention, accounting gap, or inconclusive external workload.

## 3. Cache Retention Ownership Audit

- Inventory every strong reference from client factory to cache keys, entries,
  generation states, in-flight loads, refresh tokens, schedulers, subscriptions,
  meter suppliers, request snapshots, response metadata, auth contexts, and
  decoded values.
- Add weak-reference/queue or heap-query tests that prove each owner is released
  after success, failure, empty completion, cancellation, expiry, size eviction,
  explicit eviction, refresh replacement, and factory destruction.
- Verify duplicate misses, detached waiters, and late refresh callbacks cannot
  recreate generation state or entries after invalidation/close.
- Prove diagnostics and Actuator snapshots do not retain factory/cache instances
  beyond the application context lifecycle.

## 4. Retained-Weight Contract Spike

- Evaluate measurement at response decode, cache publication, and application-
  supplied weighting boundaries.
- Define supported value shapes, unknown-value behavior, overflow handling,
  maximum calculation cost, and whether `ResponseEntity<T>` includes retained
  header metadata.
- Reject unbounded reflection, recursive graph walking, `Instrumentation` agent
  requirements, and response re-serialization on the event loop.
- Decide whether one entry may exceed the aggregate budget, must bypass storage,
  or must fail the call; the preferred default is to return the successful value
  without caching it.
- Produce a written go/no-go decision before adding public configuration or API.

## 5. Optional Weighted Admission and Eviction

If Priority 4 selects a defensible design:

- Add one explicitly named per-policy aggregate weight limit with strict numeric
  validation and generated metadata.
- Keep `maximum-size` and TTL mandatory; an entry must satisfy every configured
  bound before publication.
- Make admission and replacement atomic with generation checks so concurrent
  duplicate loads cannot overshoot accounting or replace a newer winner.
- Define deterministic handling for zero, negative, unknown, overflowing, and
  over-budget weights.
- Ensure refresh replacement transfers weight exactly once and failure retains
  the previous entry without double accounting.
- Preserve ordinary successful responses when cache admission is skipped.

## 6. Capacity, Expiry, and Concurrency Invariants

- Test mixed small/large entries under size and weight pressure with deterministic
  eviction evidence; do not depend on unspecified victim order unless the public
  contract promises it.
- Verify lookup, publication, expiry cleanup, explicit eviction, refresh,
  diagnostics, and close cannot observe negative or impossible totals.
- Bound metadata independently from value occupancy so adversarial miss-only key
  cardinality cannot retain one generation record per attempted key.
- Stress same-key and many-key loads across immediate completion, delayed body,
  cancellation, timeout, and shutdown.
- Keep event-loop paths free of blocking cleanup or unbounded accounting work.

## 7. Single-Flight and Refresh Memory Boundaries

- Account for one shared load independently from caller count and release waiter
  state immediately when each caller terminates.
- Prove a leader timeout does not retain its context, request arguments, or
  terminal state while another waiter keeps the load alive.
- Bound hidden refresh lifetime by the existing refresh timeout/hard expiry and
  release its frozen request/auth state on every terminal path.
- Verify eviction and shutdown cancel hidden refresh work and prevent late
  publication without cancelling caller-visible successful work unnecessarily.

## 8. Metrics, Diagnostics, and Health Semantics

- Keep cache memory telemetry separately opt-in under the cache observability
  switch.
- If weighted admission ships, expose bounded current/maximum weight gauges and
  an admission outcome with stable low-cardinality tags; define the unit in the
  meter description.
- Never export keys, values, body samples, request targets, identity material, or
  a label derived from them.
- Extend diagnostics schema V1 only additively with nullable configured/runtime
  facts; lazy/uncreated managers remain unknown rather than being instantiated.
- Keep ordinary downstream health based on dispatched calls, not cache hits or
  local admission outcomes.
- Remove all owned cache meters on factory destruction and prove destroy/recreate
  registration observes the new manager.

## 9. Operations and Support-Bundle Evidence

- Add a memory-triage decision tree that starts with cache selection, policy
  count, occupancy, TTL, entry pressure, direct memory, and connection pools.
- Document how to distinguish expected retained cache values from monotonically
  growing keys, flights, refreshes, meters, or transport resources.
- Provide a sanitized fixture with time window, process/container memory,
  heap/direct-memory summaries, cache occupancy/capacity, eviction/admission
  counters, and lifecycle events; include no keys, values, headers, bodies,
  targets, identities, credentials, or tenant data.
- State explicitly that RSS does not equal Java heap and that a heap dump can
  contain sensitive application data requiring separate handling.

## 10. Mock and Assembled-Consumer Parity

- Give deterministic mock time/control surfaces for occupancy, expiry,
  admission, eviction, and close without exposing production cache internals.
- Verify cache-disabled consumers need no optional Caffeine/runtime additions.
- Add an assembled Boot 4 consumer that selects the new budget only when its
  required dependency/configuration is present.
- Preserve published `4.1.0` source/binary behavior and test-helper usage when no
  weight limit is configured.

## 11. AOT, Native, and Shutdown Parity

- Register only the configuration/SPI types actually required by the selected
  design; avoid broad reflective graph traversal.
- Extend native smoke with a bounded cache fill, over-budget non-publication,
  eviction/expiry, and factory shutdown assertion.
- Record native binary provenance and verify diagnostics do not initialize lazy
  cache components.
- Stress application-context restart and factory recreation for scheduler,
  meter, cache, and direct-buffer cleanup.

## 12. Performance and Allocation Re-Audit

- Measure disabled-cache publisher creation to prove no new work on unselected
  clients.
- Measure hit, miss publication, rejected admission, eviction, single-flight
  attachment, refresh replacement, and accounting under no-network and loopback
  workloads.
- Use JFR/heap evidence to distinguish transient measurement allocation from
  retained values and metadata.
- Keep public wording non-numerical unless a clean promoted report compares the
  candidate with published `4.1.0` on the same machine and equivalent scenarios.

## 13. Public API, Documentation, and Release Readiness

- Freeze any additive configuration/SPI only after the weight spike passes; do
  not publish an unstable estimator contract.
- Update effective configuration, contract snapshots, diagnostics, metadata,
  native hints, cache guide, observability guide, operations guide, support
  bundles, and migration notes from one vocabulary.
- Pass strict root and module API checks against fresh published `4.1.0`, package
  guards, dependency matrix, current/published consumers, AOT/native, shutdown,
  benchmarks, and documentation tests.
- Select `4.2.0` only if the final behavior is additive, bounded, and supported
  by immutable evidence. Otherwise release only proven internal leak fixes as an
  appropriate patch or record a no-go.
- Move baselines and archive V29 only after Central artifacts and an assembled
  published consumer are verified.

## Acceptance Summary

V29 is complete when the project can explain production memory growth with
reproducible ownership evidence, every starter-owned cache lifecycle releases
its state, and any new admission budget has a precise non-heap unit and bounded
cost. Entry count, weight, metrics, and diagnostics must remain opt-in and must
not expose request or response material. If a defensible generic weight cannot
be implemented, the roadmap succeeds by documenting that no-go and shipping
only proven retention fixes rather than an inaccurate memory promise.
