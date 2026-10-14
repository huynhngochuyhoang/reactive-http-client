# V36 Ordinary Allocation Capture

> **Recorded:** 2026-10-13
> **Status:** Priority 4 complete with accepted probe-accounting exception; findings unresolved
> **Authorization:** V36-P3-1; 40 of 48 worker slots consumed
> **Remaining:** intervention IDs 41-48 unexecuted; accounting revision needed before Priority 5; no ordinary retry
> **Findings:** A001 and A002 remain unresolved/blocking
> **Implementation and release scope:** unselected

This executes O/M/T from the [approved plan](INVESTIGATION-PLAN.md), after the
[Priority 2 audit](EVIDENCE-AUDIT.md). V1-V35 evidence and scores are unchanged.
The plan's statement that all slots were unused is its historical approval
checkpoint; the execution ledger below is the current budget state.

## Source and Capture Boundary

Started from clean reachable `3d9253ca25aae8190d7a8b37116a04a7b8fa15b2`, tree
`92d0f0a63adfe30b53f8fd9a6c291ae3da106e8d`. Capture used that base plus a sealed
tooling/test patch. The source patch, input manifest, recorder JAR and frozen plan
were saved before the first experiment process. No production code, dependency,
saved benchmark/fixture, version or public surface changed.

- The [runner](../../scripts/capture-v36-allocation.py) executes one profile,
  one saved artifact and one worker per fresh stage. A stage token, seal digest,
  launcher group, worker PID/start ticks, command and classpath bind its single
  result row to the observed process. It never uses timestamp sorting as identity.
- The [external recorder](../../.github/v36-boundary-recorder/BoundaryRecorder.java)
  uses JMH's existing iteration callbacks, not the measured method. M/T record
  worker identity, VM arguments, code sources and ten paired warmup/measurement
  envelopes. Bracketing each uptime read with monotonic clocks preserves sampling
  uncertainty. O neither selects the profiler nor records a compiler trace.
- The [shared reader](../../scripts/review-v35-allocation.py) retains legacy
  unverified attribution rather than upgrading old recordings. Its V36 path
  cross-checks each stage/worker/row, paired envelopes, compiler PID/arguments/
  classpath, exact log inventory and required loaded-class origins. It preserves
  relevant tasks, completion, installation and invalidation, including superseded
  versions and tasks without elimination events.
- Synthetic [capture tests](../../scripts/test_v36_capture.py) reject missing,
  duplicate, substituted, failed and mismatched records, broken iteration pairs,
  overlapping clocks, truncated XML and insufficient resources. They verify the
  fixed schedule, owned-group cancellation and refusal to rerun an existing ledger.
  The [callback test](../../.github/v36-boundary-recorder/BoundaryRecorderTest.java)
  runs callbacks directly without a JMH runner or workload.

The initial preparation was preserved separately when clock-read bracketing was
added before measurement. Neither preparation ran a benchmark or diagnostic pilot.
The final recorder callback test passed and the final inputs were resealed before
the two authorized toolchain preflight JVMs and stage 01. Both preparations remain
in the bundle; only the final `capture/` inputs produced these observations.

## Inputs and Budget

All eight plan-pinned JDK/artifact/inventory hashes matched. Both ordered
121-JAR classpaths were verified, including the starter and 120 non-starter
dependencies per side; the 39 harness/build inputs and shaded executable classes
matched their saved pair contract. Rehashed all **931** V35 P10 manifest entries
after capture. This is exact saved-input reuse, not a fresh Central download or
new benchmark build. The plan's Java 21.0.8 / Boot 4.0.0 / JMH 1.37 stack,
512 MiB heaps, two active processors, one worker/thread, GET warm-publisher row,
five one-second warmups and five one-second measurements stayed unchanged.

| Lane | Completed IDs | Workers | Measurement iterations | Trace/boundary evidence |
|---|---|---:|---:|---|
| O | 01-16 | 16 | 80 | Worker identity only; no measurement interval or compiler attribution |
| M | 17-24 | 8 | 40 | Worker identity and iteration envelopes; no compiler tracing |
| T | 25-40 | 16 | 80 | Worker identity, iteration envelopes, compiler XML and class-load logs |
| I | 41-48 | 0 | 0 | Not run; reserved for Priority 5 |

Both toolchain preflights and all **40 launcher/worker stages exited 0** and
validated. The supervisor ran **502.141 seconds** of the 7,200-second active
schedule ceiling, including preflight, stage checks and inter-stage work. Carry
that consumption into Priority 5; do not reset it. No failed/interrupted stage,
workload pilot, replacement fork or result-selected retry occurred. The user's `retry`
message caused monitoring of the existing process, not a restarted capture.

### JMH Probe Accounting Exception

Final local bytecode inspection found that JMH 1.37's
`CompilerHints.compilerBlackholesAvailable()` invokes `Utils.runWith()` for two
short-lived `java -version` commands when compiler-blackhole autodetection
succeeds. Every launcher log reports compiler blackholes as auto-detected. This
implies **80 internal probe invocations across 40 launchers**, additional to the
two explicit preflights; it is a code-path inference, **not an observed PID/exit
inventory**. Probe commands enable experimental VM options and test
`-XX:CompileCommand=blackhole,some/fake/Class.method`, first with `quiet`, then
without. They execute no benchmark workload. Their elapsed time is inside the
recorded stage deadlines, but one-second process polling does not establish their
individual resource peaks or identities.

The frozen plan explicitly allowed only two additional preflight JVMs and did not
budget these JMH-internal probes. This is a **process-accounting exception**, not
an extra measured worker or a reason to discard inconvenient scores. It was
discovered after capture; no claim of prior authorization is made. The exact JMH
bytecode inspection, hash and log checks are retained. The 40 worker identities
and metrics remain usable observations; this does not establish compliance with
the plan's total-JVM wording. No rerun is proposed.
Priority 5 must first revise probe accounting explicitly, even though eight
worker slots remain; the existing budget does not silently authorize extra probes.

On 2026-10-14 the maintainer replied **"Accept with documented exception
(Recommended)"** to accepting these 40 workers' observations with the unbudgeted,
unrecorded internal probes, without rerunning anything, while leaving Priority 5
gated on revised accounting. This accepts the observations and Priority 4 closure
with the exception, **not retroactive authorization**, a complete process census,
finding acceptance or permission for new execution. The original plan is unchanged.

Host prechecks confirmed at least 3 GiB available RAM, 16 GiB free disk, two CPUs
and sufficient cgroup headroom. The runner checked the actual cgroup and its
ancestors, not just root files; this host had eight allowed CPUs and no finite
ancestor CPU/memory cap. Before/after snapshots preserve host pressure, quotas,
memory and visible processes. Sampled owned-group RSS/output and deadlines did
not trip their stop bounds. This is not proof of host exclusivity, continuous
peak memory measurement or production resource behavior. No agent-started build,
test or profiler ran concurrently with the capture.

## Per-Profile Observations

Values below are ranges of **fork means**, not confidence intervals or pooled
iteration means. Each raw fork retains all five measured samples, its mean/error,
and a low/high/other/mixed classification using the approved fixed bands. The
bundle includes sample standard deviation and full per-fork distributions.

| Lane | Profile | Artifact | Low / high / other-or-mixed forks | B/op range | ns/op range |
|---|---|---|---|---|---|
| O | AUTO_REGISTRY | B | 4 / 0 / 0 | 1744.0197-1744.0207 | 454.734-468.477 |
| O | AUTO_REGISTRY | C | 4 / 0 / 0 | 1744.0196-1744.0215 | 454.649-489.649 |
| O | HOOK | B | 3 / 1 / 0 | 1744.0212-2000.0237 | 475.358-549.604 |
| O | HOOK | C | 3 / 1 / 0 | 1744.0205-2000.0249 | 481.114-571.297 |
| M | AUTO_REGISTRY | B | 2 / 0 / 0 | 1744.0328-1744.0334 | 458.879-468.230 |
| M | AUTO_REGISTRY | C | 2 / 0 / 0 | 1744.0328-1744.0339 | 461.027-472.226 |
| M | HOOK | B | 2 / 0 / 0 | 1744.0348-1744.0350 | 483.796-491.378 |
| M | HOOK | C | 2 / 0 / 0 | 1744.0345-1744.0346 | 489.015-495.545 |
| T | AUTO_REGISTRY | B | 4 / 0 / 0 | 1744.0322-1744.0338 | 454.546-464.910 |
| T | AUTO_REGISTRY | C | 4 / 0 / 0 | 1744.0316-1744.0336 | 449.698-466.014 |
| T | HOOK | B | 4 / 0 / 0 | 1744.0336-1744.0370 | 472.558-497.638 |
| T | HOOK | C | 4 / 0 / 0 | 1744.0354-1744.0366 | 492.831-503.905 |

The explicit same-JAR HOOK B/B slots produced high stage **10** (2000.0237 B/op)
then low stage **11** (1744.0212); C/C produced low stage **14** (1744.0205) then
high stage **15** (2000.0249). Thus the split reappeared without changing either
artifact, in opposite within-pair orders. Each of those forks' five samples lies
within its reported band. All same-JAR AUTO_REGISTRY slots and all T same-JAR
slots were low. This constrains a simple changed-artifact explanation but cannot
identify the allocation mechanism of an untraced fork.

M/T have only low-mode witnesses. The small extra normalized allocation relative
to O-low is retained in the raw data, not subtracted. The absence of HOOK-high
under instrumentation is compatible with perturbation **or limited sampling**;
eight M workers and sixteen T workers do not distinguish those alternatives.
No AUTO_REGISTRY high mode was observed in any lane. Neither absence resolves its
historical flag. Lane timings are context, not promoted performance comparisons.

## Compiler Evidence and Limits

All **16 traced workers** have independently validated identity, complete XML and
class logs, plus five warmup and five measurement envelopes. The reader retains
**1,542 relevant tasks and 1,542 installation records**. Across these workers,
**40 compilation tasks** include elimination of `EffectiveCachePolicy$Selection`;
each worker has at least one such task. These counts are process-local histories,
not object counts per operation or proof that those versions executed.

Every retained relevant installation precedes the recorded measurement envelopes
in its worker. Superseded code and invalidations remain in each history. The
new records bound warmup versus measurement conservatively, but an envelope
includes profiler/fixture work outside the timed loop; a boundary-adjacent event
stays ambiguous. `measurementInterval=null` and `executingCodeIdentity=unverified`
are deliberately retained. No last-task selection or inferred execution claim
is used to attribute a score.

| Finding | Priority 4 disposition |
|---|---|
| A001 / AUTO_REGISTRY | **Unresolved/blocking:** only low modes captured; ordinary high-mode compiler/class-load evidence is still missing |
| A002 / HOOK | **Unresolved/blocking:** ordinary high/low split reproduced on both same JARs, but both high forks are untraced; instrumented ordinary high mode remains missing |
| E001 / evidence reliability | New single-worker association, record validation and iteration envelopes verified for this capture. Exact executing code identity is still unavailable; legacy multi-fork attribution stays unverified |

Priority 4 captures observations and records its limits; **non-reproduction is
not resolution**. Priority 5 has eight intervention slots, subject to the probe
accounting decision above.
A forced high mode cannot supply the missing ordinary trace. Additional ordinary
capture, changed stacks or broader investigation requires a new recorded decision.
No production change, performance all-clear, memory-leak diagnosis or release
selection follows from this result.

## Verification and Preservation

Fresh checks pass **74 Python tooling tests**, **40 untimed workload cases**, the
standalone recorder callback contract, and **111 documentation cases per Boot
row** (4.0.0 and 4.1.0), with zero failures/errors/skips. Workload and callback
checks completed before measurement; documentation checks run after capture and
are not experiment workers. Ordinary Java checks use explicit GC disabled.
`git diff --check` passes. No full reactor, strict API, consumer, native or
60-row acceptance rerun is claimed for this tooling-only change.

Fresh commands, exits, source patch, input seal, final recorder, original results,
40 worker inventories, 24 boundary records, 16 XML/class-log pairs and derived
analysis live at `target/release-evidence/v36/priority4/`, with a verified copy at
`verification-evidence/v36-priority4/` and a dated archive. The checklist records
the external seal. Re-analysis revalidated all 40 stages against their sealed
expectations and reproduced the saved reports without new forks. Verification
scripts are preserved with the evidence; ignored bundles are not in a clean clone.

The exact capture commands were:

```bash
python3 -B scripts/capture-v36-allocation.py prepare \
  --pair verification-evidence/v35-priority10 \
  --output target/release-evidence/v36/priority4/capture
python3 -B scripts/capture-v36-allocation.py ordinary \
  --pair verification-evidence/v35-priority10 \
  --output target/release-evidence/v36/priority4/capture
```

These commands describe the completed run, **not permission to repeat it**. The
runner rejects an existing execution ledger. Do not evade that guard by choosing
a new output directory: 40 slots are already consumed. Restore hash-verified
recordings for analysis, or obtain new approval before replacement capture.
The runner intentionally exposes no Priority 5 execution command in this step.
Preserve all evidence outside Maven output before any cleanup; never run root
`mvn clean` against the only copy.
