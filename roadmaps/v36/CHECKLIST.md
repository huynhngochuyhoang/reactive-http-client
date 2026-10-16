# Reactive HTTP Client - Roadmap V36 Execution Checklist

> **Status:** active
> **Theme:** remaining allocation attribution and measurement reliability
> **Published baseline:** `4.4.2`
> **Development coordinate:** `4.5.0-SNAPSHOT`
> **Proposed scope:** V35 AUTO_REGISTRY and HOOK allocation flags; evidence reliability needed to classify them
> **Experiment authorization:** V36-P3-1 plus V36-P5-1; 48/48 worker slots consumed, no further execution authorized
> **Implementation and release scope:** approved no production change (V36-P6-1); release unselected
> **Adopted:** 2026-10-12

Execution companion to [ROADMAP.md](ROADMAP.md). Adoption authorizes baseline,
source/evidence review, deterministic tooling tests and experiment planning only.
Creating this checklist completes no execution item. No benchmark or diagnostic
JVM run is authorized before **3.3**; no production edit before **6.2**; no release
or reduced-scope closure before **Priority 10**. V1-V35 history stays unchanged.

Execute priorities in order; record justified dependency-based reordering before
work. Preserve failed attempts and missing evidence. Mark a box only after its
actual result, source and verification are recorded. Completing an approved run
is not resolving a finding. Conditional work may be N/A only with a dated reason
and applicability evidence, not because execution failed or a mode was absent.

## Finding and Approval Gates

| Required row | Owner | Initial state / acceptance |
|---|---|---|
| V36-A001 / AUTO_REGISTRY | Performance maintainer | Unresolved; ordinary high-mode evidence and per-profile causal disposition required |
| V36-A002 / HOOK | Performance maintainer with observer/hook review | Unresolved; independent attribution preserving real hook semantics required |
| V36-E001 / evidence reliability | Benchmark tooling maintainer | Review pending, not a confirmed defect; fork/trace association and evidence integrity must be verified |

Keep the two allocation findings independent. Valid outcomes are **explained
without production change**, **fixed and verified** after a reproduced defect and
approved correction, or **unresolved/blocking**. An absent high mode, forced
non-inlining or a clear confirmation run alone cannot resolve A001/A002. Any
unresolved row blocks full-scope closure unless the maintainer explicitly excludes
it; exclusion is not a performance pass.

| Gate | Requirement |
|---|---|
| 1-2 | Reconcile available inputs and audit workload/evidence semantics before new measurement |
| 3.3 | Approve an exact numerical budget, schedule and stop rules before any experiment |
| 4-5 | Execute only approved ordinary/diagnostic lanes and retain contrary or incomplete results |
| 6.2 | Approve any production correction with controls, acceptance and rollback before editing |
| 7-8 | Establish per-profile/combined cost and applicable compatibility/ownership acceptance |
| 9-10 | Preserve evidence, reconcile guidance, obtain explicit scope/release decision and verify closure |

C001-C005 and both C004 subrows retain V35's no-change outcomes. No dependency,
feature, API/SPI, default, telemetry, JVM/pool/scheduler tuning or broad handler/AOT
rewrite is selected. No experiment or implementation approval carries over from V35.

## Evidence Rules

- Preserve commands/exits, actual counts, toolchains, effective dependencies,
  raw samples, source/JAR hashes and clean/dirty state. A dirty patch is a base
  revision plus hashed changes, never an invented clean commit.
- Use fresh stage directories under `target/release-evidence/v36/priority<N>/`
  and verified durable copies outside Maven output **before any cleanup**. Keep
  manifests and archive hashes in tracked records; ignored bundles are not in a clone.
- P2-P8 lost V35 raw evidence remains unavailable. New regeneration has new
  provenance; unchanged-input reuse must identify exact inputs and narrower limits.
- Freeze measurement rules before scoring; preserve all forks/iterations, both
  artifact orders, interruptions and failures. No hidden pilots, automatic retries,
  favorable-fork selection or threshold resets. Do not run competing scored work.
- Separate ordinary measurement, traced attribution and single-factor intervention;
  diagnostic results do not replace release scores. B/op is not retention or RSS.
- Keep ordinary tests independent of forced GC. Use controlled reachability lanes
  for collection claims and deterministic gates/cleanup acknowledgements for races.
- Export only bounded synthetic/sanitized evidence, not production bodies, headers,
  identities, cache keys, request targets or arbitrary error text. Keep credentials
  and signing passphrases out of records.

Create records when evidence exists, not empty placeholders. Suggested records:
`BASELINE-SCOPE.md`, `EVIDENCE-AUDIT.md`, `INVESTIGATION-PLAN.md`,
`ALLOCATION-EVIDENCE.md`, `CORRECTION-DECISION.md`, `VERIFICATION.md`,
`MAINTAINER-GUIDANCE.md` and `RELEASE-DECISION.md`. Reuse existing V34/V35 tooling
where sound; no second benchmark framework is selected.

## Priority 1 - Post-V35 Baseline and Narrow Scope Integrity

### [x] 1.1 Verify adoption and version state

- [x] Verify roadmap/checklist/index/readiness agree on active V36, closed V1-V35,
      `4.5.0-SNAPSHOT`, baseline `4.4.2` and `plannedFinalVersion=null`.
- [x] Run archive/version/documentation guards; preserve V35's two unchecked
      acceptance markers and explicit reduced-scope/no-release decision.

### [x] 1.2 Reconcile reachable source and evidence availability

- [x] Record source/tree, worktree, toolchains and effective dependencies; inventory
      P9-P12 bundles, artifact/JDK/harness hashes and published provenance.
- [x] Rehash available inputs and state exact reuse limits. Record missing historical
      objects/bundles honestly; regeneration must not claim the lost run's identity.

### [x] 1.3 Initialize the narrow finding ledger

- [x] Record A001, A002 and E001 with owners, controls, open questions, evidence,
      acceptance and stop conditions; keep the two profiles independent.
- [x] Identify possible MINIMAL/AUTO_NO_REGISTRY/RESILIENCE_ENABLED_ONLY controls
      without approving their execution or reopening C001-C005.

**Priority 1 record (2026-10-12):** [Baseline and narrow ledger](BASELINE-SCOPE.md).
Clean inspected source `728653e4fb3db911cddd362ab0c22708619bb68f`; fresh guards
use that base plus the retained documentation/test patch. All 3,274 files and
five archives in available V35 P9-P12 bundles rehash, along with both benchmark
artifacts, 121 classpath JARs per side and 39 harness/build inputs. P10's recorded
source is locally available but not an ancestor; reachable input reconciliation
does not relabel that run. P2-P8 lost recordings remain unavailable.

The three ledger rows retain A001/A002 unresolved and E001 pending review, not a
confirmed defect. V35's two unchecked acceptance markers and all V1-V35 records
stay unchanged. No benchmark/diagnostic fork, runtime edit or release is selected;
Priorities 3.3, 6.2 and 10 retain their separate approval gates.

Fresh verification passes **108 documentation cases per Boot row** (4.0.0 and
4.1.0), zero failures/errors/skips, plus reactor validation, baseline-provenance
fixtures, matrix-script syntax and `git diff --check`. Both effective dependency
inventories are captured with Java 21.0.8/Maven 3.9.9; ordinary test forks disable
explicit GC. The new guard's expected initial missing-record failure is retained.
No fresh full-module, API, consumer, AOT/native or performance run is claimed.

The Priority 1 bundle seals **143 files** at
`verification-evidence/v36-priority1/`, mirrored at
`target/release-evidence/v36/priority1/`. Manifest SHA-256:
`f97eed03840851470b58d060a1fd08d5b30d2b7d685ef9ba77924a128937dc02`.
Archive `verification-evidence/v36-priority1-20261012.tar.gz` SHA-256:
`f556547626e006dda88796ddfb09e563952cbb0e965387a8116005f38a0f1e4b`.
Copied and archived contents were verified. This external integrity paragraph is
added after sealing to avoid self-reference; it is the only excluded delta.

## Priority 2 - Audit Workloads, Fork Identity and Compiler Evidence

### [x] 2.1 Verify effective workload boundaries

- [x] Inspect existing workload/runner/reviewer code and available saved records;
      verify AUTO_REGISTRY/HOOK beans, features, GET scenario and publisher use.
- [x] Verify untimed semantic witnesses distinguish warm assembly, subscription,
      transport, construction and first use; keep instrumentation outside scored work.

### [x] 2.2 Audit fork association and relevant compiler history

- [x] Require unambiguous artifact/profile/process/trace association; review the
      existing timestamp ordering without assuming sorting proves identity.
- [x] Identify relevant tasks, installed code, recompilation and deoptimization
      relative to measurement; document unavailable timing/code-version facts.
- [x] Add deterministic synthetic fixtures for duplicate, missing, truncated,
      reordered and mismatched records, including unrelated/superseded tasks.

### [x] 2.3 Record audit conclusions and bounded tooling corrections

- [x] Distinguish verified behavior, reproduced parser/harness defects and unknowns.
      Preserve a failing fixture and affected conclusions for each actual defect.
- [x] Record any local tooling correction and its tests separately; require new
      affected evidence before relying on corrected conclusions. No runtime patch
      or new measured fork is authorized by an audit concern.

**Priority 2 record (2026-10-13):** [Workload and compiler-evidence audit](EVIDENCE-AUDIT.md).
Reachable base `18c64f7b6147189fc2c1229816e97c46bc6a9d39` plus the retained
tooling/test/documentation patch. All 931 P10 files rehash. Corrected re-analysis
reads 58 existing compiler processes across 22 diagnostic stages; it does not
create new scores or forks. Unsupported timestamp-to-score joins are removed,
command/trace validation is shared, and process-local compilation histories are
retained without claiming measurement-time code identity.

Workload semantics pass 40 untimed JUnit cases. E001 has reproduced local tooling
defects and bounded corrections, but durable per-fork association and measurement
boundaries remain unavailable in the old recordings. A001/A002 remain unresolved;
Priority 3.3 must approve new capture. V1-V35 history and raw evidence are unchanged.

Fresh verification passes **63 Python tooling tests**, **40 untimed workload
cases**, and **109 documentation cases per Boot row** (4.0.0 and 4.1.0), zero
failures/errors/skips in accepted runs. The original-reader failing reproduction
is retained, not counted as passing. `git diff --check` passes. No production,
timed harness, API, native or scored-performance change/run is claimed.

The Priority 2 bundle seals **131 files** at
`verification-evidence/v36-priority2/`, mirrored at
`target/release-evidence/v36/priority2/`. Manifest SHA-256:
`402a0f0c837eab3e6f0fe35dfd46a4cd17c866f53b4559c74b2b8e882ffb2967`.
Archive `verification-evidence/v36-priority2-20261013.tar.gz` SHA-256:
`b6d06ef27e939a1adcb478aeed948b3b6ca8675bfa5fbd775ee711c4f506512c`.
Copied and archived contents were verified; all 22 derived stage metrics match
their historical summaries. This external integrity paragraph is added after
sealing to avoid self-reference and is the only excluded delta.

## Priority 3 - Freeze and Approve a New Bounded Investigation

### [x] 3.1 Specify hypotheses and distinguishing observations

- [x] Cover ordinary compiler modes, instrumentation perturbation, workload/phase
      mismatch and artifact/dependency differences with evidence that could refute each.
- [x] Separate lowest-perturbation ordinary, traced and intervention lanes; specify
      how missing modes and perturbation will limit attribution per profile.

### [x] 3.2 Freeze exact inputs, schedule and resource bounds

- [x] Freeze profiles/controls, artifacts, JDK/JMH/Boot/dependencies, VM flags,
      heap, threads, warmup/measurement, units, order and same-JAR controls.
- [x] Start from recorded Java 21.0.8/Boot 4.0.0 where available; changed stacks
      are separate experiments, not silent replacements of historical comparisons.
- [x] Set a numerical fork ceiling, per-stage timeout, total resource/time bound,
      host preconditions and abort policy covering every control/trace/intervention.

### [x] 3.3 Obtain explicit experiment approval

- [x] Record the maintainer's approval of the frozen plan and stop rules before
      launching any benchmark or diagnostic JVM, including pilots.
- [x] Confirm V35's 338-fork history and exhausted 36-fork extension grant no new
      budget. Failures or absent modes do not authorize retries; extensions require
      a new recorded decision before additional execution.

### Priority 3 Planning Record

[V36-P3-1](INVESTIGATION-PLAN.md) freezes competing hypotheses, saved artifact/JDK
hashes, exact measurement settings and the ordered **48-fork** O/M/T/I schedule.
The numerical ceiling includes same-JAR and instrumentation controls; each stage
has a 120-second deadline and the cumulative active investigation is capped at
120 minutes. Failure aborts the schedule; neither failure nor missing modes grants
retries. V35's 338 forks and exhausted extension confer no authorization.

The new identity contract uses one worker per stage and a separately controlled
iteration recorder. Its intervals are envelopes, not exact timed-loop boundaries
or proof of executing code. Recorder/runner implementation and actual E001 capture
verification remain Priority 4 work. A001/A002 remain unresolved.

Started from clean `56bb5753cd1c247654360c86a5257a1c2aaf201c`; the planning delta
contains only documentation and its regression guard. Eight pinned input hashes
were rechecked without rebuilding saved artifacts. On 2026-10-13 the maintainer
replied **"Approve V36-P3-1 (Recommended)"**, selecting this 48-fork / 120-minute
investigation with fixed lanes and no retries after failure without a new decision.
This authorizes later Priorities 4/5 only, not production edits or a release.
**No benchmark or diagnostic fork was launched; all 48 slots remain unused.**

Verification passes **63 Python tooling tests** and **110 documentation cases per
Boot row** (4.0.0 and 4.1.0), with zero failures/errors/skips. The documentation
guard checks exact lane order, contiguous stage IDs, 48-fork arithmetic, limits,
links and the recorded approval. `git diff --check` passes. The initial Boot 4.0.0
run passed, but its report-copy step used an incorrect filename; that failed
collection attempt is retained separately from corrected complete runs.
No fresh workload, full-reactor, API, consumer, native or performance run is claimed.
Commands, input hashes, reports, readiness and base-plus-patch provenance are
preserved at `verification-evidence/v36-priority3/`, mirrored at
`target/release-evidence/v36/priority3/`; the external seal follows verification.

The Priority 3 bundle seals **59 files**. Manifest SHA-256:
`2ce190c822d2f64936038b3c051ea14b65a1701dd9111570100930a2d19edd1c`.
Archive `verification-evidence/v36-priority3-20261013.tar.gz` SHA-256:
`461b1920e9351050b009342e5093b53ed3509ac013821e37e279cb7e41257d69`.
Copied and archived contents were verified, including the exact plan approved
before its status update. This integrity paragraph is added after sealing to
avoid self-reference and is the only excluded delta.

## Priority 4 - Capture Ordinary Allocation Modes Per Profile

### [x] 4.1 Validate inputs and execute the approved ordinary schedule

- [x] Verify artifact/plan/toolchain hashes and host limits before each stage;
      retain commands, exits and host/JVM observations without competing scored work.
- [x] Keep all forks/iterations and artifact orders; preserve partial/failed attempts
      and stop at the approved boundary rather than selecting favorable reruns.

### [x] 4.2 Associate observations independently for A001 and A002

- [x] Couple traces to their actual measured forks on both matched artifacts and
      approved same-JAR controls. Reject ambiguous evidence rather than guessing.
- [x] Record per-profile high/low modes, uncertainty and absent modes; retain raw
      outputs with reproducible summaries, not just means or best forks.

### [x] 4.3 Record ordinary evidence and observation limits

- [x] State whether instrumentation affected observability and which ordinary
      high-mode compiler/class-load evidence is still missing.
- [x] Preserve incomplete attribution when the budget ends. An intervention cannot
      manufacture an ordinary witness; completed execution is not finding acceptance.

### Priority 4 Evidence

[Ordinary capture](ORDINARY-CAPTURE.md) records source, implementation, all lanes,
same-JAR observations, compiler history and limits. From clean
`3d9253ca25aae8190d7a8b37116a04a7b8fa15b2` plus the sealed tooling/test patch,
both preflights and **40 O/M/T workers** completed successfully in **502.141
seconds**. All 200 measurement iterations are retained. No retry or intervention
ran; IDs 41-48 remain for Priority 5 within the original cumulative time budget.
Final inspection found unbudgeted JMH-internal blackhole-detection JVM probes,
inferred from JMH bytecode and launcher logs but not individually recorded.
On 2026-10-14 the maintainer answered **"Accept with documented exception
(Recommended)"**, accepting the observations and Priority 4 closure without a
rerun. This is not retroactive authorization or full process accounting. No new
worker or retry is authorized; Priority 5 needs explicit probe-accounting
revision before execution. The frozen plan and original records are unchanged.

HOOK produced one untraced high fork per artifact (stages 10 and 15), alongside
low forks of each same JAR. Every AUTO_REGISTRY, marker-only and traced fork was
low. All 40 worker associations and 16 compiler/class-log pairs validated;
24 workers have iteration envelopes, not exact executing-code identity. E001's
new recording boundary is verified with those limits. **A001/A002 remain
unresolved/blocking**; missing ordinary high traces are not supplied by approval,
elapsed budget or a later forced intervention. V1-V35 evidence is unchanged.

Verification passes **74 tooling tests**, **40 untimed workload cases**, the
recorder callback contract and **111 documentation cases per Boot row**, with
zero failures/errors/skips. `git diff --check` passes. Re-analysis reproduced all
40 stage reports and rehashed 931 historical P10 files without new forks. No
production, dependency, native, API, consumer or full acceptance change/run is
claimed. Evidence is mirrored under `verification-evidence/v36-priority4/` and
`target/release-evidence/v36/priority4/`; the external seal follows verification.

The Priority 4 bundle seals **597 files**. Manifest SHA-256:
`07a1785de010946808ebefb68eee512754b96383301d48a475a041b7b8cec44b`.
Archive `verification-evidence/v36-priority4-20261014.tar.gz` SHA-256:
`13b35a8f51bb899e9624c37c2344d691b3afba7f5fcc25d915448c8a0ed10b47`.
Copied and archived contents were verified, including the unchanged frozen plan,
original capture and accepted accounting exception. This integrity paragraph is
added after sealing to avoid self-reference and is the only excluded delta.

## Priority 5 - Test Causal Explanations Without Replacing Scores

**Execution gate:** [V36-P5-1 accounting amendment](INTERVENTION-ACCOUNTING.md)
approves eight remaining workers and sixteen inferred internal probes, with no
new preflights or retries. The maintainer answered **"approve v36-p5-1"** on
2026-10-14. That approval authorized only the run below; the original cumulative
budget is not reset. All eight remaining slots have now been consumed.

### [x] 5.1 Execute only approved one-factor controls

- [x] Challenge the leading explanation with the approved interventions and preserve
      workload semantics, all results and budget accounting.
- [x] Label forced non-inlining, disabled escape analysis or controlled class loading
      as diagnostics, not production advice, ordinary evidence or release scores.

### [x] 5.2 Build per-profile causal evidence chains

- [x] Link inputs, measured modes, relevant compiler history and interventions;
      retain contrary evidence and distinguish sampling from exact object accounting.
- [x] Do not extend AUTO_NO_REGISTRY's explanation to A001/A002 without their own
      evidence. State what an untraced historical fork cannot establish.

### [x] 5.3 Classify findings and remaining limits

- [x] Disposition A001 and A002 independently as explained without production change,
      reproduced defect requiring selection, or unresolved/blocking.
- [x] Disposition E001 with tests and affected-result applicability. Evidence-processing
      corrections must not erase raw observations; non-reproduction is not resolution.

### Priority 5 Evidence

[Intervention results](INTERVENTION-RESULTS.md) record all eight I workers,
per-profile causal chains, contrary evidence and independent dispositions. The
maintainer approved V36-P5-1 before execution. Reachable base
`fe13031f171dd33fc86e6ee5392c2b83a14407e4` plus the sealed tooling/test/docs patch
reused the exact saved JARs and recorder, without production or harness changes.

Stages **41-48** exited zero and validated; all **40 measurement iterations**
remain. No retries, preflights or extra profiles ran. New execution consumed
**101.688487 seconds**, cumulative **603.828999 seconds**. All **48/48 worker
slots are exhausted**. Sixteen new probes are inferred under the accepted
accounting limit, not individually observed: combined accounting is 96 inferred
probes plus 48 launchers, 48 workers and two historical preflights.

Each profile/artifact produced two I-high forks near 2000 B/op versus four T-low
forks near 1744 B/op. All eight I histories contain the requested compiler
refusals; 28 call-site refusals and 763 relevant tasks/installations are retained.
This supports a controlled inlining-boundary effect, not the cause of an
untraced ordinary high mode. **A001 and A002 remain unresolved/blocking**. E001's
new association/validation is verified with explicit limitations; no legacy
pairing, exact executing-code identity or individual probe census is asserted.
No additional experiment, production change or release is selected.

Verification passes **83 tooling tests** and **113 documentation cases per Boot
row** (4.0.0/4.1.0), zero failures/errors/skips. Re-analysis reproduced all 48 stage
reports and rehashed 597 P4 and 931 P10 files. Prior workload/callback checks are
reused, not fresh runs. `git diff --check` passes. No full reactor/API/consumer/
native or release scoring rerun is claimed. Evidence is preserved under
`target/release-evidence/v36/priority5/` and `verification-evidence/v36-priority5/`;
the external seal follows verification.

The Priority 5 bundle seals **201 files**. Manifest SHA-256:
`b1b6a7ec0e180f959feb3fd4901ef9cc4ec65c7abc499e5ec43ecb6e92c4dc54`.
Archive `verification-evidence/v36-priority5-20261014.tar.gz` SHA-256:
`d963c7f7e8eddcc04c63bc72f61fc530310685b099feec826d0d6bef0952e905`.
Copied and archived contents were verified, including the approved amendment,
capture-time source patch, all eight raw stages and final result/test evidence.
This integrity paragraph is added after sealing to avoid self-reference and is
the only excluded delta.

## Priority 6 - Select a Bounded Correction or No-Change Outcome

### [x] 6.1 Compare the smallest justified options

- [x] Evaluate bounded tooling correction, local production fix and no production
      change against demonstrated need; required work or compiler variation alone
      does not justify optimization.
- [x] Name affected profiles, semantics, ownership, acceptance and rollback. Stop
      out-of-scope API/default/dependency or architectural needs for separate approval.

### [x] 6.2 Obtain implementation or no-change decision

- [x] Record explicit maintainer approval before any production edit. Tooling-only
      authorization is not runtime authorization; no-change is an evidence-backed outcome.
- [x] Keep late registration, prototype/provider/order/support behavior, fresh public
      metadata, full mutation validation and body/context/terminal ownership in scope.

### [x] 6.3 Implement and verify only the selected boundary

- [x] Verify approved corrections with focused regressions and selected cache/auth/
      resilience controls. No shared mutable state, skipped cleanup or cached discovery.
      Correction-specific work N/A; existing retained-behavior controls pass below.
- [x] Require repeatable attributed benefit or approved reproduced correctness cost;
      roll back independently if acceptance fails and retain the evidence.
      Candidate acceptance/rollback N/A on 2026-10-14: no runtime correction selected.
- [x] If no correction is selected, record implementation N/A and substantiate retained
      behavior; do not reinstate unrelated rejected V35 patches.

**Priority 6 decision (2026-10-14):** [Correction/no-change record](CORRECTION-DECISION.md).
The maintainer answered **"Approve no production change (Recommended)"** to
V36-P6-1, retaining the verified tooling corrections and current runtime behavior.
This approves no experiment, scope exclusion, closure or release. A001/A002 stay
**unresolved/blocking**; E001 remains verified only for the new capture within its
recorded limits. Implementation N/A because no runtime correction is selected;
there is no new candidate to score or roll back. V35 candidates remain rejected.

Fresh retained-behavior checks pass **141 cases across eight classes per Boot
row** (4.0.0/4.1.0), zero failures/errors/skips, plus **83 tooling tests**.
Java tests disable explicit GC. The record identifies discovery, fresh metadata,
full mutation validation, body/context/reporting ownership and selected cache/
auth/resilience witnesses. All **798 P4/P5 files** and both archive hashes
revalidate; **202 production/build/benchmark inputs** are unchanged from V36
adoption. Verification uses reachable base `71f989f5492bcd8c38d1c63197f56b507fa311c9`
plus this docs/guard patch. No new experiment, production change, full-reactor,
API, assembled-consumer or native run is claimed. Priorities 7-10 remain pending.

Final documentation/readiness/link guards pass **114 cases per Boot row**,
zero failures/errors/skips: **255 Java cases per row** including the 141
retained-behavior controls, not an additional distinct-contract count. The new
guard's initial missing-record failure and the first full documentation run's
stale unselected-scope assertion are preserved; the corrected rerun passes.
`git diff --check` passes. Evidence is retained under
`target/release-evidence/v36/priority6/` and copied to
`verification-evidence/v36-priority6/` before cleanup; the external seal follows.

The Priority 6 bundle seals **87 files**. Manifest SHA-256:
`4ff957a173bda3af19e191695d4f1f0c7ae4b6b85a0a50d606ad9507cdddcce7`.
Archive `verification-evidence/v36-priority6-20261014.tar.gz` SHA-256:
`244bce01667664bfb60d069381b79753da22c3e1d28644dbec394d80c71e9cc8`.
Copied and archived contents were verified, including the approval, source patch,
unchanged-input inventory, failed guard attempts and successful final checks.
This integrity paragraph is added after sealing to avoid self-reference and is
the only excluded delta.

## Priority 7 - Verify Matched Cost and Independent Acceptance

### [x] 7.1 Freeze final cost applicability and comparison scope

- [x] Separate tooling/instrumentation changes from runtime changes; verify exact
      unchanged-input reuse where justified, retaining coordinates and original flags.
- [x] Before new scoring, approve a frozen affected matrix and numerical budget under
      the same 3.3 rules. Use the existing 60-row matrix for shared default-path changes;
      this priority does not implicitly extend the earlier diagnostic budget.
      New scoring N/A on 2026-10-14: approved no production change; scored inputs unchanged.

### [x] 7.2 Execute applicable matched scoring and confirmation

- [x] Keep a Central-only `4.4.2` baseline, matched harness/non-starter dependencies,
      semantic witnesses and both artifact orders; retain every row/fork/interval.
      Exact saved-input reuse, not new measurement or fresh Central resolution.
- [x] Apply >20% latency and >max(32 B/op, 5%) allocation review triggers and reverse
      confirmation of every flag. Do not drop inconvenient rows or substitute diagnostics.

### [x] 7.3 Accept findings and final combination independently

- [x] Review A001/A002 individually and combined; unexplained required evidence stays
      blocking. Clear aggregates or forced modes are not blanket acceptance.
      Review completed with non-acceptance: both findings and their combination remain blocking.
- [x] Apply public-report promotion rules only to qualified scores. No public number
      is required; explicit scope exclusion is not an explanation or speedup claim.

**Priority 7 review (2026-10-14):** [Matched cost and acceptance](COST-ACCEPTANCE.md).
New scoring N/A under V36-P6-1; no runtime or scored-harness change exists.
The offline audit revalidates **1,816 files** and four archive hashes, **202
production/build/benchmark inputs**, 39 harness/build inputs, both ordered
121-JAR classpaths and all 24,224 shaded class entries. The saved Central-only
4.4.2 / 4.5.0-SNAPSHOT pair retains original source coordinates and limits.

Re-analysis exactly reproduces all **60 primary rows per artifact** and **four
reverse-confirmation rows per artifact**, including all samples and intervals:
zero primary latency flags, **three primary allocation flags**, zero reverse
flags. Every primary flag and the historical enabled-only row has reverse
confirmation. The 256 scored workers are reused V35 history, not new execution.
V36's 48/48 worker budget remains exhausted; no new worker/probe is launched.

A001/A002 remain **unresolved/blocking**, independently and combined. Completion
records the acceptance review and named unaccepted scope, **not a performance pass**,
scope exclusion, closure or release. E001 retains its limited new-capture
verification. No public performance number/report is promoted. Priorities 8-10
remain pending; V1-V35 and their original flags/closure decisions are unchanged.

Verification passes **115 documentation/readiness/link cases per Boot row**
(4.0.0/4.1.0), zero failures/errors/skips, and **83 Python tooling tests**.
Ordinary Java tests disable explicit GC. The initial missing-record guard
failure is preserved. `git diff --check` passes. No fresh full-reactor, API,
assembled-consumer, native or benchmark run is claimed. Commands/exits, input
hashes, full derived reviews and source patch are preserved under
`target/release-evidence/v36/priority7/` and copied to
`verification-evidence/v36-priority7/` before cleanup; the external seal follows.

The Priority 7 bundle seals **63 files**. Manifest SHA-256:
`c11a1e2854189d77e89757815c4f0e7c92c9da2094305dbb78f0836f2af76dfa`.
Archive `verification-evidence/v36-priority7-20261014.tar.gz` SHA-256:
`6ed0851e9fa0983a7258c06534540508fbe57aa0deb693cd4d35e433d2389516`.
Copied and archived contents were verified, including the original input-manifest
references, derived reviews, failed guard attempt, final tests and source patch.
This integrity paragraph is added after sealing to avoid self-reference and is
the only excluded delta. The four referenced historical bundles remain required
for raw samples and binaries; this smaller audit archive does not replace them.

## Priority 8 - Compatibility, Cross-Path and Lifecycle Verification

### [x] 8.1 Select and run delta-appropriate contract checks

- [x] For tooling/docs-only work, verify parser/workload/archive/readiness/guidance
      guards and exact applicability of reused runtime evidence; do not claim fresh native.
- [x] For runtime/fixture changes, run affected public/factory/mock/assembled contracts,
      genuine Boot 4.0.0/4.1.0 rows and physically absent optional integrations.
      Fresh executions N/A on 2026-10-14: no runtime/fixture change; exact prior inputs reverified.

### [x] 8.2 Verify ownership and runtime/AOT boundaries

- [x] Preserve metadata/properties selection, cancellation/discard, failed creation,
      shutdown and application-versus-starter resource ownership.
- [x] Use explicit GC disabled for ordinary tests, controlled reachability for
      collection claims, and deterministic concurrency/cleanup gates rather than sleeps.

### [x] 8.3 Verify applicable API, packaging and native evidence

- [x] For retained runtime changes, run strict root and independent starter source/binary
      API checks against isolated Central `4.4.2`; run both even if the first fails.
      New comparisons N/A on 2026-10-14: no retained runtime change; both strict results reused.
- [x] Verify applicable packaging/JVM/AOT/native paths. Native compile and executable
      must use clean reachable source containing final implementation and fixture.
- [x] Record actual totals/failures and exact reuse or dated N/A rationale for unchanged
      paths; JVM/AOT success is not a native pass, nor local install Central provenance.

**Priority 8 verification (2026-10-14):** [Compatibility and lifecycle record](VERIFICATION.md).
V36-P6-1 retains no production change. The audit rehashes **2,892 files** and
four archives, compares **392 unchanged runtime/build/test/fixture inputs** to
reachable P9 source, and reconciles historical XML, Boot parents, installed
artifact identities, API failure switches/reports and native binary/source/exits.
P9's 2,446 cases per Boot row, 29 selected-consumer/one absence/six smoke cases
per row, controlled 16/5/2 reachability cases and native result are **reused**,
not fresh executions. P10 strict root/independent starter and packaging evidence
is likewise reused; fresh execution is N/A for this tooling/docs-only delta.

Fresh checks pass **83 Python tooling tests**, **40 untimed workload cases per
Boot row** (4.0.0/4.1.0), the standalone recorder callback contract and synthetic
provenance fixtures. Explicit GC is disabled for ordinary Java checks. No JMH
runner, measured worker, internal probe or new experiment budget is used.
The first provenance-fixture run failed on a read-only default Maven cache;
the isolated rerun uses the recorded writable repository and passes. The first
offline audit's starter-report layout assumption is corrected without changing
or rerunning historical evidence. Both failures and the missing-record guard
failure are preserved. Current documentation/readiness guards are recorded below.

Final documentation/readiness/link guards pass **116 cases per Boot row**,
zero failures/errors/skips, giving **156 Java cases per row** with the workload
checks, plus the standalone callback contract. A heading-case assertion mismatch
in the first full documentation run is corrected and retained with its failed XML;
the final rerun passes. `git diff --check` passes. Exact commands/exits, current
source patch, failed attempts, actual totals and reused-evidence inventories are
preserved in `target/release-evidence/v36/priority8/` and copied to
`verification-evidence/v36-priority8/` before cleanup; the external seal follows.

The Priority 8 bundle seals **203 files**. Manifest SHA-256:
`8d69e9d56fffe322df1313b929743c6b272d584f4214922b9d97005ebf035e85`.
Archive `verification-evidence/v36-priority8-20261014.tar.gz` SHA-256:
`13f223432e297407b34a144d6fabfa8f4ff6e95dd5a36044fb510b117f7132f6`.
Copied and archived contents were verified, including fresh guards, failed
attempts, corrected reuse audit, original input-manifest references and source
patch. This paragraph is added after sealing to avoid self-reference and is the
only excluded delta. Referenced historical bundles remain required for raw
runtime/API/native evidence; the audit archive does not replace those binaries.

A001/A002 remain **unresolved/blocking**; correctness verification is not cost
acceptance, scope exclusion, closure or release. Priorities 9-10 remain pending.

## Priority 9 - Evidence Preservation and Maintainer Guidance

### [ ] 9.1 Preserve reproducible evidence outside build output

- [ ] Seal source or base-plus-patch, commands/exits, toolchains/dependencies, raw
      samples/reports and file/archive hashes; verify copied and archived contents.
- [ ] Test missing/corrupt/incomplete evidence rejection; preserve failed/interrupted
      attempts and original V35 artifacts. State local-bundle availability limits.

### [ ] 9.2 Reconcile findings, guidance and operating limits

- [ ] Document all three dispositions, owners, supported alternatives and reopening
      triggers; retain unresolved scope rather than silently deferring it.
- [ ] Separate diagnostics from public scores, allocation from retention/RSS and
      starter from application work. No snapshot-upgrade remedy or mesh diagnosis.

### [ ] 9.3 Verify current guidance and release-state boundaries

- [ ] Verify examples, local links, metadata/configuration, versions and readiness
      against actual evidence; distinguish candidate from published `4.4.2` behavior.
- [ ] Keep synthetic capture bounded/sanitized and V1-V35 history unchanged. Record
      missing evidence and any proposed reduction before requesting closure.

## Priority 10 - Conditional Release and Closure

### [ ] 10.1 Reconcile scope and obtain release intent

- [ ] Reconcile A001/A002/E001; obtain explicit reduced-scope approval or keep the
      roadmap open if required evidence remains unresolved. No-release alone clears nothing.
- [ ] Obtain release preparation or review-only/no-release approval. Evaluate a
      compatible patch first if a justified change remains; no implicit `4.5.0` selection.

### [ ] 10.2 Select exact candidate or no-release branch

- [ ] For release, approve exact version and update reactor/modules/fixtures,
      guards/current docs/readiness together, keeping historical evidence coordinates.
- [ ] Keep baseline `4.4.2` until verified publication and V36 active through any
      final-version cut. Preparation is neither publication nor final release GO.
- [ ] For no release, record delivered/rolled-back/unresolved scope and explicit
      exclusions; label release-only gates N/A, not passed.

### [ ] 10.3 Assemble immutable final evidence

- [ ] Inventory reachable final source, decisions, findings, tests, cost, API,
      ownership, Boot/consumer/AOT/native and guidance with applicability/reuse limits.
- [ ] Seal exact commands/statuses/counts, dependencies, reports/artifact hashes and
      failures; revalidate after final input edits without claiming a dirty patch is a commit.
- [ ] For release, verify final-coordinate packaging and applicable unsigned/staged
      checks separately from signing, tag/workflow, publication and Central consumption.

### [ ] 10.4 Verify publication or explicit no-release closure

- [ ] For release, verify signing/signatures, version-matched tag/source/workflow,
      publication and isolated Central assembled consumption before advancing baselines.
- [ ] For no release, record approved final disposition/reduced scope; unrun release
      gates are N/A, not passes, and unresolved findings remain named.
- [ ] Close roadmap/checklist/index/readiness together with source/evidence links;
      leave future scope unselected and V35 closed unchanged.

## Completion Criteria

- [ ] A001, A002 and E001 have evidence-backed dispositions; unresolved scope is
      explicitly excluded or still blocking, not silently cleared.
- [ ] Fork/trace association and relevant compiler-history limits are verified.
- [ ] All measurement has prior approval, frozen budgets and preserved stop rules;
      no diagnostic-score substitution, favorable-rerun selection or unapproved extension.
- [ ] Any retained correction has approval, semantic/ownership/compatibility controls
      and matched benefit or accepted correctness cost; failed candidates remain rejected.
- [ ] Final evidence identifies actual inputs, results, failures and reuse limits;
      lost recordings and V1-V35 history are not rewritten.
- [ ] Guidance, release/no-release decision and lifecycle closure match actual delivered
      behavior without a blanket performance or deployment-memory claim.

All boxes are execution gates, not adoption accomplishments. No experiment,
production implementation or release is selected by creating this checklist.
