# V37 Matched Cost, Compatibility and Ownership Review

> **Recorded:** 2026-10-24
> **Status:** applicable verification complete; full-scope cost acceptance blocked
> **Route:** V37-P2-1 approved stop; no new measurement or correction approval
> **Published / development:** `4.4.2` / `4.5.0-SNAPSHOT`
> **Release scope:** unselected

This records [Priority 8](CHECKLIST.md) after the
[approved stop](FEASIBILITY-DECISION.md#23-approved-stop-and-remaining-gates).
A001/AUTO_REGISTRY and A002/HOOK remain independently unresolved/blocking.
Completing verification cannot make them passed or excluded from closure.

## Applicability Frozen Before Verification

Clean starting source is `fd87028a863e1bbe53d62c6628d92f82f2715844`, tree
`435497f99bd33e2c3319535eb84250cfb7b1b0f8`. Fresh checks use that reachable base
plus the preserved documentation/test patch. V1-V36 history stays unchanged.
No runtime, scored harness, dependency, public API, fixture or JVM default change
is selected. Priorities 3-7 remain N/A, not run, not passed; this review does not
reopen them or approve gates 4.3/7.2.

| Boundary | Applicability on 2026-10-24 |
|---|---|
| New scoring | N/A under V37-P2-1. No numerical budget, scored worker, diagnostic worker, pilot, probe, preflight or recorder smoke is selected. Reuse complete historical samples only after integrity/input checks; do not rebuild or relabel the scored artifacts. |
| Independent/combined cost acceptance | Required review, not a pass. Preserve all primary flags, reverse results, uncertainty and the original >20% latency / >max(32 B/op, 5%) allocation triggers. Unexplained findings remain blocking. |
| Tooling, workload, documentation and readiness | Fresh deterministic Python guards, untimed workload cases and documentation/link/lifecycle checks on Boot 4.0.0/4.1.0. No JMH runner or diagnostic recorder is started. |
| Runtime, assembled consumer, optional absence, ownership | Fresh full runs provisionally N/A for unchanged runtime/fixtures; verify exact input identity and historical XML/artifact provenance before claiming reuse. Any mismatch invalidates this decision. |
| Strict API, packaging, JVM/AOT and native | Fresh runs provisionally N/A for unchanged production/build inputs. Verify both retained strict source/binary API lanes and their isolated Central baseline, packaging and native evidence; historical success is not fresh execution. |
| Collection claims | No fresh collection claim; ordinary tests disable explicit GC. Preserve the historical controlled reachability lane limits separately. |

The [V36 verification record](../v36/VERIFICATION.md) identifies historical
evidence, not a substitute for this review. Missing files must be reported, not
manufactured or replaced by a new run with old provenance. Source equivalence and
archive integrity alone establish neither causality nor current host behavior.

## Exact Reuse and Limits

The offline audit verifies **4,143 files in ten bundles and their complete
archives**: V35 P9/P10, V36 P4/P5/P6/P7/P8 and V37 P1/P2/P7. Original bundles,
failures and historical roadmap records are unchanged. The current source matches
**392 runtime/build/test/fixture inputs** from reachable native/runtime revision
`f38fb8d22486d35401a7af9cb9e80f5e5e8c75ef`, **202 production/build/benchmark
inputs** from reachable V35 P10 integration and both scored inventories' **39
harness/build inputs**. The evolving documentation test and V36 diagnostic
recorder are explicit exclusions from the historical runtime inventory, not
features certified by that run. The recorder is unchanged in V37 and not executed.

Both saved shaded JARs rehash; their **24,224 class entries** match. The ordered
inventories retain **120 matched non-starter dependencies** plus the starter,
with the baseline at `4.4.2` and current at `4.5.0-SNAPSHOT`. However, **121
historical classpath files per artifact remain missing** at their original paths.
Standalone scored starter JARs and remote markers are not freshly reverified at
those paths. The saved Central-only baseline provenance remains historical;
fresh general-cache test dependencies do not restore that old environment.

The recorded V35 P10 execution source
`beb8a237215529c9c8c7ddc285295c9564e4bce0` is not relabeled as reachable integration
`85948837c4f4001f648b15adb3efd80b9de05abd`. Reuse keeps the original source/patch
and dates. Lost V35 P2-P8 recordings remain unavailable. Neither the new audit nor
its hashes reconstructs missing evidence or proves historical commands runnable.

## Cost Review: Not Accepted

The existing reviewer exactly reproduces **60 primary rows per artifact** and
**four reverse-confirmation rows per artifact** from the saved raw JSON. All
forks, samples and intervals are retained. Saved commands/exits/timestamps verify
baseline-then-current primary execution and current-then-baseline confirmation.
Every primary flag plus the historical enabled-only control has confirmation.
These are the **256 historical scored workers**, not fresh execution.

The unchanged >20% latency and >max(32 B/op, 5%) allocation triggers retain **zero
primary latency flags**, **three primary allocation flags** and **zero reverse
flags**. AUTO_NO_REGISTRY's historical flag stays visible with its V35 no-change
explanation; that explanation is not transferred to either required profile.
Diagnostic O/M/T/I observations remain separate from scores. Clear reverse
aggregates do not erase the primary split or demonstrate ordinary causality.

| Required scope | Disposition and retained evidence |
|---|---|
| A001 / AUTO_REGISTRY | **unresolved/blocking**. The primary allocation flag remains; V36 has no ordinary high witness for this profile. Forced non-inlining is not its ordinary cause. Owner: performance maintainer with metrics-integration review. |
| A002 / HOOK | **unresolved/blocking** independently. Ordinary high stages 10 and 15 lack compiler/allocation attribution. Low-only traces and A001's observations cannot explain this row. Owner: performance maintainer with observer/hook review. |
| Combined A001 + A002 | **not accepted**. Input equivalence and correctness checks do not supply either causal explanation. Both findings remain in scope; no named exclusion has been approved. |
| E001 | Feasibility decision complete by V37-P2-1; attribution limitations remain unresolved. No new tooling defect or correction is claimed. Owner: benchmark tooling maintainer. |

Item 8.2's acceptance heading remains unchecked. Its evidence-preservation and
threshold-review tasks can be complete without a performance pass. Full-scope
closure remains blocked; Priority 10 must separately decide any named exclusion.
No public performance claim or report is promoted. B/op is not retained heap,
direct memory, process/pod RSS or a leak diagnosis.

## Historical Correctness and Ownership

XML, exits, actual fixture parents, classpath inventories, artifact hashes and
source state were reread from the sealed V35 evidence, not rerun:

| Historical lane | Reverified result and limitation |
|---|---|
| Full modules, Boot 4.0.0/4.1.0 | **2,446 cases per row**: starter 2,229, helper 80, OTel 62, benchmark contracts 75. Includes old documentation tests, not validation of today's docs. |
| Assembled and optional absence | **29 selected-consumer and one absence case per row**, with genuine Boot parents. Absent Caffeine, registries, MeterRegistry, OTel API and helper remain confirmed from saved classpaths. |
| JVM/AOT | **Six fixture cases per row**, with separate ordinary/AOT-enabled execution exits zero. Boot 4.1 JVM/AOT is not a second native image. |
| Controlled collection | **16 / 5 / 2** cache/handoff/benchmark cases in dedicated historical GC-enabled lanes. No fresh collection or whole-process memory claim. |
| Native | Six fixture cases, clean source before/after, compile and execution exits zero. Binary SHA-256 `66de72017d7c8e8a33e1a4a593f55a0736813ffb479d88ccd3bbd7ec77bd7271`; Boot 4.0.0, GraalVM 25.0.3, Java target 21. Not a new build/run. |
| Strict API and packaging | Root's three reports and independent starter report retain `No changes.` Both binary/source failure switches remain true, `ignoreMissingClasses=false`, distinct isolated Central `4.4.2` repositories and successful exits. Both Boot packaging lanes retain binary/source/Javadoc/metadata success. Not new comparisons or publication verification. |

The API audit uses the later retained root reports associated with their retained
artifacts, not the earlier output overwritten during Boot 4.1 packaging. Historical
full-suite and controlled-lane totals are not added to current test counts.

Unchanged witnesses retain late/prototype discovery, fresh metadata, mutation
validation, body/context isolation, cancel/discard cleanup, failed-construction
cleanup and application-owned resource boundaries. Independent non-single-flight
loads remain caller-owned and may outlive manager close; late cache publication
is rejected. Manager-owned flights/refreshes terminate on shutdown. Removed meters
are not post-close completion evidence. These limits remain as documented in
[V36's ownership review](../v36/VERIFICATION.md#reused-runtime-and-ownership-contracts).

The unchanged-input checks confirm the provisional N/A decisions for fresh full
runtime, assembled, API, packaging and native execution. A later runtime, fixture
or build-input change invalidates that reuse and requires affected verification.

## Fresh Verification and Preservation

Fresh verification passes with zero failures/errors/skips:

- **92 Python tooling tests**, including parser, threshold, workload-input,
  process-accounting and archive rejection guards. Synthetic runner tests use
  bounded Python subprocess witnesses, not diagnostic or native JVM execution.
- **40 V34WorkloadContractTest cases per Boot row** (4.0.0/4.1.0): untimed
  assembly/subscription, exact request/result and terminal-cleanup witnesses.
  The benchmark profile runs `test`, not the JMH runner or a scored workload.
- **123 documentation/readiness/link cases per Boot row**, including this review
  and the unchecked cost-acceptance boundary. Total fresh Java coverage is
  **163 cases per row**, not 326 distinct contracts or a full-suite result.
- Published-baseline provenance fixtures pass with expected negative cases;
  they are synthetic checks, not fresh Central resolution. `git diff --check`
  passes. Repeated final documentation runs are not additional unique cases.

No measured worker, diagnostic recorder, pilot or preflight is started. Ordinary tests use
Oracle Java 21.0.8 / Maven 3.9.9, two active processors, a 512 MiB Maven heap and
explicit GC disabled; these test JVMs are not a diagnostic investigation budget.

The first tooling stage ran **92 Python tests successfully**, then the local
evidence driver rejected its obsolete expectation of 83. The failed driver stage
and passing test log are retained unchanged; only the count check is corrected.
Remaining checks resume in a new directory without rerunning or relabeling those
successful tests. This is bookkeeping failure, not a failing product/tooling test.

Evidence lives under `target/release-evidence/v37/priority8/`; verified copied and
archived contents outside Maven output will be anchored in [CHECKLIST.md](CHECKLIST.md).
The frozen applicability record, adapted offline readers, exact commands/exits,
source patch, classpath hashes, reviews and failures are preserved. Original
bundles remain necessary for their raw reports and binaries. Ignored local copies
are not clean-clone artifacts or off-machine backups.

V37 remains active, `plannedFinalVersion=null`, `releaseLane=unselected`.
Priorities 9-10 remain required; no signing, publication or release decision is
claimed. No production data, identities, request material or new telemetry is
collected, and no snapshot-upgrade or Kubernetes/mesh remedy is recommended.

Reproduce fresh guards with the recorded installations and a writable Maven cache:

```bash
export JAVA_HOME=/usr/lib/jvm/jdk-21.0.8-oracle-x64
export PATH="$JAVA_HOME/bin:$HOME/.sdkman/candidates/maven/current/bin:$PATH"
export MAVEN_OPTS='-Xmx512m -XX:ActiveProcessorCount=2 -Dmaven.repo.local=/tmp/v37-draft-maven-repository'
python3 -B -m unittest discover -s scripts -p 'test_*v3[3456]*.py' -v || exit
bash scripts/verify-published-baseline-fixtures.sh || exit
for BOOT in 4.0.0 4.1.0; do
  mvn -B -ntp -s .mvn/maven-central-settings.xml -Dspring-boot.version="$BOOT" \
    -Pbenchmarks -pl reactive-http-client-benchmarks -am \
    -Dtest=V34WorkloadContractTest -Dsurefire.failIfNoSpecifiedTests=false \
    '-DargLine=-XX:+DisableExplicitGC -XX:ActiveProcessorCount=2' test || exit
  mvn -B -ntp -s .mvn/maven-central-settings.xml -Dspring-boot.version="$BOOT" \
    -pl reactive-http-client-starter -Dtest=DocumentationReleaseArtifactTest \
    '-DargLine=-XX:+DisableExplicitGC -XX:ActiveProcessorCount=2' test || exit
done
git diff --check
```

The preserved offline readers require restored, hash-verified historical bundles;
they inspect files only. Do not run capture/preparation entry points or root
`mvn clean` as part of this review.
