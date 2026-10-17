# V36 Compatibility, Cross-Path and Lifecycle Verification

> **Recorded:** 2026-10-14
> **Status:** Priority 8 complete; allocation acceptance remains blocked
> **Selected delta:** tooling/documentation only; no production change
> **Release scope:** unselected

This executes [Priority 8](CHECKLIST.md) under [V36-P6-1](CORRECTION-DECISION.md).
The [cost review](COST-ACCEPTANCE.md) remains non-acceptance: **A001/A002 remain
unresolved/blocking**. Correctness verification does not resolve their ordinary
allocation modes or authorize another investigation, exclusion or release.

## Applicability and Source

The clean starting commit is `00e0adfca489c59184dde97113943548e9518dc5`, tree
`3b8d3d33f8ad9f5330af77d0edd8bb347df71097`. Fresh verification uses that reachable
base plus this separately preserved documentation/guard patch. No production,
dependency, public API, runtime fixture, scored harness or version change is
selected. V36's diagnostic recorder and parser changes need their own fresh
checks; unchanged runtime paths qualify for exact historical evidence reuse.

The audit compares **392 tracked inputs** with clean reachable runtime/native
source `f38fb8d22486d35401a7af9cb9e80f5e5e8c75ef`: root/module POMs, `.mvn`,
runtime sources/resources, runtime tests, consumer/native fixtures and relevant
packaging/provenance/V33 runners. No compared input differs. Explicit exclusions
are the added V36 diagnostic recorder and evolving documentation artifact test;
neither exclusion is claimed covered by the old runtime run. Later verification
orchestration changes are not substituted for the preserved execution commands.

All **2,892 files** in V35 P9/P10 and V36 P6/P7 rehash, with exact manifest
coverage and all four archive hashes verified. Historical XML is reparsed,
module/fixture totals reconciled, and original exits, artifact hashes, actual
Boot parents, classpaths and native source states checked. This is integrity and
applicability verification, not rerunning historical behavior.

| Boundary | Applicable execution |
|---|---|
| Parser, capture accounting, archive/path safety, threshold and workload guards | Fresh Python guards, untimed workload cases, recorder callback and documentation/readiness/link tests. No measured worker or JMH runner is started. |
| Public/factory/mock, selected integrations, lifecycle and ownership | Reuse the unchanged V35 P9 runtime fixtures and XML; V36 P6 focused witnesses remain separately preserved. New full/assembled runs N/A on 2026-10-14 because there is no runtime/fixture delta. |
| Strict source/binary API | Reuse V35 P10 strict root and independent starter comparisons against isolated Central 4.4.2. New comparisons N/A on 2026-10-14 for the same unchanged-input reason. |
| Packaging, JVM/AOT and native | Reuse verified binary/source/Javadoc, metadata, JVM/AOT and native evidence. Fresh compilation/execution N/A on 2026-10-14; this is not a new native pass. |
| Collection claims | Reuse only the separate controlled reachability lanes; no fresh collection claim or GC-dependent ordinary test. |

## Fresh Guards

- **83 Python tests** pass across the V33-V36 tooling suites, including missing,
  duplicate, malformed, truncated and mismatched recording rejection, compiler
  association limits, bounded intervention accounting and evidence-directory
  safety. Synthetic tests do not launch diagnostic workers.
- **40 V34WorkloadContractTest cases on each of Boot 4.0.0 and Boot 4.1.0** pass,
  with no failures/errors/skips. They verify equivalent request/result/dispatch
  and callback semantics without timing or scoring. Same cases on two stacks are
  not 80 unique contracts. The benchmark profile is used for `test`, not JMH.
- **BoundaryRecorderTest** compiles and its standalone callback contract passes.
  It directly exercises identity, before/after ordering, incomplete-record and
  invalid callback rejection; there is no measured worker, preflight or probe.
- Published-baseline provenance fixtures pass with their expected negative
  results, including local/mixed artifacts and root/module self-comparison.
  They are synthetic fixtures, not fresh Central consumption or an API run.
- **116 current documentation/readiness/link cases per Boot row** pass with zero
  failures/errors/skips. Together with the 40 workload cases this is **156 Java
  cases per row**, plus the standalone callback contract, not a full-suite run.
  Overlapping documentation reruns are not added to unique totals. They replace
  no old scores or runtime evidence. `git diff --check` passes.

Java checks use Oracle Java 21.0.8, Maven 3.9.9, a 512 MiB Maven heap, two active
processors and **explicit GC disabled**. The recorder callback uses the same GC
restriction. No benchmark, profile or native compile overlaps these checks.
The exhausted 48/48 investigation budget is unchanged.

The first new documentation test intentionally fails because this record does
not yet exist. The first provenance-fixture invocation exits 1 when its nested
Maven lookup writes to the read-only default repository. The preserved diagnostic
reproduces `resolver-status.properties` access failure; selecting the existing
writable repository through `MAVEN_OPTS` lets the fixtures pass without code or
dependency changes. Earlier successful workload, parser and callback checks are
not rerun or relabeled because that supplemental command failed.

The first reuse-audit attempt also stops at an incorrect report-directory
assumption: the saved independent starter report is directly under its module,
unlike the root's `japicmp` subdirectory. The corrected offline reader still
requires three root reports and one starter report. Both audit drivers and the
failed assertion are preserved; neither API comparison was executed again.
The first full documentation run also caught a case-sensitive mismatch in the
new guard's `Failed creation` heading expectation; the guard was corrected, not
the underlying ownership claim. Its failed XML and subsequent rerun are retained.

## Reused Runtime and Ownership Contracts

These are the [2026-10-11 regenerated P9 results](../v35/VERIFICATION.md), not
fresh V36 executions. The earlier lost P9 attempt and V35 P2-P8 recordings
remain unavailable; the verified replacement does not reconstruct them.

| Saved lane | Verified historical result and limit |
|---|---|
| Full modules, both Boot rows | **2,446 cases per row**: starter 2,229, helper 80, OTel 62, benchmark contracts 75. Includes 101 historical documentation cases per row, which do not validate current docs. Fresh guards are separate, not added to full-suite totals. |
| Assembled selected consumer | **29 cases per row**; fixture parent is genuinely Boot 4.0.0 / Boot 4.1.0, not a mixed-parent dependency override. Saved starter/helper/OTel JAR hashes match that row's installed artifacts. |
| Optional-absence consumer | **One case per row**, with Caffeine, Resilience4j registries, MeterRegistry, OTel API and mock helper physically absent. Enabled-only resilience dispatches two GET calls with no selected operator. |
| JVM/AOT smoke | **Six fixture cases per row** and separate ordinary/AOT-enabled execution exits 0. Boot 4.1.0 JVM/AOT is not a second native image. |
| Controlled collection | **16 / 5 / 2** cache/handoff/benchmark reachability cases on Boot 4.0.0, in dedicated bounded Serial-GC profiles with explicit GC enabled. These support only their named collection claims. |

All reused cases have zero failures/errors/skips. Ordinary runtime suites have
explicit GC disabled. The saved native binary is
`66de72017d7c8e8a33e1a4a593f55a0736813ffb479d88ccd3bbd7ec77bd7271`, from the clean
reachable source above, unchanged before/after its recorded compile and execution.
Both exits are zero; six fixture cases passed. It used GraalVM 25.0.3, Boot 4.0.0,
Java target 21, a 6 GiB build heap and two build workers. Hash verification is
not a new native build/execution or a current build-resource measurement.

| Retained contract | Unchanged witnesses included in historical totals |
|---|---|
| Runtime/AOT metadata/properties selection | 243 properties-selection, seven metadata-selection, 27 AOT smoke, seven binding-lifecycle, four effective-selection and two cross-path cases per row retain scopes/aliases/proxies, processor ordering/restoration, binding awareness/init timing and selected record hints. Runtime lifecycle tracking stops after singleton initialization; AOT history is factory-owned. |
| Fresh planning and dynamic discovery | PublicStaticMetadataContractTest, DefaultPathCostOwnershipTest and OptionalPreparationContractTest retain fresh metadata, late/prototype providers, supports/order behavior and complete mutation validation. |
| Body/context and cancellation/discard | StreamingUploadOwnershipTest, MultipartWireOwnershipContractTest, CacheWorkOwnershipContractTest, explicit handoff and terminal-state tests retain cleanup acknowledgements, discarded-value handling and subscription isolation. Their deterministic gates are not replaced by sleeps or forced collection. |
| Cache/auth/retry/deadline composition | Selected cache-hit authorization, retry/replay bytes, independent caller budgets, shared loads and refresh remain covered by composition and semantic-read suites. Mock results remain synthetic, not transport-buffer evidence. |
| Failed creation and shutdown | ResourceOwnershipReviewTest retains no-leak rejected construction, owned meter-lease cleanup and application connector ownership. Supplied pools, schedulers, registries and SDKs remain application-owned; API presence alone does not transfer disposal responsibility. |

The independent non-single-flight load path remains **caller-owned** and can
outlive manager close. Its terminal/cancel signal releases captures; late cache
publication stays rejected. Manager-owned flights and refreshes terminate on
shutdown. Removed meters cannot prove post-close completion; the existing
tests use cancel/discard acknowledgements and bounded no-dispatch witnesses.
These finite witnesses are not a collection, whole-process heap or pod RSS proof.

## API, Packaging and Provenance

The [P10 API record](../v35/FINAL-ACCEPTANCE.md) retains strict root and independent
starter source/binary comparisons against Central 4.4.2 in different isolated
repositories. Both exits are zero; root's three module reports and starter's
separate report say `No changes.` Effective POMs keep both binary/source failure
switches true and `ignoreMissingClasses=false`. No report-only lane is accepted.

The original root reports predate a Boot 4.1 packaging rebuild. Reuse the later
retained root comparison with its exact JARs; do not associate the old report
with overwritten output. The independent starter's original retained reports
and artifacts remain applicable. Saved Central provenance is not newly resolved
publication/signature evidence, and installed current artifacts are not Central.

Both Boot rows retain successful binary/source/Javadoc and generated-metadata
packaging checks. No public surface, dependency, component ownership or module
boundary changed in V36. Fresh strict API, full packaging, consumer, JVM/AOT and
native executions are unnecessary for this tooling/docs-only delta, not passes
that were executed now. Any later runtime/fixture change invalidates this N/A
decision and requires its own affected checks, including clean native source.

## Preservation and Remaining Gates

Commands/exits, actual XML totals, readiness snapshots, failed attempts, current
source patch, unchanged-input inventory and historical-evidence references are
kept in `target/release-evidence/v36/priority8/`, copied before cleanup to
`verification-evidence/v36-priority8/` and a verified dated archive. The checklist
records the seal. Original P9/P10/P6/P7 bundles remain required for their raw
reports/binaries; ignored local bundles are not delivered in a clean clone.

No benchmark worker, new compatibility result, signing/publication check or
release approval is implied. Priorities 9-10 remain pending. Readiness stays
`activeRoadmap=v36`, `plannedFinalVersion=null`, `releaseLane=unselected`;
V1-V35 history is unchanged. The allocation flags remain blocking independently
of these correctness and evidence checks.

Fresh guard reproduction, with the recorded Java/Maven installations on PATH:

```bash
export MAVEN_OPTS='-Xmx512m -XX:ActiveProcessorCount=2 -Dmaven.repo.local=/tmp/rhttp-v35-p10-20261011/repository-current'
python3 -B -m unittest discover -s scripts -p 'test_*v3[3456]*.py' -v
for BOOT in 4.0.0 4.1.0; do
  mvn -B -ntp -s .mvn/maven-central-settings.xml -Dspring-boot.version="$BOOT" \
    -Pbenchmarks -pl reactive-http-client-benchmarks -am \
    -Dtest=V34WorkloadContractTest -Dsurefire.failIfNoSpecifiedTests=false \
    '-DargLine=-XX:+DisableExplicitGC -XX:ActiveProcessorCount=2' test || exit
  mvn -B -ntp -s .mvn/maven-central-settings.xml -Dspring-boot.version="$BOOT" \
    -pl reactive-http-client-starter -Dtest=DocumentationReleaseArtifactTest \
    '-DargLine=-XX:+DisableExplicitGC -XX:ActiveProcessorCount=2' test || exit
done
bash scripts/verify-published-baseline-fixtures.sh
git diff --check
```

Exact recorder compile/callback and offline reuse-audit commands are in the
sealed drivers. They verify callbacks and existing records only, not new scores.
Never run root `mvn clean` against the only evidence copy.
