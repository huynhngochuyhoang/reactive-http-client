# V32 Baseline and Review Scope

> **Recorded:** 2026-09-14
> **Published baseline:** `4.4.0`
> **Development coordinate:** `4.5.0-SNAPSHOT`
> **Implementation and release scope:** unselected

This completes the baseline review in [Priority 1](CHECKLIST.md), not the
architecture assessment. The [finding register](FINDINGS.md) has no confirmed
V32 finding or accepted production change. Priorities 2-7 supply evidence;
Priority 8 requires a maintainer decision before implementation. Review-only
closure remains valid.

## Source and Version Boundary

Reviewed clean commit: `e284ced3219aa69c11e7a92405e8d949644127b8`.
Published tag: `v4.4.0`, reachable ancestor
`9d7d38da9b501bf429d76035425e6a137b7c1333`.
The baseline audit and Maven validation ran before tracked edits. Subsequent
documentation verification uses that commit plus this explicitly recorded
documentation/test patch; it is not represented as a clean release build.

Root, starter, test-helper, OTel, benchmark, native-smoke and both current
consumer fixtures remain `4.5.0-SNAPSHOT`. README/quick-start examples,
`latest.published.version`, strict API comparisons, published consumers and
benchmark baselines remain `4.4.0`. The current published benchmark profile
has no source exclusions: shipped V31 named-reader and snapshot rows are
included. Older version-specific exclusion profiles remain historical tools.
No coordinate, dependency, production source or default changes are needed.

The [archive index](../README.md), roadmap, checklist and generated readiness
agree that V32 alone is active. Readiness is `snapshot-development`, with
`activeRoadmap=v32`, `releaseLane=unselected`, candidate scope unselected,
`published=false` and `plannedFinalVersion=null`. A deferred `4.5.0` candidate
label is not a release decision. V31's historical null active-roadmap result
describes its closure checkpoint, not the later V32 adoption.

Support remains Java 21, Spring Boot 4 / Framework 7 / Jackson 3 on this line.
The default Boot version is 4.0.0; the retained supported matrix covers 4.0.0
and 4.1.0. The separate 2.x / Boot 3.5 maintenance lane is not migrated,
retired or revalidated by this review. See the
[compatibility guide](../../docs/20-native-release-compatibility.md).

## Reused Evidence

Evidence root: `target/release-evidence/v32/priority1/`. Reuse is deliberate:
these are not fresh Central downloads, new consumer executions, or new API,
native, matrix or performance runs. The sealed
[V31 closure](../v31/RELEASE-DECISION.md#post-publication-closure) and candidate
inventories both pass `sha256sum --quiet -c SHA256SUMS` unchanged.
The new `reuse-audit.json` records exact hashes, counts and source applicability;
`reused/` preserves artifact/consumer records and the API/discovery stage records.

| Evidence | Verification and applicability |
|---|---|
| Published 4.4.0 artifacts | All 13 files: parent POM plus module POM/binary/source/Javadoc for starter, helper and OTel. Original isolated `release-artifacts-4.4.0` Central repository, four remote-marker records, SHA-256 hashes, POM and embedded JAR versions rechecked. Every packaged main Java source matches the release tag. |
| Published assembled consumption | Four baseline tests and eleven full-profile tests, zero failures/errors/skips. Original isolated `consumer-4.4.0` classpaths contain all three published JARs with matching hashes and no reactor classes/test-classes. Effective POMs, dependency trees, XML, commands, settings and successful stage provenance retained. Full profile includes V31 readers/handoff. |
| Strict source/binary API | Separate root and starter checks against independently resolved 4.4.0 baselines passed during V31 archive verification. Original commands, Central provenance and reports retained, not rerun. Main Java is unchanged from the tag; review/test edits do not add API delta. Passing API reports do not prove behavioral equivalence. |
| Benchmark discovery | 34 harness tests in each current/published-4.4.0 lane; original reports include V31 named-header and context-snapshot rows. This establishes comparable row availability, not timing or allocation measurements. |
| Native | Retained clean snapshot fixture input `f9b94fd207e6c5af1fc36ee047fd2c491a6c0e30`, six fixture tests and successful executable. Source archive, commands, actual GraalVM 25.0.3 environment and binary hash retained. This is not a new native build or a final-4.4.0/4.5.0 binary certification. |
| Supported matrix | Original V31 Priority 9 Boot 4.0.0/4.1.0 runs: 1,879 tests each (1,736 starter, 78 helper, 62 OTel, three consumer), strict API against 4.3.0. Oracle JDK 21.0.8; exact resolved dependency versions remain in each row. Snapshot/dirty-source provenance is preserved, not relabeled as current validation. |
| Targeted performance | Original 50 current / 26 baseline smoke rows, 26 matched, 24 helper-only, and five controlled-fork reachability cases. Retain the **no-public-performance-claim** decision and original review flags. No release-quality benchmark rerun or new speed/allocation claim. |

The original published-consumer provenance names squash-local fixture commit
`902308a88cbb4c134e931339583cbc0948b3df42`. V32 does not require that object
as an ancestor. Instead, its sealed `reviewed-source.tar` is compared with the
reachable release tag for the entire published-consumer fixture, Central
settings and artifact/consumer/provenance scripts. Production, native-fixture
and benchmark Java are unchanged between the tag and reviewed V32 commit.
Current fixture dependency coordinates differ intentionally from the release;
published runs explicitly selected `-Dreactive-http-client.version=4.4.0`.
Later native source/measurement changes require new applicable evidence.

| Durable integrity anchor | SHA-256 |
|---|---|
| V31 closure `SHA256SUMS` | `52882a35c94ce9fa6c008b75bb092a453ce93324070f8243df19d8e8f68d54e6` |
| V31 candidate `SHA256SUMS` | `0f4c6073090903df4c5e84fec1e27d9bd707cf7f1e0c25b108302128343cec8d` |
| Published 13-artifact inventory | `93320f57d0de442bd3e23859060571a39111524dc020e9e4ec7f2e9e70ef56b7` |
| Published-consumer reviewed source archive | `f87eb459beba3b77cf1bce0c538fa03624a99ef5cc8d9f6fb2f56ac81acff684` |
| Retained native fixture archive | `bc90e76f73bb1ce5936ca1829b25cead99c6afa112240710160df2d2026fd922` |
| Retained native executable | `c074e8f5bc3a340f9a8d982136fcc5b752a74c1bf9952c7156e26bd9be1fa5ed` |

Native/matrix/performance inputs and their original provenance remain under
`target/release-evidence/v31/priority10/reviewed/`; they are referenced, not
rewritten. The V32 audit records the two benchmark binary/report hashes as
well. Preserve these target-only bundles outside `target/` before a root clean.

## Historical Decision Inventory

This is a reconciliation of recorded decisions, not a new execution of every
old acceptance box. The archive index and sibling checklists (V2 predates them)
remain the completion authority. **Delivered** describes that release's result;
**superseded** describes a later explicit decision, not a failed earlier roadmap.
The final column carries constraints into review, not automatic implementation.

| Record | Recorded disposition and evolution | Relevant V32 constraint |
|---|---|---|
| [V1](../v1/ROADMAP.md) | Delivered foundational transport, auth, context, multipart, test and observability capabilities | Review existing owners and SPIs before inventing alternatives |
| [V2](../v2/ROADMAP.md) | Delivered 2.0.0; no separate checklist | Preserve auth, TLS, rate-limit and cleanup contracts; no new historical checklist |
| [V3](../v3/ROADMAP.md) | Delivered 2.1/2.2 maturity and diagnostics work | Diagnostic safety and migration matter alongside extensibility |
| [V4](../v4/ROADMAP.md) | Delivered 2.3 extension-point and observability guardrails | Supported customization is not a general integration framework |
| [V5](../v5/ROADMAP.md) | Delivered 2.4 native, response, URI/body and compatibility hardening | Startup/native and runtime must agree on supported contracts |
| [V6](../v6/ROADMAP.md) | Delivered 2.5 explicit async context propagation | Applications own handoff; no implicit propagation |
| [V7](../v7/ROADMAP.md) | Delivered 2.6 retry/idempotency/body and outbound diagnostics | Replays require distinct safety and ownership evidence |
| [V8](../v8/ROADMAP.md) | Delivered 2.7 subscription-isolated reporting and extension testability | Caller state must not become shared attempt state |
| [V9](../v9/ROADMAP.md) | Delivered 2.8 grammar/startup and resource-safe response handling | Unsupported shapes fail at the documented boundary |
| [V10](../v10/ROADMAP.md) | Delivered shared client contracts in 2.9 | Inheritance and per-client effective policy remain explicit |
| [V11](../v11/ROADMAP.md) | Delivered contract export/drift guards in 2.9 | Inspection must reflect runtime while preserving unknown facts |
| [V12](../v12/ROADMAP.md) | Delivered reproducible benchmark infrastructure in 2.10 cycle | Measurements need comparable inputs and actual provenance |
| [V13](../v13/ROADMAP.md) | Delivered promoted reports and measured changes in 2.10 cycle | Historical performance results are not current guarantees |
| [V14](../v14/ROADMAP.md) | Completed post-2.10 release discipline | Release scope comes from delivered work, not a snapshot label |
| [V15](../v15/ROADMAP.md) | Delivered 2.11 diagnostics/auth/generic/stream hardening | Review composition with existing strict and body constraints |
| [V16](../v16/ROADMAP.md) | Delivered 2.12 adoption and optional integrations | Optional absence and documented manual helpers remain supported |
| [V17](../v17/ROADMAP.md) | Delivered 2.13 release/support/native discipline | Separate exact-source evidence from fixture capability |
| [V18](../v18/ROADMAP.md) | Delivered 2.14 baseline/automation hardening | Preserve sanitized support and independent published baselines |
| [V19](../v19/ROADMAP.md) | Completed 3.0 no-go; publication decision superseded by V20 | A passing migration spike alone is not a releasable artifact |
| [V20](../v20/ROADMAP.md) | Delivered Boot 4 / Jackson 3 generation as 3.0; replaces spike-only path | No dual-generation JAR; separate 2.x maintenance lane |
| [V21](../v21/ROADMAP.md) | Delivered 3.1 published consumption and diagnostics schema v1 | Preserve schema compatibility, release-state and provenance rules |
| [V22](../v22/ROADMAP.md) | Delivered 3.2 wire reliability/failure diagnostics | Wire evidence and structural failure attribution, not guessed stages |
| [V23](../v23/ROADMAP.md) | Delivered 3.3 attempt correctness/bounded operations | Streaming cleanup, deadline and final-attempt isolation |
| [V24](../v24/ROADMAP.md) | Delivered 3.4 composition/return grammar/proxy/mTLS/H2 contracts | Evidence must observe retirement, cancellation and replay at the wire |
| [V25](../v25/ROADMAP.md) | Delivered 3.5 request grammar/URI/framing/multipart/stale recovery | Ordered body identity, no implicit transport retry, bounded teardown |
| [V26](../v26/ROADMAP.md) | Delivered 3.6 logical duration and observability; resilience redesign deferred to V27 | Logical time, attempts, size and health samples remain distinct |
| [V27](../v27/ROADMAP.md) | Delivered 4.0 explicit operator activation and four opt-in cache phases | Supersedes implicit activation; GET-only cache limit later expanded by V28 |
| [V28](../v28/ROADMAP.md) | Delivered 4.1 acknowledged semantic-read caching across verbs | Explicit eligibility, body/wire identity and final auth/target partition |
| [V29](../v29/ROADMAP.md) | Delivered 4.2 decoded-response-byte bounds and memory evidence | Measured bytes are not retained heap/RSS; earlier active-work limits extended by V30 |
| [V30](../v30/ROADMAP.md) | Delivered 4.3 bounded caller/load/refresh admission | Capacity includes preparation and owned cleanup; no early reuse |
| [V31](../v31/ROADMAP.md) | Delivered 4.4 additive case-insensitive named readers and handoff evidence | Bulk spelling/order, multiplicity, redaction and explicit ownership stay compatible |

The [resilience proposal](../proposals/OPT_IN_RESILIENCE_ACTIVATION.md) was
adopted and delivered by V27; its original implicit-default description is
historical, not a current defect. The
[architecture proposal](../proposals/POST_4_4_ARCHITECTURE_REVIEW.md) is adopted
by V32 for review only. These are the two proposal files present at the reviewed
revision; there is no additional unadopted proposal to silently pull into scope.
Broader feature/redesign proposals require separate adoption. Historical
unchecked branches, superseded version commands and no-go records are not
reopened or rewritten.

## Review Coverage

These are selected review areas, not claims that their internals have already
been audited. Priority 2 builds the detailed source/contract/owner map.

| Module or boundary | Review work and later evidence |
|---|---|
| Starter assembly | Factory/auto-configuration, supported replacement beans, partial construction, transport and shutdown; Priorities 2, 4, 6 |
| Declarative/effective decisions | Metadata, grammar, property/annotation precedence, frozen vs dynamic policy, runtime/mock/AOT/inspection agreement; Priorities 2-4 |
| Invocation | Mono/Flux, auth, error decoding, retry/redirect, pre-lookup customizations, shared load and independent caller deadlines; Priorities 3-5 |
| Cache identity/storage/work | Final URI/header/serialized-body identity, generation, bounded entries/bytes/work, context/resource ownership; Priorities 5-6 |
| Test-helper artifact | External-consumer API, same-package internal bridges, optional dependencies, deterministic controls and lifecycle parity; Priorities 3, 7 |
| OTel companion and diagnostics | Optional linkage, observers without a registry, meter ownership, sanitized unknown-state output; Priorities 4, 6-7 |
| Evidence infrastructure | Benchmarks, assembled/minimal consumers, API/provenance scripts, AOT/native, support fixtures and release tools; Priorities 7, 10 |

No module split, new SPI, dependency replacement, connector/backend expansion,
automatic forwarding/trust/context propagation, MVC bridge, broader cache
eligibility, resilience redesign or performance optimization is selected.
Application-owned executors, connectors, publishers and retained responses
remain application-owned unless an accepted contract explicitly transfers them.
No production diff is authorized by this inventory.

## Reported and Exploratory Cases

| Origin | Current classification | Evidence needed before a V32 finding |
|---|---|---|
| Reported post-4.0 pod-memory increase | Unverified deployment leak hypothesis. [V29 characterization](../v29/MEMORY-CHARACTERIZATION.md) reproduced bounded retention, not a general leak; [ownership audit](../v29/CACHE-RETENTION-OWNERSHIP.md) distinguishes external owners. V30 subsequently added active-work bounds. | Sanitized version/config/traffic/time-window capture, separate heap/direct/native/RSS and live-work evidence, retained-root reproduction. No new application capture was supplied for V32. |
| Reported WebFlux headers visible in exchange but nullable map lookup | [V31 characterization](../v31/INBOUND-HEADER-CHARACTERIZATION.md) reproduced exact-case map mismatch; 4.4 supplies named readers. Mesh-specific rewriting or context loss remains unverified. | Reproducer using the named API with capture policy/filter order and explicit handoff. Do not diagnose Istio solely from casing. |
| Maintainer architecture-review request | Real request to assess missing contracts and extension cost; no specific new blocker confirmed | Source-linked external-consumer cases and alternatives in Priorities 2-7; explicit Priority 8 decision |
| Exploratory tenant auth and request customization | Proposed scenarios, not production incidents | Test per-caller hit/miss gates and all Boot/client/builder mutations using supported APIs |
| Exploratory metadata/codec/auth/error-decoder replacement | Proposed scenarios, not confirmed selection or extensibility defects | Minimal external consumer, normal/lazy/primary/hierarchy selection and applicable AOT parity |
| Exploratory observer, async handoff and connector replacement | Proposed scenarios, not requirements for new integration APIs | No-registry observer case, explicit worker snapshot, and deliberate connector lifecycle evidence |

## Verification

Fresh runs use Maven 3.9.9, Oracle JDK 21.0.8, Java target 21 and default Boot
4.0.0, with `.mvn/maven-central-settings.xml`. Commands, timestamps, exit
statuses, source diffs, XML and regenerated readiness are recorded per stage.
The final results are recorded in the [Priority 1 checklist](CHECKLIST.md).
The new baseline-record test first failed on the absent document; its red
report remains separate from passing runs. No signing, tagging, deployment,
native compile, full matrix or performance measurement was performed for V32.

Fresh repository checks:

```bash
mvn -B -ntp -s .mvn/maven-central-settings.xml validate
bash scripts/verify-published-baseline-fixtures.sh
bash scripts/verify-api-compatibility-fixtures.sh
mvn -B -ntp -s .mvn/maven-central-settings.xml -pl reactive-http-client-starter -Dtest=DocumentationReleaseArtifactTest test
bash -n scripts/verify-supported-matrix.sh scripts/verify-published-consumer.sh scripts/verify-published-release-artifacts.sh scripts/verify-published-baseline-provenance.sh
git diff --check
```

The two historical inventory checks run from their respective evidence roots:

```bash
(cd target/release-evidence/v31/priority10-4 && sha256sum --quiet -c SHA256SUMS)
(cd target/release-evidence/v31/priority10 && sha256sum --quiet -c SHA256SUMS)
```

The target-only `v32-baseline-audit.py` retains the artifact/source/consumer
checks described above. An initial matrix recount omitted `consumer-TEST-*.xml`
and stopped at 1,876; its failed stage/script is preserved. Including those
three consumer cases verifies the recorded 1,879 per row without changing any
historical evidence. The final evidence inventory covers this audit, the
reviewed source archive, current record/test copies and successful stage outputs.
