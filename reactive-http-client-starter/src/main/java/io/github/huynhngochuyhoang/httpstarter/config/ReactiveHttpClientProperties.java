package io.github.huynhngochuyhoang.httpstarter.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration properties for all reactive HTTP clients.
 * <p>
 * Example {@code application.yml}:
 * <pre>{@code
 * reactive:
 *   http:
 *     network:
 *       connect-timeout-ms: 2000
 *       read-timeout-ms: 5000
 *       write-timeout-ms: 5000
 *       connection-pool:
 *         max-connections: 200
 *         pending-acquire-timeout-ms: 5000
 *     clients:
 *       user-service:
 *         base-url: https://api.example.com
 *         codec-max-in-memory-size-mb: 2
 *         compression-enabled: false
 *         log-body: false
 *         auth-provider: userServiceAuthProvider
 *         resilience:
 *           enabled: false
 *           circuit-breaker: default
 *           retry: default
 *           bulkhead: default
 *           timeout-ms: 0
 * }</pre>
 */
@ConfigurationProperties(prefix = "reactive.http")
public class ReactiveHttpClientProperties {

    private NetworkConfig network = new NetworkConfig();
    private Map<String, ClientConfig> clients = new HashMap<>();

    public NetworkConfig getNetwork() { return network; }
    public void setNetwork(NetworkConfig network) { this.network = network; }

    public Map<String, ClientConfig> getClients() { return clients; }
    public void setClients(Map<String, ClientConfig> clients) { this.clients = clients; }

    // ---- global network configuration ----

    public static class NetworkConfig {
        private int connectTimeoutMs = 2000;
        private int readTimeoutMs = 5000;
        private int writeTimeoutMs = 5000;
        private ConnectionPoolConfig connectionPool = new ConnectionPoolConfig();

        public int getConnectTimeoutMs() { return connectTimeoutMs; }
        public void setConnectTimeoutMs(int connectTimeoutMs) { this.connectTimeoutMs = connectTimeoutMs; }

        public int getReadTimeoutMs() { return readTimeoutMs; }
        public void setReadTimeoutMs(int readTimeoutMs) { this.readTimeoutMs = readTimeoutMs; }

        public int getWriteTimeoutMs() { return writeTimeoutMs; }
        public void setWriteTimeoutMs(int writeTimeoutMs) { this.writeTimeoutMs = writeTimeoutMs; }

        public ConnectionPoolConfig getConnectionPool() { return connectionPool; }
        public void setConnectionPool(ConnectionPoolConfig connectionPool) { this.connectionPool = connectionPool; }
    }

    public static class ConnectionPoolConfig {
        private int maxConnections = 200;
        private long pendingAcquireTimeoutMs = 5000;

        public int getMaxConnections() { return maxConnections; }
        public void setMaxConnections(int maxConnections) { this.maxConnections = maxConnections; }

        public long getPendingAcquireTimeoutMs() { return pendingAcquireTimeoutMs; }
        public void setPendingAcquireTimeoutMs(long pendingAcquireTimeoutMs) {
            this.pendingAcquireTimeoutMs = pendingAcquireTimeoutMs;
        }
    }

    // ---- per-client configuration ----

    public static class ClientConfig {

        private String baseUrl;
        private int codecMaxInMemorySizeMb = 2;
        private boolean compressionEnabled = false;
        private boolean logBody = false;
        /**
         * Bean name of {@code AuthProvider} to use for this client.
         * Empty means no automatic auth injection.
         */
        private String authProvider;
        private ResilienceConfig resilience = new ResilienceConfig();

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

        public int getCodecMaxInMemorySizeMb() { return codecMaxInMemorySizeMb; }
        public void setCodecMaxInMemorySizeMb(int codecMaxInMemorySizeMb) { this.codecMaxInMemorySizeMb = codecMaxInMemorySizeMb; }

        public boolean isCompressionEnabled() { return compressionEnabled; }
        public void setCompressionEnabled(boolean compressionEnabled) { this.compressionEnabled = compressionEnabled; }

        public boolean isLogBody() { return logBody; }
        public void setLogBody(boolean logBody) { this.logBody = logBody; }

        public String getAuthProvider() { return authProvider; }
        public void setAuthProvider(String authProvider) { this.authProvider = authProvider; }

        public ResilienceConfig getResilience() { return resilience; }
        public void setResilience(ResilienceConfig resilience) { this.resilience = resilience; }
    }

    // ---- resilience sub-config ----

    public static class ResilienceConfig {

        private boolean enabled = false;
        /** Name of the Resilience4j CircuitBreaker instance (from application config). */
        private String circuitBreaker = "default";
        /** Name of the Resilience4j Retry instance. */
        private String retry = "default";
        /** Name of the Resilience4j Bulkhead instance. */
        private String bulkhead = "default";
        /** Request timeout in milliseconds (0 = disabled). */
        private long timeoutMs = 0;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public String getCircuitBreaker() { return circuitBreaker; }
        public void setCircuitBreaker(String circuitBreaker) { this.circuitBreaker = circuitBreaker; }

        public String getRetry() { return retry; }
        public void setRetry(String retry) { this.retry = retry; }

        public String getBulkhead() { return bulkhead; }
        public void setBulkhead(String bulkhead) { this.bulkhead = bulkhead; }

        public long getTimeoutMs() { return timeoutMs; }
        public void setTimeoutMs(long timeoutMs) { this.timeoutMs = timeoutMs; }
    }

    // ---- observability / metrics sub-config ----

    /**
     * Global observability settings (Micrometer metrics + tracing).
     * <p>
     * Example {@code application.yml}:
     * <pre>{@code
     * reactive:
     *   http:
     *     observability:
     *       enabled: true
     *       metric-name: http.client.requests
     *       include-url-path: true
     *       log-request-body: false
     * }</pre>
     */
    public static class ObservabilityConfig {

        /** Master switch – set to {@code false} to disable all metrics/tracing. */
        private boolean enabled = true;

        /** Micrometer timer/counter name (default: {@code http.client.requests}). */
        private String metricName = "http.client.requests";

        /**
         * Include the raw URL path as a tag.
         * Disable for high-cardinality path templates with many distinct IDs.
         */
        private boolean includeUrlPath = true;

        /** Log request body in span events (caution: PII / large payloads). */
        private boolean logRequestBody = false;

        /** Log response body in span events (caution: PII / large payloads). */
        private boolean logResponseBody = false;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public String getMetricName() { return metricName; }
        public void setMetricName(String metricName) { this.metricName = metricName; }

        public boolean isIncludeUrlPath() { return includeUrlPath; }
        public void setIncludeUrlPath(boolean includeUrlPath) { this.includeUrlPath = includeUrlPath; }

        public boolean isLogRequestBody() { return logRequestBody; }
        public void setLogRequestBody(boolean logRequestBody) { this.logRequestBody = logRequestBody; }

        public boolean isLogResponseBody() { return logResponseBody; }
        public void setLogResponseBody(boolean logResponseBody) { this.logResponseBody = logResponseBody; }
    }

    // ---- global observability config (not per-client) ----

    private ObservabilityConfig observability = new ObservabilityConfig();

    public ObservabilityConfig getObservability() { return observability; }
    public void setObservability(ObservabilityConfig observability) { this.observability = observability; }
}
