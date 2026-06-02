package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.exception.ErrorCategories;
import io.github.huynhngochuyhoang.httpstarter.exception.ErrorCategory;
import io.github.huynhngochuyhoang.httpstarter.exception.HttpClientException;
import io.github.huynhngochuyhoang.httpstarter.exception.RemoteServiceException;
import org.springframework.http.HttpHeaders;

import java.nio.charset.StandardCharsets;

/**
 * Immutable input passed to {@link ErrorResponseMapper} implementations.
 * The response body is bounded; inspect {@link #responseBodyTruncated()} and
 * {@link #retainedResponseBodyBytes()} before assuming structured input is complete.
 */
public record ErrorResponseContext(
        String clientName,
        int statusCode,
        String responseBody,
        HttpHeaders responseHeaders,
        String requestMethod,
        String requestUrl,
        ErrorCategory errorCategory,
        boolean responseBodyTruncated,
        int retainedResponseBodyBytes
) {

    public ErrorResponseContext(
            String clientName,
            int statusCode,
            String responseBody,
            HttpHeaders responseHeaders,
            String requestMethod,
            String requestUrl,
            ErrorCategory errorCategory) {
        this(clientName, statusCode, responseBody, responseHeaders, requestMethod, requestUrl, errorCategory,
                false, utf8Length(responseBody));
    }

    public ErrorResponseContext {
        responseHeaders = responseHeaders != null
                ? HttpHeaders.readOnlyHttpHeaders(responseHeaders)
                : HttpHeaders.EMPTY;
        errorCategory = errorCategory != null
                ? errorCategory
                : ErrorCategories.fromStatusCode(statusCode);
        if (retainedResponseBodyBytes < 0) {
            throw new IllegalArgumentException("retainedResponseBodyBytes must be non-negative");
        }
    }

    private static int utf8Length(String value) {
        return value != null ? value.getBytes(StandardCharsets.UTF_8).length : 0;
    }

    /**
     * Builds the starter's default domain exception for this response.
     */
    public RuntimeException defaultException() {
        if (statusCode >= 400 && statusCode < 500) {
            return new HttpClientException(statusCode, responseBody, requestMethod, requestUrl);
        }
        return new RemoteServiceException(statusCode, responseBody, requestMethod, requestUrl);
    }
}
