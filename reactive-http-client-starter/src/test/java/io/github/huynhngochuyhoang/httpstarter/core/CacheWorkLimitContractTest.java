package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.annotation.*;
import io.github.huynhngochuyhoang.httpstarter.auth.AuthProviderFactory;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientBeanFactoryInitializationAotProcessor;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import io.github.huynhngochuyhoang.httpstarter.core.CacheWorkPolicy.Limits;
import io.github.huynhngochuyhoang.httpstarter.core.CacheWorkPolicy.Selection;
import io.github.huynhngochuyhoang.httpstarter.core.CacheWorkPolicy.Snapshot;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static io.github.huynhngochuyhoang.httpstarter.core.CacheWorkLimitContract.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Internal specification and compatibility checks; does not claim runtime work-limit enforcement. */
class CacheWorkLimitContractTest {
    private final MethodMetadataCache metadata = new MethodMetadataCache();

    @Test
    void absentOrEmptyWorkIsNotAnotherActivationSwitch() {
        assertThat(normalize(null, false)).isNull();
        assertThat(normalize(null, true)).isNull();
        assertThat(normalize(new Input(null, null, null), true)).isNull();
        assertThat(bind(Map.of())).isNull();
        var snapshot = freeze(Client.class, "work-contract", metadata, config(), Map.of());
        assertThat(snapshot.policies()).isEmpty();
        assertThat(snapshot.methods().values()).allSatisfy(selection -> assertThat(selection.work()).isNull());
    }

    @ParameterizedTest
    @CsvSource({"1,1", "1000000,1000000", "1,1000000", "1000000,1"})
    void callerAndLoadBoundsAreIndependentPositiveCounts(long callers, long loads) {
        Input input = bind(Map.of("work.maximum-concurrent-callers", callers,
                "work.maximum-concurrent-loads", loads));
        assertThat(normalize(input, false)).isEqualTo(new Limits((int) callers, (int) loads, null));
        assertThat(normalize(new Input(callers, loads, 1L), true))
                .isEqualTo(new Limits((int) callers, (int) loads, 1));
        assertThat(normalize(new Input(callers, loads, MAXIMUM), true).maximumConcurrentRefreshes())
                .isEqualTo((int) MAXIMUM);
    }

    static Stream<Arguments> invalidInputs() {
        Stream.Builder<Arguments> cases = Stream.builder();
        cases.add(Arguments.of(new Input(1L, null, null), false, "maximum-concurrent-loads"));
        cases.add(Arguments.of(new Input(null, 1L, null), false, "maximum-concurrent-callers"));
        cases.add(Arguments.of(new Input(1L, 1L, null), true, "maximum-concurrent-refreshes"));
        cases.add(Arguments.of(new Input(1L, 1L, 1L), false, "requires selected refresh"));
        cases.add(Arguments.of(new Input(null, null, 1L), true, "maximum-concurrent-callers"));
        for (long value : new long[]{0, -1, MAXIMUM + 1, Integer.MAX_VALUE, Long.MAX_VALUE, Long.MIN_VALUE}) {
            cases.add(Arguments.of(new Input(value, 1L, null), false, "maximum-concurrent-callers"));
            cases.add(Arguments.of(new Input(1L, value, null), false, "maximum-concurrent-loads"));
            cases.add(Arguments.of(new Input(1L, 1L, value), true, "maximum-concurrent-refreshes"));
        }
        return cases.build();
    }

    @ParameterizedTest
    @MethodSource("invalidInputs")
    void rejectsPartialNonPositiveAndOverflowingSelections(Input input, boolean refresh, String message) {
        assertThatThrownBy(() -> normalize(input, refresh)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(message);
    }

    @ParameterizedTest
    @ValueSource(strings = {"9223372036854775808", "-9223372036854775809", "1.5", "unlimited"})
    void bindingCannotTruncateOverflowOrFractionalValues(String value) {
        assertThatThrownBy(() -> bind(Map.of("work.maximum-concurrent-callers", value,
                "work.maximum-concurrent-loads", "1"))).isInstanceOf(RuntimeException.class);
    }

    @Test
    void selectionUsesConcreteMetadataAndExistingPolicyPrecedence() throws Exception {
        var config = config();
        Map<String, Input> inputs = Map.of("client", new Input(4L, 2L, null),
                "method", new Input(8L, 3L, null), "inert", new Input(0L, -1L, null));
        var snapshot = freeze(Client.class, "work-contract", metadata, config, inputs);
        assertThat(snapshot.policies()).containsOnlyKeys("client", "method");
        assertThat(snapshot.methods()).hasSize(7);
        assertThat(selection(snapshot, "inherited").work()).isSameAs(snapshot.policies().get("client"));
        assertThat(selection(snapshot, "first").work()).isSameAs(selection(snapshot, "samePolicy").work());
        assertThat(selection(snapshot, "first").source()).isEqualTo(EffectiveCachePolicy.Source.CLIENT);
        assertThat(selection(snapshot, "samePolicy").source()).isEqualTo(EffectiveCachePolicy.Source.METHOD);
        assertThat(selection(snapshot, "method").work()).isSameAs(selection(snapshot, "search").work());
        assertThat(selection(snapshot, "method").work()).isNotSameAs(selection(snapshot, "first").work());
        assertThat(selection(snapshot, "search").eligibility())
                .isEqualTo(EffectiveCachePolicy.Eligibility.SEMANTIC_READ_SELECTED);
        assertThat(selection(snapshot, "disabled").source()).isEqualTo(EffectiveCachePolicy.Source.METHOD_DISABLED);
        assertThat(selection(snapshot, "disabled").work()).isNull();
        assertThat(selection(snapshot, "legacy").work()).isNull();
        assertThat(RequestPlan.from(metadata.get(Client.class.getMethod("inherited")), Client.class).responseType())
                .isEqualTo(String.class);

        var anotherFactory = freeze(Client.class, "work-contract", metadata, config, inputs);
        assertThat(anotherFactory).isEqualTo(snapshot);
        assertThat(anotherFactory.policies().get("client")).isNotSameAs(snapshot.policies().get("client"));
    }

    @Test
    void invalidUnusedWorkDoesNotEnableCacheOrRefresh() {
        var config = config();
        config.getCache().setPolicy(null);
        var snapshot = freeze(Unselected.class, "unselected", metadata, config,
                Map.of("client", new Input(0L, -1L, 5L)));
        assertThat(snapshot.policies()).isEmpty();
        assertThat(snapshot.methods().values()).allSatisfy(selection -> {
            assertThat(selection.eligibility()).isEqualTo(EffectiveCachePolicy.Eligibility.DISABLED);
            assertThat(selection.work()).isNull();
        });
        assertThat(config.getCache().getPolicies().get("client").isRefreshEnabled()).isFalse();
    }

    @Test
    void workCannotMakeAnInvalidCacheOrUnresolvedApiEligible() {
        var config = config();
        config.getApis().clear();
        assertThatThrownBy(() -> freeze(Client.class, "work-contract", metadata, config,
                Map.of("method", new Input(1L, 1L, null)))).hasMessageContaining("resolved HTTP method");
        config.getCache().setPolicy("missing");
        assertThatThrownBy(() -> freeze(Unselected.class, "unselected", metadata, config, Map.of()))
                .hasMessageContaining("not declared");
    }

    @Test
    void startupSnapshotRejectsMutationInsteadOfCreatingNewCapacity() {
        var config = config();
        Map<String, Input> inputs = new HashMap<>(Map.of("client", new Input(4L, 2L, null)));
        var startup = freeze(Client.class, "work-contract", metadata, config, inputs);
        startup.requireUnchanged(freeze(Client.class, "work-contract", metadata, config, inputs));
        inputs.put("client", new Input(5L, 2L, null));
        assertThat(startup.policies().get("client").maximumConcurrentCallers()).isEqualTo(4);
        assertThatThrownBy(() -> startup.requireUnchanged(
                freeze(Client.class, "work-contract", metadata, config, inputs)))
                .hasMessageContaining("changed after startup");
        inputs.clear();
        assertThatThrownBy(() -> startup.requireUnchanged(
                freeze(Client.class, "work-contract", metadata, config, inputs)))
                .hasMessageContaining("changed after startup");
        assertThatThrownBy(() -> startup.policies().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> startup.methods().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void startupSnapshotIncludesRefreshEligibilityAndPolicyMappings() {
        var config = config();
        var startup = freeze(Client.class, "work-contract", metadata, config, Map.of());
        var policy = config.getCache().getPolicies().get("method");
        policy.setRefreshAfterMs(100L);
        policy.setRefreshTimeoutMs(500L);
        assertThatThrownBy(() -> startup.requireUnchanged(
                freeze(Client.class, "work-contract", metadata, config, Map.of())))
                .hasMessageContaining("changed after startup");
        policy.setRefreshAfterMs(null);
        policy.setRefreshTimeoutMs(null);
        config.getCache().setPolicy("legacy");
        assertThatThrownBy(() -> startup.requireUnchanged(
                freeze(Client.class, "work-contract", metadata, config, Map.of())))
                .hasMessageContaining("changed after startup");
    }

    @Test
    void refreshRequiresItsOwnBoundOnlyWhenTheWorkContractIsSelected() {
        var config = config();
        var policy = config.getCache().getPolicies().get("method");
        policy.setRefreshAfterMs(100L);
        policy.setRefreshTimeoutMs(500L);
        assertThat(freeze(Client.class, "work-contract", metadata, config, Map.of()).policies()).isEmpty();
        assertThatThrownBy(() -> freeze(Client.class, "work-contract", metadata, config,
                Map.of("method", new Input(4L, 2L, null)))).hasMessageContaining("maximum-concurrent-refreshes");
        var snapshot = freeze(Client.class, "work-contract", metadata, config,
                Map.of("method", new Input(4L, 2L, 1L)));
        assertThat(selection(snapshot, "search").work().maximumConcurrentRefreshes()).isEqualTo(1);
        assertThat(selection(snapshot, "first").work()).isNull();
    }

    @Test
    void callerAdmissionPrecedesHitsAndLoadSaturationDoesNotBlockHitsOrJoins() {
        Limits limits = normalize(new Input(3L, 2L, null), false);
        assertThat(caller(limits, 2)).isEqualTo(Action.ADMIT_CALLER);
        assertThat(caller(limits, 3)).isEqualTo(Action.REJECT_CALLER);
        assertThat(caller(limits, 4)).isEqualTo(Action.REJECT_CALLER);
        assertThat(load(limits, Lookup.FRESH, false, 2)).isEqualTo(Action.HIT);
        assertThat(load(limits, Lookup.STALE, false, 2)).isEqualTo(Action.HIT);
        assertThat(load(limits, Lookup.MISS, true, 2)).isEqualTo(Action.JOIN);
        assertThat(load(limits, Lookup.MISS, false, 2)).isEqualTo(Action.REJECT_LOAD);
        assertThat(load(limits, Lookup.MISS, false, 1)).isEqualTo(Action.START_LOAD);
        assertThat(caller(null, Integer.MAX_VALUE)).isEqualTo(Action.ADMIT_CALLER);
        assertThat(load(null, Lookup.MISS, false, Integer.MAX_VALUE)).isEqualTo(Action.START_LOAD);
    }

    @Test
    void refreshSaturationSkipsOnlyNewEligibleWorkAndDoesNotUseForegroundSlots() {
        Limits limits = normalize(new Input(3L, 2L, 1L), true);
        assertThat(refresh(limits, false, false, 1)).isEqualTo(RefreshAction.NOT_ELIGIBLE);
        assertThat(refresh(limits, true, true, 1)).isEqualTo(RefreshAction.ALREADY_RUNNING);
        assertThat(refresh(limits, true, false, 1)).isEqualTo(RefreshAction.SKIP_CAPACITY);
        assertThat(refresh(limits, true, false, 0)).isEqualTo(RefreshAction.START);
        assertThat(load(limits, Lookup.MISS, false, 0)).isEqualTo(Action.START_LOAD);
        assertThat(refresh(null, true, false, Integer.MAX_VALUE)).isEqualTo(RefreshAction.START);
    }

    @ParameterizedTest
    @EnumSource(Rejection.class)
    void rejectionSpecificationIsStructuralAndNotATransportOrStorageOutcome(Rejection rejection) {
        var facts = rejection.terminalFacts();
        assertThat(facts).containsEntry("reason", rejection.name())
                .containsEntry("errorCategory", "CACHE_ADMISSION_ERROR")
                .containsEntry("attemptCount", 0).containsEntry("requestDispatched", false)
                .containsEntry("statusCode", null).containsEntry("failureStage", null);
        assertThat(facts.get("cacheOutcome")).isIn("CALLER_REJECTED", "LOAD_REJECTED");
        assertThat(rejection.message).hasSizeLessThan(80);
        assertThat(facts).doesNotContainKeys("policyName", "clientName", "key", "url", "headers", "body", "identity");
    }

    @Test
    void configuredMetadataRemainsAuthoritativeForValidationAotAndDiagnostics() throws Exception {
        var config = config();
        var work = new ReactiveHttpClientProperties.CacheWorkConfig();
        work.setMaximumConcurrentCallers(3L);
        work.setMaximumConcurrentLoads(2L);
        config.getCache().getPolicies().get("method").setWork(work);
        var properties = new ReactiveHttpClientProperties();
        properties.setClients(Map.of("replacement-work", config));
        AtomicInteger reads = new AtomicInteger();
        MethodMetadataCache replacement = new MethodMetadataCache() {
            @Override public MethodMetadata get(Method method) {
                reads.incrementAndGet();
                MethodMetadata result = new MethodMetadata();
                result.setMethod(method);
                result.setApiName(method.getName());
                result.setHttpMethod("GET");
                result.setPathTemplate("/replacement");
                result.setStaticEffectiveApi(new EffectiveApi("GET", "/replacement", MethodMetadata.TIMEOUT_NOT_SET));
                result.setReturnsMono(true);
                result.setResponseType(String.class);
                result.setCachePolicyName("method");
                return result;
            }
        };
        DefaultListableBeanFactory beans = new DefaultListableBeanFactory();
        RootBeanDefinition client = new RootBeanDefinition(ReactiveHttpClientFactoryBean.class);
        client.setAttribute(FactoryBean.OBJECT_TYPE_ATTRIBUTE, Replacement.class);
        client.getPropertyValues().add("type", Replacement.class);
        beans.registerBeanDefinition("replacementClient", client);
        beans.registerSingleton("properties", properties);
        beans.registerSingleton("metadata", replacement);
        AtomicInteger optionalCreations = new AtomicInteger();
        for (Class<?> type : List.of(RetryRegistry.class, AuthProviderFactory.class)) {
            RootBeanDefinition definition = new RootBeanDefinition(type);
            definition.setLazyInit(true);
            definition.setInstanceSupplier(() -> {
                optionalCreations.incrementAndGet();
                throw new AssertionError("Optional component must stay lazy");
            });
            beans.registerBeanDefinition(type.getSimpleName(), definition);
        }
        try {
            var snapshot = CacheWorkPolicy.freeze(Replacement.class, "replacement-work", replacement, config);
            assertThat(snapshot.methods().get(Replacement.class.getMethod("load")).source())
                    .isEqualTo(EffectiveCachePolicy.Source.METHOD);
            var exported = EffectiveHttpClientContractExporter.export(Replacement.class, "replacement-work", config,
                    replacement).getFirst();
            assertThat(exported.cache().source()).isEqualTo("method");
            assertThat(exported.cache().maximumSize()).isEqualTo(10);
            assertThat(exported.cache().work()).isEqualTo(new Limits(3, 2, null));

            int beforeAot = reads.get();
            assertThat(new ReactiveHttpClientBeanFactoryInitializationAotProcessor().processAheadOfTime(beans))
                    .isNotNull();
            assertThat(reads.get()).isGreaterThan(beforeAot);
            var entry = new ReactiveHttpClientDiagnosticsProvider(beans, properties, replacement)
                    .clientSnapshotEntries().getFirst();
            assertThat(entry.cache().policyCount()).isEqualTo(1);
            assertThat(entry.cache().policySources()).containsExactly("method");
            assertThat(entry.cache().httpMethods()).containsExactly("GET");
            assertThat(entry.cache().work().maximumCallers()).isEqualTo(3);
            assertThat(entry.cache().work().maximumLoads()).isEqualTo(2);
            assertThat(entry.cache().work().activeCallers()).isNull();
            assertThat(entry.cache().work().state()).isEqualTo("uninitialized");
            assertThat(optionalCreations).hasValue(0);
            assertThat(beans.containsSingleton("replacementClient")).isFalse();
            work.setMaximumConcurrentLoads(0L);
            assertThatThrownBy(() -> new ReactiveHttpClientBeanFactoryInitializationAotProcessor()
                    .processAheadOfTime(beans)).hasMessageContaining("maximum-concurrent-loads");
            assertThatThrownBy(() -> EffectiveHttpClientContractExporter.export(
                    Replacement.class, "replacement-work", config, replacement))
                    .hasMessageContaining("maximum-concurrent-loads");
        }
        finally {
            beans.destroySingletons();
        }
    }

    @Test
    void existingRuntimeAndExporterAgreeWhenWorkIsOmitted() {
        var config = config();
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            context.refresh();
            var observation = new ReactiveHttpClientProperties.ObservabilityConfig();
            AtomicInteger dispatches = new AtomicInteger();
            WebClient web = WebClient.builder().baseUrl("http://work.example.invalid")
                    .filter(ReactiveClientInvocationHandler.finalRequestObservationFilter())
                    .exchangeFunction(request -> {
                        dispatches.incrementAndGet();
                        return Mono.just(ClientResponse.create(HttpStatus.OK).body("response").build());
                    }).build();
            LocalResponseCacheManager manager = LocalResponseCacheManager.createForClient(Client.class, "work-contract",
                    metadata, config, getClass().getClassLoader(), observation, null);
            try {
                var handler = new ReactiveClientInvocationHandler(web, metadata, new RequestArgumentResolver(),
                        new DefaultErrorDecoder(), config, "work-contract", Client.class, context,
                        new NoopResilienceOperatorApplier(), TestJsonCodecs.jsonCodec(), observation, manager,
                        null, "http://work.example.invalid");
                Client client = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(),
                        new Class<?>[]{Client.class}, handler);
                for (int repeat = 0; repeat < 2; repeat++) {
                    assertThat(client.first().block(Duration.ofSeconds(2))).isEqualTo("response");
                    assertThat(client.disabled().block(Duration.ofSeconds(2))).isEqualTo("response");
                }
                assertThat(dispatches).hasValue(3);
                assertThat(manager.workloadSnapshotForTesting().inFlightLoads()).isZero();
                assertThat(manager.workloadSnapshotForTesting().inFlightRefreshes()).isZero();
                assertThat(manager.snapshot().currentSize()).isEqualTo(1);
                var snapshot = freeze(Client.class, "work-contract", metadata, config, Map.of());
                EffectiveHttpClientContractExporter.export(Client.class, "work-contract", config, metadata)
                        .forEach(contract -> {
                            Selection selection = selection(snapshot, contract.apiName());
                            assertThat(selection.source().value()).isEqualTo(contract.cache().source());
                            assertThat(selection.work()).isNull();
                        });
            }
            finally {
                manager.close();
            }
        }
    }

    @Test
    void inertDefinitionsAllocateNoCacheManagerAndDoNotNeedCaffeine() {
        var config = config();
        config.getCache().setPolicy(null);
        ClassLoader unavailableCaffeine = new ClassLoader(getClass().getClassLoader()) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.startsWith("com.github.benmanes.caffeine")) {
                    throw new AssertionError("Inert policy must not inspect Caffeine");
                }
                return super.loadClass(name, resolve);
            }
        };
        assertThat(LocalResponseCacheManager.createForClient(Unselected.class, "unselected", metadata, config,
                unavailableCaffeine, new ReactiveHttpClientProperties.ObservabilityConfig(), null)).isNull();
    }

    private static Input bind(Map<String, ?> properties) {
        var bound = new Binder(new MapConfigurationPropertySource(properties))
                .bind("work", Bindable.of(ReactiveHttpClientProperties.CacheWorkConfig.class)).orElse(null);
        return bound == null ? null : new Input(bound.getMaximumConcurrentCallers(),
                bound.getMaximumConcurrentLoads(), bound.getMaximumConcurrentRefreshes());
    }

    private static Selection selection(Snapshot snapshot, String method) {
        return snapshot.methods().entrySet().stream().filter(entry -> entry.getKey().getName().equals(method))
                .map(Map.Entry::getValue).findFirst().orElseThrow();
    }

    private static ReactiveHttpClientProperties.ClientConfig config() {
        var config = new ReactiveHttpClientProperties.ClientConfig();
        config.getCache().setPolicy("client");
        for (String name : List.of("client", "method", "legacy", "inert")) {
            var policy = new ReactiveHttpClientProperties.CachePolicyConfig();
            policy.setTtlMs(1_000L);
            policy.setMaximumSize(10L);
            policy.setSharedResponse(true);
            config.getCache().getPolicies().put(name, policy);
        }
        var api = new ReactiveHttpClientProperties.ApiConfig();
        api.setMethod("POST");
        api.setPath("/search");
        config.getApis().put("search", api);
        return config;
    }

    interface Parent<T> {
        @GET("/inherited") Mono<T> inherited();
    }

    @ReactiveHttpClient(name = "work-contract")
    interface Client extends Parent<String> {
        @GET("/first") Mono<String> first();
        @GET("/same") @CacheResponse("client") Mono<String> samePolicy();
        @GET("/method") @CacheResponse("method") Mono<String> method();
        @GET("/legacy") @CacheResponse("legacy") Mono<String> legacy();
        @GET("/disabled") @CacheDisabled Mono<String> disabled();
        @ApiRef("search") @CacheResponse(value = "method", semanticRead = true) Mono<String> search();
        default Mono<String> local() { return Mono.just("local"); }
    }

    interface Unselected {
        @GET("/unselected") Mono<String> load();
    }

    @ReactiveHttpClient(name = "replacement-work")
    interface Replacement {
        Mono<String> load();
    }
}
