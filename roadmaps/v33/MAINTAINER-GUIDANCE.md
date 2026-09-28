# V33 Maintainer, Migration and Operations Guidance

> **Recorded:** 2026-09-28
> **Published / development:** `4.4.1` / `4.5.0-SNAPSHOT`
> **Delivered scope:** V32-F001 + V32-F002 + V32-F003
> **Release scope:** unselected

Companion to [Priority 9](CHECKLIST.md). This is current-source guidance, not
publication approval. [V32 findings](../v32/FINDINGS.md) and its
[decision](../v32/ARCHITECTURE-DECISION.md) retain the original deferrals;
[V33 approval](FIX-DECISION.md) selected all three for correction. No accepted
finding is deferred in V33. F004/F005 remain published `4.4.1` safeguards.

## Migration by Finding

| ID / owner | Published `4.4.1` workaround | Current-source correction and migration limit |
|---|---|---|
| V32-F001; factory/cache-validation maintainer | Inspect and classify the actual starter builder SAFE alongside all applicable application customizations | [Ownership](BUILDER-OWNERSHIP.md) recognizes proven starter definitions through context/factory and parent ownership. Only that redundant entry can be removed; existing entries remain valid. Child replacements, unknown provenance and every applicable application mutation still need classification. No name-only exemption |
| V32-F002; metadata/planning maintainer | Delegate built-in parsing or use public API-ref metadata/configuration; never reflect into EffectiveApi | [Planning](STATIC-METADATA.md) derives missing static routing from public fields. Supply method, API name, supported verb, non-null path, Mono/Flux flags, element type and applicable bindings. Incomplete fresh metadata fails earlier, normally at concrete-client planning before dispatch. Existing derived values and API-ref routing keep precedence; inconsistent metadata and live mutation remain unsupported |
| V32-F003; AOT-selection maintainer | Make the intended programmatic properties bean primary for the reviewed initialized non-primary selection gap | [AOT selection](AOT-PROPERTIES-SELECTION.md) uses Spring preference and binds eligible selected definitions. Primary remains valid but is not required for the other tested preference rules. Ambiguity/invalid-selected/creation failures propagate; a valid inactive bean cannot rescue them. Predictable FactoryBean product types and the binding-lifecycle limits below still matter |

The [checked public example](../../docs/examples/v33-extensions.md) gives the
dependency, named TTL/capacity policy, Idempotency-Key variant, selective SAFE
declaration and Spring-managed replacement beans together. Ordinary applications
do not need a custom parser or programmatic properties to use the starter.
Use the existing [customizer](../../docs/15-customizer.md) and
[cache guide](../../docs/32-response-caching.md) for their full safety contracts.

Strict source/binary comparisons against `4.4.1` pass in
[Priority 8](COMPATIBILITY-COST.md). There is no new public signature, property,
default, dependency or module. Earlier metadata validation, removal of a redundant
SAFE entry and corrected AOT selection are behavioral changes even with a clean
API report. No automatic config migration is needed; test application extensions
against current source before removing a workaround. No speed, memory or startup
gain is claimed; the retained noisy benchmark and confirmation are in that record.

## Creation and Ownership Boundaries

Runtime may create selected configuration and components for an actual client.
AOT may obtain properties and metadata for build-time validation, not construct
business clients, transports, signers or cache managers merely to select them.
The starter uses non-eager discovery; framework refresh, dependencies of selected
beans and Boot binding-advisor discovery have their own creation behavior. An
unresolved raw FactoryBean in a searched metadata scope fails explicitly rather
than silently ignoring a possible replacement. Expose generics/product-type
metadata; do not allocate business resources just to reveal a type.

New properties are bound at the temporary runtime-equivalent processor boundary,
after awareness/direct-prefix preparation and before ordinary initialization.
The already-installed standard binder is reused and temporarily repositioned.
Early definition-backed objects can be bound once, but initialization callbacks
that already ran cannot be replayed. Keep the auto-configured lifecycle observer;
low-level contexts without it have only the documented fallback, not arbitrary
creation-history parity. Tracking stops/clears on normal runtime initialization
and context destruction; it is not an application hook.

Scopes must exist at build time; the starter does not activate request/session
scope. Opaque scoped factories need discoverable target metadata or the documented
cached-factory path. Broad/ambiguous non-singleton processor identity can fail;
use a singleton or unique concrete predicted product type. See the
[canonical AOT details](../../docs/20-native-release-compatibility.md#post-441-development-lane)
for aliases, wrapping, replacement and restoration limits. Neither JVM processor
tests nor one native fixture proves every application extension/native combination.

Diagnostics remains non-instantiating. Missing provider facts stay unknown/null,
not zero or permission to create lazy beans. Foreign client factories retain
their own grammar and ownership. Mocks do not reproduce Spring selection;
public handlers without a concrete interface do not gain all factory/AOT startup
or inherited-type guarantees. The [cross-path matrix](CROSS-PATH-REGRESSIONS.md)
records these intentional differences.

Recreate factories/contexts for routing, metadata or policy changes instead of
mutating consumed configuration. Spring destroys its factory-owned resources;
application connectors, auth SDKs and executors remain application-owned. Manual
factory users must destroy on failure and normal teardown. Successful low-level
handler creation is not a public AutoCloseable lifecycle. F004 releases the newly
allocated manager only on rejected public handler construction. Independent loads
can remain caller-owned after close; cancel/join that work separately.

Probes still run defaults/filters and auth before cache lookup, including hits.
Misses/retries/auth replays can repeat filters; exchange functions are load-only.
Required per-caller gates must not live only in the exchange function. Redirect,
retry, key/wire identity, caller/load/refresh state and timeout semantics are not
changed by these fixes.

## Bounded Operational Triage

Use [existing support bundles](../../docs/26-support-bundles.md), not a new schema.
Capture source/version, runtime versus AOT versus diagnostics phase, counts,
sanitized bean-role ordinals, fixed selection/classification outcomes and exception
class only. Keep actual bean/config definitions and arbitrary error messages local
for review; do not export targets, headers/bodies, credentials, identities, cache
keys/digests or retained objects. A pre-dispatch startup error has no logical-call
terminal, downstream status or transport failure stage to invent.

| Observed structural failure | Local check / safe disposition |
|---|---|
| Missing or INCOMPATIBLE customization classification | Check owning definition and applicable Boot/client/builder inventory. On `4.4.1`, retain the inspected starter-builder workaround. On current source, prove ownership; do not waive an application mutation |
| Incomplete/invalid fresh metadata | Check method ownership, verb/path/flags/type/bindings locally and preserve deliberate validation failure. Delegate parsing/API-ref when appropriate; do not fill internal fields reflectively |
| Ambiguous properties selection | Inspect Spring candidates and intended preference; remove ambiguity explicitly, never pick the first valid configuration |
| Invalid selected properties or selected-bean creation failure | Correct the selected definition/binding prerequisites, not the inactive candidate or fallback environment |
| AOT unresolved FactoryBean/processor provenance | Supply predictable product/target metadata or use a supported singleton processor; retain the failure rather than eagerly creating unrelated business factories |
| Unknown diagnostics fact | Record unknown and which provider path was available; do not instantiate it to fill an incident bundle |

Do not diagnose a deployment-memory or service-mesh issue from these extension
corrections. Existing cache/work/health dashboards and support fixtures are
unchanged. Sample counters before close; after the last metric owner closes,
absent meters cannot supply post-close terminal deltas.

## Deferred Scope and Reopening Triggers

No V33 accepted ID was silently deferred. V32-F001/F002/F003 keep their historical
published workarounds above; current corrections supersede only those reproduced
gaps. If ownership is misclassified, valid fresh planning fails, or an application
selection diverges, the corresponding owner above needs a bounded public-entry
reproducer with negative controls and new Boot/AOT/native evidence as applicable.

| Unselected expansion / owner | Supported alternative | Trigger for a separate proposal |
|---|---|---|
| Universal component resolver or new public SPI; architecture maintainer | Existing public replacement beans and owner-local selection | Repeated proven cross-owner need that cannot be served by current APIs |
| Live metadata/policy reload; planning/cache maintainer | Recreate and close factories/contexts | Concrete consumer need plus an explicit invalidation/ownership contract |
| Arbitrary scoped/processor/native equivalence; AOT maintainer | Predictable product types, available scopes, early binding and documented supported processor identity | Minimal paired runtime/AOT counterexample outside verified cases |
| Module split or dependency upgrades; module/release maintainer | Aligned current modules and explicit optional dependencies | Independent compatibility/ownership evidence and scope approval |

## Verification and Release State

[Parity](PARITY-EVIDENCE.md) records mock, assembled Boot consumers, JVM AOT and
native evidence; [compatibility/cost](COMPATIBILITY-COST.md) records final-source
API/regressions, packaging and matched JMH rows. Historical failures and limits
remain in those records. Priority 9 changes docs/tests only, not production or
native inputs, and does not claim another native or benchmark run.

V33 stays active; `4.5.0-SNAPSHOT` does not select a minor release. Readiness still
has `activeRoadmap=v33`, `plannedFinalVersion=null`, deferred/unpublished candidate
and an unselected lane. Generated pending manual release commands are candidate
revalidation gates, not a claim that the linked development verification never
ran. Priority 10 owns scope, exact version, final evidence reconciliation,
signing/publication or an explicit no-release decision.

No V1-V32 record is rewritten to turn a historical deferral into a pass.

### Priority 9 Verification

Reviewed patch on reachable source `dca0dda985f3322efc53e4f6e7a57f037813f9c1`,
2026-09-28. Oracle JDK 21.0.8, Maven 3.9.9, Boot 4.0.0 and Java target 21;
explicit GC disabled. Existing dependency cache reused, no new Central-consumer,
native, API, full-reactor or performance run claimed. Those results remain in
Priorities 7-8, with final candidate reconciliation owned by Priority 10.

| Final focused suite | Cases | Scope |
|---|---:|---|
| DocumentationReleaseArtifactTest | 77 | Local links, archive/current status, sanitized fixtures, configuration reference and generated readiness |
| V33GuidanceExampleTest | 4 | Compile the exact public Java block; counted GET/target/Accept/result, repeated cold subscription serves a cache hit, redundant SAFE remains valid, missing classification/incomplete metadata fail runtime and JVM AOT before dispatch |
| InboundContextDocumentationContractTest | 36 | Existing checked context snippets and sanitized incident fixture |
| ReactiveHttpClientPropertiesTest | 35 | Existing configuration and metadata guards |
| PublicStaticMetadataContractTest | 25 | Fresh/delegated/API-ref, grammar, inherited types and planning boundaries |
| CacheBuilderOwnershipContractTest | 13 | Ownership proof, hierarchy, replacements and conservative unknowns |
| AotPropertiesSelectionContractTest | 243 | Runtime/AOT selection, binding lifecycle and creation controls |
| **Total** | **433** | Zero failures/errors/skips; focused evidence, not the complete reactor |

The initial two-class run had **81 cases, two failures, zero errors/skips**:
the proposed builder default Accept header did not override the request-level
default. The example now uses a fixed request filter and keeps the actual-header
assertion. No production change or weakened assertion was used. Its original
Java block, Maven log and XML remain in `initial/`. The corrected seven-class
run and final rerun each pass 433 cases; these are repeated, overlapping counts.

Reproduce from the reviewed source with Java 21 and the named dependencies:

```bash
export JAVA_HOME=/usr/lib/jvm/jdk-21.0.8-oracle-x64
export PATH="$JAVA_HOME/bin:$PATH"
export MAVEN_OPTS='-Xmx512m -XX:ActiveProcessorCount=2'
REPO="$PWD/target/v33-native-runs/native-g0ynw95x/repository"
mvn -B -ntp -s .mvn/maven-central-settings.xml -Dmaven.repo.local="$REPO" \
  -pl reactive-http-client-starter -DargLine=-XX:+DisableExplicitGC \
  -Dtest=V33GuidanceExampleTest,DocumentationReleaseArtifactTest,InboundContextDocumentationContractTest,PublicStaticMetadataContractTest,CacheBuilderOwnershipContractTest,AotPropertiesSelectionContractTest,ReactiveHttpClientPropertiesTest test
git diff --check
```

`REPO` is the local cache used here; another writable Maven repository is valid,
but preserve its actual toolchain/dependency provenance. This lane tests current
reactor classes, not published binary consumption. Evidence is retained under
`target/release-evidence/v33/priority9/`: exact commands, exit codes, XML-derived
counts, toolchain, generated readiness, reviewed sources/patch and `SHA256SUMS`.
Verify from that directory with `sha256sum --quiet -c SHA256SUMS`. An absent
ignored bundle requires reproduction, not a claim to have reviewed the old run.
`git diff --check` passes. No signing or publication is performed.
