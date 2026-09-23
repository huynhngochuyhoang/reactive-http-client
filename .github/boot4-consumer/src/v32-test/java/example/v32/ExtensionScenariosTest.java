package example.v32;

import io.github.huynhngochuyhoang.httpstarter.annotation.Body;
import io.github.huynhngochuyhoang.httpstarter.annotation.CacheKey;
import io.github.huynhngochuyhoang.httpstarter.annotation.CacheResponse;
import io.github.huynhngochuyhoang.httpstarter.annotation.GET;
import io.github.huynhngochuyhoang.httpstarter.annotation.POST;
import io.github.huynhngochuyhoang.httpstarter.annotation.ReactiveHttpClient;
import io.github.huynhngochuyhoang.httpstarter.auth.AuthContext;
import io.github.huynhngochuyhoang.httpstarter.auth.AuthProvider;
import io.github.huynhngochuyhoang.httpstarter.auth.AuthProviderFactory;
import io.github.huynhngochuyhoang.httpstarter.auth.AuthRequest;
import io.github.huynhngochuyhoang.httpstarter.auth.InvalidatableAuthProvider;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientAutoConfiguration;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientBeanFactoryInitializationAotProcessor;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import io.github.huynhngochuyhoang.httpstarter.core.DefaultErrorDecoder;
import io.github.huynhngochuyhoang.httpstarter.core.Jackson3ReactiveHttpClientJsonCodec;
import io.github.huynhngochuyhoang.httpstarter.core.MethodMetadata;
import io.github.huynhngochuyhoang.httpstarter.core.MethodMetadataCache;
import io.github.huynhngochuyhoang.httpstarter.core.ReactiveHttpClientCustomizer;
import io.github.huynhngochuyhoang.httpstarter.core.ReactiveHttpClientDiagnosticsProvider;
import io.github.huynhngochuyhoang.httpstarter.core.ReactiveHttpClientJsonCodec;
import io.github.huynhngochuyhoang.httpstarter.core.ReactiveHttpClientLifecycleContext;
import io.github.huynhngochuyhoang.httpstarter.core.ReactiveHttpClientLifecycleHook;
import io.github.huynhngochuyhoang.httpstarter.core.RequestContext;
import io.github.huynhngochuyhoang.httpstarter.core.RequestContextSnapshot;
import io.github.huynhngochuyhoang.httpstarter.enable.EnableReactiveHttpClients;
import io.github.huynhngochuyhoang.httpstarter.exception.AuthProviderException;
import io.github.huynhngochuyhoang.httpstarter.filter.InboundHeadersWebFilter;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientCacheOutcome;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientObserver;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientObserverEvent;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.webclient.WebClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Scope;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.server.HttpServer;
import reactor.netty.resources.ConnectionProvider;
import reactor.util.context.Context;
import tools.jackson.databind.json.JsonMapper;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;

@Timeout(40)
class ExtensionScenariosTest {
    private static final Duration WAIT = Duration.ofSeconds(5);

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void starterManagedBuilderNeedsNoRedundantClassificationAcrossEntryPoints(boolean explicitSafe) {
        try (Scenario s = new Scenario()) {
            if (explicitSafe) {
                s.config().getCache().getCustomizations().put("starterWebClientBuilder",
                        ReactiveHttpClientProperties.CacheCustomizationSafety.SAFE);
            }
            s.runner().run(context -> {
                assertThat(context.getBeanFactory().getBeanDefinition("starterWebClientBuilder").getFactoryMethodName())
                        .isEqualTo("starterWebClientBuilder");
                assertThatCode(() -> context.getBean(MethodMetadataCache.class).validateDeclarativeCacheCustomizations(
                        context.getBeanFactory(), Client.class, "review", s.config())).doesNotThrowAnyException();
                assertThatCode(() -> context.getBean(MethodMetadataCache.class).validateDeclarativeCacheCustomizations(
                        context, Client.class, "review", s.config())).doesNotThrowAnyException();
                assertThat(new ReactiveHttpClientBeanFactoryInitializationAotProcessor()
                        .processAheadOfTime(context.getBeanFactory())).isNotNull();
                assertThat(context.getBean(ReactiveHttpClientDiagnosticsProvider.class).clientSummaries()).hasSize(1);
                assertThat(s.firstFactoryCreates).hasValue(0);
                assertThat(s.trace).isEmpty();
                assertThat(s.requests).isEmpty();

                Client client = context.getBean(Client.class);
                String result = call(client.read(), "one");
                assertThat(call(client.read(), "one")).isEqualTo(result).contains("/v1/read");
                assertThat(s.requests).hasSize(1);
                assertThat(s.events).extracting(HttpClientObserverEvent::getCacheOutcome)
                        .containsExactly(HttpClientCacheOutcome.MISS_LOADER, HttpClientCacheOutcome.FRESH_HIT);
            });
        }
    }

    @Test
    void capturedHeadersCrossAnExplicitWorkerHandoffWithoutLeakingThePreviousCaller() throws Exception {
        try (Scenario s = new Scenario(); var worker = Executors.newSingleThreadExecutor()) {
            s.runner().run(context -> {
                Client client = context.getBean(Client.class);
                Mono<String> cold = client.read();
                InboundHeadersWebFilter filter = new InboundHeadersWebFilter(s.properties.getInboundHeaders());
                for (String tenant : List.of("one", "two")) {
                    var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/capture")
                            .header("x-tenant", tenant).header("authorization", "synthetic-not-forwarded"));
                    var captured = new AtomicReference<RequestContextSnapshot>();
                    filter.filter(exchange, ignored -> Mono.deferContextual(ctx -> {
                        assertThat(RequestContext.inboundHeaders(ctx).get("X-Tenant")).isNull();
                        assertThat(RequestContext.inboundHeader(ctx, "X-TENANT")).contains(tenant);
                        assertThat(RequestContext.inboundHeader(ctx, "Authorization")).contains("[REDACTED]");
                        captured.set(RequestContextSnapshot.capture(ctx));
                        return Mono.empty();
                    })).block(WAIT);
                    var result = worker.submit(() -> {
                        assertThatThrownBy(() -> cold.block(WAIT)).isInstanceOf(AuthProviderException.class);
                        Context target = RequestContext.withInboundHeaders(Context.of("worker", "retained"),
                                Map.of("x-tenant", List.of("ambient")));
                        return Mono.deferContextual(ctx -> {
                            assertThat(ctx.<String>get("worker")).isEqualTo("retained");
                            assertThat(RequestContext.inboundHeader(ctx, "x-tenant")).contains(tenant);
                            return cold;
                        }).contextWrite(ctx -> captured.get().writeTo(target)).block(WAIT);
                    }).get(10, TimeUnit.SECONDS);
                    assertThat(result).contains(tenant + "|");
                }
                assertThat(s.requests).extracting(WireRequest::tenant).containsExactly("one", "two");
                assertThat(s.requests).allSatisfy(request -> assertThat(request.authorization()).isNull());
                assertThat(worker.submit(() -> RequestContext.inboundHeader(Context.empty(), "x-tenant"))
                        .get(5, TimeUnit.SECONDS)).isEmpty();
            });
        }
    }

    @Test
    void warmHitsRunBootDefaultsAuthAndClientGatesWithoutAMeterRegistry() {
        try (Scenario s = new Scenario()) {
            s.runner().run(context -> {
                assertThat(context.getBeansOfType(MeterRegistry.class)).isEmpty();
                Client client = context.getBean(Client.class);
                String first = call(client.read(), "one");
                assertThat(s.requests).hasSize(1);
                assertThat(s.trace).containsExactly("default", "boot", "auth", "client",
                        "default", "boot", "client");
                s.trace.clear();
                assertThat(call(client.read(), "one")).isEqualTo(first);
                assertThat(s.trace).containsExactly("default", "boot", "auth", "client");
                assertThat(s.requests).hasSize(1);
                assertThat(s.events).extracting(HttpClientObserverEvent::getCacheOutcome)
                        .containsExactly(HttpClientCacheOutcome.MISS_LOADER, HttpClientCacheOutcome.FRESH_HIT);
                assertThat(s.terminals).extracting(ReactiveHttpClientLifecycleContext::cacheOutcome)
                        .containsExactly(HttpClientCacheOutcome.MISS_LOADER, HttpClientCacheOutcome.FRESH_HIT);
                assertThat(s.events.get(1).getAttemptCount()).isZero();
                assertThat(s.events.get(1).getRequestUrl()).isNull();

                s.authAllowed.set(false);
                assertThatThrownBy(() -> call(client.read(), "one")).isInstanceOf(AuthProviderException.class);
                s.authAllowed.set(true);
                s.clientAllowed.set(false);
                assertThatThrownBy(() -> call(client.read(), "one")).hasMessageContaining("client gate");
                s.clientAllowed.set(true);
                s.defaultAllowed.set(false);
                assertThatThrownBy(() -> call(client.read(), "one")).hasMessageContaining("default gate");
                assertThat(s.requests).hasSize(1);
                assertThat(s.events).hasSize(5);
                assertThat(s.terminals).hasSize(5);
                assertThat(s.events.subList(2, 5)).allSatisfy(event -> {
                    assertThat(event.getError()).isNotNull();
                    assertThat(event.getAttemptCount()).isZero();
                    assertThat(event.getRequestUrl()).isNull();
                });
            });
        }
    }

    @Test
    void finalizedTenantAndRewrittenTargetsPartitionHits() {
        try (Scenario s = new Scenario()) {
            s.runner().run(context -> {
                Client client = context.getBean(Client.class);
                String first = call(client.read(), "one");
                assertThat(call(client.read(), "two")).isNotEqualTo(first).contains("two|");
                assertThat(call(client.read(), "one")).isEqualTo(first);
                s.route.set("/v2");
                String rewritten = call(client.read(), "one");
                assertThat(rewritten).isNotEqualTo(first).contains("/v2/read");
                assertThat(call(client.read(), "one")).isEqualTo(rewritten);
                assertThat(s.requests).extracting(WireRequest::path)
                        .containsExactly("/v1/read", "/v1/read", "/v2/read");
                assertThat(s.requests).extracting(WireRequest::tenant).containsExactly("one", "two", "one");
                assertThat(s.requests).allSatisfy(request -> assertThat(request.bootDefault()).isEqualTo("present"));
            });
        }
    }

    @Test
    void refreshedAuthCannotPublishItsResponseUnderTheEarlierIdentity() {
        try (Scenario s = new Scenario()) {
            s.rejectOnce.set(true);
            s.runner().run(context -> {
                Client client = context.getBean(Client.class);
                assertThat(call(client.read(), "one")).contains("|one-1|");
                assertThat(s.invalidations).hasValue(1);
                assertThat(s.requests).extracting(WireRequest::identity).containsExactly("one-0", "one-1");
                s.generation.set(0);
                String oldIdentity = call(client.read(), "one");
                assertThat(oldIdentity).contains("|one-0|");
                assertThat(call(client.read(), "one")).isEqualTo(oldIdentity);
                assertThat(s.requests).hasSize(3);
                s.generation.set(1);
                assertThat(call(client.read(), "one")).contains("|one-1|");
                assertThat(call(client.read(), "one")).contains("|one-1|");
                assertThat(s.requests).hasSize(4);
                assertThat(s.events).extracting(HttpClientObserverEvent::getCacheOutcome)
                        .containsExactly(HttpClientCacheOutcome.MISS_LOADER, HttpClientCacheOutcome.MISS_LOADER,
                                HttpClientCacheOutcome.FRESH_HIT, HttpClientCacheOutcome.MISS_LOADER,
                                HttpClientCacheOutcome.FRESH_HIT);
            });
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void orderedAuthFactoryAndNamedProviderPrecedenceAreObservable(boolean namedProvider) {
        try (Scenario s = new Scenario()) {
            if (namedProvider) { s.config().setAuthProvider("namedAuth"); }
            s.runner().run(context -> {
                assertThat(call(context.getBean(Client.class).plain(), "one")).contains("|one-0|");
                assertThat(s.firstFactoryCreates).hasValue(namedProvider ? 0 : 1);
                assertThat(s.secondFactoryCreates).hasValue(0);
            });
        }
    }

    @Test
    void freshStaticMetadataCannotSupplyTheInternalEffectiveApiThroughPublicConstruction() {
        // V32-F002 characterizes a gap, not the behavior to retain after an approved fix.
        try (Scenario s = new Scenario()) {
            s.metadataViaApiRef = false;
            s.runner().run(context -> {
                Client client = context.getBean(Client.class);
                assertThatThrownBy(client::mapped).isInstanceOf(NullPointerException.class);
                assertThat(s.requests).isEmpty();
                assertThat(s.events).isEmpty();
                assertThat(s.terminals).isEmpty();
            });
        }
    }

    @Test
    void customMetadataAndDecoderWorkThroughPublicReplacementBeans() {
        try (Scenario s = new Scenario()) {
            s.runner().run(context -> {
                Client client = context.getBean(Client.class);
                assertThat(call(client.mapped(), "one")).contains("/v1/mapped");
                assertThat(s.mappedMetadataCalls.get()).isPositive();
                assertThatThrownBy(() -> call(client.failure(), "one")).isInstanceOf(DomainFailure.class);
                assertThat(call(client.plain(), "one")).contains("/v1/plain");
                assertThat(s.decodes).hasValue(1);
                assertThat(s.requests).extracting(WireRequest::path)
                        .containsExactly("/v1/mapped", "/v1/failure", "/v1/plain");
                assertThat(s.events).hasSize(3);
                assertThat(s.terminals).hasSize(3);
                assertThat(s.events.get(1).getError()).isInstanceOf(DomainFailure.class);
                assertThat(s.terminals.get(1).error()).isInstanceOf(DomainFailure.class);
            });
        }
    }

    @Test
    void boundedCustomCodecExposesAndSendsTheSameBodyAndKeysItsRepresentation() {
        try (Scenario s = new Scenario()) {
            s.runner().run(context -> {
                Client client = context.getBean(Client.class);
                String first = call(client.search(new Search("alpha")), "one");
                assertThat(call(client.search(new Search("alpha")), "one")).isEqualTo(first);
                assertThat(call(client.search(new Search("beta")), "one")).isNotEqualTo(first);
                assertThat(s.requests).extracting(WireRequest::body)
                        .containsExactly("{\"application_term\":\"alpha\"}", "{\"application_term\":\"beta\"}");
                assertThat(s.authBodies).containsExactly(
                        "{\"application_term\":\"alpha\"}", "{\"application_term\":\"alpha\"}",
                        "{\"application_term\":\"beta\"}");
                assertThat(s.boundedWrites).hasValue(3);
                assertThat(s.unboundedWrites).hasValue(0);
            });
        }
    }

    @Test
    void aCodecWithoutBoundedSerializationIsAnExplicitConstraint() {
        try (Scenario s = new Scenario()) {
            s.boundedCodec = false;
            s.runner().run(context -> {
                Client client = context.getBean(Client.class);
                assertThatThrownBy(() -> call(client.search(new Search("alpha")), "one"))
                        .hasStackTraceContaining("Bounded JSON serialization is not implemented");
                assertThat(s.requests).isEmpty();
                assertThat(s.authBodies).isEmpty();
            });
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"bootDefaults", "clientMutations", "replacementBuilder"})
    void unclassifiedBuilderBehaviorIsRejectedInsteadOfSilentlyTrusted(String beanName) {
        try (Scenario s = new Scenario()) {
            s.config().getCache().getCustomizations().remove(beanName);
            var runner = s.runner();
            if (beanName.equals("replacementBuilder")) { runner = runner.withUserConfiguration(ReplacementBuilder.class); }
            runner.run(context -> {
                assertThatThrownBy(() -> new ReactiveHttpClientBeanFactoryInitializationAotProcessor()
                        .processAheadOfTime(context.getBeanFactory()))
                        .hasStackTraceContaining(beanName).hasStackTraceContaining("cache-safety classification");
                assertThatThrownBy(() -> context.getBean(ReactiveHttpClientDiagnosticsProvider.class).clientSummaries())
                        .hasStackTraceContaining(beanName).hasStackTraceContaining("cache-safety classification");
                assertThatThrownBy(() -> context.getBean(Client.class))
                        .hasStackTraceContaining(beanName).hasStackTraceContaining("cache-safety classification");
                assertThat(s.requests).isEmpty();
            });
        }
    }

    @Test
    void aClassifiedReplacementBuilderRetainsExplicitBootCustomization() {
        try (Scenario s = new Scenario()) {
            s.runner().withUserConfiguration(ReplacementBuilder.class).run(context -> {
                Client client = context.getBean(Client.class);
                String result = call(client.read(), "one");
                assertThat(call(client.read(), "one")).isEqualTo(result);
                assertThat(s.requests).hasSize(1);
                assertThat(s.requests.getFirst().builder()).isEqualTo("replacement");
                assertThat(s.requests.getFirst().bootDefault()).isEqualTo("present");
            });
        }
    }

    @Test
    void classifiedExchangeFunctionIsLoadOnlyWhileFiltersStillGateHits() {
        try (Scenario s = new Scenario()) {
            s.runner().withUserConfiguration(ReplacementExchange.class).run(context -> {
                Client client = context.getBean(Client.class);
                assertThat(call(client.read(), "one")).isEqualTo("replacement:one");
                assertThat(call(client.read(), "one")).isEqualTo("replacement:one");
                assertThat(call(client.read(), "two")).isEqualTo("replacement:two");
                assertThat(s.exchanges).hasValue(2);
                assertThat(s.requests).isEmpty();
                s.clientAllowed.set(false);
                assertThatThrownBy(() -> call(client.read(), "one")).hasMessageContaining("client gate");
                assertThat(s.exchanges).hasValue(2);
            });
        }
    }

    @Test
    void applicationOwnedConnectorRemainsUsableAfterTheStarterContextCloses() {
        try (Scenario s = new Scenario()) {
            var pool = ConnectionProvider.create("v32-application-owned", 1);
            s.connector = new ReactorClientHttpConnector(HttpClient.create(pool).disableRetry(true));
            try {
                s.runner().withUserConfiguration(ReplacementConnector.class).run(context -> {
                    assertThat(call(context.getBean(Client.class).plain(), "one")).contains("/v1/plain");
                    assertThat(pool.isDisposed()).isFalse();
                });
                assertThat(pool.isDisposed()).isFalse();
                String later = WebClient.builder().clientConnector(s.connector).baseUrl(s.baseUrl()).build()
                        .get().uri("/application-owned").retrieve().bodyToMono(String.class).block(WAIT);
                assertThat(later).contains("/application-owned");
                assertThat(s.requests).hasSize(2);
            } finally { pool.disposeLater().block(WAIT); }
            assertThat(pool.isDisposed()).isTrue();
        }
    }

    private static String call(Mono<String> request, String tenant) {
        return request.contextWrite(ctx -> RequestContext.withInboundHeaders(ctx,
                Map.of("x-tenant", List.of(tenant)))).block(WAIT);
    }

    @ReactiveHttpClient(name = "review")
    public interface Client {
        @GET("/read") @CacheResponse("read") Mono<String> read();
        @POST("/search") @CacheResponse(value = "search", semanticRead = true)
        Mono<String> search(@Body @CacheKey("body") Search body);
        @GET("/logical") Mono<String> mapped();
        @GET("/failure") Mono<String> failure();
        @GET("/plain") Mono<String> plain();
    }

    public record Search(String term) { }
    private static final class DomainFailure extends RuntimeException { }

    @Configuration(proxyBeanMethods = false)
    @EnableReactiveHttpClients(basePackageClasses = Client.class)
    static class Application {
        @Bean @Primary ReactiveHttpClientProperties properties(Scenario s) { return s.properties; }

        @Bean @Order(1) AuthProviderFactory firstFactory(Scenario s) { return s.factory(s.firstFactoryCreates); }
        @Bean @Order(2) AuthProviderFactory secondFactory(Scenario s) { return s.factory(s.secondFactoryCreates); }
        @Bean AuthProvider namedAuth(Scenario s) { return s.auth; }

        @Bean WebClientCustomizer bootDefaults(Scenario s) {
            return builder -> builder.defaultRequest(request -> {
                s.trace.add("default");
                if (!s.defaultAllowed.get()) { throw new IllegalStateException("default gate"); }
                request.header("X-Boot-Default", "present");
            }).filter((request, next) -> Mono.deferContextual(ctx -> {
                s.trace.add("boot");
                String tenant = RequestContext.inboundHeader(ctx, "X-Tenant").orElse("absent");
                return next.exchange(ClientRequest.from(request).headers(headers -> headers.set("X-Tenant", tenant)).build());
            }));
        }

        @Bean ReactiveHttpClientCustomizer clientMutations(Scenario s) {
            return builder -> builder.filter((request, next) -> Mono.defer(() -> {
                s.trace.add("client");
                if (!s.clientAllowed.get()) { return Mono.error(new IllegalStateException("client gate")); }
                var uri = UriComponentsBuilder.fromUri(request.url())
                        .replacePath(s.route.get() + request.url().getRawPath()).build(true).toUri();
                return next.exchange(ClientRequest.from(request).url(uri).build());
            }));
        }

        @Bean MethodMetadataCache metadata(Scenario s) {
            return new MethodMetadataCache() {
                @Override public MethodMetadata get(Method method) {
                    if (!method.getName().equals("mapped")) { return super.get(method); }
                    s.mappedMetadataCalls.incrementAndGet();
                    MethodMetadata metadata = new MethodMetadata();
                    metadata.setMethod(method);
                    metadata.setApiName("mapped");
                    metadata.setHttpMethod("GET");
                    metadata.setPathTemplate("/mapped");
                    if (s.metadataViaApiRef) { metadata.setApiRefName("mapped"); }
                    metadata.setReturnsMono(true);
                    metadata.setResponseType(String.class);
                    return metadata;
                }
            };
        }

        @Bean DefaultErrorDecoder decoder(Scenario s) {
            return new DefaultErrorDecoder() {
                @Override public Mono<? extends Throwable> decode(ClientResponse response) {
                    s.decodes.incrementAndGet();
                    return response.releaseBody().thenReturn(new DomainFailure());
                }
            };
        }

        @Bean ReactiveHttpClientJsonCodec codec(Scenario s) {
            var delegate = new Jackson3ReactiveHttpClientJsonCodec(JsonMapper.builder().build());
            class Codec implements ReactiveHttpClientJsonCodec {
                @Override public byte[] write(Object value) throws Exception {
                    s.unboundedWrites.incrementAndGet();
                    return delegate.write(value);
                }
                @Override public <T> T read(byte[] bytes, Class<T> type) throws Exception {
                    return delegate.read(bytes, type);
                }
            }
            if (!s.boundedCodec) { return new Codec(); }
            return new Codec() {
                @Override public byte[] writeBounded(Object value, int maximumBytes) throws Exception {
                    s.boundedWrites.incrementAndGet();
                    return delegate.writeBounded(Map.of("application_term", ((Search) value).term()), maximumBytes);
                }
            };
        }

        @Bean HttpClientObserver observer(Scenario s) { return s.events::add; }
        @Bean ReactiveHttpClientLifecycleHook lifecycle(Scenario s) {
            return new ReactiveHttpClientLifecycleHook() {
                @Override public void onSuccess(ReactiveHttpClientLifecycleContext event) { s.terminals.add(event); }
                @Override public void onError(ReactiveHttpClientLifecycleContext event) { s.terminals.add(event); }
                @Override public void onCancel(ReactiveHttpClientLifecycleContext event) { s.terminals.add(event); }
            };
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class ReplacementBuilder {
        @Bean @Scope("prototype") WebClient.Builder replacementBuilder(ObjectProvider<WebClientCustomizer> customizers) {
            var builder = WebClient.builder().defaultHeader("X-Builder", "replacement");
            customizers.orderedStream().forEach(customizer -> customizer.customize(builder));
            return builder;
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class ReplacementExchange {
        @Bean ReactiveHttpClientCustomizer replacementExchange(Scenario s) {
            return builder -> builder.exchangeFunction(request -> {
                s.exchanges.incrementAndGet();
                return Mono.just(ClientResponse.create(HttpStatus.OK).header("Content-Type", "text/plain")
                        .body("replacement:" + request.headers().getFirst("X-Tenant")).build());
            });
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class ReplacementConnector {
        @Bean ReactiveHttpClientCustomizer replacementConnector(Scenario s) {
            return builder -> builder.clientConnector(s.connector);
        }
    }

    private record WireRequest(String path, String tenant, String identity, String body,
                               String bootDefault, String builder, String authorization) { }

    private static final class Scenario implements AutoCloseable {
        final ReactiveHttpClientProperties properties = new ReactiveHttpClientProperties();
        final List<WireRequest> requests = new CopyOnWriteArrayList<>();
        final List<String> trace = new CopyOnWriteArrayList<>();
        final List<String> authBodies = new CopyOnWriteArrayList<>();
        final List<HttpClientObserverEvent> events = new CopyOnWriteArrayList<>();
        final List<ReactiveHttpClientLifecycleContext> terminals = new CopyOnWriteArrayList<>();
        final AtomicBoolean authAllowed = new AtomicBoolean(true);
        final AtomicBoolean defaultAllowed = new AtomicBoolean(true);
        final AtomicBoolean clientAllowed = new AtomicBoolean(true);
        final AtomicBoolean rejectOnce = new AtomicBoolean();
        final AtomicInteger generation = new AtomicInteger();
        final AtomicInteger invalidations = new AtomicInteger();
        final AtomicInteger firstFactoryCreates = new AtomicInteger();
        final AtomicInteger secondFactoryCreates = new AtomicInteger();
        final AtomicInteger mappedMetadataCalls = new AtomicInteger();
        final AtomicInteger decodes = new AtomicInteger();
        final AtomicInteger boundedWrites = new AtomicInteger();
        final AtomicInteger unboundedWrites = new AtomicInteger();
        final AtomicInteger exchanges = new AtomicInteger();
        final AtomicReference<String> route = new AtomicReference<>("/v1");
        boolean boundedCodec = true;
        boolean metadataViaApiRef = true;
        ReactorClientHttpConnector connector;
        final DisposableServer server;
        final InvalidatableAuthProvider auth = new InvalidatableAuthProvider() {
            @Override public Mono<AuthContext> getAuth(AuthRequest request) {
                return Mono.deferContextual(ctx -> {
                    trace.add("auth");
                    String tenant = RequestContext.inboundHeader(ctx, "X-Tenant")
                            .orElseThrow(() -> new IllegalStateException("required tenant absent"));
                    if (!authAllowed.get()) { return Mono.error(new IllegalStateException("auth gate")); }
                    assertThat(request.request().headers().getFirst("X-Tenant")).isEqualTo(tenant);
                    assertThat(request.request().headers().getFirst("X-Boot-Default")).isEqualTo("present");
                    if (request.requestBody() instanceof byte[] bytes) {
                        authBodies.add(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
                    }
                    return Mono.just(AuthContext.builder().header("X-Identity", tenant + "-" + generation.get()).build());
                });
            }
            @Override public Mono<Void> invalidate() {
                return Mono.fromRunnable(() -> { invalidations.incrementAndGet(); generation.incrementAndGet(); });
            }
        };

        Scenario() {
            server = HttpServer.create().host("127.0.0.1").port(0).handle((request, response) ->
                    request.receive().aggregate().asString().defaultIfEmpty("").flatMap(body -> {
                        var headers = request.requestHeaders();
                        var wire = new WireRequest(java.net.URI.create(request.uri()).getRawPath(),
                                headers.get("X-Tenant"), headers.get("X-Identity"),
                                body, headers.get("X-Boot-Default"), headers.get("X-Builder"), headers.get("Authorization"));
                        requests.add(wire);
                        if (rejectOnce.compareAndSet(true, false)) { return response.status(401).send().then(); }
                        if (request.path().endsWith("/failure")) {
                            return response.status(503).sendString(Mono.just("synthetic failure")).then();
                        }
                        return response.header("Content-Type", "text/plain").sendString(Mono.just(
                                wire.tenant() + "|" + wire.identity() + "|" + wire.path() + "|" + body)).then();
                    })).bindNow(WAIT);
            properties.getInboundHeaders().setAllowList(Set.of("x-tenant", "authorization"));
            properties.getObservability().getCache().setEnabled(true);
            var config = new ReactiveHttpClientProperties.ClientConfig();
            config.setBaseUrl(baseUrl());
            config.setLogicalCallTimeoutMs(5_000);
            config.setDefaultHeaders(Map.of("X-Tenant", "unresolved", "X-Identity", "unresolved"));
            var mapped = new ReactiveHttpClientProperties.ApiConfig();
            mapped.setMethod("GET");
            mapped.setPath("/mapped");
            config.getApis().put("mapped", mapped);
            var authConfig = new ReactiveHttpClientProperties.AuthConfig();
            authConfig.setType("review");
            config.setAuth(authConfig);
            var cache = config.getCache();
            cache.getPolicies().put("read", policy(false));
            cache.getPolicies().put("search", policy(true));
            // Only application-owned mutations need explicit classification.
            for (String name : List.of("bootDefaults", "clientMutations", "replacementBuilder",
                    "replacementExchange", "replacementConnector")) {
                cache.getCustomizations().put(name, ReactiveHttpClientProperties.CacheCustomizationSafety.SAFE);
            }
            properties.getClients().put("review", config);
        }

        private ReactiveHttpClientProperties.CachePolicyConfig policy(boolean body) {
            var policy = new ReactiveHttpClientProperties.CachePolicyConfig();
            policy.setTtlMs(60_000L);
            policy.setMaximumSize(16L);
            policy.setVaryByHeaders(List.of("Idempotency-Key", "X-Tenant", "X-Identity"));
            if (body) { policy.setVaryByParameters(List.of("body")); }
            return policy;
        }

        AuthProviderFactory factory(AtomicInteger calls) {
            return new AuthProviderFactory() {
                @Override public boolean supports(String type) { return "review".equals(type); }
                @Override public AuthProvider create(String clientName, ReactiveHttpClientProperties.AuthConfig config,
                                                     WebClient.Builder builder) {
                    calls.incrementAndGet();
                    return auth;
                }
            };
        }

        String baseUrl() { return "http://127.0.0.1:" + server.port(); }
        ReactiveHttpClientProperties.ClientConfig config() { return properties.getClients().get("review"); }
        ApplicationContextRunner runner() {
            return new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(ReactiveHttpClientAutoConfiguration.class))
                    .withUserConfiguration(Application.class).withBean(Scenario.class, () -> this,
                            definition -> ((AbstractBeanDefinition) definition).setDestroyMethodName(""));
        }
        @Override public void close() { server.disposeNow(WAIT); }
    }
}
