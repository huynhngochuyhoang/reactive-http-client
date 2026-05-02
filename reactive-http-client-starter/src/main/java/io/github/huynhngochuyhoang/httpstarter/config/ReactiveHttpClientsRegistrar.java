package io.github.huynhngochuyhoang.httpstarter.config;

import io.github.huynhngochuyhoang.httpstarter.annotation.ReactiveHttpClient;
import io.github.huynhngochuyhoang.httpstarter.core.ReactiveHttpClientFactoryBean;
import io.github.huynhngochuyhoang.httpstarter.enable.EnableReactiveHttpClients;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.FactoryBean;
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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

        // Collect all candidates first so we can detect duplicate client names before
        // registering any bean definitions.
        // Use a LinkedHashMap keyed by class name to de-duplicate candidates that may appear
        // more than once when base packages overlap (e.g. both "com.example" and
        // "com.example.sub" are configured and contain the same interface).
        LinkedHashMap<String, Class<?>> candidateByClassName = new LinkedHashMap<>();
        for (String basePackage : basePackages) {
            for (BeanDefinition candidate : scanner.findCandidateComponents(basePackage)) {
                String interfaceClassName = candidate.getBeanClassName();
                if (candidateByClassName.containsKey(interfaceClassName)) {
                    continue; // already seen from an overlapping package scan
                }
                try {
                    Class<?> interfaceClass = ClassUtils.resolveClassName(interfaceClassName, null);
                    candidateByClassName.put(interfaceClassName, interfaceClass);
                } catch (IllegalArgumentException e) {
                    throw new IllegalStateException(
                            "Could not load @ReactiveHttpClient interface: " + interfaceClassName, e);
                }
            }
        }
        List<Class<?>> candidates = new ArrayList<>(candidateByClassName.values());

        // Detect duplicate client names: two interfaces with the same @ReactiveHttpClient(name)
        // would silently share a connection pool — fail fast with a descriptive message.
        Map<String, String> seenNames = new HashMap<>();
        for (Class<?> interfaceClass : candidates) {
            ReactiveHttpClient annotation = interfaceClass.getAnnotation(ReactiveHttpClient.class);
            if (annotation == null) continue;
            String clientName = annotation.name();
            String previous = seenNames.put(clientName, interfaceClass.getName());
            if (previous != null) {
                throw new IllegalStateException(
                        "Duplicate @ReactiveHttpClient name \"" + clientName + "\" detected. "
                                + "Both " + previous + " and " + interfaceClass.getName()
                                + " declare the same client name. "
                                + "Each @ReactiveHttpClient interface must have a unique name.");
            }
        }

        for (Class<?> interfaceClass : candidates) {
            registerFactoryBean(interfaceClass, registry);
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
        if (hasExistingBeanRegistration(interfaceClass, registry)) {
            return;
        }

        BeanDefinitionBuilder builder = BeanDefinitionBuilder
                .genericBeanDefinition(ReactiveHttpClientFactoryBean.class);
        builder.addPropertyValue("type", interfaceClass);

        registry.registerBeanDefinition(interfaceClass.getName(), builder.getBeanDefinition());
    }

    private boolean hasExistingBeanRegistration(Class<?> interfaceClass, BeanDefinitionRegistry registry) {
        String interfaceClassName = interfaceClass.getName();
        if (registry.containsBeanDefinition(interfaceClassName)) {
            return true;
        }

        for (String beanName : registry.getBeanDefinitionNames()) {
            BeanDefinition definition = registry.getBeanDefinition(beanName);
            if (interfaceClassName.equals(definition.getBeanClassName())) {
                return true;
            }

            Object factoryObjectType = definition.getAttribute(FactoryBean.OBJECT_TYPE_ATTRIBUTE);
            if (factoryObjectType instanceof Class<?> clazz && interfaceClass.equals(clazz)) {
                return true;
            }
            if (factoryObjectType instanceof String className && interfaceClassName.equals(className)) {
                return true;
            }
        }
        return false;
    }
}
