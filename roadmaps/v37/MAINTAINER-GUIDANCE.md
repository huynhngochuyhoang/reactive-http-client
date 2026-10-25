# V37 Evidence Preservation and Maintainer Guidance

> **Recorded:** 2026-10-25
> **Scope:** Priority 9; preservation and guidance, not performance acceptance
> **Published / development:** `4.4.2` / `4.5.0-SNAPSHOT`

The later [V37-P10-1 decision](RELEASE-DECISION.md) approves reduced-scope
review-only/no-release closure, excluding A001/A002 from closure obligations
without resolving them. It supersedes pending lifecycle statements in this
dated P9 checkpoint, not its evidence or attribution limits.

This reconciles the [baseline](BASELINE-SCOPE.md),
[approved feasibility stop](FEASIBILITY-DECISION.md), dated N/A dispositions in
[the checklist](CHECKLIST.md) and [Priority 8 verification](VERIFICATION.md).
Guidance grants no new experiment, implementation, scope exclusion or release.
The final lifecycle decision is recorded separately; earlier dated records retain
their observations and limits, not authority over a later closure decision.

## Findings and Supported Behavior

| Finding / owner | Disposition | Supported alternative and reopening condition |
|---|---|---|
| A001 / AUTO_REGISTRY; performance maintainer with metrics-integration review | **unresolved/blocking**. No ordinary high-mode causal witness for the registry-selected workload. | Preserve required registry/observer behavior. Reopen only for a materially different bounded method connecting ordinary high/low work to its cause, followed by explicit numerical approval. Forced non-inlining is not the ordinary cause. |
| A002 / HOOK; performance maintainer with observer/hook review | **unresolved/blocking**, independently. Ordinary high stages 10 and 15 lack causal attribution. | Keep actual hook discovery, ordering, callbacks and terminal state. Require HOOK-specific ordinary-mode evidence; neither A001 nor AUTO_NO_REGISTRY explains it. Reopening and execution need separate approval. |
| E001 / attribution feasibility and evidence limits; benchmark tooling maintainer | Feasibility decision complete: **V37-P2-1 approved stop**. Attribution limits remain unresolved; no new tooling defect or correction is claimed. | Specify a concrete exporter/format and worker/phase/code/allocation correlation design, uncertainty and rejection rules before requesting another investigation. A tooling-defect claim needs a failing reproduction. |

Independent and combined cost acceptance did not pass. Original >20% latency and
>max(32 B/op, 5%) allocation review triggers remain unchanged. The saved V35
60 primary and four reverse rows per artifact retain three primary allocation
flags; clear reverse aggregates do not erase them. V36 diagnostics are not scores.
C001-C005 and both C004 subrows retain V35's no-change outcomes; rejected
optimizations remain rejected. No public performance number is promoted.

V37 launched **zero measurement workers**. V35's 338 workers and V36's 48/48
workers are exhausted; 96 inferred internal probes within V36's 194 accounted
JVM invocations are not an observed census. The approved stop grants no pilot,
probe, preflight, recorder smoke, tuning trial or automatic retry. Deterministic
synthetic tests and offline verification do not extend those budgets.

Published `4.4.2` remains the supported baseline; `4.5.0-SNAPSHOT` is not a
selected release or snapshot-upgrade remedy. There is no new configuration,
public API/SPI, dependency, metadata, meter or migration requirement. Dynamic
discovery, supports/order checks, fresh public metadata, policy mutation checks
and subscription-local body/context/reporting state remain intentional costs.
Reuse supported proxy lifetimes, not resolved requests or mutable caller state.
MINIMAL is not an equivalent substitute for required registry or hook behavior.

Application-supplied pools, schedulers, registries and SDKs stay application-owned.
Independent non-single-flight loads remain caller-owned and may outlive manager
close; late cache publication is rejected. Manager-owned flights/refreshes end at
shutdown. Removed meters cannot prove post-close work completion. Use existing
terminal/cleanup acknowledgements, not absent meter series, for that claim.

## Preservation and Access

Clean starting source: `2ee37be5f1d976c85b233ed1dc489a7dfb8895b8`, tree
`1d7485b43c6f82d2d2fcc707a81946777bfd24f3`. The new guidance/tests use that reachable
base plus a hashed patch. V1-V36 history, original bundles and runtime stay unchanged.

The [P8 inventory and limits](VERIFICATION.md#exact-reuse-and-limits) identify
the raw V35/V36 bundles. The trusted seals in [CHECKLIST.md](CHECKLIST.md) pin every
V37 P1-P8 bundle and its dated archive, including approvals, N/A dispositions and
the P8 review. Closure preservation verifies their complete copied and archived
contents, not just compressed archive hashes. V35 P9/P10 retain raw runtime,
consumer/native, score/API/packaging results; V36 P4/P5 retain actual diagnostic
workers. Small V37 audit bundles do not replace those original recordings.

Preserve failures alongside successes. P8's driver expected 83 tests after 92
passed; the obsolete-count failure and passing log remain intact, with remaining
checks resumed separately. Missing V35 P2-P8 recordings remain unavailable.
All 121 historical classpath paths per scored artifact remain missing; saved
shaded JARs and inventories are not a runnable old environment. Original
non-ancestor execution provenance is not renamed as a reachable integration
commit. No recording is regenerated under an old identity.

Bundles live in ignored `verification-evidence/` directories and dated archives,
outside Maven output. They must be supplied by the maintainer for a clean clone;
tracked hashes alone do not supply their contents. No off-machine backup is
claimed. Keep both verified copies before cleanup and never run root `mvn clean`
against the only evidence. Closure commands, exits, reports, classpath inventories,
input checks, approvals and source patch are retained under
`target/release-evidence/v37/closure/`, with a verified local copy/archive outside
Maven output. The external checklist seal avoids self-reference.

## Reproduction and Privacy

Run the existing read-only verifier against trusted tracked seals. It reads every
file without archive extraction or Java execution. Example for P8:

```bash
python3 -B scripts/verify-v36-evidence.py \
  verification-evidence/v37-priority8 \
  verification-evidence/v37-priority8-20261024.tar.gz \
  --manifest-sha256 a20c4b5859703f99bafac60347c2f3894a380f113d1de9685ad702f3d3020908 \
  --archive-sha256 e4b2f6bdaf941149619f56c4c60c416cb98a04849543974443f5fdba720c12d0
python3 -B -m unittest discover -s scripts -p 'test_*v3[3456]*.py' -v
```

Repeat with each original directory/archive/hash pair in the referenced checklists.
Synthetic negatives reject missing, corrupt, incomplete, unlisted or duplicate
content, changed seals, unsafe paths, links and truncated archives; no original
bundle is damaged to test rejection. Integrity does not establish causality.
The audit verifies **4,426 files across 15 bundles/archives** and **392 unchanged
runtime/build/test/fixture inputs**. Fresh checks pass **92 Python tests** and
**177 Java cases per Boot row**: 124 documentation, 18 metadata/example and 35
properties cases, with zero failures/errors/skips. Ordinary tests disable explicit
GC. Historical runtime/API/consumer/native evidence is reuse, not new execution.
Final preservation hashes are recorded in the checklist. Priority 10's named
scope exclusions and no-release decision remain pending; V37 remains active.

Use only bounded synthetic/sanitized evidence: no production bodies, headers,
identities, cache keys, request targets, arbitrary error text or credentials.
The verifier is not a sanitizer. Raw compiler traces or heap dumps are not safe
production attachments; follow [support rules](../../docs/26-support-bundles.md#performance-investigations).
B/op is allocation, not retained heap, direct/native memory or RSS. Use
[time-aligned memory and lifecycle evidence](../../docs/30-operations-troubleshooting.md#cache-memory-triage-420)
and [performance triage](../../docs/25-performance-troubleshooting.md#default-path-cost-and-ownership).
This work neither diagnoses Kubernetes/Istio/Envoy nor provides a pod-memory
all-clear. No new telemetry or production investigation is authorized.
