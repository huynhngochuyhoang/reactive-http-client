package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.annotation.*;
import io.github.huynhngochuyhoang.httpstarter.auth.AuthContext;
import io.github.huynhngochuyhoang.httpstarter.auth.InvalidatableAuthProvider;
import io.github.huynhngochuyhoang.httpstarter.auth.OutboundAuthFilter;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import io.github.huynhngochuyhoang.httpstarter.exception.LogicalCallTimeoutException;
import io.github.huynhngochuyhoang.httpstarter.filter.CorrelationIdWebFilter;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientCacheOutcome;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientFailureStage;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientObserverEvent;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.reactive.ClientHttpRequest;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;
import reactor.netty.DisposableServer;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.server.HttpServer;
import reactor.test.scheduler.VirtualTimeScheduler;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.*;

class BoundedLocalResponseCacheContractTest {

    @Test
    void lookupIsColdAndReturnsTheCachedValueByIdentity() {
        LocalResponseCacheManager manager = LocalResponseCacheManager.testing(System::nanoTime);
        EffectiveCachePolicy.Selection selection = selection("identity", 1_000, 10);
        CacheKeyContract.OpaqueKey key = key("identity");
        AtomicInteger loads = new AtomicInteger();
        List<String> value = new ArrayList<>(List.of("first"));
        Mono<List<String>> call = cached(manager, selection, key, () -> {
            loads.incrementAndGet();
            return Mono.just(value);
        });

        assertThat(loads).hasValue(0);
        List<String> first = call.block();
        List<String> second = call.block();

        assertThat(loads).hasValue(1);
        assertThat(first).isSameAs(value);
        assertThat(second).isSameAs(value);
        assertThat(manager.snapshot()).isEqualTo(
                new LocalResponseCacheManager.Snapshot(1, 10, 1, false));
    }

    @Test
    void hardExpiryUsesMonotonicTickerAndCapacityRemainsBounded() {
        AtomicLong ticker = new AtomicLong();
        LocalResponseCacheManager manager = LocalResponseCacheManager.testing(ticker::get);
        EffectiveCachePolicy.Selection selection = selection("bounded", 100, 1);
        AtomicInteger loads = new AtomicInteger();

        assertThat(load(manager, selection, key("one"), loads)).isEqualTo("value-1");
        ticker.addAndGet(Duration.ofMillis(99).toNanos());
        assertThat(load(manager, selection, key("one"), loads)).isEqualTo("value-1");
        ticker.addAndGet(Duration.ofMillis(1).toNanos());
        assertThat(load(manager, selection, key("one"), loads)).isEqualTo("value-2");
        assertThat(load(manager, selection, key("two"), loads)).isEqualTo("value-3");
        EffectiveCachePolicy.Selection secondPolicy = selection("second", 500, 3);
        assertThat(cached(manager, secondPolicy, key("second-policy"), () -> Mono.just("second")).block())
                .isEqualTo("second");

        assertThat(manager.snapshot()).isEqualTo(
                new LocalResponseCacheManager.Snapshot(2, 4, 2, 2, false));
    }

    @Test
    void concurrentMissesRemainIndependentAndFirstSuccessfulFillWins() throws Exception {
        LocalResponseCacheManager manager = LocalResponseCacheManager.testing(System::nanoTime);
        EffectiveCachePolicy.Selection selection = selection("duplicates", 1_000, 10);
        CacheKeyContract.OpaqueKey key = key("shared");
        Sinks.One<String> firstLoad = Sinks.one();
        Sinks.One<String> secondLoad = Sinks.one();
        AtomicInteger subscriptions = new AtomicInteger();
        Mono<String> call = cached(manager, selection, key, () ->
                subscriptions.getAndIncrement() == 0 ? firstLoad.asMono() : secondLoad.asMono());

        CompletableFuture<String> first = call.toFuture();
        CompletableFuture<String> second = call.toFuture();
        assertThat(subscriptions).hasValue(2);

        secondLoad.tryEmitValue("newer-completion").orThrow();
        assertThat(second.get(1, TimeUnit.SECONDS)).isEqualTo("newer-completion");
        firstLoad.tryEmitValue("older-completion").orThrow();
        assertThat(first.get(1, TimeUnit.SECONDS)).isEqualTo("older-completion");

        assertThat(call.block()).isEqualTo("newer-completion");
        assertThat(subscriptions).hasValue(2);
    }

    @Test
    void singleFlightSharesOneLoadAndKeepsKeysAndPoliciesIndependent() throws Exception {
        LocalResponseCacheManager manager = LocalResponseCacheManager.testing(System::nanoTime);
        EffectiveCachePolicy.Selection selected = selection("single", 1_000, 10, true);
        Sinks.One<String> sharedLoad = Sinks.one();
        AtomicInteger sharedSubscriptions = new AtomicInteger();
        Mono<String> call = cached(manager, selected, key("shared"), () -> Mono.defer(() -> {
            sharedSubscriptions.incrementAndGet();
            return sharedLoad.asMono();
        }));

        CompletableFuture<String> first = call.toFuture();
        CompletableFuture<String> waiter = call.toFuture();
        assertThat(sharedSubscriptions).hasValue(1);

        Sinks.One<String> otherKeyLoad = Sinks.one();
        AtomicInteger otherKeySubscriptions = new AtomicInteger();
        CompletableFuture<String> otherKey = cached(manager, selected, key("other"), () -> Mono.defer(() -> {
            otherKeySubscriptions.incrementAndGet();
            return otherKeyLoad.asMono();
        })).toFuture();
        EffectiveCachePolicy.Selection otherPolicy = selection("other-policy", 1_000, 10, true);
        AtomicInteger otherPolicySubscriptions = new AtomicInteger();
        CompletableFuture<String> otherPolicyResult = cached(
                manager, otherPolicy, key("shared"), () -> Mono.defer(() -> {
                    otherPolicySubscriptions.incrementAndGet();
                    return Mono.just("other-policy");
                })).toFuture();

        assertThat(otherKeySubscriptions).hasValue(1);
        assertThat(otherPolicySubscriptions).hasValue(1);
        assertThat(otherPolicyResult.get(1, TimeUnit.SECONDS)).isEqualTo("other-policy");
        otherKeyLoad.tryEmitValue("other-key").orThrow();
        assertThat(otherKey.get(1, TimeUnit.SECONDS)).isEqualTo("other-key");

        sharedLoad.tryEmitValue("shared-value").orThrow();
        assertThat(first.get(1, TimeUnit.SECONDS)).isEqualTo("shared-value");
        assertThat(waiter.get(1, TimeUnit.SECONDS)).isEqualTo("shared-value");
        assertThat(call.block()).isEqualTo("shared-value");
        assertThat(sharedSubscriptions).hasValue(1);
    }

    @Test
    void singleFlightRechecksTheCacheBeforeInstallingAFlightFromAStaleMiss() throws Exception {
        LocalResponseCacheManager manager = LocalResponseCacheManager.testing(System::nanoTime);
        EffectiveCachePolicy.Selection selection = selection("stale-miss", 1_000, 10, true);
        CacheKeyContract.OpaqueKey key = key("shared");
        AtomicInteger staleLoads = new AtomicInteger();
        AtomicInteger winningLoads = new AtomicInteger();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        AtomicReference<Thread> pausedThread = new AtomicReference<>();
        CountDownLatch started = new CountDownLatch(1);
        Field flightsField = LocalResponseCacheManager.class.getDeclaredField("inFlightLoads");
        flightsField.setAccessible(true);
        Object flightMonitor = flightsField.get(manager);

        try {
            CompletableFuture<String> paused;
            synchronized (flightMonitor) {
                paused = CompletableFuture.supplyAsync(() -> {
                    pausedThread.set(Thread.currentThread());
                    started.countDown();
                    return cached(manager, selection, key, () -> {
                        staleLoads.incrementAndGet();
                        return Mono.just("stale");
                    }).block();
                }, executor);
                assertThat(started.await(1, TimeUnit.SECONDS)).isTrue();
                awaitThreadState(pausedThread.get(), Thread.State.BLOCKED);

                assertThat(cached(manager, selection, key, () -> {
                    winningLoads.incrementAndGet();
                    return Mono.just("winner");
                }).block()).isEqualTo("winner");
            }

            assertThat(paused.get(1, TimeUnit.SECONDS)).isEqualTo("winner");
            assertThat(winningLoads).hasValue(1);
            assertThat(staleLoads).hasValue(0);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void singleFlightDetachesCallersAndCancelsOnlyAfterTheLastCallerLeaves() {
        LocalResponseCacheManager manager = LocalResponseCacheManager.testing(System::nanoTime);
        EffectiveCachePolicy.Selection selection = selection("cancellation", 1_000, 10, true);
        AtomicInteger subscriptions = new AtomicInteger();
        AtomicInteger cancellations = new AtomicInteger();
        Mono<String> call = cached(manager, selection, key("shared"), () -> Mono.defer(() -> {
            subscriptions.incrementAndGet();
            return Mono.<String>never().doOnCancel(cancellations::incrementAndGet);
        }));

        Disposable first = call.subscribe();
        Disposable waiter = call.subscribe();
        assertThat(subscriptions).hasValue(1);

        first.dispose();
        assertThat(cancellations).hasValue(0);
        waiter.dispose();
        assertThat(cancellations).hasValue(1);

        AtomicInteger replacementLoads = new AtomicInteger();
        assertThat(cached(manager, selection, key("shared"), () -> {
            replacementLoads.incrementAndGet();
            return Mono.just("replacement");
        }).block()).isEqualTo("replacement");
        assertThat(replacementLoads).hasValue(1);
    }

    @Test
    void reservedFlightMemberCannotReconnectAnUntrackedSource() throws Exception {
        LocalResponseCacheManager manager = LocalResponseCacheManager.testing(System::nanoTime);
        EffectiveCachePolicy.Selection selection = selection("reserved-member", 1_000, 10, true);
        Sinks.One<String> load = Sinks.one();
        AtomicInteger subscriptions = new AtomicInteger();
        AtomicInteger cancellations = new AtomicInteger();
        Mono<String> call = cached(manager, selection, key("shared"), () -> Mono.defer(() -> {
            subscriptions.incrementAndGet();
            return load.asMono().doOnCancel(cancellations::incrementAndGet);
        }));
        Disposable current = call.subscribe();

        Field flightsField = LocalResponseCacheManager.class.getDeclaredField("inFlightLoads");
        flightsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<Object, Object> flights = (Map<Object, Object>) flightsField.get(manager);
        Mono<?> delayedMember;
        synchronized (flights) {
            Object flight = flights.values().iterator().next();
            java.lang.reflect.Method reserve = flight.getClass()
                    .getDeclaredMethod("reserve", SubscriptionReportingState.class);
            reserve.setAccessible(true);
            Object member = reserve.invoke(flight, new Object[]{null});
            java.lang.reflect.Method publisher = flight.getClass()
                    .getDeclaredMethod("publisher", member.getClass());
            publisher.setAccessible(true);
            delayedMember = (Mono<?>) publisher.invoke(flight, member);
        }

        current.dispose();
        assertThat(cancellations).hasValue(0);
        AtomicReference<Object> value = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        delayedMember.subscribe(value::set, error::set);
        load.tryEmitValue("shared-value").orThrow();

        assertThat(value).hasValue("shared-value");
        assertThat(error.get()).isNull();
        assertThat(subscriptions).hasValue(1);
        assertThat(cancellations).hasValue(0);
    }

    @Test
    void singleFlightFansOutErrorAndEmptyThenAllowsANewLoad() throws Exception {
        LocalResponseCacheManager manager = LocalResponseCacheManager.testing(System::nanoTime);
        EffectiveCachePolicy.Selection selection = selection("terminal", 1_000, 10, true);
        Sinks.One<String> failedLoad = Sinks.one();
        AtomicInteger failedSubscriptions = new AtomicInteger();
        Mono<String> failure = cached(manager, selection, key("failure"), () -> Mono.defer(() -> {
            failedSubscriptions.incrementAndGet();
            return failedLoad.asMono();
        }));
        CompletableFuture<String> firstFailure = failure.toFuture();
        CompletableFuture<String> secondFailure = failure.toFuture();
        failedLoad.tryEmitError(new IllegalStateException("load failed")).orThrow();

        assertThatThrownBy(() -> firstFailure.get(1, TimeUnit.SECONDS)).hasRootCauseMessage("load failed");
        assertThatThrownBy(() -> secondFailure.get(1, TimeUnit.SECONDS)).hasRootCauseMessage("load failed");
        assertThat(failedSubscriptions).hasValue(1);
        assertThat(cached(manager, selection, key("failure"), () -> Mono.just("recovered")).block())
                .isEqualTo("recovered");

        Sinks.One<String> emptyLoad = Sinks.one();
        AtomicInteger emptySubscriptions = new AtomicInteger();
        Mono<String> empty = cached(manager, selection, key("empty"), () -> Mono.defer(() -> {
            emptySubscriptions.incrementAndGet();
            return emptyLoad.asMono();
        }));
        CompletableFuture<String> firstEmpty = empty.toFuture();
        CompletableFuture<String> secondEmpty = empty.toFuture();
        emptyLoad.tryEmitEmpty().orThrow();

        assertThat(firstEmpty.get(1, TimeUnit.SECONDS)).isNull();
        assertThat(secondEmpty.get(1, TimeUnit.SECONDS)).isNull();
        assertThat(emptySubscriptions).hasValue(1);
        assertThat(cached(manager, selection, key("empty"), () -> Mono.just("filled")).block())
                .isEqualTo("filled");
    }

    @Test
    void singleFlightKeepsEachCallersTimeoutBudgetIndependent() {
        assertTimeoutDirection(true);
        assertTimeoutDirection(false);
    }

    @Test
    void lateDuplicateCannotRepopulateAfterCapacityEviction() throws Exception {
        LocalResponseCacheManager manager = LocalResponseCacheManager.testing(System::nanoTime);
        EffectiveCachePolicy.Selection selection = selection("eviction", 1_000, 1);
        CacheKeyContract.OpaqueKey sharedKey = key("shared");
        Sinks.One<String> lateLoad = Sinks.one();
        Sinks.One<String> winningLoad = Sinks.one();
        AtomicInteger duplicateSubscriptions = new AtomicInteger();
        Mono<String> duplicate = cached(manager, selection, sharedKey, () ->
                duplicateSubscriptions.getAndIncrement() == 0 ? lateLoad.asMono() : winningLoad.asMono());

        CompletableFuture<String> late = duplicate.toFuture();
        CompletableFuture<String> winner = duplicate.toFuture();
        winningLoad.tryEmitValue("winner").orThrow();
        assertThat(winner.get(1, TimeUnit.SECONDS)).isEqualTo("winner");
        assertThat(cached(manager, selection, key("other"), () -> Mono.just("other")).block())
                .isEqualTo("other");

        lateLoad.tryEmitValue("late").orThrow();
        assertThat(late.get(1, TimeUnit.SECONDS)).isEqualTo("late");
        AtomicInteger freshLoads = new AtomicInteger();
        assertThat(cached(manager, selection, sharedKey, () -> {
            freshLoads.incrementAndGet();
            return Mono.just("fresh");
        }).block()).isEqualTo("fresh");
        assertThat(freshLoads).hasValue(1);
    }

    @Test
    void lateDuplicateCannotReplaceAFreshGenerationAfterExpiry() throws Exception {
        AtomicLong ticker = new AtomicLong();
        LocalResponseCacheManager manager = LocalResponseCacheManager.testing(ticker::get);
        EffectiveCachePolicy.Selection selection = selection("expiry-generation", 100, 10);
        CacheKeyContract.OpaqueKey key = key("shared");
        Sinks.One<String> lateLoad = Sinks.one();
        Sinks.One<String> winningLoad = Sinks.one();
        AtomicInteger subscriptions = new AtomicInteger();
        Mono<String> duplicate = cached(manager, selection, key, () ->
                subscriptions.getAndIncrement() == 0 ? lateLoad.asMono() : winningLoad.asMono());

        CompletableFuture<String> late = duplicate.toFuture();
        CompletableFuture<String> winner = duplicate.toFuture();
        winningLoad.tryEmitValue("winner").orThrow();
        assertThat(winner.get(1, TimeUnit.SECONDS)).isEqualTo("winner");

        ticker.addAndGet(Duration.ofMillis(100).toNanos());
        AtomicInteger freshLoads = new AtomicInteger();
        assertThat(cached(manager, selection, key, () -> {
            freshLoads.incrementAndGet();
            return Mono.just("fresh");
        }).block()).isEqualTo("fresh");

        lateLoad.tryEmitValue("late").orThrow();
        assertThat(late.get(1, TimeUnit.SECONDS)).isEqualTo("late");
        assertThat(cached(manager, selection, key, () -> Mono.just("unexpected")).block())
                .isEqualTo("fresh");
        assertThat(freshLoads).hasValue(1);
    }

    @Test
    void lateDuplicateCannotRepopulateAfterTheWinningEntryExpires() throws Exception {
        AtomicLong ticker = new AtomicLong();
        LocalResponseCacheManager manager = LocalResponseCacheManager.testing(ticker::get);
        EffectiveCachePolicy.Selection selection = selection("expired-winner", 100, 10);
        CacheKeyContract.OpaqueKey key = key("shared");
        Sinks.One<String> lateLoad = Sinks.one();
        Sinks.One<String> winningLoad = Sinks.one();
        AtomicInteger subscriptions = new AtomicInteger();
        Mono<String> duplicate = cached(manager, selection, key, () ->
                subscriptions.getAndIncrement() == 0 ? lateLoad.asMono() : winningLoad.asMono());

        CompletableFuture<String> late = duplicate.toFuture();
        CompletableFuture<String> winner = duplicate.toFuture();
        winningLoad.tryEmitValue("winner").orThrow();
        assertThat(winner.get(1, TimeUnit.SECONDS)).isEqualTo("winner");

        ticker.addAndGet(Duration.ofMillis(100).toNanos());
        lateLoad.tryEmitValue("late").orThrow();
        assertThat(late.get(1, TimeUnit.SECONDS)).isEqualTo("late");

        AtomicInteger freshLoads = new AtomicInteger();
        assertThat(cached(manager, selection, key, () -> {
            freshLoads.incrementAndGet();
            return Mono.just("fresh");
        }).block()).isEqualTo("fresh");
        assertThat(freshLoads).hasValue(1);
    }

    @Test
    void emptyFailureAndCancellationNeverPopulateEntries() {
        LocalResponseCacheManager manager = LocalResponseCacheManager.testing(System::nanoTime);
        EffectiveCachePolicy.Selection selection = selection("terminal", 1_000, 10);

        assertThat(cached(manager, selection, key("empty"), Mono::empty).block()).isNull();
        assertThat(cached(manager, selection, key("empty"), () -> Mono.just("after-empty")).block())
                .isEqualTo("after-empty");

        assertThatThrownBy(() -> cached(manager, selection, key("error"),
                () -> Mono.error(new IllegalStateException("load failed"))).block())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("load failed");
        assertThat(cached(manager, selection, key("error"), () -> Mono.just("after-error")).block())
                .isEqualTo("after-error");

        Disposable cancelled = cached(manager, selection, key("cancel"), Mono::<String>never).subscribe();
        cancelled.dispose();
        assertThat(cached(manager, selection, key("cancel"), () -> Mono.just("after-cancel")).block())
                .isEqualTo("after-cancel");
    }

    @Test
    void responseEntitiesRetainOnlyRepresentationHeadersAndSensitiveResponsesAreNotStored() {
        LocalResponseCacheManager manager = LocalResponseCacheManager.testing(System::nanoTime);
        EffectiveCachePolicy.Selection selection = selection("entity", 1_000, 10);
        selection.policy().setNonCacheableResponseHeaders(List.of("x-caller-session"));
        AtomicInteger safeLoads = new AtomicInteger();
        ResponseEntity<String> source = ResponseEntity.status(203)
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .header(HttpHeaders.ETAG, "v1")
                .header("X-Request-Id", "caller-one")
                .body("body");

        Mono<ResponseEntity<String>> safe = cached(manager, selection, key("safe"), () -> {
            safeLoads.incrementAndGet();
            return Mono.just(source);
        });
        ResponseEntity<String> first = safe.block();
        ResponseEntity<String> hit = safe.block();

        assertThat(first).isSameAs(source);
        assertThat(hit.getStatusCode().value()).isEqualTo(203);
        assertThat(hit.getBody()).isSameAs(source.getBody());
        assertThat(hit.getHeaders().getFirst(HttpHeaders.ETAG)).isEqualTo("v1");
        assertThat(hit.getHeaders().containsHeader("X-Request-Id")).isFalse();
        assertThat(safeLoads).hasValue(1);

        AtomicInteger sensitiveLoads = new AtomicInteger();
        Mono<ResponseEntity<String>> sensitive = cached(manager, selection, key("sensitive"), () -> {
            sensitiveLoads.incrementAndGet();
            return Mono.just(ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, "SESSION=secret")
                    .body("private"));
        });
        sensitive.block();
        sensitive.block();
        assertThat(sensitiveLoads).hasValue(2);

        for (String headerName : List.of(
                HttpHeaders.AUTHORIZATION,
                HttpHeaders.WWW_AUTHENTICATE,
                HttpHeaders.PROXY_AUTHENTICATE,
                "X-Caller-Session")) {
            AtomicInteger privateLoads = new AtomicInteger();
            Mono<ResponseEntity<String>> privateResponse = cached(
                    manager, selection, key("private-" + headerName), () -> {
                        privateLoads.incrementAndGet();
                        return Mono.just(ResponseEntity.ok()
                                .header(headerName, "private")
                                .body("private"));
                    });
            privateResponse.block();
            privateResponse.block();
            assertThat(privateLoads).as(headerName).hasValue(2);
        }

        AtomicInteger oversizedLoads = new AtomicInteger();
        Mono<ResponseEntity<String>> oversized = cached(manager, selection, key("oversized"), () -> {
            oversizedLoads.incrementAndGet();
            ResponseEntity.BodyBuilder builder = ResponseEntity.ok();
            for (int index = 0; index < 33; index++) {
                builder.header(HttpHeaders.ETAG, "value-" + index);
            }
            return Mono.just(builder.body("oversized-headers"));
        });
        oversized.block();
        oversized.block();
        assertThat(oversizedLoads).hasValue(2);
    }

    @Test
    void shutdownClearsEntriesAndRejectsLatePublication() {
        LocalResponseCacheManager manager = LocalResponseCacheManager.testing(System::nanoTime);
        EffectiveCachePolicy.Selection selection = selection("shutdown", 1_000, 10);
        Sinks.One<String> load = Sinks.one();
        CompletableFuture<String> result = cached(manager, selection, key("late"), load::asMono).toFuture();

        manager.close();
        load.tryEmitValue("late-value").orThrow();

        assertThatCode(() -> result.get(1, TimeUnit.SECONDS)).doesNotThrowAnyException();
        assertThat(manager.snapshot()).isEqualTo(
                new LocalResponseCacheManager.Snapshot(0, 0, 0, true));
        assertThatThrownBy(() -> cached(manager, selection, key("late"), () -> Mono.just("new")).block())
                .hasMessageContaining("closed");
    }

    @Test
    void shutdownTerminatesCoalescedLoadsAndPreventsLatePublication() throws Exception {
        LocalResponseCacheManager manager = LocalResponseCacheManager.testing(System::nanoTime);
        EffectiveCachePolicy.Selection selection = selection("shutdown-flight", 1_000, 10, true);
        Sinks.One<String> load = Sinks.one();
        AtomicInteger cancellations = new AtomicInteger();
        CompletableFuture<String> leader = cached(manager, selection, key("shared"), () ->
                load.asMono().doOnCancel(cancellations::incrementAndGet)).toFuture();
        CompletableFuture<String> waiter = cached(manager, selection, key("shared"), () ->
                Mono.just("must-not-run")).toFuture();

        manager.close();

        assertThat(leader.get(1, TimeUnit.SECONDS)).isNull();
        assertThat(waiter.get(1, TimeUnit.SECONDS)).isNull();
        assertThat(cancellations).hasValue(1);
        load.tryEmitValue("late").orThrow();
        assertThat(manager.snapshot()).isEqualTo(
                new LocalResponseCacheManager.Snapshot(0, 0, 0, true));
    }

    @Test
    void factoryShutdownClosesItsCacheBeforeTransportDisposal() {
        ReactiveHttpClientProperties properties = new ReactiveHttpClientProperties();
        ReactiveHttpClientProperties.ClientConfig clientConfig = config();
        clientConfig.setBaseUrl("http://cache.test");
        ReactiveHttpClientProperties.CachePolicyConfig policy =
                clientConfig.getCache().getPolicies().get("local");
        policy.setVaryByParameters(List.of("principal", "tenant"));
        policy.setVaryByHeaders(List.of("Accept-Language", "Idempotency-Key"));
        clientConfig.getCache().getCustomizations().put(
                "starterWebClientBuilder", ReactiveHttpClientProperties.CacheCustomizationSafety.SAFE);
        properties.getClients().put("cache-client", clientConfig);

        StaticApplicationContext context = new StaticApplicationContext();
        context.getBeanFactory().registerSingleton("reactiveHttpClientProperties", properties);
        context.getBeanFactory().registerSingleton("starterWebClientBuilder", WebClient.builder()
                .exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.OK)
                        .header(HttpHeaders.CONTENT_TYPE, "text/plain")
                        .body("cached")
                        .build())));
        context.getBeanFactory().registerSingleton("reactiveHttpClientJsonCodec", TestJsonCodecs.jsonCodec());

        ReactiveHttpClientFactoryBean<CacheClient> factory = new ReactiveHttpClientFactoryBean<>();
        factory.setType(CacheClient.class);
        factory.setApplicationContext(context);
        try {
            assertThat(factory.getObject().get("42", "principal", "tenant", "en-US").block())
                    .isEqualTo("cached");
            assertThat(factory.responseCacheSnapshot().currentSize()).isEqualTo(1);

            factory.destroy();

            assertThat(factory.responseCacheSnapshot()).isEqualTo(
                    new LocalResponseCacheManager.Snapshot(0, 0, 0, true));
        }
        finally {
            factory.destroy();
            context.close();
        }
    }

    @Test
    void optionalImplementationIsRequiredOnlyForSelectedPolicies() throws Exception {
        ClassLoader withoutCaffeine = new ClassLoader(getClass().getClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.startsWith("com.github.benmanes.caffeine")) {
                    throw new ClassNotFoundException(name);
                }
                return super.loadClass(name, resolve);
            }
        };
        MethodMetadataCache metadata = new MethodMetadataCache();
        ReactiveHttpClientProperties.ClientConfig disabled = new ReactiveHttpClientProperties.ClientConfig();

        LocalResponseCacheManager disabledManager = LocalResponseCacheManager.createForClient(
                CacheClient.class, "cache-client", metadata, disabled, withoutCaffeine);
        assertThat(disabledManager.snapshot().policyCount()).isZero();

        ReactiveHttpClientProperties.ClientConfig selected = config();
        assertThatThrownBy(() -> LocalResponseCacheManager.createForClient(
                CacheClient.class, "cache-client", metadata, selected, withoutCaffeine))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cache-client")
                .hasMessageContaining("com.github.ben-manes.caffeine:caffeine");
    }

    @Test
    void declarativeHitsBypassTheLoadPipelineAndVariantsRemainIsolated() {
        ReactiveHttpClientProperties.ClientConfig config = config();
        ReactiveHttpClientProperties.CachePolicyConfig policy =
                config.getCache().getPolicies().get("local");
        policy.setVaryByParameters(List.of("principal", "tenant"));
        policy.setVaryByHeaders(List.of("Accept-Language", "Idempotency-Key"));
        policy.setVaryByContext(List.of("region"));
        config.getResilience().setEnabled(true);
        config.getResilience().setCircuitBreaker("catalog-cb");
        AtomicInteger dispatches = new AtomicInteger();
        RecordingCircuitBreakerApplier applier = new RecordingCircuitBreakerApplier();
        WebClient webClient = WebClient.builder()
                .baseUrl("http://cache.test")
                .exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.OK)
                        .header(HttpHeaders.CONTENT_TYPE, "text/plain")
                        .body("dispatch-" + dispatches.incrementAndGet())
                        .build()))
                .build();

        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.refresh();
            LocalResponseCacheManager manager = LocalResponseCacheManager.testing(System::nanoTime);
            ReactiveClientInvocationHandler handler = new ReactiveClientInvocationHandler(
                    webClient,
                    new MethodMetadataCache(),
                    new RequestArgumentResolver(),
                    new DefaultErrorDecoder(),
                    config,
                    "cache-client",
                    CacheClient.class,
                    context,
                    applier,
                    TestJsonCodecs.jsonCodec(),
                    new ReactiveHttpClientProperties.ObservabilityConfig(),
                    manager);
            CacheClient client = (CacheClient) Proxy.newProxyInstance(
                    getClass().getClassLoader(), new Class<?>[]{CacheClient.class}, handler);

            Mono<String> base = client.get("42", "principal-a", "tenant-a", "en-US")
                    .contextWrite(ctx -> ctx.put("region", "apac"));
            String first = base.block();
            String hit = base.block();
            String otherPrincipal = client.get("42", "principal-b", "tenant-a", "en-US")
                    .contextWrite(ctx -> ctx.put("region", "apac"))
                    .block();
            String otherLocale = client.get("42", "principal-a", "tenant-a", "fr-FR")
                    .contextWrite(ctx -> ctx.put("region", "apac"))
                    .block();
            String otherRegion = client.get("42", "principal-a", "tenant-a", "en-US")
                    .contextWrite(ctx -> ctx.put("region", "emea"))
                    .block();

            assertThat(hit).isSameAs(first);
            assertThat(otherPrincipal).isNotEqualTo(first);
            assertThat(otherLocale).isNotEqualTo(first);
            assertThat(otherRegion).isNotEqualTo(first);
            assertThat(manager.snapshot().currentSize()).isEqualTo(4);
        }

        assertThat(dispatches).hasValue(4);
        assertThat(applier.applications).hasValue(4);
        assertThat(applier.subscriptions).hasValue(4);
    }

    @Test
    void refreshUsesThePreparedAuthAndResiliencePipelineWithoutDelayingTheStaleCaller() {
        AtomicLong ticker = new AtomicLong();
        ReactiveHttpClientProperties.ClientConfig config = config();
        config.setAuthProvider("cache-auth");
        config.getResilience().setEnabled(true);
        config.getResilience().setCircuitBreaker("catalog-cb");
        ReactiveHttpClientProperties.CachePolicyConfig policy =
                config.getCache().getPolicies().get("local");
        policy.setVaryByParameters(List.of("principal", "tenant"));
        policy.setVaryByHeaders(List.of("Accept-Language", "Idempotency-Key"));
        policy.setRefreshAfterMs(50L);
        policy.setRefreshTimeoutMs(500L);
        AtomicInteger authCalls = new AtomicInteger();
        AtomicInteger dispatches = new AtomicInteger();
        RecordingCircuitBreakerApplier applier = new RecordingCircuitBreakerApplier();
        io.github.huynhngochuyhoang.httpstarter.auth.AuthProvider authProvider = request -> {
            authCalls.incrementAndGet();
            return Mono.just(AuthContext.builder().header("Authorization", "Bearer refresh-token").build());
        };
        WebClient webClient = WebClient.builder()
                .baseUrl("http://cache.test")
                .filter(new OutboundAuthFilter("cache-client", authProvider))
                .exchangeFunction(request -> {
                    assertThat(request.headers().getFirst(HttpHeaders.AUTHORIZATION))
                            .isEqualTo("Bearer refresh-token");
                    return Mono.just(ClientResponse.create(HttpStatus.OK)
                            .header(HttpHeaders.CONTENT_TYPE, "text/plain")
                            .body("dispatch-" + dispatches.incrementAndGet())
                            .build());
                })
                .build();

        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.refresh();
            LocalResponseCacheManager manager = LocalResponseCacheManager.testing(ticker::get);
            CacheClient client = cacheClient(webClient, config, context, manager, applier, authProvider);

            assertThat(client.get("42", "principal", "tenant", "en-US").block())
                    .isEqualTo("dispatch-1");
            ticker.addAndGet(Duration.ofMillis(50).toNanos());
            assertThat(client.get("42", "principal", "tenant", "en-US").block())
                    .isEqualTo("dispatch-1");

            assertThat(dispatches).hasValue(2);
            assertThat(authCalls).hasValue(2);
            assertThat(applier.applications).hasValue(2);
            assertThat(applier.subscriptions).hasValue(2);
            assertThat(client.get("42", "principal", "tenant", "en-US").block())
                    .isEqualTo("dispatch-2");
            manager.close();
        }
    }

    @Test
    void configuredAuthRunsBeforeHitsAndIsReusedByTheMissFilter() {
        ReactiveHttpClientProperties.ClientConfig config = config();
        config.setAuthProvider("cache-auth");
        ReactiveHttpClientProperties.CachePolicyConfig policy =
                config.getCache().getPolicies().get("local");
        policy.setVaryByParameters(List.of("principal", "tenant"));
        policy.setVaryByHeaders(List.of("Accept-Language", "Idempotency-Key"));
        AtomicInteger authCalls = new AtomicInteger();
        AtomicInteger dispatches = new AtomicInteger();
        AtomicBoolean reject = new AtomicBoolean();
        io.github.huynhngochuyhoang.httpstarter.auth.AuthProvider authProvider = request -> {
            authCalls.incrementAndGet();
            if (reject.get()) {
                return Mono.error(new IllegalStateException("principal rejected"));
            }
            return Mono.just(AuthContext.builder().header("Authorization", "Bearer safe-test-token").build());
        };
        WebClient webClient = WebClient.builder()
                .baseUrl("http://cache.test")
                .filter(new OutboundAuthFilter("cache-client", authProvider))
                .exchangeFunction(request -> {
                    assertThat(request.headers().getFirst(HttpHeaders.AUTHORIZATION))
                            .isEqualTo("Bearer safe-test-token");
                    dispatches.incrementAndGet();
                    return Mono.just(ClientResponse.create(HttpStatus.OK)
                            .header(HttpHeaders.CONTENT_TYPE, "text/plain")
                            .body("authorized")
                            .build());
                })
                .build();

        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.refresh();
            ReactiveClientInvocationHandler handler = ReactiveClientInvocationHandler.create(
                    webClient,
                    new MethodMetadataCache(),
                    new RequestArgumentResolver(),
                    new DefaultErrorDecoder(),
                    config,
                    "cache-client",
                    CacheClient.class,
                    context,
                    new NoopResilienceOperatorApplier(),
                    TestJsonCodecs.jsonCodec(),
                    new ReactiveHttpClientProperties.ObservabilityConfig(),
                    authProvider,
                    "http://cache.test");
            CacheClient client = (CacheClient) Proxy.newProxyInstance(
                    getClass().getClassLoader(), new Class<?>[]{CacheClient.class}, handler);

            assertThat(client.get("42", "principal-a", "tenant-a", "en-US").block())
                    .isEqualTo("authorized");
            assertThat(authCalls).hasValue(1);
            assertThat(dispatches).hasValue(1);

            reject.set(true);
            assertThatThrownBy(() -> client.get("42", "principal-a", "tenant-a", "en-US").block())
                    .hasRootCauseMessage("principal rejected");
            assertThat(authCalls).hasValue(2);
            assertThat(dispatches).hasValue(1);
        }
    }

    @Test
    void plainSensitiveResponsesAndRedirectEnvelopesAreNeverCached() {
        ReactiveHttpClientProperties.ClientConfig config = config();
        config.getCache().getPolicies().get("local").setVaryByParameters(List.of("partition"));
        AtomicInteger plainDispatches = new AtomicInteger();
        AtomicInteger redirectDispatches = new AtomicInteger();
        WebClient webClient = WebClient.builder()
                .baseUrl("http://cache.test")
                .exchangeFunction(request -> {
                    if (request.url().getPath().startsWith("/plain/")) {
                        return Mono.just(ClientResponse.create(HttpStatus.OK)
                                .header(HttpHeaders.CONTENT_TYPE, "text/plain")
                                .header(HttpHeaders.SET_COOKIE, "SESSION=private")
                                .body("plain-" + plainDispatches.incrementAndGet())
                                .build());
                    }
                    int dispatch = redirectDispatches.incrementAndGet();
                    return Mono.just(ClientResponse.create(HttpStatus.FOUND)
                            .header(HttpHeaders.LOCATION, "/target-" + dispatch)
                            .body("redirect-" + dispatch)
                            .build());
                })
                .build();

        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.refresh();
            LocalResponseCacheManager manager = LocalResponseCacheManager.testing(System::nanoTime);
            ResponseCacheClient client = responseCacheClient(webClient, config, context, manager);

            assertThat(client.plain("42", "tenant-a").block()).isEqualTo("plain-1");
            assertThat(client.plain("42", "tenant-a").block()).isEqualTo("plain-2");

            ResponseEntity<String> first = client.redirect("42", "tenant-a").block();
            ResponseEntity<String> second = client.redirect("42", "tenant-a").block();
            assertThat(first.getStatusCode()).isEqualTo(HttpStatus.FOUND);
            assertThat(first.getHeaders().getLocation()).hasPath("/target-1");
            assertThat(second.getStatusCode()).isEqualTo(HttpStatus.FOUND);
            assertThat(second.getHeaders().getLocation()).hasPath("/target-2");
        }

        assertThat(plainDispatches).hasValue(2);
        assertThat(redirectDispatches).hasValue(2);
    }

    @Test
    void cacheAuthorizationUsesFrozenContextAndTheLogicalCallBudgetOnHits() {
        ReactiveHttpClientProperties.ClientConfig config = config();
        config.setAuthProvider("cache-auth");
        config.setLogicalCallTimeoutMs(75L);
        ReactiveHttpClientProperties.CachePolicyConfig policy =
                config.getCache().getPolicies().get("local");
        policy.setVaryByParameters(List.of("principal", "tenant"));
        policy.setVaryByHeaders(List.of("Accept-Language", "Idempotency-Key"));
        policy.setVaryByContext(List.of("region"));
        AtomicReference<List<String>> callerRegion = new AtomicReference<>();
        AtomicReference<List<String>> observedRegion = new AtomicReference<>();
        AtomicBoolean stall = new AtomicBoolean();
        AtomicInteger dispatches = new AtomicInteger();
        io.github.huynhngochuyhoang.httpstarter.auth.AuthProvider authProvider = request ->
                Mono.deferContextual(context -> {
                    callerRegion.get().set(0, "mutated-after-key");
                    observedRegion.set(List.copyOf(context.get("region")));
                    if (stall.get()) {
                        return Mono.never();
                    }
                    return Mono.just(AuthContext.empty());
                });
        WebClient webClient = WebClient.builder()
                .baseUrl("http://cache.test")
                .filter(new OutboundAuthFilter("cache-client", authProvider))
                .exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.OK)
                        .header(HttpHeaders.CONTENT_TYPE, "text/plain")
                        .body("dispatch-" + dispatches.incrementAndGet())
                        .build()))
                .build();

        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.refresh();
            LocalResponseCacheManager manager = LocalResponseCacheManager.testing(System::nanoTime);
            CacheClient client = cacheClient(webClient, config, context, manager,
                    new NoopResilienceOperatorApplier(), authProvider);

            List<String> fillRegion = new ArrayList<>(List.of("apac"));
            callerRegion.set(fillRegion);
            assertThat(client.get("42", "principal-a", "tenant-a", "en-US")
                    .contextWrite(contextView -> contextView.put("region", fillRegion))
                    .block()).isEqualTo("dispatch-1");
            assertThat(observedRegion.get()).containsExactly("apac");

            List<String> hitRegion = new ArrayList<>(List.of("apac"));
            callerRegion.set(hitRegion);
            stall.set(true);
            assertThatThrownBy(() -> client.get("42", "principal-a", "tenant-a", "en-US")
                    .contextWrite(contextView -> contextView.put("region", hitRegion))
                    .block(Duration.ofSeconds(1)))
                    .satisfies(error -> assertThat(hasCause(error, LogicalCallTimeoutException.class)).isTrue());
            assertThat(observedRegion.get()).containsExactly("apac");
            assertThat(dispatches).hasValue(1);
        }
    }

    @Test
    void cachedMissUsesOneLogicalDeadlineAndReportsResponseBodyTimeout() {
        ReactiveHttpClientProperties.ClientConfig config = config();
        config.setLogicalCallTimeoutMs(500L);
        config.getCache().getPolicies().get("local")
                .setVaryByParameters(List.of("principal", "tenant"));
        config.getCache().getPolicies().get("local")
                .setVaryByHeaders(List.of("Accept-Language", "Idempotency-Key"));
        AtomicReference<HttpClientObserverEvent> observed = new AtomicReference<>();
        DisposableServer server = HttpServer.create()
                .port(0)
                .handle((request, response) -> response
                        .header(HttpHeaders.CONTENT_TYPE, "text/plain")
                        .sendString(Flux.concat(Mono.just("first"), Mono.never()))
                        .then())
                .bindNow();
        try {
            WebClient webClient = WebClient.builder()
                    .baseUrl("http://127.0.0.1:" + server.port())
                    .clientConnector(new ReactorClientHttpConnector())
                    .filter(ReactiveClientInvocationHandler.finalRequestObservationFilter())
                    .build();
            try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
                context.getBeanFactory().registerSingleton("cacheObserver",
                        (io.github.huynhngochuyhoang.httpstarter.observability.HttpClientObserver) observed::set);
                context.refresh();
                CacheClient client = cacheClient(
                        webClient,
                        config,
                        context,
                        LocalResponseCacheManager.testing(System::nanoTime),
                        new NoopResilienceOperatorApplier(),
                        null);

                Throwable failure = catchThrowable(() -> client
                        .get("42", "principal-a", "tenant-a", "en-US")
                        .block(Duration.ofSeconds(2)));

                LogicalCallTimeoutException timeout = findCause(failure, LogicalCallTimeoutException.class);
                assertThat(timeout).isNotNull();
                assertThat(timeout.getFailureStage()).isEqualTo(HttpClientFailureStage.RESPONSE_BODY);
                assertThat(observed.get()).isNotNull();
                assertThat(observed.get().getStatusCode()).isEqualTo(200);
                assertThat(observed.get().getFailureStage()).isEqualTo(HttpClientFailureStage.RESPONSE_BODY);
                assertThat(findCause(observed.get().getError(), LogicalCallTimeoutException.class)).isSameAs(timeout);
            }
        } finally {
            server.disposeNow();
        }
    }

    @Test
    void coalescedCallersOwnIndependentLogicalDeadlinesInBothDirections() throws Exception {
        VirtualTimeScheduler scheduler = VirtualTimeScheduler.getOrSet();
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.refresh();
            LocalResponseCacheManager manager = LocalResponseCacheManager.testing(System::nanoTime);
            Map<String, Sinks.One<ClientResponse>> responses = new ConcurrentHashMap<>();
            Map<String, AtomicInteger> dispatches = new ConcurrentHashMap<>();
            WebClient webClient = WebClient.builder()
                    .baseUrl("http://cache.test")
                    .exchangeFunction(request -> {
                        String path = request.url().getPath();
                        dispatches.computeIfAbsent(path, ignored -> new AtomicInteger()).incrementAndGet();
                        return responses.get(path).asMono();
                    })
                    .build();
            ReactiveHttpClientProperties.ClientConfig shortConfig = singleFlightConfig(50);
            ReactiveHttpClientProperties.ClientConfig longConfig = singleFlightConfig(200);
            CacheClient shortBudget = cacheClient(
                    webClient, shortConfig, context, manager, new NoopResilienceOperatorApplier(), null);
            CacheClient longBudget = cacheClient(
                    webClient, longConfig, context, manager, new NoopResilienceOperatorApplier(), null);

            responses.put("/catalog/waiter-timeout", Sinks.one());
            CompletableFuture<String> leader = longBudget
                    .get("waiter-timeout", "principal", "tenant", "en-US").toFuture();
            CompletableFuture<String> waiter = shortBudget
                    .get("waiter-timeout", "principal", "tenant", "en-US").toFuture();
            scheduler.advanceTimeBy(Duration.ofMillis(50));

            assertThatThrownBy(() -> waiter.get(1, TimeUnit.SECONDS))
                    .satisfies(error -> assertThat(hasCause(error, LogicalCallTimeoutException.class)).isTrue());
            assertThat(leader).isNotDone();
            responses.get("/catalog/waiter-timeout").tryEmitValue(ok("leader-success")).orThrow();
            assertThat(leader.get(1, TimeUnit.SECONDS)).isEqualTo("leader-success");
            assertThat(dispatches.get("/catalog/waiter-timeout")).hasValue(1);

            responses.put("/catalog/leader-timeout", Sinks.one());
            CompletableFuture<String> firstCaller = shortBudget
                    .get("leader-timeout", "principal", "tenant", "en-US").toFuture();
            CompletableFuture<String> laterWaiter = longBudget
                    .get("leader-timeout", "principal", "tenant", "en-US").toFuture();
            scheduler.advanceTimeBy(Duration.ofMillis(50));

            assertThatThrownBy(() -> firstCaller.get(1, TimeUnit.SECONDS))
                    .satisfies(error -> assertThat(hasCause(error, LogicalCallTimeoutException.class)).isTrue());
            assertThat(laterWaiter).isNotDone();
            responses.get("/catalog/leader-timeout").tryEmitValue(ok("waiter-success")).orThrow();
            assertThat(laterWaiter.get(1, TimeUnit.SECONDS)).isEqualTo("waiter-success");
            assertThat(dispatches.get("/catalog/leader-timeout")).hasValue(1);
        }
        finally {
            VirtualTimeScheduler.reset();
        }
    }

    @Test
    void retryAfterFirstCallerTimeoutUsesFlightStateAndCompletesTheWaiterState() throws Exception {
        VirtualTimeScheduler scheduler = VirtualTimeScheduler.getOrSet();
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            List<HttpClientObserverEvent> observed = new CopyOnWriteArrayList<>();
            context.getBeanFactory().registerSingleton("cacheObserver",
                    (io.github.huynhngochuyhoang.httpstarter.observability.HttpClientObserver) observed::add);
            context.refresh();
            LocalResponseCacheManager manager = LocalResponseCacheManager.testing(System::nanoTime);
            AtomicInteger dispatches = new AtomicInteger();
            Sinks.One<ClientResponse> firstAttempt = Sinks.one();
            Sinks.One<ClientResponse> retryAttempt = Sinks.one();
            WebClient webClient = WebClient.builder()
                    .baseUrl("http://cache.test")
                    .filter(ReactiveClientInvocationHandler.finalRequestObservationFilter())
                    .exchangeFunction(request -> dispatches.incrementAndGet() == 1
                            ? firstAttempt.asMono()
                            : retryAttempt.asMono())
                    .build();
            ReactiveHttpClientProperties.ClientConfig shortConfig = singleFlightConfig(50);
            ReactiveHttpClientProperties.ClientConfig longConfig = singleFlightConfig(200);
            shortConfig.getResilience().setEnabled(true);
            shortConfig.getResilience().setRetry("cache-retry");
            longConfig.getResilience().setEnabled(true);
            longConfig.getResilience().setRetry("cache-retry");
            RetryOnceApplier retryApplier = new RetryOnceApplier();
            CacheClient firstCaller = cacheClient(
                    webClient, shortConfig, context, manager, retryApplier, null);
            CacheClient waiter = cacheClient(
                    webClient, longConfig, context, manager, retryApplier, null);

            CompletableFuture<String> timedOut = firstCaller
                    .get("retry-after-timeout", "principal", "tenant", "en-US").toFuture();
            CompletableFuture<String> surviving = waiter
                    .get("retry-after-timeout", "principal", "tenant", "en-US").toFuture();
            assertThat(dispatches).hasValue(1);
            scheduler.advanceTimeBy(Duration.ofMillis(50));

            assertThatThrownBy(() -> timedOut.get(1, TimeUnit.SECONDS))
                    .satisfies(error -> assertThat(hasCause(error, LogicalCallTimeoutException.class)).isTrue());
            firstAttempt.tryEmitValue(ClientResponse.create(HttpStatus.SERVICE_UNAVAILABLE)
                    .body("retry")
                    .build()).orThrow();
            assertThat(dispatches).hasValue(2);
            retryAttempt.tryEmitValue(ok("waiter-success")).orThrow();

            assertThat(surviving.get(1, TimeUnit.SECONDS)).isEqualTo("waiter-success");
            assertThat(observed).hasSize(2);
            HttpClientObserverEvent timeoutEvent = observed.stream()
                    .filter(event -> hasCause(event.getError(), LogicalCallTimeoutException.class))
                    .findFirst()
                    .orElseThrow();
            HttpClientObserverEvent waiterEvent = observed.stream()
                    .filter(event -> event.getError() == null)
                    .findFirst()
                    .orElseThrow();
            assertThat(timeoutEvent.getAttemptCount()).isEqualTo(1);
            assertThat(waiterEvent.getAttemptCount()).isEqualTo(2);
            assertThat(waiterEvent.getStatusCode()).isEqualTo(200);
            assertThat(retryApplier.applications).hasValue(1);
            assertThat(retryApplier.subscriptions).hasValue(1);
        } finally {
            VirtualTimeScheduler.reset();
        }
    }

    @Test
    void redirectDispatchesRemainInsideOneCoalescedLeader() throws Exception {
        AtomicInteger initialDispatches = new AtomicInteger();
        AtomicInteger redirectedDispatches = new AtomicInteger();
        CountDownLatch redirectedRequest = new CountDownLatch(1);
        Sinks.One<String> responseBody = Sinks.one();
        DisposableServer server = HttpServer.create()
                .port(0)
                .handle((request, response) -> {
                    if (request.uri().startsWith("/catalog/")) {
                        initialDispatches.incrementAndGet();
                        return response.status(HttpStatus.TEMPORARY_REDIRECT.value())
                                .header(HttpHeaders.LOCATION, "/redirect-target")
                                .send();
                    }
                    redirectedDispatches.incrementAndGet();
                    redirectedRequest.countDown();
                    return response.header(HttpHeaders.CONTENT_TYPE, "text/plain")
                            .sendString(responseBody.asMono());
                })
                .bindNow();
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.refresh();
            ReactiveHttpClientProperties.ClientConfig config = singleFlightConfig(0);
            config.setFollowRedirects(true);
            WebClient webClient = WebClient.builder()
                    .baseUrl("http://127.0.0.1:" + server.port())
                    .clientConnector(new ReactorClientHttpConnector(HttpClient.create().followRedirect(true)))
                    .build();
            CacheClient client = cacheClient(
                    webClient,
                    config,
                    context,
                    LocalResponseCacheManager.testing(System::nanoTime),
                    new NoopResilienceOperatorApplier(),
                    null);

            Mono<String> call = client.get("redirect", "principal", "tenant", "en-US");
            CompletableFuture<String> leader = call.toFuture();
            assertThat(redirectedRequest.await(1, TimeUnit.SECONDS)).isTrue();
            CompletableFuture<String> waiter = call.toFuture();
            assertThat(initialDispatches).hasValue(1);
            assertThat(redirectedDispatches).hasValue(1);

            responseBody.tryEmitValue("redirected").orThrow();
            assertThat(leader.get(1, TimeUnit.SECONDS)).isEqualTo("redirected");
            assertThat(waiter.get(1, TimeUnit.SECONDS)).isEqualTo("redirected");
            assertThat(initialDispatches).hasValue(1);
            assertThat(redirectedDispatches).hasValue(1);
        }
        finally {
            server.disposeNow();
        }
    }

    @Test
    void concurrentCallersCreateOneWireBodySubscription() throws Exception {
        AtomicInteger serverDispatches = new AtomicInteger();
        AtomicReference<String> receivedBody = new AtomicReference<>();
        CountDownLatch bodyReceived = new CountDownLatch(1);
        Sinks.One<String> responseBody = Sinks.one();
        DisposableServer server = HttpServer.create()
                .port(0)
                .handle((request, response) -> request.receive().aggregate().asString()
                        .flatMap(body -> {
                            serverDispatches.incrementAndGet();
                            receivedBody.set(body);
                            bodyReceived.countDown();
                            return response.header(HttpHeaders.CONTENT_TYPE, "text/plain")
                                    .sendString(responseBody.asMono())
                                    .then();
                        }))
                .bindNow();
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.refresh();
            AtomicInteger bodySubscriptions = new AtomicInteger();
            WebClient webClient = WebClient.builder()
                    .baseUrl("http://127.0.0.1:" + server.port())
                    .clientConnector(new ReactorClientHttpConnector())
                    .filter((request, next) -> {
                        BodyInserter<Object, ClientHttpRequest> countedBody = (output, inserterContext) ->
                                Mono.defer(() -> {
                                    bodySubscriptions.incrementAndGet();
                                    return request.body().insert(output, inserterContext);
                                });
                        return next.exchange(ClientRequest.from(request).body(countedBody).build());
                    })
                    .build();
            ReactiveHttpClientProperties.ClientConfig config = config();
            config.setDefaultHeaders(Map.of(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_VALUE));
            config.getCache().getPolicies().get("local").setSingleFlight(true);
            config.getCache().getPolicies().get("local").setSharedResponse(true);
            BodyCacheClient client = bodyCacheClient(
                    webClient, config, context, LocalResponseCacheManager.testing(System::nanoTime));

            Mono<String> call = client.get("body", "payload");
            CompletableFuture<String> leader = call.toFuture();
            assertThat(bodyReceived.await(1, TimeUnit.SECONDS)).isTrue();
            CompletableFuture<String> waiter = call.toFuture();
            assertThat(bodySubscriptions).hasValue(1);
            assertThat(serverDispatches).hasValue(1);
            assertThat(receivedBody).hasValue("payload");

            responseBody.tryEmitValue("accepted").orThrow();
            assertThat(leader.get(1, TimeUnit.SECONDS)).isEqualTo("accepted");
            assertThat(waiter.get(1, TimeUnit.SECONDS)).isEqualTo("accepted");
            assertThat(bodySubscriptions).hasValue(1);
            assertThat(serverDispatches).hasValue(1);
        }
        finally {
            server.disposeNow();
        }
    }

    @Test
    void cacheAuthorizationSeesUpstreamRequestHeadersOnMissesAndHits() {
        ReactiveHttpClientProperties.ClientConfig config = config();
        config.setAuthProvider("cache-auth");
        config.getCache().getPolicies().get("local")
                .setVaryByParameters(List.of("principal", "tenant"));
        config.getCache().getPolicies().get("local")
                .setVaryByHeaders(List.of("Accept-Language", "Idempotency-Key"));
        AtomicInteger authCalls = new AtomicInteger();
        AtomicInteger downstreamFilterCalls = new AtomicInteger();
        AtomicInteger dispatches = new AtomicInteger();
        List<HttpHeaders> authHeaders = new ArrayList<>();
        io.github.huynhngochuyhoang.httpstarter.auth.AuthProvider authProvider = request -> {
            authCalls.incrementAndGet();
            authHeaders.add(HttpHeaders.readOnlyHttpHeaders(request.request().headers()));
            return Mono.just(AuthContext.builder().header("Authorization", "Bearer signed").build());
        };
        WebClient webClient = WebClient.builder()
                .baseUrl("http://cache.test")
                .defaultRequest(request -> request.header("X-Boot-Default", "default-value"))
                .filter((request, next) -> Mono.deferContextual(context -> next.exchange(
                        ClientRequest.from(request)
                                .header("X-Trace-Id", context.<String>get("trace-id"))
                                .build())))
                .filter(CorrelationIdWebFilter.exchangeFilter())
                .filter(new OutboundAuthFilter("cache-client", authProvider))
                .filter((request, next) -> {
                    downstreamFilterCalls.incrementAndGet();
                    return next.exchange(request);
                })
                .exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.OK)
                        .header(HttpHeaders.CONTENT_TYPE, "text/plain")
                        .body("authorized-" + dispatches.incrementAndGet())
                        .build()))
                .build();

        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.refresh();
            CacheClient client = cacheClient(
                    webClient,
                    config,
                    context,
                    LocalResponseCacheManager.testing(System::nanoTime),
                    new NoopResilienceOperatorApplier(),
                    authProvider);
            Mono<String> call = client.get("42", "principal-a", "tenant-a", "en-US")
                    .contextWrite(reactorContext -> RequestContext.withCorrelationId(
                            reactorContext.put("trace-id", "trace-1"), "correlation-1"));

            assertThat(call.block()).isEqualTo("authorized-1");
            assertThat(call.block()).isEqualTo("authorized-1");
        }

        assertThat(authCalls).hasValue(2);
        assertThat(dispatches).hasValue(1);
        assertThat(downstreamFilterCalls).hasValue(1);
        assertThat(authHeaders).allSatisfy(headers -> {
            assertThat(headers.getFirst("X-Boot-Default")).isEqualTo("default-value");
            assertThat(headers.getFirst("X-Trace-Id")).isEqualTo("trace-1");
            assertThat(headers.getFirst(CorrelationIdWebFilter.CORRELATION_ID_HEADER))
                    .isEqualTo("correlation-1");
        });
    }

    @Test
    void cacheHitsRejectInvalidPreResolvedAuthHeaders() {
        ReactiveHttpClientProperties.ClientConfig config = config();
        config.setAuthProvider("cache-auth");
        config.getCache().getPolicies().get("local")
                .setVaryByParameters(List.of("principal", "tenant"));
        config.getCache().getPolicies().get("local")
                .setVaryByHeaders(List.of("Accept-Language", "Idempotency-Key"));
        AtomicInteger dispatches = new AtomicInteger();
        AtomicReference<AuthContext> currentAuth = new AtomicReference<>(AuthContext.empty());
        io.github.huynhngochuyhoang.httpstarter.auth.AuthProvider authProvider = request ->
                Mono.just(currentAuth.get());
        WebClient webClient = WebClient.builder()
                .baseUrl("http://cache.test")
                .filter(new OutboundAuthFilter("cache-client", authProvider))
                .exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.OK)
                        .header(HttpHeaders.CONTENT_TYPE, "text/plain")
                        .body("dispatch-" + dispatches.incrementAndGet())
                        .build()))
                .build();

        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.refresh();
            CacheClient client = cacheClient(
                    webClient,
                    config,
                    context,
                    LocalResponseCacheManager.testing(System::nanoTime),
                    new NoopResilienceOperatorApplier(),
                    authProvider);

            assertThat(client.get("42", "principal-a", "tenant-a", "en-US").block())
                    .isEqualTo("dispatch-1");

            currentAuth.set(AuthContext.builder()
                    .header("Authorization", "Bearer token\r\nX-Evil: 1")
                    .build());
            Throwable invalidValue = catchThrowable(() ->
                    client.get("42", "principal-a", "tenant-a", "en-US").block());
            assertThat(findCause(invalidValue, IllegalArgumentException.class))
                    .hasMessageContaining("Invalid auth header value");

            currentAuth.set(AuthContext.builder().header("Bad Header", "value").build());
            Throwable invalidName = catchThrowable(() ->
                    client.get("42", "principal-a", "tenant-a", "en-US").block());
            assertThat(findCause(invalidName, IllegalArgumentException.class))
                    .hasMessageContaining("Invalid auth header name");
        }

        assertThat(dispatches).hasValue(1);
    }

    @Test
    void publicCreationRejectsAuthenticatedCachingWithoutAuthorizationInputs() {
        ReactiveHttpClientProperties.ClientConfig config = config();
        config.setAuthProvider("cache-auth");
        WebClient webClient = WebClient.builder()
                .baseUrl("http://cache.test")
                .filter(new OutboundAuthFilter("cache-client", request -> Mono.just(AuthContext.empty())))
                .exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.OK).body("ok").build()))
                .build();

        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.refresh();

            assertThatThrownBy(() -> ReactiveClientInvocationHandler.create(
                    webClient,
                    new MethodMetadataCache(),
                    new RequestArgumentResolver(),
                    new DefaultErrorDecoder(),
                    config,
                    "cache-client",
                    CacheClient.class,
                    context,
                    new NoopResilienceOperatorApplier(),
                    TestJsonCodecs.jsonCodec(),
                    new ReactiveHttpClientProperties.ObservabilityConfig()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Authenticated response caching")
                    .hasMessageContaining("AuthProvider and resolved base URL");
        }
    }

    @Test
    void singleFlightKeepsAuthReplayAndRetryInsideOneLeaderLoad() throws Exception {
        ReactiveHttpClientProperties.ClientConfig config = config();
        config.setAuthProvider("cache-auth");
        config.getCache().getPolicies().get("local").setSingleFlight(true);
        config.getCache().getPolicies().get("local")
                .setVaryByParameters(List.of("principal", "tenant"));
        config.getCache().getPolicies().get("local")
                .setVaryByHeaders(List.of("Accept-Language", "Idempotency-Key"));
        config.getResilience().setEnabled(true);
        config.getResilience().setRetry("cache-retry");
        AtomicInteger authCalls = new AtomicInteger();
        AtomicInteger invalidations = new AtomicInteger();
        AtomicInteger staleDispatches = new AtomicInteger();
        AtomicInteger freshDispatches = new AtomicInteger();
        Sinks.One<ClientResponse> finalResponse = Sinks.one();
        List<HttpClientObserverEvent> observed = new CopyOnWriteArrayList<>();
        InvalidatableAuthProvider authProvider = new InvalidatableAuthProvider() {
            @Override
            public Mono<AuthContext> getAuth(io.github.huynhngochuyhoang.httpstarter.auth.AuthRequest request) {
                String credential = authCalls.incrementAndGet() == 1 ? "stale" : "fresh";
                return Mono.just(AuthContext.builder()
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + credential)
                        .build());
            }

            @Override
            public Mono<Void> invalidate() {
                invalidations.incrementAndGet();
                return Mono.empty();
            }
        };
        WebClient webClient = WebClient.builder()
                .baseUrl("http://cache.test")
                .filter(new OutboundAuthFilter("cache-client", authProvider))
                .filter(ReactiveClientInvocationHandler.finalRequestObservationFilter())
                .exchangeFunction(request -> {
                    String authorization = request.headers().getFirst(HttpHeaders.AUTHORIZATION);
                    if ("Bearer stale".equals(authorization)) {
                        staleDispatches.incrementAndGet();
                        return Mono.just(ClientResponse.create(HttpStatus.UNAUTHORIZED).build());
                    }
                    int dispatch = freshDispatches.incrementAndGet();
                    if (dispatch == 1) {
                        return Mono.just(ClientResponse.create(HttpStatus.SERVICE_UNAVAILABLE)
                                .body("retry")
                                .build());
                    }
                    return finalResponse.asMono();
                })
                .build();

        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getBeanFactory().registerSingleton("cacheObserver",
                    (io.github.huynhngochuyhoang.httpstarter.observability.HttpClientObserver) observed::add);
            context.refresh();
            RetryOnceApplier retryApplier = new RetryOnceApplier();
            CacheClient client = cacheClient(
                    webClient,
                    config,
                    context,
                    LocalResponseCacheManager.testing(System::nanoTime),
                    retryApplier,
                    authProvider);

            Mono<String> call = client.get("42", "principal-a", "tenant-a", "en-US");
            CompletableFuture<String> leader = call.toFuture();
            CompletableFuture<String> waiter = call.toFuture();
            Disposable cancelledWaiter = call.subscribe();
            cancelledWaiter.dispose();

            assertThat(retryApplier.applications).hasValue(1);
            assertThat(retryApplier.subscriptions).hasValue(1);
            assertThat(authCalls).hasValue(5);
            assertThat(invalidations).hasValue(1);
            assertThat(staleDispatches).hasValue(1);
            assertThat(freshDispatches).hasValue(2);

            finalResponse.tryEmitValue(ClientResponse.create(HttpStatus.OK)
                    .header(HttpHeaders.CONTENT_TYPE, "text/plain")
                    .body("authorized")
                    .build()).orThrow();
            assertThat(leader.get(1, TimeUnit.SECONDS)).isEqualTo("authorized");
            assertThat(waiter.get(1, TimeUnit.SECONDS)).isEqualTo("authorized");

            assertThat(observed).hasSize(3);
            assertThat(observed).extracting(HttpClientObserverEvent::getAttemptCount)
                    .containsExactlyInAnyOrder(0, 0, 2);
            HttpClientObserverEvent leaderEvent = observed.stream()
                    .filter(event -> event.getAttemptCount() == 2)
                    .findFirst()
                    .orElseThrow();
            HttpClientObserverEvent waiterEvent = observed.stream()
                    .filter(event -> event.getAttemptCount() == 0)
                    .findFirst()
                    .orElseThrow();
            assertThat(leaderEvent.getStatusCode()).isEqualTo(200);
            assertThat(leaderEvent.getRequestUrl()).isNotNull();
            assertThat(waiterEvent.getStatusCode()).isNull();
            assertThat(waiterEvent.getRequestUrl()).isNull();
            assertThat(observed).anySatisfy(event -> {
                assertThat(event.getAttemptCount()).isZero();
                assertThat(event.getError()).isInstanceOf(java.util.concurrent.CancellationException.class);
            });
        }
    }

    private CacheClient cacheClient(
            WebClient webClient,
            ReactiveHttpClientProperties.ClientConfig config,
            AnnotationConfigApplicationContext context,
            LocalResponseCacheManager manager,
            ResilienceOperatorApplier resilienceOperatorApplier,
            io.github.huynhngochuyhoang.httpstarter.auth.AuthProvider authProvider) {
        ReactiveClientInvocationHandler handler = new ReactiveClientInvocationHandler(
                webClient,
                new MethodMetadataCache(),
                new RequestArgumentResolver(),
                new DefaultErrorDecoder(),
                config,
                "cache-client",
                CacheClient.class,
                context,
                resilienceOperatorApplier,
                TestJsonCodecs.jsonCodec(),
                new ReactiveHttpClientProperties.ObservabilityConfig(),
                manager,
                authProvider,
                "http://cache.test");
        return (CacheClient) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{CacheClient.class}, handler);
    }

    private TerminalCacheClient terminalCacheClient(
            WebClient webClient,
            ReactiveHttpClientProperties.ClientConfig config,
            AnnotationConfigApplicationContext context,
            LocalResponseCacheManager manager,
            ResilienceOperatorApplier resilienceOperatorApplier,
            io.github.huynhngochuyhoang.httpstarter.auth.AuthProvider authProvider) {
        return terminalCacheClient(webClient, config, context, manager, resilienceOperatorApplier,
                authProvider, new ReactiveHttpClientProperties.ObservabilityConfig());
    }

    private TerminalCacheClient terminalCacheClient(
            WebClient webClient,
            ReactiveHttpClientProperties.ClientConfig config,
            AnnotationConfigApplicationContext context,
            LocalResponseCacheManager manager,
            ResilienceOperatorApplier resilienceOperatorApplier,
            io.github.huynhngochuyhoang.httpstarter.auth.AuthProvider authProvider,
            ReactiveHttpClientProperties.ObservabilityConfig observability) {
        ReactiveClientInvocationHandler handler = new ReactiveClientInvocationHandler(
                webClient,
                new MethodMetadataCache(),
                new RequestArgumentResolver(),
                new DefaultErrorDecoder(),
                config,
                "cache-client",
                TerminalCacheClient.class,
                context,
                resilienceOperatorApplier,
                TestJsonCodecs.jsonCodec(),
                observability,
                manager,
                authProvider,
                "http://cache.test");
        return (TerminalCacheClient) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{TerminalCacheClient.class}, handler);
    }

    private ReadWriteCacheClient readWriteCacheClient(
            WebClient webClient,
            ReactiveHttpClientProperties.ClientConfig config,
            AnnotationConfigApplicationContext context,
            LocalResponseCacheManager manager) {
        ReactiveClientInvocationHandler handler = new ReactiveClientInvocationHandler(
                webClient,
                new MethodMetadataCache(),
                new RequestArgumentResolver(),
                new DefaultErrorDecoder(),
                config,
                "read-write-cache-client",
                ReadWriteCacheClient.class,
                context,
                new NoopResilienceOperatorApplier(),
                TestJsonCodecs.jsonCodec(),
                new ReactiveHttpClientProperties.ObservabilityConfig(),
                manager);
        return (ReadWriteCacheClient) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{ReadWriteCacheClient.class}, handler);
    }

    private ResponseCacheClient responseCacheClient(
            WebClient webClient,
            ReactiveHttpClientProperties.ClientConfig config,
            AnnotationConfigApplicationContext context,
            LocalResponseCacheManager manager) {
        ReactiveClientInvocationHandler handler = new ReactiveClientInvocationHandler(
                webClient,
                new MethodMetadataCache(),
                new RequestArgumentResolver(),
                new DefaultErrorDecoder(),
                config,
                "response-cache-client",
                ResponseCacheClient.class,
                context,
                new NoopResilienceOperatorApplier(),
                TestJsonCodecs.jsonCodec(),
                new ReactiveHttpClientProperties.ObservabilityConfig(),
                manager);
        return (ResponseCacheClient) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{ResponseCacheClient.class}, handler);
    }

    private BodyCacheClient bodyCacheClient(
            WebClient webClient,
            ReactiveHttpClientProperties.ClientConfig config,
            AnnotationConfigApplicationContext context,
            LocalResponseCacheManager manager) {
        ReactiveClientInvocationHandler handler = new ReactiveClientInvocationHandler(
                webClient,
                new MethodMetadataCache(),
                new RequestArgumentResolver(),
                new DefaultErrorDecoder(),
                config,
                "body-cache-client",
                BodyCacheClient.class,
                context,
                new NoopResilienceOperatorApplier(),
                TestJsonCodecs.jsonCodec(),
                new ReactiveHttpClientProperties.ObservabilityConfig(),
                manager);
        return (BodyCacheClient) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{BodyCacheClient.class}, handler);
    }

    private static boolean hasCause(Throwable error, Class<? extends Throwable> expectedType) {
        Throwable current = error;
        while (current != null) {
            if (expectedType.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static <T extends Throwable> T findCause(Throwable error, Class<T> expectedType) {
        Throwable current = error;
        while (current != null) {
            if (expectedType.isInstance(current)) {
                return expectedType.cast(current);
            }
            current = current.getCause() != current ? current.getCause() : null;
        }
        return null;
    }

    private static void awaitThreadState(Thread thread, Thread.State expectedState) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
        while (thread.getState() != expectedState && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        assertThat(thread.getState()).isEqualTo(expectedState);
    }

    private static void assertTimeoutDirection(boolean firstCallerTimesOut) {
        VirtualTimeScheduler scheduler = VirtualTimeScheduler.create();
        try {
            LocalResponseCacheManager manager = LocalResponseCacheManager.testing(System::nanoTime);
            EffectiveCachePolicy.Selection selection = selection("timeout-" + firstCallerTimesOut, 1_000, 10, true);
            Sinks.One<String> load = Sinks.one();
            AtomicInteger subscriptions = new AtomicInteger();
            AtomicInteger cancellations = new AtomicInteger();
            Mono<String> call = cached(manager, selection, key("shared"), () -> Mono.defer(() -> {
                subscriptions.incrementAndGet();
                return load.asMono().doOnCancel(cancellations::incrementAndGet);
            }));
            AtomicReference<String> firstValue = new AtomicReference<>();
            AtomicReference<String> waiterValue = new AtomicReference<>();
            AtomicReference<Throwable> firstError = new AtomicReference<>();
            AtomicReference<Throwable> waiterError = new AtomicReference<>();

            call.timeout(Duration.ofMillis(firstCallerTimesOut ? 100 : 200), scheduler)
                    .subscribe(firstValue::set, firstError::set);
            scheduler.advanceTimeBy(Duration.ofMillis(50));
            call.timeout(Duration.ofMillis(firstCallerTimesOut ? 100 : 50), scheduler)
                    .subscribe(waiterValue::set, waiterError::set);
            scheduler.advanceTimeBy(Duration.ofMillis(50));

            assertThat(subscriptions).hasValue(1);
            if (firstCallerTimesOut) {
                assertThat(firstError.get()).isInstanceOf(TimeoutException.class);
                assertThat(waiterError.get()).isNull();
            }
            else {
                assertThat(firstError.get()).isNull();
                assertThat(waiterError.get()).isInstanceOf(TimeoutException.class);
            }
            assertThat(cancellations).hasValue(0);

            load.tryEmitValue("shared-value").orThrow();
            if (firstCallerTimesOut) {
                assertThat(firstValue.get()).isNull();
                assertThat(waiterValue.get()).isEqualTo("shared-value");
            }
            else {
                assertThat(firstValue.get()).isEqualTo("shared-value");
                assertThat(waiterValue.get()).isNull();
            }
            assertThat(cancellations).hasValue(0);
        }
        finally {
            scheduler.dispose();
        }
    }

    @Test
    void refreshOnAccessReturnsStaleAndStartsOnlyOneRefresh() {
        AtomicLong ticker = new AtomicLong();
        VirtualTimeScheduler scheduler = VirtualTimeScheduler.create();
        LocalResponseCacheManager manager = LocalResponseCacheManager.testing(ticker::get, scheduler);
        EffectiveCachePolicy.Selection selection = refreshSelection("refresh", 1_000, 10, 100, 500);
        CacheKeyContract.OpaqueKey key = key("refresh");
        Sinks.One<String> refresh = Sinks.one();
        AtomicInteger subscriptions = new AtomicInteger();
        Mono<String> call = cached(manager, selection, key, () -> Mono.defer(() -> {
            int subscription = subscriptions.incrementAndGet();
            return subscription == 1 ? Mono.just("initial") : refresh.asMono();
        }));

        assertThat(call.block()).isEqualTo("initial");
        ticker.addAndGet(Duration.ofMillis(100).toNanos());

        assertThat(call.block()).isEqualTo("initial");
        assertThat(call.block()).isEqualTo("initial");
        assertThat(subscriptions).hasValue(2);

        refresh.tryEmitValue("refreshed").orThrow();
        assertThat(call.block()).isEqualTo("refreshed");
        assertThat(subscriptions).hasValue(2);
        manager.close();
        scheduler.dispose();
    }

    @Test
    void refreshFailurePreservesTheValueOnlyUntilHardExpiry() {
        AtomicLong ticker = new AtomicLong();
        LocalResponseCacheManager manager = LocalResponseCacheManager.testing(ticker::get);
        EffectiveCachePolicy.Selection selection = refreshSelection("failure", 200, 10, 50, 100);
        AtomicInteger subscriptions = new AtomicInteger();
        Mono<String> call = cached(manager, selection, key("failure"), () -> Mono.defer(() -> {
            int subscription = subscriptions.incrementAndGet();
            if (subscription == 1) {
                return Mono.just("initial");
            }
            if (subscription == 2) {
                return Mono.error(new IllegalStateException("refresh failed"));
            }
            return Mono.just("after-expiry");
        }));

        assertThat(call.block()).isEqualTo("initial");
        ticker.addAndGet(Duration.ofMillis(50).toNanos());
        assertThat(call.block()).isEqualTo("initial");
        assertThat(subscriptions).hasValue(2);

        ticker.addAndGet(Duration.ofMillis(150).toNanos());
        assertThat(call.block()).isEqualTo("after-expiry");
        assertThat(subscriptions).hasValue(3);
        manager.close();
    }

    @Test
    void refreshTimeoutAndHardExpiryCancelWorkAndRejectLateResults() {
        AtomicLong ticker = new AtomicLong();
        VirtualTimeScheduler scheduler = VirtualTimeScheduler.create();
        LocalResponseCacheManager manager = LocalResponseCacheManager.testing(ticker::get, scheduler);
        EffectiveCachePolicy.Selection selection = refreshSelection("deadline", 200, 10, 50, 75);
        Sinks.One<String> firstRefresh = Sinks.one();
        Sinks.One<String> secondRefresh = Sinks.one();
        AtomicInteger subscriptions = new AtomicInteger();
        AtomicInteger cancellations = new AtomicInteger();
        Mono<String> call = cached(manager, selection, key("deadline"), () -> Mono.defer(() -> {
            int subscription = subscriptions.incrementAndGet();
            if (subscription == 1) {
                return Mono.just("initial");
            }
            if (subscription == 4) {
                return Mono.just("after-expiry");
            }
            return (subscription == 2 ? firstRefresh : secondRefresh).asMono()
                    .doOnCancel(cancellations::incrementAndGet);
        }));

        assertThat(call.block()).isEqualTo("initial");
        ticker.addAndGet(Duration.ofMillis(50).toNanos());
        assertThat(call.block()).isEqualTo("initial");
        scheduler.advanceTimeBy(Duration.ofMillis(75));
        assertThat(cancellations).hasValue(1);

        assertThat(call.block()).isEqualTo("initial");
        assertThat(subscriptions).hasValue(3);
        ticker.addAndGet(Duration.ofMillis(150).toNanos());
        assertThat(call.block()).isEqualTo("after-expiry");
        assertThat(cancellations).hasValue(2);
        secondRefresh.tryEmitValue("late-refresh");
        assertThat(call.block()).isEqualTo("after-expiry");

        manager.close();
        scheduler.dispose();
    }

    @Test
    void evictionCancelsRefreshAndRefreshUsesTheTriggeringContext() {
        AtomicLong ticker = new AtomicLong();
        LocalResponseCacheManager manager = LocalResponseCacheManager.testing(ticker::get);
        EffectiveCachePolicy.Selection selection = refreshSelection("eviction-refresh", 1_000, 1, 50, 500);
        Sinks.One<String> refresh = Sinks.one();
        AtomicReference<String> refreshTenant = new AtomicReference<>();
        AtomicInteger cancellations = new AtomicInteger();
        AtomicInteger subscriptions = new AtomicInteger();
        Mono<String> firstKey = cached(manager, selection, key("one"), () -> Mono.deferContextual(context -> {
            int subscription = subscriptions.incrementAndGet();
            if (subscription == 1) {
                return Mono.just("one");
            }
            refreshTenant.set(context.get("tenant"));
            return refresh.asMono().doOnCancel(cancellations::incrementAndGet);
        }));

        assertThat(firstKey.contextWrite(context -> context.put("tenant", "initial")).block()).isEqualTo("one");
        ticker.addAndGet(Duration.ofMillis(50).toNanos());
        assertThat(firstKey.contextWrite(context -> context.put("tenant", "live-trigger")).block()).isEqualTo("one");
        assertThat(refreshTenant).hasValue("live-trigger");

        assertThat(cached(manager, selection, key("two"), () -> Mono.just("two")).block()).isEqualTo("two");
        assertThat(cancellations).hasValue(1);
        refresh.tryEmitValue("late");
        assertThat(cached(manager, selection, key("two"), () -> Mono.just("unexpected")).block())
                .isEqualTo("two");
        manager.close();
    }

    @Test
    void hardExpiryDeadlineCancelsRefreshWithoutAnotherCacheAccess() {
        VirtualTimeScheduler scheduler = VirtualTimeScheduler.create();
        LocalResponseCacheManager manager = LocalResponseCacheManager.testing(
                () -> scheduler.now(TimeUnit.NANOSECONDS), scheduler);
        EffectiveCachePolicy.Selection selection = refreshSelection("hard-deadline", 200, 10, 50, 500);
        AtomicInteger subscriptions = new AtomicInteger();
        AtomicInteger cancellations = new AtomicInteger();
        Mono<String> call = cached(manager, selection, key("hard-deadline"), () -> Mono.defer(() -> {
            if (subscriptions.incrementAndGet() == 1) {
                return Mono.just("initial");
            }
            return Mono.<String>never().doOnCancel(cancellations::incrementAndGet);
        }));

        assertThat(call.block()).isEqualTo("initial");
        scheduler.advanceTimeBy(Duration.ofMillis(50));
        assertThat(call.block()).isEqualTo("initial");
        scheduler.advanceTimeBy(Duration.ofMillis(149));
        assertThat(cancellations).hasValue(0);
        scheduler.advanceTimeBy(Duration.ofMillis(1));
        assertThat(cancellations).hasValue(1);

        manager.close();
        scheduler.dispose();
    }

    @Test
    void factoryShutdownCancelsAnActiveRefreshAndClearsOwnedState() {
        AtomicLong ticker = new AtomicLong();
        LocalResponseCacheManager manager = LocalResponseCacheManager.testing(ticker::get);
        EffectiveCachePolicy.Selection selection = refreshSelection("shutdown-refresh", 1_000, 10, 50, 500);
        AtomicInteger subscriptions = new AtomicInteger();
        AtomicInteger cancellations = new AtomicInteger();
        Mono<String> call = cached(manager, selection, key("shutdown-refresh"), () -> Mono.defer(() -> {
            if (subscriptions.incrementAndGet() == 1) {
                return Mono.just("initial");
            }
            return Mono.<String>never().doOnCancel(cancellations::incrementAndGet);
        }));

        assertThat(call.block()).isEqualTo("initial");
        ticker.addAndGet(Duration.ofMillis(50).toNanos());
        assertThat(call.block()).isEqualTo("initial");
        assertThat(subscriptions).hasValue(2);

        manager.close();

        assertThat(cancellations).hasValue(1);
        assertThat(manager.snapshot()).isEqualTo(
                new LocalResponseCacheManager.Snapshot(0, 0, 0, true));
    }


    @Test
    void failedRefreshAndSubsequentHitDoNotInheritLoadTerminalEvidence() {
        AtomicLong ticker = new AtomicLong();
        ReactiveHttpClientProperties.ClientConfig config = singleFlightConfig(0);
        ReactiveHttpClientProperties.CachePolicyConfig policy =
                config.getCache().getPolicies().get("local");
        policy.setRefreshAfterMs(50L);
        policy.setRefreshTimeoutMs(500L);
        AtomicInteger dispatches = new AtomicInteger();
        List<HttpClientObserverEvent> observed = new CopyOnWriteArrayList<>();
        List<HttpExchangeLogContext> exchangeLogs = new CopyOnWriteArrayList<>();
        List<ReactiveHttpClientLifecycleContext> lifecycleTerminals = new CopyOnWriteArrayList<>();
        WebClient webClient = WebClient.builder()
                .baseUrl("http://cache.test")
                .filter(ReactiveClientInvocationHandler.finalRequestObservationFilter())
                .exchangeFunction(request -> {
                    if (dispatches.incrementAndGet() == 1) {
                        return Mono.just(ClientResponse.create(HttpStatus.PARTIAL_CONTENT)
                                .header(HttpHeaders.CONTENT_TYPE, "text/plain")
                                .header(HttpHeaders.CONTENT_LENGTH, "6")
                                .header("X-Load-Evidence", "first")
                                .body("cached")
                                .build());
                    }
                    return Mono.just(ClientResponse.create(HttpStatus.SERVICE_UNAVAILABLE)
                            .header("X-Refresh-Evidence", "failed")
                            .body("refresh failed")
                            .build());
                })
                .build();

        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getBeanFactory().registerSingleton("cacheObserver",
                    (io.github.huynhngochuyhoang.httpstarter.observability.HttpClientObserver) observed::add);
            context.getBeanFactory().registerSingleton(
                    "cacheCompositionLogger", new CacheCompositionLogger(exchangeLogs));
            context.getBeanFactory().registerSingleton("cacheLifecycleHook", new ReactiveHttpClientLifecycleHook() {
                @Override
                public void onSuccess(ReactiveHttpClientLifecycleContext lifecycleContext) {
                    lifecycleTerminals.add(lifecycleContext);
                }
            });
            context.refresh();
            TerminalCacheClient client = terminalCacheClient(
                    webClient,
                    config,
                    context,
                    LocalResponseCacheManager.testing(ticker::get),
                    new NoopResilienceOperatorApplier(),
                    null);

            String loaded = client.get("terminal", "principal", "tenant", "en-US").block();
            ticker.addAndGet(Duration.ofMillis(50).toNanos());
            String stale = client.get("terminal", "principal", "tenant", "en-US").block();

            assertThat(stale).isSameAs(loaded);
            assertThat(dispatches).hasValue(2);
            assertThat(observed).hasSize(2);
            assertThat(observed).extracting(HttpClientObserverEvent::getCacheOutcome).containsOnlyNulls();
            assertThat(observed.get(0).getAttemptCount()).isEqualTo(1);
            assertThat(observed.get(0).getStatusCode()).isEqualTo(206);
            assertThat(observed.get(0).getRequestUrl()).endsWith("/catalog/terminal");
            assertThat(observed.get(0).getResponseBytes()).isEqualTo(6);
            assertIsolatedCacheTerminal(observed.get(1));
            assertThat(observed.get(1).getError()).isNull();
            assertThat(exchangeLogs).hasSize(2);
            assertThat(exchangeLogs).extracting(HttpExchangeLogContext::cacheOutcome).containsOnlyNulls();
            assertThat(exchangeLogs.get(0).responseHeaders()).containsKey("X-Load-Evidence");
            assertThat(exchangeLogs.get(1).responseHeaders()).isEmpty();
            assertThat(exchangeLogs.get(1).responseStatus()).isNull();
            assertThat(exchangeLogs.get(1).requestUrl()).isNull();
            assertThat(exchangeLogs.get(1).error()).isNull();
            assertThat(exchangeLogs.get(1).subscriptionAttemptCount()).isZero();
            assertThat(lifecycleTerminals).hasSize(2);
            assertThat(lifecycleTerminals)
                    .extracting(ReactiveHttpClientLifecycleContext::cacheOutcome)
                    .containsOnlyNulls();
        }
    }

    @Test
    void cacheOutcomeIsAlignedAcrossCallerTerminalSurfacesAndHiddenRefreshStaysDetached() throws Exception {
        AtomicLong ticker = new AtomicLong();
        ReactiveHttpClientProperties.ClientConfig config = singleFlightConfig(0);
        ReactiveHttpClientProperties.CachePolicyConfig policy = config.getCache().getPolicies().get("local");
        policy.setRefreshAfterMs(50L);
        policy.setRefreshTimeoutMs(500L);
        ReactiveHttpClientProperties.ObservabilityConfig observability =
                new ReactiveHttpClientProperties.ObservabilityConfig();
        observability.getCache().setEnabled(true);
        LocalResponseCacheManager manager = LocalResponseCacheManager.testing(
                ticker::get,
                Schedulers.parallel(),
                LocalResponseCacheMetrics.enabled(new SimpleMeterRegistry(), "cache-client"),
                "cache-client");
        Sinks.One<ClientResponse> initialResponse = Sinks.one();
        AtomicInteger dispatches = new AtomicInteger();
        List<HttpClientObserverEvent> observed = new CopyOnWriteArrayList<>();
        List<HttpExchangeLogContext> exchangeLogs = new CopyOnWriteArrayList<>();
        List<ReactiveHttpClientLifecycleContext> lifecycleTerminals = new CopyOnWriteArrayList<>();
        WebClient webClient = WebClient.builder()
                .baseUrl("http://cache.test")
                .filter(ReactiveClientInvocationHandler.finalRequestObservationFilter())
                .exchangeFunction(request -> dispatches.incrementAndGet() == 1
                        ? initialResponse.asMono()
                        : Mono.just(ok("refreshed")))
                .build();

        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getBeanFactory().registerSingleton("cacheObserver",
                    (io.github.huynhngochuyhoang.httpstarter.observability.HttpClientObserver) observed::add);
            context.getBeanFactory().registerSingleton(
                    "cacheCompositionLogger", new CacheCompositionLogger(exchangeLogs));
            context.getBeanFactory().registerSingleton("cacheLifecycleHook", new ReactiveHttpClientLifecycleHook() {
                @Override
                public void onSuccess(ReactiveHttpClientLifecycleContext lifecycleContext) {
                    lifecycleTerminals.add(lifecycleContext);
                }
            });
            context.refresh();
            TerminalCacheClient client = terminalCacheClient(
                    webClient, config, context, manager, new NoopResilienceOperatorApplier(), null, observability);

            Mono<String> call = client.get("outcomes", "principal", "tenant", "en-US");
            CompletableFuture<String> leader = call.toFuture();
            CompletableFuture<String> waiter = call.toFuture();
            initialResponse.tryEmitValue(ok("initial")).orThrow();
            assertThat(leader.get(1, TimeUnit.SECONDS)).isEqualTo("initial");
            assertThat(waiter.get(1, TimeUnit.SECONDS)).isEqualTo("initial");
            assertThat(call.block()).isEqualTo("initial");

            ticker.addAndGet(Duration.ofMillis(50).toNanos());
            assertThat(call.block()).isEqualTo("initial");

            assertThat(dispatches).hasValue(2);
            assertThat(observed).hasSize(4);
            assertThat(exchangeLogs).hasSize(4);
            assertThat(lifecycleTerminals).hasSize(4);
            assertThat(observed).extracting(HttpClientObserverEvent::getCacheOutcome)
                    .containsExactlyInAnyOrder(
                            HttpClientCacheOutcome.MISS_LOADER,
                            HttpClientCacheOutcome.COALESCED_WAITER,
                            HttpClientCacheOutcome.FRESH_HIT,
                            HttpClientCacheOutcome.STALE_HIT);
            HttpClientObserverEvent loader = observed.stream()
                    .filter(event -> event.getCacheOutcome() == HttpClientCacheOutcome.MISS_LOADER)
                    .findFirst().orElseThrow();
            assertThat(loader.getAttemptCount()).isEqualTo(1);
            assertThat(loader.getStatusCode()).isEqualTo(200);
            assertThat(loader.getRequestUrl()).endsWith("/catalog/outcomes");
            observed.stream()
                    .filter(event -> event.getCacheOutcome() != HttpClientCacheOutcome.MISS_LOADER)
                    .forEach(BoundedLocalResponseCacheContractTest::assertIsolatedCacheTerminal);
            assertThat(exchangeLogs).extracting(HttpExchangeLogContext::cacheOutcome)
                    .containsExactlyInAnyOrder(
                            HttpClientCacheOutcome.MISS_LOADER,
                            HttpClientCacheOutcome.COALESCED_WAITER,
                            HttpClientCacheOutcome.FRESH_HIT,
                            HttpClientCacheOutcome.STALE_HIT);
            assertThat(lifecycleTerminals).extracting(ReactiveHttpClientLifecycleContext::cacheOutcome)
                    .containsExactlyInAnyOrder(
                            HttpClientCacheOutcome.MISS_LOADER,
                            HttpClientCacheOutcome.COALESCED_WAITER,
                            HttpClientCacheOutcome.FRESH_HIT,
                            HttpClientCacheOutcome.STALE_HIT);
        }
        finally {
            manager.close();
        }
    }


    @Test
    void authFailureCannotReturnAHitAndDoesNotLeakIntoTheLaterHit() {
        ReactiveHttpClientProperties.ClientConfig config = singleFlightConfig(0);
        config.setAuthProvider("cache-auth");
        AtomicBoolean reject = new AtomicBoolean();
        AtomicInteger authCalls = new AtomicInteger();
        AtomicInteger dispatches = new AtomicInteger();
        List<HttpClientObserverEvent> observed = new CopyOnWriteArrayList<>();
        io.github.huynhngochuyhoang.httpstarter.auth.AuthProvider authProvider = request -> {
            authCalls.incrementAndGet();
            return reject.get()
                    ? Mono.error(new IllegalStateException("caller rejected"))
                    : Mono.just(AuthContext.empty());
        };
        WebClient webClient = WebClient.builder()
                .baseUrl("http://cache.test")
                .filter(new OutboundAuthFilter("cache-client", authProvider))
                .filter(ReactiveClientInvocationHandler.finalRequestObservationFilter())
                .exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.OK)
                        .header(HttpHeaders.CONTENT_TYPE, "text/plain")
                        .body("authorized-" + dispatches.incrementAndGet())
                        .build()))
                .build();

        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getBeanFactory().registerSingleton("cacheObserver",
                    (io.github.huynhngochuyhoang.httpstarter.observability.HttpClientObserver) observed::add);
            context.refresh();
            CacheClient client = cacheClient(
                    webClient,
                    config,
                    context,
                    LocalResponseCacheManager.testing(System::nanoTime),
                    new NoopResilienceOperatorApplier(),
                    authProvider);

            assertThat(client.get("auth", "principal", "tenant", "en-US").block())
                    .isEqualTo("authorized-1");
            reject.set(true);
            assertThatThrownBy(() -> client.get("auth", "principal", "tenant", "en-US").block())
                    .hasRootCauseMessage("caller rejected");
            reject.set(false);
            assertThat(client.get("auth", "principal", "tenant", "en-US").block())
                    .isEqualTo("authorized-1");

            assertThat(authCalls).hasValue(3);
            assertThat(dispatches).hasValue(1);
            assertThat(observed).hasSize(3);
            assertThat(observed.get(1).getError()).isNotNull();
            assertIsolatedCacheTerminal(observed.get(1));
            assertIsolatedCacheTerminal(observed.get(2));
            assertThat(observed.get(2).getError()).isNull();
        }
    }


    @Test
    void openCircuitAndRetryExhaustionNeverPopulateOrLeakIntoLaterHits() {
        ReactiveHttpClientProperties.ClientConfig circuitConfig = singleFlightConfig(0);
        circuitConfig.getResilience().setEnabled(true);
        circuitConfig.getResilience().setCircuitBreaker("cache-circuit");
        AtomicInteger circuitDispatches = new AtomicInteger();
        List<HttpClientObserverEvent> circuitEvents = new CopyOnWriteArrayList<>();
        ToggleCircuitBreakerApplier circuitApplier = new ToggleCircuitBreakerApplier();
        WebClient circuitWebClient = WebClient.builder()
                .baseUrl("http://cache.test")
                .filter(ReactiveClientInvocationHandler.finalRequestObservationFilter())
                .exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.OK)
                        .header(HttpHeaders.CONTENT_TYPE, "text/plain")
                        .body("circuit-" + circuitDispatches.incrementAndGet())
                        .build()))
                .build();

        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getBeanFactory().registerSingleton("circuitObserver",
                    (io.github.huynhngochuyhoang.httpstarter.observability.HttpClientObserver) circuitEvents::add);
            context.refresh();
            CacheClient client = cacheClient(
                    circuitWebClient,
                    circuitConfig,
                    context,
                    LocalResponseCacheManager.testing(System::nanoTime),
                    circuitApplier,
                    null);

            assertThatThrownBy(() -> client.get("circuit", "principal", "tenant", "en-US").block())
                    .satisfies(error -> assertThat(hasCause(error, CallNotPermittedException.class)).isTrue());
            circuitApplier.reject.set(false);
            assertThat(client.get("circuit", "principal", "tenant", "en-US").block())
                    .isEqualTo("circuit-1");
            assertThat(client.get("circuit", "principal", "tenant", "en-US").block())
                    .isEqualTo("circuit-1");

            assertThat(circuitDispatches).hasValue(1);
            assertThat(circuitApplier.applications).hasValue(2);
            assertThat(circuitEvents).hasSize(3);
            assertIsolatedCacheTerminal(circuitEvents.get(0));
            assertThat(circuitEvents.get(0).getError()).isInstanceOf(CallNotPermittedException.class);
            assertThat(circuitEvents.get(1).getAttemptCount()).isEqualTo(1);
            assertIsolatedCacheTerminal(circuitEvents.get(2));
            assertThat(circuitEvents.get(2).getError()).isNull();
        }

        ReactiveHttpClientProperties.ClientConfig retryConfig = singleFlightConfig(0);
        retryConfig.getResilience().setEnabled(true);
        retryConfig.getResilience().setRetry("cache-retry");
        AtomicBoolean fail = new AtomicBoolean(true);
        AtomicInteger retryDispatches = new AtomicInteger();
        List<HttpClientObserverEvent> retryEvents = new CopyOnWriteArrayList<>();
        WebClient retryWebClient = WebClient.builder()
                .baseUrl("http://cache.test")
                .filter(ReactiveClientInvocationHandler.finalRequestObservationFilter())
                .exchangeFunction(request -> {
                    int dispatch = retryDispatches.incrementAndGet();
                    return Mono.just(fail.get()
                            ? ClientResponse.create(HttpStatus.SERVICE_UNAVAILABLE)
                                    .header("X-Retry-Evidence", Integer.toString(dispatch))
                                    .body("retry")
                                    .build()
                            : ClientResponse.create(HttpStatus.OK)
                                    .header(HttpHeaders.CONTENT_TYPE, "text/plain")
                                    .body("recovered")
                                    .build());
                })
                .build();

        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getBeanFactory().registerSingleton("retryObserver",
                    (io.github.huynhngochuyhoang.httpstarter.observability.HttpClientObserver) retryEvents::add);
            context.refresh();
            CacheClient client = cacheClient(
                    retryWebClient,
                    retryConfig,
                    context,
                    LocalResponseCacheManager.testing(System::nanoTime),
                    new RetryOnceApplier(),
                    null);

            assertThatThrownBy(() -> client.get("retry", "principal", "tenant", "en-US").block())
                    .satisfies(error -> assertThat(hasCause(error,
                            io.github.huynhngochuyhoang.httpstarter.exception.RemoteServiceException.class)).isTrue());
            fail.set(false);
            assertThat(client.get("retry", "principal", "tenant", "en-US").block())
                    .isEqualTo("recovered");
            assertThat(client.get("retry", "principal", "tenant", "en-US").block())
                    .isEqualTo("recovered");

            assertThat(retryDispatches).hasValue(3);
            assertThat(retryEvents).hasSize(3);
            assertThat(retryEvents.get(0).getAttemptCount()).isEqualTo(2);
            assertThat(retryEvents.get(0).getStatusCode()).isEqualTo(503);
            assertThat(retryEvents.get(0).getError()).isNotNull();
            assertThat(retryEvents.get(1).getAttemptCount()).isEqualTo(1);
            assertIsolatedCacheTerminal(retryEvents.get(2));
            assertThat(retryEvents.get(2).getError()).isNull();
        }
    }


    @Test
    void cachedReadsDoNotSuppressWritesOrInferInvalidation() {
        ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();
        ReactiveHttpClientProperties.CachePolicyConfig policy =
                new ReactiveHttpClientProperties.CachePolicyConfig();
        policy.setTtlMs(60_000L);
        policy.setMaximumSize(10L);
        policy.setSharedResponse(true);
        config.getCache().getPolicies().put("local", policy);
        AtomicInteger reads = new AtomicInteger();
        AtomicInteger writes = new AtomicInteger();
        WebClient webClient = WebClient.builder()
                .baseUrl("http://cache.test")
                .exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.OK)
                        .header(HttpHeaders.CONTENT_TYPE, "text/plain")
                        .body(request.method() == org.springframework.http.HttpMethod.GET
                                ? "read-" + reads.incrementAndGet()
                                : "write-" + writes.incrementAndGet())
                        .build()))
                .build();

        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.refresh();
            ReadWriteCacheClient client = readWriteCacheClient(
                    webClient, config, context, LocalResponseCacheManager.testing(System::nanoTime));

            assertThat(client.read("42").block()).isEqualTo("read-1");
            assertThat(client.read("42").block()).isEqualTo("read-1");
            assertThat(client.write("42").block()).isEqualTo("write-1");
            assertThat(client.read("42").block()).isEqualTo("read-1");
        }

        assertThat(reads).hasValue(1);
        assertThat(writes).hasValue(1);
    }

    private static void assertIsolatedCacheTerminal(HttpClientObserverEvent event) {
        assertThat(event.getAttemptCount()).isZero();
        assertThat(event.getStatusCode()).isNull();
        assertThat(event.getRequestUrl()).isNull();
        assertThat(event.getRequestHeaders()).isEmpty();
        assertThat(event.getRequestBytes()).isEqualTo(event.getCacheOutcome() != null
                ? HttpClientObserverEvent.UNKNOWN_SIZE
                : 0L);
        assertThat(event.getResponseBytes()).isEqualTo(HttpClientObserverEvent.UNKNOWN_SIZE);
        assertThat(event.getFailureStage()).isNull();
    }

    private static String load(LocalResponseCacheManager manager,
                               EffectiveCachePolicy.Selection selection,
                               CacheKeyContract.OpaqueKey key,
                               AtomicInteger loads) {
        return cached(manager, selection, key,
                () -> Mono.just("value-" + loads.incrementAndGet())).block();
    }

    @SuppressWarnings("unchecked")
    private static <T> Mono<T> cached(LocalResponseCacheManager manager,
                                      EffectiveCachePolicy.Selection selection,
                                      CacheKeyContract.OpaqueKey key,
                                      Supplier<Mono<T>> loader) {
        return (Mono<T>) manager.getOrLoad(selection, key, () -> loader.get());
    }

    private static CacheKeyContract.OpaqueKey key(String value) {
        return CacheKeyContract.OpaqueKey.from(value.getBytes(StandardCharsets.UTF_8));
    }

    private static ClientResponse ok(String body) {
        return ClientResponse.create(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_TYPE, "text/plain")
                .body(body)
                .build();
    }

    private static EffectiveCachePolicy.Selection selection(String name, long ttlMs, long maximumSize) {
        return selection(name, ttlMs, maximumSize, false);
    }

    private static EffectiveCachePolicy.Selection selection(
            String name, long ttlMs, long maximumSize, boolean singleFlight) {
        ReactiveHttpClientProperties.CachePolicyConfig policy = new ReactiveHttpClientProperties.CachePolicyConfig();
        policy.setTtlMs(ttlMs);
        policy.setMaximumSize(maximumSize);
        policy.setSingleFlight(singleFlight);
        return new EffectiveCachePolicy.Selection(true, EffectiveCachePolicy.Source.CLIENT, name, policy);
    }

    private static EffectiveCachePolicy.Selection refreshSelection(
            String name, long ttlMs, long maximumSize, long refreshAfterMs, long refreshTimeoutMs) {
        ReactiveHttpClientProperties.CachePolicyConfig policy = new ReactiveHttpClientProperties.CachePolicyConfig();
        policy.setTtlMs(ttlMs);
        policy.setMaximumSize(maximumSize);
        policy.setRefreshAfterMs(refreshAfterMs);
        policy.setRefreshTimeoutMs(refreshTimeoutMs);
        return new EffectiveCachePolicy.Selection(true, EffectiveCachePolicy.Source.CLIENT, name, policy);
    }

    private static ReactiveHttpClientProperties.ClientConfig config() {
        ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();
        ReactiveHttpClientProperties.CachePolicyConfig policy = new ReactiveHttpClientProperties.CachePolicyConfig();
        policy.setTtlMs(60_000L);
        policy.setMaximumSize(100L);
        policy.setVaryByHeaders(List.of("Idempotency-Key"));
        config.getCache().setPolicy("local");
        config.getCache().getPolicies().put("local", policy);
        return config;
    }

    private static ReactiveHttpClientProperties.ClientConfig singleFlightConfig(long logicalCallTimeoutMs) {
        ReactiveHttpClientProperties.ClientConfig config = config();
        config.setLogicalCallTimeoutMs(logicalCallTimeoutMs);
        config.getCache().getPolicies().get("local").setSingleFlight(true);
        config.getCache().getPolicies().get("local").setVaryByParameters(List.of("principal", "tenant"));
        config.getCache().getPolicies().get("local")
                .setVaryByHeaders(List.of("Accept-Language", "Idempotency-Key"));
        return config;
    }

    @ReactiveHttpClient(name = "cache-client")
    interface CacheClient {
        @GET("/catalog/{id}")
        Mono<String> get(@PathVar("id") String id,
                         @CacheKey("principal") String principal,
                         @CacheKey("tenant") String tenant,
                         @HeaderParam("Accept-Language") String language);
    }

    @ReactiveHttpClient(name = "cache-client")
    @LogHttpExchange(logger = CacheCompositionLogger.class)
    interface TerminalCacheClient {
        @GET("/catalog/{id}")
        Mono<String> get(@PathVar("id") String id,
                         @CacheKey("principal") String principal,
                         @CacheKey("tenant") String tenant,
                         @HeaderParam("Accept-Language") String language);
    }

    @ReactiveHttpClient(name = "read-write-cache-client")
    interface ReadWriteCacheClient {
        @GET("/catalog/{id}")
        @CacheResponse("local")
        Mono<String> read(@PathVar("id") String id);

        @POST("/catalog/{id}")
        Mono<String> write(@PathVar("id") String id);
    }

    @ReactiveHttpClient(name = "response-cache-client")
    interface ResponseCacheClient {
        @GET("/plain/{id}")
        Mono<String> plain(@PathVar("id") String id, @CacheKey("partition") String partition);

        @GET("/redirect/{id}")
        Mono<ResponseEntity<String>> redirect(
                @PathVar("id") String id, @CacheKey("partition") String partition);
    }

    @ReactiveHttpClient(name = "body-cache-client")
    interface BodyCacheClient {
        @GET("/body/{id}")
        Mono<String> get(@PathVar("id") String id, @Body String body);
    }

    private static final class RetryOnceApplier extends NoopResilienceOperatorApplier {
        private final AtomicInteger applications = new AtomicInteger();
        private final AtomicInteger subscriptions = new AtomicInteger();

        @Override
        public <T> Mono<T> applyRetry(Mono<T> mono, String instanceName) {
            applications.incrementAndGet();
            Mono<T> retried = mono.retry(1);
            return Mono.defer(() -> {
                subscriptions.incrementAndGet();
                return retried;
            });
        }

        @Override
        public boolean canRetryMoreThanOnce(String instanceName) {
            return true;
        }
    }

    private static final class CacheCompositionLogger implements HttpExchangeLogger {
        private final List<HttpExchangeLogContext> logs;

        private CacheCompositionLogger(List<HttpExchangeLogContext> logs) {
            this.logs = logs;
        }

        @Override
        public void log(HttpExchangeLogContext context) {
            logs.add(context);
        }
    }

    private static final class ToggleCircuitBreakerApplier extends NoopResilienceOperatorApplier {
        private final CircuitBreaker circuitBreaker = CircuitBreaker.ofDefaults("cache-open");
        private final AtomicBoolean reject = new AtomicBoolean(true);
        private final AtomicInteger applications = new AtomicInteger();

        @Override
        public <T> Mono<T> applyCircuitBreaker(Mono<T> mono, String instanceName) {
            applications.incrementAndGet();
            return Mono.defer(() -> reject.get()
                    ? Mono.error(CallNotPermittedException.createCallNotPermittedException(circuitBreaker))
                    : mono);
        }
    }

    private static final class RecordingCircuitBreakerApplier extends NoopResilienceOperatorApplier {
        private final AtomicInteger applications = new AtomicInteger();
        private final AtomicInteger subscriptions = new AtomicInteger();

        @Override
        public <T> Mono<T> applyCircuitBreaker(Mono<T> mono, String instanceName) {
            applications.incrementAndGet();
            return Mono.defer(() -> {
                subscriptions.incrementAndGet();
                return mono;
            });
        }
    }
}
