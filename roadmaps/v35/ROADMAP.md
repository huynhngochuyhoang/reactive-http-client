# Reactive HTTP Client - Roadmap V35

> **Status:** active
> **Theme:** resolve V34 deferred performance and default-path findings
> **Published baseline:** `4.4.2`
> **Development coordinate:** `4.5.0-SNAPSHOT`
> **Investigation scope:** all V34-C001 through V34-C005 and the unresolved P3/P10 allocation finding
> **Implementation authorization:** seven-row bounded plan approved in Priority 3.3; no production change delivered
> **Release scope:** unselected
> **Draft date:** 2026-10-04
> **Adopted:** 2026-10-04

Follow-up to [V34's review-only closure](../v34/RELEASE-DECISION.md).
V34 delivered regression controls, investigation tooling and guidance, but no
production improvement: C004's disabled-value reuse failed its benefit gate and
was rolled back. Its enabled-only allocation finding remains unresolved, not
passed. V35 addresses **every deferred finding**, not repeating a
selection that considers only C004.

The [execution checklist](CHECKLIST.md) adopts baseline, reproduction and fix-design
work without reopening V34, approving production edits, changing coordinates or
selecting a release. Checklist creation completes no execution item. Concrete
fixes require the maintainer decision in Priority 3.3; release selection remains
separate in Priority 12. Index/archive/readiness identify active V35 with
`activeRoadmap=v35` and `plannedFinalVersion=null`.

## Objective and Completion Meaning

Fix reproduced unnecessary costs or ownership defects with the smallest safe
change, and resolve unsupported hypotheses with evidence. A deferred optimization
is not automatically a bug. Required discovery, validation, isolation and cleanup
must not disappear just to make an allocation score smaller.

Every row in the inventory below must receive a final disposition:

- **Fixed and verified:** attributed cost or defect, exact bounded patch, regression
  controls, matched before/after evidence and ownership/compatibility acceptance.
- **Resolved without production change:** evidence establishes intentional cost,
  disproves a suspected defect or explains a measurement artifact; document the
  tested boundary and why no safe improvement is justified. A noisy score, lack of
  investigation or repeating V34's deferral is not sufficient.
- **Unresolved/blocking:** preserve the finding, owner, missing evidence and next
  experiment. This cannot be called fixed or count as completing all findings.
  Any further deferral requires explicit maintainer scope reduction at closure.

No positive speedup, zero-overhead path, production memory leak or pod-memory
reduction is presumed. Release remains conditional even if every review item is
resolved. Keep allocation rate, retained heap, direct/native memory and RSS separate.

## Starting Evidence

The [Priority 1 baseline](BASELINE-SCOPE.md) reconciles reusable artifacts with
reachable source and preserves effective profiles. The [finding ledger](FINDINGS.md)
tracks all seven workstreams and their independent acceptance requirements.
[Priority 2](ALLOCATION-INVESTIGATION.md) explains the observed allocation split
through compiler/class-loading evidence without a production change or performance
all-clear. [Priority 4](DISCOVERY-COMPOSITION.md) characterizes C001 and rejects
its bounded accumulation candidate after matched scoring; no production change
remains. [Priority 5](BODY-REPORTING-OWNERSHIP.md) audits body/reporting ownership
and rolls back the null-holder candidate after its matched cost gate fails.
[Priority 6](STATIC-PLANNING-PROJECTION.md) reviews static/dynamic planning ownership
and rolls back interface-annotation reuse after matched scoring finds no repeatable
attributed benefit. Three implementation workstreams remain open; no production
change is delivered.
[Priority 3's specifications](FIX-DECISION.md) define local candidate boundaries,
no-change routes and acceptance/rollback for all seven rows. The maintainer approved
this bounded plan in Priority 3.3; execution and release selection remain separate.

The inspected starting revision is
`21ad81bd44db15b75d6787a0ceac308939a2741e`, containing V34's closure.
Re-establish reachable source and artifact provenance during execution; historical
records may cite pre-squash local revisions and ignored bundles absent in a clone.
Do not silently turn an unavailable object into clean-source evidence.

- [V34 findings](../v34/COST-OWNERSHIP.md) separate construction, invocation,
  subscription, loopback, sampled attribution and ownership witnesses.
- [The C004 experiment](../v34/HARDENING-EVIDENCE.md) did not demonstrate repeatable
  benefit from immutable disabled-value reuse. The complete policy mutation scan
  remains required; restoring the old patch is not a new fix.
- [Final matched evidence](../v34/COMPATIBILITY-PERFORMANCE.md) records identical
  310 starter class files and 120 non-starter JARs in the compared artifacts, yet
  enabled-only GET publisher forks split near 1,392/1,136 B/op versus baseline
  1,136/1,136. Byte identity does not explain that split. JIT/escape analysis is
  still a hypothesis; the favorable primary pair is not an all-clear.
- [Maintainer guidance](../v34/MAINTAINER-GUIDANCE.md) maps existing fixtures,
  supported alternatives and owners. Reuse them rather than creating a second
  benchmark, policy or reactive-composition framework.
- [V33 extension guidance](../v33/MAINTAINER-GUIDANCE.md) and V34's
  [body/context](../v34/BODY-CONTEXT-OWNERSHIP.md),
  [lifecycle](../v34/INACTIVE-LIFECYCLE.md) and
  [parity](../v34/PARITY-EVIDENCE.md) controls are safeguards, not deferred defects
  to redesign without a new reproduction.

## Complete Finding Inventory

Keep the V34 IDs for traceability. Subrows prevent the failed narrow C004
experiment from standing in for its broader deferred work.

| Workstream | Current finding and proposed repair boundary | Owner and required outcome |
|---|---|---|
| V34-P3/P10 allocation finding | Unexplained fork-dependent allocation in enabled-only warm GET publisher assembly, with related 256-byte splits in other profiles. Attribute the split before assigning it to production code | Performance maintainer. Causal evidence and representative validation, or explicit unresolved status; never select the favorable fork |
| V34-C001 | Per-invocation observer/hook discovery and composition. Remove only attributed intermediate work while preserving dynamic provider behavior | Observer/hook maintainer. Equivalent late registration, ordering, prototype/support behavior and invocation-time capture with measured benefit or a justified no-change conclusion |
| V34-C002 | Invocation-wide body-ownership holder and subscription-local reporting state. Avoid demonstrably unused work only for proven non-resource/unconsumed paths | Invocation/body maintainer. Exactly-once cleanup, independent caller state and preserved terminal/attempt evidence, with no retained-memory trade |
| V34-C003 | Repeated static logging/plan derivation and argument/header/URI projection. Move only truly invariant work into the existing plan or handler boundary | Planning/resolver maintainer. Exact public metadata and wire semantics; no retained arguments/context or replacement metadata model |
| V34-C004 optional preparation | Cache identity WebClient construction, shared scheduler access and legacy dormant manager ownership when caching is unselected | Handler/factory maintainer. Establish actual acquisition and lifetime, then avoid unnecessary work without weakening selected-cache probes, admission, authorization or shutdown |
| V34-C004 rolled-back value reuse | Reconsider only with new attributed evidence that distinguishes benefit from the failed V34 experiment | Effective-policy maintainer. Keep every mutation check; do not reinstate constants solely because fewer constructors appear in source |
| V34-C005 | Cold construction/first-call work, AOT-only infrastructure and runtime retention | Factory/AOT maintainer. Isolate removable lifecycle work without changing binding/selection/initialization or moving costs invisibly into first subscription |

All rows are mandatory workstreams in this roadmap. Cross-cutting changes
must name every affected row and its controls; none disappears because another
change happens to improve a combined score.

## Boundaries That Must Survive

- Keep the existing [effective profiles](../v34/BASELINE-SCOPE.md#effective-profiles):
  minimal public proxy, real auto-configured defaults with/without MeterRegistry,
  resilience enabled without operator intent, application observer/hook, physical
  optional absence, independent pool gauges and selected cache/work controls.
  They are not equivalent configurations or deployable performance modes.
- Observer/hook selection occurs per invocation; an already assembled publisher
  retains its selected consumers on resubscription. Preserve late registrations,
  ordering, per-client support checks, custom providers and prototype materialization.
  No permanent empty-result cache or forced singleton behavior.
- Preserve the complete policy mutation-validation boundary, including changes to
  sibling endpoints and disabled-to-selected transitions. No unchecked live-config
  mutation, missing cache-key variant, skipped auth gate or hidden resource creation.
- Keep per-subscription tenant/context/reporting state independent. Cache callers,
  shared loads and refreshes have different owners; no state pooling or request
  snapshots retained by static plans, shared globals or unrelated clients.
- Preserve body presence, charset, wire order/encoding, custom codecs, streaming,
  backpressure, one-shot input limits, cancellation/discard and terminal-once
  reporting. Ordinary mutable arguments do not become deep-frozen by implication.
- Preserve V32 cleanup and V33 builder/public-metadata/AOT selection contracts,
  optional class loading and non-instantiating diagnostics. Application connectors,
  registries, schedulers and SDKs retain their established ownership.
- No new public API/SPI, performance switch, module, dependency upgrade, default,
  scheduler/pool tuning, caching/retry rule, telemetry export or broad handler/AOT
  rewrite. A demonstrated need outside these boundaries requires separate scope.

## Priorities

## 1. Post-V34 Baseline and Complete Deferred-Scope Integrity

- Verify V34 remains closed without a release, published/API/consumer/benchmark
  baselines are `4.4.2` and the reactor remains `4.5.0-SNAPSHOT`.
- At adoption, map every inventory row to its owner, reproduction, proposed
  change, acceptance and final disposition. No carried-over checkbox is a pass.
- Revalidate V34's retained controls, published provenance and reusable evidence;
  identify changed or unavailable inputs, actual toolchains and supported Boot
  4.0.0/4.1.0 rows without dependency upgrades.
- Preserve V1-V34 history. Record new results and changed conclusions in V35.

Deliver a baseline and complete findings ledger, not a claim of implementation.

## 2. Explain the Unresolved Allocation Split

- Freeze a bounded experiment plan before new scoring: hypotheses, distinguishing
  observations, number/order of forks and comparisons, stop conditions and artifact
  identity. Use the historical enabled-only GET assembly row and minimal/registry
  sentinels; do not run until a favorable pair appears.
- Audit harness bytecode, parameters, classpaths/order, artifact metadata, loaded
  classes and VM arguments in addition to starter byte identity. Compare repeated
  forks of the **same saved JAR** with the baseline/current pair to distinguish
  artifact effects from within-artifact variability.
- Collect compiler/inlining/escape-analysis or allocation-site evidence in separate
  diagnostic runs where supported. Preserve profiler perturbation and distinguish
  sampled allocation from exact B/op. Diagnostic JVM switches are experiments,
  not new supported deployment defaults or substituted release scores.
- Test a proposed cause by changing one factor while keeping the rest matched.
  Retain contrary samples. If production code is causal, specify a bounded fix;
  if the harness/VM is causal, document the evidence and measurement correction.
  Revalidate corrected harnesses on both artifacts, never overwrite old samples.
- Separate a plausible explanation from a validated one. If the experiment budget
  ends without attribution, record blocking evidence and request explicit next
  scope; do not silently clear the flag or relax the historical thresholds.

This gate precedes benefit claims on affected rows. Independent correctness or
structural ownership tests may proceed with the dependency recorded; an unexplained
allocation split cannot justify accepting an optimization.

## 3. Specify Bounded Fixes for Every Workstream

- For each C001-C005 boundary and the allocation finding, identify attributed
  unnecessary work or reproduced defect, smallest local correction, expected
  affected phase and exact compatibility/ownership dependencies.
- Compare the no-change alternative. Required costs need a positive explanation,
  not a speculative implementation. Require new evidence before reopening the
  rolled-back C004 value-reuse experiment.
- Obtain maintainer approval of the concrete bounded implementation plans before
  production edits. The remit is all findings; approving a patch for one row does
  not silently defer the others. Any scope reduction must be explicit.
- Freeze per-change semantic tests, benefit/hardening acceptance and rollback
  criteria. Keep changes individually attributable and reversible; no combined
  optimization that hides which candidate helped or regressed.

Deliver a fix matrix. Source-level allocation counts alone cannot approve a fix.

## 4. C001 - Dynamic Discovery and Composition Cost

- Isolate provider lookup, stream/list materialization, empty fallback and
  composite construction in [the handler][handler]. Distinguish empty, single,
  multiple, ordered and prototype consumers from framework lookup cost.
- Implement only the approved local reduction of intermediate work. Preserve
  custom-provider results/failures and lookup/materialization timing; do not assume
  an empty ordered stream makes another provider operation equivalent.
- Test empty-to-present registration, per-client support, concurrent first calls,
  captured publishers versus new invocations, no-registry observers and master-off
  exports with application consumers still active.
- Measure production invocation paths as well as explanatory helpers. Omitting
  callbacks or forcing singleton consumers is a failed optimization.

## 5. C002 - Body and Reporting-State Ownership

- Separate the invocation-wide [body owner][handler] from per-subscription state.
  Attribute null/immutable-body holder work before introducing a non-owning path;
  enumerate recognized resources rather than assuming an arbitrary DTO is safe.
- Preserve stream/reader/channel/DataBuffer/multipart ownership, inner streaming
  bodies, decode/serialization failure, pre-dispatch rejection, empty completion,
  cancellation and late-value discard. No reuse of consumed one-shot inputs.
- Keep state required by auth, explicit operators, generated idempotency, deadlines,
  logging/observers/hooks and cache work even when exports are absent. Enabled-only
  resilience is an existing behavior boundary, not automatic permission to remove
  its state. Any proposed change must prove the complete observable contract.
- Use deterministic cleanup acknowledgements and caller-isolation tests. Pair
  allocation benefit with retention evidence; forced collection belongs only in
  the controlled reachability lane, never normal unit-suite acceptance.

## 6. C003 - Static Planning and Dynamic Request Projection

- Investigate repeated interface-level log annotation traversal and immutable
  derivation using existing [request plans][plans] and [metadata cache][metadata].
  Retain per-invocation selection and application replacement behavior.
- Reduce copies only where mutation/aliasing cannot affect current or later calls.
  Preserve public fresh metadata, inherited generics, API-ref precedence, return
  shape, null/empty bodies, default/dynamic headers and case-insensitive variants.
- Keep URI/query/header order, charset, idempotency/context inputs and codec
  serialization at their proper boundary. No pre-serialized body or resolved
  per-caller request in a reusable plan.
- Test exact dispatch method/target/headers/body and first/warm behavior across
  public, factory and mock paths; benchmark construction and warm calls separately.

## 7. C004 - Unselected Feature Preparation and Resources

- Attribute [cache identity view][handler] construction, scheduler access and
  [manager][manager] lifetime separately. A dormant object, a shared scheduler
  reference, a worker thread and a scheduled refresh are different observations.
- If selected, defer/elide unneeded preparation only where effective policy and
  existing mutation guards make it safe. Preserve public constructors, static
  creation, Spring factories, cache-enabled mocks and diagnostics unknown states.
- Selected caches must still run non-dispatching finalized-request probes through
  all required filters, auth validation and request identity checks. Preserve
  caller/load/refresh admission, byte/entry bounds and same-tag meter ownership.
- Verify concurrent first use, failed construction, cancellation and close races,
  no optional linkage on unselected paths, and no publication after closure.
  Laziness must not move validation failures or recreate resources after destroy.
- Treat disabled-value reuse as a separate subrow. Retain every whole-interface
  mutation check; accept reuse only with new repeatable benefit over V34's failed
  experiment. It cannot substitute for investigating broader preparation costs.

## 8. C005 - Construction, AOT and Runtime Lifecycle Cost

- Separate context/proxy construction, first invocation/subscription and warm
  execution. Identify reused static work and exact owners before adding caches.
- Preserve properties/metadata preference, parent/scoped/FactoryBean selection,
  binding/awareness/initialization order and processor restoration. Unknown types
  must not lead to eager unrelated business-bean construction or default fallback.
- Verify [AOT lifecycle tracking][binding] remains bounded and stops at its proper
  runtime boundary. Startup-only work must not retain request/application state
  or leak into warm calls. Do not redesign V33's selection contract without a
  separate reproduced requirement.
- Test context recreation, class-loader isolation, partial failure and destruction.
  Compare total construction plus first-use cost so deferred work cannot masquerade
  as a startup improvement; release owned resources on both boundaries.

## 9. Cross-Path, Optional-Integration and Lifecycle Verification

- Run affected public/factory/mock regressions and ordinary full module suites.
  Keep normal tests deterministic with explicit GC disabled; use existing
  controlled reachability JVMs for actual collection claims.
- Verify assembled JAR consumers with genuine Boot 4.0.0/4.1.0 parents, optional
  classes physically absent, and selected auth/resilience/cache/telemetry sentinels.
  Name real API meters and preserve attempt/dispatch/caller distinctions.
- Verify affected JVM AOT/hints and a clean-source native executable. Reuse older
  evidence only through exact unchanged-input comparison with narrower limits;
  production/fixture changes require a rerun of affected lanes.
- Exercise final cleanup, failed creation, shared application resources, same-tag
  recreation and diagnostic reads without instantiating lazy owners. Test shared
  loads/refreshes separately from independent caller-owned work surviving close.

## 10. Final Matched Cost and Compatibility Acceptance

- Reuse [V34's workload contract](../v34/WORKLOAD-CONTRACT.md): all 60 matched
  primary rows plus justified new rows for newly changed boundaries. Freeze any
  additions and their criteria before candidate scoring; old results stay intact.
- Keep identical harness/non-starter dependencies, isolated Central `4.4.2`
  provenance, source/JAR hashes, semantic witnesses, raw samples, intervals and
  order. Confirm every flagged row and the historical enabled-only row in reversed
  order; include same-JAR controls from Priority 2 where needed.
- Retain historical review triggers (>20% mean latency and >max(32 B/op, 5%)
  allocation increase). They are investigation triggers, not regression allowances
  or a promised minimum benefit. Investigate variance, do not select best forks.
- Accept optimizations only with repeatable attributed benefit under equivalent
  behavior. Accept correctness hardening only with a reproduced defect and honest
  cost/tradeoff approval. Roll back failed candidates individually and reverify
  the final combination and no-change controls.
- Run strict root and independent starter source/binary comparisons against
  published `4.4.2` with separate provenance/reports even if the first fails.
  API success does not replace behavioral, ownership or optional-linkage controls.
- No blanket performance all-clear while the allocation finding or another
  required acceptance remains unexplained. Follow the existing public report
  promotion process; no public quantitative claim is required.

## 11. Complete Findings and Maintainer Guidance

- Reconcile all seven inventory rows to actual patches or evidence-backed no-change
  conclusions. Keep unresolved items plainly blocking unless the maintainer
  explicitly reduces scope. Do not call repeated deferral resolution.
- Record owners, remaining intentional costs, supported workarounds, migration
  effects if any, measured workload limits and reproducible experiments.
- Update relevant performance, customizer/context, lifecycle and operations guides.
  Keep support capture bounded, synthetic where possible and sanitized; no request
  targets, headers, bodies, identities, cache keys or arbitrary errors in evidence.
- Preserve V34's failed experiment and unresolved historical observations. New
  evidence may explain them; it must not rewrite them into past passes or establish
  a service-mesh/pod-memory diagnosis without deployment evidence.

## 12. Conditional Release and Closure

- Obtain the maintainer's release or review-only decision after all findings are
  reconciled. Evaluate a compatible patch first; `4.5.0-SNAPSHOT` is not a selected
  minor. An incompatible need stops this scope for a separate migration decision.
- Inventory clean reachable final source, decisions, tests, Boot/consumer/AOT/native,
  matched cost, API, ownership and guidance evidence. Seal exact commands, actual
  totals, toolchains, effective dependencies, artifacts/hashes and failures.
- For a release, update coordinates, fixtures, version/matrix guards, current docs,
  changelog and readiness together. Keep baseline `4.4.2` until publication is
  verified; signing/staging, tag/workflow, Central artifacts and isolated published
  consumption remain distinct gates. Authorize signing locally, never in evidence.
- For no release, obtain explicit disposition of delivered/rolled-back work and
  any unresolved scope; label release-only gates N/A, not successful executions.
  No-release closure is not permission to mark unexplained findings fixed.
- Close roadmap/checklist/index/readiness together only with the corresponding
  evidence. Future scope remains unselected; V34's archive is unchanged.

## Evidence and Execution Rules

Use the existing evidence conventions under
`target/release-evidence/v35/priority<N>/`. Create tracked baseline, allocation
investigation, per-finding fix/disposition, verification and closure records only
when work produces evidence. Ignored bundles are not durable source history;
retain integrity anchors and tracked reproduction commands, including generation
of any consumer overlay. Preserve failed/partial attempts and unavailable gates.

For every change, record approval, source/patch, affected effective profiles,
untimed correctness witnesses, resource owners, actual counts, costs and rollback.
Do not add profiling counters to scored hot paths. Do not run competing builds or
profilers alongside scored pairs. Capture host/JVM constraints and tool limits;
new measurements cannot silently replace V34's historical results.

## Acceptance Criteria

- [ ] All deferred findings, including both C004 subrows and P3/P10 allocation,
      have explicit evidence-backed final dispositions; none is silently dropped.
- [ ] The allocation split is explained and validated, or explicitly blocks full
      scope completion; there is no favorable-rerun or threshold-reset workaround.
- [ ] Every retained production change has approved scope, reproduced need,
      passing semantic/ownership controls and matched benefit or approved hardening.
- [ ] Dynamic extensions, request/body/context/terminal semantics, mutation
      validation, optional loading, AOT selection and cleanup remain compatible.
- [ ] Public/mock/factory/assembled/Boot/AOT/native/API and final cost evidence
      identify actual inputs, outcomes and reuse limits.
- [ ] Guidance states intentional costs and limits without claiming an unproven
      speedup, leak fix, zero-overhead mode or deployment-memory remedy.
- [ ] Release/no-release choice, remaining scope and closure evidence are explicit.

These are acceptance criteria, not executed checks. Implementation and release
remain unselected until the corresponding scope decisions in the execution checklist.

[handler]: ../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/core/ReactiveClientInvocationHandler.java
[plans]: ../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/core/RequestPlan.java
[metadata]: ../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/core/MethodMetadataCache.java
[manager]: ../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/core/LocalResponseCacheManager.java
[binding]: ../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/config/PropertiesBindingLifecycle.java
