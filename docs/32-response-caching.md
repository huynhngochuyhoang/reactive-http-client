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
              vary-by-headers: [Idempotency-Key]
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
              vary-by-headers: [Accept-Language, Idempotency-Key]
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
`vary-by-headers` must name a declared header/idempotency parameter, a
method-level generated idempotency header, the conventional `Idempotency-Key`
header available through `RequestContext.withIdempotencyKey(...)`, or a
configured default header and is case-insensitive. Because any invocation can
supply the context-only idempotency header, a selected policy must either vary
by its effective idempotency header or explicitly acknowledge reuse with
`shared-response: true`. `vary-by-context` reads string keys from the
subscriber's Reactor context. Blank, duplicate, unknown parameter/header, and
ambiguous declarations fail startup.

For an authenticated method with no explicit parameter/header/context
partition, set `shared-response: true` only when the response is deliberately
shared across identities. The effective idempotency header alone is not an
authenticated identity partition because it may be absent and is required for
every non-shared policy. Select at least one additional stable parameter,
header, or context dimension, or explicitly acknowledge `shared-response`.
The same acknowledgement is required when dynamic headers, header maps, or a
body are intentionally omitted. It cannot be used to remove explicitly
selected variants; those dimensions still partition the response.

Request IDs, correlation IDs, trace IDs, and similarly unique values are poor
cache variants: they make nearly every call a miss and provide no response
isolation. Prefer stable tenant, locale, authorization-scope, or business
partition values.

Supported selected values are null, primitive/scalar values, strings, enums,
scalar records, arrays, typed lists/sets/maps, and optionals. The starter
defensively freezes one snapshot per subscription and uses it for both the key
and request materialization. Supported records are reconstructed from one
captured accessor pass; an accessor that cannot reproduce that captured state
is rejected before dispatch. Publishers, streams, resources, raw containers,
unresolved generics, mutable DTOs, and mutable nested record components also
fail before transport dispatch. A top-level query array is supported and
expands to ordered query values. Arrays used as path values or nested inside
query elements are rejected because the current request-target conversion would
serialize them by object identity; format those values as stable scalars
instead. Arrays of containers must use a component type such as `List`, `Set`,
or `Map` that can hold the defensive snapshot. Incompatible concrete or
covariant runtime array components fail before dispatch instead of producing an
array-store error.
Freezing and startup validation count one depth level per nested container or
record and enforce one cumulative 10,000-element budget across the selected
argument graph and another across selected Reactor context values. Container
elements, optional values, and record components consume that budget, so shared
or deeply nested object graphs cannot expand without a bound. Runtime freezing
charges actual iterated list, set, and map members instead of trusting reported
container sizes. It also preserves equal-by-value elements from identity-based
sets and every iterated identity-map entry so the frozen request cannot silently
lose query, header, or body values.

The canonical representation uses type tags, explicit nulls, length framing,
container boundaries, and sorted map/set encodings for values that are not
request-bound. Path/query dimensions use a bounded structural string snapshot
that is then passed to URI construction, so repeated nested values cannot build
an unbounded intermediate projection and the key sees the exact dispatched
value. A selected body or header set, or a selected body map, preserves the
iteration order sent on the wire instead of being sorted for the key. URI
variants retain their non-normalized text, so a literal Unicode path and an
explicitly percent-escaped path remain distinct. These projections prevent
wire-distinct requests from collapsing into one structural key. Canonical
encoding and request-target projection each have a cumulative 1 MiB byte limit.
UTF-8 scalar length, URI text length, and `BigInteger`/`BigDecimal` encoded
magnitude length are checked before encoded scalar bytes are materialized, so
one oversized scalar cannot bypass the allocation bound. This wire projection
is not a fallback for arbitrary
`@CacheKey` or Reactor-context values. Only the SHA-256 digest is retained as
the local opaque key. Raw values and digest text are never exported through
metrics, logs, traces, diagnostics, health, or support bundles. Auth tokens,
credentials, and cookies selected as variants therefore never become ordinary
retained key text.

### Native context record values

The AOT processor registers record accessors and canonical constructors
reachable from selected client method parameters. A record used only as a
runtime `vary-by-context` value has no discoverable Java type in the client
contract, so a native application must register both explicitly:

```java
import org.springframework.aot.hint.ExecutableMode;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

record SalesRegion(String region, int tier) {
}

final class CacheContextRuntimeHints implements RuntimeHintsRegistrar {
    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        hints.reflection().registerType(SalesRegion.class, typeHint -> {});
        var components = SalesRegion.class.getRecordComponents();
        for (var component : components) {
            hints.reflection().registerMethod(
                    component.getAccessor(), ExecutableMode.INVOKE);
        }
        try {
            hints.reflection().registerConstructor(
                    SalesRegion.class.getDeclaredConstructor(String.class, int.class),
                    ExecutableMode.INVOKE);
        } catch (NoSuchMethodException ex) {
            throw new IllegalStateException(ex);
        }
    }
}

@Configuration
@ImportRuntimeHints(CacheContextRuntimeHints.class)
class CacheNativeConfiguration {
}
```

JVM applications need no additional hint. Native applications that do not
register a context-only record must use a supported scalar/container context
value instead.

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
