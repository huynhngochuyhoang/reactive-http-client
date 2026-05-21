# Quick Start

## Requirements

- Java 21+
- Spring Boot 3.x
- Maven 3.8+

## Add the dependency

```xml
<dependency>
  <groupId>io.github.huynhngochuyhoang</groupId>
  <artifactId>reactive-http-client-starter</artifactId>
  <version>2.3.0</version>
</dependency>
```

In WebFlux applications also include:

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-webflux</artifactId>
</dependency>
```

## Enable client scanning

Add `@EnableReactiveHttpClients` to your application class and point it at the package(s) that contain your client interfaces:

```java
@SpringBootApplication
@EnableReactiveHttpClients(basePackages = "com.myapp.client")
public class MyApp {
    public static void main(String[] args) {
        SpringApplication.run(MyApp.class, args);
    }
}
```

You can list multiple packages or use `basePackageClasses` to reference a marker class instead.

## Define a client interface

```java
@ReactiveHttpClient(name = "user-service")
public interface UserApiClient {

    @GET("/users/{id}")
    @ApiName("user.getById")
    @TimeoutMs(5000)
    Mono<UserDto> getUser(
            @PathVar("id") String id,
            @QueryParam("expand") String expand
    );

    @POST("/users")
    Mono<UserDto> createUser(
            @Body CreateUserRequest body,
            @HeaderParam("X-Tenant") String tenant
    );
}
```

The `name` must match a key in `reactive.http.clients` so the starter knows which `base-url` to use.

## Optional: dynamic API map with `@ApiRef`

If you want to keep request method/path/timeout in configuration instead of annotations, use `@ApiRef`:

```java
@ReactiveHttpClient(name = "user-service")
public interface UserApiClient {
    @ApiRef("user-get-by-id")
    Mono<UserDto> getUser(@PathVar("id") String id);
}
```

```yaml
reactive:
  http:
    clients:
      user-service:
        base-url: https://api.example.com
        apis:
          user-get-by-id:
            method: GET
            path: /users/{id}
            timeout-ms: 5000
```

Prefer `-` in API keys (for example `user-get-by-id`) so both YAML and `.properties` stay simple.
If you keep dots in API keys, use bracket notation in `.properties`:

```properties
reactive.http.clients.user-service.apis[user.getById].method=GET
reactive.http.clients.user-service.apis[user.getById].path=/users/{id}
reactive.http.clients.user-service.apis[user.getById].timeout-ms=5000
```

Add quotes around bracket notation in `.yaml` to avoid parsing errors:

```yaml
reactive:
  http:
    clients:
      user-service:
        base-url: https://api.example.com
        apis:
          "[user.getById]":
            method: GET
            path: /users/{id}
            timeout-ms: 5000
```

## Minimal `application.yml`

```yaml
reactive:
  http:
    clients:
      user-service:
        base-url: https://api.example.com
        default-headers:
          X-Client: my-service
          X-Tenant: public
        default-query-params:
          locale: [en-US]
          include: [summary, links]
```

`default-headers` are sent on every request for that client. A method parameter annotated with
`@HeaderParam` for the same header name overrides the configured default.

`default-query-params` are also sent on every request:

- no method query: `GET /users` becomes `/users?locale=en-US&include=summary&include=links`
- existing method query: `@QueryParam("page")` appends beside defaults
- same-name method query: `@QueryParam("locale")` replaces the configured `locale`

## Inject and use

```java
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserApiClient userApiClient;

    public Mono<UserDto> getUser(String id) {
        return userApiClient.getUser(id, null);
    }
}
```

The proxy is registered as a regular Spring bean and can be injected anywhere.

## Full configuration reference

A more complete `application.yml` showing every top-level section:

```yaml
reactive:
  http:
    correlation-id:
      max-length: 128
      mdc-keys: [correlationId, X-Correlation-Id, traceId]
    network:
      connect-timeout-ms: 2000
      network-read-timeout-ms: 60000
      network-write-timeout-ms: 60000
      connection-pool:
        max-connections: 200
        pending-acquire-timeout-ms: 5000
        max-idle-time-ms: 30000
        max-life-time-ms: 300000
        evict-in-background-ms: 60000
    clients:
      user-service:
        base-url: https://api.example.com
        auth-provider: userServiceAuthProvider
        # Or use a built-in provider:
        # auth:
        #   type: oauth2-client-credentials
        #   oauth2-client-credentials:
        #     token-uri: https://auth.example.com/oauth/token
        #     client-id: user-service
        #     client-secret: ${USER_SERVICE_CLIENT_SECRET}
        codec-max-in-memory-size-mb: 2
        compression-enabled: false
        http2-enabled: false
        default-headers:
          X-Client: my-service
        default-query-params:
          locale: [en-US]
        log-exchange: false
        log-preset: metadata-only
        request-timeout-ms: 0
        resilience:
          enabled: true
          circuit-breaker: user-service
          retry: user-service
          retry-methods: [GET, HEAD]
          rate-limiter: user-service
          bulkhead: user-service
    observability:
      enabled: true
      metric-name: reactive.http.client.requests
      include-url-path: false
      include-server-address: false
      log-request-body: false
      log-response-body: false
```

## Next steps

| Topic | Guide |
|---|---|
| All annotations | [02-annotations.md](02-annotations.md) |
| Error handling | [03-error-handling.md](03-error-handling.md) |
| Timeouts | [04-timeouts.md](04-timeouts.md) |
| Connection pool | [05-connection-pool.md](05-connection-pool.md) |
| Outbound auth | [06-auth-providers.md](06-auth-providers.md) |
| Resilience4j | [07-resilience4j.md](07-resilience4j.md) |
| Observability | [08-observability.md](08-observability.md) |
| Correlation ID | [09-correlation-id.md](09-correlation-id.md) |
| Multipart uploads | [10-multipart.md](10-multipart.md) |
| Streaming responses | [11-streaming.md](11-streaming.md) |
| Proxy & TLS/mTLS | [12-proxy-tls.md](12-proxy-tls.md) |
| Exchange logging | [13-exchange-logging.md](13-exchange-logging.md) |
| Test helpers | [14-test-helpers.md](14-test-helpers.md) |
| Conflict and cardinality guardrails | [18-conflict-cardinality-guardrails.md](18-conflict-cardinality-guardrails.md) |
| Configuration properties | [configuration-properties.md](configuration-properties.md) |
