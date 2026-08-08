# Public API Report: 2.14.0 to 3.0.0 Candidate

> **Immutable historical API evidence.** This report describes only the named
> candidate comparison. Do not rerun its command as current compatibility
> guidance; use [Native Image and Release Compatibility](20-native-release-compatibility.md).

This source-controlled summary reviews the report-only japicmp comparison
between published 2.14.0 artifacts and the isolated Boot 4 candidate assembled
from the 2.14.1 workspace. The candidate label does not publish or rename the
maintenance reactor.

```bash
mvn -B -ntp -s .mvn/boot4-spike-settings.xml \
  -Pboot4-spike,api-compatibility,major-api-report \
  -DskipTests -Dmaven.javadoc.skip=true verify
```

Detailed reports are under each module target/japicmp directory.
major-api-report disables binary-break failure and ignores missing
generation-specific framework classes only for this explicit report. The normal
api-compatibility profile remains strict.

## Frozen baseline surface

The baseline is the published 2.14.0 starter, test-helper, and OTel artifacts.
The reviewed surface is the
[documented public surface map](20-native-release-compatibility.md#documented-public-surface-map).
It covers annotations, auth, exceptions, filters, observability, configuration,
documented core SPIs and helpers, test helpers, and OTel.

## Compatibility results

| Module | Result | Reviewed changes |
|---|---|---|
| reactive-http-client-starter | Major | Removed HttpClientHealthIndicator; added Boot4HttpClientHealthIndicator, ReactiveHttpClientJsonCodec, Jackson 2 and 3 adapters, and a codec Problem Detail constructor. |
| reactive-http-client-test | Compatible additive | Added MockReactiveHttpClient.Builder.jsonCodec(...); deprecated but retained objectMapper(...). |
| reactive-http-client-otel | Compatible | Added only the generation-selected customizer configuration import. |

## Break classification

### Required by Boot 4

HttpClientHealthIndicator is replaced by Boot4HttpClientHealthIndicator. Boot 4
moved health contracts from org.springframework.boot.actuate.health to
org.springframework.boot.health.contributor and changed the public health()
return type. Direct type users migrate; bean overrides retain the
reactiveHttpClientHealthIndicator name.

### Intentional additive migration APIs

- ReactiveHttpClientJsonCodec separates starter JSON ownership from Jackson.
- Jackson3ReactiveHttpClientJsonCodec is the Boot 4 adapter.
- Jackson2ReactiveHttpClientJsonCodec and mapper overloads are deprecated shims.
- MockReactiveHttpClient.Builder.jsonCodec(...) provides helper parity.

### Accidental or unrelated breaks

None. There are no changes to annotations, exception categories, lifecycle
hooks, observer events, diagnostics sanitization, retry and idempotency,
request contexts, auth SPIs, or OTel observer and filter APIs.

## Review policy

Every new incompatible row requires a category here and a matching instruction
in [Spring Boot 4 and Starter 3.x Migration](28-spring-boot-4-jackson-migration.md).
An unrelated removal blocks 3.0.0 and must be restored.
