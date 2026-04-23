# Reactive HTTP Client — Improvement Roadmap

> Baseline: v1.8.1 (2026-04-23). All items from `TASK.md` are shipped; see `CHANGELOG.md`
> for what has already been addressed. This document only lists **new work** — nothing
> already shipped or already planned under `TASK.md` is restated here.

The roadmap is split into three buckets:

1. **Features to add** — net-new capabilities users currently have to build themselves.
2. **Features to optimize** — existing code that works but can be made faster, clearer,
   or more ergonomic.
3. **Bugs / correctness to fix** — behaviour that is wrong, fragile, or surprising.

Each item cites the exact file and (where relevant) line numbers so it can be picked up
and executed without another round of discovery.

---

## 1. Features to add

### 1.1 OpenTelemetry-native instrumentation
- **Why:** Micrometer timers and `HttpClientObserverEvent` already exist, but production
  services increasingly rely on OTel spans/baggage for distributed tracing, and the
  current design forces users to bolt that on themselves.
- **Where:** new module `reactive-http-client-otel` (optional), plus an `OpenTelemetryHttpClientObserver`
  mirroring `MicrometerHttpClientObserver.java`.
- **How:** register an OTel span per invocation using semantic conventions
  (`http.request.method`, `http.response.status_code`, `server.address`, `error.type`
  mapped from `ErrorCategory`). Auto-configure behind
  `@ConditionalOnClass(io.opentelemetry.api.OpenTelemetry.class)` inside
  `ReactiveHttpClientAutoConfiguration`.

### 1.2 Multipart / form-data request bodies
- **Why:** `@Body` handles JSON / `byte[]` / `String`, but there is no first-class way
  to send `multipart/form-data` (file upload, form submission). Today users must drop
  down to the raw `WebClient`.
- **Where:** new `@MultipartBody`, `@FormField`, `@FormFile` annotations; extend
  `MethodMetadataCache.java` parser and `ReactiveClientInvocationHandler.java`
  body-encoding branch (~`:459-490`).
- **How:** when a method has `@MultipartBody`, build a `MultiValueMap<String, HttpEntity<?>>`
  from annotated parameters, delegate to Spring's `MultipartBodyBuilder`, and set
  `Content-Type: multipart/form-data` with the generated boundary.

### 1.3 Dedicated test artifact (`reactive-http-client-test`)
- **Why:** consumers currently have to stand up WireMock or MockWebServer manually and
  reimplement `@ReactiveHttpClient` proxy setup for tests. The project has extensive
  internal test scaffolding that should be published.
- **Where:** new Maven module `reactive-http-client-test`.
- **How:** ship a `MockReactiveHttpClient<T>` helper that builds a proxy against an
  in-process `ExchangeFunction`, a JUnit 5 `@MockHttpServer` extension that records
  requests and serves canned responses, and assertion helpers for the standard
  `ErrorCategory` values.

### 1.4 Per-client connection-pool tuning + `maxIdleTime` / `maxLifeTime`
- **Why:** `ReactiveHttpClientFactoryBean.java:155-158` builds every pool from the
  **global** `NetworkConfig.ConnectionPool`. A chatty internal API and a slow third-party
  partner must share the same `maxConnections` and `pendingAcquireTimeout`, which is
  wrong for most real deployments. Additionally, `maxIdleTime`, `maxLifeTime`, and
  `evictInBackground` are not configurable at all — pooled connections linger forever,
  which bites load balancers that silently drop idle sockets.
- **Where:** `ReactiveHttpClientProperties.ClientConfig` (add optional
  `ConnectionPoolConfig pool`), `ReactiveHttpClientFactoryBean.buildWebClient()`.
- **How:** resolve pool config as client-override → global default. Add
  `maxIdleTimeMs`, `maxLifeTimeMs`, `evictInBackgroundMs` to `ConnectionPoolConfig`
  and forward them to `ConnectionProvider.builder(...)`.

### 1.5 HTTP proxy and custom SSL / mTLS support
- **Why:** no way to route outbound calls through an HTTP(S) proxy or present a client
  certificate for mTLS. Users currently expose a custom `WebClient.Builder` bean,
  which defeats most of the starter's value.
- **Where:** new `ProxyConfig` and `TlsConfig` sub-records under `NetworkConfig` /
  `ClientConfig`; applied in `ReactiveHttpClientFactoryBean.buildWebClient()`.
- **How:** `HttpClient.proxy(...)` for the proxy path and
  `SslContextBuilder` + `HttpClient.secure(...)` for mTLS, both gated by
  `@ConditionalOnProperty`.

### 1.6 Actuator health indicator + pool metrics
- **Why:** operators currently have no runtime visibility into downstream health or
  pool saturation. `MicrometerHttpClientObserver` surfaces per-request metrics but
  does not bridge Netty's `ConnectionProvider` metrics, and there is no
  `HealthIndicator`.
- **Where:** new `HttpClientHealthIndicator`, bound to `@ConditionalOnClass(HealthIndicator.class)`
  in `ReactiveHttpClientAutoConfiguration.java`; extend
  `MicrometerHttpClientObserver.java` to register pool-level gauges
  (`reactor.netty.connection.provider.*`).
- **How:** when `reactor.netty.Metrics` is enabled, bind each client's
  `ConnectionProvider` to the `MeterRegistry`. Optional health probe endpoint keyed
  by client name — surfaces `DOWN` when error rate over a rolling window exceeds a
  threshold.

### 1.7 Built-in auth providers: OAuth2 client-credentials and AWS SigV4
- **Why:** `RefreshingBearerAuthProvider` is powerful but requires users to implement
  their own `AccessTokenProvider`. The two most common concrete cases (OAuth2
  client-credentials, AWS SigV4 request signing) should ship in the box.
- **Where:** `auth/` package — new `OAuth2ClientCredentialsTokenProvider` and
  `AwsSigV4AuthProvider`.
- **How:** OAuth2 provider reuses `RefreshingBearerAuthProvider` infrastructure; AWS
  SigV4 implements `AuthProvider` directly (canonical-request → string-to-sign →
  signature → `Authorization` header), using the already-cached body bytes from
  `ReactiveClientInvocationHandler.java:167-175`.

### 1.8 Response streaming and backpressure for large payloads
- **Why:** `DefaultErrorDecoder` and the codec limit (default 2 MB,
  `ReactiveHttpClientFactoryBean.java:39`) force callers into full in-memory buffering.
  There is no way to stream a large response through a `Flux<DataBuffer>` back to the
  caller for pass-through proxies or file downloads.
- **Where:** new return-type handling in `ReactiveClientInvocationHandler.java:212-253`
  to recognise `Flux<DataBuffer>` and `Mono<ResponseEntity<Flux<DataBuffer>>>` and skip
  the in-memory codec entirely.

### 1.9 Per-method resilience annotations
- **Why:** today resilience is configured per client via
  `ReactiveHttpClientProperties.ResilienceConfig`. One client often fronts several
  endpoints with very different retry/bulkhead profiles (cheap GET vs. expensive POST).
- **Where:** new `@Retry`, `@CircuitBreaker`, `@Bulkhead` annotations consumed in
  `Resilience4jOperatorApplier.java`.
- **How:** annotations reference named Resilience4j instances; metadata parsed in
  `MethodMetadataCache.java`; applier picks per-method instance when present,
  falling back to the client-level config.

### 1.10 Configurable correlation-ID / inbound-header propagation rules
- **Why:** `CorrelationIdWebFilter.java:32-36` hard-codes the MDC fallback key list
  (`correlationId`, `X-Correlation-Id`, `traceId`). Teams using Zipkin
  (`X-B3-TraceId`) or Jaeger (`uber-trace-id`) have to fork the filter.
  Similarly, `InboundHeadersWebFilter` captures **every** inbound header, which over-shares
  cookies and auth artefacts into `HttpExchangeLogContext`.
- **Where:** `ReactiveHttpClientProperties` — add
  `correlation-id.mdc-keys: list` and `inbound-headers.allow-list: list` /
  `deny-list: list`; `CorrelationIdWebFilter` / `InboundHeadersWebFilter`.
- **How:** both filters read the configured lists at construction time; defaults
  preserve today's behaviour.

---

## 2. Features to optimize

### 2.1 Expose connection-pool + Resilience4j metrics to Micrometer
- **Where:** `MicrometerHttpClientObserver.java`, `Resilience4jOperatorApplier.java`.
- **What:** register Resilience4j's own bindings (`CircuitBreakerMetrics`, `RetryMetrics`,
  `BulkheadMetrics`) against the shared `MeterRegistry` at starter init, and enable
  `reactor.netty.Metrics` on each `ConnectionProvider`. Gives operators visibility into
  circuit state, queue depth, and pool saturation without extra wiring. Gate behind
  `@ConditionalOnBean(MeterRegistry.class)`.

### 2.2 Record request / response body sizes
- **Where:** `MicrometerHttpClientObserver.java:72-104`, `HttpClientObserverEvent.java`.
- **What:** add two `DistributionSummary` metrics — `http.client.requests.request.size`
  and `http.client.requests.response.size`. Capture sizes in the exchange pipeline
  (the cached body bytes in
  `ReactiveClientInvocationHandler.java:167-175` are already serialised; response size
  is known from `DataBuffer` capacity before decoding). Lets operators detect
  over-fetch / response bloat without a tracing backend.

### 2.3 Publish `spring-configuration-metadata.json`
- **Where:** new `reactive-http-client-starter/src/main/resources/META-INF/additional-spring-configuration-metadata.json`.
- **What:** describe every property on `ReactiveHttpClientProperties` with type,
  default, and description, including deprecation markers for the (still-present)
  `log-body` alias at `ReactiveHttpClientProperties.java:96-127`. IDEs will auto-complete
  `reactive.http.*` in `application.yml`, which is the #1 onboarding friction today.

### 2.4 Separate "safety-net" timeouts from "request" timeouts in naming and docs
- **Where:** `ReactiveHttpClientProperties.NetworkConfig` at lines 56-69; README §2.5.
- **What:** the distinction between channel-level `ReadTimeoutHandler` and per-request
  `@TimeoutMs` was a repeat source of incidents (see 1.5.1 and 1.7.0 CHANGELOG
  entries). Rename `read-timeout-ms` → `network-read-timeout-ms` (keep the old name as
  a deprecated alias), document defaults with a one-paragraph "which timeout fires
  first" matrix, and surface the metadata hints from 2.3 so the purpose is visible in
  the IDE.

### 2.5 Remove the deprecated `log-body` property after one more minor release
- **Where:** `ReactiveHttpClientProperties.java:96-127`.
- **What:** `logBody` / `isLogBody` / `setLogBody` / `isExchangeLoggingEnabled` carries
  legacy code that exists only to keep a pre-1.6 name working. Flip to
  `@DeprecatedConfigurationProperty` with a replacement hint in 1.9.x, delete in 2.0.0.

### 2.6 Cache the case-insensitive header view once per invocation
- **Where:** `ReactiveClientInvocationHandler.java:443-454`.
- **What:** `getHeaderIgnoreCase` still walks the header list per call site (Accept,
  Content-Type, potentially auth paths). Build a `TreeMap<String, String>(CASE_INSENSITIVE_ORDER)`
  once inside `ResolvedArgs` and look up from there. Low absolute cost today but
  appears in every request's hot path.

### 2.7 Defer work inside the exchange logger when logging is disabled
- **Where:** `DefaultHttpExchangeLogger.java`.
- **What:** header-redaction and body-truncation logic (the `sanitize*` / `limitBody`
  helpers) runs eagerly before the `log.isInfoEnabled()` guards on the actual
  statement. For clients that emit thousands of rps with `log-exchange: false` this is
  measurable GC. Move all preparation inside the guard or use SLF4J's
  `log.atInfo().addKeyValue(...).log()` fluent API so the values are materialised lazily.

### 2.8 Avoid rebuilding the logger cache key map on every invocation
- **Where:** `ReactiveClientInvocationHandler.java:520-541`.
- **What:** the bounded `loggerCache` is correct, but `getOrDefault` still allocates
  wrapper frames for classes that have a cache miss (common at startup). Store the
  resolved `HttpExchangeLogger` reference on `MethodMetadata` after first resolution
  so steady-state lookups become one field read instead of a map lookup.

### 2.9 Replace `IdentityHashMap`-based cause traversal in error classification
- **Where:** `ReactiveClientInvocationHandler.resolveErrorCategory` (~`:650-704`).
- **What:** every error path allocates an `IdentityHashMap` to guard against cyclic
  causes. Use a bounded-depth loop (e.g. max 16 frames) instead — cause cycles are
  pathological, and the allocation runs on every 4xx/5xx/timeout response.

### 2.10 Consolidate the duplicated Mono / Flux pipelines
- **Where:** `ReactiveClientInvocationHandler.java` Mono branch (~`:140-210`) and Flux
  branch (`:212-253`).
- **What:** 1.8.1 already extracted `reportExchange(...)` to deduplicate terminal
  callbacks, but the request-construction and error-decoding portions of both
  branches still diverge. Any bug fixed in one branch has historically been missed
  in the other (see 1.8.1 null-body regression). Promote the shared pipeline to a
  private helper taking a `Function<ClientResponse, Publisher<?>>` so new bugs can't
  appear in only one branch.

---

## 3. Bugs / correctness to fix

### 3.1 Validate correlation-ID length and character set
- **Where:** `CorrelationIdWebFilter.java:40-43`.
- **What:** the inbound `X-Correlation-Id` is copied verbatim into the Reactor
  `Context` and onto every outbound request, with no length cap or character check.
  A malicious or misbehaving caller can store an arbitrarily large string in the
  context (propagated across all sub-calls) or inject log-forgery sequences.
- **Fix:** reject or truncate values longer than 128 chars; reject any that contain
  control characters (same check as `RequestArgumentResolver.validateHeaderValue`);
  log the rejection at DEBUG. Add a property
  `reactive.http.correlation-id.max-length`.

### 3.2 Silent fallback when `codec-max-in-memory-size-mb` is 0 or negative
- **Where:** `ReactiveHttpClientFactoryBean.java:191-200`.
- **What:** `sizeMb > 0 ? sizeMb : DEFAULT_CODEC_MAX_IN_MEMORY_SIZE_MB`. If a user
  sets the property to `0` (intending "unlimited") or a negative number (a typo),
  they silently get 2 MB. Two recent support incidents on our side have traced back
  to this exact shape.
- **Fix:** reject negative values with `IllegalArgumentException` at startup;
  either support `0 = unlimited` (pass `-1` to
  `codecs().defaultCodecs().maxInMemorySize`) or keep the default **and log a
  warning** so users learn their value was ignored.

### 3.3 Connection-pool name collision when two clients share a name
- **Where:** `ReactiveHttpClientFactoryBean.java:155`.
- **What:** `ConnectionProvider.builder("reactive-http-client-" + clientName)`. If
  two `@ReactiveHttpClient` interfaces declare the same `name` (common when teams
  copy-paste a client definition), they share the same pool by reference. No error,
  no warning — just bewildering saturation behaviour in production.
- **Fix:** detect duplicate client names in `ReactiveHttpClientsRegistrar.java` and
  fail fast with a descriptive message. Optionally append the interface FQN to the
  provider name for belt-and-braces uniqueness.

### 3.4 `@TimeoutMs` accepts unbounded values
- **Where:** `MethodMetadataCache.java:107-114`.
- **What:** the validator rejects negative timeouts but accepts `Long.MAX_VALUE`. In
  combination with `Duration.ofMillis(...)` later in the pipeline, extreme values can
  overflow or cause silent infinite waits.
- **Fix:** cap at a documented maximum (e.g. 30 minutes); reject values above the cap
  with a clear error and document the range on the annotation Javadoc.

### 3.5 `@GET("")` / empty-path templates parsed without warning
- **Where:** `MethodMetadataCache.java` annotation-parsing branch (~`:33-45`).
- **What:** a blank path template is accepted and resolves to the base URL.
  Occasionally intentional, but more often a copy-paste bug that only surfaces in
  staging when the wrong endpoint gets hit. No warning is emitted.
- **Fix:** warn at parse time when the path is blank. Don't hard-error — some users
  legitimately do this — but put a single `log.warn` behind the first observation
  (use a `ConcurrentHashMap<Method, Boolean>` to dedupe) so it shows up in logs
  exactly once per method.

### 3.6 Preserve the original response context when error decoding itself fails
- **Where:** `ReactiveClientInvocationHandler.decodeErrorResponse` (~`:376-381`) and
  `DefaultErrorDecoder.java:50-69`.
- **What:** if the body cannot be read (e.g. `DataBufferLimitException` or malformed
  encoding) while we are already handling a non-2xx response, the decoding exception
  replaces the original HTTP context. Operators see "JSON parse error" in logs
  instead of "got 502 from upstream".
- **Fix:** always construct an `HttpClientException` / `RemoteServiceException` with
  the status code and a best-effort body, and attach the decoding exception as
  `cause`. Never let the decoding exception escape alone.

### 3.7 `InboundHeadersWebFilter` captures headers users never intended to propagate
- **Where:** `InboundHeadersWebFilter.java`.
- **What:** captures the full inbound header map unconditionally. Combined with
  `DefaultHttpExchangeLogger` now logging `inboundHeaders=…` at INFO (CHANGELOG 1.8.0),
  this means cookies, `Authorization`, and any custom secret header sent by the
  upstream caller can leak into log aggregation. `DefaultHttpExchangeLogger`'s
  redaction list covers **outbound** headers, not inbound.
- **Fix:** apply the same redaction list to the inbound-header snapshot before
  storing it in `HttpExchangeLogContext`. Or, preferably, expose the allow/deny list
  from item 1.10 so the snapshot only contains headers the operator explicitly
  opted into.

### 3.8 Connection provider is never closed on context shutdown
- **Where:** `ReactiveHttpClientFactoryBean.java:155-158`.
- **What:** each client creates a `ConnectionProvider` but the factory bean does not
  register a disposal hook. At Spring context shutdown, the provider leaks — fine in
  a JVM that is exiting, but problematic in tests that reload the context many
  times (OOM in long-running CI) and in hot-reload scenarios.
- **Fix:** make the factory bean implement `DisposableBean` (or hold the provider in
  a `@Bean(destroyMethod = "disposeLater")` wrapper) and call `disposeLater()` on
  shutdown.

### 3.9 `Scope("prototype")` `WebClient.Builder` does not run downstream
  `WebClientCustomizer` beans
- **Where:** `ReactiveHttpClientAutoConfiguration.java:41-46`.
- **What:** 1.8.1 fixed the auth-leak by making the builder prototype-scoped, but
  `WebClient.builder()` is now called directly, bypassing any `WebClientCustomizer`
  beans a user might have registered (Sleuth, request logging, etc.). Users upgrading
  from 1.8.0 may silently lose customisation.
- **Fix:** inject `ObjectProvider<WebClientCustomizer>` into the factory method, call
  `customizers.orderedStream().forEach(c -> c.customize(builder))` before returning.
  Add a regression test that asserts a custom `WebClientCustomizer` is applied to
  every client.

### 3.10 `resilienceWarningKeys` can grow unbounded over time
- **Where:** `ReactiveClientInvocationHandler.java:83`.
- **What:** a `ConcurrentHashMap.newKeySet()` used to dedupe Resilience4j warning
  logs. Keys are derived from Resilience4j instance names — normally bounded, but
  if a user passes dynamic names (e.g. per-tenant instance names) the set grows
  without eviction. Mirrors the bug that was fixed in M10 for `loggerCache`.
- **Fix:** apply the same 256-entry cap with a one-time overflow warning, or
  switch to a Caffeine cache with `expireAfterAccess`.

---

## Suggested priority order

1. **3.7** (inbound-header leakage) and **3.1** (correlation-ID validation) — both are
   latent information-leak / log-forgery issues in code that is already on the hot path.
2. **3.9** (`WebClientCustomizer` regression) — silent behaviour change since 1.8.1.
3. **1.4** + **2.4** — per-client pool tuning plus timeout-name disambiguation,
   since both surface in on-call rotations already.
4. **2.1** + **2.2** + **1.6** — operational visibility (Resilience4j metrics, pool
   metrics, health indicator, body-size distributions).
5. **1.2** (multipart), **1.3** (test artifact), **1.7** (OAuth2 / SigV4) — the
   three most-requested "missing features" by adopting services.
6. **1.1** (OpenTelemetry) and **1.9** (per-method resilience annotations) — larger
   surface changes; schedule for a 1.9.0 or 2.0.0 cut.
7. The remaining items are good-housekeeping work that can be rolled into patch
   releases as capacity allows.
