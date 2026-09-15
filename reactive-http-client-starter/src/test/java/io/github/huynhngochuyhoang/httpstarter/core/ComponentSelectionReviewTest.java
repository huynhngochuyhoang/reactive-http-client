package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.annotation.GET;
import io.github.huynhngochuyhoang.httpstarter.annotation.ReactiveHttpClient;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.OrderComparator;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@org.junit.jupiter.api.Timeout(30)
class ComponentSelectionReviewTest {
    enum RegistryState { ABSENT, SINGLETON, LAZY, PROTOTYPE, CACHED_PRODUCT, UNCACHED_PRODUCT }
    enum Preference { PRIMARY, NON_FALLBACK, PRIORITY, DEFAULT }

    @ParameterizedTest
    @EnumSource(RegistryState.class)
    void diagnosticsDoNotCreateRegistriesAndRuntimeUsesTheSelectedInstance(RegistryState state) {
        try (var fixture = new Fixture()) {
            var creations = new AtomicInteger();
            RetryRegistry registry = registry(2);
            switch (state) {
                case ABSENT -> { }
                case SINGLETON -> fixture.context.registerBean(RetryRegistry.class, () -> {
                    creations.incrementAndGet();
                    return registry;
                });
                case LAZY, PROTOTYPE -> {
                    var definition = new RootBeanDefinition(RetryRegistry.class, () -> {
                        creations.incrementAndGet();
                        return registry(2);
                    });
                    definition.setLazyInit(state == RegistryState.LAZY);
                    if (state == RegistryState.PROTOTYPE) { definition.setScope("prototype"); }
                    fixture.context.registerBeanDefinition("registry", definition);
                }
                case CACHED_PRODUCT, UNCACHED_PRODUCT -> {
                    fixture.context.getBeanFactory().registerSingleton("registry", new RegistryFactory(registry, creations));
                    if (state == RegistryState.CACHED_PRODUCT) {
                        fixture.context.getBeanFactory().getBean("registry");
                    }
                }
            }
            fixture.context.refresh();
            int before = creations.get();
            boolean unresolved = state == RegistryState.LAZY || state == RegistryState.PROTOTYPE
                    || state == RegistryState.UNCACHED_PRODUCT;
            assertThat(fixture.snapshot())
                    .containsEntry("retry", state == RegistryState.ABSENT ? "unavailable" : unresolved ? "unknown" : "review")
                    .containsEntry("strictUnsafeRetryValidation", state == RegistryState.ABSENT
                            ? Boolean.FALSE : unresolved ? null : Boolean.TRUE);
            assertThat(creations).hasValue(before);
            assertThat(fixture.context.getBeanFactory().containsSingleton("client")).isFalse();
            Client client = fixture.context.getBean(Client.class);
            if (state == RegistryState.ABSENT) {
                assertThatThrownBy(() -> client.get().block(Duration.ofSeconds(5)))
                        .isInstanceOf(io.github.huynhngochuyhoang.httpstarter.exception.RemoteServiceException.class);
                assertThat(fixture.exchanges).hasValue(1);
                assertThat(creations).hasValue(0);
            } else {
                assertThat(client.get().block(Duration.ofSeconds(5))).isEqualTo("ok");
                assertThat(fixture.exchanges).hasValue(2);
                assertThat(creations).hasValue(1);
                assertThat(fixture.snapshot())
                        .containsEntry("retry", state == RegistryState.PROTOTYPE ? "unknown" : "review")
                        .containsEntry("strictUnsafeRetryValidation", state == RegistryState.PROTOTYPE ? null : true);
                assertThat(creations).hasValue(1);
            }
        }
    }

    @ParameterizedTest
    @EnumSource(Preference.class)
    void initializedRegistryPrecedenceMatchesDiagnosticsAndActualRetry(Preference preference) {
        try (var fixture = new Fixture()) {
            RetryRegistry secondary = registry(1);
            RetryRegistry preferred = registry(2);
            var first = new RootBeanDefinition(RetryRegistry.class, () -> secondary);
            var second = new RootBeanDefinition(RetryRegistry.class, () -> preferred);
            switch (preference) {
                case PRIMARY -> second.setPrimary(true);
                case NON_FALLBACK -> first.setFallback(true);
                case DEFAULT -> first.setDefaultCandidate(false);
                case PRIORITY -> fixture.context.getDefaultListableBeanFactory().setDependencyComparator(new OrderComparator() {
                    @Override public Integer getPriority(Object candidate) {
                        return candidate == preferred ? 0 : candidate == secondary ? 1 : null;
                    }
                });
            }
            fixture.context.registerBeanDefinition("secondary", first);
            fixture.context.registerBeanDefinition("preferred", second);
            fixture.context.refresh();
            assertThat(fixture.context.getBeanProvider(RetryRegistry.class).getIfAvailable()).isSameAs(preferred);
            assertThat(fixture.snapshot()).containsEntry("retry", "review").containsEntry("strictUnsafeRetryValidation", true);
            assertThat(fixture.context.getBean(Client.class).get().block(Duration.ofSeconds(5))).isEqualTo("ok");
            assertThat(fixture.exchanges).hasValue(2);
            assertThat(preferred.retry("review").getMetrics().getNumberOfSuccessfulCallsWithRetryAttempt()).isEqualTo(1);
            assertThat(secondary.retry("review").getMetrics().getNumberOfSuccessfulCallsWithRetryAttempt()).isZero();
        }
    }

    private static RetryRegistry registry(int attempts) {
        var registry = RetryRegistry.of(RetryConfig.custom().maxAttempts(attempts).waitDuration(Duration.ZERO).build());
        registry.retry("review");
        return registry;
    }

    @ReactiveHttpClient(name = "selection")
    interface Client { @GET("/read") Mono<String> get(); }

    private record RegistryFactory(RetryRegistry registry, AtomicInteger creations) implements FactoryBean<RetryRegistry> {
        @Override public RetryRegistry getObject() { creations.incrementAndGet(); return registry; }
        @Override public Class<?> getObjectType() { return RetryRegistry.class; }
        @Override public boolean isSingleton() { return true; }
    }

    private static final class Fixture implements AutoCloseable {
        final GenericApplicationContext context = new GenericApplicationContext();
        final ReactiveHttpClientProperties properties = new ReactiveHttpClientProperties();
        final MethodMetadataCache metadata = new MethodMetadataCache();
        final AtomicInteger exchanges = new AtomicInteger();

        Fixture() {
            var config = new ReactiveHttpClientProperties.ClientConfig();
            config.setBaseUrl("http://localhost");
            config.getResilience().setEnabled(true);
            config.getResilience().setRetry("review");
            config.getResilience().setStrictUnsafeRetryValidation(true);
            properties.getClients().put("selection", config);
            context.registerBean(ReactiveHttpClientProperties.class, () -> properties);
            context.registerBean(MethodMetadataCache.class, () -> metadata);
            context.registerBean(WebClient.Builder.class, () -> WebClient.builder().exchangeFunction(request -> Mono.just(
                    ClientResponse.create(exchanges.incrementAndGet() == 1 ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.OK)
                            .header("Content-Type", "text/plain").body("ok").build())));
            var definition = new RootBeanDefinition(ReactiveHttpClientFactoryBean.class);
            definition.setLazyInit(true);
            definition.getPropertyValues().add("type", Client.class);
            definition.setAttribute(FactoryBean.OBJECT_TYPE_ATTRIBUTE, Client.class);
            context.registerBeanDefinition("client", definition);
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> snapshot() {
            var snapshot = ReactiveHttpClientDiagnosticsSnapshot.toMap(new ReactiveHttpClientDiagnosticsProvider(
                    context.getDefaultListableBeanFactory(), properties, metadata));
            return ((java.util.List<Map<String, Object>>) snapshot.get("clients")).getFirst();
        }

        @Override public void close() { context.close(); }
    }
}
