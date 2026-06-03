package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe cache that parses and stores {@link MethodMetadata} for each interface method.
 */
public class MethodMetadataCache {

    private static final long MAX_TIMEOUT_MS = 30L * 60 * 1000; // 30 minutes

    private final ConcurrentHashMap<Method, MethodMetadata> cache = new ConcurrentHashMap<>();
    // Tracks which methods have already had a blank-path warning emitted so the warning fires exactly once per method.
    private final ConcurrentHashMap<Method, Boolean> blankPathWarned = new ConcurrentHashMap<>();

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(MethodMetadataCache.class);

    public MethodMetadata get(Method method) {
        return cache.computeIfAbsent(method, this::parse);
    }

    /**
     * Returns the number of methods for which a blank-path warning has been emitted.
     * Package-private; used only in unit tests to verify the warn-once deduplication.
     */
    int testOnlyBlankPathWarnedCount() {
        return blankPathWarned.size();
    }

    private MethodMetadata parse(Method method) {
        MethodMetadata meta = new MethodMetadata();
        meta.setMethod(method);
        meta.setApiName(method.getName());

        // ---- HTTP verb ----
        applyHttpVerb(method, meta);

        ApiRef apiRef = method.getAnnotation(ApiRef.class);
        if (apiRef != null) {
            requireNonBlankAnnotationValue(apiRef.value(), "@ApiRef", method);
            meta.setApiRefName(apiRef.value().trim());
            meta.setApiName(meta.getApiRefName());
            if (meta.getHttpMethod() != null) {
                throw new IllegalStateException(
                        "@ApiRef cannot be combined with @" + meta.getHttpMethod() + " on method: " + method);
            }
        }
        if (meta.getHttpMethod() == null && meta.getApiRefName() == null) {
            throw new IllegalStateException(
                    "Method " + method + " must declare an HTTP verb annotation or @ApiRef");
        }

        // Warn once per method when the path template is blank.
        // Blank paths are occasionally intentional (resolves to the base URL) but are far
        // more often a copy-paste mistake that only surfaces in staging. A single per-method
        // warning makes them easy to spot in logs without hard-failing user code.
        if (meta.getHttpMethod() != null
                && (meta.getPathTemplate() == null || meta.getPathTemplate().isBlank())) {
            blankPathWarned.computeIfAbsent(method, m -> {
                log.warn("@{} on {}.{}() has a blank path template — this resolves to the client base URL. "
                                + "If this is intentional you can ignore this warning.",
                        meta.getHttpMethod(),
                        method.getDeclaringClass().getSimpleName(),
                        method.getName());
                return Boolean.TRUE;
            });
        }

        // ---- Parameters ----
        IdempotencyKey methodIdempotencyKey = method.getAnnotation(IdempotencyKey.class);
        if (methodIdempotencyKey != null) {
            requireNonBlankAnnotationValue(methodIdempotencyKey.value(), "@IdempotencyKey", method);
            meta.setGeneratedIdempotencyKeyHeader(methodIdempotencyKey.value());
        }
        Annotation[][] paramAnnotations = method.getParameterAnnotations();
        Class<?>[] parameterTypes = method.getParameterTypes();
        for (int i = 0; i < paramAnnotations.length; i++) {
            for (Annotation ann : paramAnnotations[i]) {
                if (ann instanceof PathVar pv) {
                    requireNonBlankAnnotationValue(pv.value(), "@PathVar", method);
                    meta.getPathVars().put(i, pv.value());
                } else if (ann instanceof QueryParam qp) {
                    requireNonBlankAnnotationValue(qp.value(), "@QueryParam", method);
                    meta.getQueryParams().put(i, qp.value());
                } else if (ann instanceof HeaderParam hp) {
                    if (Map.class.isAssignableFrom(parameterTypes[i])) {
                        if (hp.value() != null && !hp.value().isBlank()) {
                            throw new IllegalArgumentException(
                                    "@HeaderParam value must be blank for Map parameter at index " + i + " in method: " + method);
                        }
                        meta.getHeaderMapParams().add(i);
                    } else {
                        if (hp.value() == null || hp.value().isBlank()) {
                            throw new IllegalArgumentException(
                                    "@HeaderParam value must not be blank for non-Map parameter at index " + i + " in method: " + method);
                        }
                        meta.getHeaderParams().put(i, hp.value());
                    }
                } else if (ann instanceof Body) {
                    if (meta.getBodyIndex() >= 0) {
                        throw new IllegalStateException(
                                "Multiple @Body parameters on method: " + method
                                        + " at indexes " + meta.getBodyIndex() + " and " + i);
                    }
                    meta.setBodyIndex(i);
                } else if (ann instanceof IdempotencyKey idempotencyKey) {
                    requireNonBlankAnnotationValue(idempotencyKey.value(), "@IdempotencyKey", method);
                    meta.getIdempotencyKeyParams().put(i, idempotencyKey.value());
                } else if (ann instanceof FormField ff) {
                    if (ff.value() == null || ff.value().isBlank()) {
                        throw new IllegalArgumentException(
                                "@FormField value must not be blank for parameter at index " + i + " in method: " + method);
                    }
                    meta.getFormFieldParams().put(i, ff.value());
                } else if (ann instanceof FormFile ff) {
                    if (ff.value() == null || ff.value().isBlank()) {
                        throw new IllegalArgumentException(
                                "@FormFile value must not be blank for parameter at index " + i + " in method: " + method);
                    }
                    meta.getFormFileParams().put(i, ff);
                }
            }
        }

        if (method.isAnnotationPresent(MultipartBody.class)) {
            meta.setMultipart(true);
            if (meta.getBodyIndex() >= 0) {
                throw new IllegalStateException(
                        "@MultipartBody cannot be combined with a @Body parameter on method: " + method);
            }
            if (meta.getFormFieldParams().isEmpty() && meta.getFormFileParams().isEmpty()) {
                throw new IllegalStateException(
                        "@MultipartBody method has no @FormField / @FormFile parameters: " + method);
            }
        } else if (!meta.getFormFieldParams().isEmpty() || !meta.getFormFileParams().isEmpty()) {
            throw new IllegalStateException(
                    "@FormField / @FormFile parameters require the method to be annotated @MultipartBody: " + method);
        }

        // ---- Return type ----
        Class<?> declaredReturnType = method.getReturnType();
        meta.setReturnsMono(Mono.class.isAssignableFrom(declaredReturnType));
        meta.setReturnsFlux(Flux.class.isAssignableFrom(declaredReturnType));
        if (!meta.isReturnsMono() && !meta.isReturnsFlux()) {
            throw new IllegalStateException("Method " + method + " must return Mono<T> or Flux<T>");
        }

        Type returnType = method.getGenericReturnType();
        if (returnType instanceof ParameterizedType pt) {
            Class<?> rawType = (Class<?>) pt.getRawType();
            meta.setReturnsMono(Mono.class.isAssignableFrom(rawType));
            meta.setReturnsFlux(Flux.class.isAssignableFrom(rawType));
            Type[] args = pt.getActualTypeArguments();
            if (args.length > 0) {
                meta.setResponseType(args[0]);
            }
        }

        LogHttpExchange ann = method.getAnnotation(LogHttpExchange.class);
        if (ann != null) {
            meta.setHttpExchangeLoggingEnabled(true);
            meta.setHttpExchangeLoggerClass(ann.logger());
        }

        ApiName apiName = method.getAnnotation(ApiName.class);
        if (apiName != null) {
            requireNonBlankAnnotationValue(apiName.value(), "@ApiName", method);
            meta.setApiName(apiName.value().trim());
        }

        TimeoutMs timeoutMs = method.getAnnotation(TimeoutMs.class);
        if (timeoutMs != null) {
            if (timeoutMs.value() < 0) {
                throw new IllegalArgumentException(
                        "@TimeoutMs value must be non-negative (>= 0) for method: " + method);
            }
            if (timeoutMs.value() > MAX_TIMEOUT_MS) {
                throw new IllegalArgumentException(
                        "@TimeoutMs value must be <= " + MAX_TIMEOUT_MS + " ms (30 minutes) but was "
                                + timeoutMs.value() + " for method: " + method);
            }
            meta.setTimeoutMs(timeoutMs.value());
        }

        if (meta.getApiRefName() == null) {
            meta.setStaticEffectiveApi(new EffectiveApi(
                    meta.getHttpMethod(),
                    meta.getPathTemplate(),
                    MethodMetadata.TIMEOUT_NOT_SET));
        }

        Retry retry = method.getAnnotation(Retry.class);
        if (retry != null) {
            requireNonBlankAnnotationValue(retry.value(), "@Retry", method);
            meta.setRetryInstanceName(retry.value());
        }
        CircuitBreaker circuitBreaker = method.getAnnotation(CircuitBreaker.class);
        if (circuitBreaker != null) {
            requireNonBlankAnnotationValue(circuitBreaker.value(), "@CircuitBreaker", method);
            meta.setCircuitBreakerInstanceName(circuitBreaker.value());
        }
        Bulkhead bulkhead = method.getAnnotation(Bulkhead.class);
        if (bulkhead != null) {
            requireNonBlankAnnotationValue(bulkhead.value(), "@Bulkhead", method);
            meta.setBulkheadInstanceName(bulkhead.value());
        }
        RateLimiter rateLimiter = method.getAnnotation(RateLimiter.class);
        if (rateLimiter != null) {
            requireNonBlankAnnotationValue(rateLimiter.value(), "@RateLimiter", method);
            meta.setRateLimiterInstanceName(rateLimiter.value());
        }

        meta.freezeCollections();
        meta.setRequestPlan(RequestPlan.from(meta));
        return meta;
    }

    private static void applyHttpVerb(Method method, MethodMetadata meta) {
        List<String> verbAnnotations = new ArrayList<>(7);
        String httpMethod = null;
        String pathTemplate = null;

        GET get = method.getAnnotation(GET.class);
        if (get != null) {
            verbAnnotations.add("@GET");
            httpMethod = "GET";
            pathTemplate = get.value();
        }
        POST post = method.getAnnotation(POST.class);
        if (post != null) {
            verbAnnotations.add("@POST");
            httpMethod = "POST";
            pathTemplate = post.value();
        }
        PUT put = method.getAnnotation(PUT.class);
        if (put != null) {
            verbAnnotations.add("@PUT");
            httpMethod = "PUT";
            pathTemplate = put.value();
        }
        DELETE delete = method.getAnnotation(DELETE.class);
        if (delete != null) {
            verbAnnotations.add("@DELETE");
            httpMethod = "DELETE";
            pathTemplate = delete.value();
        }
        PATCH patch = method.getAnnotation(PATCH.class);
        if (patch != null) {
            verbAnnotations.add("@PATCH");
            httpMethod = "PATCH";
            pathTemplate = patch.value();
        }
        HEAD head = method.getAnnotation(HEAD.class);
        if (head != null) {
            verbAnnotations.add("@HEAD");
            httpMethod = "HEAD";
            pathTemplate = head.value();
        }
        OPTIONS options = method.getAnnotation(OPTIONS.class);
        if (options != null) {
            verbAnnotations.add("@OPTIONS");
            httpMethod = "OPTIONS";
            pathTemplate = options.value();
        }

        if (verbAnnotations.size() > 1) {
            throw new IllegalStateException(
                    "Multiple HTTP verb annotations " + String.join(", ", verbAnnotations)
                            + " on method: " + method);
        }
        if (verbAnnotations.size() == 1) {
            meta.setHttpMethod(httpMethod);
            meta.setPathTemplate(pathTemplate);
        }
    }

    private static void requireNonBlankAnnotationValue(String value, String annotationName, Method method) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    annotationName + " value must not be blank on method: " + method);
        }
    }
}
