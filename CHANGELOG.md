# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [Unreleased]

### Added

- Added observability error categories for network failures:
  - `ErrorCategory.CONNECT_ERROR` for `ConnectException`
  - `ErrorCategory.UNKNOWN_HOST` for `UnknownHostException`

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

[Unreleased]: https://github.com/huynhngochuyhoang/reactive-http-client/compare/v1.5.1...HEAD
[1.5.1]: https://github.com/huynhngochuyhoang/reactive-http-client/compare/v1.5.0...v1.5.1
[1.4.0]: https://github.com/huynhngochuyhoang/reactive-http-client/compare/v1.3.0...v1.4.0
[1.3.0]: https://github.com/huynhngochuyhoang/reactive-http-client/compare/v1.2.0...v1.3.0
[1.2.0]: https://github.com/huynhngochuyhoang/reactive-http-client/compare/v1.1.0...v1.2.0
[1.1.0]: https://github.com/huynhngochuyhoang/reactive-http-client/compare/v1.0.0...v1.1.0
[1.0.0]: https://github.com/huynhngochuyhoang/reactive-http-client/releases/tag/v1.0.0
