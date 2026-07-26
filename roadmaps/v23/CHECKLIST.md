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

### [x] 4.1 Distinguish connection and stream pressure

- [x] Add real HTTP/1.1 and HTTP/2 bounded-capacity fixtures.
- [x] Distinguish queued connection acquisition from HTTP/2 stream-capacity pressure
      only where Reactor Netty supplies proof.
- [x] Report unknown instead of inferring unsupported distinctions.
- [x] Keep metrics and support metadata bounded and address-free.

### [x] 4.2 Prove pool ownership under pressure

- [x] Cover cancellation and timeout of queued demand.
- [x] Verify released capacity serves later requests without leaks or starvation.
- [x] Verify factory destruction waits for owned resources and pending demand terminates.
- [x] Align health, diagnostics, failure-stage, and operations guidance.

Evidence:

- Added real HTTP/1.1 and H2C fixtures with one physical connection; the H2 peer
  advertises one concurrent stream. HTTP/1.1 queues additional connection demand,
  while H2 multiplexing uses one transport and queues the second stream until peer
  capacity is released.
- Pool metrics now use one factory-owned registrar per client and the starter-owned
  `reactive.http.client.connection.pool.*` namespace, avoiding incompatible tag sets
  when Reactor Netty built-in meters coexist. Common
  `total.connections` and `idle.connections` gauges carry only the bounded pool
  name; HTTP/1.1 adds active/pending connection gauges, while HTTP/2 adds
  `active.streams` and `pending.streams`. Remote addresses are deliberately
  omitted, and factory
  destruction removes the registered meters after provider disposal.
- Provider-backed schema-v1 diagnostics add configured `poolProtocol`,
  `poolCapacityBasis`, and nullable `poolMaxConcurrentStreams`. H2 reports
  connection-and-peer-stream capacity but leaves the negotiated peer limit unknown;
  collection-backed snapshots preserve unknown/null semantics.
- Real H2 pressure coverage proves queued cancellation and acquire timeout do not
  consume later stream capacity, released capacity serves the next request on the
  same transport, and factory shutdown terminates active/pending demand and waits
  for owned provider disposal.
- `POOL_ACQUIRE` remains bounded generic pool-admission evidence because Reactor
  Pool terminal exceptions do not prove connection versus H2 stream pressure.
  Health retains the bounded aggregate failure count; diagnostics, support-bundle,
  observability, pool, and operations guidance document the same boundary.
- Focused protocol-capacity, existing pool/H2, diagnostics, Actuator, health, and
  release-documentation suites passed. The complete starter and test-helper suites
  and `git diff --check` passed.

---

## Priority 5 - DNS, Proxy, TLS, and Connect Failure Attribution

### [x] 5.1 Add deterministic pre-response fixtures

- [x] Cover DNS resolution, proxy connection/tunnel, TLS handshake, connect timeout,
      and certificate failures where local fixtures can prove ownership.
- [x] Preserve concrete pre-response stages without requiring response URL/status evidence.
- [x] Prevent nested auth-provider and custom-filter errors from being promoted into
      business-request transport stages.
- [x] Keep `ErrorCategory` backward compatible.

### [x] 5.2 Align bounded failure evidence

- [x] Expose only additive low-cardinality stage values.
- [x] Align lifecycle, observer, exchange-log, health, diagnostics, and mock behavior.
- [x] Add sanitized operations reproduction guidance for every supported stage.
- [x] Run focused real-transport and compatibility tests.

Evidence:

- Added the bounded `DNS_RESOLUTION`, `PROXY_CONNECT`, and `TLS_HANDSHAKE` values;
  expanded `CONNECT` to cover direct connection refusal while retaining existing
  connect-timeout behavior. The eight-value stage set remains low-cardinality.
- Reserved `.invalid` DNS with a bounded resolver query, a closed loopback port, a
  local proxy returning `407` to `CONNECT`, a plaintext peer on an HTTPS port, and
  an untrusted loopback certificate prove DNS, connect, proxy-tunnel, TLS-handshake,
  and certificate attribution. Synthetic Netty connect-timeout coverage remains.
- `WebClientRequestException` is the recognized wrapped transport boundary. Direct
  concrete failures remain attributable without URL or status evidence; auth-provider
  exceptions are a hard boundary, and arbitrary nested custom-filter failures require
  final-request dispatch evidence before their cause chain is considered.
- `ErrorCategory` is unchanged. In particular, DNS remains `UNKNOWN_HOST`, direct
  connect remains `CONNECT_ERROR`, TLS remains `TLS_ERROR`, and HTTPS proxy failures
  retain the category selected from their existing outer exception chain.
- Production starter coverage proves observer, lifecycle, and exchange-log parity.
  Micrometer and OTel export only enum names, health counts the failures without new
  per-stage fields, configured-client diagnostics remain request-agnostic, and the mock
  helper reproduces terminal stage semantics without claiming transport timing.
- Error, timeout, observability, diagnostic-context, support-bundle, and operations
  guidance now use the same stage set and include sanitized `EXAMPLE_` reproduction
  procedures for every supported stage.
- Focused real-transport, timeout compatibility, Micrometer, OTel, health, mock,
  generated-documentation, and release-documentation tests passed. The complete
  starter, test-helper, and OTel suites passed. Strict starter japicmp against
  published `3.2.0` passed, and `git diff --check` passed.

---

## Priority 6 - Compression and Aggregate-Limit Boundaries

### [x] 6.1 Define byte and aggregation ownership

- [x] Document encoded, decoded, advertised, consumed, and unknown byte boundaries.
- [x] Define limits for unary JSON, Problem Detail, bodiless, `ResponseEntity`, and
      streaming responses.
- [x] Prevent compressed unary payloads from bypassing decoded aggregate safeguards.
- [x] Keep streaming incremental and caller-owned.

### [x] 6.2 Prove release behavior

- [x] Cover gzip truncation, corruption, cancellation, and oversized payloads.
- [x] Verify pooled connections and buffers release exactly once.
- [x] Avoid hidden body aggregation for streaming diagnostics.
- [x] Align configuration metadata, diagnostics, operations docs, and real-wire tests.

Evidence:

- The response-compression contract now separates encoded wire bytes, decoded
  application bytes, advertised post-transport length, consumed bytes, and unknown
  size. Decoded unary values and `ResponseEntity<T>` use the codec aggregate cap;
  error mapping keeps independent decoded 4 KiB/64 KiB retention caps; bodiless
  results drain; and direct/envelope `DataBuffer` streams remain incremental.
- Real one-connection-pool tests prove highly compressible decoded JSON and
  `ResponseEntity` payloads cannot bypass a 1 MiB codec cap, Problem Detail input
  truncates at 64 KiB while draining, unexpected bodiless content is drained, and
  multi-megabyte direct/envelope streams are not hiddenly aggregated.
- Real-wire cancellation, truncated gzip, and corrupt gzip coverage verifies
  terminal behavior and subsequent pool usability. Corrupt decompression closes the
  affected channel; the documented contract conservatively records that a
  framing-complete truncated gzip member may expose partial decoded data. Existing
  pooled-buffer error-capture tests continue to prove every consumed buffer is
  released, including cancellation paths.
- Provider-backed diagnostics schema v1 now adds `compressionEnabled` and
  `codecMaxInMemorySizeMb`; collection-backed snapshots preserve `null`/unknown.
  Configuration metadata, generated property reference, support-bundle and
  operations guidance, schema fixture, changelog, and focused documentation tests
  use the same decoded aggregation and streaming ownership semantics.

---

## Priority 7 - OAuth2 Token-Service Transport Isolation

### [x] 7.1 Separate token-service policy

- [x] Make token-service timeout, proxy/TLS, retry, and connection ownership explicit.
- [x] Preserve single-flight refresh, waiter cancellation, invalidation, and one-time
      `401` replay across logical clients.
- [x] Keep business-client names correct while identifying token-service failures.
- [x] Avoid silently inheriting incompatible business-request transport assumptions.

### [x] 7.2 Preserve sanitized diagnostics

- [x] Preserve status, safe headers, typed sanitized body decoding, and safe causes.
- [x] Cover raw, encoded, escaped, nested, and Basic credential redaction.
- [x] Prove observers, logs, exceptions, and support snapshots contain no credentials.
- [x] Add real token-service transport and concurrency fixtures.

Evidence:

- Built-in object-style OAuth2 now owns a separate Reactor Netty connector and
  bounded pool per client. The additive `token-service` block controls connect
  and total request timeout, pool capacity/acquisition, direct-or-explicit proxy,
  platform-or-explicit TLS, and opt-in transient retry. Defaults preserve disabled
  request timeout and one attempt; business filters, redirects, resilience
  operators, proxy/TLS, and per-client customizers are not inherited.
- Transient retry is limited to token-request timeout, transport failures, HTTP
  `429`, and `5xx`; credential failures and malformed responses are not retried.
  The provider-owned pool is synchronously disposed with the business client,
  while manually wired and custom providers retain caller-owned `WebClient`
  transport and lifecycle. Startup diagnostics report only bounded policy values.
- A real loopback token/API fixture proves concurrent callers share one refresh,
  business-only filters do not reach the token service, configured token retries
  do not duplicate the business request, hidden `401` replay invalidates and
  refreshes exactly once, final authorization uses the fresh token, and shutdown
  disposes the isolated pool. Existing cancellation, invalidation, failure-cooldown,
  multi-client naming, lifecycle, observer, exchange-log, and replay suites passed.
- Existing sanitization coverage retains HTTP status, safe headers, full sanitized
  typed-decoding bytes and configured token-service codecs while redacting raw,
  form, URL-encoded, JSON-escaped, nested JSON, colon-delimited, Basic, header, and
  request metadata variants. Support guidance records only bounded policy and
  preserves `AuthProviderException` as the token-service/business-stage boundary.
- Focused provider, refresh, lifecycle, factory, real-transport, binding, metadata,
  generated-reference, and native-hint tests passed. The complete starter and
  test-helper suites, strict starter japicmp against published `3.2.0`, metadata
  JSON validation, and `git diff --check` passed.

---

## Priority 8 - Streaming Upload Wire Contract

### [x] 8.1 Prove outbound framing and ownership

- [x] Cover publisher, `DataBuffer`, resource, and application-stream bodies at a
      real server boundary.
- [x] Define deterministic content-length versus chunked transfer behavior.
- [x] Verify cancellation, partial writes, disconnects, and backpressure ownership.
- [x] Prove no hidden duplicate subscription or read across retry, redirect, or auth.

### [x] 8.2 Enforce replay and signing contracts

- [x] Keep non-repeatable body rejection or warnings aligned with strict/runtime policy.
- [x] Require SigV4 bytes to match the actual outbound representation.
- [x] Cover write timeout and unsafe replay before and after dispatch.
- [x] Align effective-contract, diagnostics, mock helpers, and streaming docs.

Evidence:

- Real HTTP/1.1 coverage sends publisher, direct `DataBuffer`, known-length
  `Resource`, `InputStream`, `Reader`, and `ReadableByteChannel` bodies. Unknown-length
  streaming bodies use chunked framing, while the known resource carries an exact
  content length. H2C coverage proves HTTP/2 negotiation and distinguishes its DATA
  frames from Reactor Netty's synthetic server-side transfer-encoding compatibility
  view.
- Application-owned byte and character streams are read off the event loop and closed
  on terminal signals, including cancellation or logical timeout before body subscription.
  Authenticated raw shapes bypass JSON materialization. A peer-disconnect fixture proves
  partial delivery cancels
  bounded upstream demand, releases every pooled `DataBuffer`, and leaves the client
  pool usable for the next request.
- Retry, redirect, and hidden OAuth2 401 replay fixtures count one publisher
  subscription or resource read for each actual dispatch. No eager subscription or
  hidden buffering was added; one-shot streams remain application-owned and require a
  caller-supplied replayable body when replay is enabled.
- Existing runtime warnings and strict built-in SigV4 validation retain the
  `NON_REPEATABLE` versus `APPLICATION_OWNED` distinction. Effective-contract
  snapshots now prove publisher/direct-buffer and Java stream/resource classifications.
- Real-wire SigV4 tests compare `x-amz-content-sha256` with the exact byte and JSON
  representations received by the server. Large `Reader` payloads retain one continuous
  caller-selected charset encoding even with a JSON content type. Body-write timeout
  tests preserve `REQUEST_WRITE` only after dispatch; auth and custom-filter write
  failures before dispatch remain without a transport stage.
- Mock helper coverage materializes and closes an `InputStream` once without making it
  repeatable. Streaming, retry, timeout, test-helper, operations, and changelog guidance
  describe the same framing, ownership, replay, and signing boundaries.
- Focused upload, retry-safety, strict-signing, diagnostics, observability, and mock
  suites passed. The complete starter and test-helper suites passed.

---

## Priority 9 - Diagnostics Schema V1 Compatibility

### [x] 9.1 Freeze and extend schema v1 safely

- [x] Preserve existing keys and value types with source-controlled fixtures.
- [x] Make additive fields nullable or unknown when runtime proof is unavailable.
- [x] Keep provider-, collection-, custom-provider-, and proxied-provider paths aligned.
- [x] Avoid instantiating lazy clients, auth providers, or resilience instances.

### [x] 9.2 Enforce every output bound

- [x] Apply UTF-8 byte, client, endpoint, and field limits to JSON, map, Markdown,
      Actuator, health, and native output.
- [x] Preserve redaction and deterministic ordering across all renderers.
- [x] Version any incompatible schema change instead of mutating v1.
- [x] Run fixture, endpoint, support-bundle, AOT, and compatibility tests.

Evidence:

- Added an immutable published `3.2.0` schema-v1 fixture and a recursive compatibility
  assertion that permits additive fields while rejecting removal or JSON value-kind
  drift. The current exact fixture still freezes ordering, sanitization, and the full
  reviewed V23 field set.
- Provider, collection, JSON, Markdown, Actuator, custom-provider, class-proxy, and
  native paths retain the same keys. Provider-only facts remain nullable/unknown
  when unavailable; unresolved strict Retry instances are no longer created by a
  diagnostics read, while resolved one-attempt and multi-attempt instances report
  false and true respectively. Lazy client factories and auth providers remain
  uninstantiated.
- Map and Actuator output continue to pass through the bounded JSON renderer. Health
  details now use deterministic client-name ordering, reject more than 256 clients or
  names over 512 characters, and a maximum-cardinality multibyte fixture remains
  within the 1 MiB UTF-8 ceiling. The existing 10,000-endpoint and renderer byte
  limits remain covered.
- Focused schema, endpoint, health, AOT, configuration-metadata, support-bundle, and
  release-documentation tests passed. The complete starter and test-helper suites
  passed, strict japicmp against published `3.2.0` passed, and the updated GraalVM 25
  native image compiled and executed successfully. `git diff --check` passed.

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
