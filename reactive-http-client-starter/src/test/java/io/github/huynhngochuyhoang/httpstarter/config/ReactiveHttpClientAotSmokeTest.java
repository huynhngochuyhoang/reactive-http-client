package io.github.huynhngochuyhoang.httpstarter.config;

import io.github.huynhngochuyhoang.httpstarter.annotation.GET;
import io.github.huynhngochuyhoang.httpstarter.annotation.ReactiveHttpClient;
import io.github.huynhngochuyhoang.httpstarter.config.smoke.AotSmokeClient;
import io.github.huynhngochuyhoang.httpstarter.core.ReactiveHttpClientFactoryBean;
import io.github.huynhngochuyhoang.httpstarter.enable.EnableReactiveHttpClients;
import org.junit.jupiter.api.Test;
import org.springframework.aot.generate.ClassNameGenerator;
import org.springframework.aot.generate.DefaultGenerationContext;
import org.springframework.aot.generate.InMemoryGeneratedFiles;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotContribution;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.aot.ApplicationContextAotGenerator;
import org.springframework.javapoet.ClassName;

import static org.assertj.core.api.Assertions.assertThat;

class ReactiveHttpClientAotSmokeTest {

    @Test
    void runtimeHintsCoverAnnotationsAndConfigurationProperties() {
        RuntimeHints hints = new RuntimeHints();

        new ReactiveHttpClientRuntimeHints().registerHints(hints, getClass().getClassLoader());

        assertThat(hints.reflection().getTypeHint(ReactiveHttpClient.class).getMemberCategories())
                .contains(MemberCategory.INTROSPECT_DECLARED_METHODS);
        assertThat(hints.reflection().getTypeHint(GET.class).getMemberCategories())
                .contains(MemberCategory.INTROSPECT_DECLARED_METHODS);
        assertThat(hints.reflection().getTypeHint(ReactiveHttpClientProperties.class).getMemberCategories())
                .contains(MemberCategory.INVOKE_DECLARED_CONSTRUCTORS, MemberCategory.INVOKE_PUBLIC_METHODS);
        assertThat(hints.reflection().getTypeHint(ReactiveHttpClientProperties.ClientConfig.class).getMemberCategories())
                .contains(MemberCategory.INVOKE_DECLARED_CONSTRUCTORS, MemberCategory.INVOKE_PUBLIC_METHODS);
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
        assertThat(generationContext.getRuntimeHints().reflection().getTypeHint(AotSmokeClient.class).getMemberCategories())
                .contains(MemberCategory.INTROSPECT_DECLARED_METHODS);
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
        context.close();
    }

    private DefaultGenerationContext newGenerationContext() {
        return new DefaultGenerationContext(
                new ClassNameGenerator(ClassName.get("com.example", "ReactiveHttpClientAotSmoke")),
                new InMemoryGeneratedFiles());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableReactiveHttpClients(basePackageClasses = AotSmokeClient.class)
    @Import(ReactiveHttpClientAutoConfiguration.class)
    static class AotSmokeApplication {
    }
}
