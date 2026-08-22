# V27 Implicit Resilience Activation Baseline

This inventory freezes the behavior before Priority 2.2 changes resilience
selection. It describes the published `3.6.0` contract and the unchanged
`4.0.0-SNAPSHOT` starting implementation. It is characterization evidence, not
the V27 target behavior.

## Current Behavior

| Input or condition | Current behavior before Priority 2.2 |
|---|---|
| `enabled: false` | The invocation handler applies no resilience operator. |
| `enabled: true` with no instance properties | Constructor defaults select available Retry, RateLimiter, CircuitBreaker, and Bulkhead instances named `default`. |
| Client-level instance property | The configured value is selected when no method annotation overrides it. |
| Method-level annotation | A nonblank annotation value wins over the client-level value while the master switch is enabled. Blank annotation values fail metadata parsing. |
| Blank client-level instance property | The blank value is not normalized to disabled. It is forwarded to the operator applier. An absent property instead retains the constructor value `default`. |
| Missing operator registry or adapter | The corresponding Resilience4j applier method is pass-through. Contract export and diagnostics report `unavailable`. |
| `retry-methods` | The set controls Retry eligibility only. Its default is `GET` and `HEAD`; it does not gate RateLimiter, CircuitBreaker, or Bulkhead. |
| Strict unsafe-retry validation | Disabled by default. When selected, it runs only for an enabled, available Retry that can make another attempt and an eligible unsafe method without a startup-provable idempotency key. |

Operator assembly order is `retry -> rate-limiter -> circuit-breaker ->
bulkhead`. Because Reactor subscribes from the outermost publisher inward, the
subscription order is `logical-call-timeout -> bulkhead -> circuit-breaker ->
rate-limiter -> retry -> request-attempt`.

## Direct Read Inventory

| Surface | Current activation reads |
|---|---|
| `ReactiveHttpClientProperties.ResilienceConfig` | Owns `enabled=false`, four `default` instance names, `retryMethods=[GET, HEAD]`, and warning-only strict validation defaults. `ClientConfig` creates this object eagerly. |
| `ReactiveClientInvocationHandler` | `applyResilienceMono` and `applyResilienceFlux` read the master gate, retry methods, method annotations, and all four client instance values. `usesSubscriptionState` also treats enabled resilience as stateful. |
| `ReactiveHttpClientFactoryBean` | Proxy construction resolves all four optional registries, then uses the master gate for per-method instance validation, strict retry validation, and method diagnostics. Startup summaries read all four values and retry methods. |
| `EffectiveHttpClientContractExporter` | `resiliencePolicy` independently resolves the master gate, retry eligibility, method precedence, availability, and all four instance values. |
| `ReactiveHttpClientDiagnosticsProvider` | Client summaries consume exporter policy. Provider-backed strict-retry diagnostics additionally read the master gate, retry methods, method override/client Retry value, availability, configured-instance state, and maximum attempts. |
| `MockReactiveHttpClient` | `clientConfig(...)` and `resilienceOperatorApplier(...)` delegate selection to the production handler. `retry(...)` creates an enabled config with explicit instance `mock` and explicit retry methods. |
| Metadata and docs | Additional Spring metadata and `docs/configuration-properties.md` publish the four `default` values. `docs/07-resilience4j.md`, annotation docs, examples, and `docs/31-3x-to-4x-resilience-migration.md` describe the current gate, precedence, retry eligibility, and migration target. |

## Characterization Evidence

- The pre-change focused characterization run covered enabled-only Mono and
  Flux calls, application/subscription order, method precedence, blank client
  values, unavailable operators, retry-method gating, and configuration
  defaults before the executable suite moved to the 4.x contract.
- `PerMethodResilienceTest` covers annotation parsing, blank annotation
  rejection, client fallback, and missing method-level instances.
- `ReactiveHttpClientFactoryBeanDiagnosticsTest` covers strict retry enabled,
  disabled, unavailable, single-attempt, unsafe-method, and idempotency cases.
- `EffectiveHttpClientContractExporterTest` covers retry-method exclusion and
  unavailable operator reporting.
- `ReactiveHttpClientConfigurationMetadataTest` freezes the published property
  defaults until Priority 2.2 deliberately changes them.
