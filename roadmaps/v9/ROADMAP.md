# Reactive HTTP Client — Roadmap V9

> **Status:** completed and released as `2.8.0`. V9 focused on declarative
> contract completeness, startup validation, and resource-safe body handling.

V9 keeps the same three-bucket shape:

1. **Features to add** — small annotation and test-helper additions that fill
   clear gaps in the existing HTTP contract.
2. **Features to optimize** — startup validation and streaming documentation
   that should become easier to trust.
3. **Bugs / correctness to fix** — invalid method shapes and response-body
   paths that should fail or drain consistently.

The bias for V9: reject ambiguous client declarations before traffic reaches a
downstream, and make body ownership explicit for bodiless and streaming
responses. Add only HTTP primitives that fit the current annotation model; do
not turn the starter into a general-purpose protocol framework.

Non-goals:

- Do not add browser-oriented CORS handling to the outbound client starter.
- Do not add arbitrary HTTP method strings to static annotations.
- Do not buffer streaming response bodies to improve diagnostics.
- Do not auto-consume `Flux<DataBuffer>` bodies after returning them to callers.
- Do not change the documented raw-value URI encoding contract.
- Do not remove compatibility aliases or constructors in a minor release.

---

## 1. Features to add

### 1.1 Declarative `@HEAD` and `@OPTIONS` support

**Why:** The retry-safety classifier already understands `HEAD` and `OPTIONS`,
but statically declared clients cannot express those methods without
configuration-backed `@ApiRef`. Health probes, metadata lookups, and protocol
capability checks should not require a dynamic API map.

**What:**

- Add `@HEAD` and `@OPTIONS` annotations with the same path-template contract as
  the existing verb annotations.
- Parse them into the same immutable method metadata and request plan.
- Keep `TRACE` out of the static annotation surface unless a concrete use case
  justifies exposing it.
- Update AOT, annotation-reference, and mock-client coverage.

**Acceptance:**

- [ ] A `@HEAD("/objects/{id}")` method sends `HEAD` and resolves path
      variables normally.
- [ ] An `@OPTIONS("/objects")` method sends `OPTIONS`.
- [ ] Both methods participate in timeout, resilience, lifecycle, observer, and
      exchange-log paths exactly like existing verbs.
- [ ] `@ApiRef` remains mutually exclusive with every static verb annotation.
- [ ] Native-image reflection hints cover the new annotations without special
      application configuration.

---

### 1.2 Multi-value outbound header parameters

**Why:** `@QueryParam` supports collections and arrays, but `@HeaderParam`
currently reduces ordinary values and map entries to one string. HTTP headers
such as `Accept`, `Cache-Control`, and forwarding headers can legitimately
carry repeated values. Applications should not need a custom `WebClient` filter
for that basic request shape.

**What:**

- Let `@HeaderParam("Name")` accept a scalar, collection, or array.
- Let map-based `@HeaderParam` preserve scalar values and support collection or
  array values.
- Keep null values omitted and preserve caller-provided value order.
- Validate every expanded value against the existing CRLF/control-character
  guard.
- Define precedence as header-name replacement: a method-level header replaces
  same-name configured defaults case-insensitively.

**Acceptance:**

- [ ] A collection-valued `@HeaderParam("Accept")` emits repeated values in
      order.
- [ ] An array-valued map entry emits repeated values in order.
- [ ] Null method arguments remain omitted.
- [ ] A null collection element is omitted or rejected with a documented,
      tested rule.
- [ ] A CRLF/control character in any expanded value is rejected before the
      request is sent.
- [ ] Scalar header behavior remains unchanged.

---

### 1.3 Header-aware test-helper assertions and response builders

**Why:** Multi-value request headers and body-drain fixes need compact tests.
`RecordedExchangeAssertions` already understands repeated request headers, but
mock response construction still requires low-level `ClientResponse` assembly
for headers, binary bodies, and deliberately unexpected bodies.

**What:**

- Add minimal mock response builder helpers for headers and raw body content.
- Keep existing `json(...)` and `empty(...)` conveniences unchanged.
- Add examples for repeated request headers and an unexpected body on a
  bodiless endpoint.
- Avoid a broad response-stubbing DSL.

**Acceptance:**

- [ ] Tests can build a response with custom headers without manual
      `ClientResponse.create(...)` boilerplate.
- [ ] Tests can return a raw text or byte body to a `Mono<Void>` method.
- [ ] Existing helper methods remain source compatible.
- [ ] Docs include one compact repeated-header example.

---

### 1.4 Opt-in automatic redirect following

**Why:** V8 made visible 3xx responses a documented normal-response contract:
the default transport leaves redirect handling to application code. Reactor
Netty can also follow redirects before the proxy processes the response, but the
starter does not expose that capability through per-client configuration.
Applications that call stable redirecting endpoints should not need a custom
transport solely to opt in.

**What:**

- Add per-client `follow-redirects`, defaulting to `false`.
- When enabled, configure the starter-created Reactor Netty transport to follow
  redirects before error decoding and response mapping.
- Preserve visible 3xx responses when the option is disabled.
- Document redirect header behavior, especially for cross-authority redirects;
  do not add custom logic that blindly forwards sensitive credentials.
- Audit `301`, `302`, `303`, `307`, and `308` behavior for methods with and
  without request bodies.
- Keep redirect-loop handling bounded by the transport and document the
  resulting failure behavior.

**Acceptance:**

- [ ] `follow-redirects: false` preserves the V8 contract: callers can receive
      a visible `Mono<ResponseEntity<T>>` 3xx response and inspect `Location`.
- [ ] `follow-redirects: true` follows a same-origin `GET` redirect and exposes
      the final response to the proxy.
- [ ] A final 4xx or 5xx response after a redirect still flows through
      `DefaultErrorDecoder`.
- [ ] Tests cover same-origin and cross-authority redirects without leaking
      sensitive headers.
- [ ] Tests cover a redirect loop or excessive redirect chain.
- [ ] Docs explain replay risk for request bodies, especially non-repeatable
      bodies with `307` and `308`.
- [ ] Observer and exchange-log docs state whether diagnostics describe the
      initial request URL, final request URL, or both.

---

## 2. Features to optimize

### 2.1 Eager declarative method-contract validation

**Why:** Proxy construction already validates several client-level settings,
but some invalid endpoint declarations are still parsed lazily or can be
silently reduced to one interpretation. Ambiguous client interfaces should fail
before serving traffic.

**What:**

- Eagerly parse every abstract endpoint method during proxy construction.
- Reject multiple static HTTP verb annotations on one method.
- Reject multiple `@Body` parameters.
- Reject a method that has neither a static HTTP verb nor `@ApiRef`.
- Validate non-blank `@PathVar`, `@QueryParam`, `@HeaderParam`, `@ApiName`, and
  idempotency-key annotation values consistently.
- Keep Java default methods available as interface helpers without requiring an
  endpoint annotation.

**Acceptance:**

- [ ] Invalid endpoint declarations fail during proxy construction, not first
      invocation.
- [ ] Two verb annotations fail with a message naming the method and
      conflicting annotations.
- [ ] Two `@Body` parameters fail with a message naming the method.
- [ ] An unannotated abstract method fails while a Java default helper method
      remains valid.
- [ ] Inherited endpoint methods are validated.
- [ ] AOT smoke coverage exercises inherited validation.

---

### 2.2 URI-template and `@ApiRef` startup validation parity

**Why:** Static annotations and `@ApiRef` ultimately feed the same request
builder, but configuration-backed APIs currently receive a narrower startup
check. Typos in HTTP methods, malformed base URLs, and path-variable mismatches
should fail with one consistent contract.

**What:**

- Validate effective base URLs before building the client.
- Validate `@ApiRef.method` against the supported outbound HTTP methods.
- Audit static and configured path templates with the same placeholder rules.
- Reject duplicate or missing path-variable bindings where the request cannot
  be constructed deterministically.
- Preserve empty paths as an intentional base-URL request, with the existing
  warning where applicable.
- Keep literal query strings and raw-value percent encoding unchanged.

**Acceptance:**

- [ ] A malformed effective base URL fails startup with the client name and
      configuration source.
- [ ] An unsupported or misspelled `@ApiRef.method` fails startup.
- [ ] Static annotations and `@ApiRef` use the same placeholder validation.
- [ ] Missing, duplicate, and unused `@PathVar` declarations have documented,
      tested outcomes.
- [ ] Existing raw-value path and query encoding tests remain unchanged.

---

### 2.3 Streaming response ownership contract

**Why:** `Flux<DataBuffer>` and `Mono<ResponseEntity<Flux<DataBuffer>>>` expose
reference-counted buffers directly to callers. The starter must state exactly
which component owns release, when logical-call terminal signals fire, and what
happens on cancellation. The envelope form is especially easy to misread
because the outer `Mono` can complete before the streamed body is consumed.

**What:**

- Audit direct `Flux<DataBuffer>` and response-envelope streaming paths.
- Document buffer ownership for pass-through, manual consumption, discard, and
  cancellation cases.
- Define terminal observer, lifecycle, and exchange-log timing for
  `Mono<ResponseEntity<Flux<DataBuffer>>>`.
- Keep streaming bodies unbuffered.
- Add leak-sensitive tests using retained and released `DataBuffer` instances.

**Acceptance:**

- [ ] Docs distinguish outer response-envelope completion from streamed-body
      completion.
- [ ] Tests prove buffer release behavior on full consumption and cancellation.
- [ ] Pass-through examples do not imply that manually consumed buffers are
      released automatically.
- [ ] Observer, lifecycle, and exchange-log timing matches the documented
      contract.
- [ ] No streaming path introduces aggregate buffering.

---

## 3. Bugs / correctness to fix

### 3.1 Drain unexpected bodies for all bodiless response shapes

**Why:** `Mono<ResponseEntity<Void>>` explicitly releases an unexpected
upstream body before returning metadata, but plain `Mono<Void>` still delegates
to `bodyToMono(Void.class)`. A downstream that occasionally returns content for
a modeled-bodiless endpoint should not force connection churn or leave pooled
connection reuse dependent on response shape.

**What:**

- Audit every successful bodiless response path.
- Drain or release unexpected content for plain `Mono<Void>` and
  `ResponseEntity<Void>` consistently.
- Keep 4xx/5xx decoding behavior unchanged.
- Verify connection reuse with a real Reactor Netty server and pooled client.

**Acceptance:**

- [ ] `Mono<Void>` drains an unexpected 2xx body before completing.
- [ ] `Mono<ResponseEntity<Void>>` retains status and headers while draining an
      unexpected body.
- [ ] A pooled connection remains reusable after each bodiless response shape.
- [ ] Error responses still flow through `DefaultErrorDecoder`.

---

### 3.2 Reject ambiguous body and verb metadata before caching

**Why:** Method metadata is cached and reused for every invocation. If parsing
silently lets a later `@Body` parameter overwrite an earlier one, or chooses one
verb from conflicting annotations, every request is deterministically wrong.
The parser should reject ambiguity before creating a request plan.

**What:**

- Count static verb annotations explicitly instead of relying on `else if`
  selection.
- Detect a second `@Body` parameter before replacing the first index.
- Keep exception messages deterministic for startup diagnostics and tests.
- Ensure invalid metadata is never cached as a usable request plan.

**Acceptance:**

- [ ] A method with two static verbs is rejected.
- [ ] A method with two bodies is rejected.
- [ ] Repeated parse attempts produce the same actionable failure.
- [ ] Valid inherited and overloaded methods still cache independently.

---

### 3.3 Streaming-envelope cancellation and terminal reporting correctness

**Why:** Returning `Mono<ResponseEntity<Flux<DataBuffer>>>` transfers body
consumption to the caller after the response envelope is emitted. Cancellation,
discard, or decode failure in the inner stream must not leak buffers or produce
terminal diagnostics that claim a stronger completion guarantee than the
runtime provides.

**What:**

- Reproduce cancellation before and during inner-stream consumption.
- Ensure discarded buffers are released where ownership remains with the
  starter.
- Keep terminal reporting subscription-local.
- Record or document envelope completion separately from inner-body
  consumption rather than conflating them.

**Acceptance:**

- [ ] Cancelling before inner-stream subscription does not leak retained
      buffers.
- [ ] Cancelling during consumption releases discarded buffers.
- [ ] Concurrent subscriptions do not cross-report streaming terminal state.
- [ ] Diagnostics use wording that reflects envelope completion accurately.

---

## Suggested Priority Order

1. Reject ambiguous body and verb metadata before caching.
2. Eager declarative method-contract validation.
3. URI-template and `@ApiRef` startup validation parity.
4. Declarative `@HEAD` and `@OPTIONS` support.
5. Drain unexpected bodies for all bodiless response shapes.
6. Opt-in automatic redirect following.
7. Streaming response ownership and terminal-reporting contract.
8. Multi-value outbound headers and focused test-helper support.
9. Compatibility automation, AOT/native-image evidence, documentation, and
   release readiness.
