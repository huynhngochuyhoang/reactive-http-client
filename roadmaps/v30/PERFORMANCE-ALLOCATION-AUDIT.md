# V30 Performance and Allocation Audit

Status: harness and bounded allocation/ownership audit implemented; manual
release-quality measurements and their review remain open (Priority 12.3).
No public performance claim.

Evidence date: 2026-09-11. Reachable source base:
`131a72481ea8ae4393a91ccae12e8695388d5a20`, plus this working-tree patch, not a
clean release benchmark commit. Reactor `4.3.0-SNAPSHOT`, published starter/API
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

## Manual completion gate

After committing this reviewed tree, execute from the same idle Linux machine:

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

Priority 12.3 stays open until these artifacts are available and reviewed,
with either a supported versioned result or an explicit
**no-public-performance-claim** disposition. Smoke/JFR results do not close it.
No native, consumer, API, publication or final V30 release gate is closed here.
