# Response Caching

V27 introduces an explicit local response-cache contract in four phases. Phase
one now provides bounded process-local TTL storage after policy selection,
startup eligibility, and key isolation have succeeded. Request coalescing,
refresh, and cache telemetry remain later, separately opt-in phases.

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

Omitting `cache.policy` keeps client-wide caching disabled. Adding the cache
implementation dependency or declaring policies never selects caching.

The starter keeps Caffeine optional so applications that do not select caching
do not acquire a cache runtime transitively. A cache-enabled application must
provide the Boot-managed dependency:

```xml
<dependency>
  <groupId>com.github.ben-manes.caffeine</groupId>
  <artifactId>caffeine</artifactId>
</dependency>
```

If a selected client starts without Caffeine, startup fails with the client and
policy name. Cache-disabled clients do not load the implementation.

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

Only a final successful, non-null decoded emission is a cache candidate. Empty
completion, cancellation, redirect, auth/decode/
transport/resilience failure, and mapped 4xx/5xx failure never create an entry.

A cached `ResponseEntity` represents the previously materialized application
value and status. Hits retain only `Content-Type`, `Content-Language`,
`Content-Encoding`, `ETag`, `Last-Modified`, `Cache-Control`, `Expires`, and
`Vary`, capped at 32 values and 16 KiB of UTF-8 value text. A response carrying
`Set-Cookie`, another `SensitiveHeaders` name, `WWW-Authenticate`, or
`Proxy-Authenticate` is not stored. Other headers are omitted. A hit does not
create a new downstream wire response.

## Phase-one runtime behavior

Each client factory owns one Caffeine cache for every selected policy. Caffeine
provides concurrent maximum-size eviction and hard expiry from a monotonic
ticker; wall-clock changes do not extend or shorten an entry. Factory shutdown
invalidates every entry and prevents an already-running load from publishing
after shutdown. Internal aggregate state exposes only policy count, configured
capacity, and current size; entries and opaque keys are not inspectable.

Lookup is cold and repeats for every subscription. Mandatory key, request
variant, and configured `AuthProvider` checks run before a value can be returned.
The per-subscription logical-call timeout, when configured, starts before cache
body preparation and authorization, so those gates cannot make either a hit or
miss exceed the end-to-end budget. Selected Reactor-context variants are frozen
once and that same context snapshot is visible to pre-lookup authorization.
On a hit, the existing HTTP load publisher is not constructed or subscribed, so
the call consumes no downstream resilience permit, redirect, pool acquisition,
or transport dispatch. On a miss, the existing decoded `Mono` pipeline runs and
the pre-resolved auth context is consumed only by its first outer attempt. A
Resilience4j retry resolves current auth again; the filter's one-time 401
invalidation/replay remains inside that attempt. A failure, empty completion,
or cancellation releases the miss token without storing.

Phase one deliberately does not coalesce misses. Concurrent same-key callers
may each dispatch. The first successful completion observed for that key and
generation fills the cache; later duplicate completions still return their own
value to their caller but cannot replace the winner, restart its TTL, or
repopulate after expiry, eviction, or shutdown.

Cached application values are retained and returned by identity. The starter
does not copy or serialize a decoded value solely for caching. Prefer immutable
DTOs; callers that mutate a cached object must copy it on their side. A cached
`ResponseEntity` is rebuilt only to retain the bounded safe header subset; its
body retains the decoded object identity.

Response cacheability is decided from the final wire status and headers for
both plain `Mono<T>` and `Mono<ResponseEntity<T>>` contracts. Redirect responses
are never stored. Responses carrying credential, cookie, authentication
challenge, or another sensitive header are also never stored, even though a
plain-body return type does not expose those headers to application code.

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
A parameter that has only `@CacheKey` and no request-binding annotation must be
selected by the effective policy. Startup rejects that cache-only parameter
when caching is disabled or when `vary-by-parameters` omits its label, because
otherwise the argument would affect neither the request nor the cache key.
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
and request materialization. Caller-created records are retained without
rerunning their canonical constructors; only canonical field accessors and
immutable scalar/record components are accepted. Publishers, streams,
resources, raw containers, unresolved generics, mutable DTOs, and mutable
nested record components fail before transport dispatch.
A top-level query array is supported and
expands to ordered query values. Arrays used as path values or nested inside
query elements are rejected because the current request-target conversion would
serialize them by object identity; format those values as stable scalars
instead. Arrays of containers must use a component type such as `List`, `Set`,
or `Map` that can hold the defensive snapshot. Incompatible concrete or
covariant runtime array components fail before dispatch instead of producing an
array-store error. Path values and nested query elements whose collection, map,
or record type overrides `toString()` are also rejected. Enums that override
`toString()` are rejected for path, nested query, and selected header values:
arbitrary conversion cannot be interrupted at the projection byte limit.
Standard container text and compiler-generated record text are reproduced
structurally under that limit.
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
value. A selected body is serialized once through `ReactiveHttpClientJsonCodec`;
its opaque key and outbound request use those exact bytes, including
`@JsonValue` and application serializer behavior. An absent body has a distinct
key marker from a present zero-length body because body presence can change
effective headers and downstream behavior. Selected header sets preserve their
wire order. Application-defined `List`, `Set`, and `Map` implementations are
rejected when used as selected bodies because replacing them with a defensive
collection snapshot cannot preserve an arbitrary concrete-type codec serializer;
use a JDK collection or an immutable record body. Selected header scalar and
nested-container projections are validated before freezing, so a nested custom
container cannot lose its wire conversion. They are then
materialized under the same cumulative 1 MiB bound before the ordinary request
resolver can call `String.valueOf`. Path and query arguments are always frozen
because they define the request target. A body or dynamic header omitted under
`shared-response: true` is neither cache-key validated nor frozen; the explicit
sharing acknowledgement leaves its ordinary request behavior unchanged. URI
variants retain their non-normalized text, so a literal Unicode path and an
explicitly percent-escaped path remain distinct. These projections prevent
wire-distinct requests from collapsing into one structural key. Canonical
encoding and request-target projection each have a cumulative 1 MiB byte limit.
UTF-8 scalar length, selected String body length, URI text length, and
`BigInteger`/`BigDecimal` encoded magnitude length are checked before encoded
bytes are materialized, so one oversized value cannot bypass the allocation
bound. Cache-selected JSON uses `ReactiveHttpClientJsonCodec.writeBounded(...)`,
which must enforce the 1 MiB limit while encoding. The built-in Jackson 3 codec
writes through a capped buffer; a custom codec must implement the bounded method
or the selected call fails before dispatch. Serialized body bytes are checked
again before defensive key/request copies.
This wire projection is not a fallback for arbitrary
`@CacheKey` or Reactor-context values. Only the SHA-256 digest is retained as
the local opaque key. Raw values and digest text are never exported through
metrics, logs, traces, diagnostics, health, or support bundles. Auth tokens,
credentials, and cookies selected as variants therefore never become ordinary
retained key text.

Effective-contract snapshots include normalized cache isolation policy next to
TTL and maximum size. Parameter and context names are trimmed and sorted;
case-insensitive header names are trimmed, sorted, and rendered lowercase; and
`shared-response` is explicit. Variant names use quoted, escaped list entries so
punctuation cannot make different policies render identically. Approval diffs
therefore expose tenant, locale, header, or sharing-policy drift without
rendering selected values.

### Native context record values

The AOT processor registers record accessors reachable from selected client
method parameters. Supported records must use
canonical field accessors; computed or stateful component accessors are rejected
because their later serialized value cannot be proven equal to the captured key
value. The processor also registers each reachable record class resource for
that validation. A record used only as a
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
        hints.resources().registerPattern(
                SalesRegion.class.getName().replace('.', '/') + ".class");
        hints.reflection().registerType(SalesRegion.class, typeHint -> {});
        var components = SalesRegion.class.getRecordComponents();
        for (var component : components) {
            hints.reflection().registerMethod(
                    component.getAccessor(), ExecutableMode.INVOKE);
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
