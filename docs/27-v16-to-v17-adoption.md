# V16 to V17 Adoption Guide

Use this guide after upgrading from the V16 release line. The V17 work is mostly
about making V16 diagnostics and strict validation easier to adopt safely; start
by capturing support-safe evidence, then enable strict startup validation one
client at a time.

## Recommended Order

1. Confirm the application still follows the [Quick Start](01-quick-start.md)
   setup and uses the expected starter version.
2. Capture a diagnostics snapshot or `rhttpclients` endpoint response before
   changing strict validation settings.
3. Capture a small [Production Support Bundle](26-support-bundles.md) for the
   clients you plan to audit.
4. Review health details, startup summaries, and metadata-only exchange logs for
   the same clients.
5. Roll out strict unsafe-retry validation for one client at a time.
6. Roll out strict built-in AWS SigV4 body-signing validation only for clients
   whose body shapes match the documented contract.
7. Keep benchmark and compatibility evidence tied to the release workflow in
   [Benchmarks](22-benchmarks.md) and
   [Native Image and Release Compatibility](20-native-release-compatibility.md).

## Capture Diagnostics First

Provider-backed `ReactiveHttpClientDiagnosticsSnapshot` output and the opt-in
Actuator endpoint both expose sanitized configured-client summaries. Capture one
of them before enabling strict validation so startup failures can be compared
with the previous effective policy.

```yaml
reactive:
  http:
    observability:
      diagnostics-endpoint:
        enabled: true
    clients:
      inventory-api:
        base-url: https://inventory-api.example.invalid
        request-timeout-ms: 750
        log-exchange: true
        log-preset: metadata-only

management:
  endpoint:
    health:
      show-details: always
  endpoints:
    web:
      exposure:
        include: health,rhttpclients

logging:
  level:
    io.github.huynhngochuyhoang.httpstarter.core.ReactiveHttpClientFactoryBean: DEBUG
    io.github.huynhngochuyhoang.httpstarter.core.DefaultHttpExchangeLogger: INFO
```

Health details show recent Micrometer error-rate status, sample counts, and
thresholds. Startup summaries show sanitized client configuration at DEBUG.
The `rhttpclients` endpoint shows sanitized configured-client diagnostics when
Actuator is present, the endpoint property is enabled, and the endpoint id is
exposed. It does not expose request bodies, response bodies, concrete base URLs,
secret header values, or auth-provider bean names. See
[Observability](08-observability.md), [Diagnostic Context Contracts](21-diagnostic-contexts.md),
and [Production Support Bundles](26-support-bundles.md).

## Roll Out Strict Unsafe-Retry Validation

Strict unsafe-retry validation is a startup guard for retry-enabled unsafe
methods that have no startup-provable `Idempotency-Key` contract. Enable it only
after the diagnostics snapshot and support bundle are captured for the target
client.

```yaml
reactive:
  http:
    clients:
      payment-api:
        base-url: https://payment-api.example.invalid
        default-headers:
          Idempotency-Key: ${PAYMENT_IDEMPOTENCY_KEY}
        resilience:
          enabled: true
          retry: paymentWriteRetry
          retry-methods:
            - GET
            - HEAD
            - POST
          strict-unsafe-retry-validation: true
```

Rollout guidance:

- Keep the default warning-only behavior while reviewing one client.
- Prefer idempotent HTTP methods for retryable calls when the downstream
  contract allows it.
- For retryable unsafe methods, use method-level `@IdempotencyKey` generation
  or a static default `Idempotency-Key` that the method cannot override
  dynamically.
- Leave strict validation disabled for methods that rely on Reactor context,
  `@HeaderParam`, `@IdempotencyKey` parameters, or header maps for the key.
  Those values can still be absent for a specific invocation, so they are
  runtime contracts rather than startup-provable contracts.

The starter only sends an idempotency key. It does not provide downstream
idempotency storage or duplicate-response replay. See
[Resilience4j Integration](07-resilience4j.md) and
[Correlation ID](09-correlation-id.md).

## Roll Out Strict Built-In SigV4 Body Signing

Strict body-signing validation is only for the starter built-in object-style
AWS SigV4 provider. Enable it when the method contracts use body shapes whose
stable bytes can be proven at startup.

```yaml
reactive:
  http:
    clients:
      inventory-api:
        base-url: https://inventory-api.example.invalid
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

Use built-in strict signing for empty bodies, `byte[]`, charset-declared
`String` bodies, and concrete JSON DTO or object bodies whose WebClient codecs
stay aligned with the configured `ObjectMapper`. Do not enable it for clients
that send publisher, multipart, resource, Java stream, erased `Object`, or
dynamic non-JSON body shapes through the built-in provider.

Named `auth-provider` beans and custom `AuthProviderFactory` selections own
their own signing contract. Keep strict built-in SigV4 validation disabled for
custom streaming signatures, custom multipart signing, or providers that sign
bytes produced outside the starter auth pipeline. See
[Outbound Auth Providers](06-auth-providers.md).

## Related Docs

- [Quick Start](01-quick-start.md)
- [Outbound Auth Providers](06-auth-providers.md)
- [Resilience4j Integration](07-resilience4j.md)
- [Observability](08-observability.md)
- [Production Support Bundles](26-support-bundles.md)
- [Benchmarks](22-benchmarks.md)
- [Native Image and Release Compatibility](20-native-release-compatibility.md)
