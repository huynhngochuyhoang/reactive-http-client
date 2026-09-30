# V34 Equivalent Workloads and Measurement Rules

> **Recorded:** 2026-09-30, before scored measurement
> **Comparison:** published `4.4.2` versus current `4.5.0-SNAPSHOT`
> **Implementation and release scope:** unselected
> **Status:** workload contract; no scored performance conclusions

This implements Priority 2 of the [checklist](CHECKLIST.md), using the
[effective profiles](BASELINE-SCOPE.md#effective-profiles). Priority 3 measures
the unchanged implementation. Priority 4.3 must approve findings before any
production optimization. A setup/discovery/smoke pass is not a latency result,
allocation improvement, retention claim or release decision.

## Harness and Phases

Keep the existing JMH module, runner, JSON/environment output, Markdown report,
fairness checks and report comparator. Do not introduce a separate timing engine.
The additional fixtures fill the missing auto-configuration/phase/body cases;
they do not replace the existing planning and internal-resolution workloads.

| Fixture and exact method selection | Measured operation | Setup and interpretation |
|---|---|---|
| `V33PlanningCostBenchmark.metadataColdConcretePlan` | Concrete plan from already parsed metadata | Reflection and annotation parse outside timing; not cold JVM startup |
| `V33PlanningCostBenchmark.metadataColdParsingAndPlan` | Fresh cache, annotation parse and concrete plan | Method discovery outside timing |
| `StarterInvocationInternalsBenchmark.*` (four methods) | Cached metadata/plan lookup or path/query/header resolution | Shared parsed metadata/plan; helper attribution only |
| `V34ConstructionBenchmark.defaultV34ConstructionContextAndProxy` | Fresh Spring context refresh, auto-configuration and factory proxy creation | Close outside latency timing; no HTTP subscription or socket |
| `V34ConstructionBenchmark.defaultV34ConstructionFirstPublisher` | First publisher assembly on a newly constructed proxy | Fresh context/proxy in invocation setup; zero dispatch |
| `V34ConstructionBenchmark.defaultV34ConstructionFirstCall` | First assembly plus no-network subscription, decode and terminal processing on a fresh proxy | Fresh context/proxy in invocation setup; one exchange |
| `V34DefaultPathBenchmark.defaultV34NoNetworkWarmPublisher` | Warm proxy invocation returning a cold Mono | No subscription, dispatch, decode or terminal event; return value consumed by JMH |
| `V34DefaultPathBenchmark.defaultV34NoNetworkWarmSubscription` | Subscribe/block an already assembled ordinary cold Mono | Publisher assembly outside timing; a new exchange and subscription-local state per operation |
| `V34DefaultPathBenchmark.defaultV34LoopbackCall` | Warm assembly, subscription, TCP loopback, body writing/reading, decode and terminal processing | Actual starter factory for AUTO profiles; server/pool/context setup and two correctness subscriptions outside timing |

V33's two warm proxy methods remain available as historical combined-work
attribution, but are not substituted for V34's separately timed warm subscription.
Construction rows are warm-JVM, fresh-context/proxy measurements: class loading,
JIT and static framework caches can already be warm. They are not process startup.
**Invocation-level setup and teardown allocations remain in GC-profiler totals.**
Construction/first-call B/op is therefore the entire fixture lifecycle per operation,
not isolated first-call allocation. Report that scope explicitly; do not subtract
different lifecycle row means to manufacture an isolated allocation estimate.

The frozen primary matrix has 60 benchmark/parameter rows: six existing
planning/resolution rows, 24 warm rows, 24 loopback rows and six construction rows.
`verify-v34-benchmark-inputs.py results` checks all 54 V34 parameter combinations;
the existing runner checks discovery/completion of the six unparameterized rows.
Retain modes and parameters as part of row identity, never collapse profiles.

## Effective Workloads

| Parameter | Profile mapping and effective behavior |
|---|---|
| `MINIMAL` | V34-P01: hand-created proxy, empty context, no logger/observer/hook/auth/cache/operator/deadline. Dedicated application-owned pool for loopback, otherwise synthetic exchange. Not a Spring default |
| `AUTO_NO_REGISTRY` | V34-P02: actual `ReactiveHttpClientAutoConfiguration`, Boot Jackson auto-configuration, environment-bound URL, starter prototype builder and `ReactiveHttpClientFactoryBean`. Observability defaults true but no registry/observer bean |
| `AUTO_REGISTRY` | V34-P03: same context plus context-owned SimpleMeterRegistry; built-in Micrometer observer selected by auto-configuration, real `v34.*` API timer verified |
| `RESILIENCE_ENABLED_ONLY` | V34-P04: auto-configured no-registry profile with only resilience enabled; four operator names remain null, no registry selected, stateful execution retained |
| `OBSERVER`, `HOOK` | V34-P06: auto-configured, one application observer or lifecycle hook, no registry, built-in exports explicitly disabled; terminal callbacks remain required |

Warm rows cross all six profiles with GET/TARGET. Loopback crosses MINIMAL,
AUTO_NO_REGISTRY and AUTO_REGISTRY with all eight scenarios below. Construction
crosses its three methods with AUTO_NO_REGISTRY and AUTO_REGISTRY.

This is outbound auto-configuration, not a running inbound WebFlux server or a
full service startup benchmark. The context is non-web, so inbound WebFilters are
not registered; the factory's outbound filters remain intact. No synthetic inbound
context is installed. No-network AUTO rows use one explicit customizer replacing
only the exchange function; HTTP encoding/transport cost belongs to loopback rows.
Loopback AUTO rows do not replace the builder, connector or factory pool.
Optional classes are present but cache/auth/operators are unselected. Java 21,
Boot 4.0.0, the defaults in Priority 1, JSON codec, connect/read/write timeouts,
200-entry factory connection pool, redirects/compression/HTTP2 disabled, pool
metrics disabled and no logical deadline remain unchanged. The fixture's 10-second
blocking bound is a harness safety limit, not a client timeout override.

| Scenario | Synthetic request | Response and witness |
|---|---|---|
| GET | GET `/get`, no body | 200 text, `value` (5 UTF-8 bytes) |
| TARGET | GET `/items/a%2Fb?view=summary`, `X-Scope: scope`, no body | Same 5-byte text; encoded slash, query and header checked |
| STRING | POST `/string`, text/plain;charset=UTF-8, `fixture` (7 bytes) | Same 5-byte text |
| JSON | POST `/json`, application/json, `{"value":"fixture"}` (19 bytes) | Same 19-byte JSON, decoded immutable Payload |
| ENTITY | GET `/entity`, no body | ResponseEntity: 200, `X-Fixture: v34`, 5-byte text |
| EMPTY | GET `/empty`, no body | 204, zero bytes, empty completion |
| ERROR4 | GET `/error4`, no body | 404, `missing` (7 bytes), HttpClientException with status/body |
| ERROR5 | GET `/error5`, no body | 503, `unavailable` (11 bytes), RemoteServiceException with status/body |

Targets and payloads here are synthetic fixture constants, never captured user data.
All ordinary requests are unselected from caching: one dispatch per subscription,
including empty/error outcomes. No ignored failure may count as successful work.

## Retained Controls

- V34-P05 physical optional absence stays an assembled-consumer correctness lane
  from Priority 1, not a claim made by a dependency-rich JMH JVM. Run that lane
  again when affected production inputs change. No absent-classpath timing is
  selected until a concrete finding requires it.
- V34-P07 independent pool meters remain a correctness sentinel. Registry presence
  in AUTO_REGISTRY does not enable pool gauges. Do not relabel request metrics as
  pool telemetry or alter pool activation merely to lower benchmark cost.
- V34-P08 selected cache/admission/coalescing/refresh paths reuse
  `V30CacheWorkPerformanceBenchmarkTest.rowsExerciseProductionCallerSourceAndMetricPaths`
  and the V28/V29 witnesses. They run through real proxies with real API names,
  caller/load states, metrics-on/off assertions and explicitly gated attachment.
  No new timed concurrent workload is selected by this priority. If selected later,
  preserve those gates; sleeps cannot establish a joined waiter.
- `StarterDiagnosticsOverheadBenchmark` retains metadata logging, one/multiple
  observers/hooks, Micrometer/Prometheus/histogram and open-circuit attribution
  rows. The new registry/application-consumer rows establish actual enabled
  behavior rather than relying on presence alone. Expand the scored selection
  before measurement if an accepted finding touches these extra features.
- `LoopbackClientComparisonBenchmark`, `BenchmarkClients`,
  `LoopbackBenchmarkServer` and `BenchmarkFairnessContract` retain optional raw
  WebClient/Spring comparisons. Such rows must keep identical transport/codec/
  status/payload/body-consumption settings and explicitly account for the different
  exception APIs. They are not the primary release-to-release decision input;
  helper/local-hit rows cannot be compared with a network call.

## Correctness Before Timing

`V34WorkloadContractTest` covers all 12 warm and 24 loopback parameter combinations,
construction/first-call separation, negative witness checks, and Flux completion
and cancellation. Setup verifies exact method, target, selected headers and
decoded result; loopback additionally reads and verifies the actual request bytes
and media type. A single cold publisher is subscribed twice to prove zero work at
creation and independent ordinary work on each subscription. Synthetic exchanges
do not claim to have serialized a request body.

Every timed subscription increments an expected-work count, including errors;
teardown reconciles it against exchanges and, where selected, real terminal
callbacks or `reactive.http.client.requests{client.name="v34-workload",api.name="v34.*"}`.
There is no `unknown` API fallback in a metered row. Request/result inspection is
outside timing where possible; the counters, blocking boundary, synthetic response
construction and real selected terminal recording remain in the operation on both
sides. They are not subtracted as hypothetical zero-cost harness work.

Flux controls wait for explicit source attachment before emitting a pooled buffer,
release it, then wait for completion/cancellation acknowledgement. Reference count
zero is checked directly, not by forced GC. The shared-load sentinel separately
holds its response until both callers are attached and verifies one dispatch.
Neither control derives correctness from elapsed time or a short network delay.

Fixture close destroys the factory or explicit minimal pool, closes context and
registry, and disposes server plus its dedicated workers; assertions inspect actual
disposed resources. Reactor's shared client workers/DNS infrastructure are
JVM-owned and intentionally not destroyed between independent fixtures. JMH fork
exit releases those shared workers. Closing one fixture must not invalidate another.
Construction/lookup correctness never relies on collection timing.

An older V30 benchmark reachability method did require `System.gc()` in the normal
suite. It is now tagged `cache-reachability`, excluded from ordinary runs and
preserved in the separate `v34-benchmark-reachability` profile: fresh fork, Serial
GC, explicit GC enabled, 128 MiB heap. The starter's existing controlled reachability
lane remains unchanged. Ordinary workload tests run with `-XX:+DisableExplicitGC`.

## Frozen Measurement Rules

These apply to every primary row, including each parameter combination, before
Priority 3 runs any scored sample:

- JMH 1.37, same Java 21 distribution/build, Maven/settings and Boot-managed stack
  on both sides. Average time (`avgt`), ns/op, one caller thread, two forks, five
  one-second warmups and five one-second measurement iterations per fork, `-prof gc`,
  `-foe true`. Keep throughput/p95 experiments separate; they cannot replace avgt.
- Fixed 512 MiB initial/maximum heap, default Java 21 G1 collector,
  `-XX:ActiveProcessorCount=2`; same flags in launcher and fork. SLF4J simple logger
  WARN on both sides, no exchange logging selected. No explicit collection in
  scored methods. Server has one dedicated worker; client workers are unchanged
  Reactor defaults under the same processor limit. Record actual topology.
- Record `java -version`, `mvn -version`, `uname -a`, `lscpu`, memory/swap, affinity
  and `/proc/self/cgroup`; capture applicable cgroup CPU/memory/cpuset limits and
  throttling counters before/after each pair. ActiveProcessorCount is not CPU
  affinity or a kernel quota. Do not run Maven/tests/native builds concurrently
  with scored measurements. A shared workstation is not an isolated lab.
- Preserve payload sizes above, runtime/test dependency trees, effective POMs,
  classpaths, all source/JAR hashes, source commit and clean status. No version,
  transport, codec, pool, payload, profile or fixture change between a pair.
- Review mean latency growth **above 20%**, or B/op growth **above max(32 B/op, 5%)**
  relative to baseline. These trigger investigation, not an allowed regression
  budget or a speedup promise. Lifecycle B/op uses its broader scope above.
- Keep per-fork/per-iteration samples, JMH intervals, `gc.alloc.rate.norm`, logs,
  JSON, report/environment sidecar, ordering and shaded artifacts before the next
  module build. The runner's name-only completion guard is supplemented by the
  V34 parameter/allocation validator. A missing row/sample is not zero or a pass.
- Run published then current first. Recheck every flagged result using the same shaded
  JARs/settings in reversed order, current then published, with new output paths.
  Preserve both pairs even if the flag disappears. Overlapping intervals, unstable
  fork means, CPU pressure or missing data limit conclusions; unresolved flags
  remain open. Do not pick the best attempt or change thresholds after seeing scores.
- Stop on semantic/cleanup failures, version/provenance mismatch, missing metrics,
  incomplete rows, OOM, transfer/build failure or interference. Keep failed evidence.
  Separate CPU/allocation profiling from scored timings. Allocation rate, retained
  heap, direct/native memory and RSS are different domains; no pod-memory or leak
  conclusion follows from B/op. Timing assertions stay out of unit tests.

## Reproduction

From the root with Java 21/Maven 3.9.9, select fresh output/repository paths. Never
root-clean retained evidence or install into the published repository. This exact
build also runs ordinary benchmark-module correctness tests, not scored JMH:

```bash
set -euo pipefail
export MAVEN_OPTS='-Xmx512m -XX:ActiveProcessorCount=2'
MVN=(mvn -B -ntp -s .mvn/maven-central-settings.xml)
base="$PWD/target/release-evidence/v34/priority2-new"
baselineRepo="$PWD/target/published-baseline-repositories/benchmark-v34-p2-new-4.4.2"
test ! -e "$base"
test ! -e "$baselineRepo"
mkdir -p "$base"
"${MVN[@]}" -pl reactive-http-client-starter -am -DskipTests install
for version in 4.4.2 4.5.0-SNAPSHOT; do
  out="$base/$version"
  mkdir "$out"
  repository=()
  if [ "$version" = 4.4.2 ]; then repository=(-Dmaven.repo.local="$baselineRepo"); fi
  selected=("${MVN[@]}" "${repository[@]}" -f reactive-http-client-benchmarks/pom.xml
    -Dbenchmark.starter.version="$version")
  "${selected[@]}" clean package -DargLine='-XX:+DisableExplicitGC -XX:ActiveProcessorCount=2' || break
  cp reactive-http-client-benchmarks/target/benchmarks.jar "$out/benchmarks.jar"
  cp -r reactive-http-client-benchmarks/target/surefire-reports "$out/reports"
  "${selected[@]}" dependency:build-classpath -Dmdep.outputFile="$out/classpath.txt" || break
  "${selected[@]}" dependency:tree -DoutputFile="$out/dependency-tree.txt" || break
  "${selected[@]}" help:effective-pom -Doutput="$out/effective-pom.xml" || break
  published=()
  if [ "$version" = 4.4.2 ]; then published=(--published); fi
  python3 scripts/verify-v34-benchmark-inputs.py record "$out" "$version" "${published[@]}" || break
done
bash scripts/verify-published-baseline-provenance.sh benchmark-v34-p2-new 4.4.2 \
  "$base/provenance" reactive-http-client-starter
python3 scripts/verify-v34-benchmark-inputs.py compare \
  "$base/4.4.2/inputs.json" "$base/4.5.0-SNAPSHOT/inputs.json"
python3 -m unittest discover -s scripts -p test_verify_v34_benchmark_inputs.py -v
"${MVN[@]}" -f reactive-http-client-benchmarks/pom.xml -Pv34-benchmark-reachability test
```

The input guard compares all benchmark sources/POMs and non-starter JAR hashes,
requires the expected starter version and Central markers, rejects class directories
or extra project artifacts, and checks every shaded starter class against the
resolved JAR. Keep the independent provenance guard to reject mixed baseline
repositories. Only the starter artifact is an expected dependency difference.

After committing the final harness, with no build running, Priority 3 can use the
saved JARs and the following settings. Record environment/provenance described
above first; this command alone does not establish a clean, matched experiment:

```bash
selection='.*(V34DefaultPathBenchmark|V34ConstructionBenchmark|V33PlanningCostBenchmark.metadataCold|StarterInvocationInternalsBenchmark).*'
java -Xms512m -Xmx512m -XX:ActiveProcessorCount=2 \
  -Dorg.slf4j.simpleLogger.defaultLogLevel=warn \
  -Dbenchmark.project.version=4.5.0-SNAPSHOT -Dbenchmark.starter.version="$version" \
  -Dbenchmark.api.compatibility.baseline.version=4.4.2 -Dbenchmark.spring-boot.version=4.0.0 \
  -Dbenchmark.commit="$(git rev-parse HEAD)" -Dbenchmark.stack.context='V34 matched default path' \
  -jar "$out/benchmarks.jar" "$selection" \
  -bm avgt -tu ns -t 1 -wi 5 -w 1s -i 5 -r 1s -f 2 -prof gc -foe true \
  -jvmArgs '-Xms512m -Xmx512m -XX:ActiveProcessorCount=2 -Dorg.slf4j.simpleLogger.defaultLogLevel=warn' \
  -rf json -rff "$out/scored-jmh.json"
```

Run the two V34 fixtures separately from the helper selection when using the
`results` validator, or extract only their rows into a derived file while preserving
the complete original JSON. Never alter raw results. Discovery is `java -jar
benchmarks.jar '.*V34.*' -l`. Smoke uses one fork, no warmup, one 50 ms iteration,
256/512 MiB heap and `--smoke` validation. Smoke scores/intervals are explicitly
not promoted to release-quality evidence.

## Verification Record

Evidence lives under `target/release-evidence/v34/priority2/`, with the source
commit, working patch, exact commands/statuses, reports, source/dependency hashes,
effective POMs, both shaded JARs and Central provenance. The working-tree fixtures
are correctness/smoke evidence; clean-source scored evidence remains Priority 3.
The initial full run is retained: an older V30 GC-dependent case failed with
explicit GC disabled, and a V34 shared-worker teardown caused a later executor
failure. The controlled lane and fixture-owned cleanup changes address these
test-harness issues without changing starter runtime behavior.

Reviewed source is reachable commit
`48e4e63888b1e64d6aebd63198c5db640a0d83b6` plus this benchmark/test/documentation
patch. Both final builds (`current-verified`, `baseline-verified`) pass **75 tests
in ten classes**, zero failures/errors/skips. This includes 40 V34 contract cases
and the added internal-resolution witness. The separate reachability lane passes
**two cases against each artifact**, with controlled GC. The input/result guard
passes **eight Python tests**, including mismatched harness/dependencies, local
baseline, reactor classes, stale shading, missing parameter rows, absent allocation
and smoke-promotion rejection. These overlapping version runs are not added into
a claim of distinct behaviors.

The final pair has **39 identical benchmark source/POM files** and **120 identical
non-starter dependency JARs**. Default Boot 4.0.0 resolves WebFlux 7.0.1, Reactor
Netty 1.3.0, Netty 4.2.7.Final, Jackson 3.0.2 and Micrometer 1.16.0. No dependency
version was changed. The isolated repository
`target/published-baseline-repositories/benchmark-v34-p2-4.4.2` was created without
project artifacts; only third-party downloads were seeded. Central markers,
embedded version, published starter hash and shaded class identity pass the
existing provenance and new input guards. Candidate installation used a different
repository. No reactor classes are substituted into the baseline.

Both saved smoke builds execute **54 parameterized rows each**, with a real
allocation sample for every row. The later internal-resolution test addition
changes no benchmark or starter class: `verification.json` verifies every class
entry plus generated JMH BenchmarkList/CompilerHints against the final shaded
builds. Thus these are exact executable-input smoke reuse, not a claim that the
ZIP hashes are identical. Ordinary tests were rerun after that test addition.
The initial 57-case focused run and intermediate 74-case builds overlap the final
suite; all logs, including the initial failed full run, remain available.

| Final input | SHA-256 |
|---|---|
| Central `4.4.2` starter | `fb8646ce2f6ed172598ff446cdfa9da2f348c0fd56a2b4c9a02e7ac1dae803c1` |
| Current starter | `b95716a50f2818e22d01af3d213fe70a4794b62348cd097f55c7ecb71a436d89` |
| `baseline-verified/benchmarks.jar` | `d131c8ccd6da3d77bfb480eab123a4eb9fd7dc3743f0c5b5c40c4877b9facba3` |
| `current-verified/benchmarks.jar` | `d7bcece44fc215be76c28b048c72e16513ea0123de98eda88ca2ea3c2ee06f60` |
| `baseline-verified/inputs.json` | `e2af31ae7656af9902e6d9c67651c213961e6005ced8762a73efb6899105e94c` |
| `current-verified/inputs.json` | `9ffd1776676a04362e30155a87c5698ef13986112346255cceec6edf53d730e9` |

The final documentation/archive/readiness guard passes **81 cases**, zero
failures/errors/skips; whitespace checks pass. The
bundle inventory preserves report hashes and exact source patch. No scored
multi-fork pair, fresh upper-Boot consumer/native run, production optimization,
release or performance improvement is claimed. Priority 3 characterization and
Priority 4.3 scope approval remain open.
