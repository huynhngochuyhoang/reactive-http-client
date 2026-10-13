# V36 Workload and Compiler Evidence Audit

> **Recorded:** 2026-10-13
> **Scope:** Priority 2; workload/evidence tooling only
> **Allocation findings:** A001 and A002 remain unresolved
> **Experiment authorization:** unselected; Priority 3.3 approval required
> **Implementation and release scope:** unselected

This completes the audit in [Priority 2](CHECKLIST.md), not allocation acceptance.
The [baseline ledger](BASELINE-SCOPE.md) is the historical P1 checkpoint. V1-V35
records and saved evidence are not overwritten; the qualifications below apply
to future use of those records. No new benchmark or diagnostic fork was run.

## Source and Boundaries

Started from clean, reachable commit
`18c64f7b6147189fc2c1229816e97c46bc6a9d39`, tree
`4ce36a31c29e1bac8ec47646f287c6a00f01c4d2`. Verification/re-analysis uses that
base plus the recorded tooling/test/documentation patch. No runtime, timed
harness, dependency, version, fixture or public contract change is retained.

Reviewed the [workload contract](../v34/WORKLOAD-CONTRACT.md),
[warm benchmark](../../reactive-http-client-benchmarks/src/main/java/io/github/huynhngochuyhoang/httpstarter/benchmarks/V34DefaultPathBenchmark.java),
[fixture](../../reactive-http-client-benchmarks/src/main/java/io/github/huynhngochuyhoang/httpstarter/benchmarks/V34WorkloadFixture.java),
[construction benchmark](../../reactive-http-client-benchmarks/src/main/java/io/github/huynhngochuyhoang/httpstarter/benchmarks/V34ConstructionBenchmark.java),
[runner](../../scripts/investigate-v35-final-profiles.py) and
[shared reviewer](../../scripts/review-v35-allocation.py), including both callers
of its compiler reader. The original runner's 36-fork authorization is exhausted;
its historical command is not a V36 execution permission.

## Effective Workload

| Boundary | Verified behavior |
|---|---|
| AUTO_REGISTRY | Actual starter/Jackson auto-configuration and factory; context-owned `SimpleMeterRegistry` selects the built-in Micrometer observer. Untimed setup subscribes twice and checks the real API timer, not `unknown` |
| HOOK | Actual auto-configuration/factory, no registry, built-in observability disabled, one real application lifecycle hook. Untimed setup checks one terminal callback per subscription and its API/attempt evidence |
| GET | `client.get()` produces a cold GET publisher. No body, explicit cache policy, auth or named resilience operator is selected; this is not a cache hit or selected Retry workload |
| Warm publisher row | Fresh proxy invocation on an already constructed fixture, returning a cold Mono. Observer/hook discovery and composition occur; dispatch, decode and terminal callbacks do not. The generated JMH average-time stub consumes the returned publisher through its blackhole |
| Warm subscription row | Subscribes/blocks the same previously assembled cold publisher; independent exchanges and terminal work per subscription. Not interchangeable with publisher assembly |
| Loopback row | Assembly plus real transport, payload consumption, decoding and terminal processing; setup/server ownership stays outside the timed method |
| Construction / first use | Separate methods for context/proxy creation, first publisher and first call. Invocation setup is outside latency timing, but GC-profiler allocation includes fixture lifecycle work; it cannot be subtracted into an isolated first-call allocation |

The no-network exchange customizer replaces transport only. Optional classes are
present but unselected; this does not prove physical optional-dependency absence.
There is no inbound WebFlux server or application Reactor context in these rows.
The fixture's safety-bound `block` is used by subscriptions, not publisher assembly.
Registry/hook presence is real, but their terminal callback cost must not be
claimed as part of the assembly-only score.

Re-ran all **40 untimed** cases in
[V34WorkloadContractTest](../../reactive-http-client-benchmarks/src/test/java/io/github/huynhngochuyhoang/httpstarter/benchmarks/V34WorkloadContractTest.java):
12 warm profile/scenario cases, 24 loopback cases, construction/first-use and
negative-witness cases, and two streaming terminal/cleanup cases. They pass with
explicit GC disabled. These are ordinary JUnit semantic checks, not JMH forks
or scored work. No witness was moved into the measured method.

## Reproduced Tooling Defects

**V36-E001 now has reproduced local tooling defects, not a runtime defect.**
The new synthetic tests fail against the clean P1 readers; that reproduction is
preserved separately from the corrected run.

1. Both diagnostic readers sorted compiler logs by `time_ms`, assigned a numeric
   fork and zipped each log with JMH `rawData` fork means. Unique timestamps do
   not prove this join: **timestamp order is not fork identity**. The JMH JSON
   and console fork sections do not record worker PIDs. The XML's PID and
   `ForkedMain` port do not identify a JSON fork index. Reordered timestamps
   change the old score-to-process attribution without changing the observations.
2. The follow-up reader checked row metadata but did not check the saved command's
   exit, profile, artifact or VM flags. It accepted synthetic failed/mismatched
   command records. Compiler filename/PID, classpath and complete trace inventory
   also needed validation. Counting two selected class lines accepted two copies
   of the same class while the other required class was absent.
3. Compiler summaries retained policy calls/eliminations but dropped later
   relevant tasks without those events, and filtered installed-code records too
   narrowly. An unrelated or superseded compilation could therefore be mistaken
   for the complete history; task completion and installation were not linked.

## Bounded Correction

The existing shared reviewer now validates command success, exact profile/GET,
artifact path, fork count, measurement options and declared VM flags; it checks
complete compiler XML, unique PID/filename agreement, exact class-log inventory,
compiler classpath/VM arguments, required class identities and recorded JAR origin.
Both readers use that boundary. Missing, duplicate, truncated or mismatched input
is rejected instead of receiving an attribution. Saved JAR hashes are independently
checked against the sealed pair input manifest during this audit; a path alone is
not proof of artifact bytes.

Valid legacy inputs retain raw fork metrics and process traces **separately**:
`forkAssociation=unverified`, `measurementInterval=null`. No `fork` or
`allocationBytesPerOp` is assigned to a trace. Filename sorting provides stable
presentation only, not execution order. This is a deliberate internal report
shape change: downstream historical analysis that expects those inferred fields
must not silently reconstruct them by sorting again.

For relevant tasks the reader retains `compile_id`, method/start stamp,
`task_done` completion (or unknown), installed `nmethod` entries and
`make_not_entrant` invalidations joined by process-local compile ID. It also
retains subsequent relevant tasks without elimination/inlining events, avoiding
the old selective history. Unrelated invalidations remain raw process context,
not evidence attached to a different task. An invalidation is not, by itself,
proof of deoptimization during the measured operation. An inlining decision or
successful task is not proof that its compiled code ran during measurement.

Seven deterministic tests cover timestamp reordering without score pairing,
missing/duplicate process or result records, malformed/unfinished XML, wrong
profile/artifact/options, missing/duplicate class identities, classpath mismatch,
unrelated and superseded tasks, and unknown task completion. Existing reader,
runner and workload checks remain in place. No second benchmark framework,
instrumentation agent or production switch is introduced.

## Saved-Evidence Re-analysis

Rehashed all **931** P10 manifest entries and both saved JARs before reading them.
The saved pair's 39 source/build inputs and 120 non-starter dependency identities
still match. Derived output goes only to fresh V36 directories.

The corrected reader processed **58 compiler** processes in **22 diagnostic**
stages: ten original stages / 22 processes and twelve follow-up stages / 36
processes. This reads existing files only, including the compiler logs from the
two historical JFR stages; it does not rerun JFR or claim new sampling evidence.
The corrected per-stage metrics retain the saved raw observations. A001/A002
ordinary follow-up stages still have only low-mode scores; forced non-inlining
remains a separate intervention, not the missing ordinary high-mode witness.

| Stage family, both artifacts | Processes | Relevant tasks / installed records | Tasks with invalidation records |
|---|---:|---:|---:|
| AUTO_REGISTRY ordinary trace | 8 | 864 / 864 | 420 |
| HOOK ordinary trace | 8 | 858 / 858 | 394 |
| AUTO_REGISTRY forced non-inlining | 4 | 378 / 378 | 157 |
| HOOK forced non-inlining | 4 | 389 / 389 | 153 |

These are compiler-history inventories, not per-operation object counts or proof
that every installed version executed. Multiple compilations/invalidations make
the missing measurement boundary material: JMH aggregate JSON and untimestamped
console iterations do not say which installed code version covered an iteration.
XML uptime/wall-clock stamps and class-load times do not fill that gap. The reader
does not estimate a boundary from five seconds of nominal warmup or select the
last task as the measured one.

### Conclusions and Limits

- **Verified:** workload semantics and untimed witnesses; saved input integrity;
  stage command/result agreement; process-to-file and recorded classpath origin;
  process-local task, install and invalidation history.
- **Corrected:** unsupported timestamp-to-score pairing, insufficient command/
  trace validation and selective compiler-history output. Corrected re-analysis
  is fresh derived evidence with unchanged historical raw scores.
- **Unknown:** durable profile/artifact/fork/trace association for each measured
  fork, measurement interval and executing code version. Stage-level association
  cannot prove per-process profile identity or exclude a same-stage substituted
  process log. Report these limits, not a verified per-fork attribution.
- **Historical impact:** raw primary/confirmation scores and interventions are
  not invalidated or erased. V35 causal claims relying on its timestamp-paired
  fork traces need this qualification, including attributed control profiles;
  V36 does not silently upgrade those claims or rewrite their closed dispositions.
- **Disposition:** audit and local correction complete; E001's missing recording
  guarantees are explicit inputs to the next plan, not silently passed acceptance.
  A001 and A002 remain unresolved. No regression, memory leak or optimization
  benefit has been established.

Priority 3 must freeze the smallest way to obtain durable worker/fork identity and
measurement boundaries, account for perturbation, set the numerical budget and
obtain approval **before** any new measurement. A missing mode or ambiguous log
does not authorize another run. The historical 338 forks and exhausted 36-fork
extension remain unchanged; no execution approval carries forward.

## Verification and Preservation

Fresh evidence is under `target/release-evidence/v36/priority2/`, with a verified
copy at `verification-evidence/v36-priority2/` and its dated archive. The checklist
contains the external seal. Commands, exits, the original-reader failing fixtures,
new tests, derived trace inventories, raw-input manifest hash, source patch and
XML reports are retained; historical bundles are unchanged. The original-reader
fixture reproduction is not counted as a passing suite.
Final verification passes 63 Python tooling tests, 40 untimed workload cases,
and 109 documentation cases per Boot row (4.0.0 and 4.1.0), with zero failures,
errors or skips in the accepted runs; `git diff --check` passes. Repeated runs
overlap and are not additional distinct cases.

Reproduce deterministic checks from the root with a writable general Maven cache:

```bash
export JAVA_HOME=/usr/lib/jvm/jdk-21.0.8-oracle-x64
export PATH="$JAVA_HOME/bin:$HOME/.sdkman/candidates/maven/current/bin:$PATH"
export MAVEN_OPTS='-Xmx512m -XX:ActiveProcessorCount=2'
REPO=/tmp/rhttp-v35-p10-20261011/repository-current
python3 -B -m unittest discover -s scripts -p 'test_*v3[3456]*.py' -v
mvn -B -ntp -s .mvn/maven-central-settings.xml -Dmaven.repo.local="$REPO" \
  -Pbenchmarks -pl reactive-http-client-benchmarks -am \
  -Dtest=V34WorkloadContractTest -Dsurefire.failIfNoSpecifiedTests=false \
  '-DargLine=-XX:+DisableExplicitGC -XX:ActiveProcessorCount=2' test
for BOOT in 4.0.0 4.1.0; do
  mvn -B -ntp -s .mvn/maven-central-settings.xml -Dmaven.repo.local="$REPO" \
    -Dspring-boot.version="$BOOT" -pl reactive-http-client-starter \
    -Dtest=DocumentationReleaseArtifactTest \
    '-DargLine=-XX:+DisableExplicitGC -XX:ActiveProcessorCount=2' test || exit
done
git diff --check
```

Do not run the historical experiment runner to reproduce this audit. The sealed
audit driver invokes only input/reviewer functions, not runner `main` or `run`;
recorded artifact paths must still hash to their sealed inputs. A clone without
ignored recordings cannot reproduce their re-analysis from manifests alone.
Restore verified evidence or plan new capture with new provenance and approval.
No full reactor, strict API, native, scored performance or collection run is
claimed. The current lane stays `4.5.0-SNAPSHOT` against published `4.4.2`, active
V36, no final candidate and no release selection.
