# V12 Invocation Overhead Audit

This audit records local smoke evidence for Priority 5. The numbers below are
not release-quality benchmark claims and should not be promoted into public
performance documentation. They are only used to justify small request-path
changes and to keep future audits repeatable.

## Command

```bash
java -jar reactive-http-client-benchmarks/target/benchmarks.jar \
  '.*(StarterInvocationBenchmark|StarterInvocationInternalsBenchmark).*' \
  -wi 1 -i 1 -f 1 -r 1s -w 1s -prof gc -rf json \
  -rff reactive-http-client-benchmarks/target/benchmark-reports/invocation-audit-after-jmh.json
```

The before run used the same settings against the pre-change
`StarterInvocationBenchmark.*` rows and wrote
`target/benchmark-reports/invocation-audit-before-jmh.json`.

## Hot Spots

| Path | Evidence | Finding |
| --- | --- | --- |
| Cached metadata lookup | `cachedMethodMetadataLookup`: 112,111,264 ops/s, 16 B/op | Cache lookup is not a meaningful per-call allocation source in this smoke run. |
| Cached request-plan lookup | `cachedRequestPlanLookup`: 109,626,490 ops/s, 16 B/op | Request planning is already cached; steady-state lookup is not a hotspot. |
| Scalar path/query/header resolution | `argumentResolutionPathQueryHeaderFromPlan`: 4,233,803 ops/s, 984 B/op | Argument resolution is the main isolated invocation allocation source measured here. |
| Proxy publisher creation | `proxyInvocationCreatesPublisher`: 2,307,654 ops/s, 1712 B/op after the change | Full publisher creation adds Reactor/WebClient setup allocations beyond raw argument resolution. |
| Mock exchange | `proxyInvocationWithMockExchange`: 29,380 ops/s, 16054 B/op after the change | Mock exchange is dominated by WebClient, response decoding, and Reactor subscription work. |

## Optimization Applied

Scalar `@HeaderParam` resolution now validates and returns a singleton list for a
single header value instead of allocating a mutable `ArrayList` that is copied
again when `ResolvedArgs` is created. Collection and array header values still
use mutable lists so null filtering and validation semantics stay unchanged.

## Before / After Smoke Evidence

| Benchmark | Before | After | Delta |
| --- | ---: | ---: | ---: |
| `proxyInvocationCreatesPublisher` throughput | 2,251,061 ops/s | 2,307,654 ops/s | +2.5% |
| `proxyInvocationCreatesPublisher` allocation | 1848 B/op | 1712 B/op | -136 B/op |
| `proxyInvocationWithMockExchange` throughput | 33,921 ops/s | 29,380 ops/s | noisy; not treated as an optimization signal |
| `proxyInvocationWithMockExchange` allocation | 16334 B/op | 16054 B/op | -280 B/op |

The throughput rows are one-iteration smoke measurements and are expected to be
noisy. The allocation reduction in the publisher-creation path is the measured
signal used for this small optimization.

## Contract Check

The change preserves existing header validation and multi-value behavior:

- scalar header values are still converted with `String.valueOf` and rejected for
  CRLF/control characters;
- null scalar header values are still omitted;
- collection and array values are still flattened, null items are skipped, and
  every emitted value is validated.
