# Reactive HTTP Client — Roadmap V13

> **Status:** completed in the `2.10.0` release cycle. V13 turned release-quality
> benchmark output into public performance documentation, publishes a curated
> report, and uses measured scenarios to choose small optimization work.

V13 keeps the same three-bucket shape:

1. **Features to add** — public benchmark report promotion, documentation pages,
   and release-note evidence links.
2. **Features to optimize** — focused request-path improvements backed by
   `release-jmh.md` scenario data.
3. **Bugs / correctness to fix** — documentation, report, and benchmark-claim
   issues that could make performance evidence stale or misleading.

The available release-quality report at draft time is
`reactive-http-client-benchmarks/target/benchmark-reports/published-starter-2.8.0/release-jmh.md`.
It benchmarks starter `2.8.0` with the current V12 harness and Spring Boot
`3.5.0` dependency management. V13 should refresh the current-workspace release
report before publishing claims for the next starter version.

Observed signals from the available report:

- Success-path starter calls are slower than raw `WebClient` in the loopback
  client-side overhead scenarios: `Get No Body` +24.892%, `Get Path Query Header`
  +27.15%, `Post Json` +41.41%, and `Response Entity` +36.697% average latency.
- Success-path starter calls are also slower than Spring HTTP Interface in the
  same report, with the largest gaps in `Post Json` and `Get No Body`.
- Error paths are faster than both baselines for the small bounded 4xx/5xx
  scenarios, so V13 should not optimize those paths without fresh evidence.
- Optional feature rows are starter-only feature overhead, not default runtime
  overhead: metadata exchange logging is close to the default `Get No Body` row,
  while Micrometer and retry wrapper rows are visibly higher.
- `starterErrorMappingProblemDetailSmallBody` is starter-only error-mapping
  overhead and allocates substantially more per operation than small generic
  error mapping, so it should be documented separately and optimized only if the
  report remains stable after a current-workspace run.
- No-network invocation rows show per-call allocations in proxy invocation,
  argument resolution, and mock exchange construction; these are the safest
  places to look before changing transport or codec behavior.

Non-goals:

- Do not publish the generated `target/` report directly as a stable public link.
- Do not publish smoke benchmark numbers.
- Do not claim broad parity with raw `WebClient`; every claim must name a
  scenario and report version.
- Do not optimize error paths that are already faster in the release-quality
  report unless a new report shows a regression.
- Do not trade validation, diagnostics correctness, or public contract clarity for
  micro-optimizations.

---

## 1. Performance Docs and Published Report

### 1.1 Promote a versioned benchmark report

**Why:** V12 can generate reports, but users need a stable source-controlled
artifact when release notes or docs discuss performance.

**What:**

- Run the current-workspace release-quality benchmark for the candidate version.
- Promote the selected Markdown report into `docs/` with a versioned filename,
  for example `docs/benchmark-report-2.10.0.md`.
- Keep the original generated JSON and environment metadata referenced in release
  evidence, but do not commit `target/` artifacts.
- Add a short front matter or header note explaining the report version, starter
  version under test, benchmark commit, Java version, Spring Boot version, and
  machine limits.
- Link the promoted report from `docs/22-benchmarks.md`, `README.md`, and the
  changelog only after the report is source-controlled.

**Acceptance:**

- [ ] A versioned benchmark report exists under `docs/`.
- [ ] The report states that it is release-quality, not smoke evidence.
- [ ] The report preserves environment metadata needed to interpret the numbers.
- [ ] Public docs link only the promoted report, never `target/benchmark-reports`.
- [ ] Documentation tests fail if the promoted report link becomes stale.

---

### 1.2 Add performance summary documentation

**Why:** The generated report is detailed. Users need a concise interpretation
that explains what the numbers mean before reading raw JMH tables.

**What:**

- Add a `docs/23-performance-summary.md` page or equivalent section linked from
  `docs/22-benchmarks.md`.
- Summarize the comparison model: raw `WebClient`, Spring HTTP Interface, starter
  default path, starter optional features, and starter-only error mapping.
- Summarize measured findings using scenario names, not broad claims.
- Explain where starter overhead is expected: declarative proxy dispatch,
  metadata/request planning, annotation argument resolution, diagnostics hooks,
  resilience wrappers, and response envelope handling.
- Call out that the available published-baseline report measures starter `2.8.0`,
  and current-release claims require the refreshed current-workspace report.

**Acceptance:**

- [ ] Performance docs explain methodology before numbers.
- [ ] Every numeric claim links to the promoted report and scenario name.
- [ ] Optional feature overhead is separated from default-path overhead.
- [ ] Problem Detail mapping is labeled as starter-only error-mapping overhead.
- [ ] Quick benchmark output is not described as performance evidence.

---

### 1.3 Release-note benchmark evidence workflow

**Why:** Release notes should be trustworthy and repeatable when they mention
performance.

**What:**

- Extend release evidence or changelog guidance with a benchmark evidence block:
  report link, current-workspace command, published-baseline command, and scenario
  names used by release notes.
- Require the promoted report link when the changelog includes performance claims.
- Include both current candidate and published-baseline report paths when comparing
  release-to-release trends.
- Document how to rerun only the benchmark scenarios used by the release notes.

**Acceptance:**

- [ ] Release notes can cite the promoted report without manual path rewriting.
- [ ] Release evidence distinguishes current candidate and published baseline
      reports.
- [ ] A release with performance claims cannot rely only on a pending benchmark
      evidence entry.
- [ ] Maintainers can reproduce each release-note performance claim with a named
      command and scenario filter.

---

## 2. Measured Optimization Work

### 2.1 Optimize default success-path overhead

**Why:** The available report shows the starter default success path slower than
raw `WebClient` in the main loopback scenarios, especially `Post Json`,
`Response Entity`, `Get Path Query Header`, and `Get No Body`.

**What:**

- Refresh the current-workspace release benchmark before changing code.
- Use no-network invocation rows to identify allocation and CPU contributors in
  proxy invocation, request planning, argument resolution, and response handling.
- Focus first on simple default-path improvements that affect all successful
  calls and do not change public behavior.
- Candidate areas to inspect:
  - redundant immutable map/list copying during request argument resolution;
  - repeated header/query/path expansion work after `RequestPlan` lookup;
  - per-subscription state allocation on success paths that do not use retry,
    idempotency, lifecycle hooks, exchange logging, or observers;
  - response envelope mapping overhead for `Mono<ResponseEntity<T>>`.
- Record before/after JMH rows for each changed scenario.

**Acceptance:**

- [ ] Each optimization is tied to a before/after benchmark row.
- [ ] Behavior tests pass unchanged.
- [ ] Diagnostics, lifecycle, retry, idempotency, and validation contracts remain
      covered by tests.
- [ ] Any improvement is documented as scenario-specific, not universal parity.

---

### 2.2 Optimize JSON request/response paths

**Why:** `Post Json` has the largest average-latency gap in the available report:
starter `71.293 us/op`, 41.41% slower than raw `WebClient` and 31.359% slower
than Spring HTTP Interface.

**What:**

- Audit JSON body preparation, auth serialization bypasses, content-type handling,
  and request-body publisher creation for default unauthenticated POST calls.
- Verify scalar DTO bodies and publisher DTO bodies still use the correct encoder.
- Avoid pre-serializing bodies unless auth/signing or retry body replay requires
  it.
- Add a focused benchmark or JMH grouping if the current `Post Json` row cannot
  isolate the overhead source.

**Acceptance:**

- [ ] `Post Json` before/after numbers are recorded.
- [ ] Raw `WebClient` and Spring HTTP Interface baselines still perform equivalent
      request and response JSON work.
- [ ] Auth signing, publisher bodies, and non-repeatable body protections remain
      covered.
- [ ] No broad JSON performance claim is published without the promoted report.

---

### 2.3 Optimize `ResponseEntity` envelope handling

**Why:** `Response Entity` is 36.697% slower than raw `WebClient` in the available
report and is a common declarative-client return type.

**What:**

- Audit response-header copying and status/body envelope construction.
- Avoid duplicate response metadata snapshots when observers, lifecycle hooks, and
  exchange logging are disabled.
- Keep final outbound request diagnostics and streaming ownership semantics intact.
- Measure `Mono<ResponseEntity<T>>` separately from streaming
  `ResponseEntity<Flux<DataBuffer>>` so optimizations do not break streaming
  handoff.

**Acceptance:**

- [ ] `Response Entity` before/after numbers are recorded.
- [ ] Streaming response ownership tests still pass.
- [ ] Observer and exchange-log contexts still receive documented metadata.
- [ ] No optimization releases a body before the consumer owns it.

---

### 2.4 Audit optional feature overhead

**Why:** Optional feature rows are not default overhead, but they inform production
recommendations. The available report shows Micrometer and retry wrappers above
metadata-only exchange logging and circuit-breaker wrapping for `Get No Body`.

**What:**

- Refresh optional feature rows after default-path changes.
- Audit Micrometer tag creation and retry wrapper setup for avoidable per-call
  allocation.
- Verify metadata-only exchange logging keeps using the production logger and
  remains separate from body-capture logging.
- Keep feature comparisons labeled starter-only unless baselines perform the same
  work.

**Acceptance:**

- [ ] Optional feature overhead is documented with refreshed numbers.
- [ ] Disabled diagnostics remain cheap on the default path.
- [ ] Enabled feature recommendations are updated in docs only when backed by
      release-quality data.
- [ ] No optional feature benchmark is presented as default starter overhead.

---

### 2.5 Audit Problem Detail error-mapping overhead

**Why:** `starterErrorMappingProblemDetailSmallBody` is a starter-only row with
high allocation per operation. It should stay documented separately, and any
optimization should preserve rich Problem Detail exceptions.

**What:**

- Confirm current-workspace Problem Detail overhead after V12 error-body changes.
- Audit JSON parse and exception construction allocation.
- Preserve truncation metadata, bounded body capture, and fallback behavior for
  malformed or truncated bodies.
- Do not compare this row against raw clients unless baselines install equivalent
  Problem Detail mapping.

**Acceptance:**

- [ ] Problem Detail before/after numbers are recorded if code changes.
- [ ] Error-body truncation metadata remains correct.
- [ ] Mapper fallback behavior remains covered by tests.
- [ ] Docs continue to label this as starter-only error-mapping overhead.

---

## 3. Benchmark and Documentation Correctness

### 3.1 Current-vs-baseline report pairing

**Why:** A promoted performance story needs both the candidate report and the
published-baseline report without overwriting either file.

**What:**

- Ensure report paths for current candidate and published baseline are distinct in
  release evidence and documentation.
- Add a generated or manual comparison note that says which starter versions are
  being compared.
- Make baseline artifact resolution failure visible before report promotion.
- Verify the promoted docs do not mix current candidate numbers with published
  baseline labels.

**Acceptance:**

- [ ] Current and baseline reports can coexist under stable paths.
- [ ] The promoted report identifies the starter version under test.
- [ ] Release evidence identifies the baseline starter version.
- [ ] Docs tests catch stale or mismatched promoted report links.

---

### 3.2 Benchmark threshold guidance without hard gates

**Why:** Local benchmark numbers vary too much for strict CI thresholds, but
maintainers still need guidance on what counts as a regression worth reviewing.

**What:**

- Add guidance for comparing scenario trends across release reports.
- Define review triggers for large relative changes, allocation spikes, or optional
  feature overhead jumps.
- Keep normal CI free of long-running performance gates.
- Prefer manual release review over flaky automated fail/pass thresholds.

**Acceptance:**

- [ ] Docs describe how to compare reports without treating one run as universal.
- [ ] Review triggers are clear but not enforced as normal CI gates.
- [ ] Release checklist tells maintainers when to rerun benchmarks.
- [ ] Performance docs remain honest about machine variability.

---

### 3.3 Public documentation consistency

**Why:** Once reports are published, stale links or broad claims can quickly reduce
trust.

**What:**

- Extend documentation tests to validate promoted report links and report headers.
- Check that public docs do not link `target/benchmark-reports` or smoke-only
  artifacts.
- Keep README, benchmark docs, changelog, and release compatibility docs aligned
  on benchmark commands and evidence rules.
- Add a small checklist for release maintainers before writing performance claims.

**Acceptance:**

- [ ] Docs tests validate promoted benchmark report links.
- [ ] Public docs contain no `target/benchmark-reports` evidence links.
- [ ] Changelog performance claims cite a scenario and report.
- [ ] README points to the benchmark docs and promoted report when available.

---

## Suggested Priority Order

1. Promote a versioned benchmark report.
2. Add performance summary documentation.
3. Release-note benchmark evidence workflow.
4. Current-vs-baseline report pairing.
5. Optimize default success-path overhead.
6. Optimize JSON request/response paths.
7. Optimize `ResponseEntity` envelope handling.
8. Audit optional feature overhead.
9. Audit Problem Detail error-mapping overhead.
10. Benchmark threshold guidance without hard gates.
11. Public documentation consistency.
