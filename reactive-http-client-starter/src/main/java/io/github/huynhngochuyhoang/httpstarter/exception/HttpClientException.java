package io.github.huynhngochuyhoang.httpstarter.exception;

/**
 * Thrown when the upstream service returns a 4xx HTTP status code.
 *
 * <p>The {@link #getErrorCategory()} method returns a high-level classification:
 * <ul>
 *   <li>{@link ErrorCategory#RATE_LIMITED} – HTTP 429 Too Many Requests</li>
 *   <li>{@link ErrorCategory#CLIENT_ERROR} – all other 4xx responses</li>
 * </ul>
 */
public class HttpClientException extends RuntimeException {

    private final int statusCode;
    private final String responseBody;
    private final ErrorCategory errorCategory;

    /**
     * Creates a new {@code HttpClientException}.
     * The {@link ErrorCategory} is derived automatically from the status code:
     * 429 → {@link ErrorCategory#RATE_LIMITED}, otherwise → {@link ErrorCategory#CLIENT_ERROR}.
     *
     * @param statusCode   the HTTP status code (4xx)
     * @param responseBody the raw response body (may be empty)
     */
    public HttpClientException(int statusCode, String responseBody) {
        super("HTTP client error " + statusCode + ": " + responseBody);
        this.statusCode = statusCode;
        this.responseBody = responseBody;
        this.errorCategory = statusCode == 429 ? ErrorCategory.RATE_LIMITED : ErrorCategory.CLIENT_ERROR;
    }

    /**
     * Creates a new {@code HttpClientException} with an explicit {@link ErrorCategory}.
     *
     * @param statusCode    the HTTP status code (4xx)
     * @param responseBody  the raw response body (may be empty)
     * @param errorCategory the error category
     */
    public HttpClientException(int statusCode, String responseBody, ErrorCategory errorCategory) {
        super("HTTP client error " + statusCode + ": " + responseBody);
        this.statusCode = statusCode;
        this.responseBody = responseBody;
        this.errorCategory = errorCategory;
    }

    /**
     * Creates a new {@code HttpClientException} with an explicit cause.
     *
     * @param statusCode   the HTTP status code (4xx)
     * @param responseBody the raw response body (may be empty)
     * @param cause        the underlying cause
     */
    public HttpClientException(int statusCode, String responseBody, Throwable cause) {
        super("HTTP client error " + statusCode + ": " + responseBody, cause);
        this.statusCode = statusCode;
        this.responseBody = responseBody;
        this.errorCategory = statusCode == 429 ? ErrorCategory.RATE_LIMITED : ErrorCategory.CLIENT_ERROR;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getResponseBody() {
        return responseBody;
    }

    /**
     * Returns the high-level error classification for this exception.
     * Use this to write category-based error handling without hard-coding status codes.
     *
     * @return {@link ErrorCategory#RATE_LIMITED} for HTTP 429,
     *         {@link ErrorCategory#CLIENT_ERROR} for all other 4xx responses
     */
    public ErrorCategory getErrorCategory() {
        return errorCategory;
    }
}
