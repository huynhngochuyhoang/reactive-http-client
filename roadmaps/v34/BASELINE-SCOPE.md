# V34 Baseline and Effective Profile Scope

> **Recorded:** 2026-09-30
> **Published baseline:** `4.4.2`
> **Development coordinate:** `4.5.0-SNAPSHOT`
> **Implementation and release scope:** unselected

Baseline record for [Priority 1](CHECKLIST.md). This inventory is not a benchmark,
a regression diagnosis or approval to optimize C001-C005. **Priority 4.3** still
requires a maintainer decision before production edits. This priority makes
no production changes; V1-V33 evidence and all defaults remain unchanged.

## Source and Version Boundary

Reviewed clean source: `d246e70798e43d60a66d4e213ca08d13ba34ddf7`.
Published tag: `v4.4.2`, reachable ancestor
`bdacfc439b7fab7df1b319d057782c3b69ce9676`.
The provenance audit began before tracked edits. Fresh tests use the reviewed
source plus this baseline/checklist and test-only patch; they are not a new
clean release build. Evidence: `target/release-evidence/v34/priority1/`.

Root/module/benchmark-parent and current consumer/native starter coordinates
remain `4.5.0-SNAPSHOT`; public installation, strict API, published-consumer and
benchmark baselines remain `4.4.2`. Consumer fixture project versions are separate
from the starter dependency under test. The supported-matrix guard still expects
this development coordinate and published baseline; no guard or POM is changed.

The roadmap, checklist and index report active V34. Readiness is
`activeRoadmap=v34`, `releaseLane=unselected`, `plannedFinalVersion=null`.
The lifecycle regression covers snapshot, final-candidate and post-publication
version states while the checklist remains active. A generated deferred `4.5.0`
label is not approval to release that version. V33's historical inactive-roadmap
checkpoint remains an accurate closure record, not the current adoption state.

Production main sources in all three modules and the consumer/native fixture
sources are byte-unchanged from the release tag. Central settings and published
artifact/consumer/provenance scripts are unchanged. The tracked V33 parity runner
differs only by its expected starter coordinate, `4.4.2` to `4.5.0-SNAPSHOT`;
do not describe that runner as byte-identical to the release version.

## Revalidated Evidence

These are explicitly reused V33 results, **not fresh Central downloads**, new
assembled-consumer executions, new strict project comparisons or new native/JMH
runs. The [V33 publication record](../v33/RELEASE-DECISION.md#post-publication-closure)
and [parity record](../v33/PARITY-EVIDENCE.md) retain their original outcomes and
limits. The local `reuse-confirmed/audit.json` records the rechecks.

| Evidence | Revalidation and limit |
|---|---|
| Published parent and modules | Rehashed all 13 POM/binary/source/Javadoc artifacts in the original isolated `release-artifacts-4.4.2` repository and the sealed copies. Central remote markers and binary Maven versions match. All four POMs and 146 packaged Java sources (132 starter, nine helper, five OTel) match the reachable tag byte-for-byte. |
| Published assembled consumer | Rehashed the separate `consumer-4.4.2` inventory and successful provenance (`completedStage=evidence-verified`, exit 0, clean release-tag fixture). The dependency classpath resolves three published JARs with matching artifact hashes and no reactor output directories. Recounted four ordinary cases and 29 all-profile cases, each with zero failures/errors/skips. They overlap and are not 33 distinct cases. |
| Published signatures and workflow | The sealed publication bundle includes public release/tag/workflow data, the successful signature checks and their earlier failed attempts. Rehashing preserves that evidence; no new GPG verification, signing, deployment or online publication check was performed. |
| Upper Boot row | Rehashed the preparation bundle and recounted its genuine Boot 4.1 consumer's 29 cases and minimal consumer's one case. Its effective POM, dependency tree, classpath and artifact hashes remain available. This is reused candidate-artifact evidence, not a new Central Boot 4.1 consumer run. |
| Strict API and packaging | Independent root/starter comparisons against Central `4.4.2` and development packaging are preserved in the publication bundle and apply to the unchanged production inputs. Fresh fixture guards below exercise the gate machinery, not a new project API comparison. |
| Native and cost | Earlier V33 native and JMH results remain historical references only. No old binary or score is certified as V34 performance evidence; later selected changes determine reruns. |

The original ordinary consumer retains its own effective POMs, dependency tree
and classpath. The all-profile follow-up retains its command/log/XML; do not
claim a separately generated dependency report for that follow-up. Test report
hashes and exact inputs stay attached to the original runs.

| Reverified bundle | Entries | SHA-256 of `SHA256SUMS` |
|---|---:|---|
| V33 `priority10-publication` | 545 | `c3db94ab83d232177c9ddb1e71a9c01c3f136ae02804a2b6314199a21d16dac5` |
| V33 `priority10` | 1,277 | `060322bc4e64df2406a7ce406e7f75f7283ea4be9390eee39cfda87ab12374e8` |

All **1,822 entries** rehash successfully. The 13-artifact inventory SHA-256 is
`f806ed80a5a18c9633e1d1e6e3a31fec777ea1543e37e3b77ccc8fb9d644a9f8`.
The original consumer dependency-classpath SHA-256 is
`30f7b24f015b094e5623bb84a31cdb39cfba8272dcf944688a696ad68cdafb29`.
These local ignored bundles must be preserved before root clean. A checkout
without them must obtain and rehash them or rerun the tracked verifiers; this
document alone is not a substitute for artifact provenance.

## Toolchain and Dependencies

Fresh stages use Oracle JDK **21.0.8**, Maven **3.9.9**, Java target **21**,
`.mvn/maven-central-settings.xml` and a writable populated general Maven cache.
`MAVEN_OPTS='-Xmx512m -XX:ActiveProcessorCount=2'`; test forks additionally use
`-XX:+DisableExplicitGC`. GraalVM/native-image **25.0.3** was identified with
`--version` only, not used to compile or run a native image in Priority 1.

The fresh effective reactor POM, starter dependency tree and dependency classpath
are retained under `dependencies/`. Supported rows remain Boot **4.0.0** and
**4.1.0**; no upgrade or new support claim is made. The 2.x/Boot 3 maintenance
line is outside this work.

| Component | Fresh default starter resolution | Reused genuine Boot 4.1 consumer resolution |
|---|---|---|
| Spring Boot | 4.0.0 | 4.1.0 |
| Spring Framework / WebFlux | 7.0.1 | 7.0.8 |
| Reactor Core / Reactor Netty | 3.8.0 / 1.3.0 | 3.8.6 / 1.3.6 |
| Netty | 4.2.7.Final | 4.2.15.Final |
| Jackson Databind | 3.0.2 | 3.1.4 |
| Micrometer Core | 1.16.0 | 1.17.0 |
| Resilience4j | 2.4.0 | 2.4.0 |
| Caffeine | 3.2.3 | 3.2.4 |

These rows have different framework stacks and must not be compared as a starter
speedup. Priority 2 must keep non-starter dependencies identical within each
release-to-release measurement pair. The starter module's test/compile classpath
contains optional libraries; it is not evidence that they reach a minimal consumer.

## Effective Profiles

This freezes the profile boundaries to carry into Priority 2, not JMH payloads,
sample counts, bean-materialization counts or a claim that every combination
was freshly executed. Inventory is based on enforcing source and the named
regression/assembled witnesses. Priority 2 will build matched workloads;
Priority 3 will measure discovery, preparation and ownership costs.

### Common Declared Defaults

The [properties](../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/config/ReactiveHttpClientProperties.java)
and [factory](../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/core/ReactiveHttpClientFactoryBean.java)
establish the following before application overrides:

- No selected cache policy or auth provider; resilience disabled with all four
  operator names null. Retry methods GET/HEAD are eligibility only, not selection.
- Exchange logging, redirects, compression and HTTP/2 off. No generated
  idempotency key unless the method declares it. Client request timeout is unset
  (getter 0) and logical-call timeout is 0; explicit method/API timeout settings
  are absent from the default fixture.
- Codec aggregation limit 2 MiB. The factory retains WebClient default codecs;
  Boot's Jackson 3 mapper supplies the starter JSON codec when available.
- Connect timeout 2,000 ms; network read/write safety nets 60,000 ms each.
  The factory owns a connection provider: max 200 connections, pending acquire
  timeout 5,000 ms, idle/lifetime/background-eviction bounds unset (0).
  Transport retry is explicitly disabled. This is not a timeout-free path.
- Pool metrics off; observability master on; histogram/cache observability/
  diagnostics endpoint off; health selection on but conditional on its classes
  and registry. Default request body/response body observation options are off.
- Default header/query maps are empty; no proxy or TLS override. The required
  base URL and concrete annotated/API-ref method remain part of every fixture.

Pool metrics are independent of the observability master. A custom observer or
hook remains behavior with **no MeterRegistry** or disabled built-in exports.
Do not equate disabled metric exports with absence of consumers or reporting work.

### Factory and WebFlux Boundary

The [auto-configuration](../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/config/ReactiveHttpClientAutoConfiguration.java)
provides a prototype starter builder unless replaced, applying Boot builder
customizers once per instance. The factory configures connector/codecs, then
correlation, optional auth, ordered matching per-client customizers, framing
validation and final request observation. Actual application customizers and
builder replacements must be inventoried; their work is not starter-only cost.

Inbound correlation/header WebFilters are reactive-web-context beans, not
outbound filters on a headless context. The inbound allow-list defaults empty
(capture names not excluded by the policy), with sensitive names redacted by the
deny-list; it is not automatic forwarding. Distinguish headless outbound calls
from WebFlux requests that already carry a context snapshot.

Common infrastructure includes properties, metadata cache, error decoder,
diagnostics provider and built-in auth-provider factories. A provider-factory
bean is not an active auth provider or token-service pool. JSON codec activation
requires a mapper. Method plans are cached, but observer/hook providers are
queried per invocation. The handler chooses stateless versus stateful execution;
even stateless execution retains required request-body ownership cleanup.

| Profile | Effective selection and materialization boundary | Witness / use |
|---|---|---|
| V34-P01: named minimal ordinary call | Common client defaults; no observer/hook/logger/auth or generated key, no cache and no operators. A hand-built proxy has application-supplied WebClient/transport and provider behavior; do not silently attribute factory network defaults to it. | Internal planning/invocation control only; matched builder/transport is still required before end-to-end claims. |
| V34-P02: auto-configured defaults, no registry | Common defaults through the factory; optional classes may be present but no registry/observer/hook beans supplied. Built-in Micrometer observer and health contributor are absent; builder, ordinary connector/codec/filter infrastructure and dynamic empty discovery remain. | Fresh `defaultObservabilityWithoutRegistryDoesNotCreateBuiltInObserver`; source-level factory inventory. Use headless and WebFlux/context forms as separate workload dimensions. |
| V34-P03: defaults with registry | P02 plus a MeterRegistry bean and its applicable classes. Built-in request observer is selected by the default master setting; reporting is stateful. Health contributor is conditional on health classes/selection. No cache/histogram/pool metric selection is implied. | Existing observer/health auto-configuration controls; registry-present is not the no-observer baseline. |
| V34-P04: present, enabled-only resilience | P02 plus `resilience.enabled=true`, no operator name/annotation. No operator or registry lookup is selected, including lazy registry beans, but current handler state selection still sees the enabled flag. Auth and cache remain unselected. | `ExplicitResilienceActivationContractTest` covers Mono/Flux pass-through and lazy registry non-materialization. Keep separate from P01/P02, not a claim of identical allocation. |
| V34-P05: physical optional absence | Minimal assembled consumer has no Caffeine, Resilience4j registries, MeterRegistry, OTel API or helper. Its fixture explicitly sets enabled-only resilience, as in P04; two real GET subscriptions dispatch twice. Normal connector/codec/correlation/framing behavior remains. | [Cache-disabled consumer](../../.github/boot4-cache-disabled-consumer/src/test/java/io/github/huynhngochuyhoang/httpstarter/cachedisabled/Boot4CacheDisabledConsumerTest.java); revalidated one-case upper-row evidence. FilteredClassLoader unit controls are not a substitute for this physical classpath. |
| V34-P06: application observer/hook without exports | P02 plus an application observer or supported lifecycle hook, tested separately and with ordered multiple consumers where applicable. Include master-off/no-registry as an explicit control. Provider discovery, per-client supports checks and terminal callbacks remain requested work. | Fresh application-observer bean control plus existing lifecycle tests; disabled exports cannot justify caching a permanently empty provider result. |
| V34-P07: independent pool telemetry | P02 with pool `metrics-enabled=true` and an actual global registry sink; ordinary master on/off is a separate setting. Factory pool registrar is selected by the pool flag, not ordinary observer selection. | Source inventory and existing configuration binding; no new live pool measurement claimed. Register/remove the sink and factory-owned meters in the matched fixture, with stable pool limits. |
| V34-P08: selected-feature sentinels | One feature at a time over the relevant ordinary profile: metadata logging; explicit auth; logical deadline; named resilience operator; selected cache policy; histogram/cache metrics; OTel observer/propagation. Record the actual bean, limits and every implied filter/provider before measurement; presence alone is insufficient. | Reuse existing supported fixtures. Cache requires Caffeine, eligibility/key/customizer safety, explicit TTL/capacity and separate cache-metric selection; OTel requires its module/API/bean and configured span/propagation switches. Exact sentinel workloads/values belong to Priority 2, not a silently selected implementation. |

Resilience tagged binders are conditional on registry beans and metrics classes;
application-provided registries can add their own meters without any client
operator selection. OTel and Boot builder filters can also introduce work beyond
the ordinary Micrometer observer. Each workload must record those inputs rather
than relying only on `observability.enabled` or a client flag.

## Candidate and Safeguard Inventory

| Candidate | Inspected boundary and next question | Existing limit |
|---|---|---|
| V34-C001 | `getObserver()` / `getLifecycleHooks()` discover and compose per invocation; measure empty/single/multiple provider work. | Late registration, prototype/provider materialization, order and client support are contracts, not redundant work by assumption. |
| V34-C002 | `usesSubscriptionState()` / `RequestBodyOwnership` separate stateful/stateless execution and body cleanup. | No-body/immutable-body allocation requires measurement; resource bodies still require transfer/release/discard ownership. |
| V34-C003 | Metadata plans, argument resolution, URI/default/header projection. | Preserve V33 fresh public metadata support, API-ref precedence, concrete generics, wire order and invocation/subscription timing. |
| V34-C004 | Effective cache/operator/auth selection and resource/meter acquisition. | No selected cache returns no manager; no selected operator avoids its registry lookup. Common provider holders and configured application resources are distinct from feature-specific allocations. |
| V34-C005 | Construction, first call and AOT lifecycle infrastructure. | The V33 tracking guard stops/clears observations after normal singleton initialization. Do not reopen general bean selection or label cold work a warm-call regression without a witness. |

Sources: [handler](../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/core/ReactiveClientInvocationHandler.java),
[local cache manager](../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/core/LocalResponseCacheManager.java),
[binding lifecycle](../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/config/PropertiesBindingLifecycle.java).
These are inspection questions, not approved fixes or confirmed cost defects.

**V32-F004** failed-construction cleanup remains guarded by
[ResourceOwnershipReviewTest](../../reactive-http-client-starter/src/test/java/io/github/huynhngochuyhoang/httpstarter/core/ResourceOwnershipReviewTest.java),
including real meter-owner observations before ownership transfer.
**V32-F005** keeps ordinary tests independent of forced GC, with the separate
[controlled reachability lane](../../reactive-http-client-starter/src/test/java/io/github/huynhngochuyhoang/httpstarter/core/CacheOwnershipReachabilityIT.java)
for collection claims. This priority runs ordinary tests with explicit GC disabled,
not the collection lane. V33 builder, public metadata and AOT selection corrections
remain published behavior, including their documented unsupported shapes.

Excluded: new features/SPIs, dependencies/modules, performance switches, changed
defaults/pools/timeouts/retry/cache rules, automatic propagation, universal policy
or bean resolvers, pooled mutable caller state, unrelated production memory/mesh
diagnoses and live-reconfiguration support. No measured cost or leak premise is
assumed. Findings still need Priority 3 evidence and Priority 4.3 approval.

## Verification and Limits

Fresh checks cover Maven reactor validation, published-provenance fixtures,
source/binary compatibility fixtures (including expected rejection controls),
script syntax, dependency inventory and focused configuration/activation/
lifecycle/ownership tests. Final actual test totals and completion are recorded
under [Priority 1](CHECKLIST.md); reports include the documentation archive,
version/readiness guards and compiled public guidance example.

The baseline-record regression was first run without this record and failed
with `NoSuchFileException`; its XML/log is retained under `red/`. An initial reuse
audit expected the parity runner to be wholly unchanged and failed on the expected
coordinate transition; `reuse/` preserves that result. `reuse-confirmed/` verifies
the exact coordinate-only difference. Neither failure changes a production contract.
The first combined 220-case run also retained one new documentation-assertion
failure: it expected "physically absent" instead of the record's equivalent
"physical optional absence". The assertion wording was corrected; all 140
profile/ownership/example cases passed in that run.

Reproduction from the repository root, using a writable general Maven cache:

```bash
export JAVA_HOME=/usr/lib/jvm/jdk-21.0.8-oracle-x64
export PATH="$JAVA_HOME/bin:$PATH"
REPO="$PWD/target/v33-native-runs/native-g0ynw95x/repository"
export MAVEN_OPTS="-Xmx512m -XX:ActiveProcessorCount=2 -Dmaven.repo.local=$REPO"
mvn -B -ntp -s .mvn/maven-central-settings.xml validate
bash scripts/verify-published-baseline-fixtures.sh
bash scripts/verify-api-compatibility-fixtures.sh
mvn -B -ntp -s .mvn/maven-central-settings.xml help:effective-pom \
  -Doutput="$PWD/target/release-evidence/v34/priority1/effective-reactor.xml"
mvn -B -ntp -s .mvn/maven-central-settings.xml -pl reactive-http-client-starter \
  dependency:tree -DoutputFile="$PWD/target/release-evidence/v34/priority1/starter-tree.txt"
mvn -B -ntp -s .mvn/maven-central-settings.xml -pl reactive-http-client-starter \
  dependency:build-classpath -Dmdep.outputFile="$PWD/target/release-evidence/v34/priority1/starter-classpath.txt"
mvn -B -ntp -s .mvn/maven-central-settings.xml -pl reactive-http-client-starter \
  -DargLine=-XX:+DisableExplicitGC \
  -Dtest=DocumentationReleaseArtifactTest,V33GuidanceExampleTest,ReactiveHttpClientPropertiesTest,ReactiveHttpClientAutoConfigurationTest,Boot4AutoConfigurationTest,InboundHeadersAutoConfigurationTest,ExplicitResilienceActivationContractTest,EffectiveResiliencePolicyTest,ReactiveHttpClientLifecycleHookTest,ResourceOwnershipReviewTest,PropertiesBindingLifecycleTest test
bash -n scripts/verify-supported-matrix.sh scripts/verify-published-consumer.sh \
  scripts/verify-published-release-artifacts.sh scripts/verify-published-baseline-provenance.sh
git diff --check
```

Use new output directories when retaining another run. Recheck old `SHA256SUMS`
from each bundle's directory with `sha256sum --quiet -c SHA256SUMS`. The published
inventory files contain absolute repository paths; obtain the matching retained
repositories or rerun the verifiers rather than fabricating provenance markers.
Without retained evidence, use the tracked publication and consumer commands in
the V33 publication record with fresh repository/output locations.

No full reactor test, new strict project API comparison, Boot matrix execution,
assembled-consumer execution, native build, signing or benchmark was performed
for this baseline. Reused results do not close later performance or parity gates.
There is no new public performance claim or selected release.
