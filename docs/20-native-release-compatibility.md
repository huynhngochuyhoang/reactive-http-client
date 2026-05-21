# Native Image and Release Compatibility

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

- Optional integrations still require their own native support and runtime hints
  from their owning libraries, including Resilience4j, Reactor Netty, TLS
  providers, OAuth2 token clients, and OpenTelemetry exporters.
- Client interfaces must be visible during Spring AOT processing. Dynamically
  generating or registering new client interfaces after AOT processing is not
  supported.
- Native-image compilation itself is not run by the default CI job. The starter
  includes AOT smoke coverage that processes a minimal annotated client context
  and verifies generated proxy hints.

## Release smoke matrix

The release smoke profile exercises a minimal declarative client with Micrometer
enabled through the real starter proxy path:

```bash
mvn -Prelease-smoke test
```

The CI release smoke job currently runs:

| Java | Spring Boot | Command |
|---|---|---|
| 21 | 3.5.0 | `mvn -B -ntp -Prelease-smoke -Dspring-boot.version=3.5.0 test` |

Expand the matrix before release when adding support for another Java or Spring
Boot baseline.
