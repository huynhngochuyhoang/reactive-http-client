# V33 Cross-Path Contract and Ownership Regressions

> **Recorded:** 2026-09-27
> **Source base:** `2abc5fdc` plus the reviewed Priority 6 test/documentation patch
> **Published / development:** `4.4.1` / `4.5.0-SNAPSHOT`
> **Release scope:** unselected

Companion to [Priority 6](CHECKLIST.md), the [approved findings](FIX-DECISION.md),
[builder ownership](BUILDER-OWNERSHIP.md), [static metadata](STATIC-METADATA.md)
and [AOT selection](AOT-PROPERTIES-SELECTION.md). No production source, public API,
configuration default or dependency changes in this priority. F001-F003 are all
approved; the conditional requirement to use workarounds for unselected IDs is
not applicable. Published `4.4.1` retains the previously documented workarounds.
F004/F005 remain delivered safeguards, not reopened engine work.

## Affected-Path Matrix

| Path | Agreement verified | Intentional boundary |
|---|---|---|
| Spring factory | Actual auto-configured starter builder needs no redundant SAFE entry; application Boot/per-client mutations remain classified. Fresh public metadata and the non-fallback, non-primary properties bean produce the selected endpoint and cache policy | Factory performs Spring component selection and assembly and owns its cache/transport resources; inspection does not stand in for construction |
| Public concrete handler | Supplied fresh metadata/configuration produce the same method, encoded target, finalized variant partition and cache outcome as factory calls | Caller supplies the WebClient filter chain, auth provider, base URL and selected config, and owns handler cleanup. This is not independent Spring selection or automatic customization inventory validation |
| Metadata / request plan | Fresh public fields derive GET and `/items/{id}` without writing an internal API value back; selected policy is `chosen`. Repeated cold subscriptions do not query replacement metadata again | Concrete plan reuse is tested, not one metadata lookup across construction/inspection, live metadata mutation or universal legacy-handler startup validation |
| Effective contract export | Method/path, method-sourced cache, TTL 60 seconds, maximum eight entries and normalized idempotency/identity/tenant variants agree with the consumed plan | Declarative path templates do not pretend to predict a runtime filter rewrite or expose cache keys |
| Diagnostics | Selected policy count, phase, TTL, capacity, method/source and endpoint count agree before construction; factory-backed occupancy becomes three after calls | Occupancy is unknown before factory creation. A separately created public handler does not become the registered factory's runtime state. Diagnostics remains non-instantiating, not the AOT creation path |
| Mock helper | Fresh public metadata selects a method cache policy, custom path and String result; repeat key hits while a changed path argument dispatches and stores separately | Mock routes/counts are synthetic exchange evidence. The helper receives config/metadata directly; it does not prove Spring builder ownership, non-primary selection, sockets or native behavior |
| JVM AOT processor | The same programmatic non-fallback config reaches cache validation with fresh metadata and starter-builder provenance, without creating the client factory, auth provider or client customizer | The composed fixture invokes the processor in a refreshed JVM context, not a generated/native executable. Separate selection/lifecycle and hint suites retain their narrower early-binding/prototype/factory controls |
| Foreign implementations | Existing diagnostics and AOT controls still exclude replacement factories from starter-only return/cache grammar | A foreign product's runtime semantics are its owner's responsibility; no eager product creation or validation is added |

Primary new witnesses are [V33CrossPathContractTest][composed] (two entry points)
and `freshStaticMetadataRetainsSelectedCacheAndPathIdentity` in the
[mock helper suite][mock]. Existing [public metadata tests][metadata],
[builder ownership tests][builder], [AOT selection tests][aot],
[AOT smoke tests][smoke], and [diagnostics tests][diagnostics] supply negative and
lower-level controls. The full Priority 5 lifecycle qualifications remain in force.

## Request and Caller Boundaries

The composed fixture uses the real starter builder definition, explicitly SAFE
`bootDefaults` and `clientMutation` beans, a programmatic non-primary selected
properties definition, and a replacement parser built only with public metadata
setters. It uses a loopback server that counts every request and returns the
observed method, raw target and synthetic header dimensions. It never exports
opaque keys, real identities, credentials or production request data.

Both paths construct a cold call with no dispatch, then observe:

1. A GET miss with an encoded path argument and the finalized first route/tenant.
2. An identical hit with no transport evidence, while default-request, upstream,
   auth and downstream gates execute again.
3. A second tenant missing independently despite the same declarative method/path.
4. A changed finalized route missing independently, followed by a hit on that route.
5. Default-request, auth and per-client gate rejection on an already warm identity;
   each has one error terminal and zero attempts/dispatches.

Five successful calls produce exactly three counted wire requests and caller
attempt counts `1, 0, 1, 1, 0`. Observer and lifecycle cache outcomes agree;
cache-served callers have no URL/status transport evidence. Gate failures add
three terminal events without adding requests. The selected header partition and
wire-derived response witnesses establish reuse/isolation without inspecting keys.
Changing request-time route state is not live mutation of a frozen policy.

The existing invocation, retry/redirect/auth-replay, semantic-read replay/timeout,
logical-budget and terminal-state suites were rerun. They cover outer retries
versus hidden replays, final request evidence, redirects, admission failures,
deadlines during auth/body/backoff and cancellation. No replay engine or terminal
semantics were changed to make the new composition pass.

## Ownership and Reachability

[ResourceOwnershipReviewTest][ownership] retains failed public construction and
late assembly failures beside a live owner, preserving the original failure and
checking actual registry lease owners and meter sets. Early-invalid-URL evidence
uses enabled caching/telemetry and registration counters, not only the factory's
unassigned cache field. Successful construction transfers cleanup responsibility
to the returned handler.

The added destroy/recreate test creates two factories sequentially beside one
live same-tag handler owner. Each factory miss/hit allocates one entry, adds one
registry owner and raises the shared maximum gauge from 16 to 32. Destroying it
returns ownership/capacity to the baseline; the next factory misses again instead
of inheriting the destroyed entry. Baseline meters and the existing cache remain
usable. The supplied application WebClient and registry remain usable; the
separate real-connector test confirms an application pool remains operational
after factory destruction. Private lease inspection is test evidence, not a new
public lifecycle API.

Normal ownership/retention/async tests run with `-XX:+DisableExplicitGC` and assert
deterministic state release. Collection claims come only from the separate
`v32-cache-reachability` profile: one fresh fork, Serial GC, explicit GC enabled,
64 MiB initial / 128 MiB maximum heap, with runtime assertions of those settings.
Its collection results apply to the specified transient/cached/flight/refresh/
diagnostics roots and ownership transitions, not arbitrary application graphs.
An independent non-single-flight load may remain caller-owned after manager close
until its caller terminates; no universal shutdown or collectability claim is made.
This work establishes neither an RSS improvement nor a production memory diagnosis,
universal concurrency correctness or a performance result.

## Verification and Reproduction

Oracle JDK 21.0.8, Maven 3.9.9, Boot 4.0.0 / Spring 7.0.1, Java target 21,
Central-only settings and the previously populated repository below. This is
reviewed-patch JVM evidence, not an immutable release commit. The helper's starter
dependency was rebuilt/installed before testing; it is not Central consumption
or an external artifact-only consumer check. No native executable was run.

```bash
export JAVA_HOME=/usr/lib/jvm/jdk-21.0.8-oracle-x64
export PATH="$JAVA_HOME/bin:$PATH"
export MAVEN_OPTS='-Xmx512m -XX:ActiveProcessorCount=2'
REPO=/tmp/v32-boot41-consumer.Z9m7kq/repository
MVN=(mvn -B -ntp -s .mvn/maven-central-settings.xml -Dmaven.repo.local="$REPO")

"${MVN[@]}" -pl reactive-http-client-starter -DargLine=-XX:+DisableExplicitGC \
  -Dtest=V33CrossPathContractTest,ResourceOwnershipReviewTest,CacheBuilderOwnershipContractTest,PublicStaticMetadataContractTest,EffectiveSelectionAotReviewTest,AotPropertiesSelectionContractTest,AotMetadataSelectionContractTest,PropertiesBindingLifecycleTest,ReactiveHttpClientAotSmokeTest,EffectiveHttpClientContractExporterTest,ReactiveHttpClientDiagnosticsProviderTest,ReactiveHttpClientFactoryBeanDiagnosticsTest,InvocationCompositionReviewTest,RetryRedirectAuthReplayCompositionContractTest,SemanticReadReplayTimeoutContractTest,LogicalCallTimeoutBudgetContractTest,ReactiveHttpClientTimeoutTerminalStateContractTest,CacheWorkOwnershipContractTest,ResponseCacheRetentionOwnershipTest,AsyncHandoffOwnershipContractTest,LocalResponseCacheObservabilityTest test
"${MVN[@]}" -pl reactive-http-client-test -am -DskipTests -Dmaven.javadoc.skip=true install
"${MVN[@]}" -pl reactive-http-client-test -DargLine=-XX:+DisableExplicitGC \
  -Dtest=MockReactiveHttpClientTest,MockCacheWorkParityTest,MockInboundContextParityTest,MockResponseCacheSupportTest test
"${MVN[@]}" -pl reactive-http-client-starter -Pv32-cache-reachability test
"${MVN[@]}" -pl reactive-http-client-starter -DargLine=-XX:+DisableExplicitGC test
"${MVN[@]}" -pl reactive-http-client-starter -DargLine=-XX:+DisableExplicitGC \
  -Dtest=DocumentationReleaseArtifactTest test
git diff --check
```

Evidence under `target/release-evidence/v33/priority6/` retains commands,
source/tree/patch records, logs/XML, exit statuses, reviewed source copies,
artifact hashes and an XML-derived audit sealed by `SHA256SUMS`. A clean checkout
without the ignored bundle must rerun the commands rather than claim access to it.
The first new-fixture run failed test compilation because it used a nonexistent
policy helper; correcting the test to the existing decision API required no
production change. That log is retained; no tests ran in that initial stage.
The first full starter run had two stale documentation assertions requiring
Priority 6 to remain open; they now guard the still-pending Priority 7 instead.
That failed run is retained separately from final verification.

| Final run | Actual result | Interpretation |
|---|---|---|
| Focused starter, 21 classes | 613 passed | Composed factory/public paths, metadata/selection/inspection, replay/deadline and deterministic ownership controls; explicit GC disabled |
| Helper, four classes | 77 passed | Selected fresh-metadata mock caching plus existing mock cache/work/context controls; explicit GC disabled |
| Controlled cache reachability | 16 passed | Separate Serial GC / explicit-GC-enabled / 128 MiB fork; not part of ordinary-suite collection requirements |
| Complete starter | 2,097 passed | Includes 74 documentation cases; explicit GC disabled |
| Final documentation rerun | 74 passed | Priority 6 closure, links, historical/current scope and pending Priority 7 gates |

All final runs have zero failures/errors/skips. Focused, full and documentation
counts overlap; the controlled lane also reuses deterministic fixture scenarios
with collection checks enabled. Five cases were added: two composed starter
paths, one destroy/recreate case, one documentation guard and one mock case.
`git diff --check` passes. The audit compares the helper test JVM's installed
starter JAR with the assembled module JAR and records the identical SHA-256;
this does not upgrade the helper run to external-consumer evidence.

## Remaining Gates

Priorities 7-8 still require external assembled/optional-absence consumers,
supported Boot rows, JVM AOT and clean-source native evidence, strict public API
compatibility and the targeted cost decision. The module/documentation runs here
do not close those gates. Release scope, signing and publication remain unselected.
Earlier V33 results and V1-V32 history are unchanged.

[composed]: ../../reactive-http-client-starter/src/test/java/io/github/huynhngochuyhoang/httpstarter/core/V33CrossPathContractTest.java
[mock]: ../../reactive-http-client-test/src/test/java/io/github/huynhngochuyhoang/httpstarter/test/MockReactiveHttpClientTest.java
[metadata]: ../../reactive-http-client-starter/src/test/java/io/github/huynhngochuyhoang/httpstarter/core/PublicStaticMetadataContractTest.java
[builder]: ../../reactive-http-client-starter/src/test/java/io/github/huynhngochuyhoang/httpstarter/core/CacheBuilderOwnershipContractTest.java
[aot]: ../../reactive-http-client-starter/src/test/java/io/github/huynhngochuyhoang/httpstarter/config/AotPropertiesSelectionContractTest.java
[smoke]: ../../reactive-http-client-starter/src/test/java/io/github/huynhngochuyhoang/httpstarter/config/ReactiveHttpClientAotSmokeTest.java
[diagnostics]: ../../reactive-http-client-starter/src/test/java/io/github/huynhngochuyhoang/httpstarter/core/ReactiveHttpClientDiagnosticsProviderTest.java
[ownership]: ../../reactive-http-client-starter/src/test/java/io/github/huynhngochuyhoang/httpstarter/core/ResourceOwnershipReviewTest.java
