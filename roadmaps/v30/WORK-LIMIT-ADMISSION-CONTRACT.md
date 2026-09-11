# V30 Work-Limit and Admission Contract

> **Decision:** proceed with three optional per-policy ownership counts.
> **Current delivery boundary:** Priority 6 exposes the complete optional
> caller/load/refresh configuration with enforcement, basic contract output,
> and AOT binding hints. Priority 9 additionally delivers public rejection
> types/outcomes, live work telemetry, and schema-V1 aggregates; native
> executable evidence is tracked in Priorities 10.4 and 13.2.

Recorded on 2026-09-08 against reachable baseline
`44502219b071a73a37cc0ff7301eafb3d3a68caf` and the current
`4.3.0-SNAPSHOT` reactor. The [characterization](ACTIVE-WORK-CHARACTERIZATION.md)
established capacity exposure before storage, not a new memory leak.
The [checklist](CHECKLIST.md) records executed checks and remaining gates.

## Configuration Decision

The snapshot-only group is
`reactive.http.clients.<client>.cache.policies.<policy>.work`.
It is available on `4.3.0-SNAPSHOT`, not published `4.2.0`. See the
[configuration fragment](../../docs/32-response-caching.md#v30-snapshot-optional-work-limits).
The Priority 4 and 5 sections below describe their earlier internal-only
delivery boundaries; Priority 6 completes the public enforcement gate.

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

The production `CacheWorkPolicy` consumes that decision and freezes immutable
method selections and a named-policy limits map. It interns limits within one
named policy, not across policies or factories. The original
[model](../../reactive-http-client-starter/src/test/java/io/github/huynhngochuyhoang/httpstarter/core/CacheWorkLimitContract.java)
now delegates normalization and selection to this resolver; its separate input
map only builds properties for specification tests.

| Consumer | Existing integration point and required implementation |
|---|---|
| Binding/startup | CachePolicyConfig and MethodMetadataCache validation; attach the normalized work selection to the same effective cache decision |
| Runtime/public handler construction | ReactiveClientInvocationHandler and LocalResponseCacheManager consume the factory-owned frozen selection; no request-time independent defaulting |
| Effective contracts | EffectiveHttpClientContractExporter renders the same selected source and normalized bounds for starter-owned clients |
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

Priority 6 replaces test-only normalization with the shared production resolver
and retains those cases. Public binding and basic per-method effective output
ship together: selected source and three normalized maximums, with refresh
maximum absent when refresh is disabled. Disabled/legacy methods remain
unselected rather than displaying invented zero bounds.
Detailed live-work metrics and schema-safe diagnostics aggregation are delivered
by Priority 9. Configuration cannot advertise a bound before all selected
dimensions are enforced.

## Priority 4 Internal Enforcement

Priority 4 adds the package-private `CacheCallerAdmission` implementation to
the handler/cache-manager path. Its only selection entry is a package-private
test fixture overload; ordinary construction supplies no gate. No bindable
property, public admission exception/category/outcome, metadata, or dashboard
setting is exposed. Load/refresh limits, shared production normalization, and
public selection/effective output remain gated by Priorities 5-6 and 6.3.
The internal caller-only maximum map is frozen per manager; it is not a second
application configuration source.

Each cold cache subscription reserves before argument/context freezing,
selected or auth-required serialization, finalized-request probes, auth, or
lookup. The existing request snapshots and probe chain remain authoritative.
All APIs selecting the same policy name share capacity; another name or manager
does not. Fresh/stale hits and waiters reserve too. A saturated caller gets a
fixed, stackless internal error without preparing arguments or retaining inbound
headers in its terminal records. Enabled observer, lifecycle, and exchange-log
surfaces report one zero-attempt/no-dispatch terminal; downstream-only observers
are excluded via the existing cache-served delivery path. The future public
reason/category/cache-outcome additions above are not claimed by this internal
gate.

Reservations hold only capacity state, never arguments, context, credentials,
or key material. Terminal release is eager and exactly once; cancellation while
freezing, serializing, building a probe, invoking a filter/provider, or
synchronously subscribing its returned publisher holds the slot until that
observable frame exits. Synchronous subscription terminals are delivered after
the frame unwinds, so immediate resubscription cannot race delayed cleanup.
Cancelled preparation cannot continue into a later probe/lookup/dispatch.
Filter continuations retain the original caller guard at `next.exchange`:
a late asynchronous mapper cannot advance after caller termination. If the
continuation already entered exchange construction or subscription, its slot
remains occupied until that synchronous frame exits. The guard does not keep
an arbitrary application mapper's slot occupied while it runs outside those
boundaries; it prevents that mapper from reentering terminated starter work.
Application-created asynchronous work outside these frames remains application
owned, as in the reservation contract below.

The one existing logical-call deadline is armed before subscribing preparation,
including synchronous callbacks; it is not layered with a second timeout.
Detached shared-load/refresh context excludes the caller reservation, as it
already excludes caller reporting state and the caller deadline. A waiter
timeout releases only its caller slot. Manager close rejects new callers but
does not reset occupied slots while external work is still unwinding.

`CacheCallerAdmissionContractTest` exercises these boundaries with gated
callbacks, virtual-time timeout advancement after phase entry, repeated cold
subscriptions, cross-API/name/manager checks, 64 competing acquisitions,
immediate terminal resubscription, and weak-reference release with the manager
still open. Real loopback GET and semantic POST checks use explicitly SAFE
Boot/per-client customization beans and verify frozen context, finalized
header/URI identity, exact POST bytes, warm-hit auth failure, and the existing
empty-auth behavior.

## Priority 5 Internal Foreground Enforcement

The internal manager fixture can now select separate caller and load maximum
maps. `CacheLoadAdmission` uses the same frame-aware `CacheWorkAdmission`
reservation implementation as `CacheCallerAdmission`, but owns independent
per-policy counts and a fixed internal foreground-capacity error. There is
still no bindable work group or new public exception/outcome. Production
normalization and public selection remain Priority 6.3; refresh capacity
remains unimplemented.

An independent miss or newly registered flight reserves before invoking its
loader. Current lookup, flight selection, reservation, and bounded publication
are coordinated so a published hit is rechecked before rejecting for capacity.
A same-key waiter reserves membership, not another source slot. A rejected
miss finishes its provisional generation token and never assembles or subscribes
the loader, including the loader's Retry pipeline.

The load reservation is not tied to the first caller. It remains held across
retry/backoff, hidden auth/redirect dispatches, decoding, response metadata,
and cache publication, including successful byte-admission bypass. Empty,
error, and successful terminals release before notifying callers. Cancellation
waits for already-entered source construction, subscription, retry-hook,
publication, and synchronous cancellation frames to unwind. As with the caller
guard, arbitrary external work that ignores cancellation is not an observable
starter-owned frame.

Flight-member cancellation is attached before source startup, and the source
subscriber is registered before subscription. A reserved waiter can keep an
assembling source alive after the initiating caller leaves; an abandoned flight
cannot reconnect when a delayed member attaches. Terminal membership cleanup
is eager and exactly once. Diagnostic ownership is assigned only once, never
transferred to a late waiter. Detaching the original caller freezes its facts
and suppresses subsequent source retry hooks for that caller; coalesced waiter
terminal records remain transport-free.

Manager close rejects new load reservations and cancels shared work. It does
not reset occupied counts while synchronous frames unwind or while an
externally owned independent load continues. Such an independent load releases
at its own terminal boundary and cannot publish after close. Existing policies
without selected work limits allocate no load gate/reservation; independent
lookup keeps its existing path.

The checklist records gated tests and repeated runs for these boundaries.
Internal active-load counts are test evidence, not a new exported gauge.

## Priority 6 Refresh and Public Enforcement

Recorded on 2026-09-09 against reachable base
`89e78168b83eb74eb9ed03e68c00cc4dde742ec0` plus the Priority 6 working tree.
`LocalResponseCacheManager` reserves refresh capacity separately from caller
and foreground-load counts. Current-entry generation validation, duplicate
suppression, and capacity selection precede loader assembly. A capacity skip
finishes its provisional generation token without retaining a trigger,
changing storage/weight/freshness, or recording a terminal refresh load.

The refresh subscriber is registered before its source subscribes. Assembly,
subscription, response processing/publication, and cancellation use the same
frame-aware reservation machinery as foreground work. Eviction, close, expiry
or timeout can terminate the guard during assembly without allowing a returned
publisher to dispatch afterward. Capacity remains held until entered frames
unwind. Terminal outcome recording and cleanup are once-only. The existing
refresh timeout is armed before assembly and is capped by remaining hard TTL;
there is no recurring scheduler, new timeout property, queue, or deferred trigger.

`CacheWorkConfig` binds nullable Long leaves before range checking; only complete
selections normalize to bounded counts. `EffectiveCachePolicy` validates them
alongside the existing selected cache grammar. Normalized startup selections
initialize all three owner maps before the handler is exposed. The legacy
public constructor lacking a concrete client interface rejects work-selected
methods rather than accepting an unenforceable selection. Factory creation,
both mock clock modes, and direct concrete-interface handler creation share the
same manager path.

Handler invocation and cold subscription check the frozen selection before
admission. Live cache snapshots also reject changed selections; they cannot
report newly configured limits as though existing owners used them. Contract
export and AOT validate without creating caches or lazy optional infrastructure.
Foreign replacement factories remain outside starter-only work validation.
Metadata, generated property reference, native binding hints, and basic Markdown
contract rendering are updated together. Legacy policies allocate no admission
objects, emit no work bounds, and retain their existing terminal behavior.

Tests cover public binding, partial/range/refresh validation, immutable mappings,
stale-hit skips, byte-weight preservation, unchanged expiry, hidden deadlines,
foreground availability during refresh saturation, and separately owned
policies/factories. Gated assembly/subscription/publication/cancellation cases
prove slots are not reusable before their entered callbacks exit. Concurrent
64-key triggers respect a three-refresh bound. Both mock time modes and
starter-owned versus replacement-client AOT/contract paths are included.
The checklist records actual runs, report totals, and source provenance.

## Priority 7 Composition Evidence

`CacheWorkCompositionContractTest` selects the public policy work properties
through the production manager and invocation handler. It uses real
Resilience4j registries/operators, virtual-time caller deadlines and Retry,
gated single-flight responses, and loopback HTTP for response-read timeouts
and `307`/`308` POST redirects. No production behavior changes were needed.

| Boundary | Verified observations |
|---|---|
| Local rejection | No loader operator assembly/subscription or business guard/circuit activity; no dispatch evidence |
| Real CircuitBreaker/RateLimiter/Bulkhead rejection | Admitted caller/source slots return to zero; one error per terminal surface; zero attempts/dispatch |
| Retry backoff and hidden replay | One source slot across attempts/dispatches; no implicit Retry or unsafe replay permission |
| Auth and identity | Warm hits still authorize; refreshed auth is not reused stale on outer Retry; auth cannot mutate wire bytes; changed identity/target bypasses publication |
| Redirect | Two actual POST bodies for one admitted source, a joined waiter, and a subsequent cached hit; no extra dispatch from admission |
| Independent deadlines | Either caller can expire without stealing the surviving source slot; waiter terminals contain no source evidence |
| Response consumption | Logical timeout/cancellation preserve known response state; native response timeout ends the source and both callers |
| Hidden refresh | Real auth/operators run with a separate refresh slot and deadline; no extra caller terminal for hidden work |
| Storage byte bypass | A successful uncached response releases caller/load capacity and remains successful load work |

The final diagnostic assertions compare error identity, attempts, status, URL,
failure stage and response-header sentinels across observer, lifecycle and log
surfaces where those fields exist. A classified response-body timeout precedes
the terminal pre-dispatch auth failure, proving prior failure evidence resets.
Lifecycle/log prepared arguments are not final dispatch evidence; generated
idempotency headers are intentionally preserved. Transparent connector
redirects still report the WebClient request URL, not a hop-by-hop wire history.

Foreground limits do not impose deadlines. Deliberately nonterminating work
with request/logical timeouts disabled keeps its slot until explicit cancellation.
Applications need an end-to-end logical-call budget plus appropriate native
phase timeouts; separate refresh capacity does not isolate shared transport,
auth or resilience resources. See the work-limit composition section in
[response caching](../../docs/32-response-caching.md).

Exact test totals, commands, source revision and copied artifacts are recorded
in [Priority 7 of the checklist](CHECKLIST.md).
Public rejection types/live work telemetry remain Priority 9, and this is JVM
contract evidence, not native-image or performance evidence.

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

The public type delivered by Priority 9 is
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
and enum additions are delivered with Priority 9.

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

### Priority 8 Ownership Evidence

`CacheWorkOwnershipContractTest` exercises selected limits at synchronized
checkpoints: same-key/many-key contention, independent/shared loads, racing
success/error/cancellation, immediate assembly retries, cancellation before
attachment, first-cache creation versus close, and refresh completion versus
eviction. Acquired/released caller counts reconcile with live reservations;
source terminal callbacks and load/refresh histories are counted independently.
Generation owners and scheduled tasks are inspected only by tests.

Two runtime corrections accompany this evidence. The source reservation guard
brackets actual upstream cancellation with `try/finally`, independent of which
terminal wins; a `doOnCancel`/`doFinally(CANCEL)` pair could strand a callback
count when success won. Manager close now shares the work-configuration lock
for limiter installation/closure, without holding that lock over application
cancellation callbacks.

Bounded reference queues verify detached/rejected caller arguments, prepared
bytes, auth state, context, callbacks and loader closures are collectible while
another source remains active. A source's own state remains reachable until its
terminal. Explicit eviction releases cached values before close while a running
independent load retains its slot and loses publication rights. These checks
complement the existing real-proxy preparation and retention tests; they do not
infer ownership from RSS or introduce production GC.

Factory destruction terminates registered flights/refreshes without advancing
the test clock toward their ordinary deadlines (one-hour foreground timeout,
30-second refresh timeout). Independent caller-owned loads can survive manager
close and release only at their own terminal. Recreating a factory with the same
registry/tags leaves the new capacity, entries, snapshot and meter registrations
unaffected by old late releases. After the last metric owner closes, meters are
absent, not zero-valued history. Arbitrary blocking application cleanup must
still cooperate; it is not forcibly interrupted by the cache manager.

The replacement fixture records these synchronized checkpoints (caller/load/
refresh counts are test-only owner observations, not proposed public meters):

| Owner checkpoint | Callers | Loads | Refreshes | Entries | Cache meters |
|---|---:|---:|---:|---:|---|
| Shared factory before close | 2 | 1 | 1 | 1 | Present |
| Shared factory after close | 0 | 0 | 0 | 0 | Absent at last owner |
| Independent factory before close | 1 | 1 | 1 | 1 | Present |
| Independent manager after close, caller still active | 1 | 1 | 0 | 0 | Absent at last owner |
| Independent old owner after caller terminal | 0 | 0 | 0 | 0 | No late re-registration |
| Replacement during old late release | 1 | 1 | 0 | 1 | New owner's registrations unchanged |

Exact final commands, totals and source provenance are in the
[execution checklist](CHECKLIST.md). This is JVM ownership evidence, not native
image, benchmark, public work telemetry or an assembled-consumer claim.

[CacheWorkLimitContractTest](../../reactive-http-client-starter/src/test/java/io/github/huynhngochuyhoang/httpstarter/core/CacheWorkLimitContractTest.java)
covers input ranges/binding overflow, absence/inert definitions, refresh
conditions, concrete inheritance, method exclusion/override, API refs,
immutable snapshots, isolated policy/factory values, saturation tables, fixed
terminal facts, replacement-metadata AOT/diagnostics, and the existing runtime
path with limits omitted. Related existing mock, AOT, diagnostics, retention,
and active-work suites protect those paths.

The original model remains a test fixture, not a second production resolver.
Priorities 4-8 cover bounded preparation, foreground and refresh sources,
feature composition, shutdown/races, and bounded diagnostic-GC collection tests.
Live telemetry, assembled-consumer/native execution, and performance evidence
remain subsequent priorities. No native executable, published-binary, or
latency evidence is claimed by these implementation steps.
