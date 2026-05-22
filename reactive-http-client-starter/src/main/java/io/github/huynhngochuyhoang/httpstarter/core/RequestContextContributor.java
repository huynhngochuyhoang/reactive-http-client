package io.github.huynhngochuyhoang.httpstarter.core;

import reactor.util.context.Context;
import reactor.util.context.ContextView;

import java.util.Optional;

/**
 * Ordered contributor for starter-owned Reactor context values.
 *
 * @param <T> immutable value type captured by this contributor
 */
public interface RequestContextContributor<T> {

    /** Stable key used in a captured context snapshot map. */
    String key();

    /** Lower values restore first. Ties are resolved by {@link #key()}. */
    default int order() {
        return 0;
    }

    /** Captures an immutable value from the Reactor context, or empty when absent. */
    Optional<T> capture(ContextView context);

    /** Restores a previously captured value into the Reactor context. */
    Context restore(Context context, T value);
}
