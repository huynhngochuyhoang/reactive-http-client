# V33 Compatibility and Targeted Cost

> **Status:** Priority 8 complete; compatible within the reviewed surface and measured cost limits.
> **Release scope:** unselected.

## Pre-Measurement Decision

Recorded before measurement on 2026-09-28, source base
`9f5d1e10` plus the benchmark/evidence changes recorded with each run.
Compare the accepted production diff with published `4.4.1`, not another snapshot.

F001 changes construction-time builder-definition ownership inspection. F003
changes AOT selection/binding and adds package-private lifecycle tracking during
bean initialization, cleared at normal singleton initialization or destruction.
Neither changes request execution. F002 changes `RequestPlan.from`, including
its ordinary parsed-metadata branch, so cold-plan and warm-call measurements
are required even though the invocation handler, resolver, transport and cache
operators are unchanged. A broad no-benchmark disposition is not appropriate.

`V33PlanningCostBenchmark` runs the same four rows with the same source/dependency
stack against published `4.4.1` and the assembled candidate: parsed concrete-plan
construction, fresh annotation parse plus concrete plan, warmed publisher creation,
and warmed subscribed call. Mock exchange validates GET, encoded path, query,
header and decoded value, with exactly one exchange per subscription and none
at publisher construction. Plan and repeated-subscription witnesses run in setup.
These are no-network measurements, not TCP throughput or cold JVM startup.

Fresh public metadata without a derived API is not a functional baseline workload
in `4.4.1`. Its new validation/derivation remains correctness-tested; timing an
invalid baseline as a successful request would not be a meaningful comparison.
No quantitative claim is made for that newly supported path, builder scanning,
AOT startup, cache throughput, RSS or native startup.

Before execution, the measurement contract is: Java 21, Boot 4.0.0, JMH 1.37,
one thread, average-time nanoseconds, two forks, five 1-second warmups and five
1-second measurements per fork, GC profiler, fixed 512 MiB heap. Keep raw samples,
fork means, JMH error intervals, environment/classpaths and hashes for both builds.
Review any candidate mean latency increase above 20%, or normalized allocation
increase above max(32 B/op, 5%); confirm a flagged latency result with another
matched pair before classifying a regression. Overlapping intervals or unstable
forks limit conclusions; do not convert noisy results into improvement claims.

## Surface Inventory

- `CacheCustomizationValidator` is package-private. Genuine starter-owned builder
  definitions no longer require redundant SAFE acknowledgement; application
  replacement/customizer classification remains mandatory. Existing explicit
  acknowledgement is still accepted.
- `MethodMetadata` changes only documentation. Its public constructor, accessors
  and mutability phase remain intact. Internal `RequestPlan` derives missing
  static API state and validates incomplete fresh metadata earlier. Already
  derived/delegated metadata and API-ref configuration keep precedence.
- The public AOT processor retains its constructors/interface. It now follows
  runtime properties preference, binding lifecycle, metadata replacement and
  selected-policy hints; ambiguous/invalid selected state fails explicitly.
  `PropertiesBindingLifecycle` and its auto-configuration factory method are
  package-private infrastructure, not a new extension SPI.
- No production property/default, dependency version, optional-dependency flag,
  module boundary or outbound operator was changed. Version/baseline coordinates
  moved to the established `4.5.0-SNAPSHOT` / `4.4.1` lane. The mock and OTel
  production sources did not change. Existing valid workarounds remain supported.

Strict source/binary checks and behavioral tests are separate evidence: passing
japicmp alone cannot establish the corrected extension-selection behavior.
The [AOT qualifications](AOT-PROPERTIES-SELECTION.md#ownership-and-limits) still
apply: broad/ambiguous non-singleton processor identities can fail explicitly;
uninitialized raw FactoryBeans in a searched metadata scope require predictable
product-type metadata, including unrelated raw factories in that scope. Previously
run initialization callbacks are not replayed by fallback binding. These are
behavioral constraints requiring migration review, not newly removed Java APIs
or a universal lifecycle-parity claim. Priority 9 must carry that guidance forward.

## Verification

The strict root and independent starter source/binary comparisons against Central
`4.4.1` passed in separate repositories. Only third-party dependencies were seeded;
all project baseline artifacts were downloaded, with `maven-central` remote
markers, embedded/POM version checks and SHA-256 provenance. The root comparison
covers starter, test helper and OTel. No report-only profile substitutes for strict
failure-on-source/binary-incompatibility checks.

The API fixtures accepted additive/defaulted-annotation changes and rejected
removed constructors, nested methods, enum constants and source-only checked
exceptions. The provenance fixtures rejected locally installed, incomplete,
mixed-version, incorrectly versioned and self-compared baselines. Their expected
negative-case failures are successful guard tests, not failed candidate checks.

The first full reactor run passed 2,098 starter, 80 helper and 62 OTel cases with
`-XX:+DisableExplicitGC`. Binary/source/Javadoc and generation-packaging checks
passed. Benchmark-module correctness/report tests passed 35 cases against each
artifact. The focused 23-class run passes 646 cases (including optional-integration
auto-configuration, metadata/AOT, composition and deterministic ownership cases),
with explicit GC disabled. These focused results overlap with the full suite.
The separate controlled reachability lane passes 16 cases. All completed test
runs have zero failures/errors/skips; the fixture compile failure remains below.

The final `full-final` clean-module/root-verify run passes **2,099 starter**
(including **76 documentation**), **80 helper** and **62 OTel** cases: **2,241**
total, zero failures/errors/skips, explicit GC disabled. The additional starter
case is this priority's documentation contract. Generation-packaging passes again.
Final strict reports/artifacts are under `api-root-final` and
`api-starter-final`; these commands skip tests, so copied test reports in the
initial API directories are not counted as API-lane executions. Optional classpath
controls are covered by the full/focused suites; physical absence on both Boot
rows is the explicitly scoped Priority 7 reuse below.

## Cost Results

Initial matched pair, mean +/- JMH 99.9% error interval, ns/op; allocation is
normalized B/op. Both artifacts use the same 32 benchmark source files and 120
non-starter dependency JAR hashes. Boot 4.0.0 resolves Spring 7.0.1, Reactor Netty
1.3.0, Netty 4.2.7.Final and Jackson 3.0.2. No Maven/test build ran concurrently
with measurement. This is a shared workstation, not an isolated performance lab.

| Workload | 4.4.1 ns/op | Candidate ns/op | 4.4.1 / candidate B/op |
| --- | ---: | ---: | ---: |
| Parsed concrete plan | 1432.14 +/- 56.54 | 2409.59 +/- 1804.47 | 5480.04 / 5480.07 |
| Fresh parse plus plan | 7559.23 +/- 363.16 | 7713.75 +/- 468.61 | 14440.22 / 14488.21 |
| Warm publisher | 589.04 +/- 36.09 | 596.02 +/- 23.81 | 1992.02 / 1992.02 |
| Warm subscribed call | 8125.87 +/- 352.08 | 8288.32 +/- 266.20 | 17376.23 / 17372.23 |

Cold-plan timing triggered the predeclared 20% review: candidate fork means were
3403.02 and 1416.15 ns versus baseline 1424.34 and 1439.94 ns. Allocation did not
trigger review. The first pair is retained, not discarded; confirmation uses the
same built JARs in reverse version order (candidate then baseline), with identical
fork/warmup/measurement settings (`repeat-current`, then `repeat-baseline`).

| Confirmation workload | 4.4.1 ns/op | Candidate ns/op | 4.4.1 / candidate B/op |
| --- | ---: | ---: | ---: |
| Parsed concrete plan | 1442.13 +/- 62.16 | 1509.06 +/- 152.71 | 5480.04 / 5480.04 |
| Fresh parse plus plan | 7848.46 +/- 1235.21 | 7732.02 +/- 383.35 | 14460.22 / 14464.21 |
| Warm publisher | 589.02 +/- 43.08 | 588.93 +/- 31.71 | 1992.02 / 1992.02 |
| Warm subscribed call | 8374.34 +/- 419.36 | 8424.92 +/- 550.15 | 17456.24 / 17440.24 |

Confirmation cold-plan fork means are baseline 1467.48 / 1416.79 ns and candidate
1443.35 / 1574.78 ns: +4.64% mean, overlapping intervals, unchanged allocation.
Other confirmation latency deltas are -1.48%, -0.014% and +0.60%; all allocation
deltas remain below their predeclared thresholds. No repeatable threshold breach
was established. The initial +68.25% result is classified as an unconfirmed,
high-variance timing flag, not removed or attributed to a proven cause. These
short workstation runs cannot exclude smaller regressions or justify a speedup.
No production change was made in response to the measurements.

## Reproduction

Use Java 21 and Maven 3.9.9 from the root; use fresh output directories. The
recorded toolchain is Oracle 21.0.8 on Linux amd64. Choose a writable repository
for ordinary builds; do not install reactor artifacts into the strict baseline
repositories. An empty baseline repository is also valid (no seeding required).

```bash
export MAVEN_OPTS='-Xmx512m -XX:ActiveProcessorCount=2'
MVN=(mvn -B -ntp -s .mvn/maven-central-settings.xml)
for kind in root starter; do
  lane="v33-p8-${kind}-new"
  repo="$PWD/target/published-baseline-repositories/${lane}-4.4.1"
  modules=(reactive-http-client reactive-http-client-starter reactive-http-client-test reactive-http-client-otel)
  selection=()
  if [ "$kind" = starter ]; then
    selection=(-pl reactive-http-client-starter)
    modules=(reactive-http-client-starter)
  fi
  "${MVN[@]}" -Dmaven.repo.local="$repo" "${selection[@]}" -Papi-compatibility -DskipTests verify || break
  bash scripts/verify-published-baseline-provenance.sh "$lane" 4.4.1 "/tmp/$lane" "${modules[@]}" || break
done
"${MVN[@]}" -DargLine=-XX:+DisableExplicitGC verify
bash scripts/verify-generation-packaging.sh 4.5.0-SNAPSHOT
bash scripts/verify-api-compatibility-fixtures.sh
bash scripts/verify-published-baseline-fixtures.sh
"${MVN[@]}" -DskipTests install
```

The focused run selects the 21 classes listed in the
[cross-path reproduction](CROSS-PATH-REGRESSIONS.md#verification-and-reproduction),
plus `ReactiveHttpClientAutoConfigurationTest` and `Boot4AutoConfigurationTest`,
with `-DargLine=-XX:+DisableExplicitGC`. The separate controlled reachability
command is `mvn -pl reactive-http-client-starter -Pv32-cache-reachability test`;
its fresh fork uses Serial GC, explicit GC enabled and a 128 MiB heap. It is not
part of the normal suite's collection requirements.

For each benchmark version, preserve the shaded JAR before the next clean build:

```bash
for version in 4.4.1 4.5.0-SNAPSHOT; do
  out="$PWD/target/v33-cost-new/$version"
  mkdir -p "$out"
  "${MVN[@]}" -f reactive-http-client-benchmarks/pom.xml \
    -Dbenchmark.starter.version="$version" clean package || break
  cp reactive-http-client-benchmarks/target/benchmarks.jar "$out/benchmarks.jar"
  java -Xms512m -Xmx512m -XX:ActiveProcessorCount=2 \
    -Dbenchmark.project.version=4.5.0-SNAPSHOT -Dbenchmark.starter.version="$version" \
    -Dbenchmark.spring-boot.version=4.0.0 -Dbenchmark.api.compatibility.baseline.version=4.4.1 \
    -Dbenchmark.stack.context='V33 same-stack planning' -Dbenchmark.commit="$(git rev-parse HEAD)" \
    -jar "$out/benchmarks.jar" '.*V33PlanningCostBenchmark.*' \
    -bm avgt -tu ns -t 1 -wi 5 -w 1s -i 5 -r 1s -f 2 -prof gc -foe true \
    -jvmArgs '-Xms512m -Xmx512m -XX:ActiveProcessorCount=2' \
    -rf json -rff "$out/jmh.json" || break
done
```

Repeat flagged comparisons with new result paths and the same JARs, not a rebuilt
or different workload. Collect effective POM, dependency tree and classpath with
`help:effective-pom`, `dependency:tree` and `dependency:build-classpath` using the
same `benchmark.starter.version`. Shading leaves some automatic environment
version fields `unknown`; the retained resolved dependency tree and JAR hashes
provide those versions rather than guessing them from the shaded manifest.

## Provenance and Limits

Evidence root: `target/release-evidence/v33/priority8/`. Commands, statuses, logs,
Surefire XML, source commit/patch/new files, JMH JSON/Markdown/environment, both
shaded JARs and dependency/source hashes are retained. `test-totals.json` and
`cost-comparison.json` derive counts and cost comparisons from raw records;
`SHA256SUMS` seals the bundle. Initial `bench-baseline` failed compilation because
the new fixture referenced a package-private helper across packages. The corrected
`bench-baseline-retry1` and `bench-current` both use the same existing Jackson 3
codec; neither a production fix nor a dependency change was needed.
`confirmation-comparison.json` retains the second pair and per-fork means.
`final-artifact-equivalence.json` checks the measured candidate's decompressed
production entries against both final API-tested JARs, excluding only manifests
and Maven descriptors (ZIP timestamps are not code changes). The controlled lane
was repeated as `reachability-final` to preserve its dedicated
`cache-reachability-reports` XML, which the first collection script omitted;
the first passing log remains in `reachability`. Counts are not added together.

| Measured input/report | SHA-256 |
| --- | --- |
| `V33PlanningCostBenchmark.java` | `9ddf2d77335aedba830bfde2973b26c951b3263a9c8bca4e2c00f2ad7a5fee06` |
| Central `4.4.1` starter JAR | `ceca29018e63d800c0caa416e82e0a67975bfdafee52bf83ae37d5ca0c319fd4` |
| Measured candidate starter JAR | `f47671368715f8df0d3c11242734ef1698ddaae4c1e99713e028b5dc30f8cac6` |
| `bench-baseline-retry1/jmh.json` | `1d76fd081edef565cac5b74d8f393df52f627500f6ac90a2176b05dad7e7d605` |
| `bench-current/jmh.json` | `03320520be10842ae390fa8ceef08295e32c7797ecaa08fa997ac7cd0020ad4c` |
| `repeat-baseline/jmh.json` | `f0000d08f015a5fc128eac0ea1a196490481d32352794dc4f2f80d97cd1de710` |
| `repeat-current/jmh.json` | `5ae1c11bdd04cb4ce1535a2ade8cb6eb9be5eaa6d48e738049fda41f50318c17` |

The implementation is reachable commit
`9f5d1e107e19efd63df0ed866dc01192790eca82` plus this benchmark/documentation patch.
Priority 7's [parity evidence](PARITY-EVIDENCE.md) remains prior measured evidence,
not a new native run. `parity-input-reuse.json` records identical Git objects for
production sources, module POMs/settings, both consumers, native fixture and
verification scripts between its measured source and this reachable commit, with
no local modifications to those inputs. This preserves the exact Boot 4.0/4.1,
optional-absence and native claims; no broader startup or performance guarantee
is inferred. No release decision is made here; Priorities 9 and 10 remain open.
See [checklist](CHECKLIST.md).
