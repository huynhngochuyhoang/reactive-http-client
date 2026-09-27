package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.annotation.PathVar;
import io.github.huynhngochuyhoang.httpstarter.annotation.ReactiveHttpClient;
import io.github.huynhngochuyhoang.httpstarter.auth.AuthContext;
import io.github.huynhngochuyhoang.httpstarter.auth.AuthProvider;
import io.github.huynhngochuyhoang.httpstarter.auth.OutboundAuthFilter;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientAutoConfiguration;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientBeanFactoryInitializationAotProcessor;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientCacheOutcome;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientObserver;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientObserverEvent;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.boot.webclient.WebClientCustomizer;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Timeout(30)
class V33CrossPathContractTest {
    private static final String NAME = "cross-path";
    private static final Duration WAIT = Duration.ofSeconds(5);

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void selectedExtensionsAgreeAcrossPlanningInspectionAndCalls(boolean publicHandler) throws Exception {
        try (var fixture = new Fixture()) {
            var context = fixture.context;
            var factory = context.getDefaultListableBeanFactory();
            var config = fixture.config();
            assertThat(factory.getBeanDefinition("selected").isPrimary()).isFalse();
            assertThat(context.getBeanProvider(ReactiveHttpClientProperties.class).getObject()).isSameAs(fixture.properties);
            assertThat(config.getCache().getCustomizations()).doesNotContainKey("starterWebClientBuilder");
            assertThat(factory.getBeanDefinition("starterWebClientBuilder").getFactoryMethodName())
                    .isEqualTo("starterWebClientBuilder");
            for (var entry : List.of(context, factory)) {
                fixture.metadata.validateDeclarativeCacheCustomizations(entry, Client.class, NAME, config);
            }

            var method = Client.class.getMethod("read", String.class);
            var metadata = fixture.metadata.get(method);
            var plan = RequestPlan.from(metadata, Client.class);
            assertThat(metadata.getStaticEffectiveApi()).isNull();
            assertThat(plan.staticEffectiveApi()).isEqualTo(new EffectiveApi("GET", "/items/{id}", -1));
            assertThat(EffectiveCachePolicy.decide(plan, config, "GET").selection().policyName()).isEqualTo("chosen");
            var contracts = EffectiveHttpClientContractExporter.export(Client.class, NAME, config, fixture.metadata);
            assertThat(contracts).singleElement().satisfies(contract -> {
                assertThat(contract.httpMethod()).isEqualTo("GET");
                assertThat(contract.pathTemplate()).isEqualTo("/items/{id}");
                assertThat(contract.cache().enabled()).isTrue();
                assertThat(contract.cache().source()).isEqualTo("method");
                assertThat(contract.cache().ttlMs()).isEqualTo(60_000);
                assertThat(contract.cache().maximumSize()).isEqualTo(8);
                assertThat(contract.cache().varyByHeaders()).containsExactly("idempotency-key", "x-identity", "x-tenant");
                assertThat(contract.cache().sharedResponse()).isFalse();
            });
            var diagnostics = context.getBean(ReactiveHttpClientDiagnosticsProvider.class);
            assertConfiguredDiagnostics(diagnostics, null);
            assertThat(new ReactiveHttpClientBeanFactoryInitializationAotProcessor().processAheadOfTime(factory)).isNotNull();
            assertThat(fixture.metadata.validated).isSameAs(config);
            assertThat(fixture.factories).hasValue(0);
            assertThat(fixture.authCreations).hasValue(0);
            assertThat(fixture.customizerCreations).hasValue(0);
            assertThat(fixture.wire).isEmpty();

            Client client = fixture.client(publicHandler);
            Mono<String> cold = client.read("a/b");
            int parsed = fixture.metadata.lookups.get();
            assertThat(fixture.wire).isEmpty();
            String first = fixture.call(cold, "one");
            assertThat(first).isEqualTo("GET|/v1/items/a%2Fb|one|one-identity");
            int authorized = fixture.authorizations.get();
            fixture.trace.clear();
            assertThat(fixture.call(cold, "one")).isEqualTo(first);
            assertThat(fixture.authorizations).hasValue(authorized + 1);
            assertThat(fixture.trace).containsExactly("default", "upstream", "auth", "client");
            assertThat(fixture.wire).hasSize(1);
            assertThat(fixture.call(cold, "two")).isEqualTo("GET|/v1/items/a%2Fb|two|two-identity");
            fixture.route.set("/v2");
            assertThat(fixture.call(cold, "one")).isEqualTo("GET|/v2/items/a%2Fb|one|one-identity");
            assertThat(fixture.call(cold, "one")).isEqualTo("GET|/v2/items/a%2Fb|one|one-identity");
            assertThat(fixture.metadata.lookups).hasValue(parsed);
            assertThat(fixture.wire).containsExactly(first, "GET|/v1/items/a%2Fb|two|two-identity",
                    "GET|/v2/items/a%2Fb|one|one-identity");
            assertThat(fixture.events).extracting(HttpClientObserverEvent::getCacheOutcome).containsExactly(
                    HttpClientCacheOutcome.MISS_LOADER, HttpClientCacheOutcome.FRESH_HIT,
                    HttpClientCacheOutcome.MISS_LOADER, HttpClientCacheOutcome.MISS_LOADER, HttpClientCacheOutcome.FRESH_HIT);
            assertThat(fixture.events).extracting(HttpClientObserverEvent::getAttemptCount).containsExactly(1, 0, 1, 1, 0);
            assertThat(fixture.terminals).extracting(ReactiveHttpClientLifecycleContext::cacheOutcome)
                    .containsExactlyElementsOf(fixture.events.stream().map(HttpClientObserverEvent::getCacheOutcome).toList());
            fixture.events.stream().filter(event -> event.getAttemptCount() == 0).forEach(event -> {
                assertThat(event.getRequestUrl()).isNull();
                assertThat(event.getStatusCode()).isNull();
            });
            assertConfiguredDiagnostics(diagnostics, publicHandler ? null : 3L);

            for (var gate : List.of(fixture.defaultAllowed, fixture.authAllowed, fixture.clientAllowed)) {
                gate.set(false);
                assertThatThrownBy(() -> fixture.call(cold, "one")).hasStackTraceContaining("gate");
                gate.set(true);
            }
            assertThat(fixture.wire).hasSize(3);
            assertThat(fixture.events).hasSize(8);
            assertThat(fixture.terminals).hasSize(8);
            assertThat(fixture.events.subList(5, 8)).allSatisfy(event -> {
                assertThat(event.getError()).isNotNull();
                assertThat(event.getAttemptCount()).isZero();
                assertThat(event.getRequestUrl()).isNull();
            });
        }
    }

    private static void assertConfiguredDiagnostics(ReactiveHttpClientDiagnosticsProvider diagnostics, Long entries) {
        assertThat(diagnostics.clientSnapshotEntries()).singleElement().satisfies(entry -> {
            assertThat(entry.summary().endpointCount()).isEqualTo(1);
            assertThat(entry.cache().policyCount()).isEqualTo(1);
            assertThat(entry.cache().phase()).isEqualTo("local-ttl");
            assertThat(entry.cache().ttlMs()).isEqualTo(60_000L);
            assertThat(entry.cache().maximumSize()).isEqualTo(8);
            assertThat(entry.cache().policySources()).containsExactly("method");
            assertThat(entry.cache().httpMethods()).containsExactly("GET");
            assertThat(entry.cache().entryCount()).isEqualTo(entries);
        });
    }

    @ReactiveHttpClient(name = NAME)
    interface Client { Mono<String> read(@PathVar("id") String id); }

    static class FreshMetadata extends MethodMetadataCache {
        final AtomicInteger lookups = new AtomicInteger();
        ReactiveHttpClientProperties.ClientConfig validated;
        @Override public MethodMetadata get(Method method) {
            if (method.getDeclaringClass() != Client.class) return super.get(method);
            lookups.incrementAndGet();
            var metadata = new MethodMetadata();
            metadata.setMethod(method);
            metadata.setApiName("read");
            metadata.setHttpMethod("GET");
            metadata.setPathTemplate("/items/{id}");
            metadata.getPathVars().put(0, "id");
            metadata.setReturnsMono(true);
            metadata.setResponseType(String.class);
            metadata.setCachePolicyName("chosen");
            return metadata;
        }
        @Override public void validateDeclarativeCachePolicies(Class<?> type, String name,
                ReactiveHttpClientProperties.ClientConfig config) {
            validated = config;
            super.validateDeclarativeCachePolicies(type, name, config);
        }
    }

    private static final class Fixture implements AutoCloseable {
        final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        final ReactiveHttpClientProperties properties = new ReactiveHttpClientProperties();
        final FreshMetadata metadata = new FreshMetadata();
        final List<String> wire = new CopyOnWriteArrayList<>();
        final List<String> trace = new CopyOnWriteArrayList<>();
        final List<HttpClientObserverEvent> events = new CopyOnWriteArrayList<>();
        final List<ReactiveHttpClientLifecycleContext> terminals = new CopyOnWriteArrayList<>();
        final AtomicInteger factories = new AtomicInteger();
        final AtomicInteger authCreations = new AtomicInteger();
        final AtomicInteger customizerCreations = new AtomicInteger();
        final AtomicInteger authorizations = new AtomicInteger();
        final AtomicBoolean defaultAllowed = new AtomicBoolean(true);
        final AtomicBoolean authAllowed = new AtomicBoolean(true);
        final AtomicBoolean clientAllowed = new AtomicBoolean(true);
        final AtomicReference<String> route = new AtomicReference<>("/v1");
        final DisposableServer server;
        ReactiveClientInvocationHandler handler;

        Fixture() {
            server = HttpServer.create().host("127.0.0.1").port(0).handle((request, response) -> {
                String value = request.method().name() + "|" + request.uri() + "|"
                        + request.requestHeaders().get("X-Tenant") + "|" + request.requestHeaders().get("X-Identity");
                wire.add(value);
                return response.header("Content-Type", "text/plain").sendString(Mono.just(value));
            }).bindNow(WAIT);
            properties.getObservability().getCache().setEnabled(true);
            var config = new ReactiveHttpClientProperties.ClientConfig();
            config.setBaseUrl("http://127.0.0.1:" + server.port());
            config.setAuthProvider("auth");
            config.setDefaultHeaders(Map.of("X-Tenant", "unresolved", "X-Identity", "unresolved"));
            var policy = new ReactiveHttpClientProperties.CachePolicyConfig();
            policy.setTtlMs(60_000L);
            policy.setMaximumSize(8L);
            policy.setVaryByHeaders(List.of("Idempotency-Key", "X-Tenant", "X-Identity"));
            config.getCache().getPolicies().put("chosen", policy);
            for (String name : List.of("bootDefaults", "clientMutation")) {
                config.getCache().getCustomizations().put(name, ReactiveHttpClientProperties.CacheCustomizationSafety.SAFE);
            }
            properties.getClients().put(NAME, config);
            context.register(ReactiveHttpClientAutoConfiguration.class);
            context.registerBean("selected", ReactiveHttpClientProperties.class, () -> properties);
            context.addBeanFactoryPostProcessor(factory -> {
                for (String name : factory.getBeanNamesForType(ReactiveHttpClientProperties.class, true, false)) {
                    ((AbstractBeanDefinition) factory.getBeanDefinition(name)).setFallback(!name.equals("selected"));
                }
            });
            context.registerBean("metadata", MethodMetadataCache.class, () -> metadata);
            context.registerBean("observer", HttpClientObserver.class, () -> events::add);
            context.registerBean("lifecycle", ReactiveHttpClientLifecycleHook.class, () -> new ReactiveHttpClientLifecycleHook() {
                @Override public void onSuccess(ReactiveHttpClientLifecycleContext event) { terminals.add(event); }
                @Override public void onError(ReactiveHttpClientLifecycleContext event) { terminals.add(event); }
                @Override public void onCancel(ReactiveHttpClientLifecycleContext event) { terminals.add(event); }
            });
            context.registerBean("bootDefaults", WebClientCustomizer.class, () -> builder -> builder
                    .defaultRequest(request -> {
                        trace.add("default");
                        if (!defaultAllowed.get()) throw new IllegalStateException("default gate");
                    }).filter((request, next) -> Mono.deferContextual(ctx -> {
                        trace.add("upstream");
                        String tenant = RequestContext.inboundHeader(ctx, "X-Tenant").orElseThrow();
                        return next.exchange(ClientRequest.from(request).headers(headers -> headers.set("X-Tenant", tenant)).build());
                    })), definition -> definition.setLazyInit(true));
            context.registerBean("clientMutation", ReactiveHttpClientCustomizer.class, () -> {
                customizerCreations.incrementAndGet();
                return builder -> builder.filter((request, next) -> {
                    trace.add("client");
                    if (!clientAllowed.get()) return Mono.error(new IllegalStateException("client gate"));
                    return next.exchange(ClientRequest.from(request).url(UriComponentsBuilder.fromUri(request.url())
                            .replacePath(route.get() + request.url().getRawPath()).build(true).toUri()).build());
                });
            }, definition -> definition.setLazyInit(true));
            context.registerBean("auth", AuthProvider.class, () -> {
                authCreations.incrementAndGet();
                return request -> Mono.deferContextual(ctx -> {
                    trace.add("auth");
                    authorizations.incrementAndGet();
                    if (!authAllowed.get()) return Mono.error(new IllegalStateException("auth gate"));
                    String tenant = RequestContext.inboundHeader(ctx, "X-Tenant").orElseThrow();
                    assertThat(request.request().headers().getFirst("X-Tenant")).isEqualTo(tenant);
                    return Mono.just(AuthContext.builder().header("X-Identity", tenant + "-identity").build());
                });
            }, definition -> definition.setLazyInit(true));
            var definition = new RootBeanDefinition(ReactiveHttpClientFactoryBean.class, () -> {
                factories.incrementAndGet();
                return new ReactiveHttpClientFactoryBean<>();
            });
            definition.getPropertyValues().add("type", Client.class);
            definition.setAttribute(FactoryBean.OBJECT_TYPE_ATTRIBUTE, Client.class);
            definition.setLazyInit(true);
            context.registerBeanDefinition("client", definition);
            context.refresh();
        }

        ReactiveHttpClientProperties.ClientConfig config() { return properties.getClients().get(NAME); }

        Client client(boolean direct) {
            if (!direct) return context.getBean(Client.class);
            var auth = context.getBean("auth", AuthProvider.class);
            var builder = context.getBean(WebClient.Builder.class).baseUrl(config().getBaseUrl())
                    .filter(new OutboundAuthFilter(NAME, auth));
            context.getBean(ReactiveHttpClientCustomizer.class).customize(builder);
            handler = ReactiveClientInvocationHandler.create(builder.build(), metadata, new RequestArgumentResolver(),
                    new DefaultErrorDecoder(), config(), NAME, Client.class, context, new NoopResilienceOperatorApplier(),
                    TestJsonCodecs.jsonCodec(), properties.getObservability(), auth, config().getBaseUrl());
            return (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[]{Client.class}, handler);
        }

        String call(Mono<String> publisher, String tenant) {
            return publisher.contextWrite(ctx -> RequestContext.withInboundHeaders(ctx, Map.of("x-tenant", List.of(tenant)))).block(WAIT);
        }

        @Override public void close() {
            if (handler != null) handler.responseCacheManager().close();
            context.close();
            server.disposeNow(WAIT);
        }
    }
}
