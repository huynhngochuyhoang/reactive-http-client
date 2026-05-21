package io.github.huynhngochuyhoang.httpstarter.config;

import io.github.huynhngochuyhoang.httpstarter.core.ReactiveHttpClientFactoryBean;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.beans.PropertyValue;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotContribution;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotProcessor;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.util.ClassUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Registers native-image JDK proxy hints for scanned {@code @ReactiveHttpClient} interfaces.
 */
public class ReactiveHttpClientBeanFactoryInitializationAotProcessor implements BeanFactoryInitializationAotProcessor {

    @Override
    public BeanFactoryInitializationAotContribution processAheadOfTime(ConfigurableListableBeanFactory beanFactory) {
        List<Class<?>> clientInterfaces = findClientInterfaces(beanFactory);
        if (clientInterfaces.isEmpty()) {
            return null;
        }
        return (generationContext, beanFactoryInitializationCode) -> clientInterfaces.forEach(clientInterface -> {
            generationContext.getRuntimeHints().proxies().registerJdkProxy(clientInterface);
            generationContext.getRuntimeHints().reflection().registerType(clientInterface,
                    MemberCategory.INTROSPECT_DECLARED_METHODS);
        });
    }

    private List<Class<?>> findClientInterfaces(ConfigurableListableBeanFactory beanFactory) {
        List<Class<?>> clientInterfaces = new ArrayList<>();
        ClassLoader classLoader = beanFactory.getBeanClassLoader();
        for (String beanName : beanFactory.getBeanDefinitionNames()) {
            BeanDefinition definition = beanFactory.getBeanDefinition(beanName);
            Class<?> clientInterface = resolveClientInterface(definition, classLoader);
            if (clientInterface != null && clientInterface.isInterface() && !clientInterfaces.contains(clientInterface)) {
                clientInterfaces.add(clientInterface);
            }
        }
        return clientInterfaces;
    }

    private Class<?> resolveClientInterface(BeanDefinition definition, ClassLoader classLoader) {
        Object objectType = definition.getAttribute(FactoryBean.OBJECT_TYPE_ATTRIBUTE);
        Class<?> objectTypeClass = resolveClass(objectType, classLoader);
        if (objectTypeClass != null) {
            return objectTypeClass;
        }

        if (!ReactiveHttpClientFactoryBean.class.getName().equals(definition.getBeanClassName())) {
            return null;
        }
        PropertyValue typeProperty = definition.getPropertyValues().getPropertyValue("type");
        return typeProperty != null ? resolveClass(typeProperty.getValue(), classLoader) : null;
    }

    private Class<?> resolveClass(Object value, ClassLoader classLoader) {
        if (value instanceof Class<?> clazz) {
            return clazz;
        }
        if (value instanceof String className) {
            return ClassUtils.resolveClassName(className, classLoader);
        }
        return null;
    }
}
