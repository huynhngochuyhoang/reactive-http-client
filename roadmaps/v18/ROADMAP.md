# Reactive HTTP Client — Roadmap V18

> **Status:** draft after the `2.13.0` release cut. V17 completed the
> post-`2.12.0` baseline transition, diagnostics/support-bundle stabilization,
> strict-mode adoption audits, native hint re-audit, and dependency-baseline
> readiness. V18 should stay focused on the next baseline transition, release
> automation gaps, and feedback-driven hardening rather than adding a large new
> feature set.

The starter is now broad enough that unchecked feature growth is the main risk.
V18 should protect the production contracts already in place: diagnostics must
stay sanitized, strict validation must stay opt-in and explainable, benchmark
claims must stay tied to release-quality reports, and API compatibility must
continue to compare against a published baseline.

V18 keeps four priorities in balance:

1. **Post-release discipline** — move the reactor and compatibility baseline only
   after `2.13.0` artifacts are published and resolvable.
2. **Release automation** — reduce manual release drift without turning
   compatibility, benchmarks, or publishing into hidden magic.
3. **Operational adoption** — improve examples and troubleshooting only where
   the current diagnostics/support-bundle workflow is hard to apply.
4. **Contract maintenance** — keep public API, configuration metadata, native
   hints, and benchmark evidence aligned with the documented surface.

Non-goals:

- Do not add default runtime logging, tracing, health details, or Actuator
  exposure.
- Do not enable strict validation by default.
- Do not publish performance claims without a clean promoted benchmark report
  for the release being discussed.
- Do not add broad new HTTP abstractions or replace Spring `WebClient`.
- Do not expose secrets, provider bean names, raw sensitive headers, concrete
  base URLs, request bodies, or response bodies from support artifacts.
- Do not upgrade the Java or Spring Boot baseline as incidental cleanup.

---

## 1. Post-`2.13.0` Baseline Transition

### 1.1 Move the next development line only after `2.13.0` resolves

**Why:** V17 cut `2.13.0` changelog content while the reactor still declared
`2.13.0`. The next development line must avoid API self-comparison and benchmark
baseline confusion.

**What:**

- Resolve published `2.13.0` artifacts for starter, test-helper, and OTel.
- Move the reactor and module parents to the next development version only after
  those artifacts resolve.
- Move `api.compatibility.baseline.version` to `2.13.0` only after publication.
- Update benchmark published-baseline commands and report paths to
  `published-starter-2.13.0`.
- Keep root and module-scoped self-comparison guard checks failing for the new
  reactor version.
- Update README, quick start, changelog links, release docs, and release
  evidence together.

**Acceptance:**

- [x] Published `2.13.0` starter, test-helper, and OTel artifacts resolve.
- [x] The next reactor version does not equal the API compatibility baseline.
- [x] Root and module-scoped self-comparison guards reject the current reactor
      version.
- [x] Benchmark docs, release evidence, and performance summary name the same
      published baseline version.
- [x] Changelog `Unreleased` compare links start at `v2.13.0`.

---

## 2. Release Automation and Evidence Guardrails

### 2.1 Add a release-prep consistency checklist that is generated, not guessed

**Why:** The project now has many release evidence requirements. Maintainers
should not have to remember which commands, links, promoted reports, and baseline
artifacts apply to a release.

**What:**

- Extend the generated release evidence manifest with a concise release-prep
  checklist for the current project version.
- Keep manual commands visible rather than auto-running benchmarks or publishing.
- Include changelog section status, version snippet status, baseline artifact
  resolution commands, compatibility commands, benchmark commands, promoted
  report status, and generated-doc/link status.
- Keep target-only generated manifests out of source-controlled release proof.

**Acceptance:**

- [x] Release evidence has one checklist-style summary for the current version.
- [x] The checklist names manual commands without hiding them behind automation.
- [x] Documentation tests fail if changelog, version snippets, baseline commands,
      or promoted report paths drift.
- [x] No generated `target/` evidence is committed.

---

### 2.2 Re-audit changelog and release-note performance wording

**Why:** Benchmark docs are strict, but release notes are where stale or broad
performance wording can slip in.

**What:**

- Validate current-release changelog performance wording against promoted report
  availability.
- Reject current-release benchmark links that point at missing or target-only
  reports.
- Keep historical release sections valid without rewriting old links.
- Document how to write a no-performance-claim release note when no benchmark is
  promoted.

**Acceptance:**

- [x] Current-release performance claims require a source-controlled promoted
      benchmark report.
- [x] Historical changelog sections can retain historical report links.
- [x] Target-only benchmark paths are rejected in public release notes.
- [x] Releases without performance claims have clear allowed wording.

---

## 3. Operational Adoption Follow-Up

### 3.1 Add support-bundle capture examples for common deployment shapes

**Why:** V17 stabilized support-bundle docs. The next useful adoption step is to
show how teams collect the same sanitized evidence in common environments
without inventing new APIs.

**What:**

- Add examples for local JVM, container, and Kubernetes-style capture commands.
- Keep examples generic and fake; do not use real-looking hosts, tokens, or
  organization names.
- Show health details, `rhttpclients`, startup summaries, metadata-only exchange
  logs, and benchmark report references as separate evidence streams.
- Validate every documented `reactive.http.*` property against metadata.

**Acceptance:**

- [x] Examples are sanitized and use placeholder endpoints only.
- [x] Configuration snippets pass metadata validation.
- [x] The docs explain which evidence stream answers which support question.
- [x] No new public API is introduced for examples alone.

---

### 3.2 Re-audit strict validation adoption messages

**Why:** Strict retry and built-in SigV4 body-signing validation are intentionally
conservative. Users need startup failures that explain the remediation without
weakening the guard.

**What:**

- Review strict validation failures for inherited endpoints, `@ApiRef`, dynamic
  headers, disabled retries, custom auth providers, and dynamic content types.
- Improve message text only where remediation is unclear.
- Keep warning-only runtime behavior unchanged when strict modes are disabled.
- Add docs for deciding between method annotations, client defaults, and custom
  provider ownership.

**Acceptance:**

- [x] Strict-mode failures identify the client, method, unsafe condition, and
      remediation path.
- [x] Custom providers are not rejected by built-in validation assumptions.
- [x] Disabled/no-op retry configurations do not fail strict retry validation.
- [x] Docs keep strict modes opt-in and incremental.

---

## 4. Contract and Metadata Maintenance

### 4.1 Keep public API coverage aligned with documented helper usage

**Why:** The documented public surface is now wider: diagnostics snapshots,
contract snapshots, redaction helpers, metadata cache replacement, test helpers,
and OTel companion types. Future drift must be caught before release.

**What:**

- Re-run the documented public surface map against japicmp includes.
- Add fixtures for any newly documented helper before release.
- Keep public nested builder/client types covered when docs expose fluent APIs.
- Keep internal implementation classes out of compatibility promises.

**Acceptance:**

- [x] Every documented public helper has an explicit compatibility story.
- [x] Japicmp includes cover documented nested public APIs.
- [x] Compatibility fixtures catch representative incompatible removals.
- [x] Docs avoid presenting internal classes as replacement surfaces.

---

### 4.2 Re-audit configuration metadata and native hints after release prep

**Why:** V16 and V17 added property, diagnostics, and native-hint guardrails. A
release-prep pass can still miss a new nested configuration type or documented
property string.

**What:**

- Verify generated configuration reference matches metadata.
- Verify documented `reactive.http.*` names exist in metadata.
- Verify runtime hints cover public nested configuration property types.
- Verify optional Actuator and OTel behavior remains conditional.

**Acceptance:**

- [x] Generated configuration reference is current.
- [x] Public docs do not mention non-existent `reactive.http.*` properties.
- [x] AOT smoke tests cover any new runtime hint needs.
- [x] Optional integrations remain optional in JVM and native builds.

---

## 5. Benchmark and Dependency Baseline Review

### 5.1 Keep benchmark evidence scoped to release decisions

**Why:** The benchmark harness is valuable when it answers a release question.
V18 should avoid expanding it without a named scenario and a reason to publish or
audit the result.

**What:**

- Keep smoke, release, published-baseline, and comparison commands current.
- Add benchmark rows only when they isolate a changed request path, optional
  feature path, startup validation path, or support endpoint rendering path.
- Keep no-network rows classified separately from loopback feature rows.
- Keep review thresholds manual, not normal CI hard gates.

**Acceptance:**

- [ ] Benchmark docs name current and published-baseline commands for the active
      release line.
- [ ] Any new benchmark row has an explicit classification.
- [ ] Public performance claims cite promoted release-quality reports only.
- [ ] Benchmark threshold crossings remain manual review triggers.

---

### 5.2 Prepare dependency baseline review without upgrading by default

**Why:** The project is pinned to Java 21 and Spring Boot 3.5.x. V18 can prepare
for a future dependency move, but it should not combine that with unrelated
feature work.

**What:**

- Review Spring Boot 3.5.x patch movement and managed WebFlux/Reactor
  Netty/Micrometer/OpenTelemetry versions.
- Review Resilience4j baseline compatibility and optional dependency behavior.
- Keep benchmark reports recording dependency-management source.
- Document any proposed baseline upgrade separately from feature work.

**Acceptance:**

- [ ] Dependency docs still name Java 21 and Spring Boot 3.5.x support.
- [ ] Versionless module dependencies continue to inherit managed versions.
- [ ] Proposed baseline upgrades have separate release notes and compatibility
      checks.
- [ ] No dependency drift bypasses generated release evidence.

---

## 6. V18 Release Readiness

### 6.1 Keep V18 small enough to release confidently

**Why:** V17 was already a release-discipline roadmap. V18 should finish with
clear evidence rather than a broad backlog of partially verified improvements.

**What:**

- Keep changelog entries under `Unreleased` while V18 work is active.
- Keep generated configuration docs and Markdown links passing.
- Keep full tests, API compatibility, module-scoped compatibility, fixture
  checks, and diff checks green before marking V18 complete.
- Run benchmark release evidence only when V18 changes request-path behavior,
  optional-feature overhead, or public performance claims.
- Decide patch versus minor after implementation scope is final.

**Acceptance:**

- [ ] Release evidence names the selected next version and API baseline.
- [ ] Changelog entries stay under `Unreleased` until release prep starts.
- [ ] Generated docs and Markdown links pass.
- [ ] Full tests, API compatibility, and `git diff --check` pass.
- [ ] Benchmark evidence is promoted or explicitly deferred based on release
      claims.
