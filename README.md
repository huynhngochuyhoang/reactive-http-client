# generic-http-client

A **Spring Boot Starter** that provides a declarative, annotation-driven HTTP client layer for Spring WebFlux (reactive) applications, with built-in **Resilience4j** support for circuit-breaking, retries, bulkheads and timeouts.

---

## Modules

| Module | Description |
|---|---|
| `reactive-http-client-starter` | Spring Boot auto-configuration, annotations and core proxy engine |
| `demo-consumer` | Spring Boot demo application that uses the starter with WireMock tests |

---

## Features

- **Declarative client interfaces** – define HTTP calls with simple annotations (`@GET`, `@POST`, `@PUT`, `@DELETE`).
- **Automatic parameter extraction** – `@PathVar`, `@QueryParam`, `@HeaderParam`, `@Body` are resolved automatically via reflection.
- **Resilience4j integration** – circuit-breaker, retry, bulkhead, and per-request timeout, configured per client in `application.yml`.
- **Auto-configuration** – register client beans with a single `@EnableReactiveHttpClients` annotation; no boilerplate `@Bean` methods.
- **Correlation ID propagation** – `X-Correlation-Id` from MDC is forwarded automatically.
- **Error decoding** – 4xx → `HttpClientException`, 5xx → `RemoteServiceException`.

---

## Quick Start: Integrating the Starter into Your Project

### 1. Add the dependency

```xml
<dependency>
    <groupId>com.acme</groupId>
    <artifactId>reactive-http-client-starter</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

> Make sure `spring-boot-starter-webflux` is also on the classpath (it usually is in a WebFlux application).

### 2. Enable client scanning

```java
@SpringBootApplication
@EnableReactiveHttpClients(basePackages = "com.myapp.client")
public class MyApp {
    public static void main(String[] args) { SpringApplication.run(MyApp.class, args); }
}
```

### 3. Define a client interface

```java
@ReactiveHttpClient(name = "user-service")
public interface UserApiClient {

    @GET("/users/{id}")
    Mono<UserDto> getUser(
        @PathVar("id") String id,
        @QueryParam("expand") String expand   // null → omitted from URL
    );

    @GET("/users")
    Flux<UserDto> listUsers(@QueryParam("role") String role);

    @POST("/users")
    Mono<UserDto> createUser(
        @Body CreateUserRequest body,
        @HeaderParam("X-Tenant") String tenant
    );

    @PUT("/users/{id}")
    Mono<UserDto> updateUser(@PathVar("id") String id, @Body UserDto body);

    @DELETE("/users/{id}")
    Mono<Void> deleteUser(@PathVar("id") String id);
}
```

### 4. Configure in `application.yml`

```yaml
acme:
  http:
    clients:
      user-service:                        # matches @ReactiveHttpClient(name = "user-service")
        base-url: https://api.example.com
        connect-timeout-ms: 2000
        read-timeout-ms: 5000
        log-body: false                    # set true to log response status (caution: PII)
        resilience:
          enabled: true
          circuit-breaker: user-service    # Resilience4j instance name
          retry: user-service
          bulkhead: user-service
          timeout-ms: 3000

# Optional: Resilience4j fine-tuning
resilience4j:
  circuitbreaker:
    instances:
      user-service:
        sliding-window-size: 20
        failure-rate-threshold: 50
        wait-duration-in-open-state: 10s
  retry:
    instances:
      user-service:
        max-attempts: 3
        wait-duration: 200ms
  bulkhead:
    instances:
      user-service:
        max-concurrent-calls: 20
```

### 5. Inject and use

```java
@Service
public class UserService {

    private final UserApiClient userApiClient;

    public UserService(UserApiClient userApiClient) {
        this.userApiClient = userApiClient;
    }

    public Mono<UserDto> getUser(String id) {
        return userApiClient.getUser(id, null);
    }
}
```

---

## Running the demo-consumer

### Prerequisites

- JDK 17+
- Maven 3.8+

### Build the entire project (from the root directory)

```bash
mvn clean install -DskipTests
```

### Run the demo application

```bash
cd demo-consumer
mvn spring-boot:run
```

The application starts on **port 8080** and is pre-configured to talk to `https://jsonplaceholder.typicode.com` as the upstream `user-service`.

### Run the tests (WireMock)

```bash
mvn test
```

The integration tests in `UserApiClientWireMockTest` spin up a WireMock server on a random port, override `acme.http.clients.user-service.base-url` via `@DynamicPropertySource`, and verify the full request/response cycle using `StepVerifier`.

---

## Annotation Reference

| Annotation | Target | Description |
|---|---|---|
| `@ReactiveHttpClient(name, baseUrl)` | Interface | Declares a reactive HTTP client interface |
| `@GET(path)` | Method | HTTP GET |
| `@POST(path)` | Method | HTTP POST |
| `@PUT(path)` | Method | HTTP PUT |
| `@DELETE(path)` | Method | HTTP DELETE |
| `@PathVar(name)` | Parameter | Substituted into the path template |
| `@QueryParam(name)` | Parameter | Appended to the query string (null → omitted) |
| `@HeaderParam(name)` | Parameter | Added as a request header (null → omitted) |
| `@Body` | Parameter | Serialised as the JSON request body |

---

## Error Handling

| HTTP Status | Exception | Description |
|---|---|---|
| 4xx | `HttpClientException` | Contains `statusCode` and `responseBody` |
| 5xx | `RemoteServiceException` | Contains `statusCode` and `responseBody` |

```java
userApiClient.getUser("unknown", null)
    .onErrorResume(HttpClientException.class, ex -> {
        log.warn("Client error {}: {}", ex.getStatusCode(), ex.getResponseBody());
        return Mono.empty();
    });
```

---

## Project Structure

```text
generic-http-client/
├── pom.xml                                          # root multi-module POM
├── reactive-http-client-starter/
│   ├── pom.xml
│   └── src/main/java/com/acme/httpstarter/
│       ├── annotation/       # @ReactiveHttpClient, @GET, @POST, @PUT, @DELETE,
│       │                     #   @PathVar, @QueryParam, @HeaderParam, @Body
│       ├── enable/           # @EnableReactiveHttpClients
│       ├── config/           # AutoConfiguration, Properties, Registrar
│       ├── core/             # FactoryBean, InvocationHandler, MetadataCache,
│       │                     #   ArgumentResolver, UriTemplateExpander, ErrorDecoder
│       └── exception/        # HttpClientException, RemoteServiceException
└── demo-consumer/
    ├── pom.xml
    └── src/
        ├── main/java/com/acme/demo/
        │   ├── DemoConsumerApplication.java
        │   ├── client/UserApiClient.java
        │   ├── dto/           # UserDto, CreateUserRequest
        │   └── service/       # UserService
        └── test/java/com/acme/demo/
            └── UserApiClientWireMockTest.java
```
