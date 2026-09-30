package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.annotation.*;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientAutoConfiguration;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientObserver;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.netty.http.server.HttpServer;
import reactor.netty.resources.ConnectionProvider;
import reactor.netty.resources.LoopResources;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/** Untimed structural counts, not heap-allocation estimates or performance assertions. */
@Timeout(30)
class DefaultPathCostOwnershipTest {
    private static final String NAME = "v34-ownership";
    private static final Duration WAIT = Duration.ofSeconds(5);

    enum Profile { MINIMAL, AUTO_NO_REGISTRY, AUTO_REGISTRY, RESILIENCE_ENABLED_ONLY, OBSERVER, HOOK }

    @ParameterizedTest
    @EnumSource(Profile.class)
    void countsInvocationDiscoveryAndSubscriptionStateWithoutPreparingUnselectedFeatures(Profile profile) {
        AtomicInteger scheduled = new AtomicInteger();
        try (var fixture = new Fixture(profile)) {
            fixture.resetCounts();
            Schedulers.onScheduleHook(NAME, task -> { scheduled.incrementAndGet(); return task; });
            Mono<String> first = fixture.client.target("one", "summary", "scope");
            assertThat(fixture.dispatches).hasValue(0);
            assertThat(fixture.states).isEmpty();
            assertThat(fixture.metadata.lookups).hasValue(1);
            assertThat(fixture.resolver.resolutions).hasValue(1);
            assertDiscovery(fixture, 1);
            Map<?, ?> plans = (Map<?, ?>) ReflectionTestUtils.getField(fixture.handler, "requestPlanCache");
            assertThat(plans).hasSize(1);
            Object firstPlan = plans.values().iterator().next();

            assertThat(first.block(WAIT)).isEqualTo("ok");
            assertThat(first.block(WAIT)).isEqualTo("ok");
            assertDiscovery(fixture, 1);
            assertThat(fixture.resolver.resolutions).hasValue(1);
            assertThat(fixture.client.target("two", "detail", "other").block(WAIT)).isEqualTo("ok");
            assertDiscovery(fixture, 2);
            assertThat(fixture.metadata.lookups).hasValue(2);
            assertThat(fixture.resolver.resolutions).hasValue(2);
            assertThat(plans).hasSize(1);
            assertThat(plans.values().iterator().next()).isSameAs(firstPlan);
            assertThat(fixture.targets).containsExactly("/items/one?view=summary", "/items/one?view=summary",
                    "/items/two?view=detail");
            assertThat(fixture.dispatches).hasValue(3);
            assertThat(scheduled).hasValue(0);
            assertThat(fixture.handler.responseCacheManager()).isNull();
            assertThat((Map<?, ?>) ReflectionTestUtils.getField(fixture.handler, "cacheDecisionCache")).isEmpty();
            assertThat(ReflectionTestUtils.getField(fixture.handler, "resilienceOperatorApplier"))
                    .isExactlyInstanceOf(NoopResilienceOperatorApplier.class);

            if (profile == Profile.MINIMAL || profile == Profile.AUTO_NO_REGISTRY) {
                assertThat(fixture.states).isEmpty();
            } else {
                assertThat(fixture.states).hasSize(3).doesNotHaveDuplicates();
                assertThat(fixture.states).allSatisfy(state -> {
                    assertThat(state.attemptCount()).isEqualTo(1);
                    assertThat(state.activeAttempt()).isNull();
                    if (profile == Profile.RESILIENCE_ENABLED_ONLY) {
                        assertThat(state.terminalSnapshot()).isNull();
                    } else {
                        assertThat(state.terminalSnapshot()).isNotNull();
                    }
                });
            }
            assertThat(fixture.terminals).hasValue(profile == Profile.OBSERVER || profile == Profile.HOOK ? 3 : 0);
            assertThat(fixture.supports).hasValue(profile == Profile.HOOK ? 2 : 0);
            if (fixture.registry != null) {
                assertThat(fixture.registry.get("reactive.http.client.requests").tag("api.name", "v34.target")
                        .timer().count()).isEqualTo(3);
                assertThat(fixture.registry.getMeters()).allSatisfy(meter ->
                        assertThat(meter.getId().getName()).startsWith("reactive.http.client.requests"));
            }
            fixture.assertInactiveResources();
        } finally {
            Schedulers.resetOnScheduleHook(NAME);
        }
    }

    @Test
    void latePrototypeConsumersAreDiscoveredPerInvocationNotPerSubscription() {
        try (var fixture = new Fixture(Profile.AUTO_NO_REGISTRY)) {
            Mono<String> beforeRegistration = fixture.client.target("one", "summary", "scope");
            AtomicInteger observerCreations = new AtomicInteger();
            AtomicInteger hookCreations = new AtomicInteger();
            AtomicInteger observed = new AtomicInteger();
            AtomicInteger supported = new AtomicInteger();
            List<Integer> owners = new ArrayList<>();
            fixture.context.registerBean("lateObserver", HttpClientObserver.class, () -> {
                int owner = observerCreations.incrementAndGet();
                return event -> { observed.incrementAndGet(); owners.add(owner); };
            }, definition -> definition.setScope("prototype"));
            fixture.context.registerBean("lateHook", ReactiveHttpClientLifecycleHook.class, () -> {
                hookCreations.incrementAndGet();
                return new ReactiveHttpClientLifecycleHook() {
                    @Override public boolean supports(String client) { supported.incrementAndGet(); return true; }
                };
            }, definition -> definition.setScope("prototype"));
            assertThat(beforeRegistration.block(WAIT)).isEqualTo("ok");
            assertThat(observerCreations).hasValue(0);
            Mono<String> first = fixture.client.target("two", "detail", "scope");
            assertThat(observerCreations).hasValue(1);
            assertThat(hookCreations).hasValue(1);
            assertThat(first.block(WAIT)).isEqualTo("ok");
            assertThat(first.block(WAIT)).isEqualTo("ok");
            assertThat(fixture.client.target("three", "summary", "scope").block(WAIT)).isEqualTo("ok");
            assertThat(observerCreations).hasValue(2);
            assertThat(hookCreations).hasValue(2);
            assertThat(supported).hasValue(2);
            assertThat(observed).hasValue(3);
            assertThat(owners).containsExactly(1, 1, 2);
        }
    }

    @Test
    void inactivePolicyChecksScanTheWholeInterfaceButNotEverySubscription() {
        try (var fixture = new Fixture(Profile.AUTO_NO_REGISTRY);
             var policy = mockStatic(EffectiveCachePolicy.class, CALLS_REAL_METHODS)) {
            var config = (ReactiveHttpClientProperties.ClientConfig)
                    ReflectionTestUtils.getField(fixture.handler, "clientConfig");
            Mono<String> call = fixture.client.target("one", "summary", "scope");
            policy.verify(() -> EffectiveCachePolicy.decide(any(), same(config), anyString()), times(2));
            assertThat(call.block(WAIT)).isEqualTo("ok");
            assertThat(call.block(WAIT)).isEqualTo("ok");
            policy.verify(() -> EffectiveCachePolicy.decide(any(), same(config), anyString()), times(2));
            fixture.client.target("two", "summary", "scope");
            policy.verify(() -> EffectiveCachePolicy.decide(any(), same(config), anyString()), times(4));
            assertThat(fixture.handler.responseCacheManager()).isNull();
            assertThat(ReflectionTestUtils.getField(fixture.handler, "cacheIdentityWebClient"))
                    .isNotSameAs(ReflectionTestUtils.getField(fixture.handler, "webClient"));

            config.getCache().setPolicy("changed-after-construction");
            assertThatThrownBy(() -> fixture.client.target("three", "summary", "scope"))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("Cache work selection changed");
            assertThat(fixture.dispatches).hasValue(2);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void selectedPoolGaugesBelongToTheFactoryIndependentlyOfOrdinaryObserverExports(boolean observability) {
        var loops = LoopResources.create("v34-pool-owner", 1, true);
        var server = HttpServer.create().host("127.0.0.1").port(0).runOn(loops)
                .route(routes -> routes.get("/items/{id}", (request, response) ->
                        response.header("Content-Type", "text/plain").sendString(Mono.just("ok"))))
                .bindNow(WAIT);
        var registry = new SimpleMeterRegistry();
        var context = new AnnotationConfigApplicationContext();
        var factory = new ReactiveHttpClientFactoryBean<Client>();
        Metrics.addRegistry(registry);
        try {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource(NAME, Map.of(
                    "reactive.http.clients." + NAME + ".base-url", "http://127.0.0.1:" + server.port(),
                    "reactive.http.clients." + NAME + ".pool.metrics-enabled", true,
                    "reactive.http.observability.enabled", observability)));
            context.registerBean(SimpleMeterRegistry.class, () -> registry);
            context.register(JacksonAutoConfiguration.class, ReactiveHttpClientAutoConfiguration.class);
            context.refresh();
            factory.setType(Client.class);
            factory.setApplicationContext(context);
            Client client = factory.getObject();
            assertThat(poolMeterCount(registry)).isZero();
            assertThat(client.target("one", "summary", "scope").block(WAIT)).isEqualTo("ok");
            assertThat(poolMeterCount(registry)).isEqualTo(4);
            assertThat(registry.find("reactive.http.client.requests").timer() != null).isEqualTo(observability);
            factory.destroy();
            assertThat(poolMeterCount(registry)).isZero();
            assertThat(registry.find("reactive.http.client.requests").timer() != null).isEqualTo(observability);
        } finally {
            factory.destroy();
            Metrics.removeRegistry(registry);
            context.close();
            registry.close();
            server.disposeNow(WAIT);
            loops.disposeLater(Duration.ZERO, WAIT).block(WAIT);
        }
    }

    private static long poolMeterCount(SimpleMeterRegistry registry) {
        return registry.getMeters().stream().filter(meter -> meter.getId().getName()
                .startsWith("reactive.http.client.connection.pool.")).count();
    }

    @ParameterizedTest
    @CsvSource({"MINIMAL,false", "MINIMAL,true", "RESILIENCE_ENABLED_ONLY,false", "RESILIENCE_ENABLED_ONLY,true"})
    void resourceBodiesHaveOneInvocationOwnerThroughCompletionOrAcknowledgedCancellation(Profile profile,
                                                                                           boolean cancel)
            throws InterruptedException {
        try (var fixture = new Fixture(profile)) {
            var body = new CountingStream();
            fixture.pending = cancel;
            Mono<String> call = fixture.client.upload(body);
            assertThat(body.closes).hasValue(0);
            assertThat(fixture.dispatches).hasValue(0);
            if (cancel) {
                var subscription = call.subscribe();
                assertThat(fixture.attached.await(5, TimeUnit.SECONDS)).isTrue();
                subscription.dispose();
                assertThat(fixture.cancelled.await(5, TimeUnit.SECONDS)).isTrue();
            } else {
                assertThat(call.block(WAIT)).isEqualTo("ok");
            }
            assertThat(body.closed.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(body.closes).hasValue(1);
            assertThat(fixture.dispatches).hasValue(1);
            assertThat(fixture.states).allSatisfy(state -> assertThat(state.activeAttempt()).isNull());
        }
    }

    private static void assertDiscovery(Fixture fixture, int invocations) {
        verify(fixture.context.observers, times(invocations)).orderedStream();
        verify(fixture.context.hooks, times(invocations)).orderedStream();
        boolean hasObserver = fixture.profile == Profile.OBSERVER || fixture.profile == Profile.AUTO_REGISTRY;
        verify(fixture.context.observers, times(hasObserver ? 0 : invocations)).getIfAvailable();
    }

    private static final class Fixture implements AutoCloseable {
        final Profile profile;
        final CountingContext context = new CountingContext();
        final CountingMetadata metadata = new CountingMetadata();
        final CountingResolver resolver = new CountingResolver();
        final AtomicInteger dispatches = new AtomicInteger();
        final AtomicInteger terminals = new AtomicInteger();
        final AtomicInteger supports = new AtomicInteger();
        final List<SubscriptionReportingState> states = new ArrayList<>();
        final List<String> targets = new ArrayList<>();
        final CountDownLatch attached = new CountDownLatch(1);
        final CountDownLatch cancelled = new CountDownLatch(1);
        final SimpleMeterRegistry registry;
        final ReactiveHttpClientFactoryBean<Client> factory;
        final ReactiveClientInvocationHandler handler;
        final Client client;
        boolean pending;

        Fixture(Profile profile) {
            this.profile = profile;
            registry = profile == Profile.AUTO_REGISTRY ? new SimpleMeterRegistry() : null;
            if (profile == Profile.MINIMAL) {
                context.refresh();
                factory = null;
                handler = ReactiveClientInvocationHandler.create(WebClient.builder().baseUrl("http://fixture.invalid")
                                .exchangeFunction(this::exchange).build(), metadata, resolver, new DefaultErrorDecoder(),
                        new ReactiveHttpClientProperties.ClientConfig(), NAME, Client.class, context,
                        new NoopResilienceOperatorApplier(), null, new ReactiveHttpClientProperties.ObservabilityConfig());
                client = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[]{Client.class}, handler);
            } else {
                context.getEnvironment().getPropertySources().addFirst(new MapPropertySource(NAME, Map.of(
                        "reactive.http.clients." + NAME + ".base-url", "http://fixture.invalid",
                        "reactive.http.clients." + NAME + ".resilience.enabled", profile == Profile.RESILIENCE_ENABLED_ONLY,
                        "reactive.http.observability.enabled", profile != Profile.OBSERVER && profile != Profile.HOOK)));
                context.registerBean(MethodMetadataCache.class, () -> metadata);
                if (registry != null) context.registerBean(SimpleMeterRegistry.class, () -> registry);
                if (profile == Profile.OBSERVER) context.registerBean(HttpClientObserver.class, () -> event -> terminals.incrementAndGet());
                if (profile == Profile.HOOK) context.registerBean(ReactiveHttpClientLifecycleHook.class,
                        () -> new ReactiveHttpClientLifecycleHook() {
                            @Override public boolean supports(String client) { supports.incrementAndGet(); return true; }
                            @Override public void onSuccess(ReactiveHttpClientLifecycleContext event) { terminals.incrementAndGet(); }
                        });
                context.registerBean(ReactiveHttpClientCustomizer.class, () -> builder -> builder.exchangeFunction(this::exchange));
                context.register(JacksonAutoConfiguration.class, ReactiveHttpClientAutoConfiguration.class);
                context.refresh();
                factory = new ReactiveHttpClientFactoryBean<>();
                factory.setType(Client.class);
                factory.setApplicationContext(context);
                client = factory.getObject();
                handler = (ReactiveClientInvocationHandler) Proxy.getInvocationHandler(client);
                ReflectionTestUtils.setField(handler, "argumentResolver", resolver);
            }
        }

        void resetCounts() {
            clearInvocations(context.observers, context.hooks);
            metadata.lookups.set(0);
            resolver.resolutions.set(0);
        }

        Mono<ClientResponse> exchange(ClientRequest request) {
            return Mono.deferContextual(ctx -> {
                dispatches.incrementAndGet();
                targets.add(request.url().getRawPath() + (request.url().getRawQuery() != null ? "?" + request.url().getRawQuery() : ""));
                ctx.stream().map(Map.Entry::getValue).filter(SubscriptionReportingState.class::isInstance)
                        .map(SubscriptionReportingState.class::cast).forEach(states::add);
                if (pending) return Mono.<ClientResponse>never().doOnSubscribe(ignored -> attached.countDown())
                        .doOnCancel(cancelled::countDown);
                return Mono.just(ClientResponse.create(HttpStatus.OK).header("Content-Type", "text/plain").body("ok").build());
            });
        }

        void assertInactiveResources() {
            if (factory == null) return;
            assertThat(factory.responseCacheSnapshot()).isNull();
            assertThat(factory.responseCacheWorkSnapshot()).isNull();
            assertThat(ReflectionTestUtils.getField(factory, "tokenServiceConnectionProvider")).isNull();
            assertThat(ReflectionTestUtils.getField(factory, "connectionPoolMeterRegistrar")).isNull();
            assertThat((Set<?>) ReflectionTestUtils.getField(factory, "ownedConnections")).isEmpty();
            assertThat(ReflectionTestUtils.getField(factory, "connectionProvider")).isInstanceOf(ConnectionProvider.class);
        }

        @Override public void close() {
            try {
                if (factory != null) {
                    factory.destroy();
                    assertThat(((ConnectionProvider) ReflectionTestUtils.getField(factory, "connectionProvider")).isDisposed()).isTrue();
                }
            } finally {
                context.close();
                if (registry != null) {
                    registry.close();
                    assertThat(registry.isClosed()).isTrue();
                }
            }
        }
    }

    private static final class CountingContext extends AnnotationConfigApplicationContext {
        ObjectProvider<HttpClientObserver> observers;
        ObjectProvider<ReactiveHttpClientLifecycleHook> hooks;

        @Override @SuppressWarnings("unchecked")
        public <T> ObjectProvider<T> getBeanProvider(Class<T> type) {
            if (type == HttpClientObserver.class) {
                if (observers == null) observers = spy(super.getBeanProvider(HttpClientObserver.class));
                return (ObjectProvider<T>) observers;
            }
            if (type == ReactiveHttpClientLifecycleHook.class) {
                if (hooks == null) hooks = spy(super.getBeanProvider(ReactiveHttpClientLifecycleHook.class));
                return (ObjectProvider<T>) hooks;
            }
            return super.getBeanProvider(type);
        }
    }

    private static final class CountingMetadata extends MethodMetadataCache {
        final AtomicInteger lookups = new AtomicInteger();
        @Override public MethodMetadata get(Method method) { lookups.incrementAndGet(); return super.get(method); }
    }

    private static final class CountingResolver extends RequestArgumentResolver {
        final AtomicInteger resolutions = new AtomicInteger();
        @Override public ResolvedArgs resolve(RequestPlan plan, Object[] args) {
            resolutions.incrementAndGet();
            return super.resolve(plan, args);
        }
    }

    private static final class CountingStream extends ByteArrayInputStream {
        final AtomicInteger closes = new AtomicInteger();
        final CountDownLatch closed = new CountDownLatch(1);
        CountingStream() { super(new byte[]{1, 2}); }
        @Override public void close() { closes.incrementAndGet(); closed.countDown(); }
    }

    @ReactiveHttpClient(name = NAME)
    interface Client {
        @GET("/items/{id}") @ApiName("v34.target") Mono<String> target(@PathVar("id") String id,
                @QueryParam("view") String view, @HeaderParam("X-Scope") String scope);
        @POST("/upload") @ApiName("v34.upload") Mono<String> upload(@Body InputStream body);
    }
}
