package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.annotation.*;
import io.github.huynhngochuyhoang.httpstarter.auth.AuthContext;
import io.github.huynhngochuyhoang.httpstarter.auth.InvalidatableAuthProvider;
import io.github.huynhngochuyhoang.httpstarter.auth.OutboundAuthFilter;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import io.github.huynhngochuyhoang.httpstarter.exception.LogicalCallTimeoutException;
import io.github.huynhngochuyhoang.httpstarter.filter.CorrelationIdWebFilter;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientFailureStage;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientObserverEvent;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
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
                new LocalResponseCacheManager.Snapshot(2, 4, 2, false));
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
    void resilienceRetryResolvesCurrentAuthAfterTheOneTimeUnauthorizedReplay() {
        ReactiveHttpClientProperties.ClientConfig config = config();
        config.setAuthProvider("cache-auth");
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
                    return Mono.just(ClientResponse.create(HttpStatus.OK)
                            .header(HttpHeaders.CONTENT_TYPE, "text/plain")
                            .body("authorized")
                            .build());
                })
                .build();

        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.refresh();
            CacheClient client = cacheClient(
                    webClient,
                    config,
                    context,
                    LocalResponseCacheManager.testing(System::nanoTime),
                    new RetryOnceApplier(),
                    authProvider);

            assertThat(client.get("42", "principal-a", "tenant-a", "en-US").block())
                    .isEqualTo("authorized");
        }

        assertThat(authCalls).hasValue(3);
        assertThat(invalidations).hasValue(1);
        assertThat(staleDispatches).hasValue(1);
        assertThat(freshDispatches).hasValue(2);
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

    private static EffectiveCachePolicy.Selection selection(String name, long ttlMs, long maximumSize) {
        ReactiveHttpClientProperties.CachePolicyConfig policy = new ReactiveHttpClientProperties.CachePolicyConfig();
        policy.setTtlMs(ttlMs);
        policy.setMaximumSize(maximumSize);
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

    @ReactiveHttpClient(name = "cache-client")
    interface CacheClient {
        @GET("/catalog/{id}")
        Mono<String> get(@PathVar("id") String id,
                         @CacheKey("principal") String principal,
                         @CacheKey("tenant") String tenant,
                         @HeaderParam("Accept-Language") String language);
    }

    @ReactiveHttpClient(name = "response-cache-client")
    interface ResponseCacheClient {
        @GET("/plain/{id}")
        Mono<String> plain(@PathVar("id") String id, @CacheKey("partition") String partition);

        @GET("/redirect/{id}")
        Mono<ResponseEntity<String>> redirect(
                @PathVar("id") String id, @CacheKey("partition") String partition);
    }

    private static final class RetryOnceApplier extends NoopResilienceOperatorApplier {
        @Override
        public <T> Mono<T> applyRetry(Mono<T> mono, String instanceName) {
            return mono.retry(1);
        }

        @Override
        public boolean canRetryMoreThanOnce(String instanceName) {
            return true;
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
