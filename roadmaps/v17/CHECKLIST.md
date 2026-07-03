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
- Public `clientSummaries()` remains summary-only and does not resolve strict auth providers before discarding snapshot-only flags.
- Provider-backed snapshots do not construct auth providers while reporting strict body-signing diagnostics.
- Strict retry diagnostics now report true only when resilience is enabled, the strict flag is active, the Retry operator is available, and at least one resolved retry instance can make duplicate attempts; method-level missing `@Retry` names are not materialized during diagnostics.
- Strict body-signing diagnostics now report true only when object-style AWS SigV4 selects the starter built-in factory; named auth-provider beans and custom factories report false without provider construction.
- Summary-only snapshot overloads now render provider-only strict validation values as unknown/null instead of silently reporting false, and provider snapshot overloads respect custom `clientSummaries()` overrides.
- Provider snapshot overloads now classify class-based Spring/CGLIB proxies by the user class so the default provider keeps strict validation booleans instead of falling back to summary-only unknown values.
- Added endpoint coverage for multiple clients, inherited generic endpoints, strict unsafe-retry config, strict AWS SigV4 body-signing config, deterministic client ordering, and sanitized output that omits concrete base URLs, auth secrets, sensitive header material, request bodies, and response bodies.
- Updated observability, diagnostic-context, and support-bundle docs to describe endpoint output versus health details, exchange logs, and helper snapshots.
- `mvn -q -pl reactive-http-client-starter -Dtest=ReactiveHttpClientAutoConfigurationTest,ReactiveHttpClientDiagnosticsProviderTest test` passed.
- `mvn -q -pl reactive-http-client-starter -Dtest=DocumentationReleaseArtifactTest test` passed.
- `mvn -q -pl reactive-http-client-starter -Dtest=ReactiveHttpClientConfigurationMetadataTest test` passed.
- `git diff --check` passed.


---

## Priority 4 — Support-Bundle Regression Fixtures

### [x] 2.2 Add production support-bundle regression fixtures
- [x] Add approval-style or deterministic fixtures for sanitized diagnostics
      snapshot output.
- [x] Add or validate support-bundle examples that combine diagnostics JSON,
      health details, startup summaries, exchange-log category settings, and
      benchmark report links.
- [x] Ensure support-bundle examples use fake hostnames and placeholder values.
- [x] Ensure examples never include tokens, secrets, request bodies, response
      bodies, concrete base URLs, or raw sensitive header values.
- [x] Keep fixtures small enough for normal code review.
- [x] Validate support-bundle configuration snippets against metadata.
- [x] Validate support-bundle links through documentation tests.
- [x] Run focused support-bundle documentation tests.
- [x] Run `git diff --check`.

Evidence:

- Added a deterministic diagnostics JSON fixture in `ReactiveHttpClientDiagnosticsProviderTest` that renders `ReactiveHttpClientDiagnosticsSnapshot.toJson(provider)`, normalizes only `projectVersion`, and verifies client count, inherited endpoint count, timeout source, auth mode, redirect policy, strict validation flags, and sanitized omission of base URL, auth-provider bean name, Authorization, Cookie, and raw header values.
- Added a reviewable support-bundle fixture section to `docs/26-support-bundles.md` that combines diagnostics JSON, health JSON, startup summary logs, metadata-only exchange logs, sanitized client config, and a promoted benchmark report link placeholder.
- Added support-bundle-specific metadata and safety coverage in `ReactiveHttpClientConfigurationMetadataTest`; it validates the support-bundle YAML against generated starter metadata and asserts the fixture uses `.example.invalid` plus placeholder paths without raw Authorization, bearer, cookie, body, localhost, or common public-domain examples.
- Existing Markdown link validation in `DocumentationReleaseArtifactTest` covers the support-bundle related-doc links and benchmark report link.
- `mvn -q -pl reactive-http-client-starter -Dtest=ReactiveHttpClientDiagnosticsProviderTest,ReactiveHttpClientConfigurationMetadataTest test` passed.
- `mvn -q -pl reactive-http-client-starter -Dtest=DocumentationReleaseArtifactTest test` passed.
- `git diff --check` passed.

---

## Priority 5 — Strict Unsafe-Retry Adoption Audit

### [x] 3.1 Audit strict unsafe-retry validation against real configuration shapes
- [x] Re-test strict retry validation with inherited endpoints.
- [x] Re-test strict retry validation with `@ApiRef` endpoint mappings.
- [x] Re-test strict retry validation with method-level generated idempotency
      keys.
- [x] Re-test strict retry validation with Reactor-context idempotency keys and
      document why they are dynamic rather than startup-provable.
- [x] Re-test strict retry validation with configured default headers.
- [x] Re-test strict retry validation with dynamic header maps and header params
      that can remove or override `Idempotency-Key`.
- [x] Re-test strict retry validation with overloaded methods.
- [x] Re-test strict retry validation with disabled retry instances and no-op
      retry operators.
- [x] Improve startup error messages only where adoption evidence shows they are
      ambiguous.
- [x] Keep warning-only behavior unchanged when strict mode is disabled.
- [x] Add incremental rollout guidance for enabling strict mode one client at a
      time.
- [x] Run retry, idempotency, diagnostics, and documentation tests.

Evidence:

- Existing strict retry tests continue to cover inherited overloaded endpoints, method-level generated `@IdempotencyKey`, idempotent HTTP methods, configured default `Idempotency-Key` headers, dynamic `@HeaderParam` and header-map overrides, unavailable Retry operators, and single-attempt Retry instances.
- Added startup coverage for warning-only compatibility when resilience retry is enabled for POST but `strict-unsafe-retry-validation` remains disabled.
- Added `@ApiRef` strict retry coverage: a configured POST API ref without startup-provable idempotency fails startup, while the same API ref with method-level generated `@IdempotencyKey` succeeds.
- Re-audited Reactor-context idempotency as intentionally dynamic; docs now keep it outside startup-provable contracts and add an incremental one-client-at-a-time strict-mode rollout pattern.
- Existing startup error text already names the client, method signature, HTTP method, retry instance, retry methods, and explains that runtime-provided keys from parameters, header maps, or Reactor context are not startup-provable, so no message change was needed.
- `mvn -q -pl reactive-http-client-starter -Dtest=ReactiveHttpClientFactoryBeanDiagnosticsTest,ReactiveClientInvocationHandlerRetrySafetyTest,IdempotencyKeySupportTest test` passed.
- `mvn -q -pl reactive-http-client-starter -Dtest=ReactiveHttpClientDiagnosticsProviderTest,ReactiveHttpClientConfigurationMetadataTest,DocumentationReleaseArtifactTest test` passed.
- `git diff --check` passed.

---

## Priority 6 — Strict AWS SigV4 Body-Signing Audit

### [x] 3.2 Audit strict body-signing validation for built-in AWS SigV4
- [x] Re-test strict validation for byte-array bodies.
- [x] Re-test strict validation for UTF-8 string bodies.
- [x] Re-test strict validation for JSON DTO bodies.
- [x] Re-test strict validation for absent body paths.
- [x] Re-test strict validation for static non-JSON `Content-Type` values.
- [x] Re-test strict validation for dynamic `Content-Type` header parameters.
- [x] Re-test strict validation for erased `Object` bodies.
- [x] Re-test strict validation for publisher bodies.
- [x] Re-test strict validation for resource and multipart bodies.
- [x] Re-test strict validation for Java stream body types.
- [x] Verify custom `AuthProviderFactory` selection is not rejected by built-in
      SigV4 assumptions.
- [x] Verify named custom auth providers are not rejected by built-in SigV4
      assumptions.
- [x] Document JSON signing codec-alignment requirements.
- [x] Run SigV4, auth provider, strict body-signing, and documentation tests.

Evidence:

- Added strict SigV4 audit coverage for concrete DTO JSON bodies and direct
  `Resource` body rejection in `ReactiveHttpClientFactoryBeanDiagnosticsTest`.
- Existing strict validation coverage re-confirmed `byte[]`, default UTF-8
  `String`, absent bodies, static and dynamic `Content-Type` rejection, erased
  `Object`, `Publisher`, `DataBuffer`, multipart, Java stream bodies, named
  auth providers, and custom `AuthProviderFactory` selection.
- Documented the JSON DTO/object codec-alignment requirement in
  `docs/06-auth-providers.md`.
- `mvn -q -pl reactive-http-client-starter -Dtest=AwsSigV4AuthProviderTest,AuthProviderFactoryTest,RefreshingBearerAuthProviderTest,ReactiveHttpClientFactoryBeanDiagnosticsTest test` passed.
- `mvn -q -pl reactive-http-client-starter -Dtest=ReactiveHttpClientConfigurationMetadataTest,DocumentationReleaseArtifactTest test` passed.

---

## Priority 7 — Documented Public Surface Audit Guard

### [x] 4.1 Add a documented-public-surface audit guard
- [x] Define a source-controlled map of documented public helper types to japicmp
      include patterns.
- [x] Cover annotation packages and declarative method annotations.
- [x] Cover exception and auth extension points.
- [x] Cover exchange logging and lifecycle hook public contracts.
- [x] Cover diagnostics provider, diagnostics snapshot, and contract snapshot
      public contracts.
- [x] Cover `SensitiveHeaders`, `MethodMetadataCache`, `MethodMetadata*`, and
      `ResilienceOperatorApplier*`.
- [x] Cover test-helper package public contracts.
- [x] Cover OTel companion public contracts.
- [x] Add a release-doc test that fails when a documented helper is missing from
      the compatibility include map.
- [x] Document the workflow for adding new public helpers with compatibility
      coverage in the same change.
- [x] Keep implementation internals excluded unless they are documented as
      replacement or extension surfaces.
- [x] Run API compatibility and release documentation tests.

Evidence:

- Added a documented public surface map to
  `docs/20-native-release-compatibility.md`, with one row per japicmp include
  pattern for annotation, auth, exception, exchange logging, lifecycle,
  diagnostics, contract snapshot, metadata cache, redaction, test-helper, and
  OTel public surfaces.
- Added a compatibility include workflow requiring future documented public
  helpers to update the map and POM include set together, while keeping
  implementation internals excluded unless documented as extension surfaces.
- Added `DocumentationReleaseArtifactTest.documentedPublicSurfaceMapMatchesApiCompatibilityIncludes`, which fails when the documented map and POM
  `api-compatibility` include set diverge.
- `mvn -q -pl reactive-http-client-starter -Dtest=DocumentationReleaseArtifactTest test` passed.
- `mvn -q -Papi-compatibility -DskipTests verify` passed.
- `mvn -q -pl reactive-http-client-starter -Papi-compatibility -DskipTests verify` passed.
- `bash scripts/verify-api-compatibility-fixtures.sh` passed.

---

## Priority 8 — Public Constructor and Mutable Model Review

### [x] 4.2 Review public constructors and mutable models for long-term support
- [x] Review public constructors and setters on `MethodMetadata*`.
- [x] Review diagnostics provider and diagnostics snapshot public models.
- [x] Review contract snapshot builder, client model, nested classes, and return
      models.
- [x] Review test-helper public records and assertion helper methods.
- [x] Review public enums and nested types exposed through compatibility-covered
      helpers.
- [x] Document every compatibility-covered type as supported, deprecated, or
      reserved for a future major release.
- [x] Avoid removals or narrowing changes in the current minor line.
- [x] Add javadocs only where a public type is intended for user implementation
      or direct instantiation.
- [x] Run API compatibility fixtures.
- [x] Run root and module-scoped API compatibility.

Evidence:

- Added support-status coverage to the documented public surface map in
  `docs/20-native-release-compatibility.md`; every compatibility-covered
  include pattern is currently marked `Supported`, and the doc now states that
  future reserved or deprecated surfaces must be marked in the same table.
- Added a constructor and mutable model policy covering `MethodMetadata`,
  `MethodMetadataCache`, diagnostics summary records, diagnostics snapshot
  overloads, contract snapshot `Client` and `Builder`, test-helper APIs,
  `ResilienceOperatorApplier.InstanceType`, and OTel public constructors/static
  factories.
- Added focused Javadocs for user-facing mutable/constructible surfaces:
  `MethodMetadata`, `MethodMetadataCache`, diagnostics summary records,
  contract snapshot `Client`/`Builder`, and `ResilienceOperatorApplier`.
- Extended `DocumentationReleaseArtifactTest.documentedPublicSurfaceMapMatchesApiCompatibilityIncludes` to require support statuses and the constructor/mutability policy.
- `mvn -q -pl reactive-http-client-starter -Dtest=DocumentationReleaseArtifactTest test` passed.
- `bash scripts/verify-api-compatibility-fixtures.sh` passed.
- `mvn -q -Papi-compatibility -DskipTests verify` passed.
- `mvn -q -pl reactive-http-client-starter -Papi-compatibility -DskipTests verify` passed.

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
