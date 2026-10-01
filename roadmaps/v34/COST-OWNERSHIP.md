# V34 Default-Path Cost and Ownership

> **Comparison:** published `4.4.2` versus pre-fix `4.5.0-SNAPSHOT`
> **Implementation and release scope:** unselected
> **Status:** Priority 3 complete, 2026-10-01; one allocation flag remains unresolved

Priority 3 of the [checklist](CHECKLIST.md) applies the previously frozen
[workload contract](WORKLOAD-CONTRACT.md) to the
[effective profiles](BASELINE-SCOPE.md#effective-profiles). Scores, structural
counts, sampled attribution and retained ownership are different evidence.
An allocation is not automatically waste, and B/op does not measure retained
heap, direct memory, RSS or a leak. Priority 4.3 must select any implementation.

## Source and Experiment

Both artifacts were rebuilt with the committed harness from clean reachable
`ec225b8ed93ab0d1bd461d4eda7a38f23a2579e1` before any Priority 3 edits.
Production sources are unchanged from
`v4.4.2`; later edits are ownership tests, analysis guards and this record only.
Published `4.4.2` comes from the separately Central-resolved repository reused
and revalidated from Priority 2, never from a reactor installation. New builds
and scored output live in `target/release-evidence/v34/priority3/`.

The two builds each pass 75 ordinary benchmark tests with explicit GC disabled.
Their 39 harness source/POM files and 120 non-starter dependency JAR hashes
match. All 310 starter class files are byte-identical across the resolved JARs;
their differing overall JAR hashes do not indicate changed implementation bytes.
Oracle Java 21.0.8, Maven 3.9.9, Boot 4.0.0, WebFlux 7.0.1,
Reactor Netty 1.3.0, Netty 4.2.7.Final, Jackson 3.0.2 and Micrometer 1.16.0
remain fixed. No upgraded framework row is presented as starter performance.

The scored settings are two forks, five one-second warmups and five one-second
measurements, avgt/ns, one caller thread, GC allocation profiling, fixed 512 MiB
heap and ActiveProcessorCount=2. No Maven, correctness test or separate profiler
runs concurrently with scoring. The workstation is shared, not an isolated lab;
affinity, CPU/memory pressure, limits and before/after counters are retained.

The host is an i7-1165G7 (four cores/eight logical CPUs), with affinity/cpuset 0-7.
The primary pair's boundary samples show 7.82-8.06 GB available RAM, 0.47-0.59 GB
swap in use, CPU pressure avg10 0.02-1.62%, and zero cgroup throttling counters.
CPU/memory maximum files are not visible in this namespace; that is not proof of
an unconstrained host. Boundary snapshots cannot exclude intermediate contention.

The first pair runs published then current. Every mean-latency increase above
20%, or allocation increase above max(32 B/op, 5%), requires the same row/JARs
in reverse order. These are review triggers, not regression allowances. Raw
samples and intervals remain attached to every attempt. The existing report
comparator is descriptive; its older allocation thresholds do not replace V34's
frozen rule. `scripts/review-v34-benchmark-results.py` validates the 60 matched
parameterized/helper rows and applies the V34 rule, including missing-row,
allocation, sample, flag and toolchain rejection.

## Measurement Results

All 60 primary rows completed on each artifact, with ten measurement samples per
row (two forks). The first pair flags zero latency rows and two allocation rows.
Both flagged rows completed reverse-order confirmation using the same saved
JARs and flags. There is no production change to credit for lower means.

The following are **first-pair** means with JMH's reported 99.9% error interval,
not selected best attempts. Latencies are microseconds; B/op are rounded bytes.
P02 is auto-configured without a registry, P03 with one.

| Phase/profile | 4.4.2 us/op +/- error | Current us/op +/- error | 4.4.2 B/op | Current B/op |
|---|---:|---:|---:|---:|
| New context/proxy, P02 | 7,198 +/- 1,555 | 6,592 +/- 1,418 | 3,134,024 | 3,123,345 |
| New context/proxy, P03 | 7,787 +/- 1,248 | 7,077 +/- 993 | 3,357,825 | 3,105,060 |
| First publisher, P02 | 118.38 +/- 92.36 | 67.61 +/- 5.36 | 3,080,479 | 3,064,229 |
| First publisher, P03 | 266.08 +/- 420.98 | 80.47 +/- 6.79 | 3,320,247 | 3,216,571 |
| First no-network call, P02 | 526.97 +/- 49.66 | 482.24 +/- 42.55 | 3,086,606 | 3,082,151 |
| First no-network call, P03 | 768.00 +/- 64.67 | 688.83 +/- 53.60 | 3,252,530 | 3,243,065 |
| Warm GET publisher, P02 | 0.311 +/- 0.024 | 0.293 +/- 0.009 | 832 | 832 |
| Warm TARGET publisher, P02 | 0.696 +/- 0.025 | 0.704 +/- 0.035 | 2,008 | 2,008 |
| Warm GET subscription, P02 | 5.570 +/- 0.185 | 4.977 +/- 0.350 | 13,536 | 13,564 |
| Warm TARGET subscription, P02 | 8.149 +/- 0.799 | 7.948 +/- 0.349 | 17,608 | 17,568 |
| Warm GET subscription, P03 | 9.124 +/- 1.154 | 8.548 +/- 0.686 | 18,072 | 17,916 |
| Loopback GET, P02 | 120.07 +/- 43.41 | 109.84 +/- 39.60 | 29,657 | 29,666 |
| Loopback GET, P03 | 241.75 +/- 134.20 | 156.59 +/- 53.67 | 37,979 | 37,399 |
| Loopback JSON, P02 | 239.53 +/- 97.01 | 177.28 +/- 47.95 | 56,694 | 56,267 |

Construction/first-call B/op includes invocation-fixture setup/teardown, while
latency excludes those boundaries as defined in the workload contract. These are
not process-startup measurements or isolated first-call allocation. Warm
subscription reuses an assembled publisher; loopback includes new assembly,
request/response framing and the synthetic server in the same JVM. It has no
injected backend delay. Its B/op includes server work; neither subtracting the
no-network mean nor attributing the whole difference to the starter is valid.
Empty, entity, string, 4xx/5xx and all other frozen rows remain in the full JSON.

Within current warm GET subscriptions, minimal/P02/enabled-only/observer/hook/P03
are respectively 12,144 / 13,564 / 16,208 / 17,112 / 16,832 / 17,916 B/op.
These are different effective behaviors, not six interchangeable implementations.
Their latencies respectively are 4.760 / 4.977 / 6.302 / 7.454 / 7.377 / 8.548 us.
Consumer callbacks, framework filters and requested metrics must not be removed
to make these rows equal.

The six helper rows also completed: current cached metadata/plan lookups are
7.67/8.75 ns and 16 B/op; concrete plan derivation 1,359 ns and 5,480 B/op;
parsing plus plan 7,279 ns and 14,488 B/op; metadata/plan argument resolution
231/220 ns and 1,288 B/op. These include helper behavior, not end-to-end calls.
No helper crosses the review rule. V33's earlier cold-plan flag is not evidence
of a new V34 regression.

### Confirmed and Unresolved Flags

| Warm GET publisher | First pair: baseline -> current B/op | Reverse pair: baseline -> current B/op | Disposition |
|---|---:|---:|---|
| AUTO_REGISTRY | 1,744.02 +/- 0.06 -> 1,872.02 +/- 203.99 | 1,872.02 +/- 203.99 -> 1,744.02 +/- 0.05 | Flag does not repeat; retain both attempts |
| RESILIENCE_ENABLED_ONLY | 1,136.02 +/- 0.04 -> 1,264.02 +/- 203.99 | 1,136.01 +/- 0.04 -> 1,264.02 +/- 203.99 | Allocation flag remains unresolved |

In both current enabled-only attempts the two fork means are approximately
1,136 and 1,392 B/op; the baseline forks are approximately 1,136. The registry
row similarly switches between 1,744/2,000 B/op across artifacts/attempts. The
256-byte fork split is compatible with differing compilation/escape analysis,
but no compiler trace establishes that cause. Byte-identical starter classes,
overlapping intervals and shared-host sampling do not justify blaming a new
implementation or clearing the flag. Preserve it for Priority 4 review; do not
claim all thresholds passed, choose only a favorable fork, or rerun until green.

## Sampled Attribution

Seven separate one-fork JFR runs use the same current JAR, five warmup and five
measurement iterations, `-prof jfr` and stack depth 128. **Profiler timings are
not scored evidence.** Raw recordings and event exports are preserved with
sanitized class/method/count aggregates in `attribution-final/`.

| Profiled boundary | Allocation events | Sampled/source-supported sites |
|---|---:|---|
| P02 GET assembly | 1,499 | Disabled policy selections; resolver maps; observer/hook streams and lambdas; Mono assembly; body owner |
| P02 TARGET assembly | 1,537 | Header copying, ordered maps/lists/streams and parameter resolution; body owner |
| P02 GET subscription | 1,560 | Spring URI/header/media-type work, Reactor subscribers, synthetic response construction/decoding and byte arrays |
| Enabled-only GET subscription | 1,550 | Reactor context insertion; final request/response evidence; header copies and URI construction |
| P03 GET subscription | 1,520 | Observer tags/events, state/attempt evidence, registry/framework and synthetic response work |
| P02 loopback GET | 1,407 | Client and server Netty headers/channel operations, bytes and Reactor transport; not purely starter allocation |
| P02 context/proxy | 1,556 | Spring bean discovery/reflection dominates sampled sites; concrete plans/grammar also present |

Do not convert JFR sample weights into exact per-call savings: the first worker
sample can carry allocation weight accumulated before recording starts, and
sampling is uneven. Stack absence also does not prove absence of starter-induced
work when the call is now inside a Reactor subscription. CPU evidence is usable
only as site presence: assembly records 386/343 execution/native samples, but
the three subscription profiles have only 6/6/7, mostly profiler/process I/O.
Loopback records 270, including 241 native epoll-wait samples; these are not CPU
utilization percentages. Construction has 18 CPU samples and ten truncated
allocation stacks. No numeric CPU-share or downstream-time claim is made.

The assembly stacks expose both `CacheWorkPolicy.validator` and
`resolveInterfaceLevelLogAnnotation`; the latter repeats annotation traversal
even without a selected logger. This belongs to C003's static-derivation question,
not a newly approved optimization. Structural counts below provide the stronger
invocation/subscription boundary evidence where sampling is sparse.

## Preparation Boundaries

The new `DefaultPathCostOwnershipTest` is an untimed structural witness through
both public handler creation and real outbound auto-configuration/factory paths.
All 14 cases pass with explicit GC disabled. Its spies count discovery and
resolution; those spies never enter scored JARs. Its context-state observations
are deliberately test-owned references, not a claim of garbage collection.

| Ordinary profile | Observer stream / empty fallback per invocation | Hook stream / support checks per invocation | Reporting states per subscription | Terminal consumer |
|---|---|---|---:|---|
| P01 MINIMAL | 1 / 1 | 1 / 0 | 0 | None |
| P02 AUTO_NO_REGISTRY | 1 / 1 | 1 / 0 | 0 | None |
| P03 AUTO_REGISTRY | 1 / 0 | 1 / 0 | 1 | One Micrometer record |
| P04 RESILIENCE_ENABLED_ONLY | 1 / 1 | 1 / 0 | 1 | No terminal snapshot/consumer |
| P06 OBSERVER | 1 / 0 | 1 / 0 | 1 | One application event, exports disabled |
| P06 HOOK | 1 / 1 | 1 / 1 | 1 | One application hook, exports disabled |

Each row assembles two publishers and performs three ordinary subscriptions,
including subscribing the first publisher twice. The witness observes two
metadata lookups, two argument resolutions, one retained concrete plan for the
invoked method, zero assembly dispatches and three exchanges. Active attempts
are cleared. Stateful subscriptions have three distinct state identities; no
state is observed in P01/P02. The synthetic exchange schedules zero Reactor
tasks and acquires zero transport connections; loopback timings are separate.

The late-registration control creates two prototype observers and two prototype
hooks for two subsequent invocations, not three for their three subscriptions.
An earlier publisher remains bound to its earlier no-consumer selection.
The policy control counts two decisions per invocation for its two abstract
endpoints, zero additional decisions on resubscription, and rejection before
dispatch after changing cache selection. The scored fixture has eight abstract
endpoints, not two; do not transfer the witness's interface size to its scores.

The P07 control observes zero pool meters before first transport acquisition,
four after a real HTTP/1 call, and zero after factory destruction, with both
ordinary observability settings. Only the enabled observer records an ordinary
timer; that registry-owned timer survives factory destruction. The fixture
explicitly closes its supplied registry and private server workers. Its initial
failed case incorrectly assumed generic `registerBean` registration alone would
close that supplied registry; the fixed fixture follows the existing benchmark's
explicit ownership. The failed log/XML are retained, not a production leak claim.

Source-level construction sites must not be interpreted as exact heap allocation
counts: escape analysis may eliminate objects, and GC/JFR include framework and
harness work. The relevant distinctions are:

- `getObserver()` queries the retained provider's ordered stream per invocation;
  an empty result additionally calls `getIfAvailable()`. `getLifecycleHooks()`
  queries/filter-copies its ordered stream and calls each candidate's client
  support check. Assembly captures these consumers; resubscribing that publisher
  does not rediscover them. Late registration affects later invocations, and
  prototype consumers are not permanent singletons.
- `metadataCache.get` and argument resolution run per invocation. The concrete
  request plan is handler-cached by method. Request arguments, mutable body and
  per-caller context do not belong in that reusable plan. Default empty maps
  avoid merging but resolution still constructs its maps/projection and publisher.
- `CacheWorkPolicy.validator` checks every frozen abstract method at each
  invocation, including disabled selections. There is no cache lookup or task
  implied by those checks. They preserve the existing post-construction selection
  mutation rejection, so an unconditional no-op would remove a guard.
- `RequestBodyOwnership` plus its released flag are constructed per ordinary
  invocation even for a null or immutable body. Resource-body termination cleanup
  is conditional, and its owner is invocation-wide. A one-shot stream does not
  become replayable because a Mono supports repeated subscriptions.
- Stateless subscriptions construct the request-body descriptor when preparing
  the request; stateful assembly prepares/caches the serialization publisher.
  Null/no-auth bodies do not invoke the JSON codec or schedule bounded-elastic
  serialization. Auth/raw JSON preparation remains a separate selected path.
- `usesSubscriptionState` is false for minimal/auto-without-registry ordinary
  calls. A registry-backed observer, application observer, hook, auth, deadline,
  generated idempotency key, or the enabled resilience flag selects state.
  Enabled-only resilience has no active operator, but still selects state and
  attempt evidence. This is existing cost, not a newly introduced API regression.
- Stateful subscriptions create independent reporting state/attempts and request
  and response evidence. Terminal reporting adds immutable snapshots/events only
  when consumed. Never pool those mutable holders across callers or retain their
  auth/context/response data in a long-lived plan to reduce allocation rate.

## Ownership and Inactive Resources

| Boundary | Owner and lifetime | Limit on reuse / evidence |
|---|---|---|
| Context and factory construction | Context owns properties, metadata, codec and optional observer; factory owns its business pool handle and selected resources | Headless outbound context is not a full inbound application; framework construction is included in the cold fixture |
| Handler construction | Provider handles, concrete-plan map, cache-work selection/plans and a second WebClient view for cache identity live with the handler | The identity view is also built when caching is unselected; it shares transport configuration, not a second active network pool |
| Unselected cache | Static `create` / factory paths return no manager, entries, flights, admissions or cache meters | `Schedulers.parallel()` is accessed before the selected-policy check; a shared scheduler handle is not scheduled refresh work. Legacy public constructors instead retain a lazy manager shell; that source-inspected entry point is not the scored minimal profile |
| Unselected resilience/auth | No named operator registry or auth provider is selected | Enabled-only still selects reporting state; lazy-registry and selected-Retry controls distinguish this from acquisition |
| Publisher assembly | Resolved arguments/body, selected consumers and cleanup holder live with the returned cold publisher | Retaining that publisher can intentionally retain its arguments/consumer; this is not a handler-global request cache |
| Each subscription | WebClient request/spec, context, optional reporting state and attempt evidence | Separate caller state even when sharing the cold publisher; no dispatch at assembly |
| Success/error/cancellation | Logical terminal/reporting, request-body release and response consumption/discard follow their existing paths | Clearing active attempt is not erasing a terminal event retained by an application observer; resources need cleanup, not forced GC |
| Factory destruction | Selected managers/leases and factory-owned connections/pools are closed | Request timers belong to the registry; shared Reactor worker/DNS/scheduler infrastructure belongs to the JVM, not one fixture |
| Context destruction | Context-owned registry/infrastructure closes | Application-supplied resources are not silently reclaimed by unrelated factories |
| Failed construction | Validation-before-acquisition and handler rollback remain covered by `ResourceOwnershipReviewTest`; Spring destroys registered factories, while direct factory callers must destroy their factory after failure | Actual lease/owner sets, live neighboring owners and original failure are checked, not merely an unassigned factory field |
| Runtime versus AOT | `PropertiesBindingLifecycle` clears/stops observations at ordinary singleton initialization; AOT retains weak observations until close | Existing lifecycle tests cover prototype/scoped bursts; no new native or AOT speedup claim |

P05 physical optional absence is the unchanged assembled-consumer evidence
revalidated in Priority 1, not a timing conclusion from a classpath-rich JMH JVM.
P07 independently selected pool telemetry and P08 cache/work/refresh sentinels
remain correctness controls, not the ordinary no-cache profile. Pool gauges
must not disappear just because ordinary observer exports are disabled.

## Ranked Findings and Limits

This ranks investigation value, not approved implementation. No correctness or
ownership defect is reproduced in these controls, and no repeatable *attributed*
starter regression is established. The allocation flag above remains unresolved.

| Rank / ID | Profiles and source anchors | Magnitude, confidence and user impact | Classification / reuse constraint |
|---|---|---|---|
| 1 / V34-C004 | All ordinary calls: [CacheWorkPolicy](../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/core/CacheWorkPolicy.java) `validator` (line 80), [handler](../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/core/ReactiveClientInvocationHandler.java) construction/`invoke` (lines 277, 408) | Every invocation scans all frozen endpoint selections: two decisions in the witness, eight endpoints in the scored fixture. Disabled selection is sampled in the 832 B/op P02 GET publisher. High confidence in repeated work; no isolated B/op or latency saving measured. Potential impact scales with interface size. | Existing mutation guard is intentional. Dormant cache-identity WebClient/shared scheduler handle are not managers/tasks/leaks. Any fast path must still reject selection mutation, preserve physical absence and independent pool metrics; no blanket skip. |
| 2 / V34-C003 | P01-P04/P06: [resolver](../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/core/RequestArgumentResolver.java) `resolve`/`copyHeaders`; [URI builder](../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/core/DeclarativeRequestUri.java) `parse`; handler `requestPlan`/`resolveInterfaceLevelLogAnnotation` | P02 TARGET assembly 2,008 vs GET 832 B/op; target helper resolution 1,288 B/op. One cached concrete plan survives repeat calls; dynamic argument/header and URI work is sampled. High confidence in work sites, not causal savings from subtraction. Material on very fast/high-volume clients. | Mostly necessary projection; possible repeated static derivation remains a hypothesis. Preserve public metadata/generic/API-ref precedence, header/query order, null/empty, URI encoding and dynamic inputs; never cache resolved requests. |
| 3 / V34-C002 | P01/P02 ordinary owner; P04/P03/P06 state: handler `invokeResolved`/`usesSubscriptionState`/`RequestBodyOwnership` (628, 789, 2888), [reporting state](../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/core/SubscriptionReportingState.java) | One source-level cleanup holder per invocation, even no body; zero reporting states in P01/P02, one per P04/P03/P06 subscription. P04 current 16,208 vs P02 13,564 B/op; not an isolated holder cost. Owner/state sites sampled. High confidence in ownership, unresolved publisher fork allocation. | Required cleanup/state is intentional; unnecessary-wrapper hypothesis is not yet isolated. No pooling of request/context/auth state; preserve resource release, repeated/concurrent subscriptions, discard, retry/auth/deadline evidence and terminal-once semantics. |
| 4 / V34-C001 | All ordinary profiles: handler `getObserver`/`getLifecycleHooks` (325, 340) | Two provider streams per invocation, plus empty observer fallback; hook support once per candidate. Current P02 GET assembly 293 ns/832 B/op includes this work. Late prototype witness proves two materializations for two invocations, not three subscriptions. High confidence in frequency; provider-only cost unmeasured. | Dynamic discovery is intentional. An empty/single-result cache could break late registrations, prototype ownership, support or ordering. No permanent consumer cache or shared mutable consumer without an approved contract. |
| 5 / V34-C005 | Construction and AOT lifecycle: [factory](../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/core/ReactiveHttpClientFactoryBean.java), [binding lifecycle](../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/config/PropertiesBindingLifecycle.java) | P02 context/proxy 6.59 ms, 3.12 MB/lifecycle; much broader than first-call work. Cold parsing/planning 7.28 us/14.49 KB helper. Warm plan identity retained; seven lifecycle regressions stop/clear normal runtime tracking. High confidence in tested boundary, low CPU attribution for cold work. | Expected framework/validation work, no reproduced warm-path tracking leak. No general Spring/AOT selection rewrite. Reusing contexts/metadata can alter application ownership; cold allocation is not a steady-state gain. |

C001-C005 remain **unselected**. No new public API, performance switch,
dependency, default, cache/retry rule or release is selected. Priority 4 must
compare bounded alternatives, no-change and acceptance/rollback tests before
asking for specific approval; this record grants none.

Retaining the current implementation is a valid outcome. Any proposed reuse must
account for dynamic providers, client support/order, complete public metadata,
mutable request inputs, selection validation, concurrency and terminal cleanup.
No pod-memory, leak-fix, universal speedup or process-startup claim follows from
these synthetic allocation/latency rows.

## Verification and Reproduction

The final focused regression run passes **108 tests in ten classes**, with zero
failures, errors or skips and `-XX:+DisableExplicitGC`. Counts are 14 new ownership,
18 resource review, 7 properties lifecycle, 17 explicit resilience, 2 effective
resilience, 5 subscription-local reporting, 14 streaming upload, 13 streaming
response, 5 protocol-aware pool and 13 housekeeping cases. Benchmark builds pass
75 each, including cache/refresh/work sentinels. These counts are separate runs,
not a combined suite. Python input/result and review guards pass **14 tests**;
the documentation/archive/readiness guard passes **82 tests**, also with zero
failures, errors or skips. Whitespace and unchanged production/packaging checks pass.

The initial 14-case ownership run has one fixture-cleanup failure, retained under
`tests-ownership/`; `tests-ownership-fixed/` passes. The final regression reports
are under `tests-regression-final/`: the first 108-case run's collector also
copied one unrelated stale XML by substring, so exact-name collection was fixed
and all ten classes rerun. Seal-audit failures and original reports remain
preserved, not counted as extra tests. No failed run is replaced. No new native,
AOT compilation, full-reactor/API or physical
absence run is claimed: production/packaging did not change, and reused P1/P2
evidence is explicitly bounded above.

For a new experiment, use the [matched-build commands](WORKLOAD-CONTRACT.md#reproduction)
with fresh output/repository paths, then the scored command there twice per
artifact: selection `.*(V34DefaultPathBenchmark|V34ConstructionBenchmark).*`
into `v34.json`, and `.*(V33PlanningCostBenchmark.metadataCold|StarterInvocationInternalsBenchmark).*`
into `helpers.json`. Run baseline then current, with no simultaneous build or
profiler. Capture all environment/provenance before and after, not only JMH output.
The commands below use this record's saved directory names; choose a new output
directory when repeating analysis (the guard refuses overwrites):

```bash
base=target/release-evidence/v34/priority3
python3 scripts/review-v34-benchmark-results.py \
  --baseline "$base/scored-baseline/v34.json" "$base/scored-baseline/helpers.json" \
  --current "$base/scored-current/v34.json" "$base/scored-current/helpers.json" \
  --output "$base/review-new"
```

For each flag use that same scored command/JAR/flags, exact fully-qualified
benchmark name and parameters, **current then baseline**, in fresh paths. Here
both flags are `V34DefaultPathBenchmark.defaultV34NoNetworkWarmPublisher`,
`-p scenario=GET`, with `-p profile=AUTO_REGISTRY` then
`-p profile=RESILIENCE_ENABLED_ONLY`. Apply the same analysis using
`--confirmation`, both `row-0.json`/`row-1.json` inputs and a new output directory.
Do not change scoring/warmup/heap/CPU settings to clear a flag.

For independent attribution use the same current saved JAR and scored settings
except `-f 1 -prof 'jfr:dir=<fresh-output>;stackDepth=128;verbose=true'`, with one
exact row/parameter set per invocation. Rows are P02 warm GET/TARGET publisher,
P02/P04/P03 warm GET subscription, P02 loopback GET and P02 context/proxy.
Export with `jfr summary <recording>` and `jfr print --json --events
jdk.ObjectAllocationSample,jdk.ExecutionSample,jdk.NativeMethodSample <recording>`.
Aggregate only event type, allocated class, method/line, count/weight and thread
role; no request data. Do not promote the resulting timing JSON to scored input.

```bash
export MAVEN_OPTS='-Xmx512m -XX:ActiveProcessorCount=2'
mvn -B -ntp -s .mvn/maven-central-settings.xml -pl reactive-http-client-starter \
  -Dtest=DefaultPathCostOwnershipTest,ResourceOwnershipReviewTest,PropertiesBindingLifecycleTest,ExplicitResilienceActivationContractTest,EffectiveResiliencePolicyTest,SubscriptionLocalReportingStateTest,StreamingUploadOwnershipTest,StreamingResponseTest,ProtocolAwarePoolCapacityContractTest,HousekeepingTest \
  -DargLine='-XX:+DisableExplicitGC -XX:ActiveProcessorCount=2' test
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s scripts -p 'test_*v34*.py' -v
mvn -B -ntp -s .mvn/maven-central-settings.xml -pl reactive-http-client-starter \
  -Dtest=DocumentationReleaseArtifactTest -DargLine='-XX:+DisableExplicitGC -XX:ActiveProcessorCount=2' test
```

### Integrity Anchors

Raw evidence is local, ignored output, not a substitute for this tracked record.
The bundle retains exact argument arrays/exit statuses/timestamps, clean-build
inputs, later test-only dirty patch, reports, JSON/Markdown/environment sidecars,
JFR and aggregates, class hashes, first and reversed pairs, and failed attempts.
The final `SHA256SUMS` inventory is sealed after verification; its digest is
recorded in the checklist. Both scored builds remain immutable after later tests.

| Artifact | SHA-256 |
|---|---|
| Central starter 4.4.2 | `fb8646ce2f6ed172598ff446cdfa9da2f348c0fd56a2b4c9a02e7ac1dae803c1` |
| Current starter | `b95716a50f2818e22d01af3d213fe70a4794b62348cd097f55c7ecb71a436d89` |
| Baseline scored shaded JAR | `205f65aab4d6d676a2de00fdd15a074a391363264b952318b58fd6f9e1ee8d68` |
| Current scored shaded JAR | `a3afec65700216e52d4b6b5cb23c58109ce6fbaee1d3fae9a88ff9bc7cbe5433` |
