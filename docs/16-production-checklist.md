# Production Checklist

Use this checklist before putting a `@ReactiveHttpClient` client on a production path.

## Client contract

- Define one Java interface per downstream service.
- Set `@ReactiveHttpClient(name = "...")` to match `reactive.http.clients.<name>`.
- Keep method paths, HTTP verbs, path variables, query parameters, and body types explicit.
- Use `@ApiRef` only when method/path/timeout must be controlled from configuration.

## Timeouts and pool

- Set `reactive.http.network.connect-timeout-ms`.
- Keep `network-read-timeout-ms` and `network-write-timeout-ms` above normal request timeouts.
- Use `@TimeoutMs` or `reactive.http.clients.<name>.request-timeout-ms` for per-request response limits.
- Tune `connection-pool.max-connections` and `pending-acquire-timeout-ms` for expected concurrency.
- Use per-client `pool` blocks only when one client needs different limits.

## Protocol and network

- Leave `http2-enabled: false` unless the downstream supports HTTP/2.
- Enable HTTP/2 per client:

```yaml
reactive:
  http:
    clients:
      inventory:
        base-url: https://inventory.example.com
        http2-enabled: true
```

- Configure proxy and TLS globally when most clients share the same network path.
- Use per-client proxy/TLS blocks when one downstream has different routing or trust material.
- Never use `tls.insecure-trust-all: true` outside local development.

## Auth and request defaults

- Prefer an `AuthProvider` for credentials that rotate or require signing.
- Use built-in `auth` blocks for OAuth2 client credentials or AWS SigV4.
- Do not configure both `auth-provider` and `auth.type` unless you intentionally want the bean to win.
- Use `default-headers` only for static non-secret headers.
- Use `default-query-params` only for stable parameters that belong on every request.

## Resilience

- Enable resilience per client only after choosing the Resilience4j instances:

```yaml
reactive:
  http:
    clients:
      inventory:
        resilience:
          enabled: true
          retry: inventory
          circuit-breaker: inventory
          bulkhead: inventory
          rate-limiter: inventory
          retry-methods: [GET, HEAD]
```

- Keep retries limited to idempotent methods unless the downstream contract is explicitly safe.
- Use bulkheads and rate limiters for shared downstreams that can overload callers.

## Observability

- Keep `reactive.http.observability.enabled: true`.
- Review metric tags before enabling high-cardinality fields such as resolved server address.
- Use the published error categories from [Error Handling](03-error-handling.md) in alerts and dashboards.
- Enable OpenTelemetry with the `reactive-http-client-otel` module when traces are required.

## Logging and testing

- Keep exchange body logging disabled unless a production incident requires it.
- Keep `log-preset: metadata-only` by default; use `headers` or `bodies` only for targeted investigations.
- Redact sensitive headers in inbound snapshots and exchange logs.
- Cover client behavior with `reactive-http-client-test` mocks for status mapping, headers, query params, and auth.
- Run `mvn test` before release.
