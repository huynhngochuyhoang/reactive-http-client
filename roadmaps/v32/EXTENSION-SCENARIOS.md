# V32 Real Application Extension Scenarios

> **Status:** characterized, Priority 3
> **Reviewed:** 2026-09-14
> **Source baseline:** `c017b234a3770e41c6cc54d16440de611941f347`
> **Published / development:** `4.4.0` / `4.5.0-SNAPSHOT`
> **Implementation and release scope:** unselected

Extends the [architecture map](ARCHITECTURE-MAP.md) and
[reported/exploratory inventory](BASELINE-SCOPE.md#reported-and-exploratory-cases).
The [assembled consumer fixture][consumer] is in `example.v32`, outside all starter
packages. It uses installed artifacts and public annotations, properties, SPIs,
Spring configuration, WebFlux capture and Reactor APIs. The `Method` parameter is
the metadata SPI's input, not reflective access to hidden implementation state.
No access override, private-field lookup, invocation-handler construction or
same-package cache bridge makes a scenario work.

All request values, paths and auth data are synthetic. The header case comes from
the reported exact-case lookup failure; this is not a mesh diagnosis. Other cases
are exploratory extensions requested by the architecture review, not newly
reported production incidents. Application-owned connector cleanup is a bounded
ownership observation, not evidence that a pod-memory report is or is not a leak.

## Scenario Matrix

All named methods below belong to [ExtensionScenariosTest][consumer]. Passing a
characterization assertion that expects a defect does not make the defect correct.

| ID | Origin / application need | Observed classification and exact restriction | Existing alternative / next action |
|---|---|---|---|
| V32-E01 | Reported allowlisted header visible inbound but exact-case map lookup missing; explicit worker handoff | Directly supported named reader and snapshot. Bulk lookup stays case-sensitive; a detached subscription without the snapshot fails its required auth gate | Use `inboundHeader` and `RequestContextSnapshot.writeTo`; do not infer mesh rewriting or implicit queue propagation |
| V32-E02 | Exploratory per-caller tenant authorization and custom terminal callbacks on warm cache hits | Supported with constraints: all inspected Boot/defaultRequest/client gates must precede lookup, all relevant variants selected; no MeterRegistry bean is required for custom outcomes | Keep gates in the pre-lookup filter/defaultRequest boundary. Classify every applicable customization, do not put required gates only in the exchange function |
| V32-E03 | Exploratory tenant header injection and per-client URI rewrite | Supported with constraints: final target and declared selected headers partition lookup and publication; changing tenant or rewritten target causes a distinct load | Declare header names in the request/default inventory and policy; the fixture overwrites sentinel defaults before auth/dispatch and rejects missing inbound tenant |
| V32-E04 | Exploratory 401 invalidation changes the auth identity | Supported with constraints: refreshed response is returned to its caller but not stored under the earlier identity | Current final-request identity revalidation; do not weaken auth/key isolation for cache fill rate |
| V32-E05 | Exploratory ordered auth factory and named-provider precedence | Directly supported: first supporting ordered factory creates the provider, or the named provider wins without a factory create | Existing `AuthProviderFactory`, bean-method `@Order`, `auth-provider`; no factory-selection SPI needed by this case |
| V32-E06 | Exploratory fresh metadata parser plus domain error decoding | Static fresh metadata contradicts the supported construction surface: validated proxy throws before returning a Mono, [V32-F002](FINDINGS.md#v32-f002-fresh-static-metadata-requires-an-inaccessible-derived-value). API-ref metadata and a public decoder subclass work | Tested API-ref configuration alternative; delegate unchanged built-in parsing where sufficient. Do not use reflection/package relocation to construct internal `EffectiveApi` |
| V32-E07 | Exploratory custom selected-JSON serialization | Supported with constraints: bounded codec bytes reach both auth inspection and wire and distinguish cached bodies; a codec with only `write` is rejected before auth/dispatch | Implement `writeBounded` using a truly capped encoder. Fixture delegates to the public Jackson 3 bounded adapter, not an aggregate-then-size-check |
| V32-E08 | Exploratory unknown Boot/per-client/replacement-builder behavior | Intentionally rejected until explicitly classified; no request is dispatched | Inventory and prove the complete mutation, then classify SAFE, or disable caching. This rejection is not an extension defect |
| V32-E09 | Exploratory replacement prototype WebClient builder | Supported with constraints: application replacement explicitly invokes ordered Boot customizers; their execution is not automatic on an arbitrary replacement | Retain the starter builder when replacement is unnecessary; otherwise own its Boot customization and cache-safety inventory |
| V32-E10 | Exploratory terminal exchange-function replacement | Supported with constraints: called only for loads, while client filters still reject warm hits | Keep mandatory per-caller decisions outside the terminal exchange function, or leave that method unselected from caching |
| V32-E11 | Exploratory application connector and resource lifecycle | Directly supported application ownership: own pool works after starter context close and is disposed by the application | Own connector-native timeout/proxy/TLS/pool settings and lifecycle; not a transfer of those resources to factory destruction |
| V32-E12 | Exploratory minimal starter auto-configuration with its own prototype builder | Contradictory classification across bean-factory/context entry points, [V32-F001](FINDINGS.md#v32-f001-starter-builder-is-misclassified-through-applicationcontext) | Explicitly classify the inspected starter builder as well as all application mutations; record the gap for Priority 4/8, not a validator bypass |

## Configuration and Observations

### Common Fixture

The [opt-in Maven profile][pom] `v32-extension-scenarios` adds its own Caffeine
dependency and `src/v32-test/java`. The consumer is not a reactor module. Boot
4.0.0, Java 21, the current starter/helper/OTel JARs and normal test libraries are
resolved as Maven artifacts; no starter output directory is added to its classpath.

An `ApplicationContextRunner` bootstraps the real starter auto-configuration and
registrar with a primary programmatic properties bean. It intentionally supplies
no MeterRegistry bean. A real IPv4 loopback HTTP server records completed request
bodies and final headers/targets. E10 replaces the terminal exchange function and
asserts **zero network requests**, so its two exchange calls are not called TCP
dispatches. E01 uses Spring's mock inbound exchange with the production WebFilter
and a real downstream client; it does not claim an ingress wire/protocol test.

Two method-selected policies have TTL 60,000 ms and maximum size 16. Neither
acknowledges shared responses; both vary on `Idempotency-Key`, `X-Tenant` and
`X-Identity`. Search additionally selects the `@Body @CacheKey("body")` label
and carries method-specific semantic-read intent. Tenant/identity defaults declare
the variant inventory, not permission to dispatch unresolved values. Auth verifies
the captured tenant and upstream Boot header, and emits the selected identity.

The deliberately inspected starter builder receives an additional SAFE entry
because of F001. Boot defaults, client mutations and each selected replacement are
classified separately. Negative cases remove the relevant classification rather
than overriding the validator. Every test owns its server; the test state is not
closed by the nested application context. The connector case separately owns and
closes its ConnectionProvider.

### Exact Observations

| Case | Executable observation |
|---|---|
| E01 | `ExtensionScenariosTest#capturedHeadersCrossAnExplicitWorkerHandoffWithoutLeakingThePreviousCaller`: two lower-case captured tenant values restore into a worker target containing a different tenant, preserving an unrelated target key; the same cold call dispatches once per restored tenant. Missing handoff rejects, subsequent empty context stays empty, and captured redacted Authorization is not implicitly forwarded |
| E02 | `ExtensionScenariosTest#warmHitsRunBootDefaultsAuthAndClientGatesWithoutAMeterRegistry`: miss order is default/Boot/auth/client then default/Boot/client for dispatch; hit order is default/Boot/auth/client with no new wire request. Observer and lifecycle each record exactly miss and fresh hit. Separate auth, client-filter and defaultRequest revocations each reject with zero attempts/URL and no additional dispatch |
| E03 | `ExtensionScenariosTest#finalizedTenantAndRewrittenTargetsPartitionHits`: two tenants and a changed rewrite target produce exactly three network requests; repeated matching calls hit. The peer verifies both selected tenant values and the Boot default header |
| E04 | `ExtensionScenariosTest#refreshedAuthCannotPublishItsResponseUnderTheEarlierIdentity`: first call dispatches old identity, 401, refreshed identity; reselecting the old identity requires another load, and later selecting the refreshed identity requires its own fill. Five callers produce four wire requests and outcomes miss/miss/hit/miss/hit |
| E05 | `ExtensionScenariosTest#orderedAuthFactoryAndNamedProviderPrecedenceAreObservable`: two parameter cases prove first-factory create counts 1/0, second-factory count always 0, and authenticated wire identity in either mode |
| E06 | `ExtensionScenariosTest#freshStaticMetadataCannotSupplyTheInternalEffectiveApiThroughPublicConstruction`: fresh static metadata passes client construction but method invocation throws NPE before a Mono, observer/lifecycle record or dispatch. `ExtensionScenariosTest#customMetadataAndDecoderWorkThroughPublicReplacementBeans`: API-ref alternative reaches the mapped path; decoder drains a 503 and supplies the same domain error to caller, observer and lifecycle; a later ordinary request succeeds |
| E07 | `ExtensionScenariosTest#boundedCustomCodecExposesAndSendsTheSameBodyAndKeysItsRepresentation`: three calls serialize bounded application-specific JSON three times for auth/key preparation but dispatch only two distinct bodies; captured auth-visible bytes equal each selected body's wire bytes. `ExtensionScenariosTest#aCodecWithoutBoundedSerializationIsAnExplicitConstraint`: default bounded-method rejection, no auth body or network request |
| E08 | `ExtensionScenariosTest#unclassifiedBuilderBehaviorIsRejectedInsteadOfSilentlyTrusted`: three parameter cases name Boot customizer, client customizer or replacement builder in the rejection, with no dispatch |
| E09 | `ExtensionScenariosTest#aClassifiedReplacementBuilderRetainsExplicitBootCustomization`: miss then hit, one network request carrying both replacement-builder and Boot-default headers |
| E10 | `ExtensionScenariosTest#classifiedExchangeFunctionIsLoadOnlyWhileFiltersStillGateHits`: two tenant-specific exchange calls, a same-tenant hit, then client-filter denial without another exchange; network request count remains zero |
| E11 | `ExtensionScenariosTest#applicationOwnedConnectorRemainsUsableAfterTheStarterContextCloses`: starter request succeeds, context closes, own provider remains undisposed, another application WebClient request succeeds, then explicit owner disposal completes |
| E12 | `ExtensionScenariosTest#starterManagedBuilderClassificationDiffersBetweenFactoryAndContextLookup`: same configuration and metadata validate through the public bean-factory entry point but proxy creation through ApplicationContext rejects the starter builder; no network dispatch |

Auth-body observation is not a cryptographic signature verification. The fixture
does not inspect raw cache keys or reach inside cache maps, invocation handlers,
reporting state, factory-owned resources or private bean caches.

## Limits and Alternatives

F001 and F002 are **unresolved findings**, not accepted fixes. Their assertion
methods intentionally describe present failure behavior. An accepted correction
must update those assertions to the intended public behavior, not preserve the
defects to keep characterization green. [FINDINGS.md](FINDINGS.md) records owners,
minimal alternatives, acceptance and the Priority 8.3 decision gate.

Supported constraints are not findings merely because configuration is explicit:
bounded serialization, customization classification, explicit handoff and connector
ownership all have working public paths here. Building a required authorization
gate only inside a terminal exchange function is intentionally outside the
cache-aware contract; disabling cache selection is an existing alternative.

The static-metadata alternative is awkward but public: selecting an API-ref and
declaring its method/path duplicates some information the parser already has. It
does not prove arbitrary fresh static metadata supported. No consumer case was made
successful through unsupported internal access. Lazy/primary/fallback/hierarchy
permutations, custom connector transport settings, native/AOT for these new
consumers, concurrent refresh/retry lock ownership and arbitrary third-party
customizers remain for Priorities 4-7/10. Existing internal tests and prior native
binaries are not relabeled as external evidence for those unrun combinations.

For the reported memory hypothesis, this review adds only E11's lifecycle
observation. It supplies no new heap, RSS, native-memory or retained-root capture.
For inbound headers, see the [V31 characterization](../v31/INBOUND-HEADER-CHARACTERIZATION.md)
and the [existing assembled handoff test][v31-consumer]. Reuse of that source is
explicit; the new case does not assert Istio deployment behavior.

## Reproduce and Evidence

From the repository root, install current artifacts, then run the standalone
profile without any earlier parity profile:

```bash
mvn -B -ntp -s .mvn/maven-central-settings.xml -DskipTests -Dmaven.javadoc.skip=true install
mvn -B -ntp -s .mvn/maven-central-settings.xml -f .github/boot4-consumer/pom.xml \
  -Dconsumer.v32.extensions=true -Dtest=ExtensionScenariosTest clean test
```

The [current-consumer verifier][verifier] activates this profile for tests,
effective POM, dependency tree and classpath. The published-consumer lane remains
unchanged: current characterization is not a new Central compatibility run.

Actual stage commands, XML totals, source copies/diffs, reachable baseline source,
installed artifact/classpath hashes, toolchain and failures are retained under
`target/release-evidence/v32/priority3/`; the final totals are in the
[Priority 3 checklist](CHECKLIST.md). This review uses freshly installed current
project artifacts in the existing local Maven repository, **not a fresh dependency
repository or fresh Central downloads**. Classpath/artifact verification checks the
JARs against those installed from this source and rejects reactor classes-directory
leakage. Preserve the ignored evidence bundle before root clean.

The first attempted run stopped positive scenarios at F001; the second exposed
F002 and two fixture path assertions. Those runs remain separate from successful
cases. The source/test/record patch is uncommitted evidence against the reachable
baseline, not a claimed new clean release revision. No production code, dependency
version, published coordinate or historical roadmap is changed.

[consumer]: ../../.github/boot4-consumer/src/v32-test/java/example/v32/ExtensionScenariosTest.java
[pom]: ../../.github/boot4-consumer/pom.xml
[verifier]: ../../scripts/verify-current-consumer.sh
[v31-consumer]: ../../.github/boot4-consumer/src/v31-test/java/io/github/huynhngochuyhoang/httpstarter/v31consumer/Boot4InboundContextConsumerTest.java
