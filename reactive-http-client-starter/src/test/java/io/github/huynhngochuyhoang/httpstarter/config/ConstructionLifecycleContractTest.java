package io.github.huynhngochuyhoang.httpstarter.config;

import io.github.huynhngochuyhoang.httpstarter.annotation.GET;
import io.github.huynhngochuyhoang.httpstarter.annotation.ReactiveHttpClient;
import io.github.huynhngochuyhoang.httpstarter.core.MethodMetadataCache;
import io.github.huynhngochuyhoang.httpstarter.core.ReactiveHttpClientFactoryBean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConfigurationPropertiesBindingPostProcessor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Timeout(30)
class ConstructionLifecycleContractTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void recreatedContextsKeepHistoryFactoryLocalAndStopAfterClose(boolean aot) {
        var retained = new ArrayList<PropertiesBindingLifecycle>();
        var values = new ArrayList<ReactiveHttpClientProperties>();
        for (int round = 0; round < 3; round++) {
            PropertiesBindingLifecycle lifecycle;
            try (var context = new AnnotationConfigApplicationContext()) {
                context.register(ReactiveHttpClientAutoConfiguration.class);
                if (aot) context.refreshForAotProcessing(new RuntimeHints());
                else context.refresh();
                lifecycle = context.getBean(PropertiesBindingLifecycle.class);
                assertThat(retained).noneMatch(previous -> previous == lifecycle);
                var properties = context.getBean(ReactiveHttpClientProperties.class);
                values.add(properties);
                assertThat(history(lifecycle).isEmpty()).isEqualTo(!aot);
                assertThat(ReflectionTestUtils.getField(lifecycle, "factory")).isSameAs(context.getBeanFactory());
            }
            retained.add(lifecycle);
            assertThat(history(lifecycle)).isEmpty();
            assertThat(ReflectionTestUtils.getField(lifecycle, "tracking")).isEqualTo(false);
            lifecycle.postProcessBeforeInitialization(values.getLast(), "late");
            lifecycle.postProcessAfterInitialization(values.getLast(), "late");
            lifecycle.bound(values.getLast(), "late", new ConfigurationPropertiesBindingPostProcessor());
            assertThat(history(lifecycle)).isEmpty();
        }
        assertThat(values).hasSize(3);
    }

    @Test
    void failedRefreshDestroysHistoryBeforeSingletonCompletion() {
        try (var context = new AnnotationConfigApplicationContext()) {
            var lifecycle = new PropertiesBindingLifecycle(context.getDefaultListableBeanFactory());
            context.registerBean("history", PropertiesBindingLifecycle.class, () -> lifecycle);
            context.registerBean("properties", ReactiveHttpClientProperties.class);
            context.registerBean("failure", Object.class, () -> {
                assertThat(history(lifecycle)).hasSize(1);
                throw new IllegalStateException("fixture initialization failure");
            }, definition -> definition.setDependsOn("properties"));

            assertThatThrownBy(context::refresh).hasRootCauseMessage("fixture initialization failure");

            assertThat(history(lifecycle)).isEmpty();
            assertThat(ReflectionTestUtils.getField(lifecycle, "tracking")).isEqualTo(false);
            lifecycle.postProcessBeforeInitialization(new ReactiveHttpClientProperties(), "late");
            assertThat(history(lifecycle)).isEmpty();
        }
    }

    @Test
    void closingOneAotOwnerDoesNotEraseAnotherOwnersHistory() {
        try (var first = new AnnotationConfigApplicationContext(); var second = new AnnotationConfigApplicationContext()) {
            for (var context : List.of(first, second)) {
                context.register(ReactiveHttpClientAutoConfiguration.class);
                context.refreshForAotProcessing(new RuntimeHints());
                context.getBean(ReactiveHttpClientProperties.class);
            }
            var firstHistory = first.getBean(PropertiesBindingLifecycle.class);
            var secondHistory = second.getBean(PropertiesBindingLifecycle.class);
            int secondSize = history(secondHistory).size();
            assertThat(secondSize).isPositive();
            first.close();
            assertThat(history(firstHistory)).isEmpty();
            assertThat(history(secondHistory)).hasSize(secondSize);
            assertThat(ReflectionTestUtils.getField(secondHistory, "tracking")).isEqualTo(true);
        }
    }

    @Test
    void repeatedAotSelectionDoesNotRebindOrAccumulateTemporaryProcessors() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.register(ReactiveHttpClientAutoConfiguration.class);
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("fixture",
                    Map.of("review.clients.lifecycle.request-timeout-ms", "8123")));
            context.registerBean("selected", CountedProperties.class, CountedProperties::new,
                    definition -> definition.setPrimary(true));
            registerLazyClient(context, Client.class);
            context.refreshForAotProcessing(new RuntimeHints());
            var properties = context.getBean(CountedProperties.class);
            var lifecycle = context.getBean(PropertiesBindingLifecycle.class);
            var processors = List.copyOf(context.getDefaultListableBeanFactory().getBeanPostProcessors());
            var aot = new ReactiveHttpClientBeanFactoryInitializationAotProcessor(context.getEnvironment());
            int historySize = -1;
            for (int call = 0; call < 20; call++) {
                assertThat(aot.processAheadOfTime(context.getBeanFactory())).isNotNull();
                assertThat(properties.binds).hasValue(1);
                assertThat(properties.getClients().get("lifecycle").getRequestTimeoutMs()).isEqualTo(8123);
                assertThat(context.getDefaultListableBeanFactory().getBeanPostProcessors()).containsExactlyElementsOf(processors);
                if (historySize == -1) historySize = history(lifecycle).size();
                assertThat(history(lifecycle)).hasSize(historySize);
                assertThat(context.getBeanFactory().containsSingleton("client")).isFalse();
            }
        }
    }

    @Test
    void sameNamedClientClassesStaySeparateAcrossClassLoaders() throws Exception {
        Class<?> firstType = isolatedClient();
        Class<?> secondType = isolatedClient();
        assertThat(firstType.getName()).isEqualTo(secondType.getName());
        assertThat(firstType).isNotSameAs(secondType);
        try (var first = new AnnotationConfigApplicationContext(); var second = new AnnotationConfigApplicationContext()) {
            var contexts = List.of(first, second);
            var types = List.of(firstType, secondType);
            var metadata = new ArrayList<MethodMetadataCache>();
            for (int index = 0; index < contexts.size(); index++) {
                var context = contexts.get(index);
                var type = types.get(index);
                context.setClassLoader(type.getClassLoader());
                context.register(ReactiveHttpClientAutoConfiguration.class);
                registerLazyClient(context, type);
                context.refreshForAotProcessing(new RuntimeHints());
                assertThat(new ReactiveHttpClientBeanFactoryInitializationAotProcessor(context.getEnvironment())
                        .processAheadOfTime(context.getBeanFactory())).isNotNull();
                var cache = context.getBean(MethodMetadataCache.class);
                metadata.add(cache);
                assertThat(cache.get(type.getMethod("value")).getMethod().getDeclaringClass()).isSameAs(type);
            }
            assertThat(metadata.getFirst()).isNotSameAs(metadata.getLast());
            first.close();
            assertThat(metadata.getLast().get(secondType.getMethod("value")).getMethod().getDeclaringClass())
                    .isSameAs(secondType);
            assertThat(second.getBeanFactory().containsSingleton("client")).isFalse();
        }
    }

    @Test
    void concurrentCallbacksCannotRestartStoppedTracking() throws Exception {
        var lifecycle = new PropertiesBindingLifecycle(new org.springframework.beans.factory.support.DefaultListableBeanFactory());
        var binder = new ConfigurationPropertiesBindingPostProcessor();
        var properties = new ReactiveHttpClientProperties();
        lifecycle.postProcessBeforeInitialization(properties, "properties");
        var start = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var callbacks = executor.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                for (int i = 0; i < 100; i++) {
                    lifecycle.postProcessAfterInitialization(properties, "properties");
                    lifecycle.bound(properties, "properties", binder);
                }
                return null;
            });
            var close = executor.submit(() -> { start.await(5, TimeUnit.SECONDS); lifecycle.destroy(); return null; });
            callbacks.get(5, TimeUnit.SECONDS);
            close.get(5, TimeUnit.SECONDS);
        }
        assertThat(history(lifecycle)).isEmpty();
        assertThat(lifecycle.wasBound(properties, "properties", binder)).isNull();
    }

    private static List<?> history(PropertiesBindingLifecycle lifecycle) {
        return (List<?>) ReflectionTestUtils.getField(lifecycle, "observations");
    }

    private static void registerLazyClient(AnnotationConfigApplicationContext context, Class<?> type) {
        var definition = new RootBeanDefinition(ReactiveHttpClientFactoryBean.class, () -> {
            throw new AssertionError("AOT must not construct the client factory");
        });
        definition.setLazyInit(true);
        definition.setAttribute(FactoryBean.OBJECT_TYPE_ATTRIBUTE, type);
        definition.getPropertyValues().add("type", type);
        context.registerBeanDefinition("client", definition);
    }

    private static Class<?> isolatedClient() throws IOException {
        String name = Client.class.getName();
        byte[] bytes;
        try (var stream = Client.class.getResourceAsStream("/" + name.replace('.', '/') + ".class")) {
            bytes = java.util.Objects.requireNonNull(stream).readAllBytes();
        }
        return new ClassLoader(Client.class.getClassLoader()) {
            Class<?> define() { return defineClass(name, bytes, 0, bytes.length); }
        }.define();
    }

    @ConfigurationProperties("review")
    public static class CountedProperties extends ReactiveHttpClientProperties {
        final AtomicInteger binds = new AtomicInteger();
        @Override public void setClients(Map<String, ClientConfig> clients) {
            binds.incrementAndGet();
            super.setClients(clients);
        }
    }

    @ReactiveHttpClient(name = "lifecycle", baseUrl = "http://lifecycle.invalid")
    public interface Client { @GET("/value") Mono<String> value(); }
}
