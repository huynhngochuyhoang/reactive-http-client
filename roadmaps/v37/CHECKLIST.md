# Reactive HTTP Client - Roadmap V37 Execution Checklist

> **Status:** active
> **Theme:** ordinary allocation-mode attribution with a feasibility-first boundary
> **Published baseline:** `4.4.2`
> **Development coordinate:** `4.5.0-SNAPSHOT`
> **Proposed scope:** unresolved AUTO_REGISTRY/HOOK attribution and the minimum evidence needed to decide it
> **Experiment authorization:** none; no inherited budget
> **Implementation and release scope:** unselected
> **Adopted:** 2026-10-18

Execution companion to [ROADMAP.md](ROADMAP.md). Adoption authorizes baseline,
source/evidence review, bounded deterministic synthetic tests and planning only.
Creating this checklist completes no execution item. No benchmark or diagnostic
execution, including pilots, probes, preflights and recorder smoke, is authorized
before **4.3**; no production edit before **7.2**; no release or reduced-scope
closure before **Priority 10**. V1-V36 stay closed and unchanged.

Execute priorities in order; record justified dependency-based reordering before
work. Check a box only after recording its result, source and verification.
Conditional work may be dated N/A only with an explicit applicability decision,
not because a run failed or high mode was absent. On an approved **2.3 stop**,
skip new capture and correction work, document applicability in Priority 8, retain
Priority 9's evidence/guidance obligations, and request Priority 10's decision.
A stop is not an automatic closure or a performance pass.

## Finding and Approval Gates

| Required row | Owner | Initial state / proof obligation |
|---|---|---|
| V37-A001 / AUTO_REGISTRY | Performance maintainer with metrics-integration review | Unresolved/blocking; explain ordinary high/low allocation for the actual registry-selected warm GET publisher workload |
| V37-A002 / HOOK | Performance maintainer with observer/hook review | Unresolved/blocking; independently explain ordinary modes while preserving genuine hook discovery/composition |
| V37-E001 / attribution feasibility and evidence limits | Benchmark tooling maintainer | Feasibility pending; identify the missing observation, a credible method and complete process accounting, without reopening verified V36 fixes absent a reproduction |

Valid profile outcomes are **explained without production change**, **fixed and
verified** after an approved reproduced correction, or **unresolved/blocking**.
Absent high modes, forced non-inlining and control-profile explanations alone
resolve neither A001 nor A002. Both require independent and combined acceptance;
explicit closure exclusions are not performance passes.

| Gate | Required decision |
|---|---|
| 2.3 | Proceed to planning with a materially different credible method, or stop before new measurement |
| 4.3 | Approve exact inputs, a numerical schedule, all process categories and resource/stop bounds before execution |
| 7.2 | Approve the smallest production correction or select no production change; neither closes findings by itself |
| Priority 8 | Approve any new scoring budget separately and establish applicable cost, compatibility and ownership acceptance |
| Priority 10 | Decide unresolved scope and release/no-release separately, then verify and record closure |

V35's 338 workers and V36's 48/48 workers are exhausted. The 96 inferred internal
probes in V36's 194 accounted JVM invocations are not an observed census or a
V37 allowance. No numerical budget is selected. C001-C005 and both C004 subrows
keep V35's no-change outcomes; no feature, API/SPI, dependency, telemetry, JVM
default, pool/scheduler tuning or broad invocation/AOT rewrite is selected.

## Evidence Rules

- Preserve source/tree and clean/dirty state, commands/exits, actual counts,
  toolchains/dependencies, artifact hashes, raw samples and failed attempts.
  Use a reachable revision or hashed base-plus-patch; verify squash-local claims.
- Use fresh `target/release-evidence/v37/priority<N>/` stage directories. Verify
  copied and archived contents outside Maven output before any cleanup; ignored
  local bundles are neither clean-clone artifacts nor off-machine backups.
- Lost V35 P2-P8 recordings stay unavailable. New runs have new provenance;
  exact unchanged-input reuse must name inputs, availability and narrower limits.
- Keep ordinary scores, traced attribution and interventions separate. No hidden
  pilots, automatic retries, favorable-fork selection, competing scored runs or
  threshold resets. Allocation B/op is not retained heap, direct memory or RSS.
- Count launchers, workers, probes, preflights, pilots, recorder smoke and helper
  processes. Include per-category and total process ceilings, per-stage and
  cumulative time, memory/storage caps. Extensions need prior explicit approval
  and cumulative accounting; old limits never reset through a new directory.
- Ordinary tests use explicit GC disabled; collection claims need a controlled
  reachability lane. Race tests use deterministic gates/cleanup acknowledgements.
- Export bounded synthetic/sanitized evidence only: no production bodies, header
  values, identities, cache keys, request targets, arbitrary error text or secrets.
  This is not authorization for production or Kubernetes/mesh investigation.

Create records when evidence exists, not empty placeholders. Suggested records:
`BASELINE-SCOPE.md`, `FEASIBILITY-DECISION.md`, `CAPTURE-CONTRACT.md`,
`INVESTIGATION-PLAN.md`, `ALLOCATION-EVIDENCE.md`, `FINDINGS.md`,
`CORRECTION-DECISION.md`, `VERIFICATION.md`, `MAINTAINER-GUIDANCE.md` and
`RELEASE-DECISION.md`. Reuse existing capture/review/archive tooling where sound.

## Priority 1 - Post-V36 Baseline and Narrow Scope Integrity

### [x] 1.1 Verify adoption and version state

- [x] Verify roadmap/checklist/index/readiness agree on active V37, closed V1-V36,
      `4.5.0-SNAPSHOT`, baseline `4.4.2`, `plannedFinalVersion=null` and unselected
      release lane; run archive/version/documentation guards.
- [x] Preserve V36's reduced-scope no-release decision and unresolved A001/A002;
      adoption does not retrospectively pass them or complete Priority 1.

### [x] 1.2 Inventory source and reusable evidence

- [x] Record reachable source/tree, worktree changes, toolchains, JDK/JMH/Boot and
      dependencies; inventory raw samples, saved JARs, patches, manifests/archives.
- [x] Rehash inputs before reuse and record missing data, inferred process counts
      and unavailable historical revisions without manufacturing provenance.

### [x] 1.3 Initialize the narrow finding ledger

- [x] Record A001, A002 and E001 with owners, evidence, proof obligations, controls,
      uncertainty and stop conditions; retain independent profile dispositions.
- [x] Confirm exhausted V35/V36 approvals grant no worker, pilot, probe or time
      budget, and C001-C005 are not reopened by this follow-up.

**Priority 1 record (2026-10-18):** [Baseline and narrow ledger](BASELINE-SCOPE.md).
Started from clean `7e67a7ccec5ef8b0100d4b559d91e34ad8b8c2fc`; fresh checks use
that reachable base plus the retained documentation/test patch. The read-only
audit verifies 5,132 files across 15 bundles and their complete archives, 392
unchanged runtime/build/test/fixture inputs and both saved benchmark JARs.
All 121 historical classpath paths per artifact are now absent; input hashes
and shaded artifacts are preserved, not a claim of a runnable old environment.
Lost V35 recordings remain unavailable, and non-ancestor execution objects keep
their original provenance rather than being relabeled as reachable revisions.

Verification passes **121 documentation cases per Boot row** (4.0.0/4.1.0), zero
failures/errors/skips, **nine archive-verifier tests**, reactor validation,
baseline-provenance fixtures, matrix-script syntax and `git diff --check`.
Fresh effective POMs/dependency trees and test classpath hashes are retained;
Java 21.0.8/Maven 3.9.9 use explicit GC disabled for ordinary tests. Repeated
verification is not counted as additional unique cases. No benchmark, runtime
full-suite, API, consumer, AOT/native or new Central verification is claimed.

A001/A002 remain unresolved/blocking; E001 is feasibility pending, not a new
confirmed defect. V1-V36 history and all versions are unchanged. Priority 2.3
must decide proceed-to-plan or stop; 4.3, 7.2 and Priority 10 retain their separate
approval gates. Priority 1 launches zero workers and selects no numerical budget,
production correction or release. Evidence is retained under
`target/release-evidence/v37/priority1/`; the external seal follows verification.

The Priority 1 bundle seals **146 files** at
`verification-evidence/v37-priority1/`, mirrored under Maven output. Manifest
SHA-256: `a21c2f68403d8f20460dd8efe02e4cd4cd35a823af2da9deb26c8d3af68249eb`.
Archive `verification-evidence/v37-priority1-20261018.tar.gz` SHA-256:
`7a15f706e7b00c32ab7c6e7d9cc6b55e8fd97b910f4084123cd5002c1a60bdeb`.
Copied and archived contents and the complete base-plus-patch were verified.
This external integrity paragraph is added after sealing to avoid self-reference;
it is the only excluded delta. Ignored local copies are not an off-machine backup.

## Priority 2 - Attribution Feasibility and Stop Decision

### [ ] 2.1 Identify the missing causal observation

- [ ] Audit available ordinary, marker, traced and intervention evidence offline;
      state what each supports and the missing high-mode causal bridge per profile.
- [ ] Distinguish installed/inlined/eliminated code and iteration envelopes from
      exact executing-code identity; preserve old acceptance and uncertainty.

### [ ] 2.2 Evaluate a materially different method

- [ ] Compare methods using existing tooling/source evidence, with expected
      perturbation, permissions/resources and outcomes that would refute usefulness.
- [ ] Specify minimum sufficient evidence for each proposed explanation. Another
      profiler name, more forks or repetition of V36's low-only tracing is not proof
      of feasibility and authorizes no pilot.

### [ ] 2.3 Obtain proceed-to-plan or stop approval

- [ ] Record the maintainer's explicit decision and rationale before advancing;
      proceeding permits planning only, not execution before 4.3.
- [ ] If no credible method exists, stop before launching workers, retain findings
      unresolved and record dated applicability for later priorities. Complete
      applicable verification/preservation and seek a separate closure decision.

## Priority 3 - Minimal Capture and Process-Accounting Contract

### [ ] 3.1 Specify the smallest missing observation

- [ ] Reuse [capture](../../scripts/capture-v36-allocation.py),
      [review](../../scripts/review-v35-allocation.py) and
      [archive verification](../../scripts/verify-v36-evidence.py) where applicable;
      add only evidence needed by the approved feasibility approach.
- [ ] Bind artifact/profile/scenario, worker identity, commands/VM flags, measurement
      boundaries and compiler/class-load records. Preserve unknown code identity;
      timestamp sorting is not a trace-to-score join.

### [ ] 3.2 Define complete process accounting

- [ ] Specify how every launcher, measured worker, internal JMH probe, preflight
      and profiler/helper process is identified and charged before execution.
- [ ] Record observation limits and abort behavior for unexpected launches or
      incomplete association; inferred counts must not be called a process census.

### [ ] 3.3 Verify tooling with bounded synthetic fixtures

- [ ] Test missing, duplicate, truncated, mismatched, reordered and superseded
      records; preserve a failing synthetic reproduction for each tooling fix.
- [ ] Review what corrected conclusions need fresh evidence. Keep diagnostic JVM
      and recorder smoke behind 4.3, not disguised as deterministic tooling tests.

## Priority 4 - Freeze and Approve One Bounded Investigation

### [ ] 4.1 Freeze inputs and distinguishing lanes

- [ ] Pin artifact/harness/JDK/JMH/Boot/dependency identities, workload phase,
      payload, VM flags, heap/threads, iterations, units, order and host conditions.
- [ ] Select minimal ordinary/observed/intervention and same-JAR controls, with
      untimed registry/hook witnesses. Changed stacks remain separately labeled;
      instrumentation cannot silently enter ordinary scoring.

### [ ] 4.2 Set numerical execution and stop bounds

- [ ] Freeze per-category and total process ceilings, worker count, stage deadlines,
      cumulative time and memory/storage limits, including every pilot/probe/control.
- [ ] Specify stop rules for failure, absent modes, unexpected processes, incomplete
      evidence and exhausted bounds; preserve partial results, with no automatic retry.

### [ ] 4.3 Obtain explicit investigation approval

- [ ] Record approval of the exact plan, numerical schedule and stop policy before
      any benchmark or diagnostic execution, including smoke or preflight processes.
- [ ] Verify no inherited budget and require a new recorded decision before any
      extension, with cumulative rather than reset process/resource accounting.

## Priority 5 - Ordinary-Mode Capture and Perturbation Checks

### [ ] 5.1 Execute only the approved schedule

- [ ] Verify inputs and host preconditions before launch; use fresh directories
      with no competing scored work, preserving every exit, interruption and failure.
- [ ] Capture every scheduled artifact/profile/worker and interval, not only
      favorable modes; stop at the frozen limits without unapproved replacement runs.

### [ ] 5.2 Evaluate observation perturbation

- [ ] Compare ordinary and observed mode availability separately without pooling
      scores; record suppressed/changed modes as observation limits, not fixes.
- [ ] Preserve absent high modes as missing evidence; do not infer resolution from
      forced non-inlining, same-JAR controls or another profile's explanation.

### [ ] 5.3 Reconcile execution and evidence integrity

- [ ] Verify actual process counts, category totals, resource use and per-worker
      association against approval; retain incomplete or unexpected work as failures.
- [ ] Record what capture establishes and what it cannot. Request a new decision
      if bounds or stop rules prevent completion; do not launch more work to improve it.

## Priority 6 - Causal Review and Independent Finding Dispositions

### [ ] 6.1 Review each ordinary-mode explanation

- [ ] Relate high/low observations to relevant compilation, installation,
      invalidation and class-load history under the frozen proof obligations.
- [ ] Preserve uncertainty when sampling/envelopes cannot identify executed code;
      a forced high-mode intervention tests mechanism, not necessarily ordinary cause.

### [ ] 6.2 Separate tooling, runtime and measurement conclusions

- [ ] Classify reproduced tooling defects, runtime defects, explained variability
      and unresolved limits; require affected evidence for stronger corrected claims.
- [ ] Keep contrary observations and original scores. Any further experiment needs
      prior numerical approval; causal review creates no implicit follow-up lane.

### [ ] 6.3 Record independent and combined dispositions

- [ ] Record A001/A002 and E001 outcomes, evidence, owners and reopening triggers;
      do not transfer AUTO_NO_REGISTRY or control acceptance to either profile.
- [ ] Mark unexplained flags unresolved/blocking and identify closure implications;
      completing approved execution alone resolves no finding.

## Priority 7 - Explicit Correction or No-Change Selection

### [ ] 7.1 Specify a bounded response to the evidence

- [ ] Prefer no production change for required behavior or inconclusive evidence;
      distinguish a scoped tooling correction from any proposed runtime optimization.
- [ ] For a reproduced runtime defect, specify the smallest correction, contracts,
      benefit/correctness-cost criterion, regression controls and rollback.

### [ ] 7.2 Obtain implementation or no-change approval

- [ ] Record explicit approval before any production edit; do not reinstate rejected
      V34/V35 candidates or disable genuine registry/hook behavior by default.
- [ ] State that a no-change decision grants no extra experiments, performance
      acceptance, scope exclusion, closure or release.

### [ ] 7.3 Verify retained changes and rejected candidates

- [ ] Verify focused positive/negative regressions for each retained change, including
      late/prototype discovery, ordering/supports, fresh metadata, mutation validation
      and subscription-local body/context/terminal ownership where affected.
- [ ] Roll back a failed candidate and preserve its evidence; record justified N/A
      for unselected runtime implementation, not an unexecuted pass.

## Priority 8 - Matched Cost, Compatibility and Ownership Acceptance

### [ ] 8.1 Decide applicability and approve any new scoring

- [ ] Freeze applicability and exact unchanged-input reuse limits before scoring;
      changed shared default paths require the existing 60-row matrix.
- [ ] Obtain a separate numerical budget for new scores, covering all processes
      and controls; unused diagnostic time does not authorize scored execution.

### [ ] 8.2 Establish independent and combined cost acceptance

- [ ] Keep Central-only `4.4.2`, matched non-starter dependencies/harness, semantic
      witnesses, both artifact orders and all raw samples/intervals.
- [ ] Apply original review triggers, >20% latency and >max(32 B/op, 5%) allocation;
      retain primary flags through confirmation and distinguish scores from diagnostics.

### [ ] 8.3 Verify compatibility, cross-path and ownership applicability

- [ ] For tooling/docs-only work, run affected parser/workload/archive/readiness/
      guidance guards and justify runtime-evidence reuse. On a feasibility stop,
      still verify the decision, links, lifecycle and applicable preserved evidence.
- [ ] For runtime/fixture changes, verify affected public/factory/mock/assembled,
      optional-absence and runtime/AOT/lifecycle paths on genuine Boot 4.0.0/4.1.0.
- [ ] For retained runtime changes, attempt strict root and independent starter
      source/binary comparisons against isolated Central even if the first fails;
      run applicable packaging and clean-source native compile/run. Record actual
      totals, failures and dated reuse/N/A, with controlled reachability where needed.

## Priority 9 - Durable Evidence and Maintainer Guidance

### [ ] 9.1 Seal and verify durable evidence

- [ ] Seal reachable source or base-plus-patch, approvals, commands/exits,
      toolchains/dependencies, raw results, failures and file/archive hashes.
- [ ] Verify copied and archived contents outside Maven output before cleanup;
      record location/access/backup limits without calling a local copy off-machine.

### [ ] 9.2 Preserve historical truth and rejection guards

- [ ] Keep V35/V36 originals unchanged; test missing/corrupt/incomplete evidence
      rejection and distinguish regeneration from recovery of lost recordings.
- [ ] Preserve actual process-accounting and executing-code limits; no missing
      mode, exhausted budget or inferred census becomes a passed gate.

### [ ] 9.3 Reconcile maintainer and operations guidance

- [ ] Align findings, intentional costs, supported alternatives and reopening
      triggers with current docs/examples/metadata/readiness and baseline versions.
- [ ] Check sanitized evidence boundaries; make no snapshot-upgrade remedy,
      application-memory/mesh diagnosis, new telemetry or unsupported performance claim.

## Priority 10 - Scope Decision and Conditional Release or Closure

### [ ] 10.1 Reconcile findings and closure scope

- [ ] Reconcile all three rows, including any feasibility stop, with evidence-backed
      dispositions. Full-scope closure requires acceptance, not merely completed runs.
- [ ] If unresolved findings remain, obtain explicit named exclusion from closure
      obligations or keep V37 open; exclusion and no-release are not performance passes.

### [ ] 10.2 Select release or no-release intent

- [ ] Obtain a separate maintainer decision; consider a compatible patch first only
      for a justified retained change. `4.5.0-SNAPSHOT` selects no minor release.
- [ ] For release, approve exact coordinates and update current inputs together.
      Otherwise record no-release scope; future implementation/release stays unselected.

### [ ] 10.3 Assemble immutable release or review evidence

- [ ] Seal final decisions, delivered/rolled-back/unresolved scope, verification,
      approvals and hashes with reproducible provenance and verified durable copies.
- [ ] For release, verify final packaging, signing/signatures and tag/source/workflow
      separately. For no release, record unrun release gates as N/A, not passed.

### [ ] 10.4 Verify publication or explicit no-release closure

- [ ] For release, verify publication and isolated Central consumption separately;
      preparation is not final GO. Keep baseline `4.4.2` and V37 active until verified
      closure, then update current version references from the verified release.
- [ ] For either branch, close checklist/roadmap/index/readiness together only after
      the decision and applicable gates. Retain exclusions and unresolved flags;
      leave future scope unselected and preserve V1-V36 history.

## Completion Rule

- [ ] Every required row has an evidence-backed disposition or an explicit named
      closure exclusion; no stop/no-change/no-release decision silently clears it.
- [ ] Applicable execution, approval, integrity and verification gates have actual
      results or dated justified N/A; no inherited budget or diagnostic-score substitution.
- [ ] Lifecycle, guidance and final evidence agree; no release is claimed without
      independent signing/publication/consumer verification.
