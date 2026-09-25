package io.github.huynhngochuyhoang.httpstarter.config;

import io.github.huynhngochuyhoang.httpstarter.annotation.ReactiveHttpClient;
import io.github.huynhngochuyhoang.httpstarter.core.MethodMetadata;
import io.github.huynhngochuyhoang.httpstarter.core.MethodMetadataCache;
import io.github.huynhngochuyhoang.httpstarter.core.ReactiveHttpClientFactoryBean;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.scope.ScopedObject;
import org.springframework.aop.scope.ScopedProxyFactoryBean;
import org.springframework.aop.target.SimpleBeanTargetSource;
import org.springframework.aot.hint.ExecutableMode;
import org.springframework.aot.hint.ReflectionHints;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.TypeReference;
import org.springframework.beans.PropertyValue;
import org.springframework.beans.factory.*;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotContribution;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotProcessor;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.NamedBeanHolder;
import org.springframework.beans.factory.support.AbstractBeanFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.FactoryBeanRegistrySupport;
import org.springframework.beans.factory.support.MergedBeanDefinitionPostProcessor;
import org.springframework.boot.context.properties.ConfigurationPropertiesBindingPostProcessor;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.OrderComparator;
import org.springframework.core.PriorityOrdered;
import org.springframework.core.ResolvableType;
import org.springframework.core.env.Environment;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

import java.lang.reflect.Type;
import java.util.*;

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
        MethodMetadataCache metadataCache;
        ReactiveHttpClientProperties properties;
        Map<AbstractBeanFactory, PropertiesBinding> bindings = new LinkedHashMap<>();
        try {
            for (BeanFactory current = beanFactory; current instanceof ConfigurableListableBeanFactory configurable;
                 current = configurable.getParentBeanFactory()) {
                if (current instanceof AbstractBeanFactory factory
                        && factory.containsLocalBean(ConfigurationPropertiesBindingPostProcessor.BEAN_NAME)) {
                    var binding = new PropertiesBinding(factory);
                    if (factory.getBeanPostProcessors().stream().anyMatch(processor -> processor == binding.delegate)) {
                        continue;
                    }
                    bindings.put(factory, binding);
                    var processors = factory.getBeanPostProcessors();
                    Map<Object, String> processorBeans = existingProcessorBeans(factory);
                    List<String> registrationOrder = Arrays.asList(
                            configurable.getBeanNamesForType(BeanPostProcessor.class, true, false));
                    processorBeans.entrySet().removeIf(entry -> !registrationOrder.contains(entry.getValue()));
                    int bindingRegistrationIndex = registrationOrder.indexOf(ConfigurationPropertiesBindingPostProcessor.BEAN_NAME);
                    int index = 0;
                    Comparator<Object> comparator = configurable instanceof DefaultListableBeanFactory listable
                            && listable.getDependencyComparator() != null
                            ? listable.getDependencyComparator() : OrderComparator.INSTANCE;
                    // Direct-only registrations precede auto-detected processors regardless of order.
                    while (index < processors.size() && !processorBeans.containsKey(processors.get(index))) {
                        index++;
                    }
                    // Spring moves merged-definition processors into the trailing internal group.
                    for (int i = index; i < processors.size(); i++) {
                        var processor = processors.get(i);
                        int registrationIndex = registrationOrder.indexOf(processorBeans.get(processor));
                        if (!(processor instanceof MergedBeanDefinitionPostProcessor)
                                && registrationIndex >= 0
                                && configurable.isTypeMatch(processorBeans.get(processor), PriorityOrdered.class)) {
                            int comparison = comparator.compare(processor, binding.delegate);
                            if (comparison < 0 || (comparison == 0 && registrationIndex < bindingRegistrationIndex)) {
                                index = i + 1;
                            }
                        }
                    }
                    processors.add(index, binding);
                }
            }
            metadataCache = beanFactory.getBeanProvider(MethodMetadataCache.class)
                    .getIfAvailable(MethodMetadataCache::new);
            properties = properties(beanFactory, bindings);
        } finally {
            bindings.forEach((factory, binding) -> factory.getBeanPostProcessors().remove(binding));
        }
        Map<Class<?>, ReactiveHttpClientProperties.ClientConfig> clientConfigs = new HashMap<>();
        clientInterfaces.forEach(clientInterface -> {
            ReactiveHttpClient annotation = clientInterface.getAnnotation(ReactiveHttpClient.class);
            if (annotation != null) {
                ReactiveHttpClientProperties.ClientConfig clientConfig = properties.getClients()
                        .getOrDefault(annotation.name(), new ReactiveHttpClientProperties.ClientConfig());
                clientConfigs.put(clientInterface, clientConfig);
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
                if (isCacheSelected(method, metadataCache, clientConfigs.get(clientInterface))) {
                    for (int index = 0; index < method.getParameterCount(); index++) {
                        registerRecordAccessors(runtimeHints,
                                ResolvableType.forMethodParameter(method, index, clientInterface),
                                registeredRecordTypes,
                                new HashSet<>());
                    }
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

    private static Map<Object, String> existingProcessorBeans(AbstractBeanFactory factory) {
        Map<Object, String> processors = new IdentityHashMap<>();
        var cachedProduct = ReflectionUtils.findMethod(
                FactoryBeanRegistrySupport.class, "getCachedObjectForFactoryBean", String.class);
        if (cachedProduct != null) ReflectionUtils.makeAccessible(cachedProduct);
        for (String name : factory.getSingletonNames()) {
            Object singleton = factory.getSingleton(name);
            if (singleton instanceof BeanPostProcessor) {
                processors.put(singleton, singleton instanceof FactoryBean<?> ? BeanFactory.FACTORY_BEAN_PREFIX + name : name);
            }
            // Inspect cached products only: classifying the chain must not create processors.
            if (singleton instanceof FactoryBean<?> && cachedProduct != null
                    && factory.isTypeMatch(name, BeanPostProcessor.class)) {
                Object product = ReflectionUtils.invokeMethod(cachedProduct, factory, name);
                if (product instanceof BeanPostProcessor) processors.put(product, name);
            }
        }
        return processors;
    }

    private ReactiveHttpClientProperties properties(ConfigurableListableBeanFactory beanFactory,
                                                     Map<AbstractBeanFactory, PropertiesBinding> bindings) {
        NamedBeanHolder<ReactiveHttpClientProperties> selected;
        try {
            selected = beanFactory.resolveNamedBean(ReactiveHttpClientProperties.class);
        } catch (NoUniqueBeanDefinitionException ex) {
            throw ex;
        } catch (NoSuchBeanDefinitionException ex) {
            if (ex.getResolvableType() == null
                    || ex.getResolvableType().resolve() != ReactiveHttpClientProperties.class) {
                throw ex;
            }
            // Named resolution cannot delegate to an opaque parent, unlike a provider.
            BeanFactory ancestor = beanFactory;
            while (ancestor instanceof ConfigurableListableBeanFactory configurable) {
                ancestor = configurable.getParentBeanFactory();
            }
            return ancestor != null
                    ? ancestor.getBeanProvider(ReactiveHttpClientProperties.class)
                            .getIfAvailable(this::environmentProperties)
                    : environmentProperties();
        }
        return bindSelectedPropertiesForAot(beanFactory, selected, bindings, false);
    }

    private ReactiveHttpClientProperties environmentProperties() {
        return environment != null
                ? Binder.get(environment)
                        .bind("reactive.http", Bindable.of(ReactiveHttpClientProperties.class))
                        .orElseGet(ReactiveHttpClientProperties::new)
                : new ReactiveHttpClientProperties();
    }

    private static ReactiveHttpClientProperties bindSelectedPropertiesForAot(
            ConfigurableListableBeanFactory beanFactory, NamedBeanHolder<ReactiveHttpClientProperties> selected,
            Map<AbstractBeanFactory, PropertiesBinding> bindings, boolean resolvedByName) {
        while (true) {
            String beanName = BeanFactoryUtils.transformedBeanName(selected.getBeanName());
            String localName = beanFactory instanceof AbstractBeanFactory factory
                    ? factory.canonicalName(beanName) : beanName;
            if (BeanFactoryUtils.isFactoryDereference(selected.getBeanName())) {
                localName = BeanFactory.FACTORY_BEAN_PREFIX + localName;
            }
            boolean local = beanFactory.containsLocalBean(localName);
            Class<?> localType = local && !resolvedByName ? beanFactory.getType(localName, false) : null;
            if (local && (resolvedByName || (localType != null && ReactiveHttpClientProperties.class.isAssignableFrom(localType)))) {
                selected = new NamedBeanHolder<>(localName, selected.getBeanInstance());
                break;
            }
            // A scoped target was already type-checked by getBean(name, type). Follow
            // that named lookup's aliases, not a potentially imprecise type prediction.
            if (resolvedByName) selected = new NamedBeanHolder<>(localName, selected.getBeanInstance());
            if (!(beanFactory.getParentBeanFactory() instanceof ConfigurableListableBeanFactory parent)) {
                return selected.getBeanInstance();
            }
            beanFactory = parent;
        }
        String beanName = BeanFactoryUtils.transformedBeanName(selected.getBeanName());
        if (!BeanFactoryUtils.isFactoryDereference(selected.getBeanName())
                && beanFactory.containsBeanDefinition(beanName) && beanFactory.isFactoryBean(beanName)) {
            Class<?> factoryType = beanFactory.getType(BeanFactory.FACTORY_BEAN_PREFIX + beanName, false);
            if (factoryType == null || !ScopedProxyFactoryBean.class.isAssignableFrom(factoryType)) {
                return selected.getBeanInstance();
            }
            Object targetName;
            if (selected.getBeanInstance() instanceof Advised advised
                    && advised.getTargetSource() instanceof SimpleBeanTargetSource targetSource) {
                targetName = targetSource.getTargetBeanName();
            } else {
                targetName = beanFactory.getMergedBeanDefinition(beanName)
                        .getPropertyValues().get("targetBeanName");
            }
            if (!(targetName instanceof String candidate) || !StringUtils.hasText(candidate)) {
                // Opaque programmatic proxies hide Advised and may have no definition property.
                // Read only the initialized factory at build time; never create a second factory.
                Object factory = beanFactory.getSingleton(beanName);
                if (factory instanceof ScopedProxyFactoryBean) {
                    var field = ReflectionUtils.findField(ScopedProxyFactoryBean.class, "targetBeanName", String.class);
                    if (field != null) {
                        ReflectionUtils.makeAccessible(field);
                        targetName = ReflectionUtils.getField(field, factory);
                    }
                }
            }
            if (!(targetName instanceof String name) || !StringUtils.hasText(name)) {
                throw new IllegalStateException("Scoped properties proxy has no targetBeanName: " + selected.getBeanName());
            }
            // Validate the same target that was bound, including prototype-scoped targets.
            return bindSelectedPropertiesForAot(beanFactory, new NamedBeanHolder<>(name,
                    beanFactory.getBean(name, ReactiveHttpClientProperties.class)), bindings, true);
        }
        // Existing instances missed the creation callback; newly created ones are already tracked.
        PropertiesBinding binding = bindings.get(beanFactory);
        if (binding != null && beanFactory.containsBeanDefinition(beanName)) {
            return (ReactiveHttpClientProperties) binding.bindExisting(selected.getBeanInstance(), beanName);
        }
        return selected.getBeanInstance();
    }

    private static final class PropertiesBinding implements BeanPostProcessor {
        private final AbstractBeanFactory factory;
        private final ConfigurationPropertiesBindingPostProcessor delegate;
        private final Map<Object, Object> bound = new IdentityHashMap<>();
        private final Set<String> boundBeanNames = new HashSet<>();

        private PropertiesBinding(AbstractBeanFactory factory) {
            this.factory = factory;
            this.delegate = factory.getBean(ConfigurationPropertiesBindingPostProcessor.BEAN_NAME,
                    ConfigurationPropertiesBindingPostProcessor.class);
        }

        private Object bindExisting(Object bean, String beanName) {
            // A later post-processor may replace the bound instance. This name guard
            // applies only to fallback; each new prototype still takes the creation callback.
            if (!boundBeanNames.contains(factory.canonicalName(beanName))) {
                Object result = postProcessBeforeInitialization(bean, beanName);
                return result != null ? result : bean;
            }
            return bean;
        }

        @Override
        public Object postProcessBeforeInitialization(Object bean, String beanName) {
            if (bean instanceof ReactiveHttpClientProperties && !isScopedProxy(bean, beanName)) {
                if (bound.containsKey(bean)) return bound.get(bean);
                Object result = delegate.postProcessBeforeInitialization(bean, beanName);
                bound.put(bean, result);
                if (result != null && result != bean) bound.put(result, result);
                boundBeanNames.add(factory.canonicalName(beanName));
                return result;
            }
            return bean;
        }

        private boolean isScopedProxy(Object bean, String beanName) {
            if (!(bean instanceof ScopedObject) || !factory.isFactoryBean(beanName)) return false;
            Class<?> factoryType = factory.getType(BeanFactory.FACTORY_BEAN_PREFIX + beanName, false);
            return factoryType != null && ScopedProxyFactoryBean.class.isAssignableFrom(factoryType);
        }
    }

    private static boolean isCacheSelected(
            java.lang.reflect.Method method,
            MethodMetadataCache metadataCache,
            ReactiveHttpClientProperties.ClientConfig clientConfig) {
        MethodMetadata metadata = metadataCache.get(method);
        if (metadata.isCacheDisabled()) {
            return false;
        }
        if (StringUtils.hasText(metadata.getCachePolicyName())) {
            return true;
        }
        return clientConfig != null
                && clientConfig.getCache() != null
                && StringUtils.hasText(clientConfig.getCache().getPolicy());
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
