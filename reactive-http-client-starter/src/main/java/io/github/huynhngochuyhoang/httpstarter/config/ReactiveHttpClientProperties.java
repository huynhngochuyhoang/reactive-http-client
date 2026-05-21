package io.github.huynhngochuyhoang.httpstarter.config;

import io.github.huynhngochuyhoang.httpstarter.core.SensitiveHeaders;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.DeprecatedConfigurationProperty;

import java.util.*;

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
 *         log-exchange: false
 *         request-timeout-ms: 5000
 *         auth-provider: userServiceAuthProvider
 *         resilience:
 *           enabled: false
 *           circuit-breaker: default
 *           retry: default
 *           retry-methods: [GET, HEAD]
 *           bulkhead: default
 * }</pre>
 */
@ConfigurationProperties(prefix = "reactive.http")
public class ReactiveHttpClientProperties {

    private NetworkConfig network = new NetworkConfig();
    private Map<String, ClientConfig> clients = new HashMap<>();
    private CorrelationIdConfig correlationId = new CorrelationIdConfig();
    private InboundHeadersConfig inboundHeaders = new InboundHeadersConfig();

    public NetworkConfig getNetwork() { return network; }
    public void setNetwork(NetworkConfig network) { this.network = network; }

    public Map<String, ClientConfig> getClients() { return clients; }
    public void setClients(Map<String, ClientConfig> clients) { this.clients = clients; }

    public CorrelationIdConfig getCorrelationId() { return correlationId; }
    public void setCorrelationId(CorrelationIdConfig correlationId) {
        this.correlationId = correlationId != null ? correlationId : new CorrelationIdConfig();
    }

    public InboundHeadersConfig getInboundHeaders() { return inboundHeaders; }
    public void setInboundHeaders(InboundHeadersConfig inboundHeaders) {
        this.inboundHeaders = inboundHeaders != null ? inboundHeaders : new InboundHeadersConfig();
    }

    private static int requireAtLeast(String propertyName, int value, int min) {
        if (value < min) {
            throw new IllegalArgumentException(propertyName + " must be >= " + min + " but was " + value + ".");
        }
        return value;
    }

    private static long requireAtLeast(String propertyName, long value, long min) {
        if (value < min) {
            throw new IllegalArgumentException(propertyName + " must be >= " + min + " but was " + value + ".");
        }
        return value;
    }

    private static long requireAtMost(String propertyName, long value, long max) {
        if (value > max) {
            throw new IllegalArgumentException(propertyName + " must be <= " + max + " but was " + value + ".");
        }
        return value;
    }

    // ---- global network configuration ----

    /**
     * Global Netty-level network policy applied to every client.
     *
     * <p>Two distinct timeout layers act on outbound calls, and confusion between
     * them has been a repeat source of incidents:
     *
     * <ul>
     *   <li><b>Network safety-net timeouts</b>
     *       ({@link #getNetworkReadTimeoutMs() network-read-timeout-ms} /
     *        {@link #getNetworkWriteTimeoutMs() network-write-timeout-ms}) —
     *       Netty {@code ReadTimeoutHandler} / {@code WriteTimeoutHandler}
     *       attached to every pooled connection. These are absolute upper bounds,
     *       sized larger than any per-request timeout. They catch stuck pooled
     *       connections, not ordinary slow responses.</li>
     *   <li><b>Per-request response timeouts</b>
     *       (method-level {@link io.github.huynhngochuyhoang.httpstarter.annotation.TimeoutMs @TimeoutMs}
     *       or client-level {@code request-timeout-ms}) — applied via
     *       {@code HttpClientRequest.responseTimeout()} on each attempt. This is
     *       the timeout most callers want to tune.</li>
     * </ul>
     *
     * <p>The per-request timeout always fires first if both are set. The safety
     * nets should be set well above the largest business timeout. Defaults:
     * 60 s for each safety-net timeout; no per-request timeout.
     *
     * <p>The legacy property names {@code read-timeout-ms} and
     * {@code write-timeout-ms} are accepted as aliases for the canonical
     * {@code network-read-timeout-ms} / {@code network-write-timeout-ms}. Both
     * bind to the same backing field; pick one. The legacy names are deprecated
     * and will be removed in a future major release.
     */
    public static class NetworkConfig {
        private int connectTimeoutMs = 2000;
        private int networkReadTimeoutMs = 60_000;
        private int networkWriteTimeoutMs = 60_000;
        private ConnectionPoolConfig connectionPool = new ConnectionPoolConfig();
        /** Optional global proxy applied to every client. {@code null} = direct connection. */
        private ProxyConfig proxy;
        /** Optional global TLS configuration applied to every client. {@code null} = JDK defaults. */
        private TlsConfig tls;

        public int getConnectTimeoutMs() { return connectTimeoutMs; }
        public void setConnectTimeoutMs(int connectTimeoutMs) {
            this.connectTimeoutMs = requireAtLeast("reactive.http.network.connect-timeout-ms", connectTimeoutMs, 1);
        }

        /**
         * Canonical name for the Netty {@code ReadTimeoutHandler} safety-net timeout.
         * Fires when a pooled connection produces no inbound bytes for this duration.
         */
        public int getNetworkReadTimeoutMs() { return networkReadTimeoutMs; }
        public void setNetworkReadTimeoutMs(int networkReadTimeoutMs) {
            this.networkReadTimeoutMs = requireAtLeast("reactive.http.network.network-read-timeout-ms", networkReadTimeoutMs, 1);
        }

        /**
         * Canonical name for the Netty {@code WriteTimeoutHandler} safety-net timeout.
         * Fires when a pooled connection accepts no outbound bytes for this duration.
         */
        public int getNetworkWriteTimeoutMs() { return networkWriteTimeoutMs; }
        public void setNetworkWriteTimeoutMs(int networkWriteTimeoutMs) {
            this.networkWriteTimeoutMs = requireAtLeast("reactive.http.network.network-write-timeout-ms", networkWriteTimeoutMs, 1);
        }

        /**
         * @deprecated use {@link #getNetworkReadTimeoutMs()} / {@code network-read-timeout-ms}.
         *             Kept as a YAML alias bound to the same backing field.
         */
        @Deprecated
        @DeprecatedConfigurationProperty(replacement = "reactive.http.network.network-read-timeout-ms")
        public int getReadTimeoutMs() { return networkReadTimeoutMs; }

        /** @deprecated setter retained so {@code read-timeout-ms} continues to bind. */
        @Deprecated
        public void setReadTimeoutMs(int readTimeoutMs) {
            this.networkReadTimeoutMs = requireAtLeast("reactive.http.network.read-timeout-ms", readTimeoutMs, 1);
        }

        /**
         * @deprecated use {@link #getNetworkWriteTimeoutMs()} / {@code network-write-timeout-ms}.
         *             Kept as a YAML alias bound to the same backing field.
         */
        @Deprecated
        @DeprecatedConfigurationProperty(replacement = "reactive.http.network.network-write-timeout-ms")
        public int getWriteTimeoutMs() { return networkWriteTimeoutMs; }

        /** @deprecated setter retained so {@code write-timeout-ms} continues to bind. */
        @Deprecated
        public void setWriteTimeoutMs(int writeTimeoutMs) {
            this.networkWriteTimeoutMs = requireAtLeast("reactive.http.network.write-timeout-ms", writeTimeoutMs, 1);
        }

        public ConnectionPoolConfig getConnectionPool() { return connectionPool; }
        public void setConnectionPool(ConnectionPoolConfig connectionPool) { this.connectionPool = connectionPool; }

        public ProxyConfig getProxy() { return proxy; }
        public void setProxy(ProxyConfig proxy) { this.proxy = proxy; }

        public TlsConfig getTls() { return tls; }
        public void setTls(TlsConfig tls) { this.tls = tls; }
    }

    public static class ConnectionPoolConfig {
        private int maxConnections = 200;
        private long pendingAcquireTimeoutMs = 5000;
        /** Idle duration after which a pooled connection is evicted. {@code 0} leaves Reactor Netty's default (no idle eviction). */
        private long maxIdleTimeMs = 0;
        /** Max lifetime of a pooled connection. {@code 0} leaves Reactor Netty's default (unlimited). */
        private long maxLifeTimeMs = 0;
        /** Interval at which the provider sweeps for evictable connections. {@code 0} disables background eviction. */
        private long evictInBackgroundMs = 0;
        /**
         * When {@code true}, the provider publishes Reactor Netty's built-in pool metrics
         * ({@code reactor.netty.connection.provider.*} gauges) to the globally-registered
         * {@code MeterRegistry}. Requires {@code micrometer-core} on the classpath;
         * leave {@code false} (the default) to avoid the small per-request overhead
         * when pool visibility isn't needed.
         */
        private boolean metricsEnabled = false;

        public int getMaxConnections() { return maxConnections; }
        public void setMaxConnections(int maxConnections) {
            this.maxConnections = requireAtLeast("reactive.http.network.connection-pool.max-connections", maxConnections, 1);
        }

        public long getPendingAcquireTimeoutMs() { return pendingAcquireTimeoutMs; }
        public void setPendingAcquireTimeoutMs(long pendingAcquireTimeoutMs) {
            this.pendingAcquireTimeoutMs = requireAtLeast("reactive.http.network.connection-pool.pending-acquire-timeout-ms", pendingAcquireTimeoutMs, 0);
        }

        public long getMaxIdleTimeMs() { return maxIdleTimeMs; }
        public void setMaxIdleTimeMs(long maxIdleTimeMs) {
            this.maxIdleTimeMs = requireAtLeast("reactive.http.network.connection-pool.max-idle-time-ms", maxIdleTimeMs, 0);
        }

        public long getMaxLifeTimeMs() { return maxLifeTimeMs; }
        public void setMaxLifeTimeMs(long maxLifeTimeMs) {
            this.maxLifeTimeMs = requireAtLeast("reactive.http.network.connection-pool.max-life-time-ms", maxLifeTimeMs, 0);
        }

        public long getEvictInBackgroundMs() { return evictInBackgroundMs; }
        public void setEvictInBackgroundMs(long evictInBackgroundMs) {
            this.evictInBackgroundMs = requireAtLeast("reactive.http.network.connection-pool.evict-in-background-ms", evictInBackgroundMs, 0);
        }

        public boolean isMetricsEnabled() { return metricsEnabled; }
        public void setMetricsEnabled(boolean metricsEnabled) { this.metricsEnabled = metricsEnabled; }
    }

    // ---- HTTP proxy configuration ----

    /**
     * Routes outbound calls through an HTTP / HTTPS / SOCKS proxy.
     *
     * <p>Example:
     * <pre>{@code
     * reactive:
     *   http:
     *     network:
     *       proxy:
     *         type: HTTP
     *         host: proxy.example.com
     *         port: 8080
     *         username: ${PROXY_USER}
     *         password: ${PROXY_PASS}
     *         non-proxy-hosts: "localhost|.*\\.internal"
     * }</pre>
     */
    public static class ProxyConfig {

        public enum Type { HTTP, HTTPS, SOCKS4, SOCKS5, NONE }

        /** Proxy protocol; set to {@link Type#NONE} to explicitly disable inherited global proxy. */
        private Type type = Type.HTTP;
        private String host;
        private int port;
        private String username;
        private String password;
        /**
         * Reactor Netty {@code nonProxyHosts} pattern. Java {@link java.util.regex.Pattern}
         * syntax — pipe-separated alternatives. Use {@code .*\.internal} (a real
         * regex), not {@code *.internal} (a glob). {@code null} = always go via the
         * proxy.
         */
        private String nonProxyHosts;

        public Type getType() { return type; }
        public void setType(Type type) { this.type = type; }

        public String getHost() { return host; }
        public void setHost(String host) { this.host = host; }

        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }

        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }

        public String getNonProxyHosts() { return nonProxyHosts; }
        public void setNonProxyHosts(String nonProxyHosts) { this.nonProxyHosts = nonProxyHosts; }
    }

    // ---- TLS / mTLS configuration ----

    /**
     * Custom SSL / mTLS configuration. Truststore and keystore paths are resolved
     * via Spring's {@link org.springframework.core.io.DefaultResourceLoader} so
     * {@code classpath:}, {@code file:} and absolute paths all work.
     *
     * <p>Example:
     * <pre>{@code
     * reactive:
     *   http:
     *     network:
     *       tls:
     *         trust-store: classpath:certs/truststore.p12
     *         trust-store-password: changeit
     *         trust-store-type: PKCS12
     *         key-store: classpath:certs/client.p12
     *         key-store-password: changeit
     *         key-store-type: PKCS12
     *         protocols: [TLSv1.3, TLSv1.2]
     *         ciphers: []
     *         insecure-trust-all: false
     * }</pre>
     *
     * <p>Setting {@code insecure-trust-all: true} disables certificate verification
     * — only acceptable in development environments. The starter logs a WARN when
     * this is enabled.
     */
    public static class TlsConfig {
        private String trustStore;
        private String trustStorePassword;
        private String trustStoreType = "PKCS12";

        private String keyStore;
        private String keyStorePassword;
        private String keyStoreType = "PKCS12";

        private java.util.List<String> protocols = java.util.List.of();
        private java.util.List<String> ciphers = java.util.List.of();
        /** {@code true} disables certificate validation — never use in production. */
        private boolean insecureTrustAll = false;

        public String getTrustStore() { return trustStore; }
        public void setTrustStore(String trustStore) { this.trustStore = trustStore; }
        public String getTrustStorePassword() { return trustStorePassword; }
        public void setTrustStorePassword(String trustStorePassword) { this.trustStorePassword = trustStorePassword; }
        public String getTrustStoreType() { return trustStoreType; }
        public void setTrustStoreType(String trustStoreType) { this.trustStoreType = trustStoreType; }

        public String getKeyStore() { return keyStore; }
        public void setKeyStore(String keyStore) { this.keyStore = keyStore; }
        public String getKeyStorePassword() { return keyStorePassword; }
        public void setKeyStorePassword(String keyStorePassword) { this.keyStorePassword = keyStorePassword; }
        public String getKeyStoreType() { return keyStoreType; }
        public void setKeyStoreType(String keyStoreType) { this.keyStoreType = keyStoreType; }

        public java.util.List<String> getProtocols() { return protocols; }
        public void setProtocols(java.util.List<String> protocols) {
            this.protocols = protocols == null ? java.util.List.of() : java.util.List.copyOf(protocols);
        }
        public java.util.List<String> getCiphers() { return ciphers; }
        public void setCiphers(java.util.List<String> ciphers) {
            this.ciphers = ciphers == null ? java.util.List.of() : java.util.List.copyOf(ciphers);
        }

        public boolean isInsecureTrustAll() { return insecureTrustAll; }
        public void setInsecureTrustAll(boolean insecureTrustAll) { this.insecureTrustAll = insecureTrustAll; }
    }

    // ---- per-client configuration ----

    public static class ClientConfig {

        private static final int MAX_CODEC_MAX_IN_MEMORY_SIZE_MB = Integer.MAX_VALUE / (1024 * 1024);
        private static final long MAX_REQUEST_TIMEOUT_MS = 30L * 60 * 1000;

        private String baseUrl;
        private int codecMaxInMemorySizeMb = 2;
        private boolean compressionEnabled = false;
        private boolean http2Enabled = false;
        private boolean logExchange = false;
        private LogPreset logPreset = LogPreset.METADATA_ONLY;
        /** Per-request response timeout in milliseconds. {@code null} means not configured; {@code 0} disables it. */
        private Long requestTimeoutMs;
        /**
         * Bean name of {@code AuthProvider} to use for this client.
         * Empty means no automatic auth injection.
         */
        private String authProvider;
        /**
         * Object-style auth-provider configuration. When set, this is used only
         * if {@link #authProvider} is blank.
         */
        private AuthConfig auth;
        private ResilienceConfig resilience = new ResilienceConfig();
        /**
         * Static headers added to every request for this client. Method-level
         * {@code @HeaderParam} values with the same name override these defaults.
         */
        private Map<String, String> defaultHeaders = new HashMap<>();
        /**
         * Static query parameters added to every request for this client. Method-level
         * {@code @QueryParam} values with the same name override these defaults.
         */
        private Map<String, List<String>> defaultQueryParams = new HashMap<>();
        /**
         * Optional named API definitions used by {@code @ApiRef}.
         * Keys are logical API names; values define method/path/timeout.
         */
        private Map<String, ApiConfig> apis = new HashMap<>();
        /**
         * Per-client connection-pool override. When {@code null}, the client inherits
         * {@link NetworkConfig#getConnectionPool()}. When set, every field on this
         * instance takes precedence — there is no field-level merging.
         */
        private ConnectionPoolConfig pool;
        /**
         * Per-client HTTP proxy override. When {@code null}, the client inherits
         * {@link NetworkConfig#getProxy()}. Set to disable a global proxy for one
         * client, supply {@code type: NONE} or override host/port to {@code null}.
         */
        private ProxyConfig proxy;
        /**
         * Per-client TLS override. When {@code null}, the client inherits
         * {@link NetworkConfig#getTls()}. When set, every field on this instance
         * takes precedence over the global block (no field-level merging).
         */
        private TlsConfig tls;

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

        public int getCodecMaxInMemorySizeMb() { return codecMaxInMemorySizeMb; }
        public void setCodecMaxInMemorySizeMb(int codecMaxInMemorySizeMb) {
            requireAtLeast("reactive.http.clients.*.codec-max-in-memory-size-mb", codecMaxInMemorySizeMb, 0);
            requireAtMost("reactive.http.clients.*.codec-max-in-memory-size-mb", codecMaxInMemorySizeMb, MAX_CODEC_MAX_IN_MEMORY_SIZE_MB);
            this.codecMaxInMemorySizeMb = codecMaxInMemorySizeMb;
        }

        public boolean isCompressionEnabled() { return compressionEnabled; }
        public void setCompressionEnabled(boolean compressionEnabled) { this.compressionEnabled = compressionEnabled; }

        public boolean isHttp2Enabled() { return http2Enabled; }
        public void setHttp2Enabled(boolean http2Enabled) { this.http2Enabled = http2Enabled; }

        public boolean isLogExchange() { return logExchange; }
        public void setLogExchange(boolean logExchange) { this.logExchange = logExchange; }

        public LogPreset getLogPreset() { return logPreset; }
        public void setLogPreset(LogPreset logPreset) {
            this.logPreset = logPreset != null ? logPreset : LogPreset.METADATA_ONLY;
        }

        public long getRequestTimeoutMs() { return requestTimeoutMs != null ? requestTimeoutMs : 0; }
        public void setRequestTimeoutMs(long requestTimeoutMs) { setRequestTimeoutMs(Long.valueOf(requestTimeoutMs)); }
        public void setRequestTimeoutMs(Long requestTimeoutMs) {
            if (requestTimeoutMs == null) {
                this.requestTimeoutMs = null;
                return;
            }
            requireAtLeast("reactive.http.clients.*.request-timeout-ms", requestTimeoutMs, 0);
            requireAtMost("reactive.http.clients.*.request-timeout-ms", requestTimeoutMs, MAX_REQUEST_TIMEOUT_MS);
            this.requestTimeoutMs = requestTimeoutMs;
        }
        public boolean isRequestTimeoutMsConfigured() { return requestTimeoutMs != null; }

        public boolean isExchangeLoggingEnabled() {
            return logExchange;
        }

        public void setExchangeLoggingEnabled(boolean exchangeLoggingEnabled) {
            this.logExchange = exchangeLoggingEnabled;
        }

        public String getAuthProvider() { return authProvider; }
        public void setAuthProvider(String authProvider) { this.authProvider = authProvider; }

        public AuthConfig getAuth() { return auth; }
        public void setAuth(AuthConfig auth) { this.auth = auth; }

        public boolean hasAuthConfigured() {
            return org.springframework.util.StringUtils.hasText(authProvider)
                    || (auth != null && org.springframework.util.StringUtils.hasText(auth.getType()));
        }

        public ResilienceConfig getResilience() { return resilience; }
        public void setResilience(ResilienceConfig resilience) { this.resilience = resilience; }

        public Map<String, String> getDefaultHeaders() { return defaultHeaders; }
        public void setDefaultHeaders(Map<String, String> defaultHeaders) {
            this.defaultHeaders = defaultHeaders != null ? defaultHeaders : new HashMap<>();
        }

        public Map<String, List<String>> getDefaultQueryParams() { return defaultQueryParams; }
        public void setDefaultQueryParams(Map<String, List<String>> defaultQueryParams) {
            this.defaultQueryParams = defaultQueryParams != null ? defaultQueryParams : new HashMap<>();
        }

        public Map<String, ApiConfig> getApis() { return apis; }
        public void setApis(Map<String, ApiConfig> apis) {
            this.apis = apis != null ? apis : new HashMap<>();
        }

        public ConnectionPoolConfig getPool() { return pool; }
        public void setPool(ConnectionPoolConfig pool) { this.pool = pool; }

        public ProxyConfig getProxy() { return proxy; }
        public void setProxy(ProxyConfig proxy) { this.proxy = proxy; }

        public TlsConfig getTls() { return tls; }
        public void setTls(TlsConfig tls) { this.tls = tls; }
    }

    public enum LogPreset {
        METADATA_ONLY,
        HEADERS,
        BODIES
    }

    public static class AuthConfig {
        private String type;
        private OAuth2ClientCredentialsAuthConfig oauth2ClientCredentials = new OAuth2ClientCredentialsAuthConfig();
        private AwsSigV4AuthConfig awsSigV4 = new AwsSigV4AuthConfig();

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        public OAuth2ClientCredentialsAuthConfig getOauth2ClientCredentials() { return oauth2ClientCredentials; }
        public void setOauth2ClientCredentials(OAuth2ClientCredentialsAuthConfig oauth2ClientCredentials) {
            this.oauth2ClientCredentials = oauth2ClientCredentials != null
                    ? oauth2ClientCredentials
                    : new OAuth2ClientCredentialsAuthConfig();
        }

        public AwsSigV4AuthConfig getAwsSigV4() { return awsSigV4; }
        public void setAwsSigV4(AwsSigV4AuthConfig awsSigV4) {
            this.awsSigV4 = awsSigV4 != null ? awsSigV4 : new AwsSigV4AuthConfig();
        }
    }

    public static class OAuth2ClientCredentialsAuthConfig {
        private String tokenUri;
        private String clientId;
        private String clientSecret;
        private String scope;
        private String audience;
        private String authStyle;
        private long expiryLeewayMs = 30_000;

        public String getTokenUri() { return tokenUri; }
        public void setTokenUri(String tokenUri) { this.tokenUri = tokenUri; }

        public String getClientId() { return clientId; }
        public void setClientId(String clientId) { this.clientId = clientId; }

        public String getClientSecret() { return clientSecret; }
        public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }

        public String getScope() { return scope; }
        public void setScope(String scope) { this.scope = scope; }

        public String getAudience() { return audience; }
        public void setAudience(String audience) { this.audience = audience; }

        public String getAuthStyle() { return authStyle; }
        public void setAuthStyle(String authStyle) { this.authStyle = authStyle; }

        public long getExpiryLeewayMs() { return expiryLeewayMs; }
        public void setExpiryLeewayMs(long expiryLeewayMs) { this.expiryLeewayMs = expiryLeewayMs; }
    }

    public static class AwsSigV4AuthConfig {
        private String accessKeyId;
        private String secretAccessKey;
        private String sessionToken;
        private String region;
        private String service;

        public String getAccessKeyId() { return accessKeyId; }
        public void setAccessKeyId(String accessKeyId) { this.accessKeyId = accessKeyId; }

        public String getSecretAccessKey() { return secretAccessKey; }
        public void setSecretAccessKey(String secretAccessKey) { this.secretAccessKey = secretAccessKey; }

        public String getSessionToken() { return sessionToken; }
        public void setSessionToken(String sessionToken) { this.sessionToken = sessionToken; }

        public String getRegion() { return region; }
        public void setRegion(String region) { this.region = region; }

        public String getService() { return service; }
        public void setService(String service) { this.service = service; }
    }

    public static class ApiConfig {
        private static final long MAX_TIMEOUT_MS = 30L * 60 * 1000;
        private String method;
        private String path;
        /** Timeout in milliseconds. {@code -1} means not configured. */
        private long timeoutMs = -1;

        public String getMethod() { return method; }
        public void setMethod(String method) {
            this.method = method != null ? method.trim().toUpperCase(Locale.ROOT) : null;
        }

        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }

        public long getTimeoutMs() { return timeoutMs; }
        public void setTimeoutMs(long timeoutMs) {
            if (timeoutMs < -1) {
                throw new IllegalArgumentException("reactive.http.clients.*.apis.*.timeout-ms must be >= -1.");
            }
            if (timeoutMs > MAX_TIMEOUT_MS) {
                throw new IllegalArgumentException("reactive.http.clients.*.apis.*.timeout-ms must be <= "
                        + MAX_TIMEOUT_MS + " ms (30 minutes).");
            }
            this.timeoutMs = timeoutMs;
        }
    }

    // ---- resilience sub-config ----

    public static class ResilienceConfig {

        private static final long MAX_TIMEOUT_MS = 30L * 60 * 1000;

        private boolean enabled = false;
        /** Name of the Resilience4j CircuitBreaker instance (from application config). */
        private String circuitBreaker = "default";
        /** Name of the Resilience4j Retry instance. */
        private String retry = "default";
        /**
         * HTTP methods eligible for retry.
         * Defaults to idempotent-safe methods.
         */
        private Set<String> retryMethods = new LinkedHashSet<>(Set.of("GET", "HEAD"));
        /** Name of the Resilience4j Bulkhead instance. */
        private String bulkhead = "default";
        /** Name of the Resilience4j RateLimiter instance. */
        private String rateLimiter = "default";
        /** @deprecated use client-level request-timeout-ms. */
        @Deprecated
        private Long timeoutMs;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public String getCircuitBreaker() { return circuitBreaker; }
        public void setCircuitBreaker(String circuitBreaker) { this.circuitBreaker = circuitBreaker; }

        public String getRetry() { return retry; }
        public void setRetry(String retry) { this.retry = retry; }

        public Set<String> getRetryMethods() { return retryMethods; }
        public void setRetryMethods(Set<String> retryMethods) {
            if (retryMethods == null || retryMethods.isEmpty()) {
                this.retryMethods = new LinkedHashSet<>();
                return;
            }
            LinkedHashSet<String> normalized = new LinkedHashSet<>();
            retryMethods.stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(value -> !value.isEmpty())
                    .map(value -> value.toUpperCase(Locale.ROOT))
                    .forEach(normalized::add);
            this.retryMethods = normalized;
        }

        public String getBulkhead() { return bulkhead; }
        public void setBulkhead(String bulkhead) { this.bulkhead = bulkhead; }

        public String getRateLimiter() { return rateLimiter; }
        public void setRateLimiter(String rateLimiter) { this.rateLimiter = rateLimiter; }

        /**
         * @deprecated use {@code reactive.http.clients.<name>.request-timeout-ms}.
         */
        @Deprecated
        @DeprecatedConfigurationProperty(replacement = "reactive.http.clients.[name].request-timeout-ms")
        public long getTimeoutMs() { return timeoutMs != null ? timeoutMs : 0; }
        /** @deprecated setter retained so {@code resilience.timeout-ms} continues to bind for one compatibility cycle. */
        @Deprecated
        public void setTimeoutMs(long timeoutMs) { setTimeoutMs(Long.valueOf(timeoutMs)); }
        /** @deprecated setter retained so {@code resilience.timeout-ms} continues to bind for one compatibility cycle. */
        @Deprecated
        public void setTimeoutMs(Long timeoutMs) {
            if (timeoutMs == null) {
                this.timeoutMs = null;
                return;
            }
            requireAtLeast("reactive.http.clients.*.resilience.timeout-ms", timeoutMs, 0);
            requireAtMost("reactive.http.clients.*.resilience.timeout-ms", timeoutMs, MAX_TIMEOUT_MS);
            this.timeoutMs = timeoutMs;
        }
        public boolean isTimeoutMsConfigured() { return timeoutMs != null; }
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
     *       metric-name: reactive.http.client.requests
     *       include-url-path: false
     *       log-request-body: false
     * }</pre>
     */
    public static class ObservabilityConfig {

        /** Master switch – set to {@code false} to disable all metrics/tracing. */
        private boolean enabled = true;

        /** Micrometer timer/counter name (default: {@code reactive.http.client.requests}). */
        private String metricName = "reactive.http.client.requests";

        /**
         * Include the URL path template as a metric tag and span attribute.
         * Opt in only when path templates are bounded and do not contain raw IDs.
         */
        private boolean includeUrlPath = false;

        /**
         * Include resolved outbound server.address / server.port as Micrometer tags.
         * Disabled by default because upstream hosts can be high-cardinality.
         */
        private boolean includeServerAddress = false;

        /** Log request body in span events (caution: PII / large payloads). */
        private boolean logRequestBody = false;

        /** Log response body in span events (caution: PII / large payloads). */
        private boolean logResponseBody = false;

        private HealthConfig health = new HealthConfig();
        private HistogramConfig histogram = new HistogramConfig();

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public String getMetricName() { return metricName; }
        public void setMetricName(String metricName) { this.metricName = metricName; }

        public boolean isIncludeUrlPath() { return includeUrlPath; }
        public void setIncludeUrlPath(boolean includeUrlPath) { this.includeUrlPath = includeUrlPath; }

        public boolean isIncludeServerAddress() { return includeServerAddress; }
        public void setIncludeServerAddress(boolean includeServerAddress) { this.includeServerAddress = includeServerAddress; }

        public boolean isLogRequestBody() { return logRequestBody; }
        public void setLogRequestBody(boolean logRequestBody) { this.logRequestBody = logRequestBody; }

        public boolean isLogResponseBody() { return logResponseBody; }
        public void setLogResponseBody(boolean logResponseBody) { this.logResponseBody = logResponseBody; }

        public HealthConfig getHealth() { return health; }
        public void setHealth(HealthConfig health) {
            this.health = health != null ? health : new HealthConfig();
        }

        public HistogramConfig getHistogram() { return histogram; }
        public void setHistogram(HistogramConfig histogram) {
            this.histogram = histogram != null ? histogram : new HistogramConfig();
        }
    }

    /**
     * Settings for latency histogram metrics.
     *
     * <p>When enabled, a separate {@code <metricName>.latency} timer with SLO histogram
     * buckets is recorded alongside the main timer. The histogram uses only low-cardinality
     * tags ({@code client.name}, {@code api.name}, {@code http.method}, {@code uri}) to
     * avoid Prometheus time-series explosion.
     *
     * <p>Example {@code application.yml}:
     * <pre>{@code
     * reactive:
     *   http:
     *     observability:
     *       histogram:
     *         enabled: true
     *         slo-boundaries-ms: [50, 100, 200, 500, 1000, 2000, 5000]
     * }</pre>
     */
    public static class HistogramConfig {

        private static final List<Long> DEFAULT_SLO_BOUNDARIES_MS =
                Arrays.asList(50L, 100L, 200L, 500L, 1000L, 2000L, 5000L);

        /** Enable latency histogram (SLO buckets) on the latency metric. Default: {@code false}. */
        private boolean enabled = false;

        /**
         * SLO bucket boundaries in milliseconds. Each value produces a
         * {@code le="<value>"} histogram bucket. Defaults to
         * {@code [50, 100, 200, 500, 1000, 2000, 5000]} if not configured.
         */
        private List<Long> sloBoundariesMs = new ArrayList<>(DEFAULT_SLO_BOUNDARIES_MS);

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public List<Long> getSloBoundariesMs() { return sloBoundariesMs; }
        public void setSloBoundariesMs(List<Long> sloBoundariesMs) {
            if (sloBoundariesMs == null) {
                this.sloBoundariesMs = new ArrayList<>(DEFAULT_SLO_BOUNDARIES_MS);
                return;
            }
            if (sloBoundariesMs.isEmpty()) {
                throw new IllegalArgumentException("reactive.http.observability.histogram.slo-boundaries-ms must not be empty.");
            }
            ArrayList<Long> validated = new ArrayList<>(sloBoundariesMs.size());
            long previous = 0;
            for (Long boundary : sloBoundariesMs) {
                if (boundary == null) {
                    throw new IllegalArgumentException("reactive.http.observability.histogram.slo-boundaries-ms must not contain null values.");
                }
                requireAtLeast("reactive.http.observability.histogram.slo-boundaries-ms", boundary, 1);
                if (!validated.isEmpty() && boundary <= previous) {
                    throw new IllegalArgumentException("reactive.http.observability.histogram.slo-boundaries-ms must be strictly increasing.");
                }
                validated.add(boundary);
                previous = boundary;
            }
            this.sloBoundariesMs = validated;
        }
    }

    /**
     * Settings for
     * {@link io.github.huynhngochuyhoang.httpstarter.observability.HttpClientHealthIndicator}.
     *
     * <p>The indicator computes a per-client error ratio from probe-to-probe deltas
     * on the {@code reactive.http.client.requests} timer meters. A client reports DOWN when
     * its delta sample count meets {@link #getMinSamples()} and its error ratio
     * exceeds {@link #getErrorRateThreshold()}; otherwise UP. The overall status is
     * DOWN if any tracked client is DOWN.
     *
     * <p>Example {@code application.yml}:
     * <pre>{@code
     * reactive:
     *   http:
     *     observability:
     *       health:
     *         enabled: true
     *         error-rate-threshold: 0.5
     *         min-samples: 10
     * }</pre>
     */
    public static class HealthConfig {

        /** Master switch for the health indicator. Default {@code true} (active when actuator is on the classpath). */
        private boolean enabled = true;

        /**
         * Error ratio (in [0, 1]) above which a client is reported DOWN. Default
         * {@code 0.5} (50 %) — tuned for "obviously degraded" downstream services.
         */
        private double errorRateThreshold = 0.5;

        /**
         * Minimum sample count (delta of invocations since the previous probe)
         * required to evaluate a client. Avoids noisy DOWN statuses from one or two
         * isolated errors during a quiet window. Default {@code 10}.
         */
        private long minSamples = 10;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public double getErrorRateThreshold() { return errorRateThreshold; }
        public void setErrorRateThreshold(double errorRateThreshold) {
            if (!Double.isFinite(errorRateThreshold) || errorRateThreshold < 0.0 || errorRateThreshold > 1.0) {
                throw new IllegalArgumentException("reactive.http.observability.health.error-rate-threshold must be between 0.0 and 1.0 but was " + errorRateThreshold + ".");
            }
            this.errorRateThreshold = errorRateThreshold;
        }

        public long getMinSamples() { return minSamples; }
        public void setMinSamples(long minSamples) {
            this.minSamples = requireAtLeast("reactive.http.observability.health.min-samples", minSamples, 1);
        }
    }

    // ---- global observability config (not per-client) ----

    private ObservabilityConfig observability = new ObservabilityConfig();

    public ObservabilityConfig getObservability() { return observability; }
    public void setObservability(ObservabilityConfig observability) { this.observability = observability; }

    // ---- correlation-id filter config ----

    /**
     * Settings for {@link io.github.huynhngochuyhoang.httpstarter.filter.CorrelationIdWebFilter}.
     *
     * <p>The MDC fallback list controls which logging-MDC keys the outbound exchange
     * filter consults when no correlation id is present in the Reactor context — useful
     * for non-reactive integrations (Brave, Sleuth) and tracing libraries that publish
     * their own keys (e.g. Zipkin's {@code X-B3-TraceId}, Jaeger's
     * {@code uber-trace-id}). Keys are tried in the configured order; the first
     * non-blank value wins. Defaults preserve the previously hard-coded list:
     * {@code ["correlationId", "X-Correlation-Id", "traceId"]}.
     *
     * <p>Example {@code application.yml}:
     * <pre>{@code
     * reactive:
     *   http:
     *     correlation-id:
     *       max-length: 128
     *       mdc-keys: [correlationId, X-Correlation-Id, traceId, X-B3-TraceId]
     * }</pre>
     */
    public static class CorrelationIdConfig {

        /** Upper bound on the accepted correlation-id value length. Values longer than this are rejected. */
        private int maxLength = 128;

        /**
         * Ordered list of MDC keys consulted by the outbound exchange filter when no
         * correlation id is present in the Reactor context. The first key with a
         * non-blank value wins.
         */
        private java.util.List<String> mdcKeys = defaultMdcKeys();

        public int getMaxLength() { return maxLength; }
        public void setMaxLength(int maxLength) { this.maxLength = maxLength; }

        public java.util.List<String> getMdcKeys() { return mdcKeys; }
        public void setMdcKeys(java.util.List<String> mdcKeys) {
            if (mdcKeys == null || mdcKeys.isEmpty()) {
                this.mdcKeys = java.util.List.of();
                return;
            }
            java.util.List<String> normalized = new java.util.ArrayList<>(mdcKeys.size());
            for (String key : mdcKeys) {
                if (key == null) continue;
                String trimmed = key.trim();
                if (!trimmed.isEmpty()) normalized.add(trimmed);
            }
            this.mdcKeys = java.util.List.copyOf(normalized);
        }

        private static java.util.List<String> defaultMdcKeys() {
            return java.util.List.of("correlationId", "X-Correlation-Id", "traceId");
        }
    }

    // ---- inbound-headers filter config ----

    /**
     * Settings for
     * {@link io.github.huynhngochuyhoang.httpstarter.filter.InboundHeadersWebFilter}.
     *
     * <p>The inbound-header snapshot stored in the Reactor context (and subsequently
     * logged by {@link io.github.huynhngochuyhoang.httpstarter.core.DefaultHttpExchangeLogger})
     * is filtered through the allow-list / deny-list before being stored:
     *
     * <ol>
     *   <li>If {@link #getAllowList()} is non-empty, only headers whose name matches
     *       an entry in the allow-list are captured.</li>
     *   <li>Captured header values whose name matches {@link #getDenyList()} are
     *       replaced with {@code [REDACTED]}.</li>
     * </ol>
     *
     * <p>Defaults: allow-list empty (capture everything), deny-list set to
     * {@link SensitiveHeaders#DEFAULTS} so credentials and session cookies are never
     * stored or logged.
     *
     * <p>Example {@code application.yml}:
     * <pre>{@code
     * reactive:
     *   http:
     *     inbound-headers:
     *       allow-list: [X-Request-Id, X-User-Id]
     *       deny-list:  [Authorization, Cookie, Set-Cookie, Proxy-Authorization, X-Api-Key]
     * }</pre>
     */
    public static class InboundHeadersConfig {

        private Set<String> allowList = new LinkedHashSet<>();
        private Set<String> denyList = defaultDenyList();

        public Set<String> getAllowList() { return allowList; }
        public void setAllowList(Set<String> allowList) { this.allowList = normalize(allowList); }

        public Set<String> getDenyList() { return denyList; }
        public void setDenyList(Set<String> denyList) {
            // Passing {@code null} or an explicit empty list disables redaction entirely,
            // which may be intentional in tightly controlled environments.
            this.denyList = normalize(denyList);
        }

        private static Set<String> defaultDenyList() {
            LinkedHashSet<String> defaults = new LinkedHashSet<>();
            SensitiveHeaders.DEFAULTS.forEach(defaults::add);
            return defaults;
        }

        private static Set<String> normalize(Set<String> input) {
            if (input == null || input.isEmpty()) {
                return new LinkedHashSet<>();
            }
            LinkedHashSet<String> out = new LinkedHashSet<>();
            for (String entry : input) {
                if (entry == null) continue;
                String trimmed = entry.trim();
                if (trimmed.isEmpty()) continue;
                out.add(trimmed.toLowerCase(Locale.ROOT));
            }
            return out;
        }
    }
}
