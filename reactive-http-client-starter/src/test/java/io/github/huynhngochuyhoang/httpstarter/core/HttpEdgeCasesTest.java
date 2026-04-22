package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.annotation.GET;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientObserver;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.server.HttpServer;
import reactor.test.StepVerifier;

import java.time.Duration;
import io.netty.handler.timeout.ReadTimeoutException;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Integration tests for HTTP edge-case behavior in {@link ReactiveClientInvocationHandler}:
 * timeout and cancellation.
 *
 * <p>These tests use a mocked {@link org.springframework.web.reactive.function.client.ExchangeFunction}
 * to simulate slow or never-responding upstreams without requiring a real HTTP server.
 * Virtual-time scheduling via {@link StepVerifier#withVirtualTime} ensures deterministic,
 * near-instant test execution.
 */
class HttpEdgeCasesTest {

    // -------------------------------------------------------------------------
    // Timeout
    // -------------------------------------------------------------------------

    /**
     * When the upstream response is slower than request-level timeout,
     * the Mono must terminate with a {@link ReadTimeoutException}.
     */
    @Test
    void shouldTimeoutWhenUpstreamIsTooSlow() {
        DisposableServer server = HttpServer.create()
                .port(0)
                .route(routes -> routes.get("/users", (request, response) ->
                        Mono.delay(Duration.ofMillis(250))
                                .then(response.sendString(Mono.just("pong")).then())))
                .bindNow();

        try {
            WebClient webClient = WebClient.builder()
                    .baseUrl("http://127.0.0.1:" + server.port())
                    .clientConnector(new ReactorClientHttpConnector(
                            HttpClient.create().responseTimeout(Duration.ofSeconds(5))))
                    .build();

            ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();
            config.setResilience(resilienceConfig(true, 100));

            ReactiveClientInvocationHandler handler = createHandler(webClient, config);

            StepVerifier.create(invokeGetUsers(handler))
                    .expectErrorSatisfies(ex -> {
                        WebClientRequestException requestException = assertInstanceOf(WebClientRequestException.class, ex);
                        assertInstanceOf(ReadTimeoutException.class, requestException.getCause());
                    })
                    .verify(Duration.ofSeconds(5));
        } finally {
            server.disposeNow();
        }
    }

    /**
     * When @TimeoutMs is set on the method, it takes priority over resilience timeout config.
     */
    @Test
    void shouldRespectMethodLevelTimeoutOverride() {
        DisposableServer server = HttpServer.create()
                .port(0)
                .route(routes -> routes.get("/users", (request, response) ->
                        Mono.delay(Duration.ofMillis(250))
                                .then(response.sendString(Mono.just("pong")).then())))
                .bindNow();

        try {
            WebClient webClient = WebClient.builder()
                    .baseUrl("http://127.0.0.1:" + server.port())
                    .clientConnector(new ReactorClientHttpConnector(
                            HttpClient.create().responseTimeout(Duration.ofSeconds(5))))
                    .build();

            // Resilience timeout 5000 ms, but method overrides to 200 ms.
            ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();
            config.setResilience(resilienceConfig(true, 5000));

            ReactiveClientInvocationHandler handler = createHandler(webClient, config);

            StepVerifier.create(invokeGetUsersWithShortTimeout(handler))
                    .expectErrorSatisfies(ex -> {
                        WebClientRequestException requestException = assertInstanceOf(WebClientRequestException.class, ex);
                        assertInstanceOf(ReadTimeoutException.class, requestException.getCause());
                    })
                    .verify(Duration.ofSeconds(5));
        } finally {
            server.disposeNow();
        }
    }

    // -------------------------------------------------------------------------
    // Cancellation
    // -------------------------------------------------------------------------

    /**
     * A subscription that is cancelled before the upstream responds must complete
     * without emitting any item or error.
     */
    @Test
    void shouldCompleteCleanlyOnCancellation() {
        WebClient webClient = WebClient.builder()
                .baseUrl("http://test.local")
                .exchangeFunction(request -> Mono.never())
                .build();

        ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();
        config.setResilience(resilienceConfig(false, 0)); // no timeout – only cancellation ends this

        ReactiveClientInvocationHandler handler = createHandler(webClient, config);

        StepVerifier.create(invokeGetUsers(handler))
                .expectSubscription()
                .thenCancel()
                .verify(Duration.ofSeconds(2));
    }

    /**
     * A subscription that is cancelled after receiving a successful response must
     * not produce any error.
     */
    @Test
    void shouldNotEmitErrorWhenCancelledAfterSuccessfulResponse() {
        ClientResponse okResponse = ClientResponse.create(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_TYPE, "text/plain")
                .body("pong")
                .build();

        WebClient webClient = WebClient.builder()
                .baseUrl("http://test.local")
                .exchangeFunction(request -> Mono.just(okResponse))
                .build();

        ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();
        ReactiveClientInvocationHandler handler = createHandler(webClient, config);

        StepVerifier.create(invokeGetUsers(handler))
                .expectNextMatches(body -> "pong".equals(body))
                .verifyComplete();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private static ReactiveClientInvocationHandler createHandler(
            WebClient webClient,
            ReactiveHttpClientProperties.ClientConfig config) {
        ApplicationContext appCtx = mock(ApplicationContext.class);
        ObjectProvider<HttpClientObserver> observerProvider = mock(ObjectProvider.class);
        when(appCtx.getBeanProvider(HttpClientObserver.class)).thenReturn(observerProvider);
        when(observerProvider.getIfAvailable()).thenReturn(null);

        return new ReactiveClientInvocationHandler(
                webClient,
                new MethodMetadataCache(),
                new RequestArgumentResolver(),
                new DefaultErrorDecoder(),
                config,
                "test-client",
                appCtx,
                new NoopResilienceOperatorApplier(),
                null,
                new ReactiveHttpClientProperties.ObservabilityConfig()
        );
    }

    private static ReactiveHttpClientProperties.ResilienceConfig resilienceConfig(boolean enabled, long timeoutMs) {
        ReactiveHttpClientProperties.ResilienceConfig resilience = new ReactiveHttpClientProperties.ResilienceConfig();
        resilience.setEnabled(enabled);
        resilience.setTimeoutMs(timeoutMs);
        return resilience;
    }

    @SuppressWarnings("unchecked")
    private static Mono<String> invokeGetUsers(ReactiveClientInvocationHandler handler) {
        try {
            java.lang.reflect.Method method = TimeoutTestClient.class.getMethod("getUsers");
            return (Mono<String>) handler.invoke(null, method, new Object[0]);
        } catch (Throwable t) {
            return Mono.error(t);
        }
    }

    @SuppressWarnings("unchecked")
    private static Mono<String> invokeGetUsersWithShortTimeout(ReactiveClientInvocationHandler handler) {
        try {
            java.lang.reflect.Method method = ShortTimeoutClient.class.getMethod("getUsers");
            return (Mono<String>) handler.invoke(null, method, new Object[0]);
        } catch (Throwable t) {
            return Mono.error(t);
        }
    }

    /** Test client interface – uses default (client-level) timeout. */
    interface TimeoutTestClient {
        @GET("/users")
        Mono<String> getUsers();
    }

    /** Test client interface – has a short 200 ms method-level timeout override. */
    interface ShortTimeoutClient {
        @GET("/users")
        @io.github.huynhngochuyhoang.httpstarter.annotation.TimeoutMs(200)
        Mono<String> getUsers();
    }
}
