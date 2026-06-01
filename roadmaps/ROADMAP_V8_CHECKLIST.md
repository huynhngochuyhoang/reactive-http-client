# Roadmap V8 Execution Checklist

> Companion to [`ROADMAP_V8.md`](ROADMAP_V8.md). This file tracks execution;
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

### [ ] 1.4 `@ApiRef` fallback for observability API names
- [ ] Resolve observability API names with precedence:
  `@ApiName` value > `@ApiRef` value > Java method name.
- [ ] Keep explicit `@ApiName` values authoritative for compatibility.
- [ ] Feed the resolved name into `HttpClientObserverEvent`.
- [ ] Verify Micrometer `api.name` uses the resolved name.
- [ ] Verify OpenTelemetry `rhttp.api.name` and span names use the resolved name.
- [ ] Keep lifecycle context API names aligned with observers.
- [ ] Test a method with only `@ApiRef`.
- [ ] Test a method with both `@ApiName` and `@ApiRef`.
- [ ] Test a method with neither annotation.
- [ ] Document the naming rule without implying a request-routing change.

---

## Priority 5 — Error-Body Truncation Metadata and Drain Behavior

### [ ] 1.3 Error-body truncation metadata for mappers
- [ ] Expose whether mapper input was truncated.
- [ ] Expose the retained body byte count.
- [ ] Preserve source compatibility for existing `ErrorResponseContext`
  callers.
- [ ] Keep the default 4 KiB capture cap unchanged.
- [ ] Keep the `application/problem+json` 64 KiB capture cap unchanged.
- [ ] Test complete and truncated default mapper bodies.
- [ ] Test complete and truncated Problem Detail mapper bodies.
- [ ] Verify generic exceptions still expose at most their documented 4 KiB
  body text.
- [ ] Document that mapper input remains bounded and may be incomplete.

### [ ] 3.3 Retained error-body drain and cancellation behavior
- [ ] Audit `DataBuffer` release during bounded error-body capture.
- [ ] Test oversized chunked error bodies.
- [ ] Test mapper fallback after truncated input.
- [ ] Test cancellation during error-body capture.
- [ ] Verify every consumed `DataBuffer` is released on success, fallback, and
  cancellation paths.
- [ ] Verify retained memory stays bounded.
- [ ] Document draining behavior required for connection reuse.

---

## Priority 6 — Diagnostic Context Contract Alignment

### [ ] 2.1 Diagnostic context contract alignment
- [ ] Audit fields exposed by `HttpExchangeLogContext`.
- [ ] Audit fields exposed by `HttpClientObserverEvent`.
- [ ] Audit fields exposed by `ReactiveHttpClientLifecycleContext`.
- [ ] Audit fields exposed by `ErrorResponseContext`.
- [ ] Add missing fields only where a concrete extension-point need exists.
- [ ] Document one capability matrix covering final request URL, final request
  headers, response status, response headers, error body, duration, and
  subscription-attempt count.
- [ ] Verify docs do not promise response headers in lifecycle hooks or
  observers when unavailable.
- [ ] Document raw-versus-redacted header behavior for custom exchange loggers
  and custom observers.
- [ ] Add focused contract tests for each documented context field.

---

## Priority 7 — Redirect Handling Contract

### [ ] 3.2 Redirect handling contract
- [ ] Audit visible 3xx handling through the real starter proxy path.
- [ ] Audit followed redirects when the configured transport supports them.
- [ ] Ensure 3xx responses are never mapped to `RemoteServiceException` solely
  because they are non-4xx.
- [ ] Keep `ErrorResponseMapper` invocation restricted to actual error statuses.
- [ ] Verify observer outcome is `REDIRECTION` when a 3xx response is visible.
- [ ] Test one visible redirect response.
- [ ] Test one followed redirect path if supported by transport configuration.
- [ ] Update error-handling docs to describe 4xx/5xx decoding precisely.

---

## Priority 8 — Compatibility Evidence and Release Readiness

### [ ] 2.2 Public API compatibility automation
- [ ] Select and document the released API compatibility baseline.
- [ ] Add an API compatibility report for published artifacts.
- [ ] Fail CI for unreviewed binary-incompatible public API changes.
- [ ] Keep internal implementation classes and test fixtures out of the public
  API check.
- [ ] Test that accidental public constructor removal fails the check.
- [ ] Verify additive public APIs pass without manual suppression.
- [ ] Document how to review an intentional breaking change for a future major
  release.

### [ ] 2.3 Native-image and Spring Boot compatibility evidence
- [ ] Keep the fast Spring AOT smoke test in normal CI.
- [ ] Add an opt-in or scheduled minimal native-image build for the core starter
  path.
- [ ] Define the supported Spring Boot baseline policy.
- [ ] Expand release-smoke CI to each documented Spring Boot baseline.
- [ ] Verify inherited client methods remain covered by AOT reflection hints.
- [ ] Verify optional integrations can be absent without breaking AOT
  processing.
- [ ] Document tested core support separately from optional integration
  ownership.

### [ ] Release Readiness
- [ ] `CHANGELOG.md` has V8 entries grouped under Added/Changed/Fixed/Docs.
- [ ] README stays short and links to detailed docs.
- [ ] New public APIs have focused tests and concise docs.
- [ ] New test-helper APIs are covered by examples.
- [ ] Configuration metadata is updated if new properties are added.
- [ ] Subscription-attempt wording does not imply guaranteed HTTP network
  sends.
- [ ] Exchange-log, observer, lifecycle, and mapper docs match their actual
  runtime fields.
- [ ] Error-body docs do not imply unbounded capture or complete truncated
  input.
- [ ] Redirect docs describe visible and followed response behavior precisely.
- [ ] API compatibility checks pass against the selected released baseline.
- [ ] `mvn test` passes.
- [ ] `mvn -Prelease-smoke test` passes before release.
- [ ] Breaking behavior, if any, is explicitly called out before release.
