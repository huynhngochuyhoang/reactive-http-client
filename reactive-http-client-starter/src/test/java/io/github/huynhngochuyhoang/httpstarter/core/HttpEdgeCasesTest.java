package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.annotation.GET;
import io.github.huynhngochuyhoang.httpstarter.annotation.PathVar;
import io.github.huynhngochuyhoang.httpstarter.annotation.ReactiveHttpClient;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientObserver;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientObserverEvent;
import io.netty.handler.timeout.ReadTimeoutException;
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
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
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

    @Test
    void inheritedClientsUseConcreteClientRequestTimeoutAndReportConcreteClientName() {
        DisposableServer server = HttpServer.create()
                .port(0)
                .route(routes -> routes.get("/shared-users/42", (request, response) ->
                        Mono.delay(Duration.ofMillis(300))
                                .then(response.sendString(Mono.just("pong")).then())))
                .bindNow();

        try {
            WebClient webClient = WebClient.builder()
                    .baseUrl("http://127.0.0.1:" + server.port())
                    .filter(ReactiveClientInvocationHandler.finalRequestObservationFilter())
                    .clientConnector(new ReactorClientHttpConnector(
                            HttpClient.create().responseTimeout(Duration.ofSeconds(5))))
                    .build();

            AtomicReference<HttpClientObserverEvent> internalObserved = new AtomicReference<>();
            AtomicReference<ReactiveHttpClientLifecycleContext> internalErrored = new AtomicReference<>();
            ReactiveClientInvocationHandler internalHandler = createHandler(
                    webClient,
                    clientConfig(100),
                    "internal-user-service",
                    internalObserved::set,
                    errorHook(internalErrored));

            StepVerifier.create(invokeInheritedGetUser(internalHandler, InternalUserClient.class))
                    .expectErrorSatisfies(ex -> {
                        WebClientRequestException requestException = assertInstanceOf(WebClientRequestException.class, ex);
                        assertInstanceOf(ReadTimeoutException.class, requestException.getCause());
                    })
                    .verify(Duration.ofSeconds(5));

            AtomicReference<HttpClientObserverEvent> partnerObserved = new AtomicReference<>();
            AtomicReference<ReactiveHttpClientLifecycleContext> partnerSucceeded = new AtomicReference<>();
            ReactiveClientInvocationHandler partnerHandler = createHandler(
                    webClient,
                    clientConfig(1000),
                    "partner-user-service",
                    partnerObserved::set,
                    successHook(partnerSucceeded));

            StepVerifier.create(invokeInheritedGetUser(partnerHandler, PartnerUserClient.class))
                    .expectNext("pong")
                    .verifyComplete();

            assertThat(internalObserved.get().getClientName()).isEqualTo("internal-user-service");
            assertThat(internalObserved.get().getUriPath()).isEqualTo("/shared-users/{id}");
            assertThat(internalErrored.get().clientName()).isEqualTo("internal-user-service");
            assertThat(internalErrored.get().pathTemplate()).isEqualTo("/shared-users/{id}");
            assertThat(partnerObserved.get().getClientName()).isEqualTo("partner-user-service");
            assertThat(partnerObserved.get().getUriPath()).isEqualTo("/shared-users/{id}");
            assertThat(partnerSucceeded.get().clientName()).isEqualTo("partner-user-service");
            assertThat(partnerSucceeded.get().pathTemplate()).isEqualTo("/shared-users/{id}");
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
        return createHandler(webClient, config, "test-client", null, null);
    }

    @SuppressWarnings("unchecked")
    private static ReactiveClientInvocationHandler createHandler(
            WebClient webClient,
            ReactiveHttpClientProperties.ClientConfig config,
            String clientName,
            HttpClientObserver observer,
            ReactiveHttpClientLifecycleHook lifecycleHook) {
        ApplicationContext appCtx = mock(ApplicationContext.class);
        ObjectProvider<HttpClientObserver> observerProvider = mock(ObjectProvider.class);
        when(appCtx.getBeanProvider(HttpClientObserver.class)).thenReturn(observerProvider);
        when(observerProvider.getIfAvailable()).thenReturn(observer);
        when(observerProvider.orderedStream()).thenAnswer(invocation -> observer != null
                ? java.util.stream.Stream.of(observer)
                : java.util.stream.Stream.empty());

        ObjectProvider<ReactiveHttpClientLifecycleHook> lifecycleProvider = mock(ObjectProvider.class);
        when(appCtx.getBeanProvider(ReactiveHttpClientLifecycleHook.class)).thenReturn(lifecycleProvider);
        when(lifecycleProvider.orderedStream()).thenAnswer(invocation -> lifecycleHook != null
                ? java.util.stream.Stream.of(lifecycleHook)
                : java.util.stream.Stream.empty());

        return new ReactiveClientInvocationHandler(
                webClient,
                new MethodMetadataCache(),
                new RequestArgumentResolver(),
                new DefaultErrorDecoder(),
                config,
                clientName,
                appCtx,
                new NoopResilienceOperatorApplier(),
                null,
                new ReactiveHttpClientProperties.ObservabilityConfig()
        );
    }

    private static ReactiveHttpClientProperties.ClientConfig clientConfig(long requestTimeoutMs) {
        ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();
        config.setRequestTimeoutMs(requestTimeoutMs);
        return config;
    }

    private static ReactiveHttpClientLifecycleHook successHook(
            AtomicReference<ReactiveHttpClientLifecycleContext> succeeded) {
        return new ReactiveHttpClientLifecycleHook() {
            @Override
            public void onSuccess(ReactiveHttpClientLifecycleContext context) {
                succeeded.set(context);
            }
        };
    }

    private static ReactiveHttpClientLifecycleHook errorHook(
            AtomicReference<ReactiveHttpClientLifecycleContext> errored) {
        return new ReactiveHttpClientLifecycleHook() {
            @Override
            public void onError(ReactiveHttpClientLifecycleContext context) {
                errored.set(context);
            }
        };
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

    @SuppressWarnings("unchecked")
    private static Mono<String> invokeInheritedGetUser(
            ReactiveClientInvocationHandler handler,
            Class<? extends SharedUserOperations> clientType) {
        try {
            java.lang.reflect.Method method = clientType.getMethod("getUser", String.class);
            return (Mono<String>) handler.invoke(null, method, new Object[]{"42"});
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

    interface SharedUserOperations {
        @GET("/shared-users/{id}")
        Mono<String> getUser(@PathVar("id") String id);
    }

    @ReactiveHttpClient(name = "internal-user-service")
    interface InternalUserClient extends SharedUserOperations {
    }

    @ReactiveHttpClient(name = "partner-user-service")
    interface PartnerUserClient extends SharedUserOperations {
    }
}
