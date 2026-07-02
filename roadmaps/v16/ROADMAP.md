# Reactive HTTP Client — Roadmap V16

> **Status:** completed for the `2.12.0` candidate line.

V15 finished the production-hardening cycle around diagnostics, auth refresh
failure handling, inherited generic endpoint types, streaming ownership,
benchmark evidence, and release readiness. V16 should avoid another large
feature sweep. The project now needs a smaller adoption and operations pass:
make the `2.11.0` behavior easier to consume, add opt-in integrations where the
manual helper surface is too repetitive, and strengthen release evidence for
the next baseline transition.

V16 keeps three priorities in balance:

1. **Operational adoption** — optional integrations and examples that turn the
   diagnostics and benchmark work into production workflows.
2. **Production guardrails** — stricter opt-in validation for risky but currently
   warning-only contracts.
3. **Release discipline** — clean baseline transition after `2.11.0`, evidence
   reproducibility, and public API compatibility upkeep.

Non-goals:

- Do not enable new endpoints, logging, tracing, or health output by default.
- Do not expose concrete base URLs, secrets, header values, request bodies, or
  response bodies from diagnostics or support artifacts.
- Do not turn benchmark review thresholds into normal CI hard gates.
- Do not add a second HTTP abstraction or replace Spring `WebClient`.
- Do not optimize based on one benchmark run; only act on repeatable named rows.

---

## 1. Post-Release Baseline and Evidence Transition

### 1.1 Move the compatibility baseline after `2.11.0` is published

**Why:** V15 prepared `2.11.0` against published `2.10.0`. After `2.11.0`
artifacts are published and resolvable, the next development line must compare
against `2.11.0` instead of the older baseline.

**What:**

- Resolve published `2.11.0` artifacts for starter, test, and OTel modules.
- Move `api.compatibility.baseline.version` to `2.11.0` only after artifacts
  resolve.
- Update benchmark published-baseline commands and paths to
  `published-starter-2.11.0`.
- Keep self-comparison rejected for root and module-scoped compatibility runs.
- Update release docs and roadmap evidence with the exact transition commands.

**Acceptance:**

- [x] Published `2.11.0` starter, test, and OTel artifacts resolve.
- [x] API compatibility compares the next reactor version against `2.11.0`.
- [x] Root and module-scoped self-comparison guard checks still fail as expected.
- [x] Benchmark docs and release evidence point at the same baseline version.

---

### 1.2 Preserve clean benchmark provenance for promoted reports

**Why:** V15 exposed that benchmark evidence is weak if it points at a dirty
tree. Future promoted reports should be traceable to an immutable clean commit
without relying on manual review.

**What:**

- Add a release-doc check that rejects promoted benchmark reports whose
  `benchmarkCommit` contains `dirty`, `unknown`, or is absent.
- Check that the promoted report commit matches a short Git SHA-like value.
- Keep target-only generated reports free to record local paths, but require
  source-controlled promoted reports to use sanitized relative paths.
- Document the clean-commit benchmark sequence in `docs/22-benchmarks.md`.

**Acceptance:**

- [x] Promoted reports with dirty or unknown benchmark commits fail tests.
- [x] Promoted reports with machine-local paths fail tests.
- [x] Release benchmark docs show the clean-commit command sequence.
- [x] Existing historical reports remain valid unless they are current-release
      evidence.

---

## 2. Optional Operational Integrations

### 2.1 Add an opt-in Actuator diagnostics endpoint

**Why:** V15 added explicit snapshot helpers. Many Spring Boot applications will
still write the same small Actuator adapter to expose sanitized client summaries
to support teams.

**What:**

- Add a conditional Actuator endpoint that is disabled by default.
- Reuse `ReactiveHttpClientDiagnosticsSnapshot` and
  `ReactiveHttpClientDiagnosticsProvider`.
- Support JSON output first; Markdown can remain a helper-only format unless
  Actuator conventions make it natural.
- Keep endpoint output sanitized and bounded by the existing snapshot contract.
- Document how this endpoint differs from health details, exchange logs, and
  startup summaries.

**Acceptance:**

- [x] No Actuator endpoint is registered unless explicitly enabled.
- [x] Output matches the existing sanitized diagnostics snapshot contract.
- [x] Endpoint tests prove secrets, header values, body values, auth provider
      bean names, and concrete base URLs are absent.
- [x] Missing Actuator dependencies keep the starter behavior unchanged.

---

### 2.2 Add production support bundle examples

**Why:** Diagnostics snapshots, health details, startup summaries, exchange logs,
and benchmark reports now exist, but users need concrete examples for collecting
the right evidence during incidents.

**What:**

- Add a support-bundle documentation page that shows which artifacts to capture
  for configuration issues, auth failures, retry behavior, timeout incidents,
  streaming ownership issues, and performance investigations.
- Include copyable examples for diagnostics snapshot export, health details,
  log categories, and benchmark evidence links.
- Keep examples sanitized and avoid instructing users to collect raw request or
  response bodies by default.

**Acceptance:**

- [x] Docs describe a minimal safe support bundle for common incident categories.
- [x] Examples use existing public APIs and configuration keys.
- [x] Documentation link validation covers the new page.

---

## 3. Strict Production Guardrails

### 3.1 Add opt-in strict mode for unsafe retry contracts

**Why:** The starter warns about unsafe retry for non-idempotent methods without
an `Idempotency-Key`. Some production teams prefer startup failure over warning
logs for these contracts.

**What:**

- Add an opt-in strict validation mode for unsafe retry diagnostics.
- Fail startup when retry is actually available and an unsafe method is retryable
  without an effective idempotency key.
- Preserve the current warning-only default.
- Respect existing retry operator availability checks so no-op retry wiring does
  not fail startup.
- Document when to use strict mode and how generated/context/default idempotency
  keys satisfy the guard.

**Acceptance:**

- [x] Default behavior remains warning-only.
- [x] Strict mode fails startup only for retry paths that can actually retry.
- [x] Idempotent methods and effective idempotency keys do not fail strict mode.
- [x] Tests cover overloaded methods and inherited endpoints.

---

### 3.2 Add opt-in strict mode for ambiguous outbound body signing

**Why:** V15 clarified SigV4 raw-body signing contracts. Production users may
want startup-time enforcement for unsupported multipart, streaming, or
charset-sensitive signing combinations instead of runtime failures.

**What:**

- Add strict validation for built-in body-signing auth providers when method
  metadata shows unsupported body shapes.
- Keep custom auth providers source-compatible and avoid guessing whether every
  custom provider signs bodies.
- Preserve default runtime behavior for existing applications.
- Document supported and unsupported body shapes with strict-mode examples.

**Acceptance:**

- [x] Default behavior remains source and behavior compatible.
- [x] Strict mode rejects known unsupported built-in signing shapes before the
      first request.
- [x] Tests cover JSON, byte array, String with charset, publisher, multipart,
      and empty-body paths.
- [x] Documentation does not promise signing parity when codecs are customized
      beyond the signer bytes.

---

## 4. Configuration and Contract Usability

### 4.1 Add generated effective-configuration examples

**Why:** The diagnostics provider reports effective policies, but users still
learn configuration by reading hand-written YAML snippets that can drift from
metadata.

**What:**

- Generate or validate examples for common client configurations: inherited
  shared interface, OAuth2 client credentials, AWS SigV4, proxy/TLS, redirect
  following, strict retry, and diagnostics endpoint.
- Keep examples metadata-backed where possible.
- Add tests that reject scalar assignments to configuration groups and stale
  property names.

**Acceptance:**

- [x] Examples bind against current configuration metadata.
- [x] Strict-mode examples include both valid and invalid snippets.
- [x] Docs clearly separate starter, OTel, and benchmark module properties.

---

### 4.2 Improve startup validation messages for generic inherited contracts

**Why:** V15 fixed concrete type resolution for inherited generic endpoints.
When users bind the wrong child type, diagnostics should explain the generic
mapping instead of only showing a decoded type mismatch later.

**What:**

- Improve startup diagnostics and snapshot output for inherited generic endpoint
  bindings.
- Include parent interface, concrete client interface, type variable, resolved
  type, and endpoint method where useful.
- Add examples for correct and incorrect generic client declarations.
- Avoid adding runtime casts or special cases for impossible Java generic
  declarations.

**Acceptance:**

- [x] Diagnostics explain `ApiOperators<T>` to `BusResponse` style bindings.
- [x] Incorrect generic declarations fail or report with actionable context.
- [x] Existing inherited endpoint behavior remains compatible.

---

## 5. Benchmark and Performance Follow-Up

### 5.1 Re-audit ResponseEntity and JSON rows after `2.11.0`

**Why:** The clean `2.11.0` report shows `Post Json` and `Response Entity`
slower than both baselines in that run. These rows need repeatable evidence
before any optimization work.

**What:**

- Run current-vs-published benchmark comparisons on the same machine after
  `2.11.0` is published.
- Repeat the `Post Json` and `Response Entity` rows enough to distinguish noise
  from a persistent regression.
- Inspect allocation deltas and profiler output only if the movement repeats.
- Prefer removing redundant work over adding caches.

**Acceptance:**

- [x] Any optimization proposal cites named before/after benchmark rows.
- [x] No public claim is made from smoke output or one noisy release run.
- [x] If no repeatable issue appears, document that no optimization was made.

---

### 5.2 Separate no-network diagnostics audit output from release feature claims

**Why:** V15 corrected no-network diagnostics row classification. V16 should
make this contract harder to regress in future benchmark additions.

**What:**

- Add report-generation tests for every benchmark prefix category.
- Document naming conventions for loopback feature rows versus no-network audit
  rows.
- Keep release summaries focused on comparable loopback rows and starter-only
  rows with clear labels.

**Acceptance:**

- [x] New benchmark prefixes must have an explicit label contract.
- [x] No-network rows cannot appear in the optional-feature summary table.
- [x] Documentation explains how to interpret no-network diagnostics rows.

---

## 6. API Compatibility and Release Readiness

### 6.1 Expand public API baseline coverage for new V15/V16 helpers

**Why:** V15 added public support helpers and test-helper behavior. V16 should
make sure every documented public helper is covered by the compatibility include
set.

**What:**

- Audit the japicmp include list against documented public types.
- Include new diagnostics snapshot, diagnostics provider, auth exception, mock
  helper, and endpoint-related public types as appropriate.
- Keep internal implementation classes out of the public API promise.

**Acceptance:**

- [x] Every documented public class or method has compatibility coverage or an
      explicit reason for exclusion.
- [x] Compatibility tests pass against the current published baseline.
- [x] Release docs describe any intentionally unsupported internal types.

---

### 6.2 Prepare release readiness for the next minor or patch

**Why:** After V15, release readiness is strong but still manual. V16 should keep
that evidence current without adding brittle automation.

**What:**

- Keep release evidence commands aligned with the selected next version.
- Verify promoted benchmark links, generated configuration docs, Markdown links,
  baseline artifact resolution, and API compatibility commands.
- Update changelog under `Unreleased` as V16 priorities complete.
- Decide patch versus minor only after scope is known.

**Acceptance:**

- [x] Release readiness evidence names the selected version and baseline.
- [x] Changelog entries stay under `Unreleased` until release prep starts.
- [x] Release tests pass before marking V16 complete.

