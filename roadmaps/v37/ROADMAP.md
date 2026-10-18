# Reactive HTTP Client - Roadmap V37

> **Status:** active
> **Theme:** ordinary allocation-mode attribution with a feasibility-first boundary
> **Published baseline:** `4.4.2`
> **Development coordinate:** `4.5.0-SNAPSHOT`
> **Proposed scope:** unresolved AUTO_REGISTRY/HOOK attribution and the minimum evidence needed to decide it
> **Experiment authorization:** none; no inherited budget
> **Implementation and release scope:** unselected
> **Draft date:** 2026-10-18
> **Adopted:** 2026-10-18

Follow-up to [V36's reduced-scope no-release closure](../v36/RELEASE-DECISION.md).
The [execution checklist](CHECKLIST.md) adopts this roadmap for baseline,
source/evidence review, bounded deterministic synthetic tests and planning only.
Creating the checklist completes no execution item. Adoption authorizes no
benchmark, diagnostic worker, pilot, preflight, production edit, version change
or release. Readiness reports `activeRoadmap=v37`, `plannedFinalVersion=null`,
`releaseLane=unselected`.
V1-V36 stay closed; the two unresolved flags are not retrospectively passed.

[Priority 1's baseline and ledger](BASELINE-SCOPE.md) inventory preserved evidence
and missing historical classpaths. They verify baseline integrity, not a causal
explanation or permission for new measurements; feasibility remains Priority 2.

## Objective and Difference from V36

Decide whether a materially different, bounded method can connect an ordinary
high-allocation observation to its cause for each profile. Only then consider
new capture or a correction. Repeating the same low-only traced schedule is not
the default next step, and a production defect or optimization is not presumed.

V36 improved process association but did not obtain ordinary high-mode causal
evidence. V37 therefore starts with a **feasibility decision before measurement**:
identify the missing observation, explain how a proposed method can obtain it,
and define what would disprove that method's usefulness. If no credible method
exists, stop before launching workers and request a scope decision. An explicit
no-investigation outcome is preferable to another automatic experiment cycle;
it does not resolve the findings or authorize closure by itself.

## Starting Evidence and Required Findings

Draft-stage inspected closure revision: `0574d892603b30fec903deee79211464a1fe86c8`.
Drafting claimed source review only, not fresh artifact, Central, compiler or
native verification. Execution records reconcile reachable source and preserved
base-plus-patch evidence; squash-local hashes must not be presented as reachable
execution revisions without verification.

- [V36 cost acceptance](../v36/COST-ACCEPTANCE.md) retains the V35 60 primary
  rows and four reverse-confirmation rows per artifact, including three primary
  allocation flags. Clear reverse aggregates do not replace those observations.
- [Ordinary capture](../v36/ORDINARY-CAPTURE.md) observed untraced HOOK high
  modes on both artifacts. [Intervention](../v36/INTERVENTION-RESULTS.md) forced
  high allocation in both profiles, but neither result supplies the missing
  ordinary high-mode compiler attribution. AUTO_REGISTRY remains independent.
- [E001's corrections and limits](../v36/EVIDENCE-AUDIT.md) distinguish reliable
  new process association from legacy joins, measurement envelopes from exact
  executing code, and installed compiler tasks from demonstrated execution.
- V35's 338 workers and V36's **48/48 workers** are exhausted. V36's accepted
  accounting exception includes 96 inferred internal probes in 194 accounted
  JVM invocations, not an individually observed process census. None grants V37
  workers, pilots, probes, retries or remaining wall-clock time.
- [Evidence preservation](../v36/MAINTAINER-GUIDANCE.md#evidence-inventory) and
  [closure verification](../v36/RELEASE-DECISION.md#source-and-evidence-inventory)
  identify local bundles and reuse limits. Lost V35 P2-P8 recordings remain
  unavailable; manifests and new runs cannot reconstruct them.

| Required row | Owner | Initial state and proof obligation |
|---|---|---|
| V37-A001 / AUTO_REGISTRY | Performance maintainer with metrics-integration review | Unresolved/blocking. Explain the ordinary high/low allocation split for the actual registry-selected warm GET publisher workload, with matched artifact and compiler evidence. |
| V37-A002 / HOOK | Performance maintainer with observer/hook review | Unresolved/blocking. Independently explain the split while preserving actual hook discovery/composition; untraced high mode and forced non-inlining are insufficient alone. |
| V37-E001 / attribution feasibility and evidence limits | Benchmark tooling maintainer | Review the remaining observation gap, not reopen verified V36 fixes without a reproduction. Establish a defensible capture method and complete process accounting before seeking execution approval. |

Valid per-profile outcomes are **explained without production change**, **fixed
and verified** after a reproduced defect and approved correction, or
**unresolved/blocking**. The same-JAR split constrains explanations but does not
prove an ordinary fork's cause. An AUTO_NO_REGISTRY/control explanation cannot
be transferred to either required profile. C001-C005 and both C004 subrows retain
V35's no-change outcomes; this roadmap does not reinstate rejected candidates.

## Boundaries

- No new feature, public API/SPI, dependency upgrade, telemetry, runtime performance
  switch, JVM default, pool/scheduler tuning or broad invocation/AOT rewrite.
- Preserve late/prototype discovery, ordering/supports checks, fresh public
  metadata, complete policy mutation validation and subscription-local state.
  Do not disable required registry/hook behavior to resemble a MINIMAL workload.
- Separate warm publisher assembly, subscription, transport, construction and
  first use. Allocation B/op is not retained heap, direct/native memory or RSS;
  a synthetic result is not an application leak or Kubernetes/mesh diagnosis.
- No favorable-fork selection, rerun-until-high, threshold changes after capture
  or diagnostic-score substitution. Retain original contrary results and failures.
- No new measurement before **Priority 4 approval**, no production correction
  before **Priority 7 approval**, and no release or scope exclusion before
  **Priority 10 decision**. Adoption alone grants none of these.

## 1. Post-V36 Baseline and Narrow Scope Integrity

- Verify the closed V36 decision, published/API/consumer/benchmark baseline
  `4.4.2`, development `4.5.0-SNAPSHOT`, source/tree and clean/dirty state.
  Preserve V1-V36 history and explicit unresolved/excluded findings.
- Inventory required raw samples, commands, saved artifacts, JDK/JMH/Boot inputs,
  source patches and seals. Verify availability and integrity before relying on
  reuse; record absent data rather than manufacturing historical identity.
- On adoption, create the execution checklist and update index/archive/readiness
  together. Track roadmap lifecycle independently of the version suffix; a draft
  remains inactive. Initialize all three rows with owners and stop conditions.

## 2. Attribution Feasibility and Stop Decision

- Audit existing ordinary, marker, traced and intervention evidence without new
  workers. State exactly which claim each observation supports and which bridge
  to ordinary high-mode causality remains missing, separately for A001 and A002.
- Compare candidate observation methods from existing tooling/source evidence.
  Describe why each might distinguish the modes when V36's tracing did not,
  expected perturbation, required permissions/resources and falsifying outcomes.
  Listing another profiler or requesting more forks is not a feasibility argument.
- Record the minimum sufficient evidence for the intended explanation, including
  residual uncertainty. Installed/inlined/eliminated code and iteration envelopes
  alone do not prove exact executing-code identity. Do not weaken old acceptance
  rules to relabel an unresolved flag as explained.
- Obtain an explicit proceed-to-plan or stop decision. If no materially different
  credible approach is identified, do not advance to measurement; retain both
  findings unresolved and take the proposed no-investigation route to Priority 10.
  Conditional later work is dated N/A only after that decision, not silently passed.

## 3. Minimal Capture and Process-Accounting Contract

- Reuse the [V36 capture runner](../../scripts/capture-v36-allocation.py),
  [shared reviewer](../../scripts/review-v35-allocation.py) and
  [archive verifier](../../scripts/verify-v36-evidence.py) where applicable.
  Add only the selected missing observation; no second benchmark framework.
- Bind artifact hash, profile/scenario, worker identity, commands/VM flags,
  measurement boundaries and compiler/class-load records explicitly. Preserve
  unknown code identity; timestamp sorting is not a trace-to-score join.
- Specify accounting for launchers, measured workers, internal JMH probes,
  toolchain preflights and any profiler/helper processes. Identify observation
  limits and how unexpected launches stop work; inferred counts are not a census.
- Test missing, duplicate, truncated, mismatched, reordered and superseded records
  with bounded synthetic fixtures. Keep any process/recorder smoke requiring a
  diagnostic JVM behind Priority 4's approval, not hidden in tooling validation.

## 4. Freeze and Approve One Bounded Investigation

- Freeze exact artifact/harness/JDK/JMH/Boot/dependency identities, workload phase,
  payload, VM flags, heap/threads, iterations, units, order and host conditions.
  Start with the preserved stack where available; changed stacks are separately
  labeled evidence, not replacements for the original comparison.
- Select the smallest ordinary/observed/intervention and same-JAR controls that
  can refute the hypotheses. Preserve genuine registry/hook semantics using
  untimed witnesses; instrumentation must not silently enter ordinary scores.
- Approve an exact numerical schedule: per-category and total process ceilings,
  worker count, per-stage timeout, cumulative time, memory/storage caps, and
  stop rules. Include all pilots, smoke checks, probes and controls before launch.
  No numerical budget is selected by adoption; old approvals do not carry over.
- Record approval of the frozen plan before any execution. Failures, absent modes,
  unexpected processes or exhausted bounds stop the plan; no automatic retries.
  Extensions require a new decision and cumulative accounting, never a reset.

## 5. Ordinary-Mode Capture and Perturbation Checks

- Execute only the approved schedule in fresh directories, with pre-launch input
  verification and no competing scored runs. Preserve every worker, interval,
  exit, interruption and contrary outcome for both artifacts and each profile.
- Compare ordinary and observed mode availability without combining their scores.
  A mode suppressed or changed by instrumentation is an observation limit, not
  a runtime fix. An absent high mode remains missing evidence, not resolution.
- Verify actual process accounting and per-worker association before attributing
  results. Abort on incomplete evidence or unexpected work. Report what the
  capture established without launching additional workers to improve the answer.

## 6. Causal Review and Independent Finding Dispositions

- Relate ordinary high/low observations to relevant compilation, installation,
  invalidation and class-load evidence under the approved proof obligations.
  Retain uncertainty where sampling/envelopes cannot identify executed code.
- Treat a forced non-inlining/high-mode intervention as a test of mechanism, not
  proof of the original cause. Any additional experiment needs prior numerical
  approval; this review creates no implicit follow-up lane.
- Record A001/A002 independently and combined, with contrary evidence, owner and
  reopening trigger. Distinguish reproduced tooling defects, runtime defects,
  explained variability and unresolved measurement limits. Correct tooling must
  have failing synthetic evidence; stronger conclusions require affected evidence.

## 7. Explicit Correction or No-Change Selection

- Prefer no production change when evidence supports required behavior or remains
  inconclusive. A confirmed tooling defect authorizes only its scoped correction,
  not an unrelated runtime optimization or public performance claim.
- Before a production edit, obtain explicit approval of the reproduced defect,
  smallest correction, affected contracts, benefit/correctness-cost criterion and
  rollback. Preserve dynamic discovery, mutation validation and body/context/
  terminal ownership; do not reinstate V34/V35 rejected optimizations by default.
- Verify every retained change with focused regression/negative controls. Roll
  back a failed candidate and retain its evidence. No-change selection by itself
  resolves neither remaining finding nor the later closure decision.

## 8. Matched Cost, Compatibility and Ownership Acceptance

- Freeze scoring applicability before execution. Exact unchanged-input reuse may
  be justified; changed shared default paths require the existing 60-row matrix.
  New scoring needs its own approved numerical budget, not unused diagnostic time.
- Keep Central-only `4.4.2`, matched non-starter dependencies/harness, semantic
  witnesses, both artifact orders, full samples/intervals and the original review
  triggers: >20% latency and >max(32 B/op, 5%) allocation. Confirm every flag without
  erasing primary results. No diagnostic result replaces a public score.
- For tooling/docs-only changes, run parser/workload/archive/readiness/guidance
  guards and verify exact runtime-evidence reuse. For runtime/fixture changes,
  run affected public/factory/mock/assembled contracts on genuine Boot 4.0.0/4.1.0
  rows, optional-absence and runtime/AOT selection/lifecycle paths.
- Retained runtime changes require strict root and independent starter source/
  binary API comparisons against isolated Central baselines, both attempted even
  if one fails, plus applicable packaging and clean-source native compile/run.
  Use explicit GC disabled for ordinary tests and controlled reachability only
  for collection claims. Record actual counts, failures and dated reuse/N/A limits.

## 9. Durable Evidence and Maintainer Guidance

- Seal reachable source or base-plus-patch, approvals, commands/exits, toolchains,
  dependencies, raw samples/reports, failed attempts and file/archive hashes.
  Verify copied and archived contents outside Maven output before cleanup; an
  ignored local bundle is not available in a clean clone or an off-machine backup.
- Keep V35/V36 originals unchanged and test missing/corrupt/incomplete evidence
  rejection. Regeneration receives new provenance; it does not recover lost runs.
- Reconcile findings, intentional costs, supported alternatives and reopening
  triggers with current docs/examples/metadata/readiness. No snapshot-upgrade
  remedy, new telemetry or automatic application-memory/mesh diagnosis.
- Use bounded synthetic/sanitized capture only: no production bodies, header
  values, identities, cache keys, request targets, arbitrary error text or secrets
  in shared evidence. No production investigation is authorized by this roadmap.

## 10. Scope Decision and Conditional Release or Closure

- Reconcile all three rows, including a possible feasibility stop. Full-scope
  closure requires evidence-backed dispositions; unresolved findings require
  explicit named exclusion or the roadmap stays open. No-release alone clears
  nothing. Stopping an investigation is not a performance pass.
- Obtain release/no-release intent separately. Evaluate a compatible patch first
  only if a justified retained change exists; `4.5.0-SNAPSHOT` selects no minor.
  A tooling/documentation-only or no-investigation result may close without release.
- For release, approve exact coordinates, update all current inputs together and
  verify final packaging, signing/signatures, tag/source/workflow, publication and
  isolated Central consumption separately. Keep baseline `4.4.2` and the roadmap
  active until verified closure; preparation is not final release GO.
- For no release, record delivered/rolled-back/unresolved scope and exclusions;
  mark unrun release gates N/A, not passed. Seal final decisions/verification and
  close checklist/roadmap/index/readiness together, leaving future scope unselected.

## Acceptance Criteria

- [ ] Feasibility has an explicit proceed or stop decision before new measurement.
- [ ] A001/A002 have independent evidence-backed dispositions, or remain blocking
      with any closure exclusion explicitly approved and named.
- [ ] Capture association, perturbation, process accounting and code-identity
      limits are verified without treating forced modes as ordinary causality.
- [ ] Every new experiment has prior numerical approval, preserved inputs, stop
      rules and complete results; no inherited budget or favorable-rerun selection.
- [ ] Any retained correction has approval and appropriate semantic, ownership,
      compatibility and matched-cost acceptance; failed candidates remain rejected.
- [ ] Evidence, guidance and lifecycle/release decisions agree with delivered
      behavior; V1-V36 history, missing evidence and unresolved flags remain honest.

These remain proposal criteria, not completed work. Execution status and approval
gates live in [CHECKLIST.md](CHECKLIST.md); no next release is selected.
