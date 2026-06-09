# Roadmap V12 Execution Checklist

> Companion to [`ROADMAP.md`](ROADMAP.md). This file tracks execution;
> keep rationale and design discussion in the roadmap.

---

## Priority 1 — Benchmark Module and Repeatable Harness

### [x] 1.1 Benchmark module and repeatable harness
- [x] Add a dedicated benchmark module or Maven profile that is excluded from
      normal `mvn test`.
- [x] Add JMH microbenchmarks for proxy invocation, argument resolution,
      metadata lookup, and request construction.
- [x] Add an end-to-end benchmark harness against a local in-process or loopback
      server with fixed response bodies.
- [x] Build equivalent raw `WebClient`, Spring HTTP Interface, and starter
      clients with the same transport, codecs, base URL, request model, and
      server.
- [x] Add a quick benchmark command that acts only as a harness smoke check.
- [x] Ensure quick benchmark output is labeled smoke-only and excluded from
      publishable benchmark reports.
- [x] Add a longer release-quality benchmark command for maintainers.
- [x] Capture environment metadata: Java version, OS, CPU, Spring Boot version,
      Reactor Netty version, project version, and benchmark commit.
- [x] Document benchmark commands and keep them out of default CI.

---

## Priority 2 — Benchmark Fairness Guardrails

### [x] 3.1 Benchmark fairness guardrails
- [x] Ensure compared clients use equivalent connection providers, codecs, base
      URLs, headers, serialization settings, and response decoding.
- [x] Add assertions that every compared client receives the same status,
      headers of interest, and body shape.
- [x] Ensure every benchmark consumes the response so client work is not
      optimized away.
- [x] Add warmup before measured runs.
- [x] Avoid measuring server bottlenecks as client overhead by using a cheap
      local handler or reporting server saturation indicators.
- [x] Fail benchmarks when a scenario configures different payloads or headers
      across clients.
- [x] Document known limits such as loopback transport cost and local machine
      variability.
- [x] Keep benchmark output positioned as trend evidence, not exact universal
      nanosecond claims.

---

## Priority 3 — Core Scenario Matrix

### [x] 1.2 Core scenario matrix
- [x] Split scenarios into client-side overhead benchmarks and feature-parity
      benchmarks.
- [x] Use an extremely light local loopback server for client-side overhead
      scenarios.
- [x] Add successful call scenarios for `GET` with no body, `GET` with path,
      query, and header arguments, `POST` JSON, and `Mono<ResponseEntity<T>>`.
- [x] Add error scenarios for 4xx/5xx with a small bounded error body.
- [x] Add `application/problem+json` as starter error-mapping overhead unless
      baselines implement equivalent Problem Detail mapping.
- [x] Add optional feature scenarios one feature at a time: exchange logging,
      Micrometer observer, retry, and circuit breaker.
- [x] Do not compare starter feature work against raw `WebClient` or Spring HTTP
      Interface baselines that omit the same work.
- [x] Include Spring HTTP Interface only where the framework can support the
      scenario without custom code that changes the comparison.
- [x] Record throughput, average latency, p50/p95/p99 latency, allocation rate,
      and error count where reliable.
- [x] Keep scenario names and result columns stable for release-to-release
      diffs.

---

## Priority 4 — Generated Benchmark Report

### [x] 1.3 Generated benchmark report
- [x] Generate a Markdown benchmark report under `target/benchmark-reports/` by
      default.
- [x] Include raw result tables plus a short interpretation section.
- [x] Include environment metadata in the report.
- [x] Add comparison summaries against raw `WebClient` and Spring HTTP
      Interface for each valid scenario.
- [x] Label smoke-only results, optional starter features, starter-only overhead
      scenarios, and release-quality runs clearly.
- [x] Keep generated benchmark files out of source unless intentionally
      promoted.
- [x] Document how maintainers promote a selected release report into `docs/`.
- [x] Link promoted benchmark reports from release notes when performance
      claims are included.

---

## Priority 5 — Invocation Overhead Audit

### [x] 2.1 Invocation overhead audit
- [x] Measure proxy invocation overhead without network I/O.
- [x] Measure cached `MethodMetadata`, request planning, and
      argument-resolution paths.
- [x] Audit allocation hot spots for common scalar path, query, and header
      arguments.
- [x] Identify the top per-invocation allocation or CPU contributors.
- [x] Apply only simple, measured optimizations that preserve validation and
      diagnostics contracts.
- [x] Record before/after benchmark numbers for every optimization in
      [`INVOCATION_OVERHEAD_AUDIT.md`](INVOCATION_OVERHEAD_AUDIT.md).
- [x] Verify existing behavior tests pass unchanged.
- [x] Verify startup validation and diagnostics remain precise.

---

## Priority 6 — Optional Diagnostics Overhead Audit

### [x] 2.2 Optional diagnostics overhead audit
- [x] Benchmark default observer and exchange-log disabled paths.
- [x] Benchmark metadata-only exchange logging without body capture.
- [x] Benchmark Micrometer observer recording with a simple in-memory registry.
- [x] Verify runtime diagnostics provider calls are on-demand and not part of
      the request path.
- [x] Check disabled diagnostics do not allocate large per-request diagnostic
      structures.
- [x] Document measurable enabled-diagnostics overhead and recommended
      production defaults.
- [x] Distinguish request-path overhead from startup or diagnostic-query
      overhead in docs.

---

## Priority 7 — Benchmark-Backed Release Evidence

### [x] 2.3 Benchmark-backed release evidence
- [x] Add benchmark command names and generated report paths to release
      evidence.
- [x] Keep benchmark execution manual or profile-gated.
- [x] Add a lightweight smoke check that verifies benchmark classes compile and
      the quick benchmark command can start.
- [x] Define when benchmark numbers must be refreshed: request construction,
      observability, resilience wrapping, transport/client-builder changes, or
      public performance claims.
- [x] Ensure normal CI remains fast.
- [x] Make missing benchmark evidence visible before publishing a release that
      changes request-path behavior.
- [x] Document how maintainers attach benchmark evidence to release notes.

---

## Priority 8 — Baseline Availability and Dependency Drift

### [ ] 3.2 Baseline availability and dependency drift
- [ ] Record starter version and baseline library versions in benchmark reports.
- [ ] Verify the benchmark module uses the same Spring Boot and Reactor Netty
      dependency management as the starter.
- [ ] Document how to benchmark the current workspace.
- [ ] Document how to benchmark against the last published starter release.
- [ ] Make unresolved published baseline artifacts visible in release evidence.
- [ ] Keep benchmark and API compatibility baseline warnings actionable.

---

## Priority 9 — Documentation Claims Tied to Benchmark Data

### [ ] 3.3 Documentation claims tied to benchmark data
- [ ] Add public documentation that explains benchmark methodology before
      numbers.
- [ ] Include benchmark limitations: local loopback, machine variability,
      warmup, and scenario-specific conclusions.
- [ ] Add a clear comparison table for raw `WebClient`, Spring HTTP Interface,
      and the starter.
- [ ] Scope every performance claim to a named scenario and report version.
- [ ] Avoid broad claims such as "near zero overhead" unless directly supported
      by a measured scenario.
- [ ] Add a docs test or checklist check that prevents stale benchmark report
      links.
- [ ] Verify public docs do not publish quick smoke benchmark numbers as real
      performance evidence.

