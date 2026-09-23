# V33 Baseline and Characterization Scope

> **Recorded:** 2026-09-23
> **Published baseline:** `4.4.1`
> **Development coordinate:** `4.5.0-SNAPSHOT`
> **Implementation and release scope:** unselected

This is the baseline record for [Priority 1](CHECKLIST.md), not approval to fix
all proposed findings. **Priority 2.3** requires an explicit maintainer decision
before production changes. The [roadmap](ROADMAP.md) reconsiders V32-F001-F003;
their original deferral and workarounds remain valid history. V1-V32 remain
completed; V32-F004/F005 are already delivered in `4.4.1`.

## Source and Version Boundary

Reviewed clean source: `66e8b7689e16f9393c158879fac9317de47895e7`.
Published tag: `v4.4.1`, reachable ancestor
`0e3667c1407b5a481ce1f020a3bf4747fdad5b48`.
The provenance audit and Maven validation started before tracked edits. Fresh
documentation verification uses that source plus the recorded baseline/checklist
and documentation-test patch, not a clean release or production build.

Root, starter, test-helper, OTel, benchmark parent, current Boot consumer,
cache-disabled consumer and native-smoke coordinates remain `4.5.0-SNAPSHOT`.
README/quick start, public/API/consumer/benchmark baselines remain `4.4.1`.
No version bump, dependency, production API, configuration or schema change is
needed. Production main sources are unchanged from the release tag. The published
consumer sources, Central settings and publication/provenance scripts are also
unchanged; the current fixture's default coordinate intentionally differs, while
the published verifier explicitly selects `4.4.1`.

V33 alone is active in the [index](../README.md), roadmap, checklist and archive
guard. Readiness reads the checklist's exact active status, not the snapshot
suffix. Its existing test covers snapshot, final-candidate and post-publication
version states while the checklist remains active. Current readiness is
`activeRoadmap=v33`, `releaseLane=unselected`, `plannedFinalVersion=null`.
The generated deferred `4.5.0` label is not a candidate selection. V32's historical
`activeRoadmap=null` describes its closure checkpoint, not V33 adoption.

Support remains Java 21 with Boot 4.0.0 as the default and Boot 4.1.0 as the
existing upper matrix row. Framework 7 / Jackson 3 remain the current generation;
the separate 2.x / Boot 3.5 maintenance lane is neither upgraded nor revalidated.
See the [compatibility guide](../../docs/20-native-release-compatibility.md).

## Revalidated Evidence

Evidence root: `target/release-evidence/v33/priority1/`. These are explicitly
revalidated V32 results, **not fresh Central downloads**, new consumer executions,
new strict API comparisons or new native/benchmark runs. The
[V32 publication record](../v32/CLOSURE-EVIDENCE.md#post-publication-closure) and
[compatibility record](../v32/COMPATIBILITY-VERIFICATION.md) remain immutable.
The target-only `reuse-audit.py` and `reuse-audit.json` retain exact checks and
artifact/report hashes; original bundles retain commands, effective POMs,
dependency trees, classpaths, XML and toolchains.

| Evidence | Recheck and applicability |
|---|---|
| Published `4.4.1` parent/modules | All 13 artifacts rehashed in the original isolated `release-artifacts-4.4.1` repository: parent POM and three modules' POM/binary/source/Javadoc files. Central remote markers and embedded versions checked. All four POMs and all 145 packaged Java sources (131 starter, nine helper, five OTel) match the reachable release tag. |
| Published assembled consumer | Original separate `consumer-4.4.1` repository, Central provenance and successful completion rechecked. Four baseline cases, zero failures/errors/skips; the classpath contains all three published JARs, hash-identical to the artifact repository, with no reactor output directories. The full-profile report separately contains 28 passing cases. These are original executions, not new V33 results. |
| Strict source/binary API | V32 archive's independent root and starter comparisons against Central `4.4.1` remain applicable to unchanged production sources. Reports/provenance are covered by the publication manifest; baseline POM/JAR inventories were rehashed separately. No fresh strict run or behavioral-equivalence claim. |
| Supported reactor rows | V32 Priority 10 reports recount to 1,928 module cases in each Boot 4.0.0/4.1.0 row, zero failures/errors/skips. These tested the original snapshot source, not this documentation patch. Both supported versions remain configured. |
| Genuine upper consumer | Rechecked the 25-file standalone reproduction bundle, its 28 passing cases, effective Boot 4.1 parent and Boot 4.1.0 JAR versions. This assembled snapshot consumer is not a published `4.4.1` consumer. The earlier property-only/matrix-script mixed-version consumer must not be relabeled Boot 4.1 evidence. |
| Native and targeted costs | Original V32 native completion and no-hot-path-cost-change conclusions remain historical references only. No native binary or cost result is regenerated or certified for V33. Preserve the **no-public-performance-claim** decision; implementation determines later rerun needs. |

The original published-consumer provenance names clean fixture commit
`95774391cbb3545eb5b11fd3d116ebb2f3ca8e3e`. Its applicability does not depend on
that squash-local identifier being an ancestor: the retained release archive,
reachable release tag and unchanged current fixture supply durable source anchors.
The baseline consumer retains effective POMs/tree/classpath for its baseline
profile; do not describe those files as separately generated full-profile inputs.
The 28-case full-profile result has its own command/log/XML, not its own resolved
classpath capture.

| Reverified bundle | Files | SHA-256 of `SHA256SUMS` |
|---|---:|---|
| V32 `priority12-publication` | 477 | `2f5e8825bb6faaa79dac1841be10b5611a28c08fe66118e764cf68bc96447c6b` |
| V32 `priority10` | 692 | `db5f5544c250d5992af0e8a6fdae2af6f4edfbb0be72b2744afb1491b75a164b` |
| V32 `priority10-consumer-reproduction` | 25 | `3313af37a01667e71691e4e5f3d9d64bbacbd74d55e7216b5b78f3f19ab72cbc` |

The published 13-artifact inventory SHA-256 is
`8ed5d7df86f52c31292b1cb7a0d3449251b12eb174d91a93d6d80137a68666a4`.
Rechecking the sealed publication bundle preserves its signature verification,
original failures and source archives; it is not a new signing or publication
verification. No V32 record or bundle was edited. Preserve target-only evidence
before root clean; a checkout without it must rerun publication/consumer checks
or obtain and rehash the original bundles, not assume a pass from this prose.

## Characterization Scope

All IDs retain their meaning from the [V32 finding register](../v32/FINDINGS.md)
and [scope decision](../v32/ARCHITECTURE-DECISION.md). Priority 1 inventories
historical reproductions and inspects unchanged enforcing code; Priority 2 must
freshly reproduce them and separate defect witnesses from fixture errors.

| ID / owner | Original witness and current workaround | Missing evidence / reconsideration boundary |
|---|---|---|
| V32-F001; factory/cache-validation maintainer | External E12 in [ExtensionScenariosTest](../../.github/boot4-consumer/src/v32-test/java/example/v32/ExtensionScenariosTest.java): bean-factory validation recognizes the starter builder but ApplicationContext does not. Explicitly classify the inspected starter builder SAFE, along with all application customizations. | Paired entry points, parent ownership/child shadowing, replacements/same names, lazy non-instantiation, external and AOT/inspection checks. Reconsider the local ownership lookup when the workaround blocks a consumer; no name-only exemption. |
| V32-F002; metadata/planning maintainer | External E06 in the same fixture: fresh static public metadata validates but lacks internal EffectiveApi and invocation throws before a publisher/dispatch. Delegate built-in parsing or use the tested public API-ref metadata/configuration. | Valid fresh static metadata, deliberate invalid-input failures, generic return/timeout precedence and cached-plan identity, runtime/mock/AOT/export parity. Reconsider when delegation/API-ref cannot serve the parser; keep EffectiveApi internal. |
| V32-F003; AOT selection maintainer | [EffectiveSelectionAotReviewTest](../../reactive-http-client-starter/src/test/java/io/github/huynhngochuyhoang/httpstarter/config/EffectiveSelectionAotReviewTest.java): primary passes runtime and AOT; non-fallback, priority and default-candidate runtime selection differs from AOT's first-singleton choice. Mark the intended properties bean primary. | Environment/programmatic binding, hierarchy/FactoryBeans, prototype creation counts, invalid preferred configuration and no business-client initialization. Reconsider when primary is unsuitable; JVM processor evidence alone is not native certification. |
| V32-F004; invocation-assembly maintainer | Delivered failed-handler-construction cleanup, verified by [ResourceOwnershipReviewTest](../../reactive-http-client-starter/src/test/java/io/github/huynhngochuyhoang/httpstarter/core/ResourceOwnershipReviewTest.java). Rejected construction must leave meter leases/live owners unchanged and preserve the original error. | Preserve rollback before ownership transfer, same-tag owners, optional-dependency absence and application ownership. Not new V33 fix work or proof of the reported pod-memory cause. |
| V32-F005; test/ownership maintainer | Delivered deterministic ordinary cleanup checks plus the separate 16-case [CacheOwnershipReachabilityIT](../../reactive-http-client-starter/src/test/java/io/github/huynhngochuyhoang/httpstarter/core/CacheOwnershipReachabilityIT.java) controlled lane. | Keep ordinary tests independent of forced collection; explicit-GC-disabled checks and controlled prerequisite/reachability runs stay distinct. No production instrumentation or collection guarantee. |

F001-F003 remain deferred pending **Priority 2.3**. The proposed set is not
approval; a subset or review-only outcome remains valid. The original external
and AOT tests intentionally characterize defects, so their passing results must
not be presented as corrected behavior.

Excluded: new public SPI or universal resolver, module/dependency redesign,
second metadata model, automatic propagation/forwarding, hot reload, relaxed
cache SAFE/identity/auth requirements, broader cache or resilience semantics,
new resource ownership, and unrelated production memory/mesh claims. Runtime
and AOT may create eligible configuration; diagnostics must preserve its
non-instantiating/unknown contract. No production change follows from this audit.

## Fresh Verification

Fresh stages use Oracle JDK 21.0.8, Maven 3.9.9, Java target 21, default Boot
4.0.0 and `.mvn/maven-central-settings.xml` (Central-only mirror).
`MAVEN_OPTS='-Xmx512m -XX:ActiveProcessorCount=2'`; the focused test disables
explicit GC. Maven validation/documentation use the already populated local
repository `/tmp/v32-boot41-consumer.Z9m7kq/repository`, not a published-evidence
repository. The fixture scripts use their own target fixtures and normal Maven
repository; neither is substituted for published artifact consumption.

Maven validation, the provenance and API fixture guards, script syntax and
unchanged-scope checks passed. The documentation/archive/readiness suite passed
**69 tests, zero failures/errors/skips**. The final checklist update is rerun
through the same suite before sealing the evidence.

The new baseline-record regression was first run without the record and failed
with `NoSuchFileException`; its log/XML remain under `docs-red/`. Final actual
results and completion are recorded in the [checklist](CHECKLIST.md). No full
reactor, matrix, strict project API, assembled consumer, native, signing,
publication or benchmark execution is claimed for Priority 1.

Reproduction from the repository root:

```bash
export JAVA_HOME=/usr/lib/jvm/jdk-21.0.8-oracle-x64
export PATH="$JAVA_HOME/bin:$PATH"
export MAVEN_OPTS='-Xmx512m -XX:ActiveProcessorCount=2'
mvn -B -ntp -s .mvn/maven-central-settings.xml \
  -Dmaven.repo.local=/tmp/v32-boot41-consumer.Z9m7kq/repository validate
bash scripts/verify-published-baseline-fixtures.sh
bash scripts/verify-api-compatibility-fixtures.sh
mvn -B -ntp -s .mvn/maven-central-settings.xml \
  -Dmaven.repo.local=/tmp/v32-boot41-consumer.Z9m7kq/repository \
  -pl reactive-http-client-starter -DargLine=-XX:+DisableExplicitGC \
  -Dtest=DocumentationReleaseArtifactTest test
bash -n scripts/verify-supported-matrix.sh scripts/verify-published-consumer.sh \
  scripts/verify-published-release-artifacts.sh scripts/verify-published-baseline-provenance.sh
git diff --check
```

Substitute a populated local repository as needed. Each evidence stage retains its
exact command, timestamp, exit status, source/tree IDs, working-tree state and
patch. The final inventory includes current V33 records, test source, reports,
settings hash and generated readiness. Historical/proposal/production/POM scopes
are checked unchanged against the reviewed commit.

Recheck original bundle integrity from each bundle directory:

```bash
(cd target/release-evidence/v32/priority12-publication && sha256sum --quiet -c SHA256SUMS)
(cd target/release-evidence/v32/priority10 && sha256sum --quiet -c SHA256SUMS)
(cd target/release-evidence/v32/priority10-consumer-reproduction && sha256sum --quiet -c SHA256SUMS)
sha256sum --quiet -c target/release-evidence/v32/priority12-publication/central-artifacts/project-artifact-sha256.txt
sha256sum --quiet -c target/release-evidence/v32/priority12-publication/central-consumer/published-baseline-provenance/project-artifact-sha256.txt
```
