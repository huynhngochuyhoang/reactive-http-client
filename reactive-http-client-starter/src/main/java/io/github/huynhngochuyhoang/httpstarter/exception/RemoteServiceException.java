package io.github.huynhngochuyhoang.httpstarter.exception;

/**
 * Thrown when the upstream service returns a 5xx HTTP status code.
 */
public class RemoteServiceException extends RuntimeException {

    private final int statusCode;
    private final String responseBody;

    public RemoteServiceException(int statusCode, String responseBody) {
        super("Remote service error " + statusCode + ": " + responseBody);
        this.statusCode = statusCode;
        this.responseBody = responseBody;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getResponseBody() {
        return responseBody;
    }
}
