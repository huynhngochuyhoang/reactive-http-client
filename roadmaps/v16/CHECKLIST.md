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

### [ ] 4.2 Improve startup validation messages for generic inherited contracts
- [ ] Identify current startup diagnostics for inherited generic endpoint
      bindings.
- [ ] Include parent interface in relevant diagnostics.
- [ ] Include concrete client interface in relevant diagnostics.
- [ ] Include type variable and resolved concrete type where useful.
- [ ] Include endpoint method and declaring interface where useful.
- [ ] Improve snapshot or diagnostics provider output only if it remains
      sanitized and bounded.
- [ ] Add docs for correct `ApiOperators<T>` style declarations.
- [ ] Add docs for incorrect generic declarations such as binding a train client
      to the bus response type.
- [ ] Add tests for successful generic inherited diagnostics.
- [ ] Add tests for incorrect generic inherited declarations or actionable
      failure messages.
- [ ] Preserve existing inherited endpoint behavior.

Evidence:

- Pending.

---

## Priority 9 — ResponseEntity and JSON Benchmark Re-Audit

### [ ] 5.1 Re-audit ResponseEntity and JSON rows after `2.11.0`
- [ ] Confirm published `2.11.0` artifacts resolve before comparing against
      them.
- [ ] Run the published-baseline benchmark for `2.11.0`.
- [ ] Run the current-workspace benchmark on the same machine.
- [ ] Compare `Post Json` current versus published baseline.
- [ ] Compare `Response Entity` current versus published baseline.
- [ ] Repeat rows enough to distinguish noise from persistent movement.
- [ ] Inspect allocation deltas for repeated movement.
- [ ] Use profiler output only if repeated movement justifies it.
- [ ] Document whether optimization is required.
- [ ] If optimizing, record named before/after benchmark rows.
- [ ] If not optimizing, record why no code change was made.
- [ ] Keep public docs scenario-specific and avoid broad performance claims.

Evidence:

- Pending.

---

## Priority 10 — Benchmark Prefix Classification Contract

### [ ] 5.2 Separate no-network diagnostics audit output from release feature claims
- [ ] Add report-generation tests for `clientSideOverhead*` rows.
- [ ] Add report-generation tests for `starterFeature*` rows.
- [ ] Add report-generation tests for `starterErrorMapping*` rows.
- [ ] Add report-generation tests for no-network invocation rows.
- [ ] Verify no-network rows do not appear in the optional-feature summary table.
- [ ] Verify loopback `starterFeature*` rows remain in the optional-feature
      summary table.
- [ ] Document benchmark naming conventions for loopback feature rows.
- [ ] Document benchmark naming conventions for no-network audit rows.
- [ ] Run benchmark module tests.

Evidence:

- Pending.

---

## Priority 11 — Public API Compatibility Coverage Audit

### [ ] 6.1 Expand public API baseline coverage for new V15/V16 helpers
- [ ] Audit documented public starter types against japicmp includes.
- [ ] Audit documented public test-helper types against japicmp includes.
- [ ] Audit documented public OTel types against japicmp includes.
- [ ] Include `ReactiveHttpClientDiagnosticsProvider` public surface where
      appropriate.
- [ ] Include `ReactiveHttpClientDiagnosticsSnapshot` public surface where
      appropriate.
- [ ] Include `AuthProviderException` public constructors and accessors where
      appropriate.
- [ ] Include mock helper public APIs where appropriate.
- [ ] Keep internal implementation classes out of the documented compatibility
      promise.
- [ ] Document any explicit exclusions.
- [ ] Run root API compatibility.
- [ ] Run module-scoped API compatibility for starter and test modules.
- [ ] Run API compatibility fixture script.

Evidence:

- Pending.

---

## Priority 12 — Release Readiness for Next Patch or Minor

### [ ] 6.2 Prepare release readiness for the next minor or patch
- [ ] Decide patch versus minor after V16 scope is finalized.
- [ ] Keep changelog entries under `Unreleased` until release prep starts.
- [ ] Ensure release evidence commands name the selected next version.
- [ ] Ensure release evidence commands name the selected API baseline.
- [ ] Verify promoted benchmark links are current if performance claims are
      included.
- [ ] Verify generated configuration docs are current.
- [ ] Verify Markdown links pass.
- [ ] Verify baseline artifact resolution commands are listed.
- [ ] Verify API compatibility commands are listed.
- [ ] Verify manual benchmark commands are listed when performance claims are
      included.
- [ ] Run focused release documentation tests.
- [ ] Run the full reactor test suite.
- [ ] Run `git diff --check`.
- [ ] Mark `ROADMAP.md` completed only after implementation and evidence are
      complete.

Evidence:

- Pending.

