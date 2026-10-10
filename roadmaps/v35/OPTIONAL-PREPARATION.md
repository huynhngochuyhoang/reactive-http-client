# V35 Optional Preparation and Disabled Decisions

> **Plan frozen:** 2026-10-10, before production edits or scoring
> **Workstreams:** V34-C004 optional preparation; V34-C004 rolled-back value reuse
> **Authorization:** Priority 3.3; release scope unselected

Starting clean commit: `69db22d60f54fc6673908a78dc1a15e28f9aa602`.
Execute [Priority 7](CHECKLIST.md) within the two separate
[approved boundaries](FIX-DECISION.md#v34-c004-optional-preparation).
No earlier rejected candidate is applied. Earlier evidence stays immutable.

## Frozen Candidates and Ownership

**Preparation:** reuse the supplied WebClient as the unused identity view only
when the handler has no cache manager. A cacheable invocation requires that manager
before any probe; factory/static creation obtains no manager only for unselected
policies. Keep the complete frozen whole-interface mutation validator. Legacy
constructors always have a lazy manager and retain their separate view, even with
a concrete interface. Fresh/dynamic metadata, selected and mixed clients retain
the original manager-required/validation behavior. No extra policy scan per call.

Resolve `Schedulers.parallel()` in the default manager creation path only after
the existing selected-policy scan. Keep explicitly supplied schedulers, selected
validation/failure timing, and legacy lazy-manager creation unchanged. Share the
post-selection implementation rather than repeating the scan. The application
and Reactor still own supplied/shared schedulers and connectors; never dispose them.
View and scheduler edits are separately reversible. The legacy manager gets an
explicit no-change ownership disposition, not an unapproved removal/reownership.

**Value reuse, independent artifact:** only two immutable disabled Selections and
Decisions for DISABLED and METHOD_DISABLED. Selected/invalid values stay unshared;
null name/policy/reason and source identity stay distinct. Do not change the
whole-interface mutation scan, eligibility, failure timing or configuration lookup.
Evaluate against the unchanged baseline, not stacked on preparation changes.
P2 identifies eliminated/materialized modes; it does not guarantee reuse benefit.

## Frozen Measurement Gate

Use the unchanged saved P6 baseline harness and reconcile all 310 starter classes
against its restored build and this commit's unchanged production source. Build
two independent artifacts. Match all 39 harness/POM files, 120 non-starter JARs,
ordered classpaths and shaded entries; permit changes only in the named production
classes and their nested classes. Record patches, hashes, commands and raw samples.

Score **34 rows per artifact**: all 24 six-profile GET/TARGET warm publisher and
subscription rows, all six V34 construction/first-publisher/first-call rows, and
V30 no-network cache hit/caller-capacity rejection with metered=false/true (four).
Order baseline/preparation/reuse, then reuse/preparation/baseline; two forks each,
**408 scored forks**. Java 21.0.8, JMH 1.37, avgt/ns, one thread, five one-second
warmups/measures, GC profiler, fixed 512 MiB heap, ActiveProcessorCount=2, WARN.
No competing agent builds/tests during scoring; 25-minute stage limit and 2 GiB
available-memory launch floor. Preserve failures, both orders and every fork.

Keep >20% latency and >max(32 B/op, 5%) allocation review triggers. Require
repeatable attributed ordinary production-path benefit, no unexplained adverse
control, correct ownership and no cold-to-first-use cost relocation. Setup/teardown
allocations remain in construction/first-use GC totals. Helper, no-network and
transport tests are not interchangeable measurements. No result-selected rerun,
threshold waiver, best-fork pick, diagnostic-only benefit or refinement is selected.

If modes differ, use existing P2 same-artifact compiler/class-load evidence to
identify the limit, not subtract its 256 bytes from fresh results. Do not accept
reuse solely against a high-mode fork; additional causal work would need a separately
frozen diagnostic plan. Roll back an unsupported candidate independently. A
substantiated no-change outcome must include owner/acquisition and mutation
evidence, not just an unfavorable score. No full-matrix/release/heap/RSS claim.

## Verification Plan

Add an isolated public-constructor fixture counting WebClient mutations and
scheduler access; distinguish those from task/worker/cache/connection/meter
acquisition. Cover unselected, defined-but-unselected, selected and legacy paths,
late metadata/cache selection, distinct disabled sources, concurrent first use and
closed-manager non-recreation. Keep existing gated cancellation, refresh, load,
failed-construction, same-tag meter and no-registry terminal regressions.
Ordinary tests use explicit GC disabled; no reachability assertion from System.gc.

Run the physically optional-absent built-JAR consumer, selected cache/auth/probe,
admission/deadline/publication/byte-bound controls and non-instantiating diagnostics.
Retain startup errors; no laziness may move a required selected failure. Capture
exact fresh XML counts and limits. Append results without modifying this prefix.

## Ownership and Acquisition Review

Recorded 2026-10-10. The baseline and final production source are identical.
The two experimental artifacts were independent, never stacked.

| Boundary | Actual preparation/acquisition | Owner and disposition |
| --- | --- | --- |
| Factory/public static creation, no selected policy | Complete selection/validation scan; no cache manager. Handler builds a separate identity WebClient using the supplied builder state | Dormant handler-owned view, not another connection pool. Probe requires a manager before use. Elision is technically possible under the existing guards, but the measured candidate is rejected |
| Default manager creation | Evaluates `Schedulers.parallel()` before finding no selected policy | Shared Reactor scheduler access, not evidence of a scheduled refresh or worker. The candidate moved access after selection without a second scan; no established benefit justifies retaining it |
| Legacy public constructors, including concrete-interface overload | Always retain a lazy manager, shared scheduler handle and separate identity view | Manager owns empty maps, shutdown sink and lifecycle state; not a Caffeine cache or meter lease. Legacy late selection can use it. Removal/reownership is outside approval and would change supported behavior |
| Selected factory/static creation | Creates policy caches, freezes work selection, validates startup, registers selected metrics and creates the probe view | Factory owns manager and meter leases; static callers retain handler ownership. Failed construction closes only the newly acquired manager. No delayed failure or changed ownership is selected |
| Selected request | Frozen arguments/context, body identity, per-call auth/filter probe, lookup, then eligible loader | Hits still run required gates. Neither candidate changes key, auth, retry, redirect, admission, publication, body or terminal state |
| Mock helper | Checks selection before manager/scheduler access and retains a control for selected deterministic and ordinary mocks | Mock closes its manager. No extra laziness or resource transfer is selected |
| Refresh | Timer/source subscriptions are created only for eligible access-driven refresh work | Shared or explicitly supplied scheduler remains external. Manager close cancels owned work, not the scheduler; Caffeine uses the synchronous executor |
| Close and diagnostics | Manager clears caches, ends shared flights/refreshes, closes admission and meter leases; summaries do not create clients | Independent non-single-flight caller work can outlive close under its caller subscription; late cache publication is rejected. No claim that close cancels every external owner |

Classpath presence is not acquisition. The scheduler witness substitutes a mock
handle and verifies no scheduling/disposal calls during creation and close; it does
not count Reactor's internal executor initialization, threads or process RSS.
The identity-view witness counts builder mutation and dispatch separately, not
retained heap. Neither experiment adds request/context retention or a new pool.
No controlled reachability run is required because no collection claim is made.

The unchanged whole-interface validator still runs before metadata/plan selection
on every invocation, including excluded siblings. Already assembled ordinary
publishers retain their invocation projection. Fresh metadata that introduces a
cache policy without a manager still fails before probing. Legacy construction
without a concrete interface can select a policy before first use, requiring its
retained manager and view. These are distinct contracts, not a single disabled flag.

## Results and Independent Decisions

**Both C004 rows: Resolved without production change.** This is not a performance
pass. The [preparation patch](c004-preparation-rejected.patch) and
[reuse patch](c004-reuse-rejected.patch) are rejected experiment artifacts, not
applied production code. No refinement or result-selected rerun was performed.

All **408 scored forks** completed across 18 stages with zero scored retries:
34 rows per artifact in both orders. Preparation has zero latency/allocation
flags forward and **three allocation flags** reverse (zero latency flags).
Reuse has zero flags in either order, but absence of flags is not attributed
benefit. The unchanged review triggers are not regression allowances.

| Candidate and row | Forward baseline -> candidate | Reverse baseline -> candidate | Interpretation |
| --- | --- | --- | --- |
| Preparation, MINIMAL GET publisher | 259.6 -> 268.8 ns/op; 832 -> 832 B/op | 264.7 -> 282.7; 832 -> 832 | No warm saving |
| Preparation, AUTO_NO_REGISTRY GET publisher | 319.9 -> 335.7; 960 -> 960 | 302.9 -> 329.1; 832 -> 960 | Reverse allocation flag; candidate forks 832/1,088 |
| Preparation, OBSERVER GET publisher | 465.2 -> 475.6; 1,744 -> 1,744 | 495.5 -> 495.9; 1,744 -> 1,872 | Reverse allocation flag; candidate forks 1,744/2,000 |
| Preparation, enabled-only GET publisher | 385.3 -> 368.6; 1,392 -> 1,264 | 364.0 -> 389.4; 1,136 -> 1,264 | Reverse allocation flag; apparent forward gain does not persist |
| Preparation, AUTO_NO_REGISTRY context/proxy | 6.251 -> 6.328 ms/op; 2,988,389 -> 3,061,326 B/op | 5.966 -> 6.218; 3,005,861 -> 3,119,538 | No cold allocation reduction established |
| Preparation, AUTO_NO_REGISTRY first publisher | 63.754 -> 65.219 us/op | 64.750 -> 63.806 | Opposite directions, not a demonstrated removal of first-use cost |
| Reuse, MINIMAL GET publisher | 259.6 -> 266.3 ns/op; 832 -> 832 B/op | 264.7 -> 270.4; 832 -> 832 | Same low-mode bytes and slightly higher timing means |
| Reuse, AUTO_NO_REGISTRY GET publisher | 319.9 -> 300.6; 960 -> 832 | 302.9 -> 298.2; 832 -> 832 | Forward baseline forks 832/1,088; no saving against reverse low mode |
| Reuse, enabled-only GET publisher | 385.3 -> 355.1; 1,392 -> 1,136 | 364.0 -> 359.9; 1,136 -> 1,136 | High-mode comparison alone cannot satisfy the frozen gate |
| Reuse, enabled-only TARGET publisher | 731.9 -> 729.6; 2,328 -> 2,296 | 768.5 -> 742.5; 2,312 -> 2,296 | Reverse baseline forks 2,296/2,328, including the candidate's low value |

Both metered/unmetered selected-cache hit and caller-rejection controls have no
review flags for either candidate/order. All six construction/first-use rows also
have no flags, but no consistent attributed preparation saving. Their GC totals
include invocation-level fixture setup/teardown and must not be priced as isolated
view/scheduler allocation. Full intervals, raw samples and per-fork means remain
in the bundle, including favorable subscription means and contradictory controls.

P2's same-artifact compiler evidence explains why disabled Selection allocation
may be eliminated or materialized; it does not establish fresh candidate causality.
No new compiler intervention was selected, no 256-byte subtraction is applied,
and no high-mode-only comparison is accepted as ordinary-path benefit. Reuse
preserves all mutation checks and distinct disabled sources, but fails the benefit
gate independently of preparation. It is not a reinstatement of V34's rejected fix.

Preparation's no-change route is supported by the acquisition inventory, supported
legacy late selection, selected-filter and lifecycle witnesses, and the separately
reversible local experiment. The view and scheduler are not declared indispensable;
new optimization needs new bounded evidence. Legacy-manager removal is explicitly
not attempted. Reuse's no-change route uses the newly matched low/high fork evidence
and mutation controls, not only V34's prior rollback. Only C005 remains open.

## Provenance and Reproduction

Evidence: `target/release-evidence/v35/priority7/`. `source.json` records starting
commit/tree; `frozen-plan.md` is the unchanged prefix above. Candidate directories
retain source, patches, artifacts, commands and benchmark-contract XML. The saved
P6 baseline is reused, not rebuilt before scoring; production source at the starting
commit matches the restored P6 source. The audit matches **39 harness files**,
**120 non-starter dependencies**, ordered classpaths and shaded entry sets. Only
handler/manager classes change for preparation, only EffectiveCachePolicy classes
for reuse. Final restoration is checked separately from whole-JAR packaging hashes.

Use a new evidence directory and the saved P6 baseline; a clean clone must first
produce that unchanged baseline. The tracked rejected patches reproduce the source
experiments independently with `git apply --unidiff-zero`; never stack them.

```bash
OUT="$PWD/target/release-evidence/v35/priority7"
python3 -B scripts/investigate-v35-optional.py freeze --output "$OUT"
# Build each independent artifact and record inputs with verify-v34-benchmark-inputs.py.
python3 -B scripts/investigate-v35-optional.py audit --output "$OUT"
python3 -B scripts/investigate-v35-optional.py score --output "$OUT"
python3 -B scripts/investigate-v35-optional.py review --output "$OUT"
python3 -B -m unittest discover -s scripts -p 'test_*v3[45]*.py'
git apply --check --unidiff-zero roadmaps/v35/c004-preparation-rejected.patch
git apply --check --unidiff-zero roadmaps/v35/c004-reuse-rejected.patch
git diff --check
```

This bounded run is not full 60-row acceptance, strict API, native/AOT, new Central
verification or heap/RSS evidence. Versions, defaults, dependencies, all production
source and release selection remain unchanged. Tests/builds run outside scored
stages; ordinary tests disable explicit GC. Historical P2-P6 evidence is immutable.

## Regression Evidence

The corrected baseline red run has **13 cases, four intended failures** against
the preparation expectations (view creation and scheduler access); the first two
attempts retain a fixture compile error and a mock-stubbing invocation-count error.
Neither is production evidence. Preparation then passes 87 cases, expanded to
**89 cases** after adding missing-WebClient and fresh-metadata witnesses (15 new,
42 default-path, 12 mutation-policy and 20 resource-ownership cases). Reuse passes
**127 cases** across policy selection, work limits, mutation guards and default
path. Each candidate build also passes **42 benchmark-contract cases**.

After rollback the 15 new tests characterize retained baseline behavior: one view
at construction, no manager for static unselected creation, legacy lazy ownership,
shared scheduler access without scheduling/disposal, independent selected/invalid
decisions, source distinction, probe on miss/hit, late selection/metadata, gated
concurrent first use and no selected-cache revival after close. The candidate
fixture is saved under `preparation/OptionalPreparationContractTest.java`; baseline
assertions are not presented as passing the rejected elision expectations.

Fresh restored verification passes **567 starter cases across 24 classes** and
**72 mock cases across three classes**, with zero failures/errors/skips:

- `OptionalPreparationContractTest` (15), `DefaultPathCostOwnershipTest` (42),
  `ResourceOwnershipReviewTest` (20): entry-point preparation, dynamic consumer
  discovery, early validation, failed-construction cleanup and external owners.
- `DeclarativeCachePolicyTest` (27), `CacheWorkLimitContractTest` (46),
  `CacheWorkPolicyEnforcementTest` (12): selected/unselected rules, distinct sources,
  startup failures and every whole-interface mutation guard.
- `CacheWorkOwnershipContractTest` (25), `CacheCallerAdmissionContractTest` (44),
  `CacheLoadAdmissionContractTest` (29), `CacheRefreshAdmissionContractTest` (20),
  `CacheWorkCompositionContractTest` (23): gated preparation/subscription/cancellation,
  cleanup-before-release, leader/waiter budgets, capacity and refresh isolation.
- `CacheWorkTelemetryContractTest` (21), `LocalResponseCacheObservabilityTest` (17),
  `CacheWorkDiagnosticsContractTest` (7), `ReactiveHttpClientDiagnosticsProviderTest`
  (61): same-tag ownership/recreation, failed optional linkage cleanup, cache outcomes
  without MeterRegistry, no lazy client/registry materialization and unknown facts.
- `BoundedLocalResponseCacheContractTest` (51),
  `ResponseCacheCapacityConcurrencyInvariantTest` (4),
  `ResponseCacheRetentionOwnershipTest` (10), `CacheBuilderOwnershipContractTest`
  (13): limits, independent caller ownership after close, stale publication rejection,
  builder classification and deterministic cleanup. These are not GC reachability runs.
- `V33CrossPathContractTest` (2), `SemanticReadLocalCacheContractTest` (4),
  `SemanticReadReplayTimeoutContractTest` (8),
  `SemanticReadSingleFlightRefreshContractTest` (6), `CacheKeyContractTest` (60):
  actual request/body identity, per-call auth/filter gates, replay, timeout and isolation.
- `MockReactiveHttpClientTest` (65), `MockCacheWorkParityTest` (5),
  `MockResponseCacheSupportTest` (2): public helper semantics, selected/unselected
  control ownership, close and retained terminal/eviction evidence.

Counts are fresh XML cases, not annotation counts. Preliminary/candidate/restored
runs overlap; their counts are not additive distinct-test coverage.

### Rebuilding the Independent Artifacts

For each candidate, start from unchanged production, apply only its tracked patch,
and use `SIDE=preparation` or `SIDE=reuse` below. Use `SIDE=restored` with neither
patch for the final restoration. Run outside scored stages. Maven's local repository
must be writable; the retained commands record the exact repository used locally.

```bash
export JAVA_HOME=/usr/lib/jvm/jdk-21.0.8-oracle-x64
export PATH="$JAVA_HOME/bin:$PATH"
export MAVEN_OPTS="-Xmx512m -XX:ActiveProcessorCount=2"
mkdir -p "$OUT/$SIDE"
mvn -B -ntp -s .mvn/maven-central-settings.xml -pl reactive-http-client-starter -am -DskipTests install
mvn -B -ntp -s .mvn/maven-central-settings.xml -f reactive-http-client-benchmarks/pom.xml \
  clean package -Dtest=V34WorkloadContractTest,V30CacheWorkPerformanceBenchmarkTest \
  '-DargLine=-XX:+DisableExplicitGC -XX:ActiveProcessorCount=2'
cp reactive-http-client-benchmarks/target/benchmarks.jar "$OUT/$SIDE/benchmarks.jar"
mvn -B -ntp -s .mvn/maven-central-settings.xml -f reactive-http-client-benchmarks/pom.xml \
  dependency:build-classpath -Dmdep.outputFile="$OUT/$SIDE/classpath.txt"
mvn -B -ntp -s .mvn/maven-central-settings.xml -f reactive-http-client-benchmarks/pom.xml \
  dependency:tree -DoutputFile="$OUT/$SIDE/dependency-tree.txt"
mvn -B -ntp -s .mvn/maven-central-settings.xml -f reactive-http-client-benchmarks/pom.xml \
  help:effective-pom -Doutput="$OUT/$SIDE/effective-pom.xml"
python3 -B scripts/verify-v34-benchmark-inputs.py record "$OUT/$SIDE" 4.5.0-SNAPSHOT
mvn -B -ntp -s .mvn/maven-central-settings.xml -f .github/boot4-cache-disabled-consumer/pom.xml \
  clean test '-DargLine=-XX:+DisableExplicitGC -XX:ActiveProcessorCount=2'
```

The rejected patches are zero-context source deltas. Reverse only the applied
experiment before building the other; do not undo unrelated working-tree edits.
The helper `verify.py` retained in the bundle records exact argv, Java/Maven
environment, timestamps, exits and selected fresh XML for the local runs.

| Scored shaded artifact | SHA-256 |
| --- | --- |
| Baseline | `f30482de11e8ccefa8980beb29f4074a9de011683cb8cee37d0a84925b8239db` |
| Preparation | `9444ef508889dbb5d93e634a3967e6c76bd62b927275acb0cf726449d902ab62` |
| Reuse | `54e1b5003eb3e19dc5ec322a213d7cd737cd178bc686fbe96c2b237d77d7ebd8` |

The physically optional-absent consumer passes **one Boot 4.0.0 case** on both
preparation and restored production. Its built-JAR classpath lacks Caffeine, all
four Resilience4j registries, MeterRegistry, OpenTelemetry API and the mock helper.
Resilience is enabled with no selected operator; two requests must reach the real
loopback server as GET `/value`. This is physical absence, not mocked availability.
Consumer command logs, effective POM, dependency tree, classpath, artifact hash and
fresh XML are retained independently for each run. No published download or wider
Boot matrix is claimed here.

## Final Verification

The restored build passes **42 benchmark-contract cases** and all **38 Python
checks**. All **310 starter classes** and **24,224 shaded benchmark classes**
match the saved baseline byte-for-byte; source remains unchanged. Both tracked
zero-context patches reproduce their saved candidate source exactly in isolated
temporary trees. This is class/source identity, not whole-JAR packaging identity.

All **100 documentation cases** pass after updating the older guard that still
expected Priority 7.3 to be unchecked. The initial failed run is retained in
`docs-final/`, corrected evidence in `docs-final-corrected/`; it is not concealed
or included as passing evidence. With the restored suites and consumer, final
functional/documentation verification covers **740 cases**, plus the separate
42 benchmark-contract cases and 38 Python checks. Earlier overlapping runs and
the final documentation rerun do not increase those totals.

`final-checks/` records restoration, patch reproduction, Python results and source
scope checks. `final/` holds the final source/readiness audit and fresh evidence
inventory. Prior P2-P6 inventories are reverified without modification. Source
copies precede the checklist's external checksum anchor to avoid self-reference.
Readiness remains active V35, development `4.5.0-SNAPSHOT`, published/API baseline
`4.4.2`, planned final version null and release scope unselected. Later priorities
remain unchecked; this priority does not close the roadmap.
