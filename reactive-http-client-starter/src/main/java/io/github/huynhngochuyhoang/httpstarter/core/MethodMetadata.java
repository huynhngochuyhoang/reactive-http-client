package io.github.huynhngochuyhoang.httpstarter.core;

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
    private final Map<Integer, String> pathVars = new HashMap<>();

    /** index → query parameter name */
    private final Map<Integer, String> queryParams = new HashMap<>();

    /** index → header name */
    private final Map<Integer, String> headerParams = new HashMap<>();
    /** parameter indexes of map-based headers */
    private final Set<Integer> headerMapParams = new HashSet<>();

    /** parameter index of the @Body argument, or -1 if absent */
    private int bodyIndex = -1;

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
}
