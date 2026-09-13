# V31 Release Review

## Decision

**2026-09-13: GO for the `4.4.0` additive release candidate; publication pending.**
The two new public readers justify a minor release rather than a documentation
or patch-only disposition. Reviewed clean source:
`880eb800a783a640c062bb4712108235005bd817`. The candidate is that source plus
the recorded version/documentation/verification patch. Priorities 10.1-10.3
are complete; 10.4 and roadmap closure remain open. This is not permission to
publish an uncommitted tree or unsigned artifacts.

## Scope Freeze

Candidate: **4.4.0**, additive minor. Published, API, consumer and benchmark
baselines remain **4.3.0**. Signing and publication are not claimed.

The only runtime/public API additions are
`RequestContext.inboundHeaderValues(ContextView, String): List<String>` and
`RequestContext.inboundHeader(ContextView, String): Optional<String>`.
The [surface freeze](../../docs/20-native-release-compatibility.md#v31-additive-surface-freeze)
and [context examples](../../docs/09-correlation-id.md) define the same error,
absence, multiplicity and redaction contract. Existing bulk-map spelling,
string aliases, snapshot constructor/merge behavior and mock exact-name
assertions remain compatible. No metadata, configuration, schema, meter,
forwarding, trust or automatic propagation feature is added.

## Accepted Scope

| Priority | Delivered evidence and boundary |
|---|---|
| 1 | Independent published 4.3.0 baseline; V1-V30 remain archived |
| 2-3 | Case mismatch characterization and additive readers; no proven mesh context-loss defect |
| 4 | Real HTTP/1.1 and HTTP/2 WebFlux capture/filter ordering; no Istio deployment certification or MVC bridge |
| 5 | Explicit isolated handoff and terminal ownership; GC reachability lives in a controlled fork, not ordinary tests |
| 6 | Auth/cache/retry/redirect/deadline isolation and exact terminal reporting; no runtime composition change |
| 7 | Mock/current and minimal consumers, AOT and exact-source native smoke; optional integrations retain their documented limits |
| 8 | Version-scoped safe examples and guarded structural fixture; manual raw context writes remain application-owned |
| 9 | Strict API, complete dependency matrix and targeted allocation smoke; no public performance claim |

No accepted production feature is deferred. Broad implicit propagation, MVC,
header DTO parsing and mesh certification are excluded, not passing checks.
Release-quality benchmark commands remain optional and unexecuted under the
[no-public-performance-claim disposition](COMPATIBILITY-PERFORMANCE.md).
Smoke review flags do not establish performance parity or a resolved regression.

## Evidence Review

Evidence root: `target/release-evidence/v31/priority10/`. The clean starting
commit/status and portable source archive are in `reviewed/`. Priority 9's
source copies match that committed revision byte for byte; its complete report
inventory was rechecked before copying. The original matrix and benchmark
commands retain their snapshot/dirty-run provenance, not a retroactive clean
run claim. Earlier failures and successful retries remain separate.

| Reviewed evidence | Result | Path below the root |
|---|---|---|
| Java 21 / Boot 4.0.0 and 4.1.0 full matrix | 1,879 each: 1,736 starter, 78 helper, 62 OTel and three assembled-consumer cases; strict API checks pass on both rows | `reviewed/priority9/matrix/` |
| Negative API/provenance fixtures and packaging | Pass; includes source-only incompatibility and non-Central/mixed baseline rejection | `reviewed/priority9/` |
| Published 4.3.0 baseline | Four consumer tests; all 13 parent/module POM/JAR/source/Javadoc hashes and Central markers verified | `reviewed/priority9/published-consumer/`, `reviewed/priority1/`, `reviewed/published-artifacts/` |
| Targeted performance | 50 current / 26 baseline smoke rows; 26 matched, 24 helper-only, no baseline-only; allocation fields present | `reviewed/priority9/smoke-current/`, `smoke-baseline/`, `smoke-comparison.md` |
| Post-terminal retention | Five controlled-fork reachability cases pass; separate from transient allocation | `reviewed/priority9/retention-reports/` |
| Native smoke | Clean-source compile and executable pass, six fixture tests | `reviewed/native-clean/` |

The native input remains `f9b94fd207e6c5af1fc36ee047fd2c491a6c0e30`;
the source archive and commands are retained even across squashed history.
Production and native fixture Java are unchanged at the reachable reviewed
commit and in this candidate. The only native-POM change is the candidate
dependency coordinate. The executable hash was revalidated; it remains
snapshot-source native evidence, not a newly compiled `4.4.0` binary. Candidate
AOT output was rebuilt and run separately below. No mesh or broader optional
integration certification is inferred.

| Candidate check | Actual result | Path below `candidate/` |
|---|---|---|
| Full release-profile reactor with `-Dgpg.skip=true install` | 1,877 tests: 1,737 starter, 78 helper, 62 OTel; unsigned build/install passes | `reactor/` |
| Generation and artifacts | Binary/source/Javadoc guards pass; all 13 unsigned final-coordinate artifacts retained with embedded versions | `generation/`, `artifacts/` |
| Strict root and separate starter API | Both source/binary checks pass against independent fresh Central 4.3.0 repositories; only the two additive methods differ | `api-root/`, `api-starter/` |
| Assembled current consumer | 74 mock, 11 full and three minimal tests; no reactor output leakage or accidental Caffeine/OTel/helper dependency in the minimal client | `current-consumer/assembled/` |
| AOT package and JVM execution | Six fixture tests; generated-AOT JVM exits zero and emits `V31 inbound context: capture=3 restored=2 rejected=1; parity passed` | `aot-package/`, `aot-run/` |
| Benchmark module verification | 34 harness tests plus a repeated 1,737-case starter regression; no candidate-coordinate measurements promoted | `benchmarks/` |
| Support capture filters | Nine Python tests pass | `support/` |
| Final context/documentation/AOT regression | 203 tests, including 50 release-documentation cases, with explicit GC disabled | `final-docs/` |

All reported passing suites have zero failures, errors and skips. The initial
readiness run passed 85 cases before the new release-state test was added;
subsequent suites include it. Closure-note validation is retained separately
under `candidate/closure-docs/`, without replacing those earlier reports.

Maven 3.9.9, GraalVM JDK 25.0.3, Java target 21, default Boot 4.0.0;
settings `.mvn/maven-central-settings.xml`. The retained matrix used Oracle
JDK 21.0.8 and records its actual dependency versions in each row. Native
compilation used GraalVM 25.0.3, shared-arena support, a 4 GiB compiler heap and
two compiler threads. Stage commands, timestamps, exit codes, source patches,
fresh XML and generated readiness are retained. This preparation did not run
signing, tagging, publication or another native compile.

`audit.json` validates totals, readiness, source equivalence and artifact hashes.
`SHA256SUMS` inventories the assembled bundle with relative paths; verify it
from the evidence root using `sha256sum -c SHA256SUMS`. Preserve the bundle
outside `target/` before a root clean. Original nested inventories retain their
historical paths. Priority 1 artifact files had moved during Priority 9 target
preservation; their 13 original hashes were checked at the preserved location
and copied here, without changing the original provenance or claiming a new
Central download.

Stable integrity anchors:

| Artifact | SHA-256 |
|---|---|
| Clean reviewed `source.tar` | `4fd591b7c7722fd9cb3aa7b75e2b05e8bad16b8068f806eb524d488d872bca30` |
| Original Priority 9 report inventory | `06a0de2828d1485ebbf3565439fa7dae86cea2f36eb385d836076f06587421e6` |
| Verified native executable | `c074e8f5bc3a340f9a8d982136fcc5b752a74c1bf9952c7156e26bd9be1fa5ed` |

Generated readiness selects `release-candidate`, `plannedFinalVersion=4.4.0`,
`published=false`, and the 4.3.0 published/API baseline. It intentionally does
not ingest external command results: its conservative manual checks remain
pending, while this review and the inventory identify actual completed runs.
A target-only manifest is not signing, final-tag or publication evidence.

## Publication Boundary

Priority 10.4 remains open. Commit and review the final preparation patch before
tagging. Build/sign and verify the exact final artifacts through the existing
credentialed publication workflow, including staged assembled consumption and
generation packaging before deployment. An unsigned candidate build or scope
GO is not successful signing. Do not move baselines or archive V31 until fresh
Central artifact and published-consumer verification succeeds.

Optional local signing preflight, with the passphrase entered only locally:

```bash
export GPG_TTY="$(tty)"
mvn -ntp -s .mvn/maven-central-settings.xml -Prelease -DskipTests verify
bash scripts/verify-publishable-artifacts.sh 4.4.0
bash scripts/verify-generation-packaging.sh 4.4.0
```

These commands do not deploy. Local signing intentionally omits Maven batch
mode so pinentry can prompt. No credential or signing-agent configuration is
changed by this release preparation.

Remaining risks: absence/redaction and singleton validation do not establish
trust; applications still own value size/schema checks and explicit handoff.
Arbitrary raw maps may expose unstable iteration order. Native and loopback
evidence is bounded to the tested fixtures. Smoke measurements make no latency,
allocation, retained-memory or deployment-performance guarantee. Successful
credentialed signing, staged consumption, the reviewed clean final commit/tag,
publication and fresh Central verification are mandatory before 10.4 closes.
