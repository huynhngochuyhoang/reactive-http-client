package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.annotation.GET;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import io.github.huynhngochuyhoang.httpstarter.exception.HttpClientException;
import io.github.huynhngochuyhoang.httpstarter.exception.RemoteServiceException;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientObserver;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for Priority-7 housekeeping items:
 * <ul>
 *   <li>Guard codec-max-in-memory-size-mb edge cases</li>
 *   <li>Warn once on blank path template</li>
 *   <li>Preserve HTTP context when error decoding fails</li>
 *   <li>Cap resilienceWarningKeys growth</li>
 * </ul>
 */
class Priority7HousekeepingTest {

    // -------------------------------------------------------------------------
    // codec-max-in-memory-size-mb edge-case guards
    // -------------------------------------------------------------------------

    @Test
    void codecSizeMbNegativeValueIsRejectedAtStartup() {
        ReactiveHttpClientFactoryBean<Object> factory = new ReactiveHttpClientFactoryBean<>();
        ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();
        config.setCodecMaxInMemorySizeMb(-1);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> factory.resolveCodecMaxInMemorySizeBytes(config));
        assertTrue(ex.getMessage().contains("must be >= 0"),
                "Error should mention the minimum allowed value");
    }

    @Test
    void codecSizeMbZeroMapsToUnlimitedCodecBuffer() {
        ReactiveHttpClientFactoryBean<Object> factory = new ReactiveHttpClientFactoryBean<>();
        ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();
        config.setCodecMaxInMemorySizeMb(0);

        int result = factory.resolveCodecMaxInMemorySizeBytes(config);

        assertEquals(-1, result, "0 should map to -1 (unlimited codec buffer)");
    }

    @Test
    void codecSizeMbPositiveValueReturnsByteCount() {
        ReactiveHttpClientFactoryBean<Object> factory = new ReactiveHttpClientFactoryBean<>();
        ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();
        config.setCodecMaxInMemorySizeMb(4);

        int result = factory.resolveCodecMaxInMemorySizeBytes(config);

        assertEquals(4 * 1024 * 1024, result);
    }

    // -------------------------------------------------------------------------
    // Blank path template: warn-once behaviour
    // -------------------------------------------------------------------------

    @Test
    void blankPathTemplateDoesNotPreventParsing() throws Exception {
        // A blank path must be accepted (not fail-hard) because some callers intentionally
        // target the base URL directly.
        Method method = BlankPathClient.class.getMethod("root");
        MethodMetadata meta = new MethodMetadataCache().get(method);

        assertNotNull(meta);
        assertEquals("GET", meta.getHttpMethod());
        assertTrue(meta.getPathTemplate() == null || meta.getPathTemplate().isBlank());
    }

    @Test
    void blankPathTemplateWarningIsFiredOnlyOnce() throws Exception {
        // Re-parse the same method multiple times via the same cache instance.
        // The underlying ConcurrentHashMap ensures the warning fires exactly once
        // regardless of how many times the method is looked up.
        MethodMetadataCache cache = new MethodMetadataCache();
        Method method = BlankPathClient.class.getMethod("root");

        for (int i = 0; i < 5; i++) {
            cache.get(method);
        }

        assertEquals(1, cache.testOnlyBlankPathWarnedCount(),
                "Blank-path warning must be recorded exactly once per method, not once per parse call");
    }

    // -------------------------------------------------------------------------
    // Error-decode failure preserves HTTP status context
    // -------------------------------------------------------------------------

    @Test
    void fiveXxDecodeFailureProducesRemoteServiceExceptionWithCorrectStatus() {
        WebClient webClient = WebClient.builder()
                .baseUrl("http://test.local")
                .exchangeFunction(req ->
                        Mono.just(ClientResponse.create(HttpStatus.BAD_GATEWAY)
                                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                                .body("upstream error")
                                .build()))
                .build();

        StepVerifier.create(invokeSimple(buildHandler(webClient)))
                .expectErrorSatisfies(ex -> {
                    assertInstanceOf(RemoteServiceException.class, ex,
                            "5xx must produce RemoteServiceException, not a raw codec error");
                    assertEquals(502, ((RemoteServiceException) ex).getStatusCode());
                })
                .verify();
    }

    @Test
    void fourXxDecodeFailureProducesHttpClientExceptionWithCorrectStatus() {
        WebClient webClient = WebClient.builder()
                .baseUrl("http://test.local")
                .exchangeFunction(req ->
                        Mono.just(ClientResponse.create(HttpStatus.BAD_REQUEST)
                                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                                .body("bad request body")
                                .build()))
                .build();

        StepVerifier.create(invokeSimple(buildHandler(webClient)))
                .expectErrorSatisfies(ex -> {
                    assertInstanceOf(HttpClientException.class, ex,
                            "4xx must produce HttpClientException, not a raw codec error");
                    assertEquals(400, ((HttpClientException) ex).getStatusCode());
                })
                .verify();
    }

    @Test
    void decodeFailureCauseIsAttachedToWrapperException() {
        // When the error body itself cannot be decoded, the original decode error must be
        // attached as the cause so callers can distinguish "502 with unreadable body"
        // from a normal 502 response.
        WebClient webClient = WebClient.builder()
                .baseUrl("http://test.local")
                .exchangeFunction(req ->
                        Mono.just(ClientResponse.create(HttpStatus.INTERNAL_SERVER_ERROR)
                                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                                .body("{\"error\":\"boom\"}")
                                .build()))
                .build();

        DefaultErrorDecoder failingDecoder = new DefaultErrorDecoder() {
            @Override
            public Mono<? extends Throwable> decode(
                    org.springframework.web.reactive.function.client.ClientResponse response) {
                return response.releaseBody()
                        .then(Mono.error(
                                new org.springframework.core.io.buffer.DataBufferLimitException("limit exceeded")));
            }
        };

        ApplicationContext ctx = mock(ApplicationContext.class);
        ObjectProvider<HttpClientObserver> provider = mock(ObjectProvider.class);
        when(ctx.getBeanProvider(HttpClientObserver.class)).thenReturn(provider);
        when(provider.getIfAvailable()).thenReturn(null);
        ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();
        ReactiveClientInvocationHandler handler = new ReactiveClientInvocationHandler(
                webClient, new MethodMetadataCache(), new RequestArgumentResolver(),
                failingDecoder, config, "test-client", ctx,
                new NoopResilienceOperatorApplier(), new ObjectMapper(),
                new ReactiveHttpClientProperties.ObservabilityConfig());

        StepVerifier.create(invokeSimple(handler))
                .expectErrorSatisfies(ex -> {
                    assertInstanceOf(RemoteServiceException.class, ex);
                    RemoteServiceException rse = (RemoteServiceException) ex;
                    assertEquals(500, rse.getStatusCode());
                    assertNotNull(rse.getCause(), "Original decode error must be attached as cause");
                    assertInstanceOf(
                            org.springframework.core.io.buffer.DataBufferLimitException.class,
                            rse.getCause(),
                            "Cause must be the original DataBufferLimitException");
                })
                .verify();
    }

    // -------------------------------------------------------------------------
    // resilienceWarningKeys cap
    // -------------------------------------------------------------------------

    @Test
    void resilienceWarningKeySetStopsGrowingAfterCap() {
        ReactiveClientInvocationHandler handler = buildHandler(WebClient.builder()
                .baseUrl("http://test.local")
                .exchangeFunction(req -> Mono.just(ClientResponse.create(HttpStatus.OK)
                        .header(HttpHeaders.CONTENT_TYPE, "text/plain")
                        .body("ok")
                        .build()))
                .build());

        for (int i = 0; i < 300; i++) {
            handler.testOnlyLogResilienceOperatorFailure("retry", "instance-" + i,
                    new RuntimeException("simulated failure " + i));
        }

        assertTrue(handler.testOnlyResilienceWarningKeysSize() <= 256,
                "resilienceWarningKeys must not exceed 256 entries");
    }

    @Test
    void resilienceWarningKeyDuplicateDoesNotGrowSet() {
        ReactiveClientInvocationHandler handler = buildHandler(WebClient.builder()
                .baseUrl("http://test.local")
                .exchangeFunction(req -> Mono.just(ClientResponse.create(HttpStatus.OK)
                        .header(HttpHeaders.CONTENT_TYPE, "text/plain")
                        .body("ok")
                        .build()))
                .build());

        for (int i = 0; i < 10; i++) {
            handler.testOnlyLogResilienceOperatorFailure("retry", "same-instance",
                    new RuntimeException("repeated failure"));
        }

        assertEquals(1, handler.testOnlyResilienceWarningKeysSize(),
                "Repeated failures for the same instance must add only one entry");
    }

    // -------------------------------------------------------------------------
    // ConnectionProvider disposal on context shutdown
    // -------------------------------------------------------------------------

    @Test
    void factoryBeanImplementsDisposableBean() {
        ReactiveHttpClientFactoryBean<Object> factory = new ReactiveHttpClientFactoryBean<>();

        assertTrue(factory instanceof org.springframework.beans.factory.DisposableBean,
                "ReactiveHttpClientFactoryBean must implement DisposableBean for clean resource release");
    }

    @Test
    void destroyDoesNotThrowWhenConnectionProviderIsNull() throws Exception {
        // Before getObject() is called the connectionProvider field is null;
        // destroy() must handle this gracefully.
        ReactiveHttpClientFactoryBean<Object> factory = new ReactiveHttpClientFactoryBean<>();

        factory.destroy(); // must not throw
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private ReactiveClientInvocationHandler buildHandler(WebClient webClient) {
        ApplicationContext ctx = mock(ApplicationContext.class);
        ObjectProvider<HttpClientObserver> provider = mock(ObjectProvider.class);
        when(ctx.getBeanProvider(HttpClientObserver.class)).thenReturn(provider);
        when(provider.getIfAvailable()).thenReturn(null);
        ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();
        return new ReactiveClientInvocationHandler(
                webClient, new MethodMetadataCache(), new RequestArgumentResolver(),
                new DefaultErrorDecoder(), config, "test-client", ctx,
                new NoopResilienceOperatorApplier(), new ObjectMapper(),
                new ReactiveHttpClientProperties.ObservabilityConfig());
    }

    @SuppressWarnings("unchecked")
    private Mono<String> invokeSimple(ReactiveClientInvocationHandler handler) {
        try {
            Method method = SimpleTestClient.class.getMethod("fetch");
            return (Mono<String>) handler.invoke(null, method, new Object[0]);
        } catch (Throwable t) {
            return Mono.error(t);
        }
    }

    // Test interfaces

    interface BlankPathClient {
        @GET("")
        Mono<String> root();
    }

    interface SimpleTestClient {
        @GET("/items")
        Mono<String> fetch();
    }
}
