package io.github.huynhngochuyhoang.httpstarter.observability;

import io.github.huynhngochuyhoang.httpstarter.exception.AuthProviderException;
import io.github.huynhngochuyhoang.httpstarter.exception.LogicalCallTimeoutException;

import javax.net.ssl.SSLException;
import java.net.ConnectException;
import java.net.UnknownHostException;

/**
 * Bounded stage of an outbound failure when the runtime provides conclusive evidence.
 *
 * <p>A {@code null} stage means the starter cannot safely attribute the failure to a
 * specific transport phase. Callers should continue to use the coarser
 * {@link io.github.huynhngochuyhoang.httpstarter.exception.ErrorCategory} contract
 * for general error handling.
 */
public enum HttpClientFailureStage {
    /** Failure while resolving the remote endpoint name. */
    DNS_RESOLUTION,
    /** Failure while connecting to or establishing a tunnel through a proxy. */
    PROXY_CONNECT,
    /** Failure while establishing a connection to the remote endpoint. */
    CONNECT,
    /** Failure while negotiating TLS, including certificate validation. */
    TLS_HANDSHAKE,
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
    private static final String PROXY_CONNECT_EXCEPTION = "io.netty.handler.proxy.ProxyConnectException";
    private static final String WEB_CLIENT_REQUEST_EXCEPTION =
            "org.springframework.web.reactive.function.client.WebClientRequestException";
    private static final String WRITE_TIMEOUT = "io.netty.handler.timeout.WriteTimeoutException";
    private static final String READ_TIMEOUT = "io.netty.handler.timeout.ReadTimeoutException";

    /**
     * Resolves a stage from a concrete failure without response-state inference.
     * Read timeouts remain unclassified because this overload cannot prove whether
     * response headers were observed.
     *
     * @param error terminal outbound error
     * @return proven DNS, proxy, TLS, connection, pool-acquire, or request-write stage; otherwise {@code null}
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
     * pre-dispatch filters. Dispatch evidence disambiguates read timeouts and arbitrary
     * outer wrappers; direct concrete pre-response failures and WebClient transport
     * failures remain attributable.
     * Auth-provider failures are never promoted into business-request transport stages.
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
        boolean transportBoundarySeen = false;
        HttpClientFailureStage deferredStage = null;
        while (current != null && depth < MAX_CAUSE_DEPTH) {
            if (current instanceof LogicalCallTimeoutException logicalCallTimeout) {
                return logicalCallTimeout.getFailureStage();
            }
            if (current instanceof AuthProviderException) {
                return null;
            }
            String className = current.getClass().getName();
            if (isType(current, WEB_CLIENT_REQUEST_EXCEPTION)) {
                transportBoundarySeen = true;
            }
            boolean concreteFailureEligible = depth == 0 || transportBoundarySeen || responseStateKnown;
            boolean preResponseFailureEligible = statusCode == null && concreteFailureEligible;
            if (preResponseFailureEligible && current instanceof UnknownHostException) {
                return DNS_RESOLUTION;
            }
            if (preResponseFailureEligible && isType(current, PROXY_CONNECT_EXCEPTION)) {
                return PROXY_CONNECT;
            }
            if (preResponseFailureEligible && current instanceof SSLException) {
                // HTTPS proxy tunnel rejection can be wrapped by an outer SSL failure.
                deferredStage = TLS_HANDSHAKE;
                transportBoundarySeen = true;
            }
            if (preResponseFailureEligible && (CONNECT_TIMEOUT.equals(className)
                    || current instanceof ConnectException)) {
                return CONNECT;
            }
            if (preResponseFailureEligible && (POOL_ACQUIRE_TIMEOUT.equals(className)
                    || POOL_ACQUIRE_PENDING_LIMIT.equals(className)
                    || POOL_SHUTDOWN.equals(className)
                    || (className.startsWith(SHADED_POOL_PREFIX)
                            && (className.endsWith("PoolAcquireTimeoutException")
                            || className.endsWith("PoolAcquirePendingLimitException")
                            || className.endsWith("PoolShutdownException"))))) {
                return POOL_ACQUIRE;
            }
            if (preResponseFailureEligible && WRITE_TIMEOUT.equals(className)) {
                return REQUEST_WRITE;
            }
            if (concreteFailureEligible && READ_TIMEOUT.equals(className)) {
                return responseStateKnown
                        ? (statusCode != null ? RESPONSE_BODY : RESPONSE_HEADERS)
                        : null;
            }
            if (depth == 0 && !responseStateKnown && !transportBoundarySeen) {
                return deferredStage;
            }
            Throwable cause = current.getCause();
            current = cause != current ? cause : null;
            depth++;
        }
        return deferredStage;
    }

    private static boolean isType(Throwable error, String typeName) {
        Class<?> type = error.getClass();
        while (type != null) {
            if (typeName.equals(type.getName())) {
                return true;
            }
            type = type.getSuperclass();
        }
        return false;
    }
}
