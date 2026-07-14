# Spring Boot 4 and Starter 3.x Migration

This guide migrates published Spring Boot 3.5 starter 2.x applications to the
Spring Boot 4 starter 3.x line. The current reactor version is `3.0.0`; use the
latest published `3.x` version when consuming it from a release repository.

Annotations, exception categories, lifecycle hooks, observers, retry and
idempotency behavior, diagnostics sanitization, and reactive.http property names
remain unchanged. Review the
[2.14.1 to 3.0.0 API Report](api-report-2.14.1-to-3.0.0.md).

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

The health type replacement is the only reviewed binary and source
incompatibility. Boot 4 moved the health API and changed the health method
return type. No unrelated public removal is accepted.

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

Replace these deprecated Jackson 2 migration shims:

- ProblemDetailErrorResponseMapper(com.fasterxml.jackson.databind.ObjectMapper)
- MockReactiveHttpClient.Builder.objectMapper(...)
- ReactiveClientInvocationHandler constructors accepting Jackson 2

Use the codec constructor, MockReactiveHttpClient.Builder.jsonCodec(...), and
codec-based handler constructors. `Jackson2ReactiveHttpClientJsonCodec` remains for Boot 3 source migration. Jackson 2
also remains a transitive dependency while these deprecated public signatures
exist; removing that dependency requires removing or hiding those signatures.

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

The endpoint ID remains rhttpclients. Custom health code uses the Boot 4
org.springframework.boot.health.contributor package. The V19 native baseline
uses GraalVM Java 25 while source remains Java 21 and covers loopback HTTP,
inherited generics, Problem Detail, auth, Micrometer, diagnostics, and health.
See [Native Image and Release Compatibility](20-native-release-compatibility.md).

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

## Migration checklist

1. Upgrade to Boot 4 and Java 21.
2. Replace Boot WebClientCustomizer and health imports.
3. Move application JSON modules and configuration to Jackson 3.
4. Replace mapper-based starter calls with ReactiveHttpClientJsonCodec.
5. Move Resilience4j users to its Boot 4 line.
6. Run configuration, Problem Detail, auth, lifecycle, retry, streaming, and
   test-helper suites.
7. Run AOT and native verification for native applications.

## Transport header ownership

Starter 3.x rejects application supplied Content-Length, Transfer-Encoding,
Connection, Expect, and Host. Reactor Netty owns framing and authority headers.
Overrides fail before network I/O.
