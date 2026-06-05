# Roadmap V10 Execution Checklist

> Companion to [`ROADMAP.md`](ROADMAP.md). This file tracks execution;
> keep rationale and design discussion in the roadmap.

---

## Priority 1 — Documentation Link Hygiene

### [x] 3.3 Documentation link hygiene after roadmap restructuring
- [x] Scan `roadmaps/`, README, docs, and changelog for stale flat roadmap
  links.
- [x] Keep every checklist linked to its sibling `ROADMAP.md`.
- [x] Keep cross-version references relative and explicit.
- [x] Verify `roadmaps/README.md` links to every roadmap subdirectory.
- [x] Verify no stale flat roadmap-file references remain.
- [x] Run `git diff --check`.

---

## Priority 2 — Shared Contract Documentation and Examples

### [x] 1.1 Shared contract documentation and examples
- [x] Promote the inherited-endpoint pattern in quick-start docs.
- [x] Keep the inherited-endpoint pattern documented in the annotation
  reference.
- [x] Show one parent operations interface and two concrete
  `@ReactiveHttpClient` children.
- [x] Add an example where each child uses a different
  `reactive.http.clients.<name>.request-timeout-ms`.
- [x] Clarify that `@ReactiveHttpClient` belongs on each concrete scanned
  client, not on the shared parent interface.
- [x] Clarify that Java default methods remain local helpers and do not need
  HTTP metadata.
- [x] Run inherited-method validation and AOT smoke tests.

---

## Priority 3 — Effective Timeout Reporting for Inherited Endpoints

### [x] 3.1 Effective timeout reporting for inherited endpoints
- [x] Audit timeout diagnostics for inherited endpoint methods.
- [x] Audit lifecycle contexts for inherited endpoint methods.
- [x] Audit observer events for inherited endpoint methods.
- [x] Add focused tests proving two concrete clients inherit the same method
  but use different client-level request timeouts.
- [x] Verify observer and lifecycle metadata report the concrete client name for
  both clients.
- [x] Keep timeout precedence unchanged:
  `@TimeoutMs` > `@ApiRef timeout-ms` > client `request-timeout-ms` >
  deprecated resilience timeout alias.
- [x] Ensure docs and diagnostics describe the timeout as concrete-client
  policy.

---

## Priority 4 — Inherited `@ApiRef` Parity

### [x] 3.2 Inherited `@ApiRef` parity
- [x] Add tests for inherited `@ApiRef` methods with two concrete clients that
  define the same API key differently.
- [x] Verify each concrete client resolves its own configured method, path, and
  timeout for the inherited `@ApiRef`.
- [x] Verify path-variable validation uses the concrete client's configured API
  map.
- [x] Verify missing API config fails for the concrete client that owns the bad
  configuration.
- [x] Verify malformed API config fails for the concrete client that owns the
  bad configuration.
- [x] Verify observability API-name precedence remains
  `@ApiName` > `@ApiRef` > method name.

---

## Priority 5 — Mock Helper Coverage for Inherited Clients

### [x] 1.3 Mock helper coverage for inherited clients
- [x] Add a mock-client test for a concrete client that inherits endpoint
  methods from a parent interface.
- [x] Add a docs example for testing an inherited endpoint with
  `MockReactiveHttpClient`.
- [x] Verify a mock proxy can call an inherited endpoint method successfully.
- [x] Verify observer test hooks receive the concrete child client name.
- [x] Verify lifecycle test hooks receive the concrete child client name.
- [x] Verify recorded request metadata matches the inherited endpoint path and
  method.
- [x] Keep unannotated-interface fallback behavior unchanged.
- [x] Verify existing mock helper behavior for directly declared methods is
  unchanged.

---

## Priority 6 — Inherited Endpoint Startup Validation Messages

### [x] 2.1 Inherited endpoint startup validation messages
- [x] Audit validation errors for inherited methods.
- [x] Include the parent method-declaring interface where inherited-method
  validation fails.
- [x] Include the concrete child client name or type where proxy construction is
  the failing context.
- [x] Cover blank `@PathVar` failures on inherited methods.
- [x] Cover unused path-variable failures on inherited methods.
- [x] Cover missing path-variable failures on inherited methods.
- [x] Cover invalid `@ApiRef` metadata on inherited methods.
- [x] Preserve current exception types.
- [x] Keep directly declared method errors concise.

---

## Priority 7 — Effective Client-Policy Diagnostics

### [x] 1.2 Effective client-policy diagnostics
- [x] Add DEBUG startup diagnostics for inherited endpoint methods.
- [x] Include the concrete client name in inherited endpoint diagnostics.
- [x] Include the parent method-declaring interface in inherited endpoint
  diagnostics.
- [x] Include effective request timeout source:
  method `@TimeoutMs`, `@ApiRef` timeout, client `request-timeout-ms`,
  deprecated alias, or disabled.
- [x] Include effective base URL source.
- [x] Mark whether the endpoint method came from a parent interface.
- [x] Verify two children inheriting the same method can report different
  effective request timeouts.
- [x] Verify explicit `@TimeoutMs` still appears as the winning source when
  present.
- [x] Keep valid-client diagnostics out of INFO/WARN logs.

---

## Priority 8 — Configuration Examples for Multi-Client Reuse

### [x] 2.2 Configuration metadata examples for multi-client reuse
- [x] Add a concise multi-client YAML example to docs.
- [x] Show two clients sharing one Java interface contract with different base
  URLs.
- [x] Show two clients sharing one Java interface contract with different
  request timeouts.
- [x] Show which fields naturally vary per concrete client: base URL, timeout,
  default headers, auth provider, resilience instances, and redirect following.
- [x] Link to detailed timeout, auth, resilience, proxy/TLS, and redirect docs.
- [x] Keep README concise.

---

## Priority 9 — API Compatibility and Release Evidence Upkeep

### [x] 2.3 API compatibility and release evidence upkeep
- [x] Keep API compatibility baseline pointed at the latest released artifact.
- [x] Verify roadmap links after the directory cleanup.
- [x] Keep native/release smoke docs aligned with the current project version.
- [x] Add V10 changelog entries only when implementation begins.
- [x] Run `mvn test`.
- [x] Run `mvn -Prelease-smoke test`.
- [x] Run `mvn -Papi-compatibility -DskipTests verify`.
- [x] Run API compatibility fixtures.
- [x] Run `git diff --check`.
