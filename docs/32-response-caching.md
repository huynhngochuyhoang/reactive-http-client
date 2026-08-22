# Response Caching

V27 introduces an explicit local response-cache contract in four phases. This
page currently freezes policy selection and startup eligibility. The Priority 4
implementation does not yet store or reuse responses; bounded storage is added
only after key and variant isolation are complete.

## Explicit selection

Define named policies under one client. A policy definition is inert until the
client or a method explicitly selects it.

```yaml
reactive:
  http:
    clients:
      catalog-service:
        base-url: https://catalog.example.invalid
        cache:
          policy: catalog-read
          policies:
            catalog-read:
              ttl-ms: 60000
              maximum-size: 10000
```

Omitting `cache.policy` keeps client-wide caching disabled. Adding a future
cache implementation dependency or declaring policies never selects caching.

Method precedence is:

1. `@CacheDisabled` excludes the method.
2. `@CacheResponse("name")` selects that policy.
3. A non-blank client `cache.policy` applies.
4. Otherwise caching is disabled.

The same method annotations work on inherited endpoints, overloads, and
`@ApiRef` methods. Policy names are trimmed and must not be blank. A selected
policy must exist and must define:

- `ttl-ms`: `1` through `31536000000` (365 days).
- `maximum-size`: `1` through `1000000` entries.

Missing, zero, negative, or larger values fail startup and identify the
concrete client, Java method, policy source, and `@ApiRef` key when applicable.

## Initial eligibility

Only a resolved `GET` returning a finite `Mono<T>` or
`Mono<ResponseEntity<T>>` is eligible. Startup rejects selected caching for:

- `Flux`, raw `Mono`, `Mono<Void>`, and streaming response envelopes;
- `Publisher`, `DataBuffer`, `Resource`, unresolved wildcard/type-variable,
  raw generic, and unknown `Object` response values;
- multipart requests and request bodies owned as a publisher, data buffer,
  resource, input stream, reader, or readable byte channel;
- every resolved HTTP method other than `GET`, including a non-GET `@ApiRef`.

When storage is added, only a final successful, non-null decoded emission is a
cache candidate. Empty completion, cancellation, redirect, auth/decode/
transport/resilience failure, and mapped 4xx/5xx failure never create an entry.

A cached `ResponseEntity` represents the previously materialized application
value, status, and only the bounded representation-header allowlist defined by
the storage phase. A hit does not create a new downstream wire response.
Per-caller and sensitive response headers are never reusable cache metadata.

## Customization safety

A cache hit will eventually occur before HTTP dispatch, so arbitrary builder
customization cannot be assumed safe. For a selected client, startup inventories
all applicable Boot `WebClientCustomizer` beans, matching
`ReactiveHttpClientCustomizer` beans, and replacement `WebClient.Builder`
beans. Every bean must be classified by Spring bean name:

```yaml
reactive:
  http:
    clients:
      catalog-service:
        cache:
          customizations:
            webClientCodecCustomizer: SAFE
            catalogAuditCustomizer: SAFE
```

`SAFE` is an explicit assertion that the complete builder mutation cannot add
a per-caller authorization/tenant gate, change an omitted response variant, or
alter decoded value semantics. `INCOMPATIBLE` and missing classifications reject
selected caching. Do not label a dynamic `defaultRequest`, authorization or
tenant filter, exchange-function replacement, codec/connector mutation, or
other request/response transformation `SAFE` without accounting for its full
effect.

The cache runtime must execute mandatory authorization, tenant, and policy
checks at a cache-aware pre-lookup boundary for both hits and misses. Until a
customization is represented by that gate or by the key/variant contract, mark
it `INCOMPATIBLE`; classification is not a bypass mechanism.

## Contract evidence

Proxy startup, AOT processing, effective-contract export, diagnostics, and
`MockReactiveHttpClient` use the same `MethodMetadataCache`-backed grammar.
Replacement metadata caches remain authoritative. Contract snapshots expose a
bounded `Cache` cell with only source, TTL, and maximum size; policy names and
request values are not exported.
