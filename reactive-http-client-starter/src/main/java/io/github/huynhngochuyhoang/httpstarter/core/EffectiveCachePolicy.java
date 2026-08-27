package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import org.reactivestreams.Publisher;
import org.springframework.core.io.Resource;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.ResponseEntity;

import java.lang.reflect.*;
import java.util.*;

/** Internal, startup-only resolution of an explicitly selected response-cache policy. */
final class EffectiveCachePolicy {

    static final long MAX_TTL_MS = 365L * 24 * 60 * 60 * 1000;
    static final long MAXIMUM_SIZE = 1_000_000L;
    static final int MAX_NON_CACHEABLE_RESPONSE_HEADERS = 32;

    private EffectiveCachePolicy() {
    }

    static Selection validate(Class<?> clientInterface,
                              String clientName,
                              RequestPlan plan,
                              ReactiveHttpClientProperties.ClientConfig clientConfig,
                              String effectiveHttpMethod) {
        Selection selection = resolve(plan, clientConfig);
        if (!selection.enabled()) {
            DeclarativeRequestParameterGrammar.validateCacheKeyActivity(
                    clientInterface, clientName, plan, selection);
            return selection;
        }

        Method method = plan.method();
        String context = context(clientInterface, clientName, plan, selection, effectiveHttpMethod);
        ReactiveHttpClientProperties.CachePolicyConfig policy = selection.policy();
        if (policy == null) {
            throw invalid(context, "the selected policy is not declared under cache.policies");
        }
        requireBound(context, "ttl-ms", policy.getTtlMs(), MAX_TTL_MS);
        requireBound(context, "maximum-size", policy.getMaximumSize(), MAXIMUM_SIZE);
        validateRefreshBounds(context, policy);
        normalizedNonCacheableResponseHeaders(context, policy);

        if (!hasText(effectiveHttpMethod)) {
            throw invalid(context, "selected cache methods require a resolved HTTP method; configure the "
                    + "referenced API method before applying semantic-read intent");
        }
        String normalizedHttpMethod = effectiveHttpMethod.trim().toUpperCase(Locale.ROOT);
        if (!ReactiveHttpClientFactoryBean.isSupportedOutboundHttpMethod(normalizedHttpMethod)) {
            throw invalid(context, "resolved HTTP method '" + effectiveHttpMethod
                    + "' is unsupported and cannot be acknowledged as a semantic read");
        }
        boolean semanticNonGet = !"GET".equals(normalizedHttpMethod);
        if (semanticNonGet && !plan.cacheSemanticRead()) {
            throw invalid(context, "selected non-GET methods require a method-specific semantic-read "
                    + "acknowledgement; add @CacheResponse(value = \"" + selection.policyName()
                    + "\", semanticRead = true) only when omitting downstream dispatch cannot omit a required "
                    + "side effect");
        }
        if (plan.returnsFlux()) {
            throw invalid(context, "Flux responses are streaming and cannot be cached");
        }

        Type responseType = plan.responseType();
        if (responseType == null) {
            throw invalid(context, "raw Mono responses have no finite materialized response type");
        }
        if (containsUnresolvedType(responseType)) {
            throw invalid(context, "the response type is unresolved: " + responseType.getTypeName());
        }

        Type cacheValueType = responseType;
        if (ResponseEntity.class.equals(rawClass(responseType))) {
            cacheValueType = DeclarativeReturnTypeGrammar.responseEntityBodyType(responseType);
            if (cacheValueType == null) {
                throw invalid(context, "ResponseEntity must declare a finite materialized body type");
            }
        }
        validateMaterializedType(context, cacheValueType);
        validateRequestBody(context, plan);
        if (semanticNonGet && plan.bodyIndex() >= 0
                && !CacheKeyContract.selectsRequestBody(plan, policy)) {
            throw invalid(context, "body-bearing semantic non-GET methods require the @Body parameter to "
                    + "declare @CacheKey and the policy to select that label through vary-by-parameters; "
                    + "shared-response cannot waive wire-byte body identity");
        }
        CacheKeyContract.validate(clientInterface, clientName, plan, clientConfig, selection);
        DeclarativeRequestParameterGrammar.validateCacheKeyActivity(
                clientInterface, clientName, plan, selection);
        return selection;
    }

    static Selection resolve(RequestPlan plan,
                             ReactiveHttpClientProperties.ClientConfig clientConfig) {
        ReactiveHttpClientProperties.CacheConfig cache = clientConfig != null && clientConfig.getCache() != null
                ? clientConfig.getCache()
                : new ReactiveHttpClientProperties.CacheConfig();
        if (plan.cacheDisabled()) {
            return Selection.disabled(Source.METHOD_DISABLED);
        }
        if (hasText(plan.cachePolicyName())) {
            return selected(plan.cachePolicyName().trim(), Source.METHOD, cache.getPolicies());
        }
        if (hasText(cache.getPolicy())) {
            return selected(cache.getPolicy().trim(), Source.CLIENT, cache.getPolicies());
        }
        return Selection.disabled(Source.DISABLED);
    }

    static String effectiveHttpMethod(RequestPlan plan,
                                      ReactiveHttpClientProperties.ClientConfig clientConfig) {
        if (plan.apiRefName() == null) {
            return plan.httpMethod();
        }
        ReactiveHttpClientProperties.ApiConfig api = clientConfig != null && clientConfig.getApis() != null
                ? clientConfig.getApis().get(plan.apiRefName())
                : null;
        return api != null ? api.getMethod() : null;
    }

    private static Selection selected(String policyName,
                                      Source source,
                                      Map<String, ReactiveHttpClientProperties.CachePolicyConfig> policies) {
        ReactiveHttpClientProperties.CachePolicyConfig policy = policies != null ? policies.get(policyName) : null;
        return new Selection(true, source, policyName, policy);
    }

    private static void requireBound(String context, String property, Long value, long maximum) {
        if (value == null) {
            throw invalid(context, property + " is required");
        }
        if (value <= 0) {
            throw invalid(context, property + " must be > 0 but was " + value);
        }
        if (value > maximum) {
            throw invalid(context, property + " must be <= " + maximum + " but was " + value);
        }
    }

    private static void validateRefreshBounds(
            String context, ReactiveHttpClientProperties.CachePolicyConfig policy) {
        Long refreshAfterMs = policy.getRefreshAfterMs();
        Long refreshTimeoutMs = policy.getRefreshTimeoutMs();
        if (refreshAfterMs == null && refreshTimeoutMs == null) {
            return;
        }
        if (refreshAfterMs == null) {
            throw invalid(context, "refresh-after-ms is required when refresh-timeout-ms is configured");
        }
        if (refreshTimeoutMs == null) {
            throw invalid(context, "refresh-timeout-ms is required when refresh-after-ms is configured");
        }
        requireBound(context, "refresh-after-ms", refreshAfterMs, MAX_TTL_MS);
        requireBound(context, "refresh-timeout-ms", refreshTimeoutMs, MAX_TTL_MS);
        if (refreshAfterMs >= policy.getTtlMs()) {
            throw invalid(context, "refresh-after-ms must be strictly less than ttl-ms but was "
                    + refreshAfterMs + " with ttl-ms " + policy.getTtlMs());
        }
    }

    static List<String> normalizedNonCacheableResponseHeaders(
            ReactiveHttpClientProperties.CachePolicyConfig policy) {
        return normalizedNonCacheableResponseHeaders("Response-cache policy", policy);
    }

    private static List<String> normalizedNonCacheableResponseHeaders(
            String context, ReactiveHttpClientProperties.CachePolicyConfig policy) {
        if (policy == null || policy.getNonCacheableResponseHeaders() == null) {
            return List.of();
        }
        if (policy.getNonCacheableResponseHeaders().size() > MAX_NON_CACHEABLE_RESPONSE_HEADERS) {
            throw invalid(context, "non-cacheable-response-headers must contain at most "
                    + MAX_NON_CACHEABLE_RESPONSE_HEADERS + " names");
        }
        TreeSet<String> normalized = new TreeSet<>();
        for (String headerName : policy.getNonCacheableResponseHeaders()) {
            String name = headerName != null ? headerName.trim() : "";
            if (!isHeaderName(name)) {
                throw invalid(context, "non-cacheable-response-headers contains an invalid header name: "
                        + String.valueOf(headerName));
            }
            normalized.add(name.toLowerCase(Locale.ROOT));
        }
        return List.copyOf(normalized);
    }

    private static boolean isHeaderName(String value) {
        if (value.isEmpty() || value.length() > 128) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character <= 32 || character >= 127
                    || "()<>@,;:\\\"/[]?={} \t".indexOf(character) >= 0) {
                return false;
            }
        }
        return true;
    }

    private static void validateMaterializedType(String context, Type type) {
        Class<?> rawType = rawClass(type);
        if (Void.class.equals(rawType) || void.class.equals(rawType)) {
            throw invalid(context, "Mono<Void> and bodiless ResponseEntity<Void> responses cannot be cached");
        }
        if (Object.class.equals(rawType)) {
            throw invalid(context, "Object does not prove a finite cache-safe response shape");
        }
        if (containsAssignableType(type, Publisher.class)) {
            throw invalid(context, "Publisher and streaming response values cannot be cached");
        }
        if (containsAssignableType(type, DataBuffer.class)) {
            throw invalid(context, "DataBuffer response values cannot be retained in the cache");
        }
        if (containsAssignableType(type, Resource.class)) {
            throw invalid(context, "Resource response values are application-owned and cannot be cached");
        }
    }

    private static void validateRequestBody(String context, RequestPlan plan) {
        if (plan.multipart()) {
            throw invalid(context, "multipart requests are not cache-eligible");
        }
        Type bodyType = plan.bodyType();
        if (bodyType != null && containsUnresolvedType(bodyType)) {
            throw invalid(context, "the request body type is unresolved: " + bodyType.getTypeName());
        }
        Class<?> rawBodyType = rawClass(bodyType);
        if (rawBodyType == null) {
            return;
        }
        if (Publisher.class.isAssignableFrom(rawBodyType)
                || DataBuffer.class.isAssignableFrom(rawBodyType)
                || Resource.class.isAssignableFrom(rawBodyType)
                || RequestPlan.isApplicationOwnedStreamBody(rawBodyType)) {
            throw invalid(context, "streaming or application-owned request bodies are not cache-eligible: "
                    + bodyType.getTypeName());
        }
    }

    private static boolean containsUnresolvedType(Type type) {
        if (type instanceof TypeVariable<?> || type instanceof WildcardType) {
            return true;
        }
        if (type instanceof Class<?> clazz) {
            return clazz.isArray()
                    ? containsUnresolvedType(clazz.getComponentType())
                    : clazz.getTypeParameters().length > 0;
        }
        if (type instanceof ParameterizedType parameterizedType) {
            return containsUnresolvedOwner(parameterizedType.getOwnerType())
                    || Arrays.stream(parameterizedType.getActualTypeArguments())
                    .anyMatch(EffectiveCachePolicy::containsUnresolvedType);
        }
        if (type instanceof GenericArrayType arrayType) {
            return containsUnresolvedType(arrayType.getGenericComponentType());
        }
        return false;
    }

    private static boolean containsUnresolvedOwner(Type ownerType) {
        if (!(ownerType instanceof ParameterizedType parameterizedOwner)) {
            return false;
        }
        return containsUnresolvedOwner(parameterizedOwner.getOwnerType())
                || Arrays.stream(parameterizedOwner.getActualTypeArguments())
                .anyMatch(EffectiveCachePolicy::containsUnresolvedType);
    }

    private static boolean containsAssignableType(Type type, Class<?> target) {
        if (type == null) {
            return false;
        }
        Class<?> rawType = rawClass(type);
        if (rawType != null && target.isAssignableFrom(rawType)) {
            return true;
        }
        if (type instanceof Class<?> clazz && clazz.isArray()) {
            return containsAssignableType(clazz.getComponentType(), target);
        }
        if (type instanceof ParameterizedType parameterizedType) {
            return containsAssignableOwnerArgument(parameterizedType.getOwnerType(), target)
                    || Arrays.stream(parameterizedType.getActualTypeArguments())
                    .anyMatch(argument -> containsAssignableType(argument, target));
        }
        if (type instanceof GenericArrayType arrayType) {
            return containsAssignableType(arrayType.getGenericComponentType(), target);
        }
        return false;
    }

    private static boolean containsAssignableOwnerArgument(Type ownerType, Class<?> target) {
        if (!(ownerType instanceof ParameterizedType parameterizedOwner)) {
            return false;
        }
        return containsAssignableOwnerArgument(parameterizedOwner.getOwnerType(), target)
                || Arrays.stream(parameterizedOwner.getActualTypeArguments())
                .anyMatch(argument -> containsAssignableType(argument, target));
    }

    private static Class<?> rawClass(Type type) {
        if (type instanceof Class<?> clazz) {
            return clazz;
        }
        if (type instanceof ParameterizedType parameterizedType
                && parameterizedType.getRawType() instanceof Class<?> clazz) {
            return clazz;
        }
        return null;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String context(Class<?> clientInterface,
                                  String clientName,
                                  RequestPlan plan,
                                  Selection selection,
                                  String effectiveHttpMethod) {
        Method method = plan.method();
        return "Reactive HTTP client '" + clientName + "' concreteClient=" + clientInterface.getName()
                + " method=" + method.toGenericString()
                + " resolvedHttpMethod=" + String.valueOf(effectiveHttpMethod)
                + " cachePolicy='" + selection.policyName()
                + "' source=" + selection.source().value
                + (plan.apiRefName() != null ? " apiRef='" + plan.apiRefName() + "'" : "");
    }

    private static IllegalStateException invalid(String context, String reason) {
        return new IllegalStateException(context + " is not cache-eligible: " + reason);
    }

    enum Source {
        DISABLED("disabled"),
        CLIENT("client"),
        METHOD("method"),
        METHOD_DISABLED("method-disabled");

        private final String value;

        Source(String value) {
            this.value = value;
        }

        String value() {
            return value;
        }
    }

    record Selection(boolean enabled,
                     Source source,
                     String policyName,
                     ReactiveHttpClientProperties.CachePolicyConfig policy) {

        static Selection disabled(Source source) {
            return new Selection(false, source, null, null);
        }
    }
}
