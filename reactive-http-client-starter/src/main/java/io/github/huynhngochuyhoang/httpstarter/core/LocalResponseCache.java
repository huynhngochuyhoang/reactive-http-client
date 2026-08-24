package io.github.huynhngochuyhoang.httpstarter.core;

/** Internal bounded storage contract kept independent from the optional cache implementation. */
interface LocalResponseCache extends AutoCloseable {

    Lookup lookup(CacheKeyContract.OpaqueKey key);

    RefreshToken beginRefresh(EntryToken entryToken);

    boolean isRefreshCurrent(RefreshToken refreshToken);

    long hardExpiryRemainingNanos(RefreshToken refreshToken);

    void publishRefresh(RefreshToken refreshToken, Object value);

    void finishRefresh(RefreshToken refreshToken);

    void publish(LoadToken token, Object value);

    void finish(LoadToken token);

    long estimatedSize();

    long evictionCount();

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
        SIZE;

        String tagValue() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    interface RemovalObserver {
        void onRemoval(LocalResponseCache cache, CacheKeyContract.OpaqueKey key, RemovalReason reason);
    }
}
