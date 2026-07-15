# Reactive HTTP Client - Roadmap V21

> **Status:** draft
> **Target:** stabilize the first Spring Boot 4 release line, prove consumption
> of published `3.0.0` artifacts, and prepare the next patch or minor release
> without weakening API, native-image, or benchmark provenance.

## Current State

V20 published `3.0.0` and moved development to `3.1.0-SNAPSHOT` with published
`3.0.0` as the strict API and benchmark baseline. The default reactor now owns
the Spring Boot 4, Spring Framework 7, Jackson 3, Actuator, AOT/native, test
helper, and optional-integration contracts that were previously isolated in a
migration lane.

The immediate work is post-major stabilization:

- Public consumer snippets correctly remain on published `3.0.0`, while reactor
  and internal fixtures use `3.1.0-SNAPSHOT`.
- CI isolates the published API baseline, but generated release commands and
  other manual recipes must follow the same provenance contract consistently.
- The assembled consumer validates reactor artifacts; a separate fixture is
  still needed to prove the public Maven Central artifacts and generated POMs.
- Spring Framework 7 reports several AOT `MemberCategory` constants as
  deprecated and marked for removal.
- Native provenance still uses a V20-specific evidence directory even though
  the workflow now serves the ongoing `3.x` line.
- Release evidence is generated for a snapshot reactor, so candidate report
  names and public coordinates must not advertise unreleased artifacts.

V21 should harden these boundaries before adding another broad feature set.

## Release Decision

Do not assume that V21 must publish `3.1.0` merely because the reactor currently
uses `3.1.0-SNAPSHOT`. Choose the release version from the completed scope:

| Resulting scope | Release direction |
|---|---|
| Correctness, documentation, build, or compatibility fixes only | `3.0.x` patch |
| Backward-compatible public capability or configuration addition | `3.1.0` minor |
| Binary/source-incompatible public change | Defer to a future major |

Public README and quick-start coordinates must remain on the latest published
release until the next release resolves from Maven Central.

## Goals

1. Make every published-baseline check resolve immutable public artifacts from
   an isolated repository.
2. Prove that published `3.0.0` works in independent Boot 4 consumers without
   reactor classes or locally installed candidate artifacts.
3. Remove known Framework 7/AOT deprecation debt before those APIs disappear.
4. Stress cancellation, draining, streaming, retries, and connection reuse on
   real transport resources.
5. Keep runtime, diagnostics, snapshots, mocks, metadata, and documentation on
   one effective client-contract model.
6. Preserve a reproducible Boot 3.5 `2.x` maintenance lane.
7. Prepare the next release from clean, attributable compatibility, native, and
   benchmark evidence.

## Non-Goals

- Do not reopen the Boot 3/Boot 4 dual-generation jar decision.
- Do not add a broad HTTP abstraction or replace Spring `WebClient`.
- Do not remove a `3.0.0` public API in the first minor line.
- Do not enable diagnostics, exchange logging, redirects, retries, strict
  validation, or Actuator exposure by default.
- Do not publish numerical performance claims from smoke runs, dirty commits,
  or locally installed baselines.
- Do not turn release checks into hidden publication or benchmark automation.
- Do not declare the `2.x` line end-of-life without a separate support decision.

---

## 1. Snapshot and Release-Version Contract

Keep development coordinates, published consumer examples, changelog links,
native fixtures, generated manifests, and release candidates in distinct
states. Generated evidence should understand that `3.1.0-SNAPSHOT` is not a
publishable report or public dependency version.

**Acceptance:**

- Reactor modules and current-artifact fixtures use the same snapshot version.
- README and quick-start snippets use the latest published release.
- Generated release evidence identifies both development and published consumer
  versions without proposing `benchmark-report-*-SNAPSHOT.md` for promotion.
- The Central workflow rejects snapshots and validates tag/version alignment
  before deployment.
- Documentation tests cover snapshot development and final release-cut states.

## 2. Published `3.0.0` Consumer Baseline

Add an independent Boot 4 consumer lane that resolves parent, starter,
test-helper, and OTel `3.0.0` from a fresh Maven repository. Keep it separate
from the current-reactor consumer so packaging regressions and development
regressions remain distinguishable.

**Acceptance:**

- Public artifacts resolve with release-repository markers in an empty local repository.
- The consumer covers declarative calls, inherited generics, `@ApiRef`, JSON,
  Problem Detail, diagnostics/health, test helpers, and OTel propagation.
- Dependency evidence contains no reactor output directory or locally installed
  starter artifact.
- Current-reactor and published-release consumer failures are reported separately.

## 3. One Published-Baseline Provenance Contract

Unify API compatibility, benchmark baselines, release evidence, and manual
documentation around one isolated-repository convention. A baseline command
must fail rather than reuse an existing repository that could contain a local
release candidate.

**Acceptance:**

- Root and module-scoped japicmp commands use a fresh target-local repository.
- Published benchmark commands chain the freshness guard to Maven and record
  Maven Central settings plus remote markers.
- Generated release commands match CI and copyable documentation commands.
- Baseline artifact checksums and repository origin are retained as target-only evidence.
- Regression fixtures prove local candidate artifacts cannot satisfy baseline checks.

## 4. Framework 7 AOT and Runtime-Hint Modernization

Replace deprecated `MemberCategory` usage with supported Spring AOT hint APIs
while preserving inherited endpoint discovery, configuration binding,
reflection, proxy, and resource behavior.

**Acceptance:**

- Production AOT code compiles without the currently reported
  `MemberCategory` removal warnings.
- Runtime hints remain no broader than required by concrete client contracts.
- Inherited generic and `@ApiRef` clients pass AOT processing.
- The native fixture passes success, auth, Problem Detail, diagnostics, health,
  and metrics checks.
- Native provenance uses a release-independent evidence path and records the
  exact project version and source commit.

## 5. Transport Resource-Ownership Stress Suite

Extend deterministic transport tests beyond single calls. Exercise pooled
connections under cancellation, concurrent subscriptions, retries, redirects,
timeouts, unexpected bodies, and delayed streaming consumption.

**Acceptance:**

- POST-then-PUT and mixed bodied/bodiless calls reuse HTTP/1.1 connections
  without parser desynchronization.
- Cancellation and timeout paths release or drain owned response bodies.
- `ResponseEntity<Flux<DataBuffer>>` keeps streaming ownership until the caller
  consumes or cancels the body.
- Retry does not resubscribe non-repeatable bodies without an explicit supported contract.
- Tests assert connection reuse/disposal and bounded pool state, not only response values.

## 6. Effective Contract Parity

Create a shared fixture matrix for startup validation, request planning,
diagnostics export, contract snapshots, lifecycle/observer reporting, and mock
helpers. Each surface should describe the request that is actually sent, with
subscription-local state and resolved inherited generic types.

**Acceptance:**

- Direct, inherited, generic, and configured `@ApiRef` methods share expected
  method, path, timeout, resilience, redirect, auth, and response-type results.
- Invalid methods fail consistently before export or invocation.
- Runtime diagnostics never instantiate lazy auth providers or create missing
  resilience instances.
- Mock and production fixtures agree on client names, resolved URLs, final
  headers, idempotency keys, attempts, lifecycle order, and logger selection.
- Snapshot and endpoint output remains sanitized and deterministic.

## 7. Diagnostics and Support-Bundle Schema Stability

Treat the `rhttpclients` endpoint and support snapshots as operational contracts
without freezing internal implementation classes. Define additive schema rules,
unknown-value behavior, size/cardinality limits, and proxy/custom-provider behavior.

**Acceptance:**

- Provider-backed and collection-backed snapshots distinguish false, disabled,
  unavailable, and unknown values correctly.
- Spring proxies and custom diagnostics providers preserve documented overrides.
- Support output contains no credentials, sensitive headers, raw bodies, or
  machine-local paths.
- A versioned fixture detects accidental field removal or semantic reinterpretation.
- Health, diagnostics, lifecycle, observer, and exchange-log docs state exactly
  which response/request metadata each surface owns.

## 8. `2.x` Maintenance-Lane Reproducibility

Verify that the documented Boot 3.5 maintenance lane is operational rather than
only stated in V20. Keep this check isolated from `3.x` source and dependency
graphs.

**Acceptance:**

- A documented branch or tag can build the latest `2.x` release from a clean repository.
- A critical-fix rehearsal produces Boot 3.5 artifacts without Boot 4 classes or POM leakage.
- API compatibility uses the correct published `2.x` predecessor.
- Shared security or transport fixes have an explicit forward-port policy.
- No normal `3.x` build compiles Boot 3 adapters.

## 9. Dependency and Supported-Matrix Review

Review the minimum and current Spring Boot 4 lines and their managed Framework,
Reactor Netty, Netty, Jackson, Micrometer, OTel, Resilience4j, and test
dependencies. Upgrade deliberately through the matrix rather than changing the
BOM as incidental cleanup.

**Acceptance:**

- Minimum and forward-compatibility Boot rows resolve from clean repositories.
- Managed dependency versions are recorded in generated evidence.
- Full tests, assembled consumers, AOT, and optional-integration back-off pass
  on each supported row.
- Any baseline movement has an explicit compatibility and migration assessment.
- Java 21 remains the project minimum unless separately approved.

## 10. Benchmark the First Post-`3.0.0` Line

Use published `3.0.0` as the isolated release-to-release baseline. Keep raw
WebClient and Spring HTTP Interface comparisons limited to equivalent work, and
keep no-network diagnostics rows in their own classification.

**Acceptance:**

- Smoke validates harness discovery without creating public claims.
- Release-quality current and published-baseline runs use clean attributable inputs.
- Comparison covers default success, JSON, `ResponseEntity`, error mapping,
  diagnostics, lifecycle, observer, and allocation-sensitive request expansion.
- Regressions are investigated before optimization; no threshold is a hidden CI gate.
- A report is promoted only when release notes make a supported numerical claim.

## 11. Adoption Feedback and Documentation Consolidation

Use real `3.0.0` adoption findings to improve the Boot 4 migration and operations
guides. Reduce duplicated release-era instructions while preserving historical
evidence and working links.

**Acceptance:**

- Common Boot 4 dependency/classpath failures have concrete diagnosis steps.
- Current commands are separated from historical V18-V20 evidence.
- Public examples compile against published coordinates.
- Configuration examples remain metadata-validated and use clearly fake values.
- No new public API is added solely to simplify documentation.

## 12. Next-Release Go/No-Go

Choose patch or minor from the completed API/configuration delta, then assemble
one clean release-readiness record. Do not remove `-SNAPSHOT`, update public
snippets, or promote a benchmark report until the release candidate and evidence
are ready.

**Acceptance:**

- The selected version matches semantic-versioning scope.
- Full reactor, strict API compatibility, packaging, consumers, optional
  integrations, AOT/native, metadata, links, and provenance checks pass.
- Public baseline artifacts resolve from fresh repositories.
- Benchmark evidence is promoted or explicitly deferred based on release-note claims.
- The release commit is clean and immutable; tag, changelog date, artifacts,
  and public snippets identify the same released version.
- After publication, the next snapshot and compatibility baseline move only
  after all companion artifacts resolve publicly.

## Exit Criteria

V21 is complete when the project has evidence for a stable first post-`3.0.0`
release, has selected patch versus minor from the actual delivered contract,
and either publishes that release or records a concrete no-go. A green reactor
build alone is insufficient without published-artifact consumption, isolated
baseline provenance, and native/transport ownership evidence.
