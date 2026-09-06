# Response Caching

Published `4.0.0` introduced an explicit local response-cache contract in four
phases. Phase one provides bounded process-local TTL storage after policy
selection, startup eligibility, and key isolation have succeeded. Phase two
adds separately opt-in request coalescing, phase three adds separately opt-in
refresh on access, and phase four adds separately opt-in bounded metrics and
terminal cache outcomes. Existing explicit `GET` selection remains the
cache-friendly path; `GET` is not cached automatically. The `4.1.x` line also
permits one specific non-`GET` method to opt in as a semantic read.

The optional decoded-response representation-byte bound is an unpublished
`4.2.0`/V29 release-candidate feature and is not available in published `4.1.x`.
`maximum-size` continues to count entries. The optional byte value counts the
decoded response representation retained by the cache; it is not exact Java
heap, direct memory, process RSS, or container memory.

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
              maximum-total-decoded-response-bytes: 268435456
              single-flight: true
              refresh-after-ms: 30000
              refresh-timeout-ms: 5000
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
2. `@CacheResponse("name")` selects that policy and carries any method-specific
   semantic-read acknowledgement.
3. A non-blank client `cache.policy` applies.
4. Otherwise caching is disabled.

`GET` remains the cache-friendly default. A selected non-`GET` method must
declare `@CacheResponse(value = "name", semanticRead = true)` on that exact
method. Client-wide policy selection cannot acknowledge all non-`GET` methods;
the annotation also takes precedence over the client policy. The same spelling
applies to a non-`GET` method whose verb and path come from `@ApiRef`.

The acknowledgement is an application guarantee that serving a cache hit may
suppress downstream dispatch without omitting a required side effect. It is
not inferred from an idempotency key, a retry annotation, the HTTP method name,
or response status, and it does not provide write invalidation or replay safety.
Its default is `false`, preserving the published `4.0.0` GET-only behavior for
existing declarations.

A body-bearing semantic non-`GET` read must annotate its `@Body` parameter with
`@CacheKey` and select that label through `vary-by-parameters`. The resulting
key dimension is derived from the exact prepared wire bytes. `shared-response`
cannot waive this requirement. For `@ApiRef`, the configured method must first
resolve to a nonblank supported verb; semantic intent does not repair missing
API configuration.

The same method annotations work on inherited endpoints, overloads, and
`@ApiRef` methods. Policy names are trimmed and must not be blank. A selected
policy must exist and must define:

- `ttl-ms`: `1` through `31536000000` (365 days).
- `maximum-size`: `1` through `1000000` entries.
- Optional `maximum-total-decoded-response-bytes`: `1` through
  `1099511627776` (1 TiB) decoded response representation bytes retained
  across one policy cache. The count is taken after transport decompression and
  includes only response-header names and values retained in a cached
  `ResponseEntity`; it is not Java heap, direct memory, RSS, or container memory.
  The final unary decoder stream is counted without copying or reserializing the
  value. A successful response whose count is unknown or exceeds the policy
  limit is returned to the caller but is not stored. Admissible publication and
  refresh replacement evict entries from the same policy as needed and keep the
  retained representation-byte total at or below the configured limit. Policies
  that omit this setting retain the existing TTL plus entry-count behavior and
  do not activate response-byte measurement.

Refresh remains disabled when both refresh settings are absent. Selecting it requires
`refresh-after-ms` to be positive and strictly below `ttl-ms`, plus a
positive `refresh-timeout-ms` no greater than 365 days. Supplying only one
refresh setting fails startup.

Missing, zero, negative, or larger values fail startup and identify the
concrete client, Java method, policy source, and `@ApiRef` key when applicable.

### Semantic-read examples

A JSON search must key the prepared body bytes and every response-changing
variant. This example partitions by criteria, tenant scope, and the effective
idempotency header:

```yaml
reactive:
  http:
    clients:
      catalog-search:
        base-url: https://catalog-search.example.invalid
        cache:
          policies:
            catalog-search:
              ttl-ms: 30000
              maximum-size: 1000
              vary-by-parameters: [criteria]
              vary-by-headers: [Idempotency-Key, X-Tenant-Scope]
```

```java
@POST("/catalog/search")
@CacheResponse(value = "catalog-search", semanticRead = true)
Mono<SearchResult> search(
        @HeaderParam("X-Tenant-Scope") String tenantScope,
        @Body @CacheKey("criteria") SearchCriteria criteria);
```

An RPC-style query follows the same contract. Here `@ApiRef` supplies the
resolved `POST` verb and target, while the body, effective idempotency header,
and principal scope define the response partition:

```yaml
reactive:
  http:
    clients:
      reporting-rpc:
        base-url: https://reporting-rpc.example.invalid
        apis:
          report-query:
            method: POST
            path: /rpc/query
        cache:
          policies:
            reporting-query:
              ttl-ms: 15000
              maximum-size: 500
              vary-by-parameters: [criteria]
              vary-by-headers: [Idempotency-Key]
              vary-by-context: [principalScope]
```

```java
@ApiRef("report-query")
@CacheResponse(value = "reporting-query", semanticRead = true)
Mono<ReportResult> query(
        @Body @CacheKey("criteria") ReportCriteria criteria);
```

These snippets show request identity only. A cache-enabled application must
also add Caffeine and classify every applicable WebClient customization as
described below. The complete copyable configurations, auth placeholders, and
observability prerequisites are in the
[effective configuration examples](examples/effective-configuration.md#semantic-read-cache-examples).

## Initial eligibility

A resolved `GET`, or an explicitly acknowledged semantic read, is eligible only
when it returns a finite `Mono<T>` or `Mono<ResponseEntity<T>>`. Startup rejects
selected caching for:

- `Flux`, raw `Mono`, `Mono<Void>`, and streaming response envelopes;
- `Publisher`, `DataBuffer`, `Resource`, unresolved wildcard/type-variable,
  raw generic, and unknown `Object` response values;
- multipart requests and request bodies owned as a publisher, data buffer,
  resource, input stream, reader, or readable byte channel;
- every resolved non-`GET` method without method-specific
  `@CacheResponse(..., semanticRead = true)`, including a non-`GET` `@ApiRef`.

An acknowledged method still passes every existing response, request-body,
key, auth, and customization-safety check. The acknowledgement does not make a
streaming, unresolved, multipart, or application-owned shape cacheable.

Only a final successful, non-null decoded emission is a cache candidate. Empty
completion, cancellation, redirect, auth/decode/
transport/resilience failure, and mapped 4xx/5xx failure never create an entry.

A cached `ResponseEntity` represents the previously materialized application
value and status. Hits retain only `Content-Type`, `Content-Language`,
`Content-Encoding`, `ETag`, `Last-Modified`, `Cache-Control`, `Expires`, and
`Vary`, capped at 32 values and 16 KiB of UTF-8 value text. A response carrying
`Set-Cookie`, another `SensitiveHeaders` name, `WWW-Authenticate`,
`Proxy-Authenticate`, or a name selected through
`non-cacheable-response-headers` is not stored. Other headers are omitted. The
load caller sees the original response headers; a later hit sees only the retained
representation allowlist and does not create a new downstream wire response.

## Application safety review

`semanticRead = true` is an application correctness and data-isolation
decision, not an HTTP optimization hint. Before a non-`GET` method is selected,
the endpoint owner must approve the declaration. A false declaration can
suppress a required action on a hit or share one caller's response with another
caller when an identity dimension is missing.

Record approval for every row:

| Review | Required proof |
|---|---|
| Side effects | Omitting a downstream invocation on every hit does not omit a payment, mutation, audit action, job submission, lock, sequence advance, or other required action. |
| Body determinism | The selected body bytes completely identify the query and serialization does not depend on unkeyed mutable or ambient state. |
| Response variants | Path, query, body, media type, locale, API version, and every response-changing input are represented by the final request identity. |
| Auth and tenant partition | Principal, authorization scope, and tenant boundaries are selected explicitly, or sharing is deliberately approved for public data. |
| TTL and hard expiry | The finite TTL is no longer than the business staleness budget, including during a downstream incident. |
| Refresh | Hidden refresh traffic, credentials, Retry behavior, and refresh failure are acceptable; otherwise refresh remains disabled. |
| Invalidation owner | The endpoint owner accepts TTL/eviction as the bound or names the application-owned invalidation/coherence mechanism used after writes. |

No transport or response metadata substitutes for that approval. Idempotency does
not authorize local response reuse. Retry configuration does not authorize local
response reuse. A successful HTTP status does not authorize local response reuse.
A method named `find` or `query` does not authorize local response reuse.
`Cache-Control` does not authorize local response reuse. None can prove that
suppressing a call is safe or that two callers may share one decoded value.

Ordinary writes, payments, job submissions, commands, and mutations stay
unselected. When a client-wide policy could otherwise select them, exclude each
method explicitly:

```java
@ReactiveHttpClient(
        name = "command-api",
        baseUrl = "https://commands.example.invalid")
interface CommandClient {

    @POST("/payments")
    @CacheDisabled
    Mono<PaymentReceipt> submitPayment(@Body PaymentCommand command);

    @POST("/jobs")
    @CacheDisabled
    Mono<JobReceipt> submitJob(@Body JobCommand command);

    @PUT("/customers/{id}")
    @CacheDisabled
    Mono<Customer> updateCustomer(
            @PathVar("id") String id,
            @Body CustomerUpdate update);

    @POST("/commands")
    @CacheDisabled
    Mono<CommandReceipt> executeCommand(@Body Command command);
}
```

Do not add `semanticRead = true` merely to make one of these methods pass cache
validation. Leave it unselected or disabled unless the endpoint owner can prove
that it is a read under the complete review above.

## Phase-one runtime behavior

Each client factory owns one Caffeine cache for every selected policy. Caffeine
provides concurrent maximum-size eviction and hard expiry from a monotonic
ticker; wall-clock changes do not extend or shorten an entry. Factory shutdown
invalidates every entry and prevents an already-running load from publishing
after shutdown. Internal aggregate state tracks policy count, configured entry capacity,
current size, and, only for policies that select it, the bounded retained decoded-response
byte total. Entries, opaque keys, and cached values are not inspectable.

Lookup is cold and repeats for every subscription. Mandatory key, request
variant, and configured `AuthProvider` checks run before a value can be returned.
The per-subscription logical-call timeout, when configured, starts before cache
body preparation and authorization, so those gates cannot make either a hit or
miss exceed the end-to-end budget. One absolute deadline is handed to the miss
pipeline's existing subscription-reporting state, so a timeout after response
headers retains response-body attribution instead of being reported as an outer
cache cancellation. Selected Reactor-context variants are frozen once and that
same context snapshot is visible to pre-lookup authorization.

Configured pre-lookup auth traverses the same WebClient `defaultRequest` and
filter chain as a real request, including Boot-provided, correlation/trace, and
post-auth SAFE customizer mutations. `OutboundAuthFilter` resolves and validates
the auth context, then forwards the authorized request to the terminal
non-dispatching probe. The effective method, URI, and selected headers are thus
captured after all request filters without calling the exchange function or
transport. A miss reuses that validated context only for its first outer attempt;
later resilience attempts resolve current auth normally.

Without configured auth, a terminal non-dispatching probe runs the same
`defaultRequest` and filter chain before lookup. The effective method, URI, and
selected headers are therefore captured after request customization without
calling the exchange function or transport.

The starter factory and `MockReactiveHttpClient` supply the configured provider
and resolved base URL to this pre-lookup gate. Low-level callers using
`ReactiveClientInvocationHandler.create(...)` must use its provider-aware
overload for authenticated cached clients. Legacy constructors and the shorter
factory overload fail closed when a method selects caching, instead of allowing
an authenticated first miss followed by unauthorized hits.

On a hit, the existing HTTP load publisher is not constructed or subscribed, so
the call consumes no downstream resilience permit, redirect, pool acquisition,
or transport dispatch. On a miss, the existing decoded `Mono` pipeline runs and
the pre-resolved auth context is consumed only by its first outer attempt. A
Resilience4j retry resolves current auth again; the filter's one-time 401
invalidation/replay remains inside that attempt. A failure, empty completion,
or cancellation releases the miss token without storing.
If a retry or `401` replay changes any method, target, or selected-header fact
that contributed to the lookup key, the successful response is returned to its
caller but is not published under the stale identity.

Phase one deliberately does not coalesce misses. Concurrent same-key callers
may each dispatch. The first successful completion observed for that key and
generation fills the cache; later duplicate completions still return their own
value to their caller but cannot replace the winner, restart its TTL, or
repopulate after expiry, eviction, or shutdown. Publication forces Caffeine to
process expiry before rechecking the load token's generation.

## Local-only consistency and invalidation

The cache belongs to one reactive client factory in one application process. It
does not coordinate entries between application instances, pods, regions, or
deployments. After a rollout or downstream change, per-instance divergence is
expected until each local entry expires, is evicted, is refreshed through
access, or its factory shuts down.

The starter does not invalidate related cached reads after a write. An unselected
or explicitly disabled `POST`, `PUT`, `PATCH`, or `DELETE` follows its normal
request path without searching for related read keys or broadcasting invalidation.
A selected non-`GET` method fails proxy construction unless that exact method is
declared as a semantic read. Use `@CacheDisabled` when a client-level policy
would otherwise select a command. A false semantic-read declaration is an
application correctness defect: a hit can intentionally suppress the downstream
call. Choose a TTL that bounds acceptable staleness, and use an application-owned
invalidation/coherence system when writes must be visible sooner.

This feature is not a distributed-cache abstraction. It provides no shared
storage, cross-instance single flight, distributed locks, write-through policy,
event-bus invalidation, or cluster-wide refresh. Cached decoded objects are also
returned by identity within the process; callers must treat them as immutable or
copy them before mutation.

## Phase-two request coalescing

Set `single-flight: true` on a selected policy to coalesce concurrent misses for
the same opaque isolated key. The default is `false`; selecting caching does not
silently enable coalescing. Keys from another method, policy, client, or request
variant remain independent.

Every caller still runs its mandatory key, context, and authorization gates.
After those gates report the same miss key, one leader owns the downstream load
and waiters share its final decoded signal. Resilience4j retry, one-time `401`
auth invalidation/replay, redirects, request-body insertion, pool acquisition,
and transport dispatch therefore occur only inside the leader load, not once per
waiter. Cache recheck and waiter reservation are one atomic per-key transition:
a caller delayed after an earlier miss observes a value filled in the meantime,
and a reserved waiter keeps its flight alive before attaching to the result.
Completed or abandoned flight publishers cannot reconnect and start an
untracked second load.

Logical-call deadlines remain subscription-local. A waiter timing out or being
cancelled detaches only that caller while another interested caller keeps the
load alive. The first caller's timeout likewise cannot truncate a later
waiter's budget. Request/attempt timeouts remain inside the leader pipeline. If
the final interested caller leaves, the leader is cancelled and its abandoned
result cannot populate the cache.

Transport attempts use a flight-owned reporting state rather than the first
caller's terminal state. If that caller detaches, its attempt evidence is frozen
and a surviving waiter becomes the diagnostic owner for later retries. A retry
therefore cannot mutate an already-terminal caller, while the final attempt is
still represented by one active caller's terminal record.

Success, failure, and empty completion are fanned out to current callers, then
the in-flight state is removed. A later caller can load again after failure,
empty completion, cancellation, or shutdown. Coordination is per cache and key;
a slow or failed load does not execute under a global load lock or block another
key. Cache-specific metrics remain disabled unless cache observability is explicitly selected.

## Phase-three refresh on access

Set both `refresh-after-ms` and `refresh-timeout-ms` on a selected policy to
refresh an aging entry when it is accessed. Before `refresh-after-ms`, an access is
a normal fresh hit. From that threshold until hard `ttl-ms` expiry, the access
returns the current value and starts at most one hidden refresh for that cache key.
Concurrent stale accesses keep receiving the current value and do not join or start
another refresh. Miss single flight and refresh ownership use separate state.

The triggering subscription supplies the already validated frozen arguments, key
variants, Reactor context, and pre-lookup authorization result. The refresh bypasses
only recursive cache lookup; its transport work uses the normal auth, Resilience4j,
redirect, request/response timeout, body, and decoding pipeline. No scheduler invents
a request or retains the invocation that originally populated the entry.

Refresh success generation-checks and atomically replaces the exact triggering entry,
then restarts its age. Failure is hidden from the stale caller and leaves the current
value available only until its original hard expiry. Each refresh is cancelled at the
earliest of `refresh-timeout-ms`, the entry hard-expiry deadline, or factory
shutdown. Expiry, size eviction, replacement, and shutdown invalidate its publication
token, so cancellation or a late signal cannot revive an old entry. A post-expiry
caller follows the ordinary miss and optional miss-single-flight path.

Refresh uses Reactor shared scheduling only for its finite timeout; the cache manager
owns no scheduler or thread. Factory shutdown disposes active refresh subscriptions,
clears refresh/cache state, and releases the captured key, context, auth, and invocation
references.

Cached application values are retained and returned by identity. The starter
does not copy or serialize a decoded value solely for caching. Prefer immutable
DTOs; callers that mutate a cached object must copy it on their side. A cached
`ResponseEntity` is rebuilt only to retain the bounded safe header subset; its
body retains the decoded object identity.

## Phase-four observability

Cache telemetry is independent from cache selection and defaults off. Enable it
only under the existing global observability gate:

```yaml
reactive:
  http:
    observability:
      enabled: true
      cache:
        enabled: true
```

This setting does not select a cache policy and does not enable Caffeine stats.
When no method selects caching, no cache meter is registered. Caller terminal
records use only `FRESH_HIT`, `MISS_LOADER`, `COALESCED_WAITER`, or `STALE_HIT`.
A hit, waiter, or stale return has zero transport attempts, no status, URL,
server, failure stage, or wire-size evidence. The miss loader retains the final
HTTP attempt facts. If that leader detaches while a waiter remains, the waiter
does not become a synthetic transport owner and remains transport-empty.

Only miss leaders enter the ordinary Micrometer downstream request timer.
Fresh hits, stale hits, and coalesced waiters still reach lifecycle hooks,
exchange logs, custom observers, and OpenTelemetry, but they cannot dilute the
health indicator's downstream error-rate denominator.

Hidden refresh does not create an OpenTelemetry span or lifecycle terminal
record. Its success, failure, cancellation, and duration are represented by
bounded cache meters and a metadata-only debug log containing client, API, and
outcome. Cache keys, selected values, arguments, headers, bodies, URLs, tenant
values, and credentials are never meter tags or cache outcome fields. See
[Observability](08-observability.md#response-cache-metrics-separately-opt-in)
for the complete meter and dashboard contract. Meter names, types, base units,
tag keys, and zero-series behavior are verb-independent; the resolved HTTP method
is intentionally not added as a cache-meter tag.

Weighted policies additionally expose current and maximum decoded response
representation-byte gauges plus one bounded admission outcome counter. These
signals use only client, policy, and fixed outcome tags. Occupancy gauges are
current state; lookup/load/refresh/admission/eviction counters are terminal event
history. They are absent unless cache observability and the policy byte bound are
both selected, and they do not change downstream health.

Provider-backed diagnostics and effective-contract snapshots expose bounded cache
policy source, resolved HTTP method, and semantic-read acknowledgement. Collection
snapshot overloads and replacement client factories render provider-only semantic
facts as `null`/unknown rather than inventing `false`. None of these outputs
contain policy names, request targets, selected headers, bodies, keys, tenants, or
identities. Provider diagnostics also expose current retained representation
bytes only when an existing manager can prove the aggregate; lazy/uncreated and
partly unweighted state remains `null` without creating or traversing a cache.

Response cacheability is decided from the final wire status and headers for
both plain `Mono<T>` and `Mono<ResponseEntity<T>>` contracts. Redirect responses
are never stored. Responses carrying credential, cookie, authentication
challenge, another sensitive header, or a configured per-caller response header
are also never stored, even though a plain-body return type does not expose those
headers to application code. Header names are case-insensitive, limited to 32
valid names, and exported in normalized effective-contract snapshots.

## Feature-composition boundary

Each subscription freezes its arguments and selected context, constructs the
opaque key, and passes mandatory policy and authorization gates before lookup.
Only then can a hit return. A hit does not subscribe the resilience, redirect,
pool, transport, or decode pipeline. A miss leader and an access-driven refresh
run that existing pipeline unchanged; storage occurs only after its final
successful decoded value passes status and response-header checks.

With single flight, every caller owns its logical-call deadline and terminal
reporting state. Waiters may follow the leader load evidence while interested,
but timeout or cancellation freezes only that caller. A hit, auth rejection, or
open-circuit rejection has no URL, response status, response headers, transport
failure stage, or attempt evidence from a prior load or refresh. Retry exhaustion
retains only its final attempt facts, and a failed hidden refresh does not rewrite
the stale caller result.

Caching remains a read optimization. Selected non-`GET` methods fail startup
without method-specific semantic-read intent even when an idempotency key is
present. An unselected or disabled write still dispatches normally, does not
invalidate cached reads, and is never suppressed by a cached result.

## Key and variant isolation

Every key includes the logical and concrete client identity, the full
generic-resolved method signature, the effective HTTP method, and the finalized
request URI. The URI is captured after path/query resolution and pre-lookup
auth, so authority, path, encoded query values, and query ordering remain part
of the opaque identity. Additional values are explicit:

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
              non-cacheable-response-headers: [X-Caller-Session, X-Identity]
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

Selected headers are read from the finalized pre-lookup request rather than
from the declarative argument map. Values added or replaced by `defaultRequest`,
upstream filters, correlation propagation, or the configured `AuthProvider`
therefore partition the key when their header name is selected. Auth still runs
for every hit and miss, and invalid auth header names or values fail before
lookup under the same rules as ordinary dispatch.

For an authenticated method with no explicit parameter/header/context
partition, set `shared-response: true` only when the response is deliberately
shared across identities. The effective idempotency header alone is not an
authenticated identity partition because it may be absent and is required for
every non-shared policy. Select at least one additional stable parameter,
header, or context dimension, or explicitly acknowledge `shared-response`.
The same acknowledgement is required when dynamic headers, header maps, or an
eligible `GET` body are intentionally omitted. A body-bearing semantic
non-`GET` read must always select its body through `vary-by-parameters`. The
acknowledgement cannot remove explicitly selected variants; those dimensions
still partition the response.

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
`@JsonValue` and application serializer behavior. Auth providers receive a
per-resolution defensive copy of the serialized bytes, so provider mutation
cannot change the writer bytes or cache identity. An absent body has a distinct
key marker from a present zero-length body because body presence can change
effective headers and downstream behavior. The body frame also includes the
normalized effective `Content-Type` and charset. An auth provider may repeat
that prepared media type, including with an equivalent parameter order, but
cannot replace it after body identity is fixed. The same validation applies to
credentials resolved after a `401`, before the replay is dispatched. It is not
installed for bodiless or unselected-body cache calls. Selected header sets
preserve their wire order. Application-defined `List`, `Set`, and `Map`
implementations are rejected when used as selected bodies because replacing
them with a defensive collection snapshot cannot preserve an arbitrary
concrete-type codec serializer;
use a JDK collection or an immutable record body. Selected header scalar and
nested-container projections are validated before freezing, so a nested custom
container cannot lose its wire conversion. They are then
materialized under the same cumulative 1 MiB bound before the ordinary request
resolver can call `String.valueOf`. Path and query arguments are always frozen
because they define the request target. An eligible `GET` body or dynamic
header omitted under `shared-response: true` is neither cache-key validated nor
frozen; the explicit sharing acknowledgement leaves its ordinary request
behavior unchanged. Semantic non-`GET` bodies cannot use this waiver. URI
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

Effective-contract snapshots include normalized cache isolation policy and the
`singleFlight` decision next to TTL and maximum size. Parameter and context
names are trimmed and sorted;
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

Inventory includes ancestor contexts and replacement builders without relying
on bean creation order. An already-created per-client customizer is filtered by
`supports(clientName)`; an uninitialized lazy or factory-backed customizer is
treated conservatively as applicable and must be classified. Startup, AOT, and
diagnostics perform this inventory without creating the lazy bean.

The cache runtime must execute mandatory authorization, tenant, and policy
checks at a cache-aware pre-lookup boundary for both hits and misses. Until a
customization is represented by that gate or by the key/variant contract, mark
it `INCOMPATIBLE`; classification is not a bypass mechanism.

## Contract evidence

Proxy startup, AOT processing, effective-contract export, diagnostics, and
`MockReactiveHttpClient` use the same `MethodMetadataCache`-backed grammar.
Replacement metadata caches remain authoritative. Contract snapshots expose a
bounded `Cache` cell with source, `semanticRead`, TTL, maximum size, the
optional aggregate decoded-response-byte limit, normalized variants, shared-response acknowledgement, the single-flight decision, and
bounded refresh threshold/timeout values. Policy names,
raw values, and opaque key digests are not exported.

## Compatibility and related contracts

`CacheResponse.semanticRead()` defaults to `false`, so existing compiled and
source `4.0.0` cache clients retain their explicit `GET` behavior on `4.1.x`.
The new member is method-scoped; no client-wide property can opt every non-GET
method into caching. See
[Native Image and Release Compatibility](20-native-release-compatibility.md#documented-public-surface-map)
for the compatibility-covered annotation, metadata, property, and test-helper
surface.

Review the interacting contracts before enabling a policy:

- [Annotations](02-annotations.md#response-cache-selection) for selection and
  startup grammar.
- [Resilience4j](07-resilience4j.md) for Retry and idempotency eligibility.
- [Outbound Auth Providers](06-auth-providers.md) for per-call authorization
  and `401` invalidation/replay.
- [Redirect Responses](03-error-handling.md#redirect-responses) and
  [Timeouts](04-timeouts.md) for miss-loader composition.
- [Observability](08-observability.md#response-cache-metrics-separately-opt-in)
  and [Diagnostic Context Contracts](21-diagnostic-contexts.md) for bounded
  terminal and support fields.
- [Production Checklist](16-production-checklist.md),
  [Operations Troubleshooting](30-operations-troubleshooting.md), and
  [Support Bundles](26-support-bundles.md#response-cache-incidents) for rollout
  and incident handling.
- [Spring Boot 4 and Starter 4.x Migration](28-spring-boot-4-jackson-migration.md)
  and [3.x to 4.x Resilience Migration](31-3x-to-4x-resilience-migration.md) for
  the supported runtime and explicit operator baseline.
