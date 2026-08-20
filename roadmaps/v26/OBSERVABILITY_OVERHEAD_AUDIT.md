# V26 Observability Overhead Audit

## Scope

This is target-only review evidence for Priority 10. It compares equivalent
no-network starter invocation rows in current `3.6.0-SNAPSHOT` and published
`3.5.0`. It is not a raw `WebClient` comparison, a transport benchmark, or a
source for public numerical performance claims.

Both runs used the same machine, JDK, Spring Boot `4.0.0`, Spring WebFlux
`7.0.1`, Reactor Netty `1.3.0`, Netty `4.2.7.Final`, and Micrometer `1.16.0`.
The baseline resolved from a fresh target-local Maven repository and passed the
published-baseline provenance verifier.

## Evidence

- Current report:
  `target/release-evidence/v26/priority10/current-3.6.0-SNAPSHOT/release-jmh.json`
- Published report:
  `target/release-evidence/v26/priority10/published-3.5.0/release-jmh.json`
- Comparison:
  `target/release-evidence/v26/priority10/current-vs-published-3.5.0.md`
- Threshold reruns:
  `target/release-evidence/v26/priority10/rerun-{current,published-3.5.0}/release-jmh.json`

The report paths are generated and intentionally remain under `target/`. The
current run identifies the dirty worktree honestly, so it must not be promoted.

## Results

| No-network row | Throughput current / baseline (ops/us) | Average current / baseline (us/op) | Sample p95 current / baseline (us/op) | Average-mode allocation current / baseline (B/op) |
| --- | ---: | ---: | ---: | ---: |
| Diagnostics disabled | 0.184 / 0.181 | 5.509 / 5.015 | 9.760 / 9.088 | 13,512 / 13,552 |
| Open-circuit rejection | 0.310 / 0.318 | 3.446 / 3.290 | 5.760 / 5.512 | 4,392 / 4,456 |
| Simple registry | 0.116 / 0.118 | 8.640 / 8.075 | 13.728 / 13.872 | 19,253 / 19,417 |
| Prometheus registry | 0.115 / 0.123 | 8.844 / 7.988 | 13.792 / 15.744 | 19,253 / 19,357 |
| Prometheus plus histogram | 0.111 / 0.118 | 8.978 / 8.433 | 14.224 / 20.032 | 19,145 / 19,193 |

The first comparison produced review flags only for lower current histogram
sample tails: p95 was `14.224` versus `20.032` us/op and p99 was `22.336`
versus `32.672` us/op. A same-machine focused rerun measured p95 at `14.320`
versus `14.912` us/op and p99 at `20.393` versus `20.896` us/op. That rerun
reduced both differences below 4%, so the original flag is treated as local tail
variance, not as a release movement.

Within the initial current run, switching from Simple to Prometheus observation
changed average time by about 2.4%. Enabling the Prometheus histogram changed
average time by about 1.5%, throughput by about -3.5%, sample p95 by about 3.1%,
and did not produce persistent allocation growth. These local values describe
the opt-in fixture's observed cost only; they are not universal estimates.

## Decision

- No current-versus-baseline allocation row crossed 15% or 4 KiB/op.
- No repeatable latency regression crossed the 20% review trigger.
- The disabled unary path is also covered structurally: it does not install
  `SubscriptionReportingState` when exchange logging, observers, and lifecycle
  hooks are inactive.
- Prometheus scrape rendering remains a separate on-demand concern. No scrape
  benchmark was added because no concrete scrape bottleneck was identified.
- No production optimization is justified by this audit.
- No report is promoted because V26 makes no public numerical performance claim.
