package com.acme.httpstarter.exception;

/**
 * Thrown when the upstream service returns a 4xx HTTP status code.
 */
public class HttpClientException extends RuntimeException {

    private final int statusCode;
    private final String responseBody;

    public HttpClientException(int statusCode, String responseBody) {
        super("HTTP client error " + statusCode + ": " + responseBody);
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
