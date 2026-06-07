package io.github.huynhngochuyhoang.httpstarter.core;

record EffectiveHttpClientContract(
        String clientName,
        String concreteClientInterface,
        String declaringInterface,
        boolean inherited,
        String javaMethodSignature,
        String httpMethod,
        String pathTemplate,
        String apiName,
        String apiRef,
        TimeoutPolicy timeout,
        ResiliencePolicy resilience,
        String redirectPolicy,
        RequestBodyRepeatability bodyRepeatability
) {

    record TimeoutPolicy(String source, long timeoutMs) {
    }

    record ResiliencePolicy(String retry, String rateLimiter, String circuitBreaker, String bulkhead) {
    }
}
