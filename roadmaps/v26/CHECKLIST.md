# Reactive HTTP Client - Roadmap V26 Execution Checklist

Companion to [`ROADMAP.md`](ROADMAP.md). Execute priorities in order unless a
confirmed correctness or release blocker requires reordering. Check an item only
after implementation and verification evidence is recorded under that priority.
Generated evidence belongs under `target/release-evidence/v26/` unless a
promoted, versioned artifact is explicitly required.

---

## Priority 1 - Post-`3.5.0` Baseline and V26 Scope Integrity

### [x] 1.1 Align development and published-release lanes

- [x] Keep root/module and reactor-only fixture coordinates on
      `3.6.0-SNAPSHOT`.
- [x] Keep public dependency snippets and `latest.published.version` on
      published `3.5.0`.
- [x] Keep API compatibility, published-consumer, and benchmark baselines on
      published `3.5.0`.
- [x] Preserve root and module guards that reject a baseline equal to the
      current reactor version.
- [x] Keep V26 as the only active roadmap and preserve completed V1-V25 records.

### [x] 1.2 Prove published `3.5.0` provenance

- [x] Resolve the parent POM plus starter, test-helper, and OTel
      POM/JAR/source/Javadoc artifacts from a previously absent Central-only
      repository.
- [x] Require Maven Central remote markers and record SHA-256 values for every
      required artifact.
- [x] Run strict root japicmp against published `3.5.0` from an isolated
      repository.
- [x] Run strict starter-module japicmp against published `3.5.0` from a
      separate isolated repository.
- [x] Run published-baseline fixtures for contamination, mixed versions, missing
      attachments, and self-comparison.

### [x] 1.3 Keep generated readiness honest

- [x] Report `3.6.0-SNAPSHOT` as snapshot development and `3.5.0` as the
      latest published/API baseline.
- [x] Keep candidate version and promotable benchmark output deferred until
      release preparation.
- [x] Include unresolved compatibility, native, consumer, benchmark, and
      publication work in the release-readiness manifest.
- [x] Run release-documentation tests and `git diff --check`.

Evidence:

- Verified root/module and reactor-only fixture coordinates at
  `3.6.0-SNAPSHOT`, public snippets and published baselines at `3.5.0`, the
  root/module self-comparison guards, and V26 as the sole active roadmap.
- Resolved the published parent plus starter, test-helper, and OTel
  POM/JAR/sources/Javadoc artifacts from a fresh Central-only repository;
  recorded 13 SHA-256 entries and Maven Central remote markers under
  `target/release-evidence/v26/published-baselines/release-artifacts-3.5.0/`.
- Passed strict root and starter-module japicmp from separate isolated `3.5.0`
  repositories and recorded provenance under the matching V26 `api-root-3.5.0/` and `api-starter-3.5.0/`
  evidence directories.
- Passed published-baseline provenance fixtures for local contamination, mixed
  versions, missing POM/source/Javadoc artifacts, mismatched POM/JAR versions,
  and root/module self-comparison; API compatibility fixtures also passed.
- `mvn -B -ntp -pl reactive-http-client-starter -Dtest=DocumentationReleaseArtifactTest test`
  passed and regenerated snapshot-development readiness with candidate/report
  promotion deferred and compatibility, native, consumer, benchmark, and
  publication work still visible as pending.
- `bash -n scripts/verify-published-baseline-fixtures.sh`,
  `bash -n scripts/verify-api-compatibility-fixtures.sh`, readiness assertions,
  and `git diff --check` passed.

---

## Priority 2 - Logical-Call Duration Foundation

### [x] 2.1 Reproduce every zero-origin duration path

- [x] Add a deterministic open-circuit test that terminates before the
      request-attempt publisher subscribes and first demonstrates the
      epoch-sized duration defect.
- [x] Cover exhausted rate limiter and saturated bulkhead rejection before the
      first attempt.
- [x] Cover cancellation, auth failure, request serialization failure, and a
      custom-filter failure before transport dispatch.
- [x] Assert `attemptCount=0`, no status, no response headers, no request URL,
      `ErrorCategory.RESILIENCE_ERROR` where applicable, and no invented
      transport failure stage.
- [x] Inventory every use of wall-clock start/duration in terminal state,
      exchange logging, observer events, logical-call timeout, and OTel.

### [x] 2.2 Establish one monotonic subscription clock

- [x] Start timing once for each subscription to the public returned
      `Mono`/`Flux`, before logical timeout and resilience admission.
- [x] Store monotonic elapsed-time state separately from any epoch timestamp
      needed for tracing.
- [x] Remove elapsed calculations that subtract an uninitialized zero or depend
      on wall-clock adjustments.
- [x] Preserve one fresh clock per independent subscription to the same cold
      publisher.
- [x] Include rate-limiter/bulkhead admission and retry delay once in the
      logical-call duration without changing per-attempt response timeouts.
- [x] Do not cap valid slow calls as a substitute for correct initialization.

### [x] 2.3 Preserve terminal timing boundaries

- [x] Verify unary `Mono<T>` duration reaches value, empty completion, error, or
      cancellation.
- [x] Verify direct `Flux<T>` duration reaches stream completion, error, or
      cancellation.
- [x] Keep `Mono<ResponseEntity<Flux<DataBuffer>>>` timing at outer envelope
      delivery; later inner-body consumption remains owned by its subscriber.
- [x] Derive observer, exchange-log, Micrometer, and OTel durations from one
      immutable terminal snapshot within a bounded tolerance.
- [x] Keep lifecycle callbacks on the same one-terminal boundary without adding
      a duration field to their public context.
- [x] Run focused subscription-state, timeout, resilience-composition, streaming,
      exchange-log, Micrometer, and OTel tests.

Evidence:

- Reproduced the former epoch-sized duration on open-circuit, exhausted-rate-limiter,
  and saturated-bulkhead rejection before request-attempt subscription; all now
  report one finite `RESILIENCE_ERROR` terminal with `attemptCount=0`, no status,
  response headers, request URL, or invented transport stage.
- Added zero-attempt cancellation during rate-limiter admission and subscribed
  pre-dispatch auth, serialization, URI, and custom-filter failure coverage.
  Cancellation no longer fabricates lifecycle attempt `1`; subscribed failures
  that enter request preparation retain attempt `1`.
- `SubscriptionReportingState` now creates one `System.nanoTime()` clock per public
  cold-publisher subscription and freezes elapsed milliseconds in its immutable
  terminal snapshot. Exchange logging and observer reporting consume that exact
  value; Micrometer records it and OTel derives its span interval from the event.
- Revalidated unary value, empty, error, and cancellation boundaries; direct Flux
  completion, error, and cancellation; retry delay and rate-limit admission; and
  streaming-envelope completion before caller-owned inner-body consumption.
- `mvn -B -ntp -pl reactive-http-client-starter -Dtest=SubscriptionReportingStateTest,SubscriptionLocalReportingStateTest,DiagnosticContextContractTest,ReactiveHttpClientLifecycleHookTest,ResilienceOperatorCompositionContractTest,ReactiveHttpClientTimeoutTerminalStateContractTest,StreamingResponseTest test`
  passed with 64 tests.
- `mvn -B -ntp -pl reactive-http-client-starter test` passed with 1,022 tests.
- `mvn -B -ntp -pl reactive-http-client-otel -am -Dtest=OpenTelemetryHttpClientObserverTest -Dsurefire.failIfNoSpecifiedTests=false test`
  passed with 25 OTel tests.

---

## Priority 3 - Micrometer Timer and Export Contract

### [ ] 3.1 Freeze main timer and latency-histogram exports

- [ ] Add a Prometheus-compatible scrape fixture for the default main timer.
- [ ] Verify `_count`, `_sum`, and `_max` values and their seconds base unit
      for success, HTTP error, timeout, cancellation, and open-circuit outcomes.
- [ ] Prove the main timer and opt-in latency timer never record epoch-sized or
      negative values.
- [ ] Verify the main timer retains final status/error tags while the latency
      timer retains only its documented low-cardinality tags.
- [ ] Verify configured SLO boundaries and histogram buckets without adding
      status, exception, category, or failure-stage tags.
- [ ] Characterize Micrometer time-window maximum expiry/reset behavior in tests
      where the registry permits deterministic clock control.

### [ ] 3.2 Correct the attempts-summary contract

- [ ] Verify the default attempts summary exports only the distribution
      statistics supported by its current registry configuration; do not claim
      p95/p99.
- [ ] Keep `publishPercentiles(0.95, 0.99)` disabled by default because
      per-instance client-side quantiles are not aggregable across replicas.
- [ ] Document `0` as pre-subscription rejection, `1` as one attempt
      regardless of outcome, and `>1` as retry resubscription.
- [ ] Remove the unsupported “p95 above 1” guidance from the default meter
      contract.
- [ ] Recommend Resilience4j retry counters and explicitly labeled
      `attempts_sum / attempts_count` mean-attempt queries for the default
      summary without presenting either as a percentile.
- [ ] Add an attempts histogram only if V26 records a separate opt-in property,
      bounded integer buckets, metadata, scrape tests, multi-instance
      `histogram_quantile` evidence, and overhead/cardinality review.

### [ ] 3.3 Inventory conditional meter creation

- [ ] Verify the main timer and attempts summary are recorded for every observer
      event accepted by the built-in observer.
- [ ] Verify request-size and response-size summaries are absent when size is
      unknown and record explicit zero where zero is known.
- [ ] Verify the separate latency timer exists only when histogram support is
      enabled.
- [ ] Keep custom metric-name behavior consistent across main, attempts, size,
      histogram, and health lookups.
- [ ] Add documentation assertions that prevent “four meters per exchange” and
      unsupported attempts-percentile wording from returning.

---

## Priority 4 - Opt-In Resilience Activation and Admission Semantics

### [ ] 4.1 Make every operator explicit by intent

- [ ] Add failing compatibility fixtures proving that `resilience.enabled=true`
      currently activates retry, rate limiter, circuit breaker, and bulkhead
      through their implicit `default` instance names.
- [ ] Keep `resilience.enabled` as the client-level master gate, but make
      client-level `retry`, `rate-limiter`, `circuit-breaker`, and `bulkhead`
      absent/disabled by default.
- [ ] Activate an operator only from a non-blank client-level instance property
      or the matching method annotation. Preserve explicit `default` as a valid
      instance name; do not infer activation from registry availability.
- [ ] Define and test method-level selection over client-level selection while
      retaining the master gate. A blank client-level property must not suppress
      an explicit method annotation.
- [ ] Keep `retry-methods` as Retry eligibility rather than activation, and keep
      strict unsafe-retry validation dormant when Retry is not effectively
      selected or cannot make another attempt.
- [ ] Prove `enabled: true` alone applies no operator for `Mono` and `Flux`.
- [ ] Prove retry-only, rate-limiter-only, circuit-breaker-only, and
      bulkhead-only configurations apply exactly the selected operator and leave
      the other three absent.

### [ ] 4.2 Align every effective-policy surface

- [ ] Centralize the effective operator-selection rule used by invocation,
      startup validation/logging, `EffectiveHttpClientContractExporter`, and
      `ReactiveHttpClientDiagnosticsProvider` without creating operators or
      registries from diagnostics.
- [ ] Report unselected operators as `disabled`, selected but unavailable
      operators as `unavailable`, and unresolved lazy candidates as `unknown`
      where the diagnostics schema already permits it.
- [ ] Update configuration metadata defaults and descriptions so IDEs and
      generated references do not advertise implicit `default` activation.
- [ ] Update annotations, resilience docs, quick start, production examples,
      support bundles, mock helpers, and assembled consumers with retry-only and
      mixed explicit-selection examples.
- [ ] Add a migration table covering `enabled` alone, one explicitly named
      operator, explicit `default`, method annotations, blank values,
      `retry-methods`, and strict retry validation.
- [ ] Add documentation/configuration drift tests that reject a return to
      all-operators-on behavior or examples that imply registry presence is
      activation.

### [ ] 4.3 Prove each admission outcome

- [ ] Exercise open circuit, exhausted rate limiter, saturated zero-wait
      bulkhead, delayed rate-limit permission, and delayed bulkhead admission.
- [ ] Verify zero-attempt rejections record `http.status_code=NONE`,
      `outcome=UNKNOWN`, the concrete exception, and
      `error.category=RESILIENCE_ERROR`.
- [ ] Verify delayed admission is included in logical-call duration and does not
      increment subscription attempts.
- [ ] Verify Resilience4j's own operator meters remain separate from the
      starter's one-terminal-record metrics.
- [ ] Preserve the established
      `logical-call-timeout -> bulkhead -> circuit-breaker -> rate-limiter -> retry -> request-attempt`
      composition.

### [ ] 4.4 Preserve retry and hidden-replay boundaries

- [ ] Verify retry delay and each retry subscription contribute to one logical
      duration and final attempt count.
- [ ] Verify one-time auth replay and automatic redirect dispatch do not
      increment the subscription-attempt summary.
- [ ] Verify no metric or guide equates subscription attempts with downstream
      request count.
- [ ] Ensure prior attempt URL, status, headers, error, failure stage, and timing
      cannot leak into an outer terminal rejection.
- [ ] Cover cancellation during admission and retry delay with one terminal
      record and no late metric update.
- [ ] Run the full resilience, retry/redirect/auth composition, idempotency, and
      body-repeatability suites.
- [ ] Record the change as SemVer-major release scope. Do not ship these
      semantics under `3.6.0`; either prepare a deliberate `4.0.0` candidate or
      defer the opt-in activation change intact.

---

## Priority 5 - Request and Response Size Semantics

### [ ] 5.1 Align String measurement with outbound encoding

- [ ] Add real-wire UTF-8 and explicit non-UTF-8 String request fixtures.
- [ ] Compare `HttpClientObserverEvent.requestBytes`, Micrometer summary, and
      OTel attribute with bytes observed by the server.
- [ ] Use the effective outbound charset for String measurement, or explicitly
      relabel the value as a UTF-8 estimate if exact alignment cannot be proved.
- [ ] Keep null at zero and `byte[]` at exact array length.
- [ ] Preserve auth signing and WebClient codec ownership; metrics must not
      independently re-encode the payload differently from the request path.

### [ ] 5.2 Preserve unknown and advertised-size boundaries

- [ ] Keep POJO, publisher, resource, multipart, and streaming request bodies
      unknown unless bytes already exist on the normal request path.
- [ ] Do not buffer, consume, reopen, or serialize a body solely for metrics.
- [ ] Keep response size equal to surviving advertised `Content-Length`, not
      decoded or consumed bytes.
- [ ] Cover chunked, compressed, malformed-length, streaming-cancellation,
      bodiless, and `ResponseEntity` outcomes.
- [ ] Verify Micrometer and OTel publish the same known value or both omit an
      unknown value.

---

## Priority 6 - Health Indicator Metric Parity

### [ ] 6.1 Align health detail schema and documentation

- [ ] Add `poolAcquireFailureCount` to the documented per-client field inventory
      and sample Actuator response.
- [ ] Verify field names, JSON types, bounds, status values, and reasons against
      `Boot4HttpClientHealthIndicator`.
- [ ] Update support-bundle fixtures and assertions to require the complete
      health detail shape without sensitive fields.
- [ ] Preserve the documented replacement bean name and conditional
      auto-configuration behavior.

### [ ] 6.2 Prove count-based probe semantics

- [ ] Verify health uses main-timer count plus error-category/failure-stage tags;
      max, sum, and histogram buckets cannot independently mark a client DOWN.
- [ ] Cover no samples, insufficient samples, threshold equality, above-threshold
      errors, and pool-acquire failures.
- [ ] Cover registry reset and meter removal/recreation without negative deltas.
- [ ] Cover multiple tagged timers for one client and the configured custom
      metric name.
- [ ] Retain client-count/name bounds and sanitized output.

---

## Priority 7 - OpenTelemetry Logical-Call Contract

### [ ] 7.1 Align span timing and terminal semantics

- [ ] Document and test one terminal `CLIENT` span per logical client call, not
      per retry, redirect, auth replay, or transport dispatch.
- [ ] Verify open-circuit and other zero-attempt failures produce finite current
      start/end timestamps matching the shared logical duration.
- [ ] Verify status, `error.type`, `rhttp.failure.stage`, attempt count,
      byte attributes, and cardinality gates remain aligned with the observer
      event.
- [ ] Preserve response-envelope timing for streaming `ResponseEntity` bodies.
- [ ] Ensure observer failures remain isolated from business publishers.

### [ ] 7.2 Resolve body-option documentation drift

- [ ] Characterize exactly how `log-request-body` and `log-response-body`
      affect `HttpClientObserverEvent` for custom and composite observers.
- [ ] Confirm the built-in OTel observer emits no raw body, header, URL,
      exception-message, or stack-trace span events.
- [ ] Update property metadata, generated configuration reference, Javadocs, and
      public docs to describe the selected behavior.
- [ ] Do not add built-in body span events without a separately reviewed bounded
      redaction and size contract.
- [ ] Preserve OTel master/spans/propagation switches, inbound extraction,
      outbound injection, and caller-header precedence.

---

## Priority 8 - Observability Documentation and Dashboard Recipes

### [ ] 8.1 Reconcile the canonical observability reference

- [ ] Rewrite the meter introduction to list always-recorded, conditionally
      recorded, and opt-in meters separately.
- [ ] Correct logical-call duration, zero/one/multiple attempt, hidden replay,
      and wire-dispatch wording.
- [ ] Correct attempts percentile availability and distinguish average,
      percentile, and retry-event queries.
- [ ] Add timer base-unit and time-window `_max` semantics.
- [ ] Correct health detail fields, OTel logical-call span wording, and body
      option behavior.
- [ ] Retain verified API-name, tag, response-size, diagnostics, pool-gauge, and
      propagation contracts.

### [ ] 8.2 Add unit-safe operational queries

- [ ] Add PromQL examples for request rate and error ratio from the main timer.
- [ ] Add a zero-attempt resilience rejection query only if the exported summary
      permits it truthfully; otherwise direct operators to Resilience4j counters.
- [ ] Add p95/p99 latency queries only for the opt-in latency histogram.
- [ ] Add average-attempt and pool-pressure queries with explicit limitations.
- [ ] State seconds versus milliseconds on every duration query and avoid
      interpreting `_max` as a permanent maximum.
- [ ] Distinguish starter logical-call, Resilience4j operator, Reactor Netty
      transport, and OTel telemetry in one table.

### [ ] 8.3 Prevent documentation drift

- [ ] Cross-check error handling, resilience, cardinality, support bundles,
      production checklist, configuration reference, and operations
      troubleshooting.
- [ ] Add focused documentation assertions for conditional meters, attempts
      semantics, percentile availability, health fields, body options, and units.
- [ ] Keep local links, anchors, placeholders, generated metadata, and public
      version snippets valid.
- [ ] Run documentation and configuration-metadata tests plus
      `git diff --check`.

---

## Priority 9 - Mock, Consumer, and Support-Bundle Parity

### [ ] 9.1 Extend mock observability assertions

- [ ] Exercise configured open-circuit/pre-subscription rejection through
      `MockReactiveHttpClient`.
- [ ] Assert one lifecycle terminal callback and one observer/exchange-log
      record; compare attempt, status, category, and failure-stage facts where
      each contract exposes them, and compare duration only between observer and
      exchange logging.
- [ ] Add helper assertions only when they remove repeated test logic and match
      production terminology.
- [ ] Keep mock evidence labeled no-network and avoid transport/pool/scrape
      claims.
- [ ] Preserve custom observer, lifecycle ordering, auth, inherited client, and
      custom logger support.

### [ ] 9.2 Extend assembled-consumer evidence

- [ ] Add success, HTTP error, and open-circuit calls to the current Boot 4
      consumer fixture.
- [ ] Inspect or scrape main timer duration and attempts from the assembled app.
- [ ] Verify corrected health details and OTel span timing.
- [ ] Reject reactor-classpath leakage and retain effective POM, dependency tree,
      classpath, test reports, and stage/provenance evidence.
- [ ] Run the published `3.5.0` consumer lane unchanged as the compatibility
      baseline.

### [ ] 9.3 Update bounded support evidence

- [ ] Add units and structural fields needed to distinguish fast resilience
      rejection, transport failure, and downstream HTTP failure.
- [ ] Keep support fixtures bounded, deterministic, sanitized, and free of raw
      URLs, headers, credentials, bodies, and exception messages.
- [ ] Validate required false, zero, and null fields by presence and JSON type.

---

## Priority 10 - Observability Overhead Re-Audit

### [ ] 10.1 Preserve benchmark fairness

- [ ] Re-run default no-network invocation and Micrometer observer rows against
      current `3.6.0-SNAPSHOT` and published `3.5.0` under equivalent
      dependencies and environment metadata.
- [ ] Confirm the diagnostics-disabled unary path does not allocate reporting
      state for inactive features.
- [ ] Measure any new Prometheus/histogram path separately from default
      observation.
- [ ] Add a pre-subscription rejection benchmark only if it answers a concrete
      allocation/latency question; label it no-network.
- [ ] Keep smoke output non-promotable and release output tied to a clean commit.

### [ ] 10.2 Review movement without hard gates

- [ ] Compare current and baseline throughput, average/sample latency, and
      allocation for named equivalent scenarios.
- [ ] Investigate material movement using the documented review thresholds.
- [ ] Optimize only measured regressions attributable to V26 changes.
- [ ] Record expected costs for any opt-in histogram or scrape fixture.
- [ ] Promote a versioned report only if release notes make numerical
      performance claims.

---

## Priority 11 - Dependency, API, AOT, and Native Evidence

### [ ] 11.1 Review supported dependency rows

- [ ] Review Spring Boot, Spring Framework, Reactor Netty, Micrometer,
      Resilience4j, OTel, Jackson, and native-build versions.
- [ ] Run the supported minimum/forward matrix without mixing Boot generations.
- [ ] Preserve evidence incrementally when a later matrix row fails.
- [ ] Keep published-baseline resolution isolated from locally installed reactor
      artifacts.

### [ ] 11.2 Preserve public API compatibility

- [ ] Inventory touched public observer, event, health, diagnostics, and
      test-helper types.
- [ ] Prefer package-private timing changes and avoid adding a public clock or
      mutable timing model.
- [ ] Add any intentional public addition to japicmp includes and documentation.
- [ ] Run strict root and starter-module API compatibility against published
      `3.5.0`.
- [ ] Run API and published-baseline contamination fixtures.

### [ ] 11.3 Revalidate AOT and native behavior

- [ ] Run AOT generation for direct, inherited, generic, and `@ApiRef` clients
      with Micrometer and the diagnostics endpoint present.
- [ ] Compile and execute the native smoke fixture with the corrected
      zero-attempt terminal path.
- [ ] Verify no reflection fallback or missing resource hint is introduced.
- [ ] Record clean commit, GraalVM/JDK versions, binary hash, and evidence path.
- [ ] Run generation packaging, full reactor verification, and
      `git diff --check`.

---

## Priority 12 - V26 Release Go/No-Go

### [ ] 12.1 Select release scope and candidate version

- [ ] Inventory delivered timing fixes, metric/config additions, documentation
      corrections, public API effects, and diagnostics schema effects.
- [ ] Select a `3.5.x` maintenance release only if the work is internal
      correctness/documentation and the maintenance lane is intentionally used.
- [ ] Select `3.6.0` for backward-compatible metric, configuration, diagnostics,
      or test-helper additions on the current reactor line.
- [ ] Select `4.0.0` if opt-in resilience activation is delivered, with a
      migration guide and explicit evidence that implicit `default` operators
      no longer activate.
- [ ] Reject/defer binary/source incompatible changes and diagnostics schema-v1
      breaks.
- [ ] Record whether numerical performance claims require a promoted benchmark
      report.

### [ ] 12.2 Assemble immutable release evidence

- [ ] Run clean full reactor verification from the release-prep tree.
- [ ] Run strict root/module API compatibility from isolated Central-only
      repositories.
- [ ] Run generation packaging, current/published consumer, supported matrix,
      duration/resilience/metrics/health/OTel composition, AOT/native, and
      documentation gates.
- [ ] Verify complete candidate parent, starter, test-helper, and OTel POM,
      binary, source, and Javadoc artifacts.
- [ ] Re-run every release gate from one clean immutable commit.
- [ ] Cite a clean promoted benchmark report or keep changelog wording
      non-numerical.

### [ ] 12.3 Record the mutually exclusive decision

- [ ] **Go path:** tag and publish the selected version from the matching
      immutable commit.
- [ ] **Go path:** verify every Maven Central artifact and assembled published
      consumer before moving public/API/consumer/benchmark baselines.
- [ ] **Go path:** open the next snapshot line and archive V26 only after Central
      verification succeeds.
- [ ] **No-go path:** publish nothing and record each blocker, reproduction, and
      retained evidence path.
- [ ] Update roadmap/checklist/index/changelog status to match the selected path.
- [ ] Run final release-documentation tests and `git diff --check`.

## Completion Rule

V26 is complete only when every checked behavior has evidence at the layer that
owns it. Unit tests cannot prove Prometheus scrape units, multi-instance
histogram aggregation, assembled-consumer behavior, or native execution. A
duration fix must start at logical subscription, use monotonic elapsed time, and
cover zero-attempt resilience rejection without adding arbitrary caps. Check
only the release-decision branch that actually occurs; the unchecked mutually
exclusive branch remains historical context after V26 is archived.
