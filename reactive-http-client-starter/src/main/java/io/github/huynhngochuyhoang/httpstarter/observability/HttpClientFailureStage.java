package io.github.huynhngochuyhoang.httpstarter.observability;

import io.github.huynhngochuyhoang.httpstarter.exception.LogicalCallTimeoutException;

/**
 * Bounded stage of an outbound failure when the runtime provides conclusive evidence.
 *
 * <p>A {@code null} stage means the starter cannot safely attribute the failure to a
 * specific transport phase. Callers should continue to use the coarser
 * {@link io.github.huynhngochuyhoang.httpstarter.exception.ErrorCategory} contract
 * for general error handling.
 */
public enum HttpClientFailureStage {
    /** Failure while establishing a connection to the remote endpoint. */
    CONNECT,
    /** Failure while waiting for a connection from the Reactor Netty pool. */
    POOL_ACQUIRE,
    /** Failure while writing the outbound request. */
    REQUEST_WRITE,
    /** Failure while waiting for response headers. */
    RESPONSE_HEADERS,
    /** Failure while consuming a response body after headers were observed. */
    RESPONSE_BODY;

    private static final int MAX_CAUSE_DEPTH = 16;
    private static final String POOL_ACQUIRE_TIMEOUT = "reactor.pool.PoolAcquireTimeoutException";
    private static final String POOL_ACQUIRE_PENDING_LIMIT = "reactor.pool.PoolAcquirePendingLimitException";
    private static final String POOL_SHUTDOWN = "reactor.pool.PoolShutdownException";
    private static final String SHADED_POOL_PREFIX = "reactor.netty.internal.shaded.reactor.pool.";
    private static final String CONNECT_TIMEOUT = "io.netty.channel.ConnectTimeoutException";
    private static final String WRITE_TIMEOUT = "io.netty.handler.timeout.WriteTimeoutException";
    private static final String READ_TIMEOUT = "io.netty.handler.timeout.ReadTimeoutException";

    /**
     * Resolves a stage from a concrete failure without response-state inference.
     * Read timeouts remain unclassified because this overload cannot prove whether
     * response headers were observed.
     *
     * @param error terminal outbound error
     * @return proven connection, pool-acquire, or request-write stage; otherwise {@code null}
     */
    public static HttpClientFailureStage from(Throwable error) {
        return resolve(error, null, false);
    }

    /**
     * Resolves a stage from concrete transport exceptions and observed status.
     * A status proves response-body processing; without status or explicit dispatch
     * evidence, a Netty read timeout remains unclassified. Generic timeout exceptions
     * also remain unclassified.
     *
     * @param error terminal outbound error
     * @param statusCode observed HTTP status, or {@code null} before response headers
     * @return proven failure stage, otherwise {@code null}
     */
    public static HttpClientFailureStage from(Throwable error, Integer statusCode) {
        return from(error, statusCode, statusCode != null);
    }

    /**
     * Resolves a stage using explicit evidence that the primary request passed
     * pre-dispatch filters. Dispatch evidence disambiguates only read timeouts;
     * concrete connect, pool-acquire, and request-write stages remain attributable.
     *
     * @param error terminal outbound error
     * @param statusCode observed HTTP status, or {@code null} before response headers
     * @param requestDispatched whether final outbound request observation ran
     * @return proven failure stage, otherwise {@code null}
     */
    public static HttpClientFailureStage from(
            Throwable error, Integer statusCode, boolean requestDispatched) {
        return resolve(error, statusCode, requestDispatched || statusCode != null);
    }

    private static HttpClientFailureStage resolve(
            Throwable error, Integer statusCode, boolean responseStateKnown) {
        Throwable current = error;
        int depth = 0;
        while (current != null && depth < MAX_CAUSE_DEPTH) {
            if (current instanceof LogicalCallTimeoutException logicalCallTimeout) {
                return logicalCallTimeout.getFailureStage();
            }
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
            if (CONNECT_TIMEOUT.equals(className)) {
                return CONNECT;
            }
            if (WRITE_TIMEOUT.equals(className)) {
                return REQUEST_WRITE;
            }
            if (READ_TIMEOUT.equals(className)) {
                return responseStateKnown
                        ? (statusCode != null ? RESPONSE_BODY : RESPONSE_HEADERS)
                        : null;
            }
            Throwable cause = current.getCause();
            current = cause != current ? cause : null;
            depth++;
        }
        return null;
    }
}
