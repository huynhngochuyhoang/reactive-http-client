# Per-Client WebClient Customizer

`ReactiveHttpClientCustomizer` is an extension point that lets you apply arbitrary
`WebClient.Builder` customizations — including custom `ExchangeFilterFunction`s — to
one or more reactive HTTP clients **without recreating a raw `WebClient`** and losing
all starter-managed filters and configuration.

Do not use this hook just to enable HTTP/2. Use
`reactive.http.clients.<name>.http2-enabled: true` instead, so the starter keeps
owning the Reactor Netty connector and all network settings still apply.

Avoid calling `builder.clientConnector(...)` from a customizer unless you intend to
replace the starter-managed Reactor Netty connector. Replacing it bypasses the
starter's configured connection pool, timeouts, compression, proxy, TLS/mTLS, and
HTTP/2 settings for that client.

---

## Overview

Register any number of `ReactiveHttpClientCustomizer` beans in your Spring context.
During proxy construction, `ReactiveHttpClientFactoryBean` will:

1. Collect every `ReactiveHttpClientCustomizer` bean in `@Order / Ordered` sequence.
2. Call `supports(clientName)` on each one.
3. Apply `customize(builder)` on every customizer that returned `true`.

Spring `WebClientCustomizer` beans run first when the starter creates its
prototype `WebClient.Builder`. Optional companion modules, including
`reactive-http-client-otel`, use that hook to add global filters such as OTel
outbound propagation.

Per-client `ReactiveHttpClientCustomizer` beans run **after** starter per-client
filters such as correlation-ID propagation and outbound auth are registered. After
customizers have been applied, the starter appends a final diagnostics filter that
captures the outbound method, URL, and headers for exchange logging and observers.

At DEBUG level, the starter logs the applied `WebClientCustomizer` classes and the
per-client `ReactiveHttpClientCustomizer` classes in execution order.

---

## Interface

```java
@FunctionalInterface
public interface ReactiveHttpClientCustomizer {

    /**
     * Return {@code false} to skip this client. Defaults to {@code true} (apply to all).
     */
    default boolean supports(String clientName) {
        return true;
    }

    void customize(WebClient.Builder builder);
}
```

---

## Adding a custom filter to one specific client

```java
@Component
public class RequestSigningCustomizer implements ReactiveHttpClientCustomizer {

    private final HmacSigner signer;

    public RequestSigningCustomizer(HmacSigner signer) {
        this.signer = signer;
    }

    @Override
    public boolean supports(String clientName) {
        return "payment-service".equals(clientName);
    }

    @Override
    public void customize(WebClient.Builder builder) {
        builder.filter((request, next) -> {
            ClientRequest signed = ClientRequest.from(request)
                .header("X-Signature", signer.sign(request))
                .build();
            return next.exchange(signed);
        });
    }
}
```

No extra configuration is required — registering the bean is sufficient. When exchange logging uses the `headers` or `bodies` preset, the default logger reports this final outbound header after redaction rules are applied.

---

## Applying a customizer to all clients

Omit the `supports()` override. The default implementation returns `true` for every
client name:

```java
@Component
public class DebugHeaderCustomizer implements ReactiveHttpClientCustomizer {

    @Override
    public void customize(WebClient.Builder builder) {
        builder.defaultHeader("X-Debug-Source", "reactive-http-client");
    }
}
```

Lambdas are equivalent because `ReactiveHttpClientCustomizer` is a `@FunctionalInterface`:

```java
@Bean
ReactiveHttpClientCustomizer addDebugHeader() {
    return builder -> builder.defaultHeader("X-Debug-Source", "reactive-http-client");
}
```

---

## Controlling execution order

Use Spring's standard `@Order` annotation or implement `org.springframework.core.Ordered`.
Lower values run first.

```java
@Component
@Order(1)
public class TracingFilterCustomizer implements ReactiveHttpClientCustomizer {
    @Override
    public void customize(WebClient.Builder builder) {
        builder.filter(tracingFilter());
    }
}

@Component
@Order(2)
public class AuditFilterCustomizer implements ReactiveHttpClientCustomizer {
    @Override
    public void customize(WebClient.Builder builder) {
        builder.filter(auditFilter());
    }
}
```

When no `@Order` is declared, Spring's default bean-registration order applies.

---

## Targeting multiple clients

```java
@Component
public class InternalServiceCustomizer implements ReactiveHttpClientCustomizer {

    private static final Set<String> INTERNAL = Set.of("order-service", "inventory-service");

    @Override
    public boolean supports(String clientName) {
        return INTERNAL.contains(clientName);
    }

    @Override
    public void customize(WebClient.Builder builder) {
        builder.filter(internalServiceFilter());
    }
}
```
