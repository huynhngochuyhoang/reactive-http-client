# Configuration Properties

> Generated from `reactive-http-client-starter/src/main/resources/META-INF/additional-spring-configuration-metadata.json`.
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
| `reactive.http.clients.[name].auth.oauth2-client-credentials.audience` | `java.lang.String` |  | Optional OAuth2 audience sent to the token endpoint. |  |
| `reactive.http.clients.[name].auth.oauth2-client-credentials.auth-style` | `java.lang.String` | `"basic-auth"` | OAuth2 client authentication style. Values: basic-auth, form-post. Default: basic-auth. |  |
| `reactive.http.clients.[name].auth.oauth2-client-credentials.client-id` | `java.lang.String` |  | OAuth2 client ID for type oauth2-client-credentials. |  |
| `reactive.http.clients.[name].auth.oauth2-client-credentials.client-secret` | `java.lang.String` |  | OAuth2 client secret for type oauth2-client-credentials. |  |
| `reactive.http.clients.[name].auth.oauth2-client-credentials.expiry-leeway-ms` | `java.lang.Long` | `30000` | Milliseconds subtracted from OAuth2 expires_in before caching the access token. Default: 30000. |  |
| `reactive.http.clients.[name].auth.oauth2-client-credentials.scope` | `java.lang.String` |  | Optional OAuth2 scope sent to the token endpoint. |  |
| `reactive.http.clients.[name].auth.oauth2-client-credentials.token-uri` | `java.lang.String` |  | OAuth2 token endpoint URI for type oauth2-client-credentials. |  |
| `reactive.http.clients.[name].auth.type` | `java.lang.String` |  | Object-style auth provider type. Built-in values: oauth2-client-credentials, aws-sigv4. Ignored when auth-provider bean name is set. |  |
| `reactive.http.clients.[name].base-url` | `java.lang.String` |  | Base URL for this client (e.g. https://api.example.com). |  |
| `reactive.http.clients.[name].codec-max-in-memory-size-mb` | `java.lang.Integer` | `2` | Maximum in-memory codec buffer size in MiB for response decoding. Default: 2. |  |
| `reactive.http.clients.[name].compression-enabled` | `java.lang.Boolean` | `false` | Enable HTTP response compression (Accept-Encoding: gzip). Default: false. |  |
| `reactive.http.clients.[name].default-headers` | `java.util.Map<java.lang.String,java.lang.String>` |  | Static headers added to every request for this client. Method-level @HeaderParam values with the same header name override configured defaults. |  |
| `reactive.http.clients.[name].default-query-params` | `java.util.Map<java.lang.String,java.util.List<java.lang.String>>` |  | Static query parameters added to every request for this client. Method-level @QueryParam values with the same name override configured defaults. Multiple values are sent as repeated query parameters. |  |
| `reactive.http.clients.[name].http2-enabled` | `java.lang.Boolean` | `false` | Enable Reactor Netty HTTP/2 for this client. Default: false. |  |
| `reactive.http.clients.[name].log-exchange` | `java.lang.Boolean` | `false` | Enable structured HTTP exchange logging (request + response) for all methods on this client. Default: false. |  |
| `reactive.http.clients.[name].log-preset` | `io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties$LogPreset` | `"metadata-only"` | Controls how much data the default exchange logger writes when log-exchange is enabled or @LogHttpExchange uses DefaultHttpExchangeLogger. Values: metadata-only, headers, bodies. Default: metadata-only. |  |
| `reactive.http.clients.[name].pool.evict-in-background-ms` | `java.lang.Long` |  | Per-client background eviction interval override in milliseconds. 0 disables background eviction. |  |
| `reactive.http.clients.[name].pool.max-connections` | `java.lang.Integer` |  | Per-client connection pool max connections override. Overrides the global pool when set. |  |
| `reactive.http.clients.[name].pool.max-idle-time-ms` | `java.lang.Long` |  | Per-client idle eviction timeout override in milliseconds. 0 means no idle eviction. |  |
| `reactive.http.clients.[name].pool.max-life-time-ms` | `java.lang.Long` |  | Per-client connection max lifetime override in milliseconds. 0 means unlimited. |  |
| `reactive.http.clients.[name].pool.metrics-enabled` | `java.lang.Boolean` |  | Per-client pool metrics override. When true, publishes Reactor Netty pool gauges to the MeterRegistry. |  |
| `reactive.http.clients.[name].pool.pending-acquire-timeout-ms` | `java.lang.Long` |  | Per-client pending acquire timeout override in milliseconds. |  |
| `reactive.http.clients.[name].proxy.host` | `java.lang.String` |  | Per-client proxy host override. |  |
| `reactive.http.clients.[name].proxy.non-proxy-hosts` | `java.lang.String` |  | Per-client non-proxy hosts regex override. |  |
| `reactive.http.clients.[name].proxy.password` | `java.lang.String` |  | Per-client proxy authentication password override. |  |
| `reactive.http.clients.[name].proxy.port` | `java.lang.Integer` |  | Per-client proxy port override. |  |
| `reactive.http.clients.[name].proxy.type` | `io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties$ProxyConfig$Type` |  | Per-client proxy type override. Set to NONE to bypass a global proxy for this client. |  |
| `reactive.http.clients.[name].proxy.username` | `java.lang.String` |  | Per-client proxy authentication username override. |  |
| `reactive.http.clients.[name].request-timeout-ms` | `java.lang.Long` | `0` | Canonical per-request response timeout in milliseconds applied via HttpClientRequest.responseTimeout(). 0 disables the timeout. Wins over deprecated resilience.timeout-ms. Default: 0. |  |
| `reactive.http.clients.[name].resilience.bulkhead` | `java.lang.String` | `"default"` | Name of the Resilience4j Bulkhead instance from application config. Default: default. |  |
| `reactive.http.clients.[name].resilience.circuit-breaker` | `java.lang.String` | `"default"` | Name of the Resilience4j CircuitBreaker instance from application config. Default: default. |  |
| `reactive.http.clients.[name].resilience.enabled` | `java.lang.Boolean` | `false` | Master switch for Resilience4j operators (retry, circuit-breaker, bulkhead, rate-limiter) on this client. Default: false. |  |
| `reactive.http.clients.[name].resilience.rate-limiter` | `java.lang.String` | `"default"` | Name of the Resilience4j RateLimiter instance from application config. Default: default. |  |
| `reactive.http.clients.[name].resilience.retry` | `java.lang.String` | `"default"` | Name of the Resilience4j Retry instance from application config. Default: default. |  |
| `reactive.http.clients.[name].resilience.retry-methods` | `java.util.Set<java.lang.String>` | `["GET","HEAD"]` | HTTP methods eligible for retry. Values are upper-cased. Default: [GET, HEAD]. |  |
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
| `reactive.http.network.connection-pool.metrics-enabled` | `java.lang.Boolean` | `false` | When true, publishes Reactor Netty pool gauges (reactor.netty.connection.provider.*) to the MeterRegistry. Requires micrometer-core. Default: false. |  |
| `reactive.http.network.connection-pool.pending-acquire-timeout-ms` | `java.lang.Long` | `5000` | Maximum time in milliseconds to wait for a connection from the pool before failing. Default: 5000. |  |
| `reactive.http.network.network-read-timeout-ms` | `java.lang.Integer` | `60000` | Netty ReadTimeoutHandler safety-net: fires when no inbound bytes arrive for this duration. Sized well above any per-request timeout. Default: 60000. |  |
| `reactive.http.network.network-write-timeout-ms` | `java.lang.Integer` | `60000` | Netty WriteTimeoutHandler safety-net: fires when no outbound bytes are accepted for this duration. Sized well above any per-request timeout. Default: 60000. |  |
| `reactive.http.network.proxy.host` | `java.lang.String` |  | Proxy host name or IP address. |  |
| `reactive.http.network.proxy.non-proxy-hosts` | `java.lang.String` |  | Java regex pattern for hosts that bypass the proxy (pipe-separated alternatives). Example: localhost\|.*\.internal. |  |
| `reactive.http.network.proxy.password` | `java.lang.String` |  | Optional proxy authentication password. |  |
| `reactive.http.network.proxy.port` | `java.lang.Integer` |  | Proxy port number. |  |
| `reactive.http.network.proxy.type` | `io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties$ProxyConfig$Type` | `"HTTP"` | Proxy protocol type. Set to NONE to explicitly bypass a global proxy for one client. Default: HTTP. |  |
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
| `reactive.http.observability.enabled` | `java.lang.Boolean` | `true` | Master switch for all metrics and tracing. Default: true. |  |
| `reactive.http.observability.health.enabled` | `java.lang.Boolean` | `true` | Enable the Actuator health indicator for reactive HTTP clients. Default: true. |  |
| `reactive.http.observability.health.error-rate-threshold` | `java.lang.Double` | `0.5` | Error ratio threshold [0, 1] above which a client is reported DOWN. Default: 0.5. |  |
| `reactive.http.observability.health.min-samples` | `java.lang.Long` | `10` | Minimum probe-interval sample count required before evaluating a client's health. Avoids noisy DOWN status from isolated errors. Default: 10. |  |
| `reactive.http.observability.histogram.enabled` | `java.lang.Boolean` | `false` | Enable latency histogram (SLO buckets) recorded as <metricName>.latency (default: reactive.http.client.requests.latency). Default: false. |  |
| `reactive.http.observability.histogram.slo-boundaries-ms` | `java.util.List<java.lang.Long>` | `[50,100,200,500,1000,2000,5000]` | SLO bucket boundaries in milliseconds for the latency histogram. Default: [50, 100, 200, 500, 1000, 2000, 5000]. |  |
| `reactive.http.observability.include-server-address` | `java.lang.Boolean` | `false` | Include resolved server.address and server.port as Micrometer metric tags and OpenTelemetry span attributes. Disabled by default because upstream hosts can be high-cardinality. |  |
| `reactive.http.observability.include-url-path` | `java.lang.Boolean` | `false` | Include the URL path template as a Micrometer tag and OpenTelemetry url.template attribute. Opt in only when path templates are bounded and do not contain raw IDs. Default: false. |  |
| `reactive.http.observability.log-request-body` | `java.lang.Boolean` | `false` | Include the request body in span events. Caution: may expose PII or large payloads. Default: false. |  |
| `reactive.http.observability.log-response-body` | `java.lang.Boolean` | `false` | Include the response body in span events. Caution: may expose PII or large payloads. Default: false. |  |
| `reactive.http.observability.metric-name` | `java.lang.String` | `"reactive.http.client.requests"` | Micrometer timer/counter name for outbound HTTP client requests. Default: reactive.http.client.requests. |  |
