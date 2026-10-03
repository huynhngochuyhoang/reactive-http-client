# V34 Matched Performance and Compatibility Evidence

> **Status:** 10.2 and 10.3 complete; 10.1 performance acceptance pending, 2026-10-03
> **Delivered production IDs:** none; C004 rolled back in Priority 6
> **Release scope:** unselected

This applies [Priority 10](CHECKLIST.md) and the frozen
[workload rules](WORKLOAD-CONTRACT.md) to clean starting commit
`44d6ffaede52a5be92148cc078f287428791d511`. Both benchmark JARs were built
before local documentation changes. Later edits are evidence and documentation
guards, not production, benchmark, dependency, fixture or coordinate changes.
The [P3 allocation flag](COST-OWNERSHIP.md#confirmed-and-unresolved-flags) and
[failed C004 benefit gate](HARDENING-EVIDENCE.md) remain historical observations;
neither is erased by selecting a favorable later sample.

## Matched Inputs and Measurement

The current reactor and Central `4.4.2` use the same committed JMH harness.
Both builds pass 75 ordinary benchmark correctness tests with explicit GC
disabled, including default-path, loopback, response-body and selected-feature
sentinels. Input verification matches 39 source/POM files and 120 non-starter
JARs; shaded starter classes match each resolved dependency. The published
repository is newly created under
`target/published-baseline-repositories/benchmark-v34-p10-4.4.2`, seeded with
third-party dependencies only. No project artifact was copied into it or installed
there. Central provenance and embedded-version guards run separately.

Oracle Java 21.0.8, Maven 3.9.9 and Boot 4.0.0 remain fixed for both scored
artifacts. The comparison is not between different Boot versions. JMH 1.37 uses
average time in ns/op, one caller thread, two forks, five one-second warmups and
five one-second measurements, GC allocation profiling and fail-on-error. Launcher
and fork have a 512 MiB fixed heap, ActiveProcessorCount=2 and WARN logging.

The primary sequence is published then current, all 60 frozen rows per artifact:
54 V34 profile/scenario combinations and six planning/resolution helpers. No
Maven, native build, correctness test or separate profiler runs concurrently with
scoring. Before/after toolchain, CPU, affinity, cgroup limits/counters, pressure
and memory snapshots are retained. This shared workstation is not an isolated lab;
boundary snapshots cannot exclude intermediate contention.

The confirmation selection is the union of every newly flagged primary row and
the earlier enabled-only GET publisher row, even when the latter does not cross
the new trigger. Each is rerun once, current before published, using the same saved
JARs and frozen settings. Mean latency above 20%, or B/op growth above
max(32 B/op, 5%), triggers review; these are not regression allowances. Raw JSON,
ten measurement samples per row, intervals and unsuccessful attempts are retained.

### Results and Flag Disposition

The primary pair completed all 60 rows with **zero latency flags and zero
allocation flags**. Reverse confirmation completed the historical enabled-only
GET publisher row with **one allocation flag**, zero latency flags. No sample,
semantic witness or selected row is missing. Passing the input/completion checker
is not passing performance acceptance.

Representative primary GET rows follow. Values are mean +/- JMH reported error;
raw iterations, fork means, intervals and all 60 rows remain in
`review/primary/` and the two `scored-*` directories. These are internal review
observations, not promoted release numbers or causal savings.

| Phase / profile | Baseline ns/op | Current ns/op | Baseline B/op | Current B/op |
|---|---:|---:|---:|---:|
| Warm publisher / AUTO_NO_REGISTRY | 331.35 +/- 27.36 | 304.08 +/- 5.11 | 960.01 +/- 203.99 | 832.01 +/- 0.03 |
| Warm publisher / AUTO_REGISTRY | 466.65 +/- 23.49 | 473.38 +/- 30.34 | 1744.02 +/- 0.05 | 1744.02 +/- 0.06 |
| Warm subscription / AUTO_NO_REGISTRY | 5351.24 +/- 219.97 | 5572.90 +/- 138.09 | 13716.22 +/- 146.63 | 13696.23 +/- 38.25 |
| Warm subscription / AUTO_REGISTRY | 8564.23 +/- 603.14 | 8703.39 +/- 208.08 | 18116.36 +/- 133.88 | 17956.37 +/- 70.12 |
| Loopback / AUTO_NO_REGISTRY | 110967.94 +/- 38232.78 | 123839.15 +/- 48141.28 | 29638.69 +/- 714.07 | 29981.48 +/- 616.21 |
| Loopback / AUTO_REGISTRY | 163001.09 +/- 47437.90 | 176781.82 +/- 32012.63 | 37252.61 +/- 815.60 | 37100.07 +/- 531.13 |

Construction/first-call and six helper rows also have no primary review flags;
their broader allocation scopes remain distinct. The selected-feature cache/work,
pool, auth and resilience sentinels are correctness controls, not newly timed
workloads. No additional scored selection was introduced after observing results.

#### Unresolved Enabled-Only Allocation

`defaultV34NoNetworkWarmPublisher`, `profile=RESILIENCE_ENABLED_ONLY`,
`scenario=GET`:

| Pair / artifact | Mean ns/op +/- error | Mean B/op +/- error | Allocation fork means B/op |
|---|---:|---:|---|
| Primary / baseline | 370.30 +/- 14.50 | 1136.016 +/- 0.041 | 1136.016, 1136.015 |
| Primary / current | 371.14 +/- 11.02 | 1136.016 +/- 0.041 | 1136.016, 1136.016 |
| Reverse / current | 375.02 +/- 48.80 | 1264.016 +/- 203.988 | **1392.018, 1136.015** |
| Reverse / baseline | 356.39 +/- 17.73 | 1136.015 +/- 0.040 | 1136.015, 1136.015 |

The reverse pair's current mean is about 128 B/op (11.27%) higher, exceeding
the frozen max(32 B/op, 5%) review trigger. Its two current forks reproduce the
1136/1392 split seen in P3; the favorable primary pair does not clear it. The wide
current interval and unstable forks limit interpretation, not evidence retention.
The 310 starter classes are byte-identical between these resolved artifacts and
all 120 non-starter JARs match. Those checks exclude a retained production-code or
dependency change in this pair, but do not explain the allocation split. JIT or
escape-analysis variation is a hypothesis, not an established cause.

The enabled-only allocation flag remains unresolved. No production optimization
survives the C004 rollback, so there is no optimization benefit or correctness
tradeoff to approve here. **10.1 remains pending** at its acceptance item; 10.2
and 10.3 record completed compatibility and evidence work, not an all-clear cost
gate. Follow-up must explain this row with bounded, separately recorded profiling
or obtain explicit scope disposition before acceptance. Do not rerun until a
favorable pair appears or silently waive the frozen trigger. P12 release/no-release
selection is still required; this record makes neither decision.

## Compatibility and Ownership

No production optimization remains to justify with a measured benefit. Defaults,
explicit activation, public API and supported extension contracts are unchanged
from `4.4.2`. Required discovery, mutation validation, body cleanup, context
isolation and per-subscription terminal state were not removed to reduce cost.
The scope still excludes C001-C003, C005 and broader C004 changes.

Strict root and independent starter comparisons use separate fresh repositories
seeded only with third-party dependencies. Both source- and binary-incompatibility
failure switches remain enabled, missing classes are not broadly ignored, and
each command has its own reports, exit and Central provenance. The starter lane
runs even if the root lane fails. A source/API pass does not prove behavioral
compatibility by itself.

### Fresh Verification

| Lane | Actual result |
|---|---|
| Strict root API / Central provenance | exit 0 / 0; starter, test-helper and OTel reports each say `No changes.` |
| Independent strict starter API / Central provenance | exit 0 / 0; separate fresh repository and report, `No changes.` |
| Complete modules, Boot 4.0.0 | starter 2,152; test-helper 80; OTel 62; **2,294 passed** |
| Complete modules, Boot 4.1.0 | starter 2,152; test-helper 80; OTel 62; **2,294 passed** |
| Matched benchmark correctness builds | 75 passed per artifact, 150 executions, not 150 distinct cases |
| Generation/packaging | exit 0 on both Boot rows; binary, sources, Javadoc, generated references/metadata retained |
| Controlled cache / handoff / benchmark reachability | 16 / 5 / 2 passed, separate JVM profiles |
| API negative-fixture guard / published-baseline guard | exit 0 / 0; expected incompatibility, self-baseline and provenance failures retained |
| V34 input/reviewer / native-runner Python tests | 14 / 5 passed |
| Final documentation guard | 89 passed on each Boot row after this record/checklist and the new guard were added |

All test rows have zero failures, errors and skips. The full-module totals precede
the new documentation test (88 documentation cases then, 89 now); the final
documentation reruns are separate, overlapping executions, not another full-suite
run or additions to the 2,294 count. Only evidence, navigation and that guard
changed after full verification. Production, dependencies, module packaging and
benchmark inputs did not change; those results are reused exactly, not claimed
as new runs after the final documentation edit.

Ordinary runs disable explicit GC. Cache, async-handoff and benchmark reachability
use their existing separate small-heap Serial-GC profiles; they do not change
ordinary-suite assumptions. Full suites retain P5-P9 controls for metadata and
fresh public views, mutation checks, builder ownership, auth/cache/retry semantics,
body release, cancellation, context isolation, terminal recording, meter leases,
framework binding and application-owned resources. The P9 entry-point matrix
below supplies the assembled/physical-absence coverage; japicmp alone does not.

### Cross-Path Reuse

[Priority 9](PARITY-EVIDENCE.md) remains the exact fresh assembled/native record
for commit `fc98e58b9d5154a0ba539ea878b05c532b379554`. The current starting
commit adds only documentation and its guard. Reuse requires identical production
sources, POMs/settings, consumer/native fixtures and runners, plus verification of
both sealed inventories and the native binary hash. It is not another native
execution or another set of consumer test cases in P10 totals.

The reused native binary SHA-256 is
`a3476f5f749d0cb546e4c175f371ea070291923d5621d92cb819758ab9a3b10e`.
Its clean-source compile and executable passed under GraalVM 25.0.3 with Boot
4.0.0, counted routes/catch-all, a bounded open-circuit quiet period, terminal
shutdown checks and same-tag recreation. Genuine Boot 4.1.0 remains assembled
consumer and JVM/AOT evidence, not a second native image.

## Reproduction and Evidence

Evidence root: `target/release-evidence/v34/priority10/`. Exact command arrays,
environments, exits, classpaths, reports and artifacts are retained under each
stage. `SHA256SUMS` seals the retained bundle; its hash is recorded in the
checklist, outside the source-copy seal to avoid a self-referential hash.
`final/` verifies P3-P9 inventories, unchanged P9 Git objects, the native inventory
and binary, API artifact class equality, matched source hashes and final readiness.
`analysis-final/results.json` explicitly retains both primary and confirmation
flags; the earlier `analysis/results.json` describes primary flags only and is
preserved, not used as an all-clear summary. The readiness manifest still has
`activeRoadmap=v34`, `releaseLane=unselected` and `plannedFinalVersion=null`.
Later source/fixture/coordinate changes require a new applicability audit, not
editing this sealed run. This is not clean-commit release evidence for the local
documentation delta and is not signing or publication evidence.

| Artifact | SHA-256 |
|---|---|
| Central starter `4.4.2` | `fb8646ce2f6ed172598ff446cdfa9da2f348c0fd56a2b4c9a02e7ac1dae803c1` |
| Current starter used for scoring | `e2803a7bf45c774d73f65c41676a2d8adc435db00842ef7055db3ff25c41abf2` |
| Baseline shaded benchmark | `c9ff9fa21d4778a5dfcadbac145439283c1bc89fc13dedc538fe48c63c69cf9a` |
| Current shaded benchmark | `f30482de11e8ccefa8980beb29f4074a9de011683cb8cee37d0a84925b8239db` |

Do not root-clean this evidence or install current artifacts into a baseline
repository. Use new paths for every attempt, preserving failed attempts.

Build the matched pair with the [tracked input guard and reproduction commands](WORKLOAD-CONTRACT.md#reproduction),
selecting the fresh `benchmark-v34-p10` provenance lane for this run. Score the
saved artifacts sequentially with the frozen command there. Review all 60 rows:

```bash
BASE="$PWD/target/release-evidence/v34/priority10"
python3 scripts/review-v34-benchmark-results.py \
  --baseline "$BASE/scored-baseline/v34.json" "$BASE/scored-baseline/helpers.json" \
  --current "$BASE/scored-current/v34.json" "$BASE/scored-current/helpers.json" \
  --output "$BASE/review/primary-new"
```

Confirm every flagged row plus the historical enabled-only publisher once in
reverse order. Keep its exact benchmark name and `-p profile=... -p scenario=...`
parameters, all frozen flags and a separate JSON per row. Use `--confirmation`
with the same reviewer for the matched subset; never edit raw JSON or omit an
unfavorable fork. The recorded `review/confirmation-selection.json` preserves the
selection made before confirmation.

For ordinary verification with Java 21 and Maven 3.9.9, use the central-only
settings and an explicitly writable current repository:

```bash
export JAVA_HOME=/usr/lib/jvm/jdk-21.0.8-oracle-x64
export PATH="$JAVA_HOME/bin:$HOME/.sdkman/candidates/maven/current/bin:$PATH"
export MAVEN_OPTS='-Xmx512m -XX:ActiveProcessorCount=2'
REPO="$PWD/target/v33-native-runs/native-g0ynw95x/repository"
MVN=(mvn -B -ntp -s .mvn/maven-central-settings.xml "-Dmaven.repo.local=$REPO")
for boot in 4.0.0 4.1.0; do
  "${MVN[@]}" -Dspring-boot.version="$boot" \
    -pl reactive-http-client-starter,reactive-http-client-test,reactive-http-client-otel clean || break
  "${MVN[@]}" -Dspring-boot.version="$boot" \
    -DargLine='-XX:+DisableExplicitGC -XX:ActiveProcessorCount=2' verify || break
  bash scripts/verify-generation-packaging.sh 4.5.0-SNAPSHOT || break
  # Archive this row's XML, artifacts and effective dependency inputs before the next clean.
done
"${MVN[@]}" -pl reactive-http-client-starter -Pv32-cache-reachability test
"${MVN[@]}" -pl reactive-http-client-starter -Pv31-handoff-reachability test
"${MVN[@]}" -f reactive-http-client-benchmarks/pom.xml -Pv34-benchmark-reachability test
```

Module clean is scoped so retained root evidence survives. Root module rows select
the Boot BOM; they do not replace the genuine consumer parents already verified
in P9. Stop and archive failures before proceeding; do not interpret a partial
loop as a passing matrix. Run strict API lanes independently; this loop preserves
the root failure status without skipping the starter comparison:

```bash
for kind in root starter; do
  lane="v34-p10-api-$kind-rerun"
  repo="$PWD/target/published-baseline-repositories/$lane-4.4.2"
  out="$BASE/$lane"
  test ! -e "$repo" && test ! -e "$out" || exit 1
  mkdir -p "$repo" "$out"
  rsync -a --exclude=/io/github/huynhngochuyhoang/ "$REPO/" "$repo/"
  api=(mvn -B -ntp -s .mvn/maven-central-settings.xml "-Dmaven.repo.local=$repo")
  selection=()
  modules=(reactive-http-client-starter reactive-http-client-test reactive-http-client-otel)
  artifacts=(reactive-http-client "${modules[@]}")
  if [ "$kind" = starter ]; then
    selection=(-pl reactive-http-client-starter)
    modules=(reactive-http-client-starter)
    artifacts=(reactive-http-client-starter)
  fi
  "${api[@]}" -pl "$(IFS=,; echo "${modules[*]}")" clean || exit 1
  if "${api[@]}" "${selection[@]}" -Papi-compatibility -DskipTests verify >"$out/verify.log" 2>&1; then
    status=0
  else
    status=$?
  fi
  printf '%s\n' "$status" >"$out/verify.exit-status"
  if bash scripts/verify-published-baseline-provenance.sh "$lane" 4.4.2 \
      "$out/provenance" "${artifacts[@]}"; then
    provenance_status=0
  else
    provenance_status=$?
  fi
  printf '%s\n' "$provenance_status" >"$out/provenance.exit-status"
  for module in "${modules[@]}"; do
    mkdir -p "$out/$module"
    if [ -d "$module/target/japicmp" ]; then
      cp -r "$module/target/japicmp" "$out/$module/"
    fi
  done
done
```

A nonzero saved exit keeps its lane failed regardless of provenance or the other
lane. No `major-api-report` profile or relaxed compatibility switches are used.
The recorded runner uses a structured third-party-only copy instead of `rsync`;
the baseline isolation is identical. Run the tracked
`verify-api-compatibility-fixtures.sh` and `verify-published-baseline-fixtures.sh`
with `MAVEN_OPTS` also selecting the writable current repository; archive their
dedicated output directories. Expected failures inside those negative fixtures
are guard successes, not candidate API passes.

After documentation-only edits, rerun the affected guard on both supported rows
and archive its XML separately from the earlier full-module reports:

```bash
for boot in 4.0.0 4.1.0; do
  "${MVN[@]}" -Dspring-boot.version="$boot" -pl reactive-http-client-starter \
    -Dtest=DocumentationReleaseArtifactTest \
    -DargLine='-XX:+DisableExplicitGC -XX:ActiveProcessorCount=2' test || break
  # Archive this row's DocumentationReleaseArtifactTest XML before the next run.
done
python3 -m unittest discover -s scripts -p 'test_*v34*.py' -v
python3 -m unittest discover -s scripts -p 'test_verify_v33_native.py' -v
git diff --check
```

## Claim Limits

This is not process-startup, deployment throughput, p95/p99, heap-retention,
direct-memory or pod-RSS evidence. Construction/first-call B/op includes fixture
setup/teardown even when latency excludes it. Loopback allocation includes server
work. Helper and cache-hit rows cannot be compared with a network call, and
subtracting unlike row means cannot isolate starter cost. Different profiles
retain different required behavior. No zero-overhead promise follows from a pass.

No public performance number is promoted by this record. Promotion requires the
existing release-quality report process and resolved claims; smoke/profile timings
or shared-host noise cannot substitute. P11 guidance and P12 release scope remain
open, with no implied patch/minor release and no signed/publication evidence.
