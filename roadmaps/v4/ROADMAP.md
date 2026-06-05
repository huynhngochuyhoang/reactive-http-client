# Reactive HTTP Client — Roadmap V4

> **Status:** draft after completing the V3 maturity roadmap. V3 made the
> starter safer and easier to operate. V4 should focus on extensibility without
> turning the HTTP starter into a general integration framework.

V4 keeps the same three-bucket shape:

1. **Features to add** — capabilities that reduce real application boilerplate.
2. **Features to optimize** — existing behavior that can become cleaner or more
   maintainable.
3. **Bugs / correctness to fix** — contracts that need stronger guarantees.

The bias for V4: keep the roadmap strictly focused on HTTP client behavior and
avoid pulling other protocol concepts into this starter.

---

## 1. Features to add

### 1.1 Client lifecycle hooks

**Why:** Users often need lightweight hooks around a call: enrich context,
record audit events, attach tenant metadata, or observe retries. Today they can
write filters and custom observers, but there is no single starter-level hook
contract.

**What:**

- Add a small lifecycle SPI around resolved request start, response success,
  error, cancellation, and retry attempt boundaries.
- Keep hooks read-mostly; mutation should stay in existing request filters and
  auth providers.
- Make hook failures isolated from business calls, like observer failures.

**Acceptance:**

- [ ] SPI exists with clear ordering and failure isolation.
- [ ] Tests cover success, error, cancellation, and multiple hook beans.
- [ ] Docs explain when to use hooks vs customizers vs observers.

---

### 1.2 Declarative response error decoding policy

**Why:** The default error decoder is consistent, but real downstreams often use
structured error bodies. Users need a simpler way to map those bodies without
replacing the whole decoder.

**What:**

- Add an extension contract for per-client structured error body mapping.
- Keep the default exception and category model unchanged.
- Support fallback to the current decoder when a mapper does not apply.

**Acceptance:**

- [ ] Per-client mapper registration is supported.
- [ ] Mapping preserves `statusCode`, `responseBody`, and `ErrorCategory`.
- [ ] Tests cover mapper hit, mapper miss, invalid body, and fallback behavior.

---

## 2. Features to optimize

### 2.1 Request plan model

**Why:** V3 cached static request plans for non-`@ApiRef` methods. V4 should
finish that direction only where it improves clarity.

**What:**

- Introduce a small immutable request plan object for method metadata.
- Move stable header/query/path/body decisions into that plan where possible.
- Keep dynamic argument resolution explicit and readable.

**Acceptance:**

- [ ] Request behavior tests pass unchanged.
- [ ] No unbounded caches are introduced.
- [ ] Allocation notes are captured in the implementation PR.

---

### 2.2 Documentation as release artifact

**Why:** The docs now cover enough features that release quality depends on doc
quality. V4 should make doc drift harder.

**What:**

- Add link validation for local docs.
- Add checks for version snippets in README and quick-start docs.
- Add a generated property reference from configuration metadata if practical.

**Acceptance:**

- [ ] Broken local doc links fail CI.
- [ ] Version snippets are checked against `project.version`.
- [ ] Metadata-derived property reference is generated or the reason is
  documented.

---

### 2.3 Test helper ergonomics

**Why:** The test module is useful, but teams need expressive assertions for
headers, query params, retries, and error categories without hand-parsing
recorded exchanges.

**What:**

- Add fluent assertions for `RecordedExchange`.
- Add helpers for repeated query params and redacted headers.
- Add examples for common failure tests.

**Acceptance:**

- [ ] Fluent assertions cover method, path, query, headers, body, and status.
- [ ] Existing test-helper API remains source-compatible.
- [ ] Docs include concise examples.

---

## 3. Bugs / correctness to fix

### 3.1 Annotation and configuration conflict audit

**Why:** The starter now has several ways to define request behavior:
annotations, `@ApiRef`, defaults, customizers, auth, and resilience settings.
V4 should explicitly audit the remaining conflict cases.

**What:**

- Review precedence for annotation vs configuration behavior.
- Add tests for any undocumented conflict.
- Turn ambiguous behavior into either validation or documented precedence.

**Acceptance:**

- [ ] Precedence table exists in docs.
- [ ] Tests cover each documented conflict path.
- [ ] Ambiguous behavior is removed or explicitly documented.

---

### 3.2 Observability cardinality guardrails

**Why:** The starter exposes useful tags, but production metrics can become
expensive when tags are too dynamic.

**What:**

- Audit metric and span attributes for high-cardinality risks.
- Add warnings or metadata guidance for risky settings.
- Keep defaults conservative.

**Acceptance:**

- [ ] Docs identify high-cardinality tags and safer alternatives.
- [ ] Tests prove defaults avoid resolved host/path explosion where applicable.
- [ ] Warnings are emitted only for actionable risky configuration.

---

## Suggested Priority Order

1. Documentation/release checks and test-helper assertions.
2. Annotation/configuration conflict audit.
3. Request plan model cleanup.
4. Lifecycle hooks.
5. Structured error body mapping.
6. Observability cardinality guardrails.
