package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.annotation.ApiRef;
import io.github.huynhngochuyhoang.httpstarter.annotation.GET;
import io.github.huynhngochuyhoang.httpstarter.annotation.PathVar;
import io.github.huynhngochuyhoang.httpstarter.annotation.QueryParam;
import io.github.huynhngochuyhoang.httpstarter.auth.AuthProvider;
import io.github.huynhngochuyhoang.httpstarter.auth.OutboundAuthFilter;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import io.github.huynhngochuyhoang.httpstarter.exception.AuthProviderException;
import io.github.huynhngochuyhoang.httpstarter.exception.ErrorCategory;
import io.github.huynhngochuyhoang.httpstarter.exception.HttpClientException;
import io.github.huynhngochuyhoang.httpstarter.exception.RemoteServiceException;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientObserver;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientObserverEvent;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReactiveHttpClientLifecycleHookTest {

    @Test
    void shouldRunMultipleHooksInOrderAndIsolateHookFailures() throws Throwable {
        List<String> events = new ArrayList<>();
        ReactiveHttpClientLifecycleHook first = new RecordingHook("first", events);
        ReactiveHttpClientLifecycleHook failing = new FailingStartHook(events);
        ReactiveHttpClientLifecycleHook second = new RecordingHook("second", events);
        ReactiveClientInvocationHandler handler = createHandler(okWebClient(), List.of(first, failing, second),
                new NoopResilienceOperatorApplier(), defaultConfig());

        StepVerifier.create(invokeGet(handler, "42"))
                .expectNext("ok")
                .verifyComplete();

        assertEquals(List.of(
                "first:start:1:get",
                "failing:start",
                "second:start:1:get",
                "first:success:1:200",
                "second:success:1:200"), events);
    }

    @Test
    void shouldNotifyErrorHookWithDecodedException() throws Throwable {
        List<ReactiveHttpClientLifecycleContext> errors = new ArrayList<>();
        ReactiveHttpClientLifecycleHook hook = new ReactiveHttpClientLifecycleHook() {
            @Override
            public void onError(ReactiveHttpClientLifecycleContext context) {
                errors.add(context);
            }
        };
        WebClient webClient = WebClient.builder()
                .baseUrl("http://test.local")
                .exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.BAD_REQUEST)
                        .header(HttpHeaders.CONTENT_TYPE, "text/plain")
                        .body("bad request")
                        .build()))
                .build();
        ReactiveClientInvocationHandler handler = createHandler(webClient, List.of(hook),
                new NoopResilienceOperatorApplier(), defaultConfig());

        StepVerifier.create(invokeGet(handler, "42"))
                .expectError(HttpClientException.class)
                .verify();

        assertEquals(1, errors.size());
        ReactiveHttpClientLifecycleContext context = errors.get(0);
        assertEquals("test-client", context.clientName());
        assertEquals("get", context.apiName());
        assertEquals(400, context.statusCode());
        assertInstanceOf(HttpClientException.class, context.error());
    }

    @Test
    void authFailureLifecycleContextUsesLogicalClientWithoutCredentials() throws Throwable {
        List<ReactiveHttpClientLifecycleContext> errors = new ArrayList<>();
        ReactiveHttpClientLifecycleHook hook = new ReactiveHttpClientLifecycleHook() {
            @Override
            public void onError(ReactiveHttpClientLifecycleContext context) {
                errors.add(context);
            }
        };
        AuthProvider authProvider = request -> Mono.error(new AuthProviderException(
                request.clientName(), "OAuth2 token endpoint returned HTTP 401"));
        WebClient webClient = WebClient.builder()
                .baseUrl("http://test.local")
                .filter(new OutboundAuthFilter("test-client", authProvider))
                .exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.OK).build()))
                .build();
        ReactiveClientInvocationHandler handler = createHandler(webClient, List.of(hook),
                new NoopResilienceOperatorApplier(), defaultConfig());

        StepVerifier.create(invokeGet(handler, "42"))
                .expectError(AuthProviderException.class)
                .verify();

        assertEquals(1, errors.size());
        ReactiveHttpClientLifecycleContext context = errors.get(0);
        assertEquals("test-client", context.clientName());
        assertEquals("test-client", assertInstanceOf(
                AuthProviderException.class, context.error()).getClientName());
        assertEquals(Map.of(), context.headers());
        assertNull(context.requestUrl());
    }

    @Test
    void shouldNotifyRetryAttemptBoundary() throws Throwable {
        List<String> events = new ArrayList<>();
        List<HttpClientObserverEvent> observed = new ArrayList<>();
        AtomicInteger calls = new AtomicInteger();
        WebClient webClient = WebClient.builder()
                .baseUrl("http://test.local")
                .exchangeFunction(request -> {
                    if (calls.incrementAndGet() == 1) {
                        return Mono.error(new IllegalStateException("first attempt failed"));
                    }
                    return Mono.just(ClientResponse.create(HttpStatus.OK)
                            .header(HttpHeaders.CONTENT_TYPE, "text/plain")
                            .body("ok")
                            .build());
                })
                .build();
        ReactiveClientInvocationHandler handler = createHandler(webClient, List.of(new RecordingHook("hook", events)),
                retryOnceApplier(), retryConfig(), observed::add);

        StepVerifier.create(invokeGet(handler, "42"))
                .expectNext("ok")
                .verifyComplete();

        assertEquals(List.of(
                "hook:start:1:get",
                "hook:retry:2:get",
                "hook:success:2:200"), events);
        assertEquals(1, observed.size());
        assertEquals(2, observed.get(0).getAttemptCount());
        assertEquals(null, observed.get(0).getError());
    }

    @Test
    void shouldNotifyRetryExhaustionOnce() throws Throwable {
        List<String> events = new ArrayList<>();
        List<HttpClientObserverEvent> observed = new ArrayList<>();
        WebClient webClient = WebClient.builder()
                .baseUrl("http://test.local")
                .exchangeFunction(request -> Mono.error(new IllegalStateException("downstream failed")))
                .build();
        ReactiveClientInvocationHandler handler = createHandler(webClient, List.of(new RecordingHook("hook", events)),
                retryOnceApplier(), retryConfig(), observed::add);

        StepVerifier.create(invokeGet(handler, "42"))
                .expectError(IllegalStateException.class)
                .verify();

        assertEquals(List.of(
                "hook:start:1:get",
                "hook:retry:2:get",
                "hook:error:2:null:IllegalStateException"), events);
        assertEquals(1, observed.size());
        assertEquals(2, observed.get(0).getAttemptCount());
        assertInstanceOf(IllegalStateException.class, observed.get(0).getError());
    }

    @Test
    void shouldNotifyCancellationOnceWhenCancelledDuringRetryAttempt() throws Throwable {
        List<String> events = new ArrayList<>();
        List<HttpClientObserverEvent> observed = new ArrayList<>();
        AtomicInteger calls = new AtomicInteger();
        WebClient webClient = WebClient.builder()
                .baseUrl("http://test.local")
                .exchangeFunction(request -> calls.incrementAndGet() == 1
                        ? Mono.error(new IllegalStateException("first attempt failed"))
                        : Mono.never())
                .build();
        ReactiveClientInvocationHandler handler = createHandler(webClient, List.of(new RecordingHook("hook", events)),
                retryOnceApplier(), retryConfig(), observed::add);

        StepVerifier.create(invokeGet(handler, "42"))
                .expectSubscription()
                .thenAwait(Duration.ofMillis(25))
                .thenCancel()
                .verify(Duration.ofSeconds(2));

        assertEquals(2, calls.get());
        assertEquals(List.of(
                "hook:start:1:get",
                "hook:retry:2:get",
                "hook:cancel:2"), events);
        assertEquals(1, observed.size());
        assertEquals(2, observed.get(0).getAttemptCount());
        assertInstanceOf(CancellationException.class, observed.get(0).getError());
    }

    @Test
    void shouldNotifyMapperFallbackErrorOnceAfterRetry() throws Throwable {
        List<String> events = new ArrayList<>();
        List<HttpClientObserverEvent> observed = new ArrayList<>();
        AtomicInteger calls = new AtomicInteger();
        WebClient webClient = WebClient.builder()
                .baseUrl("http://test.local")
                .exchangeFunction(request -> {
                    if (calls.incrementAndGet() == 1) {
                        return Mono.error(new IllegalStateException("first attempt failed"));
                    }
                    return Mono.just(ClientResponse.create(HttpStatus.BAD_GATEWAY)
                            .header(HttpHeaders.CONTENT_TYPE, "text/plain")
                            .body("not-json")
                            .build());
                })
                .build();
        DefaultErrorDecoder decoder = new DefaultErrorDecoder("test-client", List.of(context -> {
            throw new IllegalArgumentException("invalid structured body");
        }));
        ReactiveClientInvocationHandler handler = createHandler(webClient, List.of(new RecordingHook("hook", events)),
                retryOnceApplier(), retryConfig(), observed::add, decoder);

        StepVerifier.create(invokeGet(handler, "42"))
                .expectError(RemoteServiceException.class)
                .verify();

        assertEquals(2, calls.get());
        assertEquals(List.of(
                "hook:start:1:get",
                "hook:retry:2:get",
                "hook:error:2:502:RemoteServiceException"), events);
        assertEquals(1, observed.size());
        assertEquals(2, observed.get(0).getAttemptCount());
        assertEquals(ErrorCategory.SERVER_ERROR, observed.get(0).getErrorCategory());
        assertInstanceOf(RemoteServiceException.class, observed.get(0).getError());
    }

    @Test
    void shouldNotifyLifecycleAndObserverOnceWhenBulkheadRejects() throws Throwable {
        List<String> events = new ArrayList<>();
        List<HttpClientObserverEvent> observed = new ArrayList<>();
        Throwable rejection = BulkheadFullException.createBulkheadFullException(Bulkhead.ofDefaults("orders"));
        ReactiveClientInvocationHandler handler = createHandler(okWebClient(), List.of(new RecordingHook("hook", events)),
                bulkheadRejectingApplier(rejection), retryConfig(), observed::add);

        StepVerifier.create(invokeGet(handler, "42"))
                .expectError(BulkheadFullException.class)
                .verify();

        assertEquals(List.of("hook:error:0:null:BulkheadFullException"), events);
        assertEquals(1, observed.size());
        assertZeroAttemptResilienceEvent(observed.get(0), BulkheadFullException.class);
    }

    @Test
    void shouldNotifyLifecycleAndObserverOnceWhenRateLimiterRejects() throws Throwable {
        List<String> events = new ArrayList<>();
        List<HttpClientObserverEvent> observed = new ArrayList<>();
        Throwable rejection = RequestNotPermitted.createRequestNotPermitted(RateLimiter.ofDefaults("orders"));
        ReactiveClientInvocationHandler handler = createHandler(okWebClient(), List.of(new RecordingHook("hook", events)),
                rateLimiterRejectingApplier(rejection), retryConfig(), observed::add);

        StepVerifier.create(invokeGet(handler, "42"))
                .expectError(RequestNotPermitted.class)
                .verify();

        assertEquals(List.of("hook:error:0:null:RequestNotPermitted"), events);
        assertEquals(1, observed.size());
        assertZeroAttemptResilienceEvent(observed.get(0), RequestNotPermitted.class);
    }

    @Test
    void shouldNotifyLifecycleAndObserverOnceWhenCircuitBreakerRejects() throws Throwable {
        List<String> events = new ArrayList<>();
        List<HttpClientObserverEvent> observed = new ArrayList<>();
        CircuitBreaker circuitBreaker = CircuitBreaker.ofDefaults("orders");
        circuitBreaker.transitionToOpenState();
        Throwable rejection = CallNotPermittedException.createCallNotPermittedException(circuitBreaker);
        ReactiveClientInvocationHandler handler = createHandler(okWebClient(), List.of(new RecordingHook("hook", events)),
                circuitBreakerRejectingApplier(rejection), retryConfig(), observed::add);

        StepVerifier.create(invokeGet(handler, "42"))
                .expectError(CallNotPermittedException.class)
                .verify();

        assertEquals(List.of("hook:error:0:null:CallNotPermittedException"), events);
        assertEquals(1, observed.size());
        assertZeroAttemptResilienceEvent(observed.get(0), CallNotPermittedException.class);
    }

    @Test
    void shouldNotifyLifecycleAndObserverOnceWhenTimeoutOccurs() throws Throwable {
        List<String> events = new ArrayList<>();
        List<HttpClientObserverEvent> observed = new ArrayList<>();
        WebClient webClient = WebClient.builder()
                .baseUrl("http://test.local")
                .exchangeFunction(request -> Mono.error(io.netty.handler.timeout.ReadTimeoutException.INSTANCE))
                .build();
        ReactiveClientInvocationHandler handler = createHandler(webClient, List.of(new RecordingHook("hook", events)),
                new NoopResilienceOperatorApplier(), defaultConfig(), observed::add);

        StepVerifier.create(invokeGet(handler, "42"))
                .expectError(io.netty.handler.timeout.ReadTimeoutException.class)
                .verify();

        assertEquals(List.of("hook:start:1:get", "hook:error:1:null:ReadTimeoutException"), events);
        assertEquals(1, observed.size());
        assertEquals(ErrorCategory.TIMEOUT, observed.get(0).getErrorCategory());
    }

    @Test
    void shouldNotifyCancellationHook() throws Throwable {
        List<String> events = new ArrayList<>();
        WebClient webClient = WebClient.builder()
                .baseUrl("http://test.local")
                .exchangeFunction(request -> Mono.never())
                .build();
        ReactiveClientInvocationHandler handler = createHandler(webClient, List.of(new RecordingHook("hook", events)),
                new NoopResilienceOperatorApplier(), defaultConfig());
        Method method = StreamingLifecycleClient.class.getMethod("stream");

        @SuppressWarnings("unchecked")
        Flux<String> flux = (Flux<String>) handler.invoke(null, method, new Object[0]);
        StepVerifier.create(flux)
                .thenCancel()
                .verify();

        assertEquals(List.of(
                "hook:start:1:stream",
                "hook:cancel:1"), events);
    }

    @Test
    void shouldNotifyLifecycleAndObserverOnceWhenCancelledBeforeResponse() throws Throwable {
        List<String> events = new ArrayList<>();
        List<HttpClientObserverEvent> observed = new ArrayList<>();
        WebClient webClient = WebClient.builder()
                .baseUrl("http://test.local")
                .exchangeFunction(request -> Mono.never())
                .build();
        ReactiveClientInvocationHandler handler = createHandler(webClient, List.of(new RecordingHook("hook", events)),
                new NoopResilienceOperatorApplier(), defaultConfig(), observed::add);

        StepVerifier.create(invokeGet(handler, "42"))
                .expectSubscription()
                .thenCancel()
                .verify(Duration.ofSeconds(2));

        assertEquals(List.of(
                "hook:start:1:get",
                "hook:cancel:1"), events);
        assertEquals(1, observed.size());
        HttpClientObserverEvent event = observed.get(0);
        assertEquals(null, event.getStatusCode());
        assertInstanceOf(CancellationException.class, event.getError());
    }

    @Test
    void shouldNotifyLifecycleAndObserverOnceWhenCancelledDuringBodyRead() throws Throwable {
        List<String> events = new ArrayList<>();
        List<HttpClientObserverEvent> observed = new ArrayList<>();
        DefaultDataBufferFactory bufferFactory = new DefaultDataBufferFactory();
        WebClient webClient = WebClient.builder()
                .baseUrl("http://test.local")
                .exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.OK)
                        .header(HttpHeaders.CONTENT_TYPE, "text/plain")
                        .body(Flux.concat(
                                Mono.fromSupplier(() -> bufferFactory.wrap("first".getBytes(StandardCharsets.UTF_8))),
                                Mono.never()))
                        .build()))
                .build();
        ReactiveClientInvocationHandler handler = createHandler(webClient, List.of(new RecordingHook("hook", events)),
                new NoopResilienceOperatorApplier(), defaultConfig(), observed::add);
        Method method = DataBufferLifecycleClient.class.getMethod("stream");

        @SuppressWarnings("unchecked")
        Flux<DataBuffer> flux = (Flux<DataBuffer>) handler.invoke(null, method, new Object[0]);
        StepVerifier.create(flux)
                .expectNextMatches(buffer -> buffer.readableByteCount() == 5)
                .thenCancel()
                .verify(Duration.ofSeconds(2));

        assertEquals(List.of(
                "hook:start:1:stream",
                "hook:cancel:1"), events);
        assertEquals(1, observed.size());
        HttpClientObserverEvent event = observed.get(0);
        assertEquals(200, event.getStatusCode());
        assertInstanceOf(CancellationException.class, event.getError());
    }

    @Test
    void shouldUseApiRefFallbackForLifecycleAndObserverApiName() throws Throwable {
        List<ReactiveHttpClientLifecycleContext> starts = new ArrayList<>();
        List<HttpClientObserverEvent> observed = new ArrayList<>();
        ReactiveHttpClientLifecycleHook hook = new ReactiveHttpClientLifecycleHook() {
            @Override
            public void onStart(ReactiveHttpClientLifecycleContext context) {
                starts.add(context);
            }
        };
        ReactiveHttpClientProperties.ApiConfig api = new ReactiveHttpClientProperties.ApiConfig();
        api.setMethod("GET");
        api.setPath("/users/{id}");
        ReactiveHttpClientProperties.ClientConfig config = defaultConfig();
        config.setApis(Map.of("user.getById", api));
        ReactiveClientInvocationHandler handler = createHandler(okWebClient(), List.of(hook),
                new NoopResilienceOperatorApplier(), config, observed::add);
        Method method = ApiRefLifecycleClient.class.getMethod("get", String.class);

        @SuppressWarnings("unchecked")
        Mono<String> mono = (Mono<String>) handler.invoke(null, method, new Object[]{"42"});
        StepVerifier.create(mono)
                .expectNext("ok")
                .verifyComplete();

        assertEquals("user.getById", starts.get(0).apiName());
        assertEquals("user.getById", observed.get(0).getApiName());
    }

    @Test
    void shouldPreserveNullQueryElementsWhenLifecycleHookIsRegistered() throws Throwable {
        AtomicReference<ClientRequest> capturedRequest = new AtomicReference<>();
        List<ReactiveHttpClientLifecycleContext> starts = new ArrayList<>();
        ReactiveHttpClientLifecycleHook hook = new ReactiveHttpClientLifecycleHook() {
            @Override
            public void onStart(ReactiveHttpClientLifecycleContext context) {
                starts.add(context);
            }
        };
        WebClient webClient = WebClient.builder()
                .baseUrl("http://test.local")
                .exchangeFunction(request -> {
                    capturedRequest.set(request);
                    return Mono.just(ClientResponse.create(HttpStatus.OK)
                            .header(HttpHeaders.CONTENT_TYPE, "text/plain")
                            .body("ok")
                            .build());
                })
                .build();
        ReactiveClientInvocationHandler handler = createHandler(webClient, List.of(hook),
                new NoopResilienceOperatorApplier(), defaultConfig());
        Method method = QueryLifecycleClient.class.getMethod("search", List.class);

        @SuppressWarnings("unchecked")
        Mono<String> mono = (Mono<String>) handler.invoke(null, method, new Object[]{Arrays.asList("a", null, "b")});
        StepVerifier.create(mono)
                .expectNext("ok")
                .verifyComplete();

        assertEquals(Arrays.asList("a", null, "b"), starts.get(0).queryParams().get("tag"));
        assertEquals(List.of("a", "null", "b"),
                UriComponentsBuilder.fromUri(capturedRequest.get().url()).build().getQueryParams().get("tag"));
    }

    private static Mono<String> invokeGet(ReactiveClientInvocationHandler handler, String id) throws Throwable {
        Method method = LifecycleClient.class.getMethod("get", String.class);
        @SuppressWarnings("unchecked")
        Mono<String> mono = (Mono<String>) handler.invoke(null, method, new Object[]{id});
        return mono;
    }

    private static WebClient okWebClient() {
        return WebClient.builder()
                .baseUrl("http://test.local")
                .exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.OK)
                        .header(HttpHeaders.CONTENT_TYPE, "text/plain")
                        .body("ok")
                        .build()))
                .build();
    }

    private static ReactiveClientInvocationHandler createHandler(
            WebClient webClient,
            List<ReactiveHttpClientLifecycleHook> hooks,
            ResilienceOperatorApplier resilienceOperatorApplier,
            ReactiveHttpClientProperties.ClientConfig config) {
        return createHandler(webClient, hooks, resilienceOperatorApplier, config, null);
    }

    @SuppressWarnings("unchecked")
    private static ReactiveClientInvocationHandler createHandler(
            WebClient webClient,
            List<ReactiveHttpClientLifecycleHook> hooks,
            ResilienceOperatorApplier resilienceOperatorApplier,
            ReactiveHttpClientProperties.ClientConfig config,
            HttpClientObserver observer) {
        return createHandler(webClient, hooks, resilienceOperatorApplier, config, observer, new DefaultErrorDecoder());
    }

    @SuppressWarnings("unchecked")
    private static ReactiveClientInvocationHandler createHandler(
            WebClient webClient,
            List<ReactiveHttpClientLifecycleHook> hooks,
            ResilienceOperatorApplier resilienceOperatorApplier,
            ReactiveHttpClientProperties.ClientConfig config,
            HttpClientObserver observer,
            DefaultErrorDecoder errorDecoder) {
        ApplicationContext appCtx = mock(ApplicationContext.class);
        ObjectProvider<HttpClientObserver> observerProvider = mock(ObjectProvider.class);
        when(appCtx.getBeanProvider(HttpClientObserver.class)).thenReturn(observerProvider);
        when(observerProvider.orderedStream()).thenAnswer(invocation -> observer != null
                ? java.util.stream.Stream.of(observer)
                : java.util.stream.Stream.empty());
        when(observerProvider.getIfAvailable()).thenReturn(observer);

        ObjectProvider<ReactiveHttpClientLifecycleHook> hookProvider = mock(ObjectProvider.class);
        when(appCtx.getBeanProvider(ReactiveHttpClientLifecycleHook.class)).thenReturn(hookProvider);
        when(hookProvider.orderedStream()).thenAnswer(invocation -> hooks.stream());

        return new ReactiveClientInvocationHandler(
                webClient,
                new MethodMetadataCache(),
                new RequestArgumentResolver(),
                errorDecoder,
                config,
                "test-client",
                appCtx,
                resilienceOperatorApplier,
                TestJsonCodecs.jsonCodec(),
                new ReactiveHttpClientProperties.ObservabilityConfig()
        );
    }

    private static ReactiveHttpClientProperties.ClientConfig defaultConfig() {
        return new ReactiveHttpClientProperties.ClientConfig();
    }

    private static ReactiveHttpClientProperties.ClientConfig retryConfig() {
        ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();
        ReactiveHttpClientProperties.ResilienceConfig resilience = new ReactiveHttpClientProperties.ResilienceConfig();
        resilience.setEnabled(true);
        resilience.setRetry("retry-test");
        resilience.setRateLimiter("rate-limiter-test");
        resilience.setCircuitBreaker("circuit-breaker-test");
        resilience.setBulkhead("bulkhead-test");
        resilience.setRetryMethods(Set.of("GET"));
        config.setResilience(resilience);
        return config;
    }

    private static ResilienceOperatorApplier retryOnceApplier() {
        return new NoopResilienceOperatorApplier() {
            @Override
            public boolean isOperatorAvailable(InstanceType type) {
                return type == InstanceType.RETRY;
            }

            @Override
            public <T> Mono<T> applyRetry(Mono<T> mono, String instanceName) {
                return mono.retry(1);
            }
        };
    }

    private static ResilienceOperatorApplier bulkheadRejectingApplier(Throwable rejection) {
        return new NoopResilienceOperatorApplier() {
            @Override
            public boolean isOperatorAvailable(InstanceType type) {
                return type == InstanceType.BULKHEAD;
            }

            @Override
            public <T> Mono<T> applyBulkhead(Mono<T> mono, String instanceName) {
                return Mono.error(rejection);
            }
        };
    }

    private static ResilienceOperatorApplier rateLimiterRejectingApplier(Throwable rejection) {
        return new NoopResilienceOperatorApplier() {
            @Override
            public boolean isOperatorAvailable(InstanceType type) {
                return type == InstanceType.RATE_LIMITER;
            }

            @Override
            public <T> Mono<T> applyRateLimiter(Mono<T> mono, String instanceName) {
                return Mono.error(rejection);
            }
        };
    }

    private static ResilienceOperatorApplier circuitBreakerRejectingApplier(Throwable rejection) {
        return new NoopResilienceOperatorApplier() {
            @Override
            public boolean isOperatorAvailable(InstanceType type) {
                return type == InstanceType.CIRCUIT_BREAKER;
            }

            @Override
            public <T> Mono<T> applyCircuitBreaker(Mono<T> mono, String instanceName) {
                return Mono.error(rejection);
            }
        };
    }

    private static void assertZeroAttemptResilienceEvent(
            HttpClientObserverEvent event,
            Class<? extends Throwable> errorType) {
        assertEquals(ErrorCategory.RESILIENCE_ERROR, event.getErrorCategory());
        assertInstanceOf(errorType, event.getError());
        assertEquals(0, event.getAttemptCount());
        assertNull(event.getStatusCode());
        assertNull(event.getRequestUrl());
        assertTrue(event.getRequestHeaders().isEmpty());
        assertNull(event.getFailureStage());
        assertTrue(event.getDurationMs() >= 0 && event.getDurationMs() < 5_000,
                "pre-attempt rejection duration must be finite and near-immediate");
    }

    interface LifecycleClient {
        @GET("/items/{id}")
        Mono<String> get(@PathVar("id") String id);
    }

    interface ApiRefLifecycleClient {
        @ApiRef("user.getById")
        Mono<String> get(@PathVar("id") String id);
    }

    interface StreamingLifecycleClient {
        @GET("/stream")
        Flux<String> stream();
    }

    interface DataBufferLifecycleClient {
        @GET("/stream")
        Flux<DataBuffer> stream();
    }

    interface QueryLifecycleClient {
        @GET("/search")
        Mono<String> search(@QueryParam("tag") List<String> tags);
    }

    static final class RecordingHook implements ReactiveHttpClientLifecycleHook {
        private final String name;
        private final List<String> events;

        RecordingHook(String name, List<String> events) {
            this.name = name;
            this.events = events;
        }

        @Override
        public void onStart(ReactiveHttpClientLifecycleContext context) {
            events.add(name + ":start:" + context.attemptNumber() + ":" + context.apiName());
        }

        @Override
        public void onRetryAttempt(ReactiveHttpClientLifecycleContext context) {
            events.add(name + ":retry:" + context.attemptNumber() + ":" + context.apiName());
        }

        @Override
        public void onSuccess(ReactiveHttpClientLifecycleContext context) {
            events.add(name + ":success:" + context.attemptNumber() + ":" + context.statusCode());
        }

        @Override
        public void onCancel(ReactiveHttpClientLifecycleContext context) {
            events.add(name + ":cancel:" + context.attemptNumber());
        }

        @Override
        public void onError(ReactiveHttpClientLifecycleContext context) {
            events.add(name + ":error:" + context.attemptNumber() + ":"
                    + context.statusCode() + ":" + context.error().getClass().getSimpleName());
        }
    }

    static final class FailingStartHook implements ReactiveHttpClientLifecycleHook {
        private final List<String> events;

        FailingStartHook(List<String> events) {
            this.events = events;
        }

        @Override
        public void onStart(ReactiveHttpClientLifecycleContext context) {
            events.add("failing:start");
            throw new IllegalStateException("hook failed");
        }
    }
}
