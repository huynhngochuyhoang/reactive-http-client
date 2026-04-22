# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [Unreleased]

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

[Unreleased]: https://github.com/huynhngochuyhoang/reactive-http-client/compare/v1.8.0...HEAD
[1.8.0]: https://github.com/huynhngochuyhoang/reactive-http-client/compare/v1.7.0...v1.8.0
[1.7.0]: https://github.com/huynhngochuyhoang/reactive-http-client/compare/v1.6.0...v1.7.0
[1.6.0]: https://github.com/huynhngochuyhoang/reactive-http-client/compare/v1.5.1...v1.6.0
[1.5.1]: https://github.com/huynhngochuyhoang/reactive-http-client/compare/v1.5.0...v1.5.1
[1.4.0]: https://github.com/huynhngochuyhoang/reactive-http-client/compare/v1.3.0...v1.4.0
[1.3.0]: https://github.com/huynhngochuyhoang/reactive-http-client/compare/v1.2.0...v1.3.0
[1.2.0]: https://github.com/huynhngochuyhoang/reactive-http-client/compare/v1.1.0...v1.2.0
[1.1.0]: https://github.com/huynhngochuyhoang/reactive-http-client/compare/v1.0.0...v1.1.0
[1.0.0]: https://github.com/huynhngochuyhoang/reactive-http-client/releases/tag/v1.0.0
