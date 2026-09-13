# V31 Compatibility and Targeted Performance Evidence

## Scope and disposition

Priority 9 validates the additive inbound-context surface against published
`4.3.0`; development remains `4.4.0-SNAPSHOT`. No production optimization,
dependency upgrade, context normalization, global lookup index, metric or
release decision is introduced here. V1-V30 evidence remains historical.

**Performance disposition: no public performance claim.** This is an opt-in
correctness/usability API, not a throughput, latency or memory optimization.
Harness smoke and GC-profiler samples establish executable coverage only. A
release-quality comparison is not required to make this scoped disposition;
it is required before making any later performance claim. Optional manual
commands below are not represented as executed release-quality evidence.

## Measured boundaries

- `V31NamedHeaderBenchmark` calls the actual public plural and singleton helpers:
  absent map/name, successful singleton, multi-value lookup and ambiguity rejection.
  Inputs contain 0, 8 or 32 bounded header names. Exception allocation is included
  in the rejection row. These APIs do not exist in `4.3.0`, so these rows are
  current-only, never matched with a substitute implementation.
- `V31ContextSnapshotBenchmark` calls public snapshot capture and restore with
  the same counts. It also captures two independently restored caller envelopes
  on one worker, gates both subscriptions, completes the second first, checks
  isolation and awaits both terminal callbacks. No timed delay proves attachment.
  This measures application snapshot capture, not ingress WebFilter/network cost.
- Shared invocation rows use the existing cache-disabled proxy creation and
  subscription plus unweighted cached proxy creation and subscription, unchanged
  on both artifacts. Local cache hits are not compared with a network request.
- Allocation (`gc.alloc.rate.norm`) is transient bytes per benchmark operation,
  including the fixture's gates, futures and snapshot copies where applicable.
  It is not retained heap, RSS, native-buffer usage or a deployment memory claim.
- Post-terminal reachability is checked separately by the existing controlled
  `v31-handoff-reachability` JVM lane. Normal V31 unit tests and the new harness
  use deterministic cleanup acknowledgements, not forced-GC success. The named helper validates the
  source map and copies only selected values; it does not copy the whole Reactor
  context or retain a global index. Snapshot copying is explicit caller-owned work.

## Optional clean-source comparison

Commit the reviewed harness first; keep the checkout unchanged throughout all
three commands. Use the same idle machine, JDK, CPU limits and Maven settings for
both runs. A previous run must be retained under a different directory rather
than overwritten. Capture `git rev-parse HEAD`, `git status --porcelain`,
`mvn -version`, CPU/memory/container limits and output hashes alongside results.

Current (shared rows plus the new helper):

```bash
test -z "$(git status --porcelain)" && \
test ! -e target/release-evidence/v31/priority9/manual-current && \
mvn -B -ntp -s .mvn/maven-central-settings.xml \
  -Pbenchmarks,benchmark-release -pl reactive-http-client-benchmarks -am verify \
  -DskipTests -Dbenchmark.commit="$(git rev-parse HEAD)" \
  '-Dbenchmark.include=.*(contextV31.*|cacheDisabledProxyInvocation(CreatesPublisher|Subscription)|cacheV29NoNetworkUnweighted(PublisherCreation|Subscription))$' \
  -Dbenchmark.result.dir="$PWD/target/release-evidence/v31/priority9/manual-current"
```

Published baseline (fresh Central-only repository, no reactor `-am`):

```bash
test -z "$(git status --porcelain)" && \
test ! -e target/release-evidence/v31/priority9/manual-baseline && \
test ! -e target/published-baseline-repositories/benchmark-v31-release-4.3.0 && \
mvn -B -ntp -s .mvn/maven-central-settings.xml \
  -Dmaven.repo.local="$PWD/target/published-baseline-repositories/benchmark-v31-release-4.3.0" \
  -Pbenchmarks,benchmark-release,benchmark-published-baseline \
  -pl reactive-http-client-benchmarks clean verify -DskipTests \
  -Dbenchmark.starter.version=4.3.0 -Dbenchmark.commit="$(git rev-parse HEAD)" \
  '-Dbenchmark.include=.*(contextV31(SnapshotCapture|SnapshotRestore|ExplicitHandoff)|cacheDisabledProxyInvocation(CreatesPublisher|Subscription)|cacheV29NoNetworkUnweighted(PublisherCreation|Subscription))$' \
  -Dbenchmark.result.dir="$PWD/target/release-evidence/v31/priority9/manual-baseline" && \
scripts/verify-published-baseline-provenance.sh benchmark-v31-release 4.3.0 \
  target/release-evidence/v31/priority9/manual-baseline/provenance \
  reactive-http-client-starter
```

Comparison (clean the benchmark module only to remove published-profile output):

```bash
test -z "$(git status --porcelain)" && \
mvn -B -ntp -f reactive-http-client-benchmarks/pom.xml clean && \
mvn -B -ntp -s .mvn/maven-central-settings.xml \
  -Pbenchmarks,benchmark-compare -pl reactive-http-client-benchmarks -am verify -DskipTests \
  -Dbenchmark.compare.current="$PWD/target/release-evidence/v31/priority9/manual-current/release-jmh.json" \
  -Dbenchmark.compare.baseline="$PWD/target/release-evidence/v31/priority9/manual-baseline/release-jmh.json" \
  -Dbenchmark.compare.output="$PWD/target/release-evidence/v31/priority9/manual-comparison.md"
```

The expected selection is 50 current and 26 baseline benchmark/mode/parameter
rows: 26 matched, 24 candidate-only, no baseline-only. Both release-profile runs
use two forks, five warmups, five measurements and GC profiling. Verify every
selected row and profiler result exists, review confidence intervals and
unmatched rows, and retain environment sidecars, JAR/report hashes and Central
markers. Neither a green comparator nor a smoke score certifies a public claim.

## Verification record

Evidence is under `target/release-evidence/v31/priority9/`, based on reachable
commit `b1cb3c0760c0f0ef584b9a64e7b5f2314059e1de` plus the uncommitted benchmark,
test and documentation changes. This is not clean final-release evidence.
`commands.txt`, the matrix's per-row commands and `benchmark-final-commands.txt`
retain exact commands, with separate logs and exit statuses.

| Check | Verified result |
|---|---|
| Java 21.0.8 / Boot 4.0.0 full matrix | 1,736 starter + 78 mock + 62 OTel + 3 assembled consumer = 1,879; no failures/errors/skips |
| Java 21.0.8 / Boot 4.1.0 full matrix | Same 1,879 cases; no failures/errors/skips |
| Root strict API, both matrix rows | Fresh independent Central `4.3.0` repositories; source and binary failure gates enabled |
| Separate starter strict API | Fresh `v31-priority9-api-starter-4.3.0` repository; passed |
| Negative API / provenance fixtures | Additive APIs accepted; source-only exceptions, constructor/nested-method/enum removal, self-comparison, contamination, missing attachments and inconsistent versions rejected |
| Current assembled consumers | 74 mock + 11 full consumer + 3 minimal consumer; no failures/errors/skips, no reactor-classpath leakage |
| Published assembled consumer | 4 passed from a fresh Central-only `4.3.0` repository |
| Final focused context/docs/AOT regression | 202 passed with explicit GC disabled; includes 27 AOT cases |
| Controlled post-terminal reachability | 5 passed in the profile's isolated Serial GC, 128 MiB JVM |
| Generation packaging | Binary, source and Javadoc artifact guards passed for all three modules |
| Complete benchmark harness | 34 passed; focused new-harness/report rerun previously passed 27 |
| Published-compatible benchmark harness | 24 passed with only the named-helper source/test excluded |
| GC-profiled JMH smoke | 50 current / 26 baseline rows, 26 matched, 24 helper-only, no baseline-only; every row has finite allocation data |
| Existing support sanitizer | 9 Python cases passed |

The strict diff contains only the two additive `RequestContext` methods;
mock-helper and OTel public APIs have no changes. The matrix records exact
effective POMs/dependency trees: Boot 4.0.0 uses Spring 7.0.1 / Reactor Netty
1.3.0; Boot 4.1.0 uses Spring 7.0.8 / Reactor Netty 1.3.6. Resilience4j remains
2.4.0. Remaining checks use Maven 3.9.9 / GraalVM JDK 25.0.3, Java 21 target.

The first matrix attempt used an incomplete Java 21 installation without
`javac`/`ct.sym`: two documentation-compilation cases failed. Its 1,736-case
starter report is preserved in `matrix-incomplete-jdk/`; the complete Oracle
JDK rerun is separate in `matrix/`. Harness compile failures (missing import
and a package typo), a temporary runner interruption and the non-executable
packaging-script invocation are retained separately, not counted as passes.
An initial 175-case regression selector did not match the AOT class; the final
202-case command uses `ReactiveHttpClientAotSmokeTest` explicitly.

Priority 7 native evidence remains at clean commit
`f9b94fd207e6c5af1fc36ee047fd2c491a6c0e30`, not this dirty benchmark revision.
Its executable SHA-256 remains
`c074e8f5bc3a340f9a8d982136fcc5b752a74c1bf9952c7156e26bd9be1fa5ed`.
Production and native-smoke sources have no diff from that revision; the
202-case regression rechecks AOT without relabeling the earlier native binary.
Prior target artifacts were preserved in `target/v31-priority9-previous-target/`
before matrix reactor cleans; V31 Priority 1-8 evidence was restored to its
original evidence paths.

## Smoke review

Final smoke uses one fork, one 250 ms warmup and one 250 ms measurement for
throughput and average-time modes. `audit.json` validates the complete
method/mode/header-count cross product, finite allocation measurements and
actual XML test totals; the validator is retained beside it. Current and fresh
Central baseline use the same harness and managed Boot 4.0.0 stack.
Direct-JAR smoke sidecars mark `dirty-smoke` / `smokeOnly=true`; some dependency
version fields remain unknown because the Maven release-profile metadata inputs
were not supplied. No sidecar is promoted as clean release evidence.

The comparator flags several shared latency and allocation rows for review.
Single-iteration scores cannot establish stable confidence intervals, and
some allocation deltas change direction between throughput and average-time
runs. Those flags remain exploratory, not an accepted speed/overhead claim or
a confirmed regression. The helper-only rows measure lookup and exception
allocation, while explicit snapshot/handoff rows include the copying and
synchronization they actually perform. Post-terminal retention is a separate
five-probe result, not an inference from allocation counters.

The initial current smoke completed but warned about Reactor's graceful-disposal
awaiter keeping JMH fork threads alive; it is retained as
`smoke-current-graceful-disposal/` and is excluded from final comparison.
The harness now owns its executor, awaits termination directly and asserts that
state in tests. Both final smoke logs have no stray-thread warning. Interrupted
baseline resolution was retained in a separate repository and the final
baseline began from a previously absent repository. Report/JAR/source hashes,
remote markers, exact commands and the final dirty-source record are retained
alongside the audit. This completes the scoped no-public-claim disposition;
Priority 10 still owns clean release evidence and any release decision.
