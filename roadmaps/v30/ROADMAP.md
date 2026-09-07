# Reactive HTTP Client - Roadmap V30

> **Status:** active
> **Theme:** bounded active cache work and overload visibility
> **Candidate release direction:** `4.3.0`, subject to an additive opt-in contract
> **Starting development line:** `4.3.0-SNAPSHOT`
> **Published/API baseline:** `4.2.0`
> **Execution:** [checklist adopted](CHECKLIST.md); final release scope not yet selected

## Starting State

V27 introduced explicit response caching, V28 extended it to acknowledged
semantic reads, and V29 shipped optional decoded-response representation-byte
admission in `4.2.0`. Stored values now have mandatory entry-count and TTL
bounds plus an optional aggregate representation-byte bound. Authorization,
request identity, single flight, refresh, terminal diagnostics, and shutdown
already have compatibility contracts.

The next capacity question concerns work before storage. A burst of distinct
misses can retain many request snapshots and active loaders while cache entry
occupancy remains zero. Single flight reduces duplicate downstream work for one
key, but each attached caller still owns subscription state. Refresh has a
per-entry deadline and duplicate suppression, but many stale keys can start
refreshes together. A response-byte admission limit applies after decoding and
cannot bound these concurrent owners.

The current implementation provides the starting evidence:

- `LocalResponseCacheManager.coalescedLoad()` creates a flight per active key
  and reserves a member for every joining caller without a configured work
  count limit. Independent misses use `load()` without joining that flight map.
- `triggerRefresh()` suppresses duplicate refreshes for the same entry but has
  no separate configured policy-wide concurrent-refresh limit.
- Cache policy properties describe stored-entry bounds and refresh timing;
  they do not select a bound on preparation, active callers, or loaders.
- Current cache meters describe occupancy and terminal activity. The workload
  test snapshot exposes live flight/waiter/refresh counts, but that internal
  snapshot is not an operations API.

See the [cache manager](../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/core/LocalResponseCacheManager.java),
[policy properties](../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/config/ReactiveHttpClientProperties.java),
and [cache meters](../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/core/MicrometerLocalResponseCacheMetrics.java).
These are capacity gaps to characterize, not evidence that published `4.2.0`
leaks. The completed V29
[memory characterization](../v29/MEMORY-CHARACTERIZATION.md) and
[ownership audit](../v29/CACHE-RETENTION-OWNERSHIP.md) remain the baseline.

## Goals

1. Bound the number of cache-selected caller subscriptions, foreground loads,
   and hidden refreshes when the application explicitly selects work limits.
2. Acquire capacity before the work and retained state it claims to govern.
3. Define saturation without hidden queues, duplicate dispatch, or stale state
   left by rejected subscriptions.
4. Preserve per-caller authorization, deadlines, final request identity, and
   existing cache eligibility across `GET` and acknowledged semantic reads.
5. Make active work and rejected/skipped work observable without exporting
   request, response, key, or identity material.
6. Keep published behavior and dependencies when the new limits are absent.

## Non-Goals

- Exact heap/RSS control, object graph sizing, or an application weigher SPI.
- Distributed concurrency limits, cross-pod cache coherence, or invalidation
  inferred from writes.
- A general replacement for Resilience4j Bulkhead, RateLimiter, connection-pool
  acquisition limits, or ingress admission control.
- Background refresh scheduling, warming, pending-work queues, or adaptive
  limits inferred from CPU, memory, downstream latency, or registry presence.
- Per-tenant quotas, fairness guarantees, priorities, or a new key-bearing API.
- Broadening cacheable response/body shapes or modifying semantic-read intent.
- Implicit activation of caching, resilience operators, refresh, or telemetry.

## Proposed Contract

### Three separate ownership bounds

The intended optional policy contract has three dimensions. Public property
names and numeric ranges must be frozen in Priority 3 before implementation.

| Dimension | Counted owner | Reservation lifetime |
|---|---|---|
| Foreground callers | Every subscribed cache-selected call, including preparation, auth, fresh/stale hits, miss leaders, and coalesced waiters | Before argument freezing, serialization, and pre-lookup auth until that caller terminates |
| Foreground loads | Every independent miss or shared miss load, including a shared load whose first caller has detached | Before loader assembly/subscription until source termination or cancellation |
| Hidden refreshes | Every admitted refresh, including preparation and transport work | Before hidden loader assembly until success, failure, timeout, cancellation, or invalidation terminates it |

Limits belong to one selected client factory and named policy. APIs selecting
that policy share capacity. Different factories and pods have separate capacity;
one policy definition does not imply a process-wide or cluster-wide limit.

Selecting the bounded-work contract requires positive caller and foreground-load
limits. When refresh is enabled, it also requires a positive refresh limit.
An absent work contract retains published behavior. Partial, zero, negative,
overflowing, or contradictory selections fail validation; zero is not a second
spelling for disabled. Work selection cannot enable caching or refresh.

The caller bound also caps attached single-flight waiters across the policy, so
V30 does not need a separate per-key waiter setting. Hits consume a short-lived
caller reservation because mandatory preparation and authorization precede a
safe lookup. Consequently a hit can be rejected at caller saturation; the
starter must not bypass its authorization gate to make a hit appear available.

Foreground and refresh capacity are separate. Foreground load capacity must
remain available even when the refresh allowance is full. This isolation is
local to the cache: both workloads can still contend for an explicitly shared
Resilience4j instance, auth service, scheduler, or transport pool.

### Fail immediately; do not queue rejected work

| Situation | Required result |
|---|---|
| Caller capacity exhausted | One local terminal admission error; no freeze, serializer, auth, flight attachment, or transport work |
| Fresh hit after caller admission | Authorized cached value; no load or refresh reservation |
| Existing same-key flight and caller capacity available | Join the flight without another load reservation, even when load capacity is full |
| Miss needs a new load and load capacity is exhausted | One local terminal admission error; no loader invocation or fallback uncached dispatch |
| Stale hit and refresh capacity available | Return the authorized stale value and admit at most one current-entry refresh |
| Stale hit and refresh capacity exhausted | Return the authorized stale value before hard expiry; skip refresh without queueing a trigger |
| Entry reaches hard expiry | Ordinary miss admission; no stale fallback beyond TTL |

Refresh skips do not extend TTL, reset refresh age, record a terminal refresh
that never started, or retain the triggering context for later execution. A
later access may try again when capacity is available.

No new implicit foreground deadline is introduced. A hung admitted call/load
holds its reservation until its configured timeout or cancellation terminates
it. Operators still choose logical-call and request deadlines; a finite count
limit bounds simultaneous work, not its duration. Refresh retains its existing
mandatory timeout and hard-expiry boundary.

### Ownership and accounting are atomic

Every admitted owner has one release token. Completion, synchronous failure,
cancellation before source attachment, Retry resubscription, eviction, and
close must not double-release or clear another owner's capacity. Slot release
must be ordered so immediate downstream resubscription can use released capacity
without relying on a prior attempt's delayed `doFinally` callback.

Cache lookup, joining an existing flight, and reserving a new load must handle
concurrent publication and last-member cancellation as one consistent decision.
A rejected attempt leaves no flight, member, generation token, loader closure,
or deferred task. Evicting storage does not free a slot still owned by a running
independent load; that load can finish for its caller but cannot republish an
invalidated generation.

A caller's deadline is never the shared load's lifetime. The first caller may
terminate while an admitted waiter keeps the source alive. Source capacity is
released only when that source terminates; the detached caller's state is
released independently. Refresh uses its own state and existing authenticated,
resilience-aware load pipeline.

Counts are not byte estimates. The limits exclude application-held publishers,
returned values, unselected context objects, JVM/transport arenas, and other
clients. Existing bounded key/body preparation reduces per-call work but does
not establish a bound on arbitrary application object graphs or total process
memory.

## Priorities

## 1. Post-`4.2.0` Baseline and V30 Scope Integrity

- Verify the published parent/module artifact set and assembled consumer from
  fresh Central repositories; preserve V29's clean release/tag evidence.
- Keep public, API, consumer, and benchmark baselines on `4.2.0` and current
  fixtures on `4.3.0-SNAPSHOT`. Include V29 weighted benchmark rows in the
  published baseline.
- Adopt one reviewed execution checklist before marking V30 implementation
  active. Generated readiness must distinguish this draft, active execution,
  release selection, and publication.
- Preserve V1-V29 as completed historical evidence. The resilience proposal was
  already adopted by V27; it does not introduce another migration into V30.

## 2. Active-Work Characterization

- Extend deterministic loopback workloads with high-cardinality blocked misses,
  many callers on one key, independent duplicate misses, slow pre-lookup auth,
  and simultaneous stale-key refreshes.
- Record active caller, load, flight, waiter, refresh, generation, pool, and
  retained-owner evidence separately from stored entries/bytes. Use bounded
  test inputs, gates, and named checkpoints; zero occupancy is not idle work.
- Compare cache disabled, published cache behavior, and explicitly selected
  Resilience4j Bulkhead/pool limits to establish which stages each protects.
- Record capacity exposure, normal caller-owned retention, confirmed defect,
  or inconclusive evidence before changing production behavior. Keep heap/JFR
  captures private and avoid assertions on absolute GC or RSS values.

## 3. Effective Work-Limit and Admission Contract

- Freeze the smallest per-policy configuration implementing the three dimensions
  above, error surface, units, valid ranges, and startup mutation behavior.
- Keep method/client cache precedence and `@CacheDisabled` authoritative; inert
  definitions, omitted limits, and optional libraries cannot activate bounds.
- Share one normalized decision across binding, startup, runtime, effective
  contracts, diagnostics, mocks, and AOT, including replacement metadata caches.
- Specify the local admission error's structural reason, retry semantics, cache
  outcome, and observability selection before exposing a public type or enum.
  It must be distinguishable from byte-storage bypass, pool acquisition,
  CircuitBreaker, Bulkhead, and downstream failures without arbitrary text.
- Prove an application can migrate one selected policy without changing another
  policy or the published `4.2.0` path.

## 4. Admission Before Request Preparation

- Reserve caller capacity inside each cold subscription before freezing,
  JSON/body materialization, context snapshots, and authorization probes.
- Reject at N+1 with N deliberately blocked callers and prove the rejected
  call never reaches the serializer, auth provider, customizer, or transport.
- Cover fresh hits, stale hits, miss leaders, waiters, inherited methods, and
  `@ApiRef` semantic reads; allow bounded reporting setup for the local error.
- Release after preparation/auth error, empty authorization, cancellation,
  logical timeout, and synchronous exceptions, including source-attachment races.
- Preserve the mandatory finalized-request/auth gate and one end-to-end caller
  deadline. Admitted serialization and request materialization use the same
  frozen representation as the cache key.

## 5. Foreground Load and Single-Flight Capacity

- Apply foreground load admission to both independent and coalesced misses.
  Reserve before loader assembly and count one load across its Retry, redirect,
  auth replay, response decode, and publication work.
- Permit joining an existing flight at load saturation when caller capacity is
  available. Distinct keys and non-coalesced duplicates each need a load slot.
- Test stale lookup decisions, completed flights, last-waiter cancellation,
  delayed subscription attachment, and immediate terminal resubscription with
  deterministic gates rather than sleep-based expectations.
- Prove the first caller's cancellation/timeout frees its caller slot but keeps
  the load slot while another caller remains interested, without transferring
  transport evidence into the waiter's terminal event.
- Saturation must not create an untracked source, busy retry loop, pending-work
  queue, or uncached duplicate request.

## 6. Refresh Capacity and Foreground Isolation

- Reserve refresh capacity before hidden preparation and loader assembly;
  combine it with current-generation validation and same-entry suppression.
- Test multiple stale keys and hot repeated access at saturation. Skips leave
  the old entry's value, stored byte weight, age, and hard expiry unchanged.
- Release on synchronous preparation error, source termination, timeout, hard
  expiry, eviction, and close, including cancellation before subscription attach.
- Keep foreground and refresh counts separate, and prove a saturated refresh
  allowance alone does not consume foreground slots.
- After hard expiry, any new foreground load must satisfy foreground admission;
  cancelled or obsolete refresh callbacks cannot publish into its generation.

## 7. Resilience, Auth, Redirect, and Deadline Composition

- Preserve current operator selection/order and unsafe replay validation. Cache
  capacity never implicitly enables a Resilience4j operator or retry permission.
- Place foreground local rejection outside the loader's Retry/CircuitBreaker
  pipeline: no downstream attempt, guard permit, or circuit failure sample is
  created by a rejected cache reservation.
- Cover auth rejection on a warm hit, `401` refresh followed by identity change,
  body-preserving redirects, open-circuit admission, and Retry backoff.
- Keep shared-load capacity through retries without reacquiring for hidden
  dispatches. Caller deadlines remain independent, including late waiters.
- Prove successful values that bypass byte storage still release work capacity;
  byte admission and active-work admission remain different decisions.

## 8. Cancellation, Eviction, and Shutdown Ownership

- Assert 0 <= active <= configured maximum at every synchronized test checkpoint
  under same-key and many-key success, failure, empty, timeout, and close races.
- Verify exactly one reservation/release for every accepted owner and no retained
  owner for a rejection or skipped refresh; test exceptions during assembly.
- Use weak references or reference queues to prove caller/auth/body/context and
  callback release independently of cached-value retention.
- Preserve published close ownership: registered flights/refreshes terminate;
  an independent caller-visible load may retain its lease until its own terminal
  signal, but cannot publish or acquire new work after manager close.
- Recreated factories start with independent capacity. Late releases from an old
  manager cannot change the replacement's counters or meters.

## 9. Live Metrics and Terminal Diagnostics

- Under existing explicit cache observability selection, expose bounded current
  and configured work counts for policies selecting limits, plus fixed local
  rejection and refresh-skip reasons. Freeze names/tags/units before recipes.
- Count admitted foreground loads separately from shared flights and attached
  callers; document which sums overlap and which are independent.
- Preserve existing meter tag sets and caller/load/refresh terminal meanings.
  Never count a skipped refresh as completed work or a load as a wire dispatch.
- Keep local rejections out of downstream timer/health samples while delivering
  one structural terminal event to configured observer, lifecycle, log, and OTel
  surfaces. Cache outcomes remain available without a MeterRegistry bean.
- Extend diagnostics V1 additively with bounded, nullable configuration/runtime
  facts; lazy, absent, mixed-policy, and closed states have explicit meanings.
  Reading diagnostics must not create a manager or traverse request values.
- Coordinate overlapping meter owners with identical tags, define aggregation
  of their current/maximum counts, and remove registrations only when ownership
  ends. Test GC, close/recreate, and old-owner late callbacks.

## 10. Mock, Consumer, AOT, and Native Parity

- Add deterministic mock controls/snapshots for admission and release without
  exporting keys or production internals; cover optional real and virtual time.
- Exercise limits through assembled consumers for `GET` and semantic `POST`,
  weighted and count-only storage, single flight, and refresh.
- Cache-disabled consumers still run without Caffeine. Unbounded published
  policies remain source/binary/behavior compatible.
- Validate properties and effective decisions during AOT using the application's
  configured metadata/properties beans; no new reflective request graph scan.
- Extend native smoke with gated saturation, rejected no-dispatch evidence,
  released-capacity reuse, refresh skip, and shutdown. Record the executable
  hash and exact clean fixture commit.

## 11. Operations and Support-Bundle Evidence

- Document which knob bounds stored entries, representation bytes, caller work,
  loads, refreshes, pool acquisition, and Resilience4j concurrency. State each
  scope and the deliberate foreground error/refresh-skip behavior.
- Build saturation and recovery recipes from the actual live/terminal signals
  in Priority 9. Preserve scrape-target labels and zero/absent distinctions;
  no key cardinality or inferred flight lifetime may masquerade as an export.
- Add a sanitized fixture with selected client, API-to-policy mapping, configured
  limits, observability selection, timestamped active-count checkpoints, counter
  deltas, and lifecycle events. Reconcile counters and occupancy at the same
  boundaries and capture registered meters before factory close.
- Version-scope new fields to the candidate while preserving published `4.2.0`
  capture compatibility, null/unknown semantics, quarantine, bounded download
  duration/bytes, and validation of one JSON document.
- Require rollout guidance for local rejection handling and caller/request
  deadlines. Finite work counts do not prove a complete process-memory bound.

## 12. Performance and Allocation Evidence

- Compare published `4.2.0` with the candidate on cache-disabled invocation,
  existing unbounded policies, admitted hits/misses, and weighted publication.
- Add selected-limit rows for caller rejection, load rejection, same-key joins,
  refresh admission/skip, permit release, and saturated contention.
- Use gates proving both subscriber attachment and source lifetime; exercise
  production API names, reporting states, and metrics selection in metered rows.
- Measure transient allocation and post-terminal retention independently. Verify
  counters do not add allocation proportional to rejected requests retained over
  time or blocking waits on the event loop.
- Keep release-quality benchmark commands available for a manual run. Smoke
  checks establish wiring only; publish numerical claims only from reviewed,
  clean, comparable evidence with explicit coverage of newly added rows.

## 13. Public API, Documentation, and Release Go/No-Go

- Freeze configuration, admission error/outcome, mock helpers, effective contract,
  diagnostics, and metric additions together. Update generated metadata and
  canonical guides from the same definitions.
- Pass strict root/module source and binary compatibility against fresh Central
  `4.2.0`, the supported dependency matrix, package/generation guards, full tests,
  current/published consumers, AOT/native, shutdown, and benchmark disposition.
- Assemble immutable evidence from a reviewed clean commit with commands,
  toolchain versions, actual totals, report checksums, and remaining uncertainty.
- Select `4.3.0` only for an additive explicitly selected work-bound contract.
  If characterization supports only compatible defect fixes, record a patch
  scope or no-go without exposing unenforced settings.
- Publish only after final-cut review; verify Central parent/module artifacts
  and a published assembled consumer before moving baselines, archiving V30,
  and selecting the next development coordinate.

## Completion Criteria

V30 is ready to close when selected policies cannot admit more caller, load, or
refresh owners than configured; every terminal path releases the correct owner;
and overload has deterministic, visible behavior without duplicate work or an
implicit queue. Existing `4.2.0` policies keep their behavior when limits are
unset. Operations evidence must distinguish active work from stored responses
and process memory, and all public additions require compatibility, native,
performance, and publication evidence before release. A narrowed or no-go scope
must record its evidence and leave no unenforced public configuration behind.
