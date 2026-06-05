# Reactive HTTP Client — Roadmap V2

> **Status:** baseline v1.14.0 (2026-05-12). The V1 roadmap
> ([`v1/ROADMAP.md`](../v1/ROADMAP.md) + [`v1/CHECKLIST.md`](../v1/CHECKLIST.md)) is functionally complete: OpenTelemetry
> tracing and baggage propagation, multipart, test helpers, per-client pools,
> HTTP proxy + TLS/mTLS, actuator health indicator + pool metrics, OAuth2
> client-credentials, streaming passthrough, per-method resilience,
> configurable correlation-ID, API-map routing via `@ApiRef`, plus every V1
> optimisation and correctness fix shipped.

This V2 roadmap follows the same three-bucket shape as [`v1/ROADMAP.md`](../v1/ROADMAP.md):

1. **Features to add** — net-new capabilities users currently have to build themselves.
2. **Features to optimize** — existing code that works but can be made faster, clearer,
   or more ergonomic.
3. **Bugs / correctness to fix** — behaviour that is wrong, fragile, or surprising.

Execution checklists live under `tasks/v2/`; this file stays as the product and
technical roadmap.

---

## 1. Features to add

### 1.1 AWS SigV4 auth provider

**Origin:** V1 Priority 5 / 1.7 (deferred sub-item).

**Why:** OAuth 2.0 client-credentials shipped in v1.9.0, but the second half of
the original "Built-in auth providers" item — AWS Signature Version 4 — is the
most commonly requested auth scheme for talking to AWS-hosted internal
services. Hand-rolling SigV4 in application code is a recurring source of
production bugs (URL-encoded path segments, empty query strings, multi-line
headers all have subtle canonicalisation rules).

**Why it was deferred:** SigV4 is ~300 LOC of cryptographic code (canonical
request construction, double-HMAC key derivation, regional / service scope
handling). Shipping it without thorough AWS reference test vectors is a
footgun — production HMAC bugs typically only surface under very specific
request shapes, and a broken signature fails opaquely with `403`.

**Design notes:**

- Class: `AwsSigV4AuthProvider implements AuthProvider`, in the
  `auth/` package next to the existing providers.
- Reuse the cached body bytes captured at
  `ReactiveClientInvocationHandler.java:~175` (the same `Mono.cache()` that
  feeds the existing `OutboundAuthFilter`); SigV4 needs the SHA-256 of the
  unsigned body, so we want one allocation per invocation, not one per retry.
- Configurable inputs: `accessKeyId`, `secretAccessKey`, `region`, `service`,
  optional `sessionToken` (for STS-issued credentials). Signature time read
  from `Clock` so it's mockable in tests.
- Builder API mirroring `OAuth2ClientCredentialsTokenProvider.Builder`.

**Acceptance bar:**

- [ ] `AwsSigV4AuthProvider` implementation with builder + Javadoc.
- [ ] Unit tests pass against the official **AWS SigV4 test-suite** vectors
      (https://github.com/awslabs/aws-c-auth/tree/main/tests/aws-sig-v4-test-suite).
      Every vector in `get-vanilla`, `get-utf8`, `get-header-key-duplicate`,
      `post-vanilla`, `post-x-www-form-urlencoded`, plus the multi-line and
      space-handling cases must match the expected canonical request,
      string-to-sign, and final `Authorization` header byte-for-byte.
- [ ] Documentation in README §2.5.1 with an example pointing at a hypothetical
      AWS-hosted endpoint, plus a defer/disclaimer for sigv4a (asymmetric
      regional variant) which is intentionally out of scope.

**Not in scope:** SigV4a (asymmetric), STS assume-role flow (use AWS SDK for
that and inject the resulting session credentials), pre-signed URLs.

---

### 1.2 Property-driven auth-provider configuration

**Origin:** V1 Priority 5 / 1.7 (deferred sub-item).

**Why:** Both built-in providers (the existing `RefreshingBearerAuthProvider`
+ `OAuth2ClientCredentialsTokenProvider`, plus future `AwsSigV4AuthProvider`)
currently require a hand-written `@Bean` definition. A property-driven
configuration block would shrink the boilerplate to a few YAML lines.

**Why it was deferred:** Tied to 1.1 — designing the YAML schema before both
providers exist risks a shape that doesn't fit SigV4's distinct parameter set.

**Design notes:**

```yaml
reactive:
  http:
    clients:
      user-service:
        auth-provider:
          type: oauth2-client-credentials       # or aws-sigv4
          token-uri: https://...
          client-id: ${OAUTH_CLIENT_ID}
          client-secret: ${OAUTH_CLIENT_SECRET}
          scope: read:users
      partner-aws:
        auth-provider:
          type: aws-sigv4
          access-key-id: ${AWS_ACCESS_KEY}
          secret-access-key: ${AWS_SECRET}
          region: us-east-1
          service: execute-api
```

- The existing `auth-provider: <beanName>` string form stays supported (the new
  block kicks in when the value is an object instead of a string).
- A dedicated `AuthProviderFactory` SPI in the starter dispatches on `type:`;
  built-in providers register themselves on the classpath; users can supply
  their own factories for custom types.

**Acceptance bar:**

- [ ] `AuthProviderFactory` SPI in `auth/`.
- [ ] Built-in factories for `oauth2-client-credentials` and `aws-sigv4`.
- [ ] Property binding test (config block → constructed provider).
- [ ] Tests verifying the string form still works (back-compat).
- [ ] Documentation in README §2.5.1.

---

### 1.3 Composite observer support

**Origin:** V2 observation after OpenTelemetry module work.

**Why:** Today, when both `reactive-http-client-otel` and the Micrometer
observer are on the classpath, the OTel observer wins (it registers under
`@ConditionalOnMissingBean(HttpClientObserver.class)` and suppresses the
Micrometer one). Users who want **both** metrics and traces have to hand-roll a
delegating observer.

**Design notes:**

- New `CompositeHttpClientObserver` in the starter, taking a
  `List<HttpClientObserver>`. Forward to each observer and isolate individual
  observer failures so telemetry cannot break the request pipeline.
- Revisit the `@ConditionalOnMissingBean(HttpClientObserver.class)` guards in
  both Micrometer and OTel auto-configurations. Prefer named overrides for
  built-ins and a clear user override story.
- Document whether custom observers now run alongside built-ins or replace
  them.

**Acceptance bar:**

- [ ] `CompositeHttpClientObserver` + auto-config wiring.
- [ ] Regression test: an app with Micrometer + `reactive-http-client-otel`
      records both a timer and a CLIENT span for one exchange.
- [ ] Regression test: user-supplied observers still fire with documented
      semantics.
- [ ] `CHANGELOG.md` clearly states whether observer override behavior changed.

---

### 1.4 JUnit 5 `@MockHttpServer` extension

**Origin:** V1 Priority 5 / 1.3 (deferred sub-item).

**Why:** The `reactive-http-client-test` module ships
`MockReactiveHttpClient` as a builder API today. For multi-test classes that
share matchers across cases, a JUnit 5 extension would let users declare
the mock once at the class level and inject it per test method.

**Why it was deferred:** The builder API on `MockReactiveHttpClient` already
covers the same ergonomics inside a single `@Test`. The extension is a thin
convenience wrapper whose value only emerges with real consumer feedback on
multi-test patterns. We didn't have that feedback yet.

**Design notes:**

- New `@MockHttpServer` annotation applied to a `MockReactiveHttpClient<T>`
  field on a JUnit 5 test class.
- Companion `MockHttpServerExtension` implements `ParameterResolver` +
  `BeforeEachCallback` to wire the field with a fresh proxy per test (resetting
  recorded exchanges between cases).
- Matchers declared at the class level via a `static List<MockMatcher>` field
  or via `@RegisterMatcher` annotated methods.

**Acceptance bar:**

- [ ] Annotation + extension class in `reactive-http-client-test`.
- [ ] At least one consuming test using the extension end-to-end.
- [ ] Documentation in the test-module section of README.

**Open question:** whether to ship as a separate `reactive-http-client-test-junit5`
artifact to avoid the JUnit 5 dependency leaking into non-JUnit setups, or to
keep JUnit 5 as an `optional` dep on the existing test module.

---

### 1.5 Resilience4j rate-limiter support

**Origin:** New V2 opportunity.

**Why:** Retry, circuit breaker, and bulkhead are supported. Rate limiting is
the remaining common Resilience4j operator users still wire manually for
partner APIs with strict quotas.

**Design notes:**

- Add `@RateLimiter` method annotation mirroring `@Retry`, `@CircuitBreaker`,
  and `@Bulkhead`.
- Add optional client-level `resilience.rate-limiter` configuration.
- Keep classpath-safe no-op behavior when Resilience4j rate-limiter classes are
  absent.

**Acceptance bar:**

- [x] Optional Resilience4j rate-limiter integration.
- [x] Client-level and method-level instance selection.
- [x] Tests cover missing registry, configured registry, and per-method override.
- [x] Docs explain operator ordering relative to retry/circuit-breaker/bulkhead.

---

## 2. Features to optimize

### 2.1 OTel `server.address` and `server.port` attributes

**Origin:** V1 Priority 6 / 1.1 (sub-item folded into "Semantic conventions"
note — never strictly deferred but tracked as future enhancement).

**Why:** `OpenTelemetryHttpClientObserver` sets `http.request.method`,
`http.response.status_code`, `url.template`, `error.type`. The OTel HTTP
client semantic conventions also recommend `server.address` (and optionally
`server.port`) — useful for grouping spans by upstream host without
parsing `url.full`.

**Why it was deferred:** The starter's `HttpClientObserverEvent` carries the
path template (`/users/{id}`), not the resolved URL. Threading the host
through the observer event is a small but cross-cutting change.

**Design notes:**

- Add `serverAddress` and `serverPort` to `HttpClientObserverEvent` (alongside
  existing `requestBytes` / `responseBytes`). Keep them nullable for non-HTTP
  paths or when the host cannot be determined.
- Populate from the `WebClient` `ClientRequest.url()` in
  `ReactiveClientInvocationHandler.notifyObserver(...)`.
- `MicrometerHttpClientObserver` can pick them up as new tags
  (`server.address` / `server.port`) — but cautious cardinality: only enable
  them under a property flag (`reactive.http.observability.include-server-address`),
  default off, since many internal services hit dozens of distinct hosts.
- `OpenTelemetryHttpClientObserver` sets them unconditionally (OTel handles
  cardinality on the backend).

**Acceptance bar:**

- [ ] Two new fields on `HttpClientObserverEvent` + deprecation cycle for the
      13-arg constructor (replaced by a 15-arg one; current one delegates).
- [ ] Handler captures host + port from `ClientRequest.url()`.
- [ ] OTel observer test asserts `server.address` and `server.port` on every
      span.
- [ ] Micrometer observer respects the cardinality opt-in flag.

---

### 2.2 Precompute immutable request plans per method

**Origin:** New V2 opportunity.

**Why:** `MethodMetadata` is cached, but invocation still repeats several
stable decisions: effective API resolution, HTTP method conversion, timeout
precedence, multipart checks, and response-shape checks. An immutable request
plan would make the hot path clearer and reduce repeated branching.

**Design notes:**

- Create a cached method-level plan from `MethodMetadata` plus resolved API-map
  config.
- Preserve startup validation and current error messages for `@ApiRef`.
- Keep dynamic method arguments separate from static method plans.

**Acceptance bar:**

- [ ] Cached immutable request/effective-API plan per method.
- [ ] Existing behavior tests pass unchanged.
- [ ] Focused tests cover `@ApiRef` timeout/path/method precedence.

### 2.3 Improve diagnostics for auto-configured filter order

**Origin:** New V2 opportunity after adding OTel propagation filters.

**Why:** A starter-built `WebClient` may contain Spring customizer filters,
correlation propagation, auth, exchange logging, OTel propagation, and user
custom filters. When behavior is surprising, users cannot see which filters or
customizers applied to a client.

**Design notes:**

- DEBUG log applied `WebClientCustomizer` classes per client in order.
- Document built-in filter ordering.
- Keep caller-supplied propagation/auth headers preserved unless a feature
  explicitly documents overwrite behavior.

**Acceptance bar:**

- [ ] DEBUG diagnostics list customizers per client.
- [ ] Tests cover OTel propagation header preservation and customizer ordering.
- [ ] Docs describe where custom filters sit relative to built-ins.

### 2.4 Documentation and examples package

**Origin:** New V2 ergonomics item.

**Why:** The README is comprehensive but long. Users would benefit from
copy-pasteable examples for high-value combinations: OAuth2, Resilience4j,
OTel propagation, multipart, streaming, and test helpers.

**Acceptance bar:**

- [x] Add minimal examples under `docs/examples/` or a compileable `examples/`
      module.
- [x] Include OTel trace context + baggage propagation.
- [x] Include test-helper usage without a live server.
- [x] Decide whether examples compile in CI or are clearly marked snippets.

---

## 3. Bugs / correctness to fix

### 3.1 TLS integration test with a real self-signed peer

**Origin:** V1 Priority 6 / 1.5 (deferred sub-item).

**Why:** `TlsContextApplier` is currently covered by unit-level tests that
generate an empty PKCS12 truststore and confirm the `SslContext` builds. An
end-to-end mTLS handshake against a real local server would catch issues with
hostname verification, cipher negotiation, and protocol-version selection
that the unit tests miss by construction.

**Why it was deferred:** Adds a heavy test dependency (Reactor Netty server
or Spring's `WebTestClient` with a self-signed cert). Lower marginal value
than the unit-level coverage already provides.

**Design notes:**

- Spin up an in-process Reactor Netty `HttpServer` with a self-signed cert
  generated at test-time (use `io.netty.handler.ssl.util.SelfSignedCertificate`
  — already on the classpath transitively via `reactor-netty-http`).
- Configure the starter's `TlsConfig` to point at the same cert's CA.
- Assert a `200 OK` GET succeeds through `WebClient`.
- Add a negative test: when the truststore does not contain the server's CA,
  the request fails with a TLS handshake error (specifically an
  `SSLHandshakeException`).

**Acceptance bar:**

- [ ] One integration test in `reactive-http-client-starter` covering the
      happy path.
- [ ] One negative test covering an untrusted-cert failure.
- [ ] Both tests complete in under 5 seconds (network-free, fully in-process).

---

### 3.2 Remove deprecated `log-body` property

**Origin:** V1 Priority 7 / 2.5 (deferred sub-item).

**Why:** `reactive.http.clients.*.log-body` is replaced by `log-exchange`
since 1.9.0, with `@DeprecatedConfigurationProperty` pointing at the new
name. The backwards-compatibility branch in
`ClientConfig.isExchangeLoggingEnabled()` exists only for users still on the
old key. A 2.0.0 release lets us delete it cleanly along with the deprecated
getter/setter.

**Why it was deferred:** Removing a property is a major-version change per
this project's SemVer policy. The 1.x line continues to support both keys.

**Acceptance bar:**

- [ ] Remove the `logBody` field, getter, setter on
      `ReactiveHttpClientProperties.ClientConfig`.
- [ ] Simplify `isExchangeLoggingEnabled()` to `return logExchange`.
- [ ] Remove the metadata entry from
      `additional-spring-configuration-metadata.json`.
- [ ] CHANGELOG entry under **Removed** with a migration note.
- [ ] Bump version to `2.0.0`.

### 3.3 Verify OTel propagation disable semantics

**Origin:** New V2 correctness item from v1.14.0.

**Why:** `reactive.http.observability.otel.enabled=false` disables the OTel
auto-configuration as a block. That likely disables both span recording and
context propagation together. Some users may want propagation without spans, or
spans without server-side extraction.

**Acceptance bar:**

- [ ] Decide whether separate toggles are needed, for example
      `otel.spans.enabled` and `otel.propagation.enabled`.
- [ ] Tests cover disabled observer, disabled propagation, and default behavior.
- [ ] Docs state exactly what the master switch controls.

### 3.4 Observer constructor compatibility audit

**Origin:** New V2 correctness item tied to observer event expansion.

**Why:** Adding fields such as host/port to `HttpClientObserverEvent` can break
external users who instantiate the event directly or implement custom observers
in tests. The event shape needs a compatibility plan before changing again.

**Acceptance bar:**

- [ ] Existing constructors delegate where practical.
- [ ] Older overloads are deprecated only when replacement overloads exist.
- [ ] Tests cover custom-observer compatibility.

---

## Suggested execution order

1. **3.3 + 2.3** — lock down OTel propagation semantics and filter diagnostics
   while the v1.14.0 propagation work is fresh.
2. **1.1 + 1.2** — AWS SigV4 plus property-driven auth providers; highest
   user-visible missing capability.
3. **2.1 + 1.3 + 3.4** — observer event expansion and composite observer work
   should be designed together.
4. **3.1** — TLS integration tests; pure hardening with low product risk.
5. **1.5** — rate-limiter support; useful but should follow observer/auth work
   unless a user need appears first.
6. **1.4 + 2.4** — test ergonomics and examples; valuable for adoption.
7. **3.2** — hold for the `2.0.0` breaking-change release.

See `tasks/v2/README.md` for the implementation task index.

## Items intentionally **not** on this roadmap

These were considered during V1 and decided against; documented here so they
don't get re-added without a fresh argument:

- **SigV4a (asymmetric regional sigv4).** Used only by a handful of AWS
  global services. Cryptographically heavier (ECDSA P-256). Recommend
  consumers use the official AWS SDK and inject the resulting signed
  request when needed.
- **Additional HTTP/2 abstractions.** Reactor Netty's HTTP/2 protocol selection is
  covered by the per-client `http2-enabled` option. More protocol abstractions
  should require a fresh argument.
- **Built-in response decompression toggle.** Already exists as
  `reactive.http.clients.*.compression-enabled` since v1.x.
- **gRPC integration.** Out of scope — different protocol, different abstraction
  (we'd be a thin layer over `grpc-java`'s own client, which already does
  everything reactive HTTP would do for it).
