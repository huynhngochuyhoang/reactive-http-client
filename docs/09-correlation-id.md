# Correlation ID Propagation

The starter automatically captures an inbound `X-Correlation-Id` header from each incoming WebFlux request and forwards it on every outbound reactive HTTP client call made within the same request chain, without any extra wiring in application code.

---

## How it works

1. **`CorrelationIdWebFilter`** reads the `X-Correlation-Id` header from the inbound request, validates it, and stores it in the Reactor `Context` under the key `"correlationId"`.
2. **Outbound exchange filter** reads the correlation ID from the Reactor context on every outbound call. If not found in the context, it falls back to the configured MDC keys in order (useful for Brave/Sleuth integrations). The validated value is injected as an `X-Correlation-Id` request header.
3. **`InboundHeadersWebFilter`** additionally captures a filtered snapshot of all inbound request headers into the Reactor context so they can appear in exchange log output.

Both filters are auto-registered in a reactive web application when their filter
types have not been replaced by application beans. WebFlux/WebClient on the
classpath alone does not register inbound capture in a servlet/MVC or non-web
application; the starter does not provide a servlet-to-Reactor ingress bridge.

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

## Public context contract

The starter owns these Reactor context keys and keeps the existing string keys stable for compatibility:

| Value | String key | Typed helper |
|---|---|---|
| Correlation ID | `correlationId` | `RequestContext.correlationId(ctx)` / `RequestContext.withCorrelationId(ctx, value)` |
| Filtered inbound headers | `inboundHeaders` | `RequestContext.inboundHeaders(ctx)` / `RequestContext.withInboundHeaders(ctx, headers)` |
| Outbound idempotency key | `idempotencyKey` | `RequestContext.idempotencyKey(ctx)` / `RequestContext.withIdempotencyKey(ctx, value)` |

Prefer the `RequestContext` helpers or `RequestContextSnapshot` for new code. Existing integrations that write `CorrelationIdWebFilter.CORRELATION_ID_CONTEXT_KEY` or `InboundHeadersWebFilter.INBOUND_HEADERS_CONTEXT_KEY` continue to work because those constants keep the same string values.

`RequestContext.defaultContributors()` exposes the built-in correlation ID and inbound header contributors for custom integration code that needs ordered capture/restore without referencing raw context keys. Optional integrations can pass an empty contributor list without changing behavior. Contributors restore in ascending `order()`, then by `key()`.

Correlation ID precedence is:

1. Caller-supplied outbound `X-Correlation-Id` request header.
2. Reactor context value, including a restored `RequestContextSnapshot`.
3. Configured MDC fallback keys, in order.
4. No outbound correlation header.

Idempotency key precedence is:

1. Caller-supplied outbound idempotency header from `@HeaderParam`, header map, or `@IdempotencyKey` parameter.
2. Client `default-headers`.
3. `RequestContext.withIdempotencyKey(ctx, value)`.
4. Method-level `@IdempotencyKey` generated value.
5. Later `WebClient` filters, including `ReactiveHttpClientCustomizer`, can still mutate the final request.

Method-level generation is opt-in and creates one key per invocation. The starter does not generate idempotency keys for every request and does not provide downstream idempotency storage. `RequestContextSnapshot` carries correlation ID and inbound headers; if you need an idempotency key after an async handoff, write it explicitly with `RequestContext.withIdempotencyKey(...)` when subscribing the outbound call.

Restoring a `RequestContextSnapshot` writes the values present in the snapshot into the subscriber context. If that subscriber context already contains values for the same starter-owned keys, the restored snapshot values replace them; missing snapshot values do not clear existing context values.

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

Matching is case-insensitive. Captured snapshots preserve the original inbound header casing, while denied values are replaced with `[REDACTED]` before the snapshot is stored. The stored snapshot is an immutable defensive copy, so later request-header mutation cannot change what loggers or async handoff code observe. The default deny-list protects the listed fields, not every application-specific sensitive field; review both lists for your application.

---

### WebFlux, protocol and filter boundaries

The snapshot preserves the names exposed by the WebFlux request at capture time,
not necessarily the spelling originally sent before a proxy or protocol change.
The V31 loopback tests observed lowercase HTTP/2 names and both lowercase and
mixed-case HTTP/1.1 names. On the same request, Spring `HttpHeaders.getFirst`
can find a field while exact `Map.get` using another spelling returns null.
That difference does not establish that the Reactor context was lost.

Capture has no explicit `Ordered`/`@Order` contract over application security
filters. A request mutation before capture is included; a mutation after capture
does not change the defensive snapshot. Do not reorder authentication/security
filters to recover a missing field without reviewing the application's order.
A replacement `InboundHeadersWebFilter` controls capture; an unrelated
`WebFilter` does not suppress default registration.

This ingress integration is WebFlux-only. A Spring MVC request does not populate
the starter's Reactor context automatically. There is no implicit ThreadLocal
or MDC bridge, even when an MVC controller calls a reactive outbound client.

Capturing or reading inbound fields does not forward them to outbound clients.
Correlation-ID propagation is a separate filter. Allow-list/deny-list matching
does not authorize values: an outside-allow-list field is omitted even if denied,
and a selected denied field remains `[REDACTED]`, not the original credential.

See the [V31 wire evidence](../roadmaps/v31/INBOUND-WIRE-BOUNDARIES.md) for actual
HTTP/1.1, cleartext HTTP/2 and TLS HTTP/2 protocol assertions. No normalizing
intermediary was needed for that contract. These tests do not identify which hop,
if any, changed names in a particular Istio/Envoy deployment; that diagnosis
requires deployment-specific evidence. The named readers below are versioned
separately from these existing capture/registration semantics.

---

## Published 4.3.0 named lookup workaround

On published `4.3.0`, the bulk accessor is an exact-key map, not Spring
`HttpHeaders`. Do not call `getFirst()` on a nullable `Map.get(...)` result.
For application-owned lookup of a fixed ASCII field name, collect every case
alias and reject ambiguity before parsing. This standalone example uses only
published APIs and validates the entire stored shape, including manual writes:

```java
import io.github.huynhngochuyhoang.httpstarter.core.RequestContext;
import reactor.util.context.ContextView;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class PublishedInboundHeaderExample {
    public static List<String> fixtureValues(ContextView ctx) {
        Object stored = ctx.getOrDefault(RequestContext.INBOUND_HEADERS_CONTEXT_KEY, Map.of());
        if (!(stored instanceof Map<?, ?> fields)) {
            throw new IllegalStateException("Malformed inbound context");
        }
        List<String> matches = new ArrayList<>();
        for (var entry : fields.entrySet()) {
            if (!(entry.getKey() instanceof String name)
                    || !name.matches("[!#$%&'*+.^_`|~0-9A-Za-z-]+")
                    || !(entry.getValue() instanceof List<?> values)) {
                throw new IllegalStateException("Malformed inbound context");
            }
            for (Object item : values) {
                if (!(item instanceof String value)) {
                    throw new IllegalStateException("Malformed inbound context");
                }
                if (name.equalsIgnoreCase("X-Fixture-Data")) {
                    matches.add(value);
                }
            }
        }
        return List.copyOf(matches);
    }

    public static String requiredFixtureData(ContextView ctx) {
        List<String> values = fixtureValues(ctx);
        if (values.isEmpty()) {
            throw new IllegalArgumentException("Required fixture field is absent");
        }
        if (values.size() != 1) {
            throw new IllegalStateException("Ambiguous fixture field");
        }
        String value = values.getFirst();
        if (value.isEmpty() || value.equals("[REDACTED]") || value.length() > 4096) {
            throw new IllegalArgumentException("Unusable fixture field");
        }
        return value;
    }
}
```

Read inside `Mono.deferContextual`, not at publisher assembly. The 4096 UTF-16
code-unit limit is an example application limit, not a starter default. This
example deliberately rejects empty strings and the redaction marker rather than
confusing either with absence. It does not authenticate a value: review ingress
trust and enforce decoder size/depth/schema constraints before application JSON
conversion. Do not include rejected values in exceptions or logs. An untrusted
sender can also send the literal marker, so its presence alone proves no origin.

## Named inbound header access (4.4.0 development)

The additive `4.4.0-SNAPSHOT` APIs below are not available in published `4.3.0`:

| Method | Return type |
|---|---|
| `RequestContext.inboundHeaderValues(ContextView context, String name)` | `List<String>` |
| `RequestContext.inboundHeader(ContextView context, String name)` | `Optional<String>` |

Both read only the captured `inboundHeaders` context value. Names are nonempty
ASCII HTTP field-name tokens and match independently of the default locale.
Underscores and hyphens remain distinct. The bulk `inboundHeaders(ctx)` accessor
still exposes captured spelling and exact map-key lookup.

| Captured state | `inboundHeaderValues` | `inboundHeader` |
|---|---|---|
| Missing key/name or matching empty lists | Immutable empty list | Empty optional |
| Exactly one value, including `""` or `[REDACTED]` | Value unchanged | Present value unchanged |
| Multiple values, including equal duplicates | All values unchanged | `IllegalStateException` (ambiguous) |
| Case aliases in the map | Concatenate in map-iteration then list order | Same total-value multiplicity rule |

There is no exact-case preference, deduplication, comma joining/splitting,
trimming, value normalization, parsing, or fallback credential source. Arbitrary
maps may expose an unstable iteration order; the helpers cannot recover wire
order that the map has lost. All-values results are defensive immutable copies.

Null context/name arguments throw `NullPointerException`. Invalid lookup tokens
throw `IllegalArgumentException`. Malformed stored input throws
`IllegalStateException`: the context value must be a map, every key must be a
valid field-name string, every value must be a non-null list, and every list
element must be a non-null string. Validation includes unrelated entries, even
when the requested name is absent or already has multiple values. Errors use
fixed structural messages, never header names, values or object descriptions.

Applications decide whether absence, an empty string, a redacted marker, or a
schema violation is acceptable. The candidate equivalent of the published
example is:

```java
import io.github.huynhngochuyhoang.httpstarter.core.RequestContext;
import reactor.util.context.ContextView;

public final class CandidateInboundHeaderExample {
    public static String requiredFixtureData(ContextView ctx) {
        // inboundHeader rejects multiple values, including equal duplicates.
        String value = RequestContext.inboundHeader(ctx, "X-Fixture-Data")
                .orElseThrow(() -> new IllegalArgumentException("Required fixture field is absent"));
        if (value.isEmpty() || value.equals("[REDACTED]") || value.length() > 4096) {
            throw new IllegalArgumentException("Unusable fixture field");
        }
        return value;
    }
}
```

Call `CandidateInboundHeaderExample.requiredFixtureData(ctx)` inside
`Mono.deferContextual`, then apply the same application trust/schema checks as
the published example. The helper never deserializes a header DTO or supplies
native reflection hints for application parsing. See the
[inbound-context troubleshooting path](30-operations-troubleshooting.md#inbound-header-and-context-triage)
before treating a missing field as lost context.

Manual `withInboundHeaders`, raw context writes, and custom contributors do not
run ingress allow/deny filtering or authenticate their values. The new readers
do not sanitize or mutate those inputs. A raw application map can remain mutable
even though a returned values list is immutable. The filter, defensive writer,
snapshot record constructors, public string-key aliases, and restore precedence
are unchanged: present snapshot fields replace target values; absent fields do
not clear them.

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
        sink.tryEmitNext(new EventEnvelope<>(event, RequestContextSnapshot.capture(ctx))).orThrow();
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

Composed `publishOn`, `subscribeOn` and nested publishers do not by themselves
lose Reactor context. Read or capture inside `deferContextual` at subscription
time, not when constructing a cold publisher. An executor callback that creates
an independent subscription needs an explicit envelope just like a sink consumer.

For a reused worker, restore into an isolated per-envelope target: absent
snapshot fields do **not** erase values already in that target. Keep worker-wide
context free of caller-specific values, or deliberately remove those keys on the
inner subscription before restoration:

```java
downstreamClient.send(envelope.payload())
        .contextWrite(ctx -> envelope.context().writeTo(ctx
                .delete(RequestContext.CORRELATION_ID_CONTEXT_KEY)
                .delete(RequestContext.INBOUND_HEADERS_CONTEXT_KEY)
                .delete(RequestContext.IDEMPOTENCY_KEY_CONTEXT_KEY)));
```

This example intentionally drops any worker idempotency key. Carry an application
idempotency value separately when needed, and define the same isolation rule for
application-specific context keys. Do not clear the entire Reactor context or
silently copy it into an envelope. Contributor capture/restore sorts by `order`,
then `key`; the default contributors still cover only correlation and headers.
Explicit outbound correlation/idempotency headers retain their precedence.

Finishing or cancelling a call does not empty application queues or retained
observer/logger records. Bound their lifetime and release them explicitly.
A timeout outside the starter cancels the inner call; it is not the starter's
logical-call-timeout classification. The [V31 handoff ownership report](../roadmaps/v31/ASYNC-HANDOFF-OWNERSHIP.md)
records gated concurrency and terminal/reference-release evidence, without an
immediate GC or RSS-reduction guarantee. These snapshot semantics also apply to
published `4.3.0`; the named header readers above are candidate-only additions.

### Event envelope guidance

For durable queues or long-lived broker messages, prefer explicit low-cardinality fields over a full inbound header snapshot:

```java
record OrderEventEnvelope<T>(
        T payload,
        String correlationId,
        String requestId,
        String tenantId,
        Map<String, String> traceContext) {}
```

Choose only validated fields required by the receiving side, with explicit trust, size and retention rules. Correlation/request IDs and tenant-like routing keys may themselves be sensitive or high-cardinality; they are not metric labels or safe support-bundle values. Avoid copying large, user-controlled, or sensitive header snapshots into long-lived queues.

Use the full `RequestContextSnapshot` for short-lived in-process boundaries such as `Sinks.Many`, executor callbacks, or local handoff queues where the event remains inside the process and keeps the same retention expectations as the request.

A candidate-only (`4.4.0-SNAPSHOT`) queue handoff can capture explicit fields
before enqueue and restore only the values needed before the outbound call.
On `4.3.0`, adapt the fixed-name workaround above to the selected field instead
of using the new reader. The application validates any present request ID before
enqueueing; an absent ID stays absent, not a fabricated identity:

```java
record QueueEnvelope<T>(T payload, String correlationId, String requestId) {}

Mono<Void> enqueue(OrderCreated event) {
    return Mono.deferContextual(ctx -> {
        String requestId = RequestContext.inboundHeader(ctx, "X-Request-Id").orElse(null);
        if (requestId != null && (requestId.isEmpty() || requestId.equals("[REDACTED]")
                || !requestId.matches("[A-Za-z0-9-]{1,64}"))) {
            return Mono.error(new IllegalArgumentException("Unusable request ID"));
        }
        boolean accepted = queue.offer(new QueueEnvelope<>(
                event,
                RequestContext.correlationId(ctx).orElse(null),
                requestId));
        if (!accepted) {
            return Mono.error(new IllegalStateException("Handoff queue is full"));
        }
        return Mono.empty();
    });
}

Mono<Void> handle(QueueEnvelope<OrderCreated> envelope) {
    return downstreamClient.send(envelope.payload())
            .contextWrite(ctx -> RequestContext.withCorrelationId(ctx
                    .delete(RequestContext.CORRELATION_ID_CONTEXT_KEY)
                    .delete(RequestContext.INBOUND_HEADERS_CONTEXT_KEY)
                    .delete(RequestContext.IDEMPOTENCY_KEY_CONTEXT_KEY), envelope.correlationId()));
}
```

For custom in-process integrations that cannot use a shared envelope, use the contributor SPI directly:

```java
Map<String, Object> snapshot = RequestContext.capture(ctx, RequestContext.defaultContributors());
Context restored = RequestContext.restore(Context.empty(), snapshot, RequestContext.defaultContributors());
```

Applications should still prefer explicit event fields for brokered or persisted messages. The SPI is intended for framework adapters and short-lived custom integrations where the starter-owned values are restored immediately before subscribing to downstream work.
