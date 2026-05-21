# Reactive HTTP Client — Roadmap V5

> **Status:** completed for `2.4.0` on 2026-05-21. V4 completed the starter's
> extension points, release-doc checks, conflict audit, and observability
> guardrails. V5 hardened the starter itself: native readiness, clearer HTTP
> response contracts, compatibility confidence, and correctness around
> URI/body edge cases.

V5 keeps the same three-bucket shape:

1. **Features to add** — narrowly scoped capabilities users still have to wire
   around the starter.
2. **Features to optimize** — existing behavior that can become clearer or less
   surprising without broad redesign.
3. **Bugs / correctness to fix** — contracts that need stronger guarantees.

The bias for V5: improve confidence in production and framework upgrades before
adding new integration concepts. The starter should stay an HTTP client starter,
not become a general workflow, service-discovery, or API-gateway layer.

---

## 1. Features to add

### 1.1 Spring AOT and native-image readiness

**Why:** The starter creates interface proxies, binds nested configuration
objects, scans annotated clients, and optionally wires auth, observability, and
Resilience4j integrations. Native-image applications need these paths to be
declared intentionally instead of relying on runtime classpath behavior.

**What:**

- Add Spring `RuntimeHints` for starter annotations, proxy interfaces, nested
  configuration properties, and optional integration types where needed.
- Add a small AOT smoke test application that registers at least one annotated
  client through `@EnableReactiveHttpClients`.
- Document native-image support boundaries, including optional modules and any
  unsupported customization patterns.

**Acceptance:**

- [x] Runtime hints cover proxy creation for scanned `@ReactiveHttpClient`
      interfaces.
- [x] AOT test proves the starter context can be processed without missing
      reflection/proxy hints.
- [x] Docs include a native-image section with supported and unsupported paths.

---

### 1.2 Non-streaming response envelope support

**Why:** Normal JSON clients sometimes need successful response status and
headers, such as `ETag`, pagination headers, `Location`, `204`, or `304`.
Today the starter exposes ordinary decoded bodies and a streaming
`Mono<ResponseEntity<Flux<DataBuffer>>>` path, but not a buffered
`ResponseEntity<T>` contract.

**What:**

- Support `Mono<ResponseEntity<T>>` for successful non-streaming responses.
- Preserve the existing error decoder for non-2xx responses unless a mapper
  explicitly handles the response.
- Support `ResponseEntity<Void>` for success-without-body cases.
- Keep `Flux<T>` semantics unchanged.

**Acceptance:**

- [x] `Mono<ResponseEntity<T>>` returns decoded body, status, and headers for
      successful responses.
- [x] `Mono<ResponseEntity<Void>>` handles `204` and empty `200` responses.
- [x] Non-2xx responses still use the existing error category and mapper
      fallback model.
- [x] Docs explain when to use `ResponseEntity<T>` vs normal body returns vs
      streaming response entities.

---

### 1.3 Built-in Problem Detail error mapping

**Why:** V4 added `ErrorResponseMapper`, but many Spring services already return
RFC 7807 / `application/problem+json` payloads. Every consumer should not need
to write the same mapper to preserve problem fields.

**What:**

- Add an opt-in built-in mapper for `application/problem+json`.
- Preserve `statusCode`, raw `responseBody`, and `ErrorCategory`.
- Expose parsed problem details through a starter exception type or accessor
  without replacing the existing exception model.

**Acceptance:**

- [x] Mapper recognizes compatible problem-detail responses when enabled.
- [x] Invalid or partial problem payloads fall back to default decoding.
- [x] Tests cover `4xx`, `5xx`, missing content type, invalid JSON, and mapper
      ordering relative to user-provided mappers.
- [x] Docs include a small example for application error handling.

---

## 2. Features to optimize

### 2.1 Move request timeout configuration out of resilience

**Why:** `reactive.http.clients.<name>.resilience.timeout-ms` is a real
per-request response timeout, not a Resilience4j operator. Keeping it under
`resilience` makes configuration harder to explain and easy to misread.

**What:**

- Add a clearer client-level request timeout property, such as
  `reactive.http.clients.<name>.request.timeout-ms`.
- Keep the current `resilience.timeout-ms` as a deprecated alias for one
  compatibility cycle.
- Preserve the existing precedence:
  `@TimeoutMs` > `@ApiRef timeout-ms` > client request timeout.

**Acceptance:**

- [x] New property binds and is documented with metadata.
- [x] Existing `resilience.timeout-ms` continues to work and emits migration
      guidance.
- [x] Conflict behavior is explicit if both properties are configured.
- [x] Timeout docs and guardrail docs use the new canonical property.

---

### 2.2 Auto-configuration and override contract audit

**Why:** The starter now has many conditional beans: observers, health,
Resilience4j adapters, auth factories, filters, exchange logging, and optional
OpenTelemetry. Small changes to `@Conditional*` rules can silently change what
applications get.

**What:**

- Add focused `ApplicationContextRunner` tests for each optional classpath and
  user-override path.
- Document named built-in beans that users may override intentionally.
- Check that disabled properties actually suppress the expected beans.

**Acceptance:**

- [x] Tests cover no optional dependencies, Micrometer only, Resilience4j only,
      actuator only, and user-supplied observer/auth/customizer beans.
- [x] Built-in bean names and override semantics are documented.
- [x] No existing public override path is broken.

---

### 2.3 Compatibility smoke matrix

**Why:** This starter depends on Spring Boot, WebFlux, Reactor Netty,
Resilience4j, Micrometer, and OpenTelemetry. A single happy-path JDK build is
not enough signal before release.

**What:**

- Add a small compatibility smoke profile that compiles and starts a minimal app
  against the supported Spring Boot line.
- Keep the main test suite on one primary dependency set to avoid slow CI.
- Add release notes that state the tested framework versions.

**Acceptance:**

- [x] CI includes a smoke job or profile that can be run before release.
- [x] The smoke app exercises one declarative client, auth disabled, metrics
      enabled, and a mocked exchange.
- [x] Release docs name the tested Java, Spring Boot, Reactor Netty,
      Resilience4j, Micrometer, and OpenTelemetry versions.

---

## 3. Bugs / correctness to fix

### 3.1 URI encoding and template edge-case audit

**Why:** Declarative clients are only safe if path variables, default query
parameters, method query parameters, and `@ApiRef` paths encode consistently.
Double encoding, raw slash handling, blank query values, and repeated parameters
are easy to regress.

**What:**

- Audit path-template expansion for reserved characters, spaces, slashes, and
  already-encoded values.
- Audit query merging for configured defaults, method parameters, existing query
  strings, repeated values, and empty values.
- Document the exact encoding contract.

**Acceptance:**

- [x] Tests cover reserved characters in `@PathVar` and `@QueryParam`.
- [x] Tests cover default query values combined with method values and existing
      query strings.
- [x] Tests cover `@ApiRef` paths with templates and query strings.
- [x] Docs state whether callers pass raw or pre-encoded values.

---

### 3.2 Body consumption and cancellation safety

**Why:** Error decoding, structured mappers, exchange logging, auth signing, and
observers all touch request or response data. Reactive body handling must avoid
double consumption, hanging cancellation paths, and leaked buffers.

**What:**

- Add tests around cancellation before response, cancellation during body read,
  mapper fallback after body read, and body logging disabled paths.
- Audit `DataBuffer` handling in streaming and error paths.
- Keep body capture bounded by existing codec and logging limits.

**Acceptance:**

- [x] Cancellation tests prove lifecycle hooks and observers receive the
      expected terminal signal once.
- [x] Error mapper fallback does not consume the response body twice.
- [x] Streaming response paths do not buffer accidentally.
- [x] No new unbounded body buffering is introduced.

---

### 3.3 Validation for numeric configuration bounds

**Why:** Several settings are numeric limits or durations. Invalid values should
fail fast with actionable messages instead of silently producing surprising
Netty, Resilience4j, or metrics behavior.

**What:**

- Audit timeout, pool, codec, histogram, and health-threshold properties.
- Add validation for negative durations, impossible thresholds, and empty SLO
  boundary lists where the current behavior is ambiguous.
- Keep `0` semantics only where they are already documented.

**Acceptance:**

- [x] Validation tests cover invalid timeout, pool, codec, histogram, and health
      values.
- [x] Error messages include the property name and accepted range.
- [x] Docs preserve every documented `0 = disabled/default` rule.

---

## Suggested Priority Order

1. URI encoding audit and numeric validation.
2. Body consumption and cancellation safety.
3. Request timeout property migration.
4. Auto-configuration override audit.
5. Non-streaming response envelope support.
6. Problem Detail mapper.
7. AOT/native readiness and compatibility smoke matrix.
