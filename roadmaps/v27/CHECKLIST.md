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

Evidence recorded on 2026-08-23:

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
- [x] Count one depth level per nested record in both startup validation and
      runtime freezing, including a valid 17-record scalar chain.
- [x] Preserve top-level query arrays as ordered multi-value parameters while
      rejecting path arrays and arrays nested in query elements whose current
      `String.valueOf` projection is identity-based.
- [x] Preflight UTF-8 scalar length against the remaining canonical byte budget
      before allocating encoded scalar bytes.
- [x] Charge the cumulative freeze budget from actual list/set/map iteration,
      not untrusted container `size()` metadata.
- [x] Preserve every iterated identity-set member even when distinct members are
      equal by value.
- [x] Preserve selected request-body map entry order while keeping cache-only
      maps canonically order-independent.
- [x] Keep non-normalized URI spellings distinct in canonical key material.
- [x] Reject container arrays whose declared or runtime component cannot hold
      the defensive snapshot while retaining interface-typed container arrays.
- [x] Preserve every iterated identity-map entry without collapsing frozen keys
      under `equals` semantics.
- [x] Preflight `BigInteger` and `BigDecimal` magnitude length before allocating
      their canonical byte arrays.
- [x] Bound the cumulative request-target projection before combining repeated
      path containers or nested query values, then dispatch that same snapshot.
- [x] Retain supported caller-created records without rerunning canonical
      constructors and reject non-canonical accessors whose value can change.
- [x] Preflight URI text length before allocating its canonical UTF-8 payload.

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
- [x] Do not count that universally required, potentially absent idempotency
      header as the sole authenticated-response partition; require another
      explicit parameter/header/context dimension or `shared-response`.
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

Evidence recorded on 2026-08-23:

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
- Follow-up validation rejects cache-only `@CacheKey` parameters unless the
  effective policy selects their label, rejects computed/stateful record
  accessors, and bounds AOT generic traversal with a visited-type set so
  F-bounded method parameters cannot recurse indefinitely. Native hints include
  the record class resources required by accessor validation.
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
- A fourth review pass excluded the effective idempotency header as the sole
  authenticated identity partition, aligned record depth accounting, preserved
  top-level multi-value query arrays while rejecting identity-based nested/path
  arrays, exercised generic-record fan-out, and preflighted oversized UTF-8
  scalars before byte-array allocation.
- A fifth review pass charged the freeze budget per iterated collection/map
  member, preserved duplicate-equal identity-set elements, retained selected
  request-body map order, and stopped normalizing distinct URI spellings into
  one key.
- A sixth review pass rejected incompatible concrete/covariant container arrays,
  preserved duplicate-equal identity-map entries, and preflighted arbitrary-
  precision numeric magnitudes before canonical byte allocation.
- A seventh review pass bounded request-target string projection before
  materialization, rejected unstable record accessors, registered record class
  resources for native validation, and preflighted URI text before UTF-8
  allocation.
- An eighth review pass retained caller-created immutable records without
  rerunning canonical constructors and made selected-body keys and requests
  share one `ReactiveHttpClientJsonCodec` byte representation.
- A ninth review pass rejected unbounded custom container/record request-target
  conversions, reproduced compiler-generated record text structurally under the
  projection budget, distinguished absent from present-empty selected bodies,
  and preflighted selected String and serialized-body lengths before copying.
- A tenth review pass exports normalized parameter, lowercase header, context,
  and shared-response isolation settings in effective contract snapshots;
  rejects selected application-defined list, set, and map bodies before
  defensive copying can change their codec subtype; freezes only request-target
  and explicitly selected request variants; and bounds selected header
  projection before ordinary request argument resolution can materialize
  expanded values. A further review rejects custom nested header and enum
  conversions before invoking them, and routes selected JSON through a
  codec-owned bounded writer instead of checking a fully allocated byte array.
- `mvn -q -s .mvn/maven-central-settings.xml -pl reactive-http-client-test -am
  test` passed `1142` starter tests and `55` test-helper tests with zero
  failures, errors, or skips. Metadata JSON validation and `git diff --check`
  also passed.

---

## Priority 6 - Phase One: Bounded Local TTL Cache

### [x] 6.1 Integrate a proven local cache implementation

- [x] Select a maintained local cache implementation with bounded maximum-size
      eviction and monotonic expiry; do not hand-roll concurrent eviction.
- [x] Keep implementation types out of public starter APIs unless deliberately
      reviewed as long-term extension points.
- [x] Decide and verify dependency packaging so cache-disabled consumers do not
      fail when the optional implementation is absent.
- [x] Fail startup clearly only when a client selects caching but the required
      implementation is unavailable.

### [x] 6.2 Implement hit, miss, and storage boundaries

- [x] Build one cold per-subscription cache lookup around eligible declarative
      invocations.
- [x] Require the validated Priority 5 key/variant decision before any cache
      lookup or response storage can occur.
- [x] Return an unexpired hit without downstream resilience admission, redirect,
      pool acquisition, or HTTP dispatch only after all mandatory pre-lookup
      policy/auth/key-partition gates succeed.
- [x] Execute the existing logical-call pipeline once for a phase-one miss and
      store only its final successful decoded value.
- [x] Preserve phase-one behavior where concurrent misses may execute separate
      loads; do not claim single flight before Priority 7.
- [x] Publish concurrent same-key loads conditionally so the first successful
      fill for the observed generation wins; a late duplicate returns to its
      caller but cannot replace the entry or restart TTL.
- [x] Prevent a late duplicate from repopulating after expiry, eviction, refresh,
      or shutdown has advanced the entry generation.
- [x] Prove hit/miss behavior with deterministic time and dispatch counters,
      including reversed duplicate-load completion order and auth/tenant/locale
      variants.

### [x] 6.3 Bound expiry, eviction, identity, and lifecycle

- [x] Enforce hard TTL with monotonic time and maximum-size eviction under
      concurrent access.
- [x] Cover expiry, replacement, size eviction, load failure, cancellation, and
      factory shutdown without retaining orphaned entries.
- [x] Document that cached mutable values are returned by identity and are not
      copied or serialized solely for caching.
- [x] Verify aggregate configured capacity and current size can be inspected
      without exposing entries or keys.

Evidence recorded on 2026-08-23:

- Caffeine `3.2.3`, managed by the Spring Boot 4 BOM, is an optional starter
  dependency behind package-private storage/manager types. Disabled clients do
  not link the implementation; selected clients verify availability during
  proxy construction and fail with client/policy/dependency identity when it is
  absent.
- One manager per client owns one hard-TTL/maximum-size Caffeine cache per
  selected policy. A package-private snapshot exposes only policy count,
  aggregate configured capacity, current size, and closed state. Factory
  destruction invalidates all entries before transport disposal and advances
  the closed generation so late loads cannot publish.
- Declarative cache lookup is cold per subscription and occurs only after the
  Priority 5 opaque key is derived. One logical-call deadline starts before
  cache preparation and authorization, then transfers to the miss pipeline's
  existing subscription-reporting state so response-body timeout attribution is
  retained. The frozen context variant snapshot is applied to authorization and
  lookup. Configured auth traverses normal upstream WebClient/default-request
  header mutations and validates its result before lookup; its result is
  consumed once by `OutboundAuthFilter`. Later resilience attempts resolve
  current auth while one-time 401 replay remains attempt-local. A hit does not
  build or subscribe the existing load pipeline, so it consumes no resilience
  operator or HTTP dispatch. The provider-aware public creation overload and
  mock helper preserve this gate; legacy low-level entry points fail closed for
  authenticated cached methods when provider/base-URL inputs are unavailable.
- Phase-one misses remain independent. Generation-checked publication makes the
  first successful completion win without replacing its value/TTL; deterministic
  reversed-completion, expiry, and eviction tests prove late duplicates cannot
  restore an obsolete generation. Expiry processing precedes the publication
  generation check. Empty completion, failure, cancellation, expiry, capacity
  eviction, and shutdown create no orphaned entry.
- Cached values retain decoded object identity. `ResponseEntity` hits retain
  status/body identity plus an allowlisted 32-value/16-KiB representation-header
  subset. Redirects and credential/session/auth-challenge responses are
  non-cacheable for both plain bodies and `ResponseEntity`; oversized retained
  headers are also non-cacheable.
- `BoundedLocalResponseCacheContractTest` passes `20` deterministic contracts,
  including the real factory-destroy path, wire-header/redirect rejection,
  public-construction auth safety, retry/auth freshness, upstream auth headers
  and validation, frozen pre-lookup context, late expiry publication, and
  hit/miss logical timeout attribution. `OutboundAuthFilterTest`
  passes `15` contracts, including the non-dispatching cache auth probe and
  compatibility for the prior direct pre-resolved attribute form. The complete
  reactor passes starter `1159`, test-helper `55`, and OTel `52` tests with zero
  failures, errors, or skips. An isolated assembled-consumer dependency tree
  contains the starter but no Caffeine artifact, and `git diff --check` passes.

---

## Priority 7 - Phase Two: Request Coalescing / Single Flight

### [x] 7.1 Add separately opt-in single flight

- [x] Keep single flight disabled unless the selected cache policy enables it.
- [x] Share exactly one in-flight load for concurrent misses of the same key.
- [x] Keep different keys, clients, methods, and policies independent.
- [x] Prove one transport dispatch and one request-body subscription with
      latches/barriers rather than sleep-only timing.

### [x] 7.2 Preserve caller subscription ownership

- [x] Give every waiter its own subscription-local timeout, cancellation, and
      one terminal lifecycle/observer/exchange record.
- [x] Keep the shared load alive while any caller remains interested; the first
      caller's outer logical timeout must detach only that caller rather than
      terminating the load for later waiters.
- [x] Keep request/attempt timeouts and any explicit shared-load safety bound
      effective without borrowing one caller's logical deadline.
- [x] Ensure cancelling one waiter does not cancel a load required by another.
- [x] Define and test cancellation when the last interested caller leaves;
      abandoned work cannot populate the cache accidentally.
- [x] Fan out load success, error, empty completion, and cancellation
      deterministically, then remove completed/failed in-flight state.
- [x] Prove slow/failed keys do not hold a global lock or block unrelated keys.
- [x] Deterministically prove both timeout directions: waiter timeout with first
      caller success, and first-caller timeout with later waiter success.

### [x] 7.3 Keep hidden replay inside the leader

- [x] Run Retry, one-time auth replay, redirects, and transport dispatch only in
      the leader load.
- [x] Prevent each waiter from consuming resilience permits or creating hidden
      replays.
- [x] Keep each waiter's terminal cache outcome distinct from the leader's HTTP
      attempt and dispatch facts.
- [x] Cover waiter timeout/cancellation while the leader retries, redirects, or
      refreshes auth.

Evidence recorded on 2026-08-23 and revalidated on 2026-08-24:

- `single-flight` is a default-false cache-policy property and is included in
  generated configuration metadata and effective-contract snapshots.
  `LocalResponseCacheManager` owns one in-flight state machine per cache and
  opaque key. Cache recheck and member reservation are atomic, and its terminal
  sink cannot reconnect after removal. Phase-one duplicate-miss behavior is
  unchanged when the property is false.
- Subscription-local reporting wraps each coalesced caller. Logical-call
  deadlines and cancellation detach only that caller, while request/attempt
  timeouts remain inside the shared leader. The transport uses a flight-owned
  reporting state and freezes attempt evidence into the current diagnostic
  owner, so later retries never mutate an already-terminal caller. Last-caller
  cancellation and factory shutdown cancel the load without publishing a late
  cache entry.
- `BoundedLocalResponseCacheContractTest` uses virtual time, latches, a real
  Reactor Netty redirect server, an auth-refresh-plus-retry fixture, and a real
  body inserter to prove both timeout directions, one dispatch/body
  subscription, independent keys/policies, deterministic terminal fan-out,
  leader-only replay, waiter cancellation during replay, stale-miss recheck,
  and delayed reserved-member attachment. The cache contract suite passes `31`
  tests. The complete reactor passes starter `1172`, test-helper `55`, and OTel
  `52` tests with zero failures, errors, or skips.

---

## Priority 8 - Phase Three: Refresh on Access

### [x] 8.1 Add a bounded refresh state machine

- [x] Keep refresh disabled unless explicitly selected for a cache policy.
- [x] Require positive `refresh-after` strictly below the hard TTL.
- [x] Require a positive finite refresh timeout and cap each refresh at the
      earliest of that deadline, hard expiry, and factory shutdown.
- [x] Return the current value after refresh-after and trigger at most one
      access-driven refresh for that key.
- [x] Keep concurrent stale callers on the current value without starting
      duplicate refresh loads.
- [x] Separate miss single-flight and refresh single-flight transitions.

### [x] 8.2 Preserve hard expiry and live invocation context

- [x] Use the live triggering invocation's validated key/variant context rather
      than retaining arbitrary argument graphs or scheduling invented requests.
- [x] Run refresh through the same pre-lookup gates, auth, selected resilience
      operators, redirects, request/response timeouts, and transport pipeline as
      a miss while bypassing recursive cache lookup.
- [x] Atomically replace on refresh success and restart entry age.
- [x] Preserve the current value on refresh failure only until hard TTL.
- [x] Never serve stale after hard expiry; a later caller follows normal miss
      and single-flight behavior.
- [x] Cancel a refresh at hard expiry and prevent its late result from
      repopulating the cache.
- [x] Use a non-terminating refresh fixture to prove deadline cancellation and
      release of invocation/auth/key state after the stale caller completes.
- [x] Cover refresh failure, cancellation, late completion, hard-expiry race,
      and eviction during refresh deterministically.

### [x] 8.3 Own refresh shutdown and resources

- [x] Cancel or await cache-owned refresh work under the factory's existing
      aggregate shutdown deadline.
- [x] Prevent late refresh completion from repopulating a closed or evicted
      cache.
- [x] Release values, key snapshots, invocation context, and in-flight state on
      shutdown.
- [x] Prove no cache scheduler/thread/resource survives factory destruction.

Evidence recorded on 2026-08-24:

- `refresh-after-ms` and `refresh-timeout-ms` are nullable policy fields, so
  refresh remains off unless both are selected. Startup requires positive bounded
  values and a refresh threshold strictly below hard TTL; generated metadata and
  effective-contract snapshots expose the normalized decision.
- Caffeine entries retain monotonic write age and issue opaque generation-bound
  refresh tokens. One manager-owned refresh map per cache/key is independent from
  miss single flight. Stale callers return the current object immediately while one
  hidden load reuses the triggering frozen arguments, Reactor context, prepared auth,
  resilience, redirect, timeout, transport, response-header, and decode path without
  recursively entering cache lookup.
- Refresh success replaces only the exact current generation and restarts age. Failure
  leaves the old value only to hard TTL. The effective timeout is the earlier of the
  configured refresh deadline and remaining hard TTL; Caffeine removal callbacks,
  factory shutdown, and Reactor timeout cancel work, while generation checks reject
  late completion after expiry, size eviction, replacement, or closure. The manager
  uses Reactor shared scheduling and owns no scheduler or thread.
- `BoundedLocalResponseCacheContractTest` uses a monotonic test ticker, Reactor
  virtual time, controllable sinks, and a declarative auth/resilience fixture to prove
  fresh versus stale access, one concurrent refresh, success, failure, timeout, hard
  expiry, late completion, size eviction, live triggering context, pipeline reuse, and
  factory shutdown. The focused cache suite passes `38` tests. The complete
  reactor passes starter `1179`, test-helper `55`, and OTel `52` tests with
  zero failures, errors, or skips.

---

## Priority 9 - Cache, Resilience, Auth, Redirect, and Timeout Composition

### [x] 9.1 Freeze operator boundaries

- [x] Place lookup after mandatory cache-aware policy, authorization, tenant,
      and key-partition gates but before downstream resilience, redirect, pool,
      and transport work.
- [x] Reject caching for a client with any unclassified Boot/per-client
      customizer or builder mutation; a hit cannot bypass dynamic request,
      authorization, tenant, exchange-function, filter, codec, connector, or
      response-transformation behavior.
- [x] Keep a miss leader behaviorally identical to an uncached invocation until
      its final successful decoded value is stored.
- [x] Make refresh bypass lookup only and reuse the miss auth/resilience/
      redirect/timeout/transport pipeline, with separate hidden-refresh terminal
      reporting.
- [x] Include lookup and each caller's coalesced wait in that caller's logical
      timeout without placing any caller-specific outer deadline inside the
      shared load publisher.
- [x] Keep non-GET/unsafe methods ineligible regardless of idempotency keys.
- [x] Do not infer read invalidation from any write method.

### [x] 9.2 Preserve terminal-state isolation

- [x] Prevent prior load/refresh URL, status, headers, error, failure stage,
      attempt count, body size, and dispatch facts from leaking into a hit.
- [x] Prevent leader terminal state from being shared as a waiter's
      subscription-local terminal state.
- [x] Keep generated idempotency keys, prepared headers, attempt cleanup, and
      logical timing isolated per caller/load as applicable.
- [x] Cover hit, miss, waiter, timeout, cancellation, auth failure, open circuit,
      redirect, retry exhaustion, and refresh failure in one composition suite.

### [x] 9.3 Preserve envelope and object contracts

- [x] Return cached `Mono<T>` values without re-decoding or re-serializing.
- [x] Preserve cached `ResponseEntity<T>` value and status while copying only a
      documented bounded allowlist of representation headers.
- [x] Treat `Set-Cookie`, auth challenges, `SensitiveHeaders`, and configured
      per-caller response headers as non-cacheable, and never replay
      non-allowlisted headers to later callers.
- [x] Verify load responses and cache hits document their header difference and
      never expose the first caller's session/identity headers.
- [x] Prove mutable-value identity behavior and document caller responsibility.
- [x] Reject any later response shape that cannot retain deterministic ownership
      and terminal semantics.


Evidence recorded on 2026-08-24:

- `BoundedLocalResponseCacheContractTest` now serves as the deterministic feature-
  composition suite. Existing real-transport and controlled-publisher cases cover
  coalesced miss/waiter ownership, both timeout directions, cancellation, redirects,
  one-time auth replay, retries, and refresh; focused additions cover auth rejection,
  open-circuit rejection, retry exhaustion, failed refresh, successful hits, and an
  explicit write without inferred invalidation. Hits and pre-dispatch failures retain
  zero attempts with no prior URL, status, request headers, response size, or failure
  stage, while dispatched failures retain only their final attempt evidence.
- Lookup remains after frozen key/context and mandatory pre-lookup auth gates but before
  downstream resilience and transport. Miss leaders and refreshes reuse the normal load
  pipeline; each coalesced caller owns its logical deadline and terminal state. The
  existing startup inventory still rejects every applicable Boot/per-client builder
  customization unless it is explicitly classified `SAFE`.
- Cache policies now expose a bounded, case-insensitive
  `non-cacheable-response-headers` list. Final wire metadata is checked for plain and
  `ResponseEntity` responses; configured per-caller headers, `SensitiveHeaders`,
  cookies, and auth challenges prevent storage. Cached envelopes preserve body identity
  and status while retaining only the documented representation-header allowlist.
- The focused cache/configuration/metadata/snapshot run passes `114` tests with zero
  failures, errors, or skips.

---

## Priority 10 - Phase Four: Cache Metrics, Observability, and Diagnostics

### [x] 10.1 Freeze one cache outcome vocabulary

- [x] Define bounded caller outcomes for fresh hit, miss loader, coalesced
      waiter, and stale hit with refresh.
- [x] Define bounded hidden-work outcomes for load/refresh success, failure, and
      cancellation plus TTL and size eviction.
- [x] Add public event/context fields only after null/unknown behavior and
      compatibility constructors are reviewed.
- [x] Keep raw keys, values, arguments, headers, bodies, URLs, tenant values,
      and credentials out of every outcome model.
- [x] Add a separate cache-observability opt-in that defaults false and remains
      subordinate to the existing global observability master gate.

### [x] 10.2 Implement and verify Micrometer meters

- [x] Add a lookup counter by bounded client, API name, and `hit`/`miss` result.
- [x] Add separate coalesced, stale-serving, load, refresh, and eviction facts
      without making hit ratio ambiguous.
- [x] Add load/refresh duration only under distinct meter names and correct
      registry time units.
- [x] Export current entry count, configured maximum size, and eviction count
      under starter-specific names that cannot collide with cache-library meters.
- [x] Track every cache meter registration as factory-owned state and remove all
      counters, timers, summaries, and gauges from the `MeterRegistry` during
      factory destruction.
- [x] Prove destroy is idempotent, releases cache references, and prevents late
      load/refresh completion from recording into removed meters.
- [x] Destroy and recreate a factory with the same meter tags against one live
      registry; verify the replacement gauge observes only the new cache and no
      stale meter registration is reused.
- [x] Verify meter absence when caching is unselected and prove metrics enablement
      cannot activate caching.
- [x] Verify a cache-enabled, cache-observability-disabled client records no
      cache-library stats, cache meters, cache OTel attributes, or cache-specific
      log/context fields.
- [x] Add Prometheus scrape tests for names, types, tags, zero-series behavior,
      hit ratio, coalescing ratio, refresh failure rate, and capacity pressure.

### [x] 10.3 Align terminal observability surfaces

- [x] Report a hit once with `attemptCount=0`, `requestDispatched=false`, and no
      invented HTTP status, server, transport stage, or wire byte count.
- [x] Keep miss-loader HTTP facts while distinguishing coalesced waiters and
      stale hits from dispatched calls.
- [x] Align lifecycle, observer, exchange logger, Micrometer, and OTel on the
      same bounded caller cache outcome.
- [x] Add one bounded cache outcome attribute to built-in OTel logical-call
      spans without exporting keys or values.
- [x] Keep hidden refresh from silently creating detached OTel spans; expose it
      through reviewed bounded meters and sanitized logs unless a later explicit
      span opt-in is approved.
- [x] Prove exactly one terminal record per caller for hit, miss, waiter, stale
      hit, timeout, cancellation, load failure, and refresh failure.

### [x] 10.4 Align diagnostics, health, and operations

- [x] Export only bounded policy and aggregate state: enabled phase, TTL,
      refresh threshold, single-flight state, maximum size, entry count, and
      evictions.
- [x] Never enumerate cache entries or expose keys through the diagnostics
      endpoint, snapshot helper, health, or support bundle.
- [x] Keep hit ratio, refresh failure, eviction pressure, and entry count as
      operational signals that do not mark downstream health UP/DOWN by
      themselves.
- [x] Add bounded support-bundle examples and PromQL/dashboard recipes for each
      supported cache signal.

Evidence recorded on 2026-08-24:

- `HttpClientCacheOutcome` freezes the four caller-visible values, while
  load/refresh work and TTL/size evictions use separate bounded meter tags.
  Existing event/context constructors preserve `null` for unselected or
  metrics-disabled caching; no cache key or selected value enters a public
  observability surface.
- `reactive.http.observability.cache.enabled` defaults to `false`, requires
  the global observability gate, and does not activate caching. The optional
  Micrometer implementation is isolated from the always-loaded cache facade,
  preserving no-Micrometer test-helper consumers.
- Factory-owned `reactive.http.client.cache.*` counters, timers, and gauges
  cover lookups, caller outcomes, coalescing, stale serving, loads, refreshes,
  TTL/size evictions, entries, and maximum entries. Deterministic tests cover
  zero series, Prometheus names/tags and ratios, registry time units,
  idempotent removal, late completion, and same-tag factory recreation.
- Lifecycle hooks, observers, exchange logs, Micrometer, and OTel share the
  caller outcome. Non-dispatched hits/waiters/stale callers retain zero attempts
  and unknown wire size; hidden refreshes produce bounded work meters and
  sanitized DEBUG facts without detached caller terminals or OTel spans.
- Diagnostics expose only bounded policy/aggregate cache state and never entries
  or keys. Health parity tests prove cache gauges and refresh failures do not
  change downstream health. The operations guides include bounded support data
  plus hit, coalescing, refresh-failure, and capacity PromQL recipes.
- Follow-up regression coverage records refresh cancellation exactly once when
  eviction wins before subscription attachment, keeps detached coalesced waiters
  transport-empty, strongly retains the maximum-entry gauge supplier, and keeps
  cache-served callers out of the downstream request timer and health samples
  while preserving custom-observer and OTel terminal reporting.
- `mvn -q test` passed `1199` starter, `55` test-helper, and `53` OTel
  tests (`1307` total) with zero failures, errors, or skips. The focused
  no-Micrometer mock/test-helper run and `git diff --check` also passed.

---

## Priority 11 - Mock, Consumer, AOT, Native, and Shutdown Parity

### [x] 11.1 Extend the mock helper without claiming wire evidence

- [x] Add deterministic clock/policy setup and hit/miss/coalesced/refresh
      assertions to `MockReactiveHttpClient`.
- [x] Expose load counts and explicit eviction for tests without leaking the
      selected cache implementation into public APIs.
- [x] Preserve production client names, metadata-cache replacement, auth,
      lifecycle ordering, observers, and final-request behavior.
- [x] Document that the helper does not prove transport dispatch, pool reuse,
      socket cancellation, or native cleanup.

### [x] 11.2 Prove assembled Boot 4 consumer behavior

- [x] Cover cache-disabled, one-method opt-in, client-wide opt-in/exclusion,
      TTL expiry, capacity eviction, single flight, and refresh.
- [x] Cover explicit retry-only activation and enabled-only no-operator behavior.
- [x] Record effective POM, dependency tree, classpath, test reports, source
      commit/state, and no-reactor-leakage checks incrementally.
- [x] Preserve reports from failed stages without uploading stale prior-run
      evidence.

### [ ] 11.3 Revalidate AOT, native, and shutdown

- [x] Add precise runtime hints for selected public cache metadata and required
      implementation resources without broad package reflection.
- [x] Keep starter-factory ownership and replacement metadata-cache boundaries
      during AOT validation.
- [x] Compile and run native smoke for one cached read, one actual load, one
      refresh, and explicit retry-only activation while counting all dispatches.
- [x] Prove factory destruction clears entries and terminates coalesced loads and
      refresh work within the shared shutdown deadline.
- [x] Prove destruction removes factory-owned cache meters before same-tag
      recreation and leaves no registry reference to the closed cache.
- [ ] Record clean commit, GraalVM/JDK versions, dependency list, executable
      status, and binary SHA-256.

Evidence recorded on 2026-08-25:

- `MockReactiveHttpClient` now has an opt-in deterministic cache clock, inert
  policy helper, cache outcomes, load counts, entry count, and explicit
  eviction. Its tests preserve auth, replacement metadata, lifecycle,
  observer, client-name, and final-request behavior and explicitly avoid wire,
  pool, socket, or native claims.
- The isolated `v27-current-parity` Boot 4 consumer profile covers disabled,
  method-selected, client-selected/excluded, TTL, capacity, single-flight,
  refresh, explicit Retry, and enabled-only resilience. The fresh-repository
  verifier passed `56` test-helper and `4` assembled-consumer tests and retained
  effective POM, dependency tree, classpath, Surefire reports, artifact hashes,
  stage provenance, and no-reactor-output-leakage evidence. Follow-up consumer
  evidence also proves that cache-enabled mock consumers receive Caffeine
  transitively from `reactive-http-client-test` without declaring it directly.
- AOT validation selects a unique primary programmatic
  `ReactiveHttpClientProperties` bean before binding the AOT environment, while
  retaining starter-factory and replacement-metadata ownership. Runtime hints
  remain narrow: public cache annotations/configuration, selected record
  accessors, and the exact Caffeine `SSLMSW` constructor/`FACTORY` lookup used by
  the starter's bounded expire-after-write cache.
- The original clean commit `9d537313520d62d6261c0baa18697db886b4cce7` compiled and ran
  the expanded Boot `4.0.0` fixture with Java and GraalVM `25.0.3`. The
  provenance records `sourceState=clean`, starter `4.0.0-SNAPSHOT`, the full
  dependency list, and `executableStatus=passed`. The executable proved one
  load plus one hit, one refresh dispatch, explicit two-attempt Retry, zero
  open-circuit dispatch, and exact total dispatch count. Its SHA-256 is
  `4bceb1e8a5faf326b1a3c903a25dca1b38b77b6e3d01694f502af18d441e5846`;
  this immutable evidence predates the follow-up AOT properties fix.
- Follow-up clean commit `8b0a0a596e20395b64b8b3c14725b284b4aaa445`
  compiled and ran the same fixture with Java and GraalVM `25.0.3` and Spring
  Boot `4.0.0`. The refreshed provenance records `sourceState=clean`, starter
  `4.0.0-SNAPSHOT`, the full dependency list, and `executableStatus=passed`.
  Its SHA-256 is
  `90c983475ac3d5bdafd49aa708b3fc8d9ca4eda2699a2070d7dc3d55342d00e2`,
  superseding the provisional dirty-tree validation. This immutable evidence
  predates the follow-up eviction and non-deterministic mock-ownership fixes.
- Follow-up lifecycle coverage proves explicit eviction advances active load
  generations without cancelling caller-visible work, preventing a pending
  single flight from repopulating the cache. Every mock construction path now
  retains and closes its cache manager; a non-deterministic cache-enabled mock
  cancels active work during `close()`. The full reactor and fresh isolated
  consumer verifier pass. Clean native provenance remains open until these
  review fixes are committed and rerun from that commit.
- Factory-destruction coverage proves active load/refresh cancellation, entry
  release, aggregate shutdown completion, meter removal, and same-tag registry
  recreation without retaining the closed cache.

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
