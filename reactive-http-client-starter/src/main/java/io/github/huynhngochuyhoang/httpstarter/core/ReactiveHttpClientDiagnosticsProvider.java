package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.annotation.ReactiveHttpClient;
import io.github.huynhngochuyhoang.httpstarter.auth.AuthProviderFactory;
import io.github.huynhngochuyhoang.httpstarter.auth.AwsSigV4AuthProviderFactory;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.FactoryBeanRegistrySupport;
import org.springframework.core.OrderComparator;
import org.springframework.core.ResolvableType;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;

/**
 * Provides sanitized runtime summaries for registered {@link ReactiveHttpClient}
 * clients.
 *
 * <p>The provider is intentionally independent from Spring Boot Actuator. Apps
 * that want an endpoint can wrap this bean in their own controller or Actuator
 * endpoint and keep endpoint exposure policy local to the application.
 */
public class ReactiveHttpClientDiagnosticsProvider {

    private static final Method GET_CACHED_FACTORY_BEAN_OBJECT = ReflectionUtils.findMethod(
            FactoryBeanRegistrySupport.class, "getCachedObjectForFactoryBean", String.class);

    private final ConfigurableListableBeanFactory beanFactory;
    private final ReactiveHttpClientProperties properties;
    private final MethodMetadataCache metadataCache;

    public ReactiveHttpClientDiagnosticsProvider(ConfigurableListableBeanFactory beanFactory,
                                                 ReactiveHttpClientProperties properties,
                                                 MethodMetadataCache metadataCache) {
        this.beanFactory = beanFactory;
        this.properties = properties != null ? properties : new ReactiveHttpClientProperties();
        this.metadataCache = metadataCache != null ? metadataCache : new MethodMetadataCache();
    }

    public List<ClientSummary> clientSummaries() {
        ResilienceDiagnostics resilienceDiagnostics = resilienceDiagnostics();
        return List.of(beanFactory.getBeanDefinitionNames()).stream()
                .map(this::clientRegistration)
                .filter(Objects::nonNull)
                .distinct()
                .map(registration -> clientSummaryEntry(registration, resilienceDiagnostics.operatorApplier()))
                .sorted(Comparator.comparing(ClientSummary::clientName)
                        .thenComparing(ClientSummary::clientInterface))
                .toList();
    }

    List<ClientSnapshotEntry> clientSnapshotEntries() {
        ResilienceDiagnostics resilienceDiagnostics = resilienceDiagnostics();
        return List.of(beanFactory.getBeanDefinitionNames()).stream()
                .map(this::clientRegistration)
                .filter(Objects::nonNull)
                .distinct()
                .map(registration -> clientSnapshotEntry(registration, resilienceDiagnostics))
                .sorted(Comparator.comparing((ClientSnapshotEntry entry) -> entry.summary().clientName())
                        .thenComparing(entry -> entry.summary().clientInterface()))
                .toList();
    }

    private ClientSummary clientSummaryEntry(ClientRegistration registration,
                                             ResilienceOperatorApplier resilienceOperatorApplier) {
        Class<?> clientInterface = registration.clientInterface();
        ReactiveHttpClient annotation = clientInterface.getAnnotation(ReactiveHttpClient.class);
        String clientName = annotation != null ? annotation.name() : "";
        ReactiveHttpClientProperties.ClientConfig clientConfig = properties.getClients()
                .getOrDefault(clientName, new ReactiveHttpClientProperties.ClientConfig());
        return clientSummary(clientInterface, clientName, clientConfig, metadataCache,
                resilienceOperatorApplier, registration.starterFactory());
    }

    private ClientSnapshotEntry clientSnapshotEntry(ClientRegistration registration,
                                                    ResilienceDiagnostics resilienceDiagnostics) {
        Class<?> clientInterface = registration.clientInterface();
        ReactiveHttpClient annotation = clientInterface.getAnnotation(ReactiveHttpClient.class);
        String clientName = annotation != null ? annotation.name() : "";
        ReactiveHttpClientProperties.ClientConfig clientConfig = properties.getClients()
                .getOrDefault(clientName, new ReactiveHttpClientProperties.ClientConfig());
        ClientSummary summary = clientSummary(
                clientInterface, clientName, clientConfig, metadataCache, resilienceDiagnostics.operatorApplier(),
                registration.starterFactory());
        return new ClientSnapshotEntry(
                summary,
                strictUnsafeRetryValidation(clientInterface, clientConfig, resilienceDiagnostics.operatorApplier(),
                        resilienceDiagnostics.retryRegistryUnresolved()),
                strictBodySigningValidation(clientConfig),
                poolSummary(clientConfig),
                clientConfig.getLogicalCallTimeoutMs(),
                clientConfig.isCompressionEnabled(),
                clientConfig.getCodecMaxInMemorySizeMb());
    }

    static ClientSummary clientSummary(Class<?> clientInterface,
                                       String clientName,
                                       ReactiveHttpClientProperties.ClientConfig clientConfig,
                                       MethodMetadataCache metadataCache,
                                       ResilienceOperatorApplier resilienceOperatorApplier) {
        return clientSummary(clientInterface, clientName, clientConfig, metadataCache,
                resilienceOperatorApplier, true);
    }

    private static ClientSummary clientSummary(Class<?> clientInterface,
                                               String clientName,
                                               ReactiveHttpClientProperties.ClientConfig clientConfig,
                                               MethodMetadataCache metadataCache,
                                               ResilienceOperatorApplier resilienceOperatorApplier,
                                               boolean validateDeclarativeReturnTypes) {
        ReactiveHttpClient annotation = clientInterface.getAnnotation(ReactiveHttpClient.class);
        ReactiveHttpClientProperties.ClientConfig resolvedConfig = clientConfig != null
                ? clientConfig
                : new ReactiveHttpClientProperties.ClientConfig();
        List<EffectiveHttpClientContract> contracts = EffectiveHttpClientContractExporter.export(
                clientInterface, clientName, resolvedConfig, metadataCache, resilienceOperatorApplier,
                null, false, validateDeclarativeReturnTypes);
        long inherited = contracts.stream()
                .filter(EffectiveHttpClientContract::inherited)
                .count();
        EffectiveHttpClientContract.TimeoutPolicy timeout = representativeTimeout(contracts);
        EffectiveHttpClientContract.ResiliencePolicy resilience = representativeResilience(contracts);

        return new ClientSummary(
                clientName,
                clientInterface.getName(),
                baseUrlSource(annotation, resolvedConfig),
                new TimeoutSummary(timeout.source(), timeout.timeoutMs()),
                new ResilienceSummary(
                        resolvedConfig.getResilience() != null && resolvedConfig.getResilience().isEnabled(),
                        resilience.retry(),
                        resilience.rateLimiter(),
                        resilience.circuitBreaker(),
                        resilience.bulkhead()),
                authMode(resolvedConfig),
                resolvedConfig.isFollowRedirects(),
                contracts.size(),
                Math.toIntExact(inherited));
    }

    private PoolSummary poolSummary(ReactiveHttpClientProperties.ClientConfig clientConfig) {
        boolean clientOverride = clientConfig != null && clientConfig.getPool() != null;
        ReactiveHttpClientProperties.NetworkConfig network = properties.getNetwork() != null
                ? properties.getNetwork()
                : new ReactiveHttpClientProperties.NetworkConfig();
        ReactiveHttpClientProperties.ConnectionPoolConfig pool = clientOverride
                ? clientConfig.getPool()
                : network.getConnectionPool();
        if (pool == null) {
            pool = new ReactiveHttpClientProperties.ConnectionPoolConfig();
        }
        return new PoolSummary(
                clientOverride ? "client" : "global",
                pool.getMaxConnections(),
                pool.getPendingAcquireTimeoutMs(),
                pool.isMetricsEnabled(),
                clientConfig != null && clientConfig.isHttp2Enabled() ? "HTTP/2" : "HTTP/1.1",
                clientConfig != null && clientConfig.isHttp2Enabled()
                        ? "connections-and-peer-streams"
                        : "connections",
                null);
    }

    private ClientRegistration clientRegistration(String beanName) {
        BeanDefinition definition = beanFactory.getBeanDefinition(beanName);
        boolean starterFactory = isStarterFactory(definition);
        Object objectType = definition.getAttribute(FactoryBean.OBJECT_TYPE_ATTRIBUTE);
        if (objectType == null && starterFactory) {
            objectType = definition.getPropertyValues().get("type");
            if (objectType == null) {
                objectType = definition.getResolvableType()
                        .as(FactoryBean.class).getGeneric(0).resolve();
            }
        }
        if (objectType instanceof Class<?> clazz && clazz.isInterface()
                && clazz.isAnnotationPresent(ReactiveHttpClient.class)) {
            return new ClientRegistration(clazz, starterFactory);
        }
        if (objectType instanceof String className) {
            try {
                Class<?> clazz = ClassUtils.resolveClassName(className, beanFactory.getBeanClassLoader());
                if (clazz.isInterface() && clazz.isAnnotationPresent(ReactiveHttpClient.class)) {
                    return new ClientRegistration(clazz, starterFactory);
                }
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        return null;
    }

    private boolean isStarterFactory(BeanDefinition definition) {
        String beanClassName = definition.getBeanClassName();
        if (beanClassName != null) {
            try {
                Class<?> beanClass = ClassUtils.resolveClassName(beanClassName, beanFactory.getBeanClassLoader());
                if (ReactiveHttpClientFactoryBean.class.isAssignableFrom(beanClass)) {
                    return true;
                }
            } catch (IllegalArgumentException ignored) {
                // Fall through to the factory-method return type.
            }
        }
        Class<?> resolvedBeanType = definition.getResolvableType().resolve();
        return resolvedBeanType != null
                && ReactiveHttpClientFactoryBean.class.isAssignableFrom(resolvedBeanType);
    }

    private record ClientRegistration(Class<?> clientInterface, boolean starterFactory) {
    }

    private static EffectiveHttpClientContract.TimeoutPolicy representativeTimeout(List<EffectiveHttpClientContract> contracts) {
        return contracts.stream()
                .map(EffectiveHttpClientContract::timeout)
                .filter(timeout -> !"disabled".equals(timeout.source()))
                .findFirst()
                .orElse(new EffectiveHttpClientContract.TimeoutPolicy("disabled", 0));
    }

    private static EffectiveHttpClientContract.ResiliencePolicy representativeResilience(List<EffectiveHttpClientContract> contracts) {
        return contracts.stream()
                .map(EffectiveHttpClientContract::resilience)
                .filter(resilience -> !"disabled".equals(resilience.retry())
                        || !"disabled".equals(resilience.rateLimiter())
                        || !"disabled".equals(resilience.circuitBreaker())
                        || !"disabled".equals(resilience.bulkhead()))
                .findFirst()
                .orElse(new EffectiveHttpClientContract.ResiliencePolicy(
                        "disabled", "disabled", "disabled", "disabled"));
    }

    private static String baseUrlSource(ReactiveHttpClient annotation,
                                 ReactiveHttpClientProperties.ClientConfig clientConfig) {
        if (annotation != null && StringUtils.hasText(annotation.baseUrl())) {
            return "annotation";
        }
        if (StringUtils.hasText(clientConfig.getBaseUrl())) {
            return "property";
        }
        return "missing";
    }

    private static String authMode(ReactiveHttpClientProperties.ClientConfig clientConfig) {
        if (StringUtils.hasText(clientConfig.getAuthProvider())) {
            return "provider-bean";
        }
        if (clientConfig.getAuth() != null && StringUtils.hasText(clientConfig.getAuth().getType())) {
            return clientConfig.getAuth().getType();
        }
        return "none";
    }

    private Boolean strictUnsafeRetryValidation(Class<?> clientInterface,
                                                ReactiveHttpClientProperties.ClientConfig clientConfig,
                                                ResilienceOperatorApplier resilienceOperatorApplier,
                                                boolean retryRegistryUnresolved) {
        ReactiveHttpClientProperties.ResilienceConfig resilience = clientConfig.getResilience();
        if (resilience == null
                || !resilience.isEnabled()
                || !resilience.isStrictUnsafeRetryValidation()) {
            return false;
        }
        boolean retryOperatorAvailable = resilienceOperatorApplier.isOperatorAvailable(
                ResilienceOperatorApplier.InstanceType.RETRY);
        boolean unresolvedRetry = false;
        for (Method method : clientInterface.getMethods()) {
            if (!isDeclarativeClientMethod(method)) {
                continue;
            }
            MethodMetadata meta = metadataCache.get(method);
            String httpMethod = diagnosticHttpMethod(meta, clientConfig);
            if (!isRetryMethodEnabled(resilience, httpMethod)) {
                continue;
            }
            if (retryRegistryUnresolved) {
                return null;
            }
            if (!retryOperatorAvailable) {
                return false;
            }
            RequestPlan plan = RequestPlan.from(meta, clientInterface);
            String retryInstance = resolveResilienceInstanceName(plan.retryInstanceName(), resilience.getRetry());
            if (!resilienceOperatorApplier.isInstanceConfigured(
                    ResilienceOperatorApplier.InstanceType.RETRY, retryInstance)) {
                unresolvedRetry = true;
                continue;
            }
            if (resilienceOperatorApplier.canRetryMoreThanOnce(retryInstance)) {
                return true;
            }
        }
        return unresolvedRetry ? null : false;
    }

    private boolean strictBodySigningValidation(ReactiveHttpClientProperties.ClientConfig clientConfig) {
        if (StringUtils.hasText(clientConfig.getAuthProvider())
                || clientConfig.getAuth() == null
                || clientConfig.getAuth().getAwsSigV4() == null
                || !AwsSigV4AuthProviderFactory.TYPE.equalsIgnoreCase(clientConfig.getAuth().getType())
                || !clientConfig.getAuth().getAwsSigV4().isStrictBodySigningValidation()) {
            return false;
        }
        return beanFactory.getBeanProvider(AuthProviderFactory.class)
                .orderedStream()
                .filter(factory -> factory.supports(clientConfig.getAuth().getType()))
                .findFirst()
                .filter(AwsSigV4AuthProviderFactory.class::isInstance)
                .isPresent();
    }

    private static String diagnosticHttpMethod(MethodMetadata meta,
                                               ReactiveHttpClientProperties.ClientConfig clientConfig) {
        if (StringUtils.hasText(meta.getHttpMethod())) {
            return meta.getHttpMethod();
        }
        if (StringUtils.hasText(meta.getApiRefName()) && clientConfig.getApis() != null) {
            ReactiveHttpClientProperties.ApiConfig apiConfig = clientConfig.getApis().get(meta.getApiRefName());
            if (apiConfig != null && StringUtils.hasText(apiConfig.getMethod())) {
                return apiConfig.getMethod().trim().toUpperCase(Locale.ROOT);
            }
        }
        return null;
    }

    private static boolean isRetryMethodEnabled(ReactiveHttpClientProperties.ResilienceConfig resilience,
                                                String httpMethod) {
        return httpMethod != null
                && resilience.getRetryMethods() != null
                && resilience.getRetryMethods().contains(httpMethod.toUpperCase(Locale.ROOT));
    }

    private static String resolveResilienceInstanceName(String methodLevel, String clientLevel) {
        return StringUtils.hasText(methodLevel) ? methodLevel : clientLevel;
    }

    private static boolean isDeclarativeClientMethod(Method method) {
        int modifiers = method.getModifiers();
        return method.getDeclaringClass() != Object.class
                && Modifier.isAbstract(modifiers)
                && !Modifier.isStatic(modifiers)
                && !method.isSynthetic()
                && !method.isDefault()
                && !method.isBridge();
    }

    private ResilienceDiagnostics resilienceDiagnostics() {
        ExistingBeanLookup circuitBreakerRegistry = existingBean(
                "io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry");
        ExistingBeanLookup retryRegistry = existingBean("io.github.resilience4j.retry.RetryRegistry");
        ExistingBeanLookup bulkheadRegistry = existingBean("io.github.resilience4j.bulkhead.BulkheadRegistry");
        ExistingBeanLookup rateLimiterRegistry = existingBean(
                "io.github.resilience4j.ratelimiter.RateLimiterRegistry");
        ResilienceOperatorApplier operatorApplier;
        try {
            operatorApplier = new Resilience4jOperatorApplier(
                    circuitBreakerRegistry.value(), retryRegistry.value(),
                    bulkheadRegistry.value(), rateLimiterRegistry.value());
        } catch (LinkageError error) {
            operatorApplier = new NoopResilienceOperatorApplier();
        }
        return new ResilienceDiagnostics(operatorApplier, retryRegistry.unresolved());
    }

    private ExistingBeanLookup existingBean(String className) {
        try {
            Class<?> type = ClassUtils.forName(className, beanFactory.getBeanClassLoader());
            return existingBean(beanFactory, type);
        } catch (ClassNotFoundException | LinkageError ex) {
            return ExistingBeanLookup.absent();
        }
    }

    private ExistingBeanLookup existingBean(ConfigurableListableBeanFactory factory, Class<?> type) {
        String[] beanNames = factory.getBeanNamesForType(type, true, false);
        List<String> uninspectableFactoryBeans = uninspectableFactoryBeanNames(
                factory, type, beanNames);
        if (!uninspectableFactoryBeans.isEmpty()
                && !selectionUnaffectedByUninspectableFactories(
                        factory, beanNames, uninspectableFactoryBeans)) {
            return ExistingBeanLookup.unresolvedLookup();
        }
        if (beanNames.length == 0) {
            BeanFactory parent = factory.getParentBeanFactory();
            if (parent instanceof ConfigurableListableBeanFactory parentFactory) {
                return existingBean(parentFactory, type);
            }
            return parent == null ? ExistingBeanLookup.absent() : ExistingBeanLookup.unresolvedLookup();
        }
        String beanName = selectedBeanName(factory, type, beanNames);
        if (beanName == null) {
            return ExistingBeanLookup.unresolvedLookup();
        }
        Object value = existingSingletonValue(factory, type, beanName);
        return value != null
                ? ExistingBeanLookup.available(value)
                : ExistingBeanLookup.unresolvedLookup();
    }

    private String selectedBeanName(ConfigurableListableBeanFactory factory,
                                    Class<?> type,
                                    String[] beanNames) {
        List<String> candidates = candidateNames(factory, beanNames);
        if (candidates.size() == 1) {
            return candidates.get(0);
        }

        String primaryCandidate = null;
        int primaryCount = 0;
        for (String beanName : candidates) {
            BeanDefinition definition = beanDefinition(factory, beanName);
            if (definition != null && definition.isPrimary()) {
                primaryCandidate = beanName;
                primaryCount++;
            }
        }
        if (primaryCount == 1) {
            return primaryCandidate;
        }
        if (primaryCount > 1) {
            return null;
        }

        String nonFallbackCandidate = null;
        int nonFallbackCount = 0;
        for (String beanName : candidates) {
            BeanDefinition definition = beanDefinition(factory, beanName);
            if (definition == null || !definition.isFallback()) {
                nonFallbackCandidate = beanName;
                nonFallbackCount++;
            }
        }
        if (nonFallbackCount == 1) {
            return nonFallbackCandidate;
        }

        PriorityCandidate priorityCandidate = priorityCandidate(factory, type, candidates);
        if (priorityCandidate.conflict()) {
            return null;
        }
        if (priorityCandidate.beanName() != null) {
            return priorityCandidate.beanName();
        }

        String defaultCandidate = null;
        for (String beanName : candidates) {
            BeanDefinition definition = beanDefinition(factory, beanName);
            boolean isDefault = !(definition instanceof AbstractBeanDefinition abstractDefinition)
                    || abstractDefinition.isDefaultCandidate();
            if (isDefault) {
                if (defaultCandidate != null) {
                    return null;
                }
                defaultCandidate = beanName;
            }
        }
        return defaultCandidate;
    }

    private static List<String> candidateNames(ConfigurableListableBeanFactory factory,
                                               String[] beanNames) {
        if (beanNames.length == 1) {
            return List.of(beanNames[0]);
        }
        List<String> autowireCandidates = new ArrayList<>(beanNames.length);
        for (String beanName : beanNames) {
            BeanDefinition definition = beanDefinition(factory, beanName);
            if (definition == null || definition.isAutowireCandidate()) {
                autowireCandidates.add(beanName);
            }
        }
        return autowireCandidates.isEmpty() ? List.of(beanNames) : autowireCandidates;
    }

    private PriorityCandidate priorityCandidate(ConfigurableListableBeanFactory factory,
                                                Class<?> type,
                                                List<String> candidates) {
        if (!(factory instanceof DefaultListableBeanFactory defaultFactory)
                || !(defaultFactory.getDependencyComparator() instanceof OrderComparator comparator)) {
            return PriorityCandidate.none();
        }
        String selected = null;
        Integer selectedPriority = null;
        boolean conflict = false;
        for (String beanName : candidates) {
            Object candidate = candidateValue(factory, type, beanName);
            if (candidate == null) {
                continue;
            }
            Integer priority = comparator.getPriority(candidate);
            if (priority == null) {
                continue;
            }
            if (selectedPriority == null || priority < selectedPriority) {
                selected = beanName;
                selectedPriority = priority;
                conflict = false;
            } else if (priority.equals(selectedPriority)) {
                conflict = true;
            }
        }
        return new PriorityCandidate(selected, conflict);
    }

    private Object candidateValue(ConfigurableListableBeanFactory factory,
                                  Class<?> type,
                                  String beanName) {
        Object value = existingSingletonValue(factory, type, beanName);
        return value != null ? value : factory.getType(beanName, false);
    }

    private Object existingSingletonValue(ConfigurableListableBeanFactory factory,
                                          Class<?> type,
                                          String beanName) {
        Object singleton = factory.getSingleton(beanName);
        if (type.isInstance(singleton)) {
            return singleton;
        }
        if (singleton instanceof FactoryBean<?>) {
            Object product = cachedFactoryBeanProduct(factory, beanName);
            if (type.isInstance(product)) {
                return product;
            }
        }
        return null;
    }

    private List<String> uninspectableFactoryBeanNames(
            ConfigurableListableBeanFactory factory,
            Class<?> type,
            String[] knownBeanNames) {
        List<String> knownNames = List.of(knownBeanNames);
        List<String> uninspectableNames = new ArrayList<>();
        for (String beanName : factory.getBeanDefinitionNames()) {
            if (knownNames.contains(beanName) || factory.containsSingleton(beanName)) {
                continue;
            }
            BeanDefinition definition = beanDefinition(factory, beanName);
            if (definition == null || definition.isAbstract()) {
                continue;
            }
            Class<?> factoryType = beanType(factory, definition);
            if (factoryType == null || !FactoryBean.class.isAssignableFrom(factoryType)) {
                continue;
            }
            Class<?> objectType = factoryBeanObjectType(factory, definition);
            if (objectType == null || type.isAssignableFrom(objectType)) {
                uninspectableNames.add(beanName);
            }
        }
        return uninspectableNames;
    }

    private static boolean selectionUnaffectedByUninspectableFactories(
            ConfigurableListableBeanFactory factory,
            String[] knownBeanNames,
            List<String> uninspectableBeanNames) {
        if (knownBeanNames.length == 0) {
            return false;
        }
        List<String> allBeanNames = new ArrayList<>(
                knownBeanNames.length + uninspectableBeanNames.size());
        Collections.addAll(allBeanNames, knownBeanNames);
        allBeanNames.addAll(uninspectableBeanNames);
        List<String> candidates = candidateNames(factory, allBeanNames.toArray(String[]::new));
        Set<String> uninspectableNames = Set.copyOf(uninspectableBeanNames);
        if (candidates.stream().noneMatch(uninspectableNames::contains)) {
            return true;
        }

        String primaryCandidate = null;
        int primaryCount = 0;
        for (String beanName : candidates) {
            BeanDefinition definition = beanDefinition(factory, beanName);
            if (definition != null && definition.isPrimary()) {
                primaryCandidate = beanName;
                primaryCount++;
            }
        }
        return primaryCount == 1 && !uninspectableNames.contains(primaryCandidate);
    }

    private static Class<?> beanType(ConfigurableListableBeanFactory factory,
                                     BeanDefinition definition) {
        Class<?> type = definition.getResolvableType().resolve();
        if (type != null || definition.getBeanClassName() == null) {
            return type;
        }
        try {
            return ClassUtils.resolveClassName(
                    definition.getBeanClassName(), factory.getBeanClassLoader());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static Class<?> factoryBeanObjectType(ConfigurableListableBeanFactory factory,
                                                  BeanDefinition definition) {
        Object objectType = definition.getAttribute(FactoryBean.OBJECT_TYPE_ATTRIBUTE);
        if (objectType instanceof Class<?> clazz) {
            return clazz;
        }
        if (objectType instanceof ResolvableType resolvableType) {
            return resolvableType.resolve();
        }
        if (objectType instanceof String className) {
            try {
                return ClassUtils.resolveClassName(className, factory.getBeanClassLoader());
            } catch (IllegalArgumentException ex) {
                return null;
            }
        }
        return definition.getResolvableType().as(FactoryBean.class).getGeneric(0).resolve();
    }

    private static BeanDefinition beanDefinition(ConfigurableListableBeanFactory factory,
                                                 String beanName) {
        return factory.containsBeanDefinition(beanName)
                ? factory.getMergedBeanDefinition(beanName)
                : null;
    }

    private Object cachedFactoryBeanProduct(ConfigurableListableBeanFactory factory,
                                            String beanName) {
        if (!(factory instanceof FactoryBeanRegistrySupport registry)
                || GET_CACHED_FACTORY_BEAN_OBJECT == null) {
            return null;
        }
        try {
            ReflectionUtils.makeAccessible(GET_CACHED_FACTORY_BEAN_OBJECT);
            return ReflectionUtils.invokeMethod(GET_CACHED_FACTORY_BEAN_OBJECT, registry, beanName);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private record PriorityCandidate(String beanName, boolean conflict) {

        private static PriorityCandidate none() {
            return new PriorityCandidate(null, false);
        }
    }

    private record ResilienceDiagnostics(
            ResilienceOperatorApplier operatorApplier,
            boolean retryRegistryUnresolved) {
    }

    private record ExistingBeanLookup(Object value, boolean unresolved) {

        private static ExistingBeanLookup available(Object value) {
            return new ExistingBeanLookup(value, false);
        }

        private static ExistingBeanLookup absent() {
            return new ExistingBeanLookup(null, false);
        }

        private static ExistingBeanLookup unresolvedLookup() {
            return new ExistingBeanLookup(null, true);
        }
    }

    record ClientSnapshotEntry(
            ClientSummary summary,
            Boolean strictUnsafeRetryValidation,
            boolean strictBodySigningValidation,
            PoolSummary pool,
            long logicalCallTimeoutMs,
            boolean compressionEnabled,
            int codecMaxInMemorySizeMb
    ) {
    }

    record PoolSummary(
            String source,
            int maxConnections,
            long pendingAcquireTimeoutMs,
            boolean metricsEnabled,
            String protocol,
            String capacityBasis,
            Long maxConcurrentStreams
    ) {
    }

    /** Sanitized immutable summary for one registered reactive HTTP client. */
    public record ClientSummary(
            String clientName,
            String clientInterface,
            String baseUrlSource,
            TimeoutSummary timeout,
            ResilienceSummary resilience,
            String authMode,
            boolean followRedirects,
            int endpointCount,
            int inheritedEndpointCount
    ) {
    }

    /** Effective timeout source and value exposed in diagnostics snapshots. */
    public record TimeoutSummary(String source, long timeoutMs) {
    }

    /** Effective resilience operator names exposed in diagnostics snapshots. */
    public record ResilienceSummary(
            boolean configured,
            String retry,
            String rateLimiter,
            String circuitBreaker,
            String bulkhead
    ) {
    }
}
