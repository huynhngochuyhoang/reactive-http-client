# V32 Architecture Finding Register

> **Status:** open for review; two confirmed extension gaps, no accepted implementation
> **Baseline:** [verified scope and evidence](BASELINE-SCOPE.md)
> **Decision owner:** maintainer, through [Priority 8.3](CHECKLIST.md)

Priority 1 established the register structure. Priority 3 populated F001 and F002
from the external-consumer scenarios below. This is not an exhaustive defect
inventory. Other reported hypotheses remain in the baseline record until
reproduced or bounded by later review.

## Required Finding Fields

Assign stable `V32-F001`-style IDs when recording an investigated issue; do not
renumber or reuse IDs after disposition. Keep one section per ID with these
fields rather than duplicating its evidence across roadmaps.

| Field | Required content |
|---|---|
| Need and origin | Reported application workflow or explicitly exploratory scenario; affected user and impact |
| Classification | Verified behavior, confirmed gap, documented constraint, intentional non-goal, or unresolved question |
| Contract and owner | Existing promise, source/module, decision owner, lifecycle, supported SPI versus internal cooperation |
| Evidence | Reachable reviewed revision and dirty/clean state; source/test references, deterministic reproducer, actual commands/results/hashes and missing observations |
| Alternatives | No change, guidance, existing helper/SPI, local correction, or narrowly justified new boundary |
| Tradeoffs | Correctness/security, binary/source/behavior compatibility, optional dependencies, concurrency, retention and hot-path cost |
| Priority and dependencies | Safety first, reproduced supported-extension blocker next, evidenced maintenance cost after; dependency order |
| Disposition | Unresolved, fix now, document, retain intentionally, defer with owner/trigger, or reject; dated rationale |
| Acceptance and rollback | Observable assertions, required consumer/native/performance lanes, failure conditions and bounded rollback |
| Decision reference | Maintainer approval, date, selected alternative/scope and verification budget, or explicitly not selected |

Separate observations from hypotheses. A large class, historical fix count,
unchecked proposal box, absent test, or smoke benchmark flag alone is not a
confirmed defect. Do not store real headers, request targets, bodies, cache
keys, credentials or identities in source-controlled reproductions.

## Decision Gate

No production change is authorized by the findings below. Their current-behavior
tests are characterization, not desired behavior to preserve after an accepted fix.

## V32-F001: Starter Builder Is Misclassified Through ApplicationContext

| Field | Recorded evidence / disposition |
|---|---|
| Need and origin | Exploratory E12: an application selects caching with the starter-managed builder and correctly classifies its own customizers |
| Classification | Confirmed extension/startup-validation gap, not a security bypass or a reported production incident |
| Contract and owner | Cache customization inventory excludes the actual starter-managed builder; application/Boot customizers and replacement builders require classification. Owner: starter factory and CacheCustomizationValidator |
| Evidence | Reachable source `c017b234a3770e41c6cc54d16440de611941f347`, uncommitted external fixture. `ExtensionScenariosTest#starterManagedBuilderClassificationDiffersBetweenFactoryAndContextLookup` validates through ConfigurableListableBeanFactory, then observes the same config fail proxy creation through ApplicationContext with no request. See [E12](EXTENSION-SCENARIOS.md) and Priority 3 stage reports |
| Enforcing path | [Factory.getObject][factory] passes ApplicationContext into [metadata validation][metadata]; [isStarterManagedBuilder][customizations] immediately returns false unless that object itself is ConfigurableListableBeanFactory. The context is not that interface, even though it owns the required bean definitions |
| Alternatives | Retain current behavior with an explicit inspected `starterWebClientBuilder: SAFE` entry; or narrowly make definition lookup understand the owning context while retaining replacement/customizer checks. Do not skip validation or trust arbitrary beans by name alone |
| Tradeoffs | Extra application configuration and runtime/AOT/inspection drift versus a narrowly corrected lookup. Replacement-builder safety, hierarchy ownership and lazy non-instantiation must remain intact; no new public SPI or dependency is required by this evidence |
| Priority and dependencies | Review blocker for the claimed default-builder exemption in this context path. Priority 4 compares selection entry points; Priority 7 checks assembled/AOT linkage; no broad redesign inferred |
| Disposition | Unresolved, 2026-09-14. Review owner: maintainer/selection review. Reconsider at Priority 4 and scope gate 8.3; affected full-Boot combinations with a different Boot-provided builder are not inferred from this fixture |
| Acceptance and rollback | Default-owned builder needs no extra classification in either path; actual Boot/per-client/replacement mutations still reject when unclassified; hierarchy/lazy tests and external consumer pass. Roll back any correction that accepts an unclassified application mutation |
| Decision reference | Priority 8.3: not selected, no implementation approval |

## V32-F002: Fresh Static Metadata Requires an Inaccessible Derived Value

| Field | Recorded evidence / disposition |
|---|---|
| Need and origin | Exploratory E06: a replacement MethodMetadataCache builds a fresh MethodMetadata with public method/path/return-type setters rather than delegating to the built-in parser |
| Classification | Confirmed public-extension contract gap. Client validation succeeds but calling the method throws NPE before returning a publisher |
| Contract and owner | [MethodMetadata][metadata-value] documents its no-arg constructor, accessors and parsing mutability as compatibility-covered; the [replacement-bean guide][replacement-guide] supports metadata parsing replacement. Owner: metadata/planning and invocation |
| Evidence | Same reachable source and uncommitted fixture as F001. `ExtensionScenariosTest#freshStaticMetadataCannotSupplyTheInternalEffectiveApiThroughPublicConstruction` observes no dispatch/observer/lifecycle terminal on invocation failure. `ExtensionScenariosTest#customMetadataAndDecoderWorkThroughPublicReplacementBeans` proves the public API-ref alternative works; source and actual reports are linked from [E06](EXTENSION-SCENARIOS.md) |
| Enforcing path | Default parsing creates [EffectiveApi][effective-api], a package-private record. The public metadata setter exposes that inaccessible type. [Handler.resolveEffectiveApi][handler] returns a null static value for fresh non-API-ref metadata, and invoke dereferences it instead of deriving the effective method/path from the public metadata |
| Alternatives | Delegate unchanged built-in parsing when sufficient; tested public alternative sets an API-ref name and declares its method/path in ApiConfig. A narrow planner fallback from public metadata is a candidate for review, not selected work. Do not require package relocation, reflective access, or promotion of internal types just to make the consumer pass |
| Tradeoffs | API-ref workaround duplicates parser/configuration information. A correction must preserve method/API-ref precedence, timeout defaults, generic return grammar and cached-plan semantics without expanding public internal cooperation accidentally |
| Priority and dependencies | Reproduced blocker for fresh static parser implementations. Priorities 4 and 7 review runtime/mock/AOT/export consistency and compatibility; not a claim that delegating replacements all fail |
| Disposition | Unresolved, 2026-09-14. Review owner: maintainer/planning review. Reconsider at Priority 4 and 8.3; no public fallback has been implemented |
| Acceptance and rollback | External fresh valid static metadata dispatches the declared target without access to an internal type; invalid metadata fails with deliberate validation, API-ref alternative remains valid, return/timeout/identity contracts and assembled/AOT checks pass. Roll back if derived precedence or per-method plan behavior changes unintentionally |
| Decision reference | Priority 8.3: not selected, no implementation approval |

## Implementation Gate

No production change is authorized by baseline completion or by creating a
finding. Priority 8.3 must record the maintainer's selected IDs, alternatives,
scope, compatibility/dependencies, verification and rollback in the eventual
architecture decision record. An urgent confirmed defect needs an explicit
expedited decision too. Until then implementation and release remain unselected.

No-change conclusions are valid. Deferred findings require an owner or concrete
reconsideration trigger and named missing evidence; deferral is not completion
of an implementation. Accepted blocking work must be resolved or explicitly
removed from scope before Priority 12 closes the review. Review-only/no-release
is a supported final outcome, not a failed release.

[factory]: ../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/core/ReactiveHttpClientFactoryBean.java
[metadata]: ../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/core/MethodMetadataCache.java
[customizations]: ../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/core/CacheCustomizationValidator.java
[metadata-value]: ../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/core/MethodMetadata.java
[effective-api]: ../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/core/EffectiveApi.java
[handler]: ../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/core/ReactiveClientInvocationHandler.java
[replacement-guide]: ../../docs/18-conflict-cardinality-guardrails.md
