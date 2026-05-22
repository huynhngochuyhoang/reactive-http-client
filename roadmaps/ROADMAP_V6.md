# Reactive HTTP Client — Roadmap V6

> **Status:** completed for `2.5.0` on 2026-05-22. V6 focused on explicit
> context propagation across asynchronous boundaries without turning the starter
> into an eventing framework.

V6 keeps the same three-bucket shape:

1. **Features to add** — small APIs and helpers that remove repeated
   application glue around Reactor context.
2. **Features to optimize** — existing context, header, and observability
   behavior that can become clearer and safer.
3. **Bugs / correctness to fix** — async-boundary and snapshot edge cases that
   need explicit tests.

The bias for V6: make request context handoff explicit. Reactor `Context` works
inside one subscription chain, but it does not automatically travel through
`Sinks`, queues, schedulers, message brokers, or arbitrary callbacks. The
starter should provide a narrow, documented way to capture and restore HTTP
request context where applications choose to carry it.

Non-goals:

- Do not add a message broker abstraction.
- Do not make sink subscribers implicitly inherit emitter context.
- Do not propagate sensitive inbound headers unless the existing allow/deny
  policy permits them.
- Do not make OpenTelemetry, Micrometer, or Resilience4j mandatory.

---

## 1. Features to add

### 1.1 Request context snapshot for async handoff

**Why:** `InboundHeadersWebFilter`, `CorrelationIdWebFilter`, and the optional
OpenTelemetry filter store request context in Reactor `Context`. That context is
available to outbound client calls inside the same reactive chain, but it is not
preserved when code emits an event into `Sinks.Many`, a queue, or another
out-of-band publisher. Applications need a simple, safe object they can place in
their own event envelope.

**What:**

- Add a small immutable context snapshot type for values the starter owns:
  correlation ID and filtered inbound headers.
- Provide capture and restore helpers, for example:
  `RequestContextSnapshot.capture(ContextView)` and
  `snapshot.writeTo(Context)`.
- Keep the API independent of any concrete event, sink, or broker type.
- Preserve current redaction and allow-list behavior; the snapshot should only
  contain values already admitted into Reactor context by the filters.

**Acceptance:**

- [x] A handler can capture the current request context and include it in an
      application event envelope.
- [x] A sink subscriber can restore that snapshot before calling a
      `@ReactiveHttpClient`.
- [x] Empty context produces an empty snapshot without errors.
- [x] Docs clearly state that sinks do not automatically carry Reactor context.

---

### 1.2 Context-aware test helpers

**Why:** Applications using async handoff need tests that prove the captured
context is present when a later subscriber performs an outbound call. Today the
test helper module focuses on HTTP exchanges, not request-context envelopes.

**What:**

- Add test helper assertions for context snapshots, such as correlation ID,
  inbound header presence, redacted header values, and absence of denied
  headers.
- Add a small example test using `Sinks.Many` to show capture on emit and
  restore on subscribe.
- Keep the helper optional and lightweight; no test framework beyond the
  existing JUnit/Reactor test stack.

**Acceptance:**

- [x] Tests can assert a captured correlation ID.
- [x] Tests can assert allowed, denied, and redacted inbound headers.
- [x] A documented sink example verifies context restoration before an outbound
      mock client call.

---

### 1.3 Explicit context propagation hooks for custom integrations

**Why:** Users may integrate schedulers, event buses, or internal queues that do
not use a common envelope type. They need extension points that are simpler than
reading raw string context keys, but still do not couple the starter to an
eventing library.

**What:**

- Publish a minimal SPI for context contributors/readers owned by the starter.
- Let optional modules contribute their own context values only when present.
- Keep restore order deterministic when multiple contributors write to Reactor
  context.
- Document when applications should prefer explicit event fields over custom
  propagation hooks.

**Acceptance:**

- [x] Built-in correlation ID and inbound headers are available through the SPI.
- [x] Optional contributors can be absent without changing starter behavior.
- [x] Restore order is tested and documented.
- [x] The SPI does not expose mutable internal maps or lists.

---

## 2. Features to optimize

### 2.1 Typed context-key and snapshot contract audit

**Why:** The starter currently exposes string Reactor context keys such as
`"correlationId"` and `"inboundHeaders"`. Those keys are easy to use, but they
are also easy to collide with application code and hard to evolve.

**What:**

- Audit all public Reactor context keys and document which are stable public
  contracts.
- Prefer typed accessors/helper methods for new APIs while preserving existing
  keys for compatibility.
- Add tests that verify old keys still work.

**Acceptance:**

- [x] Public context keys are listed in docs.
- [x] New snapshot APIs do not require users to reference raw string keys.
- [x] Existing correlation ID and inbound header context behavior remains
      backward compatible.

---

### 2.2 Context precedence and fallback audit

**Why:** Correlation ID can come from inbound headers, Reactor context, or MDC
fallbacks. OpenTelemetry can come from Reactor context or current OTel scope.
Async handoff makes precedence more visible, and inconsistent rules become hard
to debug.

**What:**

- Document precedence for Reactor context, restored snapshot values, MDC
  fallback, and caller-supplied outbound headers.
- Add tests for restored snapshot values combined with existing subscriber
  context.
- Keep caller-supplied outbound request headers as the final authority where
  that is already the documented behavior.

**Acceptance:**

- [x] Docs state precedence for correlation ID and inbound headers.
- [x] Tests cover snapshot restore over empty context.
- [x] Tests cover snapshot restore when subscriber context already has values.
- [x] Caller-supplied outbound headers still win where currently documented.

---

### 2.3 Async observability guidance

**Why:** Carrying inbound headers and context through events can increase
cardinality, memory usage, and accidental data exposure. V5 added conservative
observability defaults; V6 should extend that guidance to async handoff.

**What:**

- Document recommended event-envelope fields for correlation ID, request ID,
  tenant-like low-cardinality keys, and trace context.
- Warn against placing large or sensitive header snapshots in long-lived queues.
- Add examples showing small explicit fields as the default, with full snapshot
  handoff reserved for short-lived in-process boundaries.

**Acceptance:**

- [x] Docs include an async-context section with sink and queue examples.
- [x] Docs call out memory and cardinality risks.
- [x] The production checklist includes async-context handoff guidance.

---

## 3. Bugs / correctness to fix

### 3.1 Inbound header snapshot immutability

**Why:** A captured inbound header snapshot should not observe later mutation of
request header collections or application-owned lists. Async handoff makes this
more important because events may be consumed after the request has completed.

**What:**

- Audit `InboundHeadersWebFilter` snapshot construction.
- Ensure stored maps and lists are defensive, immutable copies.
- Preserve header order and multi-value behavior.

**Acceptance:**

- [x] Tests prove mutating the original request headers does not mutate the
      stored snapshot.
- [x] Tests prove mutating a returned snapshot is not possible.
- [x] Header order and multi-value values remain stable.

---

### 3.2 Sink and scheduler boundary correctness

**Why:** Reactor `Context` is subscription-scoped. Bugs appear when users assume
it survives `Sinks.Many`, `publishOn`, `subscribeOn`, manual `subscribe`, or
callbacks. The starter should document and test the boundaries it supports.

**What:**

- Add focused tests showing what context is visible across sink emission and
  subscription.
- Add tests for snapshot restore across `publishOn` and `subscribeOn`.
- Keep behavior explicit; do not add global hooks that change all Reactor
  pipelines in an application.

**Acceptance:**

- [x] Tests show sink subscribers do not see emitter context unless a snapshot
      is carried explicitly.
- [x] Tests show restored snapshot values survive scheduler hops inside the
      subscriber chain.
- [x] No global Reactor hook is required for starter behavior.

---

### 3.3 Header casing, redaction, and deny-list consistency

**Why:** Inbound header snapshots are used for logs, lifecycle context, and
future async handoff. Case-insensitive matching, original-name preservation, and
redaction must remain consistent across those consumers.

**What:**

- Audit allow-list and deny-list matching for mixed-case headers.
- Verify denied headers are redacted before snapshot capture and remain redacted
  after snapshot restore.
- Document whether original header casing is preserved in snapshots.

**Acceptance:**

- [x] Mixed-case sensitive headers are denied/redacted consistently.
- [x] Snapshot restore cannot reintroduce raw denied header values.
- [x] Docs state casing behavior for captured inbound headers.

---

## Suggested Priority Order

1. Inbound header snapshot immutability.
2. Request context snapshot for async handoff.
3. Sink and scheduler boundary correctness.
4. Context precedence and fallback audit.
5. Context-aware test helpers.
6. Typed context-key and snapshot contract audit.
7. Async observability guidance.
8. Explicit context propagation hooks for custom integrations.
