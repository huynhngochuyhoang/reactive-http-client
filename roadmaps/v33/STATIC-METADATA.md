# V33 Public Static Metadata and Request Planning

> **Recorded:** 2026-09-23
> **Source base:** `f0c3e167fc73f1ba41a7da769cf275a93e1a99e2`
> **State:** reviewed working-tree patch on that reachable commit
> **Finding:** V32-F002 implemented; F003 pending
> **Published / development:** `4.4.1` / `4.5.0-SNAPSHOT`
> **Release scope:** unselected

Companion to [Priority 4](CHECKLIST.md) and the approved
[acceptance decision](FIX-DECISION.md#v32-f002). Priority 2 and V32 retain their
historical defect witnesses; they are not rewritten as passing fix evidence.

## Derivation and Precedence

[RequestPlan.from](../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/core/RequestPlan.java)
derives an `EffectiveApi` only when both the public metadata's API-ref name and
existing static derived value are absent. It uses the public HTTP method/path
and `TIMEOUT_NOT_SET`, not the method timeout as an API-map timeout. No derived
value is written back to the mutable metadata, and EffectiveApi remains internal.
No public constructor, accessor or setter signature changes.

| Input | Routing and timeout behavior |
|---|---|
| Fresh static public metadata | HTTP method/path supply the missing derived value; method timeout, then client timeout/legacy alias, retain existing precedence |
| Existing supplied derived value, including built-in parsing | Retain that exact value, not a replacement derived from edited fields |
| API-ref metadata | Configured method/path/API timeout remain authoritative even if a static value is also supplied; method timeout still wins, including zero |

Public fields and any existing derived value must describe the same endpoint.
Preserving supplied-value precedence is not support for inconsistent metadata
or mutation after plan consumption. Applications can continue to delegate the
built-in parser or use API-ref configuration. Published `4.4.1` still requires
one of those workarounds for this defect; the correction is development-only.

## Validation and Ownership

For fresh static metadata with a concrete client, planning requires a reflective
method owned by that interface or an inherited interface, a supported uppercase
HTTP verb, non-null path, matching Mono/Flux flags, and a supplied element type
for parameterized returns. Empty paths intentionally remain valid. The existing
URI-template and concrete return-type grammars then validate the plan, including
inherited generic resolution. There is no new parser or mutable decision model.

Incomplete/invalid fresh metadata now fails deliberately during concrete planning,
normally proxy construction/validation, with no dispatch. This is earlier than
the previous null-derived-value failure at invocation and is not a logical-call
terminal error. The regression covers missing/foreign methods, missing/unsupported
verbs, missing paths, authority/fragment/unbound/malformed templates, mismatched
flags, missing element types, nested publishers and non-reactive returns.

Lower-level entry points remain narrower: argument-only `RequestArgumentResolver`
use can operate on partial metadata without an endpoint. Legacy handlers without
a concrete interface derive routing at invocation; they do not acquire all
factory/AOT startup guarantees or concrete inherited-type resolution; fresh
metadata can still require planning on each legacy invocation. The public
create overload with a concrete client is separately exercised. No validation of
foreign client implementations is added.

Plans copy binding collections and resolve types per concrete client. Concrete-client
handler plan reuse is unchanged: repeated subscriptions perform no metadata lookup
or planning, and subsequent invocations reuse the handler's plan. A replacement
cache may still be queried at construction and invocation as before; this does
not promise one metadata lookup or one derivation for all entry points. No call
arguments or Reactor context are stored in metadata. Tests vary targets and
generic clients while asserting distinct client plans and stable per-client reuse.

## Verification

Oracle JDK 21.0.8, Maven 3.9.9, Boot 4.0.0 and Java target 21; test JVMs disable
explicit GC. Central-only settings and the previously populated repository
`/tmp/v32-boot41-consumer.Z9m7kq/repository` are used. This is rebuilt development
artifact consumption, not isolated published-artifact verification.

| Run | Actual result | Boundary |
|---|---|---|
| External desired-behavior test before production edits | One error: NullPointerException at invocation; zero failures/skips | Reproduces the missing static API using public setters outside starter packages |
| Final focused starter set | 381 passed, zero failures/errors/skips | Includes 25 new planning cases; metadata, return/parameter/URI grammar, timeout/API-ref, key, contract export, diagnostics, JVM AOT, composition and construction-cleanup controls |
| Rebuilt assembled external consumer | 18 passed, zero failures/errors/skips | Fresh static GET method/target/result, two subscriptions without additional metadata lookup, AOT processor validation, existing delegated/API-ref/customization/auth controls |
| Mock helper controls | 76 passed, zero failures/errors/skips | Fresh public metadata routes the mock through the custom target, plus existing cache/work/context controls |
| Documentation/archive/readiness guards | 72 passed, zero failures/errors/skips | Current fix scope, pending F003/release gates, historical links and published/development distinction |

The first local test compilation failed on an unsupported assertion helper and
boxed numeric literals; the next run had two incorrect quoted String response
expectations. Their logs and the latter XML are retained. After fixture fixes,
the initial 158-case set passed; the final 381-case set adds explicit timeout,
legacy-constructor and broader regression controls. No production workaround was
introduced to accommodate those fixture errors.

The external fixture is `example.v32.ExtensionScenariosTest`, using assembled
starter/helper/OTel JARs, not module output directories. The effective POM,
dependency tree, actual Surefire classpath and consumed JAR hashes are captured.
The reactor install is incremental; the external fixture is cleaned. AOT here
means JVM processor tests, not a native binary. Mock and public-handler tests
do not prove Spring factory inventory or native creation behavior.

## Reproduction and Evidence

Use the reviewed patch on the source base above. For the pre-fix witness, use
the base's starter with the renamed external test; its expected result is the
recorded error. The final commands are:

```bash
export JAVA_HOME=/usr/lib/jvm/jdk-21.0.8-oracle-x64
export PATH="$JAVA_HOME/bin:$PATH"
export MAVEN_OPTS='-Xmx512m -XX:ActiveProcessorCount=2'
REPO=/tmp/v32-boot41-consumer.Z9m7kq/repository

mvn -B -ntp -s .mvn/maven-central-settings.xml -Dmaven.repo.local="$REPO" \
  -pl reactive-http-client-starter -DargLine=-XX:+DisableExplicitGC \
  -Dtest=PublicStaticMetadataContractTest,MethodMetadataValidationTest,MethodMetadataTimeoutTest,DeclarativeReturnTypeGrammarTest,DeclarativeRequestParameterGrammarTest,DeclarativeUriTemplateStartupTest,ApiLevelTimeoutReadTimeoutPrecedenceTest,ReactiveClientInvocationHandlerTimeoutResolutionTest,ReactiveClientInvocationHandlerApiRefTest,EffectiveHttpClientContractExporterTest,HeaderParamMapSupportTest,ReactiveHttpClientAotSmokeTest,ResourceOwnershipReviewTest,CacheKeyContractTest,InvocationCompositionReviewTest,ReactiveHttpClientFactoryBeanDiagnosticsTest,ReactiveHttpClientDiagnosticsProviderTest test

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

Evidence under `target/release-evidence/v33/priority4/` includes commands,
source/tree/dirty-state records, logs, exit codes, red and final XML, source
copies and reviewed patch, artifact audit and SHA256SUMS. From that directory,
`sha256sum --quiet -c SHA256SUMS` verifies the bundle. The source is a reviewed
working-tree patch, not an immutable clean release commit.

## Cost, Rollback and Remaining Gates

Only missing-static plan construction adds derivation and validation. Existing
supplied-derived and API-ref plans avoid it; the new EffectiveApi is retained in
the existing plan, not an additional global cache. Repeated subscription and
concrete-handler plan-reuse assertions pass. Construction/inspection can still
build multiple plans; Priority 8 must assess that cost and measure it if needed.
Any per-subscription parsing, new global retention, internal type promotion,
setter incompatibility or parser redesign requires renewed scope review.

Rollback the local derivation/validation and its desired-behavior regressions
together if valid metadata or timeout/URI semantics regress. The published
delegation/API-ref workaround remains available. F003 and shared full-suite,
strict source/binary API, supported-Boot, clean-source native and targeted cost
gates remain in their own priorities. No signing, publication, benchmark result,
release version or release approval is selected here.
