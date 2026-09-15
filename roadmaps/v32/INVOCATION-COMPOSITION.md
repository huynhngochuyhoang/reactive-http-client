# V32 Invocation and Composition Review

> **Status:** characterized, Priority 5
> **Reviewed:** 2026-09-15
> **Source:** `e714af451cce5e24d74183cf23936819278ad086` plus the recorded review/test patch
> **Implementation and release scope:** unselected

This extends the [composition map](ARCHITECTURE-MAP.md) and
[selection review](EFFECTIVE-POLICY-SELECTION.md), not the supported surface.
The reactor remains `4.5.0-SNAPSHOT`, published baseline `4.4.0`. No production
code, dependency, configuration default or public contract changes in this review.
Priority 8.3 still requires a maintainer decision before corrections/extractions.

## Preparation and Publication

| Boundary | Owner and as-is operation | Constraint to preserve |
|---|---|---|
| Invocation | [Handler][handler] resolves concrete RequestPlan, EffectiveApi and cache decision; ordinary arguments resolve before returning the publisher | Ordinary invocation is not universally subscription-frozen; invalid declarations can fail before a logical-call record exists |
| Cached subscription | `cacheCaller` creates caller reporting state, resolves optional reporting components, acquires configured caller admission and arms the logical deadline before subscribing preparation | Each subscription has its own budget/reservation. A local rejection cannot authorize, serialize, look up or enter transport/resilience work |
| Selected snapshot | `freezeArguments`, argument/default resolution, context/generated idempotency resolution, request-target snapshot, `prepareContext` | Freeze only supported selected dimensions plus required path/query projections; `shared-response` acknowledges omitted inputs, not universal deep copying |
| Body preparation | `prepareCacheSelectedBody` produces a bounded selected wire representation and body-presence/media-type identity; `prepareCacheLoadBody` also supports ordinary auth preparation of unselected bodies | Selected JSON uses the bounded codec path; one prepared representation supplies the key and writer. Do not impose selected-body rules on an acknowledged unselected GET body |
| Pre-lookup probe | `authorizeCacheLookup` builds through a cloned WebClient with the same defaults/filters, guarded when caller work is selected; the final identity filter captures method, URI and headers and returns 204 locally | Both authenticated and unauthenticated paths reach the terminal non-dispatching probe. SAFE classification covers the whole applicable builder mutation, not only filter presence |
| Auth body boundary | [OutboundAuthFilter][auth] isolates prepared raw bytes for the provider and applies the body-specific AuthContext validator, including 401 replay | Auth-visible byte mutation must not alter selected wire bytes; validation of equivalent Content-Type is independent of parameter insertion order |
| Key and context | `CacheKeyContract.derive` combines frozen inputs/context, final request identity and selected serialized body key; prepared context surrounds authorization and lookup | Context selected for identity is the same frozen representation seen by authorization. A raw context value is not retroactively deep-copied everywhere in the application |
| Lookup and foreground admission | [Manager][manager] looks up atomically with flight selection/optional load acquisition; a reserved member cannot reconnect a removed flight | Hits need no foreground slot. A waiter consumes caller capacity but not another load slot. Guard the subscription frame, not just assembly of the returned Mono |
| Load attempt | `invokeResolved` uses separate load state; stateful request construction begins a visible attempt, prepares idempotency/body, enters WebClient and records final request/response facts | Outer retry re-subscribes this request source. Cache caller timeout is not installed as the shared source's outer timeout |
| Response and publication | Status/body decoding and response metadata precede candidate selection; `cacheResponseMetadata` derives against successful final WebClient identity again, then storage applies header/status, size/weight and token checks | Identity mismatch bypasses storage, not a retroactive rekey or a promise to undo the response delivered to existing callers. Redirects are separately transport-owned |

Request construction versus subscription is significant: user filters can do work
when their `filter` method is called and again in the returned publisher. The
invocation trace below counts entry into each named callback, not all side effects
a custom publisher could start. Application-managed detached subscriptions are
not automatically brought under a starter reservation.

### Finalized Request Order

[Factory.buildWebClient][factory] starts from the selected builder (which can
already contain Boot WebClientCustomizer mutations), sets base URL/connector/codecs,
then appends correlation, optional OutboundAuthFilter, applicable ordered
ReactiveHttpClientCustomizer mutations, framing validation and final observation.
`defaultRequest` runs during each WebClient request build before filter traversal.
A customizer can rearrange/replace filters or the exchange function; the order
below applies to the tested append-only SAFE customizer, not arbitrary rewrites.

The probe clone adds `cacheRequestIdentityFilter` last, so custom downstream URI
rewrites are seen before lookup even after auth. The real request carries final
identity observation too. A replacement exchange function is never called on a
successful probe/hit, but its filters/defaults are: SAFE is an application assertion
about replayable pre-lookup behavior, not automatic analysis of arbitrary code.

## Counted Execution Paths

New `InvocationCompositionReviewTest#factorySeparatesProbeOuterAttemptAndAuthReplayWithoutAMeterRegistry`
uses a real Spring FactoryBean, explicit SAFE replacement builder and per-client
customizer, real RetryRegistry, an InvalidatableAuthProvider and append-only trace.
There is **no MeterRegistry**; observer, lifecycle and exchange-log collections
assert one terminal each, including zero-attempt cache outcomes. The exchange
function returns synthetic responses: these are exchange invocations, **not TCP dispatch**.
The replacement builder models already-applied Boot customization; this is not
a new full Boot auto-configuration/assembled-consumer run.

Notation: `D` defaultRequest, `U` upstream builder filter, `A` getAuth, `C`
downstream per-client filter (rewrites `/read` to a fixed synthetic `/final`),
`X` exchange function, `I` invalidation. Terminal probe return is implicit after C.
Counts exclude a deliberate warm-up where noted. Rechecking every selected case
as a fresh hit verifies that the finalized rewrite did not prevent cache filling.

| Scenario | Exact trace / counts | Visible attempts |
|---|---|---|
| Ordinary success | `D U A C X` | 1 |
| Cached miss, success | `D U A C; D U C X` | 1 |
| Fresh hit (after warm-up) | `D U A C`, zero X | 0 |
| Miss, 503, outer retry, 200 | `D U A C; D U C X; D U A C X` | 2 |
| Miss, 401, invalidation, 200 | `D U A C; D U C X; I A C X` | 1 |
| Miss, 401 replay yields 503, outer retry yields 200 | `D U A C; D U C X; I A C X; D U A C X` | 2, three X |

`InvocationCompositionReviewTest#unauthenticatedHitStillRunsBuilderAndDownstreamMutationsWithoutExchange`
records miss `D U C; D U C X`, then hit `D U C`, with one total X.
Auth presence does not determine whether the finalized request is probed.

Additional measured paths, using strengthened existing fixtures:

- `BoundedLocalResponseCacheContractTest#refreshUsesThePreparedAuthAndResiliencePipelineWithoutDelayingTheStaleCaller`:
  fake ticker, synthetic exchange. Initial miss and stale access each record
  `D U A C; D U C X`; the later refreshed hit is `D U A C`. Two source executions,
  two auth calls before that last hit and two CircuitBreaker applications/subscriptions.
  The stale caller's pre-lookup auth is consumed once by the refresh load. Further
  refresh retries resolve current auth; this is not an unauthenticated hidden path.
- `CacheWorkCompositionContractTest#bodyPreservingRedirectUsesOneSlotAndTwoWireBodies`:
  real loopback 307 and 308 cases. Before releasing the gated redirected response,
  the leader has two D/two upstream-filter entries, one A, two actual requests,
  and identical POST bodies. Native redirect does not re-enter WebClient defaults
  or filters. With one coalesced waiter, a different-key load rejection and a
  final hit, totals are five D/five upstream filters/four A but still two wire
  requests, one successful load, and four caller terminals. The waiter is joined
  before response release; no fixed network delay proves coalescing.

Redirects are Reactor Netty work below WebClient's finalized-identity filter, not
another application filter pass. The existing redirected cache test deliberately
records the original WebClient target in the caller's successful diagnostics.
Do not reinterpret that as every redirect hop being a separately keyed or
separately reported logical call. Factory transport retry is explicitly disabled;
configured outer retry, redirects and auth invalidation remain distinct mechanisms.

## Caller and Load Lifetimes

| Lifetime | Deadline / reporting / capacity | Evidence |
|---|---|---|
| Ordinary Mono/Flux subscription | Logical-call state and outer budget; response timeout per native request. No cache probe; Flux remains cache-ineligible | `ResilienceOperatorCompositionContractTest#retryExhaustionIsOneOuterAdmissionWithPerAttemptTimeoutsAndOneTerminalResult`: three requests/attempts, one start, two retry callbacks, one terminal and one outer guard result |
| Cache caller, including hit/waiter | One caller deadline covering preparation through delivery, optional caller reservation, one caller terminal | `CacheCallerAdmissionContractTest#oneDeadlineKeepsResponseBodyAttributionAndTerminatesWaitingCallers`: one body-phase timeout, not an inner cancellation misreported as caller outcome |
| Independent miss | Separate attempt evidence followed by caller; source remains caller-owned. No shared interested-member lifetime | Existing manager path and ordinary-load tests; no claim that an unbounded independent subscription is cancelled merely by manager close |
| Shared foreground source | Own attempt state, one load reservation through retries/replays/backoff, one terminal load metric; request timeout affects the shared source | `CacheWorkCompositionContractTest#independentCallerDeadlinesNeverStealTheSurvivingSourcesCapacity` covers leader-first and waiter-first timeout with controlled scheduler |
| Detached original caller | Freeze its final attempt evidence, remove following reference, suppress subsequent load lifecycle attempt callbacks to that caller; never promote waiter into transport ownership | `BoundedLocalResponseCacheContractTest#retryAfterFirstCallerTimeoutKeepsTheWaiterTransportStateIsolated`: source retries after leader timeout, waiter succeeds with zero transport evidence |
| Hidden refresh | Triggering prepared context, separate hidden state/refresh reservation; minimum of refresh timeout and remaining hard TTL; no second caller observer/log/hook terminal | `CacheWorkCompositionContractTest#refreshUsesRealAuthAndOperatorsButItsOwnCapacityAndDeadline`; `BoundedLocalResponseCacheContractTest#cacheOutcomeIsAlignedAcrossCallerTerminalSurfacesAndHiddenRefreshStaysDetached` |

Resilience assembly applies Retry, RateLimiter, CircuitBreaker, Bulkhead; actual
subscription admission is Bulkhead -> CircuitBreaker -> RateLimiter -> Retry ->
request source. Guards wrap the whole retry operation, not each retry separately.
`CacheWorkCompositionContractTest#localRejectionsNeverEnterRealOperatorsAndHitsDoNotNeedLoadCapacity`
records both orders; local limits do not implicitly select any operator.

`RetryRedirectAuthReplayCompositionContractTest#retryAndOAuthRefreshSeparateHiddenReplayFromResilienceAttempts`
and `RetryRedirectAuthReplayCompositionContractTest#redirectAndOAuthRefreshReplayTheOriginalRequestWithOneVisibleAttempt`
exercise the other replay combinations on loopback. Semantic-read cache selection
does not authorize unsafe retry or make a stream repeatable. Existing idempotency,
body-repeatability, cross-authority sensitive-header removal and retry-method rules
remain in force; no replay-policy change is proposed here.

### Cancellation and Terminal Observations

[CacheWorkAdmission][admission] separates terminal intent from frame count.
`preparing` brackets a synchronous callback; `subscribePreparation` brackets
subscription attachment; `preparingFilter` captures a guarded `next.exchange`
closure for later asynchronous continuations. A terminated continuation cannot
advance into that closure, while an already-entered starter-owned frame keeps
capacity until it unwinds. This does not forcibly stop arbitrary application
code before it calls next, and guards are conditional on selected work limits.

Fresh regression includes these gated observations, not only eventual futures:

- `CacheCallerAdmissionContractTest#cancellationHoldsAdmissionThroughLookupSubscription`
  and `CacheCallerAdmissionContractTest#timeoutHoldsAdmissionThroughLookupSubscription`:
  lookup, flight reservation and flight start gates retain capacity until release.
- `CacheCallerAdmissionContractTest#asynchronousFilterContinuationRespectsCancellation`
  and `CacheCallerAdmissionContractTest#asynchronousFilterContinuationRespectsTimeout`:
  auth/filter continuation versus entered exchange/subscription frames have
  different permitted release times; resumed terminated next never advances.
- `CacheCallerAdmissionContractTest#asynchronousLastFilterCannotProbeAfterCallerTermination`:
  after releasing the blocked continuation, probe identity remains unset, dispatch
  stays zero, replacement capacity stays owned, and each terminal collection has
  one record before the replacement is allowed to complete.
- `CacheCallerAdmissionContractTest#reportingSetupFailureReleasesAdmissionBeforeSourceSubscription`:
  invalid raw context during reporting setup cannot strand admission. This is
  cleanup evidence, not certification of arbitrary malformed context payloads.
- `SubscriptionReportingStateTest#exactlyOneCompetingTerminalSignalWinsWithOneImmutableSnapshot`:
  one immutable terminal snapshot wins competing signals. New trace tests keep
  all observer/log/hook events rather than overwriting the most recent callback.
- `CacheWorkCompositionContractTest#preDispatchAuthFailureAfterClassifiedRetryClearsPriorTerminalEvidence`
  and `CacheWorkCompositionContractTest#authReplayAndOuterRetryRevalidateIdentityAndNeverReuseAuthVisibleByteMutations`:
  prior URL/status/headers do not survive a later pre-dispatch auth failure;
  auth-visible bytes stay isolated, and identity drift suppresses publication.

`SubscriptionReportingState` resets attempt evidence without discarding prepared
arguments, so generated/context idempotency survives backoff timeout. A detached
caller's snapshot is not updated by a subsequent source retry. The separate load
terminal counters describe source work even when no caller owns final transport
evidence; do not manufacture a waiter dispatch or extra caller terminal to fill it.

### Observability Is Not a Transport Counter

`notifyObserver` routes cache-served callers to `recordCacheServed`. The interface
default still calls custom `record`; Micrometer overrides this path without
incrementing the ordinary request timer. Cache configuration selection, not meter
availability, enables terminal cache outcomes. Hidden refresh suppresses ordinary
observer/lifecycle/exchange logging while retaining cache terminal work metrics.

`LocalResponseCacheObservabilityTest#cacheObservabilityWithoutMeterRegistryStillRecordsCallerOutcomes`
and the new actual-factory trace both preserve non-Micrometer surfaces.
`Boot4HttpClientHealthIndicatorTest#cacheServedCallersAndAdmissionOutcomesDoNotDiluteDispatchedHealthSamples`
checks downstream health does not improve simply because local cached reads
increase. The ordinary logical-call timer can still describe resilience rejection
before dispatch; neither attempt count nor cache `.loads` is a network packet count.

## Change Dependency Review

These are concrete contract dependencies, not approved feature requests or an
argument based on file length. Existing regressions demonstrate why the owners
cannot be merged just because they cooperate.

| Supported change / correction under review | Coordinated owners and required observation | Smallest alternative versus extraction |
|---|---|---|
| A SAFE customizer rewrites request URI or adds a selected header | Builder/defaults, probe terminal identity, key projection, final successful-attempt comparison; trace must fill and then hit the same rewritten target | Keep the same WebClient mutations and existing final-identity helper. A second independently built request pipeline would duplicate auth/order behavior |
| A signing provider inspects or changes prepared body bytes/media type | Selected serialization/key, auth-visible copy, request writer and replay validator; exercise first auth, 401 refresh and outer retry | Use the current prepared-body/validator boundary. Extracting only serialization might improve a future accepted correction's focused tests, but must not mix body absence, unselected ordinary bodies or auth and writer arrays |
| A deadline expires during synchronous lookup or asynchronous next.exchange | Caller deadline state, subscription/continuation guard, manager flight membership; test both before-next and already-entered cleanup | Existing frame-aware admission helpers provide independent tests. Replacing them with doFinally or one shared caller/load timeout loses the measured ordering |
| A terminal field or cache-local outcome is added | Attempt state and immutable terminal snapshot, caller detach, lifecycle/logger/observer projections, Micrometer exclusion/health and optional backends | Retain shared terminal snapshot, separate projections and distinct caller/load states. A reporting-only extraction is justified only by a selected change with parity tests, not by merging hidden-source terminal events into caller callbacks |
| A response becomes uncacheable or its identity changes after replay | Final metadata, candidate eligibility and generation-checked storage, normal caller delivery | Keep response-delivery and publication decisions separate. A new public caching hook is not needed to preserve the existing bypass behavior |

The fresh trace uses the real factory without private-field reflection; admission,
state and storage helpers already allow deterministic isolated tests. This is
concrete independent testability, not proof that every future extension is easy.
F002's fresh-metadata invocation failure remains an actual upstream dependency,
not grounds for rebuilding the invocation pipeline. No public helper promotion,
second pipeline, universal context snapshot or class split is selected.

## Disposition

No new confirmed finding from Priority 5. Preserve the measured ordering and
independent lifetimes; [F001-F003](FINDINGS.md) remain unresolved. Q4 from the
architecture map is narrowed by the subscription/continuation and publication
observations above, not closed as a universal no-post-terminal-work proof.

Priority 6 owns deeper lock/callback teardown, partial construction and resource
retention review. Arbitrary application continuations, custom callback failures,
reordered/replaced filter chains, connectors, concurrent policy mutation and
unsupported publisher protocols are not exhaustively characterized here.
No forced-GC reachability assertion, production heap/RSS, mesh, HTTP/2, native,
new assembled consumer, API comparison or performance claim is inferred.

## Verification and Provenance

See [Priority 5](CHECKLIST.md) for exact commands and fresh XML totals. The new
factory trace has seven cases; existing refresh and two redirect cases have new
counter/order assertions. A documentation guard checks the named source/test
references and links to the map/finding register. Source reading and earlier
architecture conclusions are distinguished from these freshly executed cases.

Preserve `target/release-evidence/v32/priority5/` before root clean. It contains
per-stage commands/timestamps/exit status, source and working-tree copies,
fresh Surefire XML/logs, source baseline archive and a hashed audit inventory.
Initial fixture compilation/setup failures and the missing-document red test are
retained; they are not production findings. This is current-reactor JVM evidence
with repository Central-only settings and locally available dependencies, not
freshly downloaded published-release verification.

[handler]: ../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/core/ReactiveClientInvocationHandler.java
[factory]: ../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/core/ReactiveHttpClientFactoryBean.java
[auth]: ../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/auth/OutboundAuthFilter.java
[manager]: ../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/core/LocalResponseCacheManager.java
[admission]: ../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/core/CacheWorkAdmission.java
