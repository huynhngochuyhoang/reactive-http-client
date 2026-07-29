package io.github.huynhngochuyhoang.httpstarter.core;

import org.reactivestreams.Publisher;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.lang.reflect.*;
import java.util.*;

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
        if (!(declaredReturnType instanceof ParameterizedType parameterizedReturnType)) {
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
        Type declaredResponseType = parameterizedReturnType.getActualTypeArguments()[0];
        if (containsUnresolvedTypeVariable(declaredResponseType, concreteClientInterface, method)) {
            throw unsupported(concreteClientInterface, clientName, method, responseType,
                    "the reactive element type must resolve against the concrete client interface");
        }
        if (directWildcardBoundAssignableTo(responseType, Void.class) != null) {
            throw unsupported(concreteClientInterface, clientName, method, responseType,
                    "bodiless Void responses must declare Void directly");
        }
        Class<?> wildcardEnvelopeType = directWildcardBoundAssignableTo(responseType, ResponseEntity.class);
        if (wildcardEnvelopeType != null) {
            String reason = ResponseEntity.class.equals(wildcardEnvelopeType)
                    ? "ResponseEntity envelopes must be declared directly as Mono<ResponseEntity<T>>"
                    : "ResponseEntity subclasses are not supported as declarative return types";
            throw unsupported(concreteClientInterface, clientName, method, responseType, reason);
        }
        Class<?> responseRawType = rawClass(responseType);
        if (responseRawType != null && !ResponseEntity.class.equals(responseRawType)
                && ResponseEntity.class.isAssignableFrom(responseRawType)) {
            throw unsupported(concreteClientInterface, clientName, method, responseType,
                    "ResponseEntity subclasses are not supported as declarative return types");
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
            if (responseRawType != null && DataBuffer.class.isAssignableFrom(responseRawType)
                    && !DataBuffer.class.equals(responseRawType)) {
                throw unsupported(concreteClientInterface, clientName, method, responseType,
                        "raw DataBuffer streams must be declared directly as Flux<DataBuffer>");
            }
            if (directWildcardBoundAssignableTo(responseType, DataBuffer.class) != null) {
                throw unsupported(concreteClientInterface, clientName, method, responseType,
                        "raw DataBuffer streams must be declared directly as Flux<DataBuffer>");
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
            if (directWildcardBoundAssignableTo(bodyType, Void.class) != null) {
                throw unsupported(concreteClientInterface, clientName, method, responseType,
                        "bodiless Void responses must declare Void directly");
            }
            if (containsResponseEntity(bodyType)) {
                throw unsupported(concreteClientInterface, clientName, method, responseType,
                        "a ResponseEntity body cannot contain another ResponseEntity envelope");
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
        if (type == null) {
            return false;
        }
        if (type instanceof Class<?> clazz && clazz.isArray()) {
            return containsPublisher(clazz.getComponentType());
        }
        Class<?> rawType = rawClass(type);
        if (rawType != null && Publisher.class.isAssignableFrom(rawType)) {
            return true;
        }
        if (type instanceof ParameterizedType parameterizedType) {
            return containsPublisherInOwnerBindings(parameterizedType.getOwnerType())
                    || Arrays.stream(parameterizedType.getActualTypeArguments())
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

    private static boolean containsPublisherInOwnerBindings(Type ownerType) {
        if (!(ownerType instanceof ParameterizedType parameterizedOwner)) {
            return false;
        }
        return containsPublisherInOwnerBindings(parameterizedOwner.getOwnerType())
                || Arrays.stream(parameterizedOwner.getActualTypeArguments())
                .anyMatch(DeclarativeReturnTypeGrammar::containsPublisher);
    }

    private static boolean containsResponseEntity(Type type) {
        if (type == null) {
            return false;
        }
        if (type instanceof Class<?> clazz && clazz.isArray()) {
            return containsResponseEntity(clazz.getComponentType());
        }
        Class<?> rawType = rawClass(type);
        if (rawType != null && ResponseEntity.class.isAssignableFrom(rawType)) {
            return true;
        }
        if (type instanceof ParameterizedType parameterizedType) {
            return containsResponseEntity(parameterizedType.getOwnerType())
                    || Arrays.stream(parameterizedType.getActualTypeArguments())
                    .anyMatch(DeclarativeReturnTypeGrammar::containsResponseEntity);
        }
        if (type instanceof WildcardType wildcardType) {
            return Arrays.stream(wildcardType.getUpperBounds())
                    .anyMatch(DeclarativeReturnTypeGrammar::containsResponseEntity)
                    || Arrays.stream(wildcardType.getLowerBounds())
                    .anyMatch(DeclarativeReturnTypeGrammar::containsResponseEntity);
        }
        if (type instanceof GenericArrayType arrayType) {
            return containsResponseEntity(arrayType.getGenericComponentType());
        }
        return false;
    }

    private static Class<?> directWildcardBoundAssignableTo(Type type, Class<?> expectedRawType) {
        if (!(type instanceof WildcardType wildcardType)) {
            return null;
        }
        for (Type bound : wildcardType.getUpperBounds()) {
            Class<?> rawBound = rawClass(bound);
            if (rawBound != null && expectedRawType.isAssignableFrom(rawBound)) {
                return rawBound;
            }
        }
        for (Type bound : wildcardType.getLowerBounds()) {
            Class<?> rawBound = rawClass(bound);
            if (rawBound != null && expectedRawType.isAssignableFrom(rawBound)) {
                return rawBound;
            }
        }
        return null;
    }

    private static boolean containsUnresolvedTypeVariable(Type type,
                                                          Class<?> concreteClientInterface,
                                                          Method method) {
        Map<TypeVariable<?>, Type> bindings = findTypeBindings(
                concreteClientInterface, method.getDeclaringClass(), new HashMap<>());
        return containsUnresolvedTypeVariable(
                type, bindings != null ? bindings : Map.of(), new HashSet<>());
    }

    private static boolean containsUnresolvedTypeVariable(Type type,
                                                          Map<TypeVariable<?>, Type> bindings,
                                                          Set<TypeVariable<?>> visiting) {
        if (type == null) {
            return false;
        }
        if (type instanceof TypeVariable<?> variable) {
            Type binding = bindings.get(variable);
            if (binding == null || !visiting.add(variable)) {
                return true;
            }
            boolean unresolved = containsUnresolvedTypeVariable(binding, bindings, visiting);
            visiting.remove(variable);
            return unresolved;
        }
        if (type instanceof ParameterizedType parameterizedType) {
            return containsUnresolvedTypeVariable(parameterizedType.getOwnerType(), bindings, visiting)
                    || Arrays.stream(parameterizedType.getActualTypeArguments())
                    .anyMatch(argument -> containsUnresolvedTypeVariable(argument, bindings, visiting));
        }
        if (type instanceof WildcardType wildcardType) {
            return Arrays.stream(wildcardType.getUpperBounds())
                    .anyMatch(bound -> containsUnresolvedTypeVariable(bound, bindings, visiting))
                    || Arrays.stream(wildcardType.getLowerBounds())
                    .anyMatch(bound -> containsUnresolvedTypeVariable(bound, bindings, visiting));
        }
        if (type instanceof GenericArrayType arrayType) {
            return containsUnresolvedTypeVariable(arrayType.getGenericComponentType(), bindings, visiting);
        }
        return false;
    }

    private static Map<TypeVariable<?>, Type> findTypeBindings(Type currentType,
                                                               Class<?> targetType,
                                                               Map<TypeVariable<?>, Type> inheritedBindings) {
        Class<?> currentClass = rawClass(currentType);
        if (currentClass == null) {
            return null;
        }
        Map<TypeVariable<?>, Type> bindings = new HashMap<>(inheritedBindings);
        if (currentType instanceof ParameterizedType parameterizedType) {
            TypeVariable<?>[] variables = currentClass.getTypeParameters();
            Type[] arguments = parameterizedType.getActualTypeArguments();
            for (int i = 0; i < Math.min(variables.length, arguments.length); i++) {
                bindings.put(variables[i], arguments[i]);
            }
        }
        if (currentClass.equals(targetType)) {
            return bindings;
        }
        for (Type parentInterface : currentClass.getGenericInterfaces()) {
            Map<TypeVariable<?>, Type> result = findTypeBindings(parentInterface, targetType, bindings);
            if (result != null) {
                return result;
            }
        }
        Type superclass = currentClass.getGenericSuperclass();
        return superclass != null
                ? findTypeBindings(superclass, targetType, bindings)
                : null;
    }

    private static boolean containsTypeVariable(Type type) {
        if (type == null) {
            return false;
        }
        if (type instanceof TypeVariable<?>) {
            return true;
        }
        if (type instanceof ParameterizedType parameterizedType) {
            return containsTypeVariable(parameterizedType.getOwnerType())
                    || Arrays.stream(parameterizedType.getActualTypeArguments())
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
