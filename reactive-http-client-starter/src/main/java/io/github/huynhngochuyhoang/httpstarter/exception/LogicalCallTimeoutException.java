package io.github.huynhngochuyhoang.httpstarter.exception;

import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientFailureStage;

import java.util.concurrent.TimeoutException;

/**
 * Signals exhaustion of an opt-in end-to-end logical-call timeout budget.
 *
 * <p>The budget spans resilience admission, retries, redirects, authentication,
 * pool acquisition, request exchange, and response consumption owned by the
 * returned publisher. {@link #getFailureStage()} is {@code null} unless the
 * final attempt supplied conclusive phase evidence when the budget expired.
 */
public final class LogicalCallTimeoutException extends TimeoutException {

    private final long timeoutMs;
    private final HttpClientFailureStage failureStage;

    public LogicalCallTimeoutException(long timeoutMs, HttpClientFailureStage failureStage) {
        super("Reactive HTTP client logical call exceeded its " + timeoutMs + "ms timeout budget");
        if (timeoutMs <= 0) {
            throw new IllegalArgumentException("timeoutMs must be greater than 0");
        }
        this.timeoutMs = timeoutMs;
        this.failureStage = failureStage;
    }

    public long getTimeoutMs() {
        return timeoutMs;
    }

    public HttpClientFailureStage getFailureStage() {
        return failureStage;
    }
}
