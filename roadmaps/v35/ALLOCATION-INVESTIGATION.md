# V35 Allocation Split Investigation

> **Plan frozen:** 2026-10-07, before new measurements
> **Finding:** V34-P3/P10 allocation finding
> **Implementation authorization:** pending Priority 3.3; no production change approved
> **Release scope:** unselected

This executes [Priority 2](CHECKLIST.md) against the saved P10 artifacts, not a
rebuilt optimization. The [baseline](BASELINE-SCOPE.md) reconciles their inputs
with reachable history. Starting clean revision is
`c1bcb978d2857ed4f7c14697a61f486897c9bf5a`. New records/scripts are a local
investigation delta; production, fixture and benchmark bytecode stay unchanged.

## Frozen Questions and Acceptance

Preserve V34's historical enabled-only current fork means near 1,136/1,392 B/op
and the related 256-byte shifts in registry/no-registry profiles. A favorable
pair or byte-identical classes cannot clear the finding. Test these alternatives:

| Hypothesis | Distinguishing observation |
|---|---|
| Different executable inputs | Audit every shaded class/resource, embedded starter version, harness hashes, ordered classpath and VM configuration. Unexpected code/dependency differences stop comparison; version metadata differences are recorded rather than normalized away. |
| Fork-dependent compilation/elimination | Repeated forks of each same saved JAR can occupy different allocation levels. Independently traced C2 decisions must identify the changing allocation site; disabling escape analysis tests sensitivity, not proof by itself. |
| Disabled-selection materialization | Eight endpoint mutation checks call `EffectiveCachePolicy.resolveSelection`. Change only its inline directive in separate experiments. A materialized 32-byte selection per endpoint could account for 256 B/op, but source-level object arithmetic alone is not evidence of elimination or causality. |
| Host/order or profiling perturbation | Preserve before/after CPU/quota/pressure/memory data, both artifact orders, traced and untraced samples. Shared-host timings and JFR sampling cannot establish precise savings or production retention. |

A validated explanation requires observed allocation levels plus a matching
compiler/allocation-site mechanism and one-factor intervention on **both**
artifacts. Merely forcing higher allocation or observing fewer JFR samples is
insufficient. If default traced forks do not distinguish the historical modes,
retain the causal gap. No runtime-default change, measurement correction or
production patch is approved. If a correction is proposed later, validate it on
both artifacts and representative profiles without replacing these samples.

## Frozen Work and Stop Rules

Use `V34DefaultPathBenchmark.defaultV34NoNetworkWarmPublisher`, `scenario=GET`.
Each operation assembles/returns a cold Mono without subscribing; the unchanged
fixture checks zero assembly dispatch, two setup subscriptions and correct
teardown totals. Construction, setup/teardown and transport are not the timed
operation. No extra instrumentation enters the scored method.

- Scored-context subset: `MINIMAL`, `AUTO_REGISTRY`, `RESILIENCE_ENABLED_ONLY`;
  saved baseline then current, then current then baseline. Two forks per row,
  24 forks total. Each artifact therefore has four forks per profile. This subset
  is attribution evidence, not the complete 60-row final acceptance matrix.
- Trace control: enabled-only GET, baseline then current, four forks each,
  eight forks total. Add compiler XML and class-load logs only.
- One-factor interventions relative to that trace control: disable escape analysis,
  disallow inlining of `EffectiveCachePolicy.resolveSelection`, then request its
  inlining. Each uses baseline then current, two forks each, 12 forks total.
  Compiler logs must confirm whether directives actually take effect.
- Allocation sampling: one enabled-only fork per artifact, baseline then current,
  with JFR and GC profiling, two forks total. Sampling is corroboration, not exact
  per-operation accounting. An unavailable profiler is recorded, not substituted
  silently. Diagnostic `PrintEscapeAnalysis` availability is probed, not presumed.

Maximum: **46 forks**, no result-selected additional run, at most 15 minutes per
three-profile invocation and 10 minutes per diagnostic invocation. Stop a failed
stage and retain its outputs; do not retry until green or relax thresholds.
If the cause remains unexplained at this boundary, leave 2.4 unresolved and ask
the maintainer for explicit next scope. Do not mark the finding resolved because
the experiment budget was consumed.

All runs retain Oracle Java 21.0.8, JMH 1.37, Boot 4.0.0, one benchmark thread,
average-time nanoseconds, five one-second warmups, five one-second measurements,
`-prof gc`, `-foe true`, fixed 512 MiB heap, `ActiveProcessorCount=2` and WARN
logging. Keep V34's **>20% latency** and **>max(32 B/op, 5%) allocation** review
triggers; no threshold adjustment after observing results. LogCompilation,
class-load logging, escape-analysis and inline directives/JFR are diagnostic-only;
their timings are not scored benefit or recommended application flags.

Run sequentially with no agent-started Maven/test/native/profiler process beside
scored work. Record host CPU, affinity, cgroup limits/counters, pressure and memory
before/after each invocation. Process visibility may be namespace-limited;
boundary snapshots do not prove a workstation was uncontended during every sample.
Stop before a stage if less than 2 GiB memory is available. No taskset, governor,
pool, scheduler, dependency, heap or payload tuning is selected.

## Reproduction

The tracked `scripts/investigate-v35-allocation.py` uses the existing saved harness,
refuses reused output directories and seals the plan/source/input identities
before measurement. Commands run from the repository root:

```bash
OUT="$PWD/target/release-evidence/v35/priority2"
PAIR="$PWD/target/release-evidence/v34/priority10"
python3 scripts/investigate-v35-allocation.py prepare --pair "$PAIR" --output "$OUT"
python3 scripts/investigate-v35-allocation.py scored --pair "$PAIR" --output "$OUT"
python3 scripts/investigate-v35-allocation.py diagnostic --pair "$PAIR" --output "$OUT"
```

Stop and preserve failure exits before proceeding. Missing saved artifacts require
the reachable-input reconciliation/rebuild process in the baseline; an ignored
bundle path is not provenance by itself. Later results and their disposition are
appended below without rewriting this frozen plan.

## Results and Attribution

**Disposition, 2026-10-07: Resolved without production change for the observed
enabled-only 256 B/op split.** This is an allocation-mechanism explanation, **not
a performance pass**, release acceptance or proof of uniform allocation across
JVMs. No runtime tuning and no measurement correction are adopted. Six implementation
workstreams remain open; Priority 3.3 approval is still required before production
edits. No memory-retention claim is made: this is allocation, **not a leak**.

All 46 planned forks completed in four scored-subset and ten diagnostic
invocations, exit 0. The measurement sequence took about nine minutes including
the boundary between scored and diagnostic stages. No additional result-selected
fork was run. Evidence root: `target/release-evidence/v35/priority2/`.

### Input and Host Audit

The saved JAR hashes are unchanged:

| Artifact | SHA-256 |
|---|---|
| Published-baseline harness | `c9ff9fa21d4778a5dfcadbac145439283c1bc89fc13dedc538fe48c63c69cf9a` |
| Current harness | `f30482de11e8ccefa8980beb29f4074a9de011683cb8cee37d0a84925b8239db` |

All **24,224** shaded class entries, including starter, harness/generated JMH and
dependency bytecode, match. Only the starter's embedded Maven `pom.xml` and
`pom.properties` differ. The 39 harness/POM source hashes still match current
source; all 120 non-starter JAR identities and ordered dependency classpaths match
apart from the starter coordinate. Class-load logs verify that starter/harness
file-loaded classes came from the corresponding saved JAR, not reactor output or
another dependency. Launcher/fork commands and the effective VM flag inventory
are retained. These are still the historically built artifacts, not freshly built
clean V35 binaries; the baseline's reachable-input reconciliation applies.

Oracle Java 21.0.8 uses compressed object/class pointers, escape analysis and
allocation elimination enabled, tiered compilation and the frozen two-CPU/512 MiB
settings. The process affinity exposed eight CPUs. Boundary available RAM ranged
from 5.31 to 5.65 GiB. CPU, memory, pressure and process-namespace observations are
retained; the fixed cgroup control paths were unavailable and no quota is inferred.
No agent-started build/test/native/profile overlapped scored comparisons. The
shared-host and namespace-visibility limits remain; these are not lab throughput
numbers. `PrintEscapeAnalysis` exited 1 because this product JVM exposes it only
in a debug build. Supported LogCompilation XML supplies the elimination evidence.

### Same-Artifact and Both-Order Results

Each cell lists the two allocation fork means in B/op, not a favorable sample:

| Order / artifact | MINIMAL | AUTO_REGISTRY | RESILIENCE_ENABLED_ONLY |
|---|---|---|---|
| Forward / baseline | 832.007 / 832.007 | 1,744.019 / 1,744.020 | **1,392.017 / 1,136.016** |
| Forward / current | 832.007 / 832.009 | 1,744.019 / 1,744.020 | 1,136.015 / 1,136.015 |
| Reverse / current | 832.007 / 832.007 | 1,744.019 / 1,744.020 | **1,392.016 / 1,392.017** |
| Reverse / baseline | 832.008 / 832.007 | 1,744.019 / 1,744.020 | **1,392.018 / 1,136.015** |

Forward enabled-only means: baseline 1,264.017 +/- 203.987 B/op versus current
1,136.015 +/- 0.041. Reverse: baseline 1,264.017 +/- 203.988 versus current
1,392.017 +/- 0.045. The frozen reviewer retains **one allocation review flag**
in reverse order, zero forward allocation flags and zero latency flags in either
order. Explaining the variance does not delete that flag or change its threshold.
Enabled-only latency means are 380.616 +/- 33.350 versus 357.747 +/- 17.845 ns/op
forward, and 385.220 +/- 58.723 versus 391.722 +/- 13.393 reverse. All intervals,
five samples per fork, raw JSON and other profile timings remain in the bundle.

### Compiler Mechanism and Interventions

The fixture's eight endpoint checks call `EffectiveCachePolicy.decide` and
`resolveSelection` inside `CacheWorkPolicy.lambda$validator$1`. A disabled
`Selection` has a `CachePolicyConfig` reference in its constructor signature even
though the passed value is null. Whether that signature type is loaded when C2
compiles the hot validator changes constructor inlining and scalar replacement.

The compiler traces distinguish the observed levels, not merely sampled sites:

| Trace / fork | B/op | Validator C2 task | Signature type load | Evidence |
|---|---:|---|---|---|
| Default traced baseline / 1 | 1,136.014 | 2616 at 0.990 s | 0.978 s | Constructor inlined; explicit `eliminate_allocation` for both Selection and Decision |
| Default traced baseline / 2 | 1,136.014 | 2553 at 1.003 s | 0.996 s | Same elimination evidence |
| Default traced baseline / 3 | 1,136.015 | 2585 at 1.007 s | 0.993 s | Same elimination evidence |
| Default traced baseline / 4 | 1,392.017 | 2550 at 1.014 s | 1.026 s | Selection constructor `inline_fail`: `unloaded signature classes`; only Decision eliminated |
| Inline-request current / 1 | 1,136.015 | 2537 at 1.002 s | 0.975 s | Selection and Decision eliminated |
| Inline-request current / 2 | 1,392.017 | 2598 at 1.043 s | 1.056 s | Same unloaded-signature constructor failure; only Decision eliminated despite the resolver inline request |

Task timestamps are compilation-start observations, not invented exact instants
for every inline decision. The inline-failure records themselves establish the
unloaded signature condition. Fork association uses ordered JVM-start timestamps
from compiler XML and retains process IDs, emitted nmethods, deoptimizations,
class-load records and raw task frames for review.

The controlled interventions corroborate the materialization boundary on both
artifacts without changing a class file:

| Diagnostic factor | Baseline forks B/op | Current forks B/op | Interpretation |
|---|---|---|---|
| Trace only | 1,136.014 / 1,136.014 / 1,136.015 / 1,392.017 | 1,136.015 / 1,136.015 / 1,136.014 / 1,136.015 | The historical modes remain observable with compiler evidence |
| `-XX:-DoEscapeAnalysis` | 2,032.019 / 2,032.017 | 2,032.017 / 2,032.019 | Broader elimination sensitivity, not an isolated 256-byte cause by itself |
| Resolver `dontinline` | 1,392.018 / 1,392.018 | 1,392.017 / 1,392.017 | Compiler confirms `disallowed by CompileCommand`; Decision eliminated, Selection not eliminated |
| Resolver `inline` | 1,136.015 / 1,136.017 | 1,136.015 / 1,392.017 | A resolver directive cannot guarantee the constructor's signature classes are available |

Selection materialization at eight checks accounts for the 256 B/op increment
(consistent with 32 bytes per selection in this VM layout). This is backed by
the explicit elimination/missed-inlining records and the one-factor intervention,
not object arithmetic alone. The remaining nondeterminism is **when** class
resolution and compilation happen; no deterministic scheduling guarantee is made.
There is no source/version delta to fix in these compared artifacts. No eager
class-loading workaround or JVM flag is promoted as a measurement correction.

### Profiling and Privacy Limits

The one-fork JFR controls report 966.209/966.769 B/op on baseline/current,
different from unprofiled levels. This is observable profiler perturbation, not
a reduction to advertise. Recordings contain 1,511/1,560 allocation samples, of
which 11/9 name EffectiveCachePolicy classes. Samples corroborate site presence;
they are not exact object counts and do not explain the split on their own.

JFR's default profile also captures environment/system-process events. The
exported recordings were scrubbed to **`jdk.ObjectAllocationSample` only**;
summary verification confirms zero environment/process events. Original hashes
and scrub commands are retained, while unsanitized originals are mode-0600 files
under private mode-0700 `target/v35-private-attribution/`, outside the sealed
evidence bundle. Do not attach those originals to support or release evidence.
The reviewer rejects recordings containing other populated event types.

For a new run, set `umask 077` before the commands above. Before reviewing or
sealing JFR output, move each original to a private directory and run the local
Java 21 tool against that original:

```bash
jfr scrub --include-events jdk.ObjectAllocationSample "$PRIVATE_ORIGINAL" "$EXPORTED_RECORDING"
python3 scripts/review-v35-allocation.py "$OUT" --output "$OUT/review"
```

Use the Java distribution selected above, preserve original/output hashes and
do not overwrite a prior review directory. This removes unrelated captured data,
not measurement samples, command results or compiler evidence.

### Acceptance Implications and Verification

- The enabled-only allocation finding is explained for these saved artifacts and
  this JVM/workload. It is not necessary to request another attribution experiment
  at the stop boundary; the conditional unresolved-scope branch is N/A here.
- C004's disabled selections demonstrably can materialize, but often are already
  eliminated. That does not establish a stable benefit for the rolled-back reuse
  patch, authorize restoring it or remove the whole-interface mutation guard.
- C001-C003 and broader C004/C005 still require their own attribution, semantic
  and ownership evidence. Six implementation workstreams remain open. No blanket
  claim is made about the earlier registry/no-registry shifts: those profiles were
  stable in this representative run, so their historical samples remain intact.
- Final Priority 10 must retain representative forks, intervals, both orders and
  the full matched matrix. A lower mean produced by a different mixture of
  compiler modes is not evidence of a production benefit. No threshold is reset.

The ordinary guards validate the frozen schedule, isolated factors, complete
samples, byte/input mismatch rejection, owned-process timeout cleanup, loaded
classes, compiler summary context and JFR privacy boundary. No ordinary test
asserts timing, GC collection or which compiler mode must occur. Final counts
and the seal are recorded in [Priority 2](CHECKLIST.md).

The first preparation was replaced **before measurement** to ensure timeouts kill
the owned launcher/worker process group; both preparations remain available.
An intermediate reviewer rejected equivalent `file:/` and `file:///` paths; it
was corrected to parse URIs, with no benchmark rerun. The new documentation test
was first run against the plan without this results section and failed as intended.
The first final review rejected JFR metadata because its event-name allowlist
misspelled `jdk.Checkpoint`; correcting that guard passed against the same
scrubbed recordings. A documentation assertion also failed on a wrapped line;
whitespace-normalizing that assertion passed the full 166-case rerun. Both failed
attempts remain in the bundle. None caused a new scored or diagnostic fork.
Failed/unavailable diagnostics and partial analysis are preserved, not silently
turned into successful scored evidence.
