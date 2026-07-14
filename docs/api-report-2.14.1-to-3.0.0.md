# Public API Report: 2.14.1 to 3.0.0

This source-controlled summary reviews the report-only japicmp comparison
between published 2.14.1 artifacts and the default Boot 4 `3.0.0` reactor.
The comparison is report-only because this is an intentional major-version
boundary.

```bash
mvn -B -ntp -s .mvn/maven-central-settings.xml \
  -Papi-compatibility,major-api-report -DskipTests verify
```

Detailed reports are under each module `target/japicmp` directory.
`major-api-report` disables binary-break failure and ignores missing
generation-specific framework classes only for this explicit report. The normal
`api-compatibility` profile remains strict.

Because report-only mode ignores missing generation-specific framework classes,
the generated summary can omit the health-type removal. The reviewed public
surface map and source delta therefore classify that required replacement as a
major starter change.

## Frozen baseline surface

The baseline is the published 2.14.1 starter, test-helper, and OTel artifacts.
The reviewed surface is the
[documented public surface map](20-native-release-compatibility.md#documented-public-surface-map).
It covers annotations, auth, exceptions, filters, observability, configuration,
documented core SPIs and helpers, test helpers, and OTel.

## Compatibility results

| Module | Result | Reviewed changes |
|---|---|---|
| reactive-http-client-starter | Major | Removes `HttpClientHealthIndicator` and retains the Boot 4 `Boot4HttpClientHealthIndicator`; JSON codec migration APIs already shipped in 2.14.1. |
| reactive-http-client-test | Compatible | No reviewed public helper removal from the 2.14.1 baseline. |
| reactive-http-client-otel | Compatible | No reviewed public OTel API removal from the 2.14.1 baseline. |

## Break classification

### Required by Boot 4

`HttpClientHealthIndicator` is replaced by `Boot4HttpClientHealthIndicator`. Boot 4
moved health contracts from `org.springframework.boot.actuate.health` to
`org.springframework.boot.health.contributor` and changed the public `health()`
return type. Direct type users migrate; bean overrides retain the
`reactiveHttpClientHealthIndicator` name.

### Retained migration APIs

- `ReactiveHttpClientJsonCodec` already exists in 2.14.1 and separates
  starter JSON ownership from Jackson.
- `Jackson3ReactiveHttpClientJsonCodec` is the Boot 4 adapter.
- `Jackson2ReactiveHttpClientJsonCodec` and mapper overloads are deprecated shims.
- `MockReactiveHttpClient.Builder.jsonCodec(...)` already exists in 2.14.1 and
  provides helper parity.

### Accidental or unrelated breaks

None. There are no changes to annotations, exception categories, lifecycle
hooks, observer events, diagnostics sanitization, retry and idempotency,
request contexts, auth SPIs, or OTel observer and filter APIs.

## Review policy

Every new incompatible row requires a category here and a matching instruction
in [Spring Boot 4 and Starter 3.x Migration](28-spring-boot-4-jackson-migration.md).
An unrelated removal blocks 3.0.0 and must be restored.
