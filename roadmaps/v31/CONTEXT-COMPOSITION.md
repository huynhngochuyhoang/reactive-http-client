# V31 Cache, Auth, Retry, and Terminal Context Composition

## Scope and Provenance

Priority 6 is a characterization/test-only change against reachable base commit
`7b1344b42abd10d8331bdf5da80575a8d3c948fb`, plus the working-tree test and
documentation changes recorded with the evidence. Development remains
`4.4.0-SNAPSHOT`; published/API/consumer/benchmark baselines remain `4.3.0`.
No production fix, public API, telemetry schema, configuration, dependency or
automatic context owner was introduced.

The new [starter composition suite](../../reactive-http-client-starter/src/test/java/io/github/huynhngochuyhoang/httpstarter/core/InboundContextCompositionContractTest.java)
has 22 executed cases. The [OTel observer suite](../../reactive-http-client-otel/src/test/java/io/github/huynhngochuyhoang/httpstarter/otel/OpenTelemetryHttpClientObserverTest.java)
adds six cases, one per existing cache-outcome enum. These use synthetic bounded
fixture data, not real credentials, tenants or mesh observations.

## Caller and Work Ownership

| Boundary | Evidence and observed contract |
|---|---|
| Uncached resubscription | One cold publisher restored under two distinct snapshots produces two independent records; explicit outbound correlation/idempotency headers win, otherwise restored correlation and explicitly handed-off idempotency apply |
| Fresh hit | Current caller authorization runs; another caller's inbound headers are not restored from the entry; terminal attempts/status/URL are zero/absent |
| Stale hit | Current caller completes with its own snapshot; its pre-lookup auth result is reused for the initial hidden refresh dispatch |
| Hidden refresh | A source gate proves refresh remains active after stale completion; the trigger's correlation is used by that load; finishing it does not append another visible caller terminal record |
| Same-key sharing | Attachment is proved by the manager's two-member flight state before detaching the first caller; only the explicitly selected tenant/idempotency header variants partition reuse |
| Leader cancellation/deadline | Source remains subscribed while the waiter is interested; a subsequent real Resilience4j Retry uses the load's original context, not the waiter's; caller terminal evidence is frozen |
| Waiter completion | Independent snapshot, COALESCED_WAITER, zero attempts and no transport URL/status/headers; no later retry callback is delivered to an already terminal caller |
| Changed tenant | Auth-selected final header creates another cache identity and dispatches; merely reading unselected caller/correlation fields does not partition the cache |
| Changed auth on 401 | First request and hidden replay use different final tenant headers; replay succeeds for its caller but is not published under the old lookup key |

Named access is only a read helper. The fixture explicitly validates its allowed
scope values in the auth provider and explicitly selects `X-Tenant` and
`Idempotency-Key` as variants. Missing, redacted, ambiguous and malformed values
are rejected; a redaction marker is neither proof of identity nor authorization.
Inbound fields are not automatically forwarded.

## Replay and Terminal Evidence

- Selected Retry plus 401 invalidation yields three dispatches but two outer
  attempts per uncached subscription. Repeating that cold call under a second
  snapshot preserves each caller's prepared idempotency key at start, retry and
  terminal hooks. Earlier 401/503 response-header sentinels are absent at success.
- The real IPv4 loopback redirect fixture observes exactly the initial GET and
  its 307 target. The next authorized caller hits the cache and sends neither.
  Correlation remains on both transport requests. The starter's existing
  terminal URL remains the observed WebClient request URL, not an invented
  redirect-hop URL.
- A body sink is actually subscribed after response headers, then the logical
  deadline fires. All three terminal surfaces retain that caller's error,
  status, response headers and RESPONSE_BODY stage; source cancellation and
  zero sink subscribers are acknowledged.
- Warm-hit authorization denial, malformed named values and invalid auth headers
  produce one error record on each enabled observer/lifecycle/log surface, no
  attempt/dispatch/response evidence, and no cache admission leak.
- Delayed auth is cancelled by explicit cancellation and logical timeout. The
  test waits for source teardown before emitting a late auth result, advancing
  virtual time and checking no dispatch. A subsequent valid call still works.

## Reporting Setup Boundary

A non-Map raw value under the public `inboundHeaders` key retains the existing
`ClassCastException` behavior during reporting setup. That occurs before the
inner publisher installs terminal callbacks: there are **zero**, not one,
observer/lifecycle/log terminal records, zero auth subscriptions and zero
dispatches. Three consecutive subscriptions through `retry(2)` do not retain
capacity. This is covered for uncached Mono, uncached Flux, cached calls without
work selection, and cached calls with work selection. A replacement subscription
then acquires normally and its source cancellation is acknowledged.

Malformed values inside a Map fail later when the auth provider uses named
access. Those failures are inside the reporting boundary and produce exactly one
terminal record. This work does not sanitize application-written raw maps or
change the legacy bulk accessor into a validated API. Custom loggers still own
their records and must not treat arbitrary raw context as sanitized ingress.

The broader regression includes preparation-frame, asynchronous continuation,
load/refresh ownership and duplicate-finalization tests. Simple future disposal
is not the cleanup proof: the new suite uses source terminal futures, sink
subscriber counts, actual flight attachment and virtual-time deadlines. There
are no sleeps or forced-GC assertions in the new suite.

## Privacy and Evidence

Captured redaction markers and absent fields remain unchanged in caller logs.
The cache meter test allowlists existing tag keys and rejects fixture header,
identity and correlation material in tag values. The OTel test explicitly reads
named context under each cache outcome and passes synthetic headers in a
terminal observer event: the resulting span still has only the existing five
structural attributes and no header names/values or correlation. This is an
observer-boundary test, not a new tracing propagation integration.

Existing diagnostics/support fixture and default-logger regression tests remain
part of the verification. No support fields or span attributes were added.
Manual Istio/Envoy testing is not claimed; only the redirect uses a real socket,
while the other composition cases use gated WebClient exchange functions and
the production handler/cache/auth/resilience implementations.

Verification completed on 2026-09-12: 545 tests across 32 starter classes,
110 additional composition executions across five single-CPU forks with explicit
GC disabled, and 56 tests across four OTel suites. All passed without skips.
Maven 3.9.9 and GraalVM JDK 25.0.3 used Java target 21 and the repository's
Central settings. Exact commands, fresh XML reports, source copies/SHA-256,
toolchain and working-tree provenance are under
`target/release-evidence/v31/priority6/verification/`. Source checksum and
whitespace checks passed before this documentation-only completion update.
Earlier fixture failures remain separate from final evidence.
