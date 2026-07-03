# Production Policy Example

This is a compact documentation example for a production-style policy set. It is
not a runnable sample service. Copy the interface and configuration shapes into
an application package, then replace every `.example.invalid` host and
`${EXAMPLE_*}` placeholder with local values.

The example combines:

- Shared inherited endpoint contracts with per-client timeout policy.
- OAuth2 client credentials for a payment command client.
- Retry on POST with a startup-provable method-level `Idempotency-Key` contract.
- Strict unsafe-retry validation.
- Built-in AWS SigV4 strict body signing only for a concrete JSON DTO body.
- Diagnostics endpoint and support-bundle capture snippets.

## Client Contracts

The shared read contract is inherited by two concrete clients. Each child client
keeps its own name, base URL, timeout, and configured `@ApiRef` mapping.

```java
interface OrderReadOperations<T extends OrderResponse> {

    @ApiRef("orders-get")
    Mono<T> getOrder(@PathVar("orderId") String orderId);
}

interface OrderResponse {
}

record BusOrderResponse(String code, String message) implements OrderResponse {
}

record TrainOrderResponse(String code, String bookingCode) implements OrderResponse {
}

@ReactiveHttpClient(name = "bus-orders")
interface BusOrdersClient extends OrderReadOperations<BusOrderResponse> {
}

@ReactiveHttpClient(name = "train-orders")
interface TrainOrdersClient extends OrderReadOperations<TrainOrderResponse> {
}
```

The payment command client uses method-level `@IdempotencyKey`, so strict retry
validation can prove that retried POST calls carry an idempotency key without
requiring a runtime header parameter or Reactor context value.

```java
@ReactiveHttpClient(name = "payment-command")
interface PaymentCommandClient {

    @POST("/payments")
    @IdempotencyKey
    Mono<PaymentReceipt> createPayment(@Body PaymentCommand command);
}

record PaymentCommand(String accountId, long amountCents) {
}

record PaymentReceipt(String id, String status) {
}
```

The SigV4 client uses a concrete JSON DTO body and no dynamic `Content-Type`
header parameter. This is the body shape that the built-in strict body-signing
validator can prove at startup when the client declares JSON content.

```java
@ReactiveHttpClient(name = "inventory-signer")
interface InventorySignerClient {

    @POST("/inventory/events")
    Mono<Void> publish(@Body InventoryEvent event);
}

record InventoryEvent(String sku, int quantity) {
}
```

## Policy Configuration

```yaml
reactive:
  http:
    observability:
      diagnostics-endpoint:
        enabled: true
    clients:
      bus-orders:
        base-url: https://bus-orders.example.invalid
        request-timeout-ms: 900
        apis:
          orders-get:
            method: GET
            path: /api/orders/{orderId}
            timeout-ms: 800
      train-orders:
        base-url: https://train-orders.example.invalid
        request-timeout-ms: 1800
        apis:
          orders-get:
            method: GET
            path: /api/orders/{orderId}
            timeout-ms: 1500
      payment-command:
        base-url: https://payments.example.invalid
        request-timeout-ms: 2500
        log-exchange: true
        log-preset: metadata-only
        auth:
          type: oauth2-client-credentials
          oauth2-client-credentials:
            token-uri: https://auth.example.invalid/oauth2/token
            client-id: ${EXAMPLE_PAYMENT_CLIENT_ID}
            client-secret: ${EXAMPLE_PAYMENT_CLIENT_SECRET}
            auth-style: form-post
            expiry-leeway-ms: 60000
        resilience:
          enabled: true
          retry: paymentCommandRetry
          retry-methods:
            - GET
            - HEAD
            - POST
          strict-unsafe-retry-validation: true
      inventory-signer:
        base-url: https://inventory-signing.example.invalid
        request-timeout-ms: 2000
        default-headers:
          Content-Type: application/json
        auth:
          type: aws-sigv4
          aws-sig-v4:
            access-key-id: ${EXAMPLE_AWS_ACCESS_KEY_ID}
            secret-access-key: ${EXAMPLE_AWS_SECRET_ACCESS_KEY}
            session-token: ${EXAMPLE_AWS_SESSION_TOKEN:}
            region: us-east-1
            service: execute-api
            strict-body-signing-validation: true

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

`paymentCommandRetry` must be a Resilience4j Retry instance that can make more
than one attempt before strict unsafe-retry validation becomes active. Keep the
method-level `@IdempotencyKey` on every retried unsafe command method, or leave
strict validation disabled for that client.

Keep `strict-body-signing-validation` limited to the built-in object-style AWS
SigV4 provider and body shapes with stable bytes. Do not enable it for publisher,
resource, multipart, Java stream, erased `Object`, or custom streaming-signature
clients.

## Support Bundle Capture

Capture diagnostics before enabling strict validation in a new environment, and
again after the policy starts successfully.

```text
support-bundle/
  diagnostics/rhttpclients.json
  health/health.json
  logs/startup-summary.log
  logs/exchange-metadata.log
  config/reactive-http-client.yml
```

```bash
curl -s "$EXAMPLE_MANAGEMENT_URL/actuator/rhttpclients" > support-bundle/diagnostics/rhttpclients.json
curl -s "$EXAMPLE_MANAGEMENT_URL/actuator/health" > support-bundle/health/health.json
```

The diagnostics endpoint and startup summaries are sanitized configured-client
views. Metadata-only exchange logs provide per-call status, duration, error, and
subscription-attempt count without collecting request or response bodies.

## Related Docs

- [Annotation Reference](../02-annotations.md)
- [Outbound Auth Providers](../06-auth-providers.md)
- [Resilience4j Integration](../07-resilience4j.md)
- [Observability](../08-observability.md)
- [Production Support Bundles](../26-support-bundles.md)
- [V16 to V17 Adoption Guide](../27-v16-to-v17-adoption.md)
