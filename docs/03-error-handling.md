# Error Handling

The starter maps 4xx and 5xx responses plus network-level failures to typed exceptions so callers can handle errors uniformly without inspecting raw `WebClientResponseException`.

---

## Exception hierarchy

```
RuntimeException
 └─ HttpClientException      – 4xx responses
 └─ RemoteServiceException   – 5xx responses
```

Both types expose:

| Method | Returns | Description |
|---|---|---|
| `getStatusCode()` | `int` | HTTP status code |
| `getResponseBody()` | `String` | Raw response body (may be empty) |
| `getErrorCategory()` | `ErrorCategory` | Coarse-grained failure category |

## Redirect responses

3xx responses are not errors and do not invoke `DefaultErrorDecoder` or registered
`ErrorResponseMapper` beans. The starter-created Reactor Netty transport leaves
automatic redirect following disabled, so a proxy method such as
`Mono<ResponseEntity<T>>` can receive a visible 3xx response and inspect its
`Location` header.

If an application supplies a separately configured transport that follows
redirects, the transport resolves the redirect before the proxy handles the
response. The proxy then emits or decodes the final response status normally.

## Error body retention policy

`DefaultErrorDecoder` keeps a bounded copy of the error response body before it
creates an exception or invokes an `ErrorResponseMapper`:

| Response body kind | Retained cap | Applies to |
|---|---:|---|
| Default error bodies | 4 KiB | Generic exceptions and custom mappers |
| `application/problem+json` bodies | 64 KiB | Mapper input for Problem Detail and other structured mappers |

`HttpClientException`, `RemoteServiceException`, and the Problem Detail exception
subclasses still expose at most 4 KiB from `getResponseBody()`. The larger
Problem Detail cap is for mapper parsing reliability, so a structured mapper can
read richer `application/problem+json` payloads before constructing its exception.
Malformed `Content-Type` headers fall back to the default 4 KiB cap instead of
aborting decoding. Mapper input is always bounded. Check
`ErrorResponseContext.responseBodyTruncated()` before assuming structured input is
complete; `retainedResponseBodyBytes()` reports the number of retained bytes.
See [Diagnostic Context Contracts](21-diagnostic-contexts.md) for the fields
available to error mappers and the other diagnostic extension points.

After reaching the cap, `DefaultErrorDecoder` continues draining the response and
releases each consumed `DataBuffer` so the connection can be reused when the
transport allows it. If the subscriber cancels before draining completes, capture
stops and consumed buffers are still released, but connection reuse is not
guaranteed. Exchange logging still follows `log-preset`: metadata-only and headers
presets omit bodies; only the `bodies` preset logs request/response payloads.

---

## Error categories

`ErrorCategory` is an enum with the following values:

| Category | When |
|---|---|
| `RATE_LIMITED` | HTTP 429 response |
| `CLIENT_ERROR` | Other 4xx response |
| `SERVER_ERROR` | 5xx response |
| `TIMEOUT` | `TimeoutException` or `ReadTimeoutException` |
| `CONNECT_ERROR` | `ConnectException` — TCP connection refused / timed out |
| `UNKNOWN_HOST` | `UnknownHostException` — DNS resolution failed |
| `AUTH_PROVIDER_ERROR` | `AuthProviderException` — token fetch / signing failed |
| `TLS_ERROR` | `SSLException` — TLS handshake or certificate validation failed |
| `RESILIENCE_ERROR` | Resilience4j rejected the call before it reached the remote service |
| `RESPONSE_DECODE_ERROR` | Codec/deserialization error on a 2xx response |
| `CANCELLED` | Reactive subscription cancelled before completion |
| `UNKNOWN` | Any other uncategorized error |

Published mapping contract:

| Input | Category |
|---|---|
| HTTP `429` | `RATE_LIMITED` |
| Other HTTP `4xx` | `CLIENT_ERROR` |
| HTTP `5xx` | `SERVER_ERROR` |
| 2xx response with decode/deserialization failure | `RESPONSE_DECODE_ERROR` |
| `TimeoutException`, Netty `ReadTimeoutException`, premature close | `TIMEOUT` |
| `CancellationException` | `CANCELLED` |
| `AuthProviderException` | `AUTH_PROVIDER_ERROR` |
| `SSLException` | `TLS_ERROR` |
| Resilience4j `CallNotPermittedException`, `BulkheadFullException`, or `RequestNotPermitted` | `RESILIENCE_ERROR` |
| `UnknownHostException` | `UNKNOWN_HOST` |
| `ConnectException` | `CONNECT_ERROR` |
| Other throwable | `UNKNOWN` |

---

## Reacting to errors in calling code

```java
userApiClient.getUser(id)
    .onErrorResume(HttpClientException.class, ex -> {
        if (ex.getErrorCategory() == ErrorCategory.RATE_LIMITED) {
            // back off and retry from the caller
        }
        return Mono.error(ex);
    })
    .onErrorResume(RemoteServiceException.class, ex -> {
        log.error("user-service returned {}: {}", ex.getStatusCode(), ex.getResponseBody());
        return Mono.error(ex);
    });
```

Use `ErrorCategories` when business logic receives a generic `Throwable`, for
example in one shared reactive error handler:

```java
userApiClient.getUser(id)
    .onErrorResume(error -> switch (ErrorCategories.from(error)) {
        case RATE_LIMITED -> backoffFallback(error);
        case CLIENT_ERROR -> Mono.error(new BusinessValidationException(error));
        case SERVER_ERROR, TIMEOUT, CONNECT_ERROR -> retryLater(error);
        case null, default -> Mono.error(error);
    });
```

---

## Handling auth errors

`AuthProviderException` is thrown when the `AuthProvider` fails (token endpoint unreachable, credentials invalid, or the token returned is already expired). It wraps the original cause and carries the logical client name:

```java
userApiClient.getUser(id)
    .onErrorResume(AuthProviderException.class, ex -> {
        log.warn("Auth failed for {}: {}", ex.getClientName(), ex.getCause().getMessage());
        return Mono.error(ex);
    });
```

---

## Handling request serialization errors

`RequestSerializationException` is thrown when the request body cannot be serialized before the HTTP call is made. This is a programming error (wrong content type, missing codec, or a type the codec cannot handle) rather than a runtime error, so it surfaces early:

```java
userApiClient.createUser(badPayload)
    .onErrorResume(RequestSerializationException.class, ex -> { ... });
```

---

## Structured error body mapping

Register `ErrorResponseMapper` beans when one downstream returns structured error
bodies that should become a more specific exception. Mappers are discovered in
`@Order` / `Ordered` sequence and can opt in per client with `supports(...)`.

```java
@Component
public class PaymentErrorMapper implements ErrorResponseMapper {

    private final ObjectMapper objectMapper;

    public PaymentErrorMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(String clientName) {
        return "payment-service".equals(clientName);
    }

    @Override
    public Optional<? extends Throwable> map(ErrorResponseContext context) throws Exception {
        if (context.responseBodyTruncated()) {
            return Optional.empty();
        }
        PaymentError error = objectMapper.readValue(context.responseBody(), PaymentError.class);
        if (!"DECLINED".equals(error.code())) {
            return Optional.empty();
        }
        return Optional.of(new PaymentDeclinedException(
                context.statusCode(),
                context.responseBody()));
    }
}
```

Return `Optional.empty()` when a mapper does not apply. If a mapper throws while
parsing an invalid body, the starter logs a warning and falls back to the default
decoder. The fallback preserves the original HTTP status, bounded retained response
body, and `ErrorCategory`.

`ErrorResponseContext.defaultException()` is available when a mapper wants to
inspect or wrap the default `HttpClientException` / `RemoteServiceException`.

### Problem Detail responses

The starter includes an opt-in mapper for RFC 9457 `application/problem+json`
responses. Register it as an `ErrorResponseMapper` bean when a downstream uses
Problem Detail consistently:

```java
@Bean
ErrorResponseMapper problemDetailErrorResponseMapper(ObjectMapper objectMapper) {
    return new ProblemDetailErrorResponseMapper(objectMapper);
}
```

When the response has `Content-Type: application/problem+json`, 4xx responses map
to `ProblemDetailHttpClientException` and 5xx responses map to
`ProblemDetailRemoteServiceException`. Both exceptions expose the parsed
`ProblemDetail` and keep the original status, bounded retained response body,
request context, and `ErrorCategory` from the default exception model.

```java
orderClient.createOrder(request)
    .onErrorResume(ProblemDetailHttpClientException.class, ex -> {
        ProblemDetail problem = ex.getProblemDetail();
        return Mono.error(new OrderRejectedException(problem.getTitle(), ex));
    });
```

Missing content type, non-problem content type, or invalid problem JSON falls back
to the default decoder. Problem Detail responses use the 64 KiB structured-body
cap above so richer problem payloads can still be parsed without exposing
unbounded response bodies.

---

## Observability and error categories

The `error.category` tag on the `reactive.http.client.requests` timer and the `error.type` attribute on OTel spans both reflect `ErrorCategory`. This makes error-rate dashboards and alerts easy to slice by failure type (e.g. alert on `SERVER_ERROR` rate > 5 %, ignore `RATE_LIMITED` from alert but feed it into a backpressure dashboard).

See [08-observability.md](08-observability.md) for the full metrics reference.
