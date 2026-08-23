package io.github.huynhngochuyhoang.httpstarter.core;

/** Internal bounded storage contract kept independent from the optional cache implementation. */
interface LocalResponseCache extends AutoCloseable {

    Lookup lookup(CacheKeyContract.OpaqueKey key);

    void publish(LoadToken token, Object value);

    void finish(LoadToken token);

    long estimatedSize();

    @Override
    void close();

    record Lookup(Object value, LoadToken loadToken) {

        static Lookup hit(Object value) {
            return new Lookup(value, null);
        }

        static Lookup miss(LoadToken loadToken) {
            return new Lookup(null, loadToken);
        }

        boolean hit() {
            return loadToken == null;
        }
    }

    interface LoadToken {
    }
}
