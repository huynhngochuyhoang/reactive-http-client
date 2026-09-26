# Reactive HTTP Client - Roadmap V33 Execution Checklist

> **Status:** active
> **Published baseline:** `4.4.1`
> **Development coordinate:** `4.5.0-SNAPSHOT`
> **Implementation scope:** V32-F001 + V32-F002 + V32-F003 approved and implemented; shared verification pending
> **Release scope:** unselected
> **Adopted:** 2026-09-19

Execution companion to [`ROADMAP.md`](ROADMAP.md). Adoption starts baseline and
characterization work; it does not complete Priority 1, select F001-F003 for
implementation, authorize publication, or reopen V32. V1-V32 remain completed
release records. V32-F004/F005 are delivered safeguards, not new feature work.

Priority 2.3 approval is recorded in [FIX-DECISION.md](FIX-DECISION.md).
All three corrections are selected for Priorities 3-5; none is implemented by
the reproduction/decision work. Priority 3 records the F001 correction and its
verification separately. Release scope remains unselected.

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

### [x] 1.1 Align adoption and version state

- [x] Verify reactor/module/current-consumer/native/benchmark coordinates remain
      `4.5.0-SNAPSHOT` and public/API/consumer/benchmark baselines remain `4.4.1`.
- [x] Verify roadmap, checklist, index and archive guard report active V33 while
      V1-V32 remain completed; no release or implementation scope is selected.
- [x] Verify generated readiness uses checklist lifecycle, stays active through
      a final-version cut, and keeps the current planned final version unset.
- [x] Preserve historical V1-V32 and proposal content; no adoption-only dependency,
      production API, configuration or schema change.

### [x] 1.2 Establish baseline provenance

- [x] Record reachable source/release tag, toolchain, settings and clean/dirty state.
      Identify which V32 publication and compatibility results remain reusable.
- [x] Verify the published parent/module artifacts and assembled `4.4.1` consumer
      through isolated Central provenance, or explicitly revalidate and label exact
      reused evidence. Do not substitute a reactor install for published consumption.
- [x] Retain artifact hashes, versions, effective POMs, dependency trees, classpaths
      and actual test totals; distinguish fresh results from prior observations.
- [x] Confirm the Java 21 and existing Boot 4.0.0/4.1.0 lanes without upgrading them.

### [x] 1.3 Freeze characterization scope

- [x] Inventory F001-F003, their original reproductions, workarounds and missing
      evidence from V32; retain original finding IDs.
- [x] Identify F004 cleanup and F005 controlled-test safeguards that must remain
      intact, without treating their fixes as new V33 work.
- [x] Record exclusions and baseline conclusions, then run documentation,
      archive/readiness and applicable version guards with actual results.

Baseline and scope record: [BASELINE-SCOPE.md](BASELINE-SCOPE.md).
Evidence: `target/release-evidence/v33/priority1/`. Published/matrix results
are revalidated V32 evidence, not new Central downloads or runtime executions.
Implementation and release scope remain unselected; Priority 2.3 is still required.

**Completed 2026-09-23.** Reviewed clean source
`66e8b7689e16f9393c158879fac9317de47895e7`; fresh documentation results use
that source plus this recorded baseline/test patch. Oracle JDK 21.0.8, Maven
3.9.9 and Central-only settings; coordinates remain unchanged.

| Verification | Actual result |
|---|---|
| Documentation/archive/readiness guards | 69 tests, zero failures/errors/skips; initial missing-record red test retained separately |
| Maven reactor `validate` | Passed |
| Published-baseline provenance fixtures | Passed, including rejected local/mismatched/missing artifacts and root/module self-comparison |
| API compatibility fixtures | Passed additive/defaulted annotation controls and expected source/binary-breaking rejections; not a new strict project comparison |
| Reused publication evidence | 477-file bundle verified; 13 artifact hashes/versions/Central markers and release-tag sources rechecked; original four baseline and 28 full-profile consumer cases recounted |
| Reused supported lanes | 692-file compatibility bundle and 25-file genuine Boot 4.1 consumer bundle verified; original 1,928 module tests per Boot row and 28 overlay cases recounted |
| Syntax, unchanged scope and whitespace | Passed; V1-V32/proposals/production/POMs untouched |

The baseline record links exact commands, integrity anchors, source applicability
and limitations. No fresh Central download, assembled-consumer execution, full
reactor test suite, matrix, native build, benchmark or release decision is claimed.

## Priority 2 - Reproduction and Explicit Fix Selection

### [x] 2.1 Reproduce the three candidate gaps

- [x] Reproduce F001 through bean-factory and ApplicationContext entry points using
      the same starter builder and properly classified application customizations.
- [x] Reproduce F002 with fresh static public metadata from an external parser;
      keep delegated parsing and API-ref construction as passing controls.
- [x] Reproduce F003 with paired runtime/AOT primary, non-fallback, priority and
      default-candidate properties selection, including the existing primary control.
- [x] Record construction, selection and dispatch witnesses separately. Preserve
      baseline outcomes and fixture failures before changing defect expectations.

### [x] 2.2 Bound alternatives and acceptance

- [x] For each ID, document affected consumer need, workaround cost, no-change
      alternative and smallest correction within the existing owner.
- [x] Specify API/configuration/behavioral compatibility, negative tests, creation
      permissions, dependencies, verification budget and rollback conditions.
- [x] Name unsupported or untested shapes rather than inferring universal Spring,
      native, parser or builder compatibility from representative fixtures.
- [x] Reject new public abstractions or broader refactors not required by the
      reproduced gap; record any need for a separate proposal.

### [x] 2.3 Record the maintainer scope decision

- [x] Obtain explicit approval of selected finding IDs, or a review-only decision.
      Proposed F001 + F002 + F003 is not approval merely because it is listed.
- [x] Record date, rationale, bounded changes, acceptance/verification requirements
      and stop conditions. Identify the independent implementation order.
- [x] Mark unselected candidate priorities not applicable with their deferral,
      workaround, owner and reconsideration trigger; do not claim a fix.
      **Not applicable, 2026-09-23:** all three IDs were explicitly selected;
      Priorities 3-5 remain open, not deferred or complete.
- [x] Update V33 implementation status consistently without selecting a release.
      Stop production work until this decision exists.

Decision and acceptance: [FIX-DECISION.md](FIX-DECISION.md).
On 2026-09-23 the maintainer selected **F001 + F002 + F003**. The fixes are
independent; execute F001, F002, then F003 in checklist order. No new SPI,
general resolver, public internal type, version bump or release was approved.
Evidence: `target/release-evidence/v33/priority2/`; reviewed clean source
`9fd20c3a069ebcc101f8b314d33bb69f0afcf969`.

**Completed 2026-09-23.** Oracle JDK 21.0.8, Maven 3.9.9, Boot 4.0.0 and
Central-only settings. Freshly rebuilt reactor artifacts were installed into a
reused dependency repository; this is not published-artifact consumption.

| Verification | Actual result |
|---|---|
| External extension reproduction and controls | 17 tests, zero failures/errors/skips; F001/F002 remain asserted defects, delegated/API-ref and gate controls pass |
| Paired runtime/AOT properties reproduction | Four tests, zero failures/errors/skips; primary passes both, three non-primary cases retain the recorded drift |
| Consumer provenance | Actual Surefire and exported classpaths use rebuilt starter/helper/OTel JARs with matching hashes, not reactor classes; effective POM, tree and Boot version retained |
| Documentation/archive/readiness guards | 70 tests, zero failures/errors/skips after updating two stale scope assertions; initial failing log/XML preserved |
| Scope and whitespace | Passed; production, runtime fixtures, coordinates, dependencies and V1-V32/proposals unchanged |

The 21 reproduction cases ran against the clean reviewed source before tracked
edits. Documentation verification uses the recorded scope/guard patch. The
decision freezes alternatives, negative controls, creation permissions, later
verification budget and rollback conditions. No API/native/matrix/performance
result for a future correction is claimed.

## Priority 3 - Starter Builder Ownership and Cache-Safety Validation

Conditional on **V32-F001** approval.

### [x] 3.1 Correct ownership lookup locally

- [x] Add desired-behavior regressions for context versus owning bean-factory lookup,
      retaining a reproducible pre-fix baseline.
- [x] Resolve the actual owning definition through existing factory/context helpers;
      do not exempt by bean name, factory-method name or SAFE label alone.
- [x] Preserve conservative rejection of unknown ownership and existing explicit
      SAFE declarations; do not create lazy components just to inspect ownership.

### [x] 3.2 Preserve application customization safety

- [x] Cover inherited builders, child shadowing, application replacement builders
      and same-named definitions with both positive and negative controls.
- [x] Keep applicable Boot/per-client customizers classified, including filters,
      defaultRequest, exchange functions, connector and other builder mutations.
- [x] Count lazy/prototype/product creation where relevant to prove inspection
      does not materialize application components.
- [x] Keep cache auth, request variants and per-caller gates enforced independently
      of the starter-builder exemption.

### [x] 3.3 Verify affected entry points

- [x] Prove the external E12 consumer no longer needs the redundant starter-builder
      classification through runtime, AOT and inspection paths as applicable.
- [x] Prove unclassified application behavior still fails and prior valid SAFE
      configurations remain accepted.
- [x] Record changed behavior, focused tests and rollback assessment. Stop for
      scope review if the fix requires weakening ownership proof or the inventory.

Completed on 2026-09-23. [BUILDER-OWNERSHIP.md](BUILDER-OWNERSHIP.md) records the
bounded F001 correction, reproducible red/green tests, hierarchy and creation
controls, commands and rollback assessment. Source is the reviewed working-tree
patch on reachable `0c2daf13ef2b8409ecfb58021b432d86a3288ce0`, not a clean release
commit. Evidence is under `target/release-evidence/v33/priority3/`.

- Pre-fix: 12 ownership cases, three expected failures; first corrected run: 12 passed.
- Final focused starter run: 321 passed, including 13 final ownership cases.
- Assembled external consumer: 18 passed; mock helper controls: 75 passed.
- Documentation/archive/readiness guards: 71 passed; the initial stale-name
  assertion failure is preserved with its log/XML.
- All passing runs have zero failures/errors/skips and disable explicit GC.

No application customization is automatically classified SAFE. The published
4.4.1 workaround is retained; the development guide describes the correction.
F002/F003, final cross-path/API/matrix/native gates and release selection remain
pending in their own priorities.

## Priority 4 - Public Static Metadata and Effective Request Planning

Conditional on **V32-F002** approval.

### [x] 4.1 Define and implement static derivation

- [x] Add an external fresh-public-metadata regression that fails before the fix
      and asserts actual method/target/result afterward.
- [x] Derive missing static effective API at the existing validated planning boundary
      without reflection, package relocation or making EffectiveApi public.
- [x] Define supplied-derived-value versus public-field behavior and preserve
      built-in/static/API-ref precedence without another mutable decision model.

### [x] 4.2 Preserve grammar and plan ownership

- [x] Reject incomplete/invalid method, path and return metadata deliberately with
      zero dispatch; state construction/planning versus logical-call failure timing.
- [x] Cover inherited methods, concrete generics, Mono/Flux grammar, URI templates,
      cache identity and method/API-ref/client timeout precedence.
- [x] Retain delegated parser and API-ref alternatives, existing valid metadata and
      source/binary accessors. Document any earlier invalid-input failure.
- [x] Verify plan reuse and concrete-client isolation without per-subscription
      parsing or retention of invocation arguments/context in metadata.

### [x] 4.3 Verify the public extension boundary

- [x] Prove construction and invocation from a consumer outside starter packages
      using only supported public APIs.
- [x] Check relevant contract export, mock and AOT validation with explicit limits;
      do not impose factory-only guarantees on lower-level entry points.
- [x] Record focused results and cost-review triggers for Priority 8. Stop for
      a public-type promotion, breaking setter change or broader parser redesign.

Completed on 2026-09-23. [STATIC-METADATA.md](STATIC-METADATA.md) records F002's
missing-value derivation, retained supplied/API-ref precedence, earlier deliberate
fresh-input validation, lower-level limits, commands and Priority 8 cost triggers.
Source is the reviewed working-tree patch on reachable
`f0c3e167fc73f1ba41a7da769cf275a93e1a99e2`, not a clean release commit.
Evidence is under `target/release-evidence/v33/priority4/`.

- Pre-fix external desired-behavior test: one NullPointerException at invocation.
- Final focused starter run: 381 passed, including 25 new planning cases.
- Assembled external consumer: 18 passed; mock helper controls: 76 passed.
- Documentation/archive/readiness guards: 72 passed.
- Passing runs have zero failures/errors/skips and disable explicit GC; initial
  fixture compilation/assertion errors are retained, not counted as passing work.

EffectiveApi stays internal; public signatures, dependencies and coordinates
are unchanged. No per-subscription parsing or new metadata state is added.
F003, shared full-suite/API/matrix/native/cost gates and release selection remain
pending in their own priorities. Earlier Priority 2/3 evidence retains its dated
pre-F002 status.

## Priority 5 - AOT and Runtime Properties Selection Parity

Conditional on **V32-F003** approval.

### [x] 5.1 Select the effective properties

- [x] Turn the paired runtime/AOT drift cases into desired-behavior regressions
      while preserving their baseline provenance.
- [x] Prefer supported Spring candidate resolution at the properties boundary;
      remove registration-order fallback rather than duplicating precedence rules.
- [x] Preserve environment binding lifecycle, primary programmatic properties and
      foreign-factory behavior; do not select an unbound configuration object.

### [x] 5.2 Cover selection and failure semantics

- [x] Cover primary, non-fallback, priority, default candidate, absence and ambiguity
      against the runtime selection oracle.
- [x] Exercise parent/child shadowing, lazy/prototype and FactoryBean shapes touched
      by the actual lookup; label untested shapes explicitly.
- [x] Prove invalid selected configuration fails instead of choosing a valid inactive
      bean or falling back to environment/default values.
- [x] Count prototype/product materializations to detect duplicate creation and
      discarded configuration instances.

### [x] 5.3 Preserve build-time ownership

- [x] Assert properties/metadata creation permissions separately from zero business
      client, transport, cache-manager and signer creation during selection.
- [x] Retain non-instantiating diagnostics and supported unknown values; do not
      reuse the eager AOT path for inspection.
- [x] Run environment-only, replacement-properties and foreign-factory controls;
      record focused results, remaining constraints and rollback assessment.

Initially completed on 2026-09-24; binding-lifecycle provenance reopened and repaired
within the documented lifecycle boundaries on 2026-09-26.
[AOT-PROPERTIES-SELECTION.md](AOT-PROPERTIES-SELECTION.md)
records Spring-owned selection, Boot binding, creation counts, failure semantics,
commands and remaining gates. Evidence is under
`target/release-evidence/v33/priority5/`, against the reviewed patch on reachable
`a81447c85739d375d8b1b32fffeae8c2df37dcc3` (the committed Priority 4), not a clean
release revision.
The pre-fix four-case desired-behavior run had three expected selection errors;
the original V32 and V33 Priority 2 records remain historical provenance.
Initial completion runs: 264 focused starter cases (including 27 new cases), 18 assembled
consumer cases and 73 documentation/archive/readiness cases; **355 passed**,
zero failures/errors/skips, with explicit GC disabled. The companion record
labels prior fixture failures, binding constraints and consumer coverage; the
sealed bundle records consumed artifact hashes and actual classpath provenance.
Diagnostics, public APIs, runtime routing, dependencies and coordinates are
unchanged. Shared cross-path, full-suite/API/matrix/native/cost gates in
Priorities 6-8 and release selection remain pending.

Binding-order review correction, 2026-09-24: the selected definition-backed
properties bean is bound even when an earlier AOT processor resolved its
singleton. Definition-less direct registrations, FactoryBean products and the
normal runtime binding pass remain excluded. A direct registration sharing a
properties definition's name follows that definition's binding contract.
The companion record supersedes the original singleton-presence assumption.
Follow-up evidence is under `target/release-evidence/v33/priority5-binding-order/`;
the pre-fix four-case run failed in both cache-selected and ordinary-client forms.
The nine-class focused rerun passes **271 cases**, including 34 selection cases;
the documentation rerun passes **73 cases**, all with zero failures/errors/skips
and explicit GC disabled. Original assembled/native scope is unchanged.

Scoped-proxy review correction, 2026-09-24: Spring scoped proxies now resolve and
bind their target using its definition metadata, and AOT validates that same
instance (including prototype targets). Ordinary FactoryBean products remain
excluded. Nine new cases cover scoped/prototype targets, alternate binding
prefixes, invalid configuration, early creation, runtime binding and parent
ownership, with counted creation and zero business-resource assembly.
Follow-up evidence: `target/release-evidence/v33/priority5-scoped-binding/`;
**280 focused cases** and **73 documentation cases** pass, zero failures/errors/
skips, explicit GC disabled. Four pre-fix errors and the initial fixture compile
error are retained. The companion record states scope availability and remaining
consumer/API/matrix/native limitations; no release gate is closed by this rerun.

Creation-time binding review correction, 2026-09-24: initialized scoped-proxy
target-source metadata supports `@Bean` and supplier-configured targets without
a definition property. A temporary properties-only callback binds new beans
before initialization and is removed on success or failure; fallback binding
of earlier-created objects does not replay their initialization. Nine new cases
cover one binding pass, three initialization callbacks, programmatic proxy forms
and cleanup after binding/init errors. Follow-up evidence under
`target/release-evidence/v33/priority5-creation-binding/` records three pre-fix
errors, **289 focused cases** (52 selection cases) and **73 documentation cases**
passing with explicit GC disabled, zero failures/errors/skips. The companion
record retains earlier provenance and the pending consumer/API/matrix/native gates.

Awareness-order review correction, 2026-09-24: temporary binding follows context
awareness and precedes AOT's merged-definition/init processors. Existing lifecycle
cases now require injected environment and application context during binding,
matching normal refresh for ordinary and programmatic scoped-proxy properties.
Evidence under `target/release-evidence/v33/priority5-awareness-binding/` retains
three pre-fix errors and **289 focused / 73 documentation cases** passing, zero
failures/errors/skips, explicit GC disabled. Cleanup, earlier provenance and
pending consumer/API/matrix/native gates remain unchanged.

Ordinary-processor review correction, 2026-09-24: the temporary binder now precedes
ordinary application post-processors as well as init callbacks, while preserving
Spring awareness infrastructure and higher-priority regular processors. Six new
runtime/AOT ordering cases cover ordinary properties and both programmatic proxy
forms. Evidence under `target/release-evidence/v33/priority5-ordinary-processors/`
retains three pre-fix errors and **295 focused / 73 documentation cases** passing,
zero failures/errors/skips, explicit GC disabled. The companion record explains
the insertion boundary and retains prior evidence and pending release gates.

Opaque scoped-proxy review correction, 2026-09-24: when public proxy/definition
metadata is unavailable, AOT reads the target name from the already-initialized
singleton scoped factory without recreating it or changing proxy opacity. The
companion record documents this build-time Spring field dependency and the
definition-metadata requirement for non-cached opaque factories. Thirteen added
cases cover opaque registration forms, target/binding counts, callback ordering,
failure cleanup, early/normal targets and parent ownership. Separate evidence in
`target/release-evidence/v33/priority5-opaque-scoped-binding/` records two pre-fix
errors among five cases, then **308 focused / 73 documentation cases** passing,
zero failures/errors/skips, explicit GC disabled. Remaining release gates are unchanged.

Direct-registration/target-alias correction, 2026-09-25: temporary binding now
preserves the directly installed processor prefix regardless of ordering interfaces,
while remaining before auto-detected ordinary processor beans. Cache-only identity
inspection includes singleton FactoryBean products without creating them. Scoped
target aliases are canonicalized before definition lookup and binding, so early
targets retain their binding metadata without recreation. Twelve runtime/AOT
ordering cases and two alias-chain cases cover the correction. Evidence under
`target/release-evidence/v33/priority5-registration-alias/` preserves eight initial
pre-fix errors and **322 focused / 73 documentation cases** passing, zero
failures/errors/skips, explicit GC disabled. The companion record documents the
build-time cached-product accessor dependency; release gates remain unchanged.

Product/owner/wrapper correction, 2026-09-25: cached processor products with null
or non-processor exposed types retain direct-registration ordering; unrelated
child aliases no longer rewrite parent-selected names. Creation-time binding is
tracked by canonical name for fallback decisions so later wrappers are not
rebound, while separate prototypes still bind individually. Thirteen added cases
cover these paths. Evidence under
`target/release-evidence/v33/priority5-product-owner-binding/` retains the initial
13-case run (five failures, five errors, three controls) and **335 focused / 73
documentation cases** passing, zero failures/errors/skips, explicit GC disabled.
Earlier bundles and remaining release gates are unchanged.

Stable processor ordering review, 2026-09-25: equal-priority regular processor
beans now retain their type-discovery registration order relative to Boot's binder.
Six paired runtime/AOT controls do not reproduce the ordinary bean-backed direct
prefix finding: Spring removes the earlier singleton occurrence and re-registers
it in auto-detected order. Four tie-order cases cover definitions registered before
and after the binder. Evidence in
`target/release-evidence/v33/priority5-stable-processor-order/` records two initial
failures among ten cases, followed by **345 focused / 73 documentation cases**
passing, zero failures/errors/skips, explicit GC disabled. The companion record
qualifies the tested runtime baseline; remaining release gates are unchanged.

Registered-binder/dual-role/scoped-target correction, 2026-09-25: insertion uses
the actual registered binding processor's order and binding callback. Only names
returned by Spring's processor discovery classify cached identities as auto-detected,
preserving a directly installed factory when only its separate product is discovered.
Resolved scoped targets use by-name ownership, including broad predictions and
parent aliases. Twelve added cases retain lifecycle/creation/ownership controls.
Evidence in `target/release-evidence/v33/priority5-custom-binding-targets/` separates
fixture setup iterations from the corrected ten-case pre-fix run (six errors),
then records **357 focused / 73 documentation cases** passing, zero failures/errors/
skips, explicit GC disabled. The companion record qualifies the broad-target fixture;
remaining release gates are unchanged.

Factory-identity/binder-result correction, 2026-09-25: dereferenced properties
factories retain their selected instance while definition/binding metadata uses
the canonical name without `&`. Ordinary properties and scoped targets implementing
`ScopedObject` bind; only scoped proxy products are excluded. Binder replacements,
proxies and null results propagate through the creation callback; early fallback
uses returned replacements for validation without registry replacement or init replay.
Thirteen added cases retain paired runtime checks and ownership controls. Evidence
in `target/release-evidence/v33/priority5-binding-identity/` records the initial ten
cases (two failures, seven errors, one control), then **370 focused / 73 documentation
cases** passing, zero failures/errors/skips, explicit GC disabled. Remaining
consumer/API/matrix/native and release gates are unchanged.

Binder-discovery/comparator correction, 2026-09-25: installation is skipped only
for the exact standard registered binding processor, not an unrelated binder
subclass. Predictive type checks preserve FactoryBean processor registration groups;
the configured dependency comparator orders the priority group, with standard
fallback ordering and stable discovery-order ties. Ten new paired runtime/AOT cases
cover direct/named observer binders, custom comparator precedence, and ordinary/
ordered predictions for priority-ordered products. Evidence in
`target/release-evidence/v33/priority5-processor-discovery/` records ten initial
AOT errors after passing runtime checks, then **380 focused / 73 documentation
cases** passing, zero failures/errors/skips, explicit GC disabled. Ownership and
processor-restoration checks remain active; consumer/API/matrix/native and release
gates are unchanged.

Non-singleton/non-eager correction, 2026-09-25: uniquely typed non-singleton
processors retain discovery ordering without replacement creation; ambiguous type
matches fail explicitly. Properties and metadata selection use a short-lived
non-eager Spring selection view with owner-delegated creation. Raw properties
factories need predictable product-type metadata or prior initialization. Fourteen
added cases include explicit Spring dependency/Boot advisor discovery boundaries,
which this correction does not suppress. Evidence in
`target/release-evidence/v33/priority5-non-eager-selection/` records ten initial
failing cases and intermediate boundary probes, then **394 focused / 73 documentation
cases** passing, zero failures/errors/skips, explicit GC disabled. The companion
record qualifies the limits; consumer/API/matrix/native and release gates are unchanged.

Aliased-binder/broad-product correction, 2026-09-26: comparator ties use the
registered delegate's discovered definition name even when Boot's standard name
is an alias. Broad non-singleton processor predictions now fail explicitly before
binding rather than being misclassified as direct registrations; use a singleton
or unique concrete product type. Ten added paired runtime/AOT cases cover alias
order and ordinary/ordered interface/base predictions with ordinary/opaque-scoped
properties. Evidence in
`target/release-evidence/v33/priority5-aliased-binder-products/` retains the fixture
iteration, **eight pre-fix failures among fourteen cases**, then **404 focused /
73 documentation cases** passing with zero failures/errors/skips and explicit GC
disabled. Product/resource ownership and processor-chain restoration are checked;
consumer/API/matrix/native and release gates are unchanged.

Mixed-chain/creation-failure correction, 2026-09-26: direct-only processors across
the whole installed chain precede rediscovered instances. Temporary ordering is
restored after successful/failed lookup while retaining processors registered
during it. Selected named-bean lookup failures retain their original cause and do
not trigger the environment fallback, including required same-type lookups.
Eighteen new cases cover runtime/AOT ordering, local/parent creation failures and
restoration. Evidence in
`target/release-evidence/v33/priority5-direct-chain-failures/` records **four failures
and six errors among twenty pre-fix cases**, then **422 focused / 73 documentation
cases** passing, zero failures/errors/skips, explicit GC disabled. Existing
non-singleton restrictions and framework discovery boundaries remain documented;
consumer/API/matrix/native and release gates are unchanged.

Installed-delegate/lifetime correction, 2026-09-26: fallback state is retained for
definition-backed singletons older than the delegate's canonical singleton entry.
Definition-less processors after Spring's merged-definition registration boundary
stay late. Restoration retains additions and does not resurrect removed callbacks.
Eighteen added cases extend local/parent/scoped binding, late-processing and removal
coverage. Evidence in
`target/release-evidence/v33/priority5-installed-binder-lifecycle/` records **eight
failures and eight errors among twenty pre-fix cases**, then **440 focused /
73 documentation cases** passing, zero failures/errors/skips, explicit GC disabled.
The companion record explicitly leaves arbitrary installation-history inference
unresolved; consumer/API/matrix/native and release gates remain pending.

- [x] Resolve lifecycle provenance when another AOT processor instantiates the
      binder before properties but installs it afterward; do not treat singleton
      registration order as proof of binding or close this gap from the rerun above.

Installed non-singleton/scoped-history correction, 2026-09-26: resolve an installed
binder's discovered name before requesting another instance. The internal
auto-configured lifecycle observer tracks properties and delegate identities weakly
from AOT refresh, so early custom-scoped targets and delegates created before their
installation no longer depend on singleton registration order. Repeated inspection
does not rebind an existing target or its final wrapper. Twenty added selection and
three tracker cases
cover non-singleton scopes/aliases, real custom scopes, auto-configuration wiring,
identity/replacement tracking and deterministic weak-reference cleanup. Evidence in
`target/release-evidence/v33/priority5-binding-lifecycle-tracker/` records **six
failures and two errors among ten pre-fix cases**, then **463 focused cases** passing,
zero failures/errors/skips, explicit GC disabled. The companion record qualifies
manual contexts without the observer, earlier callbacks and arbitrary chain mutation;
shared consumer/API/matrix/native and release gates remain pending.
The final full starter rerun passes **2,062 cases** (including **73 documentation
cases**), zero failures/errors/skips, with explicit GC disabled. The bundle's
XML-derived audit and `SHA256SUMS` retain the final patch, including new source
files; focused/full/documentation counts overlap. No later checklist priority or
release gate is closed by this correction.

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
