# V29 Retained-Weight Admission Semantics

Recorded on 2026-08-31 from durable baseline commit
`fd762589f038fd41ee85856dd78576d02cd1a23e` and the
`4.2.0-SNAPSHOT` response-cache implementation. This source-controlled
admission contract and its candidate evaluation are part of the reviewed V29
Priority 4 change layered on that baseline.

## Scope

This document completes Priority 4.2 over an abstract weight unit. It does not
select decoded representation bytes or an application-supplied estimate, does
not make the Priority 4.3 go/no-go decision, and adds no public configuration or
SPI.

The semantics apply only if a future policy explicitly selects a positive
aggregate weight budget. TTL and `maximum-size` remain mandatory independent
bounds. Weight is never described as Java heap, object-graph size, RSS, direct
memory, or container memory.

## Abstract Model

For one policy cache:

- `B` is the configured aggregate weight budget and must be a startup-valid
  positive integer in the unit selected by Priority 4.3.
- `M` is the existing positive `maximum-size` entry-count limit.
- `R` is the sum of weights stored on the entries currently retained by the
  cache. Pending loads, waiters, refreshes, and rejected candidates do not
  contribute to `R`.
- A stored entry owns its immutable weight alongside its value and write time.
  Removal uses that stored weight; it never invokes a weigher or traverses the
  value again.
- A measurement produces exactly one of `known(w)`, `unknown`, `invalid`, or
  `overflow`. Unknown, invalid, and overflow are distinct from `known(0)`.

The externally observable invariant after every completed cache operation is:

```text
entryCount <= M
0 <= R <= B
R == sum(weight of each currently retained entry)
```

The storage policy may choose an eviction victim or decline an otherwise valid
candidate. No API promises LRU/LFU order or that every `w <= B` candidate will
remain cached. It does promise that storage rejection cannot change the
successful caller value.

## Measurement and Admission Outcomes

| Input or result | Admission behavior | Caller behavior | Accounting behavior |
|---|---|---|---|
| Weight budget absent | Use the published TTL plus `maximum-size` path without measurement | Unchanged `4.0.0`/`4.1.0` behavior | No weight state exists |
| Configured budget is zero, negative, missing its required unit/provider, or outside the selected numeric bound | Reject the policy at startup with client, policy, and property context | Client proxy is not created | No cache or weight state is created for the invalid policy |
| Empty `Mono` completion | No cache candidate exists | Complete empty as today | No measurement, entry, or weight |
| Response rejected by status, sensitive/custom response header, request-identity mismatch, or other existing eligibility rule | Bypass storage before admission | Return the successful decoded result where current semantics do so | Discard any already collected decode count; do not invoke an application weigher and do not change `R` |
| `known(0)` | Eligible for storage | Return the successful value | Store weight zero if admitted; TTL and `M` still bound the entry |
| `known(w)` where `0 < w <= B` | Present the candidate to generation validation and bounded storage | Return the successful value whether retained, evicted, or declined | Commit `w` only if that exact candidate becomes retained; synchronous maintenance restores `R <= B` and `entryCount <= M` before publication completes |
| `known(w)` where `w > B` | Bypass storage; do not evict existing entries for a candidate that can never fit | Return the successful value uncached | No generation or aggregate-weight change |
| `unknown` | Bypass storage; never substitute zero, one, a type constant, `Content-Length`, or another estimate | Return the successful value uncached | No generation or aggregate-weight change |
| Negative application result or other invalid measurement | Treat as a weigher contract violation and bypass storage | Return the successful value uncached; a future bounded structural diagnostic may identify the policy and reason, never the value | No generation or aggregate-weight change |
| Per-entry calculation overflow | Classify as overflow and bypass storage | Return the successful value uncached | Never wrap, clamp, or saturate the candidate weight |
| Aggregate addition/subtraction overflow or an impossible negative total | Fail closed for storage and classify an internal accounting defect | Preserve the successful caller result; do not publish the candidate | Never expose a wrapped total; implementation must retain or restore the last valid entry/accounting state |
| Application weigher throws an ordinary runtime exception | Classify the measurement as invalid and bypass storage | Preserve the downstream success rather than turning an optional cache decision into a business failure | No entry or weight change; fatal JVM errors are not converted into cache outcomes |

A present empty value such as `""`, an empty collection, or `Optional.empty()` is
not an empty completion. It follows its actual measurement result, including a
valid zero, and still consumes one `maximum-size` slot if retained.

## Publication and Accounting Boundary

Measurement may happen outside the cache lifecycle lock, but application code
must never run while that lock is held. Publication revalidates all generation
and lifecycle facts after measurement. The atomic storage transition is:

1. Reject a closed cache, stale token, invalidated key, or already-retained
   winner without changing entries or accounting.
2. Reject unknown, invalid, overflowing, or individually over-budget
   measurements without changing entries, generation, or accounting.
3. Associate the immutable candidate weight with the candidate value.
4. Commit the entry only while its token is current.
5. Apply entry-count and aggregate-weight maintenance synchronously enough that
   the completed operation cannot expose either configured bound as exceeded.
6. Derive `R` from actual entry transitions. An insertion, replacement, or
   removal is charged exactly once; a proposed or losing candidate is never
   charged.
7. Advance generation only for an actual storage transition or an explicit
   invalidation/removal transition that already advances it under the existing
   replay-safety contract.

The entry, not the load token or measurement callback, is the accounting owner.
Removal callbacks carry entry identity and stored weight so duplicate callbacks
cannot subtract twice and value inspection is unnecessary.

## Transition Semantics

| Transition | Required weight behavior |
|---|---|
| First fill | The first generation-current, admissible candidate that storage retains owns its weight. A successful but unknown, invalid, overflowing, or individually over-budget candidate bypasses without preventing a later duplicate candidate from filling the still-empty key. |
| Concurrent duplicate misses | Each caller receives its own successful result. Measurement may be prepared once per candidate outside the lifecycle lock, but only the winning retained entry changes `R`. Losing candidates are discarded without accounting or eviction side effects. |
| Candidate admitted then immediately evicted by storage policy | Generation still records the completed storage/removal transition so a stale duplicate cannot repopulate it. `R` reflects the final retained set, commonly with no contribution from that candidate. |
| Ordinary replacement | Subtract the exact stored weight of the displaced entry and add the candidate weight as one lifecycle transition. There is no interval visible after completion in which both weights remain charged. |
| Refresh success | Keep the old value and weight authoritative while measuring. If the refresh token remains current and the replacement is admissible, atomically replace old weight with new weight, then enforce both bounds. |
| Refresh unknown, invalid, overflow, individually over-budget, storage-declined, failure, empty, timeout, or cancellation | Preserve the old entry, old weight, and original hard-expiry deadline. Do not restart TTL or partially transfer weight. |
| Refresh racing hard expiry, explicit eviction, replacement, or close | The stale refresh token cannot publish or alter `R`. Any prepared measurement is discarded at refresh terminal. |
| TTL expiry | Remove the entry and its stored weight exactly once when expiry becomes effective. A later load starts from the post-removal generation. |
| Entry-count or weight eviction | Remove each selected entry and its stored weight exactly once. Metrics may classify the cause later, but accounting does not depend on metric delivery. |
| Explicit key/policy eviction | Invalidate publication tokens before removing entries, subtract every removed stored weight exactly once, and reject late load/refresh publication. |
| Cache/factory close | Reject new admission, invalidate active publication rights, remove all entries, set retained weight to zero, and release weight/accounting owners. Independent non-single-flight work may finish for its caller but cannot republish. |

## Concurrency and Snapshot Rules

- `R` and the retained entry set have one synchronization owner. A separately
  updated counter that can race removal callbacks is not acceptable.
- Snapshot, gauge, and diagnostic reads report a coherent post-transition value;
  they do not sum values by traversing the cache on demand.
- A pending candidate's weight is subscription-local. It is released on losing
  a race, admission bypass, failure, empty completion, cancellation, timeout,
  eviction invalidation, or close.
- Single-flight waiters do not own or add weight. The shared leader candidate is
  measured once by the chosen implementation boundary and charged only if its
  entry is retained.
- An application weigher, if selected later, must be pure, non-blocking,
  bounded, and free of cache calls. The starter cannot safely enforce a timeout
  by blocking or moving arbitrary work to a hidden scheduler.

## Compatibility and No-Op Contract

For every published `4.0.0`/`4.1.0` policy that does not explicitly select a
weight budget:

- policy validation, key derivation, hits, misses, duplicate loads, single
  flight, refresh, TTL, entry-count eviction, and returned object identity remain
  unchanged;
- no response body counter, candidate weight, aggregate counter, weighted cache,
  application-weigher lookup, callback, meter, diagnostic field, or support
  output is allocated or activated;
- a merely declared weigher bean is not resolved or instantiated by an
  unweighted policy, including lazy beans;
- cache-disabled clients still do not create a cache manager or weight state;
- existing public constructors and methods are not changed or removed. Any
  future API after a Priority 4.3 GO must be additive and compatibility-covered;
  and
- absence of the optional dependency used by a future weigher implementation
  cannot affect an unweighted policy beyond the already documented Caffeine
  requirement for response caching itself.

Runtime mutation of TTL, `maximum-size`, refresh bounds, a future budget, unit,
or weigher selection remains unsupported after the policy cache is created. A
future implementation must reject changed effective bounds instead of retaining
a second accounting domain.

## Deferred to Priority 4.3

This semantic contract does not select a measurement unit, property name,
numeric maximum, storage implementation, public SPI shape, meter names, or
diagnostics schema. Priority 4.3 must choose exactly one surviving candidate or
record a no-go. A no-go leaves these semantics dormant and preserves the
published entry-count-only contract.
