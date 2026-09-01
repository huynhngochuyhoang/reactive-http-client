package io.github.huynhngochuyhoang.httpstarter.core;

/** Internal bounded storage contract kept independent from the optional cache implementation. */
interface LocalResponseCache extends AutoCloseable {

    Lookup lookup(CacheKeyContract.OpaqueKey key);

    RefreshToken beginRefresh(EntryToken entryToken);

    boolean isRefreshCurrent(RefreshToken refreshToken);

    long hardExpiryRemainingNanos(RefreshToken refreshToken);

    void publishRefresh(RefreshToken refreshToken, Object value);

    void publishRefresh(RefreshToken refreshToken, Object value, long decodedResponseBytes);

    default PublicationResult publishRefreshMeasured(
            RefreshToken refreshToken, Object value, long decodedResponseBytes) {
        publishRefresh(refreshToken, value, decodedResponseBytes);
        return PublicationResult.STORED;
    }

    void finishRefresh(RefreshToken refreshToken);

    void publish(LoadToken token, Object value);

    void publish(LoadToken token, Object value, long decodedResponseBytes);

    default PublicationResult publishMeasured(LoadToken token, Object value, long decodedResponseBytes) {
        publish(token, value, decodedResponseBytes);
        return PublicationResult.STORED;
    }

    void finish(LoadToken token);

    long estimatedSize();

    long evictionCount();

    Long maximumDecodedResponseBytes();

    long retainedDecodedResponseBytes();

    void invalidateAll();

    @Override
    void close();

    record Lookup(Object value, LoadToken loadToken, EntryToken entryToken, long ageNanos) {

        static Lookup hit(Object value, EntryToken entryToken, long ageNanos) {
            return new Lookup(value, null, entryToken, ageNanos);
        }

        static Lookup miss(LoadToken loadToken) {
            return new Lookup(null, loadToken, null, 0);
        }

        boolean hit() {
            return loadToken == null;
        }
    }

    interface LoadToken {
    }

    interface EntryToken {
    }

    interface RefreshToken {
    }

    enum RemovalReason {
        TTL,
        SIZE,
        WEIGHT;

        String tagValue() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    enum PublicationResult {
        STORED,
        STALE,
        CAPACITY
    }

    interface RemovalObserver {
        void onRemoval(LocalResponseCache cache, CacheKeyContract.OpaqueKey key, RemovalReason reason);
    }
}
