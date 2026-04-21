package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.annotation.ReactiveHttpClient;
import io.github.huynhngochuyhoang.httpstarter.auth.AuthProvider;
import io.github.huynhngochuyhoang.httpstarter.auth.OutboundAuthFilter;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import io.github.huynhngochuyhoang.httpstarter.filter.CorrelationIdWebFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.lang.NonNull;
import org.springframework.util.StringUtils;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.resources.ConnectionProvider;
import reactor.netty.http.client.HttpClient;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Spring {@link FactoryBean} that creates a JDK dynamic proxy for a given
 * {@code @ReactiveHttpClient} interface.
 *
 * <p>Registered automatically by {@link io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientsRegistrar}.
 */
public class ReactiveHttpClientFactoryBean<T> implements FactoryBean<T>, ApplicationContextAware {

    private static final Logger log = LoggerFactory.getLogger(ReactiveHttpClientFactoryBean.class);
    private static final int DEFAULT_CODEC_MAX_IN_MEMORY_SIZE_MB = 2;
    private static final int MAX_CODEC_MAX_IN_MEMORY_SIZE_MB = Integer.MAX_VALUE / (1024 * 1024);

    private Class<T> type;
    private ApplicationContext applicationContext;

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
        WebClient webClient = buildWebClient(baseUrl, config, properties.getNetwork(), clientName, authProvider);

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
                                     String clientName,
                                     AuthProvider authProvider) {
        ReactiveHttpClientProperties.NetworkConfig resolvedNetworkConfig = networkConfig != null
                ? networkConfig
                : new ReactiveHttpClientProperties.NetworkConfig();
        ReactiveHttpClientProperties.ConnectionPoolConfig pool = resolvedNetworkConfig.getConnectionPool() != null
                ? resolvedNetworkConfig.getConnectionPool()
                : new ReactiveHttpClientProperties.ConnectionPoolConfig();
        ConnectionProvider connectionProvider = ConnectionProvider.builder("reactive-http-client-" + clientName)
                .maxConnections(Math.max(1, pool.getMaxConnections()))
                .pendingAcquireTimeout(Duration.ofMillis(Math.max(0, pool.getPendingAcquireTimeoutMs())))
                .build();

        HttpClient httpClient = HttpClient.create(connectionProvider)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, resolvedNetworkConfig.getConnectTimeoutMs())
                // Global default response timeout. Per-method @TimeoutMs is applied per request in invocation handler.
                .responseTimeout(resolvedNetworkConfig.getReadTimeoutMs() > 0
                        ? Duration.ofMillis(resolvedNetworkConfig.getReadTimeoutMs())
                        : null)
                .doOnConnected(connection -> {
                    if (resolvedNetworkConfig.getWriteTimeoutMs() > 0) {
                        connection.addHandlerLast(new WriteTimeoutHandler(
                                resolvedNetworkConfig.getWriteTimeoutMs(), TimeUnit.MILLISECONDS));
                    }
                })
                .compress(config.isCompressionEnabled());
        WebClient.Builder builder = applicationContext
                .getBeanProvider(WebClient.Builder.class)
                .getIfAvailable(WebClient::builder);

        WebClient.Builder configured = builder
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(resolveCodecMaxInMemorySizeBytes(config)))
                .filter(correlationIdFilter());

        if (authProvider != null) {
            configured = configured.filter(new OutboundAuthFilter(clientName, authProvider));
        }

        if (config.isLogBody()) {
            configured = configured.filter(loggingFilter());
        }

        return configured.build();
    }

    private int resolveCodecMaxInMemorySizeBytes(ReactiveHttpClientProperties.ClientConfig config) {
        int sizeMb = config.getCodecMaxInMemorySizeMb() > 0
                ? config.getCodecMaxInMemorySizeMb()
                : DEFAULT_CODEC_MAX_IN_MEMORY_SIZE_MB;
        if (sizeMb > MAX_CODEC_MAX_IN_MEMORY_SIZE_MB) {
            throw new IllegalArgumentException("reactive.http.clients.*.codec-max-in-memory-size-mb must be <= "
                    + MAX_CODEC_MAX_IN_MEMORY_SIZE_MB + " but was " + sizeMb);
        }
        long sizeBytes = sizeMb * 1024L * 1024L;
        return (int) sizeBytes;
    }

    /** Propagates X-Correlation-Id from Reactor context (set by CorrelationIdWebFilter) or MDC. */
    private ExchangeFilterFunction correlationIdFilter() {
        return CorrelationIdWebFilter.exchangeFilter();
    }

    /** Logs method, URL, status and latency. */
    private ExchangeFilterFunction loggingFilter() {
        return ExchangeFilterFunction.ofResponseProcessor(response -> {
            log.debug("Response status: {}", response.statusCode());
            return Mono.just(response);
        });
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
