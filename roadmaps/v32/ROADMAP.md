# Reactive HTTP Client - Roadmap V32

> **Status:** active
> **Theme:** architecture review, practical extensibility, and justified improvements
> **Published baseline:** `4.4.0`
> **Development coordinate:** `4.5.0-SNAPSHOT`; no release scope selected
> **Execution:** [adopted checklist](CHECKLIST.md); F004/F005 implemented, Priority 10 verification pending
> **Draft date:** 2026-09-14
> **Adopted:** 2026-09-14

This roadmap develops the
[post-4.4.0 architecture review proposal](../proposals/POST_4_4_ARCHITECTURE_REVIEW.md)
after [V31 publication and closure](../v31/RELEASE-DECISION.md#post-publication-closure).
It proposes a review before more feature work, not a predetermined refactor or
promise to release `4.5.0`. V1-V31 remain completed release records. The proposal
is the historical design input. The adopted checklist starts the review, not
production implementation or release preparation; Priority 8 requires an explicit
maintainer decision before production changes.

## Intent

Answer four questions with source-linked evidence and realistic consumer cases:

1. What necessary behavior or contract is missing, and who needs it?
2. Which supported application extensions cannot be implemented cleanly today?
3. Which boundaries make otherwise small changes unnecessarily risky?
4. What genuinely needs improvement, and what should remain unchanged,
   application-owned, a documented limitation, or deferred work?

A review that justifies no production change is a successful outcome. File size,
package layout, repeated code, or the number of historical fixes alone is not a
defect finding. Existing safety restrictions must not be relaxed merely to make
an extension easier to write.

## Starting State

The inspected starting revision is
`c40dba68e141c3fc3031d125842034983351189f`. The reactor is
`4.5.0-SNAPSHOT`; published, API, consumer and benchmark baselines are `4.4.0`.
Java 21 and the existing Boot 4 support lanes remain unchanged. V31 supplies
publication and compatibility evidence; adoption does not claim a new artifact,
native, performance, or architecture validation run.

The architecture already contains declarative grammars, request plans, effective
policy helpers, distinct reporting state and work-admission helpers. Review how
they compose before introducing another representation or orchestration layer.

| Area | Source starting point | Question, not a finding |
|---|---|---|
| Assembly and destruction | [Factory bean](../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/core/ReactiveHttpClientFactoryBean.java), [auto-configuration](../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/config/ReactiveHttpClientAutoConfiguration.java) | Are selection, validation, partial construction and owned-resource shutdown explicit? |
| Declarative decisions | [RequestPlan](../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/core/RequestPlan.java), [effective resilience](../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/core/EffectiveResiliencePolicy.java), [effective cache policy](../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/core/EffectiveCachePolicy.java) | Which facts are frozen, resolved per call, or legitimately unknown before instantiation? |
| Invocation and extension ordering | [Invocation handler](../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/core/ReactiveClientInvocationHandler.java), [customization contract](../../docs/15-customizer.md) | Can changes preserve caller preparation, non-dispatching probes, load work and terminal reporting independently? |
| Identity, storage and active work | [Cache key contract](../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/core/CacheKeyContract.java), [cache manager](../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/core/LocalResponseCacheManager.java), [work admission](../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/core/CacheWorkAdmission.java) | Are publication, accounting, cancellation and ownership transitions separately specified? |
| Context and reporting | [Context snapshot](../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/core/RequestContextSnapshot.java), [subscription reporting](../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/core/SubscriptionReportingState.java), [diagnostics provider](../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/core/ReactiveHttpClientDiagnosticsProvider.java) | Do caller, hidden-work and inspection paths retain only the facts and resources they own? |
| Cross-module and native boundaries | [Mock bridge](../../reactive-http-client-test/src/main/java/io/github/huynhngochuyhoang/httpstarter/core/MockResponseCacheSupport.java), [AOT processor](../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/config/ReactiveHttpClientBeanFactoryInitializationAotProcessor.java), [OTel auto-configuration](../../reactive-http-client-otel/src/main/java/io/github/huynhngochuyhoang/httpstarter/otel/OpenTelemetryHttpClientAutoConfiguration.java) | Which dependencies are supported extension APIs, internal cooperation, or optional integrations? |

These anchors define where to start reading. They do not establish an architectural
flaw or authorize moving behavior out of these classes.

## Scope and Guardrails

Review all shipped modules: starter, test helpers and optional OTel companion.
Include benchmarks, assembled consumers, dependency/API checks, native smoke and
release tooling as evidence infrastructure. Review the paths needed to answer the
questions, not every historical test permutation or every theoretical extension.

- Preserve explicit opt-in, request and auth isolation, serialized-body identity,
  bounded cache storage/work, replay rules, caller deadlines and terminal privacy.
- Preserve published APIs, constructors, configuration defaults, optional
  dependency absence, diagnostics schema and supported Java/Boot lanes.
- Distinguish supported public SPIs from public internal bridges. Public internal
  status is not automatic permission to break packaged consumers.
- Keep cache-served callers, actual dispatches, retry attempts, hidden refreshes
  and downstream-health samples semantically distinct.
- Respect application ownership: custom connectors, executors, publishers,
  retained responses and manual context writes do not become starter-owned
  merely because they pass through a client.
- No blanket rewrite, module/package split, general pipeline framework, plugin
  system, new public SPI, or dependency replacement without an accepted finding.
- No implicit forwarding, trust inference, new cache backend, automatic context
  propagation, expanded verb eligibility or resilience redesign in this scope.
- No memory-leak, performance-gain or mesh-specific claim from structural review
  alone. Those require separate reproducible evidence.
- Keep V1-V31 and proposal history unchanged. Broader features or breaking
  redesigns require a separate proposal and explicit compatibility decision.

## Evidence and Decision Contract

Each review area must end with a bounded conclusion: verified behavior, confirmed
gap, documented constraint, intentional non-goal, or an unresolved question with
named missing evidence. Reuse existing tests when they prove the claim. Do not
relabel a historical fix or a hypothetical race as a new verified defect.

Maintain one finding register with stable IDs. Each finding records:

| Field | Required content |
|---|---|
| Need and impact | Affected user, concrete workflow, correctness/security risk or demonstrable maintenance cost |
| Evidence | Reachable revision, source references, reproducer or characterization; observation versus hypothesis |
| Contract and owner | Existing promise, responsible module, lifecycle and extension boundary |
| Alternatives | No change, documentation, local correction, existing helper/SPI, or a narrowly justified new boundary |
| Tradeoffs | Complexity removed and added, compatibility, migration, concurrency and hot-path risk |
| Disposition | Fix now, document, retain intentionally, defer with a trigger, reject, or unresolved pending evidence |
| Acceptance | Deterministic test, required consumer/native/performance evidence, and rollback boundary |

Rank confirmed safety/correctness gaps first, then reproducible supported-extension
blockers, then evidenced change cost. Record no-change decisions with the same
care as accepted changes. There is no target number of refactors to justify.

Before production edits, an explicit maintainer decision must select the finding
IDs, scope, dependency order and verification budget. A roadmap/checklist adoption
starts the review; it does not pre-approve the findings. Unaccepted or broader
work remains deferred rather than silently expanding the completion gate.

## Priorities

## 1. Post-`4.4.0` Baseline and V32 Scope Integrity

- Establish reachable source and verified published artifacts/assembled consumer;
  reuse V31 evidence only where its exact revision and claim remain applicable.
- Keep `4.4.0` public/API/consumer/benchmark baselines and `4.5.0-SNAPSHOT`
  development coordinates until an explicit release decision and publication.
- Reconcile V1-V31 decisions and proposals into delivered, superseded, still
  relevant and unverified categories. Do not reopen archived proposal boxes.
- Verify the adopted execution checklist after draft review. Keep active review,
  accepted implementation and release selection distinct in the index, archive
  guard and generated readiness; adoption does not complete Priority 1.

Deliver a baseline/scope record; no dependency upgrade or production change is
required to begin an architecture review.

## 2. Architecture, Contract, and Ownership Map

- Map module/dependency direction and supported extension points, including
  deliberate package sharing and internal bridges used by test helpers.
- Trace startup and failed construction; ordinary Mono/Flux invocation; cache
  hit, independent miss, shared load and refresh; and factory shutdown.
- Distinguish factory, client method, logical caller, attempt, shared load,
  refresh, transport connection/stream and application-owned lifetimes.
- Inventory mutable/frozen configuration, caches of decisions, scheduler/context
  transitions, synchronization, bean discovery and optional classpath checks.
- Associate each invariant with its owner and current tests. Identify absent
  evidence separately from absent behavior.

Deliver an as-is architecture map and contract/owner inventory. Sequence diagrams
must show preparation, dispatch, terminal and teardown boundaries, not just one
generic request arrow. Keep diagrams proportional to the questions they answer.

## 3. Real Application Extension Scenarios

Use small consumers outside starter packages and only documented entry points.
Prioritize reported needs; hypothetical cases must be labeled as exploratory.

| Scenario | Required observation |
|---|---|
| Per-caller authorization/tenant gate with caching | Hits still run required gates; probes do not dispatch; final identity and auth replay cannot share another caller's response |
| Boot/per-client builder customization | Inventory filters, `defaultRequest`, exchange-function and connector replacement; classify all applicable mutations, not filters alone |
| Custom auth factory, metadata parser, codec or error decoder | Prove selection, ordering and invocation through supported replacements; identify restrictions without bypassing validation |
| Custom observer/lifecycle hook without a MeterRegistry | Terminal facts remain meaningful; telemetry backend absence is not disabled behavior |
| Explicit executor or queue handoff | Snapshot boundaries and caller isolation hold without whole-context retention or implicit forwarding |
| Deliberate connector replacement | State which transport guarantees transfer to the application and which starter contracts still apply |

For each, retain the attempted consumer and classify it as directly supported,
supported with constraints, awkward but correct, dependent on unsupported
internals, contradictory to a promised contract, or intentionally out of scope.
Treat a deliberate safety rejection differently from an accidental gap. A new
extension mechanism needs a use case that existing APIs cannot reasonably serve.

## 4. Effective Policy and Component Selection Review

- Compare startup, public handler creation, invocation, diagnostics, mocks and
  AOT for declarative grammar, effective API, cache, resilience and signing facts.
- Identify the authoritative decision for each fact and when it may be evaluated.
  Equivalent decisions do not require eager component creation everywhere.
- Exercise representative primary/order/priority, parent-child shadowing,
  candidate-resolver, lazy, prototype, FactoryBean and application replacement
  cases using existing fixtures before adding more combinations.
- Distinguish absent, available, uninstantiated and uninspectable components;
  diagnostics must preserve supported unknown states without creating them.
- Inventory runtime configuration mutation handling and snapshot ownership.
  A new immutable policy representation is an option, not an assumed outcome.

Deliver a decision/selection matrix with reproduced drift or explicit parity
evidence. Do not apply starter grammar to foreign replacement clients merely to
make the matrix uniform.

## 5. Invocation and Composition Boundaries

- Trace preparation, body serialization, finalized URI/headers, cache auth probe,
  lookup, actual load, resilience operators, transport and response publication.
- Record extension execution order and cardinality on hits, misses, retries,
  auth replay, redirects and hidden refresh; include asynchronous continuations.
- Verify the separation of caller deadlines from shared work and waiters, and
  of caller reporting from attempt/hidden-work state after detachment.
- Identify changes that require coordinated edits across these paths. Use a
  concrete change/reproducer to distinguish necessary orchestration from coupling
  that prevents independent testing or correction.
- Preserve admission-before-preparation, same-snapshot key/wire identity,
  exactly-once terminal reporting and no post-terminal dispatch guarantees.

Deliver a composition boundary map and evidence for any proposed extraction.
Do not merge lifetimes or introduce a second operator pipeline to reduce file size.

## 6. Resource, Concurrency, and Retention Ownership

- Follow eager streams/readers/channels, pooled buffers, materialized responses,
  context snapshots and auth state through transfer, discard and termination.
- Audit cache generations, entries, independent loads, shared flights, refreshes
  and caller/load/refresh reservations under success, empty, error, timeout,
  cancellation, expiry, eviction and shutdown.
- Include failure after partial construction, late connection acquisition,
  destroy/recreate, overlapping meter owners and application-owned work that may
  outlive the factory. Do not claim teardown cancels every external owner.
- Record lock order and callbacks entered under ownership guards; use controlled
  gates to prove cleanup and admission release ordering, including late signals.
- Separate retained cache values from active work, allocator capacity, direct
  memory and application retention. Reuse V29/V30 memory and work evidence where
  relevant; do not infer a leak from RSS or forced-GC timing.

Deliver an ownership/terminal matrix. Candidate fixes require observable cleanup
and dispatch evidence, not only the implementation's own counters or sleeps.

## 7. Module, Optional Integration, and Evidence Boundaries

- Review starter/helper/OTel dependencies and public internal bridges without
  assuming a module split is needed. Check for copied production behavior in mocks.
- Compare runtime and AOT creation paths, generic/reflection traversal and custom
  properties/metadata beans; identify legitimate build-time limitations explicitly.
- Preserve no-Caffeine cache-disabled consumers and optional resilience,
  Micrometer and OTel absence. Separate metrics selection from registry presence.
- Assess whether existing consumer, native and wire fixtures can miss the claimed
  behavior: count all dispatch paths, synchronize asynchronous work, and avoid
  self-confirming test counters or fixture-only defaults.
- Review reproducible evidence boundaries: reachable commits, source changes
  after measurement, isolated baseline repositories, actual counts and hashes.

Deliver a cross-module/creation-path matrix and an evidence-gap list. This is not
a general rewrite of release tooling or an obligation to rerun every historical
native/benchmark lane for an unchanged review artifact.

## 8. Findings, Necessity, and Scope Decision

- Consolidate the preceding outputs into the ranked finding register; connect
  recurring symptoms only when evidence shows the same underlying contract gap.
- Compare the smallest correction, documentation-only resolution and existing
  helpers against each proposed new boundary or public extension.
- Record an explicit maintainer decision selecting a small, bounded set of
  finding IDs for V32, or selecting review-only completion with rationale.
- Define compatibility, migration/rollback, dependency order, acceptance tests
  and stop conditions for each selected item before production implementation.
- Move broader capabilities and breaking changes into separate proposals;
  unresolved hypotheses retain named evidence needs, not shipped-fix status.

This is the production-change gate. If no changes are accepted, mark subsequent
implementation-only work not applicable with a reason, not falsely verified.

## 9. Bounded Accepted Improvements

- Implement only Priority 8's accepted items, one contract/ownership boundary at
  a time, starting with a failing regression or behavior characterization.
- Prefer localized fixes and existing helper APIs. Extract only when the accepted
  evidence demonstrates reduced complexity or independent testability.
- Preserve published semantics, default activation, API/configuration compatibility
  and optional integrations. Do not bundle unrelated cleanup with a safety fix.
- Compare the resulting behavior to the as-is map; record exact improvements,
  remaining constraints and any narrower result than originally proposed.
- Stop and reopen the scope decision if a change requires an unapproved break or
  larger redesign. A spike is not a delivered feature or an accepted public SPI.

This priority is conditional on accepted production work; review-only is valid.

## 10. Compatibility and Targeted Verification

- For accepted changes, verify affected contracts and full module regressions;
  run strict root and independent starter source/binary API lanes against fresh
  Central `4.4.0`. Strict passing alone does not prove behavioral compatibility.
- Exercise applicable extension scenarios through mocks and assembled consumers,
  including minimal optional-dependency absence and replacement beans.
- Run supported dependency rows for affected shared boundaries. Require AOT and
  native execution when reflection, bean creation or lifecycle changes; retain
  exact tested source, toolchains and executable hashes.
- Measure hot-path/allocation changes against the same unchanged workload and
  published baseline. Use smoke for wiring only, not numerical release claims.
- Use deterministic gates for races; keep controlled reachability probes separate
  from ordinary tests that cannot guarantee garbage collection.
- For review-only work, verify documentation links, archive/readiness status and
  any new characterization fixtures. Record reused evidence and omitted lanes
  honestly instead of manufacturing new runtime/performance results.

Verification follows the accepted risk and scope, not the size of the roadmap.

## 11. Maintainer and Operations Guidance

- Publish the reviewed architecture/owner map, extension scenario results,
  decision register and accepted-change rationale in one linked maintainer view.
- Document which SPI to use for each supported need, its execution phase/order,
  ownership and limitations. Preserve version boundaries for any new contract.
- Correct contradictions in canonical guides proven by the review; do not make
  proposed improvements look like published `4.4.0` behavior.
- Tie troubleshooting to actually observable signals and sanitized structural
  evidence. Do not require request material, unexported internal state or counters
  that disappear before the documented observation point.
- Keep no-change and deferred decisions visible, with reconsideration triggers.
  Do not add permanent documentation for discarded experimental implementations.

## 12. Review Closure and Conditional Release Go/No-Go

- Assemble the reviewed revision, maps, findings, dispositions, selected-item
  verification and remaining risks into a dated architecture decision report.
- Close review-only/documentation-only work without a publication requirement
  when no release is justified. Explain that result rather than forcing a minor
  version merely because the reactor is `4.5.0-SNAPSHOT`.
- If a release is justified, select patch/minor scope from actual shipped changes;
  additive functionality may justify `4.5.0`, but the roadmap does not select it.
  Breaking changes require a separate major-version decision and migration work.
- On go, freeze the supported surface and guidance, assemble immutable evidence,
  verify reviewed clean final artifacts/signatures and staged consumers, publish,
  then verify fresh Central artifacts and published consumption before advancing
  baselines. A scope GO is not a signing or publication pass.
- Archive only the explicitly chosen review/release disposition. Keep unaccepted
  work deferred and future execution separate; do not reopen V31 evidence.

## Acceptance Criteria

- [ ] The as-is architecture and contract/owner inventory cover all reviewed
      modules and distinguish caller, attempt, hidden-work and factory lifetimes.
- [ ] Concrete external-consumer scenarios show which extensions work, which
      need constraints, and which are genuine gaps rather than intentional limits.
- [ ] Every reviewed area has evidence and a disposition; unresolved questions
      identify missing evidence and do not masquerade as completed findings.
- [ ] Accepted improvements have an explicit scope decision, smallest justified
      design and passing contract/compatibility evidence; unaccepted work is deferred.
- [ ] A review-only result is fully supported, with implementation-only gates
      marked not applicable rather than requiring arbitrary production changes.
- [ ] Guidance matches delivered behavior and operationally available signals;
      no speculative performance, memory, mesh or extension guarantee is published.
- [ ] A dated review/release decision and its matching archive path are complete;
      publication is claimed only after the corresponding release verification.

## Open Decisions During Execution

- Which application-reported extension cases should lead the review?
- Which boundaries actually need improvement after characterization, and which
  should remain intentionally coupled or application-owned?
- Is any new public API necessary, or can existing SPIs and clearer ownership
  solve the accepted cases without expanding the supported surface?
- Which evidence gaps warrant new fixtures versus reuse of existing contracts?
- Review-only, documentation, compatible correction or additive release scope?
