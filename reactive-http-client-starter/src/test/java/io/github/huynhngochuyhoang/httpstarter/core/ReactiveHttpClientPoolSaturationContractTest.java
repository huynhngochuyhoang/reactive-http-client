package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.annotation.GET;
import io.github.huynhngochuyhoang.httpstarter.annotation.LogHttpExchange;
import io.github.huynhngochuyhoang.httpstarter.annotation.PathVar;
import io.github.huynhngochuyhoang.httpstarter.annotation.ReactiveHttpClient;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import io.github.huynhngochuyhoang.httpstarter.exception.ErrorCategory;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientFailureStage;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientObserver;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientObserverEvent;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;
import reactor.netty.resources.ConnectionProvider;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class ReactiveHttpClientPoolSaturationContractTest {

    private static final Duration CALL_TIMEOUT = Duration.ofSeconds(5);

    @Test
    void queuedAcquireSucceedsAfterTheOnlyConnectionIsReleased() throws Exception {
        try (PoolServer server = new PoolServer(); ClientFixture fixture = ClientFixture.create(server, 2_000, 0, 0, 0)) {
            CompletableFuture<String> active = fixture.client().hold("first").toFuture();
            server.awaitPath("/hold/first");

            CompletableFuture<String> queued = fixture.client().hold("queued").toFuture();
            await(() -> !queued.isDone(), "second request should remain queued");
            assertThat(server.paths()).containsExactly("/hold/first");

            server.releaseFirst();

            assertThat(active.get(5, TimeUnit.SECONDS)).isEqualTo("first");
            assertThat(queued.get(5, TimeUnit.SECONDS)).isEqualTo("queued");
            assertThat(server.paths()).containsExactly("/hold/first", "/hold/queued");
            assertThat(server.maxActiveRequests()).isEqualTo(1);
            assertThat(server.channelIds()).containsOnly(server.channelIds().getFirst());
        }
    }

    @Test
    void acquireTimeoutKeepsErrorCategoryAndReportsProvenStageAcrossSurfaces() {
        try (PoolServer server = new PoolServer(); ClientFixture fixture = ClientFixture.create(server, 75, 0, 0, 0)) {
            CompletableFuture<String> active = fixture.client().hold("first").toFuture();
            server.awaitPath("/hold/first");
            fixture.recording().clear();

            Throwable failure = catchThrowable(() -> fixture.client().hold("timeout").block(CALL_TIMEOUT));

            assertThat(failure).isNotNull();
            assertThat(HttpClientFailureStage.from(failure)).isEqualTo(HttpClientFailureStage.POOL_ACQUIRE);
            assertThat(HttpClientFailureStage.from(failure, null, false))
                    .isEqualTo(HttpClientFailureStage.POOL_ACQUIRE);
            assertThat(HttpClientFailureStage.from(new java.util.concurrent.TimeoutException())).isNull();
            assertThat(fixture.recording().events).singleElement().satisfies(event -> {
                assertThat(event.getErrorCategory()).isEqualTo(ErrorCategory.TIMEOUT);
                assertThat(event.getFailureStage()).isEqualTo(HttpClientFailureStage.POOL_ACQUIRE);
            });
            assertThat(fixture.recording().lifecycleErrors).singleElement()
                    .satisfies(context -> assertThat(context.failureStage()).isEqualTo(HttpClientFailureStage.POOL_ACQUIRE));
            assertThat(fixture.recording().exchangeLogs).singleElement()
                    .satisfies(context -> assertThat(context.failureStage()).isEqualTo(HttpClientFailureStage.POOL_ACQUIRE));
            assertThat(server.paths()).containsExactly("/hold/first");

            server.releaseFirst();
            assertThat(active.join()).isEqualTo("first");
            assertThat(fixture.client().probe().block(CALL_TIMEOUT)).isEqualTo("probe");
        }
    }

    @Test
    void cancellingAQueuedAcquireDoesNotConsumeTheNextConnection() throws Exception {
        try (PoolServer server = new PoolServer(); ClientFixture fixture = ClientFixture.create(server, 2_000, 0, 0, 0)) {
            CompletableFuture<String> active = fixture.client().hold("first").toFuture();
            server.awaitPath("/hold/first");
            List<Throwable> cancelledErrors = new CopyOnWriteArrayList<>();
            Disposable cancelled = fixture.client().hold("cancelled").subscribe(ignored -> { }, cancelledErrors::add);
            Thread.sleep(50);
            cancelled.dispose();

            CompletableFuture<String> next = fixture.client().hold("next").toFuture();
            server.releaseFirst();

            assertThat(active.get(5, TimeUnit.SECONDS)).isEqualTo("first");
            assertThat(next.get(5, TimeUnit.SECONDS)).isEqualTo("next");
            assertThat(cancelledErrors).isEmpty();
            assertThat(server.paths()).containsExactly("/hold/first", "/hold/next");
            assertThat(fixture.client().probe().block(CALL_TIMEOUT)).isEqualTo("probe");
        }
    }

    @Test
    void backgroundEvictionRemovesIdleAndExpiredConnections() {
        assertBackgroundEviction(60, 0);
        assertBackgroundEviction(0, 60);
    }

    @Test
    void shutdownTerminatesActiveAndPendingWorkWithoutLeakingTheProvider() throws Exception {
        try (PoolServer server = new PoolServer()) {
            ClientFixture fixture = ClientFixture.create(server, 2_000, 0, 0, 0);
            CompletableFuture<String> active = fixture.client().hold("first").toFuture();
            server.awaitPath("/hold/first");
            CompletableFuture<String> pending = fixture.client().hold("pending").toFuture();
            Thread.sleep(50);

            CompletableFuture<Void> shutdown = CompletableFuture.runAsync(fixture::close);
            Thread.sleep(50);
            server.releaseFirst();
            shutdown.get(5, TimeUnit.SECONDS);

            await(active::isDone, "active request should terminate during shutdown");
            await(pending::isDone, "pending acquire should terminate during shutdown");
            assertThat(fixture.connectionProvider().isDisposed()).isTrue();
            assertThat(server.paths()).doesNotContain("/hold/pending");
        }
    }

    private static void assertBackgroundEviction(long maxIdleTimeMs, long maxLifeTimeMs) {
        try (PoolServer server = new PoolServer();
             ClientFixture fixture = ClientFixture.create(server, 2_000, maxIdleTimeMs, maxLifeTimeMs, 20)) {
            assertThat(fixture.client().probe().block(CALL_TIMEOUT)).isEqualTo("probe");
            String firstChannel = server.channelIds().getFirst();
            sleep(180);
            assertThat(fixture.client().probe().block(CALL_TIMEOUT)).isEqualTo("probe");
            assertThat(server.channelIds().getLast()).isNotEqualTo(firstChannel);
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        }
        catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }

    private static void await(BooleanSupplier condition, String message) {
        long deadline = System.nanoTime() + Duration.ofSeconds(3).toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(20);
            }
            catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(ex);
            }
        }
        assertThat(condition.getAsBoolean()).as(message).isTrue();
    }

    @ReactiveHttpClient(name = "pool-contract")
    @LogHttpExchange(logger = RecordingDiagnostics.class)
    interface PoolClient {
        @GET("/hold/{id}")
        Mono<String> hold(@PathVar("id") String id);

        @GET("/probe")
        Mono<String> probe();
    }

    private static final class RecordingDiagnostics implements HttpClientObserver,
            ReactiveHttpClientLifecycleHook, HttpExchangeLogger {
        private final List<HttpClientObserverEvent> events = new CopyOnWriteArrayList<>();
        private final List<ReactiveHttpClientLifecycleContext> lifecycleErrors = new CopyOnWriteArrayList<>();
        private final List<HttpExchangeLogContext> exchangeLogs = new CopyOnWriteArrayList<>();

        @Override
        public void record(HttpClientObserverEvent event) {
            events.add(event);
        }

        @Override
        public void onError(ReactiveHttpClientLifecycleContext context) {
            lifecycleErrors.add(context);
        }

        @Override
        public void log(HttpExchangeLogContext context) {
            exchangeLogs.add(context);
        }

        void clear() {
            events.clear();
            lifecycleErrors.clear();
            exchangeLogs.clear();
        }
    }

    private static final class ClientFixture implements AutoCloseable {
        private final StaticApplicationContext context;
        private final ReactiveHttpClientFactoryBean<PoolClient> factory;
        private final PoolClient client;
        private final ConnectionProvider connectionProvider;
        private final RecordingDiagnostics recording;
        private boolean closed;

        private ClientFixture(StaticApplicationContext context,
                              ReactiveHttpClientFactoryBean<PoolClient> factory,
                              PoolClient client,
                              ConnectionProvider connectionProvider,
                              RecordingDiagnostics recording) {
            this.context = context;
            this.factory = factory;
            this.client = client;
            this.connectionProvider = connectionProvider;
            this.recording = recording;
        }

        static ClientFixture create(PoolServer server,
                                    long acquireTimeoutMs,
                                    long maxIdleTimeMs,
                                    long maxLifeTimeMs,
                                    long backgroundEvictionMs) {
            StaticApplicationContext context = new StaticApplicationContext();
            ReactiveHttpClientProperties properties = new ReactiveHttpClientProperties();
            ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();
            config.setBaseUrl("http://127.0.0.1:" + server.port());
            ReactiveHttpClientProperties.ConnectionPoolConfig pool =
                    new ReactiveHttpClientProperties.ConnectionPoolConfig();
            pool.setMaxConnections(1);
            pool.setPendingAcquireTimeoutMs(acquireTimeoutMs);
            pool.setMaxIdleTimeMs(maxIdleTimeMs);
            pool.setMaxLifeTimeMs(maxLifeTimeMs);
            pool.setEvictInBackgroundMs(backgroundEvictionMs);
            config.setPool(pool);
            properties.getClients().put("pool-contract", config);
            RecordingDiagnostics recording = new RecordingDiagnostics();
            context.getBeanFactory().registerSingleton("reactiveHttpClientProperties", properties);
            context.getBeanFactory().registerSingleton("starterWebClientBuilder", WebClient.builder());
            context.getBeanFactory().registerSingleton("reactiveHttpClientJsonCodec", TestJsonCodecs.jsonCodec());
            context.getBeanFactory().registerSingleton("poolObserver", recording);

            ReactiveHttpClientFactoryBean<PoolClient> factory = new ReactiveHttpClientFactoryBean<>();
            factory.setType(PoolClient.class);
            factory.setApplicationContext(context);
            PoolClient client = factory.getObject();
            return new ClientFixture(context, factory, client, connectionProvider(factory), recording);
        }

        PoolClient client() {
            return client;
        }

        ConnectionProvider connectionProvider() {
            return connectionProvider;
        }

        RecordingDiagnostics recording() {
            return recording;
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
            }
            catch (ReflectiveOperationException ex) {
                throw new IllegalStateException(ex);
            }
        }
    }

    private static final class PoolServer implements AutoCloseable {
        private final Sinks.One<Void> firstRelease = Sinks.one();
        private final List<RequestRecord> records = new CopyOnWriteArrayList<>();
        private final AtomicInteger activeRequests = new AtomicInteger();
        private final AtomicInteger maxActiveRequests = new AtomicInteger();
        private final DisposableServer server;

        private PoolServer() {
            server = HttpServer.create()
                    .port(0)
                    .handle((request, response) -> {
                        String path = request.uri().split("\\?", 2)[0];
                        request.withConnection(connection -> records.add(new RequestRecord(
                                path, connection.channel().id().asLongText())));
                        int active = activeRequests.incrementAndGet();
                        maxActiveRequests.accumulateAndGet(active, Math::max);
                        Mono<Void> result = path.equals("/hold/first")
                                ? firstRelease.asMono().then(response.sendString(Mono.just("first")).then())
                                : response.sendString(Mono.just(path.equals("/probe")
                                        ? "probe"
                                        : path.substring(path.lastIndexOf('/') + 1))).then();
                        return result.doFinally(ignored -> activeRequests.decrementAndGet());
                    })
                    .bindNow();
        }

        int port() {
            return server.port();
        }

        void releaseFirst() {
            firstRelease.tryEmitEmpty();
        }

        List<String> paths() {
            return records.stream().map(RequestRecord::path).toList();
        }

        List<String> channelIds() {
            return records.stream().map(RequestRecord::channelId).toList();
        }

        int maxActiveRequests() {
            return maxActiveRequests.get();
        }

        void awaitPath(String path) {
            await(() -> paths().contains(path), "server should receive " + path);
        }

        @Override
        public void close() {
            firstRelease.tryEmitEmpty();
            server.disposeNow(Duration.ofSeconds(5));
        }
    }

    private record RequestRecord(String path, String channelId) {
    }
}
