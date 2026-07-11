# Native Image and Release Compatibility

## Supported Spring Boot baseline

The supported baseline is Java 21 with Spring Boot `3.5.0`. The default dependency
management uses Spring Boot `3.5.16`, the latest published `3.5.x` patch verified
on 2026-07-10. CI runs release smoke against both the minimum and default patch.
Adding another Spring Boot minor line requires an explicit release-smoke matrix
entry before it is documented as supported.

## Dependency baseline readiness

Current release-line dependency inputs:

| Area | Source | Current policy |
|---|---|---|
| Java runtime/compiler | Root `java.version`, `maven.compiler.source`, and `maven.compiler.target` | `21` is the supported baseline. Raising it requires a minor release, release-smoke review, and native-smoke review. |
| Spring Boot baseline | Root `spring-boot.version` | `3.5.0` is the minimum tested baseline and `3.5.16` is the default managed patch. Patch upgrades within `3.5.x` are compatibility-neutral when release smoke, AOT smoke, and generated documentation tests pass. A new Spring Boot minor line requires a minor release and an expanded release-smoke matrix. |
| Spring WebFlux, Reactor Netty, Micrometer, and OpenTelemetry API | Spring Boot dependency management | Keep module POMs versionless for these artifacts. Review exact resolved versions from the effective POM or release evidence when `spring-boot.version` changes. |
| Resilience4j | Root `resilience4j.version` plus `resilience4j-bom` | `2.2.0` remains the optional resilience baseline. Patch-compatible updates are acceptable with operator and diagnostics tests; a major or behavior-changing baseline update requires a minor release. |
| Test dependencies | Spring Boot dependency management plus explicit test-only pins | Test-only updates are compatibility-neutral when they do not change published test-helper APIs or release fixtures. Explicit pins such as the TLS fixture dependency stay local to tests. |
| Benchmark harness | Root `jmh.version` and benchmark Maven profiles | Benchmark-only updates do not change runtime compatibility, but release-quality reports must record the project version, starter version, Spring Boot baseline, resolved WebFlux/Reactor Netty versions, dependency-management source, and benchmark commit. |

Compatibility-neutral dependency maintenance includes Spring Boot `3.5.x` patch
updates, managed Spring WebFlux/Reactor Netty/Micrometer/OpenTelemetry patch
movement inherited from that Boot line, Resilience4j patch-compatible updates,
test-only dependency updates, and benchmark harness updates that keep report
metadata intact.

Requires a minor release: raising the Java baseline, adding a new Spring Boot
minor line, changing optional integrations into required runtime dependencies,
changing the Resilience4j baseline in a way that affects operator behavior or
public diagnostics, or upgrading Reactor Netty/Micrometer/OpenTelemetry outside
the managed Spring Boot baseline for runtime behavior. Do not mix a baseline
upgrade with unrelated feature work; make the baseline change visible in release
evidence, generated configuration metadata, and benchmark metadata in the same
change.

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

The OTel companion now publishes its propagation filter through the
starter-owned `ReactiveHttpClientCustomizer`, removing its direct dependency on
either Boot customizer package. Spring 7 header reads use APIs shared with
Spring 6, and streaming `ResponseEntity<Flux<DataBuffer>>` handling uses
`retrieve().toEntityFlux(...)` with explicit discarded-buffer release.

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
from the current reactor version. While the project version remains `2.14.1`,
the baseline stays on `2.14.0`:

```bash
mvn -Papi-compatibility -DskipTests verify
bash scripts/verify-api-compatibility-fixtures.sh
```

For module-scoped compatibility checks, the inherited baseline guard must still
run before japicmp:

```bash
mvn -pl reactive-http-client-starter -Papi-compatibility -DskipTests validate
mvn -pl reactive-http-client-starter -Papi-compatibility -DskipTests verify
```

The Maven profile produces japicmp reports under each module's
`target/japicmp/` directory and fails for binary-incompatible changes. The
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
the `api-compatibility` japicmp include filter.
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

No compatibility-covered type is currently deprecated. If a future public type is
reserved for removal or replacement in a future major release, mark its row as
`Reserved` or `Deprecated` and link to the migration note in the same change.

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
  retry, auth, observer, lifecycle, and assertion methods should remain
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
or extension surfaces. Run `mvn -Papi-compatibility -DskipTests verify`,
`mvn -pl reactive-http-client-starter -Papi-compatibility -DskipTests verify`,
`bash scripts/verify-api-compatibility-fixtures.sh`, and
`mvn -q -pl reactive-http-client-starter -Dtest=DocumentationReleaseArtifactTest test`
before publishing release evidence.

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

The Boot 3.5 maintenance branch is named `2.x` and is rooted at `v2.14.0`;
`2.14.1` is its next maintenance reactor version. The line accepts security and
critical correctness fixes, keeps API compatibility against the latest published
`2.x` artifact, and forward-ports applicable fixes to the future `3.x` line.
Boot 4 migration work stays isolated until the V19 `3.0.0` go/no-go gates pass.
Do not make one published jar detect or support both Boot generations.

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

Supported native-image path:

- Spring Boot AOT processing with Java 21.
- Declarative clients discovered through `@EnableReactiveHttpClients`.
- JDK dynamic proxies created by the starter for `@ReactiveHttpClient`
  interfaces.
- Starter configuration properties under `reactive.http.*`.
- Micrometer-backed client metrics when Micrometer is present.
- Diagnostics snapshot version metadata from the packaged Maven
  `pom.properties` resource.

Limits:

- The scheduled native smoke covers core bootstrap, client scanning, JDK proxy
  creation, and the default Reactor Netty transport classes. It does not exercise
  outbound network calls, auth flows, or custom TLS configuration.
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
- Native-image compilation itself is not run by the default CI job. The starter
  includes AOT smoke coverage that processes a minimal annotated client context,
  verifies inherited-method proxy hints, and tolerates unrelated unresolvable
  factory metadata.

## Release evidence manifest

`DocumentationReleaseArtifactTest` writes a target-only release evidence manifest when `mvn test` runs:

```text
target/release-evidence/reactive-http-client-release-evidence.json
```

The manifest includes a top-level readiness summary, a `releasePrepChecklist`
summary, project version, API compatibility baseline version, whether that
baseline equals the current reactor version, the Java runtime used by the test,
the configured Java baseline, the Spring Boot baseline, release-check command
names, published baseline artifacts, benchmark dependency-management metadata,
and benchmark evidence metadata. The benchmark metadata records the
manual/profile-gated smoke and release commands, generated report paths, starter
version under test, baseline library versions, review-trigger thresholds, and the
conditions that require refreshed numbers. The `mvn test` entry is marked
`pass` when this test generated the manifest; compatibility, fixture, diff-check,
and benchmark entries remain `pending` until the release maintainer runs them.

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
whose consumer classpath omits optional integrations.

The CI release smoke job currently runs:

| Java | Spring Boot | Command |
|---|---|---|
| 21 | 3.5.0 | `mvn -B -ntp -Prelease-smoke -Dspring-boot.version=3.5.0 test` |
| 21 | 3.5.16 | `mvn -B -ntp -Prelease-smoke -Dspring-boot.version=3.5.16 test` |

Expand the matrix before release when adding support for another Java or Spring
Boot baseline. Core starter AOT/native smoke ownership is distinct from optional
integration ownership: Resilience4j, alternate TLS providers, and OpenTelemetry
exporters must supply their own native support where needed.
