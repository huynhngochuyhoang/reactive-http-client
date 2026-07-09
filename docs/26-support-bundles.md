# Production Support Bundles

Use this page when opening an internal support ticket, handing an incident to an
on-call teammate, or asking maintainers to help diagnose starter behavior. The
goal is a small, support-safe bundle that explains the client configuration and
runtime symptoms without collecting raw request bodies, response bodies, tokens,
secrets, or customer data by default.

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
  diagnostics/rhttpclients.json
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

Provider-backed snapshots include project version, total client count, total endpoint count,
total inherited endpoint count, per-client policy summaries, and strict validation
flags. Summary-only collection snapshots mark strict validation flags as unknown.
They do not include concrete base URLs, header values, proxy credentials, auth-provider bean
names, request bodies, or response bodies.

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
| Diagnostics endpoint | `diagnostics/rhttpclients.json` | Which clients and endpoints exist, plus sanitized effective policy such as timeout source, auth mode, resilience, redirect policy, inherited endpoint counts, and strict-validation flags. |
| Health details | `health/health.json` | Whether recent Micrometer samples crossed the configured per-client error-rate threshold. |
| Startup summary | `logs/startup-summary.log` | Which sanitized client policy was applied when the proxy was created. |
| Metadata-only exchange logs | `logs/exchange-metadata.log` | What happened for the affected calls: method, path template, status, duration, error, and subscription-attempt count. |
| Sanitized configuration | `config/reactive-http-client.yml` | Which `reactive.http.*` settings the application intended to use, without secrets or concrete internal URLs. |
| Release evidence reference | `performance/benchmark-report-link.txt` | Which promoted source-controlled report supports a benchmark-based claim, when the ticket includes one. |

The diagnostics endpoint, health details, startup summaries, exchange logs,
configuration snippets, and release-evidence reference answer different
questions. Do not merge them into a single free-form log dump.

### Local JVM Capture

Use this when the application process and its management endpoint are reachable
from the same shell:

```bash
EXAMPLE_MANAGEMENT_URL="http://<management-host>:<management-port>"
EXAMPLE_APP_LOG="/path/to/sanitized-application.log"
EXAMPLE_SANITIZED_CONFIG="/path/to/sanitized-reactive-http-client.yml"

mkdir -p support-bundle/diagnostics support-bundle/health support-bundle/logs support-bundle/config support-bundle/performance
curl -fsS "$EXAMPLE_MANAGEMENT_URL/actuator/rhttpclients" -o support-bundle/diagnostics/rhttpclients.json
curl -fsS "$EXAMPLE_MANAGEMENT_URL/actuator/health" -o support-bundle/health/health.json
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
EXAMPLE_SANITIZED_CONFIG_IN_CONTAINER="/path/in/container/sanitized-reactive-http-client.yml"

mkdir -p support-bundle/diagnostics support-bundle/health support-bundle/logs support-bundle/config support-bundle/performance
curl -fsS "$EXAMPLE_MANAGEMENT_URL/actuator/rhttpclients" -o support-bundle/diagnostics/rhttpclients.json
curl -fsS "$EXAMPLE_MANAGEMENT_URL/actuator/health" -o support-bundle/health/health.json
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
EXAMPLE_SANITIZED_CONFIG_IN_POD="/path/in/pod/sanitized-reactive-http-client.yml"

kubectl -n "$EXAMPLE_NAMESPACE" port-forward "pod/$EXAMPLE_POD" "$EXAMPLE_LOCAL_PORT:8080"
```

```bash
EXAMPLE_MANAGEMENT_URL="http://127.0.0.1:$EXAMPLE_LOCAL_PORT"
mkdir -p support-bundle/diagnostics support-bundle/health support-bundle/logs support-bundle/config support-bundle/performance
curl -fsS "$EXAMPLE_MANAGEMENT_URL/actuator/rhttpclients" -o support-bundle/diagnostics/rhttpclients.json
curl -fsS "$EXAMPLE_MANAGEMENT_URL/actuator/health" -o support-bundle/health/health.json
kubectl -n "$EXAMPLE_NAMESPACE" logs "$EXAMPLE_POD" -c "$EXAMPLE_CONTAINER" --since=30m | grep 'ReactiveHttpClientFactoryBean' > support-bundle/logs/startup-summary.log || true
kubectl -n "$EXAMPLE_NAMESPACE" logs "$EXAMPLE_POD" -c "$EXAMPLE_CONTAINER" --since=30m | grep 'DefaultHttpExchangeLogger' > support-bundle/logs/exchange-metadata.log || true
kubectl -n "$EXAMPLE_NAMESPACE" cp "$EXAMPLE_POD:$EXAMPLE_SANITIZED_CONFIG_IN_POD" support-bundle/config/reactive-http-client.yml -c "$EXAMPLE_CONTAINER"
printf 'docs/benchmark-report-<version>.md\n' > support-bundle/performance/benchmark-report-link.txt
```

Keep namespace, pod, container, file path, and management URL values as
placeholders in shared examples. Before attaching a bundle, inspect every file
for concrete hosts, credentials, cookies, authorization headers, request bodies,
response bodies, and customer data.

## Health Details

For error-rate incidents, include the affected client entry from
`/actuator/health`. A useful detail block contains `samples`, `errors`,
`minSamples`, `errorRateThreshold`, `errorRate`, `status`, and `reason`.

```json
{
  "status": "DOWN",
  "details": {
    "user-service": {
      "samples": 10,
      "errors": 8,
      "minSamples": 10,
      "errorRateThreshold": 0.5,
      "errorRate": 0.8,
      "status": "DOWN",
      "reason": "ERROR_RATE_ABOVE_THRESHOLD"
    }
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
- Token endpoint HTTP status and sanitized response-body snippet when present.
- Metadata-only exchange logs for the downstream API call that failed after auth.

Do not include raw token endpoint request bodies, raw token responses, bearer
tokens, Basic credentials, refresh tokens, client secrets, cookies, or signed
Authorization headers. For AWS SigV4, include the body shape (`byte[]`, `String`,
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

## Timeout Incidents

Minimal safe bundle:

- Diagnostics snapshot showing timeout source and value for the affected client.
- The method annotation or API-map entry when it overrides client timeout.
- Network timeout settings: connect, read safety net, and write safety net.
- Metadata-only exchange log or observer event with duration, status when
  available, exception type, and subscription-attempt count.
- Health details for the affected client and the same time window.

For timeout after response headers, exchange logs can retain response headers;
lifecycle hooks and observer events do not expose response-header maps. Redact
headers before sharing them.

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
- [Streaming Responses](11-streaming.md)
- [Benchmarks](22-benchmarks.md)
- [Performance Troubleshooting](25-performance-troubleshooting.md)
