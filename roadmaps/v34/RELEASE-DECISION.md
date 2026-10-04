# V34 Review and No-Release Decision

> **Status:** completed; review-only, no release
> **Decision date:** 2026-10-04
> **Published baseline:** `4.4.2`
> **Development coordinate:** `4.5.0-SNAPSHOT`
> **Delivered production IDs:** none; C004 rolled back
> **Release scope:** no release; V34 closed

This closes [Priority 12](CHECKLIST.md) on its conditional review-only branch.
The dated [P10 evidence](COMPATIBILITY-PERFORMANCE.md) and
[P11 guidance](MAINTAINER-GUIDANCE.md) remain unchanged historical checkpoints.
This decision supersedes their pending scope, not their measurements or limits.

## Explicit Decision

On 2026-10-04 the maintainer answered:

> Approve review-only/no-release closure (Recommended)

The question explicitly included C004's rollback and deferring the unresolved
allocation flag, **not passed**. This is approval to close the review without a
release, not permission to publish a candidate or accept a correctness tradeoff.

A compatible patch, `4.4.3`, was evaluated first. No production fix or optimization
survives the failed C004 benefit gate, so no patch is selected. The existing
`4.5.0-SNAPSHOT` coordinate does not select a minor release. V34 retains regression
tests, investigation tooling and documentation, not a user-facing runtime change.
No version, dependency, default, API, SPI, meter or behavior changes at closure.
There is no application migration or recommendation to upgrade for performance.

**GO for review-only closure; NO-GO for a V34 release or performance all-clear.**
Candidate preparation, version/tag transition, signing, staging, publication and
new-version Central consumption are **N/A** for this approved branch. They were
not run and are not recorded as passes. Published `4.4.2` remains the baseline;
future release work requires a new explicit decision and its own gates.

## Findings and Deferrals

The maintainer accepts the review dispositions, not an optimization benefit.
The [original approval](IMPROVEMENT-DECISION.md) selected only narrow C004 work;
the [rollback](HARDENING-EVIDENCE.md) leaves production unchanged. Retained tests
preserve dynamic discovery, policy mutation checks, request/body/context ownership,
inactive resources and V32/V33 extension/lifecycle safeguards.

| Finding | Final V34 disposition | Owner, supported alternative and reopening evidence |
|---|---|---|
| C001 | Intentional observer/hook discovery retained; optimization deferred | Observer/hook maintainer. Use appropriate application bean scopes, not global cached lookup results. Reopen only with isolated cost and equivalent late-registration, order and prototype behavior |
| C002 | Body/reporting-state optimization deferred | Invocation/body maintainer. Keep per-subscription state, one-shot input limits and buffer cleanup. Reopen with attributed removable cost and deterministic cancel/discard/terminal ownership controls |
| C003 | Planning/projection optimization deferred | Planning/resolver maintainer. Reuse supported proxies, not resolved requests. Reopen with isolated static work and matched wire/metadata/argument behavior |
| C004 | Approved immutable disabled-value reuse evaluated and rolled back; broader resource work deferred | Effective-policy/factory maintainer. Keep all mutation checks and supported factory destruction. Reopen only for repeatable benefit or a reproduced resource defect under a new approval |
| C005 | Required framework work retained; optimization deferred | Factory/AOT maintainer. Use supported context lifetimes and predictable extension metadata. Reopen with an isolated lifecycle cost or retention defect, not cold fixture allocation alone |
| P3/P10 allocation flag | Unresolved; explicitly deferred outside this closed review, not cleared | Performance maintainer. Preserve original/reverse samples; use bounded, separately recorded profiling to test causes. Reopening requires explanation or fresh explicit scope, not a favorable-rerun selection |

The enabled-only allocation flag remains unresolved. P10's 60-row primary pair
has no review flags, but reverse confirmation repeats current forks near
1,392/1,136 B/op against baseline 1,136/1,136. All 310 starter classes and 120
non-starter JARs match in that pair. This excludes a retained production-code or
dependency delta there; it does not explain the split. JIT/escape analysis remains
a hypothesis, not an established cause. Original P3/P6/P10 failures and samples
are retained; there is no new scored rerun or promotion of public numbers.

The 10.1 heading and acceptance checkbox remain unchecked and explicitly deferred.
This is **not a performance pass** and **no correctness tradeoff** is accepted.
No selected production improvement remains to justify a release. The maintainer
has removed investigation of that flag from the work required to close V34, not
from the evidence record. Deployment memory and service-mesh incidents remain
unattributed; allocation, retention, direct memory and RSS are distinct domains.

## Source and Evidence Applicability

Clean reviewed source: `be2b640e75e38c13c2861165928df88a45fbd9d1`, tree
`1a3e58ef45edaf99557374aa7e173b9b4686b405`. This is reachable committed P11 source.
The closure patch changes only documentation, roadmap state and documentation
guards. It is sealed separately with file hashes and the base revision; it is
**not a new committed revision**, native image or release artifact. No commit,
tag, signing or publication operation is performed by this closure.

The three production trees, four module/root POMs, benchmark tree, scripts,
`.github` consumer/native fixtures and `.mvn` settings have identical Git object
IDs to P10's `44d6ffaede52a5be92148cc078f287428791d511`. All 11 sealed P11 source
files match the clean review source. This permits unchanged-input reuse of the
following recorded results, **not fresh executions**:

| Evidence | Reused scope and actual results |
|---|---|
| [P9 parity](PARITY-EVIDENCE.md) | Mock 76; real Boot 4.0.0/4.1.0 consumers 29 each; optional-absence consumers one each; targeted AOT/selection 283 each; fixture six each; ordinary/AOT JVM executions exit 0 |
| P9 native | Clean `fc98e58b9d5154a0ba539ea878b05c532b379554`, GraalVM 25.0.3, Boot 4.0.0; compile and executable exit 0. Boot 4.1 evidence is JVM/AOT, not a second native image |
| [P10 compatibility and cost](COMPATIBILITY-PERFORMANCE.md) | Independent strict root/starter source/binary API and Central provenance pass. Full Boot rows each 2,152 starter + 80 helper + 62 OTel = 2,294 cases. Benchmark correctness 75 per artifact; reachability 16/5/2; 14 V34 and five native-runner Python guards. Final docs 89 each overlap the full suites |
| [P11 guidance](MAINTAINER-GUIDANCE.md) | 232 focused cases (90 documentation cases), 75 benchmark correctness cases, 14 Python guards; command/input checks. No new scored JMH or native run |

All successful recorded test lanes have zero failures/errors/skips; overlapping
executions are not added together. The native binary's SHA-256 is
`a3476f5f749d0cb546e4c175f371ea070291923d5621d92cb819758ab9a3b10e`.
P10 already verifies its fixture/source applicability; this closure changes none
of those inputs. Broader release evidence retains its original coordinates.

All **2,403 files** across these four inventories were rehashed successfully:

| Local bundle | Files | SHA-256 of SHA256SUMS |
|---|---:|---|
| `target/release-evidence/v34/priority9/` | 1,213 | `596d511a2683fad7bbdd76fccf4779b9e80a9f7045883739cd023c0b0a22c5b7` |
| `target/release-evidence/v34/priority10/` | 973 | `0987d689fa4c131763d12296297380d2698346a2dec78793be4a8093652604cc` |
| `target/release-evidence/v34/priority11/` | 156 | `bdb0e8a4b1b9b890a73f2465fe3073f30da699c50cff940ba18cb6693fddde2a` |
| `target/v34-priority9-native-runs/native-feynp9yy/evidence/` | 61 | `d62ac7cd25d3f9eb816595b9ef6b07d0af431dfa53e4781c8d7968dd9a4c6821` |

These ignored bundles are local evidence, not published attachments or files in
a clean clone. Preserve them before cleaning `target`; absent bundles must be
reproduced with the tracked commands in the linked records. Their original
commands, statuses, dependency/classpath inventories, artifacts and hashes remain
sealed. Closure adds `target/release-evidence/v34/priority12/` without overwriting
any previous bundle or V1-V33 history.

## Closure Verification

Use Oracle Java 21.0.8, Maven 3.9.9 and the existing Boot 4.0.0 row. Ordinary
test JVMs disable explicit GC; Maven uses 512 MiB and two active processors.
The fresh closure runs validate the documentation/guard delta, not production
changes. Exact commands, XML counts, source snapshots, generated readiness and
all exits, including the initial missing-decision red test, are retained in P12.

| Fresh closure check | Result |
|---|---|
| Focused starter suite | 233 passed in ten classes, including 91 documentation/archive/readiness and four compiled guidance cases; zero failures/errors/skips |
| V34 Python guards | 14 passed |
| Saved benchmark input comparison and reviews | Inputs match; 60 primary rows have zero flags; reverse confirmation retains one allocation flag. Re-analysis only, no new scoring |
| Reproduction syntax and whitespace | Bash syntax and `git diff --check` pass |
| Prior integrity and applicability | All 2,403 entries rehashed; clean source and unchanged production/dependency/fixture objects verified |

The final focused rerun repeats the 233 cases after the closure text is complete;
these are overlapping runs, not 466 distinct tests. The one-case initial red run
failed because this decision record did not yet exist; it remains in `red/`.
The sealed source patch includes all closure documents and the new guard. Its
checklist snapshot precedes only the external **Priority 12 Integrity Anchor**
appended after sealing to avoid a self-referential checksum. That anchor is not
part of the sealed patch; it changes no decision, test or implementation input.

From the repository root, with a writable Maven cache (the recorded run uses
`target/v33-native-runs/native-g0ynw95x/repository`):

```bash
export MAVEN_OPTS='-Xmx512m -XX:ActiveProcessorCount=2'
REPO="$PWD/target/v33-native-runs/native-g0ynw95x/repository"
mvn -B -ntp -s .mvn/maven-central-settings.xml -Dmaven.repo.local="$REPO" \
  -pl reactive-http-client-starter \
  -Dtest=DocumentationReleaseArtifactTest,ReactiveHttpClientPropertiesTest,ReactiveHttpClientConfigurationMetadataTest,V33GuidanceExampleTest,DefaultPathCostOwnershipTest,CacheWorkPolicyEnforcementTest,StreamingUploadOwnershipTest,SubscriptionLocalReportingStateTest,RequestContextSnapshotTest,ResourceOwnershipReviewTest \
  '-DargLine=-XX:+DisableExplicitGC -XX:ActiveProcessorCount=2' test
python3 -m unittest discover -s scripts -p 'test_*v34*.py' -v
git diff --check
```

No fresh complete-module, strict API, assembled-consumer, AOT/native, scored JMH,
signing or published-artifact verification is claimed for this documentation-only
closure. The unchanged-input records above supply the applicable review evidence.

## Final State

Roadmap, checklist and index say completed, review-only/no release. Readiness
reports `activeRoadmap=null`, `plannedFinalVersion=null` and future scope unselected.
The reactor stays `4.5.0-SNAPSHOT`; published/API/consumer/benchmark baselines stay
`4.4.2`. Generated future manual release commands remain conservative pending
work for a future release, not failed or required V34 no-release gates. No V35
roadmap or next candidate is selected. Historical unchecked proposal criteria and
the expressly deferred 10.1 markers do not claim a pass or reopen this review.
