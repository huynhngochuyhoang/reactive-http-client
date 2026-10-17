# V36 Correction or No-Change Decision

> **Recorded:** 2026-10-14
> **Decision:** V36-P6-1; approved no production change
> **Status:** Priority 6 complete; no runtime correction selected
> **Findings:** A001 and A002 unresolved/blocking
> **Experiment budget:** 48/48 worker slots consumed; no further execution authorized
> **Release scope:** unselected

This records [Priority 6](CHECKLIST.md), following the
[ordinary capture](ORDINARY-CAPTURE.md) and [intervention results](INTERVENTION-RESULTS.md).
It selects what to implement, not whether the allocation findings pass. The
maintainer's no-change decision is **not a performance pass**, an explanation of
either ordinary high mode, or permission to exclude unresolved scope.

## Explicit Decision

The maintainer answered the Priority 6.2 question on 2026-10-14:

> Approve no production change (Recommended)

The question explicitly retained the verified tooling corrections and existing
runtime behavior, kept A001/A002 unresolved/blocking, and excluded authorization
for more experiments, scope exclusion, closure or release. **V36-P6-1 approves
no production change.** No runtime patch is selected or delivered. No V35
candidate is reinstated, and no dependency, public API/SPI, default, resource
owner, tuning flag or telemetry behavior changes.

Clean reviewed source is `71f989f5492bcd8c38d1c63197f56b507fa311c9`, tree
`7f1ea0076f14becc0e19b4d0e6b69230425f236e`. Verification uses that reachable base
plus this documentation/guard patch, not a newly invented clean revision. The
production main trees, POMs and benchmark module are unchanged from the reachable
V36 adoption baseline `728653e4fb3db911cddd362ab0c22708619bb68f`.

## Smallest Options

| Option | Demonstrated need and affected scope | Acceptance, ownership and rollback | Decision |
|---|---|---|---|
| Tooling correction | E001 needed explicit fork/trace association and rejection of incomplete evidence for AUTO_REGISTRY and HOOK. P2-P5 already delivered and tested that boundary. | Benchmark tooling maintainer retains the reader, recorder and bounded runner with their synthetic guards and raw evidence. Invalid association must invalidate its derived conclusion, never erase a score; repair needs a separately specified boundary. | Retain the verified corrections; no additional tooling change is justified by P5. Individual probe census and exact executing code remain limits, not silently repaired facts. |
| Local production fix | Reusing disabled cache-policy decisions or changing discovery might reduce work in a forced high mode, but P5 reproduced no production defect or repeatable attributed ordinary-path benefit. Neither profile has an associated ordinary high trace. | Performance maintainer would need a reproduced defect or equivalent-behavior benefit, a new explicit implementation decision, independent regression controls and rollback. The exhausted worker budget supplies no new comparison authority. | Not selected. Compiler variation alone is insufficient, and V35's rejected local candidates do not become approved fixes. |
| No production change | Preserve required discovery, mutation validation and per-invocation/subscription ownership while accepting the evidence limit, not the unexplained cost. Applies to both profiles independently. | Performance and observer/hook maintainers retain current behavior, focused contract checks and unresolved findings. No candidate exists to roll back; any later change needs new selection and evidence. | Selected by V36-P6-1. Runtime implementation, optimization acceptance and rollback N/A on 2026-10-14 because no correction is selected. |

The intervention's roughly 256 B/op increase occurs on both saved artifacts with
verified resolver non-inlining. That identifies sensitivity to an optimization
boundary, not the original ordinary-mode cause, an exact object count, a leak or
a saved-byte opportunity. T's Selection elimination does not prove which code
executed during an untraced ordinary high fork. AUTO_NO_REGISTRY's explanation
is not transferred to either remaining profile.

## Retained Behavior and Controls

The no-change boundary keeps late registration, prototype/provider behavior,
ordering and `supports` checks, fresh public metadata, full mutation validation
and body/context/terminal ownership. It adds no discovery cache, shared mutable
state, validation shortcut or cleanup exemption. The existing focused suites
exercise these contracts; tests are semantic witnesses, not cost measurements.

| Contract and unchanged source | Existing verification |
|---|---|
| [Invocation discovery](../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/core/ReactiveClientInvocationHandler.java) consumes ordered providers per invocation, preserves fallback/full traversal, and captures actual observers/hooks for the cold publisher. | `DefaultPathCostOwnershipTest`: late and concurrent prototype registration, custom providers, null/error boundaries, ordering, support filtering, ordinary profiles and resource ownership. |
| [Static planning](../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/core/RequestPlan.java) accepts fresh public metadata without mutating it; inherited/client-local plans preserve dynamic request projection. | `PublicStaticMetadataContractTest`: fresh/inherited metadata, API-ref precedence, invalid inputs, repeated subscriptions and wire behavior. |
| [Policy validation](../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/core/CacheWorkPolicy.java) still checks every captured interface method against the current decision; [disabled selections](../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/core/EffectiveCachePolicy.java) are not interned. | `CacheWorkPolicyEnforcementTest` and `DefaultPathCostOwnershipTest`: sibling selection changes, excluded invocations, cold calls, selected work limits and complete scans. |
| Invocation-owned body cleanup remains separate from subscription-local context and terminal reporting. | `StreamingUploadOwnershipTest`, `RequestContextSnapshotTest`, `SubscriptionLocalReportingStateTest`: cancellation/timeout/disconnect cleanup, replay, explicit context handoff, concurrent Mono/Flux subscriptions and terminal isolation. |
| Selected cache/auth/resilience composition remains intact, including per-hit probes, auth replay and retry; enabled-only resilience still selects no operators. | `InvocationCompositionReviewTest` and `ExplicitResilienceActivationContractTest`: ordinary/miss/hit/retry/replay paths and explicit operator selection. |

**Implementation N/A:** there is no selected runtime correction, so new
candidate-specific regression fixtures, optimization scoring, candidate rollback
and attributed-benefit acceptance are not executed or marked passed. Existing
focused tests substantiate retained behavior without claiming a production fix.
No API/default/dependency or architectural need has been demonstrated in this
scope; such a need stops here for separate approval rather than broadening V36.

## Independent Dispositions

| Finding | Evidence and limit | Current disposition / owner |
|---|---|---|
| A001 / AUTO_REGISTRY | O/M/T stayed low; I was high on both artifacts with compiler refusals. The historical ordinary high mode still lacks associated causal evidence. | **unresolved/blocking**; performance maintainer. No-change selection is not an explanation. |
| A002 / HOOK | Each artifact produced untraced ordinary high and low forks; M/T were low; I was high. Real hook semantics remain, but the ordinary high workers have no traces. | **unresolved/blocking**; performance maintainer with observer/hook review. No inference from A001 or AUTO_NO_REGISTRY resolves it. |
| E001 / evidence reliability | Association/validation is **verified for the new capture** within the recorded limits. | Evidence maintainer retains the correction. Legacy limitations remain: legacy pairing, exact executing-code identity and individual probe census are unverified. |

Priority 7 must preserve original scores and review each flag and their combined
acceptance independently. No-change does not satisfy those gates. Priority 8
still owns delta-appropriate compatibility/parity verification; this step does
not close it by reference. Priority 10 alone can approve release/no-release or an
explicit scope exclusion. V36 stays active with `plannedFinalVersion=null` and
`releaseLane=unselected`; V1-V35 history and V35's unchecked cost gates stay intact.

## Verification and Preservation

Fresh retained-behavior verification passes **141 cases across eight classes on
each Boot row** (4.0.0 and 4.1.0), zero failures/errors/skips. The classes above
contribute respectively 42 discovery/ownership, 25 metadata, 12 mutation/work,
24 body, nine context, five reporting, seven composition and 17 resilience
cases. These are the same cases on two supported stacks, not 282 distinct
contracts. **83 tooling tests** pass without starting a benchmark worker.
Documentation/readiness/link guards separately pass **114 cases per Boot row**,
zero failures/errors/skips, bringing the selected Java total to 255 per row.
The new guard first failed because this decision record did not exist. The
first full documentation run caught an older assertion still requiring the
unselected scope; that assertion now checks the approved no-change state and
both corrected rows pass. Both failures are preserved. `git diff --check` passes.
Java 21.0.8/Maven 3.9.9 use a 512 MiB Maven heap
and two active processors; ordinary test forks disable explicit GC and make no
forced-collection claim.

All **798 files** in the P4/P5 bundles and their two archive hashes revalidate.
The 202 production/build/benchmark input files inspected are unchanged from the
reachable V36 adoption source. This verifies the applicability of the recorded
options to unchanged inputs, not a fresh performance or native result. P4's
probe-accounting exception and P5's inferred-probe limit remain unchanged.
V1-V35 files, experiment tooling and the frozen approval documents are untouched.

Commands, exits, actual XML totals, input hashes and the base-plus-source patch
are preserved under `target/release-evidence/v36/priority6/`, then copied to
`verification-evidence/v36-priority6/` and a verified dated archive. The checklist
records the external seal. Ignored bundles are not available in a clean clone;
restore and hash-check recordings before any further analysis. No root cleanup
is needed for this decision.

Focused verification (set JAVA_HOME/PATH to the Java/Maven paths recorded in the
bundle; use `-Dspring-boot.version=4.1.0` for the second row):

```bash
mvn -B -ntp -s .mvn/maven-central-settings.xml \
  -Dmaven.repo.local=/tmp/rhttp-v35-p10-20261011/repository-current \
  -Dspring-boot.version=4.0.0 -pl reactive-http-client-starter \
  -DargLine='-XX:+DisableExplicitGC -XX:ActiveProcessorCount=2' \
  -Dtest=DefaultPathCostOwnershipTest,PublicStaticMetadataContractTest,CacheWorkPolicyEnforcementTest,StreamingUploadOwnershipTest,RequestContextSnapshotTest,SubscriptionLocalReportingStateTest,InvocationCompositionReviewTest,ExplicitResilienceActivationContractTest test
mvn -B -ntp -s .mvn/maven-central-settings.xml \
  -Dmaven.repo.local=/tmp/rhttp-v35-p10-20261011/repository-current \
  -Dspring-boot.version=4.0.0 -pl reactive-http-client-starter \
  -DargLine='-XX:+DisableExplicitGC -XX:ActiveProcessorCount=2' \
  -Dtest=DocumentationReleaseArtifactTest test
python3 -B -m unittest discover -s scripts -p 'test_*v3[3456]*.py' -v
git diff --check
```

No new benchmark, diagnostic worker, preflight or internal JMH probe is run.
No fresh full-reactor, strict API, assembled-consumer or native run is claimed.
The previous results remain historical evidence with their own source identities
and limits; verifying their hashes does not recreate or extend their execution.
