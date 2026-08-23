package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.annotation.*;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import org.springframework.boot.webclient.WebClientCustomizer;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.io.Resource;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class DeclarativeCachePolicyTest {

    private final MethodMetadataCache metadataCache = new MethodMetadataCache();

    @Test
    void policyDefinitionsAreInertUntilExplicitlySelected() {
        ReactiveHttpClientProperties.ClientConfig config = configWithPolicy("unused", 0L, -1L);

        assertThatCode(() -> validate(UnselectedClient.class, "unselected-cache", config))
                .doesNotThrowAnyException();
    }

    @Test
    void clientSelectionMethodOverrideAndMethodExclusionHaveDeterministicPrecedence() {
        ReactiveHttpClientProperties.ClientConfig config = configWithPolicy("client-policy", 1_000L, 100L);
        addPolicy(config, "method-policy", 2_000L, 200L);
        config.getCache().setPolicy("client-policy");

        assertThatCode(() -> validate(PrecedenceClient.class, "cache-precedence", config))
                .doesNotThrowAnyException();

        Map<String, EffectiveHttpClientContract.CachePolicy> policies = EffectiveHttpClientContractExporter.export(
                        PrecedenceClient.class, "cache-precedence", config, metadataCache).stream()
                .collect(java.util.stream.Collectors.toMap(
                        contract -> contract.javaMethodSignature().substring(
                                0, contract.javaMethodSignature().indexOf('(')),
                        EffectiveHttpClientContract::cache));
        assertThat(policies.get("inherited")).isEqualTo(
                new EffectiveHttpClientContract.CachePolicy(true, "client", 1_000L, 100L));
        assertThat(policies.get("overridden")).isEqualTo(
                new EffectiveHttpClientContract.CachePolicy(true, "method", 2_000L, 200L));
        assertThat(policies.get("excluded")).isEqualTo(
                new EffectiveHttpClientContract.CachePolicy(false, "method-disabled", 0L, 0L));
    }

    @Test
    void selectedPolicyRequiresDeclaredPositivePracticalBounds() {
        assertInvalidBounds(configSelectingMissingPolicy(), "not declared under cache.policies");
        assertInvalidBounds(selectedPolicy(null, 1L), "ttl-ms is required");
        assertInvalidBounds(selectedPolicy(0L, 1L), "ttl-ms must be > 0");
        assertInvalidBounds(selectedPolicy(-1L, 1L), "ttl-ms must be > 0");
        assertInvalidBounds(selectedPolicy(EffectiveCachePolicy.MAX_TTL_MS + 1, 1L),
                "ttl-ms must be <= " + EffectiveCachePolicy.MAX_TTL_MS);
        assertInvalidBounds(selectedPolicy(1L, null), "maximum-size is required");
        assertInvalidBounds(selectedPolicy(1L, 0L), "maximum-size must be > 0");
        assertInvalidBounds(selectedPolicy(1L, -1L), "maximum-size must be > 0");
        assertInvalidBounds(selectedPolicy(1L, EffectiveCachePolicy.MAXIMUM_SIZE + 1),
                "maximum-size must be <= " + EffectiveCachePolicy.MAXIMUM_SIZE);
    }

    @Test
    void eligibilityAcceptsOnlyFiniteGetMonoContracts() {
        ReactiveHttpClientProperties.ClientConfig config = selectedPolicy(1_000L, 100L);

        assertThatCode(() -> validate(EligibleClient.class, "eligible-cache", config))
                .doesNotThrowAnyException();
        assertRejected(PostClient.class, config, "only GET methods");
        assertRejected(FluxClient.class, config, "Flux responses are streaming");
        assertRejected(VoidClient.class, config, "Mono<Void>");
        assertRejected(StreamingEnvelopeClient.class, config, "Publisher and streaming response values");
        assertRejected(DataBufferClient.class, config, "DataBuffer response values");
        assertRejected(ResourceClient.class, config, "Resource response values");
        assertRejected(PublisherValueClient.class, config, "Publisher and streaming response values");
        assertRejected(UnresolvedClient.class, config, "does not prove a finite cache-safe response shape");
        assertRejected(StreamBodyClient.class, config, "streaming or application-owned request bodies");
        assertRejected(UnresolvedBodyClient.class, config, "request body type is unresolved");
        assertRejected(MultipartClient.class, config, "multipart requests are not cache-eligible");
    }

    @Test
    void inheritedGenericOverloadsAndApiRefsResolveAgainstConcreteClient() {
        ReactiveHttpClientProperties.ClientConfig config = selectedPolicy(1_000L, 100L);
        ReactiveHttpClientProperties.ApiConfig api = new ReactiveHttpClientProperties.ApiConfig();
        api.setMethod("GET");
        api.setPath("/configured/{id}");
        config.getApis().put("configured", api);

        assertThatCode(() -> validate(ConcreteInheritedClient.class, "concrete-cache", config))
                .doesNotThrowAnyException();
        assertThatCode(() -> validate(OverloadedClient.class, "overloaded-cache", config))
                .doesNotThrowAnyException();
        assertThatCode(() -> validate(ApiRefCacheClient.class, "api-ref-cache", config))
                .doesNotThrowAnyException();

        api.setMethod("POST");
        assertThatThrownBy(() -> validate(ApiRefCacheClient.class, "api-ref-cache", config))
                .hasMessageContaining("api-ref-cache")
                .hasMessageContaining("configured")
                .hasMessageContaining("resolved HTTP method is POST");
    }

    @Test
    void annotationConflictsAndBlankSelectionsFailDuringMetadataParsing() throws Exception {
        assertThatThrownBy(() -> metadataCache.get(InvalidAnnotationClient.class.getMethod("conflict")))
                .hasMessageContaining("@CacheResponse cannot be combined with @CacheDisabled")
                .hasMessageContaining("conflict");
        assertThatThrownBy(() -> metadataCache.get(InvalidAnnotationClient.class.getMethod("blank")))
                .hasMessageContaining("@CacheResponse value must not be blank")
                .hasMessageContaining("blank");
    }

    @Test
    void selectedCachingRejectsUnknownOrIncompatibleBuilderCustomizations() {
        ReactiveHttpClientProperties.ClientConfig config = selectedPolicy(1_000L, 100L);
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            context.registerBean("bootMutation", WebClientCustomizer.class,
                    () -> builder -> builder.defaultHeader("X-Tenant", "dynamic"));
            context.registerBean("clientMutation", ReactiveHttpClientCustomizer.class,
                    () -> builder -> builder.filter((request, next) -> next.exchange(request)));
            context.registerBean("starterWebClientBuilder", WebClient.Builder.class, WebClient::builder);
            context.refresh();

            assertThatThrownBy(() -> metadataCache.validateDeclarativeCacheCustomizations(
                    context, EligibleClient.class, "eligible-cache", config))
                    .hasMessageContaining("eligible-cache")
                    .hasMessageContaining("method=")
                    .hasMessageContaining("bootMutation")
                    .hasMessageContaining("has no cache-safety classification");

            config.getCache().getCustomizations().put(
                    "bootMutation", ReactiveHttpClientProperties.CacheCustomizationSafety.SAFE);
            config.getCache().getCustomizations().put(
                    "clientMutation", ReactiveHttpClientProperties.CacheCustomizationSafety.INCOMPATIBLE);
            assertThatThrownBy(() -> metadataCache.validateDeclarativeCacheCustomizations(
                    context, EligibleClient.class, "eligible-cache", config))
                    .hasMessageContaining("clientMutation")
                    .hasMessageContaining("classified INCOMPATIBLE");

            config.getCache().getCustomizations().put(
                    "clientMutation", ReactiveHttpClientProperties.CacheCustomizationSafety.SAFE);
            assertThatThrownBy(() -> metadataCache.validateDeclarativeCacheCustomizations(
                    context, EligibleClient.class, "eligible-cache", config))
                    .hasMessageContaining("starterWebClientBuilder")
                    .hasMessageContaining("has no cache-safety classification");

            config.getCache().getCustomizations().put(
                    "starterWebClientBuilder", ReactiveHttpClientProperties.CacheCustomizationSafety.SAFE);
            assertThatCode(() -> metadataCache.validateDeclarativeCacheCustomizations(
                    context, EligibleClient.class, "eligible-cache", config))
                    .doesNotThrowAnyException();
        }
    }

    @Test
    void unselectedCachingDoesNotInspectCustomizerBeans() {
        ReactiveHttpClientProperties.ClientConfig config = configWithPolicy("unused", 1_000L, 100L);
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            context.registerBean("unknownMutation", WebClientCustomizer.class,
                    () -> builder -> builder.defaultHeader("X-Tenant", "dynamic"));
            context.refresh();

            assertThatCode(() -> metadataCache.validateDeclarativeCacheCustomizations(
                    context, UnselectedClient.class, "unselected-cache", config))
                    .doesNotThrowAnyException();
        }
    }

    @Test
    void replacementMetadataCacheControlsCacheSelectionValidation() throws Exception {
        MethodMetadata replacement = new MethodMetadataCache().get(
                UnselectedClient.class.getMethod("get"));
        replacement.setCachePolicyName("selected-by-replacement");
        MethodMetadataCache replacementCache = new MethodMetadataCache() {
            @Override
            public MethodMetadata get(java.lang.reflect.Method method) {
                return replacement;
            }
        };
        ReactiveHttpClientProperties.ClientConfig config = configWithPolicy(
                "selected-by-replacement", 1_000L, 100L);

        assertThatCode(() -> replacementCache.validateDeclarativeCachePolicies(
                UnselectedClient.class, "replacement-cache", config))
                .doesNotThrowAnyException();
    }

    private void assertInvalidBounds(ReactiveHttpClientProperties.ClientConfig config, String reason) {
        assertThatThrownBy(() -> validate(EligibleClient.class, "bounded-cache", config))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("bounded-cache")
                .hasMessageContaining("cachePolicy='selected'")
                .hasMessageContaining("method=")
                .hasMessageContaining(reason);
    }

    private void assertRejected(Class<?> client,
                                ReactiveHttpClientProperties.ClientConfig config,
                                String reason) {
        assertThatThrownBy(() -> validate(client, "rejected-cache", config))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("rejected-cache")
                .hasMessageContaining("concreteClient=" + client.getName())
                .hasMessageContaining("method=")
                .hasMessageContaining(reason);
    }

    private void validate(Class<?> client,
                          String clientName,
                          ReactiveHttpClientProperties.ClientConfig config) {
        metadataCache.validateDeclarativeCachePolicies(client, clientName, config);
    }

    private static ReactiveHttpClientProperties.ClientConfig selectedPolicy(Long ttlMs, Long maximumSize) {
        ReactiveHttpClientProperties.ClientConfig config = configWithPolicy("selected", ttlMs, maximumSize);
        config.getCache().setPolicy("selected");
        return config;
    }

    private static ReactiveHttpClientProperties.ClientConfig configSelectingMissingPolicy() {
        ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();
        config.getCache().setPolicy("selected");
        return config;
    }

    private static ReactiveHttpClientProperties.ClientConfig configWithPolicy(
            String name, Long ttlMs, Long maximumSize) {
        ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();
        addPolicy(config, name, ttlMs, maximumSize);
        return config;
    }

    private static void addPolicy(ReactiveHttpClientProperties.ClientConfig config,
                                  String name,
                                  Long ttlMs,
                                  Long maximumSize) {
        ReactiveHttpClientProperties.CachePolicyConfig policy = new ReactiveHttpClientProperties.CachePolicyConfig();
        policy.setTtlMs(ttlMs);
        policy.setMaximumSize(maximumSize);
        policy.setVaryByHeaders(List.of("Idempotency-Key"));
        config.getCache().getPolicies().put(name, policy);
    }

    interface ParentOperations<T> {
        @GET("/inherited")
        Mono<T> inherited();
    }

    @ReactiveHttpClient(name = "concrete-cache")
    interface ConcreteInheritedClient extends ParentOperations<String> {
    }

    interface PrecedenceParent {
        @GET("/inherited")
        Mono<String> inherited();
    }

    interface PrecedenceClient extends PrecedenceParent {
        @GET("/overridden")
        @CacheResponse("method-policy")
        Mono<ResponseEntity<String>> overridden();

        @GET("/excluded")
        @CacheDisabled
        Mono<String> excluded();
    }

    interface UnselectedClient {
        @GET("/value")
        Mono<String> get();
    }

    interface EligibleClient {
        @GET("/value")
        Mono<String> get();

        @GET("/entity")
        Mono<ResponseEntity<List<String>>> entity();
    }

    interface PostClient {
        @POST("/value")
        Mono<String> get();
    }

    interface FluxClient {
        @GET("/value")
        Flux<String> get();
    }

    interface VoidClient {
        @GET("/value")
        Mono<Void> get();
    }

    interface StreamingEnvelopeClient {
        @GET("/value")
        Mono<ResponseEntity<Flux<DataBuffer>>> get();
    }

    interface DataBufferClient {
        @GET("/value")
        Mono<DataBuffer> get();
    }

    interface ResourceClient {
        @GET("/value")
        Mono<Resource> get();
    }

    interface PublisherValueClient {
        @GET("/value")
        Mono<ResponseEntity<Publisher<String>>> get();
    }

    interface UnresolvedClient {
        @GET("/value")
        Mono<Object> get();
    }

    interface StreamBodyClient {
        @GET("/value")
        Mono<String> get(@Body InputStream input);
    }

    interface UnresolvedBodyClient<T> {
        @GET("/value")
        Mono<String> get(@Body T body);
    }

    interface MultipartClient {
        @GET("/value")
        @MultipartBody
        Mono<String> get(@FormField("name") String name);
    }

    interface OverloadedClient {
        @GET("/one/{id}")
        Mono<String> get(@PathVar("id") String id);

        @GET("/two/{id}")
        @CacheResponse("selected")
        Mono<String> get(@PathVar("id") long id);
    }

    interface ApiRefCacheClient {
        @ApiRef("configured")
        @CacheResponse("selected")
        Mono<String> get(@PathVar("id") String id);
    }

    interface InvalidAnnotationClient {
        @GET("/conflict")
        @CacheResponse("selected")
        @CacheDisabled
        Mono<String> conflict();

        @GET("/blank")
        @CacheResponse(" ")
        Mono<String> blank();
    }
}
