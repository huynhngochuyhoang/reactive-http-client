package io.github.huynhngochuyhoang.httpstarter.core;

import com.fasterxml.jackson.annotation.JsonValue;
import io.github.huynhngochuyhoang.httpstarter.annotation.*;
import io.github.huynhngochuyhoang.httpstarter.auth.*;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import io.github.huynhngochuyhoang.httpstarter.core.fixture.cache.NonPublicRecordFixture;
import io.github.huynhngochuyhoang.httpstarter.exception.LogicalCallTimeoutException;
import io.github.huynhngochuyhoang.httpstarter.exception.RequestSerializationException;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.reactive.MockClientHttpRequest;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;

class CacheKeyContractTest {

    private final MethodMetadataCache metadataCache = new MethodMetadataCache();
    private final RequestArgumentResolver argumentResolver = new RequestArgumentResolver();

    @Test
    void canonicalEncodingSeparatesAdversarialBoundariesAndNormalizesMapOrder() throws Exception {
        ReactiveHttpClientProperties.ClientConfig config = selectedPolicy();
        policy(config).setVaryByParameters(List.of("attributes"));
        Method method = KeyClient.class.getMethod(
                "get", String.class, String.class, String.class, Map.class);

        assertThat(key(KeyClient.class, "catalog-a", method,
                new Object[]{"p", null, "null", Map.of("a", List.of(1, 2), "b", List.of(3))}, config))
                .isNotEqualTo(key(KeyClient.class, "catalog-a", method,
                        new Object[]{"p", "null", null, Map.of("a", List.of(1, 2), "b", List.of(3))}, config));
        assertThat(key(KeyClient.class, "catalog-a", method,
                new Object[]{"p", "ab", "c", Map.of("a", List.of(1, 2))}, config))
                .isNotEqualTo(key(KeyClient.class, "catalog-a", method,
                        new Object[]{"p", "a", "bc", Map.of("a", List.of(1, 2))}, config));
        assertThat(key(KeyClient.class, "catalog-a", method,
                new Object[]{"p", "", "", Map.of()}, config))
                .isNotEqualTo(key(KeyClient.class, "catalog-a", method,
                        new Object[]{"p", null, null, Map.of()}, config));

        Map<String, List<Integer>> first = new LinkedHashMap<>();
        first.put("b", List.of(2, 3));
        first.put("a", List.of(1));
        Map<String, List<Integer>> second = new LinkedHashMap<>();
        second.put("a", List.of(1));
        second.put("b", List.of(2, 3));
        assertThat(key(KeyClient.class, "catalog-a", method,
                new Object[]{"x", "y", "z", first}, config))
                .isEqualTo(key(KeyClient.class, "catalog-a", method,
                        new Object[]{"x", "y", "z", second}, config));
    }

    @Test
    void requestBodyMapsPreserveWireOrderWhileCacheOnlyMapsRemainCanonical() throws Exception {
        ReactiveHttpClientProperties.ClientConfig config = selectedPolicy();
        policy(config).setVaryByParameters(List.of("body"));
        Method method = OrderedBodyMapClient.class.getMethod("get", Map.class);
        Map<String, String> first = new LinkedHashMap<>();
        first.put("a", "one");
        first.put("b", "two");
        Map<String, String> second = new LinkedHashMap<>();
        second.put("b", "two");
        second.put("a", "one");

        assertThat(key(OrderedBodyMapClient.class, "ordered-body", method,
                new Object[]{first}, config))
                .isNotEqualTo(key(OrderedBodyMapClient.class, "ordered-body", method,
                        new Object[]{second}, config));
    }

    @Test
    void uriVariantsPreserveTheirNonNormalizedText() throws Exception {
        ReactiveHttpClientProperties.ClientConfig config = selectedPolicy();
        policy(config).setVaryByParameters(List.of("uri"));
        Method method = UriVariantClient.class.getMethod("get", URI.class);

        assertThat(key(UriVariantClient.class, "uri-variant", method,
                new Object[]{URI.create("https://example.test/\u00e9")}, config))
                .isNotEqualTo(key(UriVariantClient.class, "uri-variant", method,
                        new Object[]{URI.create("https://example.test/%C3%A9")}, config));
    }

    @Test
    void keyIncludesConcreteClientResolvedSignatureAndConfiguredVariants() throws Exception {
        ReactiveHttpClientProperties.ClientConfig config = selectedPolicy();
        policy(config).setVaryByParameters(List.of("attributes"));
        policy(config).setVaryByHeaders(List.of("X-Tenant", "Idempotency-Key"));
        policy(config).setVaryByContext(List.of("locale"));
        Method method = VariantClient.class.getMethod(
                "get", String.class, String.class, Map.class);

        CacheKeyContract.OpaqueKey base = key(
                VariantClient.class, "catalog-a", method,
                new Object[]{"42", "tenant-a", Map.of("scope", List.of(1))},
                config, Context.of("locale", "en-US"));
        assertThat(base).isNotEqualTo(key(
                VariantClient.class, "catalog-b", method,
                new Object[]{"42", "tenant-a", Map.of("scope", List.of(1))},
                config, Context.of("locale", "en-US")));
        assertThat(base).isNotEqualTo(key(
                OtherVariantClient.class, "catalog-a",
                OtherVariantClient.class.getMethod("get", String.class, String.class, Map.class),
                new Object[]{"42", "tenant-a", Map.of("scope", List.of(1))},
                config, Context.of("locale", "en-US")));
        assertThat(base).isNotEqualTo(key(
                VariantClient.class, "catalog-a", method,
                new Object[]{"42", "tenant-b", Map.of("scope", List.of(1))},
                config, Context.of("locale", "en-US")));
        assertThat(base).isNotEqualTo(key(
                VariantClient.class, "catalog-a", method,
                new Object[]{"42", "tenant-a", Map.of("scope", List.of(1))},
                config, Context.of("locale", "vi-VN")));
        assertThat(base.toString()).isEqualTo("OpaqueCacheKey")
                .doesNotContain("tenant", "locale", "42");
    }

    @Test
    void inheritedGenericAndOverloadedMethodsHaveDistinctResolvedIdentities() throws Exception {
        ReactiveHttpClientProperties.ClientConfig config = selectedPolicy();

        Method inherited = Parent.class.getMethod("get", Object.class);
        Method stringOverload = OverloadedClient.class.getMethod("get", String.class);
        Method longOverload = OverloadedClient.class.getMethod("get", long.class);

        assertThat(key(StringChild.class, "child", inherited, new Object[]{"7"}, config))
                .isNotEqualTo(key(LongChild.class, "child", inherited, new Object[]{7L}, config));
        assertThat(key(OverloadedClient.class, "overloaded", stringOverload, new Object[]{"7"}, config))
                .isNotEqualTo(key(OverloadedClient.class, "overloaded", longOverload, new Object[]{7L}, config));
    }

    @Test
    void emptyAbsentScalarTypesAndSensitiveVariantsRemainIsolatedAndOpaque() throws Exception {
        ReactiveHttpClientProperties.ClientConfig contextConfig = selectedPolicy();
        policy(contextConfig).setVaryByContext(List.of("numericTenant"));
        Method noArguments = NoArgumentClient.class.getMethod("get");
        assertThat(key(NoArgumentClient.class, "context", noArguments, new Object[0],
                contextConfig, Context.of("numericTenant", 1)))
                .isNotEqualTo(key(NoArgumentClient.class, "context", noArguments, new Object[0],
                        contextConfig, Context.of("numericTenant", 1L)));

        ReactiveHttpClientProperties.ClientConfig listConfig = selectedPolicy();
        Method listMethod = ListQueryClient.class.getMethod("get", List.class);
        assertThat(key(ListQueryClient.class, "list", listMethod, new Object[]{null}, listConfig))
                .isNotEqualTo(key(ListQueryClient.class, "list", listMethod, new Object[]{List.of()}, listConfig));

        ReactiveHttpClientProperties.ClientConfig sensitiveConfig = selectedPolicy();
        policy(sensitiveConfig).setVaryByHeaders(List.of(
                "Authorization", "Cookie", "Idempotency-Key"));
        Method sensitiveMethod = SensitiveHeaderClient.class.getMethod("get", String.class, String.class);
        CacheKeyContract.OpaqueKey first = key(
                SensitiveHeaderClient.class, "sensitive", sensitiveMethod,
                new Object[]{"Bearer secret-token", "SESSION=secret-cookie"}, sensitiveConfig);
        CacheKeyContract.OpaqueKey second = key(
                SensitiveHeaderClient.class, "sensitive", sensitiveMethod,
                new Object[]{"Bearer another-token", "SESSION=secret-cookie"}, sensitiveConfig);

        assertThat(first).isNotEqualTo(second);
        assertThat(first.toString()).isEqualTo("OpaqueCacheKey")
                .doesNotContain("secret-token", "secret-cookie", "Bearer", "SESSION");
    }

    @Test
    void startupRejectsUnknownAmbiguousAndUnpartitionedVariants() {
        ReactiveHttpClientProperties.ClientConfig config = selectedPolicy();
        policy(config).setVaryByParameters(List.of("missing"));
        assertRejected(VariantClient.class, config, "unknown vary-by-parameters name 'missing'");

        config = selectedPolicy();
        policy(config).setVaryByHeaders(List.of("X-Unknown", "Idempotency-Key"));
        assertRejected(VariantClient.class, config, "unknown vary-by-headers name 'X-Unknown'");

        config = selectedPolicy();
        assertRejected(VariantClient.class, config, "dynamic request headers must be selected");

        config = selectedPolicy();
        policy(config).setVaryByHeaders(List.of(
                "X-Tenant", "x-tenant", "Idempotency-Key"));
        assertRejected(VariantClient.class, config, "vary-by-headers contains duplicate name");

        config = selectedPolicy();
        config.setAuthProvider("oauth");
        assertRejected(NoArgumentClient.class, config, "authenticated responses require");

        ReactiveHttpClientProperties.ClientConfig partitionedAuthConfig = selectedPolicy();
        partitionedAuthConfig.setAuthProvider("oauth");
        policy(partitionedAuthConfig).setVaryByContext(List.of("tenant"));
        assertThatCode(() -> validate(NoArgumentClient.class, partitionedAuthConfig))
                .doesNotThrowAnyException();

        ReactiveHttpClientProperties.ClientConfig sharedConfig = selectedPolicy();
        sharedConfig.setAuthProvider("oauth");
        policy(sharedConfig).setSharedResponse(true);
        policy(sharedConfig).setVaryByContext(List.of("locale"));
        assertThatCode(() -> validate(NoArgumentClient.class, sharedConfig)).doesNotThrowAnyException();
    }

    @Test
    void cacheKeyLabelsMustBeNonBlankAndUnique() throws Exception {
        assertThatThrownBy(() -> metadataCache.get(
                InvalidCacheKeyClient.class.getMethod("blank", String.class)))
                .hasMessageContaining("@CacheKey value must not be blank")
                .hasMessageContaining("blank");
        assertThatThrownBy(() -> metadataCache.get(
                InvalidCacheKeyClient.class.getMethod("duplicate", String.class, String.class)))
                .hasMessageContaining("Duplicate @CacheKey(\"tenant\")")
                .hasMessageContaining("duplicate");
    }

    @Test
    void cacheOnlyParametersMustBeSelectedByTheEffectivePolicy() {
        assertRejected(CacheOnlyParameterClient.class,
                new ReactiveHttpClientProperties.ClientConfig(),
                "inactive because response caching is disabled");

        ReactiveHttpClientProperties.ClientConfig omitted = selectedPolicy();
        assertRejected(CacheOnlyParameterClient.class, omitted,
                "is not selected by the effective policy's vary-by-parameters");

        ReactiveHttpClientProperties.ClientConfig selected = selectedPolicy();
        policy(selected).setVaryByParameters(List.of("tenant"));
        assertThatCode(() -> validate(CacheOnlyParameterClient.class, selected))
                .doesNotThrowAnyException();

        assertThatCode(() -> validate(RequestBoundCacheKeyClient.class,
                new ReactiveHttpClientProperties.ClientConfig()))
                .doesNotThrowAnyException();
    }

    @Test
    void startupAcceptsSupportedShapesAndRejectsUnfreezableInputs() {
        ReactiveHttpClientProperties.ClientConfig config = selectedPolicy();
        policy(config).setVaryByParameters(List.of("record", "array", "list", "map"));
        assertThatCode(() -> validate(SupportedInputClient.class, config)).doesNotThrowAnyException();

        assertRejected(MutableInputClient.class, selectedPolicy(), "cannot be copied safely");
        ReactiveHttpClientProperties.ClientConfig rawMapConfig = selectedPolicy();
        policy(rawMapConfig).setVaryByParameters(List.of("map"));
        assertRejected(RawMapClient.class, rawMapConfig, "raw container type");
        assertRejected(StreamKeyClient.class, selectedPolicy(), "streaming or application-owned request bodies");
    }

    @Test
    @SuppressWarnings("unchecked")
    void arraySnapshotsRequireCompatibleComponentTypes() throws Exception {
        ReactiveHttpClientProperties.ClientConfig concreteConfig = selectedPolicy();
        policy(concreteConfig).setVaryByParameters(List.of("values"));
        assertRejected(ConcreteContainerArrayClient.class, concreteConfig,
                "array component type java.util.ArrayList cannot hold the defensive cache-key snapshot");

        ReactiveHttpClientProperties.ClientConfig interfaceConfig = selectedPolicy();
        policy(interfaceConfig).setVaryByParameters(List.of("values"));
        assertThatCode(() -> validate(InterfaceContainerArrayClient.class, interfaceConfig))
                .doesNotThrowAnyException();
        Method method = InterfaceContainerArrayClient.class.getMethod("get", List[].class);
        RequestPlan plan = plan(InterfaceContainerArrayClient.class, method);
        EffectiveCachePolicy.Selection selection = EffectiveCachePolicy.validate(
                InterfaceContainerArrayClient.class, "interface-array", plan, interfaceConfig, "GET");
        List<String>[] compatible = (List<String>[]) new List<?>[]{new ArrayList<>(List.of("value"))};
        assertThatCode(() -> CacheKeyContract.freezeArguments(
                plan, new Object[]{compatible}, selection.policy())).doesNotThrowAnyException();

        ArrayList<String>[] covariant = (ArrayList<String>[]) new ArrayList<?>[]{
                new ArrayList<>(List.of("value"))};
        assertThatThrownBy(() -> CacheKeyContract.freezeArguments(
                plan, new Object[]{covariant}, selection.policy()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("runtime array component type java.util.ArrayList")
                .hasMessageContaining("cannot hold defensive snapshot type");
    }

    @Test
    void contextRecordWithMutableNestedStateIsRejectedBeforeDispatch() throws Exception {
        ReactiveHttpClientProperties.ClientConfig config = selectedPolicy();
        policy(config).setVaryByContext(List.of("tenant"));

        assertThatThrownBy(() -> key(
                NoArgumentClient.class,
                "context",
                NoArgumentClient.class.getMethod("get"),
                new Object[0],
                config,
                Context.of("tenant", new MutableRecord(List.of("tenant-a")))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("record component 'values' is mutable");
    }

    @Test
    void freezingSupportsConstantSpecificEnumsAndSequentialListsWithoutReorderingSets() throws Exception {
        ReactiveHttpClientProperties.ClientConfig config = selectedPolicy();
        policy(config).setVaryByParameters(List.of("mode", "values"));
        Method method = OrderedInputClient.class.getMethod("get", Mode.class, List.class, Set.class);
        RequestPlan plan = plan(OrderedInputClient.class, method);
        EffectiveCachePolicy.Selection selection = EffectiveCachePolicy.validate(
                OrderedInputClient.class, "ordered", plan, config, "GET");
        Set<String> orderedTags = new LinkedHashSet<>(List.of("z", "a"));

        Object[] frozen = CacheKeyContract.freezeArguments(
                plan,
                new Object[]{Mode.SPECIAL, new IteratorOnlyList<>(List.of("first", "second")), orderedTags},
                selection.policy());
        RequestArgumentResolver.ResolvedArgs resolved = argumentResolver.resolve(plan, frozen);

        assertThat(frozen[0]).isSameAs(Mode.SPECIAL);
        assertThat((Object) frozen[1]).isEqualTo(List.of("first", "second"));
        assertThat((Object) new ArrayList<>((Set<?>) frozen[2])).isEqualTo(List.of("z", "a"));
        assertThat(resolved.queryParams().get("tag")).containsExactly("z", "a");
        assertThatCode(() -> CacheKeyContract.derive(
                OrderedInputClient.class,
                "ordered",
                plan,
                frozen,
                resolved,
                Context.empty(),
                selection.policy())).doesNotThrowAnyException();
    }

    @Test
    void freezingChargesActualListTraversalAndPreservesIdentitySetElements() throws Exception {
        ReactiveHttpClientProperties.ClientConfig listConfig = selectedPolicy();
        policy(listConfig).setVaryByParameters(List.of("values"));
        Method listMethod = LargeScalarClient.class.getMethod("get", List.class);
        RequestPlan listPlan = plan(LargeScalarClient.class, listMethod);
        EffectiveCachePolicy.Selection listSelection = EffectiveCachePolicy.validate(
                LargeScalarClient.class, "underreported-list", listPlan, listConfig, "GET");

        assertThatThrownBy(() -> CacheKeyContract.freezeArguments(
                listPlan,
                new Object[]{new UnderreportedList<>(Collections.nCopies(10_001, "value"))},
                listSelection.policy()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cumulative element count exceeds maximum 10000");

        ReactiveHttpClientProperties.ClientConfig setConfig = selectedPolicy();
        policy(setConfig).setVaryByParameters(List.of("mode", "values"));
        Method setMethod = OrderedInputClient.class.getMethod("get", Mode.class, List.class, Set.class);
        RequestPlan setPlan = plan(OrderedInputClient.class, setMethod);
        EffectiveCachePolicy.Selection setSelection = EffectiveCachePolicy.validate(
                OrderedInputClient.class, "identity-set", setPlan, setConfig, "GET");
        Set<String> identitySet = Collections.newSetFromMap(new IdentityHashMap<>());
        identitySet.add(new String("same"));
        identitySet.add(new String("same"));

        Object[] frozen = CacheKeyContract.freezeArguments(
                setPlan, new Object[]{Mode.SPECIAL, List.of("value"), identitySet}, setSelection.policy());
        RequestArgumentResolver.ResolvedArgs resolved = argumentResolver.resolve(setPlan, frozen);

        assertThat((Set<?>) frozen[2]).hasSize(2);
        assertThat(resolved.queryParams().get("tag")).containsExactly("same", "same");
    }

    @Test
    void freezingPreservesEveryIdentityMapEntry() throws Exception {
        ReactiveHttpClientProperties.ClientConfig config = selectedPolicy();
        policy(config).setVaryByParameters(List.of("entries"));
        Method method = IdentityMapClient.class.getMethod("get", Map.class);
        RequestPlan plan = plan(IdentityMapClient.class, method);
        EffectiveCachePolicy.Selection selection = EffectiveCachePolicy.validate(
                IdentityMapClient.class, "identity-map", plan, config, "GET");
        Identity first = new Identity("same", 1);
        Identity second = new Identity("same", 1);
        Map<Identity, String> entries = new IdentityHashMap<>();
        entries.put(first, "value");
        entries.put(second, "value");

        Object[] frozen = CacheKeyContract.freezeArguments(
                plan, new Object[]{entries}, selection.policy());

        assertThat((Map<?, ?>) frozen[0]).hasSize(2);
        assertThat(((Map<?, ?>) frozen[0]).entrySet()).hasSize(2);
        assertThat(key(IdentityMapClient.class, "identity-map", method,
                new Object[]{entries}, config))
                .isNotEqualTo(key(IdentityMapClient.class, "identity-map", method,
                        new Object[]{Map.of(first, "value")}, config));
    }

    @Test
    void requestTargetProjectionIsBoundedBeforeRepeatedScalarsAreCombined() throws Exception {
        ReactiveHttpClientProperties.ClientConfig config = selectedPolicy();
        Method method = LargePathClient.class.getMethod("get", List.class);
        RequestPlan plan = plan(LargePathClient.class, method);
        EffectiveCachePolicy.Selection selection = EffectiveCachePolicy.validate(
                LargePathClient.class, "large-path", plan, config, "GET");
        List<String> repeated = Collections.nCopies(10_000, "x".repeat(512));
        Object[] frozen = CacheKeyContract.freezeArguments(
                plan, new Object[]{repeated}, selection.policy());
        RequestArgumentResolver.ResolvedArgs resolved = argumentResolver.resolve(plan, frozen);

        assertThatThrownBy(() -> CacheKeyContract.snapshotRequestTarget(resolved))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cache key material exceeds 1048576 bytes");
    }

    @Test
    void recordAccessorsThatCanChangeAfterValidationAreRejected() throws Exception {
        ReactiveHttpClientProperties.ClientConfig config = selectedPolicy();
        policy(config).setVaryByParameters(List.of("record"));
        Method method = UnstableRecordClient.class.getMethod("get", UnstableRecord.class);
        RequestPlan plan = plan(UnstableRecordClient.class, method);
        UnstableRecord.READS.set(0);

        assertThatThrownBy(() -> EffectiveCachePolicy.validate(
                UnstableRecordClient.class, "unstable-record", plan, config, "GET"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must use a canonical field accessor");
    }

    @Test
    void freezingRecordsDoesNotInvokeTheirCanonicalConstructorAgain() throws Exception {
        ReactiveHttpClientProperties.ClientConfig config = selectedPolicy();
        policy(config).setVaryByParameters(List.of("record"));
        Method method = ConstructorSideEffectClient.class.getMethod("get", ConstructorSideEffectRecord.class);
        RequestPlan plan = plan(ConstructorSideEffectClient.class, method);
        EffectiveCachePolicy.Selection selection = EffectiveCachePolicy.validate(
                ConstructorSideEffectClient.class, "constructor-side-effect", plan, config, "GET");
        ConstructorSideEffectRecord.CONSTRUCTIONS.set(0);
        ConstructorSideEffectRecord record = new ConstructorSideEffectRecord("value");

        Object[] frozen = CacheKeyContract.freezeArguments(
                plan, new Object[]{record}, selection.policy());

        assertThat(frozen[0]).isSameAs(record);
        assertThat(ConstructorSideEffectRecord.CONSTRUCTIONS).hasValue(1);
    }

    @Test
    void customRecordAndContainerRequestTargetConversionsAreRejected() throws Exception {
        ReactiveHttpClientProperties.ClientConfig config = selectedPolicy();
        Method method = SlugPathClient.class.getMethod("get", Slug.class);
        RequestPlan plan = plan(SlugPathClient.class, method);
        EffectiveCachePolicy.Selection selection = EffectiveCachePolicy.validate(
                SlugPathClient.class, "slug", plan, config, "GET");
        Slug slug = new Slug("catalog-item");
        Object[] frozen = CacheKeyContract.freezeArguments(plan, new Object[]{slug}, selection.policy());

        assertThat(frozen[0]).isSameAs(slug);
        assertThatThrownBy(() -> CacheKeyContract.snapshotRequestTarget(
                argumentResolver.resolve(plan, frozen)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("overrides toString()")
                .hasMessageContaining("cannot be bounded");

        Method listMethod = LargePathClient.class.getMethod("get", List.class);
        RequestPlan listPlan = plan(LargePathClient.class, listMethod);
        EffectiveCachePolicy.Selection listSelection = EffectiveCachePolicy.validate(
                LargePathClient.class, "custom-list", listPlan, config, "GET");
        assertThatThrownBy(() -> CacheKeyContract.freezeArguments(
                listPlan, new Object[]{new PipeDelimitedList("a", "b")}, listSelection.policy()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("custom container toString() cannot be bounded");
    }

    @Test
    void defaultRecordRequestTargetProjectionIsStructurallyBounded() {
        LargeDefaultRecord small = new LargeDefaultRecord("left", "right");
        RequestArgumentResolver.ResolvedArgs projected = CacheKeyContract.snapshotRequestTarget(
                new RequestArgumentResolver.ResolvedArgs(
                        Map.of("value", small), Map.of(), Map.of(), null));
        assertThat(projected.pathVars())
                .containsEntry("value", "LargeDefaultRecord[left=left, right=right]");

        LargeDefaultRecord large = new LargeDefaultRecord(
                "x".repeat(600_000), "x".repeat(600_000));
        assertThatThrownBy(() -> CacheKeyContract.snapshotRequestTarget(
                new RequestArgumentResolver.ResolvedArgs(
                        Map.of("value", large), Map.of(), Map.of(), null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cache key material exceeds 1048576 bytes");
    }

    @Test
    void selectedCustomListBodiesFailBeforeTheirCodecTypeCanChange() throws Exception {
        ReactiveHttpClientProperties.ClientConfig config = selectedPolicy();
        policy(config).setVaryByParameters(List.of("body"));
        Method method = CustomListBodyClient.class.getMethod("get", List.class);
        RequestPlan plan = plan(CustomListBodyClient.class, method);
        EffectiveCachePolicy.Selection selection = EffectiveCachePolicy.validate(
                CustomListBodyClient.class, "custom-list-body", plan, config, "GET");

        assertThatThrownBy(() -> CacheKeyContract.freezeArguments(
                plan, new Object[]{new PipeDelimitedList("a", "b")}, selection.policy()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("selected request body list implementation")
                .hasMessageContaining(PipeDelimitedList.class.getName())
                .hasMessageContaining("concrete JSON codec semantics");
    }

    @Test
    void sharedResponsesLeaveUnselectedRequestVariantsUnchanged() throws Exception {
        ReactiveHttpClientProperties.ClientConfig config = selectedPolicy();
        policy(config).setVaryByHeaders(List.of());
        policy(config).setSharedResponse(true);

        assertThatCode(() -> validate(SharedUnselectedRequestClient.class, config))
                .doesNotThrowAnyException();

        Method method = SharedUnselectedRequestClient.class.getMethod(
                "get", MutableInput.class, MutableInput.class);
        RequestPlan plan = plan(SharedUnselectedRequestClient.class, method);
        EffectiveCachePolicy.Selection selection = EffectiveCachePolicy.validate(
                SharedUnselectedRequestClient.class, "shared-unselected", plan, config, "GET");
        MutableInput header = new MutableInput();
        MutableInput body = new MutableInput();

        Object[] frozen = CacheKeyContract.freezeArguments(
                plan, new Object[]{header, body}, selection.policy());

        assertThat(frozen[0]).isSameAs(header);
        assertThat(frozen[1]).isSameAs(body);
    }

    @Test
    void selectedCustomSetAndMapBodiesFailBeforeTheirCodecTypesCanChange() throws Exception {
        ReactiveHttpClientProperties.ClientConfig config = selectedPolicy();
        policy(config).setVaryByParameters(List.of("body"));

        Method setMethod = CustomSetBodyClient.class.getMethod("get", Set.class);
        RequestPlan setPlan = plan(CustomSetBodyClient.class, setMethod);
        EffectiveCachePolicy.Selection setSelection = EffectiveCachePolicy.validate(
                CustomSetBodyClient.class, "custom-set-body", setPlan, config, "GET");
        assertThatThrownBy(() -> CacheKeyContract.freezeArguments(
                setPlan, new Object[]{new CustomBodySet("a", "b")}, setSelection.policy()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("selected request body set implementation")
                .hasMessageContaining(CustomBodySet.class.getName())
                .hasMessageContaining("concrete JSON codec semantics");

        Method mapMethod = CustomMapBodyClient.class.getMethod("get", Map.class);
        RequestPlan mapPlan = plan(CustomMapBodyClient.class, mapMethod);
        EffectiveCachePolicy.Selection mapSelection = EffectiveCachePolicy.validate(
                CustomMapBodyClient.class, "custom-map-body", mapPlan, config, "GET");
        assertThatThrownBy(() -> CacheKeyContract.freezeArguments(
                mapPlan, new Object[]{new CustomBodyMap("a", "b")}, mapSelection.policy()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("selected request body map implementation")
                .hasMessageContaining(CustomBodyMap.class.getName())
                .hasMessageContaining("concrete JSON codec semantics");
    }

    @Test
    void headerProjectionEnforcesCumulativeByteLimitBeforeStringification() throws Exception {
        ReactiveHttpClientProperties.ClientConfig config = selectedPolicy();
        policy(config).setVaryByHeaders(List.of("X-Metadata"));
        policy(config).setSharedResponse(true);
        Method method = LargeHeaderClient.class.getMethod("get", List.class);
        RequestPlan plan = plan(LargeHeaderClient.class, method);
        EffectiveCachePolicy.Selection selection = EffectiveCachePolicy.validate(
                LargeHeaderClient.class, "large-header", plan, config, "GET");
        String repeated = "x".repeat(256_000);
        List<String> expandingValue = Collections.nCopies(8, repeated);

        assertThatThrownBy(() -> CacheKeyContract.freezeArguments(
                plan, new Object[]{List.of(expandingValue)}, selection.policy()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cache key material exceeds 1048576 bytes");
    }

    @Test
    void selectedHeadersRejectCustomNestedContainerConversionsBeforeFreezing() throws Exception {
        ReactiveHttpClientProperties.ClientConfig config = selectedPolicy();
        policy(config).setVaryByHeaders(List.of("X-Metadata"));
        policy(config).setSharedResponse(true);
        Method method = LargeHeaderClient.class.getMethod("get", List.class);
        RequestPlan plan = plan(LargeHeaderClient.class, method);
        EffectiveCachePolicy.Selection selection = EffectiveCachePolicy.validate(
                LargeHeaderClient.class, "custom-header", plan, config, "GET");

        assertThatThrownBy(() -> CacheKeyContract.freezeArguments(
                plan, new Object[]{List.of(new PipeDelimitedList("a", "b"))}, selection.policy()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("selected header parameter index 0")
                .hasMessageContaining("custom container toString() cannot be bounded");
    }

    @Test
    void customEnumRequestConversionsAreRejectedWithoutCallingToString() throws Exception {
        ReactiveHttpClientProperties.ClientConfig config = selectedPolicy();
        Method method = EnumPathClient.class.getMethod("get", CustomStringMode.class);
        RequestPlan plan = plan(EnumPathClient.class, method);
        EffectiveCachePolicy.Selection selection = EffectiveCachePolicy.validate(
                EnumPathClient.class, "enum-path", plan, config, "GET");
        CustomStringMode.TO_STRING_CALLS.set(0);

        assertThatThrownBy(() -> CacheKeyContract.freezeArguments(
                plan, new Object[]{CustomStringMode.VALUE}, selection.policy()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("overrides toString()")
                .hasMessageContaining("explicitly formatted scalar");
        assertThat(CustomStringMode.TO_STRING_CALLS).hasValue(0);
    }

    @Test
    void selectedBodyUsesOneCodecRepresentationForTheKeyAndWireRequest() throws Exception {
        ReactiveHttpClientProperties.ClientConfig config = selectedPolicy();
        policy(config).setVaryByParameters(List.of("body"));
        config.setAuthProvider("capture");
        Method method = JsonValueBodyClient.class.getMethod("get", JsonValueBody.class);
        RequestPlan plan = plan(JsonValueBodyClient.class, method);
        EffectiveCachePolicy.Selection selection = EffectiveCachePolicy.validate(
                JsonValueBodyClient.class, "json-value", plan, config, "GET");
        JsonValueBody body = new JsonValueBody("catalog-item");
        byte[] wireBytes = TestJsonCodecs.jsonCodec().write(body);
        Object[] frozen = CacheKeyContract.freezeArguments(plan, new Object[]{body}, selection.policy());
        RequestArgumentResolver.ResolvedArgs resolved = argumentResolver.resolve(plan, frozen);

        CacheKeyContract.OpaqueKey codecKey = CacheKeyContract.derive(
                JsonValueBodyClient.class, "json-value", plan, frozen, resolved,
                Context.empty(), selection.policy(), CacheKeyContract.serializedBodyKey(wireBytes)).key();
        CacheKeyContract.OpaqueKey differentWireKey = CacheKeyContract.derive(
                JsonValueBodyClient.class, "json-value", plan, frozen, resolved,
                Context.empty(), selection.policy(),
                CacheKeyContract.serializedBodyKey("\"different\"".getBytes(StandardCharsets.UTF_8))).key();
        assertThat(codecKey).isNotEqualTo(differentWireKey);
        CacheKeyContract.OpaqueKey absentBodyKey = CacheKeyContract.derive(
                JsonValueBodyClient.class, "json-value", plan, frozen, resolved,
                Context.empty(), selection.policy(), CacheKeyContract.absentSerializedBodyKey()).key();
        CacheKeyContract.OpaqueKey emptyBodyKey = CacheKeyContract.derive(
                JsonValueBodyClient.class, "json-value", plan, frozen, resolved,
                Context.empty(), selection.policy(), CacheKeyContract.serializedBodyKey(new byte[0])).key();
        assertThat(absentBodyKey).isNotEqualTo(emptyBodyKey);

        AtomicReference<String> dispatchedBody = new AtomicReference<>();
        AtomicReference<byte[]> signedBody = new AtomicReference<>();
        AtomicInteger boundedWrites = new AtomicInteger();
        ReactiveHttpClientJsonCodec codec = countingCodec(boundedWrites);
        AuthProvider authProvider = request -> {
            signedBody.set(((byte[]) request.requestBody()).clone());
            return Mono.just(AuthContext.empty());
        };
        WebClient webClient = WebClient.builder()
                .baseUrl("http://cache-key.test")
                .filter(new OutboundAuthFilter("json-value", authProvider))
                .exchangeFunction(request -> {
                    dispatchedBody.set(materialize(request).getBodyAsString().block());
                    return Mono.just(ClientResponse.create(HttpStatus.OK)
                            .header(HttpHeaders.CONTENT_TYPE, "text/plain")
                            .body("ok")
                            .build());
                })
                .build();
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.refresh();
            ReactiveClientInvocationHandler handler = new ReactiveClientInvocationHandler(
                    webClient, metadataCache, argumentResolver, new DefaultErrorDecoder(), config,
                    "json-value", JsonValueBodyClient.class, context,
                    new NoopResilienceOperatorApplier(), codec,
                    new ReactiveHttpClientProperties.ObservabilityConfig(),
                    LocalResponseCacheManager.testing(System::nanoTime), authProvider,
                    "http://cache-key.test");
            JsonValueBodyClient client = (JsonValueBodyClient) Proxy.newProxyInstance(
                    getClass().getClassLoader(), new Class<?>[]{JsonValueBodyClient.class}, handler);

            assertThat(client.get(body).block()).isEqualTo("ok");
        }

        assertThat(dispatchedBody).hasValue(new String(wireBytes, StandardCharsets.UTF_8));
        assertThat(dispatchedBody).hasValue("\"wire-catalog-item\"");
        assertThat(signedBody.get()).containsExactly(wireBytes);
        assertThat(boundedWrites).hasValue(1);
    }

    @Test
    void mutatingAuthRawBodyCannotChangePreparedWireBytesOrCacheIdentity() {
        ReactiveHttpClientProperties.ClientConfig config = selectedPolicy();
        policy(config).setVaryByParameters(List.of("body"));
        policy(config).setSharedResponse(true);
        config.setAuthProvider("mutating-body");
        AtomicInteger authCalls = new AtomicInteger();
        AtomicInteger dispatches = new AtomicInteger();
        List<String> dispatchedBodies = new ArrayList<>();
        AuthProvider authProvider = request -> {
            authCalls.incrementAndGet();
            byte[] authBytes = (byte[]) request.requestBody();
            authBytes[0] = 'X';
            byte[] attributeBytes = (byte[]) request.request()
                    .attribute(AuthRequest.REQUEST_RAW_BODY_ATTRIBUTE)
                    .orElseThrow();
            attributeBytes[1] = 'Y';
            return Mono.just(AuthContext.empty());
        };
        WebClient webClient = WebClient.builder()
                .baseUrl("http://cache-key.test")
                .filter(new OutboundAuthFilter("semantic-body", authProvider))
                .exchangeFunction(request -> {
                    String body = materialize(request).getBodyAsString().block();
                    dispatches.incrementAndGet();
                    dispatchedBodies.add(body);
                    return Mono.just(ClientResponse.create(HttpStatus.OK).body(body).build());
                })
                .build();

        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.refresh();
            ReactiveClientInvocationHandler handler = new ReactiveClientInvocationHandler(
                    webClient, metadataCache, argumentResolver, new DefaultErrorDecoder(), config,
                    "semantic-body", SemanticBodyClient.class, context,
                    new NoopResilienceOperatorApplier(), TestJsonCodecs.jsonCodec(),
                    new ReactiveHttpClientProperties.ObservabilityConfig(),
                    LocalResponseCacheManager.testing(System::nanoTime), authProvider,
                    "http://cache-key.test");
            SemanticBodyClient client = (SemanticBodyClient) Proxy.newProxyInstance(
                    getClass().getClassLoader(), new Class<?>[]{SemanticBodyClient.class}, handler);

            assertThat(client.query("same", MediaType.TEXT_PLAIN_VALUE).block()).isEqualTo("same");
            assertThat(client.query("same", MediaType.TEXT_PLAIN_VALUE).block()).isEqualTo("same");
        }

        assertThat(authCalls).hasValue(2);
        assertThat(dispatches).hasValue(1);
        assertThat(dispatchedBodies).containsExactly("same");
    }

    @Test
    void semanticPostBodyIdentityIncludesEffectiveContentTypeCharsetAndPresence() {
        ReactiveHttpClientProperties.ClientConfig config = selectedPolicy();
        policy(config).setVaryByParameters(List.of("body"));
        policy(config).setSharedResponse(true);
        AtomicInteger dispatches = new AtomicInteger();
        List<String> requests = new ArrayList<>();
        WebClient webClient = WebClient.builder()
                .baseUrl("http://cache-key.test")
                .exchangeFunction(request -> {
                    MockClientHttpRequest materialized = materialize(request);
                    String contentType = materialized.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE);
                    String body = materialized.getBodyAsString().block();
                    String response = String.valueOf(contentType) + "|" + body;
                    dispatches.incrementAndGet();
                    requests.add(response);
                    return Mono.just(ClientResponse.create(HttpStatus.OK).body(response).build());
                })
                .build();

        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.refresh();
            ReactiveClientInvocationHandler handler = new ReactiveClientInvocationHandler(
                    webClient, metadataCache, argumentResolver, new DefaultErrorDecoder(), config,
                    "semantic-body", SemanticBodyClient.class, context,
                    new NoopResilienceOperatorApplier(), TestJsonCodecs.jsonCodec(),
                    new ReactiveHttpClientProperties.ObservabilityConfig(),
                    LocalResponseCacheManager.testing(System::nanoTime));
            SemanticBodyClient client = (SemanticBodyClient) Proxy.newProxyInstance(
                    getClass().getClassLoader(), new Class<?>[]{SemanticBodyClient.class}, handler);

            String text = client.query("same", MediaType.TEXT_PLAIN_VALUE).block();
            String json = client.query("same", MediaType.APPLICATION_JSON_VALUE).block();
            assertThat(text).isNotEqualTo(json);
            assertThat(client.query("same", MediaType.TEXT_PLAIN_VALUE).block()).isEqualTo(text);
            assertThat(client.query("same", MediaType.APPLICATION_JSON_VALUE).block()).isEqualTo(json);

            String latin = client.query("\u00e9", "text/plain;charset=ISO-8859-1").block();
            String utf8 = client.query("\u00e9", "text/plain;charset=UTF-8").block();
            assertThat(latin).isNotEqualTo(utf8);

            String absent = client.query(null, null).block();
            String explicit = client.query(null, MediaType.APPLICATION_JSON_VALUE).block();
            String presentEmpty = client.query("", null).block();
            assertThat(absent).isNotEqualTo(explicit);
            assertThat(presentEmpty).isEqualTo(explicit);
        }

        assertThat(dispatches).hasValue(7);
        assertThat(requests).contains(
                "text/plain;charset=ISO-8859-1|\u00e9",
                "text/plain;charset=UTF-8|\u00e9",
                "null|",
                "application/json|");
    }

    @Test
    void cachePreparationTimeoutDoesNotSubscribeBodyOrPublishSensitiveMaterial() {
        ReactiveHttpClientProperties.ClientConfig config = selectedPolicy();
        policy(config).setVaryByParameters(List.of("body"));
        policy(config).setSharedResponse(true);
        config.setAuthProvider("never");
        config.setLogicalCallTimeoutMs(25);
        AtomicInteger dispatches = new AtomicInteger();
        AtomicInteger bodySubscriptions = new AtomicInteger();
        AuthProvider authProvider = request -> Mono.never();
        WebClient webClient = WebClient.builder()
                .baseUrl("http://cache-key.test")
                .filter(new OutboundAuthFilter("semantic-body", authProvider))
                .filter((request, next) -> next.exchange(ClientRequest.from(request)
                        .body((output, inserterContext) -> Mono.defer(() -> {
                            bodySubscriptions.incrementAndGet();
                            return request.body().insert(output, inserterContext);
                        }))
                        .build()))
                .exchangeFunction(request -> {
                    dispatches.incrementAndGet();
                    return Mono.just(ClientResponse.create(HttpStatus.OK).body("unexpected").build());
                })
                .build();
        LocalResponseCacheManager manager = LocalResponseCacheManager.testing(System::nanoTime);

        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.refresh();
            ReactiveClientInvocationHandler handler = new ReactiveClientInvocationHandler(
                    webClient, metadataCache, argumentResolver, new DefaultErrorDecoder(), config,
                    "semantic-body", SemanticBodyClient.class, context,
                    new NoopResilienceOperatorApplier(), TestJsonCodecs.jsonCodec(),
                    new ReactiveHttpClientProperties.ObservabilityConfig(), manager, authProvider,
                    "http://cache-key.test");
            SemanticBodyClient client = (SemanticBodyClient) Proxy.newProxyInstance(
                    getClass().getClassLoader(), new Class<?>[]{SemanticBodyClient.class}, handler);

            assertThatThrownBy(() -> client.query("private-body-material", MediaType.TEXT_PLAIN_VALUE).block())
                    .hasRootCauseInstanceOf(LogicalCallTimeoutException.class)
                    .hasMessageNotContaining("private-body-material");
        }

        assertThat(dispatches).hasValue(0);
        assertThat(bodySubscriptions).hasValue(0);
        assertThat(manager.snapshot().currentSize()).isZero();
    }

    @Test
    void authCannotReplaceContentTypeAfterBodyIdentityPreparation() {
        ReactiveHttpClientProperties.ClientConfig config = selectedPolicy();
        policy(config).setVaryByParameters(List.of("body"));
        policy(config).setSharedResponse(true);
        config.setAuthProvider("content-type");
        AtomicInteger dispatches = new AtomicInteger();
        AuthProvider authProvider = request -> Mono.just(AuthContext.builder()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build());
        WebClient webClient = WebClient.builder()
                .baseUrl("http://cache-key.test")
                .filter(new OutboundAuthFilter("semantic-body", authProvider))
                .exchangeFunction(request -> {
                    dispatches.incrementAndGet();
                    return Mono.just(ClientResponse.create(HttpStatus.OK).body("unexpected").build());
                })
                .build();
        LocalResponseCacheManager manager = LocalResponseCacheManager.testing(System::nanoTime);

        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.refresh();
            ReactiveClientInvocationHandler handler = new ReactiveClientInvocationHandler(
                    webClient, metadataCache, argumentResolver, new DefaultErrorDecoder(), config,
                    "semantic-body", SemanticBodyClient.class, context,
                    new NoopResilienceOperatorApplier(), TestJsonCodecs.jsonCodec(),
                    new ReactiveHttpClientProperties.ObservabilityConfig(), manager, authProvider,
                    "http://cache-key.test");
            SemanticBodyClient client = (SemanticBodyClient) Proxy.newProxyInstance(
                    getClass().getClassLoader(), new Class<?>[]{SemanticBodyClient.class}, handler);

            assertThatThrownBy(() -> client.query("private-body-material", MediaType.TEXT_PLAIN_VALUE).block())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("cannot replace Content-Type")
                    .hasMessageNotContaining("private-body-material");
        }

        assertThat(dispatches).hasValue(0);
        assertThat(manager.snapshot().currentSize()).isZero();
    }

    @Test
    void authRefreshCannotReplacePreparedContentType() {
        ReactiveHttpClientProperties.ClientConfig config = selectedPolicy();
        policy(config).setVaryByParameters(List.of("body"));
        policy(config).setSharedResponse(true);
        config.setAuthProvider("content-type-refresh");
        AtomicInteger dispatches = new AtomicInteger();
        AtomicInteger resolutions = new AtomicInteger();
        InvalidatableAuthProvider authProvider = new InvalidatableAuthProvider() {
            @Override
            public Mono<AuthContext> getAuth(io.github.huynhngochuyhoang.httpstarter.auth.AuthRequest request) {
                return Mono.just(resolutions.incrementAndGet() == 1
                        ? AuthContext.empty()
                        : AuthContext.builder()
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .build());
            }

            @Override
            public Mono<Void> invalidate() {
                return Mono.empty();
            }
        };
        WebClient webClient = WebClient.builder()
                .baseUrl("http://cache-key.test")
                .filter(new OutboundAuthFilter("semantic-body", authProvider))
                .exchangeFunction(request -> {
                    dispatches.incrementAndGet();
                    return Mono.just(ClientResponse.create(HttpStatus.UNAUTHORIZED).build());
                })
                .build();
        LocalResponseCacheManager manager = LocalResponseCacheManager.testing(System::nanoTime);

        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.refresh();
            ReactiveClientInvocationHandler handler = new ReactiveClientInvocationHandler(
                    webClient, metadataCache, argumentResolver, new DefaultErrorDecoder(), config,
                    "semantic-body", SemanticBodyClient.class, context,
                    new NoopResilienceOperatorApplier(), TestJsonCodecs.jsonCodec(),
                    new ReactiveHttpClientProperties.ObservabilityConfig(), manager, authProvider,
                    "http://cache-key.test");
            SemanticBodyClient client = (SemanticBodyClient) Proxy.newProxyInstance(
                    getClass().getClassLoader(), new Class<?>[]{SemanticBodyClient.class}, handler);

            assertThatThrownBy(() -> client.query("private-body-material", MediaType.TEXT_PLAIN_VALUE).block())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("cannot replace Content-Type")
                    .hasMessageNotContaining("private-body-material");
        }

        assertThat(resolutions).hasValue(2);
        assertThat(dispatches).hasValue(1);
        assertThat(manager.snapshot().currentSize()).isZero();
    }

    @Test
    void authContentTypeComparisonIgnoresParameterOrder() {
        ReactiveHttpClientProperties.ClientConfig config = selectedPolicy();
        policy(config).setVaryByParameters(List.of("body"));
        policy(config).setSharedResponse(true);
        config.setAuthProvider("content-type-order");
        AtomicInteger dispatches = new AtomicInteger();
        AuthProvider authProvider = request -> Mono.just(AuthContext.builder()
                .header(HttpHeaders.CONTENT_TYPE, "application/json;profile=v1;charset=UTF-8")
                .build());
        WebClient webClient = WebClient.builder()
                .baseUrl("http://cache-key.test")
                .filter(new OutboundAuthFilter("semantic-body", authProvider))
                .exchangeFunction(request -> {
                    dispatches.incrementAndGet();
                    return Mono.just(ClientResponse.create(HttpStatus.OK).body("ok").build());
                })
                .build();

        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.refresh();
            ReactiveClientInvocationHandler handler = new ReactiveClientInvocationHandler(
                    webClient, metadataCache, argumentResolver, new DefaultErrorDecoder(), config,
                    "semantic-body", SemanticBodyClient.class, context,
                    new NoopResilienceOperatorApplier(), TestJsonCodecs.jsonCodec(),
                    new ReactiveHttpClientProperties.ObservabilityConfig(),
                    LocalResponseCacheManager.testing(System::nanoTime), authProvider,
                    "http://cache-key.test");
            SemanticBodyClient client = (SemanticBodyClient) Proxy.newProxyInstance(
                    getClass().getClassLoader(), new Class<?>[]{SemanticBodyClient.class}, handler);

            assertThat(client.query("same", "application/json;charset=UTF-8;profile=v1").block())
                    .isEqualTo("ok");
            assertThat(client.query("same", "application/json;profile=v1;charset=UTF-8").block())
                    .isEqualTo("ok");
        }

        assertThat(dispatches).hasValue(1);
    }

    @Test
    void authContentTypeValidationSkipsUnselectedAndBodilessCacheRequests() {
        ReactiveHttpClientProperties.ClientConfig config = selectedPolicy();
        policy(config).setSharedResponse(true);
        config.setAuthProvider("ordinary-content-type");
        AtomicInteger dispatches = new AtomicInteger();
        AuthProvider authProvider = request -> Mono.just(AuthContext.builder()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_VALUE)
                .build());
        WebClient webClient = WebClient.builder()
                .baseUrl("http://cache-key.test")
                .filter(new OutboundAuthFilter("ordinary-cache", authProvider))
                .exchangeFunction(request -> {
                    dispatches.incrementAndGet();
                    return Mono.just(ClientResponse.create(HttpStatus.OK).body("ok").build());
                })
                .build();

        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.refresh();
            ReactiveClientInvocationHandler bodyHandler = new ReactiveClientInvocationHandler(
                    webClient, metadataCache, argumentResolver, new DefaultErrorDecoder(), config,
                    "ordinary-cache", UnselectedStringBodyClient.class, context,
                    new NoopResilienceOperatorApplier(), TestJsonCodecs.jsonCodec(),
                    new ReactiveHttpClientProperties.ObservabilityConfig(),
                    LocalResponseCacheManager.testing(System::nanoTime), authProvider,
                    "http://cache-key.test");
            UnselectedStringBodyClient bodyClient = (UnselectedStringBodyClient) Proxy.newProxyInstance(
                    getClass().getClassLoader(), new Class<?>[]{UnselectedStringBodyClient.class}, bodyHandler);
            assertThat(bodyClient.get("ordinary").block()).isEqualTo("ok");

            ReactiveClientInvocationHandler bodilessHandler = new ReactiveClientInvocationHandler(
                    webClient, metadataCache, argumentResolver, new DefaultErrorDecoder(), config,
                    "ordinary-cache", BodilessCacheClient.class, context,
                    new NoopResilienceOperatorApplier(), TestJsonCodecs.jsonCodec(),
                    new ReactiveHttpClientProperties.ObservabilityConfig(),
                    LocalResponseCacheManager.testing(System::nanoTime), authProvider,
                    "http://cache-key.test");
            BodilessCacheClient bodilessClient = (BodilessCacheClient) Proxy.newProxyInstance(
                    getClass().getClassLoader(), new Class<?>[]{BodilessCacheClient.class}, bodilessHandler);
            assertThat(bodilessClient.get().block()).isEqualTo("ok");
        }

        assertThat(dispatches).hasValue(2);
    }

    @Test
    void oversizedSelectedStringBodiesFailBeforeDispatchOrByteAllocation() {
        ReactiveHttpClientProperties.ClientConfig config = selectedPolicy();
        policy(config).setVaryByParameters(List.of("body"));
        AtomicInteger dispatches = new AtomicInteger();
        WebClient webClient = WebClient.builder()
                .baseUrl("http://cache-key.test")
                .exchangeFunction(request -> {
                    dispatches.incrementAndGet();
                    return Mono.just(ClientResponse.create(HttpStatus.OK).body("ok").build());
                })
                .build();

        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.refresh();
            ReactiveClientInvocationHandler handler = new ReactiveClientInvocationHandler(
                    webClient, metadataCache, argumentResolver, new DefaultErrorDecoder(), config,
                    "large-string", StringBodyClient.class, context,
                    new NoopResilienceOperatorApplier(), TestJsonCodecs.jsonCodec(),
                    new ReactiveHttpClientProperties.ObservabilityConfig());
            StringBodyClient client = (StringBodyClient) Proxy.newProxyInstance(
                    getClass().getClassLoader(), new Class<?>[]{StringBodyClient.class}, handler);

            assertThatThrownBy(() -> client.get("x".repeat(1_048_577)).block())
                    .isInstanceOf(RequestSerializationException.class)
                    .hasRootCauseMessage("Cache-selected request body exceeds 1048576 bytes");
        }

        assertThat(dispatches).hasValue(0);
    }

    @Test
    void oversizedByteBodiesAndInvalidMediaTypesFailBeforeDispatch() {
        ReactiveHttpClientProperties.ClientConfig config = selectedPolicy();
        policy(config).setVaryByParameters(List.of("body"));
        policy(config).setSharedResponse(true);
        AtomicInteger dispatches = new AtomicInteger();
        WebClient webClient = WebClient.builder()
                .baseUrl("http://cache-key.test")
                .exchangeFunction(request -> {
                    dispatches.incrementAndGet();
                    return Mono.just(ClientResponse.create(HttpStatus.OK).body("unexpected").build());
                })
                .build();

        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.refresh();
            ReactiveClientInvocationHandler handler = new ReactiveClientInvocationHandler(
                    webClient, metadataCache, argumentResolver, new DefaultErrorDecoder(), config,
                    "byte-body", ByteArrayBodyClient.class, context,
                    new NoopResilienceOperatorApplier(), TestJsonCodecs.jsonCodec(),
                    new ReactiveHttpClientProperties.ObservabilityConfig(),
                    LocalResponseCacheManager.testing(System::nanoTime));
            ByteArrayBodyClient client = (ByteArrayBodyClient) Proxy.newProxyInstance(
                    getClass().getClassLoader(), new Class<?>[]{ByteArrayBodyClient.class}, handler);

            assertThatThrownBy(() -> client.query(
                            new byte[1_048_577], MediaType.APPLICATION_OCTET_STREAM_VALUE).block())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("cumulative element count exceeds maximum 10000");
            assertThatThrownBy(() -> client.query(new byte[]{1}, "not a media type").block())
                    .isInstanceOf(RequestSerializationException.class)
                    .cause()
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Cache-selected request bodies require a valid effective Content-Type");
        }

        assertThat(dispatches).hasValue(0);
    }

    @Test
    void oversizedSelectedJsonBodiesFailThroughBoundedSerializationBeforeDispatch() {
        ReactiveHttpClientProperties.ClientConfig config = selectedPolicy();
        policy(config).setVaryByParameters(List.of("body"));
        AtomicInteger dispatches = new AtomicInteger();
        WebClient webClient = WebClient.builder()
                .baseUrl("http://cache-key.test")
                .exchangeFunction(request -> {
                    dispatches.incrementAndGet();
                    return Mono.just(ClientResponse.create(HttpStatus.OK).body("ok").build());
                })
                .build();

        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.refresh();
            ReactiveClientInvocationHandler handler = new ReactiveClientInvocationHandler(
                    webClient, metadataCache, argumentResolver, new DefaultErrorDecoder(), config,
                    "large-json", LargeJsonBodyClient.class, context,
                    new NoopResilienceOperatorApplier(), TestJsonCodecs.jsonCodec(),
                    new ReactiveHttpClientProperties.ObservabilityConfig());
            LargeJsonBodyClient client = (LargeJsonBodyClient) Proxy.newProxyInstance(
                    getClass().getClassLoader(), new Class<?>[]{LargeJsonBodyClient.class}, handler);

            assertThatThrownBy(() -> client.get(new LargeJsonBody("x".repeat(1_048_577))).block())
                    .isInstanceOf(RequestSerializationException.class)
                    .hasRootCauseMessage("Cache-selected request body exceeds 1048576 bytes");
        }

        assertThat(dispatches).hasValue(0);
    }

    @Test
    void nonPublicRecordValuesCanBeFrozenAndEncoded() throws Exception {
        ReactiveHttpClientProperties.ClientConfig config = selectedPolicy();
        Class<?> clientType = NonPublicRecordFixture.clientType();

        assertThatCode(() -> key(
                clientType,
                "non-public-record",
                NonPublicRecordFixture.method(),
                new Object[]{NonPublicRecordFixture.create("tenant-a")},
                config))
                .doesNotThrowAnyException();
    }

    @Test
    void headerVariantsIncludeEveryCaseInsensitiveHeaderSource() throws Exception {
        ReactiveHttpClientProperties.ClientConfig config = selectedPolicy();
        policy(config).setVaryByHeaders(List.of("X-Tenant", "Idempotency-Key"));
        Method method = HeaderCaseClient.class.getMethod("get", String.class);
        RequestPlan plan = plan(HeaderCaseClient.class, method);
        EffectiveCachePolicy.Selection selection = EffectiveCachePolicy.validate(
                HeaderCaseClient.class, "header-case", plan, config, "GET");
        Object[] frozen = CacheKeyContract.freezeArguments(plan, new Object[]{"dynamic-a"}, selection.policy());
        RequestArgumentResolver.ResolvedArgs base = argumentResolver.resolve(plan, frozen);

        Map<String, List<String>> firstHeaders = new LinkedHashMap<>();
        firstHeaders.put("x-tenant", List.of("static"));
        firstHeaders.put("X-TENANT", List.of("dynamic-a"));
        Map<String, List<String>> secondHeaders = new LinkedHashMap<>();
        secondHeaders.put("x-tenant", List.of("static"));
        secondHeaders.put("X-TENANT", List.of("dynamic-b"));

        CacheKeyContract.OpaqueKey first = CacheKeyContract.derive(
                HeaderCaseClient.class,
                "header-case",
                plan,
                frozen,
                new RequestArgumentResolver.ResolvedArgs(
                        base.pathVars(), base.queryParams(), firstHeaders, base.body(), Map.of()),
                Context.empty(),
                selection.policy()).key();
        CacheKeyContract.OpaqueKey second = CacheKeyContract.derive(
                HeaderCaseClient.class,
                "header-case",
                plan,
                frozen,
                new RequestArgumentResolver.ResolvedArgs(
                        base.pathVars(), base.queryParams(), secondHeaders, base.body(), Map.of()),
                Context.empty(),
                selection.policy()).key();

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void contextIdempotencyKeyIsAppliedBeforeHeaderVariantDerivation() throws Exception {
        ReactiveHttpClientProperties.ClientConfig config = selectedPolicy();
        policy(config).setVaryByHeaders(List.of("Idempotency-Key"));
        Method method = IdempotencyVariantClient.class.getMethod("get", String.class);
        RequestPlan plan = plan(IdempotencyVariantClient.class, method);
        EffectiveCachePolicy.Selection selection = EffectiveCachePolicy.validate(
                IdempotencyVariantClient.class, "idempotency", plan, config, "GET");
        Object[] frozen = CacheKeyContract.freezeArguments(plan, new Object[]{null}, selection.policy());
        RequestArgumentResolver.ResolvedArgs base = argumentResolver.resolve(plan, frozen);
        Context firstContext = Context.of(RequestContext.IDEMPOTENCY_KEY_CONTEXT_KEY, "idem-a");
        Context secondContext = Context.of(RequestContext.IDEMPOTENCY_KEY_CONTEXT_KEY, "idem-b");
        RequestArgumentResolver.ResolvedArgs firstResolved =
                ReactiveClientInvocationHandler.applyContextIdempotencyKey(plan, base, firstContext);
        RequestArgumentResolver.ResolvedArgs secondResolved =
                ReactiveClientInvocationHandler.applyContextIdempotencyKey(plan, base, secondContext);

        CacheKeyContract.OpaqueKey first = CacheKeyContract.derive(
                IdempotencyVariantClient.class,
                "idempotency",
                plan,
                frozen,
                firstResolved,
                firstContext,
                selection.policy()).key();
        CacheKeyContract.OpaqueKey second = CacheKeyContract.derive(
                IdempotencyVariantClient.class,
                "idempotency",
                plan,
                frozen,
                secondResolved,
                secondContext,
                selection.policy()).key();

        assertThat(firstResolved.headers().get("Idempotency-Key")).containsExactly("idem-a");
        assertThat(secondResolved.headers().get("Idempotency-Key")).containsExactly("idem-b");
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void contextOnlyIdempotencyHeadersAreSelectableAndPartitioned() throws Exception {
        ReactiveHttpClientProperties.ClientConfig config = selectedPolicy();
        Method method = NoArgumentClient.class.getMethod("get");
        RequestPlan plan = plan(NoArgumentClient.class, method);
        EffectiveCachePolicy.Selection selection = EffectiveCachePolicy.validate(
                NoArgumentClient.class, "context-idempotency", plan, config, "GET");
        Object[] frozen = CacheKeyContract.freezeArguments(plan, new Object[0], selection.policy());
        RequestArgumentResolver.ResolvedArgs base = argumentResolver.resolve(plan, frozen);
        Context firstContext = Context.of(RequestContext.IDEMPOTENCY_KEY_CONTEXT_KEY, "idem-a");
        Context secondContext = Context.of(RequestContext.IDEMPOTENCY_KEY_CONTEXT_KEY, "idem-b");
        RequestArgumentResolver.ResolvedArgs firstResolved =
                ReactiveClientInvocationHandler.applyCacheKeyIdempotencyKey(plan, base, firstContext);
        RequestArgumentResolver.ResolvedArgs secondResolved =
                ReactiveClientInvocationHandler.applyCacheKeyIdempotencyKey(plan, base, secondContext);

        CacheKeyContract.OpaqueKey first = CacheKeyContract.derive(
                NoArgumentClient.class, "context-idempotency", plan, frozen,
                firstResolved, firstContext, selection.policy()).key();
        CacheKeyContract.OpaqueKey second = CacheKeyContract.derive(
                NoArgumentClient.class, "context-idempotency", plan, frozen,
                secondResolved, secondContext, selection.policy()).key();

        assertThat(firstResolved.headers().get("Idempotency-Key")).containsExactly("idem-a");
        assertThat(secondResolved.headers().get("Idempotency-Key")).containsExactly("idem-b");
        assertThat(first).isNotEqualTo(second);

        ReactiveHttpClientProperties.ClientConfig unpartitioned = selectedPolicy();
        policy(unpartitioned).setVaryByHeaders(List.of());
        assertRejected(NoArgumentClient.class, unpartitioned, "dynamic request headers must be selected");

        ReactiveHttpClientProperties.ClientConfig acknowledged = selectedPolicy();
        policy(acknowledged).setVaryByHeaders(List.of());
        policy(acknowledged).setSharedResponse(true);
        assertThatCode(() -> validate(NoArgumentClient.class, acknowledged)).doesNotThrowAnyException();
    }

    @Test
    void freezingEnforcesOneCumulativeElementBudgetAcrossNestedContainers() throws Exception {
        ReactiveHttpClientProperties.ClientConfig config = selectedPolicy();
        policy(config).setVaryByParameters(List.of("nested"));
        Method method = NestedListClient.class.getMethod("get", List.class);
        RequestPlan plan = plan(NestedListClient.class, method);
        EffectiveCachePolicy.Selection selection = EffectiveCachePolicy.validate(
                NestedListClient.class, "nested", plan, config, "GET");
        List<String> sharedChild = Collections.nCopies(10_000, "value");
        List<List<String>> nested = Collections.nCopies(10_000, sharedChild);

        assertThatThrownBy(() -> CacheKeyContract.freezeArguments(
                plan, new Object[]{nested}, selection.policy()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cumulative element count exceeds maximum 10000");
    }

    @Test
    void freezingCountsRecordComponentsAgainstTheCumulativeBudget() throws Exception {
        ReactiveHttpClientProperties.ClientConfig config = selectedPolicy();
        policy(config).setVaryByContext(List.of("tenant"));
        Object shared = "tenant";
        for (int depth = 0; depth < 14; depth++) {
            shared = new Fanout<>(shared, shared);
        }

        Object contextValue = shared;
        assertThatThrownBy(() -> key(
                NoArgumentClient.class,
                "record-budget",
                NoArgumentClient.class.getMethod("get"),
                new Object[0],
                config,
                Context.of("tenant", contextValue)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cumulative element count exceeds maximum 10000");
    }

    @Test
    void startupCountsOneDepthLevelPerNestedRecord() {
        ReactiveHttpClientProperties.ClientConfig config = selectedPolicy();
        policy(config).setVaryByParameters(List.of("value"));

        assertThatCode(() -> validate(DeepRecordClient.class, config))
                .doesNotThrowAnyException();
    }

    @Test
    void startupAllowsDirectQueryArraysAndRejectsUnstableRequestTargetArrays() throws Exception {
        ReactiveHttpClientProperties.ClientConfig config = selectedPolicy();
        assertThatCode(() -> validate(ArrayQueryClient.class, config))
                .doesNotThrowAnyException();
        Method query = ArrayQueryClient.class.getMethod("get", String[].class);
        assertThat(key(ArrayQueryClient.class, "array-query", query,
                new Object[]{new String[]{"z", "a"}}, config))
                .isEqualTo(key(ArrayQueryClient.class, "array-query", query,
                        new Object[]{new String[]{"z", "a"}}, config))
                .isNotEqualTo(key(ArrayQueryClient.class, "array-query", query,
                        new Object[]{new String[]{"a", "z"}}, config));

        assertRejected(ArrayPathClient.class, selectedPolicy(),
                "array-valued path parameters cannot preserve a stable String.valueOf wire projection");
        assertRejected(NestedArrayQueryClient.class, selectedPolicy(),
                "arrays nested inside query parameter values cannot preserve a stable String.valueOf wire projection");
    }

    @Test
    void canonicalEncodingEnforcesTheByteBudgetWhileWritingNestedFrames() throws Exception {
        ReactiveHttpClientProperties.ClientConfig config = selectedPolicy();
        policy(config).setVaryByParameters(List.of("values"));
        Method method = LargeScalarClient.class.getMethod("get", List.class);
        String shared = "x".repeat(256 * 1024);
        List<String> values = Collections.nCopies(10_000, shared);

        assertThatThrownBy(() -> key(
                LargeScalarClient.class,
                "large-scalar",
                method,
                new Object[]{values},
                config))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cache key material exceeds 1048576 bytes");
    }

    @Test
    void canonicalEncodingPreflightsOversizedUtf8Scalars() throws Exception {
        ReactiveHttpClientProperties.ClientConfig config = selectedPolicy();
        policy(config).setVaryByParameters(List.of("value"));

        assertThatThrownBy(() -> key(
                StringScalarClient.class,
                "large-string",
                StringScalarClient.class.getMethod("get", String.class),
                new Object[]{"\u00e9".repeat(600_000)},
                config))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cache key material exceeds 1048576 bytes");
    }

    @Test
    void canonicalEncodingPreflightsOversizedNumericMagnitudes() throws Exception {
        ReactiveHttpClientProperties.ClientConfig config = selectedPolicy();
        policy(config).setVaryByParameters(List.of("value"));
        BigInteger oversized = BigInteger.ONE.shiftLeft((1024 * 1024 + 1) * Byte.SIZE);

        assertThatThrownBy(() -> key(
                BigIntegerClient.class,
                "large-integer",
                BigIntegerClient.class.getMethod("get", BigInteger.class),
                new Object[]{oversized},
                config))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cache key material exceeds 1048576 bytes");
        assertThatThrownBy(() -> key(
                BigDecimalClient.class,
                "large-decimal",
                BigDecimalClient.class.getMethod("get", BigDecimal.class),
                new Object[]{new BigDecimal(oversized, 2)},
                config))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cache key material exceeds 1048576 bytes");
    }

    @Test
    void canonicalEncodingPreflightsOversizedUriText() throws Exception {
        ReactiveHttpClientProperties.ClientConfig config = selectedPolicy();
        policy(config).setVaryByParameters(List.of("uri"));
        URI oversized = URI.create("https://cache-key.example.invalid/" + "x".repeat(1024 * 1024));

        assertThatThrownBy(() -> key(
                UriVariantClient.class,
                "large-uri",
                UriVariantClient.class.getMethod("get", URI.class),
                new Object[]{oversized},
                config))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cache key material exceeds 1048576 bytes");
    }

    @Test
    void startupResolvesConcreteGenericRecordComponents() throws Exception {
        ReactiveHttpClientProperties.ClientConfig config = selectedPolicy();
        policy(config).setVaryByParameters(List.of("box"));

        assertThatCode(() -> validate(GenericRecordClient.class, config)).doesNotThrowAnyException();
        assertThatCode(() -> key(
                GenericRecordClient.class,
                "generic-record",
                GenericRecordClient.class.getMethod("get", Box.class),
                new Object[]{new Box<>("tenant-a")},
                config)).doesNotThrowAnyException();
    }

    @Test
    void generatedIdempotencyHeadersRequireAndSupportExplicitPartitioning() throws Exception {
        ReactiveHttpClientProperties.ClientConfig unpartitioned = selectedPolicy();
        policy(unpartitioned).setVaryByHeaders(List.of());
        assertRejected(GeneratedIdempotencyClient.class, unpartitioned, "dynamic request headers must be selected");

        ReactiveHttpClientProperties.ClientConfig partitioned = selectedPolicy();
        policy(partitioned).setVaryByHeaders(List.of("Idempotency-Key"));
        validate(GeneratedIdempotencyClient.class, partitioned);
        Method method = GeneratedIdempotencyClient.class.getMethod("get");
        RequestPlan plan = plan(GeneratedIdempotencyClient.class, method);
        EffectiveCachePolicy.Selection selection = EffectiveCachePolicy.validate(
                GeneratedIdempotencyClient.class, "generated", plan, partitioned, "GET");
        Object[] frozen = CacheKeyContract.freezeArguments(plan, new Object[0], selection.policy());
        RequestArgumentResolver.ResolvedArgs base = argumentResolver.resolve(plan, frozen);
        RequestArgumentResolver.ResolvedArgs firstResolved =
                ReactiveClientInvocationHandler.applyCacheKeyIdempotencyKey(plan, base, Context.empty());
        RequestArgumentResolver.ResolvedArgs secondResolved =
                ReactiveClientInvocationHandler.applyCacheKeyIdempotencyKey(plan, base, Context.empty());

        CacheKeyContract.OpaqueKey first = CacheKeyContract.derive(
                GeneratedIdempotencyClient.class, "generated", plan, frozen,
                firstResolved, Context.empty(), selection.policy()).key();
        CacheKeyContract.OpaqueKey second = CacheKeyContract.derive(
                GeneratedIdempotencyClient.class, "generated", plan, frozen,
                secondResolved, Context.empty(), selection.policy()).key();

        assertThat(firstResolved.headers().get("Idempotency-Key")).hasSize(1);
        assertThat(secondResolved.headers().get("Idempotency-Key"))
                .isNotEqualTo(firstResolved.headers().get("Idempotency-Key"));
        assertThat(first).isNotEqualTo(second);

        ReactiveHttpClientProperties.ClientConfig acknowledged = selectedPolicy();
        policy(acknowledged).setVaryByHeaders(List.of());
        policy(acknowledged).setSharedResponse(true);
        assertThatCode(() -> validate(GeneratedIdempotencyClient.class, acknowledged))
                .doesNotThrowAnyException();
    }

    @Test
    void uriBoundValuesUseTheSameStringProjectionAsTheWire() throws Exception {
        ReactiveHttpClientProperties.ClientConfig config = selectedPolicy();
        Method method = UriShapeClient.class.getMethod("get", Set.class, Map.class);
        Set<String> firstPath = new LinkedHashSet<>(List.of("z", "a"));
        Set<String> secondPath = new LinkedHashSet<>(List.of("a", "z"));
        Map<String, Integer> firstQuery = new LinkedHashMap<>();
        firstQuery.put("z", 1);
        firstQuery.put("a", 2);
        Map<String, Integer> secondQuery = new LinkedHashMap<>();
        secondQuery.put("a", 2);
        secondQuery.put("z", 1);

        CacheKeyContract.OpaqueKey base = key(
                UriShapeClient.class, "uri-shape", method,
                new Object[]{firstPath, firstQuery}, config);

        assertThat(base).isNotEqualTo(key(
                UriShapeClient.class, "uri-shape", method,
                new Object[]{secondPath, firstQuery}, config));
        assertThat(base).isNotEqualTo(key(
                UriShapeClient.class, "uri-shape", method,
                new Object[]{firstPath, secondQuery}, config));
    }

    @Test
    void requestBoundSelectedSetsPreserveWireOrderInTheKey() throws Exception {
        ReactiveHttpClientProperties.ClientConfig config = selectedPolicy();
        policy(config).setVaryByParameters(List.of("tags"));
        Method method = OrderedHeaderSetClient.class.getMethod("get", Set.class);
        Set<String> first = new LinkedHashSet<>(List.of("z", "a"));
        Set<String> second = new LinkedHashSet<>(List.of("a", "z"));

        assertThat(key(
                OrderedHeaderSetClient.class,
                "ordered-header",
                method,
                new Object[]{first},
                config))
                .isNotEqualTo(key(
                        OrderedHeaderSetClient.class,
                        "ordered-header",
                        method,
                        new Object[]{second},
                        config));
    }

    @Test
    void eachFreezeUsesOneDefensiveSnapshotForKeyAndRequestMaterialization() throws Exception {
        ReactiveHttpClientProperties.ClientConfig config = selectedPolicy();
        Method method = ListQueryClient.class.getMethod("get", List.class);
        RequestPlan plan = plan(ListQueryClient.class, method);
        EffectiveCachePolicy.Selection selection = EffectiveCachePolicy.validate(
                ListQueryClient.class, "list-client", plan, config, "GET");
        List<String> callerValues = new ArrayList<>(List.of("first"));

        Object[] firstSnapshot = CacheKeyContract.freezeArguments(
                plan, new Object[]{callerValues}, selection.policy());
        RequestArgumentResolver.ResolvedArgs firstResolved =
                argumentResolver.resolve(plan, firstSnapshot);
        CacheKeyContract.OpaqueKey firstKey = CacheKeyContract.derive(
                ListQueryClient.class, "list-client", plan, firstSnapshot,
                firstResolved, Context.empty(), selection.policy()).key();

        callerValues.set(0, "second");
        Object[] secondSnapshot = CacheKeyContract.freezeArguments(
                plan, new Object[]{callerValues}, selection.policy());
        RequestArgumentResolver.ResolvedArgs secondResolved =
                argumentResolver.resolve(plan, secondSnapshot);
        CacheKeyContract.OpaqueKey secondKey = CacheKeyContract.derive(
                ListQueryClient.class, "list-client", plan, secondSnapshot,
                secondResolved, Context.empty(), selection.policy()).key();

        assertThat(firstResolved.queryParams().get("tag")).containsExactly("first");
        assertThat(secondResolved.queryParams().get("tag")).containsExactly("second");
        assertThat(firstKey).isNotEqualTo(secondKey);
    }

    @Test
    void invocationHandlerFreezesASeparateSnapshotForEachSubscription() {
        ReactiveHttpClientProperties.ClientConfig config = selectedPolicy();
        List<String> dispatchedQueries = new ArrayList<>();
        WebClient webClient = WebClient.builder()
                .baseUrl("http://cache-key.test")
                .exchangeFunction(request -> {
                    dispatchedQueries.add(request.url().getRawQuery());
                    return Mono.just(ClientResponse.create(HttpStatus.OK)
                            .header(HttpHeaders.CONTENT_TYPE, "text/plain")
                            .body("ok")
                            .build());
                })
                .build();

        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.refresh();
            ReactiveClientInvocationHandler handler = new ReactiveClientInvocationHandler(
                    webClient,
                    metadataCache,
                    argumentResolver,
                    new DefaultErrorDecoder(),
                    config,
                    "list-client",
                    ListQueryClient.class,
                    context,
                    new NoopResilienceOperatorApplier(),
                    TestJsonCodecs.jsonCodec(),
                    new ReactiveHttpClientProperties.ObservabilityConfig());
            ListQueryClient client = (ListQueryClient) Proxy.newProxyInstance(
                    getClass().getClassLoader(), new Class<?>[]{ListQueryClient.class}, handler);
            List<String> callerValues = new ArrayList<>(List.of("first"));
            Mono<String> call = client.get(callerValues);

            callerValues.set(0, "second");
            assertThat(call.block()).isEqualTo("ok");
            callerValues.set(0, "third");
            assertThat(call.block()).isEqualTo("ok");
        }

        assertThat(dispatchedQueries).containsExactly("tag=second", "tag=third");
    }

    private CacheKeyContract.OpaqueKey key(Class<?> client,
                                           String clientName,
                                           Method method,
                                           Object[] arguments,
                                           ReactiveHttpClientProperties.ClientConfig config) {
        return key(client, clientName, method, arguments, config, Context.empty());
    }

    private CacheKeyContract.OpaqueKey key(Class<?> client,
                                           String clientName,
                                           Method method,
                                           Object[] arguments,
                                           ReactiveHttpClientProperties.ClientConfig config,
                                           Context context) {
        RequestPlan plan = plan(client, method);
        EffectiveCachePolicy.Selection selection = EffectiveCachePolicy.validate(
                client, clientName, plan, config, "GET");
        Object[] frozen = CacheKeyContract.freezeArguments(plan, arguments, selection.policy());
        RequestArgumentResolver.ResolvedArgs resolved = argumentResolver.resolve(plan, frozen);
        CacheKeyContract.SerializedBodyKey serializedBody = null;
        if (CacheKeyContract.selectsRequestBody(plan, selection.policy())) {
            Object body = resolved.body();
            try {
                serializedBody = body != null
                        ? CacheKeyContract.serializedBodyKey(TestJsonCodecs.jsonCodec().write(body))
                        : CacheKeyContract.absentSerializedBodyKey();
            } catch (Exception ex) {
                throw new AssertionError("Unable to serialize selected test body", ex);
            }
        }
        return CacheKeyContract.derive(client, clientName, plan, frozen, resolved, context,
                selection.policy(), serializedBody).key();
    }

    private static MockClientHttpRequest materialize(ClientRequest request) {
        MockClientHttpRequest mock = new MockClientHttpRequest(request.method(), request.url());
        request.writeTo(mock, ExchangeStrategies.withDefaults()).block();
        return mock;
    }

    private static ReactiveHttpClientJsonCodec countingCodec(AtomicInteger boundedWrites) {
        ReactiveHttpClientJsonCodec delegate = TestJsonCodecs.jsonCodec();
        return new ReactiveHttpClientJsonCodec() {
            @Override
            public byte[] write(Object value) throws Exception {
                return delegate.write(value);
            }

            @Override
            public byte[] writeBounded(Object value, int maximumBytes) throws Exception {
                boundedWrites.incrementAndGet();
                return delegate.writeBounded(value, maximumBytes);
            }

            @Override
            public <T> T read(byte[] value, Class<T> type) throws Exception {
                return delegate.read(value, type);
            }
        };
    }

    private RequestPlan plan(Class<?> client, Method method) {
        return RequestPlan.from(metadataCache.get(method), client);
    }

    private void validate(Class<?> client, ReactiveHttpClientProperties.ClientConfig config) {
        metadataCache.validateDeclarativeCachePolicies(client, "cache-client", config);
    }

    private void assertRejected(Class<?> client,
                                ReactiveHttpClientProperties.ClientConfig config,
                                String reason) {
        assertThatThrownBy(() -> validate(client, config))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(client.getName())
                .hasMessageContaining(reason);
    }

    private static ReactiveHttpClientProperties.ClientConfig selectedPolicy() {
        ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();
        ReactiveHttpClientProperties.CachePolicyConfig policy =
                new ReactiveHttpClientProperties.CachePolicyConfig();
        policy.setTtlMs(1_000L);
        policy.setMaximumSize(100L);
        policy.setVaryByHeaders(List.of("Idempotency-Key"));
        config.getCache().setPolicy("selected");
        config.getCache().getPolicies().put("selected", policy);
        return config;
    }

    private static ReactiveHttpClientProperties.CachePolicyConfig policy(
            ReactiveHttpClientProperties.ClientConfig config) {
        return config.getCache().getPolicies().get("selected");
    }

    record Identity(String value, int version) {
    }

    record UnstableRecord(String token) {
        private static final AtomicInteger READS = new AtomicInteger();

        @Override
        public String token() {
            int read = READS.incrementAndGet();
            return read <= 2 ? token : token + "-" + read;
        }
    }

    record ConstructorSideEffectRecord(String value) {
        private static final AtomicInteger CONSTRUCTIONS = new AtomicInteger();

        ConstructorSideEffectRecord {
            CONSTRUCTIONS.incrementAndGet();
        }
    }

    record Slug(String value) {
        @Override
        public String toString() {
            return value;
        }
    }

    record JsonValueBody(String value) {
        @JsonValue
        String wireValue() {
            return "wire-" + value;
        }
    }

    record LargeDefaultRecord(String left, String right) {
    }

    record LargeJsonBody(String value) {
    }

    enum CustomStringMode {
        VALUE;

        private static final AtomicInteger TO_STRING_CALLS = new AtomicInteger();

        @Override
        public String toString() {
            TO_STRING_CALLS.incrementAndGet();
            return "custom";
        }
    }

    static final class PipeDelimitedList extends ArrayList<String> {
        PipeDelimitedList(String... values) {
            super(List.of(values));
        }

        @Override
        public String toString() {
            return String.join("|", this);
        }
    }

    static final class CustomBodySet extends LinkedHashSet<String> {
        CustomBodySet(String... values) {
            super(List.of(values));
        }
    }

    static final class CustomBodyMap extends LinkedHashMap<String, String> {
        CustomBodyMap(String key, String value) {
            put(key, value);
        }
    }

    record MutableRecord(List<String> values) {
    }

    record Box<T>(T value) {
    }

    record Fanout<T>(T left, T right) {
    }

    record Depth01(Depth02 value) {
    }

    record Depth02(Depth03 value) {
    }

    record Depth03(Depth04 value) {
    }

    record Depth04(Depth05 value) {
    }

    record Depth05(Depth06 value) {
    }

    record Depth06(Depth07 value) {
    }

    record Depth07(Depth08 value) {
    }

    record Depth08(Depth09 value) {
    }

    record Depth09(Depth10 value) {
    }

    record Depth10(Depth11 value) {
    }

    record Depth11(Depth12 value) {
    }

    record Depth12(Depth13 value) {
    }

    record Depth13(Depth14 value) {
    }

    record Depth14(Depth15 value) {
    }

    record Depth15(Depth16 value) {
    }

    record Depth16(Depth17 value) {
    }

    record Depth17(String value) {
    }

    enum Mode {
        SPECIAL {
        }
    }

    static final class IteratorOnlyList<E> extends AbstractList<E> {
        private final List<E> values;

        IteratorOnlyList(List<E> values) {
            this.values = values;
        }

        @Override
        public E get(int index) {
            throw new AssertionError("cache-key freezing must not use indexed access");
        }

        @Override
        public int size() {
            return values.size();
        }

        @Override
        public Iterator<E> iterator() {
            return values.iterator();
        }
    }

    static final class UnderreportedList<E> extends AbstractList<E> {
        private final List<E> values;

        UnderreportedList(List<E> values) {
            this.values = values;
        }

        @Override
        public E get(int index) {
            return values.get(index);
        }

        @Override
        public int size() {
            return 1;
        }

        @Override
        public Iterator<E> iterator() {
            return values.iterator();
        }
    }

    static final class MutableInput {
        private String value;
    }

    interface KeyClient {
        @GET("/items/{left}")
        Mono<String> get(
                @PathVar("left") String left,
                @QueryParam("right") String right,
                @QueryParam("tail") String tail,
                @CacheKey("attributes") Map<String, List<Integer>> attributes);
    }

    interface OrderedBodyMapClient {
        @GET("/items")
        Mono<String> get(@Body @CacheKey("body") Map<String, String> body);
    }

    interface IdentityMapClient {
        @GET("/items")
        Mono<String> get(@Body @CacheKey("entries") Map<Identity, String> entries);
    }

    interface LargePathClient {
        @GET("/items/{values}")
        Mono<String> get(@PathVar("values") List<String> values);
    }

    interface UnstableRecordClient {
        @GET("/items")
        Mono<String> get(@Body @CacheKey("record") UnstableRecord record);
    }

    interface ConstructorSideEffectClient {
        @GET("/items")
        Mono<String> get(@CacheKey("record") ConstructorSideEffectRecord record);
    }

    interface SlugPathClient {
        @GET("/items/{slug}")
        Mono<String> get(@PathVar("slug") Slug slug);
    }

    interface JsonValueBodyClient {
        @GET("/items")
        Mono<String> get(@Body @CacheKey("body") JsonValueBody body);
    }

    interface SemanticBodyClient {
        @POST("/items")
        @CacheResponse(value = "selected", semanticRead = true)
        Mono<String> query(
                @Body @CacheKey("body") String body,
                @HeaderParam("Content-Type") String contentType);
    }

    interface ByteArrayBodyClient {
        @POST("/items")
        @CacheResponse(value = "selected", semanticRead = true)
        Mono<String> query(
                @Body @CacheKey("body") byte[] body,
                @HeaderParam("Content-Type") String contentType);
    }

    interface UnselectedStringBodyClient {
        @GET("/items")
        Mono<String> get(@Body String body);
    }

    interface BodilessCacheClient {
        @GET("/items")
        Mono<String> get();
    }

    interface StringBodyClient {
        @GET("/items")
        Mono<String> get(@Body @CacheKey("body") String body);
    }

    interface CacheOnlyParameterClient {
        @GET("/items")
        Mono<String> get(@CacheKey("tenant") String tenant);
    }

    interface RequestBoundCacheKeyClient {
        @GET("/items")
        Mono<String> get(@QueryParam("tenant") @CacheKey("tenant") String tenant);
    }

    interface ConcreteContainerArrayClient {
        @GET("/items")
        Mono<String> get(@QueryParam("value") @CacheKey("values") ArrayList<String>[] values);
    }

    interface InterfaceContainerArrayClient {
        @GET("/items")
        Mono<String> get(@QueryParam("value") @CacheKey("values") List<String>[] values);
    }

    interface UriVariantClient {
        @GET("/items")
        Mono<String> get(@CacheKey("uri") URI uri);
    }

    interface BigIntegerClient {
        @GET("/items")
        Mono<String> get(@CacheKey("value") BigInteger value);
    }

    interface BigDecimalClient {
        @GET("/items")
        Mono<String> get(@CacheKey("value") BigDecimal value);
    }

    interface VariantClient {
        @GET("/items/{id}")
        Mono<String> get(
                @PathVar("id") String id,
                @HeaderParam("X-Tenant") String tenant,
                @CacheKey("attributes") Map<String, List<Integer>> attributes);
    }

    interface OtherVariantClient {
        @GET("/items/{id}")
        Mono<String> get(
                @PathVar("id") String id,
                @HeaderParam("X-Tenant") String tenant,
                @CacheKey("attributes") Map<String, List<Integer>> attributes);
    }

    interface Parent<T> {
        @GET("/items/{id}")
        Mono<String> get(@PathVar("id") T id);
    }

    interface StringChild extends Parent<String> {
    }

    interface LongChild extends Parent<Long> {
    }

    interface OverloadedClient {
        @GET("/items/string/{id}")
        Mono<String> get(@PathVar("id") String id);

        @GET("/items/long/{id}")
        Mono<String> get(@PathVar("id") long id);
    }

    interface NoArgumentClient {
        @GET("/items")
        Mono<String> get();
    }

    interface SupportedInputClient {
        @GET("/items/{id}")
        Mono<String> get(
                @CacheKey("record") Identity identity,
                @CacheKey("array") int[] array,
                @CacheKey("list") List<String> list,
                @CacheKey("map") Map<String, List<Integer>> map,
                @PathVar("id") String id);
    }

    interface MutableInputClient {
        @GET("/items/{input}")
        Mono<String> get(@PathVar("input") MutableInput input);
    }

    interface RawMapClient {
        @GET("/items")
        Mono<String> get(@CacheKey("map") Map map);
    }

    interface StreamKeyClient {
        @GET("/items")
        Mono<String> get(@Body @CacheKey("stream") InputStream stream);
    }

    interface ListQueryClient {
        @GET("/items")
        Mono<String> get(@QueryParam("tag") List<String> tags);
    }

    interface OrderedInputClient {
        @GET("/items")
        Mono<String> get(
                @CacheKey("mode") Mode mode,
                @CacheKey("values") List<String> values,
                @QueryParam("tag") Set<String> tags);
    }

    interface HeaderCaseClient {
        @GET("/items")
        Mono<String> get(@HeaderParam("X-Tenant") String tenant);
    }

    interface IdempotencyVariantClient {
        @GET("/items")
        Mono<String> get(@IdempotencyKey String idempotencyKey);
    }

    interface NestedListClient {
        @GET("/items")
        Mono<String> get(@CacheKey("nested") List<List<String>> nested);
    }

    interface LargeScalarClient {
        @GET("/items")
        Mono<String> get(@CacheKey("values") List<String> values);
    }

    interface StringScalarClient {
        @GET("/items")
        Mono<String> get(@CacheKey("value") String value);
    }

    interface DeepRecordClient {
        @GET("/items")
        Mono<String> get(@CacheKey("value") Depth01 value);
    }

    interface ArrayPathClient {
        @GET("/items/{id}")
        Mono<String> get(@PathVar("id") String[] id);
    }

    interface ArrayQueryClient {
        @GET("/items")
        Mono<String> get(@QueryParam("tag") String[] tags);
    }

    interface NestedArrayQueryClient {
        @GET("/items")
        Mono<String> get(@QueryParam("tag") List<String[]> tags);
    }

    interface GenericRecordClient {
        @GET("/items")
        Mono<String> get(@CacheKey("box") Box<String> box);
    }

    interface GeneratedIdempotencyClient {
        @GET("/items")
        @IdempotencyKey
        Mono<String> get();
    }

    interface UriShapeClient {
        @GET("/items/{path}")
        Mono<String> get(
                @PathVar("path") Set<String> path,
                @QueryParam("filters") Map<String, Integer> filters);
    }

    interface OrderedHeaderSetClient {
        @GET("/items")
        Mono<String> get(@HeaderParam("X-Tag") @CacheKey("tags") Set<String> tags);
    }

    interface CustomListBodyClient {
        @GET("/items")
        Mono<String> get(@Body @CacheKey("body") List<String> body);
    }

    interface CustomSetBodyClient {
        @GET("/items")
        Mono<String> get(@Body @CacheKey("body") Set<String> body);
    }

    interface CustomMapBodyClient {
        @GET("/items")
        Mono<String> get(@Body @CacheKey("body") Map<String, String> body);
    }

    interface SharedUnselectedRequestClient {
        @GET("/items")
        Mono<String> get(
                @HeaderParam("X-Metadata") MutableInput metadata,
                @Body MutableInput body);
    }

    interface LargeHeaderClient {
        @GET("/items")
        Mono<String> get(@HeaderParam("X-Metadata") List<List<String>> metadata);
    }

    interface EnumPathClient {
        @GET("/items/{mode}")
        Mono<String> get(@PathVar("mode") CustomStringMode mode);
    }

    interface LargeJsonBodyClient {
        @GET("/items")
        Mono<String> get(@Body @CacheKey("body") LargeJsonBody body);
    }

    interface SensitiveHeaderClient {
        @GET("/items")
        Mono<String> get(
                @HeaderParam("Authorization") String authorization,
                @HeaderParam("Cookie") String cookie);
    }

    interface InvalidCacheKeyClient {
        @GET("/blank")
        Mono<String> blank(@CacheKey(" ") String value);

        @GET("/duplicate")
        Mono<String> duplicate(
                @CacheKey("tenant") String first,
                @CacheKey("tenant") String second);
    }
}
