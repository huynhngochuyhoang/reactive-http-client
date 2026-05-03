package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.annotation.GET;
import io.github.huynhngochuyhoang.httpstarter.annotation.ReactiveHttpClient;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link ReactiveHttpClientCustomizer} — both the interface contract
 * and integration with {@link ReactiveHttpClientFactoryBean}.
 */
class ReactiveHttpClientCustomizerTest {

    // ---- Interface contract ------------------------------------------------

    @Test
    void defaultSupports_returnsTrueForEveryClientName() {
        // A customizer that only implements customize() must accept any client.
        ReactiveHttpClientCustomizer customizer = builder -> {};
        assertTrue(customizer.supports("client-a"));
        assertTrue(customizer.supports("client-b"));
        assertTrue(customizer.supports("any-arbitrary-name"));
    }

    @Test
    void overrideSupports_canRestrictToSpecificClient() {
        ReactiveHttpClientCustomizer customizer = new ReactiveHttpClientCustomizer() {
            @Override
            public boolean supports(String clientName) {
                return "order-service".equals(clientName);
            }

            @Override
            public void customize(WebClient.Builder builder) {}
        };
        assertTrue(customizer.supports("order-service"));
        assertFalse(customizer.supports("user-service"));
        assertFalse(customizer.supports(""));
    }

    // ---- Factory bean integration ------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    void factoryBean_invokesCustomize_whenSupportsReturnsTrue() throws Exception {
        ReactiveHttpClientCustomizer customizer = mock(ReactiveHttpClientCustomizer.class);
        when(customizer.supports("test-client")).thenReturn(true);

        ReactiveHttpClientFactoryBean<PingClient> factoryBean = buildFactoryBean(List.of(customizer));
        try {
            factoryBean.getObject();
            verify(customizer).customize(any(WebClient.Builder.class));
        } finally {
            factoryBean.destroy();
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void factoryBean_skipsCustomizer_whenSupportsReturnsFalse() throws Exception {
        ReactiveHttpClientCustomizer customizer = mock(ReactiveHttpClientCustomizer.class);
        when(customizer.supports("test-client")).thenReturn(false);

        ReactiveHttpClientFactoryBean<PingClient> factoryBean = buildFactoryBean(List.of(customizer));
        try {
            factoryBean.getObject();
            verify(customizer, never()).customize(any(WebClient.Builder.class));
        } finally {
            factoryBean.destroy();
        }
    }

    @Test
    void factoryBean_appliesAllMatchingCustomizers_inRegistrationOrder() throws Exception {
        List<String> invocationOrder = new ArrayList<>();

        ReactiveHttpClientCustomizer first = builder -> invocationOrder.add("first");
        ReactiveHttpClientCustomizer second = builder -> invocationOrder.add("second");
        ReactiveHttpClientCustomizer third = builder -> invocationOrder.add("third");

        ReactiveHttpClientFactoryBean<PingClient> factoryBean =
                buildFactoryBean(List.of(first, second, third));
        try {
            factoryBean.getObject();
            assertEquals(List.of("first", "second", "third"), invocationOrder);
        } finally {
            factoryBean.destroy();
        }
    }

    @Test
    void factoryBean_onlyAppliesMatching_whenMixedCustomizersRegistered() throws Exception {
        List<String> invocationOrder = new ArrayList<>();

        ReactiveHttpClientCustomizer matchingFirst = new ReactiveHttpClientCustomizer() {
            @Override
            public boolean supports(String clientName) {
                return "test-client".equals(clientName);
            }

            @Override
            public void customize(WebClient.Builder builder) {
                invocationOrder.add("matching-first");
            }
        };

        ReactiveHttpClientCustomizer notMatching = new ReactiveHttpClientCustomizer() {
            @Override
            public boolean supports(String clientName) {
                return "other-client".equals(clientName);
            }

            @Override
            public void customize(WebClient.Builder builder) {
                invocationOrder.add("not-matching");
            }
        };

        ReactiveHttpClientCustomizer matchingSecond = new ReactiveHttpClientCustomizer() {
            @Override
            public boolean supports(String clientName) {
                return "test-client".equals(clientName);
            }

            @Override
            public void customize(WebClient.Builder builder) {
                invocationOrder.add("matching-second");
            }
        };

        ReactiveHttpClientFactoryBean<PingClient> factoryBean =
                buildFactoryBean(List.of(matchingFirst, notMatching, matchingSecond));
        try {
            factoryBean.getObject();
            assertEquals(List.of("matching-first", "matching-second"), invocationOrder,
                    "Only customizers whose supports() returns true for 'test-client' must be applied");
        } finally {
            factoryBean.destroy();
        }
    }

    @Test
    void factoryBean_appliesCustomizer_withDefaultSupports_toEveryClient() throws Exception {
        List<String> invocations = new ArrayList<>();

        // Lambda → uses default supports() → returns true for all clients.
        ReactiveHttpClientCustomizer globalCustomizer = builder -> invocations.add("applied");

        ReactiveHttpClientFactoryBean<PingClient> factoryBean =
                buildFactoryBean(List.of(globalCustomizer));
        try {
            factoryBean.getObject();
            assertEquals(List.of("applied"), invocations,
                    "A customizer with the default supports() must be applied to every client");
        } finally {
            factoryBean.destroy();
        }
    }

    @Test
    void factoryBean_buildsSuccessfully_whenNoCustomizersRegistered() throws Exception {
        ReactiveHttpClientFactoryBean<PingClient> factoryBean = buildFactoryBean(List.of());
        try {
            Object proxy = factoryBean.getObject();
            assertTrue(proxy instanceof PingClient,
                    "Factory bean must produce a usable proxy even with no customizers registered");
        } finally {
            factoryBean.destroy();
        }
    }

    // ---- Helpers -----------------------------------------------------------

    /**
     * Builds a {@link ReactiveHttpClientFactoryBean} backed by a minimally-mocked
     * {@link ApplicationContext} that provides the given {@code customizers}.
     */
    @SuppressWarnings("unchecked")
    private ReactiveHttpClientFactoryBean<PingClient> buildFactoryBean(
            List<ReactiveHttpClientCustomizer> customizers) {

        ApplicationContext ctx = mock(ApplicationContext.class);

        // Catch-all: return a no-op ObjectProvider for any unrecognised bean type
        // (handles Resilience4j, ObjectMapper, HttpClientObserver, etc.).
        ObjectProvider<Object> defaultProvider = mock(ObjectProvider.class);
        when(defaultProvider.getIfAvailable()).thenReturn(null);
        lenient().when(defaultProvider.getIfAvailable(any(Supplier.class)))
                .thenAnswer(inv -> inv.getArgument(0, Supplier.class).get());
        lenient().when(defaultProvider.orderedStream()).thenReturn(Stream.empty());
        when(ctx.getBeanProvider(any(Class.class))).thenReturn((ObjectProvider) defaultProvider);

        // Properties (with a base-url so the factory bean doesn't throw)
        ObjectProvider<ReactiveHttpClientProperties> propsProvider = mock(ObjectProvider.class);
        ReactiveHttpClientProperties props = new ReactiveHttpClientProperties();
        ReactiveHttpClientProperties.ClientConfig clientConfig =
                new ReactiveHttpClientProperties.ClientConfig();
        clientConfig.setBaseUrl("http://localhost:8080");
        props.getClients().put("test-client", clientConfig);
        when(propsProvider.getIfAvailable(any(Supplier.class))).thenReturn(props);
        when(ctx.getBeanProvider(ReactiveHttpClientProperties.class)).thenReturn(propsProvider);

        // MethodMetadataCache
        ObjectProvider<MethodMetadataCache> cacheProvider = mock(ObjectProvider.class);
        when(cacheProvider.getIfAvailable(any(Supplier.class))).thenReturn(new MethodMetadataCache());
        when(ctx.getBeanProvider(MethodMetadataCache.class)).thenReturn(cacheProvider);

        // DefaultErrorDecoder
        ObjectProvider<DefaultErrorDecoder> errorProvider = mock(ObjectProvider.class);
        when(errorProvider.getIfAvailable(any(Supplier.class))).thenReturn(new DefaultErrorDecoder());
        when(ctx.getBeanProvider(DefaultErrorDecoder.class)).thenReturn(errorProvider);

        // WebClient.Builder
        ObjectProvider<WebClient.Builder> builderProvider = mock(ObjectProvider.class);
        when(builderProvider.getIfAvailable(any(Supplier.class))).thenReturn(WebClient.builder());
        when(ctx.getBeanProvider(WebClient.Builder.class)).thenReturn(builderProvider);

        // ReactiveHttpClientCustomizer
        ObjectProvider<ReactiveHttpClientCustomizer> customizerProvider = mock(ObjectProvider.class);
        when(customizerProvider.orderedStream()).thenReturn(customizers.stream());
        when(ctx.getBeanProvider(ReactiveHttpClientCustomizer.class)).thenReturn(customizerProvider);

        ReactiveHttpClientFactoryBean<PingClient> factoryBean = new ReactiveHttpClientFactoryBean<>();
        factoryBean.setType(PingClient.class);
        factoryBean.setApplicationContext(ctx);
        return factoryBean;
    }

    @ReactiveHttpClient(name = "test-client")
    interface PingClient {
        @GET("/ping")
        Mono<String> ping();
    }
}
