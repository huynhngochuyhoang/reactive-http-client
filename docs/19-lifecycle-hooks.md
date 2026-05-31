# Lifecycle Hooks

`ReactiveHttpClientLifecycleHook` is a lightweight SPI for application-side
callbacks around a generated client invocation. Register one or more Spring beans;
the starter discovers them in `@Order` / `Ordered` sequence.

```java
@Component
@Order(10)
public class AuditLifecycleHook implements ReactiveHttpClientLifecycleHook {

    @Override
    public boolean supports(String clientName) {
        return "payment-service".equals(clientName);
    }

    @Override
    public void onStart(ReactiveHttpClientLifecycleContext context) {
        audit.start(context.clientName(), context.apiName(), context.attemptNumber());
    }

    @Override
    public void onSuccess(ReactiveHttpClientLifecycleContext context) {
        audit.success(context.statusCode());
    }

    @Override
    public void onError(ReactiveHttpClientLifecycleContext context) {
        audit.error(context.statusCode(), context.error());
    }
}
```

## Callback Boundaries

| Callback | When |
|---|---|
| `onStart` | First subscription attempt starts |
| `onRetryAttempt` | A later retry subscription starts |
| `onSuccess` | Final response completes successfully |
| `onError` | Final invocation fails |
| `onCancel` | Subscriber cancels before completion |

Retries create multiple HTTP attempts inside one logical client call. `onStart`
and `onRetryAttempt` are per-attempt callbacks: `onStart` runs for attempt `1`,
and `onRetryAttempt` runs once for each later attempt. `onSuccess`, `onError`,
and `onCancel` are logical-call terminal callbacks and run at most once. A
cancelled retried call emits `onCancel`, not both `onCancel` and `onError`.

`HttpClientObserver` is also logical-call scoped. It records one terminal event
after retries finish, with `HttpClientObserverEvent.getAttemptCount()` set to the
total number of attempts. Exchange logging follows the same logical-call
boundary.

Hook failures are isolated. If a hook throws from `supports(...)` or a callback,
the starter logs a warning and continues the client call and the remaining hooks.

## Context

`ReactiveHttpClientLifecycleContext` exposes the logical client name, API name,
HTTP method, path template, resolved path/query/header values, request body,
resolved request URL when available, response status when available, terminal
error when available, and attempt number.

Context values are read-only snapshots. Use filters, auth providers, or a
`ReactiveHttpClientCustomizer` when you need to mutate a request.

## Hooks vs Customizers vs Observers

| Extension point | Use for | Can mutate request? | Failure handling |
|---|---|---:|---|
| `ReactiveHttpClientCustomizer` | WebClient filters, codecs, request mutation | Yes | Filter behavior controls the call |
| `ReactiveHttpClientLifecycleHook` | Audit, tenant call records, lifecycle side effects | No | Failures are logged and ignored |
| `HttpClientObserver` | Metrics and tracing backends | No | Failures are logged and ignored |

Use lifecycle hooks for application workflow callbacks. Use observers for
telemetry. Use customizers only when the HTTP exchange itself must change.
