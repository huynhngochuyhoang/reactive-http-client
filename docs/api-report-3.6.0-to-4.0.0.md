# `3.6.0` to `4.0.0` Public API Report

This report classifies the compatibility-covered public surface for the V27
major candidate. The baseline is the published `3.6.0` release resolved from
Maven Central into a fresh target-local repository. The candidate is
`4.0.0-SNAPSHOT` during development and `4.0.0` during the release cut.

## Result

The report-only root comparison found no binary- or source-incompatible rows in
the starter, test-helper, or OpenTelemetry artifacts. V27's resilience change is
a behavioral/configuration migration, not a Java API removal. The cache surface
is additive: cache annotations, nested configuration models, cache outcomes on
terminal contexts/events, and deterministic mock controls are new supported
APIs.

The exact reviewed incompatible delta is source-controlled in
`config/api-delta-3.6.0-to-4.0.0.txt`. It is intentionally empty. Compatible
additions remain visible in each generated `target/japicmp/compare-public-api.*`
report. `scripts/verify-v27-major-api-delta.sh` rejects any later incompatible
removal, modification, or addition that is absent from the reviewed file.

## Reproduce

Run the root report-only comparison and guard from a clean workspace:

```bash
test ! -e target/published-baseline-repositories/api-major-report-3.6.0 && \
mvn -s .mvn/maven-central-settings.xml \
  -Dmaven.repo.local=target/published-baseline-repositories/api-major-report-3.6.0 \
  -Papi-compatibility,major-api-report -DskipTests verify && \
API_COMPATIBILITY_BASELINE_REPOSITORY=target/published-baseline-repositories/api-major-report-3.6.0 \
  bash scripts/verify-v27-major-api-delta.sh && \
scripts/verify-published-baseline-provenance.sh api-major-report 3.6.0 \
  target/release-evidence/published-baselines/api-major-report-3.6.0 \
  reactive-http-client reactive-http-client-starter reactive-http-client-test reactive-http-client-otel
```

Run the independent starter comparison with a different isolated repository:

```bash
test ! -e target/published-baseline-repositories/api-starter-report-3.6.0 && \
mvn -s .mvn/maven-central-settings.xml \
  -Dmaven.repo.local=target/published-baseline-repositories/api-starter-report-3.6.0 \
  -pl reactive-http-client-starter \
  -Papi-compatibility,major-api-report -DskipTests verify && \
scripts/verify-published-baseline-provenance.sh api-starter-report 3.6.0 \
  target/release-evidence/published-baselines/api-starter-report-3.6.0 \
  reactive-http-client-starter
```

The report-only profile is classification evidence. Post-`4.0.0` development
returns to strict binary and source compatibility against the published
`4.0.0` baseline.
