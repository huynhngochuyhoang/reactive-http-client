package io.github.huynhngochuyhoang.httpstarter.observability;

/**
 * Bounded stage of an outbound failure when the runtime provides conclusive evidence.
 *
 * <p>A {@code null} stage means the starter cannot safely attribute the failure to a
 * specific transport phase. Callers should continue to use the coarser
 * {@link io.github.huynhngochuyhoang.httpstarter.exception.ErrorCategory} contract
 * for general error handling.
 */
public enum HttpClientFailureStage {
    /** Failure while waiting for a connection from the Reactor Netty pool. */
    POOL_ACQUIRE;

    private static final int MAX_CAUSE_DEPTH = 16;
    private static final String POOL_ACQUIRE_TIMEOUT = "reactor.pool.PoolAcquireTimeoutException";
    private static final String POOL_ACQUIRE_PENDING_LIMIT = "reactor.pool.PoolAcquirePendingLimitException";
    private static final String POOL_SHUTDOWN = "reactor.pool.PoolShutdownException";
    private static final String SHADED_POOL_PREFIX = "reactor.netty.internal.shaded.reactor.pool.";

    /**
     * Resolves a stage from concrete Reactor Pool exceptions without inspecting messages.
     *
     * @param error terminal outbound error
     * @return {@link #POOL_ACQUIRE} when proven, otherwise {@code null}
     */
    public static HttpClientFailureStage from(Throwable error) {
        Throwable current = error;
        int depth = 0;
        while (current != null && depth < MAX_CAUSE_DEPTH) {
            String className = current.getClass().getName();
            if (POOL_ACQUIRE_TIMEOUT.equals(className)
                    || POOL_ACQUIRE_PENDING_LIMIT.equals(className)
                    || POOL_SHUTDOWN.equals(className)
                    || (className.startsWith(SHADED_POOL_PREFIX)
                            && (className.endsWith("PoolAcquireTimeoutException")
                            || className.endsWith("PoolAcquirePendingLimitException")
                            || className.endsWith("PoolShutdownException")))) {
                return POOL_ACQUIRE;
            }
            Throwable cause = current.getCause();
            current = cause != current ? cause : null;
            depth++;
        }
        return null;
    }
}
