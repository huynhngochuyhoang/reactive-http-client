# Reactive HTTP Client — Roadmap V19 Execution Checklist

Companion to [`ROADMAP.md`](ROADMAP.md). Execute priorities in order. Do not
move the default reactor to Spring Boot 4 until the isolated migration gates
show that the `3.0.0` line is viable.

---

## Priority 1 — Close `2.14.0` and Establish Release Lanes

### [x] 1.1 Close the published release without moving baselines early

- [x] Record `2.14.0` in `CHANGELOG.md` with release date `2026-07-10`.
- [x] Move the released V18 entries from `Unreleased` into `2.14.0`.
- [x] Update the `Unreleased` comparison link to start at `v2.14.0`.
- [x] Add the `2.14.0` comparison link from `v2.13.0`.
- [x] Confirm the remote `v2.14.0` tag points at the intended release source.
- [x] Resolve `reactive-http-client-starter:2.14.0` from the release environment.
- [x] Resolve `reactive-http-client-test:2.14.0` from the release environment.
- [x] Resolve `reactive-http-client-otel:2.14.0` from the release environment.
- [x] Do not move `api.compatibility.baseline.version` until all published
      artifacts resolve.
- [x] Define the Boot 3.5 `2.x` maintenance branch and next maintenance version.
- [x] Document that `2.x` receives security and critical fixes while `3.x`
      owns Boot 4 migration work.
- [x] Keep historical V18 release and benchmark evidence unchanged.
- [x] Run focused release documentation tests.
- [x] Run `git diff --check`.

Evidence:

- User confirmed `2.14.0` was published on 2026-07-10.
- Remote tag `v2.14.0` resolves to `2f877a37c747fe92e5cd41d3c44dc0901e891f60`,
  the current release source whose root POM declares `2.14.0`.
- Forced mirror refresh resolved published starter, test-helper, and OTel
  `2.14.0` artifacts.
- Moved the Boot 3.5 maintenance reactor and all module parents to `2.14.1` only
  after artifact resolution succeeded.
- Moved `api.compatibility.baseline.version`, benchmark published-baseline
  commands, report paths, and generated release evidence to published `2.14.0`.
- Updated README and quick-start dependency snippets to maintenance version
  `2.14.1`; historical V18 release and promoted benchmark reports remain
  unchanged.
- Defined `2.x` as the Boot 3.5 maintenance branch rooted at `v2.14.0`, with
  `2.14.1` as its next version and Boot 4 work isolated for future `3.x`.
- `mvn -U -q dependency:get -Dartifact=io.github.huynhngochuyhoang:reactive-http-client-starter:2.14.0` passed.
- `mvn -U -q dependency:get -Dartifact=io.github.huynhngochuyhoang:reactive-http-client-test:2.14.0` passed.
- `mvn -U -q dependency:get -Dartifact=io.github.huynhngochuyhoang:reactive-http-client-otel:2.14.0` passed.
- `mvn -q -pl reactive-http-client-starter -Dtest=DocumentationReleaseArtifactTest test` passed.
- `mvn -q -Papi-compatibility -DskipTests verify` passed against published
  `2.14.0`.
- `mvn -q -pl reactive-http-client-starter -Papi-compatibility -DskipTests verify`
  passed against published `2.14.0`.
- `git diff --check` passed.

---

## Priority 2 — Latest Spring Boot 3.5 Migration Bridge

### [x] 2.1 Prepare the supported line before Boot 4

- [x] Query the latest published Spring Boot `3.5.x` patch when execution starts.
- [x] Compare it with the V18-reviewed `3.5.16` candidate.
- [x] Record exact managed Spring Framework, WebFlux, Reactor Netty, Netty,
      Micrometer, OTel, Jackson, and test versions.
- [x] Run the full reactor with the selected Boot 3.5 patch override.
- [x] Run release smoke with the selected patch.
- [x] Run AOT and native smoke with the selected patch.
- [x] Run configuration metadata and generated documentation tests.
- [x] Run optional Actuator, Micrometer, OTel, and Resilience4j absence/presence
      tests.
- [x] Audit production use of APIs deprecated in the latest Boot 3.5 line.
- [x] Remove deprecated use only where the change remains compatible with `2.x`.
- [x] Decide whether the patch movement ships as a separate `2.x` maintenance
      release or remains deferred with evidence.
- [x] Do not include Boot 4 public API changes in this priority.
- [x] Run API compatibility against published `2.14.0` after the baseline can
      safely move.
- [x] Run `git diff --check`.

Evidence:

- Maven Central metadata queried on 2026-07-10 listed `3.5.16` as the latest
  published `3.5.x` patch, matching the V18 candidate.
- Adopted Boot `3.5.16` as the default dependency-management patch for the
  `2.14.1` maintenance release. CI retains `3.5.0` as the minimum smoke row and
  adds `3.5.16`; Boot 4 work remains isolated.
- Resolved versions: Spring Framework/WebFlux/Test `6.2.19`, Reactor Netty HTTP
  `1.2.18`, Netty HTTP codec `4.1.135.Final`, Micrometer Core `1.15.12`, OTel API
  `1.49.0`, Jackson Databind `2.21.4`, JUnit Jupiter `5.12.2`, and Mockito Core
  `5.17.0`.
- `mvn -Dspring-boot.version=3.5.16 verify` passed all 763 tests before changing
  the default; `mvn -q verify` then passed with `3.5.16` as the default.
- `mvn -q -Dspring-boot.version=3.5.16 -Prelease-smoke test` passed.
- `mvn -q -Prelease-smoke -Dspring-boot.version=3.5.0 test` passed, preserving
  minimum-patch evidence.
- Focused TLS, AOT, configuration-metadata, and generated-documentation tests
  passed; the full suite covered Actuator/Micrometer absence, Resilience4j
  presence/absence, and OTel presence/absence paths.
- Deprecation compilation removed the Reactor Netty TLS warning. Only calls to
  the starter-owned deprecated timeout compatibility accessor remain, by design.
- The Boot `3.5.16` native fixture passed JVM AOT processing, compiled with
  GraalVM Community Java 21, and its generated executable started successfully.
  The internal mirror lacked the optional reachability-metadata ZIP, so the
  isolated container resolved that ZIP from Maven Central.
- `mvn -q -Papi-compatibility -DskipTests verify` passed against published
  `2.14.0`.
- `git diff --check` passed.

---

## Priority 3 — Isolated Spring Boot 4 Build Spike

### [x] 3.1 Establish a reproducible migration profile

- [x] Select and document the minimum Boot 4 line under evaluation.
- [x] Select and document the current stable Boot 4 line for the test matrix.
- [x] Create an isolated profile, branch, or temporary reactor property for the
      Boot 4 spike.
- [x] Keep the normal `2.x` build on Boot 3.5 during the spike.
- [x] Resolve Boot 4 artifacts from a repository known to contain them.
- [x] Distinguish local mirror failures from dependency or source failures.
- [x] Compile starter, test-helper, OTel, and benchmark modules independently.
- [x] Record exact managed dependency versions and management sources.
- [x] Classify failures as module/package, Jackson, test infrastructure,
      optional integration, AOT/native, or transport issues.
- [x] Add a deterministic CI matrix only after local resolution and compilation
      are repeatable.
- [x] Ensure no experimental Boot 4 artifact can be published.
- [x] Run `git diff --check`.

Evidence:

- Selected Boot `4.0.0` as the minimum line under evaluation and Boot `4.1.0`
  as the current stable line listed by Maven Central metadata on 2026-07-11.
- Added the opt-in `boot4-spike` profile. It defaults to `4.0.0`, allows a
  `-Dspring-boot.version=4.1.0` override, and sets `maven.deploy.skip=true` plus
  `skipPublishing=true`. The normal reactor still resolves Boot `3.5.16`.
- Added `.mvn/boot4-spike-settings.xml`, which routes only explicit spike
  commands to Maven Central. The configured internal mirror could not resolve
  the Boot `4.0.0` BOM; direct Central effective-POM and compile resolution
  succeeded, separating repository availability from source compatibility.
- Boot `4.0.0` resolves Spring/WebFlux/Test `7.0.1`, Reactor Netty `1.3.0`,
  Netty `4.2.7.Final`, Micrometer `1.16.0`, OTel `1.55.0`, Jackson 3 `3.0.2`,
  Jackson 2 compatibility `2.20.1`, JUnit `6.0.1`, and Mockito `5.20.0`.
- Boot `4.1.0` resolves Spring/WebFlux/Test `7.0.8`, Reactor Netty `1.3.6`,
  Netty `4.2.15.Final`, Micrometer `1.17.0`, OTel `1.62.0`, Jackson 3 `3.1.4`,
  Jackson 2 compatibility `2.21.4`, JUnit `6.0.3`, and Mockito `5.23.0`.
- Independent starter compiles on both lines reach source compilation and fail
  on relocated Actuator health types and `WebClientCustomizer`; these are Boot
  module/package ownership failures for Priority 4.
- Independent test-helper compiles on both lines fail because Spring 7
  `HttpHeaders` no longer exposes the used `containsKey` contract; this is a
  Spring/test-helper API migration.
- Independent OTel compiles on both lines fail on relocated
  `WebClientCustomizer`; this is optional-integration/module ownership.
- The benchmark harness compiles on both lines while consuming the locally
  installed Boot 3 starter. This verifies only harness source compatibility,
  not a Boot 4 starter path.
- Reactor Netty and Netty resolve without a transport compile finding. Jackson
  3 and AOT/native remain downstream gates because starter compilation stops
  first; they are not reported as passing.
- No Boot 4 CI row was added because starter, test-helper, and OTel compilation
  is not repeatable yet. Later priorities own those migrations.
- `mvn -q -DforceStdout help:evaluate -Dexpression=spring-boot.version` returned
  `3.5.16` without the spike profile.
- `git diff --check` passed.

---

## Priority 4 — Boot 4 Module and Auto-Configuration Migration

### [x] 4.1 Replace Boot 3 module assumptions

- [x] Inventory every production `org.springframework.boot.*` import.
- [x] Map each import to its Boot 4 module, package, and dependency owner.
- [x] Replace broad or obsolete dependencies with focused Boot 4 modules.
- [x] Update auto-configuration imports and registration metadata.
- [x] Migrate `WebClientCustomizer` integration to the supported Boot 4 API.
- [x] Migrate configuration-properties and metadata processing dependencies.
- [x] Migrate Actuator health and endpoint imports.
- [x] Preserve conditional back-off when Actuator is absent.
- [x] Verify `rhttpclients` endpoint discovery and alphanumeric endpoint ID.
- [x] Use `spring-boot-starter-classic` only as a temporary diagnostic if needed.
- [x] Remove classic compatibility dependencies from the final graph.
- [x] Add focused packaged-application auto-configuration tests.
- [x] Run generated metadata and Markdown-link tests.
- [x] Run `git diff --check`.

Evidence:

- Inventoried every production Boot import and documented its Boot 3.5 and Boot 4 artifact/package owner in `docs/20-native-release-compatibility.md`.
- Added profile-selected Boot 4 adapters for `WebClientCustomizer` and Actuator health. The Boot 4 graph uses focused `spring-boot-webclient` and optional `spring-boot-health`; it does not contain `spring-boot-starter-classic`.
- Kept auto-configuration registration in the existing `AutoConfiguration.imports` resources and retained the optional endpoint condition.
- Migrated OTel propagation to the starter-owned `ReactiveHttpClientCustomizer`, avoiding a direct dependency on either Boot customizer package.
- Added `Boot4AutoConfigurationTest` for builder customization, health registration, the alphanumeric `rhttpclients` endpoint ID, and optional Actuator absence.
- Replaced Spring 7-removed header map calls with Spring 6/7-compatible reads and moved streaming envelopes to `retrieve().toEntityFlux(...)` while preserving pooled-buffer discard release.
- `mvn -s .mvn/boot4-spike-settings.xml -q -Pboot4-spike -pl reactive-http-client-starter,reactive-http-client-test,reactive-http-client-otel -am test` passed on Boot `4.0.0`.
- Boot `4.1.0` multi-module compilation and focused `Boot4AutoConfigurationTest,StreamingResponseTest` passed.
- Normal Boot 3.5 starter, test-helper, and OTel compilation passed; focused auto-configuration, streaming, and OTel tests passed.
- Generated metadata/documentation checks and the full normal reactor verification passed.
- `git diff --check` passed.

---

## Priority 5 — Jackson 3 and Codec Ownership

### [ ] 5.1 Define the `3.0.0` serialization contract

- [ ] Inventory public and internal Jackson 2 types in all modules.
- [ ] Record public APIs that expose `com.fasterxml.jackson.databind.ObjectMapper`.
- [ ] Decide whether public customization uses Jackson 3 directly or a narrow
      starter-owned serialization contract.
- [ ] Keep default Boot 4 applications free from a required Jackson 2 mapper.
- [ ] Ensure WebClient encoding and auth signing consume the same bytes.
- [ ] Migrate built-in SigV4 JSON signing and charset-sensitive String signing.
- [ ] Migrate Problem Detail mapping.
- [ ] Migrate OAuth2 sanitized error-body decoding without losing configured
      codecs or typed response metadata.
- [ ] Migrate diagnostics and contract snapshot JSON rendering.
- [ ] Migrate mock helper body serialization and assertions.
- [ ] Cover Java time modules, naming strategies, Kotlin, custom serializers,
      and unknown-property behavior.
- [ ] Keep any Jackson 2 compatibility module temporary and deprecated.
- [ ] Add every public serialization break to the `3.0.0` migration guide.
- [ ] Run focused serialization, auth, error, and test-helper tests.
- [ ] Run `git diff --check`.

Evidence:

- Pending.

---

## Priority 6 — Framework 7 and Transport Correctness

### [ ] 6.1 Revalidate request framing and response ownership

- [ ] Run real-server JSON, `ResponseEntity`, streaming, bodiless, redirect,
      timeout, cancellation, and error-drain tests on the Boot 4 stack.
- [ ] Add a raw HTTP/1.1 fixture that sends POST then PUT on one persistent
      connection.
- [ ] Confirm the normal sequence produces no decoder warning or leaked bytes.
- [ ] Add a deliberately malformed `Content-Length` fixture that reproduces a
      framing failure.
- [ ] Verify Netty's synthetic `GET /bad-request HTTP/1.0` is reported only as a
      decoder placeholder, never as the application endpoint.
- [ ] Audit default headers, `@HeaderParam`, header maps, customizers, and inbound
      forwarding for hop-by-hop/framing headers.
- [ ] Define supported behavior for `Content-Length`, `Transfer-Encoding`,
      `Connection`, `Expect`, and `Host` overrides.
- [ ] Cover direct loopback and one representative proxy or sidecar path where
      available.
- [ ] Revalidate HTTP/1.1, TLS H2, and H2C separately.
- [ ] Revalidate unexpected bodiless responses and pooled connection reuse.
- [ ] Revalidate streaming body ownership after outer publisher completion.
- [ ] Document the full decoder cause required for future transport reports.
- [ ] Run transport-focused tests and `git diff --check`.

Evidence:

- Pending.

---

## Priority 7 — Optional Integrations and Resilience

### [ ] 7.1 Preserve optional activation and no-op behavior

- [ ] Review Resilience4j compatibility with Framework 7 and the selected
      Reactor line.
- [ ] Keep Resilience4j dependencies optional and version-managed explicitly.
- [ ] Verify no-registry/no-operator fallback behavior.
- [ ] Verify retry, circuit breaker, rate limiter, bulkhead, and metrics
      independently.
- [ ] Verify strict retry diagnostics match actual operator availability.
- [ ] Verify Micrometer observer and health activation/back-off.
- [ ] Verify OTel observer, propagation, semantic attributes, and back-off.
- [ ] Verify OAuth2 client credentials under Boot 4 codecs.
- [ ] Verify AWS SigV4 under Boot 4 codecs and transport.
- [ ] Keep diagnostics endpoint and health details opt-in and sanitized.
- [ ] Add minimal-classpath tests without Actuator, Micrometer, OTel, or
      Resilience4j.
- [ ] Check published POMs for accidental transitive optional integrations.
- [ ] Run focused optional-integration tests and `git diff --check`.

Evidence:

- Pending.

---

## Priority 8 — Boot 4 AOT and Native Image Baseline

### [ ] 8.1 Rebuild native evidence

- [ ] Select the GraalVM/native-image baseline supported by the chosen Boot 4
      line.
- [ ] Update native build commands and generated release evidence.
- [ ] Re-audit reflection hints for client interfaces and inherited methods.
- [ ] Re-audit proxy hints and nested configuration binding hints.
- [ ] Re-audit metadata, Maven version resource, and diagnostics resources.
- [ ] Verify diagnostics endpoint and health behavior in native mode.
- [ ] Build a native smoke application that performs a loopback request.
- [ ] Exercise inherited generic endpoints and Problem Detail mapping.
- [ ] Exercise one auth provider and one optional observability integration.
- [ ] Record Java, GraalVM, Boot, Framework, starter, and commit versions.
- [ ] Run AOT/native tests and `git diff --check`.

Evidence:

- Pending.

---

## Priority 9 — Public API and `3.0.0` Migration Guide

### [ ] 9.1 Make every major-line break explicit

- [ ] Freeze the latest published `2.x` public surface map.
- [ ] Produce a report-only API diff from published `2.x` to the `3.0.0`
      candidate.
- [ ] Categorize every break as required, intentional, or accidental.
- [ ] Remove unrelated accidental API breaks.
- [ ] Preserve annotation and exception semantics where Boot 4 does not require
      a change.
- [ ] Preserve lifecycle, observer, diagnostic sanitization, and retry semantics.
- [ ] Document dependency and package changes.
- [ ] Document Jackson 2 to Jackson 3 migration.
- [ ] Document Actuator, AOT/native, configuration, and test-helper changes.
- [ ] Add complete Boot 3 `2.x` and Boot 4 `3.x` Maven examples.
- [ ] Add complete before/after YAML examples.
- [ ] Add metadata deprecations or replacements for changed properties.
- [ ] Run public-surface documentation and compatibility-fixture tests.
- [ ] Run `git diff --check`.

Evidence:

- Pending.

---

## Priority 10 — Boot 4 Consumer and Test-Helper Fixtures

### [ ] 10.1 Prove behavior in assembled applications

- [ ] Add a minimal Boot 4 WebFlux consumer fixture.
- [ ] Exercise a real declarative loopback request.
- [ ] Add inherited generic endpoint and `@ApiRef` fixtures.
- [ ] Add OAuth2 and SigV4 fixtures.
- [ ] Add strict retry and diagnostics endpoint fixtures.
- [ ] Add health and OTel activation/back-off fixtures.
- [ ] Migrate `reactive-http-client-test` to the selected serialization contract.
- [ ] Verify lifecycle ordering and final outbound metadata.
- [ ] Verify retry attempt counts and idempotency-key behavior.
- [ ] Verify redirects, streaming ownership, and multi-value headers.
- [ ] Keep Boot 3 fixtures on the `2.x` maintenance line rather than publishing
      a dual-generation helper jar.
- [ ] Run consumer and helper tests from a clean local repository.
- [ ] Run `git diff --check`.

Evidence:

- Pending.

---

## Priority 11 — Boot 4 Benchmark Baseline

### [ ] 11.1 Re-establish same-stack performance evidence

- [ ] Run raw WebClient, Spring HTTP Interface, and starter on the same Boot 4
      BOM and transport.
- [ ] Re-run default success, JSON, `ResponseEntity`, and small error paths.
- [ ] Re-run Problem Detail, diagnostics, observer, and lifecycle rows affected
      by migration.
- [ ] Keep no-network rows classified separately from loopback rows.
- [ ] Label Boot 3 versus Boot 4 results as stack-migration context.
- [ ] Do not describe cross-stack movement as a pure starter optimization.
- [ ] Record Boot, Framework, Reactor Netty, Netty, Jackson, Micrometer, OTel,
      Java, starter, baseline, and commit versions.
- [ ] Keep thresholds as manual review signals.
- [ ] Promote a clean source-controlled report only if release notes make a
      public performance claim.
- [ ] Run benchmark report/classification tests and `git diff --check`.

Evidence:

- Pending.

---

## Priority 12 — `3.0.0` Go/No-Go and Release Readiness

### [ ] 12.1 Release only with complete migration evidence

- [ ] Document the selected minimum and current Boot 4 test matrix.
- [ ] Verify all mandatory priorities above are complete or explicitly blocked.
- [ ] Run full JVM tests.
- [ ] Run packaged Boot 4 consumer fixtures.
- [ ] Run AOT and native smoke.
- [ ] Run optional-integration presence/absence tests.
- [ ] Run generated configuration docs and Markdown-link validation.
- [ ] Run the `2.x` to `3.x` report-only API diff.
- [ ] Run API compatibility fixtures for the intended `3.x` surface.
- [ ] Verify dependency provenance and published POM contents.
- [ ] Promote benchmark evidence or explicitly defer it based on release claims.
- [ ] Verify starter, test-helper, and OTel versions are all `3.0.0`.
- [ ] Keep `2.x` maintenance instructions visible in release notes.
- [ ] Record a **go** decision only when all mandatory gates pass.
- [ ] Otherwise record a **no-go** decision with blockers and keep `2.x`
      releasable.
- [ ] Run `git diff --check`.
- [ ] Mark `ROADMAP.md` completed only after the go/no-go evidence is recorded.

Evidence:

- Pending.
