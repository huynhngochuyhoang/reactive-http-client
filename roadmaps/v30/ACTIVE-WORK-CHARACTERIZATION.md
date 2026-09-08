# V30 Active-Work Characterization

Recorded on 2026-09-08 for Priority 2 of the [checklist](CHECKLIST.md).
This is test-only characterization, not a new configuration contract, public
metric, process-memory bound, or release selection.

## Scope and Provenance

The starting revision is `0b969f089709be67fcf0a1f35d6ad4b16ea60ce1`
(`4.3.0-SNAPSHOT`). Its starter production sources have no diff from `v4.2.0`.
The workload runs the current reactor, not a separately resolved published
binary. Reports record the reachable starting commit and dirty source state
because the test/report are new. Priority 1 retains fresh published-baseline
evidence; release-quality provenance remains a later gate.

[ResponseCacheActiveWorkTest](../../reactive-http-client-starter/src/test/java/io/github/huynhngochuyhoang/httpstarter/core/ResponseCacheActiveWorkTest.java)
uses the real invocation handler/proxy, JSON codec boundary, outbound auth
filter, Resilience4j applier, cache manager, and HTTP/1.1 loopback transport.
Each case owns a separate context, manager, connection provider, server, and
refresh scheduler. There is no mock exchange or production instrumentation.
Only the cache ticker is deterministic; caller/transport clocks are unchanged.

## Fixed Workload

| Input | Selection |
|---|---|
| Verbs | GET and acknowledged semantic POST |
| Request body | One fixed synthetic JSON record; selected wire-byte body identity |
| Response | 256 decoded bytes per success |
| Storage | 8 entries, 60,000 ms TTL; count-only or additionally 2,048 decoded-response bytes |
| Foreground burst | 12 subscriptions; 12 distinct identities or one duplicate identity |
| Refresh burst | Seed 8 entries, advance ticker by 1,000 ms, access all 8 twice; 24 caller operations |
| Refresh deadline | 60,000 ms, also bounded by the 60,000 ms hard TTL |
| Normal pool | Starter default 200 connections, Reactor default 400 pending acquisitions |
| Constrained pool | 2 connections, 3 pending acquisitions |
| Selected Bulkhead | 2 concurrent calls, zero wait; other operators unselected |
| Foreground deadlines | Request and logical-call timeouts explicitly disabled |
| Pending-acquire deadline | 120,000 ms, beyond the 10-second fixture observation window |
| Warmup | None; structural assertions, not performance measurements |

Policies acknowledge shared synthetic responses. Auth returns an empty context;
its gate measures per-caller work, not identity-dependent reuse. Disabled cases
invoke explicitly disabled methods and create no policy storage, although the
harness keeps a manager available for inspection.

The normal case preserves the starter's default pool capacity; only the acquire
deadline is extended so it cannot terminate work during observation. The
constrained pending-queue size is a direct Reactor fixture setting, not a new
starter configuration property.

Responses are gated before headers. POST requests are completely consumed and
their fixed JSON bytes verified first. Burst checkpoints wait for dispatch,
auth/serializer completion, and expected flight membership. Auth has a separate
subscriber gate; serialization has an explicit callback-entry counter and latch.
A bounded poll waits for those signals; delays do not establish overlap.

Ordinary checkpoints cover admitted, saturated, traffic-stopped, source-terminal,
and post-close states. Saturated means the complete fixture burst is held, not
that a V30 work limit already exists. Specialized cases name first-caller,
manager-close, and callback boundaries. No new calls enter after traffic stops.

## Measurement Boundaries

| Field | Meaning |
|---|---|
| `activeCallers` | Entered caller subscriptions minus caller terminals, including hits/preparation/waiters |
| `activePreparationOwners` | Auth subscriptions plus executing serializer frames; can outlive cancelled callers |
| `activeSerializers`, `authSubscriptions` | Executing codec frames and cumulative auth subscriptions respectively |
| `foregroundLoadTokens` | Outstanding miss tokens, including independent and shared sources |
| `independentLoads` | Outstanding foreground tokens minus registered shared flights at settled checkpoints |
| `sharedFlights`, `attachedMembers`, `coalescedWaiters` | Registered flights, current members, and members beyond one per flight |
| `hiddenRefreshes`, `refreshTokens` | Registered hidden work and outstanding refresh tokens |
| `generationMapOwners` | Per-key records in cache bookkeeping, including idle stored-entry records |
| `activeGenerationOwners` | Observed states with outstanding tokens, even after removal from the cache map |
| `httpExchangesAwaitingHeaders` | Real exchange subscriptions until headers/error/cancellation; no identity probes |
| `dispatches`, `activeServerResponses` | Requests consumed by the peer and its nonterminal response publishers |
| `entries`, `retainedDecodedBytes` | Stored occupancy; unweighted bytes are unknown |
| `pool` | HTTP/1.1 total/idle/active connections and pending acquisitions; stream fields null |
| `memory` | RSS, heap used/committed/max, JVM direct buffers, server allocator bytes, and threads |

Generation inspection is test-only reflection under the cache lifecycle lock.
Remembered state objects keep independent tokens inspectable after map clearing.
Gates establish settled boundaries; these separately sampled counters are not
an atomic production snapshot. Disabled calls have no cache tokens but still
have HTTP exchanges. At the auth gate preparation owners equal preparing callers;
a non-cooperative callback can remain active after its logical caller terminates.

The existing [ResponseCacheMemoryDomains](../../reactive-http-client-starter/src/test/java/io/github/huynhngochuyhoang/httpstarter/core/ResponseCacheMemoryDomains.java)
sampler uses `-1` for unavailable memory measurements. The server allocator is
not the entire transport/JVM native-memory domain. There are no HTTP/2 findings;
stream counts cannot be inferred from HTTP/1.1. `providerReportsDisposed` is
Reactor's raw state, which can be true before any pool is allocated; it does not
prove that close was invoked.

## Observations

GET/POST and count/weighted variants agree at the gated boundaries:

| Held workload | Callers | Foreground tokens | Flights / waiters | Refreshes | Entries | Classification |
|---|---:|---:|---|---:|---:|---|
| Disabled, 12 blocked requests | 12 | 0 | 0 / 0 | 0 | 0 | Expected caller/transport ownership |
| 12 distinct misses | 12 | 12 | 12 / 0 | 0 | 0 | Capacity exposure before storage |
| 12 independent duplicate misses | 12 | 12 | 0 / 0 | 0 | 0 | Capacity exposure; one generation state with 12 tokens |
| 12 same-key shared callers | 12 | 1 | 1 / 11 | 0 | 0 | Load suppression does not bound callers |
| 12 pre-lookup auth calls | 12 | 0 | 0 / 0 | 0 | 0 | Preparation exposure before load admission |
| Auth released, Bulkhead selected | 2 | 2 | 2 / 0 | 0 | 0 | 10 explicit Bulkhead rejections |
| Auth released, constrained pool | 5 | 5 | 5 / 0 | 0 | 0 | 2 dispatched, 3 queued, 7 pending-limit rejections |
| 8 stale keys after caller completion | 0 | 0 | 0 / 0 | 8 | 8 | Hidden refresh capacity exposure |

The normal pool permits all 12 loads after auth. Registry presence alone does
not select Bulkhead. Selected Bulkhead limits loaders, but all 12 pre-lookup
auth subscriptions and POST serializations have already occurred. Pool limits
act later: queued requests retain tokens and caller state without peer dispatch.

After responses complete, distinct fills retain at most 8 entries and duplicate
fills retain one. Weighted occupancy is exactly 256 bytes per stored response
here. Idle entries retain generation records with zero active tokens until
eviction/expiry/close. This is expected bookkeeping, not live work or a leak.

Additional ownership cases:

- Cancelling the first of 12 shared POST callers leaves 11 members, one token,
  and one wire exchange. The remaining callers complete from that source.
- Manager close during 12 independent GET loads clears maps/storage, but
  caller subscriptions still own 12 tokens referencing one state. They finish
  successfully without late publication. Transport is deliberately open at
  this checkpoint: this is not proof that connections survive full factory
  destruction. Tokens reach zero at source terminal; transport/context then close.
- A custom serializer ignores cancellation interrupts. After caller cancellation
  and manager close its frame still executes, although the caller count is zero.
  Releasing the gate ends it; no auth or wire work follows. Subscription counts
  cannot certify that arbitrary application work or external tasks stopped.

Final harness-close assertions require zero callers, load/refresh tokens, flights,
refreshes, stored entries, HTTP exchanges, total connections, and pending acquires.
Owner paths are [invocation/preparation](../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/core/ReactiveClientInvocationHandler.java),
[load/flight/refresh lifecycle](../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/core/LocalResponseCacheManager.java),
and [generation lifecycle](../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/core/CaffeineLocalResponseCache.java).
The V29 [ownership audit](../v29/CACHE-RETENTION-OWNERSHIP.md) remains unchanged.

## Decision and Limits

Proceed to Priority 3's explicit caller/load/refresh contract. Stored byte and
entry limits do not cap preparation, independent misses, shared members, or
refreshes across stale keys. The evidence supports capacity exposure, not an
immediate production defect fix. No production code changes are part of Priority 2.

No new leak is established. Memory domains are contextual measurements, not pass
thresholds. The harness retains futures/results and observed bookkeeping for
inspection; it does not prove GC collectability or a steady-state plateau.
V29 weak-reference tests separately cover terminal release. Attribution of
production pod growth remains inconclusive without matching private process
evidence; a high/nonzero post-close RSS is insufficient.

This suite produces no JFR or heap dump. Follow-up raw captures must remain
private under target, not in roadmaps or public bundles. Its JSON reports are
internal test evidence, not supported diagnostics or operator-visible live gauges.

## Reproduction

Run from the reactor root:

~~~bash
mvn -B -ntp -pl reactive-http-client-starter \
  -Dtest=ResponseCacheActiveWorkTest test
~~~

The 35 cases write separate JSON files under
`target/release-evidence/v30/priority2/`, including fixed bounds, actual operations
and key cardinality, environment/source state, and named checkpoints. Preserve
reports and Surefire XML per repeat before rerunning. The checklist records
verified regression/repeat evidence; this does not close later release gates.
