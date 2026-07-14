# Reactive HTTP Client - Roadmap V20 Execution Checklist

Companion to [`ROADMAP.md`](ROADMAP.md). Execute priorities in order. Check an
item only after its command, artifact, or review evidence is recorded. V19 spike
results may justify the migration direction, but they do not replace evidence
from the default publishable `3.x` reactor.

---

## Priority 1 - Close `2.14.1` and Protect the Maintenance Lane

### [x] 1.1 Verify the published maintenance release

- [x] Record `2.14.1` in `CHANGELOG.md` with release date `2026-07-13`.
- [x] Move the completed V19 entries from `Unreleased` into `2.14.1`.
- [x] Update the `Unreleased` comparison link to start at `v2.14.1`.
- [x] Add the `2.14.1` comparison link from `v2.14.0`.
- [x] Confirm the remote `v2.14.1` tag points at the intended clean release source.
- [x] Resolve `reactive-http-client-starter:2.14.1` from the public release repository.
- [x] Resolve `reactive-http-client-test:2.14.1` from the public release repository.
- [x] Resolve `reactive-http-client-otel:2.14.1` from the public release repository.
- [x] Record artifact checksums and effective POMs as target-only release evidence.
- [x] Keep the `2.14.1` reactor compatibility baseline on published `2.14.0`
      until Priority 2 changes the project version, preventing self-comparison;
      then move the cross-major baseline to published `2.14.1`.

### [x] 1.2 Preserve the Boot 3.5 maintenance lane

- [x] Identify the branch or tag from which a critical `2.x` fix can be built.
- [x] Verify that lane builds without Boot 4 source or dependency leakage.
- [x] Keep the documented `2.x` scope limited to security and critical correctness fixes.
- [x] Confirm Boot 4 work does not rewrite historical `2.x` release evidence.
- [x] Run the Boot 3.5 maintenance reactor and API compatibility profile.
- [x] Run focused release-documentation tests and `git diff --check`.

Evidence:

- Local and remote `v2.14.1` both resolve to clean release commit
  `f0a1989eb7d19c702c530301798dc34fa4d3819b`, whose POM declares starter
  `2.14.1`, Spring Boot `3.5.16`, and API baseline `2.14.0` and whose changelog
  contains the dated `2.14.1` release section.
- Resolved starter, test-helper, and OTel jars and POMs from an empty Maven local
  repository. Maven `_remote.repositories` markers identify the configured
  public release mirror rather than a reactor install.
- Target-only evidence is under `target/release-evidence/v20-priority1/` with
  downloaded jars/POMs, effective POMs, and `SHA256SUMS`. Jar SHA-256 values are
  starter `97d8550d46fc555fce22cf0bb76b339851bc6fe00f2440ad859d1941f57470b4`,
  test-helper `4f1f5bfedacb3c35ed088e57eb2ba0771f8e11f37a2348a3a2c447edb2259daf`,
  and OTel `32c3089e290310a3c498e6030146b2bf32ec8c3229157159d4b9755795b59be3`.
- Verified the maintenance reconstruction point in a detached worktree created
  from `v2.14.1`. Default active profiles were empty, the managed Boot version
  was `3.5.16`, the dependency tree contained Boot 3.5 artifacts, and the
  starter jar contained no Boot 4 or Jackson 3 adapter classes.
- `mvn -q verify` passed from the detached `v2.14.1` worktree.
- `mvn -q -Papi-compatibility -DskipTests verify` passed there against published
  `2.14.0`, preserving the non-self baseline until the reactor moves to `3.x`.
- `mvn -q -pl reactive-http-client-starter -Dtest=DocumentationReleaseArtifactTest test`
  and `git diff --check` passed in the V20 workspace.

---

## Priority 2 - Establish the Actual `3.x` Reactor

### [x] 2.1 Make Boot 4 the default development line

- [x] Move reactor and module coordinates to the selected `3.0.0` development version.
- [x] Set Spring Boot 4 dependency management as the default build baseline.
- [x] Keep Java 21 as the project minimum unless a separate decision changes it.
- [x] Promote Boot 4 production sources out of spike-only profile selection.
- [x] Remove `maven.deploy.skip` and equivalent non-publishing defaults from the
      intended `3.x` release path.
- [x] Prevent Boot 3 adapters from compiling or packaging in the `3.x` reactor.
- [x] Remove `spring-boot-starter-classic` from all production dependency graphs.
- [x] Keep any `2.x` build support in its maintenance lane, not in the `3.x` jar.

### [x] 2.2 Verify default-build identity

- [x] Run `mvn verify` without `-Pboot4-spike`.
- [x] Verify all generated manifests, POMs, metadata, docs, and support snapshots
      report the same `3.x` version.
- [x] Verify no normal release command depends on the old spike profile.
- [x] Add a build guard that rejects Boot 3 implementation leakage into `3.x` artifacts.
- [x] Run `git diff --check`.

Evidence:

- The root and all published modules now declare `3.0.0`, use Java 21, import
  Spring Boot `4.0.0`, manage Resilience4j `2.4.0`, and compare across the major
  boundary with published `2.14.1`. Maven reports no active default profile.
- Boot 4 WebClient, health, Jackson 3, OTel, test-helper, and benchmark adapters
  now live under normal `src/main` and `src/test` roots. The spike profile,
  build-helper source selection, publishing skips, and Boot 3 implementation
  adapters were removed; the deprecated Jackson 2 migration shim remains an
  optional compatibility dependency for the later public-API migration work.
- The starter dependency tree resolves Boot `4.0.0` focused modules and contains
  no `spring-boot-starter-classic`. CI release smoke now covers Boot `4.0.0` and
  `4.1.0`, while Boot 3.5 maintenance remains reconstructable from `v2.14.1`.
- `DocumentationReleaseArtifactTest` now rejects the old spike profile,
  publishing skips, Boot 3 adapter paths, classic starter dependency, and a
  non-Boot-4 release-smoke matrix.
- `mvn -q -s .mvn/maven-central-settings.xml test` passed, including 726 starter
  tests and the test-helper and OTel suites.
- `mvn -q -s .mvn/maven-central-settings.xml verify` passed without the old
  profile and produced binary, source, and Javadoc jars for all three published
  modules. Their packaged `pom.properties` resources report `3.0.0`.
- `mvn -q -s .mvn/maven-central-settings.xml -Dspring-boot.version=4.1.0
  -Prelease-smoke test` passed. Generated release evidence reports project
  `3.0.0` and API baseline `2.14.1`; README and quick-start snippets also use
  `3.0.0`.
- Starter Javadoc generation passed with the stable codec SPI and Jackson 3 adapter.
- `git diff --check` passed.

---

## Priority 3 - Make Generation-Specific Packaging Release-Ready

### [x] 3.1 Align compiled and attached source artifacts

- [x] Define one production source layout for the Boot 4 generation.
- [x] Remove obsolete profile-selected Boot 3 source roots from the `3.x` build.
- [x] Ensure test compilation uses the same generation selected for main sources.
- [x] Ensure source-jar generation contains only the effective `3.x` sources.
- [x] Ensure Javadoc generation scans only effective `3.x` public sources.
- [x] Fix the V19 attached-Javadoc failure without globally skipping Javadocs.

### [x] 3.2 Audit packaged contents

- [x] Run full `verify` with source and Javadoc attachment enabled.
- [x] Inspect starter, test-helper, and OTel binary jars.
- [x] Inspect source and Javadoc jars.
- [x] Reject duplicate auto-configuration entries and stale Boot 3 classes.
- [x] Verify service descriptors, runtime hints, configuration metadata, and
      auto-configuration imports are present once.
- [x] Add packaging regressions for generation-specific contents.
- [x] Run `git diff --check`.

Evidence:

- Boot 4 production and test code now use the normal module `src/main/java` and
  `src/test/java` roots only. The packaging guard rejects legacy `src/boot3` or
  `src/boot4` directories and Boot 3 WebClient package references in either tree.
- Added `scripts/verify-generation-packaging.sh` after normal CI `clean verify` and
  in the Maven Central publish job after a GPG-enabled clean release `verify` and
  before `deploy`. It compares attached source jars with the effective main Java
  source set, rejects binary classes absent from Maven Compiler's current output, inspects all jars for
  duplicate or stale-generation entries, and requires checked-in resources
  in both binary and source jars alongside generated metadata and runtime hints.
- `mvn -q -s .mvn/maven-central-settings.xml -pl reactive-http-client-starter
  -Dtest=DocumentationReleaseArtifactTest test` passed.
- `mvn -q -s .mvn/maven-central-settings.xml clean verify` passed with source
  and Javadoc attachment enabled for all three published modules.
- `bash scripts/verify-generation-packaging.sh 3.0.0` passed against the clean
  binary, source, and Javadoc artifacts.
- `git diff --check` passed.

---

## Priority 4 - Finalize Jackson 3 and Codec Ownership

### [x] 4.1 Freeze the `3.x` JSON contract

- [x] Make `ReactiveHttpClientJsonCodec` the stable starter serialization boundary.
- [x] Audit every public Jackson 2 type inherited from `2.x`.
- [x] Remove, replace, or explicitly isolate deprecated Jackson 2 compatibility APIs.
- [x] Record each intentional removal or signature change in the migration ledger.
- [x] Verify default request, response, and Problem Detail paths use Jackson 3.
- [x] Verify OAuth2 token responses and sanitized error decoding use configured codecs.
- [x] Verify SigV4 signing bytes exactly match outbound JSON bytes.

### [x] 4.2 Cover codec customization

- [x] Test application-provided Jackson 3 modules, naming strategies, and serializers.
- [x] Test strict body-signing validation with default and custom codecs.
- [x] Test starter and mock-helper serialization parity.
- [x] Test minimal classpaths without Jackson compatibility modules.
- [x] Update configuration metadata, native hints, and codec documentation.
- [x] Run focused codec tests, consumer fixtures, and `git diff --check`.

Evidence:

- Removed the deprecated Jackson 2 codec adapter, mapper constructor, invocation-handler
  constructors, and mock-helper `objectMapper(...)` method. The starter and test-helper
  POMs no longer declare Jackson 2 databind; the migration and API ledgers classify each
  removal as intentional at the `3.0.0` boundary.
- `ReactiveHttpClientJsonCodec` is the documented stable byte-serialization SPI. The
  default Jackson 3 adapter uses Boot's application mapper, and an application codec bean
  backs off the default without requiring configuration metadata or reflection hints.
- Focused tests passed for Jackson 2-hidden startup, Jackson 3 modules, naming strategies,
  custom serializers, default and application codecs, Problem Detail mapping, configured
  OAuth2 error decoding, strict SigV4 validation, exact signing and wire bytes, and mock parity.
- The focused codec selection and the complete starter and test-helper test suites passed.
- `git diff --check` passed.
- Installed the `3.0.0` candidate and ran the independent Boot 4 consumer fixture. Its
  filtered dependency tree contains no `com.fasterxml.jackson.core:jackson-databind`;
  `tools.jackson.core:jackson-databind:3.0.2` remains the runtime mapper.

---

## Priority 5 - Produce Publishable Module POMs

### [x] 5.1 Audit release coordinates and dependency ownership

- [x] Generate effective POMs for the parent, starter, test-helper, OTel, and benchmarks.
- [x] Verify Boot 4 dependencies use focused modules and versionless reactor dependencies.
- [x] Verify Resilience4j, Actuator, Micrometer, OTel, and native integrations retain
      their intended optional scopes.
- [x] Verify licenses, developers, SCM, issue tracking, distribution management,
      signing, and source/Javadoc attachment metadata.
- [x] Verify no generated POM references local paths, spike profiles, or unpublished
      compatibility artifacts.

### [x] 5.2 Stage the complete artifact set

- [x] Produce a clean local staging repository for every publishable module.
- [x] Verify checksums and signatures for staged artifacts.
- [x] Build independent consumers against staged coordinates only.
- [x] Reject accidental resolution from reactor classes or pre-existing local snapshots.
- [x] Record staged dependency trees and effective POMs as target-only evidence.
- [x] Run Central publication validation without releasing until the final go decision.
- [x] Run `git diff --check`.

Evidence:

- Generated target-only effective POMs for the parent, starter, test-helper,
  OTel, and benchmark harness under
  `target/release-evidence/v20-priority5/effective-poms/`. The three published
  child POMs keep Boot 4/Spring dependencies versionless, inherit managed
  reactor coordinates, and retain optional Resilience4j, health, Actuator, and
  Micrometer boundaries; OTel API remains the companion module's required API.
- Removed inherited deploy suppression from the publishable reactor while
  retaining explicit deploy suppression on the benchmark module. Added
  issue-tracker metadata and explicit child project/SCM URLs so effective POMs
  do not invent invalid `.git/<module>` repository paths.
- Central Portal publication now defaults to manual approval
  (`autoPublish=false`). The release workflow explicitly opts into
  `-DautoPublish=true` only at the final publish step and runs
  `scripts/verify-publishable-artifacts.sh` before that step.
- `mvn -q -s .mvn/maven-central-settings.xml -Prelease -DskipTests
  -Dgpg.homedir=/tmp/reactive-http-client-v20-staging-gpg clean verify`
  passed with an ephemeral staging key, proving source/Javadoc attachment and
  signing without using release credentials.
- `GNUPGHOME=/tmp/reactive-http-client-v20-staging-gpg bash
  scripts/verify-publishable-artifacts.sh 3.0.0` passed. It staged the parent
  plus all three binary/source/Javadoc module sets, verified 13 signatures,
  generated SHA-256 checksums, excluded benchmarks, and produced target-only
  staged artifact and dependency-tree evidence.
- The independent Boot 4 fixture passed from an empty target-local Maven
  repository. Its parent, starter, test-helper, and OTel
  `_remote.repositories` markers all identify `v20-stage`, not the reactor
  or the pre-existing local Maven repository.
- `DocumentationReleaseArtifactTest`, shell syntax validation, and
  `git diff --check` passed.

---

## Priority 6 - Revalidate Auto-Configuration, Actuator, AOT, and Native Contracts

### [ ] 6.1 Verify Boot 4 runtime integration

- [ ] Verify auto-configuration ordering after WebClient, Jackson, metrics, and health setup.
- [ ] Verify configuration-properties binding and generated metadata.
- [ ] Verify diagnostics endpoint discovery and sanitized output.
- [ ] Verify health contributor discovery, details, thresholds, and back-off behavior.
- [ ] Verify Micrometer observers and OTel propagation with integrations present.
- [ ] Verify clean back-off when Actuator, Micrometer, or OTel APIs are absent.

### [ ] 6.2 Verify AOT and native execution

- [ ] Re-audit reflection, resource, serialization, and proxy hints for Boot 4 types.
- [ ] Run AOT processing from the default `3.x` reactor.
- [ ] Verify inherited generic and `@ApiRef` clients are discovered from generated metadata.
- [ ] Build the native fixture with the documented GraalVM baseline.
- [ ] Run the native executable against real loopback success, auth, and Problem Detail endpoints.
- [ ] Record dependency and native-image provenance.
- [ ] Run `git diff --check`.

---

## Priority 7 - Revalidate Consumers, Test Helpers, and Optional Integrations

### [ ] 7.1 Test assembled Boot 4 consumers

- [ ] Build the independent Boot 4 consumer against staged artifacts.
- [ ] Cover inherited generic and configured `@ApiRef` endpoints.
- [ ] Cover repeated headers, redirects, bodiless responses, `ResponseEntity`, and streaming.
- [ ] Cover timeout, Problem Detail, lifecycle, observer, diagnostics, and health behavior.
- [ ] Verify consumers do not import reactor source directories or test classes.

### [ ] 7.2 Test helper and optional feature parity

- [ ] Verify mock client naming, URL resolution, final headers, retries, and idempotency.
- [ ] Verify application codec injection and raw-body signing parity.
- [ ] Verify constructor-injected custom exchange loggers in the isolated mock context.
- [ ] Verify lifecycle ordering and terminal subscription-local state.
- [ ] Test Resilience4j operators independently and with registries absent.
- [ ] Test OAuth2, SigV4, OTel, Micrometer, and Actuator presence/absence boundaries.
- [ ] Run starter, helper, OTel, and consumer test suites plus `git diff --check`.

---

## Priority 8 - Freeze and Audit the `3.0.0` Public Surface

### [ ] 8.1 Classify the cross-major API delta

- [ ] Resolve published `2.14.1` artifacts before running the comparison.
- [ ] Run report-only japicmp from `2.14.1` to the staged `3.0.0` candidate.
- [ ] Classify Boot 4, Framework 7, Actuator, and Jackson 3 breaks.
- [ ] Identify and fix accidental removals unrelated to the major migration.
- [ ] Audit documented extension points, nested builder APIs, constructors, enums,
      mutable models, and test-helper methods against compatibility filters.
- [ ] Update the public-surface inventory and migration ledger.

### [ ] 8.2 Preserve baseline correctness

- [ ] Keep normal `2.x` compatibility checks on the published `2.x` baseline.
- [ ] Do not configure a normal `3.x` baseline until `3.0.0` is published.
- [ ] Keep the cross-major comparison report-only and explicitly labeled.
- [ ] Verify root and module-scoped guards reject self-comparison.
- [ ] Run compatibility fixtures and `git diff --check`.

---

## Priority 9 - Close Transport and Protocol Regression Evidence

### [ ] 9.1 Preserve transport ownership contracts

- [ ] Run POST-then-PUT HTTP/1.1 reuse against a real Reactor Netty server.
- [ ] Verify both calls can reuse a pooled connection without parser desynchronization.
- [ ] Reject application-supplied framing and authority headers at startup or request preparation.
- [ ] Verify request body length/chunking is owned by the transport.
- [ ] Re-run redirect, timeout, error-drain, bodiless, and streaming ownership tests.

### [ ] 9.2 Investigate malformed-request warnings with wire evidence

- [ ] Add a deterministic reproducer for `GET /bad-request HTTP/1.0` before changing code.
- [ ] Capture client and server bytes, connection identifiers, and intermediary behavior.
- [ ] Distinguish starter request construction from proxy, mesh, ingress, and TCP corruption.
- [ ] Add a failing regression test before implementing any starter fix.
- [ ] Record an external-cause diagnosis when starter behavior cannot reproduce the warning.
- [ ] Run transport-focused tests and `git diff --check`.

---

## Priority 10 - Establish a Boot 4 Benchmark Baseline

### [ ] 10.1 Rebuild the repeatable harness on final `3.x` artifacts

- [ ] Run quick smoke to validate benchmark discovery and report generation.
- [ ] Run same-stack loopback baselines for raw WebClient, Spring HTTP Interface,
      and the starter only where each performs equivalent work.
- [ ] Run no-network invocation and diagnostics audits under separate classifications.
- [ ] Enable optional features one at a time and record the matched comparison policy.
- [ ] Record exact starter, Boot, Framework, Reactor Netty, Netty, Jackson,
      Micrometer, OTel, JVM, OS, CPU, and commit metadata.
- [ ] Verify all benchmark method prefixes map to an explicit report classification.

### [ ] 10.2 Decide whether to publish performance evidence

- [ ] Review throughput, average time, percentiles, and allocation movement manually.
- [ ] Treat Boot 3 versus Boot 4 movement as migration context, not abstraction overhead.
- [ ] If release notes make numerical claims, rerun from a clean immutable commit.
- [ ] Promote a versioned source-controlled report only for reproducible claims.
- [ ] Otherwise record explicit report deferral and keep numerical claims out of public notes.
- [ ] Run benchmark report tests, documentation guards, and `git diff --check`.

---

## Priority 11 - Publish the `3.0.0` Migration and Operations Guide

### [ ] 11.1 Complete the adoption path

- [ ] Document Maven coordinates and the Boot 4/JDK baseline.
- [ ] Document package and dependency changes from focused Boot 4 modules.
- [ ] Document Jackson 2 to Jackson 3 API and codec migration.
- [ ] Document Actuator diagnostics/health and native-image changes.
- [ ] Document test-helper and custom exchange-logger setup.
- [ ] Include complete before/after configuration and code examples.
- [ ] Include a minimal independent Boot 4 consumer that compiles against staged artifacts.
- [ ] Explain when applications should remain on the `2.14.1` maintenance lane.

### [ ] 11.2 Align all public release documentation

- [ ] Update README, quick start, annotation, auth, resilience, observability,
      support-bundle, benchmark, and compatibility guides.
- [ ] Remove stale Boot 3 assumptions from `3.x` instructions without rewriting
      historical release reports.
- [ ] Ensure release notes distinguish migration evidence from performance claims.
- [ ] Generate configuration reference and release-readiness artifacts.
- [ ] Run code-snippet consumers, metadata tests, Markdown-link checks, and `git diff --check`.

---

## Priority 12 - `3.0.0` Go/No-Go and Release Readiness

### [ ] 12.1 Assemble clean candidate evidence

- [ ] Select and record the minimum and current supported Boot 4 matrix.
- [ ] Verify Priorities 1-11 are complete or have explicit release blockers.
- [ ] Run full default-reactor JVM tests from a clean candidate commit.
- [ ] Run source/Javadoc packaging and inspect all artifacts.
- [ ] Run staged independent consumers and test-helper fixtures.
- [ ] Run optional-integration presence/absence suites.
- [ ] Run AOT processing and the native executable smoke.
- [ ] Run generated metadata, documentation, and Markdown-link validation.
- [ ] Run the report-only `2.14.1` to `3.0.0` API comparison and compatibility fixtures.
- [ ] Resolve every staged artifact from an isolated repository.
- [ ] Promote or explicitly defer benchmark evidence based on public claims.
- [ ] Generate one release-readiness snapshot with exact commands and provenance.

### [ ] 12.2 Record the release decision

- [ ] Confirm no mandatory blocker or unclassified compatibility break remains.
- [ ] Confirm the candidate commit is clean, immutable, and matches every generated artifact.
- [ ] Confirm the `2.x` maintenance path remains available.
- [ ] For **go**, publish `3.0.0`, verify public artifact resolution, tag the exact
      source, date the changelog, and establish the new `3.x` compatibility baseline.
- [ ] For **no-go**, publish nothing, record each blocker and its reproduction,
      and keep `2.x` maintenance unaffected.
- [ ] Update `ROADMAP.md` status only after the decision evidence exists.
- [ ] Run final release-document tests and `git diff --check`.

## Completion Rule

V20 is complete only when a publishable default Boot 4 reactor produces either
a reproducible `3.0.0` release or a new evidence-backed no-go decision. Passing
the old non-publishing V19 spike does not satisfy this checklist.
