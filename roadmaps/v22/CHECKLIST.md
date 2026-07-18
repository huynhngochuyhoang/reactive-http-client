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

### [ ] 4.1 Add bounded pool-saturation fixtures

- [ ] Cover queued acquire success and timeout with a one-connection pool.
- [ ] Cover cancellation while queued.
- [ ] Cover idle/lifetime eviction and background eviction.
- [ ] Cover shutdown with active and pending work.
- [ ] Assert no pending waiter or connection leak remains.

### [ ] 4.2 Improve safe saturation diagnostics

- [ ] Preserve existing `ErrorCategory` behavior.
- [ ] Add a failure-stage signal only when the runtime can prove it.
- [ ] Keep any new value bounded and optional.
- [ ] Align lifecycle, observer, exchange-log, health, diagnostics, and docs.
- [ ] Keep server address opt-in.
- [ ] Run compatibility and focused tests.

Evidence:

- Pending.

---

## Priority 5 - Timeout Phase and Terminal-State Parity

### [ ] 5.1 Exercise each timeout boundary

- [ ] Cover connect, pool acquire, request write, response headers, unary body, and stream body.
- [ ] Cover method/client timeout and Resilience4j timeout behavior.
- [ ] Preserve `0 = disabled` and existing precedence.
- [ ] Use real transport where timing ownership matters.

### [ ] 5.2 Align terminal reporting

- [ ] Compare status, headers, attempts, duration, final request metadata, category, and cancellation.
- [ ] Keep streaming-envelope timing separate from inner-body consumption.
- [ ] Ensure mock helpers assert semantics without pretending to emulate network timing.
- [ ] Update timeout and diagnostic-context docs.
- [ ] Run focused tests and `git diff --check`.

Evidence:

- Pending.

---

## Priority 6 - Streaming Backpressure and Upload Ownership

### [ ] 6.1 Prove publisher request-body ownership

- [ ] Keep invocation cold until subscription.
- [ ] Prove one request-body subscription per actual transport attempt.
- [ ] Cover cancellation before and during upload.
- [ ] Cover retry, redirect, auth, and serialization boundaries.
- [ ] Reject unsupported non-repeatable combinations before sending.

### [ ] 6.2 Prove response-buffer ownership

- [ ] Cover direct and envelope `Flux<DataBuffer>`.
- [ ] Release discarded buffers exactly once.
- [ ] Preserve caller ownership after handoff.
- [ ] Align repeatability decisions across runtime, strict validation, diagnostics, docs, and mocks.
- [ ] Run leak-sensitive tests and `git diff --check`.

Evidence:

- Pending.

---

## Priority 7 - OAuth2 Refresh and Token-Service Reliability

### [ ] 7.1 Revalidate concurrent refresh state

- [ ] Deduplicate concurrent refreshes.
- [ ] Prevent one cancelled waiter from cancelling shared refresh work.
- [ ] Cover expiry leeway, absent expiry, invalidation, repeated 401, failure, and recovery.
- [ ] Preserve the logical downstream client name.

### [ ] 7.2 Revalidate safe token failures

- [ ] Cover empty/malformed/oversized 2xx and 4xx/5xx bodies.
- [ ] Cover encoded, nested, escaped, and non-UTF-8 secret-bearing payloads.
- [ ] Preserve safe status, headers, typed decoding, and configured codecs.
- [ ] Preserve custom WebClient status-handler behavior.
- [ ] Verify no credentials appear in messages, causes, request metadata, or snapshots.
- [ ] Run focused auth tests and `git diff --check`.

Evidence:

- Pending.

---

## Priority 8 - Failure Attribution Contract

### [ ] 8.1 Audit the existing taxonomy

- [ ] Cover DNS, connect, TLS, timeout, cancellation, decode, auth, resilience, 4xx, 429, 5xx, and unknown.
- [ ] Keep cause traversal bounded.
- [ ] Preserve the most actionable proven category.
- [ ] Preserve retry terminal cause and subscription-attempt semantics.

### [ ] 8.2 Guard additive public changes

- [ ] Add no public category or accessor without a demonstrated consumer need.
- [ ] Include any addition in japicmp and compatibility fixtures.
- [ ] Align test-helper assertions and public docs.
- [ ] Run strict root/module API checks and focused tests.
- [ ] Run `git diff --check`.

Evidence:

- Pending.

---

## Priority 9 - Diagnostics Schema V1 Evolution

### [ ] 9.1 Preserve schema equivalence

- [ ] Compare provider, collection snapshot, JSON, Markdown, Actuator, and native output.
- [ ] Keep additions nullable and backward-compatible.
- [ ] Preserve distinct unknown, unavailable, disabled, false, and zero states.
- [ ] Reject removals, renames, and type changes in the source-controlled fixture.

### [ ] 9.2 Preserve support-output safety

- [ ] Enforce client, endpoint, field, and UTF-8 byte limits on map and rendered forms.
- [ ] Reject secret-bearing and unbounded fields.
- [ ] Keep concrete URLs, headers, auth identifiers, payloads, and proxy credentials out.
- [ ] Run JVM/native schema fixtures and `git diff --check`.

Evidence:

- Pending.

---

## Priority 10 - Mock and Consumer Parity

### [ ] 10.1 Revalidate mock-owned behavior

- [ ] Cover auth, retry, lifecycle, observers, exchange logging, inherited generics, and repeated headers.
- [ ] Preserve final request metadata.
- [ ] Preserve constructor-injected loggers and application JSON codecs.
- [ ] Do not fake protocol negotiation, TLS, compression wire bytes, pool timing, or connection reuse.

### [ ] 10.2 Revalidate assembled consumers

- [ ] Cover transport-owned behavior with real servers.
- [ ] Run current-reactor and published-`3.1.0` consumers separately.
- [ ] Use fresh repositories and reject reactor leakage.
- [ ] Record separate target-only reports and provenance.
- [ ] Run focused tests and `git diff --check`.

Evidence:

- Pending.

---

## Priority 11 - Dependency, AOT, and Native Matrix

### [ ] 11.1 Revalidate supported dependencies

- [ ] Run minimum and forward Boot 4 rows.
- [ ] Record Framework, Reactor Netty, Netty, Jackson, Micrometer, OTel, and Resilience4j versions.
- [ ] Keep Java 21 as the compilation baseline.
- [ ] Verify optional integrations back off when absent.

### [ ] 11.2 Revalidate AOT/native behavior

- [ ] Preserve inherited client and annotation reflection metadata.
- [ ] Avoid deprecated Framework 7 hint categories.
- [ ] Add at least one V22 contract to the native fixture.
- [ ] Compile and execute with GraalVM 25.
- [ ] Record clean immutable target-only provenance.
- [ ] Run `git diff --check`.

Evidence:

- Pending.

---

## Priority 12 - Benchmark and Allocation Re-Audit

### [ ] 12.1 Preserve harness fairness

- [ ] Run discovery and smoke profiles.
- [ ] Keep current and published-`3.1.0` stacks equivalent.
- [ ] Keep no-network diagnostics separate from loopback scenarios.
- [ ] Add no scenario without an equivalent comparison contract.

### [ ] 12.2 Gather release-quality evidence

- [ ] Run current and published-baseline release profiles from clean inputs.
- [ ] Compare success, JSON, `ResponseEntity`, error, diagnostics, lifecycle, observer, and argument expansion.
- [ ] Investigate material movement before changing production code.
- [ ] Record accepted noise and optimization decisions.
- [ ] Promote a report only for supported numerical release claims.
- [ ] Run report tests and `git diff --check`.

Evidence:

- Pending.

---

## Priority 13 - Documentation and Operations Consolidation

### [ ] 13.1 Update current operational guidance

- [ ] Cover protocol, compression, pool, timeout, streaming, OAuth2, and failure diagnosis.
- [ ] Update sanitized support-bundle recipes.
- [ ] Keep configuration examples metadata-valid and clearly fake.
- [ ] Keep public coordinates on the latest published release.

### [ ] 13.2 Guard generated and historical documentation

- [ ] Separate current commands from historical evidence.
- [ ] Reject stale active baselines without rewriting release history.
- [ ] Run generated configuration/reference checks.
- [ ] Run local Markdown-link and release-note evidence checks.
- [ ] Run `git diff --check`.

Evidence:

- Pending.

---

## Priority 14 - V22 Release Go/No-Go

### [ ] 14.1 Select release scope and version

- [ ] Inventory production, API, configuration, and behavior changes.
- [ ] Select `3.1.x` for fixes/hardening only.
- [ ] Select `3.2.0` only for backward-compatible public additions.
- [ ] Reject or defer incompatible changes.
- [ ] Keep snapshot/public/baseline coordinates separated until release.

### [ ] 14.2 Assemble immutable release evidence

- [ ] Run clean full reactor and strict root/module API checks.
- [ ] Run packaging, current/published consumers, optional integrations, transport, AOT/native, metadata, and docs.
- [ ] Resolve the complete published baseline from a fresh Central repository.
- [ ] Promote or defer benchmark evidence according to claims.
- [ ] Record one target-only readiness snapshot.
- [ ] For go, publish/tag/date/verify one exact commit and version.
- [ ] For no-go, publish nothing and record blockers plus reproduction commands.
- [ ] Move the next snapshot and baseline only after all companion artifacts resolve publicly.
- [ ] Update roadmap/changelog status and run `git diff --check`.

Evidence:

- Pending.

---

## Completion Rule

V22 is complete only when Priorities 1-13 are checked and Priority 14 records an
evidence-backed go or no-go decision. Synthetic response fixtures alone are not
sufficient for protocol, compression, pool, timeout, streaming, or native
ownership claims.
