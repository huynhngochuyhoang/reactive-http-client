# V33 Reproduction and Fix Decision

> **Recorded:** 2026-09-23
> **Reviewed source:** `9fd20c3a069ebcc101f8b314d33bb69f0afcf969` (clean)
> **Published / development:** `4.4.1` / `4.5.0-SNAPSHOT`
> **Implementation scope:** V32-F001 + V32-F002 + V32-F003 approved; F001/F002 implemented, F003 pending
> **Release scope:** unselected

Companion to [Priority 2](CHECKLIST.md) and the [baseline](BASELINE-SCOPE.md).
This record reproduces and bounds the three candidates from the
[V32 finding register](../v32/FINDINGS.md). It does not rewrite V32's deferral
or treat a passing defect-characterization test as a correction.

Subsequent implementation: [Priority 3](BUILDER-OWNERSHIP.md) corrects F001 and
[Priority 4](STATIC-METADATA.md) corrects F002.
The reproduction results below describe the pre-fix baseline, not the revised
fixture or current behavior. F003 and release selection remain pending.

## Fresh Reproductions

The existing fixtures ran unchanged before tracked edits. The three modules
were cleaned, then assembled and installed as `4.5.0-SNAPSHOT` into the
previously populated `/tmp/v32-boot41-consumer.Z9m7kq/repository`.
The external fixture consumed those JARs, not reactor output directories.
This is freshly assembled development consumption, not isolated Central
consumption or new published-`4.4.1` verification.

Oracle JDK 21.0.8, Maven 3.9.9, Java target 21, Boot 4.0.0 and Central-only
settings were used. Test JVMs disabled explicit GC. Evidence is under
`target/release-evidence/v33/priority2/`: exact commands, logs/XML, source
archive, clean-state records, effective consumer POM, dependency tree,
classpath and artifact hashes. The source commit is reachable; the baseline
fixtures and production sources are unchanged by this priority.

| Run | Actual result | Meaning |
|---|---|---|
| [ExtensionScenariosTest](../../.github/boot4-consumer/src/v32-test/java/example/v32/ExtensionScenariosTest.java) | 17 cases; zero failures/errors/skips | External F001/F002 witnesses plus delegated/API-ref and customization/auth/cache controls. Two cases deliberately assert defects. |
| [EffectiveSelectionAotReviewTest](../../reactive-http-client-starter/src/test/java/io/github/huynhngochuyhoang/httpstarter/config/EffectiveSelectionAotReviewTest.java) | Four cases; zero failures/errors/skips | Paired runtime/AOT primary, non-fallback, priority and default-candidate selection. Three cases deliberately assert AOT rejection. |

No fixture failure occurred in these reproduction runs. Expected exceptions
were caught and asserted by their tests, not hidden failed stages. Preserve
these original XML/logs before later priorities change the defect expectations.

### V32-F001: Starter Builder Ownership

Witness:
`starterManagedBuilderClassificationDiffersBetweenFactoryAndContextLookup`.

- Construction/selection: the same refreshed context and starter-owned builder
  definition are used. Boot `bootDefaults` and per-client `clientMutations`
  remain explicitly SAFE; only the redundant `starterWebClientBuilder`
  classification is removed. The fixture checks its factory-method provenance.
- Validation: `validateDeclarativeCacheCustomizations(context.getBeanFactory(), ...)`
  succeeds. Actual `context.getBean(Client.class)` enters factory validation
  through ApplicationContext and fails, naming the starter builder's missing
  classification. This compares ownership lookup, not prototype object identity.
- Dispatch: the peer request list remains empty in the rejected construction.
  The separate warm-hit control retains the explicit starter-builder entry and
  successfully constructs, dispatches once, then serves a hit while running gates.
- Cause: [CacheCustomizationValidator](../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/core/CacheCustomizationValidator.java)
  requires the lookup argument itself to be a ConfigurableListableBeanFactory
  in `isStarterManagedBuilder`; its other lookup helper already understands
  ConfigurableApplicationContext. No equivalent correction was implemented here.

### V32-F002: Fresh Public Static Metadata

Witness:
`freshStaticMetadataCannotSupplyTheInternalEffectiveApiThroughPublicConstruction`.

- Construction/selection: an external `example.v32` replacement
  MethodMetadataCache creates MethodMetadata with public method, API name, GET,
  path and response setters. Client construction succeeds.
- Invocation: `client.mapped()` throws NullPointerException before returning a
  publisher. The fresh model has no inaccessible static EffectiveApi value.
- Dispatch/terminal: no peer request, observer event or lifecycle terminal is
  observed. This is an invocation/planning failure, not a subscription error.
- Controls: `customMetadataAndDecoderWorkThroughPublicReplacementBeans`
  dispatches the API-ref mapping to `/v1/mapped`, exercises its error decoder,
  then dispatches the delegated built-in `plain` metadata to `/v1/plain`.
  It asserts three peer requests and three observer/lifecycle terminal records.
- Cause: [RequestPlan.from](../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/core/RequestPlan.java)
  copies `meta.getStaticEffectiveApi()`, and
  [resolveEffectiveApi](../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/core/ReactiveClientInvocationHandler.java)
  returns that null for non-API-ref methods. Built-in parsing supplies the
  derived value; fresh public construction cannot construct its package-private
  type. This does not justify exposing EffectiveApi.

### V32-F003: Runtime/AOT Properties Selection

Witness:
`aotFirstSingletonFallbackDiffersFromRuntimeNonPrimarySelection`.

| Preference | Direct runtime provider / actual factory | AOT validation |
|---|---|---|
| Primary | Preferred properties; client constructs | Contribution returned |
| Non-fallback | Preferred properties; client constructs | Rejects missing selected cache policy |
| Comparator priority | Preferred properties; client constructs | Rejects missing selected cache policy |
| Sole default candidate | Preferred properties; client constructs | Rejects missing selected cache policy |

Both properties definitions are already initialized. Only the preferred object
contains the selected policy. The fixture asserts the runtime provider's exact
object identity, validates that policy, invokes AOT, verifies the lazy business
factory is not a singleton, then constructs the actual runtime client.

No business method is invoked: this is selection/construction evidence, not a
wire dispatch or native-image test. It does not count every possible ancillary
allocation. The priority case uses the bean factory's comparator `getPriority`,
not a claim that every annotation/comparator combination was tested.

Cause: [AOT properties resolution](../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/config/ReactiveHttpClientBeanFactoryInitializationAotProcessor.java)
handles primary candidates but then takes the first existing singleton.
Registration order wins over the remaining runtime selection rules.

## Alternatives and Necessity

These are confirmed extension gaps within representative fixtures, not
production incident prevalence or newly observed memory/mesh failures.
No-change alternatives remain usable; selecting a fix is a maintainer decision.

| ID / owner | Consumer need and workaround cost | No-change alternative | Smallest correction / rejected expansion |
|---|---|---|---|
| V32-F001; factory/cache-validation maintainer | Ordinary starter-owned builder should not need a redundant SAFE entry only because validation receives its context; that entry couples application configuration to internal ownership naming | Explicitly classify the inspected starter builder and every applicable application mutation. Do not recommend blanket SAFE declarations | Resolve the actual owning factory/definition with existing local facilities. Keep provenance proof, hierarchy/shadowing and conservative unknowns. No new SPI or name-only exemption |
| V32-F002; metadata/planning maintainer | Supported replacement parsers should construct valid static endpoints with public fields; API-ref duplicates derived mapping in configuration and delegation may not express a custom parser | Delegate built-in parsing or use the tested API-ref configuration; document the unsupported fresh-static case | Derive missing static effective API at the existing validated planning boundary. Keep supplied-derived-value/static/API-ref precedence explicit. No public EffectiveApi, reflective workaround, new metadata model or setter redesign |
| V32-F003; AOT selection maintainer | Build-time validation should inspect runtime's chosen configuration; forcing primary may conflict with an application's intentional non-fallback/priority/default selection | Mark the intended programmatic properties bean primary and retain the documented constraint | Prefer supported Spring selection at this properties boundary, preserving binding lifecycle. No universal resolver, copied precedence algorithm or eager diagnostics path |

## Acceptance and Verification Budget

A budget means mandatory lanes and boundaries, not promised counts or elapsed
time. These are requirements for later implementation, **not passing fix evidence**.
All three target source/binary compatibility with published `4.4.1`; no new
property/default, dependency or public SPI is proposed.

### V32-F001

- Acceptance: starter-owned E12 works through bean factory, context and applicable
  AOT/inspection entry points without the redundant entry. Existing SAFE
  configurations remain valid. Application Boot/per-client customizers and
  builder mutations must still be classified, including non-filter mutations.
- Negative controls: same-named application definitions, replacements, inherited
  ownership/child shadowing, unknown factory provenance and lazy/prototype
  inspection. Count creation where needed; validation must not instantiate an
  application component just to prove ownership.
- Preserve cache identity, per-call auth and safety checks independently of the
  exemption. Neither bean name nor a SAFE label alone proves starter ownership.
- Budget: focused customization/ownership tests, external E12 positive and
  rejection controls, mock/contract/diagnostics/AOT checks where affected.
  Shared full-suite, consumer, API, supported-Boot and native gates apply below.
- Stop/rollback: any unclassified application mutation accepted, eager inspection,
  ownership ambiguity resolved by guessing, or need for a new SPI. Expected cost
  is construction/inspection only; any hot-path change reopens the cost review.

### V32-F002

- Acceptance: fresh valid static public metadata constructs and dispatches the
  declared method/target/result from an external package. Derive once at the
  existing stable plan boundary, not by reparsing each subscription.
- Negative controls: incomplete/invalid method/path/return metadata must fail
  deliberately with no dispatch. Cover inherited concrete generics, Mono/Flux,
  URI templates, supplied-derived/static/API-ref precedence, method/API/client
  timeout precedence, plan reuse and cache identity.
- Preserve public accessors and supported mutable parsing followed by immutable
  consumed plans. This does not authorize hot reload, retaining call/context
  state in metadata or mutation of already-consumed plans.
- Budget: external fresh/delegated/API-ref controls plus metadata/plan/invocation,
  export, mock and AOT tests. Shared gates apply; Priority 8 must assess planning
  and steady-state cost and run targeted measurements if the actual diff warrants.
- Stop/rollback: promoting internal types, changing a supported signature,
  precedence/timeout regression, per-call parsing/retention or broader parser
  redesign. Earlier deliberate rejection of invalid input needs a behavior note,
  even when source/binary compatibility passes.

### V32-F003

- Acceptance: all four paired cases use the same effective properties; an invalid
  preferred configuration must fail rather than select a valid inactive bean.
  Keep primary programmatic properties, environment-only binding and foreign
  client-factory controls.
- Negative controls: absence/ambiguity, parent/child shadowing, lazy/prototype and
  FactoryBean cases touched by the resolution path. Count prototype/product
  creation to detect discarded or duplicate materialization.
- Creation permissions: AOT may obtain properties and metadata for build-time
  validation after appropriate binding. Do not instantiate business clients,
  transports, cache managers or signers for that selection. Diagnostics retains
  its non-instantiating and supported-unknown rules; it cannot reuse an eager path.
- Budget: paired selection and AOT smoke/control tests, assembled programmatic
  properties consumer, supported Boot rows and clean-source native creation/
  selected-cache hints, plus shared API/full-suite gates.
- Stop/rollback: lost programmatic/environment configuration, duplicate creation,
  changed runtime selection, business-client construction during AOT selection,
  or a generic resolver extraction. A build-time-only diff needs no new
  steady-state benchmark; changes outside that boundary require review.

### Shared Gates and Exclusions

After selected corrections, Priorities 6-8 must retain F004 failed-construction
rollback and F005 deterministic/controlled-test separation. Run relevant focused
cases, full starter/helper/OTel suites, assembled and physically minimal consumers,
strict root and independent starter source/binary comparisons against `4.4.1`,
and existing Boot 4.0.0/4.1.0 rows. Use a genuine Boot 4.1 consumer parent rather
than mislabeling the fixed-4.0-parent matrix consumer. Native evidence must use a
clean reachable commit containing the final fixtures and approved corrections;
JVM processor tests do not waive compilation/executable checks.

No public performance or memory claim is selected. Priority 8 records the
targeted cost decision and any required measurements. Native/API/matrix/cost
results from V32 are historical inputs, not verification of a future changed tree.

Untested at this checkpoint: hierarchy/shadowing and arbitrary full-Boot builder
combinations; invalid/inherited/generic/timeout fresh metadata; uninitialized,
prototype or FactoryBean properties and binding lifecycle permutations; native
behavior of proposed corrections. The acceptance above supplies these later gates,
not an assertion that this 21-case baseline already covers them.

Excluded: new public extension framework, universal resolver, module split,
dependency upgrade, second metadata model, live configuration refresh, relaxed
cache safety/identity/auth, altered resilience/replay/deadline/terminal behavior,
automatic propagation or new application-resource ownership. If any is required,
stop for separate scope/proposal approval instead of expanding a selected fix.

## Maintainer Decision

**Approved, 2026-09-23.** In response to the explicit Priority 2.3 scope question,
the maintainer selected: **"F001 + F002 + F003 (recommended)"**.
This approves the three bounded corrections and the acceptance/verification
requirements above, not a release or implementation in Priority 2.

Approved order: F001 (Priority 3), F002 (Priority 4), F003 (Priority 5). None needs
another to be implemented first; this is checklist order, not a common-resolver
dependency. Reproduce before each correction and preserve baseline witnesses.

Rationale: each gap now has a fresh public-extension or paired runtime/AOT witness,
a demonstrated workaround and a correction bounded by an existing owner. They
do not share a proven root cause that justifies a common abstraction. Workaround
cost makes correction worthwhile without expanding the supported API.

No candidate ID is unselected, so the unselected-priority/deferral branch of 2.3
is **not applicable**. Priorities 3-5 are open, not complete. Workarounds remain
necessary until their corresponding correction is implemented and verified.
The original V32 dispositions remain immutable history. Adding scope or removing
an accepted fix requires another explicit decision; a stop condition above is
not permission to silently weaken its gate. Release scope remains unselected.

## Reproduction Commands

Run from the repository root. Only module targets are cleaned; do not root-clean
retained evidence. Substitute a local repository if needed, but rebuild the
candidate artifacts before claiming current assembled consumption.

```bash
export JAVA_HOME=/usr/lib/jvm/jdk-21.0.8-oracle-x64
export PATH="$JAVA_HOME/bin:$PATH"
export MAVEN_OPTS='-Xmx512m -XX:ActiveProcessorCount=2'
REPO=/tmp/v32-boot41-consumer.Z9m7kq/repository
MVN=(mvn -B -ntp -s .mvn/maven-central-settings.xml -Dmaven.repo.local="$REPO")
"${MVN[@]}" -pl reactive-http-client-starter,reactive-http-client-test,reactive-http-client-otel clean
"${MVN[@]}" -DskipTests -Dmaven.javadoc.skip=true install
CONSUMER=("${MVN[@]}" -f .github/boot4-consumer/pom.xml
  -Dreactive-http-client.version=4.5.0-SNAPSHOT -Dconsumer.v32.extensions=true)
"${CONSUMER[@]}" -DargLine=-XX:+DisableExplicitGC -Dtest=ExtensionScenariosTest clean test
"${MVN[@]}" -pl reactive-http-client-starter -DargLine=-XX:+DisableExplicitGC \
  -Dtest=EffectiveSelectionAotReviewTest test
OUT="$PWD/target/release-evidence/v33/priority2"
"${CONSUMER[@]}" help:effective-pom -Doutput="$OUT/consumer-effective-pom.xml" \
  dependency:tree -DoutputFile="$OUT/consumer-dependency-tree.txt" \
  dependency:build-classpath -Dmdep.outputFile="$OUT/consumer-classpath.txt"
"${MVN[@]}" -pl reactive-http-client-starter -DargLine=-XX:+DisableExplicitGC \
  -Dtest=DocumentationReleaseArtifactTest test
git diff --check
```

The baseline source archive and report hashes remain in the Priority 2 bundle.
Final documentation-test counts, scope status and evidence integrity are recorded
in the checklist. No strict API, native, dependency-matrix or JMH run was performed
for this reproduction/decision priority; those remain the selected-fix gates.

## Completion Evidence

The documentation/archive/readiness suite passed **70 tests, zero failures/errors/skips**
after updating two stale current-scope expectations. The initial 70-case run had
two assertion failures (old unselected/deferred wording), with no errors or skips;
its log/XML are retained under `docs-red/`. Those are documentation transition
failures, not failed reproductions or evidence that a production fix was applied.
Final verification uses the reviewed source plus the recorded scope/documentation
guard patch. Production sources, original runtime fixtures, coordinates,
dependencies, V1-V32 and proposal records remain unchanged.

`reproduction-audit.json` compares all three rebuilt JAR hashes with both the
exported dependency classpath and the actual external-test JVM classpath. It
also records the exact 17 + 4 reports, clean reproduction stages, embedded
versions and Boot 4.0.0 effective POM/JARs. The preserved reviewed source archive
has SHA-256 `16b644496d5b8bb649d6c4cd9c65b96c039a8b7b096f5279403704e6e4a0e639`.
The bundle retains the assembled artifacts and is sealed with `SHA256SUMS`;
preserve it before cleaning `target/`. A checkout without that bundle must rerun
the commands, not claim to have inspected the original evidence.

Recheck the sealed bundle from its directory:

```bash
cd target/release-evidence/v33/priority2
sha256sum --quiet -c SHA256SUMS
```
