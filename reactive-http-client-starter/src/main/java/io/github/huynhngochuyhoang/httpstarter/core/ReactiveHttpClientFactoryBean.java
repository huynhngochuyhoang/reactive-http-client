package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.annotation.ReactiveHttpClient;
import io.github.huynhngochuyhoang.httpstarter.auth.AuthProvider;
import io.github.huynhngochuyhoang.httpstarter.auth.OutboundAuthFilter;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import io.github.huynhngochuyhoang.httpstarter.filter.CorrelationIdWebFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.lang.NonNull;
import org.springframework.util.StringUtils;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.resources.ConnectionProvider;
import reactor.netty.http.client.HttpClient;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Spring {@link FactoryBean} that creates a JDK dynamic proxy for a given
 * {@code @ReactiveHttpClient} interface.
 *
 * <p>Registered automatically by {@link io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientsRegistrar}.
 */
public class ReactiveHttpClientFactoryBean<T> implements FactoryBean<T>, ApplicationContextAware, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(ReactiveHttpClientFactoryBean.class);
    private static final int MAX_CODEC_MAX_IN_MEMORY_SIZE_MB = Integer.MAX_VALUE / (1024 * 1024);

    private Class<T> type;
    private ApplicationContext applicationContext;
    private ConnectionProvider connectionProvider;

    // -------------------------------------------------------------------------
    // FactoryBean contract
    // -------------------------------------------------------------------------

    @Override
    @SuppressWarnings("unchecked")
    public T getObject() {
        ReactiveHttpClient annotation = type.getAnnotation(ReactiveHttpClient.class);
        String clientName = annotation.name();

        ReactiveHttpClientProperties properties = applicationContext
                .getBeanProvider(ReactiveHttpClientProperties.class)
                .getIfAvailable(ReactiveHttpClientProperties::new);

        ReactiveHttpClientProperties.ClientConfig config = properties.getClients()
                .getOrDefault(clientName, new ReactiveHttpClientProperties.ClientConfig());

        String baseUrl = StringUtils.hasText(annotation.baseUrl())
                ? annotation.baseUrl()
                : config.getBaseUrl();

        if (!StringUtils.hasText(baseUrl)) {
            throw new IllegalStateException(
                    "No baseUrl configured for @ReactiveHttpClient(name=\"" + clientName + "\"). "
                            + "Set reactive.http.clients." + clientName + ".base-url in application.yml "
                            + "or use @ReactiveHttpClient(name=\"" + clientName + "\", baseUrl=\"...\")");
        }

        AuthProvider authProvider = resolveAuthProvider(clientName, config);
        WebClient webClient = buildWebClient(
                baseUrl,
                config,
                properties.getNetwork(),
                properties.getCorrelationId(),
                clientName,
                authProvider);

        MethodMetadataCache metadataCache = applicationContext
                .getBeanProvider(MethodMetadataCache.class)
                .getIfAvailable(MethodMetadataCache::new);

        DefaultErrorDecoder errorDecoder = applicationContext
                .getBeanProvider(DefaultErrorDecoder.class)
                .getIfAvailable(DefaultErrorDecoder::new);

        Object circuitBreakerRegistry = resolveSafely("io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry");
        Object retryRegistry = resolveSafely("io.github.resilience4j.retry.RetryRegistry");
        Object bulkheadRegistry = resolveSafely("io.github.resilience4j.bulkhead.BulkheadRegistry");
        ResilienceOperatorApplier resilienceOperatorApplier = resolveResilienceOperatorApplier(
                circuitBreakerRegistry, retryRegistry, bulkheadRegistry);
        ObjectMapper objectMapper = applicationContext.getBeanProvider(ObjectMapper.class).getIfAvailable();

        if (config.getResilience() != null && config.getResilience().isEnabled()) {
            validatePerMethodResilienceInstances(type, metadataCache, resilienceOperatorApplier, clientName);
        }

        ReactiveClientInvocationHandler handler = new ReactiveClientInvocationHandler(
                webClient,
                metadataCache,
                new RequestArgumentResolver(),
                errorDecoder,
                config,
                clientName,
                applicationContext,
                resilienceOperatorApplier,
                objectMapper,
                properties.getObservability()
        );

        log.info("Creating reactive HTTP client proxy for [{}] → {}", clientName, baseUrl);

        return (T) Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class[]{type},
                handler
        );
    }

    @Override
    public Class<T> getObjectType() {
        return type;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    // -------------------------------------------------------------------------
    // ApplicationContextAware
    // -------------------------------------------------------------------------

    @Override
    public void setApplicationContext(@NonNull ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    // -------------------------------------------------------------------------
    // DisposableBean
    // -------------------------------------------------------------------------

    /**
     * Disposes the {@link ConnectionProvider} created for this client when the
     * Spring context shuts down. Without this, the pool leaks — harmless in a
     * normal JVM exit but problematic in test suites that reload the context
     * many times (OOM on repeated context cycles) and in hot-reload scenarios.
     */
    @Override
    public void destroy() {
        if (connectionProvider != null) {
            connectionProvider.disposeLater()
                    .subscribe(
                            null,
                            e -> log.warn("Error while disposing ConnectionProvider for client [{}]",
                                    type != null ? type.getSimpleName() : "?", e));
        }
    }

    // -------------------------------------------------------------------------
    // Setters (called by Spring's BeanDefinitionBuilder)
    // -------------------------------------------------------------------------

    public void setType(Class<T> type) {
        this.type = type;
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private WebClient buildWebClient(String baseUrl,
                                     ReactiveHttpClientProperties.ClientConfig config,
                                     ReactiveHttpClientProperties.NetworkConfig networkConfig,
                                     ReactiveHttpClientProperties.CorrelationIdConfig correlationIdConfig,
                                     String clientName,
                                     AuthProvider authProvider) {
        ReactiveHttpClientProperties.NetworkConfig resolvedNetworkConfig = networkConfig != null
                ? networkConfig
                : new ReactiveHttpClientProperties.NetworkConfig();
        ReactiveHttpClientProperties.ConnectionPoolConfig pool = resolveConnectionPool(config, resolvedNetworkConfig);
        // Include the interface fully-qualified name in the pool name so two clients that
        // share the same logical name but correspond to different interfaces never silently
        // share a connection pool.
        String poolName = "reactive-http-client-" + clientName
                + (type != null ? "-" + type.getName() : "");
        ConnectionProvider.Builder providerBuilder = ConnectionProvider.builder(poolName)
                .maxConnections(Math.max(1, pool.getMaxConnections()))
                .pendingAcquireTimeout(Duration.ofMillis(Math.max(0, pool.getPendingAcquireTimeoutMs())));
        if (pool.getMaxIdleTimeMs() > 0) {
            providerBuilder.maxIdleTime(Duration.ofMillis(pool.getMaxIdleTimeMs()));
        }
        if (pool.getMaxLifeTimeMs() > 0) {
            providerBuilder.maxLifeTime(Duration.ofMillis(pool.getMaxLifeTimeMs()));
        }
        if (pool.getEvictInBackgroundMs() > 0) {
            providerBuilder.evictInBackground(Duration.ofMillis(pool.getEvictInBackgroundMs()));
        }
        if (pool.isMetricsEnabled()) {
            providerBuilder.metrics(true);
        }
        // Store the provider on the instance field so destroy() can dispose it cleanly on context shutdown.
        this.connectionProvider = providerBuilder.build();

        HttpClient httpClient = HttpClient.create(this.connectionProvider)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, resolvedNetworkConfig.getConnectTimeoutMs())
                .doOnConnected(connection -> {
                    // Safety-net handlers: fire if a connection gets stuck in the pool beyond the configured limit.
                    connection.addHandlerLast(new ReadTimeoutHandler(
                            resolvedNetworkConfig.getNetworkReadTimeoutMs(), TimeUnit.MILLISECONDS));
                    connection.addHandlerLast(new WriteTimeoutHandler(
                            resolvedNetworkConfig.getNetworkWriteTimeoutMs(), TimeUnit.MILLISECONDS));
                })
                .compress(config.isCompressionEnabled());

        ReactiveHttpClientProperties.ProxyConfig proxy = resolveProxy(config, resolvedNetworkConfig);
        if (proxy != null && proxy.getType() != ReactiveHttpClientProperties.ProxyConfig.Type.NONE
                && StringUtils.hasText(proxy.getHost())) {
            if (proxy.getPort() <= 0) {
                throw new IllegalArgumentException(
                        "Proxy host is set but port is invalid (got " + proxy.getPort() + "). "
                                + "Set reactive.http.clients." + clientName + ".proxy.port (or reactive.http.network.proxy.port) to a valid port > 0.");
            }
            httpClient = HttpProxyApplier.apply(httpClient, proxy);
        }

        ReactiveHttpClientProperties.TlsConfig tls = resolveTls(config, resolvedNetworkConfig);
        if (tls != null) {
            httpClient = TlsContextApplier.apply(httpClient, tls, clientName);
        }
        WebClient.Builder builder = applicationContext
                .getBeanProvider(WebClient.Builder.class)
                .getIfAvailable(WebClient::builder);

        WebClient.Builder configured = builder
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(resolveCodecMaxInMemorySizeBytes(config)))
                .filter(correlationIdFilter(correlationIdConfig));

        if (authProvider != null) {
            configured = configured.filter(new OutboundAuthFilter(clientName, authProvider));
        }

        if (config.isExchangeLoggingEnabled()) {
            configured = configured.filter(loggingFilter());
        }

        // Apply per-client customizations registered as Spring beans.
        // Customizers are applied in @Order / Ordered sequence after all built-in filters.
        final WebClient.Builder finalConfigured = configured;
        applicationContext.getBeanProvider(ReactiveHttpClientCustomizer.class)
                .orderedStream()
                .filter(customizer -> customizer.supports(clientName))
                .forEach(customizer -> customizer.customize(finalConfigured));

        return configured.build();
    }

    private ReactiveHttpClientProperties.ConnectionPoolConfig resolveConnectionPool(
            ReactiveHttpClientProperties.ClientConfig config,
            ReactiveHttpClientProperties.NetworkConfig networkConfig) {
        if (config != null && config.getPool() != null) {
            return config.getPool();
        }
        return networkConfig.getConnectionPool() != null
                ? networkConfig.getConnectionPool()
                : new ReactiveHttpClientProperties.ConnectionPoolConfig();
    }

    /** Per-client proxy override wins; otherwise the global proxy applies. {@code null} = direct connection. */
    static ReactiveHttpClientProperties.ProxyConfig resolveProxy(
            ReactiveHttpClientProperties.ClientConfig config,
            ReactiveHttpClientProperties.NetworkConfig networkConfig) {
        if (config != null && config.getProxy() != null) return config.getProxy();
        return networkConfig != null ? networkConfig.getProxy() : null;
    }

    /** Per-client TLS override wins; otherwise the global TLS applies. {@code null} = JDK defaults. */
    static ReactiveHttpClientProperties.TlsConfig resolveTls(
            ReactiveHttpClientProperties.ClientConfig config,
            ReactiveHttpClientProperties.NetworkConfig networkConfig) {
        if (config != null && config.getTls() != null) return config.getTls();
        return networkConfig != null ? networkConfig.getTls() : null;
    }

    int resolveCodecMaxInMemorySizeBytes(ReactiveHttpClientProperties.ClientConfig config) {
        int sizeMb = config.getCodecMaxInMemorySizeMb();
        if (sizeMb < 0) {
            throw new IllegalArgumentException(
                    "reactive.http.clients.*.codec-max-in-memory-size-mb must be >= 0 but was " + sizeMb
                            + ". Use 0 for unlimited, or a positive value to set a cap in MiB.");
        }
        if (sizeMb == 0) {
            // 0 means "unlimited" — pass -1 to Spring's codec configuration.
            log.warn("reactive.http.clients.*.codec-max-in-memory-size-mb is 0: codec buffer limit is disabled (unlimited). "
                    + "Set a positive value to enforce a cap and avoid out-of-memory errors on large responses.");
            return -1;
        }
        if (sizeMb > MAX_CODEC_MAX_IN_MEMORY_SIZE_MB) {
            throw new IllegalArgumentException("reactive.http.clients.*.codec-max-in-memory-size-mb must be <= "
                    + MAX_CODEC_MAX_IN_MEMORY_SIZE_MB + " but was " + sizeMb);
        }
        long sizeBytes = sizeMb * 1024L * 1024L;
        return (int) sizeBytes;
    }

    /** Propagates X-Correlation-Id from Reactor context (set by CorrelationIdWebFilter) or MDC. */
    private ExchangeFilterFunction correlationIdFilter(ReactiveHttpClientProperties.CorrelationIdConfig correlationIdConfig) {
        return CorrelationIdWebFilter.exchangeFilter(correlationIdConfig);
    }

    /** Logs method, URL, status and latency when exchange logging is enabled for the client. */
    private ExchangeFilterFunction loggingFilter() {
        return (request, next) -> {
            long startMs = System.currentTimeMillis();
            return next.exchange(request)
                    .doOnNext(response -> {
                        String outcome = response.statusCode().isError() ? "HTTP_ERROR" : "OK";
                        log.debug("[{}] {} {} -> {} {} ({}ms)",
                                type != null ? type.getSimpleName() : "ReactiveHttpClient",
                                request.method(),
                                request.url(),
                                outcome,
                                response.statusCode().value(),
                                System.currentTimeMillis() - startMs);
                    })
                    .doOnError(error -> log.debug("[{}] {} {} -> TRANSPORT_ERROR {} ({}ms)",
                            type != null ? type.getSimpleName() : "ReactiveHttpClient",
                            request.method(),
                            request.url(),
                            error.getClass().getSimpleName(),
                            System.currentTimeMillis() - startMs));
        };
    }

    private Object resolveSafely(String className) {
        try {
            Class<?> clazz = Class.forName(className);
            return applicationContext.getBeanProvider(clazz).getIfAvailable();
        } catch (ClassNotFoundException ignored) {
            return null;
        }
    }

    private ResilienceOperatorApplier resolveResilienceOperatorApplier(
            Object circuitBreakerRegistry,
            Object retryRegistry,
            Object bulkheadRegistry) {
        if (circuitBreakerRegistry == null && retryRegistry == null && bulkheadRegistry == null) {
            return new NoopResilienceOperatorApplier();
        }
        try {
            return new Resilience4jOperatorApplier(circuitBreakerRegistry, retryRegistry, bulkheadRegistry);
        } catch (Throwable error) {
            log.warn("Resilience4j operator applier could not be initialized. Falling back to no-op resilience.",
                    error);
            return new NoopResilienceOperatorApplier();
        }
    }

    /**
     * Eagerly parses every method on {@code clientInterface}, then verifies that
     * any per-method {@code @Retry} / {@code @CircuitBreaker} / {@code @Bulkhead}
     * instance name has a corresponding entry in the matching Resilience4j
     * registry. Fails fast at proxy construction time so a typo doesn't silently
     * fall back to default-configured behaviour.
     */
    private void validatePerMethodResilienceInstances(Class<?> clientInterface,
                                                      MethodMetadataCache metadataCache,
                                                      ResilienceOperatorApplier applier,
                                                      String clientName) {
        List<String> missing = new ArrayList<>();
        for (Method method : clientInterface.getMethods()) {
            if (method.isSynthetic() || method.isDefault() || method.isBridge()) continue;
            MethodMetadata meta;
            try {
                meta = metadataCache.get(method);
            } catch (RuntimeException e) {
                // Methods that fail to parse (e.g. helper methods without HTTP verb)
                // are validated only when invoked; skip them here.
                continue;
            }
            checkInstance(applier, ResilienceOperatorApplier.InstanceType.RETRY,
                    meta.getRetryInstanceName(), method, "@Retry", missing);
            checkInstance(applier, ResilienceOperatorApplier.InstanceType.CIRCUIT_BREAKER,
                    meta.getCircuitBreakerInstanceName(), method, "@CircuitBreaker", missing);
            checkInstance(applier, ResilienceOperatorApplier.InstanceType.BULKHEAD,
                    meta.getBulkheadInstanceName(), method, "@Bulkhead", missing);
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "Reactive HTTP client '" + clientName + "' references undefined Resilience4j instances:\n  - "
                            + String.join("\n  - ", missing)
                            + "\nDefine them under resilience4j.<retry|circuitbreaker|bulkhead>.instances.* in application config.");
        }
    }

    private static void checkInstance(ResilienceOperatorApplier applier,
                                      ResilienceOperatorApplier.InstanceType type,
                                      String instanceName,
                                      Method method,
                                      String annotationName,
                                      List<String> missing) {
        if (instanceName == null || instanceName.isBlank()) return;
        if (!applier.isInstanceConfigured(type, instanceName)) {
            missing.add(annotationName + "(\"" + instanceName + "\") on "
                    + method.getDeclaringClass().getSimpleName() + "#" + method.getName());
        }
    }

    private AuthProvider resolveAuthProvider(String clientName, ReactiveHttpClientProperties.ClientConfig config) {
        if (config == null || !StringUtils.hasText(config.getAuthProvider())) {
            return null;
        }
        try {
            return applicationContext.getBean(config.getAuthProvider(), AuthProvider.class);
        } catch (NoSuchBeanDefinitionException ex) {
            throw new IllegalStateException(
                    "No AuthProvider bean named '" + config.getAuthProvider() + "' configured for client '" + clientName + "'", ex);
        }
    }
}
