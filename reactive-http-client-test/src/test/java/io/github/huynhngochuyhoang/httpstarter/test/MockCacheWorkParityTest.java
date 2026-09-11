package io.github.huynhngochuyhoang.httpstarter.test;

import io.github.huynhngochuyhoang.httpstarter.annotation.*;
import io.github.huynhngochuyhoang.httpstarter.auth.AuthContext;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import io.github.huynhngochuyhoang.httpstarter.core.MethodMetadataCache;
import io.github.huynhngochuyhoang.httpstarter.core.NoopResilienceOperatorApplier;
import io.github.huynhngochuyhoang.httpstarter.exception.CacheWorkRejectedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.ClientResponse;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@org.junit.jupiter.api.Timeout(20)
class MockCacheWorkParityTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void admissionRejectionJoiningAndReuseWorkWithEitherClock(boolean deterministic) {
        var body = Sinks.<String>one();
        var authCalls = new AtomicInteger();
        var retryCalls = new AtomicInteger();
        var config = config(true, deterministic);
        config.getResilience().setEnabled(true);
        config.getResilience().setRetry("custom");
        config.getResilience().setRetryMethods(java.util.Set.of("GET"));
        var builder = MockReactiveHttpClient.forClient(Client.class).clientConfig(config)
                .withCacheObservability().withAuthProvider(request -> {
                    authCalls.incrementAndGet(); return Mono.just(AuthContext.empty());
                }).resilienceOperatorApplier(new NoopResilienceOperatorApplier() {
                    @Override public <T> Mono<T> applyRetry(Mono<T> source, String name) {
                        return source.doOnSubscribe(ignored -> retryCalls.incrementAndGet());
                    }
                }).respondToPath("/get/a", ignored -> response(body));
        if (deterministic) { builder.withDeterministicCacheTime(); }
        try (var mock = builder.build()) {
            var first = mock.proxy().get("a").toFuture();
            reject(() -> mock.proxy().get("b").block(), CacheWorkRejectedException.Reason.LOAD_CAPACITY);
            var waiter = mock.proxy().get("a").toFuture();
            assertThat(mock.cacheSnapshot().coalescedWaiterCount()).isEqualTo(1);
            assertThat(mock.cacheWorkSnapshot().activeCallers()).isEqualTo(2);
            assertThat(mock.cacheWorkSnapshot().activeLoads()).isEqualTo(1);
            reject(() -> mock.proxy().get("c").block(), CacheWorkRejectedException.Reason.CALLER_CAPACITY);
            body.tryEmitValue("ok").orThrow();
            assertThat(first.join()).isEqualTo("ok");
            assertThat(waiter.join()).isEqualTo("ok");
            assertThat(mock.proxy().get("a").block()).isEqualTo("ok");
            assertThat(mock.loadCount()).isEqualTo(1);
            assertThat(retryCalls).hasValue(1);
            assertThat(authCalls.get()).isGreaterThanOrEqualTo(4);
            var work = mock.cacheWorkSnapshot();
            assertThat(work.activeCallers()).isZero();
            assertThat(work.activeLoads()).isZero();
            assertThat(work.rejectionCounts().get("work"))
                    .containsEntry("caller_capacity", 1L).containsEntry("load_capacity", 1L);
            assertThatThrownBy(() -> work.rejectionCounts().get("work").put("other", 1L))
                    .isInstanceOf(UnsupportedOperationException.class);
            mock.close();
            assertThat(mock.cacheWorkSnapshot().state()).isEqualTo("closed");
            assertThat(mock.cacheWorkSnapshot().rejectionCounts()).isEqualTo(work.rejectionCounts());
        }
    }

    @Test
    void semanticPostRefreshSkipsAndTerminalHistorySurviveClose() {
        var refresh = Sinks.<String>one();
        var calls = new AtomicInteger();
        var config = config(true, true);
        var policy = config.getCache().getPolicies().get("work");
        policy.setRefreshAfterMs(1000L);
        policy.setRefreshTimeoutMs(60000L);
        policy.getWork().setMaximumConcurrentRefreshes(1L);
        try (var mock = MockReactiveHttpClient.forClient(Client.class).clientConfig(config)
                .withDeterministicCacheTime()
                .respondToPath("/search", ignored -> calls.incrementAndGet() <= 2
                        ? MockReactiveHttpClient.text(200, "old") : response(refresh)).build()) {
            assertThat(mock.proxy().search("a").block()).isEqualTo("old");
            assertThat(mock.proxy().search("b").block()).isEqualTo("old");
            mock.advanceCacheTime(Duration.ofSeconds(2));
            assertThat(mock.proxy().search("a").block()).isEqualTo("old");
            assertThat(mock.proxy().search("b").block()).isEqualTo("old");
            assertThat(mock.cacheWorkSnapshot().activeRefreshes()).isEqualTo(1);
            assertThat(mock.cacheWorkSnapshot().refreshSkipCounts().get("work")).containsEntry("capacity", 1L);
            assertThat(mock.cacheSnapshot().refreshCounts()).isEmpty();
            mock.close();
            assertThat(mock.cacheWorkSnapshot().activeRefreshes()).isZero();
            assertThat(mock.cacheSnapshot().refreshCounts().values()).singleElement()
                    .satisfies(counts -> assertThat(counts).containsEntry("cancellation", 1L));
            assertThat(mock.cacheWorkSnapshot().refreshSkipCounts().get("work")).containsEntry("capacity", 1L);
        }
    }

    @Test
    void independentLoadRemainsVisibleAfterCloseUntilItsCallerTerminates() {
        var body = Sinks.<String>one();
        try (var mock = MockReactiveHttpClient.forClient(Client.class).clientConfig(config(false, false))
                .withCacheObservability().respondToPath("/get/a", ignored -> response(body)).build()) {
            var pending = mock.proxy().get("a").toFuture();
            mock.close();
            assertThat(mock.cacheWorkSnapshot().closed()).isTrue();
            assertThat(mock.cacheWorkSnapshot().activeLoads()).isEqualTo(1);
            assertThat(mock.cacheWorkSnapshot().activeCallers()).isEqualTo(1);
            body.tryEmitValue("done").orThrow();
            assertThat(pending.join()).isEqualTo("done");
            assertThat(mock.cacheWorkSnapshot().activeLoads()).isZero();
            assertThat(mock.cacheWorkSnapshot().activeCallers()).isZero();
            assertThat(mock.cacheWorkSnapshot().loadCounts().values()).singleElement()
                    .satisfies(counts -> assertThat(counts).containsEntry("success", 1L));
            assertThat(mock.cacheEntryCount()).isZero();
        }
    }

    @Test
    void failedLateValidationRestoresCallerConfigAndUnselectedMocksHaveNoCacheOwner() {
        var config = new ReactiveHttpClientProperties.ClientConfig();
        var failure = new IllegalStateException("customization rejected");
        var context = new java.util.concurrent.atomic.AtomicReference<org.springframework.context.ApplicationContext>();
        var metadata = new MethodMetadataCache() {
            @Override public void validateDeclarativeCacheCustomizations(
                    org.springframework.beans.factory.ListableBeanFactory application, Class<?> type, String name,
                    ReactiveHttpClientProperties.ClientConfig client) {
                context.set((org.springframework.context.ApplicationContext) application);
                throw failure;
            }
        };
        assertThatThrownBy(() -> MockReactiveHttpClient.forClient(Unselected.class).clientConfig(config)
                .methodMetadataCache(metadata).withAuthProvider(request -> Mono.just(AuthContext.empty())).build())
                .isSameAs(failure);
        assertThat(config.hasAuthConfigured()).isFalse();
        assertThat(((org.springframework.context.ConfigurableApplicationContext) context.get()).isActive()).isFalse();
        try (var mock = MockReactiveHttpClient.forClient(Unselected.class).clientConfig(config)
                .withDeterministicCacheTime().respondToPath("/plain", ignored -> MockReactiveHttpClient.text(200, "ok")).build()) {
            assertThat(mock.proxy().get().block()).isEqualTo("ok");
            assertThat(ReflectionTestUtils.getField(mock, "cacheControl")).isNull();
            assertThatThrownBy(mock::cacheWorkSnapshot).hasMessageContaining("No response-cache policy");
        }
        // Preserve the published constructor as the new view evolves independently.
        assertThat(new MockReactiveHttpClient.CacheSnapshot(0, null, 0, 0, 0, 0,
                Map.of(), Map.of(), Map.of(), true).closed()).isTrue();
    }

    private static ReactiveHttpClientProperties.ClientConfig config(boolean singleFlight, boolean weighted) {
        var config = new ReactiveHttpClientProperties.ClientConfig();
        var policy = new ReactiveHttpClientProperties.CachePolicyConfig();
        policy.setTtlMs(60000L);
        policy.setMaximumSize(10L);
        if (weighted) { policy.setMaximumTotalDecodedResponseBytes(1024L); }
        policy.setSingleFlight(singleFlight);
        policy.setSharedResponse(true);
        policy.setVaryByParameters(List.of("body"));
        var work = new ReactiveHttpClientProperties.CacheWorkConfig();
        work.setMaximumConcurrentCallers(2L);
        work.setMaximumConcurrentLoads(1L);
        policy.setWork(work);
        config.getCache().getPolicies().put("work", policy);
        return config;
    }

    private static ClientResponse response(Sinks.One<String> body) {
        return ClientResponse.create(HttpStatus.OK).header("Content-Type", "text/plain")
                .body(body.asMono().<org.springframework.core.io.buffer.DataBuffer>map(value -> org.springframework.core.io.buffer.DefaultDataBufferFactory
                        .sharedInstance.wrap(value.getBytes(java.nio.charset.StandardCharsets.UTF_8))).flux()).build();
    }

    private static void reject(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, CacheWorkRejectedException.Reason reason) {
        assertThatThrownBy(call).isInstanceOfSatisfying(CacheWorkRejectedException.class,
                rejection -> assertThat(rejection.getReason()).isEqualTo(reason));
    }

    @ReactiveHttpClient(name = "work-mock")
    interface Client {
        @GET("/get/{id}") @CacheResponse("work") Mono<String> get(@PathVar("id") @CacheKey("body") String id);
        @POST("/search") @CacheResponse(value = "work", semanticRead = true)
        Mono<String> search(@Body @CacheKey("body") String body);
    }

    @ReactiveHttpClient(name = "plain-mock")
    interface Unselected { @GET("/plain") Mono<String> get(); }
}
