# Native Image and Release Compatibility

Sections without a version label describe the current `3.4.0-SNAPSHOT`
development line. Sections labeled V18, V19, or V20 preserve release-era
evidence and are not current commands. Use the command in the first applicable
current section; historical sections remain for provenance only.

## Supported Spring Boot baseline

The `3.x` line requires Java 21 and Spring Boot `4.0.0` or later. Default
dependency management uses Spring Boot `4.0.0`; CI also exercises the current
`4.1.0` line. The published `2.x` line remains the separate Spring Boot 3.5
maintenance lane.

## Dependency baseline readiness

Current release-line dependency inputs:

| Area | Source | Current policy |
|---|---|---|
| Java runtime/compiler | Root `java.version`, `maven.compiler.source`, and `maven.compiler.target` | `21` is the supported baseline. Raising it requires a minor release, release-smoke review, and native-smoke review. |
| Spring Boot baseline | Root `spring-boot.version` | `4.0.0` is the minimum and default managed baseline. The current `4.1.0` line is an additional compatibility row. |
| Spring WebFlux, Reactor Netty, Micrometer, and OpenTelemetry API | Spring Boot dependency management | Keep module POMs versionless for these artifacts. Review exact resolved versions from the effective POM or release evidence when `spring-boot.version` changes. |
| Resilience4j | Root `resilience4j.version` plus `resilience4j-bom` | `2.4.0` is the optional Boot 4 resilience baseline. |
| Test dependencies | Spring Boot dependency management plus explicit test-only pins | Test-only updates are compatibility-neutral when they do not change published test-helper APIs or release fixtures. Explicit pins such as the TLS fixture dependency stay local to tests. |
| Benchmark harness | Root `jmh.version` and benchmark Maven profiles | Benchmark-only updates do not change runtime compatibility, but release-quality reports must record the project version, starter version, Spring Boot baseline, resolved WebFlux/Reactor Netty versions, dependency-management source, and benchmark commit. |

Compatibility-neutral dependency maintenance includes Spring Boot `4.x` patch
updates, managed Spring WebFlux/Reactor Netty/Micrometer/OpenTelemetry patch
movement inherited from that Boot line, Resilience4j patch-compatible updates,
test-only dependency updates, and benchmark harness updates that keep report
metadata intact.

### V23 resolved supported matrix

The `2026-07-26` review retains Spring Boot `4.0.0` as the minimum/default row
and `4.1.0` as the forward-compatibility row. Both rows were resolved from
separate fresh Maven Central repositories and exercised under a complete Java
21 JDK.

| Managed dependency | Boot `4.0.0` minimum | Boot `4.1.0` forward |
|---|---:|---:|
| Spring Framework / WebFlux | `7.0.1` | `7.0.8` |
| Reactor Netty HTTP | `1.3.0` | `1.3.6` |
| Netty HTTP codec | `4.2.7.Final` | `4.2.15.Final` |
| Jackson Databind | `3.0.2` | `3.1.4` |
| Micrometer Core | `1.16.0` | `1.17.0` |
| OpenTelemetry API | `1.55.0` | `1.62.0` |
| JUnit Jupiter API | `6.0.1` | `6.0.3` |
| Mockito Core | `5.20.0` | `5.23.0` |
| Resilience4j | `2.4.0` | `2.4.0` |

The minimum does not move: Boot 4.0, Framework 7, Jackson 3, and Java 21 remain
the published `3.x` generation contract. Boot 4.1 is retained as a
forward-compatibility row because its managed Framework, transport, JSON,
observability, and test-library movement exercises the starter without forcing
consumers to upgrade. No configuration or public API migration is required by
this review.

### V24 supported-matrix revalidation

V24 keeps the same minimum and forward rows, but reruns the complete reactor,
assembled consumer, optional-integration back-off, and strict API comparison
after its return-grammar, composition, proxy, HTTP/2, and diagnostics changes.
The V23 table above remains immutable resolved-version evidence; each V24 run
records its own fresh resolved dependency provenance.

Run the full review with an active complete JDK 21:

```bash
scripts/verify-supported-matrix.sh
```

For each row the verifier uses a distinct temporary Maven repository, runs the
full reactor, Spring AOT, optional-integration presence/back-off, transport
tests, and the assembled external consumer. Each row then runs strict API
compatibility against published `3.3.0` with that row's Boot-managed classpath
and a separate repository through the shared Central provenance guard. An exit
trap copies completed and partial row reports to the upload path before
preserving any failure status. Effective POMs, dependency trees, resolved
versions, explicit optional-integration back-off results, Surefire and japicmp
reports, commands, and provenance are copied to
`target/release-evidence/v24-priority9/matrix/`. The manual
`Supported Dependency Matrix` workflow runs and uploads the same contract.

Requires a minor release: raising the Java baseline, adding a new Spring Boot
minor line, changing optional integrations into required runtime dependencies,
changing the Resilience4j baseline in a way that affects operator behavior or
public diagnostics, or upgrading Reactor Netty/Micrometer/OpenTelemetry outside
the managed Spring Boot baseline for runtime behavior. Do not mix a baseline
upgrade with unrelated feature work; make the baseline change visible in release
evidence, generated configuration metadata, and benchmark metadata in the same
change.

### V20 default Spring Boot 4 reactor

The default reactor now declares `3.4.0-SNAPSHOT`, imports Spring Boot `4.0.0`,
and uses published `3.3.0` as its strict compatibility baseline. Boot 4
WebClient, health, Jackson 3, OTel, test-helper, and benchmark adapters live in
normal source roots. The old `boot4-spike` profile, compiler exclusions,
`maven.deploy.skip`, and `skipPublishing` controls are absent.

Run the production generation without a profile:

```bash
mvn -s .mvn/maven-central-settings.xml verify
mvn -s .mvn/maven-central-settings.xml \
  -Dspring-boot.version=4.1.0 -Prelease-smoke test
```

The Central-only settings file is optional when the configured Maven mirror
already contains Boot 4. It changes repository resolution only; it does not
select source sets or alter publishing.

### Publishable module staging

The published parent manages the starter, test-helper, and OTel coordinates, so
module POM dependencies on another reactor artifact remain versionless. The parent
also owns the license, developer, repository, issue-tracker, source/Javadoc, GPG,
and Central Portal metadata. Central publishing defaults to manual approval; the
release workflow is the final go decision and explicitly sets `autoPublish=true`.
The benchmark module remains outside the default reactor and keeps deploy disabled.

After a signed release-profile build, stage and consume the exact artifacts without
uploading them:

```bash
mvn -s .mvn/maven-central-settings.xml clean -Prelease -DskipTests verify
bash scripts/verify-generation-packaging.sh
bash scripts/verify-publishable-artifacts.sh
```

The staging guard writes only target-local evidence under
`target/release-evidence/v20-priority5/`. It generates effective POMs for the
parent, three publishable modules, and benchmarks; deploys the parent, binary,
source, and Javadoc artifacts to a clean file repository; verifies every GPG
signature; writes SHA-256 checksums; and runs the independent Boot 4 consumer
with an empty Maven local repository. The `_remote.repositories` markers must
identify `v20-stage`, proving the consumer did not use reactor classes or a
pre-existing local artifact.

A credentialed `mvn -Prelease deploy` leaves a validated deployment awaiting
manual approval in Central. Only the final publish workflow adds
`-DautoPublish=true`.

The immutable Boot 3.5 maintenance reconstruction point remains `v2.14.1`.
Create a dedicated maintenance branch from that tag for security or critical
correctness fixes; do not compile Boot 3 adapters into the `3.x` artifacts.

### Boot 3.5 maintenance-lane rehearsal

`v2.14.1` at commit `f0a1989eb7d19c702c530301798dc34fa4d3819b`
is the latest immutable `2.x` release tag. The maintenance lane has no
permanently advancing branch: when a security or critical transport/correctness
fix is approved, create `maintenance/2.14.x` from that tag, make only the
required fix and regression test, and delete the branch after publishing the
patch. This policy does not declare an end-of-life date for `2.x`.

Reconstruct the release and its `2.14.0` API baseline from clean, isolated
state with:

```bash
scripts/verify-maintenance-lane-fixtures.sh
scripts/verify-maintenance-lane.sh v2.14.1 2.14.0
```

The verifier checks the local tag against `origin`, creates a detached
worktree, resolves all dependencies and the published API predecessor through
Maven Central into a fresh target-local repository, and runs the historical
release plus strict compatibility profiles. It requires Boot 3.5 and Jackson 2
in the dependency tree, rejects Boot 4/Jackson 3 implementation entries, and
requires binary, source, and Javadoc artifacts for all three modules. Release
effective POMs must retain GPG signing and Central Portal wiring. Actual signing
and deployment remain credentialed manual release operations and are not
performed by this rehearsal.

Evidence is written only below
`target/release-evidence/maintenance-2x-2.14.1/`: exact commands, local and
remote tag commits, effective POMs, dependency trees, artifact entry lists,
maintenance artifact hashes, and Maven Central markers plus hashes for the
published `2.14.0` parent, starter, test-helper, and OTel baseline. The manual
`Boot 3.5 Maintenance Lane` workflow runs the same contract from a checkout
that includes tags.

Apply a shared fix to `maintenance/2.14.x` first so the vulnerable published
lane receives the smallest reviewable patch. After that patch is validated,
forward-port the fix and equivalent regression test to `3.x`; adapt APIs only
where Boot 4, Framework 7, or Jackson 3 ownership differs. Never merge the
whole maintenance branch into `3.x`, and never backport unrelated `3.x`
features. Normal `3.x` builds continue to run
`scripts/verify-generation-packaging.sh`, which rejects Boot 3 source roots,
classes, and attached artifacts.

### V18 dependency patch review (historical)

The V18 review on `2026-07-10` compared the pinned Spring Boot `3.5.0` BOM with
the separately evaluated `3.5.16` patch candidate:

| Managed dependency | Pinned `3.5.0` BOM | Reviewed `3.5.16` candidate |
|---|---:|---:|
| Spring WebFlux | `6.2.7` | `6.2.19` |
| Reactor Netty HTTP | `1.2.6` | `1.2.18` |
| Micrometer Core | `1.15.0` | `1.15.12` |
| OpenTelemetry API | `1.49.0` | `1.49.0` |

The candidate is **deferred**. This review does not change the Java 21 or Spring
Boot 3.5.x support contract and does not pin any managed module dependency.
Evaluate the candidate in a separate dependency-baseline change with release
smoke, AOT smoke, optional-integration, generated-documentation, compatibility,
and benchmark metadata checks. Resilience4j remains independently managed by
its `2.2.0` BOM and optional in the starter; the no-registry/no-operator fallback
continues to be part of the supported runtime contract.

### V19 Spring Boot 3.5 migration bridge

Maven Central metadata queried on 2026-07-10 confirmed `3.5.16` as the latest
published Spring Boot `3.5.x` patch, matching the candidate reviewed in V18. V19
adopts that patch as the default dependency-management version for the `2.14.1`
maintenance reactor while retaining `3.5.0` as the minimum release-smoke row.

| Managed dependency | Boot `3.5.16` resolved version |
|---|---:|
| Spring Framework / WebFlux / Test | `6.2.19` |
| Reactor Netty HTTP | `1.2.18` |
| Netty HTTP codec | `4.1.135.Final` |
| Micrometer Core | `1.15.12` |
| OpenTelemetry API | `1.49.0` |
| Jackson Databind | `2.21.4` |
| JUnit Jupiter | `5.12.2` |
| Mockito Core | `5.17.0` |

The full reactor, release-smoke profile, focused AOT/configuration metadata,
optional-integration paths, and API compatibility checks pass with `3.5.16`.
Production deprecation compilation found one Reactor Netty TLS overload, which
was replaced by the non-deprecated built-context overload. Calls to the starter's
own deprecated `resilience.timeout-ms` accessor remain intentionally to preserve
the documented `2.x` compatibility fallback. The native consumer fixture now
uses `3.5.16`. Its AOT processing, GraalVM Community Java 21 native compilation,
and generated executable all ran successfully in an isolated container. The
configured internal Maven mirror lacked the optional GraalVM reachability-metadata
repository ZIP, so that isolated build resolved the ZIP directly from Maven
Central; the scheduled workflow remains the clean-environment native gate.

### V19 isolated Spring Boot 4 build spike

The `boot4-spike` Maven profile evaluates Boot 4 without changing the normal
Boot 3.5 reactor. It defaults to Boot `4.0.0`, the minimum major line under
evaluation, and accepts `-Dspring-boot.version=4.1.0` for the current stable
line verified on 2026-07-11. The profile sets both `maven.deploy.skip` and the
Central Publishing plugin's `skipPublishing` property to `true`; artifacts
produced by this profile are experimental and must not be published.

Use the credential-free spike settings file when an application-level Maven
mirror does not contain Boot 4 artifacts:

```bash
mvn -s .mvn/boot4-spike-settings.xml -Pboot4-spike \
  -pl reactive-http-client-starter -DskipTests clean compile
mvn -s .mvn/boot4-spike-settings.xml -Pboot4-spike \
  -Dspring-boot.version=4.1.0 -pl reactive-http-client-starter \
  -DskipTests clean compile
```

The settings file mirrors only this explicit spike invocation to Maven Central.
The configured internal mirror failed while resolving the Boot `4.0.0` BOM;
the same effective-POM and compile commands resolved from Maven Central, so the
remaining failures below are source/module compatibility failures rather than
repository failures.

| Managed dependency | Boot `4.0.0` | Boot `4.1.0` |
|---|---:|---:|
| Spring Framework / WebFlux / Test | `7.0.1` | `7.0.8` |
| Reactor Netty HTTP | `1.3.0` | `1.3.6` |
| Netty HTTP codec | `4.2.7.Final` | `4.2.15.Final` |
| Micrometer Core | `1.16.0` | `1.17.0` |
| OpenTelemetry API | `1.55.0` | `1.62.0` |
| Jackson 3 Databind | `3.0.2` | `3.1.4` |
| Jackson 2 Databind compatibility line | `2.20.1` | `2.21.4` |
| JUnit Jupiter | `6.0.1` | `6.0.3` |
| Mockito Core | `5.20.0` | `5.23.0` |

Priority 4 replaces the failed package assumptions with profile-selected source
adapters. The normal reactor compiles the Boot 3 adapters; `boot4-spike` adds
`src/boot4/java` and excludes only their Boot 3 counterparts. No runtime
package detection or `spring-boot-starter-classic` dependency is used.

| Production contract | Boot 3.5 owner | Boot 4 owner |
|---|---|---|
| Auto-configuration and conditions | `spring-boot-autoconfigure` | `spring-boot-autoconfigure` |
| `WebClientCustomizer` | `spring-boot-autoconfigure`, `org.springframework.boot.web.reactive.function.client` | `spring-boot-webclient`, `org.springframework.boot.webclient` |
| Health contributor API | `spring-boot-actuator`, `org.springframework.boot.actuate.health` | `spring-boot-health`, `org.springframework.boot.health.contributor` |
| `Endpoint` / `ReadOperation` | `spring-boot-actuator` | `spring-boot-actuator` |
| Configuration properties | `spring-boot` / `spring-boot-autoconfigure` | `spring-boot` / `spring-boot-autoconfigure` |
| Metadata processor | `spring-boot-configuration-processor` | `spring-boot-configuration-processor` |

The OTel companion uses profile-selected Spring Boot `WebClientCustomizer`
bridges, preserving propagation on the starter prototype builder on both Boot
generations. Health beans are likewise declared by generation-specific
configuration with their concrete contributor return type. Spring 7 header
reads use APIs shared with Spring 6, and streaming
`ResponseEntity<Flux<DataBuffer>>` handling uses `retrieve().toEntityFlux(...)`
with explicit discarded-buffer release and status/header capture before error
decoding.

Starter, test-helper, and OTel compilation now succeeds on Boot `4.0.0` and
`4.1.0`. The Boot 4 test source set verifies ordered customizer application,
health contributor registration, `rhttpclients` endpoint discovery, and
startup when optional Actuator modules are hidden. The auto-configuration
imports metadata remains unchanged and packaged in each module. Jackson 3,
AOT/native, and transport release gates remain owned by later priorities. The
normal CI and published `2.x` artifacts remain on Boot `3.5.16`.

## Public API compatibility

The `api-compatibility` profile compares the supported public surfaces of all
three published jars against a published baseline that is intentionally different
from the current reactor version. The `3.4.0-SNAPSHOT` development line compares
strictly against published `3.3.0`:

```bash
test ! -e target/published-baseline-repositories/api-root-3.3.0 && \
mvn -s .mvn/maven-central-settings.xml \
  -Dmaven.repo.local=target/published-baseline-repositories/api-root-3.3.0 \
  -Papi-compatibility -DskipTests verify && \
scripts/verify-published-baseline-provenance.sh api-root 3.3.0 \
  target/release-evidence/published-baselines/api-root-3.3.0 \
  reactive-http-client-starter reactive-http-client-test reactive-http-client-otel
bash scripts/verify-api-compatibility-fixtures.sh
```

For release evidence, resolve the three `3.3.0` jars into a fresh target-local
Maven repository and pass that repository through `-Dmaven.repo.local` to the
japicmp build. The frozen `scripts/verify-major-api-delta.sh` remains historical
evidence for the reviewed `2.14.1` to `3.0.0` migration and is no longer part of
normal minor-line CI. A fresh repository prevents a stale or locally installed
artifact from understating the current delta. Every published baseline lane uses
`target/published-baseline-repositories/<lane>-<version>` and records Maven
Central markers plus SHA-256 values under
`target/release-evidence/published-baselines/`. Every module baseline must
contain both its Maven Central-marked POM and JAR before provenance is accepted.
Keep the absence check, Maven
invocation, and `verify-published-baseline-provenance.sh` call in one `&&` chain.
Run `bash scripts/verify-published-baseline-fixtures.sh` to prove that a seeded
locally installed candidate is rejected while Central-marked artifacts pass.

For module-scoped compatibility checks, the inherited baseline guard must still
run before japicmp:

```bash
test ! -e target/published-baseline-repositories/api-starter-3.3.0 && \
mvn -s .mvn/maven-central-settings.xml \
  -Dmaven.repo.local=target/published-baseline-repositories/api-starter-3.3.0 \
  -pl reactive-http-client-starter -Papi-compatibility -DskipTests verify && \
scripts/verify-published-baseline-provenance.sh api-starter 3.3.0 \
  target/release-evidence/published-baselines/api-starter-3.3.0 \
  reactive-http-client-starter
```

The Maven profiles produce japicmp reports under each module's
`target/japicmp/` directory. `api-compatibility` is strict for the current minor
line. The source-controlled cross-major report and reviewed-delta guard retain
the classified `3.0.0` removals as historical migration evidence. The
fixture script verifies that additive APIs pass while removals of a public
constructor, nested fluent method, or public enum constant fail. The filtered
comparison covers the documented extension
points, annotations, exceptions, observability types, configuration properties,
test-helper package, OpenTelemetry companion package, documented cache and
redaction helpers such as `MethodMetadataCache`, `MethodMetadata`, and
`SensitiveHeaders`, the contract snapshot resilience hook
`ResilienceOperatorApplier`, and public diagnostics helpers such as
`ReactiveHttpClientDiagnosticsProvider`,
`ReactiveHttpClientDiagnosticsSnapshot`, and
`ReactiveHttpClientContractSnapshot`. Internal implementation classes remain
excluded unless they are explicitly listed in the POM include set; examples
include proxy invocation internals, URI argument resolution internals,
transport/TLS applicators, and generated release-test fixtures.

### Documented public surface map

This source-controlled map is the release contract between the public docs and
the `api-compatibility` japicmp include filter. The published `2.14.1` form is
frozen as the baseline for the
[3.0.0 API report](api-report-2.14.1-to-3.0.0.md).
`DocumentationReleaseArtifactTest` fails when a mapped pattern is missing from
the POM include set or lacks an explicit support status.

| Japicmp include pattern | Documented public surface | Examples | Support status |
|---|---|---|---|
| `io.github.huynhngochuyhoang.httpstarter.annotation` | Declarative client annotations | Client, HTTP verb, argument binding, ApiRef, idempotency, timeout, and logging annotations | Supported |
| `io.github.huynhngochuyhoang.httpstarter.auth` | Auth extension points and built-in provider helpers | `AuthProvider`, `AuthProviderFactory`, `InvalidatableAuthProvider`, token providers, OAuth2, AWS SigV4 | Supported |
| `io.github.huynhngochuyhoang.httpstarter.enable` | Enablement annotation package | Starter enablement annotations | Supported |
| `io.github.huynhngochuyhoang.httpstarter.exception` | Public exception hierarchy | Client, remote-service, problem-detail, and auth exceptions | Supported |
| `io.github.huynhngochuyhoang.httpstarter.filter` | Public filter contracts | Inbound header filtering support | Supported |
| `io.github.huynhngochuyhoang.httpstarter.observability` | Observer contracts and events | Observer APIs and event models | Supported |
| `io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties*` | Configuration model used by tests and diagnostics | Root, client, auth, resilience, observability, proxy, TLS, and pool config models | Supported |
| `io.github.huynhngochuyhoang.httpstarter.core.DefaultErrorDecoder` | Error decoding extension surface | Default decoder customization and replacement | Supported |
| `io.github.huynhngochuyhoang.httpstarter.core.DefaultHttpExchangeLogger` | Default exchange logger | Built-in metadata, headers, and body logging implementation | Supported |
| `io.github.huynhngochuyhoang.httpstarter.core.ErrorResponseContext` | Error mapper context | Error status, headers, body, and truncation metadata | Supported |
| `io.github.huynhngochuyhoang.httpstarter.core.ErrorResponseMapper` | Error mapper SPI | Custom status/body-to-exception mapping | Supported |
| `io.github.huynhngochuyhoang.httpstarter.core.FileAttachment` | Multipart helper model | File upload metadata used by multipart tests and docs | Supported |
| `io.github.huynhngochuyhoang.httpstarter.core.HttpExchangeLogContext` | Exchange logger context | Final outbound request and response metadata | Supported |
| `io.github.huynhngochuyhoang.httpstarter.core.HttpExchangeLogger` | Exchange logger SPI | Custom logger implementations | Supported |
| `io.github.huynhngochuyhoang.httpstarter.core.MethodMetadataCache` | Replaceable metadata cache | `methodMetadataCache` bean replacement | Supported |
| `io.github.huynhngochuyhoang.httpstarter.core.MethodMetadata*` | Metadata cache model | Public metadata returned by cache implementations | Supported |
| `io.github.huynhngochuyhoang.httpstarter.core.ProblemDetailErrorResponseMapper` | Problem Detail mapper | Built-in RFC 9457 mapper | Supported |
| `io.github.huynhngochuyhoang.httpstarter.core.*ReactiveHttpClientJsonCodec` | JSON codec SPI and Jackson 3 adapter | Starter-owned JSON byte materialization | Supported |
| `io.github.huynhngochuyhoang.httpstarter.core.ReactiveHttpClientCustomizer` | WebClient builder customizer SPI | Per-client builder filters and codecs | Supported |
| `io.github.huynhngochuyhoang.httpstarter.core.ReactiveHttpClientLifecycleContext` | Lifecycle hook context | Attempt/subscription metadata | Supported |
| `io.github.huynhngochuyhoang.httpstarter.core.ReactiveHttpClientLifecycleHook` | Lifecycle hook SPI | Audit and side-effect callbacks | Supported |
| `io.github.huynhngochuyhoang.httpstarter.core.ReactiveHttpClientDiagnosticsProvider*` | Diagnostics provider and nested models | Runtime support summaries | Supported |
| `io.github.huynhngochuyhoang.httpstarter.core.ReactiveHttpClientDiagnosticsSnapshot` | Diagnostics snapshot helper | JSON and Markdown support-bundle rendering | Supported |
| `io.github.huynhngochuyhoang.httpstarter.core.ReactiveHttpClientContractSnapshot*` | Contract snapshot helper and nested builder/client APIs | Approval-style effective contract snapshots | Supported |
| `io.github.huynhngochuyhoang.httpstarter.core.RequestContext` | Request context model | Context values shared with contributors | Supported |
| `io.github.huynhngochuyhoang.httpstarter.core.RequestContextContributor` | Request context contributor SPI | Context enrichment hooks | Supported |
| `io.github.huynhngochuyhoang.httpstarter.core.RequestContextSnapshot` | Request context snapshot model | Immutable context snapshot exports | Supported |
| `io.github.huynhngochuyhoang.httpstarter.core.ResilienceOperatorApplier*` | Contract snapshot resilience hook | Operator availability and instance-type hook | Supported |
| `io.github.huynhngochuyhoang.httpstarter.core.SensitiveHeaders` | Header redaction helper | Custom exchange logger redaction checks | Supported |
| `io.github.huynhngochuyhoang.httpstarter.test` | Test helper package | `MockReactiveHttpClient`, `RecordedExchange`, `RecordedExchangeAssertions`, `ErrorCategoryAssertions`, `MockHttpServer`, and `MockHttpServerExtension` | Supported |
| `io.github.huynhngochuyhoang.httpstarter.otel` | OpenTelemetry companion public package | `OpenTelemetryHttpClientObserver`, `OpenTelemetryContextWebFilter`, `OpenTelemetryContextExchangeFilter`, and `OpenTelemetryHttpClientAutoConfiguration` | Supported |

The `3.0.0` migration replaces `HttpClientHealthIndicator` with its Boot 4
counterpart. The `3.0.0` migration removes `Jackson2ReactiveHttpClientJsonCodec`
and the mapper-based Jackson 2 overloads documented in the
[Boot 4 migration guide](28-spring-boot-4-jackson-migration.md). These are
intentional cross-major changes from the `2.14.1` baseline. No other
compatibility-covered type is reserved for removal. Any future deprecation must
link to its migration note in the same change.

### Constructor and mutable model policy

The V17 review treats these compatibility-covered constructors, records,
builder stages, public nested types, and mutable models as supported for the
current minor line:

- `MethodMetadata` and `MethodMetadataCache` are the replacement-cache surface.
  The `MethodMetadata` no-arg constructor, `TIMEOUT_NOT_SET`, getters, setters,
  mutable maps and sets returned during parsing, and immutable cached maps and
  sets returned after parsing are compatibility-covered. Do not remove, rename,
  narrow, or change the mutability phase of those members in a minor release.
- `ReactiveHttpClientDiagnosticsProvider.ClientSummary`, `TimeoutSummary`, and
  `ResilienceSummary` are immutable diagnostics read models. Their canonical
  record constructors and component names are compatibility-covered; add new
  diagnostics through additive snapshot map fields before changing record
  components.
- `ReactiveHttpClientDiagnosticsSnapshot` static rendering methods are supported
  support-bundle helpers. Provider overloads may include provider-only fields;
  collection overloads must keep unknown provider-only fields explicit rather
  than rendering false values.
- `ReactiveHttpClientContractSnapshot.Client`, `Builder`, builder methods, and
  rendered table columns are supported approval-test helpers. Additive builder
  methods are allowed; removing builder stages, changing existing record
  components, or narrowing the render contract requires a future major release.
- Test helper classes under `io.github.huynhngochuyhoang.httpstarter.test`,
  including `MockReactiveHttpClient`, `RecordedExchange`,
  `RecordedExchangeAssertions`, `ErrorCategoryAssertions`, and
  `MockHttpServerExtension`, are supported test APIs. Package-private
  constructors remain internal; public factory, builder, accessor, response,
  retry, auth, observer, lifecycle, exchange-logger, and assertion methods should
  remain
  additive in the current minor line.
- `ResilienceOperatorApplier.InstanceType` is a public nested enum used by
  diagnostics and contract snapshots. Do not remove or rename enum constants in
  the current minor line; add new constants only with corresponding docs and
  compatibility-fixture review.
- OTel companion constructors and static factories documented through the
  `io.github.huynhngochuyhoang.httpstarter.otel` package are supported. Avoid
  constructor removal or signature narrowing in the current minor line.

### Compatibility include workflow

When documenting a new public helper, update this table and the POM
`api-compatibility` include set in the same change. Prefer the narrowest include
pattern that covers the documented contract. Use a package include only when the
whole package is documented as public, and use a trailing `*` when documented
nested types or builder stages are part of the contract. Keep implementation
internals excluded unless a public doc explicitly presents them as replacement
or extension surfaces. Run the authoritative root and module-scoped commands in
[Public API compatibility](#public-api-compatibility), then run the fixtures and
documentation checks:

```bash
bash scripts/verify-api-compatibility-fixtures.sh
mvn -q -pl reactive-http-client-starter \
  -Dtest=DocumentationReleaseArtifactTest test
```

The profile also fails during `validate` when
`api.compatibility.baseline.version` equals the current reactor
`project.version`. Keep the baseline pointed at the last published release so
Maven cannot satisfy the old artifact from the current reactor or local build.
For the `2.14.1` reactor, the guard must reject
`-Dapi.compatibility.baseline.version=2.14.1`; that self-comparison is never
valid release evidence.

### Configuration metadata and native-hint ownership

The generated [configuration reference](configuration-properties.md) combines
the starter and OTel metadata resources. Starter metadata source types and
nested client-group source methods are validated on the starter classpath;
OTel metadata source types are validated by
`OpenTelemetryConfigurationMetadataTest` on the companion module classpath.
This keeps the starter independent from the optional OTel implementation while
still failing documentation generation when either metadata source drifts.

`ReactiveHttpClientAotSmokeTest` requires reflection hints for every public
nested type declared by `ReactiveHttpClientProperties`, including diagnostics,
health, histogram, auth, resilience, proxy, TLS, pool, correlation, and inbound
header configuration. Optional Actuator endpoint creation remains guarded by
the endpoint API class and the explicit diagnostics property. Optional OTel
auto-configuration remains guarded by both the OTel API class and an
`OpenTelemetry` bean; focused tests exercise both missing-dependency paths.

### Release baseline sequence

While cutting `2.14.1`, keep `api.compatibility.baseline.version` on `2.14.0`
until the `2.14.1` artifacts are published and resolve. Before publishing,
resolve every published `2.14.0` baseline artifact that the release evidence
manifest lists:

```bash
mvn dependency:get -Dartifact=io.github.huynhngochuyhoang:reactive-http-client-starter:2.14.0
mvn dependency:get -Dartifact=io.github.huynhngochuyhoang:reactive-http-client-test:2.14.0
mvn dependency:get -Dartifact=io.github.huynhngochuyhoang:reactive-http-client-otel:2.14.0
```

Run the root API compatibility command and at least one module-scoped
compatibility command before release so the inherited guard is exercised outside
the full reactor. After `2.14.1` is published and resolves, the next development
cycle may bump the reactor to the next version and update
`api.compatibility.baseline.version` to `2.14.1`. Update benchmark
published-baseline commands, release evidence docs, and promoted-report pairing
wording in the same change whenever that baseline property changes.

### V19 baseline transition

The V19 post-release transition moved the maintenance reactor to `2.14.1` only
after the remote `v2.14.0` tag was verified and the
published `2.14.0` starter, test, and OTel artifacts resolved. The API
compatibility baseline and benchmark published-baseline paths now use `2.14.0`,
so release evidence compares the `2.14.1` candidate against the last published
`2.x` release. The default reactor remains on Boot 3.5; Boot 4 work remains
isolated for the future `3.x` line.

### V19 release lanes

V19 established the Boot 3.5 maintenance lane from `v2.14.0` and produced
`2.14.1` as its next release. After that release, the immutable reconstruction
point for a security or critical correctness fix is `v2.14.1`. Create a
dedicated maintenance branch from that tag when a fix is required; do not base
it on the Boot 4 migration work. Applicable fixes are forward-ported to the
future `3.x` line. Do not make one published jar detect or support both Boot
generations.

The checked-in reactor still declares `2.14.1`, so its normal API compatibility
baseline remains `2.14.0`: changing it to `2.14.1` now would compare the reactor
with itself. Once the reactor identity moves to `3.x`, update the cross-major
published baseline to `2.14.1` in the same change. Fresh-repository resolution
of the `2.14.1` starter, test-helper, and OTel artifacts is required before that
transition.

The V19 audit selected **no-go** for publishing `3.0.0`. Runtime, consumer,
AOT/native, and API migration evidence passed, but the candidate still has the
`2.14.1` maintenance identity and Boot 4 Javadoc packaging is not release-ready.
See the [V19 release decision](29-v19-release-decision.md) for the complete gate
record and required next actions.

### V18 baseline transition

The V18 post-release transition moved the reactor to `2.14.0` only after the
published `2.13.0` starter, test, and OTel artifacts resolved. The API
compatibility baseline and benchmark published-baseline paths now use `2.13.0`,
so release evidence compares the `2.14.0` candidate against the last published
release. Until `2.14.0` is published and resolvable, keep the API compatibility
baseline on `2.13.0` unless the release policy explicitly changes patch-line
baselines.

### V17 baseline transition

The V17 post-release transition moved the reactor to `2.13.0` only after the
published `2.12.0` starter, test, and OTel artifacts resolved. The API
compatibility baseline and benchmark published-baseline paths now use `2.12.0`,
so release evidence compares the `2.13.0` candidate against the last published
release. Until `2.13.0` is published and resolvable, keep the API compatibility
baseline on `2.12.0` unless the release policy explicitly changes patch-line
baselines.

### V16 baseline transition

The V16 post-release transition moved the reactor to `2.12.0` only after the
published `2.11.0` starter, test, and OTel artifacts resolved. The API
compatibility baseline and benchmark published-baseline paths now use `2.11.0`,
so release evidence compares the `2.12.0` candidate against the last published
release.

### V15 baseline transition

The V15 minor release moved the reactor to `2.11.0` only after the published
`2.10.0` starter, test, and OTel artifacts resolved. The API compatibility
baseline and benchmark published-baseline paths now use `2.10.0`, so release
evidence compares the `2.11.0` candidate against the last published release.
If a future patch-only `2.11.x` scope is cut before `2.11.0` is published and
resolvable, keep the API compatibility baseline on `2.10.0` unless the release
policy explicitly changes patch-line baselines.

For an intentional breaking change, target a future major release. Review the
japicmp report, document the migration in `CHANGELOG.md`, update
`api.compatibility.baseline.version` after the release-version bump is
committed and the previous version is published, and keep CI failing until that
review is complete.

## Spring AOT and native image

The starter registers Spring runtime hints for its annotation model, configuration
properties, and scanned `@ReactiveHttpClient` interfaces. During AOT processing,
each registered reactive client factory contributes a JDK proxy hint for the
client interface and reflection metadata for its annotated methods.
Framework 7 executable hints are registered per constructor or method with no
broad deprecated member-category registrations; inherited public endpoint
methods are registered from the concrete client contract.

Supported Boot 4 native-image path:

- Java 21 remains the source and bytecode baseline. Native compilation and
  execution use GraalVM Java 25, as required by the selected Boot 4 line.
- Declarative clients discovered through `@EnableReactiveHttpClients`.
- JDK dynamic proxies created by the starter for `@ReactiveHttpClient`
  interfaces.
- Inherited generic endpoint response types resolved through the concrete
  client interface.
- A configured inherited `@ApiRef` endpoint discovered during AOT and invoked
  by the generated native client proxy.
- Starter configuration properties under `reactive.http.*`.
- Problem Detail error mapping, a named auth provider, and Micrometer-backed
  client metrics.
- Opt-in gzip negotiation and transparent JSON response decompression over the
  real loopback transport.
- The optional `rhttpclients` Actuator endpoint and reactive health indicator.
- Diagnostics snapshot version metadata from the packaged Maven
  `pom.properties` resource.

The scheduled smoke installs the default Boot 4 reactor, compiles the fixture,
and runs the generated executable. Native compilation is bounded to 6 GiB and
four worker threads so the fixture remains usable on modest CI and developer
machines:

```bash
mvn -B -ntp -s .mvn/maven-central-settings.xml \
  -Dmaven.javadoc.skip=true install
mvn -B -ntp -s .mvn/maven-central-settings.xml \
  -pl reactive-http-client-test -am \
  -Dtest=Boot4MockReactiveHttpClientTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
mvn -B -ntp -s .mvn/maven-central-settings.xml \
  -f .github/native-smoke/pom.xml -Pnative \
  -Dreactive-http-client.version=3.4.0-SNAPSHOT native:compile
.github/native-smoke/target/reactive-http-client-native-smoke
```

The workflow refuses a dirty source tree and uploads `native-smoke-provenance`
containing the exact Java and native-image toolchains, immutable source commit,
starter and Boot versions, complete fixture dependency list, executable SHA-256,
and successful execution marker. Local generated evidence remains target-only under
`target/release-evidence/native-smoke/`.

Netty 4.2 uses shared Foreign Function and Memory API arenas. The GraalVM 25
fixture therefore enables `-H:+SharedArenaSupport`; removing that build option
must be accompanied by a successful executable smoke run.

Limits:

- The scheduled native smoke uses a real Reactor Netty loopback request. It does
  not exercise custom TLS configuration, Resilience4j, or OTel exporters.
- Optional libraries still require native support and runtime hints from their
  owners, including Resilience4j, alternate TLS providers, and OpenTelemetry
  exporters.
- The `rhttpclients` Actuator endpoint remains optional in native images: the
  starter contributes property-binding hints for
  `reactive.http.observability.diagnostics-endpoint.enabled`, but Actuator endpoint
  infrastructure and management exposure still come from the application.
- Client interfaces must be visible during Spring AOT processing. Dynamically
  generating or registering new client interfaces after AOT processing is not
  supported.

## Release evidence manifest

`DocumentationReleaseArtifactTest` writes a target-only release evidence manifest when `mvn test` runs:

```text
target/release-evidence/reactive-http-client-release-evidence.json
```

The manifest includes a top-level readiness summary, a `releasePrepChecklist`
summary, project version, release state, development version, latest published
consumer version, optional planned final version, API compatibility baseline
version, whether that
baseline equals the current reactor version, the Java runtime used by the test,
the configured Java baseline, the Spring Boot baseline, release-check command
names, published baseline artifacts, benchmark dependency-management metadata,
and benchmark evidence metadata. The benchmark metadata records the
manual/profile-gated smoke and release commands, generated report paths, starter
version under test, baseline library versions, review-trigger thresholds, and the
conditions that require refreshed numbers. The `mvn test` entry is marked
`pass` when this test generated the manifest; compatibility, fixture, diff-check,
and benchmark entries remain `pending` until the release maintainer runs them.

During snapshot development, `plannedFinalVersion` and the promotable report
path are absent. The manifest keeps README and quick-start expectations on the
latest published consumer version and reports benchmark promotion as deferred
until an explicit release-cut transition removes the snapshot suffix.
The root `latest.published.version` property owns public consumer snippets;
`api.compatibility.baseline.version` remains an independent compatibility policy.

The readiness summary reports generated-test evidence separately from manual
release evidence. Generated documentation and link checks can be marked `pass`
by this test, while compatibility and benchmark commands remain `pending` until
a maintainer runs them. It also surfaces the promoted benchmark report path,
missing promoted reports, stale benchmark-report links, and the target-only
release evidence directory.

The `releasePrepChecklist` field is the concise release-prep view for humans. It
lists the current changelog status, README and quick-start version-snippet
status, published-baseline artifact resolution commands, root and module-scoped
API compatibility commands, the API compatibility fixture command, benchmark
smoke/release/published-baseline commands, promoted benchmark report status,
generated-doc and Markdown-link status, and the target-only evidence reminder.
Use its `manualCommands` list as the one-place pending release-work list; the
manifest is still generated under `target/` and is not committed as release
proof.

Before publishing, run the pending commands and resolve every published baseline
artifact command listed in the manifest. An unresolved baseline artifact is a
release blocker because API compatibility or published-starter benchmark evidence
would not be reproducible. If the release changes request
construction, observability, resilience wrapping, transport/client-builder
behavior, or includes public performance claims, also run the release benchmark
command. For public performance claims, promote the release-quality report into
`docs/benchmark-report-<version>.md` and cite that source-controlled report from
the release notes; do not link generated `target/` reports directly. The smoke
benchmark proves the harness starts; do not publish smoke-only numbers as
performance evidence. When comparing against a published baseline, keep the
current candidate and published-baseline reports at the distinct paths recorded
in the manifest and resolve the listed baseline artifacts before report
promotion. Benchmark threshold crossings are manual review triggers, not hard
gates; rerun the relevant current and baseline methods on the same machine
before treating a movement as a release trend. Attach the JSON manifest to the
release notes or paste its contents into the release checklist. Do not commit
files from `target/release-evidence/`; regenerate them from the release
candidate checkout.

## Release smoke matrix

The release smoke profile exercises a minimal declarative client with Micrometer
enabled through the real starter proxy path:

```bash
mvn -Prelease-smoke test
```

Normal CI keeps the fast Spring AOT smoke tests. The manually triggered and
weekly `native-smoke.yml` workflow also builds and runs one minimal native image
which includes Micrometer and Actuator while omitting Resilience4j and OpenTelemetry.

The CI release smoke job currently runs:

| Java | Spring Boot | Command |
|---|---|---|
| 21 | 4.0.0 | `mvn -B -ntp -Prelease-smoke -Dspring-boot.version=4.0.0 test` |
| 21 | 4.1.0 | `mvn -B -ntp -Prelease-smoke -Dspring-boot.version=4.1.0 test` |

Expand the matrix before release when adding support for another Java or Spring
Boot baseline. Core starter AOT/native smoke ownership is distinct from optional
integration ownership: Resilience4j, alternate TLS providers, and OpenTelemetry
exporters must supply their own native support where needed.

### Boot 4 assembled consumer fixture

The external fixture under `.github/boot4-consumer` is not a reactor module and
cannot be published. It consumes installed artifacts through an independent
Boot 4 POM, so missing dependencies or auto-configuration imports fail at the
application boundary.

```bash
scripts/verify-current-consumer.sh
```

The assembled application performs real inherited-generic and configured
`@ApiRef` loopback calls plus repeated headers, automatic redirects, unexpected
bodies on bodiless methods, typed `ResponseEntity`, deferred streaming-body
consumption, timeout classification, and Problem Detail mapping. The same
application verifies lifecycle and observer terminal metadata, diagnostics,
health, Micrometer, OTel, strict retry startup, constructor-injected custom
exchange loggers, and auth-aware application JSON codec bytes on the wire. The
full default reactor test run supplies detailed OAuth2, SigV4 raw-body signing,
and optional integration absence fixtures. The normal test-helper sources additionally verify
Jackson 3 signing bytes and final outbound metadata through
`MockReactiveHttpClient`.

The verifier installs the current reactor into a fresh target-local repository,
runs the complete mock parity classes, then runs the assembled consumer against
the installed jars. It rejects reactor `target/classes` leakage and records
separate mock and real-server test reports, the consumer classpath, dependency
tree, effective POM, artifact hashes, commit state, and provenance under
`target/release-evidence/current-consumer/current-3.4.0-SNAPSHOT/`. Fresh Surefire XML is copied immediately after each successful mock or consumer
test stage. An `EXIT` trap repeats that filtered copy before preserving the original
verifier status, including when either test stage fails.
It also records the last completed stage and exit status when a later
classpath/provenance check fails.

The mock evidence is limited to starter-owned auth, retry, lifecycle, observer,
exchange-logging, inherited-generic, repeated-header, JSON-codec, and final-request
metadata behavior. Protocol negotiation, TLS, compression wire bytes, pool timing,
and connection reuse remain real-transport claims and are not inferred from the
in-process helper.

The normal reactor and release-smoke matrix use Boot 4. The Boot 3.5 `2.x` line
is reconstructed only from its maintenance tag; no dual-generation helper
artifact is published.

### Published Boot 4 consumer baseline

The current-reactor fixture above and the published baseline are separate
evidence lanes. From a clean checkout, resolve the latest published parent,
starter, test helper, and OTel companion exclusively through Maven Central:

```bash
scripts/verify-published-release-artifacts.sh 3.3.0
scripts/verify-published-consumer.sh 3.3.0
```

The consumer command refuses an existing
`target/published-baseline-repositories/consumer-3.3.0` directory instead of reusing it.
It runs the same Boot 4 application fixture against published `3.3.0`, verifies
the Maven Central `_remote.repositories` marker for the parent and every project artifact,
rejects reactor `target/classes` entries, and writes target-only dependency
trees, classpaths, consumer/module effective POMs, published parent/module POM
and jar SHA-256 values, test reports, fixture commit state, completed stage, exit
status, and provenance under
`target/release-evidence/published-consumer/published-3.3.0/`.
Fresh Surefire XML is copied immediately after the consumer test stage. Its `EXIT`
trap repeats that filtered copy and retains the evidence when a test, Central marker,
classpath, or checksum check fails.

The release-artifact command uses its own fresh repository and additionally
requires the starter, test-helper, and OTel source and Javadoc jars. Its
target-only Central markers, checksums, and provenance are written under
`target/release-evidence/published-baselines/release-artifacts-3.3.0/`.

The manually dispatched `Published Consumer Smoke` workflow runs both commands
and uploads only their published-release evidence directories. The normal
`Boot 4 Assembled Consumer` CI job remains the current-reactor lane.

### V20 Jackson 3 codec ownership

`ReactiveHttpClientJsonCodec` is the serialization boundary for bytes owned by
the starter. The default Boot 4 reactor selects
`Jackson3ReactiveHttpClientJsonCodec` from the application Jackson 3 mapper.
The effective Boot 4 POM excludes Jackson 2 and owns Jackson 3 through
`spring-boot-jackson` and `tools.jackson.core:jackson-databind`.

Authenticated JSON is serialized once. The resulting `byte[]` is both the raw
body supplied to auth/signing and the body written by WebClient. Problem Detail
uses the same codec. OAuth2 token/error decoding remains on configured WebClient
codecs, while deterministic diagnostics and contract snapshots require no
application mapper. Boot 4 tests cover Jackson 2 absence, Problem Detail, naming
strategy, Java time, unknown properties, and mock-helper byte parity. See the
[Jackson migration guide](28-spring-boot-4-jackson-migration.md).

### V20 optional integrations on Boot 4

The default Boot 4 reactor manages Resilience4j `2.4.0`, including the published
`resilience4j-spring-boot4` generation, while the Boot 3.5 `2.14.1` maintenance
line remains on `2.2.0`. Retry, circuit breaker, bulkhead, rate limiter, and
tagged metrics tests pass with Spring Framework 7 and the Boot-managed Reactor
line. A combined minimal-classpath test hides Resilience4j, Micrometer core,
Actuator health/endpoint APIs, and OpenTelemetry while retaining the starter
WebClient and static sanitized diagnostics provider.

A clean consumer dependency tree contains no Resilience4j module,
`micrometer-core`, Actuator, or OpenTelemetry dependency. Spring WebFlux still
owns its normal `micrometer-observation` dependency. The OTel companion remains
explicit opt-in and activates observer, propagation, and semantic attributes
only when its API and beans are present.
