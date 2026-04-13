package com.acme.httpstarter.core;

import com.acme.httpstarter.annotation.ReactiveHttpClient;
import com.acme.httpstarter.config.ReactiveHttpClientProperties;
import com.acme.httpstarter.observability.HttpClientObserver;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.lang.NonNull;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.lang.reflect.Proxy;
import java.util.concurrent.TimeUnit;

/**
 * Spring {@link FactoryBean} that creates a JDK dynamic proxy for a given
 * {@code @ReactiveHttpClient} interface.
 *
 * <p>Registered automatically by {@link com.acme.httpstarter.config.ReactiveHttpClientsRegistrar}.
 */
public class ReactiveHttpClientFactoryBean<T> implements FactoryBean<T>, ApplicationContextAware {

    private static final Logger log = LoggerFactory.getLogger(ReactiveHttpClientFactoryBean.class);

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
                            + "Set acme.http.clients." + clientName + ".base-url in application.yml "
                            + "or use @ReactiveHttpClient(name=\"" + clientName + "\", baseUrl=\"...\")");
        }

        WebClient webClient = buildWebClient(baseUrl, config);

        MethodMetadataCache metadataCache = applicationContext
                .getBeanProvider(MethodMetadataCache.class)
                .getIfAvailable(MethodMetadataCache::new);

        DefaultErrorDecoder errorDecoder = applicationContext
                .getBeanProvider(DefaultErrorDecoder.class)
                .getIfAvailable(DefaultErrorDecoder::new);

        Object circuitBreakerRegistry = resolveSafely("io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry");
        Object retryRegistry = resolveSafely("io.github.resilience4j.retry.RetryRegistry");
        Object bulkheadRegistry = resolveSafely("io.github.resilience4j.bulkhead.BulkheadRegistry");

        HttpClientObserver observer = applicationContext
                .getBeanProvider(HttpClientObserver.class)
                .getIfAvailable();

        ReactiveClientInvocationHandler handler = new ReactiveClientInvocationHandler(
                webClient,
                metadataCache,
                new RequestArgumentResolver(),
                errorDecoder,
                config,
                clientName,
                applicationContext,
                circuitBreakerRegistry,
                retryRegistry,
                bulkheadRegistry,
                observer,
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

    private WebClient buildWebClient(String baseUrl, ReactiveHttpClientProperties.ClientConfig config) {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, config.getConnectTimeoutMs())
                .doOnConnected(conn ->
                        conn.addHandlerLast(new ReadTimeoutHandler(config.getReadTimeoutMs(), TimeUnit.MILLISECONDS)));

        WebClient.Builder builder = applicationContext
                .getBeanProvider(WebClient.Builder.class)
                .getIfAvailable(WebClient::builder);

        WebClient.Builder configured = builder
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .filter(correlationIdFilter());

        if (config.isLogBody()) {
            configured = configured.filter(loggingFilter());
        }

        return configured.build();
    }

    /** Propagates X-Correlation-Id from MDC when available. */
    private ExchangeFilterFunction correlationIdFilter() {
        return ExchangeFilterFunction.ofRequestProcessor(request -> {
            String correlationId = org.slf4j.MDC.get("correlationId");
            if (StringUtils.hasText(correlationId)) {
                return Mono.just(
                        org.springframework.web.reactive.function.client.ClientRequest
                                .from(request)
                                .header("X-Correlation-Id", correlationId)
                                .build()
                );
            }
            return Mono.just(request);
        });
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
}
