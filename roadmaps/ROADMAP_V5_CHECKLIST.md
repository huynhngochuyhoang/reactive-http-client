# Roadmap V5 Execution Checklist

> Companion to [`ROADMAP_V5.md`](ROADMAP_V5.md). This file tracks execution;
> keep rationale and design discussion in the roadmap.

---

## Priority 1 — Correctness guardrails

### [x] 3.1 URI encoding and template edge-case audit
- [x] Cover reserved characters in `@PathVar` and `@QueryParam`.
- [x] Cover default query values combined with method values and existing query
  strings.
- [x] Cover `@ApiRef` paths with templates and query strings.
- [x] Document whether callers pass raw or pre-encoded values.

### [x] 3.3 Validation for numeric configuration bounds
- [x] Audit timeout, pool, codec, histogram, and health-threshold properties.
- [x] Add validation for negative durations and impossible thresholds.
- [x] Preserve documented `0 = disabled/default` behavior.
- [x] Ensure validation messages include the property name and accepted range.

---

## Priority 2 — Reactive body safety

### [x] 3.2 Body consumption and cancellation safety
- [x] Test cancellation before response and during response body read.
- [x] Verify lifecycle hooks and observers receive one terminal signal.
- [x] Verify mapper fallback does not consume the body twice.
- [x] Verify streaming responses do not buffer accidentally.

---

## Priority 3 — Configuration clarity

### [x] 2.1 Move request timeout configuration out of resilience
- [x] Add the new canonical client request-timeout property.
- [x] Keep `resilience.timeout-ms` as a deprecated alias for one compatibility
  cycle.
- [x] Define conflict behavior when both properties are configured.
- [x] Update metadata, timeout docs, and guardrail docs.

### [x] 2.2 Auto-configuration and override contract audit
- [x] Add context-runner tests for optional dependency combinations.
- [x] Add tests for user-supplied observer/auth/customizer override paths.
- [x] Document named built-in beans that users may override.
- [x] Verify disabled properties suppress expected beans.

---

## Priority 4 — HTTP contract ergonomics

### [x] 1.2 Non-streaming response envelope support
- [x] Support `Mono<ResponseEntity<T>>` for successful responses.
- [x] Support `Mono<ResponseEntity<Void>>`.
- [x] Preserve existing non-2xx error decoder and mapper behavior.
- [x] Document response-envelope usage.

### [x] 1.3 Built-in Problem Detail error mapping
- [x] Add opt-in mapper for `application/problem+json`.
- [x] Preserve status, raw body, and `ErrorCategory`.
- [x] Test valid, invalid, missing-content-type, `4xx`, and `5xx` cases.
- [x] Document application handling examples.

---

## Priority 5 — Release compatibility

### [ ] 1.1 Spring AOT and native-image readiness
- [ ] Add runtime hints for client proxies, annotations, and configuration
  properties.
- [ ] Add an AOT smoke test application.
- [ ] Document supported native-image paths and limits.

### [ ] 2.3 Compatibility smoke matrix
- [ ] Add a release smoke job or profile.
- [ ] Exercise a minimal declarative client with metrics enabled.
- [ ] Capture tested Java and framework versions in release docs.

---

## Release Readiness

- [ ] `CHANGELOG.md` has V5 entries grouped under Added/Changed/Fixed/Docs.
- [ ] README stays short and links to detailed docs.
- [ ] New properties have configuration metadata and metadata tests.
- [ ] New public APIs have focused tests and concise docs.
- [ ] `mvn test` passes.
- [ ] Breaking behavior, if any, is explicitly called out before release.
