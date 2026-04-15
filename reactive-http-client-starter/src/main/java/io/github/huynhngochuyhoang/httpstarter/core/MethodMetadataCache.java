package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe cache that parses and stores {@link MethodMetadata} for each interface method.
 */
public class MethodMetadataCache {

    private final ConcurrentHashMap<Method, MethodMetadata> cache = new ConcurrentHashMap<>();

    public MethodMetadata get(Method method) {
        return cache.computeIfAbsent(method, this::parse);
    }

    private MethodMetadata parse(Method method) {
        MethodMetadata meta = new MethodMetadata();
        meta.setMethod(method);
        meta.setApiName(method.getName());

        // ---- HTTP verb ----
        if (method.isAnnotationPresent(GET.class)) {
            meta.setHttpMethod("GET");
            meta.setPathTemplate(method.getAnnotation(GET.class).value());
        } else if (method.isAnnotationPresent(POST.class)) {
            meta.setHttpMethod("POST");
            meta.setPathTemplate(method.getAnnotation(POST.class).value());
        } else if (method.isAnnotationPresent(PUT.class)) {
            meta.setHttpMethod("PUT");
            meta.setPathTemplate(method.getAnnotation(PUT.class).value());
        } else if (method.isAnnotationPresent(DELETE.class)) {
            meta.setHttpMethod("DELETE");
            meta.setPathTemplate(method.getAnnotation(DELETE.class).value());
        }

        // ---- Parameters ----
        Annotation[][] paramAnnotations = method.getParameterAnnotations();
        Class<?>[] parameterTypes = method.getParameterTypes();
        for (int i = 0; i < paramAnnotations.length; i++) {
            for (Annotation ann : paramAnnotations[i]) {
                if (ann instanceof PathVar pv) {
                    meta.getPathVars().put(i, pv.value());
                } else if (ann instanceof QueryParam qp) {
                    meta.getQueryParams().put(i, qp.value());
                } else if (ann instanceof HeaderParam hp) {
                    if (Map.class.isAssignableFrom(parameterTypes[i])) {
                        meta.getHeaderMapParams().add(i);
                    } else {
                        if (hp.value() == null || hp.value().isBlank()) {
                            throw new IllegalArgumentException(
                                    "@HeaderParam value must not be blank for non-Map parameter at index " + i + " in method: " + method);
                        }
                        meta.getHeaderParams().put(i, hp.value());
                    }
                } else if (ann instanceof Body) {
                    meta.setBodyIndex(i);
                }
            }
        }

        // ---- Return type ----
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

        if (method.isAnnotationPresent(LogHttpExchange.class)) {
            LogHttpExchange ann = method.getAnnotation(LogHttpExchange.class);
            meta.setHttpExchangeLoggingEnabled(true);
            meta.setHttpExchangeLoggerClass(ann.logger());
        }

        ApiName apiName = method.getAnnotation(ApiName.class);
        if (apiName != null) {
            meta.setApiName(apiName.value());
        }

        TimeoutMs timeoutMs = method.getAnnotation(TimeoutMs.class);
        if (timeoutMs != null) {
            if (timeoutMs.value() < 0) {
                throw new IllegalArgumentException(
                        "@TimeoutMs value must be non-negative (>= 0) for method: " + method);
            }
            meta.setTimeoutMs(timeoutMs.value());
        }

        return meta;
    }
}
