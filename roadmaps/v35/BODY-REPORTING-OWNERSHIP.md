# V35 Body and Reporting-State Ownership

> **Plan frozen:** 2026-10-08, before candidate edits or measurements
> **Workstream:** V34-C002; Priority 5
> **Authorization:** Priority 3.3 null-body holder experiment only
> **Release scope:** unselected

Starting clean source: `6198a986decd35028501f8c76a7a834ecbd0d459`.
This executes [Priority 5](CHECKLIST.md) under [FIX-DECISION.md](FIX-DECISION.md#v34-c002).
C001 remains rolled back. No reporting-state shortcut or arbitrary non-owning-body
classification is authorized.

## Frozen Candidate and Witnesses

Only omit `new RequestBodyOwnership(resolved.body())` when the body is null,
and make the two private termination helpers accept that absence. No owner pool,
new field, changed public API or altered non-null/one-shot ownership. Keep every
existing `usesSubscriptionState()` condition, including enabled-only resilience.

Inventory all body shapes and ordinary/shared/refresh state consumers before
closing the priority. Extend untimed constructor witnesses for absent bodies and
retain non-null ownership, cancellation, discard, terminal-once and concurrent
subscription controls. Ordinary tests disable explicit GC; explicit close/release
acknowledgements are not heap-collection evidence. No retained-memory/RSS claim is
selected, so controlled reachability is required only if a later claim needs it.

## Frozen Measurement and Stop Rules

Reuse P4's saved baseline JAR, whose 310 starter classes match the restored source,
and keep the existing V34 harness unchanged. Audit 39 harness files, 120
non-starter dependencies, ordered classpath and shaded entries; only handler
implementation classes may change. Keep source, candidate diff, JAR/VM hashes,
exact commands/exits, host observations and raw samples including failures.

Before candidate edits collect two baseline JFR diagnostic forks: AUTO_NO_REGISTRY
GET warm publisher for invocation ownership, AUTO_REGISTRY GET warm subscription
for reporting state. Use stack depth 128, five one-second warmups/measures, GC and
JFR profilers. Export only `jdk.ObjectAllocationSample`, keeping private originals
outside the evidence bundle. Sampled sites establish ownership, not exact B/op or
proof that an unsampled object is eliminated. Diagnostics are never scored benefit.

Scored selection: **48 rows**, two forks each in both artifact orders:

- 24 warm publisher/subscription rows: all six profiles x GET/TARGET.
- 24 loopback rows: MINIMAL/AUTO_NO_REGISTRY/AUTO_REGISTRY x
  GET/TARGET/STRING/JSON/ENTITY/EMPTY/ERROR4/ERROR5. STRING/JSON are non-null controls.

Sequence: baseline warm, baseline loopback, candidate warm, candidate loopback;
candidate warm, candidate loopback, baseline warm, baseline loopback.
Total **384 scored forks**, plus two diagnostic forks. Java 21.0.8/JMH 1.37,
avgt/ns, one thread, five one-second warmups and measures, GC profiler, fixed
512 MiB heap, ActiveProcessorCount=2 and WARN logging. Each stage has a 25-minute
bound. Refuse launch below 2 GiB available RAM. No competing agent builds/tests
during scoring; namespace snapshots do not prove a dedicated host.

One candidate, no refinement or result-selected rerun. Require repeatable
attributed production-path benefit in both orders, unchanged ownership/terminal
semantics and no unexplained adverse control. Retain >20% latency and
>max(32 B/op, 5%) allocation review triggers; they are not acceptable regression
budgets. P2/P4 compiler-sensitive modes cannot be assigned to holder removal.
Unattributed/no-benefit or changed cleanup/retention means rollback and a reasoned
no-change assessment, not broader state reuse. Incomplete measurements remain
incomplete. Full 60-row/API/native/assembled acceptance remains later work.

## Reproduction

With the baseline Java/Maven environment and writable repository, use a new output
directory. Saved P4 baseline artifacts are prerequisites, not tracked downloads.

```bash
OUT="$PWD/target/release-evidence/v35/priority5"
python3 -B scripts/investigate-v35-body-ownership.py attribution --output "$OUT"
# Run baseline controls, apply the bounded candidate, build and audit candidate/.
python3 -B scripts/investigate-v35-body-ownership.py score --output "$OUT"
python3 -B scripts/investigate-v35-body-ownership.py review --output "$OUT"
```

Final execution, disposition and verification will be appended, not substituted
for this frozen plan. V1-V34 and earlier sealed V35 evidence remain unchanged.

## Results and Disposition

Completed 2026-10-09: **Resolved without production change** for V34-C002.
The null-body candidate passed the ownership regressions but was **rolled back**
after failing the frozen cost gate. This is **not a performance pass**. No
reporting-state removal, non-null ownership shortcut or release is selected.
Four implementation workstreams remain open: C003, both C004 rows and C005.

All eight stages completed with exit zero: **384 scored forks**, 48 matched rows
per artifact/order, plus **two separate diagnostic forks**. Scoring completed
during the session interruption; resuming did not rerun a stage. There were no
scored retries, discarded forks, refinements or relaxed thresholds. Exact
commands, exits, timestamps, input hashes, raw samples/intervals and host snapshots
remain under `target/release-evidence/v35/priority5/`. The pre-results plan is
preserved separately as `frozen-plan.md`.

### Attribution

The baseline AUTO_NO_REGISTRY/GET warm-publisher recording contains **226 sampled
RequestBodyOwnership allocations** beneath `invokeResolved()`. This confirms an
actual allocation site rather than merely counting source constructors. It does
not establish a fixed allocation count per operation or that the nested atomic
guard escapes in every JVM/profile. In the AUTO_REGISTRY warm-subscription
recording, samples include **15 SubscriptionReportingState** objects, 16
constructor-owned AtomicReferences, two AtomicBooleans, one AtomicInteger and
separate attempt/terminal projections. These identify a different, required
subscription lifetime; they are not a mandate to remove reporting state.

The candidate removes the unused holder/guard construction for null bodies only.
The MINIMAL warm-publisher comparison repeats **832 -> 792 B/op** for GET and
**1992 -> 1952 B/op** for TARGET in both orders. That local 40 B/op result is
consistent with the narrow source change and baseline allocation attribution.
It is not proof of a universal saving, leak fix, lower retained heap or RSS.
JFR includes warmup and sampling, not exact allocation accounting; diagnostic
timings are not scores. Only `jdk.ObjectAllocationSample` is exported, with
private originals outside the bundle.

### Matched Cost and Rejection

Rounded warm-publisher mean B/op; the full two-fork/five-measurement samples and
JMH intervals are retained in `scored/*/results.json` and `review/review.json`.

| GET profile | Forward baseline -> candidate | Reverse baseline -> candidate |
| --- | --- | --- |
| MINIMAL | 832 -> 792 | 832 -> 792 |
| AUTO_NO_REGISTRY | 960 -> 792 | 1088 -> 920 |
| AUTO_REGISTRY | 1872 -> 1704 | 1744 -> 1704 |
| RESILIENCE_ENABLED_ONLY | 1392 -> 1224 | 1136 -> 1352 |
| OBSERVER | 1744 -> 1832 | 1744 -> 1704 |
| HOOK | 1744 -> 1832 | 1744 -> 1832 |

Forward review: **three latency flags and two allocation flags**. The allocation
flags are HOOK/GET and OBSERVER/GET warm publisher; each candidate splits about
1960/1704 B/op versus baseline 1744/1744, giving a mean increase of 88 B/op.
Reverse review: **zero latency flags and two allocation flags**, HOOK/GET again
and RESILIENCE_ENABLED_ONLY/GET (1136/1136 baseline, 1352/1352 candidate).
The unchanged allocation trigger is `>max(32 B/op, 5%)`.

The three forward latency flags are loopback AUTO_NO_REGISTRY EMPTY
(158682 -> 229825 ns/op), AUTO_NO_REGISTRY ENTITY (157827 -> 257968), and MINIMAL
GET (87042 -> 123456). Their JMH intervals overlap substantially; no reverse
latency mean crosses the unchanged 20% trigger. This does not establish a
universal slowdown, but neither is it a clean production-path acceptance result.
STRING/JSON non-null controls have no threshold flags in either order; their
varying whole-call allocations cannot be credited to a null-body optimization.
All TARGET, warm-subscription, response/entity/empty/error and non-null control
rows remain in the review, including contrary results.

P2 diagnosed the saved enabled-only Selection compiler mode, not these candidate
forks. The repeated 256-byte separations are consistent with that sensitivity,
but no candidate compiler trace isolates their cause. Do not subtract 256 bytes,
select only favorable forks, or claim every mean reduction is holder removal.
Reporting state is subscription-local; a warm-resubscription difference is not
evidence of skipping its required state. Host snapshots likewise do not prove
exclusive machine use or a causal explanation for timing noise.

The acceptance gate therefore fails despite demonstrated local allocation savings
and green semantics. The approved no-change route retains the simple existing
holder until a separately approved experiment can establish stable end-to-end
benefit without unexplained adverse controls. The broader C002 audit below also
establishes which body/state work is intentional and why pooling, arbitrary DTO
classification, one-shot replay changes and suppressing enabled-only state are
not safe alternatives. This is not merely V34's deferral or a failed score without
ownership investigation; it also does not claim null holders are indispensable
or that no future improvement is possible.

### Artifact Identity

Baseline JAR SHA-256:
`f30482de11e8ccefa8980beb29f4074a9de011683cb8cee37d0a84925b8239db`.
Candidate JAR SHA-256:
`af60aa58e8c00e59dc27ba8550a58ce220fccd482962de5962ccc2e3f2516544`.
The audit matches **39 harness source files**, **120 non-starter dependencies**
and ordered classpath. Only `ReactiveClientInvocationHandler.class` differs;
all other shaded entries match. Baseline is the saved source-reconciled artifact,
not a new Central download. Final production source equals the starting commit;
candidate binaries, source and patch are labeled rejected evidence.

The [rejected production patch](c002-rejected-candidate.patch) is tracked for
reproduction, not installed in source. Use an isolated experiment checkout of
the starting source and the same Java/Maven environment, collect baseline
attribution first, then:

```bash
git apply --check --unidiff-zero roadmaps/v35/c002-rejected-candidate.patch
git apply --unidiff-zero roadmaps/v35/c002-rejected-candidate.patch
mvn -B -ntp -s .mvn/maven-central-settings.xml -pl reactive-http-client-starter -am -DskipTests install
mvn -B -ntp -s .mvn/maven-central-settings.xml -f reactive-http-client-benchmarks/pom.xml clean package \
  -Dtest=V34WorkloadContractTest '-DargLine=-XX:+DisableExplicitGC -XX:ActiveProcessorCount=2'
```

Preserve the shaded JAR, `dependency:build-classpath`, `dependency:tree` and
`help:effective-pom` outputs in `candidate/`, then run
`scripts/verify-v34-benchmark-inputs.py record <candidate-directory> 4.5.0-SNAPSHOT`
before the scored audit. Exact resolved commands are in `candidate/commands.json`;
the saved baseline bundle is an explicit prerequisite, not present in a clean
clone. Use a fresh output directory and keep failures. The candidate's no-owner
assertion and source diff are preserved in `candidate/source.patch`; the retained
regular-suite test instead characterizes the restored invocation allocation.

No public surface, dependency, default or version changes. No fresh native,
strict API, assembled consumer, full 60-row acceptance or collection pass is
claimed. The final checklist records test totals and the sealed integrity anchor.

## Body and State Ownership Review

Source owners: [ReactiveClientInvocationHandler](../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/core/ReactiveClientInvocationHandler.java)
`invokeResolved`,
`statefulRequestHeadersSpec`, `statelessRequestHeadersSpec`,
`buildRequestHeadersSpec`, `requestFromDataBuffers`, `requestFromPublisher`,
`serializeRequestBodyForAuth`, `buildMultipartBody`, `RequestBodyOwnership`;
[SubscriptionReportingState](../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/core/SubscriptionReportingState.java)
and [LocalResponseCacheManager](../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/core/LocalResponseCacheManager.java).

| Input/owner | Assembly, subscription and release contract | Candidate boundary |
| --- | --- | --- |
| No body / explicit null @Body | Resolved body is null; no writer body/content-type branch, requiresCleanup false. Holder and atomic guard were constructed during invokeResolved, not each resubscription | Only this unused holder may be absent; Mono and Flux termination helpers must accept null |
| String, byte[], scalar, DTO | Non-null invocation input remains captured. Ordinary mutable inputs are not deep-frozen; JSON/auth serialization and replay follow their existing cached/prepared boundaries. No close/release contract inferred for arbitrary DTOs | Holder retained even when requiresCleanup is false; no type-based optimization |
| Direct DataBuffer | Before writer subscription, owner releases on logical termination; writer subscription atomically transfers responsibility. Writer/discard path releases transferred buffers | Same owner and atomic guard; consumed buffer is not made replayable |
| InputStream / ReadableByteChannel | Invocation-wide holder; eager source ownership transfers on subscription. Non-closing read adapters keep writer closure from duplicating logical close; writer/logical termination converge on compare-and-set | Same source/guard, including pre-write auth error, timeout and cancel |
| Reader | Incremental encoding runs on boundedElastic; logical/writer termination closes once. Reader content-type/charset and surrogate handling unchanged | Same owner; no pooling or reusable one-shot source |
| Publisher body | Application publisher controls coldness/replayability and per-subscription buffers. DataBuffer element discard/writer ownership applies to inner values; no eager body subscription | Non-null holder retained, but it does not own/close arbitrary publisher contents |
| Resource | WebClient's writer opens/closes resource streams per dispatch; replayable only when the resource can reopen | Holder retained; no claim that every Resource is replayable |
| Multipart | Resolved top-level body may be null, but ordered multipart parts and auth view are built separately from arguments. Multipart writer owns opened resources/inner values | No null top-level holder cleanup was responsible for part resources; ordered parts and replay tests retained |
| Raw streaming response / streaming ResponseEntity | Envelope completion is not inner-body completion; subscriber owns delivered buffers and must release/cancel inner body | No response/writer changes |

The same cold publisher may retain its invocation arguments, body and selected
observers until the application drops it. Removing an unused null holder does not
claim shorter retention of those real inputs. There is no new global owner/cache,
body-state pool or collection assertion. Repeated or concurrent subscriptions to
one-shot inputs are not newly supported; replayable String and publisher controls
verify supported repeated calls.
Before subscription (including a synchronous assembly/provider failure), eager
inputs remain application-owned. A never-subscribed publisher does not release
them automatically; see the canonical request ownership contract in
[streaming guide](../../docs/11-streaming.md#wire-framing-and-request-ownership).
Construction counts must not be confused with that transfer.

| State consumer | Preserved reason/lifetime |
| --- | --- |
| Plain default/no-registry | Stateless path only when every usesSubscriptionState condition is false; subscriber context is still read for conventional idempotency |
| Application logger/observer/hook | Each subscription receives its own state and immutable terminal projection, including attempts, final request and response facts; one subscriber cannot overwrite another |
| Auth | Stateful preparation carries attempt evidence and hidden 401 reset/final request observation; serialization may remain invocation-cached without sharing mutable reporting state |
| Enabled-only resilience and selected operators | The enabled flag deliberately selects state even when zero operators are active. Retry attempts share that subscriber's logical-call state, not another caller's state |
| Generated idempotency | Subscription-local generated key is stable across that call's retries; explicit/default/context precedence remains unchanged |
| Logical deadline | State registers with the subscriber's deadline for attempt/failure-stage attribution; no new timeout or earlier unattributed timer |
| Cache ordinary caller | Pre-lookup preparation, authorization, lookup and terminal outcome use caller state, with fresh local hits/waiters distinct from transport work |
| Shared miss load | Separate load state survives an individual caller detach; attempt evidence is frozen/detached before terminal caller mutation. No leader-state reuse |
| Hidden refresh | Manager-owned load state and bounded deadline/hard expiry; ordinary caller hooks/log/observer are suppressed, cache terminal metrics remain separate |
| Health/metrics | Cache-served and admission-only events remain excluded from downstream request health samples while allowed custom terminal surfaces retain outcomes |

## Regression Witnesses

Baseline: 425 cases across 25 classes. Candidate: 431 after six null-owner
constructor witnesses. The new witness failed on baseline with six assertion
failures, then passed on the candidate; it does not time code or request GC.
Mockito constructor interception is confined to these structural null-body tests,
not the scored artifact or real-resource cleanup controls.
After rollback, the six retained cases assert two invocation holders for two
assembled calls and no further holders across four subscriptions, alongside
zero stateless or four distinct stateful reporting states. This characterizes
the retained behavior; it is not an assertion that future null-holder removal
is forbidden. The candidate's zero-holder assertion remains in its saved diff.

- `StreamingUploadOwnershipTest` (24) and `MultipartWireOwnershipContractTest`
  (7): coldness, one transport attempt per subscription, bounded demand, peer
  disconnect, stream/reader/channel closure, pooled buffer counts, unopened
  resources, replayable bodies and retry/redirect/401 resource reopening.
- `CacheWorkOwnershipContractTest` (25): a pooled value is gated between onNext
  and completion; cancellation with a buffered value or a value arriving later
  invokes discard exactly once and reaches reference count zero. Blocking cleanup
  keeps admission held until acknowledgement; no timing-based race assumption.
- `CacheCallerAdmissionContractTest` (44), `CacheRefreshAdmissionContractTest`
  (20), `BoundedLocalResponseCacheContractTest` (51) and
  `SemanticReadSingleFlightRefreshContractTest` (6): rejection before preparation,
  empty/error/serialization recovery on the identical JSON call, source/caller
  deadlines, last-waiter detach, refresh cancellation/expiry/close, late publication.
- `SubscriptionLocalReportingStateTest` (5), `SubscriptionReportingStateTest`
  (4), timeout/budget suites (11 each): independent concurrent Mono/Flux/envelope
  calls, empty/cancel terminals, attempt/final-request facts and response-body
  timeout attribution. `IdempotencyKeySupportTest` adds generated-key per-caller
  and retry stability in final verification, not a new performance row.
- Context/header/contributor and async-handoff suites (94 combined): snapshots,
  header/tenant isolation, application-retained envelopes and out-of-order terminals.
- `LocalResponseCacheObservabilityTest` (17), `CacheWorkTelemetryContractTest`
  (21): cache outcomes without a registry, once-only terminal refresh accounting,
  meter lifecycle and cache/admission exclusions from downstream health.
- Transport resource/error capture/housekeeping (5/9/14) and retry/redirect/auth
  and resilience composition (9/12): decode failures, response release and pool
  recovery, actual dispatch evidence, attempts and replay boundaries.

All counts must be taken from retained XML, not inferred from annotations. Runs
overlap and are not summed. Existing bounded transport polling supports integration;
explicit gates and cleanup acknowledgements support race claims. No
absence-of-all-retention, RSS reduction or heap collection is inferred.
Controlled reachability is N/A
for this local allocation experiment rather than an unrun collection pass.
