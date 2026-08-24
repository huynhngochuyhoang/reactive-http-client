# Exchange Logging

The starter provides request/response logging through the `@LogHttpExchange` annotation and the `HttpExchangeLogger` extension point.

---

## Enabling logging for an entire client interface

Annotate the client interface to apply one logger to all methods:

```java
@ReactiveHttpClient(name = "user-service")
@LogHttpExchange(logger = UserApiExchangeLogger.class)
public interface UserApiClient {

    @GET("/users/{id}")
    Mono<User> getUser(@PathVar("id") String id);
}
```

Method-level `@LogHttpExchange` overrides the client-level logger:

```java
@GET("/users/{id}")
@LogHttpExchange(logger = AuditExchangeLogger.class)
Mono<User> getUser(@PathVar("id") String id);
```

## Enabling logging for a method

Add `@LogHttpExchange` to any client method:

```java
@ReactiveHttpClient(name = "user-service")
public interface UserApiClient {

    @GET("/users/{id}")
    @LogHttpExchange
    Mono<User> getUser(@PathVar("id") String id);
}
```

The `@LogHttpExchange` annotation has an optional `logger` attribute that names the `HttpExchangeLogger` class to use. The runtime resolves this logger by looking up a Spring bean of that class, or—if no bean is found—instantiating the class directly via its no-arg constructor. The default is `DefaultHttpExchangeLogger`.

```java
// Use the default logger
@GET("/users/{id}")
@LogHttpExchange
Mono<User> getUser(@PathVar("id") String id);

// Use a custom logger class
@POST("/orders")
@LogHttpExchange(logger = MyOrderLogger.class)
Mono<Order> placeOrder(@Body NewOrder body);
```

### Enabling logging for an entire client

Set `log-exchange: true` in the client configuration to enable logging on all methods of that client:

```yaml
reactive:
  http:
    clients:
      user-service:
        log-exchange: true
        log-preset: metadata-only
```

`log-exchange` turns on client-wide exchange logging. `log-preset` controls what the default logger writes:

| Preset | Logged data |
|---|---|
| `metadata-only` | Method, path template, status, duration, error type, category, and proven failure stage. Headers, bodies, and exception messages are omitted. This is the default. |
| `headers` | Metadata plus inbound, request, and response headers. Sensitive headers are redacted. Bodies are omitted. |
| `bodies` | Metadata, redacted headers, and request/response bodies. Use only when payload logging is acceptable. |

Replace any older `log-body: true` setting with `log-exchange: true` and, if body payloads are required, `log-preset: bodies`.

---

## `HttpExchangeLogContext`

The context record carries all exchange fields available to the logger:

| Field | Type | Description |
|---|---|---|
| `clientName` | `String` | Logical client name |
| `httpMethod` | `String` | HTTP verb |
| `pathTemplate` | `String` | Path template, e.g. `/users/{id}` |
| `requestUrl` | `URI` | Final outbound request URL after `WebClient` filters have run, when available |
| `pathVariables` | `Map<String, Object>` | Resolved path variable values |
| `queryParameters` | `Map<String, List<Object>>` | Query parameters |
| `inboundHeaders` | `Map<String, List<String>>` | Filtered snapshot of inbound request headers (populated by `InboundHeadersWebFilter`) |
| `requestHeaders` | `Map<String, String>` | Final outbound request headers after `WebClient` filters have run, when available; otherwise declarative resolved headers |
| `requestBody` | `Object` | Request body (may be `null`) |
| `responseStatus` | `Integer` | HTTP response status code (`null` on network error) |
| `responseHeaders` | `Map<String, List<String>>` | Response headers |
| `responseBody` | `Object` | Decoded response body (`null` for `Flux<T>` responses) |
| `durationMs` | `long` | Logical-call duration in milliseconds |
| `subscriptionAttemptCount` | `int` | Number of subscription attempts inside this logical call; not a guaranteed count of HTTP requests sent |
| `error` | `Throwable` | Thrown exception, or `null` on success |
| `logPreset` | `LogPreset` | Configured preset for the default logger |

---

## Default logger — `DefaultHttpExchangeLogger`

The built-in logger logs at `INFO` on success and `WARN` on error. Request/response headers and bodies are controlled by `log-preset`; omitted values are written as `{}` or `[OMITTED]`.

Sensitive headers (`Authorization`, `Cookie`, `Set-Cookie`, `Proxy-Authorization`, `X-Api-Key`) are automatically replaced with `[REDACTED]` in both request and response header maps.

When the starter-managed `WebClient` is used, `requestHeaders` reflects the final
outbound headers visible after built-in filters and per-client
`ReactiveHttpClientCustomizer` filters. This lets header logging show
customizer-added values such as `X-Request-ID` without buffering or inspecting
the request body. See [Production Support Bundles](26-support-bundles.md) for
safe logging bundles.

### Default log format (success)

```
[user-service] GET /users/{id} inboundHeaders={...} reqHeaders={...} reqBody=[OMITTED] respStatus=200 respHeaders={...} respBody=[OMITTED] duration=45ms subscriptionAttemptCount=1
```

### Default log format (error)

```
[user-service] GET /users/{id} inboundHeaders={...} reqHeaders={...} reqBody=[OMITTED] respStatus=404 respHeaders={...} respBody=[OMITTED] duration=12ms subscriptionAttemptCount=1 errorType=io.github.huynhngochuyhoang.httpstarter.exception.HttpClientException errorCategory=CLIENT_ERROR failureStage=none
```

The default logger never appends an exception message, cause text, or stack
trace. This keeps arbitrary auth-provider and custom-filter payload text out of
built-in logs while retaining structural error type, category, and proven
failure-stage metadata.

---

## Custom logger

You can supply a custom `HttpExchangeLogger` class (or bean) via the `logger` attribute on `@LogHttpExchange` at either interface level or method level. The runtime first checks the Spring `ApplicationContext` for a bean of the named class; if none is found it instantiates the class directly using its no-arg constructor.

```java
public class AuditExchangeLogger implements HttpExchangeLogger {

    private static final Logger log = LoggerFactory.getLogger(AuditExchangeLogger.class);

    @Override
    public void log(HttpExchangeLogContext context) {
        if (context.error() != null) {
            log.error("[{}] {} {} -> {} ({}ms) ERROR: {}",
                    context.clientName(),
                    context.httpMethod(),
                    context.pathTemplate(),
                    context.responseStatus(),
                    context.durationMs(),
                    context.error().getClass().getName());
        } else {
            log.info("[{}] {} {} -> {} ({}ms)",
                    context.clientName(),
                    context.httpMethod(),
                    context.pathTemplate(),
                    context.responseStatus(),
                    context.durationMs());
        }
    }
}
```

Annotate individual methods to select the logger class to use:

```java
@POST("/orders")
@LogHttpExchange(logger = AuditExchangeLogger.class)
Mono<Order> placeOrder(@Body NewOrder body);
```

Custom loggers receive the raw throwable and must bound and sanitize any
exception message before persisting it. Logging only the exception class,
category, and proven failure stage matches the built-in support-safe policy.

To share a single configured instance (e.g. one that needs constructor injection), register it as a Spring bean:

```java
@Bean
AuditExchangeLogger auditExchangeLogger(AuditService auditService) {
    return new AuditExchangeLogger(auditService);
}
```

The runtime will resolve the bean by class and reuse it across all methods that reference `AuditExchangeLogger.class`. Different methods can reference different logger classes — there is no global limit of one logger bean.

`MockReactiveHttpClient` uses an isolated application context and does not import
logger beans from the application test context. Register each constructor-injected
logger instance explicitly on the mock builder:

```java
AuditExchangeLogger logger = new AuditExchangeLogger(auditService);

MockReactiveHttpClient<OrdersClient> mock = MockReactiveHttpClient
        .forClient(OrdersClient.class)
        .withExchangeLogger(logger)
        .respondTo(HttpMethod.POST, "/orders",
                exchange -> MockReactiveHttpClient.json(201, "{\"id\":1}"))
        .build();
```

The annotation still selects the logger by concrete class at interface or method
level. Repeated registrations may use different logger classes; registering two
instances of the same concrete class is rejected as ambiguous. In the production
application, register a logger through either `@Bean` or component scanning, not
both.

When `@LogHttpExchange` is used without a `logger` attribute (i.e. `logger = DefaultHttpExchangeLogger.class`), the runtime resolves `DefaultHttpExchangeLogger` through the same look-up/instantiation path.

`log-preset` is applied by `DefaultHttpExchangeLogger`. Custom `HttpExchangeLogger` implementations receive the preset in `HttpExchangeLogContext` and may choose to honor or ignore it.

`subscriptionAttemptCount` is logical-call metadata. It counts retry subscriptions,
not guaranteed HTTP network sends. For example, request-body serialization can
fail before dispatch after an attempt has started.

`cacheOutcome()` is `null` unless response-cache observability is separately
enabled for a cache-selected method. Its only values are `FRESH_HIT`,
`MISS_LOADER`, `COALESCED_WAITER`, and `STALE_HIT`; it never carries a cache
key or value. Hits and waiters have no invented transport status or URL.

---

## Structured logging example

```java
@Bean
HttpExchangeLogger structuredExchangeLogger(ObjectMapper mapper) {
    return context -> {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("client", context.clientName());
        fields.put("method", context.httpMethod());
        fields.put("path", context.pathTemplate());
        fields.put("status", context.responseStatus());
        fields.put("durationMs", context.durationMs());
        fields.put("subscriptionAttemptCount", context.subscriptionAttemptCount());
        if (context.cacheOutcome() != null) {
            fields.put("cacheOutcome", context.cacheOutcome().name());
        }
        if (context.error() != null) {
            fields.put("errorType", context.error().getClass().getName());
            fields.put("errorCategory",
                    ErrorCategories.from(context.error(), context.responseStatus()).name());
            fields.put("failureStage", context.failureStage() != null
                    ? context.failureStage().name()
                    : null);
        }
        try {
            log.info(mapper.writeValueAsString(fields));
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize exchange log", e);
        }
    };
}
```

This example records bounded structural failure metadata and deliberately omits
exception messages, cause text, and stack traces.

---

## Sensitive header redaction

The default logger redacts the following headers automatically:

| Header | Redacted in |
|---|---|
| `Authorization` | Request and response |
| `Cookie` | Request and response |
| `Set-Cookie` | Response |
| `Proxy-Authorization` | Request |
| `X-Api-Key` | Request |

Custom loggers receive raw values from `HttpExchangeLogContext`. Use `SensitiveHeaders.isSensitive(headerName)` to apply the same deny-list in your own implementation.
See [Diagnostic Context Contracts](21-diagnostic-contexts.md) for the canonical
extension-point capability matrix and raw-versus-redacted header behavior.

---

## Inbound headers in log context

`HttpExchangeLogContext.inboundHeaders()` contains a filtered immutable snapshot of the inbound request headers from the calling WebFlux request (populated by `InboundHeadersWebFilter`). This is useful for correlating outbound calls with their originating request context. The snapshot preserves original header casing, applies allow-list / deny-list matching case-insensitively, and stores redacted values before loggers see them.

Configure which headers are captured and which are redacted in:

```yaml
reactive:
  http:
    inbound-headers:
      allow-list: [X-Request-Id, X-User-Id]
      deny-list:  [Authorization, Cookie]
```

See [09-correlation-id.md](09-correlation-id.md) for full details on the inbound headers filter.
