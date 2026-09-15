package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.annotation.GET;
import io.github.huynhngochuyhoang.httpstarter.annotation.ReactiveHttpClient;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.server.HttpServer;
import reactor.netty.resources.ConnectionProvider;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.HashSet;
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
    @ValueSource(booleans = {false, true})
    void rejectedPublicHandlerConstructionLeavesMeterLeasesWithoutAReturnedOwner(boolean liveOwner)
            throws Exception {
        var context = new GenericApplicationContext();
        var registry = new SimpleMeterRegistry();
        context.getBeanFactory().registerSingleton("registry", registry);
        context.refresh();
        var properties = properties(true);
        var config = properties.getClients().get(NAME);
        ReactiveClientInvocationHandler existing = null;
        try {
            if (liveOwner) {
                existing = handler(context, config, properties);
            }
            config.setAuthProvider("applicationAuth");
            for (int attempt = 1; attempt <= 3; attempt++) {
                assertThatThrownBy(() -> handler(context, config, properties))
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("caching").hasMessageContaining("AuthProvider");
                assertThat(registry.get(LocalResponseCacheMetrics.PREFIX + ".maximum.entries")
                        .gauge().value()).isEqualTo(16 * (attempt + (liveOwner ? 1 : 0)));
                assertThat(metricOwners(registry)).hasSize(attempt + (liveOwner ? 1 : 0));
            }
            if (existing != null) { existing.responseCacheManager().close(); }
            context.close();
            assertThat(metricOwners(registry)).hasSize(3);
            assertThat(registry.get(LocalResponseCacheMetrics.PREFIX + ".maximum.entries")
                    .gauge().value()).isEqualTo(48);
        } finally {
            if (existing != null) { existing.responseCacheManager().close(); }
            context.close();
            // Test-only access releases the abandoned leases; the failed public call returns no owner.
            metricOwners(registry).forEach(LocalResponseCacheMetrics::close);
            assertThat(registry.getMeters()).isEmpty();
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
        try (var context = new GenericApplicationContext()) {
            var properties = properties(false);
            properties.getClients().get(NAME).setBaseUrl("relative");
            context.registerBean(ReactiveHttpClientProperties.class, () -> properties);
            context.refresh();
            var factory = new ReactiveHttpClientFactoryBean<Client>();
            factory.setType(Client.class);
            factory.setApplicationContext(context);
            try {
                assertThatThrownBy(factory::getObject).isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("baseUrl");
                assertThat(field(factory, "connectionProvider")).isNull();
                assertThat(field(factory, "tokenServiceConnectionProvider")).isNull();
                assertThat(factory.responseCacheSnapshot()).isNull();
            } finally { factory.destroy(); }
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
