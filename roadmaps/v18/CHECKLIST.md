# Reactive HTTP Client — Roadmap V18 Checklist

Companion to [`ROADMAP.md`](ROADMAP.md). Execute priorities in order unless a
release blocker requires reordering.

---

## Priority 1 — Post-`2.13.0` Baseline Transition

### [x] 1.1 Move the next development line only after `2.13.0` resolves
- [x] Confirm published `2.13.0` artifacts resolve before changing the baseline.
- [x] Resolve `io.github.huynhngochuyhoang:reactive-http-client-starter:2.13.0`.
- [x] Resolve `io.github.huynhngochuyhoang:reactive-http-client-test:2.13.0`.
- [x] Resolve `io.github.huynhngochuyhoang:reactive-http-client-otel:2.13.0`.
- [x] Bump the next development reactor version so it does not equal the
      `2.13.0` API baseline.
- [x] Move `api.compatibility.baseline.version` to `2.13.0` only after artifact
      resolution succeeds.
- [x] Update benchmark published-baseline commands to
      `-Dbenchmark.starter.version=2.13.0`.
- [x] Update benchmark published-baseline report paths to
      `published-starter-2.13.0`.
- [x] Update README, quick start, release compatibility docs, benchmark docs,
      changelog links, and generated release evidence together.
- [x] Verify root API compatibility passes against published `2.13.0`.
- [x] Verify module-scoped starter API compatibility passes against published
      `2.13.0`.
- [x] Verify root self-comparison guard rejects the current reactor version.
- [x] Verify module-scoped self-comparison guard rejects the current reactor
      version.
- [x] Run focused release documentation tests.
- [x] Run `git diff --check`.

Evidence:

- `mvn -q dependency:get -Dartifact=io.github.huynhngochuyhoang:reactive-http-client-starter:2.13.0` passed.
- `mvn -q dependency:get -Dartifact=io.github.huynhngochuyhoang:reactive-http-client-test:2.13.0` passed.
- `mvn -q dependency:get -Dartifact=io.github.huynhngochuyhoang:reactive-http-client-otel:2.13.0` passed.
- Bumped the reactor and module parent versions to `2.14.0`, keeping the next development version distinct from the published `2.13.0` API baseline.
- Moved `api.compatibility.baseline.version` to `2.13.0` only after published artifact resolution succeeded.
- Updated README and quick-start dependency snippets to `2.14.0`.
- Updated release compatibility docs, benchmark published-baseline commands, benchmark comparison paths, and performance-summary baseline wording to use `2.13.0` and `published-starter-2.13.0`.
- Kept promoted benchmark report links tied to the latest source-controlled `2.12.0` report; no `2.13.0` or `2.14.0` public performance claim was introduced here.
- Added the `Unreleased` changelog entry for the post-`2.13.0` baseline transition; the `Unreleased` comparison already starts at `v2.13.0...HEAD`.
- `mvn -q -pl reactive-http-client-starter -Dtest=DocumentationReleaseArtifactTest test` passed.
- `mvn -q -pl reactive-http-client-starter -Dtest=ReactiveHttpClientConfigurationMetadataTest#documentedReactiveHttpPropertiesExistInGeneratedMetadata test` passed.
- `mvn -q -Papi-compatibility -DskipTests verify` passed against the published `2.13.0` baseline.
- `mvn -q -pl reactive-http-client-starter -Papi-compatibility -DskipTests verify` passed against the published `2.13.0` baseline.
- `mvn -Papi-compatibility -DskipTests -Dapi.compatibility.baseline.version=2.14.0 validate` failed as expected with the self-comparison guard message.
- `mvn -pl reactive-http-client-starter -Papi-compatibility -DskipTests -Dapi.compatibility.baseline.version=2.14.0 validate` failed as expected with the self-comparison guard message.
- `git diff --check` passed.

---

## Priority 2 — Generated Release-Prep Consistency Checklist

### [x] 2.1 Add a generated checklist summary to release evidence
- [x] Extend the generated release evidence manifest with one concise
      checklist-style release-prep summary for the current project version.
- [x] Include changelog section status.
- [x] Include README, quick-start, and version-snippet status.
- [x] Include published-baseline artifact resolution commands.
- [x] Include root and module-scoped API compatibility commands.
- [x] Include the API compatibility fixture command.
- [x] Include benchmark smoke, release, and published-baseline commands.
- [x] Include promoted benchmark report status.
- [x] Include generated-doc and Markdown-link validation status.
- [x] Keep the checklist as a visible manual command list, not hidden automation.
- [x] Ensure target-only generated manifests are not committed as release proof.
- [x] Add or update tests so release evidence drift fails fast.
- [x] Run focused release documentation tests.
- [x] Run `git diff --check`.

Evidence:

- Added a top-level `releasePrepChecklist` object to the generated release
  evidence manifest. It includes checklist items for changelog status,
  README/quick-start version snippets, published-baseline artifact resolution,
  API compatibility, benchmark evidence, promoted benchmark report status,
  generated-doc/link status, and target-only evidence handling.
- The checklist exposes `manualCommands` as the one-place pending release-work
  list while keeping the generated manifest under `target/release-evidence/`.
- Updated `docs/20-native-release-compatibility.md` to document the new
  `releasePrepChecklist` field and the rule that generated target evidence is
  not source-controlled release proof.
- Updated `DocumentationReleaseArtifactTest` so release evidence generation fails
  when the checklist is missing required items, commands, version snippets,
  changelog compare-link status, generated-doc status, or target-only evidence
  metadata.
- `mvn -q -pl reactive-http-client-starter -Dtest=DocumentationReleaseArtifactTest test` passed.
- `mvn -q -pl reactive-http-client-starter -Dtest=ReactiveHttpClientConfigurationMetadataTest#documentedReactiveHttpPropertiesExistInGeneratedMetadata test` passed.
- `jq '.releasePrepChecklist | {status, projectVersion, apiCompatibilityBaselineVersion, itemIds: [.items[].id], manualCommandCount: (.manualCommands | length)}' target/release-evidence/reactive-http-client-release-evidence.json` showed status `pending`, project `2.14.0`, baseline `2.13.0`, eight checklist item IDs, and 11 manual commands.
- `git diff --check` passed.

---

## Priority 3 — Changelog and Release-Note Performance Wording Guard

### [x] 2.2 Re-audit release performance wording against promoted reports
- [x] Validate current-release changelog performance wording against promoted
      report availability.
- [x] Reject current-release benchmark links that point at missing reports.
- [x] Reject current-release benchmark links that point at `target/` artifacts.
- [x] Allow historical release sections to retain historical promoted report
      links without rewriting old entries.
- [x] Document acceptable release-note wording when no performance claim is
      included.
- [x] Ensure public performance claims cite source-controlled promoted reports
      only.
- [x] Run focused release documentation tests.
- [x] Run `git diff --check`.

Evidence:

- Added a current-release changelog guard to `DocumentationReleaseArtifactTest`.
  It validates `Unreleased` until a `2.14.0` release section exists, rejects
  current-release benchmark links to missing reports or `target/` artifacts, and
  requires current-release public performance claims to cite a source-controlled
  promoted report for the same release version.
- Added a focused regression test that allows baseline-transition/no-claim
  wording while rejecting stale promoted-report claims, missing promoted-report
  links, and target-only benchmark links.
- Updated `docs/22-benchmarks.md` with acceptable release-note wording when no
  public performance claim is included.
- Added an `Unreleased` changelog entry for the release-note evidence guard
  without introducing a public performance claim.
- `mvn -q -pl reactive-http-client-starter -Dtest=DocumentationReleaseArtifactTest test` passed.
- `mvn -q -pl reactive-http-client-starter -Dtest=ReactiveHttpClientConfigurationMetadataTest#documentedReactiveHttpPropertiesExistInGeneratedMetadata test` passed.
- `git diff --check` passed.

---

## Priority 4 — Support-Bundle Capture Examples

### [x] 3.1 Add capture examples for common deployment shapes
- [x] Add or extend support-bundle examples for local JVM capture.
- [x] Add or extend support-bundle examples for container capture.
- [x] Add or extend support-bundle examples for Kubernetes-style capture.
- [x] Keep every hostname, token, namespace, service name, and credential value
      fake or placeholder-based.
- [x] Show health details, `rhttpclients`, startup summaries, metadata-only
      exchange logs, and benchmark report references as separate evidence
      streams.
- [x] Explain which evidence stream answers which support question.
- [x] Validate documented `reactive.http.*` properties against metadata.
- [x] Ensure examples do not introduce new public APIs.
- [x] Run focused documentation and metadata tests.
- [x] Run `git diff --check`.

Evidence:

- Added `docs/26-support-bundles.md` capture recipes for local JVM, container,
  and Kubernetes-style collection using placeholder management URLs, container
  names, namespace names, pod names, file paths, and sanitized configuration
  inputs.
- Added an evidence-stream table that keeps `rhttpclients`, health details,
  startup summaries, metadata-only exchange logs, sanitized configuration, and
  release-evidence references separate and explains what question each stream
  answers.
- Replaced the remaining concrete localhost diagnostics-endpoint example with
  the placeholder-based `EXAMPLE_MANAGEMENT_URL` form.
- Extended `ReactiveHttpClientConfigurationMetadataTest` so the support-bundle
  fixture and capture recipes remain metadata-valid, placeholder-based, and free
  of real-looking hosts or secret-bearing strings.
- Added an `Unreleased` changelog entry for the support-bundle capture recipes
  without introducing a public performance claim.
- `mvn -q -pl reactive-http-client-starter -Dtest=ReactiveHttpClientConfigurationMetadataTest test` passed.
- `mvn -q -pl reactive-http-client-starter -Dtest=DocumentationReleaseArtifactTest test` passed.
- `git diff --check` passed.

---

## Priority 5 — Strict Validation Adoption Message Audit

### [x] 3.2 Re-audit strict validation failure messages
- [x] Review strict retry validation failures for inherited endpoints.
- [x] Review strict retry validation failures for `@ApiRef` endpoints.
- [x] Review strict retry validation failures for dynamic headers and default
      `Idempotency-Key` interactions.
- [x] Review strict retry validation behavior for disabled, single-attempt, and
      unavailable retry operators.
- [x] Review strict SigV4 validation failures for custom auth providers.
- [x] Review strict SigV4 validation failures for dynamic content types and
      ambiguous body shapes.
- [x] Improve message text only where remediation is unclear.
- [x] Keep warning-only runtime behavior unchanged when strict modes are
      disabled.
- [x] Document how to choose between method annotations, client defaults, and
      custom provider ownership.
- [x] Add or update focused tests for any changed message contract.
- [x] Run focused tests.
- [x] Run `git diff --check`.

Evidence:

- Strict retry failures now distinguish direct annotations from resolved
  `@ApiRef` endpoints, retain concrete-versus-declaring interface context for
  inherited methods, and report whether the key is missing, runtime-provided,
  or made unprovable by a dynamic override of a client default.
- Strict built-in SigV4 failures now report endpoint ownership and a concrete
  built-in body-contract or custom-provider remediation path while retaining
  the existing dynamic content-type and ambiguous body-shape reasons.
- Existing focused coverage confirms strict retry remains dormant when disabled,
  single-attempt, or unavailable, and strict SigV4 validation continues to skip
  named providers and custom factory selections.
- Added retry and SigV4 ownership decision tables to `docs/07-resilience4j.md`
  and `docs/06-auth-providers.md`.
- `mvn -q -pl reactive-http-client-starter -Dtest=ReactiveHttpClientFactoryBeanDiagnosticsTest test` passed.
- `mvn -q -pl reactive-http-client-starter -Dtest=ReactiveHttpClientConfigurationMetadataTest,DocumentationReleaseArtifactTest test` passed.
- `git diff --check` passed.

---

## Priority 6 — Public API Compatibility Coverage Alignment

### [x] 4.1 Keep documented helper usage aligned with japicmp includes
- [x] Re-run the documented public surface map against japicmp include patterns.
- [x] Confirm diagnostics snapshot helpers are compatibility-covered.
- [x] Confirm contract snapshot helpers and nested fluent types are
      compatibility-covered.
- [x] Confirm redaction helpers documented for custom loggers are
      compatibility-covered or explicitly excluded.
- [x] Confirm metadata cache replacement types are compatibility-covered or no
      longer documented as replacement surfaces.
- [x] Confirm test helper public APIs documented for applications are covered.
- [x] Confirm OTel companion public types are covered or explicitly scoped.
- [x] Add API compatibility fixtures for any newly covered helper before
      release.
- [x] Keep internal implementation classes out of compatibility promises.
- [x] Run focused release documentation tests.
- [x] Run API compatibility fixture script.
- [x] Run root API compatibility.
- [x] Run module-scoped starter API compatibility.
- [x] Run `git diff --check`.

Evidence:

- The documented public-surface map and the root japicmp include set remain an
  exact match. No production include pattern was added: diagnostics snapshots,
  contract snapshot nested APIs, `SensitiveHeaders`, `MethodMetadataCache`,
  `MethodMetadata*`, and `ResilienceOperatorApplier*` were already covered.
- Confirmed the package-level test-helper pattern covers every documented helper
  in `reactive-http-client-test`, and the OTel package pattern covers all four
  public companion types. The compatibility guide now lists those types
  explicitly.
- Kept proxy invocation, URI/argument resolution, transport/TLS, and generated
  release-test implementation classes outside the compatibility promise.
- Expanded the compatibility fixtures so additive changes pass while removal of
  a public constructor, nested fluent method, or public enum constant fails.
- `mvn -q -pl reactive-http-client-starter -Dtest=DocumentationReleaseArtifactTest test` passed.
- `bash scripts/verify-api-compatibility-fixtures.sh` passed.
- `mvn -q -Papi-compatibility -DskipTests verify` passed against published `2.13.0`.
- `mvn -q -pl reactive-http-client-starter -Papi-compatibility -DskipTests verify` passed against published `2.13.0`.
- `git diff --check` passed.

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
