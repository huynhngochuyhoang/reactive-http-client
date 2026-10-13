# V36 Bounded Allocation Investigation

> **Plan:** V36-P3-1; frozen 2026-10-13 before new measurement
> **Status:** approved in Priority 3.3 on 2026-10-13; execution not started
> **Findings:** V36-A001 / AUTO_REGISTRY; V36-A002 / HOOK; V36-E001 / evidence reliability
> **Ceiling:** 48 worker forks; no pilots or retries
> **Implementation and release scope:** unselected

This is the experiment specification for [Priority 3](CHECKLIST.md), following
the [workload and evidence audit](EVIDENCE-AUDIT.md). Started from clean reachable
commit `56bb5753cd1c247654360c86a5257a1c2aaf201c`, tree
`48dffc591d23fe13ea0158d7c41f5e3f5acaab73`. The planning delta is documentation
and its regression guard only. No new benchmark or diagnostic fork was run.
V35's **338-fork history**, including its **exhausted 36-fork extension**, grants
no new budget. A001/A002 remain unresolved/blocking. C001-C005 and both C004
subrows retain their closed no-change outcomes; no production repair is presumed.

## Hypotheses and Refutation

Evaluate each profile independently, including contrary evidence:

| Hypothesis | Distinguishing observation and refutation boundary |
|---|---|
| H1: ordinary compiler-dependent materialization | The same saved JAR reaches different allocation modes; a correctly associated ordinary trace distinguishes the relevant resolver/selection allocation and installed-code histories. Same-mode histories, unchanged relevant code across modes, or a different allocation site weaken/refute this specific explanation. The forced intervention alone cannot confirm H1. |
| H2: instrumentation changes which modes are observable | Compare ordinary O with marker-only M, then M with traced T on each artifact/profile. A mode disappearing or shifting only with instrumentation supports perturbation; overlapping observed modes weaken that explanation but small samples cannot prove absence of perturbation. Never substitute M/T/I scores for O. |
| H3: workload or phase mismatch | Retain the P2 semantic witnesses and the exact publisher-assembly row. Unexpected subscription, different effective beans/features, or a different benchmark/scenario rejects equivalence. Passing witnesses refute those tested mismatches, not all possible harness defects. |
| H4: artifact, dependency or VM-input difference | Verify both saved shaded artifacts, input inventories, executable-class equality, ordered classpaths and VM arguments. A mismatch stops comparison. Identical executable inputs rule out a differing class implementation, not class-loading order, metadata-sensitive behavior or compiler decisions. |
| H5: host/order effects | Preserve both artifact orders, same-JAR controls and host pressure/quota observations. An order-correlated shift without corresponding compiler evidence leaves attribution open. Boundary observations cannot prove the workstation was uncontended throughout each iteration. |

The suspected 256 B/op increment is a question, not an imposed answer. Report all
five measurement iterations and each fork mean. For both target profiles, use
historical descriptive bands **1744 +/- 32 B/op** (low) and **2000 +/- 32 B/op**
(high); outside-band or mixed-iteration forks stay explicitly other/mixed. These
labels are not acceptance thresholds and must not be widened after seeing data.
Keep the existing **>20% latency** and **>max(32 B/op, 5%) allocation** review
triggers. Compare like lanes only, show distributions and uncertainty, and retain
all original V35 flags. A missing mode is not evidence that the mode cannot occur.

## Immutable Inputs

Use the saved V35 P10 pair, not a new snapshot build or a fresh dependency resolve.
`PAIR=verification-evidence/v35-priority10`; B means `build-baseline`, C means
`build-current`. Paths can be relocated only with a recorded mapping and unchanged
hashes. The baseline's Central provenance is reused, not freshly verified here.

| Input | SHA-256 |
|---|---|
| B `benchmarks.jar` | `e0de82156b03f42c986f10fd9a66229956df64189e5de605b345affccdd48d6c` |
| C `benchmarks.jar` | `b9fd5b42980c03cd8c0415f68101aef553e266354b672ea4f3b3ca992a3376b9` |
| B `inputs.json` | `704233da818cf56c31435170538d2dc523d41d93c06c28cc725d1cd8cea76dd6` |
| C `inputs.json` | `56dd6782d72e40e25de6f0a12b6bec8b2f9e8cbe6f74dc60a82958307152481f` |
| JDK `bin/java` | `2b9272f8d8ec174cdc2679ec62bcbc8cd785b095a511498ec80392b16accc543` |
| JDK `release` | `954ece6266f0ea0b4e06ac7924c18db129f126de48d7bbcbe9fc9587b7c618ee` |
| JDK `lib/server/libjvm.so` | `7ccd2a82c8231b70cc3db61f8cb0073c42e1fd4f80aee30477b9e6c95499328a` |
| JDK `lib/modules` | `914f00f14c4271869c57ccc065f23ecaa2d37746772ff260a0fc39cc30ba1f68` |

These eight hashes were rechecked during planning. The sealed inventories pin
all 39 harness/build inputs and 120 non-starter dependencies, with starter
`4.4.2` for B and saved `4.5.0-SNAPSHOT` for C. Reuse the existing
[`audit_pair`](../../scripts/investigate-v35-allocation.py) input checks; do not
invoke its historical runner to execute this plan. Recheck individual dependency
hashes and all shaded executable classes before execution, not just filenames.
Missing saved evidence stops execution; rebuilding it is a new approved plan.

Pinned stack: Oracle **Java 21.0.8**, JMH **1.37**, Boot **4.0.0**, Spring **7.0.1**,
Reactor **3.8.0**, Reactor Netty **1.3.0**, Netty **4.2.7.Final**, Jackson 3
**3.0.2**, Micrometer **1.16.0**, Resilience4j **2.4.0**, Caffeine **3.2.3**.
JDK home is `/usr/lib/jvm/jdk-21.0.8-oracle-x64`. Boot 4.1.0 documentation checks
are not a performance stack change. Another JDK/stack needs separate approval.

Only `V34DefaultPathBenchmark.defaultV34NoNetworkWarmPublisher`, `scenario=GET`,
profiles `AUTO_REGISTRY` and `HOOK` are selected. Keep the real registry/observer
and application-hook beans and untimed setup witnesses described by P2. Each
operation returns a fresh cold Mono without dispatch; no measured subscription,
transport, construction or first-use work is added. MINIMAL, AUTO_NO_REGISTRY and
RESILIENCE_ENABLED_ONLY are not selected as additional profiles.

## Common Measurement Contract

Every stage contains exactly one profile, one artifact, one JMH result row and
one worker: `-f 1 -wf 0 -bm avgt -tu ns -t 1 -wi 5 -w 1s -i 5 -r 1s -prof gc
-foe true -rf json`. Report latency in ns/op and normalized allocation in B/op.
Common launcher/worker flags are:

```text
-Xms512m -Xmx512m -XX:ActiveProcessorCount=2
-Dorg.slf4j.simpleLogger.defaultLogLevel=warn
```

Retain JMH's generated blackhole/compiler options and record the actual child
arguments. Do not silently add explicit-GC, tiering, heap, collector, class-loading
or scheduler tuning. Reject inherited `JAVA_TOOL_OPTIONS`, `_JAVA_OPTIONS` and
`JDK_JAVA_OPTIONS`. The launcher retains the saved `BenchmarkRunner` and metadata
properties for project/starter/baseline/Boot/source, with stack context V36-P3-1.
The saved source commit is not relabeled as the new runner's commit.

All lanes use the same classpath order: saved shaded JAR first, one small external
boundary-recorder JAR second; invoke the existing `BenchmarkRunner` main class.
That JAR may contain only the diagnostic profiler and its own support classes,
no shadowed benchmark/starter/dependency classes or agent. O does not select or
load its profiler. This common launch layout differs from historical `-jar`;
new observations do not replace historical scores. Seal the recorder, runner,
reader, source patch and this plan before the first experiment process. Their
implementation in Priority 4 must satisfy the contract below without changing
the saved harness. Any change to the frozen experiment design needs new approval.

## Schedule and Lanes

Use this exact order; no result-selected branching. For each artifact slot visit
AUTO_REGISTRY then HOOK on odd slots, HOOK then AUTO_REGISTRY on even slots.
Reset slot numbering in each lane. Each visit is a new JVM in a new directory.

| Lane | Stage IDs | Artifact slots | Forks | Added instrumentation / purpose |
|---|---|---|---:|---|
| O | 01-16 | B C C B B B C C | 16 | GC profiler only; lowest-perturbation ordinary observations |
| M | 17-24 | B C C B | 8 | O plus boundary recorder only; isolate recorder perturbation |
| T | 25-40 | B C C B B B C C | 16 | M plus compiler XML and class-load logs; ordinary traced history |
| I | 41-48 | B C C B | 8 | T plus resolver non-inlining only; single-factor intervention |

Thus **16 + 8 + 16 + 8 = 48 worker forks**. O/T each give four workers per
artifact/profile: forward B/C, reverse C/B, then explicit B/B and C/C same-JAR
controls. M/I give two workers per artifact/profile in forward/reverse order.
Priority 4 owns O/M/T (40 forks); Priority 5 owns I (8 forks). Keep the lanes
separate in every report. No allocation sampling, JFR, forced inlining, disabled
escape analysis, extra control profile, warmup fork or uncounted pilot is selected.

T adds exactly:

```text
-XX:+UnlockDiagnosticVMOptions -XX:+LogCompilation
-XX:LogFile=compilation-%p.xml
-Xlog:class+load=info:file=classes-%p.log
```

I adds only this directive to T:

```text
-XX:CompileCommand=dontinline,io.github.huynhngochuyhoang.httpstarter.core.EffectiveCachePolicy::resolveSelection
```

Verify the directive was honored in that worker's relevant compilation history.
Failure to observe an effect is a result, not permission for another directive.
Intervention timing/allocation is diagnostic only, not an ordinary high witness,
production advice, benefit claim or release-quality score.

## Identity and Measurement Boundaries

P2's reader intentionally cannot establish legacy fork identity. Do not restore
timestamp-to-score joins. Priority 4's minimum recording implementation must:

1. Create an exclusive stage directory, persist a unique stage ID and the sealed
   plan/artifact/tool hashes before spawn, and pass the stage ID to the worker.
   The supervisor records the owned launcher/process group and exactly one
   `ForkedMain` descendant's PID, process start identity, command and classpath
   while it is alive. One `-f 1` row joins to that one worker, not sorted times.
   Missing, duplicate or unexpected workers make the stage unusable and stop it.
2. For M/T/I, use JMH 1.37's existing `InternalProfiler.beforeIteration` and
   `afterIteration` callbacks, outside the timed benchmark method. Record stage,
   PID, actual benchmark/profile/scenario, VM arguments, code-source paths,
   iteration type and sequential index, JVM uptime and monotonic timestamps.
   Require five matched warmup and five matched measurement envelopes plus a
   completion record; buffer small records and write outside the timed operation.
   Cross-check worker identity against the supervisor and T/I compiler XML
   properties, PID/filename and exact class-log inventory/origins.
3. Treat these as **iteration envelopes, not exact timed-loop boundaries**. Include
   profiler/setup/teardown overhead and clock precision uncertainty when joining
   JVM-uptime compilation stamps. An event near an envelope boundary stays
   ambiguous. Preserve every relevant task, completion, installation and
   invalidation, including superseded tasks and tasks with no elimination event.
   Never select the last compilation as proof of executed measurement code.
4. Keep O's measurement boundary unknown: it has worker identity but no recorder
   or compiler trace. M/T/I add observations and can perturb compilation. Even
   a version installed throughout an envelope is not proof that it executed.
   An observed ordinary high mode without an adequate causal trace remains open.
5. Before experiments, test missing/duplicate/substituted stage or PID, reordered
   logs, wrong artifact/profile/flags, truncated XML, mismatched iteration pairs,
   clock overlap, failure exits and unrelated/superseded code using deterministic
   fixtures. Extend the existing reader rather than a second benchmark framework;
   it must not silently accept the old multi-fork records as the new format.

The local JMH 1.37 API was inspected while planning; no recorder implementation
or runtime capture is claimed yet. Hash-sealing an implementation does not waive
these acceptance checks. If this mechanism cannot provide the required identity
or timing evidence, stop and record E001 unverified, not a guessed attribution.

## Resource Bounds and Stop Rules

- Maximum 48 attempted worker slots and 48 launchers, sequentially. A slot is
  consumed on launcher spawn, including failure before worker creation. Permit
  only two additional toolchain preflight JVMs: `java -version` and the common
  flags plus `-XX:+PrintFlagsFinal -version`, each at most 30 seconds. No other
  experimental JVM/pilot is authorized. Compilation and deterministic JUnit
  checks are not measurement and must finish before the schedule starts.
- Each stage has a **120-second** launcher/worker-group timeout, including setup,
  teardown and shutdown. Cap cumulative active investigation wall time at
  **120 minutes**, including preflight, checks and boundary waiting; pausing
  between Priorities 4/5 does not reset consumed time or fork slots. Raw stage
  deadlines alone total at most 96 minutes; this is a ceiling, not a target.
- Before every stage require at least **3 GiB available RAM**, **16 GiB free
  disk**, two allowed CPUs and, if limited, two CPUs of cgroup quota and 3 GiB of
  cgroup memory headroom. Record unavailable quota/pressure information as an
  observation limit, never as confirmed host exclusivity. Stop if required memory,
  disk or CPU bounds cannot be established; do not silently lower them.
- Record host CPU/affinity/cgroup/pressure/memory and visible processes before and
  after stages. Run no concurrent agent-started Maven, tests, native builds or
  profilers. Ask the operator to avoid competing work. Monitor the owned group
  once per second; abort above **2 GiB aggregate RSS**, **256 MiB stage output**
  or **8 GiB total raw output**. These are sampled abort thresholds, not hard OS
  allocation guarantees; retain overshoot/failure evidence. Never kill unrelated
  processes or change host tuning to force a run.
- Abort the remaining schedule on timeout, interrupt, nonzero exit, input drift,
  malformed/incomplete result, identity failure or breached resource bound.
  Terminate and reap only the owned process group, preserve partial files and
  record attempted/unattempted IDs. No automatic retry or replacement stage.
  Missing modes do not stop or extend the fixed successful schedule.
- After 48 attempts, or an earlier abort, stop. Failures, absent high modes and
  exhausted time are not approvals for additional execution. Any resumption of
  an aborted schedule or extension needs a **new recorded maintainer decision**.

## Preservation and Decision

Use fresh `target/release-evidence/v36/priority4/` and `priority5/` directories for
execution; never overwrite the saved pair or prior runs. Copy verified raw files,
commands/exits, process identities, environment, plan/tool hashes, iteration
records, all scores, failures and derived reports to `verification-evidence/`
and verify an archive **before any cleanup**. Privacy remains synthetic-only.
An ignored path is not an artifact available to another clone; preserve availability
limits and restore verified copies before reproduction. Priority 3's planning
checks live under `target/release-evidence/v36/priority3/` separately.

Full explanation requires each profile's own ordinary evidence and a compatible
one-factor result on both artifacts, with perturbation and contradictory evidence
accounted for. Budget completion, a clear mean, same-JAR class equality, a forced
high mode or non-reproduction is not resolution. Preserve unresolved/blocking
status wherever the causal chain is missing. E001's new capture is verified only
by valid new records, not approval of this design. Production selection remains
Priority 6.2; matched acceptance Priority 7; release/closure Priority 10.

### Priority 3.3 Approval

On 2026-10-13 the maintainer answered **"Approve V36-P3-1 (Recommended)"** to:

> Priority 3.3: approve V36-P3-1 in roadmaps/v36/INVESTIGATION-PLAN.md for later
> execution: at most 48 AUTO_REGISTRY/HOOK worker forks, 120 minutes, fixed
> ordinary/marker/trace/non-inlining lanes, and no retries after failure without
> a new decision? This approves investigation only, not production changes or
> a release.

The approved plan before this status/decision update has SHA-256
`342fd51e17fc94fe4a313b151bb347c1a256075d99f8f966b4ead12d7cc85bf2`;
its exact bytes are preserved in the Priority 3 bundle as
`approved-plan-before-status-update.md`. No experiment design changed with this
approval record. All 48 worker slots remain unused. Priority 3 is complete;
implementation of capture and execution stay in later Priorities 4/5, not this
planning step. Production selection and release gates remain unselected.

### Planning Verification

The documentation guard checks schedule arithmetic/order, input-hash inventory,
local links, approval and unexecuted later gates. All 110 documentation cases pass
on each of Boot 4.0.0 and 4.1.0 with explicit GC disabled; 63 Python tooling tests
and `git diff --check` pass. Commands, exits, XML reports, eight rechecked input
hashes and source patch are preserved in `verification-evidence/v36-priority3/`
and its mirror under `target/release-evidence/v36/priority3/`. The checklist records
the external seal. The initial test run succeeded but collection used an incorrect
report filename; preserve that failed collection separately, not as a new test
failure or an accepted complete evidence run. No fresh workload, full-reactor,
API, consumer, native, scored-performance or diagnostic experiment is claimed.
