# Roadmap V8 Execution Checklist

> Companion to [`ROADMAP.md`](ROADMAP.md). This file tracks execution;
> keep rationale and design discussion in the roadmap.

---

## Priority 1 — Subscription-Local Terminal Reporting State

### [x] 3.1 Subscription-local terminal reporting state
- [x] Audit mutable invocation state in `ReactiveClientInvocationHandler`.
- [x] Move request start time into subscription-local state.
- [x] Move observed request URL into subscription-local state.
- [x] Move response status and response headers into subscription-local state.
- [x] Move terminal error into subscription-local state.
- [x] Keep generated idempotency keys, prepared headers, attempt count, and
  final request observation isolated per subscription.
- [x] Verify retry attempts remain grouped inside one logical call.
- [x] Test concurrent subscriptions with different `Mono<T>` statuses and
  headers.
- [x] Test concurrent subscriptions with one cancellation and one completion.
- [x] Test concurrent streaming `Flux<T>` subscriptions.
- [x] Verify lifecycle hooks, observers, and exchange logs report the matching
  subscriber state.

---

## Priority 2 — Exchange-Log Subscription-Attempt Count

### [x] 1.1 Exchange-log subscription-attempt count
- [x] Add a precisely named subscription-attempt count to
  `HttpExchangeLogContext`.
- [x] Feed the final logical-call count into custom exchange loggers.
- [x] Include the count in `DefaultHttpExchangeLogger` output.
- [x] Preserve source compatibility for existing convenience constructors by
  defaulting the count to `1`.
- [x] Test a first-try call with count `1`.
- [x] Test a retried call with one terminal exchange-log record and the final
  count.
- [x] Test pre-network serialization failure wording and behavior.
- [x] Document that the value counts subscriptions, not guaranteed network
  sends.
- [x] Add a custom logger example that records the count.

---

## Priority 3 — Observer and Lifecycle Test Helpers

### [x] 1.2 Observer and lifecycle support in test helpers
- [x] Add `MockReactiveHttpClient` builder support for a custom
  `HttpClientObserver`.
- [x] Add builder support for one or more `ReactiveHttpClientLifecycleHook`
  instances.
- [x] Register supplied hooks using starter ordering semantics.
- [x] Keep existing helper behavior unchanged when no observer or hook is
  supplied.
- [x] Keep the helper independent of Micrometer and OpenTelemetry by default.
- [x] Test one terminal observer event on retry success.
- [x] Test one terminal observer event on retry exhaustion.
- [x] Test lifecycle callback order for first subscription and retry
  subscriptions.
- [x] Document compact observer and lifecycle helper examples.

---

## Priority 4 — `@ApiRef` Observability Name Fallback

### [x] 1.4 `@ApiRef` fallback for observability API names
- [x] Resolve observability API names with precedence:
  `@ApiName` value > `@ApiRef` value > Java method name.
- [x] Keep explicit `@ApiName` values authoritative for compatibility.
- [x] Feed the resolved name into `HttpClientObserverEvent`.
- [x] Verify Micrometer `api.name` uses the resolved name.
- [x] Verify OpenTelemetry `rhttp.api.name` and span names use the resolved name.
- [x] Keep lifecycle context API names aligned with observers.
- [x] Test a method with only `@ApiRef`.
- [x] Test a method with both `@ApiName` and `@ApiRef`.
- [x] Test a method with neither annotation.
- [x] Document the naming rule without implying a request-routing change.

---

## Priority 5 — Error-Body Truncation Metadata and Drain Behavior

### [x] 1.3 Error-body truncation metadata for mappers
- [x] Expose whether mapper input was truncated.
- [x] Expose the retained body byte count.
- [x] Preserve source compatibility for existing `ErrorResponseContext`
  callers.
- [x] Keep the default 4 KiB capture cap unchanged.
- [x] Keep the `application/problem+json` 64 KiB capture cap unchanged.
- [x] Test complete and truncated default mapper bodies.
- [x] Test complete and truncated Problem Detail mapper bodies.
- [x] Verify generic exceptions still expose at most their documented 4 KiB
  body text.
- [x] Document that mapper input remains bounded and may be incomplete.

### [x] 3.3 Retained error-body drain and cancellation behavior
- [x] Audit `DataBuffer` release during bounded error-body capture.
- [x] Test oversized chunked error bodies.
- [x] Test mapper fallback after truncated input.
- [x] Test cancellation during error-body capture.
- [x] Verify every consumed `DataBuffer` is released on success, fallback, and
  cancellation paths.
- [x] Verify retained memory stays bounded.
- [x] Document draining behavior required for connection reuse.

---

## Priority 6 — Diagnostic Context Contract Alignment

### [x] 2.1 Diagnostic context contract alignment
- [x] Audit fields exposed by `HttpExchangeLogContext`.
- [x] Audit fields exposed by `HttpClientObserverEvent`.
- [x] Audit fields exposed by `ReactiveHttpClientLifecycleContext`.
- [x] Audit fields exposed by `ErrorResponseContext`.
- [x] Add missing fields only where a concrete extension-point need exists.
- [x] Document one capability matrix covering final request URL, final request
  headers, response status, response headers, error body, duration, and
  subscription-attempt count.
- [x] Verify docs do not promise response headers in lifecycle hooks or
  observers when unavailable.
- [x] Document raw-versus-redacted header behavior for custom exchange loggers
  and custom observers.
- [x] Add focused contract tests for each documented context field.

---

## Priority 7 — Redirect Handling Contract

### [x] 3.2 Redirect handling contract
- [x] Audit visible 3xx handling through the real starter proxy path.
- [x] Audit followed redirects when the configured transport supports them.
- [x] Ensure 3xx responses are never mapped to `RemoteServiceException` solely
  because they are non-4xx.
- [x] Keep `ErrorResponseMapper` invocation restricted to actual error statuses.
- [x] Verify observer outcome is `REDIRECTION` when a 3xx response is visible.
- [x] Test one visible redirect response.
- [x] Test one followed redirect path if supported by transport configuration.
- [x] Update error-handling docs to describe 4xx/5xx decoding precisely.

---

## Priority 8 — Compatibility Evidence and Release Readiness

### [x] 2.2 Public API compatibility automation
- [x] Select and document the released API compatibility baseline.
- [x] Add an API compatibility report for published artifacts.
- [x] Fail CI for unreviewed binary-incompatible public API changes.
- [x] Keep internal implementation classes and test fixtures out of the public
  API check.
- [x] Test that accidental public constructor removal fails the check.
- [x] Verify additive public APIs pass without manual suppression.
- [x] Document how to review an intentional breaking change for a future major
  release.

### [x] 2.3 Native-image and Spring Boot compatibility evidence
- [x] Keep the fast Spring AOT smoke test in normal CI.
- [x] Add an opt-in or scheduled minimal native-image build for the core starter
  path.
- [x] Define the supported Spring Boot baseline policy.
- [x] Expand release-smoke CI to each documented Spring Boot baseline.
- [x] Verify inherited client methods remain covered by AOT reflection hints.
- [x] Verify optional integrations can be absent without breaking AOT
  processing.
- [x] Document tested core support separately from optional integration
  ownership.

### [x] Release Readiness
- [x] `CHANGELOG.md` has V8 entries grouped under Added/Changed/Fixed/Docs.
- [x] README stays short and links to detailed docs.
- [x] New public APIs have focused tests and concise docs.
- [x] New test-helper APIs are covered by examples.
- [x] Configuration metadata is updated if new properties are added.
- [x] Subscription-attempt wording does not imply guaranteed HTTP network
  sends.
- [x] Exchange-log, observer, lifecycle, and mapper docs match their actual
  runtime fields.
- [x] Error-body docs do not imply unbounded capture or complete truncated
  input.
- [x] Redirect docs describe visible and followed response behavior precisely.
- [x] API compatibility checks pass against the selected released baseline.
- [x] `mvn test` passes.
- [x] `mvn -Prelease-smoke test` passes before release.
- [x] Breaking behavior, if any, is explicitly called out before release.
