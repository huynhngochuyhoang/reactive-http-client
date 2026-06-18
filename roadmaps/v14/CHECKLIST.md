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

### [x] 2.2 Add a performance troubleshooting guide
- [x] Add a troubleshooting guide for high outbound latency.
- [x] Distinguish starter client abstraction overhead from downstream service
      latency.
- [x] Distinguish network latency from app serialization and body processing.
- [x] Explain when to inspect exchange logging metadata.
- [x] Recommend `METADATA_ONLY` before body capture.
- [x] Explain Micrometer tags and how to keep cardinality bounded.
- [x] Explain lifecycle hooks and retry attempts as diagnostic signals.
- [x] Explain timeout source diagnostics.
- [x] Explain request and response body-size effects.
- [x] Include a checklist for comparing user workload shape with benchmark rows.
- [x] Link to observability docs.
- [x] Link to exchange logging docs.
- [x] Link to lifecycle hooks docs.
- [x] Link to benchmark docs.
- [x] Avoid universal performance promises.
- [x] Add documentation tests for links and required guidance.

---

## Priority 6 — Re-Audit Default Success Path After `2.10.0`

### [x] 3.1 Re-audit default success path after `2.10.0`
- [x] Resolve published `2.10.0` artifacts before comparing.
- [x] Run the current V14 workspace release-quality benchmark.
- [x] Run the published `2.10.0` baseline benchmark.
- [x] Keep current and baseline reports in distinct paths.
- [x] Compare `Get No Body` rows.
- [x] Compare `Get Path Query Header` rows.
- [x] Compare `Post Json` rows.
- [x] Compare `Response Entity` rows.
- [x] Apply V13 review triggers as review-only signals.
- [x] Prioritize only persistent movements that cross review triggers.
- [x] Record before/after evidence for every code change.
- [x] Verify diagnostics contracts remain covered.
- [x] Verify lifecycle contracts remain covered.
- [x] Verify retry and idempotency contracts remain covered.
- [x] Verify streaming response ownership remains covered.

Evidence:

- Published `2.10.0` artifacts resolved before comparison:
  `reactive-http-client-starter`, `reactive-http-client-test`, and
  `reactive-http-client-otel`.
- Published `2.10.0` benchmark baseline was generated from
  `reactive-http-client-benchmarks/target/benchmark-reports/published-starter-2.10.0/release-jmh.json`
  and records `starterVersion=2.10.0`, `projectVersion=2.10.0`, and
  `benchmarkCommit=2.10.0`.
- Current V14 workspace benchmark was generated from
  `reactive-http-client-benchmarks/target/benchmark-reports/v14-current/release-jmh.json`
  and records `starterVersion=2.10.0`, `projectVersion=2.10.0`, and
  `benchmarkCommit=ce5baf0`.
- Comparison output:
  `reactive-http-client-benchmarks/target/benchmark-reports/v14-current-vs-published-2.10.0.md`.
- The comparison covers `Get No Body`, `Get Path Query Header`, `Post Json`,
  and `Response Entity` rows for raw `WebClient`, Spring HTTP Interface, and
  the starter. V13 threshold crossings are reported with `review` status and
  remain informational.
- Requested starter default success-path rows show no allocation regression that
  crosses the V13 allocation review trigger. Review-trigger timing rows were not
  treated as a release blocker because this priority made no runtime code change
  and the movements need a same-machine rerun before being treated as persistent.
- No before/after code evidence is attached because this priority was an audit
  only; no runtime code was changed.
- Contract verification:
  `mvn -pl reactive-http-client-starter -Dtest=ReactiveHttpClientFactoryBeanDiagnosticsTest,ReactiveHttpClientLifecycleHookTest,ReactiveClientInvocationHandlerRetrySafetyTest,IdempotencyKeySupportTest,StreamingResponseTest,SubscriptionLocalReportingStateTest,ResponseEntitySupportTest test`
  passed with 108 tests, 0 failures, 0 errors, and 0 skipped.

---

## Priority 7 — Request Argument Expansion Allocation Audit

### [x] 3.2 Audit object allocation in request argument expansion
- [x] Capture `Get Path Query Header` evidence before changing code.
- [x] Inspect per-call allocation in path variable expansion.
- [x] Inspect per-call allocation in query parameter expansion.
- [x] Inspect per-call allocation in header parameter expansion.
- [x] Reuse cached request-plan metadata where safe.
- [x] Avoid changing URI-template validation behavior.
- [x] Avoid changing query encoding behavior.
- [x] Avoid changing multi-value header behavior.
- [x] Avoid changing header precedence behavior.
- [x] Avoid changing parameter validation behavior.
- [x] Add no-network microbenchmark coverage only if current rows cannot isolate
      the overhead source.
- [x] Keep any helper internal.
- [x] Run URI-template tests.
- [x] Run multi-value header tests.
- [x] Document any improvement as scenario-specific.
- [x] Do not claim universal raw `WebClient` parity.

Evidence:

- Before evidence came from the V14 current release-quality benchmark:
  `reactive-http-client-benchmarks/target/benchmark-reports/v14-current/release-jmh.md`.
  `clientSideOverheadStarterGetPathQueryHeader` measured 27,484.809 B/op
  in throughput mode, 27,039.796 B/op in average-time mode, and
  27,350.477 B/op in sample mode.
- Isolated request-argument evidence already exists in the same report:
  `argumentResolutionPathQueryHeaderFromMetadata` and
  `argumentResolutionPathQueryHeaderFromPlan` both measured 984 B/op. This
  isolates path-variable, query-parameter, and header-parameter resolution from
  WebClient transport and URI-building costs.
- Inspection confirmed the invocation path resolves arguments from cached
  `RequestPlan` metadata when available. No extra no-network benchmark was added
  because the existing `argumentResolutionPathQueryHeaderFromPlan` row already
  isolates the requested overhead source.
- No runtime code changed in this audit. That preserves URI-template validation,
  query encoding, multi-value header behavior, header precedence, and parameter
  validation semantics.
- Verification: `mvn -pl reactive-http-client-starter -Dtest=ReactiveClientInvocationHandlerBehaviorTest,HeaderParamMapSupportTest,MethodMetadataValidationTest,ReactiveHttpClientFactoryBeanDiagnosticsTest test`
  passed with 94 tests, 0 failures, 0 errors, and 0 skipped.
- No performance claim is made beyond this scenario-specific audit evidence.

---

## Priority 8 — Release Version and Benchmark Report Consistency

### [x] 4.1 Guard release version and benchmark report consistency
- [x] Extend documentation tests to verify the promoted report filename matches
      the project version for release candidates.
- [x] Verify promoted report `projectVersion` matches the current project version
      when performance claims are present.
- [x] Verify promoted report `starterVersion` matches the current project version
      when performance claims are present.
- [x] Verify README benchmark report links match the current project version.
- [x] Verify changelog benchmark report links match the release being drafted.
- [x] Verify performance summary links match the current promoted report.
- [x] Allow historical benchmark reports to remain under `docs/`.
- [x] Prevent updating an old promoted report in place for a new version.
- [x] Add tests for stale promoted report links.
- [x] Add tests for mismatched promoted report metadata.

Evidence:

- Added `promotedBenchmarkReportVersionsMatchReleaseDocumentation()` to
  `DocumentationReleaseArtifactTest`. The test validates that the current
  promoted report path is `docs/benchmark-report-<projectVersion>.md`, that
  README, benchmark docs, performance summary, benchmark consumer examples, and
  the current changelog release section reference only the current project
  version report, and that stale `benchmark-report-*.md` references fail.
- The same test scans every source-controlled `docs/benchmark-report-*.md` file
  and verifies its filename version matches its `Report version`, `projectVersion`,
  `Starter version under test`, and `starterVersion` metadata. That keeps
  historical reports allowed while preventing an old promoted report from being
  edited in place for a new release.
- Verification: `mvn -pl reactive-http-client-starter -Dtest=DocumentationReleaseArtifactTest test`
  passed with 8 tests, 0 failures, 0 errors, and 0 skipped.

---

## Priority 9 — API Compatibility Baseline Release Awareness

### [x] 4.2 Keep API compatibility baseline release-aware
- [x] Document the exact sequence for cutting `2.10.0`.
- [x] Document when to update `api.compatibility.baseline.version` after release.
- [x] Keep the baseline guard dynamic.
- [x] Keep the baseline guard profile-scoped.
- [x] Verify module-scoped API compatibility still runs the guard.
- [x] Align benchmark published-baseline commands with the API baseline version.
- [x] Verify release evidence lists baseline artifacts for every published
      module.
- [x] Verify self-comparison is still rejected.
- [x] Verify published baseline artifact resolution commands use the configured
      baseline version.
- [x] Update docs if the next development cycle changes the baseline sequence.

Evidence:

- `docs/20-native-release-compatibility.md` now documents the `2.10.0` release
  baseline sequence: keep `api.compatibility.baseline.version=2.9.0` while
  cutting `2.10.0`, resolve all published `2.9.0` baseline artifacts, run root
  and module-scoped API compatibility, and move the baseline to `2.10.0` only
  after `2.10.0` artifacts are published and the next development version is
  active.
- `docs/22-benchmarks.md` now states that the published-starter benchmark
  version must match the root `api.compatibility.baseline.version` and that the
  `published-starter-<version>` report paths must move with that property.
- Added `apiCompatibilityBaselineReleaseDocsStayAlignedWithPom()` to
  `DocumentationReleaseArtifactTest`. It verifies release docs, benchmark docs,
  generated release evidence artifact-resolution commands, and published-starter
  benchmark commands all use the configured API baseline version.
- `mvn -pl reactive-http-client-starter -Dtest=DocumentationReleaseArtifactTest test`
  passed with 9 tests, 0 failures, 0 errors, and 0 skipped.
- `mvn -pl reactive-http-client-starter -Papi-compatibility -DskipTests validate`
  passed and executed `reject-current-api-baseline` in the selected module.
- `mvn -pl reactive-http-client-starter -Papi-compatibility -DskipTests -Dapi.compatibility.baseline.version=2.10.0 validate`
  failed as expected with the baseline guard message rejecting the current
  reactor version as the API baseline.
