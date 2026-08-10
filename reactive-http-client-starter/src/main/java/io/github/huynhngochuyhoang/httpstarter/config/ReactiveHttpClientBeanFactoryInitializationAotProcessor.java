package io.github.huynhngochuyhoang.httpstarter.config;

import io.github.huynhngochuyhoang.httpstarter.annotation.ReactiveHttpClient;
import io.github.huynhngochuyhoang.httpstarter.core.MethodMetadataCache;
import io.github.huynhngochuyhoang.httpstarter.core.ReactiveHttpClientFactoryBean;
import org.springframework.aot.hint.ExecutableMode;
import org.springframework.aot.hint.TypeReference;
import org.springframework.beans.PropertyValue;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotContribution;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotProcessor;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.ResolvableType;
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
        MethodMetadataCache metadataCache = beanFactory.getBeanProvider(MethodMetadataCache.class)
                .getIfAvailable(MethodMetadataCache::new);
        clientInterfaces.forEach(clientInterface -> {
            ReactiveHttpClient annotation = clientInterface.getAnnotation(ReactiveHttpClient.class);
            if (annotation != null) {
                metadataCache.validateDeclarativeRequestParameters(clientInterface, annotation.name());
                metadataCache.validateDeclarativeUriTemplates(clientInterface, annotation.name());
                metadataCache.validateDeclarativeReturnTypes(clientInterface, annotation.name());
            }
        });
        return (generationContext, beanFactoryInitializationCode) -> clientInterfaces.forEach(clientInterface -> {
            generationContext.getRuntimeHints().proxies().registerJdkProxy(clientInterface);
            var reflectionHints = generationContext.getRuntimeHints().reflection();
            for (var method : clientInterface.getMethods()) {
                reflectionHints.registerMethod(method, ExecutableMode.INVOKE);
            }
            reflectionHints.registerType(clientInterface, typeHint -> {
                for (var method : clientInterface.getMethods()) {
                    typeHint.withMethod(method.getName(),
                            TypeReference.listOf(method.getParameterTypes()), ExecutableMode.INVOKE);
                }
            });
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
        Class<?> beanClass = resolveClass(definition.getBeanClassName(), classLoader, true);
        ResolvableType beanType = definition.getResolvableType();
        Class<?> resolvedBeanType = beanType.resolve();
        if ((beanClass == null || !ReactiveHttpClientFactoryBean.class.isAssignableFrom(beanClass))
                && (resolvedBeanType == null
                || !ReactiveHttpClientFactoryBean.class.isAssignableFrom(resolvedBeanType))) {
            return null;
        }

        Object objectType = definition.getAttribute(FactoryBean.OBJECT_TYPE_ATTRIBUTE);
        Class<?> objectTypeClass = resolveClass(objectType, classLoader, true);
        if (objectTypeClass != null) {
            return objectTypeClass;
        }
        PropertyValue typeProperty = definition.getPropertyValues().getPropertyValue("type");
        if (typeProperty != null) {
            return resolveClass(typeProperty.getValue(), classLoader, false);
        }
        return beanType.as(FactoryBean.class).getGeneric(0).resolve();
    }

    private Class<?> resolveClass(Object value, ClassLoader classLoader, boolean ignoreResolutionFailures) {
        if (value instanceof Class<?> clazz) {
            return clazz;
        }
        if (value instanceof String className) {
            try {
                return ClassUtils.resolveClassName(className, classLoader);
            } catch (IllegalArgumentException ex) {
                if (ignoreResolutionFailures) {
                    return null;
                }
                throw ex;
            }
        }
        return null;
    }
}
