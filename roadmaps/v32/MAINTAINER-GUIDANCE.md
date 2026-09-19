# V32 Maintainer and Operations Guidance

> **Reviewed:** 2026-09-18
> **Published / candidate:** `4.4.0` / `4.4.1`
> **Implementation scope:** V32-F004 + V32-F005 only
> **Release scope:** patch `4.4.1` selected; signing/publication and closure pending

Start here when changing or extending the reviewed starter. This is a navigation
and usage guide, not another policy authority or a release decision. Earlier
review records retain their dated as-is observations; use the implementation and
verification records below for subsequent corrections. Published `4.4.0` does
not contain the F004 failed-construction fix or F005 contributor-lane changes.
Neither requires an application API/configuration migration.
The [2026-09-19 decision](CLOSURE-EVIDENCE.md) selects the unpublished patch;
it does not change the version boundary or waive final publication checks.

## Review Navigation

| Question | Authoritative record |
|---|---|
| What is the baseline and what history is reused? | [Baseline and scope](BASELINE-SCOPE.md) |
| Which module owns a contract, state or resource? | [Architecture map](ARCHITECTURE-MAP.md) |
| Can an external application implement this extension? | [E01-E12 consumer scenarios](EXTENSION-SCENARIOS.md), including negative controls |
| Who selects a component or effective policy? | [Policy and component selection](EFFECTIVE-POLICY-SELECTION.md) |
| Where do probes, retries, auth replays and terminal signals run? | [Invocation and composition](INVOCATION-COMPOSITION.md) |
| Who cleans up success, failure, cancellation and partial construction? | [Resource ownership](RESOURCE-OWNERSHIP.md) |
| What do mock, optional, AOT and native lanes actually prove? | [Module and evidence boundaries](MODULE-EVIDENCE-BOUNDARIES.md) |
| What is accepted, deferred or intentionally unchanged? | [Finding register](FINDINGS.md) and [maintainer decision](ARCHITECTURE-DECISION.md) |
| What changed, and which verification ran? | [F004/F005 implementation](ACCEPTED-IMPROVEMENTS.md) and [compatibility verification](COMPATIBILITY-VERIFICATION.md) |
| What remains before review closure or publication? | [Execution checklist](CHECKLIST.md), Priority 12 |

## Choosing an Extension

The table applies to the reviewed `4.4.0` contracts and unchanged snapshot paths,
not arbitrary third-party implementations. E-numbers refer to the external
scenario record. Use the linked canonical guide for configuration, not internal
manager/planner APIs. Cardinality describes the tested append-only filter chain;
reordering or replacing that chain requires its own proof.

| Application need / scenario | Supported surface and phase | Cardinality, ownership and limit |
|---|---|---|
| Inbound field and explicit worker handoff, E01 | [RequestContext and RequestContextSnapshot](../../docs/09-correlation-id.md); read inside the subscriber context and restore at the receiving subscription | Capture/copy explicitly per handoff; no implicit detached-task or ThreadLocal propagation. Named readers match names case-insensitively; bulk map access does not. Plural preserves exposed map/list order; singleton rejects multiplicity. Application owns DTO parsing, queues and retained envelopes |
| Auth selection, E05 | [AuthProvider / AuthProviderFactory](../../docs/06-auth-providers.md); named provider wins, otherwise first supporting ordered eligible factory at client assembly | Provider selection is not per wire request. getAuth runs per ordinary attempt and cached caller probe; a prepared result is consumed by the first load attempt, later retries resolve again. Invalidation may resolve again within one attempt (E04). Application-supplied provider/SDK resources remain application-owned |
| Tenant gate and finalized URI/header variants, E02/E03/E08 | [Boot WebClientCustomizer and ReactiveHttpClientCustomizer](../../docs/15-customizer.md); Boot prepares the builder, factory appends correlation/auth, then per-client mutations, framing and observation | Customization runs at builder/client assembly; defaultRequest and filters run per request build/probe, including warm hits. A miss normally traverses them for probe and load, not once per logical call. Mandatory gates must precede lookup and selected variants must cover response differences. SAFE is a reviewed assertion, not automatic analysis |
| Replacement builder or exchange function, E09/E10 | Same builder SPI; application replacement must explicitly apply any desired ordered Boot customizers | Exchange function is load-only: the terminal non-dispatching probe does not invoke it. Do not place a required per-caller gate only there. Application owns replacement behavior; preserve framing/final observation and classify the whole mutation or leave caching unselected |
| Replacement connector, E11 | builder.clientConnector through the [customizer](../../docs/15-customizer.md) | Application owns transport settings, pool, executors and disposal. Factory destruction does not dispose that application pool. Starter pool/proxy/TLS/timeout/HTTP2 settings do not configure the replacement connector; no automatic transport-retry guarantee for it |
| Metadata and domain error decoding, E06 | MethodMetadataCache replacement during planning; [DefaultErrorDecoder / ErrorResponseMapper](../../docs/03-error-handling.md) at response decoding | Metadata/plans are cached, not a hot-reload API. Delegate built-in parsing or use the tested public API-ref alternative while F002 is deferred. Decoder must consume/release its response and bound error data; do not assume arbitrary decoder subclasses get independent per-client state |
| Selected JSON representation, E07 | [ReactiveHttpClientJsonCodec](../../docs/32-response-caching.md); writeBounded during selected-body preparation, before lookup/auth | Runs per cache caller, even hits; prepared bytes feed identity and writer. Implement a truly capped encoder, not full serialization followed by a length check. A write-only codec is deliberately insufficient. Native application types/custom serializers need their own hints |
| Terminal audit/telemetry, E02/E04 | [HttpClientObserver, lifecycle hooks and exchange logger](../../docs/21-diagnostic-contexts.md) | One terminal per caller subscription, not per retry or wire hop. Coalesced waiters have zero transport evidence; hidden refresh has separate cache work telemetry, not another caller terminal. Selected outcomes still reach custom surfaces without MeterRegistry. Sinks own sanitization and any retained records |
| Programmatic properties, F003 | [Configuration and AOT guidance](../../docs/20-native-release-compatibility.md); Spring selection at construction/build time | Designate the intended programmatic properties bean primary for the reviewed AOT workaround. Recreate factories for changed policy/configuration; runtime, AOT and non-instantiating diagnostics are different operations, not interchangeable selectors |

For required dependencies, start with the [cache guide](../../docs/32-response-caching.md):
production cache selection needs explicitly added Caffeine, a selected named
policy, TTL/capacity and valid variants. Enabled-only resilience selects no
operator; use the [explicit operator contract](../../docs/07-resilience4j.md).
Do not infer cache eligibility or replay safety from a customizer or semantic-read
acknowledgement. Auth, idempotency, body repeatability and redirect rules still apply.

The [counted composition traces](INVOCATION-COMPOSITION.md) distinguish outer
retry from one-time 401 replay and native redirects. Redirect hops do not re-enter
WebClient filters; hidden refresh uses prepared auth and the load/operator pipeline
with its own capacity and deadline. A changed successful final identity bypasses
publication; it does not rekey the response or undo delivery to interested callers.

## Deferred and Intentional Limits

The [decision register](ARCHITECTURE-DECISION.md) owns the complete rationale,
acceptance criteria and stop conditions. These are workarounds, not fixes:

- **V32-F001, factory/cache-validation owner:** inspect and explicitly classify
  the starter builder SAFE as well as all applicable application customizations
  when context-based validation cannot prove the exemption. Never blanket-trust
  a bean name. Reopen when this workaround blocks a consumer or builder lookup
  changes; other full-Boot builder combinations need evidence.
- **V32-F002, metadata/planning owner:** delegate built-in parsing or use public
  API-ref metadata/configuration. Fresh static metadata using only public setters
  still fails before a publisher in the reviewed case. Reopen when neither
  alternative serves a real parser; add invalid/generic/timeout cases. Do not
  reflectively construct internal EffectiveApi as an application workaround.
- **V32-F003, AOT selection owner:** mark the intended properties bean primary.
  Non-primary initialized selection drift remains; parent/prototype permutations
  need evidence. Reopen before changing this lookup or when primary is unsuitable.
  This JVM processor finding is not an observed native-image failure.

No module split, universal component resolver, second invocation pipeline or new
public SPI was justified. Preserve separate caller/load/refresh lifetimes,
non-instantiating diagnostics with unknown facts, optional integrations, and
explicit customization safety. Public-internal bridges (including
MockResponseCacheSupport) remain compatibility-covered linkage, not recommended
application extension APIs or permission to remove them. Keep module versions
aligned; mocks do not prove transport or arbitrary factory assembly behavior.
Discarded alternatives in the decision record are not supported APIs.

## Construction and Shutdown

**Unpublished F004 correction:** public handler create closes its newly allocated
manager if later handler construction throws, preserves the original exception,
and suppresses cleanup failure on it. Success transfers ownership unchanged;
borrowed WebClient/auth/registry and other same-tag owners remain usable. This is
not a general factory rollback transaction or proof of an earlier pod-memory cause.

Use normal Spring factory lifecycle where possible. A caller manually creating a
factory must destroy it on failure as well as normal teardown. Successful
low-level handler creation is not an AutoCloseable client API; prefer the
Spring-managed factory or the closeable mock helper for cache lifecycle ownership.
The unpublished guard only handles failure before a handler is returned.
In published `4.4.0`, avoid retry
loops around rejected cache-selected public handler creation; reproduce and fix
the invalid construction inputs rather than trying reflective cache cleanup.

Independent non-single-flight loads remain caller-owned and may outlive manager
close until their own terminal signal. Close rejects late publication but is not
universal cancellation of application work. Cancel/join owned subscriptions and
dispose application connectors/executors separately. Application callbacks may
block cleanup; do not interpret the factory transport deadline as a whole-process
shutdown guarantee.

## Operations Evidence

Use [operations troubleshooting](../../docs/30-operations-troubleshooting.md) and
the existing [sanitized support bundles](../../docs/26-support-bundles.md), not
internal registry-lease reflection or a new support schema. Before attributing
growth to F004, establish repeated rejected public handler construction and a
retained owner in a controlled reproduction. Ordinary successful cached traffic,
pool growth or high pod RSS alone does not establish that path.

Sample registered cache/work meters before and after a quiet interval **before
close**, tied to a bounded process/context ordinal and time window. Record
observability selection and backend availability separately. After the last
metric owner closes, absent series are unavailable, not zero or a terminal delta;
overlapping same-tag live owners can keep aggregated gauges registered. Capture
post-close lifecycle completion and heap/direct/RSS/pool samples only where their
application-owned collectors still exist. Neither cache decoded bytes nor active
work equals retained heap or RSS.

Retain only structural counts, bounded enum outcomes, version/source identifiers,
timestamps and sanitized configuration facts allowed by the existing bundle.
Exclude request targets, headers/bodies, cache keys/digests, credentials/identities
and arbitrary exception messages. A missing/unknown fact stays unknown. Retained
root analysis belongs in access-controlled local tooling, not a raw heap dump in
the reviewable support bundle. No new meter, performance, memory or mesh result
is established by this guidance.

## Contributor Verification

F005 changes the contributor test lane, not consumer behavior. The
[implementation record](ACCEPTED-IMPROVEMENTS.md#contributor-commands) gives ordinary
cleanup commands with explicit GC disabled and the separate
`-Pv32-cache-reachability` lane. Its 16 scenarios require SerialGC, explicit GC
enabled and at most 128 MiB heap; unsuitable JVM prerequisites fail rather than
skip. Keep deterministic witnesses in ordinary tests. The unchanged V31 handoff
reachability lane remains separate. Do not repair a normal-suite failure with
longer GC sleeps.

Use the [verification record](COMPATIBILITY-VERIFICATION.md) for exact clean-source
JVM/API/consumer/AOT/native commands and limitations, including the reproducible
Boot 4.1 overlay. Historical results are not measurements of a later source tree.
Priority 11 documentation/fixture reruns are recorded in the
[checklist](CHECKLIST.md); Priority 12 still owns release selection and closure.
