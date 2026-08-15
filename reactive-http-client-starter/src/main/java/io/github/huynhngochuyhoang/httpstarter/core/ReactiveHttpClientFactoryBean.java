package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.annotation.ReactiveHttpClient;
import io.github.huynhngochuyhoang.httpstarter.auth.*;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import io.github.huynhngochuyhoang.httpstarter.filter.CorrelationIdWebFilter;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOption;
import io.netty.handler.codec.compression.DecompressionException;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.io.Resource;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.lang.NonNull;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.Connection;
import reactor.netty.http.HttpProtocol;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.lang.reflect.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Spring {@link FactoryBean} that creates a JDK dynamic proxy for a given
 * {@code @ReactiveHttpClient} interface.
 *
 * <p>Registered automatically by {@link io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientsRegistrar}.
 */
public class ReactiveHttpClientFactoryBean<T> implements FactoryBean<T>, ApplicationContextAware, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(ReactiveHttpClientFactoryBean.class);
    private static final Duration CONNECTION_DISPOSAL_TIMEOUT = Duration.ofSeconds(5);
    private static final int MAX_CODEC_MAX_IN_MEMORY_SIZE_MB = Integer.MAX_VALUE / (1024 * 1024);
    private static final Set<String> SUPPORTED_OUTBOUND_HTTP_METHODS = Set.of(
            "GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS");
    private static final Set<String> UNSAFE_APPLICATION_REQUEST_HEADERS = Set.of(
            HttpHeaders.CONTENT_LENGTH.toLowerCase(Locale.ROOT),
            HttpHeaders.TRANSFER_ENCODING.toLowerCase(Locale.ROOT),
            HttpHeaders.CONNECTION.toLowerCase(Locale.ROOT),
            HttpHeaders.EXPECT.toLowerCase(Locale.ROOT),
            HttpHeaders.HOST.toLowerCase(Locale.ROOT));

    private Class<T> type;
    private ApplicationContext applicationContext;
    private ConnectionProvider connectionProvider;
    private ConnectionProvider tokenServiceConnectionProvider;
    private final Set<Connection> ownedConnections = ConcurrentHashMap.newKeySet();
    private final Object ownedConnectionLifecycleMonitor = new Object();
    private boolean shuttingDown;
    private ProtocolAwareConnectionPoolMeterRegistrar connectionPoolMeterRegistrar;

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

        validateBaseUrl(clientName, baseUrl, annotationBaseUrl ? "annotation" : "property");
        validateClientConfiguration(clientName, config, properties.getNetwork());
        MethodMetadataCache metadataCache = applicationContext
                .getBeanProvider(MethodMetadataCache.class)
                .getIfAvailable(MethodMetadataCache::new);
        metadataCache.validateDeclarativeRequestParameters(type, clientName);
        metadataCache.validateDeclarativeUriTemplates(type, clientName, config.getApis());
        metadataCache.validateDeclarativeReturnTypes(type, clientName);

        AuthProvider authProvider = resolveAuthProvider(clientName, config);
        logStartupConfiguration(
                clientName,
                baseUrl,
                annotationBaseUrl ? "annotation" : "property",
                config,
                properties.getNetwork(),
                properties.getObservability());
        logMethodPolicyDiagnostics(
                type,
                metadataCache,
                config,
                clientName,
                baseUrl,
                annotationBaseUrl ? "annotation" : "property");
        WebClient webClient = buildWebClient(
                baseUrl,
                config,
                properties.getNetwork(),
                properties.getCorrelationId(),
                clientName,
                authProvider);

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
        logStartupSummary(type, clientName, config, metadataCache, resilienceOperatorApplier, properties.getObservability());
        ReactiveHttpClientJsonCodec jsonCodec = applicationContext.getBeanProvider(ReactiveHttpClientJsonCodec.class).getIfAvailable();
        validateStrictBodySigningContracts(type, metadataCache, config, authProvider, jsonCodec, clientName);

        if (config.getResilience() != null && config.getResilience().isEnabled()) {
            validatePerMethodResilienceInstances(type, metadataCache, resilienceOperatorApplier, clientName);
            validateStrictUnsafeRetryContracts(type, metadataCache, config, resilienceOperatorApplier, clientName);
            logMethodResilienceDiagnostics(type, metadataCache, config, resilienceOperatorApplier, clientName);
        }

        ReactiveClientInvocationHandler handler = ReactiveClientInvocationHandler.create(
                webClient,
                metadataCache,
                new RequestArgumentResolver(),
                errorDecoder,
                config,
                clientName,
                type,
                applicationContext,
                resilienceOperatorApplier,
                jsonCodec,
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
        disposeResources(CONNECTION_DISPOSAL_TIMEOUT);
        if (connectionPoolMeterRegistrar != null) {
            connectionPoolMeterRegistrar.close();
        }
    }

    private void disposeResources(Duration timeout) {
        List<Connection> connections;
        synchronized (ownedConnectionLifecycleMonitor) {
            shuttingDown = true;
            connections = List.copyOf(ownedConnections);
        }
        try {
            Mono.whenDelayError(
                            disposeConnectionProvider(connectionProvider, "business"),
                            disposeConnectionProvider(tokenServiceConnectionProvider, "OAuth2 token-service"),
                            disposeOwnedConnections(connections))
                    .block(timeout);
        } catch (RuntimeException error) {
            log.warn("Error while disposing resources for client [{}]",
                    type != null ? type.getSimpleName() : "?", error);
        } finally {
            ownedConnections.removeAll(connections);
        }
    }

    private Mono<Void> disposeConnectionProvider(ConnectionProvider provider, String owner) {
        if (provider == null) {
            return Mono.empty();
        }
        return Mono.defer(provider::disposeLater)
                .doOnError(error -> log.warn(
                        "Error while disposing {} ConnectionProvider for client [{}]",
                        owner, type != null ? type.getSimpleName() : "?", error));
    }

    private void trackOwnedConnection(Connection connection) {
        boolean closeImmediately;
        synchronized (ownedConnectionLifecycleMonitor) {
            closeImmediately = shuttingDown;
            if (!closeImmediately) {
                ownedConnections.add(connection);
                connection.onDispose(() -> ownedConnections.remove(connection));
            }
        }
        if (closeImmediately) {
            connection.channel().close();
        }
    }

    private Mono<Void> disposeOwnedConnections(List<Connection> connections) {
        if (connections.isEmpty()) {
            return Mono.empty();
        }
        return Mono.defer(() -> {
                    connections.forEach(connection -> connection.channel().close());
                    return Mono.whenDelayError(connections.stream().map(Connection::onDispose).toList());
                })
                .doOnError(error -> log.warn("Error while disposing active connections for client [{}]",
                        type != null ? type.getSimpleName() : "?", error));
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
            this.connectionPoolMeterRegistrar =
                    new ProtocolAwareConnectionPoolMeterRegistrar(poolName, config.isHttp2Enabled());
            providerBuilder.metrics(true, () -> this.connectionPoolMeterRegistrar);
        }
        // Store the provider on the instance field so destroy() can dispose it cleanly on context shutdown.
        this.connectionProvider = providerBuilder.build();

        // Retry is an explicit starter-level policy; do not let the transport replay before headers are sent.
        HttpClient httpClient = HttpClient.create(this.connectionProvider)
                .disableRetry(true)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, resolvedNetworkConfig.getConnectTimeoutMs())
                .doOnConnected(connection -> {
                    trackOwnedConnection(connection);
                    // Safety-net handlers: fire if a connection gets stuck in the pool beyond the configured limit.
                    connection.addHandlerLast(new ReadTimeoutHandler(
                            resolvedNetworkConfig.getNetworkReadTimeoutMs(), TimeUnit.MILLISECONDS));
                    connection.addHandlerLast(new WriteTimeoutHandler(
                            resolvedNetworkConfig.getNetworkWriteTimeoutMs(), TimeUnit.MILLISECONDS));
                    if (config.isCompressionEnabled()) {
                        connection.addHandlerLast(new ChannelInboundHandlerAdapter() {
                            @Override
                            public void exceptionCaught(ChannelHandlerContext context, Throwable error) {
                                if (isDecompressionFailure(error)) {
                                    context.fireExceptionCaught(error);
                                    context.executor().execute(context::close);
                                    return;
                                }
                                context.fireExceptionCaught(error);
                            }
                        });
                    }
                })
                .compress(config.isCompressionEnabled());
        if (config.isFollowRedirects()) {
            httpClient = httpClient.followRedirect(true);
        }
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

        finalConfigured.filter(outboundFramingHeaderFilter(config.isCompressionEnabled()));
        finalConfigured.filter(ReactiveClientInvocationHandler.finalRequestObservationFilter());
        return configured.build();
    }

    static ExchangeFilterFunction outboundFramingHeaderFilter() {
        return outboundFramingHeaderFilter(false);
    }

    static ExchangeFilterFunction outboundFramingHeaderFilter(boolean compressionEnabled) {
        return (request, next) -> {
            for (Map.Entry<String, List<String>> header : request.headers().headerSet()) {
                String headerName = header.getKey();
                if (UNSAFE_APPLICATION_REQUEST_HEADERS.contains(headerName.toLowerCase(Locale.ROOT))) {
                    return reactor.core.publisher.Mono.error(new IllegalArgumentException(
                            "Application-supplied outbound header '" + headerName + "' is not supported; "
                                    + "HTTP framing and authority headers are owned by the configured transport"));
                }
                if (compressionEnabled && HttpHeaders.ACCEPT_ENCODING.equalsIgnoreCase(headerName)) {
                    return reactor.core.publisher.Mono.error(new IllegalArgumentException(
                            "Application-supplied outbound header '" + headerName + "' is not supported when "
                                    + "compression-enabled=true; response compression negotiation and "
                                    + "decompression are owned by the configured transport"));
                }
            }
            return next.exchange(request);
        };
    }

    private static boolean isDecompressionFailure(Throwable error) {
        Throwable current = error;
        for (int depth = 0; current != null && depth < 16; depth++) {
            if (current instanceof DecompressionException) {
                return true;
            }
            Throwable cause = current.getCause();
            current = cause != current ? cause : null;
        }
        return false;
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

    private void validateBaseUrl(String clientName, String baseUrl, String source) {
        URI uri;
        try {
            uri = new URI(baseUrl);
        } catch (URISyntaxException ex) {
            throw invalidBaseUrl(clientName, baseUrl, source, ex);
        }
        String scheme = uri.getScheme();
        if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                || !StringUtils.hasText(uri.getHost())) {
            throw invalidBaseUrl(clientName, baseUrl, source, null);
        }
    }

    private IllegalArgumentException invalidBaseUrl(String clientName, String baseUrl, String source, Throwable cause) {
        String message = "Invalid baseUrl for reactive HTTP client [" + clientName + "] (source=" + source + "): "
                + baseUrl + ". Expected an absolute http(s) URL with a host.";
        return cause != null ? new IllegalArgumentException(message, cause) : new IllegalArgumentException(message);
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
                        + "pool=maxConnections:{}, pendingAcquireTimeoutMs:{}, proxy={}, tls={}, auth={}, tokenService={}, requestTimeout={}, logicalCallTimeout={}ms, resilience={}, "
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
                tokenServiceSummary(config),
                requestTimeoutSummary(config),
                config.getLogicalCallTimeoutMs(),
                resilienceSummary(resilience),
                observabilityEnabled ? "enabled" : "disabled",
                config.isExchangeLoggingEnabled() ? "enabled" : "disabled",
                config.getLogPreset().name().toLowerCase(Locale.ROOT).replace('_', '-'));
    }

    private void logStartupSummary(Class<?> clientInterface,
                                   String clientName,
                                   ReactiveHttpClientProperties.ClientConfig config,
                                   MethodMetadataCache metadataCache,
                                   ResilienceOperatorApplier resilienceOperatorApplier,
                                   ReactiveHttpClientProperties.ObservabilityConfig observabilityConfig) {
        if (!log.isDebugEnabled()) {
            return;
        }
        ReactiveHttpClientDiagnosticsProvider.ClientSummary summary = ReactiveHttpClientDiagnosticsProvider.clientSummary(
                clientInterface, clientName, config, metadataCache, resilienceOperatorApplier);
        boolean observabilityEnabled = observabilityConfig == null || observabilityConfig.isEnabled();

        log.debug("Reactive HTTP client [{}] startup summary: interface={}, endpoints={}, inheritedEndpoints={}, "
                        + "baseUrlSource={}, timeout={}, logicalCallTimeout={}ms, resilience={}, auth={}, redirects={}, observability={}",
                summary.clientName(),
                summary.clientInterface(),
                summary.endpointCount(),
                summary.inheritedEndpointCount(),
                summary.baseUrlSource(),
                diagnosticsTimeoutSummary(summary.timeout()),
                config.getLogicalCallTimeoutMs(),
                diagnosticsResilienceSummary(summary.resilience()),
                summary.authMode(),
                summary.followRedirects() ? "follow" : "manual",
                observabilityEnabled ? "enabled" : "disabled");
    }

    private static String diagnosticsTimeoutSummary(ReactiveHttpClientDiagnosticsProvider.TimeoutSummary timeout) {
        return timeout.source() + ":" + timeout.timeoutMs() + "ms";
    }

    private static String diagnosticsResilienceSummary(ReactiveHttpClientDiagnosticsProvider.ResilienceSummary resilience) {
        return "configured=" + resilience.configured()
                + ",retry=" + resilience.retry()
                + ",rateLimiter=" + resilience.rateLimiter()
                + ",circuitBreaker=" + resilience.circuitBreaker()
                + ",bulkhead=" + resilience.bulkhead();
    }

    private void logMethodPolicyDiagnostics(Class<?> clientInterface,
                                            MethodMetadataCache metadataCache,
                                            ReactiveHttpClientProperties.ClientConfig clientConfig,
                                            String clientName,
                                            String baseUrl,
                                            String baseUrlSource) {
        if (!log.isDebugEnabled()) {
            return;
        }
        for (Method method : clientInterface.getMethods()) {
            if (!isDeclarativeClientMethod(method)) continue;
            MethodMetadata meta = metadataCache.get(method);
            RequestPlan plan = RequestPlan.from(meta, clientInterface);
            EffectiveApi effectiveApi = diagnosticEffectiveApi(plan, clientConfig);
            boolean inherited = method.getDeclaringClass() != clientInterface;
            log.debug("Reactive HTTP client [{}] method policy: method=[{}#{}], declaredBy={}, "
                            + "concreteClient={}, inherited={}, apiRef={}, genericBindings={}, responseType={}, "
                            + "bodyType={}, httpMethod={}, pathTemplate={}, baseUrl={} (source={}), "
                            + "requestTimeout={}, logicalCallTimeout={}ms, redirectPolicy={}, retrySafety={}, bodyRepeatability={}",
                    clientName,
                    method.getDeclaringClass().getSimpleName(),
                    method.getName(),
                    method.getDeclaringClass().getName(),
                    clientInterface.getName(),
                    inherited,
                    StringUtils.hasText(plan.apiRefName()) ? plan.apiRefName() : "none",
                    EffectiveHttpClientContractExporter.genericBindings(clientInterface, method),
                    EffectiveHttpClientContractExporter.typeName(plan.responseType()),
                    EffectiveHttpClientContractExporter.typeName(plan.bodyType()),
                    effectiveApi.httpMethod(),
                    effectiveApi.pathTemplate(),
                    baseUrl,
                    baseUrlSource,
                    requestTimeoutSummary(plan, effectiveApi, clientConfig),
                    clientConfig.getLogicalCallTimeoutMs(),
                    clientConfig.isFollowRedirects() ? "follow" : "manual",
                    diagnosticRetrySafety(plan, effectiveApi.httpMethod(), clientConfig),
                    plan.bodyRepeatability());
        }
    }

    private static EffectiveApi diagnosticEffectiveApi(RequestPlan plan,
                                                       ReactiveHttpClientProperties.ClientConfig clientConfig) {
        if (!StringUtils.hasText(plan.apiRefName())) {
            return plan.staticEffectiveApi();
        }
        ReactiveHttpClientProperties.ApiConfig apiConfig = clientConfig.getApis() != null
                ? clientConfig.getApis().get(plan.apiRefName())
                : null;
        if (apiConfig == null) {
            return new EffectiveApi("unresolved", "unresolved", MethodMetadata.TIMEOUT_NOT_SET);
        }
        return new EffectiveApi(
                StringUtils.hasText(apiConfig.getMethod())
                        ? apiConfig.getMethod().trim().toUpperCase(Locale.ROOT)
                        : "unresolved",
                StringUtils.hasText(apiConfig.getPath()) ? apiConfig.getPath() : "unresolved",
                apiConfig.getTimeoutMs());
    }

    private static String requestTimeoutSummary(RequestPlan plan,
                                                EffectiveApi effectiveApi,
                                                ReactiveHttpClientProperties.ClientConfig config) {
        if (plan.timeoutMs() != MethodMetadata.TIMEOUT_NOT_SET) {
            return timeoutSummary("method @TimeoutMs", plan.timeoutMs());
        }
        if (effectiveApi.timeoutMs() != MethodMetadata.TIMEOUT_NOT_SET) {
            return timeoutSummary("@ApiRef timeout-ms", effectiveApi.timeoutMs());
        }
        if (config.isRequestTimeoutMsConfigured()) {
            return timeoutSummary("client request-timeout-ms", config.getRequestTimeoutMs());
        }
        ReactiveHttpClientProperties.ResilienceConfig resilience = config.getResilience();
        if (resilience != null && resilience.isTimeoutMsConfigured()) {
            return timeoutSummary("deprecated resilience.timeout-ms", resilience.getTimeoutMs());
        }
        return "disabled";
    }

    private static String timeoutSummary(String source, long timeoutMs) {
        return timeoutMs > 0 ? source + "(" + timeoutMs + "ms)" : "disabled(" + source + ")";
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

    private static String tokenServiceSummary(ReactiveHttpClientProperties.ClientConfig config) {
        if (StringUtils.hasText(config.getAuthProvider())
                || config.getAuth() == null
                || !OAuth2ClientCredentialsAuthProviderFactory.TYPE.equalsIgnoreCase(config.getAuth().getType())) {
            return "not-applicable";
        }
        ReactiveHttpClientProperties.OAuth2TokenServiceConfig policy =
                config.getAuth().getOauth2ClientCredentials().getTokenService();
        return "owned(connectTimeoutMs=" + policy.getConnectTimeoutMs()
                + ", requestTimeoutMs=" + policy.getRequestTimeoutMs()
                + ", maxConnections=" + policy.getMaxConnections()
                + ", pendingAcquireTimeoutMs=" + policy.getPendingAcquireTimeoutMs()
                + ", retryMaxAttempts=" + policy.getRetryMaxAttempts()
                + ", retryBackoffMs=" + policy.getRetryBackoffMs()
                + ", proxy=" + (policy.getProxy() != null ? "configured" : "direct")
                + ", tls=" + (policy.getTls() != null ? "configured" : "platform-default") + ")";
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
        return "configured(retry=" + resilience.getRetry()
                + ", retryMethods=" + resilience.getRetryMethods()
                + ", rateLimiter=" + resilience.getRateLimiter()
                + ", circuitBreaker=" + resilience.getCircuitBreaker()
                + ", bulkhead=" + resilience.getBulkhead()
                + ", operatorOrder=" + ReactiveClientInvocationHandler.RESILIENCE_OPERATOR_ORDER
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
            if (!isDeclarativeClientMethod(method)) continue;
            MethodMetadata meta = metadataCache.get(method);
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

    private void validateStrictBodySigningContracts(Class<?> clientInterface,
                                                    MethodMetadataCache metadataCache,
                                                    ReactiveHttpClientProperties.ClientConfig clientConfig,
                                                    AuthProvider authProvider,
                                                    ReactiveHttpClientJsonCodec jsonCodec,
                                                    String clientName) {
        if (!usesStrictBuiltInAwsSigV4(clientConfig, authProvider)) {
            return;
        }

        List<String> unsupportedMethods = new ArrayList<>();
        for (Method method : clientInterface.getMethods()) {
            if (!isDeclarativeClientMethod(method)) continue;
            MethodMetadata meta = metadataCache.get(method);
            RequestPlan plan = RequestPlan.from(meta, clientInterface);
            BodySigningContract contract = bodySigningContract(plan, clientConfig, jsonCodec);
            if (contract.supported()) {
                continue;
            }
            unsupportedMethods.add("clientInterface=" + clientInterface.getName()
                    + ", method=" + methodSignature(method)
                    + ", endpointSource=" + diagnosticEndpointSource(plan)
                    + ", httpMethod=" + diagnosticHttpMethod(meta, clientConfig)
                    + ", pathTemplate=" + plan.pathTemplate()
                    + ", bodyShape=" + contract.bodyShape()
                    + ", auth=aws-sigv4"
                    + ", reason=" + contract.reason()
                    + ", remediation=use an empty, byte[], charset-stable String, or concrete JSON body with a "
                    + "startup-provable JSON Content-Type; otherwise select a custom AuthProvider and leave built-in "
                    + "strict body-signing validation disabled");
        }

        if (!unsupportedMethods.isEmpty()) {
            throw new IllegalStateException("Reactive HTTP client [" + clientName + "] failed strict body-signing validation "
                    + "(reactive.http.clients." + clientName
                    + ".auth.aws-sig-v4.strict-body-signing-validation=true). "
                    + "Built-in AWS SigV4 body signing supports empty, byte[], String, and concrete JSON object bodies "
                    + "only when stable raw bytes and a JSON-compatible Content-Type can be proven at startup. "
                    + "Publisher, Java stream, resource, multipart, Object or erased generic bodies, and dynamic "
                    + "Content-Type values require a custom AuthProvider or strict validation disabled.\n  - "
                    + String.join("\n  - ", unsupportedMethods));
        }
    }

    private static boolean usesStrictBuiltInAwsSigV4(ReactiveHttpClientProperties.ClientConfig clientConfig,
                                                     AuthProvider authProvider) {
        if (clientConfig == null || StringUtils.hasText(clientConfig.getAuthProvider())) {
            return false;
        }
        ReactiveHttpClientProperties.AuthConfig auth = clientConfig.getAuth();
        return auth != null
                && AwsSigV4AuthProviderFactory.TYPE.equalsIgnoreCase(auth.getType())
                && auth.getAwsSigV4() != null
                && auth.getAwsSigV4().isStrictBodySigningValidation()
                && authProvider instanceof AwsSigV4AuthProvider;
    }

    private static BodySigningContract bodySigningContract(RequestPlan plan,
                                                           ReactiveHttpClientProperties.ClientConfig clientConfig,
                                                           ReactiveHttpClientJsonCodec jsonCodec) {
        if (plan.multipart()) {
            return BodySigningContract.unsupported("multipart",
                    "multipart bodies do not expose stable raw bytes for built-in SigV4 signing");
        }
        Type bodyType = plan.bodyType();
        if (bodyType == null) {
            return BodySigningContract.supported("empty");
        }
        Class<?> rawType = rawClass(bodyType);
        if (rawType == null) {
            return BodySigningContract.unsupported("unknown(" + bodyType.getTypeName() + ")",
                    "generic body type cannot be proven to materialize stable raw bytes");
        }
        String typeName = rawType.getTypeName();
        if (Publisher.class.isAssignableFrom(rawType)) {
            return BodySigningContract.unsupported("publisher(" + typeName + ")",
                    "Publisher bodies are non-repeatable and are not consumed for built-in SigV4 signing");
        }
        if (DataBuffer.class.isAssignableFrom(rawType)) {
            return BodySigningContract.unsupported("data-buffer(" + typeName + ")",
                    "DataBuffer bodies are streaming buffers and are not consumed for built-in SigV4 signing");
        }
        if (Resource.class.isAssignableFrom(rawType)) {
            return BodySigningContract.unsupported("resource(" + typeName + ")",
                    "Resource bodies are application-owned streams and do not expose stable raw bytes");
        }
        if (RequestPlan.isApplicationOwnedStreamBody(rawType)) {
            return BodySigningContract.unsupported("stream(" + typeName + ")",
                    "Stream body types are application-owned and do not expose stable raw bytes");
        }
        if (Object.class.equals(rawType)) {
            return BodySigningContract.unsupported("unknown(" + typeName + ")",
                    "Object or erased generic bodies cannot prove stable raw bytes at startup");
        }
        if (byte[].class.equals(rawType)) {
            return BodySigningContract.supported("byte[]");
        }
        if (String.class.equals(rawType)) {
            return BodySigningContract.supported("String");
        }
        BodySigningContract contentTypeContract = jsonContentTypeContract(plan, clientConfig, typeName);
        if (!contentTypeContract.supported()) {
            return contentTypeContract;
        }
        if (jsonCodec == null) {
            return BodySigningContract.unsupported("json(" + typeName + ")",
                    "JSON body signing requires a ReactiveHttpClientJsonCodec bean to materialize raw bytes");
        }
        return BodySigningContract.supported("json(" + typeName + ")");
    }

    private static BodySigningContract jsonContentTypeContract(RequestPlan plan,
                                                              ReactiveHttpClientProperties.ClientConfig clientConfig,
                                                              String typeName) {
        if (hasDynamicContentTypeHeader(plan)) {
            return BodySigningContract.unsupported("json(" + typeName + ")",
                    "Content-Type is supplied dynamically and cannot be proven JSON-compatible at startup");
        }
        String defaultContentType = defaultHeaderValue(clientConfig, HttpHeaders.CONTENT_TYPE);
        if (!StringUtils.hasText(defaultContentType) || isJsonCompatibleContentType(defaultContentType)) {
            return BodySigningContract.supported("json(" + typeName + ")");
        }
        return BodySigningContract.unsupported("json(" + typeName + ")",
                "configured default Content-Type [" + defaultContentType + "] is not JSON-compatible");
    }

    private static boolean hasDynamicContentTypeHeader(RequestPlan plan) {
        return !plan.headerMapParams().isEmpty()
                || plan.headerParams().stream()
                .map(RequestPlan.NamedArgumentBinding::name)
                .anyMatch(HttpHeaders.CONTENT_TYPE::equalsIgnoreCase);
    }

    private static String defaultHeaderValue(ReactiveHttpClientProperties.ClientConfig clientConfig, String headerName) {
        if (clientConfig == null || clientConfig.getDefaultHeaders() == null) {
            return null;
        }
        return clientConfig.getDefaultHeaders().entrySet().stream()
                .filter(entry -> headerName.equalsIgnoreCase(entry.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    private static boolean isJsonCompatibleContentType(String contentType) {
        try {
            MediaType mediaType = MediaType.parseMediaType(contentType);
            return MediaType.APPLICATION_JSON.isCompatibleWith(mediaType)
                    || mediaType.getSubtype().toLowerCase(Locale.ROOT).endsWith("+json");
        } catch (Exception ignored) {
            return false;
        }
    }

    private static Class<?> rawClass(Type type) {
        if (type instanceof Class<?> clazz) {
            return clazz;
        }
        if (type instanceof ParameterizedType parameterizedType
                && parameterizedType.getRawType() instanceof Class<?> clazz) {
            return clazz;
        }
        return null;
    }

    private record BodySigningContract(boolean supported, String bodyShape, String reason) {
        static BodySigningContract supported(String bodyShape) {
            return new BodySigningContract(true, bodyShape, null);
        }

        static BodySigningContract unsupported(String bodyShape, String reason) {
            return new BodySigningContract(false, bodyShape, reason);
        }
    }

    private void validateStrictUnsafeRetryContracts(Class<?> clientInterface,
                                                    MethodMetadataCache metadataCache,
                                                    ReactiveHttpClientProperties.ClientConfig clientConfig,
                                                    ResilienceOperatorApplier resilienceOperatorApplier,
                                                    String clientName) {
        ReactiveHttpClientProperties.ResilienceConfig resilience = clientConfig.getResilience();
        if (resilience == null
                || !resilience.isEnabled()
                || !resilience.isStrictUnsafeRetryValidation()
                || !resilienceOperatorApplier.isOperatorAvailable(ResilienceOperatorApplier.InstanceType.RETRY)) {
            return;
        }

        List<String> unsafeMethods = new ArrayList<>();
        for (Method method : clientInterface.getMethods()) {
            if (!isDeclarativeClientMethod(method)) continue;
            MethodMetadata meta = metadataCache.get(method);
            RequestPlan plan = RequestPlan.from(meta, clientInterface);
            String httpMethod = diagnosticHttpMethod(meta, clientConfig);
            if (!isRetryMethodEnabled(resilience, httpMethod)) {
                continue;
            }
            String retryInstance = resolveResilienceInstanceName(plan.retryInstanceName(), resilience.getRetry());
            if (!resilienceOperatorApplier.canRetryMoreThanOnce(retryInstance)) {
                continue;
            }
            if (startupRetrySafety(plan, httpMethod, clientConfig) != RetrySafetyClassification.UNSAFE_RETRY) {
                continue;
            }
            unsafeMethods.add("clientInterface=" + clientInterface.getName()
                    + ", method=" + methodSignature(method)
                    + ", endpointSource=" + diagnosticEndpointSource(plan)
                    + ", httpMethod=" + httpMethod
                    + ", retry=" + retryInstance
                    + ", retrySource=" + (StringUtils.hasText(plan.retryInstanceName())
                    ? "method-level @Retry" : "client resilience.retry")
                    + ", retryMethods=" + resilience.getRetryMethods()
                    + strictRetryDiagnostic(plan, clientConfig));
        }

        if (!unsafeMethods.isEmpty()) {
            throw new IllegalStateException("Reactive HTTP client [" + clientName + "] failed strict unsafe retry validation "
                    + "(reactive.http.clients." + clientName
                    + ".resilience.strict-unsafe-retry-validation=true). "
                    + "Retryable unsafe methods must use an idempotent HTTP method, a configured default "
                    + "Idempotency-Key header, or method-level @IdempotencyKey generation. "
                    + "Runtime-provided idempotency keys from parameters, header maps, or Reactor context "
                    + "cannot be proven at startup; keep strict validation disabled for those dynamic contracts.\n  - "
                    + String.join("\n  - ", unsafeMethods));
        }
    }

    private static RetrySafetyClassification startupRetrySafety(RequestPlan plan,
                                                                String httpMethod,
                                                                ReactiveHttpClientProperties.ClientConfig clientConfig) {
        if (ReactiveClientInvocationHandler.isSafeRetryMethod(httpMethod)
                || plan.retrySafety() == RetrySafetyClassification.SAFE_METHOD) {
            return RetrySafetyClassification.SAFE_METHOD;
        }
        if (StringUtils.hasText(plan.generatedIdempotencyKeyHeader())
                || hasStartupProvableDefaultIdempotencyKeyHeader(plan, clientConfig)) {
            return RetrySafetyClassification.EXPLICIT_IDEMPOTENCY_KEY;
        }
        return RetrySafetyClassification.UNSAFE_RETRY;
    }

    private static boolean hasStartupProvableDefaultIdempotencyKeyHeader(RequestPlan plan,
                                                                        ReactiveHttpClientProperties.ClientConfig clientConfig) {
        return hasDefaultIdempotencyKeyHeaderValue(clientConfig)
                && !canOverrideDefaultIdempotencyKeyHeader(plan);
    }

    private static boolean canOverrideDefaultIdempotencyKeyHeader(RequestPlan plan) {
        return !plan.headerMapParams().isEmpty()
                || plan.headerParams().stream()
                .map(RequestPlan.NamedArgumentBinding::name)
                .anyMatch(name -> "Idempotency-Key".equalsIgnoreCase(name))
                || plan.idempotencyKeyParams().stream()
                .map(RequestPlan.NamedArgumentBinding::name)
                .anyMatch(name -> "Idempotency-Key".equalsIgnoreCase(name));
    }

    private static String strictRetryDiagnostic(RequestPlan plan,
                                                ReactiveHttpClientProperties.ClientConfig clientConfig) {
        boolean hasDefault = hasDefaultIdempotencyKeyHeaderValue(clientConfig);
        boolean hasHeaderMap = !plan.headerMapParams().isEmpty();
        boolean hasDynamicKeyParameter = plan.headerParams().stream()
                .map(RequestPlan.NamedArgumentBinding::name)
                .anyMatch(name -> "Idempotency-Key".equalsIgnoreCase(name))
                || plan.idempotencyKeyParams().stream()
                .map(RequestPlan.NamedArgumentBinding::name)
                .anyMatch(name -> "Idempotency-Key".equalsIgnoreCase(name));
        if (hasDefault && (hasHeaderMap || hasDynamicKeyParameter)) {
            String source = hasHeaderMap ? "a dynamic header map" : "a dynamic method parameter";
            return ", reason=the configured default Idempotency-Key can be overridden by " + source
                    + ", remediation=remove that dynamic Idempotency-Key override or use method-level "
                    + "@IdempotencyKey generation";
        }
        if (hasHeaderMap || hasDynamicKeyParameter) {
            String source = hasHeaderMap ? "header maps" : "method parameters";
            return ", reason=Idempotency-Key values from " + source + " can be absent at runtime"
                    + ", remediation=use method-level @IdempotencyKey generation, a non-overrideable client default, "
                    + "or keep strict validation disabled for this runtime-provided contract";
        }
        return ", reason=no startup-provable Idempotency-Key contract is declared"
                + ", remediation=use an idempotent HTTP method, method-level @IdempotencyKey generation, or a "
                + "non-overrideable client default Idempotency-Key";
    }

    private static String diagnosticEndpointSource(RequestPlan plan) {
        return StringUtils.hasText(plan.apiRefName())
                ? "@ApiRef(" + plan.apiRefName() + ")"
                : "method annotation";
    }

    private static String methodSignature(Method method) {
        String parameters = Arrays.stream(method.getParameterTypes())
                .map(type -> type.getCanonicalName() != null ? type.getCanonicalName() : type.getName())
                .collect(java.util.stream.Collectors.joining(","));
        return method.getDeclaringClass().getName() + "#" + method.getName() + "(" + parameters + ")";
    }

    private void logMethodResilienceDiagnostics(Class<?> clientInterface,
                                                MethodMetadataCache metadataCache,
                                                ReactiveHttpClientProperties.ClientConfig clientConfig,
                                                ResilienceOperatorApplier resilienceOperatorApplier,
                                                String clientName) {
        if (!log.isDebugEnabled()) {
            return;
        }
        ReactiveHttpClientProperties.ResilienceConfig resilience = clientConfig.getResilience();
        if (resilience == null || !resilience.isEnabled()) {
            return;
        }
        for (Method method : clientInterface.getMethods()) {
            if (!isDeclarativeClientMethod(method)) continue;
            MethodMetadata meta = metadataCache.get(method);
            RequestPlan plan = RequestPlan.from(meta, clientInterface);
            String httpMethod = diagnosticHttpMethod(meta, clientConfig);
            boolean retryConfiguredForMethod = isRetryMethodEnabled(resilience, httpMethod);
            String retryInstance = retryConfiguredForMethod
                    ? operatorDiagnostic(resilienceOperatorApplier,
                    ResilienceOperatorApplier.InstanceType.RETRY,
                    plan.retryInstanceName(),
                    resilience.getRetry())
                    : "disabled";
            String rateLimiterInstance = operatorDiagnostic(resilienceOperatorApplier,
                    ResilienceOperatorApplier.InstanceType.RATE_LIMITER,
                    plan.rateLimiterInstanceName(), resilience.getRateLimiter());
            String circuitBreakerInstance = operatorDiagnostic(resilienceOperatorApplier,
                    ResilienceOperatorApplier.InstanceType.CIRCUIT_BREAKER,
                    plan.circuitBreakerInstanceName(), resilience.getCircuitBreaker());
            String bulkheadInstance = operatorDiagnostic(resilienceOperatorApplier,
                    ResilienceOperatorApplier.InstanceType.BULKHEAD,
                    plan.bulkheadInstanceName(), resilience.getBulkhead());
            log.debug("Reactive HTTP client [{}] method [{}#{}] resilience: httpMethod={}, retry={}, "
                            + "rateLimiter={}, circuitBreaker={}, bulkhead={}, retrySafety={}, bodyRepeatability={}, "
                            + "operatorOrder={}, subscriptionOrder={}",
                    clientName,
                    method.getDeclaringClass().getSimpleName(),
                    method.getName(),
                    httpMethod != null ? httpMethod : "unresolved",
                    retryInstance,
                    rateLimiterInstance,
                    circuitBreakerInstance,
                    bulkheadInstance,
                    diagnosticRetrySafety(plan, httpMethod, clientConfig),
                    plan.bodyRepeatability(),
                    ReactiveClientInvocationHandler.RESILIENCE_OPERATOR_ORDER,
                    ReactiveClientInvocationHandler.RESILIENCE_SUBSCRIPTION_ORDER);
        }
    }

    private static String diagnosticHttpMethod(MethodMetadata meta, ReactiveHttpClientProperties.ClientConfig clientConfig) {
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

    private static boolean isRetryMethodEnabled(ReactiveHttpClientProperties.ResilienceConfig resilience, String httpMethod) {
        return httpMethod != null
                && resilience.getRetryMethods() != null
                && resilience.getRetryMethods().contains(httpMethod.toUpperCase(Locale.ROOT));
    }

    private static RetrySafetyClassification diagnosticRetrySafety(RequestPlan plan,
                                                                    String httpMethod,
                                                                    ReactiveHttpClientProperties.ClientConfig clientConfig) {
        if (ReactiveClientInvocationHandler.isSafeRetryMethod(httpMethod)
                || plan.retrySafety() == RetrySafetyClassification.SAFE_METHOD) {
            return RetrySafetyClassification.SAFE_METHOD;
        }
        if (hasStartupProvableDefaultIdempotencyKeyHeader(plan, clientConfig)) {
            return RetrySafetyClassification.EXPLICIT_IDEMPOTENCY_KEY;
        }
        return plan.retrySafety();
    }

    private static boolean hasDefaultIdempotencyKeyHeaderValue(ReactiveHttpClientProperties.ClientConfig clientConfig) {
        if (clientConfig.getDefaultHeaders() == null) {
            return false;
        }
        return clientConfig.getDefaultHeaders().entrySet().stream()
                .anyMatch(entry -> "Idempotency-Key".equalsIgnoreCase(entry.getKey())
                        && StringUtils.hasText(entry.getValue()));
    }

    private static String operatorDiagnostic(ResilienceOperatorApplier resilienceOperatorApplier,
                                             ResilienceOperatorApplier.InstanceType type,
                                             String methodLevel,
                                             String clientLevel) {
        if (!resilienceOperatorApplier.isOperatorAvailable(type)) {
            return "unavailable";
        }
        return resolveResilienceInstanceName(methodLevel, clientLevel);
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

    static void validateApiRef(ReactiveHttpClientProperties.ApiConfig apiConfig,
                               String configPrefix,
                               String apiRefContext) {
        if (apiConfig == null) {
            throw new IllegalStateException(apiRefContext + " but " + configPrefix + " is not configured.");
        }
        if (!StringUtils.hasText(apiConfig.getMethod())) {
            throw new IllegalStateException(apiRefContext + " but " + configPrefix + ".method is blank.");
        }
        String method = apiConfig.getMethod().trim().toUpperCase(Locale.ROOT);
        if (!SUPPORTED_OUTBOUND_HTTP_METHODS.contains(method)) {
            throw new IllegalStateException(apiRefContext + " but " + configPrefix + ".method ["
                    + apiConfig.getMethod() + "] is not supported. Supported methods: "
                    + SUPPORTED_OUTBOUND_HTTP_METHODS);
        }
        if (!StringUtils.hasText(apiConfig.getPath())) {
            throw new IllegalStateException(apiRefContext + " but " + configPrefix + ".path is blank.");
        }
    }

    private static Set<String> pathVarNames(MethodMetadata meta) {
        return pathVarNames(meta.getRequestPlan() != null ? meta.getRequestPlan() : RequestPlan.from(meta));
    }

    static Set<String> pathVarNames(RequestPlan plan) {
        Set<String> names = new LinkedHashSet<>();
        for (RequestPlan.NamedArgumentBinding binding : plan.pathVars()) {
            if (!names.add(binding.name())) {
                throw new IllegalStateException("Method " + plan.method()
                        + " declares duplicate @PathVar(\"" + binding.name() + "\") bindings.");
            }
        }
        return names;
    }

    static void validatePathTemplate(String pathTemplate, Set<String> declaredPathVars, String context) {
        DeclarativeRequestUri.validate(pathTemplate, declaredPathVars, context);
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
        AuthProviderFactory factory = authProviderFactories()
                .filter(candidate -> candidate.supports(type))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No AuthProviderFactory supports auth type '" + type + "' for client '" + clientName + "'"));
        WebClient.Builder builder;
        if (factory instanceof OAuth2ClientCredentialsAuthProviderFactory) {
            builder = buildOAuth2TokenServiceWebClientBuilder(clientName,
                    config.getAuth().getOauth2ClientCredentials().getTokenService());
        } else {
            builder = applicationContext
                    .getBeanProvider(WebClient.Builder.class)
                    .getIfAvailable(WebClient::builder);
        }
        return factory.create(clientName, config.getAuth(), builder);
    }

    private java.util.stream.Stream<AuthProviderFactory> authProviderFactories() {
        if (!(applicationContext.getAutowireCapableBeanFactory()
                instanceof ConfigurableListableBeanFactory beanFactory)) {
            return applicationContext.getBeanProvider(AuthProviderFactory.class).orderedStream();
        }
        if (!hasConfigurableBeanFactoryHierarchy(beanFactory)) {
            return applicationContext.getBeanProvider(AuthProviderFactory.class).orderedStream();
        }

        List<AuthProviderFactoryCandidates.Candidate> candidates = new ArrayList<>();
        collectAuthProviderFactories(beanFactory, beanFactory, Set.of(), candidates);
        AuthProviderFactoryCandidates.sort(candidates, beanFactory);
        return candidates.stream().map(AuthProviderFactoryCandidates.Candidate::value);
    }

    private boolean hasConfigurableBeanFactoryHierarchy(ConfigurableListableBeanFactory factory) {
        org.springframework.beans.factory.BeanFactory parent = factory.getParentBeanFactory();
        if (parent == null) {
            return true;
        }
        return parent instanceof ConfigurableListableBeanFactory parentFactory
                && hasConfigurableBeanFactoryHierarchy(parentFactory);
    }

    private void collectAuthProviderFactories(
            ConfigurableListableBeanFactory rootFactory,
            ConfigurableListableBeanFactory factory,
            Set<String> shadowedBeanNames,
            List<AuthProviderFactoryCandidates.Candidate> candidates) {
        for (String beanName : factory.getBeanNamesForType(AuthProviderFactory.class, true, true)) {
            if (shadowedBeanNames.contains(beanName)
                    || !AuthProviderFactoryCandidates.isAutowireCandidate(rootFactory, beanName)) {
                continue;
            }
            candidates.add(new AuthProviderFactoryCandidates.Candidate(
                    factory, beanName, factory.getBean(beanName, AuthProviderFactory.class)));
        }

        org.springframework.beans.factory.BeanFactory parent = factory.getParentBeanFactory();
        if (parent instanceof ConfigurableListableBeanFactory parentFactory) {
            collectAuthProviderFactories(
                    rootFactory,
                    parentFactory,
                    AuthProviderFactoryCandidates.withLocalBeanNames(factory, shadowedBeanNames),
                    candidates);
        }
    }

    private WebClient.Builder buildOAuth2TokenServiceWebClientBuilder(
            String clientName,
            ReactiveHttpClientProperties.OAuth2TokenServiceConfig tokenService) {
        ReactiveHttpClientProperties.OAuth2TokenServiceConfig policy = tokenService != null
                ? tokenService
                : new ReactiveHttpClientProperties.OAuth2TokenServiceConfig();
        validateTokenServiceTransport(clientName, policy);
        String poolName = "reactive-http-client-" + clientName + "-oauth2-token-service"
                + (type != null ? "-" + type.getName() : "");
        this.tokenServiceConnectionProvider = ConnectionProvider.builder(poolName)
                .maxConnections(policy.getMaxConnections())
                .pendingAcquireTimeout(Duration.ofMillis(policy.getPendingAcquireTimeoutMs()))
                .build();
        HttpClient tokenHttpClient = HttpClient.create(this.tokenServiceConnectionProvider)
                .disableRetry(true)
                .doOnConnected(this::trackOwnedConnection)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, policy.getConnectTimeoutMs());

        ReactiveHttpClientProperties.ProxyConfig proxy = policy.getProxy();
        if (proxy != null && proxy.getType() != ReactiveHttpClientProperties.ProxyConfig.Type.NONE
                && StringUtils.hasText(proxy.getHost())) {
            tokenHttpClient = HttpProxyApplier.apply(tokenHttpClient, proxy);
        }
        if (policy.getTls() != null) {
            tokenHttpClient = TlsContextApplier.apply(
                    tokenHttpClient, policy.getTls(), clientName + " OAuth2 token service");
        }
        return WebClient.builder().clientConnector(new ReactorClientHttpConnector(tokenHttpClient));
    }

    private static void validateTokenServiceTransport(
            String clientName,
            ReactiveHttpClientProperties.OAuth2TokenServiceConfig policy) {
        String owner = "OAuth2 token service for client '" + clientName + "'";
        ReactiveHttpClientProperties.ProxyConfig proxy = policy.getProxy();
        if (proxy != null && proxy.getType() != ReactiveHttpClientProperties.ProxyConfig.Type.NONE) {
            boolean hasHost = StringUtils.hasText(proxy.getHost());
            boolean hasPort = proxy.getPort() > 0;
            if (hasHost != hasPort) {
                throw new IllegalArgumentException("Proxy host and a valid port must be configured together for "
                        + owner + ".");
            }
            if (StringUtils.hasText(proxy.getUsername()) != StringUtils.hasText(proxy.getPassword())) {
                throw new IllegalArgumentException("Proxy username and password must be configured together for "
                        + owner + ".");
            }
        }
        ReactiveHttpClientProperties.TlsConfig tls = policy.getTls();
        if (tls != null) {
            if (StringUtils.hasText(tls.getTrustStorePassword()) && !StringUtils.hasText(tls.getTrustStore())) {
                throw new IllegalArgumentException(
                        "TLS trust-store-password is set but trust-store is blank for " + owner + ".");
            }
            if (StringUtils.hasText(tls.getKeyStorePassword()) && !StringUtils.hasText(tls.getKeyStore())) {
                throw new IllegalArgumentException(
                        "TLS key-store-password is set but key-store is blank for " + owner + ".");
            }
        }
    }
}
