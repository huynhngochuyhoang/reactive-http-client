package io.github.huynhngochuyhoang.httpstarter.config;

import io.github.huynhngochuyhoang.httpstarter.annotation.ReactiveHttpClient;
import io.github.huynhngochuyhoang.httpstarter.core.MethodMetadataCache;
import io.github.huynhngochuyhoang.httpstarter.core.ReactiveHttpClientFactoryBean;
import org.springframework.aot.hint.ExecutableMode;
import org.springframework.aot.hint.ReflectionHints;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.TypeReference;
import org.springframework.beans.PropertyValue;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotContribution;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotProcessor;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.ResolvableType;
import org.springframework.core.env.Environment;
import org.springframework.util.ClassUtils;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Registers native-image JDK proxy hints for scanned {@code @ReactiveHttpClient} interfaces.
 */
public class ReactiveHttpClientBeanFactoryInitializationAotProcessor implements BeanFactoryInitializationAotProcessor {

    private final Environment environment;

    public ReactiveHttpClientBeanFactoryInitializationAotProcessor() {
        this(null);
    }

    ReactiveHttpClientBeanFactoryInitializationAotProcessor(Environment environment) {
        this.environment = environment;
    }

    @Override
    public BeanFactoryInitializationAotContribution processAheadOfTime(ConfigurableListableBeanFactory beanFactory) {
        List<Class<?>> clientInterfaces = findClientInterfaces(beanFactory);
        if (clientInterfaces.isEmpty()) {
            return null;
        }
        MethodMetadataCache metadataCache = beanFactory.getBeanProvider(MethodMetadataCache.class)
                .getIfAvailable(MethodMetadataCache::new);
        ReactiveHttpClientProperties properties = properties(beanFactory);
        clientInterfaces.forEach(clientInterface -> {
            ReactiveHttpClient annotation = clientInterface.getAnnotation(ReactiveHttpClient.class);
            if (annotation != null) {
                ReactiveHttpClientProperties.ClientConfig clientConfig = properties.getClients()
                        .getOrDefault(annotation.name(), new ReactiveHttpClientProperties.ClientConfig());
                metadataCache.validateDeclarativeRequestParameters(clientInterface, annotation.name());
                metadataCache.validateDeclarativeUriTemplates(clientInterface, annotation.name());
                metadataCache.validateDeclarativeReturnTypes(clientInterface, annotation.name());
                metadataCache.validateDeclarativeCachePolicies(
                        clientInterface, annotation.name(), clientConfig);
                metadataCache.validateDeclarativeCacheCustomizations(
                        beanFactory, clientInterface, annotation.name(), clientConfig);
            }
        });
        return (generationContext, beanFactoryInitializationCode) -> clientInterfaces.forEach(clientInterface -> {
            generationContext.getRuntimeHints().proxies().registerJdkProxy(clientInterface);
            RuntimeHints runtimeHints = generationContext.getRuntimeHints();
            var reflectionHints = runtimeHints.reflection();
            Set<Class<?>> registeredRecordTypes = new HashSet<>();
            for (var method : clientInterface.getMethods()) {
                reflectionHints.registerMethod(method, ExecutableMode.INVOKE);
                for (int index = 0; index < method.getParameterCount(); index++) {
                    registerRecordAccessors(runtimeHints,
                            ResolvableType.forMethodParameter(method, index, clientInterface),
                            registeredRecordTypes,
                            new HashSet<>());
                }
            }
            reflectionHints.registerType(clientInterface, typeHint -> {
                for (var method : clientInterface.getMethods()) {
                    typeHint.withMethod(method.getName(),
                            TypeReference.listOf(method.getParameterTypes()), ExecutableMode.INVOKE);
                }
            });
        });
    }

    private ReactiveHttpClientProperties properties(ConfigurableListableBeanFactory beanFactory) {
        for (String beanName : beanFactory.getBeanNamesForType(
                ReactiveHttpClientProperties.class, false, false)) {
            if (beanFactory.containsSingleton(beanName)) {
                return beanFactory.getBean(beanName, ReactiveHttpClientProperties.class);
            }
        }
        if (environment != null) {
            return Binder.get(environment)
                    .bind("reactive.http", Bindable.of(ReactiveHttpClientProperties.class))
                    .orElseGet(ReactiveHttpClientProperties::new);
        }
        return beanFactory.getBeanProvider(ReactiveHttpClientProperties.class)
                .getIfAvailable(ReactiveHttpClientProperties::new);
    }

    private static void registerRecordAccessors(RuntimeHints runtimeHints,
                                                ResolvableType type,
                                                Set<Class<?>> registeredTypes,
                                                Set<Type> visitedTypes) {
        if (!visitedTypes.add(type.getType())) {
            return;
        }
        if (type.isArray()) {
            registerRecordAccessors(runtimeHints, type.getComponentType(), registeredTypes, visitedTypes);
            return;
        }
        for (ResolvableType generic : type.getGenerics()) {
            registerRecordAccessors(runtimeHints, generic, registeredTypes, visitedTypes);
        }
        Class<?> candidate = type.resolve();
        if (candidate == null || !candidate.isRecord() || !registeredTypes.add(candidate)) {
            return;
        }
        ReflectionHints hints = runtimeHints.reflection();
        runtimeHints.resources().registerPattern(candidate.getName().replace('.', '/') + ".class");
        hints.registerType(candidate, typeHint -> {});
        var components = candidate.getRecordComponents();
        for (var component : components) {
            hints.registerMethod(component.getAccessor(), ExecutableMode.INVOKE);
            registerRecordAccessors(runtimeHints,
                    ResolvableType.forType(component.getGenericType()),
                    registeredTypes,
                    visitedTypes);
        }
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
