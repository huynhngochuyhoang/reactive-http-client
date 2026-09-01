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
    private final Cache<CacheKeyContract.OpaqueKey, StoredEntry> cache;
    private final long ttlNanos;
    private final LongSupplier ticker;
    private final RemovalObserver removalObserver;
    private final AtomicLong evictions = new AtomicLong();
    private final WeightState weightState;

    CaffeineLocalResponseCache(long ttlMs,
                               long maximumSize,
                               LongSupplier ticker,
                               RemovalObserver removalObserver) {
        this(ttlMs, maximumSize, null, ticker, removalObserver);
    }

    CaffeineLocalResponseCache(long ttlMs,
                               long maximumSize,
                               Long maximumDecodedResponseBytes,
                               LongSupplier ticker,
                               RemovalObserver removalObserver) {
        this.ttlNanos = TimeUnit.MILLISECONDS.toNanos(ttlMs);
        this.weightState = maximumDecodedResponseBytes != null
                ? new WeightState(maximumDecodedResponseBytes)
                : null;
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
        if (weightState != null) {
            synchronized (lifecycleMonitor) {
                return lookupUnderLifecycleLock(key);
            }
        }

        StoredEntry entry = cache.getIfPresent(key);
        if (entry != null) {
            long ageNanos = ageNanos(entry);
            if (ageNanos < ttlNanos) {
                requireOpen();
                return hit(key, entry, ageNanos);
            }
            cache.cleanUp();
        }
        synchronized (lifecycleMonitor) {
            return lookupUnderLifecycleLock(key);
        }
    }

    private Lookup lookupUnderLifecycleLock(CacheKeyContract.OpaqueKey key) {
        requireOpen();
        StoredEntry entry = cache.getIfPresent(key);
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

    private Lookup hit(CacheKeyContract.OpaqueKey key, StoredEntry entry, long ageNanos) {
        return Lookup.hit(entry.value(), new GenerationEntryToken(key, entry), ageNanos);
    }

    private long ageNanos(StoredEntry entry) {
        return Math.max(0, ticker.getAsLong() - entry.writtenNanos());
    }

    @Override
    public RefreshToken beginRefresh(EntryToken entryToken) {
        GenerationEntryToken token = entryToken(entryToken);
        synchronized (lifecycleMonitor) {
            requireOpen();
            StoredEntry current = cache.getIfPresent(token.key);
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
            StoredEntry current = cache.getIfPresent(token.key);
            return current == token.entry && token.state.generation == token.observedGeneration;
        }
    }

    @Override
    public long hardExpiryRemainingNanos(RefreshToken refreshToken) {
        GenerationRefreshToken token = refreshToken(refreshToken);
        long ageNanos = Math.max(0, ticker.getAsLong() - token.entry.writtenNanos());
        return Math.max(0, ttlNanos - ageNanos);
    }

    @Override
    public void publishRefresh(RefreshToken refreshToken, Object value) {
        publishRefresh(refreshToken, value, 0);
    }

    @Override
    public void publishRefresh(RefreshToken refreshToken, Object value, long decodedResponseBytes) {
        publishRefreshMeasured(refreshToken, value, decodedResponseBytes);
    }

    @Override
    public PublicationResult publishRefreshMeasured(
            RefreshToken refreshToken, Object value, long decodedResponseBytes) {
        GenerationRefreshToken token = refreshToken(refreshToken);
        synchronized (lifecycleMonitor) {
            if (closed.get()) {
                return PublicationResult.STALE;
            }
            StoredEntry current = cache.getIfPresent(token.key);
            if (closed.get()
                    || current != token.entry
                    || token.state.generation != token.observedGeneration) {
                return PublicationResult.STALE;
            }
            StoredEntry replacement = newEntry(value, decodedResponseBytes);
            if (!makeRoomFor(replacement.weight(), token.key, current)) {
                return PublicationResult.CAPACITY;
            }
            replace(token.key, current, replacement);
            token.state.generation++;
            cache.cleanUp();
            return PublicationResult.STORED;
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
        publish(loadToken, value, 0);
    }

    @Override
    public void publish(LoadToken loadToken, Object value, long decodedResponseBytes) {
        publishMeasured(loadToken, value, decodedResponseBytes);
    }

    @Override
    public boolean isLoadCurrent(LoadToken loadToken) {
        GenerationLoadToken token = token(loadToken);
        synchronized (lifecycleMonitor) {
            if (closed.get()) {
                return false;
            }
            StoredEntry current = cache.getIfPresent(token.key);
            return current == null && token.state.generation == token.observedGeneration;
        }
    }

    @Override
    public PublicationResult publishMeasured(
            LoadToken loadToken, Object value, long decodedResponseBytes) {
        GenerationLoadToken token = token(loadToken);
        synchronized (lifecycleMonitor) {
            if (closed.get()) {
                return PublicationResult.STALE;
            }
            StoredEntry currentValue = cache.getIfPresent(token.key);
            if (closed.get()
                    || token.state.generation != token.observedGeneration
                    || currentValue != null) {
                return PublicationResult.STALE;
            }
            StoredEntry entry = newEntry(value, decodedResponseBytes);
            if (!makeRoomFor(entry.weight(), token.key, null)) {
                return PublicationResult.CAPACITY;
            }
            replace(token.key, null, entry);
            token.state.generation++;
            cache.cleanUp();
            return PublicationResult.STORED;
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
        if (weightState != null) {
            synchronized (lifecycleMonitor) {
                cache.cleanUp();
                return cache.estimatedSize();
            }
        }
        cache.cleanUp();
        return cache.estimatedSize();
    }

    @Override
    public long evictionCount() {
        return evictions.get();
    }

    @Override
    public Long maximumDecodedResponseBytes() {
        return weightState != null ? weightState.maximumBytes : null;
    }

    @Override
    public long retainedDecodedResponseBytes() {
        if (weightState == null) {
            return 0;
        }
        synchronized (lifecycleMonitor) {
            cache.cleanUp();
            return weightState.retainedBytes;
        }
    }

    @Override
    public void invalidateAll() {
        requireOpen();
        synchronized (lifecycleMonitor) {
            requireOpen();
            generations.values().forEach(state -> state.generation++);
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

    private StoredEntry newEntry(Object value, long decodedResponseBytes) {
        if (weightState == null) {
            return new CachedEntry(value, ticker.getAsLong());
        }
        return new WeightedCachedEntry(value, ticker.getAsLong(), decodedResponseBytes);
    }

    private boolean makeRoomFor(
            long candidateWeight, CacheKeyContract.OpaqueKey candidateKey, StoredEntry replaced) {
        if (weightState == null) {
            return true;
        }
        cache.cleanUp();
        if (candidateWeight < 0 || candidateWeight > weightState.maximumBytes) {
            return false;
        }
        long replacedWeight = accountedWeight(replaced);
        while (weightState.retainedBytes - replacedWeight
                > weightState.maximumBytes - candidateWeight) {
            Map.Entry<CacheKeyContract.OpaqueKey, StoredEntry> victim = cache.asMap().entrySet().stream()
                    .filter(entry -> !entry.getKey().equals(candidateKey))
                    .filter(entry -> accountedWeight(entry.getValue()) > 0)
                    .findFirst()
                    .orElse(null);
            if (victim == null) {
                return false;
            }
            removeForWeight(victim.getKey(), victim.getValue());
        }
        return true;
    }

    private void replace(
            CacheKeyContract.OpaqueKey key, StoredEntry replaced, StoredEntry replacement) {
        unaccount(replaced);
        account(replacement);
        cache.put(key, replacement);
    }

    private void removeForWeight(CacheKeyContract.OpaqueKey key, StoredEntry entry) {
        unaccount(entry);
        cache.invalidate(key);
        evictions.incrementAndGet();
        if (removalObserver != null) {
            removalObserver.onRemoval(this, key, RemovalReason.WEIGHT);
        }
    }

    private long accountedWeight(StoredEntry entry) {
        return entry instanceof WeightedCachedEntry weighted && weighted.accounted
                ? weighted.weight
                : 0;
    }

    private void account(StoredEntry entry) {
        if (entry instanceof WeightedCachedEntry weighted && !weighted.accounted) {
            weightState.retainedBytes = Math.addExact(weightState.retainedBytes, weighted.weight);
            weighted.accounted = true;
        }
    }

    private void unaccount(StoredEntry entry) {
        if (entry instanceof WeightedCachedEntry weighted && weighted.accounted) {
            weightState.retainedBytes = Math.subtractExact(weightState.retainedBytes, weighted.weight);
            weighted.accounted = false;
        }
    }

    private void onRemoval(CacheKeyContract.OpaqueKey key, StoredEntry value, RemovalCause cause) {
        if (key == null) {
            return;
        }
        synchronized (lifecycleMonitor) {
            unaccount(value);
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

    private interface StoredEntry {
        Object value();

        long writtenNanos();

        long weight();
    }

    private record CachedEntry(Object value, long writtenNanos) implements StoredEntry {
        @Override
        public long weight() {
            return 0;
        }
    }

    private static final class WeightedCachedEntry implements StoredEntry {
        private final Object value;
        private final long writtenNanos;
        private final long weight;
        private boolean accounted;

        private WeightedCachedEntry(Object value, long writtenNanos, long weight) {
            this.value = value;
            this.writtenNanos = writtenNanos;
            this.weight = weight;
        }

        @Override
        public Object value() {
            return value;
        }

        @Override
        public long writtenNanos() {
            return writtenNanos;
        }

        @Override
        public long weight() {
            return weight;
        }
    }

    private static final class WeightState {
        private final long maximumBytes;
        private long retainedBytes;

        private WeightState(long maximumBytes) {
            this.maximumBytes = maximumBytes;
        }
    }

    private static final class GenerationState {
        private long generation;
        private int activeLoads;
        private int activeRefreshes;
    }

    private record GenerationEntryToken(
            CacheKeyContract.OpaqueKey key, StoredEntry entry) implements EntryToken {
    }

    private static final class GenerationRefreshToken implements RefreshToken {
        private final CacheKeyContract.OpaqueKey key;
        private final GenerationState state;
        private final long observedGeneration;
        private final StoredEntry entry;
        private boolean finished;

        private GenerationRefreshToken(CacheKeyContract.OpaqueKey key,
                                       GenerationState state,
                                       long observedGeneration,
                                       StoredEntry entry) {
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
