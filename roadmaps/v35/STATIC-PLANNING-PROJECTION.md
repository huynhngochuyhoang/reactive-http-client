# V35 Static Planning and Dynamic Request Projection

> **Plan frozen:** 2026-10-10, before candidate edits or scoring
> **Workstream:** V34-C003; Priority 6
> **Authorization:** Priority 3.3 interface-annotation reuse only
> **Release scope:** unselected

Starting clean source: `1f016a8000bb835fff8ae8a509b12f1194a6215d`.
Execute [Priority 6](CHECKLIST.md) under [FIX-DECISION.md](FIX-DECISION.md#v34-c003).
C001/C002 remain rolled back. No collection-copy, metadata, URI or serialization
optimization is authorized by this experiment.

## Frozen Candidate

Lazily retain only the handler's concrete interface `LogHttpExchange` annotation
or its known absence. Reuse only for a JDK proxy exposing exactly that one
interface, and only when the invoked declaring interface is assignable from it.
Keep original traversal for legacy/null interface, null/non-proxy objects,
different/multiple interfaces and the declaring-interface fallback. Do not cache
the proxy class, method fallback, request arguments, context or resolved logger.

Use a handler-local annotation reference and volatile publication flag. Benign
concurrent first lookups may repeat immutable reflection; no lock or map. Preserve
live client logging configuration, method-metadata precedence, late logger bean
discovery, construction/failure timing and the existing bounded logger owner.
The `getInterfaces()` array and verification checks remain per invocation; this
candidate removes only annotation lookup, not all reflection or request planning.

## Frozen Measurement and Stop Rules

Reuse the saved P5 baseline artifact, reconciling its 310 starter classes with
the clean source build. Match 39 harness files, 120 non-starter dependencies,
ordered classpath and shaded entries; only handler implementation may change.
Record hashes, commands/exits, dirty candidate patch, host observations and raw
samples. No synthetic timing engine or new benchmark workload.

Score **35 matched rows**, two forks per artifact in both orders (**280 forks**):

- 24 GET/TARGET warm publisher/subscription rows across all six V34 profiles.
- `StarterDiagnosticsOverheadBenchmark.metadataOnlyExchangeLoggingGetNoBody`.
- Both `V33PlanningCostBenchmark.metadataCold*` helpers and all four
  `StarterInvocationInternalsBenchmark` helpers, kept distinct from call costs.
- First publisher and first call for AUTO_NO_REGISTRY/AUTO_REGISTRY using
  `V34ConstructionBenchmark`. Invocation setup is outside latency timing but
  inside GC-profiler allocation totals; do not attribute those totals to lookup.

Order: baseline warm/controls/first, candidate warm/controls/first, then candidate
warm/controls/first and baseline warm/controls/first. Java 21.0.8/JMH 1.37,
avgt/ns, one thread, five one-second warmups and measures, GC profiler, fixed
512 MiB heap, ActiveProcessorCount=2, WARN logging. Bound each stage to 25 minutes;
refuse launch below 2 GiB available RAM and run no competing agent builds/tests.
Host snapshots do not establish dedicated hardware. Two separate baseline JFR
GET warm-publisher forks (MINIMAL, AUTO_REGISTRY; stack depth 128) sample allocation
sites before candidate edits; export only allocation events, not private originals.

One candidate, no refinement/result-selected rerun. Keep >20% latency and
>max(32 B/op, 5%) allocation review triggers, both orders, intervals and every
fork. Require repeatable attributed production-path benefit, unchanged semantics
and no unexplained adverse control. An invariant source expression is not enough.
No P2 compiler-mode subtraction or waiver. Roll back on changed behavior, growing
retention, no stable benefit or unexplained controls; document the approved
no-change route after completing the planning/projection audit. Incomplete work
remains incomplete. No full 60-row, API/native/consumer or RSS/heap claim.

## Verification Plan

Add precedence/fallback, concurrent first use, lazy logger and failure witnesses;
retain fresh/replacement metadata, inherited generic and API-ref controls. Verify
dynamic argument mutation boundaries, query/header ordering and case aliases,
null/empty body presence, encoding and custom codec behavior across public,
factory and mock paths. Existing wire suites supply real transport controls.
Ordinary tests disable explicit GC; no collection inference from local references.

Record exact commands and XML counts, not annotation counts. Preserve baseline,
candidate and any rollback results separately. Check production and historical
evidence scope, prior inventories and dependency/harness identity before closure.
Append results below without changing this frozen prefix.

## Reproduction

Use the baseline Java/Maven environment and a new evidence directory. The saved
P5 baseline bundle is a prerequisite, not a tracked artifact in a clean clone.

```bash
OUT="$PWD/target/release-evidence/v35/priority6"
python3 -B scripts/investigate-v35-planning.py attribution --output "$OUT"
# Run baseline controls; apply/build the bounded candidate and record its inputs.
python3 -B scripts/investigate-v35-planning.py score --output "$OUT"
python3 -B scripts/investigate-v35-planning.py review --output "$OUT"
```

## Static and Dynamic Ownership Review

| Boundary | Owner and invariant | Why the remaining work stays |
| --- | --- | --- |
| `MethodMetadataCache.get(Method)` | Shared cache keyed by reflective Method; public replacements can supply fresh metadata | Invocation still asks the selected extension; no cached provider/negative selection or new public metadata slot |
| `requestPlan()` | Concrete handler caches a plan by Method, resolving inherited generic types against its concrete client. Legacy handler uses metadata's supplied plan or derives one | Reusing a parent Method's concrete plan across clients would mix String/Integer or body types. Mutation after plan consumption remains unsupported |
| `RequestPlan.from()` | Immutable binding lists sorted by argument index, copied map/set membership, reflective type/annotation references only | Cold validation, supplied derived API, missing static API derivation and API-ref precedence remain. No request values or context in a plan |
| Effective API | Static metadata uses its existing derived API. API-ref resolution reads the current client's named API config on invocation | Freezing a resolved API-ref would hide supported configuration/projection changes. No new parsed-target model selected |
| Interface logging | Actual proxy interface order, assignability and declaring fallback; method metadata wins, client logging is live | Candidate caches only a single concrete interface annotation/absence. Multi/different/direct/legacy shapes keep traversal. Even a cached absence does not remove the declaring fallback |
| Logger ownership | Existing bounded per-handler class-to-logger map; method logger slot remains on metadata | Bean lookup/constructor failures happen on invocation. No startup logger creation or new retention beyond this existing contract |
| Path/query resolution | New invocation maps; path values remain references; collection/array query membership is copied in order | Query elements may still be mutable. Removing copies would alias outer membership, while deep copies would change ordinary invocation semantics |
| Headers | Values converted/validated at invocation, duplicates rejected case-insensitively, lists copied and case-insensitive index derived | Snapshot and lookup coherence require these boundaries. Default/method precedence stays case-insensitive; no new cross-name ordering guarantee from Map.copyOf |
| Default query/header merge | Invocation-local merge, preserving dynamic parameter precedence and per-value order | Caching a resolved map would retain caller values or ignore live defaults |
| URI materialization | Subscription/attempt builds through the actual WebClient builder, declarative template and current resolved values | Encoding, query replacement, configured authority and final filter/auth mutations cannot be replaced with a prebuilt request URI |
| Body/charset/auth | Original body presence and codec paths, auth-visible serialization and repeated subscription ownership unchanged | No pre-serialized body cache, new freeze, removed holder or inferred immutable DTO |
| Context/idempotency | Subscription-local conventional/generated keys and auth inputs; selected-cache snapshot rules remain separate | Ordinary parameter planning cannot cache Reactor context, tenant identity or generated request state |

The candidate adds one annotation reference and one publication flag per handler;
no per-method/proxy growth, proxy Class key, arguments, request/response or context
is added to its state. Annotation absence is published lazily and concurrent
first calls may repeat immutable reflection. This is a bounded lifetime source
argument, not proof of heap collection or deployment RSS reduction. Neither field
may justify skipping logger resolution, client configuration or method precedence.
No GC-dependent ordinary test or controlled-reachability claim is introduced.

## Results and Disposition

Recorded 2026-10-10. **Resolved without production change:** the single bounded
candidate was rejected and rolled back, with no refinement. This is not a
performance pass. The [rejected patch](c003-rejected-candidate.patch) is evidence,
not applied production code. Three implementation workstreams remain open.

All **280 scored forks** completed: 35 matched rows per artifact in both orders.
Forward review has **two latency flags and one allocation flag**; reverse has
**zero latency flags and zero allocation flags**. Neither order replaces the other.
The >20% latency and >max(32 B/op, 5%) triggers remain unchanged, not regression
allowances. Representative baseline -> candidate means (ns/op; rounded):

| Phase/profile/scenario | Forward ns/op | Reverse ns/op | Allocation interpretation |
| --- | --- | --- | --- |
| Warm publisher MINIMAL/GET | 260.5 -> 288.3 | 268.2 -> 285.2 | 832 -> 856 B/op in every fork, both orders |
| Warm publisher MINIMAL/TARGET | 552.8 -> 615.4 | 581.5 -> 597.3 | 1,992 -> 2,016 B/op in every fork, both orders |
| Warm publisher AUTO_REGISTRY/GET | 462.3 -> 498.2 | 472.7 -> 492.9 | 1,744 -> 1,768 B/op in every fork, both orders |
| Warm publisher HOOK/TARGET | 911.8 -> 1,452.9 | 890.3 -> 909.5 | Forward latency flag; 2,872 -> 2,896 B/op both orders |
| Warm publisher OBSERVER/GET | 488.3 -> 544.9 | 495.9 -> 493.8 | Forward allocation flag; baseline forks 1,744/2,000, candidate 2,024/2,024 |
| Warm subscription RESILIENCE_ENABLED_ONLY/TARGET | 9,552.5 -> 12,940.1 | 9,820.3 -> 9,412.1 | Forward latency flag, not an isolated annotation-cost measurement |
| Metadata-only logging, no-network subscribed call | 9,215.3 -> 9,327.4 | 9,164.6 -> 9,775.7 | 18,588 -> 18,204 then 18,440 -> 18,416 B/op; no stable timing benefit |
| First publisher AUTO_NO_REGISTRY | 66,127.0 -> 68,835.7 | 66,687.5 -> 70,104.8 | GC totals include invocation-level fixture setup/teardown |
| First call AUTO_NO_REGISTRY | 480,673.4 -> 485,879.2 | 476,443.5 -> 483,826.3 | Not comparable with warm assembly or isolated plan lookup |

Forward HOOK/TARGET publisher intervals are 911.8 +/- 42.7 versus
1,452.9 +/- 563.0 ns/op; enabled-only TARGET subscription is 9,552.5 +/- 469.1
versus 12,940.1 +/- 6,111.5. Wide intervals and shared-host conditions prevent a
universal causal latency claim; they do not waive the flags. The complete report
retains all intervals and both forks, including apparent improvements that reverse
direction: AUTO_NO_REGISTRY/TARGET publisher improves 37.9% forward but worsens
1.6% reverse. AUTO_NO_REGISTRY/GET baseline allocation itself shifts from 832 to
1,088 B/op between orders. No candidate compiler trace establishes why; no P2
256-byte subtraction or favorable-mode selection is applied.

The six helper rows have no review flags in either order. Both argument-resolution
helpers remain about 1,288 B/op; cached metadata/plan lookup about 16 B/op; cold
concrete planning about 5,480 B/op. Cold parse-and-plan is about 14,440-14,488 B/op.
These helpers retain required static and projection work and do not price removable
whole-call work. No loopback performance row was selected for this bounded plan;
real-wire tests below establish correctness, not transport timing or RSS benefit.

Two separate baseline diagnostic forks sample `jdk.ObjectAllocationSample` with
stack depth 128. MINIMAL has nine and AUTO_REGISTRY fifteen samples of the
`Class[]` returned through `Class.getInterfaces()` and
`resolveInterfaceLevelLogAnnotation()`. The candidate deliberately retains that
array/verification path. The samples do not establish annotation-lookup allocation
or exact B/op, and no candidate JFR was selected. Repeated +24 B/op warm-publisher
results and absent repeatable attributed benefit fail the candidate's gate even
where they fall below a review trigger. Its extra checks/state are not justified.

The no-change route is supported by the static/dynamic ownership inventory,
extension and mutation regressions, sampled remaining work and this rejected local
alternative, not merely a noisy score. Keep reflection on variable proxy paths;
keep concrete plans and required per-invocation projections. Do not broaden the
experiment into copy removal, cached public metadata or prebuilt request targets.
Another optimization requires separately approved scope and measurement.

## Regression Evidence

Initial baseline and candidate runs each passed **215 starter cases in 14 classes
and 65 mock cases**, with explicit GC disabled. Two additional regression cases
then covered cached annotation absence with declaring-interface fallback, and
fresh method metadata overriding an already warm interface decision. The expanded
candidate rerun passed **217 starter cases and 65 mock cases**. Runs overlap; these
are not additive distinct-test totals.

`InterfaceLoggingPlanningContractTest` now has 15 cases: warm concrete, legacy,
null/non-proxy, different/multiple interface and declaring fallback paths;
method/client precedence; lazy logger selection and recovery after construction
failure; gated simultaneous first use; shallow argument projection; query order
and case-alias rejection. Requests assert actual method, path, result and logger.

Existing, freshly executed witnesses:

- `PublicStaticMetadataContractTest` (25), `MethodMetadataValidationTest` (23):
  fresh metadata, invalid shape, inherited Mono/Flux generic types, concrete plan
  identity, immutable bindings, supplied API values and API-ref precedence.
- `V33CrossPathContractTest` (2): custom metadata/auth/context/cache policy across
  public and factory paths, final tenant headers and actual loopback result.
- `DeclarativeRequestUriTest` (4), `DeclarativeRequestTargetWireContractTest` (6):
  exact encoded targets over HTTP/1.1, h2c, TLS and HTTP proxy, auth query replacement,
  redirects and final-request observation.
- `ReactiveClientInvocationHandlerBehaviorTest` (34), `HeaderParamMapSupportTest`
  (8): default/dynamic query/header precedence, case aliases, ordered multi-values,
  null/empty wire shape, body presence, String/byte/JSON auth bytes and charset.
- `HousekeepingTest` (13), `ExchangeLogSubscriptionAttemptCountTest` (3),
  `SubscriptionLocalReportingStateTest` (5): logger ownership, final headers,
  serialization failure, retry terminal counts and independent subscribers.
- `DefaultPathCostOwnershipTest` (42), `IdempotencyKeySupportTest` (13),
  `StreamingUploadOwnershipTest` (24): coldness, fresh provider discovery,
  repeated/concurrent state, context/generated idempotency, body encoding,
  transport release and one-shot/replay ownership.
- `MockReactiveHttpClientTest` (65): interface/method loggers, fresh/replacement
  static metadata, inherited generics, API-ref target/auth precedence,
  query/header/body recording, cache/work/replay and terminal behavior.

An initial verification selection named a nonexistent suite. Maven passed its
181 discovered cases, but the evidence script rejected the missing report; the
complete corrected selection was rerun separately. The partial record is retained.
During scoring a resumed terminal's process namespace hid the original runner.
A mistaken duplicate launch was rejected by JMH's lock before any fork; the active
directory was restored before its stage completed. The original runner completed
all twelve stages with zero scored retries. Both the failed launch and correction
are retained; no completed sample was replaced or result-selected rerun performed.

## Artifact Provenance and Limits

Evidence: `target/release-evidence/v35/priority6/`. `source.json` records the starting
commit/tree and dirty test/plan files; `frozen-plan.md` is the unchanged prefix above.
`candidate/source.patch`, the saved handler and both binaries preserve the exact
experiment independently of the rollback. Baseline starter classes were reconciled
with the previously installed restored artifact, not freshly rebuilt before scoring.
The starting commit's production source is unchanged from that P5 build's source.

| Saved artifact | SHA-256 |
| --- | --- |
| Baseline shaded JAR | `f30482de11e8ccefa8980beb29f4074a9de011683cb8cee37d0a84925b8239db` |
| Candidate shaded JAR | `58e8bf38a6916e8160e9fdab1825c51fc8e8a8e4c08684e21579057d3a09b5b0` |

The pair matches **39 harness source files**, **120 non-starter dependencies**,
ordered classpath and shaded entry set. Twelve class entries change: the handler
and eleven nested classes; `javap -c -p` confirms all eleven nested declarations
and instructions match, despite changed class-file debug metadata. All other
entries match. Full commands, exits, raw samples, host observations and allocation
event exports are retained. Private original JFR recordings stay outside the bundle.

Local results are not a fresh Central download, full 60-row acceptance, strict API,
assembled-consumer, AOT/native or controlled-reachability rerun. No heap/RSS saving,
universal zero-overhead path or production speedup is established. Versions,
defaults, dependency scopes and release selection remain unchanged.

## Final Verification

After rollback, **316 starter cases across 15 classes** (including 99 documentation
cases) and **65 mock cases** passed: **381 total**, zero failures/errors/skips.
The 15 planning/logger cases pass on restored production, including the two added
after the initial baseline run. The benchmark rebuild passed **41 contract cases**
(40 V34 workload and one V33 planning); all **34 Python checks** passed. A fresh
restored starter build matches all **310 starter classes**, and its benchmark
rebuild matches all **24,224 shaded classes** in the saved baseline. JAR packaging
hashes differ; class-byte identity, not whole-JAR identity, is the restoration check.

The retained XML, exact argv/environment/time records and logs are under
`tests-final/` and `final-checks/`. Tests/builds ran outside scored stages.
Core reproduction commands (Java 21.0.8; the evidence records the local Maven
repository used):

```bash
TESTS=InterfaceLoggingPlanningContractTest,PublicStaticMetadataContractTest,MethodMetadataValidationTest,V33CrossPathContractTest,DeclarativeRequestUriTest,DeclarativeRequestTargetWireContractTest,HousekeepingTest,HeaderParamMapSupportTest,IdempotencyKeySupportTest,DefaultPathCostOwnershipTest,SubscriptionLocalReportingStateTest,ExchangeLogSubscriptionAttemptCountTest,ReactiveClientInvocationHandlerBehaviorTest,StreamingUploadOwnershipTest,DocumentationReleaseArtifactTest
mvn -B -ntp -s .mvn/maven-central-settings.xml -pl reactive-http-client-starter -am \
  -Dsurefire.failIfNoSpecifiedTests=false \
  '-DargLine=-XX:+DisableExplicitGC -XX:ActiveProcessorCount=2' -Dtest="$TESTS" test
mvn -B -ntp -s .mvn/maven-central-settings.xml -pl reactive-http-client-test -am \
  -Dsurefire.failIfNoSpecifiedTests=false \
  '-DargLine=-XX:+DisableExplicitGC -XX:ActiveProcessorCount=2' -Dtest=MockReactiveHttpClientTest test
python3 -B -m unittest discover -s scripts -p 'test_*v3[45]*.py'
git diff --check
git apply --check --unidiff-zero roadmaps/v35/c003-rejected-candidate.patch
```

`final-checks/commands.json` also records the restored starter install and benchmark
clean/package with `-Dtest=V34WorkloadContractTest,V33PlanningCostBenchmarkTest`.
The finding ledger and later-priority guard now leave only Priorities 7 onward
pending. Readiness retains active V35, `4.5.0-SNAPSHOT`, published/API baseline
`4.4.2` and unselected release scope. The checklist carries the evidence inventory
anchor outside the sealed source copies to avoid a self-referential checksum.
