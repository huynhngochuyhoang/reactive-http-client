# Optional Diagnostics Overhead Audit

## Scope

This audit is smoke-only evidence for request-path diagnostics overhead. It is not
release-quality benchmark data and should not be published as a universal
performance claim.

The request-path benchmarks use the no-network mock exchange so transport and
server work do not hide diagnostic allocation. The metadata logger output is
redirected to a temp file so the production logger still executes without
flooding the JMH console. The runtime diagnostics provider
is benchmarked separately because it is an on-demand query API, not part of the
request path.

## Command

```bash
java -Dorg.slf4j.simpleLogger.logFile=/tmp/reactive-http-client-diagnostics-benchmark.log -jar reactive-http-client-benchmarks/target/benchmarks.jar \
  '.*StarterDiagnosticsOverheadBenchmark.*' \
  -wi 1 -i 1 -f 1 -r 1s -w 1s -prof gc -rf json \
  -rff reactive-http-client-benchmarks/target/benchmark-reports/diagnostics-audit-jmh.json
```

## Smoke Results

Recorded locally on 2026-06-09 with the quick smoke command above.

| Benchmark | Throughput | Allocation | Notes |
| --- | ---: | ---: | --- |
| `diagnosticsDisabledGetNoBody` | 53,680 ops/s | 13,779 B/op | Default request path with no observer bean and exchange logging disabled. |
| `metadataOnlyExchangeLoggingGetNoBody` | 17,954 ops/s | 17,906 B/op | Starter-only metadata logging path using the production `DefaultHttpExchangeLogger`, no request or response body capture. |
| `micrometerObserverGetNoBody` | 22,221 ops/s | 17,714 B/op | Micrometer observer with `SimpleMeterRegistry`; records timer, attempts, and size summaries. |
| `runtimeDiagnosticsProviderClientSummaries` | 39,885 ops/s | 44,074 B/op | On-demand diagnostics-provider query, measured outside the request path. |

## Findings

Disabled diagnostics do not build `HttpExchangeLogContext` or
`HttpClientObserverEvent` instances because terminal reporting is only attached
when exchange logging, observer, or lifecycle hooks are present. The disabled
path still resolves bean providers so late observer and lifecycle registrations
remain visible, but it does not allocate the larger per-request diagnostic
payloads used by enabled exchange logging or observer recording.

Metadata-only exchange logging adds the exchange-log context and production
message-building work. It intentionally avoids request/response body capture when
`METADATA_ONLY` is selected.

Micrometer observation has measurable request-path overhead because it builds tag
sets and records one timer plus distribution summaries into the registry. Keep it
enabled when metrics are required; leave additional body logging and histogram
settings disabled unless there is an operational need.

`ReactiveHttpClientDiagnosticsProvider.clientSummaries()` is a separate query
surface. It scans bean definitions and exports sanitized effective contracts only
when called by application code; it is not wired into the proxy invocation path.
