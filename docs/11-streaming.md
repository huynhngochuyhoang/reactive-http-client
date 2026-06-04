# Streaming Responses

Methods that declare `Flux<DataBuffer>` or `Mono<ResponseEntity<Flux<DataBuffer>>>` as their return type bypass the in-memory codec entirely. Payloads of any size are streamed without risk of a `DataBufferLimitException`, regardless of the `codec-max-in-memory-size-mb` setting. Streaming bodies are pass-through; the starter does not aggregate or inspect them.

---

## Streaming to a `Flux<DataBuffer>`

```java
@ReactiveHttpClient(name = "object-store")
public interface ObjectStoreClient {

    @GET("/objects/{key}")
    Flux<DataBuffer> download(@PathVar("key") String key);
}
```

The caller receives `DataBuffer` chunks as Reactor Netty produces them. Emitted buffers are owned by the downstream consumer. If your code reads or drops chunks manually, release them with `DataBufferUtils.release(buffer)` or hand the `Flux` to a WebFlux response writer that owns that release step. The starter releases buffers that Reactor discards before handoff, such as queued chunks discarded by cancellation.

---

## Streaming with response status and headers

Use `Mono<ResponseEntity<Flux<DataBuffer>>>` to expose the upstream HTTP status and response headers alongside the streaming body. This is useful for proxy / pass-through implementations:

```java
@ReactiveHttpClient(name = "object-store")
public interface ObjectStoreClient {

    @GET("/objects/{key}")
    Mono<ResponseEntity<Flux<DataBuffer>>> downloadEntity(@PathVar("key") String key);
}
```

Usage in a Spring WebFlux controller:

```java
@GetMapping("/proxy/objects/{key}")
Mono<ResponseEntity<Flux<DataBuffer>>> proxy(
        @PathVariable String key,
        ObjectStoreClient client) {
    return client.downloadEntity(key);
}
```

The upstream status code and headers are forwarded to the caller without buffering the body. The outer `Mono` completes when the response envelope is available; the inner `Flux<DataBuffer>` is not subscribed until the caller consumes it. If the caller never subscribes the inner body, no body chunks are read by the starter.

---

## Memory behaviour

- Streaming responses are not buffered by the starter. Memory usage stays bounded by the number of in-flight chunks, not the total payload.
- Emitted `DataBuffer` chunks are consumer-owned. Release them after manual reads, or pass them to a component that documents ownership transfer.
- Buffers discarded before consumer handoff are released by the starter, including queued chunks discarded by cancellation.
- For direct `Flux<DataBuffer>` methods, lifecycle hooks, observers, and exchange logs finish when the stream completes, errors, or is cancelled.
- For `Mono<ResponseEntity<Flux<DataBuffer>>>`, lifecycle hooks, observers, and exchange logs finish when the response envelope is emitted. They do not prove that the inner body was subscribed, fully consumed, or released. Response-size diagnostics still use `Content-Length` when available.

---

## Combining streaming with other features

Streaming methods support all other annotations (`@PathVar`, `@QueryParam`, `@HeaderParam`, `@TimeoutMs`, `@Retry`, `@LogHttpExchange`, etc.) exactly like non-streaming methods.

```java
@GET("/exports/{format}")
@ApiName("export.download")
@TimeoutMs(0)   // disable per-request timeout for long downloads
Flux<DataBuffer> exportData(
        @PathVar("format") String format,
        @QueryParam("from") String from,
        @QueryParam("to") String to);
```

---

## Error handling for streaming responses

Errors from the upstream server (4xx / 5xx) are decoded and thrown before the streaming body is emitted, following the same error-handling contract as non-streaming methods. Error bodies are bounded and released by `DefaultErrorDecoder`. See [03-error-handling.md](03-error-handling.md).
