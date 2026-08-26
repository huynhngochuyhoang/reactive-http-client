# Starter 3.x to 4.x Resilience Migration

This is the migration report for published `4.0.0`. Applications upgrading
from `3.6.0` must make each intended Resilience4j operator explicit.

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
| Blank or absent client-level instance | A blank configured value is forwarded to the operator applier rather than treated as disabled. An absent property retains the constructor value `default`; blank method annotations are rejected. | Disabled for that operator. | Use explicit `default` instead of relying on absence or a blank value. |
| `retry-methods` only | Restricts eligibility for the Retry selected by the default property. | Remains eligibility only and does not activate Retry. | Add `retry: default` or a named Retry when retry is intended. |
| Selected operator with no matching registry/adapter | The configured operator is passed through a no-op application boundary. | The effective policy is `unavailable`; no operator is applied or subscribed. | Add the matching Resilience4j registry and Reactor adapter, or remove the selection. |
| Selected operator with a lazy registry that diagnostics cannot inspect safely | Runtime client creation resolves the registry; support snapshots can otherwise understate the selection as unavailable. | Support snapshots report `unknown` without creating the lazy registry or `FactoryBean` product. | Treat `unknown` as unproven and inspect the application-context lifecycle before changing policy. |
| Strict unsafe-retry validation | Runs only when an effective Retry can make another attempt. | Same rule; an unselected Retry keeps validation dormant. | None unless the client relied on implicit Retry activation. |

The effective policy uses four stable states on every starter surface:

| State | Meaning |
|---|---|
| `disabled` | The master gate is off, the instance selection is blank/absent, or Retry is not eligible for the resolved HTTP method. |
| `unavailable` | An instance is selected, but the matching operator registry or adapter is not available. |
| instance name | The selected operator is available and active. |
| `unknown` | Diagnostics cannot prove availability without creating a lazy registry or `FactoryBean` product. Runtime client construction still resolves its normal dependencies. |

## Explicit single-operator examples

Start with only the operator the client needs. Every example below is complete
at the starter-property layer. Client-level names may be absent from
`resilience4j.*.instances`; Resilience4j then creates the selected name from the
registry defaults. Define a named instance only when it needs configuration that
differs from those defaults.

### Retry only

```yaml
reactive:
  http:
    clients:
      orders-api:
        resilience:
          enabled: true
          retry: default
```

Retry remains limited by `retry-methods`, whose default is `GET,HEAD`.
Use a named instance such as `retry: orders-read` instead of `default` when
that client has a dedicated policy.

### CircuitBreaker only

```yaml
reactive:
  http:
    clients:
      catalog-api:
        resilience:
          enabled: true
          circuit-breaker: catalog-read
```

### Bulkhead only

```yaml
reactive:
  http:
    clients:
      inventory-api:
        resilience:
          enabled: true
          bulkhead: inventory-read
```

### RateLimiter only

```yaml
reactive:
  http:
    clients:
      partner-api:
        resilience:
          enabled: true
          rate-limiter: partner-read
```

Do not add another operator property unless that operator is intended for the
client. A named value and the literal value `default` are both explicit
selections.

### All four published defaults

To retain the published `3.6.0` enabled-only behavior exactly, select all four
default instances explicitly:

```yaml
reactive:
  http:
    clients:
      legacy-all-operators:
        resilience:
          enabled: true
          retry: default
          rate-limiter: default
          circuit-breaker: default
          bulkhead: default
```

## Method precedence and validation

The effective selection is deterministic for each method:

1. A non-blank method annotation such as `@Retry("orders-read")` overrides
   the matching client-level instance.
2. Blank method annotation values fail startup; they do not disable an
   inherited client selection.
3. Without a method annotation, the non-blank client property is used.
4. An absent or blank client property disables that operator for the method.
5. `resilience.enabled: false` disables every operator regardless of an
   annotation or instance property.

Retry has one additional eligibility gate. The resolved HTTP method must be in
`retry-methods`; an ineligible method reports Retry as disabled even when an
instance is selected. A selected method annotation is validated only when its
operator is available, and Retry annotations are validated only when the method
is eligible. Missing active method-annotation instances fail proxy construction.
Client-level instance properties are not registry-membership fail-fast checks:
when a selected client-level name is absent, Resilience4j resolves it from the
registry defaults instead of the starter rejecting proxy construction for the
missing named instance.

`strict-unsafe-retry-validation: true` runs only when the effective Retry is
available, eligible, and can make more than one attempt. It does not activate
Retry. Startup accepts an unsafe method only when it can prove a non-overridable
idempotency-key contract; nullable runtime headers and Reactor-context values
remain runtime checks. See
[Strict unsafe-retry validation](07-resilience4j.md#strict-unsafe-retry-validation)
for the full proof rules.

## Initial API report

The initial `4.0.0-SNAPSHOT` lane changed reactor coordinates only. The final
candidate adds reviewed cache and terminal-diagnostics APIs but contains no
incompatible Java API row relative to published `3.6.0`. The behavior migration
above is not represented by a Java signature diff.

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
strict command. Strict mode fails on binary or source incompatibilities; the
report-only profile disables both failure switches so the report still runs
after a strict failure:

```bash
test ! -e target/published-baseline-repositories/api-major-report-3.6.0 && \
mvn -s .mvn/maven-central-settings.xml \
  -Dmaven.repo.local=target/published-baseline-repositories/api-major-report-3.6.0 \
  -Papi-compatibility,major-api-report -DskipTests verify
```

Priority 14 freezes the final reviewed incompatible delta before release. Any
strict japicmp failure remains an unresolved release blocker rather than an
implicitly accepted consequence of the major version.

## Release state

- Released major: `4.0.0` from tag `v4.0.0`.
- Latest published and API baseline: `4.0.0`.
- Public README and quick-start coordinates: `4.0.0`.
- Development continues on `4.1.0-SNAPSHOT` after Maven Central verification.
