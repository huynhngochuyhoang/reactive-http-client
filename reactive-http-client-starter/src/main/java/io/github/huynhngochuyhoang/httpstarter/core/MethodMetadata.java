package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.annotation.FormFile;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Parsed metadata for a single method on a {@code @ReactiveHttpClient} interface.
 */
public class MethodMetadata {

    public static final long TIMEOUT_NOT_SET = -1L;

    private String httpMethod;
    private String pathTemplate;

    /** index → path variable name */
    private Map<Integer, String> pathVars = new HashMap<>();

    /** index → query parameter name */
    private Map<Integer, String> queryParams = new HashMap<>();

    /** index → header name */
    private Map<Integer, String> headerParams = new HashMap<>();
    /** parameter indexes of map-based headers */
    private Set<Integer> headerMapParams = new HashSet<>();

    /** parameter index of the @Body argument, or -1 if absent */
    private int bodyIndex = -1;

    /** {@code true} when the method is annotated {@code @MultipartBody}. */
    private boolean multipart;
    /** index → form-field name (for {@code @FormField} parameters). */
    private Map<Integer, String> formFieldParams = new HashMap<>();
    /** index → {@code @FormFile} metadata. */
    private Map<Integer, FormFile> formFileParams = new HashMap<>();

    private boolean returnsMono;
    private boolean returnsFlux;

    /** The inner type argument of Mono<T> / Flux<T>. */
    private Type responseType;
    private Method method;
    private String apiName;
    /** Method-level timeout override in milliseconds: {@link #TIMEOUT_NOT_SET} means not configured. */
    private long timeoutMs = TIMEOUT_NOT_SET;
    private boolean httpExchangeLoggingEnabled;
    private Class<? extends HttpExchangeLogger> httpExchangeLoggerClass;

    /** Per-method Resilience4j instance overrides; {@code null} means "fall back to client-level config". */
    private String retryInstanceName;
    private String circuitBreakerInstanceName;
    private String bulkheadInstanceName;

    /**
     * Resolved {@link HttpExchangeLogger} instance cached after first resolution.
     * Stored as a {@code volatile} field so that the write on first invocation is
     * visible to all subsequent invocations without a lock.  The {@code null} sentinel
     * means "not yet resolved"; the {@link #NOOP_EXCHANGE_LOGGER} sentinel means
     * "resolved to no logger" (i.e. logging is disabled for this method).
     */
    private static final HttpExchangeLogger NOOP_EXCHANGE_LOGGER = ctx -> {};
    private volatile HttpExchangeLogger resolvedExchangeLogger;

    // ---- getters / setters ----

    public String getHttpMethod() { return httpMethod; }
    public void setHttpMethod(String httpMethod) { this.httpMethod = httpMethod; }

    public String getPathTemplate() { return pathTemplate; }
    public void setPathTemplate(String pathTemplate) { this.pathTemplate = pathTemplate; }

    public Map<Integer, String> getPathVars() { return pathVars; }
    public Map<Integer, String> getQueryParams() { return queryParams; }
    public Map<Integer, String> getHeaderParams() { return headerParams; }
    public Set<Integer> getHeaderMapParams() { return headerMapParams; }

    public int getBodyIndex() { return bodyIndex; }
    public void setBodyIndex(int bodyIndex) { this.bodyIndex = bodyIndex; }

    public boolean isMultipart() { return multipart; }
    public void setMultipart(boolean multipart) { this.multipart = multipart; }

    public Map<Integer, String> getFormFieldParams() { return formFieldParams; }
    public Map<Integer, FormFile> getFormFileParams() { return formFileParams; }

    public boolean isReturnsMono() { return returnsMono; }
    public void setReturnsMono(boolean returnsMono) { this.returnsMono = returnsMono; }

    public boolean isReturnsFlux() { return returnsFlux; }
    public void setReturnsFlux(boolean returnsFlux) { this.returnsFlux = returnsFlux; }

    public Type getResponseType() { return responseType; }
    public void setResponseType(Type responseType) { this.responseType = responseType; }

    public Method getMethod() { return method; }
    public void setMethod(Method method) { this.method = method; }

    public String getApiName() { return apiName; }
    public void setApiName(String apiName) { this.apiName = apiName; }

    public long getTimeoutMs() { return timeoutMs; }
    public void setTimeoutMs(long timeoutMs) { this.timeoutMs = timeoutMs; }

    public boolean isHttpExchangeLoggingEnabled() { return httpExchangeLoggingEnabled; }
    public void setHttpExchangeLoggingEnabled(boolean httpExchangeLoggingEnabled) {
        this.httpExchangeLoggingEnabled = httpExchangeLoggingEnabled;
    }

    public Class<? extends HttpExchangeLogger> getHttpExchangeLoggerClass() { return httpExchangeLoggerClass; }
    public void setHttpExchangeLoggerClass(Class<? extends HttpExchangeLogger> httpExchangeLoggerClass) {
        this.httpExchangeLoggerClass = httpExchangeLoggerClass;
    }

    public String getRetryInstanceName() { return retryInstanceName; }
    public void setRetryInstanceName(String retryInstanceName) { this.retryInstanceName = retryInstanceName; }

    public String getCircuitBreakerInstanceName() { return circuitBreakerInstanceName; }
    public void setCircuitBreakerInstanceName(String circuitBreakerInstanceName) {
        this.circuitBreakerInstanceName = circuitBreakerInstanceName;
    }

    public String getBulkheadInstanceName() { return bulkheadInstanceName; }
    public void setBulkheadInstanceName(String bulkheadInstanceName) {
        this.bulkheadInstanceName = bulkheadInstanceName;
    }

    /**
     * Returns the cached exchange-logger for this method, or {@code null} if it has
     * not been resolved yet.  The sentinel value {@link #NOOP_EXCHANGE_LOGGER} indicates
     * that resolution has already happened and produced no logger (logging disabled).
     */
    public HttpExchangeLogger getResolvedExchangeLogger() { return resolvedExchangeLogger; }

    /**
     * Stores the resolved exchange-logger.  Pass {@link #NOOP_EXCHANGE_LOGGER} to record
     * that resolution has happened but produced no logger (so the null-check fast-path
     * above can be used even for methods where logging is disabled).
     */
    public void setResolvedExchangeLogger(HttpExchangeLogger resolvedExchangeLogger) {
        this.resolvedExchangeLogger = resolvedExchangeLogger;
    }

    public static HttpExchangeLogger noopExchangeLogger() { return NOOP_EXCHANGE_LOGGER; }

    void freezeCollections() {
        pathVars = Map.copyOf(pathVars);
        queryParams = Map.copyOf(queryParams);
        headerParams = Map.copyOf(headerParams);
        headerMapParams = Set.copyOf(headerMapParams);
        formFieldParams = Map.copyOf(formFieldParams);
        formFileParams = Map.copyOf(formFileParams);
    }
}
