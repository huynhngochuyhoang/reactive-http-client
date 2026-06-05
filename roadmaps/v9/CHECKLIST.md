# Roadmap V9 Execution Checklist

> Companion to [`ROADMAP.md`](ROADMAP.md). This file tracks execution;
> keep rationale and design discussion in the roadmap.

---

## Priority 1 — Reject Ambiguous Body and Verb Metadata

### [x] 3.2 Reject ambiguous body and verb metadata before caching
- [x] Count static HTTP verb annotations explicitly instead of relying on
  ordered `else if` selection.
- [x] Reject a method with more than one static HTTP verb annotation.
- [x] Include the method and conflicting annotations in the failure message.
- [x] Detect a second `@Body` parameter before replacing the first body index.
- [x] Include the method in the multiple-body failure message.
- [x] Ensure invalid metadata never enters `MethodMetadataCache`.
- [x] Test repeated parse attempts for the same deterministic failure.
- [x] Verify valid inherited and overloaded methods still cache independently.

---

## Priority 2 — Eager Declarative Method Validation

### [x] 2.1 Eager declarative method-contract validation
- [x] Parse every abstract endpoint method during proxy construction.
- [x] Reject an abstract method with neither a static HTTP verb nor `@ApiRef`.
- [x] Keep Java default methods available as interface helpers without endpoint
  annotations.
- [x] Validate inherited abstract endpoint methods during proxy construction.
- [x] Validate non-blank `@PathVar` names.
- [x] Validate non-blank `@QueryParam` names.
- [x] Validate non-blank non-map `@HeaderParam` names.
- [x] Validate non-blank `@ApiName` values.
- [x] Keep idempotency-key annotation value validation consistent.
- [x] Test that invalid declarations fail at proxy construction rather than
  first invocation.
- [x] Extend AOT smoke coverage for inherited endpoint validation.

---

## Priority 3 — URI Template and `@ApiRef` Startup Validation

### [x] 2.2 URI-template and `@ApiRef` startup validation parity
- [x] Validate the effective base URL before building the client.
- [x] Include the client name and annotation-or-property source in malformed
  base-URL failures.
- [x] Define the supported outbound HTTP-method set for configured APIs.
- [x] Reject unsupported or misspelled `@ApiRef.method` values during proxy
  construction.
- [x] Apply the same path-template placeholder validation to static annotations
  and configured `@ApiRef` paths.
- [x] Define and test the outcome for missing `@PathVar` bindings.
- [x] Define and test the outcome for duplicate `@PathVar` bindings.
- [x] Define and test the outcome for unused `@PathVar` declarations.
- [x] Preserve intentional empty paths and the existing warning behavior.
- [x] Preserve literal query strings in annotation and configured API paths.
- [x] Verify existing raw-value path and query encoding tests remain unchanged.

---

## Priority 4 — Declarative `@HEAD` and `@OPTIONS`

### [x] 1.1 Declarative `@HEAD` and `@OPTIONS` support
- [x] Add `@HEAD` with the existing static path-template annotation shape.
- [x] Add `@OPTIONS` with the existing static path-template annotation shape.
- [x] Parse both annotations into immutable method metadata and request plans.
- [x] Keep `TRACE` out of the static annotation surface.
- [x] Verify `@HEAD("/objects/{id}")` sends `HEAD` and resolves path variables.
- [x] Verify `@OPTIONS("/objects")` sends `OPTIONS`.
- [x] Verify timeout, resilience, lifecycle, observer, and exchange-log paths
  work for both methods.
- [x] Reject combining `@ApiRef` with `@HEAD` or `@OPTIONS`.
- [x] Update annotation-reference docs.
- [x] Update mock-client coverage.
- [x] Verify AOT and native-image reflection hints cover the new annotations.

---

## Priority 5 — Drain Bodiless Responses

### [x] 3.1 Drain unexpected bodies for all bodiless response shapes
- [x] Audit every successful bodiless response path.
- [x] Drain or release unexpected 2xx content for plain `Mono<Void>`.
- [x] Keep `Mono<ResponseEntity<Void>>` status and headers while draining
  unexpected content.
- [x] Keep 4xx and 5xx responses routed through `DefaultErrorDecoder`.
- [x] Add real Reactor Netty pooled-client coverage for plain `Mono<Void>`.
- [x] Add real Reactor Netty pooled-client coverage for
  `Mono<ResponseEntity<Void>>`.
- [x] Verify a pooled connection remains reusable after both response shapes.
- [x] Document the bodiless-response drain contract where response envelopes are
  described.

---

## Priority 6 — Opt-In Automatic Redirect Following

### [x] 1.4 Opt-in automatic redirect following
- [x] Add per-client `follow-redirects`, defaulting to `false`.
- [x] Update Spring configuration metadata and generated property docs.
- [x] Configure the starter-created Reactor Netty transport to follow redirects
  only when the option is enabled.
- [x] Preserve visible 3xx pass-through when the option is disabled.
- [x] Test a visible `Mono<ResponseEntity<T>>` redirect with its `Location`
  header when disabled.
- [x] Test a same-origin `GET` redirect exposing the final response when
  enabled.
- [x] Test final 4xx and 5xx responses after redirects through
  `DefaultErrorDecoder`.
- [x] Audit `301`, `302`, `303`, `307`, and `308` behavior with and without
  request bodies.
- [x] Test cross-authority redirects without leaking sensitive headers.
- [x] Test a redirect loop or excessive redirect chain.
- [x] Document request-body replay risks, especially non-repeatable bodies with
  `307` and `308`.
- [x] Document whether observer and exchange-log diagnostics expose initial,
  final, or both request URLs.

---

## Priority 7 — Streaming Response Ownership

### [x] 2.3 Streaming response ownership contract
- [x] Audit direct `Flux<DataBuffer>` response handling.
- [x] Audit `Mono<ResponseEntity<Flux<DataBuffer>>>` response handling.
- [x] Document buffer ownership for pass-through, manual consumption, discard,
  and cancellation.
- [x] Distinguish outer response-envelope completion from inner streamed-body
  completion.
- [x] Define observer, lifecycle, and exchange-log timing for response-envelope
  streaming.
- [x] Keep streaming bodies unbuffered.
- [x] Add leak-sensitive tests for full stream consumption.
- [x] Add leak-sensitive tests for stream cancellation.
- [x] Ensure pass-through examples do not imply automatic release after manual
  consumption.

### [x] 3.3 Streaming-envelope cancellation and terminal reporting correctness
- [x] Reproduce cancellation before inner-stream subscription.
- [x] Reproduce cancellation during inner-stream consumption.
- [x] Release discarded buffers where ownership remains with the starter.
- [x] Keep streaming terminal reporting subscription-local.
- [x] Test concurrent streaming-envelope subscriptions.
- [x] Ensure diagnostics describe envelope completion accurately without
  claiming full streamed-body consumption.

---

## Priority 8 — Multi-Value Headers and Test Helpers

### [x] 1.2 Multi-value outbound header parameters
- [x] Let named `@HeaderParam` arguments accept scalar values.
- [x] Let named `@HeaderParam` arguments accept collections.
- [x] Let named `@HeaderParam` arguments accept arrays.
- [x] Let map-based `@HeaderParam` entries accept scalar, collection, and array
  values.
- [x] Preserve caller-provided header-value order.
- [x] Keep null method arguments omitted.
- [x] Define and test the null collection-element rule.
- [x] Validate every expanded value against the existing CRLF and
  control-character guard.
- [x] Keep scalar header behavior unchanged.
- [x] Replace same-name configured defaults case-insensitively.
- [x] Document multi-value header behavior and precedence.

### [x] 1.3 Header-aware test-helper assertions and response builders
- [x] Add minimal mock response helpers for custom headers.
- [x] Add minimal mock response helpers for raw text or byte bodies.
- [x] Keep existing `json(...)` and `empty(...)` helpers source compatible.
- [x] Avoid introducing a broad response-stubbing DSL.
- [x] Add a mock example for repeated outbound request headers.
- [x] Add a mock example for an unexpected body on a `Mono<Void>` endpoint.

---

## Priority 9 — Compatibility Evidence and Release Readiness

### [x] Compatibility automation
- [x] Update the API compatibility baseline only when the prior release
  artifact is available.
- [x] Run the published-artifact API compatibility report.
- [x] Verify additive annotations, properties, and helper methods remain binary
  compatible.
- [x] Keep internal implementation classes and fixtures outside the public API
  check.
- [x] Verify AOT smoke coverage includes inherited endpoint validation.
- [x] Verify the scheduled/manual native-image smoke path covers the final
  starter artifact.

### [x] Release readiness
- [x] Add V9 entries to `CHANGELOG.md` under Added/Changed/Fixed/Docs.
- [x] Keep README concise and link detailed docs.
- [x] Update annotation-reference docs for `@HEAD` and `@OPTIONS`.
- [x] Update configuration metadata and generated property docs for
  `follow-redirects`.
- [x] Update redirect docs for opt-in forwarding, sensitive-header behavior,
  loop handling, and request-body replay risks.
- [x] Update streaming docs for buffer ownership and envelope completion.
- [x] Update test-helper docs for repeated headers and raw mock responses.
- [x] Verify body-drain docs do not promise connection reuse after
  cancellation.
- [x] Verify redirect docs preserve the V8 visible-3xx default contract.
- [x] Run `mvn test`.
- [x] Run `mvn -Prelease-smoke test`.
- [x] Run `mvn -Papi-compatibility -DskipTests verify`.
- [x] Run API compatibility fixtures.
- [x] Run `git diff --check`.
- [x] Call out any breaking behavior before release.
