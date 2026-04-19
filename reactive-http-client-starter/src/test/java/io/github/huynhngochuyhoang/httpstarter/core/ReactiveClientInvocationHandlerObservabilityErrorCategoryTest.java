package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.annotation.GET;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import io.github.huynhngochuyhoang.httpstarter.exception.ErrorCategory;
import io.github.huynhngochuyhoang.httpstarter.exception.HttpClientException;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientObserver;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientObserverEvent;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

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

    private static ReactiveClientInvocationHandler createHandler(
            WebClient webClient,
            int resilienceTimeoutMs,
            HttpClientObserver observer) {
        ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();
        ReactiveHttpClientProperties.ResilienceConfig resilienceConfig = new ReactiveHttpClientProperties.ResilienceConfig();
        resilienceConfig.setEnabled(true);
        resilienceConfig.setTimeoutMs(resilienceTimeoutMs);
        config.setResilience(resilienceConfig);

        return new ReactiveClientInvocationHandler(
                webClient,
                new MethodMetadataCache(),
                new RequestArgumentResolver(),
                new DefaultErrorDecoder(),
                config,
                "test-client",
                mock(ApplicationContext.class),
                null,
                null,
                null,
                observer,
                new ReactiveHttpClientProperties.ObservabilityConfig()
        );
    }

    @SuppressWarnings("unchecked")
    private static Mono<String> invoke(ReactiveClientInvocationHandler handler) {
        try {
            Method method = TestClient.class.getMethod("call");
            return (Mono<String>) handler.invoke(null, method, new Object[0]);
        } catch (Throwable t) {
            return Mono.error(t);
        }
    }

    interface TestClient {
        @GET("/users")
        Mono<String> call();
    }
}
