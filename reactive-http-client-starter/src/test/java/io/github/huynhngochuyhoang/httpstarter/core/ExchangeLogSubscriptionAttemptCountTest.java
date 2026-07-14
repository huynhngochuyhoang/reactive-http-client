package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.annotation.Body;
import io.github.huynhngochuyhoang.httpstarter.annotation.GET;
import io.github.huynhngochuyhoang.httpstarter.annotation.POST;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import io.github.huynhngochuyhoang.httpstarter.exception.RequestSerializationException;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientObserver;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExchangeLogSubscriptionAttemptCountTest {

    @Test
    void firstTryCallReportsOneSubscriptionAttempt() throws Throwable {
        AtomicInteger sends = new AtomicInteger();
        RecordingLogger logger = new RecordingLogger();
        WebClient webClient = webClient(request -> {
            sends.incrementAndGet();
            return Mono.just(ClientResponse.create(HttpStatus.OK).body("ok").build());
        });

        StepVerifier.create(invoke(createHandler(webClient, logger, Set.of(), null)))
                .expectNext("ok")
                .verifyComplete();

        assertEquals(1, sends.get());
        assertEquals(1, logger.contexts.size());
        assertEquals(1, logger.contexts.get(0).subscriptionAttemptCount());
    }

    @Test
    void retriedCallReportsOneTerminalLogWithFinalSubscriptionAttemptCount() throws Throwable {
        AtomicInteger sends = new AtomicInteger();
        RecordingLogger logger = new RecordingLogger();
        WebClient webClient = webClient(request -> {
            if (sends.incrementAndGet() == 1) {
                return Mono.just(ClientResponse.create(HttpStatus.INTERNAL_SERVER_ERROR).body("boom").build());
            }
            return Mono.just(ClientResponse.create(HttpStatus.OK).body("ok").build());
        });

        StepVerifier.create(invoke(createHandler(webClient, logger, Set.of("GET"), null)))
                .expectNext("ok")
                .verifyComplete();

        assertEquals(2, sends.get());
        assertEquals(1, logger.contexts.size());
        assertEquals(2, logger.contexts.get(0).subscriptionAttemptCount());
    }

    @Test
    void preNetworkSerializationFailureCountsSubscriptionAttemptWithoutSendingRequest() throws Throwable {
        AtomicInteger sends = new AtomicInteger();
        RecordingLogger logger = new RecordingLogger();
        WebClient webClient = webClient(request -> {
            sends.incrementAndGet();
            return Mono.just(ClientResponse.create(HttpStatus.OK).body("ok").build());
        });
        Map<String, Object> cyclic = new HashMap<>();
        cyclic.put("self", cyclic);

        StepVerifier.create(invokeWithBody(createHandler(webClient, logger, Set.of(), "serializationAuthProvider"), cyclic))
                .expectError(RequestSerializationException.class)
                .verify();

        assertEquals(0, sends.get());
        assertEquals(1, logger.contexts.size());
        HttpExchangeLogContext context = logger.contexts.get(0);
        assertEquals(1, context.subscriptionAttemptCount());
        assertNull(context.responseStatus());
        assertInstanceOf(RequestSerializationException.class, context.error());
    }

    private static WebClient webClient(org.springframework.web.reactive.function.client.ExchangeFunction exchangeFunction) {
        return WebClient.builder()
                .baseUrl("http://test.local")
                .exchangeFunction(exchangeFunction)
                .build();
    }

    @SuppressWarnings("unchecked")
    private static Mono<String> invoke(ReactiveClientInvocationHandler handler) throws Throwable {
        Method method = TestClient.class.getMethod("call");
        return (Mono<String>) handler.invoke(null, method, new Object[0]);
    }

    @SuppressWarnings("unchecked")
    private static Mono<String> invokeWithBody(ReactiveClientInvocationHandler handler, Map<String, Object> body) throws Throwable {
        Method method = TestClient.class.getMethod("create", Map.class);
        return (Mono<String>) handler.invoke(null, method, new Object[]{body});
    }

    @SuppressWarnings("unchecked")
    private static ReactiveClientInvocationHandler createHandler(
            WebClient webClient,
            RecordingLogger logger,
            Set<String> retryMethods,
            String authProviderName) {
        ApplicationContext context = mock(ApplicationContext.class);
        ObjectProvider<HttpClientObserver> observerProvider = mock(ObjectProvider.class);
        when(context.getBeanProvider(HttpClientObserver.class)).thenReturn(observerProvider);
        when(observerProvider.orderedStream()).thenAnswer(invocation -> java.util.stream.Stream.empty());
        when(observerProvider.getIfAvailable()).thenReturn(null);

        ObjectProvider<DefaultHttpExchangeLogger> loggerProvider = mock(ObjectProvider.class);
        when(context.getBeanProvider(DefaultHttpExchangeLogger.class)).thenReturn(loggerProvider);
        when(loggerProvider.getIfAvailable()).thenReturn(logger);

        ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();
        config.setExchangeLoggingEnabled(true);
        config.setAuthProvider(authProviderName);

        ResilienceOperatorApplier resilienceOperatorApplier = new NoopResilienceOperatorApplier();
        if (!retryMethods.isEmpty()) {
            ReactiveHttpClientProperties.ResilienceConfig resilience = new ReactiveHttpClientProperties.ResilienceConfig();
            resilience.setEnabled(true);
            resilience.setRetry("default");
            resilience.setRetryMethods(retryMethods);
            config.setResilience(resilience);
            RetryConfig retryConfig = RetryConfig.custom()
                    .maxAttempts(2)
                    .waitDuration(Duration.ZERO)
                    .build();
            resilienceOperatorApplier = new Resilience4jOperatorApplier(
                    null, RetryRegistry.of(retryConfig), null, null);
        }

        return new ReactiveClientInvocationHandler(
                webClient,
                new MethodMetadataCache(),
                new RequestArgumentResolver(),
                new DefaultErrorDecoder(),
                config,
                "test-client",
                context,
                resilienceOperatorApplier,
                TestJsonCodecs.jsonCodec(),
                new ReactiveHttpClientProperties.ObservabilityConfig());
    }

    interface TestClient {
        @GET("/items")
        Mono<String> call();

        @POST("/items")
        Mono<String> create(@Body Map<String, Object> body);
    }

    static final class RecordingLogger extends DefaultHttpExchangeLogger {
        private final List<HttpExchangeLogContext> contexts = new CopyOnWriteArrayList<>();

        @Override
        public void log(HttpExchangeLogContext context) {
            contexts.add(context);
        }
    }
}
