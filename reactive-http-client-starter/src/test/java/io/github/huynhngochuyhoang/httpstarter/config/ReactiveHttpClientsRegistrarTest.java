package io.github.huynhngochuyhoang.httpstarter.config;

import io.github.huynhngochuyhoang.httpstarter.config.duplicates.DuplicateNameClientA;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    @Test
    void shouldThrowWhenTwoInterfacesShareTheSameClientName() {
        BeanDefinitionRegistry registry = new DefaultListableBeanFactory();

        // DuplicateConfiguration scans the duplicates package which contains DuplicateNameClientA
        // and DuplicateNameClientB — both carry name = "duplicate-fixture".
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> registrar.registerBeanDefinitions(
                        AnnotationMetadata.introspect(DuplicateConfiguration.class), registry));

        assertTrue(ex.getMessage().contains("duplicate-fixture"),
                "Error message must include the duplicate client name");
        assertTrue(ex.getMessage().contains("unique name"),
                "Error message must guide the user to use a unique name");
    }

    @EnableReactiveHttpClients(basePackageClasses = RegistrarScannedClient.class)
    private static class TestRegistrarConfiguration {
    }

    @EnableReactiveHttpClients(basePackageClasses = DuplicateNameClientA.class)
    private static class DuplicateConfiguration {
    }
}
