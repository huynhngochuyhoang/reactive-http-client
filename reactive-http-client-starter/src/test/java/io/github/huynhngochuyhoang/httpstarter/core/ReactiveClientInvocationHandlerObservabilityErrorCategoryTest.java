package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.annotation.GET;
import io.github.huynhngochuyhoang.httpstarter.auth.AuthProvider;
import io.github.huynhngochuyhoang.httpstarter.auth.OutboundAuthFilter;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import io.github.huynhngochuyhoang.httpstarter.exception.ErrorCategory;
import io.github.huynhngochuyhoang.httpstarter.exception.AuthProviderException;
import io.github.huynhngochuyhoang.httpstarter.exception.HttpClientException;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientObserver;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientObserverEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.core.codec.DecodingException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.lang.reflect.Method;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReactiveClientInvocationHandlerObservabilityErrorCategoryTest {

    @Test
    void shouldObserveRateLimitedCategoryForHttp429() {
        ClientResponse response = ClientResponse.create(HttpStatus.TOO_MANY_REQUESTS)
                .body("rate-limited")
                .build();
        WebClient webClient = WebClient.builder()
                .baseUrl("http://test.local")
                .exchangeFunction(request -> Mono.just(response))
                .build();

        AtomicReference<HttpClientObserverEvent> observed = new AtomicReference<>();
        ReactiveClientInvocationHandler handler = createHandler(webClient, 5000, observed::set);

        StepVerifier.create(invoke(handler))
                .expectError(HttpClientException.class)
                .verify();

        HttpClientObserverEvent event = observed.get();
        assertNotNull(event);
        assertEquals(ErrorCategory.RATE_LIMITED, event.getErrorCategory());
    }

    @Test
    void shouldObserveTimeoutCategoryWhenRequestTimesOut() {
        WebClient webClient = WebClient.builder()
                .baseUrl("http://test.local")
                .exchangeFunction(request -> Mono.never())
                .build();

        AtomicReference<HttpClientObserverEvent> observed = new AtomicReference<>();
        ReactiveClientInvocationHandler handler = createHandler(webClient, 100, observed::set);

        StepVerifier.withVirtualTime(() -> invoke(handler))
                .expectSubscription()
                .thenAwait(Duration.ofMillis(101))
                .expectError(TimeoutException.class)
                .verify();

        HttpClientObserverEvent event = observed.get();
        assertNotNull(event);
        assertEquals(ErrorCategory.TIMEOUT, event.getErrorCategory());
    }

    @Test
    void shouldObserveAuthProviderErrorCategoryWhenAuthProviderFails() {
        AuthProvider authProvider = request -> Mono.error(new IllegalStateException("auth provider down"));
        WebClient webClient = WebClient.builder()
                .baseUrl("http://test.local")
                .filter(new OutboundAuthFilter("test-client", authProvider))
                .exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.OK).build()))
                .build();

        AtomicReference<HttpClientObserverEvent> observed = new AtomicReference<>();
        ReactiveClientInvocationHandler handler = createHandler(webClient, 5000, observed::set);

        StepVerifier.create(invoke(handler))
                .expectError(AuthProviderException.class)
                .verify();

        HttpClientObserverEvent event = observed.get();
        assertNotNull(event);
        assertEquals(ErrorCategory.AUTH_PROVIDER_ERROR, event.getErrorCategory());
    }

    @Test
    void shouldObserveResponseDecodeErrorCategoryWhenBodyToMonoFails() {
        ClientResponse response = ClientResponse.create(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body("\"VGVzdA==\"")
                .build();
        WebClient webClient = WebClient.builder()
                .baseUrl("http://test.local")
                .exchangeFunction(request -> Mono.just(response))
                .build();

        AtomicReference<HttpClientObserverEvent> observed = new AtomicReference<>();
        ReactiveClientInvocationHandler handler = createHandler(webClient, 5000, observed::set);

        StepVerifier.create(invokeMono(handler, MonoIntegerClient.class, "callInt"))
                .expectError()
                .verify();

        HttpClientObserverEvent event = observed.get();
        assertNotNull(event);
        assertEquals(ErrorCategory.RESPONSE_DECODE_ERROR, event.getErrorCategory());
    }

    @Test
    void shouldObserveResponseDecodeErrorCategoryWhenBodyToFluxFails() {
        ClientResponse response = ClientResponse.create(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body("[\"VGVzdA==\"]")
                .build();
        WebClient webClient = WebClient.builder()
                .baseUrl("http://test.local")
                .exchangeFunction(request -> Mono.just(response))
                .build();

        AtomicReference<HttpClientObserverEvent> observed = new AtomicReference<>();
        ReactiveClientInvocationHandler handler = createHandler(webClient, 5000, observed::set);

        StepVerifier.create(invokeFlux(handler, FluxIntegerClient.class, "callIntFlux"))
                .expectError()
                .verify();

        HttpClientObserverEvent event = observed.get();
        assertNotNull(event);
        assertEquals(ErrorCategory.RESPONSE_DECODE_ERROR, event.getErrorCategory());
    }

    @Test
    void shouldNotObserveResponseDecodeErrorCategoryWhenNoResponseStatusAvailable() {
        WebClient webClient = WebClient.builder()
                .baseUrl("http://test.local")
                .exchangeFunction(request -> Mono.error(new DecodingException("decode error without response status")))
                .build();

        AtomicReference<HttpClientObserverEvent> observed = new AtomicReference<>();
        ReactiveClientInvocationHandler handler = createHandler(webClient, 5000, observed::set);

        StepVerifier.create(invoke(handler))
                .expectError()
                .verify();

        HttpClientObserverEvent event = observed.get();
        assertNotNull(event);
        assertEquals(ErrorCategory.UNKNOWN, event.getErrorCategory());
    }

    @Test
    void shouldObserveConnectErrorCategoryWhenConnectExceptionOccurs() {
        WebClient webClient = WebClient.builder()
                .baseUrl("http://test.local")
                .exchangeFunction(request -> Mono.error(new ConnectException("connection refused")))
                .build();

        AtomicReference<HttpClientObserverEvent> observed = new AtomicReference<>();
        ReactiveClientInvocationHandler handler = createHandler(webClient, 5000, observed::set);

        StepVerifier.create(invoke(handler))
                .expectError(ConnectException.class)
                .verify();

        HttpClientObserverEvent event = observed.get();
        assertNotNull(event);
        assertEquals(ErrorCategory.CONNECT_ERROR, event.getErrorCategory());
    }

    @Test
    void shouldObserveUnknownHostCategoryWhenUnknownHostExceptionOccurs() {
        WebClient webClient = WebClient.builder()
                .baseUrl("http://test.local")
                .exchangeFunction(request -> Mono.error(new UnknownHostException("unknown host")))
                .build();

        AtomicReference<HttpClientObserverEvent> observed = new AtomicReference<>();
        ReactiveClientInvocationHandler handler = createHandler(webClient, 5000, observed::set);

        StepVerifier.create(invoke(handler))
                .expectError(UnknownHostException.class)
                .verify();

        HttpClientObserverEvent event = observed.get();
        assertNotNull(event);
        assertEquals(ErrorCategory.UNKNOWN_HOST, event.getErrorCategory());
    }

    private static ReactiveClientInvocationHandler createHandler(
            WebClient webClient,
            int resilienceTimeoutMs,
            HttpClientObserver observer) {
        ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();
        ReactiveHttpClientProperties.ResilienceConfig resilienceConfig = new ReactiveHttpClientProperties.ResilienceConfig();
        resilienceConfig.setEnabled(true);
        resilienceConfig.setTimeoutMs(resilienceTimeoutMs);
        config.setResilience(resilienceConfig);
        ApplicationContext applicationContext = mock(ApplicationContext.class);
        ObjectProvider<HttpClientObserver> observerProvider = mock(ObjectProvider.class);
        when(applicationContext.getBeanProvider(HttpClientObserver.class)).thenReturn(observerProvider);
        when(observerProvider.getIfAvailable()).thenReturn(observer);

        return new ReactiveClientInvocationHandler(
                webClient,
                new MethodMetadataCache(),
                new RequestArgumentResolver(),
                new DefaultErrorDecoder(),
                config,
                "test-client",
                applicationContext,
                new NoopResilienceOperatorApplier(),
                null,
                new ReactiveHttpClientProperties.ObservabilityConfig()
        );
    }

    @SuppressWarnings("unchecked")
    private static Mono<String> invoke(ReactiveClientInvocationHandler handler) {
        return invokeMono(handler, TestClient.class, "call");
    }

    @SuppressWarnings("unchecked")
    private static <T> Mono<T> invokeMono(ReactiveClientInvocationHandler handler, Class<?> clientType, String methodName) {
        try {
            Method method = clientType.getMethod(methodName);
            return (Mono<T>) handler.invoke(null, method, new Object[0]);
        } catch (Throwable t) {
            return Mono.error(t);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> Flux<T> invokeFlux(ReactiveClientInvocationHandler handler, Class<?> clientType, String methodName) {
        try {
            Method method = clientType.getMethod(methodName);
            return (Flux<T>) handler.invoke(null, method, new Object[0]);
        } catch (Throwable t) {
            return Flux.error(t);
        }
    }

    interface TestClient {
        @GET("/users")
        Mono<String> call();
    }

    interface MonoIntegerClient {
        @GET("/users")
        Mono<Integer> callInt();
    }

    interface FluxIntegerClient {
        @GET("/users")
        Flux<Integer> callIntFlux();
    }
}
