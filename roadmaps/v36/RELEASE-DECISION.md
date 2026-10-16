# V36 Conditional Release and Closure Decision

> **Status:** completed; review-only, no release; explicitly reduced scope
> **Recorded:** 2026-10-16
> **Published / development:** `4.4.2` / `4.5.0-SNAPSHOT`
> **Delivered production changes:** none
> **Scope reduction:** A001/AUTO_REGISTRY and A002/HOOK excluded from closure obligations, not resolved
> **Release scope:** no release; V36 closed

This closes [Priority 10](CHECKLIST.md) under the maintainer's explicit decision.
It supersedes pending lifecycle/closure statements in dated P1-P9 records, not
their scores, failures, uncertainty or scope limits. Full-scope cost acceptance
did not pass. V1-V35 history remains unchanged.

## Explicit Decision

On 2026-10-16 the maintainer answered:

> Approve reduced-scope, no-release closure (Recommended)

The question explicitly excluded unresolved **A001 (AUTO_REGISTRY)** and
**A002 (HOOK)** from closure obligations, **not marking them passed**, stated
that no production change remains, and granted no additional experiments.
This is V36-P10-1: **GO for reduced-scope review closure; NO-GO for a V36 release
or performance all-clear**. No-release alone would not clear either finding;
the named exclusions are the separate scope decision.

Evaluate a compatible patch first: `4.4.3` would be the next patch, but the
retained corrections are evidence tooling/tests/guidance, not a reproduced
runtime fix or accepted optimization to package. No production correction is
selected. `4.5.0-SNAPSHOT` selects no minor release, and no incompatible change
requires migration. Reactor/modules/fixtures stay unchanged; public, API,
consumer and benchmark baselines remain published `4.4.2`.

Candidate/version transition, final-coordinate packaging, unsigned/staged checks,
signing/signature verification, version-matched tag/workflow, publication and
new-version Central consumption are **N/A for the approved no-release branch**
on 2026-10-16. They were not run and are not passes. No commit, tag, publication,
changelog release section or public performance report is created by closure.
Future scope remains unselected; there is no snapshot-upgrade remedy.

## Final Findings

| Finding / owner | Final disposition | Supported alternative and reopening condition |
|---|---|---|
| A001 / AUTO_REGISTRY; performance maintainer with metrics-integration review | **unresolved/blocking** for full-scope cost acceptance; explicitly excluded from closure. Ordinary high-mode causal compiler evidence is still missing. | Retain required registry/observer behavior and original scores. New investigation needs separately approved budget/stop rules and independent ordinary high-mode attribution, not favorable-rerun selection. |
| A002 / HOOK; performance maintainer with observer/hook review | **unresolved/blocking** for full-scope cost acceptance; explicitly excluded from closure. Ordinary high mode occurred on both artifacts but not in the traced lane. | Preserve actual hook discovery/order/lifecycle/terminal semantics. Reopening needs its own attributable ordinary high-mode evidence, not the A001 or AUTO_NO_REGISTRY explanation. |
| E001 / evidence reliability; benchmark tooling maintainer | **verified for new capture within limits**. Unsupported timestamp-to-score joins were corrected; new capture verifies process/artifact/profile association, bounded compiler history and incomplete-evidence rejection. | Preserve exact per-worker records and seals. Legacy pairing, exact executing-code identity and individual internal-probe census remain unverified, not retrospectively repaired. Changed tooling/JMH or a reproduced association defect requires controls and approved affected capture before stronger claims. |

All **48/48 worker slots are exhausted**: 40 P4 workers and eight P5 intervention
workers. The 194-invocation accounting includes 96 inferred internal probes,
not an observed process census. The maintainer accepted the P4 probe-accounting
exception and separately approved V36-P5-1 before intervention. Closure does not
erase that exception or authorize a pilot, preflight, probe, scoring or diagnostic
retry. The prior V35 338-fork authorization is also exhausted.

V36-P6-1 retains no production change. C001-C005 and both C004 subrows keep their
V35 no-change outcomes; no rejected optimization is reinstated. The saved V35
60 primary rows and four reverse rows per artifact retain all three primary
allocation flags and zero primary latency flags. V36 diagnostics do not replace
those scores. Both findings remain unaccepted independently and combined.
No public number, universal speedup, leak diagnosis or pod/mesh all-clear follows.

Dynamic discovery, fresh public metadata, complete policy mutation validation,
per-subscription body/context/terminal ownership, cancellation/discard and failed
construction cleanup remain intact. Application-supplied pools, schedulers,
registries and SDKs remain application-owned. Independent non-single-flight
loads remain caller-owned and can outlive manager close, with late cache publication
rejected. Manager-owned flights/refreshes terminate; removed meters are not
post-close completion evidence. Allocation B/op is not retained heap or RSS.

## Source and Evidence Inventory

Clean starting source: `5f160cccdff9b6116fdf74d64f8d85a47ed75150`, tree
`c3dbda315c7e405523d0e3b6a96affb2497e03ac`. The final documentation/test changes
are a separately hashed **base-plus-patch**, including new files, not a clean
release commit. Patch applicability and resulting file hashes are verified.

The [P9 catalog](MAINTAINER-GUIDANCE.md#evidence-inventory) pins 13 original
bundles, their 4,758 files and archive hashes. P9 itself adds 229 files:

- Bundle: `verification-evidence/v36-priority9/`.
- Manifest SHA-256: `938d27c50f957853c3ec2fd6ea62edebaa79d098fc8a931d047207323d738d28`.
- Archive: `verification-evidence/v36-priority9-20261016.tar.gz`.
- Archive SHA-256: `7dd057b61e9b942f2557f325aceaf5534765d080edda1d18b0a4883d32d4f32d`.

The closure audit revalidates **4,987 files across 14 bundles**, every archived
file and all trusted manifest/archive hashes without modifying or extracting the
originals. It verifies **392 unchanged runtime/build/test/fixture inputs** against
the P8 inventory tied to clean reachable runtime/native source
`f38fb8d22486d35401a7af9cb9e80f5e5e8c75ef`. Current docs and tooling receive fresh
checks; old documentation results are not current verification.

| Evidence | Result and applicability |
|---|---|
| Decisions and measurement | P3/P4 accounting exception/P5/P6 approvals, original scores and bounded new capture preserved. P7 completes a non-acceptance review, not a performance pass. No new worker is launched. |
| Correctness and ownership | P8 reuses unchanged V35 P9: 2,446 cases per Boot row, 29 assembled-consumer cases, one physically absent optional-integration case, six JVM fixture cases and separate ordinary/AOT-enabled execution. P6's 141 focused semantic cases remain separately recorded, not added to unique full-suite totals. |
| Controlled reachability | Historical 16/5/2 cache/handoff/benchmark cases support only their named collection claims. Ordinary fresh tests disable explicit GC; no fresh collection or RSS claim. |
| Native | Historical clean-source Boot 4.0 compile/executable exits zero; binary `66de72017d7c8e8a33e1a4a593f55a0736813ffb479d88ccd3bbd7ec77bd7271`. Boot 4.1 evidence is JVM/AOT, not a second native image or a new native execution. |
| API and packaging | P8/P10 reuse strict root and independent starter source/binary comparisons against separate isolated Central 4.4.2 repositories, plus both Boot packaging results. Exact retained report/artifact identities apply, not overwritten build output. |
| Guidance and preservation | P9's 170 cases per Boot row, 92 Python tests, 13 archives and negative integrity fixtures are preserved with all failures. Closure checks validate final lifecycle/readiness changes separately. |

These are **unchanged-input reuse, not fresh runtime/API/consumer/AOT/native
executions**. Original commands/exits, toolchains/dependencies, artifact/source
hashes, reports, raw measurements and failed attempts remain in their referenced
bundles. Saved Central provenance is not fresh signing or publication verification.

Lost V35 P2-P8 recordings and the earlier lost P9 attempt remain unavailable.
The original V33 publication bundle remains absent. V35 P10's original execution
object is locally available but not an ancestor; reachable input reconciliation
does not relabel that run. P9's read-only audit archive is not a replacement for
the raw archives, nor an off-machine backup. Ignored bundles are unavailable in
a clean clone until supplied by the maintainer. No missing evidence was invented
or regenerated under a historical identity. No production request material,
credentials or passphrases belong in this bounded synthetic evidence.

## Final Verification and Preservation

Fresh closure verification uses the existing documentation, examples, metadata,
configuration and readiness checks on genuine Boot 4.0.0/4.1.0 dependencies, plus
the deterministic Python tooling suite. **171 Java cases per Boot row** pass:
118 documentation/readiness/link, 18 metadata/example and 35 property cases;
**92 Python tooling tests** pass. There are zero failures/errors/skips. Overlapping
final reruns are not additional unique cases. Java 21.0.8/Maven 3.9.9 are recorded,
with explicit GC disabled for ordinary tests. Local links, generated readiness,
metadata/examples and `git diff --check` pass. The final external seal is
recorded in [CHECKLIST.md](CHECKLIST.md). No new benchmark, runtime full-suite,
API, consumer, native or release-gate execution is claimed.

Reproduction from the repository root, with a writable repository (the recorded
path is local, not a portable prerequisite):

```bash
export JAVA_HOME=/usr/lib/jvm/jdk-21.0.8-oracle-x64
export PATH="$JAVA_HOME/bin:$HOME/.sdkman/candidates/maven/current/bin:$PATH"
export MAVEN_OPTS='-Xmx512m -XX:ActiveProcessorCount=2 -Dmaven.repo.local=/tmp/v36-p9-maven-repository'
for BOOT in 4.0.0 4.1.0; do
  mvn -B -ntp -s .mvn/maven-central-settings.xml -Dspring-boot.version="$BOOT" \
    -pl reactive-http-client-starter \
    -Dtest=DocumentationReleaseArtifactTest,ReactiveHttpClientConfigurationMetadataTest,ReactiveHttpClientPropertiesTest \
    '-DargLine=-XX:+DisableExplicitGC -XX:ActiveProcessorCount=2' test || exit
done
python3 -B -m unittest discover -s scripts -p 'test_*v3[3456]*.py' -v
git diff --check
```

Use [the read-only archive verification commands](MAINTAINER-GUIDANCE.md#reproduction-and-privacy)
with each original trusted seal. The final driver, commands/statuses, reports,
toolchain/effective dependencies, failed attempts if any, input audit, complete
source patch and readiness are under `target/release-evidence/v36/priority10/`,
copied before cleanup to `verification-evidence/v36-priority10/` and its dated
archive. Both copies and archived contents are verified. The checklist's external
integrity paragraph is added after sealing to avoid self-reference; it is the only
excluded delta. Never clean the only evidence copy.

## Final State

Roadmap/checklist/index agree on **completed; review-only, no release; explicitly
reduced scope**. Readiness reports `activeRoadmap=null`, `plannedFinalVersion=null`
and `releaseLane=unselected`. Versions and V1-V35 history are unchanged. Future
pending manual-release commands are conservative future-release checks, not
unfulfilled no-release gates or evidence that V36 remains active. Historical
acceptance proposal boxes do not imply a passed performance gate or reopen this
approved review. A001/A002 remain named and unresolved after closure.
