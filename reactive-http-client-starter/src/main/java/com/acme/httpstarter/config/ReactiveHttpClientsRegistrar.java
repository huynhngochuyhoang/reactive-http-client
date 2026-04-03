package com.acme.httpstarter.config;

import com.acme.httpstarter.annotation.ReactiveHttpClient;
import com.acme.httpstarter.core.ReactiveHttpClientFactoryBean;
import com.acme.httpstarter.enable.EnableReactiveHttpClients;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.util.ClassUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Scans the configured base packages for interfaces annotated with
 * {@link ReactiveHttpClient} and registers a
 * {@link ReactiveHttpClientFactoryBean} bean definition for each one.
 *
 * <p>Imported via {@link EnableReactiveHttpClients}.
 */
public class ReactiveHttpClientsRegistrar implements ImportBeanDefinitionRegistrar {

    @Override
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata,
                                        BeanDefinitionRegistry registry) {

        AnnotationAttributes attributes = AnnotationAttributes.fromMap(
                importingClassMetadata.getAnnotationAttributes(
                        EnableReactiveHttpClients.class.getName()));

        if (attributes == null) return;

        List<String> basePackages = new ArrayList<>(
                Arrays.asList(attributes.getStringArray("basePackages")));

        // Also resolve type-safe basePackageClasses
        for (Class<?> clazz : attributes.getClassArray("basePackageClasses")) {
            basePackages.add(ClassUtils.getPackageName(clazz));
        }

        // Fall back to the package of the annotated class
        if (basePackages.isEmpty()) {
            basePackages.add(ClassUtils.getPackageName(importingClassMetadata.getClassName()));
        }

        ClassPathScanningCandidateComponentProvider scanner = buildScanner();

        for (String basePackage : basePackages) {
            for (BeanDefinition candidate : scanner.findCandidateComponents(basePackage)) {
                String interfaceClassName = candidate.getBeanClassName();
                try {
                    Class<?> interfaceClass = Class.forName(interfaceClassName);
                    registerFactoryBean(interfaceClass, registry);
                } catch (ClassNotFoundException e) {
                    throw new IllegalStateException(
                            "Could not load @ReactiveHttpClient interface: " + interfaceClassName, e);
                }
            }
        }
    }

    private ClassPathScanningCandidateComponentProvider buildScanner() {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false) {
                    @Override
                    protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
                        // Accept only interfaces
                        return beanDefinition.getMetadata().isInterface();
                    }
                };
        scanner.addIncludeFilter(new AnnotationTypeFilter(ReactiveHttpClient.class));
        return scanner;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void registerFactoryBean(Class<?> interfaceClass, BeanDefinitionRegistry registry) {
        BeanDefinitionBuilder builder = BeanDefinitionBuilder
                .genericBeanDefinition(ReactiveHttpClientFactoryBean.class);
        builder.addPropertyValue("type", interfaceClass);

        String beanName = interfaceClass.getName();
        if (!registry.containsBeanDefinition(beanName)) {
            registry.registerBeanDefinition(beanName, builder.getBeanDefinition());
        }
    }
}
