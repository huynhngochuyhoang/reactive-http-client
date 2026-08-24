package io.github.huynhngochuyhoang.httpstarter.core;

import java.util.List;

record EffectiveHttpClientContract(
        String clientName,
        String concreteClientInterface,
        String declaringInterface,
        boolean inherited,
        String javaMethodSignature,
        String genericBindings,
        String responseType,
        String bodyType,
        String httpMethod,
        String pathTemplate,
        String baseUrl,
        String baseUrlSource,
        String apiName,
        String apiRef,
        TimeoutPolicy timeout,
        long logicalCallTimeoutMs,
        ResiliencePolicy resilience,
        CachePolicy cache,
        String redirectPolicy,
        String authMode,
        RequestBodyRepeatability bodyRepeatability
) {

    record TimeoutPolicy(String source, long timeoutMs) {
    }

    record ResiliencePolicy(String retry, String rateLimiter, String circuitBreaker, String bulkhead) {
    }

    record CachePolicy(boolean enabled,
                       String source,
                       long ttlMs,
                       long maximumSize,
                       List<String> varyByParameters,
                       List<String> varyByHeaders,
                       List<String> varyByContext,
                       List<String> nonCacheableResponseHeaders,
                       boolean sharedResponse,
                       boolean singleFlight,
                       long refreshAfterMs,
                       long refreshTimeoutMs) {
    }
}
