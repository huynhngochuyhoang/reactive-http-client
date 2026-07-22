package io.github.huynhngochuyhoang.httpstarter.core;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Metrics;
import reactor.netty.http.client.HttpConnectionPoolMetrics;
import reactor.netty.resources.ConnectionPoolMetrics;
import reactor.netty.resources.ConnectionProvider;

import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.ToDoubleFunction;

final class ProtocolAwareConnectionPoolMeterRegistrar implements ConnectionProvider.MeterRegistrar {

    static final String TOTAL_CONNECTIONS = "reactor.netty.connection.provider.total.connections";
    static final String ACTIVE_CONNECTIONS = "reactor.netty.connection.provider.active.connections";
    static final String IDLE_CONNECTIONS = "reactor.netty.connection.provider.idle.connections";
    static final String PENDING_CONNECTIONS = "reactor.netty.connection.provider.pending.connections";
    static final String ACTIVE_STREAMS = "reactor.netty.connection.provider.active.streams";
    static final String PENDING_STREAMS = "reactor.netty.connection.provider.pending.streams";

    private final String poolName;
    private final boolean http2;
    private final Map<String, ConnectionPoolMetrics> pools = new ConcurrentHashMap<>();
    private final List<Meter> meters = new ArrayList<>();

    ProtocolAwareConnectionPoolMeterRegistrar(String poolName, boolean http2) {
        this.poolName = poolName;
        this.http2 = http2;
    }

    @Override
    public synchronized void registerMetrics(String ignoredPoolName,
                                             String id,
                                             SocketAddress ignoredRemoteAddress,
                                             ConnectionPoolMetrics metrics) {
        if (http2 && !(metrics instanceof HttpConnectionPoolMetrics)) {
            return;
        }
        pools.put(id, metrics);
        if (meters.isEmpty()) {
            meters.add(gauge(TOTAL_CONNECTIONS, ConnectionPoolMetrics::allocatedSize));
            meters.add(gauge(IDLE_CONNECTIONS, ConnectionPoolMetrics::idleSize));
            if (http2) {
                meters.add(gauge(ACTIVE_STREAMS, ProtocolAwareConnectionPoolMeterRegistrar::activeStreams));
                meters.add(gauge(PENDING_STREAMS, ConnectionPoolMetrics::pendingAcquireSize));
            } else {
                meters.add(gauge(ACTIVE_CONNECTIONS, ConnectionPoolMetrics::acquiredSize));
                meters.add(gauge(PENDING_CONNECTIONS, ConnectionPoolMetrics::pendingAcquireSize));
            }
        }
    }

    @Override
    public synchronized void deRegisterMetrics(String ignoredPoolName,
                                               String id,
                                               SocketAddress ignoredRemoteAddress) {
        pools.remove(id);
    }

    synchronized void close() {
        pools.clear();
        meters.forEach(Metrics.globalRegistry::remove);
        meters.clear();
    }

    private Meter gauge(String name, ToDoubleFunction<ConnectionPoolMetrics> value) {
        return Gauge.builder(name, pools, registered -> registered.values().stream()
                        .mapToDouble(value)
                        .sum())
                .tag("name", poolName)
                .register(Metrics.globalRegistry);
    }

    private static double activeStreams(ConnectionPoolMetrics metrics) {
        return metrics instanceof HttpConnectionPoolMetrics httpMetrics
                ? httpMetrics.activeStreamSize()
                : 0;
    }
}
