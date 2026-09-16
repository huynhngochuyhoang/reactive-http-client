# V32 Bounded Accepted Improvements

> **Status:** implemented with focused verification; Priority 10 pending
> **Date:** 2026-09-16
> **Starting commit:** `59fd8b7b2e6ee20aca65d08ad8ee7aaf871d7977` (clean)
> **Published / development:** `4.4.0` / `4.5.0-SNAPSHOT`
> **Approved scope:** V32-F004 + V32-F005 only

The [maintainer decision](ARCHITECTURE-DECISION.md) selected these two boundaries.
This records their implementation, not full release readiness. Release scope remains unselected.
F001-F003 remain deferred with their recorded workarounds and reconsideration
triggers. No experimental API, migration, dependency or refactor is included.

## F004

Public `ReactiveClientInvocationHandler.create` owns the newly allocated
`LocalResponseCacheManager` until the handler constructor returns. It now closes
that manager on `RuntimeException` or `Error` from construction and rethrows the
same failure; a cleanup failure is suppressed on the original. A null manager
(cache unselected) requires no cleanup. Successful creation transfers ownership
unchanged. Manager-internal construction rollback is unchanged.

This is one local try/catch, following the manager's existing failure idiom.
No steady-state request code, filter order, policy, identity, replay, timeout,
optional integration, public signature or configuration/default changes.
Supplied WebClient, auth provider, registry and other same-tag owners are not
closed. The fix does not introduce a general factory rollback transaction or
promise successful cleanup when an application cleanup callback itself fails.

Before: the Priority 6 fixture observed one abandoned lease and 16 additional
maximum entries per rejected public create; three failures survived valid-owner
and context teardown. The new desired assertions were run before the production
edit: 15 cases, six failures, zero errors/skips. They detect both abandoned owners
and missing suppressed cleanup failures. An earlier test-compilation error
incorrectly used SimpleMeterRegistry as AutoCloseable; that attempt is retained
separately, not presented as a behavioral failure.

After: `ResourceOwnershipReviewTest` exercises repeated auth-input rejection
with/without a live same-tag owner and telemetry, later WebClient mutation failure
for runtime exceptions and errors, original/suppressed failure identity, missing
Caffeine through the public entry point, cache-selected/unselected successful
proxy calls, and the original factory/connector controls. Registry lease sets
and meters return to the pre-call state; another owner remains open and borrowed
components remain usable. Cleanup-failure injection uses a mocked manager only;
real registry/manager paths prove normal rollback and successful transfer.

No application migration. This correction addresses the reproduced construction
root only, not the historical pod-memory incident or arbitrary late factory failures.

## F005

Ordinary tests invoke the same scenario bodies with reachability probes disabled.
They retain deterministic terminal/slot/token, eviction, cancellation, active-owner,
cache-state, metadata and meter-removal assertions. Actual weak-reference collection
checks are invoked only by `CacheOwnershipReachabilityIT`, outside default Surefire
test-name patterns. Its checked prerequisites are SerialGC, explicit GC enabled
and a maximum 128 MiB test heap; failure is explicit, not a skipped test.

The new `v32-cache-reachability` profile forks a dedicated JVM, disables fork reuse,
sets those flags and uses `target/cache-reachability-reports`. CI explicitly runs
this lane and uploads its XML separately from ordinary reports. The existing V31
profile and its scenarios are unchanged. Shared scenario bodies avoid copying the
fixtures or silently removing their collection assertions. Test-only boolean
parameters select the extra probes; there is no production instrumentation.

### Preserved Scenario Inventory

Every row runs ordinary cleanup assertions and is delegated to by the controlled
lane under the same method name. Counts include boolean parameterizations.

| Original class | Scenario | Cases |
|---|---|---|
| ResponseCacheRetentionOwnershipTest | terminalOutcomesReleaseTransientOwnersWhileTheManagerRemainsOpen | 1 |
| ResponseCacheRetentionOwnershipTest | expiryCapacityAndRefreshTransitionsReleaseDisplacedOwners | 1 |
| ResponseCacheRetentionOwnershipTest | independentLoadRemainsCallerOwnedAfterManagerCloseAndReleasesAtCallerTerminal | 1 |
| ResponseCacheRetentionOwnershipTest | detachedWaiterReleasesItsArgumentsContextAndStateBeforeTheLeaderEnds | 1 |
| ResponseCacheRetentionOwnershipTest | detachedLeaderReleasesItsCallerStateWhileAWaiterKeepsTheLoadAlive | 1 |
| ResponseCacheRetentionOwnershipTest | hiddenRefreshTerminalPathsReleaseTheirCapturedState | 1 |
| ResponseCacheRetentionOwnershipTest | preparedBodyAuthContextFrozenArgumentsAndResponseMetadataEndAtPublication | 1 |
| ResponseCacheRetentionOwnershipTest | closeRemovesMeterRootsAndRejectsLatePublication | 1 |
| ResponseCacheRetentionOwnershipTest | retainedDiagnosticsMapDoesNotOwnFactoryManagerCacheOrValue | 1 |
| CacheWorkOwnershipContractTest | rejectedAndSkippedClosuresCollectWhileAdmittedOwnersRemainAlive | 1 |
| CacheWorkOwnershipContractTest | detachedCallerCollectsWhileSourceRetainsItsOwnState | 2 |
| CacheWorkOwnershipContractTest | evictionReleasesValuesBeforeCloseWithoutReleasingRunningLoad | 2 |
| CacheCallerAdmissionContractTest | terminalPreparationReleasesArgumentsContextAndAuthWhileManagerStaysOpen | 2 |

Total: **16**. The policy-mutation case remains ordinary, not a reachability
scenario. Explicit eviction still checks the capacity survivor before manager
close. Independent unbounded loads remain caller-owned after close until their
own terminal signal; this ownership distinction was not rewritten.

### Contributor Commands

Ordinary cleanup on a JVM that ignores explicit GC:

```bash
mvn -B -ntp -s .mvn/maven-central-settings.xml \
  -pl reactive-http-client-starter -DargLine=-XX:+DisableExplicitGC \
  -Dtest=ResponseCacheRetentionOwnershipTest,CacheWorkOwnershipContractTest,CacheCallerAdmissionContractTest test
```

Controlled collection, separate from the ordinary suite:

```bash
mvn -B -ntp -s .mvn/maven-central-settings.xml \
  -pl reactive-http-client-starter -Pv32-cache-reachability test
```

To verify rejection of unsuitable prerequisites, deliberately bypass the profile
and select the IT with `-Dtest=CacheOwnershipReachabilityIT` and one of:

- `'-DargLine=-Xms64m -Xmx128m -XX:+UseSerialGC -XX:+DisableExplicitGC'`
- `'-DargLine=-Xms64m -Xmx128m -XX:+UseG1GC -XX:-DisableExplicitGC'`
- `'-DargLine=-Xms64m -Xmx256m -XX:+UseSerialGC -XX:-DisableExplicitGC'`

Each command is expected to fail before scenario execution, respectively at the
explicit-GC, collector or heap prerequisite. Those three failing prerequisite
checks are negative evidence, not 16 skipped scenarios or passing collection runs.

## Verification

Evidence is recorded from the reachable starting commit plus the source patch,
not a clean release commit. Stage commands, UTC timestamps, exit statuses, fresh
XML, toolchain/settings and final source hashes are retained under
`target/release-evidence/v32/priority9/`. Preserve this directory before root clean.
The [checklist](CHECKLIST.md) records the final applicable suite totals.

The fresh pre-edit caller-retention baseline reproduced two failures with explicit
GC disabled. After the split the ordinary three-class run passed 79 cases without
explicit GC. The controlled lane passed all 16 scenarios. All three deliberately
invalid JVM configurations failed their prerequisite assertion. Later focused
verification covers construction, cache composition/telemetry and helper parity;
these counts are not full-module or native evidence.

Priority 10 still owns full affected-module tests, supported Boot rows, fresh
Central strict root/independent-starter API comparisons, assembled consumers,
JVM AOT and clean-source native creation/lifecycle evidence. No reused historical
native binary is relabeled as validation of this patch. No steady-state changes
justify a new JMH lane, and no heap/RSS or numerical performance claim is made.
