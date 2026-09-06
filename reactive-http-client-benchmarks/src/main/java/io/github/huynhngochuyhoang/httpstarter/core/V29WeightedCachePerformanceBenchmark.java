package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.annotation.CacheResponse;
import io.github.huynhngochuyhoang.httpstarter.annotation.GET;
import io.github.huynhngochuyhoang.httpstarter.annotation.PathVar;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import reactor.netty.DisposableServer;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.server.HttpServer;
import reactor.netty.resources.ConnectionProvider;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@State(Scope.Benchmark)
@BenchmarkMode({Mode.Throughput, Mode.AverageTime})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
public class V29WeightedCachePerformanceBenchmark {

    private static final String VALUE = "value";
    private static final long VALUE_BYTES = VALUE.getBytes(StandardCharsets.UTF_8).length;
    private static final long TTL_MS = Duration.ofDays(1).toMillis();
    private static final long MAXIMUM_SIZE = 256;

    private final AtomicLong sequence = new AtomicLong();
    private final AtomicLong ticker = new AtomicLong();
    private final AtomicLong loopbackTicker = new AtomicLong();
    private final AtomicLong loopbackDispatches = new AtomicLong();

    private GenericApplicationContext noNetworkContext;
    private LocalResponseCacheManager unweightedInvocationManager;
    private LocalResponseCacheManager weightedInvocationManager;
    private WeightedCacheClient unweightedClient;
    private WeightedCacheClient weightedClient;

    private LocalResponseCacheManager hitManager;
    private LocalResponseCacheManager publicationManager;
    private LocalResponseCacheManager bypassManager;
    private LocalResponseCacheManager sizeManager;
    private LocalResponseCacheManager weightManager;
    private LocalResponseCacheManager singleFlightManager;
    private LocalResponseCacheManager refreshManager;
    private LocalResponseCacheManager meteredManager;
    private EffectiveCachePolicy.Selection hitSelection;
    private EffectiveCachePolicy.Selection publicationSelection;
    private EffectiveCachePolicy.Selection bypassSelection;
    private EffectiveCachePolicy.Selection sizeSelection;
    private EffectiveCachePolicy.Selection weightSelection;
    private EffectiveCachePolicy.Selection singleFlightSelection;
    private EffectiveCachePolicy.Selection refreshSelection;
    private EffectiveCachePolicy.Selection meteredSelection;
    private SimpleMeterRegistry meterRegistry;
    private Scheduler refreshScheduler;

    private DisposableServer loopbackServer;
    private ConnectionProvider connectionProvider;
    private GenericApplicationContext loopbackContext;
    private LocalResponseCacheManager loopbackManager;
    private WeightedLoopbackClient loopbackClient;

    @Setup(Level.Trial)
    public void setup() {
        refreshScheduler = Schedulers.newSingle("v29-cache-benchmark-refresh");
        setupInvocationClients();
        setupNoNetworkManagers();
        setupLoopbackClient();
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        close(unweightedInvocationManager);
        close(weightedInvocationManager);
        close(hitManager);
        close(publicationManager);
        close(bypassManager);
        close(sizeManager);
        close(weightManager);
        close(singleFlightManager);
        close(refreshManager);
        close(meteredManager);
        close(loopbackManager);
        if (noNetworkContext != null) {
            noNetworkContext.close();
        }
        if (loopbackContext != null) {
            loopbackContext.close();
        }
        if (connectionProvider != null) {
            connectionProvider.disposeLater().block(Duration.ofSeconds(5));
        }
        if (loopbackServer != null) {
            loopbackServer.disposeNow(Duration.ofSeconds(5));
        }
        if (meterRegistry != null) {
            meterRegistry.close();
        }
        if (refreshScheduler != null) {
            refreshScheduler.dispose();
        }
    }

    @Benchmark
    public Mono<String> cacheV29NoNetworkUnweightedPublisherCreation() {
        return unweightedClient.unweighted("publisher");
    }

    @Benchmark
    public String cacheV29NoNetworkUnweightedSubscription() {
        return requireValue(unweightedClient.unweighted(nextId("unweighted")).block());
    }

    @Benchmark
    public Mono<String> cacheV29NoNetworkWeightedMetricsDisabledPublisherCreation() {
        return weightedClient.weighted("publisher");
    }

    @Benchmark
    public String cacheV29NoNetworkWeightedMetricsDisabledSubscription() {
        return requireValue(weightedClient.weighted(nextId("weighted")).block());
    }

    @Benchmark
    public Object cacheV29NoNetworkWeightedHit() {
        return weightedCached(hitManager, hitSelection, key("hit"), VALUE, VALUE_BYTES).block();
    }

    @Benchmark
    public Object cacheV29NoNetworkMissPublication() {
        return weightedCached(
                publicationManager, publicationSelection, nextKey("publication"), VALUE, VALUE_BYTES).block();
    }

    @Benchmark
    public Object cacheV29NoNetworkBypassedAdmission() {
        return weightedCached(bypassManager, bypassSelection, nextKey("bypass"), VALUE, 16).block();
    }

    @Benchmark
    public long cacheV29NoNetworkSizeEviction() {
        cached(sizeManager, sizeSelection, nextKey("size"), Mono.just(VALUE)).block();
        return sizeManager.snapshot().evictions();
    }

    @Benchmark
    public long cacheV29NoNetworkWeightEviction() {
        weightedCached(weightManager, weightSelection, nextKey("weight"), VALUE, VALUE_BYTES).block();
        return weightManager.snapshot().evictions();
    }

    @Benchmark
    public List<Object> cacheV29NoNetworkSingleFlightAttachment() {
        CacheKeyContract.OpaqueKey key = nextKey("single");
        Sinks.One<Object> source = Sinks.one();
        Mono<Object> call = weightedCached(
                singleFlightManager, singleFlightSelection, key, source.asMono(), VALUE_BYTES);
        CompletableFuture<Object> leader = call.toFuture();
        CompletableFuture<Object> waiter = call.toFuture();
        source.tryEmitValue(VALUE).orThrow();
        return List.of(leader.join(), waiter.join());
    }

    @Benchmark
    public long cacheV29NoNetworkRefreshReplacement() {
        ticker.addAndGet(Duration.ofMillis(2).toNanos());
        weightedCached(refreshManager, refreshSelection, key("refresh"), VALUE, VALUE_BYTES).block();
        awaitRefreshCompletion(refreshManager);
        return refreshManager.retainedDecodedResponseBytesForTesting();
    }

    @Benchmark
    public LocalResponseCacheManager.Snapshot cacheV29NoNetworkAccountingSnapshot() {
        weightedCached(
                publicationManager, publicationSelection, nextKey("accounting"), VALUE, VALUE_BYTES).block();
        return publicationManager.snapshot();
    }

    @Benchmark
    public LocalResponseCacheManager.Snapshot cacheV29NoNetworkMeteredAccountingPublication() {
        weightedCached(meteredManager, meteredSelection, nextKey("metered"), VALUE, VALUE_BYTES).block();
        return meteredManager.snapshot();
    }

    @Benchmark
    public String cacheV29LoopbackMissPublication() {
        long before = loopbackDispatches.get();
        String result = requireValue(loopbackClient.miss(nextId("miss")).block());
        requireDispatchDelta(before, 1, "weighted miss publication");
        return result;
    }

    @Benchmark
    public String cacheV29LoopbackBypassedAdmission() {
        long before = loopbackDispatches.get();
        String result = loopbackClient.bypass(nextId("bypass")).block();
        if (!"oversized".equals(result)) {
            throw new IllegalStateException("Expected oversized but received " + result);
        }
        requireDispatchDelta(before, 1, "over-budget admission bypass");
        return result;
    }

    @Benchmark
    public long cacheV29LoopbackSizeEviction() {
        long before = loopbackDispatches.get();
        requireValue(loopbackClient.size(nextId("size")).block());
        requireDispatchDelta(before, 1, "size eviction");
        return loopbackManager.snapshot().evictions();
    }

    @Benchmark
    public long cacheV29LoopbackWeightEviction() {
        long before = loopbackDispatches.get();
        requireValue(loopbackClient.weight(nextId("weight")).block());
        requireDispatchDelta(before, 1, "weight eviction");
        return loopbackManager.snapshot().evictions();
    }

    @Benchmark
    public List<String> cacheV29LoopbackSingleFlightAttachment() {
        long before = loopbackDispatches.get();
        String id = nextId("single");
        Mono<String> call = loopbackClient.single(id);
        CompletableFuture<String> leader = call.toFuture();
        CompletableFuture<String> waiter = call.toFuture();
        List<String> result = List.of(requireValue(leader.join()), requireValue(waiter.join()));
        requireDispatchDelta(before, 1, "single-flight attachment");
        return result;
    }

    @Benchmark
    public long cacheV29LoopbackRefreshReplacement() {
        long before = loopbackDispatches.get();
        loopbackTicker.addAndGet(Duration.ofMillis(2).toNanos());
        requireValue(loopbackClient.refresh("refresh").block());
        awaitRefreshCompletion(loopbackManager);
        requireDispatchDelta(before, 1, "refresh replacement");
        return loopbackManager.retainedDecodedResponseBytesForTesting();
    }

    @Benchmark
    public LocalResponseCacheManager.Snapshot cacheV29LoopbackAccountingPublication() {
        long before = loopbackDispatches.get();
        requireValue(loopbackClient.accounting(nextId("accounting")).block());
        requireDispatchDelta(before, 1, "loopback accounting publication");
        return loopbackManager.snapshot();
    }

    private void setupInvocationClients() {
        noNetworkContext = new GenericApplicationContext();
        noNetworkContext.refresh();
        WebClient webClient = WebClient.builder()
                .baseUrl("http://benchmark.local")
                .filter(ReactiveClientInvocationHandler.finalRequestObservationFilter())
                .exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.OK)
                        .header("Content-Type", MediaType.TEXT_PLAIN_VALUE)
                        .body(VALUE)
                        .build()))
                .build();

        ReactiveHttpClientProperties.ClientConfig unweightedConfig = config();
        unweightedConfig.getCache().getPolicies().put(
                "unweighted", policy(MAXIMUM_SIZE, null, false, null, null));
        unweightedInvocationManager = LocalResponseCacheManager.testing(ticker::get, refreshScheduler);
        unweightedClient = client(webClient, unweightedConfig, unweightedInvocationManager);

        ReactiveHttpClientProperties.ClientConfig weightedConfig = config();
        weightedConfig.getCache().getPolicies().put(
                "weighted", policy(MAXIMUM_SIZE, 1_024L, false, null, null));
        weightedInvocationManager = LocalResponseCacheManager.testing(ticker::get, refreshScheduler);
        weightedClient = client(webClient, weightedConfig, weightedInvocationManager);
    }

    private void setupNoNetworkManagers() {
        hitManager = manager();
        publicationManager = manager();
        bypassManager = manager();
        sizeManager = manager();
        weightManager = manager();
        singleFlightManager = manager();
        refreshManager = manager();

        hitSelection = selection("hit", MAXIMUM_SIZE, 1_024L, false, null, null);
        publicationSelection = selection("publication", MAXIMUM_SIZE, 1_024L, false, null, null);
        bypassSelection = selection("bypass", MAXIMUM_SIZE, 8L, false, null, null);
        sizeSelection = selection("size", 1, null, false, null, null);
        weightSelection = selection("weight", MAXIMUM_SIZE, 8L, false, null, null);
        singleFlightSelection = selection("single", MAXIMUM_SIZE, 1_024L, true, null, null);
        refreshSelection = selection("refresh", MAXIMUM_SIZE, 1_024L, false, 1L, 1_000L);
        weightedCached(hitManager, hitSelection, key("hit"), VALUE, VALUE_BYTES).block();
        cached(sizeManager, sizeSelection, key("size-seed"), Mono.just(VALUE)).block();
        weightedCached(weightManager, weightSelection, key("weight-seed"), VALUE, VALUE_BYTES).block();
        weightedCached(refreshManager, refreshSelection, key("refresh"), VALUE, VALUE_BYTES).block();

        meterRegistry = new SimpleMeterRegistry();
        LocalResponseCacheMetrics metrics = LocalResponseCacheMetrics.enabled(meterRegistry, "benchmark-v29");
        metrics.registerApi("weighted.accounting");
        meteredManager = LocalResponseCacheManager.testing(
                ticker::get, refreshScheduler, metrics, "benchmark-v29");
        meteredSelection = selection("metered", MAXIMUM_SIZE, 1_024L, false, null, null);
    }

    private void setupLoopbackClient() {
        loopbackServer = HttpServer.create()
                .host("127.0.0.1")
                .port(0)
                .handle((request, response) -> {
                    loopbackDispatches.incrementAndGet();
                    String requestTarget = request.uri();
                    String value = requestTarget.contains("/v29/bypass/") ? "oversized" : VALUE;
                    Mono<String> body = requestTarget.contains("/v29/single/")
                            ? Mono.delay(Duration.ofMillis(2)).map(ignored -> value)
                            : Mono.just(value);
                    return response.header("Content-Type", MediaType.TEXT_PLAIN_VALUE)
                            .sendString(body)
                            .then();
                })
                .bindNow();
        connectionProvider = ConnectionProvider.create("v29-cache-benchmark");
        WebClient webClient = WebClient.builder()
                .baseUrl("http://127.0.0.1:" + loopbackServer.port())
                .clientConnector(new ReactorClientHttpConnector(HttpClient.create(connectionProvider)))
                .filter(ReactiveClientInvocationHandler.finalRequestObservationFilter())
                .build();

        ReactiveHttpClientProperties.ClientConfig config = config();
        config.getCache().getPolicies().put("miss", policy(MAXIMUM_SIZE, 1_024L, false, null, null));
        config.getCache().getPolicies().put("bypass", policy(MAXIMUM_SIZE, 4L, false, null, null));
        config.getCache().getPolicies().put("size", policy(1, null, false, null, null));
        config.getCache().getPolicies().put("weight", policy(MAXIMUM_SIZE, 8L, false, null, null));
        config.getCache().getPolicies().put("single", policy(MAXIMUM_SIZE, 32L, true, null, null));
        config.getCache().getPolicies().put("refresh", policy(MAXIMUM_SIZE, 32L, false, 1L, 1_000L));
        config.getCache().getPolicies().put("accounting", policy(MAXIMUM_SIZE, 1_024L, false, null, null));

        loopbackContext = new GenericApplicationContext();
        loopbackContext.refresh();
        loopbackManager = LocalResponseCacheManager.testing(loopbackTicker::get, refreshScheduler);
        MethodMetadataCache metadataCache = new MethodMetadataCache();
        ReactiveClientInvocationHandler handler = new ReactiveClientInvocationHandler(
                webClient,
                metadataCache,
                new RequestArgumentResolver(),
                new DefaultErrorDecoder(),
                config,
                "benchmark-v29-loopback",
                WeightedLoopbackClient.class,
                loopbackContext,
                new NoopResilienceOperatorApplier(),
                null,
                new ReactiveHttpClientProperties.ObservabilityConfig(),
                loopbackManager);
        loopbackClient = (WeightedLoopbackClient) Proxy.newProxyInstance(
                WeightedLoopbackClient.class.getClassLoader(),
                new Class<?>[]{WeightedLoopbackClient.class},
                handler);
        requireValue(loopbackClient.size("size-seed").block());
        requireValue(loopbackClient.weight("weight-seed").block());
        requireValue(loopbackClient.refresh("refresh").block());
    }

    private WeightedCacheClient client(
            WebClient webClient,
            ReactiveHttpClientProperties.ClientConfig config,
            LocalResponseCacheManager manager) {
        ReactiveClientInvocationHandler handler = new ReactiveClientInvocationHandler(
                webClient,
                new MethodMetadataCache(),
                new RequestArgumentResolver(),
                new DefaultErrorDecoder(),
                config,
                "benchmark-v29-no-network",
                WeightedCacheClient.class,
                noNetworkContext,
                new NoopResilienceOperatorApplier(),
                null,
                new ReactiveHttpClientProperties.ObservabilityConfig(),
                manager);
        return (WeightedCacheClient) Proxy.newProxyInstance(
                WeightedCacheClient.class.getClassLoader(),
                new Class<?>[]{WeightedCacheClient.class},
                handler);
    }

    private LocalResponseCacheManager manager() {
        return LocalResponseCacheManager.testing(ticker::get, refreshScheduler);
    }

    private String nextId(String prefix) {
        return prefix + '-' + sequence.getAndIncrement();
    }

    private CacheKeyContract.OpaqueKey nextKey(String prefix) {
        return key(nextId(prefix));
    }

    private void requireDispatchDelta(long before, long expected, String scenario) {
        long actual = loopbackDispatches.get() - before;
        if (actual != expected) {
            throw new IllegalStateException(scenario + " dispatched " + actual
                    + " requests instead of " + expected);
        }
    }

    private static String requireValue(String actual) {
        if (!VALUE.equals(actual)) {
            throw new IllegalStateException("Expected " + VALUE + " but received " + actual);
        }
        return actual;
    }

    private static void awaitRefreshCompletion(LocalResponseCacheManager manager) {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (System.nanoTime() < deadline) {
            if (manager.workloadSnapshotForTesting().inFlightRefreshes() == 0) {
                return;
            }
            Thread.onSpinWait();
        }
        throw new IllegalStateException("Timed out waiting for benchmark refresh completion");
    }

    private static void close(LocalResponseCacheManager manager) {
        if (manager != null) {
            manager.close();
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> Mono<T> cached(
            LocalResponseCacheManager manager,
            EffectiveCachePolicy.Selection selection,
            CacheKeyContract.OpaqueKey key,
            Mono<T> loader) {
        return (Mono<T>) manager.getOrLoad(selection, key, () -> loader);
    }

    private static <T> Mono<T> weightedCached(
            LocalResponseCacheManager manager,
            EffectiveCachePolicy.Selection selection,
            CacheKeyContract.OpaqueKey key,
            T value,
            long decodedResponseBytes) {
        return weightedCached(manager, selection, key, Mono.just(value), decodedResponseBytes);
    }

    @SuppressWarnings("unchecked")
    private static <T> Mono<T> weightedCached(
            LocalResponseCacheManager manager,
            EffectiveCachePolicy.Selection selection,
            CacheKeyContract.OpaqueKey key,
            Mono<T> loader,
            long decodedResponseBytes) {
        return (Mono<T>) manager.getOrLoad(selection, key, () -> loader,
                () -> responseMetadata(selection, decodedResponseBytes));
    }

    private static LocalResponseCacheManager.ResponseMetadata responseMetadata(
            EffectiveCachePolicy.Selection selection, long decodedResponseBytes) {
        long maximum = selection.policy().getMaximumTotalDecodedResponseBytes();
        LocalResponseCacheManager.DecodedResponseBytes measurement =
                new LocalResponseCacheManager.DecodedResponseBytes(maximum);
        measurement.add(decodedResponseBytes);
        measurement.complete();
        return new LocalResponseCacheManager.ResponseMetadata(200, Map.of(), true, measurement);
    }

    private static CacheKeyContract.OpaqueKey key(String value) {
        return CacheKeyContract.OpaqueKey.from(value.getBytes(StandardCharsets.UTF_8));
    }

    private static EffectiveCachePolicy.Selection selection(
            String name,
            long maximumSize,
            Long maximumDecodedResponseBytes,
            boolean singleFlight,
            Long refreshAfterMs,
            Long refreshTimeoutMs) {
        return new EffectiveCachePolicy.Selection(
                true,
                EffectiveCachePolicy.Source.CLIENT,
                name,
                policy(maximumSize, maximumDecodedResponseBytes, singleFlight,
                        refreshAfterMs, refreshTimeoutMs));
    }

    private static ReactiveHttpClientProperties.CachePolicyConfig policy(
            long maximumSize,
            Long maximumDecodedResponseBytes,
            boolean singleFlight,
            Long refreshAfterMs,
            Long refreshTimeoutMs) {
        ReactiveHttpClientProperties.CachePolicyConfig policy =
                new ReactiveHttpClientProperties.CachePolicyConfig();
        policy.setTtlMs(TTL_MS);
        policy.setMaximumSize(maximumSize);
        policy.setMaximumTotalDecodedResponseBytes(maximumDecodedResponseBytes);
        policy.setSingleFlight(singleFlight);
        policy.setSharedResponse(true);
        policy.setRefreshAfterMs(refreshAfterMs);
        policy.setRefreshTimeoutMs(refreshTimeoutMs);
        return policy;
    }

    private static ReactiveHttpClientProperties.ClientConfig config() {
        ReactiveHttpClientProperties.ClientConfig config =
                new ReactiveHttpClientProperties.ClientConfig();
        config.setBaseUrl("http://benchmark.local");
        return config;
    }

    interface WeightedCacheClient {
        @GET("/v29/unweighted/{id}")
        @CacheResponse("unweighted")
        Mono<String> unweighted(@PathVar("id") String id);

        @GET("/v29/weighted/{id}")
        @CacheResponse("weighted")
        Mono<String> weighted(@PathVar("id") String id);
    }

    interface WeightedLoopbackClient {
        @GET("/v29/miss/{id}")
        @CacheResponse("miss")
        Mono<String> miss(@PathVar("id") String id);

        @GET("/v29/bypass/{id}")
        @CacheResponse("bypass")
        Mono<String> bypass(@PathVar("id") String id);

        @GET("/v29/size/{id}")
        @CacheResponse("size")
        Mono<String> size(@PathVar("id") String id);

        @GET("/v29/weight/{id}")
        @CacheResponse("weight")
        Mono<String> weight(@PathVar("id") String id);

        @GET("/v29/single/{id}")
        @CacheResponse("single")
        Mono<String> single(@PathVar("id") String id);

        @GET("/v29/refresh/{id}")
        @CacheResponse("refresh")
        Mono<String> refresh(@PathVar("id") String id);

        @GET("/v29/accounting/{id}")
        @CacheResponse("accounting")
        Mono<String> accounting(@PathVar("id") String id);
    }
}
