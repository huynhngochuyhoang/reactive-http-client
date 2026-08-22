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
        return export(clientInterface, clientName, clientConfig, metadataCache, resilienceOperatorApplier,
                EffectiveResiliencePolicy.availability(resilienceOperatorApplier), methodFilter,
                validateResilienceInstances, validateDeclarativeReturnTypes);
    }

    static List<EffectiveHttpClientContract> export(Class<?> clientInterface,
                                                    String clientName,
                                                    ReactiveHttpClientProperties.ClientConfig clientConfig,
                                                    MethodMetadataCache metadataCache,
                                                    ResilienceOperatorApplier resilienceOperatorApplier,
                                                    EffectiveResiliencePolicy.OperatorAvailability operatorAvailability,
                                                    Predicate<Method> methodFilter,
                                                    boolean validateResilienceInstances,
                                                    boolean validateDeclarativeReturnTypes) {
        return Arrays.stream(clientInterface.getMethods())
                .filter(EffectiveHttpClientContractExporter::isDeclarativeClientMethod)
                .filter(method -> methodFilter == null || methodFilter.test(method))
                .map(method -> contract(clientInterface, clientName, clientConfig, metadataCache,
                        resilienceOperatorApplier, operatorAvailability, method, validateResilienceInstances,
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
                                                        EffectiveResiliencePolicy.OperatorAvailability operatorAvailability,
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
        EffectiveResiliencePolicy resiliencePolicy = EffectiveResiliencePolicy.resolve(
                plan, effectiveApi.httpMethod(), clientConfig.getResilience(), operatorAvailability);
        if (validateResilienceInstances) {
            validateResilienceInstances(resiliencePolicy, method, clientName, resilienceOperatorApplier);
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
                resiliencePolicy(resiliencePolicy),
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

    private static void validateResilienceInstances(EffectiveResiliencePolicy policy,
                                                    Method method,
                                                    String clientName,
                                                    ResilienceOperatorApplier applier) {
        if (applier == null) {
            return;
        }
        validateResilienceInstance(applier, policy.retry(), method, clientName, "@Retry");
        validateResilienceInstance(applier, policy.rateLimiter(), method, clientName, "@RateLimiter");
        validateResilienceInstance(applier, policy.circuitBreaker(), method, clientName, "@CircuitBreaker");
        validateResilienceInstance(applier, policy.bulkhead(), method, clientName, "@Bulkhead");
    }

    private static void validateResilienceInstance(ResilienceOperatorApplier applier,
                                                   EffectiveResiliencePolicy.Operator operator,
                                                   Method method,
                                                   String clientName,
                                                   String annotationName) {
        if (!operator.active()
                || operator.source() != EffectiveResiliencePolicy.Source.METHOD
                || applier.isInstanceConfigured(operator.type(), operator.instanceName())) {
            return;
        }
        throw new IllegalStateException("Reactive HTTP client '" + clientName
                + "' references undefined Resilience4j instance " + annotationName + "(\""
                + operator.instanceName() + "\") on " + method.getDeclaringClass().getSimpleName()
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
            EffectiveResiliencePolicy policy) {
        return new EffectiveHttpClientContract.ResiliencePolicy(
                policy.retry().diagnosticValue(),
                policy.rateLimiter().diagnosticValue(),
                policy.circuitBreaker().diagnosticValue(),
                policy.bulkhead().diagnosticValue());
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
