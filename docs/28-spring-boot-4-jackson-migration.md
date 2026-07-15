# Spring Boot 4 and Starter 3.x Migration

This guide migrates published Spring Boot 3.5 starter 2.x applications to the
Spring Boot 4 starter 3.x line. The current reactor version is `3.0.0`; use the
latest published `3.x` version when consuming it from a release repository.

Annotations, exception categories, lifecycle hooks, observers, retry and
idempotency behavior, diagnostics sanitization, and reactive.http property names
remain unchanged. Review the
[2.14.1 to 3.0.0 API Report](api-report-2.14.1-to-3.0.0.md).

## Choose the release lane

Upgrade to starter `3.x` only when the application can move to Spring Boot 4,
Java 21, Jackson 3, and the Boot 4 Resilience4j integration. Stay on published
starter `2.14.1` when the application must remain on Boot 3.5, still calls the
deprecated Jackson 2 constructors or mock `objectMapper(...)` adapter, or cannot
yet update custom Boot WebClient/health imports. The `2.x` lane is limited to
security and critical correctness maintenance; new migration work belongs on
`3.x`.

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

Boot 4 and starter 3.x:

```xml
<parent>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-parent</artifactId>
  <version>4.0.0</version>
  <relativePath/>
</parent>
<properties>
  <java.version>21</java.version>
  <reactive-http-client.version>3.0.0</reactive-http-client.version>
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

## Package and module changes

| Contract | Boot 3.5 / 2.x | Boot 4 / 3.x | Action |
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

Boot 4 and starter 3.x use Jackson 3 plus the stable codec boundary:

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
they migrate. The `3.x` starter and test helper do not depend on Jackson 2.

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
        base-url: https://orders.example.test
        request-timeout-ms: 3000
        resilience:
          enabled: true
          retry-methods: [GET, HEAD]
        auth:
          type: oauth2-client-credentials
          oauth2-client-credentials:
            token-uri: https://identity.example.test/oauth/token
            client-id: ${ORDERS_CLIENT_ID}
            client-secret: ${ORDERS_CLIENT_SECRET}
    observability:
      enabled: true
      diagnostics-endpoint:
        enabled: true
```

After, Boot 4 and starter 3.x; configuration is unchanged:

```yaml
reactive:
  http:
    clients:
      orders:
        base-url: https://orders.example.test
        request-timeout-ms: 3000
        resilience:
          enabled: true
          retry-methods: [GET, HEAD]
        auth:
          type: oauth2-client-credentials
          oauth2-client-credentials:
            token-uri: https://identity.example.test/oauth/token
            client-id: ${ORDERS_CLIENT_ID}
            client-secret: ${ORDERS_CLIENT_SECRET}
    observability:
      enabled: true
      diagnostics-endpoint:
        enabled: true
```

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
do not reuse a Boot 3 native configuration for the `3.x` artifact.

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

## Independent Boot 4 consumer

`.github/boot4-consumer` is a non-reactor Boot 4 application fixture. It compiles
and runs inherited generic and `@ApiRef` clients, Problem Detail, redirects,
streaming ownership, timeout diagnostics, health, Micrometer, OTel, and strict
retry against assembled starter artifacts. Run it against the current installed
candidate with:

```bash
PROJECT_VERSION=$(mvn -q -DforceStdout help:evaluate -Dexpression=project.version)
mvn -B -ntp -s .mvn/maven-central-settings.xml \
  -Dmaven.javadoc.skip=true install
mvn -B -ntp -s .mvn/maven-central-settings.xml \
  -f .github/boot4-consumer/pom.xml \
  -Dreactive-http-client.version="$PROJECT_VERSION" test
```

Release validation uses `scripts/verify-publishable-artifacts.sh` after signed
artifacts are built. That script deploys the parent, starter, test helper, and
OTel module to a target-local staging repository, runs this same consumer from
an empty local repository, and rejects resolution from reactor class directories
or a pre-existing Maven cache.

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

Starter 3.x rejects application supplied Content-Length, Transfer-Encoding,
Connection, Expect, and Host. Reactor Netty owns framing and authority headers.
Overrides fail before network I/O.
