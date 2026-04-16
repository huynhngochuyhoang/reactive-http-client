package io.github.huynhngochuyhoang.httpstarter.exception;

/**
 * Thrown when the upstream service returns a 5xx HTTP status code.
 *
 * <p>The {@link #getErrorCategory()} method always returns {@link ErrorCategory#SERVER_ERROR}.
 */
public class RemoteServiceException extends RuntimeException {

    private final int statusCode;
    private final String responseBody;
    private final ErrorCategory errorCategory;

    /**
     * Creates a new {@code RemoteServiceException}.
     * The {@link ErrorCategory} is always {@link ErrorCategory#SERVER_ERROR}.
     *
     * @param statusCode   the HTTP status code (5xx)
     * @param responseBody the raw response body (may be empty)
     */
    public RemoteServiceException(int statusCode, String responseBody) {
        super("Remote service error " + statusCode + ": " + responseBody);
        this.statusCode = statusCode;
        this.responseBody = responseBody;
        this.errorCategory = ErrorCategory.SERVER_ERROR;
    }

    /**
     * Creates a new {@code RemoteServiceException} with an explicit cause.
     *
     * @param statusCode   the HTTP status code (5xx)
     * @param responseBody the raw response body (may be empty)
     * @param cause        the underlying cause
     */
    public RemoteServiceException(int statusCode, String responseBody, Throwable cause) {
        super("Remote service error " + statusCode + ": " + responseBody, cause);
        this.statusCode = statusCode;
        this.responseBody = responseBody;
        this.errorCategory = ErrorCategory.SERVER_ERROR;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getResponseBody() {
        return responseBody;
    }

    /**
     * Returns the high-level error classification for this exception.
     *
     * @return always {@link ErrorCategory#SERVER_ERROR}
     */
    public ErrorCategory getErrorCategory() {
        return errorCategory;
    }
}
