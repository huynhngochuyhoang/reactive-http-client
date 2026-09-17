# V32 Architecture Scope Decision

> **Status:** F004 + F005 implemented; Priority 10 verification complete
> **Reviewed:** 2026-09-16
> **Source baseline:** `15d0d16bc81f92ed920679f61754e8df0e32bfd2`
> **Published / development:** `4.4.0` / `4.5.0-SNAPSHOT`
> **Implementation scope:** V32-F004 + V32-F005
> **Release scope:** unselected

This consolidates [findings](FINDINGS.md) and the completed reviews for
[Priority 8](CHECKLIST.md).
The maintainer approved the bounded F004 + F005 scope below; this is not
authorization to publish. Earlier review records describe their dated as-is
checkpoints. This record owns the scope decision for Priority 9. The dated
[implementation result](ACCEPTED-IMPROVEMENTS.md) records the delivered corrections
and focused tests separately. [Priority 10 verification](COMPATIBILITY-VERIFICATION.md)
passes the broader JVM, API, matrix, consumer, AOT and native lanes. Earlier
native build failures are retained alongside the successful post-restart run.

## Ranked Findings

Impact order is not implementation dependency order. All five are reproducible
within the stated fixture; none establishes prevalence in deployed applications.
The register retains the source, reproducer, owner, lifecycle and original
provenance for each ID. The original consolidation did not implement a fix;
current implementation dispositions live in the register and Priority 9 result.

| Rank | ID | Necessity and confidence | Approved disposition / owner and trigger |
|---|---|---|---|
| 1 | V32-F004 | Confirmed failed-construction retention: repeated rejected public handler creation leaves cache meter leases without a returned owner. No response or forced collection is needed to observe it | Accepted local cleanup; handler-assembly owner. Must pass before claiming rejected construction is leak-free. This is not a production pod-memory diagnosis |
| 2 | V32-F003 | Confirmed runtime/AOT properties-selection drift for initialized non-primary candidates; primary is a passing control. Not an observed native-image failure | Deferred; AOT owner. Reopen when a supported application cannot designate its intended properties bean primary, or before changing this lookup. Parent/prototype permutations still need evidence |
| 3 | V32-F001 | Confirmed starter-builder exemption lost when validation receives ApplicationContext rather than its bean factory; application mutations still require classification | Deferred; factory/cache-validation owner. Reopen when the inspected-builder SAFE workaround is unacceptable or builder selection changes; full-Boot builder variants need separate coverage |
| 4 | V32-F002 | Confirmed fresh static public metadata passes validation but fails before returning a publisher because its internal derived API is absent | Deferred; metadata/planning owner. Reopen for a parser that cannot delegate or use the tested public API-ref alternative. Invalid/generic/timeout fallback cases need new regressions |
| 5 | V32-F005 | Confirmed test-environment gap: two ordinary caller-retention cases fail with explicit GC disabled and pass in the corresponding controlled JVM. Not a production leak | Accepted test-lane correction, before full-suite portability claims; test/ownership owner. Inventory all 16 collection-dependent cases, without pretending all 16 had the two-JVM comparison |

The approved scope is **F004 + F005**: correct an observed unowned
resource path, and make the ownership verification independent of opportunistic
collection. F005 ranks below production defects in impact but is a verification
prerequisite for a trustworthy ordinary-suite result. F001-F003 have demonstrated
bounded workarounds; their deferral does not resolve those defects.
Adding any deferred finding requires a new explicit maintainer decision.

F001 and F003 both involve bean selection, but one inspects builder ownership and
the other chooses build-time properties: no common resolver defect was proved.
F002 is a derived-metadata gap, not another instance of either lookup failure.
F004's meter-owner root and F005's collector-dependent observation are distinct.
Historical cache races, retry defaults and header casing are not reopened as new
findings. The earlier pod-memory report still lacks deployment-specific retained
roots; the header report has V31's named-reader alternative, not a mesh diagnosis.

## Alternatives and Necessity

| Finding | No change / documentation | Existing helper or SPI | Smallest correction and rejected new boundary |
|---|---|---|---|
| F004 | Document provider-aware valid construction; this does not clean an already rejected allocation | Mirror the mock bridge's local ownership rollback, not its whole assembly pipeline | Close only the newly created manager if subsequent handler construction fails; early validation may supplement but cannot replace rollback. No public disposal SPI or general assembly framework |
| F003 | Document primary-only workaround; acknowledge remaining runtime/AOT drift | Prefer Spring's existing candidate-resolution semantics at the properties boundary; preserve environment binding when appropriate | Correct only AOT properties choice, not non-instantiating diagnostic inspection. No universal eager resolver or new registry abstraction |
| F001 | Explicitly classify the inspected starter builder SAFE as well as every application customization; do not recommend blanket SAFE declarations | Existing owning-context/bean-factory facilities and customization validator | Resolve ownership through the actual factory/context while retaining application-builder checks. No name-only exemption or new customization SPI |
| F002 | Delegate built-in metadata parsing or declare the tested API-ref mapping; acknowledge duplication/limitations | Existing public metadata fields, concrete plan and API-ref validation | Derive missing static effective API from validated public metadata at the existing planning boundary. Do not expose EffectiveApi or introduce a second metadata hierarchy merely to bypass this gap |
| F005 | Document a restrictive ordinary-test JVM, which leaves common JVM configurations unsupported; longer sleeps give no guarantee | Reuse the controlled-fork pattern from AsyncHandoffReachabilityIT and deterministic ownership witnesses | Keep observable cleanup assertions in regular tests; relocate collection assertions to an explicit checked JVM lane. No production instrumentation API, blanket test disabling or removed reachability coverage |

## Acceptance and Verification Budget

The F004/F005 requirements below are frozen for the approved scope; F001-F003
remain evaluated alternatives, not additional implementation work. These are
pre-implementation requirements, not completed verification. A budget
means the named minimum lanes and scope, not a promised test count or elapsed
time. A newly affected boundary requires review instead of silently waiving a
lane. Source/binary compatibility below is the intended constraint, to be checked
against published `4.4.0`; behavior and configuration require separate tests.

### V32-F004

- Boundary / cost: public handler assembly owns the manager until construction
  succeeds. Add a local failure guard, reusing existing close semantics; no
  per-request state, synchronization or configuration. Expected startup-only cost.
- Acceptance: repeated rejected constructions leave no new registry leases,
  gauges or retained manager owner, both alone and beside a same-tag live owner.
  Exercise auth-input rejection and a later assembly failure, telemetry on/off,
  selected-cache dependency failure and successful ownership transfer. Preserve
  the original failure; attach cleanup failures without replacing it. Supplied
  WebClient, auth, registry and other owners remain usable.
- Verification: change ResourceOwnershipReviewTest's gap assertions to the
  desired behavior; run cache telemetry/construction, mock lifecycle and optional
  absence regressions, full starter/helper suites and assembled creation/close
  scenarios. Run strict root and independent starter API checks and supported
  Boot rows. Run JVM AOT and clean-source native lifecycle/creation evidence for
  this changed assembly boundary, with a witness for failed creation if feasible
  without exposing internals. Priority 10 records the completed native gate and
  the JVM-only scope of private failed-construction lease inspection.
- Compatibility / rollback: no API signature, default or validation relaxation;
  only abandoned-resource cleanup changes. No application migration. Roll back
  this guard if it closes a successful or application-owned component, hides the
  original error, or requires hot-path restructuring. No new JMH lane for a
  strictly construction-only patch; any steady-state edit reopens that decision.

### V32-F003

- Boundary / cost: replace first-singleton preference only in AOT properties
  resolution; remove duplicated precedence where Spring already owns it. Build
  time may instantiate configuration, not business clients; diagnostics retains
  its distinct no-instantiation contract. No per-call cost or new public API.
- Acceptance: all four existing primary/non-fallback/priority/default cases use
  runtime's preferred properties. An invalid preferred configuration fails rather
  than falling back to a valid inactive bean. Preserve environment, primary
  programmatic and foreign-factory paths. Add parent, ambiguity, lazy/prototype
  and FactoryBean tests for the actual candidate traversal, avoiding duplicate
  materialization and documenting any unsupported shape.
- Verification: paired selection and AOT smoke tests, full starter suite,
  assembled programmatic-properties consumer, strict API and both Boot rows;
  clean-source native compile/run exercising selected properties and cache hints.
  E7-03 needs a selected recursive-graph witness only if traversal/hints change.
- Compatibility / rollback: intended source/binary compatible selection fix; no
  new configuration. Applications accidentally relying on first-registration
  choice change behavior and need a correction note. Roll back/review on changed
  runtime selection, lost environment/programmatic config or business creation.
  No benchmark for an AOT-only patch; no general resolver extraction authorized.

### V32-F001

- Boundary / cost: owning bean-definition lookup in CacheCustomizationValidator;
  retain proof of starter management and all other safety checks. Construction
  and inspection only, no dispatch fast path or new configuration field.
- Acceptance: the external default-builder case needs no redundant entry through
  context, bean factory, AOT and diagnostics. A same-named application replacement,
  inherited/shadowed builder and Boot/per-client mutations remain classified or
  rejected; lazy inspection must not create them. A SAFE label alone is not a
  security proof and does not waive variant/auth validation.
- Verification: E12 paired external scenario plus negative customizer inventory,
  hierarchy/lazy inspection and AOT tests; full starter/helper, assembled/minimal
  consumers, strict API, supported Boot rows and clean-source native creation.
- Compatibility / rollback: intended source/binary compatible repair of the
  documented exemption; existing explicit SAFE entries remain accepted. No
  mandatory migration. Stop if any unclassified application mutation is accepted
  or ownership requires trusting only a bean name. No hot-path measurement unless
  the implementation leaves the construction/inspection boundary.

### V32-F002

- Boundary / cost: static effective-API derivation at existing metadata/planning
  boundary; keep the derived internal type internal. Avoid duplicate mutable
  models, repeated per-call parsing or changes to API-ref precedence.
- Acceptance: an external parser using only public static setters dispatches its
  declared target. Deliberate validation rejects incomplete/invalid metadata;
  API-ref alternative, concrete generics, inherited methods, method/client/ref
  timeouts, return grammar, identity and cached-plan reuse stay correct. Do not
  require reflection, package relocation or an additional public constructor.
- Verification: external E06 desired result and negative variants, metadata/plan,
  URI/cache/return/timeout regressions, full affected starter/helper and assembled
  consumers, strict API and Boot rows. JVM AOT and clean native with replacement
  metadata are required because planning/creation changes. If invocation or plan
  allocation changes, compare identical cold/warm workloads against `4.4.0`;
  discovery alone cannot establish cost.
- Compatibility / rollback: intended source/binary compatible public-extension
  correction; malformed inputs may deliberately fail earlier, which must be
  documented. No configuration migration for valid metadata. Stop if it requires
  public internal-type promotion, a breaking setter contract or broader parser
  redesign. Roll back on precedence drift or per-subscription model leakage.

### V32-F005

- Boundary / cost: the 16 inventoried collection-dependent cases in
  ResponseCacheRetentionOwnershipTest, CacheWorkOwnershipContractTest and
  CacheCallerAdmissionContractTest. Retain deterministic cleanup/state witnesses
  in ordinary tests and the same reachability scenarios in a controlled lane.
  Do not sweep in unrelated tests that merely call System.gc without requiring
  collection. Test configuration changes only; no production/API/hot-path cost.
- Acceptance: ordinary affected suites pass with `-XX:+DisableExplicitGC` and
  still detect missing slot release, eviction or owner detachment. Controlled
  probes validate collector, explicit-GC availability and bounded heap, execute
  all migrated scenarios, and reject wrong JVM prerequisites clearly. Separate
  caller-owned work from manager-owned work; do not invent collection guarantees.
- Verification: per-scenario before/after inventory, ordinary tests with explicit
  GC disabled, controlled opt-in fork XML, negative prerequisite case, then full
  affected starter suites on the supported Boot rows. Preserve actual counts for
  ordinary versus opt-in lanes; no skips presented as passes. Reuse the existing
  profile pattern without silently changing the V31 scenario contract.
- Compatibility / rollback: source/binary/behavior/config unchanged for consumers;
  contributor CI commands must explicitly schedule the new controlled lane.
  Roll back if a scenario disappears, regular assertions weaken, or the ordinary
  suite still requires collection. API, assembled consumer, native and benchmark
  reruns are not required for this test-only item alone; combined production
  selections retain their own requirements.

Approved execution order: F005's deterministic test foundation,
then F004's local fix, then combined verification. There is no production-code
dependency between them. Rerun shared creation tests after the F004 boundary.
Never make F004 depend on a general factory or metadata refactor.

## Reviewed Areas and No-Change Outcomes

| Reviewed area | Evidence-backed disposition / limit |
|---|---|
| Module and public surface | [Map](ARCHITECTURE-MAP.md), [module review](MODULE-EVIDENCE-BOUNDARIES.md): keep starter/helper/OTel ownership, aligned artifacts and public-internal bridges. Package sharing or class size alone is no reason for a module split or new SPI |
| External extension needs | [E01-E12](EXTENSION-SCENARIOS.md): retain existing named readers, explicit handoff, auth factories, codec/decoder and connector customization. F001/F002 are specific gaps; safe customization classification and bounded serialization are intentional constraints |
| Policy and component selection | [Selection review](EFFECTIVE-POLICY-SELECTION.md): explicit operator activation, shape-specific availability, unknown diagnostic facts and direct-type versus ordered-stream lookup stay distinct. F003 does not justify eager diagnostics or a universal selector |
| Mutable configuration and plans | Selection mutation inventory: no general hot reload; bound/mapping mutation guards are not full immutability. Recreate factories for configuration changes. Fresh metadata construction (F002) is distinct from mutating a consumed plan |
| Invocation, identity and replay | [Composition review](INVOCATION-COMPOSITION.md): preserve per-caller auth, finalized request identity and publication check, independent caller/load deadlines, hidden auth replay versus outer attempts and terminal isolation. No second pipeline or blanket handler extraction |
| Cache, concurrency and shutdown | [Ownership review](RESOURCE-OWNERSHIP.md): retain bounded storage/work, token freshness, cleanup bracketing and same-tag meter ownership. F004 is partial-construction-specific; independent caller loads and application connectors remain external owners. No global deadlock or universal shutdown-deadline claim |
| Observability and privacy | Composition/module evidence: cache-local outcomes are not downstream traffic; custom terminal fields survive absent registry; OTel SDK remains application-owned. Keep bounded structural support evidence and independent pool activation; no new exported signal is justified here |
| Mock, optional integrations and AOT | Module matrix: helper shares engine, not connector/wire proof; minimal consumer explicitly enables resilience with no selected operators/classes. Cached/uncached inspection and optional absence remain distinct. F003 and E7-03 limits stay visible |
| Evidence and historical incidents | [Baseline](BASELINE-SCOPE.md) and E7-01-E7-08: F005 is a JVM-lane gap. Native timing, generic traversal and historical provenance limits remain explicit. No new RSS/heap, speed, mesh or application-incident diagnosis |

## Deferred Questions and Stop Conditions

Deferrals below follow the 2026-09-16 maintainer decision. Owners are repository roles,
not an assertion that an individual accepted an assignment.

| Question / proposal boundary | Owner / reconsideration trigger | Missing evidence and current disposition |
|---|---|---|
| F001-F003 outside the approved scope | Selection/AOT/planning maintainers; a documented workaround blocks a real consumer or a change touches that boundary | Desired-behavior negative/parity tests named above; keep confirmed gap status, do not mark fixed |
| General resolver, pipeline extraction, public metadata model or module split | Architecture maintainer; repeated independent consumer needs and measured change cost | No such necessity established. Excluded from V32's approved scope; a separate proposal with migration and compatibility review must precede any adoption |
| New transport/backend, distributed cache, hot reload, automatic forwarding/MVC/context propagation | Feature owner; concrete unsupported application workflow | No reproducer requiring these features from this review. Remain non-goals; no empty feature proposal created to imply demand |
| E7-03 selected recursive AOT traversal | AOT owner; traversal/hints change | Existing unselected test does not reach that guard; add selected-path witness then. Missing coverage is not a reproduced stack overflow |
| E7-04/E7-05 native timing/instrumentation | Native owner; next affected native fixture/build | Retain counted transport and deterministic JVM contracts; change timing only with evidence and invalidate old binary provenance. No new flake asserted |
| Pod memory, mesh/context loss, historical raw measurements | Operations owner; sanitized deployment/version/checkpoint capture or reproducer | Need actual roots/domain measurements or named-reader/filter/handoff reproduction. Old measurements and F004 do not establish current deployment cause |

Stop and return to 8.3 for new APIs, dependency changes, defaults, breaking
behavior, broader bean discovery, per-request extraction, or a required lane
that cannot run. An unavailable required lane remains pending. Neither urgency
nor a green characterization authorizes adjacent cleanup or a release.

## Maintainer Decision

**Approved by the maintainer on 2026-09-16.** In response to the explicit 8.3
scope question, the maintainer selected: "F004 + F005 (recommended):
failed-construction cleanup and controlled reachability testing; defer F001-F003
with documented workarounds."

| Finding | Disposition | Scope or workaround |
|---|---|---|
| V32-F004 | accepted | Local rollback of a newly allocated cache manager when subsequent handler construction fails; preserve original failure and all other owners |
| V32-F005 | accepted | Deterministic ordinary ownership tests plus controlled reachability probes for the 16 inventoried cases; preserve coverage and validate JVM prerequisites |
| V32-F001 | deferred | Explicit SAFE classification of the inspected starter builder, alongside every applicable application customization; no blanket validation exemption |
| V32-F002 | deferred | Delegate built-in parsing or use the tested public API-ref metadata/configuration alternative |
| V32-F003 | deferred | Designate the intended programmatic properties bean primary; retain the runtime/AOT drift finding for other supported selection modes |

The per-item boundaries, acceptance, verification budgets, compatibility,
execution order and rollback above apply to F004/F005. Both are blocking for the
selected V32 implementation scope until verified or explicitly removed by a new
maintainer decision. No fix is delivered by checking 8.3. Non-selected findings
retain their owners, missing evidence and reconsideration triggers; they are
not blockers for this bounded scope and are not marked resolved.

Review-only is not the selected branch: its 8.3 conditional item is **not
applicable** on this dated decision. Priority 9 is now implemented and recorded;
Priority 10's applicable production/test verification and Priority 11 guidance
are not waived. F005 alone would not require API/native/performance checks, but
F004's production requirements still apply to the combined scope. Priority 12
still determines release or no-publication after results exist. No final version,
signing, publication or publication-only exemption is selected here.

## Verification and Provenance

Preparation began on the clean reachable revision above. No production, public
API, dependency, coordinate, native fixture or V1-V31 record is edited. Original
finding reproducers and the published/native/benchmark evidence keep the source
and limits in their linked reviews; those are not new runs of selected fixes.
Fresh documentation/characterization commands, actual totals, source patch,
settings, toolchain and hashes are recorded under
`target/release-evidence/v32/priority8/` and summarized in the checklist. That
sealed bundle describes the pre-approval recommendation. Approval-time source
copies and fresh verification are separately retained under
`target/release-evidence/v32/priority8-approval/`; the earlier provenance is not
rewritten to claim that approval already existed.

Priority 9 implementation has its own [result record](ACCEPTED-IMPROVEMENTS.md)
and `target/release-evidence/v32/priority9/` bundle. The paragraphs above describe
Priority 8's documentation-only evidence, not the later production patch.
The [Priority 10 record](COMPATIBILITY-VERIFICATION.md) preserves fresh full
matrix, API, consumer, controlled-JVM, AOT and clean-source native evidence,
including earlier build failures and the successful post-restart compile/run.
The accepted findings have completed Priority 10 verification. Priority 11
guidance and Priority 12 release review remain open; release is unselected.
