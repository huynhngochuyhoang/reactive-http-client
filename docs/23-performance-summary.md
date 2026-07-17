# Performance Summary

This page summarizes the current promoted release-quality benchmark report:
[Benchmark Report 2.12.0](benchmark-report-2.12.0.md). The numbers are evidence
for starter `2.12.0` under the report's local loopback environment only. They are
not a general claim about every application, payload, network, JVM, or deployment.

For production latency investigation, use the [Performance Troubleshooting](25-performance-troubleshooting.md)
guide before comparing an application workload with benchmark rows.

## Methodology First

The benchmark harness measures named scenarios before making any performance
claim. Release-quality reports run a cheap local loopback server and align raw
`WebClient`, Spring HTTP Interface, and starter clients on the same request
shape, transport, codecs, response decoding, and body consumption where the
scenario is comparable.

Quick benchmark output is smoke-only. It proves the harness starts, compiles, and
writes files; it is not performance evidence and should not be cited in release
notes or public documentation.

## Compared Surfaces

| Surface | What the report uses it for |
|---|---|
| Raw `WebClient` | Baseline for client-side loopback overhead when the raw client performs equivalent request and response work. |
| Spring HTTP Interface | Framework proxy comparison for supported loopback scenarios with equivalent request and response work. |
| Starter default path | Declarative starter client with default diagnostics, resilience, and optional feature work disabled. |
| Starter optional features | Starter-only rows with one optional feature enabled at a time. These rows are not default-path overhead. |
| Starter error mapping | Starter-only `application/problem+json` mapping overhead because the baselines do not install the same mapper. |

## Current Findings

The promoted `2.12.0` report contains these average-time loopback rows:

| Scenario | Starter avg | Scope |
|---|---:|---|
| `Get No Body` | 46.85 us/op | Default success path with no request body. |
| `Get Path Query Header` | 53.029 us/op | Default success path with path, query, and header argument resolution. |
| `Post Json` | 61.091 us/op | Default JSON request/response path. |
| `Response Entity` | 52.298 us/op | Default `Mono<ResponseEntity<T>>` envelope path. |
| `Client Error Small Body` | 53.238 us/op | Default bounded 4xx error path. |
| `Server Error Small Body` | 52.489 us/op | Default bounded 5xx error path. |

In this run, the starter default path is faster than both baselines for the
small bounded error rows, faster than raw `WebClient` but slower than Spring HTTP
Interface for `Post Json`, faster than Spring HTTP Interface but slower than raw
`WebClient` for `Get Path Query Header` and `Response Entity`, and slower than
both baselines for `Get No Body`. Treat those as scenario-specific observations
from the named report, not as universal ordering.

## Expected Overhead Sources

Starter overhead mainly comes from work that raw `WebClient` code usually writes
manually or does not perform in the same place:

- Proxy dispatch from the interface method to the invocation handler.
- Cached method metadata and request-plan lookup.
- Annotation argument resolution for path variables, query parameters, headers,
  body parameters, and method-level policy.
- Diagnostics hook checks for observers, lifecycle hooks, and exchange logging.
- Resilience wrapper selection when a feature is enabled and available.
- Response envelope handling for `ResponseEntity` return types.

The no-network invocation rows in the promoted report help separate this
request-planning work from loopback transport and codec work.

## Optional Feature Rows

Optional feature rows enable one starter feature at a time:

| Scenario | Average | Meaning |
|---|---:|---|
| `Exchange Logging Metadata Only Get No Body` | 51.469 us/op | Metadata-only exchange logging enabled. |
| `Micrometer Observer Get No Body` | 56.632 us/op | Micrometer observer enabled. |
| `Retry Wrapper Get No Body` | 51.681 us/op | Retry wrapper enabled for the request. |
| `Circuit Breaker Wrapper Get No Body` | 51.214 us/op | Circuit-breaker wrapper enabled for the request. |

These are starter optional-feature overhead rows. Do not compare them to raw
`WebClient` unless the raw baseline performs equivalent logging, observation, or
resilience work.

## Diagnostics and Strict-Mode Audit

The V17 audit did not add new benchmark methods. The promoted `2.12.0` report
already contains named no-network rows for disabled diagnostics, metadata-only
exchange logging, Micrometer observation, one or multiple observers, one or
multiple lifecycle hooks, and `runtimeDiagnosticsProviderClientSummaries`. Those
rows are labeled `No-network starter invocation` in raw results and stay out of
raw `WebClient` or Spring HTTP Interface comparison tables.

Interpret diagnostics costs in separate buckets:

- Request-path diagnostics rows isolate proxy invocation with no network I/O.
- Runtime diagnostics provider rows isolate on-demand support inspection.
- Actuator diagnostics endpoint JSON rendering is support-path work and would
  need a separate endpoint-rendering benchmark if adoption feedback shows it is a
  bottleneck.
- Strict unsafe-retry validation and strict built-in SigV4 body-signing
  validation run during startup/proxy construction. They do not add per-request
  strict-validation work after startup, so no request-path optimization was
  attempted without a dedicated startup benchmark row.

## Starter Error Mapping

`Problem Detail Small Body` is labeled starter-only error-mapping overhead in the
promoted report. Its average-time row is 60.852 us/op for a small
`application/problem+json` response. Raw `WebClient` and Spring HTTP Interface
do not provide the same Problem Detail mapper in this harness, so this row is
not a raw-client parity comparison.

## Version Scope

The available promoted report measures starter `2.12.0`. If a future release
changes request construction, JSON body handling, response envelope handling,
observability, resilience wrapping, transport, or client-builder behavior, refresh
the current-workspace release benchmark before making current-release performance
claims.

A published-baseline report generated with `benchmark.starter.version=3.1.0`
measures the published `3.1.0` artifact, not the current workspace. When
comparing a current candidate to that baseline, keep the reports separate and
name both versions. Do not reuse the `2.12.0` report as evidence for a different
starter version.

Current-vs-baseline comparisons should use the paired report paths from the
release evidence manifest: the current candidate report under
`reactive-http-client-benchmarks/target/benchmark-reports/release-jmh.md` and
the published baseline report under
`reactive-http-client-benchmarks/target/benchmark-reports/published-starter-3.1.0/release-jmh.md`.
Resolve the published baseline artifacts before promoting a comparison, and cite
only a source-controlled promoted report from public release notes.

Use benchmark thresholds as review triggers, not hard gates. A release-quality
comparison should prompt investigation when a named scenario moves about 20% in
latency percentiles, grows about 15% in allocation, adds more than 4 KiB/op
without an intentional request-shape change, or when an optional-feature row
jumps about 25%. Rerun the current and baseline methods on the same machine
before treating that movement as a trend, then either optimize the changed path
or document why the cost is expected.
