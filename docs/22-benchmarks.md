# Benchmarks

The commands in [Commands](#commands) are authoritative for the current
`3.5.0-SNAPSHOT` development line and published `3.4.0` baseline. Versioned
scope sections preserve V12-V20 evidence and are historical unless explicitly
identified as current.

The benchmark harness compares the starter with raw `WebClient` and Spring HTTP
Interface clients under equivalent local conditions. It is intentionally outside
the default reactor so normal unit tests and release smoke tests stay fast.

## Methodology and Limits

Benchmark methodology comes before benchmark numbers. A release-quality report is
only evidence for the named scenarios it contains, the dependency versions it
records, and the machine/JVM environment that produced it. Do not generalize one
local run into a universal performance claim.

The client-side overhead scenarios use a cheap local loopback server so request
construction, argument resolution, response decoding, and client abstraction cost
are easier to inspect. Optional starter features are enabled one at a time. A
feature scenario is compared with raw `WebClient` or Spring HTTP Interface only
when the baseline client performs equivalent work; otherwise it is labeled as
starter-only overhead.

Known limits:

- Local loopback still includes transport, Netty event-loop scheduling, CPU
  scheduling, JVM warmup, and machine variability.
- Quick benchmark output is smoke-only. It proves the harness starts and writes
  files; it is not publishable performance evidence.
- `application/problem+json` mapping is starter error-mapping overhead unless a
  baseline client installs an equivalent Problem Detail mapper.
- Conclusions must name the scenario and report version that produced the data.

Current promoted report:

- [Benchmark Report 2.12.0](benchmark-report-2.12.0.md) is release-quality
  evidence for starter `2.12.0`, generated from the `2.12.0` release benchmark
  profile. Use it only for the named scenarios and environment it records.
- [Performance Summary](23-performance-summary.md) explains how to read the
  promoted report without turning scenario-specific data into broad claims.
- [Production Support Bundles](26-support-bundles.md) explains which benchmark
  evidence belongs in performance incident bundles.
- [Benchmark Consumer Examples](24-benchmark-consumer-examples.md) shows the raw
  `WebClient`, Spring HTTP Interface, and starter client shapes used by one
  comparable success-path scenario.

## Comparison Model

| Surface | Benchmark role | Equivalent work required | Supported claim scope |
|---|---|---|---|
| Raw `WebClient` | Baseline client-side overhead comparison. | Same transport, codecs, base URL, request metadata, response decoding, and consumed body. | Only the named loopback scenario and report version. |
| Spring HTTP Interface | Framework proxy comparison where the scenario is supported without custom glue that changes the comparison. | Same transport, codecs, base URL, request metadata, response decoding, and consumed body. | Only the named loopback scenario and report version. |
| Starter | Declarative client under test, including starter-only feature and error-mapping overhead scenarios. | Default scenarios match the baselines; optional features are measured separately or against baselines doing equivalent work. | Only the named starter scenario and report version. |

## Commands

Compile the benchmark module without running benchmarks:

```bash
mvn -Pbenchmarks -pl reactive-http-client-benchmarks -am package
```

Discover benchmark methods and enforce naming, classification, no-network, and
three-surface comparison completeness without recording measurements:

```bash
mvn -Pbenchmarks,benchmark-discovery -pl reactive-http-client-benchmarks -am clean verify
```

Run the same current harness against published `3.4.0` on the managed Boot stack
to prove the baseline-compatible scenario set still compiles and is discoverable:

```bash
mvn -s .mvn/maven-central-settings.xml \
  -Dmaven.repo.local=target/published-baseline-repositories/benchmark-discovery-3.4.0 \
  -Pbenchmarks,benchmark-discovery,benchmark-published-baseline \
  -pl reactive-http-client-benchmarks clean verify \
  -Dbenchmark.starter.version=3.4.0
```

Run the quick harness smoke benchmark:

```bash
mvn -Pbenchmarks,benchmark-smoke -pl reactive-http-client-benchmarks -am verify
```

The smoke command is only a harness check. It proves the benchmark classes
compile, start, execute, and write result files. Do not publish smoke numbers as
project performance evidence.

Run a longer release-quality benchmark for the current workspace. The `-am` flag
intentionally builds the reactor starter under test:

```bash
mvn -Pbenchmarks,benchmark-release -pl reactive-http-client-benchmarks -am verify -Dbenchmark.commit=$(git rev-parse --short HEAD)
```

For a report that will be promoted into `docs/benchmark-report-<version>.md`,
run the release benchmark from a clean committed tree:

```bash
git status --short
git rev-parse --short HEAD
mvn -Pbenchmarks,benchmark-release -pl reactive-http-client-benchmarks -am verify -Dbenchmark.commit=$(git rev-parse --short HEAD)
```

`git status --short` must print nothing before the benchmark starts. Do not
promote reports whose environment table has a missing `benchmarkCommit`,
`benchmarkCommit=unknown`, or a commit value containing `dirty`. The promoted
`benchmarkCommit` must begin with a short Git SHA so future release comparisons
can check out the benchmarked input tree.

Generated target-only reports and adjacent JMH environment files may include
machine-local absolute paths from Maven, the JVM, or the operating system while
they remain under `target/`. Before copying a release-quality report into
`docs/`, sanitize those paths; source-controlled promoted reports must not
contain machine-local absolute paths such as `/home/`, `/workspace/`, `/tmp/`, or `C:\Users\...`.

Run the same benchmark harness against the last published starter artifact by
setting `benchmark.starter.version`, enabling `benchmark-published-baseline`, and
omitting `-am` so Maven resolves the published dependency instead of the current
reactor module:

```bash
test ! -e target/published-baseline-repositories/benchmark-3.4.0 && \
mvn -s .mvn/maven-central-settings.xml \
  -Dmaven.repo.local=target/published-baseline-repositories/benchmark-3.4.0 \
  -Pbenchmarks,benchmark-release,benchmark-published-baseline \
  -pl reactive-http-client-benchmarks clean verify \
  -Dbenchmark.starter.version=3.4.0 -Dbenchmark.commit=3.4.0 && \
scripts/verify-published-baseline-provenance.sh benchmark 3.4.0 \
  target/release-evidence/published-baselines/benchmark-3.4.0 \
  reactive-http-client-starter
```

The example version must match the root `api.compatibility.baseline.version`
(`3.4.0` for this development line). When that property changes for the next
development cycle, update this command and the `published-starter-<version>`
report paths together.
V20 used `2.14.1` for its cross-major evidence. After `3.4.0` publication, the
normal benchmark baseline moves to `3.4.0`; the historical V20 report and
commands remain in the V20 checklist.

The repository path must not exist before the run. The shared provenance verifier
records the published starter POM and jar hashes plus their Maven Central remote
marker as target-only evidence. It also rejects any project artifact version other
than the selected baseline. Select another empty
target-local path when retaining earlier evidence. The command uses the current
benchmark harness and current managed Spring Boot BOM, and excludes current-only
diagnostics-provider benchmarks that cannot compile against the published
baseline artifact. Its report is written under
`reactive-http-client-benchmarks/target/benchmark-reports/published-starter-<version>/`
so it does not overwrite the current-workspace release report.
For an exact historical release environment, check out the release tag and run
the current-workspace command from that checkout.

Release-quality runs write JMH JSON under:

```text
reactive-http-client-benchmarks/target/benchmark-reports/
```

Each run also writes an adjacent `*.environment.properties` file with Java,
OS, CPU, Spring Boot, Spring WebFlux, Reactor Netty, project version, starter
version under test, API compatibility baseline version, dependency-management
source, and benchmark commit metadata. Profile-backed runs redirect benchmark
logger output to
`reactive-http-client-benchmarks/target/benchmark-logger.log` so the real
metadata exchange logger can be measured without flooding stderr.

## Release Evidence

`DocumentationReleaseArtifactTest` includes benchmark evidence in
`target/release-evidence/reactive-http-client-release-evidence.json`. The
manifest keeps benchmark execution manual or profile-gated, records the benchmark
dependency-management baseline, lists the published baseline artifacts that must
resolve before release, and lists these generated report paths:

- `reactive-http-client-benchmarks/target/benchmark-reports/smoke-only-jmh.md`
  for the smoke harness check.
- `reactive-http-client-benchmarks/target/benchmark-reports/release-jmh.md`
  for current-workspace release-quality evidence.
- `reactive-http-client-benchmarks/target/benchmark-reports/published-starter-<version>/release-jmh.md`
  for published-starter baseline release-quality evidence.

Refresh release benchmark numbers when a release changes request construction,
observability, resilience wrapping, transport or client-builder behavior, or when
release notes make public performance claims. Before publishing those claims,
promote the release-quality report into `docs/` and cite that source-controlled
promoted report from the release notes. A pending benchmark entry in the
release evidence manifest is intentional for releases with no request-path
change; for benchmark-relevant releases it is the visible reminder
that release evidence still needs to be produced. Published baseline artifact
entries include `mvn dependency:get -Dartifact=...` commands; a resolution
failure is a release blocker because the benchmark or API compatibility baseline
would no longer be reproducible.

## Release-Note Benchmark Evidence

When release notes include performance claims, add a benchmark evidence block near
the claim or in the release checklist. This copyable example uses paths relative
to the repository root, as they should appear in `CHANGELOG.md` or root-level
release notes:

```markdown
Benchmark evidence:
- Promoted report: `docs/benchmark-report-<version>.md` after the release-quality report is generated and promoted
- Current candidate command: `mvn -Pbenchmarks,benchmark-release -pl reactive-http-client-benchmarks -am verify -Dbenchmark.commit=$(git rev-parse --short HEAD)`
- Published baseline command: `test ! -e target/published-baseline-repositories/benchmark-3.4.0 && mvn -s .mvn/maven-central-settings.xml -Dmaven.repo.local=target/published-baseline-repositories/benchmark-3.4.0 -Pbenchmarks,benchmark-release,benchmark-published-baseline -pl reactive-http-client-benchmarks clean verify -Dbenchmark.starter.version=3.4.0 -Dbenchmark.commit=3.4.0 && scripts/verify-published-baseline-provenance.sh benchmark 3.4.0 target/release-evidence/published-baselines/benchmark-3.4.0 reactive-http-client-starter`
- Current candidate report: `reactive-http-client-benchmarks/target/benchmark-reports/release-jmh.md`
- Published baseline report: `reactive-http-client-benchmarks/target/benchmark-reports/published-starter-3.4.0/release-jmh.md`
- Scenarios cited: `Get No Body`, `Post Json`
```

`DocumentationReleaseArtifactTest` also writes `target/release-evidence/reactive-http-client-benchmark-evidence.md` from the same manifest data. Use that generated target-only snippet as the release-note starting point, then paste it only after the promoted `docs/benchmark-report-<version>.md` file exists.

The promoted report link is required for public performance claims. Use the
manifest `promotedReport` value, such as `docs/benchmark-report-<version>.md`,
when citing the report from `CHANGELOG.md` or release notes. A pending
benchmark entry in the generated release evidence manifest is not enough for a
release that publishes performance wording; run the current candidate benchmark,
promote the report, and cite the promoted report from the release notes.

When a release has no public performance claim, release notes may say that
benchmark evidence remains manual, pending, or unchanged. Do not mention
faster/slower movement, overhead reductions, latency changes, throughput changes,
or allocation changes unless the current release section links to a
source-controlled promoted report for that release version.

## Release-Maintainer Performance Claim Checklist

Before adding or approving a public performance claim in `CHANGELOG.md`, release
notes, README, or docs, verify all of the following:

- The claim names the promoted release-quality report, not a generated
  `target/benchmark-reports` file and not a smoke-only report.
- The claim names the exact scenario or scenario group, such as `Get No Body`,
  `Post Json`, or `Problem Detail Small Body`.
- The claim names the compared surfaces, such as starter, raw `WebClient`, or
  Spring HTTP Interface.
- The claim names the metric being discussed: average time, p50, p95, p99,
  throughput, or allocation per operation.
- Current-candidate and published-baseline reports are kept in separate paths and
  the published baseline artifacts resolved before comparison.
- Any review-trigger movement is rerun on the same machine and either optimized
  or documented as expected before publication.
- Broad claims such as "near zero overhead", "always faster", or "same
  performance as raw WebClient" are removed unless the cited report directly
  supports that exact wording for the named scenario.

## Current vs Published Baseline Pairing

Keep the current candidate report and published-baseline report as a pair when
reviewing release-to-release trends. The current candidate run writes
`reactive-http-client-benchmarks/target/benchmark-reports/release-jmh.md`; the
published-baseline run writes
`reactive-http-client-benchmarks/target/benchmark-reports/published-starter-<version>/release-jmh.md`.
Those paths must remain distinct so one run cannot overwrite the other.

Compare the paired JMH JSON reports with the target-only helper after both reports exist:

```bash
mvn -Pbenchmarks,benchmark-compare -pl reactive-http-client-benchmarks -am verify \
  -Dbenchmark.compare.current=reactive-http-client-benchmarks/target/benchmark-reports/release-jmh.json \
  -Dbenchmark.compare.baseline=reactive-http-client-benchmarks/target/benchmark-reports/published-starter-3.4.0/release-jmh.json
```

The helper writes `reactive-http-client-benchmarks/target/benchmark-reports/benchmark-comparison.md` by default. The comparison includes each matching benchmark method and mode, current and baseline values, absolute and relative deltas, average time, p50, p95, p99, throughput, and allocation per operation when those metrics are present. Missing current or baseline rows are listed explicitly. V13 threshold crossings are marked as `review`, but the command exits successfully by default so normal CI does not become a benchmark gate. For local release review, add `-Dbenchmark.compare.fail-on-review=true` to return a non-zero exit when any row is marked `review`. Attach or paste the generated `benchmark-comparison.md` next to the promoted report link when release notes discuss current-vs-baseline movement.


Before promoting a comparison, resolve every published baseline artifact listed
in `target/release-evidence/reactive-http-client-release-evidence.json`. A
baseline artifact resolution failure means the published-baseline report cannot
be reproduced and blocks promotion. Promoted reports must name the starter
version under test and the published baseline version separately; do not label
current candidate numbers as baseline numbers or reuse one report for both
versions.

To rerun only the benchmark methods used by a release-note claim, keep the
Maven release profile so the generated report keeps project, starter, dependency,
and commit metadata. Override only the JMH include pattern and result directory:

```bash
mvn -Pbenchmarks,benchmark-release -pl reactive-http-client-benchmarks -am verify \
  -Dbenchmark.commit=$(git rev-parse --short HEAD) \
  -Dbenchmark.include='.*(clientSideOverhead.*(GetNoBody|GetPathQueryHeader|PostJson|ResponseEntity|ClientErrorSmallBody|ServerErrorSmallBody)|starterErrorMappingProblemDetailSmallBody).*' \
  -Dbenchmark.result.dir=target/benchmark-reports/release-note
```

Keep current-candidate and published-baseline reports in separate paths when
comparing releases. Release notes should name the starter versions and scenario
names being compared so readers can reproduce each claim from the cited report.

## Benchmark Review Triggers

Benchmark thresholds are review triggers, not CI gates and not automatic release
blockers. Normal CI stays free of long-running benchmark pass/fail checks; run
release-quality benchmarks manually or through the benchmark profiles when a
release changes request-path behavior or makes performance claims.

When comparing a current-candidate report with a promoted or published-baseline
report, investigate these signals before publishing a performance claim:

- Average-time, p50, p95, or p99 movement of about 20% or more in a named
  client-side scenario. Rerun the pair on the same machine before treating it as
  a trend.
- Allocation growth of about 15% or more, or a persistent increase larger than
  4 KiB/op, in a scenario whose request/response shape did not intentionally
  change.
- Optional-feature rows moving about 25% or more, or growing more than 4 KiB/op,
  compared with the previous release-quality report for the same feature row.
- Error-mapping rows moving materially after body-capture, mapper, exception, or
  fallback behavior changes. Keep Problem Detail rows starter-only unless a
  baseline installs equivalent mapping.

A triggered review should identify the changed code path, rerun the relevant
current and baseline methods, and either optimize the regression or document why
the change is expected. Do not fail a release solely because one local benchmark
run crosses a threshold; benchmark variance, JVM warmup, and local scheduling
remain part of the evidence.

## Publishing Performance Claims

Every public performance claim must include:

- The release-quality report version or promoted report link.
- The exact JMH scenario name or scenario group.
- The compared surfaces, such as raw `WebClient`, Spring HTTP Interface, or the
  starter.
- The measured dimension being discussed, such as throughput, average latency,
  percentile latency, or allocation rate.

Avoid broad wording such as "near zero overhead", "always faster", or "same
performance as raw `WebClient`" unless a release-quality report directly supports
that wording for the named scenario. Stale promoted benchmark report links fail
documentation checks, and smoke-only report links must not be used as performance
evidence.

## Current Scope

### Spring Boot 4 release baseline

V20 establishes the harness on the default publishable Boot 4 dependency stack.
Run the complete smoke matrix from the current reactor with:

```bash
mvn -s .mvn/maven-central-settings.xml \
  -Pbenchmarks,benchmark-smoke \
  -pl reactive-http-client-benchmarks -am \
  -DskipTests -Dmaven.javadoc.skip=true \
  -Dbenchmark.commit=$(git rev-parse --short HEAD) \
  clean verify
```

Use the [3.x migration guide](28-spring-boot-4-jackson-migration.md) to identify
Boot 3-to-Boot 4 dependency and codec changes before interpreting cross-release
movement. Cross-generation movement is migration context, not starter-only cost.

This is a smoke-only harness check. It exercises raw `WebClient`, Spring HTTP
Interface, and the starter against the same local server, Reactor Netty
transport setup, Boot 4 BOM, and request/response validation. Boot 4 selects
the Jackson 3 starter codec for starter-owned serialization; Boot 3 keeps the
Jackson 2 codec on the maintenance lane. Problem Detail remains a
starter-specific error-mapping row because neither baseline installs that
mapper.

Generated metadata records Spring Boot, Spring Framework/WebFlux, Reactor
Netty, Netty, the selected Jackson generation, Micrometer, OpenTelemetry, Java,
starter, API baseline, and commit versions. Reports label their stack context
and state that Boot 3 versus Boot 4 movement is migration context, not evidence
of a pure starter optimization. Review thresholds remain manual signals.

V20 did not promote this smoke report. The `3.0.0` release notes make no
numerical performance movement claim, and a smoke run is not release-quality
evidence even when its commit metadata is immutable. If `3.0.0` release notes
later make a public performance claim, rerun the required rows with
`benchmark-release` from a clean commit, promote the sanitized versioned report
into `docs/`, and cite only the same-stack scenarios that support the claim.

The benchmark module currently includes:

- A no-network starter invocation benchmark for metadata lookup, cached
  request-plan lookup, proxy invocation, scalar path/query/header argument
  resolution, and mock-exchange request construction.
- Client-side loopback overhead scenarios across raw `WebClient`, Spring HTTP
  Interface, and the starter for `GET` with no body, `GET` with path/query/header
  arguments, `POST` JSON, `Mono<ResponseEntity<T>>`, 4xx with a small bounded
  body, and 5xx with a small bounded body.
- A starter-only `application/problem+json` scenario labeled as error-mapping
  overhead, because the raw `WebClient` and Spring HTTP Interface baselines do
  not install the starter Problem Detail mapper.
- Starter-only optional feature scenarios that enable one feature at a time:
  metadata-only exchange logging, Micrometer observation, retry wrapping, and
  circuit-breaker wrapping. These are not compared against baseline clients that
  omit equivalent feature work.
- A no-network optional diagnostics audit for disabled diagnostics,
  metadata-only exchange logging, Micrometer observation with `SimpleMeterRegistry`,
  and the on-demand runtime diagnostics provider.

Both smoke and release-quality commands write a Markdown report next to the JMH
JSON. For example, the smoke profile writes `smoke-only-jmh.md` and the
release profile writes `release-jmh.md` under the benchmark report directory.
Generated files stay under `target/` unless a maintainer intentionally promotes
a release-quality report into `docs/` with a versioned filename. Only link a
promoted report from release notes when the release makes performance claims
backed by that report.

## Benchmark Naming Contract

The generated Markdown report classifies rows by benchmark method prefix. Keep
new benchmark names in one of these buckets so release reports do not mix
different scenario shapes:

- `clientSideOverhead<RawWebClient|SpringHttpExchange|Starter><Scenario>` is
  reserved for loopback scenarios where all compared clients perform the same
  HTTP request and validation work. These rows feed the comparison summary.
- `starterFeature<Scenario>` is reserved for loopback starter scenarios that
  enable exactly one optional starter feature while still sending a request to
  the shared loopback server. These rows appear in the starter-only optional
  feature summary.
- `starterErrorMapping<Scenario>` is reserved for loopback starter-specific
  error-mapping work, such as Problem Detail mapping, where baseline clients do
  not install equivalent behavior. These rows appear as starter-only
  error-mapping overhead.
- No-network invocation or diagnostics audits must not use the `starterFeature`
  prefix. Accepted no-network prefixes are `metadata...`, `cached...`,
  `argumentResolution...`, `proxyInvocation...`, `diagnosticsDisabled...`,
  `diagnosticsNoNetwork...`, `metadataOnly...`, `micrometerObserver...`, and
  `runtimeDiagnosticsProvider...`. These rows remain in raw results as
  `No-network starter invocation` and are excluded from optional feature
  summary tables.

Report generation fails for an unknown prefix, an unknown
`clientSideOverhead` surface, or an empty scenario suffix. Add the classification
contract and its report test in the same change as any new benchmark method.
Discovery also fails unless every `clientSideOverhead` scenario has exactly one
raw `WebClient`, Spring HTTP Interface, and starter method. Loopback-only prefixes
are rejected on no-network benchmark classes, so diagnostics rows cannot drift
into transport comparison tables.
The V18 scope review added no benchmark rows and promoted no report because it
introduced no public performance claim or changed request path.

Resilience wrapper rows such as `starterFeatureRetryWrapperGetNoBody`,
`starterFeatureRateLimiterWrapperGetNoBody`, and
`starterFeatureCircuitBreakerWrapperGetNoBody` are starter-only optional-feature
scenarios. Each enables only the named operator around the same loopback request.
Compare these rows release to release against the same starter scenario; do not
compare them with raw `WebClient` unless that baseline is changed to perform the
same resilience work.

## Metrics

Loopback benchmarks use stable JMH benchmark names for release-to-release diffs
and run in throughput, average-time, and sample-time modes. The sample-time mode
emits percentile rows such as `p0.50`, `p0.95`, and `p0.99` in the JMH output.

The release-quality profile adds the JMH GC profiler so allocation rate is present
in the release JSON where the JVM can report it reliably. Expected HTTP error
scenarios catch and validate the mapped exception instead of failing the JMH run;
unexpected benchmark failures remain visible as JMH errors. The loopback server
also tracks invalid request counts and reports the first mismatch when a client
does not send the shared scenario shape.

## Optional Diagnostics Overhead

Optional diagnostics are measured in two separate buckets. Request-path
diagnostics benchmarks exercise proxy calls with no network I/O, so disabled
exchange logging and observer paths can be compared with metadata-only exchange
logging and Micrometer recording. Runtime diagnostics provider calls are measured
separately because `ReactiveHttpClientDiagnosticsProvider.clientSummaries()` is an
on-demand inspection API, not part of proxy invocation. Actuator diagnostics
endpoint rendering is also support-path work, not request-path work; add a
dedicated endpoint-rendering row only if support-bundle adoption shows that JSON
serialization is a real bottleneck.

Strict unsafe-retry validation and strict built-in SigV4 body-signing validation
are startup/proxy-construction checks. Do not measure them with request-path
loopback rows. Add a dedicated startup-construction benchmark only if adoption
evidence shows strict validation affects application startup materially; avoid
optimizing strict-mode code without a repeatable named row.

Recommended production defaults are to keep exchange logging disabled unless an
app needs request auditing, use `METADATA_ONLY` before enabling body capture, and
leave extra Micrometer body/histogram settings disabled unless the operational
question needs them. The local audit notes live in
[`roadmaps/v12/DIAGNOSTICS_OVERHEAD_AUDIT.md`](../roadmaps/v12/DIAGNOSTICS_OVERHEAD_AUDIT.md)
and remain smoke-only evidence.

## Fairness Guardrails

The loopback comparison uses one shared scenario definition for raw `WebClient`,
Spring HTTP Interface, and the starter. The local server validates the path
variable, query parameter, and `X-Tenant` header for every request. If one client
uses different request metadata, the benchmark fails instead of recording a
misleading comparison.

Each compared client decodes the response as `ResponseEntity<BenchmarkUser>` and
checks the same status, `Content-Type`, benchmark scenario header, body-shape
header, and response body before returning the consumed body to JMH. This keeps
response work visible and prevents a benchmark from measuring only publisher
creation.

The loopback server is intentionally cheap: it returns a fixed JSON body from a
local handler and records invalid request counts plus max concurrent in-flight
requests for failure diagnostics. This reduces server work in the client-side
overhead scenario, but loopback transport, local machine variability, JVM
warmup, CPU scheduling, and Netty event-loop behavior still affect results.
Use benchmark output as trend evidence for named scenarios, not as universal
nanosecond-level claims.
