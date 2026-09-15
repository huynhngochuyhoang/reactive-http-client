# V32 Module, Optional Integration, and Evidence Boundaries

> **Status:** as-is review, Priority 7
> **Reviewed:** 2026-09-15
> **Source baseline:** `6fb540fa9e7b73657a247caa6ea4c2a19a774d48`
> **Published / development:** `4.4.0` / `4.5.0-SNAPSHOT`
> **Implementation and release scope:** unselected

Companion to [the map](ARCHITECTURE-MAP.md), [selection review](EFFECTIVE-POLICY-SELECTION.md),
[ownership review](RESOURCE-OWNERSHIP.md), [findings](FINDINGS.md) and
[execution record](CHECKLIST.md). Review began on the clean reachable revision
above; the new consumer/documentation fixtures and this record are a recorded
patch, not a clean native or release measurement.

## Cross-Module Contract

| Boundary | Reused production behavior / deliberate substitute | Conclusion |
|---|---|---|
| [Starter POM](../../reactive-http-client-starter/pom.xml) | Owns grammar, effective policy, request identity, invocation, cache, transport, reporting and hints. Optional compile dependencies permit direct implementation types without requiring every integration in consumers | Classpath absence must be tested from assembled artifacts, not inferred from optional=true or an ApplicationContextRunner alone |
| [Mock builder](../../reactive-http-client-test/src/main/java/io/github/huynhngochuyhoang/httpstarter/test/MockReactiveHttpClient.java) | Calls MethodMetadataCache validation and production handler through MockResponseCacheSupport. Writes real BodyInserters to MockClientHttpRequest and records the request only after body materialization. Supplies a matcher ExchangeFunction, its own context, default decoder and explicit test collaborators | No copied key/storage/admission algorithm. Does not exercise factory pool/TLS/proxy/redirect configuration, automatic Boot customization discovery or peer receipt. A recorded mock exchange is not a network dispatch |
| [Public internal bridge](../../reactive-http-client-test/src/main/java/io/github/huynhngochuyhoang/httpstarter/core/MockResponseCacheSupport.java) | Same-package access installs production manager, ticker, scheduler and RecordingMetrics; factory validation is reused. Session/Control expose testing snapshots and cleanup. The bridge closes its manager if handler construction fails | Public and @hidden is not private or removable. Mixed starter/helper versions and JPMS split-package support are not established. Preserve artifact version alignment; no package/module extraction is justified by package sharing alone |
| [Helper dependencies](../../reactive-http-client-test/pom.xml) | Caffeine is transitive for cache-enabled mocks; JUnit extension API is optional. retry(...) deliberately installs a lightweight test retry instead of a Resilience4j registry | Simulated retry proves handler composition, not Resilience4j admission/timing. RecordingMetrics mirrors bounded outcomes but does not prove Micrometer registration, exporter behavior or lease removal |
| [OTel companion](../../reactive-http-client-otel/pom.xml) | Depends on starter and OTel API; no reverse starter dependency. SDK/testing exporters are test dependencies. Observer consumes common logical terminal events; filters handle propagation | SDK/exporter lifecycle remains application-owned. Optional companion absence is different from API-hidden auto-configuration or no OpenTelemetry bean |
| [Benchmarks](../../reactive-http-client-benchmarks/pom.xml) and [assembled consumer](../../.github/boot4-consumer/pom.xml) | Benchmarks also use same-package internals; consumer tests use artifact JARs and public interfaces outside starter packages | Internal microbenchmarks cannot certify public extension support. External V32 scenarios are the appropriate witness for F001/F002; passing signatures alone do not resolve them |

The mock duplicates **assembly choices**, not the cache engine: its auth/final
request filters, observer registration, test clock and shutdown control are
locally composed. Production customizer/connector ordering belongs to the
factory and is separately characterized. V32-F004's public handler failure has a
local rollback counterpart in the mock bridge; this is a concrete correction
candidate, not a reason to unify every creation path.

## Optional Integration Matrix

| State and selection | Enforcing path / observing evidence | Limit of conclusion |
|---|---|---|
| Cache unselected; Caffeine, resilience registries, MeterRegistry, OTel and helper physically absent | Strengthened `Boot4CacheDisabledConsumerTest#cacheDisabledConsumerRunsWithoutCaffeine` starts full Boot against the installed starter JAR and completes two counted loopback calls. Every listed optional class is asserted absent | Actual artifact classpath, not filtered-loader simulation. Spring's required micrometer-observation / commons remain; this is not a claim that all io.micrometer classes can be removed from WebClient |
| Cache selected, Caffeine hidden | `BoundedLocalResponseCacheContractTest#optionalImplementationIsRequiredOnlyForSelectedPolicies` checks no manager when unselected and explicit dependency error when selected; `CacheWorkTelemetryContractTest#failedCacheConstructionReleasesMetersWithoutAffectingLiveOwners` checks no leaked work leases and leaves another owner intact | JVM classloader simulation for selected-cache failure; no new physically stripped cache-enabled native image |
| Resilience absent or unselected | Factory selects operator names first, resolveSafely loads registry types by name, and resolves no-op when unavailable; auto-configuration isolates metric binders behind class/bean conditions. Minimal consumer proves enabled-default/no-operator behavior without registry classes | No arbitrary partial/mismatched Resilience4j artifact matrix. Existing full-classpath registry-absence/selection cases are different from removal of individual operator classes |
| Micrometer Core absent / present without registry | Minimal consumer exercises physical Core absence. Auto-configuration tests hide classes or omit registry/binder beans. `LocalResponseCacheObservabilityTest#cacheObservabilityWithoutMeterRegistryStillRecordsCallerOutcomes` proves selected cache outcomes still reach custom observers | Context-only bootstrap tests do not themselves prove client dispatch. Cache observability is configuration-derived, not metrics.enabled(); no registry means no Micrometer cache meters, not disabled custom terminal reporting |
| Cache observability disabled or overlapping owners | LocalResponseCacheObservabilityTest and CacheWorkTelemetryContractTest exercise disabled dimensions, backend-independent rejection records, shared same-tag owners and last-owner deregistration | Pool gauges have their own pool.metricsEnabled switch. Cache-served callers stay out of the ordinary downstream timer/health input; RecordingMetrics is not the Micrometer ownership implementation |
| OTel API hidden / bean absent / properties disabled | `OpenTelemetryHttpClientAutoConfigurationTest#autoConfigurationBacksOffWithoutOpenTelemetryApi` and companion tests cover missing bean, independent spans/propagation switches, replacement names and coexistence with Micrometer | Filtered API test proves conditional registration, not a complete arbitrary classpath-removal matrix. Starter-only consumer has no companion/API at all |
| OTel propagation and terminal spans present | Companion propagation, observer and request/response-size tests use explicit SDK/test exporter and shared terminal contracts | No exporter service, mesh or application ThreadLocal bridge is involved; no automatic detached-task propagation is inferred |

Sources: [factory](../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/core/ReactiveHttpClientFactoryBean.java),
[auto-configuration](../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/config/ReactiveHttpClientAutoConfiguration.java),
[cache metric facade](../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/core/LocalResponseCacheMetrics.java),
[OTel conditions](../../reactive-http-client-otel/src/main/java/io/github/huynhngochuyhoang/httpstarter/otel/OpenTelemetryHttpClientAutoConfiguration.java).
No new SPI, module split, dependency/default change or production correction is selected.

## Runtime and AOT Creation

| Input / ownership | Runtime | AOT / native constraint and evidence |
|---|---|---|
| Properties | Spring type lookup selects effective application bean; ordinary creation may instantiate it | [AOT processor](../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/config/ReactiveHttpClientBeanFactoryInitializationAotProcessor.java) handles unique primary, then first initialized singleton, then environment binding/provider fallback. Primary programmatic bean and environment cases are tested; V32-F003 remains for non-primary precedence. Build-time instantiation of configuration is legitimate, unlike non-instantiating diagnostics |
| MethodMetadataCache | Replacement parser selected before grammar/plans, but fresh static metadata still has V32-F002's inaccessible derived-value gap | Processor gets replacement metadata via provider and validates parameters, URI, returns, cache policies/customizations. API-ref cache validation uses resolved config; the general URI call does not receive configured APIs. This is not an assertion that every runtime transport/TLS/auth/retry validation runs during AOT |
| Replacement client / FactoryBean | Registrar backs off for an advertised replacement client; replacement owner supplies its behavior | Processor accepts only ReactiveHttpClientFactoryBean-compatible definitions and advertised interface/type/generic metadata. Foreign factories are deliberately ignored, not forced through starter grammar; `ReactiveHttpClientAotSmokeTest#beanFactoryAotProcessorIgnoresAnnotatedClientsBackedByForeignFactoryBeans` observes this |
| Signature records and generics | Selected key inputs obey bounded record/shape grammar and serialization rules | Selected cache methods scan parameter ResolvableTypes, arrays, generics and record accessors; visited Type and registered record-class sets bound recursion. Proxy/method/resource hints and inherited selected record cases are asserted. Generic substitution edge graphs beyond these fixtures are not proven complete |
| Context-only records | Type arrives dynamically through selected Reactor context | Not inferable from client signatures. Application registers accessor/resource hints explicitly; `ReactiveHttpClientAotSmokeTest#applicationRuntimeHintsCanCoverContextOnlyCacheRecords` verifies that route. Header DTO parsing remains application-owned, not scanned automatically |
| Custom integrations | Runtime can call application auth, codec, factory and connector code | Their own reflection/resources and SDK/connector lifecycle must be native-compatible. Existing smoke uses explicit DTO hints and fixtures, not arbitrary custom serializers/providers |

### Native Rerun Selection

This review changes only tests and review documents. Production source and native
fixture source match reachable `v4.4.0`; the evidence inventory rechecks that
comparison. This is **not a new native build**, nor certification of a
`4.5.0-SNAPSHOT` executable. Original V31 source archive, binary hash, GraalVM
version and commands remain historical evidence via [BASELINE-SCOPE.md](BASELINE-SCOPE.md).

If Priority 8.3 accepts a change:
- Bean discovery, properties/metadata selection, optional linkage, hints, proxies
  or record traversal require JVM AOT plus applicable assembled/native reruns.
- Factory/handler/cache lifecycle changes require ownership/mock/consumer
  regressions; rerun native shutdown when that execution path changes. F004
  needs a native impact decision for its actual patch, not automatic exemption.
- Reporting/OTel/filter changes require both observer and production pipeline
  parity; native propagation/terminal paths must rerun if affected.
- Test-only documentation assertions require their focused suite. Editing any
  native fixture invalidates its old source/binary pairing even without a
  production edit. Release-quality measurements require exact clean source.

No module extraction is needed to establish these boundaries. F001-F004 keep
their existing implementation decision gate and acceptance criteria.

## Evidence Gaps

| ID / classification | Actual observation and claim boundary | Proportionate remedy / owner and trigger |
|---|---|---|
| E7-01 / confirmed test-environment gap, V32-F005 | The two parameterized cases in `CacheCallerAdmissionContractTest#terminalPreparationReleasesArgumentsContextAndAuthWhileManagerStaysOpen` fail their weak-reference null assertion with SerialGC, 512 MiB heap and -XX:+DisableExplicitGC, but pass when only that flag is reversed. This is not evidence of a production leak | Maintainer/test ownership at Priority 8. Separate deterministic release/slot assertions from collection probes; move actual reachability checks into a controlled opt-in JVM lane. Do not merely raise the timeout or remove ownership assertions |
| E7-02 / inventory, not individually rerun under hostile GC | ResponseCacheRetentionOwnershipTest has nine collection-dependent cases; CacheWorkOwnershipContractTest has five parameterized collection cases, and the caller test above has two. CacheWorkTelemetryContractTest also calls System.gc but checks live gauges, not mandatory collection | Review the 16 ordinary collection-dependent cases before claiming whole-suite portability. Existing AsyncHandoffReachabilityIT verifies SerialGC, explicit-GC availability and bounded heap in an opt-in profile; ordinary AsyncHandoffOwnershipContractTest is deterministic. This priority does not silently migrate or disable old tests |
| E7-03 / missing direct traversal witness | `ReactiveHttpClientAotSmokeTest#beanFactoryAotProcessorGuardsRecursiveGenericParameterTraversal` uses an unselected cache method. It proves the signature is accepted and method hints exist, but the current isCacheSelected gate skips record/generic traversal | AOT owner, when changing traversal: add a selected-path fixture with an actually traversed recursive graph and assertions on required hints. Do not infer the visited-set branch was executed from the old test name; no stack overflow reproduced here |
| E7-04 / native timing and instrumentation limits | NativeSmokeApplication counts the open-circuit route and unmatched requests; a one-second quiet observation supplements the rejection, unlike the former immediate check. NativeCacheWorkScenario counts every server request before routing, gates responses, waits for waiter attachment and checks both futures plus dispatch count | Native owner, next affected build: retain peer-side witnesses; coalesced/gauge counters alone cannot prove dispatch or attachment correctness. Bounded quiet periods cannot prove absence for unbounded scheduling delay |
| E7-05 / native deadline/refresh smoke, not deterministic concurrency proof | NativeCacheWorkScenario joins the later caller after eight seconds of a ten-second leader budget and uses a 120 ms refresh age wait. Suspension can exhaust the remaining deadline or hard TTL; built-in fixture timeout/SAFE/default settings are explicit in application.properties | Native owner, next fixture change: use controlled admission/attachment gates or generous independently validated timing margins, backed by deterministic JVM contracts. No scheduling failure is claimed in this review; no new smoke execution |
| E7-06 / wire and mock scope | GOAWAY fixture now records transmitted last-stream ID, completes accepted streams before a draining quiet window with spare stream slots, resets a processed upload without a response, records gzip negotiation and uses acquire deadlines beyond shutdown observation. Semantic-read flight fixture waits for source subscription and two members before error/empty, and retries the identical JSON publisher after one serialization failure | Retain these independent observations. Internal manager member counts establish attachment but not transport receipt; mock writeTo captures request encoding but not socket framing. Fresh focused wire/flight regressions are listed below, not whole H2/proxy/mesh coverage |
| E7-07 / provenance and classpath scope | Published/current verification scripts demand fresh target repositories and record artifacts/classpaths; published provenance verifies Central markers. V32-P1 retains source-archive bridges for squash-local IDs rather than asserting ancestry. Current review consumer uses freshly installed JARs in the existing local Maven repository | Retain classpath/JAR hashes and distinguish this fresh artifact-consumption run from a new isolated Central download. Next release/accepted dependency change uses the fresh-baseline lane; never relabel this run as that lane |
| E7-08 / historical revision and measurements | Prior V32-P6 correction recorded dc862d7... before squash into the current reachable 6fb540fa...; its recorded changed-source copies can be compared with this baseline. Historical V31 native/benchmark claims keep their archives/toolchains; V29/V30 raw memory bundles are not present | Use the reachable current revision plus source-copy bridge, not orphan ancestry, for reused correction evidence. No new GC collectability, heap/RSS, speed/allocation, Istio or application incident conclusion |

Wire sources: [GOAWAY fixture](../../reactive-http-client-starter/src/test/java/io/github/huynhngochuyhoang/httpstarter/core/Http2GoAwayRetirementContractTest.java),
[semantic-read flights](../../reactive-http-client-starter/src/test/java/io/github/huynhngochuyhoang/httpstarter/core/SemanticReadSingleFlightRefreshContractTest.java),
[native main](../../.github/native-smoke/src/main/java/io/github/huynhngochuyhoang/httpstarter/nativesmoke/NativeSmokeApplication.java),
[native work](../../.github/native-smoke/src/main/java/io/github/huynhngochuyhoang/httpstarter/nativesmoke/NativeCacheWorkScenario.java),
[native configuration](../../.github/native-smoke/src/main/resources/application.properties).
Evidence infrastructure: [current consumer](../../scripts/verify-current-consumer.sh),
[published consumer](../../scripts/verify-published-consumer.sh),
[Central provenance](../../scripts/verify-published-baseline-provenance.sh).
These are bounded audit findings, not authority to rewrite release tooling.

## Verification

Commands, actual XML totals, failed runs, source copies/diffs, settings,
toolchain, dependency trees/effective POMs, classpaths and SHA-256 inventory are
under `target/release-evidence/v32/priority7/`. The [checklist](CHECKLIST.md)
records final results and the exact selectors. The deliberate absent-record
red test and failing GC characterization are separate from passing regressions;
the GC control is not a general collection guarantee.

No production/API/POM/coordinate or historical V1-V31 record changed.
No new native, supported dependency matrix, strict API comparison, fresh Central
release verification or performance/memory measurement was required for these
test/documentation edits. Those lanes remain conditional on selected production
work, not implicitly passed. F001-F004 remain unapproved; F005 is a test-evidence
finding. Priority 8.3 owns the next scope decision.
