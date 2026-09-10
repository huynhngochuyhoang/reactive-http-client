# V30 Performance and Allocation Audit

Status: Priority 12 complete, including the manual release-run review on
2026-09-11. **No public performance claim.** Regression flags remain recorded;
evidence completion is not final release approval.

Initial bounded evidence date: 2026-09-11. Reachable source base:
`131a72481ea8ae4393a91ccae12e8695388d5a20`, plus the retained implementation
patch, not a clean release benchmark commit. Subsequent manual runs use clean
`ad87b60fa4daa144b6a01fa258932747f4288284` as detailed below.
Reactor `4.3.0-SNAPSHOT`, published starter/API
baseline `4.2.0`, Boot `4.0.0`, Maven `3.9.9`, GraalVM JDK `25.0.3`, Java target
`21`. Historical V1-V29 measurements are not rewritten or promoted.

## Workload identity

The [benchmark guide](../../docs/22-benchmarks.md#v30-active-work-performance-and-allocation-audit)
and [manual wrapper](../../scripts/run-v30-benchmarks.sh) define the executable
row inventory. Eight unchanged methods yield 16 comparable average-time and
throughput rows against published 4.2.0:

- Cache-disabled GET publisher creation and subscription.
- V29 unweighted publisher creation and subscription.
- V29 weighted, metrics-disabled publisher creation and subscription.
- V29 weighted hit and metered accounting publication.

Eleven new proxy methods each run with cache telemetry disabled and enabled,
in both modes (44 rows). They cover publisher creation, hit, miss, weighted
publication, caller rejection, load rejection, same-key join, release/reuse,
refresh start, refresh capacity skip, and real HTTP/1.1 loopback join.
One four-thread saturated admission primitive contributes two more rows.
Total: **20 methods, 62 rows; 16 matched, 46 current-only, zero baseline-only**.
The new feature rows have no 4.2.0 counterpart. They are not WebClient speedup
ratios. Parameter values participate in report identity, not just display.

Proxy fixtures use the production invocation handler, declarative API name
`read`, caller/source reporting state, real cache manager and metric selection.
No convenience `unknown` API meters are allowed. Each fixture has 32 entry
slots, two callers, one foreground load, and, when selected, one refresh.
Weighted fixtures have a 65,536 decoded-response-byte bound and five-byte
responses. Resilience is disabled; request/logical deadlines are ten seconds.
Refresh eligibility uses a synthetic ticker with a one-day hard TTL, one-ms
refresh threshold, and ten-second refresh timeout.

Single-flight responses are explicitly held until two flight members attach;
both caller futures must still be pending and dispatch count must be one
before release. There is no fixed response delay used to infer membership.
Every completed scenario checks live slots, membership, dispatch deltas and
entry capacity. The loopback fixture also uses a gated server, IPv4 loopback,
two physical pool slots and disabled Reactor Netty transport retry.

No-network proxy rows replace only the exchange function. Rejection/join/
release/refresh operations include their full setup, seed, hold, cancellation,
release and invariant checks. They are whole-cycle costs, not isolated event
latencies. Miss/publication rows include eviction churn after capacity fills.
The four-thread primitive holds one owner for the trial and measures failed
acquisitions only; it neither creates a fresh reservation nor measures the
proxy or cache telemetry. Separate fixtures own their registry/scheduler/pool,
and trial cleanup checks that no cache meters remain.

## Optional-path audit

- `ReactiveClientInvocationHandler` freezes work selection and precomputes a
  validator even for cache-disabled or unselected-limit clients. Its invocation
  path still checks for configuration mutation. `CacheWorkPolicy.validator`
  traverses the fixed set of interface endpoint plans, evaluates cache
  selection and, for selected limits, normalizes limits. The loop is bounded
  by interface method count, not rejected calls, live work or cache size.
  Decision/selection objects and selected `Limits` can be transient allocations.
  This is intentional fail-fast mutation enforcement, not zero-cost opt-out.
- No per-call work reservation or selected-limit ownership wrapper is created
  when limits are absent. The precomputed maps belong to the handler/factory,
  not a growing request history. Existing key, reporting, cache and request
  preparation costs remain and must not be relabeled as admission cost.
- `CacheWorkAdmission.acquire` does a synchronized fixed-policy map lookup and
  counter check. Saturation creates a structural rejection exception with
  stack traces/suppression disabled, without an owner, task or capacity queue.
  Admission does not sleep, wait for capacity, scan entries or scan active
  callers. Java monitor contention can still block a thread briefly: this is
  not a lock-free or wait-free guarantee. The four-thread row includes that
  contention. Test harness gates/collection waits are not production admission.
- Successful acquisition creates one frame-aware reservation. Entered
  preparation/cancellation frames retain it until terminal cleanup unwinds.
  Release clears the owner link; no completed-reservation collection is added.
  Shared work remains legitimately owned while another caller is interested;
  independent caller-owned loads can outlive manager close until terminal.

These costs are recorded, not optimized in this benchmark-only priority.
The release comparison must review cache-disabled and unselected rows, not
assume optional selection makes all overhead disappear.

## Transient allocation and retained ownership

The short GC-profiler run has seven average-time rows: primitive rejection
and proxy hit/caller-rejection/load-rejection with both metric settings.
It uses one one-second warmup, one one-second measurement, one fork, and
`-prof gc`, not release-quality settings. Its JSON records B/op separately
from us/op. Primitive rejection observes transient exception allocation;
proxy rows also allocate request/caller/source/reporting state and harness
objects. Neither is retained-entry weight or exact reservation size.

This run is a wiring/measurement check on a working machine, not a quiet-host
latency comparison. Startup/JIT/GC/harness effects and concurrent machine work
preclude promoting its numbers, subtracting rows as precise feature overhead,
or drawing a metrics-on/off regression conclusion.

`V30CacheWorkPerformanceBenchmarkTest` has four parameterized cases:

- Both telemetry modes exercise every new workload, assert real API caller/
  load/refresh counters, and check four threads making 1,000 saturated
  acquisition attempts each against one fixed owner.
- Both modes make 2,000 real-proxy caller rejections while an admitted leader
  and waiter keep one gated source live. Each rejected call carries a one-KiB
  context object. The first 32 references per mode must collect before
  admitted work is stopped. Slots stay at two callers/one source/one waiter,
  entries stay zero, and dispatch count stays one. After cancellation all
  owners disappear and a replacement request succeeds.

The separately rerun `CacheWorkOwnershipContractTest` has **25 cases**. Its
reference-queue checks cover rejected callers/loads, skipped refresh closures,
detached caller versus live source ownership, explicit eviction and closure.
While admitted work is saturated, only its single expected virtual timeout
task remains; terminal cleanup leaves no queued tasks or key tokens.
This complements repeated proxy saturation; it does not claim an exact heap
size or arbitrary application context collection while a source still needs it.

A bounded JFR of the four harness cases uses profile settings, a 32-MiB
recording cap and dump-on-exit. The retained `work-retention.jfr` is 1,355,278
bytes over two seconds, with 422 allocation samples; one samples
`CacheWorkRejectedException`. Sampling can miss reservations and cannot prove
zero allocations or determine exact per-object sizes. The weak-reference/
reference-queue assertions, not absent JFR samples or process RSS, establish
the tested ownership release. Explicit GC is diagnostic test code only.
Raw JFR/environment data stays under ignored `target/`; review before sharing.

## Verification and artifacts

Local evidence is under `target/release-evidence/v30/priority12/`:

- Current packaging/discovery and report/harness regression: **27 tests**,
  zero failures/errors/skips. Fresh published-4.2.0 packaging/discovery:
  **23 tests**, excluding only the four V30 cases. Maven Central provenance
  and artifact hashes are in `baseline-provenance/`.
- Current smoke: **62 rows**. Published smoke: **16 rows**. JSON, Markdown,
  environment sidecars and shaded jars are retained separately. The smoke
  comparator checks **16 matched / 46 current-only / zero baseline-only**;
  its noisy timing review triggers are not a release decision.
- GC allocation smoke: seven rows under `allocation/`; JFR run: four cases;
  ownership run: 25 cases. Raw logs and copied XML retain their actual totals.
- Documentation/configuration-metadata regression: **101 tests**, zero
  failures/errors/skips; reactor validation and diff checks pass.
- The exact-coverage verifier self-test checks missing modes/parameters,
  duplicates, units, fork/iteration/thread counts and allocation metrics.
  The manual wrapper refuses dirty commits, reused output/repository paths,
  failed runs and changed commits. Root `target/` evidence survives module
  cleanup.

See `commands.md` and logs for commands, `source.patch`/`source/` for the
working-tree implementation, and `sha256.txt` for evidence hashes. Local
evidence is not a substitute for a reachable clean release commit.

## Manual release review

The user executed these commands at clean reachable commit
`ad87b60fa4daa144b6a01fa258932747f4288284`; review completed 2026-09-11.
The commands remain the reproducible entry points; preserve existing evidence
before rerunning because the wrapper refuses occupied output/repository paths.

~~~bash
bash scripts/run-v30-benchmarks.sh current
bash scripts/run-v30-benchmarks.sh baseline
bash scripts/run-v30-benchmarks.sh compare
~~~

The wrapper selects two forks, five warmup and measurement iterations per
mode, GC allocation profiling, exact row coverage and fresh Central artifacts.
V29/V30 iterations last one second; the unchanged cache-disabled methods retain
JMH's default duration. Review durations in the result JSON for both runs.
It retains commit cleanliness, machine/toolchain metadata, source artifact
provenance, report inputs and hashes. Review both environment sidecars and the
comparison, including every regression flag and the 46 new-only rows. A source
or benchmark-path correction requires rerunning both measurements.

### Provenance and coverage

- Current command window: `2026-09-10T19:39:04Z` to `20:14:46Z`.
  Baseline: `20:15:28Z` to `20:34:39Z`. Comparison: `20:35:08Z`.
  Both commands exited zero, with empty before/after worktree-status files;
  both sidecars identify the same harness commit and `smokeOnly=false`.
  The local calendar date is September 11 (UTC+07:00).
- Revalidated all 62 current and 16 published rows: 16 matched, 46 current-only,
  zero missing baseline rows. Each result has two forks with five measurement
  samples each and five warmup iterations, expected units, finite positive
  scores and GC B/op. Both telemetry values occur for every new proxy method.
  Only the saturated primitive uses four threads; every other row uses one.
  Cache-disabled iterations are ten seconds in both runs; V29/V30 use one.
- Same Intel i7-1165G7/eight logical CPUs, Linux amd64/kernel, Maven `3.9.9`,
  GraalVM JDK `25.0.3` and JVM tuning flags. Both use Boot `4.0.0`, WebFlux
  `7.0.1`, Reactor Netty `1.3.0`, Netty `4.2.7.Final`, Jackson `3.0.2`,
  Micrometer `1.16.0` and OTel `1.55.0`. Expected differences are the starter
  artifact version and dependency repository paths.
- These are same-stack release-profile runs, not proof of a permanently idle
  or thermally controlled host. CPU scaling snapshots differ (61%/65%), both
  show occupied swap, and free/available memory varies. No sustained pressure,
  governor or background-work trace establishes the cause of that variation.
  Wide confidence intervals in several new proxy/loopback rows further limit
  precise latency and metrics-on/off claims.
- Original `current/`, `baseline/`, `benchmark-comparison.md` and
  `release-sha256.txt` remain unchanged. Their checksums and the fresh Central
  starter artifact hashes verify. Existing coverage files intentionally retain
  `release-quality-pending-review`, the verifier's pre-review machine output;
  this signed-off narrative supplies the disposition without rewriting inputs.

SHA-256 anchors, relative to `target/release-evidence/v30/priority12/`:

| Artifact | SHA-256 |
|---|---|
| `current/release-jmh.json` | `9309483a12039a155af46b1e6dbe8bd5b57bce3cdf652bcb9294bf5a973dd24e` |
| `baseline/release-jmh.json` | `8fcd4c9107b13b6b62e464d30ab663d9751b39e34f4c56f83a5b38c3253a9538` |
| `benchmark-comparison.md` | `9f43d3030edca07dd0a7aed072a58a0d33474c0d9d67dadf677a6a4fd86909ba` |
| `release-sha256.txt` | `16ff85ab138cee5d3fcd94de58ba6016830d0e5d1bfb9598cfa274d15a47ee0e` |
| `current/benchmarks.jar` | `90798445b8ffbf58cb1dffaba304c4f3fb5a3b2a5f209a4fdb2058ffc1ee4dfa` |
| `baseline/benchmarks.jar` | `0083642739955b520dcb7f2f08cca5e368eae746c98fd2c562aecbb947152652` |

The published starter jar hash is
`384ef7fc0361877aee94bfc71f184304b50da19f3e5875e273f163033d6b90de`;
its POM hash is
`d578ba1a083b5fd53224fe9569b72a43ceed56953a4a604a08569ebd962ee8ce`.
`baseline/provenance/` retains the Central remote markers and checksum list.

### Review flags and limits

The comparator emits **16 informational flags across five method groups**.
The following are internal evidence observations, not product latency claims.
Values are baseline -> current, rounded from the unchanged JSON:

| Shared method | Flagged observations | Disposition |
|---|---|---|
| Cache-disabled publisher creation | Average 0.414 -> 0.502 us/op (+21.23%); p50 also flagged. Throughput falls 23.65% even though the comparator labels it `ok`. | Preserve the startup-policy-check cost risk; no zero-overhead claim. |
| V29 unweighted publisher creation | Average 0.027 -> 0.086 us/op (+214.76%); p50/p95/p99 flagged. Allocation 160.010 -> 224.030 B/op in average mode, 160.009 -> 280.032 B/op in throughput mode. | Regressed publisher-only path; distinct from subscription latency or network time. |
| V29 weighted metrics-disabled publisher creation | Average 0.028 -> 0.082 us/op (+194.73%); p50/p95/p99 flagged. Allocation approximately 160 -> 224 B/op in both modes. | Absent work limits do not mean allocation-neutral invocation. |
| V29 metered accounting publication | Throughput-mode allocation 3040.992 -> 4241.079 B/op (+39.46%). | Retain flag; average-mode allocation also rises but remains below the relative review trigger. This pair does not isolate the allocating component. |
| V29 weighted hit | Throughput-mode allocation 516.093 -> 627.769 B/op (+21.64%). | Retain flag; average-mode allocation rises 2.47%, so do not generalize one mode's magnitude. |

Source inspection identifies method-selection validation on invocation even
when limits are absent. This is a plausible contributor to publisher-creation
cost, not a causal allocation profile proving every delta. The full
cache-disabled/unweighted/weighted subscription averages change by -2.26%,
+2.90% and +1.37% respectively in this pair; those results do not cancel the
publisher-only regressions. A throughput `ok` label does not prove no decline.
The comparator's p50/p95/p99 summarize JMH iteration scores, not per-request
service-latency percentiles.

All 46 new-only rows retain their two-mode/telemetry coverage and asserted
workload identity. The eleven proxy methods measure scenario operations,
including gates, preparation, seed/cancel/release and checks where applicable.
The isolated rejection row is not a proxy or metrics pipeline; its approximately
40 B/op is transient allocation per rejected acquisition in this JVM, not
retained ownership or an exact portable object size. Large error bars in
loopback, refresh and several miss/reuse rows preclude precise metrics-on/off
overhead ratios. No 4.2.0 counterpart is invented for those rows.

### Completion disposition

**Priority 12 is complete with no public performance claim.** The benchmark
pair is accepted as reviewed engineering evidence with explicit regressions
and measurement limits, not as a regression-free performance certification.
No promoted `docs/benchmark-report-4.3.0.md` or numerical release wording is
created. Preserve these flags for Priority 13's scope decision; optimization
or a numerical claim requires fresh profiling and comparable clean runs.
The bounded retention checks remain independent evidence, not a conclusion
drawn from B/op or RSS. No runtime, benchmark or public API code was changed
while closing this review. Native, consumer, API, publication and final V30
release gates remain separate.
