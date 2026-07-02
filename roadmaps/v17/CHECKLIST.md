# Reactive HTTP Client — Roadmap V17 Checklist

Companion to [`ROADMAP.md`](ROADMAP.md). Execute priorities in order unless a
release blocker requires reordering.

---

## Priority 1 — Release V16 as `2.12.0` When Evidence Is Ready

### [x] 0.1 Release V16 as `2.12.0` when evidence is ready
- [x] Confirm the V16 scope still requires a minor release rather than a patch.
- [x] Keep `api.compatibility.baseline.version` on `2.11.0` until `2.12.0` is
      published and resolvable.
- [x] Move `CHANGELOG.md` V16 entries from `Unreleased` to a dated `2.12.0`
      section only during release prep.
- [x] Verify README, quick start, Maven project versions, and release evidence
      agree on `2.12.0` before tagging.
- [x] Run root API compatibility against published `2.11.0`.
- [x] Run module-scoped starter API compatibility against published `2.11.0`.
- [x] Run API compatibility fixture script.
- [x] Run the full reactor test suite.
- [x] If release notes include performance claims, run a clean release benchmark.
- [x] If release notes include performance claims, promote
      `docs/benchmark-report-2.12.0.md` from clean release evidence.
- [x] If no performance claims are included, explicitly keep the missing
      `2.12.0` promoted report out of public performance claims.
- [x] Ensure no target-only release evidence is committed as source-controlled
      proof.
- [x] Run focused release documentation tests.
- [x] Run `git diff --check`.

Evidence:

- Confirmed V16 is a minor release candidate because it ships opt-in Actuator
  diagnostics, strict validation modes, generated examples, and expanded public
  compatibility coverage.
- Moved the completed V16 changelog content from `Unreleased` to
  `## [2.12.0] - 2026-07-02`. The `Unreleased` section is now empty for future
  V17 work.
- Verified the reactor version, README dependency snippets, quick-start
  dependency snippet, and release evidence name `2.12.0`;
  `api.compatibility.baseline.version` remains `2.11.0` until published
  `2.12.0` artifacts are resolvable.
- Release notes do not publish numeric or comparative `2.12.0` performance
  claims, so no clean `2.12.0` release benchmark was promoted for this priority.
  The generated readiness manifest still points to
  `docs/benchmark-report-2.12.0.md` with status `missing`, and current public
  performance docs remain tied to the latest promoted historical report.
- No target-only release evidence files were added to source control.
- `mvn -Papi-compatibility -DskipTests verify` passed against the published
  `2.11.0` baseline.
- `mvn -pl reactive-http-client-starter -Papi-compatibility -DskipTests verify`
  passed against the published `2.11.0` baseline.
- `bash scripts/verify-api-compatibility-fixtures.sh` passed: additive API
  accepted and constructor removal rejected.
- `mvn test` passed: 741 tests, 0 failures, 0 errors, 0 skipped.
- `mvn -q -pl reactive-http-client-starter -Dtest=DocumentationReleaseArtifactTest test`
  passed.
- `git diff --check` passed.

---

## Priority 2 — Post-`2.12.0` Baseline Transition

### [x] 1.1 Move the baseline only after `2.12.0` is published
- [x] Confirm published `2.12.0` artifacts resolve before changing the baseline.
- [x] Resolve `io.github.huynhngochuyhoang:reactive-http-client-starter:2.12.0`.
- [x] Resolve `io.github.huynhngochuyhoang:reactive-http-client-test:2.12.0`.
- [x] Resolve `io.github.huynhngochuyhoang:reactive-http-client-otel:2.12.0`.
- [x] Bump the next development reactor version so it does not equal the
      `2.12.0` baseline.
- [x] Move `api.compatibility.baseline.version` to `2.12.0` only after artifact
      resolution succeeds.
- [x] Update benchmark published-baseline commands to
      `-Dbenchmark.starter.version=2.12.0`.
- [x] Update benchmark published-baseline report paths to
      `published-starter-2.12.0`.
- [x] Update release compatibility docs with the transition commands.
- [x] Verify root API compatibility passes against published `2.12.0`.
- [x] Verify module-scoped starter API compatibility passes against published
      `2.12.0`.
- [x] Verify root self-comparison guard rejects the current reactor version.
- [x] Verify module-scoped self-comparison guard rejects the current reactor
      version.
- [x] Run focused release documentation tests.
- [x] Run `git diff --check`.

Evidence:

- `mvn dependency:get -Dartifact=io.github.huynhngochuyhoang:reactive-http-client-starter:2.12.0` passed.
- `mvn dependency:get -Dartifact=io.github.huynhngochuyhoang:reactive-http-client-test:2.12.0` passed.
- `mvn dependency:get -Dartifact=io.github.huynhngochuyhoang:reactive-http-client-otel:2.12.0` passed.
- Bumped the reactor and module parent versions to `2.13.0`, keeping the next development version distinct from the published `2.12.0` API baseline.
- Moved `api.compatibility.baseline.version` to `2.12.0` only after published artifact resolution succeeded.
- Updated README and quick-start dependency snippets to `2.13.0`.
- Updated release compatibility docs, benchmark published-baseline commands, and benchmark comparison paths to use `2.12.0` and `published-starter-2.12.0`.
- Kept public promoted benchmark-report links tied to the latest promoted historical `2.11.0` report; no `2.12.0` or `2.13.0` performance claim was introduced here.
- Added the `Unreleased` changelog entry for the post-`2.12.0` baseline transition and updated changelog comparison links.
- `mvn -Papi-compatibility -DskipTests verify` passed against the published `2.12.0` baseline.
- `mvn -pl reactive-http-client-starter -Papi-compatibility -DskipTests verify` passed against the published `2.12.0` baseline.
- `mvn -Papi-compatibility -DskipTests -Dapi.compatibility.baseline.version=2.13.0 validate` failed as expected with the self-comparison guard message.
- `mvn -pl reactive-http-client-starter -Papi-compatibility -DskipTests -Dapi.compatibility.baseline.version=2.13.0 validate` failed as expected with the self-comparison guard message.
- `mvn -q -pl reactive-http-client-starter -Dtest=DocumentationReleaseArtifactTest test` passed.
- `git diff --check` passed.

---

## Priority 3 — Diagnostics Endpoint Contract Stabilization

### [x] 2.1 Stabilize the opt-in diagnostics endpoint contract
- [x] Re-audit endpoint enablement property and default-disabled behavior.
- [x] Re-audit the Actuator endpoint id and exposure examples.
- [x] Verify the endpoint is absent when Actuator endpoint dependencies are not
      available.
- [x] Verify endpoint output is generated from
      `ReactiveHttpClientDiagnosticsSnapshot` or equivalent sanitized data.
- [x] Verify endpoint output omits concrete base URLs, secrets, sensitive header
      values, request bodies, response bodies, and auth provider bean names.
- [x] Add or extend tests for multiple configured clients.
- [x] Add or extend tests for inherited generic endpoint summaries.
- [x] Add or extend tests for strict validation mode summaries.
- [x] Add or extend tests for disabled and missing optional dependency paths.
- [x] Update docs that explain endpoint output versus health details, logs,
      observers, exchange logging, and helper snapshots.
- [x] Run diagnostics provider, auto-configuration, and endpoint tests.
- [x] Run documentation metadata/link tests.

Evidence:

- Endpoint enablement remains opt-in through `reactive.http.observability.diagnostics-endpoint.enabled=true`; the default auto-configuration path still registers only `ReactiveHttpClientDiagnosticsProvider` and no endpoint bean.
- Confirmed the Actuator endpoint id remains the alphanumeric `rhttpclients` id used by the docs and exposure examples.
- Existing missing-Actuator coverage still verifies the endpoint bean is skipped without failing startup when Actuator endpoint annotation classes are unavailable.
- `ReactiveHttpClientDiagnosticsEndpoint.diagnostics()` still delegates to `ReactiveHttpClientDiagnosticsSnapshot.toMap(provider)`; the new multi-client endpoint test asserts endpoint output equals the snapshot helper output.
- Provider-backed snapshot output now includes sanitized `strictUnsafeRetryValidation` and `strictBodySigningValidation` booleans without changing the public `ClientSummary` record constructor.
- Added endpoint coverage for multiple clients, inherited generic endpoints, strict unsafe-retry config, strict AWS SigV4 body-signing config, deterministic client ordering, and sanitized output that omits concrete base URLs, auth secrets, sensitive header material, request bodies, and response bodies.
- Updated observability, diagnostic-context, and support-bundle docs to describe endpoint output versus health details, exchange logs, and helper snapshots.
- `mvn -q -pl reactive-http-client-starter -Dtest=ReactiveHttpClientAutoConfigurationTest,ReactiveHttpClientDiagnosticsProviderTest test` passed.
- `mvn -q -pl reactive-http-client-starter -Dtest=DocumentationReleaseArtifactTest test` passed.
- `mvn -q -pl reactive-http-client-starter -Dtest=ReactiveHttpClientConfigurationMetadataTest test` passed.
- `git diff --check` passed.


---

## Priority 4 — Support-Bundle Regression Fixtures

### [ ] 2.2 Add production support-bundle regression fixtures
- [ ] Add approval-style or deterministic fixtures for sanitized diagnostics
      snapshot output.
- [ ] Add or validate support-bundle examples that combine diagnostics JSON,
      health details, startup summaries, exchange-log category settings, and
      benchmark report links.
- [ ] Ensure support-bundle examples use fake hostnames and placeholder values.
- [ ] Ensure examples never include tokens, secrets, request bodies, response
      bodies, concrete base URLs, or raw sensitive header values.
- [ ] Keep fixtures small enough for normal code review.
- [ ] Validate support-bundle configuration snippets against metadata.
- [ ] Validate support-bundle links through documentation tests.
- [ ] Run focused support-bundle documentation tests.
- [ ] Run `git diff --check`.

Evidence:

- Pending.

---

## Priority 5 — Strict Unsafe-Retry Adoption Audit

### [ ] 3.1 Audit strict unsafe-retry validation against real configuration shapes
- [ ] Re-test strict retry validation with inherited endpoints.
- [ ] Re-test strict retry validation with `@ApiRef` endpoint mappings.
- [ ] Re-test strict retry validation with method-level generated idempotency
      keys.
- [ ] Re-test strict retry validation with Reactor-context idempotency keys and
      document why they are dynamic rather than startup-provable.
- [ ] Re-test strict retry validation with configured default headers.
- [ ] Re-test strict retry validation with dynamic header maps and header params
      that can remove or override `Idempotency-Key`.
- [ ] Re-test strict retry validation with overloaded methods.
- [ ] Re-test strict retry validation with disabled retry instances and no-op
      retry operators.
- [ ] Improve startup error messages only where adoption evidence shows they are
      ambiguous.
- [ ] Keep warning-only behavior unchanged when strict mode is disabled.
- [ ] Add incremental rollout guidance for enabling strict mode one client at a
      time.
- [ ] Run retry, idempotency, diagnostics, and documentation tests.

Evidence:

- Pending.

---

## Priority 6 — Strict AWS SigV4 Body-Signing Audit

### [ ] 3.2 Audit strict body-signing validation for built-in AWS SigV4
- [ ] Re-test strict validation for byte-array bodies.
- [ ] Re-test strict validation for UTF-8 string bodies.
- [ ] Re-test strict validation for JSON DTO bodies.
- [ ] Re-test strict validation for absent body paths.
- [ ] Re-test strict validation for static non-JSON `Content-Type` values.
- [ ] Re-test strict validation for dynamic `Content-Type` header parameters.
- [ ] Re-test strict validation for erased `Object` bodies.
- [ ] Re-test strict validation for publisher bodies.
- [ ] Re-test strict validation for resource and multipart bodies.
- [ ] Re-test strict validation for Java stream body types.
- [ ] Verify custom `AuthProviderFactory` selection is not rejected by built-in
      SigV4 assumptions.
- [ ] Verify named custom auth providers are not rejected by built-in SigV4
      assumptions.
- [ ] Document JSON signing codec-alignment requirements.
- [ ] Run SigV4, auth provider, strict body-signing, and documentation tests.

Evidence:

- Pending.

---

## Priority 7 — Documented Public Surface Audit Guard

### [ ] 4.1 Add a documented-public-surface audit guard
- [ ] Define a source-controlled map of documented public helper types to japicmp
      include patterns.
- [ ] Cover annotation packages and declarative method annotations.
- [ ] Cover exception and auth extension points.
- [ ] Cover exchange logging and lifecycle hook public contracts.
- [ ] Cover diagnostics provider, diagnostics snapshot, and contract snapshot
      public contracts.
- [ ] Cover `SensitiveHeaders`, `MethodMetadataCache`, `MethodMetadata*`, and
      `ResilienceOperatorApplier*`.
- [ ] Cover test-helper package public contracts.
- [ ] Cover OTel companion public contracts.
- [ ] Add a release-doc test that fails when a documented helper is missing from
      the compatibility include map.
- [ ] Document the workflow for adding new public helpers with compatibility
      coverage in the same change.
- [ ] Keep implementation internals excluded unless they are documented as
      replacement or extension surfaces.
- [ ] Run API compatibility and release documentation tests.

Evidence:

- Pending.

---

## Priority 8 — Public Constructor and Mutable Model Review

### [ ] 4.2 Review public constructors and mutable models for long-term support
- [ ] Review public constructors and setters on `MethodMetadata*`.
- [ ] Review diagnostics provider and diagnostics snapshot public models.
- [ ] Review contract snapshot builder, client model, nested classes, and return
      models.
- [ ] Review test-helper public records and assertion helper methods.
- [ ] Review public enums and nested types exposed through compatibility-covered
      helpers.
- [ ] Document every compatibility-covered type as supported, deprecated, or
      reserved for a future major release.
- [ ] Avoid removals or narrowing changes in the current minor line.
- [ ] Add javadocs only where a public type is intended for user implementation
      or direct instantiation.
- [ ] Run API compatibility fixtures.
- [ ] Run root and module-scoped API compatibility.

Evidence:

- Pending.

---

## Priority 9 — V16-to-V17 Adoption Guide

### [ ] 5.1 Add a V16-to-V17 adoption guide
- [ ] Add a guide for adopting V16 features after upgrade.
- [ ] Put diagnostics snapshot and support-bundle capture before strict startup
      validation.
- [ ] Explain health details, startup summaries, and diagnostics endpoint scope.
- [ ] Explain strict retry rollout one client at a time.
- [ ] Explain strict body-signing rollout and when not to enable it.
- [ ] Explain dynamic idempotency-key limitations.
- [ ] Explain custom body-signing provider limitations.
- [ ] Link to quick start, auth, resilience, observability, support bundle,
      benchmarks, and native compatibility docs.
- [ ] Use metadata-backed configuration keys in examples.
- [ ] Run documentation link and metadata tests.

Evidence:

- Pending.

---

## Priority 10 — Production Policy Example Coverage

### [ ] 5.2 Improve example app coverage without creating a new framework
- [ ] Add one compact example page or package for common production policy.
- [ ] Include inherited clients with per-client timeout policy.
- [ ] Include OAuth2 client credentials with placeholder values.
- [ ] Include retry with a startup-provable idempotency-key contract.
- [ ] Include strict unsafe-retry validation.
- [ ] Include strict built-in SigV4 body-signing validation only where the body
      shape is supported.
- [ ] Include diagnostics endpoint and support-bundle capture snippets.
- [ ] Keep values fake and obviously non-production.
- [ ] Avoid adding a runnable sample service unless tests require it.
- [ ] Validate configuration snippets against metadata.
- [ ] Run documentation link tests.

Evidence:

- Pending.

---

## Priority 11 — `2.12.0` Benchmark Report Promotion or Deferral

### [ ] 6.1 Promote or explicitly defer the `2.12.0` benchmark report
- [ ] Decide whether `2.12.0` release notes include public performance claims.
- [ ] If performance claims are included, run the clean current-candidate release
      benchmark with `-Dbenchmark.commit=$(git rev-parse --short HEAD)`.
- [ ] If performance claims are included, run the published-baseline benchmark
      against `2.11.0`.
- [ ] If performance claims are included, promote
      `docs/benchmark-report-2.12.0.md` from clean release-quality evidence.
- [ ] If performance claims are included, ensure changelog wording cites the
      promoted `2.12.0` report.
- [ ] If performance claims are not included, document the report as intentionally
      deferred and keep public performance docs tied to the latest promoted
      historical report.
- [ ] Keep current and published-baseline report paths distinct.
- [ ] Ensure target-only reports are not cited as release evidence.
- [ ] Run benchmark report validation tests.
- [ ] Run focused release documentation tests.

Evidence:

- Pending.

---

## Priority 12 — Diagnostics and Strict-Mode Overhead Audit

### [ ] 6.2 Re-audit diagnostics and strict-mode overhead only if adoption needs it
- [ ] Confirm whether user feedback or release evidence requires a new overhead
      audit.
- [ ] Keep benchmark smoke and release commands available.
- [ ] Add or refresh no-network audit rows only when they isolate a real
      diagnostics or strict-mode question.
- [ ] Separate startup validation cost from request-path cost and endpoint
      rendering cost.
- [ ] Keep optional diagnostics rows out of raw-client comparison tables.
- [ ] Preserve benchmark prefix classification for any new rows.
- [ ] Avoid optimization without repeatable named-row evidence.
- [ ] Keep performance documentation scoped to measured scenarios.
- [ ] Run benchmark module tests when benchmark code changes.
- [ ] Run documentation tests when performance docs change.

Evidence:

- Pending.

---

## Priority 13 — Native Hint Re-Audit

### [ ] 7.1 Re-audit native hints for V16 public and configuration surfaces
- [ ] Verify runtime hints cover diagnostics endpoint configuration binding.
- [ ] Verify runtime hints cover diagnostics snapshot version metadata resources.
- [ ] Verify inherited client proxy hints still cover inherited endpoint methods.
- [ ] Verify public annotations and configuration property types needed at
      runtime are covered.
- [ ] Verify optional Actuator behavior remains conditional in JVM and native
      builds.
- [ ] Verify optional OTel behavior remains conditional in JVM and native builds.
- [ ] Add native smoke coverage only when a V16 surface requires new reflection
      or resource hints.
- [ ] Document native limitations explicitly.
- [ ] Run AOT smoke tests.
- [ ] Run focused release documentation tests.

Evidence:

- Pending.

---

## Priority 14 — Dependency Baseline Readiness

### [ ] 7.2 Prepare dependency-baseline review for the next Spring Boot line
- [ ] Audit the current Java support policy.
- [ ] Audit the current Spring Boot baseline.
- [ ] Audit Reactor Netty, Micrometer, Resilience4j, OpenTelemetry, and test
      dependency versions from dependency management.
- [ ] Document which dependency upgrades are compatibility-neutral.
- [ ] Document which dependency upgrades require a minor release.
- [ ] Keep benchmark dependency metadata recording resolved versions and source.
- [ ] Avoid upgrading baselines as part of unrelated feature work.
- [ ] Ensure generated metadata and release evidence tests catch dependency drift.
- [ ] Run benchmark metadata tests if dependency metadata changes.
- [ ] Run release documentation tests.

Evidence:

- Pending.

---

## Priority 15 — V17 Release Readiness

### [ ] 8.1 Keep V17 release evidence source-controlled and current
- [ ] Decide patch versus minor after V17 scope is finalized.
- [ ] Keep changelog entries under `Unreleased` while V17 work is active.
- [ ] Ensure release evidence commands name the selected next version.
- [ ] Ensure release evidence commands name the selected API baseline.
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
- [ ] Run `git diff --check`.
- [ ] Mark `ROADMAP.md` completed only after implementation and evidence are
      complete.

Evidence:

- Pending.
