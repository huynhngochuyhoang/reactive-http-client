# Reactive HTTP Client — Roadmap V14 Checklist

Companion to [`ROADMAP.md`](ROADMAP.md). Execute priorities in order unless a
release blocker requires reordering.

---

## Priority 1 — Release `2.10.0` From Completed V12/V13 Work

### [x] 0.1 Release as `2.10.0`
- [x] Move Unreleased changelog entries into a dated `2.10.0` section.
- [x] Bump root and module Maven versions from `2.9.0` to `2.10.0`.
- [x] Update README dependency snippets to `2.10.0`.
- [x] Update quick-start dependency snippets to `2.10.0`.
- [x] Update generated configuration or release documentation references that
      include the current project version.
- [x] Set `api.compatibility.baseline.version` to `2.9.0` after published
      `2.9.0` artifacts are available.
- [x] Verify the API compatibility baseline does not equal the current reactor
      version.
- [x] Refresh or promote `docs/benchmark-report-2.10.0.md` for the release.
- [x] Update benchmark docs to point at the `2.10.0` promoted report.
- [x] Update performance summary to describe the `2.10.0` promoted report.
- [x] Update changelog comparison links for `2.10.0`.
- [x] Run normal tests.
- [x] Run API compatibility against published `2.9.0`.
- [x] Run `git diff --check`.
- [x] Run benchmark smoke.
- [x] Run release-quality benchmark when release notes include performance
      wording.
- [x] Verify release evidence manifest lists current and baseline benchmark
      paths.

Evidence:

- `mvn test` passed for the root reactor on `2.10.0`.
- Published baseline artifacts resolved after VPN was connected:
  `mvn dependency:get -Dartifact=io.github.huynhngochuyhoang:reactive-http-client-starter:2.9.0`,
  `mvn dependency:get -Dartifact=io.github.huynhngochuyhoang:reactive-http-client-test:2.9.0`,
  and `mvn dependency:get -Dartifact=io.github.huynhngochuyhoang:reactive-http-client-otel:2.9.0`.
- `mvn -Papi-compatibility -DskipTests verify` passed after the published
  `2.9.0` baseline artifacts resolved.
- `mvn -Pbenchmarks,benchmark-smoke -pl reactive-http-client-benchmarks -am verify`
  passed and wrote smoke-only benchmark output with project/starter `2.10.0`
  and API baseline `2.9.0`.
- `git diff --check` passed.
- `mvn -Pbenchmarks,benchmark-release -pl reactive-http-client-benchmarks -am verify -Dbenchmark.commit=$(git rev-parse --short HEAD)`
  produced the promoted `2.10.0` release-quality report. The report records
  release input commit `6b46be8`; the original runner property was `04aeb61`
  from the pre-commit release-prep workspace.

---

## Priority 2 — Benchmark Report Comparison Helper

### [x] 1.1 Add a benchmark report comparison helper
- [x] Choose a small implementation surface for report comparison.
- [x] Parse two JMH JSON reports by stable benchmark method name.
- [x] Compare rows by benchmark method and mode.
- [x] Print current value, baseline value, absolute delta, and relative delta.
- [x] Include average time when present.
- [x] Include p50, p95, and p99 when present.
- [x] Include throughput when present.
- [x] Include allocation per operation when present.
- [x] Mark V13 review-trigger crossings as `review`.
- [x] Keep the default command exit status successful for review-trigger rows.
- [x] Add an opt-in flag for non-zero exit on review-trigger rows if useful for
      local release review.
- [x] Keep generated comparison output under `target/`.
- [x] Document the comparison command in benchmark docs.
- [x] Document how to attach comparison output to release notes.
- [x] Verify normal CI does not run the comparison as a hard gate.
- [x] Add tests or fixtures for matching rows, missing rows, and review-trigger
      output.

---

## Priority 3 — Generated Release-Note Benchmark Evidence

### [x] 1.2 Generate release-note benchmark evidence from manifest data
- [x] Generate a Markdown benchmark evidence snippet from the release evidence
      manifest.
- [x] Include the promoted report path.
- [x] Include the current candidate benchmark command.
- [x] Include the published baseline benchmark command.
- [x] Include the current candidate report path.
- [x] Include the published baseline report path.
- [x] Include release-note scenario names.
- [x] Keep the generated snippet under `target/release-evidence/`.
- [x] Ensure the generated snippet uses the current project version.
- [x] Ensure the generated snippet uses the configured baseline version.
- [x] Ensure the generated snippet never presents smoke-only reports as promoted
      evidence.
- [x] Document that maintainers paste the generated block only after the promoted
      report exists.
- [x] Extend documentation tests to verify required generated fields.
- [x] Verify generated evidence remains target-only and is not committed.

---

## Priority 4 — Benchmark Consumer Example

### [x] 2.1 Add a small benchmark consumer example
- [x] Add a documentation page or example section for benchmark client shapes.
- [x] Show the raw `WebClient` version of one success-path scenario.
- [x] Show the Spring HTTP Interface version of the same scenario.
- [x] Show the starter interface version of the same scenario.
- [x] Explain which request and response work is equivalent across the compared
      clients.
- [x] Explain why optional feature rows are starter-only unless baselines do the
      same feature work.
- [x] Explain why Problem Detail rows are starter-only unless baselines install an
      equivalent mapper.
- [x] Link to benchmark methodology.
- [x] Link to the promoted benchmark report.
- [x] Avoid broad performance claims.
- [x] Add documentation tests for links and required headings.

---

## Priority 5 — Performance Troubleshooting Guide

### [ ] 2.2 Add a performance troubleshooting guide
- [ ] Add a troubleshooting guide for high outbound latency.
- [ ] Distinguish starter client abstraction overhead from downstream service
      latency.
- [ ] Distinguish network latency from app serialization and body processing.
- [ ] Explain when to inspect exchange logging metadata.
- [ ] Recommend `METADATA_ONLY` before body capture.
- [ ] Explain Micrometer tags and how to keep cardinality bounded.
- [ ] Explain lifecycle hooks and retry attempts as diagnostic signals.
- [ ] Explain timeout source diagnostics.
- [ ] Explain request and response body-size effects.
- [ ] Include a checklist for comparing user workload shape with benchmark rows.
- [ ] Link to observability docs.
- [ ] Link to exchange logging docs.
- [ ] Link to lifecycle hooks docs.
- [ ] Link to benchmark docs.
- [ ] Avoid universal performance promises.
- [ ] Add documentation tests for links and required guidance.

---

## Priority 6 — Re-Audit Default Success Path After `2.10.0`

### [ ] 3.1 Re-audit default success path after `2.10.0`
- [ ] Resolve published `2.10.0` artifacts before comparing.
- [ ] Run the current V14 workspace release-quality benchmark.
- [ ] Run the published `2.10.0` baseline benchmark.
- [ ] Keep current and baseline reports in distinct paths.
- [ ] Compare `Get No Body` rows.
- [ ] Compare `Get Path Query Header` rows.
- [ ] Compare `Post Json` rows.
- [ ] Compare `Response Entity` rows.
- [ ] Apply V13 review triggers as review-only signals.
- [ ] Prioritize only persistent movements that cross review triggers.
- [ ] Record before/after evidence for every code change.
- [ ] Verify diagnostics contracts remain covered.
- [ ] Verify lifecycle contracts remain covered.
- [ ] Verify retry and idempotency contracts remain covered.
- [ ] Verify streaming response ownership remains covered.

---

## Priority 7 — Request Argument Expansion Allocation Audit

### [ ] 3.2 Audit object allocation in request argument expansion
- [ ] Capture `Get Path Query Header` evidence before changing code.
- [ ] Inspect per-call allocation in path variable expansion.
- [ ] Inspect per-call allocation in query parameter expansion.
- [ ] Inspect per-call allocation in header parameter expansion.
- [ ] Reuse cached request-plan metadata where safe.
- [ ] Avoid changing URI-template validation behavior.
- [ ] Avoid changing query encoding behavior.
- [ ] Avoid changing multi-value header behavior.
- [ ] Avoid changing header precedence behavior.
- [ ] Avoid changing parameter validation behavior.
- [ ] Add no-network microbenchmark coverage only if current rows cannot isolate
      the overhead source.
- [ ] Keep any helper internal.
- [ ] Run URI-template tests.
- [ ] Run multi-value header tests.
- [ ] Document any improvement as scenario-specific.
- [ ] Do not claim universal raw `WebClient` parity.

---

## Priority 8 — Release Version and Benchmark Report Consistency

### [ ] 4.1 Guard release version and benchmark report consistency
- [ ] Extend documentation tests to verify the promoted report filename matches
      the project version for release candidates.
- [ ] Verify promoted report `projectVersion` matches the current project version
      when performance claims are present.
- [ ] Verify promoted report `starterVersion` matches the current project version
      when performance claims are present.
- [ ] Verify README benchmark report links match the current project version.
- [ ] Verify changelog benchmark report links match the release being drafted.
- [ ] Verify performance summary links match the current promoted report.
- [ ] Allow historical benchmark reports to remain under `docs/`.
- [ ] Prevent updating an old promoted report in place for a new version.
- [ ] Add tests for stale promoted report links.
- [ ] Add tests for mismatched promoted report metadata.

---

## Priority 9 — API Compatibility Baseline Release Awareness

### [ ] 4.2 Keep API compatibility baseline release-aware
- [ ] Document the exact sequence for cutting `2.10.0`.
- [ ] Document when to update `api.compatibility.baseline.version` after release.
- [ ] Keep the baseline guard dynamic.
- [ ] Keep the baseline guard profile-scoped.
- [ ] Verify module-scoped API compatibility still runs the guard.
- [ ] Align benchmark published-baseline commands with the API baseline version.
- [ ] Verify release evidence lists baseline artifacts for every published
      module.
- [ ] Verify self-comparison is still rejected.
- [ ] Verify published baseline artifact resolution commands use the configured
      baseline version.
- [ ] Update docs if the next development cycle changes the baseline sequence.
