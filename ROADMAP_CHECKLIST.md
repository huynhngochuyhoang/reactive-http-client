# Roadmap Execution Checklist

> Companion to `ROADMAP.md`. Ordered by the priority suggested at the bottom of that document.
> Check items off as they ship. Each top-level entry points back to the matching section in
> `ROADMAP.md` for full rationale — this file is the tracker, not the spec.

---

## Priority 1 — Latent security / log-safety issues

### [ ] 3.7 Redact or allow-list inbound headers before logging
- [ ] Reuse `DefaultHttpExchangeLogger`'s sensitive-header list in `InboundHeadersWebFilter`
  before storing the snapshot in `HttpExchangeLogContext`.
- [ ] Add property `reactive.http.inbound-headers.deny-list` (default: same as the
  outbound redaction list) and `reactive.http.inbound-headers.allow-list` (optional).
- [ ] Unit test: upstream request with `Authorization`, `Cookie`, `X-Api-Key` headers —
  the captured snapshot must contain redacted markers, not the raw values.
- [ ] Integration test: `DefaultHttpExchangeLogger` INFO line must not contain raw
  redacted header values.
- [ ] Changelog entry under **Security**.

### [ ] 3.1 Validate correlation-ID length and character set
- [ ] Add `reactive.http.correlation-id.max-length` (default 128).
- [ ] In `CorrelationIdWebFilter.filter(...)`: reject values that exceed the cap or
  contain control characters; log at DEBUG and continue without a correlation ID.
- [ ] Unit test: oversize value → dropped; CRLF value → dropped; normal UUID → preserved.
- [ ] Changelog entry under **Security**.

---

## Priority 2 — Silent behaviour regression since 1.8.1

### [ ] 3.9 Apply `WebClientCustomizer` beans to every client
- [ ] Inject `ObjectProvider<WebClientCustomizer>` into `ReactiveHttpClientFactoryBean`.
- [ ] Apply customizers in order after `WebClient.builder()` and before the
  starter-owned filters.
- [ ] Regression test: register a `WebClientCustomizer` bean, assert each
  `@ReactiveHttpClient` proxy's underlying builder was customised exactly once.
- [ ] Regression test: two clients must not share filter state (existing 1.8.1 guarantee
  must still hold).
- [ ] Changelog entry under **Fixed**.

---

## Priority 3 — On-call pain points

### [ ] 1.4 Per-client connection-pool tuning + idle / max-life eviction
- [ ] Extend `ClientConfig` with optional `ConnectionPoolConfig pool`.
- [ ] Add `maxIdleTimeMs`, `maxLifeTimeMs`, `evictInBackgroundMs` to `ConnectionPoolConfig`.
- [ ] Resolve client-override → global default in `ReactiveHttpClientFactoryBean.buildWebClient()`.
- [ ] Forward new values to `ConnectionProvider.builder(...)`.
- [ ] Unit test: two clients with different `maxConnections` must not share a pool.
- [ ] Document behaviour in README, including when a pooled connection is evicted.

### [ ] 2.4 Rename / clarify network-level timeouts
- [ ] Introduce `network-read-timeout-ms` / `network-write-timeout-ms` as the canonical
  names; keep the old names as deprecated aliases.
- [ ] Tag deprecated fields with `@DeprecatedConfigurationProperty`.
- [ ] Update `NetworkConfig` Javadoc to state which timeout wins in each scenario.
- [ ] Update README §2.5 with a "which timeout fires first" matrix.
- [ ] Add one sentence to `@TimeoutMs` Javadoc cross-referencing the property.

---

## Priority 4 — Operational visibility

### [ ] 2.1 Expose connection-pool + Resilience4j metrics
- [ ] Enable `reactor.netty.Metrics` on each `ConnectionProvider` when a
  `MeterRegistry` bean is present.
- [ ] Auto-register Resilience4j's `TaggedCircuitBreakerMetrics`,
  `TaggedRetryMetrics`, `TaggedBulkheadMetrics` bindings against the shared registry.
- [ ] Gate both behind `@ConditionalOnBean(MeterRegistry.class)`.
- [ ] Document the new metric names in README's observability section.

### [ ] 2.2 Request / response body-size distributions
- [ ] Capture request size once per invocation (the cached body bytes already exist).
- [ ] Capture response size in the exchange pipeline prior to decoding.
- [ ] Emit `http.client.requests.request.size` and `http.client.requests.response.size`
  `DistributionSummary` metrics with the same tags as the existing timer.
- [ ] Extend `HttpClientObserverEvent` (+ deprecate the old constructor if needed) to
  carry the sizes.
- [ ] Unit test for both directions; ensure sizes are `0` when the body is null rather
  than omitted.

### [ ] 1.6 Actuator health indicator + pool gauges
- [ ] New `HttpClientHealthIndicator` gated by
  `@ConditionalOnClass(HealthIndicator.class)`.
- [ ] Compute status from a rolling error-rate window per client name; expose
  status and rate in the `Health` details map.
- [ ] Wire pool gauges (`reactor.netty.connection.provider.total.connections`, etc.)
  into the starter's Micrometer integration.
- [ ] Document opt-in in README.

---

## Priority 5 — Most-requested new features

### [ ] 1.2 Multipart / form-data bodies
- [ ] Add annotations: `@MultipartBody` (method), `@FormField`, `@FormFile` (parameter).
- [ ] Extend `MethodMetadataCache` parser to recognise them.
- [ ] Extend `ReactiveClientInvocationHandler` body-encoding branch to build a
  `MultiValueMap<String, HttpEntity<?>>` via `MultipartBodyBuilder`.
- [ ] Unit tests: single file, multiple files, mixed file + fields, `Resource` vs. byte[],
  rejection when `@MultipartBody` is combined with `@Body`.
- [ ] README example for file upload.

### [ ] 1.3 `reactive-http-client-test` module
- [ ] New Maven module with its own `pom.xml` under the parent.
- [ ] `MockReactiveHttpClient<T>` helper that builds a proxy against an in-process
  `ExchangeFunction`.
- [ ] JUnit 5 `@MockHttpServer` extension backed by a canned-response registry.
- [ ] Assertion helpers for `ErrorCategory` outcomes.
- [ ] Port a subset of the internal tests to the new module as a dogfood check.
- [ ] Publish docs / README for the new artifact.

### [ ] 1.7 Built-in auth providers
- [ ] `OAuth2ClientCredentialsTokenProvider` on top of `RefreshingBearerAuthProvider`.
  - [ ] Token-endpoint call, client-id / client-secret basic auth, configurable
    scope / audience.
  - [ ] Unit test + concurrent-refresh test parity with the existing refreshing provider.
- [ ] `AwsSigV4AuthProvider` implementing `AuthProvider` directly.
  - [ ] Canonical request → string-to-sign → signature; reuse cached body bytes.
  - [ ] Unit test against AWS SigV4 test vectors.
- [ ] Auto-configuration hooks so users can enable either via `application.yml`.

---

## Priority 6 — Larger API-surface changes (1.9.x / 2.0.0)

### [ ] 1.1 OpenTelemetry-native observer
- [ ] New optional module `reactive-http-client-otel`.
- [ ] `OpenTelemetryHttpClientObserver` mirroring `MicrometerHttpClientObserver`.
- [ ] Semantic conventions: `http.request.method`, `http.response.status_code`,
  `server.address`, `error.type` (from `ErrorCategory`).
- [ ] Baggage propagation through the Reactor `Context`.
- [ ] Auto-configure under `@ConditionalOnClass(io.opentelemetry.api.OpenTelemetry.class)`.
- [ ] Integration test with the OTel SDK in-memory exporter.

### [ ] 1.9 Per-method `@Retry`, `@CircuitBreaker`, `@Bulkhead`
- [ ] New annotations, parsed in `MethodMetadataCache`.
- [ ] `Resilience4jOperatorApplier` picks per-method instance names when present,
  falling back to the client-level config.
- [ ] Fail-fast validation when a referenced instance name is missing at startup.
- [ ] Tests: method-level override wins, missing instance fails fast, coexistence with
  the existing client-level config.

### [ ] 1.5 HTTP proxy + custom SSL / mTLS support
- [ ] `NetworkConfig.ProxyConfig` (host, port, type, optional auth).
- [ ] `ClientConfig.TlsConfig` (truststore, keystore, protocols, cipher allow-list).
- [ ] Apply in `buildWebClient` via `HttpClient.proxy(...)` and `HttpClient.secure(...)`.
- [ ] Integration test with a local self-signed peer.

### [ ] 1.8 Response streaming passthrough
- [ ] Detect `Flux<DataBuffer>` / `Mono<ResponseEntity<Flux<DataBuffer>>>` return types.
- [ ] Skip the in-memory codec in those cases.
- [ ] Make sure observability still records duration / size correctly.
- [ ] Test with a large (>codec-max) response.

### [ ] 1.10 Configurable correlation-ID and inbound-header filters
- [ ] `reactive.http.correlation-id.mdc-keys: list` consumed by `CorrelationIdWebFilter`.
- [ ] `reactive.http.inbound-headers.allow-list` / `deny-list` consumed by
  `InboundHeadersWebFilter` (overlaps with 3.7 — implement once).

---

## Priority 7 — Housekeeping (roll into patch releases)

### [ ] 3.2 Guard `codec-max-in-memory-size-mb` edge cases
- [ ] Reject negative values at startup.
- [ ] Choose: treat `0` as "unlimited" (pass `-1`) **or** log a WARN that the default
  is being used. Document the decision.

### [ ] 3.3 Fail fast on duplicate client names
- [ ] Detect duplicates in `ReactiveHttpClientsRegistrar`.
- [ ] Throw with a message naming both interfaces.
- [ ] Include interface FQN in the pool name as a belt-and-braces safety net.

### [ ] 3.4 Cap `@TimeoutMs`
- [ ] Reject values above a documented maximum (30 min) at parse time.
- [ ] Update `@TimeoutMs` Javadoc with the accepted range.

### [ ] 3.5 Warn once on empty `@GET("")` / blank path template
- [ ] Parse-time detection in `MethodMetadataCache`.
- [ ] Per-method dedupe so the warning fires once per method, not per call.

### [ ] 3.6 Preserve HTTP context when error decoding fails
- [ ] In `decodeErrorResponse`, always construct `HttpClientException` / `RemoteServiceException`
  carrying status code + best-effort body.
- [ ] Attach the decoding exception as `cause`.
- [ ] Test: induce a `DataBufferLimitException` while decoding a 502 — assert the final
  exception is `RemoteServiceException(502, cause=DataBufferLimitException)`.

### [ ] 3.8 Dispose `ConnectionProvider` on context shutdown
- [ ] Make `ReactiveHttpClientFactoryBean` implement `DisposableBean`.
- [ ] Call `connectionProvider.disposeLater().subscribe()` on destroy.
- [ ] Context-reload test asserting providers are not leaked between cycles.

### [ ] 3.10 Cap `resilienceWarningKeys` growth
- [ ] Apply the 256-entry cap already used by `loggerCache`, or switch to Caffeine
  with `expireAfterAccess`.

### [ ] 2.3 Publish `spring-configuration-metadata.json`
- [ ] Author `additional-spring-configuration-metadata.json` covering every property
  on `ReactiveHttpClientProperties` (and nested config classes).
- [ ] Include `@DeprecatedConfigurationProperty` hints for `log-body`.
- [ ] CI check: spring-boot-configuration-processor run produces no warnings.

### [ ] 2.5 Finish deprecating `log-body`
- [ ] Mark as deprecated in 1.9.x with replacement hint.
- [ ] Delete in 2.0.0 along with the backwards-compatibility branch in
  `ClientConfig.isExchangeLoggingEnabled()`.

### [ ] 2.6 Cache the case-insensitive header view per invocation
- [ ] Build a `TreeMap(CASE_INSENSITIVE_ORDER)` once inside `ResolvedArgs`.
- [ ] Replace all `getHeaderIgnoreCase` call sites with a lookup.

### [ ] 2.7 Defer work inside `DefaultHttpExchangeLogger`
- [ ] Move header sanitisation / body truncation inside the `log.isInfoEnabled()` guard,
  or switch to SLF4J fluent API (`log.atInfo().addKeyValue(...)`).
- [ ] Microbench before/after at ≥1 krps.

### [ ] 2.8 Resolve `HttpExchangeLogger` once per method
- [ ] Store the resolved instance on `MethodMetadata` after first resolution.
- [ ] Drop the per-invocation `loggerCache` lookup on the hot path (keep the cache for
  the initial resolution).

### [ ] 2.9 Bounded-depth cause traversal in error classification
- [ ] Replace `IdentityHashMap` guard with a bounded loop (max depth 16).
- [ ] Confirm no regression against existing cause-chain tests.

### [ ] 2.10 Unify Mono / Flux invocation pipelines
- [ ] Extract the shared request-construction + error-decoding portion into a private
  helper parameterised by a `Function<ClientResponse, Publisher<?>>`.
- [ ] Existing 1.8.1 `reportExchange(...)` already covers the terminal callback.
- [ ] Regression tests must cover both paths after the refactor.

---

## How to use this checklist

- Work roughly top-to-bottom; priority 1 items should be the next release's headline.
- Items within a priority band are independent and can parallelise.
- Tick each sub-bullet as it merges; leave the parent unchecked until **all** sub-bullets
  are done, so the file doubles as a release-note source.
- When a full section ships, move its entry into `CHANGELOG.md` under the release
  version and leave the checked heading here for a release or two before pruning.
