# Reactive HTTP Client - Roadmap V23 Execution Checklist

Companion to [`ROADMAP.md`](ROADMAP.md). Execute priorities in order unless a
confirmed correctness or release blocker requires reordering. Check an item only
after implementation and verification evidence is recorded under that priority.
Generated evidence belongs under `target/release-evidence/` unless a promoted,
versioned artifact is explicitly required.

---

## Priority 1 - Post-`3.2.0` Baseline Integrity

### [x] 1.1 Align active version and baseline contracts

- [x] Keep root and module development coordinates on `3.3.0-SNAPSHOT`.
- [x] Keep public README, quick-start, migration, operations, and support examples
      on published `3.2.0`.
- [x] Keep API compatibility and benchmark baselines on published `3.2.0`.
- [x] Keep current-reactor consumer and native fixtures on `3.3.0-SNAPSHOT`.
- [x] Reject same-version API baselines in root and module-scoped builds.

### [x] 1.2 Prove complete published-bundle provenance

- [x] Resolve the parent POM plus starter, test-helper, and OTel POM/JAR artifacts
      from an absent Central-only repository.
- [x] Resolve source and Javadoc attachments for every published module.
- [x] Require Central markers and record SHA-256 values for every required file.
- [x] Resolve through a neutral consumer model so the current reactor cannot
      satisfy its own parent GAV.
- [x] Reject stale repositories, missing artifacts, local markers, unrelated
      version directories, mismatched POM versions, and mismatched binary versions.
- [x] Run strict root and starter-module japicmp against fresh `3.2.0` repositories.
- [x] Run focused release-documentation and provenance fixture tests.
- [x] Run the complete published `3.2.0` bundle verifier and `git diff --check`.

Evidence:

- Root and module coordinates remain `3.3.0-SNAPSHOT`; public dependency snippets,
  `latest.published.version`, API compatibility, consumer, and benchmark baselines
  remain published `3.2.0`. Explicit root and starter-module validation runs with
  `api.compatibility.baseline.version=3.3.0-SNAPSHOT` both failed at the inherited
  same-version guard as required.
- Hardened `verify-published-baseline-provenance.sh --release-artifacts` to compare
  each published POM's direct project version, falling back to its parent version
  only when the project version is omitted, and every binary JAR's embedded Maven
  `pom.properties` version with the requested baseline. Negative fixtures cover a
  mismatched parent-only module POM, a matching parent with a mismatched direct
  project version, and a mismatched binary while retaining stale repository, local
  marker, unrelated version, and missing attachment rejection.
- `scripts/verify-published-release-artifacts.sh 3.2.0` passed from an absent
  Central-only repository through the neutral Boot 4 consumer model. Evidence at
  `target/release-evidence/published-baselines/release-artifacts-3.2.0/` records
  Central markers and 13 SHA-256 values for the parent plus all module POM,
  binary, source, and Javadoc artifacts.
- Published binary SHA-256 values are starter
  `3ba861994c5dc913e08d88d8c3e2fe99464f15bd8c332ec5637a68d67980a72d`,
  test helper `3e1f01408ddb83456ffed63d6ede076eaac629e5c3129bd87bc94ea91a3d4058`,
  and OTel `714ea9ef2037d3e48f88552f084818d3e9f62467585a5228a5213048f483c143`.
- Fresh strict root and starter-module japicmp builds passed against published
  `3.2.0`; provenance is recorded under `api-root-3.2.0/` and
  `api-starter-3.2.0/`. Shell syntax, provenance fixtures, and
  `DocumentationReleaseArtifactTest` passed. `git diff --check` passed.

---

## Priority 2 - Logical Call, Subscription, and Dispatch Semantics

### [x] 2.1 Freeze existing attempt semantics

- [x] Document logical invocation, cold-publisher subscription, retry subscription,
      and outbound dispatch as separate boundaries.
- [x] Preserve the established `attemptCount` meaning across lifecycle, observer,
      exchange-log, diagnostics, and test-helper contracts.
- [x] Keep serialization, auth, and filter failures before dispatch distinguishable
      from requests that reached the connector.
- [x] Add compatibility tests for existing constructors and fields.

### [x] 2.2 Keep reporting state attempt- and subscription-local

- [x] Reset final-request and dispatch evidence before every retry or hidden auth replay.
- [x] Cover redirects and one-time OAuth2 `401` refresh without reusing prior dispatch state.
- [x] Cover concurrent subscriptions to the same cold `Mono` and `Flux`.
- [x] Add a bounded dispatch count only if existing contracts cannot express the
      proven behavior without ambiguity.
- [x] Run focused state-machine, retry, redirect, auth, and compatibility tests.

Evidence:

- `docs/21-diagnostic-contexts.md` now defines Java invocation, caller subscription,
  Resilience4j retry subscription, and outbound dispatch as separate boundaries.
  Existing attempt fields remain subscription counts, not wire-request counts.
- `SubscriptionState.resetAttemptEvidence()` is used for every resilience retry and
  as the reset callback before the auth filter's hidden `401` replay. It clears URL,
  final headers, response status/headers, and terminal error without changing the
  logical call's subscription-attempt count.
- Real redirect coverage proves two wire dispatches remain one subscription attempt
  and retain the original declarative request snapshot. Auth replay coverage proves
  the refreshed request replaces first-dispatch headers while keeping attempt count
  `1`; pre-dispatch serialization, auth, and custom-filter failures retain no false
  request/status evidence.
- No public dispatch count was added. Reactor Netty follows redirects below the
  `WebClient` filter boundary, so an exact cross-path count cannot be proven. Existing
  final-request evidence plus subscription-attempt count expresses the supported
  contract without adding a misleading field.
- Compatibility tests freeze legacy observer and exchange-log constructor defaults,
  canonical attempt fields, and lifecycle attempt numbering. Existing concurrent
  cold `Mono`, `Flux`, and streaming-envelope subscription tests passed.
- Focused state-machine, retry, redirect, auth, lifecycle, exchange-log, and
  compatibility suites passed. The full `reactive-http-client-starter` test suite
  and focused `MockReactiveHttpClientTest` / `Boot4MockReactiveHttpClientTest`
  suites also passed.

---

## Priority 3 - End-to-End Timeout Budget

### [x] 3.1 Define the opt-in logical-call budget

- [x] Preserve all existing timeout properties, precedence, and `0 = disabled` behavior.
- [x] Make any new budget monotonic and subscription-local.
- [x] Ensure retry, redirect, and auth refresh do not reset the budget.
- [x] Define interaction with connect, pool-acquire, request-write, response,
      method, and Resilience4j timeouts.

### [x] 3.2 Prove timeout ownership

- [x] Cover exhaustion before dispatch, between retries, in the pool queue, and
      during unary response consumption with real-clock fixtures.
- [x] Keep streaming-envelope acquisition separate from caller-owned body consumption.
- [x] Report only the final attempt's proven timeout phase.
- [x] Align lifecycle, observer, exchange-log, diagnostics, mock, metadata, and docs.

Evidence:

- Added opt-in per-client `logical-call-timeout-ms` with `0` disabled and bounded
  validation. The existing method/API/client/deprecated response-timeout precedence
  is unchanged. The new outer deadline starts once per cold-publisher subscription
  after invocation and does not restart for Resilience4j admission/retry, redirect,
  auth refresh, pool acquisition, or response consumption.
- `LogicalCallTimeoutException` retains `ErrorCategory.TIMEOUT` and carries only a
  proven final-attempt stage. An observed status proves `RESPONSE_BODY`; expiry
  before status remains unknown because URL/request creation does not distinguish
  pool queueing from a dispatched request waiting for headers. Prior-attempt state
  is cleared between retries and hidden auth replay, and compare-and-set cleanup
  prevents a late prior-attempt `doFinally` from clearing the active retry.
- Real-clock contracts cover auth expiry before dispatch, retry backoff, hidden
  `401` refresh, a saturated one-connection pool, redirect handling, unary response
  consumption, an active direct stream, and delayed caller-owned envelope body
  consumption. Additional cases prove `0` remains disabled and the existing native
  per-attempt response timeout wins when it expires first.
- Lifecycle hooks, observer events, and exchange-log contexts agree on terminal
  status, error, attempt count, and failure stage. Effective-contract and runtime
  diagnostics expose the separate budget, diagnostics schema v1 adds
  `logicalCallTimeoutMs`, and `MockReactiveHttpClient.logicalCallTimeout(...)`
  applies the production operator without claiming transport-phase simulation.
- `LogicalCallTimeoutBudgetContractTest` passed all 11 cases. The focused metadata,
  generated documentation, diagnostics, factory, contract snapshot, exporter, and
  mock-helper tests passed. `mvn -q -pl reactive-http-client-test -am test` passed
  the complete starter and test-helper suites, and `git diff --check` passed.

---

## Priority 4 - Protocol-Aware Pool Capacity

### [ ] 4.1 Distinguish connection and stream pressure

- [ ] Add real HTTP/1.1 and HTTP/2 bounded-capacity fixtures.
- [ ] Distinguish queued connection acquisition from HTTP/2 stream-capacity pressure
      only where Reactor Netty supplies proof.
- [ ] Report unknown instead of inferring unsupported distinctions.
- [ ] Keep metrics and support metadata bounded and address-free.

### [ ] 4.2 Prove pool ownership under pressure

- [ ] Cover cancellation and timeout of queued demand.
- [ ] Verify released capacity serves later requests without leaks or starvation.
- [ ] Verify factory destruction waits for owned resources and pending demand terminates.
- [ ] Align health, diagnostics, failure-stage, and operations guidance.

---

## Priority 5 - DNS, Proxy, TLS, and Connect Failure Attribution

### [ ] 5.1 Add deterministic pre-response fixtures

- [ ] Cover DNS resolution, proxy connection/tunnel, TLS handshake, connect timeout,
      and certificate failures where local fixtures can prove ownership.
- [ ] Preserve concrete pre-response stages without requiring response URL/status evidence.
- [ ] Prevent nested auth-provider and custom-filter errors from being promoted into
      business-request transport stages.
- [ ] Keep `ErrorCategory` backward compatible.

### [ ] 5.2 Align bounded failure evidence

- [ ] Expose only additive low-cardinality stage values.
- [ ] Align lifecycle, observer, exchange-log, health, diagnostics, and mock behavior.
- [ ] Add sanitized operations reproduction guidance for every supported stage.
- [ ] Run focused real-transport and compatibility tests.

---

## Priority 6 - Compression and Aggregate-Limit Boundaries

### [ ] 6.1 Define byte and aggregation ownership

- [ ] Document encoded, decoded, advertised, consumed, and unknown byte boundaries.
- [ ] Define limits for unary JSON, Problem Detail, bodiless, `ResponseEntity`, and
      streaming responses.
- [ ] Prevent compressed unary payloads from bypassing decoded aggregate safeguards.
- [ ] Keep streaming incremental and caller-owned.

### [ ] 6.2 Prove release behavior

- [ ] Cover gzip truncation, corruption, cancellation, and oversized payloads.
- [ ] Verify pooled connections and buffers release exactly once.
- [ ] Avoid hidden body aggregation for streaming diagnostics.
- [ ] Align configuration metadata, diagnostics, operations docs, and real-wire tests.

---

## Priority 7 - OAuth2 Token-Service Transport Isolation

### [ ] 7.1 Separate token-service policy

- [ ] Make token-service timeout, proxy/TLS, retry, and connection ownership explicit.
- [ ] Preserve single-flight refresh, waiter cancellation, invalidation, and one-time
      `401` replay across logical clients.
- [ ] Keep business-client names correct while identifying token-service failures.
- [ ] Avoid silently inheriting incompatible business-request transport assumptions.

### [ ] 7.2 Preserve sanitized diagnostics

- [ ] Preserve status, safe headers, typed sanitized body decoding, and safe causes.
- [ ] Cover raw, encoded, escaped, nested, and Basic credential redaction.
- [ ] Prove observers, logs, exceptions, and support snapshots contain no credentials.
- [ ] Add real token-service transport and concurrency fixtures.

---

## Priority 8 - Streaming Upload Wire Contract

### [ ] 8.1 Prove outbound framing and ownership

- [ ] Cover publisher, `DataBuffer`, resource, and application-stream bodies at a
      real server boundary.
- [ ] Define deterministic content-length versus chunked transfer behavior.
- [ ] Verify cancellation, partial writes, disconnects, and backpressure ownership.
- [ ] Prove no hidden duplicate subscription or read across retry, redirect, or auth.

### [ ] 8.2 Enforce replay and signing contracts

- [ ] Keep non-repeatable body rejection or warnings aligned with strict/runtime policy.
- [ ] Require SigV4 bytes to match the actual outbound representation.
- [ ] Cover write timeout and unsafe replay before and after dispatch.
- [ ] Align effective-contract, diagnostics, mock helpers, and streaming docs.

---

## Priority 9 - Diagnostics Schema V1 Compatibility

### [ ] 9.1 Freeze and extend schema v1 safely

- [ ] Preserve existing keys and value types with source-controlled fixtures.
- [ ] Make additive fields nullable or unknown when runtime proof is unavailable.
- [ ] Keep provider-, collection-, custom-provider-, and proxied-provider paths aligned.
- [ ] Avoid instantiating lazy clients, auth providers, or resilience instances.

### [ ] 9.2 Enforce every output bound

- [ ] Apply UTF-8 byte, client, endpoint, and field limits to JSON, map, Markdown,
      Actuator, health, and native output.
- [ ] Preserve redaction and deterministic ordering across all renderers.
- [ ] Version any incompatible schema change instead of mutating v1.
- [ ] Run fixture, endpoint, support-bundle, AOT, and compatibility tests.

---

## Priority 10 - Mock and Assembled-Consumer Parity

### [ ] 10.1 Extend stable mock contracts

- [ ] Cover stable timeout, dispatch, OAuth2, upload, and diagnostics additions.
- [ ] Keep wire timing and protocol negotiation outside mock claims.
- [ ] Preserve constructor-injected custom logger and application JSON codec parity.
- [ ] Keep final resolved request metadata aligned with production filters.

### [ ] 10.2 Reprove assembled consumers

- [ ] Run current `3.3.0-SNAPSHOT` and published `3.2.0` consumers from separate repositories.
- [ ] Reject reactor leakage in the published lane.
- [ ] Preserve Surefire and provenance evidence on every failure path.
- [ ] Run focused helper tests and both assembled-consumer scripts.

---

## Priority 11 - Dependency, API, AOT, Native, and Benchmark Evidence

### [ ] 11.1 Revalidate compatibility and runtime matrices

- [ ] Record minimum and forward Boot 4 dependency rows under Java 21.
- [ ] Cover every public addition with japicmp and defer incompatible changes.
- [ ] Add AOT/runtime hints without deprecated Framework 7 member categories.
- [ ] Compile and execute one V23 native contract on GraalVM 25 from a clean commit.

### [ ] 11.2 Re-audit changed hot paths

- [ ] Run benchmark discovery and smoke checks.
- [ ] Pair changed paths with published `3.2.0` on equivalent stacks.
- [ ] Keep smoke output out of public numerical claims.
- [ ] Promote only clean, immutable, versioned benchmark evidence when required.

---

## Priority 12 - Documentation and Release Readiness

### [ ] 12.1 Consolidate public and operations guidance

- [ ] Align protocol, pool, timeout, OAuth2, compression, streaming, and failure docs.
- [ ] Use bounded sanitized examples and fake hosts/credentials.
- [ ] Regenerate configuration metadata/examples and validate public links.
- [ ] Keep changelog performance wording tied to promoted evidence or non-numerical.

### [ ] 12.2 Select and prove the release

- [ ] Select patch versus minor from delivered public scope.
- [ ] Resolve the complete published baseline and record one target-only readiness manifest.
- [ ] Run full reactor, strict API, packaging, consumer, transport, AOT/native, and
      required benchmark gates from one immutable commit.
- [ ] Verify publication before moving public coordinates and the next baseline.
- [ ] Record explicit blockers when the release is not ready.

---

## Completion Rule

V23 is complete only when every implemented contract has ownership evidence at
the layer that controls it, strict compatibility is proven against published
`3.2.0`, and the release decision records either a verified publication or
explicit blockers. Synthetic `ClientResponse` tests alone are insufficient for
dispatch count, timeout budget, protocol capacity, compression, streaming, or
token-service transport claims.
