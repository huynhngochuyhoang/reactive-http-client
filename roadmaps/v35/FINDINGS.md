# V35 Complete Deferred-Finding Ledger

> **Recorded:** 2026-10-04
> **Implementation authorization:** seven-row bounded plan approved in Priority 3.3; no production change delivered
> **Release scope:** unselected

This is the seven-workstream inventory required by [Priority 1](CHECKLIST.md),
using the [reachable baseline](BASELINE-SCOPE.md). Owners below are maintainer
roles, not assumptions about an assigned person. Priority 1 initialized all seven
as unresolved. [Priority 2](ALLOCATION-INVESTIGATION.md) now explains the measured
allocation split without production changes. Priority 4 rejects C001's bounded
candidate with evidence-backed no change. Priority 5 likewise investigates body
and reporting-state ownership and rolls back the null-holder candidate. Priority 6
audits static planning/dynamic projection and rolls back interface-annotation reuse.
Priority 7 inventories optional acquisition and independently rejects preparation
and disabled-decision reuse. Only C005 remains open. Priority 3.3 approves the concrete bounded plan below, not a
delivered fix. V34's limited approval did not carry into V35.

Approved production boundaries and no-change routes are specified in the dated
decision below; no V35 production patch is delivered. The allocation, C001, C002, C003 and both C004 rows
have dated no-change evidence; later execution must attach
evidence and a disposition to the remaining rows, preserving
contrary results. Fixed/verified and substantiated no-change outcomes require the
checklist's acceptance rules; unresolved rows block complete-scope closure unless
the maintainer explicitly reduces scope. No new API, dependency, default, feature
switch, telemetry export or broad framework rewrite is selected.

## Priority 3 Specifications

[FIX-DECISION.md](FIX-DECISION.md), recorded 2026-10-07, specifies all seven
boundaries, no-change alternatives, dependencies, regression witnesses and
per-candidate cost/rollback gates. The maintainer approved its seven-row plan in
Priority 3.3; it is not a production patch or acceptance of any implementation.
The proposed local experiments retain full dynamic discovery, body/state ownership,
public metadata, whole-interface mutation checks and optional-resource ownership.
C005 takes an evidence-first route, not an unspecified framework rewrite. Each
execution priority must attach its own evidence before changing the statuses below.

## V34-P3/P10 allocation finding

**Owner:** performance maintainer; execution Priority 2, final acceptance Priority 10.
**Status:** Resolved without production change, 2026-10-07; observed enabled-only
allocation mechanism explained, not a final performance pass.
**Profiles:** V34-P04 enabled-only GET publisher, with P01/P02/P03 sentinels.

**Current evidence:** [V34 P3](../v34/COST-OWNERSHIP.md#confirmed-and-unresolved-flags)
and [P10 reverse confirmation](../v34/COMPATIBILITY-PERFORMANCE.md#unresolved-enabled-only-allocation)
retain the 1,392/1,136 B/op current-fork split. Those records remain unchanged.
[V35's 46-fork investigation](ALLOCATION-INVESTIGATION.md) reproduces both levels
on each same saved JAR, records constructor inlining blocked by an unloaded
CachePolicyConfig signature type in high forks, and observes Selection elimination
in low forks. A no-inline intervention reproduces the high level on both artifacts;
forcing resolver inlining alone does not guarantee constructor elimination.

**Reproduction:** the frozen experiment ran repeated forks of
the **same saved JAR** and matched pairs in both orders for
`V34DefaultPathBenchmark.defaultV34NoNetworkWarmPublisher`,
`profile=RESILIENCE_ENABLED_ONLY`, `scenario=GET`. Use the tracked
[harness/reviewer](../v34/WORKLOAD-CONTRACT.md#reproduction), preserving all forks,
VM/classpath/environment identity and failures. No rerun-until-green policy.

**Proposed boundary:** attribution only, now evidenced; no production workaround,
eager class loading, JVM default or threshold relaxation is selected.
**Controls:** `V34WorkloadContractTest`, `DefaultPathCostOwnershipTest` and
`ExplicitResilienceActivationContractTest` separate assembly, subscription,
dispatch and enabled-only state. Keep profiler experiments separate from scores.
**Acceptance:** met for the observed enabled-only allocation mechanism through
compiler/class-load evidence and one-factor intervention on both artifacts.
Minimal/registry controls remain stable in this run. Retain >20% latency and
>max(32 B/op, 5%) triggers: reverse comparison still has one explained allocation
flag, not a benefit or full-matrix pass. No measurement correction was adopted.
**Open questions:** exact scheduling of signature resolution versus compilation
is not controlled; other JVMs and earlier related profile shifts are not diagnosed
universally. Any optimization still needs stable matched benefit and separate
approval. These limits do not turn the observed mechanism back into a source-delta claim.

## V34-C001

**Owner:** observer/hook maintainer; execution Priority 4.
**Status:** Resolved without production change, 2026-10-08; bounded candidate
rejected and rolled back, not a performance pass.
**Profiles:** V34-P01/P02/P03/P04/P06; P08 terminal-surface sentinels.

**Current evidence:** [handler](../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/core/ReactiveClientInvocationHandler.java)
`getObserver()`/`getLifecycleHooks()` discover per invocation. V34 witnesses
observe two provider streams, empty-observer fallback and support checks;
whole-call scores do not isolate removable provider cost.
[Priority 4](DISCOVERY-COMPOSITION.md) adds local filter/lambda versus provider
allocation-site evidence, 21 regression cases and 224 matched scored forks in
both orders. Sequential scalar/iterator accumulation preserves the tested
semantics but fails acceptance: reverse HOOK/GET has an allocation review flag,
multiple-hook means increase in both orders, and compiler-sensitive modes cannot
be claimed as C001 savings. The rejected patch and raw results are preserved.
**Reproduction:** use `DefaultPathCostOwnershipTest` cases
`latePrototypeConsumersAreDiscoveredPerInvocationNotPerSubscription` and
`concurrentFirstInvocationsStillMaterializeIndependentPrototypeConsumers`;
separate empty, single and ordered multiple providers over assembly/subscription.
**Proposed boundary:** only attributed intermediate discovery/composition work;
not a permanent empty/single consumer cache or changed discovery timing.
**Controls:** late registration, prototype materialization, ordered consumers,
hook support, master-off/custom observer behavior, repeated/concurrent cold
subscriptions and terminal-once contracts. Keep `ReactiveHttpClientLifecycleHookTest`
and `SubscriptionLocalReportingStateTest` in affected verification.
**Acceptance:** preserve requested callbacks and per-invocation capture, with
matched benefit from a bounded approved patch or a substantiated intentional-cost
conclusion. The no-change route is met by required discovery/fallback/support
contracts, sampled local/provider attribution and the rejected iterator/copy
tradeoff. No provider-result cache or public-copy removal is authorized.
**Open questions:** exact bytes attributable to each intermediate/composite and
candidate compiler modes are not established; no universal performance claim is
made. Further experiments require new bounded scope, not automatic refinement.

## V34-C002

**Owner:** invocation/body maintainer; execution Priority 5.
**Status:** Resolved without production change, 2026-10-09; null-body candidate
rolled back after matched review, not a performance pass.
**Profiles:** V34-P01/P02 stateless owners; P03/P04/P06 stateful callers; P08 replays.

**Current evidence:** handler `RequestBodyOwnership` exists per invocation even
without a body; `usesSubscriptionState()` selects state per subscribed logical
call. [V34 body/context evidence](../v34/BODY-CONTEXT-OWNERSHIP.md) verifies cleanup,
not that required holders are redundant or retained after termination.
[Priority 5](BODY-REPORTING-OWNERSHIP.md) now samples actual null-body holder
allocation separately from subscription reporting state, inventories every body
shape and state consumer, and verifies cleanup/terminal boundaries. The three-line
candidate passes 431 ownership cases and repeats a 40 B/op minimal warm-publisher
reduction, but 384 scored forks retain adverse flags in both orders. No production
change remains; no state reuse or non-null ownership shortcut is adopted.
**Reproduction:** `DefaultPathCostOwnershipTest` state/owner counters and
`StreamingUploadOwnershipTest` for complete, error, pre-dispatch rejection,
timeout, cancellation and discard. Separate no-body, immutable body and
resource-owning body; compare same-phase workloads rather than subtracting unlike
profile scores. Exercise repeated/concurrent subscriptions to one cold publisher.
**Proposed boundary:** only reproduced unnecessary holder/wrapper work; no pooled
mutable request, body, auth, context or reporting state.
**Controls:** `SubscriptionLocalReportingStateTest`, `RequestContextSnapshotTest`,
`CacheCallerAdmissionContractTest`, streaming upload/response and retry/auth
composition controls. Cancellation must await cleanup ownership; ordinary tests
remain independent of GC. Use controlled reachability lanes for collection claims.
**Acceptance:** exact transfer/release/discard and terminal ordering remain intact;
measured benefit or an approved correctness-cost tradeoff, otherwise evidence for
a no-change result. The no-change route is met through allocation-site evidence,
the body/state lifetime inventory, terminal/cleanup regressions and the rejected
local alternative; the cost gate did not pass. No retained-heap/RSS claim inferred
from B/op. Required reporting state, one-shot cleanup and publisher/resource inner
ownership are preserved rather than treated as avoidable because exports are off.
**Open questions:** candidate compiler modes and noisy loopback timing are not
causally isolated; no universal performance claim is made. Null holders are not
declared indispensable, but any new optimization needs separate bounded scope.

## V34-C003

**Owner:** planning/resolver maintainer; execution Priority 6.
**Status:** Resolved without production change, 2026-10-10; annotation-reuse
candidate rolled back, not a performance pass.
**Profiles:** V34-P01/P02/P03/P04/P06; P08 cache/auth identity controls.

**Current evidence:** cached concrete plans remain stable, while
[request resolution](../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/core/RequestArgumentResolver.java),
[URI building](../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/core/DeclarativeRequestUri.java)
and interface log lookup repeat appropriate dynamic or potentially static work.
V34 helper measurements do not imply the difference between TARGET and GET is removable.
[Priority 6](STATIC-PLANNING-PROJECTION.md) now inventories invariant versus dynamic
owners, preserves public metadata and aliasing/mutation contracts, and executes
280 scored forks in both orders. The candidate preserves tested behavior but
minimal GET/TARGET publisher allocation grows by 24 B/op in every fork/order.
Forward review retains two latency flags and one allocation flag; reverse has
none. No repeatable attributed production-path benefit justifies the extra state.
**Reproduction:** `V33PlanningCostBenchmark` and `StarterInvocationInternalsBenchmark`
under the frozen matched harness; `DefaultPathCostOwnershipTest` for cached-plan
identity. Separate cold parsing, warm plan lookup and dynamic header/query projection.
**Proposed boundary:** cache only proven immutable derivations at the correct
concrete client/method lifetime; never cache resolved arguments or requests.
**Controls:** `MethodMetadataValidationTest`, `PublicStaticMetadataContractTest`,
`DeclarativeRequestUriTest`, `DeclarativeRequestTargetWireContractTest`,
`V33CrossPathContractTest` and selected-cache
identity tests. Preserve fresh public metadata extensions, concrete generics,
API-ref precedence, wire order, null/empty, URI escaping and mutation checks.
**Acceptance:** equivalent public/static metadata and effective wire identity,
with bounded lifetime and matched benefit or justified no-change evidence.
The no-change route is met by the owner inventory, sampled interface-array work,
precedence/fallback and wire regressions, and rejected bounded bookkeeping tradeoff.
No collection copy, metadata-extension lookup or dynamic projection is removed.
**Open questions:** exact compiler causes of allocation modes and noisy latency
are not isolated; sampled baseline arrays do not establish annotation allocation.
No universal performance or heap/RSS benefit is claimed. Further optimization
requires new bounded scope, not automatic refinement of this rejected candidate.

## V34-C004 optional preparation

**Owner:** handler/factory maintainer; execution Priority 7.1-7.2.
**Status:** Resolved without production change, 2026-10-10; preparation candidate
rolled back, not a performance pass.
**Profiles:** V34-P01/P02/P04/P05/P07, with P08 selected-cache/work sentinels.

**Current evidence:** the structural witness sees no factory-selected cache manager
without a policy, but does see a separately built cache-identity WebClient. Legacy
public construction can retain a lazy manager. A dormant scheduler handle is not
evidence of a task/thread leak. [V34 inactive-resource review](../v34/INACTIVE-LIFECYCLE.md)
distinguishes starter-owned and application-owned resources.
[Priority 7](OPTIONAL-PREPARATION.md) now separates view construction, shared
scheduler access, lazy manager state and actual cache/meter/refresh acquisition.
The independent preparation artifact passes focused ownership controls and the
physically optional-absent consumer, but has three reverse allocation flags and
no repeatable attributed cold/first-use benefit in the 408-fork comparison.
**Reproduction:** `DefaultPathCostOwnershipTest.inactivePolicyChecksScanTheWholeInterfaceButNotEverySubscription`,
`ResourceOwnershipReviewTest` and public/factory/auto-configured path controls;
observe creation, lazy use, failed construction, close and same-tag recreation.
**Proposed boundary:** avoid only proven unneeded optional preparation at its
actual ownership boundary; no removal of common transport/codec/filter work.
**Controls:** physical-absence consumer, independent pool gauges, selected auth/
cache/work lifecycle, optional class linkage, meter leases and application-owned
builders/executors. No registry lookup merely to populate diagnostics.
**Acceptance:** same effective behavior and cleanup with lazy/non-acquisition
witnesses, selected-feature parity and bounded cost/retention benefit or a
substantiated no-change disposition. A disabled flag alone is insufficient.
The no-change route is supported by the owner inventory, selected probe/lifecycle
regressions and rejected view/scheduler experiment. Legacy late selection requires
its retained manager/view; no removal or ownership transfer was approved.
**Open questions:** isolated retained bytes and scheduler-internal thread allocation
are not measured; no heap/RSS or universal zero-cost claim is made. View/scheduler
elision remains technically possible but requires new bounded benefit evidence.

## V34-C004 rolled-back value reuse

**Owner:** effective-policy maintainer; execution Priority 7.3.
**Status:** Resolved without production change, 2026-10-10; independently approved
V35 experiment rolled back, not a performance pass.
**Profiles:** V34-P01/P02/P04/P05; P08 selection-mutation sentinels.

**Current evidence:** [V34 experiment](../v34/HARDENING-EVIDENCE.md) failed its
repeatable-benefit gate; no production change remains. Every invocation still
runs [CacheWorkPolicy](../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/core/CacheWorkPolicy.java)
whole-interface mutation validation, including excluded sibling methods.
[Priority 7](OPTIONAL-PREPARATION.md) executes newly frozen independent reuse
against the unchanged baseline in both orders. No review flags occur, but low-mode
baseline and candidate publisher bytes match; high-mode-only apparent reductions
do not meet the benefit gate. Selected/invalid decisions remain independent and
all mutation checks are retained. The new evidence, not V34's rollback alone,
supports this no-change disposition.
**Reproduction:** `DefaultPathCostOwnershipTest.inactivePolicyChecksScanTheWholeInterfaceButNotEverySubscription`
counts two decisions per invocation of its two-method interface, not per repeat
subscription. `CacheWorkPolicyEnforcementTest` changes sibling selection and
bounds. Reproduce the original narrow allocation hypothesis only after Priority 2.
**Proposed boundary:** immutable disabled decision values only if newly supported
by attribution; broader optional resources remain the separate row above.
Restoring the old patch is **not a new fix** or new maintainer approval.
**Controls:** whole-interface mutation checks on every invocation, frozen cache/work
bounds, cold publishers created before mutation, late first-use and concurrent
invocations, no selected infrastructure when policy is absent.
**Acceptance:** explicit Priority 3.3 approval and new stable matched benefit,
with full mutation rejection, or a reasoned no-change conclusion backed by new
investigation. V34's failed attempt alone resolves neither C004 row.
The route is now met by P2 attribution, independent P7 fork evidence, distinct
source/selection controls and the preserved whole-interface validation contract.
**Open questions:** P2 establishes that disabled Selection materialization is
compiler-dependent; a fresh candidate compiler intervention was not selected.
No mode subtraction, threshold waiver or universal causality is inferred. Another
optimization requires new bounded approval, not automatic refinement.

## V34-C005

**Owner:** factory/AOT maintainer; execution Priority 8.
**Status:** unresolved/blocking; no reproduced warm tracking leak, cold cost unattributed.
**Profiles:** V34-P02/P03/P05/P06, public P01 construction and selected P08 controls.

**Current evidence:** [PropertiesBindingLifecycle](../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/config/PropertiesBindingLifecycle.java)
stops/clears normal runtime tracking. V34 context/proxy construction scores cover
much more than warm invocation; [V33 selection behavior](../v33/AOT-PROPERTIES-SELECTION.md)
is a published contract, not permission for a generic resolver rewrite.
**Reproduction:** `V34ConstructionBenchmark` separates context/proxy construction,
first publisher and first subscription. `PropertiesBindingLifecycleTest` covers
normal versus AOT lifecycle; `ResourceOwnershipReviewTest` covers failure/close
with real owners. Review startup before attributing allocation to normal runtime.
**Proposed boundary:** only a demonstrated cold/runtime lifecycle defect or
unnecessary local bookkeeping; preserve supported Spring selection/initialization.
**Controls:** `AotPropertiesSelectionContractTest`, `EffectiveSelectionAotReviewTest`,
`V33CrossPathContractTest`, genuine Boot consumers, native fixture and existing
controlled reachability lanes. Keep owner cleanup and application resources intact.
**Acceptance:** runtime/AOT properties and metadata selection remain equivalent,
no duplicate binding or early materialization, and measured same-phase benefit or
substantiated intentional-cost conclusion. GC-disabled unit tests are not a heap proof.
**Open questions:** which cold sites are attributable to starter work rather than
framework/context fixture work; whether any surviving observation or resource
has an invalid owner/lifetime; whether a narrower change is worth its complexity.

## Evidence Use

Fresh P1 tests and exactly reused suites are distinguished in
[BASELINE-SCOPE.md](BASELINE-SCOPE.md). The test names here identify controls to
retain in each later workstream, not a claim that every named suite was freshly
run in P1. Reproduction commands for ordinary controls and historical artifacts
are linked there. New profiling/scoring needs the Priority 2 frozen experiment;
new production edits need Priority 3.3. The allocation-mechanism, C001, C002,
C003 and both C004 findings have their P2/P4/P5/P6/P7 no-change dispositions;
C005 remains unresolved. The inventory itself closes no finding.
