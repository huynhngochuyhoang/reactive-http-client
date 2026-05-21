package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.web.reactive.function.client.WebClient;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class ReactiveClientInvocationHandlerTimeoutResolutionTest {

    @Test
    void shouldPreferMethodTimeoutOverClientRequestTimeout() throws Exception {
        ReactiveHttpClientProperties.ClientConfig clientConfig = clientConfig(3000);
        ReactiveClientInvocationHandler handler = createHandler(clientConfig);
        MethodMetadata meta = new MethodMetadata();
        meta.setTimeoutMs(1200);

        assertEquals(1200, resolveTimeoutMs(handler, meta));
    }

    @Test
    void shouldUseClientRequestTimeoutWhenMethodAndApiTimeoutNotConfigured() throws Exception {
        ReactiveHttpClientProperties.ClientConfig clientConfig = clientConfig(3000);
        ReactiveClientInvocationHandler handler = createHandler(clientConfig);
        MethodMetadata meta = new MethodMetadata();

        assertEquals(3000, resolveTimeoutMs(handler, meta));
    }

    @Test
    void shouldUseApiMapTimeoutWhenMethodTimeoutNotConfigured() throws Exception {
        ReactiveHttpClientProperties.ClientConfig clientConfig = clientConfig(3000);
        ReactiveClientInvocationHandler handler = createHandler(clientConfig);
        MethodMetadata meta = new MethodMetadata();

        assertEquals(1800, resolveTimeoutMs(handler, meta, 1800));
    }

    @Test
    void shouldUseDeprecatedResilienceTimeoutAliasWhenCanonicalTimeoutNotConfigured() throws Exception {
        ReactiveHttpClientProperties.ClientConfig clientConfig = deprecatedAliasConfig(3000);
        ReactiveClientInvocationHandler handler = createHandler(clientConfig);
        MethodMetadata meta = new MethodMetadata();

        assertEquals(3000, resolveTimeoutMs(handler, meta));
    }

    @Test
    void shouldPreferClientRequestTimeoutOverDeprecatedResilienceTimeoutAlias() throws Exception {
        ReactiveHttpClientProperties.ClientConfig clientConfig = clientConfig(2000);
        clientConfig.getResilience().setTimeoutMs(3000);
        ReactiveClientInvocationHandler handler = createHandler(clientConfig);
        MethodMetadata meta = new MethodMetadata();

        assertEquals(2000, resolveTimeoutMs(handler, meta));
    }

    @Test
    void shouldReturnZeroWhenNoClientTimeoutConfigured() throws Exception {
        ReactiveHttpClientProperties.ClientConfig clientConfig = new ReactiveHttpClientProperties.ClientConfig();
        ReactiveClientInvocationHandler handler = createHandler(clientConfig);
        MethodMetadata meta = new MethodMetadata();

        assertEquals(0, resolveTimeoutMs(handler, meta));
    }

    private static ReactiveHttpClientProperties.ClientConfig clientConfig(long requestTimeoutMs) {
        ReactiveHttpClientProperties.ClientConfig clientConfig = new ReactiveHttpClientProperties.ClientConfig();
        clientConfig.setRequestTimeoutMs(requestTimeoutMs);
        return clientConfig;
    }

    private static ReactiveHttpClientProperties.ClientConfig deprecatedAliasConfig(long timeoutMs) {
        ReactiveHttpClientProperties.ClientConfig clientConfig = new ReactiveHttpClientProperties.ClientConfig();
        ReactiveHttpClientProperties.ResilienceConfig resilienceConfig = new ReactiveHttpClientProperties.ResilienceConfig();
        resilienceConfig.setTimeoutMs(timeoutMs);
        clientConfig.setResilience(resilienceConfig);
        return clientConfig;
    }

    private static ReactiveClientInvocationHandler createHandler(ReactiveHttpClientProperties.ClientConfig clientConfig) {
        return new ReactiveClientInvocationHandler(
                WebClient.builder().baseUrl("http://localhost").build(),
                new MethodMetadataCache(),
                new RequestArgumentResolver(),
                new DefaultErrorDecoder(),
                clientConfig,
                "test-client",
                mock(ApplicationContext.class),
                new NoopResilienceOperatorApplier(),
                null,
                new ReactiveHttpClientProperties.ObservabilityConfig()
        );
    }

    private static long resolveTimeoutMs(ReactiveClientInvocationHandler handler, MethodMetadata meta) throws Exception {
        Method method = ReactiveClientInvocationHandler.class.getDeclaredMethod("resolveTimeoutMs", MethodMetadata.class);
        method.setAccessible(true);
        return (long) method.invoke(handler, meta);
    }

    private static long resolveTimeoutMs(ReactiveClientInvocationHandler handler, MethodMetadata meta, long configuredApiTimeoutMs) throws Exception {
        Method method = ReactiveClientInvocationHandler.class.getDeclaredMethod("resolveTimeoutMs", MethodMetadata.class, long.class);
        method.setAccessible(true);
        return (long) method.invoke(handler, meta, configuredApiTimeoutMs);
    }
}
