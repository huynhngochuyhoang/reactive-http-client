# Reactive HTTP Client - Roadmap V31

> **Status:** active
> **Theme:** reliable inbound-header access and explicit request-context boundaries
> **Published/API baseline:** `4.3.0`
> **Release candidate:** `4.4.0` (not published)
> **Release scope:** additive inbound-header access; [release review](RELEASE-DECISION.md)
> **Execution:** [adopted checklist](CHECKLIST.md); publication and archive gates remain open

The proposal below preserves the adopted scope; its checklist and release review
record delivered behavior and evidence. V31 is the sole active execution roadmap.
V1-V30 remain completed records. The `4.4.0` additive candidate is selected by
Priority 10's scope review, not by its former snapshot coordinate. No public
default changes or publication are claimed.
The [resilience activation proposal](../proposals/OPT_IN_RESILIENCE_ACTIVATION.md)
was adopted by V27 and is not additional V31 work.

## Starting State

V30 shipped optional active cache-work bounds in `4.3.0`. Its
[release review](../v30/RELEASE-DECISION.md) preserves publication, compatibility,
native, ownership and performance evidence. V31 proposes a smaller interoperability
and usability scope, prompted by an application reporting an inbound header
present in `ServerWebExchange` but missing from an exact-name map lookup inside
`Mono.deferContextual`.

The current code explains a plausible failure without proving context loss:

- [InboundHeadersWebFilter](../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/filter/InboundHeadersWebFilter.java)
  matches allow/deny lists case-insensitively, then stores immutable values under
  the names exposed by the server header collection.
- [RequestContext.inboundHeaders](../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/core/RequestContext.java)
  returns that map, or an empty map when the context key is absent. It does not
  supply case-insensitive name lookup.
- [Existing filter tests](../../reactive-http-client-starter/src/test/java/io/github/huynhngochuyhoang/httpstarter/filter/InboundHeadersWebFilterTest.java)
  explicitly require preservation of captured spelling. Converting every key to
  lowercase would change an established public behavior.
- [RequestContextSnapshot](../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/core/RequestContextSnapshot.java)
  and contributors already support explicit in-process capture/restore.
  [Snapshot tests](../../reactive-http-client-starter/src/test/java/io/github/huynhngochuyhoang/httpstarter/core/RequestContextSnapshotTest.java)
  distinguish independent sink subscriptions from ordinary scheduler changes.
- [Mock inbound assertions](../../reactive-http-client-test/src/main/java/io/github/huynhngochuyhoang/httpstarter/test/RecordedExchangeAssertions.java)
  also use exact map keys. The [context guide](../../docs/09-correlation-id.md)
  and [logging guide](../../docs/13-exchange-logging.md) describe the snapshot,
  but callers still have to implement their own safe named lookup.

HTTP field names are case-insensitive; HTTP/2 constructs them in lowercase.
See [RFC 9110 section 5.1](https://www.rfc-editor.org/rfc/rfc9110.html#section-5.1)
and [RFC 9113 section 8.2](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.2).
An ingress, protocol transition, or application can therefore expose spelling
different from the application's literal. This does not establish that Istio
changed this particular request, nor that a sidecar can remove an in-process
Reactor context. Deployment-specific claims require deployment-specific evidence.

## Goals

1. Offer safe, case-insensitive named access without changing the legacy map.
2. Distinguish absence, empty values, multiplicity, redaction, and context-boundary
   mistakes before application parsing or authorization.
3. Preserve filtering, immutable snapshots, explicit handoff and per-subscription
   isolation through the starter's existing composition paths.
4. Provide reproducible HTTP/1.1 and HTTP/2 evidence and useful mesh troubleshooting
   without requiring Kubernetes for normal tests.
5. Keep diagnostics, retention and compatibility requirements proportionate to
   this scope; do not introduce implicit context propagation or header forwarding.

## Non-Goals

- Reconfigure Istio/Envoy, infer mesh trust, or certify every sidecar deployment.
- Rename legacy context keys or normalize the keys of the existing bulk map.
- Automatically forward captured headers, trust identity headers, or deserialize
  arbitrary header JSON inside the starter.
- Add a Spring MVC ingress bridge, a general ThreadLocal/MDC bridge, global Reactor
  hooks, or automatic transfer through queues, executors, or independent subscribe.
- Expand `RequestContextSnapshot` to retain all context, idempotency, credentials,
  bodies, exchange objects or application principals.
- Change cache eligibility, key semantics, work limits, resilience selection,
  timeout defaults, or the release's memory/performance guarantees.
- Add header-name/value meter tags or a public per-request debugging endpoint.

## Proposed Contract

### Add named access, preserve bulk access

Keep `RequestContext.inboundHeaders(ctx)`, string-key aliases, snapshot record
components, captured spelling, and existing map/value immutability contracts.
Add the smallest named-lookup API to `RequestContext`; exact method names and
signatures are frozen before implementation, not by this roadmap.

The proposed surface offers all values and an optional single value. It reads
only the captured snapshot, never the live exchange or an alternate credential
source. Single-value access must not silently select an identity from duplicates.

| Input state | All-values lookup | Single-value lookup |
|---|---|---|
| Context key absent or no matching name | Immutable empty list | Empty optional |
| Matching entry with an empty list | Immutable empty list | Empty optional |
| One captured value, including an empty string | That value unchanged | Present value unchanged |
| Multiple captured values | All values in captured order | Explicit ambiguity failure |
| Several map keys differing only by ASCII case | Collect all matching values, preserving map iteration and per-entry list order | Same multiplicity rule; no exact-case preference |
| Filter-produced redacted entry | Existing redacted marker, never the original value | Same single-value rule; not evidence of an authenticated identity |
| Wrong type under the public context key, or malformed map/list entries | Bounded structural misuse error, not a misleading empty result | Same error contract |

Duplicate values remain duplicate; do not deduplicate, comma-join/split, trim,
parse JSON, or change value casing. A raw application map may have no stable
iteration order: lookup preserves the order it exposes but cannot reconstruct
lost wire order. For singleton business/identity fields the caller must use the
ambiguity check, not rely on unspecified ordering.

Lookup names must be valid nonempty HTTP field-name tokens, with ASCII
case-insensitive matching independent of the default locale. Invalid names and
null required arguments fail without including request values in messages.
Do not substitute underscores for hyphens or normalize distinct field names.
Helpers return defensive immutable values and do not introduce a global index
or retain contexts beyond the call.

A missing value is not malformed JSON. Applications decide whether a header is
required, whether its value is redacted, how to validate its size/schema, and how
to return their own validation response. The starter does not invent a default
principal or suppress a missing required header.

### Capture is not forwarding or authentication

Keep the current allow-list/deny-list rules and defaults. Denied selected headers
remain redacted; outside-allow-list headers remain absent. A normalized lookup
must not resurrect either original value. Capture uses the request visible when
the WebFilter runs, not the original pre-proxy wire spelling.

Manual `withInboundHeaders`, raw context writes, and custom contributors are
application-owned inputs, not proof that the ingress filter sanitized or trusted
them. Document that boundary explicitly; do not promise that arbitrary context
writes receive global filtering. Filter capture and explicit snapshot handoff
must remain immutable without retaining the exchange or its request.

Do not reorder ingress/custom security filters merely to make a header visible.
Characterize filter ordering and document when an upstream request mutation is
captured; a later mutation must not retroactively alter a snapshot.

### Subscription ownership stays explicit

Read context at subscription time. Ordinary composed scheduler transitions do
not establish a new request context; independent sink/queue/callback subscriptions
require an explicit capture/restore boundary. Preserve existing restore semantics:
present snapshot values replace the matching target keys; absent snapshot values
do not clear them. Do not silently change this merge behavior to fix application
reuse; demonstrate isolated target contexts when reusing a worker.

Headers belong to each caller. Concurrent requests, repeat subscriptions, cache
hits, coalesced waiters and detached refresh/load work must not borrow another
caller's snapshot. Existing per-caller authorization and final request-key
isolation still apply; context convenience cannot authorize a cache hit or
implicitly make a header a cache variant.

Malformed starter-key values must not strand cache-work reservations, suppress
required cleanup or emit duplicate terminal records. Any production correction
requires a reproducer and a separately reviewed compatibility decision; this
roadmap does not classify all raw-context misuse as a library defect.

## Priorities

## 1. Post-`4.3.0` Baseline and V31 Scope Integrity

- Verify published parent/module artifacts and assembled consumer from fresh
  Central repositories; retain V30's release/tag and native/benchmark provenance.
- Keep public/API/consumer/benchmark baselines at `4.3.0` and reactor fixtures at
  `4.4.0-SNAPSHOT`; include published V30 rows in baseline benchmark discovery.
- Adopt an execution checklist separately. Keep draft, active execution, scope
  selection and publication distinct in the archive and generated readiness.
- Preserve V1-V30 evidence and the already-adopted resilience proposal unchanged.

## 2. Inbound Header and Context Characterization

- Reproduce mixed/lower/upper-case names using fake bounded values; prove the
  context key and case-variant entry exist when exact `Map.get` fails.
- Separately exercise absent key, outside-allow-list, redaction, empty list,
  empty string, repeated values and independent-subscription loss.
- Cover native server `HttpHeaders`, application-supplied maps and snapshot
  restore. Record observed behavior before proposing a runtime correction.
- Retain the distinction between a verified library defect, documented caller
  misuse, an additive usability gap, and an unverified deployment hypothesis.

## 3. Case-Insensitive Header Access Contract

- Freeze additive method signatures, name validation, absence/error behavior,
  duplicate-case aggregation, single-value ambiguity and immutable returns.
- Test differing value order, duplicate equal values, case aliases, locale,
  malformed manually supplied context, and source mutation after capture.
- Preserve legacy map spelling and exact lookup behavior, record constructor
  compatibility and public string-key aliases.
- Keep validation localized to the new surface unless characterization proves
  an existing path requires a correction; no hidden application parser.

## 4. WebFlux, Protocol, and Filter Boundaries

- Exercise real loopback WebFlux requests over HTTP/1.1 and HTTP/2, with lowercase
  and mixed-case inputs where legal; assert negotiated protocol and captured names.
- Add a deterministic normalizing intermediary fixture only where direct protocol
  tests cannot cover the boundary. A simulated proxy is not an Istio certification.
- Test allow/deny matching, redaction, repeated fields and filter-order mutations.
  Show which request version is captured and prohibit post-capture mutation.
- Document WebFlux registration/replacement and the absence of a servlet ingress
  bridge; WebClient use in an MVC application alone is not inbound capture.
- Optional manual mesh evidence records relevant versions, protocol hops and
  sanitized structural observations, never full request headers or identities.

## 5. Explicit Async Handoff and Caller Isolation

- Cover `deferContextual`, composed `publishOn`/`subscribeOn`, nested publishers,
  and independent sink/executor callbacks with and without explicit snapshots.
- Test concurrent request envelopes and reused workers with deterministic gates,
  including present-value overwrite and absent-value non-clearing semantics.
- Preserve contributor ordering and correlation/idempotency precedence; snapshots
  still carry only correlation ID and inbound headers unless explicitly extended
  in a separate proposal.
- Verify completion, error, timeout and cancellation release starter-owned
  snapshot references; application queues and retained records remain external
  owners. Avoid absolute GC timing or process-RSS assertions.

## 6. Cache, Auth, Retry, and Terminal-State Composition

- Exercise uncached calls, fresh/stale hits, same-key waiters, first-caller
  detachment and hidden refresh with distinct caller snapshots.
- Validate that explicit auth/header variants and finalized request identity
  still isolate responses; a convenience lookup never bypasses authorization.
- Cover retries, auth replay, redirects and deadlines without changing their
  published selection/order or introducing another context-capture owner.
- Test malformed raw context with enabled reporting and work admission: no leaked
  reservation, post-terminal dispatch or duplicate observer/lifecycle/log record.
- Preserve redaction and absence through existing terminal surfaces without
  copying headers into meter tags, tracing attributes or support snapshots.

## 7. Mock, Assembled-Consumer, AOT, and Native Parity

- Provide matching helper behavior in mock recordings/assertions. Preserve
  existing assertion semantics or add explicit alternatives; do not silently
  reinterpret established exact-spelling tests.
- Run an assembled consumer with lowercase inbound fields and safe optional/
  multi-value access, plus an explicit handoff scenario and missing-header case.
- Exercise the new public surface in AOT/native smoke from the application
  context; no reflective header-DTO scanning or mandatory new dependency.
- Preserve application replacement beans, cache-disabled/no-Caffeine consumers
  and optional OTel absence. Record native executable and clean source hashes.

## 8. Documentation and Operations Guidance

- Consolidate named lookup, filtering, handoff and precedence examples in
  [Correlation and Context](../../docs/09-correlation-id.md),
  [Exchange Logging](../../docs/13-exchange-logging.md),
  [Test Helpers](../../docs/14-test-helpers.md) and the production checklist.
- Replace unsafe nullable-map-value `getFirst()` recipes with explicit
  absence/multiplicity handling and application-owned parsing; distinguish
  published `4.3.0` workarounds from candidate-only helpers.
- Add an operations decision path separating exact-case mismatch, filtering,
  wrong chain/filter order, malformed key values and independent subscriptions.
  Do not diagnose every mesh deployment as stripping headers or Reactor context.
- Keep support evidence structural: versions, protocol, capture/read boundary,
  key-present/match-present flags and bounded value counts using fake field
  aliases. Do not export arbitrary names, values, tokens, tenant IDs or payloads.
- Prefer existing diagnostics; new meters or diagnostics fields require a
  demonstrated gap, bounded schema and independent privacy review.

## 9. Compatibility and Targeted Performance Evidence

- Pass strict root and starter-module source/binary comparisons against fresh
  Central `4.3.0`, including request-context and test-helper public surfaces.
- Run full tests, supported dependency rows, generation/package guards and
  focused composition regressions; preserve actual totals and failure reports.
- Measure named lookup/capture/restore at realistic bounded header counts and
  concurrent handoffs. Avoid copying the complete context for one lookup.
- Compare unchanged cache-disabled/cached invocation overhead against `4.3.0`.
  Use smoke only for wiring; leave release-quality comparison manual when needed,
  with clean provenance and no numerical claim from unreviewed measurements.

## 10. Release Scope and Go/No-Go

- Freeze public helpers, mock assertions, compatibility and canonical guidance
  together; update metadata only if a genuine configuration addition is approved.
- Assemble immutable clean-commit evidence for API, consumers, AOT/native,
  lifecycle and targeted benchmarks before selecting release scope.
- Consider `4.4.0` only for additive shipped functionality. A documentation-only
  or defect-only result may instead justify a patch or no-go; the snapshot
  coordinate and this roadmap do not make that decision.
- Reject silent bulk-map casing changes, new implicit propagation, unverified
  mesh claims, or a release depending on an unimplemented contract.
- After a go decision, prepare/sign final artifacts and staged consumers, publish
  the reviewed tag, verify fresh Central artifacts and a published consumer,
  then advance baselines and archive the roadmap.

## Acceptance Criteria

- [ ] A reproducible distinction exists between header-case mismatch, filtered
      values and true context-boundary loss, without blaming an unobserved mesh.
- [ ] Additive named access handles absence and multiplicity explicitly while
      preserving legacy spelling, key aliases and immutable snapshot behavior.
- [ ] Filtering, redaction, explicit handoff and per-caller isolation survive
      protocol and feature composition without implicit forwarding or trust.
- [ ] Mock, consumer, compatibility, native, lifecycle and documentation evidence
      cover the delivered surface; performance claims match reviewed evidence.
- [ ] Scope and release disposition are explicit; publication/archive completion
      is recorded in an adopted execution checklist, not these proposal boxes.

## Open Decisions During Execution

- Final helper names and bounded structural exception contract.
- Whether characterization identifies any production defect beyond the additive
  lookup gap; corrections must have focused regression evidence.
- Whether real mesh reproduction is needed beyond protocol/intermediary tests.
  Environment-dependent verification remains labeled unverified until supplied.
- Minor, patch, documentation-only or no-go disposition after evidence review.
