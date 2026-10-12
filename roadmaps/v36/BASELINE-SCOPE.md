# V36 Baseline and Narrow Finding Ledger

> **Recorded:** 2026-10-12
> **Published baseline:** `4.4.2`
> **Development coordinate:** `4.5.0-SNAPSHOT`
> **Experiment authorization:** unselected; Priority 3.3 approval required
> **Implementation and release scope:** unselected

This establishes [Priority 1](CHECKLIST.md), not an allocation explanation,
optimization or release decision. V36 is active; V1-V35 stay closed. Readiness is
`activeRoadmap=v36`, `plannedFinalVersion=null`, `releaseLane=unselected`.
[V35's approved closure](../v35/RELEASE-DECISION.md) remains reduced-scope,
review-only/no release. Its 10.2 heading and unresolved-profile checkbox remain
unchecked: exclusion is **not a performance pass**.

## Reachable Source and Unchanged Inputs

The inventory began on clean commit `728653e4fb3db911cddd362ab0c22708619bb68f`,
tree `9659c4869caa102593ebde7c05c5b5b0cd20730d`. Fresh verification is that base
plus the separately retained documentation/test patch, not a new clean build.
The audit records the initial empty status and each V1-V35 roadmap tree identity.
No production, dependency, fixture, version or historical roadmap edit is made.

| Source | Reachability and permitted use |
|---|---|
| `v4.4.2`, `bdacfc439b7fab7df1b319d057782c3b69ce9676` | Reachable published tag; starter/helper/OTel main trees remain identical |
| `f38fb8d22486d35401a7af9cb9e80f5e5e8c75ef` | Reachable P9 native/parity source |
| `beb8a237215529c9c8c7ddc285295c9564e4bce0` | P10 recorded build source exists locally but is **not an ancestor**; do not require it in a clean clone |
| `85948837c4f4001f648b15adb3efd80b9de05abd` | Reachable P10 integration; the compared runtime, fixture and harness inputs match |
| `3ccbeb73947517a0eea5d1e33a88a656ffb2c4d6` | Reachable P11 source and P12 clean base; closure used its recorded dirty patch |
| `9253d4d6ca62f405bb807a61f848180b3a973d95` | Reachable V35 closure; preserved without rewriting results |

The root/module POMs, `.mvn`, `.github`, starter/helper/OTel/benchmark main trees
and V33 parity/native runners match the reachable P9/P10/P11/V35 closure inputs.
All 39 P10 harness/build input file hashes also match the current checkout.
This permits unchanged-input reuse of those specific runtime results; it does
not make historical test/documentation output a test of the new V36 records.
The original P10 execution source stays labeled as recorded, not relabeled as
the later integration commit.

## Available Evidence

The fresh audit rehashed **3,274** files across five bundles, checked manifest
coverage without extra/missing files, and verified all five archive hashes.
These are local ignored records under `verification-evidence/`, not files a clean
clone receives. P12 preparation remains a separate pending-decision checkpoint;
the final P12 record contains the subsequent approved closure.

| V35 bundle | Files | SHA-256 of `SHA256SUMS` |
|---|---:|---|
| `priority9` | 1,811 | `e1f1ee5bb1004963a1c2aca7a4faa28da67193b8101af9ab522e80b0340599c0` |
| `priority10` | 931 | `62e4375ebb17f9d4591449b86b67f16795ab126d4419c24616af37435bb33ba4` |
| `priority11` | 210 | `b8d580e78d3bcf8c24b8b14371d7a80310f688dd49731f1727c4efdbc6ee3af0` |
| `priority12-preparation` | 109 | `4570d4129101658cf2c54747cf63e23ea021d1023c65962b21d54d06c78077e6` |
| `priority12` | 213 | `073353a4aa26c735f8af4e1678480f250f4adab1b16d17c741614a3c542358a9` |

Their dated archives are `v35-priority<N>-20261011.tar.gz` (including
`12-preparation`). Revalidated archive SHA-256 values, in the same order:

```text
ca124e7ee2c18e999ba5c089b8cc2a488d71997dca49fc93e62fc81e42e0b8ba
ee8f62205902af433aeb3da5b8d08203c22e0026af2664c9c6b89150a8e109e0
5ebf81ad9e39de30167d76a2d0c533be34725f22eb8237770291566060aa97bf
26b04de5e085e0b1d439e239099a1874c9fbd34ec6217f785914b2ab985a3a3c
9a0a5554993d4178b68161a777f0f67028d5620679ff83c7acc897c92f54674a
```

| Input | Fresh revalidation and limit |
|---|---|
| P9 JVM/AOT/consumer/native records | Bundle and native binary match; binary SHA-256 `66de72017d7c8e8a33e1a4a593f55a0736813ffb479d88ccd3bbd7ec77bd7271`. This is not a new native compile, execution or consumer run |
| P10 benchmark baseline/current | Both saved shaded JARs and all 121 classpath JARs per artifact rehash; 120 non-starter dependencies per side remain available. No benchmark or diagnostic fork was launched |
| P10 published provenance | Saved Central-only `4.4.2` starter POM/JAR inventory and remote markers rehash; inventory SHA-256 `897cb8d6fd0cb8838215d38ee1bff8d811e1a2a384fcb6bed909c5ca0f7534bf`. This is not fresh Central verification or a renewed signature/publication check |
| P10 API and allocation records | Original strict/API and score records are preserved, not rerun. Sixty primary rows/artifact, zero latency and three allocation flags, with four clear reverse rows; flags are not erased |
| P11/P12 guidance and closure | Original commands, source patches, tests and decisions preserved. Their overlapping case totals are not added to current verification |

Artifact SHA-256 inventory:

| Artifact | SHA-256 |
|---|---|
| Published starter `4.4.2` | `fb8646ce2f6ed172598ff446cdfa9da2f348c0fd56a2b4c9a02e7ac1dae803c1` |
| P10 current starter `4.5.0-SNAPSHOT` | `c3746166dc136fe56235fe5aea2f2b603ddc0d2781c00b06ad8e9f670e57076f` |
| P10 baseline shaded benchmark | `e0de82156b03f42c986f10fd9a66229956df64189e5de605b345affccdd48d6c` |
| P10 current shaded benchmark | `b9fd5b42980c03cd8c0415f68101aef553e266354b672ea4f3b3ca992a3376b9` |
| Baseline/current harness input inventories | `704233da818cf56c31435170538d2dc523d41d93c06c28cc725d1cd8cea76dd6` / `56dd6782d72e40e25de6f0a12b6bec8b2f9e8cbe6f74dc60a82958307152481f` |

P2-P8 historical raw recordings lost during the V35 root-clean incident are
**not recovered**. Rehashed P9-P12 records do not recreate rejected candidates.
Missing bundles require restored verified copies or newly approved regeneration
with new provenance; a manifest or reachable source alone cannot replace a run.
Hash agreement establishes integrity, not causal correctness. Fork/trace
association remains the Priority 2 review; this baseline does not endorse its
timestamp-based mapping or infer an ordinary high-mode trace that is absent.
The original V33 full-publication bundle is also absent from the local durable
inventory. Its tracked publication record is historical context only; the
available P10 starter POM/JAR provenance is narrower than all-module signatures,
sources/Javadocs or published-consumer verification.

## Toolchain and Effective Dependencies

Fresh guards use Oracle Java 21.0.8, Maven 3.9.9, a 512 MiB Maven heap and two
active processors; ordinary test forks have explicit GC disabled. No dependency
upgrade is selected. Effective starter POMs, trees, classpaths and JAR hashes for
both Boot rows are captured freshly, without rebuilding benchmark artifacts.

| Component | Boot 4.0.0 | Boot 4.1.0 |
|---|---|---|
| Spring Framework | 7.0.1 | 7.0.8 |
| Reactor / Reactor Netty | 3.8.0 / 1.3.0 | 3.8.6 / 1.3.6 |
| Netty | 4.2.7.Final | 4.2.15.Final |
| Jackson 3 | 3.0.2 | 3.1.4 |
| Micrometer | 1.16.0 | 1.17.0 |
| Resilience4j | 2.4.0 | 2.4.0 |
| Caffeine | 3.2.3 | 3.2.4 |

P10's measured stack remains Java 21.0.8 / Boot 4.0.0 / JMH 1.37. The Java
launcher hash matches its recorded `javaSha256`:
`2b9272f8d8ec174cdc2679ec62bcbc8cd785b095a511498ec80392b16accc543`.
Fresh `release`, `lib/server/libjvm.so` and `lib/modules` hashes are captured as
new inventory, not asserted as historical full-JDK hashes. The fresh libjvm hash
is `7ccd2a82c8231b70cc3db61f8cb0073c42e1fd4f80aee30477b9e6c95499328a`.
The Boot 4.1 guard row is not a matched cross-stack performance comparison.

## Narrow Finding Ledger

### V36-A001 / AUTO_REGISTRY

- **Owner:** Performance maintainer.
- **Status:** Unresolved/blocking for full-scope acceptance; not an approved fix.
- **Evidence:** [V35 final acceptance](../v35/FINAL-ACCEPTANCE.md) and rehashed
  P10 primary, confirmation and profile-follow-up artifacts. Ordinary follow-up
  traces were low mode only; forced resolver non-inlining reproduced about
  256 B/op but did not establish the original ordinary high-mode cause.
- **Controls:** Same-artifact low/high comparisons; possible MINIMAL and
  AUTO_NO_REGISTRY controls. Preserve actual registry/observer selection and
  the warm GET publisher phase. Priority 2 must verify the effective workload.
- **Open questions:** Which compilation/code version ran in the high-scoring
  fork? Does tracing perturb it? Is the fork/trace association reliable?
- **Acceptance:** Independently associated ordinary high/low evidence and a
  causal disposition: explained without production change, or a reproduced,
  approved and verified correction. Absent high mode leaves this row unresolved.
- **Stop conditions:** No execution before Priority 3.3; stop at the frozen
  budget or evidence/resource failure. No retry permission follows from absence
  of a mode. No production edit before Priority 6.2; no closure before Priority 10.

### V36-A002 / HOOK

- **Owner:** Performance maintainer with observer/hook review.
- **Status:** Unresolved/blocking independently of AUTO_REGISTRY.
- **Evidence:** The same preserved P10 lanes, evaluated for HOOK itself. Its
  ordinary follow-up traces were low mode only; forced non-inlining and another
  profile's explanation do not resolve the missing ordinary high-mode evidence.
- **Controls:** Matched HOOK low/high observations with real hook discovery and
  composition intact; possible MINIMAL/AUTO_NO_REGISTRY controls. Do not replace
  the hook profile with a no-op path or confuse assembly with terminal delivery.
- **Open questions:** Which installed compiler state explains HOOK's observed
  delta? Can it be observed without instrumentation removing the mode? What
  evidence would distinguish a workload mismatch from compiler variability?
- **Acceptance:** HOOK-specific causal evidence under verified workload/fork
  identity, with its own explained/fixed/unresolved disposition. A001 acceptance
  or clear reverse scores alone cannot satisfy this row.
- **Stop conditions:** The same Priority 3.3 budget/stop and Priority 6.2 edit
  gates apply independently. Missing evidence remains blocking; no automatic
  extension and no full-scope closure without disposition or explicit exclusion.

### V36-E001 / evidence reliability

- **Owner:** Benchmark tooling maintainer.
- **Status:** Review pending; not a confirmed defect.
- **Evidence:** Rehashed P10 command/result, raw samples, class-load/compiler
  traces, source manifests and the tracked
  [follow-up runner](../../scripts/investigate-v35-final-profiles.py) and
  [allocation reviewer](../../scripts/review-v35-allocation.py).
- **Controls:** Priority 2 deterministic synthetic fixtures for reordered,
  missing, duplicate, truncated and mismatched records; unambiguous artifact,
  profile, process and trace identity, including superseded compilation tasks.
- **Open questions:** Does association depend only on timestamps? Can the saved
  logs establish installed code during the measurement interval? Which
  historical conclusions need qualification if that evidence is unavailable?
- **Acceptance:** Document verified associations and limits; reproduce any
  actual tooling defect with a failing fixture before a bounded correction.
  Re-evaluate affected conclusions without changing original raw observations.
- **Stop conditions:** Ambiguous/incomplete input cannot be promoted as proof.
  No new fork is authorized by a parser concern; require Priority 3.3 approval
  for regeneration and a separate scope decision for unrelated defects.

MINIMAL, AUTO_NO_REGISTRY and RESILIENCE_ENABLED_ONLY are possible controls only,
not an approved schedule or reopened findings. C001-C005 and both C004 subrows
retain V35's no-change outcomes. The original 302 plus approved 36-fork extension
total 338 forks; that authorization is exhausted. P1 grants zero new forks and
does not choose a production correction, release or scope exclusion.

## Verification and Reproduction

Commands/exits, actual XML counts, dependency inventories, clean source and dirty
patch are retained under `target/release-evidence/v36/priority1/`, with a verified
durable copy at `verification-evidence/v36-priority1/` and a dated archive. The
checklist records the final seal. Preserve these ignored copies before cleanup;
they do not ship with source. No root clean was used.

The new regression first fails because this baseline record is absent. Final
checks cover this ledger and approval gates, archive continuity, version guards,
generated readiness and local links. Existing baseline-provenance positive and
negative fixtures run without treating their synthetic artifacts as publication.
Final verification passes 108 documentation cases per Boot row (4.0.0 and 4.1.0),
zero failures/errors/skips, plus reactor validation, provenance fixtures, matrix
script syntax and `git diff --check`. The initial failing guard is preserved,
not counted as a passing test. Overlapping reruns are not additional cases.

Reproduce from the repository root with an available writable general Maven cache
(not an isolated Central baseline repository):

```bash
export JAVA_HOME=/usr/lib/jvm/jdk-21.0.8-oracle-x64
export PATH="$JAVA_HOME/bin:$HOME/.sdkman/candidates/maven/current/bin:$PATH"
REPO=/tmp/rhttp-v35-p10-20261011/repository-current
export MAVEN_OPTS="-Xmx512m -XX:ActiveProcessorCount=2 -Dmaven.repo.local=$REPO"
mvn -B -ntp -s .mvn/maven-central-settings.xml validate
bash scripts/verify-published-baseline-fixtures.sh
bash -n scripts/verify-supported-matrix.sh
for BOOT in 4.0.0 4.1.0; do
  mvn -B -ntp -s .mvn/maven-central-settings.xml -Dspring-boot.version="$BOOT" \
    -pl reactive-http-client-starter -Dtest=DocumentationReleaseArtifactTest \
    '-DargLine=-XX:+DisableExplicitGC -XX:ActiveProcessorCount=2' test || exit
done
git diff --check
```

Stop on failed commands and preserve their outputs. Effective inputs can be
recaptured with `help:effective-pom`, `dependency:tree` and
`dependency:build-classpath` using the same Boot/repository selection and fresh
output paths. The sealed audit retains exact commands and all input hashes.
No fresh full-module, API comparison, assembled consumer, AOT/native, scored
performance or collection run is claimed. B/op is not retention or pod RSS;
no snapshot-upgrade or mesh diagnosis follows from this baseline.
