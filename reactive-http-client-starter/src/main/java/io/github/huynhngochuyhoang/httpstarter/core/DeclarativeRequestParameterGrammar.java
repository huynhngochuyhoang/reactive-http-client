package io.github.huynhngochuyhoang.httpstarter.core;

import org.springframework.core.io.Resource;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;

/** Internal grammar for request parameter roles supported by the invocation handler. */
final class DeclarativeRequestParameterGrammar {

    private DeclarativeRequestParameterGrammar() {
    }

    static List<Integer> validate(Class<?> concreteClientInterface, String clientName, RequestPlan plan) {
        Method method = plan.method();
        Map<Integer, List<String>> rolesByParameter = rolesByParameter(plan);
        for (Map.Entry<Integer, List<String>> entry : rolesByParameter.entrySet()) {
            if (entry.getValue().size() > 1) {
                throw invalid(concreteClientInterface, clientName, plan, entry.getKey(),
                        "conflicting request-binding roles " + entry.getValue());
            }
        }

        rejectDuplicateNames(concreteClientInterface, clientName, plan, plan.pathVars(), "@PathVar", false);
        rejectDuplicateNames(concreteClientInterface, clientName, plan, plan.queryParams(), "@QueryParam", false);
        rejectDuplicateHeaderNames(concreteClientInterface, clientName, plan);
        validateHeaderMapTypes(concreteClientInterface, clientName, plan);
        validateFormFileTypes(concreteClientInterface, clientName, plan);

        List<Integer> unannotated = new ArrayList<>();
        for (int index = 0; index < method.getParameterCount(); index++) {
            if (!rolesByParameter.containsKey(index)) {
                unannotated.add(index);
            }
        }
        return List.copyOf(unannotated);
    }

    private static Map<Integer, List<String>> rolesByParameter(RequestPlan plan) {
        Map<Integer, List<String>> roles = new LinkedHashMap<>();
        plan.pathVars().forEach(binding -> addRole(roles, binding.argumentIndex(),
                "@PathVar(\"" + binding.name() + "\")"));
        plan.queryParams().forEach(binding -> addRole(roles, binding.argumentIndex(),
                "@QueryParam(\"" + binding.name() + "\")"));
        plan.headerParams().forEach(binding -> addRole(roles, binding.argumentIndex(),
                "@HeaderParam(\"" + binding.name() + "\")"));
        plan.headerMapParams().stream().sorted().forEach(index -> addRole(roles, index, "@HeaderParam map"));
        plan.idempotencyKeyParams().forEach(binding -> addRole(roles, binding.argumentIndex(),
                "@IdempotencyKey(\"" + binding.name() + "\")"));
        if (plan.bodyIndex() >= 0) {
            addRole(roles, plan.bodyIndex(), "@Body");
        }
        plan.formFields().forEach(binding -> addRole(roles, binding.argumentIndex(),
                "@FormField(\"" + binding.name() + "\")"));
        plan.formFiles().forEach(binding -> addRole(roles, binding.argumentIndex(),
                "@FormFile(\"" + binding.annotation().value() + "\")"));
        return roles;
    }

    private static void addRole(Map<Integer, List<String>> roles, int index, String role) {
        roles.computeIfAbsent(index, ignored -> new ArrayList<>()).add(role);
    }

    private static void rejectDuplicateNames(Class<?> concreteClientInterface,
                                             String clientName,
                                             RequestPlan plan,
                                             List<RequestPlan.NamedArgumentBinding> bindings,
                                             String annotation,
                                             boolean ignoreCase) {
        Map<String, RequestPlan.NamedArgumentBinding> seen = new HashMap<>();
        for (RequestPlan.NamedArgumentBinding binding : bindings) {
            String key = ignoreCase ? binding.name().toLowerCase(Locale.ROOT) : binding.name();
            RequestPlan.NamedArgumentBinding previous = seen.putIfAbsent(key, binding);
            if (previous != null) {
                String duplicate = annotation.startsWith("@")
                        ? "duplicate " + annotation + "(\"" + binding.name() + "\") bindings"
                        : "duplicate " + annotation + " name '" + binding.name() + "'";
                throw invalid(concreteClientInterface, clientName, plan, binding.argumentIndex(),
                        duplicate + "; also declared at parameter index " + previous.argumentIndex());
            }
        }
    }

    private static void rejectDuplicateHeaderNames(Class<?> concreteClientInterface,
                                                   String clientName,
                                                   RequestPlan plan) {
        List<RequestPlan.NamedArgumentBinding> headers = new ArrayList<>(plan.headerParams());
        headers.addAll(plan.idempotencyKeyParams());
        headers.sort(java.util.Comparator.comparingInt(RequestPlan.NamedArgumentBinding::argumentIndex));
        rejectDuplicateNames(concreteClientInterface, clientName, plan, headers,
                "header/idempotency-key", true);
    }

    private static void validateHeaderMapTypes(Class<?> concreteClientInterface,
                                               String clientName,
                                               RequestPlan plan) {
        for (Integer index : plan.headerMapParams()) {
            Class<?> rawType = rawClass(parameterType(plan, index));
            if (rawType == null || !Map.class.isAssignableFrom(rawType)) {
                throw invalid(concreteClientInterface, clientName, plan, index,
                        "@HeaderParam without a name requires a Map parameter");
            }
        }
        for (RequestPlan.NamedArgumentBinding binding : plan.headerParams()) {
            Class<?> rawType = rawClass(parameterType(plan, binding.argumentIndex()));
            if (rawType != null && Map.class.isAssignableFrom(rawType)) {
                throw invalid(concreteClientInterface, clientName, plan, binding.argumentIndex(),
                        "@HeaderParam on a Map parameter must not declare a name");
            }
        }
    }

    private static void validateFormFileTypes(Class<?> concreteClientInterface,
                                              String clientName,
                                              RequestPlan plan) {
        for (RequestPlan.FormFileBinding binding : plan.formFiles()) {
            Type type = parameterType(plan, binding.argumentIndex());
            Class<?> rawType = rawClass(type);
            if (byte[].class.equals(rawType)
                    || (rawType != null && Resource.class.isAssignableFrom(rawType))
                    || FileAttachment.class.equals(rawType)) {
                continue;
            }
            throw invalid(concreteClientInterface, clientName, plan, binding.argumentIndex(),
                    "@FormFile supports only byte[], Resource, or FileAttachment");
        }
    }

    private static Type parameterType(RequestPlan plan, int index) {
        return index >= 0 && index < plan.parameterTypes().size()
                ? plan.parameterTypes().get(index)
                : null;
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

    private static IllegalStateException invalid(Class<?> concreteClientInterface,
                                                 String clientName,
                                                 RequestPlan plan,
                                                 int parameterIndex,
                                                 String reason) {
        Method method = plan.method();
        Type parameterType = parameterType(plan, parameterIndex);
        return new IllegalStateException("Reactive HTTP client '" + clientName
                + "' has an invalid declarative request parameter: concreteClient="
                + concreteClientInterface.getName()
                + ", declaringInterface=" + method.getDeclaringClass().getName()
                + ", method=" + method.toGenericString()
                + ", parameterIndex=" + parameterIndex
                + ", resolvedParameterType=" + (parameterType != null ? parameterType.getTypeName() : "unknown")
                + ", reason=" + reason);
    }
}
