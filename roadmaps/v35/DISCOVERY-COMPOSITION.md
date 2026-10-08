# V35 Dynamic Discovery and Composition

> **Plan frozen:** 2026-10-08, before candidate/scored measurements
> **Workstream:** V34-C001; Priority 4
> **Authorization:** Priority 3.3 bounded accumulation experiment only
> **Release scope:** unselected

Starting clean source: `32930459abb783dfa2f1c153e7f38d54de9048f4`.
This executes [Priority 4](CHECKLIST.md) within [FIX-DECISION.md](FIX-DECISION.md#v34-c001).
No provider-result cache, discovery shortcut, public composite change, body/state
change or other C001-C005 experiment is included.

## Frozen Characterization

`getObserver()` calls `orderedStream()` once per invocation, fully materializes
it, calls `getIfAvailable()` for null/empty streams, returns the lone element or
constructs `CompositeHttpClientObserver`. That public composite defensively
copies the list and isolates callback failures. A single null observer returns
null; multiple observers including null fail during composite construction,
after full traversal. Empty-stream fallback can materialize or fail independently.

`getLifecycleHooks()` calls its ordered provider once, executes every support
check, skips ordinary support exceptions and returns accepted hooks in encounter
order. A null provider/stream yields no hooks; a null element or fatal support
error still fails. Neither method closes provider streams. Captured consumers
belong to the assembled publisher; subscribing again does not discover again.
New invocations must see late registrations and fresh prototype instances.

The new untimed cases in `DefaultPathCostOwnershipTest` distinguish stream calls,
fallback, element materialization, support checks, list cardinality, callbacks,
dispatch and resubscription. Existing ordered/prototype/concurrent, master-off,
no-registry and independent-pool controls remain. These counts are not B/op.

## Candidate and Ownership

Within the two private handler methods only, sequential streams use iterator
traversal, keep the first accepted value in a local scalar and allocate a list
only on the second value. Preserve null observer cardinality and defer composite
validation until traversal completes. Hook lists remain immutable. Parallel
streams retain the existing stream pipeline: callback execution/ordering cannot
be silently made sequential. Preserve full support/fallback/failure timing.

All locals remain invocation-owned; no new fields, mutable shared holder,
reference cache or application-object lifetime is allowed. Stream/provider
infrastructure remains intentional cost. Iterator wrappers, additional copying
or branching may offset collection savings; fewer source constructors alone
cannot pass acceptance. No production-memory/leak or collection claim is made.

## Frozen Measurement and Stop Rules

Use the unchanged saved V34 P10 baseline/current harnesses, whose executable
classes match the starting production source as reconciled in [BASELINE-SCOPE.md](BASELINE-SCOPE.md).
Preserve the saved current JAR as the untouched comparison artifact; build the
candidate with the identical harness and non-starter dependencies. Compare all
shaded classes and resource differences; only handler implementation bytes and
recorded artifact metadata may differ. Final release comparison to Central is
still Priority 10, not this local acceptance experiment.

Before editing production, collect two separate baseline allocation-site JFR
controls: enabled application HOOK and AUTO_NO_REGISTRY, GET warm publisher,
one fork each. Attribute provider stream infrastructure versus local collection/
filter/composite sites using sampled stack ownership, not subtraction of unlike
profile totals. These diagnostic timings are not scores or exact allocation counts.
Scrub recordings to `jdk.ObjectAllocationSample` only before export; quarantine
unsanitized originals privately outside the evidence bundle, as in P2.

Scored selection is fixed to **28 rows**, each two forks, in both orders:

- `V34DefaultPathBenchmark.defaultV34NoNetworkWarmPublisher` and
  `defaultV34NoNetworkWarmSubscription`, all six profiles, GET/TARGET: 24 rows.
- `StarterDiagnosticsOverheadBenchmark` methods
  `diagnosticsNoNetworkOneObserverGetNoBody`,
  `diagnosticsNoNetworkMultipleObserversGetNoBody`,
  `diagnosticsNoNetworkOneLifecycleHookGetNoBody`,
  `diagnosticsNoNetworkMultipleLifecycleHooksGetNoBody`: four existing combined
  discovery/composition controls, not isolated provider-cost subtraction.

Order: baseline warm, baseline composition, candidate warm, candidate composition;
candidate warm, candidate composition, baseline warm, baseline composition.
Total **224 scored forks**, plus the two diagnostic forks. No additional
result-selected rerun. At most 20 minutes per warm invocation and five minutes
per four-row composition invocation. Preserve partial/failed stages and stop.
This task selects one candidate, no refinement; lack of stable benefit means
rollback and an evidence-backed no-change assessment, not a wider optimization.

Retain Java 21.0.8/JMH 1.37, five one-second warmups and five one-second measures,
avgt/ns, one thread, GC profiler, fixed 512 MiB, ActiveProcessorCount=2 and WARN
logging. Exact source/JAR/classpath/VM hashes, commands, raw forks/intervals and
host snapshots are retained. No competing agent builds/tests during scoring.
Stop a stage before launch below 2 GiB available RAM. Namespace/host observations
do not prove a dedicated workstation. No VM/classloading workaround is adopted.

Acceptance requires repeatable attributed warm assembly benefit on ordinary
no-registry and application-consumer rows in both orders, unchanged semantics,
and no unexplained adverse control. Preserve >20% latency and >max(32 B/op, 5%)
allocation review triggers, all contrary samples and P2 compiler-mode limitations.
A shifted 256-byte selection mode is not C001 benefit. If needed for attribution,
stop and preserve the gap rather than run unplanned diagnostics until favorable.
Full-matrix, consumer, API/native and combined-change acceptance remain later work.

## Reproduction

Use a fresh evidence directory; do not overwrite an earlier result. The runner
requires byte/input checks before scoring and records failures. With Java/Maven
and a writable general repository configured as in the V35 baseline:

```bash
OUT="$PWD/target/release-evidence/v35/priority4"
python3 -B scripts/investigate-v35-discovery.py attribution --output "$OUT"
# Run untimed controls, apply only the approved candidate, build and audit it.
python3 -B scripts/investigate-v35-discovery.py score --output "$OUT"
python3 -B scripts/investigate-v35-discovery.py review --output "$OUT"
```

The exact candidate Maven build/input-capture commands and test totals will be
recorded with execution below. No timing or explicit-GC assertion belongs in the
ordinary suite. Do not label reused evidence a fresh native or published run.

## Results and Disposition

Completed 2026-10-08: **Resolved without production change** for V34-C001.
The one authorized candidate was tested and **rolled back**; this is not a
performance pass. Required dynamic discovery is retained and the local
accumulation alternative did not meet the frozen acceptance gate. Five other
implementation workstreams remain open. No release, default, public API,
dependency or lifetime change is selected.

All eight scored stages exited zero: **224 scored forks**, 28 matched rows in
each order, plus **two separate diagnostic forks**. The forward comparison has
zero review flags. Reverse has zero latency flags and **one allocation review
flag**, HOOK/GET warm publisher. There were no scored retries or refinements.
The complete raw samples, intervals, host snapshots and command exits are in
`target/release-evidence/v35/priority4/`; the frozen pre-results plan is retained
there independently of this appended outcome.

### Attribution and Intentional Work

The baseline AUTO_NO_REGISTRY recording contains 205 sampled lambda allocations
under `getLifecycleHooks`, one filter-pipeline sample, and seven provider stream
head samples (six hook, one observer). The HOOK recording contains 342
`LinkedHashMap` samples under Spring's `orderedStream()` plus three stream-head
samples. These distinguish a real local filter/lambda site from provider-owned
discovery infrastructure; they do not assign exact B/op, prove an unsampled site
absent, or measure composite/list cost by subtracting whole-call profiles.
Recordings include warmup and have bounded/truncated stacks. Diagnostic timings
are not scored benefit. Exported JFR contains only `jdk.ObjectAllocationSample`;
private originals remain outside the evidence bundle.

The required work has independent witnesses: every invocation must materialize
the ordered provider result, run each hook support check and independently honor
empty-stream fallback. Caching consumers would miss late/prototype beans;
replacing fallback would change custom-provider results/failures; stopping after
the first value would miss later errors/consumers. Public composite copying and
failure isolation remain required. Captured consumers live with their publisher,
not a global cache; no new retained owner or collection claim is introduced.

The rejected sequential iterator candidate removes the local filter pipeline
but adds iterator/adaptor work and, for multiple accepted hooks, an `ArrayList`
plus immutable-copy path. Source structure identifies this tradeoff, not its
exact runtime allocation. Preserving parallel-stream behavior required retaining
the old pipeline on that branch. Broader provider/discovery substitutions and
removing public defensive copies are outside authorization and fail these
contracts; none was tried to rescue the empty case.

### Matched Results

Rounded mean B/op below; full two-fork/five-measurement samples and JMH intervals
remain in `scored/*/results.json` and `review/review.json`. These are observed
whole-row differences, not isolated removable C001 bytes.

| GET warm publisher | Forward baseline -> candidate | Reverse baseline -> candidate |
| --- | --- | --- |
| MINIMAL | 832 -> 560 | 832 -> 560 |
| AUTO_NO_REGISTRY | 832 -> 560 | 960 -> 560 |
| AUTO_REGISTRY | 1744 -> 1680 | 1744 -> 1704 |
| RESILIENCE_ENABLED_ONLY | 1392 -> 1120 | 1264 -> 1120 |
| OBSERVER | 1744 -> 1704 | 1744 -> 1704 |
| HOOK | 1744 -> 1728 | 1744 -> 1856 |

| Composition control | Forward baseline -> candidate B/op | Reverse baseline -> candidate B/op |
| --- | --- | --- |
| One observer | 19268.40 -> 19112.42 | 19204.43 -> 19148.41 |
| Multiple observers | 19508.43 -> 19628.39 | 19636.44 -> 19524.43 |
| One hook | 19208.38 -> 19292.39 | 19128.41 -> 19180.44 |
| Multiple hooks | 19256.43 -> 19428.42 | 19152.50 -> 19328.42 |

Reverse HOOK/GET baseline forks are 1744.023/1744.025 B/op; candidate forks
are 1728.021/1984.025. Its mean increase of 112 B/op crosses the unchanged
`max(32 B/op, 5%)` trigger. The candidate's JMH allocation interval is wide
(1856.023 +/- 203.989 B/op), so this is an acceptance failure/unstable result,
not proof of a universal 112-byte regression. Baseline AUTO_NO_REGISTRY likewise
splits 1088/832 and enabled-only splits 1392/1136 in reverse. P2 explains the
observed enabled-only mechanism, but no candidate compiler trace proves the
cause of these other 256-byte modes. Do not attribute them to C001 savings.

Multiple-hook allocation increases in both orders (about 172 and 176 B/op),
even below the percentage trigger; one-hook means also rise, with overlapping
intervals. Multiple-observer direction changes by order. No latency mean crosses
20%, but overlapping/noisy intervals and these adverse controls cannot certify
an improvement merely because a threshold is not crossed. Warm subscription
controls and all TARGET results are retained; publisher-captured discovery is
not repeated on resubscription, so those differences are not discovery savings.

This is sufficient to reject this candidate without another result-selected run.
The no-change decision also rests on the demonstrated required provider work,
local allocation-site evidence, full semantic characterization and the examined
iterator/copy tradeoff, not merely on V34's previous deferral. It does not claim
that no future algorithm could improve discovery. Any new candidate or compiler
investigation needs new bounded scope; none is silently approved here.

### Artifact Identity and Reproduction

The saved baseline JAR SHA-256 is
`f30482de11e8ccefa8980beb29f4074a9de011683cb8cee37d0a84925b8239db`;
candidate is `12c98844ddb187b040f88b9cafddfbbe527edbfb873eddb1835df5c2dceead93`.
Audit matches **39 harness source files**, **120 non-starter dependencies** and
ordered classpath. Only handler/nested-handler executable classes differ (12
class files, including line-number shifts); no other shaded entries differ.
The baseline is a reused, source-reconciled V34 artifact, not a fresh
Central download. Candidate source/patch, build commands, effective POM and
dependency hashes remain with it. The final production source equals the starting
commit; candidate artifacts are retained as rejected evidence only.

The [rejected patch](c001-rejected-candidate.patch) is tracked for reproduction,
not applied production code. In a separate experiment checkout, after collecting
the baseline attribution, apply it with:

```bash
git apply --check --unidiff-zero roadmaps/v35/c001-rejected-candidate.patch
git apply --unidiff-zero roadmaps/v35/c001-rejected-candidate.patch
mvn -B -ntp -s .mvn/maven-central-settings.xml -pl reactive-http-client-starter -am -DskipTests install
mvn -B -ntp -s .mvn/maven-central-settings.xml -f reactive-http-client-benchmarks/pom.xml clean package \
  -Dtest=V34WorkloadContractTest '-DargLine=-XX:+DisableExplicitGC -XX:ActiveProcessorCount=2'
```

Use the Java 21.0.8 environment and writable general repository from the baseline
record. Preserve the shaded JAR, `dependency:build-classpath`, `dependency:tree`,
`help:effective-pom` outputs and `verify-v34-benchmark-inputs.py record` manifest
in `candidate/` before the scored runner audit. Exact resolved commands are in
`candidate/commands.json`; the saved baseline bundle is a prerequisite, not a
file available from a clean clone. Do not overwrite sealed evidence. Roll back
only this experiment afterward with `git apply -R --unidiff-zero` on the same
patch. Final local installation is restored to the unchanged production source.

### Verification and Limits

`DefaultPathCostOwnershipTest` now has 36 cases, including 21 new cases for
null/empty fallback, ordered and duplicate consumers, full materialization,
parallel streams, prototype capture and synchronous support/provider failures.
Existing concurrent first-invocation, master-off/no-registry application callback,
independent pool telemetry, mutation and terminal-state controls remain.
Baseline and candidate runs initially passed 106 cases; after the last ordered
prototype witness the candidate passed 107. These overlapping runs are not summed.
The final restored-source run passed 204 cases across eight classes (107 contract
cases and 97 documentation cases); the final documentation-only rerun overlaps
those 97. The candidate benchmark correctness suite passed 40 cases, and 26
Python checks passed. The restored local artifact's 310 classes match the saved
baseline. No unit test asserts
timing, B/op, compiler mode or forced collection; Maven tests disabled explicit GC.

This is local C001 rejection evidence, not a full 60-row acceptance, fresh native,
strict API or published-consumer verification. No leak/RSS conclusion follows
from these samples. P2/P3 evidence and V1-V34 records remain unchanged. C002,
C003, both C004 rows and C005 remain unresolved; active V35 and release scope
stay unchanged.
