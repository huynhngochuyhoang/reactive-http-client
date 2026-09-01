package io.github.huynhngochuyhoang.httpstarter.core;

import com.github.benmanes.caffeine.cache.Cache;
import io.github.huynhngochuyhoang.httpstarter.annotation.GET;
import io.github.huynhngochuyhoang.httpstarter.annotation.ReactiveHttpClient;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientCacheOutcome;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import io.prometheus.metrics.model.registry.PrometheusRegistry;
import org.junit.jupiter.api.Test;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class LocalResponseCacheObservabilityTest {

    private static final RequestArgumentResolver.ResolvedArgs RESOLVED =
            new RequestArgumentResolver.ResolvedArgs(Map.of(), Map.of(), Map.of(), null);

    @Test
    void prometheusMetersUseBoundedTagsAndExposeDeterministicZeroSeries() {
        PrometheusMeterRegistry registry = new PrometheusMeterRegistry(
                PrometheusConfig.DEFAULT, new PrometheusRegistry(), io.micrometer.core.instrument.Clock.SYSTEM);
        LocalResponseCacheMetrics metrics = LocalResponseCacheMetrics.enabled(registry, "catalog-client");
        SizedCache cache = new SizedCache();

        metrics.registerApi("catalog.get");
        metrics.registerCache("catalog", 10, cache);

        String zeroScrape = registry.scrape();
        assertThat(zeroScrape)
                .contains("reactive_http_client_cache_lookups_total{api_name=\"catalog.get\",client_name=\"catalog-client\",result=\"hit\"} 0.0")
                .contains("reactive_http_client_cache_callers_total")
                .contains("reactive_http_client_cache_loads_total")
                .contains("reactive_http_client_cache_refreshes_total")
                .contains("reactive_http_client_cache_evictions_total")
                .contains("reactive_http_client_cache_entries")
                .contains("reactive_http_client_cache_maximum_entries")
                .doesNotContain("cause=\"weight\"");

        SizedCache weightedCache = new SizedCache(100L);
        weightedCache.retainedBytes.set(40L);
        metrics.registerCache("weighted", 10, weightedCache);
        assertThat(registry.get(LocalResponseCacheMetrics.PREFIX + ".evictions")
                .tags("client.name", "catalog-client", "cache.policy", "weighted", "cause", "weight")
                .counter().count()).isZero();
        assertThat(registry.get(LocalResponseCacheMetrics.PREFIX + ".retained.decoded.response.bytes")
                .tags("client.name", "catalog-client", "cache.policy", "weighted")
                .gauge())
                .satisfies(gauge -> {
                    assertThat(gauge.value()).isEqualTo(40.0);
                    assertThat(gauge.getId().getDescription())
                            .isEqualTo("Current decoded response representation bytes retained by this policy cache");
                });
        assertThat(registry.get(LocalResponseCacheMetrics.PREFIX + ".maximum.decoded.response.bytes")
                .tags("client.name", "catalog-client", "cache.policy", "weighted")
                .gauge())
                .satisfies(gauge -> {
                    assertThat(gauge.value()).isEqualTo(100.0);
                    assertThat(gauge.getId().getDescription())
                            .isEqualTo("Configured maximum decoded response representation bytes for this policy cache");
                });
        for (LocalResponseCacheMetrics.AdmissionOutcome outcome
                : LocalResponseCacheMetrics.AdmissionOutcome.values()) {
            assertThat(registry.get(LocalResponseCacheMetrics.PREFIX + ".admissions")
                    .tags("client.name", "catalog-client", "cache.policy", "weighted",
                            "outcome", outcome.tagValue())
                    .counter().count()).isZero();
        }

        metrics.lookup("catalog.get", "hit");
        metrics.lookup("catalog.get", "miss");
        metrics.coalesced("catalog.get");
        metrics.stale("catalog.get");
        metrics.caller("catalog.get", HttpClientCacheOutcome.FRESH_HIT);
        metrics.load("catalog.get", LocalResponseCacheMetrics.WorkOutcome.SUCCESS,
                Duration.ofMillis(10).toNanos());
        metrics.refresh("catalog.get", LocalResponseCacheMetrics.WorkOutcome.FAILURE,
                Duration.ofMillis(20).toNanos());
        metrics.refresh("catalog.get", LocalResponseCacheMetrics.WorkOutcome.CANCELLATION, 0);
        metrics.eviction("catalog", LocalResponseCache.RemovalReason.TTL);
        metrics.eviction("catalog", LocalResponseCache.RemovalReason.SIZE);
        cache.size.set(7);

        double hits = registry.get(LocalResponseCacheMetrics.PREFIX + ".lookups")
                .tags("client.name", "catalog-client", "api.name", "catalog.get", "result", "hit")
                .counter().count();
        double misses = registry.get(LocalResponseCacheMetrics.PREFIX + ".lookups")
                .tags("client.name", "catalog-client", "api.name", "catalog.get", "result", "miss")
                .counter().count();
        assertThat(hits / (hits + misses)).isEqualTo(0.5);
        assertThat(registry.get(LocalResponseCacheMetrics.PREFIX + ".coalesced")
                .tags("client.name", "catalog-client", "api.name", "catalog.get")
                .counter().count() / misses).isEqualTo(1.0);
        assertThat(registry.get(LocalResponseCacheMetrics.PREFIX + ".callers")
                .tags("client.name", "catalog-client", "api.name", "catalog.get", "outcome", "FRESH_HIT")
                .counter().count()).isEqualTo(1.0);
        assertThat(registry.get(LocalResponseCacheMetrics.PREFIX + ".refreshes")
                .tags("client.name", "catalog-client", "api.name", "catalog.get", "outcome", "failure")
                .counter().count()).isEqualTo(1.0);
        assertThat(registry.get(LocalResponseCacheMetrics.PREFIX + ".load.duration")
                .tags("client.name", "catalog-client", "api.name", "catalog.get", "outcome", "success")
                .timer().totalTime(TimeUnit.SECONDS)).isEqualTo(0.01);
        assertThat(registry.get(LocalResponseCacheMetrics.PREFIX + ".refresh.duration")
                .tags("client.name", "catalog-client", "api.name", "catalog.get", "outcome", "failure")
                .timer().totalTime(TimeUnit.SECONDS)).isEqualTo(0.02);
        assertThat(registry.get(LocalResponseCacheMetrics.PREFIX + ".entries")
                .tags("client.name", "catalog-client", "cache.policy", "catalog")
                .gauge().value()).isEqualTo(7.0);
        assertThat(registry.get(LocalResponseCacheMetrics.PREFIX + ".maximum.entries")
                .tags("client.name", "catalog-client", "cache.policy", "catalog")
                .gauge().value()).isEqualTo(10.0);

        assertThat(registry.scrape())
                .doesNotContain("cache-key")
                .doesNotContain("tenant-value")
                .doesNotContain("Authorization");
    }

    @Test
    void weightedManagerRecordsBoundedAdmissionOutcomesAndCurrentRepresentationBytes() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        LocalResponseCacheMetrics metrics = LocalResponseCacheMetrics.enabled(registry, "catalog-client");
        metrics.registerApi("catalog.get");
        LocalResponseCacheManager manager = LocalResponseCacheManager.testing(
                System::nanoTime, Schedulers.parallel(), metrics, "catalog-client");
        EffectiveCachePolicy.Selection selection = selection("weighted", false, null);
        selection.policy().setMaximumTotalDecodedResponseBytes(5L);

        assertThat(load(manager, selection, key("admitted"), "catalog.get", state(), state(),
                Mono.just("secret-value"), measuredResponse(5, 4))).isEqualTo("secret-value");
        assertThat(load(manager, selection, key("unknown"), "catalog.get", state(), state(),
                Mono.just("unknown-secret"), LocalResponseCacheManager.ResponseMetadata.successWithoutHeaders()))
                .isEqualTo("unknown-secret");
        assertThat(load(manager, selection, key("over"), "catalog.get", state(), state(),
                Mono.just("over-secret"), measuredResponse(5, 6)))
                .isEqualTo("over-secret");

        assertThat(registry.get(LocalResponseCacheMetrics.PREFIX + ".admissions")
                .tags("client.name", "catalog-client", "cache.policy", "weighted",
                        "outcome", "admitted")
                .counter().count()).isEqualTo(1.0);
        assertThat(registry.get(LocalResponseCacheMetrics.PREFIX + ".admissions")
                .tags("client.name", "catalog-client", "cache.policy", "weighted",
                        "outcome", "bypassed_unknown_size")
                .counter().count()).isEqualTo(1.0);
        assertThat(registry.get(LocalResponseCacheMetrics.PREFIX + ".admissions")
                .tags("client.name", "catalog-client", "cache.policy", "weighted",
                        "outcome", "bypassed_over_budget")
                .counter().count()).isEqualTo(1.0);
        assertThat(registry.get(LocalResponseCacheMetrics.PREFIX + ".retained.decoded.response.bytes")
                .tags("client.name", "catalog-client", "cache.policy", "weighted")
                .gauge().value()).isEqualTo(4.0);
        assertThat(manager.snapshot().retainedDecodedResponseBytes()).isEqualTo(4L);
        assertThat(registry.getMeters())
                .extracting(meter -> meter.getId().getTags().toString())
                .allMatch(tags -> !tags.contains("secret"));

        manager.close();
        assertThat(registry.getMeters()).noneMatch(LocalResponseCacheObservabilityTest::isCacheMeter);
    }

    @Test
    void closeRemovesEveryOwnedMeterAndSameTagsObserveOnlyReplacementCache() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        LocalResponseCacheMetrics first = LocalResponseCacheMetrics.enabled(registry, "catalog-client");
        SizedCache oldCache = new SizedCache(100L);
        oldCache.size.set(3);
        oldCache.retainedBytes.set(30L);
        first.registerApi("catalog.get");
        first.registerCache("catalog", 10, oldCache);

        first.close();
        first.close();
        first.lookup("catalog.get", "hit");
        first.load("catalog.get", LocalResponseCacheMetrics.WorkOutcome.SUCCESS, 1);
        assertThat(registry.getMeters()).noneMatch(LocalResponseCacheObservabilityTest::isCacheMeter);

        LocalResponseCacheMetrics replacement = LocalResponseCacheMetrics.enabled(registry, "catalog-client");
        SizedCache newCache = new SizedCache(200L);
        newCache.size.set(8);
        newCache.retainedBytes.set(80L);
        replacement.registerApi("catalog.get");
        replacement.registerCache("catalog", 10, newCache);

        assertThat(registry.get(LocalResponseCacheMetrics.PREFIX + ".entries")
                .tags("client.name", "catalog-client", "cache.policy", "catalog")
                .gauge().value()).isEqualTo(8.0);
        assertThat(registry.get(LocalResponseCacheMetrics.PREFIX + ".retained.decoded.response.bytes")
                .tags("client.name", "catalog-client", "cache.policy", "catalog")
                .gauge().value()).isEqualTo(80.0);
        assertThat(registry.get(LocalResponseCacheMetrics.PREFIX + ".maximum.decoded.response.bytes")
                .tags("client.name", "catalog-client", "cache.policy", "catalog")
                .gauge().value()).isEqualTo(200.0);
        oldCache.size.set(9);
        oldCache.retainedBytes.set(90L);
        assertThat(registry.get(LocalResponseCacheMetrics.PREFIX + ".entries")
                .tags("client.name", "catalog-client", "cache.policy", "catalog")
                .gauge().value()).isEqualTo(8.0);
        assertThat(registry.get(LocalResponseCacheMetrics.PREFIX + ".retained.decoded.response.bytes")
                .tags("client.name", "catalog-client", "cache.policy", "catalog")
                .gauge().value()).isEqualTo(80.0);
        replacement.close();
        assertThat(registry.getMeters()).noneMatch(LocalResponseCacheObservabilityTest::isCacheMeter);
    }

    @Test
    void disabledRecorderCreatesNoMetersAndCannotActivateStorage() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        LocalResponseCacheMetrics disabled = LocalResponseCacheMetrics.disabled();
        disabled.registerApi("catalog.get");
        disabled.registerCache("catalog", 10, new SizedCache());
        disabled.lookup("catalog.get", "hit");
        disabled.refresh("catalog.get", LocalResponseCacheMetrics.WorkOutcome.SUCCESS, 1);

        assertThat(registry.getMeters()).isEmpty();
    }

    @Test
    void selectedCacheWithoutCacheObservabilityRecordsNoLibraryStatsOrCacheOutcome() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ReactiveHttpClientProperties.ClientConfig config = clientCacheConfig();
        ReactiveHttpClientProperties.ObservabilityConfig observability =
                new ReactiveHttpClientProperties.ObservabilityConfig();
        LocalResponseCacheManager manager = LocalResponseCacheManager.createForClient(
                CacheObservedClient.class,
                "catalog-client",
                new MethodMetadataCache(),
                config,
                getClass().getClassLoader(),
                observability,
                registry);
        EffectiveCachePolicy.Selection selection = selection("catalog", false, null);
        SubscriptionReportingState miss = state();
        SubscriptionReportingState hit = state();

        assertThat(load(manager, selection, key("selected"), "catalog.get", miss, state(), Mono.just("value")))
                .isEqualTo("value");
        assertThat(load(manager, selection, key("selected"), "catalog.get", hit, state(), Mono.just("unexpected")))
                .isEqualTo("value");

        assertThat(miss.cacheOutcome()).isNull();
        assertThat(hit.cacheOutcome()).isNull();
        assertThat(registry.getMeters()).noneMatch(LocalResponseCacheObservabilityTest::isCacheMeter);
        assertThat(nativeCache(manager).stats().requestCount()).isZero();
        manager.close();
    }

    @Test
    void cacheObservabilityDoesNotCreateMetersWhenNoPolicyIsSelected() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ReactiveHttpClientProperties.ObservabilityConfig observability =
                new ReactiveHttpClientProperties.ObservabilityConfig();
        observability.getCache().setEnabled(true);

        LocalResponseCacheManager manager = LocalResponseCacheManager.createForClient(
                CacheObservedClient.class,
                "catalog-client",
                new MethodMetadataCache(),
                new ReactiveHttpClientProperties.ClientConfig(),
                getClass().getClassLoader(),
                observability,
                registry);

        assertThat(manager.snapshot().policyCount()).isZero();
        assertThat(registry.getMeters()).noneMatch(LocalResponseCacheObservabilityTest::isCacheMeter);
        manager.close();
    }

    @Test
    void weightedCacheWithCacheMetricsDisabledCreatesNoMetersOrMeterSuppliers() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ReactiveHttpClientProperties.ClientConfig config = clientCacheConfig();
        config.getCache().getPolicies().get("catalog")
                .setMaximumTotalDecodedResponseBytes(1_024L);
        ReactiveHttpClientProperties.ObservabilityConfig observability =
                new ReactiveHttpClientProperties.ObservabilityConfig();
        observability.setEnabled(true);

        LocalResponseCacheManager manager = LocalResponseCacheManager.createForClient(
                CacheObservedClient.class,
                "catalog-client",
                new MethodMetadataCache(),
                config,
                getClass().getClassLoader(),
                observability,
                registry);

        assertThat(manager.snapshot().retainedDecodedResponseBytes()).isZero();
        assertThat(registry.getMeters()).noneMatch(LocalResponseCacheObservabilityTest::isCacheMeter);
        manager.close();
    }

    @Test
    void managerMetersClassifyRealLoadAndRefreshFailuresAndCancellations() {
        AtomicLong ticker = new AtomicLong();
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        LocalResponseCacheMetrics metrics = LocalResponseCacheMetrics.enabled(registry, "catalog-client");
        metrics.registerApi("catalog.get");
        LocalResponseCacheManager manager = LocalResponseCacheManager.testing(
                ticker::get, Schedulers.parallel(), metrics, "catalog-client");
        EffectiveCachePolicy.Selection ordinary = selection("ordinary", false, null);

        assertThat(call(manager, ordinary, key("failed"), "catalog.get", state(), state(),
                Mono.error(new IllegalStateException("load failed"))).onErrorComplete().block())
                .isNull();
        Disposable cancelled = call(manager, ordinary, key("cancelled"), "catalog.get", state(), state(),
                Mono.never()).subscribe();
        cancelled.dispose();

        EffectiveCachePolicy.Selection refresh = selection("refresh", false, 50L);
        assertThat(load(manager, refresh, key("refresh"), "catalog.get", state(), state(), Mono.just("initial")))
                .isEqualTo("initial");
        ticker.addAndGet(Duration.ofMillis(50).toNanos());
        assertThat(load(manager, refresh, key("refresh"), "catalog.get", state(), state(),
                Mono.error(new IllegalStateException("refresh failed"))))
                .isEqualTo("initial");

        assertThat(registry.get(LocalResponseCacheMetrics.PREFIX + ".loads")
                .tags("client.name", "catalog-client", "api.name", "catalog.get", "outcome", "failure")
                .counter().count()).isEqualTo(1.0);
        assertThat(registry.get(LocalResponseCacheMetrics.PREFIX + ".loads")
                .tags("client.name", "catalog-client", "api.name", "catalog.get", "outcome", "cancellation")
                .counter().count()).isEqualTo(1.0);
        assertThat(registry.get(LocalResponseCacheMetrics.PREFIX + ".refreshes")
                .tags("client.name", "catalog-client", "api.name", "catalog.get", "outcome", "failure")
                .counter().count()).isEqualTo(1.0);
        manager.close();
    }

    @Test
    void sizeEvictionCancelsRefreshAndRecordsBothBoundedFacts() {
        AtomicLong ticker = new AtomicLong();
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        LocalResponseCacheMetrics metrics = LocalResponseCacheMetrics.enabled(registry, "catalog-client");
        metrics.registerApi("catalog.get");
        LocalResponseCacheManager manager = LocalResponseCacheManager.testing(
                ticker::get, Schedulers.parallel(), metrics, "catalog-client");
        EffectiveCachePolicy.Selection refresh = selection("refresh", false, 50L);
        refresh.policy().setMaximumSize(1L);

        assertThat(load(manager, refresh, key("first"), "catalog.get", state(), state(), Mono.just("first")))
                .isEqualTo("first");
        ticker.addAndGet(Duration.ofMillis(50).toNanos());
        assertThat(load(manager, refresh, key("first"), "catalog.get", state(), state(), Mono.never()))
                .isEqualTo("first");
        assertThat(load(manager, refresh, key("second"), "catalog.get", state(), state(), Mono.just("second")))
                .isEqualTo("second");

        assertThat(registry.get(LocalResponseCacheMetrics.PREFIX + ".refreshes")
                .tags("client.name", "catalog-client", "api.name", "catalog.get", "outcome", "cancellation")
                .counter().count()).isEqualTo(1.0);
        assertThat(registry.get(LocalResponseCacheMetrics.PREFIX + ".evictions")
                .tags("client.name", "catalog-client", "cache.policy", "refresh", "cause", "size")
                .counter().count()).isEqualTo(1.0);
        manager.close();
    }

    @Test
    void evictionDuringRefreshAssemblyRecordsCancellationExactlyOnce() throws Exception {
        AtomicLong ticker = new AtomicLong();
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        LocalResponseCacheMetrics metrics = LocalResponseCacheMetrics.enabled(registry, "catalog-client");
        metrics.registerApi("catalog.get");
        LocalResponseCacheManager manager = LocalResponseCacheManager.testing(
                ticker::get, Schedulers.parallel(), metrics, "catalog-client");
        EffectiveCachePolicy.Selection refresh = selection("refresh", false, 50L);
        refresh.policy().setMaximumSize(1L);
        CacheKeyContract.OpaqueKey firstKey = key("assembly-first");
        CountDownLatch assembling = new CountDownLatch(1);
        CountDownLatch continueAssembly = new CountDownLatch(1);

        assertThat(load(manager, refresh, firstKey, "catalog.get", state(), state(), Mono.just("first")))
                .isEqualTo("first");
        ticker.addAndGet(Duration.ofMillis(50).toNanos());
        CompletableFuture<?> staleCaller = CompletableFuture.supplyAsync(() -> manager.getOrLoad(
                        refresh,
                        firstKey,
                        "catalog.get",
                        ignored -> {
                            assembling.countDown();
                            try {
                                if (!continueAssembly.await(1, TimeUnit.SECONDS)) {
                                    throw new IllegalStateException("refresh assembly gate timed out");
                                }
                            }
                            catch (InterruptedException error) {
                                Thread.currentThread().interrupt();
                                throw new IllegalStateException("refresh assembly interrupted", error);
                            }
                            return Mono.never();
                        },
                        LocalResponseCacheManager.ResponseMetadata::successWithoutHeaders,
                        state(),
                        state())
                .block());

        assertThat(assembling.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(load(manager, refresh, key("assembly-second"), "catalog.get",
                state(), state(), Mono.just("second"))).isEqualTo("second");
        continueAssembly.countDown();

        assertThat(staleCaller.get(1, TimeUnit.SECONDS)).isEqualTo("first");
        assertThat(registry.get(LocalResponseCacheMetrics.PREFIX + ".refreshes")
                .tags("client.name", "catalog-client", "api.name", "catalog.get", "outcome", "cancellation")
                .counter().count()).isEqualTo(1.0);
        assertThat(registry.get(LocalResponseCacheMetrics.PREFIX + ".refresh.duration")
                .tags("client.name", "catalog-client", "api.name", "catalog.get", "outcome", "cancellation")
                .timer().count()).isEqualTo(1L);
        manager.close();
    }

    @Test
    void factoryCloseCancelsActiveLoadAndLateSignalsCannotRestoreMeters() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        LocalResponseCacheMetrics metrics = LocalResponseCacheMetrics.enabled(registry, "catalog-client");
        metrics.registerApi("catalog.get");
        LocalResponseCacheManager manager = LocalResponseCacheManager.testing(
                System::nanoTime, Schedulers.parallel(), metrics, "catalog-client");
        Sinks.One<String> load = Sinks.one();
        CompletableFuture<?> result = call(manager, selection("single", true, null), key("shared"),
                "catalog.get", state(), state(), load.asMono()).toFuture();

        assertThat(registry.getMeters()).anyMatch(LocalResponseCacheObservabilityTest::isCacheMeter);
        manager.close();
        load.tryEmitValue("late");

        assertThat(result).isDone();
        assertThat(registry.getMeters()).noneMatch(LocalResponseCacheObservabilityTest::isCacheMeter);
    }

    @Test
    void factoryDestroyCancelsLoadsAndRefreshesClearsEntriesAndAllowsSameTagRecreation() throws Exception {
        AtomicLong ticker = new AtomicLong();
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        LocalResponseCacheMetrics metrics = LocalResponseCacheMetrics.enabled(registry, "catalog-client");
        metrics.registerApi("catalog.get");
        LocalResponseCacheManager manager = LocalResponseCacheManager.testing(
                ticker::get, Schedulers.parallel(), metrics, "catalog-client");
        EffectiveCachePolicy.Selection refresh = selection("refresh", false, 50L);
        AtomicLong refreshCancellations = new AtomicLong();
        assertThat(load(manager, refresh, key("refresh"), "catalog.get",
                state(), state(), Mono.just("initial"))).isEqualTo("initial");
        ticker.addAndGet(Duration.ofMillis(50).toNanos());
        assertThat(load(manager, refresh, key("refresh"), "catalog.get", state(), state(),
                Mono.<String>never().doOnCancel(refreshCancellations::incrementAndGet)))
                .isEqualTo("initial");

        AtomicLong loadCancellations = new AtomicLong();
        CompletableFuture<?> load = call(manager, selection("single", true, null), key("load"),
                "catalog.get", state(), state(),
                Mono.<String>never().doOnCancel(loadCancellations::incrementAndGet)).toFuture();
        assertThat(manager.snapshot().currentSize()).isEqualTo(1);
        assertThat(registry.getMeters()).anyMatch(LocalResponseCacheObservabilityTest::isCacheMeter);

        ReactiveHttpClientFactoryBean<CacheObservedClient> factory = new ReactiveHttpClientFactoryBean<>();
        Field cacheManager = ReactiveHttpClientFactoryBean.class.getDeclaredField("responseCacheManager");
        cacheManager.setAccessible(true);
        cacheManager.set(factory, manager);
        long startedAtNanos = System.nanoTime();

        factory.destroy();

        assertThat(System.nanoTime() - startedAtNanos).isLessThan(Duration.ofSeconds(1).toNanos());
        assertThat(load).isDone();
        assertThat(loadCancellations).hasValue(1);
        assertThat(refreshCancellations).hasValue(1);
        assertThat(manager.snapshot()).isEqualTo(
                new LocalResponseCacheManager.Snapshot(0, 0, 0, 0, true));
        assertThat(registry.getMeters()).noneMatch(LocalResponseCacheObservabilityTest::isCacheMeter);

        LocalResponseCacheMetrics replacementMetrics =
                LocalResponseCacheMetrics.enabled(registry, "catalog-client");
        replacementMetrics.registerApi("catalog.get");
        LocalResponseCacheManager replacement = LocalResponseCacheManager.testing(
                ticker::get, Schedulers.parallel(), replacementMetrics, "catalog-client");
        assertThat(load(replacement, selection("refresh", false, 50L), key("replacement"),
                "catalog.get", state(), state(), Mono.just("replacement"))).isEqualTo("replacement");
        assertThat(registry.get(LocalResponseCacheMetrics.PREFIX + ".entries")
                .tags("client.name", "catalog-client", "cache.policy", "refresh")
                .gauge().value()).isEqualTo(1.0);
        replacement.close();
        assertThat(registry.getMeters()).noneMatch(LocalResponseCacheObservabilityTest::isCacheMeter);
    }

    @Test
    void callerOutcomesDistinguishMissHitWaiterAndStaleWithoutKeyMaterial() throws Exception {
        AtomicLong ticker = new AtomicLong();
        LocalResponseCacheMetrics metrics = LocalResponseCacheMetrics.enabled(
                new SimpleMeterRegistry(), "catalog-client");
        LocalResponseCacheManager manager = LocalResponseCacheManager.testing(
                ticker::get, Schedulers.parallel(), metrics, "catalog-client");
        EffectiveCachePolicy.Selection ordinary = selection("ordinary", false, null);
        CacheKeyContract.OpaqueKey ordinaryKey = key("opaque-secret");
        SubscriptionReportingState miss = state();

        assertThat(load(manager, ordinary, ordinaryKey, "catalog.get", miss, state(), Mono.just("value")))
                .isEqualTo("value");
        assertThat(miss.cacheOutcome()).isEqualTo(HttpClientCacheOutcome.MISS_LOADER);

        SubscriptionReportingState hit = state();
        assertThat(load(manager, ordinary, ordinaryKey, "catalog.get", hit, state(), Mono.just("unexpected")))
                .isEqualTo("value");
        assertThat(hit.cacheOutcome()).isEqualTo(HttpClientCacheOutcome.FRESH_HIT);

        EffectiveCachePolicy.Selection singleFlight = selection("single", true, null);
        Sinks.One<String> shared = Sinks.one();
        SubscriptionReportingState leader = state();
        SubscriptionReportingState waiter = state();
        CompletableFuture<?> leaderResult = call(
                manager, singleFlight, key("shared"), "catalog.single", leader, state(), shared.asMono()).toFuture();
        CompletableFuture<?> waiterResult = call(
                manager, singleFlight, key("shared"), "catalog.single", waiter, state(), Mono.just("duplicate"))
                .toFuture();
        assertThat(leader.cacheOutcome()).isEqualTo(HttpClientCacheOutcome.MISS_LOADER);
        assertThat(waiter.cacheOutcome()).isEqualTo(HttpClientCacheOutcome.COALESCED_WAITER);
        shared.tryEmitValue("shared-value").orThrow();
        assertThat(leaderResult.get(1, TimeUnit.SECONDS)).isEqualTo("shared-value");
        assertThat(waiterResult.get(1, TimeUnit.SECONDS)).isEqualTo("shared-value");

        EffectiveCachePolicy.Selection refresh = selection("refresh", false, 50L);
        CacheKeyContract.OpaqueKey refreshKey = key("refresh");
        assertThat(load(manager, refresh, refreshKey, "catalog.refresh", state(), state(), Mono.just("stale")))
                .isEqualTo("stale");
        ticker.addAndGet(Duration.ofMillis(50).toNanos());
        SubscriptionReportingState stale = state();
        assertThat(load(manager, refresh, refreshKey, "catalog.refresh", stale, state(), Mono.never()))
                .isEqualTo("stale");
        assertThat(stale.cacheOutcome()).isEqualTo(HttpClientCacheOutcome.STALE_HIT);

        manager.close();
    }

    private static boolean isCacheMeter(Meter meter) {
        return meter.getId().getName().startsWith(LocalResponseCacheMetrics.PREFIX);
    }

    private static SubscriptionReportingState state() {
        return new SubscriptionReportingState(RESOLVED);
    }

    private static Object load(LocalResponseCacheManager manager,
                               EffectiveCachePolicy.Selection selection,
                               CacheKeyContract.OpaqueKey key,
                               String apiName,
                               SubscriptionReportingState caller,
                               SubscriptionReportingState load,
                               Mono<?> source) {
        return call(manager, selection, key, apiName, caller, load, source).block();
    }

    private static Object load(LocalResponseCacheManager manager,
                               EffectiveCachePolicy.Selection selection,
                               CacheKeyContract.OpaqueKey key,
                               String apiName,
                               SubscriptionReportingState caller,
                               SubscriptionReportingState load,
                               Mono<?> source,
                               LocalResponseCacheManager.ResponseMetadata responseMetadata) {
        return manager.getOrLoad(selection, key, apiName, ignored -> source,
                () -> responseMetadata, caller, load).block();
    }

    private static LocalResponseCacheManager.ResponseMetadata measuredResponse(
            long maximumBytes, long decodedBytes) {
        LocalResponseCacheManager.DecodedResponseBytes measurement =
                new LocalResponseCacheManager.DecodedResponseBytes(maximumBytes);
        measurement.add(decodedBytes);
        measurement.complete();
        return new LocalResponseCacheManager.ResponseMetadata(200, Map.of(), true, measurement);
    }

    private static Mono<?> call(LocalResponseCacheManager manager,
                                EffectiveCachePolicy.Selection selection,
                                CacheKeyContract.OpaqueKey key,
                                String apiName,
                                SubscriptionReportingState caller,
                                SubscriptionReportingState load,
                                Mono<?> source) {
        return manager.getOrLoad(selection, key, apiName, ignored -> source,
                LocalResponseCacheManager.ResponseMetadata::successWithoutHeaders, caller, load);
    }

    private static EffectiveCachePolicy.Selection selection(
            String name, boolean singleFlight, Long refreshAfterMs) {
        ReactiveHttpClientProperties.CachePolicyConfig policy =
                new ReactiveHttpClientProperties.CachePolicyConfig();
        policy.setTtlMs(1_000L);
        policy.setMaximumSize(10L);
        policy.setSingleFlight(singleFlight);
        policy.setRefreshAfterMs(refreshAfterMs);
        if (refreshAfterMs != null) {
            policy.setRefreshTimeoutMs(500L);
        }
        return new EffectiveCachePolicy.Selection(true, EffectiveCachePolicy.Source.CLIENT, name, policy);
    }

    private static CacheKeyContract.OpaqueKey key(String value) {
        return CacheKeyContract.OpaqueKey.from(value.getBytes(StandardCharsets.UTF_8));
    }

    @SuppressWarnings("unchecked")
    private static Cache<?, ?> nativeCache(LocalResponseCacheManager manager) throws Exception {
        Field cachesField = LocalResponseCacheManager.class.getDeclaredField("caches");
        cachesField.setAccessible(true);
        Object storage = ((Map<?, ?>) cachesField.get(manager)).values().iterator().next();
        Field cacheField = CaffeineLocalResponseCache.class.getDeclaredField("cache");
        cacheField.setAccessible(true);
        return (Cache<?, ?>) cacheField.get(storage);
    }

    private static ReactiveHttpClientProperties.ClientConfig clientCacheConfig() {
        ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();
        ReactiveHttpClientProperties.CachePolicyConfig policy =
                new ReactiveHttpClientProperties.CachePolicyConfig();
        policy.setTtlMs(1_000L);
        policy.setMaximumSize(10L);
        config.getCache().setPolicy("catalog");
        config.getCache().getPolicies().put("catalog", policy);
        return config;
    }

    @ReactiveHttpClient(name = "catalog-client")
    interface CacheObservedClient {
        @GET("/catalog")
        Mono<String> get();
    }

    private static final class SizedCache implements LocalResponseCache {
        private final AtomicLong size = new AtomicLong();
        private final AtomicLong retainedBytes = new AtomicLong();
        private final Long maximumDecodedResponseBytes;

        private SizedCache() {
            this(null);
        }

        private SizedCache(Long maximumDecodedResponseBytes) {
            this.maximumDecodedResponseBytes = maximumDecodedResponseBytes;
        }

        @Override public Lookup lookup(CacheKeyContract.OpaqueKey key) { throw new UnsupportedOperationException(); }
        @Override public RefreshToken beginRefresh(EntryToken entryToken) { throw new UnsupportedOperationException(); }
        @Override public boolean isRefreshCurrent(RefreshToken refreshToken) { return false; }
        @Override public long hardExpiryRemainingNanos(RefreshToken refreshToken) { return 0; }
        @Override public void publishRefresh(RefreshToken refreshToken, Object value) { }
        @Override public void publishRefresh(RefreshToken refreshToken, Object value, long bytes) { }
        @Override public void finishRefresh(RefreshToken refreshToken) { }
        @Override public void publish(LoadToken token, Object value) { }
        @Override public void publish(LoadToken token, Object value, long bytes) { }
        @Override public void finish(LoadToken token) { }
        @Override public long estimatedSize() { return size.get(); }
        @Override public long evictionCount() { return 0; }
        @Override public Long maximumDecodedResponseBytes() { return maximumDecodedResponseBytes; }
        @Override public long retainedDecodedResponseBytes() { return retainedBytes.get(); }
        @Override public void invalidateAll() { size.set(0); }
        @Override public void close() { }
    }
}
