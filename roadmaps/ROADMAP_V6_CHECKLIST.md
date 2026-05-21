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

### [ ] 1.1 Request context snapshot for async handoff
- [ ] Add immutable request context snapshot type for starter-owned values.
- [ ] Capture correlation ID from Reactor context.
- [ ] Capture filtered inbound headers from Reactor context.
- [ ] Add helper to restore snapshot values into Reactor context.
- [ ] Ensure empty context produces an empty snapshot.
- [ ] Document that sinks do not automatically carry Reactor context.

### [ ] 3.2 Sink and scheduler boundary correctness
- [ ] Add tests showing sink subscribers do not see emitter context by default.
- [ ] Add tests showing explicit snapshot handoff across `Sinks.Many`.
- [ ] Add tests showing restored snapshot values survive `publishOn`.
- [ ] Add tests showing restored snapshot values survive `subscribeOn`.
- [ ] Verify no global Reactor hook is required.

---

## Priority 3 — Precedence and public contract

### [ ] 2.2 Context precedence and fallback audit
- [ ] Document precedence for Reactor context, restored snapshot values, MDC
  fallback, and caller-supplied outbound headers.
- [ ] Test snapshot restore over empty subscriber context.
- [ ] Test snapshot restore when subscriber context already has correlation ID
  and inbound headers.
- [ ] Verify caller-supplied outbound headers still win where documented.

### [ ] 2.1 Typed context-key and snapshot contract audit
- [ ] List public Reactor context keys in docs.
- [ ] Add typed accessors/helpers so new APIs do not require raw string keys.
- [ ] Preserve existing string-key compatibility.
- [ ] Add tests that old correlation ID and inbound header keys still work.

---

## Priority 4 — Test helper support

### [ ] 1.2 Context-aware test helpers
- [ ] Add assertions for captured correlation ID.
- [ ] Add assertions for allowed inbound headers.
- [ ] Add assertions for denied or absent inbound headers.
- [ ] Add assertions for redacted inbound header values.
- [ ] Add a documented `Sinks.Many` example proving capture on emit and restore
  on subscribe before an outbound mock client call.

---

## Priority 5 — Documentation and extension hooks

### [ ] 2.3 Async observability guidance
- [ ] Add async-context guidance to docs.
- [ ] Document recommended event-envelope fields.
- [ ] Warn against large or sensitive header snapshots in long-lived queues.
- [ ] Add sink and queue examples.
- [ ] Update the production checklist with async-context handoff guidance.

### [ ] 1.3 Explicit context propagation hooks for custom integrations
- [ ] Publish minimal SPI for starter-owned context contributors/readers.
- [ ] Include built-in correlation ID and inbound header contributors.
- [ ] Allow optional contributors to be absent without changing starter
  behavior.
- [ ] Test deterministic restore order.
- [ ] Ensure SPI exposes immutable snapshots only.
- [ ] Document when explicit event fields are preferred over custom hooks.

---

## Release Readiness

- [ ] `CHANGELOG.md` has V6 entries grouped under Added/Changed/Fixed/Docs.
- [ ] README stays short and links to detailed docs.
- [ ] New public APIs have focused tests and concise docs.
- [ ] New test-helper APIs are covered by examples.
- [ ] Configuration metadata is updated if new properties are added.
- [ ] Async context behavior is documented without implying automatic sink
  propagation.
- [ ] `mvn test` passes.
- [ ] `mvn -Prelease-smoke test` passes before release.
- [ ] Breaking behavior, if any, is explicitly called out before release.
