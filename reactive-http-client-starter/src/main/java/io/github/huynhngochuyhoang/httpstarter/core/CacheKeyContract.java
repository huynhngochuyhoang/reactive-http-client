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
        for (int index : indexes) {
            if (index < frozen.length) {
                frozen[index] = freeze(frozen[index], 0, "parameter index " + index);
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
        for (String name : variants.contextNames()) {
            Object value = reactorContext.getOrDefault(name, null);
            contextValues.put(name, freeze(value, 0, "Reactor context '" + name + "'"));
        }

        CanonicalWriter writer = new CanonicalWriter();
        writer.value("reactive-http-cache-key-v1");
        writer.value(clientName);
        Class<?> concreteClient = clientInterface != null ? clientInterface : plan.method().getDeclaringClass();
        writer.value(concreteClient.getName());
        writer.value(resolvedMethodSignature(plan));
        writer.value(resolved.pathVars());
        writer.value(resolved.queryParams());

        Map<String, Object> selectedParameters = new TreeMap<>();
        variants.parameterIndexes().forEach((name, index) ->
                selectedParameters.put(name, index < frozenArguments.length ? frozenArguments[index] : null));
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
                .anyMatch(binding -> !selected.contains(binding.name())
                        && !selectedParameters.contains(binding.argumentIndex()));
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
                    validateRecord(raw, context, depth + 1);
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
            validateRecord(raw, context, depth + 1);
            return;
        }
        if (Collection.class.isAssignableFrom(raw) || Map.class.isAssignableFrom(raw)
                || Optional.class.isAssignableFrom(raw)) {
            throw invalid(context, "raw container type " + raw.getTypeName() + " cannot be frozen safely");
        }
        throw invalid(context, "mutable or unsupported type " + raw.getTypeName()
                + " cannot be copied safely; use immutable scalars, arrays, typed lists/sets/maps, enums, or records");
    }

    private static void validateRecord(Class<?> recordType, String context, int depth) {
        for (RecordComponent component : recordType.getRecordComponents()) {
            Type type = component.getGenericType();
            if (type instanceof ParameterizedType || type instanceof Class<?> clazz && clazz.isArray()) {
                throw invalid(context, "record component '" + component.getName()
                        + "' is mutable; records used as cache-key inputs must contain immutable scalar/record values");
            }
            validateType(type, context + " record component '" + component.getName() + "'", depth + 1);
        }
    }

    private static Object freeze(Object value, int depth, String context) {
        if (value == null) {
            return null;
        }
        if (depth > MAX_DEPTH) {
            throw invalid(context, "cache-key value nesting exceeds " + MAX_DEPTH);
        }
        Class<?> type = value.getClass();
        if (type.isPrimitive() || IMMUTABLE_SCALARS.contains(type) || type.isEnum()) {
            return value;
        }
        if (type.isArray()) {
            int length = Array.getLength(value);
            requireElementCount(length, context);
            Object copy = Array.newInstance(type.getComponentType(), length);
            for (int i = 0; i < length; i++) {
                Array.set(copy, i, freeze(Array.get(value, i), depth + 1, context + "[" + i + "]"));
            }
            return copy;
        }
        if (value instanceof List<?> list) {
            requireElementCount(list.size(), context);
            List<Object> copy = new ArrayList<>(list.size());
            for (int i = 0; i < list.size(); i++) {
                copy.add(freeze(list.get(i), depth + 1, context + "[" + i + "]"));
            }
            return Collections.unmodifiableList(copy);
        }
        if (value instanceof Set<?> set) {
            requireElementCount(set.size(), context);
            List<Object> values = new ArrayList<>(set.size());
            for (Object element : set) {
                values.add(freeze(element, depth + 1, context + " set element"));
            }
            values.sort(Comparator.comparing(CacheKeyContract::canonicalBytes, CacheKeyContract::compareBytes));
            return Collections.unmodifiableSet(new LinkedHashSet<>(values));
        }
        if (value instanceof Map<?, ?> map) {
            requireElementCount(map.size(), context);
            List<Map.Entry<Object, Object>> entries = new ArrayList<>(map.size());
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                Object key = freeze(entry.getKey(), depth + 1, context + " map key");
                Object mapped = freeze(entry.getValue(), depth + 1, context + " map value");
                entries.add(new AbstractMap.SimpleImmutableEntry<>(key, mapped));
            }
            entries.sort((left, right) -> {
                int compared = compareBytes(canonicalBytes(left.getKey()), canonicalBytes(right.getKey()));
                return compared != 0
                        ? compared
                        : compareBytes(canonicalBytes(left.getValue()), canonicalBytes(right.getValue()));
            });
            Map<Object, Object> copy = new LinkedHashMap<>();
            entries.forEach(entry -> copy.put(entry.getKey(), entry.getValue()));
            return Collections.unmodifiableMap(copy);
        }
        if (value instanceof Optional<?> optional) {
            return optional.map(item -> freeze(item, depth + 1, context + " optional"));
        }
        if (type.isRecord()) {
            for (RecordComponent component : type.getRecordComponents()) {
                try {
                    Object componentValue = component.getAccessor().invoke(value);
                    Object frozen = freeze(componentValue, depth + 1,
                            context + " record component '" + component.getName() + "'");
                    if (frozen != componentValue) {
                        throw invalid(context, "record component '" + component.getName()
                                + "' is mutable and cannot be copied without changing the record");
                    }
                } catch (ReflectiveOperationException ex) {
                    throw invalid(context, "cannot read record component '" + component.getName() + "'");
                }
            }
            return value;
        }
        throw invalid(context, "runtime value type " + type.getTypeName() + " cannot be copied safely");
    }

    private static void requireElementCount(int count, String context) {
        if (count > MAX_ELEMENTS) {
            throw invalid(context, "contains " + count + " elements; maximum is " + MAX_ELEMENTS);
        }
    }

    private static List<String> headerValues(Map<String, List<String>> headers, String requestedName) {
        return headers.entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(requestedName))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    private static String resolvedMethodSignature(RequestPlan plan) {
        StringJoiner signature = new StringJoiner(",", plan.method().getName() + "(", ")->"
                + (plan.responseType() != null ? plan.responseType().getTypeName() : "raw"));
        plan.parameterTypes().forEach(type -> signature.add(type.getTypeName()));
        return signature.toString();
    }

    private static byte[] canonicalBytes(Object value) {
        CanonicalWriter writer = new CanonicalWriter();
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
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private final DataOutputStream output = new DataOutputStream(bytes);

        void value(Object value) {
            try {
                writeValue(value, 0);
                if (bytes.size() > MAX_CANONICAL_BYTES) {
                    throw new IllegalStateException("Cache key material exceeds " + MAX_CANONICAL_BYTES + " bytes");
                }
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
                scalar(5, type, enumValue.name().getBytes(StandardCharsets.UTF_8));
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
                List<byte[]> encoded = set.stream().map(CacheKeyContract::canonicalBytes)
                        .sorted(CacheKeyContract::compareBytes).toList();
                output.writeInt(encoded.size());
                encoded.forEach(this::rawFrame);
            } else if (value instanceof Map<?, ?> map) {
                output.writeByte(23);
                List<MapEntryBytes> entries = map.entrySet().stream()
                        .map(entry -> new MapEntryBytes(
                                canonicalBytes(entry.getKey()), canonicalBytes(entry.getValue())))
                        .sorted(Comparator.comparing(MapEntryBytes::key, CacheKeyContract::compareBytes)
                                .thenComparing(MapEntryBytes::value, CacheKeyContract::compareBytes))
                        .toList();
                output.writeInt(entries.size());
                for (MapEntryBytes entry : entries) {
                    rawFrame(entry.key());
                    rawFrame(entry.value());
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
                    try {
                        framed(component.getAccessor().invoke(value), depth + 1);
                    } catch (ReflectiveOperationException ex) {
                        throw new IllegalStateException(
                                "Unable to encode record component " + component.getName(), ex);
                    }
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
            CanonicalWriter nested = new CanonicalWriter();
            nested.writeValue(value, depth);
            rawFrame(nested.finish());
        }

        private void rawFrame(byte[] value) {
            try {
                output.writeInt(value.length);
                output.write(value);
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

    private record MapEntryBytes(byte[] key, byte[] value) {
    }
}
