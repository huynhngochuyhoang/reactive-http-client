# Reactive HTTP Client — Roadmap V12

> **Status:** draft after `2.9.0`. V12 focuses on reproducible performance
> evidence so users can compare the starter against raw `WebClient` and Spring
> HTTP Interface clients before trusting it in production paths.

V12 keeps the same three-bucket shape:

1. **Features to add** — benchmark harnesses, scenarios, and generated reports
   that make performance tradeoffs inspectable.
2. **Features to optimize** — focused overhead reductions only where benchmark
   evidence shows meaningful cost.
3. **Bugs / correctness to fix** — benchmark methodology problems that could
   produce misleading numbers or hide regressions.

The bias for V12: publish honest evidence, not marketing numbers. The starter
adds declarative metadata parsing, argument resolution, diagnostics, optional
auth, resilience, logging, and observability. Users should be able to see what
that costs in steady-state calls, where the cost is paid, and how it compares to
plain framework alternatives under equivalent transport and codec settings.

Non-goals:

- Do not claim the starter is always faster than raw `WebClient` or Spring HTTP
  Interfaces.
- Do not benchmark remote internet calls or unstable external services.
- Do not enable extra starter features in comparisons unless the same feature is
  represented in the baseline or the scenario is explicitly labeled.
- Do not put long-running benchmarks in normal `mvn test`.
- Do not treat one laptop result as universal performance truth.
- Do not optimize code before the harness can reproduce the overhead.

---

## 1. Features to add

### 1.1 Benchmark module and repeatable harness

**Why:** Performance claims need a repeatable local command. A benchmark that
cannot be rerun by maintainers or users will not build trust.

**What:**

- Add a dedicated benchmark module or profile that is not part of normal unit
  tests.
- Use JMH for microbenchmarks around proxy invocation, argument resolution,
  metadata lookup, and request construction.
- Add an end-to-end benchmark harness for HTTP calls against a local in-process
  or loopback server with fixed response bodies.
- Keep raw `WebClient`, Spring HTTP Interface (`@HttpExchange`), and starter
  clients on the same Reactor Netty transport, codecs, base URL, request model,
  and server.
- Provide one command for quick local smoke benchmarks and one command for
  longer release-quality runs.
- Treat the quick benchmark command as a harness smoke check only. It must prove
  the benchmarks compile, start, and collect results; it must not produce numbers
  that are published as project evidence.

**Acceptance:**

- [ ] A maintainer can run a short benchmark without publishing artifacts.
- [ ] Quick benchmark output is labeled as smoke-only and excluded from
      publishable benchmark reports.
- [ ] The harness compares equivalent raw `WebClient`, Spring HTTP Interface,
      and starter clients.
- [ ] Benchmark commands are documented and excluded from default `mvn test`.
- [ ] Results include environment metadata: Java version, OS, CPU, Spring Boot
      version, Reactor Netty version, project version, and benchmark commit.

---

### 1.2 Core scenario matrix

**Why:** One benchmark hides tradeoffs. Users need scenarios that map to real
starter usage and show which features add overhead, without comparing a starter
path that does extra work against a baseline that does not do that work.

**What:**

- Split scenarios into client-side overhead benchmarks and feature-parity
  benchmarks.
- Client-side overhead benchmarks use an extremely light local loopback server
  so the result mostly reflects request construction, proxy dispatch, codecs,
  response mapping, and Reactor/WebClient client-side cost rather than server
  work.
- Cover steady-state successful client-side overhead calls:
  - `GET` with no body and small JSON response.
  - `GET` with path, query, and header arguments.
  - `POST` with small JSON request and response.
  - `Mono<ResponseEntity<T>>` response mapping.
- Cover client-side error paths:
  - 4xx/5xx with small bounded error body.
  - `application/problem+json` mapping as starter error-mapping overhead when
    raw `WebClient` or Spring HTTP Interface baselines do not implement the same
    Problem Detail mapper.
- Cover optional feature-parity tiers by enabling one feature at a time:
  - Baseline starter with diagnostics disabled beyond defaults.
  - Exchange logging metadata-only enabled, compared only with baselines that
    perform equivalent logging or with a clearly labeled starter-only overhead
    measurement.
  - Micrometer observer enabled, compared only with baselines that record
    equivalent metrics or with a clearly labeled starter-only overhead
    measurement.
  - Resilience retry/circuit-breaker wrappers enabled, compared only with
    baselines that apply equivalent retry/circuit-breaker behavior or with a
    clearly labeled starter-only overhead measurement.
- Record throughput, average latency, p50/p95/p99 latency, allocation rate, and
  error count for each scenario where the harness can do so reliably.

**Acceptance:**

- [ ] Every scenario has a raw `WebClient` baseline.
- [ ] Spring HTTP Interface is included where the scenario is supported by the
      framework without custom code that changes the comparison.
- [ ] Optional starter feature overhead is measured separately from the default
      path.
- [ ] Optional feature comparisons do not compare starter feature work against a
      raw `WebClient` baseline that omits the same work.
- [ ] Problem Detail scenarios are labeled as starter error-mapping overhead
      unless baselines implement equivalent mapping.
- [ ] Scenario names and result columns are stable enough for release-to-release
      diffs.

---

### 1.3 Generated benchmark report

**Why:** Users should not need to run benchmarks before seeing the project's own
evidence. Maintainers also need a stable artifact for release notes.

**What:**

- Generate a Markdown benchmark report under `target/benchmark-reports/` by
  default.
- Allow maintainers to promote a selected release report into `docs/` when
  publishing.
- Include raw result tables plus a short interpretation section that calls out
  what the benchmark does and does not prove.
- Add a comparison summary showing starter overhead relative to raw `WebClient`
  and Spring HTTP Interface for each scenario.
- Link the report from release notes when a release includes benchmark-relevant
  changes.

**Acceptance:**

- [ ] Benchmark output can be attached to release notes without manual table
      editing.
- [ ] The report includes enough environment metadata to interpret the numbers.
- [ ] The report explicitly labels optional features and avoids implying they
      are enabled by default.
- [ ] Generated benchmark files stay out of source unless intentionally promoted.

---

## 2. Features to optimize

### 2.1 Invocation overhead audit

**Why:** Once the harness exists, the first likely question is whether proxy
invocation, metadata lookup, argument resolution, and context preparation add
avoidable per-call overhead.

**What:**

- Measure proxy invocation overhead without network I/O.
- Compare cached `MethodMetadata`, `RequestPlan`, and argument-resolution paths.
- Audit allocation hot spots for common scalar path/query/header arguments.
- Optimize only changes that are simple, measurable, and do not weaken
  validation or diagnostics contracts.

**Acceptance:**

- [ ] The audit identifies the top per-invocation allocation or CPU contributors.
- [ ] Any optimization includes before/after benchmark numbers.
- [ ] Existing behavior tests pass unchanged.
- [ ] Startup validation and diagnostics do not become less precise.

---

### 2.2 Optional diagnostics overhead audit

**Why:** The starter has added observers, exchange logging, lifecycle hooks, and
runtime diagnostics. These should be cheap when disabled and measurable when
enabled.

**What:**

- Benchmark default observer/exchange-log disabled paths.
- Benchmark metadata-only exchange logging without body capture.
- Benchmark Micrometer observer recording with a simple in-memory registry.
- Verify the runtime diagnostics provider does not add per-request overhead
  unless explicitly invoked.
- Document any measurable overhead and recommended production defaults.

**Acceptance:**

- [ ] Disabled diagnostics do not allocate large per-request structures only for
      diagnostics.
- [ ] Enabled diagnostics overhead is documented with benchmark numbers.
- [ ] The runtime diagnostics provider is confirmed to be on-demand, not part of
      the request path.
- [ ] Docs distinguish request-path overhead from startup or diagnostic-query
      overhead.

---

### 2.3 Benchmark-backed release evidence

**Why:** V11 added release evidence metadata. V12 should add performance evidence
without making every CI run slow or flaky.

**What:**

- Add benchmark command names and report paths to the release evidence workflow.
- Keep benchmark execution manual or profile-gated.
- Add a lightweight smoke check that verifies benchmark classes compile and the
  quick benchmark command can start.
- Define a release-maintainer rule for when benchmark numbers must be refreshed:
  request construction changes, observability changes, resilience wrapping
  changes, transport/client-builder changes, or public performance claims.

**Acceptance:**

- [ ] Release evidence mentions the benchmark command and generated report path.
- [ ] Normal CI remains fast.
- [ ] Release documentation says when benchmark reports need to be regenerated.
- [ ] Missing benchmark evidence is visible before publishing a release that
      changes request-path behavior.

---

## 3. Bugs / correctness to fix

### 3.1 Benchmark fairness guardrails

**Why:** Bad benchmark methodology can be worse than no benchmark. The raw
`WebClient`, Spring HTTP Interface, and starter paths must perform equivalent
work.

**What:**

- Ensure all clients use equivalent connection providers, codecs, base URLs,
  headers, serialization settings, and response decoding.
- Ensure warmup happens before measurement and each benchmark consumes the
  response so work is not optimized away.
- Avoid measuring server bottlenecks as client overhead by reporting server
  saturation indicators or using a sufficiently cheap local handler.
- Keep benchmark assertions that verify every client receives the same status,
  headers of interest, and body shape.

**Acceptance:**

- [ ] Benchmarks fail if a compared client does not actually consume the response.
- [ ] Benchmarks fail if a scenario configures different payloads or headers
      across clients.
- [ ] The report documents known limits such as loopback transport cost and
      local machine variability.
- [ ] Benchmarks are deterministic enough to compare trends, not exact absolute
      nanosecond claims.

---

### 3.2 Baseline availability and dependency drift

**Why:** Benchmark comparisons and API compatibility evidence both depend on
resolvable baselines and stable dependency versions. Missing artifacts or
accidental dependency upgrades can weaken release evidence.

**What:**

- Verify benchmark reports record dependency versions used for the run.
- Add a check that the benchmark module uses the same Spring Boot and Reactor
  Netty dependency management as the starter.
- Document how to run benchmarks against the current workspace and against the
  last released starter version.
- Make unresolved baseline artifacts visible in release evidence rather than
  burying them in Maven warnings.

**Acceptance:**

- [ ] Benchmark report identifies the starter version under test.
- [ ] Benchmark report identifies baseline library versions.
- [ ] Release evidence calls out unresolved published baselines as a warning.
- [ ] Docs explain how to compare the current branch against a published release.

---

### 3.3 Documentation claims tied to benchmark data

**Why:** Once performance numbers exist, docs can drift or overstate what the
numbers prove. Claims should remain tied to measured scenarios.

**What:**

- Add a documentation section that explains the benchmark methodology before the
  numbers.
- Avoid broad claims such as "near zero overhead" unless a specific scenario
  supports it.
- Link each performance claim to the scenario and report version that supports
  it.
- Add a docs test or checklist item that prevents stale benchmark report links.

**Acceptance:**

- [ ] Public docs include benchmark methodology and limitations.
- [ ] Public docs include a clear comparison table for raw `WebClient`, Spring
      HTTP Interface, and the starter.
- [ ] Performance claims are scoped to named scenarios.
- [ ] Stale local benchmark report links fail documentation checks.

---

## Suggested priority order

1. Benchmark module and repeatable harness.
2. Benchmark fairness guardrails.
3. Core scenario matrix.
4. Generated benchmark report.
5. Invocation overhead audit.
6. Optional diagnostics overhead audit.
7. Benchmark-backed release evidence.
8. Baseline availability and dependency drift.
9. Documentation claims tied to benchmark data.
