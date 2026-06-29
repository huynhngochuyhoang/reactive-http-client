package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.annotation.FormFile;
import org.reactivestreams.Publisher;
import org.springframework.core.ResolvableType;
import org.springframework.core.io.Resource;
import org.springframework.core.io.buffer.DataBuffer;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.*;

/**
 * Immutable request-shape decisions parsed from a client method.
 *
 * <p>Invocation arguments are still resolved per call by {@link RequestArgumentResolver};
 * this plan only carries stable annotation-derived bindings.
 */
record RequestPlan(
        Method method,
        String apiName,
        String apiRefName,
        EffectiveApi staticEffectiveApi,
        String httpMethod,
        String pathTemplate,
        List<NamedArgumentBinding> pathVars,
        List<NamedArgumentBinding> queryParams,
        List<NamedArgumentBinding> headerParams,
        Set<Integer> headerMapParams,
        List<NamedArgumentBinding> idempotencyKeyParams,
        String generatedIdempotencyKeyHeader,
        int bodyIndex,
        boolean multipart,
        List<FormFieldBinding> formFields,
        List<FormFileBinding> formFiles,
        boolean returnsFlux,
        Type responseType,
        long timeoutMs,
        String retryInstanceName,
        String circuitBreakerInstanceName,
        String bulkheadInstanceName,
        String rateLimiterInstanceName,
        RetrySafetyClassification retrySafety,
        RequestBodyRepeatability bodyRepeatability,
        Type bodyType
) {

    static RequestPlan from(MethodMetadata meta) {
        return from(meta, null);
    }

    static RequestPlan from(MethodMetadata meta, Class<?> concreteClientInterface) {
        Type bodyType = bodyType(meta, concreteClientInterface);
        return new RequestPlan(
                meta.getMethod(),
                meta.getApiName(),
                meta.getApiRefName(),
                meta.getStaticEffectiveApi(),
                meta.getHttpMethod(),
                meta.getPathTemplate(),
                namedBindings(meta.getPathVars()),
                namedBindings(meta.getQueryParams()),
                namedBindings(meta.getHeaderParams()),
                Set.copyOf(meta.getHeaderMapParams()),
                namedBindings(meta.getIdempotencyKeyParams()),
                meta.getGeneratedIdempotencyKeyHeader(),
                meta.getBodyIndex(),
                meta.isMultipart(),
                formFieldBindings(meta.getFormFieldParams()),
                formFileBindings(meta.getFormFileParams()),
                meta.isReturnsFlux(),
                responseType(meta, concreteClientInterface),
                meta.getTimeoutMs(),
                meta.getRetryInstanceName(),
                meta.getCircuitBreakerInstanceName(),
                meta.getBulkheadInstanceName(),
                meta.getRateLimiterInstanceName(),
                retrySafety(meta.getHttpMethod(), meta.getHeaderParams(),
                        meta.getIdempotencyKeyParams(), meta.getGeneratedIdempotencyKeyHeader()),
                bodyRepeatability(meta, concreteClientInterface, bodyType),
                bodyType);
    }

    private static List<NamedArgumentBinding> namedBindings(Map<Integer, String> source) {
        return source.entrySet().stream()
                .map(entry -> new NamedArgumentBinding(entry.getKey(), entry.getValue()))
                .toList();
    }

    private static List<FormFieldBinding> formFieldBindings(Map<Integer, String> source) {
        return source.entrySet().stream()
                .map(entry -> new FormFieldBinding(entry.getKey(), entry.getValue()))
                .toList();
    }

    private static List<FormFileBinding> formFileBindings(Map<Integer, FormFile> source) {
        return source.entrySet().stream()
                .map(entry -> new FormFileBinding(entry.getKey(), entry.getValue()))
                .toList();
    }

    private static RetrySafetyClassification retrySafety(String httpMethod,
                                                        Map<Integer, String> headerParams,
                                                        Map<Integer, String> idempotencyKeyParams,
                                                        String generatedIdempotencyKeyHeader) {
        if (ReactiveClientInvocationHandler.isSafeRetryMethod(httpMethod)) {
            return RetrySafetyClassification.SAFE_METHOD;
        }
        boolean hasIdempotencyKeyHeader = headerParams.values().stream()
                .anyMatch(name -> "Idempotency-Key".equalsIgnoreCase(name))
                || !idempotencyKeyParams.isEmpty()
                || generatedIdempotencyKeyHeader != null;
        return hasIdempotencyKeyHeader
                ? RetrySafetyClassification.EXPLICIT_IDEMPOTENCY_KEY
                : RetrySafetyClassification.UNSAFE_RETRY;
    }

    private static RequestBodyRepeatability bodyRepeatability(MethodMetadata meta,
                                                             Class<?> concreteClientInterface,
                                                             Type bodyType) {
        if (meta.isMultipart()) {
            return multipartRepeatability(meta, concreteClientInterface);
        }
        if (bodyType == null) {
            return RequestBodyRepeatability.NONE;
        }
        Class<?> rawType = rawClass(bodyType);
        if (rawType == null) {
            return RequestBodyRepeatability.APPLICATION_OWNED;
        }
        if (Publisher.class.isAssignableFrom(rawType) || DataBuffer.class.isAssignableFrom(rawType)) {
            return RequestBodyRepeatability.NON_REPEATABLE;
        }
        if (Resource.class.isAssignableFrom(rawType)) {
            return RequestBodyRepeatability.APPLICATION_OWNED;
        }
        return RequestBodyRepeatability.REPEATABLE;
    }

    private static RequestBodyRepeatability multipartRepeatability(MethodMetadata meta,
                                                                  Class<?> concreteClientInterface) {
        if (meta.getFormFileParams().isEmpty()) {
            return RequestBodyRepeatability.REPEATABLE;
        }
        boolean applicationOwnedFile = meta.getFormFileParams().keySet().stream()
                .map(index -> rawClass(parameterType(meta, index, concreteClientInterface)))
                .anyMatch(type -> type == null || Resource.class.isAssignableFrom(type));
        return applicationOwnedFile
                ? RequestBodyRepeatability.APPLICATION_OWNED
                : RequestBodyRepeatability.REPEATABLE;
    }

    private static Type bodyType(MethodMetadata meta, Class<?> concreteClientInterface) {
        int bodyIndex = meta.getBodyIndex();
        if (bodyIndex < 0) {
            return null;
        }
        return parameterType(meta, bodyIndex, concreteClientInterface);
    }

    private static Type responseType(MethodMetadata meta, Class<?> concreteClientInterface) {
        Type fallback = meta.getResponseType();
        Method method = meta.getMethod();
        if (method == null || concreteClientInterface == null) {
            return fallback;
        }
        ResolvableType returnType = ResolvableType.forMethodReturnType(method, concreteClientInterface);
        ResolvableType reactiveType = meta.isReturnsFlux()
                ? returnType.as(Flux.class)
                : returnType.as(Mono.class);
        if (reactiveType == ResolvableType.NONE || !reactiveType.hasGenerics()) {
            return fallback;
        }
        return resolvedType(reactiveType.getGeneric(0), fallback);
    }

    private static Type parameterType(MethodMetadata meta, int index, Class<?> concreteClientInterface) {
        Method method = meta.getMethod();
        if (method == null || index >= method.getGenericParameterTypes().length) {
            return null;
        }
        Type fallback = method.getGenericParameterTypes()[index];
        if (concreteClientInterface == null) {
            return fallback;
        }
        return resolvedType(ResolvableType.forMethodParameter(method, index, concreteClientInterface), fallback);
    }

    private static Type resolvedType(ResolvableType resolvableType, Type fallback) {
        if (resolvableType == ResolvableType.NONE) {
            return fallback;
        }
        Type type = resolvableType.getType();
        Class<?> rawClass = resolvableType.resolve();
        if (resolvableType.hasGenerics()) {
            if (rawClass == null) {
                return fallback;
            }
            Type[] generics = Arrays.stream(resolvableType.getGenerics())
                    .map(generic -> resolvedType(generic, generic.getType()))
                    .toArray(Type[]::new);
            return new ResolvedParameterizedType(ownerType(type), rawClass, generics);
        }
        if (!(type instanceof TypeVariable<?>)) {
            return type;
        }
        return rawClass != null ? rawClass : fallback;
    }

    private static Type ownerType(Type type) {
        return type instanceof ParameterizedType parameterizedType
                ? parameterizedType.getOwnerType()
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

    record NamedArgumentBinding(int argumentIndex, String name) {}

    record FormFieldBinding(int argumentIndex, String name) {}

    record FormFileBinding(int argumentIndex, FormFile annotation) {}

    private static final class ResolvedParameterizedType implements ParameterizedType {
        private final Type ownerType;
        private final Class<?> rawType;
        private final Type[] actualTypeArguments;

        private ResolvedParameterizedType(Type ownerType, Class<?> rawType, Type[] actualTypeArguments) {
            this.ownerType = ownerType;
            this.rawType = Objects.requireNonNull(rawType, "rawType");
            this.actualTypeArguments = actualTypeArguments.clone();
        }

        @Override
        public Type[] getActualTypeArguments() {
            return actualTypeArguments.clone();
        }

        @Override
        public Type getRawType() {
            return rawType;
        }

        @Override
        public Type getOwnerType() {
            return ownerType;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof ParameterizedType parameterizedType)) {
                return false;
            }
            return Objects.equals(ownerType, parameterizedType.getOwnerType())
                    && Objects.equals(rawType, parameterizedType.getRawType())
                    && Arrays.equals(actualTypeArguments, parameterizedType.getActualTypeArguments());
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(actualTypeArguments)
                    ^ Objects.hashCode(ownerType)
                    ^ Objects.hashCode(rawType);
        }

        @Override
        public String getTypeName() {
            return rawType.getTypeName() + "<"
                    + Arrays.stream(actualTypeArguments)
                    .map(Type::getTypeName)
                    .reduce((left, right) -> left + ", " + right)
                    .orElse("")
                    + ">";
        }
    }
}
