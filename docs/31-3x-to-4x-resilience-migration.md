# Starter 3.x to 4.x Resilience Migration

This is the initial migration report for the `4.0.0-SNAPSHOT` development
line. Published consumer coordinates remain `3.6.0`; `4.0.0` is deferred until
the V27 resilience and caching contracts, compatibility evidence, and release
gates are complete.

The major version is required by one behavior change: selecting
`reactive.http.clients.<name>.resilience.enabled=true` will no longer select all
available `default` Resilience4j operators. Each operator must be selected by an
explicit client property or method annotation. V27 does not change the existing
operator composition order, retry-method eligibility, or master-gate meaning.

## Migration matrix

| Configuration | Published `3.6.0` behavior | `4.0.0` target behavior | Migration |
|---|---|---|---|
| `resilience.enabled: false` | No operator is applied. | Unchanged. | None. |
| `resilience.enabled: true` with no instance properties | The default property values can select every available `default` Retry, CircuitBreaker, Bulkhead, and RateLimiter. | No operator is selected. | Add only the operator properties the client requires. |
| `retry: default` | Selects the `default` Retry when available and the method is retry-eligible. | Unchanged explicit selection. | Keep the property when Retry is intended. |
| Named client-level instance | Selects that operator instance when available. | Unchanged explicit selection. | None. |
| Method-level resilience annotation | Selects that method's instance under the enabled master gate. | Unchanged explicit selection and precedence. | None. |
| Blank or absent client-level instance | Does not name a usable instance. The published defaults can still supply `default` when the property is absent. | Disabled for that operator. | Use explicit `default` instead of relying on absence. |
| `retry-methods` only | Restricts eligibility for the Retry selected by the default property. | Remains eligibility only and does not activate Retry. | Add `retry: default` or a named Retry when retry is intended. |
| Strict unsafe-retry validation | Runs only when an effective Retry can make another attempt. | Same rule; an unselected Retry keeps validation dormant. | None unless the client relied on implicit Retry activation. |

Equivalent explicit single-operator configuration:

```yaml
reactive:
  http:
    clients:
      orders-api:
        resilience:
          enabled: true
          retry: default
```

Do not add CircuitBreaker, Bulkhead, or RateLimiter properties unless those
operators are intended for this client.

## Initial API report

The initial `4.0.0-SNAPSHOT` lane changes reactor coordinates only. It contains
no reviewed binary or source incompatibility relative to published `3.6.0`.
The behavior migration above is not represented by a Java signature diff.

Strict compatibility remains the release guard and is run for the root reactor
and starter module from separate, previously absent Maven Central repositories:

```bash
test ! -e target/published-baseline-repositories/api-root-3.6.0 && \
mvn -s .mvn/maven-central-settings.xml \
  -Dmaven.repo.local=target/published-baseline-repositories/api-root-3.6.0 \
  -Papi-compatibility -DskipTests verify

test ! -e target/published-baseline-repositories/api-starter-3.6.0 && \
mvn -s .mvn/maven-central-settings.xml \
  -Dmaven.repo.local=target/published-baseline-repositories/api-starter-3.6.0 \
  -pl reactive-http-client-starter \
  -Papi-compatibility -DskipTests verify
```

The report-only major lane is additional evidence, not a replacement for either
strict command:

```bash
test ! -e target/published-baseline-repositories/api-major-report-3.6.0 && \
mvn -s .mvn/maven-central-settings.xml \
  -Dmaven.repo.local=target/published-baseline-repositories/api-major-report-3.6.0 \
  -Papi-compatibility,major-api-report -DskipTests verify
```

Priority 14 freezes the final reviewed incompatible delta before release. Until
then, any strict japicmp failure is an unresolved release blocker rather than an
implicitly accepted consequence of the major version.

## Release state

- Development version: `4.0.0-SNAPSHOT`.
- Latest published and API baseline: `3.6.0`.
- Public README and quick-start coordinates: `3.6.0`.
- `4.0.0` publication: deferred pending all V27 priorities.

