# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [Unreleased]

### Changed

- **Declarative return-type grammar.** Added one inherited-generic-aware startup
  validator for supported `Mono`, `Flux`, `ResponseEntity`, and raw
  `DataBuffer` streaming shapes; nested publishers and ambiguous response
  envelopes now fail before proxy creation with concrete client, declaring
  method, resolved type, and supported-shape context. Factory startup,
  effective contracts, diagnostics, AOT processing, runtime envelope handling,
  and `MockReactiveHttpClient` now use the same decision. AOT validation is
  limited to starter factory beans and honors replacement metadata caches;
  inherited arrays and wildcard bounds are materialized before unresolved
  generic checks, bounded unresolved variables remain invalid, and publisher
  arrays are rejected as nested reactive values. Parameterized owner bindings
  are resolved for runtime codecs and diagnostics, while diagnostics skip this
  starter-only grammar for clients supplied by replacement factory beans. AOT
  processing recognizes starter factories declared through `@Bean` methods,
  diagnostics applies the same ownership decision, wildcard-wrapped
  `ResponseEntity` envelopes are rejected at startup, and wildcard member types
  retain their parameterized owner bindings for codecs and contract export.
  `ResponseEntity` subclasses and wildcard `DataBuffer` streams are also
  rejected before they can bypass envelope or pooled-buffer ownership handling;
  nested `ResponseEntity` types are rejected recursively within envelope bodies.
  Resolved parameterized array types now use structural `GenericArrayType`
  equality so reflection and codec type tokens recognize equivalent arrays.
  Wildcard `Void` responses and concrete `DataBuffer` subtypes now fail at
  startup rather than bypassing bodiless or raw-buffer ownership handling. Raw
  generic element classes retain raw codec semantics, and nested DTOs no longer
  inherit publisher classification from an enclosing owner class while reactive
  owner type arguments remain invalid. Envelope-body inspection likewise ignores
  enclosing `ResponseEntity` classes while still rejecting `ResponseEntity`
  values carried in owner type arguments, collections, arrays, and other ordinary
  reactive response element graphs.
- **Resilience composition contract.** Froze the internal transformation and
  subscription wrapper order across per-attempt request timeouts and the logical
  call budget; documented one outer rate-limiter, circuit-breaker, and bulkhead
  admission around retry attempts; made delayed rate-limiter admission cancel
  with the logical call; aligned unavailable-operator startup diagnostics; and
  added deterministic timeout, retry-exhaustion, cancellation, permit, terminal
  lifecycle, exchange-log, observer, and Micrometer evidence.
- **Roadmap archive integrity.** Added the V24 execution checklist, normalized
  V1-V24 archive status and sibling links without rewriting historical planning
  boxes as release evidence, and added a normal documentation test that rejects
  missing roadmap directories, index/checklist links, and conflicting lifecycle
  status.
- **Open the post-`3.3.0` development line.** Verified the complete `3.3.0` parent, starter, test-helper, and OTel release bundle from Maven Central; moved public examples plus API, consumer, and benchmark baselines to `3.3.0`; and advanced reactor-only coordinates to `3.4.0-SNAPSHOT` so compatibility checks cannot compare the current reactor with itself.

## [3.3.0] - 2026-07-26

### Changed

- **Streaming upload wire contract.** Added raw `DataBuffer`, `InputStream`, `Reader`, and `ReadableByteChannel` request writers with transport-owned HTTP/1.1 framing and close/discard ownership; preserved continuous caller-selected charset encoding for `Reader` bodies without JSON codec selection; bypassed auth JSON materialization for raw and streaming body shapes; added pre-write cancellation and logical-timeout cleanup for eager streams and direct buffers; preserved application-owned replay warnings and pre-dispatch SigV4 rejection; and added real-wire coverage for known-length resources, chunked streams, peer disconnects, bounded demand, pooled-buffer release, retry/redirect/auth subscriptions, payload-hash equality, and request-write attribution.
- **OAuth2 token-service transport isolation.** Built-in object-style OAuth2 client credentials now use a client-owned token-service connector and pool with explicit timeout, proxy/TLS, and opt-in transient retry policy. Token calls no longer inherit business filters, redirects, resilience operators, or transport customizers; single-flight refresh, one-time `401` replay, logical client attribution, sanitized causes, and resource disposal remain intact.
- **Open the post-`3.2.0` development line.** Verified the complete `3.2.0` parent, starter, test-helper, and OTel release bundle from Maven Central; moved public examples plus API, consumer, and benchmark baselines to `3.2.0`; advanced reactor-only coordinates to `3.3.0-SNAPSHOT`; and made published-release verification resolve from a neutral consumer model so the current reactor cannot satisfy its own parent POM.
- **End-to-end timeout budget.** Added opt-in `logical-call-timeout-ms` as one subscription-local deadline across resilience admission, auth, pool acquisition, redirects, retries, and starter-owned response consumption; preserved existing per-attempt timeout precedence and streaming-envelope ownership; guarded retry cleanup by attempt number; and aligned final-attempt failure stages, diagnostics, metadata, docs, and test-helper support.
- **Protocol-aware pool capacity.** Added address-free per-client pool meters under the starter-owned `reactive.http.client.connection.pool.*` namespace so Reactor Netty built-in metric families can coexist; separated HTTP/1.1 pending connections from HTTP/2 active/pending streams, retained unknown peer stream limits in sanitized diagnostics, and added real one-connection H1/H2 pressure coverage for queueing, cancellation, timeout, reuse, and owned shutdown.
- **Compression aggregate boundaries.** Enforced decoded codec limits after gzip for unary values and `ResponseEntity<T>`, preserved independent decoded error-retention and bodiless drain policies, kept `DataBuffer` streams incremental and caller-owned, closed corrupt gzip connections before pool reuse, and exposed the configured compression/aggregate policy in sanitized diagnostics.
- **Pre-response failure attribution.** Added bounded `DNS_RESOLUTION`, `PROXY_CONNECT`, and `TLS_HANDSHAKE` stages alongside expanded `CONNECT` evidence; preserved the existing `ErrorCategory` mapping; prevented nested auth-provider and pre-dispatch custom-filter failures, including direct write-timeout signals, from being promoted into business-request transport stages; and aligned real DNS, proxy, connect, TLS, certificate, lifecycle, logging, metrics, health, mock, support-bundle, and operations contracts.

## [3.2.0] - 2026-07-21

### Changed

- **Complete published-baseline provenance.** Added one fresh-repository Maven Central check for the published parent plus starter, test-helper, and OTel POM, binary, source, and Javadoc artifacts; generated readiness evidence now uses that complete bundle, and current migration/consumer guidance consistently tracks published `3.1.0`.
- **Real HTTP/2 contract evidence.** Added starter-proxy integration coverage for TLS H2/ALPN, clear-text H2C, unchanged HTTP/1.1 defaults, unary and streaming response shapes, bounded concurrent streams, cancellation, reset, timeout, mapped errors, TLS protocol/cipher composition, and provider shutdown.
- **Compression and content-encoding correctness.** Defined `compression-enabled` as response negotiation and incremental decompression only, added real gzip/identity/empty/error/envelope/streaming coverage, rejected application `Accept-Encoding` while connector compression is active, and aligned observer byte metrics with post-transport advertised lengths without consuming streams.
- **Pool saturation and acquisition diagnostics.** Added deterministic one-connection fixtures for queued success, acquire timeout, queued cancellation, idle/lifetime background eviction, and shutdown with active/pending work; introduced optional bounded `POOL_ACQUIRE` attribution while preserving `ErrorCategory`, and aligned metrics, OTel, health, support snapshots, and troubleshooting guidance.
- **Timeout phase and terminal-state parity.** Added bounded `CONNECT`, `REQUEST_WRITE`, `RESPONSE_HEADERS`, and `RESPONSE_BODY` failure-stage attribution alongside `POOL_ACQUIRE`; aligned lifecycle, exchange-log, observer, Micrometer, and OTel terminal metadata; required final-attempt request-dispatch evidence for response-header attribution while preserving concrete pre-response stages without URL evidence; added real-wire response-header, unary-body, streaming, cancellation, envelope, precedence, and disabled-timeout contracts; and added explicit mock terminal-failure support without implying network timing simulation.
- **Streaming upload ownership.** Added real-wire publisher upload coverage for cold invocation, bounded demand, cancellation before acquisition and during writes, retry, body-preserving redirect, one-time 401 refresh, DTO encoding, and reusable multipart resources; aligned runtime, strict signing, diagnostics, documentation, and mock-helper repeatability contracts without buffering publisher bodies; and conservatively classifies `Object`, erased generic, and Java stream declarations as application-owned.
- **OAuth2 refresh and token-service reliability.** Made shared refresh failures retain each waiter's logical downstream client name, preserved cancellation-safe single-flight caching and one-time `401` refresh behavior, sanitized credential-bearing token-response headers while retaining safe status/header/codec diagnostics, and expanded refresh, recovery, token-body, lifecycle, observer, and secret-leak regression coverage.
- **Failure attribution parity.** Made error-category extraction one bounded outer-to-inner cause walk, preserved wrapped HTTP and auth categories through retry infrastructure, retained final retry cause and subscription-attempt semantics, and aligned test-helper assertions with runtime diagnostics without adding public categories or accessors.
- **Diagnostics schema v1 evolution.** Froze provider, collection, JSON, Markdown, Actuator, and native output on the reviewed additive v1 field set and the published `3.2.0` key/type baseline; preserved nullable unknown states without instantiating lazy clients, auth providers, or Retry instances; enforced deterministic health ordering plus existing cardinality, field, and UTF-8 byte limits; and deferred request-scoped transport facts that cannot be represented accurately as configured-client state.
- **Mock and consumer parity.** Added fresh-repository current and published consumer verifiers with stage-aware failure provenance and current-run Surefire preservation; proved constructor-injected exchange loggers and auth-aware application JSON codec bytes against a real loopback server; retained focused timeout, dispatch, OAuth2 refresh, upload, diagnostics, and final-request mock coverage; rejected reactor classpath leakage; and kept protocol, TLS, compression, pool, backpressure, and connection-reuse claims on real transports.
- **Dependency, AOT, and native matrix.** Revalidated the full reactor, assembled consumer, optional-integration back-off, resolved dependency versions, and strict API compatibility on Spring Boot `4.0.0` and `4.1.0` under Java 21; expanded the GraalVM 25 native fixture with real gzip negotiation/decompression and clean-commit executable provenance guards.
- **Benchmark fairness contract.** Added a discovery profile and fail-fast validation that keeps no-network diagnostics separate and requires every comparable loopback scenario to provide exactly one raw `WebClient`, Spring HTTP Interface, and starter row before smoke or release measurements run.
- **Operations documentation consolidation.** Added one current-release incident triage index for protocol, compression, pool, timeout, streaming, OAuth2, and failure attribution; expanded sanitized support-bundle evidence recipes; and kept active consumer guidance separate from immutable historical release evidence.

## [3.1.0] - 2026-07-17

### Changed

- **Snapshot-aware release evidence.** Separated the development reactor, latest published consumer, and explicit release-candidate versions in generated release evidence; deferred promotable performance artifact paths while the reactor is a snapshot; and required Maven Central publication to run from the matching release tag.
- **Published consumer baseline.** Added a manually gated Boot 4 consumer run that resolves starter, test-helper, and OTel `3.0.0` artifacts from a fresh Maven Central-only repository and records remote markers, effective POMs, hashes, dependency provenance, and separate test results.
- **Published-baseline provenance.** Standardized API compatibility, benchmark, generated release, documentation, and consumer baseline resolution on fresh lane-specific Maven Central repositories; added shared remote-marker, checksum, and candidate-contamination verification.
- **Framework 7 AOT hints.** Replaced deprecated broad reflection categories with exact constructor and method registrations, preserved concrete inherited endpoint and marker-annotation metadata plus native configuration/diagnostics resources, and moved native provenance to a release-independent evidence path.
- **Transport resource ownership.** Added a bounded single-connection stress suite for framing, mixed and unexpected bodies, errors, redirects, timeouts, cancellation, concurrent cold subscriptions, and delayed streaming ownership; factory shutdown now waits for connection-provider disposal.
- **Effective contract parity.** Added sanitized auth mode to effective-contract and Markdown snapshot output, aligned request-plan, timeout, resilience, redirect, generic-type, diagnostics, and snapshot fixtures, and now rejects undefined method-level resilience instances from explicit exports without mutating support diagnostics.
- **Diagnostics schema stability.** Versioned sanitized diagnostics and `rhttpclients` output as additive schema v1, distinguished disabled, unavailable, false, and unknown states, bounded support-output cardinality and size, and added a source-controlled regression fixture plus JVM and native endpoint coverage.
- **Boot 3.5 maintenance reproducibility.** Added a detached-worktree rehearsal for immutable `v2.14.1` that verifies the published `2.14.0` API predecessor from an isolated Maven Central repository, Boot 3.5/Jackson 2 dependency and artifact ownership, release-profile packaging, target-only provenance, and the critical-fix forward-port policy.
- **Supported dependency matrix.** Retained Java 21 and Spring Boot `4.0.0` as the minimum, added full-reactor and assembled-consumer verification for the Boot `4.1.0` forward row, and now records managed Framework, transport, Jackson, Micrometer, OTel, Resilience4j, JUnit, and Mockito versions from fresh repositories.
- **JDK 25 release metadata packaging.** Configured the Spring Boot configuration processor explicitly so clean release builds continue to package generated configuration metadata when annotation processors are no longer discovered implicitly.
- **V21 release decision.** Selected `3.1.0` because the diagnostics schema and effective-contract output are backward-compatible public additions. All release gates passed (full reactor, strict API compatibility, packaging, consumer, transport, AOT/native, documentation, published-baseline), and `3.1.0` was published to and resolves from Maven Central. Benchmark promotion stays deferred because this release makes no numerical performance claim.

## [3.0.0] - 2026-07-15

### Changed

- **Default Spring Boot 4 reactor.** Moved the project to `3.0.0` with Spring Boot `4.0.0`, Resilience4j `2.4.0`, and published `2.14.1` as the cross-major compatibility baseline; promoted Boot 4/Jackson 3 adapters into normal sources and removed the non-publishing `boot4-spike` profile.
- **Jackson 3 codec ownership.** Made `ReactiveHttpClientJsonCodec` the stable serialization boundary, removed deprecated Jackson 2 adapters and mapper constructors at the `3.0.0` boundary, and removed Jackson 2 from the starter and test-helper dependency graphs while preserving configured-codec behavior for Problem Detail, OAuth2 errors, SigV4 signing, and mocks.
- **Generation-specific release packaging.** Added a CI packaging guard that aligns normal Boot 4 sources with attached source and Javadoc artifacts, rejects stale Boot 3 or duplicate jar entries, and verifies auto-configuration, runtime-hint, and configuration-metadata resources are packaged once.
- **Publishable module POMs.** Enabled deployment for the parent, starter, test-helper, and OTel artifacts; centralized sibling dependency versions; corrected inherited project, SCM, and issue metadata; made Central publication manual by default; and added signed local staging plus isolated-consumer validation before the final release deploy.
- **Boot 4 runtime and native contracts.** Revalidated WebClient, Jackson, metrics, and health auto-configuration ordering; configuration metadata and optional integration back-off; and a resource-bounded GraalVM 25 executable covering inherited generic and configured `@ApiRef` endpoints, auth, Problem Detail, sanitized diagnostics, health, and Micrometer metrics.
- **Boot 4 assembled-consumer and helper parity.** Expanded the independent artifact consumer across repeated headers, redirects, bodiless and streaming ownership, `ResponseEntity`, timeout and Problem Detail errors, lifecycle, observer, diagnostics, health, Micrometer, and OTel; revalidated mock naming, final metadata, retries, idempotency, codec/signing bytes, injected loggers, hook ordering, and optional integration back-off.
- **Frozen `3.0.0` public surface.** Audited the report-only `2.14.1` cross-major API delta, classified the Boot 4 health replacement and deprecated Jackson 2 removals, and added a CI guard that rejects any unreviewed incompatible row while preserving self-baseline and compatibility-fixture checks.
- **Transport regression evidence.** Carried POST-then-PUT HTTP/1.1 reuse into the real `3.0.0` starter proxy and single-connection pool, verified transport-generated framing and response ownership, and isolated Reactor Netty's synthetic `GET /bad-request HTTP/1.0` to deliberately malformed raw wire input rather than normal starter request construction.
- **Boot 4 benchmark baseline.** Revalidated the complete same-stack smoke harness on the default `3.0.0` Boot 4 reactor, including equivalent raw WebClient, Spring HTTP Interface, and starter loopback work plus separately classified optional-feature and no-network rows. The smoke output remains target-only and no report is promoted.
- **`3.0.0` migration and operations guide.** Completed the Boot 3.5/`2.14.1` to Boot 4/`3.0.0` adoption path with dependency, package, Jackson 3 codec, configuration, Actuator, native-image, test-helper, constructor-injected logger, staged-consumer, maintenance-lane, and production support guidance.

## [2.14.1] - 2026-07-13

### Changed

- **Post-2.14 maintenance transition.** The Boot 3.5 maintenance reactor now targets `2.14.1` while API compatibility and published-baseline benchmark evidence compare against published `2.14.0`; Spring Boot 4 migration remains isolated for the future `3.x` line.
- **Spring Boot 3.5 migration bridge.** Updated the default managed dependency baseline from Spring Boot `3.5.0` to the latest published `3.5.16` patch, retained `3.5.0` as the minimum release-smoke row, and removed use of a deprecated Reactor Netty TLS API before the isolated Boot 4 migration.
- **Isolated Spring Boot 4 build spike.** Added a non-publishing `boot4-spike` profile and Maven Central settings file, recorded the Boot `4.0.0`/`4.1.0` managed dependency matrix, and classified starter, test-helper, OTel, and benchmark compile outcomes without changing the Boot 3.5 release line.
- **Spring Boot 4 module migration.** Added profile-selected Boot 4 WebClient and health adapters backed by focused `spring-boot-webclient` and `spring-boot-health` modules, preserved OTel propagation through generation-specific Boot customizer bridges, and added Boot 4 auto-configuration and optional-Actuator regression coverage without adding `starter-classic`.
- **Jackson 3 codec ownership.** Added `ReactiveHttpClientJsonCodec`, generation-specific Jackson adapters, exact shared signing/wire bytes for authenticated JSON, Jackson 3 Problem Detail support, and deprecated Jackson 2 compatibility shims for migration.
- **Framework 7 transport correctness.** Added real HTTP/1.1 POST-then-PUT connection-reuse and malformed-framing regressions, revalidated streaming/bodiless/redirect/timeout/error ownership on Boot 4, and rejected application-supplied framing and authority headers so Reactor Netty remains the sole transport owner.
- **Boot 4 optional integrations.** Moved the isolated Boot 4 lane to Resilience4j `2.4.0`, verified independent resilience operators and metrics, minimal-classpath back-off, Micrometer health/observer behavior, OTel propagation/semantics, OAuth2, and SigV4, while preserving optional dependency boundaries.
- **Boot 4 AOT and native baseline.** Moved the native smoke lane to GraalVM Java 25, added a real loopback client covering inherited generic decoding, Problem Detail mapping, auth, Micrometer, diagnostics, and health, and preserved AOT client discovery from generated factory metadata.
- **Boot 4 public API migration evidence.** Added a report-only cross-major compatibility profile, froze the published `2.14.0` public surface, categorized the required Boot 4 health API break, and expanded the `3.x` migration guide with complete Maven, YAML, Jackson, Actuator, native, and test-helper instructions.
- **Boot 4 assembled consumer and helper fixtures.** Added an independent Boot 4 WebFlux consumer with real inherited and `@ApiRef` loopback calls, optional diagnostics/health/OTel activation, strict retry startup, and a Jackson 3 mock-helper fixture covering signing bytes, retries, idempotency, lifecycle ordering, final metadata, and repeated headers.
- **Boot 4 benchmark baseline.** Re-established the same-stack smoke harness for raw WebClient, Spring HTTP Interface, and the starter; recorded the complete Boot 4 dependency context, kept no-network diagnostics separate, and explicitly deferred report promotion because this release section makes no public numerical claim.
- **Mock custom exchange loggers.** Added `MockReactiveHttpClient.Builder.withExchangeLogger(...)` so constructor-injected loggers selected by interface- or method-level `@LogHttpExchange` can be registered in the helper's isolated context; distinct logger classes are supported and duplicate concrete-class registrations are rejected.
- **V19 release decision.** Recorded a no-go for publishing `3.0.0`: Boot 4 JVM, consumer, AOT/native, optional-integration, and API migration gates pass, but the reactor still publishes the Boot 3.5 `2.14.1` lane and Boot 4 Javadoc packaging is not release-ready. The `2.x` maintenance lane remains supported.

## [2.14.0] - 2026-07-10

### Changed

- **Post-2.13 baseline transition.** The reactor now targets `2.14.0` while API compatibility and published-baseline benchmark evidence compare against the published `2.13.0` artifacts.
- **Release-note evidence guard.** Documentation tests now validate current changelog release-note evidence links while allowing no-claim releases to keep benchmark work manual and pending.
- **Support-bundle capture recipes.** Added placeholder-based local JVM, container, and Kubernetes-style capture commands for sanitized support bundles.
- **Strict validation adoption diagnostics.** Strict retry and built-in SigV4 startup failures now identify inherited or `@ApiRef` endpoint ownership, the unprovable contract, and a concrete remediation path without changing opt-in behavior.
- **Public API compatibility evidence.** Re-audited documented helper coverage and expanded compatibility fixtures to reject nested fluent-method and public enum-constant removals alongside constructor removals.
- **Metadata and native readiness audit.** Re-verified generated configuration metadata, nested configuration runtime hints, and optional Actuator/OTel boundaries, including explicit OTel back-off coverage when its API or bean is absent.
- **Benchmark classification guard.** Generated Markdown now rejects unclassified method prefixes and unknown comparison surfaces, keeping no-network audits separate from loopback feature rows.
- **Dependency baseline review.** Recorded the separately evaluated Spring Boot `3.5.16` patch candidate and managed library versions while retaining Java 21, Spring Boot `3.5.0`, versionless module dependencies, and optional Resilience4j behavior for this release line.
- **V18 release readiness.** Selected `2.14.0` as the minor release candidate and completed full reactor, generated documentation, link, API compatibility, and compatibility-fixture validation.

## [2.13.0] - 2026-07-05

### Changed

- **Post-2.12 baseline transition.** The reactor now targets `2.13.0` while API compatibility and published-baseline benchmark evidence compare against the published `2.12.0` artifacts.

## [2.12.0] - 2026-07-02

### Added

- **Opt-in diagnostics endpoint and support bundles.** Added an opt-in Actuator diagnostics endpoint plus production support-bundle guidance for collecting sanitized diagnostics snapshots, health details, startup summaries, exchange logs, and benchmark evidence.
- **Strict production guardrails.** Added opt-in startup validation for unsafe retry contracts and built-in body-signing contracts so teams can fail fast on risky retry/idempotency or SigV4 body shapes while preserving warning-only defaults.
- **Effective configuration and generic-contract diagnostics.** Added metadata-backed effective-configuration examples and improved inherited generic endpoint diagnostics for shared interfaces such as `ApiOperators<T>`.

### Changed

- **Post-release baseline transition.** The reactor now targets `2.12.0` while API compatibility and published-baseline benchmark evidence compare against the published `2.11.0` artifacts.
- **Release readiness evidence.** V16 keeps generated release evidence aligned with the `2.12.0` candidate and `2.11.0` API/benchmark baseline, including manual benchmark, compatibility, baseline-resolution, generated-doc, Markdown-link, and promoted-report checks.
- **Performance evidence.** Current performance documentation and release notes now require the source-controlled [Benchmark Report 2.12.0](docs/benchmark-report-2.12.0.md) as release-quality evidence for starter `2.12.0` benchmark scenarios.

### Fixed

- **Benchmark and public API readiness audits.** Re-audited JSON/`ResponseEntity` benchmark rows without adding a speculative optimization, separated no-network benchmark diagnostics from loopback feature claims, and expanded public API compatibility coverage for documented header-redaction, method-metadata cache/model, resilience hook, diagnostics, and contract snapshot helpers.


## [2.11.0] - 2026-06-30

### Added

- **Diagnostics snapshot export.** Added a public sanitized diagnostics snapshot helper for support artifacts, with project version metadata, native resource hints, and compatibility coverage for the documented API.
- **Runtime diagnostics and health details.** Health troubleshooting output and opt-in startup configuration summaries now explain effective client policy without exposing secrets, request bodies, response bodies, or raw header values.
- **Auth and test-helper diagnostics.** OAuth2 client-credentials failures now surface sanitized token-endpoint status/body context, and mock clients can assert Authorization headers and compact 401 invalidation flows without leaking credentials.
- **Benchmark audit rows.** Added observer and lifecycle-hook overhead rows to the benchmark harness so optional diagnostics cost can be audited with one or multiple observers/hooks.
- **Release readiness summary.** Release evidence now includes a one-place readiness view for promoted benchmark reports, stale performance links, generated docs, baseline artifact resolution, compatibility commands, and manual benchmark commands.

### Changed

- **Inherited generic endpoints.** Inherited generic endpoint methods now resolve response and request-body types through each concrete child client, so shared interfaces like `ApiOperators<T>` decode `Mono<T>`, `Flux<T>`, and `ResponseEntity<T>` using the child binding.
- **Release baseline.** The reactor now targets `2.11.0` and compares public API compatibility plus benchmark baseline evidence against published `2.10.0` artifacts.
- **Performance evidence.** Current performance documentation and release notes now require the source-controlled [Benchmark Report 2.11.0](docs/benchmark-report-2.11.0.md) as release-quality evidence for starter `2.11.0` benchmark scenarios.

### Fixed

- **AWS SigV4 signing contracts.** Raw-body signing coverage now documents and tests supported body shapes, avoids silently signing empty multipart uploads, and keeps unsupported streaming/body cases explicit.
- **Redirect, streaming, and bodiless contracts.** Redirect-following, streaming response ownership, and unexpected bodiless-response handling were re-audited with contract coverage so diagnostics and pooling behavior stay consistent.
- **Configuration metadata drift.** Metadata and example-validation checks now keep starter and OTel configuration docs aligned with generated metadata and reject malformed scalar/group examples.

## [2.10.0] - 2026-06-11

### Added

- **Benchmark-backed release evidence.** The release evidence manifest now lists
  benchmark smoke and release commands, generated benchmark report paths, and
  the request-path changes that require refreshed benchmark numbers before
  publishing performance claims.
- **Benchmark baseline drift evidence.** Benchmark reports now record the starter
  version under test, resolved baseline library versions, and dependency
  management source; release evidence also lists published baseline artifacts
  that must resolve before benchmark or API compatibility evidence is accepted.
- **Benchmark claim guardrails.** Public benchmark docs now require methodology,
  scenario names, and release-quality report links before publishing performance
  claims, with tests preventing generated target or smoke-only reports from being
  linked as evidence.
- **Promoted benchmark report.** Added
  [Benchmark Report 2.10.0](docs/benchmark-report-2.10.0.md) as source-controlled
  release-quality evidence for starter `2.10.0` benchmark scenarios.
- **Performance summary.** Added
  [Performance Summary](docs/23-performance-summary.md) to explain benchmark
  methodology, named scenario findings, expected starter overhead sources, and
  starter-only optional feature/error-mapping rows.
- **Release-note benchmark evidence workflow.** Added benchmark evidence
  guidance for release notes that publish performance claims, including promoted
  report links, current-candidate and published-baseline commands, report paths,
  and cited scenario names.
- **Current-vs-baseline benchmark pairing.** Release evidence and benchmark docs
  now name current-candidate and published-baseline report paths together, require
  baseline artifact resolution before promotion, and prevent promoted reports from
  mixing candidate numbers with baseline labels.
- **Benchmark review triggers.** Benchmark docs and release evidence now define
  latency, allocation, and optional-feature review triggers while keeping
  benchmark thresholds manual and outside normal CI hard gates.

### Changed

- **ResponseEntity envelope decode path.** Non-streaming `Mono<ResponseEntity<T>>`
  responses now delegate to Spring WebClient `ClientResponse.toEntity(...)`/
  `toBodilessEntity()` so status, headers, and body envelope construction stay
  on the native response path while streaming `ResponseEntity<Flux<DataBuffer>>`
  ownership remains unchanged.
- **Optional feature overhead paths.** Micrometer observation now builds the
  low-cardinality tag set once per event, and Resilience4j-backed retry,
  circuit-breaker, bulkhead, and rate-limiter paths reuse per-instance Reactor
  operators instead of recreating wrappers on every call.
- **JSON response decode path.** Simple concrete DTO responses now use
  WebClient class-based `bodyToMono`/`bodyToFlux` decoding instead of creating a
  `ParameterizedTypeReference`; generic response types keep the existing
  type-reference decode path.
- **Default success-path request construction.** Plain unauthenticated success-path
  calls now use a stateless request-construction branch when auth, resilience,
  generated idempotency keys, lifecycle hooks, exchange logging, observers, and
  timeout request customization are inactive. Feature-enabled calls keep the
  existing stateful reporting path.

### Fixed

- **Release-note benchmark reruns.** The selective rerun guidance now uses the
  Maven benchmark release profile so metadata is preserved, and its include
  pattern covers raw `WebClient`, Spring HTTP Interface, and starter rows for
  cited comparison scenarios, plus the starter-only Problem Detail row.

## [2.9.0] - 2026-06-08

### Added

- **Configuration metadata drift checks.** Metadata tests now verify documented
  `reactive.http.*` properties against generated metadata, require descriptions
  and deprecation replacements, and cover OTel conditional properties.
- **Generated documentation link validation.** Normal tests now validate local Markdown links across README, changelog, docs, and roadmaps, including common heading anchors and roadmap subdirectory links.
- **API compatibility baseline guard.** The `api-compatibility` profile now
  fails during `validate` when the configured baseline equals the current
  reactor version, preventing japicmp self-comparisons against local builds.
- **Inherited client policy diagnostics.** DEBUG startup diagnostics now report inherited endpoint policy per concrete client, including the parent declaring interface, concrete child client, base URL source, and effective request-timeout source.
- **Runtime diagnostics provider.** Added an injectable `ReactiveHttpClientDiagnosticsProvider` that reports sanitized registered-client summaries without adding an Actuator endpoint.
- **Release evidence manifest.** `mvn test` now generates `target/release-evidence/reactive-http-client-release-evidence.json` with version, baseline, Java, Spring Boot, and release-check command status.

### Changed

- **Inherited endpoint validation messages.** Startup validation failures for inherited endpoint methods now include both the parent declaring interface and the concrete `@ReactiveHttpClient` child context while preserving the existing exception categories.
- **API compatibility baseline.** Public API compatibility now compares against published `2.8.0` artifacts and keeps the baseline guard to prevent self-comparisons against current reactor builds.

### Docs

- Documented shared parent endpoint contracts, per-child timeout behavior, inherited `@ApiRef` parity, mock-helper coverage for inherited clients, and multi-client configuration examples.
- Updated roadmap links and native/release compatibility evidence after the roadmap directory cleanup.

## [2.8.0] - 2026-06-04

### Added

- **Declarative `@HEAD` and `@OPTIONS`.** Added static verb annotations with the same path-template, timeout, resilience, lifecycle, observer, exchange-log, AOT, and mock-client support as the existing HTTP verb annotations.
- **Opt-in redirect following.** Added per-client `reactive.http.clients.<name>.follow-redirects`, defaulting to `false`, to let the starter-created Reactor Netty transport follow `301`, `302`, `303`, `307`, and `308` redirects when explicitly enabled.
- **Multi-value outbound headers.** `@HeaderParam` now accepts collection and array values for named parameters and map entries, preserves caller order, validates every expanded value, and replaces same-name configured defaults case-insensitively.
- **Header-aware mock responses.** Added `MockReactiveHttpClient` raw text/byte response helpers and custom repeated response-header helpers while keeping `json(...)` and `empty(...)` source compatible.

### Changed

- **Eager declarative validation.** Client proxy construction now validates every abstract endpoint method, including inherited endpoints, missing verb or `@ApiRef` metadata, conflicting verb annotations, duplicate body parameters, blank parameter annotation names, malformed base URLs, configured `@ApiRef` methods, and URI-template placeholder mismatches.
- **Bodiless response draining.** Successful `Mono<Void>` and `Mono<ResponseEntity<Void>>` responses now drain unexpected response content before completing so pooled connections can remain reusable when the transport permits it.
- **Streaming response ownership.** `Flux<DataBuffer>` and `Mono<ResponseEntity<Flux<DataBuffer>>>` contracts now explicitly separate starter-owned discard release from consumer-owned emitted buffers, and streaming envelope diagnostics complete when the response envelope is emitted.
- **Compatibility.** Public API compatibility is checked against published `2.7.0` artifacts; no intentional breaking behavior changes are included in this release.

### Fixed

- Fixed URI-template validation so placeholders in literal query strings are included when matching declared `@PathVar` parameters.
- Fixed the annotation reference by keeping unsupported `TRACE` out of the documented `@ApiRef` method set.
- Fixed `Mono<ResponseEntity<Flux<DataBuffer>>>` handling with real `WebClient` so the inner streaming body remains consumable after the outer response envelope completes.
- Fixed startup metadata parsing so invalid declarations fail deterministically before entering the method metadata cache.

### Docs

- Documented `@HEAD`, `@OPTIONS`, multi-value `@HeaderParam`, and `@ApiRef` validation contracts.
- Documented visible-3xx default behavior, opt-in redirect forwarding, sensitive-header behavior on cross-authority redirects, loop handling, and request-body replay risks.
- Documented bodiless-response draining, error-body retention boundaries, streaming buffer ownership, and response-envelope completion semantics.
- Documented repeated-header assertions and raw mock response helpers for tests.

## [2.7.0] - 2026-06-02

### Added

- **Exchange-log subscription-attempt count.** Added
  `HttpExchangeLogContext#subscriptionAttemptCount()` and included it in default
  exchange-log output. The value counts retry subscriptions inside one logical
  call, not guaranteed HTTP network sends. Existing convenience constructors
  default the value to `1`.
- **Observer and lifecycle test helpers.** Added `MockReactiveHttpClient` builder
  support for a custom `HttpClientObserver` and ordered
  `ReactiveHttpClientLifecycleHook` instances.
- **`@ApiRef` observability-name fallback.** Methods now resolve logical API names
  with `@ApiName` value > `@ApiRef` value > Java method name precedence for
  observer events, metrics, traces, and lifecycle hooks.
- **Error-body truncation metadata.** Added
  `ErrorResponseContext#responseBodyTruncated()` and
  `retainedResponseBodyBytes()` so structured mappers can distinguish complete
  bounded input from truncated input. The existing constructor remains available.

### Changed

- **Compatibility automation.** Added a japicmp release gate against published
  `2.6.0` artifacts, a fixture check for additive versus breaking API changes,
  and a manually triggered weekly minimal native-image smoke workflow.
- **Compatibility.** This release has no intentional breaking behavior changes.

### Fixed

- Fixed `MockReactiveHttpClient` observer and terminal lifecycle metadata by
  installing the same final-request observation filter used by production
  clients.
- Fixed `MockReactiveHttpClient` hook, observer, and lifecycle client names by
  using `@ReactiveHttpClient.name()` when present and retaining `mock-client` as
  the unannotated-interface fallback.
- Fixed `MockReactiveHttpClient` lifecycle-hook ordering so `@Order` annotations
  are honored alongside `Ordered` implementations.
- Fixed bounded error-body capture to retain only cap-sized data while draining
  oversized responses, releasing consumed buffers, and installing discard release
  handling for cancellation paths.
- Restricted `DefaultErrorDecoder` and registered `ErrorResponseMapper` invocation
  to actual 4xx/5xx statuses so visible redirects remain normal response values.
- Fixed starter bootstrap when optional Micrometer dependencies are absent by
  isolating the built-in observer behind a classpath-conditional configuration.

### Docs

- Documented subscription-attempt count semantics and a custom structured
  exchange-logger example that records the count.
- Documented compact `MockReactiveHttpClient` observer and lifecycle-hook usage
  for retry subscription assertions.
- Documented bounded mapper input metadata and error-body draining behavior for
  connection reuse.
- Documented a canonical diagnostic-context capability matrix and
  raw-versus-redacted header behavior for custom exchange loggers and observers.
- Documented visible 3xx handling and transport-level redirect following.
- Documented the Spring Boot `3.5.0` baseline, public API compatibility gate, and
  core-versus-optional native-image support ownership.

---

## [2.6.0] - 2026-05-31

### Added

- **Retry-safety diagnostics.** Added warning-only diagnostics for retry-enabled
  unsafe HTTP methods without an explicit `Idempotency-Key`, plus DEBUG startup
  diagnostics for per-method resilience operator decisions.
- **Request body repeatability diagnostics.** Added retry diagnostics for
  non-repeatable and application-owned request bodies without buffering large or
  streaming uploads.
- **Idempotency key support.** Added `@IdempotencyKey` and
  `RequestContext.withIdempotencyKey(...)` for opt-in outbound idempotency keys
  with caller/default/context/generated precedence.
- **Retry and idempotency test helpers.** Added `MockReactiveHttpClient`
  retry simulation plus fluent idempotency-key and attempt-count assertions.
- **Final outbound request diagnostics.** Added final request URL/header snapshots
  for exchange logging and observer events after `WebClient` filters mutate the
  outbound request.
- **Error body and timeout correctness coverage.** Added regression coverage for
  retained error-body caps, malformed `Content-Type`, Problem Detail mapping,
  timeout precedence, body-decode timeouts, and streaming timeout behavior.

### Changed

- **Retry signal contract.** Lifecycle hooks now have an explicit
  per-subscription-attempt versus logical-call contract. Observer records remain
  logical-call scoped with the final subscription-attempt count, while
  exchange-log records remain logical-call scoped without that count. Existing
  runtime behavior is preserved.
- **Compatibility.** This release has no breaking behavior changes.

### Fixed

- Fixed publisher request bodies so auth raw-body serialization does not convert
  `Publisher` bodies to JSON bytes, and publisher DTO bodies keep the default
  JSON content type when no explicit `Content-Type` is set.
- Fixed AWS SigV4 signing for unsupported publisher request bodies by failing
  before sending a request instead of signing an empty payload that does not
  match the streamed body bytes.
- Fixed generated `@IdempotencyKey` headers so each cold publisher subscription
  gets a fresh key, retries keep the same key, and lifecycle/logging callbacks
  read subscription-local prepared outbound headers.
- Fixed request serialization failures so lifecycle/observer attempt state starts
  before auth body serialization can fail.
- Fixed generated `@IdempotencyKey` concurrent subscriptions so lifecycle hooks
  and observer events report subscription-local attempt counts instead of
  invocation-shared counts.

### Docs

- Documented retry-safety classifications, unsafe retry warnings, and the
  canonical Resilience4j operator order.
- Documented repeatable, non-repeatable, and application-owned request body
  shapes for retry-enabled clients.
- Documented idempotency key precedence and clarified that the starter does not
  provide downstream idempotency storage.
- Documented compact retry/idempotency test-helper usage for transient
  downstream failures.
- Documented declarative versus final outbound headers for exchange logging and
  customizer debugging.
- Documented retained error body caps and timeout metadata behavior for header,
  body decode, and streaming timeout paths.
- Documented retry lifecycle per-attempt callbacks and logical-call terminal
  observer, exchange-log, success, error, and cancellation semantics.

---

## [2.5.0] - 2026-05-22

### Added

- **Async request context handoff.** Added `RequestContextSnapshot` to capture
  and restore starter-owned Reactor context values across explicit async
  boundaries such as `Sinks.Many` event envelopes.
- **Typed request context helpers.** Added `RequestContext` accessors for
  starter-owned Reactor context values so new integrations do not need raw
  string keys.
- **Context-aware test helpers.** `MockReactiveHttpClient` now records the
  starter request-context snapshot visible to the mock exchange function, and
  `RecordedExchangeAssertions` can assert captured correlation IDs and inbound
  headers.
- **Request context contributor SPI.** Added ordered `RequestContextContributor`
  hooks plus built-in correlation ID and inbound header contributors for custom
  in-process integrations.

### Fixed

- **Inbound header snapshots.** Stored inbound header snapshots are now
  immutable defensive copies, preserving original header casing while keeping
  allow-list and deny-list matching case-insensitive.

### Docs

- Documented inbound header snapshot immutability, casing, and redaction
  behavior for logs and future async context handoff.
- Documented that Reactor context is not automatically carried through sinks,
  queues, or callbacks, and showed explicit snapshot capture/restore.
- Documented public context keys and precedence for caller-supplied headers,
  Reactor context or restored snapshots, and MDC fallback.
- Documented test-helper assertions for captured async request context with a
  `Sinks.Many` snapshot handoff example.
- Added async observability guidance for event envelopes, queue handoff, and
  production use of request-context snapshots.

---

## [2.4.0] - 2026-05-21

### Added

- **HTTP contract ergonomics.** Added non-streaming `Mono<ResponseEntity<T>>`
  and `Mono<ResponseEntity<Void>>` response envelope support, plus an opt-in
  `ProblemDetailErrorResponseMapper` for `application/problem+json` errors.
- **AOT and release compatibility.** Added runtime hints for starter
  annotations, configuration properties, and scanned client interface proxies,
  plus AOT and release-smoke coverage for a minimal metrics-enabled client.

### Changed

- **URI encoding contract hardened.** Request URI construction now preserves
  literal query strings in annotation and `@ApiRef` paths while appending
  configured and method-level query parameters consistently.
- **Numeric configuration validation.** Timeout, pool, codec, histogram, and
  health-threshold settings now fail fast with property-specific range errors
  instead of reaching Netty or metrics code with invalid values.
- **Reactive body safety guardrails.** Cancellation before response and during
  body streaming now has lifecycle and observer terminal-signal coverage, while
  mapper fallback and streaming response tests assert bodies are not consumed or
  buffered accidentally.
- **Configuration clarity.** Added canonical client-level
  `request-timeout-ms`, kept `resilience.timeout-ms` as a deprecated alias, and
  documented override contracts for named built-in beans and disabled
  auto-configuration paths.

### Docs

- Documented the raw-value URI encoding contract for `@PathVar`,
  `@QueryParam`, literal path query strings, and `@ApiRef` paths.
- Documented native-image support boundaries and the release smoke matrix.

---

## [2.3.0] - 2026-05-18

### Added

- **Lifecycle hook SPI.** Added ordered `ReactiveHttpClientLifecycleHook`
  callbacks for request start, retry attempts, success, error, and cancellation,
  with failures isolated from the client call and from other hooks.
- **Structured error response mapping.** Added ordered `ErrorResponseMapper`
  support so applications can map per-client structured error bodies to
  domain-specific exceptions while retaining default decoder fallback.
- **Test-helper assertions.** Added fluent `RecordedExchange` assertions for
  method, path, query parameters, headers, body, status, repeated query values,
  and redacted headers.
- **Documentation release checks.** Added local doc-link validation, version
  snippet checks, generated configuration property reference, and CI-style tests
  to catch documentation drift.

### Changed

- **Request plan model.** Invocation internals now use an immutable request plan
  built from method metadata so stable annotation-derived request decisions are
  kept out of the per-call path while dynamic argument resolution remains
  explicit.
- **Conflict and precedence coverage.** Documented and tested precedence for
  annotations, `@ApiRef`, defaults, auth, customizers, resilience, logging, and
  observability configuration.
- **Observability guardrails.** High-cardinality Micrometer tags and OTel
  attributes remain opt-in, with metadata and documentation guidance for risky
  settings.

### Fixed

- **Ambiguous configuration behavior.** Remaining annotation/configuration
  conflict cases now either fail fast or have documented precedence.
- **Mapper fallback safety.** Invalid structured error bodies and mapper misses
  fall back to default `HttpClientException` / `RemoteServiceException` decoding
  while preserving status, response body, and `ErrorCategory`.

### Docs

- Added conflict/cardinality guardrails, lifecycle hook, test-helper, and
  configuration-property reference docs.
- Updated quick-start and observability docs for conservative observability
  defaults and extension-point guidance.

---

## [2.2.0] - 2026-05-17

### Added

- **Error-category contract coverage.** Added published mappings and tests for
  HTTP status, decode, timeout, cancellation, DNS, connect, TLS, auth-provider,
  and Resilience4j rejection failures.
- **TLS and resilience categories.** Added `TLS_ERROR` and `RESILIENCE_ERROR`
  to the published `ErrorCategory` model.
- **Exchange logging presets.** Added
  `reactive.http.clients.*.log-preset` with `metadata-only`, `headers`, and
  `bodies` modes for the default exchange logger.
- **Configuration metadata guardrail.** Added tests that fail when high-value
  documented `reactive.http.*` properties or defaults disappear from Spring
  configuration metadata.

### Changed

- **Observable error names are consistent.** Micrometer and OpenTelemetry now
  verify they emit the same published `ErrorCategory` names.
- **Client-name validation.** Client names now use the documented
  `[A-Za-z0-9][A-Za-z0-9._-]{0,127}` pattern so property keys, diagnostics,
  pool names, metrics, spans, health details, and exception messages stay
  consistent. Applications with previously invalid names must rename them.
- **Invocation-path allocation cleanup.** Static method/path/timeout request
  plans are cached in method metadata instead of rebuilt on every invocation.

### Docs

- Added production checklist and migration-from-`WebClient` reference docs.
- Documented the published error-category mapping table.
- Documented `log-preset` behavior and how custom exchange loggers receive the
  configured preset.

---

## [2.1.0] - 2026-05-17

### Added

- **Startup diagnostics for resolved clients.** At DEBUG level, each client now
  logs its resolved base URL source, HTTP protocol, pool source, proxy/TLS
  state, auth mode, resilience operators, observability, and exchange logging
  flags with sensitive values redacted.
- **Per-client default headers.** Added
  `reactive.http.clients.*.default-headers` for static headers applied to every
  request. Method-level `@HeaderParam` values override configured defaults.
- **Per-client default query parameters.** Added
  `reactive.http.clients.*.default-query-params` for static query parameters
  applied to every request. Method-level `@QueryParam` values replace defaults
  with the same name, and list values are sent as repeated query parameters.
- **Configuration metadata for V3 properties.** Added Spring metadata for the
  new default-header and default-query-parameter properties.

### Changed

- **HTTP/2 and TLS confidence coverage.** Added in-process Reactor Netty tests
  proving HTTP/2 over TLS works when opted in and default TLS clients retain the
  HTTP/1.1 path.
- **Auth precedence is explicit.** When both `auth-provider` and object-style
  `auth.type` are configured, the bean-name `auth-provider` wins and startup
  logs a warning that object-style auth is ignored.
- **Default header and query safety checks.** Configured default headers and
  default query parameters now fail fast for invalid names or control-character
  values. Sensitive-looking configured keys warn at startup without logging
  their values.

### Docs

- Documented default header and default query YAML usage, including no-query,
  appended-query, and same-name override examples.
- Documented auth bean-name versus object-style auth precedence.
- Documented the risk of replacing the starter-managed `WebClient` connector in
  a `ReactiveHttpClientCustomizer`.

---

## [2.0.0] - 2026-05-16

### Added

- **JUnit 5 mock HTTP extension.** Added `@MockHttpServer` and
  `MockHttpServerExtension` in `reactive-http-client-test` for fresh
  `MockReactiveHttpClient<T>` field injection before each test method.
- **Per-client HTTP/2 opt-in.** Added
  `reactive.http.clients.*.http2-enabled` so a client can use Reactor Netty
  HTTP/2 without replacing the starter-managed connector in a customizer.

### Removed

- **Deprecated `log-body` client property.** Removed
  `reactive.http.clients.*.log-body` compatibility. Use
  `reactive.http.clients.*.log-exchange` for client-wide exchange logging.

---

## [1.16.0] - 2026-05-14

### Added

- **Resilience4j rate-limiter support.** Added optional `RateLimiterRegistry`
  integration, client-level `resilience.rate-limiter` configuration, and
  method-level `@RateLimiter` overrides with startup validation.
- **Rate-limiter metrics binding.** When `resilience4j-micrometer` and a
  `RateLimiterRegistry` bean are present, the starter now registers
  `reactiveHttpRateLimiterMeterBinder` for tagged Resilience4j rate-limiter
  metrics.
- **Composite HTTP client observation.** Multiple `HttpClientObserver` beans now
  run for each exchange, with failures isolated per observer. The Micrometer and
  OpenTelemetry built-ins are registered as named observers so metrics and spans
  can be emitted together without user-written delegation.
- **Resolved server attributes for observability.** `HttpClientObserverEvent`
  now carries nullable `serverAddress` and `serverPort` fields. OTel spans set
  `server.address` and `server.port` when available, and Micrometer can include
  those tags with `reactive.http.observability.include-server-address=true`
  (default `false` to avoid high-cardinality metric labels).
- **TLS integration coverage.** Added an HTTPS integration test with an
  in-process self-signed server to verify trusted and untrusted TLS paths.
- **Error category extraction helper.** Added `ErrorCategories` so application
  business logic can extract `ErrorCategory` from starter exceptions and common
  wrapped network failures.
- **Examples documentation.** Added `docs/examples/` snippets for OAuth2,
  Resilience4j, OpenTelemetry propagation, multipart upload, streaming, and
  test-helper usage without a live server.

### Changed

- **Resilience operator ordering documented.** Resilience is now documented as
  `retry -> rate-limiter -> circuit-breaker -> bulkhead`.
- **Observer override semantics documented.** User `HttpClientObserver` beans now
  run alongside built-ins. Override built-ins by registering beans named
  `micrometerHttpClientObserver` or `openTelemetryHttpClientObserver`.

---

## [1.15.0] – 2026-05-13

### Added

- **Property-driven auth providers.** Clients can now use an object-style
  `reactive.http.clients.<name>.auth` block for built-in auth providers while
  keeping the legacy `auth-provider` bean-name shortcut unchanged.
- **AWS SigV4 auth provider.** Added `AwsSigV4AuthProvider` and a built-in
  `aws-sigv4` factory for signing requests with AWS Signature Version 4,
  including raw request body hashing when the starter has serialized bytes for
  auth signing.
- **OAuth2 client-credentials auth factory.** Added the built-in
  `oauth2-client-credentials` factory, composing
  `OAuth2ClientCredentialsTokenProvider` with `RefreshingBearerAuthProvider`
  from YAML configuration.
- **Independent OpenTelemetry span and propagation toggles.** The
  `reactive-http-client-otel` module now keeps
  `reactive.http.observability.otel.enabled` as the master switch while adding
  `reactive.http.observability.otel.spans.enabled` and
  `reactive.http.observability.otel.propagation.enabled` for finer control.
  This lets applications disable span recording while keeping propagation, or
  disable propagation while keeping outbound span recording.
- **Filter-order DEBUG diagnostics.** Starter-created `WebClient.Builder`
  instances now DEBUG-log applied Spring `WebClientCustomizer` classes, and
  per-client proxy creation DEBUG-logs applied `ReactiveHttpClientCustomizer`
  classes after built-in filters are wired.

### Changed

- **Documented OTel switch semantics and filter order.** README and docs now
  state exactly what the OTel master switch controls, how the child toggles
  behave, and where global `WebClientCustomizer` filters and per-client
  `ReactiveHttpClientCustomizer` filters sit relative to built-ins.
- **Expanded OTel header-preservation coverage.** Propagation tests now verify
  caller-supplied `traceparent` headers are preserved alongside `baggage`.

---

## [1.14.0] – 2026-05-12

### Added

- **OpenTelemetry Reactor context propagation.** The optional
  `reactive-http-client-otel` module now auto-registers a server-side
  `OpenTelemetryContextWebFilter` that extracts inbound OTel context from
  request headers and stores it in Reactor `Context`.
- **Outbound OTel header propagation for starter-built clients.** Added
  `OpenTelemetryContextExchangeFilter`, wired through a `WebClientCustomizer`,
  to inject the configured OTel propagator headers onto outbound
  `@ReactiveHttpClient` requests. This propagates W3C `traceparent` and
  `baggage` when those propagators are configured on the application
  `OpenTelemetry` bean.
- **OTel propagation regression coverage.** Added tests for inbound baggage
  extraction, outbound injection, caller-supplied header preservation,
  no-context no-op behavior, and the end-to-end WebFilter-to-WebClient flow.

### Changed

- **OTel module docs now cover trace context and baggage pass-through.**
  `docs/08-observability.md` and the README describe how inbound OTel
  context reaches outbound reactive HTTP clients, including the rule that
  caller-supplied propagation headers are not overwritten.

---

## [1.13.1] – 2026-05-12

### Changed

- **`@ApiRef` mismatch checks now fail fast at startup.** Client proxy creation validates
  referenced API-map entries so missing mappings and blank `method` / `path` values fail
  immediately with config-path-specific diagnostics instead of surfacing at first invocation.
- **Unified `@ApiRef` diagnostic path formatting.** Startup and invocation-time validation now
  share the same API config-prefix/context builder, keeping error messages consistent.

### Added

- **Startup validation coverage for blank API-map fields.** Added tests that assert
  `ReactiveHttpClientFactoryBean#getObject()` fails when `apis[...].method` or `apis[...].path`
  is blank for a referenced `@ApiRef`.

---

## [1.13.0] – 2026-05-10

### Added

- **Optional `@ApiRef` API-map routing.** Client methods can now resolve HTTP method, path,
  and timeout by logical API name from `reactive.http.clients.<client>.apis.<api-name>`,
  as an alternative to method-level HTTP verb annotations.
- **Per-client API map configuration model.** Added
  `apis.<api-name>.method`, `apis.<api-name>.path`, and optional
  `apis.<api-name>.timeout-ms` (`-1` unset, `0` disable request timeout).

### Changed

- **Timeout precedence for `@ApiRef` methods.** Effective timeout order is now:
  method-level `@TimeoutMs` → API-map `timeout-ms` → client `resilience.timeout-ms`.
- **Clearer API-map error paths.** `@ApiRef` config errors now report map-key paths in
  bracket notation (for example `reactive.http.clients.<name>.apis[user.getById].path`)
  to avoid ambiguity with dotted API keys.

### Fixed

- **Spring Boot config metadata source type for API-map fields.** API-map metadata now points
  to `ReactiveHttpClientProperties.ApiConfig` for accurate property mapping in IDE metadata.

---

## [1.12.1] – 2026-05-07

### Fixed

- **Client-level `@LogHttpExchange` correctness for inherited interfaces.** Interface-level logger
  resolution now uses the actual reactive client proxy interface at invocation time, so methods
  inherited from base interfaces correctly pick up `@LogHttpExchange` declared on the extending
  client interface.
- **No cross-client logger leakage for shared methods.** Interface-level logger resolution is no
  longer cached on shared `MethodMetadata`, preventing annotation leakage when multiple clients
  share the same inherited base method signature.

---

## [1.12.0] – 2026-05-06

### Added

- **Opt-in latency histogram with SLO buckets.** `MicrometerHttpClientObserver` now
  records a second Timer — `<metricName>.latency` (default:
  `reactive.http.client.requests.latency`) — configured with
  `serviceLevelObjectives(...)` boundaries. This enables P99/SLO-style latency
  analysis without tag-cardinality explosion. The histogram is disabled by default and
  uses only low-cardinality tags (`client.name`, `api.name`, `http.method`, `uri`).
  Enable it with:
  ```yaml
  reactive:
    http:
      observability:
        histogram:
          enabled: true
          slo-boundaries-ms: [50, 100, 200, 500, 1000, 2000, 5000]
  ```
  The `slo-boundaries-ms` list is validated at startup: null and non-positive values
  are silently ignored; if the resulting list is empty the histogram is treated as
  disabled. See [docs/08-observability.md](docs/08-observability.md) for the full
  reference.
- **`HistogramConfig` configuration group.** Two new `reactive.http.observability.histogram.*`
  properties with Spring Boot IDE auto-completion metadata:
  - `histogram.enabled` (default `false`) — opt-in toggle.
  - `histogram.slo-boundaries-ms` (default `[50, 100, 200, 500, 1000, 2000, 5000]`) —
    SLO bucket boundaries in milliseconds.
- **Histogram Timer caching.** Timer instances are cached per low-cardinality tag
  combination in a `ConcurrentHashMap`, avoiding repeated `Timer.Builder` allocation
  on the hot request path.

---

## [1.11.1] – 2026-05-06

### Fixed

- **Default `metric-name` changed to `reactive.http.client.requests`.** The previous
  default `http.client.requests` collides with the Spring Boot built-in HTTP client
  timer of the same name, causing metric double-counting and tag conflicts in
  `MicrometerHttpClientObserver`. The new default `reactive.http.client.requests`
  avoids this collision. Users who rely on the old default can restore it explicitly:
  ```yaml
  reactive:
    http:
      observability:
        metric-name: http.client.requests
  ```

---

## [1.11.0] – 2026-05-03

### Added

- **`ReactiveHttpClientCustomizer` SPI.** New `@FunctionalInterface` in the `core`
  package that lets applications attach custom `ExchangeFilterFunction`s (or any
  other `WebClient.Builder` customization) to one or more reactive HTTP clients
  without recreating a raw `WebClient` and losing starter-managed filters.
  `ReactiveHttpClientFactoryBean` discovers all `ReactiveHttpClientCustomizer` beans
  via `ObjectProvider.orderedStream()` (honoring `@Order` / `Ordered`), filters them
  by `supports(clientName)`, and applies matching ones after built-in filters
  (correlation-ID, auth, exchange logging) and before `WebClient.build()`.
  The default `supports()` implementation returns `true`, so a customizer declared
  without overriding the method applies to every client. See
  [docs/15-customizer.md](docs/15-customizer.md) for the full reference.

---

## [1.10.1] – 2026-05-02

### Added

- **Spring Boot configuration metadata.** Added
  `META-INF/additional-spring-configuration-metadata.json` covering all
  `ReactiveHttpClientProperties` fields so IDEs provide auto-completion and
  documentation for every `reactive.http.*` property. (#36)
- **Method-scoped logger caching.** `MethodMetadata` now resolves and caches the
  per-method `HttpExchangeLogger` on first use via a `volatile` field with a
  `NOOP_EXCHANGE_LOGGER` sentinel, avoiding repeated registry lookups on the hot
  path. (#36)
- **`MethodMetadataCache.testOnlyBlankPathWarnedCount()`.** Test-only helper that
  exposes how many times the blank-path-template warning has been emitted, allowing
  the `blankPathTemplateWarningIsFiredOnlyOnce` test to assert the exact count rather
  than relying on log output. (#37)

### Changed

- **Unified Mono / Flux invocation pipeline.** Refactored `ReactiveClientInvocationHandler`
  to share a single `exchange(...)` method for both `Mono` and `Flux` return types,
  eliminating duplicated pipeline assembly and reducing the risk of divergence between
  the two paths. (#35)
- **`@DeprecatedConfigurationProperty` on `log-body`.** The `logBody` getter in
  `ClientConfig` is now annotated with `@DeprecatedConfigurationProperty` (with a
  `replacement` and `since` value) so Spring Boot's configuration processor surfaces
  the deprecation in IDE hints. (#36)
- **Header lookup optimisation.** `ResolvedArgs` now builds a case-insensitive
  `TreeMap` view of its headers once on construction; all downstream header lookups
  use this cached view instead of iterating the raw map. (#36)
- **Logger guard helpers.** `DefaultHttpExchangeLogger` extracts `logSuccess()` and
  `logError()` private methods with per-level `isEnabled` guards to avoid unnecessary
  string formatting on the hot path; `responseBody` is now included in the WARN log
  path. (#36)
- **Bounded root-cause traversal.** `getRootCause` replaces the previous
  `IdentityHashMap`-based cycle detection with a simple bounded loop (max depth 16),
  removing the allocation overhead on every exception-handling call. (#36)
- **`buildFallbackException` reactive safety.** `releaseBody()` is now composed
  inside the reactive chain via `.thenReturn()` instead of a `subscribe()` side
  effect, ensuring the release is always sequenced and never silently dropped. (#37)
- **`ReactiveHttpClientFactoryBean` destroy logging.** `destroy()` now passes the
  full exception object to `log.warn(...)` so the stack trace is visible to operators
  when connection-provider shutdown fails. (#37)
- **Explicit `this.connectionProvider` reference** in `ReactiveHttpClientFactoryBean`
  to make the intent clear and avoid potential confusion with a local variable of the
  same name. (#37)

### Fixed

- **`ReactiveHttpClientsRegistrar` false-positive duplicate-name error.** Candidates
  are now de-duplicated by interface class name before the duplicate-name check,
  preventing spurious `IllegalStateException` when base-package lists overlap and the
  same interface is scanned more than once. (#37)

---

## [1.10.0] – 2026-05-01

### Added

- **`reactive-http-client-otel` artifact.** New companion module providing
  `OpenTelemetryHttpClientObserver`, an `HttpClientObserver` that records each
  outbound HTTP exchange as an OTel `CLIENT` span using the standard semantic
  conventions: `http.request.method`, `http.response.status_code`,
  `url.template`, `error.type` (mapped from `ErrorCategory`, falling back to the
  exception's simple class name), plus starter-specific
  `rhttp.client.name` / `rhttp.api.name` / `rhttp.attempt.count` /
  `rhttp.request.bytes` / `rhttp.response.bytes` attributes. Span name follows
  the OTel low-cardinality recommendation (`<METHOD> <api.name>`). Activated
  under `reactive.http.observability.otel.enabled` (default `true` when the
  OTel API is on the classpath and an `OpenTelemetry` bean is available).
  Auto-configured via `META-INF/spring/...AutoConfiguration.imports`; gated on
  `@ConditionalOnMissingBean(HttpClientObserver.class)` so it shuts off the
  Micrometer observer when both modules are on the classpath. (Roadmap 1.1)
- **Per-method resilience overrides.** New `@Retry`, `@CircuitBreaker`,
  `@Bulkhead` annotations select a specific Resilience4j instance by name on
  one method, taking precedence over the client-level
  `reactive.http.clients.<name>.resilience.*` setting. The factory bean
  validates referenced names at proxy-construction time via the new
  `ResilienceOperatorApplier.isInstanceConfigured(...)` hook and fails fast
  with a descriptive `IllegalStateException` when an instance is missing.
  (Roadmap 1.9)
- **HTTP proxy and TLS / mTLS configuration.** Two new sub-configs:
  `reactive.http.network.proxy.*` (HTTP / HTTPS / SOCKS4 / SOCKS5, optional
  username/password, `nonProxyHosts` regex) and
  `reactive.http.network.tls.*` (truststore + keystore via Spring's
  `DefaultResourceLoader`, configurable protocols / ciphers, plus an
  `insecure-trust-all` flag for development that emits a startup WARN).
  Both also accept per-client overrides under
  `reactive.http.clients.<name>.proxy.*` / `.tls.*` — the override replaces
  the global block wholesale (no field-level merging). (Roadmap 1.5)
- **Streaming response passthrough.** Methods declaring
  `Flux<DataBuffer>` or `Mono<ResponseEntity<Flux<DataBuffer>>>` skip the
  in-memory codec entirely, so payloads larger than
  `codec-max-in-memory-size-mb` are streamed without a
  `DataBufferLimitException`. The `ResponseEntity` variant exposes the
  upstream status and headers alongside the streaming body for proxy /
  pass-through use cases. (Roadmap 1.8)
- **Configurable correlation-id MDC fallback keys.**
  `reactive.http.correlation-id.mdc-keys` replaces the previously hard-coded
  list (`correlationId`, `X-Correlation-Id`, `traceId`) with a configurable
  one — useful for Zipkin's `X-B3-TraceId`, Jaeger's `uber-trace-id`, or any
  custom tracing key. An empty list disables the MDC fallback entirely.
  Defaults preserve the prior list. (Roadmap 1.10)

---

## [1.9.0] – 2026-04-23

### Added

- `reactive.http.correlation-id.max-length` (default `128`). Inbound `X-Correlation-Id`
  values longer than the limit, or containing CR / LF / other ISO control characters,
  are now dropped with a DEBUG log and never stored in the Reactor context or
  propagated outbound. Prevents log-forgery and context-bloat via malicious upstream
  callers. (Roadmap 3.1)
- `reactive.http.inbound-headers.allow-list` and `reactive.http.inbound-headers.deny-list`.
  `InboundHeadersWebFilter` now filters the inbound-header snapshot before storing it
  in the Reactor context: if the allow-list is non-empty only those headers are
  captured, and any captured header whose name matches the deny-list has its value
  replaced with `[REDACTED]`. Deny-list defaults to the shared
  `SensitiveHeaders.DEFAULTS` list (`Authorization`, `Cookie`, `Set-Cookie`,
  `Proxy-Authorization`, `X-Api-Key`). (Roadmap 3.7)
- `SensitiveHeaders` utility consolidating the credential / session-cookie deny-list
  used by `DefaultHttpExchangeLogger` and `InboundHeadersWebFilter`.
- **Per-client connection-pool overrides.** `reactive.http.clients.<name>.pool.*`
  now accepts every field of the global `reactive.http.network.connection-pool`
  block. When set the client-level block replaces the global one wholesale (no
  field-level merging). Leaving it unset inherits the global pool, preserving
  prior behaviour. (Roadmap 1.4)
- Connection-pool idle / lifetime eviction knobs on both the global and
  per-client `connection-pool` blocks: `max-idle-time-ms`, `max-life-time-ms`,
  `evict-in-background-ms`. All default to `0` (disabled), preserving prior
  Reactor Netty behaviour. Set behind load balancers that silently drop
  long-idle sockets to avoid handing out half-dead pooled connections. (Roadmap 1.4)
- `reactive.http.network.connection-pool.metrics-enabled` (default `false`).
  When flipped on and a `MeterRegistry` bean is present, the `ConnectionProvider`
  publishes Reactor Netty's built-in pool gauges
  (`reactor.netty.connection.provider.total.connections`,
  `.active.connections`, `.idle.connections`, `.pending.connections`) tagged by
  the pool name. (Roadmap 1.6 pool gauges / 2.1a)
- Resilience4j Micrometer auto-binding. When
  `io.github.resilience4j:resilience4j-micrometer` is on the classpath **and**
  a `CircuitBreakerRegistry` / `RetryRegistry` / `BulkheadRegistry` bean is
  present alongside a `MeterRegistry`, the starter registers
  `TaggedCircuitBreakerMetrics` / `TaggedRetryMetrics` /
  `TaggedBulkheadMetrics` as `MeterBinder` beans (names
  `reactiveHttpCircuitBreakerMeterBinder` / `reactiveHttpRetryMeterBinder` /
  `reactiveHttpBulkheadMeterBinder`). Each binding is skipped independently
  when its dedicated registry is absent; users can override a specific
  binding by declaring a `MeterBinder` bean with the matching name. (Roadmap 2.1b)
- Request / response body-size metrics. `HttpClientObserverEvent` now carries
  `requestBytes` and `responseBytes` (both `long`, `-1` / `UNKNOWN_SIZE` when
  not measurable), and `MicrometerHttpClientObserver` emits
  `http.client.requests.request.size` and
  `http.client.requests.response.size` `DistributionSummary` meters tagged
  with `client.name`, `api.name`, `http.method`, `uri`. Request size is
  measured for `byte[]` / `String` / `CharSequence` / `null` bodies; arbitrary
  objects are left unmeasured to avoid double-serialisation. Response size is
  read from `Content-Length`; chunked / headerless responses are skipped.
  (Roadmap 2.2)
- `HttpClientHealthIndicator`. When `spring-boot-actuator` is on the classpath
  and a `MeterRegistry` bean is present, the starter auto-registers a health
  indicator that reads the existing `http.client.requests` timers and reports
  per-client error rates computed from probe-to-probe deltas. New properties:
  `reactive.http.observability.health.enabled` (default `true`),
  `.error-rate-threshold` (default `0.5`), `.min-samples` (default `10`). The
  indicator does not implement `HttpClientObserver`, so the existing
  `@ConditionalOnMissingBean(HttpClientObserver.class)` override contract is
  preserved. Added `spring-boot-actuator` as an optional dependency.
  (Roadmap 1.6)
- **Multipart / form-data request encoding.** New annotations:
  `@MultipartBody` (method), `@FormField` (scalar / multi-value text part),
  `@FormFile` (file part — accepts `byte[]`, any
  `org.springframework.core.io.Resource`, or the new `FileAttachment`
  convenience record carrying bytes + filename + content-type). The starter
  builds the `multipart/form-data` body via Spring's `MultipartBodyBuilder`;
  the boundary-bearing `Content-Type` is generated automatically.
  Combining `@MultipartBody` with `@Body`, or using `@FormField` /
  `@FormFile` without `@MultipartBody`, is rejected at metadata-parse time.
  (Roadmap 1.2)
- **Built-in OAuth 2.0 client-credentials token provider.**
  `OAuth2ClientCredentialsTokenProvider` implements `AccessTokenProvider`
  and posts the standard {@code grant_type=client_credentials} flow to the
  configured token endpoint. Supports both client-authentication schemes
  (HTTP Basic — default — and `client_id`/`client_secret` form post via
  `authStyle(AuthStyle.FORM_POST)`); forwards optional `scope` / `audience`
  parameters; converts the server's `expires_in` into an
  `AccessToken.expiresAt()` minus a configurable `expiryLeeway` (default
  30 s). Compose with `RefreshingBearerAuthProvider` for caching +
  single-in-flight refresh. (Roadmap 1.7 — OAuth2 half. AWS SigV4
  intentionally deferred.)
- **`reactive-http-client-test` artifact.** New companion module
  containing `MockReactiveHttpClient<T>` (builds a real
  `@ReactiveHttpClient` proxy against an in-process `ExchangeFunction`,
  records every outbound exchange, and serves canned responses by matcher),
  `RecordedExchange` (materialised request snapshot — method, URI,
  headers, UTF-8 body), and `ErrorCategoryAssertions` (fluent
  `assertThatFails(mono).hasErrorCategory(...).hasStatusCode(...)` helper).
  Pulls `spring-test` as a compile dep so consumers don't need to add it
  themselves. (Roadmap 1.3)
- Canonical safety-net timeout property names
  `reactive.http.network.network-read-timeout-ms` and
  `reactive.http.network.network-write-timeout-ms`. Existing `read-timeout-ms` /
  `write-timeout-ms` keys continue to bind to the same backing fields and are
  now flagged as deprecated configuration properties — IDEs will show the
  replacement. README §2.5 now includes a "which timeout fires first" matrix
  distinguishing the channel-level safety nets from per-request
  `@TimeoutMs` / `resilience.timeout-ms` values. (Roadmap 2.4)

### Deprecated

- `reactive.http.network.read-timeout-ms` and `reactive.http.network.write-timeout-ms`
  — use `network-read-timeout-ms` / `network-write-timeout-ms` instead. Both keys
  bind to the same backing field, so existing configuration continues to work.

### Fixed

- **`WebClientCustomizer` beans are now applied to every `@ReactiveHttpClient`
  proxy.** The 1.8.1 prototype-scope fix for the auth-header leak inadvertently
  stopped running `WebClientCustomizer` beans — our `starterWebClientBuilder()`
  returned a bare `WebClient.builder()` without applying customizers. This mirrors
  Spring Boot's own `WebClientAutoConfiguration` pattern: the prototype bean now
  takes `ObjectProvider<WebClientCustomizer>` and applies each in `@Order` before
  handing the builder to the factory. Users who lost Sleuth / Micrometer / custom
  instrumentation on upgrade to 1.8.1 will regain it. (Roadmap 3.9)

### Security

- Inbound headers captured by `InboundHeadersWebFilter` and logged via
  `HttpExchangeLogContext#inboundHeaders()` are now subject to the same redaction
  rules as outbound headers, closing a leakage path introduced in 1.8.0 where
  upstream-supplied credentials could land in log aggregation.
- Correlation-id length and character-set validation prevent log forgery and
  Reactor-context bloat via oversized or control-character-laden inbound values.

---

## [1.8.1] – 2026-04-23

### Fixed

- **Auth header leakage between clients** — `starterWebClientBuilder()` in `ReactiveHttpClientAutoConfiguration` was registered as a singleton. Because `WebClient.Builder` is mutable, each client's factory bean called `.filter()` on the *same* shared instance, accumulating filters across clients. A client with no `AuthProvider` configured would therefore inherit the `OutboundAuthFilter` of whichever client was initialised first, causing that client's auth headers to appear on all its outbound requests. Fixed by adding `@Scope("prototype")` to `starterWebClientBuilder()`, mirroring Spring Boot's own `WebClientAutoConfiguration`.
- **Double metrics/log recording on null-body responses** — when an external API returned a null or empty body, Reactor fired `doOnTerminate` for the `onComplete` signal and then `doOnCancel` as Netty released the connection, causing both hooks to execute. An `AtomicBoolean` guard now ensures only the first signal (termination for a completed request, cancellation for a true cancel) triggers logging and observer notification.

### Changed

- Extracted `reportExchange(...)` private helper in `ReactiveClientInvocationHandler` to consolidate the duplicated logger/observer dispatch logic shared by `doOnTerminate` and `doOnCancel` in both the Mono and Flux paths.

---

## [1.8.0] – 2026-04-22

### Added

- `InboundHeadersWebFilter` — a new `WebFilter` that captures a snapshot of all inbound request headers from the upstream caller and stores them in the Reactor `Context` under `InboundHeadersWebFilter.INBOUND_HEADERS_CONTEXT_KEY`. Auto-registered by `ReactiveHttpClientAutoConfiguration` when Spring WebFlux is present (`@ConditionalOnWebApplication(REACTIVE)`).
- `HttpExchangeLogContext#inboundHeaders()` — new field on the log-context record carrying the inbound headers map. Populated automatically when `InboundHeadersWebFilter` is active and the outbound call originates within a WebFlux request chain; defaults to an empty map otherwise.

### Changed

- `DefaultHttpExchangeLogger` now includes `inboundHeaders=` in both success (`INFO`) and error (`WARN`) log lines, making it easy to correlate outbound calls with the triggering inbound request.
- `ReactiveClientInvocationHandler` uses `Mono.deferContextual` / `Flux.deferContextual` to read inbound headers from the Reactor `Context` and passes them into the log context.

### Removed

- `UpstreamHeadersWebFilter` — replaced by the more general `InboundHeadersWebFilter`.

---

## [1.7.0] – 2026-04-22

### Added

- `HttpClientObserverEvent.getAttemptCount()` — total subscription attempts for an invocation (1 = first-try success; >1 = Resilience4j retry fired at least once). Useful for detecting degraded downstream services.
- `http.client.requests.attempts` Micrometer `DistributionSummary` recorded by `MicrometerHttpClientObserver` alongside the existing timer. Tags: `client.name`, `api.name`, `http.method`, `uri`. A p95 > 1 signals a degraded downstream.
- `ResilienceOperatorApplierTest` — unit tests covering `NoopResilienceOperatorApplier` (passthrough, error propagation) and `Resilience4jOperatorApplier` (success paths, error recording, saturated bulkhead rejection, null-registry fallthrough, non-Resilience4j constructor arguments).

### Changed

- `NetworkConfig` defaults for `readTimeoutMs` and `writeTimeoutMs` raised from 5 000 ms to **60 000 ms (60 s)**. These Netty-level handlers are intentionally larger than any per-request business timeout and act as absolute safety nets for pooled connections.

### Fixed

- **`ReadTimeoutHandler` restored** as a Netty `doOnConnected` channel handler. A previous change had mistakenly replaced it with a global `HttpClient.responseTimeout()` call, which conflated channel-level safety-net behaviour with per-request timeout semantics.
- `PrematureCloseException` (fired when a per-request `responseTimeout` is cancelled by Reactor Netty) now maps to `ErrorCategory.TIMEOUT` instead of falling through to `UNKNOWN`.
- Metric duration now reflects **total elapsed time across all retry attempts**. Previously `start` was reset in `doOnSubscribe` on each re-subscription, so the recorded duration captured only the last attempt.
- `logRequest()` debug log no longer fires on every retry re-subscription; it is emitted exactly once per invocation.
- Request body serialization (`objectMapper.writeValueAsBytes`) is now **cached** with `Mono.cache()` so retries reuse the already-serialised bytes instead of re-running JSON serialisation on `boundedElastic`.

---

## [1.6.0] – 2026-04-22

### Added

- `@PATCH` annotation and method-parser support for HTTP PATCH verbs (H6).
- `RequestSerializationException` for JSON serialization failures previously wrapped as `AuthProviderException` (M9).
- Observability error categories for network failures:
  - `ErrorCategory.CONNECT_ERROR` for `ConnectException`
  - `ErrorCategory.UNKNOWN_HOST` for `UnknownHostException`
- Support for Java `default` methods on `@ReactiveHttpClient` interfaces via `InvocationHandler.invokeDefault` (H4).
- Request method/URL context on `HttpClientException` and `RemoteServiceException`, including cause-accepting constructors (L10).
- Additional MDC key fallbacks (`correlationId`, `X-Correlation-Id`, `traceId`) for correlation-ID propagation (M8).
- Bounded `HttpExchangeLogger` cache (max 256 entries) with one-time warning on eviction (M10).
- Message-overload constructor on `AuthProviderException` for richer diagnostics (L9).
- Test coverage for: `@PATCH`, byte[]/String bodies, `default` interface methods, non-reactive return types rejected at parse time, concurrent `RefreshingBearerAuthProvider.invalidate()` races, URL-encoded auth query params, Netty `ReadTimeoutException` classification, and registrar skip-if-present across both orderings (T1–T7, L7).

### Changed

- `DefaultHttpExchangeLogger` redacts sensitive headers (`Authorization`, `Cookie`, `Set-Cookie`, `Proxy-Authorization`, `X-Api-Key`) and logs bodies at DEBUG only when explicitly enabled (C1).
- Exception messages no longer embed response bodies; stored bodies are truncated to 4 KB and remain available via `getResponseBody()` (C2).
- `DefaultErrorDecoder` truncates error bodies to 4 KB before constructing exceptions (C3).
- `OutboundAuthFilter` URL-encodes auth-provider query parameter values (H1); validates auth header values for CRLF and control characters (C4).
- `MethodMetadataCache` rejects non-reactive return types at parse time (H2) and rejects non-blank `@HeaderParam` values on `Map` parameters (H8).
- `MicrometerHttpClientObserver` tags network errors with `http.status_code="NONE"` instead of `CLIENT_ERROR`; defaults `clientName` to `"UNKNOWN"` on null (H5, L8).
- `MethodMetadata` collections are frozen (`Map.copyOf` / `Set.copyOf`) after parsing (M1).
- Auth body serialization runs on `Schedulers.boundedElastic()` and is skipped entirely when no auth provider is configured (M2).
- Observability duration is now measured from subscribe time, not proxy-invoke time (M3).
- Consolidated timeout-resolution helpers into a single source of truth (M4).
- `CorrelationIdWebFilter` sets (rather than appends) `X-Correlation-Id` on outbound requests to prevent duplicates (M7).
- `loggingFilter` now logs method, URL, status, and latency as documented, tagging outcomes as `OK`, `HTTP_ERROR`, or `TRANSPORT_ERROR` (M5).
- Request-argument resolver validates `@HeaderParam` values for CRLF / control characters (C4).
- Internal cleanups: simplified `getObserver()`, removed redundant `Set<String>` qualifier, replaced Stream-based `getHeaderIgnoreCase` with a loop, switched `Class.forName` to `ClassUtils.resolveClassName` for container safety (L1–L4).

### Fixed

- Netty `ReadTimeoutException` now maps to `ErrorCategory.TIMEOUT` instead of `UNKNOWN` (H3).
- Race in `RefreshingBearerAuthProvider.invalidate()` where an in-flight refresh could re-populate the cache immediately after invalidation is resolved via a monotonic invalidation epoch (H7).
- `RemoteServiceException` message formatting when method is `"UNKNOWN"` or only one of method/URL is present.

### Security

- Sensitive-header redaction (C1) and the removal of response bodies from exception messages (C2, C3) reduce the risk of credentials and PII leaking into logs, metric tags, and error-reporting pipelines.
- Header-injection hardening via CRLF / control-character validation on `@HeaderParam` and auth-provider header values (C4).

### Deprecated

- The `HttpClientObserverEvent` constructor that leaves `errorCategory` unset (L6).

### Build

- Pinned `maven-surefire-plugin` to 3.2.5 in the parent POM so `mvn test` discovers JUnit 5 tests without an explicit plugin coordinate.

### Removed

- Dead utility `UriTemplateExpander` (M6).

---

## [1.5.1] – 2026-04-21

### Fixed

- Restored API-level timeout precedence so method `@TimeoutMs` overrides global network timeout per request.
- Supported explicit timeout disable with `@TimeoutMs(0)` even when global `read-timeout-ms` is configured.

### Changed

- Clarified `reactive.http.network.read-timeout-ms` semantics as Reactor Netty response timeout in code/docs.

---

## [1.4.0] – 2026-04-20

### Added

- Added `ErrorCategory.RESPONSE_DECODE_ERROR` to classify response decode/deserialization failures
  during `bodyToMono` / `bodyToFlux` conversion (e.g. malformed JSON, mismatched type/shape, encoded payload).
- Added test coverage for Mono/Flux decode-failure observability category emission.

---

## [1.3.0] – 2026-04-20

### Added

- Added `ErrorCategory.AUTH_PROVIDER_ERROR` for outbound authentication provider failures.
- Added `AuthProviderException` to normalize errors raised by `AuthProvider`.
- Added test coverage for auth-provider failure wrapping and observability error category emission.

### Changed

- Updated `OutboundAuthFilter` to map auth-provider failures to `AuthProviderException` without double wrapping.
- Updated `ReactiveClientInvocationHandler` to classify `AuthProviderException` as `AUTH_PROVIDER_ERROR`.
- Updated observability docs/tag semantics to include `AUTH_PROVIDER_ERROR`.

---

## [1.2.0] – 2026-04-19

### Added

- Global network policy configuration via `reactive.http.network`:
  - `connect-timeout-ms`
  - `read-timeout-ms`
  - `write-timeout-ms`
  - `connection-pool.max-connections`
  - `connection-pool.pending-acquire-timeout-ms`
- Built-in outbound bearer auth refresh strategy:
  - `AccessToken` model and `AccessTokenProvider` abstraction.
  - `RefreshingBearerAuthProvider` with cached token reuse, refresh-before-expiry window, and single in-flight refresh deduplication.
- Unit tests for token reuse, refresh trigger, concurrent refresh deduplication, and expired-token rejection.

### Changed

- `ReactiveHttpClientFactoryBean` now applies global transport timeout and pool policy to all clients.
- Request-timeout ownership is simplified:
  1. method `@TimeoutMs`
  2. `resilience.timeout-ms` (when enabled)
  3. no request timeout
- Removed client-level request-timeout precedence from invocation timeout resolution.
- Updated README examples and property docs to align with the global network policy model.

---

## [1.1.0] – 2026-04-16

### Added

- **`ErrorCategory` enum** – high-level classification of HTTP client errors
  (`CLIENT_ERROR`, `RATE_LIMITED`, `SERVER_ERROR`, `TIMEOUT`, `CANCELLED`, `UNKNOWN`).
  Allows category-based error handling without hard-coding status codes.
- **`HttpClientException.getErrorCategory()`** – returns `RATE_LIMITED` for HTTP 429,
  `CLIENT_ERROR` for all other 4xx responses.
- **`RemoteServiceException.getErrorCategory()`** – always returns `SERVER_ERROR`.
- New constructor overloads on `HttpClientException` and `RemoteServiceException`
  that accept an explicit `Throwable cause` for wrapping low-level errors.
- **Integration tests** for HTTP edge cases (`DefaultErrorDecoderTest`, `HttpEdgeCasesTest`):
  - 429 / 5xx response decoding and `ErrorCategory` mapping.
  - Timeout behavior (using virtual-time `StepVerifier`).
  - Cancellation behavior.
- **CI workflow** (`.github/workflows/ci.yml`) – runs `mvn verify` on JDK 17 and 21
  for every push to `main` and every PR targeting `main`.

### Changed

- `HttpClientException` and `RemoteServiceException` now expose `getErrorCategory()`
  in addition to the existing `getStatusCode()` and `getResponseBody()` methods.
  All existing constructors and method signatures are **backward-compatible**.

### Fixed

- Fixed an issue where `X-Correlation-Id` was not forwarded on outbound reactive
  HTTP client calls.

---

## [1.0.0] – 2025-04-10

### Added

- Initial release of `reactive-http-client-starter`.
- Declarative annotation-driven HTTP client (`@ReactiveHttpClient`, `@GET`, `@POST`,
  `@PUT`, `@DELETE`, `@PathVar`, `@QueryParam`, `@HeaderParam`, `@Body`).
- Auto-configuration via `@EnableReactiveHttpClients`.
- Resilience4j integration (circuit-breaker, retry, bulkhead, timeout).
- Micrometer observability support (`MicrometerHttpClientObserver`).
- Per-method timeout override via `@TimeoutMs`.
- Request/response exchange logging via `@LogHttpExchange`.
- `DefaultErrorDecoder` – maps 4xx → `HttpClientException`, 5xx → `RemoteServiceException`.

---

## Versioning Policy

This project uses **Semantic Versioning** (`MAJOR.MINOR.PATCH`):

| Change type | Version bump |
|---|---|
| Backward-incompatible API change | `MAJOR` (e.g., 1.x → 2.0.0) |
| New backward-compatible feature | `MINOR` (e.g., 1.0.x → 1.1.0) |
| Backward-compatible bug fix | `PATCH` (e.g., 1.1.x → 1.1.1) |

### Release process

1. Update `<version>` in the root `pom.xml` (remove `-SNAPSHOT` suffix for releases).
2. Update this file: move items from `[Unreleased]` to a new versioned section.
3. Create and push a git tag: `git tag v<VERSION> && git push origin v<VERSION>`.
4. Create a GitHub Release from that tag.
   The `publish-maven-central.yml` workflow will automatically build, sign, and publish the artifacts.

[Unreleased]: https://github.com/huynhngochuyhoang/reactive-http-client/compare/v3.3.0...HEAD
[3.3.0]: https://github.com/huynhngochuyhoang/reactive-http-client/compare/v3.2.0...v3.3.0
[3.2.0]: https://github.com/huynhngochuyhoang/reactive-http-client/compare/v3.1.0...v3.2.0
[3.1.0]: https://github.com/huynhngochuyhoang/reactive-http-client/compare/v3.0.0...v3.1.0
[3.0.0]: https://github.com/huynhngochuyhoang/reactive-http-client/compare/v2.14.1...v3.0.0
[2.14.1]: https://github.com/huynhngochuyhoang/reactive-http-client/compare/v2.14.0...v2.14.1
[2.14.0]: https://github.com/huynhngochuyhoang/reactive-http-client/compare/v2.13.0...v2.14.0
[2.13.0]: https://github.com/huynhngochuyhoang/reactive-http-client/compare/v2.12.0...v2.13.0
[2.12.0]: https://github.com/huynhngochuyhoang/reactive-http-client/compare/v2.11.0...v2.12.0
[2.11.0]: https://github.com/huynhngochuyhoang/reactive-http-client/compare/v2.10.0...v2.11.0
[2.10.0]: https://github.com/huynhngochuyhoang/reactive-http-client/compare/v2.9.0...v2.10.0
[2.9.0]: https://github.com/huynhngochuyhoang/reactive-http-client/compare/v2.8.0...v2.9.0
[2.8.0]: https://github.com/huynhngochuyhoang/reactive-http-client/compare/v2.7.0...v2.8.0
[2.7.0]: https://github.com/huynhngochuyhoang/reactive-http-client/compare/v2.6.0...v2.7.0
[2.6.0]: https://github.com/huynhngochuyhoang/reactive-http-client/compare/v2.5.0...v2.6.0
[2.5.0]: https://github.com/huynhngochuyhoang/reactive-http-client/compare/v2.4.0...v2.5.0
[2.4.0]: https://github.com/huynhngochuyhoang/reactive-http-client/compare/v2.3.0...v2.4.0
[2.3.0]: https://github.com/huynhngochuyhoang/reactive-http-client/compare/v2.2.0...v2.3.0
[2.2.0]: https://github.com/huynhngochuyhoang/reactive-http-client/compare/v2.1.0...v2.2.0
[2.1.0]: https://github.com/huynhngochuyhoang/reactive-http-client/compare/v2.0.0...v2.1.0
[2.0.0]: https://github.com/huynhngochuyhoang/reactive-http-client/compare/v1.16.0...v2.0.0
[1.16.0]: https://github.com/huynhngochuyhoang/reactive-http-client/compare/v1.15.0...v1.16.0
[1.15.0]: https://github.com/huynhngochuyhoang/reactive-http-client/compare/v1.14.0...v1.15.0
[1.14.0]: https://github.com/huynhngochuyhoang/reactive-http-client/compare/v1.13.1...v1.14.0
[1.13.1]: https://github.com/huynhngochuyhoang/reactive-http-client/compare/v1.13.0...v1.13.1
[1.13.0]: https://github.com/huynhngochuyhoang/reactive-http-client/compare/v1.12.1...v1.13.0
[1.12.1]: https://github.com/huynhngochuyhoang/reactive-http-client/compare/v1.12.0...v1.12.1
[1.12.0]: https://github.com/huynhngochuyhoang/reactive-http-client/compare/v1.11.1...v1.12.0
[1.11.1]: https://github.com/huynhngochuyhoang/reactive-http-client/compare/v1.11.0...v1.11.1
[1.11.0]: https://github.com/huynhngochuyhoang/reactive-http-client/compare/v1.10.1...v1.11.0
[1.10.1]: https://github.com/huynhngochuyhoang/reactive-http-client/compare/v1.10.0...v1.10.1
[1.10.0]: https://github.com/huynhngochuyhoang/reactive-http-client/compare/v1.9.0...v1.10.0
[1.9.0]: https://github.com/huynhngochuyhoang/reactive-http-client/compare/v1.8.1...v1.9.0
[1.8.1]: https://github.com/huynhngochuyhoang/reactive-http-client/compare/v1.8.0...v1.8.1
[1.8.0]: https://github.com/huynhngochuyhoang/reactive-http-client/compare/v1.7.0...v1.8.0
[1.7.0]: https://github.com/huynhngochuyhoang/reactive-http-client/compare/v1.6.0...v1.7.0
[1.6.0]: https://github.com/huynhngochuyhoang/reactive-http-client/compare/v1.5.1...v1.6.0
[1.5.1]: https://github.com/huynhngochuyhoang/reactive-http-client/compare/v1.5.0...v1.5.1
[1.4.0]: https://github.com/huynhngochuyhoang/reactive-http-client/compare/v1.3.0...v1.4.0
[1.3.0]: https://github.com/huynhngochuyhoang/reactive-http-client/compare/v1.2.0...v1.3.0
[1.2.0]: https://github.com/huynhngochuyhoang/reactive-http-client/compare/v1.1.0...v1.2.0
[1.1.0]: https://github.com/huynhngochuyhoang/reactive-http-client/compare/v1.0.0...v1.1.0
[1.0.0]: https://github.com/huynhngochuyhoang/reactive-http-client/releases/tag/v1.0.0
