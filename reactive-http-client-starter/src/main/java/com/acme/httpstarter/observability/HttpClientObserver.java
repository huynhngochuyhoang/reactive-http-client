package com.acme.httpstarter.observability;

/**
 * Extension point for observability hooks on every HTTP call made by a
 * {@code @ReactiveHttpClient} proxy.
 *
 * <p>Implement this interface and register the implementation as a Spring bean to receive
 * a callback after each request completes (successfully or with an error).
 *
 * <p>The default implementation ({@link MicrometerHttpClientObserver}) records
 * Micrometer metrics when a {@code MeterRegistry} bean is present on the classpath.
 * Register your own bean to replace or extend the default behaviour.
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
}
