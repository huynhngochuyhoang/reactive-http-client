# V32 Resource, Concurrency, and Retention Ownership

> **Status:** characterized for Priority 6 on 2026-09-15
> **Reviewed source:** `c11d281330b48bcae3917f83bd2048913d03dcca` plus the recorded review/test patch
> **Implementation and release scope:** unselected; Priority 8.3 remains the gate

This is an as-is ownership review, not a resource-management refactor. The
[architecture map](ARCHITECTURE-MAP.md) and [composition review](INVOCATION-COMPOSITION.md)
describe the enclosing paths. One additional reproducible gap is recorded as
[V32-F004](FINDINGS.md#v32-f004-rejected-handler-construction-abandons-cache-meter-leases).
It is not evidence that the reported production pod-memory increase has this cause.

## Resource and Terminal Owners

Priority 9 delta, 2026-09-16: [F004/F005 results](ACCEPTED-IMPROVEMENTS.md)
supersede the failed-construction and ordinary-GC gaps below. Public handler
creation now closes its newly allocated manager on failure. The renamed
rejection regression referenced below now asserts no leaked owners instead of
the original gap; its original result remains tied to this review's baseline.
All 16 collection scenarios remain in a controlled lane. Broader Priority 10
verification is pending; other ownership/lock conclusions are unchanged.

| Resource / acquisition | Transfer and terminal owner | Retention boundary / limits |
|---|---|---|
| Eager InputStream, Reader or ReadableByteChannel supplied as a body | Handler `RequestBodyOwnership` wraps streams/channels without delegating close to each writer attempt; its atomic release guard closes on request-body or logical termination, including auth timeout and pre-write cancellation | An already-created resource that is never invoked remains application-owned. Repeated subscriptions/replays do not make a single-use resource reopenable; cleanup failures are logged, not proof of successful close |
| Eager DataBuffer | Guard releases before writer subscription; subscription transfers it to the writer. Publisher buffers instead transfer as demand emits them, with `DataBufferUtils.release` discard hooks | Raw publisher producers own un-emitted buffers and their cancellation behavior. Reference-count assertions are distinct from JVM GC or allocator page return |
| Multipart Resource | Spring's multipart writer opens and closes each stream it acquires, including cancellation/error | One close per open, not universally one open per subscription: content-length determination can perform another read. Caller-owned Resource and unopened streams are not closed speculatively |
| Streaming response envelope / body publisher | Exchange-to-body handoff transfers consumption/cancellation to the application subscriber; DataBuffer discard/release remains necessary | Obtaining a ResponseEntity with a streaming body is not consuming that body. Factory shutdown is not a substitute for application response ownership |
| Materialized or cached response | Successful candidate publication transfers the decoded value to the entry; readers receive that object, not a general deep clone | Eviction drops the cache reference, not application references or arbitrary AutoCloseable resources. Sensitive/redirect responses and identity mismatches bypass publication; no generic response close callback is promised |
| Selected arguments, prepared bytes, context and auth | Per-subscription freezing/preparation owns selected snapshots; shared load and refresh have separate contexts/state. Auth-visible serialized bytes are defensive copies | Cache entry contains the opaque key/value and bounded representation metadata, not the load closure. Caller-retained cold publishers, decoded values, logs, lifecycle records and explicitly captured envelopes remain external roots |
| Factory providers and tracked channels | Business/token providers are assigned to factory fields; `destroy` closes the cache then concurrently disposes providers and closes tracked channels | Tracking and shutdown share one monitor. A channel reported after the shutdown gate is immediately closed. Replacement connectors do not automatically join this set |
| Scheduler, executor, auth provider, registry or custom connector supplied by the application | Owning application/context manages their lifecycle; manager borrows its refresh scheduler | Cache close cancels scheduled refresh work but does not dispose shared Reactor schedulers or arbitrary application executors. An auth implementation may retain tokens or create detached tasks outside starter ownership |
| Metadata, plans, diagnostics | Factory/handler caches retain method-scoped metadata; returned diagnostics are scalar/list snapshots | Retaining a diagnostic snapshot is different from retaining its provider, which owns its bean factory. User observer/logger implementations may intentionally retain full terminal records |

Source boundaries: [handler][handler], [cache manager][manager],
[factory][factory], [storage][storage] and [context snapshot][snapshot].
Fresh wire tests include
`StreamingUploadOwnershipTest#cancellationBeforeBodySubscriptionReleasesEagerStreamingBodies`,
`StreamingUploadOwnershipTest#logicalTimeoutBeforeAuthCompletesClosesEagerBodyWithoutDispatch`,
`StreamingUploadOwnershipTest#peerDisconnectAfterPartialWriteCancelsDemandAndReleasesPooledBuffers`,
`MultipartWireOwnershipContractTest#cancellationBeforeWriteDoesNotOpenResourceAndCancellationDuringWriteClosesIt`
and `TransportResourceOwnershipStressTest#streamingEnvelopeRemainsCallerOwnedUntilDelayedConsumeOrCancel`.
These are bounded fixture observations, not a claim about arbitrary producer implementations.

## Cache Work Ownership Matrix

| State | Acquisition / publication | Finish, invalidation and shutdown |
|---|---|---|
| Policy cache | First selected validated bounds; manager rejects changed TTL/count/byte/refresh bounds instead of accumulating tuples | Manager closes and clears its cache map. Cache creation rechecks closure under that map's monitor |
| Generation and miss token | Missing-key lookup increments active loads; token captures generation | Publication checks generation after observing expiry, and first successful fill wins. `finish` is idempotent; unused generation is removed only with no live entry/load/refresh. Explicit invalidation advances outstanding generations |
| Entry and byte accounting | Current token admits only successful cacheable candidates. Weighted publication/replacement and bypass freshness run under storage lifecycle lock | TTL, size, weight eviction, explicit invalidation, replacement and close release storage/accounting. Decoded response bytes are not object-graph heap bytes |
| Independent miss | Caller subscription owns its token, loader/context and optional load reservation; no shared-flight registry entry | Manager close invalidates publication but does not cancel this externally owned source. Its token/reservation ends at caller/source terminal; counts must not pretend it already stopped |
| Single flight / members | Lookup, reservation and member registration recheck under flight monitor. One source state; each member owns its caller state and deadline | Source terminal removes the flight before result delivery. Last-member cancellation removes before cancelling; detached publishers cannot restart it. Close freezes/removes flights and cancels sources; empty caller completion is a valid shutdown signal |
| Hidden refresh | Current entry token, duplicate check and refresh capacity before loader assembly; deadline is the lesser of refresh timeout and remaining hard TTL | Before-start invalidation is a skip. Started work has once-only terminal classification; failure does not extend hard TTL. Removal/deadline/close cancel it and finish token/reservation |
| Caller reservation | Each selected cold subscription, before preparation/auth/probe/lookup, including hits and waiters | Terminal intent prevents later guarded advancement. Capacity returns only after entered preparation/subscription/cancellation frames unwind |
| Load reservation | Independent miss or shared leader before assembly/subscription, not each waiter or retry | Retry/backoff/auth replay/redirect/decoding/publication stay inside the same source owner; terminal cleanup precedes reuse when no owned frame remains |
| Refresh reservation | Separate policy capacity after entry/duplicate checks | No caller/load slot is consumed. Running cancellation/cleanup retains this capacity until its frame exits; skip does not invent a source terminal |
| Meter leases | Per-manager owner leases shared registry/name/tag meters; live gauges sum owners, counters retain history until the last close | Closing one owner removes its suppliers, not another owner's meters. Last close removes meters and shared ownership map entries. Failed manager initialization rolls back; Priority 9 adds rollback for subsequent public handler construction failure (F004) |

## Construction and Teardown

The Priority 6 [ResourceOwnershipReviewTest][review-test] introduced six cases.
Priority 9 expands it to 17; the rejection description below reflects the current
regression, with the original leak observation separated afterward.

- `ResourceOwnershipReviewTest#earlyValidationDoesNotAcquireAConnectionProvider`:
  with caching and telemetry selected, an invalid base URL leaves both provider
  fields empty and acquires no registry lease. A meter-registration callback
  also detects a transient allocation even if it is subsequently removed; the
  factory's unassigned manager field alone is not the cache-allocation proof.
  Correcting only the URL in the same fixture then creates one observable cache
  owner with a maximum-entry gauge of 16, validating that the instrumentation is active.
- `ResourceOwnershipReviewTest#lateFactoryFailureIsDisposedBySpringOrTheDirectCaller`:
  a throwing customizer fails after business-provider assignment. Two cases
  distinguish a registered lazy Spring FactoryBean from a directly constructed
  factory. Context close invokes disposal only for the registered owner; the
  direct caller invokes `destroy`. A spy verifies disposal delegation, rather
  than trusting `isDisposed` on a never-used lazy pool. This test does not create
  network work during the failing assembly.
- `ResourceOwnershipReviewTest#replacementConnectorStaysApplicationOwnedAfterFactoryDestroy`:
  a real HTTP/1.1 loopback request uses a customizer-supplied connector/provider;
  the starter tracking set stays empty. After factory/context close a separate
  WebClient still dispatches through that connector. The test's application owner
  finally disposes it. Both peer requests are counted.
- `ResourceOwnershipReviewTest#rejectedPublicHandlerConstructionReleasesOnlyItsNewManager`:
  four cases combine telemetry enabled/disabled with and without an overlapping
  valid owner, rejecting authenticated caching through the provider-less public
  create overload three times. Every rejection leaves the meter and owner sets
  unchanged. With telemetry and a live owner, the maximum-entry gauge remains 16
  and that owner's manager stays open. After normal owner/context teardown,
  no owners or meters remain; these assertions precede the defensive reflective
  cleanup in the test's finally block.

Historical Priority 6 observation: the former method
`rejectedPublicHandlerConstructionLeavesMeterLeasesWithoutAReturnedOwner`
ran two cases with telemetry enabled. Each rejection added a lease and 16 maximum
entries; three rejected calls left three owners and a gauge of 48 after normal
teardown. Reflective cleanup was then needed to prevent test pollution. That
result belongs to the reviewed baseline above, not the renamed Priority 9 regression.

Manager-local failure is a passing control:
`CacheWorkTelemetryContractTest#failedCacheConstructionReleasesMetersWithoutAffectingLiveOwners`
rejects absent Caffeine, closes partial metrics and preserves a live overlapping
owner. This does not cover failure after the manager was successfully returned
to `ReactiveClientInvocationHandler.create`.

`getObject` has no universal rollback transaction. Early validation, token-client
auth assembly, TLS/builder/customizer assembly, strict validation, handler creation,
startup logging and proxy creation have different acquisition points. Fields
already assigned remain the factory's teardown responsibility; a manager lost
before assignment cannot be recovered by factory destruction. The public handler
constructor also borrows application components. F004 is reproduced at the public
create boundary; arbitrary custom Spring component exceptions and every late
startup-logging failure were not injected, so they are not additional findings.

Transport shutdown uses one five-second budget for the two provider waits and
tracked-channel drain, not three sequential budgets.
`Priority7HousekeepingTest#connectionTrackedAfterShutdownStartsIsClosedImmediately`
gates late tracking during disposal; the companion disposal test exercises the
shortened shared deadline with never-completing providers. Real pool shutdown is
covered by `TransportResourceOwnershipStressTest#factoryDestroyWaitsForConnectionProviderDisposal`.
The five-second wait starts after synchronous cache close. It does not bound
arbitrary blocking application cancellation callbacks, custom close methods or
application-owned connectors. No universal wall-clock shutdown guarantee is inferred.

## Locks and External Callbacks

This is a source-derived acquisition map, not a global deadlock-freedom proof.

| Held owner / nested operations | Callback or ordering constraint |
|---|---|
| Factory connection monitor -> tracked set | `trackOwnedConnection` registers its removal callback while open; close snapshots under the monitor, then channel close/provider waits run outside it |
| Manager monitor -> admission monitors | Work configuration and close share the manager monitor. Close marks admissions closed, then releases this monitor before meter shutdown and source cancellation |
| Cache-map monitor -> storage lifecycle / metrics | Creation, snapshots, invalidation and final cache close hold the map lock. Cleanup/expiry may invoke synchronous removal observers; this is not a callback-free lock |
| Flight-map monitor -> storage / load admission / state | Lookup/join/reservation and limited-load publication are serialized here. Normal loader assembly, sink delivery and last-member cancellation are outside the explicit flight block |
| Refresh-map monitor -> refresh admission / terminal flags | `finishRefresh` and `cancelRefresh` remove/elect under this monitor, then finish tokens or dispose subscriptions outside the explicit block. Pre-start publication checks use the storage lock separately |
| Storage lifecycle -> removal observer / metrics | Caffeine uses `Runnable::run`. Weighted removals directly invoke the observer inside publication; TTL/size callbacks may run while an enclosing storage operation still holds its reentrant monitor, even though `onRemoval` exits its own synchronized block before notifying |
| Removal observer -> refresh cancellation -> application cancellation code | An enclosing storage/cache-map/flight lock may therefore remain held across cancellation. Do not generalize local 'outside synchronized' placement into 'no external callback under any lock'. Application code must not wait for another thread needing these owners |
| Reservation -> admission release | `complete` sets terminal intent; `exit` releases only with zero active frames. It clears its owner after one release. Admission acquire/close does not acquire a reservation monitor in reverse |
| Metrics instance -> global ownership -> shared-meter monitor -> registry | Registration/removal coordinates leases. Gauge sampling copies suppliers under its shared-meter monitor and releases it before entering cache/admission state. Custom MeterRegistry callbacks remain an extension boundary, not proven harmless |

The cancellation and terminal suites gate callback entry/exit and inspect
replacement admission, source subscription, discard and cache publication, rather
than relying only on gauge values. Coverage includes:

- `CacheWorkOwnershipContractTest#valuedSourceKeepsItsReservationUntilCompletionOrCancellationCleanup`:
  holds between onNext and onComplete, races cancellation, verifies exactly one
  discard and no capacity reuse during a blocking cancellation callback.
- `CacheCallerAdmissionContractTest#asynchronousFilterContinuationRespectsCancellation`
  and `CacheCallerAdmissionContractTest#asynchronousFilterContinuationRespectsTimeout`:
  cancellation before `next.exchange` prevents advancement; an already-entered
  guarded continuation retains capacity until unwind.
- `CacheCallerAdmissionContractTest#cancellationHoldsAdmissionThroughLookupSubscription`
  and `CacheCallerAdmissionContractTest#timeoutHoldsAdmissionThroughLookupSubscription`:
  three controlled lookup/flight/start phases in each case.
- `CacheLoadAdmissionContractTest#everySourceOutcomeReleasesExactlyOnce`
  and `CacheRefreshAdmissionContractTest#everyTerminalReleasesOneRefreshAndLaterAccessCanRetry`:
  source outcomes, empty/error/timeout/cancel, reuse and publication ownership.
- `BoundedLocalResponseCacheContractTest#lateDuplicateCannotRepopulateAfterTheWinningEntryExpires`,
  `BoundedLocalResponseCacheContractTest#explicitEvictionPreventsAnActiveSingleFlightFromRepopulatingTheCache`
  and `LocalResponseCacheObservabilityTest#bypassRecordingIsAtomicWithLoadAndRefreshFreshness`:
  stale generations cannot publish or invent admission history.
- `CacheWorkOwnershipContractTest#factoryCloseAndLateReleaseCannotChangeReplacementOwners`,
  `CacheWorkTelemetryContractTest#limitedOwnersAggregateGaugesAndHistoryUntilLastClose`
  and `CacheWorkTelemetryContractTest#closingRacesCannotRegisterOrIncrementForADepartedOwner`:
  independent/shared close, overlapping/replacement owners and late terminals.

No deadlock was reproduced by these cases. Cross-thread waits introduced by
arbitrary callbacks remain a review limitation; any lock refactor requires a
specific wait graph/reproducer and Priority 8.3 approval, not class size alone.

## Retention Classification and Historical Evidence

| Domain | Permitted conclusion |
|---|---|
| Entry occupancy and decoded-response bytes | Bounded stored representation, not retained Java object size. Eviction does not prove collection if callers retain values |
| Active callers, loads, refreshes and generations | Owned work, not necessarily a leak. Independent sources and executing application callbacks can remain after manager close |
| Java heap retention | Requires a strong-root path or a controlled reachability experiment. F004 has a direct static lease-owner path and monotonically growing owners without requiring GC |
| JVM direct buffers and Netty allocator capacity | Buffers can be released while arenas/pages remain reserved. Reference-count cleanup does not imply process RSS drops |
| Process RSS / pod memory | Includes committed heap, code, thread stacks, libraries and native/allocator state. No pod, cgroup or Istio memory diagnosis is made here |

The source-controlled V29 [memory characterization](../v29/MEMORY-CHARACTERIZATION.md)
records a historical 55-sample, Java 25.0.3/G1, bounded-heap/direct-memory experiment
against its named revision plus test patch. Its [retention audit](../v29/CACHE-RETENTION-OWNERSHIP.md)
records the subsequently corrected mutable-bounds cache-set defect and the
independent caller-owner exception. V30 [active-work characterization](../v30/ACTIVE-WORK-CHARACTERIZATION.md)
records `0b969f089709be67fcf0a1f35d6ad4b16ea60ce1` plus its test patch, before work
limits existed; the [later admission contract](../v30/WORK-LIMIT-ADMISSION-CONTRACT.md)
defines the three selected capacities now implemented. Their dates, versions and
source hashes are retained in the Priority 6 inventory; historical roadmaps are
unchanged. These are reused historical conclusions, not fresh memory measurements
or revalidated raw JFR/heap artifacts. Those old target-only bundles are absent
from this workspace; no numeric RSS/heap finding is promoted as current evidence.

Fresh runs disable explicit GC (`-XX:+DisableExplicitGC`). The collection-dependent
`ResponseCacheRetentionOwnershipTest` is not rerun. Three collection-dependent
methods in `CacheWorkOwnershipContractTest` and one in
`CacheCallerAdmissionContractTest` are excluded explicitly in the recorded command;
no skipped JUnit case is counted as passed. Existing telemetry's optional GC call
does not require collection. `AsyncHandoffOwnershipContractTest` uses deterministic
owner clearing in normal tests and keeps reachability probes in its opt-in lane.
No new test waits for collection. Migrating the remaining legacy forced-GC tests
to a controlled lane is an evidence-gap follow-up for Priority 7, not silently
performed as part of this review.

## Disposition and Verification

Q1 is narrowed by early/late construction controls and confirmed F004. Q4 has an
explicit nested-lock/callback map and deterministic terminal evidence, not a
universal concurrency theorem. Q6 distinguishes real replacement-connector
ownership, pooled-buffer release and application-retained context/records from
process memory. F001-F003 remain unresolved. No production correction, new SPI,
dependency, default or version change is selected.

[Priority 6 in the checklist](CHECKLIST.md)
records final commands and actual totals. Logs, fresh Surefire XML, reachable
source plus uncommitted patch, toolchain, historical record hashes and
`SHA256SUMS` are under `target/release-evidence/v32/priority6/`. Initial compile and
fixture-expectation failures remain separate from passing characterization.
No full-reactor, new consumer, native, performance, heap/GC-reachability or pod
experiment is claimed. Existing wire cases include their protocol-specific scope;
the new replacement connector case is HTTP/1.1, not an HTTP/2 ownership proof.

[handler]: ../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/core/ReactiveClientInvocationHandler.java
[manager]: ../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/core/LocalResponseCacheManager.java
[factory]: ../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/core/ReactiveHttpClientFactoryBean.java
[storage]: ../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/core/CaffeineLocalResponseCache.java
[snapshot]: ../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/core/RequestContextSnapshot.java
[review-test]: ../../reactive-http-client-starter/src/test/java/io/github/huynhngochuyhoang/httpstarter/core/ResourceOwnershipReviewTest.java
