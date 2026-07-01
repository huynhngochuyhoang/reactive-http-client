# Benchmarks

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

- [Benchmark Report 2.11.0](benchmark-report-2.11.0.md) is release-quality
  evidence for starter `2.11.0`, generated from the `2.11.0` release benchmark
  profile. Use it only for the named scenarios and environment it records.
- [Performance Summary](23-performance-summary.md) explains how to read the
  promoted report without turning scenario-specific data into broad claims.
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
contain machine-local paths such as `/home/` or `/Users/`.

Run the same benchmark harness against the last published starter artifact by
setting `benchmark.starter.version`, enabling `benchmark-published-baseline`, and
omitting `-am` so Maven resolves the published dependency instead of the current
reactor module:

```bash
mvn -Pbenchmarks,benchmark-release,benchmark-published-baseline -pl reactive-http-client-benchmarks clean verify -Dbenchmark.starter.version=2.11.0 -Dbenchmark.commit=2.11.0
```

The example version must match the root `api.compatibility.baseline.version`
(`2.11.0` for this release line). When that property changes for the next
development cycle, update this command and the `published-starter-<version>`
report paths together.
For the V16 post-release transition, this example now uses `2.11.0` because
the reactor has been bumped to `2.12.0` and the published `2.11.0`
artifacts resolve. Move both `benchmark.starter.version` and `published-starter-<version>` paths together
again after the next release baseline changes.

That command cleans the benchmark module before compiling, uses the current
benchmark harness and current managed Spring Boot BOM, and excludes current-only
diagnostics-provider benchmarks that cannot compile against the published baseline
artifact. Its report is written under
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
- Published baseline command: `mvn -Pbenchmarks,benchmark-release,benchmark-published-baseline -pl reactive-http-client-benchmarks clean verify -Dbenchmark.starter.version=2.11.0 -Dbenchmark.commit=2.11.0`
- Current candidate report: `reactive-http-client-benchmarks/target/benchmark-reports/release-jmh.md`
- Published baseline report: `reactive-http-client-benchmarks/target/benchmark-reports/published-starter-2.11.0/release-jmh.md`
- Scenarios cited: `Get No Body`, `Post Json`
```

`DocumentationReleaseArtifactTest` also writes `target/release-evidence/reactive-http-client-benchmark-evidence.md` from the same manifest data. Use that generated target-only snippet as the release-note starting point, then paste it only after the promoted `docs/benchmark-report-<version>.md` file exists.

The promoted report link is required for public performance claims. Use the
manifest `promotedReport` value, such as `docs/benchmark-report-<version>.md`,
when citing the report from `CHANGELOG.md` or release notes. A pending
benchmark entry in the generated release evidence manifest is not enough for a
release that publishes performance wording; run the current candidate benchmark,
promote the report, and cite the promoted report from the release notes.

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
  -Dbenchmark.compare.baseline=reactive-http-client-benchmarks/target/benchmark-reports/published-starter-2.11.0/release-jmh.json
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
on-demand inspection API, not part of proxy invocation.

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
