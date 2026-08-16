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
  remain green. The auth-visible multipart part list retains declared names and
  global order, including noncontiguous repetitions, without exposing the wire
  writer's synthetic ordering keys.
- Added a mock-helper multipart assertion and documented that mock materialization
  is in-process evidence only. Focused suites passed 115 starter tests and 43
  test-helper tests; the full starter/test-helper reactor passed 981 and 46 tests.
- Strict starter japicmp passed against Central-resolved `3.4.0` from the fresh
  `target/published-baseline-repositories/api-starter-v25-priority5-followup-3.4.0`
  repository; generated documentation checks and `git diff --check` passed.

---

## Priority 6 - Subscription and Terminal-State Invariant Consolidation

### [x] 6.1 Characterize behavior before extraction

- [x] Freeze stateless/stateful unary, typed flux, raw streaming, and streaming
      envelope outcomes.
- [x] Freeze retry subscription, redirect dispatch, auth replay, idempotency-key,
      cancellation, timeout, response status/header, and terminal-report counts.
- [x] Cover concurrent subscriptions and immediate retry resubscription races.
- [x] Record the current diagnostics-disabled allocation-sensitive path before
      implementation changes.
- [x] Identify duplicated state transitions and cleanup paths; do not extract
      helpers that only rename one call site.

### [x] 6.2 Centralize only proven invariants

- [x] Keep generated key, prepared arguments, active attempt, request observation,
      response state, error, timing, and report-once state subscription-local.
- [x] Guard cleanup by the attempt that installed each mutable fact.
- [x] Build one immutable terminal snapshot for lifecycle, observer, exchange-log,
      Micrometer, health, and OTel reporting where practical.
- [x] Preserve established `attemptCount`, redirect/auth dispatch, failure-stage,
      and streaming-body ownership semantics.
- [x] Keep implementation package-private with no configurable state machine or
      unrelated request-construction rewrite.
- [x] Verify no regression in focused race/composition suites, full reactor,
      allocation audit, strict japicmp, and `git diff --check`.

Evidence:

- Characterized the existing stateless/stateful unary, typed-flux, raw-stream,
  streaming-envelope, retry, redirect, auth replay, generated-key, cancellation,
  timeout, status/header, and one-terminal-report behavior before extraction.
  Existing immediate-retry and composition fixtures remain the behavioral source
  of truth; new concurrent-subscription and competing-terminal tests cover the
  state ownership races directly.
- Added package-private `SubscriptionReportingState`. Generated idempotency keys,
  prepared arguments, timing, attempts, and terminal selection remain local to
  each subscription. Each attempt owns one immutable observation/response value,
  and cleanup/reset operations are accepted only from the attempt that installed
  the active state, so delayed retry cleanup cannot clear a newer attempt.
  Backoff cleanup resets the latest attempt's transport evidence in place while
  retaining its prepared arguments for terminal lifecycle and exchange logging.
- Mono and Flux terminal paths now create one immutable `TerminalSnapshot` through
  a single compare-and-set winner. Lifecycle hooks, exchange logging, observers,
  and observer-derived Micrometer, health, and OTel consumers read that same
  snapshot. The diagnostics-disabled stateless request path remains separate.
- The focused race/composition suite passed 45 tests. A follow-up timeout,
  idempotency-key, and subscription-state suite passed 42 tests. The full starter,
  test-helper, and OTel reactor passed 984, 46, and 43 tests respectively,
  including streaming ownership, redirect/auth composition, failure-stage, and
  immediate-retry coverage.
- A local diagnostics-disabled JMH smoke recorded 13,602.549 B/op before and
  13,699.036 B/op after the change (0.7% difference with overlapping uncertainty).
  This is characterization evidence only and is not a public performance claim.
- Strict starter japicmp passed against Central-resolved `3.4.0` from
  `target/published-baseline-repositories/api-starter-v25-priority6-3.4.0`;
  `git diff --check` passed.

---

## Priority 7 - Stale Pooled-Connection Recovery

### [x] 7.1 Add bounded stale HTTP/1.1 fixtures

- [x] Cover `Connection: close` after a successful response.
- [x] Cover peer FIN after response, idle close before reuse, reset during reuse,
      and close during response consumption.
- [x] Use one-connection pools to prove stale channel removal and replacement
      capacity deterministically.
- [x] Verify active/pending gauges converge and queued demand is neither stranded
      nor double-dispatched.
- [x] Verify factory shutdown remains within the existing bounded disposal policy.

### [x] 7.2 Separate recovery from replay

- [x] Prove a later independent call can use a replacement connection.
- [x] Prove a failed request is not automatically replayed outside configured
      resilience behavior.
- [x] Preserve safe-method, idempotency-key, body-repeatability, and subscription
      attempt rules when explicit retry is enabled.
- [x] Prevent stale channel, decoder, URL, status, headers, and failure stage from
      leaking into the next call.
- [x] Retain V24 GOAWAY behavior and add only missing abrupt H2 close/replacement
      evidence.
- [x] Run pool/framing/retry/diagnostics suites and full starter verification.

Evidence:

- Added `StalePooledConnectionRecoveryContractTest`, a seven-case raw IPv4 HTTP/1.1
  fixture using the real starter proxy and a one-connection Reactor Netty pool.
  It distinguishes transport-owned `Connection: close`, peer FIN after a complete
  response, a peer-closing an idle pooled socket, RST after a reused request is
  observed, FIN during a declared fixed-length response body, and RST before any
  request bytes are read. The FIN and idle-close cases wait for the tracked client
  channel's disposal signal before issuing replacement demand, so server-side
  close observation cannot race pool retirement.
- The reset-on-reuse case holds the active exchange while an independent probe is
  queued, observes `active.connections=1` and `pending.connections=1`, then proves
  the failed path was dispatched once, the probe used a different connection, and
  active/pending/total/idle gauges converged to `0/0/1/1`. The partial-body case
  retains its `200`, response headers, and `RESPONSE_BODY` stage while the next
  probe carries only its own URL/status/header/success facts.
- Reactor Netty's one-time connection-reset retry is disabled for both starter-owned
  business and OAuth2 token-service transports. A reset before request bytes are
  read therefore opens one connection and records no hidden second dispatch. With
  an explicit two-attempt GET retry, a dispatched transport failure resubscribes
  once, uses replacement capacity, and reports subscription attempt count `2`;
  existing retry-safety, idempotency-key, publisher/application-owned body, and
  retry/redirect/auth composition tests remain green; no transport retry or body
  buffering was added.
- Factory shutdown with active and pending work completed exceptionally inside the
  existing five-second disposal bound while their request and acquire deadlines
  were both 30 seconds. The factory rejects pending acquisitions and closes tracked
  active channels without dispatching queued work. A stalled-resource regression
  proves business-provider, OAuth2 token-service-provider, and active-channel
  disposal all start concurrently under one shared deadline rather than receiving
  three sequential timeouts. A concurrent-registration regression also proves a
  connection completing after shutdown crosses its lifecycle gate is closed
  immediately and cannot escape the initial drain. Existing V24 H2
  GOAWAY/abrupt-close coverage remained unchanged and all four retirement tests
  passed.
- Updated `docs/05-connection-pool.md`, `docs/12-proxy-tls.md`, and
  `docs/30-operations-troubleshooting.md` to distinguish graceful retirement,
  stale-socket replacement, failed-call replay, H2 GOAWAY, gauge convergence, and
  idle/lifetime eviction limits. Added matching unreleased changelog evidence.
- Focused pool/framing/retry/OAuth2 verification passed 119 tests, and the
  shutdown-focused pool/H2/OAuth2 matrix passed 38 tests. Full starter
  verification passed 993 tests, including 32 documentation/release-artifact
  tests. `git diff --check` passed.

---

## Priority 8 - Terminal Diagnostics and Redaction Parity

### [x] 8.1 Align final-attempt facts

- [x] Cover validation, URI, serialization, auth, pool, write, response-header,
      response-body, stale-connection, and cancellation terminal paths.
- [x] Expose a failure stage only when final-attempt evidence proves it.
- [x] Keep pre-dispatch errors free of stale URL, status, and response headers.
- [x] Align lifecycle, observer, exchange log, Micrometer, health, and OTel on all
      fields each contract exposes.
- [x] Preserve one terminal result and subscription-attempt count under retry,
      redirect, auth replay, and concurrent subscriptions.

### [x] 8.2 Preserve bounded, side-effect-free support output

- [x] Prevent arbitrary mapper, codec, auth, filter, multipart, or transport
      messages from entering default diagnostics unsanitized.
- [x] Keep schema v1 additive, deterministic, request-fact-free, and within
      client/endpoint/text/UTF-8 size bounds.
- [x] Prove support queries instantiate no lazy client, auth provider, resilience
      registry/instance, resource, pool, or connection.
- [x] Add sanitized support-bundle fixtures for one request-validation failure and
      one stale-connection recovery.
- [x] Run starter/OTel diagnostics suites, schema fixtures, native endpoint checks,
      strict japicmp, and `git diff --check`.

Evidence:

- `DiagnosticContextContractTest` now proves required request-argument validation
  creates no logical-call terminal record, while URI construction and auth-body
  serialization failures create exactly one attempt-1, stage-unknown terminal
  outcome with no final URL, status, or response headers across lifecycle,
  observer, and exchange-log surfaces.
- Existing real-transport and composition suites were re-run for auth/filter,
  pool acquisition, request write, response headers/body, cancellation,
  stale-socket recovery, retry, redirect, one-time auth replay, and concurrent
  subscriptions. The focused starter matrix passed 192 tests. Micrometer and
  health now explicitly reject a 10 KiB arbitrary exception message from tags
  and details; existing default-logger and OTel structural-redaction assertions
  remain green.
- `ReactiveHttpClientDiagnosticsProvider` now snapshots existing singleton or
  cached `AuthProviderFactory` candidates once per report, excludes non-autowire
  candidates, suppresses parent factories shadowed by child names, and honors
  `PriorityOrdered`, `Ordered` instance precedence over bean metadata, Spring's
  non-order comparator fallback, custom autowire candidate resolvers, and
  factory-method ordering metadata. Runtime auth-factory selection uses the same
  name-aware candidate rules, including disabled parent and prototype
  definitions. An unresolved lazy factory or uncached singleton factory product,
  whether definition-backed or directly registered, leaves
  `strictBodySigningValidation=null` without creating the factory, client, auth
  provider, registry/instance, resource, pool, connection, or other network
  state. Existing schema-v1 determinism, cardinality, text, and UTF-8 byte-limit
  fixtures remain unchanged.
- Added reviewable sanitized fixtures at
  `docs/fixtures/support-bundle-request-validation.json` and
  `docs/fixtures/support-bundle-stale-connection-recovery.json`, with executable
  documentation assertions rejecting credentials, concrete URLs, payload/error
  text fields, and machine-local paths. The stale-connection fixture uses the
  published `TIMEOUT` category for `PrematureCloseException`.
- Focused OTel verification passed 25 tests; AOT/Actuator endpoint verification
  passed 41 tests. The GraalVM native smoke compiled successfully in 4m27s and
  its executable exited `0` after checking the `rhttpclients` endpoint and schema
  outputs. Strict reactor japicmp against published `3.4.0` passed for starter,
  test helper, and OTel modules. `git diff --check` passed.

---

## Priority 9 - Mock and Assembled-Consumer Parity

### [x] 9.1 Keep mock contracts within in-process boundaries

- [x] Apply production request-parameter validation and URI expansion decisions.
- [x] Add stable multipart part-name/header/byte assertions without claiming wire
      framing, pool reuse, or backpressure.
- [x] Preserve constructor-injected loggers, application codecs, auth providers,
      inherited generic clients, custom metadata caches, and ordered lifecycle
      hooks.
- [x] Keep retry/redirect/auth response sequencing distinct from physical socket
      dispatch and transport timing.
- [x] Add public helper API only when a V25 assertion cannot be expressed through
      an existing stable helper.

### [x] 9.2 Revalidate independent consumers

- [x] Run current `3.5.0-SNAPSHOT` consumer tests from a current-only fresh
      repository.
- [x] Run published `3.4.0` consumer tests from a separate Central-only fresh
      repository.
- [x] Reject reactor output and locally installed candidate leakage in the
      published lane.
- [x] Preserve current-run Surefire/effective-POM/dependency/classpath provenance
      incrementally and identify the last completed stage on failure.
- [x] Reject stale evidence from earlier verifier runs.

Evidence:

- `MockReactiveHttpClient` continues to run the production request-parameter,
  URI-template, and return-type validators before constructing a proxy. It now
  accepts a caller-supplied `MethodMetadataCache` for both validation and
  invocation, preserving the documented replacement-cache extension in helper
  tests. Existing constructor-injected logger, application Jackson 3 codec,
  auth-provider, inherited-generic-client, and `Ordered`/`@Order` lifecycle
  coverage remains green.
- Added `RecordedMultipartPart`, `RecordedExchange.multipartParts()`, and
  indexed fluent assertions for stable encoded part names, order, filenames,
  read-only headers, and exact binary bytes. This additive public API was needed
  because the existing UTF-8 whole-body string exposed generated boundaries and
  could not safely assert binary part bytes. Documentation explicitly limits
  these records to materialized in-process data and excludes wire framing,
  backpressure, sockets, pools, and connection reuse.
- Mock composition tests distinguish three in-process `401 -> 503 -> 200`
  exchanges from two outer retry subscriptions. A configured
  `followRedirects=true` test keeps the visible `302` as one exchange because
  redirect dispatch belongs to a real connector.
- `scripts/verify-current-consumer.sh` passed against current
  `3.5.0-SNAPSHOT` artifacts installed into
  `target/current-reactor-repositories/consumer-3.5.0-SNAPSHOT`. Its evidence
  records `completedStage=evidence-verified`, 45 mock tests, one Boot 4 helper
  smoke test, three assembled-consumer tests, effective POM, dependency tree,
  classpath, and artifact checksums. The assembled fixture now proves its
  replacement `MethodMetadataCache` is used alongside inherited APIs.
- `scripts/verify-published-consumer.sh 3.4.0` passed from the separate
  Central-only `target/published-baseline-repositories/consumer-3.4.0`
  repository. Published provenance, remote markers, effective POMs, dependency
  tree, classpath, and checksums reject reactor-output or locally installed
  candidate leakage. A preserved intermediate failed current run recorded
  `completedStage=mock-tests` and `exitStatus=1`; fixed output paths rejected
  stale evidence before the successful reruns.
- `mvn -B -ntp -pl reactive-http-client-test -am test` passed 1,015 starter
  tests and 48 test-helper tests. Strict starter/test-helper japicmp against
  published `3.4.0` passed from a new Central-only repository.
  `git diff --check` passed.

---

## Priority 10 - Dependency, API, AOT, and Native Evidence

### [x] 10.1 Revalidate dependency and public API contracts

- [x] Run minimum and forward Spring Boot 4 rows under Java 21.
- [x] Record resolved Framework, Reactor Netty, Netty, Jackson, Micrometer, OTel,
      Resilience4j, JUnit, and Mockito versions for each row.
- [x] Run full reactor and assembled consumer on each supported row.
- [x] Verify independent back-off for Actuator, Micrometer, OTel, Resilience4j,
      and auth integrations.
- [x] Run strict root and module japicmp against published `3.4.0` with every
      dependency-linked row that affects public types.
- [x] Include every V25 public addition/deprecation and defer incompatibilities.

### [ ] 10.2 Revalidate AOT and native behavior

- [x] Register concrete inherited request parameter types and every annotation
      queried at runtime without deprecated Framework 7 member categories.
- [x] Keep AOT validation scoped to starter-owned factory beans and honor a
      replacement `MethodMetadataCache`.
- [x] Cover `@Bean` factory-method clients, inherited endpoints, and foreign
      factory replacements.
- [ ] Build the GraalVM 25 fixture from a clean immutable commit.
- [x] Execute at least one feasible V25 grammar, URI, multipart, or stale-recovery
      path in the native binary.
- [x] Record command, Java/GraalVM/dependency versions, commit, executable hash,
      and runtime result under V25 release evidence.

Evidence:

- `scripts/verify-supported-matrix.sh` now accepts an explicit evidence root and
  passed under Oracle JDK `21.0.8` for Spring Boot `4.0.0` and `4.1.0`. Each row
  ran the full reactor (`1,015` starter, `48` test-helper, and `43` OTel tests),
  the three-test assembled consumer, and the independent Micrometer,
  Resilience4j, Actuator, OTel API/bean, and custom OAuth factory back-off
  contracts. Evidence is under
  `target/release-evidence/v25/priority10/matrix/`.
- The Boot `4.0.0` row resolved Framework/WebFlux `7.0.1`, Reactor Netty `1.3.0`,
  Netty `4.2.7.Final`, Jackson `3.0.2`, Micrometer `1.16.0`, OTel `1.55.0`,
  Resilience4j `2.4.0`, JUnit `6.0.1`, and Mockito `5.20.0`. The Boot `4.1.0`
  row resolved Framework/WebFlux `7.0.8`, Reactor Netty `1.3.6`, Netty
  `4.2.15.Final`, Jackson `3.1.4`, Micrometer `1.17.0`, OTel `1.62.0`,
  Resilience4j `2.4.0`, JUnit `6.0.3`, and Mockito `5.23.0`.
- Strict root japicmp passed on both matrix rows. Separate starter-module runs
  also passed against published `3.4.0` from isolated Central-only repositories;
  reports and provenance are under
  `target/release-evidence/v25/priority10/module-api/`. V25 adds request/URI
  validation cache methods and multipart test-helper records/assertions; no
  removals, deprecations, or source/binary incompatibilities were reported.
- Existing AOT tests prove starter-factory ownership, replacement
  `MethodMetadataCache` handling, `@Bean` factory-method clients, inherited
  endpoints, and foreign factory replacements. No deprecated Framework 7
  `INTROSPECT_*` member category remains in starter runtime hints.
- The first GraalVM `25.0.3` execution exposed a native-only initializer failure:
  the private type-only auth-factory lookup marker used by diagnostics had been
  removed from the image. An exact invocation hint and regression assertion now
  retain that marker. The rebuilt Boot `4.0.0` image passed inherited endpoint,
  configured `@ApiRef`, auth, compression, Problem Detail, diagnostics, health,
  and metrics paths.
- The successful dirty-tree validation records commands, toolchain, resolved
  dependencies, source commit/state, executable SHA-256, and runtime output at
  `target/release-evidence/v25/priority10/native-smoke-dirty-validation/`. A
  clean immutable-commit rerun remains required before closing `10.2`.

---

## Priority 11 - Targeted Benchmark and Allocation Re-Audit

### [x] 11.1 Keep measurements fair and scoped

- [x] Run benchmark discovery and fairness guards before measurement.
- [x] Add or change a benchmark row only for production paths modified by V25.
- [x] Keep equivalent raw `WebClient`, Spring HTTP Interface, and starter work for
      comparable loopback rows.
- [x] Keep multipart/stale-recovery setup out of timed regions or retain those
      paths as correctness fixtures only.
- [x] Keep logging output redirected and diagnostics no-network rows separately
      classified.

### [x] 11.2 Compare current work with published `3.4.0`

- [x] Run current and published-baseline release JMH from distinct fresh
      repositories.
- [x] Record project, starter, baseline, dependency, JVM, OS, and clean commit
      metadata.
- [x] Review request expansion, URI construction, unary/JSON success,
      diagnostics-disabled, and terminal-reporting scenarios only when changed.
- [x] Run the non-gating comparison with review thresholds and inspect allocation
      profiles for named movements.
- [x] Keep normal CI free of numeric hard gates and make no broad raw-WebClient
      parity claim.
- [x] Promote a versioned report only if release notes make a numerical
      performance/allocation claim.

Evidence:

- Current and published-`3.4.0` benchmark discovery/fairness profiles passed
  before measurement. The selected loopback scenarios retain one raw
  `WebClient`, one Spring HTTP Interface, and one starter implementation for Get
  No Body, Get Path Query Header, and Post JSON. No benchmark source or scenario
  was added: multipart ordering/ownership and stale pooled-connection recovery
  remain deterministic wire-contract fixtures rather than timed work.
- Release-quality current and baseline runs used the same JDK 25.0.3, Spring Boot
  4.0.0, Spring Framework/WebFlux 7.0.1, Reactor Netty 1.3.0, Netty 4.2.7.Final,
  Jackson 3.0.2, Micrometer 1.16.0, OpenTelemetry 1.55.0, Linux host, eight-CPU
  allocation, JMH settings, redirected logger, and benchmark sources. The current
  report records clean commit `0e44c8ed0caae5d1b254e02812605daa5e41d744`;
  the baseline resolves published starter `3.4.0` through a distinct fresh
  Central-only repository with verified POM/JAR provenance.
- The target-only paired reports and non-gating comparison are under
  `target/release-evidence/v25/priority11/`. Both inputs contain the same 31 JMH
  result rows covering request expansion/URI construction, diagnostics-disabled
  execution, terminal reporting, and equivalent unary/JSON loopback controls.
- GC-normalized allocation review found `+188 B/op` on both argument-expansion
  paths, about `+2.4 KiB/op` on diagnostics-disabled execution, and about
  `+3.0 KiB/op` on the full no-network proxy invocation. These cross the
  percentage review trigger but stay below its `4 KiB/op` absolute trigger and
  are retained as bounded costs of V25 request validation and immutable
  subscription/terminal reporting. No measured row crossed the absolute
  allocation trigger, so this audit does not justify a speculative hot-path
  refactor.
- Loopback latency review rows moved across raw `WebClient`, Spring HTTP
  Interface, and starter controls, including contradictory mode/fork movement;
  allocations for raw and Spring controls stayed approximately flat. The timing
  rows are classified as run-level noise, not evidence of broad raw-`WebClient`
  parity or a production optimization.
- Normal CI remains free of numeric benchmark gates. No source-controlled report
  was promoted and no numerical release-note claim was added; the scoped evidence
  remains target-only for release review. Benchmark report/comparator tests and
  `git diff --check` passed.

---

## Priority 12 - Documentation and Operations Consolidation

### [x] 12.1 Align request-side public guidance

- [x] Add one concise request-parameter role/compatibility table.
- [x] Distinguish startup declaration validation from per-invocation null/value
      validation.
- [x] Align URI encoding/authority, final-status/bodiless, multipart ownership,
      and stale-connection wording with real fixtures.
- [x] Keep README concise and point to canonical annotation, multipart,
      streaming, production, and operations guides.
- [x] Keep mock documentation explicit about in-process versus transport-owned
      evidence.

### [x] 12.2 Preserve generated and operational evidence

- [x] Update operations troubleshooting and support-bundle capture for malformed
      framing and stale-connection recovery using bounded sanitized facts.
- [x] Keep historical migration, API, benchmark, and release-decision documents
      immutable and labeled as historical evidence.
- [x] Use `EXAMPLE_` and `.example.invalid` placeholders in copyable examples.
- [x] Regenerate configuration reference only when property metadata changes.
- [x] Run configuration metadata, example-property, anchor, local-link,
      roadmap-link, public-version, and release-artifact documentation tests.
- [x] Run `git diff --check`.

Evidence:

- The annotation reference retains one parameter-role table for path, query,
  named/dynamic headers, idempotency keys, body, form field, and form file
  bindings. It now states explicitly which declaration facts fail at startup and
  which null, expanded-header, and dynamic-collision facts are checked per
  invocation before auth, body subscription, lifecycle attempts, or dispatch.
- Canonical URI authority/encoding, final-status and bodiless response,
  multipart global order/resource ownership, and stale-connection retirement
  wording remains tied to the V25 real-wire fixtures. The production checklist
  now links directly to the request grammar and multipart ownership sections.
- The support-bundle guide now has a bounded stale-connection recovery section
  that keeps the failed call and replacement call separate. Its sanitized fixture
  includes the capture window, protocol, downstream request count, connection
  sequence markers, pool samples, replay/idempotency/repeatability policy, and
  correlated per-record dispatch and terminal facts.
  Operations troubleshooting links to that capture procedure and continues to
  require the complete decoder exception rather than only the synthetic
  `GET /bad-request HTTP/1.0` text.
- README remains an index to the canonical annotation, multipart, streaming,
  test-helper, production, support-bundle, and operations guides. The mock guide
  continues to distinguish encoded in-process request evidence from protocol,
  framing, backpressure, cancellation, pool-reuse, and peer-reset evidence that
  requires a real connector.
- Copyable remote examples now use `.example.invalid` or `EXAMPLE_`
  placeholders. The documentation gate parses each URL and configured host in
  README and every public Markdown file under `docs/`, rejecting values outside
  reserved placeholders, loopback, and an explicit public-documentation
  allow-list. The generated `docs/configuration-properties.md` remains in sync
  with its metadata source after normalizing that description; no bindable
  property semantics changed. Immutable historical migration, API, benchmark,
  and release-decision artifacts were not otherwise modified.
- `DocumentationReleaseArtifactTest` and
  `ReactiveHttpClientConfigurationMetadataTest` passed 50 tests, covering
  metadata/example properties, generated reference parity, anchors, local and
  roadmap links, public versions, and release artifacts. The companion `OpenTelemetryConfigurationMetadataTest` passed four
  tests. `git diff --check` passed.

---

## Priority 13 - V25 Release Go/No-Go

### [x] 13.1 Select release scope and candidate version

- [x] Inventory delivered internal fixes, public additions, deprecations, and
      diagnostics schema effects.
- [x] Select a `3.4.x` patch for internal correctness/docs only, or `3.5.0` for a
      backward-compatible public addition.
- [x] Reject/defer binary or source incompatible changes and schema-v1 breaks.
- [x] Keep the reactor snapshot and published baseline separate until the release
      cut.
- [x] Record whether public performance claims require a promoted report.

### [x] 13.2 Assemble immutable release evidence

- [x] Run `mvn -B -ntp clean verify` from the release-prep tree.
- [x] Run strict root and module API compatibility from isolated Central-only
      repositories.
- [x] Run generation packaging, current/published consumer, supported matrix,
      request/framing/multipart/pool composition, AOT/native, and documentation
      gates.
- [x] Verify complete candidate parent, starter, test-helper, and OTel POM,
      binary, source, and Javadoc artifacts.
- [x] Re-run every reproducible release gate from one clean immutable commit.
- [x] Cite a clean promoted benchmark report or keep changelog wording
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

- V25 is selected as the backward-compatible `3.5.0` minor candidate. Internal
  correctness work covers request declarations and URI construction, terminal
  status/framing, multipart ownership/order, subscription-local reporting,
  stale pooled-connection recovery, and sanitized diagnostics/support evidence.
- The public additions are confined to test-helper multipart records/assertions,
  caller-supplied `MethodMetadataCache` support, and additive validation-cache
  methods already covered by strict japicmp. No public removals, deprecations,
  source incompatibilities, or binary incompatibilities were accepted.
- Diagnostics schema v1 retains its existing key/type contract, deterministic
  ordering, nullable unknown semantics, and size/cardinality bounds. The new
  support-bundle fixtures are documentation evidence, not schema-v1 fields.
- The release cut moves reactor-only coordinates to final `3.5.0`;
  `latest.published.version` and `api.compatibility.baseline.version` remain
  `3.4.0`. Preparing the candidate does not publish or move any baseline.
- V25 makes no public numerical performance or allocation claim. Target-only JMH
  evidence remains release-review input, so no promoted `3.5.0` benchmark report
  is required.
- Froze the final release-prep tree at clean commit
  `c0140340c9a49a51ef32cc0739f9663850c0cf62`. From that commit,
  `mvn -B -ntp -s .mvn/maven-central-settings.xml clean verify` passed 1,016
  starter, 48 test-helper, and 43 OTel tests, including request-target, framing,
  multipart, stale-pool, retry/redirect/auth composition, diagnostics,
  documentation, and AOT coverage.
- Passed the Java 21 supported matrix for Spring Boot `4.0.0` and `4.1.0`,
  including assembled consumers, optional integrations, dependency captures,
  and strict root API comparisons against Central-resolved `3.4.0`. The separate
  starter-module japicmp lane and API/published-baseline contamination fixtures
  also passed. Evidence is under
  `target/release-evidence/v25/priority13/matrix/` and
  `target/release-evidence/v25/priority13/api-starter-3.4.0/`.
- Passed isolated current-candidate and published-`3.4.0` assembled-consumer
  verification. Their dependency trees, classpaths, artifact hashes, Central
  markers, and clean-commit provenance are under
  `target/release-evidence/current-consumer/current-3.5.0/` and
  `target/release-evidence/published-consumer/published-3.4.0/`.
- Built the complete candidate attachment set with the release profile, verified
  generation packaging, and recorded 13 parent/module POM, binary, source, and
  Javadoc SHA-256 values under
  `target/release-evidence/v25/priority13/candidate-artifacts/`. Local GPG
  signing remains part of the publication workflow in 13.3.
- GraalVM `25.0.3` compiled and executed the assembled `3.5.0` native fixture;
  binary hash and provenance are under
  `target/release-evidence/v25/priority13/native/`. Benchmark smoke also passed
  from the immutable commit; its target-only checksum/provenance records that no
  report promotion is required because the changelog remains non-numerical.
  Section 13.3 remains pending.

## Completion Rule

V25 is complete only when every checked behavior has evidence at the layer that
owns it. Startup and mock tests cannot prove request-target bytes, multipart
framing, resource closure, pooled recovery, or protocol parser isolation.
Internal state extraction must remain behavior-neutral under characterization,
compatibility, and allocation evidence. Check only the release-decision branch
that actually occurred; the unchecked mutually exclusive branch remains
historical context after V25 is archived.
