package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.annotation.GET;
import io.github.huynhngochuyhoang.httpstarter.annotation.PathVar;
import io.github.huynhngochuyhoang.httpstarter.annotation.ReactiveHttpClient;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientFailureStage;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.netty.channel.Channel;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.netty.DisposableServer;
import reactor.netty.http.HttpProtocol;
import reactor.netty.http.server.HttpServer;
import reactor.netty.resources.ConnectionProvider;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class ProtocolAwarePoolCapacityContractTest {

    private static final Duration CALL_TIMEOUT = Duration.ofSeconds(5);

    @Test
    void http11MetricsReportConnectionPressureWithoutAddressTags() throws Exception {
        try (CapacityServer server = CapacityServer.http11();
             MeterFixture meters = new MeterFixture();
             ClientFixture fixture = ClientFixture.create(server, false, 2_000, true)) {
            CompletableFuture<String> active = fixture.client().hold("first").toFuture();
            server.awaitPath("/hold/first");
            CompletableFuture<String> pending = fixture.client().hold("pending").toFuture();

            meters.awaitGauge(ProtocolAwareConnectionPoolMeterRegistrar.TOTAL_CONNECTIONS, fixture.poolName(), 1);
            meters.awaitGauge(ProtocolAwareConnectionPoolMeterRegistrar.ACTIVE_CONNECTIONS, fixture.poolName(), 1);
            meters.awaitGauge(ProtocolAwareConnectionPoolMeterRegistrar.PENDING_CONNECTIONS, fixture.poolName(), 1);
            assertThat(meters.find(ProtocolAwareConnectionPoolMeterRegistrar.PENDING_STREAMS, fixture.poolName())).isNull();
            meters.assertAddressFree(fixture.poolName());

            server.releaseFirst();
            assertThat(active.get(5, TimeUnit.SECONDS)).isEqualTo("first");
            assertThat(pending.get(5, TimeUnit.SECONDS)).isEqualTo("pending");
            assertThat(server.transportChannelIds()).hasSize(1);
        }
    }

    @Test
    void h2MetricsReportPeerStreamPressureWithoutInferringAStreamLimit() throws Exception {
        try (CapacityServer server = CapacityServer.h2cOneStream();
             MeterFixture meters = new MeterFixture();
             ClientFixture fixture = ClientFixture.create(server, true, 2_000, true)) {
            CompletableFuture<String> active = fixture.client().hold("first").toFuture();
            server.awaitPath("/hold/first");
            CompletableFuture<String> pending = fixture.client().hold("pending").toFuture();

            meters.awaitGauge(ProtocolAwareConnectionPoolMeterRegistrar.TOTAL_CONNECTIONS, fixture.poolName(), 1);
            meters.awaitGauge(ProtocolAwareConnectionPoolMeterRegistrar.ACTIVE_STREAMS, fixture.poolName(), 1);
            assertThat(meters.find(ProtocolAwareConnectionPoolMeterRegistrar.ACTIVE_CONNECTIONS, fixture.poolName()))
                    .isNull();
            meters.awaitGauge(ProtocolAwareConnectionPoolMeterRegistrar.PENDING_STREAMS, fixture.poolName(), 1);
            assertThat(meters.find(ProtocolAwareConnectionPoolMeterRegistrar.PENDING_CONNECTIONS, fixture.poolName()))
                    .isNull();
            meters.assertAddressFree(fixture.poolName());
            assertThat(server.paths()).containsExactly("/hold/first");

            server.releaseFirst();
            assertThat(active.get(5, TimeUnit.SECONDS)).isEqualTo("first");
            assertThat(pending.get(5, TimeUnit.SECONDS)).isEqualTo("pending");
            assertThat(server.transportChannelIds()).hasSize(1);

            fixture.close();
            assertThat(meters.find(ProtocolAwareConnectionPoolMeterRegistrar.ACTIVE_STREAMS, fixture.poolName()))
                    .isNull();
        }
    }

    @Test
    void h2QueuedCancellationAndTimeoutReleaseCapacityForLaterStreams() throws Exception {
        try (CapacityServer server = CapacityServer.h2cOneStream();
             ClientFixture fixture = ClientFixture.create(server, true, 75, false)) {
            CompletableFuture<String> active = fixture.client().hold("first").toFuture();
            server.awaitPath("/hold/first");

            Disposable cancelled = fixture.client().hold("cancelled").subscribe();
            Thread.sleep(25);
            cancelled.dispose();

            Throwable timeout = catchThrowable(() -> fixture.client().hold("timeout").block(CALL_TIMEOUT));
            assertThat(timeout).isNotNull();
            assertThat(HttpClientFailureStage.from(timeout)).isEqualTo(HttpClientFailureStage.POOL_ACQUIRE);
            assertThat(server.paths()).containsExactly("/hold/first");

            CompletableFuture<String> next = fixture.client().hold("next").toFuture();
            server.releaseFirst();

            assertThat(active.get(5, TimeUnit.SECONDS)).isEqualTo("first");
            assertThat(next.get(5, TimeUnit.SECONDS)).isEqualTo("next");
            assertThat(server.paths()).containsExactly("/hold/first", "/hold/next");
            assertThat(server.transportChannelIds()).hasSize(1);
            assertThat(fixture.client().probe().block(CALL_TIMEOUT)).isEqualTo("probe");
        }
    }

    @Test
    void h2ShutdownDisposesOwnedConnectionAndTerminatesPendingStreamDemand() throws Exception {
        try (CapacityServer server = CapacityServer.h2cOneStream()) {
            ClientFixture fixture = ClientFixture.create(server, true, 2_000, false);
            CompletableFuture<String> active = fixture.client().hold("first").toFuture();
            server.awaitPath("/hold/first");
            CompletableFuture<String> pending = fixture.client().hold("pending").toFuture();

            CompletableFuture<Void> shutdown = CompletableFuture.runAsync(fixture::close);
            Thread.sleep(50);
            server.releaseFirst();
            shutdown.get(5, TimeUnit.SECONDS);

            await(active::isDone, "active stream should terminate during shutdown");
            await(pending::isDone, "pending stream demand should terminate during shutdown");
            assertThat(fixture.connectionProvider().isDisposed()).isTrue();
            assertThat(server.paths()).doesNotContain("/hold/pending");
        }
    }

    private static void await(BooleanSupplier condition, String message) {
        long deadline = System.nanoTime() + Duration.ofSeconds(3).toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(ex);
            }
        }
        assertThat(condition.getAsBoolean()).as(message).isTrue();
    }

    @ReactiveHttpClient(name = "protocol-capacity")
    interface CapacityClient {
        @GET("/hold/{id}")
        Mono<String> hold(@PathVar("id") String id);

        @GET("/probe")
        Mono<String> probe();
    }

    private static final class ClientFixture implements AutoCloseable {
        private final StaticApplicationContext context;
        private final ReactiveHttpClientFactoryBean<CapacityClient> factory;
        private final CapacityClient client;
        private final ConnectionProvider connectionProvider;
        private final String poolName;
        private boolean closed;

        private ClientFixture(StaticApplicationContext context,
                              ReactiveHttpClientFactoryBean<CapacityClient> factory,
                              CapacityClient client,
                              ConnectionProvider connectionProvider) {
            this.context = context;
            this.factory = factory;
            this.client = client;
            this.connectionProvider = connectionProvider;
            this.poolName = connectionProvider.name();
        }

        static ClientFixture create(CapacityServer server,
                                    boolean http2,
                                    long pendingAcquireTimeoutMs,
                                    boolean metricsEnabled) {
            StaticApplicationContext context = new StaticApplicationContext();
            ReactiveHttpClientProperties properties = new ReactiveHttpClientProperties();
            ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();
            config.setBaseUrl(server.baseUrl());
            config.setHttp2Enabled(http2);
            ReactiveHttpClientProperties.ConnectionPoolConfig pool =
                    new ReactiveHttpClientProperties.ConnectionPoolConfig();
            pool.setMaxConnections(1);
            pool.setPendingAcquireTimeoutMs(pendingAcquireTimeoutMs);
            pool.setMetricsEnabled(metricsEnabled);
            config.setPool(pool);
            properties.getClients().put("protocol-capacity", config);
            context.getBeanFactory().registerSingleton("reactiveHttpClientProperties", properties);
            context.getBeanFactory().registerSingleton("starterWebClientBuilder", WebClient.builder());
            context.getBeanFactory().registerSingleton("reactiveHttpClientJsonCodec", TestJsonCodecs.jsonCodec());

            ReactiveHttpClientFactoryBean<CapacityClient> factory = new ReactiveHttpClientFactoryBean<>();
            factory.setType(CapacityClient.class);
            factory.setApplicationContext(context);
            CapacityClient client = factory.getObject();
            return new ClientFixture(context, factory, client, connectionProvider(factory));
        }

        CapacityClient client() {
            return client;
        }

        ConnectionProvider connectionProvider() {
            return connectionProvider;
        }

        String poolName() {
            return poolName;
        }

        @Override
        public void close() {
            if (!closed) {
                closed = true;
                factory.destroy();
                context.close();
            }
        }

        private static ConnectionProvider connectionProvider(ReactiveHttpClientFactoryBean<?> factory) {
            try {
                Field field = ReactiveHttpClientFactoryBean.class.getDeclaredField("connectionProvider");
                field.setAccessible(true);
                return (ConnectionProvider) field.get(factory);
            } catch (ReflectiveOperationException ex) {
                throw new IllegalStateException(ex);
            }
        }
    }

    private static final class MeterFixture implements AutoCloseable {
        private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

        private MeterFixture() {
            Metrics.addRegistry(registry);
        }

        Meter find(String name, String poolName) {
            return registry.find(name).tag("name", poolName).meter();
        }

        void awaitGauge(String name, String poolName, double minimum) {
            await(() -> {
                io.micrometer.core.instrument.Gauge gauge = registry.find(name)
                        .tag("name", poolName)
                        .gauge();
                return gauge != null && gauge.value() >= minimum;
            }, name + " should reach " + minimum);
        }

        void assertAddressFree(String poolName) {
            List<Meter> poolMeters = registry.getMeters().stream()
                    .filter(meter -> poolName.equals(meter.getId().getTag("name")))
                    .toList();
            assertThat(poolMeters).isNotEmpty();
            assertThat(poolMeters).allSatisfy(meter -> assertThat(meter.getId().getTags())
                    .extracting(io.micrometer.core.instrument.Tag::getKey)
                    .containsExactly("name"));
        }

        @Override
        public void close() {
            Metrics.removeRegistry(registry);
            registry.close();
        }
    }

    private static final class CapacityServer implements AutoCloseable {
        private final Sinks.One<Void> firstRelease = Sinks.one();
        private final List<RequestRecord> records = new CopyOnWriteArrayList<>();
        private final DisposableServer server;

        private CapacityServer(HttpServer configured) {
            server = configured
                    .port(0)
                    .handle((request, response) -> {
                        String path = request.path().startsWith("/") ? request.path() : "/" + request.path();
                        request.withConnection(connection -> records.add(
                                new RequestRecord(path, transportChannelId(connection.channel()))));
                        Mono<Void> body = path.equals("/hold/first")
                                ? firstRelease.asMono().then(response.sendString(Mono.just("first")).then())
                                : response.sendString(Mono.just(path.equals("/probe")
                                        ? "probe"
                                        : path.substring(path.lastIndexOf('/') + 1))).then();
                        return body;
                    })
                    .bindNow();
        }

        static CapacityServer http11() {
            return new CapacityServer(HttpServer.create().protocol(HttpProtocol.HTTP11));
        }

        static CapacityServer h2cOneStream() {
            return new CapacityServer(HttpServer.create()
                    .protocol(HttpProtocol.H2C)
                    .http2Settings(settings -> settings.maxConcurrentStreams(1)));
        }

        String baseUrl() {
            return "http://127.0.0.1:" + server.port();
        }

        void releaseFirst() {
            firstRelease.tryEmitEmpty();
        }

        List<String> paths() {
            return records.stream().map(RequestRecord::path).toList();
        }

        List<String> transportChannelIds() {
            return records.stream().map(RequestRecord::transportChannelId).distinct().toList();
        }

        void awaitPath(String path) {
            await(() -> paths().contains(path), "server should receive " + path);
        }

        @Override
        public void close() {
            firstRelease.tryEmitEmpty();
            server.disposeNow(Duration.ofSeconds(5));
        }

        private static String transportChannelId(Channel channel) {
            Channel transport = channel;
            for (Channel current = channel; current != null; current = current.parent()) {
                transport = current;
            }
            return transport.id().asLongText();
        }
    }

    private record RequestRecord(String path, String transportChannelId) {
    }
}
