package io.github.huynhngochuyhoang.httpstarter.core;

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
        String redirectPolicy,
        String authMode,
        RequestBodyRepeatability bodyRepeatability
) {

    record TimeoutPolicy(String source, long timeoutMs) {
    }

    record ResiliencePolicy(String retry, String rateLimiter, String circuitBreaker, String bulkhead) {
    }
}
