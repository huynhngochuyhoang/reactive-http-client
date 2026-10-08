# Reactive HTTP Client - Roadmap V35 Execution Checklist

> **Status:** active
> **Theme:** resolve V34 deferred performance and default-path findings
> **Published baseline:** `4.4.2`
> **Development coordinate:** `4.5.0-SNAPSHOT`
> **Investigation scope:** all V34-C001 through V34-C005 and the unresolved P3/P10 allocation finding
> **Implementation authorization:** seven-row bounded plan approved in Priority 3.3; no production change delivered
> **Release scope:** unselected
> **Adopted:** 2026-10-04

Execution companion to [ROADMAP.md](ROADMAP.md). Adoption authorizes the baseline,
reproduction and bounded fix-design work, not production edits, a version change,
a release or completion of any checkbox. V34 remains closed review-only; V1-V34
records and sealed results are history, not rewritten V35 evidence.

Execute priorities in order and record dependency-based reordering. Independent
correctness or ownership controls may proceed while allocation attribution is
pending, but the unresolved split cannot justify an optimization. Production edits
require explicit maintainer approval in **Priority 3.3**. All workstreams remain
in scope; approval of one patch does not silently remove the others.

## Finding Coverage and Completion Rules

| Required workstream | Primary execution owner | Current disposition |
|---|---|---|
| V34-P3/P10 allocation finding | Priority 2; performance maintainer | Resolved without production change for the observed split; not a performance pass |
| V34-C001 | Priority 4; observer/hook maintainer | Resolved without production change; bounded candidate rolled back, not a performance pass |
| V34-C002 | Priority 5; invocation/body maintainer | Resolved without production change; ownership reviewed and null-holder candidate rolled back, not a performance pass |
| V34-C003 | Priority 6; planning/resolver maintainer | Pending attribution and fix/no-change evidence |
| V34-C004 optional preparation | Priority 7.1-7.2; handler/factory maintainer | Pending acquisition/lifetime evidence |
| V34-C004 rolled-back value reuse | Priority 7.3; effective-policy maintainer | Rolled back in V34; new evidence required |
| V34-C005 | Priority 8; factory/AOT maintainer | Pending cold/runtime lifecycle evidence |

All seven rows are mandatory. Maintain a ledger with owner, reproduction, approved
boundary, affected profiles, patch or no-change evidence, acceptance and remaining
limitations. At completion use one of these explicit dispositions:

- **Fixed and verified:** reproduced need, approved bounded correction, semantic
  and ownership controls, matched benefit or approved correctness cost.
- **Resolved without production change:** evidence establishes intentional work,
  disproves a suspected defect or validates a measurement explanation. Repeating
  V34's deferral, a failed candidate alone or lack of investigation is insufficient.
- **Unresolved/blocking:** preserve owner, missing evidence and next experiment.
  It is not fixed and cannot satisfy full-scope completion. Further deferral needs
  explicit maintainer scope reduction; closure must identify that reduced scope.

Check a box only after execution, verification and disposition are recorded.
Implementation-only substeps may be N/A for a substantiated no-change outcome;
label the dated reason and evidence beside the box. Neither N/A nor a no-release
decision can silently turn an unresolved finding into a completed investigation.

## Evidence Rules

- Record exact commands/exits, actual totals, toolchain/settings, reachable source,
  clean/dirty state, resolved dependencies/classpaths and artifact/report hashes
  under `target/release-evidence/v35/priority<N>/`. Preserve failed/partial attempts.
- Keep durable conclusions and reproduction commands in tracked V35 records.
  Ignored bundles are not source history; unavailable pre-squash revisions require
  a reachable provenance reconciliation or reproduction, not a claimed pass.
- Separate source inspection, untimed correctness, sampled attribution, scored
  cost, retention, JVM AOT, native execution and published consumption. Never add
  overlapping runs to distinct test totals or infer deployment RSS from B/op.
- Freeze semantic workloads and measurement/stop rules before scoring. Keep all
  forks/intervals and both artifact orders. No favorable-rerun selection, threshold
  reset, profiler-derived release score or missing-row pass.
- Clean-source native and final cost evidence must contain the final implementation
  and fixture. Changed inputs require affected reruns; exact unchanged-input reuse
  must state source identity and narrower limits, not claim a fresh execution.
- Keep ordinary tests deterministic with explicit GC disabled. Use controlled
  reachability JVMs for collection claims; use gates and cleanup acknowledgements
  instead of sleeps for concurrency. Keep instrumentation out of scored hot paths.
- Keep production payloads, request targets, headers, identities, credentials, cache
  keys and arbitrary error text out of evidence. Use bounded synthetic capture.

## Execution Gates

| Gate | Requirement |
|---|---|
| Priority 1 | Revalidate post-V34 baseline, all seven findings and effective profiles |
| Priority 2 | Freeze and execute allocation attribution; block affected benefit claims if unexplained |
| Priority 3.3 | Approve concrete bounded fixes and per-change acceptance before production edits |
| Priorities 4-8 | Address each C001-C005 boundary, including both C004 subrows; verify or justify no change |
| Priorities 9-10 | Establish final cross-path, ownership, API and matched cost acceptance |
| Priority 11 | Reconcile every finding; unresolved work cannot become a silent deferral |
| Priority 12 | Explicit release/no-release and scope decision, corresponding evidence, then closure |

No new API/SPI, feature switch, module, dependency/default upgrade, scheduler/pool
tuning, cache/retry behavior, telemetry export or broad handler/AOT rewrite is
selected. Required discovery, mutation validation, authorization, isolation,
terminal reporting and body/resource cleanup cannot be removed for a score.

## Intended Records

Create records as work produces evidence, not empty reports or implied results.
Linked records exist; other names are suggestions, not implied completed reports.

| Record | Contents |
|---|---|
| [BASELINE-SCOPE.md](BASELINE-SCOPE.md) | Reachable/published provenance, profiles and prior-evidence applicability |
| [FINDINGS.md](FINDINGS.md) | Complete seven-row ledger, owners, acceptance and unresolved dispositions |
| [ALLOCATION-INVESTIGATION.md](ALLOCATION-INVESTIGATION.md) | Frozen experiments, all samples, attribution and remaining limits |
| [FIX-DECISION.md](FIX-DECISION.md) | Approved seven-row plan, acceptance/rollback and scope exclusions |
| `FIX-EVIDENCE.md` | Per-finding implementation/no-change evidence, ownership and costs |
| `VERIFICATION.md` | Mock/consumer/Boot/AOT/native/API and final matched evidence |
| `MAINTAINER-GUIDANCE.md` | Retained costs, reproducible investigations and operations limits |
| `RELEASE-DECISION.md` | Exact scope, release/no-release choice, immutable evidence and closure |

## Priority 1 - Post-V34 Baseline and Complete Deferred-Scope Integrity

### [x] 1.1 Verify adoption and version state

- [x] Verify roadmap/checklist/index/readiness agree that V35 is active, V34 is
      closed review-only and no production fix or release has been selected.
- [x] Verify reactor/modules/current fixtures remain `4.5.0-SNAPSHOT`, public/API/
      consumer/benchmark baselines remain `4.4.2`, and `plannedFinalVersion=null`.
- [x] Verify readiness follows checklist lifecycle through a final-version cut,
      not the snapshot suffix; run applicable archive/version/documentation guards.

### [x] 1.2 Establish reachable source and reusable provenance

- [x] Record clean/dirty state, reachable commit/tree, toolchains/settings and
      effective dependencies for Java 21 and genuine Boot 4.0.0/4.1.0 rows.
- [x] Revalidate Central `4.4.2` provenance and existing consumer/native/API/cost
      evidence, or reproduce unavailable/changed inputs in isolated repositories.
      A reactor install cannot substitute for a published artifact.
- [x] Preserve V34's rollback, unexplained flag and all V1-V34 history. Reconcile
      pre-squash references explicitly; do not replace them with invented provenance.

### [x] 1.3 Initialize complete finding and profile coverage

- [x] Create all seven ledger rows with owner, current evidence, reproduction,
      proposed boundary, controls, acceptance and unresolved questions.
- [x] Retain minimal/public, real auto-configured no-registry/registry, enabled-only
      resilience, application observer/hook, physically absent optional integration,
      independent pool gauge and selected-cache/work profiles.
- [x] Map reusable V34 structural/ownership tests to each row. Configuration flags
      alone do not prove effective behavior, inactive resources or zero work.

Completed 2026-10-04. [BASELINE-SCOPE.md](BASELINE-SCOPE.md) records the clean
starting commit `05dfbaaa86e7f9afbdf88315ba212db7d65f0bb6`, reachable reconciliation
of V34's pre-squash references and exact unchanged-input reuse. All 3,051 entries
across six prior inventories rehash; published artifacts remain Central-derived.
[FINDINGS.md](FINDINGS.md) initializes seven unresolved rows and maps all eight
effective profiles and existing controls. No production change, finding resolution,
new score, release or version transition is approved. Priority 2 attribution and
Priority 3.3 implementation approval remain open.

Verification: reactor validation, published-baseline fixtures, strict API negative
fixtures, script syntax and fresh Boot 4.0 dependency inventory passed. The final
focused run passed **263 tests across 12 classes** (94 documentation and 169
profile/ownership cases), zero failures/errors/skips, with explicit GC disabled.
The earlier 169-case profile run overlaps, not an additional distinct total.
The new documentation guard first failed with the missing record as intended;
the initial fixture-script failure and corrected writable-repository run are
retained. Final documentation-only rerun: **94 passed**, overlapping the 263.
Prior native/API/consumer/cost results are revalidated reuse, not new executions.
Evidence: `target/release-evidence/v35/priority1/`; source copies, exact commands,
exits, XML, readiness and hashes are sealed separately from this external anchor.

Integrity anchor: **262 files**; SHA-256 of `priority1/SHA256SUMS`:
`5119e62d287d19cc7b1068fbc89cb329ae4d2dfddab65187023a9e2311907e62`.
The sealed checklist copy excludes this anchor to avoid a self-referential hash.

## Priority 2 - Explain the Unresolved Allocation Split

### [x] 2.1 Freeze distinguishing experiments before scoring

- [x] State hypotheses, distinguishing observations, bounded fork/run count and
      order, stop rules and acceptance before inspecting new scores.
- [x] Include historical enabled-only GET publisher assembly plus minimal/registry
      sentinels. Keep phase/setup/teardown and exact workload semantics explicit.
- [x] Freeze VM flags, heap, warmup, iterations, threads, profiler use and host
      constraints; keep V34's >20% latency and >max(32 B/op, 5%) review triggers.

### [x] 2.2 Reproduce matched and same-artifact variability

- [x] Audit harness bytecode, artifact metadata, parameters, classpath order,
      loaded classes, VM options and starter/non-starter identity/provenance.
- [x] Compare repeated forks of the same saved JAR with baseline/current pairs
      in both orders. Retain contrary forks, intervals and unsuccessful attempts.
- [x] Record CPU/quota/pressure/memory limits; do not run competing builds or
      diagnostic profilers alongside scored comparisons. Do not run until green.

### [x] 2.3 Test allocation explanations independently

- [x] Collect supported compiler/inlining/escape-analysis or allocation-site
      diagnostics separately; record unavailable tooling and profiler perturbation.
- [x] Change one factor at a time to test a proposed cause. Sampled stacks or
      byte-identical classes alone cannot explain the fork split.
- [x] If the harness/VM is causal, validate any measurement correction on both
      artifacts and preserve old samples. If production is causal, route the
      bounded fix through Priority 3.3; diagnostic flags are not runtime defaults.

### [x] 2.4 Record causal evidence or the unresolved gate

- [x] Distinguish a validated explanation from a plausible hypothesis. Preserve
      the historical 1,136/1,392 fork split and why a favorable pair cannot clear it.
- [x] Record representative confirmation and implications for each affected
      acceptance row; do not promote diagnostic timings as scored benefit.
- [x] If attribution remains unresolved at the stop boundary, keep the finding
      blocking and obtain explicit next scope. This gate cannot be completed as
      resolved merely because experiments ran; no silent threshold relaxation.

Completed 2026-10-07. [ALLOCATION-INVESTIGATION.md](ALLOCATION-INVESTIGATION.md)
preserves the frozen plan and all 46 forks from saved, byte-matched artifacts.
The 1,136/1,392 B/op modes reproduced on both artifacts. C2 traces connect the
high mode to unavailable Selection-constructor signature classes and lack of
Selection elimination; one-factor inlining controls corroborate the boundary.
The allocation-mechanism finding is resolved without production change, **not a
performance pass**. Reverse comparison still records one allocation review flag.
No production, dependency, default, version or release change is selected.

The 2.3 correction branch is N/A: no measurement correction is adopted; all
interventions remain diagnostic-only. The 2.4 unresolved-scope branch is N/A for
this explained mechanism, not waived because experiments ran. Six implementation
workstreams remain unresolved and Priority 3.3 approval is still open. Exact
commands, raw samples/intervals, compiler/class-load logs, privacy-filtered JFR,
failures and source/input hashes remain under `target/release-evidence/v35/priority2/`.

Verification: **166 starter cases across five classes** (95 documentation and
71 policy/ownership cases), **40 benchmark contract cases**, and **22 Python
checks** (eight investigation/reviewer and 14 existing V34 checks) passed with
zero failures/errors/skips. Maven correctness runs disabled explicit GC; no unit
test requires a particular compiler mode or measured allocation value. The
documentation-only final rerun passed **95 cases**, overlapping the 166 above.
No fresh native, API, Central-consumer or full performance-matrix run is claimed.
Source copies, exact commands/exits, XML, raw samples, analysis and readiness are
sealed separately from the checklist's external integrity anchor.

Priority 2 integrity anchor: **254 files**; SHA-256 of `priority2/SHA256SUMS`:
`c7b4c0b68eaa3b7e7db0d197d0b63527ef0dd161d3befb149136b5b91745747f`.
The sealed checklist copy excludes this anchor to avoid a self-referential hash.

## Priority 3 - Specify Bounded Fixes for Every Workstream

### [x] 3.1 Specify reproduced need and the smallest correction

- [x] For every inventory row, identify unnecessary work/defect or intentional
      cost, affected profiles/phases, source owner and local candidate boundary.
- [x] Map semantic, extension, optional-loading and lifetime dependencies; name
      cross-cutting effects instead of hiding them in a combined improvement.
- [x] Compare no change with each candidate. C004 value reuse requires new
      evidence, not reinstating the failed V34 patch under a new label.

### [x] 3.2 Define acceptance and rollback per candidate

- [x] Freeze untimed regression witnesses, matched cost selection, expected
      benefit or reproduced hardening, attribution limits and rollback criteria.
- [x] Preserve discovery/materialization timing, full policy mutation checks,
      body/context/terminal ownership, coldness and selected-feature behavior.
- [x] Keep patches independently reviewable and reversible. Specify retention
      checks so reduced allocation cannot trade for longer request lifetimes.

### [x] 3.3 Obtain explicit bounded implementation approval

- [x] Record the maintainer's concrete approved plans before production edits;
      adoption and a reproduction alone are not implementation approval.
- [x] Cover all seven workstreams in the decision. Approval for one row does not
      silently defer the others; scope reduction requires an explicit decision.
- [x] Record rejected/no-change alternatives with supporting evidence. A need
      outside the roadmap's compatibility/default boundaries stops that change
      for separate scope approval; it cannot enter by implication.

Completed 2026-10-07. [FIX-DECISION.md](FIX-DECISION.md) covers every ledger row
with source-supported need/limits, local owners, no-change alternatives,
semantic/extension/lifetime dependencies, untimed witnesses, matched cost rows
and independent acceptance/rollback rules. It preserves P2's explanation without
a performance pass, all six open implementation workstreams, and V34's rollback.
The maintainer explicitly selected "Approve the seven-row plan (Recommended)".
This supersedes the earlier P1/P2 pending-approval state, not their measurements.
Only the documented candidate boundaries and evidence-backed no-change routes are
approved for later execution; no finding is silently dropped or marked fixed.
No production edits or release/version decision are made by this priority.

Verification: **238 tests across 11 classes** passed, zero failures/errors/skips,
with explicit GC disabled: 96 documentation/archive/readiness cases and 142
existing contract/ownership controls. The earlier pre-approval run overlaps this
total. The new specification guard first failed with the missing decision record
as intended. P2's 254-file inventory rehashes unchanged; it is reused attribution,
not new scoring. Production, benchmark, module coordinates and V1-V34 records
remain unchanged. Commands, XML, proposal/approval provenance, source copies and
readiness are retained under `target/release-evidence/v35/priority3/`.
The final documentation-only rerun passed **96 cases**, overlapping the 238.
The sealed source copy excludes the external integrity anchor below.

Priority 3 integrity anchor: **49 files**; SHA-256 of `priority3/SHA256SUMS`:
`286c77f2cccc18f10f492c6d3ab8bc2a7e704eef7fef48c0a267d53d357dd665`.

## Priority 4 - C001 - Dynamic Discovery and Composition Cost

### [x] 4.1 Isolate provider and composition work

- [x] Separate provider lookup, streams/lists, empty fallback and composite work
      across empty/single/multiple/ordered/prototype consumers and custom providers.
- [x] Record invocation-time capture versus publisher resubscription, per-client
      support checks and provider result/failure behavior before changing code.
- [x] Attribute actual removable cost; helper totals cannot stand in for complete
      factory/proxy calls or justify caching provider results globally.

### [x] 4.2 Implement the approved local reduction or prove no change

- [x] Implement only Priority 3.3's boundary, preserving late registrations,
      materialization/order/support timing and custom-provider failures/results.
- [x] Do not equate an empty ordered stream with every other provider operation;
      no permanent negative lookup cache, forced singleton or skipped callback.
- [x] If no change is justified, retain measured/structural evidence of required
      work and rejected alternatives, not just the previous deferral.

### [x] 4.3 Verify dynamic behavior and matched cost

- [x] Test empty-to-present registration, multiple ordered/prototype consumers,
      per-client support, concurrent first calls and new versus existing publishers.
- [x] Preserve no-registry and master-off application observers/hooks plus
      independent pool telemetry; absent exports do not mean absent behavior.
- [x] Compare approved production-path rows and helpers, record retained ownership,
      roll back failed candidates and update C001's ledger disposition.

Completed 2026-10-08. [DISCOVERY-COMPOSITION.md](DISCOVERY-COMPOSITION.md) preserves
the frozen plan, local/provider allocation attribution, custom-provider and
dynamic capture contracts, and **224 scored forks** plus two separate diagnostic
forks. All eight scored stages completed without retries. Reverse HOOK/GET has
one allocation review flag; multiple-hook mean allocation increases in both
orders. The candidate was **rolled back** under the frozen stop rule. C001 is
**Resolved without production change**, not a performance pass or a claim that
all provider cost is irreducible. The original production source is restored;
the rejected patch remains an explicitly labeled experiment artifact.

The final restored-source suite passes **204 cases across eight classes**:
107 contract/ownership cases (including 36 discovery/ownership cases, 21 new)
and 97 documentation cases, zero failures/errors/skips. Earlier baseline,
candidate and restored-control runs overlap this total. The candidate benchmark
correctness suite passed **40 cases**; **26 Python checks** passed. The final
documentation-only rerun passed **97 cases**, overlapping the 204 above.
Tests disable explicit GC; no timing/allocation/collection assertion is introduced.
Source/classpath audit matches 39 harness files and
120 non-starter dependencies; only handler implementation classes changed in the
candidate. Raw samples, intervals, commands/exits, host snapshots, candidate
source and input hashes are retained under `target/release-evidence/v35/priority4/`.
Five implementation workstreams remain open; release scope stays unselected.
No fresh native, API, Central-consumer or full-matrix performance pass is claimed.
The restored local Maven artifact's **310 classes** match the saved baseline;
the rejected candidate is no longer installed. P2/P3 sealed inventories rehash
unchanged. Final sources, readiness, analysis and commands are sealed separately
from the external integrity anchor; the sealed checklist copy excludes the anchor.

Priority 4 integrity anchor: **187 files**; SHA-256 of `priority4/SHA256SUMS`:
`c07ef8241405cf725f2b3f3364873c0e955a02d4286e9df7350927439c56b174`.

## Priority 5 - C002 - Body and Reporting-State Ownership

### [x] 5.1 Characterize body-owner and caller-state lifetimes

- [x] Attribute invocation-wide body-owner work separately from subscription-local
      reporting state. Enumerate null/immutable/non-owning and resource inputs.
- [x] Identify state consumers for auth, operators, generated idempotency, deadlines,
      logger/observer/hooks, ordinary callers, shared loads and refreshes.
- [x] Keep enabled-only resilience distinct; absent exports do not prove that
      current state is unnecessary or authorize changing its observable behavior.

### [x] 5.2 Apply only approved ownership-preserving changes

- [x] Reduce proven unused work without pooling caller state, retaining requests
      globally, reusing one-shot bodies or assuming arbitrary DTOs are non-owning.
- [x] Preserve stream/reader/channel/DataBuffer/multipart and inner-body ownership,
      replayable repeated subscriptions, backpressure, coldness and tenant isolation.
- [x] Record a substantiated no-change outcome where appropriate; failed benefit
      alone does not resolve uninvestigated ownership or reporting questions.

### [x] 5.3 Verify cleanup, terminal semantics and retention

- [x] Cover success/error/empty completion, serialization/decode failure, admission
      rejection, cancellation, buffered and late-arriving discard, and concurrent
      caller termination with deterministic cleanup acknowledgements.
- [x] Preserve one terminal event, attempt/final-request facts, timeout attribution,
      cache outcomes and health exclusions for every consumer that remains selected.
- [x] Pair matched cost with controlled reachability where making collection claims;
      no System.gc-dependent ordinary tests. Record C002 acceptance or rollback.

Completed 2026-10-09. [BODY-REPORTING-OWNERSHIP.md](BODY-REPORTING-OWNERSHIP.md)
preserves the frozen plan, body-shape and state-consumer inventory, allocation
attribution and **384 scored forks** plus two separate diagnostic forks. All
eight stages completed without retries. Forward review has three latency and two
allocation flags; reverse has zero latency and two allocation flags. The observed
40 B/op minimal warm-publisher reduction does not waive the adverse controls.
The null-body candidate was **rolled back**; C002 is **Resolved without production
change**, not a performance pass. Original production source is restored, with
the rejected patch tracked as an experiment artifact only.

Baseline ownership verification passed **425 cases across 25 classes**. Six new
null-owner cases first failed on baseline, then the candidate passed **431 cases**.
After rollback the retained cases characterize invocation allocation versus
independent subscription state; the restored ownership suite also passed **431**.
Candidate benchmark correctness passed **40 cases**. These runs overlap. Explicit GC is disabled;
release/discard counters and cleanup acknowledgements are not collection claims.
Controlled reachability is N/A because no new collection/heap/RSS claim is made.

Final verification passed **542 cases across 27 classes**, including **98**
documentation cases and 13 idempotency cases. The first final run's documentation
phrase mismatch is retained under `tests-final/`; the complete corrected rerun is
`tests-final-corrected/`, with zero failures, errors or skips. All **30 Python
checks** passed. Restoring the local starter and rebuilding the benchmark passed
**40 benchmark-contract cases** again; 310 starter classes and all 24,224 shaded
benchmark classes match the saved baseline byte-for-byte. The rejected candidate
is retained only in the evidence bundle, not the installed/local benchmark artifact.

The matched audit preserves 39 harness files, 120 non-starter dependencies and
classpath order; only the candidate handler class differs. Commands/exits, raw
samples/intervals, host snapshots, input/source hashes, candidate and final source
are retained under `target/release-evidence/v35/priority5/`. No fresh native, strict
API, assembled consumer or full 60-row performance pass is claimed. Four
implementation workstreams remain open and release scope stays unselected.

Final documentation-only verification passed **98 cases** again after reconciling
the finding index. The P5 bundle seals **308 files**; `SHA256SUMS` SHA-256 is
`fc9cedcadd057f52bf3de9cac2295b7b34cd66ff6b5ed31affcf867adaf5a895`.
P2/P3/P4 inventories were reverified unchanged. `closure/` records the final
source/tests/readiness audit; source copies precede this external integrity anchor
to avoid a self-referential checksum. These overlapping documentation reruns do
not increase the 542-case total.

## Priority 6 - C003 - Static Planning and Dynamic Request Projection

### [ ] 6.1 Separate invariant derivation from caller inputs

- [ ] Inspect repeated interface logging-annotation traversal and static plan work
      through existing metadata/plan owners; identify actual removable derivation.
- [ ] Preserve public fresh/replacement metadata, inherited generics, API-ref
      precedence, interface identity and per-invocation selection behavior.
- [ ] Identify aliasing/mutation dependencies before removing collection copies;
      ordinary mutable arguments are not implicitly deep-snapshotted.

### [ ] 6.2 Implement the approved static-work reduction

- [ ] Reuse only proven invariant data at existing plan/handler boundaries. No
      second metadata model, pre-serialized body or per-caller resolved-request cache.
- [ ] Preserve dynamic URI/query/header projection, charset, body presence,
      idempotency/context and custom codec behavior at their original boundaries.
- [ ] Document evidence-backed no-change alternatives; reject hidden retention of
      arguments, identities, bodies or Reactor context in reusable plans.

### [ ] 6.3 Verify exact request behavior and phase-specific cost

- [ ] Assert dispatch method/target/headers/body/result for public, factory and mock
      paths, including case aliases, order, null/empty, generic and API-ref cases.
- [ ] Keep first-call, warm assembly, subscription and loopback measurements
      distinct; correctness spies must not enter the scored path.
- [ ] Record matched benefit/ownership acceptance or rollback and C003 disposition.

## Priority 7 - C004 - Unselected Feature Preparation and Resources

### [ ] 7.1 Inventory optional preparation and actual acquisition

- [ ] Attribute cache identity WebClient construction, scheduler access and manager
      lifetime separately for factory/static-create and legacy public constructors.
- [ ] Distinguish dormant holder/shared reference from worker, connection, cache,
      meter lease and scheduled refresh; classpath presence is not acquisition.
- [ ] Count behavior on selected/unselected and physically absent optional paths
      without changing validation timing or forcing lazy diagnostic materialization.

### [ ] 7.2 Apply approved preparation or ownership improvements

- [ ] Elide/defer only demonstrated unnecessary work permitted by effective policy
      and complete mutation guards. Keep supported public/factory/mock entry points.
- [ ] Preserve selected-cache finalized-request probes through all required filters,
      auth/tenant/key validation and per-call gates, including cache hits.
- [ ] Retain caller/load/refresh admission, coalescing/deadline ownership, byte/entry
      bounds, response eligibility, publication checks and same-tag meter ownership.
- [ ] Record substantiated no-change outcomes for each preparation boundary;
      laziness cannot move startup failures, skip checks or revive closed resources.

### [ ] 7.3 Re-evaluate rolled-back value reuse separately

- [ ] Preserve the V34 failed experiment and first seek new attributed benefit
      distinguishable from fork noise; fewer source constructors are not proof.
- [ ] Reopen implementation only under the explicit Priority 3.3 approval. Keep
      disabled sources distinct and selected/invalid decisions unshared as required.
- [ ] Retain every whole-interface mutation check, including sibling methods and
      disabled-to-selected changes. Record independent acceptance/rollback or an
      evidence-backed no-change conclusion; this cannot replace 7.1-7.2 work.

### [ ] 7.4 Verify optional absence, races and lifetime boundaries

- [ ] Gate concurrent first use, cancellation, failed construction and close races;
      prove no unintended acquisition, continuation or publication after closure.
- [ ] Verify optional classes physically absent, selected-feature behavior,
      no-registry terminal consumers and diagnostics' supported unknown states.
- [ ] Compare cold plus first-use cost and lifetime, retain application/shared
      ownership, and update both C004 ledger rows with distinct evidence.

## Priority 8 - C005 - Construction, AOT and Runtime Lifecycle Cost

### [ ] 8.1 Attribute cold, first-use and runtime work

- [ ] Separate context/proxy creation, first invocation/subscription and warm calls;
      identify removable static work and exact owners before proposing caches.
- [ ] Verify AOT-only bookkeeping stops at the proper runtime boundary and does
      not retain per-instance/request state or add warm-path tracking.
- [ ] Require new reproduction before broadening V33's framework-selection review;
      cold fixture allocation alone does not establish a defect or optimization.

### [ ] 8.2 Implement only the approved lifecycle correction

- [ ] Preserve properties/metadata preference, parent/scoped/FactoryBean selection,
      awareness/binding/initialization order and post-processor restoration.
- [ ] Keep selected failures and non-eager unrelated business-bean behavior; do
      not substitute defaults when supported application replacements exist.
- [ ] Retain bounded AOT tracking and non-instantiating diagnostics. Document a
      justified no-change outcome where required work cannot safely be reduced.

### [ ] 8.3 Verify recreation, failure cleanup and total cost

- [ ] Test context recreation, class-loader isolation, partial creation failure,
      scoped/prototype products and destruction without resurrecting removed owners.
- [ ] Compare construction plus first-use and warm cost together; merely moving
      work into subscription cannot count as an overall improvement.
- [ ] Verify resource/retention and runtime/AOT parity, then record C005 acceptance
      or rollback with evidence and any remaining limitation.

## Priority 9 - Cross-Path, Optional-Integration and Lifecycle Verification

### [ ] 9.1 Run final public, factory and mock regressions

- [ ] Run affected contract/ownership tests and complete ordinary module suites
      against final combined changes; retain exact totals and failed attempts.
- [ ] Cover body/context/terminal behavior, late extensions, mutable selection,
      cache/auth/retry/redirect/deadline composition and caller/load distinctions.
- [ ] Keep ordinary tests GC-independent; run controlled reachability only for
      actual collection claims and do not relabel structural tests as collection.

### [ ] 9.2 Verify assembled consumers and optional boundaries

- [ ] Run artifact-based consumers with genuine Boot 4.0.0 and 4.1.0 parents,
      matching runtime stacks; no reactor output masquerading as assembled JARs.
- [ ] Exercise physical optional absence and selected auth/resilience/cache/metrics
      controls. Count all server dispatches and assert actual HTTP methods/results.
- [ ] Use real API names/reporting paths for metered cases and distinguish caller,
      attempt, transport, coalesced and hidden-refresh evidence.

### [ ] 9.3 Verify affected AOT and native execution

- [ ] Run final runtime/AOT selection, hints and JVM smoke on applicable Boot rows.
      Track overlay generation/reproduction commands; ignored files alone are not enough.
- [ ] Compile and execute the native smoke from clean reachable final source,
      recording toolchain, source/tree, limits, logs, exits and binary hash.
- [ ] Reconcile exact unchanged-input reuse explicitly; changed production/fixtures
      require affected reruns. JVM/AOT success and a failed compile are not native passes.

### [ ] 9.4 Recheck final resource and shutdown ownership

- [ ] Verify failed creation, final cleanup, context/same-tag recreation and
      diagnostics without lazy-resource creation, including selected work limits.
- [ ] Preserve application-owned connectors/registries/SDKs/shared schedulers.
      Distinguish independent caller-owned loads from manager-owned shared/refresh work.
- [ ] Record cancellation/discard acknowledgements, bounded retention and post-close
      behavior without assuming removed meters still expose shutdown counter deltas.

## Priority 10 - Final Matched Cost and Compatibility Acceptance

### [ ] 10.1 Run final matched workloads and confirmation

- [ ] Use all 60 matched primary rows from V34 plus justified additions frozen
      before scoring. Keep source/JAR hashes, isolated Central `4.4.2` provenance,
      identical harness/non-starter dependencies and untimed semantic witnesses.
- [ ] Retain all samples/intervals and confirm every flagged row plus the historical
      enabled-only row in reversed order, with Priority 2 same-JAR controls as needed.
- [ ] Apply the frozen >20% latency and >max(32 B/op, 5%) allocation review rules;
      a zero command exit or complete row inventory is not performance acceptance.
- [ ] Do not run competing builds/profilers alongside scored runs. State host/JVM
      limits and missing evidence; no best-fork selection or diagnostic-score substitution.

### [ ] 10.2 Accept individual fixes and the final combination

- [ ] Require repeatable attributed benefit under equivalent behavior for each
      optimization, or reproduced correctness benefit with honest approved cost.
- [ ] Roll back failed candidates independently and reverify the combined final
      artifact and no-change controls; one gain cannot hide another unaccepted regression.
- [ ] Keep unexplained allocation or required acceptance blocking. Explicit scope
      reduction cannot become a blanket performance all-clear or public speedup.
- [ ] Follow existing report-promotion rules for any public number; no quantitative
      claim is required and allocation reduction is not a deployment-memory claim.

### [ ] 10.3 Verify compatibility, packaging and evidence integrity

- [ ] Run strict root and independent starter source/binary API comparisons against
      Central `4.4.2`, with separate repositories, reports, provenance and exits.
      Run the second comparison even if the first fails; preserve both outcomes.
- [ ] Reconcile behavior/default/extension/ownership compatibility beyond API tools,
      supported Boot/consumer/AOT/native rows, metadata/docs and artifact packaging.
- [ ] Seal commands, actual totals, dependencies, samples/reports, artifact hashes
      and final source. Later input changes require affected reruns or precise reuse limits.

## Priority 11 - Complete Findings and Maintainer Guidance

### [ ] 11.1 Reconcile every finding against final evidence

- [ ] Reconcile all seven ledger rows to patches or evidence-backed no-change
      conclusions, preserving owners, controls, costs and limitations.
- [ ] Identify unresolved/blocking work and obtain explicit scope reduction if
      proposed. Repeated deferral is not resolution and must not be marked fixed.
- [ ] Separate validated new explanations from V34's historical observations;
      do not rewrite failed experiments or unaccepted measurements into past passes.

### [ ] 11.2 Document retained semantics and reproducible investigation

- [ ] Update relevant performance/customizer/context/lifecycle/operations guidance
      with actual delivered behavior, intentional costs and supported alternatives.
- [ ] Publish tracked reproduction commands and bounded diagnostic methods; separate
      profiling overhead, allocation, retention/direct memory and RSS.
- [ ] Keep capture synthetic and sanitized; no production payload/identity/key
      collection, automatic mesh diagnosis or snapshot-upgrade remedy is implied.

### [ ] 11.3 Verify guidance and remaining limits

- [ ] Verify examples, local links, current configuration/metadata, version state
      and generated readiness using actual test totals and source applicability.
- [ ] Distinguish candidate behavior from published `4.4.2` and preserve V1-V34
      history; no new feature/default or universal zero-overhead promise.
- [ ] Record owners, workarounds, reopening triggers and explicit missing evidence
      for any reduced scope before requesting the Priority 12 decision.

## Priority 12 - Conditional Release and Closure

### [ ] 12.1 Reconcile scope and obtain release intent

- [ ] Match every required row to accepted fixes/no-change outcomes or explicitly
      unresolved scope. Obtain maintainer approval for any reduced scope; do not
      present partial resolution as fixing all V34 deferrals.
- [ ] Obtain release preparation or review-only/no-release approval. Evaluate a
      compatible patch first; `4.5.0-SNAPSHOT` does not select a minor version.
- [ ] Stop incompatible/out-of-scope needs for a separate migration decision and
      record unresolved release evidence as no-go/pending, not passed.

### [ ] 12.2 Select the exact candidate or no-release branch

- [ ] For release, approve an exact candidate after compatibility/claims review;
      update reactor/modules/fixtures, matrix/version guards, current docs,
      changelog and readiness together, preserving original evidence coordinates.
- [ ] Keep baseline `4.4.2` until verified publication and V35 active through the
      final-version cut. Preparation is neither publication nor final release GO.
- [ ] For no release, record rationale and delivered/rolled-back/unresolved scope;
      label candidate/signing/publication gates N/A with the explicit decision.

### [ ] 12.3 Assemble immutable release or review evidence

- [ ] Inventory clean reachable final source, decisions, correctness, ownership,
      API, Boot/consumer/AOT/native, matched cost and guidance evidence.
- [ ] Seal exact commands/statuses, actual counts, toolchains, effective dependencies,
      artifacts/reports/hashes and failures. Preserve local-bundle availability limits.
- [ ] Revalidate after final code/fixture/coordinate edits, separating exact reuse
      from fresh final-coordinate executions. Never claim a new commit for a dirty patch.
- [ ] For release, verify applicable packaging/unsigned/staged checks; signing,
      version-matched tag/workflow, publication and Central consumption remain separate.

### [ ] 12.4 Verify publication or explicit no-release closure

- [ ] For release, verify signing/staged signatures, tag/source/workflow and Central
      provenance. Authorize signing locally; never put passphrases in evidence.
- [ ] Verify isolated published assembled consumption with versions/signatures/
      hashes, not local installation or reactor shadows; only then advance baselines.
- [ ] For no release, record approved final disposition and any explicit scope
      reduction. Unrun release gates are N/A, not passes; unresolved findings stay named.
- [ ] Close roadmap/checklist/index/readiness together with closure revision and
      evidence links. Leave future scope unselected; keep V34 closed and unchanged.

## Completion Criteria

- [ ] Every inventory row has an evidence-backed final disposition; any explicit
      scope reduction is visible and does not claim all deferred findings were fixed.
- [ ] The allocation split is explained and validated, or clearly excluded by an
      explicit reduced-scope decision with no performance all-clear.
- [ ] Each retained production change has approval, reproduced need, semantic and
      ownership acceptance, plus matched benefit or approved correctness cost.
- [ ] Dynamic extensions, mutation validation, body/context/terminal semantics,
      optional integrations, AOT selection and resource cleanup remain compatible.
- [ ] Final public/mock/factory/assembled/Boot/AOT/native/API/cost/packaging evidence
      identifies actual inputs, outcomes, failures and unchanged-input reuse limits.
- [ ] Guidance and public claims reflect measured workloads and release availability.
- [ ] Release/no-release choice and final scope/archive/readiness are verifiable.

Creating this checklist completes no execution item. All seven investigations
remain required; Priority 3.3 approves bounded production work and Priority 12
selects release/no-release and closure. No implicit version bump or new feature.
