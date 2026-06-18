# Reactive HTTP Client — Roadmap V15 Checklist

Companion to [`ROADMAP.md`](ROADMAP.md). Execute priorities in order unless a
release blocker requires reordering.

---

## Priority 1 — Prepare the Next Release Baseline Transition

### [x] 4.1 Prepare the next release baseline transition
- [x] Decide whether V15 is a patch or minor release after scope is finalized.
- [x] Document the chosen next version in release compatibility docs.
- [x] Document whether the API baseline remains `2.9.0` or moves to published
      `2.10.0`.
- [x] Resolve published baseline artifacts for `reactive-http-client-starter`.
- [x] Resolve published baseline artifacts for `reactive-http-client-test`.
- [x] Resolve published baseline artifacts for `reactive-http-client-otel`.
- [x] Keep `api.compatibility.baseline.version` different from the current
      reactor version.
- [x] Verify root API compatibility rejects self-comparison.
- [x] Verify module-scoped API compatibility rejects self-comparison.
- [x] Update benchmark published-baseline commands if the baseline changes.
- [x] Update benchmark published-baseline report paths if the baseline changes.
- [x] Update release evidence docs with the selected version and baseline.
- [x] Run focused documentation release tests.
- [x] Run `git diff --check`.

Evidence:

- V15 is documented as a planned minor `2.11.0` cycle if the drafted public
  diagnostics, auth, health, and test-helper work ships. The current reactor
  remains `2.10.0`, so `api.compatibility.baseline.version` remains `2.9.0`
  until the reactor version moves off `2.10.0`.
- Release compatibility docs now document the exact transition: resolve
  published `2.10.0` artifacts first, then move the API baseline and benchmark
  published-baseline paths to `2.10.0` in the same change after the reactor is
  bumped to `2.11.0`.
- Benchmark docs now state that the V15 published-baseline command stays on
  `2.9.0` while the reactor remains `2.10.0`, then moves to `2.10.0` with the
  baseline property after the `2.11.0` reactor bump.
- Published `2.10.0` artifacts resolved successfully:
  `mvn dependency:get -Dartifact=io.github.huynhngochuyhoang:reactive-http-client-starter:2.10.0`,
  `mvn dependency:get -Dartifact=io.github.huynhngochuyhoang:reactive-http-client-test:2.10.0`,
  and `mvn dependency:get -Dartifact=io.github.huynhngochuyhoang:reactive-http-client-otel:2.10.0`.
- `mvn -pl reactive-http-client-starter -Dtest=DocumentationReleaseArtifactTest test`
  passed with 9 tests, 0 failures, 0 errors, and 0 skipped.
- `mvn -Papi-compatibility -DskipTests validate` passed with the configured
  `2.9.0` baseline and executed the guard in the root, starter, test, and OTel
  modules.
- `mvn -Papi-compatibility -DskipTests -Dapi.compatibility.baseline.version=2.10.0 validate`
  failed as expected with the guard rejecting current-reactor self-comparison.
- `mvn -pl reactive-http-client-starter -Papi-compatibility -DskipTests -Dapi.compatibility.baseline.version=2.10.0 validate`
  failed as expected with the module-scoped guard rejecting current-reactor
  self-comparison.

---

## Priority 2 — Optional Diagnostics Snapshot Export Helpers

### [x] 1.1 Add optional diagnostics snapshot export helpers
- [x] Choose the smallest public helper surface for diagnostics snapshot export.
- [x] Render `ReactiveHttpClientDiagnosticsProvider` summaries as deterministic
      Markdown or JSON.
- [x] Include project version, client count, endpoint count, and inherited
      endpoint count.
- [x] Include auth mode, redirect flag, timeout source summary, and resilience
      summary.
- [x] Keep output sanitized and omit concrete base URL values.
- [x] Omit auth secrets, header values, proxy credentials, auth provider bean
      names, request bodies, and response bodies.
- [x] Keep the helper explicit; do not auto-publish an Actuator endpoint.
- [x] Add tests for deterministic ordering.
- [x] Add tests proving sensitive fields are redacted or absent.
- [x] Document support-bundle and custom endpoint usage.
- [x] Preserve source compatibility for `ReactiveHttpClientDiagnosticsProvider`.
- [x] Run starter tests covering diagnostics provider behavior.
- [x] Run documentation link checks.

Evidence:

- Added `ReactiveHttpClientDiagnosticsSnapshot`, a small explicit helper that
  renders `ReactiveHttpClientDiagnosticsProvider` summaries to deterministic
  Markdown or JSON. It does not register an Actuator endpoint, controller, log
  line, or file writer.
- Snapshot output includes project version, total client count, total endpoint
  count, total inherited endpoint count, and per-client rows/objects with client
  name, interface, base URL source, timeout source/value, resilience summary,
  auth mode, redirect flag, endpoint count, and inherited endpoint count.
- Snapshot rendering sorts clients by client name and interface so support
  artifacts and approval-style tests are stable.
- Added diagnostics tests proving rendered Markdown and JSON omit concrete base
  URL values, auth provider bean names, sensitive headers, request bodies, and
  response bodies.
- Updated diagnostic context docs with Markdown/JSON snapshot examples and
  clarified that snapshot rendering remains explicit and does not publish an
  endpoint by default.
- `mvn -pl reactive-http-client-starter -Dtest=ReactiveHttpClientDiagnosticsProviderTest,DocumentationReleaseArtifactTest test`
  passed with 12 tests, 0 failures, 0 errors, and 0 skipped.

---

## Priority 3 — Health Indicator Troubleshooting Detail

### [ ] 1.2 Improve health indicator troubleshooting detail
- [ ] Add sanitized per-client health details for sample count.
- [ ] Add sanitized per-client health details for error count.
- [ ] Add sanitized per-client health details for error rate.
- [ ] Add threshold and reason fields for evaluated clients.
- [ ] Keep details bounded by client name.
- [ ] Avoid URL, header, auth, proxy, request-body, and response-body values.
- [ ] Preserve `reactive.http.observability.health.enabled=false` behavior.
- [ ] Preserve missing-Actuator and missing-`MeterRegistry` behavior.
- [ ] Add no-sample health tests.
- [ ] Add below-threshold health tests.
- [ ] Add above-threshold health tests.
- [ ] Update observability docs with health detail semantics.
- [ ] Clarify health indicator versus diagnostics provider versus exchange logs.
- [ ] Run observability and health indicator tests.

---

## Priority 4 — OAuth2 Token Refresh Diagnostics

### [ ] 2.1 Harden OAuth2 token refresh diagnostics
- [ ] Review token acquisition 4xx failure messages.
- [ ] Review token acquisition 5xx failure messages.
- [ ] Review malformed token response failure messages.
- [ ] Review missing `access_token` failure messages.
- [ ] Verify token endpoint response bodies remain bounded.
- [ ] Verify token endpoint response bodies are redacted in logs and exceptions.
- [ ] Verify client secrets are never included in exceptions or logs.
- [ ] Verify access tokens and refresh tokens are never included in exceptions
      or logs.
- [ ] Add tests for token endpoint 4xx.
- [ ] Add tests for token endpoint 5xx.
- [ ] Add tests for malformed JSON.
- [ ] Add tests for missing access token.
- [ ] Add tests for expiry leeway refresh behavior.
- [ ] Document `auth-style: form-post`.
- [ ] Document expiry leeway and cache behavior.
- [ ] Document 401 invalidation behavior.
- [ ] Add a complete YAML client-credentials example.
- [ ] Preserve source compatibility for auth extension points.

---

## Priority 5 — Auth-Aware Mock Helper Assertions

### [ ] 2.3 Add auth-aware mock helper assertions
- [ ] Add a mock assertion for auth header presence after filters run.
- [ ] Add a mock assertion for auth header absence after filters run.
- [ ] Ensure assertion failure messages redact sensitive header values.
- [ ] Add a compact 401 invalidation simulation path.
- [ ] Verify one invalidation retry records both outbound attempts.
- [ ] Keep helpers independent from a concrete OAuth2 server implementation.
- [ ] Document auth header assertions in test helper docs.
- [ ] Document 401 invalidation test usage.
- [ ] Preserve existing mock helper source compatibility.
- [ ] Run `reactive-http-client-test` module tests.
- [ ] Run starter auth behavior tests.

---

## Priority 6 — Redirect-Following Contract Coverage

### [ ] 3.1 Expand redirect-following contract coverage
- [ ] Add default visible-3xx coverage for `301`.
- [ ] Add default visible-3xx coverage for `302`.
- [ ] Add default visible-3xx coverage for `303`.
- [ ] Add default visible-3xx coverage for `307`.
- [ ] Add default visible-3xx coverage for `308`.
- [ ] Add opt-in follow-redirects coverage for `301`.
- [ ] Add opt-in follow-redirects coverage for `302`.
- [ ] Add opt-in follow-redirects coverage for `303`.
- [ ] Add opt-in follow-redirects coverage for `307`.
- [ ] Add opt-in follow-redirects coverage for `308`.
- [ ] Verify default behavior still returns visible redirects to proxy callers.
- [ ] Verify safe redirect cases work when `follow-redirects=true`.
- [ ] Verify docs do not promise sensitive headers across cross-authority
      redirects.
- [ ] Verify observer final request fields match the documented redirect
      semantics.
- [ ] Verify exchange-log final request fields match the documented redirect
      semantics.
- [ ] Document method and body replay limits for POST, PATCH, and streaming
      uploads.
- [ ] Run redirect and error-handling tests.

---

## Priority 7 — Streaming Response Ownership Re-Audit

### [ ] 3.2 Re-audit streaming response ownership
- [ ] Re-run real `WebClient` ownership coverage for `Flux<DataBuffer>`.
- [ ] Re-run real `WebClient` ownership coverage for
      `Mono<ResponseEntity<Flux<DataBuffer>>>`.
- [ ] Add cancellation coverage for starter-owned discarded buffers.
- [ ] Add cancellation coverage for subscriber-owned emitted buffers.
- [ ] Verify streaming bodies remain consumable after envelope emission.
- [ ] Verify diagnostics complete at the documented envelope boundary.
- [ ] Verify lifecycle hooks do not imply inner stream consumption.
- [ ] Verify observer events do not imply inner stream consumption.
- [ ] Verify exchange logs do not imply inner stream consumption.
- [ ] Avoid body buffering for streaming diagnostics.
- [ ] Update streaming docs if any wording is ambiguous.
- [ ] Run streaming response tests.
- [ ] Run timeout tests that cover streaming behavior.

---

## Priority 8 — Bodiless and Unexpected-Body Contracts

### [ ] 3.3 Clarify bodiless and unexpected-body contracts
- [ ] Audit `Mono<Void>` bodiless handling with real `ClientResponse` paths.
- [ ] Audit `Mono<ResponseEntity<Void>>` bodiless handling with real
      `ClientResponse` paths.
- [ ] Audit `HEAD` bodiless handling.
- [ ] Audit `OPTIONS` bodiless handling.
- [ ] Verify empty success bodies complete normally.
- [ ] Verify unexpected successful bodies are drained or released as documented.
- [ ] Verify pooled connections remain reusable when the transport permits it.
- [ ] Verify error body capture caps remain unchanged.
- [ ] Verify error body truncation metadata remains unchanged.
- [ ] Document draining for connection reuse versus exposing response bodies.
- [ ] Add tests for empty bodiless responses.
- [ ] Add tests for unexpected-body bodiless responses.
- [ ] Run response entity and bodiless response tests.

---

## Priority 9 — Configuration Metadata Drift Checks

### [ ] 4.2 Tighten configuration metadata drift checks
- [ ] Verify starter metadata group `sourceType` values resolve on starter tests.
- [ ] Verify OTel metadata group `sourceType` values resolve on OTel tests.
- [ ] Keep nested group metadata pointed at declaring configuration classes.
- [ ] Validate scalar `.properties` examples against property metadata only.
- [ ] Validate scalar YAML leaves against property metadata only.
- [ ] Reject scalar assignments that match only metadata groups.
- [ ] Avoid masking malformed API-map examples.
- [ ] Preserve block-list YAML validation for list-valued properties.
- [ ] Verify every documented property has metadata.
- [ ] Verify every metadata property appears in generated configuration docs.
- [ ] Require descriptions for new metadata properties.
- [ ] Require defaults where defaults are meaningful.
- [ ] Run starter configuration metadata tests.
- [ ] Run OTel configuration metadata tests.
- [ ] Regenerate `docs/configuration-properties.md` if metadata changes.

---

## Priority 10 — Startup Configuration Summary Logging

### [ ] 1.3 Add startup configuration summary logging
- [ ] Decide whether startup summary is opt-in property based or DEBUG-only.
- [ ] Add sanitized one-line summary per client.
- [ ] Include client name and interface.
- [ ] Include endpoint count and inherited endpoint count.
- [ ] Include auth mode.
- [ ] Include timeout source summary.
- [ ] Include resilience enabled/disabled summary.
- [ ] Include redirect-following flag.
- [ ] Include observability enabled/disabled summary.
- [ ] Reuse diagnostics snapshot sanitization rules.
- [ ] Verify summary output agrees with `ReactiveHttpClientDiagnosticsProvider`.
- [ ] Verify default configuration does not add INFO log noise.
- [ ] Add logging tests for enabled summary output.
- [ ] Add logging tests for sanitized output.
- [ ] Document startup summary usage.

---

## Priority 11 — Release Readiness Snapshot

### [ ] 4.3 Add release readiness snapshot for docs, metadata, and benchmarks
- [ ] Extend release evidence manifest with a top-level readiness summary.
- [ ] Include project version.
- [ ] Include API compatibility baseline version.
- [ ] Include promoted benchmark report path.
- [ ] Include generated configuration reference status.
- [ ] Include Markdown link validation status.
- [ ] Include pending manual release commands.
- [ ] Distinguish generated test evidence from manual release evidence.
- [ ] Keep manual benchmark commands pending until explicitly run.
- [ ] Keep manual compatibility commands pending until explicitly run.
- [ ] Surface missing promoted benchmark reports.
- [ ] Surface stale version links.
- [ ] Keep release evidence under `target/release-evidence/`.
- [ ] Verify target-only evidence is not documented as source-controlled output.
- [ ] Run `DocumentationReleaseArtifactTest`.

---

## Priority 12 — AWS SigV4 and Raw-Body Signing Contracts

### [ ] 2.2 Audit AWS SigV4 and raw-body signing contracts
- [ ] Add SigV4 contract tests for scalar JSON bodies.
- [ ] Add SigV4 contract tests for byte array bodies.
- [ ] Add SigV4 contract tests for string bodies.
- [ ] Add SigV4 contract tests for empty bodies.
- [ ] Add SigV4 contract tests for publisher bodies.
- [ ] Add SigV4 contract tests for streaming upload bodies.
- [ ] Verify signed content hashes match bytes on the wire for supported shapes.
- [ ] Verify publisher uploads do not sign an empty payload when the request body
      is non-empty.
- [ ] Document unsupported non-repeatable signing behavior.
- [ ] Avoid buffering large or streaming bodies only for signing convenience.
- [ ] Preserve auth provider extension source compatibility.
- [ ] Run auth and request-body tests.

---

## Priority 13 — Post-V14 Benchmark Audits

### [ ] 5.1 Re-run default and optional feature benchmark audits after V14
- [ ] Run current-vs-published benchmark pair for default success path.
- [ ] Run current-vs-published benchmark pair for optional diagnostics.
- [ ] Run current-vs-published benchmark pair for error mapping.
- [ ] Add and run auth-enabled benchmark rows only if V15 auth work needs them.
- [ ] Keep current and published-baseline reports in distinct paths.
- [ ] Apply V13 review triggers as review-only signals.
- [ ] Rerun any review-trigger movement on the same machine before acting.
- [ ] Record before/after evidence for every optimization.
- [ ] Prefer removing redundant work over adding caches.
- [ ] Verify optional feature rows are not compared to baselines without
      equivalent work.
- [ ] Update performance docs only with scenario-specific claims.
- [ ] Run benchmark smoke after benchmark code changes.
- [ ] Run release-quality benchmark when release notes include performance
      wording.

---

## Priority 14 — Observer and Lifecycle Overhead Audit

### [ ] 5.2 Audit observer and lifecycle overhead with multiple observers/hooks
- [ ] Add optional diagnostics benchmark row for one observer.
- [ ] Add optional diagnostics benchmark row for multiple observers.
- [ ] Add optional diagnostics benchmark row for one lifecycle hook.
- [ ] Add optional diagnostics benchmark row for multiple ordered lifecycle
      hooks.
- [ ] Inspect per-call allocation in observer lookup.
- [ ] Inspect per-call allocation in lifecycle hook lookup.
- [ ] Inspect composite observer construction overhead.
- [ ] Reuse immutable observer snapshots only when Spring semantics allow it.
- [ ] Reuse immutable lifecycle hook snapshots only when Spring semantics allow
      it.
- [ ] Preserve observer ordering.
- [ ] Preserve lifecycle hook ordering.
- [ ] Preserve observer failure isolation.
- [ ] Add tests for dynamic bean behavior if caching is introduced.
- [ ] Run observer and lifecycle tests.
- [ ] Record benchmark evidence before and after any optimization.
