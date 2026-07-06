# Reactive HTTP Client — Roadmap V18 Checklist

Companion to [`ROADMAP.md`](ROADMAP.md). Execute priorities in order unless a
release blocker requires reordering.

---

## Priority 1 — Post-`2.13.0` Baseline Transition

### [ ] 1.1 Move the next development line only after `2.13.0` resolves
- [ ] Confirm published `2.13.0` artifacts resolve before changing the baseline.
- [ ] Resolve `io.github.huynhngochuyhoang:reactive-http-client-starter:2.13.0`.
- [ ] Resolve `io.github.huynhngochuyhoang:reactive-http-client-test:2.13.0`.
- [ ] Resolve `io.github.huynhngochuyhoang:reactive-http-client-otel:2.13.0`.
- [ ] Bump the next development reactor version so it does not equal the
      `2.13.0` API baseline.
- [ ] Move `api.compatibility.baseline.version` to `2.13.0` only after artifact
      resolution succeeds.
- [ ] Update benchmark published-baseline commands to
      `-Dbenchmark.starter.version=2.13.0`.
- [ ] Update benchmark published-baseline report paths to
      `published-starter-2.13.0`.
- [ ] Update README, quick start, release compatibility docs, benchmark docs,
      changelog links, and generated release evidence together.
- [ ] Verify root API compatibility passes against published `2.13.0`.
- [ ] Verify module-scoped starter API compatibility passes against published
      `2.13.0`.
- [ ] Verify root self-comparison guard rejects the current reactor version.
- [ ] Verify module-scoped self-comparison guard rejects the current reactor
      version.
- [ ] Run focused release documentation tests.
- [ ] Run `git diff --check`.

Evidence:

- Pending.

---

## Priority 2 — Generated Release-Prep Consistency Checklist

### [ ] 2.1 Add a generated checklist summary to release evidence
- [ ] Extend the generated release evidence manifest with one concise
      checklist-style release-prep summary for the current project version.
- [ ] Include changelog section status.
- [ ] Include README, quick-start, and version-snippet status.
- [ ] Include published-baseline artifact resolution commands.
- [ ] Include root and module-scoped API compatibility commands.
- [ ] Include the API compatibility fixture command.
- [ ] Include benchmark smoke, release, and published-baseline commands.
- [ ] Include promoted benchmark report status.
- [ ] Include generated-doc and Markdown-link validation status.
- [ ] Keep the checklist as a visible manual command list, not hidden automation.
- [ ] Ensure target-only generated manifests are not committed as release proof.
- [ ] Add or update tests so release evidence drift fails fast.
- [ ] Run focused release documentation tests.
- [ ] Run `git diff --check`.

Evidence:

- Pending.

---

## Priority 3 — Changelog and Release-Note Performance Wording Guard

### [ ] 2.2 Re-audit release performance wording against promoted reports
- [ ] Validate current-release changelog performance wording against promoted
      report availability.
- [ ] Reject current-release benchmark links that point at missing reports.
- [ ] Reject current-release benchmark links that point at `target/` artifacts.
- [ ] Allow historical release sections to retain historical promoted report
      links without rewriting old entries.
- [ ] Document acceptable release-note wording when no performance claim is
      included.
- [ ] Ensure public performance claims cite source-controlled promoted reports
      only.
- [ ] Run focused release documentation tests.
- [ ] Run `git diff --check`.

Evidence:

- Pending.

---

## Priority 4 — Support-Bundle Capture Examples

### [ ] 3.1 Add capture examples for common deployment shapes
- [ ] Add or extend support-bundle examples for local JVM capture.
- [ ] Add or extend support-bundle examples for container capture.
- [ ] Add or extend support-bundle examples for Kubernetes-style capture.
- [ ] Keep every hostname, token, namespace, service name, and credential value
      fake or placeholder-based.
- [ ] Show health details, `rhttpclients`, startup summaries, metadata-only
      exchange logs, and benchmark report references as separate evidence
      streams.
- [ ] Explain which evidence stream answers which support question.
- [ ] Validate documented `reactive.http.*` properties against metadata.
- [ ] Ensure examples do not introduce new public APIs.
- [ ] Run focused documentation and metadata tests.
- [ ] Run `git diff --check`.

Evidence:

- Pending.

---

## Priority 5 — Strict Validation Adoption Message Audit

### [ ] 3.2 Re-audit strict validation failure messages
- [ ] Review strict retry validation failures for inherited endpoints.
- [ ] Review strict retry validation failures for `@ApiRef` endpoints.
- [ ] Review strict retry validation failures for dynamic headers and default
      `Idempotency-Key` interactions.
- [ ] Review strict retry validation behavior for disabled, single-attempt, and
      unavailable retry operators.
- [ ] Review strict SigV4 validation failures for custom auth providers.
- [ ] Review strict SigV4 validation failures for dynamic content types and
      ambiguous body shapes.
- [ ] Improve message text only where remediation is unclear.
- [ ] Keep warning-only runtime behavior unchanged when strict modes are
      disabled.
- [ ] Document how to choose between method annotations, client defaults, and
      custom provider ownership.
- [ ] Add or update focused tests for any changed message contract.
- [ ] Run focused tests.
- [ ] Run `git diff --check`.

Evidence:

- Pending.

---

## Priority 6 — Public API Compatibility Coverage Alignment

### [ ] 4.1 Keep documented helper usage aligned with japicmp includes
- [ ] Re-run the documented public surface map against japicmp include patterns.
- [ ] Confirm diagnostics snapshot helpers are compatibility-covered.
- [ ] Confirm contract snapshot helpers and nested fluent types are
      compatibility-covered.
- [ ] Confirm redaction helpers documented for custom loggers are
      compatibility-covered or explicitly excluded.
- [ ] Confirm metadata cache replacement types are compatibility-covered or no
      longer documented as replacement surfaces.
- [ ] Confirm test helper public APIs documented for applications are covered.
- [ ] Confirm OTel companion public types are covered or explicitly scoped.
- [ ] Add API compatibility fixtures for any newly covered helper before
      release.
- [ ] Keep internal implementation classes out of compatibility promises.
- [ ] Run focused release documentation tests.
- [ ] Run API compatibility fixture script.
- [ ] Run root API compatibility.
- [ ] Run module-scoped starter API compatibility.
- [ ] Run `git diff --check`.

Evidence:

- Pending.

---

## Priority 7 — Configuration Metadata and Native Hint Re-Audit

### [ ] 4.2 Re-audit generated metadata, docs, and runtime hints
- [ ] Verify generated configuration reference matches metadata.
- [ ] Verify documented `reactive.http.*` property names exist in metadata.
- [ ] Verify YAML and properties examples do not assign scalar values to groups.
- [ ] Verify metadata source types and source methods resolve for nested client
      groups.
- [ ] Verify OTel metadata checks remain in the OTel module or an appropriate
      test classpath.
- [ ] Verify runtime hints cover public nested configuration property types.
- [ ] Verify optional Actuator behavior remains conditional.
- [ ] Verify optional OTel behavior remains conditional.
- [ ] Run configuration metadata tests.
- [ ] Run focused native/AOT smoke tests if runtime hints change.
- [ ] Run `git diff --check`.

Evidence:

- Pending.

---

## Priority 8 — Benchmark Evidence Scope and Classification Review

### [ ] 5.1 Keep benchmark evidence tied to release decisions
- [ ] Confirm smoke benchmark commands are current for the active release line.
- [ ] Confirm release benchmark commands are current for the active release line.
- [ ] Confirm published-baseline benchmark commands are current for the active
      baseline.
- [ ] Confirm current and published-baseline report output paths remain distinct.
- [ ] Add benchmark rows only when they isolate a changed request path, optional
      feature path, startup validation path, or support endpoint rendering path.
- [ ] Ensure every new benchmark row has an explicit prefix classification.
- [ ] Keep no-network rows classified separately from loopback feature rows.
- [ ] Keep benchmark threshold crossings as manual review triggers, not normal
      CI hard gates.
- [ ] Promote release-quality benchmark reports only when public performance
      claims require them.
- [ ] Run benchmark/report documentation tests.
- [ ] Run `git diff --check`.

Evidence:

- Pending.

---

## Priority 9 — Dependency Baseline Review Preparation

### [ ] 5.2 Prepare dependency baseline review without upgrading by default
- [ ] Review Spring Boot 3.5.x patch movement and managed WebFlux/Reactor
      Netty/Micrometer/OpenTelemetry versions.
- [ ] Review Resilience4j baseline compatibility and optional dependency
      behavior.
- [ ] Confirm dependency docs still name Java 21 and Spring Boot 3.5.x support.
- [ ] Confirm versionless module dependencies continue to inherit managed
      versions.
- [ ] Confirm benchmark reports continue to record dependency-management source.
- [ ] Document any proposed baseline upgrade separately from feature work.
- [ ] Ensure no dependency drift bypasses generated release evidence.
- [ ] Run focused dependency/release documentation tests.
- [ ] Run `git diff --check`.

Evidence:

- Pending.

---

## Priority 10 — V18 Release Readiness

### [ ] 6.1 Keep V18 small enough to release confidently
- [ ] Decide patch versus minor after V18 scope is finalized.
- [ ] Keep changelog entries under `Unreleased` while V18 work is active.
- [ ] Ensure release evidence names the selected next version.
- [ ] Ensure release evidence names the selected API baseline.
- [ ] Verify generated configuration docs are current.
- [ ] Verify Markdown links pass across docs and roadmaps.
- [ ] Verify baseline artifact resolution commands are listed.
- [ ] Verify root and module-scoped API compatibility commands are listed.
- [ ] Verify API compatibility fixture command is listed.
- [ ] Verify benchmark smoke, release, and published-baseline commands are listed
      when performance evidence is needed.
- [ ] Run focused release documentation tests.
- [ ] Run full reactor tests.
- [ ] Run API compatibility.
- [ ] Run module-scoped starter API compatibility.
- [ ] Run API compatibility fixture script.
- [ ] Run `git diff --check`.
- [ ] Mark `ROADMAP.md` completed only after implementation and evidence are
      complete.

Evidence:

- Pending.
