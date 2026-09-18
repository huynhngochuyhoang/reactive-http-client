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
The application owns the replacement connector's pool, executors and disposal;
closing the starter factory does not close that pool. Configure transport retry
and other network behavior on the replacement explicitly. The starter's
disabled automatic transport retry applies to its own connector, not arbitrary
application connectors.

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
If the application replaces the builder bean, it must apply any desired ordered
Boot customizers itself; an arbitrary replacement does not inherit that work.

Per-client `ReactiveHttpClientCustomizer` beans run **after** starter per-client
filters such as correlation-ID propagation and outbound auth are registered. After
customizers have been applied, the starter appends a final diagnostics filter that
captures the outbound method, URL, and headers for exchange logging and observers.
This order describes append-only customizers. A customizer that replaces or
reorders filters must preserve the required auth, framing and observation behavior.

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

For a client without response caching, registering the bean is sufficient.
Cache-selected clients also require the
[customization-safety inventory](32-response-caching.md#customization-safety),
including applicable Boot/per-client customizers and replacement builders.
When exchange logging uses the `headers` or `bodies` preset, the default logger
logs the final outbound headers and redacts only the fixed
`SensitiveHeaders.DEFAULTS` names. `X-Signature` is not in that set and will be
logged. The configurable inbound-header deny-list affects captured inbound
headers only; it does not configure outbound redaction.

For this HMAC example, use `metadata-only` to omit headers (or disable exchange
logging), or explicitly select a custom `HttpExchangeLogger` through the
`@LogHttpExchange` annotation's `logger` attribute. That logger must redact
`X-Signature` as well as the default sensitive headers before writing them.
See [Custom logger](13-exchange-logging.md#custom-logger).

### Cache-aware execution

On published `4.4.0`, a selected cache call builds a non-dispatching probe through
the same defaults and filters before lookup, even on a warm hit. A miss then
builds the load request, so a filter/defaultRequest callback is not guaranteed to
run only once per logical call. Resilience retries and 401 auth replay can run
further filter passes; native redirects do not re-enter the WebClient filters.
Keep these callbacks safe to repeat and include response-affecting inputs in the
selected key variants. Mandatory per-caller gates belong before lookup, not only
in a replacement exchange function, which is load-only.

Do not copy a blanket `SAFE` classification. Inspect the entire mutation and
verify hit, miss and replay behavior, or leave caching unselected. The
[reviewed extension scenarios](../roadmaps/v32/MAINTAINER-GUIDANCE.md#choosing-an-extension)
record working public alternatives and the deferred starter-builder
classification gap (F001); they do not add a new SPI or waive validation.

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
