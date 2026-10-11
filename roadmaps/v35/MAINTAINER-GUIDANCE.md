# V35 Findings and Maintainer Guidance

> **Status:** Priority 11 complete; 10.2 cost acceptance remains pending
> **Recorded:** 2026-10-11
> **Published / development:** `4.4.2` / `4.5.0-SNAPSHOT`
> **Delivered production changes:** none
> **Scope reduction:** none proposed or approved
> **Release scope:** unselected

The maintainer requested [Priority 11](CHECKLIST.md) while **10.2 remains
pending**. This documentation-only dependency exception records the completed
investigations and their limits; it does not bypass cost acceptance. No additional
benchmark, diagnostic fork, production change or release is authorized here.
Priority 12 must still reconcile blocking scope and obtain an explicit decision.

## Final Finding Reconciliation

All seven original rows remain in the [ledger](FINDINGS.md). Their dated
no-change dispositions mean investigated intentional work or a rejected bounded
alternative, not seven delivered fixes. The additional final-combination flags
are separately blocking; neither a failed candidate alone nor repeated deferral
resolves them. Owners below are maintainer roles, not assigned individuals.

| Finding / owner | Retained contract and no-change evidence | Controls / cost limit | Supported alternative and reopening trigger |
|---|---|---|---|
| V34-P3/P10 allocation finding; performance maintainer | [P2](ALLOCATION-INVESTIGATION.md) explains the enabled-only Selection allocation split; [P10](FINAL-ACCEPTANCE.md) regenerates that evidence and attributes AUTO_NO_REGISTRY | Same saved JAR, compiler/class-load traces and resolver intervention; original scores retained, not replaced by diagnostic scores | Preserve all forks and equivalent phases. No JVM tuning, eager class loading or measurement correction is recommended. Reopen on another reproducible mode or JVM/workload change; AUTO_REGISTRY/HOOK remain open below |
| V34-C001; observer/hook maintainer | [P4](DISCOVERY-COMPOSITION.md) attributes dynamic provider/composition work; iterator/copy candidate rolled back | Late registration, prototype/order/support checks and empty-observer fallback; adverse flags and multiple-hook costs prevent a benefit claim | Keep callbacks bounded and nonblocking; use an application singleton only when its semantics allow it. Do not cache provider results. Reopen for an isolated intermediate cost with a new bounded candidate and matched benefit |
| V34-C002; invocation/body maintainer | [P5](BODY-REPORTING-OWNERSHIP.md) inventories body transfer and subscription state; null-holder candidate rolled back | StreamingUploadOwnershipTest, SubscriptionLocalReportingStateTest and cache admission; local null-body saving did not pass combined cost review | Consume/release resource-owning bodies and finish application queues. Do not pool caller state or remove cleanup when exports are off. Reopen for a reproduced ownership defect or new state-consumer proof plus matched cost |
| V34-C003; planning/resolver maintainer | [P6](STATIC-PLANNING-PROJECTION.md) separates immutable plans from fresh metadata and dynamic wire projection; annotation reuse rolled back | PublicStaticMetadataContractTest, URI/wire and cache identity controls; extra state lacked repeatable attributed benefit | Reuse supported proxies/plans at their existing lifetime, not resolved headers, arguments or requests. Reopen only for an immutable derivation with a bounded owner and equivalent wire behavior |
| V34-C004 optional preparation; handler/factory maintainer | [P7](OPTIONAL-PREPARATION.md) distinguishes dormant views/handles from cache, meter and task acquisition; preparation candidate rolled back | ResourceOwnershipReviewTest, physical optional absence, late selection and selected-cache probes; no repeatable cold/first-use benefit | Leave unwanted policies unselected and use normal factory destruction. Do not destroy application connectors or shared schedulers. Reopen on proven unnecessary acquisition/retention or new bounded preparation benefit |
| V34-C004 rolled-back value reuse; effective-policy maintainer | [P7](OPTIONAL-PREPARATION.md) independently tests disabled-value reuse without skipping mutation checks; rolled back | CacheWorkPolicyEnforcementTest and whole-interface/sibling checks; low-mode bytes match, high-mode-only reductions are insufficient | Retain every mutation check; do not restore constants because constructor counts look lower. Reopen only with new approval and benefit separated from compiler modes |
| V34-C005; factory/AOT maintainer | [P8](CONSTRUCTION-LIFECYCLE.md) attributes cold/framework work and verifies runtime tracking stop without a patch | PropertiesBindingLifecycleTest, AotPropertiesSelectionContractTest, failure/recreation controls and genuine Boot/native parity; sampling is not exact per-site cost | Retain Spring ownership and published V33 selection/binding behavior. Reopen on reproduced lifecycle/selection failure or removable same-phase work, not cost moved into subscription |

### Blocking Final Combination

**AUTO_REGISTRY and HOOK remain unresolved/blocking for 10.2.** Owner:
performance maintainer; maintainer approval is required for any further experiment
or scope reduction. Primary GET warm-publisher rows had higher allocation means.
Reverse confirmation was clear, but a favorable rerun cannot erase those flags.
The approved 36-fork follow-up is exhausted. Ordinary traces for these two
profiles all showed Selection elimination; forcing resolver non-inlining
reproduced the increment but did not meet the frozen ordinary high-mode trace
requirement. Missing evidence is the ordinary high-mode compiler/class-load path
for each profile, not another API or native pass.

AUTO_NO_REGISTRY did reproduce both ordinary modes on both artifacts and is
attributed. P10 retains all three original flags and **338 forks**; this guide
adds none. No same-byte-artifact argument, intervention or unexplained mode is a
universal performance all-clear. Until additional approved evidence or an explicit
reduced-scope decision, full-scope acceptance is pending. Guidance completion
does not select a no-release branch either.

## Retained Semantics and Migration

There is **no V35 application migration** or new API, SPI, property, default,
dependency, meter or dashboard. Published `4.4.2` remains the application
baseline; `4.5.0-SNAPSHOT` is a development coordinate, not a selected release.
[V33's published corrections](../v33/MAINTAINER-GUIDANCE.md) remain version-scoped
behavior, not new V35 fixes. No snapshot upgrade is a demonstrated latency,
startup or pod-memory remedy.

- On ordinary non-cache calls, observer/hook providers are discovered at Java
  invocation time and captured for that publisher. Cache-selected caller reporting
  resolves them per subscription; hidden refresh has no caller terminal consumers.
  Preserve these distinct boundaries, late registration,
  prototype behavior, ordering, supports checks and fallback. Master-off built-in
  exports do not imply that custom consumers or required reporting state vanish.
- Cache lookup probes and load requests can run custom filters more than once.
  Preserve per-caller authorization, explicit customization-safety classification
  and dynamic wire variants. Follow the [customizer guide](../../docs/15-customizer.md)
  and its checked [V33 example](../../docs/examples/v33-extensions.md), including
  Caffeine and policy prerequisites. A blanket SAFE declaration is not a shortcut.
- Immutable request plans do not make mutable arguments/context/request headers
  static. Keep subscription-local terminal/attempt state and frozen selected-cache
  identity. Ordinary calls and cache-selected calls retain their different
  snapshot boundaries; do not impose a new global snapshot or request cache.
- [Context contracts](../../docs/21-diagnostic-contexts.md) and explicit
  [inbound handoff](../../docs/13-exchange-logging.md) retain caller isolation.
  Application queues, envelopes and loggers own any retained copies; no global
  Reactor hook or context normalization is introduced. Case-insensitive access
  is not proof that a mesh lost or restored context.
- [Body ownership](../../docs/11-streaming.md) includes resource transfer,
  cancellation and discard. A response envelope terminal is not inner-stream
  consumption. Repeated subscriptions do not make a one-shot body replayable.
  Removing metrics does not remove body cleanup or required terminal state.
- Spring destroys factory-owned resources. Application connectors, SDKs,
  registries and executors retain their established owners. Independently loaded
  non-single-flight work can remain caller-owned after manager close; cancel or
  join it separately. Do not dispose shared scheduler infrastructure per client.
- Diagnostics preserves unknown facts without creating lazy resources. Runtime
  tracking stops at its boundary; AOT observations are factory-lifetime-bounded,
  not a fixed-cardinality guarantee for arbitrary live prototype churn. Preserve
  properties/metadata preference and binding/awareness/initialization order.

## Reproduction and Evidence Availability

The 2026-10-10 root-clean incident deleted historical ignored raw samples.
P2-P8 Markdown conclusions and rejected patches are history, **not rehashed
historical evidence**. Do not claim their old score files can be recreated under
the same hashes. P9 regenerates correctness/ownership/consumer/native evidence;
P10 regenerates final cost/API evidence. Neither reconstructs rejected-candidate
scores or retroactively proves an untraced fork's compilation history.

Use [P10's tracked runner and commands](FINAL-ACCEPTANCE.md#reproduction-and-preservation)
with fresh external output/repository paths and a frozen plan. The scored source
was `beb8a237215529c9c8c7ddc285295c9564e4bce0`; this guide starts at reachable
`85948837c4f4001f648b15adb3efd80b9de05abd`. P11 verifies production, harness,
dependency and fixture equivalence rather than relabeling old runs with the new
commit. If a clone lacks a historical object or ignored bundle, use a reachable
equivalent source only after recording the input comparison, or regenerate in a
new directory. Missing evidence is not a pass.

| Question | Workload / witness | Interpretation boundary |
|---|---|---|
| Construction / first use | V34ConstructionBenchmark context/proxy, first publisher, first call | Fixture lifecycle, not whole-application/native startup; B/op can include setup work excluded from latency |
| Warm assembly | V34DefaultPathBenchmark.defaultV34NoNetworkWarmPublisher | No subscription/transport; GET/TARGET or different profiles cannot be subtracted to price removable work |
| Warm subscribed execution | defaultV34NoNetworkWarmSubscription | Synthetic exchange, not wire encoding; selected consumers remain real |
| Wire/body work | defaultV34LoopbackCall and its named scenario | Includes server/transport; match codec, body, status and consumption |
| Static/dynamic helpers | V33PlanningCostBenchmark and StarterInvocationInternalsBenchmark | Isolated helpers are not a whole-call saving |
| Ownership | P9 ordinary controls and separate controlled reachability lanes | GC-disabled unit tests prove structural cleanup, not collection; physical optional absence differs from a disabled flag |

Preserve the 60-row matrix, all forks/intervals, Central-only baseline provenance,
matched harness/non-starter dependencies, semantic witnesses and reverse
confirmation of every flag plus historical enabled-only GET. The >20% latency
and >max(32 B/op, 5%) allocation triggers require review, not automatic acceptance.
Use fixed recorded Java/JMH/heap/CPU settings, available-memory and time bounds;
capture CPU/quota/pressure and shared-host limits. Do not overlap scoring with
Maven, native builds or profilers. Separate compiler/JFR interventions from
scores; their perturbation cannot become a recommended runtime flag.

Sealed local bundles live outside Maven output in `verification-evidence/`, with
conventional copies under `target/release-evidence/v35/`. Never reactor-root
`clean` to reproduce this review. This read-only check verifies available P9/P10
bundle contents; it performs no new measurement and does not close the cost gate:

```bash
set -euo pipefail
for bundle in verification-evidence/v35-priority9 verification-evidence/v35-priority10; do
  test -f "$bundle/SHA256SUMS"
  (cd "$bundle" && sha256sum -c SHA256SUMS)
done
```

Compare manifest hashes with the P9 and P10 anchors recorded in the
[checklist](CHECKLIST.md) before trusting an inventory. Bundles are local, not published benchmark reports
or source-controlled release artifacts. The diagnostic follow-up script documents
the already executed schedule; its existence does not authorize more forks.

## Operations and Privacy

For published incidents use [performance troubleshooting](../../docs/25-performance-troubleshooting.md#default-path-cost-and-ownership),
[memory triage](../../docs/30-operations-troubleshooting.md#cache-memory-triage-420)
and [sanitized support capture](../../docs/26-support-bundles.md#performance-investigations).
Keep one versioned, timestamped process window and separate effective features
from installed integrations. Do not disable required auth/audit/tenant gates to
match a minimal profile, or invent zeros for absent meters and unknown facts.

| Memory/cost domain | Allowed evidence and limit |
|---|---|
| Allocation / CPU | Matched synthetic phase and B/op, separate profiling; allocation is not retained heap |
| Java retention | Post-GC heap trend and owner/lifecycle checkpoints; structural counters do not force or prove collection |
| Direct/native / transport | Direct-buffer estimates, protocol-aware connections/streams, threads and available native totals; not priced by Java B/op |
| Process / container | RSS, JVM reserves, container limits and deployment times; distinguish application-container and sidecar memory |
| Shutdown | Before/after quiet-window counters while registered, then lifecycle/post-close checkpoints; removed meters cannot supply post-close deltas |

Start with synthetic reproduction. Any separately authorized production capture
needs time/size bounds and privacy review. Keep raw heap dumps, JFR and profiler
stacks private; export reviewed bounded summaries only. No request targets,
header/body values, identities, credentials, cache keys/digests, retained objects
or arbitrary exception messages belong in the bundle. Existing cache byte gauges
measure their documented decoded-response domain, not total heap/direct memory/RSS.
No new capture field or telemetry is required by this guide.

Neither the earlier pod-memory report nor the Istio/Envoy incident is diagnosed
by these microbenchmarks. Case-sensitive map access, subscription handoff and
mesh behavior require their own evidence. No automatic mesh diagnosis, leak
all-clear or snapshot-upgrade recommendation follows from V35.

## Guidance Verification

This priority changes docs and documentation guards only. P9/P10 evidence is
rehash/input-checked reuse, not fresh API, performance, native or full-reactor
execution. V1-V34 records remain unchanged. Readiness stays `activeRoadmap=v35`,
`releaseLane=unselected`, `plannedFinalVersion=null`; P12 remains unchecked.
No scope reduction is proposed, so reduced-scope approval is N/A, not granted.

From the repository root, use Java 21 and Maven 3.9.9 with a writable non-baseline
repository. The existing V33 public example is compiled by its four contract
cases; this guide introduces no new API/configuration example.

```bash
set -euo pipefail
: "${REPO:?Set REPO to a writable non-baseline Maven repository}"
export MAVEN_OPTS='-Xmx512m -XX:ActiveProcessorCount=2'
tests='DocumentationReleaseArtifactTest,ReactiveHttpClientPropertiesTest,ReactiveHttpClientConfigurationMetadataTest,V33GuidanceExampleTest,DefaultPathCostOwnershipTest,CacheWorkPolicyEnforcementTest,StreamingUploadOwnershipTest,SubscriptionLocalReportingStateTest,RequestContextSnapshotTest,ResourceOwnershipReviewTest'
for boot in 4.0.0 4.1.0; do
  mvn -B -ntp -s .mvn/maven-central-settings.xml -Dmaven.repo.local="$REPO" \
    -Dspring-boot.version="$boot" -pl reactive-http-client-starter -Dtest="$tests" \
    -DargLine='-XX:+DisableExplicitGC -XX:ActiveProcessorCount=2' test
done
python3 -B -m unittest discover -s scripts -p 'test_*v3[345]*.py' -v
git diff --check
```

### Recorded Verification

Reviewed starting commit: `85948837c4f4001f648b15adb3efd80b9de05abd`, with this
documentation/test delta, not a clean release cut. Oracle Java 21.0.8 and Maven
3.9.9 use the existing writable non-baseline repository
`/tmp/rhttp-v35-p10-20261011/repository-current`. This is not newly resolved
Central consumption. Explicit GC is disabled throughout the ordinary suites.

| Fresh verification | Cases per Boot row / result |
|---|---:|
| DocumentationReleaseArtifactTest | 104 |
| ReactiveHttpClientPropertiesTest / ReactiveHttpClientConfigurationMetadataTest | 35 / 18 |
| V33GuidanceExampleTest | 4 |
| DefaultPathCostOwnershipTest / CacheWorkPolicyEnforcementTest | 42 / 12 |
| StreamingUploadOwnershipTest / SubscriptionLocalReportingStateTest | 24 / 5 |
| RequestContextSnapshotTest / ResourceOwnershipReviewTest | 9 / 20 |
| **Focused starter total, ten classes** | **273 passed on Boot 4.0.0 and 4.1.0** |
| V33-V35 Python tooling checks | 56 passed, separate from JUnit totals |
| Two guide Bash blocks, `bash -n` | exit 0 |
| Read-only P9/P10 inventory command | exit 0; 1,811 / 931 files |
| Saved P10 primary review | 60 matched rows; three original allocation flags retained |

All accepted test runs have zero failures/errors/skips. The initial Boot 4.0.0
attempt retained three documentation failures: two broken anchors caused the
link and generated-readiness assertions to fail, and the operations guide needed
its historical V34 link retained. Those links were corrected without changing
runtime code or weakening the guards. Corrected and final runs overlap; do not
add them into doubled test totals. The final guard includes completed P11 status
and explicitly keeps 10.2 and all P12 items pending.

The audit rehashes both complete bundles and archives against the checklist's
manifest hashes. Production/module POM/settings/consumer/native fixture and V33
runner inputs match P9/P10 source. The V35 parity runner was introduced during P9,
so it matches the sealed P9 runner copy, not that run's pre-tooling source commit.
The initial audit's overly broad commit comparison failed on that added file;
its script/failure are preserved and the corrected audit checks the exact sealed
runner hash without weakening production/fixture comparisons.
All 39 recorded harness/POM hashes match the
reachable checkout. P11 re-evaluates saved raw primary JSON and preserves the
two pending profiles, not new scores or a retroactive candidate pass. Earlier
P9/P10 correctness, API and native executions are not added to fresh totals.
Generated configuration references, local links and release readiness pass.

Evidence originates at `/tmp/rhttp-v35-p11-20261011/evidence/`, is sealed under
`verification-evidence/v35-priority11/` and a dated archive outside build output,
and is copied to `target/release-evidence/v35/priority11/`. It retains commands,
exits, XML-derived counts, failed attempts, source/patch, link/syntax checks,
readiness and reuse/input audits. The checklist adds the external checksum anchor
after source sealing to avoid self-reference. No new performance, collection,
API, native, deployment or publication claim is made.
