package io.github.huynhngochuyhoang.httpstarter.observability;

/**
 * Extension point for observability hooks on every HTTP call made by a
 * {@code @ReactiveHttpClient} proxy.
 *
 * <p>Implement this interface and register the implementation as a Spring bean to receive
 * a callback after each request completes (successfully or with an error).
 *
 * <p>The default implementation ({@link MicrometerHttpClientObserver}) records
 * Micrometer metrics when a {@code MeterRegistry} bean is present on the classpath.
 * Custom observer beans run alongside built-ins. To replace a built-in, register
 * a bean with that built-in's name.
 *
 * @see MicrometerHttpClientObserver
 */
public interface HttpClientObserver {

    /**
     * Called after each HTTP exchange (success or error).
     *
     * @param event contains all data about the completed request/response cycle
     */
    void record(HttpClientObserverEvent event);

    /**
     * Called for a logical caller completed from cache-owned work without owning
     * a downstream HTTP dispatch. Custom observers receive these events by
     * default; downstream-only observers may override this method.
     *
     * @param event contains the caller's terminal cache result
     */
    default void recordCacheServed(HttpClientObserverEvent event) {
        record(event);
    }
}
