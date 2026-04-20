package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.annotation.PUT;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReactiveClientInvocationHandlerRetryMethodsTest {

    @Test
    void shouldRetryPutForMonoWhenConfigured() {
        AtomicInteger attempts = new AtomicInteger();
        WebClient webClient = WebClient.builder()
                .baseUrl("http://test.local")
                .exchangeFunction(request -> {
                    if (attempts.incrementAndGet() == 1) {
                        return Mono.just(ClientResponse.create(HttpStatus.INTERNAL_SERVER_ERROR).body("boom").build());
                    }
                    return Mono.just(ClientResponse.create(HttpStatus.OK).body("ok").build());
                })
                .build();

        ReactiveClientInvocationHandler handler = createHandler(webClient, Set.of("PUT"));

        StepVerifier.create(invokeMono(handler))
                .expectNext("ok")
                .verifyComplete();
        assertEquals(2, attempts.get());
    }

    @Test
    void shouldRetryPutForFluxWhenConfigured() {
        AtomicInteger attempts = new AtomicInteger();
        WebClient webClient = WebClient.builder()
                .baseUrl("http://test.local")
                .exchangeFunction(request -> {
                    if (attempts.incrementAndGet() == 1) {
                        return Mono.just(ClientResponse.create(HttpStatus.INTERNAL_SERVER_ERROR).body("boom").build());
                    }
                    return Mono.just(ClientResponse.create(HttpStatus.OK).body("ok").build());
                })
                .build();

        ReactiveClientInvocationHandler handler = createHandler(webClient, Set.of("PUT"));

        StepVerifier.create(invokeFlux(handler))
                .expectNext("ok")
                .verifyComplete();
        assertEquals(2, attempts.get());
    }

    private static ReactiveClientInvocationHandler createHandler(WebClient webClient, Set<String> retryMethods) {
        ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();
        ReactiveHttpClientProperties.ResilienceConfig resilience = new ReactiveHttpClientProperties.ResilienceConfig();
        resilience.setEnabled(true);
        resilience.setRetry("default");
        resilience.setRetryMethods(retryMethods);
        config.setResilience(resilience);

        ApplicationContext appCtx = mock(ApplicationContext.class);
        ObjectProvider<io.github.huynhngochuyhoang.httpstarter.observability.HttpClientObserver> observerProvider = mock(ObjectProvider.class);
        when(appCtx.getBeanProvider(io.github.huynhngochuyhoang.httpstarter.observability.HttpClientObserver.class))
                .thenReturn(observerProvider);
        when(observerProvider.getIfAvailable()).thenReturn(null);
        RetryConfig retryConfig = RetryConfig.custom()
                .maxAttempts(2)
                .waitDuration(Duration.ZERO)
                .build();

        return new ReactiveClientInvocationHandler(
                webClient,
                new MethodMetadataCache(),
                new RequestArgumentResolver(),
                new DefaultErrorDecoder(),
                config,
                "test-client",
                appCtx,
                new Resilience4jOperatorApplier(null, RetryRegistry.of(retryConfig), null),
                null,
                new ReactiveHttpClientProperties.ObservabilityConfig()
        );
    }

    @SuppressWarnings("unchecked")
    private static Mono<String> invokeMono(ReactiveClientInvocationHandler handler) {
        try {
            return (Mono<String>) handler.invoke(null, RetryClient.class.getMethod("updateOne"), new Object[0]);
        } catch (Throwable t) {
            return Mono.error(t);
        }
    }

    @SuppressWarnings("unchecked")
    private static Flux<String> invokeFlux(ReactiveClientInvocationHandler handler) {
        try {
            return (Flux<String>) handler.invoke(null, RetryClient.class.getMethod("updateMany"), new Object[0]);
        } catch (Throwable t) {
            return Flux.error(t);
        }
    }

    interface RetryClient {
        @PUT("/resource")
        Mono<String> updateOne();

        @PUT("/resource")
        Flux<String> updateMany();
    }
}
