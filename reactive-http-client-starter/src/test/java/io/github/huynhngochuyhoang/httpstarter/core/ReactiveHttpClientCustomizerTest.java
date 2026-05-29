package io.github.huynhngochuyhoang.httpstarter.core;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import io.github.huynhngochuyhoang.httpstarter.annotation.GET;
import io.github.huynhngochuyhoang.httpstarter.annotation.ReactiveHttpClient;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link ReactiveHttpClientCustomizer} — both the interface contract
 * and integration with {@link ReactiveHttpClientFactoryBean}.
 */
@ExtendWith(OutputCaptureExtension.class)
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
    void factoryBean_appliesAllMatchingCustomizers_inStreamOrder() throws Exception {
        List<String> invocationOrder = new ArrayList<>();

        // orderedStream() delivers customizers in the order the stream provides them.
        // When no @Order is declared, Spring preserves bean-registration order.
        // The mock directly controls the stream, so "first/second/third" represents
        // whatever order orderedStream() would produce (could be @Order-sorted in a real context).
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
    void factoryBean_honorsAtOrderAnnotation_onCustomizerBeans() throws Exception {
        List<String> invocationOrder = new ArrayList<>();

        // Deliver customizers in the order that Spring's AnnotationAwareOrderComparator
        // (used internally by orderedStream()) would produce for @Order-annotated beans.
        List<ReactiveHttpClientCustomizer> customizers = Arrays.asList(
                new Order2Customizer(invocationOrder),
                new Order3Customizer(invocationOrder),
                new Order1Customizer(invocationOrder));
        customizers.sort(AnnotationAwareOrderComparator.INSTANCE);

        ReactiveHttpClientFactoryBean<PingClient> factoryBean = buildFactoryBean(customizers);
        try {
            factoryBean.getObject();
            assertEquals(List.of("order-1", "order-2", "order-3"), invocationOrder,
                    "Customizers must be applied in @Order-sorted sequence");
        } finally {
            factoryBean.destroy();
        }
    }

    @Test
    void factoryBean_debugDiagnosticsListAppliedCustomizersPerClient(CapturedOutput output) throws Exception {
        List<String> invocationOrder = new ArrayList<>();
        ReactiveHttpClientCustomizer customizer = new Order1Customizer(invocationOrder);
        Logger logger = (Logger) LoggerFactory.getLogger(ReactiveHttpClientFactoryBean.class);
        Level previousLevel = logger.getLevel();
        logger.setLevel(Level.DEBUG);

        ReactiveHttpClientFactoryBean<PingClient> factoryBean = buildFactoryBean(List.of(customizer));
        try {
            factoryBean.getObject();
            assertTrue(output.getOut().contains("Applying ReactiveHttpClientCustomizer"));
            assertTrue(output.getOut().contains(Order1Customizer.class.getName()));
            assertTrue(output.getOut().contains("test-client"));
        } finally {
            logger.setLevel(previousLevel);
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
    void factoryClientExchangeLoggingSeesHeadersAddedByCustomizerFilter() throws Exception {
        AtomicReference<HttpExchangeLogContext> logged = new AtomicReference<>();
        ReactiveHttpClientCustomizer customizer = builder -> builder.filter((request, next) -> next.exchange(
                ClientRequest.from(request)
                        .header("X-Request-ID", "req-factory")
                        .build()));
        ReactiveHttpClientProperties.ClientConfig clientConfig = new ReactiveHttpClientProperties.ClientConfig();
        clientConfig.setBaseUrl("http://localhost:8080");
        clientConfig.setExchangeLoggingEnabled(true);
        clientConfig.setLogPreset(ReactiveHttpClientProperties.LogPreset.HEADERS);
        WebClient.Builder builder = WebClient.builder()
                .exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.OK)
                        .header(HttpHeaders.CONTENT_TYPE, "text/plain")
                        .body("pong")
                        .build()));

        ReactiveHttpClientFactoryBean<PingClient> factoryBean = buildFactoryBean(
                List.of(customizer), builder, clientConfig, new RecordingDefaultHttpExchangeLogger(logged));
        try {
            PingClient client = factoryBean.getObject();
            StepVerifier.create(client.ping())
                    .expectNext("pong")
                    .verifyComplete();

            assertEquals("req-factory", logged.get().requestHeaders().get("X-Request-ID"));
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
        ReactiveHttpClientProperties.ClientConfig clientConfig = new ReactiveHttpClientProperties.ClientConfig();
        clientConfig.setBaseUrl("http://localhost:8080");
        return buildFactoryBean(customizers, WebClient.builder(), clientConfig, null);
    }

    @SuppressWarnings("unchecked")
    private ReactiveHttpClientFactoryBean<PingClient> buildFactoryBean(
            List<ReactiveHttpClientCustomizer> customizers,
            WebClient.Builder builder,
            ReactiveHttpClientProperties.ClientConfig clientConfig,
            DefaultHttpExchangeLogger defaultLogger) {

        ApplicationContext ctx = mock(ApplicationContext.class);

        // Catch-all: return a no-op ObjectProvider for any unrecognised bean type
        // (handles Resilience4j, ObjectMapper, HttpClientObserver, etc.).
        ObjectProvider<Object> defaultProvider = mock(ObjectProvider.class);
        when(defaultProvider.getIfAvailable()).thenReturn(null);
        lenient().when(defaultProvider.getIfAvailable(any(Supplier.class)))
                .thenAnswer(inv -> inv.getArgument(0, Supplier.class).get());
        lenient().when(defaultProvider.orderedStream()).thenAnswer(inv -> Stream.empty());
        when(ctx.getBeanProvider(any(Class.class))).thenReturn((ObjectProvider) defaultProvider);

        // Properties (with a base-url so the factory bean doesn't throw)
        ObjectProvider<ReactiveHttpClientProperties> propsProvider = mock(ObjectProvider.class);
        ReactiveHttpClientProperties props = new ReactiveHttpClientProperties();
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
        when(builderProvider.getIfAvailable(any(Supplier.class))).thenReturn(builder);
        when(ctx.getBeanProvider(WebClient.Builder.class)).thenReturn(builderProvider);

        if (defaultLogger != null) {
            ObjectProvider<DefaultHttpExchangeLogger> loggerProvider = mock(ObjectProvider.class);
            when(loggerProvider.getIfAvailable()).thenReturn(defaultLogger);
            when(ctx.getBeanProvider(DefaultHttpExchangeLogger.class)).thenReturn(loggerProvider);
        }

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

    // ---- Static inner classes for @Order test ----------------------------

    private static class RecordingDefaultHttpExchangeLogger extends DefaultHttpExchangeLogger {
        private final AtomicReference<HttpExchangeLogContext> logged;

        private RecordingDefaultHttpExchangeLogger(AtomicReference<HttpExchangeLogContext> logged) {
            this.logged = logged;
        }

        @Override
        public void log(HttpExchangeLogContext context) {
            logged.set(context);
        }
    }

    @Order(1)
    private static class Order1Customizer implements ReactiveHttpClientCustomizer {
        private final List<String> log;
        Order1Customizer(List<String> log) { this.log = log; }
        @Override public void customize(WebClient.Builder builder) { log.add("order-1"); }
    }

    @Order(2)
    private static class Order2Customizer implements ReactiveHttpClientCustomizer {
        private final List<String> log;
        Order2Customizer(List<String> log) { this.log = log; }
        @Override public void customize(WebClient.Builder builder) { log.add("order-2"); }
    }

    @Order(3)
    private static class Order3Customizer implements ReactiveHttpClientCustomizer {
        private final List<String> log;
        Order3Customizer(List<String> log) { this.log = log; }
        @Override public void customize(WebClient.Builder builder) { log.add("order-3"); }
    }
}
