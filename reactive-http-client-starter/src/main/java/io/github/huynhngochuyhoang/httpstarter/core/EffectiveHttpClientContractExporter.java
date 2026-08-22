package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.annotation.ReactiveHttpClient;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import org.springframework.core.ResolvableType;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

final class EffectiveHttpClientContractExporter {

    private EffectiveHttpClientContractExporter() {
    }

    static List<EffectiveHttpClientContract> export(Class<?> clientInterface,
                                                    String clientName,
                                                    ReactiveHttpClientProperties.ClientConfig clientConfig,
                                                    MethodMetadataCache metadataCache) {
        return export(clientInterface, clientName, clientConfig, metadataCache, null);
    }

    static List<EffectiveHttpClientContract> export(Class<?> clientInterface,
                                                    String clientName,
                                                    ReactiveHttpClientProperties.ClientConfig clientConfig,
                                                    MethodMetadataCache metadataCache,
                                                    ResilienceOperatorApplier resilienceOperatorApplier) {
        return export(clientInterface, clientName, clientConfig, metadataCache, resilienceOperatorApplier, null, true);
    }

    static List<EffectiveHttpClientContract> export(Class<?> clientInterface,
                                                    String clientName,
                                                    ReactiveHttpClientProperties.ClientConfig clientConfig,
                                                    MethodMetadataCache metadataCache,
                                                    ResilienceOperatorApplier resilienceOperatorApplier,
                                                    Predicate<Method> methodFilter) {
        return export(clientInterface, clientName, clientConfig, metadataCache,
                resilienceOperatorApplier, methodFilter, true);
    }

    static List<EffectiveHttpClientContract> export(Class<?> clientInterface,
                                                    String clientName,
                                                    ReactiveHttpClientProperties.ClientConfig clientConfig,
                                                    MethodMetadataCache metadataCache,
                                                    ResilienceOperatorApplier resilienceOperatorApplier,
                                                    Predicate<Method> methodFilter,
                                                    boolean validateResilienceInstances) {
        return export(clientInterface, clientName, clientConfig, metadataCache, resilienceOperatorApplier,
                methodFilter, validateResilienceInstances, true);
    }

    static List<EffectiveHttpClientContract> export(Class<?> clientInterface,
                                                    String clientName,
                                                    ReactiveHttpClientProperties.ClientConfig clientConfig,
                                                    MethodMetadataCache metadataCache,
                                                    ResilienceOperatorApplier resilienceOperatorApplier,
                                                    Predicate<Method> methodFilter,
                                                    boolean validateResilienceInstances,
                                                    boolean validateDeclarativeReturnTypes) {
        return Arrays.stream(clientInterface.getMethods())
                .filter(EffectiveHttpClientContractExporter::isDeclarativeClientMethod)
                .filter(method -> methodFilter == null || methodFilter.test(method))
                .map(method -> contract(clientInterface, clientName, clientConfig, metadataCache,
                        resilienceOperatorApplier, method, validateResilienceInstances,
                        validateDeclarativeReturnTypes))
                .sorted(Comparator.comparing(EffectiveHttpClientContract::declaringInterface)
                        .thenComparing(EffectiveHttpClientContract::javaMethodSignature))
                .toList();
    }

    private static EffectiveHttpClientContract contract(Class<?> clientInterface,
                                                        String clientName,
                                                        ReactiveHttpClientProperties.ClientConfig clientConfig,
                                                        MethodMetadataCache metadataCache,
                                                        ResilienceOperatorApplier resilienceOperatorApplier,
                                                        Method method,
                                                        boolean validateResilienceInstances,
                                                        boolean validateDeclarativeReturnTypes) {
        MethodMetadata meta = metadataCache.get(method);
        RequestPlan plan = RequestPlan.from(meta, clientInterface);
        if (validateDeclarativeReturnTypes) {
            DeclarativeRequestParameterGrammar.validate(clientInterface, clientName, plan);
            DeclarativeReturnTypeGrammar.validate(clientInterface, clientName, plan);
        }
        EffectiveApi effectiveApi = effectiveApi(plan, clientName, clientConfig);
        if (validateResilienceInstances) {
            validateResilienceInstances(plan, method, clientName, clientConfig, resilienceOperatorApplier);
        }
        BaseUrl effectiveBaseUrl = effectiveBaseUrl(clientInterface, clientConfig);
        if (validateDeclarativeReturnTypes) {
            ReactiveHttpClientFactoryBean.validatePathTemplate(
                    effectiveApi.pathTemplate(),
                    ReactiveHttpClientFactoryBean.pathVarNames(plan),
                    pathTemplateContext(method, plan));
        }
        return new EffectiveHttpClientContract(
                clientName,
                clientInterface.getName(),
                method.getDeclaringClass().getName(),
                method.getDeclaringClass() != clientInterface,
                methodSignature(method),
                genericBindings(clientInterface, method),
                typeName(plan.responseType()),
                typeName(plan.bodyType()),
                effectiveApi.httpMethod(),
                effectiveApi.pathTemplate(),
                redactedBaseUrl(effectiveBaseUrl.value()),
                effectiveBaseUrl.source(),
                plan.apiName(),
                plan.apiRefName(),
                timeoutPolicy(plan, effectiveApi, clientConfig),
                clientConfig.getLogicalCallTimeoutMs(),
                resiliencePolicy(plan, effectiveApi.httpMethod(), clientConfig, resilienceOperatorApplier),
                clientConfig.isFollowRedirects() ? "follow" : "manual",
                authMode(clientConfig),
                plan.bodyRepeatability());
    }

    private static String authMode(ReactiveHttpClientProperties.ClientConfig clientConfig) {
        if (StringUtils.hasText(clientConfig.getAuthProvider())) {
            return "provider-bean";
        }
        if (clientConfig.getAuth() != null && StringUtils.hasText(clientConfig.getAuth().getType())) {
            return clientConfig.getAuth().getType();
        }
        return "none";
    }

    private static void validateResilienceInstances(RequestPlan plan,
                                                    Method method,
                                                    String clientName,
                                                    ReactiveHttpClientProperties.ClientConfig clientConfig,
                                                    ResilienceOperatorApplier applier) {
        if (applier == null || clientConfig.getResilience() == null
                || !clientConfig.getResilience().isEnabled()) {
            return;
        }
        validateResilienceInstance(applier, ResilienceOperatorApplier.InstanceType.RETRY,
                plan.retryInstanceName(), method, clientName, "@Retry");
        validateResilienceInstance(applier, ResilienceOperatorApplier.InstanceType.RATE_LIMITER,
                plan.rateLimiterInstanceName(), method, clientName, "@RateLimiter");
        validateResilienceInstance(applier, ResilienceOperatorApplier.InstanceType.CIRCUIT_BREAKER,
                plan.circuitBreakerInstanceName(), method, clientName, "@CircuitBreaker");
        validateResilienceInstance(applier, ResilienceOperatorApplier.InstanceType.BULKHEAD,
                plan.bulkheadInstanceName(), method, clientName, "@Bulkhead");
    }

    private static void validateResilienceInstance(ResilienceOperatorApplier applier,
                                                   ResilienceOperatorApplier.InstanceType type,
                                                   String instanceName,
                                                   Method method,
                                                   String clientName,
                                                   String annotationName) {
        if (!StringUtils.hasText(instanceName) || applier.isInstanceConfigured(type, instanceName)) {
            return;
        }
        throw new IllegalStateException("Reactive HTTP client '" + clientName
                + "' references undefined Resilience4j instance " + annotationName + "(\""
                + instanceName + "\") on " + method.getDeclaringClass().getSimpleName()
                + "#" + method.getName());
    }

    private static BaseUrl effectiveBaseUrl(Class<?> clientInterface,
                                            ReactiveHttpClientProperties.ClientConfig clientConfig) {
        ReactiveHttpClient annotation = clientInterface.getAnnotation(ReactiveHttpClient.class);
        if (annotation != null && StringUtils.hasText(annotation.baseUrl())) {
            return new BaseUrl(annotation.baseUrl(), "annotation");
        }
        if (clientConfig != null && StringUtils.hasText(clientConfig.getBaseUrl())) {
            return new BaseUrl(clientConfig.getBaseUrl(), "property");
        }
        return new BaseUrl(null, "missing");
    }

    private static String redactedBaseUrl(String baseUrl) {
        if (!StringUtils.hasText(baseUrl)) {
            return baseUrl;
        }
        try {
            URI uri = new URI(baseUrl);
            if (!StringUtils.hasText(uri.getRawUserInfo())) {
                return baseUrl;
            }
            return new URI(uri.getScheme(), "REDACTED", uri.getHost(), uri.getPort(),
                    uri.getPath(), uri.getQuery(), uri.getFragment()).toString();
        } catch (URISyntaxException | IllegalArgumentException ex) {
            return baseUrl.replaceFirst("(?i)^(https?://)[^@/]+@", "$1REDACTED@");
        }
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
            ReactiveHttpClientProperties.ClientConfig clientConfig,
            ResilienceOperatorApplier resilienceOperatorApplier) {
        ReactiveHttpClientProperties.ResilienceConfig resilience = clientConfig.getResilience();
        if (resilience == null || !resilience.isEnabled()) {
            return new EffectiveHttpClientContract.ResiliencePolicy(
                    "disabled", "disabled", "disabled", "disabled");
        }
        String retry = isRetryableMethod(httpMethod, resilience)
                ? operatorInstance(resilienceOperatorApplier, ResilienceOperatorApplier.InstanceType.RETRY,
                        plan.retryInstanceName(), resilience.getRetry())
                : "disabled";
        return new EffectiveHttpClientContract.ResiliencePolicy(
                retry,
                operatorInstance(resilienceOperatorApplier, ResilienceOperatorApplier.InstanceType.RATE_LIMITER,
                        plan.rateLimiterInstanceName(), resilience.getRateLimiter()),
                operatorInstance(resilienceOperatorApplier, ResilienceOperatorApplier.InstanceType.CIRCUIT_BREAKER,
                        plan.circuitBreakerInstanceName(), resilience.getCircuitBreaker()),
                operatorInstance(resilienceOperatorApplier, ResilienceOperatorApplier.InstanceType.BULKHEAD,
                        plan.bulkheadInstanceName(), resilience.getBulkhead()));
    }

    private static String operatorInstance(ResilienceOperatorApplier resilienceOperatorApplier,
                                           ResilienceOperatorApplier.InstanceType type,
                                           String methodLevel,
                                           String clientLevel) {
        String instanceName = resolve(methodLevel, clientLevel);
        if (!StringUtils.hasText(instanceName)) {
            return "disabled";
        }
        if (resilienceOperatorApplier != null && !resilienceOperatorApplier.isOperatorAvailable(type)) {
            return "unavailable";
        }
        return instanceName;
    }

    private static boolean isRetryableMethod(String httpMethod, ReactiveHttpClientProperties.ResilienceConfig resilience) {
        return httpMethod != null
                && resilience.getRetryMethods() != null
                && resilience.getRetryMethods().contains(httpMethod.toUpperCase(Locale.ROOT));
    }

    private static String resolve(String methodLevel, String clientLevel) {
        if (StringUtils.hasText(methodLevel)) {
            return methodLevel;
        }
        return StringUtils.hasText(clientLevel) ? clientLevel : null;
    }

    static String genericBindings(Class<?> clientInterface, Method method) {
        if (method.getDeclaringClass() == clientInterface) {
            return "none";
        }
        TypeVariable<?>[] variables = method.getDeclaringClass().getTypeParameters();
        if (variables.length == 0) {
            return "none";
        }
        ResolvableType declaringType = ResolvableType.forClass(clientInterface).as(method.getDeclaringClass());
        if (declaringType == ResolvableType.NONE) {
            return "unresolved";
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < variables.length; i++) {
            if (i > 0) {
                out.append(", ");
            }
            ResolvableType resolved = declaringType.getGeneric(i);
            out.append(variables[i].getName()).append("=").append(typeName(resolved, variables[i]));
        }
        return out.toString();
    }

    private static String typeName(ResolvableType resolvableType, Type fallback) {
        if (resolvableType == ResolvableType.NONE) {
            return typeName(fallback);
        }
        Class<?> rawClass = resolvableType.resolve();
        if (resolvableType.hasGenerics() && rawClass != null) {
            return rawClass.getTypeName() + "<"
                    + Arrays.stream(resolvableType.getGenerics())
                    .map(generic -> typeName(generic, generic.getType()))
                    .reduce((left, right) -> left + ", " + right)
                    .orElse("")
                    + ">";
        }
        Type type = resolvableType.getType();
        if (type instanceof TypeVariable<?>) {
            return rawClass != null ? rawClass.getTypeName() : typeName(fallback);
        }
        return typeName(type);
    }

    private static String methodSignature(Method method) {
        return method.getName() + "("
                + Arrays.stream(method.getGenericParameterTypes())
                .map(EffectiveHttpClientContractExporter::typeName)
                .reduce((left, right) -> left + "," + right)
                .orElse("")
                + ")";
    }

    static String typeName(Type type) {
        return type != null ? type.getTypeName() : "none";
    }

    private record BaseUrl(String value, String source) {
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
