# HTTP/2, HTTP Proxy, and TLS / mTLS

Both outbound proxy routing and custom TLS/mTLS are configured under the `reactive.http.network` block (global) and can be overridden per client under `reactive.http.clients.<name>`. When a per-client block is present it replaces the global block wholesale — there is no field-level merging.

---

## HTTP/2

HTTP/2 is opt-in per client. Leave it disabled unless the upstream service is known to support HTTP/2.

```yaml
reactive:
  http:
    clients:
      inventory-service:
        base-url: https://inventory.example.invalid
        http2-enabled: true
```

This keeps the starter-managed Reactor Netty connector, so global and per-client settings for connection pool, timeouts, compression, proxy, TLS/mTLS, logging, auth, and observability continue to apply.

For an `https://` base URL, the starter selects TLS HTTP/2 and negotiates H2
through ALPN. For an `http://` base URL, it selects clear-text H2C. The
downstream must support the selected mode; this option does not silently fall
back to HTTP/1.1. With the option omitted or `false`, HTTP/1.1 remains the
default. Unary values, `ResponseEntity<T>`, direct streaming bodies, and
`ResponseEntity<Flux<DataBuffer>>` use the same protocol selection. Each HTTP/2
stream retains independent cancellation and error ownership while sharing the
configured connection pool.

### GOAWAY and connection retirement

An HTTP/2 peer can send `GOAWAY` to retire one physical connection. Streams at
or below the peer's last-stream identifier may already have been processed and
can complete on the draining connection. The client does not open new streams
on that connection. When the pool has spare physical capacity, later demand can
open a replacement connection while accepted streams continue on the draining
socket. When the pool is at its physical bound, later demand waits for capacity.

`max-connections` remains a physical-connection bound during retirement. With a
one-connection pool, pending calls can remain queued while an accepted stream
finishes and the peer keeps the draining socket open. Once the peer closes that
socket, queued work can use a new connection. A `GOAWAY` frame is not evidence
that a possibly processed request is safe to replay. The starter adds no
GOAWAY-specific retry; configured retry, redirect, and auth replay retain their
normal method, idempotency, and request-body repeatability rules.

An abrupt H2 transport close is not graceful GOAWAY retirement. Affected streams
fail according to their observed terminal state and are not replayed by the
transport; a later independent call can use replacement capacity after the closed
connection leaves the pool. The V24 GOAWAY boundary and replacement behavior
remain unchanged.

## Response compression

`compression-enabled` is opt-in per client. When enabled, Reactor Netty adds
`Accept-Encoding: gzip` and incrementally decompresses gzip responses before
Spring codecs, error mapping, or caller-owned response publishers consume them.
Identity responses continue to work. This option does not compress request
bodies and does not add request `Content-Encoding`.

Do not add `Accept-Encoding` through default headers, `@HeaderParam`, header
maps, inbound forwarding, or a customizer when compression is enabled. The
starter rejects that ambiguous combination before exchange because negotiation
and decompression belong to the connector. With compression disabled, no
negotiation or automatic decompression is installed; an application that adds
`Accept-Encoding` then owns the resulting encoded representation.

Unary JSON, errors, `ResponseEntity`, direct streams, and streaming envelopes
are decompressed consistently. The boundaries are intentionally different:

| Boundary or response shape | Ownership and limit |
|---|---|
| Encoded wire bytes | Reactor Netty reads and incrementally decompresses them. The starter does not add a second encoded-size cap. |
| Decoded unary value (including `Mono<T>` and `ResponseEntity<T>`) | Spring codecs aggregate the decoded representation and enforce `codec-max-in-memory-size-mb` after decompression. `0` means unlimited. |
| Decoded default error body | `DefaultErrorDecoder` retains at most 4 KiB, drains the remainder, and releases consumed buffers. |
| Decoded `application/problem+json` error | The mapper receives at most 64 KiB; exception body access remains capped at 4 KiB. The remainder is drained and released. |
| Bodiless result (`Mono<Void>` or `ResponseEntity<Void>`) | The decoded body is drained and released without retaining an aggregate. |
| Direct or envelope `Flux<DataBuffer>` | Decoded chunks remain incremental, bypass the codec aggregate cap, and become caller-owned after emission. |

An **advertised** size is only a surviving post-transport `Content-Length`; it
does not prove how many encoded or decoded bytes were **consumed**. When automatic
decompression removes that header, size is **unknown**, not zero. The starter does
not consume a stream to calculate it. See [Observability](08-observability.md) for
byte-counter semantics.

Corrupt gzip content terminates the response and closes the affected pooled
connection so a later request cannot reuse corrupt decoder state. HTTP framing can
still be complete while the gzip member is truncated; Reactor Netty may then expose
partial decoded data without a decompression exception. Applications that require
whole-representation integrity must validate an application checksum, digest, or
format-level completeness signal.

## Transport-owned request headers

The starter rejects application-supplied `Content-Length`, `Transfer-Encoding`,
`Connection`, `Expect`, and `Host` headers. This applies consistently to default
headers, `@HeaderParam`, header maps, inbound-header forwarding, and
`ReactiveHttpClientCustomizer` filters. Reactor Netty owns framing and authority
for HTTP/1.1, HTTP/2, redirects, TLS, and proxy connections; headers generated
by the connector are unaffected. Use end-to-end headers such as `Content-Type`,
`Accept`, `Authorization`, correlation IDs, and application metadata normally.

A Reactor Netty warning that renders `GET /bad-request HTTP/1.0` is a synthetic
decoder placeholder, not evidence that the application sent that endpoint. For a
transport incident, retain the complete warning: decoder exception and message,
original decoded request lines, channel ID and local/remote addresses, protocol
and TLS mode, proxy/sidecar path, and the immediately preceding requests on that
connection. A malformed or conflicting body length can leave bytes that are then
parsed as another request; the first decoder failure is the useful cause.

The starter regression fixture sends a byte-array POST followed by a PUT through
the real starter proxy and a one-connection Reactor Netty pool. The server sees
the exact bodies on one channel with transport-generated `Content-Length` and no
application `Transfer-Encoding`; no parser desynchronization occurs. A separate
raw-socket fixture sends a valid request and then injects orphaned body bytes on
the same connection, with no starter, proxy, mesh, or ingress in the path.
Reactor Netty answers `400` and represents the initial-line decoder failure as
synthetic `GET /bad-request HTTP/1.0`; depending on Reactor Netty version, that
failed message need not reach the application handler. Therefore, that warning
alone does not identify the starter as its source. If normal starter traffic
cannot reproduce it, capture bytes at each intermediary boundary and look for
framing mutation, stale bytes, or a non-HTTP peer on the connection.

---

## HTTP proxy

The starter delegates proxy handshakes to Reactor Netty. The configured type has
the following wire meaning:

| Type | Wire behavior |
|---|---|
| `HTTP` | Opens a plaintext connection to the proxy and uses HTTP `CONNECT` for both `http://` and `https://` targets. Target TLS, when present, runs inside the tunnel. |
| `HTTPS` | Deprecated compatibility alias for `HTTP`. It does **not** add TLS between the client and proxy. |
| `SOCKS4` / `SOCKS5` | Uses Reactor Netty's SOCKS tunnel handler for the selected protocol. |
| `NONE` | Connects directly and overrides a configured global proxy. |

This transport does not emit absolute-form requests to a traditional HTTP forward
proxy. Configure a proxy that accepts `CONNECT`, including for plaintext HTTP
targets. Local release fixtures exercise authenticated HTTP `CONNECT`, SOCKS4,
and SOCKS5 tunnels. Proxy credentials stay transport-owned and are omitted from
starter request diagnostics; startup summaries render only
`credentials=[REDACTED]`.

### Global proxy

```yaml
reactive:
  http:
    network:
      proxy:
        type: HTTP                             # HTTP | SOCKS4 | SOCKS5 | NONE; HTTPS is a deprecated HTTP alias
        host: proxy.corp.example
        port: 3128
        username: ${PROXY_USER}               # optional
        password: ${PROXY_PASS}               # optional
        non-proxy-hosts: "localhost|.*\\.internal"   # Java regex, not glob
```

### Per-client proxy override

```yaml
reactive:
  http:
    clients:
      partner-api:
        proxy:
          type: HTTP
          host: partner-proxy.example.invalid
          port: 8080
```

### Bypassing the global proxy for one client

Set `type: NONE` on the per-client proxy block to route that client directly, bypassing any inherited global proxy:

```yaml
reactive:
  http:
    network:
      proxy:
        type: HTTP
        host: proxy.corp.example
        port: 3128
    clients:
      internal-service:
        proxy:
          type: NONE   # bypass global proxy; connect directly
```

### `non-proxy-hosts` pattern

`non-proxy-hosts` is a Java `java.util.regex.Pattern`, not a glob. Pipe (`|`) separates alternatives:

```yaml
non-proxy-hosts: "localhost|127\\.0\\.0\\.1|.*\\.internal|.*\\.corp\\.example"
```

Use `.*\.internal` (regex) — **not** `*.internal` (glob). The pattern is
matched against the target host before connecting: a match bypasses the proxy,
and a non-match uses it. Both paths are covered by a local wire fixture.

---

## TLS / mTLS

### Custom truststore

Use a custom truststore when the upstream service presents a certificate signed by a private CA:

```yaml
reactive:
  http:
    network:
      tls:
        trust-store: classpath:certs/truststore.p12
        trust-store-password: changeit
        trust-store-type: PKCS12        # default
```

### Client certificate (mTLS)

Add `key-store` to present a client certificate to the upstream server:

```yaml
reactive:
  http:
    network:
      tls:
        trust-store: classpath:certs/truststore.p12
        trust-store-password: changeit
        key-store: classpath:certs/client.p12
        key-store-password: changeit
        key-store-type: PKCS12          # default
```

The configured key store is presented during the TLS handshake. Local contract
fixtures require a trusted client identity and verify it over HTTP/1.1 and TLS
H2; missing and untrusted identities fail during `TLS_HANDSHAKE`. Trust-store and
key-store passwords, certificate bytes, and private keys are never added to
request diagnostics or support snapshots.

### Protocol and cipher restrictions

```yaml
reactive:
  http:
    network:
      tls:
        protocols: [TLSv1.3, TLSv1.2]
        ciphers:
          - TLS_AES_256_GCM_SHA384
          - TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384
```

### Per-client TLS override

Each client can use a different truststore or client certificate:

```yaml
reactive:
  http:
    clients:
      partner-api:
        tls:
          trust-store: classpath:certs/partner-ts.p12
          trust-store-password: ${PARTNER_TS_PWD}
          key-store: classpath:certs/partner-client.p12
          key-store-password: ${PARTNER_CLIENT_PWD}
```

### Resource path resolution

Truststore and keystore paths are resolved via Spring's `DefaultResourceLoader`:

| Prefix | Example |
|---|---|
| `classpath:` | `classpath:certs/truststore.p12` |
| `file:` | `file:/etc/ssl/truststore.p12` |
| Absolute path | `/etc/ssl/truststore.p12` |

### Development: disable certificate validation

```yaml
reactive:
  http:
    network:
      tls:
        insecure-trust-all: true   # development only
```

The starter logs a **WARN** at startup when `insecure-trust-all: true` is set so it can never be enabled accidentally. Never use this in production.

---

## Full example

```yaml
reactive:
  http:
    network:
      proxy:
        type: HTTP
        host: proxy.corp.example
        port: 3128
        username: ${PROXY_USER}
        password: ${PROXY_PASS}
        non-proxy-hosts: "localhost|.*\\.internal"
      tls:
        trust-store: classpath:certs/truststore.p12
        trust-store-password: changeit
        key-store: classpath:certs/client.p12
        key-store-password: changeit
        protocols: [TLSv1.3, TLSv1.2]
    clients:
      internal-service:
        base-url: https://internal.corp.example
        proxy:
          type: NONE          # bypass global proxy
      partner-api:
        base-url: https://partner.example.invalid
        tls:
          trust-store: classpath:certs/partner-ts.p12
          trust-store-password: ${PARTNER_TS_PWD}
```
