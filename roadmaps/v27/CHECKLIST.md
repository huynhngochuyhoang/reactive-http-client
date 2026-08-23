# Reactive HTTP Client - Roadmap V27 Execution Checklist

Companion to [`ROADMAP.md`](ROADMAP.md). Execute priorities in order unless a
confirmed correctness or release blocker requires reordering. Check an item only
after implementation and verification evidence is recorded under that priority.
Generated evidence belongs under `target/release-evidence/v27/` unless a
promoted, versioned artifact is explicitly required.

V27 is a SemVer-major program. The explicit resilience activation change and
all four cache phases must remain independently reviewable even when they share
one candidate release.

---

## Priority 1 - Post-`3.6.0` Baseline and Major-Lane Integrity

### [x] 1.1 Prove the published `3.6.0` baseline

- [x] Resolve the parent POM plus starter, test-helper, and OTel POM/JAR/source/
      Javadoc artifacts from a previously absent Central-only repository.
- [x] Require Maven Central remote markers and record SHA-256 values for every
      required artifact.
- [x] Run strict root and starter-module japicmp against published `3.6.0` from
      separate isolated repositories.
- [x] Run published-baseline fixtures for contamination, mixed versions,
      missing attachments, mismatched POM/JAR versions, and self-comparison.

### [x] 1.2 Establish the `4.0.0` development lane

- [x] Generate and source-control the initial `3.x` to `4.0.0` resilience
      migration report, including enabled-only and explicit `default` behavior,
      before changing the reactor version.
- [x] Move root, modules, benchmark harness, assembled consumer, and native
      fixture from `3.7.0-SNAPSHOT` to `4.0.0-SNAPSHOT` in the same reviewed
      change that updates the baseline guard, migration report, and release lane.
- [x] Keep `latest.published.version`, API compatibility, published consumer,
      and benchmark baselines on published `3.6.0`.
- [x] Add a report-only major API lane without weakening strict checks for
      unrelated binary/source incompatibilities.
- [x] Preserve guards against same-version and locally installed baseline
      resolution for root and module-scoped builds.

### [x] 1.3 Keep generated readiness and roadmap state honest

- [x] Report `4.0.0-SNAPSHOT` as development, `3.6.0` as published/API
      baseline, and the release candidate as deferred.
- [x] Keep V27 as the only active roadmap without rewriting V1-V26 evidence.
- [x] Include pending resilience migration, cache phases, API, consumer,
      benchmark, AOT, native, and publication work in release readiness.
- [x] Run release-documentation tests, reactor validation, and
      `git diff --check`.

Evidence recorded on 2026-08-22:

- A previously absent
  `target/published-baseline-repositories/v27-priority1-3.6.0/` resolved the
  published parent POM and all three module POM, binary, source, and Javadoc
  artifacts through `.mvn/maven-central-settings.xml`. The shared provenance
  verifier accepted all Central markers, declared/embedded versions, and 13
  SHA-256 records under
  `target/release-evidence/v27/priority1/published-baseline/`.
- Strict root and starter-module `api-compatibility` runs passed from separate
  `api-root-3.6.0` and `api-starter-3.6.0` repositories. The additional
  `api-major-report-3.6.0` report-only run passed and produced no incompatible
  rows for starter, test-helper, or OTel; strict runs remain separate CI gates.
- `scripts/verify-published-baseline-fixtures.sh` rejected local artifacts,
  mixed candidate versions, missing POM/source/Javadoc files, mismatched
  project/parent POM versions, mismatched embedded JAR versions, and root/module
  self-comparison. `scripts/verify-api-compatibility-fixtures.sh` accepted the
  additive fixture and rejected a source-only checked-exception addition plus
  constructor, nested-method, and enum removals.
- [`docs/31-3x-to-4x-resilience-migration.md`](../../docs/31-3x-to-4x-resilience-migration.md)
  records enabled-only, explicit `default`, named, blank, method-annotation,
  retry-method, and strict-validation migration behavior before the V27 behavior
  implementation begins.
- `DocumentationReleaseArtifactTest` passed all 40 tests. Generated readiness
  reports `4.0.0-SNAPSHOT`, published/API baseline `3.6.0`, active V27 major
  lane, a non-published deferred `4.0.0` candidate, and every remaining V27
  release workstream. `mvn -q -s .mvn/maven-central-settings.xml validate` and
  `git diff --check` also passed.

---

## Priority 2 - Explicit Resilience Activation Contract

### [x] 2.1 Characterize the current implicit activation behavior

- [x] Add focused `Mono` and `Flux` tests proving that `enabled: true` currently
      selects each available `default` operator.
- [x] Record client-level, method-level, blank, unavailable-registry,
      retry-method, and strict-validation behavior before changing defaults.
- [x] Freeze the existing Retry, CircuitBreaker, Bulkhead, and RateLimiter
      composition order.
- [x] Inventory every direct read of resilience instance properties across
      invocation, startup, diagnostics, contract export, mocks, and docs.

Evidence recorded on 2026-08-22:

- [`RESILIENCE_ACTIVATION_BASELINE.md`](RESILIENCE_ACTIVATION_BASELINE.md)
  records the pre-change configuration matrix, operator and subscription order,
  strict-validation boundaries, and direct-read inventory.
- The pre-change focused characterization run proved enabled-only Mono and Flux
  calls selected all four available `default` operators, method annotations
  won, blank client values remained selected, unavailable operators passed
  through, and default retry methods gated only Retry. The executable suite now
  asserts the replacement 4.x contract in 2.2.
- Existing per-method, factory diagnostics, effective-contract, and metadata
  suites retain the missing-instance, blank-annotation, strict-validation,
  unavailable-registry, and published-default evidence.
- The focused Maven run for those six suites passed, followed by
  `git diff --check`.

### [x] 2.2 Make every operator explicit by intent

- [x] Default client-level Retry, CircuitBreaker, Bulkhead, and RateLimiter
      selections to absent rather than `default`.
- [x] Keep `resilience.enabled` as the master gate without selecting any
      operator by itself.
- [x] Treat explicit `default` and non-blank named instances as activation;
      treat blank/absent values as disabled.
- [x] Keep method annotations as explicit per-method selection under the master
      gate and above client-level selection.
- [x] Keep `retry-methods` as eligibility only; it must not activate Retry.

Evidence recorded on 2026-08-22:

- `ResilienceConfig` now leaves all four client-level instance names absent;
  generated metadata and the configuration reference publish no implicit
  `default` values.
- `ReactiveClientInvocationHandler` resolves only non-blank method/client
  selections and does not invoke an operator applier for absent selections.
  Existing startup diagnostics, strict retry validation, diagnostics snapshots,
  and effective-contract export report those selections as disabled.
- `ExplicitResilienceActivationContractTest` covers enabled-only Mono/Flux,
  explicit `default`, named subsets, blank values, method-level precedence and
  method-only activation, unavailable operators, and retry-method eligibility.

### [x] 2.3 Preserve strict and composition semantics

- [x] Keep strict unsafe-retry validation dormant when Retry is unselected,
      unavailable, missing, or configured for one attempt.
- [x] Prove retry-only, circuit-breaker-only, bulkhead-only, and
      rate-limiter-only clients apply exactly one selected operator.
- [x] Prove explicit multi-operator clients retain the established operator
      order and terminal facts.
- [x] Verify `enabled: true` alone applies no operator and performs no registry
      lookup or operator subscription.

Evidence recorded on 2026-08-22:

- `ReactiveHttpClientFactoryBeanDiagnosticsTest` proves strict unsafe-retry
  validation remains dormant for unselected, unavailable, and one-attempt
  Retry states. `PerMethodResilienceTest` proves a missing method-level Retry
  is rejected by instance validation before strict retry validation runs.
- `ExplicitResilienceActivationContractTest` independently selects Retry,
  CircuitBreaker, Bulkhead, and RateLimiter and observes exactly one matching
  operator application and subscription for each case.
- The same suite records the established assembly and subscription order for
  an explicit four-operator client. `ResilienceOperatorCompositionContractTest`
  retains the real Resilience4j retry, admission, cancellation, attempt-count,
  and one-logical-terminal-result evidence.
- Enabled-only `Mono` and `Flux` calls now assert zero operator application,
  zero operator subscription, and zero availability/configuration/capacity
  lookup through the operator-applier registry boundary.
- The focused four-suite Maven run and the complete starter test suite passed;
  `git diff --check` passed after the checklist update.

---

## Priority 3 - One Effective Resilience Policy Everywhere

### [x] 3.1 Centralize effective selection

- [x] Introduce one package-private effective resilience policy used by
      invocation, startup validation/logging, contract export, and diagnostics.
- [x] Resolve client/method precedence, blank values, Retry eligibility, and
      operator availability once without creating lazy registries.
- [x] Remove duplicated activation decisions made obsolete by the resolver.
- [x] Keep the resolver internal unless a concrete public extension contract is
      reviewed and required.

### [x] 3.2 Align diagnostics, mocks, and startup output

- [x] Report disabled, selected-but-unavailable, active, and unknown lazy
      registry states without inventing `default` instances.
- [x] Keep startup summaries from naming operators that cannot be applied.
- [x] Make `MockReactiveHttpClient` use the same effective policy and strict
      retry rules as production.
- [x] Cover parent/child factories, primary/priority/default candidates,
      `FactoryBean` products, and lazy registries without diagnostics side
      effects.

### [x] 3.3 Publish configuration and migration evidence

- [x] Update generated configuration metadata so no instance property defaults
      to implicit `default`.
- [x] Add drift tests rejecting enabled-only examples that claim operators are
      active.
- [x] Generate a migration matrix for enabled-only, explicit `default`, named
      instance, method annotation, blank, unavailable, retry-method, and strict
      validation cases.
- [x] Verify existing users can retain the old all-four behavior only by
      explicitly selecting all four `default` instances.

Evidence recorded on 2026-08-22:

- Package-private `EffectiveResiliencePolicy` is now the only core class that
  reads the four client-level instance properties. It resolves the master gate,
  method-over-client precedence, blank values, Retry HTTP-method eligibility,
  source, and tri-state operator availability into `disabled`, `unavailable`,
  active instance, or `unknown` states. Focused resolver tests cover every
  state without adding a public compatibility surface.
- `ReactiveClientInvocationHandler`, factory startup validation and method
  logging, `EffectiveHttpClientContractExporter`, and provider-backed
  diagnostics consume that policy. Startup output reports only effective
  per-method states; selected operators with no adapter are not applied or
  printed as active. Strict Retry validation runs only for an active Retry that
  can make another attempt.
- Diagnostics reuse one side-effect-free registry availability snapshot per
  report. The complete diagnostics suite retains parent/child, primary,
  priority, default/fallback candidate, cached/uncached `FactoryBean`, lazy,
  prototype, and uninspectable-registry coverage while proving lazy products
  are not created and rendering unprovable selected operators as `unknown`.
- `MockReactiveHttpClient` reaches the production validation path and its mock
  Retry applier reports whether more than one attempt is possible. New tests
  prove production-equivalent strict unsafe-retry failure and the dormant
  single-attempt case.
- Generated metadata and the configuration reference describe `enabled` as a
  master gate that selects nothing and retain absent defaults for all four
  instance properties. The 3.x-to-4.x migration matrix covers enabled-only,
  explicit `default`, named, method-level, blank, Retry-method, unavailable,
  lazy/unknown, and strict-validation cases, including the explicit four-
  `default` configuration required to retain the published behavior.
- Focused resilience, diagnostics, startup, metadata, release-document, and
  lifecycle suites passed. The complete starter suite passed `1073` tests and
  the complete test-helper suite passed `51` tests with zero failures, errors,
  or skips. A final direct-read audit and `git diff --check` passed.

---

## Priority 4 - Cache Opt-In and Declarative Eligibility Grammar

### [x] 4.1 Freeze the public cache policy model

- [x] Choose and document the final client-level and method-level activation
      API before adding it to public compatibility filters.
- [x] Keep caching disabled when no client or method explicitly selects a
      policy; adding the cache dependency or declaring defaults activates
      nothing.
- [x] Define method-over-client precedence and an explicit method exclusion for
      client-wide caching.
- [x] Require positive finite TTL and maximum size for every selected policy.
- [x] Reject missing, zero, negative, overflow, and impractical bounds at
      startup with client and method/policy identity.

### [x] 4.2 Define eligible response contracts

- [x] Initially accept only explicitly selected `GET` methods returning finite
      `Mono<T>` or `Mono<ResponseEntity<T>>` values.
- [x] Reject `Flux`, `Mono<Void>`, streaming envelopes, `DataBuffer`, Publisher,
      Resource, multipart/stream bodies, and unresolved generic values at
      startup when caching is selected.
- [x] Cache only successful non-null emissions after decoding.
- [x] Never cache empty completion, cancellation, redirect, auth/decode/
      transport/resilience error, or mapped 4xx/5xx failure.
- [x] Document cached `ResponseEntity` status/header semantics without claiming
      a new wire response on a hit.

### [x] 4.3 Apply eligibility consistently

- [x] Define a cache-aware pre-lookup policy boundary that runs authorization,
      tenant, and other required per-invocation gates before every hit or miss.
- [x] Inventory every applicable Boot `WebClientCustomizer` and per-client
      `ReactiveHttpClientCustomizer`, including filters, `defaultRequest`,
      exchange-function replacement, codecs/connectors, and other builder
      mutations.
- [x] Classify each customization as cache-safe, represented by a pre-lookup
      gate or key/variant contribution, or cache-incompatible; reject caching
      when any applicable customization or later builder mutation is unknown.
- [x] Resolve inherited methods, overloads, nested generic bindings, and
      `@ApiRef` cache metadata against the concrete client.
- [x] Run the same validation during starter proxy startup, effective-contract
      export, diagnostics, AOT, and mock construction.
- [x] Respect replacement `MethodMetadataCache` behavior and skip starter-only
      grammar for foreign `FactoryBean` clients.
- [x] Add startup failure-message tests naming the concrete client, method, and
      rejected cache shape.

Evidence recorded on 2026-08-22:

- `CacheResponse`, `CacheDisabled`, and the client `cache.policy` configuration
  freeze explicit method/client selection with method exclusion first, method
  selection second, client selection third, and disabled-by-default behavior.
  Named policy definitions remain inert. Selected policies require TTL in the
  range `1..31536000000` milliseconds and maximum size in `1..1000000` entries;
  failures include concrete client, Java method, policy source, and `@ApiRef`
  identity when present.
- Package-private `EffectiveCachePolicy` resolves inherited methods, overloads,
  nested generic bindings, and configured `@ApiRef` methods against the concrete
  interface. It accepts only finite `GET` `Mono<T>` and
  `Mono<ResponseEntity<T>>` contracts and rejects raw/unresolved values,
  streaming or application-owned response/request shapes, multipart requests,
  and every non-GET method before dispatch.
- `CacheCustomizationValidator` inventories Boot and per-client customizers plus
  replacement `WebClient.Builder` beans. Unknown and `INCOMPATIBLE` mutations
  reject selection; `SAFE` is an explicit whole-builder assertion. Pre-lookup
  gates and key/variant contributions remain intentionally unavailable until
  their later V27 contracts exist, so they cannot be asserted without an
  implementation.
- Starter proxy startup, contract export/snapshots, provider-backed diagnostics,
  AOT processing, and `MockReactiveHttpClient` use the same
  `MethodMetadataCache`-backed policy grammar. Tests prove replacement metadata
  caches remain authoritative and annotated foreign `FactoryBean` clients skip
  starter-only validation.
- `docs/32-response-caching.md`, annotation docs, generated configuration
  metadata/reference, runtime hints, and the unreleased changelog document the
  frozen contract and explicitly state that Priority 4 stores no responses.
  Focused cache, diagnostics, AOT, snapshot, metadata, and mock tests passed;
  the complete starter suite and complete `MockReactiveHttpClientTest` passed;
  `git diff --check` passed.

---

## Priority 5 - Cache Key, Variant, and Isolation Contract

### [x] 5.1 Build a deterministic opaque key

- [x] Include concrete client identity and full resolved method signature in
      every key.
- [x] Define a canonical typed structural encoding with explicit null markers,
      scalar type identifiers, length framing, container/element boundaries,
      and canonical map-entry ordering before key equality or one-way derivation.
- [x] Reject delimiter concatenation, generic `toString()` fallback, identity
      hash codes, and unframed serialized text for selected key/context values;
      key path/query dimensions through their exact frozen wire projection.
- [x] Define deterministic selected-input handling for nulls, primitives,
      strings, arrays, collections, maps, enums, records, and inherited generic
      values.
- [x] Freeze one supported argument snapshot per subscription and use that same
      snapshot for both key construction and request materialization.
- [x] Reject mutable/nested inputs that cannot be copied safely rather than
      allowing the key and dispatched request to observe different values.
- [x] Reject publishers, streams, resources, unstable maps, and unresolved or
      unsupported values selected as key inputs.
- [x] Prove no collision across clients, overloads, inherited methods, argument
      order, and configured variants.
- [x] Add adversarial collision tests for null versus `"null"`, scalar values
      with different types, `("ab", "c")` versus `("a", "bc")`, empty versus
      absent containers, nested boundaries, and equivalent maps with different
      iteration order.
- [x] Count record components against the cumulative freeze budget and enforce
      the canonical byte cap while nested frames are written.
- [x] Preserve wire iteration order when a selected set is also request-bound;
      keep canonical set sorting only for non-request variants.

### [x] 5.2 Require explicit response variants

- [x] Define startup-validated selection for path/query inputs and additional
      parameter/header/context partition dimensions.
- [x] Require explicit partition inputs or an explicit shared-response
      acknowledgement for auth-, tenant-, locale-, header-, or Reactor-context-
      dependent responses.
- [x] Reject unknown parameter/header names and ambiguous variant declarations
      before auth or transport dispatch.
- [x] Expose the conventional context-only `Idempotency-Key` as a selectable
      header variant and require partitioning or `shared-response` even when the
      method has no idempotency annotation.
- [x] Document that request IDs and correlation IDs are not useful response
      variants and can destroy cache effectiveness.
- [x] Document the explicit runtime hint required when a native application
      uses a record type only as a selected Reactor-context value.

### [x] 5.3 Protect key material

- [x] Never export raw or hashed keys through metrics, logs, traces,
      diagnostics, health, or support bundles.
- [x] Never retain auth tokens, credentials, or cookies as ordinary key text.
- [x] Use an opaque one-way representation for explicitly selected sensitive
      partition values and clear references on eviction.
- [x] Add cross-tenant/auth/locale isolation tests and redaction tests for every
      observability surface.

Evidence recorded on 2026-08-22:

- Added the public parameter-level `@CacheKey` label and inert policy fields
  `vary-by-parameters`, `vary-by-headers`, `vary-by-context`, and
  `shared-response`. `MethodMetadataCache` validates labels once, so proxy
  startup, AOT, diagnostics/export, and `MockReactiveHttpClient` retain the
  shared declarative grammar.
- Package-private `CacheKeyContract` includes the logical and concrete client,
  resolved parameter/response signature, path/query values, and selected
  parameter/header/context variants in a typed, length-framed canonical form.
  Map and set values are canonically ordered; no delimiter concatenation,
  arbitrary serialization, identity hash, or generic `toString()` fallback is
  accepted. Equality uses an internal SHA-256 digest whose string form is only
  `OpaqueCacheKey`.
- Selected calls are prepared through `Mono.deferContextual`. Each subscription
  defensively freezes one supported argument graph, resolves the request from
  that graph, and derives the key from the same resolved values. Mutable DTOs,
  mutable record components, raw/unstable containers, unresolved values,
  publishers, streams, buffers, channels, and resources fail before dispatch.
- Startup rejects unknown/duplicate variant names, unpartitioned dynamic
  headers, header maps, bodies, and authenticated responses unless an explicit
  partition or `shared-response` acknowledgement makes the reuse decision
  reviewable. Documentation warns against request/correlation IDs as variants.
- The final canonical byte copy is zeroed immediately after one-way derivation.
  Only the digest-only key can reach the future storage boundary; selected
  values remain subscription-local, so dropping an entry cannot retain auth,
  cookie, tenant, or locale references. A source reachability audit confirms
  neither raw nor digest keys enter observability, logs, diagnostics, health,
  or support output.
- `CacheKeyContractTest` covers client/method/inherited-generic separation,
  tenant/auth/locale isolation, sensitive header opacity, adversarial scalar and
  boundary collisions, null/empty containers, map ordering, defensive copies,
  mutable nested records, and a real cold-proxy subscription snapshot. AOT
  tests cover `@CacheKey` plus record accessors nested inside resolved generic
  containers.
- Follow-up review on 2026-08-23 added constant-specific enum support, linear
  sequential-list copying, request-order-preserving set/map snapshots,
  accessible external non-public records, complete case-insensitive header
  variants, and context idempotency-key preparation before key derivation.
- A second review pass added a cumulative freeze budget, concrete generic-record
  substitution, explicit method-generated idempotency variants, and URI
  path/query wire projection. Regression tests cover shared nested containers,
  `Box<String>`, generated-header acknowledgement/partitioning, and
  order-sensitive path/query containers.
- A third review pass made the context-only conventional idempotency header a
  startup-visible variant, counted record fan-out against the cumulative freeze
  budget, preserved selected request-bound set order, and enforced the 1 MiB
  canonical limit during nested writes. The native guide now requires explicit
  runtime hints for record types that occur only in `vary-by-context` values.
- `mvn -q -s .mvn/maven-central-settings.xml -pl reactive-http-client-test -am
  test` passed `1112` starter tests and `55` test-helper tests with zero
  failures, errors, or skips. Metadata JSON validation and `git diff --check`
  also passed.

---

## Priority 6 - Phase One: Bounded Local TTL Cache

### [ ] 6.1 Integrate a proven local cache implementation

- [ ] Select a maintained local cache implementation with bounded maximum-size
      eviction and monotonic expiry; do not hand-roll concurrent eviction.
- [ ] Keep implementation types out of public starter APIs unless deliberately
      reviewed as long-term extension points.
- [ ] Decide and verify dependency packaging so cache-disabled consumers do not
      fail when the optional implementation is absent.
- [ ] Fail startup clearly only when a client selects caching but the required
      implementation is unavailable.

### [ ] 6.2 Implement hit, miss, and storage boundaries

- [ ] Build one cold per-subscription cache lookup around eligible declarative
      invocations.
- [ ] Require the validated Priority 5 key/variant decision before any cache
      lookup or response storage can occur.
- [ ] Return an unexpired hit without downstream resilience admission, redirect,
      pool acquisition, or HTTP dispatch only after all mandatory pre-lookup
      policy/auth/key-partition gates succeed.
- [ ] Execute the existing logical-call pipeline once for a phase-one miss and
      store only its final successful decoded value.
- [ ] Preserve phase-one behavior where concurrent misses may execute separate
      loads; do not claim single flight before Priority 7.
- [ ] Publish concurrent same-key loads conditionally so the first successful
      fill for the observed generation wins; a late duplicate returns to its
      caller but cannot replace the entry or restart TTL.
- [ ] Prevent a late duplicate from repopulating after expiry, eviction, refresh,
      or shutdown has advanced the entry generation.
- [ ] Prove hit/miss behavior with deterministic time and dispatch counters,
      including reversed duplicate-load completion order and auth/tenant/locale
      variants.

### [ ] 6.3 Bound expiry, eviction, identity, and lifecycle

- [ ] Enforce hard TTL with monotonic time and maximum-size eviction under
      concurrent access.
- [ ] Cover expiry, replacement, size eviction, load failure, cancellation, and
      factory shutdown without retaining orphaned entries.
- [ ] Document that cached mutable values are returned by identity and are not
      copied or serialized solely for caching.
- [ ] Verify aggregate configured capacity and current size can be inspected
      without exposing entries or keys.

---

## Priority 7 - Phase Two: Request Coalescing / Single Flight

### [ ] 7.1 Add separately opt-in single flight

- [ ] Keep single flight disabled unless the selected cache policy enables it.
- [ ] Share exactly one in-flight load for concurrent misses of the same key.
- [ ] Keep different keys, clients, methods, and policies independent.
- [ ] Prove one transport dispatch and one request-body subscription with
      latches/barriers rather than sleep-only timing.

### [ ] 7.2 Preserve caller subscription ownership

- [ ] Give every waiter its own subscription-local timeout, cancellation, and
      one terminal lifecycle/observer/exchange record.
- [ ] Keep the shared load alive while any caller remains interested; the first
      caller's outer logical timeout must detach only that caller rather than
      terminating the load for later waiters.
- [ ] Keep request/attempt timeouts and any explicit shared-load safety bound
      effective without borrowing one caller's logical deadline.
- [ ] Ensure cancelling one waiter does not cancel a load required by another.
- [ ] Define and test cancellation when the last interested caller leaves;
      abandoned work cannot populate the cache accidentally.
- [ ] Fan out load success, error, empty completion, and cancellation
      deterministically, then remove completed/failed in-flight state.
- [ ] Prove slow/failed keys do not hold a global lock or block unrelated keys.
- [ ] Deterministically prove both timeout directions: waiter timeout with first
      caller success, and first-caller timeout with later waiter success.

### [ ] 7.3 Keep hidden replay inside the leader

- [ ] Run Retry, one-time auth replay, redirects, and transport dispatch only in
      the leader load.
- [ ] Prevent each waiter from consuming resilience permits or creating hidden
      replays.
- [ ] Keep each waiter's terminal cache outcome distinct from the leader's HTTP
      attempt and dispatch facts.
- [ ] Cover waiter timeout/cancellation while the leader retries, redirects, or
      refreshes auth.

---

## Priority 8 - Phase Three: Refresh on Access

### [ ] 8.1 Add a bounded refresh state machine

- [ ] Keep refresh disabled unless explicitly selected for a cache policy.
- [ ] Require positive `refresh-after` strictly below the hard TTL.
- [ ] Require a positive finite refresh timeout and cap each refresh at the
      earliest of that deadline, hard expiry, and factory shutdown.
- [ ] Return the current value after refresh-after and trigger at most one
      access-driven refresh for that key.
- [ ] Keep concurrent stale callers on the current value without starting
      duplicate refresh loads.
- [ ] Separate miss single-flight and refresh single-flight transitions.

### [ ] 8.2 Preserve hard expiry and live invocation context

- [ ] Use the live triggering invocation's validated key/variant context rather
      than retaining arbitrary argument graphs or scheduling invented requests.
- [ ] Run refresh through the same pre-lookup gates, auth, selected resilience
      operators, redirects, request/response timeouts, and transport pipeline as
      a miss while bypassing recursive cache lookup.
- [ ] Atomically replace on refresh success and restart entry age.
- [ ] Preserve the current value on refresh failure only until hard TTL.
- [ ] Never serve stale after hard expiry; a later caller follows normal miss
      and single-flight behavior.
- [ ] Cancel a refresh at hard expiry and prevent its late result from
      repopulating the cache.
- [ ] Use a non-terminating refresh fixture to prove deadline cancellation and
      release of invocation/auth/key state after the stale caller completes.
- [ ] Cover refresh failure, cancellation, late completion, hard-expiry race,
      and eviction during refresh deterministically.

### [ ] 8.3 Own refresh shutdown and resources

- [ ] Cancel or await cache-owned refresh work under the factory's existing
      aggregate shutdown deadline.
- [ ] Prevent late refresh completion from repopulating a closed or evicted
      cache.
- [ ] Release values, key snapshots, invocation context, and in-flight state on
      shutdown.
- [ ] Prove no cache scheduler/thread/resource survives factory destruction.

---

## Priority 9 - Cache, Resilience, Auth, Redirect, and Timeout Composition

### [ ] 9.1 Freeze operator boundaries

- [ ] Place lookup after mandatory cache-aware policy, authorization, tenant,
      and key-partition gates but before downstream resilience, redirect, pool,
      and transport work.
- [ ] Reject caching for a client with any unclassified Boot/per-client
      customizer or builder mutation; a hit cannot bypass dynamic request,
      authorization, tenant, exchange-function, filter, codec, connector, or
      response-transformation behavior.
- [ ] Keep a miss leader behaviorally identical to an uncached invocation until
      its final successful decoded value is stored.
- [ ] Make refresh bypass lookup only and reuse the miss auth/resilience/
      redirect/timeout/transport pipeline, with separate hidden-refresh terminal
      reporting.
- [ ] Include lookup and each caller's coalesced wait in that caller's logical
      timeout without placing any caller-specific outer deadline inside the
      shared load publisher.
- [ ] Keep non-GET/unsafe methods ineligible regardless of idempotency keys.
- [ ] Do not infer read invalidation from any write method.

### [ ] 9.2 Preserve terminal-state isolation

- [ ] Prevent prior load/refresh URL, status, headers, error, failure stage,
      attempt count, body size, and dispatch facts from leaking into a hit.
- [ ] Prevent leader terminal state from being shared as a waiter's
      subscription-local terminal state.
- [ ] Keep generated idempotency keys, prepared headers, attempt cleanup, and
      logical timing isolated per caller/load as applicable.
- [ ] Cover hit, miss, waiter, timeout, cancellation, auth failure, open circuit,
      redirect, retry exhaustion, and refresh failure in one composition suite.

### [ ] 9.3 Preserve envelope and object contracts

- [ ] Return cached `Mono<T>` values without re-decoding or re-serializing.
- [ ] Preserve cached `ResponseEntity<T>` value and status while copying only a
      documented bounded allowlist of representation headers.
- [ ] Treat `Set-Cookie`, auth challenges, `SensitiveHeaders`, and configured
      per-caller response headers as non-cacheable, and never replay
      non-allowlisted headers to later callers.
- [ ] Verify load responses and cache hits document their header difference and
      never expose the first caller's session/identity headers.
- [ ] Prove mutable-value identity behavior and document caller responsibility.
- [ ] Reject any later response shape that cannot retain deterministic ownership
      and terminal semantics.

---

## Priority 10 - Phase Four: Cache Metrics, Observability, and Diagnostics

### [ ] 10.1 Freeze one cache outcome vocabulary

- [ ] Define bounded caller outcomes for fresh hit, miss loader, coalesced
      waiter, and stale hit with refresh.
- [ ] Define bounded hidden-work outcomes for load/refresh success, failure, and
      cancellation plus TTL and size eviction.
- [ ] Add public event/context fields only after null/unknown behavior and
      compatibility constructors are reviewed.
- [ ] Keep raw keys, values, arguments, headers, bodies, URLs, tenant values,
      and credentials out of every outcome model.
- [ ] Add a separate cache-observability opt-in that defaults false and remains
      subordinate to the existing global observability master gate.

### [ ] 10.2 Implement and verify Micrometer meters

- [ ] Add a lookup counter by bounded client, API name, and `hit`/`miss` result.
- [ ] Add separate coalesced, stale-serving, load, refresh, and eviction facts
      without making hit ratio ambiguous.
- [ ] Add load/refresh duration only under distinct meter names and correct
      registry time units.
- [ ] Export current entry count, configured maximum size, and eviction count
      under starter-specific names that cannot collide with cache-library meters.
- [ ] Track every cache meter registration as factory-owned state and remove all
      counters, timers, summaries, and gauges from the `MeterRegistry` during
      factory destruction.
- [ ] Prove destroy is idempotent, releases cache references, and prevents late
      load/refresh completion from recording into removed meters.
- [ ] Destroy and recreate a factory with the same meter tags against one live
      registry; verify the replacement gauge observes only the new cache and no
      stale meter registration is reused.
- [ ] Verify meter absence when caching is unselected and prove metrics enablement
      cannot activate caching.
- [ ] Verify a cache-enabled, cache-observability-disabled client records no
      cache-library stats, cache meters, cache OTel attributes, or cache-specific
      log/context fields.
- [ ] Add Prometheus scrape tests for names, types, tags, zero-series behavior,
      hit ratio, coalescing ratio, refresh failure rate, and capacity pressure.

### [ ] 10.3 Align terminal observability surfaces

- [ ] Report a hit once with `attemptCount=0`, `requestDispatched=false`, and no
      invented HTTP status, server, transport stage, or wire byte count.
- [ ] Keep miss-loader HTTP facts while distinguishing coalesced waiters and
      stale hits from dispatched calls.
- [ ] Align lifecycle, observer, exchange logger, Micrometer, and OTel on the
      same bounded caller cache outcome.
- [ ] Add one bounded cache outcome attribute to built-in OTel logical-call
      spans without exporting keys or values.
- [ ] Keep hidden refresh from silently creating detached OTel spans; expose it
      through reviewed bounded meters and sanitized logs unless a later explicit
      span opt-in is approved.
- [ ] Prove exactly one terminal record per caller for hit, miss, waiter, stale
      hit, timeout, cancellation, load failure, and refresh failure.

### [ ] 10.4 Align diagnostics, health, and operations

- [ ] Export only bounded policy and aggregate state: enabled phase, TTL,
      refresh threshold, single-flight state, maximum size, entry count, and
      evictions.
- [ ] Never enumerate cache entries or expose keys through the diagnostics
      endpoint, snapshot helper, health, or support bundle.
- [ ] Keep hit ratio, refresh failure, eviction pressure, and entry count as
      operational signals that do not mark downstream health UP/DOWN by
      themselves.
- [ ] Add bounded support-bundle examples and PromQL/dashboard recipes for each
      supported cache signal.

---

## Priority 11 - Mock, Consumer, AOT, Native, and Shutdown Parity

### [ ] 11.1 Extend the mock helper without claiming wire evidence

- [ ] Add deterministic clock/policy setup and hit/miss/coalesced/refresh
      assertions to `MockReactiveHttpClient`.
- [ ] Expose load counts and explicit eviction for tests without leaking the
      selected cache implementation into public APIs.
- [ ] Preserve production client names, metadata-cache replacement, auth,
      lifecycle ordering, observers, and final-request behavior.
- [ ] Document that the helper does not prove transport dispatch, pool reuse,
      socket cancellation, or native cleanup.

### [ ] 11.2 Prove assembled Boot 4 consumer behavior

- [ ] Cover cache-disabled, one-method opt-in, client-wide opt-in/exclusion,
      TTL expiry, capacity eviction, single flight, and refresh.
- [ ] Cover explicit retry-only activation and enabled-only no-operator behavior.
- [ ] Record effective POM, dependency tree, classpath, test reports, source
      commit/state, and no-reactor-leakage checks incrementally.
- [ ] Preserve reports from failed stages without uploading stale prior-run
      evidence.

### [ ] 11.3 Revalidate AOT, native, and shutdown

- [ ] Add precise runtime hints for selected public cache metadata and required
      implementation resources without broad package reflection.
- [ ] Keep starter-factory ownership and replacement metadata-cache boundaries
      during AOT validation.
- [ ] Compile and run native smoke for one cached read, one actual load, one
      refresh, and explicit retry-only activation while counting all dispatches.
- [ ] Prove factory destruction clears entries and terminates coalesced loads and
      refresh work within the shared shutdown deadline.
- [ ] Prove destruction removes factory-owned cache meters before same-tag
      recreation and leaves no registry reference to the closed cache.
- [ ] Record clean commit, GraalVM/JDK versions, dependency list, executable
      status, and binary SHA-256.

---

## Priority 12 - Performance and Allocation Evidence

### [ ] 12.1 Extend the benchmark harness fairly

- [ ] Add no-network benchmarks for cache-disabled invocation and key lookup.
- [ ] Add equivalent loopback scenarios for cache miss, hit, coalesced miss, and
      refresh-on-access.
- [ ] Keep all compared clients on equivalent transport/server conditions and
      label starter-specific work explicitly.
- [ ] Never compare a local cache hit to a raw WebClient network call as
      abstraction-overhead evidence.
- [ ] Keep smoke runs non-publishable and redirect metadata logging away from
      benchmark console I/O.

### [ ] 12.2 Audit default and resilience overhead

- [ ] Compare caching disabled against published `3.6.0` for throughput,
      average time, and allocation without making a public claim from smoke data.
- [ ] Prove enabled-only resilience performs no operator lookup/subscription and
      explicit retry-only does not initialize unrelated operators.
- [ ] Attribute any default-path regression to concrete allocations or operator
      work before optimizing.
- [ ] Preserve report environment, resolved dependency versions, commit/state,
      commands, and current-vs-published pairing.

### [ ] 12.3 Audit cache memory and allocation ownership

- [ ] Measure key construction, hit, miss, loader, waiter, eviction, and refresh
      allocations separately.
- [ ] Verify completed in-flight entries do not retain request graphs, Reactor
      context, auth tokens, or caller subscriptions.
- [ ] Measure maximum-size and refresh overhead under bounded representative
      policies rather than unbounded synthetic cardinality.
- [ ] Promote a versioned report only from a clean commit when release notes make
      numerical performance claims.

---

## Priority 13 - Migration and Operations Documentation

### [ ] 13.1 Publish the `3.x` to `4.0.0` resilience migration

- [ ] Document the old implicit activation behavior and new explicit selection
      in one migration table.
- [ ] Show retry-only, each other single operator, named instances, and explicit
      all-four `default` configuration.
- [ ] Explain method annotation precedence, blank values, retry eligibility, and
      strict validation.
- [ ] Update quick start, resilience, production checklist, changelog, and
      generated examples consistently.

### [ ] 13.2 Publish the cache contract and examples

- [ ] Document explicit client/method opt-in, required TTL/capacity, precedence,
      and method exclusion.
- [ ] Explain local-only scope, per-instance divergence, key variants,
      auth/tenant isolation, mutable object identity, and no write invalidation.
- [ ] Document phase-one duplicate concurrent misses, phase-two single flight,
      phase-three access refresh/hard TTL, and phase-four telemetry.
- [ ] List unsupported methods, return types, streaming shapes, failures, empty
      values, and distributed-cache expectations.
- [ ] Use only fake `.example.invalid` hosts and secret-free placeholders.

### [ ] 13.3 Consolidate operational guidance and drift guards

- [ ] Add hit-ratio, miss/load, coalescing, refresh failure, eviction pressure,
      and capacity dashboard recipes with units and zero-series handling.
- [ ] Add support-bundle fixtures containing bounded aggregate cache facts and
      no key/value material.
- [ ] Generate configuration examples/reference from metadata and reject group
      names used as scalar properties.
- [ ] Validate every public Markdown link, property name, placeholder host, and
      promoted benchmark reference.

---

## Priority 14 - Public API and Compatibility Evidence

### [ ] 14.1 Freeze the intended public surface

- [ ] Inventory cache annotations, configuration models, event/context fields,
      diagnostics fields, and test-helper APIs before release.
- [ ] Include every documented extension/helper and nested public type in the
      japicmp filter; explicitly document intentional exclusions.
- [ ] Keep cache implementation classes package-private or optional-integration
      internals unless a public dependency is deliberately accepted.
- [ ] Review constructors and mutable models for binary/source compatibility and
      safe future evolution.

### [ ] 14.2 Guard the reviewed major delta

- [ ] Generate root and starter major API reports against published `3.6.0` in
      report-only mode.
- [ ] Source-control the exact reviewed incompatible delta and fail on any
      unreviewed removal, modification, or incompatible addition.
- [ ] Keep strict module-scoped compatibility commands for future post-`4.0.0`
      lines and prove their baseline guard in fixtures.
- [ ] Update the public API compatibility guide with exact reproducible commands
      and isolated repositories.

### [ ] 14.3 Revalidate dependency and packaging evidence

- [ ] Record the cache implementation dependency version and optional/transitive
      behavior in generated POM and dependency evidence.
- [ ] Run the supported Spring Boot/Framework, Reactor Netty, Netty, Jackson,
      Micrometer, OTel, Resilience4j, and cache-library matrix.
- [ ] Verify parent/module POM, binary, source, and Javadoc artifacts contain only
      current generation classes/resources and no stale outputs.
- [ ] Run full reactor tests, current/published consumers, AOT, native, metadata,
      documentation, and packaging guards from clean inputs.

---

## Priority 15 - V27 / `4.0.0` Go-No-Go

### [ ] 15.1 Select release scope and candidate version

- [ ] Inventory the explicit resilience behavior break, all four cache phases,
      public APIs, configuration, diagnostics schema, dependencies, docs, and
      benchmark claims.
- [ ] Select `4.0.0` only when the resilience migration and all four cache phases
      are complete and supportable.
- [ ] Reject/defer unrelated public API removals or diagnostics breaks not
      required by the reviewed V27 contract.
- [ ] Record whether numerical performance wording requires a promoted current
      and published-baseline report pair.

### [ ] 15.2 Assemble immutable release evidence

- [ ] Run clean full reactor verification from one immutable candidate commit.
- [ ] Run reviewed major API delta, supported dependency matrix, generation
      packaging, current/published consumers, cache/resilience composition,
      AOT/native, documentation, and support-bundle gates.
- [ ] Verify complete candidate parent, starter, test-helper, and OTel POM,
      binary, source, and Javadoc artifacts.
- [ ] Record commands, logs, test reports, dependency lists, checksums, commit,
      source state, native binary hash, and benchmark disposition under one
      immutable evidence directory.
- [ ] Cite a clean promoted benchmark report pair or keep release wording
      non-numerical.

### [ ] 15.3 Record the mutually exclusive decision

- [ ] **Go path:** tag and publish `4.0.0` from the matching immutable commit.
- [ ] **Go path:** verify every Maven Central artifact and assembled published
      consumer before moving public/API/consumer/benchmark baselines.
- [ ] **Go path:** open the next snapshot line and archive V27 only after Central
      verification succeeds.
- [ ] **No-go path:** publish nothing and record every blocker, reproduction,
      retained evidence path, and follow-up scope.
- [ ] Update roadmap/checklist/index/changelog status to match the selected path.
- [ ] Run final release-documentation tests, reactor validation, and
      `git diff --check`.

## Completion Rule

V27 is complete only when the resilience activation break has an explicit
migration and each checked cache behavior has evidence at the layer that owns
it. Unit tests cannot prove real transport dispatch, concurrent single flight,
Prometheus scrape shape, assembled-consumer behavior, native execution, or
Maven Central provenance. Phase one must remain useful without silently enabling
phase two, phase three, or phase four exports. Check only the release-decision
branch that actually occurs; the unchecked mutually exclusive branch remains
historical context after V27 is archived.
