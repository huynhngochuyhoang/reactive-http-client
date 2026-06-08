# Reactive HTTP Client — Roadmap V10

> **Status:** completed and released as `2.9.0`. V10 focused on shared client contracts,
> effective-policy diagnostics, and configuration ergonomics for applications
> that reuse the same endpoint interface across multiple downstream clients.

V10 keeps the same three-bucket shape:

1. **Features to add** — small surfaces that make shared contracts and
   multi-client setups easier to use and test.
2. **Features to optimize** — diagnostics and metadata checks that should make
   the effective runtime policy obvious before traffic is sent.
3. **Bugs / correctness to fix** — edge cases where inherited methods,
   per-client configuration, or diagnostics can drift from the actual request.

The bias for V10: make multi-client contract reuse boring. If two concrete
clients inherit the same endpoint method, operators should be able to see which
client-specific base URL, timeout, headers, auth, resilience, and observability
policy applies without reading proxy internals.

Non-goals:

- Do not introduce class inheritance or abstract classes for client contracts.
- Do not add a second annotation model for inherited methods.
- Do not change the current timeout precedence contract.
- Do not merge per-client configuration blocks field-by-field unless a concrete
  compatibility story exists.
- Do not create a broad client-code generation feature.
- Do not make diagnostic logging noisier by default.

---

## 1. Features to add

### 1.1 Shared contract documentation and examples

**Why:** The starter already supports inherited endpoint methods, but the
supported pattern is easy to miss. A shared parent interface lets multiple
concrete clients reuse the same Java endpoint contract while keeping independent
client names and per-client configuration.

**What:**

- Promote the inherited-endpoint pattern in quick-start and annotation docs.
- Add one example where two concrete clients inherit the same parent endpoint
  interface and use different `request-timeout-ms` values.
- Clarify that `@ReactiveHttpClient` belongs on each concrete scanned client,
  not on the shared parent interface.
- Clarify that Java default methods remain local helpers and do not need HTTP
  metadata.

**Acceptance:**

- [ ] Docs show one parent operations interface and two concrete
      `@ReactiveHttpClient` children.
- [ ] The example proves each child uses its own
      `reactive.http.clients.<name>.request-timeout-ms`.
- [ ] Docs distinguish inherited abstract endpoint methods from Java default
      helper methods.
- [ ] Existing inherited-method validation and AOT smoke tests remain green.

---

### 1.2 Effective client-policy diagnostics

**Why:** Startup logs describe client-level settings and per-method resilience,
but multi-client contract reuse needs one concise way to confirm the effective
policy for an inherited endpoint under each concrete client name.

**What:**

- Add DEBUG startup diagnostics that include inherited endpoint methods with the
  declaring interface and concrete client name.
- Include effective request timeout source: method `@TimeoutMs`, `@ApiRef`
  timeout, client `request-timeout-ms`, deprecated alias, or disabled.
- Include effective base URL source and whether the endpoint method came from a
  parent interface.
- Keep diagnostics disabled unless DEBUG logging is enabled.

**Acceptance:**

- [ ] A child client inheriting `getUser(...)` logs the concrete client name and
      parent method declaration at DEBUG.
- [ ] Two children inheriting the same method can report different effective
      request timeouts.
- [ ] Explicit `@TimeoutMs` still appears as the winning source when present.
- [ ] No new INFO/WARN noise is added for valid clients.

---

### 1.3 Mock helper coverage for inherited clients

**Why:** `MockReactiveHttpClient` supports annotated concrete interfaces, but
docs and examples focus on methods declared directly on the client. Shared
contracts should be equally easy to test.

**What:**

- Add a mock-client test and docs example for a concrete client that inherits
  endpoint methods from a parent interface.
- Verify the mock helper keeps the concrete `@ReactiveHttpClient.name()` in
  observer events, lifecycle contexts, and recorded exchanges.
- Keep unannotated-interface fallback behavior unchanged.

**Acceptance:**

- [ ] A mock proxy can call an inherited endpoint method successfully.
- [ ] Observer and lifecycle test hooks receive the concrete child client name.
- [ ] Recorded request metadata matches the inherited endpoint path and method.
- [ ] Existing mock helper behavior for directly declared methods is unchanged.

---

## 2. Features to optimize

### 2.1 Inherited endpoint startup validation messages

**Why:** V9 validates inherited endpoint methods at proxy construction. When a
shared parent contract is reused by many clients, validation failures should
make it clear which parent method is invalid and which concrete client exposed
it.

**What:**

- Audit validation errors for inherited methods.
- Include both the concrete client interface and method-declaring interface
  when that improves actionability.
- Preserve current exception types and avoid changing valid runtime behavior.

**Acceptance:**

- [ ] Blank `@PathVar`, unused path variables, missing path variables, and
      invalid `@ApiRef` metadata on inherited methods identify the parent
      method.
- [ ] The concrete child client name or type is present where proxy construction
      is the failing context.
- [ ] Directly declared method errors remain concise.

---

### 2.2 Configuration metadata examples for multi-client reuse

**Why:** Shared endpoint contracts often pair with near-duplicate per-client
configuration. Users need examples for the parts that are intentionally
per-client: base URL, timeout, default headers, auth provider, resilience
instances, and redirect following.

**What:**

- Add a concise multi-client YAML example to docs.
- Show which fields naturally vary per concrete client.
- Keep the example small enough to scan; link to detailed docs for auth,
  resilience, proxy/TLS, and redirects.

**Acceptance:**

- [ ] Docs include two clients sharing one Java interface contract with
      different base URLs and timeouts.
- [ ] The example links to timeout and resilience docs instead of duplicating
      full configuration reference tables.
- [ ] README remains concise.

---

### 2.3 API compatibility and release evidence upkeep

**Why:** V9 moved roadmap files into version directories and V10 may touch
public docs and helper APIs. Release evidence should stay current and easy to
run.

**What:**

- Keep API compatibility baseline pointed at the latest released artifact.
- Verify roadmap links after the directory cleanup.
- Keep native/release smoke docs aligned with the current project version.
- Add V10 changelog entries only when implementation begins.

**Acceptance:**

- [ ] `roadmaps/README.md` links to every roadmap subdirectory.
- [ ] No stale `ROADMAP_V*.md` links remain.
- [ ] Release commands still pass after any V10 code changes.

---

## 3. Bugs / correctness to fix

### 3.1 Effective timeout reporting for inherited endpoints

**Why:** Inherited endpoint methods can be shared while each concrete client has
its own `request-timeout-ms`. Any lifecycle, observer, debug, or docs wording
that implies the timeout belongs to the parent method rather than the concrete
client would be misleading.

**What:**

- Audit timeout diagnostics and lifecycle/observer contexts for inherited
  methods.
- Add focused tests proving two concrete clients inherit the same method but use
  different client-level request timeouts.
- Keep existing precedence unchanged:
  `@TimeoutMs` > `@ApiRef timeout-ms` > client `request-timeout-ms` >
  deprecated resilience timeout alias.

**Acceptance:**

- [ ] Two child clients sharing one inherited method can time out at different
      client-level durations.
- [ ] Observer and lifecycle metadata report the concrete client name for both.
- [ ] Docs and diagnostics describe the timeout as concrete-client policy.

---

### 3.2 Inherited `@ApiRef` parity

**Why:** A parent interface may declare an `@ApiRef` endpoint so each concrete
client can supply a different configured method, path, and timeout for the same
logical operation. Startup validation should make that pattern reliable.

**What:**

- Test inherited `@ApiRef` methods with two concrete clients that define the
  same API key differently.
- Verify path-variable validation uses the concrete client's configured API map.
- Verify observability `api.name` still follows
  `@ApiName` > `@ApiRef` > method name.

**Acceptance:**

- [ ] Two clients can inherit the same `@ApiRef("user-get")` method and resolve
      different configured paths.
- [ ] Missing or malformed API config fails for the concrete client that owns
      the bad configuration.
- [ ] Metrics, traces, lifecycle hooks, and observers keep the existing
      observability-name precedence.

---

### 3.3 Documentation link hygiene after roadmap restructuring

**Why:** Moving roadmaps into subdirectories reduces clutter, but stale links can
make release planning harder to follow.

**What:**

- Scan `roadmaps/`, README, docs, and changelog for stale flat roadmap links.
- Keep each checklist linked to its sibling roadmap.
- Keep cross-version references relative and explicit.

**Acceptance:**

- [ ] No stale flat roadmap-file references remain.
- [ ] Every roadmap checklist links to its sibling `ROADMAP.md`.
- [ ] `git diff --check` passes.

---

## Suggested priority order

1. Documentation link hygiene after roadmap restructuring.
2. Shared contract documentation and examples.
3. Effective timeout reporting for inherited endpoints.
4. Inherited `@ApiRef` parity.
5. Mock helper coverage for inherited clients.
6. Inherited endpoint startup validation messages.
7. Effective client-policy diagnostics.
8. Configuration metadata examples for multi-client reuse.
9. API compatibility and release evidence upkeep.
