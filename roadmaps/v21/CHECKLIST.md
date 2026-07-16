# Reactive HTTP Client - Roadmap V21 Execution Checklist

Companion to [`ROADMAP.md`](ROADMAP.md). Execute priorities in order unless a
confirmed release blocker requires reordering. Check an item only after its
implementation, command output, or review artifact is recorded below the
priority. Target-only evidence belongs under `target/release-evidence/`; do not
commit generated evidence as release proof unless the roadmap explicitly calls
for a promoted source-controlled artifact.

The release version is intentionally undecided. Keep `3.1.0-SNAPSHOT` during
development. Priority 12 selects a `3.0.x` patch for fixes-only scope or `3.1.0`
when the completed work contains a backward-compatible public addition.

---

## Priority 1 - Snapshot and Release-Version Contract

### [x] 1.1 Separate development, published, and candidate versions

- [x] Keep the root reactor and every module parent on the same snapshot version.
- [x] Keep current-reactor consumer and native fixtures on that snapshot version.
- [x] Keep README and quick-start dependency snippets on published `3.0.0`.
- [x] Keep `api.compatibility.baseline.version` on published `3.0.0`.
- [x] Keep the `Unreleased` changelog comparison anchored at `v3.0.0`.
- [x] Reject a snapshot from the Central publication workflow.
- [x] Reject a release deployment when the checked-out tag and POM version differ.
- [x] Add tests for snapshot development, release-candidate, and post-publication states.

### [x] 1.2 Make generated release evidence snapshot-aware

- [x] Export development version and latest published consumer version separately.
- [x] Do not propose `docs/benchmark-report-*-SNAPSHOT.md` as a promotable report.
- [x] Use the planned final version only after an explicit release-cut transition.
- [x] Keep public snippet expectations on the published version during snapshot development.
- [x] Verify release-prep status explains pending work without treating a snapshot as released.
- [x] Run `DocumentationReleaseArtifactTest`.
- [x] Run configuration metadata drift tests.
- [x] Run `git diff --check`.

Evidence:

- The generated manifest reports `releaseState=snapshot-development`,
  `developmentVersion=3.1.0-SNAPSHOT`,
  `latestPublishedConsumerVersion=3.0.0`, and `plannedFinalVersion=null`.
- Root properties keep `latest.published.version=3.0.0` independent from
  `api.compatibility.baseline.version=3.0.0`.
- Snapshot evidence reports `promotableReportAvailable=false` and leaves
  `promotedReport` unset until the explicit release-cut transition.
- Central publication rejects snapshots and requires the checked-out tag to be
  exactly `v<project.version>`.
- Passed `mvn -q -s .mvn/maven-central-settings.xml -pl reactive-http-client-starter -Dtest=DocumentationReleaseArtifactTest test`.
- Passed `mvn -q -s .mvn/maven-central-settings.xml -pl reactive-http-client-starter -Dtest=ReactiveHttpClientConfigurationMetadataTest test`.
- Passed `mvn -q -s .mvn/maven-central-settings.xml -pl reactive-http-client-starter test`.
- Passed `git diff --check`.

---

## Priority 2 - Published `3.0.0` Consumer Baseline

### [x] 2.1 Build an isolated published-artifact consumer

- [x] Create or adapt a Boot 4 fixture that resolves starter `3.0.0` from Maven Central.
- [x] Resolve test-helper `3.0.0` from Maven Central.
- [x] Resolve OTel companion `3.0.0` from Maven Central.
- [x] Use a fresh target-local Maven repository that fails if it already exists.
- [x] Record `_remote.repositories` markers for every project artifact.
- [x] Record artifact SHA-256 values and effective POMs as target-only evidence.
- [x] Reject reactor output directories and locally installed project artifacts.

### [x] 2.2 Exercise the published consumer contract

- [x] Cover a direct declarative JSON endpoint.
- [x] Cover inherited generic response decoding.
- [x] Cover configured `@ApiRef` method and path resolution.
- [x] Cover Problem Detail error mapping.
- [x] Cover diagnostics and health with Actuator present.
- [x] Cover test-helper request assertions.
- [x] Cover OTel propagation with the companion module present.
- [x] Keep current-reactor and published-release consumer results in separate reports.
- [x] Add a CI or manually gated fixture command with explicit provenance.
- [x] Run the published consumer from a clean repository.
- [x] Run `git diff --check`.

Evidence:

- Passed `scripts/verify-published-consumer.sh 3.0.0` from an absent
  `target/published-baseline-repositories/consumer-3.0.0` repository.
- The published parent POM plus all three published jars and module POMs contain
  Maven Central remote markers; their SHA-256 values, consumer/module effective
  POMs, dependency tree, isolated classpath,
  provenance, and test report are under
  `target/release-evidence/v21-priority2/published-3.0.0/`.
- `Boot4ConsumerApplicationTest` passed 3 tests with 0 failures and 0 errors
  against published `3.0.0` artifacts.
- A second invocation was rejected because the isolated repository already
  existed, proving the freshness guard does not silently reuse baseline state.
- The manually dispatched `Published Consumer Smoke` workflow uploads published
  evidence separately from current-reactor consumer reports.
- Passed `git diff --check`.

---

## Priority 3 - One Published-Baseline Provenance Contract

### [x] 3.1 Standardize isolated baseline resolution

- [x] Define one target-local repository naming convention for published baselines.
- [x] Require the repository path to be absent before each baseline run.
- [x] Chain every freshness guard directly to its Maven command with `&&`.
- [x] Use `.mvn/maven-central-settings.xml` for public baseline resolution.
- [x] Apply the convention to root API compatibility.
- [x] Apply the convention to module-scoped API compatibility.
- [x] Apply the convention to published-starter benchmark runs.
- [x] Apply the convention to generated release evidence commands.
- [x] Apply the convention to copyable release documentation.

### [x] 3.2 Prove baseline provenance

- [x] Record public artifact checksums and Maven remote markers.
- [x] Reject a repository containing locally installed project artifacts.
- [x] Add a fixture that seeds a conflicting local candidate and proves it is not selected.
- [x] Keep current candidate artifacts out of the published-baseline repository.
- [x] Verify CI, generated manifest, and documentation commands remain identical in purpose.
- [x] Run strict root API compatibility against published `3.0.0`.
- [x] Run strict module-scoped API compatibility against published `3.0.0`.
- [x] Run API compatibility fixtures.
- [x] Run release-documentation tests and `git diff --check`.

Evidence:

- All published lanes now use `target/published-baseline-repositories/<lane>-<version>` and chain the absent-path guard, Maven Central-only invocation, and shared provenance verifier with `&&`.
- Passed strict root API compatibility against published `3.0.0`; Maven Central marker and SHA-256 evidence is under `target/release-evidence/published-baselines/api-root-3.0.0/`.
- Passed strict module-scoped starter compatibility against published `3.0.0`; provenance evidence is under `target/release-evidence/published-baselines/api-starter-3.0.0/`.
- Passed `bash scripts/verify-published-baseline-fixtures.sh`; locally installed same-version artifacts and a repository contaminated with `3.1.0-SNAPSHOT` were rejected.
- Passed `bash scripts/verify-api-compatibility-fixtures.sh`.
- Passed `mvn -q -s .mvn/maven-central-settings.xml -pl reactive-http-client-starter -Dtest=DocumentationReleaseArtifactTest test`.
- Passed `git diff --check`.

---

## Priority 4 - Framework 7 AOT and Runtime-Hint Modernization

### [x] 4.1 Replace deprecated AOT hint usage

- [x] Inventory every production and test use of deprecated `MemberCategory` constants.
- [x] Map each hint to the supported Spring Framework 7 registration API.
- [x] Preserve only the constructors, methods, fields, resources, and proxies needed at runtime.
- [x] Keep inherited public endpoint methods discoverable.
- [x] Keep configuration-properties nested types bindable.
- [x] Keep diagnostics POM metadata resources available in native images.
- [x] Add focused runtime-hint assertions for the replacement registrations.
- [x] Compile production AOT code without the known removal warnings.

### [x] 4.2 Revalidate AOT and native behavior

- [x] Run Spring AOT processing for direct, inherited generic, and `@ApiRef` clients.
- [x] Build the native fixture with the documented GraalVM baseline.
- [x] Run real loopback success, auth, and Problem Detail calls.
- [x] Verify diagnostics endpoint, health, and Micrometer behavior.
- [x] Replace V20-specific native evidence paths with release-independent paths.
- [x] Record project version, commit, Boot version, dependency list, Java, and
      native-image versions.
- [x] Run native-hint documentation tests and `git diff --check`.

Evidence:

- Spring Framework 7 plain type registration supplies introspection metadata; exact `ExecutableMode.INVOKE` registrations retain invocation access for annotation attributes and concrete-client public methods, including inherited endpoints.
- Production and focused AOT test sources contain no `MemberCategory` or `ExecutableMode.INTROSPECT` usage, and the release-documentation guard enforces that contract.
- Focused `ReactiveHttpClientAotSmokeTest` and `DocumentationReleaseArtifactTest` pass with empty member-category assertions and exact invocation hints.
- Clean commit `0dad12152a1a06c9bfd91a5074c8d78ddc696d89`, whose four AOT files exactly match this restored implementation, passed reactor installation, Spring AOT processing, and GraalVM Native Image 25.0.3 compilation in 4m38s with the documented 6 GiB/four-thread bounds.
- The clean native executable completed every real loopback assertion for inherited generic and configured `@ApiRef` calls, auth, Problem Detail, diagnostics sanitization, health, and Micrometer.
- Clean target-only provenance under `target/release-evidence/native-smoke/native-provenance.txt` records GraalVM Java/native-image 25.0.3, Boot 4.0.0, starter `3.1.0-SNAPSHOT`, source commit `0dad12152a1a06c9bfd91a5074c8d78ddc696d89`, and the complete fixture dependency list.

---

## Priority 5 - Transport Resource-Ownership Stress Suite

### [x] 5.1 Add real pooled-transport stress fixtures

- [x] Use a real Reactor Netty server and a bounded client connection pool.
- [x] Cover POST followed by PUT on a reused HTTP/1.1 connection.
- [x] Cover mixed bodied and bodiless success responses.
- [x] Cover unexpected bodies for endpoints declared as `Void`.
- [x] Cover redirect-following enabled and disabled.
- [x] Cover response timeout before headers and after headers.
- [x] Cover cancellation before response body consumption.
- [x] Cover concurrent subscriptions to the same cold client publisher.
- [x] Assert request framing remains transport-owned.

### [x] 5.2 Verify drain, streaming, and retry ownership

- [x] Verify cancellation and timeout paths release or drain starter-owned bodies.
- [x] Verify pooled connections remain reusable after bodiless and error responses.
- [x] Verify `ResponseEntity<Flux<DataBuffer>>` remains owned by the caller until consume or cancel.
- [x] Verify delayed streaming body subscription works against a real `WebClient` transport.
- [x] Verify discarded `DataBuffer` instances are released.
- [x] Verify retry does not silently make non-repeatable bodies repeatable.
- [x] Verify factory destruction completes connection-provider disposal deterministically.
- [x] Assert bounded connection, pending-acquire, and disposal state.
- [x] Run transport-focused tests repeatedly and run `git diff --check`.

Evidence:

- Added `TransportResourceOwnershipStressTest` around a real Reactor Netty server and production `ReactiveHttpClientFactoryBean` with `maxConnections=1` and a bounded pending-acquire timeout.
- The real transport matrix keeps POST then PUT framing transport-owned, reuses one HTTP/1.1 channel across bodied, bodiless, unexpected-body, 5xx-drain, and probe calls, and rejects application-supplied `Content-Length` before exchange.
- Redirect-disabled calls expose 302 while redirect-enabled calls reach the final 200 response on the bounded pool.
- Before-header and between-chunk response timeouts, cancellation after headers but before the first body chunk, cancellation after one chunk, and subsequent probe calls complete without exhausting the pool.
- Concurrent subscriptions to the same cold `Mono` queue behind the one-connection pool, complete independently on the reused channel, and never exceed one active server request.
- Real `ResponseEntity<Flux<DataBuffer>>` envelopes remain consumable after delayed inner subscription; caller-consumed buffers are released explicitly and cancellation returns capacity for a probe call.
- `ReactiveHttpClientFactoryBean.destroy()` now blocks for bounded connection-provider disposal, and the stress fixture verifies `isDisposed()` immediately after return.
- Passed the grouped `Framework7TransportCorrectnessTest`, `ResponseEntitySupportTest`, `StreamingResponseTest`, `ErrorBodyCaptureTest`, and `ReactiveClientInvocationHandlerRetrySafetyTest` ownership regressions.
- Passed `TransportResourceOwnershipStressTest` five consecutive times and `git diff --check`.

---

## Priority 6 - Effective Contract Parity

### [x] 6.1 Build one effective-contract fixture matrix

- [x] Cover directly declared endpoints.
- [x] Cover inherited endpoints.
- [x] Cover nested generic bindings.
- [x] Cover configured `@ApiRef` endpoints.
- [x] Cover method, API-map, client, deprecated, and disabled timeout sources.
- [x] Cover retry, rate limiter, circuit breaker, and bulkhead availability.
- [x] Cover redirect and auth modes.
- [x] Cover request and response generic types.
- [x] Cover invalid method, path variable, API reference, and resilience configurations.

### [x] 6.2 Compare every contract surface

- [x] Compare startup validation with `RequestPlan` resolution.
- [x] Compare runtime invocation with effective contract export.
- [x] Compare contract snapshot output with startup diagnostics.
- [x] Compare lifecycle and observer final metadata with the actual outbound request.
- [x] Compare `MockReactiveHttpClient` behavior with a real production proxy.
- [x] Preserve subscription-local headers, keys, attempt counts, and terminal state.
- [x] Ensure diagnostics do not instantiate lazy auth providers.
- [x] Ensure diagnostics do not create missing resilience instances.
- [x] Keep invalid contracts from being exported or snapshotted as placeholders.
- [x] Run focused parity tests, full starter tests, and `git diff --check`.

Evidence:

- `EffectiveHttpClientContractExporterTest` is the shared matrix for direct, inherited, nested-generic, configured `@ApiRef`, request/response type, timeout-source, resilience-availability, redirect, and auth contracts.
- Effective contracts now export the same sanitized auth mode used by startup diagnostics; deterministic Markdown snapshots include the `Auth` column without provider names or credentials.
- Explicit exports and contract snapshots reject missing `@Retry`, `@RateLimiter`, `@CircuitBreaker`, and `@Bulkhead` instances through non-creating `isInstanceConfigured` checks when resilience is enabled.
- Provider-backed support diagnostics intentionally use non-validating export so lazy diagnostics neither instantiate auth providers nor create or reject missing resilience registry entries before proxy startup.
- Existing startup/runtime parity suites cover invalid verb and body metadata, path variables, API refs, effective timeout precedence, final outbound lifecycle/observer metadata, subscription-local idempotency and attempt state, and real redirect behavior.
- `MockReactiveHttpClientTest` and `Boot4MockReactiveHttpClientTest` pass all 33 helper tests for production-aligned names, URLs, final headers, auth, retries, idempotency, hooks, and logger selection.
- Added test-scope `spring-boot-actuator-autoconfigure` so the packaged Boot 4 auto-configuration application fixture has its complete split-module test classpath.
- Passed 40 focused exporter/snapshot/diagnostics tests, all 742 starter tests, all 33 test-helper tests, and `git diff --check`.

---

## Priority 7 - Diagnostics and Support-Bundle Schema Stability

### [ ] 7.1 Define additive diagnostics schema rules

- [ ] Inventory fields emitted by `rhttpclients` and diagnostics snapshot helpers.
- [ ] Define semantics for enabled, disabled, unavailable, false, and unknown values.
- [ ] Preserve custom diagnostics-provider overrides through Spring proxies.
- [ ] Keep collection-backed snapshot overloads explicit about unavailable provider-only data.
- [ ] Define size, client-count, endpoint-count, and cardinality limits.
- [ ] Add a versioned sanitized fixture for schema regression review.
- [ ] Reject accidental field removal or semantic reinterpretation in a minor release.

### [ ] 7.2 Re-audit support output safety and ownership

- [ ] Verify no credentials, provider secrets, sensitive headers, or raw bodies are exported.
- [ ] Verify no machine-local paths are exported into source-controlled support artifacts.
- [ ] Verify client names, URLs, and configuration sources follow existing sanitization rules.
- [ ] Align health, diagnostics, lifecycle, observer, and exchange-log metadata documentation.
- [ ] Keep endpoint exposure and health details opt-in.
- [ ] Re-run support-bundle fixture and native endpoint coverage.
- [ ] Run documentation, metadata, Markdown-link, and API compatibility tests.
- [ ] Run `git diff --check`.

Evidence:

- Pending.

---

## Priority 8 - `2.x` Maintenance-Lane Reproducibility

### [ ] 8.1 Reconstruct the latest Boot 3.5 release lane

- [ ] Identify the immutable latest `2.x` release tag and maintenance branch policy.
- [ ] Build the latest `2.x` release from a clean detached worktree.
- [ ] Resolve its published API baseline from an isolated repository.
- [ ] Verify its dependency tree remains on Boot 3.5 and Jackson 2.
- [ ] Verify its artifacts contain no Boot 4 or Jackson 3 implementation classes.
- [ ] Record commands, effective POMs, checksums, and dependency evidence target-only.

### [ ] 8.2 Rehearse a critical-fix release

- [ ] Document how a security or critical transport fix is applied to the `2.x` lane.
- [ ] Define forward-port ordering from `2.x` to `3.x` where code is shared.
- [ ] Verify `2.x` API compatibility against its correct published predecessor.
- [ ] Verify source, Javadoc, signing, and Central staging remain possible.
- [ ] Confirm normal `3.x` builds never compile Boot 3 adapters.
- [ ] Update maintenance documentation without inventing an EOL date.
- [ ] Run maintenance-lane checks and `git diff --check`.

Evidence:

- Pending.

---

## Priority 9 - Dependency and Supported-Matrix Review

### [ ] 9.1 Resolve and record the supported matrix

- [ ] Re-evaluate the minimum supported Spring Boot 4 line when execution starts.
- [ ] Re-evaluate the current forward-compatibility Spring Boot 4 line.
- [ ] Resolve both rows from clean Maven repositories.
- [ ] Record managed Spring Framework, WebFlux, Reactor Netty, Netty, Jackson,
      Micrometer, OTel, Resilience4j, JUnit, and Mockito versions.
- [ ] Keep Java 21 as the minimum unless a separate decision approves a change.
- [ ] Document why each retained or changed baseline is supported.

### [ ] 9.2 Validate each matrix row

- [ ] Run full reactor tests.
- [ ] Run generated configuration metadata and documentation tests.
- [ ] Run the assembled current-reactor consumer.
- [ ] Run optional-integration presence and absence tests.
- [ ] Run AOT processing.
- [ ] Run transport-focused tests.
- [ ] Run strict API compatibility against published `3.0.0`.
- [ ] Record dependency provenance in generated release evidence.
- [ ] Run `git diff --check`.

Evidence:

- Pending.

---

## Priority 10 - Benchmark the First Post-`3.0.0` Line

### [ ] 10.1 Validate the harness and scenario contract

- [ ] Build the benchmark module with the current reactor.
- [ ] Run benchmark smoke to verify discovery and report generation.
- [ ] Verify every benchmark prefix maps to an explicit report classification.
- [ ] Keep loopback comparisons limited to equivalent work.
- [ ] Keep no-network invocation and diagnostics rows in separate classifications.
- [ ] Verify environment metadata records snapshot/candidate, dependencies, and commit correctly.

### [ ] 10.2 Gather release-quality comparison evidence manually

- [ ] **Manual:** run the current candidate benchmark from a clean immutable commit.
- [ ] **Manual:** run published starter `3.0.0` with the chained fresh-repository guard.
- [ ] **Manual:** verify Maven Central markers and baseline artifact checksums.
- [ ] **Manual:** compare paired current and published-baseline JMH JSON reports.
- [ ] Review default success, JSON, `ResponseEntity`, and error-mapping rows.
- [ ] Review diagnostics, lifecycle, observer, and request-expansion allocation rows.
- [ ] Investigate regressions before changing implementation.
- [ ] Keep review thresholds advisory rather than hard CI gates.
- [ ] Promote a source-controlled report only if release notes make numerical claims.
- [ ] Otherwise record an explicit benchmark-report deferral.
- [ ] Run benchmark report/documentation tests and `git diff --check`.

Evidence:

- Pending.

---

## Priority 11 - Adoption Feedback and Documentation Consolidation

### [ ] 11.1 Improve post-`3.0.0` adoption guidance

- [ ] Collect confirmed Boot 4 dependency and classpath failure modes from fixtures or reports.
- [ ] Add diagnosis steps for WebClient customizers, Actuator health, Jackson 3,
      and optional modules.
- [ ] Keep public Maven examples on the latest released coordinates.
- [ ] Keep internal current-reactor instructions explicitly marked as snapshot development.
- [ ] Compile code examples against published artifacts where practical.
- [ ] Validate every `reactive.http.*` example against configuration metadata.
- [ ] Use clearly fake hosts, identifiers, and credentials.

### [ ] 11.2 Separate current instructions from historical evidence

- [ ] Identify duplicated current commands across release, benchmark, native, and migration docs.
- [ ] Keep one authoritative current command and link to it from related guides.
- [ ] Preserve V18-V20 historical evidence without presenting it as current work.
- [ ] Preserve valid historical benchmark and API report links.
- [ ] Do not add public API solely for documentation convenience.
- [ ] Run code-snippet consumers where available.
- [ ] Run documentation release tests and Markdown-link checks.
- [ ] Run `git diff --check`.

Evidence:

- Pending.

---

## Priority 12 - Next-Release Go/No-Go

### [ ] 12.1 Select the release scope and candidate version

- [ ] Inventory all completed production, public API, configuration, and behavior changes.
- [ ] Select `3.0.x` when the scope contains fixes and hardening only.
- [ ] Select `3.1.0` only when the scope contains a backward-compatible public addition.
- [ ] Reject or defer any binary/source-incompatible change.
- [ ] Record the decision and rationale in the roadmap and changelog.
- [ ] Keep `-SNAPSHOT` until all implementation priorities and mandatory evidence are complete.
- [ ] Update candidate coordinates, docs, fixtures, and generated evidence together.

### [ ] 12.2 Assemble release evidence and decide

- [ ] Run a clean full-reactor `verify` from the candidate commit.
- [ ] Run strict root and module-scoped API compatibility against published `3.0.0`.
- [ ] Run API compatibility fixtures.
- [ ] Build and inspect binary, source, and Javadoc artifacts.
- [ ] Run current-reactor and published-`3.0.0` consumers.
- [ ] Run optional-integration presence and absence suites.
- [ ] Run AOT processing and the native executable.
- [ ] Run transport resource-ownership tests.
- [ ] Run generated metadata, release-documentation, and Markdown-link checks.
- [ ] Resolve every public baseline artifact from fresh repositories.
- [ ] Promote or explicitly defer benchmark evidence based on release-note claims.
- [ ] Generate one target-only readiness snapshot with exact commands and provenance.
- [ ] Confirm the candidate commit is clean and immutable.
- [ ] For **go**, publish, verify Central resolution, tag the exact commit, and date the changelog.
- [ ] For **no-go**, publish nothing and record every blocker with reproduction steps.
- [ ] Move the next snapshot and compatibility baseline only after all companion
      artifacts resolve publicly.
- [ ] Update `ROADMAP.md` status after the decision evidence exists.
- [ ] Run final release-documentation tests and `git diff --check`.

Evidence:

- Pending.

---

## Completion Rule

V21 is complete only when Priorities 1-11 are checked and Priority 12 records an
evidence-backed go or no-go decision. A successful local reactor build is not
sufficient without published-artifact consumption, isolated baseline
provenance, native/transport evidence, and a semantic-versioning decision based
on the delivered scope.
