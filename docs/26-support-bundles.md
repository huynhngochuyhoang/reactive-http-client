# Production Support Bundles

This is the canonical current capture procedure for an outbound client incident.
Use it when opening an internal support ticket, handing an incident to an
on-call teammate, or asking maintainers to help diagnose starter behavior. The
goal is a small, support-safe bundle that explains the client configuration and
runtime symptoms without collecting raw request bodies, response bodies, tokens,
secrets, or customer data by default.

Start triage in [Operations Troubleshooting](30-operations-troubleshooting.md),
then return here for the bounded artifacts requested by that decision path.

The commands and endpoint names on this page target published starter `4.1.0`
on Boot 4. Boot 3.5 applications remain on `2.14.1`; use the
[4.x migration guide](28-spring-boot-4-jackson-migration.md) before applying the
Boot 4 health type or native-image instructions.

## Baseline Bundle

Start every bundle with these artifacts:

| Artifact | How to capture | Why it helps |
|---|---|---|
| Starter version | `mvn dependency:tree -Dincludes=io.github.huynhngochuyhoang` or the application build file | Confirms the runtime contract and known fixes. |
| Client names and interfaces | `ReactiveHttpClientDiagnosticsSnapshot` or the `rhttpclients` Actuator endpoint | Shows registered clients, inherited endpoint counts, timeout source, auth mode, resilience, and redirect policy without exposing URLs or secrets. |
| Health detail | `/actuator/health` when the starter health indicator is enabled | Shows recent error-rate status and sample thresholds per client. |
| Metadata-only exchange logs | `log-exchange: true` with `log-preset: metadata-only` for the affected client | Shows method, path template, status, duration, error, and subscription-attempt count while omitting headers and bodies. |
| Relevant exception type and message | Application logs with stack traces, after normal log redaction | Identifies error category, timeout type, auth failure, or response mapping path. |
| Configuration snippets | Only the affected `reactive.http.clients.<name>` block with secret values replaced | Confirms timeout, auth, resilience, proxy, TLS, redirect, and logging settings. |

Do not collect raw request bodies, raw response bodies, bearer tokens, client
secrets, cookies, proxy credentials, or full concrete base URLs unless your
incident process explicitly approves that data. When headers are required, prefer
redacted header names and presence/absence evidence over raw values.

## Reviewable Bundle Fixture

A small reviewable support bundle should keep each artifact separate and use
fake hostnames plus placeholders before it leaves the incident system:

```text
support-bundle/
  diagnostics/rhttpclients-curl-exit-status.txt
  diagnostics/rhttpclients-http-status.txt
  diagnostics/rhttpclients.json
  health/reactive-http-client-health-curl-exit-status.txt
  health/reactive-http-client-health-http-status.txt
  health/health.json
  logs/startup-summary.log
  logs/exchange-metadata.log
  config/reactive-http-client.yml
  performance/benchmark-report-link.txt
```

```yaml
reactive:
  http:
    observability:
      diagnostics-endpoint:
        enabled: true
    clients:
      inventory-api:
        base-url: https://inventory-api.example.invalid
        request-timeout-ms: 750
        follow-redirects: false
        log-exchange: true
        log-preset: metadata-only
        resilience:
          enabled: true
          retry: inventoryReadRetry
          retry-methods:
            - GET

management:
  endpoint:
    health:
      show-details: always
  endpoints:
    web:
      exposure:
        include: health,rhttpclients

logging:
  level:
    io.github.huynhngochuyhoang.httpstarter.core.ReactiveHttpClientFactoryBean: DEBUG
    io.github.huynhngochuyhoang.httpstarter.core.DefaultHttpExchangeLogger: INFO
```

```text
performance/benchmark-report-link.txt
docs/benchmark-report-<version>.md
```

The diagnostics JSON, health JSON, startup summary, metadata-only exchange log,
sanitized client configuration, and promoted benchmark report link together form
the reviewable bundle. The example intentionally uses `.example.invalid`,
placeholder instance names, and metadata-only logging. Replace any production
header values with presence/absence notes before sharing.

Source-controlled incident fixtures show the structural facts expected for
common terminal outcomes:

- [Request validation before subscription](fixtures/support-bundle-request-validation.json)
  creates no lifecycle, observer, or exchange-log terminal record because no
  logical call starts and no request is dispatched.
- [Stale connection followed by a healthy replacement](fixtures/support-bundle-stale-connection-recovery.json)
  keeps the failed and replacement calls separate. A missing status or failure
  stage remains unknown; the example does not infer transport facts from an
  exception message.
- [Terminal outcome comparison](fixtures/support-bundle-terminal-outcomes.json)
  uses milliseconds explicitly and separates a zero-attempt resilience
  rejection, a dispatched transport failure, and a downstream HTTP failure by
  structural status, category, stage, attempt, and dispatch facts.
- [Aggregate response-cache incident](fixtures/support-bundle-response-cache.json)
  records one bounded time window of configuration, lookup, caller, load,
  refresh, eviction, and capacity counts plus one sanitized caller terminal
  record, without cache material or request variants.
- [Cache-memory triage](fixtures/support-bundle-cache-memory.json) records one
  bounded V29 window for one fake client/process instance, API-tagged caller work,
  policy-tagged cache signals, timestamped memory/cache/pool checkpoints, and
  lifecycle events. It contains only fake bounded client/API/policy names and no
  cache keys/values, request variants, identity values, or exception messages.

These fixtures are illustrative sanitized records, not raw logger output. They
contain fake client and path-template metadata only. Default support output must
not copy arbitrary exception messages, concrete request URLs, header values, or
payloads into any record.

## Diagnostics Snapshot

For a one-off JSON or Markdown snapshot, inject
`ReactiveHttpClientDiagnosticsProvider` and use the public helper:

```java
@Component
class SupportBundleExporter {
    private final ReactiveHttpClientDiagnosticsProvider diagnostics;

    SupportBundleExporter(ReactiveHttpClientDiagnosticsProvider diagnostics) {
        this.diagnostics = diagnostics;
    }

    String diagnosticsJson() {
        return ReactiveHttpClientDiagnosticsSnapshot.toJson(diagnostics);
    }

    String diagnosticsMarkdown() {
        return ReactiveHttpClientDiagnosticsSnapshot.toMarkdown(diagnostics);
    }
}
```

Provider-backed snapshots include schema version, project version, total client count, total endpoint count,
total inherited endpoint count, per-client policy summaries including effective pool policy, and strict validation
flags. Summary-only collection snapshots mark pool policy and strict validation flags as unknown.
They do not include concrete base URLs, header values, proxy credentials, auth-provider bean
names, request bodies, or response bodies. Schema v1 is additive within the `3.x` line;
the versioned sanitized [fixture](fixtures/rhttpclients-schema-v1.json), value semantics,
and output limits are defined in [Diagnostic Context Contracts](21-diagnostic-contexts.md#diagnostics-schema-v1).

If Actuator is available and the endpoint is explicitly enabled, the same
sanitized data can be read from the `rhttpclients` endpoint:

```yaml
reactive:
  http:
    observability:
      diagnostics-endpoint:
        enabled: true

management:
  endpoint:
    health:
      show-details: always
  endpoints:
    web:
      exposure:
        include: health,rhttpclients
```

```bash
EXAMPLE_MANAGEMENT_URL="http://<management-host>:<management-port>"
curl -s "$EXAMPLE_MANAGEMENT_URL/actuator/rhttpclients"
```

## Capture Recipes

Use the same artifact names for every environment so reviewers can compare
bundles without learning a deployment-specific layout. The commands below use
placeholder names only. Replace them with local values inside your incident
workspace, and sanitize configuration files before adding them to the bundle.

Each recipe captures separate evidence streams:

| Stream | File | Answers |
|---|---|---|
| Diagnostics endpoint | `diagnostics/rhttpclients-http-status.txt`, `diagnostics/rhttpclients-curl-exit-status.txt`, plus validated `diagnostics/rhttpclients.json` | Whether the transfer completed, which status was returned, and, only for the expected schema, which clients/endpoints and sanitized effective policies exist. |
| Health details | `health/reactive-http-client-health-http-status.txt`, `health/reactive-http-client-health-curl-exit-status.txt`, plus sanitized `health/health.json` | Whether the transfer completed, which status was returned, and, only for the expected component shape, whether recent samples crossed the affected client's error-rate threshold. |
| Startup summary | `logs/startup-summary.log` | Which sanitized client policy was applied when the proxy was created. |
| Metadata-only exchange logs | `logs/exchange-metadata.log` | What happened for the affected calls: method, path template, status, duration, error, and subscription-attempt count. |
| Sanitized configuration | `config/reactive-http-client.yml` | Which `reactive.http.*` settings the application intended to use, without secrets or concrete internal URLs. |
| Release evidence reference | `performance/benchmark-report-link.txt` | Which promoted source-controlled report supports a benchmark-based claim, when the ticket includes one. |

The diagnostics endpoint, health details, startup summaries, exchange logs,
configuration snippets, and release-evidence reference answer different
questions. Do not merge them into a single free-form log dump.

The recipes deliberately use `curl -sS` without `--fail`/`-f` and always write
the HTTP status to a bundle file. `--connect-timeout 5` bounds connection setup
and `--max-time 30` bounds the complete transfer. `--max-filesize 1048576`
bounds each raw endpoint download at 1 MiB. Response bodies first go to
quarantined `*.raw.json`
files outside the bundle. An authentication gateway, reverse proxy, or generic
error handler can return arbitrary sensitive content, so an HTTP error body is
not automatically diagnostics evidence. Retain it only after the shared
validation/sanitization step below confirms the expected endpoint shape and
allowlists its fields. Every recipe removes stale raw files before invoking
`curl` and sets `umask 077` before creating capture files, so newly created
quarantined bodies use mode `0600`. Never attach the raw files.

### Local JVM Capture

Use this when the application process and its management endpoint are reachable
from the same shell:

```bash
EXAMPLE_MANAGEMENT_URL="http://<management-host>:<management-port>"
EXAMPLE_CLIENT_NAME="example-client"
EXAMPLE_APP_LOG="/path/to/sanitized-application.log"
EXAMPLE_SANITIZED_CONFIG="/path/to/sanitized-reactive-http-client.yml"

umask 077
mkdir -p support-bundle/diagnostics support-bundle/health support-bundle/logs support-bundle/config support-bundle/performance
rm -f support-bundle/diagnostics/rhttpclients.json support-bundle/health/health.json
rm -f support-bundle/diagnostics/rhttpclients-curl-exit-status.txt support-bundle/health/reactive-http-client-health-curl-exit-status.txt
rm -f rhttpclients.raw.json reactive-http-client-health.raw.json
if curl -sS --connect-timeout 5 --max-time 30 --max-filesize 1048576 -w '%{http_code}\n' -o rhttpclients.raw.json "$EXAMPLE_MANAGEMENT_URL/actuator/rhttpclients" > support-bundle/diagnostics/rhttpclients-http-status.txt; then
  printf '0\n' > support-bundle/diagnostics/rhttpclients-curl-exit-status.txt
else
  printf '%s\n' "$?" > support-bundle/diagnostics/rhttpclients-curl-exit-status.txt
fi
if curl -sS --connect-timeout 5 --max-time 30 --max-filesize 1048576 -w '%{http_code}\n' -o reactive-http-client-health.raw.json "$EXAMPLE_MANAGEMENT_URL/actuator/health/reactiveHttpClient" > support-bundle/health/reactive-http-client-health-http-status.txt; then
  printf '0\n' > support-bundle/health/reactive-http-client-health-curl-exit-status.txt
else
  printf '%s\n' "$?" > support-bundle/health/reactive-http-client-health-curl-exit-status.txt
fi
grep 'ReactiveHttpClientFactoryBean' "$EXAMPLE_APP_LOG" > support-bundle/logs/startup-summary.log || true
grep 'DefaultHttpExchangeLogger' "$EXAMPLE_APP_LOG" > support-bundle/logs/exchange-metadata.log || true
cp "$EXAMPLE_SANITIZED_CONFIG" support-bundle/config/reactive-http-client.yml
printf 'docs/benchmark-report-<version>.md\n' > support-bundle/performance/benchmark-report-link.txt
```

### Container Capture

Use this when the application runs in a container and logs are read through the
container runtime. The configuration file copied into the bundle must already be
sanitized:

```bash
EXAMPLE_CONTAINER="example-app-container"
EXAMPLE_MANAGEMENT_URL="http://<management-host>:<management-port>"
EXAMPLE_CLIENT_NAME="example-client"
EXAMPLE_SANITIZED_CONFIG_IN_CONTAINER="/path/in/container/sanitized-reactive-http-client.yml"

umask 077
mkdir -p support-bundle/diagnostics support-bundle/health support-bundle/logs support-bundle/config support-bundle/performance
rm -f support-bundle/diagnostics/rhttpclients.json support-bundle/health/health.json
rm -f support-bundle/diagnostics/rhttpclients-curl-exit-status.txt support-bundle/health/reactive-http-client-health-curl-exit-status.txt
rm -f rhttpclients.raw.json reactive-http-client-health.raw.json
if curl -sS --connect-timeout 5 --max-time 30 --max-filesize 1048576 -w '%{http_code}\n' -o rhttpclients.raw.json "$EXAMPLE_MANAGEMENT_URL/actuator/rhttpclients" > support-bundle/diagnostics/rhttpclients-http-status.txt; then
  printf '0\n' > support-bundle/diagnostics/rhttpclients-curl-exit-status.txt
else
  printf '%s\n' "$?" > support-bundle/diagnostics/rhttpclients-curl-exit-status.txt
fi
if curl -sS --connect-timeout 5 --max-time 30 --max-filesize 1048576 -w '%{http_code}\n' -o reactive-http-client-health.raw.json "$EXAMPLE_MANAGEMENT_URL/actuator/health/reactiveHttpClient" > support-bundle/health/reactive-http-client-health-http-status.txt; then
  printf '0\n' > support-bundle/health/reactive-http-client-health-curl-exit-status.txt
else
  printf '%s\n' "$?" > support-bundle/health/reactive-http-client-health-curl-exit-status.txt
fi
docker logs "$EXAMPLE_CONTAINER" --since 30m | grep 'ReactiveHttpClientFactoryBean' > support-bundle/logs/startup-summary.log || true
docker logs "$EXAMPLE_CONTAINER" --since 30m | grep 'DefaultHttpExchangeLogger' > support-bundle/logs/exchange-metadata.log || true
docker cp "$EXAMPLE_CONTAINER:$EXAMPLE_SANITIZED_CONFIG_IN_CONTAINER" support-bundle/config/reactive-http-client.yml
printf 'docs/benchmark-report-<version>.md\n' > support-bundle/performance/benchmark-report-link.txt
```

### Kubernetes-Style Capture

Use this shape for a pod-based deployment. Run the port-forward command in a
separate terminal, then capture the bundle from another shell:

```bash
EXAMPLE_NAMESPACE="example-namespace"
EXAMPLE_POD="example-app-pod"
EXAMPLE_CONTAINER="example-app-container"
EXAMPLE_LOCAL_PORT="18080"
EXAMPLE_MANAGEMENT_PORT="<management-port>"
EXAMPLE_SANITIZED_CONFIG_IN_POD="/path/in/pod/sanitized-reactive-http-client.yml"

kubectl -n "$EXAMPLE_NAMESPACE" port-forward "pod/$EXAMPLE_POD" "$EXAMPLE_LOCAL_PORT:$EXAMPLE_MANAGEMENT_PORT"
```

```bash
EXAMPLE_NAMESPACE="example-namespace"
EXAMPLE_POD="example-app-pod"
EXAMPLE_CONTAINER="example-app-container"
EXAMPLE_LOCAL_PORT="18080"
EXAMPLE_MANAGEMENT_PORT="<management-port>"
EXAMPLE_SANITIZED_CONFIG_IN_POD="/path/in/pod/sanitized-reactive-http-client.yml"
EXAMPLE_MANAGEMENT_URL="http://127.0.0.1:$EXAMPLE_LOCAL_PORT"
EXAMPLE_CLIENT_NAME="example-client"
umask 077
mkdir -p support-bundle/diagnostics support-bundle/health support-bundle/logs support-bundle/config support-bundle/performance
rm -f support-bundle/diagnostics/rhttpclients.json support-bundle/health/health.json
rm -f support-bundle/diagnostics/rhttpclients-curl-exit-status.txt support-bundle/health/reactive-http-client-health-curl-exit-status.txt
rm -f rhttpclients.raw.json reactive-http-client-health.raw.json
if curl -sS --connect-timeout 5 --max-time 30 --max-filesize 1048576 -w '%{http_code}\n' -o rhttpclients.raw.json "$EXAMPLE_MANAGEMENT_URL/actuator/rhttpclients" > support-bundle/diagnostics/rhttpclients-http-status.txt; then
  printf '0\n' > support-bundle/diagnostics/rhttpclients-curl-exit-status.txt
else
  printf '%s\n' "$?" > support-bundle/diagnostics/rhttpclients-curl-exit-status.txt
fi
if curl -sS --connect-timeout 5 --max-time 30 --max-filesize 1048576 -w '%{http_code}\n' -o reactive-http-client-health.raw.json "$EXAMPLE_MANAGEMENT_URL/actuator/health/reactiveHttpClient" > support-bundle/health/reactive-http-client-health-http-status.txt; then
  printf '0\n' > support-bundle/health/reactive-http-client-health-curl-exit-status.txt
else
  printf '%s\n' "$?" > support-bundle/health/reactive-http-client-health-curl-exit-status.txt
fi
kubectl -n "$EXAMPLE_NAMESPACE" logs "$EXAMPLE_POD" -c "$EXAMPLE_CONTAINER" --since=30m | grep 'ReactiveHttpClientFactoryBean' > support-bundle/logs/startup-summary.log || true
kubectl -n "$EXAMPLE_NAMESPACE" logs "$EXAMPLE_POD" -c "$EXAMPLE_CONTAINER" --since=30m | grep 'DefaultHttpExchangeLogger' > support-bundle/logs/exchange-metadata.log || true
kubectl -n "$EXAMPLE_NAMESPACE" exec "$EXAMPLE_POD" -c "$EXAMPLE_CONTAINER" -- cat "$EXAMPLE_SANITIZED_CONFIG_IN_POD" > support-bundle/config/reactive-http-client.yml
printf 'docs/benchmark-report-<version>.md\n' > support-bundle/performance/benchmark-report-link.txt
```

### Validate and sanitize captured endpoint bodies

Run this in the same capture workspace after any recipe above. Point
`EXAMPLE_RHTTPCLIENTS_SCHEMA` at a reviewed copy of the source-controlled
`docs/fixtures/rhttpclients-schema-v1.json`. The first filter requires a 2xx
HTTP status, schema V1, every version-applicable required field, the expected
recursive leaf types (including documented nullable unknown states), nonnegative
numeric values, 512-character strings, bounded arrays/counts, and at most 1 MiB
of UTF-8 JSON before it retains allowlisted fields. Before either filter starts,
the shell also verifies that curl reported a successful transfer, the quarantined
raw file exists, and the file is at most 1 MiB; this bounds whitespace and other
bytes that compacted `tojson` does not measure.
The two V29 decoded-response byte fields are optional only when `projectVersion`
identifies a published `4.1.x` response. A V29 `4.2.0-SNAPSHOT` response must
include both fields, although their values may be `null` where the diagnostics
contract permits an unknown state. Each filter slurps the raw input and requires
exactly one parsed JSON value, so empty bodies and JSON streams are rejected. The
health filter also requires a `2xx` status or a `5xx` response whose top-level
status is `DOWN`, rejecting authentication and other `4xx` responses while
retaining a structurally valid Actuator DOWN response. It requires the requested
client entry and emits only the documented
structural health fields. For nonzero samples, the reported error rate must equal
`errors / samples` within an absolute tolerance of `0.000000000001`. Its
top-level status is derived from that selected client, not from unrelated clients
in the aggregate health response. The sanitized projection preserves omission of
`errorRate` when the selected client has zero samples:

```bash
EXAMPLE_RHTTPCLIENTS_SCHEMA="/path/to/reviewed/rhttpclients-schema-v1.json"

test "$(cat support-bundle/diagnostics/rhttpclients-curl-exit-status.txt)" = "0" &&
  test -f rhttpclients.raw.json &&
  test "$(wc -c < rhttpclients.raw.json)" -le 1048576 &&
  jq --slurp \
  --arg httpStatus "$(cat support-bundle/diagnostics/rhttpclients-http-status.txt)" \
  --slurpfile schema "$EXAMPLE_RHTTPCLIENTS_SCHEMA" '
  def nonnegative_integer:
    (type == "number") and (. >= 0) and (. <= 9223372036854775807)
      and (. == floor);
  def nullable_number($field):
    ([
      "poolMaxConnections", "poolPendingAcquireTimeoutMs",
      "poolMaxConcurrentStreams", "cachePolicyCount", "cacheTtlMs",
      "cacheRefreshAfterMs", "cacheMaximumSize",
      "cacheMaximumTotalDecodedResponseBytes",
      "cacheRetainedDecodedResponseBytes", "cacheEntryCount",
      "cacheEvictions", "logicalCallTimeoutMs", "codecMaxInMemorySizeMb"
    ] | index($field)) != null;
  def nullable_boolean($field):
    ([
      "poolMetricsEnabled", "cacheMetricsEnabled",
      "cacheSemanticReadAcknowledged", "compressionEnabled",
      "strictUnsafeRetryValidation", "strictBodySigningValidation"
    ] | index($field)) != null;
  def nullable_array($field):
    $field == "cachePolicySources" or $field == "cacheHttpMethods";
  def published_4_1($version):
    ($version | type) == "string"
      and ($version | test("^4\\.1\\.[0-9]+$"));
  def optional_field($projectVersion; $field):
    published_4_1($projectVersion)
      and ($field == "cacheMaximumTotalDecodedResponseBytes"
        or $field == "cacheRetainedDecodedResponseBytes");
  def valid_leaf($field; $shape):
    ($shape | type) as $expected
    | if $expected == "string" then
        (type == "string") and (length <= 512)
      elif $expected == "number" then
        nonnegative_integer
          or (nullable_number($field) and type == "null")
      elif $expected == "boolean" then
        type == "boolean"
          or (nullable_boolean($field) and type == "null")
      elif $expected == "null" then
        if nullable_boolean($field)
        then type == "null" or type == "boolean"
        elif nullable_number($field)
        then type == "null" or nonnegative_integer
        else false
        end
      else false
      end;
  def keep_shape($shape; $field; $projectVersion):
    ($shape | type) as $expected
    | if $expected == "object" then
        ($shape | keys
          | map(select((optional_field($projectVersion; .)) | not))) as $required
        | if (type == "object"
            and (($required - keys) | length) == 0)
          then with_entries(
            select(.key as $key | $shape | has($key))
            | . as $entry
            | .value = ($entry.value
                | keep_shape($shape[$entry.key]; $entry.key; $projectVersion))
          )
          else error("unexpected diagnostics object")
          end
      elif $expected == "array" then
        if type == "null" and nullable_array($field) then .
        elif type != "array" then error("unexpected diagnostics array")
        elif $field == "clients" and length <= 256 then
          [.[] | keep_shape($shape[0]; "client"; $projectVersion)]
        elif (($field == "cachePolicySources" or $field == "cacheHttpMethods")
            and length <= 16
            and all(.[]; type == "string" and length <= 512))
        then .
        else error("unexpected diagnostics array")
        end
      elif valid_leaf($field; $shape) then .
      else error("unexpected diagnostics scalar")
      end;
  if length == 1 then .[0]
  else error("expected exactly one diagnostics JSON value")
  end
  | if (($httpStatus | test("^2[0-9][0-9]$"))
      and .schemaVersion == 1
      and (.clients | type) == "array"
      and (.clients | length) <= 256
      and ((tojson | utf8bytelength) <= 1048576))
  then .projectVersion as $projectVersion
    | keep_shape($schema[0]; "root"; $projectVersion)
    | if (.clientCount == (.clients | length)
        and .endpointCount <= 10000
        and .inheritedEndpointCount <= .endpointCount
        and all(.clients[]; .inheritedEndpointCount <= .endpointCount)
        and ([.clients[].endpointCount] | add // 0) == .endpointCount
        and ([.clients[].inheritedEndpointCount] | add // 0)
          == .inheritedEndpointCount
        and ((tojson | utf8bytelength) <= 1048576))
      then .
      else error("inconsistent diagnostics counts")
      end
  else error("unexpected rhttpclients response")
  end
' rhttpclients.raw.json > rhttpclients.sanitized.json &&
  mv rhttpclients.sanitized.json support-bundle/diagnostics/rhttpclients.json

test "$(cat support-bundle/health/reactive-http-client-health-curl-exit-status.txt)" = "0" &&
  test -f reactive-http-client-health.raw.json &&
  test "$(wc -c < reactive-http-client-health.raw.json)" -le 1048576 &&
  jq --slurp \
  --arg httpStatus "$(cat support-bundle/health/reactive-http-client-health-http-status.txt)" \
  --arg client "$EXAMPLE_CLIENT_NAME" '
  def nonnegative_integer:
    (type == "number") and (. >= 0) and (. == floor);
  def unit_rate:
    (type == "number") and (. >= 0) and (. <= 1);
  def rate_matches($detail):
    ($detail.errorRate | unit_rate)
      and (($detail.errors / $detail.samples) as $calculated
        | (($detail.errorRate - $calculated) >= -0.000000000001)
        and (($detail.errorRate - $calculated) <= 0.000000000001));
  if length == 1 then .[0]
  else error("expected exactly one health JSON value")
  end
  | .details[$client] as $detail
  | if (((($httpStatus | test("^2[0-9][0-9]$"))
          or (($httpStatus | test("^5[0-9][0-9]$")) and .status == "DOWN"))
      and (.status == "UP" or .status == "DOWN"))
      and ((.details | type) == "object")
      and (($detail | type) == "object")
      and ($detail.samples | nonnegative_integer)
      and ($detail.errors | nonnegative_integer)
      and ($detail.sampleCount | nonnegative_integer)
      and ($detail.errorCount | nonnegative_integer)
      and ($detail.poolAcquireFailureCount | nonnegative_integer)
      and ($detail.minSamples | nonnegative_integer)
      and ($detail.minSamples >= 1)
      and ($detail.errorRateThreshold | unit_rate)
      and ($detail.samples == $detail.sampleCount)
      and ($detail.errors == $detail.errorCount)
      and ($detail.errors <= $detail.samples)
      and ($detail.poolAcquireFailureCount <= $detail.errors)
      and (($detail.samples == 0) or rate_matches($detail))
      and (
        ($detail.reason == "NO_SAMPLES"
          and $detail.status == "INSUFFICIENT_SAMPLES"
          and $detail.samples == 0
          and $detail.errorRate == null)
        or ($detail.reason == "INSUFFICIENT_SAMPLES"
          and $detail.status == "INSUFFICIENT_SAMPLES"
          and $detail.samples > 0
          and $detail.samples < $detail.minSamples
          and ($detail.errorRate | unit_rate))
        or ($detail.reason == "ERROR_RATE_WITHIN_THRESHOLD"
          and $detail.status == "UP"
          and $detail.samples >= $detail.minSamples
          and ($detail.errorRate | unit_rate)
          and $detail.errorRate <= $detail.errorRateThreshold)
        or ($detail.reason == "ERROR_RATE_ABOVE_THRESHOLD"
          and $detail.status == "DOWN"
          and $detail.samples >= $detail.minSamples
          and ($detail.errorRate | unit_rate)
          and $detail.errorRate > $detail.errorRateThreshold)
      ))
  then {
    status: (if $detail.status == "DOWN" then "DOWN" else "UP" end),
    details: {
      ($client): (
        $detail
        | {
            samples, errors, sampleCount, errorCount, poolAcquireFailureCount,
            minSamples, errorRateThreshold, status, reason
          }
        | if $detail.samples == 0 then .
          else . + {errorRate: $detail.errorRate}
          end
      )
    }
  }
  else error("unexpected reactive HTTP client health response")
  end
' reactive-http-client-health.raw.json > reactive-http-client-health.sanitized.json &&
  mv reactive-http-client-health.sanitized.json support-bundle/health/health.json

rm -f rhttpclients.raw.json rhttpclients.sanitized.json \
  reactive-http-client-health.raw.json reactive-http-client-health.sanitized.json
```

If `curl` reports any nonzero transfer status, including a connection timeout,
total-transfer timeout, transfer-bound, truncation, or connection-reset failure,
a raw-size check fails, an input does not contain exactly one JSON value, an HTTP
status is ineligible, or either `jq` command otherwise fails,
keep the HTTP and curl exit-status files, omit the endpoint body from the bundle,
and delete the raw file. Do not weaken the shape check to retain a gateway, proxy,
authentication, or generic error document.

The Kubernetes recipe uses `kubectl exec ... cat` instead of `kubectl cp` so it
does not require `tar` in the application image. If the image also lacks `cat`
or cannot read the sanitized file path, capture the already-sanitized
configuration from the deployment source and place it at
`support-bundle/config/reactive-http-client.yml`.

Keep client, namespace, pod, container, file path, and management URL values as
placeholders in shared examples. Before attaching a bundle, inspect every file
for concrete hosts, credentials, cookies, authorization headers, request bodies,
response bodies, and customer data.

## Health Details

For error-rate incidents, include the affected client entry from
`/actuator/health`. A useful detail block contains `samples`, `errors`,
`sampleCount`, `errorCount`, `poolAcquireFailureCount`, `minSamples`,
`errorRateThreshold`, `errorRate`, `status`, and `reason`. The integer
counters cover the same probe-to-probe window; `errorRate` is omitted when
that window has no samples. Health does not consume duration sums, time-window
maxima, percentiles, or histogram buckets. When a bundle includes Prometheus
screenshots or queries, use the
[unit-safe dashboard recipes](08-observability.md#dashboard-recipes).

The source-controlled [health fixture](fixtures/support-bundle-health.json) is
the complete sanitized structural example:

```json
{
  "status": "DOWN",
  "details": {
    "user-service": {
      "samples": 10,
      "errors": 8,
      "sampleCount": 10,
      "errorCount": 8,
      "poolAcquireFailureCount": 2,
      "minSamples": 10,
      "errorRateThreshold": 0.5,
      "errorRate": 0.8,
      "status": "DOWN",
      "reason": "ERROR_RATE_ABOVE_THRESHOLD"
    },
    "errorRateThreshold": 0.5,
    "minSamples": 10
  }
}
```

Health details are recent Micrometer error-rate signals. They do not describe the
configured endpoint contract, final request headers, response headers, request
bodies, or response bodies.

## Log Categories

Use targeted logging for the affected client and time window. Start with startup
summaries and metadata-only exchange logs:

```yaml
logging:
  level:
    io.github.huynhngochuyhoang.httpstarter.core.ReactiveHttpClientFactoryBean: DEBUG
    io.github.huynhngochuyhoang.httpstarter.core.DefaultHttpExchangeLogger: INFO

reactive:
  http:
    clients:
      user-service:
        log-exchange: true
        log-preset: metadata-only
```

The startup summary is DEBUG-only and sanitized. Metadata-only exchange logs omit
headers and bodies while still reporting status, duration, error, and
subscription-attempt count. Move to `log-preset: headers` only when header
presence is required and redaction is acceptable. Use `log-preset: bodies` only
under an approved incident procedure.

## Configuration Issues

Minimal safe bundle:

- Diagnostics snapshot for the affected client.
- The sanitized `reactive.http.clients.<name>` YAML block.
- Startup summary logs from `ReactiveHttpClientFactoryBean` at DEBUG.
- The startup exception and stack trace, if the proxy fails to build.
- The annotation or `@ApiRef` method signature for the affected endpoint.

This bundle is usually enough to diagnose client-name mismatches, missing API-map
entries, invalid path variables, inherited endpoint behavior, timeout precedence,
redirect policy, and auth/resilience precedence.

## OAuth2 and Auth Failures

Minimal safe bundle:

- Diagnostics snapshot showing `authMode` for the affected client.
- Sanitized auth configuration with `client-id`, `client-secret`, tokens, and
  authorization headers replaced.
- `AuthProviderException` class name, message, client name, and cause type.
- Token endpoint HTTP status, safe headers such as `Retry-After`, and sanitized
  response-body snippet when present. Do not collect the token request metadata
  or credential-bearing response headers from the exception cause.
- Metadata-only exchange logs for the downstream API call that failed after auth.

Do not include raw token endpoint request bodies, raw token responses, bearer
tokens, Basic credentials, refresh tokens, client secrets, cookies, or signed
Authorization headers.

For the built-in object-style OAuth2 provider, record only the sanitized
`token-service` policy: connect/request timeout, maximum connections, pending
acquire timeout, retry attempts/backoff, whether a proxy is configured, and
whether custom TLS is configured. Do not record proxy credentials, trust-store
passwords, client IDs, client secrets, token endpoint query strings, or tokens.
Token-service failures remain `AuthProviderException` failures for the logical
business client; they are not promoted into business-request DNS, connect,
proxy, TLS, or response stages. Raw token-request wrappers are replaced by a fixed transport diagnostic and a
cause-type-only safe cause so request metadata cannot expose Basic credentials.
For AWS SigV4, include the body shape (`byte[]`, `String`,
JSON object, publisher, multipart, or empty) and whether the request uses a
custom `WebClient` codec configuration; do not include the signature or payload.

## Retry and Idempotency Behavior

Minimal safe bundle:

- Diagnostics snapshot showing resilience configuration for the client.
- The sanitized client resilience YAML and relevant Resilience4j instance config.
- Metadata-only exchange log lines for one logical call, including
  `subscriptionAttemptCount`.
- The Java method signature, HTTP method, and whether an `Idempotency-Key` is
  provided by annotation, header parameter, default header, or request context.
- Lifecycle hook or observer output if your application already records it.

Attempt counts are subscription attempts, not proof that each attempt reached the
network. Do not collect idempotency-key values by default; record only whether a
key was present and which source provided it.

## Protocol and Compression Incidents

Minimal safe bundle:

- Provider-backed diagnostics snapshot showing `poolProtocol`,
  `compressionEnabled`, and the decoded unary `codecMaxInMemorySizeMb` policy for
  the affected client.
- Downstream-observed protocol (`HTTP/1.1`, H2, or H2C), TLS/ALPN mode, and the
  proxy, ingress, or service-mesh hops in the path.
- The complete first Reactor Netty decoder exception and channel context for a
  malformed-request warning; do not report only the synthetic
  `GET /bad-request HTTP/1.0` placeholder.
- Presence or absence of request `Accept-Encoding` and post-transport response
  `Content-Encoding`/`Content-Length`, with values sanitized when needed.
- Exception type and whether JSON, an error body, `ResponseEntity`, a direct
  stream, or a streaming envelope was being decoded.

Do not include request or response payloads by default. Automatic gzip
decompression can remove encoded representation headers, so an unknown response
size is expected and is not evidence of a missing body. When compression is
enabled, application-provided `Accept-Encoding` is a configuration conflict.
Record only header presence unless an approved incident procedure requires a
sanitized value. Distinguish encoded wire bytes, decoded application bytes, an
advertised post-transport length, bytes actually consumed, and an unknown size. A
framing-complete truncated gzip member can yield partial decoded data without an
exception, so retain application checksum or format-completeness evidence when full
payload integrity matters.

## Pool Saturation Incidents

Minimal safe bundle:

- Provider-backed diagnostics snapshot showing configured protocol, capacity basis,
  pool source, maximum physical connections, pending-acquire timeout, and whether
  pool metrics are enabled. A null H2 stream limit is expected.
- `reactive.http.client.connection.pool.idle.connections` and `.total.connections`
  for the affected client. Add `.active.connections` and `.pending.connections`
  for HTTP/1.1 or `.active.streams` and `.pending.streams` for HTTP/2.
- Request timer samples grouped by `failure.stage`; `POOL_ACQUIRE` is bounded
  proof of a Reactor Pool admission failure, but does not by itself distinguish
  connection from H2 stream pressure. An absent stage is unknown.
- Health details including `poolAcquireFailureCount` for the same probe window.
- Metadata-only exchange logs or lifecycle/observer output with category and stage.

Do not add concrete upstream addresses to the bundle by default. The stage and
pool policy are sufficient for saturation triage; enable server-address reporting
only under the existing opt-in and sanitize it before sharing.

## Stale Connection Recovery Incidents

Keep the failed call and a later independent replacement call as separate
records. Capture only:

- A bounded time window, logical client and method, HTTP protocol, and sanitized
  path template for each call.
- Terminal status, `ErrorCategory`, optional `failure.stage`,
  `requestDispatched`, subscription-attempt count, and exception/cause class
  names. A missing status or stage remains unknown.
- Downstream-observed request count and a sanitized connection-sequence marker
  that can distinguish the retired socket from replacement capacity without
  exposing a peer address.
- Active, pending, idle, and total pool gauges immediately before the failure,
  while replacement demand waits, and after both calls terminate.
- Whether Resilience4j retry, one-time `401` auth replay, or automatic redirects
  were enabled, plus idempotency-key presence and body-repeatability class. Do
  not include key values or request bodies.

Do not merge the replacement URL, status, headers, error, or failure stage into
the failed call. A removed socket and newly available pool capacity are not
proof that the failed request was replayed. Use the sanitized
[stale-connection fixture](fixtures/support-bundle-stale-connection-recovery.json)
as the structural example and correlate it with downstream connection evidence.

## DNS, Proxy, Connect, and TLS Incidents

Minimal safe bundle:

- Metadata-only terminal observer, lifecycle, or exchange-log evidence with the outermost
  exception class, bounded cause-class chain, compatible `ErrorCategory`, and optional
  `DNS_RESOLUTION`, `PROXY_CONNECT`, `CONNECT`, or `TLS_HANDSHAKE` stage.
- Sanitized effective proxy/TLS mode and timeout source; omit proxy credentials, trust-store
  and key-store contents, private keys, concrete internal addresses, and unapproved names.
- The matching approved resolver, proxy, connect, or TLS reproduction from
  [Operations Troubleshooting](30-operations-troubleshooting.md#pre-response-transport-failures).
- Health sample/error counts for the same client and time window. Health intentionally has
  no new per-stage counters; request timer tags carry the bounded stage.

An `AuthProviderException` is a hard boundary even when its token-service cause is DNS,
proxy, connect, or TLS related. Arbitrary custom-filter wrappers before final request
observation are also stage-unknown. Do not attribute those failures to the downstream
business request.

## Timeout Incidents

Minimal safe bundle:

- Diagnostics snapshot showing timeout source and value for the affected client.
- The method annotation or API-map entry when it overrides client timeout.
- Network timeout settings: connect, read safety net, and write safety net.
- Metadata-only exchange log or observer event with duration, status when
  available, exception type, subscription-attempt count, error category, and
  optional proven failure stage.
- Health details for the affected client and the same time window.

`RESPONSE_HEADERS` proves final request dispatch but has no status or response
headers. A nested auth or other pre-dispatch read timeout leaves the stage unset.
`RESPONSE_BODY` preserves
the observed status, and exchange logs can retain response headers; lifecycle hooks
and observer events do not expose response-header maps. `DNS_RESOLUTION`, `PROXY_CONNECT`, `CONNECT`, `TLS_HANDSHAKE`, `POOL_ACQUIRE`,
and `REQUEST_WRITE` identify their concrete pre-response boundaries. A missing stage
is unknown, not proof of a specific phase. Redact headers before sharing them.

For `Mono<ResponseEntity<Flux<DataBuffer>>>`, capture the envelope terminal record
and any later inner-stream error separately; the latter does not retroactively turn
the successful envelope record into a failed logical call.

## Failure Attribution Incidents

Minimal safe bundle:

- Outermost exception class and sanitized message plus the ordered cause-class
  chain; omit raw body or credential-bearing cause text.
- `ErrorCategory`, optional `failure.stage`, status when available,
  cancellation state, and final subscription-attempt count from the same
  logical call.
- Final request URL and headers only when already enabled by policy and safely
  sanitized; otherwise record the client and API name.
- Which surface produced each field: lifecycle hook, observer, exchange log,
  health, or configured-client diagnostics.

`ErrorCategory` and `failure.stage` answer different questions. A missing stage
is unknown. Do not infer a response phase from a nested auth timeout, elapsed
duration, an earlier retry's URL, or a generic timeout exception. Exchange logs
may retain response headers; lifecycle and observer records do not expose a
response-header map.

## Streaming Ownership Issues

Minimal safe bundle:

- Method return type: `Flux<DataBuffer>` or `Mono<ResponseEntity<Flux<DataBuffer>>>`.
- Consumer code path that subscribes, forwards, cancels, or releases buffers.
- Whether the stream is proxied to WebFlux, manually consumed, or discarded.
- Metadata-only exchange logs for stream start/error/cancellation.
- Any `DataBufferLimitException`, leak detector, cancellation, or timeout log.

Do not capture streaming payload bytes by default. For
`Mono<ResponseEntity<Flux<DataBuffer>>>`, remember that terminal observer,
lifecycle, and exchange-log records describe response-envelope completion, not
full consumption of the inner body.

## Response Cache Incidents

For response-cache incidents, add a separate bounded cache record before the
general performance evidence:

- Provider-backed diagnostics fields: `cachePhase`, `cachePolicyCount`,
  `cacheTtlMs`, `cacheRefreshAfterMs`, `cacheSingleFlight`,
  `cacheMaximumSize`, `cacheEntryCount`, `cacheEvictions`,
  `cacheMetricsEnabled`, `cachePolicySources`, `cacheHttpMethods`, and
  `cacheSemanticReadAcknowledged`. These are the published `4.1.0` cache
  diagnostics fields. The last three are bounded structural policy facts; they
  never contain request targets or selected values.
- V29 snapshot-only diagnostics fields:
  `cacheMaximumTotalDecodedResponseBytes` and
  `cacheRetainedDecodedResponseBytes`.
  `cacheMaximumTotalDecodedResponseBytes` is the finite sum across selected
  policies only when every selected policy configures the optional limit; `null`
  means the aggregate is unbounded or cannot be represented.
  `cacheRetainedDecodedResponseBytes` is available only from an already-created
  manager whose active policy caches all use that bound; otherwise it is `null`.
- Lookup hit/miss rates and, when applicable, coalesced-waiter and stale-serving
  rates for the affected bounded client/API names.
- Load and refresh success/failure/cancellation rates and durations, plus TTL
  and size eviction rates. V29 weighted policies also include weight eviction and
  admission outcomes.
- Current entries divided by configured maximum entries for each bounded
  client/policy pair.
- One caller terminal record containing the resolved HTTP verb in
  `resolvedHttpMethod`, bounded semantic-read acknowledgement in
  `cacheSemanticReadAcknowledged`, bounded cache outcome in `cacheOutcome`,
  subscription-attempt count in `subscriptionAttemptCount`, request-dispatch
  evidence in `requestDispatched`, and ordinary structural error fields. These
  are structural facts, not request-target, key, body, header, or identity data.

Do not capture cache keys, key digests, cached values, selected arguments,
headers, bodies, URLs, tenant/locale values, or credentials. A stale caller and
its hidden refresh are different facts: the caller reports `STALE_HIT`, while
refresh outcome and duration come from cache meters and the sanitized refresh
debug log. Cache signals are operational context and do not independently make
the downstream health indicator UP or DOWN. Use the PromQL recipes in
[Observability](08-observability.md#cache-hit-ratio-dimensionless).

Use the source-controlled
[aggregate fixture](fixtures/support-bundle-response-cache.json) as the shape
for bounded aggregate cache facts and one sanitized caller terminal record. Keep
the capture-window start, end, and every duration unit explicit. The fixture is
not a dump format: do not add keys, digests, values, arguments, request variants,
header/body content, concrete URLs, identity values, or credentials.

### Cache-memory capture (V29 snapshot only)

Published `4.1.0` incidents use the explicitly enumerated published fields and
ordinary lookup/load/refresh/TTL/size activity above. They do not include the
two V29 snapshot-only decoded-response-byte diagnostics fields, weight eviction,
or admission outcomes. Those signals exist only on the current
`4.2.0-SNAPSHOT`/V29 development line until released; their absence in
`4.1.0` is version scope, not evidence that their value is zero.

Use the source-controlled
[cache-memory fixture](fixtures/support-bundle-cache-memory.json) for one fixed
five-minute window around the symptom. It keeps the following domains separate:

- one bounded client name and one sanitized process-instance ordinal;
- static client cache-metrics selection plus per-policy source, TTL, nullable
  refresh-after/refresh-timeout bounds, entry bound, and optional
  decoded-response-byte bound;
- API-tagged lookup, caller outcome, coalesced, stale, terminal load, and refresh
  aggregates with an explicit API-to-policy mapping;
- policy-tagged TTL/size/weight eviction and weighted-admission aggregates;
- timestamped, phase-labeled post-GC memory checkpoints with per-policy occupancy,
  protocol, total/idle physical connections, and the applicable HTTP/1.1
  connection or HTTP/2 stream gauges; and
- factory start/close, context restart, and bounded deployment-change events.

Record `cacheMetricsEnabled` for the affected client. An enabled idle API uses
real zero-valued API series; a disabled or unavailable integration uses `null`,
not fabricated zeros. For an unweighted policy,
`maximumDecodedResponseBytes`, `retainedDecodedResponseBytes`, weight eviction,
and admissions are `null`; entry gauges and TTL/size evictions remain available
when cache metrics are enabled.

Use one configuration record and multiple checkpoints tied to the same
`processInstance`. Every checkpoint has `capturedAt`, a fixed `phase`, post-GC
memory, and explicit cache/transport availability. A post-close checkpoint uses
an empty policy-state list and a null transport block after their meters are
removed; it does not invent zero gauges.

Every relevant deployment or configuration change records a bounded `type`,
`capturedAt`, and safe before/after version or configuration identifiers so its
position relative to the capture window is explicit. Use an empty
`deploymentChanges` array when no relevant change occurred; never substitute
free-form release notes or configuration values.

The client, API, policy, configuration-source, process-instance, and phase values
may be included only after review confirms they are non-sensitive and bounded.
Keep at most 16 policy records, at most 64 API records, and at most 128 characters
per name. Replace unsafe names with ordinal placeholders. Never add cache keys or
digests, cached values, arguments, headers, bodies, request targets, paths, query
material, identities, credentials, tenant data, or exception messages. Aggregate
counts and fixed structural enums are sufficient for this fixture.

RSS is not Java heap, and decoded-response representation bytes are not response
wire bytes or an object-graph heap measurement. Correlate the fixture with the
[cache-memory decision tree](30-operations-troubleshooting.md#cache-memory-triage-v29-snapshot-only)
rather than adding raw application material.

Heap dumps and JFR recordings can contain payloads, object values, credentials,
identities, internal addresses, and other sensitive application data. Do not put
them in the reviewable support bundle. Capture them only through a separately
approved, encrypted, access-controlled process with explicit retention and
deletion ownership.

## Performance Investigations

Minimal safe bundle:

- Diagnostics snapshot for the affected client.
- Metadata-only exchange logs or metrics for the affected API name, method,
  status, duration, error category, and attempt count.
- Connection-pool metrics for active, idle, total, and pending connections.
- Payload shape and approximate size range, without payload contents.
- Enabled optional features: exchange logging preset, Micrometer, OpenTelemetry,
  retry, circuit breaker, rate limiter, bulkhead, auth signing, proxy, TLS, and
  custom filters.
- Promoted benchmark report link when making a benchmark-based claim.

Current promoted report: [Benchmark Report 2.12.0](benchmark-report-2.12.0.md).
That report is release-quality evidence only for the named local-loopback
scenarios, dependency versions, and environment it records. For production
incidents, use it as orientation and measure the real downstream path directly.
Do not cite smoke-only benchmark reports or generated `target/benchmark-reports`
files as public evidence.

## Related Docs

- [Diagnostic Context Contracts](21-diagnostic-contexts.md)
- [Observability](08-observability.md)
- [Exchange Logging](13-exchange-logging.md)
- [Outbound Auth Providers](06-auth-providers.md)
- [Resilience4j Integration](07-resilience4j.md)
- [Timeouts](04-timeouts.md)
- [Streaming Requests and Responses](11-streaming.md)
- [Benchmarks](22-benchmarks.md)
- [Performance Troubleshooting](25-performance-troubleshooting.md)
- [Operations Troubleshooting](30-operations-troubleshooting.md)
