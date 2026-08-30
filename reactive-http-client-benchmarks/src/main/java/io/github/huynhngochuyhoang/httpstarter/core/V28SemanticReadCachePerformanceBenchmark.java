package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.annotation.Body;
import io.github.huynhngochuyhoang.httpstarter.annotation.CacheKey;
import io.github.huynhngochuyhoang.httpstarter.annotation.CacheResponse;
import io.github.huynhngochuyhoang.httpstarter.annotation.POST;
import io.github.huynhngochuyhoang.httpstarter.auth.AuthContext;
import io.github.huynhngochuyhoang.httpstarter.auth.AuthProvider;
import io.github.huynhngochuyhoang.httpstarter.auth.OutboundAuthFilter;
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
import org.springframework.http.HttpHeaders;
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
import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@State(Scope.Benchmark)
@BenchmarkMode({Mode.Throughput, Mode.AverageTime})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
public class V28SemanticReadCachePerformanceBenchmark {

    private static final int REFRESH_KEY_COUNT = 64;
    private static final long TTL_MS = Duration.ofDays(1).toMillis();
    private static final long MAXIMUM_SIZE = 256;
    private static final String CLIENT_NAME = "benchmark-semantic-post-client";
    private static final SemanticQuery HIT_QUERY = new SemanticQuery("hit", 20);
    private static final SemanticQuery REFRESH_QUERY = new SemanticQuery("refresh", 20);

    private final AtomicLong sequence = new AtomicLong();
    private final AtomicLong noNetworkTicker = new AtomicLong();
    private final AtomicLong loopbackTicker = new AtomicLong();
    private final AtomicLong loopbackDispatches = new AtomicLong();
    private final AtomicLong authBodyChecks = new AtomicLong();

    private ReactiveHttpClientJsonCodec jsonCodec;
    private RequestPlan keyPlan;
    private RequestArgumentResolver argumentResolver;
    private ReactiveHttpClientProperties.CachePolicyConfig keyPolicy;
    private LocalResponseCacheManager noNetworkBasicManager;
    private LocalResponseCacheManager noNetworkSingleManager;
    private LocalResponseCacheManager noNetworkRefreshManager;
    private EffectiveCachePolicy.Selection basicSelection;
    private EffectiveCachePolicy.Selection singleSelection;
    private EffectiveCachePolicy.Selection refreshSelection;
    private Scheduler refreshScheduler;

    private DisposableServer loopbackServer;
    private ConnectionProvider connectionProvider;
    private GenericApplicationContext applicationContext;
    private LocalResponseCacheManager loopbackManager;
    private SemanticPostClient loopbackClient;
    private Field inFlightRefreshesField;

    @Setup(Level.Trial)
    public void setup() throws Exception {
        jsonCodec = new Jackson3ReactiveHttpClientJsonCodec(new ObjectMapper());
        argumentResolver = new RequestArgumentResolver();
        Method method = SemanticPostClient.class.getMethod("miss", SemanticQuery.class);
        keyPlan = RequestPlan.from(new MethodMetadataCache().get(method), SemanticPostClient.class);
        keyPolicy = policy(TTL_MS, MAXIMUM_SIZE, false, null, null);

        refreshScheduler = Schedulers.newSingle("v28-cache-benchmark-refresh");
        noNetworkBasicManager = LocalResponseCacheManager.testing(noNetworkTicker::get, refreshScheduler);
        noNetworkSingleManager = LocalResponseCacheManager.testing(noNetworkTicker::get, refreshScheduler);
        noNetworkRefreshManager = LocalResponseCacheManager.testing(noNetworkTicker::get, refreshScheduler);
        basicSelection = selection("semantic-basic", TTL_MS, MAXIMUM_SIZE, false, null, null);
        singleSelection = selection("semantic-single", TTL_MS, MAXIMUM_SIZE, true, null, null);
        refreshSelection = selection("semantic-refresh", TTL_MS, MAXIMUM_SIZE, false, 1L, 1_000L);

        load(noNetworkBasicManager, basicSelection, HIT_QUERY);
        for (int index = 0; index < REFRESH_KEY_COUNT; index++) {
            load(noNetworkRefreshManager, refreshSelection, refreshQuery(index));
        }

        inFlightRefreshesField = LocalResponseCacheManager.class.getDeclaredField("inFlightRefreshes");
        inFlightRefreshesField.setAccessible(true);
        setupLoopbackClient();
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        close(noNetworkBasicManager);
        close(noNetworkSingleManager);
        close(noNetworkRefreshManager);
        close(loopbackManager);
        if (applicationContext != null) {
            applicationContext.close();
        }
        if (connectionProvider != null) {
            connectionProvider.disposeLater().block(Duration.ofSeconds(5));
        }
        if (loopbackServer != null) {
            loopbackServer.disposeNow(Duration.ofSeconds(5));
        }
        if (refreshScheduler != null) {
            refreshScheduler.dispose();
        }
    }

    @Benchmark
    public CacheKeyContract.OpaqueKey cacheKeySemanticPostJsonBody() throws Exception {
        return preparedKey(new SemanticQuery("key-" + sequence.getAndIncrement(), 20));
    }

    @Benchmark
    public SemanticQuery cacheSemanticPostNoNetworkMiss() throws Exception {
        SemanticQuery query = new SemanticQuery("miss-" + sequence.getAndIncrement(), 20);
        return load(noNetworkBasicManager, basicSelection, query);
    }

    @Benchmark
    public SemanticQuery cacheSemanticPostNoNetworkHit() throws Exception {
        return load(noNetworkBasicManager, basicSelection, HIT_QUERY);
    }

    @Benchmark
    public List<SemanticQuery> cacheSemanticPostNoNetworkCoalescedWaiter() throws Exception {
        SemanticQuery query = new SemanticQuery("waiter-" + sequence.getAndIncrement(), 20);
        CacheKeyContract.OpaqueKey key = preparedKey(query);
        Sinks.One<SemanticQuery> source = Sinks.one();
        Mono<SemanticQuery> call = cached(noNetworkSingleManager, singleSelection, key, source::asMono);
        CompletableFuture<SemanticQuery> leader = call.toFuture();
        CompletableFuture<SemanticQuery> waiter = call.toFuture();
        source.tryEmitValue(query).orThrow();
        return List.of(requireQuery(query, leader.join()), requireQuery(query, waiter.join()));
    }

    @Benchmark
    public boolean cacheSemanticPostNoNetworkCancelledFlight() throws Exception {
        SemanticQuery query = new SemanticQuery("cancel-" + sequence.getAndIncrement(), 20);
        CacheKeyContract.OpaqueKey key = preparedKey(query);
        AtomicBoolean sourceCancelled = new AtomicBoolean();
        reactor.core.Disposable subscription = cached(
                noNetworkSingleManager,
                singleSelection,
                key,
                () -> Mono.<SemanticQuery>never().doOnCancel(() -> sourceCancelled.set(true)))
                .subscribe();
        subscription.dispose();
        if (!sourceCancelled.get()) {
            throw new IllegalStateException("Semantic POST cancelled flight retained its loader");
        }
        return true;
    }

    @Benchmark
    public SemanticQuery cacheSemanticPostNoNetworkRefreshCleanup() throws Exception {
        int index = Math.floorMod((int) sequence.getAndIncrement(), REFRESH_KEY_COUNT);
        SemanticQuery query = refreshQuery(index);
        noNetworkTicker.addAndGet(Duration.ofMillis(2).toNanos());
        SemanticQuery value = load(noNetworkRefreshManager, refreshSelection, query);
        awaitRefreshCompletion(noNetworkRefreshManager);
        return requireQuery(query, value);
    }

    @Benchmark
    public SemanticQuery cacheLoopbackStarterSemanticPostMiss() {
        long before = loopbackDispatches.get();
        SemanticQuery query = new SemanticQuery("wire-miss-" + sequence.getAndIncrement(), 20);
        SemanticQuery value = loopbackClient.miss(query).block();
        requireDispatchDelta(before, 1, "semantic POST cache miss");
        return requireQuery(query, value);
    }

    @Benchmark
    public SemanticQuery cacheLoopbackStarterSemanticPostHit() {
        long before = loopbackDispatches.get();
        SemanticQuery value = loopbackClient.hit(HIT_QUERY).block();
        requireDispatchDelta(before, 0, "semantic POST cache hit");
        return requireQuery(HIT_QUERY, value);
    }

    @Benchmark
    public List<SemanticQuery> cacheLoopbackStarterSemanticPostCoalescedWaiter() {
        long before = loopbackDispatches.get();
        SemanticQuery query = new SemanticQuery("wire-waiter-" + sequence.getAndIncrement(), 20);
        List<SemanticQuery> values = Mono.zip(loopbackClient.coalesced(query), loopbackClient.coalesced(query))
                .map(tuple -> List.of(tuple.getT1(), tuple.getT2()))
                .block();
        requireDispatchDelta(before, 1, "semantic POST coalesced miss");
        if (values == null || !values.equals(List.of(query, query))) {
            throw new IllegalStateException("The semantic POST waiter call returned " + values);
        }
        return values;
    }

    @Benchmark
    public SemanticQuery cacheLoopbackStarterSemanticPostRefreshOnAccess() {
        long before = loopbackDispatches.get();
        loopbackTicker.addAndGet(Duration.ofMillis(2).toNanos());
        SemanticQuery value = loopbackClient.refresh(REFRESH_QUERY).block();
        awaitRefreshCompletion(loopbackManager);
        requireDispatchDelta(before, 1, "semantic POST refresh");
        return requireQuery(REFRESH_QUERY, value);
    }

    private void setupLoopbackClient() {
        ObjectMapper serverMapper = new ObjectMapper();
        loopbackServer = HttpServer.create()
                .host("127.0.0.1")
                .port(0)
                .handle((request, response) -> {
                    loopbackDispatches.incrementAndGet();
                    boolean delayed = request.path().contains("/coalesced/");
                    return request.receive()
                            .aggregate()
                            .asString()
                            .flatMap(body -> {
                                try {
                                    serverMapper.readValue(body, SemanticQuery.class);
                                }
                                catch (Exception error) {
                                    return response.status(400)
                                            .sendString(Mono.just("invalid semantic query body"))
                                            .then();
                                }
                                Mono<String> responseBody = delayed
                                        ? Mono.delay(Duration.ofMillis(2)).map(ignored -> body)
                                        : Mono.just(body);
                                return response.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                                        .sendString(responseBody)
                                        .then();
                            });
                })
                .bindNow();

        String baseUrl = "http://127.0.0.1:" + loopbackServer.port();
        AuthProvider authProvider = request -> {
            if (!(request.requestBody() instanceof byte[] bytes) || bytes.length == 0) {
                return Mono.error(new IllegalStateException(
                        "Semantic POST benchmark auth did not receive serialized body bytes"));
            }
            authBodyChecks.incrementAndGet();
            return Mono.just(AuthContext.empty());
        };
        connectionProvider = ConnectionProvider.create("v28-semantic-post-cache-benchmark");
        WebClient webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(HttpClient.create(connectionProvider)))
                .filter(new OutboundAuthFilter(CLIENT_NAME, authProvider))
                .filter(ReactiveClientInvocationHandler.finalRequestObservationFilter())
                .build();

        ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();
        config.setAuthProvider("benchmark-auth");
        config.getDefaultHeaders().put(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        config.getCache().getPolicies().put(
                "semantic-basic", policy(TTL_MS, MAXIMUM_SIZE, false, null, null));
        config.getCache().getPolicies().put(
                "semantic-single", policy(TTL_MS, MAXIMUM_SIZE, true, null, null));
        config.getCache().getPolicies().put(
                "semantic-refresh", policy(TTL_MS, MAXIMUM_SIZE, false, 1L, 1_000L));

        applicationContext = new GenericApplicationContext();
        applicationContext.refresh();
        loopbackManager = LocalResponseCacheManager.testing(loopbackTicker::get, refreshScheduler);
        ReactiveClientInvocationHandler handler = new ReactiveClientInvocationHandler(
                webClient,
                new MethodMetadataCache(),
                argumentResolver,
                new DefaultErrorDecoder(),
                config,
                CLIENT_NAME,
                SemanticPostClient.class,
                applicationContext,
                new NoopResilienceOperatorApplier(),
                jsonCodec,
                new ReactiveHttpClientProperties.ObservabilityConfig(),
                loopbackManager,
                authProvider,
                baseUrl);
        loopbackClient = (SemanticPostClient) Proxy.newProxyInstance(
                SemanticPostClient.class.getClassLoader(),
                new Class<?>[]{SemanticPostClient.class},
                handler);
        requireQuery(HIT_QUERY, loopbackClient.hit(HIT_QUERY).block());
        requireQuery(REFRESH_QUERY, loopbackClient.refresh(REFRESH_QUERY).block());
        if (authBodyChecks.get() < 2) {
            throw new IllegalStateException("Semantic POST benchmark auth did not inspect priming bodies");
        }
    }

    private CacheKeyContract.OpaqueKey preparedKey(SemanticQuery query) throws Exception {
        Object[] frozen = CacheKeyContract.freezeArguments(keyPlan, new Object[]{query}, keyPolicy);
        RequestArgumentResolver.ResolvedArgs resolved = CacheKeyContract.snapshotRequestTarget(
                argumentResolver.resolve(keyPlan, frozen));
        byte[] wireBytes = jsonCodec.writeBounded(
                resolved.body(), CacheKeyContract.maximumSerializedBodyBytes());
        return CacheKeyContract.derive(
                SemanticPostClient.class,
                CLIENT_NAME,
                keyPlan,
                frozen,
                resolved,
                Context.empty(),
                keyPolicy,
                CacheKeyContract.serializedBodyKey(wireBytes, MediaType.APPLICATION_JSON_VALUE))
                .key();
    }

    private SemanticQuery load(LocalResponseCacheManager manager,
                               EffectiveCachePolicy.Selection selection,
                               SemanticQuery query) throws Exception {
        return requireQuery(query, cached(
                manager, selection, preparedKey(query), () -> Mono.just(query)).block());
    }

    private void awaitRefreshCompletion(LocalResponseCacheManager manager) {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (System.nanoTime() < deadline) {
            try {
                Map<?, ?> refreshes = (Map<?, ?>) inFlightRefreshesField.get(manager);
                synchronized (refreshes) {
                    if (refreshes.isEmpty()) {
                        return;
                    }
                }
            }
            catch (IllegalAccessException error) {
                throw new IllegalStateException("Unable to inspect benchmark refresh completion", error);
            }
            Thread.onSpinWait();
        }
        throw new IllegalStateException("Timed out waiting for semantic POST refresh completion");
    }

    private void requireDispatchDelta(long before, long expected, String scenario) {
        long actual = loopbackDispatches.get() - before;
        if (actual != expected) {
            throw new IllegalStateException(scenario + " dispatched " + actual
                    + " requests instead of " + expected);
        }
    }

    private static SemanticQuery requireQuery(SemanticQuery expected, SemanticQuery actual) {
        if (!expected.equals(actual)) {
            throw new IllegalStateException("Expected " + expected + " but received " + actual);
        }
        return actual;
    }

    private static SemanticQuery refreshQuery(int index) {
        return new SemanticQuery("refresh-" + index, 20);
    }

    private static void close(LocalResponseCacheManager manager) {
        if (manager != null) {
            manager.close();
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> Mono<T> cached(LocalResponseCacheManager manager,
                                      EffectiveCachePolicy.Selection selection,
                                      CacheKeyContract.OpaqueKey key,
                                      java.util.function.Supplier<Mono<T>> loader) {
        return (Mono<T>) manager.getOrLoad(selection, key, loader::get);
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
        policy.setVaryByParameters(List.of("criteria"));
        policy.setRefreshAfterMs(refreshAfterMs);
        policy.setRefreshTimeoutMs(refreshTimeoutMs);
        return policy;
    }

    public record SemanticQuery(String term, int limit) {
    }

    interface SemanticPostClient {
        @POST("/semantic/miss")
        @CacheResponse(value = "semantic-basic", semanticRead = true)
        Mono<SemanticQuery> miss(@Body @CacheKey("criteria") SemanticQuery query);

        @POST("/semantic/hit")
        @CacheResponse(value = "semantic-basic", semanticRead = true)
        Mono<SemanticQuery> hit(@Body @CacheKey("criteria") SemanticQuery query);

        @POST("/semantic/coalesced/query")
        @CacheResponse(value = "semantic-single", semanticRead = true)
        Mono<SemanticQuery> coalesced(@Body @CacheKey("criteria") SemanticQuery query);

        @POST("/semantic/refresh")
        @CacheResponse(value = "semantic-refresh", semanticRead = true)
        Mono<SemanticQuery> refresh(@Body @CacheKey("criteria") SemanticQuery query);
    }
}
