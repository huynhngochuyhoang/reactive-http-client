# Response Caching

V27 introduces an explicit local response-cache contract in four phases. This
page currently freezes policy selection, startup eligibility, and key isolation.
The Priority 5 implementation still does not store or reuse responses; bounded
storage is added in phase one only after this contract.

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

## Key and variant isolation

Every key includes the logical and concrete client identity plus the full
generic-resolved method signature. Path and query values are always included.
Additional values are explicit:

```yaml
reactive:
  http:
    clients:
      catalog-service:
        cache:
          policies:
            catalog-read:
              ttl-ms: 60000
              maximum-size: 10000
              vary-by-parameters: [tenant]
              vary-by-headers: [Accept-Language]
              vary-by-context: [salesRegion]
```

```java
@GET("/catalog/{id}")
@CacheResponse("catalog-read")
Mono<CatalogItem> getItem(
        @PathVar("id") String id,
        @HeaderParam("Accept-Language") String language,
        @CacheKey("tenant") String tenantId);
```

`vary-by-parameters` names stable `@CacheKey` labels.
`vary-by-headers` must name a declared header/idempotency parameter or a
configured default header and is case-insensitive. `vary-by-context` reads
string keys from the subscriber's Reactor context. Blank, duplicate, unknown
parameter/header, and ambiguous declarations fail startup.

For an authenticated method with no explicit parameter/header/context
partition, set `shared-response: true` only when the response is deliberately
shared across identities. The same acknowledgement is required when dynamic
headers, header maps, or a body are intentionally omitted. It cannot be
used to remove explicitly selected variants; those dimensions still partition
the response.

Request IDs, correlation IDs, trace IDs, and similarly unique values are poor
cache variants: they make nearly every call a miss and provide no response
isolation. Prefer stable tenant, locale, authorization-scope, or business
partition values.

Supported selected values are null, primitive/scalar values, strings, enums,
scalar records, arrays, typed lists/sets/maps, and optionals. The starter
defensively freezes one snapshot per subscription and uses it for both the key
and request materialization. Publishers, streams, resources, raw containers,
unresolved generics, mutable DTOs, and mutable nested record components fail
before transport dispatch.

The canonical representation uses type tags, explicit nulls, length framing,
container boundaries, and sorted map/set encodings. Only its SHA-256 digest is
retained as the local opaque key. Raw values and digest text are never exported
through metrics, logs, traces, diagnostics, health, or support bundles. Auth
tokens, credentials, and cookies selected as variants therefore never become
ordinary retained key text.

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
bounded `Cache` cell with only source, TTL, and maximum size; policy names,
raw values, and opaque key digests are not exported.
