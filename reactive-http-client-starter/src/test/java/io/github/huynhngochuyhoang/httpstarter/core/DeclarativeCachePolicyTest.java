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
import java.io.Reader;
import java.nio.channels.ReadableByteChannel;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

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
                new EffectiveHttpClientContract.CachePolicy(
                        true, "client", false, 1_000L, 100L,
                        List.of(), List.of("idempotency-key"), List.of(), List.of(), false, false, 0, 0));
        assertThat(policies.get("overridden")).isEqualTo(
                new EffectiveHttpClientContract.CachePolicy(
                        true, "method", false, 2_000L, 200L,
                        List.of(), List.of("idempotency-key"), List.of(), List.of(), false, false, 0, 0));
        assertThat(policies.get("excluded")).isEqualTo(
                new EffectiveHttpClientContract.CachePolicy(
                        false, "method-disabled", false, 0L, 0L,
                        List.of(), List.of(), List.of(), List.of(), false, false, 0, 0));
    }

    @Test
    void effectiveContractExportsNormalizedIsolationPolicy() {
        ReactiveHttpClientProperties.ClientConfig config = selectedPolicy(1_000L, 100L);
        ReactiveHttpClientProperties.CachePolicyConfig policy = config.getCache().getPolicies().get("selected");
        policy.setVaryByParameters(List.of(" tenant "));
        policy.setVaryByHeaders(List.of(" X-Tenant "));
        policy.setVaryByContext(List.of(" region ", "locale"));
        policy.setNonCacheableResponseHeaders(List.of(" X-Session ", "X-Caller"));
        policy.setSharedResponse(true);
        policy.setSingleFlight(true);
        policy.setRefreshAfterMs(400L);
        policy.setRefreshTimeoutMs(250L);
        config.setDefaultHeaders(Map.of("X-Tenant", "public"));

        EffectiveHttpClientContract contract = EffectiveHttpClientContractExporter.export(
                VariantContractClient.class, "variant-contract", config, metadataCache).get(0);

        assertThat(contract.cache()).isEqualTo(new EffectiveHttpClientContract.CachePolicy(
                true, "client", false, 1_000L, 100L,
                List.of("tenant"), List.of("x-tenant"), List.of("locale", "region"),
                List.of("x-caller", "x-session"), true, true, 400, 250));
    }

    @Test
    void configuredNonCacheableResponseHeadersAreBoundedAndValidated() {
        ReactiveHttpClientProperties.ClientConfig config = selectedPolicy(1_000L, 100L);
        ReactiveHttpClientProperties.CachePolicyConfig policy =
                config.getCache().getPolicies().get("selected");
        policy.setNonCacheableResponseHeaders(List.of(" X-Caller ", "x-caller"));

        assertThatCode(() -> validate(EligibleClient.class, "eligible-cache", config))
                .doesNotThrowAnyException();
        assertThat(EffectiveHttpClientContractExporter.export(
                        EligibleClient.class, "eligible-cache", config, metadataCache).get(0)
                .cache().nonCacheableResponseHeaders())
                .containsExactly("x-caller");

        policy.setNonCacheableResponseHeaders(List.of("Bad Header"));
        assertThatThrownBy(() -> validate(EligibleClient.class, "eligible-cache", config))
                .hasMessageContaining("invalid header name");

        policy.setNonCacheableResponseHeaders(java.util.stream.IntStream.range(0, 33)
                .mapToObj(index -> "X-Caller-" + index)
                .toList());
        assertThatThrownBy(() -> validate(EligibleClient.class, "eligible-cache", config))
                .hasMessageContaining("must contain at most 32");
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
        assertRejected(PostClient.class, config, "method-specific semantic-read acknowledgement");
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
    void semanticReadDefaultsFalseAndIsCapturedInPublicMetadata() throws Exception {
        MethodMetadata defaultMetadata = metadataCache.get(
                MethodSelectedPostClient.class.getMethod("query"));
        MethodMetadata acknowledgedMetadata = metadataCache.get(
                AcknowledgedPostClient.class.getMethod("query"));

        assertThat(CacheResponse.class.getMethod("semanticRead").getDefaultValue()).isEqualTo(false);
        assertThat(defaultMetadata.isCacheSemanticRead()).isFalse();
        assertThat(defaultMetadata.getRequestPlan().cacheSemanticRead()).isFalse();
        assertThat(acknowledgedMetadata.isCacheSemanticRead()).isTrue();
        assertThat(acknowledgedMetadata.getRequestPlan().cacheSemanticRead()).isTrue();
    }

    @Test
    void oneEffectiveDecisionClassifiesEveryVerbEligibilityState() throws Exception {
        ReactiveHttpClientProperties.ClientConfig unselected = configWithPolicy("selected", 1_000L, 100L);
        ReactiveHttpClientProperties.ClientConfig selected = selectedPolicy(1_000L, 100L);

        assertThat(decision(UnselectedClient.class, "get", unselected).eligibility())
                .isEqualTo(EffectiveCachePolicy.Eligibility.DISABLED);
        assertThat(decision(EligibleClient.class, "get", selected).eligibility())
                .isEqualTo(EffectiveCachePolicy.Eligibility.GET_FRIENDLY_SELECTED);
        assertThat(decision(AcknowledgedPostClient.class, "query", unselected).eligibility())
                .isEqualTo(EffectiveCachePolicy.Eligibility.SEMANTIC_READ_SELECTED);

        EffectiveCachePolicy.Decision invalid = decision(
                MethodSelectedPostClient.class, "query", unselected);
        assertThat(invalid.eligibility()).isEqualTo(EffectiveCachePolicy.Eligibility.INVALID);
        assertThat(invalid.invalidReason())
                .contains("method-specific semantic-read acknowledgement")
                .contains("semanticRead = true");
    }

    @Test
    void everyUnacknowledgedNonGetVerbRetainsThePublishedGetOnlyDefault() {
        ReactiveHttpClientProperties.ClientConfig config = selectedPolicy(1_000L, 100L);
        Map<String, String> verbs = Map.of(
                "post", "POST",
                "put", "PUT",
                "patch", "PATCH",
                "delete", "DELETE",
                "head", "HEAD",
                "options", "OPTIONS");

        verbs.forEach((methodName, verb) -> {
            try {
                var method = UnacknowledgedVerbClient.class.getMethod(methodName);
                RequestPlan plan = RequestPlan.from(metadataCache.get(method), UnacknowledgedVerbClient.class);
                assertThatThrownBy(() -> EffectiveCachePolicy.validate(
                        UnacknowledgedVerbClient.class, "verb-cache", plan, config, verb))
                        .hasMessageContaining("Reactive HTTP client 'verb-cache'")
                        .hasMessageContaining("concreteClient=" + UnacknowledgedVerbClient.class.getName())
                        .hasMessageContaining("method=" + method.toGenericString())
                        .hasMessageContaining("resolvedHttpMethod=" + verb)
                        .hasMessageContaining("cachePolicy='selected'")
                        .hasMessageContaining("source=client")
                        .hasMessageContaining("@CacheResponse(value = \"selected\", semanticRead = true)")
                        .hasMessageContaining("omitting downstream dispatch cannot omit a required side effect");
            } catch (NoSuchMethodException ex) {
                throw new AssertionError(ex);
            }
        });

        assertThatThrownBy(() -> validate(MixedSelectionClient.class, "mixed-cache", config))
                .hasMessageContaining("MixedSelectionClient")
                .hasMessageContaining("resolvedHttpMethod=POST")
                .hasMessageContaining("source=client");
    }

    @Test
    void methodSelectionAlsoRequiresAnExplicitSemanticReadAcknowledgement() {
        ReactiveHttpClientProperties.ClientConfig config = configWithPolicy("selected", 1_000L, 100L);

        assertThatThrownBy(() -> validate(MethodSelectedPostClient.class, "method-cache", config))
                .hasMessageContaining("resolvedHttpMethod=POST")
                .hasMessageContaining("cachePolicy='selected'")
                .hasMessageContaining("source=method")
                .hasMessageContaining("semanticRead = true");
    }

    @Test
    void methodSpecificAcknowledgementSupportsEveryResolvedVerbAndApiRef() {
        ReactiveHttpClientProperties.ClientConfig config = configWithPolicy("selected", 1_000L, 100L);
        ReactiveHttpClientProperties.ApiConfig api = new ReactiveHttpClientProperties.ApiConfig();
        api.setMethod("POST");
        api.setPath("/configured");
        config.getApis().put("configured", api);

        assertThatCode(() -> validate(AcknowledgedVerbClient.class, "semantic-verbs", config))
                .doesNotThrowAnyException();
        assertThatCode(() -> validate(AcknowledgedApiRefClient.class, "semantic-api-ref", config))
                .doesNotThrowAnyException();
        assertThatCode(() -> validate(ConcreteSemanticReadClient.class, "semantic-inherited", config))
                .doesNotThrowAnyException();
        assertThatCode(() -> validate(MultiLevelSemanticReadClient.class, "semantic-multi-level", config))
                .doesNotThrowAnyException();
        assertThatCode(() -> validate(AcknowledgedOverloadedClient.class, "semantic-overloaded", config))
                .doesNotThrowAnyException();
        assertThatCode(() -> validate(BridgeSemanticReadClient.class, "semantic-bridge", config))
                .doesNotThrowAnyException();
        assertThat(java.util.Arrays.stream(BridgeSemanticReadClient.class.getDeclaredMethods())
                .anyMatch(java.lang.reflect.Method::isBridge)).isTrue();

        List<EffectiveHttpClientContract> contracts = EffectiveHttpClientContractExporter.export(
                AcknowledgedVerbClient.class, "semantic-verbs", config, metadataCache);
        assertThat(contracts)
                .extracting(EffectiveHttpClientContract::httpMethod)
                .containsExactlyInAnyOrder("POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS");
        assertThat(contracts).allSatisfy(contract -> {
            assertThat(contract.cache().enabled()).isTrue();
            assertThat(contract.cache().source()).isEqualTo("method");
            assertThat(contract.cache().semanticRead()).isTrue();
        });
    }

    @Test
    void semanticNonGetBodiesRequireWireBytePartitionEvenWhenResponsesAreShared() {
        ReactiveHttpClientProperties.ClientConfig config = configWithPolicy("selected", 1_000L, 100L);
        ReactiveHttpClientProperties.CachePolicyConfig policy = config.getCache().getPolicies().get("selected");
        policy.setSharedResponse(true);

        assertThatThrownBy(() -> validate(AcknowledgedBodyPostClient.class, "semantic-body", config))
                .hasMessageContaining("resolvedHttpMethod=POST")
                .hasMessageContaining("body-bearing semantic non-GET methods")
                .hasMessageContaining("vary-by-parameters")
                .hasMessageContaining("shared-response cannot waive wire-byte body identity");

        policy.setVaryByParameters(List.of("criteria"));
        assertThatCode(() -> validate(AcknowledgedBodyPostClient.class, "semantic-body", config))
                .doesNotThrowAnyException();
    }

    @Test
    void semanticReadAuthRequiresARealPartitionInsteadOfAnAbsentIdempotencyHeader() {
        ReactiveHttpClientProperties.ClientConfig config = configWithPolicy("selected", 1_000L, 100L);
        config.setAuthProvider("principal-auth");
        ReactiveHttpClientProperties.CachePolicyConfig policy =
                config.getCache().getPolicies().get("selected");

        assertThatThrownBy(() -> validate(AcknowledgedPostClient.class, "semantic-auth", config))
                .hasMessageContaining("authenticated responses require an explicit")
                .hasMessageContaining("partition or shared-response acknowledgement");

        policy.setVaryByContext(List.of("tenant"));
        assertThatCode(() -> validate(AcknowledgedPostClient.class, "semantic-auth", config))
                .doesNotThrowAnyException();

        policy.setVaryByContext(List.of());
        policy.setSharedResponse(true);
        assertThatCode(() -> validate(AcknowledgedPostClient.class, "semantic-auth", config))
                .doesNotThrowAnyException();
    }

    @Test
    void semanticReadCannotAcknowledgeAnUnresolvedApiRefVerb() {
        ReactiveHttpClientProperties.ClientConfig config = configWithPolicy("selected", 1_000L, 100L);

        assertThatThrownBy(() -> validate(AcknowledgedApiRefClient.class, "semantic-api-ref", config))
                .hasMessageContaining("resolvedHttpMethod=null")
                .hasMessageContaining("apiRef='configured'")
                .hasMessageContaining("require a resolved HTTP method")
                .hasMessageContaining("configure the referenced API method");

        ReactiveHttpClientProperties.ApiConfig unsupported = new ReactiveHttpClientProperties.ApiConfig();
        unsupported.setMethod("BREW");
        unsupported.setPath("/configured");
        config.getApis().put("configured", unsupported);
        assertThatThrownBy(() -> validate(AcknowledgedApiRefClient.class, "semantic-api-ref", config))
                .hasMessageContaining("resolvedHttpMethod=BREW")
                .hasMessageContaining("resolved HTTP method 'BREW' is unsupported")
                .hasMessageContaining("cannot be acknowledged as a semantic read");
    }

    @Test
    void disabledAndUnselectedNonGetMethodsStayOnTheOrdinaryPath() {
        ReactiveHttpClientProperties.ClientConfig selected = selectedPolicy(1_000L, 100L);
        ReactiveHttpClientProperties.ClientConfig unselected = configWithPolicy("selected", 1_000L, 100L);

        assertThatCode(() -> validate(DisabledPostClient.class, "disabled-post", selected))
                .doesNotThrowAnyException();
        assertThatCode(() -> validate(UnselectedPostClient.class, "unselected-post", unselected))
                .doesNotThrowAnyException();
        assertThatCode(() -> validate(MixedDisabledClient.class, "mixed-disabled", selected))
                .doesNotThrowAnyException();

        EffectiveHttpClientContract.CachePolicy disabled = EffectiveHttpClientContractExporter.export(
                DisabledPostClient.class, "disabled-post", selected, metadataCache).get(0).cache();
        EffectiveHttpClientContract.CachePolicy absent = EffectiveHttpClientContractExporter.export(
                UnselectedPostClient.class, "unselected-post", unselected, metadataCache).get(0).cache();
        assertThat(disabled.enabled()).isFalse();
        assertThat(disabled.source()).isEqualTo("method-disabled");
        assertThat(absent.enabled()).isFalse();
        assertThat(absent.source()).isEqualTo("disabled");

        Map<String, EffectiveHttpClientContract.CachePolicy> mixed = EffectiveHttpClientContractExporter.export(
                        MixedDisabledClient.class, "mixed-disabled", selected, metadataCache).stream()
                .collect(java.util.stream.Collectors.toMap(
                        EffectiveHttpClientContract::javaMethodSignature,
                        EffectiveHttpClientContract::cache));
        assertThat(mixed.values()).extracting(EffectiveHttpClientContract.CachePolicy::source)
                .containsExactlyInAnyOrder("client", "method-disabled");
    }

    @Test
    void acknowledgementDoesNotBroadenFiniteResponseOrOwnedBodyRules() {
        ReactiveHttpClientProperties.ClientConfig config = configWithPolicy("selected", 1_000L, 100L);

        assertRejected(AcknowledgedFluxClient.class, config, "Flux responses are streaming");
        assertRejected(AcknowledgedVoidClient.class, config, "Mono<Void>");
        assertRejected(AcknowledgedBodilessEnvelopeClient.class, config, "bodiless ResponseEntity<Void>");
        assertRejected(AcknowledgedRawMonoClient.class, config,
                "raw Mono responses have no finite materialized response type");
        assertRejected(AcknowledgedStreamingEnvelopeClient.class, config,
                "Publisher and streaming response values");
        assertRejected(AcknowledgedDataBufferResponseClient.class, config, "DataBuffer response values");
        assertRejected(AcknowledgedResourceResponseClient.class, config, "Resource response values");
        assertRejected(AcknowledgedStreamBodyClient.class, config,
                "streaming or application-owned request bodies");
        assertRejected(AcknowledgedReaderBodyClient.class, config,
                "streaming or application-owned request bodies");
        assertRejected(AcknowledgedChannelBodyClient.class, config,
                "streaming or application-owned request bodies");
        assertRejected(AcknowledgedPublisherBodyClient.class, config,
                "streaming or application-owned request bodies");
        assertRejected(AcknowledgedDataBufferBodyClient.class, config,
                "streaming or application-owned request bodies");
        assertRejected(AcknowledgedResourceBodyClient.class, config,
                "streaming or application-owned request bodies");
        assertRejected(AcknowledgedMultipartClient.class, config,
                "multipart requests are not cache-eligible");
    }

    @Test
    void finiteMaterializedBodiesAreVerbIndependentAfterAcknowledgement() {
        ReactiveHttpClientProperties.ClientConfig config = configWithPolicy("selected", 1_000L, 100L);
        config.getCache().getPolicies().get("selected").setVaryByParameters(List.of("payload"));

        assertThatCode(() -> validate(AcknowledgedMaterializedBodyClient.class, "materialized-bodies", config))
                .doesNotThrowAnyException();
    }

    @Test
    void namesIdempotencyAndRetryMetadataDoNotImplySemanticReadIntent() {
        ReactiveHttpClientProperties.ClientConfig config = selectedPolicy(1_000L, 100L);

        assertThatThrownBy(() -> validate(ImplicitSafetyClient.class, "implicit-cache", config))
                .hasMessageContaining("method-specific semantic-read acknowledgement")
                .hasMessageContaining("resolvedHttpMethod=POST")
                .hasMessageContaining("source=client");
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
                .hasMessageContaining("resolvedHttpMethod=POST")
                .hasMessageContaining("semanticRead = true");
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
    void semanticReadCustomizationInventoryIncludesAncestorsAndLazyBeansWithoutCreatingThem() {
        ReactiveHttpClientProperties.ClientConfig config = configWithPolicy("selected", 1_000L, 100L);
        AtomicInteger lazyCreations = new AtomicInteger();
        try (GenericApplicationContext parent = new GenericApplicationContext()) {
            parent.registerBean("parentBootMutation", WebClientCustomizer.class, () -> {
                lazyCreations.incrementAndGet();
                return builder -> builder.defaultHeader("X-Parent", "dynamic");
            }, definition -> definition.setLazyInit(true));
            parent.refresh();

            try (GenericApplicationContext child = new GenericApplicationContext()) {
                child.setParent(parent);
                child.registerBean("orderedBootMutation", WebClientCustomizer.class,
                        () -> builder -> builder.defaultHeader("X-Ordered", "dynamic"),
                        definition -> definition.setLazyInit(true));
                child.registerBean("lazyClientMutation", ReactiveHttpClientCustomizer.class, () -> {
                    lazyCreations.incrementAndGet();
                    return builder -> builder.defaultRequest(request -> request.header("X-Tenant", "dynamic"));
                }, definition -> definition.setLazyInit(true));
                child.registerBean("replacementBuilder", WebClient.Builder.class, () -> {
                    lazyCreations.incrementAndGet();
                    return WebClient.builder();
                }, definition -> definition.setLazyInit(true));
                child.getBeanFactory().registerSingleton("otherClientMutation", new ReactiveHttpClientCustomizer() {
                    @Override
                    public boolean supports(String clientName) {
                        return false;
                    }

                    @Override
                    public void customize(WebClient.Builder builder) {
                        throw new AssertionError("Non-matching customizer must not run");
                    }
                });
                child.refresh();

                assertThatThrownBy(() -> metadataCache.validateDeclarativeCacheCustomizations(
                        child, AcknowledgedPostClient.class, "semantic-customization", config))
                        .hasMessageContaining("semantic-customization")
                        .hasMessageContaining("has no cache-safety classification");
                assertThat(lazyCreations).hasValue(0);

                config.getCache().getCustomizations().put(
                        "parentBootMutation", ReactiveHttpClientProperties.CacheCustomizationSafety.SAFE);
                config.getCache().getCustomizations().put(
                        "orderedBootMutation", ReactiveHttpClientProperties.CacheCustomizationSafety.SAFE);
                config.getCache().getCustomizations().put(
                        "lazyClientMutation", ReactiveHttpClientProperties.CacheCustomizationSafety.SAFE);
                config.getCache().getCustomizations().put(
                        "replacementBuilder", ReactiveHttpClientProperties.CacheCustomizationSafety.SAFE);

                assertThatCode(() -> metadataCache.validateDeclarativeCacheCustomizations(
                        child, AcknowledgedPostClient.class, "semantic-customization", config))
                        .doesNotThrowAnyException();
                assertThat(lazyCreations).hasValue(0);
            }
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

    private void assertInvalidRefresh(Long refreshAfterMs, Long refreshTimeoutMs, String reason) {
        ReactiveHttpClientProperties.ClientConfig config = selectedPolicy(1_000L, 100L);
        ReactiveHttpClientProperties.CachePolicyConfig policy = config.getCache().getPolicies().get("selected");
        policy.setRefreshAfterMs(refreshAfterMs);
        policy.setRefreshTimeoutMs(refreshTimeoutMs);
        assertInvalidBounds(config, reason);
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

    private EffectiveCachePolicy.Decision decision(
            Class<?> client,
            String methodName,
            ReactiveHttpClientProperties.ClientConfig config) throws NoSuchMethodException {
        var method = client.getMethod(methodName);
        RequestPlan plan = RequestPlan.from(metadataCache.get(method), client);
        return EffectiveCachePolicy.decide(
                plan, config, EffectiveCachePolicy.effectiveHttpMethod(plan, config));
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

    interface VariantContractClient {
        @GET("/value")
        Mono<String> get(@CacheKey("tenant") String tenant);
    }

    interface PostClient {
        @POST("/value")
        Mono<String> get();
    }

    interface MethodSelectedPostClient {
        @POST("/query")
        @CacheResponse("selected")
        Mono<String> query();
    }

    interface AcknowledgedPostClient {
        @POST("/query")
        @CacheResponse(value = "selected", semanticRead = true)
        Mono<String> query();
    }

    interface AcknowledgedBodyPostClient {
        @POST("/query")
        @CacheResponse(value = "selected", semanticRead = true)
        Mono<String> query(@Body @CacheKey("criteria") String criteria);
    }

    interface MixedSelectionClient {
        @GET("/read") Mono<String> read();
        @POST("/query") Mono<String> query();
    }

    interface MixedDisabledClient {
        @GET("/read") Mono<String> read();
        @POST("/command") @CacheDisabled Mono<String> command();
    }

    interface UnacknowledgedVerbClient {
        @POST("/post") Mono<String> post();
        @PUT("/put") Mono<String> put();
        @PATCH("/patch") Mono<String> patch();
        @DELETE("/delete") Mono<String> delete();
        @HEAD("/head") Mono<String> head();
        @OPTIONS("/options") Mono<String> options();
    }

    interface AcknowledgedVerbClient {
        @POST("/post") @CacheResponse(value = "selected", semanticRead = true) Mono<String> post();
        @PUT("/put") @CacheResponse(value = "selected", semanticRead = true) Mono<ResponseEntity<String>> put();
        @PATCH("/patch") @CacheResponse(value = "selected", semanticRead = true) Mono<String> patch();
        @DELETE("/delete") @CacheResponse(value = "selected", semanticRead = true) Mono<String> delete();
        @HEAD("/head") @CacheResponse(value = "selected", semanticRead = true) Mono<String> head();
        @OPTIONS("/options") @CacheResponse(value = "selected", semanticRead = true) Mono<String> options();
    }

    interface AcknowledgedApiRefClient {
        @ApiRef("configured")
        @CacheResponse(value = "selected", semanticRead = true)
        Mono<String> query();
    }

    interface SemanticReadOperations<T> {
        @POST("/inherited")
        @CacheResponse(value = "selected", semanticRead = true)
        Mono<T> query();
    }

    interface ConcreteSemanticReadClient extends SemanticReadOperations<String> {
    }

    interface IntermediateSemanticReadOperations<T> extends SemanticReadOperations<List<T>> {
    }

    interface MultiLevelSemanticReadClient extends IntermediateSemanticReadOperations<String> {
    }

    interface BridgeOperations<T> {
        T query();
    }

    interface BridgeSemanticReadClient extends BridgeOperations<Mono<String>> {
        @Override
        @POST("/bridge")
        @CacheResponse(value = "selected", semanticRead = true)
        Mono<String> query();
    }

    interface AcknowledgedOverloadedClient {
        @POST("/query/{id}")
        @CacheResponse(value = "selected", semanticRead = true)
        Mono<String> query(@PathVar("id") String id);

        @POST("/query/{id}")
        @CacheResponse(value = "selected", semanticRead = true)
        Mono<String> query(@PathVar("id") long id);
    }

    interface DisabledPostClient {
        @POST("/command")
        @CacheDisabled
        Mono<String> command();
    }

    interface UnselectedPostClient {
        @POST("/command")
        Mono<String> command();
    }

    interface AcknowledgedFluxClient {
        @POST("/stream")
        @CacheResponse(value = "selected", semanticRead = true)
        Flux<String> stream();
    }

    interface AcknowledgedVoidClient {
        @POST("/empty")
        @CacheResponse(value = "selected", semanticRead = true)
        Mono<Void> empty();
    }

    interface AcknowledgedBodilessEnvelopeClient {
        @POST("/empty-entity")
        @CacheResponse(value = "selected", semanticRead = true)
        Mono<ResponseEntity<Void>> empty();
    }

    interface AcknowledgedRawMonoClient {
        @POST("/raw")
        @CacheResponse(value = "selected", semanticRead = true)
        @SuppressWarnings("rawtypes")
        Mono raw();
    }

    interface AcknowledgedStreamingEnvelopeClient {
        @POST("/stream-entity")
        @CacheResponse(value = "selected", semanticRead = true)
        Mono<ResponseEntity<Flux<DataBuffer>>> stream();
    }

    interface AcknowledgedDataBufferResponseClient {
        @POST("/buffer-response")
        @CacheResponse(value = "selected", semanticRead = true)
        Mono<DataBuffer> buffer();
    }

    interface AcknowledgedResourceResponseClient {
        @POST("/resource-response")
        @CacheResponse(value = "selected", semanticRead = true)
        Mono<Resource> resource();
    }

    interface AcknowledgedStreamBodyClient {
        @POST("/stream-body")
        @CacheResponse(value = "selected", semanticRead = true)
        Mono<String> query(@Body InputStream input);
    }

    interface AcknowledgedReaderBodyClient {
        @POST("/reader-body")
        @CacheResponse(value = "selected", semanticRead = true)
        Mono<String> query(@Body Reader reader);
    }

    interface AcknowledgedChannelBodyClient {
        @POST("/channel-body")
        @CacheResponse(value = "selected", semanticRead = true)
        Mono<String> query(@Body ReadableByteChannel channel);
    }

    interface AcknowledgedPublisherBodyClient {
        @POST("/publisher-body")
        @CacheResponse(value = "selected", semanticRead = true)
        Mono<String> query(@Body Publisher<String> publisher);
    }

    interface AcknowledgedDataBufferBodyClient {
        @POST("/buffer-body")
        @CacheResponse(value = "selected", semanticRead = true)
        Mono<String> query(@Body DataBuffer buffer);
    }

    interface AcknowledgedResourceBodyClient {
        @POST("/resource-body")
        @CacheResponse(value = "selected", semanticRead = true)
        Mono<String> query(@Body Resource resource);
    }

    interface AcknowledgedMultipartClient {
        @POST("/multipart")
        @MultipartBody
        @CacheResponse(value = "selected", semanticRead = true)
        Mono<String> query(@FormFile("file") Resource resource);
    }

    interface AcknowledgedMaterializedBodyClient {
        @POST("/post")
        @CacheResponse(value = "selected", semanticRead = true)
        Mono<BodyValue> post(@Body @CacheKey("payload") BodyValue payload);

        @PUT("/put")
        @CacheResponse(value = "selected", semanticRead = true)
        Mono<ResponseEntity<String>> put(@Body @CacheKey("payload") String payload);

        @PATCH("/patch")
        @CacheResponse(value = "selected", semanticRead = true)
        Mono<byte[]> patch(@Body @CacheKey("payload") byte[] payload);

        @DELETE("/delete")
        @CacheResponse(value = "selected", semanticRead = true)
        Mono<BodyValue> delete(@Body @CacheKey("payload") BodyValue payload);

        @HEAD("/head")
        @CacheResponse(value = "selected", semanticRead = true)
        Mono<ResponseEntity<String>> head(@Body @CacheKey("payload") String payload);

        @OPTIONS("/options")
        @CacheResponse(value = "selected", semanticRead = true)
        Mono<byte[]> options(@Body @CacheKey("payload") byte[] payload);
    }

    record BodyValue(String query) {
    }

    interface ImplicitSafetyClient {
        @POST("/find")
        @IdempotencyKey
        @Retry("retry")
        Mono<String> find();
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
