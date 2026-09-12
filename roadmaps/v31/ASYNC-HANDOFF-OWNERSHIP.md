# V31 Explicit Async Handoff and Ownership

## Scope and disposition

Recorded on 2026-09-12 against reachable base commit
`89b8785e724562b40c23e55453e165bc57443abb` plus the tests and documentation in
this dirty working tree. Reactor `4.4.0-SNAPSHOT`; published/API baseline
`4.3.0`. This is scoped contract/retention evidence, not clean-commit native,
performance or release evidence.

No production defect was reproduced and no runtime code, public API, context
field, forwarding default, contributor selection or filter order was changed.
An independent subscription not seeing its emitter context is the documented
boundary; restoring an empty snapshot into a populated worker is a non-clearing
restore, not evidence that the starter retained another caller.

## Subscription and isolation matrix

[ExplicitAsyncHandoffContractTest](../../reactive-http-client-starter/src/test/java/io/github/huynhngochuyhoang/httpstarter/core/ExplicitAsyncHandoffContractTest.java)
has 13 executed cases:

| Cases | Boundary | Required observation |
|---|---|---|
| 3 | Composed publishOn, subscribeOn, both | One cold publisher reads three distinct subscriber contexts, including empty; nested reads agree across the actual worker switch |
| 2 | Independent executor subscription | No automatic emitter context; explicit snapshot restores only correlation/headers; subsequent bare task sees no prior values |
| 2 | Independent sink subscriber | Without restoration it sees its own worker context; with restoration only present snapshot fields replace it |
| 1 | Twelve overlapping envelopes on one shared worker | All consumers attach behind explicit gates before any completion; reverse release produces reverse completion, with each caller's own values and empty callers remaining empty |
| 4 | Partial/empty snapshots | Each present-field combination replaces only that target field; absent values do not clear it; isolated targets prevent reuse |
| 1 | Contributor and extra-state handling | Capture and restore sort by order, then key; later writes win; defaults exclude idempotency/custom context and explicit application restoration preserves them |

The concurrent fixture removes caller-specific keys on each inner subscription,
without discarding the whole worker context. It admits all twelve envelope
consumers before releasing any gate; no sleep or server latency determines
attachment. Every post-gate read is scheduled on the same named worker.
Independent sink/executor tests intentionally start separate subscriptions.

A snapshot is captured inside `deferContextual`, never eagerly when assembling
the cold publisher. Request-context isolation is not an authorization policy or
a cache partition. Priority 6 owns the cache/auth/retry/refresh composition matrix.

## Precedence and terminal ownership

[AsyncHandoffOwnershipContractTest](../../reactive-http-client-starter/src/test/java/io/github/huynhngochuyhoang/httpstarter/core/AsyncHandoffOwnershipContractTest.java)
has seven executed cases:

- Two proxy cases preserve explicit outbound correlation/idempotency-header
  precedence over restored correlation and separately handed-off idempotency.
  The existing precedence suite supplies MDC/default/generated/retry coverage.
- Four proxy cases exercise successful completion, error, an application timeout
  and explicit cancellation after source attachment. Each asserts one start,
  one terminal hook, one observer event and one exchange log, attempt count,
  header snapshot, shared terminal error and terminal classification.
- One copy case retains the snapshot while releasing the original mutable
  header map/list, mock exchange, request, source Reactor context and arbitrary
  context map; all six original objects become weakly unreachable. Later
  source mutation cannot change the snapshot or named read, and map/list
  mutation through the snapshot is rejected.

The proxy fixture uses the real invocation handler, correlation and final-request
observation filters, a live Spring application context and a gated synthetic
WebClient exchange function. It does not claim wire/Netty resource ownership:
Priority 4 supplies wire evidence. Source subscription acknowledgement plus a
worker barrier precedes terminal emission. Source and caller terminal
acknowledgements, counts and zero remaining sink subscribers prove cleanup;
local future cancellation alone is not the proof.

The application timeout uses a fixture-owned virtual scheduler, advanced only
after attachment. It produces a TimeoutException downstream and cancellation in
the starter's terminal surfaces. It is deliberately not classified as the
starter logical-call timeout; that contract is separately regressed and belongs
to the Priority 6 composition work.

WebClient may cancel the one-response envelope publisher after consuming its
ClientResponse. Successful work therefore separately proves one emitted
response, decoded value, caller completion and exactly one source terminal
(complete or cancel); it does not mislabel that envelope cancellation as caller
cancellation.

### Ownership checkpoints

| Owner | While retained | After explicit release |
|---|---|---|
| Application envelope list | Snapshot wrapper and header value remain reachable | Wrapper becomes collectible even while proxy/context remain live |
| Application exchange logger record | Copied inbound headers still retain the selected String after envelope removal | String becomes collectible after clearing the record |
| Starter proxy, context, scheduler | Remain live during both collection checks | No test relies on factory/context close to release the snapshot/value |
| Original exchange/request/context/maps | Not part of the snapshot | Collectible while the copied snapshot is still retained |
| Fixture executors/subscriptions | Explicit test owners | Pending subscriptions cancelled in finally; schedulers disposed, executors shut down and termination acknowledged; application contexts and unused cache manager closed |

Weak references/reference queues are diagnostic reachability checks with bounded
GC retries, not an absolute collection deadline, process-memory measurement or
RSS-reduction SLA. The fixture uses synthetic bounded strings, and retains no
live production exchange or arbitrary context in new helper state. Queues,
custom contributors and user logger/observer implementations remain responsible
for the objects they choose to retain.

## Verification

The final focused command passed 20 cases, zero failures/errors/skips:

```bash
mvn -B -ntp -s .mvn/maven-central-settings.xml -pl reactive-http-client-starter \
  -Dtest=ExplicitAsyncHandoffContractTest,AsyncHandoffOwnershipContractTest test
```

The combined regression passed 288 tests, including 49 documentation tests,
with zero failures/errors/skips. Five separate focused runs passed all 20 cases
each (100 executions). Completion and final documentation verification are
recorded under Priority 5 of [CHECKLIST.md](CHECKLIST.md).
Exact commands, fresh Surefire XML, source
copies/hashes, toolchain and dirty-source provenance are retained under
`target/release-evidence/v31/priority5/`.

Two intermediate failed runs are preserved: the first fixture omitted the
factory's final-request observation filter and assumed envelope completion;
the second expected a wrapped diagnostic error where the original exception is
reported. Fixture corrections did not require a production change.
