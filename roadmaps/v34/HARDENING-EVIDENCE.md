# V34 Disabled-Policy Cost Isolation

> **Status:** Priority 6 complete, 2026-10-02
> **Delivered production IDs:** none; V34-C004 rolled back after failed benefit gate
> **Remaining verification:** Priorities 7-10 open; no retained production optimization
> **Release scope:** unselected

This executes [Priority 6](CHECKLIST.md) under the
[bounded C004-only decision](IMPROVEMENT-DECISION.md). Starting clean source:
`eaf256eb4c8569a2a524e5dd51045d6ee4cf2ff4`. All candidate results include the
recorded uncommitted patch. They are not clean-commit release evidence. Earlier
P1-P5 records retain their historical status; this record supersedes only their
pending C004 implementation statement.

## Candidate and Rollback

The attempted production change was only in [EffectiveCachePolicy][policy]:
four private constants retained two disabled selections and their corresponding
disabled decisions. `Selection.disabled` reused `DISABLED` and `METHOD_DISABLED`
values; `decide` reused the matching decision after existing selection resolution.
The sources stayed distinct, with only enums, false and null payloads retained for
the class-loader lifetime. Other helper inputs retained their prior construction
behavior; selected and invalid decisions were not interned.

The candidate passed correctness controls but failed the mandatory repeatable
P02 allocation-benefit gate below. It was **rolled back**, not promoted based on
source-level reuse. No production change remains relative to the starting commit
or published `v4.4.2` sources. The rebuilt rollback JAR's 310 starter class files
match the Central baseline byte-for-byte; only artifact/version metadata differs.
Candidate artifacts and patches remain in the evidence bundle. No additional
refinement was attempted: the measured primary path provides no evidence for
another useful change within the approved value-reuse boundary. Broader work
would need a new explicit decision.

The complete per-invocation `CacheWorkPolicy.validator` scan is unchanged. Every
live selection, eligibility/source, policy name, refresh and work-limit comparison
still runs at its former boundary. No validation, observer/hook lookup, support
check, body inspection/release, reporting state, timer, span, cache manager,
scheduler, transport subscription or cleanup was removed. There is no public API,
dependency, default, coordinate, activation or ownership change. C001/C002
production discovery/reporting work is **N/A**, as approved; broader C004 and C005
resource/lifecycle work remains deferred.

## Regression Evidence

Tests were added before production edits. The red run has 85 cases, with exactly
three failures on the intended disabled-identity assertions and no errors/skips;
selection and mutation controls already pass. After rollback, reuse-only identity
assertions became value/source equality assertions: reuse is no longer a delivered
contract. No mutation, source distinction or dispatch assertion was weakened.
Seven new behavioral cases remain:

- Distinct disabled sources, fixed null payload, repeated decisions across
  independent clients, inherited metadata, null client/cache config and inert
  invalid policy definitions.
- Concurrent decisions over independent configurations; selected decisions still
  read replacements/removals, produce the same invalid reason and are not shared.
- An unchanged `@CacheDisabled` invocation rejects a changed sibling work bound
  or an initially disabled client becoming selected, before another dispatch.
- Concrete inherited/API-ref validation retains source identity and still rejects
  unresolved API verb and client-policy mutation without altering the frozen snapshot.
- Four gated first invocations each materialize their own prototype observer;
  resubscriptions retain that invocation's observer but create separate reporting
  states. No cache manager or dispatch is created at publisher assembly.

The original whole-interface scan witness remains: every invocation checks both
abstract methods; repeating the publisher does not add invocation-level checks.
Existing cold-call/live-snapshot mutation rejection remains, including capacity,
refresh and mapping changes. No forced collection or performance threshold is
asserted in an ordinary unit test.

The final rollback starter run passes **303 tests in 18 classes**, zero failures/errors/skips,
with explicit GC disabled. Counts are exact XML suite names, not substring matches:

| Controls | Cases |
|---|---:|
| `DeclarativeCachePolicyTest`, `CacheWorkLimitContractTest`, `CacheWorkPolicyEnforcementTest` | 27 + 46 + 12 |
| `DefaultPathCostOwnershipTest`, `ResourceOwnershipReviewTest` | 15 + 18 |
| `ExplicitResilienceActivationContractTest`, `ReactiveHttpClientAutoConfigurationTest` | 17 + 23 |
| `ReactiveHttpClientLifecycleHookTest`, `CompositeHttpClientObserverTest` | 16 + 1 |
| `MicrometerHttpClientObserverTest`, `Boot4HttpClientHealthIndicatorTest`, `LocalResponseCacheObservabilityTest` | 35 + 18 + 17 |
| `InvocationCompositionReviewTest`, `RetryRedirectAuthReplayCompositionContractTest`, `LogicalCallTimeoutBudgetContractTest` | 7 + 9 + 11 |
| `IdempotencyKeySupportTest`, `HousekeepingTest`, `SubscriptionLocalReportingStateTest` | 13 + 13 + 5 |

These cover late/provider/ordered consumers, no-registry/master-off callbacks,
enabled-only versus explicitly selected resilience, terminal-once attempts,
auth replay/redirects/deadline phase, final filter-mutated evidence, cache outcomes,
downstream health exclusion, privacy and independent pool gauges. Untouched
advanced body/lifecycle paths still require the explicit applicability review in
Priorities 7-9; this matrix is not a universal parity claim.

The initial green run (302 cases) preceded the concurrent witness. Its first
303-case run failed one fixture assertion: the shared no-observer helper expected
`getIfAvailable` despite a registered observer. The test now asserts four ordered
lookups and no empty fallback; production did not change. Both attempts and the
failing XML are retained, not added to distinct-case totals or called a defect.

## Optional Integrations

The assembled Boot 4.0.0 minimal consumer passes its one case on both the candidate
and rebuilt rollback JAR, not reactor classes: no Caffeine, Resilience4j registry,
MeterRegistry, OTel API or test helper is present. Enabled-only resilience selects
no operators. Two real GETs dispatch twice; all server requests are counted and
non-GET requests cannot pass. Dependency tree/classpath and artifact identity
are retained. This P05 control is not a genuine Boot 4.1 or native run.

OTel verification passes **62 tests** in five classes on each artifact: observer
38, auto-configuration 9, size observability 6, metadata 4 and context propagation
5. Final documentation/archive/readiness passes 85 cases, and Python input/result
guards pass 14. All final runs have zero failures/errors/skips. Benchmark builds
pass 75 correctness cases each; they are overlapping runs, not 150 distinct cases.
No feature was suppressed to lower measured allocation.

## Exploratory Cost

The primary selection is the 12 warm-publisher rows: six profiles x
GET/TARGET. Initial order is published baseline then candidate; P02 GET repeats
candidate then baseline. Both artifacts use the unchanged harness and non-starter
dependency stack, Oracle Java 21.0.8, Maven 3.9.9 and Boot 4.0.0. Each row has two
forks, five one-second warmups, five one-second measurements, one thread, avgt/ns,
GC profiling and the frozen 512 MiB / ActiveProcessorCount=2 JVM flags.

Input comparison verifies all 39 harness source files and 120 non-starter JARs
are identical. The candidate changes only `EffectiveCachePolicy` class-family
bytes among 310 starter classes. This is an exploratory dirty-candidate comparison,
not final scoring:

| Profile / scenario | Baseline B/op | Candidate B/op | Baseline ns/op | Candidate ns/op |
|---|---:|---:|---:|---:|
| AUTO_NO_REGISTRY / GET | 832.013 | 832.013 | 307.65 | 310.89 |
| AUTO_NO_REGISTRY / TARGET | 2024.028 | 2024.030 | 670.76 | 706.29 |
| AUTO_REGISTRY / GET | 1744.020 | 1744.021 | 472.39 | 466.13 |
| AUTO_REGISTRY / TARGET | 2872.038 | 2872.037 | 863.78 | 859.56 |
| HOOK / GET | 1872.023 | 1744.022 | 536.26 | 519.11 |
| HOOK / TARGET | 2872.037 | 2872.038 | 866.57 | 881.68 |
| MINIMAL / GET | 832.007 | 832.009 | 257.46 | 293.00 |
| MINIMAL / TARGET | 1992.016 | 1992.016 | 571.24 | 571.31 |
| OBSERVER / GET | 1744.021 | 1744.020 | 485.23 | 466.80 |
| OBSERVER / TARGET | 2872.036 | 2872.037 | 840.76 | 842.50 |
| RESILIENCE_ENABLED_ONLY / GET | 1264.017 | 1136.016 | 387.37 | 356.81 |
| RESILIENCE_ENABLED_ONLY / TARGET | 2312.034 | 2312.033 | 784.39 | 751.57 |

The first P02 GET comparison has overlapping allocation intervals: baseline
832.01277 +/- 0.03389 B/op, candidate 832.01330 +/- 0.03556 B/op. In reversed
order the baseline mean is 960.01428 +/- 203.98708 B/op, with fork means 832.01336
and 1088.01521; candidate is 832.01270 +/- 0.03401 B/op, with fork means 832.01198
and 832.01342. The baseline's higher reverse-order mean cannot establish a
reduction distinguishable from fork variance; the initial pair has none. Both
orders fail the benefit gate. No explanation of the fork split is established.

Frozen regression-review triggers produce no new flags, which is not evidence
of benefit or a blanket no-regression result. The initial baseline also splits
for HOOK GET (2000/1744 B/op) and enabled-only GET (1136/1392 B/op); enabled-only
TARGET splits 2328/2296 B/op on both artifacts. Preserve these samples rather
than treating their means as causal savings. P3's unresolved flag is not cleared.

Artifact SHA-256 values (candidate JARs retained even after rollback):

| Artifact | SHA-256 |
|---|---|
| Central `4.4.2` starter | `fb8646ce2f6ed172598ff446cdfa9da2f348c0fd56a2b4c9a02e7ac1dae803c1` |
| Rejected candidate starter | `802bbbccf9c5952d9de59768f81a735c34d69fd4fce023cc48d64ed0aff46c38` |
| Baseline benchmark JAR | `7f271951cf1227ef7ecafc4a711832c926cd6c02417eedaf6bbaa309cb663402` |
| Rejected candidate benchmark JAR | `32774c9f679c5e9d2eaa1f8cc93153fe9c8126ea209b911cda49eaaa7e9d8715` |

No build, test or profiler runs alongside measurement. Raw samples, intervals,
machine pressure/limits, artifact hashes, commands/exits and dirty source are
preserved. These are local exploratory measurements, not final Priority 10
scoring, public benchmark promotion or a deployment throughput/RSS claim.

## Remaining Gates and Reproduction

No optimization of providers, snapshots/body owners, request projection or
framework/resource initialization is authorized here. The C004 rollback follows
the existing acceptance rule; it does not select a review-only closure or release.
The P3
enabled-only allocation flag remains unresolved and must be reconciled in the
final matched 60-row matrix. A favorable P02 result cannot clear that prior flag.

Priorities 7-10 remain open for ownership, assembled/Boot/AOT/native applicability,
strict API/packaging and final matched measurement. There is no retained
production diff; no old native binary is represented as a new verification run.
Release scope and `plannedFinalVersion` remain unselected/null. No public
performance claim or version bump is selected.

Evidence: `target/release-evidence/v34/priority6/`; final inventory in the checklist.
The checklist is an external integrity index, excluded from source-copy sealing.
Use fresh output directories on reproduction, never overwrite failed attempts.

```bash
export JAVA_HOME=/usr/lib/jvm/jdk-21.0.8-oracle-x64
export PATH="$JAVA_HOME/bin:$PATH"
export MAVEN_OPTS='-Xmx512m -XX:ActiveProcessorCount=2'
mvn -B -ntp -s .mvn/maven-central-settings.xml -pl reactive-http-client-starter \
  -Dtest=DeclarativeCachePolicyTest,CacheWorkLimitContractTest,CacheWorkPolicyEnforcementTest,DefaultPathCostOwnershipTest,ResourceOwnershipReviewTest,ExplicitResilienceActivationContractTest,ReactiveHttpClientAutoConfigurationTest,ReactiveHttpClientLifecycleHookTest,CompositeHttpClientObserverTest,MicrometerHttpClientObserverTest,Boot4HttpClientHealthIndicatorTest,LocalResponseCacheObservabilityTest,InvocationCompositionReviewTest,RetryRedirectAuthReplayCompositionContractTest,LogicalCallTimeoutBudgetContractTest,IdempotencyKeySupportTest,HousekeepingTest,SubscriptionLocalReportingStateTest \
  -DargLine='-XX:+DisableExplicitGC -XX:ActiveProcessorCount=2' test
mvn -B -ntp -s .mvn/maven-central-settings.xml -pl reactive-http-client-starter -am -DskipTests install
mvn -B -ntp -s .mvn/maven-central-settings.xml -f .github/boot4-cache-disabled-consumer/pom.xml \
  -DargLine='-XX:+DisableExplicitGC -XX:ActiveProcessorCount=2' clean test
mvn -B -ntp -s .mvn/maven-central-settings.xml -pl reactive-http-client-otel \
  -DargLine='-XX:+DisableExplicitGC -XX:ActiveProcessorCount=2' test
```

The recorded current commands select the separate local repository
`target/v33-native-runs/native-g0ynw95x/repository`; baseline builds use only
`target/published-baseline-repositories/benchmark-v34-p2-4.4.2`. Exact argument
arrays are preserved; the [workload contract](WORKLOAD-CONTRACT.md#reproduction)
provides matched build commands. Use the existing publisher benchmark selection,
not a replacement microbenchmark of a constant-returning helper.

[policy]: ../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/core/EffectiveCachePolicy.java
