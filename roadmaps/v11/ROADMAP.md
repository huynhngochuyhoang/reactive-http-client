# Reactive HTTP Client — Roadmap V11

> **Status:** completed and released as `2.9.0`. V11 focused on contract
> drift prevention, generated reference material, and production diagnostics that
> make declarative client behavior easier to audit before and after release.

V11 keeps the same three-bucket shape:

1. **Features to add** — small surfaces that expose effective client contracts
   and make them easier to review.
2. **Features to optimize** — generated evidence and diagnostics that reduce
   manual release and operations work.
3. **Bugs / correctness to fix** — edge cases where documented contracts,
   generated metadata, or runtime diagnostics can drift from actual behavior.

The bias for V11: make the starter's effective contract inspectable. If a
client interface, inherited parent method, `@ApiRef`, timeout, resilience
policy, auth provider, redirect policy, or generated metadata changes, the
project should make that visible through tests, docs, startup diagnostics, or
release evidence without requiring users to read internals.

Non-goals:

- Do not add a new annotation model.
- Do not introduce OpenAPI generation or a full schema registry.
- Do not change request/response runtime behavior only to improve docs.
- Do not expose sensitive configured values in generated output or logs.
- Do not make INFO/WARN startup logs noisier for valid configurations.
- Do not remove deprecated compatibility aliases before a planned major release.

---

## 1. Features to add

### 1.1 Effective contract export for diagnostics

**Why:** V10 made inherited client policy visible in DEBUG logs. Operators and
tests also need a stable, structured way to inspect the effective contract for a
client without parsing log lines.

**What:**

- Add an internal effective-contract model for each declarative method.
- Include concrete client name, declaring interface, Java method signature,
  effective HTTP method/path, API name, timeout source, resilience instances,
  redirect policy, and body repeatability classification.
- Keep the model sanitized: no auth secrets, default header values, proxy
  credentials, or request body content.
- Expose the model only through test/support diagnostics unless a stronger
  public API use case is proven.

**Acceptance:**

- [ ] A concrete client with directly declared methods can produce effective
      contract entries.
- [ ] A concrete client with inherited methods can produce entries that identify
      both the parent declaring interface and child client.
- [ ] `@ApiRef` entries resolve against the concrete client's API map.
- [ ] Sensitive configured values are absent from exported diagnostics.

---

### 1.2 Contract snapshot test helper

**Why:** Multi-client reuse is easy to break accidentally when a parent endpoint
or per-client config changes. Tests should be able to snapshot the effective
contract and compare it in review.

**What:**

- Add a test helper that can render effective contracts as deterministic JSON or
  Markdown.
- Keep ordering stable across JVMs and runs.
- Support filtering by client name and method name.
- Document how to use snapshots for shared parent contracts and `@ApiRef`
  clients.

**Acceptance:**

- [ ] Snapshot output is deterministic for directly declared methods.
- [ ] Snapshot output is deterministic for inherited methods.
- [ ] Snapshot output distinguishes two concrete clients that share the same
      parent interface but use different base URLs, timeouts, or API-map paths.
- [ ] Docs show a small test example without requiring a Spring context.

---

### 1.3 Runtime diagnostics provider

**Why:** In production, teams often need to confirm what the starter registered
after configuration binding and auto-configuration. A lightweight provider can
feed Spring Boot Actuator or custom diagnostics without making the starter depend
on Actuator.

**What:**

- Provide a bean-friendly diagnostics provider that returns sanitized client
  summaries.
- Include client name, base URL source, timeout summary, resilience summary,
  auth mode summary, redirect following, and registered endpoint count.
- Keep it independent from Spring Boot Actuator.
- Document a small optional Actuator endpoint wrapper that applications can own.

**Acceptance:**

- [ ] Applications can inject the diagnostics provider when the starter is
      enabled.
- [ ] The provider does not expose secret values.
- [ ] The provider reports inherited endpoint counts correctly.
- [ ] The starter does not add a built-in endpoint or new web dependency.

---

## 2. Features to optimize

### 2.1 Generated configuration examples from metadata

**Why:** Configuration docs are easy to let drift from
`spring-configuration-metadata.json`. V11 should reduce manual sync work for
release evidence.

**What:**

- Add a docs test or small generation check that verifies key configuration
  examples use valid property names.
- Cover timeout, auth, resilience, proxy/TLS, redirect following, default
  headers, and API-map examples.
- Keep generated checks focused; do not replace narrative docs.

**Acceptance:**

- [ ] Docs fail fast when a referenced `reactive.http.*` property does not exist
      in configuration metadata.
- [ ] Existing examples for multi-client reuse remain valid.
- [ ] The check is fast enough for normal `mvn test`.

---

### 2.2 Release evidence manifest

**Why:** V8-V10 added release smoke, API compatibility, native compatibility, and
roadmap evidence. The commands are documented, but the release result is still
manual prose.

**What:**

- Add a small release evidence manifest under `target/` or docs-generated output.
- Capture project version, API baseline version, Java version, Spring Boot
  baseline, and pass/fail command names.
- Keep the manifest out of source unless explicitly promoted during release.
- Document how to attach or paste the manifest into release notes.

**Acceptance:**

- [ ] `mvn test` can produce or verify the release evidence manifest.
- [ ] The manifest includes the API compatibility baseline and current project
      version.
- [ ] The manifest makes it obvious when the baseline equals the reactor version.
- [ ] No generated target files are committed by default.

---

### 2.3 Startup diagnostics consistency

**Why:** Diagnostics now cover startup configuration, per-method resilience, and
inherited method policy. The wording and fields should stay consistent so users
can search logs reliably.

**What:**

- Audit startup DEBUG diagnostics for consistent client/method identifiers.
- Align method identifiers across direct methods, inherited methods, and
  `@ApiRef` methods.
- Keep retry safety, body repeatability, timeout source, base URL source, and
  redirect policy names stable.
- Add tests that fail on accidental field-name drift.

**Acceptance:**

- [ ] Direct-method diagnostics and inherited-method diagnostics use compatible
      field names.
- [ ] `@ApiRef` diagnostics include the API key and effective method/path.
- [ ] Valid clients still emit no new INFO/WARN logs.

---

## 3. Bugs / correctness to fix

### 3.1 API compatibility baseline guard

**Why:** If the API compatibility baseline equals the current reactor version
before a release-version bump, Maven can resolve the old artifact from the
current reactor or local build and compare the new jar to itself.

**What:**

- Add an explicit guard that fails the `api-compatibility` profile when
  `api.compatibility.baseline.version` equals `project.version`.
- Allow an override only for a documented release-maintainer workflow if needed.
- Keep docs aligned with the guard.

**Acceptance:**

- [ ] `mvn -Papi-compatibility -DskipTests verify` fails early when baseline and
      current project version are equal.
- [ ] The failure message explains how to choose the correct published baseline.
- [ ] Normal compatibility checks still pass with the current published baseline.

---

### 3.2 Generated documentation link validation

**Why:** Roadmap restructuring and expanding docs make stale links likely. V10
verified links manually; V11 should make this repeatable.

**What:**

- Add a focused docs-link validation test for local Markdown links.
- Cover README, docs, changelog, and roadmaps.
- Ignore external links unless the project later adds an explicit networked link
  checker.
- Keep path handling compatible with roadmap subdirectories.

**Acceptance:**

- [ ] Broken local Markdown links fail a normal test.
- [ ] Links with anchors are validated for common heading anchors.
- [ ] Roadmap sibling links such as `ROADMAP.md` and `CHECKLIST.md` pass from
      subdirectories.

---

### 3.3 Configuration metadata drift for new properties

**Why:** New properties such as redirect following, timeout aliases, and
diagnostic policy fields should appear consistently in configuration metadata
and docs.

**What:**

- Audit all `ReactiveHttpClientProperties` fields against generated metadata.
- Add coverage for descriptions, default values, and deprecation metadata.
- Verify docs do not mention properties that metadata hides or renames.

**Acceptance:**

- [ ] Every documented `reactive.http.*` property exists in generated metadata.
- [ ] Deprecated aliases include replacement metadata where available.
- [ ] Defaults in docs match metadata for timeout, redirect, observability,
      proxy, TLS, and resilience fields.

---

## Suggested priority order

1. API compatibility baseline guard.
2. Generated documentation link validation.
3. Configuration metadata drift for new properties.
4. Effective contract export for diagnostics.
5. Contract snapshot test helper.
6. Startup diagnostics consistency.
7. Generated configuration examples from metadata.
8. Runtime diagnostics provider.
9. Release evidence manifest.
