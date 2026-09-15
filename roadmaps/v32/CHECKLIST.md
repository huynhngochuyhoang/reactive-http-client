# Reactive HTTP Client - Roadmap V32 Execution Checklist

> **Status:** active
> **Published baseline:** `4.4.0`
> **Development coordinate:** `4.5.0-SNAPSHOT`
> **Implementation scope:** unselected; Priority 8 requires an explicit decision
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

The baseline, architecture map, extension scenarios and finding register now exist;
the decision record remains planned, not completed evidence. Keep detailed matrices in these
records unless their size justifies a separate file.

| Record | Contents |
|---|---|
| [BASELINE-SCOPE.md](BASELINE-SCOPE.md) | Verified baseline, historical decision inventory and review coverage |
| [ARCHITECTURE-MAP.md](ARCHITECTURE-MAP.md) | Module, decision, composition and ownership maps with source/test references |
| [EXTENSION-SCENARIOS.md](EXTENSION-SCENARIOS.md) | External-consumer attempts, observations and supported/limited/gap classifications |
| [EFFECTIVE-POLICY-SELECTION.md](EFFECTIVE-POLICY-SELECTION.md) | Entry-point/lookup matrix, mutation boundaries and representative parity/drift evidence |
| [FINDINGS.md](FINDINGS.md) | Three reproduced selection/extension gaps, alternatives and unselected implementation decision |
| `ARCHITECTURE-DECISION.md` | Maintainer scope decision, selected-item verification and final review/release disposition |

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

### [ ] 5.1 Trace preparation through response publication

- [ ] Map admission, argument/context snapshotting, selected-body serialization,
      finalized URI/headers, cache authorization probe, lookup and load startup.
- [ ] Record filter/defaultRequest/exchange-function ordering and invocation counts
      across hits, misses, retries, redirects, auth replay and hidden refreshes.
- [ ] Trace asynchronous filter continuations and cancellation/timeout while
      starter-entered preparation or lookup frames are still active.
- [ ] Record frozen identity versus auth-visible bytes and successful-attempt
      revalidation before cache publication.

### [ ] 5.2 Verify independent caller and load contracts

- [ ] Separate caller deadlines and terminal records from shared load/refresh work,
      including original-caller detachment with remaining waiters.
- [ ] Characterize retry, redirect and auth replay attempt/subscription accounting
      without changing published order, idempotency or repeatability rules.
- [ ] Use observable dispatch and callback evidence for no post-terminal work,
      exactly-once reporting and retention of only final-attempt facts.
- [ ] Preserve cache-local outcomes versus downstream request/health accounting
      and optional observer/lifecycle behavior without a metrics backend.

### [ ] 5.3 Assess coupling using concrete changes

- [ ] Identify supported changes that require coordinated edits across paths;
      record the actual contract dependency rather than only a count of edits.
- [ ] Compare local corrections and existing helpers with possible extractions;
      explain any independent testability or complexity benefit.
- [ ] Update the composition map and findings without merging lifetimes or
      implementing a second pipeline before the scope decision.

## Priority 6 - Resource, Concurrency, and Retention Ownership

### [ ] 6.1 Inventory resources and terminal owners

- [ ] Follow eager InputStream/Reader/channel bodies, pooled buffers, materialized
      responses, selected context snapshots and auth state through ownership transfer.
- [ ] Map entries/generations, independent loads, flights, refreshes and all three
      work reservations to acquisition, publication, finish and cleanup paths.
- [ ] Record application-owned responses, connectors, executors, retained records
      and subscriptions separately, including work that may outlive the factory.
- [ ] Identify partial-construction owners and cleanup obligations when validation,
      optional dependency resolution or later assembly fails.

### [ ] 6.2 Characterize races and teardown boundaries

- [ ] Cover relevant success/empty/error/timeout/cancel paths, late signals, discard,
      expiry, explicit eviction and stale-token publication with deterministic gates.
- [ ] Verify cleanup precedes capacity reuse where required; include blocking
      cancellation callbacks and asynchronous preparation continuations.
- [ ] Review lock ordering and external callbacks, late connection tracking,
      shutdown deadlines and concurrent destroy/recreate behavior.
- [ ] Exercise overlapping meter owners and teardown without attributing another
      live factory's meters or cache resources to the closing owner.

### [ ] 6.3 Classify retention before proposing fixes

- [ ] Separate cache occupancy, active work, heap retention, direct/allocator
      capacity and process RSS; reuse applicable V29/V30 evidence with provenance.
- [ ] Avoid forced-GC success assumptions in the ordinary suite; use controlled
      reachability lanes only when collection is actually needed for the claim.
- [ ] Add ownership/terminal results and limitations to the map; require a concrete
      owner/reproducer before describing a leak or approving a concurrency refactor.

## Priority 7 - Module, Optional Integration, and Evidence Boundaries

### [ ] 7.1 Review cross-module and optional dependency contracts

- [ ] Inventory production behavior reused or copied by mocks and the public
      internal bridges that assembled helpers require.
- [ ] Check no-Caffeine cache-disabled consumption and applicable absence of
      resilience, Micrometer and OTel without accidental eager linkage.
- [ ] Distinguish observability configuration from backend availability; preserve
      non-Micrometer terminal surfaces and bounded metrics ownership.
- [ ] Record whether a module or SPI change solves a demonstrated problem rather
      than assuming package sharing or optional checks are inherently wrong.

### [ ] 7.2 Review runtime, AOT and native creation boundaries

- [ ] Compare validation/selection of custom properties, metadata and replacement
      clients in runtime and AOT; record legitimate build-time constraints.
- [ ] Audit generic/reflection traversal and supported context-only value hints
      using existing regressions before proposing more reflection or scanning.
- [ ] Identify which accepted-change categories would require a native rerun;
      do not label existing source/binary evidence as current after affected edits.
- [ ] Add creation-path and optional-integration outcomes to the architecture map.

### [ ] 7.3 Review whether the evidence proves its claims

- [ ] Inspect relevant wire/native/consumer fixtures for missed dispatch routes,
      timing-only assumptions, fixture-only defaults and self-confirming counters.
- [ ] Check ordinary tests tolerate supported JVM/runtime settings; replace no
      fixture during review without recording what behavior it should establish.
- [ ] Audit provenance for reachable revisions, fresh baseline repositories,
      artifact hashes, actual totals and source changes after measurement.
- [ ] Record bounded evidence gaps and proportionate remedies; do not expand this
      into a general release-tooling rewrite or mandatory rerun of every old lane.

## Priority 8 - Findings, Necessity, and Scope Decision

### [ ] 8.1 Consolidate and prioritize findings

- [ ] Assign stable IDs with user need, observed impact, source/reproducer,
      contract owner, affected lifecycle and confidence or missing evidence.
- [ ] Link recurring symptoms only when evidence establishes a common cause;
      separate historical fixes, current defects and exploratory concerns.
- [ ] Prioritize confirmed safety/correctness gaps, supported extension blockers
      and demonstrable maintenance cost; keep cosmetic changes out of scope.
- [ ] Record verified behavior and intentional no-change outcomes for every
      reviewed area, not only a list of proposed work.

### [ ] 8.2 Compare alternatives and verification cost

- [ ] Compare no change, documentation, local correction, existing helper/SPI and
      a narrowly justified new boundary for each candidate improvement.
- [ ] Record complexity added/removed, source/binary/behavior/configuration risk,
      migration, concurrency/hot-path impact and rollback boundary.
- [ ] Define deterministic acceptance and required consumer, optional-dependency,
      matrix, AOT/native and performance evidence per candidate.
- [ ] Move broader features or breaking redesigns to separate proposals; identify
      deferred/unresolved evidence needs and reconsideration triggers.

### [ ] 8.3 Record the maintainer scope decision

- [ ] Obtain and record a dated explicit maintainer decision: selected finding IDs
      and dependencies, or review-only completion with no production change.
- [ ] Freeze each selected item's bounded scope, acceptance, verification budget,
      compatibility classification and stop/review conditions before implementation.
- [ ] Identify blocking findings and disposition non-selected work; do not treat
      checklist adoption as approval of findings or a release commitment.
- [ ] For review-only, record which Priority 9/10/12 branches are not applicable
      and why, while preserving required review, fixture and documentation checks.

## Priority 9 - Bounded Accepted Improvements

### [ ] 9.1 Prepare selected changes or record not applicability

- [ ] Confirm selected finding IDs and maintainer approval from 8.3; stop if the
      decision is absent. For review-only, record this priority as not applicable.
- [ ] For each accepted change, retain a failing regression or behavior baseline
      and identify the smallest affected contract/module boundary.
- [ ] Keep implementation, public surface and migration scope within the accepted
      decision; do not use a spike to imply shipped functionality.

### [ ] 9.2 Implement and verify one accepted boundary at a time

- [ ] Prefer local fixes and existing helpers; extract only for an evidenced
      reduction in complexity or improvement in independent testability.
- [ ] Preserve explicit activation, ownership, identity, replay, timeout,
      diagnostics and optional-integration contracts through focused regressions.
- [ ] Keep unrelated cleanup and speculative configurability out; remove only
      unused code made obsolete by the accepted change.
- [ ] Reopen the scope decision when a larger redesign or unapproved compatibility
      break is needed, rather than silently extending this priority.

### [ ] 9.3 Reconcile outcomes with the reviewed architecture

- [ ] Update the maps and finding dispositions with exact delivered behavior and
      verification; record any narrower result and remaining constraints.
- [ ] Compare the before/after extension scenario and ownership evidence, not just
      passing unit counts or reduced lines of code.
- [ ] Remove unadopted experimental implementation from the proposed release scope;
      do not leave a partial supported contract represented as complete.

## Priority 10 - Compatibility and Targeted Verification

### [ ] 10.1 Select and run the applicable correctness lanes

- [ ] Map accepted edits and review fixtures to required verification. Review-only
      still runs new/changed characterization tests and documentation/archive guards.
- [ ] For accepted production edits, run focused contracts and full affected-module
      regressions, preserving actual failures, errors, skips and test totals.
- [ ] Run applicable strict root and independent starter source/binary comparisons
      against fresh Central `4.4.0`, plus assembled consumers without reactor leakage.
- [ ] Verify behavioral/configuration compatibility separately; an unchanged
      signature does not prove the extension, default or lifecycle contract unchanged.

### [ ] 10.2 Verify shared-boundary, native and optional parity

- [ ] For affected shared boundaries, run supported Java/Boot dependency rows and
      production/mock/consumer variants of the accepted extension cases.
- [ ] Preserve minimal no-Caffeine consumers and applicable missing integrations;
      verify replacement bean selection and factory shutdown where touched.
- [ ] When reflection, bean creation or lifecycle changes, run AOT and native
      compile/execution from exact clean source and record binary/toolchain hashes.
- [ ] Document each omitted/reused lane with its scope-based reason and provenance;
      a required but unavailable check stays pending, not implicitly passed.

### [ ] 10.3 Assess hot-path cost and evidence integrity

- [ ] For hot-path/allocation changes, compare identical workloads with published
      `4.4.0`; keep fixture setup, cold/warm state and metrics selection equivalent.
- [ ] Separate benchmark discovery/smoke from controlled measurements and public
      claims; provide manual commands when the measurement lane is external.
- [ ] Use deterministic gates for concurrency and controlled JVM lanes for any
      reachability assertion; do not certify behavior from sleeps or forced GC.
- [ ] Retain reports, exact sources, hashes and limitations; for review-only or
      unaffected paths, document not applicability instead of inventing new results.

## Priority 11 - Maintainer and Operations Guidance

### [ ] 11.1 Publish the reviewed architecture and extension guidance

- [ ] Link the reviewed map, scenarios, findings and decision record from one
      maintainer entry point without creating redundant sources of truth.
- [ ] Document the supported SPI for each demonstrated need, its phase/order,
      invocation cardinality, ownership and limits, including connector replacement.
- [ ] Preserve public-internal distinctions, no-change rationales and deferred
      reconsideration triggers; do not document discarded spikes as supported APIs.

### [ ] 11.2 Consolidate canonical public and operations guidance

- [ ] Correct only contradictions demonstrated by the review; align examples with
      actual required dependencies, policy selection and customization constraints.
- [ ] Distinguish published `4.4.0` behavior from any accepted candidate additions;
      do not select a release or claim an implementation through documentation alone.
- [ ] Tie troubleshooting to exported signals or explicitly collected bounded
      evidence available at the stated observation time, including shutdown.
- [ ] Keep support artifacts structural and sanitized; exclude request material,
      credential/identity data, arbitrary error text and unverified diagnostic claims.

### [ ] 11.3 Verify guidance and recorded scope

- [ ] Run link, archive, generated-readiness and affected example/fixture guards;
      record actual totals and any negative-case coverage added by the review.
- [ ] Reconcile every reviewed area with a conclusion and every accepted change
      with tests and guidance; unresolved questions retain named evidence needs.
- [ ] Ensure review-only guidance does not require unavailable metrics, forced
      publication, or a fabricated performance/memory/mesh result.

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
