# Code Review Task List - `reactive-http-client-starter`

> Polished Spring Boot starter for declarative reactive HTTP clients over WebFlux.
> Solid tests (15 classes), coherent observability, gracefully optional Resilience4j.
> Below are actionable tasks grouped by severity.

---

## Critical - Security & Data Leakage

- [x] **C1. Redact sensitive headers and bodies in `DefaultHttpExchangeLogger`**
  - **Where:** `DefaultHttpExchangeLogger.java:20-29`
  - **Issue:** Logs request/response headers AND bodies at INFO. Headers contain `Authorization: Bearer ?`, `Cookie`, `X-Api-Key`; bodies typically contain PII.
  - **Fix:** Redact known sensitive headers (`Authorization`, `Cookie`, `Set-Cookie`, `Proxy-Authorization`, `X-Api-Key`) by default. Log bodies only when explicitly opted in; consider DEBUG level for bodies.

- [x] **C2. Stop embedding response body in exception messages**
  - **Where:** `HttpClientException.java:27`, `RemoteServiceException.java:22`
  - **Issue:** `super("HTTP client error " + statusCode + ": " + responseBody)` ? bodies leak into logs, Sentry, metric exception tags.
  - **Fix:** Short fixed message (`"HTTP client error 400"`); expose body only via `getResponseBody()`. Optionally truncate stored body to ~4KB.

- [x] **C3. Cap error-body size in `DefaultErrorDecoder`**
  - **Where:** `DefaultErrorDecoder.java:23`
  - **Issue:** `bodyToMono(String.class)` has no size limit; a gateway 100MB HTML page is buffered entirely, or surfaces unrelated `DataBufferLimitException`.
  - **Fix:** Truncate body to a sane cap (e.g. 4KB) before constructing the exception.

- [x] **C4. Validate CRLF in `@HeaderParam` values**
  - **Where:** `RequestArgumentResolver.java:44`, `OutboundAuthFilter.java:70`
  - **Issue:** Header values flow straight into `WebClient.header()`. Spring sanitizes at Netty layer but throws generic errors.
  - **Fix:** Validate early; reject anything containing `\r`, `\n`, or control characters with a descriptive domain error.

---

## High - Correctness Bugs

- [x] **H1. Fix URL encoding of auth-provider query params**
  - **Where:** `OutboundAuthFilter.java:79-88`
  - **Issue:** `uriBuilder.build(true).toUri()` marks raw values as pre-encoded. Values with `&`, `=`, `?`, `+`, `/`, space break URLs or corrupt HMAC signatures.
  - **Fix:** Use `.encode().build().toUri()` with raw values, or URL-encode explicitly before `queryParam()`.

- [x] **H2. Reject non-reactive return types at metadata-parse time**
  - **Where:** `MethodMetadataCache.java:71-80`
  - **Issue:** A method declaring plain `T` (not `Mono<T>`/`Flux<T>`) silently returns a `Mono` to the caller, causing `ClassCastException`.
  - **Fix:**
    ```java
    if (!meta.isReturnsMono() && !meta.isReturnsFlux()) {
        throw new IllegalStateException(
            "Method " + method + " must return Mono<T> or Flux<T>");
    }
    ```

- [x] **H3. Map Netty `ReadTimeoutException` to `ErrorCategory.TIMEOUT`**
  - **Where:** `ReactiveClientInvocationHandler.java:618`
  - **Issue:** Only catches `java.util.concurrent.TimeoutException`. Reactor Netty's `responseTimeout` throws `io.netty.handler.timeout.ReadTimeoutException` (not a subclass), falls through to `UNKNOWN`.
  - **Fix:** Catch both exception types, or consolidate to a single timeout mechanism.

- [x] **H4. Support default methods on `@ReactiveHttpClient` interfaces**
  - **Where:** `ReactiveClientInvocationHandler.java:122-136`
  - **Issue:** Only `Object` methods get special handling. A `default` helper method throws `UnsupportedOperationException` because it has no HTTP verb annotation.
  - **Fix:** Detect `method.isDefault()` and invoke via `InvocationHandler.invokeDefault(proxy, method, args)` (Java 16+).

- [x] **H5. Fix misleading `http.status_code` tag for network errors**
  - **Where:** `MicrometerHttpClientObserver.java:101-103`
  - **Issue:** DNS failures / timeouts / connection-refused get tagged `http.status_code="CLIENT_ERROR"`, reading as a 4xx in dashboards.
  - **Fix:** Use `"NONE"`/`"N/A"` when no response was received; rely on `error.category` for classification.

- [x] **H6. Add `@PATCH` annotation and verb support**
  - **Where:** `MethodMetadataCache.java:31-43`, new `annotation/PATCH.java`
  - **Issue:** Only GET/POST/PUT/DELETE supported. PATCH is standard for partial updates.
  - **Fix:** Add `@PATCH` annotation and extend the parser's if/else chain.

- [x] **H7. Fix `invalidate()` race in `RefreshingBearerAuthProvider`**
  - **Where:** `RefreshingBearerAuthProvider.java:79-87`, `:128-132`
  - **Issue:** An in-flight refresh started before `invalidate()` can still commit `cachedToken` via `doOnNext` afterwards, re-populating the cache right after a 401 retry.
  - **Fix:** Introduce a monotonically increasing `invalidationEpoch`. Capture the epoch at refresh start; commit in `doOnNext` only if `epoch == currentEpoch`; bump epoch in `invalidate()`.

- [x] **H8. Reject non-blank `@HeaderParam` value on Map parameters**
  - **Where:** `MethodMetadataCache.java:54-63`
  - **Issue:** `@HeaderParam("X-Trace") Map<String,String> extra` silently ignores the value, surprising users.
  - **Fix:** Throw on non-blank value for Map params, or document behavior consistently with the non-Map branch.

---

## Medium - Design Problems

- [x] **M1. Freeze `MethodMetadata` collections after parsing**
  - **Where:** `MethodMetadata.java:54-57`
  - **Issue:** Returns direct references to private `HashMap`/`HashSet`. Callers could mutate cached entries.
  - **Fix:** At the end of `MethodMetadataCache.parse()` wrap with `Map.copyOf(...)` / `Set.copyOf(...)` or `Collections.unmodifiableMap(...)`.

- [ ] **M2. Move body serialization off the subscriber thread**
  - **Where:** `ReactiveClientInvocationHandler.java:159`
  - **Issue:** `objectMapper.writeValueAsBytes(body)` runs synchronously inside `invoke()`; blocks event loop for large bodies.
  - **Fix:** Wrap in `Mono.fromCallable(...).subscribeOn(Schedulers.boundedElastic())`. Skip serialization entirely when no auth provider is configured.

- [ ] **M3. Start duration timer at subscribe, not at proxy-invoke**
  - **Where:** `ReactiveClientInvocationHandler.java:140`
  - **Issue:** `System.currentTimeMillis()` captured at invoke-time; deferred subscription inflates observed duration.
  - **Fix:** Wrap pipeline in `Mono.defer(() -> { long start = ...; ... })`.

- [ ] **M4. Consolidate duplicated timeout resolution logic**
  - **Where:** `ReactiveClientInvocationHandler.java:336-370`
  - **Issue:** `resolveTimeoutMs` and `shouldOverrideRequestLevelResponseTimeout` duplicate the same check. Two timeout layers also surface different exception types (see H3).
  - **Fix:** Merge helpers; pick one timeout mechanism as source of truth.

- [ ] **M5. Fix `loggingFilter` javadoc / config-name mismatch**
  - **Where:** `ReactiveHttpClientFactoryBean.java:211-216`
  - **Issue:** Doc says "Logs method, URL, status and latency" but only logs status. `ClientConfig.logBody` triggers it though no body is logged.
  - **Fix:** Implement the documented contract or correct the doc and rename the flag.

- [ ] **M6. Remove or clearly scope `UriTemplateExpander`**
  - **Where:** `UriTemplateExpander.java`
  - **Issue:** Not used in the main invocation path; likely dead code or a utility.
  - **Fix:** Delete if dead, or document as public API and add load-bearing tests.

- [x] **M7. Prevent duplicate `X-Correlation-Id` on outbound request**
  - **Where:** `CorrelationIdWebFilter.java:59-61`
  - **Issue:** `ClientRequest.Builder.header(...)` appends rather than replaces; pre-set headers result in duplicates.
  - **Fix:** Check-and-skip if present, or use `.headers(h -> h.set(...))` to overwrite deterministically.

- [x] **M8. Use conventional MDC keys for correlation ID fallback**
  - **Where:** `CorrelationIdWebFilter.java:28`, `:56`
  - **Issue:** MDC key `"correlationId"` rarely matches what tracing frameworks set (`traceId`, `X-Correlation-Id`).
  - **Fix:** Try multiple conventional keys in MDC, or document explicitly.

- [x] **M9. Use distinct exception for JSON serialization failures**
  - **Where:** `ReactiveClientInvocationHandler.java:472-478`
  - **Issue:** Wrapping `JsonProcessingException` as `AuthProviderException` misclassifies developer errors as auth failures in `error.category`.
  - **Fix:** Throw `IllegalArgumentException` or a dedicated `RequestSerializationException`.

- [ ] **M10. Bound `loggerCache` growth**
  - **Where:** `ReactiveClientInvocationHandler.java:73`
  - **Issue:** Unbounded `ConcurrentHashMap` keyed by logger Class; dynamic/generated classes could leak.
  - **Fix:** Add a size bound/eviction, or document intended lifecycle.

---

## Low - Style & Minor Issues

- [x] **L1.** `getObserver()` reduces to `observerProvider.getIfAvailable()` ? remove redundant null check. *(`ReactiveClientInvocationHandler.java:117`)*
- [x] **L2.** `resilienceWarningKeys` declared as `java.util.Set<String>` despite `Set` already imported. *(`ReactiveClientInvocationHandler.java:74`)*
- [x] **L3.** Replace Stream-based `getHeaderIgnoreCase` with a simple loop (called twice per request). *(`ReactiveClientInvocationHandler.java:443-449`)*
- [x] **L4.** `Class.forName` uses default classloader; prefer `ClassUtils.resolveClassName(name, null)` for container safety. *(`ReactiveHttpClientsRegistrar.java:59`)*
- [x] **L5.** Document on `HttpClientObserverEvent.getResponseBody()` that Flux responses always pass `null`.
- [x] **L6.** Deprecate the older `HttpClientObserverEvent` constructor that leaves `errorCategory` null.
- [ ] **L7.** `@ReactiveHttpClient` meta-annotated with `@Component` but registered via registrar ? verify skip-if-present guard handles both orderings.
- [x] **L8.** `MicrometerHttpClientObserver.buildTags`: default `clientName` to `"UNKNOWN"` on null, matching `apiName`/`httpMethod`.
- [x] **L9.** `AuthProviderException`: add a message-overload constructor for richer diagnostics.
- [ ] **L10.** Enrich `DefaultErrorDecoder` exceptions with request URL/method for debugging.

---

## Test Coverage Gaps

- [x] **T1.** `@PATCH` verb (after H6)
- [x] **T2.** `@Body` with `byte[]`, `String`, and custom content types
- [x] **T3.** Default methods on `@ReactiveHttpClient` interfaces (H4)
- [x] **T4.** Non-Mono/Flux return types must throw at parse time (H2)
- [x] **T5.** Concurrent `RefreshingBearerAuthProvider.invalidate()` vs in-flight refresh (H7)
- [x] **T6.** URL encoding of auth-provider query params (H1)
- [x] **T7.** Netty `ReadTimeoutException` classification ? `TIMEOUT` (H3)

---

## Priority Order

Biggest risk reduction by addressing in this order:

1. **C1 + C2** redact headers / stop embedding bodies in exceptions
2. **H1** URL-encoding bug can produce silently wrong HMAC signatures
3. **H2 + H4** hard-to-debug silent failures for common user mistakes
4. **H6** `PATCH` support
5. **H7** auth invalidation race
6. **M1 + M2 + M3** thread safety of cached metadata and correctness of observability timers
