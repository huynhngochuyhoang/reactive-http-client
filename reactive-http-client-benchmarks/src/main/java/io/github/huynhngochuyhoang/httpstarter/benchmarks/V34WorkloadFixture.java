package io.github.huynhngochuyhoang.httpstarter.benchmarks;

import io.github.huynhngochuyhoang.httpstarter.annotation.*;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientAutoConfiguration;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import io.github.huynhngochuyhoang.httpstarter.core.*;
import io.github.huynhngochuyhoang.httpstarter.exception.HttpClientException;
import io.github.huynhngochuyhoang.httpstarter.exception.RemoteServiceException;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientObserver;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;
import reactor.netty.resources.LoopResources;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/** Shared by JMH and its untimed semantic witnesses. No application traffic or cache hits. */
final class V34WorkloadFixture implements AutoCloseable {
    static final Duration WAIT = Duration.ofSeconds(10);
    static final String CLIENT = "v34-workload";
    static final String JSON = "{\"value\":\"fixture\"}";
    static final Payload PAYLOAD = new Payload("fixture");

    enum Profile { MINIMAL, AUTO_NO_REGISTRY, AUTO_REGISTRY, RESILIENCE_ENABLED_ONLY, OBSERVER, HOOK }
    enum Scenario { GET, TARGET, STRING, JSON, ENTITY, EMPTY, ERROR4, ERROR5 }

    final Profile profile;
    final Scenario scenario;
    final boolean loopback;
    final AtomicLong dispatches = new AtomicLong();
    final AtomicLong terminals = new AtomicLong();
    final AtomicReference<String> violation = new AtomicReference<>();
    final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
    SimpleMeterRegistry registry;
    ReactiveHttpClientFactoryBean<Client> factory;
    ConnectionProvider provider;
    Client client;
    DisposableServer server;
    LoopResources serverLoops;
    volatile boolean checking = true;
    long subscriptions;
    boolean closed;

    V34WorkloadFixture(Profile profile, Scenario scenario, boolean loopback) {
        this.profile = profile;
        this.scenario = scenario;
        this.loopback = loopback;
        try {
            String baseUrl = "http://benchmark.invalid";
            if (loopback) {
                serverLoops = LoopResources.create("v34-server", 1, true);
                server = HttpServer.create().host("127.0.0.1").port(0).runOn(serverLoops)
                        .handle((request, response) -> {
                            dispatches.incrementAndGet();
                            return request.receive().aggregate().asByteArray().defaultIfEmpty(new byte[0])
                                    .flatMap(bytes -> {
                                        if (checking) {
                                            inspect(request.method().name(), request.uri(),
                                                    request.requestHeaders().get("X-Scope"),
                                                    request.requestHeaders().get("Content-Type"), bytes);
                                        }
                                        return response.status(status()).header("Content-Type", responseType())
                                                .header("X-Fixture", "v34")
                                                .sendString(Mono.just(responseBody())).then();
                                    });
                        }).bindNow(WAIT);
                baseUrl = "http://127.0.0.1:" + server.port();
            }
            if (profile == Profile.MINIMAL) {
                context.refresh();
            } else {
                context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("v34", Map.of(
                        "reactive.http.clients." + CLIENT + ".base-url", baseUrl,
                        "reactive.http.clients." + CLIENT + ".resilience.enabled",
                        profile == Profile.RESILIENCE_ENABLED_ONLY,
                        "reactive.http.observability.enabled", profile != Profile.OBSERVER && profile != Profile.HOOK)));
                if (profile == Profile.AUTO_REGISTRY) {
                    registry = new SimpleMeterRegistry();
                    context.registerBean("meterRegistry", SimpleMeterRegistry.class, () -> registry);
                }
                if (profile == Profile.OBSERVER) {
                    context.registerBean(HttpClientObserver.class, () -> event -> {
                        terminals.incrementAndGet();
                        if (!apiName().equals(event.getApiName()) || event.getAttemptCount() != 1) {
                            violation.compareAndSet(null, "observer API/attempt evidence");
                        }
                    });
                }
                if (profile == Profile.HOOK) {
                    context.registerBean(ReactiveHttpClientLifecycleHook.class, () -> new ReactiveHttpClientLifecycleHook() {
                        @Override public void onSuccess(ReactiveHttpClientLifecycleContext event) { terminal(event); }
                        @Override public void onError(ReactiveHttpClientLifecycleContext event) { terminal(event); }
                        private void terminal(ReactiveHttpClientLifecycleContext event) {
                            terminals.incrementAndGet();
                            if (!apiName().equals(event.apiName()) || event.attemptNumber() != 1) {
                                violation.compareAndSet(null, "hook API/attempt evidence");
                            }
                        }
                    });
                }
                if (!loopback) {
                    context.registerBean(ReactiveHttpClientCustomizer.class,
                            () -> builder -> builder.exchangeFunction(this::exchange));
                }
                context.register(JacksonAutoConfiguration.class, ReactiveHttpClientAutoConfiguration.class);
                context.refresh();
            }
            createProxy(baseUrl);
        } catch (RuntimeException | Error failure) {
            close();
            throw failure;
        }
    }

    void createProxy(String baseUrl) {
        if (profile == Profile.MINIMAL) {
            var builder = WebClient.builder().baseUrl(baseUrl);
            if (!loopback) builder.exchangeFunction(this::exchange);
            else {
                provider = ConnectionProvider.create("v34-minimal");
                builder.clientConnector(new ReactorClientHttpConnector(HttpClient.create(provider)));
            }
            var handler = ReactiveClientInvocationHandler.create(builder.build(), new MethodMetadataCache(),
                    new RequestArgumentResolver(), new DefaultErrorDecoder(),
                    new ReactiveHttpClientProperties.ClientConfig(), CLIENT, Client.class, context,
                    new NoopResilienceOperatorApplier(), new BenchmarkJsonCodecFactory().create(),
                    new ReactiveHttpClientProperties.ObservabilityConfig());
            client = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[]{Client.class}, handler);
        } else {
            factory = new ReactiveHttpClientFactoryBean<>();
            factory.setType(Client.class);
            factory.setApplicationContext(context);
            client = factory.getObject();
            try {
                var field = ReactiveHttpClientFactoryBean.class.getDeclaredField("connectionProvider");
                field.setAccessible(true);
                provider = (ConnectionProvider) field.get(factory);
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException(failure);
            }
        }
    }

    private Mono<ClientResponse> exchange(org.springframework.web.reactive.function.client.ClientRequest request) {
        dispatches.incrementAndGet();
        if (checking) inspect(request.method().name(), request.url().getRawPath()
                + (request.url().getRawQuery() == null ? "" : "?" + request.url().getRawQuery()),
                request.headers().getFirst("X-Scope"), request.headers().getFirst("Content-Type"), new byte[0]);
        return Mono.just(ClientResponse.create(HttpStatusCode.valueOf(status()))
                .header("Content-Type", responseType()).header("X-Fixture", "v34").body(responseBody()).build());
    }

    Mono<?> publisher() {
        return switch (scenario) {
            case GET -> client.get();
            case TARGET -> client.target("a/b", "summary", "scope");
            case STRING -> client.string("fixture", "text/plain;charset=UTF-8");
            case JSON -> client.json(PAYLOAD);
            case ENTITY -> client.entity();
            case EMPTY -> client.empty();
            case ERROR4 -> client.error4();
            case ERROR5 -> client.error5();
        };
    }

    Object subscribe(Mono<?> publisher) {
        subscriptions++;
        try {
            return publisher.block(WAIT);
        } catch (HttpClientException | RemoteServiceException expected) {
            if (scenario != Scenario.ERROR4 && scenario != Scenario.ERROR5) throw expected;
            return expected;
        }
    }

    void verify() {
        checking = true;
        long before = dispatches.get();
        Mono<?> publisher = publisher();
        require(dispatches.get() == before, "publisher assembly dispatched");
        for (int i = 0; i < 2; i++) {
            verifyResult(subscribe(publisher));
            require(dispatches.get() == before + i + 1, "ordinary resubscription dispatch count");
        }
        verifyTotals();
        if (profile != Profile.MINIMAL) {
            var properties = context.getBean(ReactiveHttpClientProperties.class);
            var config = properties.getClients().get(CLIENT);
            require(properties.getObservability().isEnabled() == (profile != Profile.OBSERVER && profile != Profile.HOOK),
                    "effective observability");
            require(config.getCache().getPolicy() == null && config.getResilience().getRetry() == null
                    && config.getResilience().getCircuitBreaker() == null
                    && config.getResilience().getRateLimiter() == null && config.getResilience().getBulkhead() == null,
                    "unintended selected feature");
            require(context.containsBean("micrometerHttpClientObserver") == (registry != null), "effective observer");
        }
        checking = false;
    }

    void verifyTotals() {
        require(violation.get() == null, "request/terminal witness: " + violation.get());
        require(dispatches.get() == subscriptions, "total dispatch count");
        if (profile == Profile.OBSERVER || profile == Profile.HOOK) {
            require(terminals.get() == subscriptions, "one terminal event per subscription");
        }
        if (registry != null && subscriptions != 0) {
            var timer = registry.find("reactive.http.client.requests").tags("client.name", CLIENT, "api.name", apiName()).timer();
            require(timer != null && timer.count() == subscriptions, "real API timer count");
            require(registry.find("reactive.http.client.requests").tag("api.name", "unknown").timer() == null,
                    "unknown API metric");
        }
    }

    void verifyResult(Object result) {
        switch (scenario) {
            case GET, TARGET, STRING -> require("value".equals(result), "decoded String");
            case JSON -> require(PAYLOAD.equals(result), "decoded JSON");
            case ENTITY -> {
                require(result instanceof ResponseEntity<?>, "response envelope");
                var entity = (ResponseEntity<?>) result;
                require(entity.getStatusCode().value() == 200 && "v34".equals(entity.getHeaders().getFirst("X-Fixture"))
                        && "value".equals(entity.getBody()), "response status/header/body");
            }
            case EMPTY -> require(result == null, "empty completion");
            case ERROR4 -> require(result instanceof HttpClientException error && error.getStatusCode() == 404
                    && "missing".equals(error.getResponseBody()), "4xx error mapping/body");
            case ERROR5 -> require(result instanceof RemoteServiceException error && error.getStatusCode() == 503
                    && "unavailable".equals(error.getResponseBody()), "5xx error mapping/body");
        }
    }

    private void inspect(String method, String target, String scope, String contentType, byte[] body) {
        String expectedTarget = switch (scenario) {
            case TARGET -> "/items/a%2Fb?view=summary";
            default -> "/" + scenario.name().toLowerCase(java.util.Locale.ROOT);
        };
        boolean post = scenario == Scenario.STRING || scenario == Scenario.JSON;
        if (!(post ? "POST" : "GET").equals(method) || !expectedTarget.equals(target)
                || (scenario == Scenario.TARGET && !"scope".equals(scope))) {
            violation.compareAndSet(null, "method/target/header");
        }
        if (loopback) {
            String expectedBody = scenario == Scenario.STRING ? "fixture" : scenario == Scenario.JSON ? JSON : "";
            if (!expectedBody.equals(new String(body, StandardCharsets.UTF_8))) violation.compareAndSet(null, "body bytes");
            if (post && (contentType == null || !contentType.startsWith(scenario == Scenario.STRING
                    ? "text/plain" : "application/json"))) violation.compareAndSet(null, "body media type");
        }
    }

    String apiName() { return "v34." + scenario.name().toLowerCase(java.util.Locale.ROOT); }
    private int status() { return switch (scenario) { case EMPTY -> 204; case ERROR4 -> 404; case ERROR5 -> 503; default -> 200; }; }
    private String responseType() { return scenario == Scenario.JSON ? "application/json" : "text/plain;charset=UTF-8"; }
    private String responseBody() { return switch (scenario) { case JSON -> JSON; case EMPTY -> ""; case ERROR4 -> "missing"; case ERROR5 -> "unavailable"; default -> "value"; }; }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        try {
            if (factory != null) factory.destroy();
            else if (provider != null) provider.disposeLater().block(WAIT);
        } finally {
            try { context.close(); }
            finally {
                if (registry != null) registry.close();
                try { if (server != null) server.disposeNow(WAIT); }
                finally { if (serverLoops != null) serverLoops.disposeLater(Duration.ZERO, WAIT).block(WAIT); }
            }
        }
        require(provider == null || provider.isDisposed(), "factory pool not disposed");
        require(!context.isActive() && (registry == null || registry.isClosed()), "context/registry not closed");
        require(server == null || server.isDisposed(), "server not disposed");
        require(serverLoops == null || serverLoops.isDisposed(), "server workers not disposed");
    }

    static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    public record Payload(String value) { }

    @ReactiveHttpClient(name = CLIENT)
    interface Client {
        @GET("/get") @ApiName("v34.get") Mono<String> get();
        @GET("/items/{id}") @ApiName("v34.target") Mono<String> target(@PathVar("id") String id,
                @QueryParam("view") String view, @HeaderParam("X-Scope") String scope);
        @POST("/string") @ApiName("v34.string") Mono<String> string(@Body String body, @HeaderParam("Content-Type") String contentType);
        @POST("/json") @ApiName("v34.json") Mono<Payload> json(@Body Payload body);
        @GET("/entity") @ApiName("v34.entity") Mono<ResponseEntity<String>> entity();
        @GET("/empty") @ApiName("v34.empty") Mono<String> empty();
        @GET("/error4") @ApiName("v34.error4") Mono<String> error4();
        @GET("/error5") @ApiName("v34.error5") Mono<String> error5();
    }
}
