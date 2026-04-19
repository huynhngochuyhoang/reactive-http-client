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
    void shouldPreferMethodTimeoutOverResilienceTimeout() throws Exception {
        ReactiveHttpClientProperties.ClientConfig clientConfig = clientConfig(true, 3000);
        ReactiveClientInvocationHandler handler = createHandler(clientConfig);
        MethodMetadata meta = new MethodMetadata();
        meta.setTimeoutMs(1200);

        assertEquals(1200, resolveTimeoutMs(handler, meta));
    }

    @Test
    void shouldUseResilienceTimeoutWhenMethodTimeoutNotConfigured() throws Exception {
        ReactiveHttpClientProperties.ClientConfig clientConfig = clientConfig(true, 3000);
        ReactiveClientInvocationHandler handler = createHandler(clientConfig);
        MethodMetadata meta = new MethodMetadata();

        assertEquals(3000, resolveTimeoutMs(handler, meta));
    }

    @Test
    void shouldReturnZeroWhenNoMethodAndResilienceTimeoutNotConfigured() throws Exception {
        ReactiveHttpClientProperties.ClientConfig clientConfig = clientConfig(false, 3000);
        ReactiveClientInvocationHandler handler = createHandler(clientConfig);
        MethodMetadata meta = new MethodMetadata();

        assertEquals(0, resolveTimeoutMs(handler, meta));
    }

    private static ReactiveHttpClientProperties.ClientConfig clientConfig(boolean resilienceEnabled, long resilienceTimeoutMs) {
        ReactiveHttpClientProperties.ClientConfig clientConfig = new ReactiveHttpClientProperties.ClientConfig();

        ReactiveHttpClientProperties.ResilienceConfig resilienceConfig = new ReactiveHttpClientProperties.ResilienceConfig();
        resilienceConfig.setEnabled(resilienceEnabled);
        resilienceConfig.setTimeoutMs(resilienceTimeoutMs);
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
                null,
                null,
                null,
                null,
                new ReactiveHttpClientProperties.ObservabilityConfig()
        );
    }

    private static long resolveTimeoutMs(ReactiveClientInvocationHandler handler, MethodMetadata meta) throws Exception {
        Method method = ReactiveClientInvocationHandler.class.getDeclaredMethod("resolveTimeoutMs", MethodMetadata.class);
        method.setAccessible(true);
        return (long) method.invoke(handler, meta);
    }
}
