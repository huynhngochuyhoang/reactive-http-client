# V34 Cross-Path and Assembled Parity

> **Status:** Priority 9 complete, 2026-10-03
> **Delivered production IDs:** none
> **Production scope:** N/A; C004 rolled back, broader changes deferred
> **Release scope:** unselected

This executes [Priority 9](CHECKLIST.md) after the
[inactive-resource controls](INACTIVE-LIFECYCLE.md). Starting clean source is
`fc98e58b9d5154a0ba539ea878b05c532b379554`. The mock, consumer and JVM/AOT
runs below use that source without a local patch. The native runner checks out
that exact commit into a separate clean workspace. Later closure edits change
only this evidence, current navigation/checklist and their documentation guard;
no production, dependency, fixture or runner input changes.

No optimization remains after the [P6 rollback](HARDENING-EVIDENCE.md).
This priority delivers fresh parity evidence, not a new feature, performance
claim or release approval. C001-C003, C005 and broader C004 remain deferred;
the enabled-only allocation flag remains unresolved.

## Entry Points and Witnesses

| Path | Retained contract and fresh witness | Intentional boundary |
|---|---|---|
| Public mock helper | Four classes exercise fresh public metadata, cold/repeated calls, selected cache identity, auth/retry, ordered observers/hooks, context handoff, weighted admission, coalescing, refresh and close with both real and deterministic clocks | In-process exchanges are not TCP, redirect-connector, pooled-buffer or socket-release evidence. An independent foreground load remains caller-owned through manager close |
| Public handler and Spring factory | `V33CrossPathContractTest` runs both creation paths: selected non-primary properties and fresh metadata drive planning, diagnostics, request targets and results. Boot/default/auth/client gates still run on hits; tenant and rewritten targets partition entries; terminals retain zero transport facts for hits/rejections | A public handler is not registered as a Spring factory, so its live entry count remains unknown in factory diagnostics. This difference is asserted, not normalized away |
| Assembled selected-feature consumers | Both actual Boot parents exercise external-package extensions, starter-builder ownership and SAFE classification, replacement codec/metadata, auth/tenant boundaries, application connector ownership, semantic-read caching, weighted/work bounds and context handoff. V26 observability assertions are explicitly enabled | Consumers use installed starter/test/OTel JARs and their selected Caffeine/Resilience4j dependencies; no reactor output directory substitutes for an artifact |
| Physically minimal consumers | Each row checks absence of Caffeine, four Resilience4j registries, MeterRegistry, OTel API and the mock helper. Resilience is enabled without operator selection; exactly two GET `/value` calls dispatch, and other verbs/targets do not receive a successful response | One basic smoke case per row. The runner does not copy the optional sibling V31 source directory into this minimal overlay; its profile flag adds no handoff cases. Handoff evidence comes from selected consumers and mocks, not this absent-classpath lane |
| Runtime/AOT selection and hints | Five focused classes per Boot row preserve properties preference, scopes/aliases, initialization and binding order, restored processor ownership, fresh metadata and hints, non-instantiation and cross-path behavior | This is targeted JVM verification, not the full module/supported-dependency/API matrix reserved for P10 |
| Ordinary/AOT-enabled/native smoke | Actual GET/POST requests, decoded records, cache miss/hit/refresh, explicit retry, open-circuit admission, weighted/work bounds, WebFlux capture/restore, shutdown and same-tag recreation | Boot 4.1 is JVM/AOT evidence only; the native image uses Boot 4.0.0. Representative fixtures do not prove every optional integration or deployment topology |

The selected consumer runs all V27-V32 fixture profiles with
`consumer.v26.observability=true`. The source profiles are `v27-current-parity`,
`v28-current-parity`, `v29-current-parity`, `v30-current-parity`,
`v31-current-parity` and `v32-extension-scenarios`.
See the [tracked parity runner][parity-runner], [consumer POM][consumer],
[physical-absence fixture][minimal] and [cross-path test][cross-path].

## Fresh JVM Results

| Lane | Boot | Cases / execution |
|---|---|---:|
| `MockReactiveHttpClientTest`, `Boot4MockReactiveHttpClientTest`, `MockCacheWorkParityTest`, `MockInboundContextParityTest` | 4.0.0 | 76 passed |
| Assembled selected-feature consumer | 4.0.0 / 4.1.0 | 29 passed each |
| Physical optional-absence consumer | 4.0.0 / 4.1.0 | 1 passed each |
| `AotPropertiesSelectionContractTest`, `AotMetadataSelectionContractTest`, `ReactiveHttpClientAotSmokeTest`, `EffectiveSelectionAotReviewTest`, `V33CrossPathContractTest` | 4.0.0 / 4.1.0 | 283 passed each |
| Smoke fixture unit tests | 4.0.0 / 4.1.0 | 6 passed each |
| Ordinary smoke executable JAR | 4.0.0 / 4.1.0 | exit 0 each |
| AOT generation and AOT-enabled executable JAR | 4.0.0 / 4.1.0 | exit 0 each |

The above total **714 JUnit executions**, including the same cases on two Boot
stacks; they are not 714 unique test definitions. All have zero failures, errors
and skips. The four application executions are separate from JUnit counts.
Oracle Java 21.0.8 and Maven 3.9.9 are used for these lanes, with
`JAVA_TOOL_OPTIONS='-XX:ActiveProcessorCount=2 -XX:+DisableExplicitGC'`.
No collection claim depends on these ordinary runs.

### Effective Stacks

The runner parses copied POMs to change the **actual parent** and, where present,
the Boot property. Every resolved Boot JAR must match its row. Overriding only
`spring-boot.version` on the fixed 4.0.0 parent is not the upper row used here.

| Resolved component | Boot 4.0.0 consumer | Boot 4.1.0 consumer |
|---|---|---|
| Spring Framework / WebFlux | 7.0.1 | 7.0.8 |
| Reactor Netty HTTP | 1.3.0 | 1.3.6 |
| Netty HTTP codec | 4.2.7.Final | 4.2.15.Final |
| Jackson databind | 3.0.2 | 3.1.4 |
| Micrometer core | 1.16.0 | 1.17.0 |
| OpenTelemetry API | 1.55.0 | 1.62.0 |
| Resilience4j retry | 2.4.0 | 2.4.0 |
| Caffeine | 3.2.3 | 3.2.4 |

These are compatibility workloads, not a matched performance pair. The repository
is the existing writable `target/v33-native-runs/native-g0ynw95x/repository`,
after a fresh reactor install. It is not an independent Central download or
published `4.4.2` consumption. Dependency classpaths contain JARs only; each
selected consumer resolves all three current module artifacts. Effective POMs,
dependency trees, full classpaths and per-artifact SHA-256 files are retained.

## Native Execution

Fresh compile and executable both passed on 2026-10-03. No earlier binary was
reused, no assertion was weakened and no native retry was needed in this priority.

| Provenance | Recorded value |
|---|---|
| Source commit | `fc98e58b9d5154a0ba539ea878b05c532b379554` |
| Source tree | `f0e6d7634d5d4f7113450f055a02c6020ccb0674` |
| Working tree | Clean before and after compilation/execution |
| Reactor / Boot | `4.5.0-SNAPSHOT` / `4.0.0` |
| Toolchain | GraalVM/native-image `25.0.3`, Maven `3.9.9`, Java target `21` |
| Build limits | `-H:+SharedArenaSupport`, `-J-Xmx6g`, `--parallelism=2` |
| Starting resources | Approximately 8.9 GiB available RAM and 4 GiB free swap |
| Compile | exit `0`, 11:50:08-11:55:52 UTC |
| Executable | exit `0`, 11:55:52-11:56:11 UTC |
| Fixture tests during compile | 6 passed; zero failures, errors or skips |
| Build report | Generation 5m 29s; peak build RSS 5.37 GB, not application memory evidence |
| Binary SHA-256 | `a3476f5f749d0cb546e4c175f371ea070291923d5621d92cb819758ab9a3b10e` |

Sealed native evidence:
`target/v34-priority9-native-runs/native-feynp9yy/evidence/`.
Its `summary.json`, per-command results, clean-source records, effective POM,
dependency tree, classpath, fixture XML, binary and `SHA256SUMS` are retained.
The separate writable repository is seeded from the existing cache, not an
independent download. Expected connection-close warnings during shutdown and
existing initialization/meter/native-access warnings remain in the logs.

The unchanged [smoke fixture][native-source] counts method-specific routes and a
catch-all. Open-circuit admission checks a one-second quiet period and then the
dispatch total, rather than reading a counter only at rejection. Shutdown awaits
active load/refresh cancellation, verifies queued callers terminated, and observes
a bounded no-late-dispatch window before same-tag recreation. The main server
expects 20 dispatches before close and exactly one more after recreation.
Separate work/context servers retain their own counters and terminal assertions.
The executable also reports GET/POST capacity, refresh skip, slot reuse and
independent deadlines passed, and capture=3/restored=2/rejected=1 for inbound context.
These bounded witnesses do not promise absence of arbitrarily delayed external work.

## Reproduction and Applicability

From the project root, using fresh output paths:

```bash
export JAVA_HOME=/usr/lib/jvm/jdk-21.0.8-oracle-x64
export PATH="$JAVA_HOME/bin:$HOME/.sdkman/candidates/maven/current/bin:$PATH"
export MAVEN_OPTS='-Xmx512m -XX:ActiveProcessorCount=2'
export JAVA_TOOL_OPTIONS='-XX:ActiveProcessorCount=2 -XX:+DisableExplicitGC'
REPO="$PWD/target/v33-native-runs/native-g0ynw95x/repository"
OUT="$PWD/target/release-evidence/v34/priority9-rerun"
for stage in install mock consumer40 consumer41 minimal40 minimal41 aot-tests aot-tests41 jvm40 jvm41; do
  python3 scripts/verify-v33-parity.py "$stage" --repository "$REPO" --evidence "$OUT" || break
done
unset JAVA_TOOL_OPTIONS
python3 scripts/verify-v33-native.py \
  --ref fc98e58b9d5154a0ba539ea878b05c532b379554 \
  --java-home "$HOME/.sdkman/candidates/java/25.0.3-graal" \
  --work-root "$PWD/target/v34-priority9-native-runs" \
  --seed-repository "$REPO"
```

The tracked runner creates the overlays; no ignored overlay or generator is needed
to reproduce either Boot parent. Stop on a failed stage and preserve its output;
do not interpret an incomplete loop as full parity. The native runner uses a new
non-root clone and repository; its seed is copied, never modified. It refuses
sudo and retains failures/timeouts with command results. No tests are skipped in
native compilation. The 6 GiB build heap and two compilation threads are resource
limits, not runtime performance results.

Evidence root: `target/release-evidence/v34/priority9/`. Stage reports come from
fresh cleaned test outputs. P8 resource controls remain earlier measured evidence,
not added again to P9 totals. No old native binary is substituted for the new run.
The final audit records source/input hashes, artifact provenance, historical
inventory checks, native evidence integrity and current readiness. The checklist
is the external integrity index and is excluded from final source-copy sealing.

**Remaining gates:** P10 still owns the final matched matrix, unresolved allocation
investigation, strict independent API comparisons and complete module/packaging
verification. P11 guidance and P12 release selection remain open. This priority
does not promote a benchmark score, infer RSS improvement, or select a release.

[parity-runner]: ../../scripts/verify-v33-parity.py
[consumer]: ../../.github/boot4-consumer/pom.xml
[minimal]: ../../.github/boot4-cache-disabled-consumer/src/test/java/io/github/huynhngochuyhoang/httpstarter/cachedisabled/Boot4CacheDisabledConsumerTest.java
[cross-path]: ../../reactive-http-client-starter/src/test/java/io/github/huynhngochuyhoang/httpstarter/core/V33CrossPathContractTest.java
[native-source]: ../../.github/native-smoke/src/main/java/io/github/huynhngochuyhoang/httpstarter/nativesmoke/NativeSmokeApplication.java
