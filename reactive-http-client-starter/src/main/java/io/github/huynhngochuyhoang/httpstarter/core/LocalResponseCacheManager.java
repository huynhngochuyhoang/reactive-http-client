package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.util.ClassUtils;
import reactor.core.publisher.Mono;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** Owns the optional local caches for one reactive client factory. */
final class LocalResponseCacheManager implements AutoCloseable {

    private static final String CAFFEINE_CLASS = "com.github.benmanes.caffeine.cache.Caffeine";
    private static final Set<String> REPRESENTATION_HEADERS = Set.of(
            HttpHeaders.CONTENT_TYPE.toLowerCase(Locale.ROOT),
            HttpHeaders.CONTENT_LANGUAGE.toLowerCase(Locale.ROOT),
            HttpHeaders.CONTENT_ENCODING.toLowerCase(Locale.ROOT),
            HttpHeaders.ETAG.toLowerCase(Locale.ROOT),
            HttpHeaders.LAST_MODIFIED.toLowerCase(Locale.ROOT),
            HttpHeaders.CACHE_CONTROL.toLowerCase(Locale.ROOT),
            HttpHeaders.EXPIRES.toLowerCase(Locale.ROOT),
            HttpHeaders.VARY.toLowerCase(Locale.ROOT));
    private static final Set<String> NON_CACHEABLE_RESPONSE_HEADERS = Set.of(
            HttpHeaders.WWW_AUTHENTICATE.toLowerCase(Locale.ROOT),
            HttpHeaders.PROXY_AUTHENTICATE.toLowerCase(Locale.ROOT));
    private static final int MAX_CACHED_RESPONSE_HEADER_VALUES = 32;
    private static final int MAX_CACHED_RESPONSE_HEADER_BYTES = 16 * 1024;

    private final ClassLoader classLoader;
    private final LongSupplier ticker;
    private final Map<PolicyBounds, LocalResponseCache> caches = new LinkedHashMap<>();
    private final AtomicBoolean closed = new AtomicBoolean();

    private LocalResponseCacheManager(ClassLoader classLoader, LongSupplier ticker) {
        this.classLoader = classLoader;
        this.ticker = ticker;
    }

    static LocalResponseCacheManager lazy(ClassLoader classLoader) {
        return new LocalResponseCacheManager(classLoader, System::nanoTime);
    }

    static LocalResponseCacheManager createForClient(
            Class<?> clientInterface,
            String clientName,
            MethodMetadataCache metadataCache,
            ReactiveHttpClientProperties.ClientConfig clientConfig,
            ClassLoader classLoader) {
        LocalResponseCacheManager manager = lazy(classLoader);
        for (Method method : clientInterface.getMethods()) {
            if (method.isDefault() || !Modifier.isAbstract(method.getModifiers())) {
                continue;
            }
            RequestPlan plan = RequestPlan.from(metadataCache.get(method), clientInterface);
            EffectiveCachePolicy.Selection selection = EffectiveCachePolicy.resolve(plan, clientConfig);
            if (selection.enabled()) {
                manager.cache(selection, clientName);
            }
        }
        return manager;
    }

    static LocalResponseCacheManager testing(LongSupplier ticker) {
        return new LocalResponseCacheManager(LocalResponseCacheManager.class.getClassLoader(), ticker);
    }

    Mono<?> getOrLoad(EffectiveCachePolicy.Selection selection,
                      CacheKeyContract.OpaqueKey key,
                      Supplier<Mono<?>> loader) {
        return Mono.defer(() -> {
            LocalResponseCache cache = cache(selection, "unknown");
            LocalResponseCache.Lookup lookup = cache.lookup(key);
            if (lookup.hit()) {
                return Mono.just(lookup.value());
            }

            LocalResponseCache.LoadToken token = lookup.loadToken();
            Mono<?> source;
            try {
                source = loader.get();
            } catch (Throwable error) {
                cache.finish(token);
                return Mono.error(error);
            }
            return source
                    .doOnSuccess(value -> {
                        if (value != null) {
                            cacheCandidate(value).ifPresent(candidate -> cache.publish(token, candidate));
                        }
                    })
                    .doFinally(ignored -> cache.finish(token));
        });
    }

    Snapshot snapshot() {
        synchronized (caches) {
            long capacity = 0;
            long size = 0;
            for (Map.Entry<PolicyBounds, LocalResponseCache> entry : caches.entrySet()) {
                capacity = Math.addExact(capacity, entry.getKey().maximumSize);
                size = Math.addExact(size, entry.getValue().estimatedSize());
            }
            return new Snapshot(caches.size(), capacity, size, closed.get());
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        synchronized (caches) {
            caches.values().forEach(LocalResponseCache::close);
            caches.clear();
        }
    }

    private LocalResponseCache cache(EffectiveCachePolicy.Selection selection, String clientName) {
        if (closed.get()) {
            throw new IllegalStateException("The local response cache for client '" + clientName + "' has been closed");
        }
        ReactiveHttpClientProperties.CachePolicyConfig policy = selection.policy();
        if (policy == null || policy.getTtlMs() == null || policy.getMaximumSize() == null) {
            throw new IllegalStateException("Cache policy '" + selection.policyName() + "' for client '"
                    + clientName + "' has not passed startup validation");
        }
        PolicyBounds bounds = new PolicyBounds(selection.policyName(), policy.getTtlMs(), policy.getMaximumSize());
        synchronized (caches) {
            return caches.computeIfAbsent(bounds, ignored -> newCache(selection.policyName(), clientName, bounds));
        }
    }

    private LocalResponseCache newCache(String policyName, String clientName, PolicyBounds bounds) {
        if (!ClassUtils.isPresent(CAFFEINE_CLASS, classLoader)) {
            throw new IllegalStateException("Reactive HTTP client '" + clientName + "' selects response-cache policy '"
                    + policyName + "', but optional dependency com.github.ben-manes.caffeine:caffeine is not available. "
                    + "Add Caffeine at runtime or disable response caching for this client.");
        }
        return new CaffeineLocalResponseCache(bounds.ttlMs, bounds.maximumSize, ticker);
    }

    private java.util.Optional<Object> cacheCandidate(Object value) {
        if (!(value instanceof ResponseEntity<?> entity)) {
            return java.util.Optional.of(value);
        }
        for (Map.Entry<String, java.util.List<String>> header : entity.getHeaders().headerSet()) {
            String headerName = header.getKey();
            String normalized = headerName.toLowerCase(Locale.ROOT);
            if (SensitiveHeaders.isSensitive(headerName)
                    || NON_CACHEABLE_RESPONSE_HEADERS.contains(normalized)) {
                return java.util.Optional.empty();
            }
        }
        HttpHeaders retained = new HttpHeaders();
        int retainedValues = 0;
        int retainedBytes = 0;
        entity.getHeaders().headerSet().forEach(header -> {
            String name = header.getKey();
            if (REPRESENTATION_HEADERS.contains(name.toLowerCase(Locale.ROOT))) {
                retained.put(name, java.util.List.copyOf(header.getValue()));
            }
        });
        for (Map.Entry<String, java.util.List<String>> header : retained.headerSet()) {
            retainedValues = Math.addExact(retainedValues, header.getValue().size());
            if (retainedValues > MAX_CACHED_RESPONSE_HEADER_VALUES) {
                return java.util.Optional.empty();
            }
            for (String headerValue : header.getValue()) {
                if (headerValue.length() > MAX_CACHED_RESPONSE_HEADER_BYTES) {
                    return java.util.Optional.empty();
                }
                retainedBytes = Math.addExact(
                        retainedBytes, headerValue.getBytes(StandardCharsets.UTF_8).length);
                if (retainedBytes > MAX_CACHED_RESPONSE_HEADER_BYTES) {
                    return java.util.Optional.empty();
                }
            }
        }
        return java.util.Optional.of(new ResponseEntity<>(entity.getBody(), retained, entity.getStatusCode()));
    }

    record Snapshot(int policyCount, long configuredCapacity, long currentSize, boolean closed) {
    }

    private record PolicyBounds(String policyName, long ttlMs, long maximumSize) {
    }
}
