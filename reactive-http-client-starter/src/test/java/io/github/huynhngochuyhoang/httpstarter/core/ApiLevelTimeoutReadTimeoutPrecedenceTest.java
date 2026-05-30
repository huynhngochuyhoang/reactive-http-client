package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.annotation.ApiRef;
import io.github.huynhngochuyhoang.httpstarter.annotation.GET;
import io.github.huynhngochuyhoang.httpstarter.annotation.TimeoutMs;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import io.github.huynhngochuyhoang.httpstarter.exception.ErrorCategory;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientObserver;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientObserverEvent;
import io.netty.handler.timeout.ReadTimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.server.HttpServer;
import reactor.test.StepVerifier;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ApiLevelTimeoutReadTimeoutPrecedenceTest {

    @Test
    void shouldDisableGlobalReadTimeoutWhenMethodTimeoutIsZero() {
        DisposableServer server = HttpServer.create()
                .port(0)
                .route(routes -> routes.get("/slow", (request, response) ->
                        Mono.delay(java.time.Duration.ofMillis(250))
                                .then(response.sendString(Mono.just("ok")).then())))
                .bindNow();

        try {
            WebClient webClient = WebClient.builder()
                    .baseUrl("http://127.0.0.1:" + server.port())
                    .clientConnector(new ReactorClientHttpConnector(
                            HttpClient.create().responseTimeout(java.time.Duration.ofMillis(100))))
                    .build();

            ReactiveClientInvocationHandler handler = createHandler(webClient, new ReactiveHttpClientProperties.ClientConfig());

            StepVerifier.create(invokeNoTimeoutApi(handler))
                    .expectNext("ok")
                    .verifyComplete();
        } finally {
            server.disposeNow();
        }
    }

    @Test
    void shouldApplyClientRequestTimeoutAsRequestLevelOverrideWhenMethodTimeoutIsNotConfigured() {
        DisposableServer server = HttpServer.create()
                .port(0)
                .route(routes -> routes.get("/slow", (request, response) ->
                        Mono.delay(java.time.Duration.ofMillis(250))
                                .then(response.sendString(Mono.just("ok")).then())))
                .bindNow();

        try {
            WebClient webClient = WebClient.builder()
                    .baseUrl("http://127.0.0.1:" + server.port())
                    .clientConnector(new ReactorClientHttpConnector(
                            HttpClient.create().responseTimeout(java.time.Duration.ofMillis(100))))
                    .build();

            ReactiveHttpClientProperties.ClientConfig clientConfig = new ReactiveHttpClientProperties.ClientConfig();
            clientConfig.setRequestTimeoutMs(1000);

            ReactiveClientInvocationHandler handler = createHandler(webClient, clientConfig);

            StepVerifier.create(invokeClientRequestTimeoutApi(handler))
                    .expectNext("ok")
                    .verifyComplete();
        } finally {
            server.disposeNow();
        }
    }

    @Test
    void shouldUseNettyReadTimeoutExceptionWhenRequestLevelTimeoutIsConfigured() {
        DisposableServer server = HttpServer.create()
                .port(0)
                .route(routes -> routes.get("/slow", (request, response) ->
                        Mono.delay(java.time.Duration.ofMillis(250))
                                .then(response.sendString(Mono.just("ok")).then())))
                .bindNow();

        try {
            WebClient webClient = WebClient.builder()
                    .baseUrl("http://127.0.0.1:" + server.port())
                    .clientConnector(new ReactorClientHttpConnector(
                            HttpClient.create().responseTimeout(java.time.Duration.ofMillis(100))))
                    .build();

            ReactiveHttpClientProperties.ClientConfig clientConfig = new ReactiveHttpClientProperties.ClientConfig();
            clientConfig.setRequestTimeoutMs(150);

            ReactiveClientInvocationHandler handler = createHandler(webClient, clientConfig);

            StepVerifier.create(invokeClientRequestTimeoutApi(handler))
                    .expectErrorSatisfies(ex -> {
                        WebClientRequestException requestException = assertInstanceOf(WebClientRequestException.class, ex);
                        assertInstanceOf(ReadTimeoutException.class, requestException.getCause());
                    })
                    .verify();
        } finally {
            server.disposeNow();
        }
    }

    @Test
    void shouldApplyApiRefTimeoutBeforeClientRequestTimeout() {
        DisposableServer server = slowServer(250);

        try {
            WebClient webClient = WebClient.builder()
                    .baseUrl("http://127.0.0.1:" + server.port())
                    .clientConnector(new ReactorClientHttpConnector(
                            HttpClient.create().responseTimeout(java.time.Duration.ofMillis(100))))
                    .build();

            ReactiveHttpClientProperties.ClientConfig clientConfig = new ReactiveHttpClientProperties.ClientConfig();
            clientConfig.setRequestTimeoutMs(100);
            clientConfig.setApis(Map.of("slow-api", api("GET", "/slow", 1000)));

            ReactiveClientInvocationHandler handler = createHandler(webClient, clientConfig);

            StepVerifier.create(invokeMono(handler, ApiRefTimeoutClient.class, "slow"))
                    .expectNext("ok")
                    .verifyComplete();
        } finally {
            server.disposeNow();
        }
    }

    @Test
    void shouldApplyMethodTimeoutBeforeApiRefTimeout() {
        DisposableServer server = slowServer(250);

        try {
            WebClient webClient = WebClient.builder()
                    .baseUrl("http://127.0.0.1:" + server.port())
                    .clientConnector(new ReactorClientHttpConnector(
                            HttpClient.create().responseTimeout(java.time.Duration.ofMillis(1000))))
                    .build();

            ReactiveHttpClientProperties.ClientConfig clientConfig = new ReactiveHttpClientProperties.ClientConfig();
            clientConfig.setRequestTimeoutMs(1000);
            clientConfig.setApis(Map.of("slow-api", api("GET", "/slow", 1000)));

            ReactiveClientInvocationHandler handler = createHandler(webClient, clientConfig);

            StepVerifier.create(invokeMono(handler, MethodTimeoutOverridesApiRefClient.class, "slow"))
                    .expectErrorSatisfies(ex -> {
                        WebClientRequestException requestException = assertInstanceOf(WebClientRequestException.class, ex);
                        assertInstanceOf(ReadTimeoutException.class, requestException.getCause());
                    })
                    .verify();
        } finally {
            server.disposeNow();
        }
    }

    @Test
    void shouldPreserveStatusMetadataWhenTimeoutOccursDuringBodyDecode() {
        List<HttpClientObserverEvent> observed = new ArrayList<>();
        WebClient webClient = WebClient.builder()
                .baseUrl("http://test.local")
                .exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.OK)
                        .body(Flux.error(ReadTimeoutException.INSTANCE))
                        .build()))
                .build();

        ReactiveClientInvocationHandler handler = createHandler(
                webClient,
                new ReactiveHttpClientProperties.ClientConfig(),
                observed::add);

        StepVerifier.create(invokeMono(handler, BodyTimeoutClient.class, "body"))
                .expectError(ReadTimeoutException.class)
                .verify();

        assertEquals(1, observed.size());
        assertEquals(200, observed.get(0).getStatusCode());
        assertEquals(ErrorCategory.TIMEOUT, observed.get(0).getErrorCategory());
    }

    @Test
    void shouldStreamItemsBeforeTimeoutWithoutBufferingResponseBody() {
        DefaultDataBufferFactory buffers = new DefaultDataBufferFactory();
        List<HttpClientObserverEvent> observed = new ArrayList<>();
        WebClient webClient = WebClient.builder()
                .baseUrl("http://test.local")
                .exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.OK)
                        .body(Flux.concat(
                                Mono.fromSupplier(() -> buffers.wrap("first".getBytes(StandardCharsets.UTF_8))),
                                Mono.error(ReadTimeoutException.INSTANCE)))
                        .build()))
                .build();

        ReactiveClientInvocationHandler handler = createHandler(
                webClient,
                new ReactiveHttpClientProperties.ClientConfig(),
                observed::add);

        StepVerifier.create(invokeDataBufferFlux(handler, StreamingTimeoutClient.class, "stream"))
                .expectNextMatches(buffer -> {
                    byte[] bytes = new byte[buffer.readableByteCount()];
                    buffer.read(bytes);
                    DataBufferUtils.release(buffer);
                    return "first".equals(new String(bytes, StandardCharsets.UTF_8));
                })
                .expectError(ReadTimeoutException.class)
                .verify();

        assertEquals(1, observed.size());
        assertEquals(ErrorCategory.TIMEOUT, observed.get(0).getErrorCategory());
        assertNull(observed.get(0).getResponseBody());
    }

    @SuppressWarnings("unchecked")
    private static ReactiveClientInvocationHandler createHandler(
            WebClient webClient, ReactiveHttpClientProperties.ClientConfig clientConfig) {
        return createHandler(webClient, clientConfig, null);
    }

    @SuppressWarnings("unchecked")
    private static ReactiveClientInvocationHandler createHandler(
            WebClient webClient,
            ReactiveHttpClientProperties.ClientConfig clientConfig,
            HttpClientObserver observer) {
        ApplicationContext appCtx = mock(ApplicationContext.class);
        ObjectProvider<HttpClientObserver> observerProvider = mock(ObjectProvider.class);
        when(appCtx.getBeanProvider(HttpClientObserver.class)).thenReturn(observerProvider);
        when(observerProvider.getIfAvailable()).thenReturn(observer);

        return new ReactiveClientInvocationHandler(
                webClient,
                new MethodMetadataCache(),
                new RequestArgumentResolver(),
                new DefaultErrorDecoder(),
                clientConfig,
                "test-client",
                appCtx,
                new NoopResilienceOperatorApplier(),
                null,
                new ReactiveHttpClientProperties.ObservabilityConfig()
        );
    }

    private static DisposableServer slowServer(long delayMillis) {
        return HttpServer.create()
                .port(0)
                .route(routes -> routes.get("/slow", (request, response) ->
                        Mono.delay(java.time.Duration.ofMillis(delayMillis))
                                .then(response.sendString(Mono.just("ok")).then())))
                .bindNow();
    }

    private static ReactiveHttpClientProperties.ApiConfig api(String method, String path, long timeoutMs) {
        ReactiveHttpClientProperties.ApiConfig api = new ReactiveHttpClientProperties.ApiConfig();
        api.setMethod(method);
        api.setPath(path);
        api.setTimeoutMs(timeoutMs);
        return api;
    }

    @SuppressWarnings("unchecked")
    private static Mono<String> invokeNoTimeoutApi(ReactiveClientInvocationHandler handler) {
        try {
            java.lang.reflect.Method method = NoTimeoutApiClient.class.getMethod("slow");
            return (Mono<String>) handler.invoke(null, method, new Object[0]);
        } catch (Throwable t) {
            return Mono.error(t);
        }
    }

    @SuppressWarnings("unchecked")
    private static Mono<String> invokeClientRequestTimeoutApi(ReactiveClientInvocationHandler handler) {
        try {
            java.lang.reflect.Method method = ClientRequestTimeoutApiClient.class.getMethod("slow");
            return (Mono<String>) handler.invoke(null, method, new Object[0]);
        } catch (Throwable t) {
            return Mono.error(t);
        }
    }

    @SuppressWarnings("unchecked")
    private static Mono<String> invokeMono(
            ReactiveClientInvocationHandler handler, Class<?> clientType, String methodName) {
        try {
            java.lang.reflect.Method method = clientType.getMethod(methodName);
            return (Mono<String>) handler.invoke(null, method, new Object[0]);
        } catch (Throwable t) {
            return Mono.error(t);
        }
    }

    @SuppressWarnings("unchecked")
    private static Flux<DataBuffer> invokeDataBufferFlux(
            ReactiveClientInvocationHandler handler, Class<?> clientType, String methodName) {
        try {
            java.lang.reflect.Method method = clientType.getMethod(methodName);
            return (Flux<DataBuffer>) handler.invoke(null, method, new Object[0]);
        } catch (Throwable t) {
            return Flux.error(t);
        }
    }

    interface NoTimeoutApiClient {
        @GET("/slow")
        @TimeoutMs(0)
        Mono<String> slow();
    }

    interface ClientRequestTimeoutApiClient {
        @GET("/slow")
        Mono<String> slow();
    }

    interface ApiRefTimeoutClient {
        @ApiRef("slow-api")
        Mono<String> slow();
    }

    interface MethodTimeoutOverridesApiRefClient {
        @ApiRef("slow-api")
        @TimeoutMs(100)
        Mono<String> slow();
    }

    interface BodyTimeoutClient {
        @GET("/body-timeout")
        Mono<String> body();
    }

    interface StreamingTimeoutClient {
        @GET("/stream-timeout")
        Flux<DataBuffer> stream();
    }
}
