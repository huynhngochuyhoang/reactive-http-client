# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [Unreleased]

---

## [1.10.1] – 2026-05-02

### Added

- **Spring Boot configuration metadata.** Added
  `META-INF/additional-spring-configuration-metadata.json` covering all
  `ReactiveHttpClientProperties` fields so IDEs provide auto-completion and
  documentation for every `reactive.http.*` property. (#36)
- **Method-scoped logger caching.** `MethodMetadata` now resolves and caches the
  per-method `HttpExchangeLogger` on first use via a `volatile` field with a
  `NOOP_EXCHANGE_LOGGER` sentinel, avoiding repeated registry lookups on the hot
  path. (#36)
- **`MethodMetadataCache.testOnlyBlankPathWarnedCount()`.** Test-only helper that
  exposes how many times the blank-path-template warning has been emitted, allowing
  the `blankPathTemplateWarningIsFiredOnlyOnce` test to assert the exact count rather
  than relying on log output. (#37)

### Changed

- **Unified Mono / Flux invocation pipeline.** Refactored `ReactiveClientInvocationHandler`
  to share a single `exchange(...)` method for both `Mono` and `Flux` return types,
  eliminating duplicated pipeline assembly and reducing the risk of divergence between
  the two paths. (#35)
- **`@DeprecatedConfigurationProperty` on `log-body`.** The `logBody` getter in
  `ClientConfig` is now annotated with `@DeprecatedConfigurationProperty` (with a
  `replacement` and `since` value) so Spring Boot's configuration processor surfaces
  the deprecation in IDE hints. (#36)
- **Header lookup optimisation.** `ResolvedArgs` now builds a case-insensitive
  `TreeMap` view of its headers once on construction; all downstream header lookups
  use this cached view instead of iterating the raw map. (#36)
- **Logger guard helpers.** `DefaultHttpExchangeLogger` extracts `logSuccess()` and
  `logError()` private methods with per-level `isEnabled` guards to avoid unnecessary
  string formatting on the hot path; `responseBody` is now included in the WARN log
  path. (#36)
- **Bounded root-cause traversal.** `getRootCause` replaces the previous
  `IdentityHashMap`-based cycle detection with a simple bounded loop (max depth 16),
  removing the allocation overhead on every exception-handling call. (#36)
- **`buildFallbackException` reactive safety.** `releaseBody()` is now composed
  inside the reactive chain via `.thenReturn()` instead of a `subscribe()` side
  effect, ensuring the release is always sequenced and never silently dropped. (#37)
- **`ReactiveHttpClientFactoryBean` destroy logging.** `destroy()` now passes the
  full exception object to `log.warn(...)` so the stack trace is visible to operators
  when connection-provider shutdown fails. (#37)
- **Explicit `this.connectionProvider` reference** in `ReactiveHttpClientFactoryBean`
  to make the intent clear and avoid potential confusion with a local variable of the
  same name. (#37)

### Fixed

- **`ReactiveHttpClientsRegistrar` false-positive duplicate-name error.** Candidates
  are now de-duplicated by interface class name before the duplicate-name check,
  preventing spurious `IllegalStateException` when base-package lists overlap and the
  same interface is scanned more than once. (#37)

---

## [1.10.0] – 2026-05-01

### Added

- **`reactive-http-client-otel` artifact.** New companion module providing
  `OpenTelemetryHttpClientObserver`, an `HttpClientObserver` that records each
  outbound HTTP exchange as an OTel `CLIENT` span using the standard semantic
  conventions: `http.request.method`, `http.response.status_code`,
  `url.template`, `error.type` (mapped from `ErrorCategory`, falling back to the
  exception's simple class name), plus starter-specific
  `rhttp.client.name` / `rhttp.api.name` / `rhttp.attempt.count` /
  `rhttp.request.bytes` / `rhttp.response.bytes` attributes. Span name follows
  the OTel low-cardinality recommendation (`<METHOD> <api.name>`). Activated
  under `reactive.http.observability.otel.enabled` (default `true` when the
  OTel API is on the classpath and an `OpenTelemetry` bean is available).
  Auto-configured via `META-INF/spring/...AutoConfiguration.imports`; gated on
  `@ConditionalOnMissingBean(HttpClientObserver.class)` so it shuts off the
  Micrometer observer when both modules are on the classpath. (Roadmap 1.1)
- **Per-method resilience overrides.** New `@Retry`, `@CircuitBreaker`,
  `@Bulkhead` annotations select a specific Resilience4j instance by name on
  one method, taking precedence over the client-level
  `reactive.http.clients.<name>.resilience.*` setting. The factory bean
  validates referenced names at proxy-construction time via the new
  `ResilienceOperatorApplier.isInstanceConfigured(...)` hook and fails fast
  with a descriptive `IllegalStateException` when an instance is missing.
  (Roadmap 1.9)
- **HTTP proxy and TLS / mTLS configuration.** Two new sub-configs:
  `reactive.http.network.proxy.*` (HTTP / HTTPS / SOCKS4 / SOCKS5, optional
  username/password, `nonProxyHosts` regex) and
  `reactive.http.network.tls.*` (truststore + keystore via Spring's
  `DefaultResourceLoader`, configurable protocols / ciphers, plus an
  `insecure-trust-all` flag for development that emits a startup WARN).
  Both also accept per-client overrides under
  `reactive.http.clients.<name>.proxy.*` / `.tls.*` — the override replaces
  the global block wholesale (no field-level merging). (Roadmap 1.5)
- **Streaming response passthrough.** Methods declaring
  `Flux<DataBuffer>` or `Mono<ResponseEntity<Flux<DataBuffer>>>` skip the
  in-memory codec entirely, so payloads larger than
  `codec-max-in-memory-size-mb` are streamed without a
  `DataBufferLimitException`. The `ResponseEntity` variant exposes the
  upstream status and headers alongside the streaming body for proxy /
  pass-through use cases. (Roadmap 1.8)
- **Configurable correlation-id MDC fallback keys.**
  `reactive.http.correlation-id.mdc-keys` replaces the previously hard-coded
  list (`correlationId`, `X-Correlation-Id`, `traceId`) with a configurable
  one — useful for Zipkin's `X-B3-TraceId`, Jaeger's `uber-trace-id`, or any
  custom tracing key. An empty list disables the MDC fallback entirely.
  Defaults preserve the prior list. (Roadmap 1.10)

---

## [1.9.0] – 2026-04-23

### Added

- `reactive.http.correlation-id.max-length` (default `128`). Inbound `X-Correlation-Id`
  values longer than the limit, or containing CR / LF / other ISO control characters,
  are now dropped with a DEBUG log and never stored in the Reactor context or
  propagated outbound. Prevents log-forgery and context-bloat via malicious upstream
  callers. (Roadmap 3.1)
- `reactive.http.inbound-headers.allow-list` and `reactive.http.inbound-headers.deny-list`.
  `InboundHeadersWebFilter` now filters the inbound-header snapshot before storing it
  in the Reactor context: if the allow-list is non-empty only those headers are
  captured, and any captured header whose name matches the deny-list has its value
  replaced with `[REDACTED]`. Deny-list defaults to the shared
  `SensitiveHeaders.DEFAULTS` list (`Authorization`, `Cookie`, `Set-Cookie`,
  `Proxy-Authorization`, `X-Api-Key`). (Roadmap 3.7)
- `SensitiveHeaders` utility consolidating the credential / session-cookie deny-list
  used by `DefaultHttpExchangeLogger` and `InboundHeadersWebFilter`.
- **Per-client connection-pool overrides.** `reactive.http.clients.<name>.pool.*`
  now accepts every field of the global `reactive.http.network.connection-pool`
  block. When set the client-level block replaces the global one wholesale (no
  field-level merging). Leaving it unset inherits the global pool, preserving
  prior behaviour. (Roadmap 1.4)
- Connection-pool idle / lifetime eviction knobs on both the global and
  per-client `connection-pool` blocks: `max-idle-time-ms`, `max-life-time-ms`,
  `evict-in-background-ms`. All default to `0` (disabled), preserving prior
  Reactor Netty behaviour. Set behind load balancers that silently drop
  long-idle sockets to avoid handing out half-dead pooled connections. (Roadmap 1.4)
- `reactive.http.network.connection-pool.metrics-enabled` (default `false`).
  When flipped on and a `MeterRegistry` bean is present, the `ConnectionProvider`
  publishes Reactor Netty's built-in pool gauges
  (`reactor.netty.connection.provider.total.connections`,
  `.active.connections`, `.idle.connections`, `.pending.connections`) tagged by
  the pool name. (Roadmap 1.6 pool gauges / 2.1a)
- Resilience4j Micrometer auto-binding. When
  `io.github.resilience4j:resilience4j-micrometer` is on the classpath **and**
  a `CircuitBreakerRegistry` / `RetryRegistry` / `BulkheadRegistry` bean is
  present alongside a `MeterRegistry`, the starter registers
  `TaggedCircuitBreakerMetrics` / `TaggedRetryMetrics` /
  `TaggedBulkheadMetrics` as `MeterBinder` beans (names
  `reactiveHttpCircuitBreakerMeterBinder` / `reactiveHttpRetryMeterBinder` /
  `reactiveHttpBulkheadMeterBinder`). Each binding is skipped independently
  when its dedicated registry is absent; users can override a specific
  binding by declaring a `MeterBinder` bean with the matching name. (Roadmap 2.1b)
- Request / response body-size metrics. `HttpClientObserverEvent` now carries
  `requestBytes` and `responseBytes` (both `long`, `-1` / `UNKNOWN_SIZE` when
  not measurable), and `MicrometerHttpClientObserver` emits
  `http.client.requests.request.size` and
  `http.client.requests.response.size` `DistributionSummary` meters tagged
  with `client.name`, `api.name`, `http.method`, `uri`. Request size is
  measured for `byte[]` / `String` / `CharSequence` / `null` bodies; arbitrary
  objects are left unmeasured to avoid double-serialisation. Response size is
  read from `Content-Length`; chunked / headerless responses are skipped.
  (Roadmap 2.2)
- `HttpClientHealthIndicator`. When `spring-boot-actuator` is on the classpath
  and a `MeterRegistry` bean is present, the starter auto-registers a health
  indicator that reads the existing `http.client.requests` timers and reports
  per-client error rates computed from probe-to-probe deltas. New properties:
  `reactive.http.observability.health.enabled` (default `true`),
  `.error-rate-threshold` (default `0.5`), `.min-samples` (default `10`). The
  indicator does not implement `HttpClientObserver`, so the existing
  `@ConditionalOnMissingBean(HttpClientObserver.class)` override contract is
  preserved. Added `spring-boot-actuator` as an optional dependency.
  (Roadmap 1.6)
- **Multipart / form-data request encoding.** New annotations:
  `@MultipartBody` (method), `@FormField` (scalar / multi-value text part),
  `@FormFile` (file part — accepts `byte[]`, any
  `org.springframework.core.io.Resource`, or the new `FileAttachment`
  convenience record carrying bytes + filename + content-type). The starter
  builds the `multipart/form-data` body via Spring's `MultipartBodyBuilder`;
  the boundary-bearing `Content-Type` is generated automatically.
  Combining `@MultipartBody` with `@Body`, or using `@FormField` /
  `@FormFile` without `@MultipartBody`, is rejected at metadata-parse time.
  (Roadmap 1.2)
- **Built-in OAuth 2.0 client-credentials token provider.**
  `OAuth2ClientCredentialsTokenProvider` implements `AccessTokenProvider`
  and posts the standard {@code grant_type=client_credentials} flow to the
  configured token endpoint. Supports both client-authentication schemes
  (HTTP Basic — default — and `client_id`/`client_secret` form post via
  `authStyle(AuthStyle.FORM_POST)`); forwards optional `scope` / `audience`
  parameters; converts the server's `expires_in` into an
  `AccessToken.expiresAt()` minus a configurable `expiryLeeway` (default
  30 s). Compose with `RefreshingBearerAuthProvider` for caching +
  single-in-flight refresh. (Roadmap 1.7 — OAuth2 half. AWS SigV4
  intentionally deferred.)
- **`reactive-http-client-test` artifact.** New companion module
  containing `MockReactiveHttpClient<T>` (builds a real
  `@ReactiveHttpClient` proxy against an in-process `ExchangeFunction`,
  records every outbound exchange, and serves canned responses by matcher),
  `RecordedExchange` (materialised request snapshot — method, URI,
  headers, UTF-8 body), and `ErrorCategoryAssertions` (fluent
  `assertThatFails(mono).hasErrorCategory(...).hasStatusCode(...)` helper).
  Pulls `spring-test` as a compile dep so consumers don't need to add it
  themselves. (Roadmap 1.3)
- Canonical safety-net timeout property names
  `reactive.http.network.network-read-timeout-ms` and
  `reactive.http.network.network-write-timeout-ms`. Existing `read-timeout-ms` /
  `write-timeout-ms` keys continue to bind to the same backing fields and are
  now flagged as deprecated configuration properties — IDEs will show the
  replacement. README §2.5 now includes a "which timeout fires first" matrix
  distinguishing the channel-level safety nets from per-request
  `@TimeoutMs` / `resilience.timeout-ms` values. (Roadmap 2.4)

### Deprecated

- `reactive.http.network.read-timeout-ms` and `reactive.http.network.write-timeout-ms`
  — use `network-read-timeout-ms` / `network-write-timeout-ms` instead. Both keys
  bind to the same backing field, so existing configuration continues to work.

### Fixed

- **`WebClientCustomizer` beans are now applied to every `@ReactiveHttpClient`
  proxy.** The 1.8.1 prototype-scope fix for the auth-header leak inadvertently
  stopped running `WebClientCustomizer` beans — our `starterWebClientBuilder()`
  returned a bare `WebClient.builder()` without applying customizers. This mirrors
  Spring Boot's own `WebClientAutoConfiguration` pattern: the prototype bean now
  takes `ObjectProvider<WebClientCustomizer>` and applies each in `@Order` before
  handing the builder to the factory. Users who lost Sleuth / Micrometer / custom
  instrumentation on upgrade to 1.8.1 will regain it. (Roadmap 3.9)

### Security

- Inbound headers captured by `InboundHeadersWebFilter` and logged via
  `HttpExchangeLogContext#inboundHeaders()` are now subject to the same redaction
  rules as outbound headers, closing a leakage path introduced in 1.8.0 where
  upstream-supplied credentials could land in log aggregation.
- Correlation-id length and character-set validation prevent log forgery and
  Reactor-context bloat via oversized or control-character-laden inbound values.

---

## [1.8.1] – 2026-04-23

### Fixed

- **Auth header leakage between clients** — `starterWebClientBuilder()` in `ReactiveHttpClientAutoConfiguration` was registered as a singleton. Because `WebClient.Builder` is mutable, each client's factory bean called `.filter()` on the *same* shared instance, accumulating filters across clients. A client with no `AuthProvider` configured would therefore inherit the `OutboundAuthFilter` of whichever client was initialised first, causing that client's auth headers to appear on all its outbound requests. Fixed by adding `@Scope("prototype")` to `starterWebClientBuilder()`, mirroring Spring Boot's own `WebClientAutoConfiguration`.
- **Double metrics/log recording on null-body responses** — when an external API returned a null or empty body, Reactor fired `doOnTerminate` for the `onComplete` signal and then `doOnCancel` as Netty released the connection, causing both hooks to execute. An `AtomicBoolean` guard now ensures only the first signal (termination for a completed request, cancellation for a true cancel) triggers logging and observer notification.

### Changed

- Extracted `reportExchange(...)` private helper in `ReactiveClientInvocationHandler` to consolidate the duplicated logger/observer dispatch logic shared by `doOnTerminate` and `doOnCancel` in both the Mono and Flux paths.

---

## [1.8.0] – 2026-04-22

### Added

- `InboundHeadersWebFilter` — a new `WebFilter` that captures a snapshot of all inbound request headers from the upstream caller and stores them in the Reactor `Context` under `InboundHeadersWebFilter.INBOUND_HEADERS_CONTEXT_KEY`. Auto-registered by `ReactiveHttpClientAutoConfiguration` when Spring WebFlux is present (`@ConditionalOnWebApplication(REACTIVE)`).
- `HttpExchangeLogContext#inboundHeaders()` — new field on the log-context record carrying the inbound headers map. Populated automatically when `InboundHeadersWebFilter` is active and the outbound call originates within a WebFlux request chain; defaults to an empty map otherwise.

### Changed

- `DefaultHttpExchangeLogger` now includes `inboundHeaders=` in both success (`INFO`) and error (`WARN`) log lines, making it easy to correlate outbound calls with the triggering inbound request.
- `ReactiveClientInvocationHandler` uses `Mono.deferContextual` / `Flux.deferContextual` to read inbound headers from the Reactor `Context` and passes them into the log context.

### Removed

- `UpstreamHeadersWebFilter` — replaced by the more general `InboundHeadersWebFilter`.

---

## [1.7.0] – 2026-04-22

### Added

- `HttpClientObserverEvent.getAttemptCount()` — total subscription attempts for an invocation (1 = first-try success; >1 = Resilience4j retry fired at least once). Useful for detecting degraded downstream services.
- `http.client.requests.attempts` Micrometer `DistributionSummary` recorded by `MicrometerHttpClientObserver` alongside the existing timer. Tags: `client.name`, `api.name`, `http.method`, `uri`. A p95 > 1 signals a degraded downstream.
- `ResilienceOperatorApplierTest` — unit tests covering `NoopResilienceOperatorApplier` (passthrough, error propagation) and `Resilience4jOperatorApplier` (success paths, error recording, saturated bulkhead rejection, null-registry fallthrough, non-Resilience4j constructor arguments).

### Changed

- `NetworkConfig` defaults for `readTimeoutMs` and `writeTimeoutMs` raised from 5 000 ms to **60 000 ms (60 s)**. These Netty-level handlers are intentionally larger than any per-request business timeout and act as absolute safety nets for pooled connections.

### Fixed

- **`ReadTimeoutHandler` restored** as a Netty `doOnConnected` channel handler. A previous change had mistakenly replaced it with a global `HttpClient.responseTimeout()` call, which conflated channel-level safety-net behaviour with per-request timeout semantics.
- `PrematureCloseException` (fired when a per-request `responseTimeout` is cancelled by Reactor Netty) now maps to `ErrorCategory.TIMEOUT` instead of falling through to `UNKNOWN`.
- Metric duration now reflects **total elapsed time across all retry attempts**. Previously `start` was reset in `doOnSubscribe` on each re-subscription, so the recorded duration captured only the last attempt.
- `logRequest()` debug log no longer fires on every retry re-subscription; it is emitted exactly once per invocation.
- Request body serialization (`objectMapper.writeValueAsBytes`) is now **cached** with `Mono.cache()` so retries reuse the already-serialised bytes instead of re-running JSON serialisation on `boundedElastic`.

---

## [1.6.0] – 2026-04-22

### Added

- `@PATCH` annotation and method-parser support for HTTP PATCH verbs (H6).
- `RequestSerializationException` for JSON serialization failures previously wrapped as `AuthProviderException` (M9).
- Observability error categories for network failures:
  - `ErrorCategory.CONNECT_ERROR` for `ConnectException`
  - `ErrorCategory.UNKNOWN_HOST` for `UnknownHostException`
- Support for Java `default` methods on `@ReactiveHttpClient` interfaces via `InvocationHandler.invokeDefault` (H4).
- Request method/URL context on `HttpClientException` and `RemoteServiceException`, including cause-accepting constructors (L10).
- Additional MDC key fallbacks (`correlationId`, `X-Correlation-Id`, `traceId`) for correlation-ID propagation (M8).
- Bounded `HttpExchangeLogger` cache (max 256 entries) with one-time warning on eviction (M10).
- Message-overload constructor on `AuthProviderException` for richer diagnostics (L9).
- Test coverage for: `@PATCH`, byte[]/String bodies, `default` interface methods, non-reactive return types rejected at parse time, concurrent `RefreshingBearerAuthProvider.invalidate()` races, URL-encoded auth query params, Netty `ReadTimeoutException` classification, and registrar skip-if-present across both orderings (T1–T7, L7).

### Changed

- `DefaultHttpExchangeLogger` redacts sensitive headers (`Authorization`, `Cookie`, `Set-Cookie`, `Proxy-Authorization`, `X-Api-Key`) and logs bodies at DEBUG only when explicitly enabled (C1).
- Exception messages no longer embed response bodies; stored bodies are truncated to 4 KB and remain available via `getResponseBody()` (C2).
- `DefaultErrorDecoder` truncates error bodies to 4 KB before constructing exceptions (C3).
- `OutboundAuthFilter` URL-encodes auth-provider query parameter values (H1); validates auth header values for CRLF and control characters (C4).
- `MethodMetadataCache` rejects non-reactive return types at parse time (H2) and rejects non-blank `@HeaderParam` values on `Map` parameters (H8).
- `MicrometerHttpClientObserver` tags network errors with `http.status_code="NONE"` instead of `CLIENT_ERROR`; defaults `clientName` to `"UNKNOWN"` on null (H5, L8).
- `MethodMetadata` collections are frozen (`Map.copyOf` / `Set.copyOf`) after parsing (M1).
- Auth body serialization runs on `Schedulers.boundedElastic()` and is skipped entirely when no auth provider is configured (M2).
- Observability duration is now measured from subscribe time, not proxy-invoke time (M3).
- Consolidated timeout-resolution helpers into a single source of truth (M4).
- `CorrelationIdWebFilter` sets (rather than appends) `X-Correlation-Id` on outbound requests to prevent duplicates (M7).
- `loggingFilter` now logs method, URL, status, and latency as documented, tagging outcomes as `OK`, `HTTP_ERROR`, or `TRANSPORT_ERROR` (M5).
- Request-argument resolver validates `@HeaderParam` values for CRLF / control characters (C4).
- Internal cleanups: simplified `getObserver()`, removed redundant `Set<String>` qualifier, replaced Stream-based `getHeaderIgnoreCase` with a loop, switched `Class.forName` to `ClassUtils.resolveClassName` for container safety (L1–L4).

### Fixed

- Netty `ReadTimeoutException` now maps to `ErrorCategory.TIMEOUT` instead of `UNKNOWN` (H3).
- Race in `RefreshingBearerAuthProvider.invalidate()` where an in-flight refresh could re-populate the cache immediately after invalidation is resolved via a monotonic invalidation epoch (H7).
- `RemoteServiceException` message formatting when method is `"UNKNOWN"` or only one of method/URL is present.

### Security

- Sensitive-header redaction (C1) and the removal of response bodies from exception messages (C2, C3) reduce the risk of credentials and PII leaking into logs, metric tags, and error-reporting pipelines.
- Header-injection hardening via CRLF / control-character validation on `@HeaderParam` and auth-provider header values (C4).

### Deprecated

- The `HttpClientObserverEvent` constructor that leaves `errorCategory` unset (L6).

### Build

- Pinned `maven-surefire-plugin` to 3.2.5 in the parent POM so `mvn test` discovers JUnit 5 tests without an explicit plugin coordinate.

### Removed

- Dead utility `UriTemplateExpander` (M6).

---

## [1.5.1] – 2026-04-21

### Fixed

- Restored API-level timeout precedence so method `@TimeoutMs` overrides global network timeout per request.
- Supported explicit timeout disable with `@TimeoutMs(0)` even when global `read-timeout-ms` is configured.

### Changed

- Clarified `reactive.http.network.read-timeout-ms` semantics as Reactor Netty response timeout in code/docs.

---

## [1.4.0] – 2026-04-20

### Added

- Added `ErrorCategory.RESPONSE_DECODE_ERROR` to classify response decode/deserialization failures
  during `bodyToMono` / `bodyToFlux` conversion (e.g. malformed JSON, mismatched type/shape, encoded payload).
- Added test coverage for Mono/Flux decode-failure observability category emission.

---

## [1.3.0] – 2026-04-20

### Added

- Added `ErrorCategory.AUTH_PROVIDER_ERROR` for outbound authentication provider failures.
- Added `AuthProviderException` to normalize errors raised by `AuthProvider`.
- Added test coverage for auth-provider failure wrapping and observability error category emission.

### Changed

- Updated `OutboundAuthFilter` to map auth-provider failures to `AuthProviderException` without double wrapping.
- Updated `ReactiveClientInvocationHandler` to classify `AuthProviderException` as `AUTH_PROVIDER_ERROR`.
- Updated observability docs/tag semantics to include `AUTH_PROVIDER_ERROR`.

---

## [1.2.0] – 2026-04-19

### Added

- Global network policy configuration via `reactive.http.network`:
  - `connect-timeout-ms`
  - `read-timeout-ms`
  - `write-timeout-ms`
  - `connection-pool.max-connections`
  - `connection-pool.pending-acquire-timeout-ms`
- Built-in outbound bearer auth refresh strategy:
  - `AccessToken` model and `AccessTokenProvider` abstraction.
  - `RefreshingBearerAuthProvider` with cached token reuse, refresh-before-expiry window, and single in-flight refresh deduplication.
- Unit tests for token reuse, refresh trigger, concurrent refresh deduplication, and expired-token rejection.

### Changed

- `ReactiveHttpClientFactoryBean` now applies global transport timeout and pool policy to all clients.
- Request-timeout ownership is simplified:
  1. method `@TimeoutMs`
  2. `resilience.timeout-ms` (when enabled)
  3. no request timeout
- Removed client-level request-timeout precedence from invocation timeout resolution.
- Updated README examples and property docs to align with the global network policy model.

---

## [1.1.0] – 2026-04-16

### Added

- **`ErrorCategory` enum** – high-level classification of HTTP client errors
  (`CLIENT_ERROR`, `RATE_LIMITED`, `SERVER_ERROR`, `TIMEOUT`, `CANCELLED`, `UNKNOWN`).
  Allows category-based error handling without hard-coding status codes.
- **`HttpClientException.getErrorCategory()`** – returns `RATE_LIMITED` for HTTP 429,
  `CLIENT_ERROR` for all other 4xx responses.
- **`RemoteServiceException.getErrorCategory()`** – always returns `SERVER_ERROR`.
- New constructor overloads on `HttpClientException` and `RemoteServiceException`
  that accept an explicit `Throwable cause` for wrapping low-level errors.
- **Integration tests** for HTTP edge cases (`DefaultErrorDecoderTest`, `HttpEdgeCasesTest`):
  - 429 / 5xx response decoding and `ErrorCategory` mapping.
  - Timeout behavior (using virtual-time `StepVerifier`).
  - Cancellation behavior.
- **CI workflow** (`.github/workflows/ci.yml`) – runs `mvn verify` on JDK 17 and 21
  for every push to `main` and every PR targeting `main`.

### Changed

- `HttpClientException` and `RemoteServiceException` now expose `getErrorCategory()`
  in addition to the existing `getStatusCode()` and `getResponseBody()` methods.
  All existing constructors and method signatures are **backward-compatible**.

### Fixed

- Fixed an issue where `X-Correlation-Id` was not forwarded on outbound reactive
  HTTP client calls.

---

## [1.0.0] – 2025-04-10

### Added

- Initial release of `reactive-http-client-starter`.
- Declarative annotation-driven HTTP client (`@ReactiveHttpClient`, `@GET`, `@POST`,
  `@PUT`, `@DELETE`, `@PathVar`, `@QueryParam`, `@HeaderParam`, `@Body`).
- Auto-configuration via `@EnableReactiveHttpClients`.
- Resilience4j integration (circuit-breaker, retry, bulkhead, timeout).
- Micrometer observability support (`MicrometerHttpClientObserver`).
- Per-method timeout override via `@TimeoutMs`.
- Request/response exchange logging via `@LogHttpExchange`.
- `DefaultErrorDecoder` – maps 4xx → `HttpClientException`, 5xx → `RemoteServiceException`.

---

## Versioning Policy

This project uses **Semantic Versioning** (`MAJOR.MINOR.PATCH`):

| Change type | Version bump |
|---|---|
| Backward-incompatible API change | `MAJOR` (e.g., 1.x → 2.0.0) |
| New backward-compatible feature | `MINOR` (e.g., 1.0.x → 1.1.0) |
| Backward-compatible bug fix | `PATCH` (e.g., 1.1.x → 1.1.1) |

### Release process

1. Update `<version>` in the root `pom.xml` (remove `-SNAPSHOT` suffix for releases).
2. Update this file: move items from `[Unreleased]` to a new versioned section.
3. Create and push a git tag: `git tag v<VERSION> && git push origin v<VERSION>`.
4. Create a GitHub Release from that tag.  
   The `publish-maven-central.yml` workflow will automatically build, sign, and publish the artifacts.

[Unreleased]: https://github.com/huynhngochuyhoang/reactive-http-client/compare/v1.10.0...HEAD
[1.10.0]: https://github.com/huynhngochuyhoang/reactive-http-client/compare/v1.9.0...v1.10.0
[1.9.0]: https://github.com/huynhngochuyhoang/reactive-http-client/compare/v1.8.1...v1.9.0
[1.8.1]: https://github.com/huynhngochuyhoang/reactive-http-client/compare/v1.8.0...v1.8.1
[1.8.0]: https://github.com/huynhngochuyhoang/reactive-http-client/compare/v1.7.0...v1.8.0
[1.7.0]: https://github.com/huynhngochuyhoang/reactive-http-client/compare/v1.6.0...v1.7.0
[1.6.0]: https://github.com/huynhngochuyhoang/reactive-http-client/compare/v1.5.1...v1.6.0
[1.5.1]: https://github.com/huynhngochuyhoang/reactive-http-client/compare/v1.5.0...v1.5.1
[1.4.0]: https://github.com/huynhngochuyhoang/reactive-http-client/compare/v1.3.0...v1.4.0
[1.3.0]: https://github.com/huynhngochuyhoang/reactive-http-client/compare/v1.2.0...v1.3.0
[1.2.0]: https://github.com/huynhngochuyhoang/reactive-http-client/compare/v1.1.0...v1.2.0
[1.1.0]: https://github.com/huynhngochuyhoang/reactive-http-client/compare/v1.0.0...v1.1.0
[1.0.0]: https://github.com/huynhngochuyhoang/reactive-http-client/releases/tag/v1.0.0
