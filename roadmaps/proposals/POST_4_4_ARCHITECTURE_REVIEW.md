# Post-4.4.0 Architecture Review Proposal

> **Status:** proposed; not adopted by an execution roadmap
> **Timing:** after verified `4.4.0` publication and V31 closure
> **Theme:** missing contracts, extension constraints, and justified improvements
> **Compatibility:** review first; classify each accepted change separately

## Intent

Use the next roadmap to review the starter's architecture as a whole before
selecting more features. Answer four questions:

1. What necessary behavior, ownership rule, or supported contract is missing?
2. Which realistic application extensions cannot be implemented cleanly today?
3. Which existing boundaries make changes unnecessarily risky or repetitive?
4. Which improvements are genuinely needed, and which should remain unchanged,
   application-owned, documented limitations, or deferred proposals?

This is a proposal for the next roadmap, not an active V32 checklist or a promise
of a `4.5.0` release. It adds no V31 release gate and does not claim that `4.4.0`
has been published. Review preparation may happen now; execution adopts a verified
published baseline after [V31](../v31/ROADMAP.md) closes. A review that justifies no
production change is a valid outcome.

## Starting Points

The current tree already has effective-policy types, declarative grammars,
subscription reporting state, admission helpers, and documented extension APIs.
Their presence is a starting point, not proof that all concerns are separated or
that another abstraction is needed. These are review targets, not defect findings:

| Area | Existing implementation | Question to investigate |
|---|---|---|
| Assembly and ownership | [ReactiveHttpClientFactoryBean](../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/core/ReactiveHttpClientFactoryBean.java) and auto-configuration | Are bean selection, validation, transport construction, and destruction responsibilities explicit and independently testable? |
| Invocation and composition | [ReactiveClientInvocationHandler](../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/core/ReactiveClientInvocationHandler.java) | Can one behavior change without accidentally changing preparation, cache authorization, replay, deadlines, or terminal reporting? |
| Effective contracts | [RequestPlan](../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/core/RequestPlan.java), [EffectiveResiliencePolicy](../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/core/EffectiveResiliencePolicy.java), and [EffectiveCachePolicy](../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/core/EffectiveCachePolicy.java) | Do startup, invocation, AOT, diagnostics, and mocks consume equivalent decisions without erasing unknown states? |
| Cache identity and work | [CacheKeyContract](../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/core/CacheKeyContract.java), [LocalResponseCacheManager](../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/core/LocalResponseCacheManager.java), and admission helpers | Are identity, storage, work lifetime, publication, accounting, and caller ownership separable without weakening invariants? |
| Customization | [ReactiveHttpClientCustomizer](../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/core/ReactiveHttpClientCustomizer.java) and [CacheCustomizationValidator](../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/core/CacheCustomizationValidator.java) | Can applications express required behavior through supported hooks, and understand which guarantees builder replacement or cache-safety classification affects? |
| Context and diagnostics | [RequestContextSnapshot](../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/core/RequestContextSnapshot.java) and [SubscriptionReportingState](../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/core/SubscriptionReportingState.java) | Are capture, trust, handoff, final evidence, and resource lifetimes distinct across asynchronous boundaries? |

Review all shipped modules: starter, test helpers, and optional OTel integration.
Include benchmark, assembled-consumer, AOT/native, and release tooling as evidence
infrastructure. File length or package names alone do not establish a design flaw.

## Review Sequence

### 1. Establish the baseline and architecture map

- Record the verified `4.4.0` artifacts, reachable source revision, supported
  Java/Boot matrix, public APIs, configuration defaults, and replacement SPIs.
- Reconcile V1-V31 decisions, deferred work, incident evidence, and review fixes.
  Separate delivered fixes from recurring design constraints and still-unverified
  reports. Do not reopen completed work merely because its original boxes remain.
- Map module dependencies and the dependency direction between Spring assembly,
  declarative contracts, request execution, transport, cache, and observability.
  Distinguish supported public extension points from public internal bridges.
- Trace startup, ordinary Mono/Flux calls, cache hits/misses, shared loads,
  refresh, streaming bodies, retries, redirects, auth replay, and shutdown.
  Record who owns each resource, when ownership transfers, and who terminates it.
- Inventory mutable configuration and subscription state, synchronization and
  scheduler boundaries, optional dependencies, and lazy bean resolution.

Deliver a source-linked architecture map and contract/owner inventory. Diagrams
must distinguish caller, attempt, shared-load, transport, and factory lifetimes;
a single generic request arrow is not sufficient for composition review.

### 2. Test extension constraints with concrete scenarios

Start with application reports and existing documented SPIs. Use small external
consumer fixtures to test representative needs, rather than assuming that an
internal refactor or a new plugin system is the answer:

- A per-caller authorization or tenant gate composed with cache hits, finalized
  request identity, auth refresh, and custom request mutation.
- A custom auth factory, codec, error mapper, or metadata parser selected through
  supported replacement rules, including lazy and parent/child contexts.
- A custom observer or lifecycle hook receiving consistent terminal facts without
  requiring Micrometer or changing downstream health attribution.
- Explicit context handoff across an application-owned executor, without implicit
  forwarding, trust decisions, or retention of the complete request context.
- A supported WebClient customization, and a deliberate connector replacement,
  with clear boundaries for which transport guarantees remain starter-owned.
- The same applicable extension through production proxies, mocks, assembled
  consumers, and AOT/native creation paths.

For each scenario, record whether it is supported directly, supported with
documented constraints, awkward but correct, requires unsupported internals,
contradicts a supported contract, or is intentionally outside starter scope.
Capture the attempted implementation and exact restriction. Separate a deliberate
safety rejection from an accidental extension gap; relaxing validation is not a
default remedy. New extension ideas require a concrete use case before a spike.

### 3. Identify missing and unnecessarily coupled contracts

Use the maps and consumer scenarios to investigate:

- Policy-selection or validation drift between assembly, runtime, diagnostics,
  mock construction, and native analysis; identify an authoritative decision for
  each fact while preserving legitimate lifecycle differences.
- Extension ordering and the boundary between per-caller gates and actual load
  work, including non-dispatching probes and asynchronous filter continuations.
- Request snapshot, serialized bytes, auth-visible data, final request identity,
  and response ownership across retry, redirect, cache publication, and refresh.
- Exactly-once terminal reporting, cancellation/discard cleanup, admission release,
  eviction generations, late connection tracking, and meter-owner teardown.
- Optional-integration absence, configuration immutability, replacement bean
  selection, and what can be inspected without creating application components.
- Cross-module reliance on internal APIs, duplicated test-only implementations,
  and test fixtures that can pass without exercising the claimed wire behavior.
- Documentation that leaves an ownership or extension limitation implicit, and
  release evidence that is difficult to reproduce against a durable revision.

Do not classify repeated code as unnecessary duplication until its differing
contracts are understood. Reuse existing helpers when they express the same
contract; do not merge caller and hidden-load state just to reduce code size.

### 4. Decide what is genuinely needed

Maintain a finding register. Every finding needs:

| Required field | Decision evidence |
|---|---|
| Problem and affected user | A concrete workflow, failure, or change that is blocked or made risky |
| Current evidence | Reachable revision, source references, reproducer or characterization test; distinguish observation from hypothesis |
| Contract and owner | Existing promise, responsible module, lifecycle and extension boundary |
| Impact | Correctness/security risk, operational cost, affected consumers, and frequency or confidence where known |
| Options | Keep as-is, clarify documentation, fix locally, reuse an existing abstraction, or introduce a narrowly justified boundary |
| Cost and compatibility | Complexity removed and added, migration needs, API/configuration changes, concurrency and performance risk |
| Disposition | Fix now, document, retain intentionally, defer with a trigger, reject, or unresolved pending named evidence |
| Verification | A deterministic acceptance test and any required consumer, native, ownership, or performance evidence |

Prioritize demonstrated safety/correctness gaps first, then repeated supported
extension blockers and measured maintenance cost. Cosmetic consistency and
speculative flexibility do not justify production churn. An accepted boundary
change must explain why a smaller local change or existing SPI cannot solve the
problem, and include a bounded migration/rollback approach.

### 5. Select a bounded follow-up scope

Produce an architecture decision report with the as-is map, extension results,
ranked findings, and explicit no-change decisions. Separate review conclusions
from implementation completion. A spike is not a delivered supported feature.

Adoption into the next roadmap should select a small, justified improvement set
only after the findings are reviewed. Each accepted change needs its own scope,
dependency order, compatibility classification, and tests before implementation.
Record broader redesigns or genuinely new capabilities as separate proposals.
Do not make completion depend on resolving every possible architectural concern.

## Guardrails

- No blanket rewrite, package split, module split, new public SPI, general pipeline
  framework, or replacement of Reactor/WebClient/Caffeine solely for extensibility.
- Preserve explicit opt-in, request isolation, body ownership, bounded cache work,
  deadline and replay semantics, and structural diagnostic privacy by default.
- Do not silently change Java/Boot support, public constructors, configuration
  defaults, optional dependency behavior, or diagnostics schema compatibility.
- Retain strict source/binary compatibility against the adopted published
  baseline. A behavioral or API break needs a separate explicit major-version
  decision and migration evidence, not an architecture-cleanup label.
- Test race-sensitive changes with controlled gates and observable terminal
  signals. Do not replace ownership evidence with sleeps, GC assumptions, or a
  changed implementation's own counters alone.
- Scale verification to accepted changes: affected contracts plus consumer/AOT
  parity for shared boundaries, native execution when reflection or lifecycle
  changes, and allocation/performance measurements for hot-path changes.
- Never infer a memory leak, performance gain, or mesh-specific defect from a
  structural review alone. Such claims need separate reproducible evidence.
- Keep V1-V31 evidence and the already-adopted resilience proposal unchanged.
  This proposal neither changes coordinates nor authorizes publication.

## Adoption Criteria

The proposal is ready to become the next execution roadmap when `4.4.0`
publication and V31 closure are verified, review deliverables and scope exclusions
are agreed, and findings have an explicit prioritization/acceptance process.

The resulting review is complete when every reviewed area has evidence and a
disposition, accepted improvements have measurable acceptance criteria, unresolved
questions name the missing evidence, and maintainers can explain what should
change, what should not change, and why. A release/version decision follows the
accepted implementation scope; the architecture review itself requires no release.
