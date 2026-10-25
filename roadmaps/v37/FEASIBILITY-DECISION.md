# V37 Attribution Feasibility and Stop Decision

> **Recorded:** 2026-10-18
> **Status:** Priority 2 complete; approved stop before new measurement (V37-P2-1)
> **Decision:** retain both findings unresolved; no closure or scope exclusion
> **Published / development:** `4.4.2` / `4.5.0-SNAPSHOT`
> **Experiment authorization:** none; no inherited budget
> **Implementation and release scope:** unselected

This executes the offline review in [Priority 2](CHECKLIST.md), following the
[baseline inventory](BASELINE-SCOPE.md). The approved stop is not closure,
a scope exclusion or a performance pass. No new worker, pilot, profiler,
attach, preflight, recorder smoke or benchmark was launched. No production,
dependency, JVM default or capture-tool change is made.

## Source and Verified Inputs

Started from clean `6b0904dddddc6447a59e9f252847bff6ed7691b1`, tree
`1b3f5cba74075da315c197dd57e284ced79eb2db`. Fresh checks use that reachable base
plus the separately preserved documentation/test patch, not a new clean commit.
V1-V36 history and original raw data remain unchanged.

The read-only audit revalidates **1,938 files in five bundles and their complete
archives**: V35 P10, V36 P4/P5/P7 and V37 P1, using their tracked seals. It runs
the existing [stage reader](../../scripts/review-v35-allocation.py) on all **48
saved stages** and reproduces every saved review exactly. It also reproduces
the 60-row primary and four-row reverse reviews from original JSON: three
primary allocation flags, zero primary latency flags, no reverse flags. No
original result is discarded or upgraded by this analysis.

P1's missing 121-entry classpaths per artifact remain missing at their recorded
temporary locations. Re-reading sealed JSON/XML needs no restored JVM launch
environment; it does not prove those historical commands are runnable today.
Lost V35 recordings remain unavailable. Integrity and source equivalence do not
establish scientific causality or recover missing observations.

## 2.1 Missing Causal Observation

The [benchmark](../../reactive-http-client-benchmarks/src/main/java/io/github/huynhngochuyhoang/httpstarter/benchmarks/V34DefaultPathBenchmark.java)
returns a newly assembled cold publisher from a warm fixture. AUTO_REGISTRY has
a real registry-selected observer; HOOK has genuine hook discovery/composition.
Their terminal callbacks and downstream transport are not the timed operation.
Controls must preserve this boundary; subscription, construction, a no-op hook
or MINIMAL is not an interchangeable workload.

Counts below aggregate artifacts only for mode inventory. Every stage, artifact,
profile and five-sample measurement vector remains separate in derived evidence.
The existing fixed low/high bands are unchanged; these counts estimate no mode
frequency and supply no new performance score.

| Existing observation | AUTO_REGISTRY | HOOK | What it supports / missing bridge |
|---|---|---|---|
| V35 primary current, two forks/profile | About 2000.024 / 1744.021 B/op | About 2000.023 / 1744.022 B/op | Both have an ordinary split; no process-associated ordinary high compiler explanation |
| V36 O, no recorder/trace | Eight low | Six low, two high | HOOK high occurs on each saved artifact; the high stages 10 and 15 have no compiler trace or iteration envelopes |
| V36 M, recorder only | Four low | Four low | Recorder supplies iteration envelopes; absence of high does not establish that the recorder suppressed it |
| V36 T, recorder plus compiler/class logs | Eight low | Eight low | Associated low-mode compiler histories exist; ordinary high-mode history does not |
| V36 I, T plus resolver non-inlining | Four high | Four high | Both profiles respond to the forced boundary; this is not the ordinary high-mode witness |

T contains **1,542 relevant tasks/installations** and **40 Selection-elimination
tasks**. I contains **763 relevant tasks/installations**, no Selection-elimination
task, and observed directive refusals in all eight workers. Those are compiler
history counts, not objects/operation. The roughly 256 B/op intervention contrast
does not prove eight 32-byte Selection allocations or rule out changed optimization
in callers. A forced mechanism is not a demonstrated ordinary cause.

The [recorder](../../.github/v36-boundary-recorder/BoundaryRecorder.java) brackets
JMH iteration callbacks with monotonic/uptime clocks. It does not record timed-loop
instruction pointers, allocation-site events or executing compilation versions.
The reader intentionally preserves `measurementInterval=null` and
`executingCodeIdentity=unverified` in all 48 reports. A process-local compile ID,
installation before an envelope, elimination event or last compilation task cannot
fill that gap. Legacy multi-fork timestamp sorting remains an invalid identity join.

- **V37-A001:** Still lacks a newly observed ordinary high mode and its causal
  evidence. Low-only O/M/T and high-only forced I cannot explain the primary flag.
- **V37-A002:** Has attributable untraced high/low observations on both same JARs,
  but lacks the code/allocation evidence for those high workers. A001 or
  AUTO_NO_REGISTRY explanations cannot be transferred.
- **Both:** Instrumentation perturbation versus limited sampling remains unresolved;
  host/order effects are not ruled out. Clear reverse aggregates do not erase flags.
- **V37-E001:** V36's process association and rejection guards remain verified
  within their limits. This review reproduces no new parser/recorder defect.

## 2.2 Candidate Methods and Falsifiers

This is a source/evidence feasibility assessment, not a claim that the problem
is impossible to solve. A method need not be experimentally proven before planning,
but it must name an obtainable new observation and a defensible route to the
intended explanation. Unknown overhead alone is not grounds to reject it; a
missing evidence link must not be disguised as an overhead-control experiment.

| Candidate | New observation and possible benefit | Perturbation / prerequisites | Falsifier or rejection boundary | Assessment |
|---|---|---|---|---|
| C1: Repeat or extend O/M/T; add compile directives | More modes or compiler history, but no new observation type | Same recorder/logging perturbation; more workers/time | Another low-only traced result or a forced-only high leaves the same gap | Not materially different; do not repeat automatically |
| C2: Allocation sampling without compiler logging | Object class and allocating stack could distinguish actual high-mode allocation sites from optimization history | Pin a sampling implementation, worker/phase join, rate, thread coverage and output/privacy limits; agent/recording overhead needs controls | No attributable ordinary high sample set, no relevant site contrast, mixed setup/measurement or incompatible mode shifts cannot establish the proposed cause | Useful corroboration, not yet an end-to-end compiler-attribution method; no exact per-operation count from sample weights |
| C3: Execution-PC sampling plus versioned JIT load/unload maps, correlated with allocation observations | Potentially identifies which compiled range ran in a high-mode worker instead of assuming the last installed task executed | Requires a pinned supported exporter/reader, clock/phase/thread join, code-address lifetime/inlining handling, lost-event detection and sampling uncertainty; native tooling and permissions are unverified | Unmapped/reused addresses, missing lifecycle events, no relevant high-mode execution samples or no link to allocating operations leave code identity unknown | Materially different in principle; no bounded complete capture path demonstrated in the reviewed tooling/source |
| C4: Delay observation until after warmup or attach on a high result | Could reduce startup perturbation | Requires a protocol that preserves the same worker/mode before and after activation, helper/process accounting and attach permissions | An aggregate result arrives after the timed work; late snapshots omit unloaded code. Mode transition or activation without phase evidence breaks attribution | Not an independent solution to C2/C3's missing joins; no high-result-selected retries |
| C5: Bytecode counters, custom VM/debug agent or disabling optimization | Could expose detailed operations in a changed workload | Changes the optimization being explained; substantial new instrumentation/VM ownership and verification | Only modified/forced workload is high, or the observer introduces the allocation | Outside the narrow proposed route; cannot replace ordinary evidence |

### Source Basis and Limits

The Java 21 [SampledObjectAlloc contract](https://docs.oracle.com/en/java/javase/21/docs/specs/jvmti.html#SampledObjectAlloc)
provides sampled object/class/size/thread observations; stack retrieval can locate
allocation origins. It does not supply an exact compiled-version attribution for
every operation. [CompiledMethodLoad](https://docs.oracle.com/en/java/javase/21/docs/specs/jvmti.html#CompiledMethodLoad)
describes loaded code ranges, possibly multiple forms and inlining; address maps
can be unavailable. [GenerateEvents](https://docs.oracle.com/en/java/javase/21/docs/specs/jvmti.html#GenerateEvents)
replays currently compiled methods, not unloaded history. These are useful
ingredients, not a ready-made timed allocation-to-executing-code join.

The upstream [async-profiler modes guide](https://github.com/async-profiler/async-profiler/blob/master/docs/ProfilingModes.md#allocation-profiling)
describes allocation-site sampling and CPU/native/Java stack profiling. That
supports C2/C3's potential, not a guarantee that its default output identifies
the compilation version responsible for this workload's allocation difference.
No profiler binary, version or integration is selected. The guide is a mutable
upstream reference consulted on 2026-10-18, not a pinned experiment input.

The reviewed local runner/recorder/reader have no execution-PC or allocation-sample
join. Adding another profiler name is therefore not a complete capture design.
**Inference from these sources and local code:** C2 can narrow allocation sites,
but C3 still needs an explicit supported export format and correlation design to
meet the current causal obligation. This review does not claim that all external
tools are incapable, or that permission to plan requires a forbidden pilot.
No tool was installed, agent attached, permission changed or JVM probe launched.

### Minimum Sufficient Evidence

For an **ordinary compiler/materialization explanation**, require all of:

1. Matched saved artifacts/dependencies and real profile semantics, one owned
   worker per result, all samples and explicit phase/thread/clock uncertainty.
2. Ordinary high and low observations for each profile, without a forcing
   directive. Observed lanes must be distinguished from unobserved scores;
   perturbation/missing modes remain limits rather than being subtracted away.
3. High/low allocation-origin contrast tied to the measured work, plus evidence
   that the relevant compiled version/path executed there. Code installation
   alone or a sampled allocating stack alone does not prove both claims.
4. A mechanism-specific contrast that can refute the explanation, with all
   contrary observations retained. Existing forced non-inlining is corroboration,
   not a replacement for item 3. Do not assert object-size/count arithmetic from
   aggregate B/op or sampled weights.
5. Independent A001/A002 dispositions and combined review with uncertainty and
   alternative hypotheses. Keep >20% latency and >max(32 B/op, 5%) allocation
   review triggers and the original scored rows unchanged.

For **instrumentation perturbation**, require a frozen comparison capable of
distinguishing mode changes from sampling variability; low-only instrumented
lanes do not suffice. For an **artifact/workload explanation**, demonstrate the
specific mismatch and its affected evidence rather than changing profiles or
stacks until scores clear. For a **runtime defect**, reproduce violated behavior
and its negative control before Priority 7.2 correction approval. None of these
alternatives currently has sufficient evidence to clear A001 or A002.

## 2.3 Approved Stop and Remaining Gates

**V37-P2-1: approved stop before new measurement.** No materially different,
bounded end-to-end method has been established by this review. C3 is a possible
reopening direction, not an approved experiment or proof of feasibility. A future
proposal should name the exporter/format, same-worker phase/code/allocation join,
uncertainty/rejection rules, owner and resource/permission prerequisites. It may
be reviewed offline without granting a pilot or presuming a runtime defect.

On 2026-10-18 the maintainer answered:

> Approve stop (Recommended)

The question explicitly retained A001/A002 as unresolved and preserved later
verification, evidence and separate closure gates; it did not close V37 or
exclude either finding. This completes **2.3**, not performance acceptance.
The initial audit's pending recommendation is a dated pre-decision checkpoint,
superseded only by this approval, not a changed observation.

No new capture is selected. A future reopening requires a new explicit decision;
proceed-to-plan would still not authorize execution before 4.3's numerical
process/resource bounds. The exhausted 338 V35 workers and 48/48 V36 workers,
including 96 inferred probes in 194 accounted JVM invocations, grant no new budget.

Stop-route applicability is recorded on **2026-10-18**. N/A means **not run,
not passed**; it does not mark later verification/preservation/closure complete:

| Later obligation | Approved stop-route applicability |
|---|---|
| Priorities 3-7 | N/A for this approved stop: new capture design/implementation, investigation schedule, measurements and production correction not selected; no new experiment or correction approval. Preserve existing causal gaps rather than claim those gates passed |
| Priority 8 | Fresh scoring/runtime/API/native execution not selected for unchanged production; still verify decision/lifecycle/docs and exact evidence-reuse limits, recording justified N/A |
| Priority 9 | Preserve this review, evidence, failures/limits and guidance; remains required |
| Priority 10 | Separate explicit scope/release/closure decision remains required. A001/A002 block full-scope closure unless explicitly excluded, not passed |

Neither a stop nor no production change authorizes closure, scope exclusion or
release. V37 remains active, `plannedFinalVersion=null`, release lane unselected.
V1-V36 are unchanged; C001-C005 and both C004 subrows are not reopened.

## Verification and Preservation

The offline audit, per-stage modes/compiler counts, reproduced primary/reverse
reviews, commands/exits and source hashes are under
`target/release-evidence/v37/priority2/`. Verification passes **122 documentation
cases per Boot row** (4.0.0/4.1.0), **36 deterministic tooling tests** and
`git diff --check`, zero failures/errors/skips. Overlapping pre/post-decision
runs are not additional unique cases. Copy/archive seals are recorded in
[CHECKLIST.md](CHECKLIST.md).
Ignored local evidence is not a clean-clone artifact or an off-machine backup.

Use only the reader/verifier functions for reproduction; do not invoke capture
runner preparation or execution. The preserved driver records every offline
call and selected input seal. Fresh tests use Java 21.0.8/Maven 3.9.9 with explicit
GC disabled, separate from diagnostic workers. No fresh full runtime, API,
consumer, native, performance or publication verification is claimed.

No application request data or production capture is authorized. B/op is not
retention/RSS, and this review is not a leak, Kubernetes or mesh diagnosis.
