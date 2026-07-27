package io.github.huynhngochuyhoang.httpstarter.core;

import org.reactivestreams.Publisher;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.lang.reflect.*;
import java.util.Arrays;

/**
 * Internal grammar for response shapes the invocation handler can execute
 * without treating a reactive publisher as a codec value.
 */
final class DeclarativeReturnTypeGrammar {

    private static final String SUPPORTED_SHAPES =
            "Mono<T>, Mono<Void>, Mono<ResponseEntity<T>>, Flux<T>, Flux<DataBuffer>, "
                    + "or Mono<ResponseEntity<Flux<DataBuffer>>>";

    private DeclarativeReturnTypeGrammar() {
    }

    static void validate(Class<?> concreteClientInterface, String clientName, RequestPlan plan) {
        Method method = plan.method();
        Class<?> outerType = method.getReturnType();
        if (!Mono.class.equals(outerType) && !Flux.class.equals(outerType)) {
            throw unsupported(concreteClientInterface, clientName, method, plan.responseType(),
                    "the outer return type must be exactly Mono or Flux");
        }

        Type declaredReturnType = method.getGenericReturnType();
        if (!(declaredReturnType instanceof ParameterizedType)) {
            if (declaredReturnType instanceof Class<?> declaredClass && declaredClass.equals(outerType)) {
                return;
            }
            throw unsupported(concreteClientInterface, clientName, method, plan.responseType(),
                    "the outer reactive return type must not be an unresolved type variable");
        }

        Type responseType = plan.responseType();
        if (responseType == null) {
            throw unsupported(concreteClientInterface, clientName, method, null,
                    "the parameterized reactive return type has no resolvable element type");
        }

        if (Flux.class.equals(outerType)) {
            if (containsTypeVariable(responseType)) {
                throw unsupported(concreteClientInterface, clientName, method, responseType,
                        "the reactive element type must resolve against the concrete client interface");
            }
            if (ResponseEntity.class.equals(rawClass(responseType))) {
                throw unsupported(concreteClientInterface, clientName, method, responseType,
                        "ResponseEntity envelopes are supported only inside Mono");
            }
            if (containsPublisher(responseType)) {
                throw unsupported(concreteClientInterface, clientName, method, responseType,
                        "a Flux element type cannot contain another reactive Publisher");
            }
            return;
        }

        if (ResponseEntity.class.equals(rawClass(responseType))) {
            Type bodyType = responseEntityBodyType(responseType);
            if (bodyType == null || containsTypeVariable(bodyType)) {
                throw unsupported(concreteClientInterface, clientName, method, responseType,
                        "ResponseEntity must declare a resolvable body type");
            }
            if (containsPublisher(bodyType) && !isFluxDataBuffer(bodyType)) {
                throw unsupported(concreteClientInterface, clientName, method, responseType,
                        "the only reactive ResponseEntity body supported is Flux<DataBuffer>");
            }
            return;
        }

        if (containsTypeVariable(responseType)) {
            throw unsupported(concreteClientInterface, clientName, method, responseType,
                    "the reactive element type must resolve against the concrete client interface");
        }
        if (containsPublisher(responseType)) {
            throw unsupported(concreteClientInterface, clientName, method, responseType,
                    "a Mono element type cannot contain another reactive Publisher");
        }
    }

    static Type responseEntityBodyType(Type responseType) {
        if (!(responseType instanceof ParameterizedType parameterizedType)
                || !ResponseEntity.class.equals(rawClass(parameterizedType))) {
            return null;
        }
        Type[] arguments = parameterizedType.getActualTypeArguments();
        return arguments.length == 1 ? arguments[0] : null;
    }

    static boolean isStreamingResponseEntity(Type responseType) {
        return isFluxDataBuffer(responseEntityBodyType(responseType));
    }

    private static boolean isFluxDataBuffer(Type type) {
        if (!(type instanceof ParameterizedType parameterizedType)
                || !Flux.class.equals(rawClass(parameterizedType))) {
            return false;
        }
        Type[] arguments = parameterizedType.getActualTypeArguments();
        return arguments.length == 1 && DataBuffer.class.equals(arguments[0]);
    }

    private static boolean containsPublisher(Type type) {
        Class<?> rawType = rawClass(type);
        if (rawType != null && Publisher.class.isAssignableFrom(rawType)) {
            return true;
        }
        if (type instanceof ParameterizedType parameterizedType) {
            return Arrays.stream(parameterizedType.getActualTypeArguments())
                    .anyMatch(DeclarativeReturnTypeGrammar::containsPublisher);
        }
        if (type instanceof WildcardType wildcardType) {
            return Arrays.stream(wildcardType.getUpperBounds())
                    .anyMatch(DeclarativeReturnTypeGrammar::containsPublisher)
                    || Arrays.stream(wildcardType.getLowerBounds())
                    .anyMatch(DeclarativeReturnTypeGrammar::containsPublisher);
        }
        if (type instanceof GenericArrayType arrayType) {
            return containsPublisher(arrayType.getGenericComponentType());
        }
        return false;
    }

    private static boolean containsTypeVariable(Type type) {
        if (type instanceof TypeVariable<?>) {
            return true;
        }
        if (type instanceof ParameterizedType parameterizedType) {
            return Arrays.stream(parameterizedType.getActualTypeArguments())
                    .anyMatch(DeclarativeReturnTypeGrammar::containsTypeVariable);
        }
        if (type instanceof WildcardType wildcardType) {
            return Arrays.stream(wildcardType.getUpperBounds())
                    .anyMatch(DeclarativeReturnTypeGrammar::containsTypeVariable)
                    || Arrays.stream(wildcardType.getLowerBounds())
                    .anyMatch(DeclarativeReturnTypeGrammar::containsTypeVariable);
        }
        if (type instanceof GenericArrayType arrayType) {
            return containsTypeVariable(arrayType.getGenericComponentType());
        }
        return false;
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

    private static IllegalStateException unsupported(Class<?> concreteClientInterface,
                                                     String clientName,
                                                     Method method,
                                                     Type resolvedResponseType,
                                                     String reason) {
        return new IllegalStateException(
                "Unsupported declarative return type for reactive HTTP client '" + clientName + "'"
                        + " [concreteClient=" + concreteClientInterface.getName()
                        + ", declaringInterface=" + method.getDeclaringClass().getName()
                        + ", method=" + method.toGenericString()
                        + ", resolvedResponseType=" + EffectiveHttpClientContractExporter.typeName(resolvedResponseType)
                        + "]: " + reason + ". Supported return shapes: " + SUPPORTED_SHAPES + ".");
    }
}
