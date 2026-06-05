# Reactive HTTP Client — Roadmap V3

> **Status:** draft after the 2.0.0 work. V1 established the production
> platform; V2 filled the major gaps around auth, observability, test helpers,
> rate limiting, TLS, and cleanup. V3 should be a maturity roadmap: fewer new
> abstractions, stronger defaults, better diagnostics, and cleaner migration
> paths for real services.

This roadmap keeps the same three-bucket shape as V1 and V2:

1. **Features to add** — small net-new capabilities users still commonly wire by hand.
2. **Features to optimize** — existing behavior that can become simpler, faster, or clearer.
3. **Bugs / correctness to fix** — sharp edges that can surprise production users.

The bias for V3: do not add a new abstraction unless it removes real application
boilerplate or protects users from a common production mistake.

---

## 1. Features to add

### 1.1 Configuration validation report at startup

**Why:** The starter now has many useful options: auth providers, resilience,
proxy, TLS, HTTP/2, pools, logging, observability, API maps. Misconfiguration is
more likely than missing functionality. Users need one clear startup report that
explains what each client resolved to.

**What:**

- Add a DEBUG startup summary per client:
  - base URL source
  - HTTP protocol (`HTTP/1.1` or `HTTP/2`)
  - pool source (global or per-client)
  - proxy/TLS presence
  - auth mode
  - resilience operators enabled
  - observability/logging flags
- Add validation errors for contradictory or incomplete settings.
- Keep secrets redacted.

**Acceptance:**

- [ ] DEBUG summary is emitted once per client.
- [ ] Invalid proxy/TLS/auth combinations fail fast with actionable messages.
- [ ] Tests cover redaction and at least three invalid config paths.

---

### 1.2 Declarative default headers per client

**Why:** Many teams add the same simple headers to every request for a client:
tenant, API version, user agent, partner key, or static feature flag. Today this
requires a customizer or auth provider even when no dynamic signing is needed.

**What:**

```yaml
reactive:
  http:
    clients:
      partner-api:
        default-headers:
          User-Agent: my-service/1.0
          X-Api-Version: "2026-05-16"
```

- Add `Map<String, String> defaultHeaders` to `ClientConfig`.
- Apply them before dynamic `@HeaderParam` values.
- Dynamic headers should override defaults for the same header name.

**Acceptance:**

- [ ] Property binding and metadata are added.
- [ ] Request tests prove default headers are sent.
- [ ] Dynamic method headers override configured defaults.

---

### 1.3 Per-client default query parameters

**Why:** Partner APIs often require stable query parameters such as API version,
locale, or account identifier. Users currently repeat them on every method or
write a filter.

**What:**

```yaml
reactive:
  http:
    clients:
      partner-api:
        default-query-params:
          api-version: "2026-05-16"
          locale: en_US
```

- Apply defaults to every request for that client.
- Method-level `@QueryParam` values should override or append based on the
  existing query-param model; document the exact rule.

**Acceptance:**

- [ ] Property binding and metadata are added.
- [ ] Tests cover no query string, existing query string, and method override.
- [ ] Docs state the merge behavior.

---

### 1.4 Optional request/response logging presets

**Why:** `log-exchange` and custom `HttpExchangeLogger` are powerful, but users
still need to know what is safe to log. V3 should provide presets that make the
secure path obvious.

**What:**

```yaml
reactive:
  http:
    clients:
      user-service:
        log-exchange: true
        log-preset: metadata-only # metadata-only | headers | bodies
```

- `metadata-only`: method, path template, status, latency, category.
- `headers`: metadata + redacted headers.
- `bodies`: current body-capable behavior, guarded by size limits and existing
  observability body flags.

**Acceptance:**

- [ ] Preset property and metadata are added.
- [ ] Default remains conservative.
- [ ] Tests prove sensitive headers remain redacted for all presets.

---

## 2. Features to optimize

### 2.1 Split README and reference docs by audience

**Why:** The README should answer "what is this and should I use it?" quickly.
Detailed reference material belongs in docs. V2 already moved in this direction;
V3 should finish the documentation information architecture.

**What:**

- Keep README under roughly 250 lines.
- Add a "production checklist" doc for pool, timeout, auth, observability, TLS,
  and retry choices.
- Add one "migration from raw WebClient / @HttpExchange" doc.

**Acceptance:**

- [ ] README stays short and links to deeper docs.
- [ ] Production checklist exists.
- [ ] Migration guide includes before/after examples.

---

### 2.2 Normalize client-name diagnostics

**Why:** Client names appear in pool names, logs, metrics, spans, health output,
and exception messages. They should be validated and normalized consistently.

**What:**

- Validate client names during registration/property resolution.
- Document allowed characters.
- Ensure every diagnostic uses the same name and, where useful, the interface FQN.

**Acceptance:**

- [ ] Invalid names fail fast.
- [ ] Duplicate-name errors include both interface names.
- [ ] Pool names, metrics tags, and logs use one consistent format.

---

### 2.3 Reduce hot-path allocations in invocation handling

**Why:** V1/V2 added capability. V3 should revisit the request hot path and trim
avoidable allocations without changing behavior.

**What:**

- Audit `ReactiveClientInvocationHandler` for per-call collections, lambdas, and
  repeated metadata decisions.
- Move stable decisions into cached method metadata/request plans where possible.
- Keep readability higher priority than micro-optimizations.

**Acceptance:**

- [ ] Before/after allocation notes are captured in the PR.
- [ ] Behavior tests pass unchanged.
- [ ] Any new cache has bounded memory behavior.

---

### 2.4 Make generated configuration metadata the source of truth

**Why:** Property docs, examples, and metadata can drift. The project now has
enough properties that drift will become a maintenance problem.

**What:**

- Add a test that verifies important documented properties exist in metadata.
- Prefer metadata descriptions that match docs exactly for common properties.
- Review defaults for every `reactive.http.*` key.

**Acceptance:**

- [ ] Metadata coverage test exists.
- [ ] Missing metadata for new properties fails CI.
- [ ] Docs and metadata agree on defaults for high-value properties.

---

## 3. Bugs / correctness to fix

### 3.1 HTTP/2 compatibility and TLS behavior tests

**Why:** `http2-enabled` is intentionally simple, but protocol negotiation bugs
can be subtle. We should test the real behavior against an in-process server.

**What:**

- Add an in-process Reactor Netty HTTP/2 server test.
- Verify an HTTP/2-enabled client succeeds against an HTTP/2 TLS endpoint.
- Verify default clients still use the existing HTTP/1.1 path.

**Acceptance:**

- [ ] Positive HTTP/2 integration test exists.
- [ ] Default behavior remains unchanged.
- [ ] Test is network-free and does not rely on external services.

---

### 3.2 Guard against unsafe default header/query configuration

**Why:** Default headers and query params can accidentally carry secrets or
malformed values into every request.

**What:**

- Reuse existing header validation for configured default headers.
- Reject control characters.
- Consider warning when sensitive-looking keys are configured outside auth.

**Acceptance:**

- [ ] Invalid default header values fail fast.
- [ ] Query parameter encoding is covered by tests.
- [ ] Sensitive-key warning is documented if implemented.

---

### 3.3 Configuration collision checks

**Why:** As config grows, users can set two options that both try to own the same
behavior: auth bean vs object auth, customizer connector vs starter transport,
logging bodies without body capture, etc.

**What:**

- Detect and document precedence where both settings are valid.
- Fail fast where simultaneous settings are ambiguous.
- Add startup diagnostics for precedence decisions.

**Acceptance:**

- [ ] Auth bean-name vs object-auth precedence has explicit tests.
- [ ] Custom connector replacement risks are documented.
- [ ] Ambiguous combinations produce clear errors or warnings.

---

### 3.4 Error-category consistency audit

**Why:** Error categories are now used by assertions, metrics, spans, and business
logic helpers. Inconsistent category mapping would be hard to debug.

**What:**

- Audit HTTP status, decode errors, timeout, cancellation, DNS, connect, TLS,
  auth-provider errors, and resilience failures.
- Ensure `ErrorCategories.from(Throwable)`, observability, and test helpers agree.

**Acceptance:**

- [ ] Category mapping table is documented.
- [ ] Parameterized tests cover all published categories.
- [ ] OTel and Micrometer tests assert the same category names.

---

## Suggested execution order

1. **3.1 + 1.1** — validate the new HTTP/2 transport path and improve startup
   diagnostics while the 2.0 transport changes are fresh.
2. **1.2 + 3.2** — add default headers with validation; high utility and small surface.
3. **1.3 + 3.3** — add default query params and lock down config precedence rules.
4. **3.4** — audit error categories before they become a stronger public contract.
5. **2.1 + 2.4** — make docs and metadata easier to maintain before more properties land.
6. **1.4** — logging presets after config validation and docs are cleaned up.
7. **2.2 + 2.3** — polish diagnostics and hot-path internals once behavior is stable.

---

## Explicitly Not V3

- **gRPC support.** Still out of scope; use grpc-java or a dedicated gRPC client.
- **RabbitMQ, Kafka, or general messaging support.** Messaging has different
  delivery, retry, acknowledgement, ordering, and backpressure semantics. If we
  support it, it should be a separate starter or sibling project, not part of
  this HTTP client starter.
- **SigV4a.** Continue to recommend AWS SDK for asymmetric regional signing.
- **A full HTTP client DSL.** The annotation model is the product; do not build a
  second configuration language around it.
- **Global HTTP/2 default.** HTTP/2 should remain per-client opt-in unless strong
  production evidence says otherwise.
