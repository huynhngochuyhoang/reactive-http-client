package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.annotation.ReactiveHttpClient;
import io.github.huynhngochuyhoang.httpstarter.auth.AuthProviderFactory;
import io.github.huynhngochuyhoang.httpstarter.auth.AwsSigV4AuthProviderFactory;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.util.ClassUtils;
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
        ResilienceOperatorApplier resilienceOperatorApplier = resilienceOperatorApplier();
        return List.of(beanFactory.getBeanDefinitionNames()).stream()
                .map(this::clientInterface)
                .filter(Objects::nonNull)
                .distinct()
                .map(clientInterface -> clientSummaryEntry(clientInterface, resilienceOperatorApplier))
                .sorted(Comparator.comparing(ClientSummary::clientName)
                        .thenComparing(ClientSummary::clientInterface))
                .toList();
    }

    List<ClientSnapshotEntry> clientSnapshotEntries() {
        ResilienceOperatorApplier resilienceOperatorApplier = resilienceOperatorApplier();
        return List.of(beanFactory.getBeanDefinitionNames()).stream()
                .map(this::clientInterface)
                .filter(Objects::nonNull)
                .distinct()
                .map(clientInterface -> clientSnapshotEntry(clientInterface, resilienceOperatorApplier))
                .sorted(Comparator.comparing((ClientSnapshotEntry entry) -> entry.summary().clientName())
                        .thenComparing(entry -> entry.summary().clientInterface()))
                .toList();
    }

    private ClientSummary clientSummaryEntry(Class<?> clientInterface,
                                             ResilienceOperatorApplier resilienceOperatorApplier) {
        ReactiveHttpClient annotation = clientInterface.getAnnotation(ReactiveHttpClient.class);
        String clientName = annotation != null ? annotation.name() : "";
        ReactiveHttpClientProperties.ClientConfig clientConfig = properties.getClients()
                .getOrDefault(clientName, new ReactiveHttpClientProperties.ClientConfig());
        return clientSummary(clientInterface, clientName, clientConfig, metadataCache, resilienceOperatorApplier);
    }

    private ClientSnapshotEntry clientSnapshotEntry(Class<?> clientInterface,
                                                    ResilienceOperatorApplier resilienceOperatorApplier) {
        ReactiveHttpClient annotation = clientInterface.getAnnotation(ReactiveHttpClient.class);
        String clientName = annotation != null ? annotation.name() : "";
        ReactiveHttpClientProperties.ClientConfig clientConfig = properties.getClients()
                .getOrDefault(clientName, new ReactiveHttpClientProperties.ClientConfig());
        ClientSummary summary = clientSummary(
                clientInterface, clientName, clientConfig, metadataCache, resilienceOperatorApplier);
        return new ClientSnapshotEntry(
                summary,
                strictUnsafeRetryValidation(clientInterface, clientConfig, resilienceOperatorApplier),
                strictBodySigningValidation(clientConfig),
                poolSummary(clientConfig));
    }

    static ClientSummary clientSummary(Class<?> clientInterface,
                                       String clientName,
                                       ReactiveHttpClientProperties.ClientConfig clientConfig,
                                       MethodMetadataCache metadataCache,
                                       ResilienceOperatorApplier resilienceOperatorApplier) {
        ReactiveHttpClient annotation = clientInterface.getAnnotation(ReactiveHttpClient.class);
        ReactiveHttpClientProperties.ClientConfig resolvedConfig = clientConfig != null
                ? clientConfig
                : new ReactiveHttpClientProperties.ClientConfig();
        List<EffectiveHttpClientContract> contracts = EffectiveHttpClientContractExporter.export(
                clientInterface, clientName, resolvedConfig, metadataCache, resilienceOperatorApplier, null, false);
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
                pool.isMetricsEnabled());
    }

    private Class<?> clientInterface(String beanName) {
        BeanDefinition definition = beanFactory.getBeanDefinition(beanName);
        Object objectType = definition.getAttribute(FactoryBean.OBJECT_TYPE_ATTRIBUTE);
        if (objectType == null && ReactiveHttpClientFactoryBean.class.getName().equals(definition.getBeanClassName())) {
            objectType = definition.getPropertyValues().get("type");
        }
        if (objectType instanceof Class<?> clazz && clazz.isInterface()
                && clazz.isAnnotationPresent(ReactiveHttpClient.class)) {
            return clazz;
        }
        if (objectType instanceof String className) {
            try {
                Class<?> clazz = ClassUtils.resolveClassName(className, beanFactory.getBeanClassLoader());
                if (clazz.isInterface() && clazz.isAnnotationPresent(ReactiveHttpClient.class)) {
                    return clazz;
                }
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        return null;
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

    private boolean strictUnsafeRetryValidation(Class<?> clientInterface,
                                                ReactiveHttpClientProperties.ClientConfig clientConfig,
                                                ResilienceOperatorApplier resilienceOperatorApplier) {
        ReactiveHttpClientProperties.ResilienceConfig resilience = clientConfig.getResilience();
        if (resilience == null
                || !resilience.isEnabled()
                || !resilience.isStrictUnsafeRetryValidation()
                || !resilienceOperatorApplier.isOperatorAvailable(ResilienceOperatorApplier.InstanceType.RETRY)) {
            return false;
        }
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
            if (StringUtils.hasText(plan.retryInstanceName())
                    && !resilienceOperatorApplier.isInstanceConfigured(ResilienceOperatorApplier.InstanceType.RETRY, retryInstance)) {
                continue;
            }
            if (resilienceOperatorApplier.canRetryMoreThanOnce(retryInstance)) {
                return true;
            }
        }
        return false;
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

    private ResilienceOperatorApplier resilienceOperatorApplier() {
        Object circuitBreakerRegistry = bean("io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry");
        Object retryRegistry = bean("io.github.resilience4j.retry.RetryRegistry");
        Object bulkheadRegistry = bean("io.github.resilience4j.bulkhead.BulkheadRegistry");
        Object rateLimiterRegistry = bean("io.github.resilience4j.ratelimiter.RateLimiterRegistry");
        try {
            return new Resilience4jOperatorApplier(
                    circuitBreakerRegistry, retryRegistry, bulkheadRegistry, rateLimiterRegistry);
        } catch (LinkageError error) {
            return new NoopResilienceOperatorApplier();
        }
    }

    private Object bean(String className) {
        try {
            Class<?> type = ClassUtils.forName(className, beanFactory.getBeanClassLoader());
            return beanFactory.getBeanProvider(type).getIfAvailable();
        } catch (ClassNotFoundException | LinkageError ex) {
            return null;
        }
    }

    record ClientSnapshotEntry(
            ClientSummary summary,
            boolean strictUnsafeRetryValidation,
            boolean strictBodySigningValidation,
            PoolSummary pool
    ) {
    }

    record PoolSummary(
            String source,
            int maxConnections,
            long pendingAcquireTimeoutMs,
            boolean metricsEnabled
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
