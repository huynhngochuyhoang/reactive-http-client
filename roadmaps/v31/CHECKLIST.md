# Reactive HTTP Client - Roadmap V31 Execution Checklist

> **Status:** active
> **Published/API baseline:** `4.3.0`
> **Development line:** `4.4.0-SNAPSHOT`
> **Release scope:** unselected

Companion to [`ROADMAP.md`](ROADMAP.md). Execute priorities in order unless a
confirmed correctness or release blocker requires reordering. Checklist adoption
starts execution planning; it does not complete Priority 1, implement a helper,
or select a release. All implementation and evidence items below remain open.

Check an item only after its implementation, verification and disposition are
recorded under that priority. Keep generated evidence under
`target/release-evidence/v31/priority<N>/`: commands, actual test totals,
toolchain versions, commit, dirty/clean state, settings and report paths. Preserve
failed runs and partial reports. Native and release-quality performance records
require exact clean source provenance and artifact/report hashes; a local
pre-squash hash must not stand in for a reachable reviewed revision.

## Execution Gates

- Priority 2 separates casing, filtering and subscription-boundary behavior
  before changing production code. The reported deployment is not proof of an
  Istio defect or starter context loss.
- Priority 3 freezes and implements the additive lookup contract. Keep existing
  bulk-map spelling, string keys, constructors and restoration semantics.
- Priorities 4-6 prove protocol, handoff and composition behavior before claiming
  safer application integration; a helper is not a forwarding or trust policy.
- Priority 7 requires real assembled-consumer and native evidence, not only mock
  tests. Optional manual mesh reproduction remains explicitly unverified or
  unnecessary with a recorded reason, never an assumed pass.
- Priority 8 uses observable structural evidence only. New public telemetry or
  configuration needs separate justification and review; it is not implied here.
- Priority 9 distinguishes harness smoke, allocation/retention evidence and
  release-quality comparison. Keep manual commands available for the user.
- Public/API/consumer/benchmark baselines stay at `4.3.0`. Priority 10 selects
  minor, patch, documentation-only or no-go scope; signing, publication and fresh
  Central verification are separate gates.

## Required Invariants

| Surface | Required behavior |
|---|---|
| Existing bulk context map | Preserve captured spelling, exact-key access and published snapshot semantics |
| New named lookup | ASCII case-insensitive field-name comparison; no value conversion or exact-case preference |
| Absence and empty list | Empty immutable values / empty optional; empty string remains a present value |
| Multiple matching values | Preserve multiplicity and exposed ordering; single-value access fails explicitly |
| Filtering and redaction | Do not recover dropped/redacted data or silently broaden capture defaults |
| Explicit snapshot restore | Present fields replace matching target keys; absent fields do not clear them |
| Caller and shared work | Independent caller snapshots; no implicit identity, authorization or cache partition |
| Terminal ownership | No stranded reservations, duplicate terminal callbacks or retained starter-owned context |
| Evidence | No arbitrary header names/values, credentials, payloads or identities in operational artifacts |

---

## Priority 1 - Post-`4.3.0` Baseline and V31 Scope Integrity

### [ ] 1.1 Align published and development lanes

- [ ] Verify root/modules, benchmark, native and current-consumer coordinates
      remain `4.4.0-SNAPSHOT`; do not bump them merely to adopt execution.
- [ ] Keep README/quick-start coordinates, `latest.published.version`, strict
      API, published consumer and benchmark baselines at `4.3.0`.
- [ ] Verify the current published-baseline benchmark profile includes V30
      work-limit rows; preserve explicitly selected historical exclusions.
- [ ] Preserve V1-V30 completed records and V30 release/tag/native/performance
      provenance. The adopted V27 resilience proposal is not new V31 scope.

### [ ] 1.2 Reprove the published baseline

- [ ] Resolve all 13 parent/module POM, binary, source and Javadoc artifacts from
      a previously absent Central-only repository; record versions, hashes and
      remote repository markers.
- [ ] Run the published `4.3.0` assembled consumer without reactor-output leakage;
      retain effective POM, dependency tree, classpath, actual totals and provenance.
- [ ] Run independent strict root and starter-module source/binary comparisons
      against fresh `4.3.0` repositories.
- [ ] Run API/published-baseline negative fixtures for self-comparison,
      contamination, missing attachments and inconsistent declared versions.

### [ ] 1.3 Verify adopted execution state

- [ ] Verify V31 is the sole active roadmap with this linked checklist and exact
      active-status matching; retain all V1-V30 completion checks.
- [ ] Regenerate readiness with active roadmap `v31`, release lane `unselected`,
      snapshot development, no planned final release and an unpublished deferred
      candidate. Adoption must not imply completed implementation.
- [ ] Run Maven validation, `DocumentationReleaseArtifactTest` and
      `git diff --check`; record actual commands and results for this priority.

## Priority 2 - Inbound Header and Context Characterization

### [ ] 2.1 Reproduce the named-lookup failure independently of a mesh

- [ ] Use fake bounded headers with mixed, lower and upper case; record that the
      Reactor key and matching captured entry exist while exact `Map.get` misses.
- [ ] Distinguish the absent map entry that makes `getFirst()` dereference null
      from an empty list, empty string, redacted value and malformed JSON.
- [ ] Reproduce against server `HttpHeaders`, ordered application maps and
      capture/restore snapshots; do not infer pre-proxy spelling from the map.
- [ ] Keep production behavior unchanged while these baseline cases are recorded.

### [ ] 2.2 Characterize independent missing-value causes

- [ ] Test absent context key, outside-allow-list names, denied selected fields,
      empty lists, empty strings, multiple values and differently cased aliases.
- [ ] Compare an ordinary composed subscription with an independent sink or
      callback subscription, with and without explicit snapshot restoration.
- [ ] Characterize raw malformed values under the public context key separately
      from well-formed absence; record the actual failing boundary.
- [ ] Record WebFilter registration, replacement and capture order relevant to
      the reproduction without changing security-filter ordering.

### [ ] 2.3 Classify the evidence and narrow production work

- [ ] Record each observation as verified defect, documented caller misuse,
      additive usability gap or unverified deployment hypothesis.
- [ ] Inventory existing tests/docs that intentionally preserve spelling,
      string keys, immutable values and restore precedence.
- [ ] Require a focused reproducer and compatibility review for any correction
      beyond the additive named-access API.
- [ ] Record whether direct protocol tests suffice or a normalizing intermediary/
      manual mesh scenario is needed; do not claim Istio caused an unobserved change.

## Priority 3 - Case-Insensitive Header Access Contract

### [ ] 3.1 Freeze the additive surface before implementation

- [ ] Choose final method names/signatures for immutable all-values and optional
      single-value access on `RequestContext`; prefer existing local APIs.
- [ ] Specify absence, empty-list, empty-string, redaction and ambiguity behavior
      exactly as the roadmap table, including duplicate equal values.
- [ ] Specify ASCII field-name token validation, locale-independent matching,
      null arguments and bounded structural misuse errors with no raw value text.
- [ ] Define malformed map/key/list/element validation, including entries outside
      the requested name, so malformed input cannot accidentally become absence.
- [ ] Preserve existing bulk map, key aliases, snapshot record components/
      constructors and merge behavior. Do not introduce a global normalized map.

### [ ] 3.2 Implement and test named access

- [ ] Read only the captured context snapshot; do not access the exchange, MDC,
      live request, auth provider or alternative credentials.
- [ ] Collect all case-insensitive matches in map-iteration and per-entry value
      order without exact-case preference, deduplication, joining or splitting.
- [ ] Return empty values/optional for absence; retain a present empty string and
      fail single-value lookup when more than one value exists across aliases.
- [ ] Preserve case and bytes of values; reject invalid names without underscore/
      hyphen substitution, Unicode case folding or locale-dependent matching.
- [ ] Return defensive immutable values; test mutation of source maps/lists after
      capture and attempted mutation of returned collections.
- [ ] Test wrong context types and malformed raw maps/lists with bounded
      structural errors; never stringify arbitrary application values for errors.

### [ ] 3.3 Lock compatibility and application responsibility

- [ ] Preserve legacy exact-key behavior for captured spellings and public string
      aliases; test against the existing snapshot and contributor contracts.
- [ ] Cover reordered aliases/values, equal duplicates, unusual legal token
      characters, invalid names and a non-English default locale with restoration.
- [ ] Demonstrate required-header validation and parsing as application-owned
      decisions; no default identity, automatic JSON codec or silent first-value
      selection is introduced.
- [ ] Verify manual context writes are not falsely described as globally
      sanitized, immutable or authenticated simply because a reader helper exists.

## Priority 4 - WebFlux, Protocol, and Filter Boundaries

### [ ] 4.1 Prove actual inbound wire behavior

- [ ] Exercise real loopback WebFlux requests over HTTP/1.1 and HTTP/2 using fake
      bounded fields; assert negotiated protocol and actual captured field names.
- [ ] Test lowercase names on both protocols and mixed-case HTTP/1.1 input;
      do not send invalid uppercase HTTP/2 field names to prove normal behavior.
- [ ] Compare exact bulk-map access and new named access for the same request,
      including repeated values and a required-but-missing field.
- [ ] Add a deterministic normalizing intermediary only if direct protocol
      evidence leaves a necessary boundary uncovered; use gates, not setup sleeps.

### [ ] 4.2 Preserve filter and registration semantics

- [ ] Test case-insensitive allow/deny matching, redaction precedence and
      outside-allow-list omission without widening existing defaults.
- [ ] Test request mutation before and after capture; snapshots reflect only the
      request visible to the capture filter and remain immutable afterward.
- [ ] Verify default and replacement WebFilter registrations without reordering
      application security filters or adding automatic outbound forwarding.
- [ ] Cover the documented WebFlux application boundary; confirm a WebClient in
      an MVC application alone does not imply an inbound servlet capture bridge.

### [ ] 4.3 Record protocol versus deployment evidence

- [ ] Document tested client/server protocol hops and fixture versions; a
      simulated intermediary is not a certification of an Istio deployment.
- [ ] For needed manual mesh reproduction, provide bounded commands and required
      version/protocol facts, with no exported header values or identity material.
- [ ] Record manual evidence as verified, unverified, or not required with
      rationale. Never turn unavailable cluster access into a passing mesh claim.

## Priority 5 - Explicit Async Handoff and Caller Isolation

### [ ] 5.1 Cover composed and independent subscriptions

- [ ] Test `deferContextual`, `publishOn`, `subscribeOn` and nested composed
      publishers with context read at subscription time.
- [ ] Show independent sink/executor subscribers do not acquire the emitter's
      context automatically, then demonstrate explicit snapshot handoff.
- [ ] Use concurrent fake request envelopes and gates to verify a shared worker
      does not reuse another caller's headers or correlation ID.
- [ ] Repeat subscriptions to the same cold publisher with distinct contexts;
      no eager method-invocation snapshot may override subscription-local values.

### [ ] 5.2 Preserve capture/restore and contributor contracts

- [ ] Verify present snapshot fields replace target values while missing fields
      leave target values intact; demonstrate isolated targets when reusing workers.
- [ ] Preserve contributor order by order/key and existing correlation/idempotency
      precedence, including explicit outbound-header precedence.
- [ ] Keep snapshots limited to correlation ID and inbound headers; idempotency
      or custom context needs explicit application handling, not automatic capture.
- [ ] Test immutable copies without retaining live exchanges, request objects or
      arbitrary context maps in new helper state.

### [ ] 5.3 Verify terminal release and external ownership

- [ ] Exercise completion, error, timeout and cancellation around handoff and
      starter-owned callbacks; prove cleanup with controlled lifecycle evidence.
- [ ] Use weak references/reference queues where justified to distinguish
      starter-owned retention from application queues or retained records.
- [ ] Close executors/subscriptions created by tests and record released owners;
      avoid exact GC deadlines or claims that RSS must immediately fall.
- [ ] Require separate evidence and a scoped fix for any retention defect found.

## Priority 6 - Cache, Auth, Retry, and Terminal-State Composition

### [ ] 6.1 Keep caller contexts separate from shared work

- [ ] Exercise uncached calls and fresh/stale hits with distinct caller snapshots;
      per-call authorization still runs where required.
- [ ] Gate same-key leader/waiter attachment and detach the first caller by
      cancellation and timeout; retain independent waiter terminal context.
- [ ] Cover hidden refresh and shared-load continuation without transferring
      another caller's headers into a visible terminal record.
- [ ] Use explicitly partitioned auth/header variants and final request identity
      checks; reading a header must not implicitly make it trusted or a cache key.

### [ ] 6.2 Preserve retry/replay and deadline behavior

- [ ] Cover selected Retry, auth invalidation/replay, redirects and logical-call
      deadlines without altering published selection, ordering or dispatch counts.
- [ ] Verify prepared arguments, correlation/idempotency precedence and final
      attempt evidence remain consistent through resubscription.
- [ ] Test auth rejection on a warm cache and identity changes after replay;
      named lookup must not bypass validation or cache isolation.
- [ ] Use deterministic subscriptions, source gates and terminal acknowledgements;
      immediate local disposal alone is not proof of transport cleanup.

### [ ] 6.3 Protect reporting and admission cleanup

- [ ] Test malformed raw context with observer, lifecycle and exchange logging
      enabled, both with and without selected cache-work admission.
- [ ] Prove capacity is released exactly once after setup/error/cancellation and
      no deferred continuation dispatches after a terminal rejection.
- [ ] Count terminal callbacks rather than overwriting a last-event reference;
      assert final error, cancellation, attempts, URL/status/header evidence.
- [ ] Preserve redaction and absence in existing terminal surfaces; no header
      values/names enter new metric tags, span attributes or support snapshots.

## Priority 7 - Mock, Assembled-Consumer, AOT, and Native Parity

### [ ] 7.1 Align mock access without silently changing assertions

- [ ] Test lowercase, mixed-case, absent, empty, duplicate and redacted captured
      values through mock recordings and the new public helpers.
- [ ] Preserve existing exact-spelling assertion behavior, or add explicitly named
      alternatives with tests and documentation rather than silently reinterpreting it.
- [ ] Cover context snapshots/handoff, repeat subscriptions and closure with both
      deterministic controls and the normal mock construction path.

### [ ] 7.2 Verify assembled-consumer and optional-integration parity

- [ ] Add current-consumer cases for lowercase inbound capture, optional/all-values
      access, ambiguity, explicit handoff and required-header failure.
- [ ] Verify assembled artifacts, effective POM, dependency tree and no reactor
      classpath leakage; preserve reports even when a stage fails.
- [ ] Keep published `4.3.0` consumer sources independent of candidate-only APIs.
- [ ] Preserve replacement beans, cache-disabled/no-Caffeine consumption and
      optional OTel absence; introduce no mandatory helper dependency.

### [ ] 7.3 Reprove AOT and native behavior on the final fixture

- [ ] Exercise the new public access from a real application context in JVM/AOT
      and native smoke, including capture and explicit restoration.
- [ ] Preserve configured replacement metadata/properties/filter beans and avoid
      reflective header-DTO scanning or unrelated runtime hints.
- [ ] Run clean-commit native compile and executable, recording toolchain, exact
      fixture commit, command, executable hash, output and test disposition.
- [ ] Rerun after relevant fixture/runtime changes; do not close with a pre-fix
      binary. Keep unsupported optional integration coverage explicitly bounded.

## Priority 8 - Documentation and Operations Guidance

### [ ] 8.1 Consolidate safe public examples

- [ ] Update the correlation/context, exchange logging, test-helper and production
      guides from the same frozen helper/filter/handoff contract.
- [ ] Replace nullable map-value `getFirst()` examples with explicit absence and
      multiplicity checks before application parsing.
- [ ] Preserve empty-string versus absent/redacted distinctions and application
      size/schema/trust validation; do not describe a redaction marker as identity.
- [ ] Separate published `4.3.0` workarounds from candidate-only APIs and use fake
      bounded names/values plus reserved placeholder domains in copyable examples.
- [ ] State WebFlux/MVC and scheduler/independent-subscription boundaries without
      implying automatic forwarding, MDC bridging or queue context propagation.

### [ ] 8.2 Add a structural troubleshooting path

- [ ] Distinguish case mismatch, filtered/redacted fields, wrong filter ordering,
      malformed context and independent-subscription loss using observable checks.
- [ ] Describe how to compare the same request at capture/read boundaries without
      collecting raw header values or attributing an unobserved rewrite to Istio.
- [ ] Define bounded support fields for versions, protocol hops, capture/read
      boundary, key/match presence and value counts using fake field aliases.
- [ ] If a new fixture is needed, add recursive privacy and type/invariant guards;
      keep arbitrary header names, tokens, identities, request targets and payloads out.
- [ ] Do not add meters or schema fields unless existing evidence is insufficient;
      record justification, bounded enums/types and privacy review before publishing.

### [ ] 8.3 Verify documentation is version-correct

- [ ] Run generated documentation, Markdown-link, archive-status and support
      validation tests with actual totals.
- [ ] Retain the current snapshot/published baseline split and V30 archived claims.
- [ ] Cross-check public examples against executable consumer tests; unsupported
      or optional manual mesh instructions must be labeled accurately.

## Priority 9 - Compatibility and Targeted Performance Evidence

### [ ] 9.1 Revalidate the supported artifact surface

- [ ] Run strict root and starter-module source/binary checks against independent
      fresh Central `4.3.0` repositories, including context and mock public APIs.
- [ ] Run negative compatibility/provenance fixtures and package/generation guards;
      do not relax strict checks to approve an accidental behavior or API break.
- [ ] Run complete tests and the supported dependency matrix; record actual totals,
      commands and toolchains, preserving failures and retried evidence separately.
- [ ] Include current/published consumers and final composition/shutdown regressions;
      retain AOT/native evidence at its exact source revision.

### [ ] 9.2 Measure the changed path, not an unrelated substitute

- [ ] Add targeted lookup/capture/restore measurements at realistic bounded header
      counts and concurrent explicit handoffs, including absent and multi-value cases.
- [ ] Check transient allocation and post-terminal retention separately; avoid a
      full-context copy or global retained index for one named lookup.
- [ ] Compare unchanged cache-disabled/cached invocation paths against `4.3.0`;
      isolate any new-helper-only rows from shared baseline comparisons.
- [ ] Exercise the real public helper/production state in measured rows and prove
      handoff/subscriber attachment with gates rather than short sleep assumptions.

### [ ] 9.3 Review performance disposition explicitly

- [ ] Run harness tests and smoke to verify wiring; do not treat those numbers as
      release-quality performance claims.
- [ ] Provide reproducible current/baseline commands for a manual release-quality
      comparison when required, with separate output and fresh baseline repositories.
- [ ] Review clean-source reports, allocation data, unmatched rows and uncertainty,
      or document why no release-quality run/public claim is required for the scope.
- [ ] Keep manual work pending until results are supplied and validated; record a
      no-public-performance-claim disposition unless reviewed comparable evidence supports it.

## Priority 10 - Release Scope and Go/No-Go

### [ ] 10.1 Freeze the delivered public surface and guidance

- [ ] Freeze helper signatures, structural errors, mock assertions and examples
      together; verify legacy bulk-map, key and snapshot compatibility.
- [ ] Keep new metadata/configuration out unless explicitly approved and fully
      enforced; no automatic forwarding, propagation, trust or broad casing change.
- [ ] Reconcile implemented behavior with every accepted priority and record any
      narrowed/deferred work without presenting it as verified.
- [ ] Confirm release notes make no unverified mesh, memory or performance claim.

### [ ] 10.2 Assemble immutable release evidence

- [ ] Assemble full tests, strict API, dependency matrix, packaging, consumers,
      AOT/native, lifecycle and targeted benchmark disposition from reviewed source.
- [ ] Record commands, actual counts, clean state, reachable commit, toolchains,
      artifact/report hashes, Central markers, remaining risk and evidence paths.
- [ ] Re-run affected checks after fixes; preserve original failures and distinguish
      uncommitted release-preparation changes from clean final-source evidence.
- [ ] Generate readiness with truthful pending signing/tag/publication steps;
      target-only manifests do not certify unexecuted manual checks.

### [ ] 10.3 Select release scope and candidate version

- [ ] Record an explicit dated go/no-go decision with reviewed commit and scope.
- [ ] Select `4.4.0` only if additive public functionality ships; otherwise select
      a compatible patch/documentation-only disposition or no-go with rationale.
- [ ] On go, align final candidate coordinates and rerun affected readiness,
      generation and release-packaging checks before claiming a release cut.
- [ ] Keep public/API/consumer/benchmark baselines at `4.3.0` until successful
      publication and fresh verification; the snapshot name alone selects nothing.

### [ ] 10.4 Publish, verify, and archive V31

- [ ] Build/sign the reviewed clean final commit/tag; verify signatures, staged
      assembled consumption and generation packaging before deployment.
- [ ] Preserve credentialed signing/publication evidence; a scope GO or unsigned
      local build is not a signing pass.
- [ ] After publication, verify all parent/module attachments and an assembled
      consumer from fresh Central-only repositories before moving baselines.
- [ ] Archive V31 and select the next development coordinate only after verified
      publication; if the chosen path is no-go/no-publication, record that exact
      disposition instead of claiming an unpublished version was released.

## Completion Criteria

- [ ] Evidence distinguishes case mismatch, filtering/redaction and genuine
      context-boundary loss without an unsupported mesh diagnosis.
- [ ] Named access handles absence, multiplicity and malformed input while legacy
      map spelling, string aliases and snapshot restoration remain compatible.
- [ ] Filtering, handoff, auth/cache isolation and terminal cleanup remain correct
      through the tested protocol and feature-composition paths.
- [ ] Mock, consumer, compatibility, AOT/native, lifecycle and documentation checks
      cover final delivered code; performance claims match reviewed evidence.
- [ ] A release or no-go disposition is recorded and the corresponding archive
      path is complete, with no unimplemented public contract represented as shipped.
