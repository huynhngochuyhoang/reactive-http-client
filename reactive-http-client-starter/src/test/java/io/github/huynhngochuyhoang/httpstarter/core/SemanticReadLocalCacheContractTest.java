package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.annotation.*;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SemanticReadLocalCacheContractTest {

    @Test
    void getAndAcknowledgedPostShareHitExpiryCapacityAndShutdownSemantics() {
        for (Verb verb : Verb.values()) {
            AtomicLong ticker = new AtomicLong();
            AtomicInteger dispatches = new AtomicInteger();
            RecordingCircuitBreakerApplier resilience = new RecordingCircuitBreakerApplier();
            WebClient webClient = WebClient.builder()
                    .baseUrl("http://semantic-cache.test")
                    .exchangeFunction(request -> Mono.just(ok("dispatch-" + dispatches.incrementAndGet())))
                    .build();

            try (AnnotationConfigApplicationContext context = context()) {
                LocalResponseCacheManager manager = LocalResponseCacheManager.testing(ticker::get);
                SemanticReadClient client = client(webClient, config(100, 1), context, manager, resilience);

                String first = plain(client, verb, "one").block();
                String hit = plain(client, verb, "one").block();
                assertThat(hit).as("%s hit identity", verb).isSameAs(first);
                assertThat(dispatches).as("%s hit dispatches", verb).hasValue(1);
                assertThat(resilience.subscriptions).as("%s hit resilience", verb).hasValue(1);

                ticker.addAndGet(Duration.ofMillis(100).toNanos());
                assertThat(plain(client, verb, "one").block()).isEqualTo("dispatch-2");
                assertThat(plain(client, verb, "two").block()).isEqualTo("dispatch-3");
                assertThat(plain(client, verb, "one").block()).isEqualTo("dispatch-4");
                assertThat(manager.snapshot().currentSize()).isEqualTo(1);
                assertThat(manager.snapshot().configuredCapacity()).isEqualTo(1);

                manager.close();
                assertThat(manager.snapshot()).isEqualTo(
                        new LocalResponseCacheManager.Snapshot(0, 0, 0, true));
                assertThatThrownBy(() -> plain(client, verb, "after-close").block())
                        .hasMessageContaining("closed");
            }

            assertThat(dispatches).as("%s total dispatches", verb).hasValue(4);
            assertThat(resilience.applications).as("%s resilience applications", verb).hasValue(4);
            assertThat(resilience.subscriptions).as("%s resilience subscriptions", verb).hasValue(4);
        }
    }

    @Test
    void duplicateMissesUseFirstSuccessfulFillForGetAndAcknowledgedPost() throws Exception {
        for (Verb verb : Verb.values()) {
            Sinks.One<ClientResponse> firstResponse = Sinks.one();
            Sinks.One<ClientResponse> secondResponse = Sinks.one();
            AtomicInteger dispatches = new AtomicInteger();
            WebClient webClient = WebClient.builder()
                    .baseUrl("http://semantic-cache.test")
                    .exchangeFunction(request -> dispatches.getAndIncrement() == 0
                            ? firstResponse.asMono()
                            : secondResponse.asMono())
                    .build();

            try (AnnotationConfigApplicationContext context = context()) {
                LocalResponseCacheManager manager = LocalResponseCacheManager.testing(System::nanoTime);
                SemanticReadClient client = client(
                        webClient, config(60_000, 10), context, manager, new NoopResilienceOperatorApplier());

                CompletableFuture<String> first = plain(client, verb, "duplicate").toFuture();
                CompletableFuture<String> second = plain(client, verb, "duplicate").toFuture();
                awaitValue(dispatches, 2);
                assertThat(dispatches).as("%s duplicate misses", verb).hasValue(2);

                secondResponse.tryEmitValue(ok("first-successful")).orThrow();
                String winner = (String) CompletableFuture.anyOf(first, second).get(3, TimeUnit.SECONDS);
                firstResponse.tryEmitValue(ok("late-success")).orThrow();
                assertThat(List.of(
                        first.get(3, TimeUnit.SECONDS),
                        second.get(3, TimeUnit.SECONDS)))
                        .containsExactlyInAnyOrder("first-successful", "late-success");

                String hit = plain(client, verb, "duplicate").block();
                assertThat(hit).isSameAs(winner);
                assertThat(dispatches).hasValue(2);
                manager.close();
            }
        }
    }

    @Test
    void responseEligibilityAndHeaderSafetyAreIdenticalAcrossVerbs() {
        for (Verb verb : Verb.values()) {
            Map<String, AtomicInteger> dispatches = new ConcurrentHashMap<>();
            AtomicBoolean stallCancellation = new AtomicBoolean(true);
            CountDownLatch cancellationDispatched = new CountDownLatch(1);
            WebClient webClient = WebClient.builder()
                    .baseUrl("http://semantic-cache.test")
                    .exchangeFunction(request -> {
                        String path = request.url().getPath();
                        int dispatch = dispatches.computeIfAbsent(path, ignored -> new AtomicInteger())
                                .incrementAndGet();
                        if (path.endsWith("/cancel") && stallCancellation.get()) {
                            cancellationDispatched.countDown();
                            return Mono.never();
                        }
                        if (path.endsWith("/empty")) {
                            return Mono.just(ClientResponse.create(HttpStatus.NO_CONTENT).build());
                        }
                        if (path.endsWith("/client-error") || path.endsWith("/server-error")) {
                            HttpStatus status = path.endsWith("/client-error")
                                    ? HttpStatus.NOT_FOUND
                                    : HttpStatus.SERVICE_UNAVAILABLE;
                            return Mono.just(ClientResponse.create(status)
                                    .header(HttpHeaders.CONTENT_TYPE, "text/plain")
                                    .body("failed")
                                    .build());
                        }
                        if (path.endsWith("/redirect")) {
                            return Mono.just(ClientResponse.create(HttpStatus.FOUND)
                                    .header(HttpHeaders.LOCATION, "/target-" + dispatch)
                                    .body("redirect-" + dispatch)
                                    .build());
                        }
                        ClientResponse.Builder response = ClientResponse.create(
                                        path.contains("/entity/")
                                                ? HttpStatus.NON_AUTHORITATIVE_INFORMATION
                                                : HttpStatus.OK)
                                .header(HttpHeaders.CONTENT_TYPE, "text/plain")
                                .header(HttpHeaders.CONTENT_LANGUAGE, "en")
                                .header(HttpHeaders.ETAG, "v" + dispatch)
                                .header("X-Request-Id", "caller-" + dispatch);
                        if (path.endsWith("/cookie")) {
                            response.header(HttpHeaders.SET_COOKIE, "SESSION=private");
                        }
                        if (path.endsWith("/challenge")) {
                            response.header(HttpHeaders.WWW_AUTHENTICATE, "Bearer realm=test");
                        }
                        if (path.endsWith("/configured")) {
                            response.header("X-Caller-Session", "private");
                        }
                        return Mono.just(response.body("value-" + dispatch).build());
                    })
                    .build();

            try (AnnotationConfigApplicationContext context = context()) {
                LocalResponseCacheManager manager = LocalResponseCacheManager.testing(System::nanoTime);
                SemanticReadClient client = client(
                        webClient, config(60_000, 100), context, manager, new NoopResilienceOperatorApplier());

                String first = plain(client, verb, "safe").block();
                assertThat(plain(client, verb, "safe").block()).isSameAs(first);
                assertDispatches(dispatches, "/plain/safe", 1);

                ResponseEntity<String> firstEntity = entity(client, verb, "safe").block();
                ResponseEntity<String> cachedEntity = entity(client, verb, "safe").block();
                assertThat(cachedEntity.getStatusCode()).isEqualTo(HttpStatus.NON_AUTHORITATIVE_INFORMATION);
                assertThat(cachedEntity.getBody()).isSameAs(firstEntity.getBody());
                assertThat(cachedEntity.getHeaders().getFirst(HttpHeaders.CONTENT_LANGUAGE)).isEqualTo("en");
                assertThat(cachedEntity.getHeaders().getFirst(HttpHeaders.ETAG)).isEqualTo("v1");
                assertThat(cachedEntity.getHeaders().containsHeader("X-Request-Id")).isFalse();
                assertDispatches(dispatches, "/entity/safe", 1);

                for (String scenario : List.of("cookie", "challenge", "configured")) {
                    assertThat(plain(client, verb, scenario).block()).isEqualTo("value-1");
                    assertThat(plain(client, verb, scenario).block()).isEqualTo("value-2");
                    assertDispatches(dispatches, "/plain/" + scenario, 2);
                }
                assertThat(entity(client, verb, "cookie").block().getBody()).isEqualTo("value-1");
                assertThat(entity(client, verb, "cookie").block().getBody()).isEqualTo("value-2");
                assertDispatches(dispatches, "/entity/cookie", 2);

                for (int attempt = 0; attempt < 2; attempt++) {
                    for (String scenario : List.of("client-error", "server-error")) {
                        assertThatThrownBy(() -> plain(client, verb, scenario).block())
                                .hasMessageContaining(scenario.equals("client-error") ? "404" : "503");
                        assertThatThrownBy(() -> entity(client, verb, scenario).block())
                                .hasMessageContaining(scenario.equals("client-error") ? "404" : "503");
                    }
                    assertThat(plain(client, verb, "empty").block()).isNull();
                }
                for (String scenario : List.of("client-error", "server-error")) {
                    assertDispatches(dispatches, "/plain/" + scenario, 2);
                    assertDispatches(dispatches, "/entity/" + scenario, 2);
                }
                assertDispatches(dispatches, "/plain/empty", 2);

                ResponseEntity<String> firstRedirect = entity(client, verb, "redirect").block();
                ResponseEntity<String> secondRedirect = entity(client, verb, "redirect").block();
                assertThat(firstRedirect.getHeaders().getLocation()).hasPath("/target-1");
                assertThat(secondRedirect.getHeaders().getLocation()).hasPath("/target-2");
                assertDispatches(dispatches, "/entity/redirect", 2);

                Disposable cancelled = plain(client, verb, "cancel").subscribe();
                await(cancellationDispatched);
                cancelled.dispose();
                stallCancellation.set(false);
                String afterCancel = plain(client, verb, "cancel").block();
                assertThat(plain(client, verb, "cancel").block()).isSameAs(afterCancel);
                assertDispatches(dispatches, "/plain/cancel", 2);
                manager.close();
            }
        }
    }

    @Test
    void evictionAndCloseRejectLatePublicationForEveryVerb() throws Exception {
        for (Verb verb : Verb.values()) {
            Sinks.One<ClientResponse> pending = Sinks.one();
            AtomicBoolean immediate = new AtomicBoolean();
            AtomicInteger dispatches = new AtomicInteger();
            WebClient webClient = WebClient.builder()
                    .baseUrl("http://semantic-cache.test")
                    .exchangeFunction(request -> {
                        int dispatch = dispatches.incrementAndGet();
                        return immediate.get() ? Mono.just(ok("fresh-" + dispatch)) : pending.asMono();
                    })
                    .build();

            try (AnnotationConfigApplicationContext context = context()) {
                LocalResponseCacheManager manager = LocalResponseCacheManager.testing(System::nanoTime);
                SemanticReadClient client = client(
                        webClient, config(60_000, 10), context, manager, new NoopResilienceOperatorApplier());

                CompletableFuture<String> active = plain(client, verb, "late").toFuture();
                awaitValue(dispatches, 1);
                assertThat(dispatches).hasValue(1);
                manager.evictAllForTesting();
                pending.tryEmitValue(ok("pre-eviction")).orThrow();
                assertThat(active.get(1, TimeUnit.SECONDS)).isEqualTo("pre-eviction");
                assertThat(manager.snapshot().currentSize()).isZero();

                immediate.set(true);
                String fresh = plain(client, verb, "late").block();
                assertThat(plain(client, verb, "late").block()).isSameAs(fresh);
                assertThat(dispatches).hasValue(2);

                manager.close();
                assertThat(manager.snapshot()).isEqualTo(
                        new LocalResponseCacheManager.Snapshot(0, 0, 0, true));
            }
        }
    }

    private static SemanticReadClient client(
            WebClient webClient,
            ReactiveHttpClientProperties.ClientConfig config,
            AnnotationConfigApplicationContext context,
            LocalResponseCacheManager manager,
            ResilienceOperatorApplier resilienceOperatorApplier) {
        ReactiveClientInvocationHandler handler = new ReactiveClientInvocationHandler(
                webClient,
                new MethodMetadataCache(),
                new RequestArgumentResolver(),
                new DefaultErrorDecoder(),
                config,
                "semantic-cache-client",
                SemanticReadClient.class,
                context,
                resilienceOperatorApplier,
                TestJsonCodecs.jsonCodec(),
                new ReactiveHttpClientProperties.ObservabilityConfig(),
                manager);
        return (SemanticReadClient) Proxy.newProxyInstance(
                SemanticReadLocalCacheContractTest.class.getClassLoader(),
                new Class<?>[]{SemanticReadClient.class},
                handler);
    }

    private static ReactiveHttpClientProperties.ClientConfig config(long ttlMs, long maximumSize) {
        ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();
        config.getCache().getPolicies().put("get-local", policy(ttlMs, maximumSize, false));
        config.getCache().getPolicies().put("post-local", policy(ttlMs, maximumSize, true));
        config.getResilience().setEnabled(true);
        config.getResilience().setCircuitBreaker("semantic-cache-cb");
        return config;
    }

    private static ReactiveHttpClientProperties.CachePolicyConfig policy(
            long ttlMs, long maximumSize, boolean bodySelected) {
        ReactiveHttpClientProperties.CachePolicyConfig policy =
                new ReactiveHttpClientProperties.CachePolicyConfig();
        policy.setTtlMs(ttlMs);
        policy.setMaximumSize(maximumSize);
        policy.setSharedResponse(true);
        policy.setNonCacheableResponseHeaders(List.of("X-Caller-Session"));
        if (bodySelected) {
            policy.setVaryByParameters(List.of("payload"));
        }
        return policy;
    }

    private static Mono<String> plain(SemanticReadClient client, Verb verb, String scenario) {
        return verb == Verb.GET
                ? client.getPlain(scenario)
                : client.postPlain(scenario, "payload-" + scenario);
    }

    private static Mono<ResponseEntity<String>> entity(
            SemanticReadClient client, Verb verb, String scenario) {
        return verb == Verb.GET
                ? client.getEntity(scenario)
                : client.postEntity(scenario, "payload-" + scenario);
    }

    private static ClientResponse ok(String body) {
        return ClientResponse.create(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_TYPE, "text/plain")
                .body(body)
                .build();
    }

    private static AnnotationConfigApplicationContext context() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.refresh();
        return context;
    }

    private static void assertDispatches(Map<String, AtomicInteger> dispatches, String path, int expected) {
        assertThat(dispatches.get(path)).as(path).hasValue(expected);
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(1, TimeUnit.SECONDS)).isTrue();
        }
        catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new AssertionError(error);
        }
    }

    private static void awaitValue(AtomicInteger value, int expected) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
        while (value.get() != expected && System.nanoTime() < deadline) {
            try {
                Thread.sleep(1);
            }
            catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new AssertionError(error);
            }
        }
    }

    private enum Verb {
        GET,
        POST
    }

    @ReactiveHttpClient(name = "semantic-cache-client", baseUrl = "http://semantic-cache.test")
    interface SemanticReadClient {

        @GET("/plain/{scenario}")
        @CacheResponse("get-local")
        Mono<String> getPlain(@PathVar("scenario") String scenario);

        @POST("/plain/{scenario}")
        @CacheResponse(value = "post-local", semanticRead = true)
        Mono<String> postPlain(
                @PathVar("scenario") String scenario,
                @Body @CacheKey("payload") String payload);

        @GET("/entity/{scenario}")
        @CacheResponse("get-local")
        Mono<ResponseEntity<String>> getEntity(@PathVar("scenario") String scenario);

        @POST("/entity/{scenario}")
        @CacheResponse(value = "post-local", semanticRead = true)
        Mono<ResponseEntity<String>> postEntity(
                @PathVar("scenario") String scenario,
                @Body @CacheKey("payload") String payload);
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
