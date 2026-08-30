# Spring Boot 4 and Starter 4.x Migration

> **Current migration guide.** Use this page for supported Boot 3-to-Boot 4 migration
> instructions. Linked API reports and versioned release decisions are immutable
> historical evidence, not commands for the current reactor.

This guide migrates published Spring Boot 3.5 starter 2.x applications to the
Spring Boot 4 starter 4.x line. The current reactor is the `4.2.0-SNAPSHOT` development line; use
published `4.1.0` when consuming it from a release repository.

Annotations, exception categories, lifecycle hooks, observers, retry and
idempotency behavior, diagnostics sanitization, and reactive.http property names
remain unchanged. Review the
[2.14.1 to 3.0.0 API Report](api-report-2.14.1-to-3.0.0.md).

Applications already using explicit `GET` response caching on published
`4.0.0` remain source and binary compatible with the `4.1.x` line.
`CacheResponse.semanticRead()` is an additive, false-defaulted member for one
explicitly selected non-`GET` method; client-wide cache policy does not supply
that acknowledgement. See [Response Caching](32-response-caching.md) for the
body-byte and variant-isolation requirements before adopting it.

## Choose the release lane

Upgrade to starter `4.x` only when the application can move to Spring Boot 4,
Java 21, Jackson 3, and the Boot 4 Resilience4j integration. Stay on published
starter `2.14.1` when the application must remain on Boot 3.5, still calls the
deprecated Jackson 2 constructors or mock `objectMapper(...)` adapter, or cannot
yet update custom Boot WebClient/health imports. The `2.x` lane is limited to
security and critical correctness maintenance; new migration work belongs on
`4.x`.

## Maven dependencies

Boot 3.5 and starter 2.x:

```xml
<parent>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-parent</artifactId>
  <version>3.5.16</version>
  <relativePath/>
</parent>
<properties>
  <java.version>21</java.version>
  <reactive-http-client.version>2.14.1</reactive-http-client.version>
</properties>
<dependencies>
  <dependency>
    <groupId>io.github.huynhngochuyhoang</groupId>
    <artifactId>reactive-http-client-starter</artifactId>
    <version>${reactive-http-client.version}</version>
  </dependency>
  <dependency>
    <groupId>io.github.huynhngochuyhoang</groupId>
    <artifactId>reactive-http-client-otel</artifactId>
    <version>${reactive-http-client.version}</version>
  </dependency>
  <dependency>
    <groupId>io.github.huynhngochuyhoang</groupId>
    <artifactId>reactive-http-client-test</artifactId>
    <version>${reactive-http-client.version}</version>
    <scope>test</scope>
  </dependency>
</dependencies>
```

Boot 4 and starter 4.x:

```xml
<parent>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-parent</artifactId>
  <version>4.0.0</version>
  <relativePath/>
</parent>
<properties>
  <java.version>21</java.version>
  <reactive-http-client.version>4.1.0</reactive-http-client.version>
</properties>
<dependencies>
  <dependency>
    <groupId>io.github.huynhngochuyhoang</groupId>
    <artifactId>reactive-http-client-starter</artifactId>
    <version>${reactive-http-client.version}</version>
  </dependency>
  <dependency>
    <groupId>io.github.huynhngochuyhoang</groupId>
    <artifactId>reactive-http-client-otel</artifactId>
    <version>${reactive-http-client.version}</version>
  </dependency>
  <dependency>
    <groupId>io.github.huynhngochuyhoang</groupId>
    <artifactId>reactive-http-client-test</artifactId>
    <version>${reactive-http-client.version}</version>
    <scope>test</scope>
  </dependency>
</dependencies>
```

Resilience4j users must move all modules to its Boot 4 line. See
[Resilience4j Integration](07-resilience4j.md) for the 2.4.x BOM and
resilience4j-spring-boot4 coordinates.

## Diagnose adoption failures

Start with the resolved runtime classpath, not only the declared dependency:

```bash
mvn -q dependency:tree \
  '-Dincludes=io.github.huynhngochuyhoang:*,org.springframework.boot:*,tools.jackson.core:*,com.fasterxml.jackson.core:*,io.github.resilience4j:*'
```

All starter modules must use one released version. A Boot 4 application must
resolve starter `4.1.0`, Boot 4 modules, Jackson 3, and Boot 4 Resilience4j
modules. Do not combine starter `2.x` and `4.x`, or override individual Spring
Framework/Jackson artifacts outside Boot dependency management.

| Symptom | Confirm | Correction |
|---|---|---|
| `TypeNotPresentException` or `ClassNotFoundException` for `org.springframework.boot.webclient.WebClientCustomizer` | The application is running Boot 3 or has a mixed Boot 3/4 classpath while loading starter 4.x. | Use starter `2.14.1` with Boot 3.5, or upgrade the complete application dependency graph to Boot 4 before using starter `4.0.0`. |
| A customizer using `org.springframework.boot.web.reactive.function.client.WebClientCustomizer` no longer compiles | That is the Boot 3 customizer package. | Import `org.springframework.boot.webclient.WebClientCustomizer`, or use the starter-owned `ReactiveHttpClientCustomizer` when customization is client-specific. |
| `reactiveHttpClientHealthIndicator` or `/actuator/rhttpclients` is absent | Check that Actuator is present, `reactive.http.observability.health.enabled` or `reactive.http.observability.diagnostics-endpoint.enabled` is true, and the endpoint is exposed. | Add the Boot 4 Actuator starter, use `org.springframework.boot.health.contributor` in custom health code, and expose `health,rhttpclients`. Do not add Boot 3 Actuator packages manually. |
| `com.fasterxml.jackson.databind.ObjectMapper` is missing, or JSON signing/Problem Detail mapping has no codec | Starter 4.x uses Jackson 3 and does not ship the removed Jackson 2 adapter. | Move application modules to `tools.jackson.*`. Let Boot create the Jackson 3 mapper and default starter codec, or provide one `ReactiveHttpClientJsonCodec` bean backed by the application mapper. |
| OTel propagation, mock helpers, or their auto-configuration is missing | Compare the starter, `reactive-http-client-otel`, and `reactive-http-client-test` versions in the tree. | Keep all three on `4.1.0`; keep the test helper test-scoped. The OTel companion remains optional and must not be copied from the `2.x` lane. |
| Retry operators are unavailable or Boot fails while creating Resilience4j beans | Check for Boot 3 `resilience4j-spring-boot3` or mixed Resilience4j versions. | Use the `2.4.x` BOM and `resilience4j-spring-boot4`; add only the operator modules required by the configured policies. |

If the tree is correct but startup still fails, capture the condition evaluation
report and a sanitized support bundle using
[Production Support Bundles](26-support-bundles.md). Do not add an old Boot or
Jackson artifact merely to satisfy one missing class; that masks the generation
mismatch.

## Package and module changes

| Contract | Boot 3.5 / 2.x | Boot 4 / 4.x | Action |
|---|---|---|---|
| WebClient customizer | org.springframework.boot.web.reactive.function.client.WebClientCustomizer | org.springframework.boot.webclient.WebClientCustomizer | Update Boot customizer imports. ReactiveHttpClientCustomizer is unchanged. |
| Health API | org.springframework.boot.actuate.health | org.springframework.boot.health.contributor | Update custom health imports. |
| Built-in health type | HttpClientHealthIndicator | Boot4HttpClientHealthIndicator | Prefer the stable reactiveHttpClientHealthIndicator bean name; update direct type references. |
| JSON mapper | com.fasterxml.jackson.databind.ObjectMapper | tools.jackson.databind.ObjectMapper | Move mapper modules and configuration to Jackson 3. |
| Starter JSON SPI | Mapper constructors | ReactiveHttpClientJsonCodec | Inject the codec into starter extension points. |
| Focused Boot modules | Boot 3 autoconfigure and Actuator packages | spring-boot-webclient and spring-boot-health | Update only direct application dependencies and imports. |

The health type replacement and the deprecated Jackson 2 shims listed below are
the only reviewed binary and source incompatibilities. Boot 4 moved the health
API and changed the health method return type. No unrelated public removal is
accepted.

## Before and after application code

The declarative client contract itself does not change:

```java
@ReactiveHttpClient(name = "orders")
public interface OrdersClient {

    @GET("/orders/{id}")
    Mono<OrderResponse> get(@PathVar("id") String id);
}
```

Boot 3.5 and starter 2.x commonly wired the deprecated mapper constructor and
the old Boot customizer package:

```java
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.web.reactive.function.client.WebClientCustomizer;

@Bean
WebClientCustomizer clientCustomizer() {
    return builder -> builder.defaultHeader("X-Application", "orders");
}

@Bean
ProblemDetailErrorResponseMapper problemDetails(ObjectMapper objectMapper) {
    return new ProblemDetailErrorResponseMapper(objectMapper);
}
```

Boot 4 and starter 4.x use Jackson 3 plus the stable codec boundary:

```java
import org.springframework.boot.webclient.WebClientCustomizer;
import tools.jackson.databind.ObjectMapper;

@Bean
WebClientCustomizer clientCustomizer() {
    return builder -> builder.defaultHeader("X-Application", "orders");
}

@Bean
ReactiveHttpClientJsonCodec reactiveHttpClientJsonCodec(ObjectMapper objectMapper) {
    return new Jackson3ReactiveHttpClientJsonCodec(objectMapper);
}

@Bean
ProblemDetailErrorResponseMapper problemDetails(ReactiveHttpClientJsonCodec jsonCodec) {
    return new ProblemDetailErrorResponseMapper(jsonCodec);
}
```

## Jackson 3 codec ownership

```java
@Bean
ReactiveHttpClientJsonCodec reactiveHttpClientJsonCodec(
        tools.jackson.databind.ObjectMapper objectMapper) {
    return new Jackson3ReactiveHttpClientJsonCodec(objectMapper);
}
```

The Boot 4 adapter uses the application Jackson 3 mapper. Authenticated JSON
uses one byte array for signing and the wire body. Problem Detail mapping uses
the same codec.

The `3.0.0` line removes these deprecated Jackson 2 migration shims:

- `Jackson2ReactiveHttpClientJsonCodec`
- ProblemDetailErrorResponseMapper(com.fasterxml.jackson.databind.ObjectMapper)
- MockReactiveHttpClient.Builder.objectMapper(...)
- ReactiveClientInvocationHandler constructors accepting Jackson 2

Use the codec constructor, MockReactiveHttpClient.Builder.jsonCodec(...), and
codec-based handler constructors. Applications that must retain the Jackson 2
adapter or mapper overloads must remain on the `2.14.x` maintenance line while
they migrate. The `4.x` starter and test helper do not depend on Jackson 2.

`ReactiveHttpClientJsonCodec` is the stable starter serialization boundary.
The default bean wraps Boot's application Jackson 3 `ObjectMapper`, so modules,
naming strategies, and serializers apply to Problem Detail mapping and exact
JSON bytes materialized for authentication. A custom codec bean replaces that
default. Configure WebClient with equivalent codecs when signing requires the
materialized JSON bytes to match the final wire representation.

The codec is a bean contract, not a `reactive.http` property. It requires no
configuration-metadata entry or reflection hint; Boot owns construction of its
Jackson 3 mapper, and the starter constructs the adapter through normal bean
methods.

## Configuration

No reactive.http property was renamed for Boot 4. Existing metadata
deprecations, including legacy timeout properties, retain their replacements.
No Boot 4-only metadata replacement is required.

Before, Boot 3.5 and starter 2.x:

```yaml
reactive:
  http:
    clients:
      orders:
        base-url: https://orders-api.example.invalid
        request-timeout-ms: 3000
        resilience:
          enabled: true
          retry-methods: [GET, HEAD]
        auth:
          type: oauth2-client-credentials
          oauth2-client-credentials:
            token-uri: https://identity.example.invalid/oauth/token
            client-id: ${ORDERS_CLIENT_ID}
            client-secret: ${ORDERS_CLIENT_SECRET}
    observability:
      enabled: true
      diagnostics-endpoint:
        enabled: true
```

After, Boot 4 and starter 4.x; Retry must be selected explicitly:

```yaml
reactive:
  http:
    clients:
      orders:
        base-url: https://orders-api.example.invalid
        request-timeout-ms: 3000
        resilience:
          enabled: true
          retry: default
          retry-methods: [GET, HEAD]
        auth:
          type: oauth2-client-credentials
          oauth2-client-credentials:
            token-uri: https://identity.example.invalid/oauth/token
            client-id: ${ORDERS_CLIENT_ID}
            client-secret: ${ORDERS_CLIENT_SECRET}
    observability:
      enabled: true
      diagnostics-endpoint:
        enabled: true
```

This example preserves Retry only. If the 2.x application relied on implicitly
activated CircuitBreaker, Bulkhead, or RateLimiter operators, select each
intended operator explicitly. Use the
[Starter 3.x to 4.x Resilience Migration](31-3x-to-4x-resilience-migration.md)
matrix to audit every client policy.

## Actuator, AOT, and native image

The endpoint ID remains `rhttpclients`. Custom health code uses the Boot 4
`org.springframework.boot.health.contributor` package; the built-in bean keeps
the name `reactiveHttpClientHealthIndicator` and has type
`Boot4HttpClientHealthIndicator`.

```yaml
reactive:
  http:
    observability:
      health:
        enabled: true
      diagnostics-endpoint:
        enabled: true

management:
  endpoints:
    web:
      exposure:
        include: health,rhttpclients
  endpoint:
    health:
      show-details: when-authorized
```

Keep management endpoint authorization in application security policy. Health
details and diagnostics are sanitized, but they still disclose client names and
effective policy useful to an operator.

The Boot 4 native baseline uses GraalVM Java 25 while source remains Java 21 and
covers loopback HTTP, inherited generics, Problem Detail, auth, Micrometer,
diagnostics, and health. Run the AOT/native fixture described in
[Native Image and Release Compatibility](20-native-release-compatibility.md);
do not reuse a Boot 3 native configuration for the `4.x` artifact.

## Test-helper migration

Mock workflows and assertion semantics remain unchanged. Custom JSON setup uses:

```java
ReactiveHttpClientJsonCodec codec =
        new Jackson3ReactiveHttpClientJsonCodec(objectMapper);
MockReactiveHttpClient<OrderClient> mock = MockReactiveHttpClient
        .forClient(OrderClient.class)
        .jsonCodec(codec)
        .respond(exchange ->
                MockReactiveHttpClient.json(200, "{\"code\":\"ok\"}"))
        .build();
```

`MockReactiveHttpClient` owns an isolated application context. When
`@LogHttpExchange` selects a constructor-injected logger, register that exact
configured instance with the mock instead of relying on a no-argument fallback:

```java
@LogHttpExchange(logger = AuditExchangeLogger.class)
@ReactiveHttpClient(name = "orders")
interface OrdersClient {
    @GET("/orders/{id}")
    Mono<OrderResponse> get(@PathVar("id") String id);
}

@Bean
AuditExchangeLogger auditExchangeLogger(AuditSink sink) {
    return new AuditExchangeLogger(sink);
}

AuditExchangeLogger logger = new AuditExchangeLogger(auditSink);
MockReactiveHttpClient<OrdersClient> mock = MockReactiveHttpClient
        .forClient(OrdersClient.class)
        .withExchangeLogger(logger)
        .respond(exchange -> MockReactiveHttpClient.json(200, "{\"code\":\"ok\"}"))
        .build();
```

Production resolves the Spring bean by logger class. The mock resolves the
instance registered through `withExchangeLogger(...)`; this supports loggers
whose constructors require application collaborators.

## Verify the migration

The non-reactor Boot 4 consumer compiles and runs representative migration code,
including inherited generic and `@ApiRef` clients, Problem Detail, redirects,
streaming ownership, timeout diagnostics, health, Micrometer, OTel, strict
retry, and mock helpers. Use the authoritative commands in
[Boot 4 assembled consumer fixture](20-native-release-compatibility.md#boot-4-assembled-consumer-fixture)
for reactor and release-candidate changes. To verify released public coordinates from a
fresh Maven Central repository, use
[Published Boot 4 consumer baseline](20-native-release-compatibility.md#published-boot-4-consumer-baseline).
The latter is the adoption check for starter `4.0.0`; it does not consume
reactor classes or `4.2.0-SNAPSHOT` development artifacts.

## Migration checklist

1. Upgrade to Boot 4 and Java 21.
2. Replace Boot WebClientCustomizer and health imports.
3. Move application JSON modules and configuration to Jackson 3.
4. Replace mapper-based starter calls with ReactiveHttpClientJsonCodec.
5. Move Resilience4j users to its Boot 4 line.
6. Run configuration, Problem Detail, auth, lifecycle, retry, streaming, and
   test-helper suites.
7. Run AOT and native verification for native applications.
8. Run the independent Boot 4 consumer and any application-specific custom
   logger, diagnostics, health, and support-bundle checks.

## Transport header ownership

Starter 4.x rejects application supplied Content-Length, Transfer-Encoding,
Connection, Expect, and Host. Reactor Netty owns framing and authority headers.
Overrides fail before network I/O.
