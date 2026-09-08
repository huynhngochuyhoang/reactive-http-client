# V30 Work-Limit and Admission Contract

> **Decision:** proceed with three optional per-policy ownership counts.
> **Delivery boundary:** internal executable specification only; no public
> configuration, admission exception, enum, meter, or runtime enforcement ships
> with Priority 3.

Recorded on 2026-09-08 against reachable baseline
`44502219b071a73a37cc0ff7301eafb3d3a68caf` and the current
`4.3.0-SNAPSHOT` reactor. The [characterization](ACTIVE-WORK-CHARACTERIZATION.md)
established capacity exposure before storage, not a new memory leak.
The [checklist](CHECKLIST.md) records executed checks and remaining gates.

## Configuration Decision

The future group is
`reactive.http.clients.<client>.cache.policies.<policy>.work`.
Names and validation below are frozen for implementation, **not copyable
configuration supported by this revision**.

| Property within work | Unit | Selection |
|---|---|---|
| maximum-concurrent-callers | Foreground caller ownership reservations | Required with any work limit |
| maximum-concurrent-loads | Independent miss or shared miss source reservations | Required with any work limit |
| maximum-concurrent-refreshes | Hidden refresh source reservations | Required with work limits iff refresh is selected; otherwise forbidden |

Every selected value is an integer from 1 through 1,000,000, inclusive.
Raw binding must preserve the integer before narrowing; fractional, malformed,
and out-of-range values fail rather than rounding, truncating, wrapping, or
saturating. Normalize once to bounded integer counts. Compare occupancy to its
maximum before incrementing. Aggregate diagnostics use checked long arithmetic;
overflow must not wrap into a valid count.

Absence of the group, or all three leaves absent/null, means **unselected**.
There is no enabled flag, default maximum, zero-as-disabled convention, inferred
limit, queue size, per-key waiter quota, or extra dependency. One or two leaves
are invalid unless they form the complete pair for a non-refresh policy.
A refresh limit never selects refresh: the existing refresh-after-ms and
refresh-timeout-ms rules must already pass. A refresh policy without any work
selection retains published behavior.

There is no requirement that load capacity be <= caller capacity. A shared
source can outlive its original caller, and a cancelled caller's executing
preparation frame can outlive that caller's signal. These are different units,
not a connection count, byte weight, thread count, or interchangeable semaphore.

Only cache-selected policy definitions receive semantic bound validation.
Unused definitions containing zero/negative/partial work settings remain inert,
as current cache definitions do. Malformed scalar text can still fail ordinary
property binding before selection, just as other typed settings do.

## One Effective Selection

Use the existing `EffectiveCachePolicy.Decision` and the application's
`MethodMetadataCache`; do not introduce another annotation/API-ref parser:

1. Apply method `@CacheDisabled` first.
2. Select the method policy when named, otherwise the client policy.
3. Resolve inherited metadata against the concrete interface and resolve
   `@ApiRef` from that client's API configuration.
4. Apply the existing cache eligibility, semantic-read intent, body/key identity,
   refresh, and customization-safety validation.
5. Normalize work only for that selected policy. No work input can make an
   unresolved verb, invalid cache body, or otherwise ineligible method valid.

The test-only [model](../../reactive-http-client-starter/src/test/java/io/github/huynhngochuyhoang/httpstarter/core/CacheWorkLimitContract.java)
consumes that decision and produces immutable method selections and a named-policy
limits map. It interns limits within one named policy, not across policies or
factories. Its separate input map is a specification fixture, not a second
runtime configuration source.

| Consumer | Existing integration point and required implementation |
|---|---|
| Binding/startup | CachePolicyConfig and MethodMetadataCache validation; attach the normalized work selection to the same effective cache decision |
| Runtime/public handler construction | ReactiveClientInvocationHandler and LocalResponseCacheManager consume the factory-owned frozen selection; no request-time independent defaulting |
| Effective contracts | EffectiveHttpClientContractExporter renders the same selected source and bounds when public configuration becomes available |
| Diagnostics | ReactiveHttpClientDiagnosticsProvider reads frozen limits of an existing factory; before creation it validates configuration without acquiring owners or instantiating lazy optional components |
| Mock helper | MockReactiveHttpClient already uses the metadata/cache manager/handler paths; reuse those paths, including non-deterministic mocks |
| AOT | ReactiveHttpClientBeanFactoryInitializationAotProcessor invokes the configured metadata cache's validation; use the selected properties bean, never create live reservations or optional infrastructure |

AOT and diagnostics must continue to leave replacement client factories outside
starter-only grammar. Mandatory selected properties/metadata may be resolved;
lazy auth factories, resilience registries, schedulers, and cache infrastructure
must not be created to answer whether work is configured.

Priority 3 tests the normalized specification and existing metadata/selection
compatibility at these boundaries. It does **not** claim that bounded values
already flow through production exports or AOT/native images. Production wiring
is required alongside enforcement in Priorities 4-6; integrated mock/native
evidence remains in its later checklist gates.

As enforcement lands, replace the test-only normalization with the shared
production resolver and retain these cases; do not keep two independent
implementations of selection and validation.

When public binding is introduced, basic per-method effective output must ship
in the same change: work selected, policy source, and the three normalized
maximums, with refresh maximum absent when refresh is disabled. Disabled/legacy
methods must remain unselected rather than displaying invented zero bounds.
Detailed live-work metrics and schema-safe diagnostics aggregation remain
Priority 9. Configuration cannot advertise a bound before all selected
dimensions are enforced.

## Scope and Immutability

One factory owns one work-capacity state per selected **policy name**. Every API
using that name shares the state, whether the selection source is client or
method. Equal numeric limits under different names do not merge capacity.
Another factory, context replacement, JVM, or pod owns independent capacity even
with identical client names. A hot API/key may consume all caller slots; there
is no tenant fairness, scheduling priority, or distributed quota.

Freeze the complete selected work map at client startup, before the first
subscription, including absent work and refresh eligibility. Do not retain mutable
configuration maps in a normalized selection. Applications must supply stable
configuration during construction; concurrent mutation is unsupported. Any
detected inconsistent selection fails construction rather than creating capacity.

Changing limits, removing/adding a work selection, or changing a selected policy
mapping during the factory lifetime requires factory recreation. A detected
change fails before accepting new work; it cannot create another limiter, reset
occupied capacity, or turn an admitted load into uncounted work. The immutable
startup selection stays authoritative for already admitted owners and exports.
The internal snapshot's mutation comparison demonstrates this contract; the
current cache manager's existing storage-mutation guard is not presented as
work-limit enforcement.

Omitted limits retain the published path: no work reservations, admission
timers, queues, new meters, or optional library lookup. This does not remove
the existing cache manager or subscription reporting for a cache-selected
legacy policy.

## Reservation Boundaries

| Owner | Acquire | Release |
|---|---|---|
| Caller | Inside each cold subscription, before argument/context freezing, body serialization, finalized-request/auth probes, or lookup | Caller terminal and all synchronous starter-entered preparation callbacks have exited |
| Foreground load | After current cache recheck/flight selection, before loader assembly or subscription | Source terminal/cancellation and final starter-owned processing/publication has unwound |
| Refresh | After current-entry/duplicate/capacity checks, before hidden preparation or loader assembly | Terminal success/error/timeout/cancellation/invalidation and owned processing has unwound |

Fresh/stale hits and coalesced waiters require caller slots. A current flight
requires only one load slot, even with many callers or after the original caller
detaches. An independent miss always needs its own load slot. Retry/backoff,
redirects, auth replay, and decode belong to the same load reservation; they do
not reacquire it as a new source. Refresh has no foreground caller/load slot.

Reservations are exactly-once tokens tied to their owner, not shared flags.
When no callback remains executing, release precedes downstream terminal delivery
so immediate resubscription does not rely on a delayed doFinally. Cancellation
during a synchronous callback releases only after that frame exits, even if
the caller already observed cancellation/timeout. Rejection during that interval
is valid occupied capacity, not a leaked permit. An application-created task
that ignores cancellation outside those observable frames remains application
owned; counts cannot prove that arbitrary external work stopped.

No count introduces a foreground timeout. Existing caller budgets and request
timeouts retain their separate lifetimes. Refresh keeps its mandatory deadline
and hard expiry. Cache eviction does not release a running load's slot.
Manager close prevents new admission/publication but cannot pretend that an
independent external caller or non-cooperative callback has terminated. Later
enforcement must preserve these owners until their actual release boundary.

## Saturation Decisions

| State after the necessary caller gate | Result |
|---|---|
| Caller count already at maximum | Reject before preparation/auth/lookup, including a would-be hit |
| Fresh value | Authorized hit, no foreground source reservation |
| Current same-key flight, load count at maximum | Join if a caller slot was obtained; no second load slot |
| Miss without current flight, load count at maximum | Reject; no loader call, provisional owner left behind, fallback dispatch, or queue |
| Stale entry, refresh count at maximum | Return authorized stale value before hard TTL; record a refresh-capacity skip only |
| Current refresh for this entry | Existing duplicate suppression; not another capacity skip |
| Hard-expired entry | Recheck as a foreground miss; never serve it as a stale fallback |

Recheck the entry before rejecting or installing a flight. Lookup, joining,
capacity reservation, generation registration, and close/removal must form a
consistent atomic transition. The specification's decision tables alone do not
prove these concurrent transitions; Priorities 4-7 must test them with gates.

A refresh-capacity skip leaves value, weight, age, TTL, and generation unchanged.
It retains no trigger/context and schedules no retry. It creates no refresh
duration or terminal refresh record because no refresh started. Another later
access can try again. Refresh occupancy never consumes foreground capacity,
though both can still contend for configured auth/resilience/transport resources.

## Local Error and Terminal Contract

The future public type is
`io.github.huynhngochuyhoang.httpstarter.exception.CacheWorkRejectedException`,
a final RuntimeException with a required nested `Reason` enum and
`getReason()`. Only two reasons exist:

| Reason | Fixed message | Cache outcome |
|---|---|---|
| CALLER_CAPACITY | Response cache caller capacity exhausted | CALLER_REJECTED |
| LOAD_CAPACITY | Response cache foreground load capacity exhausted | LOAD_REJECTED |

Use a reason-only constructor; no arbitrary message, cause, target, policy,
identity, header, body, key, or occupancy object. A stack trace may identify code,
but built-in output retains only structural metadata. Both classify as the
additive `ErrorCategory.CACHE_ADMISSION_ERROR`, not UNKNOWN, RATE_LIMITED,
RESILIENCE_ERROR, or a transport/pool error. Failure stage is null. The exception
and enum additions are reserved here, **not public types in this revision**.

Each subscribed rejected caller gets one error terminal, zero subscription
attempts, requestDispatched=false, no URL/status/headers, no request/response body,
and no inherited failure evidence. Each enabled observer, lifecycle hook, and
exchange logger receives exactly one matching terminal callback. Report only
bounded startup API/client identifiers already permitted by those surfaces;
do not materialize a request just to enrich the error. The internal terminal
map freezes facts, not callback-delivery implementation.

The two new cache outcomes follow the existing explicit cache-observability
selection, independently of MeterRegistry availability. Local rejected calls
must not enter downstream request-timer/health samples. Refresh skips are
separate hidden-work admission observations; their caller remains STALE_HIT.
Byte-admission bypass remains a successful load returning its uncached value,
not a work rejection. No telemetry is implicitly enabled by work selection.

Caller/load rejection is outside starter loader Retry: do not construct the
loader or run its Retry on rejection. An application retry/repeat or another
subscription to the cold publisher creates a new caller/admission attempt; it
can reject again and never receives a reserved place in a queue. Existing
independent loads and shared sources retain their own configured retry behavior.

## Verification and Remaining Gates

[CacheWorkLimitContractTest](../../reactive-http-client-starter/src/test/java/io/github/huynhngochuyhoang/httpstarter/core/CacheWorkLimitContractTest.java)
covers input ranges/binding overflow, absence/inert definitions, refresh
conditions, concrete inheritance, method exclusion/override, API refs,
immutable snapshots, isolated policy/factory values, saturation tables, fixed
terminal facts, replacement-metadata AOT/diagnostics, and the existing runtime
path with limits omitted. Related existing mock, AOT, diagnostics, retention,
and active-work suites protect those paths.

The model uses no Spring bean, production owner, timer, dependency, or public
export. Reservation races, one-terminal delivery, live counters, bounded
preparation/load/refresh enforcement, shutdown, native compilation, and overhead
remain the explicitly named subsequent priorities. No native, published-binary,
GC, or latency evidence is claimed by this contract decision.
