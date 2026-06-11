# Roadmap V13 Execution Checklist

> Companion to [`ROADMAP.md`](ROADMAP.md). This file tracks execution;
> keep rationale and design discussion in the roadmap.

---

## Priority 1 — Promote a Versioned Benchmark Report

### [x] 1.1 Promote a versioned benchmark report
- [x] Run the current-workspace release-quality benchmark for the candidate
      version.
- [x] Promote the selected Markdown report into `docs/` with a versioned
      filename such as `docs/benchmark-report-2.10.0.md`.
- [x] Keep generated JSON and environment metadata referenced in release
      evidence without committing `target/` artifacts.
- [x] Add report metadata for report version, starter version under test,
      benchmark commit, Java version, Spring Boot version, and machine limits.
- [x] Link the promoted report from `docs/22-benchmarks.md`, `README.md`, and
      the changelog only after the report is source-controlled.
- [x] Verify the report states that it is release-quality evidence, not smoke
      evidence.
- [x] Verify public docs link only the promoted report, never
      `target/benchmark-reports`.
- [x] Verify documentation tests fail if the promoted report link becomes stale.

---

## Priority 2 — Add Performance Summary Documentation

### [x] 1.2 Add performance summary documentation
- [x] Add `docs/23-performance-summary.md` or an equivalent public documentation
      section.
- [x] Link the performance summary from `docs/22-benchmarks.md`.
- [x] Explain benchmark methodology before publishing numbers.
- [x] Summarize raw `WebClient`, Spring HTTP Interface, starter default path,
      starter optional features, and starter-only error mapping separately.
- [x] Summarize measured findings using named scenarios, not broad claims.
- [x] Explain expected starter overhead sources: proxy dispatch,
      metadata/request planning, annotation argument resolution, diagnostics
      hooks, resilience wrappers, and response envelope handling.
- [x] State when the available report measures starter `2.8.0` and when
      current-release claims require a refreshed current-workspace report.
- [x] Verify optional feature overhead is separated from default-path overhead.
- [x] Verify Problem Detail mapping is labeled as starter-only error-mapping
      overhead.
- [x] Verify quick benchmark output is not described as performance evidence.

---

## Priority 3 — Release-Note Benchmark Evidence Workflow

### [x] 1.3 Release-note benchmark evidence workflow
- [x] Extend release evidence or changelog guidance with a benchmark evidence
      block.
- [x] Include promoted report link, current-workspace command,
      published-baseline command, and scenario names used by release notes.
- [x] Require a promoted report link when the changelog includes performance
      claims.
- [x] Include both current candidate and published-baseline report paths for
      release-to-release trend comparisons.
- [x] Document how to rerun only the benchmark scenarios cited by release notes.
- [x] Verify release notes can cite the promoted report without manual path
      rewriting.
- [x] Verify release evidence distinguishes current candidate and published
      baseline reports.
- [x] Verify a release with performance claims cannot rely only on a pending
      benchmark evidence entry.

---

## Priority 4 — Current-vs-Baseline Report Pairing

### [x] 3.1 Current-vs-baseline report pairing
- [x] Ensure current candidate and published-baseline report paths are distinct
      in release evidence.
- [x] Ensure current candidate and published-baseline report paths are distinct
      in public documentation.
- [x] Add a generated or manual comparison note that names the starter versions
      being compared.
- [x] Make baseline artifact resolution failure visible before report promotion.
- [x] Verify promoted docs do not mix current candidate numbers with published
      baseline labels.
- [x] Verify current and baseline reports can coexist under stable paths.
- [x] Verify docs tests catch stale or mismatched promoted report links.

---

## Priority 5 — Optimize Default Success-Path Overhead

### [x] 2.1 Optimize default success-path overhead
- [x] Refresh the current-workspace release benchmark before changing request
      path code.
- [x] Use no-network invocation rows to identify allocation and CPU contributors
      in proxy invocation, request planning, argument resolution, and response
      handling.
- [x] Inspect redundant immutable map/list copying during request argument
      resolution.
- [x] Inspect repeated header, query, and path expansion work after
      `RequestPlan` lookup.
- [x] Inspect per-subscription state allocation on success paths that do not use
      retry, idempotency, lifecycle hooks, exchange logging, or observers.
- [x] Inspect response envelope mapping overhead for `Mono<ResponseEntity<T>>`.
- [x] Apply only simple default-path improvements that preserve public behavior.
- [x] Record before/after JMH rows for every changed scenario.
- [x] Verify behavior tests pass unchanged.
- [x] Verify diagnostics, lifecycle, retry, idempotency, and validation contracts
      remain covered by tests.
- [x] Document improvements as scenario-specific, not universal parity.

Evidence:
- Pre-change current-workspace rows came from the promoted 2.9.0 report:
  `proxyInvocationCreatesPublisher` `2193116.621 ops/s`, `1712.001 B/op`;
  `proxyInvocationWithMockExchange` `110578.649 ops/s`, `15248.011 B/op`.
- Post-change focused release-profile report:
  `reactive-http-client-benchmarks/target/benchmark-reports/v13-priority5/release-jmh.md`.
  It measured `proxyInvocationCreatesPublisher` at `2362733.313 ops/s`,
  `1600.001 B/op`, and `proxyInvocationWithMockExchange` at
  `137029.961 ops/s`, `13932.008 B/op`.
- Scope: default unauthenticated success path only. Calls using auth, resilience,
  generated idempotency keys, lifecycle hooks, exchange logging, observers, or
  timeout request customization stay on the existing stateful path.

---

## Priority 6 — Optimize JSON Request/Response Paths

### [x] 2.2 Optimize JSON request/response paths
- [x] Audit JSON body preparation for default unauthenticated `POST` calls.
- [x] Audit auth serialization bypasses and confirm they are not used on the
      default unauthenticated path.
- [x] Audit content-type handling and request-body publisher creation.
- [x] Verify scalar DTO bodies and publisher DTO bodies still use the correct
      encoder.
- [x] Avoid pre-serializing bodies unless auth/signing or retry body replay
      requires it.
- [x] Add a focused benchmark or JMH grouping if `Post Json` cannot isolate the
      overhead source.
- [x] Record `Post Json` before/after numbers.
- [x] Verify raw `WebClient` and Spring HTTP Interface baselines still perform
      equivalent request and response JSON work.
- [x] Verify auth signing, publisher bodies, and non-repeatable body protections
      remain covered.
- [x] Verify no broad JSON performance claim is published without the promoted
      report.

Evidence:
- Audit result: default unauthenticated `POST` JSON already bypasses auth
  serialization and does not pre-serialize the body; scalar DTO requests still
  use WebClient `bodyValue`, and publisher DTO requests keep the JSON default
  content type.
- Code change: simple concrete response DTOs now use WebClient class-based
  `bodyToMono`/`bodyToFlux` decoding; generic response types keep the existing
  `ParameterizedTypeReference` path.
- Pre-change promoted 2.9.0 report row for `clientSideOverheadStarterPostJson`:
  `154.017 us/op` average time and `32063.831 B/op`.
- Post-change focused release-profile report:
  `reactive-http-client-benchmarks/target/benchmark-reports/v13-priority6/release-jmh.md`.
  It measured `clientSideOverheadStarterPostJson` at `199.255 us/op` average
  time with high local variance, `71.666 us/op` sample mean, and
  `27500.946 B/op` in the sample row.
- Scope: this is allocation-focused JSON response decode cleanup for simple DTO
  responses. No broad JSON latency claim is published without a promoted full
  benchmark report.

---

## Priority 7 — Optimize `ResponseEntity` Envelope Handling

### [x] 2.3 Optimize `ResponseEntity` envelope handling
- [x] Audit response-header copying and status/body envelope construction.
- [x] Avoid duplicate response metadata snapshots when observers, lifecycle
      hooks, and exchange logging are disabled.
- [x] Keep final outbound request diagnostics intact.
- [x] Keep streaming ownership semantics intact.
- [x] Measure `Mono<ResponseEntity<T>>` separately from streaming
      `ResponseEntity<Flux<DataBuffer>>`.
- [x] Record `Response Entity` before/after numbers.
- [x] Verify streaming response ownership tests still pass.
- [x] Verify observer and exchange-log contexts still receive documented
      metadata.
- [x] Verify no optimization releases a body before the consumer owns it.

Evidence:
- Code change: non-streaming `Mono<ResponseEntity<T>>` now delegates envelope
  construction to `ClientResponse.toEntity(...)`; `Mono<ResponseEntity<Void>>`
  delegates to `toBodilessEntity()`. Generic body types still use
  `ParameterizedTypeReference`, and `Mono<ResponseEntity<Flux<DataBuffer>>>`
  stays on the streaming ownership path introduced in V9.
- Stateless default calls still avoid subscription reporting state when observer,
  lifecycle hooks, exchange logging, auth, resilience, and generated idempotency
  keys are inactive; stateful diagnostics still capture response status/headers
  before decoding.
- Tests: `mvn -pl reactive-http-client-starter -Dtest=ResponseEntitySupportTest,StreamingResponseTest,SubscriptionLocalReportingStateTest test`
  passed 22 tests.
- Tests: `mvn -pl reactive-http-client-starter -Dtest=DiagnosticContextContractTest,ExchangeLogSubscriptionAttemptCountTest,ReactiveHttpClientLifecycleHookTest test`
  passed 21 tests.
- Focused release-profile benchmark report:
  `reactive-http-client-benchmarks/target/benchmark-reports/v13-priority7/release-jmh.md`.
- Pre-change promoted 2.9.0 report row for `clientSideOverheadStarterResponseEntity`:
  `128.981 us/op` average time, `109.773 us/op` sample mean, and
  `34304.518 B/op` sample allocation.
- Post-change focused report measured `clientSideOverheadStarterResponseEntity`
  at `145.728 us/op` average time with high local variance, `68.731 us/op`
  sample mean, `27505.814 B/op` throughput allocation, and `27529.242 B/op`
  sample allocation. Treat this as allocation-focused evidence until a promoted
  full report refreshes all V13 rows.

---

## Priority 8 — Audit Optional Feature Overhead

### [x] 2.4 Audit optional feature overhead
- [x] Refresh optional feature rows after default-path changes.
- [x] Audit Micrometer tag creation for avoidable per-call allocation.
- [x] Audit retry wrapper setup for avoidable per-call allocation.
- [x] Verify metadata-only exchange logging keeps using the production logger.
- [x] Verify metadata-only exchange logging remains separate from body-capture
      logging.
- [x] Keep feature comparisons labeled starter-only unless baselines perform the
      same work.
- [x] Document optional feature overhead with refreshed release-quality numbers.
- [x] Verify disabled diagnostics remain cheap on the default path.
- [x] Update enabled feature recommendations only when backed by release-quality
      data.
- [x] Verify no optional feature benchmark is presented as default starter
      overhead.

Evidence:
- Code change: `MicrometerHttpClientObserver` now builds common low-cardinality
  tags once per event and reuses them for the main timer, attempt/size summaries,
  and optional histogram timer lookup.
- Code change: `Resilience4jOperatorApplier` and the rate-limiter adapter now
  cache Reactor operators by Resilience4j instance name, so enabled retry,
  circuit-breaker, bulkhead, and rate-limiter paths do not recreate wrapper
  operators for every invocation.
- Audit result: metadata-only exchange logging still registers and invokes the
  production `DefaultHttpExchangeLogger`; the benchmark uses
  `LogPreset.METADATA_ONLY`, so it remains separate from body-capture logging.
- Audit result: generated benchmark reports classify `starterFeature*` rows as
  `Optional starter feature`; no optional feature row is presented as default
  starter overhead or compared against baselines that do not perform the same
  work.
- Tests: `mvn -pl reactive-http-client-starter -Dtest=MicrometerHttpClientObserverTest,ResilienceOperatorApplierTest,ReactiveClientInvocationHandlerRetryMethodsTest,ReactiveClientInvocationHandlerRetrySafetyTest test`
  passed 78 tests.
- Focused release-profile benchmark report:
  `reactive-http-client-benchmarks/target/benchmark-reports/v13-priority8/release-jmh.md`.
- Loopback optional feature rows from the focused report:
  `Exchange Logging Metadata Only Get No Body` `56.711 us/op` average,
  `61.067 us/op` sample, `27227.209 B/op` sample allocation;
  `Micrometer Observer Get No Body` `81.586 us/op` average with local variance,
  `69.300 us/op` sample, `29671.329 B/op` sample allocation;
  `Retry Wrapper Get No Body` `61.642 us/op` average, `64.258 us/op` sample,
  `27669.503 B/op` sample allocation; `Circuit Breaker Wrapper Get No Body`
  `57.035 us/op` average, `60.907 us/op` sample, `26666.085 B/op` sample allocation.
- No-network diagnostics rows from the focused report: disabled diagnostics
  `183031.141 ops/s` and `11108.006 B/op`; metadata-only exchange logging
  `88358.342 ops/s` and `16924.016 B/op`; Micrometer observer
  `107016.260 ops/s` and `16260.012 B/op`; runtime diagnostics provider
  `40768.109 ops/s` and `44668.052 B/op` as an on-demand inspection API.
- Documentation recommendation: keep current production guidance unchanged until
  a promoted full benchmark report is selected for release notes; this focused
  run is recorded as Priority 8 evidence and not promoted as the default starter
  performance report.

---

## Priority 9 — Audit Problem Detail Error-Mapping Overhead

### [ ] 2.5 Audit Problem Detail error-mapping overhead
- [ ] Confirm current-workspace Problem Detail overhead after V12 error-body
      changes.
- [ ] Audit JSON parse allocation in Problem Detail mapping.
- [ ] Audit exception construction allocation in Problem Detail mapping.
- [ ] Preserve truncation metadata.
- [ ] Preserve bounded body capture behavior.
- [ ] Preserve fallback behavior for malformed or truncated bodies.
- [ ] Avoid comparing Problem Detail rows against raw clients unless baselines
      install equivalent Problem Detail mapping.
- [ ] Record Problem Detail before/after numbers if code changes.
- [ ] Verify mapper fallback behavior remains covered by tests.
- [ ] Verify docs continue to label this as starter-only error-mapping overhead.

---

## Priority 10 — Benchmark Threshold Guidance Without Hard Gates

### [ ] 3.2 Benchmark threshold guidance without hard gates
- [ ] Add guidance for comparing scenario trends across release reports.
- [ ] Define review triggers for large relative changes.
- [ ] Define review triggers for allocation spikes.
- [ ] Define review triggers for optional feature overhead jumps.
- [ ] Keep normal CI free of long-running performance gates.
- [ ] Prefer manual release review over flaky automated pass/fail thresholds.
- [ ] Verify docs describe report comparison without treating one run as
      universal.
- [ ] Verify release checklist tells maintainers when to rerun benchmarks.
- [ ] Verify performance docs remain honest about machine variability.

---

## Priority 11 — Public Documentation Consistency

### [ ] 3.3 Public documentation consistency
- [ ] Extend documentation tests to validate promoted report links.
- [ ] Extend documentation tests to validate promoted report headers.
- [ ] Check public docs do not link `target/benchmark-reports`.
- [ ] Check public docs do not link smoke-only artifacts as evidence.
- [ ] Keep README, benchmark docs, changelog, and release compatibility docs
      aligned on benchmark commands and evidence rules.
- [ ] Add a release-maintainer checklist for writing performance claims.
- [ ] Verify changelog performance claims cite a scenario and report.
- [ ] Verify README points to benchmark docs and the promoted report when
      available.
