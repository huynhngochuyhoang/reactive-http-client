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

### [x] 7.1 Define additive diagnostics schema rules

- [x] Inventory fields emitted by `rhttpclients` and diagnostics snapshot helpers.
- [x] Define semantics for enabled, disabled, unavailable, false, and unknown values.
- [x] Preserve custom diagnostics-provider overrides through Spring proxies.
- [x] Keep collection-backed snapshot overloads explicit about unavailable provider-only data.
- [x] Define size, client-count, endpoint-count, and cardinality limits.
- [x] Add a versioned sanitized fixture for schema regression review.
- [x] Reject accidental field removal or semantic reinterpretation in a minor release.

### [x] 7.2 Re-audit support output safety and ownership

- [x] Verify no credentials, provider secrets, sensitive headers, or raw bodies are exported.
- [x] Verify no machine-local paths are exported into source-controlled support artifacts.
- [x] Verify client names, URLs, and configuration sources follow existing sanitization rules.
- [x] Align health, diagnostics, lifecycle, observer, and exchange-log metadata documentation.
- [x] Keep endpoint exposure and health details opt-in.
- [x] Re-run support-bundle fixture and native endpoint coverage.
- [x] Run documentation, metadata, Markdown-link, and API compatibility tests.
- [x] Run `git diff --check`.

Evidence:

- Diagnostics JSON, map, Markdown, and the opt-in `rhttpclients` endpoint now declare additive `schemaVersion=1`; existing v1 fields retain their names, types, and meanings within the `3.x` line.
- `disabled` identifies policy that is not applied, `unavailable` identifies enabled resilience whose optional runtime operator is absent, provider-backed strict flags use true/false, and collection-backed provider-only flags remain null/unknown.
- Snapshot rendering preserves overridden `clientSummaries()` behavior through class-based Spring proxies and never resolves lazy auth providers or creates missing resilience instances.
- Rendering rejects partial or ambiguous output beyond 256 clients, 10,000 aggregate endpoints, 512 characters per exported text field, or 1 MiB of UTF-8 encoded JSON/Markdown; count fields therefore describe every emitted client.
- Added `docs/fixtures/rhttpclients-schema-v1.json` as the exact sanitized schema fixture. Regression tests reject credentials, sensitive headers, bodies, concrete URLs, provider bean names, and common machine-local path prefixes.
- `docs/21-diagnostic-contexts.md` now inventories schema fields, value semantics, limits, proxy behavior, and metadata ownership; `docs/26-support-bundles.md` links the schema contract while retaining explicit endpoint exposure and health-detail opt-in.
- Passed focused diagnostics, effective-contract, endpoint, documentation, metadata, Markdown-link, and AOT tests; passed all 743 starter tests.
- Passed strict starter API compatibility against published `3.0.0` from `target/published-baseline-repositories/api-priority7-3.0.0`; Maven Central markers and hashes passed the shared provenance verifier, and API compatibility fixtures passed.
- Rebuilt the GraalVM 25.0.3 native image in 4m07s under the documented 6 GiB/four-thread bounds; the executable passed real loopback calls plus diagnostics schema, sanitization, health, auth, Problem Detail, and Micrometer assertions.
- Passed `git diff --check`.

---

## Priority 8 - `2.x` Maintenance-Lane Reproducibility

### [x] 8.1 Reconstruct the latest Boot 3.5 release lane

- [x] Identify the immutable latest `2.x` release tag and maintenance branch policy.
- [x] Build the latest `2.x` release from a clean detached worktree.
- [x] Resolve its published API baseline from an isolated repository.
- [x] Verify its dependency tree remains on Boot 3.5 and Jackson 2.
- [x] Verify its artifacts contain no Boot 4 or Jackson 3 implementation classes.
- [x] Record commands, effective POMs, checksums, and dependency evidence target-only.

### [x] 8.2 Rehearse a critical-fix release

- [x] Document how a security or critical transport fix is applied to the `2.x` lane.
- [x] Define forward-port ordering from `2.x` to `3.x` where code is shared.
- [x] Verify `2.x` API compatibility against its correct published predecessor.
- [x] Verify source, Javadoc, signing, and Central staging remain possible.
- [x] Confirm normal `3.x` builds never compile Boot 3 adapters.
- [x] Update maintenance documentation without inventing an EOL date.
- [x] Run maintenance-lane checks and `git diff --check`.

Evidence:

- `v2.14.1` is the immutable latest `2.x` release tag at commit
  `f0a1989eb7d19c702c530301798dc34fa4d3819b`; maintenance branches are created
  from that tag only for approved security, critical correctness, or transport
  fixes and removed after release. No `2.x` EOL date is declared.
- `scripts/verify-maintenance-lane.sh` rebuilt that tag from a clean detached
  worktree and passed 716 starter, 32 test-helper, and 38 OTel tests.
- The reconstructed dependency trees retain Spring Boot `3.5.16` and Jackson
  Databind `2.21.4`; binary and source artifacts contain no Boot 4 or Jackson 3
  implementation classes.
- Strict japicmp checks passed against published `2.14.0`, resolved through a
  dedicated Maven Central-only repository. Parent and module POM/JAR remote
  markers and SHA-256 checksums were verified by the shared provenance guard.
- The rehearsal produced binary, source, and Javadoc artifacts for every module
  and verified release-profile GPG signing plus Central publishing-extension
  wiring. Credentialed signing and deployment remain manual release actions.
- Current `3.x` tests passed with 743 starter, 33 test-helper, and 38 OTel tests;
  focused source and artifact checks found no Boot 3 adapters.
- Target-only commands, effective POMs, dependency trees, artifact inventories,
  checksums, and provenance are under
  `target/release-evidence/maintenance-2x-2.14.1/`.
- Passed `bash scripts/verify-maintenance-lane-fixtures.sh`,
  `bash scripts/verify-maintenance-lane.sh v2.14.1 2.14.0`, and
  `git diff --check`.

---

## Priority 9 - Dependency and Supported-Matrix Review

### [x] 9.1 Resolve and record the supported matrix

- [x] Re-evaluate the minimum supported Spring Boot 4 line when execution starts.
- [x] Re-evaluate the current forward-compatibility Spring Boot 4 line.
- [x] Resolve both rows from clean Maven repositories.
- [x] Record managed Spring Framework, WebFlux, Reactor Netty, Netty, Jackson,
      Micrometer, OTel, Resilience4j, JUnit, and Mockito versions.
- [x] Keep Java 21 as the minimum unless a separate decision approves a change.
- [x] Document why each retained or changed baseline is supported.

### [x] 9.2 Validate each matrix row

- [x] Run full reactor tests.
- [x] Run generated configuration metadata and documentation tests.
- [x] Run the assembled current-reactor consumer.
- [x] Run optional-integration presence and absence tests.
- [x] Run AOT processing.
- [x] Run transport-focused tests.
- [x] Run strict API compatibility against published `3.0.0`.
- [x] Record dependency provenance in generated release evidence.
- [x] Run `git diff --check`.

Evidence:

- Retained Java 21 and Spring Boot `4.0.0` as the minimum/default contract;
  retained Boot `4.1.0` as a forward-compatibility row without moving the
  consumer baseline or requiring migration.
- Boot `4.0.0` resolved Framework/WebFlux `7.0.1`, Reactor Netty `1.3.0`,
  Netty `4.2.7.Final`, Jackson `3.0.2`, Micrometer `1.16.0`, OTel `1.55.0`,
  JUnit `6.0.1`, and Mockito `5.20.0`.
- Boot `4.1.0` resolved Framework/WebFlux `7.0.8`, Reactor Netty `1.3.6`,
  Netty `4.2.15.Final`, Jackson `3.1.4`, Micrometer `1.17.0`, OTel `1.62.0`,
  JUnit `6.0.3`, and Mockito `5.23.0`. Resilience4j remains independently
  managed at `2.4.0` on both rows.
- Both rows passed under Oracle JDK `21.0.8`: 743 starter, 33 test-helper, and
  38 OTel tests plus all three assembled-consumer scenarios. These runs include
  generated metadata/docs, optional presence/back-off, Spring AOT, and real
  transport ownership coverage.
- Strict japicmp passed against published `3.0.0` on both the Boot `4.0.0` and
  `4.1.0` managed classpaths; each row uses separate Maven Central provenance,
  remote-marker, and SHA-256 evidence.
- Failure-safe exit handling copies completed and partial row Surefire,
  consumer, japicmp, dependency, and provenance evidence into the workflow
  upload path while preserving the failing command's exit status.
- Effective POMs, dependency trees, resolved versions, commands, Surefire
  reports, and provenance are under
  `target/release-evidence/v21-priority9/`.
- Passed `scripts/verify-supported-matrix.sh` and `git diff --check`.

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
