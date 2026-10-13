# Reactive HTTP Client - Roadmap V36

> **Status:** active
> **Theme:** remaining allocation attribution and measurement reliability
> **Published baseline:** `4.4.2`
> **Development coordinate:** `4.5.0-SNAPSHOT`
> **Proposed scope:** V35 AUTO_REGISTRY and HOOK allocation flags; evidence reliability needed to classify them
> **Experiment authorization:** V36-P3-1 approved; at most 48 worker forks, execution not started
> **Implementation and release scope:** unselected
> **Draft date:** 2026-10-11
> **Adopted:** 2026-10-12

Follow-up to [V35's reduced-scope no-release closure](../v35/RELEASE-DECISION.md).
The [execution checklist](CHECKLIST.md) adopts baseline review, deterministic
tooling tests and experiment planning. Creating it completes no execution item
and authorizes no diagnostic fork, benchmark rerun, production edit, version
change or release. Experiment approval is required in **3.3**, implementation
selection in **6.2**, and scope/release approval in **Priority 10**. Readiness is
`activeRoadmap=v36`, `plannedFinalVersion=null`; V1-V35 stay closed.

## Objective

Give each of the two remaining allocation flags a defensible, independently
reviewable disposition. Establish whether it reflects compiler-dependent work,
a workload/evidence-processing defect, a production difference, or an unresolved
measurement limitation before considering any repair. No regression, leak,
speedup or production fix is presumed.

V35 investigated all seven original workstreams and retained no production
optimization. Repeating those investigations or reinstating rejected patches is
not the default next step. V36 targets the two expressly excluded final-combination
flags and the minimum evidence work needed to understand them.

## Starting Evidence

Inspected source: `9253d4d6ca62f405bb807a61f848180b3a973d95`, containing V35's
closure. Re-establish reachable source, clean/dirty state and available artifacts
on execution; this planning record claims no fresh artifact, Central or benchmark verification.

- [V35 final acceptance](../v35/FINAL-ACCEPTANCE.md) preserves 60 primary rows
  per artifact, zero latency flags and three allocation flags. Four reverse
  confirmation rows were clear; they did not erase the primary observations.
- Ordinary high/low traces attributed AUTO_NO_REGISTRY and regenerated the
  historical enabled-only mechanism. AUTO_REGISTRY and HOOK ordinary follow-up
  traces showed only the low mode. Forced resolver non-inlining reproduced an
  approximately 256 B/op increment, but did not establish the missing ordinary
  high-mode cause for these profiles.
- The original 302 forks plus 36 approved follow-up forks total **338 forks**.
  That authorization is exhausted. A new bounded experiment requires a new
  decision; neither checklist adoption nor the V35 closure extends the old budget.
- Identical executable bytes constrain explanations but do not reconstruct the
  compilation history of an untraced fork. Unchanged code, a successful command,
  clear reverse scores or a forced high mode is not a performance all-clear.
- P2-P8 historical raw evidence was lost during root cleanup. Fresh P9/P10/P11/P12
  bundles remain separate evidence, not recovered rejected-candidate samples.
  Ignored bundles may be absent in a clean clone; manifests alone cannot replace
  missing recordings. Keep [V35's availability limits](../v35/MAINTAINER-GUIDANCE.md#reproduction-and-evidence-availability).

## Required Findings

| ID | Owner | Missing evidence and scope |
|---|---|---|
| V36-A001 / AUTO_REGISTRY | Performance maintainer | Ordinary high-mode compiler/class-load evidence for the warm GET publisher workload, compared with its low mode on the matched artifacts |
| V36-A002 / HOOK | Performance maintainer with observer/hook review | The same per-profile causal evidence while retaining actual lifecycle-hook discovery and composition semantics |
| V36-E001 / evidence reliability | Benchmark tooling maintainer | Verified mapping from each measured fork to its artifact, profile, process and relevant compiler history; reliable preservation and rejection of incomplete input. This is a review requirement, not a confirmed tooling defect |

AUTO_NO_REGISTRY, RESILIENCE_ENABLED_ONLY and MINIMAL are possible controls, not
automatically reopened findings or an authorization to run all profiles. C001-C005
and both C004 subrows keep their V35 no-change dispositions. A newly reproduced
defect outside these boundaries needs an explicit scope decision.

## Boundaries

- No new feature, public API/SPI, module, dependency upgrade, telemetry export,
  performance switch, JVM default or scheduler/pool tuning is selected.
- No blind repetition until a high mode appears, favorable-fork selection, changed
  threshold after seeing results, or replacement of scored results with diagnostics.
- Do not add eager class loading, cached provider results, shared mutable reporting
  state or disabled-policy reuse simply to suppress a compiler mode. Required
  discovery, mutation validation, authorization and resource cleanup remain intact.
- No global handler/AOT rewrite. V32/V33 builder, metadata, properties-selection
  and lifecycle contracts remain safeguards, not new optimization targets.
- Keep warm publisher assembly separate from subscription, transport, construction
  and first-use work. Allocation B/op is not retained heap, direct/native memory,
  process/container RSS or a service-mesh diagnosis.
- Keep evidence synthetic and sanitized. No production bodies, header values,
  identities, cache keys, request targets or arbitrary error text in exported bundles.
- Preserve V1-V35 records, original scores, failed attempts and V35's two unchecked
  acceptance markers. A V36 conclusion belongs here; it cannot rewrite past results.

## 1. Post-V35 Baseline and Narrow Scope Integrity

- Verify published/API/consumer/benchmark baseline `4.4.2`, development
  `4.5.0-SNAPSHOT`, V35's explicit exclusions and unchanged production surface.
- On adoption, create the execution checklist and update index/archive/readiness
  together. An adopted roadmap follows checklist lifecycle through any final-version
  cut; a draft is not active. Adoption is not experimental or release approval.
- Inventory available P9-P12 bundles, recorded artifact/JDK/harness/dependency
  hashes and reachable source. Verify exact reuse rather than relabeling historical
  runs. Regeneration gets new provenance and never invents the original samples.
- Initialize the three rows above with owners, evidence, open questions, stop rules
  and acceptance status. Keep A001 and A002 separate even if one later resolves.

Deliver a baseline and evidence-availability record before new measurement.

[Priority 1 baseline and finding ledger](BASELINE-SCOPE.md) records the fresh
source/evidence audit and verification; it authorizes no experiment or correction.

## 2. Audit Workloads, Fork Identity and Compiler Evidence

- Inspect the existing [workload contract](../v34/WORKLOAD-CONTRACT.md),
  [benchmark](../../reactive-http-client-benchmarks/src/main/java/io/github/huynhngochuyhoang/httpstarter/benchmarks/V34DefaultPathBenchmark.java),
  [runner](../../scripts/investigate-v35-final-profiles.py) and
  [reviewer](../../scripts/review-v35-allocation.py). Reuse their structure where
  sound; do not create a second benchmark framework.
- Verify effective AUTO_REGISTRY/HOOK beans, selected features, method/scenario,
  invocation phase, publisher use and untimed witnesses. An unused registry or
  uninvoked hook cannot stand in for the named workload.
- Audit fork-to-trace mapping, including the existing timestamp ordering. Require
  unambiguous artifact/profile/process association; detect duplicate, missing,
  truncated, reordered or mismatched records. Do not assume sorting proves identity.
- Identify relevant compilation tasks, installed code, recompilation and
  deoptimization relative to the measured interval. An inlining decision in an
  unrelated or superseded task is not proof of the code used during measurement.
- Assess observability of the needed timing and code-version facts. Record what
  logs cannot establish; do not reconstruct unavailable events from aggregate B/op.
- If a parser/harness defect is reproduced, preserve its failing fixture and
  affected historical conclusions. Correct tooling in a separately recorded
  boundary and require new affected evidence before using its conclusions.

Deliver an audit and deterministic synthetic parser fixtures. No new performance
claim or production defect is inferred merely from an audit concern.

[Priority 2 audit](EVIDENCE-AUDIT.md) verifies the workload and records bounded
reader corrections. Historical per-fork association and measurement-time code
identity remain unverified; no new experiment or allocation acceptance follows.

## 3. Freeze and Approve a New Bounded Investigation

- Before launching measurement, specify competing hypotheses and a distinguishing
  observation for each: ordinary compiler modes, instrumentation perturbation,
  phase/workload mismatch, or an artifact/dependency difference.
- Freeze exact profiles/controls, artifacts, JDK/JMH/Boot/dependency stack, VM
  flags, heap, threads, warmup/measurement lengths, order and measurement units.
  Begin from the recorded Java 21.0.8/Boot 4.0.0 context where available; a changed
  JVM or stack is a separate experiment, not the historical comparison.
- State a numerical fork ceiling, per-stage timeout, total resource/time bound,
  host preconditions and abort policy. Include all scored controls, tracing and
  interventions in the budget; no hidden pilot runs or automatic retries.
- Separate the lowest-perturbation ordinary observation lane from traced and
  single-factor diagnostic lanes. Freeze how perturbation and missing modes will
  be assessed without substituting one lane's results for another's.
- Obtain explicit maintainer approval of the plan and stop rules. V35's 36-fork
  approval does not carry over. Stop when the schedule is exhausted even if the
  desired mode never appears; any extension needs a new recorded decision.

Deliver the frozen plan and approval before running a benchmark or diagnostic JVM.

[V36-P3-1 investigation plan](INVESTIGATION-PLAN.md) fixes 48 worker forks across
ordinary, marker-only, traced and single-factor lanes, with bounded resources and
no automatic retries. The maintainer approved it in 3.3 on 2026-10-13 for later
Priorities 4/5 only; no experiment has started or finding been accepted.

## 4. Capture Ordinary Allocation Modes Per Profile

- Execute only the approved schedule, retaining every fork/iteration, artifact
  order, command, exit and host/JVM observation. Do not run competing scored work.
- Couple each permitted trace to the actual measured fork. Keep original outputs
  and reproducible derived summaries; diagnose failed association rather than
  assigning a convenient trace to an observed high score.
- Examine AUTO_REGISTRY and HOOK independently on both matched artifacts and in
  approved same-JAR controls. Include high/low uncertainty and absent modes in
  the report, not only overall means or best forks.
- If instrumentation prevents observing a mode, classify that as an observation
  limit. If no ordinary high trace appears within the budget, retain the missing
  evidence; do not manufacture an ordinary observation using an intervention.

Deliver per-profile ordinary-mode evidence, or an explicit incomplete result.

## 5. Test Causal Explanations Without Replacing Scores

- Use only approved one-factor interventions to challenge the leading explanation.
  Forced non-inlining, disabled escape analysis or controlled class loading may
  probe a cause; none is a production recommendation or an ordinary-mode witness.
- Link the allocation delta to relevant code paths and compiler evidence, while
  preserving workload semantics. Sampling is attribution support, not exact
  per-operation object accounting or a release-quality score.
- Retain a per-profile evidence chain from inputs and measured modes to compiler
  behavior and intervention. Do not generalize AUTO_NO_REGISTRY's explanation
  to A001/A002 without their own evidence.
- Classify each row as explained without production change, reproduced defect
  requiring a decision, or unresolved/blocking. Evidence-processing corrections
  must identify which conclusions change and which raw observations remain valid.

Deliver causal conclusions with contrary evidence and limits. Non-reproduction
alone does not resolve either original flag.

## 6. Select a Bounded Correction or No-Change Outcome

- Compare the smallest justified tooling correction, a bounded production fix
  and no production change. Required behavior and compiler variability alone do
  not create an obligation to optimize.
- Obtain explicit implementation approval before any production edit, with
  affected profiles, ownership controls, acceptance and rollback fixed in advance.
  A tooling-only decision does not authorize a runtime patch.
- Retain dynamic observer/hook discovery, late registration, provider/prototype
  behavior, ordering/support checks, public metadata and full mutation validation.
  Preserve body/context/terminal ownership and selected cache/auth/resilience paths.
- Require repeatable attributed benefit under equivalent behavior, or an approved
  correctness cost supported by a reproduced defect. Roll back a failed candidate
  without erasing its results or reopening unrelated rejected V35 patches.

Deliver the explicit correction/no-change decision and any independently verified patch.

## 7. Verify Matched Cost and Independent Acceptance

- Preserve the existing >20% latency and >max(32 B/op, 5%) allocation review
  triggers. They request review, not an automatic pass/fail diagnosis or permission
  to relax the missing ordinary-mode gate.
- Distinguish instrumentation-only/tooling changes from production changes. With
  unchanged runtime/harness inputs, exact prior evidence reuse may be appropriate;
  it must retain the original flags and source coordinates.
- For a retained production change or corrected scored harness, freeze the affected
  comparison matrix before scoring. Use the existing 60-row matrix when the shared
  default path is affected, with matched Central-only baseline and reverse-order
  confirmation of every flag. Do not remove inconvenient rows or clear old flags
  from a new aggregate alone.
- Accept A001 and A002 independently and review their final combination. Keep
  an unexplained required result blocking full-scope acceptance. An approved
  exclusion is not an explanation or a universal speedup claim.

Deliver matched acceptance or explicitly named unaccepted scope; no public number
is required. Diagnostic results cannot become a promoted benchmark report.

## 8. Compatibility, Cross-Path and Lifecycle Verification

- Scale verification to the accepted delta. Tooling/docs-only work needs parser,
  workload, archive/readiness and guidance guards; it does not imply a fresh native
  run. Reuse prior runtime evidence only after exact input/applicability checks.
- A runtime or fixture change requires affected public/factory/mock/assembled
  contracts, real Boot 4.0.0/4.1.0 rows and physically absent optional integrations.
  Keep runtime/AOT metadata and properties selection, cancellation/discard,
  creation failure and application-versus-starter resource ownership intact.
- For retained runtime changes, require strict root and independent starter
  source/binary API comparisons against isolated Central `4.4.2`, packaging and
  applicable JVM/AOT/native validation. Native evidence must use clean reachable
  source containing the final implementation/fixture, not a pre-fix binary.
- Ordinary tests must not require forced GC. Collection claims use controlled
  reachability lanes; deterministic race tests use gates and cleanup acknowledgements.

Deliver actual counts, compatibility/ownership outcomes and precise reuse limits.

## 9. Evidence Preservation and Maintainer Guidance

- Preserve immutable bundles outside Maven output before any cleanup. Keep
  fresh stage directories, reachable source or base-plus-patch, commands/exits,
  toolchains/dependencies, reports/raw samples and file/archive hashes. Verify
  copied and archived contents; do not claim ignored bundles exist in a clone.
- Test missing/corrupt/incomplete evidence rejection. Keep failed and interrupted
  attempts without promoting them, and preserve original V35 artifacts unchanged.
- Document intentional costs, the two profile dispositions, supported alternatives
  and reopening triggers. Separate diagnostic findings from public performance
  claims, allocation from retention/RSS, and starter behavior from application work.
- Verify examples, local links, version boundaries, metadata and generated readiness.
  No snapshot-upgrade remedy, new telemetry or automatic pod/mesh diagnosis is implied.

Deliver a reproducible, bounded investigation record and current maintainer guidance.

## 10. Conditional Release and Closure

- Reconcile A001, A002 and E001. Full-scope completion requires their evidence-backed
  dispositions. If any remains unresolved, obtain explicit reduced-scope approval
  or leave the roadmap open; review-only/no release does not silently clear it.
- Obtain the maintainer's release/no-release decision. Evaluate a compatible patch
  first only if a justified change remains; `4.5.0-SNAPSHOT` selects no minor.
  A tooling/documentation-only result may close without a release.
- For release, select the exact candidate and update coordinates, fixtures,
  guards/current docs/readiness together. Verify final-input packaging, signatures,
  tag/workflow, publication and isolated Central consumption separately before
  changing published baselines. No passphrases belong in evidence.
- For no release, record delivered/rolled-back/unresolved scope and mark release-only
  gates N/A, not passed. Seal final decisions and verification, then close roadmap,
  checklist, index and readiness together. Leave future scope unselected.

## Acceptance Criteria

- [ ] AUTO_REGISTRY and HOOK each have an evidence-backed disposition; no silent
      generalization, favorable-rerun replacement or claim that non-reproduction is resolution.
- [ ] Fork/artifact/profile/trace association and compiler-evidence limits are verified.
- [ ] New measurement has prior explicit approval, a frozen budget and preserved
      stop conditions; diagnostic results never substitute for scored acceptance.
- [ ] Any retained patch is bounded, approved and verified for semantics, ownership,
      compatibility and matched benefit or accepted correctness cost.
- [ ] Evidence is reproducible and preserved with actual results and reuse limits;
      V1-V35 history, failed candidates and unavailable recordings remain honest.
- [ ] Guidance and release/no-release claims match delivered behavior. Unresolved
      scope requires explicit exclusion, not a blanket performance all-clear.

These are acceptance criteria, not completed work. Checklist adoption, experiment
approval, implementation selection and release decision remain separate steps.
