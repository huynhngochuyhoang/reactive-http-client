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

### [ ] 2.2 Optimize JSON request/response paths
- [ ] Audit JSON body preparation for default unauthenticated `POST` calls.
- [ ] Audit auth serialization bypasses and confirm they are not used on the
      default unauthenticated path.
- [ ] Audit content-type handling and request-body publisher creation.
- [ ] Verify scalar DTO bodies and publisher DTO bodies still use the correct
      encoder.
- [ ] Avoid pre-serializing bodies unless auth/signing or retry body replay
      requires it.
- [ ] Add a focused benchmark or JMH grouping if `Post Json` cannot isolate the
      overhead source.
- [ ] Record `Post Json` before/after numbers.
- [ ] Verify raw `WebClient` and Spring HTTP Interface baselines still perform
      equivalent request and response JSON work.
- [ ] Verify auth signing, publisher bodies, and non-repeatable body protections
      remain covered.
- [ ] Verify no broad JSON performance claim is published without the promoted
      report.

---

## Priority 7 — Optimize `ResponseEntity` Envelope Handling

### [ ] 2.3 Optimize `ResponseEntity` envelope handling
- [ ] Audit response-header copying and status/body envelope construction.
- [ ] Avoid duplicate response metadata snapshots when observers, lifecycle
      hooks, and exchange logging are disabled.
- [ ] Keep final outbound request diagnostics intact.
- [ ] Keep streaming ownership semantics intact.
- [ ] Measure `Mono<ResponseEntity<T>>` separately from streaming
      `ResponseEntity<Flux<DataBuffer>>`.
- [ ] Record `Response Entity` before/after numbers.
- [ ] Verify streaming response ownership tests still pass.
- [ ] Verify observer and exchange-log contexts still receive documented
      metadata.
- [ ] Verify no optimization releases a body before the consumer owns it.

---

## Priority 8 — Audit Optional Feature Overhead

### [ ] 2.4 Audit optional feature overhead
- [ ] Refresh optional feature rows after default-path changes.
- [ ] Audit Micrometer tag creation for avoidable per-call allocation.
- [ ] Audit retry wrapper setup for avoidable per-call allocation.
- [ ] Verify metadata-only exchange logging keeps using the production logger.
- [ ] Verify metadata-only exchange logging remains separate from body-capture
      logging.
- [ ] Keep feature comparisons labeled starter-only unless baselines perform the
      same work.
- [ ] Document optional feature overhead with refreshed release-quality numbers.
- [ ] Verify disabled diagnostics remain cheap on the default path.
- [ ] Update enabled feature recommendations only when backed by release-quality
      data.
- [ ] Verify no optional feature benchmark is presented as default starter
      overhead.

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
