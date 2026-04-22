package io.github.huynhngochuyhoang.httpstarter.config;

import io.github.huynhngochuyhoang.httpstarter.config.fixtures.RegistrarScannedClient;
import io.github.huynhngochuyhoang.httpstarter.core.ReactiveHttpClientFactoryBean;
import io.github.huynhngochuyhoang.httpstarter.enable.EnableReactiveHttpClients;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.core.type.AnnotationMetadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReactiveHttpClientsRegistrarTest {

    private final ReactiveHttpClientsRegistrar registrar = new ReactiveHttpClientsRegistrar();

    @Test
    void shouldRegisterFactoryBeanWhenNoExistingRegistrationPresent() {
        BeanDefinitionRegistry registry = new DefaultListableBeanFactory();

        registrar.registerBeanDefinitions(AnnotationMetadata.introspect(TestRegistrarConfiguration.class), registry);

        assertTrue(registry.containsBeanDefinition(RegistrarScannedClient.class.getName()));
        assertEquals(1, registry.getBeanDefinitionCount());
    }

    @Test
    void shouldSkipFactoryBeanWhenInterfaceAlreadyRegisteredUnderAnotherBeanName() {
        BeanDefinitionRegistry registry = new DefaultListableBeanFactory();
        GenericBeanDefinition existing = new GenericBeanDefinition();
        existing.setBeanClassName(RegistrarScannedClient.class.getName());
        registry.registerBeanDefinition("registrarScannedClient", existing);

        registrar.registerBeanDefinitions(AnnotationMetadata.introspect(TestRegistrarConfiguration.class), registry);

        assertEquals(1, registry.getBeanDefinitionCount());
    }

    @Test
    void shouldBeIdempotentWhenRegistrarRunsTwice() {
        BeanDefinitionRegistry registry = new DefaultListableBeanFactory();

        registrar.registerBeanDefinitions(AnnotationMetadata.introspect(TestRegistrarConfiguration.class), registry);
        registrar.registerBeanDefinitions(AnnotationMetadata.introspect(TestRegistrarConfiguration.class), registry);

        assertTrue(registry.containsBeanDefinition(RegistrarScannedClient.class.getName()));
        assertEquals(1, registry.getBeanDefinitionCount());
    }

    @Test
    void shouldSkipWhenExistingFactoryBeanExposesInterfaceViaObjectTypeAttribute() {
        BeanDefinitionRegistry registry = new DefaultListableBeanFactory();
        GenericBeanDefinition preExisting = (GenericBeanDefinition) BeanDefinitionBuilder
                .genericBeanDefinition(ReactiveHttpClientFactoryBean.class)
                .getBeanDefinition();
        preExisting.setAttribute(FactoryBean.OBJECT_TYPE_ATTRIBUTE, RegistrarScannedClient.class);
        registry.registerBeanDefinition("customFactoryBean", preExisting);

        registrar.registerBeanDefinitions(AnnotationMetadata.introspect(TestRegistrarConfiguration.class), registry);

        assertEquals(1, registry.getBeanDefinitionCount());
    }

    @EnableReactiveHttpClients(basePackageClasses = RegistrarScannedClient.class)
    private static class TestRegistrarConfiguration {
    }
}
