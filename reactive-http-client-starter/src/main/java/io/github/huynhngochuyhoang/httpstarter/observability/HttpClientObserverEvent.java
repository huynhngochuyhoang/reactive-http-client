package io.github.huynhngochuyhoang.httpstarter.observability;

/**
 * Immutable data class carrying all observable data for a single HTTP exchange.
 *
 * <p>Created by {@link io.github.huynhngochuyhoang.httpstarter.core.ReactiveClientInvocationHandler} after
 * each request completes and passed to every registered {@link HttpClientObserver}.
 */
public final class HttpClientObserverEvent {

    private final String clientName;
    private final String apiName;
    private final String httpMethod;
    private final String uriPath;
    private final Integer statusCode;
    private final long durationMs;
    private final Throwable error;
    private final Object requestBody;
    private final Object responseBody;

    public HttpClientObserverEvent(
            String clientName,
            String apiName,
            String httpMethod,
            String uriPath,
            Integer statusCode,
            long durationMs,
            Throwable error,
            Object requestBody,
            Object responseBody) {
        this.clientName = clientName;
        this.apiName = apiName;
        this.httpMethod = httpMethod;
        this.uriPath = uriPath;
        this.statusCode = statusCode;
        this.durationMs = durationMs;
        this.error = error;
        this.requestBody = requestBody;
        this.responseBody = responseBody;
    }

    /** The logical name of the client (value of {@code @ReactiveHttpClient(name = ...)}). */
    public String getClientName() { return clientName; }

    /** Logical API name for the method ({@code @ApiName} or Java method name by default). */
    public String getApiName() { return apiName; }

    /** HTTP verb: {@code GET}, {@code POST}, {@code PUT}, {@code DELETE}, etc. */
    public String getHttpMethod() { return httpMethod; }

    /** The path template, e.g. {@code /users/{id}}. */
    public String getUriPath() { return uriPath; }

    /** HTTP response status code, or {@code null} if the request never reached the server. */
    public Integer getStatusCode() { return statusCode; }

    /**
     * Elapsed wall-clock time in milliseconds from the start of the request to response completion.
     * For {@code Mono<T>} responses this is the time to receive the single value (or error).
     * For {@code Flux<T>} (streaming) responses this is the time until all items have been emitted.
     */
    public long getDurationMs() { return durationMs; }

    /** Non-null when the exchange ended with an error (network failure, timeout, error-decoded exception, …). */
    public Throwable getError() { return error; }

    /** The serialised request body (may be {@code null} for GET/DELETE). */
    public Object getRequestBody() { return requestBody; }

    /** The deserialised response body (may be {@code null} for empty or error responses). */
    public Object getResponseBody() { return responseBody; }

    /** {@code true} when {@link #getError()} is non-null. */
    public boolean isError() { return error != null; }

    @Override
    public String toString() {
        return "HttpClientObserverEvent{" +
                "clientName='" + clientName + '\'' +
                ", apiName='" + apiName + '\'' +
                ", httpMethod='" + httpMethod + '\'' +
                ", uriPath='" + uriPath + '\'' +
                ", statusCode=" + statusCode +
                ", durationMs=" + durationMs +
                ", error=" + (error != null ? error.getClass().getSimpleName() : "none") +
                '}';
    }
}
