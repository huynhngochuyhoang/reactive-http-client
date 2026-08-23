package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.util.ClassUtils;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.*;
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
    private final Map<FlightKey, InFlightLoad> inFlightLoads = new HashMap<>();
    private final Sinks.Empty<Void> shutdown = Sinks.empty();
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
        return getOrLoad(selection, key, loader, ResponseMetadata::successWithoutHeaders);
    }

    Mono<?> getOrLoad(EffectiveCachePolicy.Selection selection,
                      CacheKeyContract.OpaqueKey key,
                      Supplier<Mono<?>> loader,
                      Supplier<ResponseMetadata> responseMetadata) {
        return Mono.defer(() -> {
            LocalResponseCache cache = cache(selection, "unknown");
            LocalResponseCache.Lookup lookup = cache.lookup(key);
            if (lookup.hit()) {
                return Mono.just(lookup.value());
            }

            LocalResponseCache.LoadToken token = lookup.loadToken();
            if (selection.policy().isSingleFlight()) {
                return coalescedLoad(cache, key, token, loader, responseMetadata);
            }
            return load(cache, token, loader, responseMetadata);
        });
    }

    private Mono<?> coalescedLoad(LocalResponseCache cache,
                                  CacheKeyContract.OpaqueKey key,
                                  LocalResponseCache.LoadToken token,
                                  Supplier<Mono<?>> loader,
                                  Supplier<ResponseMetadata> responseMetadata) {
        FlightKey flightKey = new FlightKey(cache, key);
        synchronized (inFlightLoads) {
            if (closed.get()) {
                cache.finish(token);
                return Mono.error(new IllegalStateException("The local response cache has been closed"));
            }
            InFlightLoad existing = inFlightLoads.get(flightKey);
            if (existing != null) {
                cache.finish(token);
                return existing.publisher;
            }

            InFlightLoad created = new InFlightLoad();
            Mono<?> source = load(cache, token, loader, responseMetadata)
                    .takeUntilOther(shutdown.asMono())
                    .doFinally(ignored -> removeFlight(flightKey, created));
            created.publisher = source.share();
            inFlightLoads.put(flightKey, created);
            return created.publisher;
        }
    }

    private Mono<?> load(LocalResponseCache cache,
                         LocalResponseCache.LoadToken token,
                         Supplier<Mono<?>> loader,
                         Supplier<ResponseMetadata> responseMetadata) {
        return Mono.defer(() -> {
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
                            cacheCandidate(value, responseMetadata.get())
                                    .ifPresent(candidate -> cache.publish(token, candidate));
                        }
                    })
                    .doFinally(ignored -> cache.finish(token));
        });
    }

    private void removeFlight(FlightKey key, InFlightLoad flight) {
        synchronized (inFlightLoads) {
            inFlightLoads.remove(key, flight);
        }
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
        shutdown.tryEmitEmpty();
        synchronized (inFlightLoads) {
            inFlightLoads.clear();
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
            if (closed.get()) {
                throw new IllegalStateException(
                        "The local response cache for client '" + clientName + "' has been closed");
            }
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

    private java.util.Optional<Object> cacheCandidate(Object value, ResponseMetadata responseMetadata) {
        if (responseMetadata != null
                && (isRedirect(responseMetadata.statusCode())
                || hasNonCacheableHeaders(responseMetadata.headers()))) {
            return java.util.Optional.empty();
        }
        if (!(value instanceof ResponseEntity<?> entity)) {
            return java.util.Optional.of(value);
        }
        if (isRedirect(entity.getStatusCode().value())
                || hasNonCacheableHeaders(entity.getHeaders())) {
            return java.util.Optional.empty();
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

    private boolean hasNonCacheableHeaders(Map<String, ? extends java.util.List<String>> headers) {
        return hasNonCacheableHeaderNames(headers.keySet());
    }

    private boolean hasNonCacheableHeaders(HttpHeaders headers) {
        return hasNonCacheableHeaderNames(
                headers.headerSet().stream().map(Map.Entry::getKey).toList());
    }

    private boolean hasNonCacheableHeaderNames(Iterable<String> headerNames) {
        for (String headerName : headerNames) {
            String normalized = headerName.toLowerCase(Locale.ROOT);
            if (SensitiveHeaders.isSensitive(headerName)
                    || NON_CACHEABLE_RESPONSE_HEADERS.contains(normalized)) {
                return true;
            }
        }
        return false;
    }

    private boolean isRedirect(int statusCode) {
        return statusCode >= 300 && statusCode < 400;
    }

    record Snapshot(int policyCount, long configuredCapacity, long currentSize, boolean closed) {
    }

    record ResponseMetadata(int statusCode, Map<String, java.util.List<String>> headers) {

        ResponseMetadata {
            if (headers == null || headers.isEmpty()) {
                headers = Map.of();
            }
            else {
                Map<String, java.util.List<String>> copied = new LinkedHashMap<>();
                headers.forEach((name, values) -> copied.put(name, java.util.List.copyOf(values)));
                headers = Map.copyOf(copied);
            }
        }

        static ResponseMetadata successWithoutHeaders() {
            return new ResponseMetadata(200, Map.of());
        }
    }

    private record PolicyBounds(String policyName, long ttlMs, long maximumSize) {
    }

    private record FlightKey(LocalResponseCache cache, CacheKeyContract.OpaqueKey key) {
    }

    private static final class InFlightLoad {
        private Mono<?> publisher;
    }
}
