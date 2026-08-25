package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.auth.AuthProvider;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.scheduler.Schedulers;

import java.util.function.LongSupplier;

/**
 * Bridge used by the test-helper artifact to install deterministic cache time
 * without exposing the starter's cache implementation.
 *
 * @hidden
 */
public final class MockResponseCacheSupport {

    private MockResponseCacheSupport() {
    }

    public static Session create(
            WebClient webClient,
            MethodMetadataCache metadataCache,
            ReactiveHttpClientProperties.ClientConfig clientConfig,
            String clientName,
            Class<?> clientInterface,
            ApplicationContext applicationContext,
            ResilienceOperatorApplier resilienceOperatorApplier,
            ReactiveHttpClientJsonCodec jsonCodec,
            ReactiveHttpClientProperties.ObservabilityConfig observabilityConfig,
            AuthProvider authProvider,
            String baseUrl,
            LongSupplier ticker) {
        ReactiveHttpClientFactoryBean.validateEffectiveResilienceContracts(
                clientInterface, metadataCache, clientConfig, resilienceOperatorApplier, clientName);
        LocalResponseCacheManager manager = LocalResponseCacheManager.createForClient(
                clientInterface,
                clientName,
                metadataCache,
                clientConfig,
                applicationContext.getClassLoader(),
                observabilityConfig,
                null,
                ticker,
                Schedulers.parallel());
        ReactiveClientInvocationHandler handler = new ReactiveClientInvocationHandler(
                webClient,
                metadataCache,
                new RequestArgumentResolver(),
                new DefaultErrorDecoder(),
                clientConfig,
                clientName,
                clientInterface,
                applicationContext,
                resilienceOperatorApplier,
                jsonCodec,
                observabilityConfig,
                manager,
                authProvider,
                baseUrl);
        return new Session(handler, new Control(manager));
    }

    public record Session(ReactiveClientInvocationHandler handler, Control control) {
    }

    public static final class Control implements AutoCloseable {
        private final LocalResponseCacheManager manager;

        private Control(LocalResponseCacheManager manager) {
            this.manager = manager;
        }

        public long entryCount() {
            return manager.snapshot().currentSize();
        }

        public void evictAll() {
            manager.evictAllForTesting();
        }

        @Override
        public void close() {
            manager.close();
        }
    }
}
