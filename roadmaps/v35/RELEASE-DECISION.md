# V35 Conditional Release and Closure Decision

> **Status:** completed; review-only, no release; explicitly reduced scope
> **Recorded:** 2026-10-11
> **Published / development:** `4.4.2` / `4.5.0-SNAPSHOT`
> **Delivered production changes:** none
> **Scope reduction:** AUTO_REGISTRY and HOOK final-combination allocation flags excluded from closure, not resolved
> **Release scope:** no release; V35 closed

This closes [Priority 12](CHECKLIST.md) on the approved reduced-scope review-only
branch. [P10](FINAL-ACCEPTANCE.md) and [P11](MAINTAINER-GUIDANCE.md) retain their
original results and limits. The dated decision supersedes pending closure intent,
not the measurements, unresolved flags or historical failures.

## Explicit Decision

On 2026-10-11 the maintainer answered:

> Approve reduced-scope, no-release closure (Recommended)

The question explicitly excluded the still-unresolved AUTO_REGISTRY and HOOK
allocation flags from closure scope, **not marking them passed**, and stated that
no production optimization remains. This approves review closure only, not a
release, cost acceptance or a claim that every V34 deferral was fixed.

Evaluate a compatible patch first: the next patch would be `4.4.3`, but no
production fix or optimization survives V35's acceptance gates. There is no
demonstrated runtime benefit to package as that patch. `4.5.0-SNAPSHOT` does not
select a minor release. No incompatible change needs a migration decision.

Selected branch: **review-only/no release with explicit reduced scope**.
The approved exclusion does not resolve those flags, accept their cost or
establish a performance all-clear. The prior 36-fork follow-up is exhausted;
no additional experiment is authorized here. Future investigation needs a new
bounded approval and the missing ordinary high-mode evidence.

**GO for reduced-scope review closure; NO-GO for a V35 release or performance
all-clear.** The 10.2 heading and unresolved-profile checkbox remain unchecked
and explicitly excluded from closure. Full-scope performance acceptance did not
pass. No production change remains, so per-change delivery/benefit gates have
no retained patch to accept; failed candidates remain rolled back.

Candidate/version transition, final-coordinate packaging, unsigned/staged release
checks, signing/signature verification, version-matched tag/workflow, publication
and new-version Central consumption are **N/A for the approved no-release branch**.
They were not run and are not passes. No commit, tag or publication operation is
performed by closure. No public speedup, deployment-memory improvement,
snapshot-upgrade remedy or new application migration is claimed. Future scope
remains unselected.

## Complete Scope Reconciliation

All seven original workstreams were investigated. Evidence-backed no-change
outcomes are not seven delivered production fixes. Historical candidate results
remain subject to the raw-evidence availability limit below.

| Finding / owner | Recorded disposition and evidence | Remaining boundary / reopening trigger |
|---|---|---|
| V34-P3/P10 allocation finding; performance maintainer | Resolved without production change for the observed enabled-only split; [P2](ALLOCATION-INVESTIGATION.md) and regenerated [P10](FINAL-ACCEPTANCE.md) explain compiler-dependent Selection allocation | Not a universal performance pass. AUTO_NO_REGISTRY is attributed; AUTO_REGISTRY and HOOK remain unresolved below |
| V34-C001; observer/hook maintainer | Resolved without production change; [discovery review](DISCOVERY-COMPOSITION.md) attributes required dynamic work and rejects the bounded candidate | Preserve late registration, ordering, prototypes and supports checks; reopen with isolated removable work and matched acceptance |
| V34-C002; invocation/body maintainer | Resolved without production change; [body/reporting review](BODY-REPORTING-OWNERSHIP.md) verifies ownership and rolls back the null-holder candidate | Preserve body cleanup and subscription-local state; reopen with reproduced ownership failure or equivalent bounded cost reduction |
| V34-C003; planning/resolver maintainer | Resolved without production change; [planning review](STATIC-PLANNING-PROJECTION.md) separates static plans and dynamic wire inputs, rolling back annotation reuse | Preserve public metadata and request projections; reopen for immutable derivation with bounded ownership and repeatable benefit |
| V34-C004 optional preparation; handler/factory maintainer | Resolved without production change; [optional preparation](OPTIONAL-PREPARATION.md) distinguishes dormant objects from resources, rolling back its candidate | Preserve selection, auth and shutdown gates; reopen for demonstrated unnecessary acquisition or retention |
| V34-C004 rolled-back value reuse; effective-policy maintainer | Resolved without production change; independent [V35 experiment](OPTIONAL-PREPARATION.md) rolled back | Do not reinstate constants or skip mutation checks; reopen only with approved evidence that distinguishes benefit from compiler modes |
| V34-C005; factory/AOT maintainer | Resolved without production change; [lifecycle review](CONSTRUCTION-LIFECYCLE.md) attributes required cold work and verifies lifecycle parity | Preserve runtime/AOT binding, selection and ownership; reopen for a reproduced defect or removable same-phase work |

### Unresolved Final-Combination Flags

Owner: performance maintainer. AUTO_REGISTRY and HOOK remain **unresolved/blocking**
for full-scope 10.2 acceptance, explicitly excluded from this approved closure.
Their primary warm GET publisher rows crossed the allocation review
threshold. Ordinary follow-up traces showed only the low mode. Forced resolver
non-inlining reproduced the increment but did not supply the missing ordinary
high-mode compiler/class-load evidence for those profiles. Reverse confirmation
does not replace or erase the primary flags.

Preserve all 60 primary rows, three allocation flags, zero latency flags, four
reverse-confirmation rows and 338 scored/diagnostic forks. No fresh score or
diagnostic fork is run for Priority 12. The approved scope reduction excludes
only these two still-unresolved final-combination flags from closure obligations;
it does not rewrite the seven investigations or call all V34 deferrals fixed.

Supported alternative: retain published `4.4.2` behavior and the existing dynamic
discovery, mutation validation and ownership controls. Reopening requires a new
approved bounded plan aimed at ordinary high-mode attribution, with the old scores
preserved, rather than favorable-rerun selection or another unrelated API/native pass.

## Source and Immutable Evidence Inventory

Clean reachable preparation source:
`3ccbeb73947517a0eea5d1e33a88a656ffb2c4d6`, tree
`f273faed8d8d0f602acb816e68544cddb776db1a`. The worktree was clean before this
preparation. The new decision record and documentation guard are a **dirty patch**
against that commit, not a new committed revision or release artifact.

All **2,952 files** across P9/P10/P11 and all three archives rehash successfully:

| Bundle | Files | SHA-256 of SHA256SUMS |
|---|---:|---|
| `verification-evidence/v35-priority9/` | 1,811 | `e1f1ee5bb1004963a1c2aca7a4faa28da67193b8101af9ab522e80b0340599c0` |
| `verification-evidence/v35-priority10/` | 931 | `62e4375ebb17f9d4591449b86b67f16795ab126d4419c24616af37435bb33ba4` |
| `verification-evidence/v35-priority11/` | 210 | `b8d580e78d3bcf8c24b8b14371d7a80310f688dd49731f1727c4efdbc6ee3af0` |

Archive SHA-256, respectively:

- P9: `ca124e7ee2c18e999ba5c089b8cc2a488d71997dca49fc93e62fc81e42e0b8ba`.
- P10: `ee8f62205902af433aeb3da5b8d08203c22e0026af2664c9c6b89150a8e109e0`.
- P11: `5ebf81ad9e39de30167d76a2d0c533be34725f22eb8237770291566060aa97bf`.

Production/module POM/settings/consumer/native inputs match reachable P9 source
`f38fb8d22486d35401a7af9cb9e80f5e5e8c75ef` and P10 closure source
`85948837c4f4001f648b15adb3efd80b9de05abd`. All 39 recorded harness/POM hashes
match; the V35 parity runner matches its sealed P9 closure copy, not its pre-tooling
starting commit. Fourteen P11 source files match the clean preparation source;
the checklist matches after excluding only P11's external checksum anchor.

| Reused evidence | Actual scope and limits |
|---|---|
| P9 correctness/ownership | Per Boot 4.0.0/4.1.0: 2,229 starter + 80 helper + 62 OTel + 75 benchmark-contract = 2,446 cases; 29 selected-consumer and one physical-absence case; ordinary/AOT-enabled JVM runs. Separate reachability lanes: 16/5/2 cases |
| P9 native | Clean-source Boot 4.0 native compile/execution passed; binary SHA-256 `66de72017d7c8e8a33e1a4a593f55a0736813ffb479d88ccd3bbd7ec77bd7271`. Boot 4.1 evidence is JVM/AOT, not a second native build |
| P10 compatibility/packaging | Independent strict root/starter source/binary checks against isolated Central `4.4.2`, negative fixtures and Boot packaging passed; root API artifacts were recaptured in a documented rerun |
| P10 cost | 60 primary rows/artifact, reverse confirmation and bounded attribution; 10.2 still pending. Re-analysis reproduces the saved three allocation flags without running benchmarks |
| P11 guidance | 273 focused cases per Boot row, including 104 documentation cases; 56 Python checks. Initial failures and corrected reruns remain sealed |

These are **unchanged-input reuse, not fresh executions**. Overlapping runs are
not summed into distinct totals. No signing/publication or new-version Central
consumption is inferred from existing `4.4.2` provenance.

P2-P8 historical raw evidence lost in the 2026-10-10 root-clean incident remains
unavailable. P9/P10 regeneration does not recreate rejected-candidate scores.
Ignored local bundles are not available in a clean clone; retain external copies
before cleaning build output. [Tracked reproduction](FINAL-ACCEPTANCE.md#reproduction-and-preservation)
requires a fresh directory, explicit input reconciliation and the approved
measurement boundary. Source inspection or a missing historical object is not a pass.

## Preparation and Closure Verification

Fresh preparation verification passes **274 cases across ten classes per Boot
row** (4.0.0 and 4.1.0), including 105 documentation and four public-example cases;
all **56 Python checks** pass. There are zero failures/errors/skips. Java 21.0.8,
Maven 3.9.9, a 512 MiB Maven heap and two active processors match P11; ordinary
test JVMs disable explicit GC. Local links, metadata/configuration and generated
readiness pass. Overlapping final reruns are not additional distinct cases.

Reproduce the focused checks from the repository root using a writable non-baseline
Maven repository (the recorded repository is shown here; no root clean is needed):

```bash
export JAVA_HOME=/usr/lib/jvm/jdk-21.0.8-oracle-x64
export PATH="$JAVA_HOME/bin:$HOME/.sdkman/candidates/maven/current/bin:$PATH"
export MAVEN_OPTS='-Xmx512m -XX:ActiveProcessorCount=2'
REPO=/tmp/rhttp-v35-p10-20261011/repository-current
for BOOT in 4.0.0 4.1.0; do
  mvn -B -ntp -s .mvn/maven-central-settings.xml -Dmaven.repo.local="$REPO" \
    -Dspring-boot.version="$BOOT" -pl reactive-http-client-starter \
    -Dtest=DocumentationReleaseArtifactTest,ReactiveHttpClientPropertiesTest,ReactiveHttpClientConfigurationMetadataTest,V33GuidanceExampleTest,DefaultPathCostOwnershipTest,CacheWorkPolicyEnforcementTest,StreamingUploadOwnershipTest,SubscriptionLocalReportingStateTest,RequestContextSnapshotTest,ResourceOwnershipReviewTest \
    '-DargLine=-XX:+DisableExplicitGC -XX:ActiveProcessorCount=2' test || exit
done
python3 -B -m unittest discover -s scripts -p 'test_*v3[345]*.py' -v
git diff --check
```

P12 preparation evidence is sealed under
`target/release-evidence/v35/priority12/preparation-20261011/`, with a durable copy
at `verification-evidence/v35-priority12-preparation/` and its dated archive.
Exact commands/statuses, reports, toolchain, clean base, dirty patch and source
copies are retained. The checklist's external integrity anchor is added only
after sealing to avoid a self-referential hash. This preparation seal did not
complete 12.3: the final decision and closure-state verification were then missing.
No fresh full-reactor, API, scored-cost, AOT/native or release-gate execution is
claimed. Closure uses a separate final evidence seal and applicable reruns rather
than silently overwriting this pending-decision checkpoint.

No production, dependency, fixture, coordinate or V1-V34 history change is made.
The pending-decision preparation checkpoint above remains sealed unchanged;
it reported `activeRoadmap=v35` and did not authorize closure. A separate final
closure run validates this approved record, checklist, index and readiness. Its
source is the same reachable base plus the separately hashed **dirty patch**, not
an invented new commit. The closure seal includes both preparation provenance and
final verification without replacing any prior bundle or score.

Fresh approved-state closure verification passes **274 cases per Boot row**
(4.0.0 and 4.1.0), including **105 documentation cases**, and **56 Python checks**;
zero failures/errors/skips. Reproduction syntax and `git diff --check` pass.
Overlapping preparation/final runs are not additional distinct cases. The final
closure integrity anchor is recorded in the checklist after sealing.
The final bundle is `verification-evidence/v35-priority12/`, its archive is
`verification-evidence/v35-priority12-20261011.tar.gz`, and the conventional copy
is `target/release-evidence/v35/priority12/closure-20261011/`. Exact commands,
statuses, actual counts, source files, patch, generated readiness and hashes are
retained. The external checksum anchor follows sealing to avoid self-reference.

## Final State

Roadmap, checklist and index report completed, review-only/no release, explicitly
reduced scope. Generated readiness reports `activeRoadmap=null`,
`plannedFinalVersion=null` and `releaseLane=unselected`; future scope is unselected.
Reactor/modules/fixtures stay `4.5.0-SNAPSHOT`, and published/API/consumer/benchmark
baselines stay `4.4.2`. No changelog release section or public performance report
is added. Generated pending commands are conservative future-release checks, not
required no-release gates or evidence that V35 remains open. V1-V34 history is
unchanged. Historical proposal boxes and the expressly excluded 10.2 markers
do not reopen this review or imply performance acceptance.
