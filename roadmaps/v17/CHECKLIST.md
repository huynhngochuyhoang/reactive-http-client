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

### [x] 5.1 Add a V16-to-V17 adoption guide
- [x] Add a guide for adopting V16 features after upgrade.
- [x] Put diagnostics snapshot and support-bundle capture before strict startup
      validation.
- [x] Explain health details, startup summaries, and diagnostics endpoint scope.
- [x] Explain strict retry rollout one client at a time.
- [x] Explain strict body-signing rollout and when not to enable it.
- [x] Explain dynamic idempotency-key limitations.
- [x] Explain custom body-signing provider limitations.
- [x] Link to quick start, auth, resilience, observability, support bundle,
      benchmarks, and native compatibility docs.
- [x] Use metadata-backed configuration keys in examples.
- [x] Run documentation link and metadata tests.

Evidence:

- Added `docs/27-v16-to-v17-adoption.md` with a diagnostics-first upgrade
  sequence: capture provider-backed diagnostics snapshots or the opt-in
  `rhttpclients` endpoint, collect a safe support bundle, then enable strict
  startup validation one client at a time.
- The guide explains health details, DEBUG startup summaries, diagnostics
  endpoint scope, strict unsafe-retry rollout, dynamic idempotency-key limits,
  strict built-in SigV4 body-signing rollout, and custom provider limitations.
- Linked the guide from the README guide table and back to quick start, auth,
  resilience, observability, support bundles, benchmarks, and native/release
  compatibility docs.
- Added release-doc coverage that asserts the adoption guide keeps the
  diagnostics-first strict-validation sequence and related links.
- `mvn -q -pl reactive-http-client-starter -Dtest=DocumentationReleaseArtifactTest test` passed.
- `mvn -q -pl reactive-http-client-starter -Dtest=ReactiveHttpClientConfigurationMetadataTest test` passed.

---

## Priority 10 — Production Policy Example Coverage

### [x] 5.2 Improve example app coverage without creating a new framework
- [x] Add one compact example page or package for common production policy.
- [x] Include inherited clients with per-client timeout policy.
- [x] Include OAuth2 client credentials with placeholder values.
- [x] Include retry with a startup-provable idempotency-key contract.
- [x] Include strict unsafe-retry validation.
- [x] Include strict built-in SigV4 body-signing validation only where the body
      shape is supported.
- [x] Include diagnostics endpoint and support-bundle capture snippets.
- [x] Keep values fake and obviously non-production.
- [x] Avoid adding a runnable sample service unless tests require it.
- [x] Validate configuration snippets against metadata.
- [x] Run documentation link tests.

Evidence:

- Added `docs/examples/production-policy.md` as a compact documentation-only
  policy example rather than a runnable sample service.
- The example covers inherited read clients with separate `@ApiRef` mappings and
  per-client timeout policy, an OAuth2 payment command client, method-level
  generated `@IdempotencyKey` for startup-provable POST retry safety, strict
  unsafe-retry validation, and built-in AWS SigV4 strict body signing for a
  concrete JSON DTO body with a static JSON `Content-Type`.
- Added diagnostics endpoint, health details, startup-summary logging,
  metadata-only exchange logging, and support-bundle capture snippets using
  `.example.invalid` hosts and `${EXAMPLE_*}` placeholders.
- Linked the page from `docs/examples/README.md`.
- Added `ReactiveHttpClientConfigurationMetadataTest` coverage that validates
  the production-policy YAML against starter metadata and checks safe
  placeholder conventions.
- `mvn -q -pl reactive-http-client-starter -Dtest=ReactiveHttpClientConfigurationMetadataTest test` passed.
- `mvn -q -pl reactive-http-client-starter -Dtest=DocumentationReleaseArtifactTest test` passed.

---

## Priority 11 — `2.12.0` Benchmark Report Promotion or Deferral

### [x] 6.1 Promote or explicitly defer the `2.12.0` benchmark report
- [x] Decide whether `2.12.0` release notes include public performance claims.
- [x] If performance claims are included, run the clean current-candidate release
      benchmark with `-Dbenchmark.commit=$(git rev-parse --short HEAD)`.
- [x] If performance claims are included, run the published-baseline benchmark
      against `2.11.0`.
- [x] If performance claims are included, promote
      `docs/benchmark-report-2.12.0.md` from clean release-quality evidence.
- [x] If performance claims are included, ensure changelog wording cites the
      promoted `2.12.0` report.
- [x] If performance claims are not included, document the report as intentionally
      deferred and keep public performance docs tied to the latest promoted
      historical report.
- [x] Keep current and published-baseline report paths distinct.
- [x] Ensure target-only reports are not cited as release evidence.
- [x] Run benchmark report validation tests.
- [x] Run focused release documentation tests.

Evidence:

- Corrected the release interpretation: although the reactor now declares
  `2.13.0` after the post-`2.12.0` baseline transition, this priority promotes
  the already-run benchmark as `2.12.0` release evidence.
- Promoted `docs/benchmark-report-2.12.0.md` from
  `reactive-http-client-benchmarks/target/benchmark-reports/release-jmh.md`,
  adding source-controlled promotion metadata, sanitized target paths, `2.12.0`
  report/starter metadata, and `2.11.0` published-baseline pairing.
- Updated README, changelog, benchmark docs, performance summary, benchmark
  consumer examples, and support-bundle docs to cite `Benchmark Report 2.12.0` for public performance
  evidence while preserving the historical `2.11.0` changelog entry.
- Kept generated target reports out of public links; public docs cite only the
  promoted source-controlled report.
- `mvn -q -pl reactive-http-client-starter -Dtest=DocumentationReleaseArtifactTest test` passed.
- `git diff --check` passed.

---

## Priority 12 — Diagnostics and Strict-Mode Overhead Audit

### [x] 6.2 Re-audit diagnostics and strict-mode overhead only if adoption needs it
- [x] Confirm whether user feedback or release evidence requires a new overhead
      audit.
- [x] Keep benchmark smoke and release commands available.
- [x] Add or refresh no-network audit rows only when they isolate a real
      diagnostics or strict-mode question.
- [x] Separate startup validation cost from request-path cost and endpoint
      rendering cost.
- [x] Keep optional diagnostics rows out of raw-client comparison tables.
- [x] Preserve benchmark prefix classification for any new rows.
- [x] Avoid optimization without repeatable named-row evidence.
- [x] Keep performance documentation scoped to measured scenarios.
- [x] Run benchmark module tests when benchmark code changes.
- [x] Run documentation tests when performance docs change.

Evidence:

- Re-audited the promoted `docs/benchmark-report-2.12.0.md` evidence and found
  existing named no-network rows for disabled diagnostics, metadata-only
  exchange logging, Micrometer observation, one/multiple observers,
  one/multiple lifecycle hooks, and `runtimeDiagnosticsProviderClientSummaries`.
- Did not add new benchmark methods because no new adoption feedback or release
  evidence identified a diagnostics or strict-mode overhead question that was
  not already isolated by those named rows.
- Documented the audit boundary in `docs/22-benchmarks.md`: request-path
  diagnostics, on-demand diagnostics-provider inspection, support-path Actuator
  endpoint rendering, and startup/proxy-construction strict validation must be
  measured separately.
- Updated `docs/23-performance-summary.md` with a V17 diagnostics and
  strict-mode audit section, keeping optional diagnostics rows out of raw-client
  comparison claims and avoiding optimization without a dedicated named row.
- No benchmark code changed, so benchmark module tests were not required for
  this priority; existing benchmark report classification keeps no-network rows
  out of optional feature summary tables.
- `mvn -q -pl reactive-http-client-starter -Dtest=DocumentationReleaseArtifactTest test` passed.
- `git diff --check` passed.

---

## Priority 13 — Native Hint Re-Audit

### [x] 7.1 Re-audit native hints for V16 public and configuration surfaces
- [x] Verify runtime hints cover diagnostics endpoint configuration binding.
- [x] Verify runtime hints cover diagnostics snapshot version metadata resources.
- [x] Verify inherited client proxy hints still cover inherited endpoint methods.
- [x] Verify public annotations and configuration property types needed at
      runtime are covered.
- [x] Verify optional Actuator behavior remains conditional in JVM and native
      builds.
- [x] Verify optional OTel behavior remains conditional in JVM and native builds.
- [x] Add native smoke coverage only when a V16 surface requires new reflection
      or resource hints.
- [x] Document native limitations explicitly.
- [x] Run AOT smoke tests.
- [x] Run focused release documentation tests.

Evidence:

- Re-audited `ReactiveHttpClientRuntimeHints` against the public nested
  `ReactiveHttpClientProperties` configuration model. `DiagnosticsEndpointConfig`
  and the diagnostics snapshot `pom.properties` resource were already covered;
  the audit found one missing configuration-property enum hint,
  `ReactiveHttpClientProperties.LogPreset`, and added it.
- Strengthened `ReactiveHttpClientAotSmokeTest` so every public nested
  `ReactiveHttpClientProperties` type must have an explicit reflection hint,
  preventing future native configuration-property drift. Existing AOT coverage
  still verifies inherited client proxy/public-method hints and the diagnostics
  snapshot version metadata resource.
- Verified optional Actuator behavior remains conditional through existing
  `ReactiveHttpClientAutoConfigurationTest` coverage: the diagnostics provider is
  registered without the endpoint, the `rhttpclients` endpoint is explicit
  opt-in, and the endpoint is skipped when Actuator endpoint classes are absent.
- Verified optional OTel behavior remains conditional through existing
  `OpenTelemetryHttpClientAutoConfigurationTest` coverage: the master switch
  disables all OTel beans, and span/propagation switches remain independently
  conditional.
- Updated `docs/20-native-release-compatibility.md` to document native support
  for diagnostics snapshot version metadata and the optional native-image limits
  for the `rhttpclients` Actuator endpoint. Added release-doc assertions for the
  new native wording.
- Focused verification passed:
  `mvn -q -pl reactive-http-client-starter -Dtest=ReactiveHttpClientAotSmokeTest,ReactiveHttpClientAutoConfigurationTest,DocumentationReleaseArtifactTest test`
  and `mvn -q -pl reactive-http-client-otel -am -Dtest=OpenTelemetryHttpClientAutoConfigurationTest -Dsurefire.failIfNoSpecifiedTests=false test`. The OTel run uses `-am` because the reactor is on unreleased `2.13.0`, so resolving the starter from a remote repository is not valid release evidence before publication.

---

## Priority 14 — Dependency Baseline Readiness

### [x] 7.2 Prepare dependency-baseline review for the next Spring Boot line
- [x] Audit the current Java support policy.
- [x] Audit the current Spring Boot baseline.
- [x] Audit Reactor Netty, Micrometer, Resilience4j, OpenTelemetry, and test
      dependency versions from dependency management.
- [x] Document which dependency upgrades are compatibility-neutral.
- [x] Document which dependency upgrades require a minor release.
- [x] Keep benchmark dependency metadata recording resolved versions and source.
- [x] Avoid upgrading baselines as part of unrelated feature work.
- [x] Ensure generated metadata and release evidence tests catch dependency drift.
- [x] Run benchmark metadata tests if dependency metadata changes.
- [x] Run release documentation tests.

Evidence:

- Audited the current dependency-baseline inputs from the root POM: Java `21`,
  Spring Boot `3.5.0`, Resilience4j `2.2.0`, JMH `1.37`, and the Spring Boot
  dependency-management source for Spring WebFlux, Reactor Netty, Micrometer,
  OpenTelemetry, and most test dependencies. No dependency version was changed
  for this priority.
- Added `docs/20-native-release-compatibility.md` dependency-baseline readiness
  guidance that classifies compatibility-neutral maintenance versus changes that
  require a minor release, and records that baseline upgrades must not be mixed
  with unrelated feature work.
- Extended `DocumentationReleaseArtifactTest` release evidence generation with a
  `dependencyBaselineReview` block covering Java, Spring Boot, WebFlux, Reactor
  Netty, Micrometer, OpenTelemetry, Resilience4j, test-dependency source, JMH,
  compatibility-neutral updates, minor-release updates, and the baseline-upgrade
  policy. The test now keeps those docs and generated evidence aligned with POM
  properties.
- Strengthened benchmark dependency-management assertions so the benchmark module
  remains versionless for Spring WebFlux, Reactor Netty, Micrometer, and
  Resilience4j dependencies while recording managed version sources in release
  evidence.
- Focused verification passed:
  `mvn -q -pl reactive-http-client-starter -Dtest=DocumentationReleaseArtifactTest test`,
  `mvn -q -pl reactive-http-client-starter -Dtest=ReactiveHttpClientConfigurationMetadataTest#documentedReactiveHttpPropertiesExistInGeneratedMetadata test`,
  and `mvn -q -Pbenchmarks -pl reactive-http-client-benchmarks -am -Dtest=BenchmarkMarkdownReportTest,BenchmarkReportComparatorTest -Dsurefire.failIfNoSpecifiedTests=false test`.

---

## Priority 15 — V17 Release Readiness

### [x] 8.1 Keep V17 release evidence source-controlled and current
- [x] Decide patch versus minor after V17 scope is finalized.
- [x] Keep changelog entries under `Unreleased` while V17 work is active.
- [x] Ensure release evidence commands name the selected next version.
- [x] Ensure release evidence commands name the selected API baseline.
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
- [x] Run `git diff --check`.
- [x] Mark `ROADMAP.md` completed only after implementation and evidence are
      complete.

Evidence:

- Selected the next release line as `2.13.0` with API compatibility and
  published-baseline benchmark evidence against `2.12.0`. `CHANGELOG.md` now
  has `## [2.13.0] - 2026-07-05` and `Unreleased` points at
  `v2.13.0...HEAD`; release tagging remains a separate release-prep action.
- Verified generated release evidence names project version `2.13.0`, API
  baseline `2.12.0`, published baseline artifact-resolution commands for
  starter/test/OTel `2.12.0`, root and module-scoped API compatibility commands,
  the compatibility fixture script, and benchmark smoke/release/published-baseline
  commands. No new performance claim was introduced in this priority, so no new
  benchmark run was required here.
- Marked `roadmaps/v17/ROADMAP.md` completed on 2026-07-04 and checked roadmap
  acceptance boxes only after full tests and compatibility evidence passed.
- Verification passed:
  `mvn -q -pl reactive-http-client-starter -Dtest=DocumentationReleaseArtifactTest test`,
  `mvn test`,
  `mvn -q -Papi-compatibility -DskipTests verify`,
  `mvn -q -pl reactive-http-client-starter -Papi-compatibility -DskipTests verify`,
  `bash scripts/verify-api-compatibility-fixtures.sh`,
  and `git diff --check`.
