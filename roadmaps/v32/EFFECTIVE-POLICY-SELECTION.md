# V32 Effective Policy and Component Selection Review

> **Status:** characterized, Priority 4
> **Reviewed:** 2026-09-15
> **Source baseline:** `6023a9132d2569108c55b2bf90bbceb7fd00084c`
> **Published / development:** `4.4.0` / `4.5.0-SNAPSHOT`
> **Implementation and release scope:** unselected

This review extends the [architecture map](ARCHITECTURE-MAP.md) and
[external-consumer scenarios](EXTENSION-SCENARIOS.md). It compares decisions and
evaluation times, not just similarly named methods. Production sources are
unchanged. A passing characterization of a current gap is not approval to retain
that behavior. [FINDINGS.md](FINDINGS.md) and Priority 8.3 own correction decisions.

## Entry-Point Matrix

| Path | Grammar and effective API | Cache | Resilience | Signing and component selection |
|---|---|---|---|---|
| Factory startup | [Factory.getObject][factory] resolves concrete client properties and replacement metadata, validates parameters, configured API refs/URI and return types before assembly. Annotation base URL wins. Metadata holds the static effective API; refs use the concrete client's config | Full selected eligibility/key grammar and customization validation before auth/transport. Manager creates bounded policy caches; work mapping is frozen | Only explicitly selected operators cause registry lookup. Actual applier availability gates method-instance and strict unsafe-retry validation in handler creation | Named AuthProvider wins; otherwise ordered eligible factories select the first supporting implementation. Strict built-in SigV4 validates supported body shapes and codec using the actual provider. No signer/provider is inferred from a configured string alone |
| Public handler creation | [create][handler] consumes caller-supplied WebClient, metadata, resolver, decoder, config and applier. It does not repeat the factory's complete startup grammar/transport/auth assembly; legacy constructors have less concrete-client information | `create` constructs manager/work selection; per-method cache validation also runs on first selected invocation. Missing authenticated cache-provider/base-URL inputs fail closed. Legacy construction cannot silently introduce work limits without a concrete client | `create` invokes effective resilience validation with the supplied applier. Mono/Flux override availability and retry capacity are distinct. Legacy constructors are not the `create` validation facade | No auth factory discovery or factory-level strict SigV4 startup validation here. Caller owns supplied components and validated assembly; runtime body/auth checks still apply. Do not advertise this as interchangeable with FactoryBean startup |
| Invocation | Metadata is read, concrete RequestPlan cached by Method, and effective API resolved at invocation. Direct plans use static derived metadata; API refs read and validate the current config. Arguments and outbound headers are resolved at their documented preparation stages | Work mapping rechecked at invocation/subscription; selected cache decision cached per method, retaining the policy reference. Selected body/context/key snapshots and final-request identity protect each lookup/load | EffectiveResiliencePolicy reads the method plan, HTTP eligibility and config with the already supplied/resolved applier. Selected active wrappers surround the attempt source; this is not fresh bean discovery each request | Filters authorize each cache caller, actual requests validate applied auth, successful publication rechecks identity. A hit is not permission to skip those gates. Full pipeline ownership remains Priority 5 |
| Diagnostics | [Provider][diagnostics] discovers starter and foreign FactoryBean registrations without creating their clients. [Exporter][exporter] uses concrete plans/API refs; starter-owned registrations get grammar checks, foreign ones skip starter-only grammar | Effective config is inspected; bounded live state comes only from an existing starter manager. Unknown provider facts remain null. Customization lookup uses the bean factory, unlike the F001 context path | Existing-registry lookup has absent/available/unresolved states. No lazy registry or uncached product is created; no Retry instance is created just to report strict capacity. Selected unavailable is not disabled; unresolved is not absent | Auth-factory view captured once per report. Existing eligible factories are source-aware ordered; unresolved relevant candidates keep strict signing unknown. Named-provider mode does not inherit object-auth strict signing |
| Mock builder | [Builder.build][mock] validates parameter/URI/return/cache grammar against supplied metadata and config; records in-process materialized exchanges, not Netty wire behavior | Production cache support/handler used with explicit controls and owned close. Cache grammar, variants, work limits and auth pre-lookup restrictions retained | Supplied applier or explicit mock Retry helper, then production strict validation. No application registry search. Noop subclasses' Mono-only override cannot activate Flux and an apply override alone does not prove duplicate capacity | Explicit provider/codec inputs; no application auth-factory inventory or factory-level transport/signing assembly. Synthetic provider selection is restored on failed build. This is not full Spring/native validation |
| AOT | [Processor][aot] locates starter factories, resolves replacement metadata, validates parameters/returns and static URI templates, then selected cache grammar. Its URI pass does not supply configured API refs; runtime remains responsible for complete configured ref/transport validation | Selected cache/key/work/customization rules and selected record hints. Custom primary properties honored, environment fallback supported. Competing initialized non-primary properties drift from runtime in F003 | Does not eagerly resolve registries or activate operators to generate hints; runtime validation of available instances/strict retry still required | Does not instantiate signers/token providers for strict body validation. Foreign client factories are excluded. JVM AOT tests verify this processor, not a native binary |

The lower-level public handler has a narrower assembly role, not a second Spring
factory. Its incomplete startup validation is a reviewed API-usage boundary; this
review does not grant invalid declarations a supported dispatch contract. Existing
factory/mock validation remains the normal entry point. Expanding that API or
centralizing every validator requires a separate demonstrated need and decision.

### Authoritative Rules and Evaluation Time

| Fact | Rule owner / evaluation | Consequence |
|---|---|---|
| Declarative parameter/return grammar | MethodMetadataCache entry points delegate to DeclarativeRequestParameterGrammar / DeclarativeReturnTypeGrammar with concrete RequestPlan generic resolution | Reuse the grammar at creation/export boundaries; do not impose it on foreign implementations merely because their interface is annotated |
| API-ref method/path and timeout | ApiRefValidationSupport plus Factory.validateApiRef/path checks; handler and exporter resolve the concrete API map. Static EffectiveApi comes from parsed metadata | Sharing a result type does not eliminate F002: fresh public static metadata lacks an accessible derived value. Configured API refs are the tested public alternative |
| Timeout precedence | Method timeout, API-ref timeout, explicit client request timeout, deprecated resilience timeout, disabled; logical-call budget is separate | Export describes effective values; subscription owns the elapsed deadline. AOT does not run transport timeout behavior |
| Cache selection and eligibility | [EffectiveCachePolicy][cache-policy]: disabled override, method policy, client policy; resolved verb, semantic-read intent, return/body/key grammar, limits and refresh rules | Selection alone is not cacheability or admission. Non-GET body partition and customizer safety are not waived for mocks/AOT |
| Work selection / live limits | [CacheWorkPolicy][work-policy] freezes method mapping and normalized dimensions at construction and compares later; [manager][manager] owns actual admission/occupancy and policy-bound mutation checks | A diagnostics config value is not an active reservation or evidence of a cache hit. Limits are not hot-reloadable |
| Resilience selection | [EffectiveResiliencePolicy][resilience-policy]: enable switch, explicit method-over-client name, Retry HTTP eligibility, publisher-shape availability | Disabled never queries availability. Absent selected operator is unavailable; uninspectable selected operator is unknown; available selected operator is active |
| Strict unsafe Retry | Factory validation with actual applier, effective eligibility and duplicate capacity; diagnostics queries existing instance capacity without creating missing instances | `maxAttempts=1` and ineligible methods must not fail. A lazy/prototype unknown is not proof the runtime policy is inactive |
| Strict body signing | Factory validates only selected built-in strict SigV4 and supported body/codec facts; diagnostics reports selection applicability, not execution of signing | Custom/named providers do not inherit built-in strict claims. A diagnostics true flag is not an end-to-end signature test |

## Lookup and Availability

### New Paired Observations

[ComponentSelectionReviewTest][selection-test] supplies a real client FactoryBean
and a synthetic WebClient exchange function. A 503 followed by 200 proves the
actual chosen Retry behavior; these are **in-process exchanges**, not TCP counts.
No reflection, private registry reader, sleeps or forced collection determine the
selection. The first snapshot also asserts the client factory remains uncreated.

`ComponentSelectionReviewTest#diagnosticsDoNotCreateRegistriesAndRuntimeUsesTheSelectedInstance`
has six cases with the same client-selected `review` Retry and strict flag:

| Registry state | Before runtime | Runtime observation | Later snapshot |
|---|---|---|---|
| Absent | unavailable / strict false; no creations | one exchange, RemoteServiceException | Absence is not inferred from a lazy candidate |
| Initialized singleton | review / strict true | two exchanges, success; one created registry | review / true |
| Lazy singleton | unknown / strict null, zero creations | runtime creates it, two exchanges, success | review / true, no further creation |
| Prototype | unknown / strict null, zero creations | runtime resolves one instance, two exchanges, success | still unknown / null, no second creation |
| Cached singleton FactoryBean product | review / strict true, product count one | cached product used, two exchanges, success | review / true, product count one |
| Uncached singleton FactoryBean product | unknown / strict null, product count zero | runtime creates product, two exchanges, success | review / true, product count one |

`ComponentSelectionReviewTest#initializedRegistryPrecedenceMatchesDiagnosticsAndActualRetry`
has four cases: primary, sole non-fallback, comparator priority, and sole default
candidate. The first registered registry permits one attempt; the preferred
registry permits two. Spring direct lookup returns the preferred instance,
diagnostics reports strict true, the actual factory succeeds after two exchanges,
and only the preferred registry reports a successful retried call. The priority
case uses the factory comparator's `getPriority`, not a fabricated ordering result.

[EffectiveSelectionAotReviewTest][aot-selection-test]
`EffectiveSelectionAotReviewTest#aotFirstSingletonFallbackDiffersFromRuntimeNonPrimarySelection`
has four competing-properties cases. Both beans are initialized; the first lacks
the selected cache policy, the preferred bean defines it. Direct lookup, ordinary
cache validation and real runtime FactoryBean creation accept the preferred config
in all four cases. AOT accepts primary, but its first-singleton fallback rejects
non-fallback/priority/default selection using the wrong bean: **V32-F003**. This
test calls the JVM processor; no native build or network dispatch is claimed.

### Reused Selection Coverage

The following existing fixtures are rerun, not replaced by mock answers to the
lookup helper. Exact suite totals and commands are in the [checklist](CHECKLIST.md).

| Candidate family | Existing evidence | Disposition |
|---|---|---|
| Primary/priority/default/sole non-autowire registry | [Diagnostics tests][diagnostic-tests]: `providerSnapshotsUseThePrimaryExistingRetryRegistry`, `providerSnapshotsHonorPriorityAndDefaultCandidates`, `providerSnapshotsUseASoleNonAutowireCandidateRegistry`; new paired actual factory cases above | Preserve direct-type lookup semantics. Do not reuse ordered-stream candidate exclusion for a sole direct candidate |
| Parent registry and deferred parent | Same suite: `providerSnapshotsResolveAnExistingRegistryFromTheParentFactory`, `providerSnapshotsKeepLazyParentRegistriesUnknown` | Parent delegation is supported; unknown parent state stays unknown |
| Uninspectable factory definitions/products | Same suite: cached/uncached products, raw and Object products, lazy/prototype, role SUPPORT/INFRASTRUCTURE, post-refresh non-lazy definitions and directly registered singleton factories | Non-instantiating lookup is conservative where type/selection cannot be proved. A unique primary or regular candidate can still win over irrelevant deferred candidates |
| Auth ordering, shadowing and eligibility | Same suite: `providerSnapshotsHonorFactoryMethodOrderMetadata`, PriorityOrdered/Ordered tests, custom/non-Order comparator tests, custom resolver/type-only descriptor tests, child name shadowing | [AuthProviderFactoryCandidates][auth-candidates] shares candidate/order semantics, not eager bean creation. Bean-method metadata and instance Ordered precedence both matter |
| Actual auth lookup and prototypes | [Factory diagnostics tests][factory-tests]: `authFactorySelectionIgnoresNonAutowireCandidates`, prototype/parent variants, `authFactorySelectionHonorsCustomAutowireCandidateResolvers`, `authFactorySelectionPreflightsIncompatibleParentsBeforeCreatingPrototypes` | Runtime candidate names filtered before materialization; incompatible-parent fallback must not create prototypes twice |
| Factory-method clients and foreign replacements | Diagnostics `factoryMethodStarterClientsUseReturnGrammarInDiagnostics`, `replacementFactoryClientsSkipStarterReturnGrammarInDiagnostics`; [AOT tests][aot-tests] factory-method hints, replacement metadata/properties, foreign exclusion | Preserve ownership recognition rather than applying starter grammar to all annotated types |
| Programmatic properties and environment | AOT `beanFactoryAotProcessorUsesPrimaryProgrammaticPropertiesBeforeEnvironmentBinding`, `beanFactoryAotProcessorBindsCachePolicyFromTheAotEnvironment`, new competing-bean test | Supported primary/environment paths retained; non-primary initialized selection needs F003 decision |

Runtime auth lookup may instantiate eligible factories and invoke `supports` and
`create`; diagnostics must not create unresolved products or signers. Diagnostics
can call `supports` on an existing factory, so application implementations still
own that method's behavior. A prototype factory can be usable at runtime while
remaining unknown to inspection. The non-instantiating registry product reader
also has a reflective Spring-cache access fallback: inability to inspect means
unknown, not permission to create the product. Native implications remain for
Priority 7; no universal bean resolver is proposed by this review.

## Mutation Inventory

| State | Current ownership / evaluation | Verified boundary or limitation |
|---|---|---|
| Client properties, API maps, headers/query defaults | Public mutable config retained; base URL, connector and chosen auth/applier assembled at factory creation, while API-ref/request/diagnostic values can be read later | No general coherent hot-reload mechanism is implemented or certified. Recreate the factory for configuration changes; this source inventory does not promise arbitrary setter mutation is safe |
| MethodMetadata / RequestPlan | Public parsing model remains mutable; built-in cache memoizes metadata and handler memoizes concrete plans | Replacement parsing is supported, but rewriting previously consumed metadata is not a verified reconfiguration API. F002 concerns fresh construction, not mutation of an already running plan |
| Cache policy reference and decision | Selection/decision records are shallow: policy config remains referenced, and first selected invocation caches its decision | Do not describe every policy field as immutable. Selected key inputs are separately frozen per subscription; isolation depends on the validated startup policy. Arbitrary variant/single-flight mutations are not certified by bound checks |
| TTL, entry/byte maximum, refresh time bounds | Manager records one PolicyBounds per name and rejects differing retained bounds on later access | [Retention test][retention-test] `runtimePolicyBoundsMutationIsRejectedWithoutCreatingAnotherCache` rerun alone; no forced-GC test is needed to establish rejection |
| Work limits and selected policy mappings | Immutable CacheWorkPolicy snapshot plus validator; reads current config against captured method plans | [Work-policy tests][work-tests] `mutationCannotResetCapacityOrChangeColdCallsAndLiveSnapshots` covers change/remove/add limits, policy removal, refresh selection at cold subscription, new invocation and live snapshot; `selectedInvalidWorkFailsStartupContractDiagnosticsAndAotWithoutCreatingInfrastructure` covers invalid/then-valid selection |
| Registry contents / factory availability | Runtime applier holds resolved registry/provider references; registry entries may be queried on later attempts. Diagnostics resolves an available-candidate view once per report | Missing client-selected instance may be created from defaults by runtime; active method annotation instance validation differs deliberately. Reporting must not create either. Hot mutation between validation and dispatch is not made atomic by a shared policy record |
| Observer/lifecycle providers | Handler resolves optional observers/hooks lazily for logical calls; cache telemetry selection is independent of MeterRegistry availability | [P3 E02](EXTENSION-SCENARIOS.md) establishes custom terminal outcomes with no registry bean. This does not test removal/replacement of observers during an active call |

Relevant strict/composition checks are reused from ExplicitResilienceActivationContractTest,
EffectiveResiliencePolicyTest, ResilienceOperatorApplierTest, factory diagnostics
and [mock tests][mock-tests]. They cover enabled-only dormancy, explicit
method/client precedence, Retry eligibility, publisher shape, distinct duplicate
capacity and strict unsafe-method failure. The mock's supplied applier is not a
Spring registry; an identical result must not require identical component lookup.

## Disposition

| Area | Result / smallest next action |
|---|---|
| Shared cache/resilience/grammar rules | Retain existing helpers; tested normal creation/export/mock/AOT paths agree where they claim the same validation. No new immutable policy hierarchy justified |
| Unknown versus unavailable | Preserve non-instantiating diagnostics and prototype unknowns; do not eagerly resolve components for cosmetic parity |
| Foreign clients | Keep starter-only validation/hints excluded; tested factory-method starter registrations remain included |
| Builder ownership lookup | V32-F001 remains unresolved: actual starter builder differs between ApplicationContext and bean-factory lookup. A local owner-aware definition lookup is smaller than weakening SAFE classification |
| Fresh static metadata | V32-F002 remains unresolved across planning consumers: inaccessible EffectiveApi prevents arbitrary fresh static metadata. Existing API-ref alternative remains; review a local derivation before any public SPI expansion |
| AOT properties candidate | F003 reproduced here. Use an explicit primary application properties bean as the current alternative. Compare a bounded properties lookup correction with documenting a stricter AOT constraint; no eager diagnostics resolver reuse |
| Mutable application state | Work and retained-bound reconfiguration intentionally rejected; other live mutable configuration is not a supported coherent refresh contract. Retain factory recreation guidance; do not claim full immutability or introduce hot reload |

All three findings are unresolved and unselected. Priority 8.3 must approve scope,
acceptance, compatibility and verification before production correction. Broader
arbitrary BeanFactory implementations, concurrent registry/config mutation,
third-party signers, optional-classpath/native behavior and all possible candidate
combinations are not exhausted by these representative fixtures. Priority 5 owns
subscription timing and Priority 7 owns remaining module/native evidence.

## Reproduce and Evidence

The [Priority 4 checklist](CHECKLIST.md) records the exact selected Maven suites
and totals. Source, stage commands, logs, XML, toolchain/settings, reachable
baseline archive, dirty patch, source copies and hashes are under
`target/release-evidence/v32/priority4/`. Preserve that ignored directory before
root clean. The first attempted parity run retains the three AOT mismatch errors
separately from its Boolean-unboxing/exception-type fixture mistakes; the corrected
characterization retains the production mismatch rather than hiding it.

The new tests are internal boundary tests with public Spring/factory entry points,
not newly assembled external consumers. Priority 3's committed external fixtures
and recorded installed-JAR evidence are reused with their original provenance.
The selected mock suite is a current reactor test, not fresh Central consumption.
No production, API/dependency coordinate, native binary, benchmark or prior
roadmap evidence is rewritten.

[factory]: ../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/core/ReactiveHttpClientFactoryBean.java
[handler]: ../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/core/ReactiveClientInvocationHandler.java
[diagnostics]: ../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/core/ReactiveHttpClientDiagnosticsProvider.java
[exporter]: ../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/core/EffectiveHttpClientContractExporter.java
[mock]: ../../reactive-http-client-test/src/main/java/io/github/huynhngochuyhoang/httpstarter/test/MockReactiveHttpClient.java
[aot]: ../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/config/ReactiveHttpClientBeanFactoryInitializationAotProcessor.java
[cache-policy]: ../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/core/EffectiveCachePolicy.java
[resilience-policy]: ../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/core/EffectiveResiliencePolicy.java
[work-policy]: ../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/core/CacheWorkPolicy.java
[manager]: ../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/core/LocalResponseCacheManager.java
[auth-candidates]: ../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/core/AuthProviderFactoryCandidates.java
[selection-test]: ../../reactive-http-client-starter/src/test/java/io/github/huynhngochuyhoang/httpstarter/core/ComponentSelectionReviewTest.java
[aot-selection-test]: ../../reactive-http-client-starter/src/test/java/io/github/huynhngochuyhoang/httpstarter/config/EffectiveSelectionAotReviewTest.java
[diagnostic-tests]: ../../reactive-http-client-starter/src/test/java/io/github/huynhngochuyhoang/httpstarter/core/ReactiveHttpClientDiagnosticsProviderTest.java
[factory-tests]: ../../reactive-http-client-starter/src/test/java/io/github/huynhngochuyhoang/httpstarter/core/ReactiveHttpClientFactoryBeanDiagnosticsTest.java
[aot-tests]: ../../reactive-http-client-starter/src/test/java/io/github/huynhngochuyhoang/httpstarter/config/ReactiveHttpClientAotSmokeTest.java
[retention-test]: ../../reactive-http-client-starter/src/test/java/io/github/huynhngochuyhoang/httpstarter/core/ResponseCacheRetentionOwnershipTest.java
[work-tests]: ../../reactive-http-client-starter/src/test/java/io/github/huynhngochuyhoang/httpstarter/core/CacheWorkPolicyEnforcementTest.java
[mock-tests]: ../../reactive-http-client-test/src/test/java/io/github/huynhngochuyhoang/httpstarter/test/MockReactiveHttpClientTest.java
