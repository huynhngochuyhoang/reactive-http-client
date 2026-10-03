# V34 Body, Context and Terminal Ownership

> **Status:** Priority 7 complete, 2026-10-03
> **Delivered production IDs:** none
> **Production scope:** N/A; C002 deferred, C004 rolled back in Priority 6
> **Release scope:** unselected

This executes [Priority 7](CHECKLIST.md) under the existing
[scope decision](IMPROVEMENT-DECISION.md). Starting clean source:
`946fffe6c1e0edfcad8a5aee4803def5b12591b4`. The recorded patch contains only
tests and current guidance. No invocation, body, context, reporting, admission,
cache or transport production code changes. Earlier dated records remain historical;
the [Priority 6 rollback](HARDENING-EVIDENCE.md) is not reopened.

## Owners and Terminal Boundaries

| Owner | Retained contract | Observable evidence |
|---|---|---|
| Ordinary returned publisher | Resolved arguments and body remain invocation-owned; subscription creates its own request/reporting state. No new deep snapshot of mutable ordinary arguments | Cold assembly, independent repeated subscriptions, final request/result evidence and distinct reporting states |
| `RequestBodyOwnership` | Per-logical-call guard closes eager stream/reader/channel once. A direct DataBuffer is released before writer attachment or transferred to the writer; non-closing adapters avoid duplicate stream/channel closure | Close counters, pooled buffer reference counts, cancellation/terminal acknowledgements and real loopback body bytes |
| Application publisher / Resource | Publisher controls replay and allocates fresh buffers for each subscription; a reusable Resource reopens per transport attempt | Subscription/open/close counts across retry, redirect and 401 replay; bounded demand and peer-reset cleanup |
| Response body | Unary decode/void/error handling consumes or releases its body. Raw streaming buffers transfer to the subscriber, which releases them; a streaming ResponseEntity's inner body remains caller-owned after envelope success | Error-capture release checks, one-connection pool reuse, inner-body cancellation/timeout and explicit subscriber releases |
| Caller context / reporting | Supported snapshots copy selected headers and restore only documented keys; each subscriber owns its context and terminal state. Application queues/loggers can intentionally retain their copies | Gated scheduler/executor handoff, reversed completion order, case-insensitive access, header/idempotency precedence and terminal-once callbacks |
| Work reservation / shared load | Entered preparation and cancellation frames retain admission until unwound. The shared source has separate lifecycle evidence from caller deadlines | Blocked cleanup gates, zero remaining reservations/tokens, detached leader/waiter evidence and rejected late publication |
| Refresh / cache manager | Access-triggered refresh has a bounded deadline/hard expiry and manager-owned cancellation; eviction invalidates publication without promising cancellation of independently caller-owned foreground work | Controlled clock, source cancellation, capacity/entry counters and close/late-terminal reconciliation |

Source review: [invocation/body ownership][handler], [work ownership][admission],
[shared loads and refresh][manager], [caller snapshots][snapshot] and
[subscription reporting][reporting]. Required ownership work remains; no holder
pooling, suppression of callbacks, body inspection shortcut or new global context
propagation was introduced.

## New and Strengthened Controls

The [upload suite][upload-tests] adds ten cases:

- Eight combinations of direct pooled buffer, InputStream, Reader and channel with
  pre-dispatch auth failure or cancellation. The auth publisher is gated; tests
  observe attachment before termination and await both auth and caller terminal
  acknowledgements. They assert no server request, one caller terminal, one close
  or zero remaining native buffer references, including repeated cancellation.
- Two cases reuse the same cold call with an immutable String or an explicitly
  replayable `Flux.defer` body. One subscription and then two zipped subscriptions
  produce three identical loopback requests; the publisher body is subscribed
  exactly three times, never during assembly. The one-connection pool may serialize
  transport; this is not a claim of overlapping socket writes.

Three existing [work-ownership cases][work-tests] now use a real pooled
NettyDataBuffer instead of an integer standing in for a resource. Gates separate
onNext from completion and cancellation cleanup. A successful value retains one
reference until the caller releases it; buffered and late-after-cancel values
invoke `doOnDiscard` exactly once and reach reference count zero. The work slot
cannot be reused while cancellation cleanup is blocked. This tests the internal
ownership guard, not permission to cache DataBuffer responses.

No fixed sleep or forced collection was added. Existing transport tests retain
bounded polling and timed traffic fixtures; they corroborate unchanged integration,
not a universal deterministic race proof. The new discard/pre-dispatch controls,
existing admission/refresh gates and handoff cleanup acknowledgements provide
the deterministic terminal evidence.

## Fresh Regression Matrix

All listed classes were freshly run with `-XX:+DisableExplicitGC`:

| Classes | Boundary | Cases |
|---|---|---:|
| `StreamingUploadOwnershipTest`, `MultipartWireOwnershipContractTest` | Cold/replayable uploads, raw shapes, HTTP/1.1 and h2c framing, backpressure, pre-write cancellation, peer reset, reopened resources and auth/retry/redirect replay | 24 + 7 |
| `TransportResourceOwnershipStressTest`, `ErrorBodyCaptureTest`, `Priority7HousekeepingTest` | Success/empty/void/error responses, consumed-buffer release, mapper fallback, decode-failure attribution, bounded pool recovery and disposal | 5 + 9 + 14 |
| `CacheWorkOwnershipContractTest`, `CacheCallerAdmissionContractTest`, `CacheRefreshAdmissionContractTest` | Pooled discard, reservation/token ownership, guarded sync/async continuations, lookup races, refresh deadline, eviction and shutdown | 25 + 44 + 20 |
| `BoundedLocalResponseCacheContractTest`, `SemanticReadSingleFlightRefreshContractTest` | Independent load/caller lifetimes, both leader/waiter deadlines, last-member cancellation, same-JSON-call recovery after serialization failure, refresh and late-publication rejection | 51 + 6 |
| `AsyncHandoffOwnershipContractTest`, `ExplicitAsyncHandoffContractTest`, `InboundContextCompositionContractTest` | Supported handoff, application-held copies, tenant/auth/context isolation, out-of-order terminals, hits and hidden refresh | 7 + 13 + 22 |
| `RequestContextHeaderAccessTest`, `RequestContextSnapshotTest`, `RequestContextContributorTest` | Named case-insensitive access, multiplicity, copied immutable header lists, supported restore keys and contributor ordering | 39 + 9 + 4 |
| `SubscriptionLocalReportingStateTest`, `SubscriptionReportingStateTest` | Per-subscriber Mono/Flux/envelope state, terminal freezing, empty/cancel boundaries and final request evidence | 5 + 4 |
| `ReactiveHttpClientTimeoutTerminalStateContractTest`, `LogicalCallTimeoutBudgetContractTest` | Response-body attribution, streaming envelope versus inner body and logical deadline ownership | 11 + 11 |
| `DefaultPathCostOwnershipTest`, `RetryRedirectAuthReplayCompositionContractTest`, `ResilienceOperatorCompositionContractTest` | Default/off/no-registry consumers, explicit selection, attempts/dispatches and shared composition controls | 15 + 9 + 12 |
| **Focused total** | **23 classes; zero failures, errors or skips** | **366** |

The three strengthened discard cases, ten new upload cases and existing
out-of-order handoff case also pass five separate targeted runs: 14 cases per run,
70 executions. These overlap the matrix and are not additional distinct tests.

## Applicability and Limits

- **Production changes N/A:** no accepted body/context/terminal optimization
  remains after P6. C002 stays deferred. The above are retained safety controls,
  not evidence of a new leak fix or performance improvement.
- **One-shot replay N/A:** a consumed direct buffer, stream, reader or channel is
  not made replayable. Applications must provide a replayable publisher/reopening
  resource when retrying or resubscribing, per the
  [request-body matrix](../../docs/11-streaming.md#request-body-repeatability-matrix).
  Arbitrary mutable-body concurrency is not newly supported.
- **Serialization boundary:** raw/resource bodies bypass JSON serialization;
  a cache-selected JSON preparation failure is verified to dispatch nothing and
  permit retry of that identical call. This is not a serialization-failure claim
  for already-consumed raw stream data. Error-decoder wrapping and pooled error
  capture have separate tests; no assertion covers arbitrary custom codec cleanup.
- **Collection claims N/A:** ordinary tests observe explicit release, reference
  counts, cleared owners and terminal acknowledgements, not actual heap collection.
  Application-retained envelopes/log records remain application-owned. No RSS
  reduction or absence of all strong references is inferred. No new reachability
  result is claimed; actual collection claims require the existing isolated
  `v31-handoff-reachability` / `v32-cache-reachability` JVM lanes, not System.gc in
  the regular suite.
- **Other lanes remain open:** this is neither an assembled-consumer/AOT/native
  run nor final strict API/packaging or matched performance evidence. Priorities
  8-12 remain open. No new JMH run; the enabled-only allocation flag remains
  unresolved. No release or review-only closure is selected.

## Verification and Reproduction

Oracle Java 21.0.8, Maven 3.9.9, Boot 4.0.0. The initial focused run passes 11 cases.
The first broader attempt fails test compilation because an AssertJ tuple assertion
selected the Iterable overload; no test executed in that attempt. Its accidentally
copied older XML files/totals are explicitly quarantined as stale, not counted.
The collector now rejects reports older than its command start. The corrected
final ownership run passes 366 cases; documentation/archive/readiness passes 86
and V34 Python evidence guards pass 14. Final Java total: **452 distinct cases**.
All final tests have zero failures/errors/skips; repeat attempts are retained.

Evidence: `target/release-evidence/v34/priority7/`. Commands/exits, source/patch,
exact-class XML reports, failure classification, repeated runs and final readiness
are retained. P3-P6 inventories are revalidated without modifying historical
records; production, benchmark, dependency and coordinate inputs are unchanged.
Generated readiness remains V34 active, release lane/scope unselected and
`plannedFinalVersion` null. The checklist contains the final inventory hash and
is excluded from final source-copy sealing as the external integrity index.

```bash
export JAVA_HOME=/usr/lib/jvm/jdk-21.0.8-oracle-x64
export PATH="$JAVA_HOME/bin:$PATH"
export MAVEN_OPTS='-Xmx512m -XX:ActiveProcessorCount=2'
mvn -B -ntp -s .mvn/maven-central-settings.xml -pl reactive-http-client-starter \
  -Dtest=StreamingUploadOwnershipTest,MultipartWireOwnershipContractTest,TransportResourceOwnershipStressTest,ErrorBodyCaptureTest,Priority7HousekeepingTest,CacheWorkOwnershipContractTest,CacheCallerAdmissionContractTest,CacheRefreshAdmissionContractTest,BoundedLocalResponseCacheContractTest,AsyncHandoffOwnershipContractTest,ExplicitAsyncHandoffContractTest,InboundContextCompositionContractTest,RequestContextHeaderAccessTest,RequestContextSnapshotTest,RequestContextContributorTest,SubscriptionLocalReportingStateTest,SubscriptionReportingStateTest,ReactiveHttpClientTimeoutTerminalStateContractTest,LogicalCallTimeoutBudgetContractTest,DefaultPathCostOwnershipTest,RetryRedirectAuthReplayCompositionContractTest,ResilienceOperatorCompositionContractTest,SemanticReadSingleFlightRefreshContractTest \
  -DargLine='-XX:+DisableExplicitGC -XX:ActiveProcessorCount=2' test
mvn -B -ntp -s .mvn/maven-central-settings.xml -pl reactive-http-client-starter \
  -Dtest=DocumentationReleaseArtifactTest \
  -DargLine='-XX:+DisableExplicitGC -XX:ActiveProcessorCount=2' test
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s scripts -p 'test_*v34*.py' -v
```

Recorded Maven commands select the existing isolated repository
`target/v33-native-runs/native-g0ynw95x/repository`; exact argument arrays are in
the bundle. Reproduction should use new output directories and keep failed runs.

[handler]: ../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/core/ReactiveClientInvocationHandler.java
[admission]: ../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/core/CacheWorkAdmission.java
[manager]: ../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/core/LocalResponseCacheManager.java
[snapshot]: ../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/core/RequestContextSnapshot.java
[reporting]: ../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/core/SubscriptionReportingState.java
[upload-tests]: ../../reactive-http-client-starter/src/test/java/io/github/huynhngochuyhoang/httpstarter/core/StreamingUploadOwnershipTest.java
[work-tests]: ../../reactive-http-client-starter/src/test/java/io/github/huynhngochuyhoang/httpstarter/core/CacheWorkOwnershipContractTest.java
