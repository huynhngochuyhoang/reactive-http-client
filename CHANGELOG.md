# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [Unreleased]

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

[Unreleased]: https://github.com/huynhngochuyhoang/reactive-http-client/compare/v1.1.0...HEAD
[1.1.0]: https://github.com/huynhngochuyhoang/reactive-http-client/compare/v1.0.0...v1.1.0
[1.0.0]: https://github.com/huynhngochuyhoang/reactive-http-client/releases/tag/v1.0.0
