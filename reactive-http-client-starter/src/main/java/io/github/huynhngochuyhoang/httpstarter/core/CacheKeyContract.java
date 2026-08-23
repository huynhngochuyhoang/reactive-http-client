package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import org.reactivestreams.Publisher;
import org.springframework.core.io.Resource;
import org.springframework.core.io.buffer.DataBuffer;
import reactor.util.context.Context;
import reactor.util.context.ContextView;

import java.io.*;
import java.lang.reflect.*;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.StandardCharsets;
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
        Set<Integer> frozenIndexes = requestArgumentIndexes(plan);
        frozenIndexes.addAll(variants.parameterIndexes().values());
        for (int index : frozenIndexes) {
            if (index < 0 || index >= plan.parameterTypes().size()) {
                throw invalid(context, "cache-key parameter index " + index + " is outside the method signature");
            }
            validateType(plan.parameterTypes().get(index), context + " parameter index " + index, 0);
        }

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
                    && variants.parameterIndexes().isEmpty()
                    && variants.headerNames().isEmpty()
                    && variants.contextNames().isEmpty()) {
                throw invalid(context, "authenticated responses require an explicit parameter/header/context "
                        + "partition or shared-response acknowledgement");
            }
        }
    }

    static Object[] freezeArguments(RequestPlan plan,
                                    Object[] arguments,
                                    ReactiveHttpClientProperties.CachePolicyConfig policy) {
        Object[] frozen = arguments != null ? arguments.clone() : new Object[0];
        VariantSelection variants = variants(
                plan, null, policy, "Method " + plan.method().toGenericString());
        Set<Integer> indexes = requestArgumentIndexes(plan);
        indexes.addAll(variants.parameterIndexes().values());
        FreezeBudget budget = new FreezeBudget();
        for (int index : indexes) {
            if (index < frozen.length) {
                frozen[index] = freeze(frozen[index], 0, "parameter index " + index, budget);
            }
        }
        return frozen;
    }

    static PreparedKey derive(Class<?> clientInterface,
                              String clientName,
                              RequestPlan plan,
                              Object[] frozenArguments,
                              RequestArgumentResolver.ResolvedArgs resolved,
                              ContextView reactorContext,
                              ReactiveHttpClientProperties.CachePolicyConfig policy) {
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
        writer.value(uriPathValues(resolved.pathVars()));
        writer.value(uriQueryValues(resolved.queryParams()));

        Map<String, Object> selectedParameters = new TreeMap<>();
        variants.parameterIndexes().forEach((name, index) -> selectedParameters.put(
                name, selectedParameterValue(plan, index, frozenArguments, resolved)));
        writer.value(selectedParameters);

        Map<String, Object> selectedHeaders = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (String name : variants.headerNames()) {
            selectedHeaders.put(name.toLowerCase(Locale.ROOT), headerValues(resolved.headers(), name));
        }
        writer.value(selectedHeaders);
        writer.value(contextValues);
        return new PreparedKey(OpaqueKey.from(writer.finish()), contextValues);
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

    private static Set<Integer> requestArgumentIndexes(RequestPlan plan) {
        Set<Integer> indexes = new LinkedHashSet<>();
        plan.pathVars().forEach(binding -> indexes.add(binding.argumentIndex()));
        plan.queryParams().forEach(binding -> indexes.add(binding.argumentIndex()));
        plan.headerParams().forEach(binding -> indexes.add(binding.argumentIndex()));
        plan.idempotencyKeyParams().forEach(binding -> indexes.add(binding.argumentIndex()));
        indexes.addAll(plan.headerMapParams());
        if (plan.bodyIndex() >= 0) {
            indexes.add(plan.bodyIndex());
        }
        return indexes;
    }

    private static void validateType(Type type, String context, int depth) {
        if (depth > MAX_DEPTH) {
            throw invalid(context, "cache-key type nesting exceeds " + MAX_DEPTH);
        }
        if (type instanceof TypeVariable<?> || type instanceof WildcardType) {
            throw invalid(context, "unresolved type " + type.getTypeName() + " cannot be frozen");
        }
        if (type instanceof GenericArrayType arrayType) {
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
                    validateRecord(parameterizedType, raw, context, depth + 1);
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
            validateType(raw.getComponentType(), context, depth + 1);
            return;
        }
        if (raw.isRecord()) {
            validateRecord(raw, raw, context, depth + 1);
            return;
        }
        if (Collection.class.isAssignableFrom(raw) || Map.class.isAssignableFrom(raw)
                || Optional.class.isAssignableFrom(raw)) {
            throw invalid(context, "raw container type " + raw.getTypeName() + " cannot be frozen safely");
        }
        throw invalid(context, "mutable or unsupported type " + raw.getTypeName()
                + " cannot be copied safely; use immutable scalars, arrays, typed lists/sets/maps, enums, or records");
    }

    private static void validateRecord(Type declaredType,
                                       Class<?> recordType,
                                       String context,
                                       int depth) {
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
            Object copy = Array.newInstance(type.getComponentType(), length);
            for (int i = 0; i < length; i++) {
                Array.set(copy, i, freeze(
                        Array.get(value, i), depth + 1, context + "[" + i + "]", budget));
            }
            return copy;
        }
        if (value instanceof List<?> list) {
            budget.consume(list.size(), context);
            List<Object> copy = new ArrayList<>(list.size());
            int index = 0;
            for (Object element : list) {
                copy.add(freeze(element, depth + 1, context + "[" + index + "]", budget));
                index++;
            }
            return Collections.unmodifiableList(copy);
        }
        if (value instanceof Set<?> set) {
            budget.consume(set.size(), context);
            List<Object> values = new ArrayList<>(set.size());
            for (Object element : set) {
                values.add(freeze(element, depth + 1, context + " set element", budget));
            }
            return Collections.unmodifiableSet(new LinkedHashSet<>(values));
        }
        if (value instanceof Map<?, ?> map) {
            budget.consume(map.size(), context);
            Map<Object, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                Object key = freeze(entry.getKey(), depth + 1, context + " map key", budget);
                Object mapped = freeze(entry.getValue(), depth + 1, context + " map value", budget);
                copy.put(key, mapped);
            }
            return Collections.unmodifiableMap(copy);
        }
        if (value instanceof Optional<?> optional) {
            if (optional.isPresent()) {
                budget.consume(1, context);
            }
            return optional.map(item -> freeze(item, depth + 1, context + " optional", budget));
        }
        if (type.isRecord()) {
            RecordComponent[] components = type.getRecordComponents();
            budget.consume(components.length, context);
            for (RecordComponent component : components) {
                Object componentValue = recordComponentValue(component, value, context);
                Object frozen = freeze(componentValue, depth + 1,
                        context + " record component '" + component.getName() + "'", budget);
                if (frozen != componentValue) {
                    throw invalid(context, "record component '" + component.getName()
                            + "' is mutable and cannot be copied without changing the record");
                }
            }
            return value;
        }
        throw invalid(context, "runtime value type " + type.getTypeName() + " cannot be copied safely");
    }

    private static Map<String, String> uriPathValues(Map<String, Object> pathVars) {
        Map<String, String> values = new LinkedHashMap<>();
        pathVars.forEach((name, value) -> values.put(name, String.valueOf(value)));
        return values;
    }

    private static Map<String, List<String>> uriQueryValues(Map<String, List<Object>> queryParams) {
        Map<String, List<String>> values = new LinkedHashMap<>();
        queryParams.forEach((name, queryValues) -> values.put(name, queryValues.stream()
                .map(String::valueOf)
                .toList()));
        return values;
    }

    private static Object selectedParameterValue(RequestPlan plan,
                                                 int index,
                                                 Object[] frozenArguments,
                                                 RequestArgumentResolver.ResolvedArgs resolved) {
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
        Object value = index < frozenArguments.length ? frozenArguments[index] : null;
        return plan.bodyIndex() == index ? preserveRequestSetOrder(value) : value;
    }

    private static Object preserveRequestSetOrder(Object value) {
        if (value instanceof Set<?> set) {
            return set.stream().map(CacheKeyContract::preserveRequestSetOrder).toList();
        }
        if (value instanceof List<?> list) {
            return list.stream().map(CacheKeyContract::preserveRequestSetOrder).toList();
        }
        if (value instanceof Map<?, ?> map) {
            Map<Object, Object> ordered = new LinkedHashMap<>();
            map.forEach((key, mapped) -> ordered.put(
                    preserveRequestSetOrder(key), preserveRequestSetOrder(mapped)));
            return ordered;
        }
        if (value instanceof Optional<?> optional) {
            return optional.map(CacheKeyContract::preserveRequestSetOrder);
        }
        if (value != null && value.getClass().isArray()) {
            int length = Array.getLength(value);
            List<Object> ordered = new ArrayList<>(length);
            for (int index = 0; index < length; index++) {
                ordered.add(preserveRequestSetOrder(Array.get(value, index)));
            }
            return ordered;
        }
        return value;
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

    private static final class ByteBudget {
        private int remaining = MAX_CANONICAL_BYTES;

        private void consume(int count) {
            if (count < 0 || count > remaining) {
                throw new IllegalStateException(
                        "Cache key material exceeds " + MAX_CANONICAL_BYTES + " bytes");
            }
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
            if (value instanceof String text) {
                scalar(1, type, text.getBytes(StandardCharsets.UTF_8));
            } else if (value instanceof Boolean booleanValue) {
                scalar(2, type, new byte[]{(byte) (booleanValue ? 1 : 0)});
            } else if (value instanceof Number number) {
                scalar(3, type, numberBytes(number));
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

        private static byte[] numberBytes(Number value) {
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

        private static byte[] scalarBytes(Object value) {
            try {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                DataOutputStream output = new DataOutputStream(bytes);
                if (value instanceof URI uri) {
                    output.write(uri.toASCIIString().getBytes(StandardCharsets.US_ASCII));
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
                    output.write(numberBytes((Number) value));
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
