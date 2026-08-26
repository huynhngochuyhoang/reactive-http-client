# Reactive HTTP Client - Roadmap V28

> **Status:** active
> **Theme:** explicit semantic-read response caching beyond `GET`
> **Candidate release direction:** `4.1.0`
> **Starting development line:** `4.1.0-SNAPSHOT`
> **Published/API baseline:** `4.0.0`

## Starting State

V27 shipped `4.0.0` and introduced an explicit, bounded local response cache
with TTL, maximum size, deterministic key isolation, single flight,
access-driven refresh, cache observability, and mock/native parity. Caching is
never automatic: a client or method must select a named policy before any
lookup or storage occurs.

The first release deliberately admits only selected `GET` methods. That is a
safe starting default, but it is not a complete application contract. Real
systems use several HTTP styles:

- conventional REST reads normally use `GET`;
- REST-ish APIs sometimes expose complex searches through `POST` bodies;
- RPC-over-HTTP APIs commonly use `POST` for side-effect-free query methods;
- legacy or constrained integrations can use another verb for an operation the
  application owner knows is a semantic read.

HTTP method alone cannot prove whether suppressing a dispatch and reusing an
earlier response is correct. The starter also cannot infer that a `POST`,
`PUT`, `PATCH`, or `DELETE` is read-only from its name, status, body, or traffic
shape. V28 therefore keeps `GET` as the friendly default under an explicitly
selected cache policy and adds a second, method-specific acknowledgement for
every non-`GET` semantic read.

The extension must not turn response caching into a write-deduplication or
idempotency feature. A cache hit intentionally avoids the downstream request;
that is valid only when the application declares that the endpoint has no
required side effect for each invocation.

## Release Direction

| Delivered V28 scope | Release direction |
|---|---|
| Additive method-specific semantic-read opt-in with existing GET behavior unchanged | `4.1.0` |
| Automatic non-GET caching from client-wide policy, method names, or inferred payload shape | No-go |
| Change to existing key, metric, diagnostics, or terminal semantics that breaks 4.0 consumers | Defer or version the contract |
| Write-through caching, mutation suppression, or automatic invalidation | Out of V28 scope |

Keep the reactor on `4.1.0-SNAPSHOT` and public dependency snippets on
published `4.0.0` until release preparation selects and verifies a candidate.

## Core Decision

`GET` is cache-friendly, not cache-automatic. It still requires the V27 client
or method policy selection. A selected non-`GET` method additionally requires a
method-specific declaration that the operation is a semantic read.

The public spelling of that declaration must be frozen before implementation.
The intended shape is one explicit member on `@CacheResponse`, or one equally
reviewable method/API-specific configuration value, whose meaning is:

> The application guarantees that omitting a downstream invocation on a cache
> hit does not omit a required side effect.

A generic client-wide policy cannot make this declaration for all non-`GET`
methods. Client-wide selection that reaches an unacknowledged non-`GET` method
continues to fail startup instead of silently caching or silently skipping that
method.

After this acknowledgement, no hard-coded verb allowlist decides eligibility.
`POST`, `PUT`, `PATCH`, `DELETE`, `OPTIONS`, `HEAD`, and an `@ApiRef`-resolved
method follow the same finite response and request-ownership grammar. A normal
side-effecting write remains ineligible by contract even if its response is
materialized.

## Selection Matrix

| Cache selection | Resolved method | Semantic-read acknowledgement | Result |
|---|---|---|---|
| none | any | any | Cache disabled; normal request path |
| client or method policy | `GET` | not required | Existing V27 eligibility rules |
| client policy only | non-`GET` | absent | Startup failure identifying the method |
| method policy | non-`GET` | absent | Startup failure identifying the missing acknowledgement |
| method/API policy | non-`GET` | present | Eligible after return, body, key, auth, and customization validation |
| any policy | any | `@CacheDisabled` | Cache disabled; normal request path |

## Goals

1. Support explicitly declared semantic reads regardless of their HTTP verb.
2. Preserve the existing explicit-opt-in and GET-friendly behavior for 4.0
   users without broadening client-wide selection silently.
3. Make the exact dispatched request identity, especially a non-GET request
   body, part of cache isolation before any response is stored.
4. Keep cache hits, miss loads, single flight, refresh, auth, resilience,
   redirect, timeout, diagnostics, and shutdown semantics aligned across verbs.
5. Make unsafe configurations fail at startup with the client, method, resolved
   verb, policy, and missing proof, without logging key or body material.
6. Publish practical examples for REST search and RPC query endpoints while
   stating clearly that ordinary writes are not cacheable reads.

## Non-Goals

- Do not enable caching merely because a method is idempotent, retryable, uses
  an idempotency key, returns `200`, or has a name such as `find` or `query`.
- Do not cache side-effecting writes, commands, job submissions, payments,
  mutations, or operations whose invocation itself is significant.
- Do not treat caching as duplicate-write prevention, retry safety, request
  replay safety, or an idempotency-key store.
- Do not infer relationships between cached reads and writes or automatically
  invalidate entries after `POST`, `PUT`, `PATCH`, or `DELETE`.
- Do not implement distributed coherence, persistent caching, HTTP
  `Cache-Control` processing, conditional requests, ETags, write-through, or
  write-behind behavior.
- Do not broaden V27 response eligibility to streaming bodies, `Flux`,
  `DataBuffer`, resources, multipart, application-owned streams, unresolved
  generics, errors, redirects, cancellations, or empty completions.
- Do not retain request bodies, arguments, headers, credentials, tenant values,
  or raw/hashed cache keys in metrics, logs, diagnostics, or support bundles.
- Do not change Resilience4j retry-method eligibility or strict unsafe-retry
  validation because a method is declared cacheable.
- Do not promote benchmark numbers from smoke runs, dirty trees, or local
  unpublished baselines.

## Vocabulary

- **GET-friendly selection:** existing explicit cache selection for a `GET`;
  it does not mean automatic caching.
- **Semantic read:** an application-declared operation for which serving an
  equivalent cached response instead of dispatching is correct and omits no
  required side effect.
- **Non-GET acknowledgement:** the method-specific declaration required in
  addition to selecting a cache policy for a semantic read whose resolved verb
  is not `GET`.
- **Request identity:** the bounded, frozen method, target, selected headers,
  context variants, and body bytes that determine one cache key.
- **Load replay:** any retry, redirect, auth replay, refresh, or resubscription
  that can dispatch the semantic read again. Cacheability alone does not permit
  it.

---

## 1. Post-`4.0.0` Baseline and V28 Scope Integrity

Open V28 on a reproducible minor-development lane before changing cache
eligibility.

**Acceptance:**

- Parent, starter, test-helper, and OTel `4.0.0` artifacts resolve from fresh
  Central-only repositories with remote markers and checksums.
- Root and starter-scoped strict japicmp compare `4.1.0-SNAPSHOT` against
  published `4.0.0`; same-version, mixed-version, and local-candidate baselines
  fail.
- Public dependency snippets remain on `4.0.0`; current consumer and native
  fixtures use `4.1.0-SNAPSHOT` only where reactor artifacts are installed.
- V1-V27 remain immutable completed release records and V28 is the only active
  draft.
- Generated readiness identifies a minor snapshot with no selected release
  candidate until the public opt-in shape is frozen.

## 2. Semantic-Read Opt-In Contract

Define intent independently of HTTP method before relaxing the current `GET`
guard.

**Acceptance:**

- Existing GET policy selection and `@CacheDisabled` precedence remain
  unchanged.
- Every selected non-GET method requires a method/API-specific semantic-read
  acknowledgement in addition to a named cache policy.
- Client-wide policy selection alone never acknowledges all non-GET methods.
- An unacknowledged selected non-GET method fails startup and reports its client,
  Java method, resolved HTTP verb, policy source, and required correction.
- The acknowledgement is accepted for inherited methods and methods whose verb
  comes from `@ApiRef`; effective resolution uses the concrete client binding.
- No annotation, method name, status code, idempotency key, retry configuration,
  or body type is treated as an implicit acknowledgement.
- The final annotation/configuration name, default, precedence, Javadoc, and
  metadata are reviewed as public API before runtime support is added.

## 3. Verb-Independent Declarative Eligibility Grammar

Replace the fixed `GET` check with one effective eligibility decision used by
startup, invocation, diagnostics, AOT, and mocks.

**Acceptance:**

- One effective cache policy resolver returns disabled, GET-friendly selected,
  acknowledged semantic read, or invalid selection with no duplicated verb
  interpretation.
- Once a non-GET semantic read is acknowledged, the existing finite
  `Mono<T>`/`Mono<ResponseEntity<T>>` response grammar applies without a second
  verb allowlist.
- `Mono<Void>`, bodiless envelopes, raw/unresolved responses, nested publishers,
  streams, resources, and retained `DataBuffer` shapes remain rejected.
- Multipart, form-data streams, publishers, resources, `InputStream`, `Reader`,
  channels, and other non-repeatable request bodies remain rejected.
- Method-level policy precedence, client-wide policy, `@CacheDisabled`, overloads,
  inherited generics, and `@ApiRef` produce the same decision on JVM and AOT
  paths.
- A selected side-effecting method cannot claim safety through idempotency or
  retry metadata; documentation treats a false semantic-read declaration as an
  application correctness defect.

## 4. Body-Bearing Request Identity

Make POST/RPC query bodies safe cache dimensions without retaining application
payloads in cache state.

**Acceptance:**

- Every supported body-bearing non-GET semantic read includes the effective
  serialized body representation in request identity by default.
- JSON, `String`, and `byte[]` key material uses the same bounded bytes supplied
  to auth signing and the final WebClient writer. A second serialization cannot
  produce a different key and wire body.
- Effective `Content-Type` and charset that change body bytes are represented in
  the key or make the method ineligible when they cannot be proven before
  lookup.
- Null body, present empty body, empty string, empty JSON value, and absent
  `Content-Type` remain distinct where the outbound requests are distinct.
- The existing canonical byte and element limits apply before large strings,
  numbers, records, containers, or encoded payloads can allocate beyond the
  configured key-material bound.
- Request-body bytes and selected argument snapshots are released after key/load
  preparation and are never retained in entries, metrics, diagnostics, or
  support output.
- Any future option to omit a non-GET body from identity requires a separate,
  explicit sharing acknowledgement and is not inferred from
  `shared-response`; V28 may defer that option rather than weaken isolation.

## 5. Request Target, Headers, Auth, and Tenant Isolation

Preserve V27's variant guarantees for verb-independent semantic reads.

**Acceptance:**

- Concrete client identity, full method signature, resolved target inputs,
  body identity, and configured header/context variants produce one opaque key.
- Dynamic auth, tenant, locale, media type, API version, and application-specific
  response variants must be partitioned or explicitly acknowledged under the
  existing shared-response rules.
- A generated idempotency header cannot be mistaken for an auth/tenant
  partition, and selecting caching does not make an idempotency key optional for
  retry safety.
- Pre-lookup auth and customization gates run for hits and misses with the same
  finalized request facts needed by the provider.
- Customizers, filters, `defaultRequest`, exchange-function replacements,
  codecs, connectors, and response transformations retain their cache-safety
  classification requirement.
- No cache entry is stored when request identity, authorization, or applicable
  customization safety is unknown.

## 6. Local Cache and Response Semantics Across Verbs

Reuse the proven V27 storage path without making HTTP method change value or
header handling.

**Acceptance:**

- Fresh hit, miss, expiry, size eviction, duplicate-miss generation, and factory
  shutdown behavior are identical for GET and acknowledged non-GET semantic
  reads.
- Only a fully decoded, successful, non-null eligible emission can publish an
  entry. Empty completion, error, redirect, cancellation, or rejected response
  remains non-cacheable.
- Plain bodies and `ResponseEntity<T>` apply the same sensitive/non-cacheable
  response-header inspection before storage.
- Cached `ResponseEntity<T>` retains only the existing bounded representation
  header allowlist; per-caller headers are never replayed.
- Cache hits preserve the V27 object-identity contract and do not serialize or
  clone values solely because the method is non-GET.
- A hit performs no transport dispatch. This suppression is the reason the
  semantic-read acknowledgement is mandatory, not an incidental optimization.

## 7. Single Flight and Refresh Composition

Prove that V27's later cache phases remain correct for body-bearing semantic
reads.

**Acceptance:**

- Same-key concurrent POST/RPC queries coalesce only when the selected policy
  enables single flight; different body bytes, targets, variants, clients, and
  methods never join.
- The shared load owns one frozen request representation and one downstream body
  subscription while each caller retains an independent timeout, cancellation,
  and terminal record.
- Access-driven refresh uses the triggering caller's freshly validated request
  identity and body representation. It never stores a body publisher, auth
  token, or caller context for later scheduled replay.
- Refresh for a non-GET semantic read requires the same method acknowledgement
  and finite refresh deadline as its ordinary miss load.
- Eviction, hard expiry, last-waiter cancellation, refresh cancellation, and
  shutdown make stale load generations unable to repopulate entries.
- Deterministic tests cover leader timeout, waiter timeout, body subscription
  count, refresh success/failure, and hard-expiry races without sleep-only
  proofs.

## 8. Retry, Redirect, Auth Replay, and Timeout Boundaries

Keep response reuse separate from transport replay permission.

**Acceptance:**

- A cache hit consumes no retry, CircuitBreaker, Bulkhead, RateLimiter, redirect,
  pool, or transport work after mandatory pre-lookup gates.
- A miss or refresh uses the existing load pipeline and existing operator order;
  the semantic-read acknowledgement does not alter `retry-methods`.
- Strict unsafe-retry validation still requires a startup-provable idempotency
  key when an active Retry can duplicate a non-safe HTTP method.
- Body-preserving redirects and auth replays require the existing repeatability
  proof. Cacheability is not accepted as replayability.
- One-time auth refresh cannot reuse stale pre-resolved credentials on later
  resilience attempts.
- The logical-call deadline covers preparation, auth, lookup, waiting, and load
  without layering an earlier unattributed timeout over response-body evidence.
- Cancellation or timeout before dispatch closes/releases prepared body
  resources and cannot publish an entry.

## 9. Terminal Diagnostics, Metrics, and Support Output

Extend observability by method semantics without exposing request material or
breaking the 4.0 cache schema.

**Acceptance:**

- Existing cache outcome names retain their meanings across verbs; no new
  outcome is invented solely for POST/PUT/PATCH/DELETE.
- Caller lifecycle, observer, exchange log, Micrometer, OTel, and support output
  retain the resolved HTTP method and one terminal record per subscription.
- A hit remains `attemptCount=0` and `requestDispatched=false`; a miss/refresh
  carries only its own final load evidence.
- Diagnostics and effective-contract snapshots export bounded policy source,
  resolved method, and whether non-GET semantic-read intent is acknowledged.
- Existing cache meter names and tag sets remain stable unless an explicit
  versioned schema decision is recorded. API name and ordinary request metrics
  remain the route to per-method analysis.
- Cache metrics stay separately opt-in and cache-served callers remain excluded
  from downstream health denominators.
- Support fixtures contain no body, target, key, digest, header, identity,
  tenant, credential, or cached value.

## 10. Mock, Consumer, AOT, Native, and Lifecycle Parity

Prove the new intent contract outside unit-only policy resolution.

**Acceptance:**

- `MockReactiveHttpClient` accepts the same method-level semantic-read selection
  and rejects the same unacknowledged non-GET contracts as production.
- Mock assertions distinguish cache hit, miss load, waiter, and refresh while
  proving body/key isolation and downstream invocation counts.
- An assembled Boot 4 consumer covers a cached GET, cached POST JSON query,
  unacknowledged POST startup failure, ordinary uncached write, auth partition,
  single flight, and refresh.
- AOT validates inherited and `@ApiRef` semantic-read metadata only for
  starter-backed factory beans and respects replacement metadata caches.
- Runtime hints cover only the final public annotation/configuration additions;
  body DTO hints remain owned by normal application/Jackson reachability.
- Native smoke proves one POST query miss and hit with exactly one loopback
  dispatch and no request body retained in diagnostics.
- Factory and mock close paths cancel active loads/refreshes, invalidate entries,
  remove cache meters, and release prepared body state under the aggregate
  shutdown deadline.

## 11. Security and Operations Review

Make the cost of a false semantic-read declaration explicit to reviewers and
operators.

**Acceptance:**

- Security documentation states that an incorrect declaration can suppress a
  required downstream action or share a response across callers; it is not a
  performance-only mistake.
- Examples use harmless catalog/search/query domains and `.example.invalid`
  endpoints, never payment, account mutation, or job-submission examples.
- Operations guidance distinguishes local response reuse from origin HTTP cache
  semantics and states that `Cache-Control` does not authorize this feature.
- Deployment review includes endpoint ownership approval, request variant
  inventory, auth/tenant partition, body determinism, TTL, capacity, refresh,
  and invalidation responsibility.
- Troubleshooting separates cache suppression from resilience retry, redirect,
  auth replay, proxy/network retry, and downstream duplicate handling.
- No automatic write invalidation is promised; applications needing coherence
  use an application-owned invalidation design or do not select caching.

## 12. Performance and Allocation Re-Audit

Measure the new body-key path without comparing unlike work.

**Acceptance:**

- Disabled-cache and GET-hit/miss rows remain comparable with published `4.0.0`
  and show no new non-GET eligibility work on unselected methods.
- Add no-network and loopback rows for a small POST JSON query miss, hit,
  single-flight waiter, and refresh using one serialized body for key/auth/wire.
- Allocation evidence covers bounded body materialization, opaque key derivation,
  hit lookup, miss publication, waiter attachment, and refresh cleanup.
- Benchmarks verify that body bytes and prepared argument graphs are not retained
  after completion, eviction, expiry, or shutdown.
- Cache-hit comparisons are labeled as local reuse and are not presented as raw
  WebClient network-call overhead comparisons.
- Smoke output remains non-publishable; public performance wording requires a
  clean promoted report paired with the published `4.0.0` baseline.

## 13. Public API, Documentation, and Compatibility Evidence

Freeze the additive contract and make migration from 4.0 explicit.

**Acceptance:**

- Japicmp covers the final `@CacheResponse`/configuration/test-helper additions
  and proves no unreviewed binary or source break against `4.0.0`.
- Adding a defaulted annotation member does not break existing compiled 4.0
  clients; configuration metadata and native hints include every new public
  property/type.
- The cache guide opens with GET-friendly explicit selection, then shows one
  POST JSON search and one RPC query with the semantic-read acknowledgement.
- Documentation states that a client-wide policy does not acknowledge non-GET
  methods and that normal writes must remain disabled/unselected.
- Effective configuration, contract snapshots, diagnostics schema, support
  bundles, and migration examples agree on the final public spelling.
- Current and published assembled consumers prove that applications using only
  4.0 GET caching continue to compile and behave unchanged.
- Dependency, packaging, source/Javadoc, API provenance, AOT, native, benchmark,
  and documentation evidence is reproducible from clean inputs.

## 14. V28 / `4.1.0` Go-No-Go

Release only when verb-independent caching remains an explicit semantic-read
contract rather than an unsafe method-list expansion.

**Acceptance:**

- Select `4.1.0` only if the final API is additive, existing GET behavior is
  unchanged, and every non-GET cache path requires reviewable method intent.
- Root and module strict API checks, dependency matrix, package guard, current
  and published consumers, AOT, native, shutdown, benchmark audit, generated
  docs, and support fixtures pass from a clean commit.
- The changelog says GET remains the friendly default, non-GET caching is an
  explicit semantic-read declaration, and caching never proves idempotency or
  write safety.
- Release notes make no claim that all POST/PUT/PATCH/DELETE endpoints are
  cacheable and contain no side-effecting example.
- If request identity cannot be made wire-equivalent and bounded for supported
  body-bearing methods, record a no-go or narrow V28 to proven shapes rather
  than weakening key isolation.
- After publication, verify all Central artifacts and an assembled `4.1.0`
  consumer before moving public coordinates, API/benchmark baselines, or closing
  V28.
