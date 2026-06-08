# Roadmap V11 Execution Checklist

> Companion to [`ROADMAP.md`](ROADMAP.md). This file tracks execution;
> keep rationale and design discussion in the roadmap.

---

## Priority 1 — API Compatibility Baseline Guard

### [x] 3.1 API compatibility baseline guard
- [x] Add an explicit guard for the `api-compatibility` profile when
  `api.compatibility.baseline.version` equals `project.version`.
- [x] Make the guard fail before the compatibility comparison can resolve a
  current-reactor artifact as the old baseline.
- [x] Include a clear failure message that tells maintainers to choose the last
  published release as the baseline.
- [x] Document any release-maintainer override only if the implementation needs
  one.
- [x] Verify `mvn -Papi-compatibility -DskipTests verify` fails early when the
  baseline equals the current project version.
- [x] Verify normal compatibility checks still pass with the current published
  baseline.

---

## Priority 2 — Generated Documentation Link Validation

### [x] 3.2 Generated documentation link validation
- [x] Add a focused Markdown local-link validation test for README, docs,
  changelog, and roadmaps.
- [x] Validate relative links from roadmap subdirectories, including sibling
  links such as `ROADMAP.md` and `CHECKLIST.md`.
- [x] Validate common generated heading anchors.
- [x] Ignore external links for now unless an explicit networked link checker
  is added later.
- [x] Verify broken local Markdown links fail a normal test.
- [x] Verify existing README, docs, changelog, and roadmap links pass.

---

## Priority 3 — Configuration Metadata Drift for New Properties

### [x] 3.3 Configuration metadata drift for new properties
- [x] Audit `ReactiveHttpClientProperties` against generated configuration
  metadata.
- [x] Verify every documented `reactive.http.*` property exists in generated
  metadata.
- [x] Verify metadata descriptions cover redirect following, timeout aliases,
  diagnostics, observability, proxy, TLS, default headers, and resilience
  fields.
- [x] Verify deprecated aliases include replacement metadata where available.
- [x] Verify documented defaults match metadata defaults for timeout, redirect,
  observability, proxy, TLS, and resilience fields.
- [x] Keep the check fast enough for normal `mvn test`.

---

## Priority 4 — Effective Contract Export for Diagnostics

### [x] 1.1 Effective contract export for diagnostics
- [x] Add an internal effective-contract model for each declarative method.
- [x] Include concrete client name, declaring interface, Java method signature,
  effective HTTP method/path, API name, timeout source, resilience instances,
  redirect policy, and body repeatability classification.
- [x] Resolve `@ApiRef` entries against the concrete client's API map.
- [x] Identify inherited methods with both parent declaring interface and
  concrete child client.
- [x] Sanitize exported diagnostics so auth secrets, default header values,
  proxy credentials, and request bodies are absent.
- [x] Keep the model limited to test/support diagnostics unless a stronger
  public API use case is proven.
- [x] Add tests for direct methods, inherited methods, `@ApiRef`, and sanitized
  output.

---

## Priority 5 — Contract Snapshot Test Helper

### [x] 1.2 Contract snapshot test helper
- [x] Add a test helper that renders effective contracts as deterministic JSON
  or Markdown.
- [x] Keep method and client ordering stable across JVMs and runs.
- [x] Support filtering by client name and method name.
- [x] Distinguish two concrete clients that share a parent interface but use
  different base URLs, timeouts, or API-map paths.
- [x] Add docs showing a small snapshot test for shared parent contracts and
  `@ApiRef` clients.
- [x] Verify the helper can run without requiring a Spring context.

---

## Priority 6 — Startup Diagnostics Consistency

### [x] 2.3 Startup diagnostics consistency
- [x] Audit startup DEBUG diagnostics for consistent client and method
  identifiers.
- [x] Align field names across direct methods, inherited methods, and `@ApiRef`
  methods.
- [x] Keep retry safety, body repeatability, timeout source, base URL source,
  and redirect policy names stable.
- [x] Ensure `@ApiRef` diagnostics include the API key and effective
  method/path.
- [x] Verify valid clients emit no new INFO/WARN logs.
- [x] Add tests that fail on accidental field-name drift.

---

## Priority 7 — Generated Configuration Examples from Metadata

### [x] 2.1 Generated configuration examples from metadata
- [x] Add a docs test or generation check that verifies key configuration
  examples use valid property names.
- [x] Cover timeout, auth, resilience, proxy/TLS, redirect following, default
  headers, and API-map examples.
- [x] Keep generated checks focused and preserve narrative docs.
- [x] Verify docs fail fast when a referenced `reactive.http.*` property is not
  present in configuration metadata.
- [x] Verify existing multi-client reuse examples remain valid.
- [x] Keep the check fast enough for normal `mvn test`.

---

## Priority 8 — Runtime Diagnostics Provider

### [x] 1.3 Runtime diagnostics provider
- [x] Provide a bean-friendly diagnostics provider that returns sanitized client
  summaries.
- [x] Include client name, base URL source, timeout summary, resilience summary,
  auth mode summary, redirect following, and registered endpoint count.
- [x] Report inherited endpoint counts correctly.
- [x] Keep the provider independent from Spring Boot Actuator.
- [x] Document a small optional Actuator endpoint wrapper that applications can
  own.
- [x] Verify the provider does not expose secret values.
- [x] Verify the starter does not add a built-in endpoint or new web
  dependency.

---

## Priority 9 — Release Evidence Manifest

### [x] 2.2 Release evidence manifest
- [x] Add a small release evidence manifest under `target/` or generated docs
  output.
- [x] Capture project version, API compatibility baseline version, Java version,
  Spring Boot baseline, and pass/fail command names.
- [x] Make it obvious when the baseline equals the current reactor version.
- [x] Keep generated target files out of source by default.
- [x] Document how to attach or paste the manifest into release notes.
- [x] Verify `mvn test` can produce or verify the manifest.
- [x] Run `mvn test`.
- [x] Run `mvn -Papi-compatibility -DskipTests verify`.
- [x] Run API compatibility fixtures.
- [x] Run `git diff --check`.
