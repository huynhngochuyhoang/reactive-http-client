package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.annotation.Body;
import io.github.huynhngochuyhoang.httpstarter.annotation.GET;
import io.github.huynhngochuyhoang.httpstarter.annotation.HeaderParam;
import io.github.huynhngochuyhoang.httpstarter.annotation.POST;
import io.github.huynhngochuyhoang.httpstarter.auth.AuthRequest;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientObserver;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReactiveClientInvocationHandlerBehaviorTest {

    @Test
    void shouldNotForceDefaultAcceptWhenUserProvidedOne() {
        AtomicReference<ClientRequest> captured = new AtomicReference<>();
        WebClient webClient = WebClient.builder()
                .baseUrl("http://test.local")
                .exchangeFunction(request -> {
                    captured.set(request);
                    return Mono.just(ClientResponse.create(HttpStatus.OK)
                            .header(HttpHeaders.CONTENT_TYPE, "text/plain")
                            .body("ok")
                            .build());
                })
                .build();

        ReactiveClientInvocationHandler handler = createHandler(webClient);
        StepVerifier.create(invokeGet(handler, "application/xml"))
                .expectNext("ok")
                .verifyComplete();

        assertEquals("application/xml", captured.get().headers().getFirst(HttpHeaders.ACCEPT));
        assertFalse(captured.get().headers().get(HttpHeaders.ACCEPT).contains("application/json"));
    }

    @Test
    void shouldNotForceDefaultContentTypeWhenUserProvidedOne() {
        AtomicReference<ClientRequest> captured = new AtomicReference<>();
        WebClient webClient = WebClient.builder()
                .baseUrl("http://test.local")
                .exchangeFunction(request -> {
                    captured.set(request);
                    return Mono.just(ClientResponse.create(HttpStatus.OK)
                            .header(HttpHeaders.CONTENT_TYPE, "text/plain")
                            .body("ok")
                            .build());
                })
                .build();

        ReactiveClientInvocationHandler handler = createHandler(webClient);
        StepVerifier.create(invokePost(handler, "text/plain", "payload"))
                .expectNext("ok")
                .verifyComplete();

        assertEquals("text/plain", captured.get().headers().getFirst(HttpHeaders.CONTENT_TYPE));
        assertFalse(captured.get().headers().get(HttpHeaders.CONTENT_TYPE).contains("application/json"));
    }

    @Test
    void shouldUseProxyIdentityForObjectMethods() {
        ReactiveClientInvocationHandler handler = createHandler(WebClient.builder().baseUrl("http://test.local").build());
        Object proxy1 = Proxy.newProxyInstance(
                ClientWithHeaders.class.getClassLoader(),
                new Class<?>[]{ClientWithHeaders.class},
                handler
        );
        Object proxy2 = Proxy.newProxyInstance(
                ClientWithHeaders.class.getClassLoader(),
                new Class<?>[]{ClientWithHeaders.class},
                handler
        );

        assertTrue(proxy1.equals(proxy1));
        assertFalse(proxy1.equals(proxy2));
        assertEquals(System.identityHashCode(proxy1), proxy1.hashCode());
        assertTrue(proxy1.toString().contains("test-client"));
    }

    @Test
    void shouldProvideRawBodyForJsonContentTypeEvenWhenHeaderExplicitlyProvided() {
        AtomicReference<byte[]> capturedRawBody = new AtomicReference<>();
        WebClient webClient = WebClient.builder()
                .baseUrl("http://test.local")
                .exchangeFunction(request -> {
                    capturedRawBody.set((byte[]) request.attribute(AuthRequest.REQUEST_RAW_BODY_ATTRIBUTE).orElse(null));
                    return Mono.just(ClientResponse.create(HttpStatus.OK)
                            .header(HttpHeaders.CONTENT_TYPE, "text/plain")
                            .body("ok")
                            .build());
                })
                .build();

        ReactiveClientInvocationHandler handler = createHandler(webClient);
        StepVerifier.create(invokePostJson(handler, "application/json", Map.of("id", 1)))
                .expectNext("ok")
                .verifyComplete();

        assertNotNull(capturedRawBody.get());
        assertTrue(new String(capturedRawBody.get(), StandardCharsets.UTF_8).contains("\"id\":1"));
    }

    @SuppressWarnings("unchecked")
    private static Mono<String> invokeGet(ReactiveClientInvocationHandler handler, String accept) {
        try {
            var method = ClientWithHeaders.class.getMethod("get", String.class);
            return (Mono<String>) handler.invoke(null, method, new Object[]{accept});
        } catch (Throwable t) {
            return Mono.error(t);
        }
    }

    @SuppressWarnings("unchecked")
    private static Mono<String> invokePost(ReactiveClientInvocationHandler handler, String contentType, String body) {
        try {
            var method = ClientWithBodyHeaders.class.getMethod("post", String.class, String.class);
            return (Mono<String>) handler.invoke(null, method, new Object[]{contentType, body});
        } catch (Throwable t) {
            return Mono.error(t);
        }
    }

    @SuppressWarnings("unchecked")
    private static Mono<String> invokePostJson(ReactiveClientInvocationHandler handler, String contentType, Map<String, Object> body) {
        try {
            var method = ClientWithJsonBodyHeaders.class.getMethod("post", String.class, Map.class);
            return (Mono<String>) handler.invoke(null, method, new Object[]{contentType, body});
        } catch (Throwable t) {
            return Mono.error(t);
        }
    }

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
                new ObjectMapper(),
                new ReactiveHttpClientProperties.ObservabilityConfig()
        );
    }

    interface ClientWithHeaders {
        @GET("/headers")
        Mono<String> get(@HeaderParam("Accept") String accept);
    }

    interface ClientWithBodyHeaders {
        @POST("/body")
        Mono<String> post(@HeaderParam("Content-Type") String contentType, @Body String body);
    }

    interface ClientWithJsonBodyHeaders {
        @POST("/body")
        Mono<String> post(@HeaderParam("Content-Type") String contentType, @Body Map<String, Object> body);
    }
}
