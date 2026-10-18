# V37 Baseline and Narrow Finding Ledger

> **Recorded:** 2026-10-18
> **Published baseline:** `4.4.2`
> **Development coordinate:** `4.5.0-SNAPSHOT`
> **Experiment authorization:** none; no inherited budget
> **Implementation and release scope:** unselected

This establishes [Priority 1](CHECKLIST.md), not attribution feasibility, a
performance pass or a release decision. V37 is active; V1-V36 stay closed.
Readiness is `activeRoadmap=v37`, `plannedFinalVersion=null`,
`releaseLane=unselected`. Public, API, consumer and benchmark baselines remain
`4.4.2`; no version or dependency change is selected.

[V36-P10-1](../v36/RELEASE-DECISION.md) remains an explicitly reduced-scope,
review-only/no-release closure. A001/AUTO_REGISTRY and A002/HOOK were excluded
from closure obligations, **not passed**. V37 tracks them independently again
without rewriting V36's results or reinstating any rejected optimization.

## Source and Reachability

The inventory began on clean `7e67a7ccec5ef8b0100d4b559d91e34ad8b8c2fc`, tree
`27293e32b6b7919060d35901c87b8004f1c9d93a`. Fresh verification uses that reachable
base plus the preserved documentation/test patch, including this new file, not
a newly committed clean revision. The audit records all V1-V36 tree identities.
No runtime, fixture, dependency, version or historical roadmap edit is made.

| Source | Fresh inventory and permitted use |
|---|---|
| `v4.4.2`, `bdacfc439b7fab7df1b319d057782c3b69ce9676` | Reachable published tag; starter/helper/OTel main trees still match exactly |
| `f38fb8d22486d35401a7af9cb9e80f5e5e8c75ef` | Reachable V35 P9 runtime/native source; historical execution only |
| `beb8a237215529c9c8c7ddc285295c9564e4bce0` | Recorded V35 P10 execution object is available locally but **not an ancestor** |
| `85948837c4f4001f648b15adb3efd80b9de05abd` | Reachable V35 P10 integration; this does not rename the original execution source |
| `5f160cccdff9b6116fdf74d64f8d85a47ed75150` | V36 closure's recorded base is available locally but **not an ancestor** after integration |
| `0574d892603b30fec903deee79211464a1fe86c8` | Reachable V36 closure integration; preserved records retain their original base-plus-patch provenance |

All **392 runtime/build/test/fixture inputs** in V36 P8's saved inventory rehash
against this checkout. Both V35 P10 inventories' **39 harness/build inputs** also
match. This establishes unchanged input content for later applicability review,
not new API, consumer, native, runtime or performance verification. Non-ancestor
objects are not clean-clone prerequisites; obtain their verified source/patch
evidence or report it missing rather than inventing a reachable execution commit.

## Available Evidence and Limits

The read-only audit verifies **5,132 files across 15 bundles**, exact manifest
coverage and every archived file against previously tracked seals. The original
bundles and archives are unchanged. Locations below are ignored local evidence,
not content delivered by a source clone or an off-machine backup.

| Inventory | Verified files | Trusted seal source and reuse limit |
|---|---:|---|
| V35 P9-P12, including P12 preparation | 3,274 | First five rows of [V36's catalog](../v36/MAINTAINER-GUIDANCE.md#evidence-inventory); historical runtime/native, scores, diagnostics, guidance and closure |
| V36 P1-P8 | 1,484 | Remaining catalog rows; audits, approvals, ordinary/intervention records, no-change decision and verification reuse |
| V36 P9 | 229 | [Closure inventory](../v36/RELEASE-DECISION.md#source-and-evidence-inventory); preservation/guidance, not a replacement for underlying raw archives |
| V36 P10 | 145 | [Final checklist seal](../v36/CHECKLIST.md); explicit reduced-scope closure and final checks |

V36 P10 manifest SHA-256 is
`65ff8f3b9ecd37febd6096f7f08aa925d5ac0ebe14c2d8ff9757143ee6283b0b`;
archive `verification-evidence/v36-priority10-20261016.tar.gz` SHA-256 is
`fe2aabfcae053661c49a25c81ff558f8ff0fa77d264f92c0143a9a5f135eccdf`.
The fresh audit retains all 15 exact directory/archive/hash pairs, commands,
exits, manifest copies and results. Integrity does not establish causal correctness.

Both saved shaded benchmark JARs remain present and rehash:

| V35 P10 artifact | SHA-256 |
|---|---|
| Baseline shaded benchmark | `e0de82156b03f42c986f10fd9a66229956df64189e5de605b345affccdd48d6c` |
| Current shaded benchmark | `b9fd5b42980c03cd8c0415f68101aef553e266354b672ea4f3b3ca992a3376b9` |
| Baseline input inventory | `704233da818cf56c31435170538d2dc523d41d93c06c28cc725d1cd8cea76dd6` |
| Current input inventory | `56dd6782d72e40e25de6f0a12b6bec8b2f9e8cbe6f74dc60a82958307152481f` |

Each historical classpath lists **121 JAR paths**, all now absent at the recorded
`/tmp/rhttp-v35-p10-20261011/` locations. Saved dependency hashes and shaded JARs
are available; standalone starter/dependency availability at those old paths is
**not verified**. Do not claim the historical launch environment remains runnable
or silently replace it with the fresh general Maven cache. Any restored inputs
need hash verification; resolving or rebuilding them creates new provenance.

The measured stack remains Java 21.0.8 / Boot 4.0.0 / JMH 1.37. Saved Central-only
starter provenance, raw primary/reverse samples, compiler records, commands and
failures remain in their sealed bundles. This is not fresh Central publication,
signature verification or new scoring. The original 60 primary rows and four
reverse rows per artifact retain their three primary allocation flags; clear
reverse aggregates and V36 diagnostics do not replace those scores.

Lost V35 P2-P8 recordings and the earlier lost P9 attempt remain unavailable.
The original V33 publication bundle is absent. Rehashed inventories cannot
reconstruct those recordings or all-module publication evidence. No regeneration
was performed; later restoration or regeneration must state its new provenance.

## Fresh Toolchain and Dependencies

Baseline checks use Oracle Java 21.0.8 / Maven 3.9.9, a 512 MiB Maven heap, two
active processors and explicit GC disabled in ordinary test forks. Effective
starter POMs and dependency trees are freshly captured for both supported rows;
test classpath hashes are separate from historical measured dependencies.

| Component | Boot 4.0.0 | Boot 4.1.0 |
|---|---|---|
| Spring Framework | 7.0.1 | 7.0.8 |
| Reactor / Reactor Netty | 3.8.0 / 1.3.0 | 3.8.6 / 1.3.6 |
| Netty | 4.2.7.Final | 4.2.15.Final |
| Jackson 3 | 3.0.2 | 3.1.4 |
| Micrometer | 1.16.0 | 1.17.0 |
| Resilience4j | 2.4.0 | 2.4.0 |
| Caffeine | 3.2.3 | 3.2.4 |

The installed Java launcher hash is
`2b9272f8d8ec174cdc2679ec62bcbc8cd785b095a511498ec80392b16accc543`,
matching the recorded launcher. Fresh `release`, `lib/modules` and
`lib/server/libjvm.so` hashes are retained; libjvm is
`7ccd2a82c8231b70cc3db61f8cb0073c42e1fd4f80aee30477b9e6c95499328a`.
Hashing and ordinary correctness tests are not diagnostic JVM experiments.
Boot 4.1 correctness verification is not a matched cross-stack cost comparison.

## Narrow Finding Ledger

### V37-A001 / AUTO_REGISTRY

- **Owner:** Performance maintainer with metrics-integration review.
- **Status:** Unresolved/blocking; no implementation selected.
- **Evidence:** [V36 cost review](../v36/COST-ACCEPTANCE.md), rehashed V35 primary/
  reverse samples and V36 ordinary/intervention records. Ordinary high-mode
  compiler attribution is missing; forced non-inlining is not the original cause.
- **Controls:** Actual registry-selected warm GET publisher, same-artifact mode
  comparisons and possible MINIMAL/AUTO_NO_REGISTRY controls; no execution selected.
- **Question:** Can a materially different method identify ordinary high-mode
  executing code without removing the mode? Existing envelopes are insufficient.
- **Proof obligation:** Independently associated ordinary high/low evidence and
  causal disposition, or a reproduced, approved and verified correction. Retain
  missing evidence as unresolved; do not transfer a control profile's explanation.
- **Stop conditions:** Priority 2.3 feasibility decision before proceeding to a
  plan, 4.3 numerical approval before any worker/probe/pilot, 7.2 before production
  edits and Priority 10 before scope exclusion/release/closure. Failure, absent
  modes or exhausted bounds grant no retry.

### V37-A002 / HOOK

- **Owner:** Performance maintainer with observer/hook review.
- **Status:** Unresolved/blocking independently of AUTO_REGISTRY.
- **Evidence:** [Ordinary capture](../v36/ORDINARY-CAPTURE.md) observed untraced
  high modes on both artifacts, not in the traced lane. [Intervention results](../v36/INTERVENTION-RESULTS.md)
  test a mechanism but do not establish ordinary high-mode causality.
- **Controls:** Genuine hook discovery/composition, ordering and terminal behavior;
  same-artifact low/high observations, not a no-op substitute or transport workload.
- **Question:** Can high-mode behavior be attributed without instrumentation
  suppressing it? Distinguish compiler variability from workload/phase mismatch.
- **Proof obligation:** HOOK-specific ordinary causal evidence and its own
  explained/fixed/unresolved disposition, plus combined cost acceptance. A001,
  AUTO_NO_REGISTRY or clear confirmation results alone cannot satisfy this row.
- **Stop conditions:** The same 2.3/4.3/7.2/Priority 10 gates apply independently;
  no useful method means stop before measurement, not automatic closure.

### V37-E001 / Attribution Feasibility and Evidence Limits

- **Owner:** Benchmark tooling maintainer.
- **Status:** Feasibility pending; not a newly reproduced defect. Preserve V36's
  verified process association/corrections within their documented limits.
- **Evidence:** [V36 evidence audit](../v36/EVIDENCE-AUDIT.md), capture records,
  [preservation catalog](../v36/MAINTAINER-GUIDANCE.md#evidence-inventory) and the
  rehashed inputs. Legacy pairing, exact executing-code identity and individual
  internal-probe census remain unknown, not retrospectively repaired.
- **Controls:** Existing capture/review/verifier tooling and bounded synthetic
  missing/duplicate/truncated/mismatched/reordered/superseded records in Priority 3.
- **Question:** What minimum new observation could refute an ordinary-mode
  explanation, with tolerable perturbation and complete process accounting?
- **Proof obligation:** A credible, materially different method or explicit stop
  decision; any claimed tooling defect needs a failing reproduction and affected
  evidence before stronger conclusions. Inferred counts are not a process census.
- **Stop conditions:** Incomplete/ambiguous inputs cannot be promoted as proof;
  no diagnostic smoke/preflight before 4.3 and no automatic experiment extension.

V35's **338 workers** and V36's **48/48 workers** are exhausted. The accepted
**96 inferred internal probes / 194 accounted JVM invocations** are not an
observed census or a V37 allowance. Priority 1 launches **zero new workers** and
selects no numerical budget. C001-C005 and both C004 subrows retain their V35
no-change outcomes. Priority 2 must decide feasibility; Priorities 4.3, 7.2 and
10 remain independent approvals. A no-change or no-release decision alone does
not resolve these findings.

## Verification and Reproduction

The read-only inventory, commands/exits, source state, hashes, effective inputs
and fresh reports are under `target/release-evidence/v37/priority1/`. Final
results and the verified durable copy/archive seal are recorded in
[CHECKLIST.md](CHECKLIST.md). Preserve ignored bundles before cleanup; no root
clean is used, and no off-machine backup is claimed.

Reproduce ordinary guards from the repository root with a writable general Maven
cache, not an isolated Central-baseline repository:

```bash
export JAVA_HOME=/usr/lib/jvm/jdk-21.0.8-oracle-x64
export PATH="$JAVA_HOME/bin:$HOME/.sdkman/candidates/maven/current/bin:$PATH"
export MAVEN_OPTS='-Xmx512m -XX:ActiveProcessorCount=2 -Dmaven.repo.local=/tmp/v37-draft-maven-repository'
mvn -B -ntp -s .mvn/maven-central-settings.xml validate || exit
bash scripts/verify-published-baseline-fixtures.sh || exit
bash -n scripts/verify-supported-matrix.sh || exit
python3 -B -m unittest discover -s scripts -p 'test_v36_evidence.py' -v || exit
for BOOT in 4.0.0 4.1.0; do
  mvn -B -ntp -s .mvn/maven-central-settings.xml -Dspring-boot.version="$BOOT" \
    -pl reactive-http-client-starter -Dtest=DocumentationReleaseArtifactTest \
    '-DargLine=-XX:+DisableExplicitGC -XX:ActiveProcessorCount=2' test || exit
done
git diff --check
```

Use [the existing archive verifier](../../scripts/verify-v36-evidence.py) with
each trusted catalog/checklist seal; the preserved audit driver records exact
commands for all 15 bundles without modifying or extracting them. The local
Maven path above is not a portable installation requirement. Dependency refresh
is new verification, not restoration of missing historical classpaths.

No fresh full-module, API, assembled-consumer, AOT/native, scored performance,
collection, signing or publication run is claimed. B/op is not retention or RSS;
no snapshot-upgrade remedy or application/mesh diagnosis follows. Shared evidence
contains bounded synthetic data, not production bodies, header values, identities,
cache keys, request targets, arbitrary error text or credentials.
