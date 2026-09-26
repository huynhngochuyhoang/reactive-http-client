# V33 AOT Properties Selection

> **Recorded:** 2026-09-24
> **Source base:** `a81447c85739d375d8b1b32fffeae8c2df37dcc3` plus the reviewed Priority 5 patch
> **Implementation:** V32-F003 implemented within documented lifecycle boundaries; shared verification pending
> **Published / development:** `4.4.1` / `4.5.0-SNAPSHOT`
> **Release scope:** unselected

Companion to [Priority 5](CHECKLIST.md), the approved [fix decision](FIX-DECISION.md)
and the historical [V32 selection review](../v32/EFFECTIVE-POLICY-SELECTION.md).
The latter and V33 Priority 2 retain their original defect observations. This
record does not turn those passing characterization tests into passing fix tests.

## Selection and Binding

The [AOT processor][processor] now uses Spring's `resolveNamedBean` through a
short-lived non-eager selection view for properties and metadata. Instance creation
remains delegated to the owning factory. It no longer implements primary handling followed by a
registration-order singleton fallback. The selected bean name is retained only
for the binding step, not as a new registry or public selection API.

| Boundary | Contract and evidence |
|---|---|
| Primary, non-fallback, priority, default candidate | The four paired [review cases][review-tests] now require AOT success against the runtime provider's selected instance. Each formerly failing non-primary case passes. |
| Invalid preferred value | All four preferences select a zero-TTL policy while an inactive bean and environment contain valid policies. AOT fails selected-policy validation, without falling back. |
| Ambiguity | Two ordinary candidates and two primary candidates retain Spring's non-unique error, even with valid environment configuration. A dependency-creation failure also propagates. |
| No properties bean | Only absence permits binding `reactive.http` from the supplied environment, or defaults when no environment was supplied. |
| Hierarchy | Parent-only selection, child same-name shadowing, and a local candidate beside a parent primary follow the runtime provider. An unrelated child bean or alias sharing the selected parent's name cannot supply its binding environment or metadata; the selected name is preserved until its owner is found. An opaque parent supporting only provider lookup is consulted when named resolution cannot delegate to it. |
| Lazy/prototype/FactoryBean | Unique lazy and prototype properties, typed factories, raw factories with product-type definition metadata, a directly registered singleton factory, and a prototype factory produce one properties object per AOT lookup, matching independent runtime-oracle factories. Factory constructor counts are also checked. Uninitialized factories with unpredictable product types are not probed by selection; no visible candidate permits the environment fallback. |
| Boot binding | AOT refresh does not install ordinary binding post-processors. A temporary properties-only callback delegates to the owning factory's registered Boot binding processor and compares that same instance, including subclass overrides. It follows the direct-only prefix (including context awareness) and precedes auto-detected ordinary application post-processors and initialization callbacks, including `@Bean` binding metadata. Rediscovered singleton processor beans follow auto-detected ordering. Predictive type checks determine registration groups; the factory's dependency comparator (or `OrderComparator` fallback) orders the priority group, with comparator ties retaining type-discovery order. Only identities named in actual processor discovery count as auto-detected: a directly installed dual-role factory stays in the prefix when only its distinct product is discovered. The callback is installed only when the standard registered processor instance is absent from the chain; unrelated binder subclasses do not suppress it. It is removed in `finally`. A fallback binds definition-backed instances resolved earlier, without replaying initialization. Tracking creation-time binding by bean name prevents rebinding a later wrapper, without suppressing binding of distinct prototype instances. No environment-derived replacement object is substituted. |
| Existing values and products | Singleton presence does not establish that binding ran. Definition-less direct registrations remain application-prepared; a direct registration coexisting with a properties definition follows that definition's binding contract. Ordinary FactoryBean products retain factory-supplied values; runtime does not run the ordinary before-initialization binding pass on those products. |
| Scoped proxies | A selected Spring ScopedProxyFactoryBean is resolved using its initialized proxy's public target-source metadata, with definition metadata as a fallback. Opaque programmatic proxies can instead use a build-time read of the cached singleton factory's target name. This covers `@Bean` and supplier-configured singleton factories without a definition property. A successfully typed by-name target lookup supplies the instance type; owner traversal follows that named lookup's aliases without requiring precise type prediction. Binding uses the owner's target definition, including its `@Bean` prefix, and validation reads that same instance instead of asking a prototype proxy for another target. The owning scope must be available at build time; no request/session scope is activated. |
| Normal refresh | When the binding processor is already installed, normal creation performs binding and AOT does not repeat it. An environment change after refresh does not overwrite that prepared value. |
| Foreign client factory | An annotated interface backed by a foreign factory is excluded before properties or metadata lookup; its factory and product remain uncreated. |

The [new contract suite][contract-tests] compares default and alternate-prefix
`@Bean` configuration after `refreshForAotProcessing` with separately refreshed
runtime contexts. It also rejects an invalid bound policy. Programmatic beans
without Boot binding infrastructure remain application-prepared values rather
than being overwritten with environment defaults. Existing primary programmatic,
environment-only, foreign-factory and reflection-hint controls remain in the
[AOT smoke suite][smoke-tests].

New properties are bound after context awareness and before initialization during this processor's lookup.
For objects created by an earlier AOT processor, fallback binding cannot undo
callbacks already run on defaults; initialization is not replayed. Application property
constructors/factory methods and custom binders/converters remain application
code. They must not depend on business traffic or produce owned transport/cache
resources during configuration creation. Definition-less prepared registrations
are not hot-reloaded, and their preparation is the registering application's
responsibility. The public bean-factory API does not reliably distinguish a
direct registration under an existing definition from that definition's early
AOT-created singleton, so both follow the declared binding contract.

## Ownership and Limits

Binding preserves the registered processor's return value: creation-time replacements
and proxies continue to subsequent processors and initialization, while a null result
stops the remaining before-initialization chain. Fallback can use a replacement for
validation, but does not replace an already registered singleton or replay its init
callbacks. Definition lookup strips a selected factory's `&` prefix while retaining
the selected factory object. A `ScopedObject` implementation alone is not proof of
a scoped proxy; ordinary implementations and resolved scoped targets are bound.

Installed non-singleton processor identities cannot be read from the singleton
registry. A unique concrete predicted type can associate an installed processor
with its discovery name without creating a replacement; ambiguous type matches
fail explicitly. This does not establish arbitrary custom registration provenance.
Use singleton processors or distinct concrete types for that boundary.

Non-eager selection does not disable framework-owned discovery during Spring's
earlier AOT refresh, dependency injection into a selected bean, or Boot binding
advisor lookup. Characterization cases below distinguish these paths from the
starter's candidate discovery. Product-type metadata and resource-free configuration
constructors remain necessary; no universal non-instantiation guarantee is made.

Properties and replacement metadata may be created. The new fixtures count
metadata creation separately and install failing/counting suppliers for the
business client factory, auth provider, transport provider and WebClient builder.
Selected caching plus cache telemetry is configured in programmatic cases; the
meter registry stays empty. No business factory is obtained, so its starter
cache-manager, transport and signer assembly cannot run. These are assertions
about this lookup boundary, not instrumentation of every application constructor.

Diagnostics is unchanged: it does not call the eager AOT selection helper.
The diagnostics/provider and component-selection controls retain non-instantiation
and supported unknown values. F004 failed-construction lease/owner checks run
with explicit GC disabled; no controlled reachability or universal collectability
claim is made here.

No public API, configuration property, dependency, coordinate, runtime resolver,
request-path or diagnostics behavior is changed. The new inventory, candidate
selection and binding occur during AOT processing, not each subscription.
There is no new static cache or retained configuration model.

Untested here: arbitrary custom BeanFactory implementations or resolver overrides,
arbitrary non-singleton processor wrappers or broad interface-only predictions,
every custom binding converter/validator, initialization before this processor, concurrent
bean-definition mutation, and native execution. The opaque-parent fixture proves
provider delegation, not access to hidden parent binding metadata. Do not infer
support for live properties mutation or universal AOT/runtime lifecycle equivalence.

## Initial Verification

Oracle JDK 21.0.8, Maven 3.9.9, Boot 4.0.0, Java target 21 and Central-only settings.
Passing test runs disable explicit GC. The source is the reviewed working-tree
patch on the reachable base above, not a clean release revision.

- Pre-fix desired-behavior run: four cases, three selection errors. Only test
  expectations were changed at that checkpoint; the old processor was retained.
- Final focused starter run: **264 passed**, zero failures/errors/skips, across
  nine classes, including 27 new selection/binding/ownership cases and the four
  converted review cases.
- Assembled consumer: **18 passed**; documentation/archive/readiness guards:
  **73 passed**, zero failures/errors/skips. The consumer retains the existing
  primary programmatic properties witness; it is not new assembled evidence of
  every non-primary preference. Final total: **355 passing cases**.
- No full-suite, strict API, Boot 4.1, native or benchmark result is substituted
  by these focused checks.

Earlier unsuccessful iterations are preserved: a fixture constructor-generic
compile error, 13 missing SAFE classifications for the deliberately uncreated
builder, and two fixture errors around when Boot replaces a registered singleton.
They are not counted as final passing evidence. One intermediate command named
a nonexistent customization test; the final command below uses the actual
`CacheBuilderOwnershipContractTest` and its 13 cases.

## Reproduction and Evidence

Run from the root with the reviewed patch. A fresh checkout can use another
populated/writable Maven repository; its results are new evidence, not the
original preserved bundle.

```bash
export JAVA_HOME=/usr/lib/jvm/jdk-21.0.8-oracle-x64
export PATH="$JAVA_HOME/bin:$PATH"
export MAVEN_OPTS='-Xmx512m -XX:ActiveProcessorCount=2'
REPO=/tmp/v32-boot41-consumer.Z9m7kq/repository
MVN=(mvn -B -ntp -s .mvn/maven-central-settings.xml -Dmaven.repo.local="$REPO")
"${MVN[@]}" -pl reactive-http-client-starter -DargLine=-XX:+DisableExplicitGC \
  -Dtest=AotPropertiesSelectionContractTest,EffectiveSelectionAotReviewTest,ReactiveHttpClientAotSmokeTest,ReactiveHttpClientDiagnosticsProviderTest,ReactiveHttpClientFactoryBeanDiagnosticsTest,ComponentSelectionReviewTest,ResourceOwnershipReviewTest,CacheBuilderOwnershipContractTest,PublicStaticMetadataContractTest test
"${MVN[@]}" -DskipTests -Dmaven.javadoc.skip=true install
"${MVN[@]}" -f .github/boot4-consumer/pom.xml \
  -Dreactive-http-client.version=4.5.0-SNAPSHOT -Dconsumer.v32.extensions=true \
  -DargLine=-XX:+DisableExplicitGC -Dtest=ExtensionScenariosTest clean test
"${MVN[@]}" -pl reactive-http-client-starter -DargLine=-XX:+DisableExplicitGC \
  -Dtest=DocumentationReleaseArtifactTest test
git diff --check
```

Evidence under `target/release-evidence/v33/priority5/` retains commands, source
and tree IDs, working-tree patches, logs, exit codes, relevant XML, reviewed
source copies, consumed JARs, consumer POM/dependency/classpath inputs and an
artifact audit. It is sealed with `SHA256SUMS`; from that directory run
`sha256sum --quiet -c SHA256SUMS`. Preserve the bundle before cleaning `target/`.
The reactor assembly is incremental and locally installed; the external consumer
is cleaned and consumes artifacts, not starter module output directories or a
published development version. JVM AOT is not a native binary.

## Binding-Order Review Correction

The follow-up on committed Priority 5 (`2281abed`) removes its singleton-presence
shortcut. Another AOT processor can resolve a definition-created properties
instance before this processor without triggering Boot binding. The former
guard silently accepted defaults for ordinary clients or rejected declared cache
policies as missing. Four desired-behavior cases reproduce that defect: two
assertion failures and two validation errors before the fix.

The corrected tests resolve properties from an earlier AOT processor and compare
the same instance's bound client policy with a separately refreshed runtime
context. Both default class binding and an alternate-prefix `@Bean` are covered,
with and without annotation-selected caching. Additional controls resolve parent
properties early, reject an early-resolved invalid policy, and distinguish a
definition-less direct registration from one still backed by a binding definition.
Normal runtime binding, FactoryBean products, materialization counts and zero
business-resource creation remain covered. The old mixed-registration exemption
was based on the same invalid inference and is explicitly superseded here.

Review evidence is separate from the sealed initial bundle, under
`target/release-evidence/v33/priority5-binding-order/`. The focused command is the
same nine-class command above; the regression-only command uses
`-Dtest=AotPropertiesSelectionContractTest#propertiesResolvedByAnEarlierAotProcessorAreStillBound`.
The documentation command above is also rerun. The initial consumer/API/native
scope and results are not relabeled as verification of this correction.
Final follow-up results: **271 focused cases** (34 in the selection contract) and
**73 documentation cases**, zero failures/errors/skips, explicit GC disabled.
The separate review bundle preserves red/green XML, commands, source base and
reviewed patch and is sealed with `SHA256SUMS`.

## Scoped-Proxy Review Correction

The follow-up on `6e3ebf5c` distinguishes Spring scoped proxies from ordinary
FactoryBean products. The prior blanket FactoryBean exclusion left scoped
properties targets unbound during AOT processing. Four pre-fix cases reproduce
cache-policy rejection or missing ordinary-client configuration, while separately
refreshed runtime contexts bind the target correctly.

That revision used the selected scoped-proxy definition's `targetBeanName` and
the owning bean factory, then applied the existing binding rules to that target.
The creation-time review below supersedes the definition-only target lookup.
Returning the resolved target for this validation pass avoids binding one
prototype instance and validating a different one. This is a build-time
configuration sample, not a change to runtime scoped-proxy dispatch. Ordinary
FactoryBean products remain excluded. Custom scopes must already be registered
and usable; inactive request/session scopes are not manufactured or silently
replaced with environment/default configuration.

Nine new cases cover class-based scoped and prototype proxies with and without
cache selection, an alternate `@Bean` prefix, invalid preferred configuration,
an already-created unbound scoped target, normal runtime binding without rebind,
and parent-owned scoped properties despite an unrelated child name collision.
Creation counters require one target per validation pass; existing ownership
assertions require no business client, transport, signer or cache telemetry.

The nine-class focused command above passes **280 cases**, including **43**
selection-contract cases. The documentation command passes **73 cases**; both
runs have zero failures/errors/skips and disable explicit GC. The initial fixture
compile error (duplicate metadata prefixes) and four pre-fix errors are retained
under `target/release-evidence/v33/priority5-scoped-binding/`, separately from
earlier sealed evidence. The regression-only command uses
`-Dtest=AotPropertiesSelectionContractTest#scopedPropertiesBindTargetMetadataAndUseOneTarget`.
Source/commands/patch, red/green XML and hashes are preserved with `SHA256SUMS`.
Consumer, strict API, supported-Boot matrix and native checks were not rerun for
this correction; their remaining gates and historical claims are unchanged.

## Creation-Time Binding Review Correction

The follow-up on `a2f30f65` addresses programmatic scoped proxies and initialization
ordering. Three pre-fix cases fail: ordinary properties reach `@PostConstruct`
unbound, while `@Bean` and supplier-created scoped proxies fail because their
definitions have no `targetBeanName`. Both factories configure the target through
`setTargetBeanName`; the supplier declares its product type for AOT discovery.

The processor now reads the initialized proxy's public `Advised` target source
before consulting definition metadata. It temporarily installs a properties-only
binding callback ahead of initialization processors in each applicable owning
factory, before resolving metadata or properties. Newly created ordinary beans
and scoped targets therefore bind during creation, not after `getBean` returns.
An identity set prevents a second binding pass when selected properties are
returned. Cleanup removes these callbacks even when binding or initialization
fails. Ordinary FactoryBean products, definition-less prepared singletons and
normal refresh with Boot binding already installed retain their prior behavior.

Nine new cases compare ordinary, `@Bean`-proxy and supplier-proxy configuration
with independent runtime contexts. Each successful case requires one construction,
one binding pass and the same bound values in `@PostConstruct`, `InitializingBean`
and a custom init method. Six failure cases cover conversion errors and rejected
init methods, requiring the original post-processor list and zero business
resources afterward. Existing early-instance, hierarchy, materialization-count,
FactoryBean, diagnostics and ownership controls remain passing. Previously run
initialization callbacks are not replayed or repaired by fallback binding.

The nine-class focused command above passes **289 cases**, including **52**
selection-contract cases; the documentation command passes **73 cases**, with
zero failures/errors/skips and explicit GC disabled. These are **362 passing
cases**, not a full-suite or native claim. Review evidence under
`target/release-evidence/v33/priority5-creation-binding/` preserves the reachable
source base, reviewed patch, commands, logs, XML and `SHA256SUMS`. Initial fixture
compilation/injection/product-discovery errors are retained separately from the
three final pre-fix errors. Earlier sealed bundles are unchanged. Consumer,
strict API, supported-Boot matrix and native execution were not rerun here.

## Awareness-Order Review Correction

The follow-up on `95e7b5e1` moves temporary binding behind context-awareness
infrastructure, before the merged-definition processors installed by AOT refresh.
This retains awareness before binding and binding before init-annotation callbacks,
without sorting or replacing the existing processor list. If no merged-definition
processors are installed, the temporary callback follows the existing infrastructure;
`InitializingBean` and custom init methods still run afterward.
The ordinary-processor correction below supersedes this merged-definition-only
insertion boundary; its recorded test results remain historical evidence.

The three existing lifecycle success cases now require `EnvironmentAware` and
`ApplicationContextAware` before the binding setter can run. The setter checks
the injected environment against the owning context and the bound configuration;
the full callback sequence matches separately refreshed runtime contexts for
ordinary, `@Bean`-proxy and supplier-proxy properties. All three fail on the prior
processor with `awareness callbacks must precede binding`. The six failure cases
also assert awareness delivery before binding failure or init-method rejection,
while retaining processor-list cleanup and zero business-resource assertions.

The nine-class focused command above passes **289 cases** (including **52** in the
selection contract), and the documentation command passes **73 cases**, all with
zero failures/errors/skips and explicit GC disabled. These strengthen existing
cases, not additional test counts. Separate evidence under
`target/release-evidence/v33/priority5-awareness-binding/` preserves the source
base, patch, red/green XML, commands and logs, sealed with `SHA256SUMS`. Earlier
bundles are unchanged; consumer/API/matrix/native checks were not rerun.

## Ordinary-Processor Review Correction

The follow-up on `71e18003` no longer uses the first merged-definition processor
as the insertion boundary. Ordinary application processors can already be
registered ahead of that trailing internal group. Three paired runtime/AOT cases
reproduce the resulting AOT failure with `unbound initialization: ordinary` while
normal refresh binds first.

Temporary binding now follows Spring's awareness-infrastructure prefix and any
regular `PriorityOrdered` processor ahead of Boot's binding order, but precedes
ordinary application processors and the trailing init processors. Awareness
infrastructure is identified by its Spring type names (including superclass
checks), since some of those classes are package-private. No private member is
invoked and no optional servlet class is linked. The existing processor order is
not sorted or replaced, and temporary registration is still removed in `finally`.

Six new cases install an ordinary non-merged processor before AOT validation,
as an earlier AOT processor may do, with the merged-definition group at the end.
For ordinary, `@Bean`-proxy and supplier-proxy properties, the complete sequence
matches independently refreshed runtime contexts: awareness, binding, application
processing, init annotation, `InitializingBean`, custom init. Three of those cases
also require a highest-priority regular processor to run after awareness but
before binding. The tests verify the selected values, original processor-list
restoration and zero business-resource creation; existing failure cleanup and
earlier-instance controls remain covered.

The nine-class focused command above passes **295 cases**, including **58**
selection-contract cases; the documentation command passes **73 cases**, zero
failures/errors/skips, with explicit GC disabled. Separate evidence under
`target/release-evidence/v33/priority5-ordinary-processors/` preserves the three-case
pre-fix errors, final XML, commands, source base, reviewed patch and hashes.
The incoming uncommitted checklist addition is retained in that patch. Earlier
sealed bundles and consumer/API/matrix/native evidence are not relabeled as
verification of this correction; those suites were not rerun here.
The direct-registration correction below supersedes the class-name-based
awareness scan while retaining these historical results.

## Opaque Scoped-Proxy Review Correction

The follow-up on `1d032418` covers programmatic scoped factories using
`setOpaque(true)`: their proxy intentionally hides `Advised`, and their bean
definition need not contain the target name. A five-case pre-fix lifecycle run
retains three successful controls and two target-name errors for opaque `@Bean`
and supplier factories, even though the independent runtime contexts succeed.

After public proxy and definition metadata are exhausted, AOT reads the
`targetBeanName` field declared by `ScopedProxyFactoryBean` from its already-cached
singleton instance. This is a narrow, build-time dependency on Spring's internal
field, accessed through `ReflectionUtils`; it is not application-facing reflection
or a runtime/native-image request path. No factory is created for this fallback,
no proxy is rebuilt or made non-opaque, and no target name is guessed. An opaque
factory outside the singleton cache must supply definition metadata; unavailable
metadata still fails rather than instantiating a different factory. The existing
target lookup/binding path then applies the correct target prefix and scope.

Thirteen added cases cover opaque `@Bean` and supplier proxies across lifecycle
ordering, ordinary/higher-priority application processing and binding/init failure
cleanup. The success cases require the same proxy instance, no `Advised` exposure,
and one prototype target with one binding pass. Further controls cover an
already-created scoped target, a normally bound target without rebind, and
parent-owned target metadata despite an unrelated child bean of the same name.
Existing ordinary FactoryBean and business-resource ownership controls remain.

The nine-class focused command above passes **308 cases**, including **71**
selection-contract cases; the documentation command passes **73 cases**, with
zero failures/errors/skips and explicit GC disabled. Evidence under
`target/release-evidence/v33/priority5-opaque-scoped-binding/` preserves the pre-fix
run, final XML, commands, source base, patch and `SHA256SUMS`. Earlier sealed
bundles are unchanged. Consumer/API/matrix/native suites were not rerun; supported
Spring upgrades must retain or deliberately replace this field-access bridge.

## Direct Registration and Target Alias Correction

The 2026-09-25 follow-up on `606c4035` preserves processors directly installed
during bean-factory post-processing before temporary binding, even when they
implement `Ordered` or `PriorityOrdered` with lowest precedence. It identifies
already-materialized processor beans by identity, leaving the direct-registration
prefix in place and inserting binding before auto-detected ordinary processors.
Higher-priority regular bean processors and the trailing merged-definition group
retain their existing rules. No processor list is sorted or replaced.

Singleton FactoryBean products are inspected only through Spring's cached-product
accessor, using build-time reflection; this classification never calls `getObject`
or creates another processor. This replaces the prior awareness-type-name scan.
As with the opaque scoped-factory bridge, supported Spring upgrades must retain
or deliberately replace this internal accessor dependency. Scoped target names
are canonicalized against each owning factory's alias registry before type,
definition and fallback binding checks. An alias does not bypass the target's
configuration-properties metadata, and binding uses the already-resolved instance.

The initial eight desired-behavior cases fail on the previous production code:
six direct-registration cases bind too early, and two early-created alias targets
remain unbound. The final twelve ordering cases compare independent AOT/runtime
contexts for ordinary and opaque-proxy properties, with ordinary, ordered and
priority-ordered direct registrations followed by either an ordinary processor
bean or a cached FactoryBean product. Two alias-chain cases cover non-opaque and
opaque proxies with one early-created target. The cases verify bound values,
callback sequence, no duplicate creation, original processor-list restoration
and zero business-resource creation.

The nine-class focused command above passes **322 cases**, including **85** in
the selection contract. The documentation command passes **73 cases**, all with
zero failures/errors/skips and explicit GC disabled: **395 passing cases** total.
Separate evidence under `target/release-evidence/v33/priority5-registration-alias/`
preserves the pre-fix errors, reviewed patch, commands, XML and `SHA256SUMS`.
Earlier sealed evidence is unchanged. Consumer/API/matrix/native suites were not
rerun; this is reviewed-patch evidence, not a clean release qualification.

## Product Discovery, Parent Names and Wrapped Properties

The 2026-09-25 follow-up on `59e2b386` narrows cached-product classification to
factory names whose exposed type matches `BeanPostProcessor`. Products added
directly while their factory reports null or a non-processor type remain in the
direct prefix, as at runtime. No additional processor product is created.
This refines the earlier unconditional cached-product classification.

Owner traversal now retains the inherited holder name across unrelated child
aliases and canonicalizes it only after finding the local properties owner.
This supersedes rewriting the holder at every descendant, while preserving the
early-created scoped-target alias-chain controls. Temporary binding also records
successful creation-time binding by canonical bean name. Fallback binding skips
a final wrapper for that name; creation callbacks still bind each distinct
prototype instance, and genuinely early unbound instances still receive fallback.
Tracking remains local to the temporary callback and is discarded after lookup.

The pre-fix run records **13 cases: five failures, five errors, three passing
controls**. Four opaque-product cases compare runtime/AOT callback ordering;
seven wrapper cases cover ordinary properties, four scoped-proxy registration
forms and two same-name prototype creations during one lookup. Two added parent
alias cases cover fresh and early-created parent values, alongside the existing
same-name controls. Each retains processor-list and business-resource assertions.

The nine-class focused command passes **335 cases**, including **98** selection
cases; documentation guards pass **73 cases**, all with zero failures/errors/skips
and explicit GC disabled (**408 passing cases** total). Separate evidence under
`target/release-evidence/v33/priority5-product-owner-binding/` preserves source,
patch, red/green reports, commands and `SHA256SUMS`. Earlier sealed bundles remain
unchanged. Consumer, API, supported-Boot matrix and native suites were not rerun;
this does not close the remaining release gates.

## Stable Processor Ordering Review

The 2026-09-25 follow-up on `c2d61fc7` resolves equal `PriorityOrdered` values using
the bean names returned by Spring's non-eager processor type discovery, including
the binding processor's own position. A processor registered before that binder
stays before it; registering it afterward does not move it ahead. Cached processor
identities now retain their bean names for this comparison, without materializing
additional processors or sorting the installed chain.

The reported ordinary bean-backed direct-prefix defect does **not** reproduce on
the tested Boot 4.0.0 / Spring 7.0.1 baseline. Normal refresh rediscovering the same
singleton removes its direct occurrence and re-registers it in auto-detected order
(`AbstractBeanFactory.addBeanPostProcessors` removes existing occurrences before
appending the group). Six independent runtime/AOT cases cover ordinary, ordered
and priority-ordered bean-backed direct registrations with ordinary and opaque
scoped properties. They require one runtime occurrence after the binder and the
same bound callback sequence in AOT. No production change forces these discovered
beans into a direct-only prefix; that would disagree with this runtime oracle.

Four further cases cover equal-priority definitions on both sides of the binder
for ordinary and opaque scoped properties. The initial ten-case run has **two
failures and eight passing controls**; only the registered-first ties fail before
the correction. The final nine-class focused run passes **345 cases**, including
**108** selection-contract cases, and documentation guards pass **73 cases**:
**418 passing cases**, zero failures/errors/skips, with explicit GC disabled.
Separate evidence under `target/release-evidence/v33/priority5-stable-processor-order/`
retains the source base, reviewed patch, commands, XML and `SHA256SUMS`. Earlier
sealed bundles remain unchanged. Consumer/API/matrix/native suites were not rerun;
the runtime ordering claim is limited to the tested baseline and fixtures.

## Registered Binder and Scoped Target Review

The 2026-09-25 correction on `ff2100cd` obtains the registered binding processor
once for each temporary callback and uses its actual `getOrder()` and binding
implementation. A default-class instance no longer supplies a potentially different
order. Processor identity classification is also restricted to Spring's actual
non-eager discovery names: when only a FactoryBean's product is discovered, the
directly installed factory object is not treated as an auto-detected `&beanName`.

Scoped targets already resolved through `getBean(name, ReactiveHttpClientProperties.class)`
now use by-name ownership and alias traversal. Their actual resolved type does not
need to be predicted again before fallback binding. Top-level type-based properties
selection still preserves parent holder names across unrelated child aliases.

Twelve added cases cover a binder subclass ordered after a preparing processor,
dual-role factories with separately discovered products, and broad target metadata
in custom/prototype scopes, including opaque proxies, early targets and parent
ownership through a child alias. The broad-target fixture supplies an explicit
programmatic proxy class and a predictor retaining `Object` after target creation;
it does not claim an ordinary untyped scoped proxy can infer a properties type.
Every successful fixture retains processor-list restoration, one target/product
creation where applicable, and zero business-resource assertions.

The initial fixture iterations had target-source initialization ordering and
discovery/prediction setup errors; they are retained separately, not counted as
defect evidence. The corrected pre-fix run records **ten cases with six errors and
four passing fresh-target controls**. The final nine-class focused command passes
**357 cases**, including **120** selection-contract cases; documentation guards
pass **73 cases**, all with zero failures/errors/skips and explicit GC disabled
(**430 passing cases** total). Commands, reviewed patch, source base, XML, logs and
`SHA256SUMS` are under `target/release-evidence/v33/priority5-custom-binding-targets/`.
Earlier sealed bundles are unchanged. Consumer/API/matrix/native suites were not
rerun, and remaining release gates stay pending.

## Factory Identity and Binder Result Review

The 2026-09-25 correction on `53a3fb0d` separates selected factory identity from
definition identity. An annotated properties FactoryBean selected as `&name` keeps
its factory instance, while canonical definition lookup and Boot binding receive
`name`. Ordinary products keep their existing no-rebind contract. Scoped-proxy
exclusion now requires the owning factory to be a `ScopedProxyFactoryBean`, not
merely a properties object implementing the public `ScopedObject` interface.

The temporary adapter returns and tracks the registered binder's result, including
replacement objects, proxies and null. Newly created replacements reach initialization
and final selection. Early-instance fallback uses a replacement for validation only;
it neither rewrites the singleton registry nor replays callbacks already completed.

Thirteen added cases compare independent runtime and AOT contexts: fresh/early
annotated factory objects, ordinary `ScopedObject` implementations, opaque and
non-opaque scoped targets implementing that interface, fresh/early binder replacements
and proxies, and the null-result stop-processing contract. Every successful AOT case
checks restoration of the processor list and absence of business resources.

The initial ten-case run has **two failures, seven errors and one passing fresh-factory
control**. The final nine-class focused command above passes **370 cases**, including
**133** selection-contract cases; documentation guards pass **73 cases**, with zero
failures/errors/skips and explicit GC disabled (**443 passing cases** total).
Evidence under `target/release-evidence/v33/priority5-binding-identity/` retains the
source base, reviewed patch, commands, logs, XML and `SHA256SUMS`. Earlier bundles
are unchanged. Consumer/API/matrix/native suites were not rerun and remaining
release gates stay pending.

## Binder Discovery and Comparator Review

The 2026-09-25 correction on `235ba71f` tests the chain for the exact standard
registered binding processor, not any instance of its base class. A directly
installed or separately named observer-only subclass therefore does not prevent
the missing standard processor's temporary callback from being installed.

Placement now uses the owning `DefaultListableBeanFactory` dependency comparator,
with `OrderComparator` as the fallback, matching the installed Spring 7.0.1
registration implementation. Comparator ties retain discovery order. Predictive
`isTypeMatch(name, PriorityOrdered.class)` checks preserve registration groups:
a cached priority-ordered product advertised as an ordinary or merely ordered
processor remains after the binder. The actual product still participates in
comparison when its predicted group is priority-ordered. Direct-only prefixes and
the trailing merged-definition processor group retain their existing treatment.

Ten added paired runtime/AOT cases cover unrelated direct/named binder subclasses,
a custom comparator placing processors on both sides of the binder contrary to
numeric order, and ordinary/ordered FactoryBean type predictions. Ordinary and
opaque-scoped properties are covered. All ten pass runtime checks and fail AOT
before the correction; the initial run records **ten errors**. Final verification
passes **380 focused cases**, including **143** selection-contract cases, plus
**73 documentation cases**, with zero failures/errors/skips and explicit GC
disabled (**453 passing cases** total). The fixtures assert processor-list
restoration, no business resources, and one factory product where applicable.

Evidence under `target/release-evidence/v33/priority5-processor-discovery/` retains
the source base, reviewed patch, commands, logs, XML and `SHA256SUMS`. Earlier
bundles are unchanged. Consumer/API/matrix/native checks were not rerun and
remaining release gates stay pending.

## Non-Singleton and Non-Eager Review

The 2026-09-25 correction on `ed87c4ee` recovers a non-singleton processor's discovery
name from a unique concrete type when no singleton/product-cache identity exists.
Ordinary prototypes, non-singleton products, and products of prototype factories
therefore stay after binding without a second creation. Ambiguous type associations
fail explicitly instead of silently selecting a registration order.

Spring 7.0.1's `resolveNamedBean` has no non-eager overload; its provider's
`allowEagerInit` flag applies to streams, not `getIfAvailable`. A private short-lived
selection view retains Spring's candidate rules and comparator while delegating
non-eager name/type discovery and all creation to the original owner. It covers
both metadata and properties, including configurable ancestors. No original
definitions, singleton registrations or factory settings are changed. A raw
properties factory must declare a predictable product type to participate without
prior initialization. The existing raw-factory fixture now supplies that metadata;
a separate unhinted control requires no factory/product creation and environment fallback.

Fourteen added cases cover six paired non-singleton processor cases, four unrelated
unknown-factory cases across local/parent and present/absent properties, the unhinted
properties-factory control, ambiguous processor types, and two framework-boundary
characterizations. The initial ten-case run records **four failures and six errors**.
An intermediate focused run exposed the old raw-factory fixture's eager-discovery
assumption. Additional probes demonstrated that Spring's AOT refresh can create a
raw factory before this processor, and creation initiated here can reach eager
framework dependency/advisor discovery. The final characterizations identify those
framework stack paths rather than treating them as fixed selection behavior.

Final verification passes **394 focused cases**, including **157** selection-contract
cases, and **73 documentation cases**, with zero failures/errors/skips and explicit
GC disabled (**467 passing cases** total). Evidence under
`target/release-evidence/v33/priority5-non-eager-selection/` preserves source base,
reviewed patch, commands, logs, XML, intermediate failures and `SHA256SUMS`.
Earlier bundles are unchanged. Consumer/API/matrix/native checks were not rerun;
remaining release gates stay pending.

## Aliased Binder and Broad Non-Singleton Review

The 2026-09-26 correction on `54bf6ffc` derives the binding processor's discovery
index from the registered delegate's identity. Boot's standard bean name may be
an alias for a differently named definition; equal-priority processors retain the
definition discovery order on either side of that binder.

Non-singleton products advertised only through a processor interface or a broader
base class cannot be reliably associated with an installed object. AOT now fails
explicitly before properties creation instead of treating them as direct-only
registrations. The error names the processor and recommends a singleton or unique
concrete product type. No replacement prototype or factory product is requested.
Existing uniquely typed non-singletons and cached broad singleton products retain
their tested support. This is an explicit AOT limitation, not broad-product parity.

Ten added cases cover both alias registration orders and ordinary/opaque-scoped
properties, plus non-singleton products advertising ordinary/ordered interfaces
or an ordered base class despite an actual priority-ordered product. Runtime
binding succeeds in all paired fixtures. After correcting the alias fixture to
rename the binder after configuration parsing, the pre-fix run records **eight
failures among fourteen cases** (six existing/control cases pass). The initial
fixture iteration is retained separately, not counted as production evidence.

Final verification passes **404 focused cases**, including **167** selection-contract
cases, and **73 documentation cases**, with zero failures/errors/skips and explicit
GC disabled (**477 passing cases** total). Tests assert no extra product creation,
no business resources, and restoration of the processor chain. Evidence under
`target/release-evidence/v33/priority5-aliased-binder-products/` preserves the source
base, reviewed patch, commands, logs, XML and `SHA256SUMS`. Earlier bundles are
unchanged. Consumer/API/matrix/native checks were not rerun; remaining release
gates stay pending.

## Mixed Direct Chains and Selected-Creation Failures

The 2026-09-26 correction on `860f89f7` identifies direct-only registrations across
the installed chain. Stable grouping places them before rediscovered processor
beans, including when a bean-backed processor was installed before a definition-less
preparer. The existing predictive groups, comparator and binder-alias tie rules
then place the temporary binder. The original chain order is restored on success
or failure; processors registered during lookup are retained afterward.

The non-eager selection view now wraps named bean lookup failures in
`BeanCreationException`, retaining their original cause. A selected bean's required
same-type dependency lookup can no longer be mistaken for an absent outer candidate
and silently replaced by environment configuration. Scoped bean/factory creation
can expose this exception directly; ordinary supplier and FactoryBean product
failures already wrapped by Spring remain propagation controls. Existing absence,
ambiguity and opaque-parent selection cases remain covered.

Eighteen added cases include six paired mixed-chain cases (ordinary, ordered and
priority-ordered processor beans with ordinary/opaque-scoped properties), eight
paired creation-failure cases across local/parent owners, and four chain-restoration
cases covering success/failure and processors added during lookup. The pre-fix
twenty-case run has **four failures and six errors**; ten controls pass. No business
resources are created, each failed selected materialization is attempted once, and
the failure paths never reach configuration validation.

Final verification passes **422 focused cases**, including **185** selection-contract
cases, plus **73 documentation cases**, with zero failures/errors/skips and explicit
GC disabled (**495 passing cases** total). Evidence under
`target/release-evidence/v33/priority5-direct-chain-failures/` retains source base,
reviewed patch, commands, logs, XML and `SHA256SUMS`. Earlier evidence is unchanged.
Consumer/API/matrix/native checks were not rerun, and remaining release gates stay
pending. This correction does not expand the documented non-singleton type support
or change Spring/Boot's separate eager creation/advisor-discovery boundaries.

## Installed Delegates and Processor Lifetime Review

The 2026-09-26 correction on `557b7c95` retains fallback binding state even when the
standard registered delegate is already installed. Definition-backed singletons
registered before the delegate's canonical singleton name can receive the missing
binding pass. Local/parent selections and ordinary/opaque-scoped singleton targets
retain instance identity. Normal bound instances and newly created beans continue
to use the installed delegate without an additional temporary binding callback.

Prefix regrouping now stops at the first discovered merged-definition processor,
the registration boundary used by Spring 7.0.1's AOT refresh. Definition-less
processors appended after that boundary by an earlier AOT processor remain late,
even when their concrete class implements `PriorityOrdered`. Restoration keeps
the relative order of surviving original processors and retains new registrations;
removed original processors, including a self-removing callback, are not restored.

Eighteen added cases cover four local/parent early-singleton scenarios, two installed
binder/scoped-target scenarios, four paired late-processor scenarios, and eight
self/other-removal scenarios across ordinary/opaque-scoped properties and successful/
failed initialization. The initial twenty-case run has **eight failures and eight
errors**; four existing controls pass. An additional probe of early custom-scoped
targets with an installed delegate failed twice: those targets are not in the
singleton registry. The final added target cases explicitly use singleton scope,
and the unsupported custom-scope variant is not claimed as repaired.
Final verification passes **440 focused cases**, including **203** selection-contract
cases, plus **73 documentation cases**
with zero failures/errors/skips and explicit GC disabled (**513 passing cases** total).
Evidence under `target/release-evidence/v33/priority5-installed-binder-lifecycle/`
retains source base, reviewed patch, commands, logs, XML and `SHA256SUMS`.

**Provenance limitation at this revision (creation tracking added below):** singleton registration order does not record
when an already-created delegate entered the processor chain. A delegate created
before properties but installed afterward is not covered by this early-singleton
repair; neither are already-created custom-scoped targets absent from the singleton
registry once the delegate is installed. Likewise, the prefix distinction relies
on retaining Spring's merged-definition registration boundary; it is not a general history of arbitrary chain
reordering by other AOT processors. Full support needs lifecycle tracking before
those operations, not more inference from the final chain. Earlier processors
should install the binder before resolving properties and leave the discovery
boundary intact. This historical rerun did not close that limitation or certify arbitrary
AOT processor ordering. Consumer/API/matrix/native and release gates are unchanged.

## Installed Non-Singleton Binders and Scoped Creation History

The 2026-09-26 correction on reachable source base `0fd38789` plus the reviewed
patch reuses an installed binding processor by its discovered bean name before
requesting a delegate. This includes prototype definitions, non-singleton products,
prototype factories and aliases of Boot's standard name. The existing unique
concrete-type association rule applies; ambiguous non-singleton provenance still
fails explicitly instead of creating another product or guessing its owner.

The auto-configuration now registers the internal `PropertiesBindingLifecycle`
merged-definition processor. Spring installs it during AOT refresh, before earlier
initialization AOT processors can create properties. It records instance identity
and installed binding-delegate identities through weak references, including
custom-scoped targets absent from the singleton registry. A delegate created before
the target but installed afterward is no longer mistaken for a completed binding
pass. Fallback records its successful binding so repeat inspection of the same
target does not bind again; newly created targets still follow creation callbacks.
Fallback also records a final wrapper whose underlying creation was already bound.
Tests verify identity rather than equality and cleared-reference pruning without
requiring garbage collection. No public API or per-request processing is added.

The initial ten-case run records **six failures and two errors**, with two passing
controls. The custom-scope regression no longer changes its target to singleton
scope. Twenty added selection cases cover installed binder scopes/aliases,
creation before installation, repeated inspection and actual auto-configuration
registration, including final scoped wrappers; three tracker cases cover identity,
replacements and weak ownership. A two-case repeat-wrapper probe exposed one
rebinding error before final-wrapper tracking was added; its control passes.
After correcting alias fixture setup to run after configuration parsing, the
ten-class final focused rerun passes **463 cases**, including **223 selection cases**
and **three tracker cases**, zero failures/errors/skips, with explicit GC disabled.
Evidence is under `target/release-evidence/v33/priority5-binding-lifecycle-tracker/`;
it retains the pre-fix runs, fixture iteration, commands, logs, XML and source patch.
The final full starter suite passes **2,062 cases**, including **73 documentation
cases**, zero failures/errors/skips, with explicit GC disabled. The final
documentation-only rerun is recorded separately; these counts overlap rather than
representing distinct cases. `SHA256SUMS` covers the reviewed files (including new
sources), complete final patch and XML-derived audit. Earlier bundles remain unchanged.

This closes the previously recorded early-singleton/custom-scope provenance gap
for contexts using the starter lifecycle infrastructure. Low-level contexts that
omit it retain only the older singleton-order fallback. The tracker observes the
chain during creation; it does not certify arbitrary mid-initialization processor
reordering, replay callbacks already run before binding, or activate unavailable
scopes. The earlier ordering/discovery and non-eager lookup qualifications remain.
Consumer/API/matrix/native evidence is not replaced by these JVM tests.

## Rollback and Remaining Gates

Rollback this selection/binding correction and its internal lifecycle observer with its desired-behavior
tests if valid prepared values are overwritten, configuration is created twice,
ambiguity is hidden, or business assembly is introduced at this boundary. Do not
restore registration-order selection as a new supported precedence rule. The
published `4.4.1` primary-bean workaround remains available on that release.

F001-F003 corrections are implemented within the qualified coverage above. Shared composed regressions,
remaining module suites, assembled programmatic non-primary selection across supported
Boot rows, strict source/binary API checks, clean-source native execution and
the Priority 8 cost decision remain in Priorities 6-8. AOT-only changes do not
justify a steady-state speed/memory claim. Release selection, signing and
publication remain unselected. V1-V32 and earlier V33 evidence are unchanged.

[processor]: ../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/config/ReactiveHttpClientBeanFactoryInitializationAotProcessor.java
[review-tests]: ../../reactive-http-client-starter/src/test/java/io/github/huynhngochuyhoang/httpstarter/config/EffectiveSelectionAotReviewTest.java
[contract-tests]: ../../reactive-http-client-starter/src/test/java/io/github/huynhngochuyhoang/httpstarter/config/AotPropertiesSelectionContractTest.java
[smoke-tests]: ../../reactive-http-client-starter/src/test/java/io/github/huynhngochuyhoang/httpstarter/config/ReactiveHttpClientAotSmokeTest.java
