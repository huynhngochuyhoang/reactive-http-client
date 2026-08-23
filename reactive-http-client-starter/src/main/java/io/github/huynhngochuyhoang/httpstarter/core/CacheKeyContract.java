package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import org.reactivestreams.Publisher;
import org.springframework.asm.ClassReader;
import org.springframework.asm.ClassVisitor;
import org.springframework.asm.MethodVisitor;
import org.springframework.asm.Opcodes;
import org.springframework.core.io.Resource;
import org.springframework.core.io.buffer.DataBuffer;
import reactor.util.context.Context;
import reactor.util.context.ContextView;

import java.io.*;
import java.lang.reflect.*;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.*;
import java.util.*;

/**
 * Internal cache-key validation and per-subscription argument preparation.
 *
 * <p>Only the SHA-256 digest survives key derivation. Canonical bytes and
 * selected raw values remain subscription-local and are never rendered.
 */
final class CacheKeyContract {

    private static final int MAX_DEPTH = 32;
    private static final int MAX_ELEMENTS = 10_000;
    private static final int MAX_CANONICAL_BYTES = 1024 * 1024;
    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    private static final Set<Class<?>> IMMUTABLE_SCALARS = Set.of(
            String.class, Boolean.class, Byte.class, Short.class, Integer.class, Long.class,
            Float.class, Double.class, Character.class, BigInteger.class, BigDecimal.class,
            UUID.class, URI.class, Instant.class, LocalDate.class, LocalTime.class,
            LocalDateTime.class, OffsetTime.class, OffsetDateTime.class, ZonedDateTime.class,
            Duration.class, Period.class);
    private static final ClassValue<CanonicalRecordAccessors> CANONICAL_RECORD_ACCESSORS =
            new ClassValue<>() {
                @Override
                protected CanonicalRecordAccessors computeValue(Class<?> type) {
                    return inspectCanonicalRecordAccessors(type);
                }
            };

    private CacheKeyContract() {
    }

    static void validate(Class<?> clientInterface,
                         String clientName,
                         RequestPlan plan,
                         ReactiveHttpClientProperties.ClientConfig clientConfig,
                         EffectiveCachePolicy.Selection selection) {
        if (!selection.enabled() || selection.policy() == null) {
            return;
        }
        String context = context(clientInterface, clientName, plan, selection);
        VariantSelection variants = variants(plan, clientConfig, selection.policy(), context);
        Set<Integer> frozenIndexes = requestTargetArgumentIndexes(plan);
        frozenIndexes.addAll(variants.parameterIndexes().values());
        frozenIndexes.addAll(selectedHeaderArgumentIndexes(plan, variants));
        for (int index : frozenIndexes) {
            if (index < 0 || index >= plan.parameterTypes().size()) {
                throw invalid(context, "cache-key parameter index " + index + " is outside the method signature");
            }
            validateType(plan.parameterTypes().get(index), context + " parameter index " + index, 0);
        }
        validateRequestTargetTypes(plan, context);

        if (!selection.policy().isSharedResponse()) {
            if (hasUnpartitionedNamedHeaders(plan, variants)) {
                throw invalid(context, "dynamic request headers must be selected by vary-by-headers or "
                        + "shared-response must be explicitly acknowledged");
            }
            if (!plan.headerMapParams().isEmpty()) {
                throw invalid(context, "map-based request headers cannot be proven fully partitioned; "
                        + "set shared-response only when reuse is intentionally safe");
            }
            if (plan.bodyIndex() >= 0
                    && !variants.parameterIndexes().containsValue(plan.bodyIndex())) {
                throw invalid(context, "the request body must be selected through vary-by-parameters or "
                        + "shared-response must be explicitly acknowledged");
            }
            if (clientConfig != null && clientConfig.hasAuthConfigured()
                    && !hasAuthenticatedResponsePartition(plan, variants)) {
                throw invalid(context, "authenticated responses require an explicit parameter/header/context "
                        + "partition or shared-response acknowledgement");
            }
        }
    }

    static Object[] freezeArguments(RequestPlan plan,
                                    Object[] arguments,
                                    ReactiveHttpClientProperties.CachePolicyConfig policy) {
        validateRequestTargetContainers(plan, arguments);
        validateSelectedBodyImplementation(plan, arguments, policy);
        Object[] frozen = arguments != null ? arguments.clone() : new Object[0];
        VariantSelection variants = variants(
                plan, null, policy, "Method " + plan.method().toGenericString());
        Set<Integer> indexes = requestTargetArgumentIndexes(plan);
        indexes.addAll(variants.parameterIndexes().values());
        indexes.addAll(selectedHeaderArgumentIndexes(plan, variants));
        FreezeBudget budget = new FreezeBudget();
        for (int index : indexes) {
            if (index < frozen.length) {
                frozen[index] = freeze(frozen[index], 0, "parameter index " + index, budget);
            }
        }
        snapshotHeaderArguments(plan, variants, frozen);
        return frozen;
    }

    private static void validateSelectedBodyImplementation(
            RequestPlan plan,
            Object[] arguments,
            ReactiveHttpClientProperties.CachePolicyConfig policy) {
        if (!selectsRequestBody(plan, policy) || arguments == null
                || plan.bodyIndex() < 0 || plan.bodyIndex() >= arguments.length) {
            return;
        }
        Object body = arguments[plan.bodyIndex()];
        String collectionKind = body instanceof List<?> ? "list"
                : body instanceof Set<?> ? "set"
                : body instanceof Map<?, ?> ? "map"
                : null;
        if (collectionKind != null && !isJdkCollectionImplementation(body.getClass())) {
            throw invalid("Method " + plan.method().toGenericString(),
                    "selected request body " + collectionKind + " implementation "
                            + body.getClass().getTypeName()
                            + " cannot preserve its concrete JSON codec semantics through a defensive snapshot; "
                            + "use a JDK collection implementation or an immutable record body");
        }
    }

    private static boolean isJdkCollectionImplementation(Class<?> type) {
        return type.getClassLoader() == null && type.getModule() == List.class.getModule();
    }

    private static void snapshotHeaderArguments(
            RequestPlan plan, VariantSelection variants, Object[] arguments) {
        RequestTargetProjector projector = new RequestTargetProjector("Header projection");
        for (int index : selectedHeaderArgumentIndexes(plan, variants)) {
            if (index >= 0 && index < arguments.length) {
                arguments[index] = projector.projectHeaderArgument(arguments[index]);
            }
        }
    }

    static RequestArgumentResolver.ResolvedArgs snapshotRequestTarget(
            RequestArgumentResolver.ResolvedArgs resolved) {
        RequestTargetProjector projector = new RequestTargetProjector("Request-target projection");
        Map<String, Object> pathVars = new LinkedHashMap<>();
        resolved.pathVars().forEach((name, value) -> pathVars.put(name, projector.project(value)));
        Map<String, List<Object>> queryParams = new LinkedHashMap<>();
        resolved.queryParams().forEach((name, values) -> queryParams.put(name, values.stream()
                .map(projector::project)
                .map(value -> (Object) value)
                .toList()));
        return new RequestArgumentResolver.ResolvedArgs(
                pathVars, queryParams, resolved.headers(), resolved.body());
    }

    static PreparedKey derive(Class<?> clientInterface,
                              String clientName,
                              RequestPlan plan,
                              Object[] frozenArguments,
                              RequestArgumentResolver.ResolvedArgs resolved,
                              ContextView reactorContext,
                              ReactiveHttpClientProperties.CachePolicyConfig policy) {
        return derive(clientInterface, clientName, plan, frozenArguments, resolved,
                reactorContext, policy, null);
    }

    static PreparedKey derive(Class<?> clientInterface,
                              String clientName,
                              RequestPlan plan,
                              Object[] frozenArguments,
                              RequestArgumentResolver.ResolvedArgs resolved,
                              ContextView reactorContext,
                              ReactiveHttpClientProperties.CachePolicyConfig policy,
                              SerializedBodyKey serializedBodyKey) {
        RequestArgumentResolver.ResolvedArgs requestTarget = hasSnapshottedRequestTarget(resolved)
                ? resolved
                : snapshotRequestTarget(resolved);
        String context = "Reactive HTTP client '" + clientName + "' method=" + plan.method().toGenericString();
        VariantSelection variants = variants(plan, null, policy, context);
        Map<String, Object> contextValues = new LinkedHashMap<>();
        FreezeBudget contextBudget = new FreezeBudget();
        for (String name : variants.contextNames()) {
            Object value = reactorContext.getOrDefault(name, null);
            contextValues.put(name, freeze(value, 0, "Reactor context '" + name + "'", contextBudget));
        }

        CanonicalWriter writer = new CanonicalWriter();
        writer.value("reactive-http-cache-key-v1");
        writer.value(clientName);
        Class<?> concreteClient = clientInterface != null ? clientInterface : plan.method().getDeclaringClass();
        writer.value(concreteClient.getName());
        writer.value(resolvedMethodSignature(plan));
        writer.value(requestTarget.pathVars());
        writer.value(requestTarget.queryParams());

        Map<String, Object> selectedParameters = new TreeMap<>();
        variants.parameterIndexes().forEach((name, index) -> selectedParameters.put(
                name, selectedParameterValue(
                        plan, index, frozenArguments, resolved, serializedBodyKey, context)));
        writer.value(selectedParameters);

        Map<String, Object> selectedHeaders = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (String name : variants.headerNames()) {
            selectedHeaders.put(name.toLowerCase(Locale.ROOT), headerValues(resolved.headers(), name));
        }
        writer.value(selectedHeaders);
        writer.value(contextValues);
        return new PreparedKey(OpaqueKey.from(writer.finish()), contextValues);
    }

    static boolean selectsRequestBody(RequestPlan plan,
                                      ReactiveHttpClientProperties.CachePolicyConfig policy) {
        return plan.bodyIndex() >= 0 && variants(
                plan, null, policy, "Method " + plan.method().toGenericString())
                .parameterIndexes().containsValue(plan.bodyIndex());
    }

    static SerializedBodyKey serializedBodyKey(byte[] wireBytes) {
        byte[] bytes = wireBytes != null ? wireBytes : new byte[0];
        requireSerializedBodyLength(bytes.length);
        return new SerializedBodyKey(true, bytes);
    }

    static SerializedBodyKey absentSerializedBodyKey() {
        return new SerializedBodyKey(false, new byte[0]);
    }

    static byte[] selectedStringBodyBytes(String value, Charset charset) {
        int encodedLength = encodedLength(value, charset);
        byte[] bytes = value.getBytes(charset);
        if (bytes.length != encodedLength) {
            requireSerializedBodyLength(bytes.length);
        }
        return bytes;
    }

    static void requireSerializedBodyLength(long length) {
        if (length < 0 || length > MAX_CANONICAL_BYTES) {
            throw new IllegalStateException(
                    "Cache-selected request body exceeds " + MAX_CANONICAL_BYTES + " bytes");
        }
    }

    private static int encodedLength(String value, Charset charset) {
        CharsetEncoder encoder = charset.newEncoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE);
        CharBuffer input = CharBuffer.wrap(value);
        ByteBuffer output = ByteBuffer.allocate(8192);
        long length = 0;
        while (true) {
            CoderResult result = encoder.encode(input, output, true);
            length = addEncodedBytes(length, output);
            if (result.isUnderflow()) {
                break;
            }
            if (!result.isOverflow()) {
                throw new IllegalStateException("Unable to measure cache-selected request body encoding");
            }
        }
        while (true) {
            CoderResult result = encoder.flush(output);
            length = addEncodedBytes(length, output);
            if (result.isUnderflow()) {
                break;
            }
            if (!result.isOverflow()) {
                throw new IllegalStateException("Unable to measure cache-selected request body encoding");
            }
        }
        return (int) length;
    }

    private static long addEncodedBytes(long current, ByteBuffer output) {
        long updated = current + output.position();
        requireSerializedBodyLength(updated);
        output.clear();
        return updated;
    }

    private static void validateRequestTargetContainers(RequestPlan plan, Object[] arguments) {
        if (arguments == null) {
            return;
        }
        FreezeBudget budget = new FreezeBudget();
        for (RequestPlan.NamedArgumentBinding binding : plan.pathVars()) {
            if (binding.argumentIndex() < arguments.length) {
                validateRequestTargetContainer(
                        arguments[binding.argumentIndex()], 0,
                        "path parameter '" + binding.name() + "'", budget);
            }
        }
        for (RequestPlan.NamedArgumentBinding binding : plan.queryParams()) {
            if (binding.argumentIndex() >= arguments.length) {
                continue;
            }
            Object value = arguments[binding.argumentIndex()];
            if (value instanceof Collection<?> collection) {
                for (Object element : collection) {
                    budget.consume(1, "query parameter '" + binding.name() + "'");
                    validateRequestTargetContainer(
                            element, 0, "query parameter '" + binding.name() + "' element", budget);
                }
            } else if (value != null && value.getClass().isArray()) {
                int length = Array.getLength(value);
                budget.consume(length, "query parameter '" + binding.name() + "'");
                for (int index = 0; index < length; index++) {
                    validateRequestTargetContainer(
                            Array.get(value, index), 0,
                            "query parameter '" + binding.name() + "' element", budget);
                }
            } else {
                validateRequestTargetContainer(
                        value, 0, "query parameter '" + binding.name() + "'", budget);
            }
        }
    }

    private static void validateRequestTargetContainer(
            Object value, int depth, String context, FreezeBudget budget) {
        if (value == null) {
            return;
        }
        if (depth > MAX_DEPTH) {
            throw invalid(context, "request-target projection nesting exceeds " + MAX_DEPTH);
        }
        if (value instanceof Collection<?> collection) {
            rejectCustomContainerToString(value.getClass(), context);
            for (Object element : collection) {
                budget.consume(1, context);
                validateRequestTargetContainer(element, depth + 1, context + " element", budget);
            }
        } else if (value instanceof Map<?, ?> map) {
            rejectCustomContainerToString(value.getClass(), context);
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                budget.consume(1, context);
                validateRequestTargetContainer(entry.getKey(), depth + 1, context + " map key", budget);
                validateRequestTargetContainer(entry.getValue(), depth + 1, context + " map value", budget);
            }
        } else if (value instanceof Optional<?> optional) {
            if (optional.isPresent()) {
                budget.consume(1, context);
                validateRequestTargetContainer(optional.orElseThrow(), depth + 1, context + " optional", budget);
            }
        }
    }

    private static void rejectCustomContainerToString(Class<?> type, String context) {
        try {
            Class<?> declaringType = type.getMethod("toString").getDeclaringClass();
            if (declaringType != AbstractCollection.class
                    && declaringType != AbstractMap.class) {
                throw invalid(context, "custom container toString() cannot be bounded for request-target use");
            }
        } catch (NoSuchMethodException ex) {
            throw invalid(context, "cannot inspect container toString() for request-target use");
        }
    }

    private static boolean hasSnapshottedRequestTarget(RequestArgumentResolver.ResolvedArgs resolved) {
        return resolved.pathVars().values().stream().allMatch(String.class::isInstance)
                && resolved.queryParams().values().stream()
                .flatMap(Collection::stream)
                .allMatch(String.class::isInstance);
    }

    private static VariantSelection variants(RequestPlan plan,
                                             ReactiveHttpClientProperties.ClientConfig clientConfig,
                                             ReactiveHttpClientProperties.CachePolicyConfig policy,
                                             String context) {
        if (policy == null) {
            return VariantSelection.EMPTY;
        }
        Map<String, Integer> declaredParameters = new LinkedHashMap<>();
        for (RequestPlan.NamedArgumentBinding binding : plan.cacheKeyParams()) {
            if (declaredParameters.putIfAbsent(binding.name(), binding.argumentIndex()) != null) {
                throw invalid(context, "duplicate @CacheKey label '" + binding.name() + "'");
            }
        }
        Map<String, Integer> selectedParameters = new TreeMap<>();
        for (String name : normalized(policy.getVaryByParameters(), "vary-by-parameters", false, context)) {
            Integer index = declaredParameters.get(name);
            if (index == null) {
                throw invalid(context, "unknown vary-by-parameters name '" + name
                        + "'; declare a matching @CacheKey on a method parameter");
            }
            selectedParameters.put(name, index);
        }

        Set<String> availableHeaders = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        plan.headerParams().forEach(binding -> availableHeaders.add(binding.name()));
        plan.idempotencyKeyParams().forEach(binding -> availableHeaders.add(binding.name()));
        availableHeaders.add(idempotencyHeaderName(plan));
        if (clientConfig != null && clientConfig.getDefaultHeaders() != null) {
            availableHeaders.addAll(clientConfig.getDefaultHeaders().keySet());
        }
        List<String> headers = normalized(policy.getVaryByHeaders(), "vary-by-headers", true, context);
        if (clientConfig != null) {
            for (String header : headers) {
                RequestArgumentResolver.validateHeaderName(header);
                if (!availableHeaders.contains(header)) {
                    throw invalid(context, "unknown vary-by-headers name '" + header
                            + "'; it is not a named @HeaderParam, @IdempotencyKey, or default header");
                }
            }
        }
        List<String> contextNames = normalized(policy.getVaryByContext(), "vary-by-context", false, context);
        return new VariantSelection(Map.copyOf(selectedParameters), List.copyOf(headers), List.copyOf(contextNames));
    }

    private static List<String> normalized(List<String> values,
                                           String property,
                                           boolean ignoreCase,
                                           String context) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        Set<String> seen = ignoreCase
                ? new TreeSet<>(String.CASE_INSENSITIVE_ORDER)
                : new HashSet<>();
        List<String> normalized = new ArrayList<>(values.size());
        for (String value : values) {
            if (value == null || value.isBlank()) {
                throw invalid(context, property + " must not contain blank names");
            }
            String name = value.trim();
            if (!seen.add(name)) {
                throw invalid(context, property + " contains duplicate name '" + name + "'");
            }
            normalized.add(name);
        }
        normalized.sort(ignoreCase ? String.CASE_INSENSITIVE_ORDER : Comparator.naturalOrder());
        return normalized;
    }

    static NormalizedVariants normalizedVariants(
            ReactiveHttpClientProperties.CachePolicyConfig policy) {
        if (policy == null) {
            return NormalizedVariants.EMPTY;
        }
        String context = "Effective cache policy";
        List<String> parameters = normalized(
                policy.getVaryByParameters(), "vary-by-parameters", false, context);
        List<String> headers = normalized(
                        policy.getVaryByHeaders(), "vary-by-headers", true, context).stream()
                .map(name -> name.toLowerCase(Locale.ROOT))
                .toList();
        List<String> contexts = normalized(
                policy.getVaryByContext(), "vary-by-context", false, context);
        return new NormalizedVariants(parameters, headers, contexts, policy.isSharedResponse());
    }

    private static boolean hasUnpartitionedNamedHeaders(RequestPlan plan, VariantSelection variants) {
        Set<String> selected = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        selected.addAll(variants.headerNames());
        Set<Integer> selectedParameters = new HashSet<>(variants.parameterIndexes().values());
        return plan.headerParams().stream()
                .anyMatch(binding -> !selected.contains(binding.name())
                        && !selectedParameters.contains(binding.argumentIndex()))
                || plan.idempotencyKeyParams().stream()
                .anyMatch(binding -> !selected.contains(binding.name()))
                || !selected.contains(idempotencyHeaderName(plan));
    }

    private static boolean hasAuthenticatedResponsePartition(RequestPlan plan, VariantSelection variants) {
        if (!variants.parameterIndexes().isEmpty() || !variants.contextNames().isEmpty()) {
            return true;
        }
        String idempotencyHeader = idempotencyHeaderName(plan);
        return variants.headerNames().stream()
                .anyMatch(name -> !name.equalsIgnoreCase(idempotencyHeader));
    }

    private static String idempotencyHeaderName(RequestPlan plan) {
        if (plan.generatedIdempotencyKeyHeader() != null
                && !plan.generatedIdempotencyKeyHeader().isBlank()) {
            return plan.generatedIdempotencyKeyHeader();
        }
        return plan.idempotencyKeyParams().stream()
                .map(RequestPlan.NamedArgumentBinding::name)
                .filter(name -> name != null && !name.isBlank())
                .findFirst()
                .orElse(IDEMPOTENCY_KEY_HEADER);
    }

    private static Set<Integer> requestTargetArgumentIndexes(RequestPlan plan) {
        Set<Integer> indexes = new LinkedHashSet<>();
        plan.pathVars().forEach(binding -> indexes.add(binding.argumentIndex()));
        plan.queryParams().forEach(binding -> indexes.add(binding.argumentIndex()));
        return indexes;
    }

    private static Set<Integer> selectedHeaderArgumentIndexes(
            RequestPlan plan, VariantSelection variants) {
        Set<String> selected = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        selected.addAll(variants.headerNames());
        Set<Integer> selectedParameters = new HashSet<>(variants.parameterIndexes().values());
        Set<Integer> indexes = new LinkedHashSet<>();
        plan.headerParams().stream()
                .filter(binding -> selected.contains(binding.name())
                        || selectedParameters.contains(binding.argumentIndex()))
                .forEach(binding -> indexes.add(binding.argumentIndex()));
        plan.idempotencyKeyParams().stream()
                .filter(binding -> selected.contains(binding.name())
                        || selectedParameters.contains(binding.argumentIndex()))
                .forEach(binding -> indexes.add(binding.argumentIndex()));
        return indexes;
    }

    private static void validateRequestTargetTypes(RequestPlan plan, String context) {
        for (RequestPlan.NamedArgumentBinding binding : plan.pathVars()) {
            int index = binding.argumentIndex();
            if (index >= 0 && index < plan.parameterTypes().size()
                    && containsArrayType(plan.parameterTypes().get(index))) {
                throw invalid(context, "array-valued path parameters cannot preserve a stable "
                        + "String.valueOf wire projection; use an explicitly formatted scalar value");
            }
        }
        for (RequestPlan.NamedArgumentBinding binding : plan.queryParams()) {
            int index = binding.argumentIndex();
            if (index >= 0 && index < plan.parameterTypes().size()
                    && containsNestedQueryArray(plan.parameterTypes().get(index))) {
                throw invalid(context, "arrays nested inside query parameter values cannot preserve a stable "
                        + "String.valueOf wire projection; use flattened values or explicitly formatted scalars");
            }
        }
    }

    private static boolean containsNestedQueryArray(Type type) {
        if (type instanceof GenericArrayType arrayType) {
            return containsArrayType(arrayType.getGenericComponentType());
        }
        if (type instanceof Class<?> clazz && clazz.isArray()) {
            return containsArrayType(clazz.getComponentType());
        }
        return containsArrayType(type);
    }

    private static boolean containsArrayType(Type type) {
        if (type instanceof GenericArrayType || type instanceof Class<?> clazz && clazz.isArray()) {
            return true;
        }
        if (type instanceof ParameterizedType parameterizedType) {
            if (containsArrayType(parameterizedType.getRawType())) {
                return true;
            }
            if (parameterizedType.getOwnerType() != null
                    && containsArrayType(parameterizedType.getOwnerType())) {
                return true;
            }
            return Arrays.stream(parameterizedType.getActualTypeArguments())
                    .anyMatch(CacheKeyContract::containsArrayType);
        }
        return false;
    }

    private static void validateType(Type type, String context, int depth) {
        if (depth > MAX_DEPTH) {
            throw invalid(context, "cache-key type nesting exceeds " + MAX_DEPTH);
        }
        if (type instanceof TypeVariable<?> || type instanceof WildcardType) {
            throw invalid(context, "unresolved type " + type.getTypeName() + " cannot be frozen");
        }
        if (type instanceof GenericArrayType arrayType) {
            validateArrayComponentSnapshotCompatibility(arrayType.getGenericComponentType(), context);
            validateType(arrayType.getGenericComponentType(), context, depth + 1);
            return;
        }
        if (type instanceof ParameterizedType parameterizedType) {
            Class<?> raw = rawClass(type);
            if (raw == null || (!List.class.isAssignableFrom(raw)
                    && !Set.class.isAssignableFrom(raw)
                    && !Map.class.isAssignableFrom(raw)
                    && !Optional.class.isAssignableFrom(raw))) {
                if (raw != null && raw.isRecord()) {
                    validateRecord(parameterizedType, raw, context, depth);
                    return;
                }
                throw invalid(context, "unsupported parameterized cache-key type " + type.getTypeName());
            }
            for (Type argument : parameterizedType.getActualTypeArguments()) {
                validateType(argument, context, depth + 1);
            }
            return;
        }
        Class<?> raw = rawClass(type);
        if (raw == null || raw == Object.class
                || Publisher.class.isAssignableFrom(raw)
                || DataBuffer.class.isAssignableFrom(raw)
                || Resource.class.isAssignableFrom(raw)
                || InputStream.class.isAssignableFrom(raw)
                || Reader.class.isAssignableFrom(raw)
                || ReadableByteChannel.class.isAssignableFrom(raw)) {
            throw invalid(context, "unsupported cache-key type " + (type != null ? type.getTypeName() : "null"));
        }
        if (raw.isPrimitive() || IMMUTABLE_SCALARS.contains(raw) || raw.isEnum()) {
            return;
        }
        if (raw.isArray()) {
            validateArrayComponentSnapshotCompatibility(raw.getComponentType(), context);
            validateType(raw.getComponentType(), context, depth + 1);
            return;
        }
        if (raw.isRecord()) {
            validateRecord(raw, raw, context, depth);
            return;
        }
        if (Collection.class.isAssignableFrom(raw) || Map.class.isAssignableFrom(raw)
                || Optional.class.isAssignableFrom(raw)) {
            throw invalid(context, "raw container type " + raw.getTypeName() + " cannot be frozen safely");
        }
        throw invalid(context, "mutable or unsupported type " + raw.getTypeName()
                + " cannot be copied safely; use immutable scalars, arrays, typed lists/sets/maps, enums, or records");
    }

    private static void validateArrayComponentSnapshotCompatibility(Type componentType, String context) {
        Class<?> component = rawClass(componentType);
        if (component == null) {
            return;
        }
        boolean compatible = !List.class.isAssignableFrom(component) || component.isAssignableFrom(List.class);
        compatible &= !Set.class.isAssignableFrom(component)
                || component.isAssignableFrom(IdentityPreservingSet.class);
        compatible &= !Map.class.isAssignableFrom(component)
                || component.isAssignableFrom(EntryPreservingMap.class);
        if (!compatible) {
            throw invalid(context, "array component type " + component.getTypeName()
                    + " cannot hold the defensive cache-key snapshot; use a List, Set, or Map component type");
        }
    }

    private static void validateRecord(Type declaredType,
                                       Class<?> recordType,
                                       String context,
                                       int depth) {
        validateCanonicalRecordAccessors(recordType, context);
        Map<TypeVariable<?>, Type> bindings = recordBindings(declaredType, recordType);
        for (RecordComponent component : recordType.getRecordComponents()) {
            Type type = resolveType(component.getGenericType(), bindings);
            if (type instanceof ParameterizedType
                    || type instanceof GenericArrayType
                    || type instanceof Class<?> clazz && clazz.isArray()) {
                Class<?> componentRaw = rawClass(type);
                if (componentRaw == null || !componentRaw.isRecord()) {
                    throw invalid(context, "record component '" + component.getName()
                            + "' is mutable; records used as cache-key inputs must contain "
                            + "immutable scalar/record values");
                }
            }
            validateType(type, context + " record component '" + component.getName() + "'", depth + 1);
        }
    }

    private static Map<TypeVariable<?>, Type> recordBindings(Type declaredType, Class<?> recordType) {
        if (!(declaredType instanceof ParameterizedType parameterizedType)) {
            return Map.of();
        }
        TypeVariable<?>[] variables = recordType.getTypeParameters();
        Type[] arguments = parameterizedType.getActualTypeArguments();
        Map<TypeVariable<?>, Type> bindings = new HashMap<>();
        for (int index = 0; index < Math.min(variables.length, arguments.length); index++) {
            bindings.put(variables[index], arguments[index]);
        }
        return bindings;
    }

    private static Type resolveType(Type type, Map<TypeVariable<?>, Type> bindings) {
        if (type instanceof TypeVariable<?> variable) {
            Type resolved = bindings.get(variable);
            return resolved != null && resolved != variable ? resolveType(resolved, bindings) : variable;
        }
        if (type instanceof ParameterizedType parameterizedType) {
            Type owner = parameterizedType.getOwnerType() != null
                    ? resolveType(parameterizedType.getOwnerType(), bindings)
                    : null;
            Type[] arguments = Arrays.stream(parameterizedType.getActualTypeArguments())
                    .map(argument -> resolveType(argument, bindings))
                    .toArray(Type[]::new);
            return new ResolvedParameterizedType(owner, parameterizedType.getRawType(), arguments);
        }
        if (type instanceof GenericArrayType arrayType) {
            Type component = resolveType(arrayType.getGenericComponentType(), bindings);
            return component instanceof Class<?> componentClass
                    ? Array.newInstance(componentClass, 0).getClass()
                    : new ResolvedGenericArrayType(component);
        }
        return type;
    }

    private static Object freeze(Object value, int depth, String context, FreezeBudget budget) {
        if (value == null) {
            return null;
        }
        if (depth > MAX_DEPTH) {
            throw invalid(context, "cache-key value nesting exceeds " + MAX_DEPTH);
        }
        Class<?> type = value.getClass();
        if (type.isPrimitive() || IMMUTABLE_SCALARS.contains(type) || value instanceof Enum<?>) {
            return value;
        }
        if (type.isArray()) {
            int length = Array.getLength(value);
            budget.consume(length, context);
            Class<?> componentType = type.getComponentType();
            Object copy = Array.newInstance(componentType, length);
            for (int i = 0; i < length; i++) {
                Object frozen = freeze(Array.get(value, i), depth + 1, context + "[" + i + "]", budget);
                if (!componentType.isPrimitive() && frozen != null && !componentType.isInstance(frozen)) {
                    throw invalid(context + "[" + i + "]", "runtime array component type "
                            + componentType.getTypeName() + " cannot hold defensive snapshot type "
                            + frozen.getClass().getTypeName());
                }
                Array.set(copy, i, frozen);
            }
            return copy;
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>();
            int index = 0;
            for (Object element : list) {
                budget.consume(1, context);
                copy.add(freeze(element, depth + 1, context + "[" + index + "]", budget));
                index++;
            }
            return Collections.unmodifiableList(copy);
        }
        if (value instanceof Set<?> set) {
            List<Object> values = new ArrayList<>();
            for (Object element : set) {
                budget.consume(1, context);
                values.add(freeze(element, depth + 1, context + " set element", budget));
            }
            return new IdentityPreservingSet(values);
        }
        if (value instanceof Map<?, ?> map) {
            List<Map.Entry<Object, Object>> entries = new ArrayList<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                budget.consume(1, context);
                Object key = freeze(entry.getKey(), depth + 1, context + " map key", budget);
                Object mapped = freeze(entry.getValue(), depth + 1, context + " map value", budget);
                entries.add(new AbstractMap.SimpleImmutableEntry<>(key, mapped));
            }
            return new EntryPreservingMap(entries, map instanceof IdentityHashMap<?, ?>);
        }
        if (value instanceof Optional<?> optional) {
            if (optional.isPresent()) {
                budget.consume(1, context);
            }
            return optional.map(item -> freeze(item, depth + 1, context + " optional", budget));
        }
        if (type.isRecord()) {
            validateCanonicalRecordAccessors(type, context);
            RecordComponent[] components = type.getRecordComponents();
            budget.consume(components.length, context);
            for (RecordComponent component : components) {
                Object componentValue = recordComponentValue(component, value, context);
                Object frozenComponent = freeze(componentValue, depth + 1,
                        context + " record component '" + component.getName() + "'", budget);
                if (frozenComponent != componentValue
                        && (componentValue == null || !componentValue.getClass().isRecord())) {
                    throw invalid(context, "record component '" + component.getName()
                            + "' is mutable and cannot be copied without changing the record");
                }
            }
            return value;
        }
        throw invalid(context, "runtime value type " + type.getTypeName() + " cannot be copied safely");
    }

    private static void validateCanonicalRecordAccessors(Class<?> recordType, String context) {
        CanonicalRecordAccessors accessors = CANONICAL_RECORD_ACCESSORS.get(recordType);
        if (!accessors.classBytesAvailable()) {
            throw invalid(context, "cannot inspect record accessors for " + recordType.getName()
                    + "; register the record class resource for native cache-key use");
        }
        for (RecordComponent component : recordType.getRecordComponents()) {
            String signature = accessorSignature(component.getAccessor());
            if (!accessors.signatures().contains(signature)) {
                throw invalid(context, "record component '" + component.getName()
                        + "' must use a canonical field accessor so its captured cache-key value cannot change");
            }
        }
    }

    private static CanonicalRecordAccessors inspectCanonicalRecordAccessors(Class<?> recordType) {
        String resource = "/" + recordType.getName().replace('.', '/') + ".class";
        try (InputStream input = recordType.getResourceAsStream(resource)) {
            if (input == null) {
                return new CanonicalRecordAccessors(false, Set.of(), false);
            }
            Map<String, RecordComponent> expected = new HashMap<>();
            for (RecordComponent component : recordType.getRecordComponents()) {
                expected.put(accessorSignature(component.getAccessor()), component);
            }
            Set<String> canonical = new HashSet<>();
            boolean[] defaultToString = {false};
            ClassReader reader = new ClassReader(input);
            reader.accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(int access,
                                                 String name,
                                                 String descriptor,
                                                 String signature,
                                                 String[] exceptions) {
                    String methodSignature = name + descriptor;
                    RecordComponent component = expected.get(methodSignature);
                    if (component != null) {
                        return new CanonicalRecordAccessorVisitor(
                                recordType, component, methodSignature, canonical);
                    }
                    return "toString".equals(name) && "()Ljava/lang/String;".equals(descriptor)
                            ? new DefaultRecordToStringVisitor(recordType, defaultToString)
                            : null;
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return new CanonicalRecordAccessors(true, Set.copyOf(canonical), defaultToString[0]);
        } catch (IOException ex) {
            return new CanonicalRecordAccessors(false, Set.of(), false);
        }
    }

    private static String accessorSignature(Method accessor) {
        return accessor.getName() + org.springframework.asm.Type.getMethodDescriptor(accessor);
    }

    private static int returnOpcode(Class<?> type) {
        if (type == long.class) {
            return Opcodes.LRETURN;
        }
        if (type == float.class) {
            return Opcodes.FRETURN;
        }
        if (type == double.class) {
            return Opcodes.DRETURN;
        }
        return type.isPrimitive() ? Opcodes.IRETURN : Opcodes.ARETURN;
    }

    private static final class CanonicalRecordAccessorVisitor extends MethodVisitor {
        private final String owner;
        private final RecordComponent component;
        private final String signature;
        private final Set<String> canonical;
        private int step;
        private boolean valid = true;

        private CanonicalRecordAccessorVisitor(Class<?> recordType,
                                               RecordComponent component,
                                               String signature,
                                               Set<String> canonical) {
            super(Opcodes.ASM9);
            this.owner = org.springframework.asm.Type.getInternalName(recordType);
            this.component = component;
            this.signature = signature;
            this.canonical = canonical;
        }

        @Override
        public void visitVarInsn(int opcode, int varIndex) {
            valid &= step == 0 && opcode == Opcodes.ALOAD && varIndex == 0;
            step++;
        }

        @Override
        public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
            valid &= step == 1
                    && opcode == Opcodes.GETFIELD
                    && this.owner.equals(owner)
                    && component.getName().equals(name)
                    && org.springframework.asm.Type.getDescriptor(component.getType()).equals(descriptor);
            step++;
        }

        @Override
        public void visitInsn(int opcode) {
            valid &= step == 2 && opcode == returnOpcode(component.getType());
            step++;
        }

        @Override
        public void visitIntInsn(int opcode, int operand) {
            valid = false;
        }

        @Override
        public void visitTypeInsn(int opcode, String type) {
            valid = false;
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
            valid = false;
        }

        @Override
        public void visitInvokeDynamicInsn(String name, String descriptor,
                                           org.springframework.asm.Handle bootstrapMethodHandle,
                                           Object... bootstrapMethodArguments) {
            valid = false;
        }

        @Override
        public void visitJumpInsn(int opcode, org.springframework.asm.Label label) {
            valid = false;
        }

        @Override
        public void visitLdcInsn(Object value) {
            valid = false;
        }

        @Override
        public void visitIincInsn(int varIndex, int increment) {
            valid = false;
        }

        @Override
        public void visitTableSwitchInsn(int min, int max, org.springframework.asm.Label dflt,
                                         org.springframework.asm.Label... labels) {
            valid = false;
        }

        @Override
        public void visitLookupSwitchInsn(org.springframework.asm.Label dflt, int[] keys,
                                          org.springframework.asm.Label[] labels) {
            valid = false;
        }

        @Override
        public void visitMultiANewArrayInsn(String descriptor, int dimensions) {
            valid = false;
        }

        @Override
        public void visitEnd() {
            if (valid && step == 3) {
                canonical.add(signature);
            }
        }
    }

    private static final class DefaultRecordToStringVisitor extends MethodVisitor {
        private final String descriptor;
        private final boolean[] defaultToString;
        private int step;
        private boolean valid = true;

        private DefaultRecordToStringVisitor(Class<?> recordType, boolean[] defaultToString) {
            super(Opcodes.ASM9);
            this.descriptor = "(L" + org.springframework.asm.Type.getInternalName(recordType)
                    + ";)Ljava/lang/String;";
            this.defaultToString = defaultToString;
        }

        @Override
        public void visitVarInsn(int opcode, int varIndex) {
            valid &= step == 0 && opcode == Opcodes.ALOAD && varIndex == 0;
            step++;
        }

        @Override
        public void visitInvokeDynamicInsn(String name, String descriptor,
                                           org.springframework.asm.Handle bootstrapMethodHandle,
                                           Object... bootstrapMethodArguments) {
            valid &= step == 1
                    && "toString".equals(name)
                    && this.descriptor.equals(descriptor)
                    && "java/lang/runtime/ObjectMethods".equals(bootstrapMethodHandle.getOwner())
                    && "bootstrap".equals(bootstrapMethodHandle.getName());
            step++;
        }

        @Override
        public void visitInsn(int opcode) {
            valid &= step == 2 && opcode == Opcodes.ARETURN;
            step++;
        }

        @Override
        public void visitEnd() {
            if (valid && step == 3) {
                defaultToString[0] = true;
            }
        }
    }

    private record CanonicalRecordAccessors(
            boolean classBytesAvailable, Set<String> signatures, boolean defaultToString) {
    }

    private static Object selectedParameterValue(RequestPlan plan,
                                                 int index,
                                                 Object[] frozenArguments,
                                                 RequestArgumentResolver.ResolvedArgs resolved,
                                                 SerializedBodyKey serializedBodyKey,
                                                 String context) {
        for (RequestPlan.NamedArgumentBinding binding : plan.headerParams()) {
            if (binding.argumentIndex() == index) {
                return headerValues(resolved.headers(), binding.name());
            }
        }
        for (RequestPlan.NamedArgumentBinding binding : plan.idempotencyKeyParams()) {
            if (binding.argumentIndex() == index) {
                return headerValues(resolved.headers(), binding.name());
            }
        }
        if (plan.bodyIndex() == index) {
            if (serializedBodyKey == null) {
                throw invalid(context, "selected request bodies require their serialized wire bytes");
            }
            return serializedBodyKey;
        }
        return index < frozenArguments.length ? frozenArguments[index] : null;
    }

    private static List<String> headerValues(Map<String, List<String>> headers, String requestedName) {
        List<String> values = new ArrayList<>();
        headers.forEach((name, headerValues) -> {
            if (name.equalsIgnoreCase(requestedName) && headerValues != null) {
                values.addAll(headerValues);
            }
        });
        return values.isEmpty() ? null : List.copyOf(values);
    }

    private static Object recordComponentValue(RecordComponent component, Object record, String context) {
        Method accessor = component.getAccessor();
        boolean accessible;
        try {
            accessible = accessor.canAccess(record) || accessor.trySetAccessible();
        } catch (RuntimeException ex) {
            throw invalid(context, "cannot access record component '" + component.getName() + "'");
        }
        if (!accessible) {
            throw invalid(context, "cannot access record component '" + component.getName() + "'");
        }
        try {
            return accessor.invoke(record);
        } catch (ReflectiveOperationException | SecurityException ex) {
            throw invalid(context, "cannot read record component '" + component.getName() + "'");
        }
    }

    private static String resolvedMethodSignature(RequestPlan plan) {
        StringJoiner signature = new StringJoiner(",", plan.method().getName() + "(", ")->"
                + (plan.responseType() != null ? plan.responseType().getTypeName() : "raw"));
        plan.parameterTypes().forEach(type -> signature.add(type.getTypeName()));
        return signature.toString();
    }

    private static byte[] canonicalBytes(Object value, ByteBudget budget) {
        CanonicalWriter writer = new CanonicalWriter(budget);
        writer.value(value);
        return writer.finish();
    }

    private static int compareBytes(byte[] left, byte[] right) {
        return Arrays.compareUnsigned(left, right);
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

    private static String context(Class<?> clientInterface,
                                  String clientName,
                                  RequestPlan plan,
                                  EffectiveCachePolicy.Selection selection) {
        return "Reactive HTTP client '" + clientName + "' concreteClient=" + clientInterface.getName()
                + " method=" + plan.method().toGenericString() + " cachePolicy='"
                + selection.policyName() + "'";
    }

    private static IllegalStateException invalid(String context, String reason) {
        return new IllegalStateException(context + " has an invalid cache key/variant contract: " + reason);
    }

    private static final class FreezeBudget {
        private int elements;

        private void consume(int count, String context) {
            if (count < 0 || count > MAX_ELEMENTS - elements) {
                throw invalid(context, "cumulative element count exceeds maximum " + MAX_ELEMENTS);
            }
            elements += count;
        }
    }

    private static final class IdentityPreservingSet<E> extends AbstractSet<E> {
        private final List<E> values;

        private IdentityPreservingSet(List<E> values) {
            this.values = Collections.unmodifiableList(new ArrayList<>(values));
        }

        @Override
        public Iterator<E> iterator() {
            return values.iterator();
        }

        @Override
        public int size() {
            return values.size();
        }

        @Override
        public boolean contains(Object candidate) {
            return values.stream().anyMatch(value -> value == candidate);
        }
    }

    private static final class EntryPreservingMap extends AbstractMap<Object, Object> {
        private final List<Map.Entry<Object, Object>> entries;
        private final Set<Map.Entry<Object, Object>> entrySet;
        private final boolean identityKeys;

        private EntryPreservingMap(List<Map.Entry<Object, Object>> entries, boolean identityKeys) {
            this.entries = Collections.unmodifiableList(new ArrayList<>(entries));
            this.entrySet = new IdentityPreservingSet(new ArrayList<>(entries));
            this.identityKeys = identityKeys;
        }

        @Override
        public Set<Map.Entry<Object, Object>> entrySet() {
            return entrySet;
        }

        @Override
        public boolean containsKey(Object key) {
            return identityKeys
                    ? entries.stream().anyMatch(entry -> entry.getKey() == key)
                    : super.containsKey(key);
        }

        @Override
        public Object get(Object key) {
            if (!identityKeys) {
                return super.get(key);
            }
            for (Map.Entry<Object, Object> entry : entries) {
                if (entry.getKey() == key) {
                    return entry.getValue();
                }
            }
            return null;
        }
    }

    private static final class ByteBudget {
        private int remaining = MAX_CANONICAL_BYTES;

        private void require(long count) {
            if (count < 0 || count > remaining) {
                throw new IllegalStateException(
                        "Cache key material exceeds " + MAX_CANONICAL_BYTES + " bytes");
            }
        }

        private void consume(int count) {
            require(count);
            remaining -= count;
        }
    }

    private static final class ResolvedParameterizedType implements ParameterizedType {
        private final Type ownerType;
        private final Type rawType;
        private final Type[] arguments;

        private ResolvedParameterizedType(Type ownerType, Type rawType, Type[] arguments) {
            this.ownerType = ownerType;
            this.rawType = rawType;
            this.arguments = arguments.clone();
        }

        @Override
        public Type[] getActualTypeArguments() {
            return arguments.clone();
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
        public String getTypeName() {
            return rawType.getTypeName() + "<" + Arrays.stream(arguments)
                    .map(Type::getTypeName)
                    .reduce((left, right) -> left + ", " + right)
                    .orElse("") + ">";
        }
    }

    private record ResolvedGenericArrayType(Type genericComponentType) implements GenericArrayType {
        @Override
        public Type getGenericComponentType() {
            return genericComponentType;
        }
    }

    record PreparedKey(OpaqueKey key, Map<String, Object> contextValues) {

        Context writeContext(Context context) {
            Context updated = context;
            for (Map.Entry<String, Object> entry : contextValues.entrySet()) {
                if (entry.getValue() != null) {
                    updated = updated.put(entry.getKey(), entry.getValue());
                }
            }
            return updated;
        }
    }

    static final class OpaqueKey {
        private final byte[] digest;

        private OpaqueKey(byte[] digest) {
            this.digest = digest;
        }

        static OpaqueKey from(byte[] canonical) {
            try {
                byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical);
                Arrays.fill(canonical, (byte) 0);
                return new OpaqueKey(digest);
            } catch (NoSuchAlgorithmException ex) {
                throw new IllegalStateException("SHA-256 is not available", ex);
            }
        }

        @Override
        public boolean equals(Object other) {
            return this == other || other instanceof OpaqueKey key
                    && MessageDigest.isEqual(digest, key.digest);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(digest);
        }

        @Override
        public String toString() {
            return "OpaqueCacheKey";
        }
    }

    private record VariantSelection(Map<String, Integer> parameterIndexes,
                                    List<String> headerNames,
                                    List<String> contextNames) {
        private static final VariantSelection EMPTY =
                new VariantSelection(Map.of(), List.of(), List.of());
    }

    record NormalizedVariants(List<String> parameterNames,
                              List<String> headerNames,
                              List<String> contextNames,
                              boolean sharedResponse) {
        private static final NormalizedVariants EMPTY =
                new NormalizedVariants(List.of(), List.of(), List.of(), false);
    }

    static final class SerializedBodyKey {
        private final boolean present;
        private final byte[] wireBytes;

        private SerializedBodyKey(boolean present, byte[] wireBytes) {
            this.present = present;
            this.wireBytes = wireBytes.clone();
        }
    }

    private static final class RequestTargetProjector {
        private final ByteBudget budget = new ByteBudget();
        private final String description;

        private RequestTargetProjector(String description) {
            this.description = description;
        }

        private String project(Object value) {
            StringBuilder projection = new StringBuilder();
            append(projection, value, 0);
            return projection.toString();
        }

        private Object projectHeaderArgument(Object value) {
            if (value == null) {
                return null;
            }
            if (value instanceof Collection<?> collection) {
                List<String> projected = new ArrayList<>();
                for (Object element : collection) {
                    if (element != null) {
                        projected.add(project(element));
                    }
                }
                return List.copyOf(projected);
            }
            if (value.getClass().isArray()) {
                int length = Array.getLength(value);
                List<String> projected = new ArrayList<>(length);
                for (int index = 0; index < length; index++) {
                    Object element = Array.get(value, index);
                    if (element != null) {
                        projected.add(project(element));
                    }
                }
                return List.copyOf(projected);
            }
            return project(value);
        }

        private void append(StringBuilder projection, Object value, int depth) {
            if (depth > MAX_DEPTH) {
                throw new IllegalStateException(description + " nesting exceeds " + MAX_DEPTH);
            }
            if (value == null) {
                appendText(projection, "null");
                return;
            }
            if (value instanceof Collection<?> collection) {
                appendText(projection, "[");
                Iterator<?> iterator = collection.iterator();
                while (iterator.hasNext()) {
                    append(projection, iterator.next(), depth + 1);
                    if (iterator.hasNext()) {
                        appendText(projection, ", ");
                    }
                }
                appendText(projection, "]");
                return;
            }
            if (value instanceof Map<?, ?> map) {
                appendText(projection, "{");
                Iterator<? extends Map.Entry<?, ?>> iterator = map.entrySet().iterator();
                while (iterator.hasNext()) {
                    Map.Entry<?, ?> entry = iterator.next();
                    append(projection, entry.getKey(), depth + 1);
                    appendText(projection, "=");
                    append(projection, entry.getValue(), depth + 1);
                    if (iterator.hasNext()) {
                        appendText(projection, ", ");
                    }
                }
                appendText(projection, "}");
                return;
            }
            if (value instanceof Optional<?> optional) {
                if (optional.isPresent()) {
                    appendText(projection, "Optional[");
                    append(projection, optional.orElseThrow(), depth + 1);
                    appendText(projection, "]");
                } else {
                    appendText(projection, "Optional.empty");
                }
                return;
            }
            Class<?> type = value.getClass();
            if (type.isRecord()) {
                CanonicalRecordAccessors record = CANONICAL_RECORD_ACCESSORS.get(type);
                if (!record.defaultToString()) {
                    throw new IllegalStateException("Record " + type.getName()
                            + " overrides toString(); custom request-target conversion cannot be bounded");
                }
                appendText(projection, type.getSimpleName());
                appendText(projection, "[");
                RecordComponent[] components = type.getRecordComponents();
                for (int index = 0; index < components.length; index++) {
                    RecordComponent component = components[index];
                    appendText(projection, component.getName());
                    appendText(projection, "=");
                    append(projection, recordComponentValue(
                            component, value, "Request-target record " + type.getName()), depth + 1);
                    if (index + 1 < components.length) {
                        appendText(projection, ", ");
                    }
                }
                appendText(projection, "]");
                return;
            }
            if (type.isArray()) {
                throw new IllegalStateException(
                        "Array values cannot be projected into a stable cache-selected request target");
            }
            preflightScalar(value);
            appendText(projection, String.valueOf(value));
        }

        private void preflightScalar(Object value) {
            if (value instanceof BigInteger integer) {
                budget.require(decimalLength(integer));
            } else if (value instanceof BigDecimal decimal) {
                budget.require((long) decimal.precision() + 16L);
            }
        }

        private static long decimalLength(BigInteger value) {
            if (value.signum() == 0) {
                return 1;
            }
            long digits = (long) Math.floor((value.bitLength() - 1) * Math.log10(2.0d)) + 1L;
            return digits + (value.signum() < 0 ? 1L : 0L);
        }

        private void appendText(StringBuilder projection, String value) {
            int encodedLength = CanonicalWriter.utf8Length(value);
            budget.consume(encodedLength);
            projection.append(value);
        }
    }

    private static final class CanonicalWriter {
        private final ByteBudget budget;
        private final BudgetedByteArrayOutputStream bytes;
        private final DataOutputStream output;

        private CanonicalWriter() {
            this(new ByteBudget());
        }

        private CanonicalWriter(ByteBudget budget) {
            this.budget = budget;
            this.bytes = new BudgetedByteArrayOutputStream(budget);
            this.output = new DataOutputStream(bytes);
        }

        void value(Object value) {
            try {
                writeValue(value, 0);
            } catch (IOException ex) {
                throw new IllegalStateException("Unable to encode cache key", ex);
            }
        }

        byte[] finish() {
            return bytes.toByteArray();
        }

        private void writeValue(Object value, int depth) throws IOException {
            if (depth > MAX_DEPTH) {
                throw new IllegalStateException("Cache key nesting exceeds " + MAX_DEPTH);
            }
            if (value == null) {
                output.writeByte(0);
                return;
            }
            Class<?> type = value.getClass();
            if (value instanceof SerializedBodyKey serializedBody) {
                output.writeByte(26);
                output.writeBoolean(serializedBody.present);
                rawFrame(serializedBody.wireBytes);
            } else if (value instanceof String text) {
                stringScalar(type, text);
            } else if (value instanceof Boolean booleanValue) {
                scalar(2, type, new byte[]{(byte) (booleanValue ? 1 : 0)});
            } else if (value instanceof Number number) {
                scalar(3, type, numberBytes(number, type));
            } else if (value instanceof Character character) {
                scalar(4, type, new byte[]{(byte) (character >>> 8), (byte) character.charValue()});
            } else if (value instanceof Enum<?> enumValue) {
                scalar(5, enumValue.getDeclaringClass(), enumValue.name().getBytes(StandardCharsets.UTF_8));
            } else if (value instanceof UUID uuid) {
                scalar(6, type, (uuid.getMostSignificantBits() + ":" + uuid.getLeastSignificantBits())
                        .getBytes(StandardCharsets.US_ASCII));
            } else if (IMMUTABLE_SCALARS.contains(type)) {
                scalar(7, type, scalarBytes(value));
            } else if (type.isArray()) {
                output.writeByte(20);
                text(type.getComponentType().getTypeName());
                int length = Array.getLength(value);
                output.writeInt(length);
                for (int index = 0; index < length; index++) {
                    framed(Array.get(value, index), depth + 1);
                }
            } else if (value instanceof List<?> list) {
                sequence(21, list, depth);
            } else if (value instanceof Set<?> set) {
                output.writeByte(22);
                List<byte[]> encoded = set.stream().map(item -> canonicalBytes(item, budget))
                        .sorted(CacheKeyContract::compareBytes).toList();
                output.writeInt(encoded.size());
                encoded.forEach(this::countedRawFrame);
            } else if (value instanceof Map<?, ?> map) {
                output.writeByte(23);
                List<MapEntryBytes> entries = map.entrySet().stream()
                        .map(entry -> new MapEntryBytes(
                                canonicalBytes(entry.getKey(), budget),
                                canonicalBytes(entry.getValue(), budget)))
                        .sorted(Comparator.comparing(MapEntryBytes::key, CacheKeyContract::compareBytes)
                                .thenComparing(MapEntryBytes::value, CacheKeyContract::compareBytes))
                        .toList();
                output.writeInt(entries.size());
                for (MapEntryBytes entry : entries) {
                    countedRawFrame(entry.key());
                    countedRawFrame(entry.value());
                }
            } else if (value instanceof Optional<?> optional) {
                output.writeByte(24);
                framed(optional.orElse(null), depth + 1);
            } else if (type.isRecord()) {
                output.writeByte(25);
                text(type.getName());
                RecordComponent[] components = type.getRecordComponents();
                output.writeInt(components.length);
                for (RecordComponent component : components) {
                    text(component.getName());
                    framed(recordComponentValue(component, value,
                            "Cache-key record " + type.getName()), depth + 1);
                }
            } else {
                throw new IllegalStateException("Unsupported cache key value type " + type.getName());
            }
        }

        private void sequence(int tag, Collection<?> values, int depth) throws IOException {
            output.writeByte(tag);
            output.writeInt(values.size());
            for (Object value : values) {
                framed(value, depth + 1);
            }
        }

        private void scalar(int tag, Class<?> type, byte[] value) throws IOException {
            output.writeByte(tag);
            text(type.getName());
            rawFrame(value);
        }

        private void stringScalar(Class<?> type, String value) throws IOException {
            output.writeByte(1);
            text(type.getName());
            utf8Frame(value);
        }

        private void utf8Frame(String value) throws IOException {
            int encodedLength = utf8Length(value);
            budget.require(Integer.BYTES + encodedLength);
            output.writeInt(encodedLength);
            output.write(value.getBytes(StandardCharsets.UTF_8));
        }

        private static int utf8Length(String value) {
            long length = 0;
            for (int index = 0; index < value.length(); index++) {
                char character = value.charAt(index);
                if (character < 0x80) {
                    length++;
                } else if (character < 0x800) {
                    length += 2;
                } else if (Character.isHighSurrogate(character)
                        && index + 1 < value.length()
                        && Character.isLowSurrogate(value.charAt(index + 1))) {
                    length += 4;
                    index++;
                } else if (Character.isSurrogate(character)) {
                    length++;
                } else {
                    length += 3;
                }
                if (length > MAX_CANONICAL_BYTES) {
                    throw new IllegalStateException(
                            "Cache key material exceeds " + MAX_CANONICAL_BYTES + " bytes");
                }
            }
            return (int) length;
        }

        private void framed(Object value, int depth) throws IOException {
            CanonicalWriter nested = new CanonicalWriter(budget);
            nested.writeValue(value, depth);
            countedRawFrame(nested.finish());
        }

        private void rawFrame(byte[] value) {
            try {
                output.writeInt(value.length);
                output.write(value);
            } catch (IOException ex) {
                throw new IllegalStateException(ex);
            }
        }

        private void countedRawFrame(byte[] value) {
            try {
                output.writeInt(value.length);
                bytes.writeAlreadyCounted(value);
            } catch (IOException ex) {
                throw new IllegalStateException(ex);
            }
        }

        private void text(String value) throws IOException {
            rawFrame(value.getBytes(StandardCharsets.UTF_8));
        }

        private byte[] numberBytes(Number value, Class<?> type) {
            requireScalarPayload(type, numericPayloadLength(value));
            try {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                DataOutputStream output = new DataOutputStream(bytes);
                if (value instanceof Byte byteValue) {
                    output.writeByte(byteValue);
                } else if (value instanceof Short shortValue) {
                    output.writeShort(shortValue);
                } else if (value instanceof Integer integerValue) {
                    output.writeInt(integerValue);
                } else if (value instanceof Long longValue) {
                    output.writeLong(longValue);
                } else if (value instanceof Float floatValue) {
                    output.writeInt(Float.floatToRawIntBits(floatValue));
                } else if (value instanceof Double doubleValue) {
                    output.writeLong(Double.doubleToRawLongBits(doubleValue));
                } else if (value instanceof BigInteger integer) {
                    output.write(integer.toByteArray());
                } else if (value instanceof BigDecimal decimal) {
                    output.writeInt(decimal.scale());
                    output.write(decimal.unscaledValue().toByteArray());
                } else {
                    throw new IllegalStateException("Unsupported numeric cache-key value " + value.getClass().getName());
                }
                return bytes.toByteArray();
            } catch (IOException ex) {
                throw new IllegalStateException(ex);
            }
        }

        private void requireScalarPayload(Class<?> type, long payloadLength) {
            long typeLength = utf8Length(type.getName());
            budget.require(1L + Integer.BYTES + typeLength + Integer.BYTES + payloadLength);
        }

        private static long numericPayloadLength(Number value) {
            if (value instanceof Byte) {
                return Byte.BYTES;
            }
            if (value instanceof Short) {
                return Short.BYTES;
            }
            if (value instanceof Integer || value instanceof Float) {
                return Integer.BYTES;
            }
            if (value instanceof Long || value instanceof Double) {
                return Long.BYTES;
            }
            if (value instanceof BigInteger integer) {
                return encodedBigIntegerLength(integer);
            }
            if (value instanceof BigDecimal decimal) {
                return Integer.BYTES + encodedBigIntegerLength(decimal.unscaledValue());
            }
            throw new IllegalStateException("Unsupported numeric cache-key value " + value.getClass().getName());
        }

        private static long encodedBigIntegerLength(BigInteger value) {
            return ((long) value.bitLength() + Byte.SIZE) / Byte.SIZE;
        }

        private byte[] scalarBytes(Object value) {
            try {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                DataOutputStream output = new DataOutputStream(bytes);
                if (value instanceof URI uri) {
                    String text = uri.toString();
                    requireScalarPayload(value.getClass(), utf8Length(text));
                    output.write(text.getBytes(StandardCharsets.UTF_8));
                } else if (value instanceof Instant instant) {
                    output.writeLong(instant.getEpochSecond());
                    output.writeInt(instant.getNano());
                } else if (value instanceof LocalDate date) {
                    output.writeLong(date.toEpochDay());
                } else if (value instanceof LocalTime time) {
                    output.writeLong(time.toNanoOfDay());
                } else if (value instanceof LocalDateTime dateTime) {
                    output.writeLong(dateTime.toLocalDate().toEpochDay());
                    output.writeLong(dateTime.toLocalTime().toNanoOfDay());
                } else if (value instanceof OffsetTime time) {
                    output.writeLong(time.toLocalTime().toNanoOfDay());
                    output.writeInt(time.getOffset().getTotalSeconds());
                } else if (value instanceof OffsetDateTime dateTime) {
                    output.writeLong(dateTime.toLocalDate().toEpochDay());
                    output.writeLong(dateTime.toLocalTime().toNanoOfDay());
                    output.writeInt(dateTime.getOffset().getTotalSeconds());
                } else if (value instanceof ZonedDateTime dateTime) {
                    output.writeLong(dateTime.toLocalDate().toEpochDay());
                    output.writeLong(dateTime.toLocalTime().toNanoOfDay());
                    output.writeInt(dateTime.getOffset().getTotalSeconds());
                    byte[] zone = dateTime.getZone().getId().getBytes(StandardCharsets.UTF_8);
                    output.writeInt(zone.length);
                    output.write(zone);
                } else if (value instanceof Duration duration) {
                    output.writeLong(duration.getSeconds());
                    output.writeInt(duration.getNano());
                } else if (value instanceof Period period) {
                    output.writeInt(period.getYears());
                    output.writeInt(period.getMonths());
                    output.writeInt(period.getDays());
                } else if (value instanceof BigInteger || value instanceof BigDecimal) {
                    output.write(numberBytes((Number) value, value.getClass()));
                } else {
                    throw new IllegalStateException("Unsupported immutable scalar " + value.getClass().getName());
                }
                return bytes.toByteArray();
            } catch (IOException ex) {
                throw new IllegalStateException(ex);
            }
        }
    }

    private static final class BudgetedByteArrayOutputStream extends ByteArrayOutputStream {
        private final ByteBudget budget;

        private BudgetedByteArrayOutputStream(ByteBudget budget) {
            this.budget = budget;
        }

        @Override
        public synchronized void write(int value) {
            budget.consume(1);
            super.write(value);
        }

        @Override
        public synchronized void write(byte[] value, int offset, int length) {
            budget.consume(length);
            super.write(value, offset, length);
        }

        private synchronized void writeAlreadyCounted(byte[] value) {
            super.write(value, 0, value.length);
        }
    }

    private record MapEntryBytes(byte[] key, byte[] value) {
    }
}
