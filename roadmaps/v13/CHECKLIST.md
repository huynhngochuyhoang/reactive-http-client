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

### [ ] 1.2 Add performance summary documentation
- [ ] Add `docs/23-performance-summary.md` or an equivalent public documentation
      section.
- [ ] Link the performance summary from `docs/22-benchmarks.md`.
- [ ] Explain benchmark methodology before publishing numbers.
- [ ] Summarize raw `WebClient`, Spring HTTP Interface, starter default path,
      starter optional features, and starter-only error mapping separately.
- [ ] Summarize measured findings using named scenarios, not broad claims.
- [ ] Explain expected starter overhead sources: proxy dispatch,
      metadata/request planning, annotation argument resolution, diagnostics
      hooks, resilience wrappers, and response envelope handling.
- [ ] State when the available report measures starter `2.8.0` and when
      current-release claims require a refreshed current-workspace report.
- [ ] Verify optional feature overhead is separated from default-path overhead.
- [ ] Verify Problem Detail mapping is labeled as starter-only error-mapping
      overhead.
- [ ] Verify quick benchmark output is not described as performance evidence.

---

## Priority 3 — Release-Note Benchmark Evidence Workflow

### [ ] 1.3 Release-note benchmark evidence workflow
- [ ] Extend release evidence or changelog guidance with a benchmark evidence
      block.
- [ ] Include promoted report link, current-workspace command,
      published-baseline command, and scenario names used by release notes.
- [ ] Require a promoted report link when the changelog includes performance
      claims.
- [ ] Include both current candidate and published-baseline report paths for
      release-to-release trend comparisons.
- [ ] Document how to rerun only the benchmark scenarios cited by release notes.
- [ ] Verify release notes can cite the promoted report without manual path
      rewriting.
- [ ] Verify release evidence distinguishes current candidate and published
      baseline reports.
- [ ] Verify a release with performance claims cannot rely only on a pending
      benchmark evidence entry.

---

## Priority 4 — Current-vs-Baseline Report Pairing

### [ ] 3.1 Current-vs-baseline report pairing
- [ ] Ensure current candidate and published-baseline report paths are distinct
      in release evidence.
- [ ] Ensure current candidate and published-baseline report paths are distinct
      in public documentation.
- [ ] Add a generated or manual comparison note that names the starter versions
      being compared.
- [ ] Make baseline artifact resolution failure visible before report promotion.
- [ ] Verify promoted docs do not mix current candidate numbers with published
      baseline labels.
- [ ] Verify current and baseline reports can coexist under stable paths.
- [ ] Verify docs tests catch stale or mismatched promoted report links.

---

## Priority 5 — Optimize Default Success-Path Overhead

### [ ] 2.1 Optimize default success-path overhead
- [ ] Refresh the current-workspace release benchmark before changing request
      path code.
- [ ] Use no-network invocation rows to identify allocation and CPU contributors
      in proxy invocation, request planning, argument resolution, and response
      handling.
- [ ] Inspect redundant immutable map/list copying during request argument
      resolution.
- [ ] Inspect repeated header, query, and path expansion work after
      `RequestPlan` lookup.
- [ ] Inspect per-subscription state allocation on success paths that do not use
      retry, idempotency, lifecycle hooks, exchange logging, or observers.
- [ ] Inspect response envelope mapping overhead for `Mono<ResponseEntity<T>>`.
- [ ] Apply only simple default-path improvements that preserve public behavior.
- [ ] Record before/after JMH rows for every changed scenario.
- [ ] Verify behavior tests pass unchanged.
- [ ] Verify diagnostics, lifecycle, retry, idempotency, and validation contracts
      remain covered by tests.
- [ ] Document improvements as scenario-specific, not universal parity.

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
