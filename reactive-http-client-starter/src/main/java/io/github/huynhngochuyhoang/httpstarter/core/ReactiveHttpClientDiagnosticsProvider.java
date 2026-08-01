package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.annotation.ReactiveHttpClient;
import io.github.huynhngochuyhoang.httpstarter.auth.AuthProviderFactory;
import io.github.huynhngochuyhoang.httpstarter.auth.AwsSigV4AuthProviderFactory;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.FactoryBeanRegistrySupport;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

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
        if (retryRegistryUnresolved) {
            return null;
        }
        if (!resilienceOperatorApplier.isOperatorAvailable(ResilienceOperatorApplier.InstanceType.RETRY)) {
            return false;
        }
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
            String[] beanNames = beanFactory.getBeanNamesForType(type, true, false);
            if (beanNames.length == 0) {
                return ExistingBeanLookup.absent();
            }
            String beanName = selectedBeanName(beanNames);
            if (beanName == null) {
                return ExistingBeanLookup.unresolvedLookup();
            }
            Object singleton = beanFactory.getSingleton(beanName);
            if (type.isInstance(singleton)) {
                return ExistingBeanLookup.available(singleton);
            }
            if (singleton instanceof FactoryBean<?>) {
                Object product = cachedFactoryBeanProduct(beanName);
                if (type.isInstance(product)) {
                    return ExistingBeanLookup.available(product);
                }
            }
            return ExistingBeanLookup.unresolvedLookup();
        } catch (ClassNotFoundException | LinkageError ex) {
            return ExistingBeanLookup.absent();
        }
    }

    private String selectedBeanName(String[] beanNames) {
        String soleCandidate = null;
        int candidateCount = 0;
        String primaryCandidate = null;
        int primaryCount = 0;
        String nonFallbackCandidate = null;
        int nonFallbackCount = 0;
        for (String beanName : beanNames) {
            BeanDefinition definition = beanFactory.containsBeanDefinition(beanName)
                    ? beanFactory.getBeanDefinition(beanName)
                    : null;
            if (definition != null && !definition.isAutowireCandidate()) {
                continue;
            }
            soleCandidate = beanName;
            candidateCount++;
            if (definition != null && definition.isPrimary()) {
                primaryCandidate = beanName;
                primaryCount++;
            }
            if (definition == null || !definition.isFallback()) {
                nonFallbackCandidate = beanName;
                nonFallbackCount++;
            }
        }
        if (primaryCount == 1) {
            return primaryCandidate;
        }
        if (primaryCount > 1) {
            return null;
        }
        if (candidateCount == 1) {
            return soleCandidate;
        }
        return nonFallbackCount == 1 ? nonFallbackCandidate : null;
    }

    private Object cachedFactoryBeanProduct(String beanName) {
        if (!(beanFactory instanceof FactoryBeanRegistrySupport registry)
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
