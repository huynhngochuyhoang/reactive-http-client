# reactive-http-client

[![Maven Central](https://img.shields.io/maven-central/v/io.github.huynhngochuyhoang/reactive-http-client-starter.svg)](https://search.maven.org/artifact/io.github.huynhngochuyhoang/reactive-http-client-starter)
[![CI](https://github.com/huynhngochuyhoang/reactive-http-client/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/huynhngochuyhoang/reactive-http-client/actions/workflows/ci.yml)

`reactive-http-client` is a Spring Boot starter for building declarative WebFlux HTTP clients.

It lets you define an HTTP client as a Java interface, then handles the common production concerns around it: configuration, timeouts, errors, auth, resilience, observability, proxy/TLS, and test helpers.

Use it when your service calls other HTTP services and you want one consistent way to declare, configure, observe, and test those outbound calls.

---

## What You Build

Instead of writing `WebClient` boilerplate for every downstream service, you write an interface:

```java
@ReactiveHttpClient(name = "user-service")
public interface UserApiClient {

    @GET("/users/{id}")
    @TimeoutMs(5000)
    Mono<UserDto> getUser(@PathVar("id") String id);

    @POST("/users")
    Mono<UserDto> createUser(@Body CreateUserRequest body);
}
```

Then inject and use it:

```java
@Service
class UserService {
    private final UserApiClient users;

    UserService(UserApiClient users) {
        this.users = users;
    }

    Mono<UserDto> getUser(String id) {
        return users.getUser(id);
    }
}
```

---

## What It Provides

| Area | Built in |
|---|---|
| Declarative clients | `@ReactiveHttpClient`, HTTP verb annotations, `@Body`, `@PathVar`, `@QueryParam`, headers |
| Configuration | Per-client config under `reactive.http.clients.<name>` |
| Timeouts | Connect timeout, network read/write safety nets, per-request `@TimeoutMs` |
| Errors | Consistent exception model and error categories |
| Auth | Per-client `AuthProvider`, OAuth2 client credentials, AWS SigV4 |
| Resilience | Optional Resilience4j retry, circuit breaker, bulkhead, rate limiter |
| Observability | Micrometer metrics, health indicator, OpenTelemetry module |
| Network | Connection pool tuning, HTTP/2 opt-in, HTTP proxy, TLS/mTLS |
| Testing | `MockReactiveHttpClient`, JUnit 5 `@MockHttpServer`, error assertions |

---

## Install

Requirements:

- Java 21+
- Spring Boot 4.x
- Maven 3.8+

Add the starter:

```xml
<dependency>
  <groupId>io.github.huynhngochuyhoang</groupId>
  <artifactId>reactive-http-client-starter</artifactId>
  <version>3.0.0</version>
</dependency>
```

WebFlux applications should also include:

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-webflux</artifactId>
</dependency>
```

Enable client scanning:

```java
@SpringBootApplication
@EnableReactiveHttpClients(basePackages = "com.myapp.client")
public class MyApp {
    public static void main(String[] args) {
        SpringApplication.run(MyApp.class, args);
    }
}
```

---

## Basic Configuration

```yaml
reactive:
  http:
    network:
      connect-timeout-ms: 2000
      network-read-timeout-ms: 60000
      network-write-timeout-ms: 60000
      connection-pool:
        max-connections: 200
        pending-acquire-timeout-ms: 5000
    clients:
      user-service:
        base-url: https://api.example.com
        codec-max-in-memory-size-mb: 2
        compression-enabled: false
        http2-enabled: false
        log-exchange: false
        log-preset: metadata-only
        request-timeout-ms: 0
        resilience:
          enabled: true
          retry: user-service
          circuit-breaker: user-service
```

`log-exchange` is the 2.0+ property for client-wide exchange logging. The old `log-body` compatibility property was removed in `2.0.0`.

---

## Optional Modules

### Test helpers

```xml
<dependency>
  <groupId>io.github.huynhngochuyhoang</groupId>
  <artifactId>reactive-http-client-test</artifactId>
  <version>3.0.0</version>
  <scope>test</scope>
</dependency>
```

```java
class UserApiClientTest {

    @MockHttpServer
    MockReactiveHttpClient<UserApiClient> mock;

    @Test
    void getsUser() {
        mock.respondTo(HttpMethod.GET, "/users/42",
                ex -> MockReactiveHttpClient.json(200, "{\"id\":42}"));

        StepVerifier.create(mock.proxy().getUser("42"))
                .expectNextMatches(user -> user.id() == 42)
                .verifyComplete();
    }
}
```

### OpenTelemetry

```xml
<dependency>
  <groupId>io.github.huynhngochuyhoang</groupId>
  <artifactId>reactive-http-client-otel</artifactId>
  <version>3.0.0</version>
</dependency>
```

---

## When To Use This

Use this starter when:

- Your service owns multiple outbound HTTP clients.
- You want one property model for base URLs, timeouts, auth, pool, proxy, TLS, and resilience.
- You want consistent error handling and observability across those clients.
- You want lightweight tests without starting real HTTP servers.

Use plain Spring `@HttpExchange` or direct `WebClient` when:

- You only need a thin declarative wrapper.
- You prefer to wire auth, resilience, observability, and testing yourself.
- You need very custom request behavior that does not fit the annotation model.

---

## Guides

| Guide | Topic |
|---|---|
| [Quick Start](docs/01-quick-start.md) | Full setup walkthrough |
| [Annotation Reference](docs/02-annotations.md) | Supported annotations |
| [Error Handling](docs/03-error-handling.md) | Exceptions and categories |
| [Timeouts](docs/04-timeouts.md) | Timeout layers and precedence |
| [Connection Pool](docs/05-connection-pool.md) | Pool tuning and metrics |
| [Outbound Auth Providers](docs/06-auth-providers.md) | OAuth2, AWS SigV4, custom auth |
| [Resilience4j Integration](docs/07-resilience4j.md) | Retry, circuit breaker, bulkhead, rate limiter |
| [Observability](docs/08-observability.md) | Micrometer and OpenTelemetry |
| [Correlation ID](docs/09-correlation-id.md) | Header propagation |
| [Multipart Uploads](docs/10-multipart.md) | Multipart and form-data |
| [Streaming Responses](docs/11-streaming.md) | Streaming response bodies |
| [Proxy & TLS / mTLS](docs/12-proxy-tls.md) | Proxy and custom TLS |
| [Exchange Logging](docs/13-exchange-logging.md) | Request/response logging |
| [Test Helpers](docs/14-test-helpers.md) | Mock clients and assertions |
| [Per-Client Customizer](docs/15-customizer.md) | Custom WebClient filters |
| [Production Checklist](docs/16-production-checklist.md) | Production readiness checks |
| [Migration from WebClient](docs/17-migration-from-webclient.md) | Migration examples |
| [Conflict and Cardinality Guardrails](docs/18-conflict-cardinality-guardrails.md) | Precedence and safe observability defaults |
| [Lifecycle Hooks](docs/19-lifecycle-hooks.md) | Ordered invocation callbacks |
| [Native Image and Release Compatibility](docs/20-native-release-compatibility.md) | AOT support and smoke matrix |
| [Diagnostic Context Contracts](docs/21-diagnostic-contexts.md) | Extension-point capability matrix |
| [Benchmarks](docs/22-benchmarks.md) | Benchmark harness and commands |
| [Benchmark Report 2.12.0](docs/benchmark-report-2.12.0.md) | Promoted release-quality benchmark report |
| [Performance Summary](docs/23-performance-summary.md) | Interpreting benchmark data |
| [Production Support Bundles](docs/26-support-bundles.md) | Safe incident evidence examples |
| [V16 to V17 Adoption Guide](docs/27-v16-to-v17-adoption.md) | Diagnostics-first strict validation rollout |
| [Spring Boot 4 and Starter 3.x Migration](docs/28-spring-boot-4-jackson-migration.md) | Complete dependency, package, configuration, Jackson, Actuator, native, and test-helper migration |
| [2.14.0 to 3.0.0 Candidate API Report](docs/api-report-2.14.0-to-3.0.0-candidate.md) | Reviewed cross-major public API diff |
| [V19 3.0.0 Release Decision](docs/29-v19-release-decision.md) | Evidence-backed no-go decision and remaining release blockers |
| [Configuration Properties](docs/configuration-properties.md) | Generated property reference |
| [Examples](docs/examples/README.md) | Copy-paste snippets |

---

## Build

```bash
mvn test
```

Modules:

- `reactive-http-client-starter`: core Spring Boot starter
- `reactive-http-client-test`: test helper artifact
- `reactive-http-client-otel`: OpenTelemetry integration

---

## License

Apache License 2.0.
