package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.annotation.GET;
import io.github.huynhngochuyhoang.httpstarter.annotation.TimeoutMs;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientObserver;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.server.HttpServer;
import reactor.test.StepVerifier;

import io.netty.handler.timeout.ReadTimeoutException;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

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
    void shouldApplyResilienceTimeoutAsRequestLevelOverrideWhenMethodTimeoutIsNotConfigured() {
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
            ReactiveHttpClientProperties.ResilienceConfig resilienceConfig = new ReactiveHttpClientProperties.ResilienceConfig();
            resilienceConfig.setEnabled(true);
            resilienceConfig.setTimeoutMs(1000);
            clientConfig.setResilience(resilienceConfig);

            ReactiveClientInvocationHandler handler = createHandler(webClient, clientConfig);

            StepVerifier.create(invokeResilienceTimeoutApi(handler))
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
            ReactiveHttpClientProperties.ResilienceConfig resilienceConfig = new ReactiveHttpClientProperties.ResilienceConfig();
            resilienceConfig.setEnabled(true);
            resilienceConfig.setTimeoutMs(150);
            clientConfig.setResilience(resilienceConfig);

            ReactiveClientInvocationHandler handler = createHandler(webClient, clientConfig);

            StepVerifier.create(invokeResilienceTimeoutApi(handler))
                    .expectErrorSatisfies(ex -> {
                        WebClientRequestException requestException = assertInstanceOf(WebClientRequestException.class, ex);
                        assertInstanceOf(ReadTimeoutException.class, requestException.getCause());
                    })
                    .verify();
        } finally {
            server.disposeNow();
        }
    }

    @SuppressWarnings("unchecked")
    private static ReactiveClientInvocationHandler createHandler(
            WebClient webClient, ReactiveHttpClientProperties.ClientConfig clientConfig) {
        ApplicationContext appCtx = mock(ApplicationContext.class);
        ObjectProvider<HttpClientObserver> observerProvider = mock(ObjectProvider.class);
        when(appCtx.getBeanProvider(HttpClientObserver.class)).thenReturn(observerProvider);
        when(observerProvider.getIfAvailable()).thenReturn(null);

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
    private static Mono<String> invokeResilienceTimeoutApi(ReactiveClientInvocationHandler handler) {
        try {
            java.lang.reflect.Method method = ResilienceTimeoutApiClient.class.getMethod("slow");
            return (Mono<String>) handler.invoke(null, method, new Object[0]);
        } catch (Throwable t) {
            return Mono.error(t);
        }
    }

    interface NoTimeoutApiClient {
        @GET("/slow")
        @TimeoutMs(0)
        Mono<String> slow();
    }

    interface ResilienceTimeoutApiClient {
        @GET("/slow")
        Mono<String> slow();
    }
}
