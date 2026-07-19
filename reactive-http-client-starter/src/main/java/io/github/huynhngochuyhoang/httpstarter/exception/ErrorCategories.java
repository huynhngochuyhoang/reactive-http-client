package io.github.huynhngochuyhoang.httpstarter.exception;

import io.netty.handler.timeout.ReadTimeoutException;
import io.netty.handler.timeout.WriteTimeoutException;
import org.springframework.core.codec.DecodingException;
import reactor.netty.http.client.PrematureCloseException;

import javax.net.ssl.SSLException;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeoutException;

/**
 * Utilities for extracting {@link ErrorCategory} from failures.
 */
public final class ErrorCategories {

    private static final int MAX_CAUSE_DEPTH = 16;
    private static final String CIRCUIT_BREAKER_NOT_PERMITTED =
            "io.github.resilience4j.circuitbreaker.CallNotPermittedException";
    private static final String BULKHEAD_FULL =
            "io.github.resilience4j.bulkhead.BulkheadFullException";
    private static final String RATE_LIMITER_NOT_PERMITTED =
            "io.github.resilience4j.ratelimiter.RequestNotPermitted";

    private ErrorCategories() {
    }

    /**
     * Extracts the best available {@link ErrorCategory} from a thrown error.
     *
     * @param error thrown error, usually from a reactive client call
     * @return the extracted category, {@link ErrorCategory#UNKNOWN} for unclassified
     * errors, or {@code null} when {@code error} is {@code null}
     */
    public static ErrorCategory from(Throwable error) {
        return from(error, null);
    }

    /**
     * Extracts the best available {@link ErrorCategory} from a thrown error and
     * optional HTTP status code.
     *
     * <p>The status code is useful for decode failures where the original HTTP
     * response was successful but the response body could not be decoded. Cause
     * traversal examines at most 16 nodes from outermost to innermost, and the
     * nearest proven category wins before status-only fallback.
     *
     * @param error thrown error, usually from a reactive client call
     * @param statusCode HTTP status code when available
     * @return the extracted category, {@link ErrorCategory#UNKNOWN} for unclassified
     * errors, or {@code null} when both inputs are {@code null}
     */
    public static ErrorCategory from(Throwable error, Integer statusCode) {
        ErrorCategory causeCategory = fromCauseChain(error, statusCode);
        if (causeCategory != null) {
            return causeCategory;
        }
        ErrorCategory statusCategory = fromStatusCode(statusCode);
        if (statusCategory != null) {
            return statusCategory;
        }
        return error != null ? ErrorCategory.UNKNOWN : null;
    }

    private static ErrorCategory fromCauseChain(Throwable error, Integer statusCode) {
        Throwable current = error;
        int depth = 0;
        while (current != null && depth < MAX_CAUSE_DEPTH) {
            if (current instanceof HttpClientException httpClientException) {
                return httpClientException.getErrorCategory();
            }
            if (current instanceof RemoteServiceException remoteServiceException) {
                return remoteServiceException.getErrorCategory();
            }
            if (current instanceof AuthProviderException) {
                return ErrorCategory.AUTH_PROVIDER_ERROR;
            }
            if (current instanceof CancellationException) {
                return ErrorCategory.CANCELLED;
            }
            if (current instanceof SSLException) {
                return ErrorCategory.TLS_ERROR;
            }
            if (isResilienceFailure(current)) {
                return ErrorCategory.RESILIENCE_ERROR;
            }
            if (current instanceof UnknownHostException) {
                return ErrorCategory.UNKNOWN_HOST;
            }
            if (current instanceof ConnectException) {
                return ErrorCategory.CONNECT_ERROR;
            }
            if (current instanceof TimeoutException
                    || current instanceof ReadTimeoutException
                    || current instanceof WriteTimeoutException
                    || current instanceof PrematureCloseException) {
                return ErrorCategory.TIMEOUT;
            }
            if (isResponseDecodeError(statusCode, current)) {
                return ErrorCategory.RESPONSE_DECODE_ERROR;
            }
            current = nextCause(current);
            depth++;
        }
        return null;
    }

    private static boolean isResilienceFailure(Throwable error) {
        String className = error.getClass().getName();
        return CIRCUIT_BREAKER_NOT_PERMITTED.equals(className)
                || BULKHEAD_FULL.equals(className)
                || RATE_LIMITER_NOT_PERMITTED.equals(className);
    }

    /**
     * Converts an HTTP status code to an {@link ErrorCategory}.
     *
     * @param statusCode HTTP status code, or {@code null}
     * @return a category for 4xx/5xx statuses, or {@code null} for non-errors
     */
    public static ErrorCategory fromStatusCode(Integer statusCode) {
        if (statusCode == null) {
            return null;
        }
        if (statusCode == 429) {
            return ErrorCategory.RATE_LIMITED;
        }
        if (statusCode >= 400 && statusCode < 500) {
            return ErrorCategory.CLIENT_ERROR;
        }
        if (statusCode >= 500) {
            return ErrorCategory.SERVER_ERROR;
        }
        return null;
    }

    /**
     * Returns whether the thrown error resolves to the expected category.
     */
    public static boolean is(Throwable error, ErrorCategory expected) {
        return from(error) == expected;
    }

    private static boolean isResponseDecodeError(Integer statusCode, Throwable error) {
        return statusCode != null && statusCode < 400 && error instanceof DecodingException;
    }

    private static Throwable nextCause(Throwable error) {
        Throwable cause = error.getCause();
        return cause != error ? cause : null;
    }
}
