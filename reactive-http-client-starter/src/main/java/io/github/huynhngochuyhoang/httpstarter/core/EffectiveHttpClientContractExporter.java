package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

final class EffectiveHttpClientContractExporter {

    private EffectiveHttpClientContractExporter() {
    }

    static List<EffectiveHttpClientContract> export(Class<?> clientInterface,
                                                    String clientName,
                                                    ReactiveHttpClientProperties.ClientConfig clientConfig,
                                                    MethodMetadataCache metadataCache) {
        return Arrays.stream(clientInterface.getMethods())
                .filter(EffectiveHttpClientContractExporter::isDeclarativeClientMethod)
                .map(method -> contract(clientInterface, clientName, clientConfig, metadataCache, method))
                .sorted(Comparator.comparing(EffectiveHttpClientContract::declaringInterface)
                        .thenComparing(EffectiveHttpClientContract::javaMethodSignature))
                .toList();
    }

    private static EffectiveHttpClientContract contract(Class<?> clientInterface,
                                                        String clientName,
                                                        ReactiveHttpClientProperties.ClientConfig clientConfig,
                                                        MethodMetadataCache metadataCache,
                                                        Method method) {
        MethodMetadata meta = metadataCache.get(method);
        RequestPlan plan = meta.getRequestPlan() != null ? meta.getRequestPlan() : RequestPlan.from(meta);
        EffectiveApi effectiveApi = effectiveApi(plan, clientName, clientConfig);
        ReactiveHttpClientFactoryBean.validatePathTemplate(
                effectiveApi.pathTemplate(),
                ReactiveHttpClientFactoryBean.pathVarNames(plan),
                pathTemplateContext(method, plan));
        return new EffectiveHttpClientContract(
                clientName,
                clientInterface.getName(),
                method.getDeclaringClass().getName(),
                method.getDeclaringClass() != clientInterface,
                methodSignature(method),
                effectiveApi.httpMethod(),
                effectiveApi.pathTemplate(),
                plan.apiName(),
                plan.apiRefName(),
                timeoutPolicy(plan, effectiveApi, clientConfig),
                resiliencePolicy(plan, effectiveApi.httpMethod(), clientConfig),
                clientConfig.isFollowRedirects() ? "follow" : "manual",
                plan.bodyRepeatability());
    }

    private static String pathTemplateContext(Method method, RequestPlan plan) {
        if (StringUtils.hasText(plan.apiRefName())) {
            return ApiRefValidationSupport.apiRefContext(method, plan.apiRefName()) + " path template";
        }
        return "Method " + method + " path template";
    }

    private static EffectiveApi effectiveApi(RequestPlan plan,
                                           String clientName,
                                           ReactiveHttpClientProperties.ClientConfig clientConfig) {
        if (!StringUtils.hasText(plan.apiRefName())) {
            return plan.staticEffectiveApi();
        }
        ReactiveHttpClientProperties.ApiConfig apiConfig = clientConfig.getApis() != null
                ? clientConfig.getApis().get(plan.apiRefName())
                : null;
        String configPrefix = ApiRefValidationSupport.configPrefix(clientName, plan.apiRefName());
        String apiRefContext = ApiRefValidationSupport.apiRefContext(plan.method(), plan.apiRefName());
        ReactiveHttpClientFactoryBean.validateApiRef(apiConfig, configPrefix, apiRefContext);
        return new EffectiveApi(
                apiConfig.getMethod().trim().toUpperCase(Locale.ROOT),
                apiConfig.getPath(),
                apiConfig.getTimeoutMs());
    }

    private static EffectiveHttpClientContract.TimeoutPolicy timeoutPolicy(
            RequestPlan plan,
            EffectiveApi effectiveApi,
            ReactiveHttpClientProperties.ClientConfig clientConfig) {
        if (plan.timeoutMs() != MethodMetadata.TIMEOUT_NOT_SET) {
            return new EffectiveHttpClientContract.TimeoutPolicy("method", plan.timeoutMs());
        }
        if (effectiveApi.timeoutMs() != MethodMetadata.TIMEOUT_NOT_SET) {
            return new EffectiveHttpClientContract.TimeoutPolicy("api-ref", effectiveApi.timeoutMs());
        }
        if (clientConfig.isRequestTimeoutMsConfigured()) {
            return new EffectiveHttpClientContract.TimeoutPolicy("client", clientConfig.getRequestTimeoutMs());
        }
        ReactiveHttpClientProperties.ResilienceConfig resilience = clientConfig.getResilience();
        if (resilience != null && resilience.isTimeoutMsConfigured()) {
            return new EffectiveHttpClientContract.TimeoutPolicy(
                    "deprecated-resilience", resilience.getTimeoutMs());
        }
        return new EffectiveHttpClientContract.TimeoutPolicy("disabled", 0);
    }

    private static EffectiveHttpClientContract.ResiliencePolicy resiliencePolicy(
            RequestPlan plan,
            String httpMethod,
            ReactiveHttpClientProperties.ClientConfig clientConfig) {
        ReactiveHttpClientProperties.ResilienceConfig resilience = clientConfig.getResilience();
        if (resilience == null || !resilience.isEnabled()) {
            return new EffectiveHttpClientContract.ResiliencePolicy(
                    "disabled", "disabled", "disabled", "disabled");
        }
        String retry = isRetryableMethod(httpMethod, resilience)
                ? resolve(plan.retryInstanceName(), resilience.getRetry())
                : "disabled";
        return new EffectiveHttpClientContract.ResiliencePolicy(
                retry,
                resolve(plan.rateLimiterInstanceName(), resilience.getRateLimiter()),
                resolve(plan.circuitBreakerInstanceName(), resilience.getCircuitBreaker()),
                resolve(plan.bulkheadInstanceName(), resilience.getBulkhead()));
    }

    private static boolean isRetryableMethod(String httpMethod, ReactiveHttpClientProperties.ResilienceConfig resilience) {
        return httpMethod != null
                && resilience.getRetryMethods() != null
                && resilience.getRetryMethods().contains(httpMethod.toUpperCase(Locale.ROOT));
    }

    private static String resolve(String methodLevel, String clientLevel) {
        return StringUtils.hasText(methodLevel) ? methodLevel : clientLevel;
    }

    private static String methodSignature(Method method) {
        return method.getName() + "("
                + Arrays.stream(method.getGenericParameterTypes())
                .map(EffectiveHttpClientContractExporter::typeName)
                .reduce((left, right) -> left + "," + right)
                .orElse("")
                + ")";
    }

    private static String typeName(Type type) {
        return type.getTypeName();
    }

    private static boolean isDeclarativeClientMethod(Method method) {
        int modifiers = method.getModifiers();
        return method.getDeclaringClass() != Object.class
                && Modifier.isAbstract(modifiers)
                && !Modifier.isStatic(modifiers)
                && !method.isSynthetic()
                && !method.isDefault()
                && !method.isBridge();
    }
}
