# V33 Conditional Release Decision

> **Recorded:** 2026-09-29
> **Reviewed source:** `a43959fee81b9ec5644b81636b5b511574d6a497` (clean before preparation)
> **Reviewed tree:** `54dc931cf332f3f5cb86d0c0104fb1fbb452a9c4`
> **Release scope:** patch `4.4.2` selected; publication pending
> **Decision:** GO for preparation; NO-GO for publication/closure until remaining gates pass

The maintainer explicitly selected **"Prepare patch 4.4.2 (Recommended)"** in
response to the question proposing F001/F002/F003 and retaining signing and
publication as closure gates. This authorizes patch preparation, not publication
or a claim that artifacts are on Central. [V33 remains active](CHECKLIST.md).
The former `4.5.0-SNAPSHOT` coordinate did not select a minor release.

## Scope and Migration Review

| Approved finding | Delivered correction and evidence | Migration / limit |
|---|---|---|
| V32-F001 | [Starter-builder ownership](BUILDER-OWNERSHIP.md), external runtime/AOT and cross-path controls | Remove only a redundant declaration for proven starter ownership. Existing SAFE workarounds remain valid; application mutations and unknown provenance still need classification |
| V32-F002 | [Fresh public metadata](STATIC-METADATA.md), external method/target/result and planning tests | Supply complete public fields and bindings. Invalid fresh models now fail deliberately before dispatch; supplied-derived/API-ref precedence remains. No internal API/reflection or live mutation |
| V32-F003 | [AOT selection/binding](AOT-PROPERTIES-SELECTION.md), paired selection/lifecycle and native witnesses | Retain invalid-selected/ambiguity failures. Predictable FactoryBean types, available scopes and processor-identity constraints apply; callbacks that already ran cannot be replayed |

All approved IDs are implemented; none was silently removed or deferred.
V32-F004/F005 cleanup and deterministic/controlled reachability safeguards remain
intact. The [migration guide](MAINTAINER-GUIDANCE.md) documents behavioral limits,
owners, workarounds and reconsideration triggers. V32's historical deferrals are
unchanged. No new public signature, dependency, policy/default, telemetry, module
or outbound operator is selected. Strict source/binary checks are separate from
the documented behavior changes; no universal Spring/native parity is promised.

A compatible patch is proportionate to the approved corrections to supported
extension behavior; there is no new public feature requiring a minor release.
No public speed, startup, memory or service-mesh claim is made. The original
cost review's noisy cold-plan sample and confirmation remain visible. No-release
and broader-feature branches are not applicable to the selected path; publication
and final-source evidence are required, not waived.

## Reachable Source and Reuse

The clean reviewed commit above contains the accepted production code and the
Priority 8 benchmark/Priority 9 example and documentation tests. The inventory
checks the recorded Git objects for all production sources, root/module POMs,
Maven settings, native fixture, both consumer fixtures and parity/native runners
against that reachable revision. They match the Priority 7 measured inputs.
It also compares 16 preserved Priority 9 and benchmark source files byte-for-byte
with committed files. This anchors previously recorded patch/local commit IDs
to a durable reachable source without rewriting historical provenance.

The native binary was compiled/executed from `08e386097f39e577349f6a4e382f424233058b17`
as `4.5.0-SNAPSHOT`, SHA-256
`1312b2fc6269b14093f5acae302494274aeef0dd2543fdfdff629979450b2842`.
It is **not** a new `4.4.2` native build. The candidate patch changes coordinates,
current commands, documentation and release guards, not native fixture Java or
production Java/resources. Candidate JVM/AOT, packaging and artifact comparison
must verify those changed inputs before reuse is accepted for preparation.
No new JMH run is required for a coordinate/documentation-only cut; no quantitative
public performance claim is selected.

## Rechecked Evidence Inventory

All four manifests were fully rehashed on 2026-09-29 before preparation; each
checksum command exited 0. Ignored bundles are local evidence, not tracked or
published attachments. A clean clone without them must reproduce the checks.

| Bundle | Entries | SHA-256 of SHA256SUMS |
|---|---:|---|
| `target/release-evidence/v33/priority7/` | 1219 | `892f553585a0809536d04eee06b19017aa80f5352292d46ce9fc85a6177b5295` |
| `target/v33-native-runs/native-g0ynw95x/evidence/` | 61 | `7e362f1a54b7ab775949031f747373b5eab9180ddf3642a16ecb08cd9e6fc32e` |
| `target/release-evidence/v33/priority8/` | 1379 | `14a97b668059f184d4f697dd6324fcdff3dd4efc8fa8fedd42c6e2339c3cb2fc` |
| `target/release-evidence/v33/priority9/` | 47 | `65367850b1edeeb2fbf18488e3c3e9f170518a92a38dfb3de2cc3d091f51dc63` |

- [Priority 7](PARITY-EVIDENCE.md): mock 76; assembled consumers 29 per genuine
  Boot 4.0.0/4.1.0 row; minimal consumers one each with physical optional absence;
  AOT tests 283 each; fixture six each; JVM/AOT execution and native compile/run
  exit 0. Keep the failed initial fixture and retired native attempts.
- [Priority 8](COMPATIBILITY-COST.md): strict root and independent starter API
  comparisons against Central `4.4.1`, provenance/negative guards, final full
  reactor 2,241 cases, focused 646, controlled reachability 16, packaging, matched
  four-row JMH and confirmation. Benchmark correctness 35 per artifact. These
  counts overlap; raw reports and the initial compile failure are preserved.
- [Priority 9](MAINTAINER-GUIDANCE.md#priority-9-verification): focused 433,
  including documentation 77 and compiled-example four, with zero failures/errors/
  skips. The initial two example-header failures remain separate.

The new `target/release-evidence/v33/priority10/` bundle contains the inventory,
checksum logs and subsequent candidate checks. Source equality is evidence reuse,
not a claim to rerun old tests or to have a signed final binary.

## Candidate Verification

The unsigned candidate is the patch over the reachable source above, not a new
commit/tag. Tests use Oracle JDK 21.0.8 and Maven 3.9.9, with Maven heap 512 MiB
and two active processors. Ordinary reactor tests disable explicit GC. Logs,
XML, commands, toolchain, dependency trees, effective POMs, baseline provenance
and copied artifacts are under `target/release-evidence/v33/priority10/`.

| Fresh candidate check | Result / local evidence |
|---|---|
| Clean unsigned reactor install | 2,105 starter + 80 helper + 62 OTel = **2,247 cases**, zero failures/errors/skips; `full/` |
| Strict root API | Source and binary comparisons against Central `4.4.1`, isolated repository and verified provenance, exit 0; `api-root/` |
| Independent starter API | Same strict baseline with separate repository/provenance, exit 0; `api-starter/` |
| Generation packaging | Binary/source/Javadoc artifacts for all three modules, exit 0; `full/02.log` |
| Current assembled consumer | Fresh candidate repository: helper 76, Boot 4.0.0 full consumer 29, cache-disabled consumer three; zero failures/errors/skips; `consumer-retry/evidence/` |
| Genuine Boot 4.1.0 consumers | Full consumer 29, minimal optional-absence consumer one; zero failures/errors/skips; `parity/rows/consumer41/` and `minimal41/` |
| Candidate JVM/AOT smoke | Six fixture tests per Boot 4.0.0/4.1.0 row, ordinary JVM and AOT generation/JVM execution exit 0; `parity/rows/jvm40/` and `jvm41/` |
| Final documentation/examples | 78 documentation + four compiled-example = **82 cases**, zero failures/errors/skips; `docs-final/` |

The full run includes 78 documentation and four compiled-example cases. Counts
from targeted/consumer runs overlap; they are not added to the reactor total.
API negative/provenance fixture tests and controlled reachability use the
unchanged Priority 8 evidence, not new executions in this cut.

`artifact-equivalence.json` compares candidate binaries against the preserved
Priority 8 artifacts: all 313 starter, 22 helper and 11 OTel entries match after
excluding manifest/Maven descriptors and ZIP timestamps. All 132/9/5 Java source
entries also match. The eight reactor/fixture POM differences are coordinate-only;
production Java/resources are unchanged. This supports the limited native/JMH
reuse above without pretending the old binary was compiled with the new version.

| Unsigned full-build binary | SHA-256 |
|---|---|
| Starter `4.4.2` | `471c3baeb5a48a8aa4f3dab533144387e61b857cca170d065f7767cdbda8d602` |
| Test helper `4.4.2` | `aae6245fb54ec5400eeebc23f0bb4d45c5720cf68ea897710872242e936596ac` |
| OpenTelemetry `4.4.2` | `54ea475466b4ad6f63c810205a7d4e0358636b1b835bcaaadc08e4d3bb2c4643` |

These are retained unsigned build hashes, not promises about subsequently signed
or published files. A clean candidate commit/tag and its provenance remain a
Priority 10.3 gate; the source/patch archive does not create that commit.
The local `SHA256SUMS` seals commands, failures, artifacts, reports, generated
readiness and `final-source/` (including this decision); verification uses
`sha256sum --quiet -c SHA256SUMS` from the bundle directory. `git diff --check`
also passes. Review and commit this preparation before recording final clean
candidate provenance; do not overwrite the prior evidence bundles.

### Reproduction

Run from the reviewed candidate tree with JDK 21 and Maven 3.9.9. Set `REPO` to a
writable Maven repository; the recorded run used
`target/v33-native-runs/native-g0ynw95x/repository`. The evidence runner's exact
commands and exit statuses are retained, but reproduction uses tracked commands:

```bash
export MAVEN_OPTS='-Xmx512m -XX:ActiveProcessorCount=2'
REPO="$PWD/target/v33-native-runs/native-g0ynw95x/repository"
mvn -B -ntp -s .mvn/maven-central-settings.xml -Dmaven.repo.local="$REPO" -pl reactive-http-client-starter,reactive-http-client-test,reactive-http-client-otel clean
mvn -B -ntp -s .mvn/maven-central-settings.xml -Dmaven.repo.local="$REPO" -DargLine=-XX:+DisableExplicitGC install
bash scripts/verify-generation-packaging.sh 4.4.2
```

For strict comparisons, use two fresh repositories, seed only third-party
dependencies if desired, and do not install candidate project artifacts into
either baseline repository before the comparison:

```bash
mvn -B -ntp -s .mvn/maven-central-settings.xml -Dmaven.repo.local="$PWD/target/published-baseline-repositories/v33-p10-root-4.4.1" -Papi-compatibility -DskipTests verify
bash scripts/verify-published-baseline-provenance.sh v33-p10-root 4.4.1 target/release-evidence/v33/priority10/api-root/provenance reactive-http-client reactive-http-client-starter reactive-http-client-test reactive-http-client-otel
mvn -B -ntp -s .mvn/maven-central-settings.xml -Dmaven.repo.local="$PWD/target/published-baseline-repositories/v33-p10-starter-4.4.1" -pl reactive-http-client-starter -Papi-compatibility -DskipTests verify
bash scripts/verify-published-baseline-provenance.sh v33-p10-starter 4.4.1 target/release-evidence/v33/priority10/api-starter/provenance reactive-http-client-starter
MAVEN_OPTS="$MAVEN_OPTS -Dmaven.repo.local=$REPO" bash scripts/verify-current-consumer.sh
```

The consumer verifier overrides that preliminary Maven cache with its own fresh
candidate repository. For genuine Boot 4.1 consumer parents and both JVM/AOT rows,
the tracked runner creates the fixture overlays; use a new evidence directory
on each rerun:

```bash
python3 scripts/verify-v33-parity.py consumer41 --repository "$REPO" --evidence target/release-evidence/v33/priority10/parity/rows
python3 scripts/verify-v33-parity.py minimal41 --repository "$REPO" --evidence target/release-evidence/v33/priority10/parity/rows
python3 scripts/verify-v33-parity.py jvm40 --repository "$REPO" --evidence target/release-evidence/v33/priority10/parity/rows
python3 scripts/verify-v33-parity.py jvm41 --repository "$REPO" --evidence target/release-evidence/v33/priority10/parity/rows
mvn -B -ntp -s .mvn/maven-central-settings.xml -Dmaven.repo.local="$REPO" -pl reactive-http-client-starter -DargLine=-XX:+DisableExplicitGC -Dtest=DocumentationReleaseArtifactTest,V33GuidanceExampleTest test
```

### Retained Failures

The initial 82-case documentation/example run had two documentation assertion
failures: stale release-cut benchmark wording and a new assertion that omitted
the Markdown bold marker. Both were corrected; all four example cases passed
even in that first run. Its XML/log remains in `docs-initial/`.

The first current-consumer command exited 1 before creating its evidence
directory and produced an empty log. The cause was not established; a separate
version-evaluation diagnostic returned `4.4.2` with exit 0. The retry explicitly
selected a writable cache for the preliminary Maven lookup and passed. Both
attempts are retained; the retry's actual consumer artifacts still came from a
fresh repository. No production behavior or safety assertion was weakened.

## Remaining Gates

| Gate | State |
|---|---|
| Patch scope | Selected by the maintainer; F001/F002/F003 only |
| Candidate coordinates | `4.4.2`; public/API/benchmark baselines remain `4.4.1` |
| Final reviewed clean commit and matching tag | Pending; do not tag or publish a dirty preparation tree |
| Signed artifacts/staged preflight | Pending; unsigned verification cannot satisfy signature checks |
| Publication workflow / Central | Pending; no deploy, tag push or Central verification performed |
| Published assembled consumer | Pending; must resolve `4.4.2` independently, not installed candidate artifacts |
| V33 closure | Pending until publication-path evidence passes; no-release closure was not selected |

After review/commit, follow the [release preflight](../../docs/20-native-release-compatibility.md).
Authorize signing locally; never put a passphrase in commands or evidence.
After actual publication, the separate verification commands are:

```bash
bash scripts/verify-published-release-artifacts.sh 4.4.2
bash scripts/verify-published-consumer.sh 4.4.2
```

These are future gates, not commands run or successful outcomes. Baselines,
public installation examples and archive/readiness status must advance together
only after verified publication. Candidate readiness must say selected patch,
pending-publication, unpublished and activeRoadmap=v33, not GO for deployment.
