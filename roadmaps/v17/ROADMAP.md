# Reactive HTTP Client — Roadmap V17

> **Status:** draft after V16 completion. V16 prepared the `2.12.0` candidate
> line with opt-in diagnostics, strict validation, generated examples,
> compatibility coverage, and release readiness. V17 should start only after the
> V16 release evidence decision is clear.

V16 closed a long production-hardening loop. The starter now has diagnostics,
strict validation, benchmark evidence, inherited generic contracts, support
bundle guidance, and a wider compatibility promise. V17 should not add another
large feature sweep by default. The next useful work is release discipline,
adoption evidence, and guardrails that keep the new public surface stable.

V17 keeps three priorities in balance:

1. **Release completion** — ship the V16 scope cleanly as `2.12.0` or explicitly
   defer release-only evidence when no public performance claims are made.
2. **Adoption confidence** — make the new strict, diagnostics, and support-bundle
   workflows easier to verify in real applications without exposing secrets.
3. **Compatibility governance** — keep documented public helpers, replacement
   beans, test helpers, and generated examples aligned with japicmp and docs.

Non-goals:

- Do not add new default logging, Actuator endpoints, tracing, health detail, or
  body capture.
- Do not publish `2.12.0` performance claims without a clean promoted benchmark
  report for `2.12.0`.
- Do not broaden strict validation defaults; strict modes remain opt-in.
- Do not expose concrete base URLs, secrets, request bodies, response bodies, or
  raw sensitive header values in support artifacts.
- Do not add another HTTP abstraction or replace Spring `WebClient`.
- Do not add public API unless there is a documented user workflow and japicmp
  coverage in the same change.

---

## 0. Release Assessment for V16

### 0.1 Release V16 as `2.12.0` when evidence is ready

**Decision:** V16 should be released as a minor version if its opt-in diagnostics
endpoint, strict validation modes, generated effective-configuration examples,
and expanded public compatibility promise are shipped.

**Why:** The V16 scope adds user-visible configuration, Actuator integration,
documentation workflows, and stricter startup validation. Even with compatible
default behavior, this is more than a patch-only correction.

**Patch release would fit only if:**

- The opt-in diagnostics endpoint and strict validation features were held back.
- The release contained only compatibility include corrections, docs updates, and
  bug fixes.
- No new configuration metadata or public helper surface was shipped.

**Release actions before tagging:**

- Keep `api.compatibility.baseline.version` on `2.11.0` until `2.12.0` is
  published and resolvable.
- Keep V16 changelog content under `Unreleased` until release prep starts, then
  cut a dated `2.12.0` section.
- Run API compatibility against published `2.11.0` and the module-scoped starter
  compatibility command.
- Run the compatibility fixture script and full reactor tests.
- If release notes include performance claims, run a clean release benchmark,
  promote `docs/benchmark-report-2.12.0.md`, and cite only that promoted report.
- If release notes do not include performance claims, leave the missing promoted
  `2.12.0` report visible in release readiness and avoid performance wording.

**Acceptance:**

- [ ] Release decision is recorded as minor or patch before tagging.
- [ ] Changelog, README, quick start, Maven versions, and release evidence agree
      on the selected version.
- [ ] API compatibility compares `2.12.0` against published `2.11.0`.
- [ ] Performance claims, if any, cite a clean promoted `2.12.0` benchmark
      report.
- [ ] No target-only release evidence is committed as source-controlled proof.

---

## 1. Post-Release Baseline and Evidence Transition

### 1.1 Move the baseline only after `2.12.0` is published

**Why:** The project has repeatedly caught self-comparison risks around japicmp
and benchmark baselines. After `2.12.0` ships, the next development line must
compare against the published `2.12.0` artifacts, not the current reactor or an
older baseline.

**What:**

- Resolve published `2.12.0` artifacts for starter, test-helper, and OTel
  modules before changing the baseline.
- Move the reactor to the next development version only after the release branch
  is cut.
- Move `api.compatibility.baseline.version` to `2.12.0` only after artifacts
  resolve from the configured repositories.
- Update benchmark published-baseline commands and report paths to
  `published-starter-2.12.0`.
- Keep root and module-scoped self-comparison guard checks failing as expected.

**Acceptance:**

- [ ] Published `2.12.0` starter, test-helper, and OTel artifacts resolve.
- [ ] The next development reactor version does not equal the API baseline.
- [ ] Root and module-scoped self-comparison guard checks still reject the
      current reactor version.
- [ ] Benchmark docs, release evidence, and performance summary name the same
      published baseline version.

---

## 2. Diagnostics Endpoint and Support Bundle Adoption

### 2.1 Stabilize the opt-in diagnostics endpoint contract

**Why:** V16 added the opt-in `rhttpclients` Actuator endpoint. It must remain
safe, bounded, and predictable before users rely on it in production support
bundles.

**What:**

- Re-audit endpoint enablement, endpoint id, Actuator exposure examples, and
  behavior when Actuator is absent.
- Verify the endpoint output matches `ReactiveHttpClientDiagnosticsSnapshot`
  sanitization and does not drift from the helper contract.
- Add focused tests for multiple clients, inherited generic endpoints, disabled
  clients, strict validation modes, and missing optional dependencies.
- Document the expected capture workflow together with health details and startup
  summaries.

**Acceptance:**

- [ ] The endpoint is absent unless explicitly enabled and exposed.
- [ ] Endpoint output is sanitized identically to diagnostics snapshots.
- [ ] Missing Actuator classes do not affect normal starter startup.
- [ ] Support-bundle docs explain endpoint output versus health, logs, observers,
      and exchange logging.

---

### 2.2 Add production support-bundle regression fixtures

**Why:** Support bundle docs are useful, but examples can drift from real output
or accidentally collect unsafe data.

**What:**

- Add approval-style fixtures for sanitized diagnostics snapshot output.
- Add support-bundle examples that combine diagnostics JSON, health details,
  startup summaries, exchange-log category settings, and benchmark report links.
- Validate that examples never include real-looking hostnames, tokens, secret
  names, request bodies, response bodies, or raw sensitive header values.
- Keep fixtures small enough to be reviewed in normal PRs.

**Acceptance:**

- [ ] Support-bundle examples are generated or tested against current public
      APIs and configuration metadata.
- [ ] Snapshot fixtures are deterministic across runs.
- [ ] Sensitive fields are redacted or absent in every example.
- [ ] Documentation tests fail when support-bundle links or property names drift.

---

## 3. Strict Validation Adoption and False-Positive Audit

### 3.1 Audit strict unsafe-retry validation against real configuration shapes

**Why:** Strict retry validation is intentionally conservative. V17 should prove
that common production patterns can adopt it without false positives, while risky
contracts still fail before traffic is sent.

**What:**

- Test strict validation with inherited endpoints, `@ApiRef`, generated
  idempotency keys, Reactor-context keys, default headers, dynamic header maps,
  overloaded methods, and disabled retry instances.
- Improve startup error messages only when a real adoption path is confusing.
- Keep warning-only behavior unchanged when strict mode is disabled.
- Add migration guidance for teams enabling strict mode one client at a time.

**Acceptance:**

- [ ] Strict retry validation fails only when a retry can actually duplicate an
      unsafe request without a startup-provable idempotency key.
- [ ] Idempotent methods, generated keys, and non-retrying configurations do not
      fail strict mode.
- [ ] Dynamic headers that can remove or override `Idempotency-Key` remain
      treated as not startup-provable.
- [ ] Docs show a safe incremental rollout pattern.

---

### 3.2 Audit strict body-signing validation for built-in AWS SigV4

**Why:** Body signing is security-sensitive and easy to misdocument. Strict mode
should fail ambiguous built-in SigV4 contracts without blocking custom providers
that intentionally support more body shapes.

**What:**

- Re-test byte array, UTF-8 string, JSON DTO, absent body, non-JSON content type,
  dynamic content type, erased `Object`, publisher, resource, multipart, and Java
  stream bodies.
- Verify custom `AuthProviderFactory` and named custom providers are not rejected
  by built-in SigV4 assumptions.
- Document the boundary between starter-auth serialization bytes and customized
  WebClient codecs.
- Keep large or streaming body buffering out of the starter.

**Acceptance:**

- [ ] Strict SigV4 validation accepts only startup-provable built-in signing
      shapes.
- [ ] Runtime behavior remains compatible when strict mode is disabled.
- [ ] Custom auth providers are not rejected by built-in SigV4 rules.
- [ ] Docs make codec-alignment requirements explicit for JSON signing.

---

## 4. Public API and Compatibility Governance

### 4.1 Add a documented-public-surface audit guard

**Why:** V16 exposed several compatibility include gaps after the public API audit
was marked complete. V17 should make that kind of drift harder to repeat.

**What:**

- Build a small release-doc test or manifest section that lists documented public
  helper types and the japicmp include pattern that protects each one.
- Cover annotations, exceptions, auth extension points, exchange logging,
  lifecycle hooks, diagnostics provider/snapshot, contract snapshot, sensitive
  header helpers, method metadata cache/model, resilience hook, test helpers, and
  OTel public types.
- Keep implementation internals excluded unless they are explicitly documented as
  replacement or extension surfaces.
- Document how to add a new public helper without missing compatibility coverage.

**Acceptance:**

- [ ] Each documented public helper has a matching japicmp include pattern or a
      documented exclusion reason.
- [ ] The release-doc test fails when a documented helper is missing from the
      compatibility include map.
- [ ] Internal classes are not accidentally promoted because they appear in docs
      as implementation detail examples.
- [ ] The compatibility guide explains the include-map maintenance workflow.

---

### 4.2 Review public constructors and mutable models for long-term support

**Why:** Several public helper models were added for diagnostics, metadata, and
snapshots. Once they are compatibility-covered, accidental public mutability or
constructors become harder to change.

**What:**

- Review public constructors, setters, nested classes, enums, and return models
  for `MethodMetadata*`, diagnostics snapshots, contract snapshots, and test
  helper records.
- Where behavior is intentionally internal but currently public, document it as
  compatibility-covered or prepare a major-version deprecation path.
- Add javadocs or docs only where a public type is meant to be implemented or
  instantiated by users.
- Avoid breaking changes in the current minor line.

**Acceptance:**

- [ ] Every compatibility-covered type has an intentional support story.
- [ ] No public type is removed or narrowed in V17.
- [ ] Any questionable public surface is documented as supported, deprecated, or
      reserved for a future major release.
- [ ] API compatibility fixtures still catch representative removals.

---

## 5. Documentation, Examples, and Migration Paths

### 5.1 Add a V16-to-V17 adoption guide

**Why:** Users now have many optional production tools. They need a practical
order for enabling them without turning every diagnostic feature on at once.

**What:**

- Add a short guide for adopting V16 features after upgrade: diagnostics snapshot,
  health details, support bundle, strict retry, strict body signing, and
  generated effective-configuration examples.
- Show safe defaults first, then opt-in validation modes.
- Explain when not to enable a feature, especially for dynamic idempotency keys
  and custom body-signing providers.
- Link to quick start, auth, resilience, observability, support bundle,
  benchmarks, and native compatibility docs.

**Acceptance:**

- [ ] The guide gives an ordered rollout path for existing applications.
- [ ] The guide separates diagnostics collection from strict startup validation.
- [ ] Examples use metadata-backed configuration keys.
- [ ] Documentation link validation covers the guide.

---

### 5.2 Improve example app coverage without creating a new framework

**Why:** Docs now contain many snippets, but users benefit from one compact
copyable configuration that demonstrates common production policy together.

**What:**

- Add one small example package or documentation page that combines inherited
  clients, OAuth2 client credentials, timeout policy, retry with idempotency key,
  strict validation, diagnostics endpoint, and support bundle capture.
- Keep values fake and obviously non-production.
- Do not add a runnable sample service unless it is needed for tests.
- Validate snippets against configuration metadata.

**Acceptance:**

- [ ] The example uses fake hostnames and placeholder credentials only.
- [ ] The example composes existing features without introducing new public API.
- [ ] Configuration snippets pass metadata validation.
- [ ] Docs explain which pieces are optional.

---

## 6. Benchmark and Runtime Evidence Follow-Up

### 6.1 Promote or explicitly defer the `2.12.0` benchmark report

**Why:** V16 release readiness intentionally reports `docs/benchmark-report-2.12.0.md`
as missing until a clean release benchmark is promoted. V17 should resolve that
state before adding new performance wording.

**What:**

- If `2.12.0` release notes include performance claims, run the clean benchmark
  command from release evidence and promote `docs/benchmark-report-2.12.0.md`.
- Pair the current report with published `2.11.0` baseline evidence.
- If no performance claims are made, document that the report is intentionally
  deferred and keep public performance docs tied to the latest promoted report.
- Do not update older promoted reports in place.

**Acceptance:**

- [ ] The release-readiness state for `docs/benchmark-report-2.12.0.md` is either
      present with clean provenance or intentionally deferred with no public
      performance claims.
- [ ] Current and published-baseline report paths remain distinct.
- [ ] Benchmark docs do not cite target-only reports as release evidence.
- [ ] Changelog performance wording, if any, cites the promoted report.

---

### 6.2 Re-audit diagnostics and strict-mode overhead only if adoption needs it

**Why:** V12 through V16 added benchmark coverage. V17 should avoid speculative
optimization, but the new diagnostics endpoint and strict-mode paths may need
focused evidence if users report overhead or startup delays.

**What:**

- Keep normal benchmark smoke and release runs available.
- Add or refresh no-network audit rows only when they isolate a real question.
- Separate startup validation cost, request-path cost, and endpoint rendering
  cost in any benchmark or measurement.
- Prefer correctness and sanitization over tiny optional-path performance gains.

**Acceptance:**

- [ ] Any new benchmark row has an explicit prefix classification.
- [ ] Optional diagnostics rows are not mixed into raw-client comparison tables.
- [ ] No optimization is made without repeatable named-row evidence.
- [ ] Performance docs keep claims scoped to measured scenarios.

---

## 7. Native, AOT, and Dependency Baseline Readiness

### 7.1 Re-audit native hints for V16 public and configuration surfaces

**Why:** V16 added configuration objects, endpoint behavior, public helpers, and
metadata-backed examples. Native support should stay boring for users who enable
AOT builds.

**What:**

- Verify runtime hints cover diagnostics endpoint configuration, support snapshot
  version metadata resources, inherited client proxies, public annotations, and
  configuration property binding.
- Add native smoke coverage when a new public or configuration surface requires
  reflection or resources.
- Keep optional Actuator and OTel behavior conditional.
- Document any native limitation explicitly.

**Acceptance:**

- [ ] AOT smoke tests cover the V16 public/configuration additions that need
      hints.
- [ ] Optional dependencies remain optional in native and JVM builds.
- [ ] Native docs do not promise unsupported Actuator or OTel behavior.
- [ ] Runtime hints stay scoped to real runtime needs.

---

### 7.2 Prepare dependency-baseline review for the next Spring Boot line

**Why:** The quick start targets Spring Boot 3.5.x and Java 21. V17 should keep
compatibility expectations explicit before a future dependency baseline change.

**What:**

- Audit Spring Boot, Reactor Netty, Micrometer, Resilience4j, OpenTelemetry, and
  test dependency versions used by the current BOM.
- Document which dependency upgrades are compatibility-neutral and which require
  a minor release.
- Keep benchmark dependency metadata recording the resolved versions.
- Avoid upgrading baselines as part of unrelated feature work.

**Acceptance:**

- [ ] Dependency baseline docs name the current Spring Boot and Java support
      policy.
- [ ] Benchmark reports continue to record dependency-management source.
- [ ] Any baseline upgrade has a dedicated release note and compatibility check.
- [ ] No dependency drift bypasses generated metadata or release evidence tests.

---

## 8. Release Readiness for the Next Line

### 8.1 Keep V17 release evidence source-controlled and current

**Why:** Release readiness is now one of the project strengths. V17 should keep
that discipline without turning every manual benchmark into a normal CI gate.

**What:**

- Keep changelog entries under `Unreleased` while V17 work is active.
- Keep generated configuration docs current.
- Keep Markdown link validation passing across docs and roadmaps.
- Keep API compatibility, module-scoped compatibility, fixture, benchmark smoke,
  release benchmark, and published-baseline commands listed in release evidence.
- Decide patch versus minor after scope is finalized.

**Acceptance:**

- [ ] Release evidence names the selected next version and API baseline.
- [ ] Changelog entries stay under `Unreleased` until release prep starts.
- [ ] Generated docs and Markdown links pass before marking V17 complete.
- [ ] Full tests, API compatibility, and `git diff --check` pass before release
      prep is considered ready.
