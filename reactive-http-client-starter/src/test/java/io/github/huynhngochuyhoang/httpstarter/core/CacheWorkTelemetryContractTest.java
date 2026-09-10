package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.annotation.GET;
import io.github.huynhngochuyhoang.httpstarter.annotation.LogHttpExchange;
import io.github.huynhngochuyhoang.httpstarter.annotation.PathVar;
import io.github.huynhngochuyhoang.httpstarter.annotation.ReactiveHttpClient;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import io.github.huynhngochuyhoang.httpstarter.exception.CacheWorkRejectedException;
import io.github.huynhngochuyhoang.httpstarter.exception.ErrorCategories;
import io.github.huynhngochuyhoang.httpstarter.exception.ErrorCategory;
import io.github.huynhngochuyhoang.httpstarter.observability.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;
import reactor.test.scheduler.VirtualTimeScheduler;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@org.junit.jupiter.api.Timeout(30)
class CacheWorkTelemetryContractTest {
    private static final String PREFIX = LocalResponseCacheMetrics.PREFIX;

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void failedCacheConstructionReleasesMetersWithoutAffectingLiveOwners(boolean liveOwner) {
        ClassLoader withoutCaffeine = new ClassLoader(getClass().getClassLoader()) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.startsWith("com.github.benmanes.caffeine")) { throw new ClassNotFoundException(name); }
                return super.loadClass(name, resolve);
            }
        };
        var config = CacheWorkPolicyEnforcementTest.config(true);
        var scheduler = VirtualTimeScheduler.create();
        try (var registry = new Registry();
             var existing = liveOwner ? new Fixture(registry, true, false, 3) : null) {
            var metersBefore = List.copyOf(registry.getMeters());
            for (int attempt = 0; attempt < 3; attempt++) {
                try (var metrics = LocalResponseCacheMetrics.enabled(registry, "work")) {
                    assertThatThrownBy(() -> LocalResponseCacheManager.createForClient(
                            Client.class, "work", new MethodMetadataCache(), config, withoutCaffeine,
                            () -> 0, scheduler, metrics, true))
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("com.github.ben-manes.caffeine:caffeine");
                    assertThat(registry.getMeters()).containsExactlyInAnyOrderElementsOf(metersBefore);
                    if (liveOwner) { assertGauge(registry, ".work.maximum.callers", 3); }
                    assertThat(metrics.enabled()).isFalse();
                }
            }
            if (existing != null) {
                assertThat(existing.client.get("one").block()).isEqualTo("value");
                existing.close();
                assertThat(registry.getMeters()).noneMatch(m -> m.getId().getName().startsWith(PREFIX));
            }
        } finally { scheduler.dispose(); }
    }

    enum PreStartInvalidation { AFTER_TOKEN, REPLACEMENT, DEADLINE, BEFORE_LOADER, EVICTION_CALLBACK }

    @ParameterizedTest
    @EnumSource(PreStartInvalidation.class)
    @SuppressWarnings("unchecked")
    void invalidationBeforeRefreshStartsIsOneSkipNotATerminal(PreStartInvalidation phase) throws Exception {
        var config = CacheWorkPolicyEnforcementTest.config(true);
        var clock = new AtomicLong();
        var scheduler = VirtualTimeScheduler.create();
        try (var registry = new Registry();
             var manager = LocalResponseCacheManager.createForClient(
                     Client.class, "work", new MethodMetadataCache(), config, getClass().getClassLoader(),
                     clock::get, scheduler, LocalResponseCacheMetrics.enabled(registry, "work"), true)) {
            var selection = new EffectiveCachePolicy.Selection(true, EffectiveCachePolicy.Source.CLIENT,
                    "work", config.getCache().getPolicies().get("work"));
            var key = CacheKeyContract.OpaqueKey.from(new byte[]{1});
            assertThat(manager.getOrLoad(selection, key, () -> Mono.just("old")).block()).isEqualTo("old");
            clock.set(Duration.ofSeconds(2).toNanos());
            var field = LocalResponseCacheManager.class.getDeclaredField("caches");
            field.setAccessible(true);
            var caches = (Map<Object, LocalResponseCache>) field.get(manager);
            var entry = caches.entrySet().iterator().next();
            var cache = entry.getValue();
            var checks = new AtomicInteger();
            var triggered = new AtomicInteger();
            // Intercept storage boundaries without adding production scheduling hooks.
            entry.setValue((LocalResponseCache) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[]{LocalResponseCache.class}, (proxy, method, args) -> {
                        if (method.getName().equals("beginRefresh")) {
                            Object token = method.invoke(cache, args);
                            assertThat(token).isNotNull();
                            if (phase == PreStartInvalidation.AFTER_TOKEN) {
                                triggered.incrementAndGet();
                                cache.invalidateAll();
                            } else if (phase == PreStartInvalidation.REPLACEMENT) {
                                triggered.incrementAndGet();
                                var replacement = cache.beginRefresh(cache.lookup(key).entryToken());
                                cache.publishRefresh(replacement, "replacement");
                                cache.finishRefresh(replacement);
                            }
                            return token;
                        }
                        if (method.getName().equals("hardExpiryRemainingNanos")
                                && phase == PreStartInvalidation.DEADLINE) {
                            triggered.incrementAndGet();
                            clock.set(Duration.ofMillis(selection.policy().getTtlMs()).toNanos());
                        }
                        if (method.getName().equals("isRefreshCurrent")) {
                            int check = checks.incrementAndGet();
                            if (phase == PreStartInvalidation.BEFORE_LOADER && check == 2) {
                                triggered.incrementAndGet();
                                cache.invalidateAll();
                            } else if (phase == PreStartInvalidation.EVICTION_CALLBACK && check == 1) {
                                triggered.incrementAndGet();
                                manager.evictAllForTesting();
                            }
                        }
                        return method.invoke(cache, args);
                    }));
            var assemblies = new AtomicInteger();
            var subscriptions = new AtomicInteger();
            assertThat(manager.getOrLoad(selection, key, () -> {
                assemblies.incrementAndGet();
                return Mono.defer(() -> { subscriptions.incrementAndGet(); return Mono.just("unexpected"); });
            }).block()).isEqualTo("old");
            assertThat(triggered).hasValue(1);
            assertThat(assemblies).hasValue(0);
            assertThat(subscriptions).hasValue(0);
            assertCounter(registry, ".refresh.skips", "reason", "entry_unavailable", 1);
            assertThat(registry.find(PREFIX + ".refreshes").counters()).allSatisfy(c -> assertThat(c.count()).isZero());
            assertThat(registry.find(PREFIX + ".refresh.duration").timers()).allSatisfy(t -> assertThat(t.count()).isZero());
            assertGauge(registry, ".work.active.refreshes", 0);
            assertThat(manager.workloadSnapshotForTesting().inFlightRefreshes()).isZero();
            var generations = CaffeineLocalResponseCache.class.getDeclaredField("generations");
            generations.setAccessible(true);
            assertThat(((Map<?, ?>) generations.get(cache)).values()).allSatisfy(state ->
                    assertThat(state).extracting("activeLoads", "activeRefreshes").containsExactly(0, 0));
            cache.invalidateAll();
            assertThat((Map<?, ?>) generations.get(cache)).isEmpty();
        } finally { scheduler.dispose(); }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void limitedOwnersAggregateGaugesAndHistoryUntilLastClose(boolean weighted) {
        try (var registry = new Registry(); var first = new Fixture(registry, true, weighted, 2);
             var second = new Fixture(registry, true, weighted, 3)) {
            assertGauge(registry, ".work.active.callers", 0);
            assertGauge(registry, ".work.maximum.callers", 5);
            assertGauge(registry, ".work.maximum.loads", 2);
            assertGauge(registry, ".work.maximum.refreshes", 2);
            assertGauge(registry, ".maximum.entries", 200);
            var a = first.client.get("busy").toFuture();
            var b = second.client.get("busy").toFuture();
            assertGauge(registry, ".work.active.callers", 2);
            assertGauge(registry, ".work.active.loads", 2);
            var waiter = first.client.get("busy").toFuture();
            reject(first.client.get("warm"), CacheWorkRejectedException.Reason.CALLER_CAPACITY);
            reject(second.client.get("other"), CacheWorkRejectedException.Reason.LOAD_CAPACITY);
            assertGauge(registry, ".work.active.callers", 3);
            assertCounter(registry, ".work.rejections", "reason", "caller_capacity", 1);
            assertCounter(registry, ".work.rejections", "reason", "load_capacity", 1);
            System.gc();
            assertGauge(registry, ".work.maximum.callers", 5);
            assertGauge(registry, ".work.active.loads", 2);
            first.close();
            assertThat(a).isCompletedWithValue(null);
            assertThat(waiter).isCompletedWithValue(null);
            assertGauge(registry, ".work.maximum.callers", 3);
            assertGauge(registry, ".work.active.callers", 1);
            assertGauge(registry, ".work.active.loads", 1);
            assertGauge(registry, ".maximum.entries", 100);
            assertCounter(registry, ".work.rejections", "reason", "caller_capacity", 1);
            b.cancel(true);
            assertGauge(registry, ".work.active.callers", 0);
            assertGauge(registry, ".work.active.loads", 0);
            second.close();
            assertThat(registry.getMeters()).noneMatch(m -> m.getId().getName().startsWith(PREFIX));
            try (var replacement = new Fixture(registry, true, weighted, 4)) {
                first.metrics.registerApi("late");
                first.metrics.lookup("late", "hit");
                first.metrics.rejection("work", CacheWorkRejectedException.Reason.LOAD_CAPACITY);
                assertGauge(registry, ".work.maximum.callers", 4);
                assertCounter(registry, ".work.rejections", "reason", "load_capacity", 0);
                assertThat(registry.getMeters()).noneMatch(m -> "late".equals(m.getId().getTag("api.name")));
            }
            assertThat(registry.getMeters()).noneMatch(m -> m.getId().getName().startsWith(PREFIX));
        }
    }

    @Test
    void refreshSkipsLeaveForegroundAndTerminalRefreshCountsIndependent() {
        try (var registry = new Registry(); var f = new Fixture(registry, true, false, 3)) {
            assertThat(f.client.get("one").block()).isEqualTo("value");
            assertThat(f.client.get("two").block()).isEqualTo("value");
            f.clock.set(Duration.ofSeconds(2).toNanos());
            assertThat(f.client.get("one").block()).isEqualTo("value");
            assertGauge(registry, ".work.active.refreshes", 1);
            assertGauge(registry, ".work.active.callers", 0);
            assertGauge(registry, ".work.active.loads", 0);
            assertThat(f.client.get("two").block()).isEqualTo("value");
            assertCounter(registry, ".refresh.skips", "reason", "capacity", 1);
            assertThat(f.client.get("one").block()).isEqualTo("value");
            assertCounter(registry, ".refresh.skips", "reason", "already_refreshing", 1);
            assertThat(registry.find(PREFIX + ".refreshes").counters()).allSatisfy(c -> assertThat(c.count()).isZero());
            assertThat(registry.find(PREFIX + ".refresh.duration").timers()).allSatisfy(t -> assertThat(t.count()).isZero());
            int callers = f.events.size();
            f.pending.tryEmitValue(response("new")).orThrow();
            assertGauge(registry, ".work.active.refreshes", 0);
            assertThat(f.events).hasSize(callers);
            assertThat(registry.find(PREFIX + ".refreshes").counters().stream().mapToDouble(c -> c.count()).sum())
                    .isEqualTo(1);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void rejectionHasOneSanitizedTerminalWithAndWithoutRegistry(boolean meterAvailable) {
        try (var registry = new Registry();
             var f = new Fixture(meterAvailable ? registry : null, true, false, 2)) {
            var leader = f.client.get("busy").toFuture();
            reject(f.client.get("load-rejected-secret").contextWrite(context ->
                    context.put("inboundHeaders", Map.of("Authorization", List.of("secret")))),
                    CacheWorkRejectedException.Reason.LOAD_CAPACITY);
            assertTerminal(f, 0, HttpClientCacheOutcome.LOAD_REJECTED);
            var waiter = f.client.get("busy").toFuture();
            reject(f.client.get("caller-rejected-secret"), CacheWorkRejectedException.Reason.CALLER_CAPACITY);
            assertTerminal(f, 1, HttpClientCacheOutcome.CALLER_REJECTED);
            assertThat(f.dispatches).hasValue(1);
            assertThat(f.events).hasSize(2);
            assertThat(f.logs.records).hasSize(2);
            assertThat(f.lifecycle).hasSize(2);
            assertThat(registry.find("reactive.http.client.requests").timers()).isEmpty();
            leader.cancel(true);
            assertThat(f.events).hasSize(3);
            f.pending.tryEmitValue(response("loaded")).orThrow();
            assertThat(waiter.join()).isEqualTo("loaded");
            var waiterEvent = f.events.get(3);
            assertThat(waiterEvent.getCacheOutcome()).isEqualTo(HttpClientCacheOutcome.COALESCED_WAITER);
            assertThat(waiterEvent.getAttemptCount()).isZero();
            assertThat(waiterEvent.getRequestUrl()).isNull();
            assertThat(waiterEvent.getStatusCode()).isNull();
        }
    }

    enum Disabled { CACHE, LIMITS, CACHE_METRICS, MASTER, NO_REGISTRY }

    @Test
    void mixedWeightedAndUnweightedOwnersRetainOnlyApplicableMeters() {
        try (var registry = new Registry(); var weighted = new Fixture(registry, true, true, 2);
             var unweighted = new Fixture(registry, true, false, 3)) {
            assertGauge(registry, ".maximum.decoded.response.bytes", 1000);
            assertGauge(registry, ".maximum.entries", 200);
            weighted.close();
            assertThat(registry.find(PREFIX + ".maximum.decoded.response.bytes").gauge()).isNull();
            assertThat(registry.find(PREFIX + ".admissions").counters()).isEmpty();
            assertGauge(registry, ".maximum.entries", 100);
            assertGauge(registry, ".work.maximum.callers", 3);
            assertThat(unweighted.client.get("one").block()).isEqualTo("value");
            assertGauge(registry, ".entries", 1);
        }
    }

    @Test
    void closingRacesCannotRegisterOrIncrementForADepartedOwner() throws Exception {
        try (var threads = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            for (int iteration = 0; iteration < 30; iteration++) {
                try (var registry = new Registry();
                     var first = LocalResponseCacheMetrics.enabled(registry, "work");
                     var second = LocalResponseCacheMetrics.enabled(registry, "work")) {
                    first.registerApi("get");
                    second.registerApi("get");
                    var start = new java.util.concurrent.CountDownLatch(1);
                    var update = threads.submit(() -> {
                        await(start);
                        first.registerApi("late");
                        first.lookup("get", "hit");
                    });
                    var close = threads.submit(() -> { await(start); first.close(); });
                    start.countDown();
                    update.get(5, java.util.concurrent.TimeUnit.SECONDS);
                    close.get(5, java.util.concurrent.TimeUnit.SECONDS);
                    var counter = registry.get(PREFIX + ".lookups")
                            .tags("client.name", "work", "api.name", "get", "result", "hit").counter();
                    double before = counter.count();
                    first.lookup("get", "hit");
                    first.registerApi("late");
                    second.lookup("get", "hit");
                    assertThat(counter.count()).isEqualTo(before + 1);
                    assertThat(registry.getMeters()).noneMatch(m -> "late".equals(m.getId().getTag("api.name")));
                    second.close();
                    assertThat(registry.getMeters()).isEmpty();
                }
            }
        }
    }

    private static void await(java.util.concurrent.CountDownLatch latch) {
        try { assertThat(latch.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue(); }
        catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new AssertionError(error); }
    }

    @Test
    void prometheusWorkMetersKeepFixedTagSetsAndZeroSeries() {
        var registry = new io.micrometer.prometheusmetrics.PrometheusMeterRegistry(
                io.micrometer.prometheusmetrics.PrometheusConfig.DEFAULT,
                new io.prometheus.metrics.model.registry.PrometheusRegistry(),
                io.micrometer.core.instrument.Clock.SYSTEM);
        try (var metrics = LocalResponseCacheMetrics.enabled(registry, "work")) {
            var admission = new CacheWorkAdmission(Map.of("work", 2), IllegalStateException::new);
            metrics.registerWork("work", new CacheWorkPolicy.Limits(2, 2, 2), admission, admission, admission);
            assertThat(registry.scrape())
                    .contains("reactive_http_client_cache_work_active_callers{cache_policy=\"work\",client_name=\"work\"} 0.0")
                    .contains("reason=\"caller_capacity\"} 0.0")
                    .contains("reason=\"load_capacity\"} 0.0")
                    .contains("reason=\"capacity\"} 0.0")
                    .contains("reason=\"already_refreshing\"} 0.0")
                    .contains("reason=\"entry_unavailable\"} 0.0");
            assertThat(registry.getMeters()).allSatisfy(m -> {
                assertThat(m.getId().getTags()).extracting(io.micrometer.core.instrument.Tag::getKey)
                        .containsOnly(m instanceof io.micrometer.core.instrument.Gauge
                                ? new String[]{"client.name", "cache.policy"}
                                : new String[]{"client.name", "cache.policy", "reason"});
            });
        } finally { registry.close(); }
    }

    @ParameterizedTest
    @EnumSource(Disabled.class)
    void disabledDimensionsNeverCreateWorkMeters(Disabled mode) {
        var config = CacheWorkPolicyEnforcementTest.config(true);
        var observation = new ReactiveHttpClientProperties.ObservabilityConfig();
        observation.getCache().setEnabled(mode != Disabled.CACHE_METRICS);
        observation.setEnabled(mode != Disabled.MASTER);
        if (mode == Disabled.CACHE) { config.getCache().setPolicy(null); }
        if (mode == Disabled.LIMITS) { config.getCache().getPolicies().get("work").setWork(null); }
        try (var registry = new Registry();
             var manager = LocalResponseCacheManager.createForClient(
                     Client.class, "work", new MethodMetadataCache(), config, getClass().getClassLoader(),
                     observation, mode == Disabled.NO_REGISTRY ? null : registry)) {
            assertThat(registry.getMeters()).noneMatch(m -> m.getId().getName().startsWith(PREFIX + ".work.")
                    || m.getId().getName().equals(PREFIX + ".refresh.skips"));
            if (mode == Disabled.CACHE) { assertThat(manager).isNull(); }
        }
    }

    @Test
    void compatibilityObserverCallsCannotDiluteDownstreamHealth() {
        try (var registry = new Registry()) {
            var config = new ReactiveHttpClientProperties.ObservabilityConfig();
            config.getHealth().setMinSamples(1);
            var observer = new MicrometerHttpClientObserver(registry, config);
            observer.record(new HttpClientObserverEvent("work", "get", "GET", "/{id}", 503, 2,
                    new IllegalStateException("downstream"), ErrorCategory.SERVER_ERROR, null, null));
            for (var reason : CacheWorkRejectedException.Reason.values()) {
                var error = new CacheWorkRejectedException(reason);
                assertThat(ErrorCategories.from(new RuntimeException(error))).isEqualTo(ErrorCategory.CACHE_ADMISSION_ERROR);
                var event = new HttpClientObserverEvent("work", "get", "GET", "/{id}", null, 1, error,
                        ErrorCategories.from(error), null, null, 0, -1, -1, null, null);
                assertThat(event.getFailureStage()).isNull();
                observer.record(event);
            }
            for (var outcome : HttpClientCacheOutcome.values()) {
                if (outcome == HttpClientCacheOutcome.MISS_LOADER) { continue; }
                observer.record(new HttpClientObserverEvent("work", "get", "GET", "/{id}", null, 1,
                        null, null, null, null, 0, -1, -1, null, null, null, Map.of(), outcome));
            }
            var health = new Boot4HttpClientHealthIndicator(registry, config).health();
            assertThat(health.getStatus().getCode()).isEqualTo("DOWN");
            var details = (Map<?, ?>) health.getDetails().get("work");
            assertThat(details.get("samples")).isEqualTo(1L);
            assertThat(details.get("errors")).isEqualTo(1L);
            assertThatThrownBy(() -> new CacheWorkRejectedException(null)).isInstanceOf(NullPointerException.class);
        }
    }

    private static void assertTerminal(Fixture f, int index, HttpClientCacheOutcome outcome) {
        var event = f.events.get(index);
        assertThat(event.getCacheOutcome()).isEqualTo(outcome);
        assertThat(event.getErrorCategory()).isEqualTo(ErrorCategory.CACHE_ADMISSION_ERROR);
        assertThat(event.getAttemptCount()).isZero();
        assertThat(event.getFailureStage()).isNull();
        assertThat(event.getRequestUrl()).isNull();
        assertThat(event.getStatusCode()).isNull();
        assertThat(event.getRequestHeaders()).isEmpty();
        assertThat(event.getRequestBody()).isNull();
        assertThat(event.getResponseBody()).isNull();
        var hook = f.lifecycle.get(index);
        assertThat(hook.error()).isSameAs(event.getError());
        assertThat(hook.cacheOutcome()).isEqualTo(outcome);
        assertThat(hook.attemptNumber()).isZero();
        assertThat(hook.headers()).isEmpty();
        assertThat(hook.pathVars()).isEmpty();
        assertThat(hook.queryParams()).isEmpty();
        assertThat(hook.requestBody()).isNull();
        assertThat(hook.requestUrl()).isNull();
        assertThat(hook.statusCode()).isNull();
        var log = f.logs.records.get(index);
        assertThat(log.error()).isSameAs(event.getError());
        assertThat(log.cacheOutcome()).isEqualTo(outcome);
        assertThat(log.subscriptionAttemptCount()).isZero();
        assertThat(log.requestHeaders()).isEmpty();
        assertThat(log.responseHeaders()).isEmpty();
        assertThat(log.inboundHeaders()).isEmpty();
        assertThat(log.requestBody()).isNull();
        assertThat(log.requestUrl()).isNull();
        assertThat(log.responseStatus()).isNull();
    }

    private static void reject(Mono<?> call, CacheWorkRejectedException.Reason reason) {
        StepVerifier.create(call).expectErrorSatisfies(error ->
                assertThat(error).isInstanceOfSatisfying(CacheWorkRejectedException.class,
                        rejection -> assertThat(rejection.getReason()).isEqualTo(reason))).verify(Duration.ofSeconds(5));
    }

    private static void assertGauge(SimpleMeterRegistry registry, String suffix, double expected) {
        assertThat(registry.get(PREFIX + suffix).tags("client.name", "work", "cache.policy", "work")
                .gauge().value()).isEqualTo(expected);
    }

    private static void assertCounter(SimpleMeterRegistry registry, String suffix, String tag, String value, double expected) {
        assertThat(registry.get(PREFIX + suffix).tags("client.name", "work", "cache.policy", "work", tag, value)
                .counter().count()).isEqualTo(expected);
    }

    static ClientResponse response(String body) {
        return ClientResponse.create(HttpStatus.OK).header("Content-Type", "text/plain").body(body).build();
    }

    @ReactiveHttpClient(name = "work")
    @LogHttpExchange(logger = RecordingLogger.class)
    interface Client {
        @GET("/{id}") Mono<String> get(@PathVar("id") String id);
    }

    static final class RecordingLogger implements HttpExchangeLogger {
        final List<HttpExchangeLogContext> records = new CopyOnWriteArrayList<>();
        @Override public void log(HttpExchangeLogContext context) { records.add(context); }
    }

    static final class Registry extends SimpleMeterRegistry implements AutoCloseable { }

    static final class Fixture implements AutoCloseable {
        final GenericApplicationContext context = new GenericApplicationContext();
        final AtomicLong clock = new AtomicLong();
        final VirtualTimeScheduler scheduler = VirtualTimeScheduler.create();
        final Sinks.One<ClientResponse> pending = Sinks.one();
        final AtomicInteger dispatches = new AtomicInteger();
        final List<HttpClientObserverEvent> events = new CopyOnWriteArrayList<>();
        final List<ReactiveHttpClientLifecycleContext> lifecycle = new CopyOnWriteArrayList<>();
        final RecordingLogger logs = new RecordingLogger();
        final LocalResponseCacheMetrics metrics;
        final LocalResponseCacheManager manager;
        final Client client;

        Fixture(SimpleMeterRegistry registry, boolean enabled, boolean weighted, int callers) {
            var config = CacheWorkPolicyEnforcementTest.config(true);
            config.getCache().getPolicies().get("work").getWork().setMaximumConcurrentCallers((long) callers);
            if (weighted) { config.getCache().getPolicies().get("work").setMaximumTotalDecodedResponseBytes(1000L); }
            var observability = new ReactiveHttpClientProperties.ObservabilityConfig();
            observability.getCache().setEnabled(enabled);
            HttpClientObserver observer = registry == null ? events::add : new CompositeHttpClientObserver(List.of(
                    events::add, new MicrometerHttpClientObserver(registry, observability)));
            context.registerBean("observer", HttpClientObserver.class, () -> observer);
            context.registerBean("logger", RecordingLogger.class, () -> logs);
            context.registerBean("hook", ReactiveHttpClientLifecycleHook.class, () -> new ReactiveHttpClientLifecycleHook() {
                @Override public void onSuccess(ReactiveHttpClientLifecycleContext value) { lifecycle.add(value); }
                @Override public void onError(ReactiveHttpClientLifecycleContext value) { lifecycle.add(value); }
                @Override public void onCancel(ReactiveHttpClientLifecycleContext value) { lifecycle.add(value); }
            });
            context.refresh();
            var metadata = new MethodMetadataCache();
            metrics = enabled ? LocalResponseCacheMetrics.enabled(registry, "work") : LocalResponseCacheMetrics.disabled();
            manager = LocalResponseCacheManager.createForClient(Client.class, "work", metadata, config,
                    Client.class.getClassLoader(), clock::get, scheduler, metrics, enabled);
            var web = WebClient.builder().baseUrl("http://localhost")
                    .filter(ReactiveClientInvocationHandler.finalRequestObservationFilter())
                    .exchangeFunction(request -> {
                        dispatches.incrementAndGet();
                        return request.url().getPath().contains("busy") || clock.get() > 0
                                ? pending.asMono() : Mono.just(response("value"));
                    }).build();
            var handler = new ReactiveClientInvocationHandler(web, metadata, new RequestArgumentResolver(),
                    new DefaultErrorDecoder(), config, "work", Client.class, context,
                    new NoopResilienceOperatorApplier(), null, null, manager);
            client = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class[]{Client.class}, handler);
        }

        @Override public void close() { manager.close(); scheduler.dispose(); context.close(); }
    }
}
