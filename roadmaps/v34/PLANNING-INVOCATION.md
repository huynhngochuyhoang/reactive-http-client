# V34 Planning and Ordinary Invocation Controls

> **Status:** Priority 5 complete, 2026-10-01; production work N/A
> **Delivered production IDs:** none
> **Deferred planning candidate:** V34-C003
> **Release scope:** unselected

This executes Priority 5 of the [checklist](CHECKLIST.md) under the
[C004-only decision](IMPROVEMENT-DECISION.md). Starting clean commit:
`a76421f22cd4827559d8b0092d58e93bfa9fb16d`. Verification includes the test/document
patch recorded in the evidence bundle, not a claim that those additions were
already in that commit. C004 implementation remains pending in Priority 6.

## Scope and Ownership

No planning or request-materialization finding was selected. Priority 5.1's
production correction and selected-ID cost experiment are **N/A**, not delivered
optimizations. C003 remains deferred to the planning/resolver owner and V34
maintainer: reopen only with an isolated static-work witness, exact wire parity,
matched benefit and renewed approval. Reuse supported client proxies; do not cache
resolved requests as an application workaround.

The existing boundary is retained:

- `MethodMetadataCache` owns shared static metadata; `RequestPlan.from` derives
  missing static routing without mutating a caller-supplied metadata object.
  Handler-local plans retain concrete method/generic identity and immutable
  bindings, not arguments, bodies, authorization or Reactor context.
- `RequestArgumentResolver.resolve` projects arguments at ordinary invocation.
  Resolved arguments/body belong to the returned publisher. Reusing an ordinary
  publisher is not a new deep-freeze or replay guarantee for mutable/resource inputs.
- Subscription materialization still applies caller context, generated/context
  idempotency and request-body handling. Dynamic consumers and reporting state
  retain their existing invocation/subscription lifetimes.
- `CacheWorkPolicy.validator` still scans every frozen abstract method and reads
  live selection configuration on every invocation. This priority changes neither
  that validation nor the disabled values C004 will address.

Source review covers [RequestPlan][plan], [RequestArgumentResolver][resolver],
[ReactiveClientInvocationHandler][handler] and the unchanged policy validator.
No new model, resolved-request cache, normalization, body pre-serialization,
retention mechanism, default or API was introduced.

## Retained Controls

All listed classes were freshly run. Tests using a captured `ClientRequest` prove
WebClient request materialization, not socket/pool behavior; the two V33 cross-path
cases additionally inspect real loopback requests through public-handler and
Spring-factory creation. No mock-only result is presented as transport evidence.

| Boundary | Witness and assertions | Cases |
|---|---|---:|
| Annotation parsing and concrete planning | `MethodMetadataValidationTest`: invalid annotations, immutable bindings, inherited/overloaded methods, concrete response/body generics and structural generic arrays | 23 |
| Complete fresh public metadata | `PublicStaticMetadataContractTest`: missing-only derivation, supplied derived value, API-ref precedence, 13 malformed forms rejected before dispatch, client-local Mono/Flux plans, repeated subscriptions without reparsing, public/legacy entry points | 25 |
| Ordinary materialization | `ReactiveClientInvocationHandlerBehaviorTest`: default/dynamic ordered headers/query, escaping, template/API-ref query, body/auth bytes, final charset, inherited decode, default methods and no unnecessary auth serialization | 34 |
| API-ref resolution | `ReactiveClientInvocationHandlerApiRefTest`: configured verb/path/timeout, missing entry, annotation fallback and inherited client-local configuration/name precedence | 5 |
| URI and startup grammar | `DeclarativeRequestUriTest` (4) plus `DeclarativeUriTemplateStartupTest` (3): exact ordered/escaped URI, empty paths, sanitized invalid template/authority/fragment errors and inherited/configured startup validation | 7 |
| Header projection | `HeaderParamMapSupportTest`: ordered scalar/collection/map values, invalid names/controls and case-insensitive collision rejection | 8 |
| Per-subscription idempotency | `IdempotencyKeySupportTest`: parameter/default/header/context precedence, generated keys per subscription, retry stability and gated overlapping subscriptions with matching terminal headers | 13 |
| Caller isolation | `SubscriptionLocalReportingStateTest`: gated overlapping Mono/Flux/envelope results, caller-specific headers/status and empty/cancellation terminal boundaries | 5 |
| Selection and mutation | `DeclarativeCachePolicyTest` (24), `DefaultPathCostOwnershipTest` (14), `CacheWorkPolicyEnforcementTest` (10): selection/exclusion, whole-interface scan, fresh invocations versus subscriptions, dynamic consumers, invalid mutation and cold/live validation | 48 |
| Composed entry paths | `V33CrossPathContractTest`: public-handler/factory routes, fresh metadata, repeated cold calls, tenant/header/URI changes and pre-lookup gate rejection with exact dispatch/results | 2 |
| **Focused total** | Zero failures, errors or skips; explicit GC disabled | **170** |

Two new parameterized cases in the [ordinary behavior suite][behavior-test] freeze
the null-versus-empty control without changing production: no dispatch before
subscription; two subscriptions each send POST; null query/header omitted versus
`?q=` and a present empty header; null body has no implicit Content-Type versus
empty body with `application/json`. The body inserter is executed against a
`MockClientHttpRequest` and both payloads are empty, while their presence/header
semantics stay distinct. An existing charset case now also checks the materialized
`text/plain;charset=ISO-8859-1` request and decoded wire text, beyond its signing hash.

These tests do not certify unsupported concurrent mutation or replay of one-shot
bodies. Advanced body/resource, native and assembled parity remain their later
priorities; this is not completion of Priorities 6-10.

## Cost Disposition

No new JMH/scored or exploratory timings were collected: there is no selected
planning optimization to measure or roll back. Reuse only the unchanged-source
[Priority 3 characterization](COST-OWNERSHIP.md), with its limitations. The measured
commit `ec225b8ed93ab0d1bd461d4eda7a38f23a2579e1` is reachable from this source;
production, benchmark fixture, packaging and measurement-input comparisons pass.
The P3 500-file inventory and P4 62-file decision inventory rehash unchanged.

Relevant historical rows are the six `helpers.json` rows (cold parsing/concrete
planning, cached metadata/plan lookups and both argument-resolution forms), plus
GET/TARGET warm-publisher rows in `v34.json`. Full initial/reversed evidence is
retained, not rescored or counted as a new run. Required dynamic projection and
validation costs remain. TARGET-minus-GET is not isolated removable static cost.
The **enabled-only allocation flag remains unresolved**; this N/A disposition
does not clear it or demonstrate C004 benefit. Priority 10 still owns final matched
verification after the selected implementation. Delivered production IDs: none;
rollback is N/A because no production optimization was attempted.

## Verification and Reproduction

Evidence: `target/release-evidence/v34/priority5/`. It retains commands, exits,
toolchain, source/patch, exact-class XML reports, input comparisons and historical
inventory validation. The final inventory digest is in the checklist; the
checklist is the external integrity index, not part of that seal.

Oracle Java 21.0.8, Maven 3.9.9, Boot 4.0.0; `MAVEN_OPTS` and Surefire flags below.
The final focused run passes **170 tests in 13 classes**. The documentation,
archive and readiness suite passes **84 tests**, including the new P5 scope/link
guard. The Python V34 input/result guards pass **14 tests**. Total Java cases:
**254**, zero failures/errors/skips. An earlier overlapping 170-case run is retained,
not added to the distinct-case total; the final run includes the charset assertion.
Generated readiness stays V34 active, release lane/scope unselected and
`plannedFinalVersion=null`. No whole-reactor, new API/native or performance result
is claimed. V1-V33, historical P1-P4 records and production remain unchanged.

```bash
export JAVA_HOME=/usr/lib/jvm/jdk-21.0.8-oracle-x64
export PATH="$JAVA_HOME/bin:$PATH"
export MAVEN_OPTS='-Xmx512m -XX:ActiveProcessorCount=2'
mvn -B -ntp -s .mvn/maven-central-settings.xml -pl reactive-http-client-starter \
  -Dtest=PublicStaticMetadataContractTest,MethodMetadataValidationTest,ReactiveClientInvocationHandlerBehaviorTest,ReactiveClientInvocationHandlerApiRefTest,DeclarativeRequestUriTest,DeclarativeUriTemplateStartupTest,HeaderParamMapSupportTest,IdempotencyKeySupportTest,SubscriptionLocalReportingStateTest,DeclarativeCachePolicyTest,DefaultPathCostOwnershipTest,CacheWorkPolicyEnforcementTest,V33CrossPathContractTest \
  -DargLine='-XX:+DisableExplicitGC -XX:ActiveProcessorCount=2' test
mvn -B -ntp -s .mvn/maven-central-settings.xml -pl reactive-http-client-starter \
  -Dtest=DocumentationReleaseArtifactTest \
  -DargLine='-XX:+DisableExplicitGC -XX:ActiveProcessorCount=2' test
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s scripts -p 'test_*v34*.py' -v
git diff --check
```

The recorded Maven commands additionally select the existing isolated local
repository `target/v33-native-runs/native-g0ynw95x/repository`. This does not reuse
the published-baseline repository as a current reactor build. All new source
changes are tests and the P5 execution record; the next step is Priority 6's
approved C004-only work, not reopening C003 or choosing a release.

[plan]: ../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/core/RequestPlan.java
[resolver]: ../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/core/RequestArgumentResolver.java
[handler]: ../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/core/ReactiveClientInvocationHandler.java
[behavior-test]: ../../reactive-http-client-starter/src/test/java/io/github/huynhngochuyhoang/httpstarter/core/ReactiveClientInvocationHandlerBehaviorTest.java
