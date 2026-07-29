package io.github.huynhngochuyhoang.httpstarter.config;

import io.github.huynhngochuyhoang.httpstarter.annotation.*;
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
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotContribution;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.annotation.*;
import org.springframework.context.aot.ApplicationContextAotGenerator;
import org.springframework.javapoet.ClassName;
import reactor.core.publisher.Mono;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

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
        assertThat(ReactiveHttpClientProperties.class.getDeclaredClasses())
                .filteredOn(type -> Modifier.isPublic(type.getModifiers()))
                .allSatisfy(type -> assertThat(hints.reflection().getTypeHint(type))
                        .as(type.getName())
                        .isNotNull());
        assertThat(RuntimeHintsPredicates.resource().forResource(ReactiveHttpClientRuntimeHints.POM_PROPERTIES_RESOURCE))
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

    @ReactiveHttpClient(name = "replacement-metadata", baseUrl = "http://replacement.test")
    interface ReplacementMetadataClient {
        Mono<String> customEndpoint();
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
