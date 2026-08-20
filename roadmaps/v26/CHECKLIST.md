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

### [x] 3.1 Freeze main timer and latency-histogram exports

- [x] Add a Prometheus-compatible scrape fixture for the default main timer.
- [x] Verify `_count`, `_sum`, and `_max` values and their seconds base unit
      for success, HTTP error, timeout, cancellation, and open-circuit outcomes.
- [x] Prove the main timer and opt-in latency timer never record epoch-sized or
      negative values.
- [x] Verify the main timer retains final status/error tags while the latency
      timer retains only its documented low-cardinality tags.
- [x] Verify configured SLO boundaries and histogram buckets without adding
      status, exception, category, or failure-stage tags.
- [x] Characterize Micrometer time-window maximum expiry/reset behavior in tests
      where the registry permits deterministic clock control.

### [x] 3.2 Correct the attempts-summary contract

- [x] Verify the default attempts summary exports only the distribution
      statistics supported by its current registry configuration; do not claim
      p95/p99.
- [x] Keep `publishPercentiles(0.95, 0.99)` disabled by default because
      per-instance client-side quantiles are not aggregable across replicas.
- [x] Document `0` as pre-subscription rejection, `1` as one attempt
      regardless of outcome, and `>1` as retry resubscription.
- [x] Remove the unsupported “p95 above 1” guidance from the default meter
      contract.
- [x] Recommend Resilience4j retry counters and explicitly labeled
      `attempts_sum / attempts_count` mean-attempt queries for the default
      summary without presenting either as a percentile.
- [x] Keep the attempts histogram absent because V26 adds no opt-in property,
      bounded integer buckets, metadata, multi-instance `histogram_quantile`
      evidence, or reviewed overhead/cardinality contract.

### [x] 3.3 Inventory conditional meter creation

- [x] Verify the main timer and attempts summary are recorded for every observer
      event accepted by the built-in observer.
- [x] Verify request-size and response-size summaries are absent when size is
      unknown and record explicit zero where zero is known.
- [x] Verify the separate latency timer exists only when histogram support is
      enabled.
- [x] Keep custom metric-name behavior consistent across main, attempts, size,
      histogram, and health lookups.
- [x] Add documentation assertions that prevent “four meters per exchange” and
      unsupported attempts-percentile wording from returning.

Evidence:

- `MicrometerPrometheusExportContractTest` covers all five terminal outcomes,
  seconds-based timer count/sum/max samples, opt-in SLO buckets and tag shape,
  deterministic time-window maximum expiry, attempts-summary statistics without
  quantiles or buckets, conditional size/latency meters, and custom-name health
  lookup parity.
- The Priority 2 integration suite supplies real nonnegative, finite
  logical-call durations for zero-attempt resilience outcomes; the scrape fixture
  also bounds every exported terminal duration away from epoch-sized values.
- `DocumentationReleaseArtifactTest.observabilityMeterDocsMatchPrometheusExportContract`
  prevents the removed fixed-meter-count, success-only attempt, and unsupported
  attempts-percentile claims from returning.
- `mvn -B -ntp -s .mvn/maven-central-settings.xml -pl reactive-http-client-starter
  -Dtest=MicrometerPrometheusExportContractTest,MicrometerHttpClientObserverTest,Boot4HttpClientHealthIndicatorTest,DocumentationReleaseArtifactTest
  test` passed with 86 tests.
- `mvn -B -ntp -s .mvn/maven-central-settings.xml
  -pl reactive-http-client-starter test` passed with 1,028 tests.
- This priority adds a test-scoped Prometheus registry only; production observer
  configuration and default meter creation remain unchanged.

---

## Priority 4 - Resilience Admission and Attempt Semantics

### [x] 4.1 Prove each admission outcome

- [x] Exercise open circuit, exhausted rate limiter, saturated zero-wait
      bulkhead, delayed rate-limit permission, and delayed bulkhead admission.
- [x] Verify zero-attempt rejections record `http.status_code=NONE`,
      `outcome=UNKNOWN`, the concrete exception, and
      `error.category=RESILIENCE_ERROR`.
- [x] Verify delayed admission is included in logical-call duration and does not
      increment subscription attempts.
- [x] Verify Resilience4j's own operator meters remain separate from the
      starter's one-terminal-record metrics.
- [x] Preserve the established
      `logical-call-timeout -> bulkhead -> circuit-breaker -> rate-limiter -> retry -> request-attempt`
      composition.

### [x] 4.2 Preserve retry and hidden-replay boundaries

- [x] Verify retry delay and each retry subscription contribute to one logical
      duration and final attempt count.
- [x] Verify one-time auth replay and automatic redirect dispatch do not
      increment the subscription-attempt summary.
- [x] Verify no metric or guide equates subscription attempts with downstream
      request count.
- [x] Ensure prior attempt URL, status, headers, error, failure stage, and timing
      cannot leak into an outer terminal rejection.
- [x] Cover cancellation during admission and retry delay with one terminal
      record and no late metric update.
- [x] Run the full resilience, retry/redirect/auth composition, idempotency, and
      body-repeatability suites.

Evidence:

- `ResilienceOperatorCompositionContractTest` now uses real Resilience4j
  registries for open-circuit, exhausted-rate-limiter, saturated zero-wait
  bulkhead, delayed permission, and delayed bulkhead admission. The three
  immediate rejections retain no status, URL, headers, or failure stage and emit
  one `RESILIENCE_ERROR` terminal with attempt `0`.
- Delayed admissions are included in the shared logical duration while the
  successful request remains attempt `1`. Retry exhaustion still records three
  request subscriptions under one outer Bulkhead, CircuitBreaker, and
  RateLimiter admission.
- Caller cancellation during rate-limit admission and Retry delay emits one
  terminal record; waits beyond the admission/retry window prove no late source
  subscription or metric update occurs.
- `ReactiveHttpClientAutoConfigurationTest` binds all four tagged Resilience4j
  meter families and the starter observer to one registry, proving
  `resilience4j.*` operator meters remain distinct from
  `reactive.http.client.requests*` logical-call meters.
- `RetryRedirectAuthReplayCompositionContractTest` retains one subscription
  attempt across auth replay and redirect dispatch, isolates concurrent outer
  subscriptions, and proves a terminal pre-dispatch auth rejection cannot reuse
  prior URL, status, headers, error, failure-stage, or timing evidence.
- `mvn -B -ntp -s .mvn/maven-central-settings.xml -pl reactive-http-client-starter
  -Dtest=ResilienceOperatorCompositionContractTest,RetryRedirectAuthReplayCompositionContractTest,ReactiveClientInvocationHandlerRetrySafetyTest,ReactiveClientInvocationHandlerRetryMethodsTest,ResilienceOperatorApplierTest,PerMethodResilienceTest,IdempotencyKeySupportTest,ReactiveHttpClientLifecycleHookTest,ReactiveHttpClientAutoConfigurationTest,DocumentationReleaseArtifactTest
  test` passed with 167 tests.
- `mvn -B -ntp -s .mvn/maven-central-settings.xml
  -pl reactive-http-client-starter test` passed with 1,036 tests.
- No production operator composition or metric creation changed in this priority.

---

## Priority 5 - Request and Response Size Semantics

### [x] 5.1 Align String measurement with outbound encoding

- [x] Add real-wire UTF-8 and explicit non-UTF-8 String request fixtures.
- [x] Compare `HttpClientObserverEvent.requestBytes`, Micrometer summary, and
      OTel attribute with bytes observed by the server.
- [x] Use the effective outbound charset for String measurement, or explicitly
      relabel the value as a UTF-8 estimate if exact alignment cannot be proved.
- [x] Keep null at zero and `byte[]` at exact array length.
- [x] Preserve auth signing and WebClient codec ownership; metrics must not
      independently re-encode the payload differently from the request path.

### [x] 5.2 Preserve unknown and advertised-size boundaries

- [x] Keep POJO, publisher, resource, multipart, and streaming request bodies
      unknown unless bytes already exist on the normal request path.
- [x] Do not buffer, consume, reopen, or serialize a body solely for metrics.
- [x] Keep response size equal to surviving advertised `Content-Length`, not
      decoded or consumed bytes.
- [x] Cover chunked, compressed, malformed-length, streaming-cancellation,
      bodiless, and `ResponseEntity` outcomes.
- [x] Verify Micrometer and OTel publish the same known value or both omit an
      unknown value.

Evidence:

- `ReactiveClientInvocationHandler` now measures String bodies with the charset
  selected from the final outbound `Content-Type` after auth and
  client-customizer filters, falling back to UTF-8, while null and `byte[]`
  retain exact zero/array-length semantics. Other request shapes, including
  multipart, remain unknown without metrics-only
  serialization, subscription, reopening, aggregation, or buffering.
- `RequestResponseSizeObservabilityContractTest` uses a real loopback server to
  compare wire bytes with observer events, Micrometer summaries, and OTel span
  attributes after direct, auth, and client-customizer header resolution for
  UTF-8, ISO-8859-1, null, `byte[]`, POJO, publisher, resource,
  stream, multipart, fixed-length, chunked, compressed, `ResponseEntity`, and
  cancelled streaming outcomes. Its ownership assertions prove opaque request
  bodies are consumed only by the normal request path.
- `HttpResponseFramingContractTest` now preserves and verifies surviving
  advertised response lengths for malformed/truncated framing and unexpected
  bodiless responses.
- `mvn -B -ntp -s .mvn/maven-central-settings.xml
  -pl reactive-http-client-starter
  -Dtest=HttpResponseFramingContractTest,ReactiveHttpClientCompressionContractTest,StreamingResponseTest,StreamingUploadOwnershipTest,MultipartWireOwnershipContractTest,MicrometerHttpClientObserverTest,MicrometerPrometheusExportContractTest,DocumentationReleaseArtifactTest
  test` passed with 126 tests.
- `mvn -B -ntp -s .mvn/maven-central-settings.xml
  -pl reactive-http-client-otel -am test` passed with 1,037 starter tests and 47
  OTel tests.

---

## Priority 6 - Health Indicator Metric Parity

### [x] 6.1 Align health detail schema and documentation

- [x] Add `poolAcquireFailureCount` to the documented per-client field inventory
      and sample Actuator response.
- [x] Verify field names, JSON types, bounds, status values, and reasons against
      `Boot4HttpClientHealthIndicator`.
- [x] Update support-bundle fixtures and assertions to require the complete
      health detail shape without sensitive fields.
- [x] Preserve the documented replacement bean name and conditional
      auto-configuration behavior.

### [x] 6.2 Prove count-based probe semantics

- [x] Verify health uses main-timer count plus error-category/failure-stage tags;
      max, sum, and histogram buckets cannot independently mark a client DOWN.
- [x] Cover no samples, insufficient samples, threshold equality, above-threshold
      errors, and pool-acquire failures.
- [x] Cover registry reset and meter removal/recreation without negative deltas.
- [x] Cover multiple tagged timers for one client and the configured custom
      metric name.
- [x] Retain client-count/name bounds and sanitized output.

Evidence:

- `Boot4HttpClientHealthIndicator` now snapshots each tagged timer generation.
  Stable meters use probe-to-probe count deltas; removed/recreated meters start
  from their own current counts, preventing negative or cross-generation deltas.
- `Boot4HttpClientHealthIndicatorTest` covers no samples, insufficient samples,
  threshold equality, above-threshold errors, pool-acquire failures, multiple
  tagged series, custom metric names, registry recreation, duration/histogram
  independence, deterministic ordering, client/name bounds, and UTF-8 output size.
- `docs/08-observability.md` and `docs/26-support-bundles.md` now document the
  complete field/type/status/reason contract and link the sanitized
  `docs/fixtures/support-bundle-health.json` fixture. Release-documentation tests
  require the complete shape and reject sensitive values.
- `Boot4AutoConfigurationTest` proves the health contributor remains conditional
  on `MeterRegistry`, honors `health.enabled=false`, and backs off for a
  replacement named `reactiveHttpClientHealthIndicator`.
- The focused health, Prometheus, auto-configuration, and documentation suite
  passed with 70 tests. The full starter suite passed with 1,043 tests.

---

## Priority 7 - OpenTelemetry Logical-Call Contract

### [x] 7.1 Align span timing and terminal semantics

- [x] Document and test one terminal `CLIENT` span per logical client call, not
      per retry, redirect, auth replay, or transport dispatch.
- [x] Verify open-circuit and other zero-attempt failures produce finite current
      start/end timestamps matching the shared logical duration.
- [x] Verify status, `error.type`, `rhttp.failure.stage`, attempt count,
      byte attributes, and cardinality gates remain aligned with the observer
      event.
- [x] Preserve response-envelope timing for streaming `ResponseEntity` bodies.
- [x] Ensure observer failures remain isolated from business publishers.

### [x] 7.2 Resolve body-option documentation drift

- [x] Characterize exactly how `log-request-body` and `log-response-body`
      affect `HttpClientObserverEvent` for custom and composite observers.
- [x] Confirm the built-in OTel observer emits no raw body, header, URL,
      exception-message, or stack-trace span events.
- [x] Update property metadata, generated configuration reference, Javadocs, and
      public docs to describe the selected behavior.
- [x] Do not add built-in body span events without a separately reviewed bounded
      redaction and size contract.
- [x] Preserve OTel master/spans/propagation switches, inbound extraction,
      outbound injection, and caller-header precedence.

Evidence:

- `OpenTelemetryHttpClientObserverTest` proves one `CLIENT` span for a terminal
  event with multiple subscription attempts, current finite zero-attempt
  timestamps within the documented one-millisecond conversion tolerance,
  terminal status/error/stage/attempt/byte/cardinality attributes, structural-only
  exception events, and observer-failure isolation.
- `RequestResponseSizeObservabilityContractTest` uses a real starter-built client
  to prove streaming `ResponseEntity<Flux<DataBuffer>>` spans end at envelope
  completion and remain single after inner-body consumption. It also proves the
  default body fields are null, opt-in fields reach custom/composite observers,
  and the built-in OTel span never contains those payloads.
- `ReactiveHttpClientProperties`, configuration metadata, generated property
  reference, observer Javadocs, and public observability/cardinality/context docs
  now describe the body settings as custom-observer event gates rather than span
  events. Documentation tests reject the former wording.
- Existing auto-configuration and propagation tests retain the OTel master, spans,
  and propagation switches, inbound extraction, outbound injection, and
  caller-header precedence. The focused starter/OTel contract run passed 108
  tests. The full starter suite passed 1,045 tests and the full OTel suite passed
  52 tests.

---

## Priority 8 - Observability Documentation and Dashboard Recipes

### [x] 8.1 Reconcile the canonical observability reference

- [x] Rewrite the meter introduction to list always-recorded, conditionally
      recorded, and opt-in meters separately.
- [x] Correct logical-call duration, zero/one/multiple attempt, hidden replay,
      and wire-dispatch wording.
- [x] Correct attempts percentile availability and distinguish average,
      percentile, and retry-event queries.
- [x] Add timer base-unit and time-window `_max` semantics.
- [x] Correct health detail fields, OTel logical-call span wording, and body
      option behavior.
- [x] Retain verified API-name, tag, response-size, diagnostics, pool-gauge, and
      propagation contracts.

### [x] 8.2 Add unit-safe operational queries

- [x] Add PromQL examples for request rate and error ratio from the main timer.
- [x] Add a zero-attempt resilience rejection query only if the exported summary
      permits it truthfully; otherwise direct operators to Resilience4j counters.
- [x] Add p95/p99 latency queries only for the opt-in latency histogram.
- [x] Add average-attempt and pool-pressure queries with explicit limitations.
- [x] State seconds versus milliseconds on every duration query and avoid
      interpreting `_max` as a permanent maximum.
- [x] Distinguish starter logical-call, Resilience4j operator, Reactor Netty
      transport, and OTel telemetry in one table.

### [x] 8.3 Prevent documentation drift

- [x] Cross-check error handling, resilience, cardinality, support bundles,
      production checklist, configuration reference, and operations
      troubleshooting.
- [x] Add focused documentation assertions for conditional meters, attempts
      semantics, percentile availability, health fields, body options, and units.
- [x] Keep local links, anchors, placeholders, generated metadata, and public
      version snippets valid.
- [x] Run documentation and configuration-metadata tests plus
      `git diff --check`.

Evidence:

- `docs/08-observability.md` now separates always-recorded logical-call meters,
  conditional known-size summaries, and the opt-in latency histogram from
  independently activated protocol-aware pool gauges. It preserves API-name, tag, response-size,
  diagnostics, OTel propagation, and body-gate contracts.
- Unit-safe PromQL recipes cover logical-call request rate, zero-preserving error
  ratio, starter-timer admission rejections, histogram-backed p95/p99 in seconds,
  average subscription attempts, and H1/H2 pending-pool counts. The guide warns
  about highest-bucket saturation and explains why attempts count/sum cannot
  identify zero-attempt calls.
- The telemetry ownership table separates starter logical calls, Resilience4j
  operators, Reactor Netty transport meters, and one terminal OTel span.
- Error handling, resilience, production, cardinality, diagnostics, support
  bundle, and operations guides now link back to the canonical recipes and use
  the same health, unit, attempt, body, and cardinality terminology.
- `DocumentationReleaseArtifactTest` guards meter availability, units, queries,
  unsupported attempts percentiles, health inputs, body gates, cross-guide links,
  and local Markdown anchors. The focused documentation, metadata, Micrometer,
  and Prometheus contract suite passed 96 tests; `git diff --check` passed.

---

## Priority 9 - Mock, Consumer, and Support-Bundle Parity

### [x] 9.1 Extend mock observability assertions

- [x] Exercise configured open-circuit/pre-subscription rejection through
      `MockReactiveHttpClient`.
- [x] Assert one lifecycle terminal callback and one observer/exchange-log
      record; compare attempt, status, category, and failure-stage facts where
      each contract exposes them, and compare duration only between observer and
      exchange logging.
- [x] Add helper assertions only when they remove repeated test logic and match
      production terminology.
- [x] Keep mock evidence labeled no-network and avoid transport/pool/scrape
      claims.
- [x] Preserve custom observer, lifecycle ordering, auth, inherited client, and
      custom logger support.

### [x] 9.2 Extend assembled-consumer evidence

- [x] Add success, HTTP error, and open-circuit calls to the current Boot 4
      consumer fixture.
- [x] Inspect or scrape main timer duration and attempts from the assembled app.
- [x] Verify corrected health details and OTel span timing.
- [x] Reject reactor-classpath leakage and retain effective POM, dependency tree,
      classpath, test reports, and stage/provenance evidence.
- [x] Run the published `3.5.0` consumer lane unchanged as the compatibility
      baseline.

### [x] 9.3 Update bounded support evidence

- [x] Add units and structural fields needed to distinguish fast resilience
      rejection, transport failure, and downstream HTTP failure.
- [x] Keep support fixtures bounded, deterministic, sanitized, and free of raw
      URLs, headers, credentials, bodies, and exception messages.
- [x] Validate required false, zero, and null fields by presence and JSON type.

Evidence:

- `MockReactiveHttpClientTest.openCircuitReportsOneNoNetworkTerminalAcrossMockDiagnostics` uses the existing client-config and resilience-applier hooks to reject before request subscription. It records no in-process exchange and proves one lifecycle error, one observer event, and one exchange-log record with attempt `0`, no status or failure stage, `RESILIENCE_ERROR`, and one shared observer/log duration.
- The complete mock-helper suite retained custom observers, ordered lifecycle hooks, auth replay/signing, inherited clients, constructor-injected custom loggers, retry, and no-network terminal behavior: the isolated current-consumer verifier passed 47 tests.
- The assembled Boot 4 fixture now makes real success, retried HTTP-error, and open-circuit calls. The current-reactor lane inspects finite main-timer duration, attempts `1`/`2`/`0`, corrected health counts/aliases, and one OTel span whose timing matches each terminal observer event within 1 ms.
- `scripts/verify-current-consumer.sh` passed from a fresh target-local repository and preserved effective POM, dependency tree, classpath, mock/consumer Surefire reports, artifact hashes, completed-stage provenance, and a successful reactor-classpath leakage check under `target/release-evidence/current-consumer/current-3.6.0-SNAPSHOT/`.
- The unchanged `scripts/verify-published-consumer.sh 3.5.0` lane passed against fresh Maven Central artifacts and recorded published provenance, effective POMs, dependency/classpath evidence, and 3 consumer tests under `target/release-evidence/published-consumer/published-3.5.0/`.
- Added the bounded, sanitized `support-bundle-terminal-outcomes.json` fixture for fast resilience rejection, transport failure, and downstream HTTP failure. Documentation tests require duration units and the presence/type of every false, zero, and null structural field; all 40 release-documentation tests passed.

---

## Priority 10 - Observability Overhead Re-Audit


### [x] 10.1 Preserve benchmark fairness

- [x] Re-run default no-network invocation and Micrometer observer rows against
      current `3.6.0-SNAPSHOT` and published `3.5.0` under equivalent
      dependencies and environment metadata.
- [x] Confirm the diagnostics-disabled unary path does not allocate reporting
      state for inactive features.
- [x] Measure any new Prometheus/histogram path separately from default
      observation.
- [x] Add a pre-subscription rejection benchmark only if it answers a concrete
      allocation/latency question; label it no-network.
- [x] Keep smoke output non-promotable and release output tied to a clean commit.

### [x] 10.2 Review movement without hard gates

- [x] Compare current and baseline throughput, average/sample latency, and
      allocation for named equivalent scenarios.
- [x] Investigate material movement using the documented review thresholds.
- [x] Optimize only measured regressions attributable to V26 changes.
- [x] Record expected costs for any opt-in histogram or scrape fixture.
- [x] Promote a versioned report only if release notes make numerical
      performance claims.

Evidence:

- `ReactiveClientInvocationHandlerBehaviorTest.diagnosticsDisabledUnaryRequestDoesNotInstallSubscriptionReportingState`
  proves structurally that an inactive unary call reaches the exchange without a
  `SubscriptionReportingState` in Reactor context; this conclusion is not
  inferred from allocation noise.
- `StarterDiagnosticsOverheadBenchmark` now keeps Simple-registry,
  Prometheus-registry, opt-in Prometheus histogram, and zero-dispatch
  open-circuit rows distinct. The class records throughput, average time, and
  sample time; release-profile runs add normalized allocation.
- Current and published `3.5.0` runs used Spring Boot `4.0.0`, Spring WebFlux
  `7.0.1`, Reactor Netty `1.3.0`, Netty `4.2.7.Final`, Micrometer `1.16.0`, and
  the same JDK/machine. The published lane used an isolated Maven repository and
  passed `verify-published-baseline-provenance.sh`.
- Target-only reports are under
  `target/release-evidence/v26/priority10/{current-3.6.0-SNAPSHOT,published-3.5.0}/`;
  their comparison is
  `target/release-evidence/v26/priority10/current-vs-published-3.5.0.md`.
  Every allocation row stayed below the documented review triggers. The initial
  histogram sample p95/p99 improvement crossed the directional latency trigger,
  but an equivalent focused rerun reduced both differences below 4%, identifying
  local tail variance rather than a V26 regression.
- The target-only audit records the measured opt-in histogram cost separately
  from default observation. No scrape row was added because scrape rendering is
  not request-path work and no concrete scrape bottleneck was identified.
- No production optimization or versioned report promotion was warranted. The
  reports are explicitly non-promotable dirty-worktree audit evidence; any future
  public numerical claim still requires a clean-commit release run and promoted
  report.

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
