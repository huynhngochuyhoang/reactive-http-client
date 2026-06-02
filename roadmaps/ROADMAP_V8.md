# Reactive HTTP Client — Roadmap V8

> **Status:** draft after V7 was released as `2.6.0`. V8 should focus on
> subscription-isolated reporting, diagnostic context parity, extension-point
> testability, and stronger compatibility evidence.

V8 keeps the same three-bucket shape:

1. **Features to add** — small public surfaces that expose facts the runtime
   already knows but extension points cannot yet consume.
2. **Features to optimize** — diagnostics, native-image support, and release
   checks that should become easier to trust.
3. **Bugs / correctness to fix** — subscription concurrency and redirect edge
   cases where reporting must match actual runtime behavior.

The bias for V8: make diagnostic data precise under retries and concurrent cold
publisher subscriptions. Add information where it removes blind spots, but keep
logging bounded, redacted, and logical-call scoped by default.

Non-goals:

- Do not add a new observability backend to the core starter.
- Do not enable header or body logging by default.
- Do not describe retry subscriptions as proof that a request reached the
  network.
- Do not retain unbounded error bodies for mapper convenience.
- Do not make full native-image compilation part of every local build.
- Do not remove compatibility aliases or constructors in a minor release.

---

## 1. Features to add

### 1.1 Exchange-log subscription-attempt count

**Why:** `HttpClientObserverEvent` exposes the final subscription-attempt count,
but `HttpExchangeLogContext` does not. A custom exchange logger can see the
logical call result but cannot tell whether Resilience4j retry re-subscribed.
That is a useful production debugging signal, especially for degraded
downstreams.

**What:**

- Add a precisely named subscription-attempt count to `HttpExchangeLogContext`.
- Feed the final logical-call count into custom exchange loggers and the default
  logger.
- Keep the meaning aligned with `HttpClientObserverEvent.getAttemptCount()`:
  this counts subscriptions, not guaranteed network sends.
- Preserve source compatibility for existing convenience constructors by
  defaulting their count to `1`.

**Acceptance:**

- [x] A first-try call reports subscription-attempt count `1`.
- [x] A retried call reports the final subscription-attempt count once in its
      terminal exchange-log context.
- [x] A pre-network serialization failure is not documented as an HTTP send.
- [x] Existing custom logger implementations continue to compile unchanged.
- [x] Exchange-logging docs show how a custom logger can record the count.

---

### 1.2 Observer and lifecycle support in test helpers

**Why:** `MockReactiveHttpClient` exercises the real proxy path, but its builder
currently creates an empty application context and cannot register an observer
or lifecycle hook. Applications that rely on custom metrics, audit hooks, or
retry callbacks need focused tests without constructing the handler manually.

**What:**

- Add small builder methods such as `withObserver(...)` and
  `withLifecycleHook(...)` to `MockReactiveHttpClient`.
- Register supplied components in the helper context using the same ordered hook
  behavior as the starter.
- Keep the helper independent of Micrometer and OpenTelemetry unless a test
  explicitly supplies those implementations.
- Add compact assertions or examples for one terminal observer event and retry
  lifecycle boundaries.

**Acceptance:**

- [x] Tests can attach one custom `HttpClientObserver` to a mock client.
- [x] Tests can attach ordered lifecycle hooks and assert callback order.
- [x] Retry success and exhaustion examples prove one logical-call observer
      event with the final subscription-attempt count.
- [x] Existing helper usage behaves exactly as before when no hook is supplied.

---

### 1.3 Error-body truncation metadata for mappers

**Why:** `DefaultErrorDecoder` intentionally caps retained error bodies. Custom
`ErrorResponseMapper` implementations receive the bounded text but cannot tell
whether it is complete or truncated. A mapper may otherwise treat a truncated
structured document as malformed input and hide the real reason for fallback.

**What:**

- Expose bounded capture metadata to mappers: whether truncation occurred and
  how many bytes were retained.
- Keep default caps unchanged unless evidence justifies a separate change.
- Preserve a compatibility constructor for existing `ErrorResponseContext`
  callers if the public record changes.
- Document that mapper input remains bounded and may be incomplete.

**Acceptance:**

- [x] A mapper can distinguish a complete body from a truncated body.
- [x] Tests cover the default 4 KiB cap and the 64 KiB
      `application/problem+json` cap.
- [x] Generic exceptions still expose at most their documented 4 KiB body text.
- [x] Existing mapper implementations remain source compatible.

---

### 1.4 `@ApiRef` fallback for observability API names

**Why:** Observer events currently use the `@ApiName` value when present and
otherwise fall back to the Java method name. For methods backed by `@ApiRef`, the
reference value is already the stable configured API identity and is often more
useful in metrics and traces than a generic Java method name such as `call`.

**What:**

- Resolve the observer-facing API name with explicit precedence:
  `@ApiName` value > `@ApiRef` value > Java method name.
- Apply the resolved name consistently to `HttpClientObserverEvent`, Micrometer
  `api.name`, OpenTelemetry `rhttp.api.name`, span names, and lifecycle context.
- Keep `@ApiName` authoritative so existing explicit metric and span names do
  not change.
- Document the fallback as an observability naming rule, not as a change to
  `@ApiRef` request routing.

**Acceptance:**

- [x] A method with only `@ApiRef("user.getById")` reports `user.getById` as its
      observer API name.
- [x] A method with both `@ApiName("user.lookup")` and
      `@ApiRef("user.getById")` reports `user.lookup`.
- [x] A method without `@ApiName` or `@ApiRef` still reports its Java method
      name.
- [x] Micrometer tags, OpenTelemetry spans, lifecycle hooks, and docs use the
      same precedence rule.

---

## 2. Features to optimize

### 2.1 Diagnostic context contract alignment

**Why:** Exchange loggers, observers, lifecycle hooks, and error mappers serve
different use cases. They should not be forced into one oversized context type,
but users need one accurate reference for which facts are available at which
stage. V7 exposed final outbound headers to logs and observers and clarified
subscription attempts; V8 should make the remaining differences explicit.

**What:**

- Audit fields across `HttpExchangeLogContext`, `HttpClientObserverEvent`,
  `ReactiveHttpClientLifecycleContext`, and `ErrorResponseContext`.
- Document a capability matrix: final outbound URL, final outbound headers,
  response status, response headers, error body, duration, and subscription
  attempt count.
- Add missing fields only where there is a concrete debugging or extension-point
  need.
- Document raw-versus-redacted behavior for custom exchange loggers and custom
  observers.

**Acceptance:**

- [x] Docs contain one canonical diagnostic-context capability matrix.
- [x] Each documented field matches the runtime value and lifecycle stage.
- [x] Docs do not imply that lifecycle hooks expose response headers when they
      do not.
- [x] Docs state which extension points receive raw headers and which built-in
      implementations redact them.

---

### 2.2 Public API compatibility automation

**Why:** The starter publishes records, constructors, annotations, helper APIs,
and configuration properties. V8 is likely to extend context records, so manual
review alone is weak evidence that a minor release remains source and binary
compatible.

**What:**

- Add an API compatibility report against the latest released baseline.
- Fail CI for unreviewed binary-incompatible public API changes.
- Allow deliberate compatibility bridges, such as retained constructors, to be
  tested explicitly.
- Keep the check scoped to published artifacts, not test fixtures or internal
  implementation classes.

**Acceptance:**

- [ ] CI compares published API surfaces against the selected release baseline.
- [ ] An accidental public constructor removal fails the compatibility check.
- [ ] Additive public APIs pass without manual suppression.
- [ ] Release docs explain how to review an intentional breaking change for a
      future major version.

---

### 2.3 Native-image and Spring Boot compatibility evidence

**Why:** The starter has Spring AOT smoke coverage, but native-image compilation
is not run by the default CI job and the release smoke matrix covers one Spring
Boot baseline. Context and constructor changes should not weaken AOT support
silently.

**What:**

- Keep the fast AOT smoke test in normal CI.
- Add an opt-in or scheduled minimal native-image build for the core starter
  path.
- Expand release-smoke coverage to the documented supported Spring Boot
  baselines when that support policy is defined.
- Cover inherited client methods and optional integrations being absent from the
  classpath.

**Acceptance:**

- [ ] Normal CI still runs the fast Spring AOT smoke test.
- [ ] A scheduled or explicitly triggered job builds one minimal native image.
- [ ] Release-smoke CI covers each documented Spring Boot baseline.
- [ ] Native-image docs clearly separate tested core support from optional
      integration ownership.

---

## 3. Bugs / correctness to fix

### 3.1 Subscription-local terminal reporting state

**Why:** A cold `Mono` or `Flux` returned by one proxy invocation can be
subscribed more than once, including concurrently. Retry count, generated
idempotency keys, and final request observation are already subscription-local,
but request start time, request URL, response status, response headers, and
terminal error still need a full audit. Shared mutable state can make one
subscriber report another subscriber's outcome.

**What:**

- Move all mutable logical-call reporting state into subscription-local state.
- Cover both `Mono<T>` and `Flux<T>` return paths.
- Preserve retry behavior inside one subscription while isolating independent
  subscriptions to the same returned publisher.
- Check cancellation, response decoding, final request observation, lifecycle
  hooks, exchange logging, and observer reporting together.

**Acceptance:**

- [x] Concurrent subscriptions can receive different statuses without
      cross-reporting response status or headers.
- [x] One cancelled subscriber does not overwrite another subscriber's terminal
      error or duration.
- [x] Final outbound URL and headers belong to the subscriber that emitted the
      terminal observer/log record.
- [x] Retry attempts remain grouped inside their originating logical call.
- [x] Tests cover concurrent `Mono<T>` and streaming `Flux<T>` subscriptions.

---

### 3.2 Redirect handling contract

**Why:** Error decoding is intentionally limited to 4xx/5xx responses, but some
documentation still talks broadly about non-2xx responses. Redirect behavior can
also vary depending on the underlying HTTP client configuration. Applications
need a tested contract so 3xx responses are not mislabeled as server errors.

**What:**

- Audit 3xx handling through the real starter proxy path.
- Decide and document whether redirect responses are exposed, decoded as normal
  responses, or followed by the configured transport.
- Keep `ErrorResponseMapper` and default exception categories restricted to
  actual error statuses.
- Verify observer outcome classification for visible redirects.

**Acceptance:**

- [x] 3xx responses are never mapped to `RemoteServiceException` solely because
      they are non-4xx.
- [x] Tests cover at least one visible redirect response and one followed
      redirect path if transport configuration supports it.
- [x] Observer outcome remains `REDIRECTION` when a 3xx response is visible.
- [x] Error-handling docs describe 4xx/5xx decoding precisely.

---

### 3.3 Retained error-body drain and cancellation behavior

**Why:** Bounded error-body capture must limit retained memory while still
releasing response buffers and returning reusable connections to the pool when
possible. Oversized or cancelled error streams are the paths most likely to
expose leaks or surprising latency.

**What:**

- Audit buffer release while retaining capped error text.
- Add tests for oversized chunked errors, mapper fallback, and cancellation
  during error-body capture.
- Keep retained memory bounded without silently claiming the entire body was
  preserved.
- Document when draining the remaining response body is required for connection
  reuse.

**Acceptance:**

- [x] Oversized error streams retain only the configured cap.
- [x] Every consumed `DataBuffer` is released on success, fallback, and
      cancellation paths.
- [x] Mapper fallback preserves status and bounded body metadata.
- [x] Error-body capture tests do not require unbounded buffering.

---

## Suggested Priority Order

1. Subscription-local terminal reporting state.
2. Exchange-log subscription-attempt count.
3. Observer and lifecycle support in test helpers.
4. `@ApiRef` fallback for observability API names.
5. Error-body truncation metadata and drain behavior.
6. Diagnostic context contract alignment.
7. Redirect handling contract.
8. Compatibility automation, native-image evidence, documentation, and release
   readiness.
