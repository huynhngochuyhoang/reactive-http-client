# Reactive HTTP Client - Roadmap V34 Execution Checklist

> **Status:** active
> **Theme:** performance and default-path hardening
> **Published baseline:** `4.4.2`
> **Development coordinate:** `4.5.0-SNAPSHOT`
> **Implementation scope:** V34-C004 evaluated and rolled back; no production change retained
> **Release scope:** unselected
> **Adopted:** 2026-09-30

Execution companion to [`ROADMAP.md`](ROADMAP.md). Adoption authorizes baseline,
workload and characterization work, not production changes, a version bump or a
release. V1-V33 remain completed release records; their evidence is not rewritten.
Creating this checklist completes no execution item and claims no new benchmark.

Execute priorities in order. Record any dependency-based reordering explicitly.
Production edits require the maintainer decision in **Priority 4.3** first.
Priorities 5-8 apply only to selected findings; no selection means documented
production work N/A with relevant regression controls retained, not an obligation
to implement every candidate. A characterization-only/no-release result is valid.

## Completion and Evidence Rules

- Check an item only after its work, verification and disposition are recorded
  under that priority. An approved plan is not evidence of execution.
- Record exact commands, actual test totals, toolchain/settings, reachable source,
  clean/dirty state, resolved dependencies, classpaths and artifact/report hashes
  under `target/release-evidence/v34/priority<N>/`. Preserve failed and partial runs.
- Put durable conclusions in tracked V34 records. Generated bundles under `target`
  are not durable source history; retain their integrity anchors and commands.
  Do not cite squash-local commits unavailable from the reviewed history.
- Distinguish inspected source, correctness witnesses, helper microbenchmarks,
  real proxy/transport workloads, JVM AOT, native execution and published consumption.
- Native and release-quality cost evidence must identify clean reachable source
  containing the final implementation and fixture. Relevant later edits require
  a rerun or an exact unchanged-input reuse statement with narrower limitations.
- Freeze matched workload semantics and measurement criteria before scored runs.
  A skipped row, missing allocation sample or failed native build is not a pass.
- **Not applicable** requires a dated decision identifying the item, reason and
  evidence. Label it beside the checked item. Deferred findings retain an owner,
  workaround, trigger and missing evidence; removing approved work needs approval.
- Allocation rate, retained heap, direct/native memory and RSS are different
  signals. Do not infer a leak fix, pod-memory reduction or universal speedup.
- Keep request targets, headers, bodies, credentials, identities, cache key
  material and arbitrary exception messages out of committed evidence. Use
  synthetic fixtures and bounded structural observations.

## Execution Gates

| Gate | Requirement |
|---|---|
| Priority 1 | Verify adoption, published/development baselines and effective profiles without a version bump |
| Priority 2 | Freeze equivalent workloads, correctness witnesses and measurement rules |
| Priority 3 | Characterize actual cost and ownership before production edits |
| Priority 4.3 | Approve specific findings, acceptance and stop conditions; review-only is valid |
| Priorities 5-8 | Implement only approved local changes and preserve their contracts |
| Priorities 9-10 | Verify affected paths and final matched cost/API/packaging evidence |
| Priority 11 | Document measured behavior, retained costs and operational limits |
| Priority 12 | Select release or no-release, then close only with corresponding evidence |

No performance-mode switch, new public feature/SPI, module, dependency upgrade,
scheduler/pool/default change, retry/cache rule, automatic propagation or broad
handler rewrite is selected. Supported dynamic providers, optional class loading,
body cleanup, validation and isolation cannot be removed to improve a score.

## Intended Records

Create records only as work produces evidence. These are suggested filenames,
not existing results or a requirement to duplicate matrices.

| Suggested record | Contents |
|---|---|
| `BASELINE-SCOPE.md` | Provenance, effective profiles, versions and exclusions |
| `WORKLOAD-CONTRACT.md` | Equivalent phases/rows, correctness witnesses and frozen measurement rules |
| `COST-OWNERSHIP.md` | Baseline costs, attributed sites, lifetimes and ranked hypotheses/findings |
| `IMPROVEMENT-DECISION.md` | Alternatives, selected IDs, approval, acceptance and rollback |
| `HARDENING-EVIDENCE.md` | Delivered local changes, regression controls and ownership results |
| `VERIFICATION.md` | Actual mock/consumer/Boot/AOT/native/API/cost results and limitations |
| `MAINTAINER-GUIDANCE.md` | Retained semantics, reproducible investigation and safe operational interpretation |
| `RELEASE-DECISION.md` | Exact release/no-release choice, immutable evidence and closure provenance |

---

## Priority 1 - Post-`4.4.2` Baseline and V34 Scope Integrity

### [x] 1.1 Verify adoption and version state

- [x] Verify roadmap, checklist, index and archive guard report active V34 while
      V1-V33 remain completed; adoption has selected neither fixes nor a release.
- [x] Verify reactor/module/current-consumer/native/benchmark coordinates remain
      `4.5.0-SNAPSHOT` and published/API/consumer/benchmark baselines remain `4.4.2`.
- [x] Verify readiness follows checklist lifecycle through a final-version cut;
      current `plannedFinalVersion` remains unset and release scope unselected.
- [x] Preserve historical evidence, configuration defaults, dependency versions
      and public behavior; no adoption-only production change.

### [x] 1.2 Establish source and published provenance

- [x] Record reachable source/release tag, clean/dirty state, Java/Maven/GraalVM
      where applicable, settings and effective dependencies. Identify reusable V33
      results without relabeling them as fresh V34 verification.
- [x] Verify published `4.4.2` artifacts and assembled consumption using isolated
      Central provenance, or explicitly revalidate exact reusable evidence. Reactor
      installation cannot substitute for a published baseline.
- [x] Retain POMs, dependency trees, artifact/classpath hashes, actual test totals
      and the existing Java 21/Boot 4.0.0/4.1.0 lanes without silently upgrading them.
- [x] Run applicable version, documentation, archive and readiness guards; record
      real results and any pending baseline evidence.

### [x] 1.3 Freeze effective profile boundaries

- [x] Inventory minimal ordinary calls, auto-configured defaults with/without a
      MeterRegistry, classpath-present/unselected and physically absent optional
      integrations, plus selected-feature controls from the roadmap.
- [x] Record effective client settings, bean inventory/materialization, selected
      operators, filters, codecs, transport/timeouts and pool metrics for each row.
      A hand-built no-observer proxy is not the Spring default profile.
- [x] Keep resilience `enabled=true` without operator intent separate from minimal
      calls. Keep application observers/hooks without a registry and independent
      pool telemetry visible; absent exports do not mean absent behavior.
- [x] Map V34-C001 through V34-C005 to inspection questions, not confirmed defects.
      Record exclusions and retain V32 cleanup/reachability and V33 extension/AOT
      corrections as existing safeguards.

Baseline and profile inventory: [BASELINE-SCOPE.md](BASELINE-SCOPE.md).
Evidence: `target/release-evidence/v34/priority1/`. Reviewed clean source
`d246e70798e43d60a66d4e213ca08d13ba34ddf7`; fresh verification includes this
documentation/test-only patch. Source inspection freezes eight effective profile
boundaries, not benchmark workloads or measured materialization costs. All
implementation candidates and release scope remain unselected.

The reuse audit rehashes 1,822 entries across V33's preparation/publication
bundles, 13 published artifacts and their isolated-consumer provenance. Recounted
ordinary/all-profile published results are four/29 cases; genuine upper-row
candidate results are 29 full and one minimal case. These are reused results,
not new consumer or Central executions. The record links original hashes,
commands, toolchains, source applicability and limitations.

**Completed 2026-09-30.** Oracle JDK 21.0.8, Maven 3.9.9, Boot 4.0.0 and
Central-only settings; ordinary test forks disable explicit GC.

| Fresh verification | Actual result |
|---|---|
| Combined focused verification | 220 cases in 11 classes: 80 documentation/archive/readiness, 136 configuration/activation/lifecycle/ownership, four compiled guidance cases; zero failures/errors/skips |
| Reactor validation and dependency inventory | Passed; effective POM, starter tree/classpath and unchanged Java 21/Boot rows retained |
| Provenance and API fixture guards | Passed, including expected local/mismatched/missing artifact and source/binary incompatibility rejections; not a fresh strict project comparison |
| Script syntax and scope/whitespace | Passed; production, POMs, defaults and V1-V33/proposals unchanged |

The earlier 140-case focused run overlaps the combined result. The missing-record
red test, coordinate-only audit failure and wording-only documentation failure
remain in the evidence bundle. No benchmark, new native build, new published
consumer execution or release decision is claimed. At Priority 1 completion,
Priorities 2-12 remained open.

## Priority 2 - Equivalent Workloads and Measurement Rules

### [x] 2.1 Build matched phase-specific workloads

- [x] Reuse existing planning, invocation, diagnostics, loopback and fairness
      harnesses. Add only rows needed to characterize the defined profiles.
- [x] Use identical harness sources and non-starter dependencies for published
      `4.4.2` and current artifacts. Record expected starter differences and reject
      accidental reactor classes or changed transport/codec versions in baseline runs.
- [x] Separate context/proxy construction, first invocation, warm publisher
      assembly, warm subscription and loopback execution. State setup/warmup work
      and what the measured operation actually includes.
- [x] Cover no-body GET, path/query/header GET, POST String/JSON, ResponseEntity,
      empty completion and HTTP errors. Keep Flux/streaming and cancellation
      correctness controls; measure them if selected changes affect their cost.
- [x] Include minimal and auto-configured paths, selected diagnostics controls and
      production proxy/factory rows. Internal helper timings cannot establish an
      end-to-end improvement. Raw WebClient/Spring comparisons must match semantics.

### [x] 2.2 Prove workload semantics before timing

- [x] Assert method, target, headers, body/result, error contract and dispatch count
      outside timed sections where possible; consume/release response bodies equally.
- [x] Prove publisher creation dispatches nothing and each ordinary subscription
      performs its expected work. A fast row must not be an accidentally unsubscribed
      publisher, an unintended cache hit or an ignored failure.
- [x] Use real API names and representative caller/load states in metered paths;
      verify enabled counters/events, not just a registry's presence.
- [x] Gate concurrent subscriptions and server completion explicitly for any
      shared-load/cancellation row; fixed delays cannot prove attachment.
- [x] Verify fixture setup/teardown releases pools, contexts, registries and worker
      resources. Preserve correctness failures before accepting scored samples.

### [x] 2.3 Freeze measurement and review rules

- [x] Record forks, warmup, measurement, heap, threads, JVM flags, CPU/container
      limits, payload sizes, throughput/latency mode and GC allocation profiler.
- [x] Adopt or justify per-row review triggers before measurement. Starting
      proposal: latency above 20% or B/op above max(32 B/op, 5%) against baseline;
      these trigger investigation, not permission for an arbitrary regression.
- [x] Preserve raw multi-fork samples, intervals, run order and exact artifacts
      before later builds overwrite them. Profiler investigation is separate from
      scored runs; smoke/discovery is not release-quality performance evidence.
- [x] Require flagged results to be checked with another matched pair in reversed
      order. Retain all attempts and explain noise, missing data and stop conditions.
- [x] Keep timing thresholds and forced collection out of normal unit tests. Use
      deterministic correctness checks and the controlled reachability lane where needed.

Workload and pre-measurement rules: [WORKLOAD-CONTRACT.md](WORKLOAD-CONTRACT.md).
Matched build inputs and untimed witnesses are under
`target/release-evidence/v34/priority2/`: 39 identical benchmark source/POM files,
120 identical non-starter dependency JARs, independently resolved Central `4.4.2`,
and the current artifact. Both ordinary benchmark suites pass 75 cases, including
40 new workload-contract cases and one internal-resolution witness. Eight input/
result guard tests pass. The controlled reachability lane passes two cases per
artifact; ordinary runs disable explicit GC. Both smoke runs cover all 54 new
JMH parameter combinations and allocation samples. Final shaded class/JMH-entry
equivalence justifies smoke reuse after the test-only addition; failures and
intermediate runs are retained, not counted again. The final documentation,
archive and readiness guard passes 81 cases; whitespace checks pass.

**Completed 2026-09-30.** The contract freezes the primary 60-row matrix, two-fork
scored settings, per-row 20% latency / max(32 B/op, 5%) allocation review triggers,
reverse-order confirmation and stop rules. Invocation-fixture allocation scope
and shared JVM resource ownership are explicit. Multi-fork scored collection is
Priority 3, not represented by these smoke results. No production/runtime or
dependency-version edit, release selection or performance conclusion is claimed.

## Priority 3 - Default-Path Cost and Ownership Characterization

### [x] 3.1 Measure the unchanged implementation

- [x] Run the frozen workloads against `4.4.2` and the current pre-fix source;
      preserve exact baseline inputs before making production edits.
- [x] Separate cold and warm results, allocation and elapsed cost, client and
      downstream time. Attribute planning/provider/state/body/reporting/Reactor/
      transport costs instead of claiming all B/op is starter overhead.
- [x] Profile dominant sites when scores alone cannot explain a finding; retain
      sanitized aggregate evidence and do not mix profiler timings into scored rows.
- [x] Investigate flagged variance using the frozen confirmation rule. Do not
      convert V33's earlier cold-plan observation into a new unmeasured regression.

### [x] 3.2 Characterize preparation and retention ownership

- [x] Count observer/hook lookups, provider materializations, plan/projection work,
      reporting/body holders and actual feature preparation for each effective profile.
- [x] Observe cache/registry/meter leases, tasks and transport subscriptions on
      inactive paths; distinguish dormant objects from acquired resources.
- [x] Record owners and lifetimes through construction, invocation, subscription,
      terminal completion/cancel and factory close, including failed construction.
- [x] Identify whether proposed reuse would retain request/context/auth data,
      freeze dynamic providers or introduce synchronization/contention. Treat such
      tradeoffs as part of the finding, not as free allocation savings.

### [x] 3.3 Classify findings before selecting fixes

- [x] Rank C001-C005 and any newly reproduced finding by affected profiles, source
      anchors, measured magnitude, confidence and user impact.
- [x] Distinguish intentional cost, repeatable regression, correctness/ownership
      defect and unresolved hypothesis. No-change is a legitimate result.
- [x] Record reproductions, negative controls, known limitations and missing
      evidence; do not assume a memory leak or a need to optimize every allocation.
- [x] Publish the characterization and preserve raw evidence before requesting
      implementation approval. Priority 3 alone authorizes no production fix.

**Evidence:** [cost and ownership characterization](COST-OWNERSHIP.md),
`DefaultPathCostOwnershipTest`, and the frozen-rule analysis/negative guards in
`scripts/review-v34-benchmark-results.py`. Both scored artifacts were built from
clean reachable `ec225b8ed93ab0d1bd461d4eda7a38f23a2579e1` before test/documentation
edits. The 39 harness files, 120 non-starter JARs and all 310 starter class bytes
match; Central `4.4.2` provenance is revalidated in the separate P2 repository.
Raw evidence is preserved under `target/release-evidence/v34/priority3/`.

**Completed 2026-10-01.** All 60 scored rows per artifact have two forks and ten
measurement samples. The first pair flags zero latency and two allocation rows;
reverse-order confirmation clears the registry flag but leaves the enabled-only
publisher's bimodal allocation flag unresolved. Seven separate JFR profiles
provide bounded attribution, not scored timings or reliable CPU-share estimates.
The 14 new structural cases and nine existing classes pass 108 focused tests
with explicit GC disabled; benchmark builds pass 75 tests each. Python evidence
guards pass 14 cases and documentation/archive/readiness passes 82 cases, with
zero failures/errors/skips. Whitespace and unchanged production/packaging checks pass.
Failed fixture cleanup evidence is retained. No production edit, attributed
regression, leak fix, new native result or speedup is claimed. C001-C005 are ranked
but unselected; Priority 4.3 and release selection remain open.

The 500-file bundle inventory is `target/release-evidence/v34/priority3/SHA256SUMS`,
SHA-256 `9efede4bdc30739a4a9992c4062b56d09f598632ce5c048656b42e6836917509`.
It includes scored/profiler inputs, raw attempts, reports and the final test/script/
characterization sources. This checklist is the integrity index, excluded from
the source-copy seal to avoid a self-referential checksum.

## Priority 4 - Explicit Bounded Improvement Selection

### [x] 4.1 Bound alternatives and acceptance

- [x] For each actionable finding, describe the smallest local correction,
      no-change alternative, expected benefit and compatibility/ownership cost.
- [x] Name exact changed owners, effective profiles, public behavior constraints,
      acceptance tests and benchmark rows; define failure and rollback conditions.
- [x] Prefer reproduced correctness/default-off ownership problems, then material
      repeatable costs. Reject broad rewrites or added abstractions without need.
- [x] Identify dependencies and evidence budget; stop for a new scope decision if
      a new public API, default, dependency or unsupported contract is required.

### [x] 4.2 Prepare retained and deferred dispositions

- [x] Mark intentional cost, disproved hypotheses and insufficient evidence
      explicitly. Record workaround, owner and reconsideration trigger for deferrals.
- [x] Map proposed IDs to Priorities 5-8 and shared regressions; avoid implementing
      the same cross-cutting change under several priorities.
- [x] Describe a characterization-only/no-release alternative and its required
      evidence. Do not make roadmap completion depend on a positive speedup.

### [x] 4.3 Record the maintainer scope decision

- [x] Obtain explicit approval of specific finding IDs and boundaries, or an
      explicit review-only decision. Candidate questions are not blanket approval.
- [x] Record date, rationale, acceptance, verification and rollback conditions;
      distinguish implementation selection from release selection.
- [x] Mark unselected production work N/A with its dated disposition while
      retaining relevant regression controls. Do not claim those findings were fixed.
- [x] Align implementation status and intended order; stop production edits until
      this decision exists. Release scope remains unselected.

**Decision, 2026-10-01:** the maintainer selected **C004 only, within that boundary**:
reuse immutable disabled cache-policy selections/decisions, preserving every
mutation check. [Bounded improvement decision](IMPROVEMENT-DECISION.md) records
the exact approved owner (`EffectiveCachePolicy`), acceptance/rollback gates,
alternatives, dated production N/A, retained controls, deferral owners and triggers.
C001-C003, C005 and broader C004 resource changes are deferred. No production
edit or measured benefit is delivered by Priority 4; release scope is unselected.

Priority 6 is the only implementation owner. Execute Priority 5's retained
controls/disposition first, then 6, then the shared ownership/resource controls
in 7-8. Disabled results may be reused, but the complete per-invocation method
scan and live configuration checks remain. The enabled-only allocation flag
remains unresolved for final matched investigation, not cleared by approval.
The no-change/no-release alternative remains valid after an explicit later
decision.

**Completed 2026-10-01.** Decision/contract verification passes 194 tests in six
classes (83 documentation plus 111 existing contract cases), with zero failures,
errors or skips and explicit GC disabled. The 14 Python evidence guards pass;
the P3 500-file inventory revalidates unchanged. Source review and whitespace
checks confirm no production, benchmark, dependency/coordinate or historical
record edit. Evidence: `target/release-evidence/v34/priority4/`. These are scope
and regression guards, not implementation, new benchmarks or native/API results.

The 62-file `priority4/SHA256SUMS` inventory has SHA-256
`0f3ab58da9fa117432561d386974c03c8102bfd901bcd19bf8fcf15e57204760`.
It retains both overlapping verification runs, final reports/readiness and the
decision/test/current-guidance sources; this checklist remains the external
integrity index and is excluded from the source-copy seal.

## Priority 5 - Planning and Ordinary Invocation Hardening

Conditional on Priority 4.3 selection affecting planning or request materialization.

**Scope disposition, 2026-10-01:** production planning/projection changes N/A;
C003 is deferred. Retain planning/selection controls and the execution disposition
below. Completed controls do not authorize a planning optimization.

### [x] 5.1 Implement only the selected local correction

- [x] Add desired-behavior and baseline cost witnesses for selected IDs before
      changing repeated static work, argument/default/header projection or collections.
      **Production N/A:** no planning ID selected; existing cost witnesses retained.
- [x] Keep the existing plan/resolver ownership boundary and invocation/subscription
      timing. Do not introduce another metadata model or deep-freeze ordinary calls
      merely to enable reuse.
- [x] Avoid retaining arguments, bodies or Reactor context in cached plans; preserve
      sufficient concrete method/generic identity for shared metadata.

### [x] 5.2 Preserve request planning and wire semantics

- [x] Exercise annotation parsing, complete fresh public metadata, retained derived
      values, API-ref precedence and inherited concrete/generic methods.
- [x] Verify null versus empty, ordered query/header values, escaping/URI projection,
      default headers, body presence, final charset and context idempotency.
- [x] Preserve dynamic per-call/subscription values and reject invalid metadata at
      the existing boundary. No body pre-serialization or normalization solely for cost.
- [x] Cover cold/repeated/concurrent subscriptions where touched; inspect exact
      requests and results rather than helper state alone.

### [x] 5.3 Verify and record the bounded outcome

- [x] Run focused planning/resolution/public-entry regressions and selected cost
      rows; label exploratory timings separately from final Priority 10 evidence.
      **Cost rerun N/A:** no selected planning correction; exact unchanged-input
      P3 evidence reuse, not new scoring or exploratory timing.
- [x] Record each delivered ID, semantic checks, remaining costs and rollback
      disposition. Remove an unhelpful optimization instead of expanding its scope.
- [x] If no relevant ID was selected, record production work N/A and the retained
      controls without claiming a planning improvement.

**Completed 2026-10-01.** [Planning and invocation controls](PLANNING-INVOCATION.md)
record production N/A, no delivered production ID and C003's deferral/reopening
boundary. Two null/empty wire cases and a strengthened materialized-charset
assertion supplement retained metadata, URI, selection and caller-isolation tests.
The final focused run passes 170 cases in 13 classes; documentation/archive/
readiness passes 84, for 254 Java cases with zero failures/errors/skips and explicit
GC disabled. Python evidence guards pass 14. Historical P3 (500 files) and P4
(62 files) inventories revalidate unchanged; no new performance claim or planning
optimization is delivered. C004 remains pending in Priority 6, release scope
unselected, and the enabled-only allocation flag remains unresolved.

Evidence: `target/release-evidence/v34/priority5/`. The earlier overlapping core
run is retained, not added to final totals. Production, benchmark, dependency,
coordinate and historical-record inputs are unchanged.

The 99-file `priority5/SHA256SUMS` inventory has SHA-256
`4a2136f069632f8d3e4e7de0ae84ab19df97e5fe962ed0b1375b5baacedfe8b6`.
It includes final reports, readiness and source copies; this checklist remains
the external integrity index and is excluded from the source-copy seal.

## Priority 6 - Diagnostics and Optional-Feature Cost Isolation

Conditional on Priority 4.3 selection affecting discovery, reporting or feature preparation.

**Selected scope, 2026-10-01:** deliver C004's disabled-value reuse only, as bounded
in [the decision](IMPROVEMENT-DECISION.md). Production discovery/reporting changes
N/A (C001/C002 deferred); retain their controls. Do not remove state or skip
selection validation to lower allocation.
The candidate was confined to `EffectiveCachePolicy` and rolled back after the
benefit gate failed; see [hardening evidence](HARDENING-EVIDENCE.md). No production
ID is delivered. Priorities 7-10 still own remaining ownership, parity and matched
cost/compatibility verification; release scope remains unselected.

### [x] 6.1 Preserve dynamic discovery while reducing proven work

- [x] Change provider discovery/composition only for selected findings; preserve
      late observer/hook registration, order and per-client support checks.
      **Production discovery N/A:** C001 deferred; C004 reuse tested, then rolled back.
- [x] Cover empty-to-present, single/multiple, prototype/provider products and
      first-call concurrency. Do not introduce a permanent empty-result cache.
- [x] Verify no-registry application consumers and classpath/bean absence; disabled
      built-in exports cannot suppress requested observer or lifecycle behavior.

### [x] 6.2 Keep state for every required semantic path

- [x] Preserve state required by auth, explicit resilience, generated idempotency
      and logical deadlines when logging/metrics are absent. Retain the separate
      enabled-only/no-operator resilience control.
- [x] Remove snapshots/body inspection/events/meter work only on genuinely
      unconsumed paths; keep independently enabled pool gauges independent.
      **Production reporting N/A:** C002 deferred; no state or telemetry removed.
- [x] Verify terminal-once reporting, attempts/dispatches, retry/auth replay/
      redirects, timeout phase, cache outcome and downstream health sampling for
      touched paths. Request evidence must reflect final filter mutations.
- [x] Keep sensitive data and unbounded labels out of all reporting surfaces.

### [x] 6.3 Verify cost isolation and selected integrations

- [x] Run profile-paired discovery/reporting tests with disabled exports, absent
      MeterRegistry and actual selected metrics/OTel/logger/hook controls as applicable.
- [x] Compare selected diagnostic rows against the same effective behavior;
      omitted telemetry is not an optimization.
- [x] Record delivered IDs and retained costs, or dated production N/A with relevant
      controls. Keep final scored confirmation for Priority 10.

**Completed 2026-10-02.** C004's disabled-value reuse passed semantic controls but
failed the required repeatable P02 allocation-benefit gate. All 12 warm-publisher
rows were compared; P02 GET was repeated in reversed order. Initial P02 allocation
is approximately 832 B/op for both artifacts; the reversed baseline's 832/1,088
B/op fork split does not establish a repeatable reduction. The optimization was
removed without broadening scope. Seven new behavioral cases remain; mutation,
provider discovery and terminal-state checks are unchanged. No delivered
production improvement, new native result or public speedup is claimed.

Final rollback verification passes 303 starter cases in 18 classes, 62 OTel cases,
one assembled minimal-consumer case and 85 documentation/archive/readiness cases;
14 Python evidence guards pass. Both candidate benchmark builds pass 75 cases
each (overlapping, not 150 distinct tests). Exact commands, failed attempts,
artifacts, raw forks and final-source checks are retained under
`target/release-evidence/v34/priority6/`. P3's enabled-only allocation flag remains
unresolved; Priorities 7-12 and release selection remain open.

The 463-file `priority6/SHA256SUMS` inventory has SHA-256
`3a8137c57ee0db61a238b8634951ee01e6c9f26e65d7aa620c9115c28a131995`.
It includes rejected candidate and rebuilt rollback artifacts, matched inputs,
raw measurements, commands, failed attempts, final reports/readiness and source
copies. P3/P4/P5 inventories revalidate unchanged; all 310 rebuilt starter class
files match Central `4.4.2`. This checklist remains the external integrity index,
excluded from final source-copy sealing to avoid a self-referential checksum.

## Priority 7 - Body, Context and Terminal Ownership

Apply to selected changes that touch execution or state; retain shared safety controls.

**Scope disposition, 2026-10-01:** production body/context/terminal changes N/A;
C002 is deferred. Retain applicable ownership regressions for the selected diff
and explicitly record applicability/reuse when executing this priority.

**Execution disposition, 2026-10-03:** C004 was rolled back in Priority 6, so
there is no retained production diff to optimize here. Keep the shared ownership
controls and the exact applicability/limitations in
[body, context and terminal ownership](BODY-CONTEXT-OWNERSHIP.md); no new
production ID or release scope is selected.

### [x] 7.1 Preserve body ownership and reactive delivery

- [x] Verify exactly-once release/transfer for DataBuffer, streams/readers/channels
      and response bodies on success, empty, error, decode/serialization failure,
      pre-dispatch rejection and cancellation where supported.
- [x] Exercise no-body/immutable-body fast paths without removing resource-body
      guards. Preserve cold publishers, backpressure and streaming behavior.
- [x] Test replayable repeated/concurrent subscriptions; keep one-shot input limits
      explicit. No cost change may promise replay of consumed resources.
- [x] Cover cancellation after a value is buffered and after cancellation races
      with delivery; assert Reactor discard cleanup, not just reference clearing.

### [x] 7.2 Preserve caller context and terminal isolation

- [x] Verify supported scheduler hops, explicit async handoff, named case-insensitive
      inbound access and independent subscriber/tenant snapshots where touched.
- [x] Prevent request/context/auth retention in reusable state; no mutable reporting
      holder pooling across callers or observations after that caller terminates.
- [x] If affected, keep admission until guarded synchronous/async preparation and
      cleanup unwind; prevent continuations advancing after the relevant terminal.
- [x] If affected, keep shared-load lifecycle separate from caller deadlines, and
      refresh hard bounds, eviction and shutdown cancellation owned correctly.

### [x] 7.3 Prove cleanup without timing or GC assumptions

- [x] Use gates, controlled time and observable cleanup acknowledgements; do not
      assert race completion immediately after cancellation or rely on fixed sleeps.
- [x] Run applicable regular regressions with explicit GC disabled. Use the existing
      controlled JVM reachability lane only for actual collection claims.
- [x] Record state/resource owners, exact terminal outcomes and selected scope;
      document N/A for untouched scenarios with evidence, not a broad safety waiver.

**Completed 2026-10-03.** Ten new loopback/upload cases verify pre-dispatch
auth-failure/cancellation cleanup and immutable/application-replayable repeated
subscriptions. Three gated discard cases now assert actual pooled-buffer release
or caller transfer, with admission held through blocked cancellation cleanup.
The ownership matrix passes 366 cases in 23 classes with explicit GC disabled;
five repeated targeted runs pass 14 cases each (70 overlapping executions).
Documentation/archive/readiness passes 86 cases; Python evidence guards pass 14.
All final tests have zero failures/errors/skips. The earlier test-compilation
failure and its explicitly excluded stale reports remain recorded, not counted.

Production work and new collection/cost claims are N/A. Cleanup counters,
reference counts and acknowledged terminal boundaries are not heap-collection or
RSS evidence. Existing controlled reachability lanes were not rerun. P3-P6
inventories and production/benchmark/packaging inputs are unchanged. Evidence:
`target/release-evidence/v34/priority7/`. C002 remains deferred, the enabled-only
allocation flag unresolved, and Priorities 8-12/release selection remain open.

The 192-file `priority7/SHA256SUMS` inventory has SHA-256
`070b2b06023d880a5af5268a8c02c0f55f1d3bfce408c5734ba2494764a30285`.
It preserves the failed compile/stale-report classification, focused and repeated
runs, final reports/readiness, source copies and P3-P6 inventory verification.
This checklist is the external integrity index, excluded from final source-copy
sealing to avoid a self-referential checksum.

## Priority 8 - Inactive Resources and Framework Lifecycle

Conditional production work follows Priority 4.3; inactive-path controls remain required.

**Scope disposition, 2026-10-01:** production resource/framework changes N/A;
broader C004 and C005 changes are deferred. Retain inactive-path, physical absence,
failed-construction and lifecycle controls; C004 was evaluated once in Priority 6
and rolled back.

### [x] 8.1 Verify unselected features do not acquire resources

- [x] Compare present/unselected and physically absent dependency profiles; observe
      actual cache/auth/operator/telemetry preparation and resource acquisition.
- [x] Verify no unneeded background tasks, registry/meter leases or transport
      subscriptions; a dormant holder alone is not a leak.
- [x] Preserve optional class loading, explicit activation and default behavior;
      do not replace absence tests with a no-op implementation on a full classpath.

### [x] 8.2 Preserve construction and framework ownership

- [x] Verify validation precedes acquisition with caching/telemetry actually selected;
      observe real leases/resources, not only an unassigned factory field.
- [x] Exercise failed-construction rollback, successful ownership transfer and
      destroy/recreate, including overlapping live metric owners where applicable.
- [x] Do not dispose application-owned connectors, pools or executors. Keep
      factory-owned cleanup and independent caller work explicitly distinguished.
- [x] For selected lifecycle changes, preserve runtime/AOT ordering, supported
      properties/metadata selection and non-instantiating diagnostics; prevent
      AOT-only tracking from accumulating runtime observations or creating unrelated factories.

### [x] 8.3 Record resource and lifecycle acceptance

- [x] Run focused disabled-path and lifecycle regressions for selected changes;
      inspect resources through terminal and close, not process RSS alone.
- [x] Record any measured cost/retention tradeoff and retained framework work;
      zero allocation and immediate RSS reduction are not acceptance promises.
- [x] Record delivered IDs or dated production N/A; preserve V32/V33 guarantees
      and route broader selection problems to a new explicit scope decision.

**Completed 2026-10-03:** [inactive-resource and lifecycle evidence](INACTIVE-LIFECYCLE.md)
records production N/A and no delivered IDs. Two new cases cover unselected
definitions (including weighted/refresh/work configuration) without materializing
lazy auth/registry beans or acquiring cache meter owners. The external connector
control now also verifies the application's auth scheduler/executor survives
factory/context close. Existing controls cover selected-cache validation,
construction rollback, overlapping live owners, caller-owned independent loads,
runtime tracking shutdown, AOT selection/order and non-instantiating diagnostics.
C004 remains rolled back; broader C004/C005 work remains deferred.

Fresh resource/diagnostics tests pass 219 cases in 12 classes; framework/AOT tests
pass 323 in seven. The isolated Boot 4.0.0 optional-absence consumer passes one
case against the installed starter JAR, with two real GET dispatches and
resilience enabled but no selected operators. Documentation/archive/readiness
passes 87 cases and V34 Python evidence guards pass 14: **630 distinct Java cases**,
zero failures/errors/skips in the final runs. All Java runs disable explicit GC. The initial
focused failure is retained as a corrected test-fixture setup error, not a
production failure. No new JMH, collection, native or RSS claim is made;
Priorities 9-12, the allocation flag and release selection remain open.

Evidence: `target/release-evidence/v34/priority8/`. The 118-file `SHA256SUMS`
inventory has SHA-256
`fc871bd90fcbd7562e8b0f3e036286a6056f4535bc333ef85511ecc0a6085297`.
It retains the failed focused attempt, corrected suites, consumer artifacts,
source/patch, readiness and fresh P3-P7 inventory checks. The consumer starter JAR
has SHA-256 `134f64c361bea6066ff6eff72bbf8d7dc64102f7887fad5131a6f0b29f284037`;
all 310 class files match the previously verified published `4.4.2` artifact.
Production, benchmark, dependency, coordinate and consumer/native fixture inputs
are unchanged. This checklist is the external integrity index, excluded from
final source-copy sealing to avoid a self-referential checksum.

## Priority 9 - Cross-Path and Assembled Parity

### [x] 9.1 Verify supported entry points and assembled consumers

- [x] Cover mocks, public handler entry points and Spring factory creation for
      the accepted diff, including intentional differences in available integration state.
- [x] Verify external consumers use built artifacts, not reactor classes; record
      selected profiles, effective dependencies, hashes and real test totals.
- [x] Run genuine Boot 4.0.0 and 4.1.0 consumer parents with tracked fixtures or exact
      reproducible overlay commands. A mixed dependency tree is not a second Boot lane.
- [x] Exercise minimal, selected-feature and physical optional-absence controls
      after relevant changes. Mock results do not prove transport release semantics.

### [x] 9.2 Verify AOT and native execution where affected

- [x] Run JVM/AOT witnesses for accepted production changes affecting execution,
      selection or lifecycle; preserve request and terminal/ownership assertions.
- [x] Compile and run required native evidence from clean reachable source with
      final fixtures. Record toolchain, build resources, command exits and binary hash.
- [x] Count all relevant loopback dispatches and use bounded quiet/terminal
      evidence; an unregistered route or immediate counter read is not zero-dispatch proof.
- [x] Preserve failed/partial attempts. Resource exhaustion, stale binaries or
      JVM-only verification leave required native work pending.

### [x] 9.3 Reconcile parity and evidence applicability

- [x] Map results to each selected boundary and retain any uncovered shapes or
      supported limitations; do not infer universal coverage from representative cases.
- [x] For unchanged inputs/review-only scope, record exact evidence reuse or dated
      N/A with limitations. Changed native inputs require the relevant rerun.
- [x] Seal commands, classpaths, reports and source/artifact hashes; fix regressions
      before promoting a cost result or accepting the implementation.

**Completed 2026-10-03:** [cross-path and assembled parity](PARITY-EVIDENCE.md)
records no delivered production IDs and fresh verification from clean starting
commit `fc98e58b9d5154a0ba539ea878b05c532b379554`. The tracked overlay runner
uses genuine Boot 4.0.0/4.1.0 parents and verifies built-JAR dependency classpaths.
Mock helpers pass 76 cases; each Boot row passes 29 selected-consumer cases,
one physical-absence case, 283 focused AOT/cross-path cases and six smoke fixture
cases. Both ordinary and AOT-enabled smoke executions pass on each Boot row.
These total 714 JUnit executions, not unique test definitions across rows.

Fresh Boot 4.0.0 native compilation and execution both exit 0 from a clean
detached checkout of that commit; six fixture tests pass during compilation.
The binary SHA-256 is
`a3476f5f749d0cb546e4c175f371ea070291923d5621d92cb819758ab9a3b10e`.
Full native evidence is in
`target/v34-priority9-native-runs/native-feynp9yy/evidence/`; parity commands,
reports, copied overlays and dependencies are under
`target/release-evidence/v34/priority9/`. No failed runtime attempt or native
retry occurred in this priority. Earlier failed attempts remain in their original
bundles, not counted as current passes.

P8 remains earlier resource evidence, not a second set of new tests here.
Mock versus transport, public-handler diagnostics, minimal-profile source and
native-versus-upper-Boot limits are explicit in the record. No production,
fixture, dependency, coordinate or runner change is retained. C004 stays rolled
back; the allocation flag, Priorities 10-12 and release selection remain open.

Final documentation/archive/readiness passes 88 cases: **808 Java test executions**
including the 714 JVM-row cases and six native-build fixture cases above, all
with zero failures/errors/skips. V34 evidence guards pass 14 Python cases and
native-runner guards pass five. Final readiness remains V34 active, release
lane/scope unselected and `plannedFinalVersion=null`. The audit verifies the
current starter's 310 class files still match the archived published baseline;
this is not the pending strict API lane or a new Central-consumer result.

The 1,213-file `priority9/SHA256SUMS` inventory has SHA-256
`596d511a2683fad7bbdd76fccf4779b9e80a9f7045883739cd023c0b0a22c5b7`.
It retains commands/exits, copied fixture outputs, reports, module artifacts,
source copies and final provenance, plus fresh P3-P8 inventory verification.
The native-reference audit verifies its separate inventory with SHA-256
`d62ac7cd25d3f9eb816595b9ef6b07d0af431dfa53e4781c8d7968dd9a4c6821`.
This checklist remains the external integrity index, excluded from final
source-copy sealing to avoid a self-referential checksum.

## Priority 10 - Matched Performance and Compatibility Evidence

### [ ] 10.1 Run final matched cost and allocation verification

- [ ] Run selected before/after rows plus minimal/default and enabled-feature
      sentinels with final fixtures and frozen Priority 2 rules.
- [ ] Preserve identical non-starter stacks and semantic witnesses, isolated
      published provenance, raw JMH/GC samples, intervals, order and artifact hashes.
- [ ] Confirm flagged rows with a reversed-order matched pair; retain original
      flags and failures. Explain missing samples or workload differences explicitly.
- [ ] Require demonstrated benefit for optimizations or reproduced safety/ownership
      benefit for hardening, with honest cost. Roll back unhelpful optimization;
      a repeatable regression needs an explicit bounded correctness tradeoff decision.

### [ ] 10.2 Verify API, behavior and packaging

- [ ] Run strict root and independent starter source/binary API comparisons against
      Central `4.4.2`, each with isolated provenance and separate reports/exits.
      Run the second comparison even if the first fails; retain failures.
- [ ] Review behavioral and extension compatibility beyond japicmp, including
      defaults, provider lifecycles, metadata/builder/AOT and optional linkage.
- [ ] Run applicable complete module suites and focused regressions, generated
      docs/metadata, packaging and version/fixture guards. Report actual totals.
- [ ] Run/reconcile supported Boot and affected mock/consumer/AOT/native evidence
      from Priority 9; preserve native collection controls and avoid double-counting cases.

### [ ] 10.3 Seal results and constrain performance claims

- [ ] Inventory final source, fixtures, dependencies, environment, commands,
      actual outcomes, raw samples/reports and artifact/evidence hashes.
- [ ] Rerun evidence affected by later implementation or fixture edits; explain
      exact unchanged-input reuse instead of substituting old totals.
- [ ] Separate microbenchmark, startup, throughput, tail latency and deployment
      memory conclusions. B/op or mean no-network time supports only that measured claim.
- [ ] Follow the existing release-quality report promotion process for public
      numbers. No claim is required; smoke runs or unresolved noise cannot justify one.

## Priority 11 - Maintainer and Operations Guidance

### [ ] 11.1 Document delivered behavior and intentional costs

- [ ] Link effective profiles, findings, approved IDs, delivered changes and
      retained/deferred work. Explain why required discovery/state/cleanup remains.
- [ ] Document unchanged defaults and extension/ownership guarantees; avoid a
      universal zero-overhead claim or invented performance-mode configuration.
- [ ] Distinguish candidate behavior from published `4.4.2` until publication;
      keep V33's historical results and unresolved incident conclusions intact.

### [ ] 11.2 Publish reproducible investigation guidance

- [ ] Provide tracked commands/fixtures for phase-specific matched workloads,
      correctness controls and repeatable confirmation of cost flags.
- [ ] Explain CPU/allocation versus retention/direct memory/RSS, profiler overhead,
      workload/classpath differences and measurement noise.
- [ ] Keep support capture bounded and sanitized; no new meter/dashboard or
      production payload capture is implied by this roadmap.

### [ ] 11.3 Reconcile guidance and documentation guards

- [ ] Update relevant benchmark, performance, customizer/context/ownership and
      operations guidance only where delivered findings require changes.
- [ ] Verify examples, local links, scope/status consistency, generated references
      and current commands with actual test totals; preserve historical evidence.
- [ ] Record remaining owners, workarounds, triggers and explicit limitations.
      Guidance cannot turn an unselected candidate or noisy score into a delivered fix.

## Priority 12 - Scope Decision and Conditional Release Go/No-Go

### [ ] 12.1 Reconcile implementation scope and release intent

- [ ] Match approved IDs to delivered/rolled-back changes, acceptance evidence
      and deferrals; resolve blockers or obtain explicit scope removal.
- [ ] Obtain the maintainer decision for release preparation or review-only/no-release.
      Evaluate a compatible patch first; `4.5.0-SNAPSHOT` does not select a minor.
- [ ] Record unresolved evidence as no-go/pending, not completed work. Review-only
      closure needs explicit disposition of all findings and any implementation.

### [ ] 12.2 Select the exact candidate or no-release branch

- [ ] For a release, approve the exact candidate after compatibility/migration and
      performance-claim review. No new feature/default enters by implication.
- [ ] Update reactor/modules/fixtures, supported-matrix/version guards, current
      commands, changelog and readiness together; keep baseline `4.4.2` until
      publication is verified and keep V34 active through the final-version cut.
- [ ] For no release, record rationale, accepted findings and implementation
      disposition; label candidate/signing/publication work N/A explicitly.
- [ ] Record the branch without treating preparation as publication or final GO;
      preserve earlier evidence coordinates and source limits.

### [ ] 12.3 Assemble immutable release or review evidence

- [ ] Inventory clean reachable final source, decisions and required correctness,
      API, consumer, Boot/AOT/native, cost, ownership and guidance evidence.
- [ ] Seal exact commands, actual totals, toolchains, effective dependencies,
      artifacts/reports and hashes; keep failures and remaining limitations.
- [ ] Revalidate after final source/fixture/coordinate changes; document exact
      unchanged-input reuse without calling it a new final-coordinate run.
- [ ] For a release, verify applicable packaging/unsigned/staged checks; signing,
      tag/workflow publication and Central consumption stay separate until verified.

### [ ] 12.4 Verify publication or no-release closure

- [ ] For the approved release, verify signing/staged signatures, version-matched
      tag/workflow and Central artifact provenance. Authorize signing locally;
      never include passphrases in evidence.
- [ ] Verify published assembled consumption from an isolated repository with
      versions/signatures/hashes, not a reactor install or local repository shadow.
- [ ] For approved no-release/no-go closure, record explicit final disposition
      and evidence limits; unrun required release gates cannot be called passes.
- [ ] Only then close roadmap/checklist/index/readiness together; advance baselines
      only after verified publication and leave future scope unselected.
- [ ] Record closure revision and evidence links. No pending required work may
      be presented as completed release evidence.

## Completion Criteria

- [ ] Effective profiles and equivalent workloads are measured against published
      `4.4.2` under rules frozen before scoring.
- [ ] Findings have explicit dispositions and production work was approved before
      edits; no regression/leak premise or positive speedup was assumed.
- [ ] Selected improvements demonstrate benefit or reproduced hardening while
      preserving defaults, supported extensions, optional behavior and ownership.
- [ ] Request/context/terminal semantics and cleanup remain verified; allocation
      reduction has not introduced retention, hidden work or cross-caller state.
- [ ] Required matched cost, API, module, mock/consumer, Boot/AOT/native and
      packaging results identify final inputs, actual outcomes and evidence limits.
- [ ] Guidance and public claims match the measured workload and release availability.
- [ ] Release/no-release choice and final archive/readiness state are verifiable.

No execution item is complete merely because this checklist exists. Production
scope is limited to the recorded decision; broader work requires renewed
Priority 4.3 approval. Release scope still requires Priority 12.
