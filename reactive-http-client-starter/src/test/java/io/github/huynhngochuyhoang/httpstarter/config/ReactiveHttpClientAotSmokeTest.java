package io.github.huynhngochuyhoang.httpstarter.config;

import io.github.huynhngochuyhoang.httpstarter.annotation.*;
import io.github.huynhngochuyhoang.httpstarter.auth.AuthProviderFactory;
import io.github.huynhngochuyhoang.httpstarter.config.smoke.AotSmokeClient;
import io.github.huynhngochuyhoang.httpstarter.config.smoke.InheritedAotSmokeClient;
import io.github.huynhngochuyhoang.httpstarter.core.MethodMetadata;
import io.github.huynhngochuyhoang.httpstarter.core.MethodMetadataCache;
import io.github.huynhngochuyhoang.httpstarter.core.ReactiveHttpClientFactoryBean;
import io.github.huynhngochuyhoang.httpstarter.enable.EnableReactiveHttpClients;
import org.junit.jupiter.api.Test;
import org.springframework.aot.generate.ClassNameGenerator;
import org.springframework.aot.generate.DefaultGenerationContext;
import org.springframework.aot.generate.InMemoryGeneratedFiles;
import org.springframework.aot.hint.ExecutableHint;
import org.springframework.aot.hint.ExecutableMode;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotContribution;
import org.springframework.beans.factory.support.FactoryBeanRegistrySupport;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.annotation.*;
import org.springframework.context.aot.ApplicationContextAotGenerator;
import org.springframework.core.env.MapPropertySource;
import org.springframework.javapoet.ClassName;
import reactor.core.publisher.Mono;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReactiveHttpClientAotSmokeTest {

    @Test
    void runtimeHintsCoverAnnotationsAndConfigurationProperties() throws Exception {
        RuntimeHints hints = new RuntimeHints();

        new ReactiveHttpClientRuntimeHints().registerHints(hints, getClass().getClassLoader());

        assertThat(RuntimeHintsPredicates.reflection().onMethodInvocation(method(ReactiveHttpClient.class, "name")))
                .accepts(hints);
        assertThat(RuntimeHintsPredicates.reflection().onMethodInvocation(method(GET.class, "value")))
                .accepts(hints);
        assertThat(RuntimeHintsPredicates.reflection().onMethodInvocation(method(HEAD.class, "value")))
                .accepts(hints);
        assertThat(RuntimeHintsPredicates.reflection().onMethodInvocation(method(OPTIONS.class, "value")))
                .accepts(hints);
        assertThat(RuntimeHintsPredicates.reflection().onMethodInvocation(method(CacheResponse.class, "value")))
                .accepts(hints);
        assertThat(RuntimeHintsPredicates.reflection().onMethodInvocation(method(CacheKey.class, "value")))
                .accepts(hints);
        assertThat(hints.reflection().getTypeHint(CacheDisabled.class)).isNotNull();
        assertThat(hints.reflection().getTypeHint(Body.class)).isNotNull();
        assertThat(hints.reflection().getTypeHint(MultipartBody.class)).isNotNull();
        assertThat(hints.reflection().getTypeHint(Body.class).getMemberCategories()).isEmpty();
        assertThat(hints.reflection().getTypeHint(MultipartBody.class).getMemberCategories()).isEmpty();
        assertThat(hints.reflection().getTypeHint(ReactiveHttpClient.class).getMemberCategories())
                .isEmpty();
        assertThat(hints.reflection().getTypeHint(ReactiveHttpClientProperties.class).getMemberCategories())
                .isEmpty();
        assertThat(hints.reflection().getTypeHint(ReactiveHttpClientProperties.class).fields())
                .isEmpty();
        assertThat(RuntimeHintsPredicates.reflection().onConstructorInvocation(
                ReactiveHttpClientProperties.class.getDeclaredConstructor()))
                .accepts(hints);
        assertThat(RuntimeHintsPredicates.reflection().onMethodInvocation(
                ReactiveHttpClientProperties.ClientConfig.class.getMethod("setBaseUrl", String.class)))
                .accepts(hints);
        assertThat(RuntimeHintsPredicates.reflection().onMethodInvocation(
                ReactiveHttpClientProperties.DiagnosticsEndpointConfig.class.getMethod("setEnabled", boolean.class)))
                .accepts(hints);
        assertThat(RuntimeHintsPredicates.reflection().onMethodInvocation(
                ReactiveHttpClientProperties.CacheObservabilityConfig.class.getMethod("setEnabled", boolean.class)))
                .accepts(hints);
        assertThat(RuntimeHintsPredicates.reflection().onMethodInvocation(
                ReactiveHttpClientProperties.CachePolicyConfig.class.getMethod("setTtlMs", Long.class)))
                .accepts(hints);
        assertThat(ReactiveHttpClientProperties.class.getDeclaredClasses())
                .filteredOn(type -> Modifier.isPublic(type.getModifiers()))
                .allSatisfy(type -> assertThat(hints.reflection().getTypeHint(type))
                        .as(type.getName())
                        .isNotNull());
        assertThat(RuntimeHintsPredicates.reflection().onMethodInvocation(
                FactoryBeanRegistrySupport.class.getDeclaredMethod(
                        "getCachedObjectForFactoryBean", String.class)))
                .accepts(hints);
        Class<?> candidates = Class.forName(
                "io.github.huynhngochuyhoang.httpstarter.core.AuthProviderFactoryCandidates");
        assertThat(RuntimeHintsPredicates.reflection().onMethodInvocation(
                candidates.getDeclaredMethod("authProviderFactoryLookup", AuthProviderFactory.class)))
                .accepts(hints);
        assertThat(RuntimeHintsPredicates.resource().forResource(ReactiveHttpClientRuntimeHints.POM_PROPERTIES_RESOURCE))
                .accepts(hints);
        Class<?> caffeineCache = Class.forName(ReactiveHttpClientRuntimeHints.CAFFEINE_LOCAL_CACHE);
        assertThat(caffeineCache.getDeclaredConstructors())
                .allSatisfy(constructor -> assertThat(
                        RuntimeHintsPredicates.reflection().onConstructorInvocation(constructor))
                        .accepts(hints));
        assertThat(RuntimeHintsPredicates.reflection().onField(
                caffeineCache.getDeclaredField("FACTORY")))
                .accepts(hints);
    }

    @Test
    void beanFactoryAotProcessorRegistersProxyHintForClientFactoryBeans() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        RootBeanDefinition beanDefinition = new RootBeanDefinition(ReactiveHttpClientFactoryBean.class);
        beanDefinition.getPropertyValues().add("type", AotSmokeClient.class);
        beanDefinition.setAttribute(FactoryBean.OBJECT_TYPE_ATTRIBUTE, AotSmokeClient.class);
        context.registerBeanDefinition(AotSmokeClient.class.getName(), beanDefinition);
        BeanFactoryInitializationAotContribution contribution =
                new ReactiveHttpClientBeanFactoryInitializationAotProcessor()
                        .processAheadOfTime(context.getDefaultListableBeanFactory());
        DefaultGenerationContext generationContext = newGenerationContext();

        contribution.applyTo(generationContext, null);

        assertThat(RuntimeHintsPredicates.proxies().forInterfaces(AotSmokeClient.class))
                .accepts(generationContext.getRuntimeHints());
        assertThat(RuntimeHintsPredicates.reflection().onMethodInvocation(method(AotSmokeClient.class, "ping")))
                .accepts(generationContext.getRuntimeHints());
        context.close();
    }

    @Test
    void beanFactoryAotProcessorRegistersHintsForFactoryMethodClientFactories() {
        AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext(FactoryMethodAotConfiguration.class);
        RootBeanDefinition beanDefinition = (RootBeanDefinition) context.getBeanFactory()
                .getBeanDefinition("factoryMethodAotClient");
        beanDefinition.setAttribute(FactoryBean.OBJECT_TYPE_ATTRIBUTE, FactoryMethodAotClient.class);

        BeanFactoryInitializationAotContribution contribution =
                new ReactiveHttpClientBeanFactoryInitializationAotProcessor()
                        .processAheadOfTime(context.getDefaultListableBeanFactory());
        DefaultGenerationContext generationContext = newGenerationContext();

        assertThat(beanDefinition.getResolvableType().resolve())
                .isEqualTo(ReactiveHttpClientFactoryBean.class);
        assertThat(contribution).isNotNull();
        contribution.applyTo(generationContext, null);

        assertThat(RuntimeHintsPredicates.proxies().forInterfaces(FactoryMethodAotClient.class))
                .accepts(generationContext.getRuntimeHints());
        assertThat(RuntimeHintsPredicates.reflection().onMethodInvocation(
                method(FactoryMethodAotClient.class, "ping")))
                .accepts(generationContext.getRuntimeHints());
        context.close();
    }

    @Test
    void beanFactoryAotProcessorRegistersPublicMethodHintForInheritedClientMethods() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        RootBeanDefinition beanDefinition = new RootBeanDefinition(ReactiveHttpClientFactoryBean.class);
        beanDefinition.getPropertyValues().add("type", ChildSmokeClient.class);
        beanDefinition.setAttribute(FactoryBean.OBJECT_TYPE_ATTRIBUTE, ChildSmokeClient.class);
        context.registerBeanDefinition(ChildSmokeClient.class.getName(), beanDefinition);
        BeanFactoryInitializationAotContribution contribution =
                new ReactiveHttpClientBeanFactoryInitializationAotProcessor()
                        .processAheadOfTime(context.getDefaultListableBeanFactory());
        DefaultGenerationContext generationContext = newGenerationContext();

        contribution.applyTo(generationContext, null);

        assertThat(RuntimeHintsPredicates.proxies().forInterfaces(ChildSmokeClient.class))
                .accepts(generationContext.getRuntimeHints());
        assertThat(RuntimeHintsPredicates.reflection().onMethodInvocation(
                method(ParentSmokeOperations.class, "parentPing")))
                .accepts(generationContext.getRuntimeHints());
        assertThat(generationContext.getRuntimeHints().reflection()
                .getTypeHint(ChildSmokeClient.class).methods().map(ExecutableHint::getName))
                .contains("parentPing");
        context.close();
    }

    @Test
    void beanFactoryAotProcessorIgnoresUnresolvableForeignFactoryBeanObjectType() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        RootBeanDefinition beanDefinition = new RootBeanDefinition(Object.class);
        beanDefinition.setAttribute(FactoryBean.OBJECT_TYPE_ATTRIBUTE, "com.example.DoesNotExist");
        context.registerBeanDefinition("foreignFactoryBean", beanDefinition);

        BeanFactoryInitializationAotContribution contribution =
                new ReactiveHttpClientBeanFactoryInitializationAotProcessor()
                        .processAheadOfTime(context.getDefaultListableBeanFactory());

        assertThat(contribution).isNull();
        context.close();
    }

    @Test
    void beanFactoryAotProcessorRejectsUnsupportedDeclarativeReturnType() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        RootBeanDefinition beanDefinition = new RootBeanDefinition(ReactiveHttpClientFactoryBean.class);
        beanDefinition.getPropertyValues().add("type", InvalidAotReturnClient.class);
        beanDefinition.setAttribute(FactoryBean.OBJECT_TYPE_ATTRIBUTE, InvalidAotReturnClient.class);
        context.registerBeanDefinition(InvalidAotReturnClient.class.getName(), beanDefinition);

        assertThatThrownBy(() -> new ReactiveHttpClientBeanFactoryInitializationAotProcessor()
                .processAheadOfTime(context.getDefaultListableBeanFactory()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("reactive HTTP client 'invalid-aot-return'")
                .hasMessageContaining("concreteClient=" + InvalidAotReturnClient.class.getName())
                .hasMessageContaining("resolvedResponseType=reactor.core.publisher.Flux<java.lang.String>");
        context.close();
    }

    @Test
    void beanFactoryAotProcessorRejectsInvalidRequestParameterGrammar() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        RootBeanDefinition beanDefinition = new RootBeanDefinition(ReactiveHttpClientFactoryBean.class);
        beanDefinition.getPropertyValues().add("type", InvalidAotParameterClient.class);
        beanDefinition.setAttribute(FactoryBean.OBJECT_TYPE_ATTRIBUTE, InvalidAotParameterClient.class);
        context.registerBeanDefinition(InvalidAotParameterClient.class.getName(), beanDefinition);

        assertThatThrownBy(() -> new ReactiveHttpClientBeanFactoryInitializationAotProcessor()
                .processAheadOfTime(context.getDefaultListableBeanFactory()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Reactive HTTP client 'invalid-aot-parameter'")
                .hasMessageContaining("parameterIndex=0")
                .hasMessageContaining("conflicting request-binding roles")
                .hasMessageContaining("@PathVar(\"id\")")
                .hasMessageContaining("@QueryParam(\"id\")");
        context.close();
    }

    @Test
    void beanFactoryAotProcessorRejectsAuthorityInAnnotationTemplate() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        RootBeanDefinition beanDefinition = new RootBeanDefinition(ReactiveHttpClientFactoryBean.class);
        beanDefinition.getPropertyValues().add("type", InvalidAotUriClient.class);
        beanDefinition.setAttribute(FactoryBean.OBJECT_TYPE_ATTRIBUTE, InvalidAotUriClient.class);
        context.registerBeanDefinition(InvalidAotUriClient.class.getName(), beanDefinition);

        assertThatThrownBy(() -> new ReactiveHttpClientBeanFactoryInitializationAotProcessor()
                .processAheadOfTime(context.getDefaultListableBeanFactory()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("InvalidAotUriClient")
                .hasMessageContaining("scheme or authority")
                .hasMessageNotContaining("secret-value");
        context.close();
    }

    @Test
    void beanFactoryAotProcessorRejectsSelectedCacheOnIneligibleMethod() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        RootBeanDefinition beanDefinition = new RootBeanDefinition(ReactiveHttpClientFactoryBean.class);
        beanDefinition.getPropertyValues().add("type", InvalidAotCacheClient.class);
        beanDefinition.setAttribute(FactoryBean.OBJECT_TYPE_ATTRIBUTE, InvalidAotCacheClient.class);
        context.registerBeanDefinition(InvalidAotCacheClient.class.getName(), beanDefinition);
        ReactiveHttpClientProperties.CachePolicyConfig policy = new ReactiveHttpClientProperties.CachePolicyConfig();
        policy.setTtlMs(1_000L);
        policy.setMaximumSize(100L);
        ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();
        config.getCache().getPolicies().put("selected", policy);
        ReactiveHttpClientProperties properties = new ReactiveHttpClientProperties();
        properties.setClients(Map.of("invalid-aot-cache", config));
        context.getBeanFactory().registerSingleton("reactiveHttpClientProperties", properties);

        assertThatThrownBy(() -> new ReactiveHttpClientBeanFactoryInitializationAotProcessor()
                .processAheadOfTime(context.getDefaultListableBeanFactory()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("invalid-aot-cache")
                .hasMessageContaining("concreteClient=" + InvalidAotCacheClient.class.getName())
                .hasMessageContaining("method=")
                .hasMessageContaining("only GET methods");
        context.close();
    }

    @Test
    void beanFactoryAotProcessorRegistersCacheKeyRecordAccessors() throws Exception {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        RootBeanDefinition beanDefinition = new RootBeanDefinition(ReactiveHttpClientFactoryBean.class);
        beanDefinition.getPropertyValues().add("type", CacheRecordAotClient.class);
        beanDefinition.setAttribute(FactoryBean.OBJECT_TYPE_ATTRIBUTE, CacheRecordAotClient.class);
        context.registerBeanDefinition(CacheRecordAotClient.class.getName(), beanDefinition);
        ReactiveHttpClientProperties.CachePolicyConfig policy = new ReactiveHttpClientProperties.CachePolicyConfig();
        policy.setTtlMs(1_000L);
        policy.setMaximumSize(100L);
        policy.setVaryByParameters(List.of("tenant"));
        policy.setVaryByHeaders(List.of("Idempotency-Key"));
        ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();
        config.getCache().getPolicies().put("selected", policy);
        ReactiveHttpClientProperties properties = new ReactiveHttpClientProperties();
        properties.setClients(Map.of("cache-record-aot", config));
        context.getBeanFactory().registerSingleton("reactiveHttpClientProperties", properties);
        BeanFactoryInitializationAotContribution contribution =
                new ReactiveHttpClientBeanFactoryInitializationAotProcessor()
                        .processAheadOfTime(context.getDefaultListableBeanFactory());
        DefaultGenerationContext generationContext = newGenerationContext();

        contribution.applyTo(generationContext, null);

        assertThat(RuntimeHintsPredicates.reflection().onMethodInvocation(
                CacheRecordVariant.class.getMethod("value")))
                .accepts(generationContext.getRuntimeHints());
        assertThat(RuntimeHintsPredicates.reflection().onMethodInvocation(
                CacheRecordVariant.class.getMethod("version")))
                .accepts(generationContext.getRuntimeHints());
        assertThat(RuntimeHintsPredicates.resource().forResource(
                CacheRecordVariant.class.getName().replace('.', '/') + ".class"))
                .accepts(generationContext.getRuntimeHints());
        context.close();
    }

    @Test
    void beanFactoryAotProcessorValidatesInheritedCacheMetadataAndRegistersItsRecordAccessors()
            throws Exception {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        RootBeanDefinition beanDefinition = new RootBeanDefinition(ReactiveHttpClientFactoryBean.class);
        beanDefinition.getPropertyValues().add("type", InheritedCacheAotClient.class);
        beanDefinition.setAttribute(FactoryBean.OBJECT_TYPE_ATTRIBUTE, InheritedCacheAotClient.class);
        context.registerBeanDefinition(InheritedCacheAotClient.class.getName(), beanDefinition);
        ReactiveHttpClientProperties.CachePolicyConfig policy = new ReactiveHttpClientProperties.CachePolicyConfig();
        policy.setTtlMs(1_000L);
        policy.setMaximumSize(100L);
        policy.setSharedResponse(true);
        policy.setVaryByParameters(List.of("tenant"));
        ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();
        config.getCache().getPolicies().put("selected", policy);
        ReactiveHttpClientProperties properties = new ReactiveHttpClientProperties();
        properties.setClients(Map.of("inherited-cache-aot", config));
        context.getBeanFactory().registerSingleton("reactiveHttpClientProperties", properties);
        BeanFactoryInitializationAotContribution contribution =
                new ReactiveHttpClientBeanFactoryInitializationAotProcessor()
                        .processAheadOfTime(context.getDefaultListableBeanFactory());
        DefaultGenerationContext generationContext = newGenerationContext();

        contribution.applyTo(generationContext, null);

        assertThat(RuntimeHintsPredicates.proxies().forInterfaces(InheritedCacheAotClient.class))
                .accepts(generationContext.getRuntimeHints());
        assertThat(RuntimeHintsPredicates.reflection().onMethodInvocation(
                InheritedCacheAotOperations.class.getMethod("get", CacheRecordVariant.class)))
                .accepts(generationContext.getRuntimeHints());
        assertThat(RuntimeHintsPredicates.reflection().onMethodInvocation(
                CacheRecordVariant.class.getMethod("value")))
                .accepts(generationContext.getRuntimeHints());
        context.close();
    }

    @Test
    void beanFactoryAotProcessorBindsCachePolicyFromTheAotEnvironment() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource(
                "aot-cache-policy",
                Map.of(
                        "reactive.http.clients.inherited-cache-aot.cache.policies.selected.ttl-ms", "1000",
                        "reactive.http.clients.inherited-cache-aot.cache.policies.selected.maximum-size", "100",
                        "reactive.http.clients.inherited-cache-aot.cache.policies.selected.shared-response", "true",
                        "reactive.http.clients.inherited-cache-aot.cache.policies.selected.vary-by-parameters[0]",
                        "tenant")));
        RootBeanDefinition beanDefinition = new RootBeanDefinition(ReactiveHttpClientFactoryBean.class);
        beanDefinition.getPropertyValues().add("type", InheritedCacheAotClient.class);
        beanDefinition.setAttribute(FactoryBean.OBJECT_TYPE_ATTRIBUTE, InheritedCacheAotClient.class);
        context.registerBeanDefinition(InheritedCacheAotClient.class.getName(), beanDefinition);

        BeanFactoryInitializationAotContribution contribution =
                new ReactiveHttpClientBeanFactoryInitializationAotProcessor(context.getEnvironment())
                        .processAheadOfTime(context.getDefaultListableBeanFactory());

        assertThat(contribution).isNotNull();
        context.close();
    }

    @Test
    void beanFactoryAotProcessorUsesPrimaryProgrammaticPropertiesBeforeEnvironmentBinding() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        RootBeanDefinition clientDefinition = new RootBeanDefinition(ReactiveHttpClientFactoryBean.class);
        clientDefinition.getPropertyValues().add("type", InheritedCacheAotClient.class);
        clientDefinition.setAttribute(FactoryBean.OBJECT_TYPE_ATTRIBUTE, InheritedCacheAotClient.class);
        context.registerBeanDefinition(InheritedCacheAotClient.class.getName(), clientDefinition);

        RootBeanDefinition defaultProperties = new RootBeanDefinition(ReactiveHttpClientProperties.class);
        context.registerBeanDefinition("defaultReactiveHttpClientProperties", defaultProperties);

        ReactiveHttpClientProperties.CachePolicyConfig policy =
                new ReactiveHttpClientProperties.CachePolicyConfig();
        policy.setTtlMs(1_000L);
        policy.setMaximumSize(100L);
        policy.setSharedResponse(true);
        policy.setVaryByParameters(List.of("tenant"));
        ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();
        config.getCache().getPolicies().put("selected", policy);
        ReactiveHttpClientProperties properties = new ReactiveHttpClientProperties();
        properties.setClients(Map.of("inherited-cache-aot", config));
        RootBeanDefinition customProperties = new RootBeanDefinition(ReactiveHttpClientProperties.class);
        customProperties.setInstanceSupplier(() -> properties);
        customProperties.setPrimary(true);
        context.registerBeanDefinition("customReactiveHttpClientProperties", customProperties);

        BeanFactoryInitializationAotContribution contribution =
                new ReactiveHttpClientBeanFactoryInitializationAotProcessor(context.getEnvironment())
                        .processAheadOfTime(context.getDefaultListableBeanFactory());

        assertThat(contribution).isNotNull();
        assertThat(context.getBeanFactory().containsSingleton("customReactiveHttpClientProperties")).isTrue();
        assertThat(context.getBeanFactory().containsSingleton("defaultReactiveHttpClientProperties")).isFalse();
        context.close();
    }

    @Test
    void beanFactoryAotProcessorGuardsRecursiveGenericParameterTraversal() throws Exception {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        RootBeanDefinition beanDefinition = new RootBeanDefinition(ReactiveHttpClientFactoryBean.class);
        beanDefinition.getPropertyValues().add("type", RecursiveGenericAotClient.class);
        beanDefinition.setAttribute(FactoryBean.OBJECT_TYPE_ATTRIBUTE, RecursiveGenericAotClient.class);
        context.registerBeanDefinition(RecursiveGenericAotClient.class.getName(), beanDefinition);
        BeanFactoryInitializationAotContribution contribution =
                new ReactiveHttpClientBeanFactoryInitializationAotProcessor()
                        .processAheadOfTime(context.getDefaultListableBeanFactory());
        DefaultGenerationContext generationContext = newGenerationContext();

        contribution.applyTo(generationContext, null);

        Method method = RecursiveGenericAotClient.class.getMethod("get", Comparable.class);
        assertThat(RuntimeHintsPredicates.reflection().onMethodInvocation(method))
                .accepts(generationContext.getRuntimeHints());
        context.close();
    }

    @Test
    void applicationRuntimeHintsCanCoverContextOnlyCacheRecords() throws Exception {
        RuntimeHints hints = new RuntimeHints();

        new CacheContextRuntimeHints().registerHints(hints, getClass().getClassLoader());

        assertThat(RuntimeHintsPredicates.reflection().onMethodInvocation(
                CacheContextVariant.class.getMethod("region"))).accepts(hints);
        assertThat(RuntimeHintsPredicates.reflection().onMethodInvocation(
                CacheContextVariant.class.getMethod("tier"))).accepts(hints);
    }

    @Test
    void beanFactoryAotProcessorUsesReplacementMethodMetadataCache() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        RootBeanDefinition beanDefinition = new RootBeanDefinition(ReactiveHttpClientFactoryBean.class);
        beanDefinition.getPropertyValues().add("type", ReplacementMetadataClient.class);
        beanDefinition.setAttribute(FactoryBean.OBJECT_TYPE_ATTRIBUTE, ReplacementMetadataClient.class);
        context.registerBeanDefinition(ReplacementMetadataClient.class.getName(), beanDefinition);
        context.getBeanFactory().registerSingleton("methodMetadataCache", new ReplacementMethodMetadataCache());

        assertThat(new ReactiveHttpClientBeanFactoryInitializationAotProcessor()
                .processAheadOfTime(context.getDefaultListableBeanFactory()))
                .isNotNull();
        context.close();
    }

    @Test
    void beanFactoryAotProcessorIgnoresAnnotatedClientsBackedByForeignFactoryBeans() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        RootBeanDefinition beanDefinition = new RootBeanDefinition(ReplacementClientFactoryBean.class);
        beanDefinition.setAttribute(FactoryBean.OBJECT_TYPE_ATTRIBUTE, ForeignReplacementClient.class);
        context.registerBeanDefinition(ForeignReplacementClient.class.getName(), beanDefinition);

        BeanFactoryInitializationAotContribution contribution =
                new ReactiveHttpClientBeanFactoryInitializationAotProcessor()
                        .processAheadOfTime(context.getDefaultListableBeanFactory());

        assertThat(contribution).isNull();
        context.close();
    }

    @Test
    void annotatedClientApplicationCanBeProcessedAheadOfTime() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.register(AotSmokeApplication.class);
        DefaultGenerationContext generationContext = newGenerationContext();

        ClassName generatedInitializer = new ApplicationContextAotGenerator()
                .processAheadOfTime(context, generationContext);

        assertThat(generatedInitializer).isNotNull();
        assertThat(RuntimeHintsPredicates.proxies().forInterfaces(AotSmokeClient.class))
                .accepts(generationContext.getRuntimeHints());
        assertThat(RuntimeHintsPredicates.proxies().forInterfaces(InheritedAotSmokeClient.class))
                .accepts(generationContext.getRuntimeHints());
        assertThat(RuntimeHintsPredicates.reflection().onMethodInvocation(
                method(InheritedAotSmokeClient.class, "inheritedPing")))
                .accepts(generationContext.getRuntimeHints());
        assertThat(generationContext.getRuntimeHints().reflection()
                .getTypeHint(InheritedAotSmokeClient.class).methods().map(ExecutableHint::getName))
                .contains("inheritedPing");
        context.close();
    }

    private static Method method(Class<?> type, String name) {
        try {
            return type.getMethod(name);
        } catch (NoSuchMethodException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private DefaultGenerationContext newGenerationContext() {
        return new DefaultGenerationContext(
                new ClassNameGenerator(ClassName.get("com.example", "ReactiveHttpClientAotSmoke")),
                new InMemoryGeneratedFiles());
    }

    interface ParentSmokeOperations {
        @GET("/parent")
        Mono<String> parentPing();
    }

    @ReactiveHttpClient(name = "child-smoke", baseUrl = "http://child-smoke.test")
    interface ChildSmokeClient extends ParentSmokeOperations {
    }

    @ReactiveHttpClient(name = "factory-method-aot", baseUrl = "http://factory-method.test")
    interface FactoryMethodAotClient {
        @GET("/ping")
        Mono<String> ping();
    }

    @Configuration(proxyBeanMethods = false)
    static class FactoryMethodAotConfiguration {
        @Bean
        @Lazy
        ReactiveHttpClientFactoryBean<FactoryMethodAotClient> factoryMethodAotClient() {
            ReactiveHttpClientFactoryBean<FactoryMethodAotClient> factoryBean =
                    new ReactiveHttpClientFactoryBean<>();
            factoryBean.setType(FactoryMethodAotClient.class);
            return factoryBean;
        }
    }

    @ReactiveHttpClient(name = "invalid-aot-return", baseUrl = "http://invalid-aot.test")
    interface InvalidAotReturnClient {
        @GET("/nested")
        Mono<reactor.core.publisher.Flux<String>> nested();
    }

    @ReactiveHttpClient(name = "invalid-aot-parameter", baseUrl = "http://invalid-aot.test")
    interface InvalidAotParameterClient {
        @GET("/items/{id}")
        Mono<String> find(@PathVar("id") @QueryParam("id") String id);
    }

    @ReactiveHttpClient(name = "invalid-aot-uri", baseUrl = "http://invalid-aot.test")
    interface InvalidAotUriClient {
        @GET("//user:secret-value@example.test/items")
        Mono<String> find();
    }

    @ReactiveHttpClient(name = "invalid-aot-cache", baseUrl = "http://invalid-aot.test")
    interface InvalidAotCacheClient {
        @POST("/items")
        @CacheResponse("selected")
        Mono<String> create();
    }

    record CacheRecordVariant(String value, int version) {
    }

    record CacheContextVariant(String region, int tier) {
    }

    static final class CacheContextRuntimeHints implements RuntimeHintsRegistrar {
        @Override
        public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
            hints.resources().registerPattern(
                    CacheContextVariant.class.getName().replace('.', '/') + ".class");
            hints.reflection().registerType(CacheContextVariant.class, typeHint -> {});
            for (var component : CacheContextVariant.class.getRecordComponents()) {
                hints.reflection().registerMethod(component.getAccessor(), ExecutableMode.INVOKE);
            }
        }
    }

    @ReactiveHttpClient(name = "cache-record-aot", baseUrl = "http://cache-record-aot.test")
    interface CacheRecordAotClient {
        @GET("/items")
        @CacheResponse("selected")
        Mono<String> get(@CacheKey("tenant") List<CacheRecordVariant> tenant);
    }

    interface InheritedCacheAotOperations {
        @GET("/items")
        @CacheResponse("selected")
        Mono<String> get(@CacheKey("tenant") CacheRecordVariant tenant);
    }

    @ReactiveHttpClient(name = "inherited-cache-aot", baseUrl = "http://inherited-cache-aot.test")
    interface InheritedCacheAotClient extends InheritedCacheAotOperations {
    }

    @ReactiveHttpClient(name = "recursive-generic-aot", baseUrl = "http://recursive-generic-aot.test")
    interface RecursiveGenericAotClient {
        @GET("/items")
        <T extends Comparable<T>> Mono<String> get(@QueryParam("value") T value);
    }

    @ReactiveHttpClient(name = "replacement-metadata", baseUrl = "http://replacement.test")
    interface ReplacementMetadataClient {
        Mono<String> customEndpoint(@PathVar("id") @QueryParam("id") String ignoredByReplacement);
    }

    static final class ReplacementMethodMetadataCache extends MethodMetadataCache {
        @Override
        public MethodMetadata get(Method method) {
            MethodMetadata metadata = new MethodMetadata();
            metadata.setMethod(method);
            metadata.setApiName(method.getName());
            metadata.setHttpMethod("GET");
            metadata.setPathTemplate("/custom");
            metadata.setReturnsMono(true);
            metadata.setResponseType(String.class);
            return metadata;
        }
    }

    @ReactiveHttpClient(name = "foreign-replacement", baseUrl = "http://replacement.test")
    interface ForeignReplacementClient {
        @CacheResponse("foreign-policy")
        String synchronousEndpoint();
    }

    static final class ReplacementClientFactoryBean implements FactoryBean<ForeignReplacementClient> {
        @Override
        public ForeignReplacementClient getObject() {
            return null;
        }

        @Override
        public Class<?> getObjectType() {
            return ForeignReplacementClient.class;
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableReactiveHttpClients(basePackageClasses = AotSmokeClient.class)
    @Import(ReactiveHttpClientAutoConfiguration.class)
    static class AotSmokeApplication {
    }
}
