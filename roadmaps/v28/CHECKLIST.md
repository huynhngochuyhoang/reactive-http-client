# Reactive HTTP Client - Roadmap V28 Execution Checklist

Companion to [`ROADMAP.md`](ROADMAP.md). Execute priorities in order unless a
confirmed correctness or release blocker requires reordering. Check an item only
after implementation and verification evidence is recorded under that priority.
Generated evidence belongs under `target/release-evidence/v28/` unless a
promoted, versioned artifact is explicitly required.

V28 is an additive cache-contract program. Existing explicit GET caching must
remain compatible throughout the work. No implementation may enable a non-GET
method until the method-specific semantic-read acknowledgement and bounded,
wire-equivalent request identity are both proven.

---

## Priority 1 - Post-`4.0.0` Baseline and V28 Scope Integrity

### [x] 1.1 Align development, published, and roadmap lanes

- [x] Keep root/module and reactor-only fixture coordinates on
      `4.1.0-SNAPSHOT`.
- [x] Keep public dependency snippets and `latest.published.version` on
      published `4.0.0`.
- [x] Keep API compatibility, published-consumer, and benchmark baselines on
      published `4.0.0`.
- [x] Keep V28 as the only active execution roadmap without rewriting completed
      V1-V27 release evidence.
- [x] Record `4.1.0` only as a candidate direction; do not advertise it as
      released before publication verification.

### [x] 1.2 Prove the published `4.0.0` baseline

- [x] Resolve the parent POM plus starter, test-helper, and OTel POM/JAR/source/
      Javadoc artifacts from a previously absent Central-only repository.
- [x] Require Maven Central remote markers and record SHA-256 values for every
      required artifact.
- [x] Run strict root japicmp against published `4.0.0` from a fresh repository.
- [x] Run strict starter-module japicmp against published `4.0.0` from a separate
      fresh repository.
- [x] Run published-baseline fixtures for local contamination, mixed versions,
      missing attachments, mismatched POM/JAR versions, and self-comparison.

### [x] 1.3 Keep generated readiness honest

- [x] Report `4.1.0-SNAPSHOT` as development and `4.0.0` as the latest
      published/API baseline.
- [x] Keep the final candidate, benchmark promotion, and publication work
      deferred until V28 release preparation.
- [x] Include every unfinished semantic-read, key/body, composition, consumer,
      native, benchmark, API, and documentation priority in pending readiness.
- [x] Run `DocumentationReleaseArtifactTest`, Maven validation, and
      `git diff --check`; record commands and totals under this priority.

Evidence recorded on 2026-08-28:

- Root, starter, test-helper, benchmark, assembled-consumer, and native-smoke
  coordinates remain on `4.1.0-SNAPSHOT`. Public README/quick-start dependency
  snippets, `latest.published.version`, API compatibility, published-consumer,
  and benchmark baselines remain on published `4.0.0`. The roadmap archive
  test confirms V28 is the only active roadmap and leaves V1-V27 completed.
- `scripts/verify-published-release-artifacts.sh 4.0.0` resolved the parent POM
  plus the starter, test-helper, and OTel POM, binary, source, and Javadoc
  artifacts through `.mvn/maven-central-settings.xml` from the previously
  absent `release-artifacts-4.0.0` repository. The provenance verifier accepted
  every Central marker, declared/embedded version, and 13 SHA-256 records copied
  under `target/release-evidence/v28/priority1/published-baseline/`.
- Strict root and starter-module `api-compatibility` builds passed independently
  against published `4.0.0` from the fresh
  `v28-priority1-api-root-4.0.0` and
  `v28-priority1-api-starter-4.0.0` repositories. Their Central provenance and
  7 root / 2 starter SHA-256 records are under
  `target/release-evidence/v28/priority1/api-root/` and `api-starter/`.
- `scripts/verify-published-baseline-fixtures.sh` rejected locally installed
  artifacts, candidate-version contamination, missing POM/source/Javadoc
  attachments, mismatched project/parent POM and embedded JAR versions, and
  root/module self-comparison.
- Generated readiness now reports `v28`, the `additive-minor` lane,
  `4.1.0-SNAPSHOT` development, published/API baseline `4.0.0`, and a deferred,
  unpublished `4.1.0` candidate. Its pending work explicitly retains release
  scope, semantic-read contract, key/body identity, composition, consumer,
  AOT/native, benchmark, API, documentation, and publication work; benchmark
  promotion and publication remain deferred until the release cut.
- `mvn -B -ntp -pl reactive-http-client-starter
  -Dtest=DocumentationReleaseArtifactTest test` passed 43 tests with no failures,
  errors, or skips. `mvn -B -ntp -s .mvn/maven-central-settings.xml validate`
  passed all four reactor modules, and `git diff --check` passed.

---

## Priority 2 - Semantic-Read Opt-In Contract

### [x] 2.1 Characterize the published `4.0.0` behavior

- [x] Add focused tests proving client- and method-selected GET caching remains
      explicitly opt-in.
- [x] Prove selected POST, PUT, PATCH, DELETE, OPTIONS, HEAD, and non-GET
      `@ApiRef` methods currently fail the fixed-GET eligibility guard.
- [x] Cover client-wide policies on mixed GET/non-GET interfaces,
      `@CacheDisabled`, inherited methods, overloads, and method-level policy
      precedence.
- [x] Record current startup messages, effective-contract output, diagnostics,
      AOT, and mock behavior before changing the grammar.
- [x] Freeze the existing return/body eligibility and cache outcome behavior so
      verb support cannot broaden unrelated cache shapes.

### [x] 2.2 Freeze the public semantic-read acknowledgement

- [x] Select one additive, method/API-specific public spelling for semantic-read
      intent before implementing runtime support.
- [x] Define the annotation/configuration default as absent or false so compiled
      and source 4.0 clients retain GET-only behavior.
- [x] Define exact precedence among method policy, API-specific configuration,
      client-wide policy, semantic-read acknowledgement, and `@CacheDisabled`.
- [x] Prevent a generic client-wide policy or method-name pattern from
      acknowledging every non-GET endpoint.
- [x] Document the declaration as an application guarantee that a cache hit may
      suppress downstream dispatch without omitting a required side effect.
- [x] Add Javadoc, configuration metadata, effective examples, native hints, and
      public API inventory entries for the final shape.

### [x] 2.3 Enforce the selection matrix

- [x] Preserve selected GET behavior without requiring the new acknowledgement.
- [x] Reject a client-policy-selected non-GET method that has no method/API
      acknowledgement.
- [x] Reject a method-policy-selected non-GET method that has no acknowledgement.
- [x] Accept an acknowledged non-GET method only after all later response, body,
      key, auth, and customization checks pass.
- [x] Keep unselected and `@CacheDisabled` methods on the ordinary request path.
- [x] Include client name, declaring/concrete method, resolved verb, policy name/
      source, and correction in startup errors without printing request data.

Evidence recorded through 2026-08-28:

- `roadmaps/v28/SEMANTIC_READ_BASELINE.md` freezes published `4.0.0` selection,
  startup, effective-contract, diagnostics, AOT, mock, response, and body
  behavior before recording the additive V28 matrix.
- `CacheResponse.semanticRead()` is the single method-scoped acknowledgement and
  defaults to `false`. `MethodMetadata`, `RequestPlan`, and effective-contract
  snapshots retain the resolved value. Client-wide policy selection, method
  names, idempotency keys, retry metadata, and statuses cannot imply intent.
- Focused grammar coverage proves all six non-GET annotation verbs plus non-GET
  `@ApiRef`, mixed interfaces, disabled/unselected methods, inherited generic
  methods, overloads, policy precedence, rich errors, and unchanged finite/body
  rejections. An unresolved `@ApiRef` verb fails before semantic intent is
  considered, and a body-bearing semantic non-GET method must select its
  serialized wire bytes even under `shared-response`. Acknowledged methods
  still pass the existing key, auth, and customization gates before cache
  allocation or dispatch.
- Startup, effective-contract, diagnostics, AOT, and mock parity are covered by
  `DeclarativeCachePolicyTest`, `ReactiveHttpClientContractSnapshotTest`,
  `ReactiveHttpClientDiagnosticsProviderTest`,
  `ReactiveHttpClientAotSmokeTest`, and `MockReactiveHttpClientTest`.
- Javadoc, configuration metadata/reference, annotation and cache guides,
  effective configuration, public API inventory, changelog, and runtime-hint
  assertions document the method-specific guarantee and the absent client-wide
  switch. The focused documentation/metadata run passed 61 tests.
- `mvn -B -ntp -pl reactive-http-client-starter,reactive-http-client-test -am
  test` passed 1,221 starter tests and 59 test-helper tests with no failures,
  errors, or skips. Root `mvn -B -ntp validate` passed all four reactor modules,
  and `git diff --check` passed.

---

## Priority 3 - Verb-Independent Declarative Eligibility Grammar

### [x] 3.1 Centralize effective cache eligibility

- [x] Replace the fixed `GET` rejection with one package-private decision that
      represents disabled, GET-friendly selected, acknowledged semantic read,
      and invalid selection.
- [x] Resolve annotation and `@ApiRef` verbs through the concrete client and
      inherited generic method before deciding eligibility.
- [x] Use the same decision in factory startup, invocation, effective-contract
      export, diagnostics, AOT, and `MockReactiveHttpClient`.
- [x] Remove duplicated verb checks made obsolete by the effective decision.
- [x] Keep foreign/replacement `FactoryBean` clients and replacement metadata
      caches outside starter-only validation where established contracts require
      it.

### [x] 3.2 Preserve finite response and owned-request boundaries

- [x] Accept only finite materialized `Mono<T>` and
      `Mono<ResponseEntity<T>>` response shapes already supported by V27.
- [x] Keep `Flux`, raw/unresolved values, `Mono<Void>`, bodiless envelopes,
      nested publishers, `DataBuffer`, `Resource`, and streaming responses
      rejected.
- [x] Keep multipart, form streams, publishers, `DataBuffer`, `Resource`,
      `InputStream`, `Reader`, channels, and other application-owned request
      bodies rejected.
- [x] Prove valid JSON, `String`, and `byte[]` body-bearing semantic reads across
      supported non-GET verbs.
- [x] Prove idempotency keys, retry annotations, response status, and method names
      cannot substitute for semantic-read intent.

### [x] 3.3 Verify every declaration path

- [x] Cover direct, inherited, multi-level generic, overloaded, bridge-method,
      factory-method, and `@ApiRef` clients.
- [x] Cover annotation-only, client-policy plus method acknowledgement,
      API-configured, disabled, missing-policy, and blank-policy cases.
- [x] Assert proxy construction fails before auth, body serialization/resource
      acquisition, lifecycle attempt hooks, cache allocation, or dispatch.
- [x] Run focused grammar, factory, diagnostics, contract-export, AOT, and mock
      suites before moving to key construction.

Evidence recorded on 2026-08-27:

- `EffectiveCachePolicy.Decision` is the package-private source of truth for
  `DISABLED`, `GET_FRIENDLY_SELECTED`, `SEMANTIC_READ_SELECTED`, and `INVALID`.
  Factory startup and AOT/mock validation consume it through
  `MethodMetadataCache`; invocation, contract export, diagnostics, customization
  checks, auth-support checks, and cache registration use the same result. The
  sole remaining `GET` interpretation is inside that decision.
- Concrete-client `RequestPlan` resolution covers direct and overloaded methods,
  inherited and multi-level generic contracts, compiler bridge methods, all six
  annotation verbs, and configured `@ApiRef` methods. The factory-method AOT
  fixture now proves an acknowledged semantic `POST`; foreign factories and a
  replacement `MethodMetadataCache` retain their established exclusions.
- Grammar tests retain every finite-response and owned-request rejection for an
  acknowledged non-GET selection, and accept JSON records, `String`, and
  `byte[]` bodies across `POST`, `PUT`, `PATCH`, `DELETE`, `HEAD`, and `OPTIONS`
  only when their wire body is selected for the key. Disabled/unselected,
  missing/blank policy, idempotency/retry, and unresolved-verb cases remain
  fail-safe.
- The focused cache/grammar/factory/diagnostics/contract/AOT run passed 291
  starter tests. The mock grammar run passed 56 tests and proves invalid proxy
  construction invokes no auth provider, lifecycle start, or dispatch.
- `mvn -B -ntp -pl reactive-http-client-starter,reactive-http-client-test -am
  test` passed 1,223 starter tests and 59 test-helper tests with no failures,
  errors, or skips.

---

## Priority 4 - Body-Bearing Request Identity

### [x] 4.1 Inventory preparation, key, auth, and wire ownership

- [x] Trace JSON, `String`, `byte[]`, null, and present-empty bodies from method
      arguments through cache preparation, auth materialization, and WebClient
      writing.
- [x] Record every point where content type, charset, codecs, customizers, or
      filters can change the effective bytes.
- [x] Identify and remove any second serialization or body copy introduced only
      by non-GET cache eligibility.
- [x] Define the lifetime and owner of frozen arguments and prepared bytes for
      hit, miss, waiter, refresh, timeout, cancellation, eviction, and shutdown.

### [x] 4.2 Derive one wire-equivalent body identity

- [x] Include every supported body-bearing non-GET request's effective serialized
      body bytes in cache identity by default.
- [x] Reuse one bounded byte representation for cache identity, built-in signing,
      and the final request writer where the existing contract supports it.
- [x] Represent effective `Content-Type` and charset whenever they can change the
      bytes or writer selected for the request.
- [x] Keep null body, present zero-length body, empty string, empty JSON value,
      and absent/explicit content type distinct when their requests differ.
- [x] Reject a body/customizer/codec combination whose final bytes cannot be
      proven before lookup.
- [x] Do not use `shared-response` as an implicit waiver for omitted non-GET body
      identity; defer omission unless a separate reviewed acknowledgement is
      implemented.

### [x] 4.3 Enforce bounded preparation and cleanup

- [x] Apply cumulative byte, element, depth, and projection limits before large
      values or encodings allocate beyond the existing key-material cap.
- [x] Test large strings, byte arrays, records, numbers, nested containers,
      shared graphs, null/empty boundaries, and serialization-limit failures.
- [x] Release prepared body bytes and frozen argument graphs after terminal
      completion and never retain them in cache entries or completed flights.
- [x] Prove cancellation/timeout before request-body subscription releases all
      cache-owned preparation state and publishes no entry.
- [x] Verify no body/key material appears in exception messages, diagnostics,
      logs, metrics, OTel attributes, or support fixtures.

Evidence recorded on 2026-08-27:

- `BODY_IDENTITY_AUDIT.md` traces null, `byte[]`, `String`, and bounded JSON from
  frozen arguments through opaque-key framing, pre-lookup auth, the WebClient
  writer, terminal cleanup, and every supported mutation/proof boundary.
- `CacheKeyContract.SerializedBodyKey` now frames body presence, normalized
  effective media type/charset, and exact prepared bytes. Semantic non-`GET`
  methods retain the mandatory selected-body rule even with `shared-response`.
  Invalid media types and auth attempts to replace the prepared content type
  fail before lookup or dispatch.
- `CacheKeyContractTest` proves a semantic `POST` partitions equal bytes by media
  type, partitions non-ASCII strings by charset bytes, distinguishes absent and
  present-empty bodies, performs one bounded JSON serialization shared with auth
  and the writer, and leaves no entry/body subscription after pre-dispatch
  timeout. Existing key tests retain large scalar, byte array, record, number,
  nested container, shared-graph, serialization-bound, privacy, and completed-
  flight retention coverage.
- Auth media-type validation is request-scoped only to prepared body identities,
  runs again before a `401` auth replay, and compares canonical parameter maps.
  Focused tests prove a refreshed replacement cannot dispatch, equivalent
  parameter orders share one entry, and unselected/bodiless calls remain valid.
- Each auth resolution receives a defensive raw-body copy. A mutating custom
  provider cannot change writer bytes or publish a response under the original
  identity for different wire content.
- Focused `CacheKeyContractTest`: **55 tests**, all passing.
- Cache/auth/replay regression selection: **129 tests**, all passing.
- Documentation and configuration-metadata guards: **61 tests**, all passing.
- Full starter and test-helper reactor run: **1,231 starter tests** and **59
  test-helper tests**, all passing.

---

## Priority 5 - Request Target, Headers, Auth, and Tenant Isolation

### [x] 5.1 Preserve complete request identity

- [x] Include concrete client identity, resolved method signature, request target,
      body identity, and selected header/context variants in the opaque key.
- [x] Preserve bounded, wire-equivalent path/query/header projections and their
      order, null, type, and framing distinctions.
- [x] Prove different methods, verbs, bodies, media types, tenants, locales, API
      versions, and explicit variants cannot collide.
- [x] Keep raw and hashed key values private to cache lookup and absent from all
      public/support surfaces.

### [x] 5.2 Preserve per-caller authorization and partitioning

- [x] Run pre-lookup auth and required policy gates for every hit and miss.
- [x] Validate auth headers on hits with the same rules as ordinary dispatch.
- [x] Require authenticated responses to have a real auth/tenant partition or an
      explicit shared-response acknowledgement; an absent idempotency header is
      not a partition.
- [x] Apply the frozen Reactor-context snapshot to auth, variants, lookup, and
      load so mutation cannot authorize one identity and key another.
- [x] Prove token refresh, different principals, and context-provided headers do
      not cross cache entries.

### [x] 5.3 Revalidate customization safety

- [x] Reuse the complete Boot/per-client WebClient customization inventory for
      every newly eligible method.
- [x] Keep unclassified or incompatible filters, `defaultRequest`, exchange
      functions, codecs, connectors, and response transformations startup-fatal.
- [x] Require customizations that mutate method, target, body, content type, or
      variants to contribute equivalent pre-lookup facts or remain incompatible.
- [x] Cover parent/child contexts, ordered customizers, replacement builders,
      lazy beans, and mock-installed customizations without creating lazy beans
      from diagnostics.

Evidence recorded on 2026-08-28:

- The subscription-local key frame now includes concrete/logical client,
  generic-resolved method signature, finalized HTTP method and URI, serialized
  body identity, selected finalized headers, and frozen context variants. Only
  the SHA-256 key survives derivation; request, header, auth, and context values
  remain private.
- The pre-lookup auth probe validates every caller, then carries the authorized
  request through downstream SAFE filters to the terminal non-dispatching probe.
  Auth, `defaultRequest`, and filter mutations therefore finalize selected
  target/header facts without reaching the exchange function, and principal/auth
  changes cannot reuse another caller's entry. A retry or `401` replay whose final
  identity differs from the lookup key returns normally but cannot publish under
  that stale key.
- Frozen context is prepared before auth and reused for authorization, key
  variants, lookup, and the miss load. Existing `401` refresh, context-header,
  and mutation contracts remain green.
- Customization validation inventories Boot customizers, per-client
  customizers, and replacement builders across ancestor contexts. Unresolved
  lazy candidates require classification without being instantiated; known
  non-matching singleton customizers remain excluded.
- Focused key/policy/auth/cache selection: **145 tests**, all passing.
- Full starter/test-helper reactor: **1,239 starter tests** and **60
  test-helper tests**, all passing. The mock suite proves authenticated cache
  partition validation occurs before auth invocation and that auth still gates
  each hit and miss.

---

## Priority 6 - Local Cache and Response Semantics Across Verbs

### [x] 6.1 Route acknowledged semantic reads through V27 storage

- [x] Reuse the existing cache manager, policy bounds, monotonic TTL, size
      eviction, and generation-checked publication path.
- [x] Preserve first-successful-fill-wins behavior for duplicate same-key misses
      when single flight is disabled.
- [x] Prove GET and acknowledged non-GET calls have identical hit, miss, expiry,
      eviction, replacement, and shutdown behavior.
- [x] Keep a hit free of downstream resilience admission, redirect, pool, and
      transport dispatch after mandatory pre-lookup gates.

### [x] 6.2 Preserve response eligibility and header safety

- [x] Store only fully decoded successful non-null emissions.
- [x] Keep errors, mapped 4xx/5xx, redirects, cancellation, and empty completion
      non-cacheable for plain and envelope responses.
- [x] Inspect wire response headers before storing plain bodies and
      `ResponseEntity<T>` values.
- [x] Keep sensitive, configured non-cacheable, auth-challenge, cookie, and
      per-caller response headers out of entries.
- [x] Preserve only the established bounded representation-header allowlist on
      cached envelopes.

### [x] 6.3 Preserve value and lifecycle ownership

- [x] Keep the documented shared object-identity behavior; do not clone or
      serialize cached values solely because the verb is non-GET.
- [x] Prevent in-flight loads from repopulating after explicit eviction, expiry,
      cache close, or factory destruction.
- [x] Recheck cache closure and generation under the same synchronization used
      for cache/flight publication.
- [x] Add deterministic tests for late completion, concurrent eviction,
      capacity pressure, and close/recreate with the same policy and meters.

Evidence recorded on 2026-08-28:

- `SemanticReadLocalCacheContractTest` drives `GET` and explicitly acknowledged
  `POST` methods through the production invocation handler and the same
  `LocalResponseCacheManager`. It proves identical hit identity, miss,
  monotonic expiry, size pressure, explicit eviction, close, and hit-side
  resilience/transport suppression.
- The same parity suite proves first-successful-fill-wins for asynchronous
  duplicate misses and rejects late publication after eviction for both verbs.
  Its response matrix keeps 4xx/5xx, redirects, cancellation, empty completion,
  cookies, auth challenges, and configured private headers out of storage while
  retaining only bounded representation headers on cached envelopes.
- Existing `BoundedLocalResponseCacheContractTest` remains the deterministic
  generation/publication, expiry, factory-destruction, and shutdown proof;
  `LocalResponseCacheObservabilityTest` remains the close/recreate proof that
  same-tag meters observe only the replacement cache.
- Focused cache contract and observability verification: **60 tests**, all
  passing (`SemanticReadLocalCacheContractTest`,
  `BoundedLocalResponseCacheContractTest`, and
  `LocalResponseCacheObservabilityTest`).
- Full starter verification: **1,243 tests**, all passing.

---

## Priority 7 - Single Flight and Refresh Composition

### [x] 7.1 Extend single flight to body-bearing semantic reads

- [x] Coalesce only callers with the same complete opaque request identity.
- [x] Prove different body bytes, content types, headers, contexts, methods,
      clients, and policies create independent flights.
- [x] Share one load and one request-body subscription while keeping each caller's
      timeout, cancellation, and terminal state independent.
- [x] Recheck the cache before creating a flight and prevent removed shared
      publishers from reconnecting as detached loads.
- [x] Keep coalesced waiters at zero transport attempts/evidence even if the
      original leader detaches and load ownership transfers internally.

### [x] 7.2 Extend refresh-on-access safely

- [x] Require the same semantic-read acknowledgement and request-identity proof
      for refresh as for a miss.
- [x] Build refresh from the triggering invocation's fresh frozen request rather
      than retaining a prior body publisher, auth context, or argument graph.
- [x] Keep one refresh flight per key with a finite refresh timeout and hard TTL/
      shutdown bound.
- [x] Preserve the current value after refresh failure only until hard expiry;
      late refresh completion cannot repopulate a newer generation.
- [x] Record refresh success, failure, and cancellation exactly once, including
      cancellation before source-subscription attachment.

### [x] 7.3 Add deterministic concurrency evidence

- [x] Cover first-caller timeout with a later waiter succeeding and waiter
      timeout with the leader succeeding.
- [x] Cover last-waiter cancellation, load error, empty completion, body
      serialization failure, hard expiry, eviction during refresh, and shutdown.
- [x] Use latches/probes and body-subscription/dispatch counts rather than
      timing-only assertions.

Evidence recorded on 2026-08-29:

- `SemanticReadSingleFlightRefreshContractTest` exercises acknowledged semantic
  `POST` methods through the production invocation handler and existing V27
  cache manager. Equal complete identities share one dispatch and one request-
  body subscription; body bytes, media type, tenant header, Reactor context,
  target, method, client, and policy variations create independent flights.
- Virtual-time caller tests cover both timeout directions. Every coalesced
  waiter retains `POST` method metadata but zero attempts, URL, status, and
  request headers after leader detachment or independent timeout.
- Deterministic cancellation, error, empty completion, and bounded JSON
  serialization failures prove abandoned semantic-read flights do not publish
  or reconnect and a later caller can create one replacement flight.
- Refresh tests prove the request header from the first stale caller drives one
  refresh per opaque key, failure preserves the old value only to hard expiry,
  and eviction or shutdown cancels work and rejects late publication.
- Existing `BoundedLocalResponseCacheContractTest` remains the direct race proof
  for the atomic cache recheck, reserved-member attachment, hard-expiry refresh
  deadline, and generation invalidation. `LocalResponseCacheObservabilityTest`
  remains the exact-once success/failure/cancellation proof, including eviction
  during refresh assembly before source-subscription attachment.
- Focused semantic and low-level cache verification: **66 tests**, all passing
  (`SemanticReadSingleFlightRefreshContractTest`,
  `SemanticReadLocalCacheContractTest`, `BoundedLocalResponseCacheContractTest`,
  and `LocalResponseCacheObservabilityTest`).
- Full starter verification: **1,249 tests**, all passing.

---

## Priority 8 - Retry, Redirect, Auth Replay, and Timeout Boundaries

### [x] 8.1 Keep cacheability separate from retry and replay safety

- [x] Preserve the configured `retry-methods` eligibility and established
      Resilience4j operator order.
- [x] Keep strict unsafe-retry validation active when a selected Retry can issue
      another non-safe HTTP attempt; semantic-read intent is not proof of an
      idempotency key.
- [x] Keep body-preserving redirect and one-time auth replay behind the existing
      repeatability checks.
- [x] Prove a cached method with retry disabled performs no implicit transport
      retry introduced by cache code.
- [x] Cover cache miss/refresh with retry success/failure, redirect, auth 401
      replay, and pre-dispatch admission rejection.

### [x] 8.2 Preserve credentials and per-attempt request state

- [x] Consume pre-resolved auth only for the intended first outer attempt and
      resolve current auth on later resilience attempts.
- [x] Reset URL, status, headers, error, failure stage, dispatch evidence, body
      size, and attempt facts between retries/auth replay/redirects.
- [x] Keep refresh hidden from the stale caller while exposing its independent
      aggregate terminal result.
- [x] Assert final diagnostics never reuse a previous attempt's body, auth,
      response headers, or classified failure.

### [x] 8.3 Preserve one logical deadline per caller

- [x] Include body preparation, auth, lookup, waiter attachment, and load waiting
      in the logical-call deadline.
- [x] Avoid nested equal-duration timeouts that erase response phase attribution
      or convert timeout into cancellation diagnostics.
- [x] Keep shared-load request/response timeouts independent from each caller's
      outer timeout while another caller remains interested.
- [x] Prove cancellation and timeout before dispatch release prepared bytes and
      cannot publish cache state.

Completion evidence (2026-08-29):

- `SemanticReadReplayTimeoutContractTest` exercises acknowledged body-bearing
  `POST` calls through the production invocation and cache paths. Retry-disabled
  calls dispatch once, `retry-methods` remains the eligibility boundary, and an
  active Retry retains the established application and subscription operator
  order.
- `ReactiveHttpClientFactoryBeanDiagnosticsTest` proves strict unsafe-retry
  validation still rejects a cache-selected semantic `POST` without an
  idempotency contract, while a Retry ineligible for `POST` remains inactive.
- A real loopback `307` fixture with Reactor Netty transport retry disabled
  proves one body-preserving redirect stays inside one miss flight, sends the
  same bytes exactly twice, caches only the final `200`, and suppresses all
  transport work on the following hit.
- The auth fixture records the exact sequence `stale`, `fresh-one`, `fresh-two`,
  and `fresh-three`: pre-resolved auth is consumed once, the hidden `401` replay
  refreshes it, the outer Retry resolves current auth, and final request-identity
  validation prevents publication under stale credentials.
- Pre-dispatch CircuitBreaker rejection leaves zero dispatches and entries;
  failed refresh retries remain hidden behind the stale value, emit one refresh
  failure, and cannot extend that value past hard expiry.
- Cancellation and logical timeout during pre-lookup auth leave no entry and
  permit one later replacement load. A real stalled response body preserves the
  final `200` and `RESPONSE_BODY` timeout stage instead of reporting cancellation,
  then permits replacement and a subsequent hit.
- Existing `RetryRedirectAuthReplayCompositionContractTest` remains the direct
  repeatability, application-owned body, response-header, classified-failure,
  and terminal-error reset proof. `BoundedLocalResponseCacheContractTest`
  remains the shared-load deadline and detached-caller isolation proof.
- Focused composition verification: **147 tests**, all passing
  (`SemanticReadReplayTimeoutContractTest`,
  `SemanticReadSingleFlightRefreshContractTest`,
  `BoundedLocalResponseCacheContractTest`,
  `RetryRedirectAuthReplayCompositionContractTest`, and
  `ReactiveHttpClientFactoryBeanDiagnosticsTest`).
- Full starter verification: **1,258 tests**, all passing.

---

## Priority 9 - Terminal Diagnostics, Metrics, and Support Output

### [x] 9.1 Keep terminal facts aligned across verbs

- [x] Preserve the existing bounded cache outcome vocabulary without adding
      verb-specific outcomes.
- [x] Keep one lifecycle, observer, exchange-log, Micrometer, and OTel terminal
      result per caller subscription.
- [x] Keep hits at `attemptCount=0` and `requestDispatched=false`; keep miss and
      refresh evidence scoped to their final load only.
- [x] Assert cancellation, timeout, auth failure, admission rejection, decode
      error, and successful hit/miss facts across GET and POST query methods.

### [x] 9.2 Export semantic intent without sensitive material

- [x] Add bounded policy source, resolved HTTP method, and semantic-read
      acknowledgement to effective contracts and provider-backed diagnostics.
- [x] Preserve null/unknown when lazy or replacement components make a fact
      unprovable without initialization.
- [x] Keep collection-backed compatibility snapshot overloads from inventing
      false semantic-read values.
- [x] Reject request/body/key/header/tenant/identity material from JSON,
      Markdown, Actuator, logs, OTel, and support fixtures.

### [x] 9.3 Preserve cache meter and health contracts

- [x] Keep cache meter names, types, units, tag keys, and zero-series behavior
      stable unless a versioned schema change is explicitly approved.
- [x] Keep cache metrics separately opt-in and absent for unselected or
      metrics-disabled cache policies.
- [x] Keep cache-served callers out of downstream request timers and health
      denominators while recording cache-specific outcomes.
- [x] Verify meter removal on factory destruction and correct registration after
      destroy/recreate against a live registry.

Completion evidence (2026-08-29):

- `SemanticReadReplayTimeoutContractTest` now drives a body-bearing semantic
  `POST` through decode failure, successful miss, and fresh hit while asserting
  exactly one lifecycle, custom-observer, and exchange-log terminal record per
  caller. The hit retains `attemptCount=0`, no status or dispatched URL, and no
  ordinary downstream timer sample; the two dispatched miss loads remain in that
  timer and cache-specific caller counters retain the existing outcome vocabulary.
- The same semantic `POST` suite covers pre-dispatch auth cancellation, logical
  timeout, admission rejection, auth replay, Retry, redirect, refresh failure,
  and response-body timeout. `BoundedLocalResponseCacheContractTest` remains the
  corresponding production-path `GET` terminal, detached-waiter, cancellation,
  timeout, auth, and hit/miss evidence.
- `OpenTelemetryHttpClientObserverTest` proves one semantic `POST` cache-hit span
  with bounded `FRESH_HIT`, `POST`, and zero-attempt attributes and no cache-key,
  tenant, authorization, or value material.
- Effective contracts already export resolved `httpMethod`, cache source, and
  `semanticRead`. Provider-backed schema-v1 snapshots now add sorted bounded
  `cachePolicySources`, `cacheHttpMethods`, and
  `cacheSemanticReadAcknowledged`; replacement factories and collection-backed
  overloads emit `null` rather than false facts. Map, JSON, Markdown, Actuator,
  the current schema fixture, and the aggregate support fixture share that contract.
- `LocalResponseCacheObservabilityTest` preserves exact meter names, types,
  units, bounded tag keys, deterministic zero series, separate opt-in behavior,
  cache-served downstream-timer exclusion, complete meter removal, and clean
  destroy/recreate registration against one live registry.
- Focused starter verification: **187 tests**, all passing
  (`SemanticReadReplayTimeoutContractTest`,
  `ReactiveHttpClientDiagnosticsProviderTest`,
  `ReactiveHttpClientAutoConfigurationTest`,
  `LocalResponseCacheObservabilityTest`,
  `BoundedLocalResponseCacheContractTest`, and
  `DocumentationReleaseArtifactTest`).
- Focused OTel verification: **29 tests**, all passing
  (`OpenTelemetryHttpClientObserverTest`).
- Full starter verification: **1,259 tests**, all passing. Full OTel verification:
  **53 tests**, all passing.

---

## Priority 10 - Mock, Consumer, AOT, Native, and Lifecycle Parity

### [x] 10.1 Extend `MockReactiveHttpClient`

- [x] Expose the final semantic-read annotation/configuration contract without a
      mock-only bypass.
- [x] Cover GET compatibility, POST JSON hit/miss, unacknowledged failure,
      ordinary uncached write, body variants, auth partition, single flight,
      refresh, eviction, and deterministic time.
- [x] Assert request body bytes and dispatch counts while keeping cache key/value
      internals inaccessible.
- [x] Close cache managers and active load/refresh state for deterministic and
      normal-clock mocks.

### [x] 10.2 Extend assembled consumers

- [x] Add current-reactor Boot 4 consumer cases for the supported non-GET shapes
      without leaking reactor classes into the fixture classpath.
- [x] Add a published `4.0.0` consumer case proving existing GET caching and
      public APIs remain compatible.
- [x] Preserve effective POM, dependency tree, classpath, Surefire reports, and
      stage-aware failure provenance for both consumers.
- [x] Ensure test-helper consumers receive or document every optional runtime
      dependency required by cache-enabled mocks.

### [ ] 10.3 Extend AOT, native, and shutdown evidence

- [x] Register only the final public annotation/configuration additions and
      concrete client method metadata required at runtime.
- [x] Keep application body DTO/Jackson hints under normal application ownership.
- [x] Add native loopback evidence for one acknowledged POST JSON miss and hit
      with exactly one server dispatch.
- [x] Observe a bounded quiet period before accepting zero dispatch on hits.
- [x] Prove native diagnostics contain semantic intent but no request body or key
      material.
- [ ] Run native compile/executable evidence from a clean committed tree and
      record commit, toolchain, binary hash, and reports.

Implementation evidence (2026-08-29):

- `MockReactiveHttpClientTest` now exercises semantic POST JSON body identity,
  exact recorded body bytes, hit/miss dispatch counts, ordinary uncached writes,
  single flight, refresh, eviction, deterministic time, auth partitioning, and
  unacknowledged-method rejection through the public mock builder. Deterministic
  refresh and existing normal-clock load shutdown cases both prove cancellation.
- `scripts/verify-current-consumer.sh` activates the V28 fixture for tests and all
  generated evidence. A fresh isolated run passed **60 mock tests** and **6 Boot 4
  consumer tests**, retained effective POM/tree/classpath/Surefire/stage
  provenance, rejected reactor output directories, and verified transitive
  Caffeine for cache-enabled test-helper consumers.
- `SemanticReadSingleFlightRefreshContractTest` now waits for the leader source
  subscription and two actual in-flight members before cancellation, failure,
  or empty completion. It no longer uses cumulative cache-meter values as a
  scheduler barrier. The formerly flaky method passed **50/50** isolated Maven/
  Surefire stress runs and the complete six-test contract class.
- `scripts/verify-published-consumer.sh 4.0.0` activates the V27 compatibility
  fixture for the complete Maven invocation. A fresh Maven Central run passed
  **4 consumer tests** and retained matching effective POM, dependency tree,
  classpath, Surefire, stage, and published-artifact provenance.
- `ReactiveHttpClientAotSmokeTest`: **21 tests**, all passing, including inherited
  semantic-read metadata, resolved `@ApiRef` metadata, replacement metadata-cache
  behavior, foreign factory exclusion, and concrete method/record hints.
- The native fixture owns its `NativeQueryRequest` reflection binding, sends one
  acknowledged JSON `POST`, proves miss/hit equality with one dispatch and a
  100 ms quiet period, and rejects body markers and cache-key material from the
  diagnostics snapshot. JVM packaging/execution and GraalVM native
  compile/execution passed with GraalVM **25.0.3**; the functional native binary
  SHA-256 was
  `83d38a3d1078e309a056af6a46a48c762737352a6b0744a1ec2529d0a699402b`.
- The native run above used the uncommitted Priority 10 tree. It validates
  functionality but intentionally does not satisfy the remaining immutable
  clean-commit provenance checkbox.

---

## Priority 11 - Security and Operations Review

### [x] 11.1 Define the application safety review

- [x] Document that a false semantic-read declaration can suppress a required
      action or share data across callers.
- [x] Require endpoint-owner approval for non-GET selection, including side
      effects, body determinism, response variants, auth/tenant partition, TTL,
      refresh, and invalidation ownership.
- [x] State that idempotency, Retry configuration, HTTP status, method naming,
      and `Cache-Control` do not authorize local response reuse.
- [x] Keep ordinary writes, commands, payments, job submissions, and mutation
      examples explicitly unselected.

### [x] 11.2 Update operational diagnosis

- [x] Distinguish cache-hit suppression from single flight, refresh, Resilience4j
      retry, redirect, auth replay, Reactor Netty retry, and downstream duplicate
      handling.
- [x] Document local per-instance behavior, rolling configuration differences,
      hard expiry, refresh failure, capacity pressure, and lack of distributed
      coherence.
- [x] State that the starter performs no automatic invalidation after writes and
      offers no write-through/write-behind semantics.
- [x] Add sanitized support-bundle capture fields for resolved verb, bounded
      semantic-read state, cache outcome, attempt count, and dispatch evidence.

### [x] 11.3 Keep examples safe and copyable

- [x] Use catalog search and RPC query examples with `.example.invalid` hosts.
- [x] Include complete cache dependency, policy, customization-safety, auth/
      tenant partition, and observability prerequisites.
- [x] Keep credentials in environment placeholders and omit real request bodies,
      headers, cache keys/digests, identities, and tenant values.
- [x] Add documentation guards for side-effecting examples and sensitive fixture
      fields.

Implementation evidence (2026-08-29):

- `docs/32-response-caching.md` now requires endpoint-owner approval across side
  effects, deterministic body identity, response variants, auth/tenant
  partition, TTL, refresh, and invalidation ownership. Payment, job, command,
  and mutation examples remain explicitly `@CacheDisabled`.
- `docs/30-operations-troubleshooting.md` separates cache suppression, single
  flight, hidden refresh, Retry, redirect, auth replay, disabled Reactor Netty
  retry, and downstream deduplication while retaining local-only hard-expiry,
  capacity, rollout, and invalidation boundaries.
- `docs/26-support-bundles.md` and its aggregate fixture include only bounded
  resolved-method, semantic-read, cache-outcome, attempt, and dispatch facts;
  the recursive fixture guard continues to reject request/key/body/header,
  identity, tenant, credential, value, and payload material.
- `docs/examples/effective-configuration.md` provides catalog-search and RPC-query
  examples with Caffeine, complete policy/isolation/customization/observability
  prerequisites, `.example.invalid` hosts, and environment-only credentials.
- Focused documentation verification: **62 tests**, all passing
  (`DocumentationReleaseArtifactTest`: 44;
  `ReactiveHttpClientConfigurationMetadataTest`: 18).
- Full starter verification: **1,261 tests**, all passing.

---

## Priority 12 - Performance and Allocation Re-Audit

### [x] 12.1 Extend benchmark classification and harness coverage

- [x] Keep disabled-cache and existing GET rows comparable with published
      `4.0.0`.
- [x] Add separately classified no-network and loopback POST JSON query rows for
      miss, hit, coalesced waiter, and refresh.
- [x] Ensure compared implementations perform equivalent serialization, keying,
      cache lookup, and network work; do not compare a local hit to a network
      call as abstraction overhead.
- [x] Keep smoke rows and release-quality rows in separate paths/classifications.

### [x] 12.2 Audit allocations and retention

- [x] Measure bounded body serialization, opaque key derivation, hit, miss,
      waiter, refresh, eviction, and cancellation paths.
- [x] Confirm unselected non-GET methods gain no request-path cache allocation.
- [x] Use JFR/allocation evidence to verify body bytes, frozen arguments, flight
      state, auth context, and cache entries are released at their ownership
      boundaries.
- [x] Record any accepted overhead with workload shape and methodology; do not
      optimize without a measured regression.

### [x] 12.3 Prepare release benchmark evidence

- [x] Run the published `4.0.0` baseline from a previously absent isolated Maven
      repository and record provenance.
- [x] Run the current release-quality JMH command from a clean candidate commit.
- [x] Compare current and baseline JSON reports with stable row classification.
- [x] Promote a source-controlled report only if release notes make a public
      performance claim; otherwise record explicit deferral.

Implementation evidence (2026-08-30):

- `V28SemanticReadCachePerformanceBenchmark` adds bounded JSON body/key,
  no-network semantic `POST` miss/hit/waiter/cancellation/refresh, and
  authenticated loopback miss/hit/waiter/refresh rows. Every loopback row
  asserts its expected dispatch delta and response body; the auth fixture also
  asserts that it receives the serialized body bytes used by the request.
- Benchmark classification keeps disabled controls, no-network allocation work,
  no-network semantic `POST` work, and starter cache loopback work separate.
  The final smoke run produced **40 rows / 20 methods** across throughput and
  average-time modes under
  `target/release-evidence/v28/priority12/smoke-final/`. Focused benchmark and
  report verification passed **19 tests**.
- Release-profile allocation evidence isolates the existing cache internals:
  fresh hit approximately **0 B/op**, miss token **96 B/op**, loader publication
  **344 B/op**, waiter **2,137 B/op**, refresh **2,195 B/op**, size eviction
  **192 B/op**, and semantic-POST cancelled flight **37,327 B/op** in
  average-time mode. The complete semantic-POST audit separately measures body
  serialization/keying, hit, miss, waiter, and refresh at approximately
  **35,870-38,163 B/op** for the bounded record workload.
- `V28SemanticReadCachePerformanceBenchmarkTest` verifies completion, eviction,
  expiry, cancellation, and shutdown release in-flight load/refresh maps while
  retaining only the eligible response entry. A six-second JFR contains **1,630
  allocation samples** and **23 old-object samples**; allocation is concentrated
  in bounded JSON/key framing, while old-object candidates are JMH, Jackson,
  Reactor Netty, and JVM infrastructure rather than body snapshots, cache keys,
  flight state, or cache entries. Evidence is under
  `target/release-evidence/v28/priority12/jfr/`.
- Unselected invocations now bypass cache-policy resolution when neither the
  method nor client selects caching. The focused behavior test proves the
  decision cache remains empty for an ordinary `POST`; **126 cache/invocation
  contract tests** pass. The final release-profile controls compare cleanly with
  published `4.0.0`: cache-disabled `POST` average time is **-1.96%** with
  **+8 B/op**, and existing `GET` average time is **+4.55%** with **+104 B/op**;
  every control comparison remains below review thresholds. Complete starter
  verification passed **1,262 tests**.
- The selected V27 loopback `GET` audit triggered review at approximately
  **+112% hit latency / +70.5% hit allocation** and **+43.2% miss latency /
  +44.7% miss allocation**. This is accepted for the audited workload because
  V28 must run the non-dispatching finalized-request probe so `defaultRequest`,
  auth, URI, header, and customizer mutations participate in cache identity.
  Skipping that work would violate the isolation contract. The local hit remains
  classified only against equivalent cache work, never a raw network call.
- Published `4.0.0` evidence contains **8 rows / 4 methods** from the previously
  absent isolated repository
  `target/published-baseline-repositories/benchmark-v28-release-4.0.0`.
  Provenance records Maven Central markers plus starter POM SHA-256
  `ee0a3daba0da3eb889755b9e17b79cec16bcc2d410ff13ec245f9816304f7fa1`
  and jar SHA-256
  `b11cd096f51d9da5f9c7ffe0f0de7478d1fde86f7383595332489bb039c6e2a8`.
  Dirty-tree release audits and comparisons are retained under
  `target/release-evidence/v28/priority12/` as non-promotable diagnostics.
- Clean-candidate release evidence was produced from commit
  `e8ba2dadbc112089bf2d32628b7470f6ba0d0266` with GraalVM/JDK **25.0.3**. The
  primary report contains **32 rows / 16 methods**; a targeted release-quality
  supplement contains the four semantic-POST loopback methods as **8 rows**, for
  **40 rows / 20 methods** in total. The JSON SHA-256 values are
  `778d223230ba065912cb6f19225dea7c79e2bc645aeaa681d5fc397d7aa5d865`
  and `190da3c2b04530e8b03117ab334081cb8a6d5ae6dca239239b46d89e81266567`.
- The clean comparison SHA-256 is
  `5e7e0f46853f2540e38bdba021328c722d95e4af03bec2c9f22de08720951704`.
  All disabled-cache control rows remain below review thresholds. The expected
  V27 loopback `GET` probe overhead remains review-classified and accepted for
  the isolation reason recorded above.
- The current changelog makes no numerical performance claim, so report
  promotion into `docs/` is explicitly deferred.

---

## Priority 13 - Public API, Documentation, and Compatibility Evidence

### [x] 13.1 Freeze public and generated contracts

- [x] Include the final annotation member/configuration/test-helper additions in
      japicmp coverage and the documented public-surface inventory.
- [x] Prove existing compiled and source 4.0 cache clients remain compatible.
- [x] Regenerate configuration metadata/reference, effective configuration,
      effective-contract snapshots, diagnostics fixtures, Javadoc, and source
      artifacts.
- [x] Keep Caffeine and implementation/runtime types out of public signatures
      unless deliberately accepted as extension APIs.

### [x] 13.2 Consolidate cache and migration guidance

- [x] Open the cache guide with existing explicit GET selection and the statement
      that GET-friendly is not automatic.
- [x] Add one POST JSON search and one RPC query using the final semantic-read
      acknowledgement and complete body/variant isolation.
- [x] State that client-wide policy alone cannot acknowledge non-GET methods and
      that an unacknowledged selected method fails startup.
- [x] Cross-link retry/idempotency, auth, redirects, timeouts, observability,
      production checklist, troubleshooting, support bundles, AOT/native, and
      migration guidance.
- [x] Remove any wording that implies all POST/PUT/PATCH/DELETE methods are safe
      or that caching suppresses duplicate writes.

### [x] 13.3 Assemble compatibility and packaging evidence

- [x] Run strict root and starter API comparison against isolated published
      `4.0.0` artifacts and record provenance.
- [x] Run API compatibility and published-baseline fixture guards.
- [x] Run generated metadata/docs checks, source/Javadoc/binary packaging guard,
      current/published consumers, supported matrix, AOT, native, and
      `git diff --check`.
- [x] Record exact commands, versions, commit, checksums, test totals, and any
      manual/deferred evidence under `target/release-evidence/v28/`.

Implementation evidence recorded on 2026-08-30:

- Strict root and starter-only japicmp comparisons pass against separate fresh
  Maven Central `4.0.0` repositories. The API fixture compiles an existing
  annotation use against both API forms and runs the old binary with the new
  false default; all other abstract-method additions remain strict. Reports,
  Central markers, and checksums are under
  `target/release-evidence/v28/priority13/api-root-4.0.0/` and
  `target/release-evidence/v28/priority13/api-starter-4.0.0/`.
- Java 21 supported-matrix rows for Spring Boot `4.0.0` and `4.1.0` each pass
  `1,377` reactor tests with no failures, including `44` release-documentation
  tests and the AOT suite. Current `4.1.0-SNAPSHOT` consumers pass `60` mock and
  `6` assembled tests; the independent published `4.0.0` consumer passes from
  Central-only artifacts. Binary, source, Javadoc, metadata, effective-contract,
  and diagnostics artifacts pass the generation-packaging guard.
- The GraalVM `25.0.3` native image compiles and executes successfully against
  Spring Boot `4.0.0`; its SHA-256 and dependency list are recorded with the
  API, fixture, matrix, package, command, and source-state evidence under
  `target/release-evidence/v28/priority13/`. This development proof records HEAD
  `422b85590fe1617bb2a93d6b2a633d699245b59e` with a dirty source state; the
  immutable clean-candidate rerun remains a Priority 14 release gate.

---

## Priority 14 - V28 / `4.1.0` Go-No-Go

### [x] 14.1 Select release scope and candidate version

- [x] Confirm the final public change is additive and existing GET behavior is
      unchanged.
- [x] Confirm every non-GET cache path requires method/API-specific semantic-read
      intent and bounded wire-equivalent request identity.
- [x] Select `4.1.0` only when all supported body shapes and composition cases are
      proven; otherwise narrow scope or record a no-go.
- [x] Keep unrelated API, metric-schema, distributed-cache, invalidation, and
      write-caching work out of the candidate.
- [x] Update changelog and release notes without claiming the candidate is
      published.

Scope decision recorded on 2026-08-30:

- Select `4.1.0` as the unpublished additive-minor candidate. The only Java API
  addition is the false-defaulted, method-scoped `CacheResponse.semanticRead()`
  contract; compiled and source `4.0.0` clients retain explicit `GET` behavior.
- The supported non-`GET` scope is finite `Mono<T>`/`Mono<ResponseEntity<T>>`
  semantic reads with bounded JSON, `String`, or `byte[]` body identity. Every
  selected method/API must declare its own intent, and body, target, header, auth,
  tenant, and context variants remain wire-equivalent and bounded.
- Priorities 3-10 prove storage, response eligibility, single flight, refresh,
  Retry, redirects, auth replay, timeouts, terminal diagnostics, metrics, mocks,
  assembled consumers, AOT, and functional native parity for that scope. The
  clean immutable native/evidence rerun remains in 14.2.
- Distributed caching, automatic invalidation, write-through/write-behind,
  ordinary write caching, metric-schema changes, and unrelated APIs remain out
  of scope. `CHANGELOG.md` keeps the release under `[Unreleased]`, states that
  `4.1.0` is unpublished, and makes no public performance claim.

### [ ] 14.2 Assemble immutable release evidence

- [ ] Run the complete reactor on the supported Java/Spring Boot matrix from a
      clean candidate commit.
- [ ] Pass strict root/module API checks, baseline provenance, dependency review,
      package guard, current/published consumers, AOT, native, shutdown, docs,
      and support fixtures.
- [ ] Complete benchmark/allocation evidence or record a no-public-claim
      deferral.
- [ ] Verify generated readiness lists every unresolved manual command and
      artifact before the release decision.
- [ ] Record one explicit go/no-go decision with candidate version, commit, date,
      evidence paths, and remaining risk.

### [ ] 14.3 Publish and transition the baseline

- [ ] On go, cut the final version only from the reviewed clean commit and run the
      publish workflow with generation-packaging checks.
- [ ] Verify parent, starter, test-helper, and OTel POM/JAR/source/Javadoc
      artifacts from fresh Maven Central repositories.
- [ ] Run an assembled consumer using only the published candidate artifacts.
- [ ] Move public snippets, latest-published/API/consumer/benchmark baselines,
      reactor snapshot, changelog, roadmap index, and readiness state only after
      Central verification.
- [ ] Mark V28 complete only after post-publication evidence is recorded; on
      no-go, keep the snapshot unpublished and document the blocking contract.
