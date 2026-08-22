# Reactive HTTP Client - Roadmap V26

> **Status:** completed - released as `3.6.0` on 2026-08-22
> **Theme:** trustworthy logical-call observability after the `3.5.0` request-boundary release
> **Delivered release:** `3.6.0`
> **Post-release development line:** `3.7.0-SNAPSHOT`
> **Published/API baseline:** `3.6.0`

## Completion Record

V26 shipped as `3.6.0` from tag `v3.6.0` at commit
`466c59f271880c37e9365f817376c6b595484fd2`. The parent, starter, test-helper,
and OTel release bundle and the assembled Boot 4 consumer were verified from
Maven Central. Public coordinates and API, consumer, and benchmark baselines
moved to `3.6.0`; reactor-only development moved to `3.7.0-SNAPSHOT`. V26 made
no promoted numerical benchmark claim.

## Starting State

V25 shipped `3.5.0` and moved the reactor to `3.6.0-SNAPSHOT`. The published
parent, starter, test-helper, and OTel artifacts plus an assembled Boot 4
consumer have been verified from Maven Central. Public dependency examples and
API, consumer, and benchmark baselines now use `3.5.0`.

The transport and declarative contracts are mature, but a fresh observability
audit found one concrete duration defect and several documentation mismatches:

- `SubscriptionReportingState.start` defaults to zero and is initialized only
  when the request-attempt publisher is subscribed. An open circuit breaker can
  reject outside that publisher, so terminal reporting subtracts zero from the
  current epoch time. The main Micrometer timer, optional latency timer,
  exchange log, and OTel span can then report an epoch-sized duration for an
  immediate rejection.
- Rate-limiter or bulkhead admission can also terminate before an HTTP attempt.
  Their wait/rejection time is not consistently included even though the timer
  is documented as end-to-end logical-call duration.
- The attempts summary can record `0` when resilience rejects before the first
  request subscription. A value of `1` means one subscription attempt, not that
  the call succeeded. `docs/08-observability.md` currently states otherwise.
- The attempts `DistributionSummary` is registered without client-side
  percentiles, a percentile histogram, or SLO buckets. It therefore does not
  expose the p95 claimed by the guide. Adding `publishPercentiles(0.95, 0.99)`
  would create per-instance, non-aggregable quantiles, so that should not become
  the default without an explicit operational and overhead decision.
- The Micrometer observer always records the main timer and attempts summary,
  records request/response size only when measurable, and adds the latency timer
  only when enabled. The guide's "four meters per exchange" wording is too
  broad.
- Health details include `poolAcquireFailureCount`, but the observability guide's
  field inventory and sample response omit it.
- The built-in OTel observer emits one terminal span per logical client call,
  not one span per retry, redirect, auth replay, or wire exchange. It emits no
  request/response body span events. The `log-request-body` and
  `log-response-body` flags currently make bodies available on the observer
  event, while the guide and metadata describe them as built-in span events.
- String request-size measurement always uses UTF-8, while an explicit outbound
  `Content-Type` can select another charset. Calling that value the wire-aligned
  application byte count is therefore not always correct.

The same audit confirmed that API-name precedence, default cardinality gates,
status/outcome/error tags, advertised response-size behavior, histogram tag
shape and SLO boundaries, the `rhttpclients` endpoint ID and sanitization,
protocol-aware pool gauge names, and OTel propagation switches match the current
implementation.

V26 should repair timing at its source, define one metric and span vocabulary,
and add scrape-level evidence. It should not add another observability backend
or expose unbounded request data.

## Release Direction

| Delivered V26 scope | Release direction |
|---|---|
| Timing fix, tests, and documentation alignment only | `3.5.x` or include in `3.6.0` |
| Backward-compatible metric, diagnostic, or test-helper addition | `3.6.0` |
| Existing metric/tag removal, semantic rename, or diagnostics schema break | Defer to a future major |

Keep the reactor on `3.6.0-SNAPSHOT` and public consumer examples on published
`3.5.0` until release preparation selects the final V26 scope.

## Goals

1. Make every terminal duration finite, non-negative, monotonic, and scoped to
   one subscription of the logical client call.
2. Define resilience admission, subscription attempt, hidden replay, wire
   dispatch, response-envelope, and streaming-body timing boundaries explicitly.
3. Keep Micrometer, OTel, lifecycle, exchange logging, health, diagnostics,
   mocks, and assembled consumers on the same terminal facts.
4. Give operators correct Prometheus units, timer-maximum behavior, histogram
   queries, and zero-attempt interpretation.
5. Align body and byte-size properties with behavior without exporting secrets,
   raw URLs, or unbounded payloads.
6. Preserve API, AOT, native, dependency, benchmark, and release-evidence
   discipline against published `3.5.0`.

## Non-Goals

- Do not add a third observability backend or replace Micrometer/OTel with a new
  telemetry abstraction.
- Do not add per-attempt or per-wire-dispatch spans by default. That would change
  volume and cardinality and needs a separate proposal.
- Do not put exception messages, stack traces, request/response bodies, raw URLs,
  header values, credentials, or remote addresses into default metric tags.
- Do not cap valid slow calls at an arbitrary duration merely to hide the
  epoch-sized timing bug.
- Do not derive body sizes by consuming streaming bodies or serializing POJOs a
  second time solely for metrics.
- Do not change established `ErrorCategory`, `HttpClientFailureStage`,
  diagnostics schema v1, or subscription-attempt semantics silently.
- Do not change Resilience4j operator ordering as part of the timing fix.
- Do not promote benchmark numbers from smoke runs, dirty commits, or local
  unpublished baselines.

---

## 1. Post-`3.5.0` Baseline and V26 Scope Integrity

Keep the active snapshot, published baseline, roadmap archive, and generated
release evidence on one timeline before observability behavior changes.

**Acceptance:**

- Parent, starter, test-helper, and OTel `3.5.0` artifacts resolve from fresh
  Central-only repositories with remote markers and checksums.
- Root and module-scoped japicmp compare `3.6.0-SNAPSHOT` against published
  `3.5.0`; same-version, mixed-version, and locally contaminated baselines fail.
- Public dependency snippets stay on `3.5.0`; reactor-only consumer and native
  fixtures stay on `3.6.0-SNAPSHOT`.
- `roadmaps/README.md` records V26 as the only active draft without rewriting
  completed V1-V25 evidence.
- Release readiness reports snapshot development and a deferred candidate until
  the V26 scope and evidence select a release.

## 2. Logical-Call Duration Foundation

Fix the epoch-sized maximum at the subscription boundary rather than masking it
in Micrometer.

**Acceptance:**

- Start timing exactly once for each subscription to the public returned
  `Mono`/`Flux`, before logical timeout and resilience admission can terminate.
- Measure elapsed time with a monotonic source. Wall-clock changes cannot produce
  negative, epoch-sized, or inflated duration values.
- Keep any epoch timestamp needed by OTel separate from elapsed-time state; derive
  a valid span start/end pair from the same terminal duration.
- Open-circuit, rate-limit, bulkhead, cancellation, auth, serialization, and
  custom-filter failures before the first request attempt produce a small valid
  duration with `attemptCount=0`.
- Retry delay and outer resilience admission time are included once in the
  logical-call duration. Per-attempt response timeout behavior remains unchanged.
- `Mono<T>` and direct `Flux<T>` retain full terminal timing. A
  `Mono<ResponseEntity<Flux<DataBuffer>>>` still reports response-envelope timing,
  not later inner-body consumption.
- Observer, exchange-log, Micrometer, and OTel duration assertions use a bounded
  tolerance and prove they came from one terminal snapshot. Lifecycle callbacks
  retain the same one-terminal boundary without gaining a duration field.

## 3. Micrometer Timer and Export Contract

Freeze what the primary timer and optional histogram mean at registry and scrape
boundaries.

**Acceptance:**

- A deterministic open-circuit regression test proves the main timer and optional
  latency timer never record epoch-sized values.
- Circuit-breaker, rate-limiter, and bulkhead pre-subscription rejections record
  `http.status_code=NONE`, `outcome=UNKNOWN`, the concrete exception,
  `error.category=RESILIENCE_ERROR`, no invented transport stage, and zero
  subscription attempts.
- Prometheus-compatible tests verify timer `_count`, `_sum`, and `_max` use the
  registry's seconds base unit even though the public event carries milliseconds.
- Document Micrometer time-window maximum behavior, including that `_max` is not
  an all-time maximum and can reset after its expiry window.
- Main timer, attempts summary, conditional size summaries, and opt-in latency
  timer are inventoried individually. Documentation does not promise every meter
  for every event.
- Remove the attempts-summary p95 claim from the default contract unless V26
  supplies matching distribution statistics. If percentile analysis remains a
  requirement, prefer an opt-in, aggregable histogram with bounded integer
  attempt buckets and document its memory/time-series cost; do not silently add
  per-instance `publishPercentiles(0.95, 0.99)`.
- Scrape-level tests prove exactly which attempts series are present by default
  and, if an opt-in histogram is added, verify its buckets and
  `histogram_quantile` query across multiple instances.
- Histogram SLO buckets remain low-cardinality and are not confused with the main
  timer's status/error-tagged series.

## 4. Resilience Admission and Attempt Semantics

Make the operator boundary understandable without turning attempts into a wire
request count.

**Acceptance:**

- Document `attemptCount=0` as rejection before source subscription, `1` as one
  subscription regardless of success, and `>1` as retry resubscription.
- Real composition tests cover open circuit, exhausted rate limiter, saturated
  bulkhead with zero wait, and bulkhead/rate-limiter admission delay.
- Retry, one-time auth replay, and redirect dispatch retain their established
  count boundaries. No metric claims that attempts equal downstream requests.
- Prior-attempt URL, status, headers, error, failure stage, and timing evidence
  cannot leak into an outer resilience rejection.
- Resilience4j's own `resilience4j.*` meters remain distinct from the starter's
  one-record-per-logical-call metrics and are documented for per-operator state.

## 5. Request and Response Size Semantics

Keep byte metrics cheap while making their representation boundary truthful.

**Acceptance:**

- `byte[]`, null, and String request-size behavior is tested against actual wire
  bytes for UTF-8 and an explicit non-UTF-8 `Content-Type` charset.
- Either use the outbound charset for String measurement or explicitly expose the
  value as a UTF-8 estimate; do not label mismatched bytes as wire-aligned.
- POJO, publisher, resource, multipart, and streaming bodies remain unknown unless
  bytes already exist on the normal request path. No metrics-only buffering or
  serialization is introduced.
- Response size continues to mean surviving advertised `Content-Length` after
  transport processing, not decoded bytes consumed by the application.
- Compression, chunking, malformed lengths, streaming cancellation, and
  `ResponseEntity` envelopes retain explicit unknown/skip behavior.
- Micrometer and OTel either publish the same byte value or both omit it.

## 6. Health Indicator Metric Parity

Keep the health contributor aligned with the metric schema it aggregates.

**Acceptance:**

- Document and test `poolAcquireFailureCount` in each client detail and in the
  sample Actuator response.
- Prove health uses timer counts and error/failure-stage tags only; a timer's max,
  sum, or histogram buckets cannot mark a client DOWN by themselves.
- Probe-to-probe deltas remain correct across registry reset, meter removal and
  recreation, custom metric names, zero samples, and multiple tagged series for
  one client.
- The detail field set, bounds, statuses, and reasons match
  `Boot4HttpClientHealthIndicator` and support-bundle fixtures.
- Health remains unavailable when required Actuator/MeterRegistry conditions are
  absent and remains replaceable by the documented bean name.

## 7. OpenTelemetry Logical-Call Contract

Align span timing and wording with the terminal observer model.

**Acceptance:**

- The built-in observer is documented as emitting one terminal `CLIENT` span per
  logical client call, not one span per wire exchange or retry attempt.
- Open-circuit and other zero-attempt failures produce current, finite span
  timestamps with the same elapsed duration as Micrometer and exchange logging.
- Span status, `error.type`, `rhttp.failure.stage`, attempt count, server-address
  gates, URL-template gates, and byte attributes remain aligned with
  `HttpClientObserverEvent`.
- The built-in OTel observer continues to exclude exception messages, stack traces,
  headers, raw URLs, and request/response bodies from span events.
- Define `log-request-body` and `log-response-body` truthfully: either document
  them as observer-event payload gates for custom observers or implement a
  separately reviewed bounded/redacted OTel contract. Do not silently export raw
  bodies.
- Inbound extraction, outbound injection, caller-header precedence, and the
  master/child OTel switches retain their existing behavior.

## 8. Observability Documentation and Dashboard Recipes

Replace the current drift with one operationally useful reference.

**Acceptance:**

- Reconcile every statement in `docs/08-observability.md` with source and focused
  tests, including meter conditions, tag values, timing boundary, attempts,
  health fields, diagnostics, pool gauges, OTel spans, and propagation.
- Add PromQL examples for request rate, error ratio, zero-attempt resilience
  rejection rate, p95/p99 from the opt-in histogram, and pool pressure.
- Every duration query states its unit. Examples do not multiply seconds as
  milliseconds or interpret `_max` as a permanent lifetime maximum.
- Distinguish starter logical-call metrics, Resilience4j operator metrics, Reactor
  Netty transport metrics, and OTel spans in one table.
- Cross-check related guidance in error handling, resilience, cardinality,
  support bundles, production checklist, generated configuration reference, and
  operations troubleshooting.
- Documentation tests fail when the attempts wording, health sample fields,
  p95 availability, body-event claim, or timer-unit guidance drifts again.

## 9. Mock, Consumer, and Support-Bundle Parity

Prove the corrected terminal facts outside focused unit tests.

**Acceptance:**

- `MockReactiveHttpClient` can exercise open-circuit/pre-subscription rejection
  with observer, lifecycle, and exchange-log assertions where resilience support
  is configured.
- Mock evidence is labeled as no-network and does not claim Prometheus export,
  Reactor Netty pool behavior, or actual wire dispatch.
- The assembled Boot 4 consumer scrapes or inspects the main timer for success,
  HTTP error, and open-circuit calls and verifies bounded duration plus attempts.
- The consumer verifies the corrected health detail field set and OTel span
  timing without loading reactor classes from the workspace.
- Support-bundle fixtures include units and enough structural tags to distinguish
  a fast resilience rejection from a slow transport request without including
  sensitive values.

## 10. Observability Overhead Re-Audit

Ensure timing correctness does not add unnecessary work to clients that do not
use diagnostics.

**Acceptance:**

- Re-run the no-network invocation and Micrometer observer rows against published
  `3.5.0` under equivalent dependencies and environment metadata.
- Add a focused pre-subscription rejection benchmark only if it answers a real
  allocation or latency question; do not compare it to raw WebClient as an
  equivalent network scenario.
- Confirm the diagnostics-disabled unary path does not allocate subscription
  reporting state solely for a feature that is inactive.
- Treat thresholds as review triggers, not hard release gates. Investigate and
  explain material allocation or latency movement before optimization.
- Promote numerical claims only from clean, versioned release-quality reports.

## 11. Dependency, API, AOT, and Native Evidence

Keep the observability repair compatible with the supported Boot 4 line.

**Acceptance:**

- Review Spring Boot, Spring Framework, Reactor Netty, Micrometer, Resilience4j,
  OTel, Jackson, and native-build baselines without mixing generations.
- Strict japicmp against published `3.5.0` covers any touched public observer,
  event, health, diagnostics, and test-helper types.
- Prefer internal timing changes. Any additive public API is documented, tested,
  and included in compatibility filters before release.
- AOT/native smoke covers Micrometer auto-configuration, the optional diagnostics
  endpoint, inherited clients, and the corrected zero-attempt terminal path
  without reflection fallbacks.
- Current and published assembled-consumer evidence uses isolated repositories
  and records exact artifact provenance.

## 12. V26 Release Go/No-Go

Select a release only after the duration defect and documentation drift are
closed with immutable evidence.

**Acceptance:**

- Select patch versus minor scope from the actual delivered API/configuration
  surface, not from the roadmap label alone.
- Changelog and release notes name the epoch-duration defect, affected terminal
  surfaces, corrected timing boundary, and any documentation-only corrections.
- Clean tests cover open circuit, rate limiter, bulkhead, retry delay, cancellation,
  OTel timing, Prometheus units, health details, mock parity, and assembled
  consumer behavior.
- Dependency matrix, strict API compatibility, AOT/native, consumer, packaging,
  documentation-link, metadata, and benchmark-evidence checks pass from a clean
  commit.
- A go decision records candidate version, immutable commit/tag, Central artifact
  provenance, and post-release baseline transition. A no-go decision leaves the
  reactor on a snapshot and records blockers without publishing partial claims.

## Definition of Done

V26 is complete when:

1. No pre-subscription terminal path can emit a zero-origin, negative, or
   epoch-sized duration.
2. Logical-call duration is identical across Micrometer, OTel, exchange logging,
   mocks, and consumers; lifecycle callbacks share the same terminal boundary
   and subscription-attempt semantics without exposing a duration field.
3. `docs/08-observability.md` and related generated metadata describe what the
   current implementation actually emits, including conditional meters, health
   fields, body gates, units, and timer maximum behavior.
4. Operators have bounded, unit-correct queries and support evidence for
   distinguishing resilience rejection, transport failure, and downstream HTTP
   failure.
5. API, dependency, AOT/native, consumer, benchmark, and release evidence is
   reproducible against published `3.5.0`.
6. The selected release is published and verified, or V26 closes with an explicit
   no-go record and no misleading release claim.
