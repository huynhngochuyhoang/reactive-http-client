# Roadmap V6 Execution Checklist

> Companion to [`ROADMAP_V6.md`](ROADMAP_V6.md). This file tracks execution;
> keep rationale and design discussion in the roadmap.

---

## Priority 1 — Snapshot correctness foundation

### [x] 3.1 Inbound header snapshot immutability
- [x] Audit `InboundHeadersWebFilter` snapshot construction.
- [x] Store defensive, immutable map and list copies.
- [x] Preserve header order and multi-value behavior.
- [x] Add tests proving original request/header-list mutation cannot affect the
  stored snapshot.
- [x] Add tests proving returned snapshots cannot be mutated.

### [x] 3.3 Header casing, redaction, and deny-list consistency
- [x] Audit mixed-case allow-list and deny-list matching.
- [x] Verify denied headers are redacted before snapshot capture.
- [x] Verify snapshot restore cannot reintroduce raw denied header values.
- [x] Document captured inbound header casing behavior.

---

## Priority 2 — Async context handoff

### [x] 1.1 Request context snapshot for async handoff
- [x] Add immutable request context snapshot type for starter-owned values.
- [x] Capture correlation ID from Reactor context.
- [x] Capture filtered inbound headers from Reactor context.
- [x] Add helper to restore snapshot values into Reactor context.
- [x] Ensure empty context produces an empty snapshot.
- [x] Document that sinks do not automatically carry Reactor context.

### [x] 3.2 Sink and scheduler boundary correctness
- [x] Add tests showing sink subscribers do not see emitter context by default.
- [x] Add tests showing explicit snapshot handoff across `Sinks.Many`.
- [x] Add tests showing restored snapshot values survive `publishOn`.
- [x] Add tests showing restored snapshot values survive `subscribeOn`.
- [x] Verify no global Reactor hook is required.

---

## Priority 3 — Precedence and public contract

### [x] 2.2 Context precedence and fallback audit
- [x] Document precedence for Reactor context, restored snapshot values, MDC
  fallback, and caller-supplied outbound headers.
- [x] Test snapshot restore over empty subscriber context.
- [x] Test snapshot restore when subscriber context already has correlation ID
  and inbound headers.
- [x] Verify caller-supplied outbound headers still win where documented.

### [x] 2.1 Typed context-key and snapshot contract audit
- [x] List public Reactor context keys in docs.
- [x] Add typed accessors/helpers so new APIs do not require raw string keys.
- [x] Preserve existing string-key compatibility.
- [x] Add tests that old correlation ID and inbound header keys still work.

---

## Priority 4 — Test helper support

### [x] 1.2 Context-aware test helpers
- [x] Add assertions for captured correlation ID.
- [x] Add assertions for allowed inbound headers.
- [x] Add assertions for denied or absent inbound headers.
- [x] Add assertions for redacted inbound header values.
- [x] Add a documented `Sinks.Many` example proving capture on emit and restore
  on subscribe before an outbound mock client call.

---

## Priority 5 — Documentation and extension hooks

### [x] 2.3 Async observability guidance
- [x] Add async-context guidance to docs.
- [x] Document recommended event-envelope fields.
- [x] Warn against large or sensitive header snapshots in long-lived queues.
- [x] Add sink and queue examples.
- [x] Update the production checklist with async-context handoff guidance.

### [x] 1.3 Explicit context propagation hooks for custom integrations
- [x] Publish minimal SPI for starter-owned context contributors/readers.
- [x] Include built-in correlation ID and inbound header contributors.
- [x] Allow optional contributors to be absent without changing starter
  behavior.
- [x] Test deterministic restore order.
- [x] Ensure SPI exposes immutable snapshots only.
- [x] Document when explicit event fields are preferred over custom hooks.

---

## Release Readiness

- [x] `CHANGELOG.md` has V6 entries grouped under Added/Changed/Fixed/Docs.
- [x] README stays short and links to detailed docs.
- [x] New public APIs have focused tests and concise docs.
- [x] New test-helper APIs are covered by examples.
- [x] Configuration metadata is updated if new properties are added.
- [x] Async context behavior is documented without implying automatic sink
  propagation.
- [x] `mvn test` passes.
- [x] `mvn -Prelease-smoke test` passes before release.
- [x] Breaking behavior, if any, is explicitly called out before release.
