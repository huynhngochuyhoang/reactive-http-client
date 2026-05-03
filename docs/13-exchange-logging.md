# Exchange Logging

The starter provides per-method request/response logging through the `@LogHttpExchange` annotation and the `HttpExchangeLogger` extension point.

---

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
```

---

## `HttpExchangeLogContext`

The context record carries all exchange fields available to the logger:

| Field | Type | Description |
|---|---|---|
| `clientName` | `String` | Logical client name |
| `httpMethod` | `String` | HTTP verb |
| `pathTemplate` | `String` | Path template, e.g. `/users/{id}` |
| `pathVariables` | `Map<String, Object>` | Resolved path variable values |
| `queryParameters` | `Map<String, List<Object>>` | Query parameters |
| `inboundHeaders` | `Map<String, List<String>>` | Filtered snapshot of inbound request headers (populated by `InboundHeadersWebFilter`) |
| `requestHeaders` | `Map<String, String>` | Outgoing request headers |
| `requestBody` | `Object` | Request body (may be `null`) |
| `responseStatus` | `Integer` | HTTP response status code (`null` on network error) |
| `responseHeaders` | `Map<String, List<String>>` | Response headers |
| `responseBody` | `Object` | Decoded response body (`null` for `Flux<T>` responses) |
| `durationMs` | `long` | Exchange duration in milliseconds |
| `error` | `Throwable` | Thrown exception, or `null` on success |

---

## Default logger — `DefaultHttpExchangeLogger`

The built-in logger logs at `INFO` on success and `WARN` on error. Request and response bodies are included only when the logger's level is `DEBUG` or finer; otherwise `[OMITTED]` is substituted.

Sensitive headers (`Authorization`, `Cookie`, `Set-Cookie`, `Proxy-Authorization`, `X-Api-Key`) are automatically replaced with `[REDACTED]` in both request and response header maps.

### Default log format (success)

```
[user-service] GET /users/{id} inboundHeaders={...} reqHeaders={...} reqBody=[OMITTED] respStatus=200 respHeaders={...} respBody=[OMITTED] duration=45ms
```

### Default log format (error)

```
[user-service] GET /users/{id} inboundHeaders={...} reqHeaders={...} reqBody=[OMITTED] respStatus=404 respHeaders={...} respBody=[OMITTED] duration=12ms error=HttpClientException: 404 Not Found
```

---

## Custom logger

You can supply a custom `HttpExchangeLogger` class (or bean) per method via the `logger` attribute on `@LogHttpExchange`. The runtime first checks the Spring `ApplicationContext` for a bean of the named class; if none is found it instantiates the class directly using its no-arg constructor.

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
                    context.error().getMessage());
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

To share a single configured instance (e.g. one that needs constructor injection), register it as a Spring bean:

```java
@Bean
AuditExchangeLogger auditExchangeLogger(AuditService auditService) {
    return new AuditExchangeLogger(auditService);
}
```

The runtime will resolve the bean by class and reuse it across all methods that reference `AuditExchangeLogger.class`. Different methods can reference different logger classes — there is no global limit of one logger bean.

When `@LogHttpExchange` is used without a `logger` attribute (i.e. `logger = DefaultHttpExchangeLogger.class`), the runtime resolves `DefaultHttpExchangeLogger` through the same look-up/instantiation path.

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
        if (context.error() != null) {
            fields.put("error", context.error().getMessage());
        }
        try {
            log.info(mapper.writeValueAsString(fields));
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize exchange log", e);
        }
    };
}
```

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

---

## Inbound headers in log context

`HttpExchangeLogContext.inboundHeaders()` contains a filtered snapshot of the inbound request headers from the calling WebFlux request (populated by `InboundHeadersWebFilter`). This is useful for correlating outbound calls with their originating request context.

Configure which headers are captured and which are redacted in:

```yaml
reactive:
  http:
    inbound-headers:
      allow-list: [X-Request-Id, X-User-Id]
      deny-list:  [Authorization, Cookie]
```

See [09-correlation-id.md](09-correlation-id.md) for full details on the inbound headers filter.
