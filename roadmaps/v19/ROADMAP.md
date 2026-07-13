# Reactive HTTP Client — Roadmap V19

> **Status:** completed with a `3.0.0` no-go decision on 2026-07-13.
> V19 prepared a Spring Boot 4 generation without silently dropping the
> supported Spring Boot 3.5 line or mixing a major dependency migration with
> unrelated feature work.

## Recommendation

V19 should **prepare and validate a Spring Boot 4-based `3.0.0` line**, but it
should not begin by changing the root `spring-boot.version` and repairing
whatever breaks. Spring's migration guide strongly discourages supporting Boot
3 and Boot 4 in one starter artifact because Boot 4 reorganizes modules and
packages. The project should instead use two explicit release lines:

| Release line | Spring Boot line | Purpose |
|---|---|---|
| Starter `2.x` | Spring Boot `3.5.x` | Maintenance, security updates, and critical fixes |
| Starter `3.x` | Spring Boot `4.x` | New major line after migration evidence passes |

Keep the same Maven coordinates and use the starter major version to express
the compatibility boundary. Do not publish parallel Boot 3/Boot 4 variants
under classifiers, and do not make one jar detect Boot internals dynamically.

Spring Boot 4 currently requires Java 17 or later, Spring Framework 7, and a
Jakarta EE 11 baseline. This project should keep Java 21 as its own minimum.
Boot 4 also prefers Jackson 3, reorganizes Boot modules and packages, and uses a
newer native-image baseline. Those changes affect this starter directly because
it exposes Jackson 2 types publicly and integrates Boot auto-configuration,
Actuator, WebClient customization, configuration metadata, and AOT hints.

Official references:

- [Spring Boot 4 migration guide](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide)
- [Spring Boot system requirements](https://docs.spring.io/spring-boot/system-requirements.html)
- [Spring Framework 7 release notes](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-7.0-Release-Notes)

## Goals

1. Keep the Boot 3.5-based `2.x` line supportable while Boot 4 work proceeds.
2. Establish whether the starter can migrate cleanly to Boot 4 without hidden
   Jackson, Actuator, AOT, transport, or optional-dependency regressions.
3. Produce a reviewable `3.0.0` migration contract before changing public APIs.
4. Add consumer evidence that exercises real Boot 4 applications, not only
   unit tests compiled against a BOM.
5. Make the release decision evidence-driven: release `3.0.0` only when all
   mandatory gates pass; otherwise document blockers and continue `2.x`.

## Non-Goals

- Do not support Boot 3 and Boot 4 from one starter jar.
- Do not raise the project baseline above Java 21 without a separate decision.
- Do not use `spring-boot-starter-classic` in the final Boot 4 artifact; it may
  be used only as a temporary migration diagnostic.
- Do not retain Jackson 2 indefinitely merely to avoid a major API change.
- Do not add unrelated HTTP features while transport and serialization
  contracts are being migrated.
- Do not publish Boot 4 performance claims using Boot 3 benchmark reports.
- Do not declare the observed `/bad-request` decoder warning fixed without a
  wire-level reproducer and regression test.

---

## 1. Release-Line and Maintenance Decision

### 1.1 Close the `2.14.0` release and establish both release lanes

**Why:** `2.14.0` is published, but release closure still requires a dated
changelog, tag confirmation, artifact resolution, and an explicit maintenance
lane. Boot 4 work must not make the last Boot 3 release impossible to reproduce
or patch.

**What:**

- Record `2.14.0` as released on 2026-07-10 and update changelog comparison
  links.
- Confirm the remote `v2.14.0` tag and release point at the intended source.
- Resolve starter, test-helper, and OTel `2.14.0` artifacts from public Maven
  Central and the configured build mirror before moving compatibility baselines.
- Keep API compatibility for `2.x` against the last published `2.x` release.
- Define the `2.x` maintenance scope as security updates and critical fixes.
- Document how long Boot 3.5 maintenance continues without inventing an EOL
  date before upstream and maintainer capacity are reviewed.
- Keep Boot 4 work off the `2.x` release branch.

**Acceptance:**

- [x] `2.14.0` is recorded as released on 2026-07-10 with updated changelog
      comparison links.
- [x] Starter, test-helper, and OTel `2.14.0` artifacts resolve from the release
      environment.
- [x] The remote `v2.14.0` tag identifies the intended release source.
- [x] The `2.x` maintenance policy is public and unambiguous.
- [x] Boot 4 changes do not rewrite historical `2.x` release evidence.
- [x] Security or critical transport fixes can still be released on `2.x`.

---

## 2. Latest Boot 3.5 Migration Bridge

### 2.1 Validate the latest `3.5.x` patch before Boot 4

**Why:** Spring recommends moving to the latest Boot 3.5 patch and removing
deprecated API usage before migrating to Boot 4. V18 reviewed Boot `3.5.16` but
kept the project pinned to `3.5.0`.

**What:**

- Re-resolve the latest available `3.5.x` patch when execution starts; do not
  assume the V18-reviewed candidate is still latest.
- Run full tests, release smoke, AOT smoke, configuration metadata, native
  hints, optional integration tests, compatibility checks, and benchmark
  metadata checks with that patch.
- Remove use of Boot/Spring APIs deprecated in the latest 3.5 line where doing
  so is source-compatible for `2.x`.
- Release the patch movement separately on `2.x`, or record why it is deferred.

**Acceptance:**

- [ ] The Boot 3.5 bridge uses a currently published patch release.
- [ ] No Boot 3.5 deprecation used by production code is ignored silently.
- [ ] Managed WebFlux, Reactor Netty, Micrometer, OTel, and test versions are
      recorded in release evidence.
- [ ] The bridge is not combined with Boot 4 public API changes.

---

## 3. Boot 4 Build Spike and Version Matrix

### 3.1 Compile before changing the default baseline

**Why:** A BOM override is not enough evidence. Boot 4 modularization can leave
code compiling while auto-configuration, Actuator, tests, or native images fail
at runtime.

**What:**

- Create an isolated Boot 4 migration profile, branch, or temporary reactor
  property; keep the default `2.x` build on Boot 3.5 until the go/no-go gate.
- Test the chosen minimum Boot 4 maintenance line and the current stable Boot 4
  line separately when they differ.
- Resolve all artifacts from a repository that actually contains the selected
  Boot 4 release; distinguish repository-mirror failures from source failures.
- Record exact Spring Framework, WebFlux, Reactor Netty, Netty, Micrometer,
  Jackson, OTel, and test dependency versions.
- Add a CI matrix only after the migration profile is deterministic.

**Acceptance:**

- [ ] Boot 4 dependency resolution is reproducible from a clean environment.
- [ ] Compilation failures are classified by module/package, Jackson, test
      infrastructure, or optional integration ownership.
- [ ] The matrix distinguishes minimum-supported and current Boot 4 versions.
- [ ] No Boot 4 artifact is published from an experimental profile.

---

## 4. Boot Module and Auto-Configuration Migration

### 4.1 Replace Boot 3 module assumptions explicitly

**Why:** Boot 4 splits previously broad modules into focused modules and moves
packages. This starter currently depends directly on auto-configuration,
Actuator health/endpoints, configuration processing, and
`WebClientCustomizer`.

**What:**

- Inventory every `org.springframework.boot.*` production import and map it to
  its Boot 4 module and package.
- Replace broad or obsolete dependencies with the minimum focused Boot 4
  modules or starters required by a third-party starter.
- Rebuild auto-configuration imports and conditional behavior without relying
  on the classic compatibility starter.
- Verify `WebClient.Builder`, `WebClientCustomizer`, configuration properties,
  metadata generation, Actuator health, and the `rhttpclients` endpoint.
- Keep Actuator optional and preserve back-off behavior when modules are absent.

**Acceptance:**

- [ ] Production code has no unresolved Boot 3 package assumptions.
- [ ] Auto-configuration loads from a packaged Boot 4 application.
- [ ] Optional Actuator absence does not prevent application startup.
- [ ] The final dependency graph does not include `starter-classic`.

---

## 5. Jackson 3 and Codec Ownership

### 5.1 Make serialization a deliberate `3.0.0` contract

**Why:** Boot 4 prefers Jackson 3 under `tools.jackson`, while the current
starter publicly exposes Jackson 2 `ObjectMapper` and uses it for auth body
serialization, Problem Detail mapping, diagnostics, and test helpers. Keeping
Jackson 2 internally can produce signed bytes that differ from WebClient's
Jackson 3 codec output.

**What:**

- Inventory every public and internal Jackson 2 type in starter and test-helper
  APIs.
- Decide whether `3.0.0` exposes Jackson 3 types or introduces a narrow
  starter-owned serialization interface where applications need customization.
- Ensure auth signing and outbound WebClient encoding use the same serialized
  bytes and configured codec behavior.
- Migrate Problem Detail decoding, OAuth2 sanitized error decoding, contract
  snapshots, and mock helper body assertions.
- Add migration examples for applications with custom modules, naming rules,
  Java time support, Kotlin, and unknown-property policies.
- Keep any Jackson 2 compatibility module temporary and explicitly deprecated.

**Acceptance:**

- [ ] Default Boot 4 applications do not require users to define a Jackson 2
      mapper.
- [ ] SigV4 hashes the exact bytes written by the outbound codec.
- [ ] Problem Detail and OAuth2 error decoding honor application codec setup.
- [ ] Public Jackson-related breaking changes are listed in the `3.0.0`
      migration guide.

---

## 6. Spring Framework 7 and Transport Correctness

### 6.1 Revalidate every response-ownership and connection-reuse contract

**Why:** Spring Framework 7 and the Boot 4 transport stack update WebFlux,
Reactor Netty, and Netty together. Existing streaming, bodiless, redirect,
timeout, and error-drain guarantees must be proven again.

**What:**

- Re-run JSON, `ResponseEntity`, streaming `Flux<DataBuffer>`, bodiless,
  redirect, timeout-after-headers, truncated error-body, and cancellation tests
  against a real Reactor Netty server.
- Add a wire-level HTTP/1.1 regression fixture for sequential POST then PUT on
  one persistent connection.
- Assert that starter-owned request construction does not forward or preserve
  unsafe `Content-Length`, `Transfer-Encoding`, `Connection`, `Expect`, or
  `Host` combinations without an explicit supported contract.
- Capture the full decoder cause for malformed requests and prove that Netty's
  synthetic `GET /bad-request HTTP/1.0` placeholder is never treated as the
  application method/path.
- Cover direct pod traffic and one representative proxy/sidecar path where the
  test environment supports it.
- Revalidate HTTP/1.1, TLS HTTP/2, and clear-text H2C negotiation separately.

**Acceptance:**

- [ ] Sequential POST/PUT reuse passes without parser warnings or leaked bytes.
- [ ] A deliberately incorrect body length reproduces a framing failure and is
      clearly distinguished from normal starter traffic.
- [ ] Streaming and bodiless responses return or close pooled connections
      according to their documented ownership contract.
- [ ] Protocol mismatch failures identify HTTP/1.1, H2, H2C, TLS, and proxy
      ownership accurately.

---

## 7. Optional Integrations and Resilience

### 7.1 Preserve absence and back-off behavior

**Why:** Boot 4 changes module boundaries. Optional integrations must not become
required merely because package locations or starter POMs changed.

**What:**

- Review Resilience4j compatibility with Spring Framework 7, Reactor, and the
  selected Boot 4 line.
- Keep retry, circuit breaker, rate limiter, bulkhead, and metrics adapters
  optional with a no-op fallback when registries or adapters are absent.
- Revalidate Micrometer observer/health behavior and OTel auto-configuration,
  propagation, semantic attributes, and classpath back-off.
- Verify OAuth2 client credentials and AWS SigV4 without relying on Boot 3
  codec or auto-configuration behavior.
- Keep diagnostics endpoint and health details opt-in and sanitized.

**Acceptance:**

- [ ] A minimal Boot 4 application starts without Resilience4j, Actuator,
      Micrometer, or OTel.
- [ ] Adding each optional integration independently activates only its owned
      behavior.
- [ ] Missing registries/operators do not produce false active-policy
      diagnostics.
- [ ] No optional dependency becomes transitive by accident.

---

## 8. AOT and Native Image Baseline

### 8.1 Rebuild native evidence for Boot 4

**Why:** Boot 4's migration guidance requires a newer GraalVM/native-image
baseline. Existing Boot 3 runtime hints are useful inputs, not proof.

**What:**

- Update the native test environment to the Boot 4-supported GraalVM baseline.
- Re-audit reflection, resource, proxy, inherited-interface, configuration
  binding, diagnostics endpoint, and Maven version resource hints.
- Build and run a native smoke application with a real declarative client.
- Exercise inherited generic endpoints, Problem Detail decoding, auth, and one
  optional observability integration in native mode.
- Keep native commands and tool versions in generated release evidence.

**Acceptance:**

- [ ] Boot 4 AOT processing succeeds for starter and OTel auto-configuration.
- [ ] The native smoke application performs a real loopback request.
- [ ] Configuration metadata and runtime binding agree in the native image.
- [ ] Native evidence records Java, GraalVM, Boot, Framework, and starter
      versions.

---

## 9. Public API and `3.0.0` Migration Guide

### 9.1 Make breaking changes reviewable

**Why:** Boot 4 and Jackson 3 justify a starter major version, but they do not
justify unrelated API churn.

**What:**

- Freeze the `2.x` public surface map and produce a report-only API diff from
  the latest published `2.x` artifact to the `3.0.0` candidate.
- Categorize every incompatibility as required by Boot 4/Jackson 3, intentionally
  redesigned, or accidental.
- Preserve annotations, exception categories, lifecycle/observer semantics,
  diagnostics sanitization, and test-helper workflows unless migration requires
  a documented break.
- Write a Boot 3.5 `2.x` to Boot 4 `3.x` adoption guide with dependency,
  package, configuration, Jackson, Actuator, native, and test-helper changes.
- Do not use japicmp's normal same-major pass/fail policy to hide major-line
  differences; keep the report as explicit release evidence.

**Acceptance:**

- [ ] Every binary/source incompatibility has a migration instruction.
- [ ] Unrelated public API changes are removed from the migration diff.
- [ ] Existing configuration properties are retained or have documented
      replacements and metadata deprecations.
- [ ] The guide includes complete before/after Maven and YAML examples.

---

## 10. Boot 4 Consumer and Test-Helper Fixtures

### 10.1 Test applications, not only library classes

**Why:** Starter modularization failures often appear only when a consumer
application assembles auto-configuration and optional dependencies.

**What:**

- Add a minimal Boot 4 WebFlux consumer fixture using one declarative client.
- Add fixtures for inherited generic endpoints, `@ApiRef`, OAuth2, SigV4,
  strict retry validation, diagnostics endpoint, health details, and OTel.
- Migrate `reactive-http-client-test` to the Boot 4/Jackson 3 contract.
- Verify lifecycle ordering, final outbound metadata, auth body bytes, retry
  attempt counts, redirects, streaming ownership, and multi-value headers.
- Keep a Boot 3.5 maintenance fixture on the `2.x` branch rather than forcing
  both generations into one published helper jar.

**Acceptance:**

- [ ] The Boot 4 consumer starts with only documented dependencies.
- [ ] Test-helper behavior matches production serialization and filters.
- [ ] Optional integration fixtures prove both activation and absence paths.
- [ ] Shared inherited endpoints resolve concrete request/response types.

---

## 11. Boot 4 Benchmark Baseline

### 11.1 Re-establish evidence on an equivalent stack

**Why:** Boot 3 benchmark numbers cannot establish Boot 4 overhead. Framework,
transport, codecs, and JVM dependencies all move.

**What:**

- Run raw WebClient, Spring HTTP Interface, and starter comparisons on the same
  Boot 4 BOM and transport.
- Re-run JSON, `ResponseEntity`, error mapping, diagnostics, lifecycle, and
  observer rows affected by migration.
- Keep Boot 3 versus Boot 4 results labeled as stack-migration context, not as a
  pure starter optimization comparison.
- Record dependency-management source and exact Framework/Reactor Netty/Netty,
  Jackson, Micrometer, OTel, Java, and commit versions.
- Keep thresholds as manual review signals.

**Acceptance:**

- [ ] Every comparison row uses equivalent work and the same Boot 4 transport.
- [ ] No Boot 3 promoted report is cited as Boot 4 release evidence.
- [ ] A clean source-controlled report exists before public performance claims.
- [ ] Regressions are investigated by scenario rather than hidden by aggregate
      averages.

---

## 12. `3.0.0` Go/No-Go and Release Readiness

### 12.1 Release only when the migration contract is complete

**Why:** A compiling Boot 4 build is not a releasable starter.

**What:**

- Require clean full tests, Boot 4 consumer fixtures, AOT/native smoke,
  optional-integration tests, API migration diff, generated docs, Markdown
  links, benchmark decision, and dependency provenance.
- Verify all published starter, test-helper, and OTel artifacts use `3.0.0` and
  the same Boot 4 baseline.
- Keep `2.x` maintenance instructions visible in the `3.0.0` release notes.
- If any mandatory gate fails, publish blocker evidence and continue the `2.x`
  line instead of shipping a partial Boot 4 migration.

**Acceptance:**

- [ ] The selected Boot 4 minimum and current-test matrix are documented.
- [ ] Full JVM, AOT, native, optional-integration, consumer, and release tests
      pass.
- [ ] The `2.x` to `3.x` API diff and migration guide are source-controlled.
- [ ] Benchmark evidence is promoted or explicitly deferred based on release
      claims.
- [ ] `3.0.0` is published only after all mandatory gates pass.

---

## V19 Completion Definition

V19 is complete when one of these outcomes is recorded:

1. **Go:** a Boot 4-based `3.0.0` candidate satisfies every mandatory gate and
   is ready for release; or
2. **No-go:** Boot 4 blockers, affected contracts, and next actions are
   source-controlled, while the Boot 3.5 `2.x` maintenance line remains
   releasable.

A root POM version change alone does not complete V19.

## Recorded Outcome

V19 completed with the **no-go** outcome. Boot 4 JVM, external consumer,
AOT/native, optional-integration, API-report, and compatibility-fixture gates
pass. Publication remains blocked because the reactor and all published modules
still use the Boot 3.5 `2.14.1` maintenance identity, the Boot 4 profile disables
publishing, and Boot 4 attached Javadocs still scan generation-incompatible Boot
3 sources. See the source-controlled
[V19 release decision](../../docs/29-v19-release-decision.md) for evidence and
next actions. The Boot 3.5 `2.x` line remains releasable.
