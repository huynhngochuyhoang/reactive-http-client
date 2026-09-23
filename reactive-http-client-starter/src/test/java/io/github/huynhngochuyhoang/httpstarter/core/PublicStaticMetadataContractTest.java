package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PublicStaticMetadataContractTest {
    interface Parent<T> {
        Mono<T> value(String id);
        Flux<T> stream(String id);
    }
    interface Strings extends Parent<String> {}
    interface Integers extends Parent<Integer> {}
    interface NestedReturn { Mono<Mono<String>> nested(); }
    interface NonReactiveReturn { String ordinary(); }
    interface Simple { Mono<String> value(); }

    @Test
    void derivesOnlyTheMissingValueWithoutMutatingPublicMetadata() throws Exception {
        MethodMetadata metadata = fresh(Strings.class.getMethod("value", String.class));
        metadata.setTimeoutMs(123);
        RequestPlan plan = RequestPlan.from(metadata, Strings.class);
        assertThat(plan.staticEffectiveApi()).isEqualTo(new EffectiveApi("GET", "/value/{id}", -1));
        assertThat(plan.timeoutMs()).isEqualTo(123);
        assertThat(metadata.getStaticEffectiveApi()).isNull();
        assertThat(metadata.getRequestPlan()).isNull();
        metadata.getPathVars().clear();
        assertThat(plan.pathVars()).containsExactly(new RequestPlan.NamedArgumentBinding(0, "id"));
        assertThatThrownBy(() -> plan.pathVars().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void preservesSuppliedDerivedValueAndApiRefPrecedence() throws Exception {
        var metadata = fresh(Strings.class.getMethod("value", String.class));
        var supplied = new EffectiveApi("HEAD", "/supplied/{id}", 321);
        metadata.setStaticEffectiveApi(supplied);
        assertThat(RequestPlan.from(metadata, Strings.class).staticEffectiveApi()).isSameAs(supplied);
        var config = config();
        var api = new ReactiveHttpClientProperties.ApiConfig();
        api.setMethod("OPTIONS");
        api.setPath("/configured/{id}");
        api.setTimeoutMs(456);
        config.getApis().put("configured", api);
        metadata.setApiRefName("configured");
        var contracts = EffectiveHttpClientContractExporter.export(Strings.class, "static", config,
                new MetadataCache(meta -> {
                    meta.setStaticEffectiveApi(supplied);
                    meta.setApiRefName("configured");
                }));
        assertThat(contracts).allSatisfy(contract -> {
            assertThat(contract.httpMethod()).isEqualTo("OPTIONS");
            assertThat(contract.pathTemplate()).isEqualTo("/configured/{id}");
            assertThat(contract.timeout().source()).isEqualTo("api-ref");
            assertThat(contract.timeout().timeoutMs()).isEqualTo(456);
        });
    }

    enum Invalid {
        MISSING_METHOD, FOREIGN_METHOD, MISSING_VERB, UNSUPPORTED_VERB, MISSING_PATH,
        AUTHORITY, FRAGMENT, UNBOUND_VARIABLE, MALFORMED_TEMPLATE, WRONG_FLAGS,
        MISSING_ELEMENT, NESTED_PUBLISHER, NON_REACTIVE
    }

    @ParameterizedTest
    @EnumSource(Invalid.class)
    void rejectsIncompleteFreshMetadataDuringConcretePlanningBeforeDispatch(Invalid invalid) {
        Class<?> client = invalid == Invalid.NESTED_PUBLISHER ? NestedReturn.class
                : invalid == Invalid.NON_REACTIVE ? NonReactiveReturn.class : Strings.class;
        var metadata = new MetadataCache(meta -> {
            switch (invalid) {
                case MISSING_METHOD -> meta.setMethod(null);
                case FOREIGN_METHOD -> {
                    try { meta.setMethod(Object.class.getMethod("toString")); }
                    catch (NoSuchMethodException ex) { throw new AssertionError(ex); }
                }
                case MISSING_VERB -> meta.setHttpMethod(null);
                case UNSUPPORTED_VERB -> meta.setHttpMethod("CONNECT");
                case MISSING_PATH -> meta.setPathTemplate(null);
                case AUTHORITY -> meta.setPathTemplate("https://other.example.invalid/{id}");
                case FRAGMENT -> meta.setPathTemplate("/value/{id}#fragment");
                case UNBOUND_VARIABLE -> meta.setPathTemplate("/value/{missing}");
                case MALFORMED_TEMPLATE -> meta.setPathTemplate("/value/{id");
                case WRONG_FLAGS -> meta.setReturnsFlux(true);
                case MISSING_ELEMENT -> meta.setResponseType(null);
                default -> { }
            }
        });
        List<ClientRequest> requests = new ArrayList<>();
        try (var context = new GenericApplicationContext()) {
            context.refresh();
            assertThatThrownBy(() -> handler(context, client, metadata, new RequestArgumentResolver(),
                    config(), requests, "\"ok\""))
                    .isInstanceOf(IllegalStateException.class)
                    .satisfies(error -> assertThat(error.getMessage()).containsAnyOf(
                            "MethodMetadata", "URI template", "Unsupported declarative return type"));
            assertThat(requests).isEmpty();
        }
    }

    @Test
    void inheritedMonoAndFluxPlansStayClientLocalAndAreReusedWithoutSubscriptionParsing() {
        var metadata = new MetadataCache(ignored -> {});
        List<RequestPlan> plans = new ArrayList<>();
        RequestArgumentResolver resolver = new RequestArgumentResolver() {
            @Override public ResolvedArgs resolve(RequestPlan plan, Object[] args) {
                plans.add(plan);
                return super.resolve(plan, args);
            }
        };
        List<ClientRequest> requests = new ArrayList<>();
        try (var context = new GenericApplicationContext()) {
            context.refresh();
            var stringsHandler = handler(context, Strings.class, metadata, resolver, config(), requests, "text");
            var integersHandler = handler(context, Integers.class, metadata, resolver, config(), requests, "42");
            try {
                Strings strings = proxy(Strings.class, stringsHandler);
                Integers integers = proxy(Integers.class, integersHandler);
                Mono<String> first = strings.value("a/b");
                int lookups = metadata.lookups.get();
                assertThat(first.block(Duration.ofSeconds(5))).isEqualTo("text");
                assertThat(first.block(Duration.ofSeconds(5))).isEqualTo("text");
                assertThat(metadata.lookups).hasValue(lookups);
                assertThat(strings.value("second").block(Duration.ofSeconds(5))).isEqualTo("text");
                assertThat(integers.value("third").block(Duration.ofSeconds(5))).isEqualTo(42);
                assertThat(plans.get(0)).isSameAs(plans.get(1));
                assertThat(plans.get(0)).isNotSameAs(plans.get(2));
                assertThat(plans.get(0).responseType()).isEqualTo(String.class);
                assertThat(plans.get(2).responseType()).isEqualTo(Integer.class);
                assertThat(strings.stream("fourth").collectList().block(Duration.ofSeconds(5))).containsExactly("text");
                assertThat(integers.stream("fifth").collectList().block(Duration.ofSeconds(5))).containsExactly(42);
                assertThat(requests).extracting(request -> request.url().getRawPath())
                        .containsExactly("/value/a%2Fb", "/value/a%2Fb", "/value/second", "/value/third",
                                "/stream/fourth", "/stream/fifth");
                assertThat(metadata.entries).hasSize(2);
                assertThat(metadata.entries.values()).allSatisfy(meta -> {
                    assertThat(meta.getStaticEffectiveApi()).isNull();
                    assertThat(meta.getRequestPlan()).isNull();
                });
            } finally {
                close(stringsHandler);
                close(integersHandler);
            }
        }
    }

    @ParameterizedTest
    @CsvSource({"false,-1", "false,0", "false,123", "true,-1", "true,0", "true,123"})
    void timeoutPrecedenceRemainsMethodThenApiRefThenClient(boolean apiRef, long timeout) {
        var config = config();
        config.setRequestTimeoutMs(789);
        var api = new ReactiveHttpClientProperties.ApiConfig();
        api.setMethod("GET");
        api.setPath("/configured/{id}");
        api.setTimeoutMs(456);
        config.getApis().put("configured", api);
        var contracts = EffectiveHttpClientContractExporter.export(Strings.class, "static", config,
                new MetadataCache(meta -> {
                    meta.setTimeoutMs(timeout);
                    if (apiRef) { meta.setApiRefName("configured"); }
                }));
        assertThat(contracts).allSatisfy(contract -> {
            assertThat(contract.timeout().source()).isEqualTo(timeout != -1 ? "method" : apiRef ? "api-ref" : "client");
            assertThat(contract.timeout().timeoutMs()).isEqualTo(timeout != -1 ? timeout : apiRef ? 456 : 789);
        });
    }

    @Test
    void legacyHandlerWithoutConcreteInterfaceAlsoDerivesStaticRoutingAtInvocation() {
        var metadata = new MetadataCache(ignored -> {});
        List<ClientRequest> requests = new ArrayList<>();
        try (var context = new GenericApplicationContext()) {
            context.refresh();
            var webClient = WebClient.builder().baseUrl(config().getBaseUrl()).exchangeFunction(request -> {
                requests.add(request);
                return Mono.just(ClientResponse.create(HttpStatus.OK).body("legacy").build());
            }).build();
            var handler = new ReactiveClientInvocationHandler(webClient, metadata, new RequestArgumentResolver(),
                    new DefaultErrorDecoder(), config(), "static", context, new NoopResilienceOperatorApplier(),
                    TestJsonCodecs.jsonCodec(), new ReactiveHttpClientProperties.ObservabilityConfig());
            try {
                assertThat(proxy(Simple.class, handler).value().block(Duration.ofSeconds(5))).isEqualTo("legacy");
                assertThat(requests).singleElement().satisfies(request -> {
                    assertThat(request.method().name()).isEqualTo("GET");
                    assertThat(request.url().getPath()).isEqualTo("/value");
                });
            } finally { close(handler); }
        }
    }

    @Test
    void freshMetadataCacheIdentityIncludesResolvedPathAndValues() {
        var config = config();
        var policy = new ReactiveHttpClientProperties.CachePolicyConfig();
        policy.setTtlMs(60_000L);
        policy.setMaximumSize(10L);
        policy.setSharedResponse(true);
        config.getCache().getPolicies().put("read", policy);
        var metadata = new MetadataCache(meta -> {
            if (meta.isReturnsMono()) { meta.setCachePolicyName("read"); }
        });
        List<ClientRequest> requests = new ArrayList<>();
        try (var context = new GenericApplicationContext()) {
            context.refresh();
            var handler = handler(context, Strings.class, metadata, new RequestArgumentResolver(), config, requests, "cached");
            try {
                Strings client = proxy(Strings.class, handler);
                assertThat(client.value("one").block(Duration.ofSeconds(5))).isEqualTo("cached");
                assertThat(client.value("one").block(Duration.ofSeconds(5))).isEqualTo("cached");
                assertThat(client.value("two").block(Duration.ofSeconds(5))).isEqualTo("cached");
                assertThat(requests).extracting(request -> request.url().getPath())
                        .containsExactly("/value/one", "/value/two");
            } finally { close(handler); }
        }
    }

    @Test
    void emptyPathIsIntentionalAndArgumentOnlyResolutionStillNeedsNoEndpoint() throws Exception {
        var metadata = fresh(Strings.class.getMethod("value", String.class));
        metadata.setPathTemplate("");
        metadata.getPathVars().clear();
        assertThat(RequestPlan.from(metadata, Strings.class).staticEffectiveApi().pathTemplate()).isEmpty();
        var partial = new MethodMetadata();
        partial.getHeaderParams().put(0, "X-Value");
        assertThat(new RequestArgumentResolver().resolve(partial, new Object[]{"one"}).headers())
                .containsEntry("X-Value", List.of("one"));
    }

    private static MethodMetadata fresh(Method method) {
        var meta = new MethodMetadata();
        meta.setMethod(method);
        meta.setApiName(method.getName());
        meta.setHttpMethod("GET");
        meta.setPathTemplate("/" + method.getName() + (method.getParameterCount() == 0 ? "" : "/{id}"));
        if (method.getParameterCount() > 0) { meta.getPathVars().put(0, "id"); }
        meta.setReturnsMono(method.getReturnType() == Mono.class);
        meta.setReturnsFlux(method.getReturnType() == Flux.class);
        if (method.getGenericReturnType() instanceof ParameterizedType type) {
            meta.setResponseType(type.getActualTypeArguments()[0]);
        }
        return meta;
    }

    private static class MetadataCache extends MethodMetadataCache {
        final Map<Method, MethodMetadata> entries = new ConcurrentHashMap<>();
        final AtomicInteger lookups = new AtomicInteger();
        final Consumer<MethodMetadata> customize;
        MetadataCache(Consumer<MethodMetadata> customize) { this.customize = customize; }
        @Override public MethodMetadata get(Method method) {
            lookups.incrementAndGet();
            return entries.computeIfAbsent(method, key -> {
                var meta = fresh(key);
                customize.accept(meta);
                return meta;
            });
        }
    }

    private static ReactiveHttpClientProperties.ClientConfig config() {
        var config = new ReactiveHttpClientProperties.ClientConfig();
        config.setBaseUrl("http://metadata.example.invalid");
        return config;
    }

    private static ReactiveClientInvocationHandler handler(GenericApplicationContext context, Class<?> client,
            MethodMetadataCache metadata, RequestArgumentResolver resolver,
            ReactiveHttpClientProperties.ClientConfig config, List<ClientRequest> requests, String body) {
        var webClient = WebClient.builder().baseUrl(config.getBaseUrl()).exchangeFunction(request -> {
            requests.add(request);
            return Mono.just(ClientResponse.create(HttpStatus.OK).header("Content-Type", "application/json")
                    .body(body).build());
        }).build();
        return ReactiveClientInvocationHandler.create(webClient, metadata, resolver, new DefaultErrorDecoder(),
                config, "static", client, context, new NoopResilienceOperatorApplier(), TestJsonCodecs.jsonCodec(),
                new ReactiveHttpClientProperties.ObservabilityConfig());
    }

    private static <T> T proxy(Class<T> type, ReactiveClientInvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }

    private static void close(ReactiveClientInvocationHandler handler) {
        if (handler.responseCacheManager() != null) { handler.responseCacheManager().close(); }
    }
}
