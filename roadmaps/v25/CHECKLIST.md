# Reactive HTTP Client - Roadmap V25 Execution Checklist

Companion to [`ROADMAP.md`](ROADMAP.md). Execute priorities in order unless a
confirmed correctness or release blocker requires reordering. Check an item only
after implementation and verification evidence is recorded under that priority.
Generated evidence belongs under `target/release-evidence/v25/` unless a
promoted, versioned artifact is explicitly required.

---

## Priority 1 - Post-`3.4.0` Baseline and V25 Scope Integrity

### [x] 1.1 Align development and published-release lanes

- [x] Keep root/module and reactor-only fixture coordinates on
      `3.5.0-SNAPSHOT`.
- [x] Keep public dependency snippets and `latest.published.version` on `3.4.0`.
- [x] Keep API compatibility, published-consumer, and benchmark baselines on
      `3.4.0`.
- [x] Preserve root and module guards that reject a baseline equal to the current
      reactor version.
- [x] Keep V25 as the only active roadmap and preserve completed V1-V24 records.

### [x] 1.2 Prove published `3.4.0` provenance

- [x] Resolve the parent POM plus starter, test-helper, and OTel POM/JAR/source/
      Javadoc artifacts from a previously absent Central-only repository.
- [x] Require Maven Central remote markers and record SHA-256 values for every
      required artifact.
- [x] Run strict root japicmp against published `3.4.0` from a fresh repository.
- [x] Run strict starter-module japicmp against published `3.4.0` from a separate
      fresh repository.
- [x] Run published-baseline fixture tests for local contamination, mixed
      versions, missing attachments, and self-comparison.

### [x] 1.3 Keep generated readiness honest

- [x] Report `3.5.0-SNAPSHOT` as snapshot development and `3.4.0` as the latest
      published/API baseline.
- [x] Keep the final candidate version and promotable benchmark path deferred
      until release preparation.
- [x] Include unresolved manual baseline, compatibility, native, benchmark, and
      publication work in the generated readiness manifest.
- [x] Run focused release-documentation tests and `git diff --check`.

Evidence:

- Verified the reactor and reactor-only fixtures at `3.5.0-SNAPSHOT`, public
  snippets and published baselines at `3.4.0`, and V25 as the sole active roadmap.
- Resolved the published parent plus all starter, test-helper, and OTel
  POM/JAR/sources/Javadoc artifacts from a fresh V25 Maven Central repository;
  recorded 13 SHA-256 entries and Central markers under
  `target/release-evidence/v25/published-baselines/`.
- Passed strict root and starter-module japicmp runs from separate fresh `3.4.0`
  repositories and recorded their provenance in the V25 evidence directory.
- Passed published-baseline provenance fixtures, including local contamination,
  mixed versions, missing attachments, mismatched POM/JAR versions, and
  root/module self-comparison guards; API compatibility fixtures also passed.
- `mvn -B -ntp -pl reactive-http-client-starter -Dtest=DocumentationReleaseArtifactTest test`
  passed 32 tests and regenerated an honest snapshot-development readiness
  manifest with native and publication work visible.
- `bash -n scripts/verify-published-baseline-fixtures.sh`, manifest assertions,
  and `git diff --check` passed.

---

## Priority 2 - Declarative Request-Parameter Grammar

### [x] 2.1 Define one starter-owned parameter grammar

- [x] Inventory path, query, named header, header map, idempotency-key, body,
      form-field, and form-file parameter roles.
- [x] Resolve inherited and multi-level generic parameter types against the
      concrete client before validation.
- [x] Reject conflicting request-binding annotations on one parameter with the
      concrete client, declaring interface, full method signature, parameter
      index/type, and conflicting roles in the message.
- [x] Define duplicate path/query/header/idempotency/form names, including
      case-insensitive header collisions, without silently overwriting values.
- [x] Validate `@FormFile` parameter types at startup.
- [x] Inventory unannotated endpoint parameters and choose a compatibility-safe
      warning or opt-in strict policy before changing accepted published clients.

### [x] 2.2 Fail before invocation side effects and keep all consumers aligned

- [x] Reject a null/missing required path variable before auth, serialization,
      body/resource subscription, lifecycle attempt notification, or dispatch.
- [x] Apply the same grammar through factory startup, effective contracts,
      diagnostics, AOT validation, and `MockReactiveHttpClient`.
- [x] Keep replacement `MethodMetadataCache` behavior and foreign `FactoryBean`
      clients outside starter-only validation.
- [x] Add direct, inherited, generic, `@ApiRef`, replacement-cache, factory-method,
      AOT, diagnostics/export, and mock tests.
- [x] Preserve valid existing scalar, collection, array, body, streaming, and
      multipart declarations.
- [x] Run focused suites, the starter/test-helper reactor, strict starter japicmp,
      and `git diff --check`.

Evidence:

- Added one request-parameter grammar over `RequestPlan` metadata. It resolves
  inherited generic parameter types against the concrete client, rejects
  conflicting roles and duplicate path/query names, treats named headers and
  parameter idempotency keys as one case-insensitive namespace, permits repeated
  multipart names as ordered parts, and validates `@FormFile` shapes at startup.
- Preserved unannotated parameters as ignored compatibility behavior with a
  deduplicated warning. Required path variables now reject null before auth,
  serialization, body subscription, lifecycle notification, or network dispatch;
  dynamic header-map collisions also fail instead of overwriting prior values.
- Applied validation through factory startup, AOT, effective-contract/diagnostic
  export, and `MockReactiveHttpClient`; replacement metadata caches retain their
  parsed contract and foreign `FactoryBean` clients remain outside starter-only
  validation.
- Focused request-grammar, invocation, AOT, diagnostics, factory, header-map, and
  mock-helper suites passed 171 starter tests and 39 mock-helper tests.
- `mvn -B -ntp -pl reactive-http-client-test -am test` passed 949 starter tests
  and 42 test-helper tests, including documentation and metadata checks.
- Strict starter japicmp passed against Central-resolved `3.4.0` from
  `target/published-baseline-repositories/api-starter-v25-priority2-3.4.0`;
  `git diff --check` passed.

---

## Priority 3 - URI Template, Request-Target, and Authority Contract

### [x] 3.1 Freeze structured URI construction

- [x] Inventory base URL path-prefix behavior for empty, relative, and
      leading-slash endpoint paths.
- [x] Cover annotation and `@ApiRef` paths with path/query template variables,
      literal query entries, repeated values, empty values, and omitted nulls.
- [x] Freeze raw Unicode, reserved-character, slash, plus, percent, hash, and
      pre-encoded input behavior using Spring structured URI APIs.
- [x] Preserve deterministic order and precedence for template, default, method,
      and auth-added query values.
- [x] Reject or explicitly support endpoint scheme, authority, user-info, and
      fragment syntax after compatibility and security review.
- [x] Produce method-specific startup/invocation errors without credentials or
      resolved sensitive values.

### [x] 3.2 Prove the real request target

- [x] Add real HTTP/1.1 and H2/H2C fixtures that capture exact path and query
      request-target bytes.
- [x] Verify direct, TLS, and configured proxy paths retain the intended
      authority and proxy request form.
- [x] Verify redirects cannot reclassify the original declarative template as a
      caller-controlled configured-client fact.
- [x] Align final-request observation, lifecycle/log/observer metadata, effective
      contracts, snapshots, and mocks with what each layer can prove.
- [x] Add malformed-template, authority-escape, inherited, `@ApiRef`, and native
      regression coverage.
- [x] Run focused URI/proxy tests, full starter verification, and
      `git diff --check`.

Evidence:

- Added one starter-owned structured URI parser/builder for annotation and
  configured `@ApiRef` paths. Startup, AOT, effective-contract diagnostics, the
  runtime handler, and `MockReactiveHttpClient` now share authority/fragment,
  template-variable, malformed-syntax, and sanitized-error rules while retaining
  replacement metadata-cache and foreign `FactoryBean` ownership boundaries.
- Froze Spring base-path joining and raw UTF-8/reserved-character expansion,
  including repeated/literal/empty/omitted query values and deliberate
  double-encoding of pre-encoded `%` input. Template, configured default, method,
  and auth-provider query precedence remains deterministic; auth mutation no
  longer re-encodes the already resolved request URI.
- Added real HTTP/1.1, H2C, TLS, and HTTP CONNECT proxy fixtures that assert exact
  request targets and authority. Redirect evidence separates both server-observed
  dispatches from the original declarative template/request URL retained at the
  WebClient observation boundary by lifecycle, observer, and exchange-log records.
- Focused URI, auth, startup, AOT, exporter/diagnostic, invocation, and mock suites
  passed 201 starter tests and 42 mock-helper tests. The full starter/test-helper
  reactor passed 965 starter tests and 45 test-helper tests, including generated
  documentation and metadata checks.
- Strict starter japicmp passed against Central-resolved `3.4.0` from the fresh
  `target/published-baseline-repositories/api-starter-v25-priority3-3.4.0`
  repository; `git diff --check` passed.

---

## Priority 4 - HTTP Method, Final-Status, and Framing Semantics

### [x] 4.1 Add one final-status and bodiless matrix

- [x] Cover real `HEAD` and `OPTIONS` over HTTP/1.1 and H2 where supported.
- [x] Cover final `204`, `205`, `304`, visible 3xx, and informational responses
      followed by a final response where Reactor Netty exposes them.
- [x] Verify `Mono<Void>`, `Mono<T>`, `Mono<ResponseEntity<Void>>`, and
      `Mono<ResponseEntity<T>>` empty-body behavior.
- [x] Preserve status and headers for supported response envelopes.
- [x] Keep final 3xx out of error mappers when redirect following is disabled and
      final 4xx/5xx inside the configured decoder/mapper chain.
- [x] Align lifecycle, observer, exchange-log, Micrometer, health, and OTel final
      status/category facts.

### [x] 4.2 Prove framing cleanup and connection safety

- [x] Cover unexpected bytes on bodiless declarations without leaking buffers or
      unnecessarily discarding reusable connections.
- [x] Cover invalid/conflicting `Content-Length`, transfer framing, truncated
      responses, and close-delimited bodies with a real malformed peer.
- [x] Quarantine malformed connections before another pooled request can reuse
      decoder state or leftover bytes.
- [x] Retain the POST-then-PUT transport framing regression and extend it with
      HEAD/bodiless and failure cases.
- [x] Verify error/failure-stage attribution follows the final protocol evidence,
      not parser message text.
- [x] Run focused framing/resource tests, full starter verification, and
      `git diff --check`.

Evidence:

- Added a real HTTP/1.1/H2C status matrix for `HEAD`, `OPTIONS`, `204`, `205`,
  `304`, visible `302`, `400`, and `503` across all supported unary and
  `ResponseEntity` no-body shapes. Status, headers, mapper boundaries, lifecycle,
  observer, exchange-log, Micrometer, health, and OTel terminal facts remain
  aligned.
- Recorded the supported-stack boundary for raw HTTP/1.1 `103 Early Hints`
  followed by `200`: Reactor Netty exposes `103` to WebClient, so the starter
  reports that response and cannot invent the later final status.
- Added a bounded raw HTTP/1.1 peer proving unexpected-body drain and same-socket
  reuse, HEAD framing, valid close-delimited completion with replacement capacity,
  invalid/conflicting length rejection, invalid chunk and truncated-body failure,
  and bad-socket quarantine before a following pooled probe.
- Extended the existing same-connection POST-then-PUT regression through HEAD and
  a following GET. Netty decoder and premature-close stages now use concrete type,
  dispatch, and status evidence, with TLS/proxy/DNS/connect evidence retaining
  precedence over generic framing fallback.
- Focused status/framing/timeout/transport/OTel suites passed 23 starter tests and
  25 OTel tests; the mTLS precedence regression suite passed another 25 tests.
- `mvn -B -ntp -pl reactive-http-client-test,reactive-http-client-otel -am test`
  passed 974 starter, 45 test-helper, and 43 OTel tests.
- Strict starter japicmp passed against Central-resolved `3.4.0` from the fresh
  `target/published-baseline-repositories/api-starter-v25-priority4-head-3.4.0`
  repository; documentation generation and `git diff --check` passed.

---

## Priority 5 - Multipart and Form-Data Wire Ownership

### [x] 5.1 Prove multipart encoding on a real peer

- [x] Parse mixed `@FormField` and `@FormFile` requests from `byte[]`,
      `FileAttachment`, and reopenable `Resource` inputs.
- [x] Assert multipart boundary, part order, repeated fields, content disposition,
      filename, content type, and exact body bytes.
- [x] Verify null scalar/file omission and collection/array ordering.
- [x] Record HTTP/1.1 framing and H2 data delivery without assuming aggregation
      or one fixed content length.
- [x] Document non-ASCII filename behavior exactly as emitted by the configured
      Spring writer; do not claim an unverified encoding convention.
- [x] Keep unsupported part types rejected by the startup grammar.

### [x] 5.2 Preserve resource and replay ownership

- [x] Cover cancellation before write and during a resource part.
- [x] Cover peer reset, request-write timeout, response timeout, retry,
      body-preserving redirect, and one-time auth replay.
- [x] Prove every opened resource is closed once and no part is read/subscribed
      after terminal cancellation or failure.
- [x] Prove resource parts remain unbuffered and produce identical bytes only when
      the application supplies a reopenable source.
- [x] Preserve runtime replay warnings/strict rejection and built-in SigV4
      pre-dispatch rejection for unprovable multipart hashes.
- [x] Keep mock multipart assertions explicitly in-process and run focused/full
      starter and test-helper verification.

Evidence:

- Added a real HTTP/1.1/H2C multipart peer that parses exact mixed `byte[]`,
  `FileAttachment`, reopenable `Resource`, scalar, collection, and array parts.
  Declaration order, including duplicate names separated by other parts,
  repeated-value order, null omission, generated boundary, disposition, filename,
  content type, and body bytes are asserted. HTTP/1.1 uses chunked framing when
  aggregate length is unknown; H2C proves complete DATA delivery without requiring
  a fixed content length.
- Froze the configured Spring 7 writer's current non-ASCII filename behavior as a
  literal UTF-8 value in quoted `filename`, without claiming `filename*` support.
  Existing startup request-parameter grammar tests continue to reject unsupported
  multipart part declarations.
- Added gated, slow, and generated resource fixtures proving no pre-write open,
  dispatch before source completion, one close per open, and no reads after
  cancellation, peer reset, request-write timeout, or terminal completion.
  Response-timeout cleanup is covered after a completed upload.
- Retry, body-preserving redirect, and one-time auth replay each open a fresh
  resource stream and send identical bytes twice. Existing repeatability warnings,
  strict replay rejection, and built-in SigV4 pre-dispatch multipart rejection
  remain green.
- Added a mock-helper multipart assertion and documented that mock materialization
  is in-process evidence only. Focused suites passed 114 starter tests and 43
  test-helper tests; the full starter/test-helper reactor passed 980 and 46 tests.
- Strict starter japicmp passed against Central-resolved `3.4.0` from the fresh
  `target/published-baseline-repositories/api-starter-v25-priority5-followup-3.4.0`
  repository; generated documentation checks and `git diff --check` passed.

---

## Priority 6 - Subscription and Terminal-State Invariant Consolidation

### [ ] 6.1 Characterize behavior before extraction

- [ ] Freeze stateless/stateful unary, typed flux, raw streaming, and streaming
      envelope outcomes.
- [ ] Freeze retry subscription, redirect dispatch, auth replay, idempotency-key,
      cancellation, timeout, response status/header, and terminal-report counts.
- [ ] Cover concurrent subscriptions and immediate retry resubscription races.
- [ ] Record the current diagnostics-disabled allocation-sensitive path before
      implementation changes.
- [ ] Identify duplicated state transitions and cleanup paths; do not extract
      helpers that only rename one call site.

### [ ] 6.2 Centralize only proven invariants

- [ ] Keep generated key, prepared arguments, active attempt, request observation,
      response state, error, timing, and report-once state subscription-local.
- [ ] Guard cleanup by the attempt that installed each mutable fact.
- [ ] Build one immutable terminal snapshot for lifecycle, observer, exchange-log,
      Micrometer, health, and OTel reporting where practical.
- [ ] Preserve established `attemptCount`, redirect/auth dispatch, failure-stage,
      and streaming-body ownership semantics.
- [ ] Keep implementation package-private with no configurable state machine or
      unrelated request-construction rewrite.
- [ ] Verify no regression in focused race/composition suites, full reactor,
      allocation audit, strict japicmp, and `git diff --check`.

Evidence:

- Pending.

---

## Priority 7 - Stale Pooled-Connection Recovery

### [ ] 7.1 Add bounded stale HTTP/1.1 fixtures

- [ ] Cover `Connection: close` after a successful response.
- [ ] Cover peer FIN after response, idle close before reuse, reset during reuse,
      and close during response consumption.
- [ ] Use one-connection pools to prove stale channel removal and replacement
      capacity deterministically.
- [ ] Verify active/pending gauges converge and queued demand is neither stranded
      nor double-dispatched.
- [ ] Verify factory shutdown remains within the existing bounded disposal policy.

### [ ] 7.2 Separate recovery from replay

- [ ] Prove a later independent call can use a replacement connection.
- [ ] Prove a failed request is not automatically replayed outside configured
      resilience behavior.
- [ ] Preserve safe-method, idempotency-key, body-repeatability, and subscription
      attempt rules when explicit retry is enabled.
- [ ] Prevent stale channel, decoder, URL, status, headers, and failure stage from
      leaking into the next call.
- [ ] Retain V24 GOAWAY behavior and add only missing abrupt H2 close/replacement
      evidence.
- [ ] Run pool/framing/retry/diagnostics suites and full starter verification.

Evidence:

- Pending.

---

## Priority 8 - Terminal Diagnostics and Redaction Parity

### [ ] 8.1 Align final-attempt facts

- [ ] Cover validation, URI, serialization, auth, pool, write, response-header,
      response-body, stale-connection, and cancellation terminal paths.
- [ ] Expose a failure stage only when final-attempt evidence proves it.
- [ ] Keep pre-dispatch errors free of stale URL, status, and response headers.
- [ ] Align lifecycle, observer, exchange log, Micrometer, health, and OTel on all
      fields each contract exposes.
- [ ] Preserve one terminal result and subscription-attempt count under retry,
      redirect, auth replay, and concurrent subscriptions.

### [ ] 8.2 Preserve bounded, side-effect-free support output

- [ ] Prevent arbitrary mapper, codec, auth, filter, multipart, or transport
      messages from entering default diagnostics unsanitized.
- [ ] Keep schema v1 additive, deterministic, request-fact-free, and within
      client/endpoint/text/UTF-8 size bounds.
- [ ] Prove support queries instantiate no lazy client, auth provider, resilience
      registry/instance, resource, pool, or connection.
- [ ] Add sanitized support-bundle fixtures for one request-validation failure and
      one stale-connection recovery.
- [ ] Run starter/OTel diagnostics suites, schema fixtures, native endpoint checks,
      strict japicmp, and `git diff --check`.

Evidence:

- Pending.

---

## Priority 9 - Mock and Assembled-Consumer Parity

### [ ] 9.1 Keep mock contracts within in-process boundaries

- [ ] Apply production request-parameter validation and URI expansion decisions.
- [ ] Add stable multipart part-name/header/byte assertions without claiming wire
      framing, pool reuse, or backpressure.
- [ ] Preserve constructor-injected loggers, application codecs, auth providers,
      inherited generic clients, custom metadata caches, and ordered lifecycle
      hooks.
- [ ] Keep retry/redirect/auth response sequencing distinct from physical socket
      dispatch and transport timing.
- [ ] Add public helper API only when a V25 assertion cannot be expressed through
      an existing stable helper.

### [ ] 9.2 Revalidate independent consumers

- [ ] Run current `3.5.0-SNAPSHOT` consumer tests from a current-only fresh
      repository.
- [ ] Run published `3.4.0` consumer tests from a separate Central-only fresh
      repository.
- [ ] Reject reactor output and locally installed candidate leakage in the
      published lane.
- [ ] Preserve current-run Surefire/effective-POM/dependency/classpath provenance
      incrementally and identify the last completed stage on failure.
- [ ] Reject stale evidence from earlier verifier runs.

Evidence:

- Pending.

---

## Priority 10 - Dependency, API, AOT, and Native Evidence

### [ ] 10.1 Revalidate dependency and public API contracts

- [ ] Run minimum and forward Spring Boot 4 rows under Java 21.
- [ ] Record resolved Framework, Reactor Netty, Netty, Jackson, Micrometer, OTel,
      Resilience4j, JUnit, and Mockito versions for each row.
- [ ] Run full reactor and assembled consumer on each supported row.
- [ ] Verify independent back-off for Actuator, Micrometer, OTel, Resilience4j,
      and auth integrations.
- [ ] Run strict root and module japicmp against published `3.4.0` with every
      dependency-linked row that affects public types.
- [ ] Include every V25 public addition/deprecation and defer incompatibilities.

### [ ] 10.2 Revalidate AOT and native behavior

- [ ] Register concrete inherited request parameter types and every annotation
      queried at runtime without deprecated Framework 7 member categories.
- [ ] Keep AOT validation scoped to starter-owned factory beans and honor a
      replacement `MethodMetadataCache`.
- [ ] Cover `@Bean` factory-method clients, inherited endpoints, and foreign
      factory replacements.
- [ ] Build the GraalVM 25 fixture from a clean immutable commit.
- [ ] Execute at least one feasible V25 grammar, URI, multipart, or stale-recovery
      path in the native binary.
- [ ] Record command, Java/GraalVM/dependency versions, commit, executable hash,
      and runtime result under V25 release evidence.

Evidence:

- Pending.

---

## Priority 11 - Targeted Benchmark and Allocation Re-Audit

### [ ] 11.1 Keep measurements fair and scoped

- [ ] Run benchmark discovery and fairness guards before measurement.
- [ ] Add or change a benchmark row only for production paths modified by V25.
- [ ] Keep equivalent raw `WebClient`, Spring HTTP Interface, and starter work for
      comparable loopback rows.
- [ ] Keep multipart/stale-recovery setup out of timed regions or retain those
      paths as correctness fixtures only.
- [ ] Keep logging output redirected and diagnostics no-network rows separately
      classified.

### [ ] 11.2 Compare current work with published `3.4.0`

- [ ] Run current and published-baseline release JMH from distinct fresh
      repositories.
- [ ] Record project, starter, baseline, dependency, JVM, OS, and clean commit
      metadata.
- [ ] Review request expansion, URI construction, unary/JSON success,
      diagnostics-disabled, and terminal-reporting scenarios only when changed.
- [ ] Run the non-gating comparison with review thresholds and inspect allocation
      profiles for named movements.
- [ ] Keep normal CI free of numeric hard gates and make no broad raw-WebClient
      parity claim.
- [ ] Promote a versioned report only if release notes make a numerical
      performance/allocation claim.

Evidence:

- Pending.

---

## Priority 12 - Documentation and Operations Consolidation

### [ ] 12.1 Align request-side public guidance

- [ ] Add one concise request-parameter role/compatibility table.
- [ ] Distinguish startup declaration validation from per-invocation null/value
      validation.
- [ ] Align URI encoding/authority, final-status/bodiless, multipart ownership,
      and stale-connection wording with real fixtures.
- [ ] Keep README concise and point to canonical annotation, multipart,
      streaming, production, and operations guides.
- [ ] Keep mock documentation explicit about in-process versus transport-owned
      evidence.

### [ ] 12.2 Preserve generated and operational evidence

- [ ] Update operations troubleshooting and support-bundle capture for malformed
      framing and stale-connection recovery using bounded sanitized facts.
- [ ] Keep historical migration, API, benchmark, and release-decision documents
      immutable and labeled as historical evidence.
- [ ] Use `EXAMPLE_` and `.example.invalid` placeholders in copyable examples.
- [ ] Regenerate configuration reference only when property metadata changes.
- [ ] Run configuration metadata, example-property, anchor, local-link,
      roadmap-link, public-version, and release-artifact documentation tests.
- [ ] Run `git diff --check`.

Evidence:

- Pending.

---

## Priority 13 - V25 Release Go/No-Go

### [ ] 13.1 Select release scope and candidate version

- [ ] Inventory delivered internal fixes, public additions, deprecations, and
      diagnostics schema effects.
- [ ] Select a `3.4.x` patch for internal correctness/docs only, or `3.5.0` for a
      backward-compatible public addition.
- [ ] Reject/defer binary or source incompatible changes and schema-v1 breaks.
- [ ] Keep the reactor snapshot and published baseline separate until the release
      cut.
- [ ] Record whether public performance claims require a promoted report.

### [ ] 13.2 Assemble immutable release evidence

- [ ] Run `mvn -B -ntp clean verify` from the release-prep tree.
- [ ] Run strict root and module API compatibility from isolated Central-only
      repositories.
- [ ] Run generation packaging, current/published consumer, supported matrix,
      request/framing/multipart/pool composition, AOT/native, and documentation
      gates.
- [ ] Verify complete candidate parent, starter, test-helper, and OTel POM,
      binary, source, and Javadoc artifacts.
- [ ] Re-run every reproducible release gate from one clean immutable commit.
- [ ] Cite a clean promoted benchmark report or keep changelog wording
      non-numerical.

### [ ] 13.3 Record the mutually exclusive decision

- [ ] **Go path:** tag and publish the selected version from the matching
      immutable commit.
- [ ] **Go path:** verify every Maven Central artifact and assembled published
      consumer before moving public/API/consumer/benchmark baselines.
- [ ] **Go path:** open the next snapshot line and archive V25 only after Central
      verification succeeds.
- [ ] **No-go path:** publish nothing and record each blocker, reproduction, and
      retained evidence path.
- [ ] Update roadmap/checklist/index/changelog status to match the selected path.
- [ ] Run final release-document tests and `git diff --check`.

Evidence:

- Pending.

## Completion Rule

V25 is complete only when every checked behavior has evidence at the layer that
owns it. Startup and mock tests cannot prove request-target bytes, multipart
framing, resource closure, pooled recovery, or protocol parser isolation.
Internal state extraction must remain behavior-neutral under characterization,
compatibility, and allocation evidence. Check only the release-decision branch
that actually occurred; the unchecked mutually exclusive branch remains
historical context after V25 is archived.
