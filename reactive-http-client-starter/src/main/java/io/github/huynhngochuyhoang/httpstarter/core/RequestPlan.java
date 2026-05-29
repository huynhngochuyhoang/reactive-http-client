package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.annotation.FormFile;
import org.reactivestreams.Publisher;
import org.springframework.core.io.Resource;
import org.springframework.core.io.buffer.DataBuffer;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
                meta.getResponseType(),
                meta.getTimeoutMs(),
                meta.getRetryInstanceName(),
                meta.getCircuitBreakerInstanceName(),
                meta.getBulkheadInstanceName(),
                meta.getRateLimiterInstanceName(),
                retrySafety(meta.getHttpMethod(), meta.getHeaderParams(),
                        meta.getIdempotencyKeyParams(), meta.getGeneratedIdempotencyKeyHeader()),
                bodyRepeatability(meta),
                bodyType(meta));
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

    private static RequestBodyRepeatability bodyRepeatability(MethodMetadata meta) {
        if (meta.isMultipart()) {
            return multipartRepeatability(meta);
        }
        Type bodyType = bodyType(meta);
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

    private static RequestBodyRepeatability multipartRepeatability(MethodMetadata meta) {
        if (meta.getFormFileParams().isEmpty()) {
            return RequestBodyRepeatability.REPEATABLE;
        }
        boolean applicationOwnedFile = meta.getFormFileParams().keySet().stream()
                .map(index -> rawClass(parameterType(meta, index)))
                .anyMatch(type -> type == null || Resource.class.isAssignableFrom(type));
        return applicationOwnedFile
                ? RequestBodyRepeatability.APPLICATION_OWNED
                : RequestBodyRepeatability.REPEATABLE;
    }

    private static Type bodyType(MethodMetadata meta) {
        int bodyIndex = meta.getBodyIndex();
        if (bodyIndex < 0) {
            return null;
        }
        return parameterType(meta, bodyIndex);
    }

    private static Type parameterType(MethodMetadata meta, int index) {
        Method method = meta.getMethod();
        if (method == null || index >= method.getGenericParameterTypes().length) {
            return null;
        }
        return method.getGenericParameterTypes()[index];
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
}
