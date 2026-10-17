# V36 Matched Cost and Independent Acceptance

> **Recorded:** 2026-10-14
> **Status:** Priority 7 review complete; full-scope cost acceptance blocked
> **Implementation:** V36-P6-1; approved no production change
> **Findings:** A001 and A002 unresolved/blocking; neither excluded
> **Execution:** exact saved-input reuse; zero new workers
> **Release scope:** unselected

This executes [Priority 7](CHECKLIST.md) after the approved
[no-change decision](CORRECTION-DECISION.md). Completing this review records
**non-acceptance**, not a performance pass. It does not change the frozen
investigation, authorize measurement or satisfy the later closure gate.

## Applicability Frozen Before Review

The clean starting commit is `edb99ad2bfab1511079d57a3979fdefccb6cc158`, tree
`f8cbad2ea5f502f15fae6619f411c25e3af2cc35`. This record and its guard use that
reachable base plus the preserved docs/test patch. No production, scored
harness, dependency, payload, fixture, JVM setting or default changes. V36's
recording/reader corrections are diagnostic tooling, not a changed scored path.

**New scoring N/A on 2026-10-14:** there is no retained runtime correction or
corrected scored harness to compare. Reuse the complete V35 P10 pair, not a
favorable subset or a newly rebuilt artifact under its old identity. No new
scoring matrix or worker budget is selected. All **48/48** approved V36 worker
slots remain consumed, with zero new workers, probes, launchers or preflights.
Future changed inputs require a frozen affected matrix and explicit numerical
approval under 3.3; shared default-path changes require the existing 60-row
matrix. Remaining clock budget is not permission to run again.

## Exact Input Reuse

The offline audit rehashes **1,816 files** across V35 P10 and V36 P4/P5/P6,
verifies exact manifest coverage and all four archive hashes. These bundles
remain unchanged; the current analysis is separate. It also verifies:

- **202 production/build/benchmark inputs** unchanged from reachable V35 P10
  integration `85948837c4f4001f648b15adb3efd80b9de05abd` through the current tree.
- All **39 harness/build inputs** match both saved inventories and this checkout.
- Both ordered 121-JAR classpaths rehash, with **120 non-starter JARs** identical
  and only the starter coordinate normalized for order comparison.
- All **24,224** shaded class entries match between the saved artifacts, including
  **310 starter classes** verified against each resolved starter. Only the
  starter's embedded Maven POM/properties differ.
- Saved **Central-only 4.4.2** POM/JAR provenance and remote markers still match;
  this is **not a fresh Central download**, publication or signature check.
- Benchmark correctness XML and summaries retain **75 cases per artifact** with
  no failures/errors/skips, including 40 workload witnesses per artifact. This
  is reused semantic evidence, not a fresh test execution.

| Saved artifact | SHA-256 |
|---|---|
| Published starter 4.4.2 | `fb8646ce2f6ed172598ff446cdfa9da2f348c0fd56a2b4c9a02e7ac1dae803c1` |
| Scored starter 4.5.0-SNAPSHOT | `c3746166dc136fe56235fe5aea2f2b603ddc0d2781c00b06ad8e9f670e57076f` |
| Baseline shaded benchmark | `e0de82156b03f42c986f10fd9a66229956df64189e5de605b345affccdd48d6c` |
| Current shaded benchmark | `b9fd5b42980c03cd8c0415f68101aef553e266354b672ea4f3b3ca992a3376b9` |

The scored execution is still dated **2026-10-11**, with recorded source
`beb8a237215529c9c8c7ddc285295c9564e4bce0`. That object is not an ancestor of
this reviewed history; the reachable integration matches its inputs, but is
**not relabeled as the execution commit**. Restore the original hash-verified
bundles before analysis in another clone. Lost V35 P2-P8 recordings remain
lost. Source identity and archive integrity do not prove causal attribution.

## Matched Scores and Confirmation

The [existing reviewer](../../scripts/review-v34-benchmark-results.py) reproduces
both saved reviews exactly from raw JSON: **60 primary rows per artifact** and
**four reverse-confirmation rows per artifact**. No row, fork, interval or
iteration is removed. This retains **240 primary** and **16 confirmation**
workers, **1,280** measurement iterations with **2,560** latency/allocation scalar
samples, not new execution. V35's other 82 diagnostic workers remain separate;
its total is still 338. V36's 48 workers are diagnostic evidence, not scores.

Commands and timestamps verify baseline-then-current primary execution followed
by current-then-baseline confirmation. The confirmation set is exactly all three
primary flags plus the historical RESILIENCE_ENABLED_ONLY GET publisher row.
Java 21.0.8, Boot 4.0.0, JMH 1.37, one thread, average time, two forks, five
one-second warmups/measurements, GC profiling, fixed 512 MiB heap, two active
processors and WARN logging remain matched. The complete intervals and host
snapshots stay in the saved bundle; the workstation was not a dedicated latency
lab and the measurements establish neither host exclusivity nor mode frequency.

The unchanged triggers are **>20% latency** and **>max(32 B/op, 5%) allocation**
growth. Re-analysis retains **zero latency flags** and **three allocation flags**
in primary, and zero flags in reverse confirmation. These are review triggers,
not statistical proof of a regression or a pass when a later aggregate clears.

All flagged rows below are GET warm-publisher assembly, not downstream traffic
or transport latency. Values are rounded B/op means with the saved JMH error;
raw unrounded results remain authoritative.

| Profile | Primary baseline mean +/- error | Primary current mean +/- error | Delta / review threshold | Reverse baseline / current means |
|---|---:|---:|---:|---:|
| AUTO_NO_REGISTRY | 832.013 +/- 0.034 | 960.014 +/- 203.988 | 128.001 / 41.601 | 832.013 / 832.013 |
| AUTO_REGISTRY | 1744.021 +/- 0.055 | 1872.022 +/- 203.988 | 128.002 / 87.201 | 1744.019 / 1744.021 |
| HOOK | 1744.022 +/- 0.059 | 1872.022 +/- 203.987 | 128.000 / 87.201 | 1744.021 / 1744.022 |

AUTO_REGISTRY's primary current fork means remain 2000.024/1744.021 B/op;
HOOK's remain 2000.023/1744.022. Their latency mean movements are respectively
7.216% and 4.748%, below the latency trigger, not evidence that allocation is
resolved. AUTO_NO_REGISTRY's original flag stays visible, with its V35 explained
no-change disposition intact; neither its explanation nor the enabled-only
control is transferred to the remaining profiles. No rejected candidate returns.

## Independent Acceptance

| Finding / scope | Evidence reviewed | Disposition and owner |
|---|---|---|
| A001 / AUTO_REGISTRY | Original primary allocation flag; reverse low modes; V36 O/M/T low only; I high on both artifacts with observed resolver refusal. No ordinary high trace explains the primary split. | **unresolved/blocking**; performance maintainer. No production change is selected, but causal acceptance is not met. |
| A002 / HOOK | Original primary allocation flag; reverse low modes; V36 O high/low on both artifacts, M/T low, I high. The two O-high workers lack compiler traces. | **unresolved/blocking**; performance maintainer with observer/hook review. Same-JAR reproduction and forced non-inlining do not attribute the ordinary high mode. |
| Combined A001 + A002 | Exact input equivalence and preserved semantic witnesses exclude a retained production delta, not unexplained compiler/host variation. Neither independent row passes. | **not accepted**; both findings remain required scope. No scope exclusion, blanket performance pass or release approval follows. |
| E001 / evidence reliability | New capture associations and synthetic validation are verified within P4/P5 limits. Legacy unsupported pairings remain rejected. | Retain verified tooling correction; evidence maintainer. It does not repair legacy fork/trace pairing, exact executing-code identity or individual probe census. |

The [intervention record](INTERVENTION-RESULTS.md) shows sensitivity to a compiler
boundary, not an exact allocation-site count or the original ordinary cause.
Instrumentation perturbation versus limited sampling remains unresolved. Missing
ordinary high-mode attribution remains blocking even though no runtime patch
survived. [V36-P6-1](CORRECTION-DECISION.md) approves retained behavior only.
Priority 10 must obtain any explicit scope exclusion or keep V36 open; this
review supplies no such approval. Readiness remains `activeRoadmap=v36`,
`plannedFinalVersion=null`, `releaseLane=unselected`. Priorities 8-10 stay pending.

## Public Claim Boundary

**No public performance claim** or promoted report is produced. These historical
internal scores are not a new speedup, release-quality comparison or raw-client
claim. Diagnostic O/M/T/I numbers cannot replace scores. B/op is not retained
heap, process/pod RSS or a memory-leak diagnosis; no runtime tuning or snapshot
upgrade remedy is recommended.

The [promotion rules](../../docs/22-benchmarks.md) still require clean reachable
source, qualified release-quality measurements, separate published/current
reports, exact scenario, compared surfaces and metric, resolved review movement,
and a sanitized versioned report linked by the claim. Missing original causal
evidence and an unreachable execution commit cannot be repaired by relabeling
this audit. No number is required for this no-change review.

## Verification and Preservation

The new documentation guard first failed because this record was absent; the
failed command, exit and XML are retained. Final documentation/readiness/link
checks pass **115 cases per Boot row** (4.0.0/4.1.0), zero failures/errors/skips,
with explicit GC disabled. **83 Python tooling tests** and `git diff --check`
pass, including existing threshold, missing-sample, mismatch and smoke-rejection
guards. Overlapping reruns are not added to unique totals.
P6's 141 retained-behavior cases per Boot row are reused,
not added to fresh counts. This is not a new native, API, assembled-consumer,
full-reactor or benchmark run; Priority 8 remains independently applicable.

The audit, commands/exits, full derived reviews, per-flag fork means/intervals,
source inventories and docs/test patch are retained under
`target/release-evidence/v36/priority7/`, copied before cleanup to
`verification-evidence/v36-priority7/` and a dated verified archive. The checklist
anchors its seal. Historical raw data remains in the four separately sealed
bundles, not copied into or overwritten by the new review. These ignored bundles
do not ship with a clean clone; their availability is a prerequisite, not a
claim that hashes recreate missing evidence.

Offline review only, using restored verified P10 inputs and fresh output paths:

```bash
PAIR=verification-evidence/v35-priority10
python3 -B scripts/review-v34-benchmark-results.py \
  --baseline "$PAIR/primary/baseline/results.json" \
  --current "$PAIR/primary/current/results.json" \
  --output /tmp/v36-p7-primary-review
python3 -B scripts/review-v34-benchmark-results.py --confirmation \
  --baseline "$PAIR"/confirm/baseline-*/results.json \
  --current "$PAIR"/confirm/current-*/results.json \
  --output /tmp/v36-p7-confirmation-review
python3 -B -m unittest discover -s scripts -p 'test_*v3[3456]*.py' -v
```

The sealed audit driver additionally verifies complete manifests, original command
order, source/dependency/shaded-class identity and exact equality with saved
reviews. None of these commands executes a measured JVM or grants new budget.
