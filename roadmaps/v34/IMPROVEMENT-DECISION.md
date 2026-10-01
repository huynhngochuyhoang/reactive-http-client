# V34 Bounded Improvement Decision

> **Status:** Priority 4 scope approved, 2026-10-01; implementation pending
> **Implementation scope:** V34-C004 only: immutable disabled cache-policy decision reuse
> **Release scope:** unselected
> **Published baseline:** `4.4.2`
> **Development coordinate:** `4.5.0-SNAPSHOT`

This is the Priority 4 decision for the [execution checklist](CHECKLIST.md).
Reviewed clean source: `34229b75f6e677f64cfea0b44afe3b2f8687c668`.
The [Priority 3 characterization](COST-OWNERSHIP.md) ranks the candidates; its
pre-approval status and scores, and the earlier baseline/workload records, remain
historical evidence. This decision supersedes their **unselected implementation**
status, not their results or limitations. No production fix is delivered here.

## Maintainer Decision

On 2026-10-01 the maintainer explicitly selected:

> C004 only, within that boundary (Recommended)

The question specified reuse of immutable disabled cache-policy decisions while
preserving every mutation check, with C001-C003, C005 and broader C004 resource
changes deferred. This approves **implementation work only**, not a release,
version change, performance claim or completion of Priorities 5-12.

Rationale: no correctness/ownership defect was reproduced. C004 has a small,
source-supported allocation opportunity that need not remove any required work.
The current validator checks all frozen endpoints per invocation. Disabled
selection objects appear in the assembly profile, but their isolated savings
are unmeasured. Provider discovery, request projection, terminal state and AOT
changes have less isolated benefit and greater contract risk. Review-only is
valid if this one optimization fails its acceptance gates.

## Approved Boundary

The only selected production owner is internal
[EffectiveCachePolicy](../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/core/EffectiveCachePolicy.java):
`Selection.disabled(Source)`, the disabled branch of `decide`, and private
constants needed by those paths. Reuse only the two existing disabled-source
values, `DISABLED` and `METHOD_DISABLED`, with their corresponding disabled
decisions. Their payload is fixed: enabled=false, eligibility=DISABLED, the
distinct source enum, and null policy name, policy object and invalid reason.
Do not intern selected/invalid decisions or attach application objects to a
constant. A bounded pair of selections/decisions may live for the defining
class loader; there must be no map keyed by client, method, request or context.

[CacheWorkPolicy](../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/core/CacheWorkPolicy.java)
remains the mutation-validation owner. **Retain the complete per-invocation
scan**, current effective configuration reads, source/eligibility/policy/refresh/
work comparisons, exceptions and validation timing. This proposal removes
repeated value construction, not the O(method-count) validation work. The current
null/missing configuration behavior and method/client policy precedence remain.

Affected profiles are P01/P02, P03/P04/P06 ordinary calls, physically absent P05,
and disabled methods in otherwise selected P08 clients. P07 independent pool
telemetry remains a control. Sharing a disabled value must not activate a cache,
operator, registry, task or transport subscription in any of them.

Explicitly outside approval:

- Skipping or narrowing the validator scan, freezing live configuration reads,
  changing startup/subscription validation or accepting post-startup mutation.
- Changing public metadata/plans, URI or header projection, observer/hook discovery,
  reporting state, body cleanup, auth, retry, redirects or deadlines.
- Removing the cache identity WebClient, moving shared scheduler initialization,
  changing legacy lazy-manager behavior, or modifying factory/lease ownership.
- New public API/SPI, switch, dependency, configuration default, scheduler/pool
  tuning, AOT selection change, synchronization, or a generalized memoization layer.

## Alternatives and Dispositions

The owner for all deferred decisions is the V34 maintainer; the named production
owner identifies who must supply evidence on reconsideration. "Workaround" does
not mean disabling a required feature or removing a safety check.

| ID / disposition | Smallest local alternative and expected benefit | Compatibility/ownership cost and no-change alternative | Owner, workaround and reopening trigger |
|---|---|---|---|
| **C004 selected, narrow boundary** | Reuse the two immutable disabled selections/decisions; potentially reduce repeated records in warm assembly. P02 GET currently totals 832 B/op, not an isolated savings estimate. | A fixed class-loader-lifetime payload replaces transient immutable values; no client/config retention. Keep every scan/check. No-change retains current allocation and behavior. | Effective-policy owner. Keep current behavior until implementation and measurement pass; revert this local change if they do not. |
| **C004 broader work deferred** | Lazily construct the cache identity view or avoid touching the shared scheduler when cache is unselected. Could reduce cold/dormant preparation, not proven warm savings. | Changes handler/factory ownership and entry-point behavior without a reproduced resource defect. Retain existing construction and rollback paths. | Handler/factory owners. Use supported factory destruction and application resource ownership. Reopen only with isolated construction cost or actual inactive-path acquisition/retention evidence and separate approval. |
| **C003 deferred** | Consider reusing only immutable interface-log annotation/static URI description derivation at the existing plan boundary; leave dynamic projection intact. TARGET assembly is 2,008 vs GET 832 B/op, but that difference is not removable static cost. | Inherited/generic/public metadata and proxy/interface identity can change which derivation is valid. No second metadata model or resolved-request cache. No-change preserves V33 planning contracts. | Planning/resolver owner. Reuse supported client proxies, not mutable resolved requests. Reopen after an isolated static-work witness and matched benefit preserving exact wire projections. |
| **C002 deferred** | Consider avoiding an unused cleanup holder only for already-recognized non-resource bodies; keep resource guards. Reporting-state removal is a separate, unselected alternative. | One-shot release, repeated subscriptions, cancellation/discard and caller isolation outweigh unmeasured wrapper savings. P04's extra state is existing behavior, not an attributed regression. | Invocation/body owner. Keep one-shot input limits and required state; do not pool callers. Reopen with an isolated unnecessary-holder cost plus resource and terminal acceptance tests. |
| **C001 retained/deferred** | Inspect empty-provider stream/composition overhead without retaining results across invocations. Discovery frequency is proven; provider-only savings are not. | Permanent empty/single-consumer caching would freeze late registrations, prototypes, ordering and support checks. Current discovery is intentional. | Observer/hook owner. Use appropriately scoped application consumers where their contract permits; keep dynamic discovery. Reopen with an equivalent dynamic-provider workload and a local change that preserves materialization timing. |
| **C005 retained/deferred** | Consider only a separately reproduced repeated cold derivation, not general Spring/AOT changes. Context/proxy cost is 6.59 ms and 3.12 MB per fixture lifecycle, not steady-state overhead. | Framework validation and ownership are necessary; normal runtime tracking already stops/clears. No-change retains verified V32/V33 lifecycle behavior. | Factory/AOT owner. Keep supported context/client lifetimes and close owned resources. Reopen for a reproduced warm-path lifecycle cost or retention defect, not cold B/op alone. |

The tested claim that unselected factory/static-create paths allocate a cache
manager or schedule refresh work is contradicted by the controls; legacy public
constructors retain a dormant manager shell and are a different boundary.
Neither fact diagnoses a production memory incident. C001 discovery and C005
framework work are intentional; C002/C003 savings and broader C004 savings are
insufficiently isolated, not disproved. There is no new confirmed correctness
defect to prioritize ahead of the selected optimization.

## Acceptance and Rollback

These are future implementation gates, not claims of tests added by Priority 4.

| Gate | Required evidence and existing controls | Failure / rollback condition |
|---|---|---|
| Immutable disabled values | Add internal tests for repeated default-disabled and method-disabled decisions, their distinct sources, null payloads and stable reuse. Exercise independent clients, repeated and concurrent calls. Only disabled values may be shared. | Any mutable config, request, method or context retained by a constant, source conflation, synchronization or growing cache: reject the implementation. |
| Selection/validation parity | Extend `DeclarativeCachePolicyTest` and `CacheWorkLimitContractTest` for inert definitions, client selection, method override/exclusion, generic metadata/API refs and invalid selected policies. Selected decisions must continue reflecting current configuration. | A different eligibility/source/invalid reason, selected policy binding or validation boundary: revert. |
| Mutation checks | Retain `DefaultPathCostOwnershipTest.inactivePolicyChecksScanTheWholeInterfaceButNotEverySubscription` and `CacheWorkPolicyEnforcementTest.mutationCannotResetCapacityOrChangeColdCallsAndLiveSnapshots`. Add a cross-method mutation witness so invoking an unchanged method still checks the mutated sibling selection. Cover an ordinary disabled client becoming selected. | Fewer methods checked, suppressed startup/invocation/subscription/snapshot failure, dispatch after rejection, or a capacity reset: revert, never weaken the test to accept a skipped guard. |
| Ownership/integration | Keep `DefaultPathCostOwnershipTest`, `ResourceOwnershipReviewTest`, `ExplicitResilienceActivationContractTest` and cache/work sentinels; preserve no-registry observers/hooks and independent pool meters. Run supported public/mock/factory/assembled paths later. | Acquired unselected resources, lost callbacks, changed attempt/state/cleanup or optional linkage: revert. |
| Cost | Primary candidate rows: `V34DefaultPathBenchmark.defaultV34NoNetworkWarmPublisher`, all six profiles x GET/TARGET (12 rows), especially P02 GET. Preserve warm subscriptions, loopback, cold/helpers and selected cache/work controls. Final Priority 10 comparison retains the complete frozen 60-row matrix. | No repeatable measured benefit above fork noise: roll back rather than widening scope. A regression or unresolved result needs investigation and disposition, not a favorable-fork selection. |

Benefit is not a promised byte count or speedup. Require a repeatable allocation
reduction in the P02 GET publisher under the same semantic workload, both initial
and reversed matched order; the reduction must be distinguishable from fork
variance. Source-level reuse alone is insufficient. Retain every sample/interval
and the Priority 2 regression triggers (>20% latency, >max(32 B/op, 5%) allocation).
Those triggers do not define an allowed regression or a minimum marketing claim.
No elapsed-time or forced-GC assertion belongs in the ordinary test suite.

The existing **enabled-only allocation flag remains unresolved**: P3 current
forks split near 1,136/1,392 B/op even though the starter classes match published
4.4.2. C004 approval neither assigns its cause nor clears it. Priority 10 must
preserve both P3 attempts, investigate the final paired results, and record its
disposition. Persistent uncertainty cannot be promoted to a blanket no-regression
or speedup claim. A repeatable tradeoff requires a new explicit decision.

## Execution and Evidence Budget

| Priority | Selected delivery / dated N/A | Retained work |
|---|---|---|
| 5 | Production planning/projection changes **N/A, 2026-10-01**: C003 deferred | Metadata, argument/wire and selection precedence controls; disposition still to be recorded at execution |
| 6 | Sole implementation owner for **C004 disabled-value reuse**; C001 discovery and C002 reporting/body changes N/A | New desired-behavior tests first, minimal internal edit, profile-paired controls and exploratory matched cost check |
| 7 | Production body/context/terminal changes **N/A, 2026-10-01**: C002 deferred | Relevant cleanup/cancellation/context isolation controls remain; untouched advanced scenarios require explicit evidence reuse/N/A, not silent waiver |
| 8 | Production resource/AOT changes **N/A, 2026-10-01**: broader C004/C005 deferred | Disabled/physical absence, validation-before-acquisition, failed construction and destruction controls |
| 9-10 | Shared parity, compatibility and final cost evidence once for the selected diff | No duplicate C004 implementation in Priorities 5/8; no reuse of an old native binary as changed-code evidence |
| 11-12 | Guidance and independent release/no-release decision | Approval is neither delivery nor release GO |

Execute Priority 5's retained controls/disposition, then implement C004 in
Priority 6, then complete retained ownership/resource controls in 7-8. This is
the checklist's order, not an approval to start Priority 6 in this task.

Budget the engineering scope to one internal owner and one local implementation
candidate, with at most one bounded refinement before rollback or renewed scope
approval. Do not add an abstraction to rescue a noise-sized result. Reuse the
existing harness and dependency stack; any exploratory helper must be labeled
separately and cannot replace or alter a frozen primary row.

Priority 4 itself only revalidates the P3 inventory, source scope and existing
contract/documentation guards. No new JMH scoring, native compilation, API run,
dependency upgrade or whole-reactor test is necessary to record a decision.
Future verification must use exact final inputs: keep baseline/current builds
separate, preserve the existing source/JAR hashes, run correctness before scoring,
do not overlap builds/profilers with measurements, and apply reverse-order
confirmation without changing thresholds. Priority 9 must assess JVM/AOT/native
applicability for the real production diff and either rerun or justify exact
unchanged-input reuse; this document pre-authorizes no native waiver.

Stop and ask for renewed scope approval if the change needs another production
owner, skips a mutation check, changes a public/default/extension contract,
touches resource ownership, needs a dependency, or fails to show benefit within
the bounded candidate/refinement budget. Rollback removes the proposed value
reuse, retains useful regressions, and records no delivered optimization.

## Review-Only Alternative

Not selected now. The maintainer may instead approve retaining the current
implementation or rolling back an unhelpful candidate. A characterization-only/
no-release closure still needs explicit dispositions for every finding, the
unresolved allocation flag and any attempted implementation; scope/ownership and
documentation guards, applicable unchanged-input evidence reuse, and the
Priority 12 no-release decision. Signing/publication are then N/A by that later
decision, not failed or unrun checks represented as passes. Positive speedup is
not a condition for an honest review-only closure.

## Priority 4 Verification

The P3 500-file inventory and immutable inputs are revalidated without editing
its bundle or historical records. The measured commit remains reachable from the
reviewed source. This is evidence reuse, not new cost collection or a C004 benefit.

Fresh verification on Oracle Java 21.0.8, Maven 3.9.9 and the unchanged Boot 4.0.0
stack passes **194 tests in six classes**, zero failures/errors/skips, with
explicit GC disabled. Exact XML-name collection reports 83 documentation/archive/
readiness, 14 default-path ownership, 24 declarative cache policy, 45 work-limit,
10 work-policy enforcement and 18 resource-ownership cases. The existing Python
input/result/review guards pass **14 tests**. Whitespace and unchanged production,
benchmark, packaging, coordinate and historical-record checks pass.

These are existing contract controls plus the new decision/status/link guard,
not tests of an implemented optimization. Exact commands, exits, source/dirty
state, reports and generated readiness are preserved under
`target/release-evidence/v34/priority4/`; the final inventory digest is in the
checklist. Generated readiness remains `activeRoadmap=v34`, release lane/scope
unselected and `plannedFinalVersion=null`. Earlier overlapping verification runs
are retained and are not added into a distinct-case total.

```bash
export MAVEN_OPTS='-Xmx512m -XX:ActiveProcessorCount=2'
mvn -B -ntp -s .mvn/maven-central-settings.xml -pl reactive-http-client-starter \
  -Dtest=DocumentationReleaseArtifactTest,DefaultPathCostOwnershipTest,DeclarativeCachePolicyTest,CacheWorkLimitContractTest,CacheWorkPolicyEnforcementTest,ResourceOwnershipReviewTest \
  -DargLine='-XX:+DisableExplicitGC -XX:ActiveProcessorCount=2' test
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s scripts -p 'test_*v34*.py' -v
git diff --check
```

The recorded run uses a separate existing repository at
`target/v33-native-runs/native-g0ynw95x/repository`, not the P3 published-baseline
repository; that Maven argument is retained in the command record. No production
source, benchmark fixture, dependency, published baseline or release coordinate
is changed by this decision. Implementation and its benefit remain pending.
