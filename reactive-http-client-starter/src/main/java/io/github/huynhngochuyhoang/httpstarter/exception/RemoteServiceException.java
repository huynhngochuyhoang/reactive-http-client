package io.github.huynhngochuyhoang.httpstarter.exception;

/**
 * Thrown when the upstream service returns a 5xx HTTP status code.
 *
 * <p>The {@link #getErrorCategory()} method always returns {@link ErrorCategory#SERVER_ERROR}.
 */
public class RemoteServiceException extends RuntimeException {

    private static final int MAX_RESPONSE_BODY_LENGTH = 4096;

    private final int statusCode;
    private final String responseBody;
    private final ErrorCategory errorCategory;
    private final String requestMethod;
    private final String requestUrl;

    /**
     * Creates a new {@code RemoteServiceException}.
     * The {@link ErrorCategory} is always {@link ErrorCategory#SERVER_ERROR}.
     *
     * @param statusCode   the HTTP status code (5xx)
     * @param responseBody the raw response body (may be empty)
     */
    public RemoteServiceException(int statusCode, String responseBody) {
        this(statusCode, responseBody, null, null);
    }

    /**
     * Creates a new {@code RemoteServiceException} enriched with request context.
     *
     * @param statusCode    the HTTP status code (5xx)
     * @param responseBody  the raw response body (may be empty)
     * @param requestMethod request method (optional)
     * @param requestUrl    request URL (optional)
     */
    public RemoteServiceException(int statusCode, String responseBody, String requestMethod, String requestUrl) {
        super(buildMessage(statusCode, requestMethod, requestUrl));
        this.statusCode = statusCode;
        this.responseBody = truncate(responseBody);
        this.errorCategory = ErrorCategory.SERVER_ERROR;
        this.requestMethod = requestMethod;
        this.requestUrl = requestUrl;
    }

    /**
     * Creates a new {@code RemoteServiceException} with an explicit cause.
     *
     * @param statusCode   the HTTP status code (5xx)
     * @param responseBody the raw response body (may be empty)
     * @param cause        the underlying cause
     */
    public RemoteServiceException(int statusCode, String responseBody, Throwable cause) {
        super("Remote service error " + statusCode, cause);
        this.statusCode = statusCode;
        this.responseBody = truncate(responseBody);
        this.errorCategory = ErrorCategory.SERVER_ERROR;
        this.requestMethod = null;
        this.requestUrl = null;
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

    public String getRequestMethod() {
        return requestMethod;
    }

    public String getRequestUrl() {
        return requestUrl;
    }

    private static String truncate(String body) {
        if (body == null) {
            return null;
        }
        if (body.length() <= MAX_RESPONSE_BODY_LENGTH) {
            return body;
        }
        return body.substring(0, MAX_RESPONSE_BODY_LENGTH);
    }

    private static String buildMessage(int statusCode, String requestMethod, String requestUrl) {
        if ((requestMethod == null || requestMethod.isBlank()) && (requestUrl == null || requestUrl.isBlank())) {
            return "Remote service error " + statusCode;
        }
        return "Remote service error " + statusCode + " (" + requestMethod + " " + requestUrl + ")";
    }
}
