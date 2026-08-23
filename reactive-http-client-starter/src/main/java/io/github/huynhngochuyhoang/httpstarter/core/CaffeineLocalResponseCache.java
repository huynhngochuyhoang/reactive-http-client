package io.github.huynhngochuyhoang.httpstarter.core;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;

/** Caffeine-backed storage with generation-checked publication for duplicate phase-one loads. */
final class CaffeineLocalResponseCache implements LocalResponseCache {

    private final Object lifecycleMonitor = new Object();
    private final Map<CacheKeyContract.OpaqueKey, GenerationState> generations = new HashMap<>();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Cache<CacheKeyContract.OpaqueKey, Object> cache;

    CaffeineLocalResponseCache(long ttlMs, long maximumSize, LongSupplier ticker) {
        this.cache = Caffeine.newBuilder()
                .maximumSize(maximumSize)
                .expireAfterWrite(Duration.ofMillis(ttlMs))
                .ticker(ticker::getAsLong)
                .executor(Runnable::run)
                .removalListener(this::onRemoval)
                .build();
    }

    @Override
    public Lookup lookup(CacheKeyContract.OpaqueKey key) {
        requireOpen();
        Object value = cache.getIfPresent(key);
        if (value != null) {
            requireOpen();
            return Lookup.hit(value);
        }

        synchronized (lifecycleMonitor) {
            requireOpen();
            value = cache.getIfPresent(key);
            if (value != null) {
                return Lookup.hit(value);
            }
            GenerationState state = generations.computeIfAbsent(key, ignored -> new GenerationState());
            state.activeLoads++;
            return Lookup.miss(new GenerationLoadToken(key, state, state.generation));
        }
    }

    @Override
    public void publish(LoadToken loadToken, Object value) {
        GenerationLoadToken token = token(loadToken);
        synchronized (lifecycleMonitor) {
            if (closed.get()
                    || token.state.generation != token.observedGeneration
                    || cache.getIfPresent(token.key) != null) {
                return;
            }
            cache.put(token.key, value);
            token.state.generation++;
            cache.cleanUp();
        }
    }

    @Override
    public void finish(LoadToken loadToken) {
        GenerationLoadToken token = token(loadToken);
        synchronized (lifecycleMonitor) {
            if (token.finished) {
                return;
            }
            token.finished = true;
            token.state.activeLoads--;
            removeUnusedGeneration(token.key, token.state);
        }
    }

    @Override
    public long estimatedSize() {
        if (closed.get()) {
            return 0;
        }
        cache.cleanUp();
        return cache.estimatedSize();
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        synchronized (lifecycleMonitor) {
            cache.invalidateAll();
            cache.cleanUp();
            generations.clear();
        }
    }

    private void onRemoval(CacheKeyContract.OpaqueKey key, Object value, RemovalCause cause) {
        if (key == null) {
            return;
        }
        synchronized (lifecycleMonitor) {
            GenerationState state = generations.get(key);
            if (state != null) {
                state.generation++;
                removeUnusedGeneration(key, state);
            }
        }
    }

    private void removeUnusedGeneration(CacheKeyContract.OpaqueKey key, GenerationState state) {
        if (state.activeLoads == 0 && cache.getIfPresent(key) == null) {
            generations.remove(key, state);
        }
    }

    private void requireOpen() {
        if (closed.get()) {
            throw new IllegalStateException("The local response cache has been closed");
        }
    }

    private static GenerationLoadToken token(LoadToken token) {
        if (token instanceof GenerationLoadToken generationToken) {
            return generationToken;
        }
        throw new IllegalArgumentException("Unknown local response-cache load token");
    }

    private static final class GenerationState {
        private long generation;
        private int activeLoads;
    }

    private static final class GenerationLoadToken implements LoadToken {
        private final CacheKeyContract.OpaqueKey key;
        private final GenerationState state;
        private final long observedGeneration;
        private boolean finished;

        private GenerationLoadToken(CacheKeyContract.OpaqueKey key,
                                    GenerationState state,
                                    long observedGeneration) {
            this.key = key;
            this.state = state;
            this.observedGeneration = observedGeneration;
        }
    }
}
