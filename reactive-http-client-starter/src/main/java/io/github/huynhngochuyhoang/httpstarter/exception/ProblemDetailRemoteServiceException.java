package io.github.huynhngochuyhoang.httpstarter.exception;

import org.springframework.http.ProblemDetail;

/**
 * Thrown for 5xx application/problem+json responses when Problem Detail mapping is enabled.
 */
public class ProblemDetailRemoteServiceException extends RemoteServiceException {

    private final ProblemDetail problemDetail;

    public ProblemDetailRemoteServiceException(
            int statusCode,
            String responseBody,
            String requestMethod,
            String requestUrl,
            ProblemDetail problemDetail) {
        super(statusCode, responseBody, requestMethod, requestUrl);
        this.problemDetail = problemDetail;
    }

    public ProblemDetail getProblemDetail() {
        return problemDetail;
    }
}
