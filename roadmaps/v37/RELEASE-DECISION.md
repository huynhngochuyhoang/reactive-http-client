# V37 Review Closure Decision

> **Status:** completed; review-only, no release; explicitly reduced scope
> **Recorded:** 2026-10-25
> **Published / development:** `4.4.2` / `4.5.0-SNAPSHOT`
> **Delivered production changes:** none
> **Scope reduction:** A001/AUTO_REGISTRY and A002/HOOK excluded from closure obligations, not resolved
> **Release scope:** no release; V37 closed

This closes [Priority 10](CHECKLIST.md) on the approved feasibility-stop route.
It supersedes pending lifecycle statements in dated P1-P9 records, not their
measurements, failures, uncertainty or evidence limits. Full-scope cost acceptance
did not pass. V1-V36 history remains unchanged.

## Explicit Decision

After the named-exclusion request to close review-only/no release, excluding
**A001 (AUTO_REGISTRY)** and **A002 (HOOK)** without marking either passed, the
maintainer replied on 2026-10-25:

> Complete the Priority 10 with review scope, no release

This is **V37-P10-1: GO for reduced-scope review closure; NO-GO for a V37 release
or performance all-clear**. The response approves the review scope described in
that request, including both named closure exclusions. No-release alone clears
neither finding. No further experiment or production correction is approved.

Evaluate a compatible patch first: `4.4.3` would be the next patch, but V37
retains only review, documentation and test evidence, not a production fix or an
accepted optimization to package. No patch is selected. `4.5.0-SNAPSHOT` selects
no minor release; reactor/modules/fixtures and published/API/consumer/benchmark
baselines stay unchanged at `4.5.0-SNAPSHOT` and `4.4.2` respectively. Future scope
remains unselected, with no migration requirement or snapshot-upgrade remedy.

Final-coordinate packaging, signing/signature checks, tag/source/workflow release
verification, publication and isolated new-version Central consumption are
**N/A on 2026-10-25 for the approved no-release branch**, not run and not passed.
No commit, tag, publication, release changelog section or public benchmark report
is created. Generated future manual-release checks do not reopen this review.

## Final Findings

| Finding / owner | Final disposition | Reopening condition |
|---|---|---|
| A001 / AUTO_REGISTRY; performance maintainer with metrics-integration review | **unresolved/blocking** for full-scope cost acceptance; explicitly excluded from closure obligations, not passed. Ordinary high-mode attribution is missing. | A materially different bounded method linking ordinary high/low allocation to its cause, then separate reopening and numerical execution approval. Retain required registry/observer behavior. |
| A002 / HOOK; performance maintainer with observer/hook review | **unresolved/blocking** independently and combined; explicitly excluded from closure obligations, not passed. Ordinary high stages 10 and 15 lack causal attribution. | HOOK-specific ordinary-mode evidence and separate approval; neither A001 nor AUTO_NO_REGISTRY explanations transfer. Preserve genuine hook discovery/order/lifecycle/terminal semantics. |
| E001 / attribution feasibility and evidence limits; benchmark tooling maintainer | **Feasibility decision complete: V37-P2-1 approved stop**. Attribution limitations remain unresolved; no new tooling defect or correction is claimed. | Concrete exporter/format and worker/phase/code/allocation correlation, uncertainty, rejection rules and resource accounting before seeking execution approval. A defect claim also needs a failing reproduction. |

V37 launched **zero measurement workers**. V35's 338 workers and V36's 48/48
workers remain exhausted; 96 inferred internal probes within 194 accounted JVM
invocations are not an observed census. No pilot, probe, recorder smoke, preflight,
scoring retry or tuning trial is authorized. Priorities 3-7 remain dated N/A, not
run or passed; gates 4.3 and 7.2 were never approved. There is no implemented or
rolled-back V37 runtime candidate. C001-C005 and both C004 subrows retain V35's
no-change outcomes and rejected candidates remain rejected.

Priority 8.2 remains unchecked: independent and combined cost acceptance is
blocked, now **excluded from closure only** by the two named exclusions. Original
>20% latency and >max(32 B/op, 5%) allocation triggers remain unchanged. Saved
60 primary and four reverse rows per artifact retain three primary allocation
flags, zero primary latency flags and zero reverse flags. Diagnostic observations
and clear reverse aggregates do not replace scores or establish ordinary cause.

Runtime contracts remain intact: dynamic discovery, fresh metadata, policy mutation
validation and per-subscription body/context/terminal ownership. Independent
non-single-flight loads remain caller-owned and may outlive manager close, while
late publication is rejected. Manager-owned flights/refreshes terminate; removed
meters are not post-close completion evidence. B/op is not retained heap or RSS,
and this review is not a Kubernetes/Istio/Envoy diagnosis or pod-memory all-clear.

## Provenance and Preservation

Reachable base `2ee37be5f1d976c85b233ed1dc489a7dfb8895b8`, tree
`1d7485b43c6f82d2d2fcc707a81946777bfd24f3`, plus the complete hashed
**base-plus-patch**, including the previously uncommitted P9 changes and new
files. This is not a clean release commit. The sealed P9 record remains unchanged;
closure adds its own source/approval/command/report record and verifies patch
application and resulting file hashes.

[Priority 9](MAINTAINER-GUIDANCE.md) verified 4,426 files across 15 bundles.
The final audit additionally verifies P9's 172-file bundle: **4,598 files across
16 bundles and complete archives**, using the original trusted checklist seals.
It rechecks **392 unchanged runtime/build/test/fixture inputs**. Original raw
scores, commands/exits, dependencies, artifact hashes, failures and approvals stay
in the referenced archives; this smaller closure archive does not replace them.

[Priority 8](VERIFICATION.md) continues to define exact unchanged-input reuse:
historical runtime, assembled/absence, controlled reachability, strict root and
independent starter source/binary API, packaging and JVM/AOT/native results are
**not fresh executions**. No new runtime full suite, API, consumer, native or
benchmark run is claimed. Fresh checks below cover the documentation/lifecycle
change rather than repeating unchanged runtime lanes.

Lost V35 P2-P8 recordings stay unavailable, as do 121 historical classpath paths
per scored artifact. Shaded JARs and saved provenance are not a runnable old
environment. Non-ancestor execution identities are not renamed as integration
commits. P8's obsolete 83-versus-92 test-count driver failure remains preserved;
no missing or failed recording is silently regenerated as a success.

Evidence lives under `target/release-evidence/v37/priority10/`, with verified copy
`verification-evidence/v37-priority10/` and dated archive outside Maven output.
Ignored local bundles must be supplied by the maintainer in a clean clone; they
are not an off-machine backup. Use the [read-only verifier](MAINTAINER-GUIDANCE.md#reproduction-and-privacy)
before cleanup. The checklist's external hash paragraph is the only final patch
delta excluded from the seal to avoid self-reference. No production request
material, identities, arbitrary error text or credentials are captured.

## Final Verification

Final verification passes **178 Java cases per Boot row** (4.0.0/4.1.0):
125 documentation/readiness/link, 18 metadata/example and 35 properties cases;
**92 Python tooling tests** also pass. There are zero failures/errors/skips.
Baseline-provenance fixtures and `git diff --check` pass. Repeated final checks
are not extra unique cases. Java 21.0.8/Maven 3.9.9, actual dependency hashes,
commands/exits and generated readiness are retained. Ordinary tests disable
explicit GC; no fresh reachability or RSS claim is made. The final seal is in
[CHECKLIST.md](CHECKLIST.md).

```bash
export JAVA_HOME=/usr/lib/jvm/jdk-21.0.8-oracle-x64
export PATH="$JAVA_HOME/bin:$HOME/.sdkman/candidates/maven/current/bin:$PATH"
export MAVEN_OPTS='-Xmx512m -XX:ActiveProcessorCount=2 -Dmaven.repo.local=/tmp/v37-draft-maven-repository'
for BOOT in 4.0.0 4.1.0; do
  mvn -B -ntp -s .mvn/maven-central-settings.xml -Dspring-boot.version="$BOOT" \
    -pl reactive-http-client-starter \
    -Dtest=DocumentationReleaseArtifactTest,ReactiveHttpClientConfigurationMetadataTest,ReactiveHttpClientPropertiesTest \
    '-DargLine=-XX:+DisableExplicitGC -XX:ActiveProcessorCount=2' test || exit
done
python3 -B -m unittest discover -s scripts -p 'test_*v3[3456]*.py' -v
git diff --check
```

Roadmap/checklist/index agree on **completed; review-only, no release; explicitly
reduced scope**. Readiness reports `activeRoadmap=null`, `plannedFinalVersion=null`
and `releaseLane=unselected`. A001/A002 remain unresolved after closure, not
performance passes. Historical N/A and excluded acceptance boxes stay unchecked;
they do not represent unfinished work within the approved review scope.
