# Reactive HTTP Client — Roadmap V16 Checklist

Companion to [`ROADMAP.md`](ROADMAP.md). Execute priorities in order unless a
release blocker requires reordering.

---

## Priority 1 — Post-`2.11.0` Baseline Transition

### [x] 1.1 Move the compatibility baseline after `2.11.0` is published
- [x] Confirm `2.11.0` is published and resolvable before changing the baseline.
- [x] Resolve `io.github.huynhngochuyhoang:reactive-http-client-starter:2.11.0`.
- [x] Resolve `io.github.huynhngochuyhoang:reactive-http-client-test:2.11.0`.
- [x] Resolve `io.github.huynhngochuyhoang:reactive-http-client-otel:2.11.0`.
- [x] Bump the reactor to `2.12.0` so the `2.11.0` baseline is not a self-comparison.
- [x] Move `api.compatibility.baseline.version` to `2.11.0`.
- [x] Update benchmark published-baseline commands to
      `-Dbenchmark.starter.version=2.11.0`.
- [x] Update benchmark published-baseline report paths to
      `published-starter-2.11.0`.
- [x] Update release compatibility docs with the transition commands.
- [x] Verify root API compatibility passes against published `2.11.0`.
- [x] Verify module-scoped starter API compatibility passes against published
      `2.11.0`.
- [x] Verify root API compatibility rejects self-comparison with the current
      reactor version.
- [x] Verify module-scoped API compatibility rejects self-comparison with the
      current reactor version.
- [x] Run focused release documentation tests.
- [x] Run `git diff --check`.

Evidence:

- Published `2.11.0` artifacts resolved with `mvn dependency:get -Dartifact=io.github.huynhngochuyhoang:reactive-http-client-starter:2.11.0`, `mvn dependency:get -Dartifact=io.github.huynhngochuyhoang:reactive-http-client-test:2.11.0`, and `mvn dependency:get -Dartifact=io.github.huynhngochuyhoang:reactive-http-client-otel:2.11.0`.
- Root and module parent versions now target `2.12.0`; `api.compatibility.baseline.version` now points at published `2.11.0`.
- Benchmark published-baseline commands and paths now use `-Dbenchmark.starter.version=2.11.0` and `published-starter-2.11.0`.
- Release docs now include the V16 post-release baseline transition and keep the latest promoted benchmark report on `2.11.0` until a future `2.12.0` release-quality report exists.
- `mvn -pl reactive-http-client-starter -Dtest=DocumentationReleaseArtifactTest test` passed.
- `mvn -Papi-compatibility -DskipTests verify` passed against the published `2.11.0` baseline.
- `mvn -pl reactive-http-client-starter -Papi-compatibility -DskipTests verify` passed against the published `2.11.0` baseline.
- `mvn -Papi-compatibility -DskipTests -Dapi.compatibility.baseline.version=2.12.0 validate` failed as expected with the self-comparison guard.
- `mvn -pl reactive-http-client-starter -Papi-compatibility -DskipTests -Dapi.compatibility.baseline.version=2.12.0 validate` failed as expected with the self-comparison guard.
- `git diff --check` passed.

---

## Priority 2 — Clean Benchmark Provenance Guard

### [x] 1.2 Preserve clean benchmark provenance for promoted reports
- [x] Add release-doc validation that rejects current promoted reports with
      `benchmarkCommit` missing.
- [x] Add release-doc validation that rejects current promoted reports with
      `benchmarkCommit=unknown`.
- [x] Add release-doc validation that rejects current promoted reports whose
      benchmark commit contains `dirty`.
- [x] Validate that the current promoted report commit looks like a short Git
      SHA.
- [x] Validate current promoted reports do not contain machine-local absolute
      paths.
- [x] Keep historical promoted reports valid unless they are current-release
      evidence.
- [x] Document the clean-commit release benchmark sequence in
      `docs/22-benchmarks.md`.
- [x] Document when generated target-only reports may still contain local paths.
- [x] Add or extend tests covering dirty, unknown, missing, and clean commit
      cases.
- [x] Run `DocumentationReleaseArtifactTest`.

Evidence:

- `docs/22-benchmarks.md` now documents the clean committed-tree benchmark sequence: check `git status --short`, capture `git rev-parse --short HEAD`, and pass that value through `-Dbenchmark.commit=$(git rev-parse --short HEAD)`.
- The benchmark guide now states that promoted source-controlled reports must not use missing, `unknown`, or `dirty` benchmark commits and must not contain machine-local absolute paths. Generated target-only reports may retain local paths while they stay under `target/`.
- `DocumentationReleaseArtifactTest` now validates the currently promoted benchmark report provenance and includes synthetic coverage for missing, `unknown`, `dirty`, malformed non-SHA, clean, and local-path report snippets, including Linux home/workspace/tmp and Windows drive paths. Historical promoted reports remain covered by metadata validation without being treated as current release evidence.
- `mvn -pl reactive-http-client-starter -Dtest=DocumentationReleaseArtifactTest test` passed.

---

## Priority 3 — Opt-In Actuator Diagnostics Endpoint

### [x] 2.1 Add an opt-in Actuator diagnostics endpoint
- [x] Choose the smallest endpoint surface and property name.
- [x] Keep the endpoint disabled by default.
- [x] Register the endpoint only when Actuator endpoint dependencies are present.
- [x] Reuse `ReactiveHttpClientDiagnosticsProvider` for source data.
- [x] Reuse `ReactiveHttpClientDiagnosticsSnapshot` sanitization rules.
- [x] Prefer JSON output first; leave Markdown as helper-only unless needed.
- [x] Verify endpoint output includes project version, client count, endpoint
      count, inherited endpoint count, auth mode, redirect flag, timeout source,
      and resilience summary.
- [x] Verify endpoint output omits concrete base URLs.
- [x] Verify endpoint output omits auth secrets, header values, proxy
      credentials, auth provider bean names, request bodies, and response
      bodies.
- [x] Verify missing Actuator dependencies keep existing starter behavior.
- [x] Add configuration metadata for the endpoint property.
- [x] Document endpoint usage and how it differs from health details, exchange
      logs, startup summaries, and helper snapshots.
- [x] Run diagnostics provider and Actuator endpoint tests.
- [x] Run documentation metadata/link tests.

Evidence:

- Added `ReactiveHttpClientDiagnosticsEndpoint`, an opt-in `rhttpclients` Actuator endpoint enabled by `reactive.http.observability.diagnostics-endpoint.enabled=false` by default.
- The endpoint returns JSON-compatible output from `ReactiveHttpClientDiagnosticsSnapshot.toMap(...)`, backed by `ReactiveHttpClientDiagnosticsProvider` summaries.
- Auto-configuration is gated on Actuator endpoint classes and the opt-in property; tests cover default-disabled behavior, enabled output, and missing Actuator endpoint classes.
- Endpoint tests verify project version, client count, endpoint count, inherited endpoint count, auth mode, redirect flag, timeout source, and resilience fields while omitting concrete base URLs, auth secrets, header values, auth-provider bean names, request bodies, and response bodies.
- Added configuration metadata and regenerated `docs/configuration-properties.md` for `reactive.http.observability.diagnostics-endpoint.enabled`.
- Documented endpoint usage and scope in `docs/21-diagnostic-contexts.md` and `docs/08-observability.md`.
- `mvn -pl reactive-http-client-starter -Dtest=ReactiveHttpClientAutoConfigurationTest,ReactiveHttpClientDiagnosticsProviderTest,ReactiveHttpClientConfigurationMetadataTest test` passed.
- `mvn -pl reactive-http-client-starter -Dtest=DocumentationReleaseArtifactTest test` passed.

---

## Priority 4 — Production Support Bundle Examples

### [x] 2.2 Add production support bundle examples
- [x] Add a support-bundle documentation page.
- [x] Document a minimal safe support bundle for configuration issues.
- [x] Document a minimal safe support bundle for OAuth2/auth failures.
- [x] Document a minimal safe support bundle for retry/idempotency behavior.
- [x] Document a minimal safe support bundle for timeout incidents.
- [x] Document a minimal safe support bundle for streaming ownership issues.
- [x] Document a minimal safe support bundle for performance investigations.
- [x] Include diagnostics snapshot export examples.
- [x] Include health detail examples.
- [x] Include log-category examples for startup summaries and exchange logs.
- [x] Include benchmark evidence links and explain promoted report scope.
- [x] Avoid instructing users to collect raw request or response bodies by
      default.
- [x] Link the page from relevant diagnostics, observability, benchmark, auth,
      timeout, and streaming docs.
- [x] Run documentation link checks.

Evidence:

- Added `docs/26-support-bundles.md` with safe baseline support-bundle contents, diagnostics snapshot export examples, Actuator `rhttpclients` capture, health detail examples, startup and exchange log category examples, and incident-specific bundles for configuration, OAuth2/auth, retry/idempotency, timeout, streaming ownership, and performance investigations.
- The support-bundle guidance avoids raw request bodies, raw response bodies, tokens, secrets, cookies, proxy credentials, concrete base URLs, and idempotency-key values by default.
- Linked the page from `README.md`, diagnostics, observability, benchmarks, auth, timeout, streaming, exchange logging, production checklist, and performance troubleshooting docs.
- `mvn -q -pl reactive-http-client-starter -Dtest=DocumentationReleaseArtifactTest test` passed.

---

## Priority 5 — Strict Unsafe-Retry Contracts

### [x] 3.1 Add opt-in strict mode for unsafe retry contracts
- [x] Choose the strict retry validation property name and default.
- [x] Preserve warning-only default behavior.
- [x] Reuse the existing retry-safety classifier where possible.
- [x] Gate strict failures on actual retry operator availability.
- [x] Treat idempotent HTTP methods as safe.
- [x] Treat non-overrideable default `Idempotency-Key` headers as safe.
- [x] Treat runtime-provided idempotency keys from supported contexts as safe
      only when startup can prove the contract, or document why warning mode is
      retained.
- [x] Include overloaded method signatures in strict failure diagnostics.
- [x] Include inherited endpoint context in strict failure diagnostics.
- [x] Add startup failure tests for unsafe retryable methods.
- [x] Add tests proving no-op retry wiring does not fail strict mode.
- [x] Add tests proving effective idempotency keys do not fail strict mode.
- [x] Add configuration metadata and docs for strict retry validation.
- [x] Run resilience, idempotency, and startup validation tests.

Evidence:

- Added per-client `reactive.http.clients.<name>.resilience.strict-unsafe-retry-validation`, default `false`, so existing unsafe retry behavior remains warning-only unless explicitly enabled.
- Strict validation runs at proxy startup only when resilience is enabled, the retry method is configured for the resolved HTTP method, the Retry operator is actually available, and the resolved Retry instance can make more than one attempt. No-op resilience wiring and max-attempts-one Retry instances are skipped.
- Strict validation allows safe HTTP retry methods, non-overrideable configured default `Idempotency-Key` headers, and method-level generated idempotency keys; runtime-supplied parameter, header-map, and Reactor-context keys remain documented as dynamic contracts that should use warning mode.
- Startup failure diagnostics include the concrete client interface, fully qualified declaring method signature, HTTP method, retry instance/source, and retry methods, including inherited overloaded methods.
- Added metadata, generated configuration reference, resilience documentation, and focused startup tests for unsafe POST failure, no-op retry availability, default/generated idempotency keys, overrideable default headers, max-attempts-one Retry instances, idempotent methods, and inherited overload diagnostics.
- `mvn -q -pl reactive-http-client-starter -Dtest=ReactiveHttpClientFactoryBeanDiagnosticsTest,ReactiveHttpClientConfigurationMetadataTest,ReactiveClientInvocationHandlerRetrySafetyTest,ReactiveClientInvocationHandlerRetryMethodsTest,IdempotencyKeySupportTest,DocumentationReleaseArtifactTest test` passed.

---

## Priority 6 — Strict Built-In Body-Signing Contracts

### [x] 3.2 Add opt-in strict mode for ambiguous outbound body signing
- [x] Choose the strict body-signing validation property name and default.
- [x] Preserve default source and behavior compatibility.
- [x] Scope strict validation to built-in body-signing auth providers.
- [x] Avoid guessing body-signing behavior for custom auth providers.
- [x] Reject unsupported multipart signing shapes in strict mode.
- [x] Reject unsupported non-repeatable streaming signing shapes in strict mode.
- [x] Validate byte-array body signing as supported.
- [x] Validate empty-body signing as supported.
- [x] Validate scalar JSON body signing under the documented codec contract.
- [x] Validate `String` body signing under the documented charset contract.
- [x] Validate publisher body signing under the documented repeatability/raw-byte
      contract.
- [x] Add clear startup diagnostics naming client, method, body shape, and auth
      mode.
- [x] Update auth provider docs with strict-mode examples.
- [x] Run SigV4, auth, request-body, and documentation tests.

Evidence:

- Added `reactive.http.clients.<name>.auth.aws-sig-v4.strict-body-signing-validation`, default `false`, under the built-in AWS SigV4 auth config. Existing runtime behavior remains unchanged unless the property is enabled.
- Strict startup validation runs only when object-style `auth.type: aws-sigv4` resolves to the starter built-in `AwsSigV4AuthProvider`; named `auth-provider` beans and custom `AuthProviderFactory` selections are treated as custom and skipped.
- Strict mode allows empty bodies, `byte[]`, `String`, and concrete JSON object bodies when an `ObjectMapper` is available and the startup-visible `Content-Type` is absent or JSON-compatible.
- Strict mode rejects multipart bodies, `Publisher` bodies, Java stream bodies, `DataBuffer` streaming bodies, `Resource` bodies, `@Body Object` or erased generic bodies, JSON object bodies when no `ObjectMapper` is available, JSON bodies with configured non-JSON `Content-Type`, and JSON bodies with runtime-supplied `Content-Type`. Diagnostics include client interface, method signature, HTTP method, path template, body shape, auth mode, and reason.
- Updated Spring configuration metadata, generated configuration reference, property binding coverage, and AWS SigV4 docs with a strict-mode YAML example.
- `mvn -q -pl reactive-http-client-starter -Dtest=ReactiveHttpClientFactoryBeanDiagnosticsTest,ReactiveHttpClientPropertiesTest,ReactiveHttpClientConfigurationMetadataTest,AuthProviderFactoryTest,AwsSigV4AuthProviderTest,OutboundAuthFilterTest,ReactiveClientInvocationHandlerBehaviorTest,MultipartRequestTest,DocumentationReleaseArtifactTest test` passed.

---

## Priority 7 — Generated Effective Configuration Examples

### [x] 4.1 Add generated effective-configuration examples
- [x] Decide whether examples are generated artifacts, validated snippets, or
      both.
- [x] Add or validate an inherited shared-interface configuration example.
- [x] Add or validate an OAuth2 client-credentials configuration example.
- [x] Add or validate an AWS SigV4 configuration example.
- [x] Add or validate proxy/TLS configuration examples.
- [x] Add or validate redirect-following configuration examples.
- [x] Add or validate strict retry configuration examples.
- [x] Add or validate strict body-signing configuration examples.
- [x] Add or validate diagnostics endpoint configuration examples.
- [x] Ensure examples bind against current configuration metadata.
- [x] Reject scalar assignments to metadata groups.
- [x] Reject stale or unknown property names.
- [x] Keep starter, OTel, and benchmark module properties separated.
- [x] Run configuration metadata and documentation release tests.

Evidence:

- Added `docs/examples/effective-configuration.md` as the validated effective-configuration examples artifact. The page covers shared inherited-client API maps, OAuth2 client credentials, AWS SigV4 strict body signing, proxy/TLS overrides, redirect following, strict retry, and the opt-in diagnostics endpoint.
- Linked the new examples page from `docs/examples/README.md`.
- Added `effectiveConfigurationExamplesCoverV16ScenariosAndUseStarterMetadata()` to require the V16 scenario properties, validate the snippets against generated configuration metadata, and keep OTel properties out of the starter examples page.
- Existing metadata tests continue to reject scalar assignments to metadata groups and malformed API-map leaves, and the new page is included in the stale/unknown property scan.
- `mvn -q -pl reactive-http-client-starter -Dtest=ReactiveHttpClientConfigurationMetadataTest test` passed.
- `mvn -q -pl reactive-http-client-starter -Dtest=DocumentationReleaseArtifactTest test` passed.

---

## Priority 8 — Generic Inherited Contract Diagnostics

### [x] 4.2 Improve startup validation messages for generic inherited contracts
- [x] Identify current startup diagnostics for inherited generic endpoint
      bindings.
- [x] Include parent interface in relevant diagnostics.
- [x] Include concrete client interface in relevant diagnostics.
- [x] Include type variable and resolved concrete type where useful.
- [x] Include endpoint method and declaring interface where useful.
- [x] Improve snapshot or diagnostics provider output only if it remains
      sanitized and bounded.
- [x] Add docs for correct `ApiOperators<T>` style declarations.
- [x] Add docs for incorrect generic declarations such as binding a train client
      to the bus response type.
- [x] Add tests for successful generic inherited diagnostics.
- [x] Add tests for incorrect generic inherited declarations or actionable
      failure messages.
- [x] Preserve existing inherited endpoint behavior.

Evidence:

- Added `genericBindings`, `responseType`, and `bodyType` to effective contract
  export and contract snapshots so inherited generic endpoint bindings are
  visible before runtime invocation.
- Startup DEBUG method-policy diagnostics now include the declaring parent
  interface, concrete client interface, inherited flag, generic binding, and
  resolved request/response body types.
- Documented correct and incorrect inherited generic client declarations in
  `docs/02-annotations.md` and `docs/14-test-helpers.md`.
- Added coverage for successful `ApiOperators<T>` resolution and a misbound
  train client that still resolves to the bus response type.
- Verified:
  `mvn -q -pl reactive-http-client-starter -Dtest=MethodMetadataValidationTest,ReactiveClientInvocationHandlerBehaviorTest,ReactiveHttpClientFactoryBeanDiagnosticsTest,EffectiveHttpClientContractExporterTest,ReactiveHttpClientContractSnapshotTest test`
- Verified:
  `mvn -q -pl reactive-http-client-starter -Dtest=DocumentationReleaseArtifactTest test`
- Verified: `git diff --check`

---

## Priority 9 — ResponseEntity and JSON Benchmark Re-Audit

### [x] 5.1 Re-audit ResponseEntity and JSON rows after `2.11.0`
- [x] Confirm published `2.11.0` artifacts resolve before comparing against
      them.
- [x] Run the published-baseline benchmark for `2.11.0`.
- [x] Run the current-workspace benchmark on the same machine.
- [x] Compare `Post Json` current versus published baseline.
- [x] Compare `Response Entity` current versus published baseline.
- [x] Repeat rows enough to distinguish noise from persistent movement.
- [x] Inspect allocation deltas for repeated movement.
- [x] Use profiler output only if repeated movement justifies it.
- [x] Document whether optimization is required.
- [x] If optimizing, record named before/after benchmark rows.
- [x] If not optimizing, record why no code change was made.
- [x] Keep public docs scenario-specific and avoid broad performance claims.

Evidence:

- User completed the first three steps with published-baseline and current-workspace
  release reports at
  `reactive-http-client-benchmarks/target/benchmark-reports/published-starter-2.11.0/release-jmh.md`
  and `reactive-http-client-benchmarks/target/benchmark-reports/release-jmh.md`.
- Generated the paired comparison with
  `mvn -Pbenchmarks,benchmark-compare -pl reactive-http-client-benchmarks -am verify -Dbenchmark.compare.current=reactive-http-client-benchmarks/target/benchmark-reports/release-jmh.json -Dbenchmark.compare.baseline=reactive-http-client-benchmarks/target/benchmark-reports/published-starter-2.11.0/release-jmh.json`,
  writing `reactive-http-client-benchmarks/target/benchmark-reports/benchmark-comparison.md`.
- `clientSideOverheadStarterPostJson` current versus published `2.11.0`:
  average time `61.057 us/op` versus `207.553 us/op` (`70.583%` faster),
  p50 `59.093 us/op` versus `195.45 us/op`, p95/p99 `71.193 us/op`
  versus `459.507 us/op`; average-time allocation moved from
  `28040.906 B/op` to `27558.631 B/op` (`1.72%` lower).
- `clientSideOverheadStarterResponseEntity` current versus published `2.11.0`:
  average time `59.448 us/op` versus `58.661 us/op` (`1.342%` slower),
  p50 `58.23 us/op` versus `56.279 us/op`, p95/p99 `69.954 us/op`
  versus `68.212 us/op`; average-time allocation moved from `27373.44 B/op`
  to `27405.011 B/op` (`0.115%` higher).
- Release benchmark profile uses `5` warmup iterations, `5` measurement
  iterations, `2` forks, throughput/average-time/sample-time modes, and the GC
  profiler, which was enough for this audit to distinguish the large Post Json
  movement from the small ResponseEntity noise-level movement.
- No additional profiler run or optimization was required: the only large named
  movement is an improvement in `clientSideOverheadStarterPostJson`;
  `clientSideOverheadStarterResponseEntity` is effectively flat and allocations
  are stable.
- No public performance docs were broadened; the audit evidence stays tied to
  the named benchmark rows and target-only comparison report.

---

## Priority 10 — Benchmark Prefix Classification Contract

### [x] 5.2 Separate no-network diagnostics audit output from release feature claims
- [x] Add report-generation tests for `clientSideOverhead*` rows.
- [x] Add report-generation tests for `starterFeature*` rows.
- [x] Add report-generation tests for `starterErrorMapping*` rows.
- [x] Add report-generation tests for no-network invocation rows.
- [x] Verify no-network rows do not appear in the optional-feature summary table.
- [x] Verify loopback `starterFeature*` rows remain in the optional-feature
      summary table.
- [x] Document benchmark naming conventions for loopback feature rows.
- [x] Document benchmark naming conventions for no-network audit rows.
- [x] Run benchmark module tests.

Evidence:

- Expanded `BenchmarkMarkdownReportTest` into focused report-generation coverage
  for `clientSideOverhead*`, `starterFeature*`, `starterErrorMapping*`, and
  no-network diagnostics/invocation rows.
- Verified `clientSideOverhead*` rows feed the comparison summary and raw results
  with the `Client-side overhead` label.
- Verified loopback `starterFeature*` rows remain in the starter-only optional
  feature summary and raw results with the `Optional starter feature` label.
- Verified `starterErrorMapping*` rows remain in the starter-only summary with
  the `Starter-only error-mapping overhead` label.
- Verified no-network rows such as `metadataOnlyExchangeLoggingGetNoBody`,
  `diagnosticsNoNetworkOneObserverGetNoBody`, and
  `runtimeDiagnosticsProviderClientSummaries` stay out of the optional-feature
  summary and render only as `No-network starter invocation` raw rows.
- Added a benchmark naming contract to `docs/22-benchmarks.md` that reserves
  `starterFeature*` for loopback feature rows and directs no-network diagnostics
  audits to descriptive non-feature names.
- Verified: `mvn -q -Pbenchmarks -pl reactive-http-client-benchmarks -am test`.
- Verified: `mvn -q -pl reactive-http-client-starter -Dtest=DocumentationReleaseArtifactTest test`.

---

## Priority 11 — Public API Compatibility Coverage Audit

### [x] 6.1 Expand public API baseline coverage for new V15/V16 helpers
- [x] Audit documented public starter types against japicmp includes.
- [x] Audit documented public test-helper types against japicmp includes.
- [x] Audit documented public OTel types against japicmp includes.
- [x] Include `ReactiveHttpClientDiagnosticsProvider` public surface where
      appropriate.
- [x] Include `ReactiveHttpClientDiagnosticsSnapshot` public surface where
      appropriate.
- [x] Include `AuthProviderException` public constructors and accessors where
      appropriate.
- [x] Include mock helper public APIs where appropriate.
- [x] Keep internal implementation classes out of the documented compatibility
      promise.
- [x] Document any explicit exclusions.
- [x] Run root API compatibility.
- [x] Run module-scoped API compatibility for starter and test modules.
- [x] Run API compatibility fixture script.

Evidence:

- Audited documented public starter surfaces against the japicmp include set.
  `ReactiveHttpClientDiagnosticsProvider*`,
  `ReactiveHttpClientDiagnosticsSnapshot`, `AuthProviderException`,
  annotations, exceptions, observability types, configuration properties, and
  extension-point packages were already covered. `SensitiveHeaders`,
  `MethodMetadataCache`, its public `MethodMetadata` return model, nested
  `ReactiveHttpClientContractSnapshot` fluent API types, and the public
  `ResilienceOperatorApplier` hook accepted by that builder were documented but
  missing from the explicit core helper includes, so they are now included in
  `pom.xml`.
- Audited public test helper and OTel surfaces. The existing include set covers
  `io.github.huynhngochuyhoang.httpstarter.test` and
  `io.github.huynhngochuyhoang.httpstarter.otel`, and generated japicmp reports
  list those filters for starter, test, and OTel module compatibility runs.
- Clarified the compatibility guide so documented public helpers are part of the
  compatibility promise while internal proxy, URI-resolution, transport/TLS,
  metadata-cache, and release-test fixture internals stay excluded unless
  explicitly listed in the POM include set.
- Verified root API compatibility with
  `mvn -Papi-compatibility -DskipTests verify`.
- Verified module-scoped starter compatibility with
  `mvn -pl reactive-http-client-starter -Papi-compatibility -DskipTests verify`.
- Verified module-scoped test-helper compatibility with
  `mvn -pl reactive-http-client-test -am -Papi-compatibility -DskipTests verify`.
  Running the test module without `-am` attempted to resolve the unpublished
  current starter `2.12.0` remotely and failed DNS resolution, so the
  reactor-backed module check is the reproducible local validation path.
- Verified compatibility guard fixtures with
  `bash scripts/verify-api-compatibility-fixtures.sh`.

---

## Priority 12 — Release Readiness for Next Patch or Minor

### [x] 6.2 Prepare release readiness for the next minor or patch
- [x] Decide patch versus minor after V16 scope is finalized.
- [x] Keep changelog entries under `Unreleased` until release prep starts.
- [x] Ensure release evidence commands name the selected next version.
- [x] Ensure release evidence commands name the selected API baseline.
- [x] Verify promoted benchmark links are current if performance claims are
      included.
- [x] Verify generated configuration docs are current.
- [x] Verify Markdown links pass.
- [x] Verify baseline artifact resolution commands are listed.
- [x] Verify API compatibility commands are listed.
- [x] Verify manual benchmark commands are listed when performance claims are
      included.
- [x] Run focused release documentation tests.
- [x] Run the full reactor test suite.
- [x] Run `git diff --check`.
- [x] Mark `ROADMAP.md` completed only after implementation and evidence are
      complete.

Evidence:

- Selected the next release line as the minor `2.12.0` candidate because V16
  adds opt-in Actuator diagnostics, strict validation modes, public diagnostics
  examples, and release-readiness behavior. The API and published benchmark
  baseline remains the published `2.11.0` line.
- Updated `CHANGELOG.md` under `Unreleased` with V16 Added, Changed, and Fixed
  entries. No `2.12.0` dated release section was created.
- Generated readiness evidence reports `projectVersion=2.12.0`,
  `apiCompatibilityBaselineVersion=2.11.0`, generated configuration docs as
  `current`, Markdown links as `pass`, and stale benchmark-report links as
  `pass`.
- Generated readiness evidence lists baseline artifact resolution commands for
  starter, test-helper, and OTel `2.11.0` artifacts; root compatibility,
  module-scoped starter compatibility, and compatibility fixture commands; and
  benchmark package, smoke, current-candidate release, and published-baseline
  commands.
- The readiness manifest points the future promoted report to
  `docs/benchmark-report-2.12.0.md` and currently marks it `missing`. This is
  expected until a release-quality `2.12.0` benchmark is run and promoted for
  public performance claims; current public performance docs continue to cite
  the historical `2.11.0` promoted report.
- Marked `roadmaps/v16/ROADMAP.md` completed and checked all acceptance criteria
  after the execution checklist reached completion.
- Verified focused release documentation with
  `mvn -q -pl reactive-http-client-starter -Dtest=DocumentationReleaseArtifactTest test`.
- Verified the full reactor test suite with `mvn test`; result: 741 tests, 0
  failures, 0 errors, 0 skipped.
- Verified formatting with `git diff --check`.

