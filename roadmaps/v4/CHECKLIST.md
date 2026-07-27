# Roadmap V4 Execution Checklist

> Companion to [`ROADMAP.md`](ROADMAP.md). This file tracks execution;
> keep rationale and design discussion in the roadmap.

---

## Priority 1 — Documentation and test-helper ergonomics

### [x] 2.2 Documentation as release artifact
- [x] Add local documentation link validation.
- [x] Check README and quick-start version snippets against `project.version`.
- [x] Decide whether to generate a property reference from configuration metadata.
- [x] If generated, add the generated reference and CI guard.

### [x] 2.3 Test helper ergonomics
- [x] Add fluent assertions for `RecordedExchange`.
- [x] Cover method, path, query, headers, body, and status assertions.
- [x] Add helpers for repeated query params and redacted headers.
- [x] Document common success and failure examples.

---

## Priority 2 — Conflict and cardinality guardrails

### [x] 3.1 Annotation and configuration conflict audit
- [x] Document precedence for annotations, `@ApiRef`, defaults, auth, customizers,
  resilience, and logging.
- [x] Add tests for each documented precedence path.
- [x] Fail fast or document behavior for any remaining ambiguity.

### [x] 3.2 Observability cardinality guardrails
- [x] Audit Micrometer tags and OpenTelemetry attributes for high-cardinality risk.
- [x] Keep risky dimensions opt-in.
- [x] Add warnings or metadata guidance for risky settings.
- [x] Add tests for conservative defaults.

---

## Priority 3 — Invocation internals

### [x] 2.1 Request plan model
- [x] Introduce an immutable request plan where it simplifies metadata use.
- [x] Move stable request decisions out of the per-call path.
- [x] Keep dynamic argument resolution explicit.
- [x] Capture allocation notes in the implementation PR.
- [x] Ensure behavior tests pass unchanged.

---

## Priority 4 — Lifecycle and error mapping extensions

### [x] 1.1 Client lifecycle hooks
- [x] Add a small lifecycle SPI for start, success, error, cancellation, and retry
  attempt boundaries.
- [x] Define ordering and failure isolation.
- [x] Add tests for multiple hook beans.
- [x] Document hooks vs customizers vs observers.

### [x] 1.2 Declarative response error decoding policy
- [x] Add per-client structured error body mapper support.
- [x] Preserve status, response body, and `ErrorCategory`.
- [x] Fall back to the existing default decoder when no mapper applies.
- [x] Add mapper hit, miss, invalid-body, and fallback tests.

---

## Release Readiness

> Historical note: this block was not updated when `2.3.0` shipped. The checked
> implementation sections above, the `2.3.0` changelog section, and the V5
> completion record supersede these unchecked administrative boxes; they are not
> active release tasks.

- [ ] `CHANGELOG.md` has V4 entries grouped under Added/Changed/Fixed/Docs.
- [ ] README remains short and links to detailed docs.
- [ ] New properties have configuration metadata and metadata tests.
- [ ] `mvn test` passes.
- [ ] Breaking behavior, if any, is explicitly called out before release.
