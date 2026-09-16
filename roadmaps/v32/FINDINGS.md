# V32 Architecture Finding Register

> **Status:** F004/F005 accepted for implementation; F001-F003 deferred; no fixes delivered yet
> **Baseline:** [verified scope and evidence](BASELINE-SCOPE.md)
> **Decision owner:** maintainer, through [Priority 8.3](CHECKLIST.md)

Priority 1 established the register structure. Priority 3 populated F001 and F002
from the external-consumer scenarios below. Priority 4 adds F003 from a paired
AOT/runtime properties-selection test. Priority 6 adds F004 from failed handler
construction with live registry ownership. This is not an exhaustive defect
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

No production change is authorized by a finding alone. The dated Priority 8.3
decision below authorizes only F004's bounded correction and F005's test work.
Current-behavior tests are characterization, not behavior to preserve after a fix.

### Priority 8 Consolidation

Reviewed 2026-09-16 at reachable `15d0d16bc81f92ed920679f61754e8df0e32bfd2`.
[ARCHITECTURE-DECISION.md](ARCHITECTURE-DECISION.md) ranks the confirmed gaps
F004, F003, F001, F002, then F005 by impact and records alternatives, per-item
acceptance/verification budgets, compatibility/rollback and all reviewed-area
no-change conclusions. Their causes remain separate; no shared resolver defect
or connection to the reported pod-memory incident has been established.

The maintainer explicitly approved F004 cleanup plus F005's test-lane foundation
on 2026-09-16, with F001-F003 deferred using their documented workarounds and
named reconsideration triggers. The decision record freezes the selected scope,
acceptance, verification and rollback. Earlier review-stage disposition sections
remain historical observations; the per-finding dispositions below are current.
No correction has run. Release scope stays unselected.

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
| Disposition | Deferred by maintainer, 2026-09-16. Owner: factory/cache-validation maintainer. Use explicit SAFE classification of the inspected starter builder plus application customizations. Reopen when this workaround blocks a consumer or builder lookup changes; different full-Boot builder combinations still need evidence |
| Acceptance and rollback | Default-owned builder needs no extra classification in either path; actual Boot/per-client/replacement mutations still reject when unclassified; hierarchy/lazy tests and external consumer pass. Roll back any correction that accepts an unclassified application mutation |
| Decision reference | [Priority 8.3 decision](ARCHITECTURE-DECISION.md): F001 deferred, not fixed or approved for implementation |

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
| Disposition | Deferred by maintainer, 2026-09-16. Owner: metadata/planning maintainer. Delegate built-in parsing or use tested public API-ref metadata/configuration. Reopen when neither serves a real parser; invalid/generic/timeout fallback cases still need evidence |
| Acceptance and rollback | External fresh valid static metadata dispatches the declared target without access to an internal type; invalid metadata fails with deliberate validation, API-ref alternative remains valid, return/timeout/identity contracts and assembled/AOT checks pass. Roll back if derived precedence or per-method plan behavior changes unintentionally |
| Decision reference | [Priority 8.3 decision](ARCHITECTURE-DECISION.md): F002 deferred, not fixed or approved for implementation |

## V32-F003: AOT Properties Selection Bypasses Non-Primary Precedence

| Field | Recorded evidence / disposition |
|---|---|
| Need and origin | Exploratory Priority 4: an application has two initialized ReactiveHttpClientProperties beans and selects one with non-fallback, priority or default-candidate metadata |
| Classification | Confirmed AOT/runtime selection gap. Runtime creates a valid cache-selected client; AOT rejects the same client because it reads the other properties object |
| Contract and owner | Runtime Factory.getObject uses Spring's direct getBeanProvider(...).getIfAvailable() selection. The AOT processor must validate the effective application properties rather than whichever singleton was registered first. Owner: AOT properties resolution |
| Evidence | Reachable source `6023a9132d2569108c55b2bf90bbceb7fd00084c`, plus uncommitted review tests. `EffectiveSelectionAotReviewTest#aotFirstSingletonFallbackDiffersFromRuntimeNonPrimarySelection` has four cases: primary passes both paths; non-fallback, comparator priority and sole default candidate pass actual runtime FactoryBean creation but fail AOT with missing selected cache policy. [Selection record](EFFECTIVE-POLICY-SELECTION.md) links source and actual stage results |
| Enforcing path | [Processor.properties][aot-properties] handles a unique primary, then returns the first containsSingleton bean without applying subsequent Spring candidate selection. The fixture initializes both definitions and proves getBeanProvider selects the second before invoking the processor |
| Alternatives | Explicitly designate the intended programmatic properties bean primary, as the passing control and existing AOT test demonstrate; retain/document a stricter AOT constraint; or correct this bounded properties lookup to honor effective runtime selection while retaining environment binding and supported build-time replacement beans. No universal diagnostics/runtime resolver is required |
| Tradeoffs | Misleading AOT rejection or validation of the wrong configuration versus selection-correct build-time resolution. Properties and metadata may legitimately be created for AOT, unlike diagnostic inspection. A correction must preserve primary/environment behavior, parent and FactoryBean constraints, avoid duplicate prototype creation, and not instantiate business clients merely to inspect properties |
| Priority and dependencies | Reproduced supported-component selection blocker; Priority 7 reviews AOT/module and build-time constraints before the Priority 8.3 implementation decision |
| Disposition | Deferred by maintainer, 2026-09-16. Owner: AOT selection maintainer. Designate the intended properties bean primary. Reopen when that workaround is unavailable or the lookup changes; parent/prototype permutations remain unverified. JVM processor evidence is not an observed native-image failure |
| Acceptance and rollback | The same preferred properties validate in runtime and AOT for all four cases; invalid preferred configuration still fails, inactive beans cannot mask it, existing primary/environment/foreign-factory tests remain green. Add proportionate current-consumer/native evidence for an accepted correction; roll back if selection instantiates business clients or discards programmatic configuration |
| Decision reference | [Priority 8.3 decision](ARCHITECTURE-DECISION.md): F003 deferred, not fixed or approved for implementation |

## Priority 5 Composition Disposition

The [invocation/composition review](INVOCATION-COMPOSITION.md) adds counted
factory/probe/retry/auth paths and strengthens refresh/redirect observations.
No new confirmed finding; distinct caller/source lifetimes, finalized publication
checks and frame-aware continuations are retained. F001-F003 remain unresolved.
Concrete change dependencies are recorded without authorizing an extraction or
second pipeline. Priority 6 owns remaining resource/teardown questions; Priority
8.3 still owns implementation scope. Review date: 2026-09-15.

## V32-F004: Rejected Handler Construction Abandons Cache Meter Leases

| Field | Recorded evidence / disposition |
|---|---|
| Need and origin | Exploratory Q1 / Priority 6: public handler creation rejects authenticated caching when no provider/base URL is supplied, while cache telemetry and a long-lived registry are enabled |
| Classification | Confirmed partial-construction retention gap. Validation is correct, but each rejected creation leaves a registered metric owner and empty cache graph without a returned cleanup owner |
| Contract and owner | [Handler.create][handler] creates the manager, then calls a constructor that can reject. [Manager.createForClient][manager] rolls back its own failures, but no guard closes a successfully returned manager if the later constructor throws. Owner: handler assembly before transfer to the factory/caller |
| Evidence | Reachable source `c11d281330b48bcae3917f83bd2048913d03dcca` plus the recorded review test. `ResourceOwnershipReviewTest#rejectedPublicHandlerConstructionLeavesMeterLeasesWithoutAReturnedOwner` repeats rejection three times, with and without a live same-tag owner. Each rejected call adds one owner and 16 to the maximum-entry gauge; closing the valid owner and context leaves three owners and 48 capacity. [Ownership review](RESOURCE-OWNERSHIP.md) records commands, fresh reports and limits |
| Root and enforcing path | [Metrics.SHARED][cache-metrics] -> SharedMeter.owners -> metrics instance -> registry; gauge suppliers also retain the cache/removal callback/manager graph. The WeakHashMap registry key does not break this value-to-registry strong path. No GC timing assumption, response dispatch or populated-cache measurement is required to observe these accumulating owners |
| Alternatives | Use the provider-aware overload with valid auth/base URL, or disable unneeded caching; reject known-invalid inputs before manager allocation; or add a local failure cleanup guard around handler construction. Early validation alone does not cover every later constructor/custom component failure. Test-only reflective lease cleanup is not a public workaround |
| Tradeoffs | A local assembly guard avoids a new API or hot-path abstraction. It must close only the newly allocated manager, preserve the original exception and suppress cleanup failures, and leave supplied WebClient/auth/registry plus other live owners untouched |
| Priority and dependencies | Reproduced resource leak on rejected construction, not proof of the earlier production pod-memory report. Priority 7 reviews public/helper creation paths; Priority 8.3 selects any correction. A Spring factory normally passes auth inputs; this exact failure does not establish that all ordinary factory startups leak |
| Disposition | Accepted for bounded local cleanup, 2026-09-16; implementation pending in Priority 9. Owner: invocation-assembly maintainer. Blocking for the selected scope until verified or explicitly removed; not proof of the reported pod-memory cause |
| Acceptance and rollback | Repeated rejected creation leaves the registry and same-tag live owner unchanged; no unreturned manager lease remains. Cover auth-input rejection and a later assembly exception, with and without telemetry, preserving no-Caffeine rollback and external component ownership. Run focused lifecycle/consumer tests and assess AOT/native impact for the actual patch; roll back on successful-owner disposal or altered validation |
| Decision reference | [Priority 8.3 decision](ARCHITECTURE-DECISION.md): F004 scope, verification and rollback approved. Current-behavior assertions must change with the fix; no general assembly refactor authorized |

## Priority 6 Ownership Disposition

[RESOURCE-OWNERSHIP.md](RESOURCE-OWNERSHIP.md) records the terminal/lock matrix,
construction controls, application connector and independent-load ownership,
overlapping meter teardown and historical memory limits. F004 is the only new
confirmed gap. No global deadlock-freedom, universal shutdown deadline, GC
collectability or current pod/RSS conclusion is claimed. Remaining legacy
collection-dependent tests are an evidence-lane follow-up for Priority 7.

## V32-F005: Ordinary Reachability Tests Depend on Explicit GC

| Field | Recorded evidence / disposition |
|---|---|
| Need and origin | Priority 7 follow-up from ownership review: ordinary Maven tests must not misclassify a collector that ignores System.gc as retained application ownership |
| Classification | Confirmed test-environment/verification gap, not a production memory leak |
| Contract and owner | CacheCallerAdmissionContractTest's two terminalPreparationReleasesArgumentsContextAndAuthWhileManagerStaysOpen cases require weak references to clear after forty GC/sleep requests. Owner: test/ownership maintainer; normal Surefire has no collector prerequisite |
| Evidence | Reachable source 6fb540fa9e7b73657a247caa6ea4c2a19a774d48, production and reproducer unchanged. SerialGC with 512 MiB heap and -XX:+DisableExplicitGC: two failures at weak-reference assertions; same selector with -XX:-DisableExplicitGC: two passes. Separate original XML/logs and exact commands retained under Priority 7; see [module/evidence review](MODULE-EVIDENCE-BOUNDARIES.md) |
| Alternatives | Document a restrictive test-JVM prerequisite; or retain deterministic terminal/slot-release assertions in normal tests and move actual reachability probes to a controlled opt-in lane using the existing AsyncHandoffReachabilityIT pattern. Longer sleeps do not create a collection guarantee |
| Tradeoffs | Test-only scope; no runtime/API dependency or behavior change. Lane migration must not silently delete reachability coverage or make a controlled test appear to have run in the ordinary suite |
| Priority and dependencies | Test reliability before claiming supported-JVM full-suite compatibility. Review 16 collection-dependent cases identified in E7-02; only the two caller cases were freshly compared under both settings |
| Disposition | Accepted for controlled reachability testing, 2026-09-16; implementation pending in Priority 9. Owner: test/ownership maintainer. Blocking for selected scope. Preserve all 16 inventoried scenarios and deterministic ordinary-suite witnesses; no tests migrated yet |
| Acceptance and rollback | Ordinary affected tests pass with explicit GC disabled using deterministic ownership witnesses; opt-in reachability verifies collector/heap prerequisites and retains original object-owner scenarios. Preserve command/count separation; roll back a migration that weakens observable release assertions |
| Decision reference | [Priority 8.3 decision](ARCHITECTURE-DECISION.md): F005 bounded test-lane work approved. No production instrumentation or unrelated test rewrite authorized |

## Priority 7 Module and Evidence Disposition

[MODULE-EVIDENCE-BOUNDARIES.md](MODULE-EVIDENCE-BOUNDARIES.md) records creation,
optional linkage, native rerun triggers and E7-01 through E7-08 limitations.
Minimal physical classpath and focused mock/OTel/AOT checks complement, not
replace, isolated published evidence. Existing F001-F004 remain unresolved;
F005 is a separate test-environment finding. Package sharing alone establishes
no need for a module split or new SPI. No production change is authorized.

## Implementation Gate

No production change is authorized by baseline completion or by creating a
finding. The [architecture decision record](ARCHITECTURE-DECISION.md) records
the maintainer's 2026-09-16 approval for F004/F005 only, including dependencies,
compatibility, acceptance, verification and rollback. Adding a deferred finding
or broader change needs a new explicit decision. Release scope remains unselected.

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
[manager]: ../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/core/LocalResponseCacheManager.java
[cache-metrics]: ../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/core/MicrometerLocalResponseCacheMetrics.java
[replacement-guide]: ../../docs/18-conflict-cardinality-guardrails.md
[aot-properties]: ../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/config/ReactiveHttpClientBeanFactoryInitializationAotProcessor.java
