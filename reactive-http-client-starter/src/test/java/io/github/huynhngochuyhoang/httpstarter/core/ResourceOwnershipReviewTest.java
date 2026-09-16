package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.annotation.GET;
import io.github.huynhngochuyhoang.httpstarter.annotation.ReactiveHttpClient;
import io.github.huynhngochuyhoang.httpstarter.auth.AuthContext;
import io.github.huynhngochuyhoang.httpstarter.auth.AuthProvider;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.server.HttpServer;
import reactor.netty.resources.ConnectionProvider;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@Timeout(30)
class ResourceOwnershipReviewTest {
    private static final String NAME = "ownership-review";
    private static final Duration WAIT = Duration.ofSeconds(5);

    @ParameterizedTest
    @CsvSource({"false,false", "false,true", "true,false", "true,true"})
    void rejectedPublicHandlerConstructionReleasesOnlyItsNewManager(boolean liveOwner, boolean telemetry)
            throws Exception {
        var context = new GenericApplicationContext();
        var registry = new SimpleMeterRegistry();
        context.getBeanFactory().registerSingleton("registry", registry);
        context.refresh();
        var properties = properties(true);
        properties.getObservability().getCache().setEnabled(telemetry);
        var config = properties.getClients().get(NAME);
        ReactiveClientInvocationHandler existing = null;
        try {
            if (liveOwner) {
                existing = handler(context, config, properties);
            }
            var metersBefore = List.copyOf(registry.getMeters());
            var ownersBefore = metricOwners(registry);
            config.setAuthProvider("applicationAuth");
            for (int attempt = 1; attempt <= 3; attempt++) {
                assertThatThrownBy(() -> handler(context, config, properties))
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("caching").hasMessageContaining("AuthProvider");
                assertThat(registry.getMeters()).containsExactlyInAnyOrderElementsOf(metersBefore);
                assertThat(metricOwners(registry)).containsExactlyInAnyOrderElementsOf(ownersBefore);
                if (liveOwner && telemetry) {
                    assertThat(registry.get(LocalResponseCacheMetrics.PREFIX + ".maximum.entries")
                            .gauge().value()).isEqualTo(16);
                }
            }
            if (existing != null) { assertThat(existing.responseCacheManager().snapshot().closed()).isFalse(); }
            if (existing != null) { existing.responseCacheManager().close(); }
            context.close();
            assertThat(metricOwners(registry)).isEmpty();
            assertThat(registry.getMeters()).isEmpty();
        } finally {
            if (existing != null) { existing.responseCacheManager().close(); }
            context.close();
            // Clean up even when this regression is run against the leaking baseline.
            metricOwners(registry).forEach(LocalResponseCacheMetrics::close);
            assertThat(registry.getMeters()).isEmpty();
            registry.close();
        }
    }

    @ParameterizedTest
    @CsvSource({"false,false", "false,true", "true,false", "true,true"})
    void lateHandlerAssemblyFailurePreservesTheFailureAndExternalOwners(boolean telemetry, boolean fatal)
            throws Exception {
        var registry = new SimpleMeterRegistry();
        try (var context = new GenericApplicationContext()) {
            context.getBeanFactory().registerSingleton("registry", registry);
            context.refresh();
            var properties = properties(true);
            properties.getObservability().getCache().setEnabled(telemetry);
            var config = properties.getClients().get(NAME);
            var existing = handler(context, config, properties);
            var metersBefore = List.copyOf(registry.getMeters());
            var ownersBefore = metricOwners(registry);
            Throwable sentinel = fatal ? new AssertionError("assembly sentinel") : new IllegalStateException("assembly sentinel");
            AtomicInteger mutations = new AtomicInteger();
            WebClient supplied = spy(WebClient.builder().baseUrl("http://localhost")
                    .exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.OK).body("ok").build()))
                    .build());
            doAnswer(invocation -> {
                mutations.incrementAndGet();
                // Creation has acquired a new lease before this later assembly boundary fails.
                assertThat(metricOwners(registry)).hasSize(telemetry ? 2 : 0);
                throw sentinel;
            }).when(supplied).mutate();
            AuthProvider auth = mock(AuthProvider.class);
            try {
                assertThatThrownBy(() -> ReactiveClientInvocationHandler.create(supplied,
                        new MethodMetadataCache(), new RequestArgumentResolver(), new DefaultErrorDecoder(),
                        config, NAME, Client.class, context, new NoopResilienceOperatorApplier(), null,
                        properties.getObservability(), auth, "http://localhost")).isSameAs(sentinel);
                assertThat(mutations).hasValue(1);
                assertThat(registry.getMeters()).containsExactlyInAnyOrderElementsOf(metersBefore);
                assertThat(metricOwners(registry)).containsExactlyInAnyOrderElementsOf(ownersBefore);
                assertThat(existing.responseCacheManager().snapshot().closed()).isFalse();
                assertThat(supplied.get().uri("/still-owned").retrieve().bodyToMono(String.class).block(WAIT)).isEqualTo("ok");
                verifyNoInteractions(auth);
                when(auth.getAuth(any())).thenReturn(Mono.just(AuthContext.empty()));
                assertThat(auth.getAuth(null).block(WAIT)).isNotNull();
                assertThat(registry.counter("application.counter").count()).isZero();
            } finally {
                existing.responseCacheManager().close();
                metricOwners(registry).forEach(LocalResponseCacheMetrics::close);
            }
        } finally {
            registry.close();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void successfulConstructionTransfersCacheOwnershipToTheReturnedHandler(boolean caching) throws Exception {
        var registry = new SimpleMeterRegistry();
        try (var context = new GenericApplicationContext()) {
            context.getBeanFactory().registerSingleton("registry", registry);
            context.refresh();
            var properties = properties(caching);
            AtomicInteger dispatches = new AtomicInteger();
            WebClient webClient = WebClient.builder().baseUrl("http://localhost")
                    .exchangeFunction(request -> {
                        dispatches.incrementAndGet();
                        return Mono.just(ClientResponse.create(HttpStatus.OK).body("ok").build());
                    }).build();
            var handler = ReactiveClientInvocationHandler.create(webClient, new MethodMetadataCache(),
                    new RequestArgumentResolver(), new DefaultErrorDecoder(), properties.getClients().get(NAME),
                    NAME, Client.class, context, new NoopResilienceOperatorApplier(), null, properties.getObservability());
            try {
                Client client = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[]{Client.class}, handler);
                assertThat(client.get().block(WAIT)).isEqualTo("ok");
                assertThat(client.get().block(WAIT)).isEqualTo("ok");
                assertThat(dispatches).hasValue(caching ? 1 : 2);
                assertThat(metricOwners(registry)).hasSize(caching ? 1 : 0);
            } finally {
                if (handler.responseCacheManager() != null) { handler.responseCacheManager().close(); }
            }
            assertThat(metricOwners(registry)).isEmpty();
            assertThat(registry.getMeters()).isEmpty();
        } finally {
            registry.close();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void cleanupFailureIsSuppressedWithoutReplacingAssemblyFailure(boolean fatal) {
        try (var context = new GenericApplicationContext();
             var managers = mockStatic(LocalResponseCacheManager.class)) {
            context.refresh();
            var properties = properties(true);
            var config = properties.getClients().get(NAME);
            var metadata = new MethodMetadataCache();
            var manager = mock(LocalResponseCacheManager.class);
            managers.when(() -> LocalResponseCacheManager.createForClient(Client.class, NAME, metadata,
                    config, context.getClassLoader(), properties.getObservability(), null)).thenReturn(manager);
            Throwable original = fatal ? new AssertionError("assembly") : new IllegalStateException("assembly");
            Throwable cleanup = fatal ? new AssertionError("cleanup") : new IllegalStateException("cleanup");
            WebClient client = mock(WebClient.class);
            when(client.mutate()).thenThrow(original);
            doThrow(cleanup).when(manager).close();
            assertThatThrownBy(() -> ReactiveClientInvocationHandler.create(client, metadata,
                    new RequestArgumentResolver(), new DefaultErrorDecoder(), config, NAME, Client.class,
                    context, new NoopResilienceOperatorApplier(), null, properties.getObservability()))
                    .isSameAs(original).satisfies(error -> assertThat(error.getSuppressed()).containsExactly(cleanup));
            verify(manager).close();
        }
    }

    @Test
    void selectedCacheDependencyFailureThroughPublicCreationLeavesNoOwner() throws Exception {
        var registry = new SimpleMeterRegistry();
        try (var context = new GenericApplicationContext()) {
            context.setClassLoader(new org.springframework.boot.test.context.FilteredClassLoader("com.github.benmanes.caffeine"));
            context.getBeanFactory().registerSingleton("registry", registry);
            context.refresh();
            var properties = properties(true);
            for (int attempt = 0; attempt < 3; attempt++) {
                assertThatThrownBy(() -> handler(context, properties.getClients().get(NAME), properties))
                        .isInstanceOf(IllegalStateException.class).hasMessageContaining("Caffeine");
                assertThat(metricOwners(registry)).isEmpty();
                assertThat(registry.getMeters()).isEmpty();
            }
        } finally {
            registry.close();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void lateFactoryFailureIsDisposedBySpringOrTheDirectCaller(boolean springOwned) throws Exception {
        var context = new GenericApplicationContext();
        var properties = properties(false);
        context.registerBean(ReactiveHttpClientProperties.class, () -> properties);
        context.registerBean("failingCustomizer", ReactiveHttpClientCustomizer.class,
                () -> builder -> { throw new IllegalStateException("assembly sentinel"); });
        ReactiveHttpClientFactoryBean<Client> factory;
        if (springOwned) {
            var definition = new RootBeanDefinition(ReactiveHttpClientFactoryBean.class);
            definition.getPropertyValues().add("type", Client.class);
            definition.setLazyInit(true);
            context.registerBeanDefinition("client", definition);
        }
        context.refresh();
        if (springOwned) {
            @SuppressWarnings("unchecked")
            var registered = (ReactiveHttpClientFactoryBean<Client>) context.getBean("&client");
            factory = registered;
        } else {
            factory = new ReactiveHttpClientFactoryBean<>();
            factory.setType(Client.class);
            factory.setApplicationContext(context);
        }
        try {
            assertThatThrownBy(() -> {
                if (springOwned) { context.getBean("client"); } else { factory.getObject(); }
            }).hasStackTraceContaining("assembly sentinel");
            ConnectionProvider provider = (ConnectionProvider) field(factory, "connectionProvider");
            assertThat(provider).isNotNull();
            assertThat(factory.responseCacheSnapshot()).isNull();
            // A new pool is lazy. Verify disposal delegation, not isDisposed() on an unused pool.
            ConnectionProvider tracked = spy(provider);
            setField(factory, "connectionProvider", tracked);
            context.close();
            if (!springOwned) {
                verifyNoInteractions(tracked);
                factory.destroy();
            }
            verify(tracked).disposeLater();
        } finally {
            context.close();
            factory.destroy();
        }
    }

    @Test
    void earlyValidationDoesNotAcquireAConnectionProvider() throws Exception {
        var registry = new SimpleMeterRegistry();
        try (var context = new GenericApplicationContext()) {
            var properties = properties(true);
            properties.getClients().get(NAME).setBaseUrl("relative");
            context.getBeanFactory().registerSingleton("registry", registry);
            context.registerBean(ReactiveHttpClientProperties.class, () -> properties);
            context.refresh();
            AtomicInteger registrations = new AtomicInteger();
            registry.config().onMeterAdded(ignored -> registrations.incrementAndGet());
            var factory = new ReactiveHttpClientFactoryBean<Client>();
            factory.setType(Client.class);
            factory.setApplicationContext(context);
            try {
                assertThat(registry.getMeters()).isEmpty();
                assertThat(metricOwners(registry)).isEmpty();
                assertThatThrownBy(factory::getObject).isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("baseUrl");
                assertThat(field(factory, "connectionProvider")).isNull();
                assertThat(field(factory, "tokenServiceConnectionProvider")).isNull();
                assertThat(factory.responseCacheSnapshot()).isNull();
                assertThat(registrations).hasValue(0);
                assertThat(registry.getMeters()).isEmpty();
                assertThat(metricOwners(registry)).isEmpty();

                // The same selected policy must allocate observable cache resources once the URL is valid.
                properties.getClients().get(NAME).setBaseUrl("http://localhost");
                assertThat(factory.getObject()).isNotNull();
                assertThat(registrations.get()).isPositive();
                assertThat(metricOwners(registry)).hasSize(1);
                assertThat(registry.get(LocalResponseCacheMetrics.PREFIX + ".maximum.entries")
                        .gauge().value()).isEqualTo(16);
            } finally {
                factory.destroy();
                // Avoid leaking an unassigned manager if this regression assertion fails in a future change.
                metricOwners(registry).forEach(LocalResponseCacheMetrics::close);
            }
        } finally {
            registry.close();
        }
    }

    @Test
    void replacementConnectorStaysApplicationOwnedAfterFactoryDestroy() throws Exception {
        AtomicInteger dispatches = new AtomicInteger();
        var server = HttpServer.create().host("127.0.0.1").port(0)
                .handle((request, response) -> {
                    dispatches.incrementAndGet();
                    return response.sendString(Mono.just("ok"));
                }).bindNow(WAIT);
        var provider = ConnectionProvider.builder("v32-external-connector").maxConnections(1).build();
        var context = new GenericApplicationContext();
        var factory = new ReactiveHttpClientFactoryBean<Client>();
        try {
            var properties = properties(false);
            String url = "http://127.0.0.1:" + server.port();
            properties.getClients().get(NAME).setBaseUrl(url);
            var connector = new ReactorClientHttpConnector(HttpClient.create(provider).disableRetry(true));
            context.registerBean(ReactiveHttpClientProperties.class, () -> properties);
            context.registerBean("applicationConnector", ReactiveHttpClientCustomizer.class,
                    () -> builder -> builder.clientConnector(connector));
            context.refresh();
            factory.setType(Client.class);
            factory.setApplicationContext(context);
            Client client = factory.getObject();
            assertThat(client.get().block(WAIT)).isEqualTo("ok");
            assertThat(provider.isDisposed()).isFalse();
            assertThat((Set<?>) field(factory, "ownedConnections")).isEmpty();
            factory.destroy();
            context.close();
            assertThat(provider.isDisposed()).isFalse();
            // The application can still use its connector; factory teardown does not own its pool.
            assertThat(WebClient.builder().baseUrl(url).clientConnector(connector).build()
                    .get().uri("/after-close").retrieve().bodyToMono(String.class).block(WAIT)).isEqualTo("ok");
            assertThat(dispatches).hasValue(2);
        } finally {
            factory.destroy();
            context.close();
            provider.disposeLater().block(WAIT);
            server.disposeNow(WAIT);
        }
        assertThat(provider.isDisposed()).isTrue();
    }

    @ReactiveHttpClient(name = NAME)
    interface Client { @GET("/read") Mono<String> get(); }

    private static ReactiveHttpClientProperties properties(boolean caching) {
        var properties = new ReactiveHttpClientProperties();
        properties.getObservability().getCache().setEnabled(true);
        var config = new ReactiveHttpClientProperties.ClientConfig();
        config.setBaseUrl("http://localhost");
        if (caching) {
            var policy = new ReactiveHttpClientProperties.CachePolicyConfig();
            policy.setTtlMs(60_000L);
            policy.setMaximumSize(16L);
            policy.setSharedResponse(true);
            config.getCache().getPolicies().put("read", policy);
            config.getCache().setPolicy("read");
        }
        properties.getClients().put(NAME, config);
        return properties;
    }

    private static ReactiveClientInvocationHandler handler(GenericApplicationContext context,
            ReactiveHttpClientProperties.ClientConfig config, ReactiveHttpClientProperties properties) {
        return ReactiveClientInvocationHandler.create(WebClient.builder().baseUrl("http://localhost").build(),
                new MethodMetadataCache(), new RequestArgumentResolver(), new DefaultErrorDecoder(),
                config, NAME, Client.class, context, new NoopResilienceOperatorApplier(), null,
                properties.getObservability());
    }

    private static Set<LocalResponseCacheMetrics> metricOwners(MeterRegistry registry) throws Exception {
        Map<?, ?> shared = (Map<?, ?>) field(null, MicrometerLocalResponseCacheMetrics.class, "SHARED");
        Map<?, ?> meters = (Map<?, ?>) shared.get(registry);
        Set<LocalResponseCacheMetrics> owners = new HashSet<>();
        if (meters != null) {
            for (Object meter : meters.values()) {
                for (Object owner : ((Map<?, ?>) field(meter, "owners")).keySet()) {
                    owners.add((LocalResponseCacheMetrics) owner);
                }
            }
        }
        return owners;
    }

    private static Object field(Object target, String name) throws Exception {
        return field(target, target.getClass(), name);
    }

    private static Object field(Object target, Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
