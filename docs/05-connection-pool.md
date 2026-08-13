# Connection Pool

The starter manages a Reactor Netty `ConnectionProvider` for each registered client. A shared global pool is applied by default; any client can replace it with its own pool block.

---

## Global pool configuration

```yaml
reactive:
  http:
    network:
      connection-pool:
        max-connections: 200            # total connections per pool
        pending-acquire-timeout-ms: 5000
        max-idle-time-ms: 30000         # evict connections idle > 30 s (0 = off)
        max-life-time-ms: 300000        # recycle connections older than 5 min (0 = unlimited)
        evict-in-background-ms: 60000   # background sweep interval (0 = off)
        metrics-enabled: false          # expose reactive.http.client.connection.pool.* gauges
```

| Property | Default | Description |
|---|---|---|
| `max-connections` | `200` | Maximum physical connections per pool; it is not an HTTP/2 stream limit |
| `pending-acquire-timeout-ms` | `5000` | How long a caller waits for a connection when the pool is full |
| `max-idle-time-ms` | `0` (off) | Evict connections that have been idle longer than this |
| `max-life-time-ms` | `0` (unlimited) | Recycle connections older than this regardless of activity |
| `evict-in-background-ms` | `0` (off) | Interval for background eviction sweeps |
| `metrics-enabled` | `false` | Publish address-free starter aggregate pool gauges to the `MeterRegistry` |

---

## Per-client pool override

Any field under `reactive.http.clients.<name>.pool` replaces the global pool wholesale — there is no field-level merging:

```yaml
reactive:
  http:
    clients:
      user-service:
        pool:
          max-connections: 500           # hot internal service
          pending-acquire-timeout-ms: 2000
          max-idle-time-ms: 15000
          metrics-enabled: true
      partner-api:
        pool:
          max-connections: 20            # low-volume partner; smaller pool
```

When the `pool` block is absent, the client uses the global defaults.

---

## Why `max-idle-time-ms` and `max-life-time-ms` matter

Load balancers and NAT gateways silently drop idle connections. Without idle eviction, Reactor Netty may hand out a half-dead socket, causing a connection-refused or read-timeout error that would not occur with a fresh socket.

`max-idle-time-ms` evicts connections that have not been used for that duration. `max-life-time-ms` recycles pooled connections regardless of usage — useful against servers that set a maximum keep-alive lifetime.

`evict-in-background-ms` enables a background thread that sweeps for evictable entries at the configured interval. Without it, eviction checks happen only at acquire time. If the pool is entirely idle between bursts, setting a background sweep ensures stale connections are removed proactively.

---

## Protocol-aware capacity

For the default HTTP/1.1 transport, one connection serves one active exchange at a
time. `max-connections` therefore bounds physical connections and additional
demand enters the connection-acquisition queue.

With `http2-enabled: true`, `max-connections` still bounds physical connections,
but each connection can carry concurrent streams. The peer advertises the stream
limit at runtime. The starter does not turn that negotiated value into configuration
metadata or infer it from `max-connections`; provider-backed diagnostics report
`poolProtocol=HTTP/2`, `poolCapacityBasis=connections-and-peer-streams`, and
`poolMaxConcurrentStreams=null` (unknown). HTTP/1.1 reports
`poolCapacityBasis=connections`.

## Connection-pool metrics

When `metrics-enabled: true`, the starter publishes address-free starter aggregate
pool gauges to the global `MeterRegistry`. Every gauge has exactly one bounded
`name=reactive-http-client-<clientName>-<interface>` tag; remote addresses are
not tags.

Common gauges are `reactive.http.client.connection.pool.total.connections` and
`reactive.http.client.connection.pool.idle.connections`. Protocol-specific
capacity gauges are:

| Protocol | Gauge | Description |
|---|---|---|
| HTTP/1.1 | `reactive.http.client.connection.pool.active.connections` | Physical connections serving an exchange |
| HTTP/1.1 | `reactive.http.client.connection.pool.pending.connections` | Calls waiting for physical connection capacity |
| HTTP/2 | `reactive.http.client.connection.pool.active.streams` | Active streams on pooled H2 connections |
| HTTP/2 | `reactive.http.client.connection.pool.pending.streams` | Calls waiting for peer-advertised stream capacity |

The public H2 pool view does not prove how active streams are distributed across
physical connections, so the starter does not publish `active.connections` for H2.

## Stale connection retirement and replacement

Reactor Netty owns pooled-channel validation and removal. A complete HTTP/1.1
response with `Connection: close`, a peer FIN after a complete response, or an
idle socket closed by the peer retires that socket. A later independent call can
then acquire replacement capacity within `max-connections`; it does not reuse the
closed channel or its decoder state.

A reset after request dispatch or a close while the response body is incomplete
is different: the affected logical call fails. Removing that unusable channel can
release capacity for queued or later demand, but replacement capacity is not
request replay. The starter disables Reactor Netty's one-time connection-reset
retry on both business and OAuth2 token-service transports, including resets
before request headers are sent. Only configured Resilience4j retry can create
another business-request subscription attempt, and the existing HTTP-method
safety, idempotency-key, body-repeatability, and application-owned resource rules
still apply.

Idle/lifetime eviction can reduce the chance that an intermediary's idle timeout
races with reuse, but it cannot prove that a socket remains live between an
acquire and a write. During recovery, inspect active and pending gauges together.
After the failed call terminates and replacement demand completes, the gauges
should converge without a stranded pending acquire or duplicate dispatch. Factory
shutdown retains the bounded five-second disposal policy: providers reject queued
acquisitions and the factory closes its tracked active channels. Business-provider
disposal, OAuth2 token-service-provider disposal, and channel closure run
concurrently under that single factory-wide deadline.

## Diagnosing saturation

With `max-connections: 1`, additional calls wait in the pending queue until the
active response releases its connection. A queued call either acquires the released
connection, is cancelled and removed from the queue, or fails after
`pending-acquire-timeout-ms`. Proven acquire failures retain the existing
`ErrorCategory.TIMEOUT` mapping and add the bounded optional stage
`POOL_ACQUIRE`; generic timeouts remain stage-unknown.

For HTTP/1.1, use `pending.connections` with `active.connections`. For HTTP/2,
use `pending.streams` with `active.streams` and the physical-connection gauges.
A non-zero H2 pending-stream gauge is proof of stream-capacity pressure while it is
observed. A `POOL_ACQUIRE` failure alone remains generic pool-admission evidence:
Reactor Pool timeout, pending-limit, and shutdown exceptions do not safely identify
connection versus stream pressure.

The health detail `poolAcquireFailureCount` intentionally aggregates those proven
pool-admission failures for the probe window. Provider-backed diagnostics expose
only sanitized configured protocol, capacity basis, pool source, maximum
connections, pending timeout, and metrics policy. The negotiated H2 stream limit
remains unknown. Metrics and support metadata do not expose a remote address;
observer and OTel server-address fields remain controlled by
`include-server-address` (default `false`).

Idle and lifetime limits are enforced on acquire, and `evict-in-background-ms`
adds proactive sweeps. Factory shutdown disposes the provider and terminates active
and queued work; callers must still handle cancellation or shutdown errors.

Enabling these gauges adds a small per-request overhead (internal Reactor Netty instrumentation). Leave disabled in latency-sensitive paths unless pool visibility is required.

See [08-observability.md](08-observability.md) for the full observability reference.
