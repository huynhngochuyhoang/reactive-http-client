# Reactive HTTP Client - Roadmap V33 Execution Checklist

> **Status:** active
> **Published baseline:** `4.4.1`
> **Development coordinate:** `4.5.0-SNAPSHOT`
> **Implementation scope:** unselected; Priority 2.3 approval required
> **Release scope:** unselected
> **Adopted:** 2026-09-19

Execution companion to [`ROADMAP.md`](ROADMAP.md). Adoption starts baseline and
characterization work; it does not complete Priority 1, select F001-F003 for
implementation, authorize publication, or reopen V32. V1-V32 remain completed
release records. V32-F004/F005 are delivered safeguards, not new feature work.

Execute priorities in order. Record any dependency-based reordering explicitly.
Production edits require the maintainer decision in **Priority 2.3** first.
Priorities 3-5 apply only to selected finding IDs; no selection means a documented
review-only path, not an obligation to implement all three candidates.

## Completion and Evidence Rules

- Check an item only after its work, verification and disposition are recorded
  under that priority. Checklist creation itself checks no execution items.
- Record exact commands, actual test totals, toolchain/settings, reachable source
  revision, clean/dirty state, report/artifact hashes and limitations under
  `target/release-evidence/v33/priority<N>/`. Keep failed and partial runs.
- Put durable conclusions in tracked V33 records. Reuse a record where sufficient;
  proposed filenames below are not existing evidence or mandatory abstractions.
- Distinguish inspected source, baseline characterization, passing desired behavior,
  assembled reactor consumers and freshly resolved published artifacts.
- Do not call a JVM processor test a native run, discovery a benchmark, or a
  historical test count verification of the current tree.
- Native and release-quality performance evidence must identify clean reachable
  source containing the final fixture and implementation. Source changes after
  measurement require a relevant rerun or a documented narrower claim.
- **Not applicable** requires a dated decision identifying the item, reason and
  supporting evidence. Label it beside the checked item; an unrun gate is not a pass.
- Deferred findings retain their workaround, owner, trigger and missing evidence.
  Removing accepted work requires an explicit scope decision, not silent deferral.
- Keep production secrets, request/header/body values, tenant identities and cache
  key material out of evidence. Structural witnesses and synthetic fixtures suffice.

## Execution Gates

| Gate | Requirement |
|---|---|
| Priority 1 | Establish the published/development baseline and adoption state without a version bump |
| Priority 2 | Reproduce candidates and explicitly approve IDs, boundaries, acceptance and verification |
| Priorities 3-5 | Implement only selected F001/F002/F003 corrections, separately |
| Priority 6 | Preserve cross-path safety, intentional validation differences and F004/F005 guarantees |
| Priorities 7-8 | Verify affected mock/consumer/Boot/AOT/native/API paths and any required cost evidence |
| Priority 9 | Publish version-scoped guidance for delivered behavior and remaining workarounds |
| Priority 10 | Select no-release or an exact candidate; close only with the corresponding evidence |

No new public SPI, general bean resolver, module split, dependency upgrade,
automatic propagation, live reconfiguration or second metadata model is selected.
Runtime/AOT may create eligible configuration where appropriate; diagnostics must
retain its non-instantiating and supported-unknown contract. Safety restrictions
are not relaxed to make an extension example pass.

## Intended Records

Create these only as work produces evidence; consolidate rather than duplicating
matrices. Links to V32 remain historical inputs.

| Suggested record | Contents |
|---|---|
| `BASELINE-SCOPE.md` | Reachable source, baseline provenance, coverage, exclusions and evidence reuse |
| `FIX-DECISION.md` | Reproductions, alternatives, selected IDs, approval, compatibility and rollback |
| `EXTENSION-PARITY.md` | Delivered corrections, external cases, negative controls and cross-path results |
| `VERIFICATION.md` | Actual JVM/API/consumer/matrix/AOT/native/cost results and retained failures |
| `RELEASE-DECISION.md` | Guidance/migration summary, exact release or no-release choice and closure provenance |

---

## Priority 1 - Post-`4.4.1` Baseline and V33 Scope Integrity

### [ ] 1.1 Align adoption and version state

- [ ] Verify reactor/module/current-consumer/native/benchmark coordinates remain
      `4.5.0-SNAPSHOT` and public/API/consumer/benchmark baselines remain `4.4.1`.
- [ ] Verify roadmap, checklist, index and archive guard report active V33 while
      V1-V32 remain completed; no release or implementation scope is selected.
- [ ] Verify generated readiness uses checklist lifecycle, stays active through
      a final-version cut, and keeps the current planned final version unset.
- [ ] Preserve historical V1-V32 and proposal content; no adoption-only dependency,
      production API, configuration or schema change.

### [ ] 1.2 Establish baseline provenance

- [ ] Record reachable source/release tag, toolchain, settings and clean/dirty state.
      Identify which V32 publication and compatibility results remain reusable.
- [ ] Verify the published parent/module artifacts and assembled `4.4.1` consumer
      through isolated Central provenance, or explicitly revalidate and label exact
      reused evidence. Do not substitute a reactor install for published consumption.
- [ ] Retain artifact hashes, versions, effective POMs, dependency trees, classpaths
      and actual test totals; distinguish fresh results from prior observations.
- [ ] Confirm the Java 21 and existing Boot 4.0.0/4.1.0 lanes without upgrading them.

### [ ] 1.3 Freeze characterization scope

- [ ] Inventory F001-F003, their original reproductions, workarounds and missing
      evidence from V32; retain original finding IDs.
- [ ] Identify F004 cleanup and F005 controlled-test safeguards that must remain
      intact, without treating their fixes as new V33 work.
- [ ] Record exclusions and baseline conclusions, then run documentation,
      archive/readiness and applicable version guards with actual results.

## Priority 2 - Reproduction and Explicit Fix Selection

### [ ] 2.1 Reproduce the three candidate gaps

- [ ] Reproduce F001 through bean-factory and ApplicationContext entry points using
      the same starter builder and properly classified application customizations.
- [ ] Reproduce F002 with fresh static public metadata from an external parser;
      keep delegated parsing and API-ref construction as passing controls.
- [ ] Reproduce F003 with paired runtime/AOT primary, non-fallback, priority and
      default-candidate properties selection, including the existing primary control.
- [ ] Record construction, selection and dispatch witnesses separately. Preserve
      baseline outcomes and fixture failures before changing defect expectations.

### [ ] 2.2 Bound alternatives and acceptance

- [ ] For each ID, document affected consumer need, workaround cost, no-change
      alternative and smallest correction within the existing owner.
- [ ] Specify API/configuration/behavioral compatibility, negative tests, creation
      permissions, dependencies, verification budget and rollback conditions.
- [ ] Name unsupported or untested shapes rather than inferring universal Spring,
      native, parser or builder compatibility from representative fixtures.
- [ ] Reject new public abstractions or broader refactors not required by the
      reproduced gap; record any need for a separate proposal.

### [ ] 2.3 Record the maintainer scope decision

- [ ] Obtain explicit approval of selected finding IDs, or a review-only decision.
      Proposed F001 + F002 + F003 is not approval merely because it is listed.
- [ ] Record date, rationale, bounded changes, acceptance/verification requirements
      and stop conditions. Identify the independent implementation order.
- [ ] Mark unselected candidate priorities not applicable with their deferral,
      workaround, owner and reconsideration trigger; do not claim a fix.
- [ ] Update V33 implementation status consistently without selecting a release.
      Stop production work until this decision exists.

## Priority 3 - Starter Builder Ownership and Cache-Safety Validation

Conditional on **V32-F001** approval.

### [ ] 3.1 Correct ownership lookup locally

- [ ] Add desired-behavior regressions for context versus owning bean-factory lookup,
      retaining a reproducible pre-fix baseline.
- [ ] Resolve the actual owning definition through existing factory/context helpers;
      do not exempt by bean name, factory-method name or SAFE label alone.
- [ ] Preserve conservative rejection of unknown ownership and existing explicit
      SAFE declarations; do not create lazy components just to inspect ownership.

### [ ] 3.2 Preserve application customization safety

- [ ] Cover inherited builders, child shadowing, application replacement builders
      and same-named definitions with both positive and negative controls.
- [ ] Keep applicable Boot/per-client customizers classified, including filters,
      defaultRequest, exchange functions, connector and other builder mutations.
- [ ] Count lazy/prototype/product creation where relevant to prove inspection
      does not materialize application components.
- [ ] Keep cache auth, request variants and per-caller gates enforced independently
      of the starter-builder exemption.

### [ ] 3.3 Verify affected entry points

- [ ] Prove the external E12 consumer no longer needs the redundant starter-builder
      classification through runtime, AOT and inspection paths as applicable.
- [ ] Prove unclassified application behavior still fails and prior valid SAFE
      configurations remain accepted.
- [ ] Record changed behavior, focused tests and rollback assessment. Stop for
      scope review if the fix requires weakening ownership proof or the inventory.

## Priority 4 - Public Static Metadata and Effective Request Planning

Conditional on **V32-F002** approval.

### [ ] 4.1 Define and implement static derivation

- [ ] Add an external fresh-public-metadata regression that fails before the fix
      and asserts actual method/target/result afterward.
- [ ] Derive missing static effective API at the existing validated planning boundary
      without reflection, package relocation or making EffectiveApi public.
- [ ] Define supplied-derived-value versus public-field behavior and preserve
      built-in/static/API-ref precedence without another mutable decision model.

### [ ] 4.2 Preserve grammar and plan ownership

- [ ] Reject incomplete/invalid method, path and return metadata deliberately with
      zero dispatch; state construction/planning versus logical-call failure timing.
- [ ] Cover inherited methods, concrete generics, Mono/Flux grammar, URI templates,
      cache identity and method/API-ref/client timeout precedence.
- [ ] Retain delegated parser and API-ref alternatives, existing valid metadata and
      source/binary accessors. Document any earlier invalid-input failure.
- [ ] Verify plan reuse and concrete-client isolation without per-subscription
      parsing or retention of invocation arguments/context in metadata.

### [ ] 4.3 Verify the public extension boundary

- [ ] Prove construction and invocation from a consumer outside starter packages
      using only supported public APIs.
- [ ] Check relevant contract export, mock and AOT validation with explicit limits;
      do not impose factory-only guarantees on lower-level entry points.
- [ ] Record focused results and cost-review triggers for Priority 8. Stop for
      a public-type promotion, breaking setter change or broader parser redesign.

## Priority 5 - AOT and Runtime Properties Selection Parity

Conditional on **V32-F003** approval.

### [ ] 5.1 Select the effective properties

- [ ] Turn the paired runtime/AOT drift cases into desired-behavior regressions
      while preserving their baseline provenance.
- [ ] Prefer supported Spring candidate resolution at the properties boundary;
      remove registration-order fallback rather than duplicating precedence rules.
- [ ] Preserve environment binding lifecycle, primary programmatic properties and
      foreign-factory behavior; do not select an unbound configuration object.

### [ ] 5.2 Cover selection and failure semantics

- [ ] Cover primary, non-fallback, priority, default candidate, absence and ambiguity
      against the runtime selection oracle.
- [ ] Exercise parent/child shadowing, lazy/prototype and FactoryBean shapes touched
      by the actual lookup; label untested shapes explicitly.
- [ ] Prove invalid selected configuration fails instead of choosing a valid inactive
      bean or falling back to environment/default values.
- [ ] Count prototype/product materializations to detect duplicate creation and
      discarded configuration instances.

### [ ] 5.3 Preserve build-time ownership

- [ ] Assert properties/metadata creation permissions separately from zero business
      client, transport, cache-manager and signer creation during selection.
- [ ] Retain non-instantiating diagnostics and supported unknown values; do not
      reuse the eager AOT path for inspection.
- [ ] Run environment-only, replacement-properties and foreign-factory controls;
      record focused results, remaining constraints and rollback assessment.

## Priority 6 - Cross-Path Contract and Ownership Regressions

### [ ] 6.1 Compare effective decisions across entry points

- [ ] Build an affected-path matrix for factory/public handler, metadata/plan,
      contract export, diagnostics, mocks and AOT for selected corrections.
- [ ] Record agreement on known facts and intentional validation/creation differences;
      keep foreign client implementations outside starter-only grammar.
- [ ] Exercise a composed cache-selected extension scenario using applicable selected
      fixes and existing workarounds for deferred IDs; do not implicitly fix them.

### [ ] 6.2 Preserve request and caller boundaries

- [ ] Assert finalized method/target, selected policy, key/wire identity and cache
      result for the composed case with properly classified customizers.
- [ ] Verify per-caller auth/gates still run on hits and probes do not dispatch.
- [ ] Run affected retry/auth-replay/redirect/deadline/terminal regressions; preserve
      published semantics without reopening unrelated engine implementation.

### [ ] 6.3 Retain construction and cleanup safeguards

- [ ] Recheck failed construction and successful ownership transfer using registry
      leases/owners, not only an unassigned factory field.
- [ ] Verify destroy/recreate beside a live same-tag meter owner and application-owned
      resources remaining usable.
- [ ] Run deterministic F004/F005 ordinary safeguards with explicit GC disabled;
      use and report the controlled lane separately for reachability claims.
- [ ] Record cross-path outcomes and scope limits without claiming universal
      shutdown, collectability, memory or concurrency guarantees.

## Priority 7 - Mock, Assembled-Consumer, AOT and Native Evidence

### [ ] 7.1 Verify mock and assembled consumers

- [ ] Exercise accepted extensions through the mock/public helper where supported;
      distinguish helper behavior from Spring selection and transport evidence.
- [ ] Run external consumers against assembled artifacts with counted method/target
      and decoded-result witnesses for selected corrections.
- [ ] Keep optional-dependency absence physical: cache-disabled without Caffeine and
      enabled-only resilience without selected operators/registry classes.
- [ ] Preserve artifact-only classpaths, effective POMs, dependency trees and actual
      counts; do not relabel installed reactor results as Central consumption.

### [ ] 7.2 Verify supported Boot and JVM AOT paths

- [ ] Run both supported Boot rows, including genuine matching consumer parents.
      Track overlays or exact generator commands required to reproduce them.
- [ ] Add/run JVM AOT witnesses for the selected builder, metadata and properties
      cases with invalid-selected and non-instantiation controls.
- [ ] Confirm actual selected programmatic policy and applicable reflection hints;
      retain environment and optional-integration absence cases.

### [ ] 7.3 Compile and run native evidence

- [ ] Commit the final fixture and implementation before release-quality native
      measurement; record the clean reachable source and toolchain/resources.
- [ ] Compile and execute native witnesses for selected corrections, counting all
      relevant dispatches and checking method/target/result and lifecycle behavior.
- [ ] Retain command, exit status, binary hash, actual assertions and prior failures;
      JVM processor passes or stale native binaries cannot satisfy this gate.
- [ ] Rerun after relevant source changes. Resource failures leave required native
      work pending; do not weaken assertions to close the priority.

## Priority 8 - Compatibility and Targeted Cost Evidence

### [ ] 8.1 Freeze the supported surface

- [ ] Inventory public APIs/constructors/accessors, metadata/configuration semantics,
      optional dependencies and module packaging affected by the accepted diff.
- [ ] Review removed redundant configuration, earlier metadata errors and corrected
      AOT preference separately from source/binary compatibility.
- [ ] Preserve valid workarounds and defaults; stop for a newly required public
      break, new SPI, dependency upgrade or broader behavior change.

### [ ] 8.2 Run compatibility and regression lanes

- [ ] Run strict root and independent starter source/binary API checks against
      published `4.4.1` with isolated repositories and artifact provenance.
- [ ] Run focused affected suites and full module regressions; preserve optional
      integration, binary/source/Javadoc and generation-packaging checks.
- [ ] Record exact final-source results and failures; reuse Priority 7 evidence only
      when its revision and scope still match. Missing gates remain visible.

### [ ] 8.3 Assess targeted cost without inventing claims

- [ ] Inspect the actual diff for construction-only, AOT-only, planning and hot-path
      changes; record the measurement decision before running benchmarks.
- [ ] If static planning/invocation allocation changes, set regression criteria and
      measure identical cold-plan and warm-call workloads against `4.4.1`.
- [ ] Include correctness witnesses, fork/warmup/allocation configuration, variance,
      source/toolchain/report hashes and comparable baseline rows.
- [ ] Record a justified no-benchmark disposition when applicable; do not imply a
      speed, RSS, cache-throughput or native-startup gain from source inspection.

## Priority 9 - Maintainer, Migration and Operations Guidance

### [ ] 9.1 Document delivered extension behavior

- [ ] Update customizer, replacement-bean, cache and native guidance only for
      accepted corrections, with release-scoped availability.
- [ ] Provide public-API examples with complete dependency, customization-safety
      and configuration prerequisites.
- [ ] Keep published `4.4.1` workarounds distinct from current-source behavior;
      do not prescribe blanket SAFE or reflection into internal metadata.

### [ ] 9.2 Preserve operational boundaries

- [ ] Explain runtime/AOT creation permissions, diagnostic unknowns, application
      ownership and factory recreation instead of unsupported live mutation.
- [ ] Document classification, incomplete-metadata, ambiguity and invalid-selected
      failures using bounded structural evidence without sensitive request data.
- [ ] Identify deferred IDs, workaround, owner and trigger; link new results to V32
      findings without rewriting their historical outcomes.

### [ ] 9.3 Validate guidance and result records

- [ ] Reconcile versioned examples, roadmap/checklist/index and readiness status
      with actual implementation and release state.
- [ ] Run documentation/link/fixture tests and applicable example validation; record
      actual counts and failures rather than copying earlier totals.
- [ ] Record delivered scope, compatibility/migration notes and limitations in V33;
      leave unrelated operational and dashboard contracts unchanged.

## Priority 10 - Scope Decision and Conditional Release Go/No-Go

### [ ] 10.1 Reconcile implementation scope and release intent

- [ ] Match approved IDs to delivered corrections, test evidence and explicit
      deferrals; resolve accepted blockers or obtain their formal scope removal.
- [ ] Obtain the maintainer decision for review-only/no-release or release preparation.
      Evaluate a compatible patch first; `4.5.0-SNAPSHOT` does not select a minor.
- [ ] Document a no-go when required evidence is missing. Open work remains open
      unless a deliberate no-release/no-go closure with disposition is approved.

### [ ] 10.2 Select the exact candidate or no-release branch

- [ ] For a release, approve the exact candidate after compatibility and migration
      review; no unapproved new API/behavior may enter this branch.
- [ ] Update reactor/modules/fixtures, version guards, matrix commands, changelog
      and readiness together. Keep `4.4.1` published baselines until publication
      is verified; leave V33 active through the release cut.
- [ ] For no release, record the rationale and disposition of any implementation;
      mark candidate/signing/publication tasks explicitly not applicable.
- [ ] Record the selected branch without treating candidate preparation as GO or
      publication, and preserve historical version evidence.

### [ ] 10.3 Assemble immutable release or review evidence

- [ ] Inventory final reachable source, scope/decision records and all required
      correctness/API/consumer/Boot/AOT/native/cost/guidance results.
- [ ] Seal commands, actual totals, toolchains, effective dependencies, reports and
      artifact hashes. Keep failed/partial attempts and remaining limitations.
- [ ] Revalidate relevant evidence after final source/fixture/coordinate changes;
      document exact reuse rather than relabeling an older run.
- [ ] For a release, verify packaging and applicable unsigned/staged checks; keep
      signing, tag publication and Central verification separate and pending until run.

### [ ] 10.4 Verify publication or no-release closure

- [ ] For the approved release, verify local/workflow signing and staged signatures,
      version-matched tag/workflow outcome and remotely published Central artifacts.
      Authorize signing locally; never place passphrases in evidence.
- [ ] Verify published assembled consumption from an isolated repository, preserving
      versions, signatures/hashes and provenance rather than reactor-output leakage.
- [ ] Alternatively, record an explicitly approved no-release/no-go closure with
      remaining work and no claim of publication; mark unused release gates N/A.
- [ ] Only then close V33 consistently in roadmap/checklist/index/readiness; advance
      baselines only to a verified publication and leave future scope unselected.
- [ ] Record the closure revision, decision and artifact/evidence links. No pending
      required work may be presented as completed release evidence.

## Completion Criteria

- [ ] Scope was explicitly selected before production edits, or review-only closure
      was approved with candidate findings still honestly classified.
- [ ] Selected corrections have baseline and desired-behavior evidence from supported
      external entry points, without weakening safety or introducing unapproved APIs.
- [ ] Cross-path, optional-integration, ownership and F004/F005 guarantees remain
      intact; deferred gaps and intentional differences are explicit.
- [ ] Required compatibility/consumer/Boot/AOT/native/cost evidence identifies the
      final tested source, with failures and inapplicable work clearly separated.
- [ ] Guidance and migration notes distinguish published versions, delivered changes
      and workarounds without sensitive evidence.
- [ ] Release or no-release closure is verifiable and archive/readiness state agrees.
