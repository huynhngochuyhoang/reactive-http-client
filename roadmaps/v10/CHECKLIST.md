# Roadmap V10 Execution Checklist

> Companion to [`ROADMAP.md`](ROADMAP.md). This file tracks execution;
> keep rationale and design discussion in the roadmap.

---

## Priority 1 — Documentation Link Hygiene

### [ ] 3.3 Documentation link hygiene after roadmap restructuring
- [ ] Scan `roadmaps/`, README, docs, and changelog for stale flat roadmap
  links.
- [ ] Keep every checklist linked to its sibling `ROADMAP.md`.
- [ ] Keep cross-version references relative and explicit.
- [ ] Verify `roadmaps/README.md` links to every roadmap subdirectory.
- [ ] Verify no stale flat roadmap-file references remain.
- [ ] Run `git diff --check`.

---

## Priority 2 — Shared Contract Documentation and Examples

### [ ] 1.1 Shared contract documentation and examples
- [ ] Promote the inherited-endpoint pattern in quick-start docs.
- [ ] Keep the inherited-endpoint pattern documented in the annotation
  reference.
- [ ] Show one parent operations interface and two concrete
  `@ReactiveHttpClient` children.
- [ ] Add an example where each child uses a different
  `reactive.http.clients.<name>.request-timeout-ms`.
- [ ] Clarify that `@ReactiveHttpClient` belongs on each concrete scanned
  client, not on the shared parent interface.
- [ ] Clarify that Java default methods remain local helpers and do not need
  HTTP metadata.
- [ ] Run inherited-method validation and AOT smoke tests.

---

## Priority 3 — Effective Timeout Reporting for Inherited Endpoints

### [ ] 3.1 Effective timeout reporting for inherited endpoints
- [ ] Audit timeout diagnostics for inherited endpoint methods.
- [ ] Audit lifecycle contexts for inherited endpoint methods.
- [ ] Audit observer events for inherited endpoint methods.
- [ ] Add focused tests proving two concrete clients inherit the same method
  but use different client-level request timeouts.
- [ ] Verify observer and lifecycle metadata report the concrete client name for
  both clients.
- [ ] Keep timeout precedence unchanged:
  `@TimeoutMs` > `@ApiRef timeout-ms` > client `request-timeout-ms` >
  deprecated resilience timeout alias.
- [ ] Ensure docs and diagnostics describe the timeout as concrete-client
  policy.

---

## Priority 4 — Inherited `@ApiRef` Parity

### [ ] 3.2 Inherited `@ApiRef` parity
- [ ] Add tests for inherited `@ApiRef` methods with two concrete clients that
  define the same API key differently.
- [ ] Verify each concrete client resolves its own configured method, path, and
  timeout for the inherited `@ApiRef`.
- [ ] Verify path-variable validation uses the concrete client's configured API
  map.
- [ ] Verify missing API config fails for the concrete client that owns the bad
  configuration.
- [ ] Verify malformed API config fails for the concrete client that owns the
  bad configuration.
- [ ] Verify observability API-name precedence remains
  `@ApiName` > `@ApiRef` > method name.

---

## Priority 5 — Mock Helper Coverage for Inherited Clients

### [ ] 1.3 Mock helper coverage for inherited clients
- [ ] Add a mock-client test for a concrete client that inherits endpoint
  methods from a parent interface.
- [ ] Add a docs example for testing an inherited endpoint with
  `MockReactiveHttpClient`.
- [ ] Verify a mock proxy can call an inherited endpoint method successfully.
- [ ] Verify observer test hooks receive the concrete child client name.
- [ ] Verify lifecycle test hooks receive the concrete child client name.
- [ ] Verify recorded request metadata matches the inherited endpoint path and
  method.
- [ ] Keep unannotated-interface fallback behavior unchanged.
- [ ] Verify existing mock helper behavior for directly declared methods is
  unchanged.

---

## Priority 6 — Inherited Endpoint Startup Validation Messages

### [ ] 2.1 Inherited endpoint startup validation messages
- [ ] Audit validation errors for inherited methods.
- [ ] Include the parent method-declaring interface where inherited-method
  validation fails.
- [ ] Include the concrete child client name or type where proxy construction is
  the failing context.
- [ ] Cover blank `@PathVar` failures on inherited methods.
- [ ] Cover unused path-variable failures on inherited methods.
- [ ] Cover missing path-variable failures on inherited methods.
- [ ] Cover invalid `@ApiRef` metadata on inherited methods.
- [ ] Preserve current exception types.
- [ ] Keep directly declared method errors concise.

---

## Priority 7 — Effective Client-Policy Diagnostics

### [ ] 1.2 Effective client-policy diagnostics
- [ ] Add DEBUG startup diagnostics for inherited endpoint methods.
- [ ] Include the concrete client name in inherited endpoint diagnostics.
- [ ] Include the parent method-declaring interface in inherited endpoint
  diagnostics.
- [ ] Include effective request timeout source:
  method `@TimeoutMs`, `@ApiRef` timeout, client `request-timeout-ms`,
  deprecated alias, or disabled.
- [ ] Include effective base URL source.
- [ ] Mark whether the endpoint method came from a parent interface.
- [ ] Verify two children inheriting the same method can report different
  effective request timeouts.
- [ ] Verify explicit `@TimeoutMs` still appears as the winning source when
  present.
- [ ] Keep valid-client diagnostics out of INFO/WARN logs.

---

## Priority 8 — Configuration Examples for Multi-Client Reuse

### [ ] 2.2 Configuration metadata examples for multi-client reuse
- [ ] Add a concise multi-client YAML example to docs.
- [ ] Show two clients sharing one Java interface contract with different base
  URLs.
- [ ] Show two clients sharing one Java interface contract with different
  request timeouts.
- [ ] Show which fields naturally vary per concrete client: base URL, timeout,
  default headers, auth provider, resilience instances, and redirect following.
- [ ] Link to detailed timeout, auth, resilience, proxy/TLS, and redirect docs.
- [ ] Keep README concise.

---

## Priority 9 — API Compatibility and Release Evidence Upkeep

### [ ] 2.3 API compatibility and release evidence upkeep
- [ ] Keep API compatibility baseline pointed at the latest released artifact.
- [ ] Verify roadmap links after the directory cleanup.
- [ ] Keep native/release smoke docs aligned with the current project version.
- [ ] Add V10 changelog entries only when implementation begins.
- [ ] Run `mvn test`.
- [ ] Run `mvn -Prelease-smoke test`.
- [ ] Run `mvn -Papi-compatibility -DskipTests verify`.
- [ ] Run API compatibility fixtures.
- [ ] Run `git diff --check`.
