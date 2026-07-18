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
        metrics-enabled: false          # expose reactor.netty.connection.provider.* gauges
```

| Property | Default | Description |
|---|---|---|
| `max-connections` | `200` | Maximum concurrent connections per pool |
| `pending-acquire-timeout-ms` | `5000` | How long a caller waits for a connection when the pool is full |
| `max-idle-time-ms` | `0` (off) | Evict connections that have been idle longer than this |
| `max-life-time-ms` | `0` (unlimited) | Recycle connections older than this regardless of activity |
| `evict-in-background-ms` | `0` (off) | Interval for background eviction sweeps |
| `metrics-enabled` | `false` | Publish Reactor Netty pool gauges to the `MeterRegistry` |

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

## Connection-pool metrics

When `metrics-enabled: true`, the `ConnectionProvider` publishes four Reactor Netty gauges to the global `MeterRegistry`:

| Gauge | Description |
|---|---|
| `reactor.netty.connection.provider.total.connections` | All connections (active + idle) |
| `reactor.netty.connection.provider.active.connections` | Connections currently in use |
| `reactor.netty.connection.provider.idle.connections` | Idle connections available for reuse |
| `reactor.netty.connection.provider.pending.connections` | Callers waiting to acquire a connection |

All gauges carry a `name` tag of the form `reactive-http-client-<clientName>`.

## Diagnosing saturation

With `max-connections: 1`, additional calls wait in the pending queue until the
active response releases its connection. A queued call either acquires the released
connection, is cancelled and removed from the queue, or fails after
`pending-acquire-timeout-ms`. Proven acquire failures retain the existing
`ErrorCategory.TIMEOUT` mapping and add the bounded optional stage
`POOL_ACQUIRE`; generic timeouts remain stage-unknown.

Use `pending.connections` together with `active.connections` and the per-call
`failure.stage` metric tag. The health detail `poolAcquireFailureCount` reports the
probe-window count, while provider-backed diagnostics expose only sanitized pool
source, maximum connections, pending timeout, and metrics-enabled policy. These
surfaces do not expose a remote address; observer and OTel server-address fields
remain controlled by `include-server-address` (default `false`).

Idle and lifetime limits are enforced on acquire, and `evict-in-background-ms`
adds proactive sweeps. Factory shutdown disposes the provider and terminates active
and queued work; callers must still handle cancellation or shutdown errors.

Enabling these gauges adds a small per-request overhead (internal Reactor Netty instrumentation). Leave disabled in latency-sensitive paths unless pool visibility is required.

See [08-observability.md](08-observability.md) for the full observability reference.
