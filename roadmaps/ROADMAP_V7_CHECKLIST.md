# Roadmap V7 Execution Checklist

> Companion to [`ROADMAP_V7.md`](ROADMAP_V7.md). This file tracks execution;
> keep rationale and design discussion in the roadmap.

---

## Priority 1 — Retry Safety and Operator Diagnostics

### [ ] 1.2 Retry safety policy for unsafe methods
- [ ] Add retry-safety classification to method metadata plans.
- [ ] Classify safe HTTP methods separately from unsafe methods.
- [ ] Detect explicit idempotency signals for unsafe methods.
- [ ] Add diagnostics for unsafe retry with client name, method, HTTP method,
  and retry source.
- [ ] Preserve compatibility unless a stricter policy is explicitly enabled.
- [ ] Ensure retry-safety logic is inactive when retry support is absent.
- [ ] Document retry-safety behavior for annotations, `@ApiRef`, and per-client
  resilience settings.

### [ ] 2.1 Resilience operator order and diagnostics audit
- [ ] Re-audit the actual retry, circuit-breaker, bulkhead, rate-limiter,
  timeout, observer, hook, and decoder order.
- [ ] Document the final operator order in one canonical place.
- [ ] Add tests for retry success terminal signals.
- [ ] Add tests for retry exhaustion terminal signals.
- [ ] Add tests for bulkhead rejection and rate-limit rejection terminal
  signals.
- [ ] Add tests for timeout and cancellation terminal signals.
- [ ] Include active resilience operators in startup diagnostics per client and
  per method where applicable.
- [ ] Verify published resilience error categories remain stable.

---

## Priority 2 — Non-Repeatable Body Handling

### [ ] 2.2 Non-repeatable body detection and guidance
- [ ] Audit request body paths for JSON objects.
- [ ] Audit form body paths.
- [ ] Audit multipart body paths.
- [ ] Audit raw publisher, streaming body, and `DataBuffer` paths.
- [ ] Mark obviously repeatable bodies as retry-compatible where metadata is
  available.
- [ ] Mark obviously non-repeatable bodies as retry-risky where metadata is
  available.
- [ ] Ensure non-repeatable body paths do not retry silently when retry-safety
  policy is enabled.
- [ ] Document repeatable, risky, and application-owned body types.
- [ ] Verify no large request bodies are buffered to make retry possible.

---

## Priority 3 — Idempotency Key Support and Header Precedence

### [ ] 1.1 Idempotency key support for outbound requests
- [ ] Add an explicit idempotency-key model for outbound calls.
- [ ] Support the default `Idempotency-Key` header.
- [ ] Allow per-client header override only if it fits existing configuration
  patterns cleanly.
- [ ] Keep caller-supplied idempotency headers authoritative.
- [ ] Avoid implicit key generation for every request.
- [ ] Document that the starter does not provide downstream idempotency storage.

### [ ] 3.3 Header precedence with generated idempotency keys
- [ ] Test precedence against annotation headers.
- [ ] Test precedence against method header parameters.
- [ ] Test precedence against configured default headers.
- [ ] Test precedence against request context or generated keys.
- [ ] Test precedence against customizers.
- [ ] Verify generated or contextual keys are scoped to one invocation.
- [ ] Verify generated keys do not bleed across concurrent requests.
- [ ] Keep logging and test-helper behavior aligned with existing header
  redaction policy.

---

## Priority 4 — Retry and Idempotency Test Helpers

### [ ] 1.3 Retry and idempotency test-helper assertions
- [ ] Add fluent assertions for idempotency header presence.
- [ ] Add fluent assertions for idempotency header absence.
- [ ] Add fluent assertions for idempotency header value.
- [ ] Add helper support to assert exchange attempt counts.
- [ ] Add an example with transient downstream failure followed by successful
  retry.
- [ ] Document compact retry/idempotency helper usage.

---

## Priority 5 — Final Outbound Request Diagnostics

### [ ] 2.4 Final outbound request diagnostics
- [ ] Capture final outbound request method, URL, and headers after `WebClient`
  filters mutate the request.
- [ ] Feed final request headers into default exchange logging when available.
- [ ] Feed final request metadata into observer diagnostics when available.
- [ ] Verify headers added by `ReactiveHttpClientCustomizer` filters appear in
  exchange logs when header logging is enabled.
- [ ] Verify built-in auth, correlation, and tracing headers use existing
  sensitive-header redaction rules.
- [ ] Verify final request diagnostics do not change the outbound request.
- [ ] Verify streaming or non-repeatable bodies are not buffered for diagnostics.
- [ ] Document declarative headers versus final outbound headers in customizer
  and exchange-logging docs.

---

## Priority 6 — Error Body and Timeout Correctness

### [ ] 2.3 Error body capture policy audit
- [ ] Document default retained error body size for exceptions.
- [ ] Document any larger cap used by structured mappers.
- [ ] Test malformed `Content-Type` without losing the response body.
- [ ] Test oversized Problem Detail payloads without generic fallback when the
  mapper has enough data.
- [ ] Test non-JSON error bodies and mapper fallback.
- [ ] Verify sensitive body logging remains controlled by existing presets.

### [ ] 3.2 Timeout layering correctness
- [ ] Re-test timeout precedence after the request-timeout migration.
- [ ] Verify precedence remains `@TimeoutMs` > `@ApiRef` > client request
  timeout.
- [ ] Add coverage for timeout during response headers.
- [ ] Add coverage for timeout during body decode.
- [ ] Add coverage for timeout during streaming response consumption.
- [ ] Verify streaming timeout does not buffer the response body.
- [ ] Verify timeout failures emit one terminal observer/lifecycle signal.

---

## Priority 7 — Retry Signal Semantics and Release Readiness

### [ ] 3.1 Duplicate lifecycle and observer signals under retry
- [ ] Audit lifecycle hook invocation points around retries.
- [ ] Audit observer invocation points around retries.
- [ ] Define per-attempt versus logical-call signals.
- [ ] Test retry success attempt count and final success once.
- [ ] Test retry exhaustion attempt count and final error once.
- [ ] Test cancellation during retry does not emit both cancellation and error
  for the same logical call.
- [ ] Test mapper failure after retry.
- [ ] Document per-attempt and logical-call signal semantics.

### [ ] Release Readiness
- [ ] `CHANGELOG.md` has V7 entries grouped under Added/Changed/Fixed/Docs.
- [ ] README stays short and links to detailed docs.
- [ ] New public APIs have focused tests and concise docs.
- [ ] New test-helper APIs are covered by examples.
- [ ] Configuration metadata is updated if new properties are added.
- [ ] Retry and idempotency behavior is documented without implying automatic
  business idempotency.
- [ ] Final outbound request diagnostics are documented without implying body
  buffering or unredacted sensitive-header logging.
- [ ] Non-repeatable body behavior is documented without implying large-body
  buffering.
- [ ] `mvn test` passes.
- [ ] `mvn -Prelease-smoke test` passes before release.
- [ ] Breaking behavior, if any, is explicitly called out before release.
