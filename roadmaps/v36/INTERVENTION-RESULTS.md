# V36 Causal Intervention Results

> **Recorded:** 2026-10-14
> **Status:** Priority 5 complete; allocation findings unresolved/blocking
> **Authorization:** V36-P3-1 plus approved V36-P5-1
> **Budget:** all 48 worker slots consumed; no retry or further experiment authorized
> **Implementation and release scope:** unselected

This records the eight remaining one-factor controls from the
[frozen investigation](INVESTIGATION-PLAN.md), under the explicitly approved
[accounting amendment](INTERVENTION-ACCOUNTING.md). It complements, and does not
replace, the [ordinary capture](ORDINARY-CAPTURE.md). Approval and successful
execution are not acceptance of either allocation finding.

## Source and Inputs

The base is reachable `fe13031f171dd33fc86e6ee5392c2b83a14407e4`, tree
`5f68a86a46b2d91e967042f0d97e0115c009b06c`, plus the sealed tooling, test and
documentation patch. The maintainer explicitly answered **"approve v36-p5-1"**
before execution. No production code, public surface, dependency, version,
workload fixture or measured benchmark method changed.

Reused the saved Central-provenance `4.4.2` / saved `4.5.0-SNAPSHOT` pair,
Java 21.0.8, Boot 4.0.0 and JMH 1.37, with all eight pinned input hashes,
ordered 121-JAR classpaths and 39 harness/build inputs verified. The recorder
JAR is byte-identical to Priority 4:
`3f667df708ab1cb26eb8992086687fb6ed6105498d902212ed42c52d8f473567`.
Capture input-seal SHA-256:
`407dcb67c57593c74962de641458972aaae756eeb6ba7d05483935138641b615`.
The frozen original plan and approved amendment were sealed before execution.
No artifact was rebuilt or dependency resolved for measurement.

The [existing runner](../../scripts/capture-v36-allocation.py) now supports the
remaining intervention with the prior ledger, cumulative time/output limits,
explicit spawn accounting and an exclusive non-retryable execution ledger.
It rejects changed evidence and unexpected observed JVM children. The
[shared reader](../../scripts/review-v35-allocation.py) distinguishes an issued
directive from observed compiler refusal, retaining contrary inlining rather
than assuming the directive worked. New
[synthetic tests](../../scripts/test_v36_intervention.py) exercise those boundaries
without launching a measured JVM. Existing O/M/T reading remains unchanged.

## Execution and Accounting

All stages **41-48** ran once, in the approved B/C/C/B slot order, with two
workers per profile/artifact. All eight stage exits were zero and all records
validated. Each has one observed worker identity, one result row, ten paired
iteration envelopes and an associated compiler XML/class-load pair. All **40
measurement iterations** are retained. No preflight, pilot, warmup fork,
replacement, extra profile, agent, process tracing or changed blackhole setting
was introduced. The user's retry resumed preparation before any worker started.

New active execution consumed **101.688487 seconds**; with Priority 4's
502.140512 seconds, cumulative consumption is **603.828999 seconds** of 7,200.
Remaining time does not authorize new workers: **48/48 slots are exhausted**.
Host checks and sampled RSS/output limits passed, including the prior capture
in the cumulative raw-output bound. No agent-started build, test or profiler ran
concurrently. This establishes neither an uncontended host nor continuous peaks.

The pinned JMH probe class matched Priority 4's audit, and all eight launcher
logs reported compiler-blackhole autodetection. The new allowance charges **16
internal probes**; successful-path inference is also 16. Together with Priority
4 this is **96 inferred probes**, 48 launchers, 48 workers and two preflights:
**194 JVM invocations including inferred probes**, not an observed process census.
Individual probe PIDs, exits and peak RSS remain unverified under the accepted
limit. The amendment is prospective approval, not a repair of historical records.

## Diagnostic Observations

These are ranges of fork means, not confidence intervals. T is the unchanged
Priority 4 traced ordinary lane; I adds only resolver non-inlining. Each I fork
and every one of its five samples falls in the frozen high band. No result or
iteration was discarded. Raw samples, JMH errors, per-fork sample deviations and
between-fork deviations remain in the bundle. In particular, the baseline
AUTO_REGISTRY I fork at 574.565 ns/op remains included despite its wider timing.

| Profile | Artifact | T low forks | T B/op range | I high forks | I B/op range | I ns/op range |
|---|---|---:|---|---:|---|---|
| AUTO_REGISTRY | B | 4 | 1744.0322-1744.0338 | 2 | 2000.0353-2000.0417 | 486.022-574.565 |
| AUTO_REGISTRY | C | 4 | 1744.0316-1744.0336 | 2 | 2000.0360-2000.0368 | 482.940-501.934 |
| HOOK | B | 4 | 1744.0336-1744.0370 | 2 | 2000.0377-2000.0391 | 519.798-528.826 |
| HOOK | C | 4 | 1744.0354-1744.0366 | 2 | 2000.0386-2000.0391 | 530.558-545.557 |

I-minus-T differences of allocation fork means are respectively **256.0054**,
**256.0033**, **256.0029** and **256.0029 B/op**. These are intervention contrasts,
not baseline/current regressions, scored benefits, public numbers or replacement
scores. Two I forks per cell do not establish a mode frequency or a stable
latency distribution. No instrumentation correction or subtraction was applied.

## Compiler Evidence

Across I, the retained histories contain **763 relevant compilation tasks** and
**763 installation records**. There are **28 resolver call-site refusals** with
reason `disallowed by CompileCommand`, distributed across all eight workers;
no resolver `inline_success` is present in these histories. No retained I task
records elimination of `EffectiveCachePolicy$Selection`. In contrast, T has
resolver inlining in every worker and 40 tasks with Selection elimination.
Neither lane records CachePolicyConfig elimination in these retained tasks; do
not attribute the delta to that type based on an assumption.

All retained relevant I installations precede the measurement envelopes.
Superseded code, invalidations and tasks without elimination events remain
available. `measurementInterval=null` and `executingCodeIdentity=unverified`
remain intentional: iteration envelopes are not exact timed-loop boundaries,
and installed code is not evidence of which version actually ran.

This controlled result supports sensitivity to the resolver inlining boundary
in each profile independently. It does not establish that one Selection object
accounts for 256 bytes, an exact object count per operation, or that all changed
allocations originate at that allocation site. A non-inlining directive can alter
optimization across its callers; aggregate B/op and compiler events are not exact
per-operation allocation attribution. No allocation sampling or executing-code
sampling was authorized or added.

## Causal Chains and Dispositions

| Finding | Evidence chain and contrary evidence | Disposition / owner |
|---|---|---|
| A001 / AUTO_REGISTRY | Matched inputs and publisher-assembly witnesses; all O/M/T observations low; I high on B and C in both orders, with compiler refusals. No ordinary high trace or newly observed ordinary high mode exists. | **Unresolved/blocking**; performance maintainer. H1 is supported as a possible controlled mechanism, not established for the historical flag. |
| A002 / HOOK | Matched inputs; same-JAR O high/low split on both artifacts; M/T low; I high on B and C with compiler refusals. Both O-high workers lack traces. | **Unresolved/blocking**; performance maintainer. The forced mode is compatible with the magnitude, but cannot supply the missing ordinary high witness. |
| E001 / evidence reliability | Legacy timestamp pairing remains rejected; all 48 new stage associations revalidated, with complete per-worker envelopes/traces where selected. Missing, malformed, substituted and failed evidence is rejected by synthetic guards. | Recording/validation correction **verified for the new capture**, not a runtime defect. Evidence maintainer owns the remaining limits: legacy pairing, exact executing code and individual probe inventory remain unverified. |

H2 remains instrumentation perturbation **or limited sampling**: the I control
does not distinguish why ordinary high modes disappeared from M/T. H3's tested
workload/phase mismatches and H4's executable-input drift are excluded within
the reused semantic and hash checks, not universally. H5 host/order effects are
not ruled out by boundary snapshots or the small fixed sample. No explanation
from AUTO_NO_REGISTRY is transferred to either profile.

No reproduced production defect is selected. Priority 5 completes the bounded
investigation and classifications, **not finding acceptance**. Priority 6 must
retain these gaps when evaluating correction or no-change options. Further
experiments need a new explicit decision; no silent extension, runtime tuning,
production edit, performance all-clear, memory-leak diagnosis or release follows.

## Verification and Preservation

Fresh verification passes **83 Python tooling tests** and **113 documentation
cases per Boot row** (4.0.0 and 4.1.0), zero failures/errors/skips, with explicit
GC disabled for the Java checks. Before capture, 83 tooling tests and 112
documentation cases per Boot row passed; the final additional case guards this
result and the completed budget. The unchanged 40-case workload and recorder
callback evidence is reused from Priority 4, not claimed as freshly executed.
No full reactor, API, assembled-consumer, native or release scoring rerun is
claimed for this tooling-only delta. `git diff --check` passes.

Re-analysis reproduced all **48** saved stage reports, rehashed **597** Priority
4 bundle files and **931** V35 P10 files without new forks. Historical scores and
evidence are unchanged. Commands, exits, approved amendment, source patch, seals,
all raw results/identities/traces and derived comparisons are preserved in
`target/release-evidence/v36/priority5/`, copied to
`verification-evidence/v36-priority5/` and a verified dated archive. The checklist
records the external integrity seal. Ignored evidence is not available in a
clean clone; restore hash-verified recordings before analysis. Never run root
`mvn clean` against the only copy.

Executed commands (historical reproduction, **not permission to rerun**):

```bash
python3 -B scripts/capture-v36-allocation.py prepare-intervention \
  --pair verification-evidence/v35-priority10 \
  --output target/release-evidence/v36/priority5/capture \
  --prior verification-evidence/v36-priority4
python3 -B scripts/capture-v36-allocation.py intervention \
  --pair verification-evidence/v35-priority10 \
  --output target/release-evidence/v36/priority5/capture
```

Existing ledgers reject resumption. Choosing a different output path does not
grant another budget; failures or missing modes require a new recorded decision.
