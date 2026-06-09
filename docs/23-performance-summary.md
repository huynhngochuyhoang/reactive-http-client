# Performance Summary

This page summarizes the current promoted release-quality benchmark report:
[Benchmark Report 2.9.0](benchmark-report-2.9.0.md). The numbers are evidence
for starter `2.9.0` under the report's local loopback environment only. They are
not a general claim about every application, payload, network, JVM, or deployment.

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

The promoted `2.9.0` report contains these average-time loopback rows:

| Scenario | Starter avg | Scope |
|---|---:|---|
| `Get No Body` | 75.746 us/op | Default success path with no request body. |
| `Get Path Query Header` | 112.547 us/op | Default success path with path, query, and header argument resolution. |
| `Post Json` | 154.017 us/op | Default JSON request/response path. |
| `Response Entity` | 128.981 us/op | Default `Mono<ResponseEntity<T>>` envelope path. |
| `Client Error Small Body` | 80.371 us/op | Default bounded 4xx error path. |
| `Server Error Small Body` | 91.668 us/op | Default bounded 5xx error path. |

In this run, the starter default path is close to Spring HTTP Interface for
`Get No Body` and `Response Entity`, slower than raw `WebClient` on success-path
rows that do additional declarative work, and faster than both baselines on the
small bounded error rows. Treat those as scenario-specific observations from the
named report, not as universal ordering.

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
| `Exchange Logging Metadata Only Get No Body` | 84.506 us/op | Metadata-only exchange logging enabled. |
| `Micrometer Observer Get No Body` | 97.935 us/op | Micrometer observer enabled. |
| `Retry Wrapper Get No Body` | 122.822 us/op | Retry wrapper enabled for the request. |
| `Circuit Breaker Wrapper Get No Body` | 80.893 us/op | Circuit-breaker wrapper enabled for the request. |

These are starter optional-feature overhead rows. Do not compare them to raw
`WebClient` unless the raw baseline performs equivalent logging, observation, or
resilience work.

## Starter Error Mapping

`Problem Detail Small Body` is labeled starter-only error-mapping overhead in the
promoted report. Its average-time row is 124.544 us/op for a small
`application/problem+json` response. Raw `WebClient` and Spring HTTP Interface
do not provide the same Problem Detail mapper in this harness, so this row is
not a raw-client parity comparison.

## Version Scope

The available promoted report measures starter `2.9.0`. If a future release
changes request construction, JSON body handling, response envelope handling,
observability, resilience wrapping, transport, or client-builder behavior, refresh
the current-workspace release benchmark before making current-release performance
claims.

A published-baseline report generated with `benchmark.starter.version=2.8.0`
measures the published `2.8.0` artifact, not the current workspace. When
comparing a current candidate to that baseline, keep the reports separate and
name both versions. Do not reuse the `2.9.0` report as evidence for a different
starter version.
