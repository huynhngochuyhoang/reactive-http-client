package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.annotation.*;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import io.github.huynhngochuyhoang.httpstarter.exception.HttpClientException;
import io.github.huynhngochuyhoang.httpstarter.exception.RemoteServiceException;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientObserver;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientObserverEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;
import reactor.test.StepVerifier;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RedirectHandlingContractTest {

    @Test
    void visibleRedirectPassesThroughProxyWithoutInvokingErrorMappers() {
        AtomicInteger mapperInvocations = new AtomicInteger();
        AtomicReference<HttpClientObserverEvent> observed = new AtomicReference<>();
        WebClient webClient = WebClient.builder()
                .baseUrl("http://redirect.test")
                .filter(ReactiveClientInvocationHandler.finalRequestObservationFilter())
                .exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.FOUND)
                        .header(HttpHeaders.LOCATION, "/final")
                        .body("redirect")
                        .build()))
                .build();

        RedirectClient client = createClient(webClient, mapperInvocations, observed::set);

        StepVerifier.create(client.get())
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
                    assertThat(response.getHeaders().getFirst(HttpHeaders.LOCATION)).isEqualTo("/final");
                    assertThat(response.getBody()).isEqualTo("redirect");
                })
                .verifyComplete();

        assertThat(mapperInvocations).hasValue(0);
        assertThat(observed.get().getStatusCode()).isEqualTo(HttpStatus.FOUND.value());
    }

    @ParameterizedTest
    @ValueSource(ints = {301, 302, 303, 307, 308})
    void starterTransportLeavesRedirectVisibleWhenFollowRedirectsDisabled(int status) {
        DisposableServer server = redirectToFinalServer(status, HttpStatus.OK.value(), "followed");
        try {
            RedirectClient client = createFactoryClient(serverBaseUrl(server), config -> { });

            StepVerifier.create(client.get())
                    .assertNext(response -> {
                        assertThat(response.getStatusCode().value()).isEqualTo(status);
                        assertThat(response.getHeaders().getFirst(HttpHeaders.LOCATION)).isEqualTo("/final");
                    })
                    .verifyComplete();
        } finally {
            server.disposeNow(Duration.ofSeconds(5));
        }
    }

    @Test
    void starterTransportFollowsSameOriginRedirectWhenEnabled() {
        AtomicInteger requests = new AtomicInteger();
        DisposableServer server = HttpServer.create()
                .port(0)
                .route(routes -> routes
                        .get("/start", (request, response) -> {
                            requests.incrementAndGet();
                            return response.status(HttpStatus.FOUND.value())
                                    .addHeader(HttpHeaders.LOCATION, "/final")
                                    .send();
                        })
                        .get("/final", (request, response) -> {
                            requests.incrementAndGet();
                            return response.sendString(Mono.just("followed"));
                        }))
                .bindNow();

        try {
            RedirectClient client = createFactoryClient(serverBaseUrl(server), config -> config.setFollowRedirects(true));

            StepVerifier.create(client.get())
                    .assertNext(response -> {
                        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                        assertThat(response.getBody()).isEqualTo("followed");
                    })
                    .verifyComplete();

            assertThat(requests).hasValue(2);
        } finally {
            server.disposeNow(Duration.ofSeconds(5));
        }
    }

    @Test
    void followedRedirectReportsOriginalDeclarativeRequestToObserverAndExchangeLogger() {
        AtomicReference<HttpClientObserverEvent> observed = new AtomicReference<>();
        RecordingExchangeLogger logger = new RecordingExchangeLogger();
        AtomicInteger dispatches = new AtomicInteger();
        DisposableServer server = redirectToFinalServer(HttpStatus.FOUND.value(), HttpStatus.OK.value(), "followed", dispatches);
        try {
            String baseUrl = serverBaseUrl(server);
            RedirectClient client = createFactoryClient(
                    baseUrl,
                    config -> {
                        config.setFollowRedirects(true);
                        config.setExchangeLoggingEnabled(true);
                    },
                    observed::set,
                    logger);

            StepVerifier.create(client.get())
                    .assertNext(response -> {
                        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                        assertThat(response.getBody()).isEqualTo("followed");
                    })
                    .verifyComplete();

            assertThat(dispatches).hasValue(2);
            assertThat(observed.get().getRequestUrl()).isEqualTo(baseUrl + "/start");
            assertThat(observed.get().getServerAddress()).isEqualTo("127.0.0.1");
            assertThat(observed.get().getServerPort()).isEqualTo(server.port());
            assertThat(observed.get().getAttemptCount()).isEqualTo(1);
            assertThat(logger.context.get().requestUrl()).hasToString(baseUrl + "/start");
            assertThat(logger.context.get().responseStatus()).isEqualTo(HttpStatus.OK.value());
            assertThat(logger.context.get().subscriptionAttemptCount()).isEqualTo(1);
        } finally {
            server.disposeNow(Duration.ofSeconds(5));
        }
    }

    @Test
    void final4xxAfterRedirectUsesDefaultErrorDecoder() {
        DisposableServer server = redirectToFinalServer(HttpStatus.FOUND.value(), HttpStatus.NOT_FOUND.value(), "missing");
        try {
            RedirectClient client = createFactoryClient(serverBaseUrl(server), config -> config.setFollowRedirects(true));

            StepVerifier.create(client.getBody())
                    .expectErrorSatisfies(error -> {
                        assertThat(error).isInstanceOf(HttpClientException.class);
                        HttpClientException exception = (HttpClientException) error;
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND.value());
                        assertThat(exception.getResponseBody()).isEqualTo("missing");
                    })
                    .verify();
        } finally {
            server.disposeNow(Duration.ofSeconds(5));
        }
    }

    @Test
    void final5xxAfterRedirectUsesDefaultErrorDecoder() {
        DisposableServer server = redirectToFinalServer(HttpStatus.FOUND.value(), HttpStatus.BAD_GATEWAY.value(), "bad gateway");
        try {
            RedirectClient client = createFactoryClient(serverBaseUrl(server), config -> config.setFollowRedirects(true));

            StepVerifier.create(client.getBody())
                    .expectErrorSatisfies(error -> {
                        assertThat(error).isInstanceOf(RemoteServiceException.class);
                        RemoteServiceException exception = (RemoteServiceException) error;
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY.value());
                        assertThat(exception.getResponseBody()).isEqualTo("bad gateway");
                    })
                    .verify();
        } finally {
            server.disposeNow(Duration.ofSeconds(5));
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {301, 302, 303, 307, 308})
    void followsStandardRedirectStatusesForGet(int status) {
        DisposableServer server = redirectToFinalServer(status, HttpStatus.OK.value(), "status-" + status);
        try {
            RedirectClient client = createFactoryClient(serverBaseUrl(server), config -> config.setFollowRedirects(true));

            StepVerifier.create(client.get())
                    .assertNext(response -> {
                        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                        assertThat(response.getBody()).isEqualTo("status-" + status);
                    })
                    .verifyComplete();
        } finally {
            server.disposeNow(Duration.ofSeconds(5));
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {307, 308})
    void preservesPostMethodAndBodyForTemporaryAndPermanentRedirects(int status) {
        DisposableServer server = postRedirectServer(status);
        try {
            RedirectClient client = createFactoryClient(serverBaseUrl(server), config -> config.setFollowRedirects(true));

            StepVerifier.create(client.post("payload"))
                    .assertNext(response -> {
                        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                        assertThat(response.getBody()).isEqualTo("POST:payload");
                    })
                    .verifyComplete();
        } finally {
            server.disposeNow(Duration.ofSeconds(5));
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {301, 302})
    void movedAndFoundRedirectsPreservePostMethodAndBody(int status) {
        DisposableServer server = postRedirectServer(status);
        try {
            RedirectClient client = createFactoryClient(serverBaseUrl(server), config -> config.setFollowRedirects(true));

            StepVerifier.create(client.post("payload"))
                    .assertNext(response -> {
                        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                        assertThat(response.getBody()).isEqualTo("POST:payload");
                    })
                    .verifyComplete();
        } finally {
            server.disposeNow(Duration.ofSeconds(5));
        }
    }

    @Test
    void seeOtherRedirectSwitchesPostToBodilessGet() {
        DisposableServer server = postRedirectServer(HttpStatus.SEE_OTHER.value());
        try {
            RedirectClient client = createFactoryClient(serverBaseUrl(server), config -> config.setFollowRedirects(true));

            StepVerifier.create(client.post("payload"))
                    .assertNext(response -> {
                        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                        assertThat(response.getBody()).isEqualTo("GET:");
                    })
                    .verifyComplete();
        } finally {
            server.disposeNow(Duration.ofSeconds(5));
        }
    }

    @Test
    void crossAuthorityRedirectDoesNotLeakSensitiveHeaders() {
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> cookie = new AtomicReference<>();
        AtomicReference<String> proxyAuthorization = new AtomicReference<>();
        DisposableServer target = HttpServer.create()
                .port(0)
                .route(routes -> routes.get("/final", (request, response) -> {
                    authorization.set(request.requestHeaders().get(HttpHeaders.AUTHORIZATION));
                    cookie.set(request.requestHeaders().get(HttpHeaders.COOKIE));
                    proxyAuthorization.set(request.requestHeaders().get("Proxy-Authorization"));
                    return response.sendString(Mono.just("final"));
                }))
                .bindNow();
        DisposableServer redirect = HttpServer.create()
                .port(0)
                .route(routes -> routes.get("/start", (request, response) -> response
                        .status(HttpStatus.FOUND.value())
                        .addHeader(HttpHeaders.LOCATION, serverBaseUrl(target) + "/final")
                        .send()))
                .bindNow();

        try {
            RedirectClient client = createFactoryClient(serverBaseUrl(redirect), config -> config.setFollowRedirects(true));

            StepVerifier.create(client.getWithSensitiveHeaders("Bearer secret", "session=secret", "Basic secret"))
                    .assertNext(response -> assertThat(response.getBody()).isEqualTo("final"))
                    .verifyComplete();

            assertThat(authorization.get()).isNull();
            assertThat(cookie.get()).isNull();
            assertThat(proxyAuthorization.get()).isNull();
        } finally {
            redirect.disposeNow(Duration.ofSeconds(5));
            target.disposeNow(Duration.ofSeconds(5));
        }
    }

    @Test
    void excessiveRedirectChainStopsAndSurfacesVisibleRedirect() {
        AtomicInteger requests = new AtomicInteger();
        DisposableServer server = HttpServer.create()
                .port(0)
                .route(routes -> routes.get("/start", (request, response) -> {
                    requests.incrementAndGet();
                    return response.status(HttpStatus.FOUND.value())
                            .addHeader(HttpHeaders.LOCATION, "/start")
                            .send();
                }))
                .bindNow();

        try {
            RedirectClient client = createFactoryClient(serverBaseUrl(server), config -> config.setFollowRedirects(true));

            StepVerifier.create(client.get())
                    .assertNext(response -> {
                        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
                        assertThat(response.getHeaders().getFirst(HttpHeaders.LOCATION)).isEqualTo("/start");
                    })
                    .verifyComplete();

            assertThat(requests.get()).isGreaterThan(1);
        } finally {
            server.disposeNow(Duration.ofSeconds(5));
        }
    }

    @SuppressWarnings("unchecked")
    private static RedirectClient createClient(
            WebClient webClient,
            AtomicInteger mapperInvocations,
            HttpClientObserver observer) {
        ErrorResponseMapper mapper = context -> {
            mapperInvocations.incrementAndGet();
            return Optional.empty();
        };
        ApplicationContext context = mock(ApplicationContext.class);
        ObjectProvider<HttpClientObserver> observerProvider = mock(ObjectProvider.class);
        when(context.getBeanProvider(HttpClientObserver.class)).thenReturn(observerProvider);
        when(observerProvider.orderedStream()).thenAnswer(invocation -> Stream.of(observer));

        ObjectProvider<ReactiveHttpClientLifecycleHook> lifecycleProvider = mock(ObjectProvider.class);
        when(context.getBeanProvider(ReactiveHttpClientLifecycleHook.class)).thenReturn(lifecycleProvider);
        when(lifecycleProvider.orderedStream()).thenReturn(Stream.empty());

        ReactiveClientInvocationHandler handler = new ReactiveClientInvocationHandler(
                webClient,
                new MethodMetadataCache(),
                new RequestArgumentResolver(),
                new DefaultErrorDecoder("redirect-client", List.of(mapper)),
                new ReactiveHttpClientProperties.ClientConfig(),
                "redirect-client",
                context,
                new NoopResilienceOperatorApplier(),
                TestJsonCodecs.jsonCodec(),
                new ReactiveHttpClientProperties.ObservabilityConfig());
        return (RedirectClient) Proxy.newProxyInstance(
                RedirectClient.class.getClassLoader(),
                new Class<?>[]{RedirectClient.class},
                handler);
    }

    private static RedirectClient createFactoryClient(
            String baseUrl,
            Consumer<ReactiveHttpClientProperties.ClientConfig> configurer) {
        return createFactoryClient(baseUrl, configurer, null, null);
    }

    private static RedirectClient createFactoryClient(
            String baseUrl,
            Consumer<ReactiveHttpClientProperties.ClientConfig> configurer,
            HttpClientObserver observer,
            DefaultHttpExchangeLogger exchangeLogger) {
        ReactiveHttpClientProperties properties = new ReactiveHttpClientProperties();
        ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();
        config.setBaseUrl(baseUrl);
        configurer.accept(config);
        properties.getClients().put("redirect-client", config);

        ReactiveHttpClientFactoryBean<RedirectClient> factoryBean = new ReactiveHttpClientFactoryBean<>();
        factoryBean.setType(RedirectClient.class);
        factoryBean.setApplicationContext(factoryContext(properties, observer, exchangeLogger));
        return factoryBean.getObject();
    }

    @SuppressWarnings("unchecked")
    private static ApplicationContext factoryContext(
            ReactiveHttpClientProperties properties,
            HttpClientObserver observer,
            DefaultHttpExchangeLogger exchangeLogger) {
        ApplicationContext context = mock(ApplicationContext.class);

        ObjectProvider<Object> defaultProvider = mock(ObjectProvider.class);
        when(defaultProvider.getIfAvailable()).thenReturn(null);
        lenient().when(defaultProvider.getIfAvailable(any(Supplier.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, Supplier.class).get());
        lenient().when(defaultProvider.orderedStream()).thenReturn(Stream.empty());
        when(context.getBeanProvider(any(Class.class))).thenReturn((ObjectProvider) defaultProvider);

        ObjectProvider<ReactiveHttpClientProperties> propertiesProvider = mock(ObjectProvider.class);
        when(propertiesProvider.getIfAvailable(any(Supplier.class))).thenReturn(properties);
        when(context.getBeanProvider(ReactiveHttpClientProperties.class)).thenReturn(propertiesProvider);

        ObjectProvider<MethodMetadataCache> cacheProvider = mock(ObjectProvider.class);
        when(cacheProvider.getIfAvailable(any(Supplier.class))).thenReturn(new MethodMetadataCache());
        when(context.getBeanProvider(MethodMetadataCache.class)).thenReturn(cacheProvider);

        ObjectProvider<DefaultErrorDecoder> errorProvider = mock(ObjectProvider.class);
        when(errorProvider.getIfAvailable(any(Supplier.class))).thenReturn(new DefaultErrorDecoder());
        when(context.getBeanProvider(DefaultErrorDecoder.class)).thenReturn(errorProvider);

        ObjectProvider<WebClient.Builder> builderProvider = mock(ObjectProvider.class);
        when(builderProvider.getIfAvailable(any(Supplier.class))).thenReturn(WebClient.builder());
        when(context.getBeanProvider(WebClient.Builder.class)).thenReturn(builderProvider);

        ObjectProvider<ReactiveHttpClientCustomizer> customizerProvider = mock(ObjectProvider.class);
        when(customizerProvider.orderedStream()).thenReturn(Stream.empty());
        when(context.getBeanProvider(ReactiveHttpClientCustomizer.class)).thenReturn(customizerProvider);

        ObjectProvider<HttpClientObserver> observerProvider = mock(ObjectProvider.class);
        when(observerProvider.orderedStream()).thenReturn(Stream.empty());
        when(observerProvider.getIfAvailable()).thenReturn(observer);
        when(context.getBeanProvider(HttpClientObserver.class)).thenReturn(observerProvider);

        ObjectProvider<DefaultHttpExchangeLogger> exchangeLoggerProvider = mock(ObjectProvider.class);
        when(exchangeLoggerProvider.getIfAvailable()).thenReturn(exchangeLogger);
        when(context.getBeanProvider(DefaultHttpExchangeLogger.class)).thenReturn(exchangeLoggerProvider);

        ObjectProvider<ReactiveHttpClientLifecycleHook> lifecycleProvider = mock(ObjectProvider.class);
        when(lifecycleProvider.orderedStream()).thenReturn(Stream.empty());
        when(context.getBeanProvider(ReactiveHttpClientLifecycleHook.class)).thenReturn(lifecycleProvider);

        ObjectProvider<ReactiveHttpClientJsonCodec> jsonCodecProvider = mock(ObjectProvider.class);
        when(jsonCodecProvider.getIfAvailable()).thenReturn(TestJsonCodecs.jsonCodec());
        when(context.getBeanProvider(ReactiveHttpClientJsonCodec.class)).thenReturn(jsonCodecProvider);

        return context;
    }

    private static DisposableServer redirectToFinalServer(int redirectStatus, int finalStatus, String finalBody) {
        return redirectToFinalServer(redirectStatus, finalStatus, finalBody, new AtomicInteger());
    }

    private static DisposableServer redirectToFinalServer(
            int redirectStatus,
            int finalStatus,
            String finalBody,
            AtomicInteger dispatches) {
        return HttpServer.create()
                .port(0)
                .route(routes -> routes
                        .get("/start", (request, response) -> {
                            dispatches.incrementAndGet();
                            return response.status(redirectStatus)
                                    .addHeader(HttpHeaders.LOCATION, "/final")
                                    .send();
                        })
                        .get("/final", (request, response) -> {
                            dispatches.incrementAndGet();
                            return response.status(finalStatus).sendString(Mono.just(finalBody));
                        }))
                .bindNow();
    }

    private static DisposableServer postRedirectServer(int redirectStatus) {
        return HttpServer.create()
                .port(0)
                .route(routes -> routes
                        .post("/start", (request, response) -> response.status(redirectStatus)
                                .addHeader(HttpHeaders.LOCATION, "/final")
                                .send())
                        .get("/final", (request, response) -> response.sendString(Mono.just("GET:")))
                        .post("/final", (request, response) -> response.sendString(
                                request.receive().aggregate().asString().map(body -> "POST:" + body))))
                .bindNow();
    }

    private static String serverBaseUrl(DisposableServer server) {
        return "http://127.0.0.1:" + server.port();
    }

    static final class RecordingExchangeLogger extends DefaultHttpExchangeLogger {
        private final AtomicReference<HttpExchangeLogContext> context = new AtomicReference<>();

        @Override
        public void log(HttpExchangeLogContext context) {
            this.context.set(context);
        }
    }

    @ReactiveHttpClient(name = "redirect-client")
    interface RedirectClient {
        @GET("/start")
        Mono<ResponseEntity<String>> get();

        @GET("/start")
        Mono<String> getBody();

        @POST("/start")
        Mono<ResponseEntity<String>> post(@Body String body);

        @GET("/start")
        Mono<ResponseEntity<String>> getWithSensitiveHeaders(
                @HeaderParam("Authorization") String authorization,
                @HeaderParam("Cookie") String cookie,
                @HeaderParam("Proxy-Authorization") String proxyAuthorization);
    }
}
