# Native Image and Release Compatibility

## Supported Spring Boot baseline

The supported baseline is Java 21 with Spring Boot `3.5.0`. CI runs the release
smoke profile against that minimum tested baseline. Patch upgrades within the
Spring Boot `3.5.x` line are expected to remain compatible, but adding another
Spring Boot minor line requires an explicit release-smoke matrix entry before it
is documented as supported.

## Public API compatibility

The `api-compatibility` profile compares the supported public surfaces of all
three published jars against the latest released baseline, `2.7.0`:

```bash
mvn -Papi-compatibility -DskipTests verify
bash scripts/verify-api-compatibility-fixtures.sh
```

The Maven profile produces japicmp reports under each module's
`target/japicmp/` directory and fails for binary-incompatible changes. The
fixture script verifies that additive APIs pass while removal of a public
constructor fails. Internal implementation classes and test fixtures are not
part of the filtered public API comparison.

For an intentional breaking change, target a future major release. Review the
japicmp report, document the migration in `CHANGELOG.md`, update
`api.compatibility.baseline.version` after the major release is published, and
keep CI failing until that review is complete.

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

Limits:

- The scheduled native smoke covers core bootstrap, client scanning, JDK proxy
  creation, and the default Reactor Netty transport classes. It does not exercise
  outbound network calls, auth flows, or custom TLS configuration.
- Optional libraries still require native support and runtime hints from their
  owners, including Resilience4j, alternate TLS providers, and OpenTelemetry
  exporters.
- Client interfaces must be visible during Spring AOT processing. Dynamically
  generating or registering new client interfaces after AOT processing is not
  supported.
- Native-image compilation itself is not run by the default CI job. The starter
  includes AOT smoke coverage that processes a minimal annotated client context,
  verifies inherited-method proxy hints, and tolerates unrelated unresolvable
  factory metadata.

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

Expand the matrix before release when adding support for another Java or Spring
Boot baseline. Core starter AOT/native smoke ownership is distinct from optional
integration ownership: Resilience4j, alternate TLS providers, and OpenTelemetry
exporters must supply their own native support where needed.
