# V32 Review Closure Evidence

> **Date:** 2026-09-19
> **Status:** patch selected; signing/publication and final closure pending
> **Reviewed commit:** `de3c1c9ab0dbf8bd78d9c92224869c2cc30c6ec8` (clean)
> **Reviewed tree:** `65be6836297d8cc30caf692a5a41b89bc378ec11`
> **Published / candidate:** `4.4.0` / `4.4.1`
> **Release scope:** patch `4.4.1` selected; signing/publication and closure pending

This is the Priority 12.1 inventory for the
[architecture decision](ARCHITECTURE-DECISION.md), not a deployment GO, signing pass,
publication claim or roadmap closure. The [checklist](CHECKLIST.md) owns execution
status. The evidence supports the accepted review scope; it does not silently
select `4.5.0` from the development coordinate.

## Scope Reconciliation

The [architecture map](ARCHITECTURE-MAP.md), [external scenarios](EXTENSION-SCENARIOS.md),
[selection review](EFFECTIVE-POLICY-SELECTION.md),
[composition review](INVOCATION-COMPOSITION.md), [ownership review](RESOURCE-OWNERSHIP.md)
and [module review](MODULE-EVIDENCE-BOUNDARIES.md) cover the reviewed modules,
entry points and resource/terminal owners. The decision's reviewed-area table
records a conclusion and limit for each area; no extra module split, universal
resolver, pipeline rewrite or SPI was justified. [Maintainer guidance](MAINTAINER-GUIDANCE.md)
links the supported alternatives and canonical operations instructions.

| Finding | Closure evidence / remaining disposition |
|---|---|
| V32-F004 | Implemented local failed-handler-construction cleanup. Seventeen construction/ownership cases cover original failure preservation, rollback, optional absence, overlapping owners and successful transfer. Broader API/consumer/AOT/native evidence is complete for the implemented snapshot, not a final release artifact |
| V32-F005 | Implemented deterministic ordinary cleanup witnesses and a separate checked 16-case reachability lane. Ordinary explicit-GC-disabled and controlled-JVM results remain distinct. No collection guarantee or production-memory fix is inferred |
| V32-F001 | Deferred; classify the inspected starter builder and all applicable mutations explicitly. Factory/cache-validation owner reopens when the workaround blocks a consumer or lookup changes; other builder combinations need evidence |
| V32-F002 | Deferred; delegate built-in parsing or use tested public API-ref metadata. Metadata/planning owner reopens when those alternatives cannot serve a real parser; invalid/generic/timeout cases remain needed |
| V32-F003 | Deferred; designate the intended programmatic properties bean primary. AOT owner reopens when primary is unsuitable or selection changes; parent/prototype permutations remain unverified |

Both accepted blocking items are implemented and verified; none was removed from
scope. Deferred does not mean fixed. The [finding register](FINDINGS.md) retains
the original reproducer, owner and confidence limits. The decision also retains
E7-03's unexercised selected generic-traversal branch, native timing limits,
application-owned work and unresolved deployment-specific memory/mesh hypotheses.
No new incident attribution is made.

## Reachable Source and Evidence Reuse

Clean implementation revision `c8f6a527450ea512ed6837bd8d09191921d4dd48`
(tree `75a14be9eb4f4bd0de1d837083753b8bcbb3d9ba`) is an ancestor of the reviewed
commit. Comparing all production sources, POMs, Maven settings and the native
fixture between those commits yields no differences. Subsequent changes are
documentation and documentation-test changes, which require their fresh checks
but do not relabel the earlier runtime binaries or reports as new measurements.
V1-V31 records match the V32 starting commit
`c40dba68e141c3fc3031d125842034983351189f` unchanged.

[Priority 10](COMPATIBILITY-VERIFICATION.md) owns the exact commands, toolchains,
source archive, full matrix/API/consumer/AOT/native results and omissions.
Reused results include 1,928 module tests per Boot 4.0.0/4.1.0 row, strict root
and independent starter comparisons against fresh Central `4.4.0`, 105 isolated
current-consumer cases, 28 genuine Boot 4.1 overlay cases, 96 GC-disabled cases,
16 cache and five handoff controlled cases, six JVM fixture cases and successful
JVM/AOT/native executables. Repeated cases are not summed as unique coverage.
The matrix script's mixed-version upper consumer is not genuine Boot 4.1
consumption; use the tracked overlay reproduction recipe.

The original Priority 10 audit intentionally remains `verificationComplete=false`
with failed native attempts. The separate native completion audit records
`verificationComplete=true` for the same clean source. Both are retained;
completion does not erase the 4 GiB heap failure, memory-constrained watchdog
failure, stale documentation assertion, negative JVM prerequisites or mixed
consumer result. Priority 11 retains its initial line-wrapping guard failure.

## Verified Inventory

The following sealed bundles live under `target/release-evidence/v32/`. All
manifest entries were rehashed successfully on 2026-09-19. These are local
evidence bundles, not tracked or published attachments; preserve them before
root clean. Durable conclusions and exact reproduction commands remain in the
linked tracked records. A checkout without these bundles can reproduce the
checks but cannot claim to have inspected the original raw evidence.

| Bundle | Manifest entries | SHA-256 of SHA256SUMS |
|---|---:|---|
| priority10 | 692 | `db5f5544c250d5992af0e8a6fdae2af6f4edfbb0be72b2744afb1491b75a164b` |
| priority10-native | 88 | `b3a4f66679b327fc25ccc85188622ca8a9d914f298f0f16f183bf4ebb5a9aba7` |
| priority10-consumer-reproduction | 25 | `3313af37a01667e71691e4e5f3d9d64bbacbd74d55e7216b5b78f3f19ab72cbc` |
| priority11 | 29 | `de579b2f48dfac7586cfe9e89901b265285667274a35f19aa937f3f775f8ce67` |

Native source archive SHA-256:
`d5999041597473fe8875d53978d0b5528ef13a05fe2cef46c5a2207b0942af1c`.
Native executable SHA-256:
`49332e7ff709fc9295c675ca54d176e52b40bc5262573afab37b2b0dc1d85d4f`.
The verified native manifest includes that executable, companion libraries,
GraalVM 25.0.3/Maven/GCC toolchain hashes, stage commands, reports and source
archive. It validates the `4.5.0-SNAPSHOT` build from the named source; no final
patch/minor-coordinate binary or signing result is implied.

The current `priority12/` bundle preserves recheck results, reachable source/tree
IDs, actual test reports, final documentation/test patch, generated readiness,
toolchain and its own hashes. Earlier sealed bundles are not modified.

## Fresh Verification

Before closure edits, the reviewed clean commit passed **121 cases** across
seven classes: documentation 65, construction/ownership 17, composition 7,
component selection 10, AOT selection 4, customizers 11, default logger 7;
zero failures/errors/skips. This includes the committed outbound-redaction
guidance correction after the original Priority 11 bundle was sealed.
The unsigned `4.4.1` candidate then passed:

| Check | Actual result |
|---|---|
| Full reactor after module clean | 1,932 cases: starter 1,792, mock helper 78, OTel 62; zero failures/errors/skips |
| Candidate documentation guards | 67 cases, including patch selection, pending publication, active roadmap and unchanged published baselines |
| Final affected-check rerun | 123 cases: documentation 67, ownership 17, composition 7, component selection 10, AOT selection 4, customizers 11, logger 7; zero failures/errors/skips |
| Generation packaging | All three modules' binary/source/Javadoc artifacts passed |
| Fresh-repository assembled consumers | 105 cases: mock 74, Boot 4 consumer 28, minimal/cache-disabled consumer 3; zero failures/errors/skips |
| Consumer provenance | `completedStage=evidence-verified`, `exitStatus=0`; assembled jars only, no reactor classes or unintended optional integrations |

Oracle JDK 21.0.8, Maven 3.9.9, target Java 21 and Boot 4.0.0 were used.
The reactor tests disabled explicit GC and used the previously populated isolated
repository. The consumer verifier used Central-only settings and a fresh
target-local repository, into which it installed the unsigned candidate; this
does not imply `4.4.1` was downloaded from Central. Tests and packaging run on a
dirty candidate patch over the reviewed commit, preserved alongside the results.
No new matrix, strict API or native run is claimed for the final coordinate;
the source-identical production evidence above remains scoped to its original
snapshot. No hot-path change justifies new JMH/memory measurement; no performance
or pod-memory claim is made.

The first direct packaging-script invocation exited 126 because the tracked
script is not executable. The successful `bash` invocation is retained separately;
no file-mode change or signing workaround was made.

Reproduction from the repository root (do not root-clean the evidence directory):

```bash
export JAVA_HOME=/usr/lib/jvm/jdk-21.0.8-oracle-x64
export PATH="$JAVA_HOME/bin:$PATH"
export MAVEN_OPTS='-Xmx512m -XX:ActiveProcessorCount=2'
mvn -B -ntp -s .mvn/maven-central-settings.xml \
  -Dmaven.repo.local=/tmp/v32-boot41-consumer.Z9m7kq/repository \
  -pl reactive-http-client-starter,reactive-http-client-test,reactive-http-client-otel clean
mvn -B -ntp -s .mvn/maven-central-settings.xml \
  -Dmaven.repo.local=/tmp/v32-boot41-consumer.Z9m7kq/repository \
  -DargLine='-Xmx768m -XX:+DisableExplicitGC' install
bash scripts/verify-generation-packaging.sh 4.4.1
bash scripts/verify-current-consumer.sh
```

The consumer script requires absent output/repository paths; preserve any previous
`target/release-evidence/current-consumer/current-4.4.1/` and
`target/current-reactor-repositories/consumer-4.4.1/` before a rerun. It rebuilds
module targets, so copy reactor reports and all attachments before invoking it.
The focused affected-check command is:

```bash
JAVA_HOME=/usr/lib/jvm/jdk-21.0.8-oracle-x64 \
PATH=/usr/lib/jvm/jdk-21.0.8-oracle-x64/bin:$PATH \
MAVEN_OPTS='-Xmx512m -XX:ActiveProcessorCount=2' \
mvn -B -ntp -s .mvn/maven-central-settings.xml \
  -Dmaven.repo.local=/tmp/v32-boot41-consumer.Z9m7kq/repository \
  -pl reactive-http-client-starter -DargLine=-XX:+DisableExplicitGC \
  -Dtest=DocumentationReleaseArtifactTest,ResourceOwnershipReviewTest,InvocationCompositionReviewTest,ComponentSelectionReviewTest,EffectiveSelectionAotReviewTest,ReactiveHttpClientCustomizerTest,DefaultHttpExchangeLoggerTest test
```

The repository path records this run's provenance; substitute another local
repository if unavailable. The locally installed candidate must not be substituted
for a fresh published artifact in release-consumer evidence.

## Remaining Decision and Publication Gates

**Maintainer decision, 2026-09-19:** "Prepare patch 4.4.1 (recommended); keep
publication and final closure pending signing/publication verification."

A `4.4.1` patch is proportionate to F004's verified cleanup correction; no new
public API, configuration/default, dependency or request-path feature requires
`4.5.0`. F005 changes contributor tests only. No migration is required. No new
performance, memory or mesh claim is made. No-publication and breaking/major
migration branches are not applicable to this selected scope; publication gates
are still required, not waived. F001-F003 are explicitly excluded.

Reactor modules, benchmark parent and current consumer/native defaults now use
`4.4.1`. The supported-matrix guard and current commands accept that candidate.
The changelog labels it Unreleased; public/API/consumer/benchmark baselines and
installation snippets stay at `4.4.0`. The build inputs' coordinate edits do not
turn the existing snapshot native binary into a signed final-coordinate binary.
Prior source comparisons above refer to the clean pre-cut reviewed revision.

| Remaining gate | State / required evidence |
|---|---|
| Reviewed clean final commit/tag | Pending; current preparation is a patch over the reviewed commit, not a created release commit or tag |
| Signed final artifacts and preflight | Pending; require binary/source/Javadoc/POM signatures and checksums. Unsigned local packaging cannot pass this gate |
| Staged consumer and generation packaging | Local candidate checks may aid preparation; repeat/retain exact final-source evidence with signed artifacts before deployment |
| Publication | Pending; no deploy command or Central publication executed here |
| Post-publication verification | Pending; fresh Central-only repositories must verify attachments and assembled consumption for `4.4.1` before advancing any baseline |
| V32 archive | Pending; keep roadmap active until the selected publication path is verified |

After committing/reviewing the final candidate, follow the
[release preflight](../../docs/20-native-release-compatibility.md) and generated
manual checklist. Authorize signing locally; never record a passphrase in a
command, log or support artifact. After actual publication run:

```bash
scripts/verify-published-release-artifacts.sh 4.4.1
scripts/verify-published-consumer.sh 4.4.1
```

These commands are future gates, not commands run by this inventory. Neither
the scope GO nor successful unsigned validation authorizes marking 12.3/12.4
complete or advertising `4.4.1` as available from Central.
