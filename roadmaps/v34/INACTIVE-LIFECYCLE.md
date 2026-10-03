# V34 Inactive Resources and Framework Lifecycle

> **Status:** Priority 8 complete, 2026-10-03
> **Delivered production IDs:** none
> **Production scope:** N/A; broader C004 and C005 deferred, C004 rolled back in Priority 6
> **Release scope:** unselected

This executes [Priority 8](CHECKLIST.md) under the existing
[scope decision](IMPROVEMENT-DECISION.md) and [rollback](HARDENING-EVIDENCE.md).
Starting clean source: `27cba549d08784a5b5d4f96c7f52e6e444e355f6`.
The patch changes tests and current guidance only. No resource optimization,
framework-selection change or production fix is delivered. Earlier dated
evidence remains historical, not silently promoted to a final release run.

## Profiles and Resource Boundaries

| Profile / owner | Fresh control | Limit / retained work |
|---|---|---|
| Present but unselected integrations | Two new factory cases have cache telemetry enabled, Caffeine and all operator types present, six lazy auth/provider-factory/registry definitions, and resilience enabled without operator names. Neither an absent cache policy nor a defined but unselected weighted/refresh/work policy creates a cache manager, token-service pool, pool registrar, meter owner or optional bean. The cold publisher dispatches only on its two subscriptions | Defining a policy is not selecting it. The controlled exchange is in-memory; it proves subscription counts, not socket behavior |
| Physical optional absence | The isolated Boot 4.0.0 consumer verifies no Caffeine, four Resilience4j registries, Micrometer registry, OTel API or test helper classes. With resilience enabled but no operators, two ordinary GET calls both reach the loopback server | Uses the freshly installed starter JAR, not reactor classes. This is not a no-op integration on the full test classpath or a Boot 4.1 parent |
| Ordinary default path | Six profile controls retain cold and repeated subscriptions, scoped scheduling counts, lazy token resources and cache absence; explicit activation tests leave unrelated lazy registries uninstantiated | The scheduling hook is installed after construction around measured synchronous mock exchanges. It is not proof of zero process-wide tasks, class loading or allocation |
| Selected resources and construction | Invalid URL with caching/telemetry selected acquires no meter lease; correcting the URL is the positive control. Rejected public construction and Caffeine absence preserve existing owners. Successful transfer, destroy/recreate and overlapping managers retain the live owner's meters until its own close | Factory business pools may exist as lazy holders. A late direct factory failure requires its caller to invoke destroy; a Spring-owned factory is destroyed on context close |
| Application-owned connector, pool and executor | The real loopback ownership test now runs auth on an application scheduler/executor. After factory and context close, the pool/scheduler/executor remain usable; a direct auth call and another request succeed | The application explicitly disposes them in test cleanup. Starter ownership does not expand to resources supplied through a custom connector or provider |
| Cache manager, active work and callers | Existing tests reconcile entries, tokens, reservations, meter leases, last-owner removal and rejected late publication. Shared loads/refreshes have manager-owned cleanup | An independent, non-single-flight foreground load can survive manager close. Its caller owns the subscription and eventual token release; close prevents repopulation, not all caller work |
| Properties binding and AOT | Normal refresh stops observation tracking even while 100 prototype/custom-scoped properties remain live. AOT history is retained until context close; late callbacks do not recreate it. Selection/order tests compare supported runtime and AOT paths | Required discovery, ordering, scoped-target handling and binding bookkeeping remain. Cleared weak-reference tests clear explicitly and do not prove GC collection |
| Diagnostics and component selection | Snapshot/summary controls do not instantiate lazy auth factories or registries. Cache/work unknowns, builder ownership, auth selection and token-service isolation remain covered | Querying diagnostics is not permission to initialize unrelated application components or infer unknown runtime state |

The two new inactive-definition cases and strengthened external-resource case are
in [ResourceOwnershipReviewTest][resources]. Configured-but-unselected cache work
is explicitly initialized in the test: optional `CacheWorkConfig` otherwise starts
null. No production defaults were changed to satisfy the fixture.

Source boundaries: [factory lifecycle][factory], [manager ownership][manager],
[shared meter ownership][meters], [properties tracking][tracking] and
[AOT selection][aot]. A dormant business-pool holder, cache identity builder or
shared scheduler holder is not by itself a leak. Their broader C004 changes remain
deferred; the evidence does not assert their allocation disappeared.

## Fresh Regression Matrix

All Java test runs use `-XX:+DisableExplicitGC`:

| Classes | Boundary | Cases |
|---|---|---:|
| `DefaultPathCostOwnershipTest`, `ExplicitResilienceActivationContractTest` | Default/off/no-registry profiles, scoped scheduling and explicit operator activation | 15 + 17 |
| `ResourceOwnershipReviewTest`, `ResponseCacheRetentionOwnershipTest` | Inactive definitions, validation/acquisition, failed construction, transferred and external ownership, cache values/tokens through close | 20 + 10 |
| `LocalResponseCacheObservabilityTest`, `CacheWorkTelemetryContractTest` | Absent/disabled telemetry, failed cache creation, overlapping owners and teardown | 17 + 21 |
| `CacheWorkOwnershipContractTest` | Foreground/refresh cancellation, entered preparation and discard ownership | 25 |
| `ComponentSelectionReviewTest`, `CacheBuilderOwnershipContractTest`, `OAuth2TokenServiceTransportIsolationContractTest` | Supported component/builder ownership, isolated token and business transports | 10 + 13 + 3 |
| `ReactiveHttpClientDiagnosticsProviderTest`, `CacheWorkDiagnosticsContractTest` | Non-instantiating diagnostics, unknown facts and work-selection snapshots | 61 + 7 |
| **Resource subtotal** | **12 classes** | **219** |
| `PropertiesBindingLifecycleTest`, `AotPropertiesSelectionContractTest`, `AotMetadataSelectionContractTest` | Runtime tracking stop, AOT selection, binding order, factory products, scopes, aliases, replacement metadata and restoration | 7 + 243 + 7 |
| `EffectiveSelectionAotReviewTest`, `ReactiveHttpClientAotSmokeTest` | Effective selection, hint/generated-code JVM witnesses | 4 + 27 |
| `ReactiveHttpClientAutoConfigurationTest`, `Boot4AutoConfigurationTest` | Framework wiring and supported defaults | 23 + 12 |
| **Framework subtotal** | **Seven classes** | **323** |
| `Boot4CacheDisabledConsumerTest` | Isolated built-artifact consumer and physical optional absence | **1** |

These are 543 distinct resource/framework/consumer cases, not a complete reactor
suite. The first focused resource run had 20 cases with one setup error: it
dereferenced the optional work configuration before creating it. That failed log
and XML remain in `tests-focused/`; the corrected 20 cases are included once in
the 219-case run, not counted again. No failure is discarded or called a passing
production result.

## Applicability and Cost Disposition

- **Production resource/framework work N/A:** no accepted production change remains
  after P6. No new measured cost or retention tradeoff is claimed. C005 and broader
  C004 require a new scope decision, not a lifecycle refactor hidden in this step.
- **Collection and RSS claims N/A:** controls inspect owned references, counters,
  leases, terminal acknowledgements and pool reuse through close. They do not infer
  heap reachability from RSS, promise immediate RSS reduction or depend on forced
  collection. Controlled reachability lanes remain separate.
- **Matched performance remains pending:** no new JMH run. The enabled-only
  allocation flag remains unresolved; P10 owns the final matched matrix and
  compatibility evidence. Zero allocation is not an acceptance promise.
- **Parity limits:** these are JVM/AOT tests and one Boot 4.0.0 consumer, not a
  new native executable or genuine Boot 4.1 consumer run. Production, benchmark,
  native/consumer fixture, dependency and coordinate inputs are unchanged for this
  priority. P9 must record final applicability/reuse or fresh required runs; no
  old native binary is relabeled as P8 evidence. Priorities 9-12 remain open.

## Verification and Reproduction

Oracle Java 21.0.8, Maven 3.9.9, effective Boot 4.0.0. Evidence is in
`target/release-evidence/v34/priority8/`: command arrays, exits, toolchains,
source/patch, exact fresh XML, consumer classpath/tree/effective POM and JAR hash.
The checklist records final documentation/Python totals and the sealed inventory
hash. Its external integrity entry is excluded from final source-copy sealing to
avoid a self-referential checksum. This dirty-patch verification is not clean-commit
release evidence. P3-P7 inventories are checked without rewriting their records.
Generated readiness remains V34 active, release lane/scope unselected and
`plannedFinalVersion` null.

```bash
export JAVA_HOME=/usr/lib/jvm/jdk-21.0.8-oracle-x64
export PATH="$JAVA_HOME/bin:$PATH"
export MAVEN_OPTS='-Xmx512m -XX:ActiveProcessorCount=2'
mvn -B -ntp -s .mvn/maven-central-settings.xml -pl reactive-http-client-starter \
  -Dtest=ResourceOwnershipReviewTest,DefaultPathCostOwnershipTest,ExplicitResilienceActivationContractTest,ResponseCacheRetentionOwnershipTest,CacheWorkOwnershipContractTest,LocalResponseCacheObservabilityTest,CacheWorkTelemetryContractTest,CacheWorkDiagnosticsContractTest,ReactiveHttpClientDiagnosticsProviderTest,ComponentSelectionReviewTest,CacheBuilderOwnershipContractTest,OAuth2TokenServiceTransportIsolationContractTest \
  -DargLine='-XX:+DisableExplicitGC -XX:ActiveProcessorCount=2' test
mvn -B -ntp -s .mvn/maven-central-settings.xml -pl reactive-http-client-starter \
  -Dtest=PropertiesBindingLifecycleTest,AotPropertiesSelectionContractTest,AotMetadataSelectionContractTest,EffectiveSelectionAotReviewTest,ReactiveHttpClientAotSmokeTest,ReactiveHttpClientAutoConfigurationTest,Boot4AutoConfigurationTest \
  -DargLine='-XX:+DisableExplicitGC -XX:ActiveProcessorCount=2' test
mvn -B -ntp -s .mvn/maven-central-settings.xml -pl reactive-http-client-starter -am -DskipTests install
mvn -B -ntp -s .mvn/maven-central-settings.xml -f .github/boot4-cache-disabled-consumer/pom.xml \
  -DargLine='-XX:+DisableExplicitGC -XX:ActiveProcessorCount=2' clean test
mvn -B -ntp -s .mvn/maven-central-settings.xml -pl reactive-http-client-starter \
  -Dtest=DocumentationReleaseArtifactTest \
  -DargLine='-XX:+DisableExplicitGC -XX:ActiveProcessorCount=2' test
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s scripts -p 'test_*v34*.py' -v
```

Recorded Maven commands select the existing isolated repository
`target/v33-native-runs/native-g0ynw95x/repository`. The consumer's dependency
commands (`dependency:build-classpath`, `dependency:tree`, `help:effective-pom`)
and output paths are retained in its command JSON. Use new output directories for
reruns and preserve failed attempts. No release or review-only closure is selected.

[resources]: ../../reactive-http-client-starter/src/test/java/io/github/huynhngochuyhoang/httpstarter/core/ResourceOwnershipReviewTest.java
[factory]: ../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/core/ReactiveHttpClientFactoryBean.java
[manager]: ../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/core/LocalResponseCacheManager.java
[meters]: ../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/core/MicrometerLocalResponseCacheMetrics.java
[tracking]: ../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/config/PropertiesBindingLifecycle.java
[aot]: ../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/config/ReactiveHttpClientBeanFactoryInitializationAotProcessor.java
