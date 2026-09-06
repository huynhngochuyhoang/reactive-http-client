# Configuration Properties

> Generated from:
> - `reactive-http-client-starter/src/main/resources/META-INF/additional-spring-configuration-metadata.json`
> - `reactive-http-client-otel/src/main/resources/META-INF/additional-spring-configuration-metadata.json`
> `DocumentationReleaseArtifactTest` fails when this file drifts from metadata.

| Property | Type | Default | Description | Deprecated |
|---|---|---|---|---|
| `reactive.http.clients` | `java.util.Map<java.lang.String,io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties.ClientConfig>` |  | Per-client configuration map. Keys are the logical client names used in @ReactiveHttpClient(name=...). |  |
| `reactive.http.clients.[name].apis` | `java.util.Map<java.lang.String,io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties.ApiConfig>` |  | Optional named API definitions for @ApiRef methods. Keys are logical API names. |  |
| `reactive.http.clients.[name].apis.[api-name].method` | `java.lang.String` |  | HTTP method for a named API definition used by @ApiRef (for example GET, POST). |  |
| `reactive.http.clients.[name].apis.[api-name].path` | `java.lang.String` |  | Request path template for a named API definition used by @ApiRef. |  |
| `reactive.http.clients.[name].apis.[api-name].timeout-ms` | `java.lang.Long` | `-1` | Optional per-request timeout in milliseconds for @ApiRef. -1 means not configured; 0 disables timeout. |  |
| `reactive.http.clients.[name].auth-provider` | `java.lang.String` |  | Bean name of the AuthProvider to inject for this client. Leave empty to disable automatic auth. Default: empty. |  |
| `reactive.http.clients.[name].auth.aws-sig-v4.access-key-id` | `java.lang.String` |  | AWS access key ID for type aws-sigv4. |  |
| `reactive.http.clients.[name].auth.aws-sig-v4.region` | `java.lang.String` |  | AWS SigV4 region, for example us-east-1. |  |
| `reactive.http.clients.[name].auth.aws-sig-v4.secret-access-key` | `java.lang.String` |  | AWS secret access key for type aws-sigv4. |  |
| `reactive.http.clients.[name].auth.aws-sig-v4.service` | `java.lang.String` |  | AWS SigV4 service signing name, for example s3 or execute-api. |  |
| `reactive.http.clients.[name].auth.aws-sig-v4.session-token` | `java.lang.String` |  | Optional AWS session token for temporary credentials. |  |
| `reactive.http.clients.[name].auth.aws-sig-v4.strict-body-signing-validation` | `java.lang.Boolean` | `false` | Fail startup for built-in AWS SigV4 clients when a declarative method uses a body shape whose stable raw bytes cannot be materialized for signing. Default: false. |  |
| `reactive.http.clients.[name].auth.oauth2-client-credentials.audience` | `java.lang.String` |  | Optional OAuth2 audience sent to the token endpoint. |  |
| `reactive.http.clients.[name].auth.oauth2-client-credentials.auth-style` | `java.lang.String` | `"basic-auth"` | OAuth2 client authentication style. Values: basic-auth, form-post. Default: basic-auth. |  |
| `reactive.http.clients.[name].auth.oauth2-client-credentials.client-id` | `java.lang.String` |  | OAuth2 client ID for type oauth2-client-credentials. |  |
| `reactive.http.clients.[name].auth.oauth2-client-credentials.client-secret` | `java.lang.String` |  | OAuth2 client secret for type oauth2-client-credentials. |  |
| `reactive.http.clients.[name].auth.oauth2-client-credentials.expiry-leeway-ms` | `java.lang.Long` | `30000` | Milliseconds subtracted from OAuth2 expires_in before caching the access token. Default: 30000. |  |
| `reactive.http.clients.[name].auth.oauth2-client-credentials.scope` | `java.lang.String` |  | Optional OAuth2 scope sent to the token endpoint. |  |
| `reactive.http.clients.[name].auth.oauth2-client-credentials.token-service.connect-timeout-ms` | `java.lang.Integer` | `2000` | Connect timeout for the isolated OAuth2 token-service transport in milliseconds. |  |
| `reactive.http.clients.[name].auth.oauth2-client-credentials.token-service.max-connections` | `java.lang.Integer` | `2` | Maximum connections in the client-owned OAuth2 token-service pool. |  |
| `reactive.http.clients.[name].auth.oauth2-client-credentials.token-service.pending-acquire-timeout-ms` | `java.lang.Long` | `5000` | Maximum OAuth2 token-service pool acquisition wait in milliseconds. |  |
| `reactive.http.clients.[name].auth.oauth2-client-credentials.token-service.proxy.host` | `java.lang.String` |  | Token-service proxy host. |  |
| `reactive.http.clients.[name].auth.oauth2-client-credentials.token-service.proxy.non-proxy-hosts` | `java.lang.String` |  | Token-service non-proxy hosts regex. |  |
| `reactive.http.clients.[name].auth.oauth2-client-credentials.token-service.proxy.password` | `java.lang.String` |  | Token-service proxy authentication password. |  |
| `reactive.http.clients.[name].auth.oauth2-client-credentials.token-service.proxy.port` | `java.lang.Integer` |  | Token-service proxy port. |  |
| `reactive.http.clients.[name].auth.oauth2-client-credentials.token-service.proxy.type` | `io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties$ProxyConfig$Type` |  | Token-service proxy transport type. HTTP uses plaintext CONNECT; HTTPS is its deprecated compatibility alias and does not add TLS to the proxy hop. Set NONE or omit the proxy block for a direct connection. |  |
| `reactive.http.clients.[name].auth.oauth2-client-credentials.token-service.proxy.username` | `java.lang.String` |  | Token-service proxy authentication username. |  |
| `reactive.http.clients.[name].auth.oauth2-client-credentials.token-service.request-timeout-ms` | `java.lang.Long` | `0` | Total timeout for each OAuth2 token request in milliseconds. Zero disables it. |  |
| `reactive.http.clients.[name].auth.oauth2-client-credentials.token-service.retry-backoff-ms` | `java.lang.Long` | `100` | Fixed delay between transient OAuth2 token request attempts in milliseconds. |  |
| `reactive.http.clients.[name].auth.oauth2-client-credentials.token-service.retry-max-attempts` | `java.lang.Integer` | `1` | Total OAuth2 token request attempts for timeouts, transport failures, HTTP 429, and 5xx responses. One disables retry. |  |
| `reactive.http.clients.[name].auth.oauth2-client-credentials.token-service.tls.ciphers` | `java.util.List<java.lang.String>` |  | Token-service allowed TLS cipher suites override. |  |
| `reactive.http.clients.[name].auth.oauth2-client-credentials.token-service.tls.insecure-trust-all` | `java.lang.Boolean` |  | Token-service certificate verification disable override — NEVER use in production. |  |
| `reactive.http.clients.[name].auth.oauth2-client-credentials.token-service.tls.key-store` | `java.lang.String` |  | Token-service keystore path override for mTLS client certificate. |  |
| `reactive.http.clients.[name].auth.oauth2-client-credentials.token-service.tls.key-store-password` | `java.lang.String` |  | Token-service keystore password override. |  |
| `reactive.http.clients.[name].auth.oauth2-client-credentials.token-service.tls.key-store-type` | `java.lang.String` |  | Token-service keystore format override. Default: PKCS12. |  |
| `reactive.http.clients.[name].auth.oauth2-client-credentials.token-service.tls.protocols` | `java.util.List<java.lang.String>` |  | Token-service allowed TLS protocol versions override. |  |
| `reactive.http.clients.[name].auth.oauth2-client-credentials.token-service.tls.trust-store` | `java.lang.String` |  | Token-service truststore path override. |  |
| `reactive.http.clients.[name].auth.oauth2-client-credentials.token-service.tls.trust-store-password` | `java.lang.String` |  | Token-service truststore password override. |  |
| `reactive.http.clients.[name].auth.oauth2-client-credentials.token-service.tls.trust-store-type` | `java.lang.String` |  | Token-service truststore format override. Default: PKCS12. |  |
| `reactive.http.clients.[name].auth.oauth2-client-credentials.token-uri` | `java.lang.String` |  | OAuth2 token endpoint URI for type oauth2-client-credentials. |  |
| `reactive.http.clients.[name].auth.type` | `java.lang.String` |  | Object-style auth provider type. Built-in values: oauth2-client-credentials, aws-sigv4. Ignored when auth-provider bean name is set. |  |
| `reactive.http.clients.[name].base-url` | `java.lang.String` |  | Base URL for this client (e.g. https://api.example.invalid). |  |
| `reactive.http.clients.[name].cache.customizations` | `java.util.Map<java.lang.String,io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties.CacheCustomizationSafety>` |  | Explicit cache-safety decisions keyed by applicable Boot WebClientCustomizer, ReactiveHttpClientCustomizer, or replacement WebClient.Builder bean name. Unknown and INCOMPATIBLE customizations reject selected caching. |  |
| `reactive.http.clients.[name].cache.policies` | `java.util.Map<java.lang.String,io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties.CachePolicyConfig>` |  | Named local response-cache policy definitions. Definitions are inert until selected by cache.policy or @CacheResponse; policy definition does not imply semantic-read intent for non-GET methods. |  |
| `reactive.http.clients.[name].cache.policies.[policy-name].maximum-size` | `java.lang.Long` |  | Required maximum entry count for a selected response-cache policy. Must be between 1 and 1000000. |  |
| `reactive.http.clients.[name].cache.policies.[policy-name].maximum-total-decoded-response-bytes` | `java.lang.Long` |  | Optional per-policy aggregate limit in decoded response representation bytes. Must be between 1 and 1099511627776 (1 TiB). `maximum-size` continues to count entries. This value is not exact Java heap, direct memory, process RSS, or container memory. |  |
| `reactive.http.clients.[name].cache.policies.[policy-name].non-cacheable-response-headers` | `java.util.List<java.lang.String>` |  | Application-specific response header names that make a response non-cacheable, matched case-insensitively. At most 32 valid header names. |  |
| `reactive.http.clients.[name].cache.policies.[policy-name].refresh-after-ms` | `java.lang.Long` |  | Optional access-driven refresh threshold in milliseconds. When configured, it must be positive and strictly below ttl-ms, and refresh-timeout-ms is required. |  |
| `reactive.http.clients.[name].cache.policies.[policy-name].refresh-timeout-ms` | `java.lang.Long` |  | Required positive finite refresh deadline in milliseconds when refresh-after-ms is configured. The effective deadline is also capped by hard expiry and factory shutdown. |  |
| `reactive.http.clients.[name].cache.policies.[policy-name].shared-response` | `java.lang.Boolean` | `false` | Explicitly acknowledge that omitted caller, auth, header, and context variants may share one response. Explicitly selected dimensions still partition the response. This cannot waive body identity for a semantic non-GET method. Default: false. |  |
| `reactive.http.clients.[name].cache.policies.[policy-name].single-flight` | `java.lang.Boolean` | `false` | Coalesce concurrent misses for the same isolated cache key into one shared load. Each caller retains its own timeout and cancellation. Default: false. |  |
| `reactive.http.clients.[name].cache.policies.[policy-name].ttl-ms` | `java.lang.Long` |  | Required hard TTL for a selected response-cache policy in milliseconds. Must be between 1 and 31536000000 (365 days). |  |
| `reactive.http.clients.[name].cache.policies.[policy-name].vary-by-context` | `java.util.List<java.lang.String>` |  | String Reactor-context keys used as response partition dimensions. |  |
| `reactive.http.clients.[name].cache.policies.[policy-name].vary-by-headers` | `java.util.List<java.lang.String>` |  | Named outbound headers used as response partition dimensions, matched case-insensitively. Include the effective idempotency header unless shared-response is acknowledged. |  |
| `reactive.http.clients.[name].cache.policies.[policy-name].vary-by-parameters` | `java.util.List<java.lang.String>` |  | Stable @CacheKey labels used as additional response partition dimensions. Path and query parameters are included automatically. |  |
| `reactive.http.clients.[name].cache.policy` | `java.lang.String` |  | Optional client-wide named response-cache policy selection. Blank or absent disables client-wide caching; method-level @CacheResponse overrides it. A selected non-GET method still requires @CacheResponse(semanticRead=true) on that exact method; there is no client-wide semantic-read acknowledgement. |  |
| `reactive.http.clients.[name].codec-max-in-memory-size-mb` | `java.lang.Integer` | `2` | Maximum decoded unary response aggregation size in MiB after transport decompression. Applies to codec-decoded values and ResponseEntity values, not bounded error retention or DataBuffer streams. 0 means unlimited. Default: 2. |  |
| `reactive.http.clients.[name].compression-enabled` | `java.lang.Boolean` | `false` | Enable Reactor Netty response compression negotiation and incremental decompression (Accept-Encoding: gzip). Request bodies are not compressed. Default: false. |  |
| `reactive.http.clients.[name].default-headers` | `java.util.Map<java.lang.String,java.lang.String>` |  | Static headers added to every request for this client. Method-level @HeaderParam values with the same header name override configured defaults. |  |
| `reactive.http.clients.[name].default-query-params` | `java.util.Map<java.lang.String,java.util.List<java.lang.String>>` |  | Static query parameters added to every request for this client. Method-level @QueryParam values with the same name override configured defaults. Multiple values are sent as repeated query parameters. |  |
| `reactive.http.clients.[name].follow-redirects` | `java.lang.Boolean` | `false` | Opt in to Reactor Netty automatic redirect following for HTTP 301, 302, 303, 307, and 308 responses on this client. Default: false, so 3xx responses remain visible to ResponseEntity callers. |  |
| `reactive.http.clients.[name].http2-enabled` | `java.lang.Boolean` | `false` | Enable Reactor Netty HTTP/2 for this client. Default: false. |  |
| `reactive.http.clients.[name].log-exchange` | `java.lang.Boolean` | `false` | Enable structured HTTP exchange logging (request + response) for all methods on this client. Default: false. |  |
| `reactive.http.clients.[name].log-preset` | `io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties$LogPreset` | `"metadata-only"` | Controls how much data the default exchange logger writes when log-exchange is enabled or @LogHttpExchange uses DefaultHttpExchangeLogger. Values: metadata-only, headers, bodies. Default: metadata-only. |  |
| `reactive.http.clients.[name].logical-call-timeout-ms` | `java.lang.Long` | `0` | Optional end-to-end timeout budget for one subscription, including resilience admission, retries, redirects, auth, pool acquisition, and starter-owned response consumption. 0 disables the budget. Default: 0. |  |
| `reactive.http.clients.[name].pool.evict-in-background-ms` | `java.lang.Long` |  | Per-client background eviction interval override in milliseconds. 0 disables background eviction. |  |
| `reactive.http.clients.[name].pool.max-connections` | `java.lang.Integer` |  | Per-client connection pool max connections override. Overrides the global pool when set. |  |
| `reactive.http.clients.[name].pool.max-idle-time-ms` | `java.lang.Long` |  | Per-client idle eviction timeout override in milliseconds. 0 means no idle eviction. |  |
| `reactive.http.clients.[name].pool.max-life-time-ms` | `java.lang.Long` |  | Per-client connection max lifetime override in milliseconds. 0 means unlimited. |  |
| `reactive.http.clients.[name].pool.metrics-enabled` | `java.lang.Boolean` |  | Per-client pool metrics override. When true, publishes address-free starter aggregate pool gauges to the MeterRegistry. |  |
| `reactive.http.clients.[name].pool.pending-acquire-timeout-ms` | `java.lang.Long` |  | Per-client pending acquire timeout override in milliseconds. |  |
| `reactive.http.clients.[name].proxy.host` | `java.lang.String` |  | Per-client proxy host override. |  |
| `reactive.http.clients.[name].proxy.non-proxy-hosts` | `java.lang.String` |  | Per-client non-proxy hosts regex override. |  |
| `reactive.http.clients.[name].proxy.password` | `java.lang.String` |  | Per-client proxy authentication password override. |  |
| `reactive.http.clients.[name].proxy.port` | `java.lang.Integer` |  | Per-client proxy port override. |  |
| `reactive.http.clients.[name].proxy.type` | `io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties$ProxyConfig$Type` |  | Per-client proxy transport override. HTTP uses plaintext CONNECT; HTTPS is its deprecated compatibility alias and does not add TLS to the proxy hop. Set NONE to bypass a global proxy. |  |
| `reactive.http.clients.[name].proxy.username` | `java.lang.String` |  | Per-client proxy authentication username override. |  |
| `reactive.http.clients.[name].request-timeout-ms` | `java.lang.Long` | `0` | Canonical per-request response timeout in milliseconds applied via HttpClientRequest.responseTimeout(). 0 disables the timeout. Wins over deprecated resilience.timeout-ms. Default: 0. |  |
| `reactive.http.clients.[name].resilience.bulkhead` | `java.lang.String` |  | Name of the Resilience4j Bulkhead instance from application config. Blank or absent disables this operator. |  |
| `reactive.http.clients.[name].resilience.circuit-breaker` | `java.lang.String` |  | Name of the Resilience4j CircuitBreaker instance from application config. Blank or absent disables this operator. |  |
| `reactive.http.clients.[name].resilience.enabled` | `java.lang.Boolean` | `false` | Master switch for Resilience4j operators on this client. Enabling it does not select Retry, CircuitBreaker, Bulkhead, or RateLimiter; select each intended operator explicitly. Default: false. |  |
| `reactive.http.clients.[name].resilience.rate-limiter` | `java.lang.String` |  | Name of the Resilience4j RateLimiter instance from application config. Blank or absent disables this operator. |  |
| `reactive.http.clients.[name].resilience.retry` | `java.lang.String` |  | Name of the Resilience4j Retry instance from application config. Blank or absent disables this operator. |  |
| `reactive.http.clients.[name].resilience.retry-methods` | `java.util.Set<java.lang.String>` | `["GET","HEAD"]` | HTTP methods eligible for retry. Values are upper-cased. Default: [GET, HEAD]. |  |
| `reactive.http.clients.[name].resilience.strict-unsafe-retry-validation` | `java.lang.Boolean` | `false` | Fail startup when strict retry validation is enabled and an actually retryable unsafe HTTP method has no startup-provable Idempotency-Key contract. Default: false. |  |
| `reactive.http.clients.[name].resilience.timeout-ms` | `java.lang.Long` | `0` | Deprecated alias for request-timeout-ms. 0 disables the per-request timeout. request-timeout-ms wins when both are configured. | warning; replacement: `reactive.http.clients.[name].request-timeout-ms` |
| `reactive.http.clients.[name].tls.ciphers` | `java.util.List<java.lang.String>` |  | Per-client allowed TLS cipher suites override. |  |
| `reactive.http.clients.[name].tls.insecure-trust-all` | `java.lang.Boolean` |  | Per-client certificate verification disable override — NEVER use in production. |  |
| `reactive.http.clients.[name].tls.key-store` | `java.lang.String` |  | Per-client keystore path override for mTLS client certificate. |  |
| `reactive.http.clients.[name].tls.key-store-password` | `java.lang.String` |  | Per-client keystore password override. |  |
| `reactive.http.clients.[name].tls.key-store-type` | `java.lang.String` |  | Per-client keystore format override. Default: PKCS12. |  |
| `reactive.http.clients.[name].tls.protocols` | `java.util.List<java.lang.String>` |  | Per-client allowed TLS protocol versions override. |  |
| `reactive.http.clients.[name].tls.trust-store` | `java.lang.String` |  | Per-client truststore path override. |  |
| `reactive.http.clients.[name].tls.trust-store-password` | `java.lang.String` |  | Per-client truststore password override. |  |
| `reactive.http.clients.[name].tls.trust-store-type` | `java.lang.String` |  | Per-client truststore format override. Default: PKCS12. |  |
| `reactive.http.correlation-id.max-length` | `java.lang.Integer` | `128` | Maximum accepted length of a correlation-ID value. Values longer than this are dropped. Default: 128. |  |
| `reactive.http.correlation-id.mdc-keys` | `java.util.List<java.lang.String>` | `["correlationId","X-Correlation-Id","traceId"]` | Ordered list of MDC keys consulted by the outbound filter when no correlation-ID is in the Reactor context. First non-blank value wins. Default: [correlationId, X-Correlation-Id, traceId]. |  |
| `reactive.http.inbound-headers.allow-list` | `java.util.Set<java.lang.String>` |  | When non-empty, only headers whose names match an entry are captured in the inbound snapshot. Empty means capture everything. Default: [] (capture all). |  |
| `reactive.http.inbound-headers.deny-list` | `java.util.Set<java.lang.String>` |  | Header names whose values are replaced with [REDACTED] in the inbound snapshot before logging. Default: SensitiveHeaders.DEFAULTS (authorization, cookie, set-cookie, proxy-authorization, x-api-key). |  |
| `reactive.http.network.connect-timeout-ms` | `java.lang.Integer` | `2000` | TCP connect timeout in milliseconds. Default: 2000. |  |
| `reactive.http.network.connection-pool.evict-in-background-ms` | `java.lang.Long` | `0` | Interval in milliseconds at which the pool sweeps for evictable connections. 0 disables background eviction. Default: 0. |  |
| `reactive.http.network.connection-pool.max-connections` | `java.lang.Integer` | `200` | Maximum number of connections in the pool. Default: 200. |  |
| `reactive.http.network.connection-pool.max-idle-time-ms` | `java.lang.Long` | `0` | Idle duration in milliseconds after which a pooled connection is evicted. 0 means no idle eviction (Reactor Netty default). Default: 0. |  |
| `reactive.http.network.connection-pool.max-life-time-ms` | `java.lang.Long` | `0` | Maximum lifetime in milliseconds of a pooled connection. 0 means unlimited (Reactor Netty default). Default: 0. |  |
| `reactive.http.network.connection-pool.metrics-enabled` | `java.lang.Boolean` | `false` | When true, publishes address-free starter aggregate pool gauges (reactive.http.client.connection.pool.*) to the MeterRegistry. Requires micrometer-core. Default: false. |  |
| `reactive.http.network.connection-pool.pending-acquire-timeout-ms` | `java.lang.Long` | `5000` | Maximum time in milliseconds to wait for a connection from the pool before failing. Default: 5000. |  |
| `reactive.http.network.network-read-timeout-ms` | `java.lang.Integer` | `60000` | Netty ReadTimeoutHandler safety-net: fires when no inbound bytes arrive for this duration. Sized well above any per-request timeout. Default: 60000. |  |
| `reactive.http.network.network-write-timeout-ms` | `java.lang.Integer` | `60000` | Netty WriteTimeoutHandler safety-net: fires when no outbound bytes are accepted for this duration. Sized well above any per-request timeout. Default: 60000. |  |
| `reactive.http.network.proxy.host` | `java.lang.String` |  | Proxy host name or IP address. |  |
| `reactive.http.network.proxy.non-proxy-hosts` | `java.lang.String` |  | Java regex pattern for hosts that bypass the proxy (pipe-separated alternatives). Example: localhost\|.*\.internal. |  |
| `reactive.http.network.proxy.password` | `java.lang.String` |  | Optional proxy authentication password. |  |
| `reactive.http.network.proxy.port` | `java.lang.Integer` |  | Proxy port number. |  |
| `reactive.http.network.proxy.type` | `io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties$ProxyConfig$Type` | `"HTTP"` | Proxy transport type. HTTP uses plaintext CONNECT for HTTP and HTTPS targets; HTTPS is a deprecated compatibility alias and does not add TLS to the proxy hop. SOCKS4 and SOCKS5 delegate to Reactor Netty. Set NONE to bypass the proxy. Default: HTTP. |  |
| `reactive.http.network.proxy.username` | `java.lang.String` |  | Optional proxy authentication username. |  |
| `reactive.http.network.read-timeout-ms` | `java.lang.Integer` |  | Deprecated alias for network-read-timeout-ms. Use network-read-timeout-ms instead. | warning; replacement: `reactive.http.network.network-read-timeout-ms` |
| `reactive.http.network.tls.ciphers` | `java.util.List<java.lang.String>` |  | Allowed TLS cipher suites. Empty list means JDK defaults. |  |
| `reactive.http.network.tls.insecure-trust-all` | `java.lang.Boolean` | `false` | Disables certificate verification — NEVER use in production. Logs a WARN when enabled. Default: false. |  |
| `reactive.http.network.tls.key-store` | `java.lang.String` |  | Path to the keystore for mTLS client certificate (classpath:, file:, or absolute). |  |
| `reactive.http.network.tls.key-store-password` | `java.lang.String` |  | Password for the keystore. |  |
| `reactive.http.network.tls.key-store-type` | `java.lang.String` | `"PKCS12"` | Keystore format. Default: PKCS12. |  |
| `reactive.http.network.tls.protocols` | `java.util.List<java.lang.String>` |  | Allowed TLS protocol versions (e.g. TLSv1.3, TLSv1.2). Empty list means JDK defaults. |  |
| `reactive.http.network.tls.trust-store` | `java.lang.String` |  | Path to the truststore (classpath:, file:, or absolute). Resolved via Spring's DefaultResourceLoader. |  |
| `reactive.http.network.tls.trust-store-password` | `java.lang.String` |  | Password for the truststore. |  |
| `reactive.http.network.tls.trust-store-type` | `java.lang.String` | `"PKCS12"` | Truststore format. Default: PKCS12. |  |
| `reactive.http.network.write-timeout-ms` | `java.lang.Integer` |  | Deprecated alias for network-write-timeout-ms. Use network-write-timeout-ms instead. | warning; replacement: `reactive.http.network.network-write-timeout-ms` |
| `reactive.http.observability.cache.enabled` | `java.lang.Boolean` | `false` | Enable bounded response-cache metrics and terminal cache outcome fields. Requires the global observability master switch and an explicitly selected cache policy. Default: false. |  |
| `reactive.http.observability.diagnostics-endpoint.enabled` | `java.lang.Boolean` | `false` | Enable the opt-in rhttpclients Actuator endpoint that returns sanitized configured-client diagnostics. Requires Actuator endpoint infrastructure and management endpoint exposure. Default: false. |  |
| `reactive.http.observability.enabled` | `java.lang.Boolean` | `true` | Master switch for all metrics and tracing. Default: true. |  |
| `reactive.http.observability.health.enabled` | `java.lang.Boolean` | `true` | Enable the Actuator health indicator for reactive HTTP clients. Default: true. |  |
| `reactive.http.observability.health.error-rate-threshold` | `java.lang.Double` | `0.5` | Error ratio threshold [0, 1] above which a client is reported DOWN. Default: 0.5. |  |
| `reactive.http.observability.health.min-samples` | `java.lang.Long` | `10` | Minimum probe-interval sample count required before evaluating a client's health. Avoids noisy DOWN status from isolated errors. Default: 10. |  |
| `reactive.http.observability.histogram.enabled` | `java.lang.Boolean` | `false` | Enable latency histogram (SLO buckets) recorded as <metricName>.latency (default: reactive.http.client.requests.latency). Default: false. |  |
| `reactive.http.observability.histogram.slo-boundaries-ms` | `java.util.List<java.lang.Long>` | `[50,100,200,500,1000,2000,5000]` | SLO bucket boundaries in milliseconds for the latency histogram. Default: [50, 100, 200, 500, 1000, 2000, 5000]. |  |
| `reactive.http.observability.include-server-address` | `java.lang.Boolean` | `false` | Include resolved server.address and server.port as Micrometer metric tags and OpenTelemetry span attributes. Disabled by default because upstream hosts can be high-cardinality. |  |
| `reactive.http.observability.include-url-path` | `java.lang.Boolean` | `false` | Include the URL path template as a Micrometer tag and OpenTelemetry url.template attribute. Opt in only when path templates are bounded and do not contain raw IDs. Default: false. |  |
| `reactive.http.observability.log-request-body` | `java.lang.Boolean` | `false` | Include the request body on the terminal HttpClientObserverEvent for custom observers. Built-in Micrometer and OpenTelemetry observers do not export it. Caution: custom observers may expose PII, credentials, or large payloads. Default: false. |  |
| `reactive.http.observability.log-response-body` | `java.lang.Boolean` | `false` | Include the decoded success response body on the terminal HttpClientObserverEvent for custom observers. Built-in Micrometer and OpenTelemetry observers do not export it. Caution: custom observers may expose PII, credentials, or large payloads. Default: false. |  |
| `reactive.http.observability.metric-name` | `java.lang.String` | `"reactive.http.client.requests"` | Micrometer timer/counter name for outbound HTTP client requests. Default: reactive.http.client.requests. |  |
| `reactive.http.observability.otel.enabled` | `java.lang.Boolean` | `true` | Master switch for OpenTelemetry observer and propagation auto-configuration when the reactive-http-client-otel module and an OpenTelemetry bean are present. Default: true. |  |
| `reactive.http.observability.otel.propagation.enabled` | `java.lang.Boolean` | `true` | Enable inbound and outbound OpenTelemetry context propagation filters while keeping span recording independently configurable. Default: true. |  |
| `reactive.http.observability.otel.spans.enabled` | `java.lang.Boolean` | `true` | Enable OpenTelemetry client span recording while keeping propagation independently configurable. Default: true. |  |
