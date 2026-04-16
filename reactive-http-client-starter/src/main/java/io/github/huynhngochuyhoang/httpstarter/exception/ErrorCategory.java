package io.github.huynhngochuyhoang.httpstarter.exception;

/**
 * High-level classification of HTTP client errors.
 *
 * <p>Use {@code ErrorCategory} to write category-based error-handling logic without
 * hard-coding specific HTTP status codes:
 *
 * <pre>{@code
 * client.getUser("123")
 *     .onErrorResume(HttpClientException.class, ex -> switch (ex.getErrorCategory()) {
 *         case RATE_LIMITED -> rateLimitFallback();
 *         case CLIENT_ERROR -> Mono.error(new BadRequestException(ex));
 *         default           -> Mono.error(ex);
 *     });
 * }</pre>
 */
public enum ErrorCategory {

    /**
     * The server returned a 4xx status code (excluding 429).
     * Indicates a problem with the request itself (bad input, not found, unauthorized, etc.).
     */
    CLIENT_ERROR,

    /**
     * The server returned HTTP 429 Too Many Requests.
     * The caller should back off and retry after an appropriate delay.
     */
    RATE_LIMITED,

    /**
     * The server returned a 5xx status code.
     * Indicates an internal or transient server-side failure.
     */
    SERVER_ERROR,

    /**
     * The request timed out before a response was received.
     * This wraps a {@link java.util.concurrent.TimeoutException}.
     */
    TIMEOUT,

    /**
     * The reactive subscription was cancelled before a response was received.
     */
    CANCELLED,

    /**
     * An unexpected or unclassified error occurred (e.g. network failure, codec error).
     */
    UNKNOWN
}
