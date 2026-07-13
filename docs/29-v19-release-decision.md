# V19 `3.0.0` Release Decision

> **Decision:** no-go for publishing `3.0.0` on 2026-07-13.

V19 proves that the starter can run on the selected Spring Boot 4 stacks, but
the current reactor is not a releasable `3.0.0` line. The default build remains
the Spring Boot 3.5 maintenance reactor at `2.14.1`; Boot 4 is selected only by
the non-publishing `boot4-spike` profile.

## Test Matrix

| Lane | Spring Boot | Purpose | Result |
|---|---:|---|---|
| `2.x` maintenance | `3.5.16` | Default full reactor | Pass |
| `3.x` minimum candidate | `4.0.0` | Full JVM and packaged consumer | Pass |
| `3.x` current candidate | `4.1.0` | Full JVM compatibility | Pass |
| Native minimum candidate | `4.0.0`, GraalVM `25.0.3` | AOT, native compile, executable smoke | Pass |

Java 21 remains the project source and runtime baseline. The native build uses
GraalVM 25 because that is the selected Boot 4 native-image lane.

## Passed Gates

- `mvn -q verify` passed the complete Boot 3.5 reactor.
- Full Boot 4 JVM tests passed on both `4.0.0` and `4.1.0`. These suites include
  optional-integration presence, absence, and no-op behavior.
- The independent `.github/boot4-consumer` fixture passed against locally
  installed Boot 4 profile artifacts.
- Spring AOT processing passed, and the `.github/native-smoke` image compiled
  and ran successfully with GraalVM `25.0.3`.
- The report-only `2.14.0` to Boot 4 candidate japicmp run passed and remains
  summarized in the
  [candidate API report](api-report-2.14.0-to-3.0.0-candidate.md).
- API compatibility fixtures accepted additive changes and rejected public
  constructor, nested method, and enum constant removals.
- Generated configuration documentation and local Markdown-link checks passed
  in the release-documentation suite.
- The resolved Boot 4 tree contains focused `spring-boot-webclient`,
  `spring-boot-health`, `spring-boot-jackson`, and Jackson 3 dependencies. It
  does not contain `spring-boot-starter-classic`; optional integrations remain
  optional in the effective module POM.
- Benchmark report promotion is explicitly deferred. The Unreleased notes make
  no numerical performance claim, and the V19 benchmark run is smoke evidence,
  not release-quality evidence.

## Release Blockers

1. **Artifact identity is not `3.0.0`.** The root, starter, test-helper, and OTel
   modules all declare `2.14.1`.
2. **Boot 4 is not the publishing lane.** The default reactor still selects
   Spring Boot `3.5.16`; `boot4-spike` sets `maven.deploy.skip=true` and
   `skipPublishing=true`.
3. **Boot 4 release packaging fails.** A full Boot 4 `verify` reaches attached
   Javadoc generation and fails because the Javadoc source scan still includes
   Boot 3-only health and WebClient customizer classes that compilation excludes.
4. **There is no reviewable `3.0.0` published POM candidate.** Dependency
   provenance is valid for the spike, but a `3.0.0` POM cannot be inspected or
   staged until the real major-version reactor exists and publishing is enabled.

These are release blockers, not failures of the runtime consumer, AOT, native,
or optional-integration contracts.

## Required Next Actions

1. Establish the real `3.x` release branch with root and module versions set to
   `3.0.0`, Boot 4 as the default baseline, and the spike publishing guards
   removed from that branch.
2. Move generation-specific Boot 3 sources out of the Boot 4 Javadoc source set
   or configure Javadoc with the same generation exclusions as compilation.
3. Run an unskipped `verify`, inspect staged starter/test-helper/OTel POMs and
   dependency trees, then rerun the consumer, AOT/native, API report, and
   compatibility fixtures against the actual `3.0.0` coordinates.
4. Promote a clean Boot 4 benchmark report only if the `3.0.0` release notes
   make a public numerical performance claim.

## `2.x` Maintenance Lane

Until those blockers are closed, use starter `2.x` with Spring Boot `3.5.x`.
The `2.x` branch remains available for security and critical correctness fixes,
with applicable fixes forward-ported to the future `3.x` line. Do not consume
the internal Boot 4 spike artifacts in applications and do not publish them as
`2.14.1` Boot 4 variants.
