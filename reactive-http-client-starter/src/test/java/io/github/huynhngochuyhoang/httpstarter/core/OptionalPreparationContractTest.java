package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.annotation.CacheDisabled;
import io.github.huynhngochuyhoang.httpstarter.annotation.GET;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@Timeout(30)
class OptionalPreparationContractTest {
    private static final Duration WAIT = Duration.ofSeconds(5);
    interface Client {
        @GET("/value") Mono<String> value();
        @GET("/disabled") @CacheDisabled Mono<String> disabled();
    }
    enum Entry { STATIC, LEGACY, LEGACY_CONCRETE }

    @ParameterizedTest
    @EnumSource(Entry.class)
    void publicConstructionSeparatesIdentityViewFromLazyManagerOwnership(Entry entry) {
        try (var fixture = new Fixture(entry, false)) {
            boolean legacy = entry != Entry.STATIC;
            verify(fixture.web).mutate();
            if (legacy) {
                assertThat(fixture.handler.responseCacheManager()).isNotNull();
                assertThat(fixture.handler.responseCacheManager().snapshot().currentSize()).isZero();
                assertThat(ReflectionTestUtils.getField(fixture.handler, "cacheIdentityWebClient"))
                        .isNotSameAs(fixture.web);
            } else {
                assertThat(fixture.handler.responseCacheManager()).isNull();
                assertThat(ReflectionTestUtils.getField(fixture.handler, "cacheIdentityWebClient")).isNotSameAs(fixture.web);
            }
            var call = fixture.client.value();
            assertThat(fixture.dispatches).hasValue(0);
            assertThat(call.block(WAIT)).isEqualTo("ok");
            assertThat(call.block(WAIT)).isEqualTo("ok");
            assertThat(fixture.dispatches).hasValue(2);
            assertThat(fixture.filters).hasValue(2);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void sharedSchedulerAccessDoesNotScheduleWorkForSelectedOrUnselectedPolicies(boolean selected) {
        var config = config(selected);
        Scheduler scheduler = mock(Scheduler.class);
        try (var schedulers = mockStatic(Schedulers.class, CALLS_REAL_METHODS)) {
            schedulers.when(Schedulers::parallel).thenReturn(scheduler);
            schedulers.clearInvocations();
            var manager = LocalResponseCacheManager.createForClient(Client.class, "optional",
                    new MethodMetadataCache(), config, getClass().getClassLoader());
            schedulers.verify(Schedulers::parallel);
            if (selected) {
                assertThat(manager).isNotNull();
                assertThat(manager.snapshot().currentSize()).isZero();
                manager.close();
            } else {
                assertThat(manager).isNull();
            }
            verifyNoInteractions(scheduler);
        }
    }

    @Test
    void suppliedSchedulerRemainsExternalAndNullStillFailsForSelectedPolicies() {
        Scheduler scheduler = mock(Scheduler.class);
        try (var schedulers = mockStatic(Schedulers.class, CALLS_REAL_METHODS)) {
            var manager = LocalResponseCacheManager.createForClient(Client.class, "optional",
                    new MethodMetadataCache(), config(true), getClass().getClassLoader(),
                    null, null, System::nanoTime, scheduler);
            assertThat(manager).isNotNull();
            manager.close();
            schedulers.verify(Schedulers::parallel, never());
            verifyNoInteractions(scheduler);
            assertThatThrownBy(() -> LocalResponseCacheManager.createForClient(Client.class, "optional",
                    new MethodMetadataCache(), config(true), getClass().getClassLoader(),
                    null, null, System::nanoTime, null)).isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("refreshScheduler");
        }
    }

    @ParameterizedTest
    @EnumSource(Entry.class)
    void selectedCacheRetainsProbeOnMissAndHit(Entry entry) {
        try (var fixture = new Fixture(entry, true)) {
            verify(fixture.web).mutate();
            assertThat(fixture.handler.responseCacheManager()).isNotNull();
            assertThat(fixture.client.value().block(WAIT)).isEqualTo("ok");
            assertThat(fixture.client.value().block(WAIT)).isEqualTo("ok");
            assertThat(fixture.dispatches).hasValue(1);
            assertThat(fixture.filters).hasValue(3);
            fixture.handler.responseCacheManager().close();
            assertThat(fixture.handler.responseCacheManager().snapshot().closed()).isTrue();
            assertThatThrownBy(() -> fixture.client.value().block(WAIT)).hasMessageContaining("closed");
            assertThat(fixture.dispatches).hasValue(1);
            assertThat(fixture.handler.responseCacheManager().snapshot().currentSize()).isZero();
        }
    }

    @Test
    void frozenSelectionRejectsChangesEvenOnExcludedSiblingAndPreviouslyAssembledCall() {
        try (var fixture = new Fixture(Entry.STATIC, false)) {
            var call = fixture.client.value();
            fixture.config.getCache().setPolicy("read");
            assertThatThrownBy(fixture.client::disabled).hasMessageContaining("changed after startup");
            // Ordinary calls capture their invocation projection; new invocations enforce the guard.
            assertThat(call.block(WAIT)).isEqualTo("ok");
            assertThatThrownBy(fixture.client::value).hasMessageContaining("changed after startup");
            assertThat(fixture.dispatches).hasValue(1);
            verify(fixture.web).mutate();
        }
    }

    @Test
    void legacyLateSelectionNeedsItsRetainedViewAndManager() {
        try (var fixture = new Fixture(Entry.LEGACY, false)) {
            fixture.config.getCache().setPolicy("read");
            assertThat(fixture.client.value().block(WAIT)).isEqualTo("ok");
            assertThat(fixture.client.value().block(WAIT)).isEqualTo("ok");
            assertThat(fixture.dispatches).hasValue(1);
            assertThat(fixture.filters).hasValue(3);
            assertThat(fixture.handler.responseCacheManager().snapshot().currentSize()).isEqualTo(1);
        }
    }

    @Test
    void concurrentFirstCallsDoNotCreateAnotherIdentityViewOrManager() throws Exception {
        try (var fixture = new Fixture(Entry.STATIC, false); var executor = Executors.newFixedThreadPool(2)) {
            var start = new CyclicBarrier(2);
            var first = executor.submit(() -> { start.await(5, TimeUnit.SECONDS); return fixture.client.value().block(WAIT); });
            var second = executor.submit(() -> { start.await(5, TimeUnit.SECONDS); return fixture.client.value().block(WAIT); });
            assertThat(first.get(5, TimeUnit.SECONDS)).isEqualTo("ok");
            assertThat(second.get(5, TimeUnit.SECONDS)).isEqualTo("ok");
            assertThat(fixture.dispatches).hasValue(2);
            assertThat(fixture.handler.responseCacheManager()).isNull();
            verify(fixture.web).mutate();
        }
    }

    @Test
    void disabledSourcesHaveNoPayloadAndSelectedDecisionsStayIndependent() throws Exception {
        var metadata = new MethodMetadataCache();
        var ordinary = RequestPlan.from(metadata.get(Client.class.getMethod("value")), Client.class);
        var excluded = RequestPlan.from(metadata.get(Client.class.getMethod("disabled")), Client.class);
        for (var config : List.of(config(false), config(false))) {
            var disabled = EffectiveCachePolicy.decide(ordinary, config, "GET");
            var methodDisabled = EffectiveCachePolicy.decide(excluded, config, "GET");
            assertThat(disabled.selection().source()).isEqualTo(EffectiveCachePolicy.Source.DISABLED);
            assertThat(methodDisabled.selection().source()).isEqualTo(EffectiveCachePolicy.Source.METHOD_DISABLED);
            assertThat(disabled).isNotEqualTo(methodDisabled);
            for (var value : List.of(disabled, methodDisabled)) {
                assertThat(value.cacheable()).isFalse();
                assertThat(value.invalidReason()).isNull();
                assertThat(value.selection().policy()).isNull();
                assertThat(value.selection().policyName()).isNull();
            }
            config.getCache().setPolicy("read");
            var selected = EffectiveCachePolicy.decide(ordinary, config, "GET");
            assertThat(selected.cacheable()).isTrue();
            assertThat(selected).isNotSameAs(EffectiveCachePolicy.decide(ordinary, config, "GET"));
            assertThat(selected.selection().policy()).isSameAs(config.getCache().getPolicies().get("read"));
            config.getCache().setPolicy("missing");
            var invalid = EffectiveCachePolicy.decide(ordinary, config, "GET");
            assertThat(invalid.eligibility()).isEqualTo(EffectiveCachePolicy.Eligibility.INVALID);
            assertThat(invalid).isNotSameAs(EffectiveCachePolicy.decide(ordinary, config, "GET"));
        }
    }

    private static ReactiveHttpClientProperties.ClientConfig config(boolean selected) {
        var config = new ReactiveHttpClientProperties.ClientConfig();
        var policy = new ReactiveHttpClientProperties.CachePolicyConfig();
        policy.setTtlMs(60_000L);
        policy.setMaximumSize(16L);
        policy.setSharedResponse(true);
        config.getCache().getPolicies().put("read", policy);
        if (selected) config.getCache().setPolicy("read");
        return config;
    }

    @Test
    void missingWebClientStillFailsDuringConstruction() {
        try (var context = new GenericApplicationContext()) {
            context.refresh();
            assertThatThrownBy(() -> ReactiveClientInvocationHandler.create(null, new MethodMetadataCache(),
                    new RequestArgumentResolver(), new DefaultErrorDecoder(), config(false), "optional",
                    Client.class, context, new NoopResilienceOperatorApplier(), null, null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Test
    void lateFreshMetadataCannotReachAProbeWithoutAManager() throws Exception {
        var selected = new java.util.concurrent.atomic.AtomicBoolean();
        var metadata = new MethodMetadataCache() {
            @Override public MethodMetadata get(java.lang.reflect.Method method) {
                var value = new MethodMetadataCache().get(method);
                if (selected.get()) value.setCachePolicyName("read");
                return value;
            }
        };
        try (var context = new GenericApplicationContext()) {
            context.refresh();
            var web = spy(WebClient.builder().baseUrl("http://optional.invalid").build());
            var handler = ReactiveClientInvocationHandler.create(web, metadata, new RequestArgumentResolver(),
                    new DefaultErrorDecoder(), config(false), "optional", Client.class, context,
                    new NoopResilienceOperatorApplier(), null, null);
            selected.set(true);
            assertThatThrownBy(() -> handler.invoke(null, Client.class.getMethod("value"), new Object[0]))
                    .isInstanceOf(NullPointerException.class).hasMessageContaining("local response cache manager");
            assertThat(handler.responseCacheManager()).isNull();
            verify(web).mutate();
        }
    }

    private static class Fixture implements AutoCloseable {
        final GenericApplicationContext context = new GenericApplicationContext();
        final AtomicInteger dispatches = new AtomicInteger();
        final AtomicInteger filters = new AtomicInteger();
        final ReactiveHttpClientProperties.ClientConfig config;
        final WebClient web;
        final ReactiveClientInvocationHandler handler;
        final Client client;

        Fixture(Entry entry, boolean selected) {
            config = config(selected);
            context.refresh();
            web = spy(WebClient.builder().baseUrl("http://optional.invalid")
                    .filter((request, next) -> { filters.incrementAndGet(); return next.exchange(request); })
                    .exchangeFunction(request -> Mono.defer(() -> {
                        dispatches.incrementAndGet();
                        return Mono.just(ClientResponse.create(HttpStatus.OK).header("Content-Type", "text/plain").body("ok").build());
                    })).build());
            var metadata = new MethodMetadataCache();
            handler = switch (entry) {
                case STATIC -> ReactiveClientInvocationHandler.create(web, metadata, new RequestArgumentResolver(),
                        new DefaultErrorDecoder(), config, "optional", Client.class, context,
                        new NoopResilienceOperatorApplier(), TestJsonCodecs.jsonCodec(), null);
                case LEGACY -> new ReactiveClientInvocationHandler(web, metadata, new RequestArgumentResolver(),
                        new DefaultErrorDecoder(), config, "optional", context,
                        new NoopResilienceOperatorApplier(), TestJsonCodecs.jsonCodec(), null);
                case LEGACY_CONCRETE -> new ReactiveClientInvocationHandler(web, metadata, new RequestArgumentResolver(),
                        new DefaultErrorDecoder(), config, "optional", Client.class, context,
                        new NoopResilienceOperatorApplier(), TestJsonCodecs.jsonCodec(), null);
            };
            client = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[]{Client.class}, handler);
        }

        @Override public void close() {
            if (handler.responseCacheManager() != null) handler.responseCacheManager().close();
            context.close();
        }
    }
}
