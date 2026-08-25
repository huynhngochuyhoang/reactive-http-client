package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.annotation.CacheResponse;
import io.github.huynhngochuyhoang.httpstarter.annotation.GET;
import io.github.huynhngochuyhoang.httpstarter.annotation.HeaderParam;
import io.github.huynhngochuyhoang.httpstarter.annotation.PathVar;
import io.github.huynhngochuyhoang.httpstarter.annotation.QueryParam;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
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
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import reactor.netty.DisposableServer;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.server.HttpServer;
import reactor.netty.resources.ConnectionProvider;
import reactor.util.context.Context;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
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
public class V27CachePerformanceBenchmark {

    private static final String VALUE = "cache-value";
    private static final int KEY_RING_SIZE = 2_048;
    private static final int REFRESH_KEY_COUNT = 64;
    private static final long TTL_MS = Duration.ofDays(1).toMillis();
    private static final long MAXIMUM_SIZE = 256;

    private final AtomicLong sequence = new AtomicLong();
    private final AtomicLong cacheTicker = new AtomicLong();
    private final AtomicLong loopbackTicker = new AtomicLong();
    private final AtomicLong loopbackDispatches = new AtomicLong();

    private CacheKeyContract.OpaqueKey[] keys;
    private CaffeineLocalResponseCache hitCache;
    private CaffeineLocalResponseCache missCache;
    private CaffeineLocalResponseCache loaderCache;
    private CaffeineLocalResponseCache evictionCache;
    private LocalResponseCacheManager waiterManager;
    private LocalResponseCacheManager refreshManager;
    private EffectiveCachePolicy.Selection singleFlightSelection;
    private EffectiveCachePolicy.Selection refreshSelection;
    private Scheduler refreshScheduler;

    private RequestPlan keyPlan;
    private RequestArgumentResolver keyArgumentResolver;
    private ReactiveHttpClientProperties.CachePolicyConfig keyPolicy;
    private Object[] keyArguments;

    private DisposableServer loopbackServer;
    private ConnectionProvider loopbackConnectionProvider;
    private GenericApplicationContext applicationContext;
    private LocalResponseCacheManager loopbackCacheManager;
    private CacheBenchmarkClient loopbackClient;
    private Field inFlightRefreshesField;

    @Setup(Level.Trial)
    public void setup() throws Exception {
        keys = new CacheKeyContract.OpaqueKey[KEY_RING_SIZE];
        for (int index = 0; index < keys.length; index++) {
            keys[index] = opaqueKey("benchmark-key-" + index);
        }

        hitCache = cache(MAXIMUM_SIZE);
        missCache = cache(1);
        loaderCache = cache(MAXIMUM_SIZE);
        evictionCache = cache(1);
        publish(hitCache, keys[0], VALUE);

        refreshScheduler = Schedulers.newSingle("v27-cache-benchmark-refresh");
        waiterManager = LocalResponseCacheManager.testing(cacheTicker::get, refreshScheduler);
        refreshManager = LocalResponseCacheManager.testing(cacheTicker::get, refreshScheduler);
        singleFlightSelection = selection("single-flight", TTL_MS, MAXIMUM_SIZE, true, null, null);
        refreshSelection = selection("refresh", TTL_MS, MAXIMUM_SIZE, false, 1L, 1_000L);
        for (int index = 0; index < REFRESH_KEY_COUNT; index++) {
            cached(refreshManager, refreshSelection, keys[index], () -> Mono.just(VALUE)).block();
        }

        setupKeyConstruction();
        setupLoopbackClient();
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        hitCache.close();
        missCache.close();
        loaderCache.close();
        evictionCache.close();
        waiterManager.close();
        refreshManager.close();
        if (loopbackCacheManager != null) {
            loopbackCacheManager.close();
        }
        if (applicationContext != null) {
            applicationContext.close();
        }
        if (loopbackConnectionProvider != null) {
            loopbackConnectionProvider.disposeLater().block(Duration.ofSeconds(5));
        }
        if (loopbackServer != null) {
            loopbackServer.disposeNow(Duration.ofSeconds(5));
        }
        if (refreshScheduler != null) {
            refreshScheduler.dispose();
        }
    }

    @Benchmark
    public CacheKeyContract.OpaqueKey cacheKeyConstructionPathQueryHeader() {
        Object[] frozen = CacheKeyContract.freezeArguments(keyPlan, keyArguments, keyPolicy);
        RequestArgumentResolver.ResolvedArgs resolved = CacheKeyContract.snapshotRequestTarget(
                keyArgumentResolver.resolve(keyPlan, frozen));
        return CacheKeyContract.derive(
                KeyContractClient.class,
                "benchmark-key-client",
                keyPlan,
                frozen,
                resolved,
                Context.empty(),
                keyPolicy).key();
    }

    @Benchmark
    public Object cacheAllocationFreshHit() {
        LocalResponseCache.Lookup lookup = hitCache.lookup(keys[0]);
        if (!lookup.hit()) {
            throw new IllegalStateException("Expected a cache hit");
        }
        return lookup.value();
    }

    @Benchmark
    public LocalResponseCache.LoadToken cacheAllocationMissToken() {
        CacheKeyContract.OpaqueKey key = nextKey();
        LocalResponseCache.Lookup lookup = missCache.lookup(key);
        if (lookup.hit()) {
            throw new IllegalStateException("Expected a cache miss");
        }
        missCache.finish(lookup.loadToken());
        return lookup.loadToken();
    }

    @Benchmark
    public Object cacheAllocationLoaderPublication() {
        CacheKeyContract.OpaqueKey key = nextKey();
        LocalResponseCache.Lookup lookup = loaderCache.lookup(key);
        if (lookup.hit()) {
            throw new IllegalStateException("Expected a cache miss before publication");
        }
        loaderCache.publish(lookup.loadToken(), VALUE);
        loaderCache.finish(lookup.loadToken());
        return VALUE;
    }

    @Benchmark
    public Object cacheAllocationCoalescedWaiter() {
        CacheKeyContract.OpaqueKey key = nextKey();
        Sinks.One<Object> load = Sinks.one();
        Mono<Object> call = cached(waiterManager, singleFlightSelection, key, load::asMono);
        CompletableFuture<Object> leader = call.toFuture();
        CompletableFuture<Object> waiter = call.toFuture();
        load.tryEmitValue(VALUE).orThrow();
        Object value = waiter.join();
        if (!VALUE.equals(leader.join())) {
            throw new IllegalStateException("The single-flight leader returned an unexpected value");
        }
        return value;
    }

    @Benchmark
    public long cacheAllocationSizeEviction() {
        CacheKeyContract.OpaqueKey key = nextKey();
        LocalResponseCache.Lookup lookup = evictionCache.lookup(key);
        if (lookup.hit()) {
            throw new IllegalStateException("Expected a cache miss before size eviction");
        }
        evictionCache.publish(lookup.loadToken(), VALUE);
        evictionCache.finish(lookup.loadToken());
        return evictionCache.evictionCount();
    }

    @Benchmark
    public Object cacheAllocationRefresh() {
        int index = Math.floorMod((int) sequence.getAndIncrement(), REFRESH_KEY_COUNT);
        cacheTicker.addAndGet(Duration.ofMillis(2).toNanos());
        return cached(refreshManager, refreshSelection, keys[index], () -> Mono.just(VALUE)).block();
    }

    @Benchmark
    public String cacheLoopbackStarterMiss() {
        long before = loopbackDispatches.get();
        String id = "miss-" + sequence.getAndIncrement();
        String value = loopbackClient.miss(id).block();
        requireDispatchDelta(before, 1, "cache miss");
        return requireValue(id, value);
    }

    @Benchmark
    public String cacheLoopbackStarterHit() {
        long before = loopbackDispatches.get();
        String value = loopbackClient.hit("hit").block();
        requireDispatchDelta(before, 0, "cache hit");
        return requireValue("hit", value);
    }

    @Benchmark
    public List<String> cacheLoopbackStarterCoalescedMiss() {
        long before = loopbackDispatches.get();
        String id = "coalesced-" + sequence.getAndIncrement();
        List<String> values = Mono.zip(loopbackClient.coalesced(id), loopbackClient.coalesced(id))
                .map(tuple -> List.of(tuple.getT1(), tuple.getT2()))
                .block();
        requireDispatchDelta(before, 1, "coalesced miss");
        if (values == null || !values.equals(List.of(id, id))) {
            throw new IllegalStateException("The coalesced cache call returned " + values);
        }
        return values;
    }

    @Benchmark
    public String cacheLoopbackStarterRefreshOnAccess() {
        long before = loopbackDispatches.get();
        loopbackTicker.addAndGet(Duration.ofMillis(2).toNanos());
        String value = loopbackClient.refresh("refresh").block();
        awaitRefreshCompletion();
        requireDispatchDelta(before, 1, "refresh on access");
        return requireValue("refresh", value);
    }

    private void setupKeyConstruction() throws NoSuchMethodException {
        Method method = KeyContractClient.class.getMethod(
                "keyed", String.class, String.class, String.class);
        MethodMetadataCache metadataCache = new MethodMetadataCache();
        keyPlan = RequestPlan.from(metadataCache.get(method), KeyContractClient.class);
        keyArgumentResolver = new RequestArgumentResolver();
        keyPolicy = new ReactiveHttpClientProperties.CachePolicyConfig();
        keyPolicy.setTtlMs(TTL_MS);
        keyPolicy.setMaximumSize(MAXIMUM_SIZE);
        keyPolicy.setVaryByHeaders(List.of("X-Locale"));
        keyArguments = new Object[]{"42", "summary", "en-US"};
    }

    private void setupLoopbackClient() throws Exception {
        loopbackServer = HttpServer.create()
                .host("127.0.0.1")
                .port(0)
                .handle((request, response) -> {
                    loopbackDispatches.incrementAndGet();
                    String path = request.path();
                    String value = path.substring(path.lastIndexOf('/') + 1);
                    Mono<String> body = path.startsWith("/cache/coalesced/")
                            ? Mono.delay(Duration.ofMillis(2)).map(ignored -> value)
                            : Mono.just(value);
                    return response.header("Content-Type", MediaType.TEXT_PLAIN_VALUE)
                            .sendString(body)
                            .then();
                })
                .bindNow();
        loopbackConnectionProvider = ConnectionProvider.create("v27-cache-benchmark");
        WebClient webClient = WebClient.builder()
                .baseUrl("http://127.0.0.1:" + loopbackServer.port())
                .clientConnector(new ReactorClientHttpConnector(
                        HttpClient.create(loopbackConnectionProvider)))
                .filter(ReactiveClientInvocationHandler.finalRequestObservationFilter())
                .build();

        ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();
        config.getCache().getPolicies().put("basic", policy(TTL_MS, MAXIMUM_SIZE, false, null, null));
        config.getCache().getPolicies().put("single", policy(TTL_MS, MAXIMUM_SIZE, true, null, null));
        config.getCache().getPolicies().put("refresh", policy(TTL_MS, MAXIMUM_SIZE, false, 1L, 1_000L));

        applicationContext = new GenericApplicationContext();
        applicationContext.refresh();
        loopbackCacheManager = LocalResponseCacheManager.testing(
                loopbackTicker::get, refreshScheduler);
        MethodMetadataCache metadataCache = new MethodMetadataCache();
        ReactiveClientInvocationHandler handler = new ReactiveClientInvocationHandler(
                webClient,
                metadataCache,
                new RequestArgumentResolver(),
                new DefaultErrorDecoder(),
                config,
                "benchmark-cache-client",
                CacheBenchmarkClient.class,
                applicationContext,
                new NoopResilienceOperatorApplier(),
                null,
                new ReactiveHttpClientProperties.ObservabilityConfig(),
                loopbackCacheManager);
        loopbackClient = (CacheBenchmarkClient) Proxy.newProxyInstance(
                CacheBenchmarkClient.class.getClassLoader(),
                new Class<?>[]{CacheBenchmarkClient.class},
                handler);
        requireValue("hit", loopbackClient.hit("hit").block());
        requireValue("refresh", loopbackClient.refresh("refresh").block());
        inFlightRefreshesField = LocalResponseCacheManager.class.getDeclaredField("inFlightRefreshes");
        inFlightRefreshesField.setAccessible(true);
    }

    private void awaitRefreshCompletion() {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (System.nanoTime() < deadline) {
            try {
                Map<?, ?> refreshes = (Map<?, ?>) inFlightRefreshesField.get(loopbackCacheManager);
                synchronized (refreshes) {
                    if (refreshes.isEmpty()) {
                        return;
                    }
                }
            } catch (IllegalAccessException error) {
                throw new IllegalStateException("Unable to inspect benchmark refresh completion", error);
            }
            Thread.onSpinWait();
        }
        throw new IllegalStateException("Timed out waiting for the benchmark refresh to complete");
    }

    private void requireDispatchDelta(long before, long expected, String scenario) {
        long actual = loopbackDispatches.get() - before;
        if (actual != expected) {
            throw new IllegalStateException(scenario + " dispatched " + actual
                    + " requests instead of " + expected);
        }
    }

    private static String requireValue(String expected, String actual) {
        if (!expected.equals(actual)) {
            throw new IllegalStateException("Expected " + expected + " but received " + actual);
        }
        return actual;
    }

    private CacheKeyContract.OpaqueKey nextKey() {
        return keys[Math.floorMod((int) sequence.getAndIncrement(), keys.length)];
    }

    private CaffeineLocalResponseCache cache(long maximumSize) {
        return new CaffeineLocalResponseCache(
                TTL_MS, maximumSize, cacheTicker::get, (cache, key, reason) -> { });
    }

    private static void publish(
            CaffeineLocalResponseCache cache, CacheKeyContract.OpaqueKey key, Object value) {
        LocalResponseCache.Lookup lookup = cache.lookup(key);
        cache.publish(lookup.loadToken(), value);
        cache.finish(lookup.loadToken());
    }

    @SuppressWarnings("unchecked")
    private static <T> Mono<T> cached(
            LocalResponseCacheManager manager,
            EffectiveCachePolicy.Selection selection,
            CacheKeyContract.OpaqueKey key,
            java.util.function.Supplier<Mono<T>> loader) {
        return (Mono<T>) manager.getOrLoad(selection, key, loader::get);
    }

    private static CacheKeyContract.OpaqueKey opaqueKey(String value) {
        return CacheKeyContract.OpaqueKey.from(value.getBytes(StandardCharsets.UTF_8));
    }

    private static EffectiveCachePolicy.Selection selection(
            String name,
            long ttlMs,
            long maximumSize,
            boolean singleFlight,
            Long refreshAfterMs,
            Long refreshTimeoutMs) {
        return new EffectiveCachePolicy.Selection(
                true,
                EffectiveCachePolicy.Source.CLIENT,
                name,
                policy(ttlMs, maximumSize, singleFlight, refreshAfterMs, refreshTimeoutMs));
    }

    private static ReactiveHttpClientProperties.CachePolicyConfig policy(
            long ttlMs,
            long maximumSize,
            boolean singleFlight,
            Long refreshAfterMs,
            Long refreshTimeoutMs) {
        ReactiveHttpClientProperties.CachePolicyConfig policy =
                new ReactiveHttpClientProperties.CachePolicyConfig();
        policy.setTtlMs(ttlMs);
        policy.setMaximumSize(maximumSize);
        policy.setSingleFlight(singleFlight);
        policy.setSharedResponse(true);
        policy.setRefreshAfterMs(refreshAfterMs);
        policy.setRefreshTimeoutMs(refreshTimeoutMs);
        return policy;
    }

    interface KeyContractClient {
        @GET("/cache/{id}")
        Mono<String> keyed(
                @PathVar("id") String id,
                @QueryParam("projection") String projection,
                @HeaderParam("X-Locale") String locale);
    }

    interface CacheBenchmarkClient {
        @GET("/cache/miss/{id}")
        @CacheResponse("basic")
        Mono<String> miss(@PathVar("id") String id);

        @GET("/cache/hit/{id}")
        @CacheResponse("basic")
        Mono<String> hit(@PathVar("id") String id);

        @GET("/cache/coalesced/{id}")
        @CacheResponse("single")
        Mono<String> coalesced(@PathVar("id") String id);

        @GET("/cache/refresh/{id}")
        @CacheResponse("refresh")
        Mono<String> refresh(@PathVar("id") String id);
    }
}
