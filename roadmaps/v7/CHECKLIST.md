# Roadmap V7 Execution Checklist

> Companion to [`ROADMAP.md`](ROADMAP.md). This file tracks execution;
> keep rationale and design discussion in the roadmap.

---

## Priority 1 — Retry Safety and Operator Diagnostics

### [x] 1.2 Retry safety policy for unsafe methods
- [x] Add retry-safety classification to method metadata plans.
- [x] Classify safe HTTP methods separately from unsafe methods.
- [x] Detect explicit idempotency signals for unsafe methods.
- [x] Add diagnostics for unsafe retry with client name, method, HTTP method,
  and retry source.
- [x] Preserve compatibility unless a stricter policy is explicitly enabled.
- [x] Ensure retry-safety logic is inactive when retry support is absent.
- [x] Document retry-safety behavior for annotations, `@ApiRef`, and per-client
  resilience settings.

### [x] 2.1 Resilience operator order and diagnostics audit
- [x] Re-audit the actual retry, circuit-breaker, bulkhead, rate-limiter,
  timeout, observer, hook, and decoder order.
- [x] Document the final operator order in one canonical place.
- [x] Add tests for retry success terminal signals.
- [x] Add tests for retry exhaustion terminal signals.
- [x] Add tests for bulkhead rejection and rate-limit rejection terminal
  signals.
- [x] Add tests for timeout and cancellation terminal signals.
- [x] Include active resilience operators in startup diagnostics per client and
  per method where applicable.
- [x] Verify published resilience error categories remain stable.

---

## Priority 2 — Non-Repeatable Body Handling

### [x] 2.2 Non-repeatable body detection and guidance
- [x] Audit request body paths for JSON objects.
- [x] Audit form body paths.
- [x] Audit multipart body paths.
- [x] Audit raw publisher, streaming body, and `DataBuffer` paths.
- [x] Mark obviously repeatable bodies as retry-compatible where metadata is
  available.
- [x] Mark obviously non-repeatable bodies as retry-risky where metadata is
  available.
- [x] Ensure non-repeatable body paths do not retry silently when retry-safety
  policy is enabled.
- [x] Document repeatable, risky, and application-owned body types.
- [x] Verify no large request bodies are buffered to make retry possible.

---

## Priority 3 — Idempotency Key Support and Header Precedence

### [x] 1.1 Idempotency key support for outbound requests
- [x] Add an explicit idempotency-key model for outbound calls.
- [x] Support the default `Idempotency-Key` header.
- [x] Leave per-client header override out because it does not fit existing
  configuration patterns cleanly yet.
- [x] Keep caller-supplied idempotency headers authoritative.
- [x] Avoid implicit key generation for every request.
- [x] Document that the starter does not provide downstream idempotency storage.

### [x] 3.3 Header precedence with generated idempotency keys
- [x] Test precedence against annotation headers.
- [x] Test precedence against method header parameters.
- [x] Test precedence against configured default headers.
- [x] Test precedence against request context or generated keys.
- [x] Test precedence against customizers.
- [x] Verify generated or contextual keys are scoped to one invocation.
- [x] Verify generated keys do not bleed across concurrent requests.
- [x] Keep logging and test-helper behavior aligned with existing header
  redaction policy.

---

## Priority 4 — Retry and Idempotency Test Helpers

### [x] 1.3 Retry and idempotency test-helper assertions
- [x] Add fluent assertions for idempotency header presence.
- [x] Add fluent assertions for idempotency header absence.
- [x] Add fluent assertions for idempotency header value.
- [x] Add helper support to assert exchange attempt counts.
- [x] Add an example with transient downstream failure followed by successful
  retry.
- [x] Document compact retry/idempotency helper usage.

---

## Priority 5 — Final Outbound Request Diagnostics

### [x] 2.4 Final outbound request diagnostics
- [x] Capture final outbound request method, URL, and headers after `WebClient`
  filters mutate the request.
- [x] Feed final request headers into default exchange logging when available.
- [x] Feed final request metadata into observer diagnostics when available.
- [x] Verify headers added by `ReactiveHttpClientCustomizer` filters appear in
  exchange logs when header logging is enabled.
- [x] Verify built-in auth, correlation, and tracing headers use existing
  sensitive-header redaction rules.
- [x] Verify final request diagnostics do not change the outbound request.
- [x] Verify streaming or non-repeatable bodies are not buffered for diagnostics.
- [x] Document declarative headers versus final outbound headers in customizer
  and exchange-logging docs.

---

## Priority 6 — Error Body and Timeout Correctness

### [x] 2.3 Error body capture policy audit
- [x] Document default retained error body size for exceptions.
- [x] Document any larger cap used by structured mappers.
- [x] Test malformed `Content-Type` without losing the response body.
- [x] Test oversized Problem Detail payloads without generic fallback when the
  mapper has enough data.
- [x] Test non-JSON error bodies and mapper fallback.
- [x] Verify sensitive body logging remains controlled by existing presets.

### [x] 3.2 Timeout layering correctness
- [x] Re-test timeout precedence after the request-timeout migration.
- [x] Verify precedence remains `@TimeoutMs` > `@ApiRef` > client request
  timeout.
- [x] Add coverage for timeout during response headers.
- [x] Add coverage for timeout during body decode.
- [x] Add coverage for timeout during streaming response consumption.
- [x] Verify streaming timeout does not buffer the response body.
- [x] Verify timeout failures emit one terminal observer/lifecycle signal.

---

## Priority 7 — Retry Signal Semantics and Release Readiness

### [x] 3.1 Duplicate lifecycle and observer signals under retry
- [x] Audit lifecycle hook invocation points around retries.
- [x] Audit observer invocation points around retries.
- [x] Define per-attempt versus logical-call signals.
- [x] Test retry success attempt count and final success once.
- [x] Test retry exhaustion attempt count and final error once.
- [x] Test cancellation during retry does not emit both cancellation and error
  for the same logical call.
- [x] Test mapper failure after retry.
- [x] Document per-attempt and logical-call signal semantics.

### [x] Release Readiness
- [x] `CHANGELOG.md` has V7 entries grouped under Added/Changed/Fixed/Docs.
- [x] README stays short and links to detailed docs.
- [x] New public APIs have focused tests and concise docs.
- [x] New test-helper APIs are covered by examples.
- [x] Configuration metadata is updated if new properties are added.
- [x] Retry and idempotency behavior is documented without implying automatic
  business idempotency.
- [x] Final outbound request diagnostics are documented without implying body
  buffering or unredacted sensitive-header logging.
- [x] Non-repeatable body behavior is documented without implying large-body
  buffering.
- [x] `mvn test` passes.
- [x] `mvn -Prelease-smoke test` passes before release.
- [x] Breaking behavior, if any, is explicitly called out before release.
