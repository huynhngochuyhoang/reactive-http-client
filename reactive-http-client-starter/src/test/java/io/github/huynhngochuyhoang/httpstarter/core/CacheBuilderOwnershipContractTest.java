package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.annotation.GET;
import io.github.huynhngochuyhoang.httpstarter.annotation.ReactiveHttpClient;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientAutoConfiguration;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientBeanFactoryInitializationAotProcessor;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.boot.webclient.WebClientCustomizer;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

class CacheBuilderOwnershipContractTest {
    private static final String BUILDER = "starterWebClientBuilder";
    private static final String CONFIGURATION = "starterConfiguration";
    private final MethodMetadataCache metadata = new MethodMetadataCache();
    private final ReactiveHttpClientProperties properties = properties();
    private final AtomicInteger creations = new AtomicInteger();

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void contextFactoryAotAndDiagnosticsRecognizeTheOwningDefinition(boolean inherited) {
        try (var parent = new GenericApplicationContext(); var child = new GenericApplicationContext()) {
            child.setParent(parent);
            starterBuilder(inherited ? parent : child);
            parent.refresh();
            child.getBeanFactory().registerSingleton("properties", properties);
            var client = new RootBeanDefinition(ReactiveHttpClientFactoryBean.class);
            client.getConstructorArgumentValues().addGenericArgumentValue(Client.class);
            client.setAttribute(FactoryBean.OBJECT_TYPE_ATTRIBUTE, Client.class);
            client.setLazyInit(true);
            child.registerBeanDefinition("client", client);
            child.refresh();

            for (ListableBeanFactory entry : List.of(child, child.getBeanFactory())) {
                assertThatCode(() -> validate(entry)).doesNotThrowAnyException();
            }
            assertThat(new ReactiveHttpClientBeanFactoryInitializationAotProcessor()
                    .processAheadOfTime(child.getBeanFactory())).isNotNull();
            var diagnostics = new ReactiveHttpClientDiagnosticsProvider(child.getBeanFactory(), properties, metadata);
            assertThat(diagnostics.clientSummaries()).hasSize(1);
            assertThat(diagnostics.clientSnapshotEntries()).hasSize(1);
            assertThat(child.getBeanFactory().containsSingleton("client")).isFalse();
            assertThat(creations).hasValue(0);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void aChildDefinitionOrSingletonCannotBorrowTheParentsOwnership(boolean singleton) {
        try (var parent = new GenericApplicationContext(); var child = new GenericApplicationContext()) {
            starterBuilder(parent);
            parent.refresh();
            child.setParent(parent);
            if (singleton) {
                child.getBeanFactory().registerSingleton(BUILDER, WebClient.builder());
            } else {
                applicationBuilder(child, BUILDER, "prototype");
            }
            child.refresh();
            rejectThenClassify(child, BUILDER);
            assertThat(creations).hasValue(0);
        }
    }

    @Test
    void inheritedBuilderUsesItsOwnFactoryEvenWhenTheChildShadowsTheConfigurationName() {
        try (var parent = new GenericApplicationContext(); var child = new GenericApplicationContext()) {
            starterBuilder(parent);
            parent.refresh();
            child.setParent(parent);
            child.registerBean(CONFIGURATION, ApplicationConfiguration.class, () -> {
                creations.incrementAndGet();
                return new ApplicationConfiguration();
            }, definition -> definition.setLazyInit(true));
            child.refresh();
            assertThatCode(() -> validate(child)).doesNotThrowAnyException();
            assertThatCode(() -> validate(child.getBeanFactory())).doesNotThrowAnyException();
            assertThat(creations).hasValue(0);
        }
    }

    @Test
    void matchingBeanAndFactoryMethodNamesDoNotProveStarterOwnership() {
        try (var context = new GenericApplicationContext()) {
            starterBuilder(context);
            context.registerBean(CONFIGURATION, ApplicationConfiguration.class, () -> {
                creations.incrementAndGet();
                return new ApplicationConfiguration();
            }, definition -> definition.setLazyInit(true));
            context.refresh();
            rejectThenClassify(context, BUILDER);
            assertThat(creations).hasValue(0);
        }
    }

    @Test
    void theStarterConfigurationAloneDoesNotProveBuilderMethodOwnership() {
        try (var context = new GenericApplicationContext()) {
            starterBuilder(context);
            context.getBeanFactory().getBeanDefinition(BUILDER).setFactoryMethodName("applicationBuilder");
            context.refresh();
            rejectThenClassify(context, BUILDER);
            assertThat(creations).hasValue(0);
        }
    }

    @Test
    void aFactorySingletonWithoutDefinitionRemainsUnknown() {
        try (var context = new GenericApplicationContext()) {
            starterBuilder(context);
            context.removeBeanDefinition(CONFIGURATION);
            context.getBeanFactory().registerSingleton(CONFIGURATION, new ReactiveHttpClientAutoConfiguration());
            context.refresh();
            rejectThenClassify(context, BUILDER);
            assertThat(creations).hasValue(0);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"singleton", "prototype"})
    void inspectingApplicationBuildersDoesNotCreateLazyOrPrototypeInstances(String scope) {
        try (var context = new GenericApplicationContext()) {
            applicationBuilder(context, BUILDER, scope);
            context.refresh();
            rejectThenClassify(context, BUILDER);
            assertThat(creations).hasValue(0);
            assertThat(context.getBean(BUILDER)).isInstanceOf(WebClient.Builder.class);
            assertThat(creations).hasValue(1);
        }
    }

    @Test
    void inspectingAKnownFactoryBeanProductCreatesNeitherFactoryNorProduct() {
        var products = new AtomicInteger();
        try (var context = new GenericApplicationContext()) {
            context.registerBean(BUILDER, BuilderFactory.class, () -> {
                creations.incrementAndGet();
                return new BuilderFactory(products);
            }, definition -> definition.setLazyInit(true));
            context.refresh();
            rejectThenClassify(context, BUILDER);
            assertThat(creations).hasValue(0);
            assertThat(products).hasValue(0);
            assertThat(context.getBean(BUILDER)).isInstanceOf(WebClient.Builder.class);
            assertThat(creations).hasValue(1);
            assertThat(products).hasValue(1);
        }
    }

    @Test
    void inheritedApplicationCustomizersStillNeedClassificationWithoutBeingMaterialized() {
        try (var parent = new GenericApplicationContext(); var child = new GenericApplicationContext()) {
            starterBuilder(parent);
            parent.registerBean("bootMutation", WebClientCustomizer.class, () -> {
                creations.incrementAndGet();
                return builder -> builder.defaultRequest(request -> request.header("X-Tenant", "dynamic"));
            }, definition -> definition.setLazyInit(true));
            parent.registerBean("clientMutation", ReactiveHttpClientCustomizer.class, () -> {
                creations.incrementAndGet();
                return builder -> builder.filter((request, next) -> next.exchange(request));
            }, definition -> definition.setScope("prototype"));
            parent.refresh();
            child.setParent(parent);
            child.refresh();
            classify(BUILDER);
            for (String name : List.of("bootMutation", "clientMutation")) {
                for (ListableBeanFactory entry : List.of(child, child.getBeanFactory())) {
                    assertThatThrownBy(() -> validate(entry)).hasMessageContaining(name)
                            .hasMessageContaining("has no cache-safety classification");
                }
                classify(name);
            }
            assertThatCode(() -> validate(child)).doesNotThrowAnyException();
            assertThat(creations).hasValue(0);
        }
    }

    @Test
    void uninspectableFactoryCannotProveOwnershipFromTheBuilderName() {
        var context = new StaticListableBeanFactory();
        context.addBean(BUILDER, WebClient.builder());
        assertThatThrownBy(() -> validate(context)).hasMessageContaining(BUILDER);
        classify(BUILDER);
        assertThatCode(() -> validate(context)).doesNotThrowAnyException();
    }

    private void starterBuilder(GenericApplicationContext context) {
        context.registerBean(CONFIGURATION, ReactiveHttpClientAutoConfiguration.class, () -> {
            creations.incrementAndGet();
            return new ReactiveHttpClientAutoConfiguration();
        }, definition -> definition.setLazyInit(true));
        var definition = new RootBeanDefinition();
        definition.setTargetType(WebClient.Builder.class);
        definition.setFactoryBeanName(CONFIGURATION);
        definition.setFactoryMethodName(BUILDER);
        definition.setScope("prototype");
        context.registerBeanDefinition(BUILDER, definition);
    }

    private void applicationBuilder(GenericApplicationContext context, String name, String scope) {
        context.registerBean(name, WebClient.Builder.class, () -> {
            creations.incrementAndGet();
            return WebClient.builder();
        }, definition -> {
            definition.setLazyInit(true);
            definition.setScope(scope);
        });
    }

    private void rejectThenClassify(GenericApplicationContext context, String name) {
        for (ListableBeanFactory entry : List.of(context, context.getBeanFactory())) {
            assertThatThrownBy(() -> validate(entry)).hasMessageContaining(name)
                    .hasMessageContaining("has no cache-safety classification");
        }
        classify(name);
        for (ListableBeanFactory entry : List.of(context, context.getBeanFactory())) {
            assertThatCode(() -> validate(entry)).doesNotThrowAnyException();
        }
    }

    private void classify(String name) {
        properties.getClients().get("ownership").getCache().getCustomizations()
                .put(name, ReactiveHttpClientProperties.CacheCustomizationSafety.SAFE);
    }

    private void validate(ListableBeanFactory context) {
        metadata.validateDeclarativeCacheCustomizations(context, Client.class, "ownership",
                properties.getClients().get("ownership"));
    }

    private static ReactiveHttpClientProperties properties() {
        var properties = new ReactiveHttpClientProperties();
        var config = new ReactiveHttpClientProperties.ClientConfig();
        var policy = new ReactiveHttpClientProperties.CachePolicyConfig();
        policy.setTtlMs(1000L);
        policy.setMaximumSize(10L);
        policy.setSharedResponse(true);
        config.getCache().getPolicies().put("read", policy);
        config.getCache().setPolicy("read");
        properties.getClients().put("ownership", config);
        return properties;
    }

    @ReactiveHttpClient(name = "ownership")
    interface Client {
        @GET("/value") Mono<String> read();
    }

    static class ApplicationConfiguration {
        public WebClient.Builder starterWebClientBuilder() { return WebClient.builder(); }
    }

    static class BuilderFactory implements FactoryBean<WebClient.Builder> {
        private final AtomicInteger products;
        BuilderFactory(AtomicInteger products) { this.products = products; }
        @Override public WebClient.Builder getObject() {
            products.incrementAndGet();
            return WebClient.builder();
        }
        @Override public Class<?> getObjectType() { return WebClient.Builder.class; }
    }
}
