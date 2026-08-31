# V29 Cache Retention Ownership Audit

> **Finding:** one cache-set retention defect was confirmed and corrected. A
> mutable `CachePolicyConfig` could create and retain one policy cache for every
> distinct runtime bounds tuple. The manager now accepts one bounds tuple per
> policy name and rejects later bounds mutation. No other starter-owned unbounded
> retention defect was reproduced. Cached values remain intentionally retained
> until TTL, size eviction, explicit eviction, replacement, or factory close.

This audit follows the controlled finding in
[`MEMORY-CHARACTERIZATION.md`](MEMORY-CHARACTERIZATION.md). It records actual
strong owners in the `4.2.0-SNAPSHOT` implementation before the retained-weight
design gate. It does not infer heap size from entry count, RSS, or weak-reference
timing.

## Owner Graph

The root path for enabled response caching is:

```text
Spring bean factory
  -> ReactiveHttpClientFactoryBean
    -> proxy / ReactiveClientInvocationHandler
      -> LocalResponseCacheManager
        -> policy caches / active loads / active refreshes / metrics
```

The proxy and handler share the same factory-owned manager. There is no static
manager, cache, key, value, flight, or refresh registry.

| Retained class or material | Strong owner and creation | Terminal/removal trigger | Shutdown owner |
|---|---|---|---|
| `LocalResponseCacheManager` | `ReactiveHttpClientFactoryBean.responseCacheManager`, assigned from the handler during proxy creation | Lives for the factory lifetime | `ReactiveHttpClientFactoryBean.destroy()` calls `close()` before transport disposal |
| Policy cache and `PolicyBounds` | Manager `caches`, created once for the first validated bounds tuple of each selected policy name | Configuration properties are startup input; a later TTL, maximum-size, or refresh-bound mutation is rejected before lookup instead of retaining another cache | Manager closes every cache and clears `caches` |
| Opaque key, decoded value, `CachedEntry`, retained `ResponseEntity` body/headers | Caffeine storage after generation-checked successful publication | TTL expiry, size eviction, explicit eviction, successful refresh replacement, or close | `CaffeineLocalResponseCache.close()` invalidates and cleans storage |
| `GenerationState` | Per-key `generations`, created by miss or refresh admission | Removed when no load/refresh is active and no entry exists; retained with a live entry to reject stale publication | Cache close clears the map |
| Load/refresh tokens | Active caller/load or refresh object plus generation state | `finish`/`finishRefresh`, including error, empty, cancellation, timeout, eviction, and late-terminal paths | Manager close finishes registered single-flight and refresh tokens. An independent-load token remains owned by its caller subscription until that subscription terminates; close makes its later publication ineligible. |
| `InFlightLoad`, result sink, load state, source subscription | Manager `inFlightLoads` from single-flight admission | Last-member cancellation or one terminal source signal removes the map entry before publishing the result | Manager close clears the map, cancels the source, freezes diagnostics, and terminates the result |
| `FlightMember`, caller state, caller context | One active `InFlightLoad.members` entry per attached caller | Each caller's `doFinally` removes its member independently; the source remains only while another member exists | Flight shutdown releases all members with the flight graph |
| `InFlightRefresh`, frozen trigger context, auth/body/load state, source subscription | Manager `inFlightRefreshes` after stale-hit admission | Success, failure, empty, timeout, hard expiry, entry removal, explicit eviction, or cancellation | Manager close cancels subscriptions, finishes tokens, and clears the map |
| Frozen arguments, prepared context, serialized body, auth context, final request identity, response metadata | Subscription-local Reactor operators and the active load/refresh closure | Caller terminal for pre-lookup failures/hits; load or refresh terminal when dispatched | Registered flights and refreshes are cancelled by close. Independent loads remain caller-owned until their own terminal signal; none of this transient state is copied into a cached entry or diagnostics snapshot. |
| Cache meters, counters, timers, gauge suppliers | `MicrometerLocalResponseCacheMetrics`; the registry strongly owns registered meters and the entries gauge strongly owns its cache | Metrics remain for the factory lifetime, not the entry lifetime | `metrics.close()` removes every owned meter and clears local meter maps before caches close |
| Diagnostics/Actuator aggregate records | Returned immutable scalar/list/map snapshot | Owned only by the caller retaining the snapshot | Snapshot contains no factory, manager, cache, key, value, request, auth, or application-context object |

## External and Long-Lived Owners

- Caffeine owns entries only inside the factory-owned cache. Its synchronous
  removal listener owns a callback to the manager, forming a collectible cycle;
  it is not a static root.
- Micrometer's registry is an external long-lived root while cache metrics are
  enabled. Factory close removes the meters before dropping caches, including
  the strong-reference entries gauge and maximum-size supplier.
- The shared Reactor parallel scheduler is externally owned. Scheduled refresh
  timeout tasks own an active refresh subscription only until terminal signal,
  cancellation, hard expiry, or manager close; the manager does not dispose the
  shared scheduler.
- The diagnostics provider intentionally owns its bean factory for its own bean
  lifetime. A snapshot performs `getSingleton` only for an already-created
  starter factory, copies scalar aggregate state, and does not store that
  factory in the returned result.
- `CacheKeyContract` has a static `ClassValue` for record accessor metadata. It
  retains class-scoped reflection metadata according to `ClassValue` lifecycle,
  never request values, canonical bytes, opaque keys, managers, or contexts.
- With `single-flight=false`, an active miss is not registered in the manager.
  Its caller subscription owns the loader closure, token, key, generation state,
  and request state until caller terminal. Manager close invalidates storage and
  rejects its late publication, but deliberately does not cancel that
  caller-visible work.
- Caller code can retain a returned cold `Mono`, decoded response, exception,
  observer event, lifecycle context, or custom log record. Those are
  application-owned roots and are outside factory cache retention. The starter
  does not add them to a global collection.

## Terminal Proof

`ResponseCacheRetentionOwnershipTest` uses a `ReferenceQueue`, weak references,
bounded five-second polling, repeated named diagnostic GC attempts, and at most
1 MiB of allocation pressure per attempt. GC is evidence only. Each fixture is
isolated in a helper scope so test locals do not become false roots.

The suite proves:

- success retains only the published value while response metadata is released;
  failure, empty completion, simulated serialization failure, and cancellation
  leave no transient owner or generation record;
- TTL, capacity eviction, and explicit eviction release ordinary cached values
  while the manager remains open; refresh replacement, failure, and cancellation
  release displaced or transient values;
- mutating an already-created policy's TTL, capacity, or refresh bounds is
  rejected without creating a second retained policy cache;
- one cancelled waiter releases its context, frozen arguments, caller state,
  and unused proposed load state while the leader and transport load remain
  active;
- a real cache-selected request releases its selected body, bounded serialized
  bytes, prepared context, auth context/header, frozen arguments, and response
  metadata after publication while the manager remains open;
- an independent load remains caller-owned after manager close, cannot publish
  into the closed cache, and releases its closure at caller terminal;
- factory close removes Micrometer roots and makes manager, cache, key, value,
  and a cancelled single-flight closure collectible while the registry remains
  alive; same-tag replacement meters observe only the replacement cache; and
- retaining the Actuator diagnostics map does not retain its provider, bean
  factory, client factory, manager, cache, or decoded value.

The existing deterministic cache suites provide the race half of the proof:

| Boundary | Regression evidence |
|---|---|
| Duplicate completion and generation replacement | `concurrentMissesRemainIndependentAndFirstSuccessfulFillWins`, `lateDuplicateCannotRepopulateAfterCapacityEviction`, `lateDuplicateCannotReplaceAFreshGenerationAfterExpiry`, `lateDuplicateCannotRepopulateAfterTheWinningEntryExpires` |
| Detached shared publishers and waiters | `singleFlightDetachesCallersAndCancelsOnlyAfterTheLastCallerLeaves`, `reservedFlightMemberCannotReconnectAnUntrackedSource`, `singleFlightKeepsEachCallersTimeoutBudgetIndependent` |
| Explicit eviction and close during loads | `explicitEvictionPreventsAnActiveSingleFlightFromRepopulatingTheCache`, `shutdownClearsEntriesAndRejectsLatePublication`, `shutdownTerminatesCoalescedLoadsAndPreventsLatePublication` |
| Refresh replacement/failure/cancellation/hard expiry | `refreshFailurePreservesTheValueOnlyUntilHardExpiry`, `refreshTimeoutAndHardExpiryCancelWorkAndRejectLateResults`, `evictionCancelsRefreshAndRefreshUsesTheTriggeringContext`, `hardExpiryDeadlineCancelsRefreshWithoutAnotherCacheAccess`, `factoryShutdownCancelsAnActiveRefreshAndClearsOwnedState` |
| Meter teardown and recreation | `closeRemovesEveryOwnedMeterAndSameTagsObserveOnlyReplacementCache`, `factoryDestroyCancelsLoadsAndRefreshesClearsEntriesAndAllowsSameTagRecreation` |
| Existing-only diagnostics | `providerSnapshotExportsOnlyBoundedAggregateCacheState`, `providerSnapshotsDoNotInstantiateLazyClientFactories` |

## Decision

One retention defect was confirmed: mutable runtime policy bounds could grow the
manager's policy-cache set. `LocalResponseCacheManager` now retains only the first
bounds tuple for a policy name and rejects later bounds mutation. Cache policy
properties remain startup configuration; live mutation is unsupported. No other
unbounded starter root was reproduced. The controlled RSS delta remains a
separate accounting gap. Priority 4 may evaluate retained weight, but it must not
present weight as a general leak fix.
