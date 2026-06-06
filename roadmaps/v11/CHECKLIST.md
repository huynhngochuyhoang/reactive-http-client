# Roadmap V11 Execution Checklist

> Companion to [`ROADMAP.md`](ROADMAP.md). This file tracks execution;
> keep rationale and design discussion in the roadmap.

---

## Priority 1 — API Compatibility Baseline Guard

### [ ] 3.1 API compatibility baseline guard
- [ ] Add an explicit guard for the `api-compatibility` profile when
  `api.compatibility.baseline.version` equals `project.version`.
- [ ] Make the guard fail before the compatibility comparison can resolve a
  current-reactor artifact as the old baseline.
- [ ] Include a clear failure message that tells maintainers to choose the last
  published release as the baseline.
- [ ] Document any release-maintainer override only if the implementation needs
  one.
- [ ] Verify `mvn -Papi-compatibility -DskipTests verify` fails early when the
  baseline equals the current project version.
- [ ] Verify normal compatibility checks still pass with the current published
  baseline.

---

## Priority 2 — Generated Documentation Link Validation

### [ ] 3.2 Generated documentation link validation
- [ ] Add a focused Markdown local-link validation test for README, docs,
  changelog, and roadmaps.
- [ ] Validate relative links from roadmap subdirectories, including sibling
  links such as `ROADMAP.md` and `CHECKLIST.md`.
- [ ] Validate common generated heading anchors.
- [ ] Ignore external links for now unless an explicit networked link checker
  is added later.
- [ ] Verify broken local Markdown links fail a normal test.
- [ ] Verify existing README, docs, changelog, and roadmap links pass.

---

## Priority 3 — Configuration Metadata Drift for New Properties

### [ ] 3.3 Configuration metadata drift for new properties
- [ ] Audit `ReactiveHttpClientProperties` against generated configuration
  metadata.
- [ ] Verify every documented `reactive.http.*` property exists in generated
  metadata.
- [ ] Verify metadata descriptions cover redirect following, timeout aliases,
  diagnostics, observability, proxy, TLS, default headers, and resilience
  fields.
- [ ] Verify deprecated aliases include replacement metadata where available.
- [ ] Verify documented defaults match metadata defaults for timeout, redirect,
  observability, proxy, TLS, and resilience fields.
- [ ] Keep the check fast enough for normal `mvn test`.

---

## Priority 4 — Effective Contract Export for Diagnostics

### [ ] 1.1 Effective contract export for diagnostics
- [ ] Add an internal effective-contract model for each declarative method.
- [ ] Include concrete client name, declaring interface, Java method signature,
  effective HTTP method/path, API name, timeout source, resilience instances,
  redirect policy, and body repeatability classification.
- [ ] Resolve `@ApiRef` entries against the concrete client's API map.
- [ ] Identify inherited methods with both parent declaring interface and
  concrete child client.
- [ ] Sanitize exported diagnostics so auth secrets, default header values,
  proxy credentials, and request bodies are absent.
- [ ] Keep the model limited to test/support diagnostics unless a stronger
  public API use case is proven.
- [ ] Add tests for direct methods, inherited methods, `@ApiRef`, and sanitized
  output.

---

## Priority 5 — Contract Snapshot Test Helper

### [ ] 1.2 Contract snapshot test helper
- [ ] Add a test helper that renders effective contracts as deterministic JSON
  or Markdown.
- [ ] Keep method and client ordering stable across JVMs and runs.
- [ ] Support filtering by client name and method name.
- [ ] Distinguish two concrete clients that share a parent interface but use
  different base URLs, timeouts, or API-map paths.
- [ ] Add docs showing a small snapshot test for shared parent contracts and
  `@ApiRef` clients.
- [ ] Verify the helper can run without requiring a Spring context.

---

## Priority 6 — Startup Diagnostics Consistency

### [ ] 2.3 Startup diagnostics consistency
- [ ] Audit startup DEBUG diagnostics for consistent client and method
  identifiers.
- [ ] Align field names across direct methods, inherited methods, and `@ApiRef`
  methods.
- [ ] Keep retry safety, body repeatability, timeout source, base URL source,
  and redirect policy names stable.
- [ ] Ensure `@ApiRef` diagnostics include the API key and effective
  method/path.
- [ ] Verify valid clients emit no new INFO/WARN logs.
- [ ] Add tests that fail on accidental field-name drift.

---

## Priority 7 — Generated Configuration Examples from Metadata

### [ ] 2.1 Generated configuration examples from metadata
- [ ] Add a docs test or generation check that verifies key configuration
  examples use valid property names.
- [ ] Cover timeout, auth, resilience, proxy/TLS, redirect following, default
  headers, and API-map examples.
- [ ] Keep generated checks focused and preserve narrative docs.
- [ ] Verify docs fail fast when a referenced `reactive.http.*` property is not
  present in configuration metadata.
- [ ] Verify existing multi-client reuse examples remain valid.
- [ ] Keep the check fast enough for normal `mvn test`.

---

## Priority 8 — Runtime Diagnostics Provider

### [ ] 1.3 Runtime diagnostics provider
- [ ] Provide a bean-friendly diagnostics provider that returns sanitized client
  summaries.
- [ ] Include client name, base URL source, timeout summary, resilience summary,
  auth mode summary, redirect following, and registered endpoint count.
- [ ] Report inherited endpoint counts correctly.
- [ ] Keep the provider independent from Spring Boot Actuator.
- [ ] Document a small optional Actuator endpoint wrapper that applications can
  own.
- [ ] Verify the provider does not expose secret values.
- [ ] Verify the starter does not add a built-in endpoint or new web
  dependency.

---

## Priority 9 — Release Evidence Manifest

### [ ] 2.2 Release evidence manifest
- [ ] Add a small release evidence manifest under `target/` or generated docs
  output.
- [ ] Capture project version, API compatibility baseline version, Java version,
  Spring Boot baseline, and pass/fail command names.
- [ ] Make it obvious when the baseline equals the current reactor version.
- [ ] Keep generated target files out of source by default.
- [ ] Document how to attach or paste the manifest into release notes.
- [ ] Verify `mvn test` can produce or verify the manifest.
- [ ] Run `mvn test`.
- [ ] Run `mvn -Papi-compatibility -DskipTests verify`.
- [ ] Run API compatibility fixtures.
- [ ] Run `git diff --check`.
