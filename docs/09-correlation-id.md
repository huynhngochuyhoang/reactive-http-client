# Correlation ID Propagation

The starter automatically captures an inbound `X-Correlation-Id` header from each incoming WebFlux request and forwards it on every outbound reactive HTTP client call made within the same request chain, without any extra wiring in application code.

---

## How it works

1. **`CorrelationIdWebFilter`** reads the `X-Correlation-Id` header from the inbound request, validates it, and stores it in the Reactor `Context` under the key `"correlationId"`.
2. **Outbound exchange filter** reads the correlation ID from the Reactor context on every outbound call. If not found in the context, it falls back to the configured MDC keys in order (useful for Brave/Sleuth integrations). The validated value is injected as an `X-Correlation-Id` request header.
3. **`InboundHeadersWebFilter`** additionally captures a filtered snapshot of all inbound request headers into the Reactor context so they can appear in exchange log output.

Both filters are auto-registered when Spring WebFlux is present on the classpath. No explicit bean declaration is required.

---

## Configuration

```yaml
reactive:
  http:
    correlation-id:
      max-length: 128                            # reject values longer than this
      mdc-keys: [correlationId, X-Correlation-Id, traceId]   # MDC fallback order
```

| Property | Default | Description |
|---|---|---|
| `max-length` | `128` | Upper bound on accepted correlation ID value length |
| `mdc-keys` | `[correlationId, X-Correlation-Id, traceId]` | MDC key lookup order used when no ID is found in the Reactor context |

---

## Validation rules

Values that fail validation are silently dropped (logged at DEBUG):

- Blank or empty string
- Length exceeds `max-length`
- Contains control characters (CR, LF, or any ISO control character)

This prevents header-injection attacks from propagating crafted values downstream.

---

## MDC fallback

When no correlation ID is in the Reactor context (e.g. calls not originating from a WebFlux request handler, or integrations using Brave/Sleuth that populate MDC instead), the outbound filter consults the `mdc-keys` list in order and forwards the first non-blank value it finds.

To add Zipkin or Jaeger trace IDs as fallback keys:

```yaml
reactive:
  http:
    correlation-id:
      mdc-keys: [correlationId, X-Correlation-Id, traceId, X-B3-TraceId, uber-trace-id]
```

Set `mdc-keys` to an empty list to disable MDC fallback entirely:

```yaml
reactive:
  http:
    correlation-id:
      mdc-keys: []
```

---

## Existing header preserved

If the outbound request already carries an `X-Correlation-Id` header (set explicitly by application code), the filter leaves it untouched and does not override it.

---

## Inbound headers snapshot

`InboundHeadersWebFilter` stores a filtered snapshot of all inbound request headers in the Reactor context under the key `"inboundHeaders"`. This snapshot is used by `DefaultHttpExchangeLogger` to include inbound context in log output.

The snapshot is filtered through an allow-list / deny-list before being stored:

```yaml
reactive:
  http:
    inbound-headers:
      allow-list: [X-Request-Id, X-User-Id]   # capture only these (empty = capture all)
      deny-list:  [Authorization, Cookie, Set-Cookie, Proxy-Authorization, X-Api-Key]
```

| Behaviour | Description |
|---|---|
| `allow-list` non-empty | Only headers matching the allow-list are captured |
| `deny-list` match | Header value is replaced with `[REDACTED]` |
| Default allow-list | Empty — capture all headers |
| Default deny-list | `authorization`, `cookie`, `set-cookie`, `proxy-authorization`, `x-api-key` |

Matching is case-insensitive. Captured snapshots preserve the original inbound header casing, while denied values are replaced with `[REDACTED]` before the snapshot is stored. The stored snapshot is an immutable defensive copy, so later request-header mutation cannot change what loggers or async handoff code observe. Sensitive headers are never stored or logged by default.

---

## Async boundaries and sinks

Reactor `Context` is scoped to a subscription chain. Values captured by
`CorrelationIdWebFilter` and `InboundHeadersWebFilter` are visible to outbound
client calls made in the same chain, but they are not automatically carried when
application code emits an event into `Sinks.Many`, a queue, or another callback.
A sink subscriber sees its own subscriber context, not the emitter context.

Use `RequestContextSnapshot` when an application needs to carry starter-owned
request context through an explicit event envelope:

```java
record EventEnvelope<T>(T payload, RequestContextSnapshot context) {}

Mono<Void> publish(OrderCreated event) {
    return Mono.deferContextual(ctx -> {
        sink.tryEmitNext(new EventEnvelope<>(event, RequestContextSnapshot.capture(ctx)));
        return Mono.empty();
    });
}

Flux<Void> consume() {
    return sink.asFlux()
            .flatMap(envelope -> downstreamClient.send(envelope.payload())
                    .contextWrite(envelope.context()::writeTo));
}
```

`RequestContextSnapshot` currently carries the starter-owned correlation ID and
filtered inbound header snapshot. Empty contexts produce an empty snapshot, and
restoring an empty snapshot is a no-op. The snapshot remains independent of any
specific sink, queue, or broker library.
