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
        base-url: https://abc123.execute-api.us-east-1.amazonaws.com/prod
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
        host: proxy.corp.example
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
