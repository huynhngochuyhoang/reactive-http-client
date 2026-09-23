# V33 Starter Builder Ownership

> **Recorded:** 2026-09-23
> **Source base:** `0c2daf13ef2b8409ecfb58021b432d86a3288ce0`
> **State:** reviewed working-tree patch on that reachable commit
> **Finding:** V32-F001 implemented; F002/F003 pending
> **Published / development:** `4.4.1` / `4.5.0-SNAPSHOT`
> **Release scope:** unselected

Companion to [Priority 3](CHECKLIST.md) and the approved
[acceptance decision](FIX-DECISION.md#v32-f001).

## Correction and Boundaries

`CacheCustomizationValidator.isStarterManagedBuilder` now uses its existing
`localBeanFactory` helper for a configurable application context as well as a
bean factory. An inherited builder is inspected in its owning parent. A local
definition, singleton or alias stops ancestor traversal; a child cannot borrow
the identity of a hidden parent builder. The owning factory's configuration
definition is used even if the child shadows that configuration's name.

The proof remains the local builder definition, the expected factory method,
and a local factory definition whose class is the starter auto-configuration.
Matching builder/method names alone do not prove ownership. A factory singleton
without a definition, an uninspectable factory, and application replacements
remain subject to explicit SAFE classification. This does not create beans,
resolve factory products, introduce a selection SPI, or change the customization
inventory. It does not attempt to prove ownership for every arbitrary bean
factory wrapper or factory arrangement; unknown provenance remains conservative.

Boot and matching per-client customizers still require their own classifications,
including defaultRequest, filters, exchange functions and connectors. A SAFE
entry is an application declaration, not inferred ownership. Existing explicit
starter-builder SAFE entries remain valid. Cache identity, auth, hit gates and
transport composition are unchanged.

No public API, dependency, configuration, coordinate or hot-path change is made.
Mock/public-handler construction has no Spring builder-ownership inventory;
its passing tests are composition controls, not Spring lookup evidence.

## Verification

Oracle JDK 21.0.8, Maven 3.9.9, Boot 4.0.0, Java target 21 and Central-only
settings were used, with explicit GC disabled in test JVMs. The reused local
repository is `/tmp/v32-boot41-consumer.Z9m7kq/repository`. This is development
artifact verification, not fresh Central consumption.

| Run | Actual result | Witness |
|---|---|---|
| Pre-fix ownership regression | 12 cases, three failures, zero errors/skips | Unchanged production from the source base rejects context and inherited ownership; nine conservative controls pass |
| First corrected ownership run | Same 12 cases pass | The local lookup correction resolves the three failures |
| Final focused starter set | 321 cases, zero failures/errors/skips | Includes 13 final ownership cases and grammar/key, customizer, contract, diagnostics, JVM AOT, composition and construction-cleanup controls |
| Assembled external E12 consumer | 18 cases, zero failures/errors/skips | Actual runtime miss/hit, direct context/factory validation, JVM AOT and diagnostics accept the starter definition; application mutations still fail without SAFE |
| Mock helper controls | 75 cases, zero failures/errors/skips | Existing cache/auth/context/work and cleanup behavior remains valid |
| Documentation/archive/readiness guards | 71 cases, zero failures/errors/skips | Current F001 status, open F002/F003 and release gates, historical archive links and development/published guidance agree |

The first documentation run had one stale historical-method-name assertion;
its log/XML is retained. The guard now maps V32's original defect name to the
renamed desired-behavior test without changing the archived V32 record.

The 13 ownership cases cover local/inherited entry points, child singleton and
definition replacements, a shadowed configuration name, same-named application
factories, a wrong factory method, missing configuration definition, unknown
factory introspection, lazy/prototype application builders, a typed FactoryBean,
and inherited Boot/per-client customizers. Creation counters remain zero during
inspection; explicit subsequent getBean controls create the builder/factory
product and increment the counters. Diagnostics and AOT leave the business
client uncreated.

The external fixture now omits the redundant entry by default. Both explicit
SAFE and omitted variants pass. Its other controls prove tenant/final-target
partitioning, hit authorization and defaultRequest/client gates, bounded body
identity, auth replay, replacement exchange functions and application-owned
connectors. F002 remains a deliberately asserted baseline defect, not a fixed
static-metadata path. AOT evidence here invokes the JVM processor, not a native
binary or a full generated native application.

## Reproduction

Use the final reviewed patch with this base; the pre-fix failure is reproducible
with the original validator from the base and the new ownership test. The
original 12-case red/green XML and command logs are preserved separately from
the final 13-case suite. Historical V32 and Priority 2 records are not rewritten.

```bash
export JAVA_HOME=/usr/lib/jvm/jdk-21.0.8-oracle-x64
export PATH="$JAVA_HOME/bin:$PATH"
export MAVEN_OPTS='-Xmx512m -XX:ActiveProcessorCount=2'
REPO=/tmp/v32-boot41-consumer.Z9m7kq/repository

mvn -B -ntp -s .mvn/maven-central-settings.xml -Dmaven.repo.local="$REPO" \
  -pl reactive-http-client-starter -DargLine=-XX:+DisableExplicitGC \
  -Dtest=CacheBuilderOwnershipContractTest,DeclarativeCachePolicyTest,ReactiveHttpClientAotSmokeTest,ReactiveHttpClientDiagnosticsProviderTest,ReactiveHttpClientFactoryBeanDiagnosticsTest,EffectiveHttpClientContractExporterTest,ReactiveHttpClientCustomizerTest,ResourceOwnershipReviewTest,InvocationCompositionReviewTest,CacheKeyContractTest test

mvn -B -ntp -s .mvn/maven-central-settings.xml -Dmaven.repo.local="$REPO" \
  -DskipTests -Dmaven.javadoc.skip=true install

mvn -B -ntp -s .mvn/maven-central-settings.xml -Dmaven.repo.local="$REPO" \
  -f .github/boot4-consumer/pom.xml -Dreactive-http-client.version=4.5.0-SNAPSHOT \
  -Dconsumer.v32.extensions=true -DargLine=-XX:+DisableExplicitGC \
  -Dtest=ExtensionScenariosTest clean test

mvn -B -ntp -s .mvn/maven-central-settings.xml -Dmaven.repo.local="$REPO" \
  -pl reactive-http-client-test -DargLine=-XX:+DisableExplicitGC \
  -Dtest=MockReactiveHttpClientTest,MockCacheWorkParityTest,MockInboundContextParityTest,MockResponseCacheSupportTest test

mvn -B -ntp -s .mvn/maven-central-settings.xml -Dmaven.repo.local="$REPO" \
  -pl reactive-http-client-starter -DargLine=-XX:+DisableExplicitGC \
  -Dtest=DocumentationReleaseArtifactTest test
```

The reactor install rebuilt the changed classes and JARs; it was incremental,
not a clean-repository build. The external fixture was cleaned. Its effective
POM, dependency tree and actual Surefire classpath are captured along with the
three consumed JAR hashes, copies and comparison to reactor JARs. No starter,
helper or OTel output directory is used as a consumer dependency.

Evidence is under `target/release-evidence/v33/priority3/`: commands, source/tree
and working-tree records, red/green/final XML, logs, exit codes, reviewed source
copies and patch, artifact audit and SHA256SUMS. Verify the manifest from that
directory with `sha256sum --quiet -c SHA256SUMS`.

## Rollback and Remaining Gates

No stop condition from the approval was triggered: the change needs neither a
weaker ownership proof nor a broader inventory exemption. Revert the local
lookup change and its desired-behavior tests together if a supported hierarchy
shows incorrect ownership; published 4.4.1's inspected explicit-SAFE workaround
remains available. Never replace this proof with a bean-name-only exemption.

Only F001 is implemented. F002/F003 remain in Priorities 4-5. Shared full-suite,
strict API, supported-Boot and final clean native verification belong to later
priorities and are not claimed here. There is no new benchmark, native,
signing/publication result or release selection.
