package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.annotation.GET;
import io.github.huynhngochuyhoang.httpstarter.annotation.LogHttpExchange;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import io.github.huynhngochuyhoang.httpstarter.exception.ErrorCategory;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientObserver;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientObserverEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.ConnectException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for Priority-7 housekeeping items:
 * <ul>
 *   <li>2.6 – Case-insensitive header view cached on {@link RequestArgumentResolver.ResolvedArgs}</li>
 *   <li>2.8 – {@link HttpExchangeLogger} resolved and cached on {@link MethodMetadata}</li>
 *   <li>2.9 – Bounded cause-chain traversal in error classification</li>
 * </ul>
 */
class HousekeepingTest {

    // -------------------------------------------------------------------------
    // 2.6 – ResolvedArgs.headersIgnoreCase()
    // -------------------------------------------------------------------------

    @Test
    void resolvedArgsBuildsCaseInsensitiveHeaderView() {
        // Headers with mixed-case names
        Map<String, List<String>> headers = Map.of(
                "Content-Type", List.of("application/json"),
                "ACCEPT", List.of("application/xml"),
                "x-custom-header", List.of("value"));

        RequestArgumentResolver.ResolvedArgs args =
                new RequestArgumentResolver.ResolvedArgs(Map.of(), Map.of(), headers, null);

        // All lookups should work regardless of case
        assertEquals("application/json", args.headersIgnoreCase().get("content-type"));
        assertEquals("application/json", args.headersIgnoreCase().get("CONTENT-TYPE"));
        assertEquals("application/json", args.headersIgnoreCase().get("Content-Type"));

        assertEquals("application/xml", args.headersIgnoreCase().get("Accept"));
        assertEquals("application/xml", args.headersIgnoreCase().get("accept"));

        assertEquals("value", args.headersIgnoreCase().get("X-Custom-Header"));
        assertEquals("value", args.headersIgnoreCase().get("x-custom-header"));
    }

    @Test
    void resolvedArgsIgnoreCaseViewIsEmptyForEmptyHeaders() {
        // Empty headers map → view must also be empty
        RequestArgumentResolver.ResolvedArgs args =
                new RequestArgumentResolver.ResolvedArgs(Map.of(), Map.of(), Map.of(), null);

        assertNotNull(args.headersIgnoreCase());
        assertEquals(0, args.headersIgnoreCase().size());
    }

    @Test
    void invocationHandlerUsesIgnoreCaseViewForAcceptHeaderCheck() {
        // The handler must not add a default Accept header when the caller supplies one
        // with a different casing (e.g. "ACCEPT" rather than "Accept").
        var captured = new java.util.concurrent.atomic.AtomicReference<
                org.springframework.web.reactive.function.client.ClientRequest>();
        WebClient webClient = WebClient.builder()
                .baseUrl("http://test.local")
                .exchangeFunction(request -> {
                    captured.set(request);
                    return Mono.just(ClientResponse.create(HttpStatus.OK)
                            .header(HttpHeaders.CONTENT_TYPE, "text/plain")
                            .body("ok")
                            .build());
                })
                .build();

        ReactiveClientInvocationHandler handler = buildHandler(webClient);

        // Method supplies "ACCEPT" (upper-case) as a header parameter
        StepVerifier.create(invokeWithHeader(handler, "text/html"))
                .expectNext("ok")
                .verifyComplete();

        // The request must carry the caller-supplied value, not application/json
        List<String> acceptValues = captured.get().headers().get(HttpHeaders.ACCEPT);
        assertNotNull(acceptValues);
        assertEquals(1, acceptValues.size(), "exactly one Accept header expected");
        assertEquals("text/html", acceptValues.get(0));
    }

    // -------------------------------------------------------------------------
    // 2.8 – HttpExchangeLogger cached on MethodMetadata after first resolution
    // -------------------------------------------------------------------------

    @Test
    void resolvedExchangeLoggerIsNullBeforeFirstInvocation() {
        MethodMetadata meta = new MethodMetadata();
        assertNull(meta.getResolvedExchangeLogger(),
                "resolvedExchangeLogger must be null until the first invocation");
    }

    @Test
    void noopSentinelIsDistinctFromNull() {
        assertNotNull(MethodMetadata.noopExchangeLogger(),
                "noopExchangeLogger() sentinel must not be null");
    }

    @Test
    void resolveExchangeLoggerCachesInstanceOnMethodMetadataOnSubsequentInvocations() throws Throwable {
        // Client whose method has @LogHttpExchange(DefaultHttpExchangeLogger.class)
        WebClient webClient = WebClient.builder()
                .baseUrl("http://test.local")
                .exchangeFunction(req -> Mono.just(ClientResponse.create(HttpStatus.OK)
                        .header(HttpHeaders.CONTENT_TYPE, "text/plain")
                        .body("ok")
                        .build()))
                .build();

        ReactiveClientInvocationHandler handler = buildHandler(webClient);
        MethodMetadataCache cache = new MethodMetadataCache();
        Method loggedMethod = LoggedClient.class.getMethod("call");
        MethodMetadata meta = cache.get(loggedMethod);

        // Before any invocation the per-method slot is empty
        assertNull(meta.getResolvedExchangeLogger());

        // Trigger resolution by calling resolveExchangeLogger indirectly via invoke()
        // (We use a handler wired to a real MethodMetadataCache so the meta object is shared.)
        ReactiveClientInvocationHandler loggedHandler = buildHandlerWithCache(webClient, cache);
        StepVerifier.create((Mono<?>) loggedHandler.invoke(null, loggedMethod, new Object[0]))
                .expectNextCount(1)
                .verifyComplete();

        // Now the per-method slot must be populated (either noop sentinel or a real logger)
        assertNotNull(meta.getResolvedExchangeLogger(),
                "resolvedExchangeLogger must be populated after the first invocation");

        // Invoke again – must return the same cached instance (identity equality)
        HttpExchangeLogger firstCached = meta.getResolvedExchangeLogger();
        StepVerifier.create((Mono<?>) loggedHandler.invoke(null, loggedMethod, new Object[0]))
                .expectNextCount(1)
                .verifyComplete();
        assertSame(firstCached, meta.getResolvedExchangeLogger(),
                "resolvedExchangeLogger must remain the same object on subsequent invocations");
    }

    @Test
    void interfaceLevelLogHttpExchangeUsesActualProxyInterfaceAndDoesNotLeakAcrossClients() throws Throwable {
        WebClient webClient = WebClient.builder()
                .baseUrl("http://test.local")
                .exchangeFunction(req -> Mono.just(ClientResponse.create(HttpStatus.OK)
                        .header(HttpHeaders.CONTENT_TYPE, "text/plain")
                        .body("ok")
                        .build()))
                .build();

        MethodMetadataCache sharedCache = new MethodMetadataCache();
        Method inheritedMethod = SharedBaseApi.class.getMethod("call");
        MethodMetadata meta = sharedCache.get(inheritedMethod);
        assertNull(meta.getResolvedExchangeLogger(), "method metadata starts with empty per-method cache");

        CountingInheritedClientLogger.LOG_COUNT.set(0);

        ReactiveClientInvocationHandler loggedHandler = buildHandlerWithCache(webClient, sharedCache);
        Object loggedProxy = Proxy.newProxyInstance(
                InheritedLoggedClient.class.getClassLoader(),
                new Class[]{InheritedLoggedClient.class},
                (proxy, method, args) -> null);
        StepVerifier.create((Mono<?>) loggedHandler.invoke(loggedProxy, inheritedMethod, new Object[0]))
                .expectNextCount(1)
                .verifyComplete();
        assertEquals(1, CountingInheritedClientLogger.LOG_COUNT.get(),
                "logger should be used when extending client interface is annotated");

        ReactiveClientInvocationHandler unloggedHandler = buildHandlerWithCache(webClient, sharedCache);
        Object unloggedProxy = Proxy.newProxyInstance(
                InheritedPlainClient.class.getClassLoader(),
                new Class[]{InheritedPlainClient.class},
                (proxy, method, args) -> null);
        StepVerifier.create((Mono<?>) unloggedHandler.invoke(unloggedProxy, inheritedMethod, new Object[0]))
                .expectNextCount(1)
                .verifyComplete();
        assertEquals(1, CountingInheritedClientLogger.LOG_COUNT.get(),
                "logger usage from one client must not leak to another client sharing the same base method");
        assertNull(meta.getResolvedExchangeLogger(),
                "interface-level logger should not be stored on shared MethodMetadata");
    }

    @Test
    void clientWideLogExchangeUsesDefaultExchangeLoggerWithConfiguredPreset() throws Throwable {
        WebClient webClient = WebClient.builder()
                .baseUrl("http://test.local")
                .exchangeFunction(req -> Mono.just(ClientResponse.create(HttpStatus.OK)
                        .header(HttpHeaders.CONTENT_TYPE, "text/plain")
                        .body("ok")
                        .build()))
                .build();
        ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();
        config.setExchangeLoggingEnabled(true);
        config.setLogPreset(ReactiveHttpClientProperties.LogPreset.HEADERS);
        AtomicReference<HttpExchangeLogContext> logged = new AtomicReference<>();

        ReactiveClientInvocationHandler handler = buildHandlerWithCache(
                webClient, new MethodMetadataCache(), config, new RecordingDefaultHttpExchangeLogger(logged));

        StepVerifier.create(invokeViaSimpleClient(handler))
                .expectNextCount(1)
                .verifyComplete();

        assertNotNull(logged.get(), "client-wide log-exchange should route through DefaultHttpExchangeLogger");
        assertEquals(ReactiveHttpClientProperties.LogPreset.HEADERS, logged.get().logPreset());
        assertEquals(List.of("text/plain"), logged.get().responseHeaders().get(HttpHeaders.CONTENT_TYPE));
    }


    @Test
    void exchangeLoggingUsesFinalRequestHeadersAddedByWebClientFilters() throws Throwable {
        AtomicReference<HttpExchangeLogContext> logged = new AtomicReference<>();
        WebClient webClient = WebClient.builder()
                .baseUrl("http://test.local")
                .filter((request, next) -> next.exchange(
                        org.springframework.web.reactive.function.client.ClientRequest.from(request)
                                .header("X-Request-ID", "req-123")
                                .header(HttpHeaders.AUTHORIZATION, "secret-token")
                                .build()))
                .filter(ReactiveClientInvocationHandler.finalRequestObservationFilter())
                .exchangeFunction(req -> Mono.just(ClientResponse.create(HttpStatus.OK)
                        .header(HttpHeaders.CONTENT_TYPE, "text/plain")
                        .body("ok")
                        .build()))
                .build();
        ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();
        config.setExchangeLoggingEnabled(true);
        config.setLogPreset(ReactiveHttpClientProperties.LogPreset.HEADERS);

        ReactiveClientInvocationHandler handler = buildHandlerWithCache(
                webClient, new MethodMetadataCache(), config, new RecordingDefaultHttpExchangeLogger(logged));

        StepVerifier.create(invokeViaSimpleClient(handler))
                .expectNext("ok")
                .verifyComplete();

        assertNotNull(logged.get());
        assertEquals("req-123", logged.get().requestHeaders().get("X-Request-ID"));
        assertEquals("secret-token", logged.get().requestHeaders().get(HttpHeaders.AUTHORIZATION));
        assertEquals("http://test.local/items", logged.get().requestUrl().toString());
    }

    @Test
    void observerReceivesFinalRequestMetadataAddedByWebClientFilters() {
        AtomicReference<HttpClientObserverEvent> observed = new AtomicReference<>();
        WebClient webClient = WebClient.builder()
                .baseUrl("http://test.local")
                .filter((request, next) -> next.exchange(
                        org.springframework.web.reactive.function.client.ClientRequest.from(request)
                                .header("X-Request-ID", "req-456")
                                .build()))
                .filter(ReactiveClientInvocationHandler.finalRequestObservationFilter())
                .exchangeFunction(req -> Mono.just(ClientResponse.create(HttpStatus.OK)
                        .header(HttpHeaders.CONTENT_TYPE, "text/plain")
                        .body("ok")
                        .build()))
                .build();

        ReactiveClientInvocationHandler handler = buildHandlerWithObserver(webClient, observed::set);

        StepVerifier.create(invokeViaSimpleClient(handler))
                .expectNext("ok")
                .verifyComplete();

        assertNotNull(observed.get());
        assertEquals("http://test.local/items", observed.get().getRequestUrl());
        assertEquals("req-456", observed.get().getRequestHeaders().get("X-Request-ID"));
    }

    @Test
    void methodLevelLogHttpExchangeOverridesInterfaceLevelOnSameClient() throws Throwable {
        WebClient webClient = WebClient.builder()
                .baseUrl("http://test.local")
                .exchangeFunction(req -> Mono.just(ClientResponse.create(HttpStatus.OK)
                        .header(HttpHeaders.CONTENT_TYPE, "text/plain")
                        .body("ok")
                        .build()))
                .build();
        Method method = InterfaceAndMethodOverrideClient.class.getMethod("call");
        CountingInheritedClientLogger.LOG_COUNT.set(0);

        ReactiveClientInvocationHandler handler = buildHandlerWithCache(webClient, new MethodMetadataCache());
        Object proxy = Proxy.newProxyInstance(
                InterfaceAndMethodOverrideClient.class.getClassLoader(),
                new Class[]{InterfaceAndMethodOverrideClient.class},
                (p, m, a) -> null);

        StepVerifier.create((Mono<?>) handler.invoke(proxy, method, new Object[0]))
                .expectNextCount(1)
                .verifyComplete();

        assertEquals(0, CountingInheritedClientLogger.LOG_COUNT.get(),
                "method-level logger should take precedence over interface-level logger");
    }

    // -------------------------------------------------------------------------
    // 2.9 – Bounded cause-chain traversal (max depth 16)
    // -------------------------------------------------------------------------

    @Test
    void errorCategoryResolvedCorrectlyThroughDeepCauseChain() {
        // Build a chain of 20 RuntimeExceptions wrapping a ConnectException at the bottom
        Throwable root = new ConnectException("refused");
        Throwable current = root;
        for (int i = 0; i < 20; i++) {
            RuntimeException wrapper = new RuntimeException("layer " + i, current);
            current = wrapper;
        }
        Throwable topLevel = current;

        WebClient webClient = WebClient.builder()
                .baseUrl("http://test.local")
                .exchangeFunction(req -> Mono.error(topLevel))
                .build();

        var observed = new java.util.concurrent.atomic.AtomicReference<
                io.github.huynhngochuyhoang.httpstarter.observability.HttpClientObserverEvent>();

        ReactiveClientInvocationHandler handler = buildHandlerWithObserver(webClient, observed::set);

        StepVerifier.create(invokeViaSimpleClient(handler))
                .expectError()
                .verify();

        // With a 16-depth cap the ConnectException at depth 20 is NOT reachable.
        // The traversal stops at RuntimeException layer 15 (0-indexed), which is
        // classified as UNKNOWN because the handler only checks HttpClientException,
        // RemoteServiceException, TimeoutException, CancellationException,
        // AuthProviderException, UnknownHostException, and ConnectException directly
        // on the top-level error or via getRootCause – and here getRootCause returns
        // the RuntimeException at depth 16, not the ConnectException at depth 20.
        // The critical property under test is *termination* (no StackOverflowError,
        // no infinite loop) even with a chain longer than the bound.
        assertNotNull(observed.get());
        assertEquals(ErrorCategory.UNKNOWN, observed.get().getErrorCategory(),
                "deeply nested cause beyond depth 16 must not be classified as CONNECT_ERROR");
    }

    @Test
    void errorCategoryStillClassifiesConnectExceptionWithinBoundedDepth() {
        // Wrap ConnectException in fewer than 16 layers – must still be detected
        Throwable root = new ConnectException("refused");
        Throwable wrapped = new RuntimeException("outer", new RuntimeException("inner", root));

        WebClient webClient = WebClient.builder()
                .baseUrl("http://test.local")
                .exchangeFunction(req -> Mono.error(wrapped))
                .build();

        var observed = new java.util.concurrent.atomic.AtomicReference<
                io.github.huynhngochuyhoang.httpstarter.observability.HttpClientObserverEvent>();

        ReactiveClientInvocationHandler handler = buildHandlerWithObserver(webClient, observed::set);

        StepVerifier.create(invokeViaSimpleClient(handler))
                .expectError()
                .verify();

        assertNotNull(observed.get());
        assertEquals(ErrorCategory.CONNECT_ERROR, observed.get().getErrorCategory());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private ReactiveClientInvocationHandler buildHandler(WebClient webClient) {
        return buildHandlerWithObserver(webClient, null);
    }

    private ReactiveClientInvocationHandler buildHandlerWithObserver(
            WebClient webClient,
            HttpClientObserver observer) {
        ApplicationContext ctx = mock(ApplicationContext.class);
        ObjectProvider<HttpClientObserver> provider = mock(ObjectProvider.class);
        when(ctx.getBeanProvider(HttpClientObserver.class)).thenReturn(provider);
        when(provider.getIfAvailable()).thenReturn(observer);
        ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();
        return new ReactiveClientInvocationHandler(
                webClient, new MethodMetadataCache(), new RequestArgumentResolver(),
                new DefaultErrorDecoder(), config, "test-client", ctx,
                new NoopResilienceOperatorApplier(), TestJsonCodecs.jsonCodec(),
                new ReactiveHttpClientProperties.ObservabilityConfig());
    }

    private ReactiveClientInvocationHandler buildHandlerWithCache(
            WebClient webClient,
            MethodMetadataCache cache) {
        return buildHandlerWithCache(
                webClient, cache, new ReactiveHttpClientProperties.ClientConfig(), new DefaultHttpExchangeLogger());
    }

    private ReactiveClientInvocationHandler buildHandlerWithCache(
            WebClient webClient,
            MethodMetadataCache cache,
            ReactiveHttpClientProperties.ClientConfig config,
            DefaultHttpExchangeLogger defaultLogger) {
        ApplicationContext ctx = mock(ApplicationContext.class);
        ObjectProvider<HttpClientObserver> provider = mock(ObjectProvider.class);
        when(ctx.getBeanProvider(HttpClientObserver.class)).thenReturn(provider);
        when(provider.getIfAvailable()).thenReturn(null);
        // Provide a real DefaultHttpExchangeLogger bean so the logger is resolved via the ApplicationContext
        ObjectProvider<DefaultHttpExchangeLogger> loggerProvider = mock(ObjectProvider.class);
        when(loggerProvider.getIfAvailable()).thenReturn(defaultLogger);
        when(ctx.getBeanProvider(DefaultHttpExchangeLogger.class)).thenReturn(loggerProvider);
        ObjectProvider<CountingInheritedClientLogger> inheritedLoggerProvider = mock(ObjectProvider.class);
        when(inheritedLoggerProvider.getIfAvailable()).thenReturn(null);
        when(ctx.getBeanProvider(CountingInheritedClientLogger.class)).thenReturn(inheritedLoggerProvider);
        return new ReactiveClientInvocationHandler(
                webClient, cache, new RequestArgumentResolver(),
                new DefaultErrorDecoder(), config, "test-client", ctx,
                new NoopResilienceOperatorApplier(), TestJsonCodecs.jsonCodec(),
                new ReactiveHttpClientProperties.ObservabilityConfig());
    }

    @SuppressWarnings("unchecked")
    private Mono<String> invokeWithHeader(ReactiveClientInvocationHandler handler, String headerValue) {
        try {
            Method method = ClientWithOneHeader.class.getMethod("call", String.class);
            return (Mono<String>) handler.invoke(null, method, new Object[]{headerValue});
        } catch (Throwable t) {
            return Mono.error(t);
        }
    }

    @SuppressWarnings("unchecked")
    private Mono<String> invokeViaSimpleClient(ReactiveClientInvocationHandler handler) {
        try {
            Method method = SimpleClient.class.getMethod("call");
            return (Mono<String>) handler.invoke(null, method, new Object[0]);
        } catch (Throwable t) {
            return Mono.error(t);
        }
    }

    // Interfaces used by the tests above

    interface SimpleClient {
        @GET("/items")
        Mono<String> call();
    }

    interface ClientWithOneHeader {
        @GET("/items")
        Mono<String> call(
                @io.github.huynhngochuyhoang.httpstarter.annotation.HeaderParam("ACCEPT") String accept);
    }

    interface LoggedClient {
        @GET("/items")
        @LogHttpExchange(logger = DefaultHttpExchangeLogger.class)
        Mono<String> call();
    }

    interface SharedBaseApi {
        @GET("/items")
        Mono<String> call();
    }

    @LogHttpExchange(logger = CountingInheritedClientLogger.class)
    interface InheritedLoggedClient extends SharedBaseApi { }

    interface InheritedPlainClient extends SharedBaseApi { }

    @LogHttpExchange(logger = CountingInheritedClientLogger.class)
    interface InterfaceAndMethodOverrideClient {
        @GET("/items")
        @LogHttpExchange(logger = DefaultHttpExchangeLogger.class)
        Mono<String> call();
    }

    private static class RecordingDefaultHttpExchangeLogger extends DefaultHttpExchangeLogger {
        private final AtomicReference<HttpExchangeLogContext> logged;

        private RecordingDefaultHttpExchangeLogger(AtomicReference<HttpExchangeLogContext> logged) {
            this.logged = logged;
        }

        @Override
        public void log(HttpExchangeLogContext context) {
            logged.set(context);
        }
    }

    public static class CountingInheritedClientLogger implements HttpExchangeLogger {
        static final AtomicInteger LOG_COUNT = new AtomicInteger();

        @Override
        public void log(HttpExchangeLogContext context) {
            LOG_COUNT.incrementAndGet();
        }
    }
}
