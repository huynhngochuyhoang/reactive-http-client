# Streaming Requests and Responses

## Publisher request bodies

A method may accept a `Publisher` body such as `Flux<DataBuffer>` or `Flux<MyDto>`. Calling the Java method only creates a cold client publisher: the starter does not subscribe to the request body until that client publisher is subscribed and the transport is ready to write it. Transport demand controls body demand, and cancellation before connection acquisition leaves the body unsubscribed. Cancellation during a write is propagated to the body publisher.

```java
@POST("/objects/{key}")
Mono<Void> upload(@PathVar("key") String key,
                  @Body Flux<DataBuffer> body);
```

Each actual transport request gets one body subscription. A retry, a body-preserving redirect, or the built-in one-time 401 auth refresh creates another transport request and therefore another subscription. The supplied publisher must create fresh content on every subscription when any of those features can replay the request. The starter never caches or aggregates an unbounded publisher to make it repeatable.

`Flux<DataBuffer>` and `Flux<byte[]>` default to `application/octet-stream`; DTO publishers default to JSON and remain encoded by the configured WebClient codecs. Publisher, direct `DataBuffer`, `Resource`, `InputStream`, `Reader`, and `ReadableByteChannel` bodies bypass auth JSON materialization and remain available to custom or bearer-token auth as request metadata. Auth providers must not consume them. Built-in AWS SigV4 rejects unsupported streaming bodies before transport because it cannot prove the payload hash without consuming the stream.

### Wire framing and request ownership

The transport owns framing; applications must not supply `Content-Length` or
`Transfer-Encoding`. Under HTTP/1.1, `byte[]` and a `Resource` with a known
length use transport-generated `Content-Length`. Publisher bodies, direct
`DataBuffer`, `InputStream`, `Reader`, and `ReadableByteChannel` bodies use
`Transfer-Encoding: chunked` because their final length is not known before the
write starts. Under HTTP/2 the same streaming bodies use DATA frames and cannot carry the
HTTP/1.1 `Transfer-Encoding` header. Reactor Netty server handlers expose an
HTTP-object compatibility view that can synthesize `transfer-encoding: chunked`
after decoding an unknown-length H2 stream; do not treat that value as a captured
wire header.

Direct `DataBuffer`, `InputStream`, and `ReadableByteChannel` bodies default to
`application/octet-stream`. A `Reader` defaults to UTF-8 `text/plain`; a
caller-supplied `Content-Type` remains authoritative. A `Resource` uses the
Spring resource writer, including media-type inference from its filename and
its known length when available.

Subscribing to the client result transfers ownership of an eager direct
`DataBuffer`, `InputStream`, `Reader`, or `ReadableByteChannel` body. The
starter closes or releases it exactly once on completion, error, or cancellation,
including cancellation or logical timeout before the HTTP writer subscribes to
the body. Once a direct `DataBuffer` write starts, ownership passes to the HTTP
writer. A `Resource` remains lazy and is opened and closed once per request
attempt. Publisher-emitted `DataBuffer` values are released by the HTTP writer
after the write or when discarded because the request is cancelled. Do not
release an emitted buffer concurrently from application code.

A peer disconnect or write timeout cancels body demand. It does not make a
partially written request replay-safe: a retry, redirect, or hidden auth replay
still creates a new transport request and a new body subscription/read.

### Request-body repeatability matrix

| Body shape | Classification | Replay ownership |
|---|---|---|
| No body, `byte[]`, `String`, concrete JSON DTO, multipart `byte[]`, `FileAttachment` | Repeatable | Starter/WebClient can write the same materialized value again. |
| `Publisher` or `DataBuffer` | Non-repeatable | Application must supply a cold, replayable publisher for retry, redirect, or 401 refresh. |
| `Resource`, multipart `Resource`, Java stream, `Object`, or erased generic | Application-owned | Application must prove reopen/encoding behavior; strict built-in SigV4 rejects shapes whose bytes are not startup-provable. |

Runtime retry diagnostics, startup method diagnostics, effective-contract snapshots, strict built-in SigV4 validation, and `MockReactiveHttpClient` use this same classification. Retry and redirect preserve compatibility and do not reject application-owned replay; built-in body signing is rejected before sending when raw bytes cannot be proven. Mock requests materialize a publisher only when its in-process exchange runs and once per mock retry attempt, but the helper does not emulate socket demand, pool acquisition, or transport cancellation.

---

## Streaming responses

Methods that declare `Flux<DataBuffer>` or `Mono<ResponseEntity<Flux<DataBuffer>>>` as their return type bypass the in-memory codec entirely. Payloads of any size are streamed without risk of a `DataBufferLimitException`, regardless of the `codec-max-in-memory-size-mb` setting. With response compression enabled, Reactor Netty incrementally decompresses the wire representation before emitting these chunks; the starter still does not aggregate or inspect them. Emitted decoded buffers are caller-owned.

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
- For `Mono<ResponseEntity<Flux<DataBuffer>>>`, lifecycle hooks, observers, and
  exchange logs finish when the response envelope is emitted. They do not prove
  that the inner body was subscribed, fully consumed, or released. Response-size
  diagnostics use the post-transport `Content-Length` when available and never
  aggregate the stream; automatically decompressed and chunked responses report
  unknown. See [Production Support Bundles](26-support-bundles.md) for safe
  streaming incident evidence.

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
