# V34 Maintainer and Operations Guidance

> **Status:** Priority 11 complete, 2026-10-03
> **Published / development:** `4.4.2` / `4.5.0-SNAPSHOT`
> **Delivered production IDs:** none; C004 rolled back
> **Release scope:** unselected

Companion to [Priority 11](CHECKLIST.md). The maintainer requested this guidance
while **10.1 performance acceptance remains pending**. Documenting the completed
review is independent of clearing that gate: [P10](COMPATIBILITY-PERFORMANCE.md)
retains the enabled-only allocation split, passing compatibility checks and all
samples. P12 still owns release or review-only/no-release selection. Neither this
guide nor the development coordinate selects a release or a performance claim.

## Delivered Behavior and Deferred Work

[Effective profiles](BASELINE-SCOPE.md), [workloads](WORKLOAD-CONTRACT.md),
[findings](COST-OWNERSHIP.md), [explicit approval](IMPROVEMENT-DECISION.md) and
[rollback](HARDENING-EVIDENCE.md) are separate records. Only C004's immutable
disabled cache-policy value reuse was approved. Its benefit did not distinguish
itself from fork variance, so it was rolled back rather than expanded. V34 adds
regression and investigation evidence, **not a production optimization**. No new
API, SPI, property, default, dependency, meter or dashboard is delivered.

Published `4.4.2` remains the application baseline; the current
`4.5.0-SNAPSHOT` review retains its production behavior. V33's published
[extension corrections and workarounds](../v33/MAINTAINER-GUIDANCE.md) remain
version-scoped history, not new V34 fixes. No application migration is needed for
V34 at this checkpoint. Do not recommend a snapshot upgrade as a measured speed,
startup or pod-memory remedy.

All reopening decisions belong to the V34 maintainer and need renewed scope
approval. Component owners below must supply the indicated evidence; workarounds
must not disable required auth, audit, isolation, validation or cleanup.

| Finding / disposition | Required work retained | Owner, supported alternative and reopening trigger |
|---|---|---|
| C001: discovery retained; optimization deferred | Ordered observer/hook discovery per invocation supports late registrations, prototype materialization and hook support checks | Observer/hook maintainer. Use suitable application bean scopes without caching provider results globally. Reopen with isolated provider cost and equivalent dynamic-discovery behavior |
| C002: body/state optimization deferred | Independent subscription state, final-attempt/terminal freezing, cancellation/discard and one-shot body ownership | Invocation/body maintainer. Use replayable inputs when needed and release streaming buffers; do not pool callers or reuse consumed bodies. Reopen with a reproduced unnecessary cost and matching ownership tests |
| C003: planning optimization deferred | Public metadata, inherited/generic plans and dynamic wire argument projection | Planning/resolver maintainer. Reuse supported client proxies, not resolved requests. Reopen after isolating static derivation from variable request work while preserving exact wire behavior |
| C004: approved value reuse rolled back; broader resource work deferred | Full per-invocation cache-policy mutation checks, including sibling methods; current cache identity and factory lifecycles | Effective-policy/factory maintainer. Recreate and close owners for configuration changes. Reopen only with a repeatable benefit or resource defect, not by skipping validation or reviving the failed optimization |
| C005: framework optimization deferred | Startup validation, Spring/AOT selection/binding order, non-instantiating diagnostics and failed-construction cleanup | Factory/AOT maintainer. Use supported context lifetimes and predictable extension metadata. Reopen for an isolated lifecycle cost or retention defect; cold fixture allocation alone is insufficient |
| P3/P10 enabled-only allocation flag: unresolved | Original and reverse samples, intervals, artifact identity and failed benefit evidence | Performance maintainer. Keep both forks and use bounded separate profiling to test hypotheses; no favorable-rerun selection. Explanation or explicit scope disposition is required before 10.1 acceptance |

## Profiles Are Not Performance Modes

These are test workload labels, not configuration presets to deploy:

| Profile | What must remain equivalent |
|---|---|
| P01 `MINIMAL` | Hand-created proxy without Spring diagnostics; not the auto-configured application default |
| P02 `AUTO_NO_REGISTRY` | Actual auto-configuration with observability's default enabled but no registry/observer bean; discovery still occurs |
| P03 `AUTO_REGISTRY` | Same configuration plus a registry and built-in observer; terminal API timers are real work, not just classes on the classpath |
| P04 `RESILIENCE_ENABLED_ONLY` | Resilience enabled without an operator name; no operator selected, but the existing stateful path remains |
| P05 physical absence | Assembled consumer lacks optional integration classes; cannot be inferred from a dependency-rich benchmark JVM |
| P06 `OBSERVER` / `HOOK` | Application consumers still run without built-in Micrometer exports; ordering, scopes and terminal ownership remain |
| P07 independent pool gauges | Pool metrics have their own activation; disabling observability is not a pool-metric switch |
| P08 selected cache/work | Explicit policy, auth/key isolation, admission, coalescing and refresh semantics; hits do less transport work, not equivalent network work |

No universal zero-overhead or new performance-mode switch follows. Avoid removing
an enabled feature to make a comparison look equivalent. A defined but unselected
cache policy is inert; a selected policy has real validation and resource costs.
Factory/static-create inactive paths and legacy public constructors are different:
the latter can retain a dormant manager shell. Neither proves an application leak.

## Extension and Ownership Boundaries

- Keep [customizer](../../docs/15-customizer.md#cache-aware-execution) safety
  classification for every applicable application mutation. Cache hits still run
  required pre-lookup gates. Misses/retries/auth replay can repeat filters; a
  load-only exchange function cannot supply a mandatory hit-time gate.
- Keep [observer/hook](../../docs/19-lifecycle-hooks.md) provider discovery and
  per-subscription terminal state. A warm proxy does not authorize sharing mutable
  reporting state or assuming no future consumer can appear.
- Ordinary calls do not gain a deep snapshot of arbitrary mutable arguments.
  [One-shot inputs](../../docs/11-streaming.md#request-body-repeatability-matrix)
  remain one-shot. Consume/release raw response buffers and inner streaming bodies;
  envelope success is not inner-body completion.
- [Context handoff](../../docs/09-correlation-id.md) is explicit. Snapshot/restore
  only supported selected keys; application queues, loggers and envelopes own
  retained copies. Case-insensitive header access is not global map normalization.
  [P7](BODY-CONTEXT-OWNERSHIP.md) preserves caller isolation and terminal-once
  behavior without a new propagation hook.
- Spring destroys factory-owned resources; application connectors, SDKs and
  executors remain application-owned. An independent non-single-flight load can
  remain caller-owned after manager close; cancel/join it separately. Reactor's
  shared infrastructure is not a per-client resource to destroy for an allocation
  saving. [P8](INACTIVE-LIFECYCLE.md) records these boundaries.
- Diagnostics reports unknown provider facts as unknown/null and must not create
  lazy resources to fill a support record. Runtime and AOT selection retain the
  [published V33 limits](../v33/MAINTAINER-GUIDANCE.md#creation-and-ownership-boundaries).

## Reproduce the Investigation

Use the tracked [P2 build and scoring commands](WORKLOAD-CONTRACT.md#reproduction)
and [P10 verification commands](COMPATIBILITY-PERFORMANCE.md#reproduction-and-evidence).
Choose new output/repository paths. Never root-clean evidence or install current
artifacts into a published-baseline repository. Clean committed benchmark inputs,
Central provenance, identical non-starter JARs, exact source/JAR hashes and semantic
witnesses precede scoring. An absent ignored bundle requires rebuilding from its
recorded source, not treating a Markdown result as executable provenance.

Select the phase, not merely a convenient benchmark name:

| Question | Existing tracked row / control | Interpretation limit |
|---|---|---|
| Context/proxy construction | `V34ConstructionBenchmark.defaultV34ConstructionContextAndProxy` | Fixture lifecycle, not whole-service or native startup |
| First publisher / first subscribed call | `defaultV34ConstructionFirstPublisher` / `defaultV34ConstructionFirstCall` | Invocation setup/teardown contributes to B/op even when excluded from latency |
| Warm assembly | `V34DefaultPathBenchmark.defaultV34NoNetworkWarmPublisher` | No subscription or transport; not end-to-end latency |
| Warm subscription | `defaultV34NoNetworkWarmSubscription` | Real selected reporting with a synthetic exchange, not HTTP encoding |
| Actual request/body handling | `defaultV34LoopbackCall` with its named scenario | Includes loopback server/transport allocation; keep payload, codec, status and consumption matched |
| Parsing/planning/projection helpers | `V33PlanningCostBenchmark.metadataCold*`, `StarterInvocationInternalsBenchmark` | Cannot subtract helper means from unlike call means to infer removable cost |
| Enabled-feature safety | `V34WorkloadContractTest` and V28-V31 benchmark contract tests | Correctness sentinels, including gated coalescing; not additional timed workloads |

The frozen matrix is 54 V34 rows plus six helpers. Preserve all parameters, both
forks and all raw samples. The reviewer applies >20% mean latency or
>max(32 B/op, 5%) allocation review triggers, not an allowed regression budget.
Primary order is baseline then current; confirmation reverses it. Confirm every
flagged row and the historical enabled-only GET publisher even when the latest
primary does not flag it. A review command can exit zero while reporting a flag.

For example, after the P2 build has saved version-named directories, this command
confirms **only the historical row**. It does not replace the full primary matrix
or confirmation of other flags. `PAIR` must name that saved pair; `OUT` must be a
new directory outside sealed evidence. Record Java/toolchain, CPU/quota/pressure,
memory, commands/exits and before/after environment as required by P2. Do not run
Maven, native builds or profilers concurrently with scored runs.

```bash
set -euo pipefail
: "${PAIR:?Set PAIR to the saved P2 version-directory pair}"
: "${OUT:?Set OUT to a new confirmation evidence directory}"
test ! -e "$OUT"
python3 scripts/verify-v34-benchmark-inputs.py compare \
  "$PAIR/4.4.2/inputs.json" "$PAIR/4.5.0-SNAPSHOT/inputs.json"
mkdir -p "$OUT"
flags='-Xms512m -Xmx512m -XX:ActiveProcessorCount=2 -Dorg.slf4j.simpleLogger.defaultLogLevel=warn'
for version in 4.5.0-SNAPSHOT 4.4.2; do
  input="$PAIR/$version"
  test "$(jq -r .worktree "$input/inputs.json")" = ''
  expected="$(jq -er .shadedSha256 "$input/inputs.json")"
  printf '%s  %s\n' "$expected" "$input/benchmarks.jar" | sha256sum -c -
  commit="$(jq -er .sourceCommit "$input/inputs.json")"
  if java -Xms512m -Xmx512m -XX:ActiveProcessorCount=2 \
      -Dorg.slf4j.simpleLogger.defaultLogLevel=warn \
      -Dbenchmark.project.version=4.5.0-SNAPSHOT -Dbenchmark.starter.version="$version" \
      -Dbenchmark.api.compatibility.baseline.version=4.4.2 -Dbenchmark.spring-boot.version=4.0.0 \
      -Dbenchmark.commit="$commit" -Dbenchmark.stack.context='V34 historical-row confirmation' \
      -jar "$input/benchmarks.jar" '.*V34DefaultPathBenchmark.defaultV34NoNetworkWarmPublisher$' \
      -p profile=RESILIENCE_ENABLED_ONLY -p scenario=GET \
      -bm avgt -tu ns -t 1 -wi 5 -w 1s -i 5 -r 1s -f 2 -prof gc -foe true \
      -jvmArgs "$flags" -rf json -rff "$OUT/$version.json" >"$OUT/$version.log" 2>&1; then
    status=0
  else
    status=$?
  fi
  printf '%s\n' "$status" >"$OUT/$version.exit-status"
  test "$status" -eq 0 || exit "$status"
done
python3 scripts/review-v34-benchmark-results.py --confirmation \
  --baseline "$OUT/4.4.2.json" --current "$OUT/4.5.0-SNAPSHOT.json" \
  --output "$OUT/review"
```

Use the same Java 21 distribution/build and frozen flags on both sides. The
input comparison checks recorded source/dependency equality, not current machine
equivalence. Hash verification above checks saved JAR identity; it cannot replace
Central provenance or recreate missing build reports. Inspect `review/flagged.json`
and fork distributions, not just exit status. P10's unfavorable confirmation
remains open; these instructions do not authorize rerunning until green.

## Bounded Operations Triage

Published-version incidents start with [performance troubleshooting](../../docs/25-performance-troubleshooting.md)
and [sanitized support capture](../../docs/26-support-bundles.md#performance-investigations),
not a snapshot benchmark score. Record effective feature selections and available
integrations, version/JVM/Boot, phase, bounded body shape/size ranges, caller and
dispatch outcomes, and one time-aligned process window. Do not invent zero values
for absent metrics or unknown diagnostic facts.

| Domain | Safe evidence / limit |
|---|---|
| CPU and allocation | Phase-matched timing and B/op on synthetic fixtures; profile separately and record profiler overhead. Allocation rate is not retained live heap |
| Java retention | Post-GC heap trends plus owner/lifecycle evidence; regular tests use counters, reference counts and terminal acknowledgements, not forced collection |
| Direct/native memory and transport | Direct-buffer estimates, protocol-aware connection/stream gauges, threads and native-memory totals where available; not explained by Java B/op alone |
| RSS/container memory | Same-process checkpoints, limits, deployment changes and quiet windows; allocator/JVM reserves and sidecars can differ from live heap |
| Shutdown | Sample cache/work counters before close; after their last metric owner closes, absent counters cannot provide post-close deltas. Record application-owned completion separately |

Use existing bounded capture recipes and fixed outcome/count fields. Raw heap
dumps, JFR/allocation recordings and profiler stacks can expose payloads or local
paths: keep them private, time/size-limit any authorized capture, and review a
sanitized summary before sharing. Do not add targets, query/header/body values,
identities, credentials, cache keys/digests, retained objects or arbitrary exception
messages to a bundle. No production payload capture, new metric or dashboard is
required by V34. Existing cache bytes describe their documented decoded-response
domain, not JVM heap, direct memory or RSS.

The earlier pod-memory and service-mesh reports remain unattributed. Neither
unchanged class bytes nor a microbenchmark allocation split proves or disproves
an application leak. An explicit default-path reproducer with matched inputs is
needed before attributing a regression or reopening a deferred change.

## Verification and Remaining Gates

Priority 11 changes guidance and its guards only. Historical P1-P10 and V1-V33
records remain untouched. [P9 parity](PARITY-EVIDENCE.md) and P10 API, Boot,
native and cost evidence are references with their original dates and limits,
not new runs. No fresh native, scored JMH, full-reactor or Central-consumer run is
claimed by this documentation work. Generated readiness stays `activeRoadmap=v34`,
`releaseLane=unselected`, `plannedFinalVersion=null`.

From the repository root, Java 21 and Maven 3.9.9, use a writable non-baseline
Maven repository. These commands validate guidance/examples and retained contracts,
not performance or collection timing:

```bash
export MAVEN_OPTS='-Xmx512m -XX:ActiveProcessorCount=2'
mvn -B -ntp -s .mvn/maven-central-settings.xml -pl reactive-http-client-starter \
  -Dtest=DocumentationReleaseArtifactTest,ReactiveHttpClientPropertiesTest,ReactiveHttpClientConfigurationMetadataTest,V33GuidanceExampleTest,DefaultPathCostOwnershipTest,CacheWorkPolicyEnforcementTest,StreamingUploadOwnershipTest,SubscriptionLocalReportingStateTest,RequestContextSnapshotTest,ResourceOwnershipReviewTest \
  -DargLine='-XX:+DisableExplicitGC -XX:ActiveProcessorCount=2' test
mvn -B -ntp -s .mvn/maven-central-settings.xml -f reactive-http-client-benchmarks/pom.xml \
  -DargLine='-XX:+DisableExplicitGC -XX:ActiveProcessorCount=2' test
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s scripts -p 'test_*v34*.py' -v
git diff --check
```

The benchmark test command requires the current starter JAR installed in that
non-baseline repository first; use P2's scoped install command if needed. Separate
reachability profiles remain in P10, not this ordinary lane.

### Priority 11 Verification

Reviewed starting commit: `922c72f98246735385b5cc47e0d90bcd974961f6`, with this
documentation/test delta. Oracle Java 21.0.8, Maven 3.9.9, Boot 4.0.0, explicit GC
disabled; current dependencies come from the existing writable
`target/v33-native-runs/native-g0ynw95x/repository`. This is not clean-commit
release evidence or newly resolved Central consumption.

| Fresh verification | Cases / result |
|---|---:|
| DocumentationReleaseArtifactTest | 90 |
| ReactiveHttpClientPropertiesTest / ReactiveHttpClientConfigurationMetadataTest | 35 / 18 |
| V33GuidanceExampleTest | 4 |
| DefaultPathCostOwnershipTest / CacheWorkPolicyEnforcementTest | 15 / 12 |
| StreamingUploadOwnershipTest / SubscriptionLocalReportingStateTest | 24 / 5 |
| RequestContextSnapshotTest / ResourceOwnershipReviewTest | 9 / 20 |
| **Focused starter total, ten classes** | **232 passed** |
| Benchmark correctness, ten classes | 75 passed |
| V34 input/result/reviewer Python guards | 14 passed |
| Both Bash blocks, `bash -n` | exit 0 |
| JMH method/parameter discovery from saved P10 JAR | exit 0; no scoring |
| P10 recorded input comparison, full-row validation and primary/reverse review | exit 0; reverse review still reports one allocation flag |

All test rows have zero failures, errors and skips. The initial and final focused
runs each pass 232; they overlap and are not 464 distinct cases. The final rerun
includes the completion/status guard and final guidance. The four example tests
compile the existing V33 public example and exercise its behavior; no new public
API or configuration example is introduced. Metadata and documentation tests
validate generated references, local links, archive/readiness and safe fixtures.

The 75 benchmark tests use the unchanged P10 current starter JAR, rehashed to
`e2803a7bf45c774d73f65c41676a2d8adc435db00842ef7055db3ff25c41abf2`.
No Maven/test process overlaps scored work because no new score was collected.
The confirmation block is syntax/discovery checked, not re-executed for new
measurements. Python validation/review was rerun on preserved P10 raw JSON in a
new output directory; the sealed P10 bundle remains unchanged and rehashes fully.

Evidence under `target/release-evidence/v34/priority11/` retains exact commands,
exits, fresh XML-derived counts, source/patch, generated readiness, command checks
and the prior inventory audit. `SHA256SUMS` is indexed in the checklist, which is
excluded from the final source-copy seal to avoid a self-referential digest.
10.1 acceptance and all P12 release decisions remain open; guidance does not
waive them. No claim is made that the earlier deployment incidents were resolved.
