# Migration from WebClient

This guide shows when and how to move outbound HTTP calls to `@ReactiveHttpClient`.

## When to migrate

Migrate when the same client needs shared configuration, auth, resilience, observability, and test helpers.

Keep direct `WebClient` when the call is one-off, highly dynamic, or uses request behavior that does not fit the annotation model.

## From raw `WebClient`

Before:

```java
@Service
class UserGateway {
    private final WebClient webClient;

    UserGateway(WebClient.Builder builder) {
        this.webClient = builder
                .baseUrl("https://users.example.invalid")
                .defaultHeader("X-Client", "orders")
                .build();
    }

    Mono<UserDto> getUser(String id) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/users/{id}")
                        .queryParam("expand", "summary")
                        .build(id))
                .retrieve()
                .bodyToMono(UserDto.class);
    }
}
```

After:

```java
@ReactiveHttpClient(name = "users")
public interface UserClient {

    @GET("/users/{id}")
    Mono<UserDto> getUser(
            @PathVar("id") String id,
            @QueryParam("expand") String expand
    );
}
```

```yaml
reactive:
  http:
    clients:
      users:
        base-url: https://users.example.invalid
        default-headers:
          X-Client: orders
```

The service injects `UserClient` directly:

```java
@Service
class UserGateway {
    private final UserClient users;

    UserGateway(UserClient users) {
        this.users = users;
    }

    Mono<UserDto> getUser(String id) {
        return users.getUser(id, "summary");
    }
}
```

## From Spring `@HttpExchange`

Before:

```java
@HttpExchange("/users")
public interface UserHttpExchangeClient {

    @GetExchange("/{id}")
    Mono<UserDto> getUser(@PathVariable String id);
}
```

```java
@Configuration
class UserHttpExchangeConfig {

    @Bean
    UserHttpExchangeClient userHttpExchangeClient(WebClient.Builder builder) {
        WebClient webClient = builder
                .baseUrl("https://users.example.invalid")
                .build();

        HttpServiceProxyFactory factory = HttpServiceProxyFactory
                .builderFor(WebClientAdapter.create(webClient))
                .build();

        return factory.createClient(UserHttpExchangeClient.class);
    }
}
```

After:

```java
@ReactiveHttpClient(name = "users")
public interface UserClient {

    @GET("/users/{id}")
    Mono<UserDto> getUser(@PathVar("id") String id);
}
```

```yaml
reactive:
  http:
    clients:
      users:
        base-url: https://users.example.invalid
        resilience:
          enabled: true
          retry: users
          circuit-breaker: users
```

No proxy factory bean is needed. Enable scanning once:

```java
@SpringBootApplication
@EnableReactiveHttpClients(basePackages = "com.myapp.client")
class MyApp {
}
```

## Migration steps

1. Create a `@ReactiveHttpClient` interface with the same downstream operations.
2. Move the base URL and shared request defaults into `reactive.http.clients.<name>`.
3. Move auth into an `AuthProvider` bean or built-in `auth` configuration.
4. Add timeout and resilience settings per client.
5. Replace service injections from `WebClient` or `@HttpExchange` client to the new interface.
6. Add tests with `MockReactiveHttpClient` for successful responses and expected error categories.
