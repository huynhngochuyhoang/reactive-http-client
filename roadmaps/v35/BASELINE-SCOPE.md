# V35 Baseline and Complete Deferred Scope

> **Recorded:** 2026-10-04
> **Published baseline:** `4.4.2`
> **Development coordinate:** `4.5.0-SNAPSHOT`
> **Implementation authorization:** pending Priority 3.3; no production change approved
> **Release scope:** unselected

This completes the baseline inventory for [Priority 1](CHECKLIST.md), not an
optimization, allocation diagnosis or release selection. The [seven-row ledger](FINDINGS.md)
keeps every V34 deferral open. V34 remains closed review-only, C004 remains rolled
back, and its unexplained allocation flag is **not a performance pass**.

## Reachable Provenance

Reviewed clean source: `05dfbaaa86e7f9afbdf88315ba212db7d65f0bb6`.
Tree: `fe26cf28c2cc9641e372716e82341bde5fe733a2`.
The reuse audit ran before tracked edits. Fresh verification uses that source
plus this documentation/test-only delta; it is not a clean release build.
Commands, exits, XML, source patch and audits are retained under
`target/release-evidence/v35/priority1/`.

Reachable ancestors are the published `v4.4.2` tag at
`bdacfc439b7fab7df1b319d057782c3b69ce9676`, V34's native/parity source
`fc98e58b9d5154a0ba539ea878b05c532b379554`, and its review-only closure
`21ad81bd44db15b75d6787a0ceac308939a2741e`.
The pre-squash P10 source `44d6ffaede52a5be92148cc078f287428791d511` and
P12 audit source `be2b640e75e38c13c2861165928df88a45fbd9d1` are **not ancestors**
of this checkout. Local object availability is not durable provenance.

The sealed P12 `pre-decision/audit.json` and P10
`final/parity-input-reuse.json` identify the exact Git objects below. The fresh
audit verifies them against both reachable closure `21ad81bd...` and current
`05dfbaaa...`, without changing historical commit labels or pretending either
reachable commit was the original execution source.

| Reconciled input | Git object identity |
|---|---|
| Starter main sources/resources | `42b18e0924d3dd29c30bcf5e0c3fb5bc9eeb072d` |
| Test-helper main sources/resources | `49964b33f403413dcdebc8176946716437eb3926` |
| OTel main sources/resources | `c2400fd22aa6e426697429dcbc3e31703dade86b` |
| Root POM | `a9df7c0436fc3811e83d466173fbd37dc3683149` |
| Benchmark module | `b1d3cf61fb5f79c338312618363f37086ea64d5f` |
| Scripts | `3943a89b42153a3f88c58a21b75f56e293cc0956` |
| Maven configuration | `a520ca227256d377de573a26ca1f3956921a5be4` |
| All GitHub fixtures/workflows | `b93dc8beb10536a3114f78171f6f7e43e3b9d660` |

The audit also compares each module POM, each consumer/native subtree and the
parity/native runners individually. All production main trees remain identical
to the release tag. V1-V34 records are byte-unchanged from the reachable closure.
This reconciliation permits narrowly stated unchanged-input reuse, not a claim
that the complete current test/documentation tree was tested historically.

### Version and Lifecycle

Reactor, module parents, benchmark parent and current assembled/native fixture
starter dependencies remain `4.5.0-SNAPSHOT`. Public/API/consumer/benchmark
baselines remain `4.4.2`; Java target 21 and the Boot 4.0.0 default are unchanged.
No POM, version guard, default or production source changes in this priority.

Roadmap, checklist, index and generated readiness agree on active V35:
`activeRoadmap=v35`, `releaseLane=unselected`, `plannedFinalVersion=null`.
The readiness regression covers snapshot, final-candidate and post-publication
coordinates while the checklist stays active. A generated deferred candidate
label is not release approval. Implementation still requires **Priority 3.3**;
release selection is independently gated by Priority 12.

## Revalidated Evidence

These are rehashed prior runs, **not fresh Central downloads**, not a new native
compile/execution, and not fresh consumer, strict API or scored JMH runs.

| Evidence | Revalidation and applicability |
|---|---|
| Central `4.4.2` | All 13 parent/module POM, binary, source and Javadoc artifacts match the isolated release repository, its Central remote markers and sealed publication copies. Four POMs and 146 packaged Java sources (132 starter, nine helper, five OTel) match the reachable release tag. No reactor install substitutes for a published artifact. |
| Published consumer | Its separate `consumer-4.4.2` inventory rehashes; the three project JARs on the captured classpath match the published copies, with no reactor classes directories. Recounted four ordinary and 29 all-profile passing cases; these overlap, not 33 distinct cases. Archived signature/workflow evidence is preserved, not newly queried or signed. |
| Genuine Boot consumers | V34 P9 consumer effective parents and every Boot JAR on each captured classpath are 4.0.0 or 4.1.0 respectively (not a Boot 4.0 parent with a partial BOM override). Recounted 29 assembled and one physically minimal case per row. Production/POM/fixture/runner inputs match; this is reused candidate-artifact evidence, not new Central consumption. |
| Native | Reachable P9 clean source, six fixture cases, successful compile/executable records and the binary are rehashed. Binary SHA-256 `a3476f5f749d0cb546e4c175f371ea070291923d5621d92cb819758ab9a3b10e`. Boot 4.0.0, GraalVM/native-image 25.0.3, Maven 3.9.9, target 21. The genuine Boot 4.1 evidence is JVM/AOT, not another native image. |
| API and modules | Both independent P10 strict comparisons against Central `4.4.2` retain exit 0 and source/binary failure switches. Recounted full-module XML: 2,152 starter + 80 helper + 62 OTel = 2,294 passed per Boot row, zero failures/errors/skips. Later documentation tests overlap these runs and are not included in that total. |
| Costs | Both saved benchmark JARs and all P10 reports rehash. All 60 primary rows had zero flags; reverse enabled-only GET confirmation had one unresolved allocation flag. Current fork means 1,392.018/1,136.015 B/op versus baseline 1,136.015/1,136.015. The recorded 310 identical starter classes and 120 matched non-starter JARs exclude a retained source/dependency delta, not compiler/layout/environment effects. No causal explanation or optimization benefit is established. |

All **3,051** entries in these six manifests pass rehashing:

| Bundle | Entries | SHA-256 of `SHA256SUMS` |
|---|---:|---|
| V34 `priority9` | 1,213 | `596d511a2683fad7bbdd76fccf4779b9e80a9f7045883739cd023c0b0a22c5b7` |
| V34 `priority10` | 973 | `0987d689fa4c131763d12296297380d2698346a2dec78793be4a8093652604cc` |
| V34 `priority11` | 156 | `bdb0e8a4b1b9b890a73f2465fe3073f30da699c50cff940ba18cb6693fddde2a` |
| V34 `priority12` | 103 | `bfff9f508b3642e4f7345dddd72712f5d3e401cf24f091ee1dcb65b88849f532` |
| P9 native `native-feynp9yy/evidence` | 61 | `d62ac7cd25d3f9eb816595b9ef6b07d0af431dfa53e4781c8d7968dd9a4c6821` |
| V33 `priority10-publication` | 545 | `c3db94ab83d232177c9ddb1e71a9c01c3f136ae02804a2b6314199a21d16dac5` |

The published artifact inventory hash remains
`f806ed80a5a18c9633e1d1e6e3a31fec777ea1543e37e3b77ccc8fb9d644a9f8`.
`reuse/audit.json` records each report hash/count, manifest hash and object check.
Preserve ignored bundles before cleaning. In a clone without them, obtain and
rehash the bundles or reproduce the affected lane using the tracked
[consumer/native runners](../v34/PARITY-EVIDENCE.md#reproduction-and-applicability),
[API commands](../v34/COMPATIBILITY-PERFORMANCE.md#reproduction-and-evidence) and
[matched harness](../v34/WORKLOAD-CONTRACT.md#reproduction), with reachable source
and fresh output/repository paths. Do not attempt checkout of a squash-local hash
or infer a pass from this table. New V35 scoring must first freeze Priority 2 rules.

## Toolchain and Dependencies

Fresh guards and tests use Oracle JDK 21.0.8, Maven 3.9.9, target 21 and
`.mvn/maven-central-settings.xml`. Maven heap is 512 MiB with two active CPUs;
ordinary test forks disable explicit GC. A writable populated general repository
is used for current builds, never installed into the published repositories.

The fresh effective reactor POM, dependency tree/classpath and JAR hashes are
retained in `dependencies/`. Upper-row inputs are revalidated from V34 P9/P10,
not freshly resolved in this priority. Effective starter stacks are:

| Component | Boot 4.0.0 | Boot 4.1.0 |
|---|---|---|
| Spring Framework | 7.0.1 | 7.0.8 |
| Reactor / Reactor Netty | 3.8.0 / 1.3.0 | 3.8.6 / 1.3.6 |
| Netty | 4.2.7.Final | 4.2.15.Final |
| Jackson | 3.0.2 | 3.1.4 |
| Micrometer | 1.16.0 | 1.17.0 |
| Resilience4j | 2.4.0 | 2.4.0 |
| Caffeine | 3.2.3 | 3.2.4 |

These are supported-stack rows, not a cross-stack performance comparison.
Genuine consumer parent and classpath hashes are in `reuse/audit.json`.

## Effective Profiles

Retain the V34 IDs, [defaults and workload definitions](../v34/BASELINE-SCOPE.md#effective-profiles).
No setting below is a new performance switch. Method/API timeout or application
customizer additions must be recorded separately from the defaults.

| Profile | Effective work and retained control |
|---|---|
| V34-P01 minimal/public | Application-supplied WebClient/provider/transport, no selected observer/hook/auth/cache/operator. Hand-built minimal cost is not the actual Spring default; compare legacy constructors and static creation separately. Public constructors can hold a lazy cache manager even when factory creation returns no manager. |
| V34-P02 actual auto-configuration, no MeterRegistry | Default master on, dynamic empty observer/hook discovery, connector, codecs, correlation/framing filters and body cleanup remain. `DefaultPathCostOwnershipTest` observes calls/materialization and state counts, not just flags. Default transport owns its pool and network timeout safety nets. |
| V34-P03 actual auto-configuration with registry | Default request observer selects per-subscription reporting; health depends on applicable beans/classes. This is not P02 or automatically selected cache/histogram/pool telemetry. |
| V34-P04 enabled-only resilience | `enabled=true` with no operator name/annotation selects no operator or registry lookup, but the current stateful-path decision still sees enabled. Preserve the Mono/Flux and lazy-registry controls; the allocation flag belongs to this profile, not selected Retry. |
| V34-P05 physically absent optional integrations | Minimal assembled fixture lacks Caffeine, Resilience4j registries, MeterRegistry, OTel API and test-helper; enabled-only resilience still performs two real GET dispatches. Reused physical classpath evidence is distinct from disabled flags or FilteredClassLoader tests. |
| V34-P06 application observer/hook | No-registry or master-off does not disable application consumers. Ordered/provider/prototype/support and late registration remain per-invocation behavior, captured before subscription. Include empty/single/multiple, repeated and concurrent subscription controls. |
| V34-P07 independent pool gauges | Pool metrics are independent of the ordinary observability master. The structural fixture selects a real registry sink and checks owner cleanup with the master both on and off; defaults leave these gauges unselected. |
| V34-P08 selected-cache/work and other sentinels | Explicit cache policy, Caffeine, eligibility/key/customizer safety and frozen bounds; work limits and cache metrics separately selected. Retain real API names, hit/miss/single-flight/refresh/rejection/deadline ownership controls. Logging, auth, named resilience and OTel remain separately selected sentinels, not implicitly absent because built-in metrics are off. |

The [handler](../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/core/ReactiveClientInvocationHandler.java),
[factory](../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/core/ReactiveHttpClientFactoryBean.java)
and [auto-configuration](../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/config/ReactiveHttpClientAutoConfiguration.java)
remain the current source boundary. Configuration alone cannot prove absence of
provider streams, cache-identity WebClient construction, scheduler handles,
application-owned resources or terminal state. Do not label a dormant handle a
scheduled task, acquisition or leak without a measured owner/lifetime witness.

## Verification and Limits

Fresh verification covers reactor validation, public-baseline and strict-API
negative fixtures, script syntax, dependency inventory and the focused ordinary
suite named below. The new documentation guard first fails with the baseline
record absent. It then checks seven unresolved ledger sections, effective profile
coverage, local links, completed P1, later unchecked priorities and unchanged
approval/release gates. Existing archive/version/readiness guards remain active.

The initial fixture-script attempt stopped at its nested Maven lookup before
finishing; the log/partial outputs remain in `guards/`. The corrected run applies
the writable repository through `MAVEN_OPTS` as well as direct Maven arguments.
No failed attempt is treated as a passing fixture run. Actual final totals and
the bundle seal are recorded in [Priority 1](CHECKLIST.md).

Reproduce the fresh checks from the repository root (set `REPO` to a writable
general Maven cache; this is not the Central-only baseline repository):

```bash
export JAVA_HOME=/usr/lib/jvm/jdk-21.0.8-oracle-x64
export PATH="$JAVA_HOME/bin:$HOME/.sdkman/candidates/maven/current/bin:$PATH"
REPO="$PWD/target/v33-native-runs/native-g0ynw95x/repository"
export MAVEN_OPTS="-Xmx512m -XX:ActiveProcessorCount=2 -Dmaven.repo.local=$REPO"
mvn -B -ntp -s .mvn/maven-central-settings.xml validate
bash scripts/verify-published-baseline-fixtures.sh
bash scripts/verify-api-compatibility-fixtures.sh
bash -n scripts/verify-supported-matrix.sh
mvn -B -ntp -s .mvn/maven-central-settings.xml -pl reactive-http-client-starter \
  -DargLine='-XX:+DisableExplicitGC -XX:ActiveProcessorCount=2' \
  -Dtest=DocumentationReleaseArtifactTest,DefaultPathCostOwnershipTest,ResourceOwnershipReviewTest,PropertiesBindingLifecycleTest,ExplicitResilienceActivationContractTest,EffectiveResiliencePolicyTest,ReactiveHttpClientAutoConfigurationTest,ReactiveHttpClientPropertiesTest,CacheWorkPolicyEnforcementTest,StreamingUploadOwnershipTest,SubscriptionLocalReportingStateTest,RequestContextSnapshotTest test
git diff --check
```

Archive exits/XML and stop on failures; do not sum overlapping historical/fresh
executions. No fresh full Boot matrix, controlled reachability, profiler, JMH,
consumer or native run is claimed. Ordinary tests are not collection proofs.
Priority 2 must explain or retain the allocation gate; P1 establishes neither a
safe optimization nor a pod-memory diagnosis.
