package io.github.huynhngochuyhoang.httpstarter.config;

import io.github.huynhngochuyhoang.httpstarter.annotation.CacheResponse;
import io.github.huynhngochuyhoang.httpstarter.annotation.GET;
import io.github.huynhngochuyhoang.httpstarter.annotation.ReactiveHttpClient;
import io.github.huynhngochuyhoang.httpstarter.auth.AuthProvider;
import io.github.huynhngochuyhoang.httpstarter.core.MethodMetadataCache;
import io.github.huynhngochuyhoang.httpstarter.core.ReactiveHttpClientFactoryBean;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.*;
import org.springframework.core.OrderComparator;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.resources.ConnectionProvider;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AotPropertiesSelectionContractTest {
    enum Preference { PRIMARY, NON_FALLBACK, PRIORITY, DEFAULT }
    enum Shape { LAZY, PROTOTYPE, TYPED_FACTORY, RAW_FACTORY, DIRECT_FACTORY, PROTOTYPE_FACTORY }
    enum Hierarchy { PARENT_ONLY, CHILD_SHADOWS, LOCAL_OVER_PARENT_PRIMARY }
    enum LifecycleShape { ORDINARY, BEAN_PROXY, SUPPLIER_PROXY, OPAQUE_BEAN_PROXY, OPAQUE_SUPPLIER_PROXY }
    enum DirectProcessorKind { ORDINARY, ORDERED, PRIORITY_ORDERED }

    @ParameterizedTest
    @CsvSource({"ORDINARY,ORDINARY,false", "ORDINARY,ORDERED,false", "ORDINARY,PRIORITY_ORDERED,false",
            "OPAQUE_SUPPLIER_PROXY,ORDINARY,false", "OPAQUE_SUPPLIER_PROXY,ORDERED,false", "OPAQUE_SUPPLIER_PROXY,PRIORITY_ORDERED,false",
            "ORDINARY,ORDINARY,true", "ORDINARY,ORDERED,true", "ORDINARY,PRIORITY_ORDERED,true",
            "OPAQUE_SUPPLIER_PROXY,ORDINARY,true", "OPAQUE_SUPPLIER_PROXY,ORDERED,true", "OPAQUE_SUPPLIER_PROXY,PRIORITY_ORDERED,true"})
    void directProcessorsPreparePropertiesBeforeBinding(LifecycleShape shape, DirectProcessorKind kind, boolean factoryProduct) {
        var aotCalls = new java.util.ArrayList<String>();
        var runtimeCalls = new java.util.ArrayList<String>();
        try (var aot = lifecycleContext(shape, aotCalls); var runtime = lifecycleContext(shape, runtimeCalls)) {
            for (var context : java.util.List.of(aot, runtime)) {
                context.addBeanFactoryPostProcessor(factory -> factory.addBeanPostProcessor(switch (kind) {
                    case ORDINARY -> new DirectPreparingProcessor();
                    case ORDERED -> new OrderedDirectPreparingProcessor();
                    case PRIORITY_ORDERED -> new PriorityDirectPreparingProcessor();
                }));
                if (factoryProduct) context.registerBean("applicationProcessor", PropertiesPostProcessorFactory.class);
                else context.registerBean("applicationProcessor", OrdinaryPropertiesPostProcessor.class);
            }
            var factory = aot.getDefaultListableBeanFactory();
            var witness = witness(factory, Client.class);
            aot.refreshForAotProcessing(new RuntimeHints());
            var internalProcessors = factory.getBeanPostProcessors().stream()
                    .filter(org.springframework.beans.factory.support.MergedBeanDefinitionPostProcessor.class::isInstance)
                    .toList();
            factory.addBeanPostProcessor(aot.getBean(OrdinaryPropertiesPostProcessor.class));
            factory.addBeanPostProcessors(internalProcessors);
            var processors = java.util.List.copyOf(factory.getBeanPostProcessors());
            assertThat(aotCalls).isEmpty();
            runtime.refresh();
            var expected = runtime.getBeanProvider(ReactiveHttpClientProperties.class).getObject().getClients().get("selection");
            assertThat(runtimeCalls).containsExactly("construct", "environment", "applicationContext", "direct", "bind",
                    "ordinary:9000", "postConstruct:9000", "afterPropertiesSet:9000", "init:9000");

            assertThat(new ReactiveHttpClientBeanFactoryInitializationAotProcessor(aot.getEnvironment())
                    .processAheadOfTime(factory)).isNotNull();

            assertThat(aotCalls).containsExactlyElementsOf(runtimeCalls);
            assertThat(witness.config.getCache().getPolicies().get("chosen").getTtlMs())
                    .isEqualTo(expected.getCache().getPolicies().get("chosen").getTtlMs());
            assertThat(factory.getBeanPostProcessors()).containsExactlyElementsOf(processors);
            if (factoryProduct) {
                assertThat(aot.getBean("&applicationProcessor", PropertiesPostProcessorFactory.class).products).hasValue(1);
                assertThat(runtime.getBean("&applicationProcessor", PropertiesPostProcessorFactory.class).products).hasValue(1);
            }
            witness.assertNoBusinessResources(factory);
        }
    }

    static class DirectPreparingProcessor implements org.springframework.beans.factory.config.BeanPostProcessor {
        @Override public Object postProcessBeforeInitialization(Object bean, String beanName) {
            if (bean instanceof LifecycleProperties properties) {
                assertThat(properties.environment).isNotNull();
                assertThat(properties.applicationContext).isNotNull();
                assertThat(properties.getClients()).isEmpty();
                properties.calls.add("direct");
            }
            return bean;
        }
    }

    static class OrderedDirectPreparingProcessor extends DirectPreparingProcessor implements org.springframework.core.Ordered {
        @Override public int getOrder() { return LOWEST_PRECEDENCE; }
    }

    static class PriorityDirectPreparingProcessor extends OrderedDirectPreparingProcessor implements org.springframework.core.PriorityOrdered { }

    static class PropertiesPostProcessorFactory implements FactoryBean<OrdinaryPropertiesPostProcessor> {
        final AtomicInteger products = new AtomicInteger();
        @Override public OrdinaryPropertiesPostProcessor getObject() {
            products.incrementAndGet();
            return new OrdinaryPropertiesPostProcessor();
        }
        @Override public Class<?> getObjectType() { return OrdinaryPropertiesPostProcessor.class; }
    }

    @ParameterizedTest
    @CsvSource({"ORDINARY,false", "BEAN_PROXY,false", "SUPPLIER_PROXY,false",
            "ORDINARY,true", "BEAN_PROXY,true", "SUPPLIER_PROXY,true",
            "OPAQUE_BEAN_PROXY,false", "OPAQUE_SUPPLIER_PROXY,false",
            "OPAQUE_BEAN_PROXY,true", "OPAQUE_SUPPLIER_PROXY,true"})
    void bindingPrecedesOrdinaryApplicationPostProcessors(LifecycleShape shape, boolean priorityProcessor) {
        var aotCalls = new java.util.ArrayList<String>();
        var runtimeCalls = new java.util.ArrayList<String>();
        try (var aot = lifecycleContext(shape, aotCalls); var runtime = lifecycleContext(shape, runtimeCalls)) {
            aot.registerBean("applicationProcessor", OrdinaryPropertiesPostProcessor.class);
            runtime.registerBean("applicationProcessor", OrdinaryPropertiesPostProcessor.class);
            if (priorityProcessor) {
                aot.registerBean("priorityProcessor", BeforeBindingPostProcessor.class);
                runtime.registerBean("priorityProcessor", BeforeBindingPostProcessor.class);
            }
            var factory = aot.getDefaultListableBeanFactory();
            var witness = witness(factory, Client.class);
            aot.refreshForAotProcessing(new RuntimeHints());
            // An earlier AOT processor installs the application processor. As at runtime,
            // merged-definition processors remain at the end of the registered chain.
            var internalProcessors = factory.getBeanPostProcessors().stream()
                    .filter(org.springframework.beans.factory.support.MergedBeanDefinitionPostProcessor.class::isInstance)
                    .toList();
            if (priorityProcessor) factory.addBeanPostProcessor(aot.getBean(BeforeBindingPostProcessor.class));
            factory.addBeanPostProcessor(aot.getBean(OrdinaryPropertiesPostProcessor.class));
            factory.addBeanPostProcessors(internalProcessors);
            var processors = java.util.List.copyOf(factory.getBeanPostProcessors());
            assertThat(aotCalls).isEmpty();
            runtime.refresh();
            var expected = runtime.getBeanProvider(ReactiveHttpClientProperties.class).getObject()
                    .getClients().get("selection");
            var expectedCalls = new java.util.ArrayList<>(java.util.List.of("construct", "environment", "applicationContext"));
            if (priorityProcessor) expectedCalls.add("priority");
            expectedCalls.addAll(java.util.List.of("bind", "ordinary:9000", "postConstruct:9000", "afterPropertiesSet:9000", "init:9000"));
            assertThat(runtimeCalls).containsExactlyElementsOf(expectedCalls);

            assertThat(new ReactiveHttpClientBeanFactoryInitializationAotProcessor(aot.getEnvironment())
                    .processAheadOfTime(factory)).isNotNull();

            assertThat(aotCalls).containsExactlyElementsOf(runtimeCalls);
            assertThat(witness.config.getCache().getPolicies().get("chosen").getTtlMs())
                    .isEqualTo(expected.getCache().getPolicies().get("chosen").getTtlMs());
            assertThat(factory.getBeanPostProcessors()).containsExactlyElementsOf(processors);
            witness.assertNoBusinessResources(factory);
        }
    }

    static class OrdinaryPropertiesPostProcessor implements org.springframework.beans.factory.config.BeanPostProcessor {
        @Override public Object postProcessBeforeInitialization(Object bean, String beanName) {
            if (bean instanceof LifecycleProperties properties) properties.record("ordinary");
            return bean;
        }
    }

    static class BeforeBindingPostProcessor implements org.springframework.beans.factory.config.BeanPostProcessor,
            org.springframework.core.PriorityOrdered {
        @Override public int getOrder() { return HIGHEST_PRECEDENCE; }
        @Override public Object postProcessBeforeInitialization(Object bean, String beanName) {
            if (bean instanceof LifecycleProperties properties) {
                assertThat(properties.environment).isNotNull();
                assertThat(properties.applicationContext).isNotNull();
                assertThat(properties.getClients()).isEmpty();
                properties.calls.add("priority");
            }
            return bean;
        }
    }

    @ParameterizedTest
    @EnumSource(LifecycleShape.class)
    void bindingPrecedesInitializationAndSupportsProgrammaticProxies(LifecycleShape shape) {
        var aotCalls = new java.util.ArrayList<String>();
        var runtimeCalls = new java.util.ArrayList<String>();
        try (var aot = lifecycleContext(shape, aotCalls); var runtime = lifecycleContext(shape, runtimeCalls)) {
            var witness = witness(aot.getDefaultListableBeanFactory(), Client.class);
            aot.refreshForAotProcessing(new RuntimeHints());
            var processors = java.util.List.copyOf(aot.getDefaultListableBeanFactory().getBeanPostProcessors());
            assertThat(aotCalls).isEmpty();
            runtime.refresh();
            var expected = runtime.getBeanProvider(ReactiveHttpClientProperties.class).getObject()
                    .getClients().get("selection");
            assertThat(runtimeCalls).containsExactly("construct", "environment", "applicationContext", "bind",
                    "postConstruct:9000", "afterPropertiesSet:9000", "init:9000");
            Object proxy = null;
            if (shape != LifecycleShape.ORDINARY) {
                proxy = aot.getBean("selectedProxy");
                assertThat(aot.getBeanFactory().getMergedBeanDefinition("selectedProxy")
                        .getPropertyValues().get("targetBeanName")).isNull();
                if (shape == LifecycleShape.OPAQUE_BEAN_PROXY || shape == LifecycleShape.OPAQUE_SUPPLIER_PROXY) {
                    assertThat(proxy).isNotInstanceOf(org.springframework.aop.framework.Advised.class);
                }
                assertThat(aotCalls).isEmpty();
            }

            assertThat(new ReactiveHttpClientBeanFactoryInitializationAotProcessor(aot.getEnvironment())
                    .processAheadOfTime(aot.getDefaultListableBeanFactory())).isNotNull();

            assertThat(aotCalls).containsExactlyElementsOf(runtimeCalls);
            assertThat(witness.config.getCache().getPolicies().get("chosen").getTtlMs())
                    .isEqualTo(expected.getCache().getPolicies().get("chosen").getTtlMs());
            assertThat(aot.getDefaultListableBeanFactory().getBeanPostProcessors()).containsExactlyElementsOf(processors);
            if (proxy != null) assertThat(aot.getBean("selectedProxy")).isSameAs(proxy);
            witness.assertNoBusinessResources(aot.getDefaultListableBeanFactory());
        }
    }

    @ParameterizedTest
    @CsvSource({"ORDINARY,false", "BEAN_PROXY,false", "SUPPLIER_PROXY,false",
            "ORDINARY,true", "BEAN_PROXY,true", "SUPPLIER_PROXY,true",
            "OPAQUE_BEAN_PROXY,false", "OPAQUE_SUPPLIER_PROXY,false",
            "OPAQUE_BEAN_PROXY,true", "OPAQUE_SUPPLIER_PROXY,true"})
    void temporaryBindingIsRemovedAfterBindingOrInitializationFailure(LifecycleShape shape, boolean failInitialization) {
        var calls = new java.util.ArrayList<String>();
        try (var context = lifecycleContext(shape, calls, failInitialization)) {
            if (!failInitialization) {
                context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("invalid", Map.of(
                        "lifecycle.clients.selection.cache.policies.chosen.ttl-ms", "not-a-number")));
            }
            var factory = context.getDefaultListableBeanFactory();
            var witness = witness(factory, Client.class);
            context.refreshForAotProcessing(new RuntimeHints());
            var processors = java.util.List.copyOf(factory.getBeanPostProcessors());

            assertThatThrownBy(() -> new ReactiveHttpClientBeanFactoryInitializationAotProcessor(context.getEnvironment())
                    .processAheadOfTime(factory))
                    .isInstanceOf(org.springframework.beans.factory.BeanCreationException.class)
                    .hasStackTraceContaining(failInitialization ? "initialization rejected" : "not-a-number");

            assertThat(factory.getBeanPostProcessors()).containsExactlyElementsOf(processors);
            assertThat(witness.config).isNull();
            witness.assertNoBusinessResources(factory);
            if (failInitialization) {
                assertThat(calls).containsExactly("construct", "environment", "applicationContext", "bind",
                        "postConstruct:9000", "afterPropertiesSet:9000", "init:9000");
            } else {
                assertThat(calls).containsExactly("construct", "environment", "applicationContext");
            }
        }
    }

    private static AnnotationConfigApplicationContext lifecycleContext(LifecycleShape shape, java.util.List<String> calls) {
        return lifecycleContext(shape, calls, false);
    }

    private static AnnotationConfigApplicationContext lifecycleContext(
            LifecycleShape shape, java.util.List<String> calls, boolean failInitialization) {
        var context = bindingContext(false);
        context.register(LifecycleBinding.class);
        context.getBeanFactory().registerSingleton("lifecycleCalls", new LifecycleCalls(calls, failInitialization));
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("lifecycle", Map.of(
                "lifecycle.clients.selection.cache.policies.chosen.ttl-ms", "9000",
                "lifecycle.clients.selection.cache.policies.chosen.maximum-size", "16",
                "lifecycle.clients.selection.cache.policies.chosen.shared-response", "true",
                "lifecycle.clients.selection.cache.customizations.Builder", "SAFE")));
        boolean opaque = shape == LifecycleShape.OPAQUE_BEAN_PROXY || shape == LifecycleShape.OPAQUE_SUPPLIER_PROXY;
        if (shape == LifecycleShape.BEAN_PROXY || shape == LifecycleShape.OPAQUE_BEAN_PROXY) {
            context.register(opaque ? OpaqueProgrammaticProxyBinding.class : ProgrammaticProxyBinding.class);
        } else if (shape == LifecycleShape.SUPPLIER_PROXY || shape == LifecycleShape.OPAQUE_SUPPLIER_PROXY) {
            var definition = new RootBeanDefinition(org.springframework.aop.scope.ScopedProxyFactoryBean.class,
                    () -> programmaticProxy(opaque));
            definition.setPrimary(true);
            definition.setAttribute(FactoryBean.OBJECT_TYPE_ATTRIBUTE, LifecycleProperties.class);
            context.registerBeanDefinition("selectedProxy", definition);
        }
        context.addBeanFactoryPostProcessor(factory -> {
            var target = (org.springframework.beans.factory.support.AbstractBeanDefinition) factory.getBeanDefinition("lifecycleTarget");
            target.setPrimary(shape == LifecycleShape.ORDINARY);
            target.setAutowireCandidate(shape == LifecycleShape.ORDINARY);
            target.setDefaultCandidate(shape == LifecycleShape.ORDINARY);
        });
        return context;
    }

    private static org.springframework.aop.scope.ScopedProxyFactoryBean programmaticProxy() {
        return programmaticProxy(false);
    }

    private static org.springframework.aop.scope.ScopedProxyFactoryBean programmaticProxy(boolean opaque) {
        var factory = new org.springframework.aop.scope.ScopedProxyFactoryBean();
        factory.setTargetBeanName("lifecycleTarget");
        factory.setOpaque(opaque);
        return factory;
    }

    @Configuration(proxyBeanMethods = false)
    static class ProgrammaticProxyBinding {
        @Bean @Primary
        org.springframework.aop.scope.ScopedProxyFactoryBean selectedProxy() { return programmaticProxy(); }
    }

    @Configuration(proxyBeanMethods = false)
    static class OpaqueProgrammaticProxyBinding {
        @Bean @Primary
        org.springframework.aop.scope.ScopedProxyFactoryBean selectedProxy() { return programmaticProxy(true); }
    }

    @Configuration(proxyBeanMethods = false)
    static class LifecycleBinding {
        @Bean(initMethod = "finish") @ConfigurationProperties("lifecycle") @Scope("prototype")
        LifecycleProperties lifecycleTarget(LifecycleCalls lifecycleCalls) {
            return new LifecycleProperties(lifecycleCalls.values(), lifecycleCalls.failInitialization());
        }
    }

    record LifecycleCalls(java.util.List<String> values, boolean failInitialization) { }

    static class LifecycleProperties extends ReactiveHttpClientProperties
            implements org.springframework.beans.factory.InitializingBean, EnvironmentAware, ApplicationContextAware {
        private final java.util.List<String> calls;
        private final boolean failInitialization;
        private Environment environment;
        private ApplicationContext applicationContext;
        LifecycleProperties(java.util.List<String> calls, boolean failInitialization) {
            this.calls = calls;
            this.failInitialization = failInitialization;
            calls.add("construct");
        }
        @Override public void setEnvironment(Environment environment) {
            this.environment = environment;
            calls.add("environment");
        }
        @Override public void setApplicationContext(ApplicationContext applicationContext) {
            this.applicationContext = applicationContext;
            calls.add("applicationContext");
        }
        @Override public void setClients(Map<String, ClientConfig> clients) {
            if (environment == null || applicationContext == null) {
                throw new IllegalStateException("awareness callbacks must precede binding");
            }
            assertThat(applicationContext.getEnvironment()).isSameAs(environment);
            assertThat(clients.get("selection").getCache().getPolicies().get("chosen").getTtlMs())
                    .isEqualTo(environment.getRequiredProperty(
                            "lifecycle.clients.selection.cache.policies.chosen.ttl-ms", Long.class));
            calls.add("bind");
            super.setClients(clients);
        }
        @jakarta.annotation.PostConstruct
        void prepared() { record("postConstruct"); }
        @Override public void afterPropertiesSet() { record("afterPropertiesSet"); }
        void finish() {
            record("init");
            if (failInitialization) throw new IllegalStateException("initialization rejected");
        }
        private void record(String callback) {
            var client = getClients().get("selection");
            if (client == null) throw new IllegalStateException("unbound initialization: " + callback);
            calls.add(callback + ":" + client.getCache().getPolicies().get("chosen").getTtlMs());
        }
    }

    @ParameterizedTest
    @EnumSource(Preference.class)
    void invalidPreferredPropertiesFailInsteadOfUsingInactiveOrEnvironmentValues(Preference preference) {
        var factory = new DefaultListableBeanFactory();
        var inactive = properties(1000);
        var selected = properties(0);
        var first = new RootBeanDefinition(ReactiveHttpClientProperties.class, () -> inactive);
        var second = new RootBeanDefinition(ReactiveHttpClientProperties.class, () -> selected);
        switch (preference) {
            case PRIMARY -> second.setPrimary(true);
            case NON_FALLBACK -> first.setFallback(true);
            case DEFAULT -> first.setDefaultCandidate(false);
            case PRIORITY -> factory.setDependencyComparator(new OrderComparator() {
                @Override public Integer getPriority(Object value) { return value == selected ? 0 : 1; }
            });
        }
        factory.registerBeanDefinition("inactive", first);
        factory.registerBeanDefinition("selected", second);
        factory.getBean("inactive");
        factory.getBean("selected");
        var witness = witness(factory, Client.class);
        assertThat(factory.getBeanProvider(ReactiveHttpClientProperties.class).getIfAvailable()).isSameAs(selected);
        assertThatThrownBy(() -> processor().processAheadOfTime(factory))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("ttl-ms");
        assertThat(witness.config).isSameAs(selected.getClients().get("selection"));
        witness.assertNoBusinessResources(factory);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void ambiguityIsNotHiddenByEnvironmentOrRegistrationOrder(boolean primary) {
        var factory = new DefaultListableBeanFactory();
        for (String name : new String[]{"first", "second"}) {
            var definition = new RootBeanDefinition(ReactiveHttpClientProperties.class, () -> properties(1000));
            definition.setPrimary(primary);
            factory.registerBeanDefinition(name, definition);
        }
        var witness = witness(factory, Client.class);
        assertThatThrownBy(() -> factory.getBeanProvider(ReactiveHttpClientProperties.class).getIfAvailable())
                .isInstanceOf(NoUniqueBeanDefinitionException.class);
        assertThatThrownBy(() -> processor().processAheadOfTime(factory))
                .isInstanceOf(NoUniqueBeanDefinitionException.class);
        assertThat(witness.config).isNull();
        witness.assertNoBusinessResources(factory);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void absenceUsesEnvironmentOrDefaultsOnlyWhenNoCandidateExists(boolean environment) {
        var factory = new DefaultListableBeanFactory();
        var witness = witness(factory, environment ? Client.class : PlainClient.class);
        assertThat(factory.getBeanProvider(ReactiveHttpClientProperties.class).getIfAvailable()).isNull();
        var processor = environment ? processor() : new ReactiveHttpClientBeanFactoryInitializationAotProcessor();
        assertThat(processor.processAheadOfTime(factory)).isNotNull();
        assertThat(witness.config.getCache().getPolicies()).hasSize(environment ? 1 : 0);
        witness.assertNoBusinessResources(factory);
    }

    @ParameterizedTest
    @EnumSource(Hierarchy.class)
    void hierarchyFollowsTheRuntimeOracle(Hierarchy hierarchy) {
        var parent = new DefaultListableBeanFactory();
        var child = new DefaultListableBeanFactory(parent);
        var inherited = properties(2000);
        var local = properties(3000);
        var parentDefinition = new RootBeanDefinition(ReactiveHttpClientProperties.class, () -> inherited);
        parentDefinition.setPrimary(true);
        parent.registerBeanDefinition("properties", parentDefinition);
        if (hierarchy != Hierarchy.PARENT_ONLY) {
            child.registerBeanDefinition(hierarchy == Hierarchy.CHILD_SHADOWS ? "properties" : "local",
                    new RootBeanDefinition(ReactiveHttpClientProperties.class, () -> local));
        }
        var witness = witness(child, Client.class);
        var expected = child.getBeanProvider(ReactiveHttpClientProperties.class).getIfAvailable();
        assertThat(expected).isSameAs(hierarchy == Hierarchy.PARENT_ONLY ? inherited : local);
        assertThat(processor().processAheadOfTime(child)).isNotNull();
        assertThat(witness.config).isSameAs(expected.getClients().get("selection"));
        witness.assertNoBusinessResources(child);
    }

    @Test
    void opaqueParentProviderIsNotReplacedByEnvironmentDefaults() {
        var parent = new org.springframework.beans.factory.support.StaticListableBeanFactory();
        var selected = properties(8000);
        parent.addBean("properties", selected);
        var child = new DefaultListableBeanFactory(new DefaultListableBeanFactory(parent));
        var witness = witness(child, Client.class);
        assertThat(child.getBeanProvider(ReactiveHttpClientProperties.class).getIfAvailable()).isSameAs(selected);
        assertThat(processor().processAheadOfTime(child)).isNotNull();
        assertThat(witness.config).isSameAs(selected.getClients().get("selection"));
        witness.assertNoBusinessResources(child);
    }

    @ParameterizedTest
    @EnumSource(Shape.class)
    void selectionMaterializesEachConfigurationOnceWithoutBusinessResources(Shape shape) {
        var products = new AtomicInteger();
        var factories = new AtomicInteger();
        var factory = propertiesFactory(shape, products, factories);
        var witness = witness(factory, Client.class);
        assertThat(products).hasValue(0);
        assertThat(processor().processAheadOfTime(factory)).isNotNull();
        assertThat(products).hasValue(1);
        assertThat(factories).hasValue(shape == Shape.LAZY || shape == Shape.PROTOTYPE ? 0 : 1);
        assertThat(witness.config.getCache().getPolicies().get("chosen").getTtlMs()).isEqualTo(4000);
        witness.assertNoBusinessResources(factory);

        // Use an independent factory: asking a prototype oracle on the same factory
        // would itself create a second product and obscure duplicate AOT creation.
        var oracleProducts = new AtomicInteger();
        var oracleFactories = new AtomicInteger();
        var oracle = propertiesFactory(shape, oracleProducts, oracleFactories)
                .getBeanProvider(ReactiveHttpClientProperties.class).getIfAvailable();
        assertThat(oracle.getClients().get("selection").getCache().getPolicies().get("chosen").getTtlMs())
                .isEqualTo(4000);
        assertThat(oracleProducts).hasValue(1);
        assertThat(oracleFactories).hasValue(factories.get());
    }

    @Test
    void dependencyFailureDoesNotFallBackToValidEnvironment() {
        var factory = new DefaultListableBeanFactory();
        var definition = new RootBeanDefinition(ReactiveHttpClientProperties.class,
                () -> { throw new org.springframework.beans.factory.NoSuchBeanDefinitionException(AuthProvider.class); });
        factory.registerBeanDefinition("broken", definition);
        var witness = witness(factory, Client.class);
        assertThatThrownBy(() -> processor().processAheadOfTime(factory))
                .hasRootCauseInstanceOf(org.springframework.beans.factory.NoSuchBeanDefinitionException.class);
        assertThat(witness.config).isNull();
        witness.assertNoBusinessResources(factory);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void aotRefreshBindsSelectedConfigurationUsingBootMetadata(boolean replacement) {
        try (var aot = bindingContext(replacement); var runtime = bindingContext(replacement)) {
            var witness = witness(aot.getDefaultListableBeanFactory(), Client.class);
            aot.refreshForAotProcessing(new RuntimeHints());
            runtime.refresh();
            var expected = runtime.getBeanProvider(ReactiveHttpClientProperties.class).getIfAvailable();
            assertThat(new ReactiveHttpClientBeanFactoryInitializationAotProcessor(aot.getEnvironment())
                    .processAheadOfTime(aot.getDefaultListableBeanFactory())).isNotNull();
            var selected = aot.getBeanProvider(ReactiveHttpClientProperties.class).getIfAvailable();
            assertThat(witness.config).isSameAs(selected.getClients().get("selection"));
            assertThat(witness.config.getCache().getPolicies().get("chosen").getTtlMs())
                    .isEqualTo(replacement ? 7000 : 5000)
                    .isEqualTo(expected.getClients().get("selection").getCache().getPolicies().get("chosen").getTtlMs());
            witness.assertNoBusinessResources(aot.getDefaultListableBeanFactory());
        }
    }

    @ParameterizedTest
    @CsvSource({"false, true", "true, true", "false, false", "true, false"})
    void propertiesResolvedByAnEarlierAotProcessorAreStillBound(boolean replacement, boolean cacheSelected) {
        try (var aot = bindingContext(replacement); var runtime = bindingContext(replacement)) {
            var witness = witness(aot.getDefaultListableBeanFactory(), cacheSelected ? Client.class : PlainClient.class);
            aot.refreshForAotProcessing(new RuntimeHints());
            var early = new java.util.concurrent.atomic.AtomicReference<ReactiveHttpClientProperties>();
            org.springframework.beans.factory.aot.BeanFactoryInitializationAotProcessor earlierProcessor = factory -> {
                early.set(factory.getBeanProvider(ReactiveHttpClientProperties.class).getObject());
                return null;
            };
            earlierProcessor.processAheadOfTime(aot.getDefaultListableBeanFactory());
            assertThat(early.get().getClients()).isEmpty();
            runtime.refresh();
            var expected = runtime.getBeanProvider(ReactiveHttpClientProperties.class).getObject();

            assertThat(new ReactiveHttpClientBeanFactoryInitializationAotProcessor(aot.getEnvironment())
                    .processAheadOfTime(aot.getDefaultListableBeanFactory())).isNotNull();

            assertThat(aot.getBeanProvider(ReactiveHttpClientProperties.class).getObject()).isSameAs(early.get());
            assertThat(witness.config).isSameAs(early.get().getClients().get("selection"));
            assertThat(witness.config.getCache().getPolicies().get("chosen").getTtlMs())
                    .isEqualTo(expected.getClients().get("selection").getCache().getPolicies().get("chosen").getTtlMs());
            witness.assertNoBusinessResources(aot.getDefaultListableBeanFactory());
        }
    }

    @ParameterizedTest
    @CsvSource({"false, true", "true, true", "false, false", "true, false"})
    void scopedPropertiesBindTargetMetadataAndUseOneTarget(boolean prototype, boolean cacheSelected) {
        var aotCreations = new AtomicInteger();
        var runtimeCreations = new AtomicInteger();
        try (var aot = scopedBindingContext(prototype, aotCreations);
             var runtime = scopedBindingContext(prototype, runtimeCreations)) {
            var witness = witness(aot.getDefaultListableBeanFactory(), cacheSelected ? Client.class : PlainClient.class);
            aot.refreshForAotProcessing(new RuntimeHints());
            var selected = aot.getBeanProvider(ReactiveHttpClientProperties.class).getObject();
            assertThat(selected).isInstanceOf(org.springframework.aop.scope.ScopedObject.class);
            assertThat(aotCreations).hasValue(0);
            runtime.refresh();
            var expected = runtime.getBeanProvider(ReactiveHttpClientProperties.class).getObject()
                    .getClients().get("selection");
            assertThat(runtimeCreations).hasValue(1);
            assertThat(expected.getCache().getPolicies().get("chosen").getTtlMs()).isEqualTo(7000);

            assertThat(new ReactiveHttpClientBeanFactoryInitializationAotProcessor(aot.getEnvironment())
                    .processAheadOfTime(aot.getDefaultListableBeanFactory())).isNotNull();

            assertThat(witness.config.getCache().getPolicies().get("chosen").getTtlMs()).isEqualTo(7000)
                    .isEqualTo(expected.getCache().getPolicies().get("chosen").getTtlMs());
            assertThat(aotCreations).hasValue(1);
            witness.assertNoBusinessResources(aot.getDefaultListableBeanFactory());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void invalidScopedPropertiesDoNotFallBackToTheValidDefault(boolean prototype) {
        var creations = new AtomicInteger();
        try (var context = scopedBindingContext(prototype, creations)) {
            String prefix = prototype ? "prototype" : "scoped";
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("invalid-scoped", Map.of(
                    prefix + ".clients.selection.cache.policies.chosen.ttl-ms", "0")));
            var witness = witness(context.getDefaultListableBeanFactory(), Client.class);
            context.refreshForAotProcessing(new RuntimeHints());

            assertThatThrownBy(() -> new ReactiveHttpClientBeanFactoryInitializationAotProcessor(context.getEnvironment())
                    .processAheadOfTime(context.getDefaultListableBeanFactory()))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("ttl-ms");

            assertThat(creations).hasValue(1);
            assertThat(witness.config.getCache().getPolicies().get("chosen").getTtlMs()).isZero();
            witness.assertNoBusinessResources(context.getDefaultListableBeanFactory());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void earlyCreatedScopedTargetIsBoundWithoutRecreation(boolean opaque) {
        var creations = new AtomicInteger();
        try (var context = scopedBindingContext(false, creations)) {
            if (opaque) useOpaqueProgrammaticScopedProxy(context);
            var witness = witness(context.getDefaultListableBeanFactory(), Client.class);
            context.refreshForAotProcessing(new RuntimeHints());
            var proxy = context.getBeanProvider(ReactiveHttpClientProperties.class).getObject();
            var target = (ReactiveHttpClientProperties) ((org.springframework.aop.scope.ScopedObject) proxy).getTargetObject();
            assertThat(target.getClients()).isEmpty();
            assertThat(creations).hasValue(1);

            assertThat(new ReactiveHttpClientBeanFactoryInitializationAotProcessor(context.getEnvironment())
                    .processAheadOfTime(context.getDefaultListableBeanFactory())).isNotNull();

            assertThat(witness.config).isSameAs(target.getClients().get("selection"));
            assertThat(witness.config.getCache().getPolicies().get("chosen").getTtlMs()).isEqualTo(7000);
            assertThat(creations).hasValue(1);
            witness.assertNoBusinessResources(context.getDefaultListableBeanFactory());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void earlyCreatedAliasedScopedTargetUsesCanonicalBindingMetadata(boolean opaque) {
        var creations = new AtomicInteger();
        try (var context = scopedBindingContext(false, creations)) {
            context.registerAlias("scopedTarget.selected", "targetAlias");
            context.registerAlias("targetAlias", "targetAliasChain");
            useProgrammaticScopedProxy(context, opaque, "targetAliasChain");
            var factory = context.getDefaultListableBeanFactory();
            var witness = witness(factory, Client.class);
            context.refreshForAotProcessing(new RuntimeHints());
            var proxy = context.getBeanProvider(ReactiveHttpClientProperties.class).getObject();
            var target = (ReactiveHttpClientProperties) ((org.springframework.aop.scope.ScopedObject) proxy).getTargetObject();
            assertThat(target.getClients()).isEmpty();
            assertThat(factory.containsBeanDefinition("targetAliasChain")).isFalse();
            assertThat(creations).hasValue(1);
            var processors = java.util.List.copyOf(factory.getBeanPostProcessors());

            assertThat(new ReactiveHttpClientBeanFactoryInitializationAotProcessor(context.getEnvironment())
                    .processAheadOfTime(factory)).isNotNull();

            assertThat(witness.config).isSameAs(target.getClients().get("selection"));
            assertThat(witness.config.getCache().getPolicies().get("chosen").getTtlMs()).isEqualTo(7000);
            assertThat(creations).hasValue(1);
            assertThat(factory.getBeanPostProcessors()).containsExactlyElementsOf(processors);
            witness.assertNoBusinessResources(factory);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void normallyBoundScopedTargetIsNotRebound(boolean opaque) {
        var creations = new AtomicInteger();
        try (var context = scopedBindingContext(false, creations)) {
            if (opaque) useOpaqueProgrammaticScopedProxy(context);
            var witness = witness(context.getDefaultListableBeanFactory(), Client.class);
            context.refresh();
            var selected = context.getBeanProvider(ReactiveHttpClientProperties.class).getObject().getClients().get("selection");
            assertThat(selected.getCache().getPolicies().get("chosen").getTtlMs()).isEqualTo(7000);
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("later-scoped", Map.of(
                    "scoped.clients.selection.cache.policies.chosen.ttl-ms", "0")));

            assertThat(new ReactiveHttpClientBeanFactoryInitializationAotProcessor(context.getEnvironment())
                    .processAheadOfTime(context.getDefaultListableBeanFactory())).isNotNull();

            assertThat(witness.config).isSameAs(selected);
            assertThat(selected.getCache().getPolicies().get("chosen").getTtlMs()).isEqualTo(7000);
            assertThat(creations).hasValue(1);
            witness.assertNoBusinessResources(context.getDefaultListableBeanFactory());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void inheritedScopedProxyUsesParentBindingMetadataAndEnvironment(boolean opaque) {
        var creations = new AtomicInteger();
        try (var parent = scopedBindingContext(false, creations);
             var child = new AnnotationConfigApplicationContext()) {
            if (opaque) useOpaqueProgrammaticScopedProxy(parent);
            child.setParent(parent);
            child.registerBean("selected", String.class, () -> "unrelated");
            child.getEnvironment().getPropertySources().addFirst(new MapPropertySource("child-invalid", Map.of(
                    "scoped.clients.selection.cache.policies.chosen.ttl-ms", "0")));
            var witness = witness(child.getDefaultListableBeanFactory(), Client.class);
            parent.refreshForAotProcessing(new RuntimeHints());
            child.refreshForAotProcessing(new RuntimeHints());

            assertThat(new ReactiveHttpClientBeanFactoryInitializationAotProcessor(child.getEnvironment())
                    .processAheadOfTime(child.getDefaultListableBeanFactory())).isNotNull();

            assertThat(witness.config.getCache().getPolicies().get("chosen").getTtlMs()).isEqualTo(7000);
            assertThat(creations).hasValue(1);
            witness.assertNoBusinessResources(child.getDefaultListableBeanFactory());
        }
    }

    @Test
    void normalBindingIsNotRepeatedOnAnInitializedBean() {
        try (var context = bindingContext(false)) {
            var witness = witness(context.getDefaultListableBeanFactory(), Client.class);
            context.refresh();
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("later", Map.of(
                    "reactive.http.clients.selection.cache.policies.chosen.ttl-ms", "0")));
            assertThat(new ReactiveHttpClientBeanFactoryInitializationAotProcessor(context.getEnvironment())
                    .processAheadOfTime(context.getDefaultListableBeanFactory())).isNotNull();
            assertThat(witness.config.getCache().getPolicies().get("chosen").getTtlMs()).isEqualTo(5000);
            witness.assertNoBusinessResources(context.getDefaultListableBeanFactory());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void parentBindingUsesItsOwnerDespiteAnUnrelatedChildBeanWithTheSameName(boolean resolvedEarly) {
        try (var parent = bindingContext(false); var child = new AnnotationConfigApplicationContext()) {
            child.setParent(parent);
            String name = "reactive.http-" + ReactiveHttpClientProperties.class.getName();
            child.registerBean(name, String.class, () -> "unrelated");
            org.springframework.boot.context.properties.ConfigurationPropertiesBindingPostProcessor.register(child);
            child.getEnvironment().getPropertySources().addFirst(new MapPropertySource("child-invalid", Map.of(
                    "reactive.http.clients.selection.cache.policies.chosen.ttl-ms", "0")));
            var witness = witness(child.getDefaultListableBeanFactory(), Client.class);
            parent.refreshForAotProcessing(new RuntimeHints());
            child.refreshForAotProcessing(new RuntimeHints());
            if (resolvedEarly) {
                assertThat(parent.getBeanProvider(ReactiveHttpClientProperties.class).getObject().getClients()).isEmpty();
            }
            assertThat(new ReactiveHttpClientBeanFactoryInitializationAotProcessor(child.getEnvironment())
                    .processAheadOfTime(child.getDefaultListableBeanFactory())).isNotNull();
            var selected = child.getBeanProvider(ReactiveHttpClientProperties.class).getIfAvailable();
            assertThat(witness.config).isSameAs(selected.getClients().get("selection"));
            assertThat(witness.config.getCache().getPolicies().get("chosen").getTtlMs()).isEqualTo(5000);
            witness.assertNoBusinessResources(child.getDefaultListableBeanFactory());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void directlyRegisteredSingletonFollowsAnyRemainingBindingDefinition(boolean definitionBacked) {
        try (var context = bindingContext(false)) {
            var selected = properties(9000);
            String generatedName = "reactive.http-" + ReactiveHttpClientProperties.class.getName();
            var witness = witness(context.getDefaultListableBeanFactory(), Client.class);
            context.refreshForAotProcessing(new RuntimeHints());
            if (!definitionBacked) {
                context.removeBeanDefinition(generatedName);
            }
            context.getBeanFactory().registerSingleton(generatedName, selected);
            assertThat(context.containsBeanDefinition(generatedName)).isEqualTo(definitionBacked);
            assertThat(new ReactiveHttpClientBeanFactoryInitializationAotProcessor(context.getEnvironment())
                    .processAheadOfTime(context.getDefaultListableBeanFactory())).isNotNull();
            assertThat(witness.config).isSameAs(selected.getClients().get("selection"));
            assertThat(witness.config.getCache().getPolicies().get("chosen").getTtlMs())
                    .isEqualTo(definitionBacked ? 5000 : 9000);
            witness.assertNoBusinessResources(context.getDefaultListableBeanFactory());
        }
    }

    @Test
    void factoryProductsAreNotEnvironmentRebound() {
        try (var aot = bindingContext(false); var runtime = bindingContext(false)) {
            var products = new AtomicInteger();
            var definition = new RootBeanDefinition(PropertiesFactory.class, () -> new PropertiesFactory(products));
            definition.setPrimary(true);
            aot.registerBeanDefinition("selected", definition);
            runtime.registerBeanDefinition("selected", definition.cloneBeanDefinition());
            var witness = witness(aot.getDefaultListableBeanFactory(), Client.class);
            aot.refreshForAotProcessing(new RuntimeHints());
            assertThat(products).hasValue(0);
            assertThat(new ReactiveHttpClientBeanFactoryInitializationAotProcessor(aot.getEnvironment())
                    .processAheadOfTime(aot.getDefaultListableBeanFactory())).isNotNull();
            assertThat(products).hasValue(1);
            runtime.refresh();
            var expected = runtime.getBeanProvider(ReactiveHttpClientProperties.class).getIfAvailable();
            assertThat(products).hasValue(2);
            assertThat(witness.config.getCache().getPolicies().get("chosen").getTtlMs()).isEqualTo(4000)
                    .isEqualTo(expected.getClients().get("selection").getCache().getPolicies().get("chosen").getTtlMs());
            witness.assertNoBusinessResources(aot.getDefaultListableBeanFactory());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void invalidBoundEnvironmentFailsSelectedPolicyValidation(boolean resolvedEarly) {
        try (var context = bindingContext(false)) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("invalid", Map.of(
                    "reactive.http.clients.selection.cache.policies.chosen.ttl-ms", "0")));
            var witness = witness(context.getDefaultListableBeanFactory(), Client.class);
            context.refreshForAotProcessing(new RuntimeHints());
            if (resolvedEarly) {
                assertThat(context.getBeanProvider(ReactiveHttpClientProperties.class).getObject().getClients()).isEmpty();
            }
            assertThatThrownBy(() -> new ReactiveHttpClientBeanFactoryInitializationAotProcessor(context.getEnvironment())
                    .processAheadOfTime(context.getDefaultListableBeanFactory()))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("ttl-ms");
            witness.assertNoBusinessResources(context.getDefaultListableBeanFactory());
        }
    }

    @Test
    void foreignClientFactoryDoesNotResolvePropertiesOrMetadata() {
        var factory = new DefaultListableBeanFactory();
        var witness = witness(factory, Client.class);
        factory.registerBeanDefinition("business", new RootBeanDefinition(ForeignClientFactory.class));
        factory.registerBeanDefinition("properties", new RootBeanDefinition(ReactiveHttpClientProperties.class,
                () -> { throw new AssertionError("foreign client must not select starter properties"); }));
        assertThat(processor().processAheadOfTime(factory)).isNull();
        assertThat(witness.metadataCreations).hasValue(0);
        assertThat(witness.businessCreations).hasValue(0);
        assertThat(factory.containsSingleton("properties")).isFalse();
        assertThat(factory.containsSingleton("business")).isFalse();
        assertThat(witness.registry.getMeters()).isEmpty();
    }

    static class ForeignClientFactory implements FactoryBean<Client> {
        ForeignClientFactory() { throw new AssertionError("foreign factory must remain uninstantiated"); }
        @Override public Client getObject() { throw new AssertionError("foreign product must remain uninstantiated"); }
        @Override public Class<?> getObjectType() { return Client.class; }
    }

    private static AnnotationConfigApplicationContext bindingContext(boolean replacement) {
        var context = new AnnotationConfigApplicationContext();
        context.register(replacement ? ReplacementBinding.class : DefaultBinding.class);
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("binding", Map.of(
                "reactive.http.clients.selection.cache.policies.chosen.ttl-ms", "5000",
                "reactive.http.clients.selection.cache.policies.chosen.maximum-size", "16",
                "reactive.http.clients.selection.cache.policies.chosen.shared-response", "true",
                "reactive.http.clients.selection.cache.customizations.Builder", "SAFE",
                "replacement.clients.selection.cache.policies.chosen.ttl-ms", "7000",
                "replacement.clients.selection.cache.policies.chosen.maximum-size", "16",
                "replacement.clients.selection.cache.policies.chosen.shared-response", "true",
                "replacement.clients.selection.cache.customizations.Builder", "SAFE")));
        return context;
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(ReactiveHttpClientProperties.class)
    static class DefaultBinding { }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(ReactiveHttpClientProperties.class)
    static class ReplacementBinding {
        @Bean @Primary @ConfigurationProperties("replacement")
        ReactiveHttpClientProperties selected() { return new ReactiveHttpClientProperties(); }
    }

    private static AnnotationConfigApplicationContext scopedBindingContext(boolean prototype, AtomicInteger creations) {
        var context = bindingContext(false);
        context.register(prototype ? PrototypeProxyBinding.class : ScopedProxyBinding.class);
        String prefix = prototype ? "prototype" : "scoped";
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("scoped-binding", Map.of(
                prefix + ".clients.selection.cache.policies.chosen.ttl-ms", "7000",
                prefix + ".clients.selection.cache.policies.chosen.maximum-size", "16",
                prefix + ".clients.selection.cache.policies.chosen.shared-response", "true",
                prefix + ".clients.selection.cache.customizations.Builder", "SAFE")));
        context.getBeanFactory().registerScope("test", new org.springframework.context.support.SimpleThreadScope());
        context.getBeanFactory().registerSingleton("targetCreations", creations);
        return context;
    }

    private static void useOpaqueProgrammaticScopedProxy(AnnotationConfigApplicationContext context) {
        useProgrammaticScopedProxy(context, true, "scopedTarget.selected");
    }

    private static void useProgrammaticScopedProxy(AnnotationConfigApplicationContext context, boolean opaque, String targetName) {
        context.addBeanFactoryPostProcessor(factory -> {
            var proxy = new RootBeanDefinition(org.springframework.aop.scope.ScopedProxyFactoryBean.class, () -> {
                var value = new org.springframework.aop.scope.ScopedProxyFactoryBean();
                value.setTargetBeanName(targetName);
                value.setOpaque(opaque);
                return value;
            });
            proxy.setPrimary(true);
            proxy.setAttribute(FactoryBean.OBJECT_TYPE_ATTRIBUTE, ReactiveHttpClientProperties.class);
            var registry = (org.springframework.beans.factory.support.BeanDefinitionRegistry) factory;
            registry.removeBeanDefinition("selected");
            registry.registerBeanDefinition("selected", proxy);
        });
    }

    @Configuration(proxyBeanMethods = false)
    static class ScopedProxyBinding {
        @Bean @Primary @ConfigurationProperties("scoped")
        @Scope(value = "test", proxyMode = ScopedProxyMode.TARGET_CLASS)
        ReactiveHttpClientProperties selected(AtomicInteger targetCreations) {
            targetCreations.incrementAndGet();
            return new ReactiveHttpClientProperties();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class PrototypeProxyBinding {
        @Bean @Primary @ConfigurationProperties("prototype")
        @Scope(value = "prototype", proxyMode = ScopedProxyMode.TARGET_CLASS)
        ReactiveHttpClientProperties selected(AtomicInteger targetCreations) {
            targetCreations.incrementAndGet();
            return new ReactiveHttpClientProperties();
        }
    }

    private static DefaultListableBeanFactory propertiesFactory(Shape shape, AtomicInteger products,
                                                                 AtomicInteger factories) {
        var factory = new DefaultListableBeanFactory();
        if (shape == Shape.LAZY || shape == Shape.PROTOTYPE) {
            var definition = new RootBeanDefinition(ReactiveHttpClientProperties.class, () -> {
                products.incrementAndGet();
                return properties(4000);
            });
            definition.setLazyInit(true);
            if (shape == Shape.PROTOTYPE) definition.setScope(BeanDefinition.SCOPE_PROTOTYPE);
            factory.registerBeanDefinition("properties", definition);
        } else if (shape == Shape.DIRECT_FACTORY) {
            factories.incrementAndGet();
            factory.registerSingleton("properties", new PropertiesFactory(products));
        } else {
            var definition = new RootBeanDefinition(
                    shape == Shape.RAW_FACTORY ? RawPropertiesFactory.class : PropertiesFactory.class);
            definition.setInstanceSupplier(() -> {
                factories.incrementAndGet();
                return shape == Shape.RAW_FACTORY ? new RawPropertiesFactory(products) : new PropertiesFactory(products);
            });
            definition.setLazyInit(true);
            if (shape == Shape.PROTOTYPE_FACTORY) definition.setScope(BeanDefinition.SCOPE_PROTOTYPE);
            factory.registerBeanDefinition("properties", definition);
        }
        return factory;
    }

    static class PropertiesFactory implements FactoryBean<ReactiveHttpClientProperties> {
        final AtomicInteger products;
        PropertiesFactory(AtomicInteger products) { this.products = products; }
        @Override public ReactiveHttpClientProperties getObject() { products.incrementAndGet(); return properties(4000); }
        @Override public Class<?> getObjectType() { return ReactiveHttpClientProperties.class; }
    }

    @SuppressWarnings("rawtypes")
    static class RawPropertiesFactory implements FactoryBean {
        final AtomicInteger products;
        RawPropertiesFactory(AtomicInteger products) { this.products = products; }
        @Override public Object getObject() { products.incrementAndGet(); return properties(4000); }
        @Override public Class<?> getObjectType() { return ReactiveHttpClientProperties.class; }
    }

    private static ReactiveHttpClientBeanFactoryInitializationAotProcessor processor() {
        var environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("valid-fallback", Map.of(
                "reactive.http.clients.selection.cache.policies.chosen.ttl-ms", "1000",
                "reactive.http.clients.selection.cache.policies.chosen.maximum-size", "16",
                "reactive.http.clients.selection.cache.policies.chosen.shared-response", "true",
                "reactive.http.clients.selection.cache.customizations.Builder", "SAFE")));
        return new ReactiveHttpClientBeanFactoryInitializationAotProcessor(environment);
    }

    private static ReactiveHttpClientProperties properties(long ttl) {
        var policy = new ReactiveHttpClientProperties.CachePolicyConfig();
        policy.setTtlMs(ttl);
        policy.setMaximumSize(16L);
        policy.setSharedResponse(true);
        var config = new ReactiveHttpClientProperties.ClientConfig();
        config.setAuthProvider("AuthProvider");
        config.getCache().getCustomizations().put("Builder", ReactiveHttpClientProperties.CacheCustomizationSafety.SAFE);
        config.getCache().getPolicies().put("chosen", policy);
        var properties = new ReactiveHttpClientProperties();
        properties.getObservability().getCache().setEnabled(true);
        properties.getClients().put("selection", config);
        return properties;
    }

    private static Witness witness(DefaultListableBeanFactory factory, Class<?> clientType) {
        var witness = new Witness();
        factory.registerBeanDefinition("metadata", new RootBeanDefinition(MethodMetadataCache.class, () -> {
            witness.metadataCreations.incrementAndGet();
            return witness;
        }));
        var client = new RootBeanDefinition(ReactiveHttpClientFactoryBean.class, () -> {
            witness.businessCreations.incrementAndGet();
            throw new AssertionError("business factory must not be created by AOT selection");
        });
        client.setLazyInit(true);
        client.getPropertyValues().add("type", clientType);
        client.setAttribute(FactoryBean.OBJECT_TYPE_ATTRIBUTE, clientType);
        factory.registerBeanDefinition("business", client);
        for (Class<?> type : new Class<?>[]{AuthProvider.class, ConnectionProvider.class, WebClient.Builder.class}) {
            var definition = new RootBeanDefinition(type, () -> {
                witness.businessCreations.incrementAndGet();
                throw new AssertionError("business resource must not be created by AOT selection");
            });
            definition.setLazyInit(true);
            factory.registerBeanDefinition(type.getSimpleName(), definition);
        }
        factory.registerSingleton("meters", witness.registry);
        return witness;
    }

    static class Witness extends MethodMetadataCache {
        ReactiveHttpClientProperties.ClientConfig config;
        final AtomicInteger metadataCreations = new AtomicInteger();
        final AtomicInteger businessCreations = new AtomicInteger();
        final SimpleMeterRegistry registry = new SimpleMeterRegistry();

        @Override public void validateDeclarativeCachePolicies(Class<?> type, String name,
                                                               ReactiveHttpClientProperties.ClientConfig config) {
            this.config = config;
            super.validateDeclarativeCachePolicies(type, name, config);
        }

        void assertNoBusinessResources(DefaultListableBeanFactory factory) {
            assertThat(metadataCreations).hasValue(1);
            assertThat(businessCreations).hasValue(0);
            assertThat(factory.containsSingleton("business")).isFalse();
            assertThat(registry.getMeters()).isEmpty();
        }
    }

    @ReactiveHttpClient(name = "selection", baseUrl = "http://localhost")
    interface Client { @GET("/read") @CacheResponse("chosen") Mono<String> get(); }

    @ReactiveHttpClient(name = "selection", baseUrl = "http://localhost")
    interface PlainClient { @GET("/read") Mono<String> get(); }
}
