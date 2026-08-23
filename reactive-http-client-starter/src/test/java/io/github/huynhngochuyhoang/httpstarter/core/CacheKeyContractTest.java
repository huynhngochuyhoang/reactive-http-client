package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.annotation.*;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import io.github.huynhngochuyhoang.httpstarter.core.fixture.cache.NonPublicRecordFixture;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.*;

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
    void startupRejectsArraysInRequestTargetValues() {
        assertRejected(ArrayPathClient.class, selectedPolicy(),
                "array-valued path/query parameters cannot preserve a stable String.valueOf wire projection");
        assertRejected(NestedArrayQueryClient.class, selectedPolicy(),
                "array-valued path/query parameters cannot preserve a stable String.valueOf wire projection");
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
        return CacheKeyContract.derive(
                client, clientName, plan, frozen, resolved, context, selection.policy()).key();
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
