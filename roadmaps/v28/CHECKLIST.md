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

### [ ] 1.1 Align development, published, and roadmap lanes

- [ ] Keep root/module and reactor-only fixture coordinates on
      `4.1.0-SNAPSHOT`.
- [ ] Keep public dependency snippets and `latest.published.version` on
      published `4.0.0`.
- [ ] Keep API compatibility, published-consumer, and benchmark baselines on
      published `4.0.0`.
- [ ] Keep V28 as the only active execution roadmap without rewriting completed
      V1-V27 release evidence.
- [ ] Record `4.1.0` only as a candidate direction; do not advertise it as
      released before publication verification.

### [ ] 1.2 Prove the published `4.0.0` baseline

- [ ] Resolve the parent POM plus starter, test-helper, and OTel POM/JAR/source/
      Javadoc artifacts from a previously absent Central-only repository.
- [ ] Require Maven Central remote markers and record SHA-256 values for every
      required artifact.
- [ ] Run strict root japicmp against published `4.0.0` from a fresh repository.
- [ ] Run strict starter-module japicmp against published `4.0.0` from a separate
      fresh repository.
- [ ] Run published-baseline fixtures for local contamination, mixed versions,
      missing attachments, mismatched POM/JAR versions, and self-comparison.

### [ ] 1.3 Keep generated readiness honest

- [ ] Report `4.1.0-SNAPSHOT` as development and `4.0.0` as the latest
      published/API baseline.
- [ ] Keep the final candidate, benchmark promotion, and publication work
      deferred until V28 release preparation.
- [ ] Include every unfinished semantic-read, key/body, composition, consumer,
      native, benchmark, API, and documentation priority in pending readiness.
- [ ] Run `DocumentationReleaseArtifactTest`, Maven validation, and
      `git diff --check`; record commands and totals under this priority.

---

## Priority 2 - Semantic-Read Opt-In Contract

### [ ] 2.1 Characterize the published `4.0.0` behavior

- [ ] Add focused tests proving client- and method-selected GET caching remains
      explicitly opt-in.
- [ ] Prove selected POST, PUT, PATCH, DELETE, OPTIONS, HEAD, and non-GET
      `@ApiRef` methods currently fail the fixed-GET eligibility guard.
- [ ] Cover client-wide policies on mixed GET/non-GET interfaces,
      `@CacheDisabled`, inherited methods, overloads, and method-level policy
      precedence.
- [ ] Record current startup messages, effective-contract output, diagnostics,
      AOT, and mock behavior before changing the grammar.
- [ ] Freeze the existing return/body eligibility and cache outcome behavior so
      verb support cannot broaden unrelated cache shapes.

### [ ] 2.2 Freeze the public semantic-read acknowledgement

- [ ] Select one additive, method/API-specific public spelling for semantic-read
      intent before implementing runtime support.
- [ ] Define the annotation/configuration default as absent or false so compiled
      and source 4.0 clients retain GET-only behavior.
- [ ] Define exact precedence among method policy, API-specific configuration,
      client-wide policy, semantic-read acknowledgement, and `@CacheDisabled`.
- [ ] Prevent a generic client-wide policy or method-name pattern from
      acknowledging every non-GET endpoint.
- [ ] Document the declaration as an application guarantee that a cache hit may
      suppress downstream dispatch without omitting a required side effect.
- [ ] Add Javadoc, configuration metadata, effective examples, native hints, and
      public API inventory entries for the final shape.

### [ ] 2.3 Enforce the selection matrix

- [ ] Preserve selected GET behavior without requiring the new acknowledgement.
- [ ] Reject a client-policy-selected non-GET method that has no method/API
      acknowledgement.
- [ ] Reject a method-policy-selected non-GET method that has no acknowledgement.
- [ ] Accept an acknowledged non-GET method only after all later response, body,
      key, auth, and customization checks pass.
- [ ] Keep unselected and `@CacheDisabled` methods on the ordinary request path.
- [ ] Include client name, declaring/concrete method, resolved verb, policy name/
      source, and correction in startup errors without printing request data.

---

## Priority 3 - Verb-Independent Declarative Eligibility Grammar

### [ ] 3.1 Centralize effective cache eligibility

- [ ] Replace the fixed `GET` rejection with one package-private decision that
      represents disabled, GET-friendly selected, acknowledged semantic read,
      and invalid selection.
- [ ] Resolve annotation and `@ApiRef` verbs through the concrete client and
      inherited generic method before deciding eligibility.
- [ ] Use the same decision in factory startup, invocation, effective-contract
      export, diagnostics, AOT, and `MockReactiveHttpClient`.
- [ ] Remove duplicated verb checks made obsolete by the effective decision.
- [ ] Keep foreign/replacement `FactoryBean` clients and replacement metadata
      caches outside starter-only validation where established contracts require
      it.

### [ ] 3.2 Preserve finite response and owned-request boundaries

- [ ] Accept only finite materialized `Mono<T>` and
      `Mono<ResponseEntity<T>>` response shapes already supported by V27.
- [ ] Keep `Flux`, raw/unresolved values, `Mono<Void>`, bodiless envelopes,
      nested publishers, `DataBuffer`, `Resource`, and streaming responses
      rejected.
- [ ] Keep multipart, form streams, publishers, `DataBuffer`, `Resource`,
      `InputStream`, `Reader`, channels, and other application-owned request
      bodies rejected.
- [ ] Prove valid JSON, `String`, and `byte[]` body-bearing semantic reads across
      supported non-GET verbs.
- [ ] Prove idempotency keys, retry annotations, response status, and method names
      cannot substitute for semantic-read intent.

### [ ] 3.3 Verify every declaration path

- [ ] Cover direct, inherited, multi-level generic, overloaded, bridge-method,
      factory-method, and `@ApiRef` clients.
- [ ] Cover annotation-only, client-policy plus method acknowledgement,
      API-configured, disabled, missing-policy, and blank-policy cases.
- [ ] Assert proxy construction fails before auth, body serialization/resource
      acquisition, lifecycle attempt hooks, cache allocation, or dispatch.
- [ ] Run focused grammar, factory, diagnostics, contract-export, AOT, and mock
      suites before moving to key construction.

---

## Priority 4 - Body-Bearing Request Identity

### [ ] 4.1 Inventory preparation, key, auth, and wire ownership

- [ ] Trace JSON, `String`, `byte[]`, null, and present-empty bodies from method
      arguments through cache preparation, auth materialization, and WebClient
      writing.
- [ ] Record every point where content type, charset, codecs, customizers, or
      filters can change the effective bytes.
- [ ] Identify and remove any second serialization or body copy introduced only
      by non-GET cache eligibility.
- [ ] Define the lifetime and owner of frozen arguments and prepared bytes for
      hit, miss, waiter, refresh, timeout, cancellation, eviction, and shutdown.

### [ ] 4.2 Derive one wire-equivalent body identity

- [ ] Include every supported body-bearing non-GET request's effective serialized
      body bytes in cache identity by default.
- [ ] Reuse one bounded byte representation for cache identity, built-in signing,
      and the final request writer where the existing contract supports it.
- [ ] Represent effective `Content-Type` and charset whenever they can change the
      bytes or writer selected for the request.
- [ ] Keep null body, present zero-length body, empty string, empty JSON value,
      and absent/explicit content type distinct when their requests differ.
- [ ] Reject a body/customizer/codec combination whose final bytes cannot be
      proven before lookup.
- [ ] Do not use `shared-response` as an implicit waiver for omitted non-GET body
      identity; defer omission unless a separate reviewed acknowledgement is
      implemented.

### [ ] 4.3 Enforce bounded preparation and cleanup

- [ ] Apply cumulative byte, element, depth, and projection limits before large
      values or encodings allocate beyond the existing key-material cap.
- [ ] Test large strings, byte arrays, records, numbers, nested containers,
      shared graphs, null/empty boundaries, and serialization-limit failures.
- [ ] Release prepared body bytes and frozen argument graphs after terminal
      completion and never retain them in cache entries or completed flights.
- [ ] Prove cancellation/timeout before request-body subscription releases all
      cache-owned preparation state and publishes no entry.
- [ ] Verify no body/key material appears in exception messages, diagnostics,
      logs, metrics, OTel attributes, or support fixtures.

---

## Priority 5 - Request Target, Headers, Auth, and Tenant Isolation

### [ ] 5.1 Preserve complete request identity

- [ ] Include concrete client identity, resolved method signature, request target,
      body identity, and selected header/context variants in the opaque key.
- [ ] Preserve bounded, wire-equivalent path/query/header projections and their
      order, null, type, and framing distinctions.
- [ ] Prove different methods, verbs, bodies, media types, tenants, locales, API
      versions, and explicit variants cannot collide.
- [ ] Keep raw and hashed key values private to cache lookup and absent from all
      public/support surfaces.

### [ ] 5.2 Preserve per-caller authorization and partitioning

- [ ] Run pre-lookup auth and required policy gates for every hit and miss.
- [ ] Validate auth headers on hits with the same rules as ordinary dispatch.
- [ ] Require authenticated responses to have a real auth/tenant partition or an
      explicit shared-response acknowledgement; an absent idempotency header is
      not a partition.
- [ ] Apply the frozen Reactor-context snapshot to auth, variants, lookup, and
      load so mutation cannot authorize one identity and key another.
- [ ] Prove token refresh, different principals, and context-provided headers do
      not cross cache entries.

### [ ] 5.3 Revalidate customization safety

- [ ] Reuse the complete Boot/per-client WebClient customization inventory for
      every newly eligible method.
- [ ] Keep unclassified or incompatible filters, `defaultRequest`, exchange
      functions, codecs, connectors, and response transformations startup-fatal.
- [ ] Require customizations that mutate method, target, body, content type, or
      variants to contribute equivalent pre-lookup facts or remain incompatible.
- [ ] Cover parent/child contexts, ordered customizers, replacement builders,
      lazy beans, and mock-installed customizations without creating lazy beans
      from diagnostics.

---

## Priority 6 - Local Cache and Response Semantics Across Verbs

### [ ] 6.1 Route acknowledged semantic reads through V27 storage

- [ ] Reuse the existing cache manager, policy bounds, monotonic TTL, size
      eviction, and generation-checked publication path.
- [ ] Preserve first-successful-fill-wins behavior for duplicate same-key misses
      when single flight is disabled.
- [ ] Prove GET and acknowledged non-GET calls have identical hit, miss, expiry,
      eviction, replacement, and shutdown behavior.
- [ ] Keep a hit free of downstream resilience admission, redirect, pool, and
      transport dispatch after mandatory pre-lookup gates.

### [ ] 6.2 Preserve response eligibility and header safety

- [ ] Store only fully decoded successful non-null emissions.
- [ ] Keep errors, mapped 4xx/5xx, redirects, cancellation, and empty completion
      non-cacheable for plain and envelope responses.
- [ ] Inspect wire response headers before storing plain bodies and
      `ResponseEntity<T>` values.
- [ ] Keep sensitive, configured non-cacheable, auth-challenge, cookie, and
      per-caller response headers out of entries.
- [ ] Preserve only the established bounded representation-header allowlist on
      cached envelopes.

### [ ] 6.3 Preserve value and lifecycle ownership

- [ ] Keep the documented shared object-identity behavior; do not clone or
      serialize cached values solely because the verb is non-GET.
- [ ] Prevent in-flight loads from repopulating after explicit eviction, expiry,
      cache close, or factory destruction.
- [ ] Recheck cache closure and generation under the same synchronization used
      for cache/flight publication.
- [ ] Add deterministic tests for late completion, concurrent eviction,
      capacity pressure, and close/recreate with the same policy and meters.

---

## Priority 7 - Single Flight and Refresh Composition

### [ ] 7.1 Extend single flight to body-bearing semantic reads

- [ ] Coalesce only callers with the same complete opaque request identity.
- [ ] Prove different body bytes, content types, headers, contexts, methods,
      clients, and policies create independent flights.
- [ ] Share one load and one request-body subscription while keeping each caller's
      timeout, cancellation, and terminal state independent.
- [ ] Recheck the cache before creating a flight and prevent removed shared
      publishers from reconnecting as detached loads.
- [ ] Keep coalesced waiters at zero transport attempts/evidence even if the
      original leader detaches and load ownership transfers internally.

### [ ] 7.2 Extend refresh-on-access safely

- [ ] Require the same semantic-read acknowledgement and request-identity proof
      for refresh as for a miss.
- [ ] Build refresh from the triggering invocation's fresh frozen request rather
      than retaining a prior body publisher, auth context, or argument graph.
- [ ] Keep one refresh flight per key with a finite refresh timeout and hard TTL/
      shutdown bound.
- [ ] Preserve the current value after refresh failure only until hard expiry;
      late refresh completion cannot repopulate a newer generation.
- [ ] Record refresh success, failure, and cancellation exactly once, including
      cancellation before source-subscription attachment.

### [ ] 7.3 Add deterministic concurrency evidence

- [ ] Cover first-caller timeout with a later waiter succeeding and waiter
      timeout with the leader succeeding.
- [ ] Cover last-waiter cancellation, load error, empty completion, body
      serialization failure, hard expiry, eviction during refresh, and shutdown.
- [ ] Use latches/probes and body-subscription/dispatch counts rather than
      timing-only assertions.

---

## Priority 8 - Retry, Redirect, Auth Replay, and Timeout Boundaries

### [ ] 8.1 Keep cacheability separate from retry and replay safety

- [ ] Preserve the configured `retry-methods` eligibility and established
      Resilience4j operator order.
- [ ] Keep strict unsafe-retry validation active when a selected Retry can issue
      another non-safe HTTP attempt; semantic-read intent is not proof of an
      idempotency key.
- [ ] Keep body-preserving redirect and one-time auth replay behind the existing
      repeatability checks.
- [ ] Prove a cached method with retry disabled performs no implicit transport
      retry introduced by cache code.
- [ ] Cover cache miss/refresh with retry success/failure, redirect, auth 401
      replay, and pre-dispatch admission rejection.

### [ ] 8.2 Preserve credentials and per-attempt request state

- [ ] Consume pre-resolved auth only for the intended first outer attempt and
      resolve current auth on later resilience attempts.
- [ ] Reset URL, status, headers, error, failure stage, dispatch evidence, body
      size, and attempt facts between retries/auth replay/redirects.
- [ ] Keep refresh hidden from the stale caller while exposing its independent
      aggregate terminal result.
- [ ] Assert final diagnostics never reuse a previous attempt's body, auth,
      response headers, or classified failure.

### [ ] 8.3 Preserve one logical deadline per caller

- [ ] Include body preparation, auth, lookup, waiter attachment, and load waiting
      in the logical-call deadline.
- [ ] Avoid nested equal-duration timeouts that erase response phase attribution
      or convert timeout into cancellation diagnostics.
- [ ] Keep shared-load request/response timeouts independent from each caller's
      outer timeout while another caller remains interested.
- [ ] Prove cancellation and timeout before dispatch release prepared bytes and
      cannot publish cache state.

---

## Priority 9 - Terminal Diagnostics, Metrics, and Support Output

### [ ] 9.1 Keep terminal facts aligned across verbs

- [ ] Preserve the existing bounded cache outcome vocabulary without adding
      verb-specific outcomes.
- [ ] Keep one lifecycle, observer, exchange-log, Micrometer, and OTel terminal
      result per caller subscription.
- [ ] Keep hits at `attemptCount=0` and `requestDispatched=false`; keep miss and
      refresh evidence scoped to their final load only.
- [ ] Assert cancellation, timeout, auth failure, admission rejection, decode
      error, and successful hit/miss facts across GET and POST query methods.

### [ ] 9.2 Export semantic intent without sensitive material

- [ ] Add bounded policy source, resolved HTTP method, and semantic-read
      acknowledgement to effective contracts and provider-backed diagnostics.
- [ ] Preserve null/unknown when lazy or replacement components make a fact
      unprovable without initialization.
- [ ] Keep collection-backed compatibility snapshot overloads from inventing
      false semantic-read values.
- [ ] Reject request/body/key/header/tenant/identity material from JSON,
      Markdown, Actuator, logs, OTel, and support fixtures.

### [ ] 9.3 Preserve cache meter and health contracts

- [ ] Keep cache meter names, types, units, tag keys, and zero-series behavior
      stable unless a versioned schema change is explicitly approved.
- [ ] Keep cache metrics separately opt-in and absent for unselected or
      metrics-disabled cache policies.
- [ ] Keep cache-served callers out of downstream request timers and health
      denominators while recording cache-specific outcomes.
- [ ] Verify meter removal on factory destruction and correct registration after
      destroy/recreate against a live registry.

---

## Priority 10 - Mock, Consumer, AOT, Native, and Lifecycle Parity

### [ ] 10.1 Extend `MockReactiveHttpClient`

- [ ] Expose the final semantic-read annotation/configuration contract without a
      mock-only bypass.
- [ ] Cover GET compatibility, POST JSON hit/miss, unacknowledged failure,
      ordinary uncached write, body variants, auth partition, single flight,
      refresh, eviction, and deterministic time.
- [ ] Assert request body bytes and dispatch counts while keeping cache key/value
      internals inaccessible.
- [ ] Close cache managers and active load/refresh state for deterministic and
      normal-clock mocks.

### [ ] 10.2 Extend assembled consumers

- [ ] Add current-reactor Boot 4 consumer cases for the supported non-GET shapes
      without leaking reactor classes into the fixture classpath.
- [ ] Add a published `4.0.0` consumer case proving existing GET caching and
      public APIs remain compatible.
- [ ] Preserve effective POM, dependency tree, classpath, Surefire reports, and
      stage-aware failure provenance for both consumers.
- [ ] Ensure test-helper consumers receive or document every optional runtime
      dependency required by cache-enabled mocks.

### [ ] 10.3 Extend AOT, native, and shutdown evidence

- [ ] Register only the final public annotation/configuration additions and
      concrete client method metadata required at runtime.
- [ ] Keep application body DTO/Jackson hints under normal application ownership.
- [ ] Add native loopback evidence for one acknowledged POST JSON miss and hit
      with exactly one server dispatch.
- [ ] Observe a bounded quiet period before accepting zero dispatch on hits.
- [ ] Prove native diagnostics contain semantic intent but no request body or key
      material.
- [ ] Run native compile/executable evidence from a clean committed tree and
      record commit, toolchain, binary hash, and reports.

---

## Priority 11 - Security and Operations Review

### [ ] 11.1 Define the application safety review

- [ ] Document that a false semantic-read declaration can suppress a required
      action or share data across callers.
- [ ] Require endpoint-owner approval for non-GET selection, including side
      effects, body determinism, response variants, auth/tenant partition, TTL,
      refresh, and invalidation ownership.
- [ ] State that idempotency, Retry configuration, HTTP status, method naming,
      and `Cache-Control` do not authorize local response reuse.
- [ ] Keep ordinary writes, commands, payments, job submissions, and mutation
      examples explicitly unselected.

### [ ] 11.2 Update operational diagnosis

- [ ] Distinguish cache-hit suppression from single flight, refresh, Resilience4j
      retry, redirect, auth replay, Reactor Netty retry, and downstream duplicate
      handling.
- [ ] Document local per-instance behavior, rolling configuration differences,
      hard expiry, refresh failure, capacity pressure, and lack of distributed
      coherence.
- [ ] State that the starter performs no automatic invalidation after writes and
      offers no write-through/write-behind semantics.
- [ ] Add sanitized support-bundle capture fields for resolved verb, bounded
      semantic-read state, cache outcome, attempt count, and dispatch evidence.

### [ ] 11.3 Keep examples safe and copyable

- [ ] Use catalog search and RPC query examples with `.example.invalid` hosts.
- [ ] Include complete cache dependency, policy, customization-safety, auth/
      tenant partition, and observability prerequisites.
- [ ] Keep credentials in environment placeholders and omit real request bodies,
      headers, cache keys/digests, identities, and tenant values.
- [ ] Add documentation guards for side-effecting examples and sensitive fixture
      fields.

---

## Priority 12 - Performance and Allocation Re-Audit

### [ ] 12.1 Extend benchmark classification and harness coverage

- [ ] Keep disabled-cache and existing GET rows comparable with published
      `4.0.0`.
- [ ] Add separately classified no-network and loopback POST JSON query rows for
      miss, hit, coalesced waiter, and refresh.
- [ ] Ensure compared implementations perform equivalent serialization, keying,
      cache lookup, and network work; do not compare a local hit to a network
      call as abstraction overhead.
- [ ] Keep smoke rows and release-quality rows in separate paths/classifications.

### [ ] 12.2 Audit allocations and retention

- [ ] Measure bounded body serialization, opaque key derivation, hit, miss,
      waiter, refresh, eviction, and cancellation paths.
- [ ] Confirm unselected non-GET methods gain no request-path cache allocation.
- [ ] Use JFR/allocation evidence to verify body bytes, frozen arguments, flight
      state, auth context, and cache entries are released at their ownership
      boundaries.
- [ ] Record any accepted overhead with workload shape and methodology; do not
      optimize without a measured regression.

### [ ] 12.3 Prepare release benchmark evidence

- [ ] Run the published `4.0.0` baseline from a previously absent isolated Maven
      repository and record provenance.
- [ ] Run the current release-quality JMH command from a clean candidate commit.
- [ ] Compare current and baseline JSON reports with stable row classification.
- [ ] Promote a source-controlled report only if release notes make a public
      performance claim; otherwise record explicit deferral.

---

## Priority 13 - Public API, Documentation, and Compatibility Evidence

### [ ] 13.1 Freeze public and generated contracts

- [ ] Include the final annotation member/configuration/test-helper additions in
      japicmp coverage and the documented public-surface inventory.
- [ ] Prove existing compiled and source 4.0 cache clients remain compatible.
- [ ] Regenerate configuration metadata/reference, effective configuration,
      effective-contract snapshots, diagnostics fixtures, Javadoc, and source
      artifacts.
- [ ] Keep Caffeine and implementation/runtime types out of public signatures
      unless deliberately accepted as extension APIs.

### [ ] 13.2 Consolidate cache and migration guidance

- [ ] Open the cache guide with existing explicit GET selection and the statement
      that GET-friendly is not automatic.
- [ ] Add one POST JSON search and one RPC query using the final semantic-read
      acknowledgement and complete body/variant isolation.
- [ ] State that client-wide policy alone cannot acknowledge non-GET methods and
      that an unacknowledged selected method fails startup.
- [ ] Cross-link retry/idempotency, auth, redirects, timeouts, observability,
      production checklist, troubleshooting, support bundles, AOT/native, and
      migration guidance.
- [ ] Remove any wording that implies all POST/PUT/PATCH/DELETE methods are safe
      or that caching suppresses duplicate writes.

### [ ] 13.3 Assemble compatibility and packaging evidence

- [ ] Run strict root and starter API comparison against isolated published
      `4.0.0` artifacts and record provenance.
- [ ] Run API compatibility and published-baseline fixture guards.
- [ ] Run generated metadata/docs checks, source/Javadoc/binary packaging guard,
      current/published consumers, supported matrix, AOT, native, and
      `git diff --check`.
- [ ] Record exact commands, versions, commit, checksums, test totals, and any
      manual/deferred evidence under `target/release-evidence/v28/`.

---

## Priority 14 - V28 / `4.1.0` Go-No-Go

### [ ] 14.1 Select release scope and candidate version

- [ ] Confirm the final public change is additive and existing GET behavior is
      unchanged.
- [ ] Confirm every non-GET cache path requires method/API-specific semantic-read
      intent and bounded wire-equivalent request identity.
- [ ] Select `4.1.0` only when all supported body shapes and composition cases are
      proven; otherwise narrow scope or record a no-go.
- [ ] Keep unrelated API, metric-schema, distributed-cache, invalidation, and
      write-caching work out of the candidate.
- [ ] Update changelog and release notes without claiming the candidate is
      published.

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
