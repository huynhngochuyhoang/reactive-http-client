# Reactive HTTP Client — Roadmap V7

> **Status:** draft after the V6 context propagation release. V6 completed
> explicit request-context snapshots, context-aware test helpers, contributor
> hooks, and async handoff documentation. V7 should focus on retry safety,
> idempotency, and payload handling under production failure modes.

V7 keeps the same three-bucket shape:

1. **Features to add** — small APIs that help applications make retry and
   idempotency decisions explicit.
2. **Features to optimize** — existing resilience, body, and observability
   behavior that can become easier to reason about.
3. **Bugs / correctness to fix** — edge cases around retried calls,
   non-repeatable bodies, cancellation, and diagnostics.

The bias for V7: make outbound retries and payload handling explicit and safe.
The starter should help applications avoid duplicate side effects and body
replay surprises, but it should not become a workflow engine, API gateway, or
business-level idempotency store.

Non-goals:

- Do not add a server-side idempotency persistence layer.
- Do not infer business idempotency from HTTP method alone.
- Do not retry non-repeatable request bodies silently.
- Do not make Resilience4j mandatory for users that only need declarative HTTP.
- Do not buffer large request or response bodies to improve diagnostics.

---

## 1. Features to add

### 1.1 Idempotency key support for outbound requests

**Why:** Applications often call downstream `POST`, `PUT`, or `PATCH` endpoints
that are safe to retry only when an idempotency key is present. Today the
starter can apply retry policies, but the idempotency signal is application
owned and must be repeated manually through headers or customizers.

**What:**

- Add a small explicit idempotency-key model for outbound calls, such as an
  annotation or typed request-context helper that writes a configured header.
- Keep the default header name conservative, for example `Idempotency-Key`, and
  allow per-client override only if the existing configuration style supports it
  cleanly.
- Let caller-supplied headers remain authoritative when they already provide an
  idempotency key.
- Avoid generating keys implicitly for every request; applications should opt in
  where duplicate side effects are understood.

**Acceptance:**

- [x] A caller can provide an idempotency key without raw header plumbing.
- [x] Caller-supplied idempotency headers keep documented precedence.
- [x] Per-client rename/disable configuration was not added; the managed
      header remains explicit through `@IdempotencyKey` or ordinary headers.
- [x] Docs explain that the starter does not provide downstream idempotency
      storage.

---

### 1.2 Retry safety policy for unsafe methods

**Why:** Retrying `GET` is usually low risk, while retrying `POST` or `PATCH`
can duplicate side effects unless the call is explicitly idempotent. The starter
should make this risk visible when a retry policy is attached to an unsafe
method.

**What:**

- Add a retry-safety classification for each method metadata plan: safe HTTP
  method, explicit idempotency key, or unsafe retry.
- Warn or fail fast when an unsafe method has retry enabled without an explicit
  idempotency signal, depending on the least surprising compatibility path.
- Preserve existing retry behavior unless the new policy is explicitly enabled,
  if a fail-fast default would be breaking.
- Document how method annotations, `@ApiRef`, and per-client resilience settings
  interact with retry safety.

**Acceptance:**

- [x] Tests cover retry-enabled `GET`, `POST` with idempotency key, and `POST`
      without idempotency key.
- [x] Unsafe retry diagnostics include client name, method, HTTP method, and
      resolved retry source.
- [x] Compatibility behavior is documented if existing unsafe retries remain
      allowed by default.
- [x] No retry-safety logic runs when retry support is absent.

---

### 1.3 Retry and idempotency test-helper assertions

**Why:** Users need tests that prove their outbound call carries the expected
idempotency key and that retries happen only where intended. The current test
helper records exchanges, but V7 should make retry-safety assertions direct.

**What:**

- Add fluent assertions for idempotency header presence, absence, and value.
- Add an example that simulates a transient downstream failure followed by a
  successful retry.
- Add assertions that a call was attempted a specific number of times without
  requiring users to inspect internal mock state.

**Acceptance:**

- [x] Tests can assert the idempotency key sent to downstream.
- [x] Tests can assert idempotency key absence for non-idempotent calls.
- [x] Tests can assert the number of attempts for a mocked exchange.
- [x] Docs include a compact retry/idempotency helper example.

---

## 2. Features to optimize

### 2.1 Resilience operator order and diagnostics audit

**Why:** Retry, circuit breaker, bulkhead, rate limiter, request timeout,
lifecycle hooks, observers, and error decoding all interact in one invocation
chain. When a call fails under load, users need to know which operator rejected,
timed out, retried, or transformed the failure.

**What:**

- Re-audit the documented operator order and align tests with the actual chain.
- Include resolved resilience settings in startup diagnostics without dumping
  noisy object internals.
- Ensure observer and lifecycle hook signals remain consistent across retry,
  rejection, timeout, and cancellation paths.

**Acceptance:**

- [x] Docs state the final operator order in one canonical place.
- [x] Tests prove observer/lifecycle terminal signals for retry success,
      retry exhaustion, bulkhead rejection, rate-limit rejection, timeout, and
      cancellation.
- [x] Startup diagnostics identify which resilience operators are active per
      client and per method where applicable.
- [x] Error categories remain stable for resilience failures.

---

### 2.2 Non-repeatable body detection and guidance

**Why:** Retrying a request with a one-shot body publisher, streaming upload, or
resource-backed multipart part can fail on the second subscription or send an
empty body. Users need the starter to avoid hiding this class of production bug.

**What:**

- Audit body inserter paths for JSON objects, form data, multipart, raw
  publishers, streaming bodies, and `DataBuffer` flows.
- Mark obviously repeatable bodies as retry-compatible and obviously
  non-repeatable bodies as retry-risky where the metadata is available.
- Document cases the starter cannot prove statically.
- Prefer diagnostics and tests over broad buffering.

**Acceptance:**

- [x] Tests cover retry with JSON body, form body, multipart body, and raw
      publisher body.
- [x] Non-repeatable body paths do not retry silently when retry-safety policy
      is enabled.
- [x] Docs explain which body types are repeatable, risky, or application-owned.
- [x] No new behavior buffers large request bodies just to make retry possible.

---

### 2.3 Error body capture policy audit

**Why:** Problem Detail mapping and custom error mappers rely on captured error
bodies. V5 improved Problem Detail reliability, but V7 should make the capture
policy explicit for larger payloads, malformed media types, and mapper fallback.

**What:**

- Document the default error body cap and any larger cap used by structured
  mappers.
- Add focused tests for malformed `Content-Type`, oversized problem payloads,
  non-JSON error bodies, and mapper fallback.
- Ensure logs and exceptions do not accidentally expose sensitive large bodies.

**Acceptance:**

- [x] Docs state how much error body content is retained for exceptions and
      structured mappers.
- [x] Tests cover malformed content type without losing the response body.
- [x] Tests cover oversized Problem Detail payloads without generic fallback
      when the mapper has enough data.
- [x] Sensitive body logging remains controlled by existing presets.

---

### 2.4 Final outbound request diagnostics

**Why:** `DefaultHttpExchangeLogger` currently logs headers from the declarative
method argument resolution step. Headers added later by `WebClient` filters,
including `ReactiveHttpClientCustomizer`, auth, tracing, or correlation filters,
may be sent downstream but not appear in the logged `requestHeaders`. That makes
production debugging confusing when users need to verify final outbound headers.

**What:**

- Capture final outbound request method, URL, and headers after the `WebClient`
  filter chain has applied request mutations.
- Feed final request headers into exchange logging and observer diagnostics when
  available.
- Preserve existing sensitive-header redaction rules.
- Do not buffer or duplicate request bodies to improve logging.
- Document the difference between declarative headers and final outbound
  headers.

**Acceptance:**

- [x] Headers added by a `ReactiveHttpClientCustomizer` filter appear in default
      exchange logs when header logging is enabled.
- [x] Built-in auth, correlation, and tracing headers are visible or redacted
      according to existing sensitive-header policy.
- [x] Final request diagnostics do not change the actual outbound request.
- [x] Streaming or non-repeatable bodies are not buffered for diagnostics.
- [x] Docs explain where to look when debugging customizer-added headers.

---

## 3. Bugs / correctness to fix

### 3.1 Duplicate lifecycle and observer signals under retry

**Why:** Retry can cause multiple HTTP attempts for one logical client call. The
starter needs clear behavior for per-attempt hooks versus logical-call hooks so
metrics, logs, and custom hooks do not double-count unexpectedly.

**What:**

- Audit lifecycle hook and observer invocation points around retries.
- Define which signals are per attempt and which are per logical invocation.
- Add tests for retry success, retry exhaustion, cancellation during retry, and
  mapper failure after retry.

**Acceptance:**

- [ ] Per-attempt and logical-call signals are documented.
- [ ] Retry success emits the expected attempt count and final success once.
- [ ] Retry exhaustion emits the expected attempt count and final error once.
- [ ] Cancellation during retry does not emit both cancellation and error for
      the same logical call.

---

### 3.2 Timeout layering correctness

**Why:** The starter has annotation-level, `@ApiRef`, and client-level request
timeouts, plus possible Resilience4j timeout behavior in user code. Misordered
timeouts can produce confusing categories or leak in-flight body consumption.

**What:**

- Re-test timeout precedence after V5's request-timeout migration.
- Add coverage for timeout during response headers, timeout during body decode,
  and timeout during streaming response consumption.
- Keep published error categories and exception metadata stable.

**Acceptance:**

- [x] Timeout precedence remains `@TimeoutMs` > `@ApiRef` > client request
      timeout.
- [x] Header-timeout and body-timeout paths preserve status/body metadata when
      available.
- [x] Streaming timeout does not buffer the response body.
- [x] Timeout failures emit one terminal observer/lifecycle signal.

---

### 3.3 Header precedence with generated idempotency keys

**Why:** If V7 adds idempotency-key support, it must not override explicit
caller intent or leak generated keys into unrelated requests through mutable
state or shared builders.

**What:**

- Test precedence between annotation headers, method header parameters,
  configured defaults, request context, generated keys, and customizers.
- Verify generated or contextual keys are scoped to one invocation.
- Ensure logs redact idempotency keys only if the configured redaction policy
  says they are sensitive.

**Acceptance:**

- [x] Method/header-parameter idempotency values win over generated values.
- [x] Generated keys do not bleed across concurrent requests.
- [x] Customizers keep their existing documented precedence.
- [x] Logging and test helpers expose or redact idempotency keys consistently
      with existing header policy.

---

## Suggested Priority Order

1. Retry safety policy and operator diagnostics audit.
2. Non-repeatable body detection and retry behavior.
3. Idempotency key support and header precedence.
4. Test-helper retry/idempotency assertions.
5. Final outbound request diagnostics.
6. Error body capture and timeout layering audits.
7. Documentation and release readiness.
