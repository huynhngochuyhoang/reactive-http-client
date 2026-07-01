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

### [ ] 2.2 Add production support bundle examples
- [ ] Add a support-bundle documentation page.
- [ ] Document a minimal safe support bundle for configuration issues.
- [ ] Document a minimal safe support bundle for OAuth2/auth failures.
- [ ] Document a minimal safe support bundle for retry/idempotency behavior.
- [ ] Document a minimal safe support bundle for timeout incidents.
- [ ] Document a minimal safe support bundle for streaming ownership issues.
- [ ] Document a minimal safe support bundle for performance investigations.
- [ ] Include diagnostics snapshot export examples.
- [ ] Include health detail examples.
- [ ] Include log-category examples for startup summaries and exchange logs.
- [ ] Include benchmark evidence links and explain promoted report scope.
- [ ] Avoid instructing users to collect raw request or response bodies by
      default.
- [ ] Link the page from relevant diagnostics, observability, benchmark, auth,
      timeout, and streaming docs.
- [ ] Run documentation link checks.

Evidence:

- Pending.

---

## Priority 5 — Strict Unsafe-Retry Contracts

### [ ] 3.1 Add opt-in strict mode for unsafe retry contracts
- [ ] Choose the strict retry validation property name and default.
- [ ] Preserve warning-only default behavior.
- [ ] Reuse the existing retry-safety classifier where possible.
- [ ] Gate strict failures on actual retry operator availability.
- [ ] Treat idempotent HTTP methods as safe.
- [ ] Treat effective default `Idempotency-Key` headers as safe.
- [ ] Treat runtime-provided idempotency keys from supported contexts as safe
      only when startup can prove the contract, or document why warning mode is
      retained.
- [ ] Include overloaded method signatures in strict failure diagnostics.
- [ ] Include inherited endpoint context in strict failure diagnostics.
- [ ] Add startup failure tests for unsafe retryable methods.
- [ ] Add tests proving no-op retry wiring does not fail strict mode.
- [ ] Add tests proving effective idempotency keys do not fail strict mode.
- [x] Add configuration metadata and docs for strict retry validation.
- [ ] Run resilience, idempotency, and startup validation tests.

Evidence:

- Pending.

---

## Priority 6 — Strict Built-In Body-Signing Contracts

### [ ] 3.2 Add opt-in strict mode for ambiguous outbound body signing
- [ ] Choose the strict body-signing validation property name and default.
- [ ] Preserve default source and behavior compatibility.
- [ ] Scope strict validation to built-in body-signing auth providers.
- [ ] Avoid guessing body-signing behavior for custom auth providers.
- [ ] Reject unsupported multipart signing shapes in strict mode.
- [ ] Reject unsupported non-repeatable streaming signing shapes in strict mode.
- [ ] Validate byte-array body signing as supported.
- [ ] Validate empty-body signing as supported.
- [ ] Validate scalar JSON body signing under the documented codec contract.
- [ ] Validate `String` body signing under the documented charset contract.
- [ ] Validate publisher body signing under the documented repeatability/raw-byte
      contract.
- [ ] Add clear startup diagnostics naming client, method, body shape, and auth
      mode.
- [ ] Update auth provider docs with strict-mode examples.
- [ ] Run SigV4, auth, request-body, and documentation tests.

Evidence:

- Pending.

---

## Priority 7 — Generated Effective Configuration Examples

### [ ] 4.1 Add generated effective-configuration examples
- [ ] Decide whether examples are generated artifacts, validated snippets, or
      both.
- [ ] Add or validate an inherited shared-interface configuration example.
- [ ] Add or validate an OAuth2 client-credentials configuration example.
- [ ] Add or validate an AWS SigV4 configuration example.
- [ ] Add or validate proxy/TLS configuration examples.
- [ ] Add or validate redirect-following configuration examples.
- [ ] Add or validate strict retry configuration examples.
- [ ] Add or validate strict body-signing configuration examples.
- [ ] Add or validate diagnostics endpoint configuration examples.
- [ ] Ensure examples bind against current configuration metadata.
- [ ] Reject scalar assignments to metadata groups.
- [ ] Reject stale or unknown property names.
- [ ] Keep starter, OTel, and benchmark module properties separated.
- [ ] Run configuration metadata and documentation release tests.

Evidence:

- Pending.

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

