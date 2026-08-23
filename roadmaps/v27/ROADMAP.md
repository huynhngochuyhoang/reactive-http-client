# Reactive HTTP Client - Roadmap V27

> **Status:** active
> **Theme:** fail-safe feature activation and explicit bounded response reuse
> **Target release:** `4.0.0` (SemVer-major candidate)
> **Starting development line:** `3.7.0-SNAPSHOT`
> **Published/API baseline:** `3.6.0`

## Starting State

V26 shipped `3.6.0` and moved the reactor to `3.7.0-SNAPSHOT`. The parent,
starter, test-helper, and OTel artifacts plus an assembled Boot 4 consumer were
verified from Maven Central. Public dependency examples and API, consumer, and
benchmark baselines now use `3.6.0`.

The transport, declarative, replay, diagnostics, and observability contracts are
mature. Two explicit-policy gaps remain:

- Resilience activation is not fail-safe. Once a client sets
  `resilience.enabled=true`, the current default instance names can activate
  Retry, CircuitBreaker, Bulkhead, and RateLimiter even when the user intended
  to select only one operator. The deferred
  [opt-in resilience proposal](../proposals/OPT_IN_RESILIENCE_ACTIVATION.md)
  correctly classifies changing this behavior as SemVer-major.
- The starter offers no response reuse for read-heavy, slowly changing data.
  Applications must build caching outside the declarative contract, where cache
  keys, replay composition, diagnostics, mocks, and lifecycle behavior can drift
  from the actual client. The starter must not infer which response is safe to
  cache. Caching must be a local, bounded, explicit opt-in at client or method
  scope.

V27 adopts the resilience proposal and introduces response caching in four
ordered phases:

1. bounded local caching with a required TTL, required maximum size, and
   explicit hit/miss outcomes;
2. request coalescing (single flight) for concurrent misses of the same key;
3. refresh-on-access with one refresh flight and a hard expiry boundary;
4. cache metrics and observability aligned across caller-visible and hidden
   cache work.

Each phase must preserve the established subscription, dispatch, timeout,
resilience, auth, redirect, body-ownership, and terminal-reporting contracts.
No phase may turn the starter into an HTTP caching proxy or guess that an
endpoint is cacheable.

## Release Direction

| Delivered V27 scope | Release direction |
|---|---|
| Opt-in cache additions without resilience behavior change | A future `3.x` minor is technically possible, but not the selected V27 lane |
| Explicit resilience activation replacing implicit `default` selections | `4.0.0` |
| Removal or redesign of unrelated public APIs | Out of scope; defer to another major proposal |

V27 targets `4.0.0`. Keep public consumer examples on published `3.6.0` until
release preparation verifies the major candidate. Move the reactor from
`3.7.0-SNAPSHOT` to `4.0.0-SNAPSHOT` only when the baseline guard, an initial
source-controlled migration report, and the release lane are updated together.

## Goals

1. Make every resilience operator inactive unless the user explicitly selects
   that operator for the client or method.
2. Add a local response cache only where a user explicitly opts in and supplies
   finite TTL and capacity bounds.
3. Define cache key isolation before storing any response so authenticated,
   tenant-aware, locale-aware, or header-varying calls cannot silently share
   entries.
4. Add single-flight, refresh, and observability as separate, testable phases
   rather than hidden consequences of enabling the basic cache.
5. Keep cache hits, misses, coalesced waiters, loads, refreshes, evictions, and
   failures observable without exporting keys, arguments, headers, bodies, or
   credentials.
6. Preserve one terminal record per caller subscription and make hidden cache
   work distinguishable from caller-visible logical calls.
7. Publish a complete `3.x` to `4.0.0` migration guide with API, configuration,
   consumer, AOT, native, and benchmark evidence.

## Non-Goals

- Do not infer cacheability from `GET`, status codes, response headers, method
  names, DTO types, traffic frequency, or Resilience4j configuration.
- Do not implement a distributed cache, cross-instance invalidation, persistent
  cache, HTTP shared-cache proxy, RFC 9111 cache-control engine, or write-through
  cache.
- Do not automatically invalidate cached reads after `POST`, `PUT`, `PATCH`, or
  `DELETE`; the starter cannot prove which writes affect which keys.
- Do not cache errors, cancellations, empty completions, redirects, streaming
  responses, publishers, `DataBuffer`, resources, multipart bodies, or
  caller-owned streams in V27.
- Do not serialize and deserialize values only to make them cacheable. Cached
  object identity and mutability must be documented.
- Do not retain raw cache keys, method arguments, Reactor context values,
  authorization material, request/response bodies, or arbitrary headers in
  metrics, logs, diagnostics, or support bundles.
- Do not add periodic background refresh that invents a request without a live
  caller context. V27 refresh is triggered by access.
- Do not change the established Resilience4j operator ordering, retry method
  eligibility, idempotency rules, or strict-validation meaning beyond explicit
  operator activation.
- Do not promote benchmark numbers from smoke runs, dirty commits, or local
  unpublished baselines.

## Cache Vocabulary

- **Policy:** explicit client- or method-level cache configuration containing at
  least TTL and maximum size.
- **Key:** an opaque, deterministic identity for one concrete client method and
  its declared response-varying inputs. Key material is never telemetry.
- **Hit:** an unexpired value satisfies one caller without a downstream load.
- **Miss:** no usable value exists when a caller performs lookup.
- **Load:** the existing client pipeline used to obtain a value after a miss.
- **Coalesced waiter:** a caller that observes a miss but joins an in-flight load
  for the same key instead of starting another load.
- **Refresh:** one access-triggered background load that replaces an eligible
  value while callers continue to receive the current value until hard expiry.
- **Hard expiry:** the TTL boundary after which a value cannot be served, even
  when refresh has failed or is still running.

---

## 1. Post-`3.6.0` Baseline and Major-Lane Integrity

Open V27 without weakening the evidence retained by the completed V26 release.

**Acceptance:**

- Parent, starter, test-helper, and OTel `3.6.0` artifacts resolve from fresh
  Central-only repositories with remote markers and checksums.
- Root and module-scoped japicmp compare the current reactor against published
  `3.6.0`; same-version, mixed-version, and locally contaminated baselines fail.
- The major report is report-only only for reviewed V27 changes. Unrelated API
  breaks remain release blockers.
- Public dependency snippets stay on published `3.6.0`; reactor-only consumer
  and native fixtures follow the selected snapshot line.
- Generated readiness evidence identifies V27 as a major candidate and does not
  advertise `4.0.0` as published before Central verification.

## 2. Explicit Resilience Activation Contract

Adopt the deferred resilience proposal as the first behavior contract of V27.

**Acceptance:**

- `resilience.enabled` remains the client-level master gate but selects no
  operator by itself.
- Client-level Retry, CircuitBreaker, Bulkhead, and RateLimiter selections
  default to absent. A non-blank instance name, including explicit `default`,
  activates only that operator.
- A method-level resilience annotation explicitly selects its matching operator
  while still requiring the master gate.
- Method-level selection has precedence over client-level selection. Blank
  values do not fall back to implicit `default`.
- `retry-methods` controls Retry eligibility only; it does not activate Retry.
- Strict unsafe-retry validation is dormant when Retry is not selected, is
  unavailable, or cannot make another attempt.
- `enabled: true` alone applies no operator for `Mono` and `Flux`; focused tests
  prove each single-operator and multi-operator selection independently.
- Existing operator order is frozen by composition tests and is not changed as
  a side effect of activation cleanup.

## 3. One Effective Resilience Policy Everywhere

Prevent startup, invocation, diagnostics, and test helpers from interpreting
the new activation model differently.

**Acceptance:**

- One package-private effective-policy resolver supplies invocation, startup
  validation/logging, contract export, diagnostics, and mocks.
- Diagnostics distinguish disabled, selected-but-unavailable, active, and
  unprovable lazy-registry states without creating lazy registries or
  `FactoryBean` products.
- Startup summaries never print an operator instance when the operator is not
  selected or cannot be applied.
- `MockReactiveHttpClient` reproduces explicit selection, operator order, strict
  retry validation, attempts, and terminal diagnostics.
- Configuration metadata and generated examples contain no implicit
  all-operators-on defaults.
- A migration matrix covers enabled-only, explicit `default`, named instances,
  method annotations, blank values, retry methods, unavailable registries, and
  strict validation.

## 4. Cache Opt-In and Declarative Eligibility Grammar

Freeze what it means to select caching before building storage or concurrency
behavior.

**Acceptance:**

- Caching is disabled when no client or method explicitly selects a cache
  policy. Merely declaring cache defaults or adding the cache library activates
  nothing.
- The public configuration supports explicit client-level and method-level
  opt-in with documented precedence and an explicit method exclusion when a
  client-wide policy is selected.
- Every selected policy requires a positive TTL and positive maximum size.
  Missing, zero, negative, overflow, and impractical values fail startup with
  the client and method/policy identity.
- Method-level activation works for inherited endpoints, overloads, `@ApiRef`,
  AOT validation, effective-contract export, diagnostics, and mocks.
- Define one cache-aware pre-lookup policy boundary that runs for every hit and
  miss before a value can be returned. Authorization, tenant, and other required
  per-invocation gates belong at this boundary.
- Classify every applicable Boot `WebClientCustomizer` and per-client
  `ReactiveHttpClientCustomizer` before cache activation. Classification covers
  the complete `WebClient.Builder` mutation surface, including filters,
  `defaultRequest`, exchange-function replacement, codecs/connectors, and other
  request/response mutations, not only `ExchangeFilterFunction` values.
- Each customization must be proven cache-safe, represented by a pre-lookup gate
  or key/variant contribution, or declared cache-incompatible. Reject caching
  when any applicable customization or later builder mutation is unclassified.
- Initial eligibility is restricted to explicitly selected `GET` methods that
  return finite, materialized `Mono<T>` or `Mono<ResponseEntity<T>>` values.
- `Flux`, `Mono<Void>`, streaming envelopes, `DataBuffer`, publishers,
  resources, multipart/stream bodies, and unresolved generic values fail
  startup when caching is selected.
- Only successful, non-null emissions are cache candidates. Empty completion,
  cancellation, redirect, decode/auth/transport/resilience errors, and mapped
  4xx/5xx failures never create entries.
- The annotation/configuration names and precedence are frozen before they enter
  the public compatibility include set; examples in this roadmap are not used
  as a substitute for that design review.

## 5. Cache Key, Variant, and Isolation Contract

Make cross-user or cross-tenant reuse impossible without explicit user intent.

**Acceptance:**

- Every key includes concrete client identity, full resolved method signature,
  and a deterministic representation of the selected request-varying inputs.
- Encode key inputs as a canonical typed structure with explicit null markers,
  scalar type identifiers, length framing, container boundaries, element/index
  boundaries, and canonical map-entry ordering before equality or one-way
  derivation. Delimiter concatenation, generic `toString()` fallback, identity
  hash codes, and unframed serialized text are not valid selected key/context
  encodings. Path/query dimensions use one bounded structural string snapshot
  sent through request-target construction so wire-distinct values remain
  distinct without unbounded intermediate projection allocation.
- Path/query values and declared key parameters have stable handling for nulls,
  arrays, collections, maps, inherited generics, and ordering. Each subscription
  freezes one supported argument snapshot and uses that same snapshot for key
  construction and request materialization. Records are reconstructed from one
  captured accessor pass and rejected when their accessors cannot represent the
  captured state. Mutable or nested values that cannot be copied safely are
  rejected rather than retained as live key/request state.
  Top-level query arrays expand into ordered query values. Arrays in path
  positions or nested inside query elements are rejected until request-target
  conversion has a stable structural projection rather than identity-based
  `String.valueOf`. Container arrays use interface component types capable of
  holding defensive snapshots; incompatible concrete declarations and
  covariant runtime arrays fail before dispatch.
- Request-bound selected collections preserve the same element order used by
  URI, header, and body materialization. Request-bound body maps preserve entry
  iteration order. Canonical map/set ordering applies only to selected values
  whose wire representation is not order-sensitive. URI variants retain their
  non-normalized textual representation.
- One cumulative element budget includes container members, optional values,
  and record components. Startup and runtime count one level per nested record.
  Runtime accounting charges actual iterated members rather than trusting
  collection size metadata, and freezing preserves equal-by-value members of
  identity-based sets plus every iterated identity-map entry. One cumulative
  byte budget is enforced while nested canonical frames and bounded
  request-target projections are written. UTF-8, URI text, and arbitrary-
  precision numeric scalar sizes are checked before encoded arrays are
  allocated.
- Header-, locale-, tenant-, Reactor-context-, or auth-dependent responses
  require explicit variant/partition inputs or an explicit shared-response
  acknowledgement. The starter never silently assumes such responses are
  globally shareable. The universally required, potentially absent effective
  idempotency header does not by itself prove authenticated identity isolation.
- The conventional context-only `Idempotency-Key` header is an available header
  variant for every selected policy and must be partitioned or explicitly
  acknowledged as shared.
- AOT registers record accessors reachable from client method parameters.
  Native applications explicitly register record types used only through
  `vary-by-context`, because their runtime type is absent from the declarative
  client signature.
- Method-level key selection is validated at startup. Unknown parameter/header
  names, unsupported values, unstable publishers/streams, and ambiguous maps are
  rejected before dispatch.
- Key equality is deterministic, but telemetry and diagnostics expose only
  bounded policy/client/API facts. Raw and hashed key values are not exported.
- Auth tokens, credentials, cookies, request IDs, and correlation IDs are never
  retained as ordinary key text. Any required sensitive partition value uses an
  opaque one-way representation and is cleared on eviction.
- Tests prove no collision across clients, overloaded methods, inherited generic
  methods, argument order, tenants, and configured variants, including
  adversarial null/string, scalar-type, prefix/suffix, nested-container, empty,
  and map-order boundary cases.

## 6. Phase One - Bounded Local TTL Cache

Implement the smallest useful cache only after key and variant isolation are
defined: local, bounded, expiring, and opt-in.

**Acceptance:**

- Use a proven local cache implementation for maximum-size eviction and expiry;
  do not implement a custom concurrent eviction algorithm.
- Cache storage is process-local and bounded by the selected policy's maximum
  size. The aggregate configured capacity is visible in sanitized diagnostics.
- TTL is a hard serve boundary measured with a monotonic source. Clock changes
  cannot make entries immortal or immediately stale.
- No response can be stored until Priority 5 has produced its validated,
  isolated key and variant decision.
- A hit runs the mandatory cache-aware pre-lookup policy gates, then returns the
  cached materialized value without downstream Resilience4j admission, redirect
  handling, pool acquisition, or transport dispatch. Auth/tenant resolution
  required for authorization or key partitioning cannot be skipped.
- A miss executes the existing logical-call pipeline once for that caller and
  stores only a successful eligible result after full decoding.
- Phase one intentionally does not coalesce concurrent misses: two simultaneous
  misses may perform two loads. This is documented so single flight is not
  claimed before phase two.
- Concurrent same-key misses use a generation-checked, first-successful-fill-wins
  publication rule. A later completion still returns its own value to its caller
  but cannot replace the winning entry or restart its TTL after another fill,
  expiry, eviction, or refresh generation has advanced.
- Maximum-size eviction, TTL expiry, replacement, cancellation, load failure,
  and factory shutdown release all cache references deterministically.
- Cached mutable objects are not copied or re-serialized. Documentation states
  that callers may observe the same object instance and should use immutable DTOs
  or copy on their side.
- A missing optional cache implementation fails startup only for clients that
  select caching; clients with caching disabled remain unaffected.

## 7. Phase Two - Request Coalescing / Single Flight

Deduplicate concurrent loads only after phase-one lookup and storage semantics
are stable.

**Acceptance:**

- Single flight is separately opt-in per cache policy and is disabled by default
  until selected.
- Concurrent misses for the same key share exactly one load. Different keys and
  different clients/policies remain independent.
- Retry, one-time auth replay, and redirects happen inside the leader load and
  are not repeated for each waiter.
- Every caller retains its own subscription, timeout budget, cancellation, and
  one terminal lifecycle/observer/exchange record. Coalescing does not merge
  caller reporting state.
- The shared load is owned by the set of interested callers, not by the first
  caller's outer logical-call timeout. If the first caller times out after a
  waiter joins, that caller detaches while the load continues for the waiter;
  request/attempt timeouts and any explicit shared-load safety bound still apply.
- Cancelling one waiter does not cancel a load still required by another waiter.
  Cancelling the last interested caller has one documented behavior and cannot
  populate a value from an abandoned load accidentally.
- Load error, empty completion, and cancellation fan out deterministically and
  remove the in-flight entry so a later caller can try again.
- A slow or failed key does not block unrelated keys, exhaust a global lock, or
  retain caller context after termination.
- Deterministic concurrency tests prove one transport dispatch and one body
  subscription for a coalesced load without using timing-only assertions. They
  cover both waiter timeout while the first caller succeeds and first-caller
  timeout while a later waiter succeeds.

## 8. Phase Three - Refresh on Access

Add bounded freshness without a scheduler inventing requests outside caller
context.

**Acceptance:**

- Refresh is separately opt-in and requires a positive refresh-after duration
  strictly less than the hard TTL.
- Every refresh has a mandatory finite refresh timeout. Its effective deadline
  is the earliest of that timeout, the entry's hard expiry, and factory shutdown;
  a non-terminating refresh cannot outlive all caller subscriptions indefinitely.
- An access after refresh-after but before hard expiry returns the current value
  and triggers at most one refresh load for that key.
- Concurrent accesses during refresh receive the current value and do not create
  additional refreshes. Miss single-flight and refresh single-flight have
  explicit, non-overlapping state transitions.
- Refresh uses the live triggering invocation's validated key/variant context.
  The cache does not retain a request publisher, auth token, Reactor context, or
  arbitrary argument graph for scheduled replay.
- Refresh bypasses recursive cache lookup but otherwise uses the same pre-lookup
  gates, auth, selected Resilience4j operators, redirect handling, request/
  response timeout, and transport pipeline as a miss load. Those operator calls
  and permits are real hidden refresh work and are reported as such.
- Refresh success atomically replaces the value and restarts its age. Refresh
  failure preserves the current value only until hard expiry and is observable
  without failing callers that already received the stale value.
- After hard expiry, no stale value is served. The next caller follows the normal
  miss/load contract; the expired refresh is cancelled and cannot publish late.
- Factory shutdown cancels/awaits refresh work under the existing aggregate
  shutdown bound and cannot leave cache-owned tasks or references alive.
- Tests cover refresh success, failure, cancellation, hard-expiry races, eviction
  during refresh, and one load under concurrent access.

## 9. Cache, Resilience, Auth, Redirect, and Timeout Composition

Define one operator boundary so cached and loaded calls cannot produce ambiguous
side effects.

**Acceptance:**

- Lookup wraps the existing load pipeline: a hit consumes no retry permit,
  circuit-breaker call, rate-limit permit, bulkhead slot, redirect, pool
  acquisition, or HTTP dispatch after mandatory pre-lookup policy/auth/key gates
  have succeeded.
- Every Boot/per-client customizer and resulting builder mutation is classified
  before caching is allowed. A cache hit cannot bypass authorization, tenant,
  dynamic `defaultRequest`, exchange-function, filter, codec, connector, or
  response-transformation behavior that affects policy, key identity, or value.
- A miss leader uses the selected resilience operators exactly as an uncached
  call does. Cache storage occurs only after the final successful decoded result.
- A refresh load bypasses lookup only and otherwise uses that same miss pipeline,
  including auth, resilience, redirects, timeouts, and transport; refresh failure
  remains hidden from the stale caller but visible as refresh work.
- Logical-call timeout includes lookup and each caller's wait. A waiter timeout
  does not rewrite or clear shared-load state, and the first caller's timeout
  cannot truncate a later waiter's fresh budget while that waiter remains
  interested.
- Unsafe methods remain ineligible even when idempotency keys exist. Caching does
  not become a replay-safety mechanism.
- Cached results do not suppress explicit downstream writes or infer
  invalidation from another method call.
- Prior load/refresh URL, status, headers, error, failure stage, attempt count,
  body size, and request-dispatch evidence cannot leak into a cache hit or a
  different caller.
- `ResponseEntity<T>` caching preserves the selected value and status but copies
  only a documented, bounded allowlist of representation headers into the cache.
  `Set-Cookie`, auth challenges, `SensitiveHeaders`, and configured per-caller
  headers make the response non-cacheable; non-allowlisted headers are never
  replayed from the first caller to later hits.

## 10. Phase Four - Cache Metrics, Observability, and Diagnostics

Make cache effectiveness and hidden cache work visible while retaining bounded
telemetry and the established one-terminal-record contract.

**Acceptance:**

- Freeze one bounded outcome vocabulary before changing public event or metric
  schemas: fresh hit, miss loader, coalesced waiter, stale hit with refresh,
  load success/failure/cancellation, refresh success/failure/cancellation, TTL
  expiry, and size eviction.
- Cache metrics and cache-specific terminal observability are separately opt-in
  and disabled by default. Selecting caching alone records no cache-library
  statistics, cache meters, cache OTel attributes, or cache-specific log/context
  exports; the existing global observability gate must also permit them.
- Micrometer exposes a lookup counter by client, API name, and `hit`/`miss`.
  Coalescing and stale-serving remain separate bounded outcomes so hit ratio is
  not made ambiguous by phase-two or phase-three behavior.
- Load and refresh meters distinguish operation kind and terminal result. Any
  duration meter uses the registry's time base and does not duplicate the
  caller's logical-call timer under the same name.
- Current entry count, configured maximum size, and eviction count are exposed
  with starter-specific meter names. Cache keys, argument values, policy names
  supplied from unbounded input, and raw method signatures are never tags.
- Cache meter registrations are owned by the client factory/cache runtime that
  created them. Factory destruction removes every cache counter, timer, summary,
  and gauge from the still-live `MeterRegistry` and releases references to the
  closed cache before returning.
- Destroying and recreating a factory with the same meter name/tags registers
  meters backed by the replacement cache, never a stale strong/weak reference or
  an old meter returned by Micrometer's duplicate-registration behavior.
- Meter names, types, units, tag keys, zero-series behavior, and PromQL recipes
  for hit ratio, miss/load rate, coalescing ratio, refresh failure rate, and
  capacity pressure are verified at scrape level.
- A cache hit reports one caller terminal outcome with `attemptCount=0` and
  `requestDispatched=false`; it does not invent an HTTP status, server address,
  failure stage, or wire byte count.
- Miss loaders retain existing terminal HTTP facts. Coalesced waiters and stale
  hits are explicitly distinguishable from transport callers.
- Lifecycle, observer, and exchange-log contexts expose the same bounded cache
  outcome without exposing a key or cached value. Existing compatibility
  constructors represent cache outcome as unknown rather than inventing a miss.
- The built-in OTel logical-call span carries a bounded cache outcome attribute
  for caller subscriptions. Hidden refresh does not silently create detached
  spans or alter parentage; any refresh span/event requires an explicit reviewed
  opt-in. Refresh remains visible through bounded cache meters and sanitized
  logging.
- Diagnostics schema exports only static policy facts and bounded aggregate
  state such as enabled phase, TTL, refresh threshold, single-flight state,
  maximum size, current entry count, and aggregate evictions. It never dumps
  entries, keys, arguments, headers, or values.
- Exchange logging, lifecycle hooks, Micrometer, OTel, health, and support
  bundles agree on caller-visible cache outcome and hidden refresh/load facts.
- Cache hit ratio, refresh failure, eviction pressure, and low entry count are
  operational signals, not health failures by themselves. The health indicator
  cannot mark a downstream UP or DOWN solely from cache efficiency.
- Deterministic tests prove exactly one caller terminal record for hits, misses,
  coalesced waiters, stale hits, timeout, cancellation, load failure, and refresh
  failure while hidden work has separate aggregate telemetry.
- Cache metrics remain absent when no cache policy is selected and when caching
  is selected but cache observability is disabled. Enabling cache metrics does
  not enable caching.

## 11. Mock, Consumer, AOT, Native, and Shutdown Parity

Prove the feature outside unit-only invocation paths.

**Acceptance:**

- `MockReactiveHttpClient` can install deterministic cache policies, control
  time, assert hit/miss/coalesced/refresh outcomes, inspect load counts, and
  evict entries without requiring sleeps.
- Mock documentation states that it does not prove real transport dispatch,
  connection pooling, or native resource cleanup.
- The assembled Boot 4 consumer covers disabled caching, one method opt-in,
  client-wide opt-in/exclusion, TTL expiry, capacity eviction, single flight,
  refresh, and explicit resilience selection.
- AOT validates inherited method cache metadata only for starter-backed factory
  beans and respects replacement `MethodMetadataCache` behavior.
- Native hints cover public annotations/configuration models and any cache
  implementation resources actually needed at runtime without broad package
  reflection.
- Native smoke proves one cached read and explicit retry-only activation while
  counting all loopback dispatches.
- Factory destruction clears cache state and terminates in-flight loads and
  refreshes under the existing shared shutdown deadline, then deregisters every
  factory-owned cache meter idempotently.

## 12. Performance and Allocation Evidence

Verify that disabled caching is nearly free and enabled caching serves its
purpose without misleading comparisons.

**Acceptance:**

- No-network invocation benchmarks compare caching disabled with the published
  `3.6.0` baseline and identify any new lookup/allocation on the default path.
- Loopback benchmarks cover cache miss, cache hit, coalesced concurrent miss, and
  refresh-on-access using equivalent work for compared implementations.
- Cache-hit benchmarks do not compare against raw WebClient performing a network
  call as if that measured abstraction overhead. Comparisons are labeled by the
  work actually performed.
- Allocation audits include key construction, hit, miss, waiter, eviction, and
  refresh paths. Keys and completed in-flight state do not retain request graphs.
- Resilience benchmarks prove `enabled: true` with no selected operators has no
  operator-subscription overhead and explicit retry-only behavior does not
  initialize unrelated operators.
- Smoke reports remain non-publishable. Any public performance claim requires a
  clean, versioned, source-controlled report and published baseline pair.

## 13. Migration and Operations Documentation

Make the major behavior change and cache limitations difficult to misunderstand.

**Acceptance:**

- A `3.x` to `4.0.0` migration guide shows the exact old and new resilience
  activation matrix and the explicit configuration needed to retain all four
  former `default` operators.
- Quick-start and resilience guides teach single-operator opt-in first. No
  example uses `enabled: true` alone while claiming operators are active.
- A cache guide explains local-only scope, TTL, capacity, object identity,
  key/variant safety, auth/tenant partitioning, no automatic write invalidation,
  and unsupported streaming/error shapes.
- Operations guidance includes hit ratio, miss/load rate, coalescing, refresh
  failures, eviction pressure, stale/hard-expiry behavior, and per-instance
  differences after deployment.
- Support-bundle fixtures use fake `.example.invalid` endpoints and contain no
  keys, arguments, headers, bodies, credentials, or tenant values.
- Generated configuration metadata, examples, docs links, public API inventory,
  and release artifacts are checked for drift.

## 14. Public API and Compatibility Evidence

Review every new cache surface and every resilience change before freezing the
major release.

**Acceptance:**

- Public cache annotations, configuration models, metrics/diagnostic fields, and
  test-helper methods are intentionally included in or excluded from japicmp and
  documented accordingly.
- No cache implementation type leaks into the starter's public API unless it is
  deliberately accepted as a long-term extension contract.
- The reviewed major API delta against published `3.6.0` is source-controlled
  and guarded exactly; additive incompatible interface changes cannot bypass the
  report-only guard.
- Dependency evidence records the chosen cache implementation version and proves
  disabled clients remain usable when that optional implementation is absent.
- Configuration binding, AOT, native, mock, current consumer, published-baseline
  consumer, and package-generation evidence all use isolated, reproducible
  inputs.

## 15. V27 / `4.0.0` Go-No-Go

Release only when the behavior change and all four cache phases are supportable
as one coherent contract.

**Acceptance:**

- Select `4.0.0` only after the explicit resilience migration, cache phases,
  public API review, and operations guidance are complete; otherwise record a
  no-go and keep the snapshot unpublished.
- Root and module-scoped major API reports, dependency matrix, packaging guard,
  current and published consumers, AOT, native, benchmark audit, generated docs,
  and support-bundle fixtures pass from a clean commit.
- Maven Central resolution for the published `3.6.0` baseline is proven from
  isolated repositories before comparison evidence is accepted.
- The changelog states the resilience behavior break prominently and does not
  describe response caching as automatic, distributed, coherent across nodes,
  or safe for undeclared variants.
- After publication, verify parent, starter, test-helper, and OTel artifacts plus
  an assembled consumer from Maven Central before moving public coordinates and
  baselines or closing V27.
