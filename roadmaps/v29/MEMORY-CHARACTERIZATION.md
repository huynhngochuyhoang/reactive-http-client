# V29 Response-Cache Memory Characterization

> **Finding:** expected bounded retention; no production-code memory leak was
> reproduced by the deterministic workload. Process RSS remains an accounting
> gap and is not treated as Java heap or starter-owned retention.

## Scope

This finding classifies the V29 Priority 2 workload before any production cache
code is changed. It is not a general proof that an application cannot leak, and
it does not infer retained Java heap from process RSS.

The recorded run used:

- commit `8ece62267b8d86112cce9276863e46028f3dedc0` plus the uncommitted,
  test-only characterization changes in this finding;
- Java `25.0.3`, HotSpot, Linux `amd64`, epoll, and Netty's adaptive allocator;
- fresh JVMs with `-Xms128m -Xmx128m`, `-XX:MaxDirectMemorySize=64m`, and G1;
- five repetitions of each of 11 scenarios, for 55 isolated samples;
- a 4 KiB synthetic decoded `byte[]`, eight keys, concurrency eight, one
  connection, a one-second TTL, and maximum size eight except for the
  four-entry pressure case.

Run the same bounded workload with:

```bash
scripts/run-v29-memory-characterization.sh
```

Raw properties, child logs, per-sample values, and aggregate values remain
target-only under
`target/release-evidence/v29/priority2/characterization/`. The prior bounded JFR
remains under `target/release-evidence/v29/priority2/profiling/` because profiles
can contain application data.

## Observations

The values below are five-run means. Heap values are taken after named explicit
GC checkpoints; no memory value is a correctness threshold.

| Scenario | Steady entries | Steady heap change | Closed heap vs control | Closed RSS vs control |
|---|---:|---:|---:|---:|
| Cache disabled | 0 | +18.0 KiB | 0 | 0 |
| Cold miss | 8 | +225.0 KiB | +200.4 KiB | +3.15 MiB |
| Warm hit | 8 | +225.2 KiB | +199.6 KiB | +1.89 MiB |
| Maximum-size pressure | 4 | +221.4 KiB | +206.9 KiB | +2.02 MiB |
| TTL expiry and reload | 8 | +240.3 KiB | +205.1 KiB | +4.26 MiB |
| Explicit eviction and reload | 8 | +238.2 KiB | +195.9 KiB | +3.83 MiB |
| Duplicate miss | 1 | +195.8 KiB | +217.6 KiB | +3.09 MiB |
| Single flight | 1 | +205.6 KiB | +199.3 KiB | +2.45 MiB |
| Refresh | 1 | +222.8 KiB | +223.3 KiB | +2.72 MiB |
| Cancellation | 0 | +198.4 KiB | +183.7 KiB | +0.90 MiB |
| Factory close | 0 | +235.9 KiB | +219.0 KiB | +2.58 MiB |

Capacity pressure completed eight loads while occupancy remained exactly four
entries in every repetition. Cold-miss and warm-hit occupancy remained exactly
eight. This structurally proves the configured entry plateau; the 4 KiB values
are too small relative to framework state for the heap means to show linear
four-versus-eight scaling.

TTL expiry and explicit eviction each reached zero entries before reload in all
five repetitions. Their mean used heap fell by 37,008 and 34,557 bytes,
respectively, at those release checkpoints. The reduction is directional
evidence only because GC, class metadata, thread state, and allocators remain
active.

Refresh, cancellation, duplicate miss, single flight, and factory-close races
each ran in five fresh JVMs. Duplicate miss produced eight load subscriptions
and eight downstream dispatches but one winning entry. At every final checkpoint
all scenarios reported zero entries, in-flight loads, in-flight refreshes, and
pool connections. The pool was disposed and its meter registrations were gone.

## Ownership Classification

The bounded JFR's class-level leak view listed
`ConcurrentHashMap$Node`, `Object[]`, Netty thread-local/allocator/channel
classes, `String`/`char[]`, Reactor sink/AQS classes, and `byte[]`. This view does
not contain reference paths, so a class name alone is not evidence that the
starter owns it.

| Retained shape or signal | Classification | Evidence and owner path |
|---|---|---|
| Decoded `byte[]` values | Application payload, intentionally cache-retained while an entry exists | The fixture counts every 4 KiB allocation. The production path is factory -> `LocalResponseCacheManager.caches` -> `CaffeineLocalResponseCache.cache` -> cached entry -> decoded value. Expiry, eviction, and close reduce structural occupancy to zero. |
| Cache keys, entries, and generation state | Starter-owned while a policy cache is live | `CaffeineLocalResponseCache` owns the cache and generation map. `invalidateAll()` removes entries; `close()` invalidates entries and clears generations. Priority 3 will add reference-path proof rather than infer collectability from counters alone. |
| Loads, waiter state, and refresh tokens | Starter-owned only while work is active | `LocalResponseCacheManager.inFlightLoads` and `inFlightRefreshes` are the explicit roots. Cancellation and close dispose subscriptions and clear both maps; every final sample reports zero. |
| Netty allocator magazines, direct buffers, channel contexts, and internal thread locals | Transport-owned | JFR names Netty classes directly. Netty allocator usage remains 1-2 MiB after the scenario while the starter's pool reports zero connections and disposed state. This is allocator/runtime retention, not an active starter pool. |
| Reactor sinks and AQS nodes | Framework/JVM-owned unless a later reference-path audit proves otherwise | These support publishers, schedulers, and synchronization. Scenario close reports no active starter load or refresh owner. |
| `ConcurrentHashMap` nodes, `Object[]`, `String`, `char[]`, and generic `byte[]` | Unresolved JVM/framework/application aggregate | The class-only JFR cannot assign these objects to the cache. They remain an accounting category for the Priority 3 reference-path audit, not a confirmed leak. |
| Process RSS above heap | JVM/transport/native accounting gap | Closed RSS remained 0.90-4.26 MiB above the cache-disabled mean while GC-stable heap differed by only 184-223 KiB and structural starter owners were zero. RSS includes committed heap pages, code cache, thread stacks, native libraries, and allocator arenas. |

`ReactiveHttpClientFactoryBean.destroy()` closes the cache manager before
disposing transport resources. The manager closes metrics, clears flights and
refreshes, disposes their subscriptions, closes every policy cache, and clears
its cache map. These source paths explain the structural zeroes but do not
replace the weak-reference and root-path evidence required by Priority 3.

## Decision

Do not change production cache code based on the reported pod RSS increase.
The controlled workload reproduces bounded decoded-value retention and separate
JVM/transport memory, not monotonic starter-owned growth. Continue with Priority
3 to prove collectability and external-owner paths. Reopen this classification
as a confirmed leak only if that audit finds a surviving starter root or a
production capture shows monotonic growth after occupancy, flights, refreshes,
and pools have returned to zero.
