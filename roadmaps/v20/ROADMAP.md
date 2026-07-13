# Reactive HTTP Client - Roadmap V20

> **Status:** draft
> **Target:** turn the validated Spring Boot 4 spike into a publishable `3.0.0`
> release while preserving the Spring Boot 3.5-based `2.x` maintenance lane.

## Decision

V19 proved that the starter can run on Spring Boot 4, Spring Framework 7,
Jackson 3, and the current native-image baseline, but correctly returned a
[`3.0.0` no-go](../../docs/29-v19-release-decision.md). The reactor still
publishes the Boot 3.5 generation, the Boot 4 build is a non-publishing profile,
and generation-specific Javadoc packaging is not release-ready.

V20 should resolve those release-engineering blockers. It should not add a new
feature batch or publish one artifact that dynamically supports both Boot
generations.

| Release lane | Framework baseline | Scope |
|---|---|---|
| `2.x` | Spring Boot 3.5 | Security and critical correctness maintenance |
| `3.x` | Spring Boot 4 | New major line and future feature development |

## Goals

1. Establish a real `3.x` reactor whose default build targets Spring Boot 4.
2. Produce publishable starter, test-helper, OTel, source, and Javadoc artifacts.
3. Finalize the Jackson 3 and Boot 4 public contracts at the major-version boundary.
4. Preserve a reproducible `2.14.1` maintenance baseline without dual-generation jars.
5. Release `3.0.0` only from staged artifacts that pass consumer, AOT, native,
   compatibility, and documentation evidence.

## Non-Goals

- Do not add unrelated HTTP client features.
- Do not make the `boot4-spike` profile the permanent production build.
- Do not publish Boot 3 and Boot 4 classifiers from one module.
- Do not claim cross-generation performance improvements from dependency movement.
- Do not remove `2.x` maintenance support without a separate lifecycle decision.

---

## 1. Close `2.14.1` and Protect the Maintenance Lane

Confirm the `v2.14.1` tag and published starter, test-helper, and OTel artifacts,
then retain a branch capable of producing critical Boot 3.5 fixes. Move the
`2.x` API compatibility baseline only after all companion artifacts resolve
from the public release repository.

**Acceptance:**

- `2.14.1` has a dated changelog section, immutable tag, and resolvable artifacts.
- The maintenance branch builds without Boot 4 source or dependency leakage.
- The `2.x` support scope remains documented as security and critical fixes.

## 2. Establish the Actual `3.x` Reactor

Create the `3.0.0` development line and make Spring Boot 4 the default dependency
and source layout. Convert or remove spike-only profile wiring, keep Java 21 as
the project baseline, and prevent accidental activation of Boot 3 adapters in
the `3.x` build.

**Acceptance:**

- A normal `mvn verify` builds the Boot 4 generation without `-Pboot4-spike`.
- Reactor coordinates and generated metadata consistently identify the `3.x` line.
- No module depends on `spring-boot-starter-classic` or a Boot 3 compatibility jar.

## 3. Make Generation-Specific Packaging Release-Ready

Promote Boot 4 adapters to owned `3.x` sources and remove obsolete source-set
selection. Fix source and Javadoc attachment so Maven never scans excluded Boot
3 implementation sources during a Boot 4 release build.

**Acceptance:**

- Main, test, source-jar, and Javadoc phases use the same generation of sources.
- `mvn verify` passes with Javadocs enabled.
- Published jars contain no duplicate auto-configuration or stale Boot 3 classes.

## 4. Finalize Jackson 3 and Codec Ownership

Make `ReactiveHttpClientJsonCodec` the stable serialization boundary for the
`3.x` line. Remove or clearly isolate deprecated Jackson 2 compatibility APIs,
verify that auth signing and wire encoding use identical bytes, and document
every intentional public API break from `2.14.1`.

**Acceptance:**

- Default JSON, Problem Detail, OAuth2, and SigV4 tests run with Jackson 3 only.
- No public `3.x` contract accidentally requires a Jackson 2 type.
- Codec customization and strict body-signing behavior are covered by consumer tests.

## 5. Produce Publishable Module POMs

Remove spike-only deploy suppression from the `3.x` release path and audit each
module's dependency graph, optional boundaries, metadata, signatures, licenses,
SCM data, and Central publication configuration.

**Acceptance:**

- Starter, test-helper, OTel, and BOM/parent artifacts can be staged together.
- Consumer fixtures resolve only staged artifacts, not reactor classes or local snapshots.
- Generated POMs expose focused Boot 4 dependencies and preserve optional integrations.

## 6. Revalidate Auto-Configuration, Actuator, AOT, and Native Contracts

Run the production Boot 4 source layout through auto-configuration ordering,
configuration binding, diagnostics/health discovery, runtime hints, AOT client
discovery, and a real native executable. The evidence must come from the default
`3.x` build rather than the old spike profile.

**Acceptance:**

- Diagnostics, health, metrics, and OTel back off correctly when optional APIs are absent.
- AOT-generated client factories cover inherited generic and `@ApiRef` endpoints.
- The native smoke fixture performs real success, auth, and Problem Detail calls.

## 7. Revalidate Consumers, Test Helpers, and Optional Integrations

Build independent Boot 4 consumers against staged artifacts. Cover the mock
helper, constructor-injected exchange loggers, resilience operators, OAuth2,
SigV4, OTel propagation, repeated headers, redirects, and streaming ownership.

**Acceptance:**

- Consumer fixtures do not import reactor source directories.
- Minimal-classpath tests prove every optional integration can back off independently.
- Mock behavior remains aligned with production client naming, codecs, hooks, and logging.

## 8. Freeze and Audit the `3.0.0` Public Surface

Compare the `3.x` candidate with published `2.14.1` in report-only mode. Classify
all expected Boot 4/Jackson 3 breaks and fail on unreviewed removals. Establish a
new normal japicmp baseline only after `3.0.0` is published.

**Acceptance:**

- The migration ledger names every intentional binary/source incompatibility.
- Documented extension points are included in compatibility filters.
- No compatibility job can compare the candidate artifact with itself.

## 9. Close Transport and Protocol Regression Evidence

Carry the V19 HTTP/1.1 connection-reuse fixture into the production `3.x` lane.
Keep framing headers transport-owned and add a wire-level reproducer before
attributing malformed `GET /bad-request HTTP/1.0` decoder warnings to the starter.

**Acceptance:**

- POST-then-PUT reuse passes against a real Reactor Netty server and pooled connection.
- Bodiless, streaming, redirect, timeout, and error-drain ownership remains covered.
- Any protocol fix is backed by captured bytes and a failing regression test.

## 10. Establish a Boot 4 Benchmark Baseline

Run the same-stack loopback and no-network audits from the final `3.x` artifact
layout. Use raw WebClient and Spring HTTP Interface baselines only where work is
equivalent, and keep diagnostics-only rows classified separately.

**Acceptance:**

- Environment metadata records exact `3.x`, Boot 4, Framework 7, Jackson 3, and transport versions.
- Public numerical claims require a clean-commit, source-controlled report.
- A no-claim release may defer report promotion without blocking correctness evidence.

## 11. Publish the `3.0.0` Migration and Operations Guide

Consolidate dependency, package, configuration, Jackson, Actuator, native,
test-helper, and release-lane changes into one adoption path. Include a minimal
Boot 4 consumer and explicit guidance for teams remaining on `2.14.1`.

**Acceptance:**

- Every changed public contract has a before/after example or explicit removal note.
- Documentation snippets compile against staged `3.0.0` artifacts.
- Release notes distinguish migration evidence from benchmark claims.

## 12. `3.0.0` Go/No-Go and Release Readiness

Generate one release-readiness snapshot from a clean candidate commit. Require
full reactor, staged consumer, Javadoc/source artifacts, API migration ledger,
AOT/native, links, metadata, and artifact-resolution evidence before changing
the V19 no-go decision.

**Acceptance:**

- Every mandatory command and artifact is recorded with reproducible provenance.
- There are no unresolved release blockers or unclassified compatibility breaks.
- A go decision publishes `3.0.0`; a no-go decision records blockers and leaves
  `2.x` maintenance unaffected.

## Exit Criteria

V20 is complete when the project either publishes a reproducible Spring Boot 4
`3.0.0` release from the default reactor or records a new evidence-backed no-go
with concrete unresolved blockers. Passing the old non-publishing spike alone
is not sufficient.
