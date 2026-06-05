# Roadmap V3 Execution Checklist

> Companion to [`ROADMAP.md`](ROADMAP.md). This file is the tracker; keep the rationale
> and design discussion in the roadmap. Check items off as they ship.

---

## Priority 1 — Transport confidence and startup diagnostics

### [x] 3.1 HTTP/2 compatibility and TLS behavior tests
- [x] Add an in-process Reactor Netty HTTP/2 server test.
- [x] Verify an `http2-enabled: true` client succeeds against an HTTP/2 TLS endpoint.
- [x] Verify default clients keep the existing HTTP/1.1 behavior.
- [x] Keep tests network-free and independent of external services.

### [x] 1.1 Configuration validation report at startup
- [x] Emit one DEBUG startup summary per resolved client.
- [x] Include base URL source, HTTP protocol, pool source, proxy/TLS, auth mode,
  resilience operators, observability, and logging flags.
- [x] Redact all secrets and sensitive values.
- [x] Fail fast for incomplete or contradictory proxy/TLS/auth settings.
- [x] Add tests for redaction and at least three invalid config paths.

---

## Priority 2 — Default headers with validation

### [x] 1.2 Declarative default headers per client
- [x] Add `defaultHeaders` to `ReactiveHttpClientProperties.ClientConfig`.
- [x] Add Spring configuration metadata for `reactive.http.clients.[name].default-headers`.
- [x] Apply default headers to every request for the client.
- [x] Ensure dynamic `@HeaderParam` values override configured defaults.
- [x] Document YAML usage.

### [x] 3.2 Guard against unsafe default header/query configuration
- [x] Reuse existing header-name/header-value validation for configured default headers.
- [x] Reject control characters in configured values.
- [x] Cover invalid default headers in tests.
- [x] Decide whether sensitive-looking configured keys should warn or fail.

---

## Priority 3 — Default query parameters and config precedence

### [x] 1.3 Per-client default query parameters
- [x] Add `defaultQueryParams` to `ReactiveHttpClientProperties.ClientConfig`.
- [x] Add Spring configuration metadata for `reactive.http.clients.[name].default-query-params`.
- [x] Apply defaults to every request for the client.
- [x] Define and test merge behavior with method-level `@QueryParam`.
- [x] Document no-query, existing-query, and override/append examples.

### [x] 3.3 Configuration collision checks
- [x] Document auth bean-name vs object-auth precedence.
- [x] Add tests for auth precedence.
- [x] Document risks of replacing the starter-managed connector in customizers.
- [x] Fail fast or warn for ambiguous configuration combinations.
- [x] Include precedence decisions in startup diagnostics where useful.

---

## Priority 4 — Error-category contract

### [x] 3.4 Error-category consistency audit
- [x] Document the published error-category mapping table.
- [x] Add parameterized tests for HTTP status, decode, timeout, cancellation,
  DNS, connect, TLS, auth-provider, and resilience failures.
- [x] Verify `ErrorCategories.from(Throwable)` agrees with observability tagging.
- [x] Verify Micrometer and OpenTelemetry emit the same category names.
- [x] Update test-helper assertions if any category semantics are clarified.

---

## Priority 5 — Documentation and metadata maintainability

### [x] 2.1 Split README and reference docs by audience
- [x] Keep README short and focused on product fit, install, and first use.
- [x] Add `docs/16-production-checklist.md`.
- [x] Add `docs/17-migration-from-webclient.md`.
- [x] Include before/after examples for raw `WebClient` and Spring `@HttpExchange`.

### [x] 2.4 Make generated configuration metadata the source of truth
- [x] Add a metadata coverage test for important documented properties.
- [x] Make missing metadata for new properties fail CI.
- [x] Review defaults for every `reactive.http.*` key.
- [x] Align high-value docs and metadata descriptions.

---

## Priority 6 — Safer exchange logging

### [x] 1.4 Optional request/response logging presets
- [x] Add `log-preset` property and metadata.
- [x] Implement `metadata-only`, `headers`, and `bodies` presets.
- [x] Keep the default conservative.
- [x] Preserve sensitive-header redaction for all presets.
- [x] Document how presets interact with body logging flags and custom loggers.

---

## Priority 7 — Naming polish and hot-path internals

### [x] 2.2 Normalize client-name diagnostics
- [x] Define and document allowed client-name characters.
- [x] Validate client names during registration/property resolution.
- [x] Ensure duplicate-name errors include both interface names.
- [x] Normalize client-name usage across pool names, metrics tags, logs, spans,
  health output, and exception messages.

### [x] 2.3 Reduce hot-path allocations in invocation handling
- [x] Audit `ReactiveClientInvocationHandler` allocations on the steady path.
- [x] Move stable decisions into cached metadata/request plans where it improves clarity.
- [x] Capture before/after allocation notes in the implementation PR.
- [x] Keep new caches bounded.
- [x] Ensure behavior tests pass unchanged.

---

## Release Readiness

- [x] `CHANGELOG.md` has V3 entries grouped under Added/Changed/Fixed/Docs.
- [x] README remains short and points to detailed docs.
- [x] Configuration metadata is complete for all new properties.
- [x] `mvn test` passes.
- [x] Breaking behavior, if any, is explicitly called out before release.
