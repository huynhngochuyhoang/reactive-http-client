# Conflict and Cardinality Guardrails

This page documents precedence when the same request behavior can be supplied in
multiple places, and identifies observability dimensions that can expand metric
or trace cardinality.

---

## Request Precedence

| Area | Highest precedence | Fallback | Behavior |
|---|---|---|---|
| Client base URL | `@ReactiveHttpClient(baseUrl = "...")` | `reactive.http.clients.<name>.base-url` | Annotation value wins. A missing effective base URL fails startup. |
| HTTP method and path | HTTP verb annotation or `@ApiRef`, but not both | none | Combining `@ApiRef` with `@GET`, `@POST`, `@PUT`, `@DELETE`, or `@PATCH` fails metadata parsing. |
| `@ApiRef` map | `reactive.http.clients.<name>.apis.<api>.method/path/timeout-ms` | none | Missing API entries, blank method, or blank path fail at proxy construction. |
| Path variables | `@PathVar` arguments | none | Path variables are resolved per invocation. Missing placeholders fail when the URI is built. |
| Query parameters | `@QueryParam` arguments | `default-query-params` | Same-name method query parameters replace configured defaults. Multi-value parameters are sent as repeated query parameters. |
| Headers | `@HeaderParam` arguments | `default-headers` | Same-name headers override configured defaults case-insensitively. |
| Request body | `@Body` or `@MultipartBody` parts | none | `@Body` and `@MultipartBody` are mutually exclusive and fail fast when combined. |
| Auth | `auth-provider` bean name | object-style `auth.type` | If both are configured, the named bean wins and the object-style block is ignored with a startup warning. |
| Customizers | `ReactiveHttpClientCustomizer` | Spring `WebClientCustomizer` | Spring `WebClientCustomizer` runs when the prototype builder is created. Starter built-ins are then added. Per-client `ReactiveHttpClientCustomizer` runs after built-ins. |
| Resilience instance names | Method annotations (`@Retry`, `@CircuitBreaker`, `@Bulkhead`, `@RateLimiter`) | client-level `resilience.*` names | Method-level names win. Missing method-level instances fail at proxy construction when resilience is enabled. |
| Timeout | `@TimeoutMs` | `@ApiRef timeout-ms`, then `request-timeout-ms`, then deprecated `resilience.timeout-ms` | `0` explicitly disables the per-request timeout. If both client timeout properties are configured, `request-timeout-ms` wins and a startup warning is logged. Network read/write safety nets still apply. |
| Exchange logging | method-level `@LogHttpExchange` | interface-level `@LogHttpExchange`, then `log-exchange` | Method-level logger wins. Interface-level logger wins over property-enabled default logging. |

Remaining ambiguity is intentionally rejected rather than guessed:

- A method cannot combine `@ApiRef` with an HTTP verb annotation.
- A multipart method cannot also have `@Body`.
- `@FormField` and `@FormFile` require `@MultipartBody`.
- Invalid default headers, query parameters, proxy, TLS, and missing auth-provider beans fail at startup.


---

## Named Built-in Beans

Registering a bean with one of these names replaces that built-in while leaving unrelated starter beans intact:

| Bean name | Type | Purpose |
|---|---|---|
| `starterWebClientBuilder` | `WebClient.Builder` | Overrides the starter-managed prototype builder. Spring `WebClientCustomizer` beans are applied only by the starter-managed builder. |
| `micrometerHttpClientObserver` | `HttpClientObserver` | Replaces the built-in Micrometer observer. Additional differently named observers run alongside built-ins. |
| `openTelemetryHttpClientObserver` | `HttpClientObserver` | Replaces the built-in OpenTelemetry observer from the optional OTel module. |
| `reactiveHttpClientHealthIndicator` | `HttpClientHealthIndicator` | Replaces the built-in Actuator health indicator. |
| `oauth2ClientCredentialsAuthProviderFactory` | `AuthProviderFactory` | Replaces the built-in OAuth2 client-credentials auth factory. |
| `awsSigV4AuthProviderFactory` | `AuthProviderFactory` | Replaces the built-in AWS SigV4 auth factory. |
| `defaultErrorDecoder` | `DefaultErrorDecoder` | Replaces default non-2xx response decoding. |
| `methodMetadataCache` | `MethodMetadataCache` | Replaces method metadata caching/parsing. |

---

## Observability Cardinality

Low-cardinality dimensions are recorded by default:

| Dimension | Micrometer | OpenTelemetry | Default |
|---|---|---|---|
| Client name | `client.name` | `rhttp.client.name` | on |
| API name | `api.name` | `rhttp.api.name` | on |
| HTTP method | `http.method` | `http.request.method` | on |
| HTTP status | `http.status_code` | `http.response.status_code` | on when known |
| Error category | `error.category` | `error.type` | on for errors |

Potentially high-cardinality dimensions are opt-in:

| Dimension | Config | Risk |
|---|---|---|
| Path template / URI | `reactive.http.observability.include-url-path: true` | Safe only when templates are bounded, such as `/users/{id}`. Do not enable when raw IDs or dynamic paths can appear. |
| Resolved server host and port | `reactive.http.observability.include-server-address: true` | Hostnames can vary per tenant, region, shard, or service discovery result. |
| Request and response bodies in spans | `log-request-body` / `log-response-body` | Bodies can contain PII, secrets, and large values. They are disabled by default. |
| Connection pool metrics | `network.connection-pool.metrics-enabled` or client `pool.metrics-enabled` | Pool names include the client name. Keep client names bounded and do not encode tenant IDs. |

The latency histogram meter is also opt-in. When enabled, it intentionally uses
only `client.name`, `api.name`, `http.method`, and `uri`, and follows the same
`include-url-path` gate for the `uri` value.
