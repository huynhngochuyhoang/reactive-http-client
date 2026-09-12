# V31 Inbound Wire and Filter Boundaries

> **Disposition:** direct protocol/capture contract verified; no runtime correction.
> **Development:** `4.4.0-SNAPSHOT`; published baseline: `4.3.0`.
> **Source:** `e33148dc92d97e97ac7c72a43299de3c2e6e159d` plus this change;
> working tree dirty. Evidence: `target/release-evidence/v31/priority4/`.

## Protocol Matrix

[InboundHeadersWireContractTest](../../reactive-http-client-starter/src/test/java/io/github/huynhngochuyhoang/httpstarter/filter/InboundHeadersWireContractTest.java)
binds Reactor Netty on IPv4 loopback with an ephemeral port and adapts an actual
WebFlux `HttpHandler` via `ReactorHttpHandlerAdapter`. Default/replacement cases
discover WebFilters through `WebHttpHandlerBuilder.applicationContext` using
the starter's auto-configuration. These are real sockets, not mocked exchanges.

| Client to WebFlux hop | Supplied fixture field | Captured key | Verified protocol evidence |
|---|---|---|---|
| Cleartext HTTP/1.1 | `x-fixture` | `x-fixture` | Both ends report `HTTP/1.1`; neither is an H2 stream channel |
| Cleartext HTTP/1.1 | `X-Fixture` | `X-Fixture` | Same HTTP/1.1 assertions with mixed-case input |
| HTTP/2 prior knowledge (H2C) | `x-fixture` | `x-fixture` | Both ends report `HTTP/2.0` on `Http2StreamChannel`; no TLS/ALPN |
| TLS HTTP/2 | `x-fixture` | `x-fixture` | Both ends report `HTTP/2.0` on stream channels and parent TLS ALPN `h2` |

Every case asserts one inbound dispatch. HTTP/2 fixtures supply lowercase names
only and verify received names are lowercase. There is no HTTP/1.1 fallback in
the HTTP/2 cases and no attempt to send illegal uppercase HTTP/2 field names.
TLS trusts only the fixture's generated localhost certificate; the client
resolves localhost to IPv4 to match the explicit server bind. No external host
or real credential is used.

For the no-forwarding case a second loopback hop uses a real declarative starter
client with its default HTTP/1.1 transport. The downstream verifies one dispatch,
absence of the captured fixture/private/credential fields, and presence of the
separately supplied synthetic correlation ID. This is not a normalizing proxy:
the application issues a new declarative request with no header forwarding.

## Capture and Application Boundaries

The suite has 24 executed cases: four protocol/name cases for selected capture,
four for unchanged default filtering, eight for mutation order (before/after),
four for replacement-filter behavior and four for no automatic forwarding.

- On each selected request, the Spring header lookup and the new named reader
  find the synthetic value while a differently cased exact map lookup misses.
  This reproduces the reported symptom without losing the context key.
- Repeated `one`, `one`, `two` values preserve order/multiplicity through the
  wire, bulk map and named all-values reader. Single-value access rejects them.
  Empty strings remain present. A required-but-missing field produces absence
  and an application-owned structural validation error/HTTP 400 response.
- Mixed-case allow/deny configuration selects the intended fields; denied
  selected fields are redacted, while excluded fields stay absent even when
  they also match the deny-list. Default capture-all and the five default
  sensitive names are checked separately without broadening production defaults.
- Ordered fixture chains exercise request mutations on either side of capture.
  Subsequent map/list mutation changes the visible request but not the captured
  map or restored snapshot; captured collections reject mutation.
- A replacement capture-filter type can leave the context key absent even when
  the request has the field. Default bean registration backs off and the
  replacement executes once. This is application-owned behavior, not context loss.

[InboundHeadersAutoConfigurationTest](../../reactive-http-client-starter/src/test/java/io/github/huynhngochuyhoang/httpstarter/config/InboundHeadersAutoConfigurationTest.java)
now has six cases. It retains reactive default/replacement/unrelated-filter and
non-web checks and adds a servlet web application context with WebClient present
but no reactive capture or servlet bridge. The Jakarta Servlet API is test-only;
this is a registration test, not a new MVC ingress implementation. A separate
assertion locks the absence of `Ordered`/`@Order` on the capture filter and its
bean method. Fixture ordering is explicit and does not certify or reorder an
application's security chain.

## Deployment Disposition

- **Direct protocol evidence: verified.** No tested selected well-formed field
  disappears between the WebFlux request, capture and named access.
- **Normalizing intermediary: not required.** The complete capture/lookup
  distinction is demonstrated directly over both protocols. An added simulated
  sidecar would not establish the reported application's actual protocol hops.
- **Manual mesh run: not required for this priority; deployment cause unverified.**
  No cluster is accessed and no Istio/Envoy causation or deployment certification
  is claimed. There is no required manual command to unblock Priority 4.

To investigate that specific deployment later, first record starter/Spring/Boot,
Istio/Envoy versions, application type, relevant filter order/replacements and
the protocol on each client/proxy/application hop. Use a bounded synthetic
field, not identity or credential data. Retain only structural observations:
context-key presence, selection/redaction decision, observed synthetic name
spelling, value count, exact/named lookup outcome and explicit handoff boundary.
Do not export production values, full header maps, request dumps or proxy config
dumps. Any production correction needs its own reproducer and compatibility review.

## Verification

Fixture versions: Spring Boot `4.0.0`, Spring Framework `7.0.1`, Reactor Core
`3.8.0`, Reactor Netty `1.3.0`, Netty `4.2.7.Final`, Jakarta Servlet API `6.1.0`
(test-only) and Bouncy Castle `1.78.1` (existing test certificate support).
The runtime is GraalVM JDK `25.0.3`, with Java `21` compilation target and Maven
`3.9.9`. This is JVM wire evidence, not native evidence or a TLS matrix audit.

All operations have ten-second bounds, reactive assertions are acknowledged
through futures, and no setup sleep determines an assertion. Each fixture owns
and closes its server/event-loop resources and deletes its temporary certificate;
the no-forwarding test also destroys its outbound client factory. Test assertions
retain only synthetic data for the lifetime of the fixture.

The checklist records actual final regression counts. Exact commands, fresh
Surefire reports, logs, toolchain/dependency versions, source copies/hashes and
generated readiness are retained under the evidence directory above. One
intermediate no-forwarding test run expected a correlation ID without sending
one; the fixture was corrected to supply that synthetic input. No runtime
generation of absent correlation IDs was added.
