package io.github.huynhngochuyhoang.httpstarter.test;

import io.github.huynhngochuyhoang.httpstarter.annotation.ReactiveHttpClient;
import io.github.huynhngochuyhoang.httpstarter.auth.AuthProvider;
import io.github.huynhngochuyhoang.httpstarter.auth.OutboundAuthFilter;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import io.github.huynhngochuyhoang.httpstarter.core.*;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientObserver;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.reactive.MockClientHttpRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.lang.reflect.Proxy;
import java.net.URI;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * Builder that produces a proxy for a {@code @ReactiveHttpClient} interface backed
 * by an in-process {@link ExchangeFunction}. Every call through the proxy is
 * materialised into a {@link RecordedExchange}, so tests can assert on request
 * URL, headers, body, and the response that was served.
 *
 * <pre>{@code
 * MockReactiveHttpClient<UserService> mock = MockReactiveHttpClient.forClient(UserService.class)
 *         .baseUrl("http://mock.local")
 *         .respondTo(HttpMethod.POST, "/users", request -> MockReactiveHttpClient.json(201, "{\"id\":1}"))
 *         .build();
 *
 * StepVerifier.create(mock.proxy().createUser(...))
 *         .expectNext(new User(1, ...))
 *         .verifyComplete();
 *
 * assertThat(mock.exchanges()).hasSize(1);
 * assertThat(mock.exchanges().get(0).bodyAsString()).contains("\"name\":\"alice\"");
 * }</pre>
 *
 * <p>Each matcher is consulted in registration order; the first matching handler
 * serves the request. Unmatched requests produce an HTTP 404 response so the
 * test fails loudly instead of hanging.
 */
public final class MockReactiveHttpClient<T> {

    private final T proxy;
    private final List<RecordedExchange> exchanges;
    private final List<Matcher> matchers;
    private final AtomicReference<ClientResponse> fallback;

    private MockReactiveHttpClient(T proxy,
                                   List<RecordedExchange> exchanges,
                                   List<Matcher> matchers,
                                   AtomicReference<ClientResponse> fallback) {
        this.proxy = proxy;
        this.exchanges = exchanges;
        this.matchers = matchers;
        this.fallback = fallback;
    }

    /** Returns the proxy implementing {@code T} — invoke its methods to exercise the client. */
    public T proxy() { return proxy; }

    /** Returns the recorded exchanges in call order. The list is live; it grows as more calls are made. */
    public List<RecordedExchange> exchanges() { return exchanges; }

    /** The most recently recorded exchange, or {@code null} if none. */
    public RecordedExchange lastExchange() {
        return exchanges.isEmpty() ? null : exchanges.get(exchanges.size() - 1);
    }

    /**
     * Registers a handler that responds to any request whose URL path equals
     * {@code path}.
     */
    public MockReactiveHttpClient<T> respondToPath(String path, Function<RecordedExchange, ClientResponse> handler) {
        return respond(ex -> path.equals(ex.uri().getPath()), handler);
    }

    /**
     * Registers a handler that responds to any request whose method and path
     * match.
     */
    public MockReactiveHttpClient<T> respondTo(org.springframework.http.HttpMethod method,
                                               String path,
                                               Function<RecordedExchange, ClientResponse> handler) {
        return respond(ex -> method.equals(ex.method()) && path.equals(ex.uri().getPath()), handler);
    }

    /** Registers a handler behind an arbitrary predicate. */
    public MockReactiveHttpClient<T> respond(java.util.function.Predicate<RecordedExchange> predicate,
                                             Function<RecordedExchange, ClientResponse> handler) {
        matchers.add(new Matcher(predicate, handler));
        return this;
    }

    /** Response served when no matcher applies. Defaults to HTTP 404. */
    public MockReactiveHttpClient<T> fallback(ClientResponse fallback) {
        this.fallback.set(fallback);
        return this;
    }

    /** Convenience factory producing a JSON response. */
    public static ClientResponse json(int status, String body) {
        return ClientResponse.create(HttpStatus.valueOf(status))
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .body(body)
                .build();
    }

    /** Convenience factory producing a text/plain response. */
    public static ClientResponse text(int status, String body) {
        return text(status, body, java.util.Map.of());
    }

    /** Convenience factory producing a text/plain response with custom headers. */
    public static ClientResponse text(int status, String body, java.util.Map<String, List<String>> headers) {
        ClientResponse.Builder builder = responseBuilder(status, headers)
                .header("Content-Type", MediaType.TEXT_PLAIN_VALUE);
        return builder.body(body != null ? body : "").build();
    }

    /** Convenience factory producing an application/octet-stream response. */
    public static ClientResponse bytes(int status, byte[] body) {
        return bytes(status, body, java.util.Map.of());
    }

    /** Convenience factory producing an application/octet-stream response with custom headers. */
    public static ClientResponse bytes(int status, byte[] body, java.util.Map<String, List<String>> headers) {
        byte[] bytes = body != null ? body : new byte[0];
        return responseBuilder(status, headers)
                .header("Content-Type", MediaType.APPLICATION_OCTET_STREAM_VALUE)
                .body(Flux.just(new DefaultDataBufferFactory().wrap(bytes)))
                .build();
    }

    /**
     * Produces a response whose body terminates with the supplied error after response
     * status and headers are available. This models stable terminal semantics only; it
     * does not emulate transport timing, socket timeouts, or connection-pool behavior.
     */
    public static ClientResponse bodyError(
            int status, java.util.Map<String, List<String>> headers, Throwable error) {
        return responseBuilder(status, headers)
                .body(Flux.error(Objects.requireNonNull(error, "error")))
                .build();
    }

    /** Convenience factory producing a response with custom headers and a raw text body. */
    public static ClientResponse response(int status, java.util.Map<String, List<String>> headers, String body) {
        return responseBuilder(status, headers)
                .body(body != null ? body : "")
                .build();
    }

    /** Convenience factory producing a response with custom headers and a raw byte body. */
    public static ClientResponse response(int status, java.util.Map<String, List<String>> headers, byte[] body) {
        byte[] bytes = body != null ? body : new byte[0];
        return responseBuilder(status, headers)
                .body(Flux.just(new DefaultDataBufferFactory().wrap(bytes)))
                .build();
    }

    /** Convenience factory producing an empty response (used for Void-returning methods). */
    public static ClientResponse empty(int status) {
        return ClientResponse.create(HttpStatus.valueOf(status)).build();
    }

    /**
     * Returns a response handler that serves one HTTP 401 response, then delegates
     * every later request to {@code afterUnauthorized}.
     */
    public static Function<RecordedExchange, ClientResponse> unauthorizedOnceThen(
            Function<RecordedExchange, ClientResponse> afterUnauthorized) {
        Objects.requireNonNull(afterUnauthorized, "afterUnauthorized");
        AtomicBoolean unauthorizedServed = new AtomicBoolean();
        return exchange -> unauthorizedServed.compareAndSet(false, true)
                ? empty(HttpStatus.UNAUTHORIZED.value())
                : afterUnauthorized.apply(exchange);
    }

    private static ClientResponse.Builder responseBuilder(int status, java.util.Map<String, List<String>> headers) {
        ClientResponse.Builder builder = ClientResponse.create(HttpStatus.valueOf(status));
        if (headers != null) {
            headers.forEach((name, values) -> builder.header(name, values.toArray(String[]::new)));
        }
        return builder;
    }

    public static <T> Builder<T> forClient(Class<T> clientInterface) {
        return new Builder<>(clientInterface);
    }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    public static final class Builder<T> {
        private final Class<T> clientInterface;
        private String baseUrl = "http://mock.local";
        private final List<Matcher> matchers = new ArrayList<>();
        private ClientResponse fallback = ClientResponse.create(HttpStatus.NOT_FOUND)
                .body("mock: no matcher for this request")
                .build();
        private ReactiveHttpClientProperties.ClientConfig clientConfig = new ReactiveHttpClientProperties.ClientConfig();
        private MethodMetadataCache methodMetadataCache = new MethodMetadataCache();
        private ResilienceOperatorApplier resilienceOperatorApplier = new NoopResilienceOperatorApplier();
        private AuthProvider authProvider;
        private ReactiveHttpClientJsonCodec jsonCodec;
        private HttpClientObserver observer;
        private final List<ReactiveHttpClientLifecycleHook> lifecycleHooks = new ArrayList<>();
        private final Map<Class<? extends HttpExchangeLogger>, HttpExchangeLogger> exchangeLoggers =
                new LinkedHashMap<>();

        private Builder(Class<T> clientInterface) {
            this.clientInterface = clientInterface;
        }

        public Builder<T> baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        /**
         * Registers a handler that responds to any request whose URL path equals
         * {@code path}. Shortcut for {@link #respond(java.util.function.Predicate, Function)}
         * with a simple path equality predicate.
         */
        public Builder<T> respondToPath(String path, Function<RecordedExchange, ClientResponse> handler) {
            return respond(ex -> path.equals(ex.uri().getPath()), handler);
        }

        /**
         * Registers a handler that responds to any request whose method and path
         * match.
         */
        public Builder<T> respondTo(org.springframework.http.HttpMethod method,
                                    String path,
                                    Function<RecordedExchange, ClientResponse> handler) {
            return respond(ex -> method.equals(ex.method()) && path.equals(ex.uri().getPath()), handler);
        }

        /** Registers a handler behind an arbitrary predicate. */
        public Builder<T> respond(java.util.function.Predicate<RecordedExchange> predicate,
                                  Function<RecordedExchange, ClientResponse> handler) {
            matchers.add(new Matcher(predicate, handler));
            return this;
        }

        /** Response served when no matcher applies. Defaults to HTTP 404. */
        public Builder<T> fallback(ClientResponse fallback) {
            this.fallback = fallback;
            return this;
        }

        /** Uses the supplied client configuration when constructing the mock proxy. */
        public Builder<T> clientConfig(ReactiveHttpClientProperties.ClientConfig clientConfig) {
            this.clientConfig = clientConfig != null ? clientConfig : new ReactiveHttpClientProperties.ClientConfig();
            return this;
        }

        /** Uses a replacement metadata cache for validation and invocation. */
        public Builder<T> methodMetadataCache(MethodMetadataCache methodMetadataCache) {
            this.methodMetadataCache = Objects.requireNonNull(methodMetadataCache, "methodMetadataCache");
            return this;
        }

        /** Applies the production logical-call timeout budget to this mock client. */
        public Builder<T> logicalCallTimeout(long timeoutMs) {
            clientConfig.setLogicalCallTimeoutMs(timeoutMs);
            return this;
        }

        /** Uses the application JSON codec for auth-aware request byte materialization. */
        public Builder<T> jsonCodec(ReactiveHttpClientJsonCodec jsonCodec) {
            this.jsonCodec = Objects.requireNonNull(jsonCodec, "jsonCodec");
            return this;
        }

        /**
         * Uses the supplied resilience operator applier when constructing the mock proxy.
         * A {@link NoopResilienceOperatorApplier} subclass may override only the
         * relevant {@code apply*} methods; those operators are inferred as available
         * for the matching Mono or Flux shape. Retry capacity remains non-duplicating
         * unless the subclass overrides {@code canRetryMoreThanOnce}.
         */
        public Builder<T> resilienceOperatorApplier(ResilienceOperatorApplier resilienceOperatorApplier) {
            this.resilienceOperatorApplier = resilienceOperatorApplier != null
                    ? resilienceOperatorApplier
                    : new NoopResilienceOperatorApplier();
            return this;
        }

        /** Installs the production outbound auth filter for requests made by this mock. */
        public Builder<T> withAuthProvider(AuthProvider authProvider) {
            this.authProvider = Objects.requireNonNull(authProvider, "authProvider");
            return this;
        }

        /** Registers a custom observer for logical-call terminal events. */
        public Builder<T> withObserver(HttpClientObserver observer) {
            this.observer = Objects.requireNonNull(observer, "observer");
            return this;
        }

        /** Registers one lifecycle hook. Repeated calls accumulate hooks. */
        public Builder<T> withLifecycleHook(ReactiveHttpClientLifecycleHook lifecycleHook) {
            lifecycleHooks.add(Objects.requireNonNull(lifecycleHook, "lifecycleHook"));
            return this;
        }

        /**
         * Registers an exchange logger in the mock's isolated application context.
         * The logger is selected when {@code @LogHttpExchange} references its concrete class.
         */
        public Builder<T> withExchangeLogger(HttpExchangeLogger exchangeLogger) {
            Objects.requireNonNull(exchangeLogger, "exchangeLogger");
            Class<? extends HttpExchangeLogger> loggerClass =
                    exchangeLogger.getClass().asSubclass(HttpExchangeLogger.class);
            if (exchangeLoggers.putIfAbsent(loggerClass, exchangeLogger) != null) {
                throw new IllegalArgumentException(
                        "HttpExchangeLogger already registered for class [" + loggerClass.getName() + "]");
            }
            return this;
        }

        /**
         * Enables a lightweight retry operator for mock-client tests.
         * {@code maxAttempts} includes the initial request.
         */
        public Builder<T> retry(int maxAttempts, String... retryMethods) {
            if (maxAttempts < 1) {
                throw new IllegalArgumentException("maxAttempts must be at least 1");
            }
            if (retryMethods == null || retryMethods.length == 0) {
                throw new IllegalArgumentException("retryMethods must contain at least one HTTP method");
            }
            java.util.LinkedHashSet<String> retryMethodSet = new java.util.LinkedHashSet<>(java.util.Arrays.asList(retryMethods));
            if (retryMethodSet.stream().anyMatch(method -> method == null || method.isBlank())) {
                throw new IllegalArgumentException("retryMethods must not contain null or blank HTTP methods");
            }
            ReactiveHttpClientProperties.ResilienceConfig resilience = new ReactiveHttpClientProperties.ResilienceConfig();
            resilience.setEnabled(true);
            resilience.setRetry("mock");
            resilience.setRetryMethods(retryMethodSet);
            clientConfig.setResilience(resilience);
            resilienceOperatorApplier = new MockRetryResilienceOperatorApplier(maxAttempts);
            return this;
        }

        public MockReactiveHttpClient<T> build() {
            ReactiveHttpClient annotation = clientInterface.getAnnotation(ReactiveHttpClient.class);
            String clientName = annotation != null ? annotation.name() : "mock-client";
            methodMetadataCache.validateDeclarativeRequestParameters(clientInterface, clientName);
            methodMetadataCache.validateDeclarativeUriTemplates(clientInterface, clientName, clientConfig.getApis());
            methodMetadataCache.validateDeclarativeReturnTypes(clientInterface, clientName);
            methodMetadataCache.validateDeclarativeCachePolicies(clientInterface, clientName, clientConfig);

            List<RecordedExchange> exchanges = new CopyOnWriteArrayList<>();
            List<Matcher> liveMatchers = new CopyOnWriteArrayList<>(matchers);
            AtomicReference<ClientResponse> fallbackRef = new AtomicReference<>(fallback);

            ExchangeFunction exchangeFunction = request -> Mono.deferContextual(contextView -> {
                RequestContextSnapshot contextSnapshot = RequestContextSnapshot.capture(contextView);
                MockClientHttpRequest materialized = new MockClientHttpRequest(
                        request.method(), URI.create(request.url().toString()));
                return request.writeTo(materialized, ExchangeStrategies.withDefaults())
                        .then(Mono.fromCallable(() -> {
                            RecordedExchange requestExchange = new RecordedExchange(
                                    request.method(),
                                    URI.create(request.url().toString()),
                                    materialized,
                                    contextSnapshot,
                                    null);
                            exchanges.add(requestExchange);
                            ClientResponse response = fallbackRef.get();
                            for (Matcher matcher : liveMatchers) {
                                if (matcher.predicate.test(requestExchange)) {
                                    response = matcher.handler.apply(requestExchange);
                                    break;
                                }
                            }
                            exchanges.set(exchanges.indexOf(requestExchange), new RecordedExchange(
                                    request.method(),
                                    URI.create(request.url().toString()),
                                    materialized,
                                    contextSnapshot,
                                    response.statusCode()));
                            return response;
                        }));
            });

            WebClient.Builder webClientBuilder = WebClient.builder()
                    .baseUrl(baseUrl)
                    .exchangeFunction(exchangeFunction);
            if (authProvider != null) {
                webClientBuilder.filter(new OutboundAuthFilter(clientName, authProvider));
            }
            WebClient webClient = webClientBuilder
                    .filter(ReactiveClientInvocationHandler.finalRequestObservationFilter())
                    .build();

            StaticApplicationContext appCtx = new StaticApplicationContext();
            appCtx.getDefaultListableBeanFactory().setDependencyComparator(AnnotationAwareOrderComparator.INSTANCE);
            int exchangeLoggerIndex = 0;
            for (HttpExchangeLogger exchangeLogger : exchangeLoggers.values()) {
                appCtx.getBeanFactory().registerSingleton(
                        "mockHttpExchangeLogger" + exchangeLoggerIndex++, exchangeLogger);
            }
            if (observer != null) {
                appCtx.getBeanFactory().registerSingleton("mockHttpClientObserver", observer);
            }
            for (int i = 0; i < lifecycleHooks.size(); i++) {
                appCtx.getBeanFactory().registerSingleton("mockReactiveHttpClientLifecycleHook" + i, lifecycleHooks.get(i));
            }
            appCtx.refresh();
            methodMetadataCache.validateDeclarativeCacheCustomizations(
                    appCtx, clientInterface, clientName, clientConfig);

            ReactiveHttpClientJsonCodec effectiveJsonCodec = jsonCodec;
            if (authProvider != null) {
                if (!clientConfig.hasAuthConfigured()) {
                    clientConfig.setAuthProvider("mock-auth-provider");
                }
                if (effectiveJsonCodec == null) {
                    effectiveJsonCodec = new MockJsonCodecFactory().create();
                }
            }

            ReactiveClientInvocationHandler handler = ReactiveClientInvocationHandler.create(
                    webClient,
                    methodMetadataCache,
                    new RequestArgumentResolver(),
                    new DefaultErrorDecoder(),
                    clientConfig,
                    clientName,
                    clientInterface,
                    appCtx,
                    resilienceOperatorApplier,
                    effectiveJsonCodec,
                    new ReactiveHttpClientProperties.ObservabilityConfig(),
                    authProvider,
                    baseUrl
            );

            @SuppressWarnings("unchecked")
            T proxy = (T) Proxy.newProxyInstance(
                    clientInterface.getClassLoader(),
                    new Class<?>[]{clientInterface},
                    handler);

            return new MockReactiveHttpClient<>(proxy, exchanges, liveMatchers, fallbackRef);
        }
    }

    private static final class MockRetryResilienceOperatorApplier extends NoopResilienceOperatorApplier {
        private final long retryCount;

        private MockRetryResilienceOperatorApplier(int maxAttempts) {
            this.retryCount = maxAttempts - 1L;
        }

        @Override
        public <T> Mono<T> applyRetry(Mono<T> mono, String instanceName) {
            return mono.retry(retryCount);
        }

        @Override
        public <T> Flux<T> applyRetry(Flux<T> flux, String instanceName) {
            return flux.retry(retryCount);
        }

        public boolean isOperatorAvailable(ResilienceOperatorApplier.InstanceType type) {
            return type == ResilienceOperatorApplier.InstanceType.RETRY;
        }

        @Override
        public boolean canRetryMoreThanOnce(String instanceName) {
            return retryCount > 0;
        }
    }

    private record Matcher(java.util.function.Predicate<RecordedExchange> predicate,
                           Function<RecordedExchange, ClientResponse> handler) {
    }
}
