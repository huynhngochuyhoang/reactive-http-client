package io.github.huynhngochuyhoang.httpstarter.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.huynhngochuyhoang.httpstarter.annotation.ReactiveHttpClient;
import io.github.huynhngochuyhoang.httpstarter.auth.AuthProvider;
import io.github.huynhngochuyhoang.httpstarter.auth.AuthProviderFactory;
import io.github.huynhngochuyhoang.httpstarter.auth.OutboundAuthFilter;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import io.github.huynhngochuyhoang.httpstarter.filter.CorrelationIdWebFilter;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.lang.NonNull;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.HttpProtocol;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
        ClientNameValidator.validateAnnotation(clientName, annotation.baseUrl(), "@ReactiveHttpClient");

        ReactiveHttpClientProperties properties = applicationContext
                .getBeanProvider(ReactiveHttpClientProperties.class)
                .getIfAvailable(ReactiveHttpClientProperties::new);
        properties.getClients().keySet()
                .forEach(name -> ClientNameValidator.validate(name, "reactive.http.clients"));

        ReactiveHttpClientProperties.ClientConfig config = properties.getClients()
                .getOrDefault(clientName, new ReactiveHttpClientProperties.ClientConfig());

        boolean annotationBaseUrl = StringUtils.hasText(annotation.baseUrl());
        String baseUrl = annotationBaseUrl
                ? annotation.baseUrl()
                : config.getBaseUrl();

        if (!StringUtils.hasText(baseUrl)) {
            throw new IllegalStateException(
                    "No baseUrl configured for @ReactiveHttpClient(name=\"" + clientName + "\"). "
                            + "Set reactive.http.clients." + clientName + ".base-url in application.yml "
                            + "or use @ReactiveHttpClient(name=\"" + clientName + "\", baseUrl=\"...\")");
        }

        validateClientConfiguration(clientName, config, properties.getNetwork());
        AuthProvider authProvider = resolveAuthProvider(clientName, config);
        logStartupConfiguration(
                clientName,
                baseUrl,
                annotationBaseUrl ? "annotation" : "property",
                config,
                properties.getNetwork(),
                properties.getObservability());
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
        validateApiRefMappings(type, metadataCache, config, clientName);

        DefaultErrorDecoder errorDecoder = applicationContext
                .getBeanProvider(DefaultErrorDecoder.class)
                .getIfAvailable(DefaultErrorDecoder::new)
                .forClient(clientName);

        Object circuitBreakerRegistry = resolveSafely("io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry");
        Object retryRegistry = resolveSafely("io.github.resilience4j.retry.RetryRegistry");
        Object bulkheadRegistry = resolveSafely("io.github.resilience4j.bulkhead.BulkheadRegistry");
        Object rateLimiterRegistry = resolveSafely("io.github.resilience4j.ratelimiter.RateLimiterRegistry");
        ResilienceOperatorApplier resilienceOperatorApplier = resolveResilienceOperatorApplier(
                circuitBreakerRegistry, retryRegistry, bulkheadRegistry, rateLimiterRegistry);
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
        httpClient = applyHttpProtocol(httpClient, config, baseUrl);

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
                .filter(ReactiveClientInvocationHandler.requestUrlObservationFilter())
                .filter(correlationIdFilter(correlationIdConfig));

        if (authProvider != null) {
            configured = configured.filter(new OutboundAuthFilter(clientName, authProvider));
        }

        // Apply per-client customizations registered as Spring beans.
        // Customizers are applied in @Order / Ordered sequence after all built-in filters.
        final WebClient.Builder finalConfigured = configured;
        applicationContext.getBeanProvider(ReactiveHttpClientCustomizer.class)
                .orderedStream()
                .filter(customizer -> customizer.supports(clientName))
                .forEach(customizer -> {
                    if (log.isDebugEnabled()) {
                        log.debug("Applying ReactiveHttpClientCustomizer [{}] to client [{}] after built-in filters",
                                customizer.getClass().getName(), clientName);
                    }
                    customizer.customize(finalConfigured);
                });

        return configured.build();
    }

    static HttpClient applyHttpProtocol(HttpClient httpClient,
                                        ReactiveHttpClientProperties.ClientConfig config,
                                        String baseUrl) {
        if (!config.isHttp2Enabled()) {
            return httpClient;
        }
        boolean clearText = baseUrl != null
                && baseUrl.regionMatches(true, 0, "http://", 0, "http://".length());
        HttpProtocol protocol = clearText ? HttpProtocol.H2C : HttpProtocol.H2;
        return httpClient.protocol(protocol);
    }

    private void validateClientConfiguration(String clientName,
                                             ReactiveHttpClientProperties.ClientConfig config,
                                             ReactiveHttpClientProperties.NetworkConfig networkConfig) {
        ReactiveHttpClientProperties.ProxyConfig proxy = resolveProxy(config, networkConfig);
        if (proxy != null && proxy.getType() != ReactiveHttpClientProperties.ProxyConfig.Type.NONE) {
            boolean hasHost = StringUtils.hasText(proxy.getHost());
            boolean hasPort = proxy.getPort() > 0;
            if (hasHost && !hasPort) {
                throw new IllegalArgumentException(
                        "Proxy host is set but port is invalid (got " + proxy.getPort() + "). "
                                + "Set reactive.http.clients." + clientName + ".proxy.port "
                                + "(or reactive.http.network.proxy.port) to a valid port > 0.");
            }
            if (!hasHost && hasPort) {
                throw new IllegalArgumentException(
                        "Proxy port is set but host is blank. Set reactive.http.clients." + clientName
                                + ".proxy.host (or reactive.http.network.proxy.host), or remove the proxy port.");
            }
            if (StringUtils.hasText(proxy.getUsername()) != StringUtils.hasText(proxy.getPassword())) {
                throw new IllegalArgumentException(
                        "Proxy username and password must be configured together for client '" + clientName + "'.");
            }
        }

        ReactiveHttpClientProperties.TlsConfig tls = resolveTls(config, networkConfig);
        if (tls != null) {
            if (StringUtils.hasText(tls.getTrustStorePassword()) && !StringUtils.hasText(tls.getTrustStore())) {
                throw new IllegalArgumentException(
                        "TLS trust-store-password is set but trust-store is blank for client '" + clientName + "'.");
            }
            if (StringUtils.hasText(tls.getKeyStorePassword()) && !StringUtils.hasText(tls.getKeyStore())) {
                throw new IllegalArgumentException(
                        "TLS key-store-password is set but key-store is blank for client '" + clientName + "'.");
            }
        }

        ReactiveHttpClientProperties.ResilienceConfig resilience = config.getResilience();
        if (config.isRequestTimeoutMsConfigured()
                && resilience != null
                && resilience.isTimeoutMsConfigured()) {
            log.warn("Reactive HTTP client [{}] has both request-timeout-ms and deprecated resilience.timeout-ms configured. "
                    + "Using request-timeout-ms [{}] and ignoring resilience.timeout-ms [{}].",
                    clientName, config.getRequestTimeoutMs(), resilience.getTimeoutMs());
        }

        if (StringUtils.hasText(config.getAuthProvider())
                && config.getAuth() != null
                && StringUtils.hasText(config.getAuth().getType())) {
            log.warn("Reactive HTTP client [{}] has both auth-provider and auth.type configured. "
                    + "Using auth-provider bean [{}] and ignoring object-style auth [{}].",
                    clientName, config.getAuthProvider(), config.getAuth().getType());
        }
        validateDefaultHeaders(clientName, config.getDefaultHeaders());
        validateDefaultQueryParams(clientName, config.getDefaultQueryParams());
    }

    private void validateDefaultHeaders(String clientName, Map<String, String> defaultHeaders) {
        if (defaultHeaders == null || defaultHeaders.isEmpty()) {
            return;
        }
        defaultHeaders.forEach((name, value) -> {
            RequestArgumentResolver.validateHeaderName(name);
            if (value == null) {
                throw new IllegalArgumentException("Default header '" + name
                        + "' for client '" + clientName + "' must not be null.");
            }
            RequestArgumentResolver.validateHeaderValue(name, value);
            if (isSensitiveConfiguredKey(name)) {
                log.warn("Reactive HTTP client [{}] default header [{}] looks sensitive. "
                        + "The value will not be logged, but prefer an AuthProvider for rotating credentials.",
                        clientName, name);
            }
        });
    }

    private void validateDefaultQueryParams(String clientName, Map<String, List<String>> defaultQueryParams) {
        if (defaultQueryParams == null || defaultQueryParams.isEmpty()) {
            return;
        }
        defaultQueryParams.forEach((name, values) -> {
            validateQueryParamName(name);
            if (values == null || values.isEmpty()) {
                throw new IllegalArgumentException("Default query parameter '" + name
                        + "' for client '" + clientName + "' must define at least one value.");
            }
            values.forEach(value -> validateQueryParamValue(name, value));
            if (isSensitiveConfiguredKey(name)) {
                log.warn("Reactive HTTP client [{}] default query parameter [{}] looks sensitive. "
                        + "The value will not be logged, but prefer an AuthProvider for rotating credentials.",
                        clientName, name);
            }
        });
    }

    private static void validateQueryParamName(String name) {
        if (!StringUtils.hasText(name)) {
            throw new IllegalArgumentException("Default query parameter name must not be blank");
        }
        for (int i = 0; i < name.length(); i++) {
            char ch = name.charAt(i);
            if (Character.isISOControl(ch)) {
                throw new IllegalArgumentException("Invalid default query parameter name '" + name
                        + "': control characters are not allowed");
            }
        }
    }

    private static void validateQueryParamValue(String name, String value) {
        if (value == null) {
            throw new IllegalArgumentException("Default query parameter '" + name + "' value must not be null");
        }
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (Character.isISOControl(ch)) {
                throw new IllegalArgumentException("Invalid default query parameter value for '" + name
                        + "': control characters are not allowed");
            }
        }
    }

    private static boolean isSensitiveConfiguredKey(String name) {
        if (SensitiveHeaders.isSensitive(name)) {
            return true;
        }
        String normalized = name.toLowerCase(Locale.ROOT);
        return normalized.contains("authorization")
                || normalized.contains("password")
                || normalized.contains("secret")
                || normalized.contains("token")
                || normalized.contains("api-key")
                || normalized.contains("api_key");
    }

    private void logStartupConfiguration(String clientName,
                                         String baseUrl,
                                         String baseUrlSource,
                                         ReactiveHttpClientProperties.ClientConfig config,
                                         ReactiveHttpClientProperties.NetworkConfig networkConfig,
                                         ReactiveHttpClientProperties.ObservabilityConfig observabilityConfig) {
        if (!log.isDebugEnabled()) {
            return;
        }
        ReactiveHttpClientProperties.NetworkConfig resolvedNetworkConfig = networkConfig != null
                ? networkConfig
                : new ReactiveHttpClientProperties.NetworkConfig();
        ReactiveHttpClientProperties.ConnectionPoolConfig pool = resolveConnectionPool(config, resolvedNetworkConfig);
        ReactiveHttpClientProperties.ProxyConfig proxy = resolveProxy(config, resolvedNetworkConfig);
        ReactiveHttpClientProperties.TlsConfig tls = resolveTls(config, resolvedNetworkConfig);
        ReactiveHttpClientProperties.ResilienceConfig resilience = config.getResilience();
        boolean observabilityEnabled = observabilityConfig == null || observabilityConfig.isEnabled();

        log.debug("Reactive HTTP client [{}] startup configuration: baseUrl={} (source={}), protocol={}, poolSource={}, "
                        + "pool=maxConnections:{}, pendingAcquireTimeoutMs:{}, proxy={}, tls={}, auth={}, requestTimeout={}, resilience={}, "
                        + "observability={}, exchangeLogging={}, logPreset={}",
                clientName,
                baseUrl,
                baseUrlSource,
                config.isHttp2Enabled() ? "HTTP/2" : "HTTP/1.1",
                config.getPool() != null ? "client" : "global",
                pool.getMaxConnections(),
                pool.getPendingAcquireTimeoutMs(),
                proxySummary(proxy),
                tlsSummary(tls),
                authSummary(config),
                requestTimeoutSummary(config),
                resilienceSummary(resilience),
                observabilityEnabled ? "enabled" : "disabled",
                config.isExchangeLoggingEnabled() ? "enabled" : "disabled",
                config.getLogPreset().name().toLowerCase(Locale.ROOT).replace('_', '-'));
    }

    private static String proxySummary(ReactiveHttpClientProperties.ProxyConfig proxy) {
        if (proxy == null) {
            return "none";
        }
        if (proxy.getType() == ReactiveHttpClientProperties.ProxyConfig.Type.NONE) {
            return "disabled";
        }
        return "enabled(type=" + proxy.getType()
                + ", host=" + (StringUtils.hasText(proxy.getHost()) ? proxy.getHost() : "blank")
                + ", port=" + proxy.getPort()
                + ", credentials=" + (StringUtils.hasText(proxy.getUsername()) ? "[REDACTED]" : "none")
                + ")";
    }

    private static String tlsSummary(ReactiveHttpClientProperties.TlsConfig tls) {
        if (tls == null) {
            return "jdk-default";
        }
        List<String> details = new ArrayList<>();
        if (StringUtils.hasText(tls.getTrustStore())) {
            details.add("trustStore=configured");
        }
        if (StringUtils.hasText(tls.getKeyStore())) {
            details.add("keyStore=configured");
        }
        if (tls.isInsecureTrustAll()) {
            details.add("insecureTrustAll=true");
        }
        if (tls.getProtocols() != null && !tls.getProtocols().isEmpty()) {
            details.add("protocols=" + tls.getProtocols());
        }
        return details.isEmpty() ? "custom" : "custom(" + String.join(", ", details) + ")";
    }

    private static String authSummary(ReactiveHttpClientProperties.ClientConfig config) {
        if (StringUtils.hasText(config.getAuthProvider())) {
            if (config.getAuth() != null && StringUtils.hasText(config.getAuth().getType())) {
                return "bean(" + config.getAuthProvider() + ", objectAuthIgnored=" + config.getAuth().getType() + ")";
            }
            return "bean(" + config.getAuthProvider() + ")";
        }
        if (config.getAuth() != null && StringUtils.hasText(config.getAuth().getType())) {
            return "configured(" + config.getAuth().getType() + ")";
        }
        return "none";
    }

    private static String requestTimeoutSummary(ReactiveHttpClientProperties.ClientConfig config) {
        if (config.isRequestTimeoutMsConfigured()) {
            return config.getRequestTimeoutMs() > 0
                    ? "configured(" + config.getRequestTimeoutMs() + "ms)"
                    : "disabled(configured)";
        }
        ReactiveHttpClientProperties.ResilienceConfig resilience = config.getResilience();
        if (resilience != null && resilience.isTimeoutMsConfigured()) {
            return resilience.getTimeoutMs() > 0
                    ? "deprecated-alias(" + resilience.getTimeoutMs() + "ms)"
                    : "disabled(deprecated-alias)";
        }
        return "disabled";
    }

    private static String resilienceSummary(ReactiveHttpClientProperties.ResilienceConfig resilience) {
        if (resilience == null || !resilience.isEnabled()) {
            return "disabled";
        }
        return "enabled(retry=" + resilience.getRetry()
                + ", circuitBreaker=" + resilience.getCircuitBreaker()
                + ", bulkhead=" + resilience.getBulkhead()
                + ", rateLimiter=" + resilience.getRateLimiter()
                + ")";
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
            Object bulkheadRegistry,
            Object rateLimiterRegistry) {
        if (circuitBreakerRegistry == null && retryRegistry == null && bulkheadRegistry == null
                && rateLimiterRegistry == null) {
            return new NoopResilienceOperatorApplier();
        }
        try {
            return new Resilience4jOperatorApplier(
                    circuitBreakerRegistry, retryRegistry, bulkheadRegistry, rateLimiterRegistry);
        } catch (Throwable error) {
            log.warn("Resilience4j operator applier could not be initialized. Falling back to no-op resilience.",
                    error);
            return new NoopResilienceOperatorApplier();
        }
    }

    /**
     * Eagerly parses every method on {@code clientInterface}, then verifies that
     * any per-method {@code @Retry} / {@code @CircuitBreaker} / {@code @Bulkhead}
     * / {@code @RateLimiter}
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
            checkInstance(applier, ResilienceOperatorApplier.InstanceType.RATE_LIMITER,
                    meta.getRateLimiterInstanceName(), method, "@RateLimiter", missing);
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "Reactive HTTP client '" + clientName + "' references undefined Resilience4j instances:\n  - "
                            + String.join("\n  - ", missing)
                            + "\nDefine them under resilience4j.<retry|circuitbreaker|bulkhead|ratelimiter>.instances.* in application config.");
        }
    }

    /**
     * Eagerly validates {@code @ApiRef} usage so typos in API-map keys fail fast
     * at startup instead of throwing only when the method is first invoked.
     */
    private void validateApiRefMappings(Class<?> clientInterface,
                                        MethodMetadataCache metadataCache,
                                        ReactiveHttpClientProperties.ClientConfig clientConfig,
                                        String clientName) {
        for (Method method : clientInterface.getMethods()) {
            if (method.isSynthetic() || method.isDefault() || method.isBridge()) continue;
            MethodMetadata meta;
            try {
                meta = metadataCache.get(method);
            } catch (RuntimeException e) {
                // Methods that fail to parse (e.g. helper methods without HTTP verb)
                // are validated only when invoked; skip @ApiRef startup checks here.
                log.debug("Skipping @ApiRef startup validation for {}.{} due to metadata parse failure.",
                        method.getDeclaringClass().getSimpleName(), method.getName(), e);
                continue;
            }
            String apiRefName = meta.getApiRefName();
            if (!StringUtils.hasText(apiRefName)) {
                continue;
            }
            ReactiveHttpClientProperties.ApiConfig apiConfig = clientConfig.getApis() != null
                    ? clientConfig.getApis().get(apiRefName)
                    : null;
            String configPrefix = ApiRefValidationSupport.configPrefix(clientName, apiRefName);
            String apiRefContext = ApiRefValidationSupport.apiRefContext(method, apiRefName);
            validateApiRef(apiConfig, configPrefix, apiRefContext);
        }
    }

    static void validateApiRef(ReactiveHttpClientProperties.ApiConfig apiConfig,
                               String configPrefix,
                               String apiRefContext) {
        if (apiConfig == null) {
            throw new IllegalStateException(apiRefContext + " but " + configPrefix + " is not configured.");
        }
        if (!StringUtils.hasText(apiConfig.getMethod())) {
            throw new IllegalStateException(apiRefContext + " but " + configPrefix + ".method is blank.");
        }
        if (!StringUtils.hasText(apiConfig.getPath())) {
            throw new IllegalStateException(apiRefContext + " but " + configPrefix + ".path is blank.");
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
        if (config == null || !config.hasAuthConfigured()) {
            return null;
        }
        if (StringUtils.hasText(config.getAuthProvider())) {
            try {
                return applicationContext.getBean(config.getAuthProvider(), AuthProvider.class);
            } catch (NoSuchBeanDefinitionException ex) {
                throw new IllegalStateException(
                        "No AuthProvider bean named '" + config.getAuthProvider()
                                + "' configured for client '" + clientName + "'", ex);
            }
        }

        String type = config.getAuth().getType();
        AuthProviderFactory factory = applicationContext.getBeanProvider(AuthProviderFactory.class)
                .orderedStream()
                .filter(candidate -> candidate.supports(type))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No AuthProviderFactory supports auth type '" + type + "' for client '" + clientName + "'"));
        WebClient.Builder builder = applicationContext
                .getBeanProvider(WebClient.Builder.class)
                .getIfAvailable(WebClient::builder);
        return factory.create(clientName, config.getAuth(), builder);
    }
}
