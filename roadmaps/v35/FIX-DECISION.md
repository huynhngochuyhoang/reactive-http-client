# V35 Bounded Fix Specifications

> **Recorded:** 2026-10-07
> **Status:** Priority 3 approved; execution pending
> **Implementation authorization:** seven-row bounded plan approved in Priority 3.3; no production change delivered
> **Release scope:** unselected

This specifies [Priority 3](CHECKLIST.md), not its later implementations. Reviewed
clean source: `0e99253bc59de00fadb0e9c3c41ca28cb2e61ca3`. The local delta contains
documentation and its guard only: **no production edits**, benchmark changes,
new performance scores, version transition or release selection.

The [seven-row ledger](FINDINGS.md) remains complete. The [P2 investigation](ALLOCATION-INVESTIGATION.md)
explains one allocation mechanism, not a performance pass. Six implementation
workstreams remain unresolved until their execution priorities supply accepted
patches or substantiated no-change conclusions. Specification and approval alone
close none of those findings. No scope reduction is proposed.

## Maintainer Decision

On 2026-10-07 the maintainer answered the concrete seven-row scope question:

> Approve the seven-row plan (Recommended)

The question explicitly limited authorization to these bounded experiments and
evidence-backed no-change routes, keeping all findings in scope and excluding
release approval. This approves one seven-row plan, not a promise that every row
needs a patch:

| Workstream | Proposed work | Implementation gate |
|---|---|---|
| Allocation finding | Retain P2's no-production-change explanation and both compiler modes | No runtime flags, eager class loading or threshold correction |
| C001 | Local empty/single observer and hook accumulation, preserving discovery | Attribute intermediate work first; no provider-result cache |
| C002 | Avoid an unused ownership guard for a null body only | Prove the null path cannot reach resource-owner operations; keep reporting state |
| C003 | Reuse immutable interface logging-annotation lookup on the fixed concrete-proxy path | Preserve the uncached fallback for legacy/direct/multiple-interface cases |
| C004 preparation | Avoid an unneeded identity WebClient clone and unselected default scheduler lookup | Establish effective non-selection and retain validation/acquisition timing; legacy manager assessed separately |
| C004 value reuse | Re-evaluate two immutable disabled values using P2's new compiler evidence | A new isolated experiment, not automatic restoration of V34's patch |
| C005 | Attribute construction/AOT/runtime work and prove an intentional-cost conclusion where supported | No framework-selection rewrite authorized; a new concrete defect requires separate approval |

**Maintainer decision: approved.** Priority 3.3 is complete; implementation and
acceptance in Priorities 4-8 remain pending. Publication/release approval remains
independent in Priority 12. This dated decision supersedes the pending-approval
state in the historical P1/P2 records, not their evidence or outcomes.

## Shared Acceptance and Stop Rules

Use the existing [equivalent workload contract](../v34/WORKLOAD-CONTRACT.md),
[effective profiles](BASELINE-SCOPE.md#effective-profiles) and P2's attribution
limits. Source observations below establish repeated work, **not isolated saved
bytes**. A plan whose proposed saving is eliminated already, immaterial, unsafe or
outweighed by bookkeeping must not be expanded to rescue its score.

- Before each candidate, preserve an untouched pre-change artifact and freeze
  its exact row/parameter selection plus any justified additions. Use the same
  harness/non-starter dependencies on both sides; retain Central `4.4.2` for final
  acceptance. Earlier V35 patches are not attributed to the next candidate.
- Use Java 21.0.8/JMH 1.37, five one-second warmups and measurements, two forks,
  one thread, avgt/ns and GC allocation profiling, fixed 512 MiB/two active CPUs,
  as frozen in V34. Run both artifact orders, preserving all forks/intervals.
  Apply **>20% latency** and **>max(32 B/op, 5%)** investigation triggers unchanged.
  These are not an allowed regression budget or a claimed minimum speedup.
- Benefit must repeat in the named production-path phase/rows, with attribution
  distinct from fork noise and no unexplained adverse control. Helpers identify
  sites but cannot replace proxy measurements. Retain enabled-only GET in every
  warm comparison; use P2 same saved JAR controls if compiler-mode mixing could
  explain a difference. Compiler directives and JFR remain diagnostic-only.
- Freeze each candidate pair and reverse confirmation before scoring. Allow
  **one candidate and at most one bounded refinement** per workstream, with a new
  recorded input identity for a refinement. Stop after those planned comparisons
  if benefit remains uncertain; retain/roll back the experiment and request new
  scope rather than running until favorable. Failed benefit alone does not close
  an uninvestigated part of a finding. An evidence-backed no-change result must
  explain the necessary work, ownership and rejected local alternative.
- Keep independent patches even where C001/C002/C003/C004 share the handler.
  Revert only the failing candidate, preserve useful tests, and reverify the
  remaining combination. Final Priority 10 still requires **all 60 matched primary
  rows**, reverse confirmation and applicable added rows; no subset all-clear.
- No pooling or global cache of request/body/auth/context/consumer state; no
  second metadata model, public API/SPI, dependency/default change, scheduler/pool
  tuning, feature switch or telemetry change. Broader need stops for new approval.
- Ordinary tests disable explicit GC and use deterministic cleanup/discard gates.
  Structural ownership is not a collection proof. Use the existing controlled
  reachability lanes (or a separately specified equivalent controlled fixture)
  before making collection claims. Lower B/op does not establish lower RSS.

## V34-P3/P10 allocation finding

**Need and limits:** P2 reproduced 1,136/1,392 B/op on both byte-matched artifacts.
Constructor inlining can fail on an unloaded `CachePolicyConfig` signature;
Selection materialization varies while the complete mutation scan remains.
The reverse pair retains one allocation review flag. Other JVM/profile effects
are not universally explained and no repeatable optimization follows from this.

**Owner and boundary:** performance maintainer; retain the P2 no-change
disposition and evidence in Priority 10. No production owner is selected here.

**No-change alternative:** selected for the explained mechanism. Do not tune
the runtime to force a preferred compiler mode, subtract 256 bytes, reset a
threshold or treat byte-identical artifacts as proof that every cost is fixed.

**Dependencies and lifetime:** P2 uses the unchanged eight-endpoint mutation
validator, harness and saved artifacts. It diagnoses allocation, not retained
objects, tasks or transport; C004's independent approval/acceptance still applies.

**Untimed witnesses:** `V34WorkloadContractTest`, `DefaultPathCostOwnershipTest`
and `ExplicitResilienceActivationContractTest`; frozen runner/reviewer unit tests
retain complete samples, both orders, class provenance and profiler separation.

**Cost selection and acceptance:** reuse the sealed 46 forks as attribution,
not a fresh P3 measurement. Keep enabled-only GET and minimal/registry controls
in later matched runs. Do not mistake changed compiler-mode proportions for gain.

**Rollback/stop:** an unexplained new mode or changed inputs needs attribution;
no automatic P2 waiver. Do not erase the explained historical flag or old results.

## V34-C001

**Need and limits:** the [handler](../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/core/ReactiveClientInvocationHandler.java)
queries two ordered provider streams per invocation. `getObserver()` collects a
list before selecting empty/single/composite; `getLifecycleHooks()` filters into
a list. Discovery and support checks are intentional; removable intermediate
allocation is not yet isolated. A stream implementation may already avoid work.

**Owner and boundary:** observer/hook maintainer, Priority 4. Candidate limited
to private `getObserver()`/`getLifecycleHooks()` accumulation: consume the same
ordered stream fully once, retain a scalar until a second applicable value needs
a list, return the existing empty/single/composite forms. Leave the public
`CompositeHttpClientObserver` defensive-copy and failure-isolation contract intact.
No provider-call substitution, retained provider results or negative lookup cache.

**No-change alternative:** preserve the current stream pipeline if source-level
accumulator changes add complexity or do not yield repeatable production-path
benefit. Attribute Spring/provider overhead separately; necessary discovery is
not an optimization failure that authorizes changing extension semantics.

**Dependencies and lifetime:** retain `orderedStream()` and the existing null/
empty observer `getIfAvailable()` fallback, full materialization before fallback,
ordering, duplicate/null/error behavior and per-hook `supports(clientName)` timing.
Do not introduce stream closing or lazy short-circuit behavior absent today.
Consumers remain captured per invocation, not per resubscription or globally.

**Untimed witnesses:** extend `DefaultPathCostOwnershipTest` with empty/single/
multiple ordered and custom providers, fallback call sequence, late prototype
registration, stream/support failures and full traversal. Keep
`ReactiveHttpClientLifecycleHookTest`, `CompositeHttpClientObserverTest` and
`SubscriptionLocalReportingStateTest`; master-off/no-registry callbacks, repeated
subscriptions and concurrent first calls must remain independent and terminal-once.

**Cost selection and acceptance:** `defaultV34NoNetworkWarmPublisher` and
`defaultV34NoNetworkWarmSubscription` for all six profiles x GET/TARGET; the four
`StarterDiagnosticsOverheadBenchmark` methods matching
`diagnosticsNoNetwork(OneObserver|MultipleObservers|OneLifecycleHook|MultipleLifecycleHooks)GetNoBody`
are the additional single/multiple controls. Freeze the discovered names before running.
Expected benefit is smaller intermediate discovery/composition allocation, not
fewer callbacks/provider lookups; require repeatable warm assembly benefit.

**Rollback/stop:** changed discovery/failure timing, callback order, consumer
retention beyond its publisher or lack of attributed benefit rejects the candidate.
Do not keep an unbounded helper/cache to salvage zero/single-case scores.

## V34-C002

**Need and limits:** `invokeResolved()` constructs `RequestBodyOwnership` and
its `AtomicBoolean` even for a null body; `requiresCleanup()` is then false.
This source observation does not prove those objects escape in every profile.
Resource cleanup is intentional. `usesSubscriptionState()` also deliberately
includes enabled-only resilience; removing that state is not selected.

**Owner and boundary:** invocation/body maintainer, Priority 5. The candidate is
**null-body only**: omit that unused holder and handle its absence in the private
termination helpers. Prove all stream/buffer ownership operations remain reached
only with a real owner. No arbitrary DTO/immutable-body classification, pooled
guard, one-shot replay change or reporting-state shortcut. Non-null bodies retain
their present owner unless a separately evidenced plan receives new approval.

**No-change alternative:** keep the holder if eliminating it requires ownership
rewrites or brings no repeatable gain. Still enumerate null, scalar, DTO, publisher,
buffer, stream, reader, channel, resource and multipart lifetimes in Priority 5.1;
the narrower patch does not silently remove those audit obligations.

**Dependencies and lifetime:** preserve invocation-wide ownership of one-shot
inputs and per-subscription reporting/deadline/auth state. Multipart and publisher
inner resources retain their own cleanup. No changes to body presence, encoding,
content type, auth-visible bytes, retry/redirect/401 replay or cache preparation.

**Untimed witnesses:** `StreamingUploadOwnershipTest`, `DefaultPathCostOwnershipTest`,
`SubscriptionLocalReportingStateTest`, `RequestContextSnapshotTest` and
`CacheCallerAdmissionContractTest`; add an explicit no-owner/null-body witness
without timing/GC assertions. Cover complete/empty/error, serialization/decode
failure, admission rejection, timeout, cancellation and late/buffered discard.
Count release/transfer exactly once with cleanup acknowledgements, including
concurrent subscriptions and retained cold publishers; do not infer collection.

**Cost selection and acceptance:** all six-profile GET/TARGET warm publisher and
subscription rows; loopback GET/TARGET/ENTITY/EMPTY/ERROR4/ERROR5 are null-body
rows, STRING/JSON are non-null controls across the existing three profiles.
Expected benefit is only a removed unused holder; allocation traces must identify
it and production-path benefit must survive both orders without terminal changes.

**Rollback/stop:** any changed cleanup/discard, body lifetime, repeatability,
terminal evidence or state ownership, or an eliminated-already/no-benefit result:
reject the patch. Do not broaden to state reuse or arbitrary non-owning bodies.

## V34-C003

**Need and limits:** `resolveInterfaceLevelLogAnnotation()` repeatedly inspects
proxy interfaces/annotations although a normal concrete proxy's interface is
fixed. Dynamic argument/header/query projection is intentional. Cached plans
already work; neither TARGET-minus-GET nor helper totals isolate removable work.

**Owner and boundary:** planning/resolver maintainer, Priority 6. Candidate is
lazy handler-local reuse of only the concrete interface annotation/known absence,
on a verified single-concrete-interface proxy path. Preserve original traversal
for direct calls, null proxy/interface, different or multiple proxy interfaces,
and the declaring-interface fallback. No request-plan or public metadata change.
Do not move logger bean discovery, construction or failures to handler startup.

**No-change alternative:** retain reflection where the proxy identity is not
fixed or bookkeeping costs more than traversal. Resolve remaining static URI/
resolver hypotheses through source/extension witnesses and measured helpers;
do not remove collection copies without aliasing/mutation evidence and new scope.

**Dependencies and lifetime:** method metadata overrides still win; client
logging config remains live. Fresh/replacement metadata, generic inheritance,
API-ref precedence, method identity and logger-cache bounds stay intact. Retained
data may reference only the handler's already-owned interface/annotation, never
arguments, request/response bodies, identities, Reactor context or logger instances
outside the existing owner. No static class-loader cache or second metadata model.

**Untimed witnesses:** `PublicStaticMetadataContractTest`, `MethodMetadataValidationTest`,
`V33CrossPathContractTest`, `DeclarativeRequestUriTest` and
`DeclarativeRequestTargetWireContractTest`; add interface/method/client logger
precedence, inherited/legacy/multiple-interface fallback and late logger bean
witnesses. Assert exact wire order, case aliases, null/empty, escaping and bodies
for public/factory/mock paths. Only invariant annotation traversal may decrease.

**Cost selection and acceptance:** GET/TARGET warm publisher/subscription across
all profiles plus `StarterDiagnosticsOverheadBenchmark.metadataOnlyExchangeLoggingGetNoBody`;
retain both `V33PlanningCostBenchmark.metadataCold*` and all four
`StarterInvocationInternalsBenchmark` helpers as attribution/control rows.
Expected benefit is less repeated annotation derivation; cold/first-use cost and
retained metadata must be accounted for, not hidden by a warm lookup score.

**Rollback/stop:** changed precedence, extension/mutation behavior, eager logger
creation, growing per-method/proxy cache, request retention or no stable benefit:
reject. A broader URI/projection optimization needs a separately approved plan.

## V34-C004 optional preparation

**Need and limits:** every handler builds a cache-identity WebClient, including
factory paths with no selected policy. The default
[manager](../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/core/LocalResponseCacheManager.java)
overload calls `Schedulers.parallel()` before the selection-return-null check.
Legacy constructors hold a lazy manager shell. These are preparation/handle
observations, not evidence of a scheduled task, cache entry, connection or leak.

**Owner and boundary:** handler/factory maintainer, Priority 7.1-7.2. Candidate
has separately reversible construction steps: omit the identity view only where
the frozen effective selection and complete mutation guard prove it unreachable;
resolve the default shared scheduler only after actual cache selection. Retain
selected-path view/filter construction and exception timing. Do not duplicate
policy semantics or introduce a second per-call selection scan to save a holder.

**No-change alternative:** retain a view wherever legacy/dynamic metadata cannot
prove non-selection, and retain the legacy lazy manager until its supported late
use/ownership is characterized. Removal/reownership of that manager is not approved
by this plan. Priority 7 must record a reasoned outcome for view, scheduler and
manager separately; two improvements cannot silently close the third boundary.

**Dependencies and lifetime:** preserve whole-interface mutation rejection before
ordinary and cache calls, selected cache auth/probe filters and their order,
tenant/body/key isolation, startup safety validation and optional class loading.
Keep shared schedulers/application builders/connectors externally owned; never
dispose them. Cache callers/loads/refreshes, byte bounds and meter leases remain
manager-owned as before. No lazy recreation after close or diagnostic acquisition.

**Untimed witnesses:** `DefaultPathCostOwnershipTest`, `ResourceOwnershipReviewTest`,
`CacheWorkPolicyEnforcementTest` and selected cache/auth/work suites. Distinguish
factory/static-create from legacy constructors; assert allocations/acquisitions,
not flags. Gate concurrent first-use/cancel/close, failed creation and same-tag
recreation; retain no-registry terminal consumers and independent pool gauges.
Rerun the physically optional-absent consumer after affected production changes.

**Cost selection and acceptance:** all six `V34ConstructionBenchmark` rows,
warm GET/TARGET and selected-cache/work controls. Add an isolated public-constructor
correctness/attribution fixture before any claim about that path. Expected benefit
is less unselected cold preparation; total construction plus first use must not
simply move cost. Invocation-level setup/teardown remain included in lifecycle B/op.

**Rollback/stop:** lost selected probes, changed validation/discovery timing,
optional linkage, unexpected acquisition/disposal, post-close work or unmeasured
cost relocation rejects the affected step. A new manager/scheduler owner needs
fresh approval, not an implicit expansion of an allocation patch.

## V34-C004 rolled-back value reuse

**Need and limits:** P2 now directly observes disabled Selection elimination in
low forks and materialization in high forks. This is new attribution, not stable
benefit for reuse. V34's candidate remains rolled back and its failure unchanged.

**Owner and boundary:** effective-policy maintainer, Priority 7.3. Approval covers
one renewed isolated candidate in
[EffectiveCachePolicy](../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/core/EffectiveCachePolicy.java):
two fixed disabled Selections and corresponding Decisions for `DISABLED` and
`METHOD_DISABLED` only. No selected/invalid interning or changes to
[CacheWorkPolicy](../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/core/CacheWorkPolicy.java)
scan/comparison semantics. Restoring the old patch is **not a new fix**; acceptance
requires fresh evidence beyond P2's causal explanation.

**No-change alternative:** retain allocation if reuse cannot improve ordinary
production-path rows reliably across compiler modes. Document why elimination
and fixed-value bookkeeping make the candidate unnecessary or not worth keeping;
neither the old rollback nor a new unfavorable score alone closes all questions.

**Dependencies and lifetime:** preserve every whole-interface mutation check per
invocation, including excluded siblings, disabled-to-selected changes and frozen
work/refresh/source/eligibility comparisons. Distinct disabled sources retain null
policy/name/reason; fixed constants retain no application objects. Selected values
remain live/unshared as required. No caching of validation success or effective config.

**Untimed witnesses:** `DeclarativeCachePolicyTest`, `CacheWorkLimitContractTest`,
`CacheWorkPolicyEnforcementTest`, plus the full-interface witness in
`DefaultPathCostOwnershipTest`. Add distinct-source/null-payload constant checks,
independent clients, concurrent first invocation, cold publishers before mutation,
late first-use and selected/invalid paths. Selection reuse must not reduce scans.

**Cost selection and acceptance:** all six-profile GET/TARGET warm publisher
rows, subscription and construction controls, plus selected cache/work sentinels.
Require repeatable ordinary-path allocation benefit in both orders, with P2-like
same-artifact controls/traces when modes differ. A gain only against a high-mode
fork, or only under diagnostic `dontinline`/no-EA, is insufficient.

**Rollback/stop:** shared mutable payload, source conflation, weaker mutation
validation, different exceptions/timing, unstable/no benefit: roll back this
candidate independently of optional preparation. No threshold reset or best-fork pick.

## V34-C005

**Need and limits:** [PropertiesBindingLifecycle](../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/config/PropertiesBindingLifecycle.java)
already disables tracking and clears observations at normal singleton completion
and destruction. `PropertiesBindingLifecycleTest` keeps 100 prototype/scoped
properties live while asserting empty normal-runtime history. No warm tracking
leak is reproduced. Whole-context allocation does not isolate starter cold cost.

**Owner and boundary:** factory/AOT maintainer, Priority 8. Approve evidence-first
classification and a substantiated no-change route, not an unspecified lifecycle
patch. Attribute cold/first-use sites and retain the current tracking boundary.
A newly reproduced unnecessary derivation or ownership defect must name a smaller
correction and obtain new approval before touching framework selection/binding.

**No-change alternative:** retain necessary construction, validation and AOT
history if attribution and ownership witnesses confirm their correct lifetime.
Priority 8 must close cold/runtime questions explicitly; this is not another
deferral based solely on the absence of a warm leak or the previous V34 decision.

**Dependencies and lifetime:** preserve V33 properties/metadata preference,
non-eager unrelated beans, parent/scoped/FactoryBean selection, identity/name
tracking, awareness/binding/init order, processor replacement and restoration.
AOT history holds weak references and lives in its factory, not a global map;
normal runtime must not retain observations after its established stop boundary.

**Untimed witnesses:** `PropertiesBindingLifecycleTest`, `AotPropertiesSelectionContractTest`,
`EffectiveSelectionAotReviewTest`, `V33CrossPathContractTest` and
`ResourceOwnershipReviewTest`. Cover failures/recreation, prototype/custom scopes,
class-loader isolation, external owners and no resurrection after close. Existing
controlled reachability evidence may be reused only with exact unchanged inputs;
source inspection and disabled-GC tests alone cannot establish collection.

**Cost selection and acceptance:** all six construction rows plus the 24 warm
rows separate cold fixture lifecycle from steady state. Attribute starter versus
Spring/fixture work independently before claiming removable cost. A no-change
conclusion needs current owner/lifetime evidence and rejected local alternatives;
no arbitrary performance target authorizes moving validation into first use.

**Rollback/stop:** duplicated binding, changed selection/init order, eager
business-bean creation, default fallback on selected failure or retained runtime
observations stops acceptance and requires a new concrete repair plan. There is
no pre-authorized framework rewrite to roll forward when an experiment fails.

## Verification and Execution

Priority 3 verification re-runs existing structural/ownership/extension controls
and a new documentation guard for all seven specifications, status, local links
and later unchecked execution priorities. It is not testing delivered optimizations.
Exact source/dirty state, commands/exits, actual XML totals, generated readiness
and final hashes belong under `target/release-evidence/v35/priority3/`.

Reproduce the focused check using the Java/Maven/repository setup in
[BASELINE-SCOPE.md](BASELINE-SCOPE.md#verification-and-limits):

```bash
mvn -B -ntp -s .mvn/maven-central-settings.xml -pl reactive-http-client-starter \
  -DargLine='-XX:+DisableExplicitGC -XX:ActiveProcessorCount=2' \
  -Dtest=DocumentationReleaseArtifactTest,DefaultPathCostOwnershipTest,ResourceOwnershipReviewTest,PropertiesBindingLifecycleTest,PublicStaticMetadataContractTest,StreamingUploadOwnershipTest,SubscriptionLocalReportingStateTest,ReactiveHttpClientLifecycleHookTest,CompositeHttpClientObserverTest,ExplicitResilienceActivationContractTest,CacheWorkPolicyEnforcementTest test
git diff --check
```

No new JMH, API comparison, native, assembled consumer or controlled-GC run is
needed to specify these boundaries; none is claimed. Affected later production
changes require fresh correctness and cost evidence and Priority 9 parity, not
automatic reuse of P3 tests or old binaries. Final counts and approval state are
recorded in the checklist; release/version state remains unselected and unchanged.

The approved-state run passed **238 cases across 11 classes**, zero failures,
errors or skips, with explicit GC disabled (96 documentation and 142 existing
semantic/ownership controls). The pre-approval run overlaps, not another distinct
total. The new guard first failed because this record was absent. The P2 inventory
rehashes unchanged. No additional scored experiment was needed or performed to
record this decision; fresh performance and candidate-specific regression evidence
remain the responsibility of the execution priorities.
