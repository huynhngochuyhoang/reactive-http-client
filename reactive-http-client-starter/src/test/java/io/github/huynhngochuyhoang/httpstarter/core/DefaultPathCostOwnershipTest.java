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
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

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
    void absentBodiesSeparateInvocationOwnersFromSubscriptionState(Profile profile) throws ClassNotFoundException {
        Class<?> ownerType = Class.forName(ReactiveClientInvocationHandler.class.getName() + "$RequestBodyOwnership");
        try (var owners = mockConstruction(ownerType,
                (owner, context) -> assertThat(context.arguments().getLast()).isNull());
             var fixture = new Fixture(profile)) {
            Mono<String> get = fixture.client.target("one", "summary", "scope");
            Mono<String> nullUpload = fixture.client.upload(null);
            // C002's allocation candidate was rejected; holders remain invocation-scoped.
            assertThat(owners.constructed()).hasSize(2);
            assertThat(fixture.dispatches).hasValue(0);
            assertThat(get.block(WAIT)).isEqualTo("ok");
            assertThat(get.block(WAIT)).isEqualTo("ok");
            assertThat(nullUpload.block(WAIT)).isEqualTo("ok");
            assertThat(nullUpload.block(WAIT)).isEqualTo("ok");
            assertThat(owners.constructed()).hasSize(2);
            assertThat(fixture.dispatches).hasValue(4);
            if (profile == Profile.MINIMAL || profile == Profile.AUTO_NO_REGISTRY) {
                assertThat(fixture.states).isEmpty();
            } else {
                assertThat(fixture.states).hasSize(4).doesNotHaveDuplicates();
                assertThat(fixture.states).allSatisfy(state -> {
                    assertThat(state.attemptCount()).isEqualTo(1);
                    assertThat(state.activeAttempt()).isNull();
                });
            }
        }
    }

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
    void concurrentFirstInvocationsStillMaterializeIndependentPrototypeConsumers() throws Exception {
        try (var fixture = new Fixture(Profile.AUTO_NO_REGISTRY);
             var executor = Executors.newFixedThreadPool(4)) {
            AtomicInteger creations = new AtomicInteger();
            CountDownLatch preparing = new CountDownLatch(4);
            List<Integer> owners = new ArrayList<>();
            fixture.context.registerBean("concurrentObserver", HttpClientObserver.class, () -> {
                int owner = creations.incrementAndGet();
                preparing.countDown();
                try {
                    assertThat(preparing.await(5, TimeUnit.SECONDS)).isTrue();
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(error);
                }
                return event -> owners.add(owner);
            }, definition -> definition.setScope("prototype"));
            fixture.resetCounts();
            var tasks = java.util.stream.IntStream.range(0, 4).<Callable<Mono<String>>>mapToObj(
                    index -> () -> fixture.client.target("caller-" + index, "summary", "scope")).toList();
            var calls = executor.invokeAll(tasks, 10, TimeUnit.SECONDS);
            assertThat(creations).hasValue(4);
            assertThat(fixture.dispatches).hasValue(0);
            verify(fixture.context.observers, times(4)).orderedStream();
            verify(fixture.context.observers, never()).getIfAvailable();
            verify(fixture.context.hooks, times(4)).orderedStream();
            for (var call : calls) {
                Mono<String> publisher = call.get(5, TimeUnit.SECONDS);
                assertThat(publisher.block(WAIT)).isEqualTo("ok");
                assertThat(publisher.block(WAIT)).isEqualTo("ok");
            }
            assertThat(creations).hasValue(4);
            assertThat(owners).hasSize(8);
            assertThat(owners.stream().distinct().toList()).hasSize(4);
            for (int index = 0; index < owners.size(); index += 2) {
                assertThat(owners.get(index)).isEqualTo(owners.get(index + 1));
            }
            assertThat(fixture.states).hasSize(8).doesNotHaveDuplicates();
            assertThat(fixture.handler.responseCacheManager()).isNull();
            fixture.assertInactiveResources();
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

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void customEmptyObserverStreamUsesFallbackWithoutClosingOrRediscoveringOnSubscription(boolean nullStream) {
        try (var fixture = new Fixture(Profile.MINIMAL)) {
            List<String> events = new ArrayList<>();
            doAnswer(ignored -> {
                events.add("stream");
                return nullStream ? null : Stream.<HttpClientObserver>empty().onClose(() -> events.add("close"));
            }).when(fixture.context.observers).orderedStream();
            doAnswer(ignored -> {
                events.add("fallback");
                return (HttpClientObserver) event -> events.add("terminal");
            }).when(fixture.context.observers).getIfAvailable();
            fixture.resetCounts();
            Mono<String> call = fixture.client.target("one", "summary", "scope");
            assertThat(events).containsExactly("stream", "fallback");
            assertThat(call.block(WAIT)).isEqualTo("ok");
            assertThat(call.block(WAIT)).isEqualTo("ok");
            assertThat(events).containsExactly("stream", "fallback", "terminal", "terminal");
            assertDiscovery(fixture, 1);
        }
    }

    @Test
    void orderedPrototypeConsumersAreMaterializedOncePerInvocationAndCapturedByColdPublishers() {
        try (var fixture = new Fixture(Profile.MINIMAL)) {
            List<String> events = new ArrayList<>();
            AtomicInteger created = new AtomicInteger();
            Mono<String> before = fixture.client.target("before", "summary", "scope");
            for (int order : new int[]{2, 0, 1}) {
                fixture.context.registerBean("observer" + order, HttpClientObserver.class,
                        () -> new OrderedObserver(order, created.incrementAndGet(), events),
                        definition -> definition.setScope("prototype"));
                fixture.context.registerBean("hook" + order, ReactiveHttpClientLifecycleHook.class,
                        () -> new OrderedHook(order, created.incrementAndGet(), events),
                        definition -> definition.setScope("prototype"));
            }
            assertThat(before.block(WAIT)).isEqualTo("ok");
            assertThat(created).hasValue(0);
            Mono<String> first = fixture.client.target("first", "summary", "scope");
            assertThat(created).hasValue(6);
            assertThat(events).containsExactly("support:0", "support:1", "support:2");
            events.clear();
            assertThat(first.block(WAIT)).isEqualTo("ok");
            List<String> firstTerminal = List.copyOf(events);
            assertThat(firstTerminal.stream().filter(value -> value.startsWith("observer:"))
                    .map(value -> value.substring(0, value.lastIndexOf(':'))).toList())
                    .containsExactly("observer:0", "observer:1", "observer:2");
            assertThat(firstTerminal.stream().filter(value -> value.startsWith("hook:"))
                    .map(value -> value.substring(0, value.lastIndexOf(':'))).toList())
                    .containsExactly("hook:0", "hook:1", "hook:2");
            events.clear();
            assertThat(first.block(WAIT)).isEqualTo("ok");
            assertThat(events).containsExactlyElementsOf(firstTerminal);
            assertThat(created).hasValue(6);
            events.clear();
            assertThat(fixture.client.target("second", "summary", "scope").block(WAIT)).isEqualTo("ok");
            assertThat(created).hasValue(12);
            assertThat(events).contains("support:0", "support:1", "support:2")
                    .doesNotContainAnyElementsOf(firstTerminal);
        }
    }

    private record OrderedObserver(int order, int owner, List<String> events)
            implements HttpClientObserver, org.springframework.core.Ordered {
        @Override public int getOrder() { return order; }
        @Override public void record(io.github.huynhngochuyhoang.httpstarter.observability.HttpClientObserverEvent event) {
            events.add("observer:" + order + ":" + owner);
        }
    }

    private record OrderedHook(int order, int owner, List<String> events)
            implements ReactiveHttpClientLifecycleHook, org.springframework.core.Ordered {
        @Override public int getOrder() { return order; }
        @Override public boolean supports(String name) { events.add("support:" + order); return NAME.equals(name); }
        @Override public void onSuccess(ReactiveHttpClientLifecycleContext event) { events.add("hook:" + order + ":" + owner); }
    }

    @ParameterizedTest
    @CsvSource({"false,1", "false,3", "true,1", "true,3"})
    void customObserverStreamsKeepOrderDuplicatesAndFullTraversal(boolean parallel, int count) {
        try (var fixture = new Fixture(Profile.MINIMAL)) {
            List<String> terminals = new ArrayList<>();
            List<Integer> materialized = java.util.Collections.synchronizedList(new ArrayList<>());
            AtomicInteger closed = new AtomicInteger();
            HttpClientObserver repeated = event -> terminals.add("repeated");
            HttpClientObserver tail = event -> terminals.add("tail");
            List<HttpClientObserver> selected = count == 1 ? List.of(repeated) : List.of(repeated, repeated, tail);
            doAnswer(ignored -> {
                Stream<HttpClientObserver> stream = java.util.stream.IntStream.range(0, selected.size())
                        .mapToObj(index -> { materialized.add(index); return selected.get(index); });
                return (parallel ? stream.parallel() : stream).onClose(closed::incrementAndGet);
            }).when(fixture.context.observers).orderedStream();
            doAnswer(ignored -> { throw new AssertionError("nonempty stream must not use fallback"); })
                    .when(fixture.context.observers).getIfAvailable();
            Mono<String> call = fixture.client.target("one", "summary", "scope");
            assertThat(materialized).containsExactlyInAnyOrderElementsOf(
                    java.util.stream.IntStream.range(0, count).boxed().toList());
            assertThat(terminals).isEmpty();
            assertThat(call.block(WAIT)).isEqualTo("ok");
            assertThat(terminals).containsExactlyElementsOf(count == 1 ? List.of("repeated")
                    : List.of("repeated", "repeated", "tail"));
            assertThat(closed).hasValue(0);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void observerTraversalFailureIsSynchronousAndNeverFallsBack(boolean fallbackFailure) {
        try (var fixture = new Fixture(Profile.MINIMAL)) {
            var failure = new IllegalArgumentException("synthetic discovery failure");
            AtomicInteger visited = new AtomicInteger();
            AtomicInteger closed = new AtomicInteger();
            doAnswer(ignored -> fallbackFailure ? Stream.empty() : Stream.<HttpClientObserver>generate(() -> {
                if (visited.incrementAndGet() == 2) throw failure;
                return event -> {};
            }).limit(2).onClose(closed::incrementAndGet)).when(fixture.context.observers).orderedStream();
            doThrow(failure).when(fixture.context.observers).getIfAvailable();
            fixture.resetCounts();
            assertThatThrownBy(() -> fixture.client.target("one", "summary", "scope")).isSameAs(failure);
            verify(fixture.context.observers, times(fallbackFailure ? 1 : 0)).getIfAvailable();
            verifyNoInteractions(fixture.context.hooks);
            assertThat(visited).hasValue(fallbackFailure ? 0 : 2);
            assertThat(fixture.dispatches).hasValue(0);
            assertThat(closed).hasValue(0);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void nullObserverSingletonDiffersFromNullInCompositeAfterFullTraversal(boolean composite) {
        try (var fixture = new Fixture(Profile.MINIMAL)) {
            AtomicInteger visited = new AtomicInteger();
            doAnswer(ignored -> Stream.<HttpClientObserver>of(null, event -> {})
                    .limit(composite ? 2 : 1).peek(value -> visited.incrementAndGet()))
                    .when(fixture.context.observers).orderedStream();
            fixture.resetCounts();
            if (composite) {
                assertThatThrownBy(() -> fixture.client.target("one", "summary", "scope"))
                        .isInstanceOf(NullPointerException.class);
                assertThat(fixture.dispatches).hasValue(0);
            } else {
                assertThat(fixture.client.target("one", "summary", "scope").block(WAIT)).isEqualTo("ok");
            }
            assertThat(visited).hasValue(composite ? 2 : 1);
            verify(fixture.context.observers, never()).getIfAvailable();
        }
    }

    @ParameterizedTest
    @CsvSource({"false,0", "false,1", "false,2", "true,0", "true,1", "true,2"})
    void hookSelectionPreservesSupportFilteringOrderingAndDuplicateCallbacks(boolean parallel, int accepted) {
        try (var fixture = new Fixture(Profile.MINIMAL)) {
            List<String> terminals = new ArrayList<>();
            List<String> visited = java.util.Collections.synchronizedList(new ArrayList<>());
            AtomicInteger closed = new AtomicInteger();
            ReactiveHttpClientLifecycleHook repeated = new ReactiveHttpClientLifecycleHook() {
                @Override public boolean supports(String name) {
                    visited.add("accepted");
                    assertThat(name).isEqualTo(NAME);
                    return true;
                }
                @Override public void onSuccess(ReactiveHttpClientLifecycleContext event) { terminals.add("ok"); }
            };
            ReactiveHttpClientLifecycleHook rejected = new ReactiveHttpClientLifecycleHook() {
                @Override public boolean supports(String name) { visited.add("rejected"); return false; }
            };
            ReactiveHttpClientLifecycleHook broken = new ReactiveHttpClientLifecycleHook() {
                @Override public boolean supports(String name) { visited.add("broken"); throw new IllegalStateException("synthetic"); }
            };
            List<ReactiveHttpClientLifecycleHook> hooks = new ArrayList<>(List.of(rejected, broken));
            for (int i = 0; i < accepted; i++) hooks.add(repeated);
            doAnswer(ignored -> (parallel ? hooks.parallelStream() : hooks.stream()).onClose(closed::incrementAndGet))
                    .when(fixture.context.hooks).orderedStream();
            fixture.resetCounts();
            Mono<String> call = fixture.client.target("one", "summary", "scope");
            assertThat(visited).hasSize(2 + accepted).contains("rejected", "broken");
            assertThat(call.block(WAIT)).isEqualTo("ok");
            assertThat(call.block(WAIT)).isEqualTo("ok");
            assertThat(terminals).hasSize(2 * accepted).allMatch("ok"::equals);
            assertThat(visited).hasSize(2 + accepted);
            assertDiscovery(fixture, 1);
            assertThat(closed).hasValue(0);
        }
    }

    @ParameterizedTest
    @CsvSource({"null-stream", "null-element", "stream-failure", "fatal-support"})
    void hookCustomProviderFailureAndNullBoundariesRemainSynchronous(String mode) {
        try (var fixture = new Fixture(Profile.MINIMAL)) {
            var failure = new AssertionError("synthetic fatal failure");
            doAnswer(ignored -> switch (mode) {
                case "null-stream" -> null;
                case "null-element" -> Stream.of((ReactiveHttpClientLifecycleHook) null);
                case "stream-failure" -> throw failure;
                default -> Stream.of(new ReactiveHttpClientLifecycleHook() {
                    @Override public boolean supports(String name) { throw failure; }
                });
            }).when(fixture.context.hooks).orderedStream();
            if (mode.equals("null-stream")) {
                assertThat(fixture.client.target("one", "summary", "scope").block(WAIT)).isEqualTo("ok");
            } else if (mode.equals("null-element")) {
                assertThatThrownBy(() -> fixture.client.target("one", "summary", "scope"))
                        .isInstanceOf(NullPointerException.class);
            } else {
                assertThatThrownBy(() -> fixture.client.target("one", "summary", "scope")).isSameAs(failure);
            }
            verify(fixture.context.hooks, never()).getIfAvailable();
            assertThat(fixture.dispatches).hasValue(mode.equals("null-stream") ? 1 : 0);
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
