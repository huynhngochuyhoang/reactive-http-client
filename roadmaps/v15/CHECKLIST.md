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

- V15 is released as the minor `2.11.0` line. The reactor now declares `2.11.0`
  and `api.compatibility.baseline.version` now points at published `2.10.0`
  artifacts.
- Release compatibility docs now document the exact transition: resolve
  published `2.10.0` artifacts first, then move the API baseline and benchmark
  published-baseline paths to `2.10.0` in the same change after the reactor is
  bumped to `2.11.0`.
- Benchmark docs now use `2.10.0` for the V15 published-baseline command and
  `published-starter-2.10.0` report paths after the `2.11.0` reactor bump.
- Published `2.10.0` artifacts resolved successfully:
  `mvn dependency:get -Dartifact=io.github.huynhngochuyhoang:reactive-http-client-starter:2.10.0`,
  `mvn dependency:get -Dartifact=io.github.huynhngochuyhoang:reactive-http-client-test:2.10.0`,
  and `mvn dependency:get -Dartifact=io.github.huynhngochuyhoang:reactive-http-client-otel:2.10.0`.
- `mvn -pl reactive-http-client-starter -Dtest=DocumentationReleaseArtifactTest test`
  passed with 9 tests, 0 failures, 0 errors, and 0 skipped.
- `mvn -q test` passed for the full reactor test suite.
- `bash scripts/verify-api-compatibility-fixtures.sh` passed: additive API
  changes were accepted and constructor removal was rejected.
- `mvn -Papi-compatibility -DskipTests verify` passed with the configured
  `2.10.0` baseline and executed the guard in the root, starter, test, and OTel
  modules.
- `mvn -pl reactive-http-client-starter -Papi-compatibility -DskipTests verify`
  passed with the configured `2.10.0` baseline and exercised the module-scoped
  guard path.
- `mvn -Papi-compatibility -DskipTests -Dapi.compatibility.baseline.version=2.11.0 validate`
  fails as expected with the guard rejecting current-reactor self-comparison.
- `mvn -pl reactive-http-client-starter -Papi-compatibility -DskipTests -Dapi.compatibility.baseline.version=2.11.0 validate`
  fails as expected with the module-scoped guard rejecting current-reactor
  self-comparison.
- Promoted release-quality benchmark evidence was added at
  `docs/benchmark-report-2.11.0.md` from the completed manual release benchmark
  run, and performance docs/changelog now cite that source-controlled report.
- `git diff --check` passed after the release-prep edits.

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

### [x] 1.2 Improve health indicator troubleshooting detail
- [x] Add sanitized per-client health details for sample count.
- [x] Add sanitized per-client health details for error count.
- [x] Add sanitized per-client health details for error rate.
- [x] Add threshold and reason fields for evaluated clients.
- [x] Keep details bounded by client name.
- [x] Avoid URL, header, auth, proxy, request-body, and response-body values.
- [x] Preserve `reactive.http.observability.health.enabled=false` behavior.
- [x] Preserve missing-Actuator and missing-`MeterRegistry` behavior.
- [x] Add no-sample health tests.
- [x] Add below-threshold health tests.
- [x] Add above-threshold health tests.
- [x] Update observability docs with health detail semantics.
- [x] Clarify health indicator versus diagnostics provider versus exchange logs.
- [x] Run observability and health indicator tests.

Evidence:

- `HttpClientHealthIndicator` now keeps existing `samples` and `errors` keys and
  adds per-client `sampleCount`, `errorCount`, `minSamples`,
  `errorRateThreshold`, and `reason` fields.
- Per-client reasons are bounded enum-like strings: `NO_SAMPLES`,
  `INSUFFICIENT_SAMPLES`, `ERROR_RATE_WITHIN_THRESHOLD`, and
  `ERROR_RATE_ABOVE_THRESHOLD`. Details remain keyed by client name and do not
  include URLs, headers, auth, proxy, request-body, or response-body values.
- Added health tests for no-delta samples and extended below-threshold and
  above-threshold assertions to cover threshold and reason fields. Existing
  auto-configuration coverage still verifies
  `reactive.http.observability.health.enabled=false`.
- Updated `docs/08-observability.md` with health detail semantics and sample
  actuator output. Updated `docs/21-diagnostic-contexts.md` to clarify health
  indicator output versus diagnostics snapshots and exchange logs.
- `mvn -pl reactive-http-client-starter -Dtest=HttpClientHealthIndicatorTest,ReactiveHttpClientAutoConfigurationTest,MicrometerHttpClientObserverTest test`
  passed with 54 tests, 0 failures, 0 errors, and 0 skipped.
- `mvn -pl reactive-http-client-starter -Dtest=DocumentationReleaseArtifactTest test`
  passed with 9 tests, 0 failures, 0 errors, and 0 skipped.

---

## Priority 4 — OAuth2 Token Refresh Diagnostics

### [x] 2.1 Harden OAuth2 token refresh diagnostics
- [x] Review token acquisition 4xx failure messages.
- [x] Review token acquisition 5xx failure messages.
- [x] Review malformed token response failure messages.
- [x] Review missing `access_token` failure messages.
- [x] Verify token endpoint response bodies remain bounded.
- [x] Verify token endpoint response bodies are redacted in logs and exceptions.
- [x] Verify client secrets are never included in exceptions or logs.
- [x] Verify access tokens and refresh tokens are never included in exceptions
      or logs.
- [x] Add tests for token endpoint 4xx.
- [x] Add tests for token endpoint 5xx.
- [x] Add tests for malformed JSON.
- [x] Add tests for missing access token.
- [x] Add tests for expiry leeway refresh behavior.
- [x] Document `auth-style: form-post`.
- [x] Document expiry leeway and cache behavior.
- [x] Document 401 invalidation behavior.
- [x] Add a complete YAML client-credentials example.
- [x] Preserve source compatibility for auth extension points.

Evidence:

- `OAuth2ClientCredentialsTokenProvider` now handles token endpoint responses with
  `exchangeToMono`, mapping HTTP 4xx/5xx responses to `AuthProviderException`
  messages that include the status and a bounded, redacted response-body snippet.
- Token response snippets are capped at 1024 characters and redact
  `client_secret`, `access_token`, `refresh_token`, and `id_token` style values.
  Malformed JSON and missing `access_token` paths use fixed diagnostic messages
  that do not include response bodies or token values.
- Added an additive `AuthProviderException(String clientName, String message,
  Throwable cause)` constructor; existing constructors remain unchanged.
- Added OAuth2 tests for token endpoint 4xx, token endpoint 5xx, malformed JSON,
  and missing access token. Existing expiry-leeway and refresh-cache tests remain
  covered by `OAuth2ClientCredentialsTokenProviderTest` and
  `RefreshingBearerAuthProviderTest`.
- Updated `docs/06-auth-providers.md` with a complete YAML client-credentials
  example, `auth-style: form-post`, expiry leeway/cache behavior, 401
  invalidation retry behavior, and redacted token-refresh diagnostics.
- `mvn -pl reactive-http-client-starter -Dtest=OAuth2ClientCredentialsTokenProviderTest,RefreshingBearerAuthProviderTest,OutboundAuthFilterTest,AuthProviderFactoryTest test`
  passed with 28 tests, 0 failures, 0 errors, and 0 skipped.
- `mvn -pl reactive-http-client-starter -Dtest=DocumentationReleaseArtifactTest test`
  passed with 9 tests, 0 failures, 0 errors, and 0 skipped.
- `mvn -pl reactive-http-client-starter -Papi-compatibility -DskipTests verify`
  passed.

---

## Priority 5 — Auth-Aware Mock Helper Assertions

### [x] 2.3 Add auth-aware mock helper assertions
- [x] Add a mock assertion for auth header presence after filters run.
- [x] Add a mock assertion for auth header absence after filters run.
- [x] Ensure assertion failure messages redact sensitive header values.
- [x] Add a compact 401 invalidation simulation path.
- [x] Verify one invalidation retry records both outbound attempts.
- [x] Keep helpers independent from a concrete OAuth2 server implementation.
- [x] Document auth header assertions in test helper docs.
- [x] Document 401 invalidation test usage.
- [x] Preserve existing mock helper source compatibility.
- [x] Run `reactive-http-client-test` module tests.
- [x] Run starter auth behavior tests.

Evidence:

- `MockReactiveHttpClient.Builder.withAuthProvider(...)` installs the production `OutboundAuthFilter`, so recorded exchanges contain final auth headers after filter execution.
- Auth-aware mock calls now serialize JSON before filters run and accept the application `ObjectMapper`; regression coverage proves custom naming rules affect both `AuthRequest` bytes and the recorded request body.
- `hasAuthorizationHeader()` and `doesNotHaveAuthorizationHeader()` never include captured credentials in failure output; unexpected values are shown only as `[REDACTED]`.
- `unauthorizedOnceThen(...)` serves one HTTP 401 before delegating to a supplied handler. Tests verify one invalidation, two auth resolutions, two recorded attempts, and statuses `401` then `200` with an in-memory `InvalidatableAuthProvider`.
- Updated `docs/14-test-helpers.md` with Authorization assertions and a complete 401 invalidation example independent of an OAuth2 token server.
- `mvn -pl reactive-http-client-test test` passed with 28 tests, 0 failures, 0 errors, and 0 skipped.
- Starter auth behavior tests passed with 52 tests, 0 failures, 0 errors, and 0 skipped.
- `mvn -pl reactive-http-client-test -Papi-compatibility -DskipTests verify` passed.
- Documentation release artifact tests passed with 9 tests, 0 failures, 0 errors, and 0 skipped.

---

## Priority 6 — Inherited Generic Endpoint Type Resolution

### [x] 3.1 Resolve inherited generic endpoint response types
- [x] Add a regression test for `BusApiOperators extends ApiOperators<BusResponse>`.
- [x] Add a regression test for `TrainApiOperators extends ApiOperators<TrainResponse>`.
- [x] Verify inherited `Mono<T>` methods decode using the concrete child binding.
- [x] Verify inherited `Flux<T>` methods decode using the concrete child binding.
- [x] Verify inherited `Mono<ResponseEntity<T>>` methods decode using the concrete child binding.
- [x] Verify nested parameterized bodies from inherited methods keep their concrete type arguments.
- [x] Resolve request-body generic parameter types with the concrete child binding when auth serialization or repeatability checks need the body type.
- [x] Keep shared `MethodMetadataCache` behavior safe for two child clients binding the same parent method differently.
- [x] Verify inherited endpoint validation still reports the concrete `@ReactiveHttpClient` child name.
- [x] Verify diagnostics, contract snapshots, and startup method policy output describe inherited generic endpoints consistently.
- [x] Document the supported generic shared-contract pattern in annotation docs.
- [x] Document that each child interface must bind the correct concrete type, e.g. train clients must extend `ApiOperators<TrainResponse>`.
- [x] Run inherited endpoint, mock helper, and contract snapshot tests.

Evidence:

- Added concrete-client request-plan resolution for inherited generic methods. Runtime proxy construction now passes the concrete `@ReactiveHttpClient` interface into `ReactiveClientInvocationHandler`, and mock clients do the same.
- `RequestPlan.from(meta, concreteClientInterface)` resolves inherited `Mono<T>`, `Flux<T>`, `Mono<ResponseEntity<T>>`, nested parameterized response bodies, and generic `@Body T` types without mutating shared `MethodMetadataCache` entries.
- Added starter regression coverage proving bus and train clients sharing `ApiOperators<T extends BaseResponse>` decode to `BusResponse` and `TrainResponse`, while the shared metadata entry remains reused safely.
- Added mock-helper regression coverage proving `MockReactiveHttpClient` decodes inherited generic child clients with the concrete response type.
- Updated annotation and test-helper docs with the supported generic shared-contract pattern and the requirement that each child bind the correct concrete DTO type.
- `mvn -pl reactive-http-client-starter -Dtest=MethodMetadataValidationTest,ReactiveClientInvocationHandlerBehaviorTest test` passed with 44 tests, 0 failures, 0 errors, and 0 skipped.
- `mvn -pl reactive-http-client-test -am test` passed with 652 tests, 0 failures, 0 errors, and 0 skipped across the root, starter, and test modules.

---

## Priority 7 — Redirect-Following Contract Coverage

### [x] 3.2 Expand redirect-following contract coverage
- [x] Add default visible-3xx coverage for `301`.
- [x] Add default visible-3xx coverage for `302`.
- [x] Add default visible-3xx coverage for `303`.
- [x] Add default visible-3xx coverage for `307`.
- [x] Add default visible-3xx coverage for `308`.
- [x] Add opt-in follow-redirects coverage for `301`.
- [x] Add opt-in follow-redirects coverage for `302`.
- [x] Add opt-in follow-redirects coverage for `303`.
- [x] Add opt-in follow-redirects coverage for `307`.
- [x] Add opt-in follow-redirects coverage for `308`.
- [x] Verify default behavior still returns visible redirects to proxy callers.
- [x] Verify safe redirect cases work when `follow-redirects=true`.
- [x] Verify docs do not promise sensitive headers across cross-authority
      redirects.
- [x] Verify observer final request fields match the documented redirect
      semantics.
- [x] Verify exchange-log final request fields match the documented redirect
      semantics.
- [x] Document method and body replay limits for POST, PATCH, and streaming
      uploads.
- [x] Run redirect and error-handling tests.

Evidence:

- Extended `RedirectHandlingContractTest` so default `follow-redirects=false`
  behavior is covered for `301`, `302`, `303`, `307`, and `308`; each remains a
  visible `ResponseEntity` redirect with the `Location` header available to the
  proxy caller.
- Existing opt-in coverage continues to verify `follow-redirects=true` for
  `301`, `302`, `303`, `307`, and `308` GET calls, final 4xx/5xx decoding after
  a redirect, excessive-chain fallback to a visible 3xx, repeatable POST body
  replay for `301`, `302`, `307`, and `308`, and `303` switching POST to a
  bodiless GET.
- Added real-transport observer and exchange-log coverage proving final request
  URL/server fields describe the original declarative `/start` request after a
  followed redirect, matching the documented diagnostics contract.
- Existing cross-authority redirect coverage proves `Authorization`, `Cookie`,
  and `Proxy-Authorization` are not forwarded by the delegated Reactor Netty
  redirect policy.
- Updated redirect docs to clarify that automatic redirect body replay is only
  safe when the body can be sent again, with explicit caution for `POST`,
  `PATCH`, and streaming uploads.
- `mvn -pl reactive-http-client-starter -Dtest=RedirectHandlingContractTest,DefaultErrorDecoderTest,ProblemDetailErrorResponseMapperTest test`
  passed with 50 tests, 0 failures, 0 errors, and 0 skipped.

---

## Priority 8 — Streaming Response Ownership Re-Audit

### [x] 3.3 Re-audit streaming response ownership
- [x] Re-run real `WebClient` ownership coverage for `Flux<DataBuffer>`.
- [x] Re-run real `WebClient` ownership coverage for
      `Mono<ResponseEntity<Flux<DataBuffer>>>`.
- [x] Add cancellation coverage for starter-owned discarded buffers.
- [x] Add cancellation coverage for subscriber-owned emitted buffers.
- [x] Verify streaming bodies remain consumable after envelope emission.
- [x] Verify diagnostics complete at the documented envelope boundary.
- [x] Verify lifecycle hooks do not imply inner stream consumption.
- [x] Verify observer events do not imply inner stream consumption.
- [x] Verify exchange logs do not imply inner stream consumption.
- [x] Avoid body buffering for streaming diagnostics.
- [x] Update streaming docs if any wording is ambiguous.
- [x] Run streaming response tests.
- [x] Run timeout tests that cover streaming behavior.

Evidence:

- Extended `StreamingResponseTest` with real Reactor Netty direct
  `Flux<DataBuffer>` coverage over the configured codec limit, complementing
  the existing real `Mono<ResponseEntity<Flux<DataBuffer>>>` envelope test.
- Added consumer-ownership assertions for direct and envelope streaming paths:
  emitted pooled buffers remain allocated until the subscriber releases them,
  while existing cancellation tests continue to prove discarded buffers are
  released by the starter.
- Added envelope-bound diagnostics coverage proving lifecycle success, observer
  events, and exchange logs are emitted when the response envelope is available,
  before the inner `Flux<DataBuffer>` is subscribed or consumed. Consuming the
  inner body does not emit additional terminal diagnostics.
- Re-ran subscription-local reporting coverage for streaming envelopes, which
  verifies concurrent streaming subscriptions keep independent terminal state.
- Reviewed `docs/11-streaming.md` and `docs/21-diagnostic-contexts.md`; no
  wording change was needed because they already document consumer-owned
  `DataBuffer` chunks and envelope-bound diagnostics for
  `Mono<ResponseEntity<Flux<DataBuffer>>>`.
- `mvn -pl reactive-http-client-starter -Dtest=StreamingResponseTest test`
  passed with 12 tests, 0 failures, 0 errors, and 0 skipped.
- `mvn -pl reactive-http-client-starter -Dtest=ApiLevelTimeoutReadTimeoutPrecedenceTest,SubscriptionLocalReportingStateTest test`
  passed with 11 tests, 0 failures, 0 errors, and 0 skipped.

---

## Priority 9 — Bodiless and Unexpected-Body Contracts

### [x] 3.4 Clarify bodiless and unexpected-body contracts
- [x] Audit `Mono<Void>` bodiless handling with real `ClientResponse` paths.
- [x] Audit `Mono<ResponseEntity<Void>>` bodiless handling with real
      `ClientResponse` paths.
- [x] Audit `HEAD` bodiless handling.
- [x] Audit `OPTIONS` bodiless handling.
- [x] Verify empty success bodies complete normally.
- [x] Verify unexpected successful bodies are drained or released as documented.
- [x] Verify pooled connections remain reusable when the transport permits it.
- [x] Verify error body capture caps remain unchanged.
- [x] Verify error body truncation metadata remains unchanged.
- [x] Document draining for connection reuse versus exposing response bodies.
- [x] Add tests for empty bodiless responses.
- [x] Add tests for unexpected-body bodiless responses.
- [x] Run response entity and bodiless response tests.

Evidence:

- Extended `ResponseEntitySupportTest` with explicit empty success-body coverage
  for `Mono<Void>` and `Mono<ResponseEntity<Void>>`; both complete normally and
  `ResponseEntity<Void>` still exposes status and headers.
- Added real-transport `@HEAD` and `@OPTIONS` bodiless coverage. The tests
  verify the proxy sends the declared method and that `Mono<Void>` /
  `Mono<ResponseEntity<Void>>` complete without body decoding.
- Added an OPTIONS `ResponseEntity<Void>` unexpected-body connection-reuse test,
  complementing the existing `Mono<Void>` and `ResponseEntity<Void>` pooled
  connection reuse tests for unexpected successful bodies.
- Existing mock-level assertions continue to verify `Mono<Void>` uses
  `releaseBody()` instead of `bodyToMono(Void.class)`, and
  `Mono<ResponseEntity<Void>>` delegates to `toBodilessEntity()`.
- Reviewed `docs/02-annotations.md` and `docs/14-test-helpers.md`; no wording
  change was needed because they already document draining unexpected content
  for successful bodiless calls and test-helper usage for unexpected bodies.
- Error body cap and truncation behavior were re-verified through
  `DefaultErrorDecoderTest` and `ProblemDetailErrorResponseMapperTest`; no
  production error-decoder changes were made.
- `mvn -pl reactive-http-client-starter -Dtest=ResponseEntitySupportTest test`
  passed with 15 tests, 0 failures, 0 errors, and 0 skipped.
- `mvn -pl reactive-http-client-starter -Dtest=DefaultErrorDecoderTest,ProblemDetailErrorResponseMapperTest,ReactiveClientInvocationHandlerBehaviorTest test`
  passed with 51 tests, 0 failures, 0 errors, and 0 skipped.

---

## Priority 10 — Configuration Metadata Drift Checks

### [x] 4.2 Tighten configuration metadata drift checks
- [x] Verify starter metadata group `sourceType` values resolve on starter tests.
- [x] Verify OTel metadata group `sourceType` values resolve on OTel tests.
- [x] Keep nested group metadata pointed at declaring configuration classes.
- [x] Validate scalar `.properties` examples against property metadata only.
- [x] Validate scalar YAML leaves against property metadata only.
- [x] Reject scalar assignments that match only metadata groups.
- [x] Avoid masking malformed API-map examples.
- [x] Preserve block-list YAML validation for list-valued properties.
- [x] Verify every documented property has metadata.
- [x] Verify every metadata property appears in generated configuration docs.
- [x] Require descriptions for new metadata properties.
- [x] Require defaults where defaults are meaningful.
- [x] Run starter configuration metadata tests.
- [x] Run OTel configuration metadata tests.
- [x] Regenerate `docs/configuration-properties.md` if metadata changes.

Evidence:

- Added starter metadata drift checks for group `sourceType` resolution and source-method return-type alignment, so nested groups stay pointed at the declaring configuration class.
- Added an explicit guard that every metadata property from starter and OTel metadata appears in `docs/configuration-properties.md`; the existing generated-reference equality test remains the stronger full-file drift check.
- Existing example extraction tests continue to validate scalar `.properties` entries and YAML leaves against property metadata only, reject scalar group assignments, preserve malformed API-map leaves, and include block-list YAML properties.
- Existing metadata tests continue to require descriptions and high-value defaults, while OTel metadata source types are validated on the OTel module classpath. No metadata files changed, so `docs/configuration-properties.md` did not need regeneration.
- `mvn -pl reactive-http-client-starter -Dtest=ReactiveHttpClientConfigurationMetadataTest,DocumentationReleaseArtifactTest test` passed with 22 tests, 0 failures, 0 errors, and 0 skipped.
- `mvn -pl reactive-http-client-otel -Dtest=OpenTelemetryConfigurationMetadataTest test` passed with 4 tests, 0 failures, 0 errors, and 0 skipped.

---

## Priority 11 — Startup Configuration Summary Logging

### [x] 1.3 Add startup configuration summary logging
- [x] Decide whether startup summary is opt-in property based or DEBUG-only.
- [x] Add sanitized one-line summary per client.
- [x] Include client name and interface.
- [x] Include endpoint count and inherited endpoint count.
- [x] Include auth mode.
- [x] Include timeout source summary.
- [x] Include resilience enabled/disabled summary.
- [x] Include redirect-following flag.
- [x] Include observability enabled/disabled summary.
- [x] Reuse diagnostics snapshot sanitization rules.
- [x] Verify summary output agrees with `ReactiveHttpClientDiagnosticsProvider`.
- [x] Verify default configuration does not add INFO log noise.
- [x] Add logging tests for enabled summary output.
- [x] Add logging tests for sanitized output.
- [x] Document startup summary usage.

Evidence:

- Added a DEBUG-only `Reactive HTTP client [...] startup summary` line emitted during proxy construction after resilience operator availability is resolved.
- The summary line uses the same `ReactiveHttpClientDiagnosticsProvider.ClientSummary` construction path as the runtime diagnostics provider for client name, interface, base URL source, timeout, resilience, auth mode, redirect policy, endpoint count, and inherited endpoint count.
- The line adds observability state from `ReactiveHttpClientProperties` and omits concrete base URL values, header values, auth-provider bean names, proxy credentials, request bodies, and response bodies.
- Added logging coverage that compares summary fields against `ReactiveHttpClientDiagnosticsProvider`, verifies sanitized output, and verifies INFO logs do not contain the startup summary.
- Updated `docs/21-diagnostic-contexts.md` with the DEBUG startup summary contract and sanitization boundary.
- `mvn -pl reactive-http-client-starter -Dtest=ReactiveHttpClientFactoryBeanDiagnosticsTest,ReactiveHttpClientDiagnosticsProviderTest test` passed with 51 tests, 0 failures, 0 errors, and 0 skipped.
- `mvn -pl reactive-http-client-starter -Dtest=DocumentationReleaseArtifactTest test` passed with 9 tests, 0 failures, 0 errors, and 0 skipped.

---

## Priority 12 — Release Readiness Snapshot

### [x] 4.3 Add release readiness snapshot for docs, metadata, and benchmarks
- [x] Extend release evidence manifest with a top-level readiness summary.
- [x] Include project version.
- [x] Include API compatibility baseline version.
- [x] Include promoted benchmark report path.
- [x] Include generated configuration reference status.
- [x] Include Markdown link validation status.
- [x] Include pending manual release commands.
- [x] Distinguish generated test evidence from manual release evidence.
- [x] Keep manual benchmark commands pending until explicitly run.
- [x] Keep manual compatibility commands pending until explicitly run.
- [x] Surface missing promoted benchmark reports.
- [x] Surface stale version links.
- [x] Keep release evidence under `target/release-evidence/`.
- [x] Verify target-only evidence is not documented as source-controlled output.
- [x] Run `DocumentationReleaseArtifactTest`.

Evidence:

- Extended `reactive-http-client-release-evidence.json` with a top-level `readiness` object containing project version, API compatibility baseline version, promoted benchmark report path/status, generated configuration reference status, Markdown link validation status, stale benchmark-report link status, and target-only evidence metadata.
- The readiness summary separates generated test evidence from manual release evidence. Generated docs/link checks can report `pass`, while benchmark, API compatibility, fixture, and diff-check commands remain `pending` until a maintainer runs them.
- Manual benchmark and compatibility readiness sections list their pending commands separately; pending benchmark data is not treated as performance-claim evidence.
- Missing promoted benchmark reports and stale benchmark-report links are surfaced with explicit `missing`/`fail` statuses when detected.
- Updated `docs/20-native-release-compatibility.md` to describe the readiness summary and reinforce that `target/release-evidence/` output is generated target-only evidence, not source-controlled output.
- `mvn -pl reactive-http-client-starter -Dtest=DocumentationReleaseArtifactTest test` passed with 9 tests, 0 failures, 0 errors, and 0 skipped.

---

## Priority 13 — AWS SigV4 and Raw-Body Signing Contracts

### [x] 2.2 Audit AWS SigV4 and raw-body signing contracts
- [x] Add SigV4 contract tests for scalar JSON bodies.
- [x] Add SigV4 contract tests for byte array bodies.
- [x] Add SigV4 contract tests for string bodies.
- [x] Add SigV4 contract tests for empty bodies.
- [x] Add SigV4 contract tests for publisher bodies.
- [x] Add SigV4 contract tests for streaming upload bodies.
- [x] Verify signed content hashes match bytes on the wire for supported shapes.
- [x] Verify publisher uploads do not sign an empty payload when the request body
      is non-empty.
- [x] Document unsupported non-repeatable signing behavior.
- [x] Avoid buffering large or streaming bodies only for signing convenience.
- [x] Preserve auth provider extension source compatibility.
- [x] Run auth and request-body tests.

Evidence:

- Added SigV4 provider coverage for `String` request bodies alongside existing
  empty, raw `byte[]`, and publisher rejection coverage.
- Added invocation-handler contract coverage proving scalar JSON, `String`,
  `byte[]`, and empty requests produce `x-amz-content-sha256` values matching
  the bytes materialized from the final outbound `ClientRequest`; non-UTF-8
  `String` signing now uses the declared `Content-Type` charset.
- Added streaming-upload and multipart coverage proving non-repeatable bodies are
  rejected by built-in SigV4 before the request is sent; publisher bodies are
  not subscribed and multipart uploads no longer sign an empty payload.
- Updated `docs/06-auth-providers.md` with the supported body-shape contract and
  explicit unsupported `Publisher`, streaming, and multipart behavior.
- `mvn -pl reactive-http-client-starter -Dtest=AwsSigV4AuthProviderTest,ReactiveClientInvocationHandlerBehaviorTest,MultipartRequestTest test`
  passed with 43 tests, 0 failures, 0 errors, and 0 skipped.
- `mvn -pl reactive-http-client-starter -Dtest=DocumentationReleaseArtifactTest test`
  passed with 9 tests, 0 failures, 0 errors, and 0 skipped.

---

## Priority 14 — Post-V14 Benchmark Audits

### [x] 5.1 Re-run default and optional feature benchmark audits after V14
- [x] Run current-vs-published benchmark pair for default success path.
- [x] Run current-vs-published benchmark pair for optional diagnostics.
- [x] Run current-vs-published benchmark pair for error mapping.
- [x] Add and run auth-enabled benchmark rows only if V15 auth work needs them.
- [x] Keep current and published-baseline reports in distinct paths.
- [x] Apply V13 review triggers as review-only signals.
- [x] Rerun any review-trigger movement on the same machine before acting.
- [x] Record before/after evidence for every optimization.
- [x] Prefer removing redundant work over adding caches.
- [x] Verify optional feature rows are not compared to baselines without
      equivalent work.
- [x] Update performance docs only with scenario-specific claims.
- [x] Run benchmark smoke after benchmark code changes.
- [x] Run release-quality benchmark when release notes include performance
      wording.

Evidence:

- Published `2.9.0` baseline artifacts were resolved before the benchmark pair:
  `reactive-http-client-starter`, `reactive-http-client-test`, and
  `reactive-http-client-otel`.
- The benchmark smoke command was run manually before the release pair. Its
  smoke artifact was intentionally not retained after the subsequent clean
  published-baseline run.
- Published-baseline release benchmark completed and wrote
  `reactive-http-client-benchmarks/target/benchmark-reports/published-starter-2.9.0/release-jmh.md`
  and `release-jmh.json` with `starterVersion=2.9.0` and
  `benchmarkCommit=2.9.0`.
- Current-workspace release benchmark completed and wrote
  `reactive-http-client-benchmarks/target/benchmark-reports/release-jmh.md`
  and `release-jmh.json` with `starterVersion=2.10.0` and
  `benchmarkCommit=7b69b4d`.
- The initial comparison command with `-am` failed in the manual run. The
  comparator-only rerun succeeded with:
  `mvn -Pbenchmarks,benchmark-compare -pl reactive-http-client-benchmarks verify -Dbenchmark.compare.current=reactive-http-client-benchmarks/target/benchmark-reports/release-jmh.json -Dbenchmark.compare.baseline=reactive-http-client-benchmarks/target/benchmark-reports/published-starter-2.9.0/release-jmh.json -Dbenchmark.compare.output=reactive-http-client-benchmarks/target/benchmark-reports/v15-current-vs-published-2.9.0.md`.
- Current and published-baseline reports are kept in distinct paths. The
  comparison report was written to
  `reactive-http-client-benchmarks/target/benchmark-reports/v15-current-vs-published-2.9.0.md`.
- Current candidate comparison summary: `Get No Body` was `106.483 us/op`,
  `Post Json` was `179.213 us/op`, `Response Entity` was `139.175 us/op`,
  `Problem Detail Small Body` was `131.914 us/op`, metadata-only exchange
  logging was `120.37 us/op`, Micrometer observer was `147.176 us/op`, and
  retry wrapper was `140.407 us/op`.
- The comparison report has 73 informational `review` rows. They were treated as
  review signals, not hard gates; no optimization was made from benchmark data in
  this priority because movement should be rerun on the same machine before
  acting.
- Optional feature rows remain starter-only/feature-specific evidence. Rows
  without a published-baseline counterpart, such as diagnostics-disabled and
  runtime diagnostics provider summaries, are reported as `missing baseline` and
  were not compared to raw WebClient or Spring HTTP Interface.
- No auth-enabled benchmark rows were added for Priority 14 because the V15 auth
  work changed signing correctness/unsupported-body contracts, not a measured
  default success-path auth feature.
- No source performance documentation was changed; the generated benchmark
  reports remain target-only evidence until a release note or promoted report
  needs scenario-specific claims.

---

## Priority 15 — Observer and Lifecycle Overhead Audit

### [x] 5.2 Audit observer and lifecycle overhead with multiple observers/hooks
- [x] Add optional diagnostics benchmark row for one observer.
- [x] Add optional diagnostics benchmark row for multiple observers.
- [x] Add optional diagnostics benchmark row for one lifecycle hook.
- [x] Add optional diagnostics benchmark row for multiple ordered lifecycle
      hooks.
- [x] Inspect per-call allocation in observer lookup.
- [x] Inspect per-call allocation in lifecycle hook lookup.
- [x] Inspect composite observer construction overhead.
- [x] Reuse immutable observer snapshots only when Spring semantics allow it.
- [x] Reuse immutable lifecycle hook snapshots only when Spring semantics allow
      it.
- [x] Preserve observer ordering.
- [x] Preserve lifecycle hook ordering.
- [x] Preserve observer failure isolation.
- [x] Add tests for dynamic bean behavior if caching is introduced.
- [x] Run observer and lifecycle tests.
- [x] Record benchmark evidence before and after any optimization.

Evidence:

- Added four smokeable JMH rows in `StarterDiagnosticsOverheadBenchmark`:
  `starterFeatureOneObserverGetNoBody`,
  `starterFeatureMultipleObserversGetNoBody`,
  `starterFeatureOneLifecycleHookGetNoBody`, and
  `starterFeatureMultipleLifecycleHooksGetNoBody`.
- Observer lookup still queries `ObjectProvider.orderedStream()` per proxy
  invocation, builds a list when observers are present, and constructs a
  `CompositeHttpClientObserver` only when more than one observer is present.
- Lifecycle lookup still queries `ObjectProvider.orderedStream()` per proxy
  invocation, filters by `supports(clientName)`, and materializes the ordered
  hook list for the call.
- No immutable observer or lifecycle snapshot cache was introduced. The current
  handler contract intentionally re-queries Spring providers per invocation so
  late-registered or scoped beans remain visible, and lifecycle `supports(...)`
  filtering continues to use the current client name. Because no caching was
  introduced, no dynamic-bean caching regression test was needed.
- Existing behavior preserves observer ordering through Spring provider order,
  lifecycle hook ordering through Spring provider order plus `Ordered` hooks, and
  observer failure isolation through `CompositeHttpClientObserver`.
- Smoke benchmark command passed:
  `mvn -Pbenchmarks,benchmark-smoke -pl reactive-http-client-benchmarks -am verify -Dbenchmark.include=.*StarterDiagnosticsOverheadBenchmark.starterFeature.*`.
  The smoke run executed all four new rows and wrote
  `reactive-http-client-benchmarks/target/benchmark-reports/smoke-only-jmh.json`.
- Compile/package verification passed:
  `mvn -q -Pbenchmarks -pl reactive-http-client-benchmarks -am -DskipTests package`.
- Focused observer/lifecycle tests passed with 57 tests, 0 failures, 0 errors,
  and 0 skipped:
  `mvn -pl reactive-http-client-starter -Dtest=ReactiveHttpClientLifecycleHookTest,CompositeHttpClientObserverTest,MicrometerHttpClientObserverTest,ReactiveClientInvocationHandlerObservabilityErrorCategoryTest test`.
- No before/after optimization numbers were recorded because this priority added
  audit coverage only; runtime optimization was deliberately deferred until a
  future benchmark-backed change can preserve Spring dynamic lookup semantics.
