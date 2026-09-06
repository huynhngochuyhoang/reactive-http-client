# Effective Configuration Examples

These snippets show effective starter policy by client: the configured base URL,
timeout, auth, retry, redirect, proxy, TLS, and diagnostics settings that apply
when the proxy is constructed. They are documentation examples validated against
the generated Spring configuration metadata by `ReactiveHttpClientConfigurationMetadataTest`.

## Shared Interface, Separate Clients

Use the same inherited endpoint interface for multiple downstreams, then give
each concrete client its own base URL, timeout, redirect policy, and `@ApiRef`
map.

```yaml
reactive:
  http:
    clients:
      bus-api:
        base-url: https://bus-api.example.invalid
        request-timeout-ms: 1500
        follow-redirects: false
        apis:
          get-order:
            method: GET
            path: /api/order
            timeout-ms: 1200
      train-api:
        base-url: https://train-api.example.invalid
        request-timeout-ms: 2500
        follow-redirects: true
        apis:
          get-order:
            method: GET
            path: /api/order
            timeout-ms: 2000
```

## OAuth2 Client Credentials

```yaml
reactive:
  http:
    clients:
      demo-billing:
        base-url: https://billing-api.example.invalid
        auth:
          type: oauth2-client-credentials
          oauth2-client-credentials:
            token-uri: https://auth.example.invalid/oauth2/token
            client-id: ${DEMO_BILLING_CLIENT_ID}
            client-secret: ${DEMO_BILLING_CLIENT_SECRET}
            auth-style: form-post
            expiry-leeway-ms: 60000
```

## AWS SigV4 With Strict Body Signing

```yaml
reactive:
  http:
    clients:
      inventory-aws:
        base-url: https://EXAMPLE_API_ID.execute-api.us-east-1.amazonaws.com/prod
        default-headers:
          Content-Type: application/json
        auth:
          type: aws-sigv4
          aws-sig-v4:
            access-key-id: ${AWS_ACCESS_KEY_ID}
            secret-access-key: ${AWS_SECRET_ACCESS_KEY}
            session-token: ${AWS_SESSION_TOKEN:}
            region: us-east-1
            service: execute-api
            strict-body-signing-validation: true
```

## Proxy And TLS Overrides

```yaml
reactive:
  http:
    network:
      proxy:
        type: HTTP
        host: proxy.corp.example.invalid
        port: 3128
        username: ${PROXY_USERNAME}
        password: ${PROXY_PASSWORD}
        non-proxy-hosts: "localhost|.*\\.internal"
      tls:
        trust-store: classpath:global-truststore.p12
        trust-store-password: ${GLOBAL_TRUSTSTORE_PASSWORD}
        trust-store-type: PKCS12
    clients:
      partner-api:
        base-url: https://partner.example.invalid
        proxy:
          type: NONE
        tls:
          trust-store: classpath:partner-truststore.p12
          trust-store-password: ${PARTNER_TRUSTSTORE_PASSWORD}
          key-store: classpath:partner-client.p12
          key-store-password: ${PARTNER_KEYSTORE_PASSWORD}
          protocols:
            - TLSv1.3
          ciphers:
            - TLS_AES_128_GCM_SHA256
```

## Strict Retry Contract

```yaml
reactive:
  http:
    clients:
      payment-command:
        base-url: https://payments.example.invalid
        default-headers:
          Idempotency-Key: ${PAYMENT_IDEMPOTENCY_KEY}
        resilience:
          enabled: true
          retry: payment-command
          retry-methods:
            - GET
            - HEAD
            - POST
          strict-unsafe-retry-validation: true
```

## Explicit Local Response Cache

This weighted example targets the current unpublished `4.2.0` V29 release
candidate and
selects one bounded local policy explicitly. Published `4.1.x` consumers must
omit `maximum-total-decoded-response-bytes`; TTL, entry-count `maximum-size`,
single flight, refresh, and cache telemetry remain available there. The V29
byte limit is additional to TTL and `maximum-size` and counts decoded response
representation bytes, not exact Java heap, direct memory, process RSS, or
container memory. Add
the optional runtime described in the
[Caffeine dependency instructions](../32-response-caching.md#explicit-selection)
before selecting the policy. With that dependency present, this example is
startup-valid as written only when `catalog-api` has no applicable customization
beans. Before enabling it in an existing application, inventory every applicable
Boot `WebClientCustomizer`, matching `ReactiveHttpClientCustomizer`, and replacement
`WebClient.Builder` bean. For each reviewed customization, add its exact Spring bean
name under `cache.customizations` with `SAFE`. Missing and `INCOMPATIBLE`
classifications reject proxy construction; see
[Customization safety](../32-response-caching.md#customization-safety).

```yaml
reactive:
  http:
    clients:
      catalog-api:
        base-url: https://catalog-api.example.invalid
        cache:
          policy: catalog-read
          policies:
            catalog-read:
              ttl-ms: 60000
              maximum-size: 10000
              maximum-total-decoded-response-bytes: 268435456
              single-flight: true
              refresh-after-ms: 30000
              refresh-timeout-ms: 5000
              vary-by-headers:
                - Idempotency-Key
    observability:
      enabled: true
      cache:
        enabled: true
```

The client-wide policy selects cache-friendly `GET` methods but does not
acknowledge non-`GET` methods. A non-`GET` semantic read must select the policy
on that exact method:

```java
@POST("/catalog/search")
@CacheResponse(value = "catalog-read", semanticRead = true)
Mono<CatalogItem> search(@QueryParam("sku") String sku);
```

There is no client-wide semantic-read switch. The declaration guarantees that
serving a hit may omit the downstream call without omitting a required side
effect; all ordinary response, request, key, auth, and customization checks
still apply.

Cache metrics remain disabled when `observability.cache.enabled` is false, and
the observability setting never selects the policy.

## Semantic Read Cache Examples

These examples require the optional Caffeine runtime in addition to the starter:

```xml
<dependency>
  <groupId>com.github.ben-manes.caffeine</groupId>
  <artifactId>caffeine</artifactId>
</dependency>
```

Before copying the configuration, complete the
[application safety review](../32-response-caching.md#application-safety-review)
with the endpoint owner. Inventory every applicable Boot or starter WebClient
customizer and replacement builder. The example bean names below are valid only
when those exact per-client beans exist and their complete behavior has been
reviewed `SAFE`; replace them with the exact names from the application or omit
the map when no applicable customization exists. An unknown or incompatible
customization must keep caching disabled.

The catalog search uses OAuth2 credentials from environment placeholders and
partitions every entry by exact JSON criteria bytes and the declared tenant
scope header. The reporting RPC query partitions by exact JSON criteria bytes
and a frozen Reactor-context principal scope:

```yaml
reactive:
  http:
    clients:
      catalog-search:
        base-url: https://catalog-search.example.invalid
        default-headers:
          Content-Type: application/json
        auth:
          type: oauth2-client-credentials
          oauth2-client-credentials:
            token-uri: https://identity.example.invalid/oauth2/token
            client-id: ${EXAMPLE_CATALOG_CLIENT_ID}
            client-secret: ${EXAMPLE_CATALOG_CLIENT_SECRET}
            auth-style: form-post
        cache:
          customizations:
            catalogTracingCustomizer: SAFE
          policies:
            catalog-search:
              ttl-ms: 60000
              maximum-size: 10000
              single-flight: true
              vary-by-parameters: [criteria]
              vary-by-headers: [Idempotency-Key, X-Tenant-Scope]
      reporting-rpc:
        base-url: https://reporting-rpc.example.invalid
        default-headers:
          Content-Type: application/json
        auth:
          type: oauth2-client-credentials
          oauth2-client-credentials:
            token-uri: https://identity.example.invalid/oauth2/token
            client-id: ${EXAMPLE_REPORTING_CLIENT_ID}
            client-secret: ${EXAMPLE_REPORTING_CLIENT_SECRET}
            auth-style: form-post
        cache:
          customizations:
            reportingTracingCustomizer: SAFE
          policies:
            reporting-query:
              ttl-ms: 30000
              maximum-size: 5000
              vary-by-parameters: [criteria]
              vary-by-headers: [Idempotency-Key]
              vary-by-context: [principalScope]
    observability:
      enabled: true
      cache:
        enabled: true
```

```java
record CatalogSearchCriteria(String phrase, int page) {
}

@ReactiveHttpClient(
        name = "catalog-search",
        baseUrl = "https://catalog-search.example.invalid")
interface CatalogSearchClient {

    @POST("/catalog/search")
    @CacheResponse(value = "catalog-search", semanticRead = true)
    Mono<CatalogPage> search(
            @Body @CacheKey("criteria") CatalogSearchCriteria criteria,
            @HeaderParam("X-Tenant-Scope") String tenantScope);
}
```

```java
record ReportingQuery(String report, LocalDate from, LocalDate to) {
}

@ReactiveHttpClient(
        name = "reporting-rpc",
        baseUrl = "https://reporting-rpc.example.invalid")
interface ReportingRpcClient {

    @POST("/rpc/query")
    @CacheResponse(value = "reporting-query", semanticRead = true)
    Mono<ReportResult> query(
            @Body @CacheKey("criteria") ReportingQuery criteria);
}

Mono<ReportResult> result = reportingRpcClient.query(criteria)
        .contextWrite(context -> context.put("principalScope", principalScope));
```

The endpoint owner must approve the body determinism, response variants,
auth/tenant partition, TTL, optional single flight, refresh decision, and
invalidation owner for each method. Cache observability is separately selected;
it never enables a policy. Keep credential values in environment variables and
do not put request bodies, headers, cache keys or digests, identities, tenant
values, or credentials in examples, diagnostics, logs, metrics, or support
fixtures.

## Diagnostics Endpoint

```yaml
reactive:
  http:
    observability:
      diagnostics-endpoint:
        enabled: true
management:
  endpoints:
    web:
      exposure:
        include: health,rhttpclients
```
