package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.annotation.GET;
import io.github.huynhngochuyhoang.httpstarter.annotation.PathVar;
import io.github.huynhngochuyhoang.httpstarter.annotation.ReactiveHttpClient;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientBeanFactoryInitializationAotProcessor;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
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
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@org.junit.jupiter.api.Timeout(30)
class CacheWorkPolicyEnforcementTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void publicSelectionEnforcesAllThreeDimensionsAndPreservesExpiry(boolean single) {
        var config = config(single);
        AtomicLong clock = new AtomicLong();
        var scheduler = VirtualTimeScheduler.create();
        AtomicInteger dispatches = new AtomicInteger();
        Sinks.One<ClientResponse> refresh = Sinks.one();
        Sinks.One<ClientResponse> foreground = Sinks.one();
        try (var context = new GenericApplicationContext()) {
            context.refresh();
            var metadata = new MethodMetadataCache();
            try (var manager = LocalResponseCacheManager.createForClient(Client.class, "work", metadata, config,
                    getClass().getClassLoader(), clock::get, scheduler, LocalResponseCacheMetrics.disabled(), false)) {
                WebClient web = WebClient.builder().baseUrl("http://localhost")
                        .exchangeFunction(request -> {
                            dispatches.incrementAndGet();
                            if (request.url().getPath().contains("busy")) { return foreground.asMono(); }
                            return clock.get() == 0 ? Mono.just(response("initial")) : refresh.asMono();
                        }).build();
                var handler = new ReactiveClientInvocationHandler(web, metadata, new RequestArgumentResolver(),
                        new DefaultErrorDecoder(), config, "work", Client.class, context,
                        new NoopResilienceOperatorApplier(), null, null, manager);
                Client client = (Client) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{Client.class}, handler);
                assertThat(client.get("a").block()).isEqualTo("initial");
                assertThat(client.other("b").block()).isEqualTo("initial");
                clock.set(Duration.ofSeconds(2).toNanos());
                assertThat(client.get("a").block()).isEqualTo("initial");
                assertThat(manager.activeRefreshesForTesting("work")).isEqualTo(1);
                assertThat(manager.callerAdmission().active("work")).isZero();
                assertThat(client.other("b").block()).isEqualTo("initial");
                assertThat(dispatches).hasValue(3);
                var first = client.get("busy").toFuture();
                assertThat(manager.activeLoadsForTesting("work")).isEqualTo(1);
                if (single) {
                    var waiter = client.get("busy").toFuture();
                    StepVerifier.create(client.get("a")).expectError(io.github.huynhngochuyhoang.httpstarter.exception.CacheWorkRejectedException.class).verify();
                    first.cancel(true);
                    assertThat(manager.activeLoadsForTesting("work")).isEqualTo(1);
                    assertThat(client.get("a").block()).isEqualTo("initial");
                    waiter.cancel(true);
                } else {
                    StepVerifier.create(client.get("busy")).expectError(io.github.huynhngochuyhoang.httpstarter.exception.CacheWorkRejectedException.class).verify();
                    first.cancel(true);
                }
                assertThat(manager.activeLoadsForTesting("work")).isZero();
                assertThat(manager.callerAdmission().active("work")).isZero();
                assertThat(dispatches).hasValue(4);
                clock.set(Duration.ofSeconds(10).toNanos());
                scheduler.advanceTimeBy(Duration.ofSeconds(8));
                assertThat(manager.activeRefreshesForTesting("work")).isZero();
                // The skipped entry's original hard TTL was not extended.
                var expired = client.other("b").toFuture();
                assertThat(dispatches).hasValue(5);
                assertThat(manager.activeLoadsForTesting("work")).isEqualTo(1);
                expired.cancel(true);
                manager.close();
                assertThat(manager.activeLoadsForTesting("work")).isZero();
                assertThat(manager.callerAdmission().active("work")).isZero();
            }
        } finally {
            scheduler.dispose();
        }
    }

    enum Mutation { LIMIT, REMOVE, ADD, POLICY_MAPPING, REFRESH_SELECTION }

    @ParameterizedTest
    @EnumSource(Mutation.class)
    void mutationCannotResetCapacityOrChangeColdCallsAndLiveSnapshots(Mutation mutation) {
        var config = config(true);
        if (mutation == Mutation.ADD) { config.getCache().getPolicies().get("work").setWork(null); }
        var metadata = new MethodMetadataCache();
        try (var context = new GenericApplicationContext()) {
            context.refresh();
            var handler = ReactiveClientInvocationHandler.create(
                    WebClient.builder().baseUrl("http://localhost")
                            .exchangeFunction(request -> Mono.just(response("value"))).build(),
                    metadata, new RequestArgumentResolver(), new DefaultErrorDecoder(), config, "work",
                    Client.class, context, new NoopResilienceOperatorApplier(), null, null);
            try (var manager = handler.responseCacheManager()) {
                Client client = (Client) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{Client.class}, handler);
                Mono<String> cold = client.get("a");
                var policy = config.getCache().getPolicies().get("work");
                switch (mutation) {
                    case LIMIT -> policy.getWork().setMaximumConcurrentCallers(3L);
                    case REMOVE -> policy.setWork(null);
                    case ADD -> policy.setWork(work());
                    case POLICY_MAPPING -> config.getCache().setPolicy(null);
                    case REFRESH_SELECTION -> {
                        policy.setRefreshAfterMs(null);
                        policy.setRefreshTimeoutMs(null);
                        policy.getWork().setMaximumConcurrentRefreshes(null);
                    }
                }
                StepVerifier.create(cold).expectErrorMatches(error ->
                        error.getMessage().contains("changed after startup")).verify();
                assertThatThrownBy(() -> client.get("b")).hasMessageContaining("changed after startup");
                assertThatThrownBy(manager::snapshot).hasMessageContaining("changed after startup");
                assertThat(manager.activeLoadsForTesting("work")).isZero();
            }
        }
    }

    @Test
    void selectedInvalidWorkFailsStartupContractDiagnosticsAndAotWithoutCreatingInfrastructure() {
        var config = config(true);
        config.getCache().getPolicies().get("work").getWork().setMaximumConcurrentRefreshes(null);
        var metadata = new MethodMetadataCache();
        assertThatThrownBy(() -> metadata.validateDeclarativeCachePolicies(Client.class, "work", config))
                .hasMessageContaining("maximum-concurrent-refreshes");
        assertThatThrownBy(() -> EffectiveHttpClientContractExporter.export(Client.class, "work", config, metadata))
                .hasMessageContaining("maximum-concurrent-refreshes");
        var properties = new ReactiveHttpClientProperties();
        properties.getClients().put("work", config);
        var beans = new DefaultListableBeanFactory();
        var definition = new RootBeanDefinition(ReactiveHttpClientFactoryBean.class);
        definition.setAttribute(FactoryBean.OBJECT_TYPE_ATTRIBUTE, Client.class);
        definition.getPropertyValues().add("type", Client.class);
        beans.registerBeanDefinition("work", definition);
        beans.registerSingleton("properties", properties);
        beans.registerSingleton("metadata", metadata);
        assertThatThrownBy(() -> new ReactiveHttpClientDiagnosticsProvider(beans, properties, metadata)
                .clientSnapshotEntries()).hasMessageContaining("maximum-concurrent-refreshes");
        assertThatThrownBy(() -> new ReactiveHttpClientBeanFactoryInitializationAotProcessor().processAheadOfTime(beans))
                .hasMessageContaining("maximum-concurrent-refreshes");
        assertThat(beans.containsSingleton("work")).isFalse();
        config.getCache().getPolicies().get("work").getWork().setMaximumConcurrentRefreshes(1L);
        assertThat(new ReactiveHttpClientBeanFactoryInitializationAotProcessor().processAheadOfTime(beans)).isNotNull();
        assertThat(beans.containsSingleton("work")).isFalse();
    }

    @Test
    void foreignFactoryContractsDoNotApplyStarterWorkGrammar() {
        var config = config(true);
        config.getCache().getPolicies().get("work").getWork().setMaximumConcurrentRefreshes(null);
        assertThat(EffectiveHttpClientContractExporter.export(
                Client.class, "work", config, new MethodMetadataCache(), null, null, false, false))
                .allSatisfy(contract -> assertThat(contract.cache().work()).isNull());
    }

    @Test
    void bindsAndExportsOnlyExplicitNormalizedWorkBounds() {
        var properties = new Binder(new MapConfigurationPropertySource(Map.of(
                "reactive.http.clients.work.cache.policy", "work",
                "reactive.http.clients.work.cache.policies.work.ttl-ms", "10000",
                "reactive.http.clients.work.cache.policies.work.maximum-size", "100",
                "reactive.http.clients.work.cache.policies.work.shared-response", "true",
                "reactive.http.clients.work.cache.policies.work.work.maximum-concurrent-callers", "4",
                "reactive.http.clients.work.cache.policies.work.work.maximum-concurrent-loads", "2")))
                .bind("reactive.http", Bindable.of(ReactiveHttpClientProperties.class)).get();
        var config = properties.getClients().get("work");
        var metadata = new MethodMetadataCache();
        var contract = EffectiveHttpClientContractExporter.export(Client.class, "work", config, metadata);
        assertThat(contract).allSatisfy(item -> assertThat(item.cache().work())
                .isEqualTo(new CacheWorkPolicy.Limits(4, 2, null)));
        assertThat(ReactiveHttpClientContractSnapshot.markdown(
                ReactiveHttpClientContractSnapshot.client(Client.class, "work", config)))
                .contains("workCallers=4,workLoads=2,workRefreshes=disabled");
        assertThat(config.getCache().getPolicies().get("work").isRefreshEnabled()).isFalse();
        assertThat(config.getCache().getPolicies().get("work").isSingleFlight()).isFalse();
        config.getCache().getPolicies().get("work").setWork(new ReactiveHttpClientProperties.CacheWorkConfig());
        assertThat(EffectiveHttpClientContractExporter.export(Client.class, "work", config, metadata))
                .allSatisfy(item -> assertThat(item.cache().work()).isNull());
        assertThat(ReactiveHttpClientContractSnapshot.markdown(
                ReactiveHttpClientContractSnapshot.client(Client.class, "work", config)))
                .doesNotContain("workCallers=", "workLoads=", "workRefreshes=");
    }

    static ReactiveHttpClientProperties.ClientConfig config(boolean single) {
        var config = new ReactiveHttpClientProperties.ClientConfig();
        config.setBaseUrl("http://localhost");
        config.getCache().setPolicy("work");
        var policy = CacheRefreshAdmissionContractTest.policy(single).policy();
        policy.setSharedResponse(true);
        policy.setWork(work());
        config.getCache().getPolicies().put("work", policy);
        return config;
    }

    static ReactiveHttpClientProperties.CacheWorkConfig work() {
        var work = new ReactiveHttpClientProperties.CacheWorkConfig();
        work.setMaximumConcurrentCallers(2L);
        work.setMaximumConcurrentLoads(1L);
        work.setMaximumConcurrentRefreshes(1L);
        return work;
    }

    private static ClientResponse response(String body) {
        return ClientResponse.create(HttpStatus.OK).header("Content-Type", "text/plain").body(body).build();
    }

    @ReactiveHttpClient(name = "work")
    interface Client {
        @GET("/{id}") Mono<String> get(@PathVar("id") String id);
        @GET("/other/{id}") Mono<String> other(@PathVar("id") String id);
    }
}
