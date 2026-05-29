package io.github.huynhngochuyhoang.httpstarter.observability;

import io.github.huynhngochuyhoang.httpstarter.exception.ErrorCategory;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable data class carrying all observable data for a single HTTP exchange.
 *
 * <p>Created by {@link io.github.huynhngochuyhoang.httpstarter.core.ReactiveClientInvocationHandler} after
 * each request completes and passed to every registered {@link HttpClientObserver}.
 */
public final class HttpClientObserverEvent {

    /** Sentinel for {@link #getRequestBytes()} / {@link #getResponseBytes()} when size could not be measured. */
    public static final long UNKNOWN_SIZE = -1L;

    private final String clientName;
    private final String apiName;
    private final String httpMethod;
    private final String uriPath;
    private final Integer statusCode;
    private final long durationMs;
    private final Throwable error;
    private final ErrorCategory errorCategory;
    private final Object requestBody;
    private final Object responseBody;
    private final int attemptCount;
    private final long requestBytes;
    private final long responseBytes;
    private final String serverAddress;
    private final Integer serverPort;
    private final String requestUrl;
    private final Map<String, String> requestHeaders;

    /**
     * @deprecated Use {@link #HttpClientObserverEvent(String, String, String, String, Integer, long, Throwable, ErrorCategory, Object, Object)}
     * to provide {@link ErrorCategory} explicitly.
     */
    @Deprecated(since = "1.5.1", forRemoval = false)
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
        this(clientName, apiName, httpMethod, uriPath, statusCode, durationMs, error, null, requestBody, responseBody);
    }

    public HttpClientObserverEvent(
            String clientName,
            String apiName,
            String httpMethod,
            String uriPath,
            Integer statusCode,
            long durationMs,
            Throwable error,
            ErrorCategory errorCategory,
            Object requestBody,
            Object responseBody) {
        this(clientName, apiName, httpMethod, uriPath, statusCode, durationMs, error, errorCategory, requestBody, responseBody, 1);
    }

    /**
     * @deprecated Use {@link #HttpClientObserverEvent(String, String, String, String, Integer, long, Throwable, ErrorCategory, Object, Object, int, long, long, String, Integer)}
     * to carry request / response byte sizes. This constructor defaults both sizes to
     * {@link #UNKNOWN_SIZE}, so the Micrometer observer will skip the size
     * distribution summaries for events constructed this way.
     */
    @Deprecated(since = "1.9.0", forRemoval = false)
    public HttpClientObserverEvent(
            String clientName,
            String apiName,
            String httpMethod,
            String uriPath,
            Integer statusCode,
            long durationMs,
            Throwable error,
            ErrorCategory errorCategory,
            Object requestBody,
            Object responseBody,
            int attemptCount) {
        this(clientName, apiName, httpMethod, uriPath, statusCode, durationMs, error, errorCategory,
                requestBody, responseBody, attemptCount, UNKNOWN_SIZE, UNKNOWN_SIZE);
    }

    /**
     * @deprecated Use {@link #HttpClientObserverEvent(String, String, String, String, Integer, long, Throwable, ErrorCategory, Object, Object, int, long, long, String, Integer)}
     * to carry resolved server address and port. This constructor keeps source compatibility
     * and leaves both server fields unset.
     */
    @Deprecated(since = "1.15.0", forRemoval = false)
    public HttpClientObserverEvent(
            String clientName,
            String apiName,
            String httpMethod,
            String uriPath,
            Integer statusCode,
            long durationMs,
            Throwable error,
            ErrorCategory errorCategory,
            Object requestBody,
            Object responseBody,
            int attemptCount,
            long requestBytes,
            long responseBytes) {
        this(clientName, apiName, httpMethod, uriPath, statusCode, durationMs, error, errorCategory,
                requestBody, responseBody, attemptCount, requestBytes, responseBytes, null, null);
    }

    public HttpClientObserverEvent(
            String clientName,
            String apiName,
            String httpMethod,
            String uriPath,
            Integer statusCode,
            long durationMs,
            Throwable error,
            ErrorCategory errorCategory,
            Object requestBody,
            Object responseBody,
            int attemptCount,
            long requestBytes,
            long responseBytes,
            String serverAddress,
            Integer serverPort) {
        this(clientName, apiName, httpMethod, uriPath, statusCode, durationMs, error, errorCategory,
                requestBody, responseBody, attemptCount, requestBytes, responseBytes, serverAddress, serverPort, null, Map.of());
    }

    public HttpClientObserverEvent(
            String clientName,
            String apiName,
            String httpMethod,
            String uriPath,
            Integer statusCode,
            long durationMs,
            Throwable error,
            ErrorCategory errorCategory,
            Object requestBody,
            Object responseBody,
            int attemptCount,
            long requestBytes,
            long responseBytes,
            String serverAddress,
            Integer serverPort,
            String requestUrl,
            Map<String, String> requestHeaders) {
        this.clientName = clientName;
        this.apiName = apiName;
        this.httpMethod = httpMethod;
        this.uriPath = uriPath;
        this.statusCode = statusCode;
        this.durationMs = durationMs;
        this.error = error;
        this.errorCategory = errorCategory;
        this.requestBody = requestBody;
        this.responseBody = responseBody;
        this.attemptCount = attemptCount;
        this.requestBytes = requestBytes;
        this.responseBytes = responseBytes;
        this.serverAddress = serverAddress;
        this.serverPort = serverPort;
        this.requestUrl = requestUrl;
        this.requestHeaders = requestHeaders == null || requestHeaders.isEmpty()
                ? Map.of()
                : Map.copyOf(new LinkedHashMap<>(requestHeaders));
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

    /** High-level error category when available; {@code null} for successful calls. */
    public ErrorCategory getErrorCategory() { return errorCategory; }

    /** The serialised request body (may be {@code null} for GET/DELETE). */
    public Object getRequestBody() { return requestBody; }

    /**
     * The deserialised response body (may be {@code null} for empty or error responses).
     * For {@code Flux<T>} responses this field is always {@code null}.
     */
    public Object getResponseBody() { return responseBody; }

    /**
     * Total number of subscription attempts made to the underlying publisher, including the
     * first attempt. Values greater than 1 indicate that Resilience4j retry fired at least once.
     * Useful for detecting whether a downstream service is degraded.
     */
    public int getAttemptCount() { return attemptCount; }

    /**
     * Size of the serialised request body in bytes, or {@link #UNKNOWN_SIZE} when the
     * starter could not measure it cheaply (i.e. body is a non-{@code byte[]}/{@code String}
     * object whose serialized form isn't materialised synchronously on the invocation path).
     */
    public long getRequestBytes() { return requestBytes; }

    /**
     * Size of the response body in bytes as advertised by the server via
     * {@code Content-Length}. {@link #UNKNOWN_SIZE} when the header was absent (e.g.
     * chunked transfer encoding or no body).
     */
    public long getResponseBytes() { return responseBytes; }

    /** Resolved outbound server host, or {@code null} when unavailable. */
    public String getServerAddress() { return serverAddress; }

    /** Resolved outbound server port, or {@code null} when unavailable. */
    public Integer getServerPort() { return serverPort; }

    /** Final outbound request URL after WebClient filters have run, or {@code null} when unavailable. */
    public String getRequestUrl() { return requestUrl; }

    /** Final outbound request headers after WebClient filters have run. */
    public Map<String, String> getRequestHeaders() { return requestHeaders; }

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
                ", attemptCount=" + attemptCount +
                ", serverAddress='" + serverAddress + '\'' +
                ", serverPort=" + serverPort +
                ", error=" + (error != null ? error.getClass().getSimpleName() : "none") +
                ", errorCategory=" + (errorCategory != null ? errorCategory.name() : "none") +
                '}';
    }
}
