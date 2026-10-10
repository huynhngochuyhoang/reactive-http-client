# V35 Construction and Lifecycle Cost

> **Plan frozen:** 2026-10-10, before profiling or new regression execution
> **Workstream:** V34-C005; Priority 8
> **Authorization:** Priority 3.3 evidence-first/no-change route, not a framework rewrite
> **Release scope:** unselected

Starting clean commit: `7b2f133ae35d2b3217a87e1a04806ac473a5d1b8`.
Follow [Priority 8](CHECKLIST.md) and the [C005 decision](FIX-DECISION.md#v34-c005).
No production candidate is selected. A new defect requires a smaller reproduction
and explicit approval before changing selection/binding behavior.

## Frozen Evidence Plan

Reuse all six construction/first-use and 24 warm GET/TARGET rows from both
P7 **baseline** stages: 30 rows in each order, 120 previously scored forks.
Do not reuse either rejected candidate. Reconcile unchanged production source,
39 harness files, 120 dependencies, ordered classpath and all executable class
bytes with a fresh build before accepting the reuse. Preserve the P7 inventory,
raw data, commands, host observations and intervals. These are reused measurements,
not fresh scores, candidate benefit or full 60-row acceptance. Construction
invocation setup/teardown remains in GC totals; first-use latency excludes setup.

Run **eight fresh diagnostic JFR forks** on that baseline: context/proxy,
first publisher and first call for AUTO_NO_REGISTRY/AUTO_REGISTRY (six), plus warm
GET subscription for those two profiles. Unchanged JMH 1.37/Java 21.0.8 harness;
one thread, five one-second warmup/measurement iterations, one fork per selection,
GC profiler, stack depth 128, 512 MiB fixed heap, two active processors, WARN logs.
No competing agent build/test. Refuse launch below 2 GiB available memory and
bound each stage to three minutes. Do not select favorable reruns or use profiled
latency as scores. Preserve failures and missing samples as limitations.

Export only `jdk.ObjectAllocationSample` events; private originals stay outside
the evidence bundle with private permissions. Summarize allocated classes and
nearest starter production versus framework/JDK/fixture stack sites. Samples and
weights are attribution clues, not exact object counts, retained bytes or a
subtraction from whole-context B/op. The fixture includes Spring binding,
reflection, codec setup, transport/pool ownership and test harness lifecycle.
AOT analysis is not executed by this runtime benchmark and has no timing claim.

## Frozen Verification and Decisions

Add deterministic witnesses for normal/AOT context recreation, overlapping owner
isolation, partial refresh failure, repeated AOT selection, and same-named client
types from distinct class loaders. Retain scoped/prototype/FactoryBean properties,
identity/name memoization, awareness/binding/init ordering and processor restoration
through the existing selection suites. Count history and callbacks with live
objects; do not depend on System.gc or infer collection from cleared state.

Run lifecycle, selection, hint, cross-path, default-path, resource-failure and
non-instantiating diagnostics suites with explicit GC disabled. Keep the public
replacement and unknown-state contracts. No production change means new native
compilation and controlled reachability are N/A here, not newly passed; final
assembled/native and combined checks remain Priority 9 gates.

Evaluate local alternatives explicitly: removing history, stopping it after one
AOT lookup, reusing temporary binding state globally, skipping validation, or
moving construction work into subscription. Reject alternatives that invalidate
supported behavior or ownership; no speculative implementation to measure.
Retain current code only with fresh attribution and lifecycle evidence, not just
the absence of a warm leak. Any unexplained defect remains open and named.
Append results without changing this frozen prefix.

## Attribution Results

Recorded 2026-10-10. All eight diagnostic forks completed without retries.
The exports contain **11,989 allocation samples**: 8,869 from the six construction/
first-use forks and 3,120 from warm subscriptions. The initial JSON export used
`jfr print`'s five-frame default; it is retained. `export-full.py` re-exports the
same scrubbed recordings with `--stack-depth 128` into `allocations-full.json`.
Event counts, allocated classes and weights match the original export. This is
an export correction, not another measurement or favorable-fork selection.

| Diagnostic phase | AUTO_NO_REGISTRY samples / visible starter frame | AUTO_REGISTRY samples / visible starter frame |
| --- | --- | --- |
| Context/proxy | 1,510 / 241 | 1,534 / 228 |
| First publisher, including invocation fixture | 1,525 / 240 | 1,470 / 217 |
| First call, including invocation fixture | 1,401 / 227 | 1,429 / 224 |
| Warm GET subscription | 1,560 / 1,157 | 1,560 / 1,263 |

A visible starter frame means the nearest production frame in the captured stack;
it does not mean the allocated object is starter-owned. No visible frame is not
proof that starter code did not cause the work. Cold stacks reach the 128-frame
limit. Counts/weights are not allocation percentages, retained heap or precise
removable bytes. Sampling may omit small/infrequent work entirely.

Observed cold sites include `RequestPlan.namedBindings` stream/spliterator objects,
`RequestPlan.resolvedType` generic-resolution sets, method/parameter reflection,
URI grammar validation, metadata parsing and `ReactiveHttpClientFactoryBean.buildWebClient`.
The six cold forks contain respectively 139/130/140/135/132/131 samples with a
RequestPlan frame, and 149/141/145/139/138/134 with a MethodMetadataCache frame;
these sets overlap. They establish construction/validation work, not its redundancy.

Other visible stacks include Spring annotation mapping and reference-cache tasks,
JDK Method copies, Boot configuration-property name parsing, ASM class-resource
reads and Jackson/framework setup. The fixture creates and destroys an entire
context per operation, not just a proxy. First-publisher/call JFR and GC totals
also contain their invocation-level context setup/teardown outside latency timing.
Their apparent megabytes are not a per-request AOT tracker allocation.

Warm samples instead show URI/request builders, headers, final-request/framing
filters and response decoding. The registry profile additionally samples
`SubscriptionReportingState.beginAttempt`, observer event construction and
Micrometer tag/record work. Neither warm recording samples RequestPlan or
MethodMetadataCache work. No recording samples `PropertiesBindingLifecycle` or
the starter initialization AOT processor; sampling alone cannot establish zero
cost. Source/lifecycle tests below establish the actual stop boundary. The
benchmark never invokes AOT analysis, so it does not price AOT binding or hints.

## Reused Cost Evidence

The unchanged P7 baseline's two orders are retained under `reused/`, with all
**30 rows per order / 120 previously scored forks**, raw samples and intervals.
No new score is run or claimed. Neither P7 rejected candidate is used. Representative
means below retain both orders; cold GC totals include fixture lifecycle allocation.

| Phase/profile | Forward latency | Reverse latency | Forward / reverse B/op |
| --- | --- | --- | --- |
| Context/proxy, AUTO_NO_REGISTRY | 6.251 ms | 5.966 ms | 2,988,389 / 3,005,861 |
| Context/proxy, AUTO_REGISTRY | 6.726 ms | 6.863 ms | 3,221,249 / 3,222,647 |
| First publisher, AUTO_NO_REGISTRY | 63.754 us | 64.750 us | 3,001,222 / 3,127,127 |
| First publisher, AUTO_REGISTRY | 75.960 us | 78.256 us | 3,204,421 / 3,206,472 |
| First call, AUTO_NO_REGISTRY | 471.707 us | 478.936 us | 3,021,932 / 2,979,620 |
| First call, AUTO_REGISTRY | 691.356 us | 680.888 us | 3,313,117 / 3,241,581 |
| Warm GET publisher, AUTO_NO_REGISTRY | 319.9 ns | 302.9 ns | 960 / 832 |
| Warm GET publisher, AUTO_REGISTRY | 466.7 ns | 470.4 ns | 1,744 / 1,744 |
| Warm GET subscription, AUTO_NO_REGISTRY | 5,339.0 ns | 5,146.6 ns | 13,704 / 13,588 |
| Warm GET subscription, AUTO_REGISTRY | 8,537.0 ns | 8,676.7 ns | 18,000 / 17,984 |

For example, forward context/proxy intervals are 6.251 +/- 0.984 ms and
6.726 +/- 1.081 ms; they do not isolate a tiny local initialization change.
AUTO_NO_REGISTRY publisher forks retain the known 832/1,088 allocation modes;
no mode subtraction or regression-threshold waiver is made. Full TARGET, MINIMAL,
enabled-only, observer and hook rows remain in the reused record, not just this
table. No sum/subtraction of unlike phases establishes saved work. A proposed
deferral into first use would need a newly frozen matched candidate comparison;
none is selected and no work has been moved.

## Ownership and Alternatives

| Boundary | Required work and lifetime | Rejected alternative and witness |
| --- | --- | --- |
| Configuration and metadata | Application-selected beans, parent preference and supported replacements; metadata keyed by reflective Method within its cache owner | A global resolved-properties/plan cache mixes bean names, contexts, concrete generic types or class loaders. Fresh metadata and same-named isolated-client witnesses require existing owners |
| Normal lifecycle history | Observe properties creation before singleton completion; then `tracking=false`, clear and trim observations. Before/after callbacks keep only the disabled branch at later bean initialization, not a per-HTTP-call tracker | Removing the observer loses early AOT scoped/prototype creation provenance. Normal runtime tests retain 100 live prototype/scoped values while history stays empty |
| AOT lifecycle history | Weak bean/delegate references, canonical bean name, owning factory. Remains available until context destruction | Stopping after the first AOT lookup loses binding provenance needed by later processors. Repeated selection must not rebind a singleton, while new prototypes may still need binding |
| Temporary binding adapter | Per-analysis, per-factory maps keyed by name and identity; local strong references while selection runs | Global reuse or identity-only memoization changes distinct-prefix/shared-object and wrapper behavior. The existing 243-case selection suite covers these contracts |
| Processor ordering | Install/reposition the registered binder at its runtime boundary; restore in `finally` while retaining application removals/replacements/re-additions | Skipping restoration or replaying the old entire chain resurrects processors or undoes intentional moves. Success and failure tests retain the exact supported ordering |
| Startup validation and plans | Parse/validate declared parameters, return type, URI, cache policy/customizer safety, concrete generic types and effective selection before client use | Skipping validation or moving it to subscription changes fail-fast guarantees; blindly sharing derived plans bypasses fresh metadata and client-specific inputs. P6 already rejects its bounded static-reuse alternative |
| Optional resources and close | Selected manager/pool/meter acquisition remains tied to its factory; application resources remain external | Lazy creation cannot hide selected startup errors. Failure/recreation suites exercise early/late failure, same-tag owners and externally owned connectors |
| Diagnostics | Read existing bounded facts, leave unavailable facts unknown, do not create lazy providers/factories | Populating missing facts through getBean changes ownership and startup timing. Provider/factory diagnostics tests preserve unknown-state and non-materialization behavior |

AOT history is **lifetime-bounded, not fixed-cardinality**: it can grow with live
properties instances/names during analysis and uses linear observation scans.
Cleared weak referents are pruned; destruction clears all records. No arbitrary
cap or early stop is safe without changing provenance semantics. No normal-runtime
history growth is reproduced; AOT throughput under unbounded application-generated
prototype churn is not characterized or advertised as constant cost.

There is no static history map. Keeping an application context, lifecycle bean or
custom MethodMetadataCache externally alive can retain its owner/type graph; close
is not proof of class unloading. The new class-loader test checks semantic and
owner isolation while both loaders remain live, not their garbage collection.

## Regression Evidence

Fresh contract verification passes **559 cases across 13 classes**, zero failures,
errors or skips, with `-XX:+DisableExplicitGC`:

- `ConstructionLifecycleContractTest` (7): repeated normal/AOT context recreation,
  overlapping AOT owners, failed refresh before singleton completion, 20 repeated
  AOT selections without duplicate binding/history growth, same-named clients in
  distinct class loaders, and concurrent callbacks versus tracker destruction.
- `PropertiesBindingLifecycleTest` (7): live prototype/custom-scope instances,
  weak-reference shape and deterministic pruning, alias/name identity and wrappers.
- `AotPropertiesSelectionContractTest` (243), `AotMetadataSelectionContractTest`
  (7), `EffectiveSelectionAotReviewTest` (4), `ReactiveHttpClientAotSmokeTest` (27):
  preference, parent/scoped/FactoryBean selection, awareness/binding/init order,
  unknown-product fail-fast, non-eager unrelated resources, failure propagation,
  processor restoration/replacement and hints. These are JVM tests, not a native build.
- `ResourceOwnershipReviewTest` (20), `DefaultPathCostOwnershipTest` (42),
  `V33CrossPathContractTest` (2), `PublicStaticMetadataContractTest` (25),
  `ReactiveClientInvocationHandlerBehaviorTest` (34): construction/close/failure,
  external owners, coldness, public extensions and actual request semantics.
- `ReactiveHttpClientDiagnosticsProviderTest` (61),
  `ReactiveHttpClientFactoryBeanDiagnosticsTest` (80): lazy resources remain lazy,
  supported unknown facts and bounded existing-owner diagnostics.

The first focused run failed compilation on two fixture API names; its log is
retained. The corrected run passes 14 cases (seven new plus seven existing), then
the complete 559-case run passes. These overlap and are not additive coverage.
No test claims collection or uses System.gc as its success condition.

## C005 Decision

**Resolved without production change**, not a performance pass. No production
patch was selected, so there is no candidate to roll back. Fresh sampled sites,
the owner inventory, failure/recreation/isolation witnesses and existing extension
contracts support retaining construction/validation and factory-local AOT history.
This is not another deferral based only on V34's warm-leak absence or a noisy score.
The alternatives above would change supported behavior or merely move cost; an
unidentified global cache or binding rewrite is not authorized.

Limitations remain explicit: samples are incomplete; no exact per-site B/op,
AOT timing, heap/RSS reduction or class unloading is established. A reproduced
new lifecycle defect or a narrower attributed optimization needs its own approval
and matched experiment. No new API/default/dependency/version change is selected.
All seven finding rows now have evidence-backed dispositions; final combined,
assembled/native, compatibility, performance and release gates remain unchecked.

## Reproduction and Provenance

Evidence: `target/release-evidence/v35/priority8/`; starting tree
`4340ca2140be646d20e70926903c6a19f17c80aa`. The saved P7 baseline and its sealed
inventory are prerequisites; a clean clone must reproduce that baseline first.
Use a new evidence directory; preserve failed stages rather than overwriting them.

```bash
OUT="$PWD/target/release-evidence/v35/priority8"
python3 -B scripts/investigate-v35-lifecycle.py freeze --output "$OUT"
python3 -B scripts/investigate-v35-lifecycle.py attribute --output "$OUT"
python3 -B scripts/investigate-v35-lifecycle.py summarize --output "$OUT"
# For each scrubbed recording, preserve a full-depth export alongside allocations.json:
/usr/lib/jvm/jdk-21.0.8-oracle-x64/bin/jfr print --json --stack-depth 128 \
  --events jdk.ObjectAllocationSample "$RECORDING" > "$STAGE/allocations-full.json"
python3 -B -m unittest discover -s scripts -p 'test_*v3[45]*.py'
```

`export-full.py` and `full-export-commands.json` in the bundle contain the exact
eight export commands and unchanged-event reconciliation. Private original JFRs
are outside the bundle; scrubbed recordings contain only allocation events.
`verify.py` records exact Maven argv/environment, exits, timestamps and fresh XML.
The scored baseline SHA-256 is
`f30482de11e8ccefa8980beb29f4074a9de011683cb8cee37d0a84925b8239db`.
The frozen plan and runner, both export depths, raw reused scores, source and
artifact reconciliation are retained. Tests/builds start after diagnostic forks.

```bash
export JAVA_HOME=/usr/lib/jvm/jdk-21.0.8-oracle-x64
export PATH="$JAVA_HOME/bin:$PATH"
TESTS=ConstructionLifecycleContractTest,PropertiesBindingLifecycleTest,AotPropertiesSelectionContractTest,AotMetadataSelectionContractTest,EffectiveSelectionAotReviewTest,ReactiveHttpClientAotSmokeTest,V33CrossPathContractTest,DefaultPathCostOwnershipTest,ResourceOwnershipReviewTest,ReactiveHttpClientDiagnosticsProviderTest,ReactiveHttpClientFactoryBeanDiagnosticsTest,PublicStaticMetadataContractTest,ReactiveClientInvocationHandlerBehaviorTest
mvn -B -ntp -s .mvn/maven-central-settings.xml -pl reactive-http-client-starter -am \
  -Dsurefire.failIfNoSpecifiedTests=false \
  '-DargLine=-XX:+DisableExplicitGC -XX:ActiveProcessorCount=2' -Dtest="$TESTS" test
git diff --check
```

Native compilation, genuine assembled Boot rows and controlled reachability are
not freshly run in P8. No production/native fixture changes require an intermediate
native rerun; final native provenance is still an explicit Priority 9 gate.
Unchanged earlier evidence is not renamed as fresh execution.

## Final Verification

The fresh starter/benchmark rebuild matches **310 starter classes** and **24,224
shaded benchmark classes** in the saved baseline byte-for-byte. All **39 harness
files**, **120 non-starter dependencies** and ordered classpaths match; production
and harness source are unchanged since P7. This reconciles score reuse, not a
whole-JAR packaging-hash claim. The rebuild passes **42 benchmark-contract cases**.

All **40 Python checks** pass, including the two new diagnostic selection and
attribution-summary guards. **101 documentation cases** pass. Together with the
559-case affected suite this is **660 functional/documentation cases**, plus the
separate 42 benchmark-contract cases and 40 Python checks. Focused/repeated runs
overlap and are not added. The initial compile failure remains recorded.

`final-checks/` retains artifact/source identity and command records; `final/`
captures the closing source/readiness audit and reverified prior inventories.
The checklist's external inventory anchor follows the sealed source copies to
avoid a self-referential hash. Active V35, development `4.5.0-SNAPSHOT`, published
and API baseline `4.4.2`, null planned-final version and unselected release scope
are unchanged. All finding investigations have dispositions; Priorities 9-12
are still pending, including final acceptance of the combined no-change outcome.
