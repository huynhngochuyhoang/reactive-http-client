# V36 Intervention Accounting Amendment

> **Proposal:** V36-P5-1; 2026-10-14
> **Status:** approved on 2026-10-14; execution recorded separately
> **Scope:** remaining I-lane stages 41-48 only
> **Findings:** A001 and A002 remain unresolved/blocking
> **Implementation and release scope:** unselected

This amends only process accounting for the remaining intervention in
[V36-P3-1](INVESTIGATION-PLAN.md). It does not modify that frozen historical plan
or repeat [Priority 4](ORDINARY-CAPTURE.md). Preparation starts from clean
`fe13031f171dd33fc86e6ee5392c2b83a14407e4`, tree
`5f68a86a46b2d91e967042f0d97e0115c009b06c`.

The maintainer accepted Priority 4's internal-probe exception, but explicitly
left Priority 5 gated on revised accounting. A request to implement Priority 5
does not by itself approve this newly specified accounting limit.

## Approved Accounting

Keep the saved JDK, artifacts, dependencies, harness, external recorder bytes,
classpath order, workload, warmup/measurement settings and generated blackhole
options unchanged. Do not add agents, process tracing, allocation sampling or
blackhole-mode overrides. The only workload intervention remains resolver
non-inlining relative to the T lane:

```text
-XX:CompileCommand=dontinline,io.github.huynhngochuyhoang.httpstarter.core.EffectiveCachePolicy::resolveSelection
```

| Category | Priority 4 history | Priority 5 ceiling | Combined accounting |
|---|---:|---:|---:|
| Launchers | 40 | 8 | 48 |
| Measured workers | 40 | 8 | 48 |
| Explicit toolchain preflights | 2 | 0 | 2 |
| Internal blackhole probes, inferred | 80 | 16 | 96 |
| JVM total, including inferred probes | 162 | 32 | 194 |

Budget two internal `java -version` probes per attempted launcher, including
failed stages, with no reclaim or retry. The saved JMH 1.37
`CompilerHints.compilerBlackholesAvailable()` bytecode contains the two probe
calls: both use `-XX:+UnlockExperimentalVMOptions` and
`-XX:CompileCommand=blackhole,some/fake/Class.method`; the first also uses
`-XX:CompileCommand=quiet`. They run no benchmark workload.

This is explicitly **inferred accounting, not an observed process census**.
Individual probe PID, exit status and peak RSS remain unavailable. Before
execution, verify the pinned JMH class bytes against Priority 4's audit; for
each completed stage require the expected compiler-blackhole autodetection log.
Record the probe allowance and inference separately from observed worker
identities. Missing or changed evidence stops the schedule; do not call inferred
probe counts independently measured or individually verified. Unexpected observed
JVM children also stop execution. The approval accepts this observation limit
prospectively; it does not repair Priority 4's missing process inventory.

Keeping the same probe behavior avoids adding a second instrumentation factor
only to I. If a complete probe census is required instead, keep execution gated
and approve a different recording design before running anything.

## Remaining Schedule and Bounds

| Stage | Profile | Artifact |
|---|---|---|
| 41 | AUTO_REGISTRY | B |
| 42 | HOOK | B |
| 43 | HOOK | C |
| 44 | AUTO_REGISTRY | C |
| 45 | AUTO_REGISTRY | C |
| 46 | HOOK | C |
| 47 | HOOK | B |
| 48 | AUTO_REGISTRY | B |

No O/M/T rerun, extra profile, pilot, warmup fork, preflight or replacement is
authorized. Charge an attempted stage on launcher spawn, even without a worker.
Carry forward **502.14051217597444 seconds** already consumed; remaining active
time is **6697.859487824026 seconds** of the original 7,200-second budget. The
120-second stage deadline includes probes and shutdown. Retain the original
3 GiB available RAM/cgroup headroom, 16 GiB disk, two CPU minimum, sampled 2 GiB
owned-group RSS, 256 MiB stage-output and cumulative 8 GiB raw-output bounds.
Include Priority 4 raw output in the cumulative bound. No concurrent agent-started
builds/tests/profilers during execution. Abort and preserve partial evidence on
failure, input drift, identity mismatch or limit breach; **no automatic retry**.

Reuse the hash-verified Priority 4 recorder and saved artifact pair; seal the
approved amendment and final runner/reader patch before spawning a launcher.
Store new evidence under `target/release-evidence/v36/priority5/`, then copy and
verify an archive outside build output before any cleanup. Do not overwrite
Priority 4, earlier V36 or V35 evidence. All current raw scores remain unchanged.

## Causal Acceptance and Decision

Verify non-inlining in each worker's relevant compiler history, retaining
contrary tasks, superseded code and invalidations. Failure to observe the intended
effect is a result, not permission for another directive. Compare I with T per
profile/artifact; do not pool lanes or substitute intervention scores for ordinary
scores. Forced high allocation is not an ordinary high-mode witness.

A001 lacks an ordinary high trace; A002's two ordinary high forks are untraced.
An intervention alone cannot fill either gap. Disposition each finding separately
and preserve unresolved/blocking status if the causal chain remains incomplete.
E001 can verify new worker association and envelopes, not exact executing-code
identity or retroactive legacy attribution. No production change, performance
all-clear or release is approved here.

**Approval request:** approve V36-P5-1's eight remaining workers and sixteen
inferred internal probes, accepting the missing individual probe inventory,
with the original cumulative time/stop limits and no retries? This authorizes
investigation only.

On 2026-10-14 the maintainer answered **"approve v36-p5-1"**. This approves the
eight remaining workers and sixteen inferred internal probes within the limits
above, including the unavailable individual probe inventory. It does not approve
production changes, finding acceptance, a retry or a release. The earlier
proposal and its verification remain preserved separately; this approved
amendment will be sealed before execution. Results belong in a separate record,
not a rewrite of this approval checkpoint.

## Preparation Verification

All **112 documentation cases per Boot row** (4.0.0 and 4.1.0) pass with zero
failures/errors/skips and explicit GC disabled. The guard checks the exact eight
stage identities, accounting arithmetic and pending execution gate.
`git diff --check` passes. The frozen plan and Priority 4 manifest/archive hashes
still match their recorded values. Commands, exits and test reports are in
`target/release-evidence/v36/priority5/accounting-proposal/`. These are preparation
checks, not experiment workers or causal results. No runtime, native, API or
performance acceptance rerun is claimed.
