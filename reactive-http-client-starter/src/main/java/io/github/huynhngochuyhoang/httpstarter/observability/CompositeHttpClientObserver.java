package io.github.huynhngochuyhoang.httpstarter.observability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Delegates each observer event to multiple observers while isolating observer
 * failures from the request pipeline and from other observers.
 */
public class CompositeHttpClientObserver implements HttpClientObserver {

    private static final Logger log = LoggerFactory.getLogger(CompositeHttpClientObserver.class);

    private final List<HttpClientObserver> observers;

    public CompositeHttpClientObserver(List<HttpClientObserver> observers) {
        this.observers = observers != null ? List.copyOf(observers) : List.of();
    }

    @Override
    public void record(HttpClientObserverEvent event) {
        record(event, false);
    }

    @Override
    public void recordCacheServed(HttpClientObserverEvent event) {
        record(event, true);
    }

    private void record(HttpClientObserverEvent event, boolean cacheServed) {
        for (HttpClientObserver observer : observers) {
            try {
                if (cacheServed) {
                    observer.recordCacheServed(event);
                }
                else {
                    observer.record(event);
                }
            } catch (Exception e) {
                log.warn("HttpClientObserver [{}] threw an exception - ignoring: {}",
                        observer.getClass().getName(), e.getMessage());
            }
        }
    }
}
