# Reactive HTTP Client - Roadmap V22 Execution Checklist

Companion to [`ROADMAP.md`](ROADMAP.md). Execute priorities in order unless a
confirmed correctness or release blocker requires reordering. Check an item only
after implementation and verification evidence is recorded under that priority.
Generated evidence belongs under `target/release-evidence/` unless a promoted,
versioned artifact is explicitly required.

---

## Priority 1 - Post-`3.1.0` Baseline Integrity

### [x] 1.1 Align active version and baseline contracts

- [x] Keep root and module development coordinates on `3.2.0-SNAPSHOT`.
- [x] Keep public README, quick-start, and current migration examples on published `3.1.0`.
- [x] Keep API compatibility and benchmark baselines on published `3.1.0`.
- [x] Keep current-reactor consumer and native fixtures on `3.2.0-SNAPSHOT`.
- [x] Preserve `3.0.0` only in explicitly historical migration/release evidence.
- [x] Add documentation guards that reject stale active baseline instructions.

### [x] 1.2 Prove the complete published release bundle

- [x] Resolve the parent POM plus starter, test-helper, and OTel POM/JAR artifacts.
- [x] Resolve source and Javadoc attachments for every published module.
- [x] Use an absent target-local Maven repository and Maven Central-only settings.
- [x] Require Central remote markers for every resolved file.
- [x] Record SHA-256 values and provenance as target-only evidence.
- [x] Reject missing parent/module POMs, binary JARs, sources, Javadocs, local markers,
      and candidate-version contamination.
- [x] Keep strict root and module-scoped API commands on the same `3.1.0` baseline.
- [x] Run focused release-documentation and provenance fixture tests.
- [x] Run the complete published-bundle command for `3.1.0`.
- [x] Run `git diff --check`.

Evidence:

- Added `scripts/verify-published-release-artifacts.sh`. It refuses reused
  repository/evidence directories, resolves through
  `.mvn/maven-central-settings.xml` with `transitive=false`, and requires the
  parent POM plus module POM, binary, source, and Javadoc artifacts.
- Extended `verify-published-baseline-provenance.sh --release-artifacts` to
  require all four project coordinates, Central remote markers, and source and
  Javadoc attachments while preserving existing API/benchmark/consumer calls.
- Extended provenance fixtures: local markers, a missing module POM, candidate
  contamination, a missing source jar, and a missing Javadoc jar all fail; a
  complete Central-marked fixture produces 13 checksums.
- Passed `scripts/verify-published-release-artifacts.sh 3.1.0` from an absent
  target repository. Target-only evidence under
  `target/release-evidence/published-baselines/release-artifacts-3.1.0/`
  records `source=Maven Central`, `releaseArtifacts=true`, remote markers,
  and 13 SHA-256 values.
- Generated readiness evidence now reports one complete-bundle command instead
  of three binary-only commands while retaining separate development
  `3.2.0-SNAPSHOT`, published `3.1.0`, and API baseline `3.1.0` fields.
- Updated current migration and published-consumer instructions to `3.1.0`;
  historical `3.0.0` API and release evidence remains explicitly labeled.
  Published-consumer evidence now uses the release-independent
  `target/release-evidence/published-consumer/` path.
- Passed fresh strict root and starter-module japicmp comparisons against
  published `3.1.0`, followed by Central provenance verification under
  `target/release-evidence/v22-priority1/`.
- Passed
  `mvn -q -s .mvn/maven-central-settings.xml -pl reactive-http-client-starter -Dtest=DocumentationReleaseArtifactTest,ReactiveHttpClientConfigurationMetadataTest test`,
  `bash scripts/verify-published-baseline-fixtures.sh`, shell syntax checks,
  and `git diff --check`.

---

## Priority 2 - Real HTTP/2 and H2C Contract

### [x] 2.1 Prove negotiated protocol behavior

- [x] Add real TLS H2/ALPN and clear-text H2C servers.
- [x] Invoke both servers through normal starter proxies.
- [x] Prove HTTP/1.1 remains the default when HTTP/2 is disabled.
- [x] Cover unary JSON and `ResponseEntity<T>`.
- [x] Cover direct and envelope streaming responses.

### [x] 2.2 Prove HTTP/2 resource ownership

- [x] Exercise concurrent streams over a bounded connection provider.
- [x] Cover cancellation, reset, timeout, and 4xx/5xx mapping.
- [x] Verify stream completion does not dispose unrelated concurrent streams.
- [x] Verify factory shutdown releases the provider.
- [x] Verify TLS protocol/cipher configuration composes with H2.
- [x] Run focused transport tests and `git diff --check`.

Evidence:

- Added `ReactiveHttpClientHttp2ContractTest`, which starts real Reactor Netty
  TLS H2/ALPN, H2C, and dual HTTP/1.1/H2C servers and invokes each through a
  normal `ReactiveHttpClientFactoryBean` proxy.
- Both HTTP/2 modes decode unary JSON and `ResponseEntity<Payload>`, transfer
  direct `Flux<DataBuffer>` and delayed-consumption
  `ResponseEntity<Flux<DataBuffer>>` bodies, and report `HTTP/2.0` at the
  server. The disabled client reports `HTTP/1.1`.
- A one-connection provider carries concurrent H2 streams. Cancelling one
  stream and an explicit `RST_STREAM(CANCEL)` leave a concurrent slow stream
  and later probe usable on the same transport channel.
- Method timeout plus 422 and 503 responses release their streams and preserve
  `HttpClientException`/`RemoteServiceException` mapping. Factory destruction
  waits until the H2 connection provider reports disposed.
- The TLS fixture negotiates H2 with configured `TLSv1.3` and
  `TLS_AES_128_GCM_SHA256`, proving custom TLS protocol/cipher settings compose
  with ALPN selection.
- Documented H2 versus H2C selection, unchanged HTTP/1.1 default, supported
  response shapes, and per-stream ownership in `docs/12-proxy-tls.md`.
- Passed `mvn -q -pl reactive-http-client-starter
  -Dtest=ReactiveHttpClientHttp2ContractTest,ReactiveHttpClientFactoryBeanHttpProtocolTest,TlsIntegrationTest,Framework7TransportCorrectnessTest,TransportResourceOwnershipStressTest,DocumentationReleaseArtifactTest
  test`: 59 tests, zero failures or errors. The complete
  `mvn -q -pl reactive-http-client-starter test` run also passed 751 tests, and
  `git diff --check` passed.

---

## Priority 3 - Compression and Content-Encoding Correctness

### [x] 3.1 Define and test the compression contract

- [x] Document request negotiation versus response decompression.
- [x] Cover gzip, identity fallback, empty responses, JSON, and errors.
- [x] Cover `ResponseEntity` and streaming response ownership.
- [x] Prove compression remains opt-in.
- [x] Reject contradictory transport-owned compression headers where appropriate.

### [x] 3.2 Align diagnostics with encoded data

- [x] Define whether request/response byte values are encoded, decoded, advertised, or unknown.
- [x] Align lifecycle, observer, exchange-log, and documentation wording.
- [x] Avoid aggregating streaming bodies for size reporting.
- [x] Add real-wire and diagnostic parity tests.
- [x] Run focused tests and `git diff --check`.

Evidence:

- Added `ReactiveHttpClientCompressionContractTest` with a real Reactor Netty
  server that sends gzip and identity representations in delayed wire chunks.
  Normal starter proxies prove opt-in `Accept-Encoding`, automatic JSON and
  error-body decompression, identity fallback, 204 handling, `ResponseEntity`,
  direct `Flux<DataBuffer>`, delayed streaming-envelope consumption, and
  cancellation followed by a healthy pooled call.
- `compression-enabled` remains response-only: the real server receives an
  unencoded request body with no request `Content-Encoding`, while observer
  request bytes report the measurable application payload before transport
  content coding.
- The final transport-ownership filter now rejects application-supplied
  `Accept-Encoding` when connector compression is enabled. A real default-header
  fixture proves rejection occurs before the server receives a request; the
  disabled mode preserves application ownership of explicitly encoded
  negotiation.
- Defined observer response bytes as post-transport advertised
  `Content-Length`, not a consumed-body count. Identity responses retain the
  advertised value; Reactor Netty removes gzip representation length and
  encoding headers after decompression, so compressed and chunked streams
  report `UNKNOWN_SIZE` without aggregation.
- Clarified that `HttpClientObserverEvent` owns byte counters,
  `ReactiveHttpClientLifecycleContext` has none, and `HttpExchangeLogContext`
  exposes post-transport headers but no size counter. Updated Micrometer, OTel,
  streaming, configuration metadata, and transport documentation consistently.
- Passed 163 focused tests across compression, transport headers, streaming,
  `ResponseEntity`, error decoding, Micrometer, OTel, configuration metadata,
  and release documentation. The OTel lane used
  `.mvn/maven-central-settings.xml` after the configured private mirror failed
  DNS resolution. The complete starter suite also passed 758 tests, and
  `git diff --check` passed.

---

## Priority 4 - Pool Saturation and Acquisition Diagnostics

### [x] 4.1 Add bounded pool-saturation fixtures

- [x] Cover queued acquire success and timeout with a one-connection pool.
- [x] Cover cancellation while queued.
- [x] Cover idle/lifetime eviction and background eviction.
- [x] Cover shutdown with active and pending work.
- [x] Assert no pending waiter or connection leak remains.

### [x] 4.2 Improve safe saturation diagnostics

- [x] Preserve existing `ErrorCategory` behavior.
- [x] Add a failure-stage signal only when the runtime can prove it.
- [x] Keep any new value bounded and optional.
- [x] Align lifecycle, observer, exchange-log, health, diagnostics, and docs.
- [x] Keep server address opt-in.
- [x] Run compatibility and focused tests.

Evidence:

- Added `ReactiveHttpClientPoolSaturationContractTest` with a real Reactor Netty
  server and a starter-created one-connection provider. Deterministic request gates
  prove queued acquire success, timeout before dispatch, queued cancellation, idle
  and lifetime background eviction, and shutdown with active plus pending work.
- Cancellation never reaches the server or consumes the next released connection;
  subsequent probes succeed, eviction creates a fresh channel, and shutdown leaves
  the provider disposed with both active and pending publishers terminated.
- Added additive `HttpClientFailureStage.POOL_ACQUIRE`, resolved only from exact
  Reactor Pool and Reactor Netty shaded acquire-timeout, pending-limit, or shutdown
  exception types. Generic failures remain unset and acquire timeout keeps the
  established `ErrorCategory.TIMEOUT` behavior.
- Exposed the derived optional stage through observer, lifecycle, and exchange-log
  contexts without changing their constructors. Micrometer adds bounded
  `failure.stage`, OTel adds `rhttp.failure.stage`, and server address remains under
  the existing disabled-by-default opt-in.
- Health details now report probe-window `poolAcquireFailureCount`. Provider-backed
  diagnostics and schema-v1 fixtures add sanitized pool source, max connections,
  pending acquire timeout, and metrics policy; collection-backed snapshots report
  those provider-only values as unknown.
- Updated error handling, pool troubleshooting, observability, diagnostic-context,
  support-bundle, and changelog documentation.
- Passed the complete starter test suite, focused OTel observer tests, strict API
  compatibility against published `3.1.0` from a fresh Central-only repository, and
  `git diff --check`. The configured private mirror failed DNS for the first OTel
  attempt, so that lane used `.mvn/maven-central-settings.xml`.

---

## Priority 5 - Timeout Phase and Terminal-State Parity

### [x] 5.1 Exercise each timeout boundary

- [x] Cover connect, pool acquire, request write, response headers, unary body, and stream body.
- [x] Cover method/client timeout and Resilience4j timeout behavior.
- [x] Preserve `0 = disabled` and existing precedence.
- [x] Use real transport where timing ownership matters.

### [x] 5.2 Align terminal reporting

- [x] Compare status, headers, attempts, duration, final request metadata, category, and cancellation.
- [x] Keep streaming-envelope timing separate from inner-body consumption.
- [x] Ensure mock helpers assert semantics without pretending to emulate network timing.
- [x] Update timeout and diagnostic-context docs.
- [x] Run focused tests and `git diff --check`.

Evidence:

- Added bounded exact attribution for connect, pool-acquire, request-write,
  response-header, and response-body failures. Response-header attribution requires
  final-attempt request-dispatch evidence, including hidden 401 auth refreshes,
  so nested auth and other pre-dispatch read timeouts remain unattributed. Generic
  timeout exceptions remain unattributed, while concrete
  connect, pool-acquire, and request-write failures remain attributable without URL
  evidence, cancellation remains `CANCELLED`, and existing error categories
  remain compatible.
- Added a real loopback transport fixture for pre-header, unary-body, direct-stream,
  streaming-envelope, disabled method timeout, inherited client timeout, and
  cancellation behavior. Deterministic Netty exception fixtures cover connect and
  write attribution without relying on flaky unreachable-network timing; the
  Priority 4 one-connection fixture remains the pool-acquire timing proof.
- Verified method, API, client, and deprecated resilience timeout precedence and
  preserved `0 = disabled`. The deprecated resilience timeout remains Reactor
  Netty response-timeout configuration, not a Resilience4j `TimeLimiter`.
- Terminal assertions compare status, response headers, attempt count, duration,
  final request URL/headers, error category, failure stage, and cancellation across
  lifecycle hooks, exchange logging, and observers. A streaming envelope reports
  outer success once; a later inner-body timeout does not rewrite that terminal
  record.
- Added `MockReactiveHttpClient.bodyError(...)` for stable terminal failure
  assertions, with documentation that it does not emulate DNS, connection, pool,
  request-write, or socket timing.
- Updated timeout, error handling, resilience, observability, test-helper,
  diagnostic-context, and support-bundle documentation plus the changelog.
- Passed focused timeout, pool, precedence, lifecycle, Micrometer, mock-helper, and
  OTel tests; the complete starter and test-helper suites; strict API compatibility
  against published `3.1.0` from an isolated Central-only repository; and
  `git diff --check`.

---

## Priority 6 - Streaming Backpressure and Upload Ownership

### [x] 6.1 Prove publisher request-body ownership

- [x] Keep invocation cold until subscription.
- [x] Prove one request-body subscription per actual transport attempt.
- [x] Cover cancellation before and during upload.
- [x] Cover retry, redirect, auth, and serialization boundaries.
- [x] Reject unsupported non-repeatable combinations before sending.

### [x] 6.2 Prove response-buffer ownership

- [x] Cover direct and envelope `Flux<DataBuffer>`.
- [x] Release discarded buffers exactly once.
- [x] Preserve caller ownership after handoff.
- [x] Align repeatability decisions across runtime, strict validation, diagnostics, docs, and mocks.
- [x] Run leak-sensitive tests and `git diff --check`.

Evidence:

- Added `StreamingUploadOwnershipTest` with a real Reactor Netty server and a
  one-connection starter pool. It proves invocation remains cold, transport
  demand is bounded, queued cancellation never subscribes the body, and
  in-flight cancellation stops the producer while leaving the client usable.
- Retry, body-preserving `307`, and the built-in one-time `401` auth refresh
  each subscribe a replayable publisher exactly once per actual request. DTO
  publishers remain on the configured WebClient JSON encoder path without a
  hidden subscription or starter aggregation.
- A reusable multipart `Resource` is opened and closed once per retry attempt.
  Existing strict and runtime SigV4 tests prove unsupported publisher and
  multipart signing fails before transport or publisher subscription.
- Revalidated direct and envelope pooled `DataBuffer` tests: emitted buffers
  remain allocated until caller release, while cancellation releases queued
  discarded buffers without a second release.
- Added mock-helper coverage showing publisher bodies remain cold and are
  materialized once per recorded retry attempt. Documentation now distinguishes
  this in-process materialization from real pool, socket-demand, redirect, and
  cancellation behavior.
- Published one repeatability matrix across streaming, resilience, multipart,
  auth, redirect, and mock-helper documentation and recorded the contract in
  the changelog. `Object`, unresolved generic, `InputStream`, `Reader`, and
  `ReadableByteChannel` declarations now export and warn as `APPLICATION_OWNED`;
  strict built-in signing reuses the same stream-shape classifier.
- Passed focused starter and test-helper ownership, response-buffer, retry,
  redirect, auth/signing, multipart, diagnostics, and documentation tests; the
  complete module suites and `git diff --check` also passed.

---

## Priority 7 - OAuth2 Refresh and Token-Service Reliability

### [x] 7.1 Revalidate concurrent refresh state

- [x] Deduplicate concurrent refreshes.
- [x] Prevent one cancelled waiter from cancelling shared refresh work.
- [x] Cover expiry leeway, absent expiry, invalidation, repeated 401, failure, and recovery.
- [x] Preserve the logical downstream client name.

### [x] 7.2 Revalidate safe token failures

- [x] Cover empty/malformed/oversized 2xx and 4xx/5xx bodies.
- [x] Cover encoded, nested, escaped, and non-UTF-8 secret-bearing payloads.
- [x] Preserve safe status, headers, typed decoding, and configured codecs.
- [x] Preserve custom WebClient status-handler behavior.
- [x] Verify no credentials appear in messages, causes, request metadata, or snapshots.
- [x] Run focused auth tests and `git diff --check`.

Evidence:

- `RefreshingBearerAuthProviderTest` proves one shared token fetch for concurrent
  callers, cancellation isolation, expiry-window and non-expiring reuse,
  invalidation epochs, deterministic failure cooldown and recovery, and distinct
  logical client names for every waiter on a shared refresh failure.
- `OutboundAuthFilterTest` proves a downstream `401` releases its body,
  invalidates once, retries once with fresh auth, and returns a repeated `401`
  without entering another refresh loop.
- `OAuth2ClientCredentialsTokenProviderTest` covers empty, malformed, large, and
  missing-token 2xx responses plus bounded 4xx and 5xx diagnostics. Existing and
  expanded fixtures cover form, JSON, nested JSON, escaped Unicode, percent
  encoding, Latin-1, Basic credentials, and whitespace-bearing secrets.
- Sanitized `WebClientResponseException` causes retain status-specific types,
  safe headers such as `Retry-After`, full sanitized bodies, typed decoding,
  configured codecs, UTF-8/content-length consistency, and no token-request
  metadata. Credential-bearing response headers are redacted.
- Default WebClient status handlers retain precedence. Observer and lifecycle
  fixtures report the logical downstream client without final request metadata
  or authorization headers when auth fails before dispatch; diagnostics snapshot
  fixtures remain free of auth values and credentials.
- Passed focused auth, factory, observer, lifecycle, and diagnostics tests, the
  complete starter module suite, documentation checks, and `git diff --check`.

---

## Priority 8 - Failure Attribution Contract

### [x] 8.1 Audit the existing taxonomy

- [x] Cover DNS, connect, TLS, timeout, cancellation, decode, auth, resilience, 4xx, 429, 5xx, and unknown.
- [x] Keep cause traversal bounded.
- [x] Preserve the most actionable proven category.
- [x] Preserve retry terminal cause and subscription-attempt semantics.

### [x] 8.2 Guard additive public changes

- [x] Add no public category or accessor without a demonstrated consumer need.
- [x] Include any addition in japicmp and compatibility fixtures.
- [x] Align test-helper assertions and public docs.
- [x] Run strict root/module API checks and focused tests.
- [x] Run `git diff --check`.

Evidence:

- `ErrorCategories` now resolves one outer-to-inner cause path with the existing
  16-node bound. Explicit starter HTTP and auth exceptions retained through retry
  wrappers win before less-specific nested transport causes; status-only fallback
  remains unchanged when the bounded chain has no proven category.
- `ErrorCategoriesTest` covers the published DNS, connect, TLS, timeout,
  cancellation, decode, auth, Resilience4j, 4xx, 429, 5xx, and unknown matrix,
  plus the exact traversal boundary, wrapped 429, and auth-over-timeout precedence.
- `ReactiveClientInvocationHandlerObservabilityErrorCategoryTest` proves retry
  exhaustion reports the exact final throwable, two subscription attempts, the
  final auth category, and no stale dispatch/failure-stage evidence.
- `ErrorCategoryAssertions.hasErrorCategory(...)` now delegates to the same
  public `ErrorCategories` resolver for HTTP, auth, transport, cancellation,
  decode, resilience, and unknown failures; `hasStatusCode(...)` remains
  limited to HTTP exceptions carrying a status.
- No public enum value, field, accessor, or signature was added, so the existing
  japicmp include set and compatibility fixtures required no update. Error handling,
  diagnostic-context, test-helper, and changelog wording use the same category and
  subscription-attempt contract.
- Passed focused failure-attribution and mock-helper tests, complete starter and
  test-helper module suites, strict root and module-scoped API comparisons against
  published `3.1.0` from isolated Maven repositories, documentation guards, and
  `git diff --check`.

---

## Priority 9 - Diagnostics Schema V1 Evolution

### [x] 9.1 Preserve schema equivalence

- [x] Compare provider, collection snapshot, JSON, Markdown, Actuator, and native output.
- [x] Keep additions nullable and backward-compatible.
- [x] Preserve distinct unknown, unavailable, disabled, false, and zero states.
- [x] Reject removals, renames, and type changes in the source-controlled fixture.

### [x] 9.2 Preserve support-output safety

- [x] Enforce client, endpoint, field, and UTF-8 byte limits on map and rendered forms.
- [x] Reject secret-bearing and unbounded fields.
- [x] Keep concrete URLs, headers, auth identifiers, payloads, and proxy credentials out.
- [x] Run JVM/native schema fixtures and `git diff --check`.

Evidence:

- Kept schema v1 unchanged. Request-scoped protocol, content-encoding, failure-stage,
  error-category, header, and payload-size facts remain in per-call diagnostics instead
  of being misrepresented as configured-client snapshot state. Future additions must
  remain nullable/additive or move to a later schema version.
- Added structural JVM coverage that compares provider map output with rendered JSON,
  freezes root and client key sets, proves provider and collection snapshots retain the
  same shape, and preserves provider `false` values separately from collection `null`/
  Markdown `unknown`. Existing fixture cases continue to cover `unavailable`,
  `disabled`, and numeric zero.
- Strengthened the opt-in Actuator test to require the exact v1 root/client fields and
  Java value types. The exact source-controlled JSON fixture remains the reviewed gate
  for removals, renames, type drift, ordering, and secret-bearing additions.
- Retained the shared 256-client, 10,000-endpoint, 512-character field, and 1 MiB UTF-8
  rendered-output limits. Map and Actuator paths still validate through the JSON
  renderer before returning, and sanitization checks continue to reject concrete URLs,
  headers, provider names, payloads, credentials, and machine-local paths.
- Expanded the GraalVM 25 fixture to compare the Actuator endpoint with direct provider
  output, validate map/collection field sets and types, preserve nullable unknown states,
  verify Markdown semantics, and reject sensitive values. The native image compiled and
  its executable completed successfully against `3.2.0-SNAPSHOT`.
- Passed the focused diagnostics, auto-configuration, Boot 4, fixture, and documentation
  tests plus the complete starter suite and `git diff --check`.

---

## Priority 10 - Mock and Consumer Parity

### [x] 10.1 Revalidate mock-owned behavior

- [x] Cover auth, retry, lifecycle, observers, exchange logging, inherited generics, and repeated headers.
- [x] Preserve final request metadata.
- [x] Preserve constructor-injected loggers and application JSON codecs.
- [x] Do not fake protocol negotiation, TLS, compression wire bytes, pool timing, or connection reuse.

### [x] 10.2 Revalidate assembled consumers

- [x] Cover transport-owned behavior with real servers.
- [x] Run current-reactor and published-`3.1.0` consumers separately.
- [x] Use fresh repositories and reject reactor leakage.
- [x] Record separate target-only reports and provenance.
- [x] Run focused tests and `git diff --check`.

Evidence:

- Kept production mock behavior unchanged. The 33 `MockReactiveHttpClientTest`
  cases plus the Boot 4 codec parity case cover auth and one-time `401` refresh,
  retry/idempotency, ordered lifecycle hooks, one terminal observer event, final
  URL/status/header metadata, annotation-selected constructor-injected exchange
  loggers, application Jackson 3 codec bytes, inherited generic DTOs, and repeated
  request/response headers.
- Documented the ownership boundary in `docs/14-test-helpers.md`: the in-process
  helper proves starter-owned request/response behavior but does not claim protocol
  negotiation, TLS, compression wire bytes, pool timing, or connection reuse.
- Added `scripts/verify-current-consumer.sh` and wired the CI assembled-consumer
  job to it. The verifier cleans module outputs, installs `3.2.0-SNAPSHOT` into a
  fresh target-local repository, runs both mock parity classes, then runs the
  independent Boot 4 consumer against those installed jars. An `EXIT` trap copies
  only Surefire XML reports newer than the invocation marker into the uploaded
  evidence directories while preserving the original verifier exit code when either
  test stage fails; stale reports from prior local runs are ignored.
- The assembled consumer uses a real Reactor Netty server for inherited-generic and
  configured `@ApiRef` endpoints, repeated headers, redirects, bodiless draining,
  delayed streaming ownership, timeout attribution, Problem Detail mapping, and
  lifecycle/observer terminal metadata. All 3 tests passed in both consumer lanes.
- The current lane rejected reactor `target/classes` leakage and recorded 34 mock
  tests, 3 consumer tests, classpath, dependency tree, effective POM, current
  artifact hashes, commit state, and provenance under
  `target/release-evidence/current-consumer/current-3.2.0-SNAPSHOT/`.
- `scripts/verify-published-consumer.sh 3.1.0` passed separately against an absent
  Maven Central-only repository, verified remote markers and hashes for the
  published parent, starter, test helper, and OTel artifacts, rejected reactor
  leakage, and wrote its reports under
  `target/release-evidence/published-consumer/published-3.1.0/`.
- Passed the focused mock parity tests and `DocumentationReleaseArtifactTest`;
  the verifier script passed `bash -n`, and final documentation tests and
  `git diff --check` passed.

---

## Priority 11 - Dependency, AOT, and Native Matrix

### [x] 11.1 Revalidate supported dependencies

- [x] Run minimum and forward Boot 4 rows.
- [x] Record Framework, Reactor Netty, Netty, Jackson, Micrometer, OTel, and Resilience4j versions.
- [x] Keep Java 21 as the compilation baseline.
- [x] Verify optional integrations back off when absent.

### [ ] 11.2 Revalidate AOT/native behavior

- [x] Preserve inherited client and annotation reflection metadata.
- [x] Avoid deprecated Framework 7 hint categories.
- [x] Add at least one V22 contract to the native fixture.
- [x] Compile and execute with GraalVM 25.
- [ ] Record clean immutable target-only provenance.
- [x] Run `git diff --check`.

Evidence:

- `scripts/verify-supported-matrix.sh` passed the full reactor, assembled Boot 4
  consumer, and strict published-`3.1.0` API comparison for Boot `4.0.0` and
  `4.1.0` under Corretto `21.0.11`, using a distinct fresh Maven Central
  repository for each row.
- Target-only evidence under `target/release-evidence/v22-priority11/` records
  Framework/WebFlux `7.0.1`/`7.0.8`, Reactor Netty `1.3.0`/`1.3.6`, Netty
  `4.2.7.Final`/`4.2.15.Final`, Jackson `3.0.2`/`3.1.4`, Micrometer
  `1.16.0`/`1.17.0`, OTel `1.55.0`/`1.62.0`, and Resilience4j `2.4.0` for
  the minimum/forward rows.
- The verifier now fails unless each row executes the absent-Micrometer,
  absent-Resilience4j-registry, absent-Actuator, absent-OTel-bean, and
  absent-OTel-API back-off contracts; both generated
  `optional-integration-contracts.properties` files report every contract as
  passed.
- `ReactiveHttpClientAotSmokeTest` and the generated release guard passed,
  preserving inherited method, marker-annotation, configuration, proxy, and
  resource hints without `MemberCategory` or introspection executable modes.
- The native fixture now negotiates gzip and decodes a manually compressed JSON
  response over its real loopback transport. GraalVM `25.0.3` completed AOT,
  built the image from a clean fixture target, and the executable passed all
  inherited generic, configured `@ApiRef`, auth, Problem Detail, diagnostics,
  health, Micrometer, and compression assertions.
- The native workflow now rejects dirty source, records the immutable commit,
  appends the executable SHA-256 and pass marker, and uploads provenance even
  when a later step fails. A clean-commit rerun remains required before closing
  11.2. `git diff --check` also passed.

---

## Priority 12 - Benchmark and Allocation Re-Audit

### [x] 12.1 Preserve harness fairness

- [x] Run discovery and smoke profiles.
- [x] Keep current and published-`3.1.0` stacks equivalent.
- [x] Keep no-network diagnostics separate from loopback scenarios.
- [x] Add no scenario without an equivalent comparison contract.

### [x] 12.2 Gather release-quality evidence

- [x] Run current and published-baseline release profiles from clean inputs.
- [x] Compare success, JSON, `ResponseEntity`, error, diagnostics, lifecycle, observer, and argument expansion.
- [x] Investigate material movement before changing production code.
- [x] Record accepted noise and optimization decisions.
- [x] Promote a report only for supported numerical release claims.
- [x] Run report tests and `git diff --check`.

Evidence:

- Added a `benchmark-discovery` profile and a fail-fast internal fairness
  contract that runs before JMH discovery, smoke, or release execution.
- The contract requires exactly one raw `WebClient`, Spring HTTP Interface, and
  starter method for every `clientSideOverhead` scenario. It rejects loopback
  classifications on no-network benchmark classes and delegates every method to
  the report naming classifier.
- Current-reactor and published-`3.1.0` discovery use the same benchmark source,
  managed Boot stack, connector setup, codec limit, loopback server, and request/
  response validation. The published profile excludes only current-only
  diagnostics-provider source and keeps its rows outside loopback comparisons.
- Focused classification/fairness tests, current and published-baseline
  discovery, the current smoke profile, report generation, documentation checks,
  and `git diff --check` passed. Release-quality measurements remain scoped to
  12.2.
- Release-quality current and published-`3.1.0` runs completed from clean inputs
  on the same managed Boot 4 stack. The current report records commit `90bfdc7`;
  the baseline report records starter `3.1.0`, and Central provenance records its
  POM and JAR SHA-256 values.
- Generated the target-only paired comparison at
  `target/release-evidence/v22-priority12/current-vs-published-3.1.0.md`.
  Core starter loopback allocations remained within approximately `-1.4%` to
  `+1.3%`; optional feature allocations remained within `-2.2%` to `+1.1%`;
  Problem Detail allocation changed by less than `0.4%`; and both argument
  expansion paths remained exactly `984 B/op`.
- Timing review rows moved across raw `WebClient`, Spring HTTP Interface, and
  starter methods. JSON was internally contradictory: starter throughput was
  about `20.1%` lower while average time was about `26.6%` lower. Together with
  stable allocations, this was classified as run-level timing noise rather than
  evidence for a production-path change.
- The no-network diagnostics audit kept feature costs explicit against the
  disabled path (`220,601 ops/s`, `11,160 B/op`): one/multiple lifecycle hooks
  measured `162,778`/`157,728 ops/s`, one/multiple observers measured
  `158,876`/`151,802 ops/s`, metadata logging measured `99,268 ops/s`, and the
  Micrometer observer measured `137,616 ops/s`. These current-only rows are not
  presented as published-`3.1.0` comparisons.
- `proxyInvocationCreatesPublisher` allocated `184 B/op` less than the published
  baseline, but the current production delta contains no intentional publisher-
  creation optimization and the comparator did not trigger its allocation review
  threshold. The change is retained as non-actionable evidence pending repetition
  in a future release audit.
- No production optimization was made and no report was promoted: the data does
  not support a stable public numerical claim. Benchmark report/comparator tests,
  release-documentation checks, and `git diff --check` passed.

---

## Priority 13 - Documentation and Operations Consolidation

### [x] 13.1 Update current operational guidance

- [x] Cover protocol, compression, pool, timeout, streaming, OAuth2, and failure diagnosis.
- [x] Update sanitized support-bundle recipes.
- [x] Keep configuration examples metadata-valid and clearly fake.
- [x] Keep public coordinates on the latest published release.

### [x] 13.2 Guard generated and historical documentation

- [x] Separate current commands from historical evidence.
- [x] Reject stale active baselines without rewriting release history.
- [x] Run generated configuration/reference checks.
- [x] Run local Markdown-link and release-note evidence checks.
- [x] Run `git diff --check`.

Evidence:

- Added `docs/30-operations-troubleshooting.md` as the current first-response
  index. Its bounded triage matrix covers HTTP/1.1, H2/H2C and malformed
  framing, gzip negotiation/decompression, pool saturation, proven timeout
  phases, publisher and `DataBuffer` ownership, OAuth2 refresh, and the
  distinction between `ErrorCategory` and optional `failure.stage`.
- Expanded the production checklist and support-bundle guide with compression
  ownership, protocol/compression incident evidence, and standalone failure-
  attribution capture. Recipes retain metadata-only defaults, fake/reserved
  hosts or explicit placeholders, and prohibit payload and credential capture.
- The operations guide scopes consumer instructions to published `3.1.0` and
  explicitly separates active instructions from immutable versioned benchmark,
  API, migration-decision, and changelog evidence. A focused documentation test
  derives that expected published version from `latest.published.version`, so a
  future baseline transition must update active guidance without rewriting
  historical artifacts.
- Added the operations guide to README navigation and the support-bundle related
  guides, and recorded the documentation consolidation under Unreleased.
- Passed
  `mvn -q -pl reactive-http-client-starter -Dtest=DocumentationReleaseArtifactTest,ReactiveHttpClientConfigurationMetadataTest test`:
  46 tests, zero failures or errors. This validates generated configuration-
  reference equality, every documented `reactive.http.*` property and YAML/
  properties example against metadata, safe support-bundle placeholders, local
  Markdown links/anchors, current version scope, and release-note benchmark
  evidence rules.
- `git diff --check` passed.

---

## Priority 14 - V22 Release Go/No-Go

### [x] 14.1 Select release scope and version

- [x] Inventory production, API, configuration, and behavior changes.
- [x] Select `3.1.x` for fixes/hardening only.
- [x] Select `3.2.0` only for backward-compatible public additions.
- [x] Reject or defer incompatible changes.
- [x] Keep snapshot/public/baseline coordinates separated until release.

Decision:

- Select **`3.2.0`** for the V22 release candidate. A `3.1.x` patch is rejected
  because the release adds backward-compatible public contracts rather than
  containing fixes and hardening only.
- The public API delta adds `HttpClientFailureStage`, bounded failure-stage
  accessors on observer, lifecycle, and exchange-log contexts, and
  `MockReactiveHttpClient.bodyError(...)`. Diagnostics schema v1 also adds
  nullable provider-backed pool-policy fields while preserving collection-
  backed unknown values.
- Configuration and behavior changes are additive or hardening: connector-owned
  response compression semantics, real H2/H2C evidence, pool-acquire and timeout
  attribution, streaming replay/ownership alignment, OAuth2 refresh isolation,
  diagnostics bounds, current-consumer verification, and benchmark fairness.
- Completed V22 strict root/module compatibility checks against published
  `3.1.0` report no incompatible public change. The available test-helper
  japicmp report classifies `bodyError(...)` as a new method; OTel has no public
  signature removal. Any incompatible result discovered by the final 14.2 rerun
  blocks release and must be reverted or deferred.
- Keep the reactor and current consumer/native fixtures on `3.2.0` for the
  release candidate.
  Keep `latest.published.version`, public dependency snippets, API compatibility,
  and benchmark baselines on published `3.1.0` until `3.2.0` is published and
  every companion artifact resolves from Maven Central. Tag creation,
  publication, and baseline transition remain part of 14.2.

### [ ] 14.2 Assemble immutable release evidence

- [x] Run clean full reactor and strict root/module API checks.
- [ ] Run packaging, current/published consumers, optional integrations, transport, AOT/native, metadata, and docs.
- [x] Resolve the complete published baseline from a fresh Central repository.
- [x] Promote or defer benchmark evidence according to claims.
- [x] Record one target-only readiness snapshot.
- [ ] For go, publish/tag/date/verify one exact commit and version.
- [ ] For no-go, publish nothing and record blockers plus reproduction commands.
- [ ] Move the next snapshot and baseline only after all companion artifacts resolve publicly.
- [ ] Update roadmap/changelog status and run `git diff --check`.

Evidence:

- Prepared the reactor, starter, test helper, OTel companion, benchmark module,
  current-consumer fixture, and native fixture as the final `3.2.0` release
  candidate. `latest.published.version`, public dependency snippets, strict API
  compatibility, and benchmark baselines remain on published `3.1.0`.
- Added the dated `3.2.0` changelog section and comparison links. The generated
  release contract identifies this state as `release-candidate`, with
  `plannedFinalVersion=3.2.0`, rather than treating the dated but not yet
  published candidate as the latest published release.
- `mvn -B -ntp clean verify` passed: 796 starter, 36 test-helper, and 40 OTel
  tests, with fresh binary, source, and Javadoc artifacts. Generation packaging
  verification also passed for all `3.2.0` artifacts.
- Strict root and module-scoped japicmp checks passed against published `3.1.0`
  from separate fresh Maven Central repositories. The API compatibility and
  published-baseline negative fixtures also passed.
- The complete published `3.1.0` parent, starter, test-helper, and OTel POM,
  binary, source, and Javadoc bundle resolved from a fresh Central-only
  repository with remote markers and SHA-256 provenance.
- Current-reactor mock and assembled Boot 4 consumer verification passed against
  target-local `3.2.0` artifacts. The independent published-consumer lane passed
  against Central-only `3.1.0`, with reactor leakage rejected in both lanes.
- The Boot `4.1.0` release-smoke row passed all 796 starter, 36 test-helper, and
  40 OTel tests. This complements the clean default Boot `4.0.0` reactor run and
  covers optional integrations, transport, AOT, metadata, and documentation.
- Benchmark report promotion is deferred: the `3.2.0` changelog makes no
  numerical latency, throughput, allocation, or overhead claim. V22 benchmark
  fairness, smoke, current-candidate, and published-`3.1.0` audits remain
  recorded under Priority 12.
- Generated target-only readiness evidence is available at
  `target/release-evidence/reactive-http-client-release-evidence.json`; it
  reports current generated docs, no broken Markdown links, no stale benchmark
  links, and the intentionally pending manual publication work.
- Final immutable native provenance cannot be recorded before the release-prep
  changes are committed because the native workflow rejects dirty source. After
  that clean commit, rerun the native smoke against `3.2.0`; publication remains
  blocked if it fails. Tag/Central publication, public artifact resolution, and
  the post-release `3.3.0-SNAPSHOT`/`3.2.0` baseline transition remain open.

---

## Completion Rule

V22 is complete only when Priorities 1-13 are checked and Priority 14 records an
evidence-backed go or no-go decision. Synthetic response fixtures alone are not
sufficient for protocol, compression, pool, timeout, streaming, or native
ownership claims.
