# Reactive HTTP Client — Roadmap V15

> **Status:** completed and released as `2.11.0` after V15
> production-hardening, diagnostics, auth, benchmark-audit, and release-readiness
> work.

V15 uses the benchmark and release-evidence system from V12 through V14 as a
decision tool. It should not become another performance-only roadmap. The
project now has enough features that the next valuable work is making production
behavior easier to inspect, safer to configure, and easier to test before users
ship clients to real downstreams.

V15 keeps the same three-bucket shape:

1. **Features to add** — operational diagnostics, optional health details,
   example-driven auth guidance, and safer test utilities for production-like
   policies.
2. **Features to optimize** — only optimize default or optional feature paths
   when V14/V15 evidence shows a persistent hotspot.
3. **Bugs / correctness to fix** — auth refresh edge cases, redirect visibility,
   streaming ownership, metadata drift, and release baseline mistakes.

Non-goals:

- Do not add an Actuator endpoint by default.
- Do not expose raw secrets, header values, proxy credentials, auth provider
  bean names, request bodies, or response bodies from diagnostics.
- Do not make benchmark review triggers normal CI hard gates.
- Do not add new public configuration unless there is a production workflow that
  cannot be solved with the existing model.
- Do not optimize optional features by weakening diagnostics correctness.

---

## 1. Runtime Operations and Diagnostics

### 1.1 Add optional diagnostics snapshot export helpers

**Why:** V11 added `ReactiveHttpClientDiagnosticsProvider`, but applications
still need to write their own adapter when they want to export a sanitized
snapshot to a support bundle, custom Actuator endpoint, or startup log.

**What:**

- Add a small helper that renders `ReactiveHttpClientDiagnosticsProvider`
  summaries as deterministic Markdown or JSON.
- Keep it library-local and explicit; do not auto-publish an endpoint.
- Preserve the existing sanitization contract.
- Include the project version, client count, endpoint count, inherited endpoint
  count, auth mode, redirect flag, timeout source summary, and resilience
  summary.
- Add focused tests that prove secrets and concrete base URL values are not
  emitted.

**Acceptance:**

- [ ] Applications can render a sanitized diagnostics snapshot with one public
      helper call.
- [ ] Output is stable enough for support artifacts and approval-style tests.
- [ ] Output never includes auth secrets, header values, proxy credentials,
      provider bean names, request bodies, or response bodies.
- [ ] Existing `ReactiveHttpClientDiagnosticsProvider` behavior remains source
      compatible.

---

### 1.2 Improve health indicator troubleshooting detail

**Why:** The health indicator reports client status from Micrometer data, but
operators need enough detail to understand whether a DOWN state is based on
insufficient samples, high error rate, or missing metrics.

**What:**

- Add sanitized health details for each evaluated client: sample count, error
  count, error rate, threshold, and reason.
- Keep details bounded by client name and avoid URL/header/body values.
- Preserve the current `reactive.http.observability.health.enabled` switch and
  missing-Actuator behavior.
- Document how health indicator detail differs from exchange logs, observers,
  and runtime diagnostics snapshots.

**Acceptance:**

- [ ] Health details explain UP/DOWN/unknown reasons without exposing request
      data.
- [ ] Health remains disabled when `reactive.http.observability.health.enabled`
      is `false`.
- [ ] Tests cover no-sample, below-threshold, and above-threshold cases.
- [ ] Documentation keeps the provider-vs-health-vs-exchange-log boundary clear.

---

### 1.3 Add startup configuration summary logging

**Why:** DEBUG startup diagnostics are useful but method-level output can be too
noisy for large client sets. Operators need a concise INFO-optional summary when
reviewing deployment configuration.

**What:**

- Add an opt-in startup summary that logs one sanitized line per client.
- Include client name, interface, endpoint count, inherited endpoint count,
  auth mode, timeout source, resilience enabled/disabled summary, redirect flag,
  and observability enabled/disabled summary.
- Keep the default disabled unless the existing logging level already makes it
  explicit.
- Use the same sanitization rules as diagnostics snapshots.

**Acceptance:**

- [ ] Startup summary is opt-in or DEBUG-only; it does not add default log noise.
- [ ] Summary output is deterministic and sanitized.
- [ ] Summary data agrees with `ReactiveHttpClientDiagnosticsProvider`.
- [ ] Documentation explains when to use startup summary versus runtime
      diagnostics.

---

## 2. Auth and Security Hardening

### 2.1 Harden OAuth2 token refresh diagnostics

**Why:** Built-in OAuth2 client credentials support is production-critical. When
token acquisition fails, users need actionable diagnostics without leaking
client secrets or token response bodies.

**What:**

- Review token acquisition failure paths for status, category, retryability, and
  sanitized message quality.
- Ensure token endpoint response bodies stay bounded and redacted in logs and
  exceptions.
- Document `auth-style: form-post`, expiry leeway, cache behavior, and 401
  invalidation with a real client example.
- Add tests for token endpoint 4xx, 5xx, malformed JSON, missing access token,
  and refresh-after-leeway behavior.

**Acceptance:**

- [ ] OAuth2 failures include enough sanitized context to debug configuration
      and downstream failures.
- [ ] No client secret, access token, refresh token, or raw token response body is
      exposed.
- [ ] Documentation includes a complete YAML example for client credentials
      injection.
- [ ] Existing auth provider extension points remain source compatible.

---

### 2.2 Audit AWS SigV4 and raw-body signing contracts

**Why:** V7 fixed publisher upload signing, but signing behavior is high risk and
should stay explicitly covered as request-body optimizations continue.

**What:**

- Add contract tests for scalar JSON, byte array, string, empty, publisher, and
  streaming upload bodies under SigV4.
- Verify content hash behavior agrees with bytes on the wire for supported body
  shapes.
- Keep unsupported non-repeatable signing behavior explicit and documented.
- Avoid buffering large or streaming bodies just to make signing convenient.

**Acceptance:**

- [ ] SigV4 tests cover every supported body shape.
- [ ] Publisher uploads do not silently sign an empty payload unless the request
      body is actually empty.
- [ ] Unsupported body shapes fail or warn with a clear contract.
- [ ] No new buffering path is added for large streaming uploads.

---

### 2.3 Add auth-aware mock helper assertions

**Why:** `MockReactiveHttpClient` can assert outbound requests, but auth
behaviors such as generated `Authorization` headers, 401 invalidation, and retry
boundaries are common production test cases.

**What:**

- Add helper assertions for presence/absence of auth headers after filters run.
- Add a compact test path for one 401 invalidation followed by a retried request.
- Preserve redaction defaults in failure messages.
- Keep helpers independent from any specific OAuth2 server implementation.

**Acceptance:**

- [ ] Tests can assert that an auth header was added without printing its value.
- [ ] Tests can simulate a 401 invalidation flow.
- [ ] Helper failure messages redact sensitive headers.
- [ ] Existing mock helper behavior remains source compatible.

---

## 3. Inherited Contracts, Redirect, Streaming, and Body Ownership Hardening

### 3.1 Resolve inherited generic endpoint response types

**Why:** The starter supports inherited abstract endpoint methods, but generic
parent contracts such as `ApiOperators<T extends BaseResponse>` need the
decoder target resolved against each concrete child interface. Otherwise
`Mono<T>` can be decoded using the parent method's erased bound instead of the
child binding, causing runtime casts from `BaseResponse` to `BusResponse` or
`TrainResponse`.

**What:**

- Resolve inherited method return types with the concrete client interface as
  generic context.
- Cover `Mono<T>`, `Flux<T>`, `Mono<ResponseEntity<T>>`, and nested parameterized
  response bodies declared on shared parent interfaces.
- Keep inherited endpoint validation, diagnostics, and snapshots aligned with
  the concrete effective type.
- Document the supported shared generic contract pattern and call out Java
  generic mistakes such as binding a train client to `ApiOperators<BusResponse>`.

**Acceptance:**

- [ ] `BusApiOperators extends ApiOperators<BusResponse>` decodes successful
      responses as `BusResponse`.
- [ ] `TrainApiOperators extends ApiOperators<TrainResponse>` decodes successful
      responses as `TrainResponse`.
- [ ] Shared generic inherited endpoints keep per-client base URL, timeout,
      auth, resilience, observability, and `@ApiRef` behavior.
- [ ] Tests cover concrete decoding and avoid relying on caller-side casts.

---

### 3.2 Expand redirect-following contract coverage

**Why:** V9 added opt-in redirect following. Production behavior depends on
method, body repeatability, cross-authority sensitive headers, and Reactor Netty
transport behavior.

**What:**

- Add integration tests for `301`, `302`, `303`, `307`, and `308` when
  `follow-redirects` is enabled.
- Verify default visible-3xx behavior remains unchanged.
- Document method/body replay limits, especially for `POST`, `PATCH`, and
  streaming uploads.
- Verify observer and exchange-log final request fields describe the documented
  URL when redirects are followed.

**Acceptance:**

- [ ] Default 3xx responses remain visible to proxy callers.
- [ ] Opt-in redirect following works for supported safe cases.
- [ ] Sensitive headers are not promised across cross-authority redirects.
- [ ] Docs clearly distinguish original declarative request from final transport
      request metadata.

---

### 3.3 Re-audit streaming response ownership

**Why:** Streaming response support is easy to regress when optimizing response
envelopes, diagnostics, or timeout reporting.

**What:**

- Re-run real `WebClient` streaming ownership tests for
  `Flux<DataBuffer>` and `Mono<ResponseEntity<Flux<DataBuffer>>>`.
- Add cancellation tests that verify discarded buffers are released when the
  starter owns them and not released when the subscriber owns them.
- Verify lifecycle, observer, and exchange-log completion semantics still match
  the documented envelope boundary.
- Avoid adding body buffering to collect diagnostics.

**Acceptance:**

- [ ] Streaming bodies remain consumable after the response envelope is emitted.
- [ ] Cancellation and discard paths release only starter-owned buffers.
- [ ] Diagnostics completion does not imply the inner stream was consumed.
- [ ] Timeout docs remain accurate for streaming responses.

---

### 3.4 Clarify bodiless and unexpected-body contracts

**Why:** Bodiless responses were hardened in V9, but users still hit downstreams
that send unexpected bodies on `204`, `205`, `HEAD`, or `ResponseEntity<Void>`.

**What:**

- Audit `Mono<Void>`, `Mono<ResponseEntity<Void>>`, `HEAD`, and `OPTIONS`
  bodiless handling with real `ClientResponse` paths.
- Verify unexpected successful bodies are drained or released as documented.
- Ensure error bodies are still retained only within configured caps.
- Document the difference between draining for connection reuse and exposing a
  response body to callers.

**Acceptance:**

- [ ] Bodiless success paths return pooled connections when possible.
- [ ] Error body capture caps and truncation metadata remain unchanged.
- [ ] Docs explain why unexpected success bodies are not exposed.
- [ ] Tests cover both empty and unexpected-body responses.

---

## 4. Compatibility, Metadata, and Release Discipline

### 4.1 Prepare the next release baseline transition

**Why:** V14 made baseline movement release-aware. V15 should use that process
when moving beyond `2.10.0`, instead of rediscovering the sequence during the
next release.

**What:**

- Decide whether V15 is a patch or minor release after scope is finalized.
- If the next version is `2.10.1`, keep API compatibility baseline on `2.9.0`
  unless the release policy intentionally moves patch baselines.
- If the next version is `2.11.0`, move the baseline to published `2.10.0` only
  after `2.10.0` artifacts resolve.
- Update benchmark published-baseline commands and report paths with the same
  baseline property.

**Acceptance:**

- [ ] Release docs name the chosen version and baseline sequence.
- [ ] Self-comparison remains rejected for root and module-scoped compatibility
      builds.
- [ ] Baseline artifact resolution covers starter, test, and OTel modules.
- [ ] Benchmark baseline commands use the same published baseline version.

---

### 4.2 Tighten configuration metadata drift checks

**Why:** The project now has many nested properties. Metadata drift can mislead
IDE completion, generated docs, and examples even when runtime binding works.

**What:**

- Keep group metadata source types pointed at declaring configuration classes.
- Validate scalar examples against property metadata only, not group names.
- Ensure OTel metadata is validated on the OTel module classpath.
- Add checks that every documented property has metadata and every metadata
  property appears in the generated reference.

**Acceptance:**

- [ ] Starter and OTel metadata validation run on classpaths that can resolve
      their source types.
- [ ] YAML and `.properties` examples fail tests when assigning scalars to
      groups.
- [ ] Generated configuration docs stay reproducible from metadata.
- [ ] New properties cannot ship without descriptions and defaults where
      applicable.

---

### 4.3 Add release readiness snapshot for docs, metadata, and benchmarks

**Why:** Release evidence exists, but maintainers still inspect multiple files to
understand whether docs, metadata, API baseline, and benchmark report versions
agree.

**What:**

- Extend the release evidence manifest with a concise readiness summary.
- Include project version, API baseline, promoted benchmark report path,
  generated config reference status, link validation status, and pending manual
  commands.
- Keep the manifest under `target/release-evidence/`.
- Do not mark manual benchmark or compatibility commands as passed unless the
  maintainer actually runs them.

**Acceptance:**

- [ ] Release evidence has a top-level readiness summary.
- [ ] Summary distinguishes generated test evidence from manual release evidence.
- [ ] Missing promoted reports or stale version links are visible in one place.
- [ ] `target/` evidence remains uncommitted.

---

## 5. Evidence-Guided Optimization Follow-Up

### 5.1 Re-run default and optional feature benchmark audits after V14

**Why:** V14 performed post-`2.10.0` audits. V15 should only optimize if fresh
evidence shows persistent movement on the same machine and same scenario pair.

**What:**

- Run current-vs-published benchmark pairs for default success path, optional
  diagnostics, error mapping, and auth-enabled scenarios if they are added.
- Use V13 review triggers as review-only signals.
- Record before/after evidence for every optimization.
- Prefer removing redundant work over adding caches that complicate correctness.

**Acceptance:**

- [ ] Every optimization has a named before/after benchmark row.
- [ ] No benchmark result becomes a normal CI hard gate.
- [ ] Optional feature rows are not compared with baselines that do not perform
      equivalent work.
- [ ] Public docs keep claims scenario-specific.

---

### 5.2 Audit observer and lifecycle overhead with multiple observers/hooks

**Why:** Real applications often register more than one observer or lifecycle
hook. The default path is optimized, but optional diagnostics should also remain
predictable when enabled.

**What:**

- Benchmark one observer, multiple observers, one lifecycle hook, and multiple
  ordered hooks.
- Inspect per-call allocation in `ObjectProvider.orderedStream()` and composite
  observer construction.
- Reuse immutable observer/hook snapshots when safe.
- Preserve dynamic bean behavior only where Spring semantics require it.

**Acceptance:**

- [ ] Optional diagnostics benchmark rows cover multiple observer/hook cases.
- [ ] Any caching respects Spring bean ordering and replacement semantics.
- [ ] Observer failure isolation remains unchanged.
- [ ] Lifecycle hook ordering tests still pass.

---

## Suggested Priority Order

1. Prepare the next release baseline transition.
2. Add optional diagnostics snapshot export helpers.
3. Improve health indicator troubleshooting detail.
4. Harden OAuth2 token refresh diagnostics.
5. Add auth-aware mock helper assertions.
6. Resolve inherited generic endpoint response types.
7. Expand redirect-following contract coverage.
8. Re-audit streaming response ownership.
9. Clarify bodiless and unexpected-body contracts.
10. Tighten configuration metadata drift checks.
11. Add startup configuration summary logging.
12. Add release readiness snapshot for docs, metadata, and benchmarks.
13. Audit AWS SigV4 and raw-body signing contracts.
14. Re-run default and optional feature benchmark audits after V14.
15. Audit observer and lifecycle overhead with multiple observers/hooks.
