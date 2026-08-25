package io.github.huynhngochuyhoang.httpstarter.core;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/** Caffeine-backed storage with generation-checked publication for duplicate phase-one loads. */
final class CaffeineLocalResponseCache implements LocalResponseCache {

    private final Object lifecycleMonitor = new Object();
    private final Map<CacheKeyContract.OpaqueKey, GenerationState> generations = new HashMap<>();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Cache<CacheKeyContract.OpaqueKey, CachedEntry> cache;
    private final long ttlNanos;
    private final LongSupplier ticker;
    private final RemovalObserver removalObserver;
    private final AtomicLong evictions = new AtomicLong();

    CaffeineLocalResponseCache(long ttlMs,
                               long maximumSize,
                               LongSupplier ticker,
                               RemovalObserver removalObserver) {
        this.ttlNanos = TimeUnit.MILLISECONDS.toNanos(ttlMs);
        this.ticker = ticker;
        this.removalObserver = removalObserver;
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
        CachedEntry entry = cache.getIfPresent(key);
        if (entry != null) {
            long ageNanos = ageNanos(entry);
            if (ageNanos < ttlNanos) {
                requireOpen();
                return hit(key, entry, ageNanos);
            }
            cache.cleanUp();
        }
        synchronized (lifecycleMonitor) {
            requireOpen();
            entry = cache.getIfPresent(key);
            if (entry != null) {
                long ageNanos = ageNanos(entry);
                if (ageNanos < ttlNanos) {
                    return hit(key, entry, ageNanos);
                }
                cache.cleanUp();
            }
            GenerationState state = generations.computeIfAbsent(key, ignored -> new GenerationState());
            state.activeLoads++;
            return Lookup.miss(new GenerationLoadToken(key, state, state.generation));
        }
    }

    private Lookup hit(CacheKeyContract.OpaqueKey key, CachedEntry entry, long ageNanos) {
        return Lookup.hit(entry.value, new GenerationEntryToken(key, entry), ageNanos);
    }

    private long ageNanos(CachedEntry entry) {
        return Math.max(0, ticker.getAsLong() - entry.writtenNanos);
    }

    @Override
    public RefreshToken beginRefresh(EntryToken entryToken) {
        GenerationEntryToken token = entryToken(entryToken);
        synchronized (lifecycleMonitor) {
            requireOpen();
            CachedEntry current = cache.getIfPresent(token.key);
            if (current != token.entry) {
                return null;
            }
            GenerationState state = generations.computeIfAbsent(token.key, ignored -> new GenerationState());
            state.activeRefreshes++;
            return new GenerationRefreshToken(token.key, state, state.generation, token.entry);
        }
    }

    @Override
    public boolean isRefreshCurrent(RefreshToken refreshToken) {
        GenerationRefreshToken token = refreshToken(refreshToken);
        synchronized (lifecycleMonitor) {
            if (closed.get()) {
                return false;
            }
            CachedEntry current = cache.getIfPresent(token.key);
            return current == token.entry && token.state.generation == token.observedGeneration;
        }
    }

    @Override
    public long hardExpiryRemainingNanos(RefreshToken refreshToken) {
        GenerationRefreshToken token = refreshToken(refreshToken);
        long ageNanos = Math.max(0, ticker.getAsLong() - token.entry.writtenNanos);
        return Math.max(0, ttlNanos - ageNanos);
    }

    @Override
    public void publishRefresh(RefreshToken refreshToken, Object value) {
        GenerationRefreshToken token = refreshToken(refreshToken);
        synchronized (lifecycleMonitor) {
            if (closed.get()) {
                return;
            }
            CachedEntry current = cache.getIfPresent(token.key);
            if (closed.get()
                    || current != token.entry
                    || token.state.generation != token.observedGeneration) {
                return;
            }
            cache.put(token.key, new CachedEntry(value, ticker.getAsLong()));
            token.state.generation++;
            cache.cleanUp();
        }
    }

    @Override
    public void finishRefresh(RefreshToken refreshToken) {
        GenerationRefreshToken token = refreshToken(refreshToken);
        synchronized (lifecycleMonitor) {
            if (token.finished) {
                return;
            }
            token.finished = true;
            token.state.activeRefreshes--;
            removeUnusedGeneration(token.key, token.state);
        }
    }

    @Override
    public void publish(LoadToken loadToken, Object value) {
        GenerationLoadToken token = token(loadToken);
        synchronized (lifecycleMonitor) {
            if (closed.get()) {
                return;
            }
            CachedEntry currentValue = cache.getIfPresent(token.key);
            if (closed.get()
                    || token.state.generation != token.observedGeneration
                    || currentValue != null) {
                return;
            }
            cache.put(token.key, new CachedEntry(value, ticker.getAsLong()));
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
    public long evictionCount() {
        return evictions.get();
    }

    @Override
    public void invalidateAll() {
        requireOpen();
        synchronized (lifecycleMonitor) {
            requireOpen();
            cache.invalidateAll();
            cache.cleanUp();
        }
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

    private void onRemoval(CacheKeyContract.OpaqueKey key, CachedEntry value, RemovalCause cause) {
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
        RemovalReason reason = switch (cause) {
            case EXPIRED -> RemovalReason.TTL;
            case SIZE -> RemovalReason.SIZE;
            default -> null;
        };
        if (reason != null) {
            evictions.incrementAndGet();
            if (removalObserver != null) {
                removalObserver.onRemoval(this, key, reason);
            }
        }
    }

    private void removeUnusedGeneration(CacheKeyContract.OpaqueKey key, GenerationState state) {
        if (state.activeLoads == 0 && state.activeRefreshes == 0 && cache.getIfPresent(key) == null) {
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

    private static GenerationEntryToken entryToken(EntryToken token) {
        if (token instanceof GenerationEntryToken generationToken) {
            return generationToken;
        }
        throw new IllegalArgumentException("Unknown local response-cache entry token");
    }

    private static GenerationRefreshToken refreshToken(RefreshToken token) {
        if (token instanceof GenerationRefreshToken generationToken) {
            return generationToken;
        }
        throw new IllegalArgumentException("Unknown local response-cache refresh token");
    }

    private record CachedEntry(Object value, long writtenNanos) {
    }

    private static final class GenerationState {
        private long generation;
        private int activeLoads;
        private int activeRefreshes;
    }

    private record GenerationEntryToken(
            CacheKeyContract.OpaqueKey key, CachedEntry entry) implements EntryToken {
    }

    private static final class GenerationRefreshToken implements RefreshToken {
        private final CacheKeyContract.OpaqueKey key;
        private final GenerationState state;
        private final long observedGeneration;
        private final CachedEntry entry;
        private boolean finished;

        private GenerationRefreshToken(CacheKeyContract.OpaqueKey key,
                                       GenerationState state,
                                       long observedGeneration,
                                       CachedEntry entry) {
            this.key = key;
            this.state = state;
            this.observedGeneration = observedGeneration;
            this.entry = entry;
        }
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
