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

### [x] 4.2 Re-audit generated metadata, docs, and runtime hints
- [x] Verify generated configuration reference matches metadata.
- [x] Verify documented `reactive.http.*` property names exist in metadata.
- [x] Verify YAML and properties examples do not assign scalar values to groups.
- [x] Verify metadata source types and source methods resolve for nested client
      groups.
- [x] Verify OTel metadata checks remain in the OTel module or an appropriate
      test classpath.
- [x] Verify runtime hints cover public nested configuration property types.
- [x] Verify optional Actuator behavior remains conditional.
- [x] Verify optional OTel behavior remains conditional.
- [x] Run configuration metadata tests.
- [x] Run focused native/AOT smoke tests if runtime hints change.
- [x] Run `git diff --check`.

Evidence:

- Confirmed `docs/configuration-properties.md` exactly matches the generated
  reference from both starter and OTel metadata resources.
- Confirmed every documented `reactive.http.*` name and every YAML/properties
  scalar leaf resolves to property metadata; group-only assignments and
  malformed API-map leaves remain rejected.
- Starter metadata tests resolve nested client-group source types, source
  methods, and declared return types on the starter classpath. OTel group source
  types remain validated separately by `OpenTelemetryConfigurationMetadataTest`
  on the companion module classpath.
- Confirmed `ReactiveHttpClientRuntimeHints` covers every public nested
  `ReactiveHttpClientProperties` type. No production hint change was needed;
  the existing dynamic AOT assertion prevents future nested-type omissions.
- Existing starter coverage confirms the diagnostics endpoint is disabled by
  default and backs off when Actuator endpoint classes are unavailable.
- Added OTel auto-configuration tests proving it backs off when either the
  `OpenTelemetry` bean or OTel API classes are absent.
- `mvn -q -pl reactive-http-client-starter -Dtest=ReactiveHttpClientConfigurationMetadataTest,DocumentationReleaseArtifactTest,ReactiveHttpClientAotSmokeTest,ReactiveHttpClientAutoConfigurationTest test` passed.
- `mvn -q -pl reactive-http-client-otel -am -Dtest=OpenTelemetryConfigurationMetadataTest,OpenTelemetryHttpClientAutoConfigurationTest -Dsurefire.failIfNoSpecifiedTests=false test` passed.
- `git diff --check` passed.

---

## Priority 8 — Benchmark Evidence Scope and Classification Review

### [x] 5.1 Keep benchmark evidence tied to release decisions
- [x] Confirm smoke benchmark commands are current for the active release line.
- [x] Confirm release benchmark commands are current for the active release line.
- [x] Confirm published-baseline benchmark commands are current for the active
      baseline.
- [x] Confirm current and published-baseline report output paths remain distinct.
- [x] Add benchmark rows only when they isolate a changed request path, optional
      feature path, startup validation path, or support endpoint rendering path.
- [x] Ensure every new benchmark row has an explicit prefix classification.
- [x] Keep no-network rows classified separately from loopback feature rows.
- [x] Keep benchmark threshold crossings as manual review triggers, not normal
      CI hard gates.
- [x] Promote release-quality benchmark reports only when public performance
      claims require them.
- [x] Run benchmark/report documentation tests.
- [x] Run `git diff --check`.

Evidence:

- Confirmed smoke and current release commands build the `2.14.0` reactor with
  `-am`; the published-baseline command intentionally omits `-am`, resolves
  starter `2.13.0`, and cleans before compiling the baseline-compatible harness.
- Confirmed current reports remain under `benchmark-reports/release-jmh.*` and
  published reports under `benchmark-reports/published-starter-2.13.0/`, with
  comparison output kept target-only and distinct.
- Added fail-closed report classification for all benchmark naming buckets.
  Unknown prefixes, unknown client-side surfaces, and empty scenario suffixes
  now stop report generation instead of silently becoming no-network evidence.
- Added a reflection-based test proving every current `@Benchmark` method has
  an explicit classification; no V18 benchmark row was added.
- Confirmed threshold crossings remain manual review signals because
  `benchmark.compare.fail-on-review` defaults to `false`.
- Kept the latest promoted report on `2.12.0`; V18 makes no public performance
  claim, so no `2.14.0` report was promoted.
- `mvn -q -Pbenchmarks -pl reactive-http-client-benchmarks -am -DskipTests=false -Dtest=BenchmarkMarkdownReportTest,BenchmarkReportComparatorTest -Dsurefire.failIfNoSpecifiedTests=false test` passed.
- `mvn -q -pl reactive-http-client-starter -Dtest=DocumentationReleaseArtifactTest test` passed.
- `git diff --check` passed.

---

## Priority 9 — Dependency Baseline Review Preparation

### [x] 5.2 Prepare dependency baseline review without upgrading by default
- [x] Review Spring Boot 3.5.x patch movement and managed WebFlux/Reactor
      Netty/Micrometer/OpenTelemetry versions.
- [x] Review Resilience4j baseline compatibility and optional dependency
      behavior.
- [x] Confirm dependency docs still name Java 21 and Spring Boot 3.5.x support.
- [x] Confirm versionless module dependencies continue to inherit managed
      versions.
- [x] Confirm benchmark reports continue to record dependency-management source.
- [x] Document any proposed baseline upgrade separately from feature work.
- [x] Ensure no dependency drift bypasses generated release evidence.
- [x] Run focused dependency/release documentation tests.
- [x] Run `git diff --check`.

Evidence:

- Kept the project on Java 21, Spring Boot `3.5.0`, and Resilience4j `2.2.0`;
  this priority makes no dependency baseline change.
- Compared effective POMs for the pinned Boot `3.5.0` BOM and separately
  evaluated `3.5.16` candidate. The managed versions move from WebFlux
  `6.2.7` to `6.2.19`, Reactor Netty HTTP `1.2.6` to `1.2.18`, and Micrometer
  Core `1.15.0` to `1.15.12`; OpenTelemetry API remains `1.49.0`.
- Documented the `3.5.16` candidate as deferred in
  `docs/20-native-release-compatibility.md`, with a separate-upgrade policy and
  required release-smoke, AOT, optional-integration, documentation,
  compatibility, and benchmark metadata checks.
- Added generated release-evidence fields for the review date, candidate,
  decision, and exact candidate-managed versions so dependency review cannot
  drift independently of release evidence.
- Added module-POM guards covering versionless Spring Boot-managed dependencies
  in starter, test-helper, OTel, and benchmark modules. The guards also require
  starter Resilience4j, Micrometer, and Actuator integrations to remain
  optional and versionless.
- Confirmed benchmark environment reports continue to record
  `dependencyManagement=spring-boot-dependencies:<spring-boot.version>` and
  exact resolved WebFlux/Reactor Netty versions.
- `mvn -q -pl reactive-http-client-otel help:effective-pom -Doutput=/tmp/reactive-http-client-effective-3.5.0.xml` passed.
- `mvn -q -pl reactive-http-client-otel -Dspring-boot.version=3.5.16 help:effective-pom -Doutput=/tmp/reactive-http-client-effective-3.5.16.xml` passed.
- `mvn -q -Dspring-boot.version=3.5.16 -Prelease-smoke test` passed for the full reactor.
- `mvn -q -pl reactive-http-client-starter -Dtest=DocumentationReleaseArtifactTest test` passed.
- `mvn -q -Pbenchmarks -pl reactive-http-client-benchmarks -am -DskipTests=false -Dtest=BenchmarkMarkdownReportTest -Dsurefire.failIfNoSpecifiedTests=false test` passed.
- `git diff --check` passed.

---

## Priority 10 — V18 Release Readiness

### [x] 6.1 Keep V18 small enough to release confidently
- [x] Decide patch versus minor after V18 scope is finalized.
- [x] Keep changelog entries under `Unreleased` while V18 work is active.
- [x] Ensure release evidence names the selected next version.
- [x] Ensure release evidence names the selected API baseline.
- [x] Verify generated configuration docs are current.
- [x] Verify Markdown links pass across docs and roadmaps.
- [x] Verify baseline artifact resolution commands are listed.
- [x] Verify root and module-scoped API compatibility commands are listed.
- [x] Verify API compatibility fixture command is listed.
- [x] Verify benchmark smoke, release, and published-baseline commands are listed
      when performance evidence is needed.
- [x] Run focused release documentation tests.
- [x] Run full reactor tests.
- [x] Run API compatibility.
- [x] Run module-scoped starter API compatibility.
- [x] Run API compatibility fixture script.
- [x] Run `git diff --check`.
- [x] Mark `ROADMAP.md` completed only after implementation and evidence are
      complete.

Evidence:

- Selected `2.14.0` as the minor release candidate. The reactor, README,
  quick-start snippets, release guide, and generated release evidence already
  use `2.14.0`; changing back to a patch line would create version drift without
  a compatibility benefit.
- Kept all V18 changelog entries under `Unreleased`. Release dating, tagging,
  signing, and publication remain separate release-prep actions.
- Generated release evidence names project version `2.14.0` and published API
  compatibility baseline `2.13.0`.
- The release-prep checklist lists published-baseline artifact resolution for
  starter, test-helper, and OTel `2.13.0`, root and module-scoped compatibility
  commands, the compatibility fixture script, and benchmark compile, smoke,
  release, and published-baseline commands.
- Deferred a `2.14.0` promoted benchmark report. V18 does not change a measured
  request path, optional-feature overhead path, or publish a performance claim;
  the latest promoted report therefore remains `2.12.0` and manual benchmark
  commands remain visible in generated evidence.
- Marked `roadmaps/v18/ROADMAP.md` completed for the `2.14.0` candidate line only
  after implementation and release evidence passed.
- `mvn -q -pl reactive-http-client-starter -Dtest=DocumentationReleaseArtifactTest,ReactiveHttpClientConfigurationMetadataTest test` passed.
- `mvn -q test` passed for the full reactor.
- `mvn -q -Papi-compatibility -DskipTests verify` passed against published `2.13.0`.
- `mvn -q -pl reactive-http-client-starter -Papi-compatibility -DskipTests verify` passed against published `2.13.0`.
- `bash scripts/verify-api-compatibility-fixtures.sh` passed: additive API was
  accepted, while constructor, nested method, and enum constant removals were
  rejected.
- `git diff --check` passed.
