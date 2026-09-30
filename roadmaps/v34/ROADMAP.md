# Reactive HTTP Client - Roadmap V34

> **Status:** active
> **Theme:** performance and default-path hardening
> **Published baseline:** `4.4.2`
> **Development coordinate:** `4.5.0-SNAPSHOT`
> **Implementation and release scope:** unselected
> **Draft date:** 2026-09-30
> **Adopted:** 2026-09-30

This follows [V33 publication and closure](../v33/RELEASE-DECISION.md#post-publication-closure).
V1-V33 remain completed release records. The [execution checklist](CHECKLIST.md)
adopts V34 for baseline, workload and characterization work; it completes no
execution item, approves no production changes, changes no defaults, and does
not select `4.5.0` as the next release. Explicit selection of measured findings
in Priority 4.3 must precede implementation.

The [Priority 1 baseline](BASELINE-SCOPE.md) records the effective profiles,
published evidence reuse and fresh guards; workload and cost evidence remain open.

## Intent

Make ordinary calls pay only for work their effective contract requires, while
keeping enabled features correct. Prioritize unnecessary CPU work, allocation,
repeated discovery and retained state in the common path before adding features.
Hardening includes deterministic proof that unselected features do not acquire
their resources, and that faster paths still preserve request and terminal semantics.

This is not an assumption that `4.4.2` regressed or leaks memory. A result that
confirms the current path, improves regression evidence and justifies no production
change is valid. Do not ship complexity solely to improve a microbenchmark score.

## Starting Evidence

The inspected starting revision is `313126fd6afea357fef968683c9dd0d5e1df9fa0`.
It records published `4.4.2`, development `4.5.0-SNAPSHOT`, Java 21 and the
existing Boot 4.0.0/4.1.0 verification rows. Re-establish the actual toolchain and
resolved dependencies at execution; this planning record claims no fresh performance run.

- [V33 targeted cost](../v33/COMPATIBILITY-COST.md) separated parsing/planning,
  warm publisher creation and a subscribed no-network call. The cold-plan timing
  flag did not reproduce beyond its review threshold on confirmation. It supports
  neither a speedup nor a default-path regression claim, and its baseline was
  `4.4.1`, not the new `4.4.2` comparison.
- The [V32 composition review](../v32/INVOCATION-COMPOSITION.md) distinguishes
  invocation, subscription, caller preparation, attempts, shared loads and hidden
  refreshes. Preserve those ownership boundaries rather than merging their state.
- The [handler][handler] already has stateless and stateful execution paths.
  `getObserver()` intentionally discovers beans on every invocation for late
  registrations; hook selection is also dynamic. `RequestBodyOwnership` supplies
  resource cleanup even when reporting is absent. These are inspection points,
  not proof that an object or lookup is redundant.
- Existing [planning][planning-bench], [internal resolution][internals-bench],
  [diagnostics][diagnostics-bench] and [loopback][loopback-bench] benchmarks provide
  reusable workloads. Extend their contracts where necessary instead of creating
  a second benchmark/reporting framework.

## What Default Path Means

Record the effective settings, classpath, bean inventory and selected operators
for every row. A new `ClientConfig` used with a hand-built proxy is not proof of
the auto-configured Spring application's default behavior.

| Profile | Purpose and required distinction |
|---|---|
| Minimal ordinary call | No selected response cache, auth, resilience operators, exchange logger, observers or lifecycle hooks; no explicit logical deadline or generated idempotency key. Preserve ordinary decoding, headers, URI, context and body cleanup. This is a named minimal profile, not a claim that every application has these defaults |
| Auto-configured defaults | Minimal declared client through the factory and Boot context. [Observability configuration][properties] defaults enabled, but actual integrations depend on available beans/classes. Measure no-registry and registry-present cases separately; record correlation/inbound filters, codecs, pool settings and transport timeouts rather than disabling them silently |
| Present but unselected | Optional libraries present with no cache policy and no selected resilience instance. Include resilience `enabled=true` with no operator intent as a distinct control, not as the minimal profile |
| Physical optional absence | Assembled minimal consumer without Caffeine, Resilience4j registry classes or OTel. A no-op implementation with every dependency present is not this check |
| Selected controls | One feature at a time: observer/hook, metadata logging, metrics/OTel, auth, deadline, explicit operator or cache policy. Keep representative combinations only when an accepted change touches their shared boundary |

Pool gauges have independent activation; absence of ordinary observability does
not establish that pool telemetry is disabled. An application observer or hook is
real behavior even without a MeterRegistry. Do not call these paths unobserved
merely because built-in metric exports are absent.

## Scope and Non-Goals

- No new public feature, performance-mode switch, SPI, module, dependency upgrade,
  scheduler, pool policy, timeout/default, retry rule or cache selection rule.
  Any such need stops the bounded work and requires a new scope decision.
- Preserve supported late observer/hook registration, ordering, per-client support
  checks, prototype/provider semantics and optional class loading. A cached empty
  bean lookup is not safe merely because the first invocation had no observers.
- Do not optimize by suppressing validation, authorization, diagnostics consumers,
  terminal callbacks, body release, discard hooks or case-insensitive context access.
  Preserve V33's supported metadata/builder/AOT corrections and V32's cleanup safeguards.
- Keep metadata/construction work separate from subscription-local values. Do not
  retain arguments, tenant/auth state, request bodies or Reactor context in plans,
  global lookup caches or reusable reporting objects. No unsupported live-config
  mutation contract is introduced.
- No broad handler rewrite, universal policy resolver, reactive-operator framework,
  whole-program allocation target or object pooling without an independently
  justified, selected finding. Prefer local removal of proven unnecessary work.
- No automatic context propagation, new HTTP response-cache semantics, cache
  weighing redesign, telemetry expansion or service-mesh tuning in this roadmap.
- Allocation per operation, retained heap, native/direct memory and RSS are separate
  measurements. Lower B/op is not a memory-leak fix or a pod-memory guarantee.
- Preserve V1-V33 evidence unchanged. New observations and changed conclusions
  belong here, with source/toolchain/fixture provenance and explicit limitations.

## Candidate Questions

These IDs identify hypotheses, not confirmed defects or approved fixes.

| ID | Inspection boundary | Question and limiting contract |
|---|---|---|
| V34-C001 | Observer and lifecycle-hook discovery in the [handler][handler] | How much do empty/single/multiple discovery and composition cost? Can any work be removed without freezing late registrations, changing provider materialization, support checks or order? |
| V34-C002 | Stateless execution, body ownership and subscription state | Which wrappers/state are unnecessary for no-body or immutable-body calls? Can allocation be reduced while resource-owning bodies, repeated subscriptions, cancellation and discard still have exactly their documented owner? |
| V34-C003 | [Request plans][plan], [argument resolution][resolver], URI/default/header projection | Is static derivation repeated on warm calls, or are redundant collections created? Preserve public metadata precedence, wire order/encoding, null versus empty and dynamic per-subscription inputs |
| V34-C004 | Effective optional selection and factory resources | Does an absent/unselected cache/auth/operator/telemetry path perform feature-specific preparation or acquire resources? Preserve explicit activation, no-registry observer behavior and validation before acquisition |
| V34-C005 | Construction, first-call and AOT-only infrastructure | Is cold or framework-lifecycle work escaping onto warm calls or retaining unnecessary runtime state? Keep it separate from steady-state gains; do not reopen V33's general Spring selection review without a reproduced need |

Other findings may be recorded, but need their own measured/reproduced evidence
and selection. File length, duplicate syntax or allocation counts without an
ownership/use analysis do not justify an implementation.

## Priorities

## 1. Post-`4.4.2` Baseline and V34 Scope Integrity

- Record reachable source, clean/dirty state, toolchain, resolved dependencies and
  V33 release evidence. Keep published/API/consumer/benchmark baseline `4.4.2`
  and development `4.5.0-SNAPSHOT` until an explicit release decision.
- On adoption, add the execution checklist and update index, archive guards and
  readiness together. A draft is not active; an adopted roadmap stays active
  through a final version cut until verified closure.
- Freeze the profile definitions above and identify application-owned versus
  starter-owned work. Configuration flags alone are not effective-behavior evidence.

Deliver a baseline/profile inventory. Adoption authorizes characterization and
test work, not automatic implementation of C001-C005.

## 2. Equivalent Workloads and Measurement Rules

- Establish identical baseline/current harness sources and non-starter dependency
  stacks. Resolve published `4.4.2` in an isolated repository, preserve Central
  provenance, and keep shaded artifacts/classpaths before the next build.
- Separate context/proxy construction, first invocation, warm publisher assembly,
  warm subscription and loopback transport. Do not hide deferred initialization
  in warmup and then claim a cold-start improvement, or count startup in every call.
- Cover no-argument GET, path/query/header GET, POST String/JSON, ResponseEntity,
  empty completion and representative HTTP errors. Include Flux/streaming and
  cancellation correctness; measure their cost if the accepted diff touches them.
- Name diagnostic profiles explicitly. Use production proxy/factory paths for
  end-to-end claims; an internal helper row can explain a cost but cannot replace
  them. Metered rows must use the real API name and caller/load reporting path.
- Reuse [benchmark fairness][fairness] for optional raw WebClient/Spring comparison:
  same transport, codec, payload, status/error contract, body consumption and
  enabled features. Release-to-release comparison remains the primary decision input.
- Assert method, target, headers, body/result and dispatch counts outside timed
  sections wherever possible. Publisher construction dispatches zero requests;
  each ordinary subscription performs the expected work. Gate concurrent attachment
  explicitly; a fixed server delay cannot prove that a waiter actually joined.

Before collecting results, record forks, warmup, heap, threads, JVM flags, CPU/
container limits, payload sizes and review criteria. A proposed starting review
trigger is latency above 20% or B/op above max(32 B/op, 5%) relative to baseline,
not an automatic allowable regression. Freeze or justify different per-row
criteria before measurement, never after seeing a candidate score.

Use the GC profiler for allocation and matched multi-fork runs, preserving raw
samples, intervals and order. Confirm flags with another matched pair in reversed
order; investigate variance instead of discarding the worse run. No noisy wall-clock
threshold or forced-GC collection assertion belongs in the ordinary unit suite.

## 3. Default-Path Cost and Ownership Characterization

- Measure the profiles against the same `4.4.2` workloads before edits. Attribute
  dominant CPU/allocation sites to planning, provider lookup, state/collections,
  body/codec, reporting, Reactor or transport rather than treating total B/op as
  starter-only overhead.
- Use focused allocation/CPU profiles when necessary; keep profiler overhead
  separate from scored benchmark runs. Prefer synthetic inputs and sanitized
  aggregate stacks, not production request/identity data in committed evidence.
- Count feature-specific preparation, bean materializations, manager/meter leases
  and transport subscriptions for disabled paths. Distinguish a cheap dormant
  holder from an actual cache, background task or registry lease.
- Record lifetime as well as rate: what survives construction, invocation, terminal
  completion, cancellation and factory close? Do not trade fewer allocations for
  request retention or a globally synchronized hot path.

Deliver a ranked findings table with source anchors, evidence, confidence and
whether the result is intentional cost, regression, correctness risk or hypothesis.

## 4. Explicit Bounded Improvement Selection

- For each reproduced finding, state affected profiles, user impact, smallest
  local correction, expected benefit, semantic dependencies and rollback condition.
- Obtain maintainer approval of specific IDs and their acceptance tests before
  production edits. Prioritize correctness/default-off ownership problems, then
  repeatable material costs; reject changes whose complexity exceeds their value.
- Record unselected items as retained, deferred with owner/trigger, or disproved.
  A characterization-only/no-release outcome is valid. Do not make completion
  depend on implementing every candidate question.

Priorities 5-8 implement only selected findings. A priority with no selected
change retains its relevant regression controls and records production work N/A.

## 5. Planning and Ordinary Invocation Hardening

- Remove only proven repeated static work or redundant argument/default/header
  projections in the existing plan/resolver boundary. Preserve invocation versus
  subscription timing; ordinary arguments are not universally deep-frozen today.
- Exercise complete public metadata, annotation parsing, inherited concrete
  methods/generics and API-ref precedence. Never key a shared plan by insufficient
  method identity or make supplied metadata silently mutable after planning.
- Keep per-call wire values, context idempotency, body presence, charset and URI
  conversion dynamic where required. Do not pre-serialize bodies or normalize
  ordered values just to reduce allocation.

Acceptance pairs exact request/result tests with the selected cold/warm cost rows.
No provider/context/resource semantics may be removed as an incidental fast path.

## 6. Diagnostics and Optional-Feature Cost Isolation

- Optimize provider discovery or observer/hook composition only if selected and
  the supported dynamic behavior is preserved. Cover empty-to-present registration,
  multiple ordered consumers, per-client support, prototype/provider products
  and first-call concurrency. No permanent negative lookup cache by assumption.
- Keep necessary reporting state for auth, selected resilience, generated
  idempotency and logical deadlines even when logging/metrics are absent. Compare
  enabled-only/no-operator behavior without silently changing activation policy.
- Avoid unnecessary observer events, snapshots, body inspection or meter lookup
  on genuinely unconsumed paths; do not suppress application observers/hooks or
  independently enabled pool metrics. Final request facts must follow filters.
- Preserve one terminal event, attempt/dispatch evidence, retry/auth-replay/redirect
  distinctions, timeout phase, cache caller outcome, health sampling and low-cardinality
  privacy contracts for every touched reporting surface.

Acceptance includes missing MeterRegistry, disabled exports and actual selected
integrations. Lower work obtained by omitting requested telemetry is not an improvement.

## 7. Body, Context and Terminal Ownership

- Any conditional ownership guard must retain exactly-once release/transfer for
  DataBuffer, streams/readers/channels and response bodies on success, error,
  empty completion, decode/serialization failure, cancel and pre-dispatch rejection.
- Preserve cold publishers, independent subscription state, backpressure and
  streaming. Test concurrent/repeated subscription with replayable inputs; do not
  claim one-shot request bodies become replayable. Cancellation races must invoke
  discard for values already buffered or arriving after cancellation.
- Preserve context visibility across supported scheduler hops and explicit
  handoff, case-insensitive named access, subscriber/tenant isolation and bounded
  snapshots. Never pool mutable reporting/context holders across callers.
- If touched, keep admission until guarded synchronous/async continuations unwind,
  shared-load state separate from caller deadlines, and refresh/shutdown ownership
  intact. No hidden continuation may advance after the relevant terminal boundary.

Use gates/latches/controlled time and observable cleanup acknowledgements, not
sleep-based attachment or an assertion immediately after a racing signal. Ordinary
tests must pass with explicit GC disabled; use the existing controlled reachability
lane only when making collection claims.

## 8. Inactive Resources and Framework Lifecycle

- Verify no selected-feature cache allocation, auth preparation, operator registry
  acquisition, background work or meter lease on profiles that do not require it.
  Keep classpath-present and physically absent cases distinct.
- Preserve validation-before-acquisition, failed-construction rollback, successful
  ownership transfer, and destroy/recreate with overlapping live metric owners.
  Observe actual leases/resources, not just an unassigned factory field.
- If selected work touches construction or lifecycle tracking, test runtime/AOT
  ordering and non-instantiating diagnostics. No AOT-only tracking should accrue
  unbounded normal-runtime observations, and no optimization may create unrelated
  business factories or dispose application-owned connectors/executors.

Acceptance is bounded ownership and unchanged optional behavior, not a promise
that the framework performs zero allocation or that process RSS immediately falls.

## 9. Cross-Path and Assembled Parity

- Cover mocks, public handler entry points and Spring factory creation for the
  accepted diff. Mock-only or helper-only evidence cannot establish real transport
  cleanup or auto-configuration defaults.
- Use external assembled consumers on genuine matching Boot 4.0.0/4.1.0 parents;
  preserve tracked fixtures or exact overlay generation. Exercise minimal and
  selected controls, including physical optional absence, after relevant changes.
- Run JVM AOT witnesses and compile/execute native evidence for accepted production
  changes affecting request execution, selection or lifecycle. Count all relevant
  server dispatches and wait for bounded quiet/terminal evidence where required.
  Require a clean reachable final source, toolchain/resources, binary hash and
  actual assertions; no stale native binary or JVM-only pass closes this gate.
- For unchanged native inputs or review-only scope, document exact evidence reuse
  or N/A with limitations. Resource failures leave required work pending.

## 10. Matched Performance and Compatibility Evidence

- Run selected before/after rows plus default and enabled-feature sentinels under
  the frozen rules. Preserve original failures/flags and repeated measurements;
  never compare different caches, dispatch counts, payloads or subscription phases.
- Require demonstrated benefit for an optimization, or reproduced safety/ownership
  improvement for hardening with honestly reported cost. Roll back an unhelpful
  optimization; unresolved repeatable regression blocks acceptance unless an
  explicit, bounded correctness tradeoff is approved and documented.
- Run strict root and independent starter source/binary API checks against Central
  `4.4.2`, applicable full/focused module suites and generation packaging. Review
  behavioral compatibility separately from japicmp; preserve valid extension usage.
- Seal source/fixture/artifact/dependency hashes, raw JMH reports, effective flags,
  environment, commands, exits and real test totals. Rerun affected evidence after
  source or fixture edits; document exact unchanged-input reuse otherwise.

Do not promote startup, throughput, tail-latency or pod-memory claims from an
unrelated no-network average-time row. Public numbers require the existing
release-quality benchmark/report promotion process and stated workload limits.

## 11. Maintainer and Operations Guidance

- Document retained defaults, measured profile boundaries, chosen changes and
  intentional costs. Explain why dynamic discovery, state or cleanup remains
  where it is required; avoid advertising a universal zero-overhead mode.
- Provide commands for repeatable performance investigation and scoped regression
  controls. Distinguish current implementation from released availability until
  publication; no new metric or dashboard is promised without explicit scope.
- Explain allocation versus retention/direct memory/RSS, measurement noise and
  profiler impact. Keep support evidence bounded and free of request targets,
  headers, bodies, identities, cache keys or arbitrary exception messages.
- Reconcile findings, deferred owners/triggers, scope and guides without rewriting
  V33's earlier results or attributing unresolved deployment incidents to the starter.

## 12. Scope Decision and Conditional Release Go/No-Go

- Reconcile selected findings, acceptance/rollback decisions and final evidence.
  Approve review-only/no-release closure when warranted, with explicit dispositions.
- For compatible delivered corrections, evaluate a patch first. `4.5.0-SNAPSHOT`
  does not select a minor; choose the exact candidate only after compatibility and
  migration review. No new feature or changed default enters by implication.
- Update coordinates, guards, matrix/current commands, changelog and readiness
  together only after that decision. Keep baseline `4.4.2` until a new publication
  is independently verified; keep V34 active through release preparation.
- Assemble immutable reachable evidence; separate unsigned checks from signing,
  staged artifacts, matching tag/workflow, Central signatures/provenance and
  isolated published consumption. Keep required missing gates open.
- Close roadmap/checklist/index/readiness together after verified release or an
  approved no-release/no-go disposition. Leave future scope unselected.

## Completion Criteria

These are proposed acceptance criteria, not execution evidence:

- [ ] Default/minimal/auto-configured/selected profiles and equivalent workloads
  are defined against published `4.4.2`, with criteria frozen before measurement.
- [ ] Measured findings have explicit dispositions and approved implementation
  boundaries; no unsupported regression or memory-leak premise was assumed.
- [ ] Selected improvements demonstrate benefit or reproduced hardening without
  silently changing defaults, extension behavior or optional-dependency contracts.
- [ ] Request identity, context isolation, reactive/terminal semantics and ownership
  remain covered; allocation reduction does not introduce retention or orphaned work.
- [ ] Required cost, API, module, mock/consumer, Boot/AOT/native and packaging evidence
  identifies final inputs, actual results, failed attempts and reuse limitations.
- [ ] Guidance and any public claims match the evidence and release availability.
- [ ] Release/no-release decision and archive/readiness state are verifiable.

The [execution checklist](CHECKLIST.md) is authoritative for execution status.
Adoption does not authorize production edits, select a release or claim new
benchmark results; approval and evidence gates remain open.

[handler]: ../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/core/ReactiveClientInvocationHandler.java
[plan]: ../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/core/RequestPlan.java
[resolver]: ../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/core/RequestArgumentResolver.java
[properties]: ../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/config/ReactiveHttpClientProperties.java
[planning-bench]: ../../reactive-http-client-benchmarks/src/main/java/io/github/huynhngochuyhoang/httpstarter/core/V33PlanningCostBenchmark.java
[internals-bench]: ../../reactive-http-client-benchmarks/src/main/java/io/github/huynhngochuyhoang/httpstarter/core/StarterInvocationInternalsBenchmark.java
[diagnostics-bench]: ../../reactive-http-client-benchmarks/src/main/java/io/github/huynhngochuyhoang/httpstarter/benchmarks/StarterDiagnosticsOverheadBenchmark.java
[loopback-bench]: ../../reactive-http-client-benchmarks/src/main/java/io/github/huynhngochuyhoang/httpstarter/benchmarks/LoopbackClientComparisonBenchmark.java
[fairness]: ../../reactive-http-client-benchmarks/src/main/java/io/github/huynhngochuyhoang/httpstarter/benchmarks/BenchmarkFairnessContract.java
