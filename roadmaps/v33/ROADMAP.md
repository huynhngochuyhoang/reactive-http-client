# Reactive HTTP Client - Roadmap V33

> **Status:** active
> **Theme:** supported extension and AOT selection parity
> **Published baseline:** `4.4.1`
> **Development reactor:** `4.5.0-SNAPSHOT`
> **Implementation scope:** V32-F001 + V32-F002 + V32-F003 approved and implemented; shared verification pending
> **Release scope:** unselected
> **Draft date:** 2026-09-19
> **Adopted:** 2026-09-19
> **Execution:** [checklist](CHECKLIST.md); [Priority 2.3 approval](FIX-DECISION.md#maintainer-decision) recorded

This roadmap defines a bounded follow-up to the
[completed V32 architecture review](../v32/ROADMAP.md) and its
[published cleanup patch](../v32/CLOSURE-EVIDENCE.md#post-publication-closure).
Checklist adoption alone starts baseline and characterization work; it does not
reopen V32, authorize production changes, or select `4.5.0` for release.
V1-V32 remain completed records. Adoption does not complete Priority 1.

The selected corrections are the three confirmed extension gaps that V32 deferred:
**V32-F001, V32-F002 and V32-F003**. Their
[original scope decision](../v32/ARCHITECTURE-DECISION.md) and workarounds remain
valid history. On 2026-09-23 the maintainer explicitly selected all three with
the bounded acceptance and verification requirements in [FIX-DECISION.md](FIX-DECISION.md).
Priority 2 completes reproduction and selection, not implementation or release
approval. [Priority 3](BUILDER-OWNERSHIP.md) implements F001 and
[Priority 4](STATIC-METADATA.md) implements F002 and
[Priority 5](AOT-PROPERTIES-SELECTION.md) implements F003. Shared verification remains pending.

## Intent

Make already-supported application extensions behave consistently at the
boundaries where they are selected, validated and used:

1. Recognize the actual starter-managed WebClient builder without requiring a
   redundant cache-safety declaration or exempting application mutations.
2. Let a replacement metadata parser describe a valid static endpoint using the
   supported public model, without needing an inaccessible derived type.
3. Validate the same effective application properties during AOT processing that
   runtime bean selection would use, without creating business clients.

These are approved compatibility corrections, not a new extension framework.
Success means a concrete consumer no longer needs the documented workaround,
backed by negative and cross-path tests. Class size, code duplication or a desire
to extract a general resolver is not sufficient justification.

## Starting State

The inspected draft revision is
`0065feeb4293b44ffe0b5741e98eaa8059b3dad7`. It records published `4.4.1`,
development `4.5.0-SNAPSHOT`, and no active execution roadmap or selected next
release. Public, API, consumer and benchmark baselines remain `4.4.1`.
Java 21, the default Boot `4.0.0` lane and the existing Boot `4.1.0` compatibility
row are unchanged by this proposal.

V32 delivered failed-construction cleanup (F004) and controlled reachability
testing (F005). Keep those regressions and evidence boundaries intact. The table
below cites historical reproductions and source inspection, not new V33 test,
native, performance or production-incident evidence.

| Candidate | Recorded gap and present workaround | Proposed correction boundary |
|---|---|---|
| [V32-F001](../v32/FINDINGS.md#v32-f001-starter-builder-is-misclassified-through-applicationcontext) | The starter builder is exempt through its bean factory but misclassified through ApplicationContext. Explicitly classify the inspected starter builder SAFE, along with every application customization | Ownership lookup in [CacheCustomizationValidator][customizations]; no name-only exemption or relaxed cache safety |
| [V32-F002](../v32/FINDINGS.md#v32-f002-fresh-static-metadata-requires-an-inaccessible-derived-value) | Fresh static public metadata validates but lacks the internal effective API used by invocation. Delegate built-in parsing or use the tested public API-ref configuration | Static derivation at the existing [metadata][metadata]/[request-plan][plan] boundary; keep internal EffectiveApi internal |
| [V32-F003](../v32/FINDINGS.md#v32-f003-aot-properties-selection-bypasses-non-primary-precedence) | AOT prefers the first initialized properties bean after primary handling, unlike runtime non-fallback, priority or default-candidate selection. Mark the intended programmatic properties bean primary | Properties resolution in the [AOT processor][aot]; preserve environment binding and legitimate build-time application configuration |

The external E06/E12 cases in [ExtensionScenariosTest][extensions-test] and the
paired [EffectiveSelectionAotReviewTest][selection-test] are starting fixtures.
Some deliberately assert the current defect. An accepted correction must replace
those expectations with desired behavior and retain a reproducible baseline;
passing the old characterization is not proof of a fix.

## Scope and Guardrails

- Preserve published constructors, accessors, SPIs, configuration names/defaults,
  supported Java/Boot lanes and physical optional-dependency absence.
- No mandatory application migration for valid metadata or configuration.
  Earlier deliberate rejection of invalid metadata must be described separately
  from source/binary compatibility. Stop for approval if a public break is needed.
- Do not change cache eligibility, SAFE obligations, key/body/auth isolation,
  bounded storage/work, replay, timeout, terminal or health semantics.
- Runtime selection, build-time validation and non-instantiating diagnostics have
  different creation permissions. Parity means agreement on known effective facts,
  not eager creation everywhere or elimination of valid unknown states.
- Application builders, customizers, connectors, auth providers and executors
  remain application-owned. No new disposal, propagation or mutation contract.
- Retain no-hot-reload guidance. Fresh metadata construction is not permission
  to mutate a consumed plan; selecting properties does not make live setters safe.
- Do not add a public SPI, universal bean resolver, second metadata hierarchy,
  pipeline framework, module split or dependency upgrade for these corrections.
- Keep V1-V32 and historical proposal/evidence records unchanged. New conclusions
  belong in V33, with links back to the original findings.
- No new memory-leak, performance-gain or mesh-specific claim from this work.
  The earlier pod-memory report remains a separate incident-evidence question.

## Priorities

## 1. Post-`4.4.1` Baseline and V33 Scope Integrity

- Record a reachable source revision, clean/dirty state, toolchain and current
  release contract. Reuse V32 publication evidence only for its exact artifact
  and claim; distinguish fresh Central checks from installed-reactor consumers.
- Keep `4.4.1` baselines and `4.5.0-SNAPSHOT` development coordinates until an
  explicit release decision. Drafting does not change generated release readiness.
- On adoption, add an execution checklist and update the index, archive guard
  and readiness together. Active execution must remain visible through a final
  version cut until closure, not depend solely on a `-SNAPSHOT` suffix.
- Inventory the F001-F003 reproductions, remaining evidence needs and workarounds;
  retain F004/F005 as delivered safeguards, not new V33 implementation work.

Deliver a baseline/scope record. Adoption starts characterization and scope
selection, not automatic approval of all proposed production changes.

## 2. Reproduction and Explicit Fix Selection

- Reproduce the three gaps on the current development source using external
  consumer entry points and the paired runtime/AOT fixture. Record actual counts,
  commands, revisions and assertions, separating baseline failure from fixture error.
- For each candidate, state the consumer need, workaround cost, smallest local
  correction, compatibility risk, negative tests and rollback condition.
- Obtain an explicit maintainer selection of finding IDs. Freeze their change
  boundaries and verification requirements before editing production code.
- Keep unselected findings deferred with their workaround, owner and reconsideration
  trigger. Do not make closure depend on implementing an unselected candidate.

The approved set is F001 + F002 + F003. They are independent fixes; shared tests
do not establish a common root cause or require a general selection abstraction.
Priorities 3-5 apply only to selected IDs.

## 3. Starter Builder Ownership and Cache-Safety Validation

- Resolve the actual owning factory when validation is entered through either
  ConfigurableListableBeanFactory or ApplicationContext. Reuse existing local
  ownership helpers where sufficient.
- Prove the builder originates from starter auto-configuration before exempting
  it. A matching bean name, factory method name or SAFE label alone is not proof.
- Cover parent ownership, child name shadowing, application replacements and
  same-named definitions. Keep unknown/uninspectable ownership conservative;
  validation must not create lazy builders or customizers merely to inspect them.
- Continue requiring classification for applicable Boot WebClientCustomizer,
  per-client customizer and replacement-builder behavior, including defaultRequest,
  filters, exchange functions and connectors. Exemption is not blanket cache safety.
- Preserve existing explicit SAFE declarations as accepted configuration.

Acceptance: the starter-owned E12 case constructs without its redundant entry
through runtime, AOT and inspection paths; unclassified application mutations
still fail. Stop if ownership can only be inferred from a name or by weakening
the inventory. Use counted construction witnesses for non-instantiation claims.

## 4. Public Static Metadata and Effective Request Planning

- Derive a missing static effective API from validated public metadata at the
  existing planning boundary, without requiring reflection, package relocation
  or public exposure of EffectiveApi.
- Define treatment of an already supplied derived API versus public fields;
  preserve built-in parsing and deliberate API-ref precedence. Do not silently
  introduce another independently mutable effective representation.
- Validate incomplete or invalid method/path/return metadata deliberately rather
  than allowing a null dereference before a publisher is returned. State which
  errors belong to construction/planning rather than logical-call reporting.
- Cover inherited methods, concrete generic return types, Mono/Flux grammar,
  URI templates, cache method identity and existing method/API-ref/client timeout
  precedence. Preserve the tested delegating and API-ref alternatives.
- Keep derived decisions attached to the correct concrete request plan. Do not
  add repeated per-subscription parsing or retain caller arguments in metadata.

Acceptance: an external replacement parser using supported public setters sends
the declared method and target and receives the decoded result. Invalid metadata
fails deliberately with no transport dispatch. Contract export, mocks and AOT
agree where they claim that validation. Stop for a public-type promotion,
breaking setter semantics or an unapproved parser redesign.

## 5. AOT and Runtime Properties Selection Parity

- Replace registration-order preference with the applicable runtime candidate
  semantics for the properties boundary. Prefer Spring's supported selection
  facilities to duplicating precedence rules.
- Establish how ordinary environment-bound configuration and application-provided
  properties are obtained during AOT. Preserve binding lifecycle, primary
  programmatic properties and the supported foreign-factory path; blindly creating
  an unbound configuration bean is not a parity fix.
- Extend paired tests for primary, non-fallback, priority, default candidate,
  absence and ambiguity; then cover parent/child shadowing, lazy/prototype and
  FactoryBean cases actually encountered by the chosen lookup.
- An invalid preferred configuration must fail, not fall back to a valid inactive
  bean. Count materializations to detect duplicate prototype/product creation.
- Build-time properties/metadata may need instantiation; business client proxies,
  transports, cache managers and signers must not be created just for selection.
  Do not copy this eager path into non-instantiating diagnostics.

Acceptance: runtime and AOT select the same effective configuration for the
supported cases, including an invalid-selected negative control. Preserve unknown
diagnostic facts and environment-only controls. Record previously untested shapes
as limitations until tested, not as assumed Spring compatibility.

## 6. Cross-Path Contract and Ownership Regressions

- Compare factory creation, lower-level public handler entry points, metadata
  planning, effective contract export, diagnostics, mocks and AOT for each selected
  correction. Preserve documented differences in their validation responsibilities.
- Exercise a cache-selected replacement-parser client with applicable customizers
  and selected properties. Assert finalized method/target, selected policy, cache
  behavior and per-caller authorization without bypassing safety validation.
- Check construction failure, successful ownership transfer and destroy/recreate
  beside another live metric owner. Retain F004's no-leak and application-ownership
  controls; do not infer successful cleanup from an unassigned factory field.
- Preserve ordinary-suite independence from forced GC. Use the controlled
  reachability lane only for collection claims and report its results separately.

Deliver an affected-path matrix with both agreement and intentional differences.
Do not expand this into another whole-engine rewrite or reopen unrelated races.

## 7. Mock, Assembled-Consumer, AOT and Native Evidence

- Prove the accepted extension scenarios from outside starter packages against
  assembled artifacts, not only same-package unit tests. Keep mock parity separate
  from Spring wiring and transport evidence.
- Retain minimal physical classpath checks: cache-disabled without Caffeine and
  enabled-only resilience without selected operators or registry classes. New
  metadata/selection paths must not eagerly link unused optional integrations.
- Run both supported Boot rows with genuine matching consumer parents. Keep any
  overlay or exact generation command tracked and independently reproducible.
- Add JVM AOT and native witnesses for the accepted builder, fresh metadata and
  properties cases. Count server method/target and all relevant dispatches; confirm
  the preferred programmatic policy without exposing sensitive request material.
- Compile and run native evidence from a clean reachable commit containing the
  final fixtures and implementation. Record toolchain, resources, command, exit
  result, binary hash and earlier failures. Rerun if relevant source changes.

No native pass is inferred from JVM processor tests or an older binary. A resource
failure leaves the required gate pending rather than weakening the assertion.

## 8. Compatibility and Targeted Cost Evidence

- Run strict root and independent starter source/binary API comparisons against
  published `4.4.1` using isolated repositories and recorded artifact provenance.
- Review behavioral compatibility separately: removed redundant configuration,
  newly deliberate metadata errors, and corrected AOT bean preference. Keep valid
  workarounds usable unless an explicit compatibility decision says otherwise.
- Run focused affected suites and full module regressions; retain dependency,
  packaging, source/Javadoc and optional-integration checks appropriate to the diff.
- Builder inspection and AOT-only changes need no new steady-state speed claim.
  If static planning or invocation allocation changes, compare identical cold-plan
  and warm-invocation workloads against `4.4.1`, with forks, allocation measurements
  and correctness witnesses. Do not compare non-equivalent benchmark rows.
- Establish performance regression criteria before measurement. Record variance
  and limitations; leave unrelated cache throughput, RSS and native startup claims
  unchanged. An unchanged hot path may justify a documented no-benchmark decision.

Deliver immutable verification evidence for the actual accepted diff, not promised
test totals or a report that predates the last implementation/fixture change.

## 9. Maintainer, Migration and Operations Guidance

- Update the customizer, replacement-bean, cache and native guides only where an
  accepted correction changes their advice. Separate published `4.4.1` workarounds
  from behavior available in the eventual new artifact.
- Provide small external examples using public APIs and complete prerequisites.
  Do not recommend blanket SAFE declarations, reflection into internal metadata,
  or making every configuration bean primary as a permanent selection model.
- Explain runtime versus AOT creation permissions, diagnostic unknowns, factory
  recreation for configuration changes and application resource ownership.
- Add bounded troubleshooting for classification, incomplete metadata, ambiguous
  properties and invalid selected configuration. Do not export header values,
  request targets, credentials, bodies or tenant/key material as evidence.
- Cross-link the V33 result and original finding IDs without rewriting V32's
  historic observations or pretending a deferred candidate was fixed.

Deliver a concise result/migration record with remaining limitations and exact
availability by release. Keep unrelated operations and dashboard contracts intact.

## 10. Scope Decision and Conditional Release Go/No-Go

- Reconcile selected IDs with delivered corrections, explicit deferrals and all
  required evidence. Choose review-only/no-release if no production correction
  is accepted; mark inapplicable gates with reasons rather than fictitious passes.
- For delivered compatible fixes, evaluate a patch release first. The development
  coordinate does not itself authorize a `4.5.0` minor release. Any new API or
  behavior outside the selected correction needs a renewed scope decision.
- Select the exact candidate only after the diff, compatibility and migration
  report are reviewed. Update reactor/fixtures, version guards, matrix commands
  and generated readiness together without rewriting historical evidence.
- Assemble a reachable immutable evidence manifest. Keep signing, staged artifact
  checks, tag publication, Central verification and published-consumer evidence
  separate from unsigned local verification. Never mark pending gates complete.
- Close V33 only after the selected release is verifiably published and consumed,
  or an explicit no-release/no-go closure is documented. Update archive/readiness
  and baselines consistently; leave future implementation scope unselected.

## Completion Criteria

These are proposed acceptance criteria, not completed execution evidence:

- [ ] The maintainer has selected the finding IDs and bounded acceptance scope.
- [ ] Every selected gap has a baseline reproducer, desired-behavior regression
  and demonstrated external consumer result; unselected IDs remain explicit.
- [ ] Selected corrections satisfy their supported contracts without weakening
  cache safety or diagnostic privacy; remaining gaps are clearly deferred.
- [ ] F004/F005 cleanup and test-lane guarantees remain intact.
- [ ] Required API, module, consumer, Boot, AOT/native and targeted cost evidence
  identifies the actual tested revision and any failures or limitations.
- [ ] Guidance distinguishes published behavior, new corrections and workarounds.
- [ ] Release/no-release selection and archive closure have verifiable provenance.

Execution status and evidence belong in the [checklist](CHECKLIST.md). These
roadmap acceptance boxes preserve the proposal; adoption does not approve the
candidate fixes or bypass the explicit implementation gate.

[customizations]: ../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/core/CacheCustomizationValidator.java
[metadata]: ../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/core/MethodMetadata.java
[plan]: ../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/core/RequestPlan.java
[aot]: ../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/config/ReactiveHttpClientBeanFactoryInitializationAotProcessor.java
[extensions-test]: ../../.github/boot4-consumer/src/v32-test/java/example/v32/ExtensionScenariosTest.java
[selection-test]: ../../reactive-http-client-starter/src/test/java/io/github/huynhngochuyhoang/httpstarter/config/EffectiveSelectionAotReviewTest.java
