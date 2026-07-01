# Performance Troubleshooting

Use this guide when an outbound call is slower than expected. Start by separating
client-side work from downstream service time, network time, serialization, and
optional diagnostics.

Related docs:

- [Benchmarks](22-benchmarks.md)
- [Performance Summary](23-performance-summary.md)
- [Production Support Bundles](26-support-bundles.md)
- [Observability](08-observability.md)
- [Exchange Logging](13-exchange-logging.md)
- [Lifecycle Hooks](19-lifecycle-hooks.md)

## Locate the Time

High outbound latency can come from several places:

- **Starter client abstraction overhead:** proxy dispatch, request-plan lookup,
  annotation argument expansion, diagnostics hook checks, response envelope
  handling, and optional feature wrappers.
- **Downstream service latency:** time spent after the request reaches the
  remote service and before it starts or completes the response.
- **Network latency:** DNS, connection acquisition, TLS handshake, proxy hops,
  packet loss, and remote socket behavior.
- **Application serialization and body processing:** JSON encoding, JSON
  decoding, multipart preparation, streaming body subscription, body capture,
  and large payload buffering.

The local benchmark rows help reason about starter-side overhead for named
scenario shapes. They do not replace measurements from your own downstream,
network, payloads, JVM, and deployment.

## Inspect Metadata First

Use metadata-only diagnostics before enabling body capture. Metadata usually
answers the first questions without adding payload logging cost or sensitive data
risk.

For exchange logging, prefer:

```yaml
reactive:
  http:
    clients:
      users:
        log-exchange: true
        log-preset: metadata-only
```

`METADATA_ONLY` records method, path template, status, duration, error, and
attempt metadata while omitting headers and bodies. Move to `headers` or `bodies`
only when the investigation requires that data and the payload is safe to log.

## Use Observability Signals

Micrometer observer events can show client name, HTTP method, status, exception
category, API name, and duration. Keep tag cardinality bounded:

- Prefer stable API names instead of raw URLs.
- Avoid user IDs, order IDs, tokens, emails, and other unbounded values in tags.
- Use path templates or configured API names for grouping.
- Compare the same client and API across the same deployment window.

Metrics help distinguish persistent latency from single-call outliers, but they
do not identify body serialization cost by themselves.

## Read Lifecycle Attempts Carefully

Lifecycle hooks expose subscription attempts, retry attempts, success, and error
callbacks. Treat attempt counts as logical subscription attempts, not proof that
each attempt reached the network.

Use lifecycle data to answer:

- Did the call retry?
- Did the error happen before a response status was available?
- Did a timeout occur after response headers or before any response?
- Did the same client/method consistently take longer than comparable methods?

## Check Timeout Source

Timeout diagnostics help identify whether the effective timeout came from method,
API reference, client configuration, deprecated fallback configuration, or no
configured timeout. When a timeout fires:

- Compare the effective timeout with the downstream service's expected latency.
- Check whether the timeout is client-specific or inherited from shared config.
- Confirm whether response status was available before the timeout.
- Keep response headers in exchange logging when they are needed for timeout
  investigations; lifecycle hooks and observer events do not expose response
  headers.

## Account for Body Size

Payload shape changes can dominate client-side overhead:

- JSON request bodies add encoding work before dispatch.
- JSON response bodies add decoding work after response bytes arrive.
- `ResponseEntity<T>` keeps response metadata and body together.
- Error body capture is bounded and may truncate large error responses.
- Body logging and body capture can add allocation and latency.
- Streaming responses move body ownership to the subscriber and should be
  measured separately from scalar `Mono<T>` responses.

Compare benchmark rows only to workloads with similar body shape and consumption.

## Compare Workload Shape

Before using benchmark data as a reference point, check:

- Is the call a no-body success path, path/query/header path, JSON `POST`,
  `ResponseEntity<T>`, small error body, Problem Detail mapping, or streaming
  response?
- Are optional features enabled: exchange logging, Micrometer, retry,
  circuit breaker, rate limiter, bulkhead, auth signing, or custom filters?
- Does the baseline client perform the same optional work?
- Are request and response bodies similar in size and codec?
- Is the downstream local loopback, same-zone service, internet service, proxy,
  or TLS-heavy endpoint?
- Are connection pool limits, pending acquire time, and TLS settings comparable?
- Are retries increasing logical duration while improving successful completion?

If the workload does not match a promoted benchmark scenario, treat the benchmark
as orientation only and measure the real path directly.

## Investigation Checklist

1. Identify the client name, API name, HTTP method, and path template.
2. Capture status, duration, exception category, timeout source, and attempt
   count through metadata-only diagnostics.
3. Compare the same API across a stable time window with bounded Micrometer tags.
4. Check downstream service logs for matching request IDs and server-side
   duration.
5. Check DNS, connection pool, TLS, proxy, and remote socket behavior.
6. Compare payload size, codec, and body capture settings with the relevant
   benchmark row.
7. Enable headers or body logging only if metadata is insufficient and the data
   is safe to record.
8. Rerun targeted benchmarks only when code changes affect request construction,
   diagnostics, resilience wrapping, body processing, or response handling.
