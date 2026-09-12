# V31 Inbound Header and Context Characterization

> **Disposition:** additive usability gap confirmed; no production correction selected.
> **Published baseline:** `4.3.0`; characterization reactor: `4.4.0-SNAPSHOT`.
> **Source:** base commit `2d51de60368e6715921ce8752b8e32132b884b18` plus the
> characterization tests and records in this change; working tree dirty.
> **Evidence:** `target/release-evidence/v31/priority2/`.

## Findings and Classification

The reported nullable `Map.get(...).getFirst()` failure is reproducible with
synthetic headers in a local WebFlux exchange. The server header lookup succeeds,
the `inboundHeaders` context key exists, and the captured entry contains the
value. A literal using different casing still returns null from the captured
map. Restoring a snapshot preserves that behavior.

The tests characterize the current public API. Future named helpers must keep
the bulk-map compatibility boundary described below. Assertions about malformed
raw inputs record existing failure locations, not a recommendation to rely on
those incidental exceptions as an application validation API.

| Observation | Classification | Verified boundary |
|---|---|---|
| Lower, mixed and upper spelling pass configured selection but another spelling misses in the map | Additive usability gap; dereferencing the absent map value is caller misuse | Server `HttpHeaders` lookup, capture, bulk read and snapshot restore |
| No context key returns an empty map; capture of a request with no headers writes a present empty map | Documented caller boundary | Key presence distinguishes these cases; the bulk map alone does not |
| Outside-allow-list field remains in the exchange but is absent from the snapshot | Documented selection behavior; expecting it in context is caller misuse | Allow-list exclusion precedes deny-list redaction |
| Selected denied field remains present with the redaction marker after handoff | Documented redaction behavior | Redaction is not absence or an original credential |
| Empty list, empty string, repeated equal values and malformed JSON remain distinct | Caller misuse when treated interchangeably | Empty-list `getFirst()` throws `NoSuchElementException`; empty string is present; first-value access silently selects among repeats; malformed JSON fails only at the application parser |
| Ordered application maps retain separately cased aliases and multiplicity | Additive usability gap | Defensive writes and snapshots preserve exposed iteration order and exact keys |
| Reads after composed `publishOn` or `subscribeOn` execute on another thread with the captured headers | Documented subscription behavior | Thread change alone does not explain missing headers |
| Independent sink subscriber sees an absent key or its own worker headers despite emission inside capture | Documented caller handoff boundary | Explicit snapshot restore supplies the emitter headers and replaces the worker header map |
| Request mutation after capture changes the live exchange but not the captured map | Documented capture boundary | Controlled WebFilter chains exercise both mutation orders |
| Replacement capture filter can leave the context key absent | Application-owned integration boundary | Auto-configuration backs off by `InboundHeadersWebFilter` type |
| Raw values of the wrong shape fail late or bypass defensive copying | Caller misuse; localized validation gap for the proposed named surface | See failure matrix below |
| Istio/Envoy changed this application's header spelling or removed its context | Unverified deployment hypothesis | No mesh configuration, protocol trace or deployment reproduction was supplied |

No tested well-formed capture/handoff path loses a selected entry. This result
does not establish correctness for every deployment or for feature compositions
reserved for later priorities.

## Raw Context Failure Matrix

These cases use the public string key directly, bypassing the defensive writer.
None is equivalent to a well-formed absent field.

| Supplied value | Bulk read / application use | Snapshot capture |
|---|---|---|
| String, list or integer instead of map | `ClassCastException` in `inboundHeaders` | `ClassCastException` |
| Map value is a string instead of a list | Map is returned; accessing the value as a list fails | `ClassCastException` |
| List element is an integer instead of a string | Map/list are returned; using the element as a string fails | Copies the element; subsequent string use still fails |
| Map key is an integer | Named string lookup misses; map is not empty | `ClassCastException` during key copying |
| Map value is null | Entry exists; dereferencing its list throws `NullPointerException` | Normalized to an empty list, as with `withInboundHeaders` |
| Mutable, otherwise well-formed map | Same object is returned; later mutations remain visible | Existing defensive snapshot behavior applies |

Manual `withInboundHeaders` writes also bypass ingress selection/redaction.
They make defensive copies but do not establish trust. Tests demonstrate this
with a synthetic denied field; no real credential or application header is
required to reproduce it.

## Registration and Ordering

[Auto-configuration](../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/config/ReactiveHttpClientAutoConfiguration.java)
registers capture only in a reactive web application, conditional on a missing
`InboundHeadersWebFilter` bean. A non-web context can provide `WebClient.Builder`
without registering capture. An unrelated `WebFilter` does not suppress it;
a replacement of the capture-filter type controls its own behavior.

The production capture filter and its bean method have no explicit `@Order` or
`Ordered` contract. Its snapshot reflects the exchange passed to it when its
`filter` method executes. The ordered test chain shows an upstream mutation is
captured and a downstream mutation is not. It deliberately chooses both orders;
it does not certify an application's security-filter order. Servlet ingress
and real protocol behavior remain part of Priority 4 verification.

## Compatibility Inventory

| Existing contract | Existing evidence |
|---|---|
| Preserve captured spelling; match configured names case-insensitively; copy maps and lists; apply selection/redaction | [InboundHeadersWebFilterTest](../../reactive-http-client-starter/src/test/java/io/github/huynhngochuyhoang/httpstarter/filter/InboundHeadersWebFilterTest.java), [context guide](../../docs/09-correlation-id.md#inbound-headers-snapshot), [exchange logging guide](../../docs/13-exchange-logging.md) |
| Retain public `correlationId` and `inboundHeaders` keys and filter aliases | [RequestContextSnapshotTest](../../reactive-http-client-starter/src/test/java/io/github/huynhngochuyhoang/httpstarter/core/RequestContextSnapshotTest.java), [RequestContext](../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/core/RequestContext.java) |
| Existing snapshot constructor/components, immutable values, explicit sink restore and scheduler composition | [RequestContextSnapshot](../../reactive-http-client-starter/src/main/java/io/github/huynhngochuyhoang/httpstarter/core/RequestContextSnapshot.java), its tests, [async guidance](../../docs/09-correlation-id.md#async-boundaries-and-sinks) |
| Present snapshot fields replace matching target fields; absent fields do not clear targets | Context guide restore contract, snapshot tests, and new ordered-alias/empty-restore characterization |
| Contributors restore in order and support old string keys | [RequestContextContributorTest](../../reactive-http-client-starter/src/test/java/io/github/huynhngochuyhoang/httpstarter/core/RequestContextContributorTest.java) |
| Mock inbound assertions use exact captured map names | [RecordedExchangeAssertions](../../reactive-http-client-test/src/main/java/io/github/huynhngochuyhoang/httpstarter/test/RecordedExchangeAssertions.java), [test-helper guide](../../docs/14-test-helpers.md) |

## Implementation and Protocol Decision

Priority 3 may add named, case-insensitive access with explicit absence,
multiplicity and structural-input validation. Preserve the bulk accessor,
snapshot constructors/components, public string keys, redaction, exposed value
order and restore precedence. Normalizing the existing map would change a
documented behavior and is not authorized by this characterization.

Any correction beyond that additive surface requires its own focused reproducer
and source/binary compatibility review before scope expands. The raw-input cases
here support validation localized to the new surface; they do not justify
changing existing bulk-read exception behavior or installing global context hooks.

Direct loopback HTTP/1.1 and HTTP/2 tests in Priority 4 are sufficient for the
planned protocol/capture contract. A normalizing intermediary is currently
unnecessary because the casing failure already occurs with a supplied server
header collection. Real protocol negotiation has not been exercised by these
mock exchanges. Manual mesh reproduction remains unverified and is needed only
for a claim about that deployment, not to establish the additive lookup gap.

## Verification

The new suites are
[InboundHeaderContextCharacterizationTest](../../reactive-http-client-starter/src/test/java/io/github/huynhngochuyhoang/httpstarter/filter/InboundHeaderContextCharacterizationTest.java),
[InboundHeaderHandoffCharacterizationTest](../../reactive-http-client-starter/src/test/java/io/github/huynhngochuyhoang/httpstarter/core/InboundHeaderHandoffCharacterizationTest.java)
and [InboundHeadersAutoConfigurationTest](../../reactive-http-client-starter/src/test/java/io/github/huynhngochuyhoang/httpstarter/config/InboundHeadersAutoConfigurationTest.java).
They contain 28 executable cases (18 + 6 + 4). All use bounded synthetic inputs;
reactive verification has a five-second limit, scheduler ownership is closed,
and sink emission occurs after consumer subscription without timing sleeps.

Exact commands, results, toolchain, source copies/hashes and reports are retained
under the evidence directory above. The companion checklist records the final
regression totals. These are JVM characterization results; native, protocol and
release-quality performance gates remain assigned to later priorities.
