package io.github.huynhngochuyhoang.httpstarter.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.huynhngochuyhoang.httpstarter.annotation.HeaderParam;
import io.github.huynhngochuyhoang.httpstarter.annotation.IdempotencyKey;
import io.github.huynhngochuyhoang.httpstarter.annotation.POST;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientObserver;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IdempotencyKeySupportTest {

    @Test
    void idempotencyKeyParameterWritesDefaultHeader() {
        AtomicReference<ClientRequest> captured = new AtomicReference<>();
        ReactiveClientInvocationHandler handler = createHandler(captureRequestWebClient(captured));

        StepVerifier.create(invoke(handler, "createWithTypedKey", "idem-1"))
                .expectNext("ok")
                .verifyComplete();

        assertEquals("idem-1", captured.get().headers().getFirst("Idempotency-Key"));
    }

    @Test
    void explicitHeaderParamWinsOverGeneratedIdempotencyKey() {
        AtomicReference<ClientRequest> captured = new AtomicReference<>();
        ReactiveClientInvocationHandler handler = createHandler(captureRequestWebClient(captured));

        StepVerifier.create(invoke(handler, "createWithGeneratedAndHeader", "caller-key"))
                .expectNext("ok")
                .verifyComplete();

        assertEquals("caller-key", captured.get().headers().getFirst("Idempotency-Key"));
    }

    @Test
    void defaultHeaderWinsOverGeneratedIdempotencyKey() {
        AtomicReference<ClientRequest> captured = new AtomicReference<>();
        ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();
        config.setDefaultHeaders(Map.of("idempotency-key", "default-key"));
        ReactiveClientInvocationHandler handler = createHandler(captureRequestWebClient(captured), config);

        StepVerifier.create(invoke(handler, "createWithGeneratedKey"))
                .expectNext("ok")
                .verifyComplete();

        assertEquals("default-key", captured.get().headers().getFirst("Idempotency-Key"));
    }

    @Test
    void requestContextWinsOverGeneratedIdempotencyKey() {
        AtomicReference<ClientRequest> captured = new AtomicReference<>();
        ReactiveClientInvocationHandler handler = createHandler(captureRequestWebClient(captured));

        StepVerifier.create(invoke(handler, "createWithGeneratedKey")
                        .contextWrite(ctx -> RequestContext.withIdempotencyKey(ctx, "context-key")))
                .expectNext("ok")
                .verifyComplete();

        assertEquals("context-key", captured.get().headers().getFirst("Idempotency-Key"));
    }

    @Test
    void generatedIdempotencyKeysAreScopedToOneInvocation() {
        Set<String> keys = ConcurrentHashMap.newKeySet();
        WebClient webClient = WebClient.builder()
                .baseUrl("http://test.local")
                .exchangeFunction(request -> {
                    keys.add(request.headers().getFirst("Idempotency-Key"));
                    return okResponse();
                })
                .build();
        ReactiveClientInvocationHandler handler = createHandler(webClient);

        StepVerifier.create(Flux.range(0, 16)
                        .flatMap(ignored -> invoke(handler, "createWithGeneratedKey"))
                        .then())
                .verifyComplete();

        assertEquals(16, keys.size());
        assertTrue(keys.stream().allMatch(key -> key != null && !key.isBlank()));
    }

    @Test
    void generatedIdempotencyKeysAreScopedToEachSubscription() {
        List<String> keys = new CopyOnWriteArrayList<>();
        AtomicInteger requests = new AtomicInteger();
        WebClient webClient = WebClient.builder()
                .baseUrl("http://test.local")
                .exchangeFunction(request -> {
                    requests.incrementAndGet();
                    keys.add(request.headers().getFirst("Idempotency-Key"));
                    return okResponse();
                })
                .build();
        ReactiveClientInvocationHandler handler = createHandler(webClient);

        Mono<String> request = invoke(handler, "createWithGeneratedKey");
        StepVerifier.create(Mono.when(request, request))
                .verifyComplete();

        assertEquals(2, requests.get());
        assertTrue(keys.stream().allMatch(key -> key != null && !key.isBlank()));
        assertEquals(2, Set.copyOf(keys).size());
    }

    @Test
    void generatedIdempotencyKeyIsStableAcrossRetryAttempts() {
        AtomicInteger attempts = new AtomicInteger();
        List<String> keys = new CopyOnWriteArrayList<>();
        WebClient webClient = WebClient.builder()
                .baseUrl("http://test.local")
                .exchangeFunction(request -> {
                    keys.add(request.headers().getFirst("Idempotency-Key"));
                    if (attempts.incrementAndGet() == 1) {
                        return Mono.just(ClientResponse.create(HttpStatus.INTERNAL_SERVER_ERROR).body("boom").build());
                    }
                    return okResponse();
                })
                .build();
        ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();
        ReactiveHttpClientProperties.ResilienceConfig resilience = new ReactiveHttpClientProperties.ResilienceConfig();
        resilience.setEnabled(true);
        resilience.setRetry("default");
        resilience.setRetryMethods(Set.of("POST"));
        config.setResilience(resilience);
        ReactiveClientInvocationHandler handler = createHandler(webClient, config, retryApplier());

        StepVerifier.create(invoke(handler, "createWithGeneratedKey"))
                .expectNext("ok")
                .verifyComplete();

        assertEquals(2, keys.size());
        assertEquals(keys.get(0), keys.get(1));
    }

    @Test
    void lifecycleAttemptHooksSeeGeneratedIdempotencyKeyHeader() {
        AtomicInteger attempts = new AtomicInteger();
        List<String> keys = new CopyOnWriteArrayList<>();
        List<ReactiveHttpClientLifecycleContext> starts = new CopyOnWriteArrayList<>();
        List<ReactiveHttpClientLifecycleContext> retries = new CopyOnWriteArrayList<>();
        ReactiveHttpClientLifecycleHook hook = new ReactiveHttpClientLifecycleHook() {
            @Override
            public void onStart(ReactiveHttpClientLifecycleContext context) {
                starts.add(context);
            }

            @Override
            public void onRetryAttempt(ReactiveHttpClientLifecycleContext context) {
                retries.add(context);
            }
        };
        WebClient webClient = WebClient.builder()
                .baseUrl("http://test.local")
                .exchangeFunction(request -> {
                    keys.add(request.headers().getFirst("Idempotency-Key"));
                    if (attempts.incrementAndGet() == 1) {
                        return Mono.just(ClientResponse.create(HttpStatus.INTERNAL_SERVER_ERROR).body("boom").build());
                    }
                    return okResponse();
                })
                .build();
        ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();
        ReactiveHttpClientProperties.ResilienceConfig resilience = new ReactiveHttpClientProperties.ResilienceConfig();
        resilience.setEnabled(true);
        resilience.setRetry("default");
        resilience.setRetryMethods(Set.of("POST"));
        config.setResilience(resilience);
        ReactiveClientInvocationHandler handler = createHandler(webClient, config, retryApplier(), List.of(hook));

        StepVerifier.create(invoke(handler, "createWithGeneratedKey"))
                .expectNext("ok")
                .verifyComplete();

        assertEquals(2, keys.size());
        assertEquals(1, starts.size());
        assertEquals(1, retries.size());
        assertEquals(keys.get(0), starts.get(0).headers().get("Idempotency-Key"));
        assertEquals(keys.get(1), retries.get(0).headers().get("Idempotency-Key"));
        assertEquals(keys.get(0), keys.get(1));
    }

    @Test
    void customizerFilterCanOverrideGeneratedIdempotencyKey() {
        AtomicReference<ClientRequest> captured = new AtomicReference<>();
        WebClient webClient = WebClient.builder()
                .baseUrl("http://test.local")
                .filter((request, next) -> next.exchange(ClientRequest.from(request)
                        .headers(headers -> headers.set("Idempotency-Key", "customizer-key"))
                        .build()))
                .exchangeFunction(request -> {
                    captured.set(request);
                    return okResponse();
                })
                .build();
        ReactiveClientInvocationHandler handler = createHandler(webClient);

        StepVerifier.create(invoke(handler, "createWithGeneratedKey"))
                .expectNext("ok")
                .verifyComplete();

        assertEquals("customizer-key", captured.get().headers().getFirst("Idempotency-Key"));
    }

    @Test
    void lifecycleSuccessSeesGeneratedIdempotencyKeyHeader() {
        AtomicReference<ClientRequest> captured = new AtomicReference<>();
        List<ReactiveHttpClientLifecycleContext> successes = new CopyOnWriteArrayList<>();
        ReactiveHttpClientLifecycleHook hook = new ReactiveHttpClientLifecycleHook() {
            @Override
            public void onSuccess(ReactiveHttpClientLifecycleContext context) {
                successes.add(context);
            }
        };
        ReactiveClientInvocationHandler handler = createHandler(
                captureRequestWebClient(captured),
                new ReactiveHttpClientProperties.ClientConfig(),
                new NoopResilienceOperatorApplier(),
                List.of(hook));

        StepVerifier.create(invoke(handler, "createWithGeneratedKey"))
                .expectNext("ok")
                .verifyComplete();

        assertEquals(1, successes.size());
        assertEquals(captured.get().headers().getFirst("Idempotency-Key"),
                successes.get(0).headers().get("Idempotency-Key"));
    }

    @Test
    void generatedCustomHeaderUsesContextValueWithoutAddingDefaultHeader() {
        AtomicReference<ClientRequest> captured = new AtomicReference<>();
        ReactiveClientInvocationHandler handler = createHandler(captureRequestWebClient(captured));

        StepVerifier.create(invoke(handler, "createWithGeneratedCustomHeader")
                        .contextWrite(ctx -> RequestContext.withIdempotencyKey(ctx, "context-key")))
                .expectNext("ok")
                .verifyComplete();

        assertEquals("context-key", captured.get().headers().getFirst("X-Idempotency-Key"));
        assertNull(captured.get().headers().getFirst("Idempotency-Key"));
    }

    private static WebClient captureRequestWebClient(AtomicReference<ClientRequest> captured) {
        return WebClient.builder()
                .baseUrl("http://test.local")
                .exchangeFunction(request -> {
                    captured.set(request);
                    return okResponse();
                })
                .build();
    }

    private static Mono<ClientResponse> okResponse() {
        return Mono.just(ClientResponse.create(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_TYPE, "text/plain")
                .body("ok")
                .build());
    }

    @SuppressWarnings("unchecked")
    private static Mono<String> invoke(ReactiveClientInvocationHandler handler, String methodName, Object... args) {
        try {
            Method method = switch (methodName) {
                case "createWithTypedKey", "createWithGeneratedAndHeader" ->
                        IdempotencyClient.class.getMethod(methodName, String.class);
                default -> IdempotencyClient.class.getMethod(methodName);
            };
            return (Mono<String>) handler.invoke(null, method, args);
        } catch (Throwable t) {
            return Mono.error(t);
        }
    }

    private static ReactiveClientInvocationHandler createHandler(WebClient webClient) {
        return createHandler(webClient, new ReactiveHttpClientProperties.ClientConfig());
    }

    private static ReactiveClientInvocationHandler createHandler(WebClient webClient,
                                                                 ReactiveHttpClientProperties.ClientConfig config) {
        return createHandler(webClient, config, new NoopResilienceOperatorApplier());
    }

    private static ReactiveClientInvocationHandler createHandler(WebClient webClient,
                                                                 ReactiveHttpClientProperties.ClientConfig config,
                                                                 ResilienceOperatorApplier resilienceOperatorApplier) {
        return createHandler(webClient, config, resilienceOperatorApplier, List.of());
    }

    @SuppressWarnings("unchecked")
    private static ReactiveClientInvocationHandler createHandler(WebClient webClient,
                                                                 ReactiveHttpClientProperties.ClientConfig config,
                                                                 ResilienceOperatorApplier resilienceOperatorApplier,
                                                                 List<ReactiveHttpClientLifecycleHook> hooks) {
        ApplicationContext appCtx = mock(ApplicationContext.class);
        ObjectProvider<HttpClientObserver> observerProvider = mock(ObjectProvider.class);
        when(appCtx.getBeanProvider(HttpClientObserver.class)).thenReturn(observerProvider);
        when(observerProvider.getIfAvailable()).thenReturn(null);

        ObjectProvider<ReactiveHttpClientLifecycleHook> hookProvider = mock(ObjectProvider.class);
        when(appCtx.getBeanProvider(ReactiveHttpClientLifecycleHook.class)).thenReturn(hookProvider);
        when(hookProvider.orderedStream()).thenAnswer(invocation -> hooks.stream());

        return new ReactiveClientInvocationHandler(
                webClient,
                new MethodMetadataCache(),
                new RequestArgumentResolver(),
                new DefaultErrorDecoder(),
                config,
                "test-client",
                appCtx,
                resilienceOperatorApplier,
                new ObjectMapper(),
                new ReactiveHttpClientProperties.ObservabilityConfig());
    }

    private static ResilienceOperatorApplier retryApplier() {
        RetryConfig retryConfig = RetryConfig.custom()
                .maxAttempts(2)
                .waitDuration(Duration.ZERO)
                .build();
        return new Resilience4jOperatorApplier(null, RetryRegistry.of(retryConfig), null, null);
    }

    interface IdempotencyClient {
        @POST("/items")
        Mono<String> createWithTypedKey(@IdempotencyKey String key);

        @POST("/items")
        @IdempotencyKey
        Mono<String> createWithGeneratedKey();

        @POST("/items")
        @IdempotencyKey
        Mono<String> createWithGeneratedAndHeader(@HeaderParam("Idempotency-Key") String key);

        @POST("/items")
        @IdempotencyKey("X-Idempotency-Key")
        Mono<String> createWithGeneratedCustomHeader();
    }
}
