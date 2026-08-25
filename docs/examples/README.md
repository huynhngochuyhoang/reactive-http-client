# Examples

These examples are documentation snippets, not a compiled sample module. They are
kept small so each block can be copied into an application and adapted to local
package names, credentials, and upstream URLs.

## Effective Configuration

See [Effective Configuration Examples](effective-configuration.md) for metadata-validated starter configuration snippets covering inherited clients, auth, proxy/TLS, redirects, strict retry, strict body signing, and diagnostics.
The V27 section also covers explicitly selected local caching, finite TTL and
capacity, single flight, bounded refresh, key variants, and cache telemetry.

## Production Policy

See [Production Policy Example](production-policy.md) for a compact metadata-validated policy example covering inherited clients, per-client timeouts, OAuth2, strict retry, strict SigV4 body signing, diagnostics, and support-bundle capture.

## OAuth2 Client Credentials

```yaml
reactive:
  http:
    clients:
      user-service:
        base-url: https://api.example.invalid
        auth:
          type: oauth2-client-credentials
          oauth2-client-credentials:
            token-uri: https://auth.example.invalid/oauth/token
            client-id: user-service
            client-secret: ${USER_SERVICE_CLIENT_SECRET}
```

See [Outbound Auth Providers](../06-auth-providers.md).

## Resilience4j

```yaml
reactive:
  http:
    clients:
      user-service:
        base-url: https://api.example.invalid
        resilience:
          enabled: true
          retry: user-service
          rate-limiter: user-service
          circuit-breaker: user-service
          bulkhead: user-service

resilience4j:
  retry:
    instances:
      user-service:
        max-attempts: 3
        wait-duration: 200ms
  ratelimiter:
    instances:
      user-service:
        limit-for-period: 50
        limit-refresh-period: 1s
        timeout-duration: 0
  circuitbreaker:
    instances:
      user-service:
        sliding-window-size: 20
        failure-rate-threshold: 50
  bulkhead:
    instances:
      user-service:
        max-concurrent-calls: 25
```

See [Resilience4j Integration](../07-resilience4j.md).

## OpenTelemetry Propagation

```yaml
reactive:
  http:
    observability:
      otel:
        enabled: true
        spans:
          enabled: true
        propagation:
          enabled: true
```

With the `reactive-http-client-otel` module on the classpath and an
`OpenTelemetry` bean in the context, inbound `traceparent` and `baggage` values
are extracted into Reactor context and injected into outbound client calls.

See [Observability](../08-observability.md).

## Multipart Upload

```java
@ReactiveHttpClient(name = "asset-service")
interface AssetClient {

    @POST("/assets")
    @MultipartBody
    Mono<AssetResponse> upload(
            @FormField("owner") String owner,
            @FormFile("file") FileAttachment file
    );
}
```

See [Multipart Uploads](../10-multipart.md).

## Streaming Response

```java
@ReactiveHttpClient(name = "download-service")
interface DownloadClient {

    @GET("/exports/{id}")
    Flux<DataBuffer> download(@PathVar("id") String id);
}
```

See [Streaming Requests and Responses](../11-streaming.md).

## Test Helper Without A Live Server

```java
MockReactiveHttpClient<UserClient> mock =
        MockReactiveHttpClient.create(UserClient.class);

mock.when("GET", "/users/42")
        .thenReturnJson(200, """
                {"id":42,"name":"Ada"}
                """);

StepVerifier.create(mock.proxy().getUser(42))
        .expectNextMatches(user -> user.id() == 42)
        .verifyComplete();
```

See [Test Helpers](../14-test-helpers.md).
