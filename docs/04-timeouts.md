# Timeouts

The starter has **two independent timeout layers** that act on every outbound call. Understanding the difference is critical to avoiding hard-to-debug incidents.

---

## Timeout layers

| Layer | Property / annotation | Default | Scope | Fires when |
|---|---|---|---|---|
| TCP connect timeout | `reactive.http.network.connect-timeout-ms` | 2 000 ms | TCP handshake only | A new connection cannot be established within the limit |
| Per-request response timeout | `@TimeoutMs(ms)` (method), `@ApiRef timeout-ms`, or `request-timeout-ms` (client) | disabled | Per attempt | An attempt produces no response within the limit; retries each get their own budget |
| Safety-net read timeout | `reactive.http.network.network-read-timeout-ms` | 60 000 ms | Per pooled connection | No inbound bytes for this duration — catches stuck sockets |
| Safety-net write timeout | `reactive.http.network.network-write-timeout-ms` | 60 000 ms | Per pooled connection | No outbound bytes accepted for this duration |

---

## Precedence rules

1. `@TimeoutMs(ms)` on the method takes highest precedence.
2. `@ApiRef timeout-ms` applies to API-map methods when no method annotation is present.
3. `request-timeout-ms` on the client config applies when neither method nor API-map timeout is present.
4. Deprecated `resilience.timeout-ms` is accepted as a compatibility alias only when `request-timeout-ms` is not configured.
5. Safety-net timeouts (`network-read-timeout-ms` / `network-write-timeout-ms`) are independent of the per-request timeout and act as absolute upper bounds on socket inactivity.

**Rule of thumb:** set the safety-net timeouts well above the largest `@TimeoutMs`, `@ApiRef timeout-ms`, or `request-timeout-ms` you use. This ensures the per-request timeout always fires first, so retries behave predictably. If the safety net fires instead, no retry is attempted — the socket is dropped.

---

## Disabling the per-request timeout for one method

`@TimeoutMs(0)` explicitly disables the per-request timeout for a single method without affecting any other method or the safety-net timeouts:

```java
@POST("/batch-import")
@TimeoutMs(0)
Mono<ImportReceipt> batchImport(@Body ImportRequest request);
```

---

## Configuration example

```yaml
reactive:
  http:
    network:
      connect-timeout-ms: 2000
      network-read-timeout-ms: 60000   # safety net — set above all business timeouts
      network-write-timeout-ms: 60000
    clients:
      user-service:
        base-url: https://api.example.com
        request-timeout-ms: 5000   # per-request default for this client
```

```java
@ReactiveHttpClient(name = "user-service")
public interface UserApiClient {

    @GET("/users/{id}")
    @TimeoutMs(3000)      // overrides request-timeout-ms for this method
    Mono<User> getUser(@PathVar("id") long id);

    @POST("/users")       // inherits request-timeout-ms = 5000
    Mono<User> createUser(@Body NewUser body);

    @GET("/users/export")
    @TimeoutMs(0)         // no per-request timeout for long exports
    Flux<User> exportAll();
}
```

---

## Deprecated property aliases

The legacy network property names `read-timeout-ms` and `write-timeout-ms` still bind for backwards compatibility but are deprecated. IDEs will flag them. Prefer `network-read-timeout-ms` and `network-write-timeout-ms`.

```yaml
# deprecated — will be removed in a future major release
reactive.http.network.read-timeout-ms: 30000

# preferred
reactive.http.network.network-read-timeout-ms: 30000
```

## Deprecated request-timeout alias

`reactive.http.clients.<name>.resilience.timeout-ms` still binds for one compatibility cycle, but new configuration should use `request-timeout-ms`. When both are configured, `request-timeout-ms` wins, including `0` to disable the per-request timeout.

```yaml
reactive:
  http:
    clients:
      user-service:
        request-timeout-ms: 5000
```
