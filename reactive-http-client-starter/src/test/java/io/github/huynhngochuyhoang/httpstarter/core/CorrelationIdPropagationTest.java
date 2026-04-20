package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.annotation.GET;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import io.github.huynhngochuyhoang.httpstarter.filter.CorrelationIdWebFilter;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientObserver;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.util.context.Context;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests that {@code X-Correlation-Id} is forwarded on outbound WebClient requests
 * when it is present in the Reactor context (placed there by {@link CorrelationIdWebFilter}).
 */
class CorrelationIdPropagationTest {

    @Test
    void shouldForwardCorrelationIdFromReactorContext() {
        AtomicReference<String> capturedHeader = new AtomicReference<>();

        // Build WebClient with the actual correlationId exchange filter applied
        WebClient webClient = WebClient.builder()
                .baseUrl("http://test.local")
                .filter(CorrelationIdWebFilter.exchangeFilter())
                .exchangeFunction(request -> {
                    capturedHeader.set(request.headers().getFirst(CorrelationIdWebFilter.CORRELATION_ID_HEADER));
                    ClientResponse ok = ClientResponse.create(HttpStatus.OK)
                            .header(HttpHeaders.CONTENT_TYPE, "text/plain")
                            .body("ok")
                            .build();
                    return Mono.just(ok);
                })
                .build();

        ReactiveClientInvocationHandler handler = createHandler(webClient);

        Mono<String> result = invokeGetUsers(handler)
                .contextWrite(Context.of(CorrelationIdWebFilter.CORRELATION_ID_CONTEXT_KEY, "testing"));

        StepVerifier.create(result)
                .expectNextMatches(body -> "ok".equals(body))
                .verifyComplete();

        assertEquals("testing", capturedHeader.get(),
                "X-Correlation-Id should be forwarded from Reactor context to outbound request");
    }

    @Test
    void shouldNotForwardCorrelationIdWhenContextIsEmpty() {
        AtomicReference<String> capturedHeader = new AtomicReference<>();

        WebClient webClient = WebClient.builder()
                .baseUrl("http://test.local")
                .filter(CorrelationIdWebFilter.exchangeFilter())
                .exchangeFunction(request -> {
                    capturedHeader.set(request.headers().getFirst(CorrelationIdWebFilter.CORRELATION_ID_HEADER));
                    ClientResponse ok = ClientResponse.create(HttpStatus.OK)
                            .header(HttpHeaders.CONTENT_TYPE, "text/plain")
                            .body("ok")
                            .build();
                    return Mono.just(ok);
                })
                .build();

        ReactiveClientInvocationHandler handler = createHandler(webClient);

        Mono<String> result = invokeGetUsers(handler);

        StepVerifier.create(result)
                .expectNextMatches(body -> "ok".equals(body))
                .verifyComplete();

        assertNull(capturedHeader.get(),
                "X-Correlation-Id should not be set when not present in context or MDC");
    }

    @Test
    void shouldForwardCorrelationIdFromMdcAsFallback() {
        AtomicReference<String> capturedHeader = new AtomicReference<>();

        WebClient webClient = WebClient.builder()
                .baseUrl("http://test.local")
                .filter(CorrelationIdWebFilter.exchangeFilter())
                .exchangeFunction(request -> {
                    capturedHeader.set(request.headers().getFirst(CorrelationIdWebFilter.CORRELATION_ID_HEADER));
                    ClientResponse ok = ClientResponse.create(HttpStatus.OK)
                            .header(HttpHeaders.CONTENT_TYPE, "text/plain")
                            .body("ok")
                            .build();
                    return Mono.just(ok);
                })
                .build();

        ReactiveClientInvocationHandler handler = createHandler(webClient);

        // Put value into MDC directly (simulates non-reactive or Brave integration)
        org.slf4j.MDC.put(CorrelationIdWebFilter.CORRELATION_ID_CONTEXT_KEY, "mdc-value");
        try {
            Mono<String> result = invokeGetUsers(handler);

            StepVerifier.create(result)
                    .expectNextMatches(body -> "ok".equals(body))
                    .verifyComplete();

            assertEquals("mdc-value", capturedHeader.get(),
                    "X-Correlation-Id should be forwarded from MDC when context key is absent");
        } finally {
            org.slf4j.MDC.remove(CorrelationIdWebFilter.CORRELATION_ID_CONTEXT_KEY);
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private static ReactiveClientInvocationHandler createHandler(WebClient webClient) {
        ApplicationContext appCtx = mock(ApplicationContext.class);
        ObjectProvider<HttpClientObserver> observerProvider = mock(ObjectProvider.class);
        when(appCtx.getBeanProvider(HttpClientObserver.class)).thenReturn(observerProvider);
        when(observerProvider.getIfAvailable()).thenReturn(null);

        return new ReactiveClientInvocationHandler(
                webClient,
                new MethodMetadataCache(),
                new RequestArgumentResolver(),
                new DefaultErrorDecoder(),
                new ReactiveHttpClientProperties.ClientConfig(),
                "test-client",
                appCtx,
                new NoopResilienceOperatorApplier(),
                null,
                new ReactiveHttpClientProperties.ObservabilityConfig()
        );
    }

    @SuppressWarnings("unchecked")
    private static Mono<String> invokeGetUsers(ReactiveClientInvocationHandler handler) {
        try {
            java.lang.reflect.Method method = CorrelationTestClient.class.getMethod("getUsers");
            return (Mono<String>) handler.invoke(null, method, new Object[0]);
        } catch (Throwable t) {
            return Mono.error(t);
        }
    }

    interface CorrelationTestClient {
        @GET("/users")
        Mono<String> getUsers();
    }
}
