# Reactive HTTP Client - Roadmap V32 Execution Checklist

> **Status:** active
> **Published baseline:** `4.4.0`
> **Development coordinate:** `4.5.0-SNAPSHOT`
> **Implementation scope:** V32-F004 + V32-F005 implemented; Priority 10 verification complete
> **Release scope:** unselected; review-only completion is valid
> **Adopted:** 2026-09-14

Execution companion to [`ROADMAP.md`](ROADMAP.md), developed from the
[architecture review proposal](../proposals/POST_4_4_ARCHITECTURE_REVIEW.md).
Adoption started the review; it did not complete Priority 1, confirm a defect,
authorize a refactor or select `4.5.0` for publication. Completion below requires
recorded evidence. V1-V31 and their release evidence remain unchanged.

Execute priorities in order. Reordering characterization work requires a recorded
dependency/risk reason. Production edits require Priority 8's explicit maintainer
decision first, including an expedited decision for a confirmed urgent defect;
urgency does not silently authorize unrelated architectural changes.

## Completion and Evidence Rules

- Check an item only after its work, verification and disposition are recorded
  under that priority. Link reusable evidence instead of copying its claims.
- Record commands, actual totals, toolchains, settings, reachable revision,
  dirty/clean state, artifact/report hashes and remaining limitations under
  `target/release-evidence/v32/priority<N>/`. Preserve failed and partial runs.
- Keep durable conclusions in source-controlled review records. Create files only
  when their evidence exists; a proposed filename is not a completed deliverable.
- Native and release-quality performance evidence must identify exact clean
  source and binary/report hashes. Do not use unavailable squash-local commits,
  reuse stale reports after code changes, or call discovery a measurement.
- A hypothesis is not a finding, absent coverage is not absent behavior, and a
  passing API comparison is not proof of behavioral compatibility.
- Conditional work may close as **not applicable** only with a dated decision
  naming the item, reason and evidence that makes the branch unnecessary. Label
  it explicitly beside the checked item; never imply an unrun check passed.
- Deferred findings require an owner or reconsideration trigger and named missing
  evidence. Deferring them is not implementing them. Review closure need not fix
  every finding, but accepted blocking items must be resolved or explicitly
  removed from scope before the final disposition.

## Execution Gates

| Gate | Requirement |
|---|---|
| Priorities 1-2 | Establish baseline, scope and as-is contracts before judging the architecture |
| Priorities 3-7 | Reproduce extension constraints and review decision, composition, ownership and module boundaries without speculative production refactors |
| Priority 8 | Obtain an explicit maintainer decision on finding IDs, alternatives, compatibility, dependencies, verification and rollback |
| Priority 9 | Implement only accepted work; review-only may make this priority not applicable |
| Priority 10 | Select verification by actual change risk; review-only still verifies new fixtures and documentation |
| Priority 11 | Publish evidence-backed guidance, including intentional limitations and no-change decisions |
| Priority 12 | Close review-only without requiring a release, or complete the separately selected release path |

The published/API/consumer/benchmark baseline stays `4.4.0`. Keep the development
coordinate `4.5.0-SNAPSHOT` until a recorded release-cut decision. An active
architecture review does not select implementation scope or a planned final
version. Do not relax validation, add public SPIs, split modules, change defaults
or swap dependencies merely to satisfy a review item.

## Required Invariants

| Surface | Preserve or explicitly review before changing |
|---|---|
| Public contracts | APIs, constructors, declarative grammar, configuration precedence and optional dependency behavior |
| Policy selection | Explicit opt-in and lifecycle-appropriate validation; unknown inspection facts do not become false |
| Request identity | One frozen selected representation for key and wire; finalized URI/headers, body bytes and auth partition remain aligned |
| Per-caller gates | Cache hits cannot bypass required authorization or applicable safe customization behavior |
| Caller and hidden work | Distinct deadlines, context, reporting, admission and resource ownership |
| Replay | Published retry, redirect and auth replay selection, repeatability and idempotency rules |
| Termination | No early capacity reuse during owned cleanup, lost discard, duplicate terminal record or post-terminal dispatch |
| Shutdown | Partial-construction cleanup, late acquisition handling and meter-owner teardown; application-owned work is identified separately |
| Observability | Cache-local work is not downstream traffic; structural privacy and unknown states survive backend absence |
| Evidence | No unverified leak, performance or mesh diagnosis and no sensitive request material in support artifacts |

## Review Records

The baseline, review maps, scenarios, findings and approved decision now exist.
The decision selects F004/F005 work, not delivered fixes or a release. Keep
detailed matrices in these records unless their size justifies a separate file.

| Record | Contents |
|---|---|
| [BASELINE-SCOPE.md](BASELINE-SCOPE.md) | Verified baseline, historical decision inventory and review coverage |
| [ARCHITECTURE-MAP.md](ARCHITECTURE-MAP.md) | Module, decision, composition and ownership maps with source/test references |
| [EXTENSION-SCENARIOS.md](EXTENSION-SCENARIOS.md) | External-consumer attempts, observations and supported/limited/gap classifications |
| [EFFECTIVE-POLICY-SELECTION.md](EFFECTIVE-POLICY-SELECTION.md) | Entry-point/lookup matrix, mutation boundaries and representative parity/drift evidence |
| [INVOCATION-COMPOSITION.md](INVOCATION-COMPOSITION.md) | Counted request/probe/replay paths, independent lifetimes and concrete change dependencies |
| [RESOURCE-OWNERSHIP.md](RESOURCE-OWNERSHIP.md) | Resource/terminal and nested-lock matrices, partial construction, external owners and retention limits |
| [MODULE-EVIDENCE-BOUNDARIES.md](MODULE-EVIDENCE-BOUNDARIES.md) | Mock/optional integration and creation matrices, native triggers, fixture/provenance gaps and test-environment limits |
| [FINDINGS.md](FINDINGS.md) | Four production gaps and one test-evidence gap; F004/F005 accepted, F001-F003 deferred with workarounds and triggers |
| [ARCHITECTURE-DECISION.md](ARCHITECTURE-DECISION.md) | Approved F004/F005 scope, alternatives, bounded acceptance/verification and rollback; Priority 10 verified, release unselected |
| [COMPATIBILITY-VERIFICATION.md](COMPATIBILITY-VERIFICATION.md) | Completed Priority 10 correctness/API/consumer/AOT/native evidence, including retained failed native attempts |
| [ACCEPTED-IMPROVEMENTS.md](ACCEPTED-IMPROVEMENTS.md) | F004 construction rollback, F005 preserved scenario inventory and controlled lane, focused evidence and remaining verification |

---

## Priority 1 - Post-`4.4.0` Baseline and V32 Scope Integrity

### [x] 1.1 Align published, development and execution state

- [x] Verify root/module and current-consumer/native/benchmark fixture coordinates
      remain `4.5.0-SNAPSHOT`, with no adoption-only version or dependency bump.
- [x] Verify public examples, published consumer, strict API and benchmark
      baselines remain `4.4.0`; include shipped V31 rows in baseline discovery.
- [x] Verify V32's roadmap/checklist/index and archive guard agree on active review;
      readiness keeps implementation/release scope unselected and no planned final
      version. Checklist adoption itself is not Priority 1 completion.
- [x] Preserve V1-V31 and proposal history; distinguish adopted design input from
      proposals that remain outside V32.

### [x] 1.2 Establish reproducible baseline evidence

- [x] Record the reachable reviewed source, release tag and clean/dirty state;
      identify which V31 artifacts and claims can be reused unchanged.
- [x] Verify all 13 parent/module POM, binary, source and Javadoc artifacts and
      assembled published consumption from isolated Central-only repositories;
      fresh verification or reused exact evidence must be identified explicitly.
- [x] Preserve versions, hashes, Central markers, consumer classpaths, effective
      POMs, dependency trees and actual test totals; reject reactor-output leakage.
- [x] Record Java/Boot support and existing API, native and benchmark evidence
      with exact provenance; do not present old runs as new V32 validation.

### [x] 1.3 Freeze review coverage, not implementation

- [x] Reconcile V1-V31 decisions and reports into delivered, superseded, relevant
      constraints and unverified hypotheses, without reopening proposal boxes.
- [x] List reviewed modules, extension needs and explicit exclusions; identify
      reported application cases separately from exploratory cases.
- [x] Create the baseline/scope record and finding-register structure, including
      evidence requirements and the Priority 8 maintainer decision gate.
- [x] Verify documentation links, archive/readiness consistency and applicable
      baseline guards; record actual results without selecting a release.

Completed on 2026-09-14. [BASELINE-SCOPE.md](BASELINE-SCOPE.md) records the
reachable clean starting commit `e284ced3219aa69c11e7a92405e8d949644127b8`,
release tag/commit, all V1-V31 dispositions, reported versus exploratory cases,
coverage and exclusions. [FINDINGS.md](FINDINGS.md) establishes stable IDs and
evidence/decision fields without inventing findings or approving implementation.

- Exact V31 artifact and consumer evidence was reused and revalidated, not
  freshly downloaded or rerun. Both sealed historical inventories passed;
  all 13 published artifacts, Central markers, POM/JAR versions and packaged
  main sources were checked. Four baseline and eleven full-profile published
  consumer cases retain passing XML, effective POMs, dependency trees and
  isolated artifact-only classpaths. The sealed fixture source archive is
  bridged to reachable `v4.4.0`; squash-local hashes are not treated as ancestors.
- Strict API, current/published discovery (34 cases each), native and Boot
  matrix evidence retains original source/toolchain provenance. Historical
  1,879-case matrix rows, native fixture/binary and benchmark source/report
  hashes were checked. No new API comparison, native build, matrix execution
  or timing/allocation measurement is claimed. V31's no-public-performance-claim
  disposition remains intact.
- Fresh Maven `validate` passed all four reactor projects. Published-baseline
  negative fixtures passed (Central provenance, missing/mixed artifacts,
  attachments, embedded versions and root/module self-comparison). API fixtures
  passed additive/defaulted-annotation cases and rejected source-only checked
  exceptions, removed constructors/methods/enum constants and incompatible
  report-only deltas.
- `DocumentationReleaseArtifactTest` passed **53 tests**, zero failures/errors/
  skips. It checks version/fixture alignment, V31 benchmark row inclusion,
  archive/adopted-proposal/readiness state and all new record links. Readiness
  remains active `v32`, snapshot development, release/candidate scope unselected
  and no planned final version. Script syntax and `git diff --check` passed.
- Toolchain: Maven 3.9.9, Oracle JDK 21.0.8, Java target 21, Boot 4.0.0 and
  `.mvn/maven-central-settings.xml`. Validation/reuse began on the clean commit;
  documentation runs include the recorded uncommitted test/record patch. The
  expected absent-record red test and unsuccessful temporary matrix recount
  remain separate from passing results.
- Commands, actual reports, source state, original/reused provenance, current
  readiness and artifact/report hashes are under
  `target/release-evidence/v32/priority1/`, covered by `SHA256SUMS`. Preserve the
  referenced V31 bundles before a root clean. No production, dependency,
  coordinate, historical-roadmap or proposal edit, signing or publication was
  performed. Priorities 2-12 remain open; Priority 8 still gates production work.

## Priority 2 - Architecture, Contract, and Ownership Map

### [x] 2.1 Map dependencies and supported surfaces

- [x] Map starter, test-helper, OTel and evidence-tooling dependencies, including
      shared packages, public internal bridges and optional linkage boundaries.
- [x] Inventory documented extension points and replacement rules; distinguish
      public supported APIs from internal cooperation without assuming removability.
- [x] Record authoritative configuration/metadata inputs and dependencies between
      assembly, declarative planning, invocation, transport, cache and reporting.
- [x] Attach source and existing test references; record what remains uninspected.

### [x] 2.2 Trace execution and lifetime boundaries

- [x] Trace successful and failed startup, ordinary Mono/Flux calls and shutdown.
- [x] Trace cache hits, independent misses, shared flights and refresh, including
      preparation, non-dispatching probes, transport and terminal publication.
- [x] Distinguish factory, method, logical caller, attempt, shared load, refresh,
      connection/stream and application-owned lifetimes in sequence diagrams.
- [x] Inventory mutable/frozen state, decision caches, context/scheduler transitions,
      lock boundaries, bean discovery and resource transfer points.

### [x] 2.3 Connect invariants to owners and evidence

- [x] For each required invariant, name its owner, enforcing path, transfer/release
      rule and tests that actually observe it.
- [x] Separate missing behavior, missing documentation and missing verification;
      do not classify file size or duplication alone as an architectural gap.
- [x] Publish the as-is map with bounded open questions and a coverage ledger
      that later review priorities can extend without duplicating the map.

**Priority 2 evidence (2026-09-14):**

- [ARCHITECTURE-MAP.md](ARCHITECTURE-MAP.md) records the module/optional linkage,
  supported extension and replacement surfaces, authoritative decision inputs,
  four execution sequences, state/lock/context ownership and all ten required
  invariant owners with source-linked observing tests. Q1-Q6 are bounded review
  questions for Priorities 3-7, not findings or authorization to change production.
- The map explicitly distinguishes independent caller-owned loads from manager-owned
  flights/refreshes, local construction rollback from unverified late assembly
  failure paths, and mutable property objects from enforced frozen cache bounds.
  No blanket leak, cleanup, configuration immutability or mesh diagnosis is made.
- Source baseline: reachable commit `876bbda919a9f9720926f3e1277e38fadd95ddaa`.
  Review began clean; verification includes the recorded uncommitted map/checklist
  and documentation-test patch. Only those three files changed. Maven 3.9.9,
  Oracle JDK 21.0.8, Java target 21, Boot 4.0.0 and
  `.mvn/maven-central-settings.xml`; reactor/baseline stay
  `4.5.0-SNAPSHOT` / `4.4.0`.
- Two new documentation tests first errored on the deliberately absent map, then
  passed. They check invariant coverage, scope, sequence presence, source/test
  links and named evidence methods. The focused boundary regression passed
  **313 tests in 12 classes**, zero failures/errors/skips, with this exact selector:

  ```bash
  mvn -B -ntp -s .mvn/maven-central-settings.xml \
    -pl reactive-http-client-starter \
    '-Dtest=DocumentationReleaseArtifactTest,ReactiveHttpClientAutoConfigurationTest,ReactiveHttpClientAotSmokeTest,EffectiveResiliencePolicyTest,SubscriptionReportingStateTest,BoundedLocalResponseCacheContractTest,CacheCallerAdmissionContractTest,CacheWorkCompositionContractTest,CacheWorkTelemetryContractTest,ReactiveHttpClientDiagnosticsProviderTest,CacheWorkOwnershipContractTest#valuedSourceKeepsItsReservationUntilCompletionOrCancellationCleanup,TransportResourceOwnershipStressTest#factoryDestroyWaitsForConnectionProviderDisposal' \
    test
  ```

  | Class / selected method | Executed cases |
  |---|---:|
  | DocumentationReleaseArtifactTest | 55 |
  | ReactiveHttpClientAutoConfigurationTest | 21 |
  | ReactiveHttpClientAotSmokeTest | 27 |
  | EffectiveResiliencePolicyTest | 2 |
  | SubscriptionReportingStateTest | 4 |
  | BoundedLocalResponseCacheContractTest | 51 |
  | CacheCallerAdmissionContractTest | 44 |
  | CacheWorkCompositionContractTest | 23 |
  | CacheWorkTelemetryContractTest | 21 |
  | ReactiveHttpClientDiagnosticsProviderTest | 61 |
  | CacheWorkOwnershipContractTest: valued-source cleanup only | 3 |
  | TransportResourceOwnershipStressTest: factory disposal only | 1 |

- Final documentation verification uses the same Maven options with
  `-Dtest=DocumentationReleaseArtifactTest`: **55 tests passed**, zero
  failures/errors/skips. `git diff --check` and the new-file whitespace check
  passed; source scope and XML totals are checked again in the evidence audit.
- Rendering follow-up (2026-09-14): the original fence/link checks did not parse
  Mermaid. Mermaid 11.17.2 reproduced all four reported parse failures caused by
  unescaped semicolons in labels. Replacing those separators with commas preserved
  the sequence content; all four diagrams then parsed and rendered to nonempty SVGs
  in headless Chrome. The existing documentation test now rejects raw semicolon
  separators in these blocks (failed before the fix); all **55 documentation tests
  passed** afterward. Parser results, original/fixed source, SVGs, screenshots and
  the cached-tool renderer are preserved under
  `target/release-evidence/v32/priority2/mermaid-follow-up/`. This is rendering
  evidence, not another production regression run or a new project dependency.
- Other map-linked tests are indexed existing coverage, not newly executed proof.
  Forced-GC ownership probes, full reactor, external consumer, native, API and
  benchmark lanes were not rerun for this documentation-only review. JVM AOT
  assertions above do not constitute a new native binary. Prior evidence remains
  linked through Priority 1 rather than overwritten or relabeled.
- Logs, exact source/diff snapshots, Surefire XML, baseline source archive,
  toolchain/settings and hash inventory are retained under
  `target/release-evidence/v32/priority2/`. Preserve this ignored directory before
  root clean. The absent-map red run is separate from passing stages. Priority 8.3
  still gates implementation; no dependency, API, historical roadmap, coordinate,
  signing or publication change was made.

## Priority 3 - Real Application Extension Scenarios

### [x] 3.1 Select representative external-consumer cases

- [x] Select concrete reported needs first; label hypothetical extensions and
      explain why their exploration is relevant to supported usage.
- [x] Place consumer examples outside starter packages, using documented public
      APIs and no reflection or package-private access to make them work.
- [x] Cover per-caller auth/tenant gates with cache hits and final identity, plus
      Boot/per-client builder mutations including non-filter callbacks/replacements.
- [x] Cover custom auth factory, metadata parser, codec/error decoder, observer or
      lifecycle hook without MeterRegistry, explicit handoff and connector replacement.

### [x] 3.2 Prove behavior without bypassing safety boundaries

- [x] Retain minimal attempted consumers, declared configuration and observed
      selection/order/callback/dispatch behavior rather than descriptions alone.
- [x] Exercise cache hit/miss and relevant auth refresh or final-request mutations
      without borrowing another caller's identity or bypassing safety classification.
- [x] Show explicit context transfer and target isolation; distinguish application
      ownership and connector-replacement limitations from starter defects.
- [x] Reuse existing fixtures where they independently prove the scenario; keep
      untested deployment assertions labeled unverified.

### [x] 3.3 Classify extension constraints

- [x] Classify each case as directly supported, supported with constraints,
      awkward but correct, dependent on unsupported internals, contradictory to
      a promised contract, or intentionally outside scope.
- [x] Identify the exact restriction and affected user. Distinguish deliberate
      safety rejection from an accidental extension gap.
- [x] Record existing SPI/local/documentation alternatives before proposing a new
      mechanism; link genuine gaps to finding IDs without approving fixes yet.

Priority 3 evidence (2026-09-14; final record verified 2026-09-15):

- [EXTENSION-SCENARIOS.md](EXTENSION-SCENARIOS.md) records twelve application
  scenarios, their configuration, exact observations, constraints and alternatives.
  The new `example.v32.ExtensionScenariosTest` lives in the separate Boot consumer
  and uses public APIs, not same-package bridges or reflective internal access.
- Reachable reviewed source: `c017b234a3770e41c6cc54d16440de611941f347`, plus the
  recorded uncommitted eight-file test/documentation/verifier patch. No production
  code, dependency version, published coordinate or historical roadmap changed.
  Implementation and release scope remain unselected. F001 and F002 are confirmed
  extension gaps with tested alternatives, **not accepted fixes**; Priority 8.3
  still owns that decision.
- Toolchain: Maven 3.9.9, Oracle JDK 21.0.8, Boot 4.0.0. Current artifacts were
  freshly installed using
  `mvn -B -ntp -s .mvn/maven-central-settings.xml -DskipTests -Dmaven.javadoc.skip=true install`.
  This used the existing local repository, not fresh Central downloads. Captured
  effective POM, dependency tree, classpath and matching installed/reactor JAR
  hashes establish assembled consumption without starter classes-directory leakage.
- Standalone profile: with
  `mvn -B -ntp -s .mvn/maven-central-settings.xml -f .github/boot4-consumer/pom.xml -Dconsumer.v32.extensions=true -Dtest=ExtensionScenariosTest test`,
  **17 cases passed in each of three consecutive final runs** (51 executions,
  zero failures/errors/skips). Its own Caffeine dependency makes this profile
  independent of the earlier parity profiles. Expected-gap assertions are included
  in that count, not evidence that F001/F002 are resolved.
- Combined assembled verification: the same Maven/consumer options, with
  `-Dconsumer.v26.observability=true -Dconsumer.v27.parity=true -Dconsumer.v28.parity=true -Dconsumer.v29.parity=true -Dconsumer.v30.parity=true -Dconsumer.v31.parity=true -Dconsumer.v32.extensions=true clean test`,
  passed **28 tests across seven classes**, zero failures/errors/skips. This includes
  the 17 new cases, not 28 additional cases. The current-consumer verifier now
  selects V32; its entire fresh-repository script was not rerun in this review.
- Final documentation command:
  `mvn -B -ntp -s .mvn/maven-central-settings.xml -pl reactive-http-client-starter -Dtest=DocumentationReleaseArtifactTest test`:
  **56 tests passed**, zero failures/errors/skips. The added guard checks the
  consumer boundary, twelve scenario IDs, fourteen referenced test methods,
  unapproved findings and independently wired profile. Link checks caught two
  finding anchors, which were corrected before this final run. Shell syntax,
  whitespace, source scope and final XML totals also passed the evidence audit.
- Preserve `target/release-evidence/v32/priority3/` before root clean: stage logs,
  commands, timestamps, XML, exact source snapshots/diffs, baseline archive,
  installed artifacts, toolchain/settings, `audit.json` and `SHA256SUMS` are there.
  Initial fixture failures and the missing-record red run remain separate. Final
  stages capture only their own module's reports; earlier mixed-module copies
  are not used for final totals.
- This is not a new ingress protocol/mesh, memory-profile, API, matrix, full-reactor
  test or native run. The handoff uses mock ingress plus real downstream transport;
  connector ownership is not a pod-memory diagnosis. Later priorities retain the
  untested selection, concurrency, optional-classpath and AOT/native combinations.

## Priority 4 - Effective Policy and Component Selection Review

### [x] 4.1 Compare decisions across creation and inspection paths

- [x] Build a matrix for startup, public handler creation, invocation, diagnostics,
      mocks and AOT covering grammar, effective API, cache, resilience and signing.
- [x] Identify one authoritative rule and evaluation time per fact; distinguish
      policy selection, component availability, validation and actual activation.
- [x] Preserve legitimate lifecycle differences and unknown values; do not force
      inspection paths to instantiate application components for superficial parity.
- [x] Ensure foreign replacement clients remain outside starter-only validation.

### [x] 4.2 Exercise representative selection and mutation cases

- [x] Reuse fixtures for primary/order/priority, fallback/default candidates,
      candidate resolvers and parent-child shadowing where relevant to each lookup.
- [x] Cover lazy/prototype components, cached/uncached FactoryBean products,
      factory-method definitions and supported programmatic replacement beans.
- [x] Record absent, available and unresolved outcomes without collapsing them;
      compare diagnostics with the actual runtime selection for the same case.
- [x] Inventory mutable configuration, frozen decisions and rejected runtime
      mutation; verify that validation and request behavior consume compatible state.

### [x] 4.3 Record parity, drift and smallest alternatives

- [x] Attach a reproducer for each confirmed mismatch and name the contract owner.
- [x] Explain where apparently repeated logic has different valid requirements;
      do not require a universal resolver or new immutable policy model by default.
- [x] Add the decision/selection matrix to the architecture map and disposition
      each reviewed area, linking only evidence-backed gaps to the finding register.

Priority 4 evidence (2026-09-15):

- [EFFECTIVE-POLICY-SELECTION.md](EFFECTIVE-POLICY-SELECTION.md) maps all six
  creation/inspection paths, authoritative rules, evaluation times, lookup states,
  mutable/frozen state and no-change alternatives. The architecture map links that
  canonical matrix. F001/F002 remain unresolved; new **V32-F003** reproduces AOT
  choosing the first initialized properties bean despite runtime non-primary
  precedence. Explicit primary selection is the passing alternative. No finding
  is approved for implementation; Priority 8.3 still owns the decision.
- Reachable reviewed source `6023a9132d2569108c55b2bf90bbceb7fd00084c`, plus the
  recorded seven-file uncommitted review/test patch. Production code, POMs,
  baseline `4.4.0`, development `4.5.0-SNAPSHOT` and V1-V31 remain unchanged.
  Maven 3.9.9, Oracle JDK 21.0.8, Boot 4.0.0, repository Central-only settings;
  existing local dependencies were used, not a fresh Central release lane.
- New paired fixtures passed **14 cases**: six absent/available/deferred Retry
  scenarios, four initialized registry-precedence cases, and four AOT/runtime
  properties cases. Retry behavior uses actual FactoryBean composition around an
  in-process 503/200 exchange; these are not TCP dispatch measurements. AOT cases
  invoke the JVM processor and runtime factory, not a native binary.
- Starter regression passed **320 tests across 16 classes**, zero
  failures/errors/skips, including those 14 cases. Reproduce with
  `mvn -B -ntp -s .mvn/maven-central-settings.xml -pl reactive-http-client-starter`
  and the following selector followed by `test`:

  ```text
  -Dtest=ComponentSelectionReviewTest,EffectiveSelectionAotReviewTest,ReactiveHttpClientDiagnosticsProviderTest,ReactiveHttpClientFactoryBeanDiagnosticsTest,ReactiveHttpClientAotSmokeTest,EffectiveHttpClientContractExporterTest,ReactiveHttpClientContractSnapshotTest,MethodMetadataValidationTest,MethodMetadataTimeoutTest,DeclarativeRequestParameterGrammarTest,DeclarativeReturnTypeGrammarTest,DeclarativeCachePolicyTest,CacheWorkPolicyEnforcementTest,EffectiveResiliencePolicyTest,ExplicitResilienceActivationContractTest,ResilienceOperatorApplierTest,ResponseCacheRetentionOwnershipTest#runtimePolicyBoundsMutationIsRejectedWithoutCreatingAnotherCache
  ```

  That mixed selector did not discover the nested applier classes. A separate
  run with the same Maven options and `'-Dtest=ResilienceOperatorApplierTest*' test`
  passed **35 additional tests** (14 no-op, nine null-registry, twelve real-registry;
  the enclosing class reports zero). Only the named retention mutation test was
  selected; no forced-GC retention probe ran.
- Mock regression:
  `mvn -B -ntp -s .mvn/maven-central-settings.xml -pl reactive-http-client-test -am -Dtest=MockReactiveHttpClientTest -Dsurefire.failIfNoSpecifiedTests=false test`:
  **63 tests passed**, zero failures/errors/skips. Upstream modules were compiled
  but had no matching tests; this is not a full reactor or newly assembled consumer
  run. Priority 3 external-consumer evidence retains its original provenance.
- Final documentation:
  `mvn -B -ntp -s .mvn/maven-central-settings.xml -pl reactive-http-client-starter -Dtest=DocumentationReleaseArtifactTest test`:
  **57 tests passed**, zero failures/errors/skips. The added guard was red before
  the selection record existed and checks all six paths, exact new fixture-method
  references and unapproved findings. A later missing full finding-ID assertion
  was corrected before final verification. Whitespace, source scope, reachable
  baseline and fresh XML totals passed the final audit.
- Preserve `target/release-evidence/v32/priority4/` before root clean: original
  failed attempts, successful stage commands/logs/XML, timestamps, source/diff
  copies, baseline archive, toolchain/settings, `audit.json` and `SHA256SUMS`.
  The initial three AOT mismatch errors are distinguished from the three
  fixture Boolean-unboxing errors and one incorrect fixture exception expectation.
  No native, API, matrix, benchmark, optional-classpath or deployment-memory
  experiment was rerun. Broader candidate/concurrency permutations remain named
  limitations for later priorities, not inferred passing evidence.

## Priority 5 - Invocation and Composition Boundaries

### [x] 5.1 Trace preparation through response publication

- [x] Map admission, argument/context snapshotting, selected-body serialization,
      finalized URI/headers, cache authorization probe, lookup and load startup.
- [x] Record filter/defaultRequest/exchange-function ordering and invocation counts
      across hits, misses, retries, redirects, auth replay and hidden refreshes.
- [x] Trace asynchronous filter continuations and cancellation/timeout while
      starter-entered preparation or lookup frames are still active.
- [x] Record frozen identity versus auth-visible bytes and successful-attempt
      revalidation before cache publication.

### [x] 5.2 Verify independent caller and load contracts

- [x] Separate caller deadlines and terminal records from shared load/refresh work,
      including original-caller detachment with remaining waiters.
- [x] Characterize retry, redirect and auth replay attempt/subscription accounting
      without changing published order, idempotency or repeatability rules.
- [x] Use observable dispatch and callback evidence for no post-terminal work,
      exactly-once reporting and retention of only final-attempt facts.
- [x] Preserve cache-local outcomes versus downstream request/health accounting
      and optional observer/lifecycle behavior without a metrics backend.

### [x] 5.3 Assess coupling using concrete changes

- [x] Identify supported changes that require coordinated edits across paths;
      record the actual contract dependency rather than only a count of edits.
- [x] Compare local corrections and existing helpers with possible extractions;
      explain any independent testability or complexity benefit.
- [x] Update the composition map and findings without merging lifetimes or
      implementing a second pipeline before the scope decision.

Priority 5 evidence (2026-09-15):

- [INVOCATION-COMPOSITION.md](INVOCATION-COMPOSITION.md) traces admission,
  selected snapshots/body bytes, finalized probe identity, guarded lookup/load
  startup, successful-attempt publication and terminal accounting. It records
  five concrete change dependencies and no-change/existing-helper alternatives.
  No new confirmed finding or extraction; F001-F003 and the Priority 8.3 gate
  remain unresolved/unselected. The architecture map and finding register link
  the review, including limits on arbitrary application continuations.
- Clean starting source `e714af451cce5e24d74183cf23936819278ad086`, plus the
  recorded eight-file review/test patch. Production, dependencies, `4.4.0`
  baseline, `4.5.0-SNAPSHOT` development and V1-V31 remain unchanged.
  Maven 3.9.9, Oracle JDK 21.0.8, Boot 4.0.0, repository Central-only settings;
  local dependencies were used, not fresh published-artifact resolution.
- New actual-factory fixture: **seven cases** count ordinary/miss/hit/retry/401
  replay/combined replay and unauthenticated mutations without MeterRegistry;
  all observer/lifecycle/log terminal collections are counted. The exchange
  function is synthetic, not TCP. Existing fake-clock refresh and two real
  loopback 307/308 cases now also assert default/filter/auth counts. Redirect
  coalescing uses confirmed flight membership before releasing the response.
- Fresh composition regression passed **236 tests across 12 classes**, zero
  failures/errors/skips. Reproduce with
  `mvn -B -ntp -s .mvn/maven-central-settings.xml -pl reactive-http-client-starter`
  and this selector followed by `test`:

  ```text
  -Dtest=InvocationCompositionReviewTest,BoundedLocalResponseCacheContractTest,CacheWorkCompositionContractTest,CacheCallerAdmissionContractTest,!CacheCallerAdmissionContractTest#terminalPreparationReleasesArgumentsContextAndAuthWhileManagerStaysOpen,RetryRedirectAuthReplayCompositionContractTest,SemanticReadReplayTimeoutContractTest,SubscriptionReportingStateTest,DiagnosticContextContractTest,ResilienceOperatorCompositionContractTest,LocalResponseCacheObservabilityTest,MicrometerHttpClientObserverTest,Boot4HttpClientHealthIndicatorTest
  ```

  Quote the selector as one shell argument. The explicit exclusion omits two
  forced-GC reachability cases, not cancellation/continuation/lookup coverage;
  the admission class ran **42 cases**. No collection or process-memory claim.
- Documentation:
  `mvn -B -ntp -s .mvn/maven-central-settings.xml -pl reactive-http-client-starter -Dtest=DocumentationReleaseArtifactTest test`:
  **58 tests passed**, zero failures/errors/skips. The new guard verifies the
  exact fixture-method references, separated caller/load/transport evidence,
  explicit scope and review links. Final source-scope, XML/exclusion, reachable
  baseline, local-link and whitespace checks accompany the SHA-256 inventory.
- Verification artifacts live in `target/release-evidence/v32/priority5/`:
  stage commands/logs/fresh XML, timestamps/exit status, baseline commit and source
  patch copies, toolchain/settings and hashed audit inventory. Preserve before
  root clean. Retained red runs distinguish a fixture accessor compilation error,
  seven missing work-config fixture setup errors and the missing-review document
  guard from production behavior. The initial trace rerun passed eight cases
  (seven new plus existing refresh); those cases are included in the 236 total,
  not additional independent coverage.
- This is targeted current-reactor JVM review evidence, not full reactor, new
  assembled consumer, AOT/native, strict API, optional-classpath, benchmark,
  HTTP/2, mesh or deployment-memory validation. Resource/teardown and remaining
  application callback permutations belong to Priorities 6-7; passing the
  tested guards is not a universal no-post-terminal-work guarantee.

## Priority 6 - Resource, Concurrency, and Retention Ownership

### [x] 6.1 Inventory resources and terminal owners

- [x] Follow eager InputStream/Reader/channel bodies, pooled buffers, materialized
      responses, selected context snapshots and auth state through ownership transfer.
- [x] Map entries/generations, independent loads, flights, refreshes and all three
      work reservations to acquisition, publication, finish and cleanup paths.
- [x] Record application-owned responses, connectors, executors, retained records
      and subscriptions separately, including work that may outlive the factory.
- [x] Identify partial-construction owners and cleanup obligations when validation,
      optional dependency resolution or later assembly fails.

### [x] 6.2 Characterize races and teardown boundaries

- [x] Cover relevant success/empty/error/timeout/cancel paths, late signals, discard,
      expiry, explicit eviction and stale-token publication with deterministic gates.
- [x] Verify cleanup precedes capacity reuse where required; include blocking
      cancellation callbacks and asynchronous preparation continuations.
- [x] Review lock ordering and external callbacks, late connection tracking,
      shutdown deadlines and concurrent destroy/recreate behavior.
- [x] Exercise overlapping meter owners and teardown without attributing another
      live factory's meters or cache resources to the closing owner.

### [x] 6.3 Classify retention before proposing fixes

- [x] Separate cache occupancy, active work, heap retention, direct/allocator
      capacity and process RSS; reuse applicable V29/V30 evidence with provenance.
- [x] Avoid forced-GC success assumptions in the ordinary suite; use controlled
      reachability lanes only when collection is actually needed for the claim.
- [x] Add ownership/terminal results and limitations to the map; require a concrete
      owner/reproducer before describing a leak or approving a concurrency refactor.

Completed on 2026-09-15. [RESOURCE-OWNERSHIP.md](RESOURCE-OWNERSHIP.md) records
resource/terminal owners, nested locks and external callbacks, construction
boundaries and retention classifications. The reachable clean starting source
is `c11d281330b48bcae3917f83bd2048913d03dcca`; verification includes the recorded
six-file review/test patch. Production sources, APIs, versions, dependencies and
V1-V31 evidence are unchanged.

- Six new `ResourceOwnershipReviewTest` cases cover early validation, registered
  Spring versus direct factory failure cleanup, a real application-owned
  HTTP/1.1 connector, and rejected public handler creation with/without a live
  same-tag meter owner. The last two cases confirm **V32-F004**: each rejection
  adds an abandoned cache metric lease; three rejected calls leave three owners
  and 48 maximum entries after normal owner/context teardown. The test cleans
  those owners reflectively only after observing the gap. This is not a fix or
  a supported application cleanup API. F001-F003 remain unresolved.
- Fresh ownership regression: **257 tests across 14 classes**, zero failures,
  errors or skips. These cover streams/readers/channels, pooled-buffer release,
  multipart and streaming response ownership, stale tokens, all three capacity
  dimensions, cancellation/discard, late connection tracking, shared transport
  disposal deadline, overlap/recreation and retained application handoff records.
  Gates assert source/peer/callback boundaries in addition to internal counters.
- `DocumentationReleaseArtifactTest`: **59 tests**, zero failures/errors/skips.
  Its new ownership guard checks record links, executable method references,
  F004, historical-memory limits and the still-unselected implementation gate.
  The final combined command below reruns all **316 cases across 15 classes**.
- All these runs use `-XX:+DisableExplicitGC`; seven collection-dependent cases
  are explicitly excluded, not counted as passing or skipped. No new test waits
  for GC. The historical V29 retention suite was not rerun; remaining legacy
  GC-dependent lane migration is a Priority 7 follow-up, not a silent test edit.
- Historical V29 memory/retention and V30 active-work/admission records are
  reused as versioned source conclusions only, with their reachable record
  revisions and hashes inventoried. Original target-only memory bundles are
  absent here. No fresh memory measurement or original raw-profile verification
  is claimed. Occupancy, active work, decoded bytes, heap, allocator/direct
  memory and RSS remain distinct; no current pod/mesh attribution is made.
- Oracle JDK 21.0.8, Maven 3.9.9, Java target 21, Boot 4.0.0 and
  `.mvn/maven-central-settings.xml`. Initial fixture setter/validation-expectation
  failures and an invalid documentation anchor remain preserved separately.
  No full-reactor, new assembled consumer, native, API comparison or performance
  lane was run. Any production correction still requires Priority 8.3 approval.
- Commands, UTC boundaries, fresh XML, exact source/patch snapshots, toolchain,
  historical-record provenance and `SHA256SUMS` are under
  `target/release-evidence/v32/priority6/`. Preserve this bundle before root clean.
  Source/report inventory and `git diff --check` validate the final review tree.

Final combined verification:

```bash
mvn -B -ntp -s .mvn/maven-central-settings.xml -pl reactive-http-client-starter \
  -DargLine=-XX:+DisableExplicitGC \
  '-Dtest=ResourceOwnershipReviewTest,CacheWorkOwnershipContractTest,!CacheWorkOwnershipContractTest#rejectedAndSkippedClosuresCollectWhileAdmittedOwnersRemainAlive+detachedCallerCollectsWhileSourceRetainsItsOwnState+evictionReleasesValuesBeforeCloseWithoutReleasingRunningLoad,CacheCallerAdmissionContractTest,!CacheCallerAdmissionContractTest#terminalPreparationReleasesArgumentsContextAndAuthWhileManagerStaysOpen,CacheLoadAdmissionContractTest,CacheRefreshAdmissionContractTest,CacheWorkTelemetryContractTest,LocalResponseCacheObservabilityTest,BoundedLocalResponseCacheContractTest,StreamingUploadOwnershipTest,MultipartWireOwnershipContractTest,TransportResourceOwnershipStressTest,Priority7HousekeepingTest,AsyncHandoffOwnershipContractTest,SubscriptionReportingStateTest,DocumentationReleaseArtifactTest' \
  test
```

Early-validation review correction (2026-09-15): caching and telemetry are now
selected in the invalid-URL fixture. It asserts zero meter registrations and
zero registry leases before cleanup, independently of the factory's unassigned
manager field. Correcting only the URL then acquires one observable cache owner.
This strengthens the earlier fixture, not production behavior or F004's scope.
The focused final rerun passed **65 tests** (six ownership, 59 documentation),
zero failures/errors/skips, with explicit GC disabled. Source is reachable
`dc862d7a1ef5533e60e557f1a63783bafcc04b94` plus the recorded three-file patch;
the earlier 316-case run is not presented as a rerun of this correction.
Fresh XML, commands, source snapshots and hashes are separately preserved under
`target/release-evidence/v32/priority6-early-validation/`, including the initial
fixture compilation failure. `git diff --check` passed.

```bash
mvn -B -ntp -s .mvn/maven-central-settings.xml -pl reactive-http-client-starter \
  -DargLine=-XX:+DisableExplicitGC \
  -Dtest=ResourceOwnershipReviewTest,DocumentationReleaseArtifactTest test
```

## Priority 7 - Module, Optional Integration, and Evidence Boundaries

### [x] 7.1 Review cross-module and optional dependency contracts

- [x] Inventory production behavior reused or copied by mocks and the public
      internal bridges that assembled helpers require.
- [x] Check no-Caffeine cache-disabled consumption and applicable absence of
      resilience, Micrometer and OTel without accidental eager linkage.
- [x] Distinguish observability configuration from backend availability; preserve
      non-Micrometer terminal surfaces and bounded metrics ownership.
- [x] Record whether a module or SPI change solves a demonstrated problem rather
      than assuming package sharing or optional checks are inherently wrong.

### [x] 7.2 Review runtime, AOT and native creation boundaries

- [x] Compare validation/selection of custom properties, metadata and replacement
      clients in runtime and AOT; record legitimate build-time constraints.
- [x] Audit generic/reflection traversal and supported context-only value hints
      using existing regressions before proposing more reflection or scanning.
- [x] Identify which accepted-change categories would require a native rerun;
      do not label existing source/binary evidence as current after affected edits.
- [x] Add creation-path and optional-integration outcomes to the architecture map.

### [x] 7.3 Review whether the evidence proves its claims

- [x] Inspect relevant wire/native/consumer fixtures for missed dispatch routes,
      timing-only assumptions, fixture-only defaults and self-confirming counters.
- [x] Check ordinary tests tolerate supported JVM/runtime settings; replace no
      fixture during review without recording what behavior it should establish.
- [x] Audit provenance for reachable revisions, fresh baseline repositories,
      artifact hashes, actual totals and source changes after measurement.
- [x] Record bounded evidence gaps and proportionate remedies; do not expand this
      into a general release-tooling rewrite or mandatory rerun of every old lane.

**Priority 7 evidence (2026-09-15):**

- [MODULE-EVIDENCE-BOUNDARIES.md](MODULE-EVIDENCE-BOUNDARIES.md) records the
  cross-module and optional-integration matrices, runtime/AOT creation rules,
  native rerun triggers and E7-01 through E7-08 evidence gaps with owners and
  reconsideration triggers. Mock assembly substitutes are distinguished from
  production cache/identity/admission reuse; public internal bridges are not
  treated as removable. No demonstrated need for a new module or SPI was found.
- The minimal consumer now asserts physical absence of Caffeine, four resilience
  registries, Micrometer Core, OTel API and the mock helper before two counted,
  bounded loopback calls. Required Micrometer observation/commons remain. The
  final minimal case and 17 external V32 extension cases passed from installed
  artifact JARs, with classpaths, dependency trees, effective POMs and JAR/source
  hashes retained. This used a fresh reactor installation in the existing local
  Maven repository, **not** a fresh isolated Central baseline run.
- Focused module regression passed **241 cases in 19 classes**: starter 101,
  helper 78 and OTel 62, with zero failures/errors/skips and explicit GC disabled.
  It includes optional selected-cache failure/rollback, no-registry terminal
  outcomes, shared metric owners, replacement properties/metadata/foreign AOT
  factories, record hints, mock/work/context parity, OTel and peer-observed
  GOAWAY/semantic-flight scenarios. It is not a full reactor or native test run.
- `DocumentationReleaseArtifactTest` passed **60 cases** after the final record
  update. Its new guard verifies review links and real named test methods across
  modules, while the existing consumer guard now matches bounded calls and
  optional-class assertions. Final applicable total: **319 passing cases**
  (241 module, 18 consumer, 60 documentation), excluding repeated runs and the
  separate GC experiment.
- The ordinary-test portability review found **V32-F005**, not a production
  leak: two existing caller reachability cases fail with SerialGC, 512 MiB heap
  and `-XX:+DisableExplicitGC`; the same cases pass when only that flag becomes
  `-XX:-DisableExplicitGC`. Both runs are preserved. Review completion does not
  claim all ordinary tests tolerate disabled GC. No collection-dependent test
  was disabled or migrated; the 16-case inventory and controlled-lane remedy
  remain for the explicit Priority 8 decision. AOT's recursive-generic fixture
  currently checks an unselected cache path, not execution of its visited guard.
- Source: clean starting revision `6fb540fa9e7b73657a247caa6ea4c2a19a774d48`
  plus this six-file test/review patch. Maven 3.9.9, Oracle JDK 21.0.8, Java
  target 21, Boot 4.0.0 and `.mvn/maven-central-settings.xml`. Production and
  native fixture sources match reachable `v4.4.0`; historical V31 archive and
  binary hashes were rechecked without claiming a new native build. Priority 1's
  sealed inventory and the Priority 6 correction's source-copy bridge to this
  reachable revision were verified; squash-local ancestry is not assumed.
- Exact commands, fresh XML, toolchain/settings, diffs/source snapshots, installed
  artifacts, historical hash checks and audit results are under
  `target/release-evidence/v32/priority7/`, sealed by `SHA256SUMS`.
  The expected absent-record red test, intermediate two documentation failures
  (old consumer source-text assertion and missing full finding ID), and GC
  characterization failures remain separate from final passing results.
  `git diff --check` passed. No production, API, POM, coordinate, V1-V31 history,
  signing or publication change; no new native/API/matrix/performance lane.
  Priorities 8-12 remain open; implementation and release scope remain unselected.

Principal fresh verification commands (artifact/classpath capture goals and the
GC control's exact invocation are retained with each stage):

```bash
mvn -B -ntp -s .mvn/maven-central-settings.xml -DskipTests -Dmaven.javadoc.skip=true install
mvn -B -ntp -s .mvn/maven-central-settings.xml \
  -pl reactive-http-client-starter,reactive-http-client-test,reactive-http-client-otel \
  -DargLine=-XX:+DisableExplicitGC -Dsurefire.failIfNoSpecifiedTests=false \
  '-Dtest=ReactiveHttpClientAotSmokeTest,EffectiveSelectionAotReviewTest,ReactiveHttpClientAutoConfigurationTest,LocalResponseCacheObservabilityTest,CacheWorkTelemetryContractTest,BoundedLocalResponseCacheContractTest#optionalImplementationIsRequiredOnlyForSelectedPolicies,Http2GoAwayRetirementContractTest,SemanticReadSingleFlightRefreshContractTest,Mock*,Boot4MockReactiveHttpClientTest,OpenTelemetry*,RequestResponseSizeObservabilityContractTest' test
mvn -B -ntp -s .mvn/maven-central-settings.xml \
  -f .github/boot4-cache-disabled-consumer/pom.xml -Dreactive-http-client.version=4.5.0-SNAPSHOT \
  -DargLine=-XX:+DisableExplicitGC clean test
mvn -B -ntp -s .mvn/maven-central-settings.xml \
  -f .github/boot4-consumer/pom.xml -Dreactive-http-client.version=4.5.0-SNAPSHOT \
  -Dconsumer.v32.extensions=true -DargLine=-XX:+DisableExplicitGC -Dtest=ExtensionScenariosTest clean test
mvn -B -ntp -s .mvn/maven-central-settings.xml -pl reactive-http-client-starter \
  -DargLine=-XX:+DisableExplicitGC -Dtest=DocumentationReleaseArtifactTest test
```

The intentionally failing environment characterization is not a passing CI lane:

```bash
mvn -B -ntp -s .mvn/maven-central-settings.xml -pl reactive-http-client-starter \
  '-DargLine=-Xms512m -Xmx512m -XX:+UseSerialGC -XX:+DisableExplicitGC' \
  -Dtest=CacheCallerAdmissionContractTest#terminalPreparationReleasesArgumentsContextAndAuthWhileManagerStaysOpen test
```

**Priority 7 consumer correction (2026-09-16):** The original minimal consumer
configured only its base URL, so it exercised resilience's disabled default, not
enabled-only selection. The corrected fixture explicitly enables resilience,
asserts the bound flag and four unselected operator names, and requires both GET
and `/value` for a successful response while still counting every request.
The documentation guard and optional-integration matrix now reflect those facts.
Fresh verification passed **61 cases** (one assembled minimal consumer and 60
documentation cases), zero failures/errors/skips, with explicit GC disabled.
Source: clean starting commit `025d4c0d11fe12e9aaf3a5528113127f626b4b18` plus
this four-file test/documentation patch. The commands above for installation,
minimal consumption and documentation were rerun; XML, source snapshots,
classpath, artifact hashes and `SHA256SUMS` are separately retained under
`target/release-evidence/v32/priority7-consumer-correction/`. The original 319-case
run is historical, not a rerun of this correction. No production change.

## Priority 8 - Findings, Necessity, and Scope Decision

### [x] 8.1 Consolidate and prioritize findings

- [x] Assign stable IDs with user need, observed impact, source/reproducer,
      contract owner, affected lifecycle and confidence or missing evidence.
- [x] Link recurring symptoms only when evidence establishes a common cause;
      separate historical fixes, current defects and exploratory concerns.
- [x] Prioritize confirmed safety/correctness gaps, supported extension blockers
      and demonstrable maintenance cost; keep cosmetic changes out of scope.
- [x] Record verified behavior and intentional no-change outcomes for every
      reviewed area, not only a list of proposed work.

### [x] 8.2 Compare alternatives and verification cost

- [x] Compare no change, documentation, local correction, existing helper/SPI and
      a narrowly justified new boundary for each candidate improvement.
- [x] Record complexity added/removed, source/binary/behavior/configuration risk,
      migration, concurrency/hot-path impact and rollback boundary.
- [x] Define deterministic acceptance and required consumer, optional-dependency,
      matrix, AOT/native and performance evidence per candidate.
- [x] Move broader features or breaking redesigns to separate proposals; identify
      deferred/unresolved evidence needs and reconsideration triggers.

### [x] 8.3 Record the maintainer scope decision

- [x] Obtain and record a dated explicit maintainer decision: selected finding IDs
      and dependencies, or review-only completion with no production change.
- [x] Freeze each selected item's bounded scope, acceptance, verification budget,
      compatibility classification and stop/review conditions before implementation.
- [x] Identify blocking findings and disposition non-selected work; do not treat
      checklist adoption as approval of findings or a release commitment.
- [x] **Not applicable (2026-09-16):** review-only branch; the maintainer selected
      F004/F005 implementation. Priority 9 and applicable Priority 10 checks remain
      required; Priority 12 separately selects release or no-publication.

Priority 8 completed on 2026-09-16 with **explicit maintainer approval for
F004 + F005**. [ARCHITECTURE-DECISION.md](ARCHITECTURE-DECISION.md) consolidates
five stable findings, ranks observed impact, compares alternatives and defines
each candidate's bounded acceptance, verification budget, compatibility and
rollback. It records no-change outcomes across all reviewed areas, excludes
unjustified redesigns from this scope and requires separate proposals before
any broader adoption. No new feature proposal is warranted by this evidence.

- Approved scope: F004's local failed-construction cleanup plus
  F005's deterministic/controlled-JVM test separation, with F001-F003 deferred
  behind their tested workarounds and named reconsideration triggers. Impact
  order differs from test dependency order. No finding is fixed, no common root
  is invented, and F004 is not attributed to the historical pod-memory report.
- The maintainer explicitly selected the recommended F004/F005 option and
  deferred F001-F003 with their documented workarounds. F005's deterministic
  test foundation precedes F004's local fix, followed by combined verification;
  there is no production-code dependency. Both selected findings block scope
  completion until verified or removed by a new decision. The record freezes
  boundaries, verification budgets, compatibility and rollback; new APIs,
  broader refactors or unavailable required lanes reopen the decision. Priority
  9 remains pending. Release is unselected; no publication is authorized.
- Pre-approval verification: **82 tests**, zero failures/errors/skips: 62 in
  DocumentationReleaseArtifactTest, six in ResourceOwnershipReviewTest, four
  in EffectiveSelectionAotReviewTest and ten in ComponentSelectionReviewTest.
  These verify documentation and as-is gap/parity characterizations, not fixes.
  The pre-approval documentation guard checks ranking, finding references, acceptance
  sections, local links and the pending approval gate. Full Markdown/manifest
  guards pass; the initial missing-record error and intermediate broken-anchor
  failures remain in separate stages.

```bash
mvn -B -ntp -s .mvn/maven-central-settings.xml \
  -pl reactive-http-client-starter -DargLine=-XX:+DisableExplicitGC \
  -Dtest=DocumentationReleaseArtifactTest,ResourceOwnershipReviewTest,EffectiveSelectionAotReviewTest,ComponentSelectionReviewTest \
  test
```

- Reviewed clean baseline: `15d0d16bc81f92ed920679f61754e8df0e32bfd2` plus the
  recorded four-file decision/documentation-test patch. Maven 3.9.9, Oracle
  JDK 21.0.8, Java target 21, Boot 4.0.0 and Central-only settings. Final checks
  rerun after checklist edits. Source copies, XML, commands, actual totals,
  toolchain/settings, reviewed source archive and hashes are under
  `target/release-evidence/v32/priority8/`, sealed by `SHA256SUMS`.
- Reused, not freshly run: Priority 7's 17 assembled extension cases and the two
  failing GC-disabled/two passing GC-control cases. The prior inventory is
  verified, sources bridged from reachable `6fb540fa9e7b73657a247caa6ea4c2a19a774d48`
  to the reviewed baseline, and reports retain original commands/provenance.
  Those reused counts are not included in the fresh 82. No fresh native, API,
  matrix, benchmark, memory measurement or assembled consumer run is claimed.
- No production, POM, dependency, coordinate, native fixture or V1-V31 change.
  Required implementation verification is defined by 8.3 and the actual patch;
  this documentation run cannot satisfy those future lanes. Historical review
  records retain their original unselected-scope checkpoints.

Approval verification on 2026-09-16: the same four-class command above passed
**82 tests**, zero failures/errors/skips, after recording the maintainer's choice
and aligning the active roadmap/index and current compatibility guide. The
documentation guard now requires exactly F004/F005 accepted and F001-F003
deferred, completed 8.3, pending Priority 9 and unselected release scope. The
approval red test and an intermediate whitespace-sensitive assertion failure
remain in separate stages, not hidden as passing runs.

The approval run uses the same reachable baseline plus a seven-file cumulative
documentation/test patch, continuing the existing uncommitted preparation work;
it is not a clean release build. Final verification was repeated after this
record. Commands, source copies, XML, toolchain/settings, prior-bundle hash and
`SHA256SUMS` are separately under
`target/release-evidence/v32/priority8-approval/`. The original sealed
`priority8/` bundle remains unchanged and still describes the pending decision
at that earlier checkpoint. No production fix or future implementation lane is
claimed by this approval verification.

## Priority 9 - Bounded Accepted Improvements

### [x] 9.1 Prepare selected changes or record not applicability

- [x] Confirm selected finding IDs and maintainer approval from 8.3; stop if the
      decision is absent. For review-only, record this priority as not applicable.
- [x] For each accepted change, retain a failing regression or behavior baseline
      and identify the smallest affected contract/module boundary.
- [x] Keep implementation, public surface and migration scope within the accepted
      decision; do not use a spike to imply shipped functionality.

### [x] 9.2 Implement and verify one accepted boundary at a time

- [x] Prefer local fixes and existing helpers; extract only for an evidenced
      reduction in complexity or improvement in independent testability.
- [x] Preserve explicit activation, ownership, identity, replay, timeout,
      diagnostics and optional-integration contracts through focused regressions.
- [x] Keep unrelated cleanup and speculative configurability out; remove only
      unused code made obsolete by the accepted change.
- [x] Reopen the scope decision when a larger redesign or unapproved compatibility
      break is needed, rather than silently extending this priority.

### [x] 9.3 Reconcile outcomes with the reviewed architecture

- [x] Update the maps and finding dispositions with exact delivered behavior and
      verification; record any narrower result and remaining constraints.
- [x] Compare the before/after extension scenario and ownership evidence, not just
      passing unit counts or reduced lines of code.
- [x] Remove unadopted experimental implementation from the proposed release scope;
      do not leave a partial supported contract represented as complete.

Priority 9 completed on 2026-09-16 from clean reachable
`59fd8b7b2e6ee20aca65d08ad8ee7aaf871d7977` plus the recorded implementation patch.
[ACCEPTED-IMPROVEMENTS.md](ACCEPTED-IMPROVEMENTS.md) inventories the exact 16
reachability cases, commands, ownership correction and remaining constraints.
Only approved F004/F005 were implemented; no broader extraction was needed,
no experimental supported surface remains, and F001-F003 remain deferred.

- F005 baseline: two caller-retention cases failed with explicit GC disabled.
  After separation, all **79 ordinary cases** in the three affected classes
  passed with `-XX:+DisableExplicitGC`. The controlled profile runs the same
  16 scenarios with extra collection assertions; it passed **16 cases**.
  Separate explicit-GC, collector and heap negative checks each failed one
  prerequisite assertion as intended, not skipped scenarios. V31's lane is unchanged.
- F004 desired-behavior red run: **15 cases, six failures**, zero errors/skips
  before the production edit. A preceding fixture compilation mistake is
  preserved separately. Local construction rollback then passed those 15 cases;
  two successful cache-selected/unselected ownership-transfer controls bring
  the final class to **17 passing cases**. No per-request production edits.
- Final focused starter/documentation run: **346 tests across 15 classes**,
  zero failures/errors/skips, with explicit GC disabled. This includes the
  **283-case** ownership/composition regression and **63 documentation guards**,
  including the new 16-scenario inventory and pending-Priority-10 checks.
- Mock/helper regression: **70 tests across three classes**, zero
  failures/errors/skips, using the current reactor (not isolated publication).
  Controlled reachability XML is separate from ordinary XML and CI uploads both.

Final starter command:

```bash
mvn -B -ntp -s .mvn/maven-central-settings.xml -pl reactive-http-client-starter \
  -DargLine=-XX:+DisableExplicitGC \
  -Dtest=ResourceOwnershipReviewTest,ResponseCacheRetentionOwnershipTest,CacheWorkOwnershipContractTest,CacheCallerAdmissionContractTest,CacheLoadAdmissionContractTest,CacheRefreshAdmissionContractTest,CacheWorkTelemetryContractTest,LocalResponseCacheObservabilityTest,BoundedLocalResponseCacheContractTest,InvocationCompositionReviewTest,CacheWorkCompositionContractTest,SemanticReadReplayTimeoutContractTest,SubscriptionReportingStateTest,AsyncHandoffOwnershipContractTest,DocumentationReleaseArtifactTest test
```

Helper command:

```bash
mvn -B -ntp -s .mvn/maven-central-settings.xml -pl reactive-http-client-test -am \
  -DargLine=-XX:+DisableExplicitGC \
  -Dtest=MockReactiveHttpClientTest,MockResponseCacheSupportTest,MockCacheWorkParityTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Oracle JDK 21.0.8, Maven 3.9.9, Java target 21, Boot 4.0.0 and repository
Central-only settings. Preserve `target/release-evidence/v32/priority9/`
before root clean: stage commands/logs/fresh XML, timestamps/exit statuses,
source/patch, toolchain/settings, audit and SHA-256 inventory. Final documentation,
source scope, XML totals and whitespace checks were rerun after the records.
Earlier Priority 1-8 evidence retains its dated as-is scope.

Priority 10 remains required: full affected-module/supported-Boot suites, strict
root and independent starter API checks against fresh Central `4.4.0`, assembled
consumers, JVM AOT and clean-source native creation/lifecycle evidence. No fresh
native, matrix, API, assembled-consumer, memory or JMH run is claimed here.
No hot-path change warrants a new benchmark. Release scope remains unselected.

## Priority 10 - Compatibility and Targeted Verification

### [x] 10.1 Select and run the applicable correctness lanes

- [x] Map accepted edits and review fixtures to required verification. Review-only
      still runs new/changed characterization tests and documentation/archive guards.
- [x] For accepted production edits, run focused contracts and full affected-module
      regressions, preserving actual failures, errors, skips and test totals.
- [x] Run applicable strict root and independent starter source/binary comparisons
      against fresh Central `4.4.0`, plus assembled consumers without reactor leakage.
- [x] Verify behavioral/configuration compatibility separately; an unchanged
      signature does not prove the extension, default or lifecycle contract unchanged.

### [x] 10.2 Verify shared-boundary, native and optional parity

- [x] For affected shared boundaries, run supported Java/Boot dependency rows and
      production/mock/consumer variants of the accepted extension cases.
- [x] Preserve minimal no-Caffeine consumers and applicable missing integrations;
      verify replacement bean selection and factory shutdown where touched.
- [x] When reflection, bean creation or lifecycle changes, run AOT and native
      compile/execution from exact clean source and record binary/toolchain hashes.
- [x] Document each omitted/reused lane with its scope-based reason and provenance;
      a required but unavailable check stays pending, not implicitly passed.

### [x] 10.3 Assess hot-path cost and evidence integrity

- [x] **Not applicable (construction-only patch):** For hot-path/allocation
      changes, compare identical workloads with published
      `4.4.0`; keep fixture setup, cold/warm state and metrics selection equivalent.
- [x] **Not applicable (no new benchmark/claim):** Separate benchmark
      discovery/smoke from controlled measurements and public
      claims; provide manual commands when the measurement lane is external.
- [x] Use deterministic gates for concurrency and controlled JVM lanes for any
      reachability assertion; do not certify behavior from sleeps or forced GC.
- [x] Retain reports, exact sources, hashes and limitations; for review-only or
      unaffected paths, document not applicability instead of inventing new results.

Priority 10 completed (2026-09-17): **10.1, 10.2 and 10.3 are complete**.
[Verification record](COMPATIBILITY-VERIFICATION.md)
maps scope to lanes, commands, compatibility distinctions and omissions.

- Clean reachable source `c8f6a527450ea512ed6837bd8d09191921d4dd48`, built in an
  isolated clone: Java 21.0.8, Maven 3.9.9, target Java 21, Central-only settings.
  Each Boot 4.0.0/4.1.0 reactor passed **1,928 tests**: 1,788 starter, 78 helper,
  62 OTel, zero failures/errors/skips. Each script row also passed three default
  consumers, but the upper consumer retained Boot 4.0.0 with Spring 7.0.8;
  this mixed result is not Boot 4.1 consumption. Both strict
  root API comparisons and separate fresh-Central starter comparison passed.
- Fresh isolated consumer lane: **74 mock + 28 full + three minimal cases**;
  property-only upper consumer rerun: **28 mixed-version cases**. The evidence
  audit identified the fixed consumer parent; an explicit Boot 4.1 parent
  overlay passed **28 cases**, with its actual Boot 4.1 classpath verified
  separately. Classpaths use installed JARs, not reactor
  classes. Minimal Boot 4.0 consumption excludes Caffeine,
  OTel and the helper, including enabled-only/no-operator resilience.
- Explicit-GC-disabled contracts: **96 passing cases** (79 affected ordinary
  scenarios plus 17 construction controls). Controlled lanes passed **16 cache
  and five V31 handoff cases**. Three negative prerequisite runs each failed
  one expected assertion. API/baseline negative-fixture scripts passed.
- JVM fixture: **six cases passed**, and ordinary/AOT executables exited zero.
  Native compilation reran six passing fixture cases but failed with
  `OutOfMemoryError: Java heap space` under GraalVM 25.0.3, 4 GiB build heap and
  two workers. A requested 6 GiB clean retry passed six JVM cases again but
  failed with watchdog exit 30 (Maven exit 1) during universe building, with
  severe host RAM/swap pressure observed. Both failures are retained. After the
  host restart, a fresh clean clone of the same commit and isolated repository
  passed six fixture cases and native compilation with 6 GiB/two workers.
  Maven exited zero after 354 seconds; the new executable exited zero after
  18.8 seconds within its 180-second bound. Native Image reported 5.40 GB peak
  RSS. Binary SHA-256:
  `49332e7ff709fc9295c675ca54d176e52b40bc5262573afab37b2b0dc1d85d4f`.
- No steady-state code change warrants JMH or memory remeasurement. F001-F003
  remain deferred and release scope unselected. Production, dependencies,
  coordinates, native fixture and V1-V31 records are unchanged by this priority.
- Stage logs/commands, clean source archive, fresh XML, artifacts, classpaths,
  strict reports, Central provenance, toolchain hashes and failure evidence are
  retained in `target/release-evidence/v32/priority10/`, with an audit and
  `SHA256SUMS`. Completion documentation and its guard are a separate patch over
  that clean source; earlier sealed evidence is unchanged.
- The fresh native completion bundle, including binary/toolchain hashes,
  companion libraries, source archive, installed reactor artifacts, commands,
  XML, final documentation rerun and its own audit/manifest, is
  `target/release-evidence/v32/priority10-native/`. The original manifest was
  verified unchanged. The source archive matches byte-for-byte; broader lanes
  above are reused from the same clean commit, not rerun after the restart.
- Final documentation/archive/readiness verification passed **63 cases**, zero
  failures/errors/skips. The earlier one-failure stale-status assertion run is
  retained separately. The post-native completion documentation rerun also
  passed **63 cases**. `git diff --check` passed. Priorities 11 and 12 and release
  selection remain open; passing verification does not authorize publication.

## Priority 11 - Maintainer and Operations Guidance

### [x] 11.1 Publish the reviewed architecture and extension guidance

- [x] Link the reviewed map, scenarios, findings and decision record from one
      maintainer entry point without creating redundant sources of truth.
- [x] Document the supported SPI for each demonstrated need, its phase/order,
      invocation cardinality, ownership and limits, including connector replacement.
- [x] Preserve public-internal distinctions, no-change rationales and deferred
      reconsideration triggers; do not document discarded spikes as supported APIs.

### [x] 11.2 Consolidate canonical public and operations guidance

- [x] Correct only contradictions demonstrated by the review; align examples with
      actual required dependencies, policy selection and customization constraints.
- [x] Distinguish published `4.4.0` behavior from any accepted candidate additions;
      do not select a release or claim an implementation through documentation alone.
- [x] Tie troubleshooting to exported signals or explicitly collected bounded
      evidence available at the stated observation time, including shutdown.
- [x] Keep support artifacts structural and sanitized; exclude request material,
      credential/identity data, arbitrary error text and unverified diagnostic claims.

### [x] 11.3 Verify guidance and recorded scope

- [x] Run link, archive, generated-readiness and affected example/fixture guards;
      record actual totals and any negative-case coverage added by the review.
- [x] Reconcile every reviewed area with a conclusion and every accepted change
      with tests and guidance; unresolved questions retain named evidence needs.
- [x] Ensure review-only guidance does not require unavailable metrics, forced
      publication, or a fabricated performance/memory/mesh result.

Priority 11 completed (2026-09-18). [MAINTAINER-GUIDANCE.md](MAINTAINER-GUIDANCE.md)
is the single maintainer entry point linked from the root README and roadmap
index. It links all review records rather than duplicating their evidence,
maps demonstrated needs to supported extensions with phase/cardinality/ownership,
and preserves public-internal distinctions, no-change conclusions and F001-F003
workarounds, owners and reconsideration triggers.

- Canonical customizer/cache guidance now distinguishes non-cached registration
  from cache-safety inventory, replayable pre-lookup gates from load-only exchange
  functions, replacement-builder Boot customization and application connector
  lifecycle. The existing cache guide remains the dependency/policy/variant
  authority; no new example invents a default or relaxes eligibility.
- Compatibility and operations guidance separates published `4.4.0` from the
  unpublished F004 cleanup and F005 contributor-lane change. Startup metadata
  and AOT selection limits remain deferred, not documented as repaired. Operations
  uses bounded construction/lifecycle evidence, pre-close samples and same-tag
  ownership caveats; absent post-close meters are not terminal deltas. Existing
  sanitized bundle schemas are reused without adding request or identity material.
- Fresh focused verification: **114 cases across six classes**, zero failures,
  errors or skips: documentation **65**, construction/ownership **17**, invocation
  composition **7**, component selection **10**, AOT selection **4**, customizers
  **11**. Documentation includes links/anchors, archive/readiness generation,
  examples and structural/private-data fixture guards. Two new tests protect the
  scope/navigation/operations wording, including five deliberately broken sibling
  and canonical-guide link mutations. The initial run had one overly literal
  line-wrapping assertion failure; its log/XML remain separate from passing runs.
- Clean starting revision `d39e4a94cb65d97c9448fd9ecd7ee21b198ef4d8` plus this
  documentation/test patch; Oracle JDK 21.0.8, Maven 3.9.9, Java target 21 and
  Boot 4.0.0. Central-only settings use the already populated isolated repository
  below, not fresh published downloads. Ordinary tests run with explicit GC
  disabled; no collection-dependent test is skipped or claimed freshly measured.
- Commands/logs, fresh XML, source copies/patch, generated readiness and source/
  report hashes are retained under `target/release-evidence/v32/priority11/`
  with `SHA256SUMS`. Preserve before root clean. `git diff --check` passed.
  No production/API/POM/coordinate or V1-V31 historical change. Prior clean-source
  API/matrix/consumer/native evidence remains in
  [Priority 10](COMPATIBILITY-VERIFICATION.md), not relabeled as a new run.
  Documentation-only edits need no new native, full-reactor or benchmark lane;
  no numerical memory/performance or mesh claim is made. Priority 12 and release
  selection remain open.

Final focused verification (from the repository root):

```bash
JAVA_HOME=/usr/lib/jvm/jdk-21.0.8-oracle-x64 \
PATH=/usr/lib/jvm/jdk-21.0.8-oracle-x64/bin:$PATH \
MAVEN_OPTS='-Xmx512m -XX:ActiveProcessorCount=2' \
mvn -B -ntp -s .mvn/maven-central-settings.xml \
  -Dmaven.repo.local=/tmp/v32-boot41-consumer.Z9m7kq/repository \
  -pl reactive-http-client-starter -DargLine=-XX:+DisableExplicitGC \
  -Dtest=DocumentationReleaseArtifactTest,ResourceOwnershipReviewTest,InvocationCompositionReviewTest,ComponentSelectionReviewTest,EffectiveSelectionAotReviewTest,ReactiveHttpClientCustomizerTest test
```

Use another local Maven repository if that machine-local directory is absent;
the path records this run's dependency provenance, not a required tracked input.

## Priority 12 - Review Closure and Conditional Release Go/No-Go

### [ ] 12.1 Assemble the architecture decision evidence

- [ ] Assemble reachable reviewed source, maps, scenarios, ranked findings,
      dispositions, selected-item results and unresolved risks in the decision record.
- [ ] Check all accepted blocking items are resolved or explicitly removed from
      scope with a maintainer decision; deferred is not implemented.
- [ ] Retain source/report/toolchain hashes, actual counts, clean/dirty state and
      original failures in an inventory that can be verified from reviewed history.
- [ ] Re-run affected checks after final edits; preserve reused historical/native/
      performance provenance without relabeling it as a final-source run.

### [ ] 12.2 Select review-only or release scope

- [ ] Record a dated review-only/documentation-only, patch, additive minor or no-go
      decision based on actual work; the snapshot coordinate selects no release.
- [ ] For review-only/no publication, state why a release is unnecessary, retain
      the `4.4.0` baseline, and mark publication-only gates not applicable explicitly.
- [ ] If a release is justified, choose and align final coordinates, supported
      surface, canonical guidance, changelog and readiness before release evidence.
- [ ] Require a separate major-version/migration decision for breaking work. Do
      not silently ship it as an architecture cleanup or automatically select `4.5.0`.

### [ ] 12.3 Complete the selected publication or no-publication path

- [ ] On release go, verify the reviewed clean final commit/tag, signed artifacts,
      staged assembled consumption and generation packaging before deployment.
- [ ] Preserve signing/publication evidence; a scope GO or unsigned local build
      is not a signing pass. Keep credentials and passphrases out of artifacts.
- [ ] After publication, verify all release attachments and an assembled consumer
      from fresh Central-only repositories before advancing public/API/consumer/
      benchmark baselines or the next development coordinate.
- [ ] On no-publication, record publication checks as not applicable with the
      12.2 decision; do not fabricate a published version to close the review.

### [ ] 12.4 Archive V32 with a truthful disposition

- [ ] Close the accepted review scope with evidence for each area and explicit
      dispositions for unresolved/deferred work and conditional checks.
- [ ] Align roadmap, checklist, index, archive guards and generated readiness with
      the chosen review/release result; future manual release tasks do not reopen it.
- [ ] Keep V1-V31 evidence unchanged and any future proposal unselected until
      separately adopted; record next-work suggestions without adding closure gates.
- [ ] Confirm no unimplemented public contract, unrun required check or unsupported
      memory/performance/mesh claim is represented as shipped or verified.

## Completion Criteria

- [ ] All reviewed modules and paths have source-linked architecture/owner maps,
      evidence and a disposition, including legitimate lifecycle differences.
- [ ] External-consumer scenarios distinguish supported usage, intentional limits
      and proven gaps without relaxing safety restrictions to make tests pass.
- [ ] Findings have concrete user impact, alternatives and evidence; accepted
      production work has an explicit maintainer decision and passing verification.
- [ ] Review-only remains a valid completed outcome; conditional work carries
      explicit not-applicable reasons rather than invented implementation evidence.
- [ ] Public/operations guidance matches delivered behavior and available signals;
      privacy, optional integrations and compatibility remain intact.
- [ ] The dated review/release disposition and matching archive path are complete,
      with publication claimed only after the corresponding verification.
