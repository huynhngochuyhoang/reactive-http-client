package io.github.huynhngochuyhoang.httpstarter.nativesmoke;

import io.github.huynhngochuyhoang.httpstarter.auth.AuthContext;
import io.github.huynhngochuyhoang.httpstarter.auth.AuthProvider;
import io.github.huynhngochuyhoang.httpstarter.core.ProblemDetailErrorResponseMapper;
import io.github.huynhngochuyhoang.httpstarter.observability.ReactiveHttpClientDiagnosticsEndpoint;
import io.github.huynhngochuyhoang.httpstarter.core.ReactiveHttpClientDiagnosticsProvider;
import io.github.huynhngochuyhoang.httpstarter.core.ReactiveHttpClientDiagnosticsSnapshot;
import io.github.huynhngochuyhoang.httpstarter.core.ReactiveHttpClientJsonCodec;
import io.github.huynhngochuyhoang.httpstarter.enable.EnableReactiveHttpClients;
import io.github.huynhngochuyhoang.httpstarter.exception.ProblemDetailRemoteServiceException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.GZIPOutputStream;

@SpringBootApplication
@EnableReactiveHttpClients(basePackageClasses = NativeSmokeClient.class)
@RegisterReflectionForBinding(NativeOrderResponse.class)
public class NativeSmokeApplication {

    private static final String AUTH_TOKEN = "native-secret-token";

    public static void main(String[] args) {
        AtomicReference<String> observedAuth = new AtomicReference<>();
        AtomicReference<String> observedAcceptEncoding = new AtomicReference<>();
        AtomicInteger dispatchCount = new AtomicInteger();
        AtomicInteger cachedDispatchCount = new AtomicInteger();
        AtomicInteger retryDispatchCount = new AtomicInteger();
        CountDownLatch openCircuitDispatch = new CountDownLatch(1);
        CountDownLatch refreshDispatch = new CountDownLatch(1);
        DisposableServer server = loopbackServer(
                observedAuth, observedAcceptEncoding, dispatchCount, cachedDispatchCount,
                retryDispatchCount, openCircuitDispatch, refreshDispatch);
        SpringApplication application = new SpringApplication(NativeSmokeApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setDefaultProperties(Map.of(
                "reactive.http.clients.native-smoke.base-url",
                "http://127.0.0.1:" + server.port()));
        try (ConfigurableApplicationContext context = application.run(args)) {
            NativeSmokeClient client = context.getBean(NativeSmokeClient.class);
            NativeOrderResponse order = client.getOrder().block(Duration.ofSeconds(5));
            require(order != null && "ok".equals(order.code()) && "native".equals(order.message()),
                    "inherited generic response decoding failed");
            require(AUTH_TOKEN.equals(observedAuth.get()), "auth provider header did not reach loopback server");

            NativeOrderResponse compressedOrder = client.getCompressedOrder().block(Duration.ofSeconds(5));
            require(compressedOrder != null && "ok".equals(compressedOrder.code())
                            && "compressed".equals(compressedOrder.message()),
                    "gzip response decoding failed");
            require(observedAcceptEncoding.get() != null && observedAcceptEncoding.get().contains("gzip"),
                    "compression negotiation header did not reach loopback server");

            try {
                client.getProblem().block(Duration.ofSeconds(5));
                throw new IllegalStateException("Problem Detail response did not fail");
            } catch (ProblemDetailRemoteServiceException error) {
                require(error.getStatusCode() == 502, "Problem Detail status was not preserved");
                require("native problem".equals(error.getProblemDetail().getTitle()),
                        "Problem Detail payload was not decoded");
            }

            int dispatchedBeforeOpenCircuit = dispatchCount.get();
            try {
                client.getOpenCircuit().block(Duration.ofSeconds(5));
                throw new IllegalStateException("Open circuit did not reject the native call");
            } catch (CallNotPermittedException expected) {
                try {
                    require(!openCircuitDispatch.await(1, TimeUnit.SECONDS),
                            "Open-circuit rejection unexpectedly reached the loopback server");
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while observing open-circuit dispatches", error);
                }
                require(dispatchCount.get() == dispatchedBeforeOpenCircuit,
                        "Open-circuit rejection unexpectedly reached the loopback server");
            }

            NativeOrderResponse firstCached = client.getCachedOrder().block(Duration.ofSeconds(5));
            NativeOrderResponse cachedHit = client.getCachedOrder().block(Duration.ofSeconds(5));
            require(firstCached != null && "cached-1".equals(firstCached.message())
                            && firstCached.equals(cachedHit),
                    "native cache miss/hit contract failed");
            require(cachedDispatchCount.get() == 1,
                    "native cache hit unexpectedly dispatched to the loopback server");
            sleep(Duration.ofMillis(120));
            NativeOrderResponse stale = client.getCachedOrder().block(Duration.ofSeconds(5));
            require(firstCached.equals(stale), "refresh-on-access did not return the stale value");
            await(refreshDispatch, "native cache refresh did not dispatch");
            NativeOrderResponse refreshed = awaitCachedValue(client, "cached-2");
            require("cached-2".equals(refreshed.message()) && cachedDispatchCount.get() == 2,
                    "native cache refresh did not replace the cached value exactly once");

            NativeOrderResponse retried = client.getRetryOnly().block(Duration.ofSeconds(5));
            require(retried != null && "retry-2".equals(retried.message())
                            && retryDispatchCount.get() == 2,
                    "explicit retry-only activation did not produce exactly two dispatches");

            require(context.containsBean("reactiveHttpClientDiagnosticsEndpoint"),
                    "opt-in diagnostics endpoint was not registered");
            require(context.containsBean("reactiveHttpClientHealthIndicator"),
                    "Micrometer health indicator was not registered");
            ReactiveHttpClientDiagnosticsProvider diagnosticsProvider =
                    context.getBean(ReactiveHttpClientDiagnosticsProvider.class);
            Map<String, Object> diagnostics = context.getBean(ReactiveHttpClientDiagnosticsEndpoint.class).diagnostics();
            Map<String, Object> directSnapshot = ReactiveHttpClientDiagnosticsSnapshot.toMap(diagnosticsProvider);
            Map<String, Object> collectionSnapshot =
                    ReactiveHttpClientDiagnosticsSnapshot.toMap(diagnosticsProvider.clientSummaries());
            String snapshot = ReactiveHttpClientDiagnosticsSnapshot.toJson(diagnosticsProvider);
            String markdown = ReactiveHttpClientDiagnosticsSnapshot.toMarkdown(diagnosticsProvider.clientSummaries());
            Set<String> rootFields = Set.of(
                    "schemaVersion", "projectVersion", "clientCount", "endpointCount",
                    "inheritedEndpointCount", "clients");
            Set<String> clientFields = Set.of(
                    "clientName", "clientInterface", "baseUrlSource", "poolSource",
                    "poolMaxConnections", "poolPendingAcquireTimeoutMs", "poolMetricsEnabled",
                    "poolProtocol", "poolCapacityBasis", "poolMaxConcurrentStreams",
                    "cachePhase", "cachePolicyCount", "cacheTtlMs", "cacheRefreshAfterMs",
                    "cacheSingleFlight", "cacheMaximumSize", "cacheEntryCount",
                    "cacheEvictions", "cacheMetricsEnabled",
                    "timeoutSource", "timeoutMs", "logicalCallTimeoutMs", "compressionEnabled",
                    "codecMaxInMemorySizeMb", "resilienceConfigured", "retry", "rateLimiter",
                    "circuitBreaker", "bulkhead", "strictUnsafeRetryValidation",
                    "strictBodySigningValidation", "authMode", "followRedirects", "endpointCount",
                    "inheritedEndpointCount");

            require(diagnostics.equals(directSnapshot),
                    "Actuator and direct diagnostics snapshots diverged");
            require(diagnostics.keySet().equals(rootFields) && collectionSnapshot.keySet().equals(rootFields),
                    "diagnostics root schema fields changed");
            require(diagnostics.get("schemaVersion") instanceof Integer version && version == 1,
                    "diagnostics schema version was not preserved");
            require(diagnostics.get("projectVersion") instanceof String,
                    "diagnostics project version type changed");
            require(diagnostics.get("clientCount") instanceof Integer count && count == 1,
                    "diagnostics did not include the native client");
            require(diagnostics.get("endpointCount") instanceof Integer
                            && diagnostics.get("inheritedEndpointCount") instanceof Integer,
                    "diagnostics aggregate count types changed");
            require(diagnostics.get("clients") instanceof List<?> clients && clients.size() == 1
                            && clients.get(0) instanceof Map<?, ?>,
                    "diagnostics client collection shape changed");
            Map<?, ?> providerClient = (Map<?, ?>) ((List<?>) diagnostics.get("clients")).get(0);
            Map<?, ?> collectionClient = (Map<?, ?>) ((List<?>) collectionSnapshot.get("clients")).get(0);
            require(providerClient.keySet().equals(clientFields) && collectionClient.keySet().equals(clientFields),
                    "diagnostics client schema fields changed");
            require(providerClient.get("poolMaxConnections") instanceof Integer
                            && providerClient.get("poolPendingAcquireTimeoutMs") instanceof Long
                            && providerClient.get("poolMetricsEnabled") instanceof Boolean
                            && providerClient.get("poolProtocol") instanceof String
                            && providerClient.get("poolCapacityBasis") instanceof String
                            && providerClient.get("poolMaxConcurrentStreams") == null
                            && providerClient.get("cachePolicyCount") instanceof Integer
                            && providerClient.get("cacheTtlMs") instanceof Long
                            && providerClient.get("cacheRefreshAfterMs") instanceof Long
                            && providerClient.get("cacheMaximumSize") instanceof Long
                            && providerClient.get("cacheEntryCount") instanceof Long
                            && providerClient.get("cacheEvictions") instanceof Long
                            && providerClient.get("cacheMetricsEnabled") instanceof Boolean
                            && providerClient.get("timeoutMs") instanceof Long
                            && providerClient.get("logicalCallTimeoutMs") instanceof Long
                            && providerClient.get("compressionEnabled") instanceof Boolean
                            && providerClient.get("codecMaxInMemorySizeMb") instanceof Integer
                            && providerClient.get("followRedirects") instanceof Boolean,
                    "provider diagnostics field types changed");
            require("unknown".equals(collectionClient.get("poolSource"))
                            && collectionClient.get("poolMaxConnections") == null
                            && collectionClient.get("poolPendingAcquireTimeoutMs") == null
                            && collectionClient.get("poolMetricsEnabled") == null
                            && "unknown".equals(collectionClient.get("poolProtocol"))
                            && "unknown".equals(collectionClient.get("poolCapacityBasis"))
                            && collectionClient.get("poolMaxConcurrentStreams") == null
                            && "unknown".equals(collectionClient.get("cachePhase"))
                            && collectionClient.get("cachePolicyCount") == null
                            && collectionClient.get("cacheTtlMs") == null
                            && collectionClient.get("cacheRefreshAfterMs") == null
                            && "unknown".equals(collectionClient.get("cacheSingleFlight"))
                            && collectionClient.get("cacheMaximumSize") == null
                            && collectionClient.get("cacheEntryCount") == null
                            && collectionClient.get("cacheEvictions") == null
                            && collectionClient.get("cacheMetricsEnabled") == null
                            && collectionClient.get("logicalCallTimeoutMs") == null
                            && collectionClient.get("compressionEnabled") == null
                            && collectionClient.get("codecMaxInMemorySizeMb") == null
                            && collectionClient.get("strictUnsafeRetryValidation") == null
                            && collectionClient.get("strictBodySigningValidation") == null,
                    "collection diagnostics unknown states changed");
            require(markdown.contains("| Schema version | `1` |")
                            && markdown.contains("| `unknown` | `unknown` |"),
                    "Markdown diagnostics schema semantics changed");
            String supportOutput = snapshot + markdown + diagnostics;
            require(!supportOutput.contains(AUTH_TOKEN) && !supportOutput.contains("127.0.0.1")
                            && !supportOutput.contains("Authorization")
                            && !supportOutput.contains("requestBody")
                            && !supportOutput.contains("responseBody"),
                    "diagnostics snapshot exposed sensitive transport data");

            MeterRegistry meterRegistry = context.getBean(MeterRegistry.class);
            require(meterRegistry.find("reactive.http.client.requests").timer() != null,
                    "Micrometer observer did not record the native calls");
            Timer rejectedTimer = meterRegistry.find("reactive.http.client.requests")
                    .tag("api.name", "getOpenCircuit")
                    .tag("exception", "CallNotPermittedException")
                    .timer();
            require(rejectedTimer != null && rejectedTimer.count() == 1
                            && rejectedTimer.max(TimeUnit.SECONDS) >= 0
                            && rejectedTimer.max(TimeUnit.SECONDS) < 5,
                    "Open-circuit terminal duration was missing or invalid");
            DistributionSummary rejectedAttempts = meterRegistry.find("reactive.http.client.requests.attempts")
                    .tag("api.name", "getOpenCircuit")
                    .summary();
            require(rejectedAttempts != null && rejectedAttempts.count() == 1
                            && rejectedAttempts.totalAmount() == 0,
                    "Open-circuit terminal did not record zero subscription attempts");
            require(dispatchCount.get() == 7,
                    "native smoke observed an unexpected total dispatch count: " + dispatchCount.get());
        } finally {
            server.disposeNow(Duration.ofSeconds(5));
        }
    }

    @Bean
    AuthProvider nativeAuthProvider() {
        return request -> Mono.just(AuthContext.builder().header("X-Native-Auth", AUTH_TOKEN).build());
    }

    @Bean
    ProblemDetailErrorResponseMapper problemDetailErrorResponseMapper(ReactiveHttpClientJsonCodec codec) {
        return new ProblemDetailErrorResponseMapper(codec);
    }

    @Bean
    CircuitBreakerRegistry circuitBreakerRegistry() {
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.ofDefaults();
        registry.circuitBreaker("default");
        registry.circuitBreaker("native-open").transitionToOpenState();
        return registry;
    }

    @Bean
    RetryRegistry retryRegistry() {
        RetryRegistry registry = RetryRegistry.of(RetryConfig.custom()
                .maxAttempts(2)
                .waitDuration(Duration.ZERO)
                .build());
        registry.retry("native-retry");
        return registry;
    }

    private static DisposableServer loopbackServer(
            AtomicReference<String> observedAuth,
            AtomicReference<String> observedAcceptEncoding,
            AtomicInteger dispatchCount,
            AtomicInteger cachedDispatchCount,
            AtomicInteger retryDispatchCount,
            CountDownLatch openCircuitDispatch,
            CountDownLatch refreshDispatch) {
        return HttpServer.create()
                .port(0)
                .route(routes -> routes
                        .get("/api/order", (request, response) -> {
                            dispatchCount.incrementAndGet();
                            observedAuth.set(request.requestHeaders().get("X-Native-Auth"));
                            return response.header("Content-Type", "application/json")
                                    .sendString(Mono.just("{\"code\":\"ok\",\"message\":\"native\"}"))
                                    .then();
                        })
                        .get("/api/compressed-order", (request, response) -> {
                            dispatchCount.incrementAndGet();
                            observedAcceptEncoding.set(request.requestHeaders().get("Accept-Encoding"));
                            return response.header("Content-Type", "application/json")
                                    .header("Content-Encoding", "gzip")
                                    .sendByteArray(Mono.just(gzip(
                                            "{\"code\":\"ok\",\"message\":\"compressed\"}")))
                                    .then();
                        })
                        .get("/api/problem", (request, response) -> {
                            dispatchCount.incrementAndGet();
                            return response.status(502)
                                    .header("Content-Type", "application/problem+json")
                                    .sendString(Mono.just("{\"status\":502,\"title\":\"native problem\",\"detail\":\"smoke\"}"))
                                    .then();
                        })
                        .get("/api/open-circuit", (request, response) -> {
                            dispatchCount.incrementAndGet();
                            openCircuitDispatch.countDown();
                            return response.header("Content-Type", "application/json")
                                    .sendString(Mono.just("{\"code\":\"unexpected\",\"message\":\"dispatched\"}"))
                                    .then();
                        })
                        .get("/api/cached-order", (request, response) -> {
                            dispatchCount.incrementAndGet();
                            int dispatch = cachedDispatchCount.incrementAndGet();
                            if (dispatch == 2) {
                                refreshDispatch.countDown();
                            }
                            return response.header("Content-Type", "application/json")
                                    .sendString(Mono.just("{\"code\":\"ok\",\"message\":\"cached-"
                                            + dispatch + "\"}"))
                                    .then();
                        })
                        .get("/api/retry-only", (request, response) -> {
                            dispatchCount.incrementAndGet();
                            int dispatch = retryDispatchCount.incrementAndGet();
                            if (dispatch == 1) {
                                return response.status(503).send();
                            }
                            return response.header("Content-Type", "application/json")
                                    .sendString(Mono.just("{\"code\":\"ok\",\"message\":\"retry-"
                                            + dispatch + "\"}"))
                                    .then();
                        }))
                .bindNow(Duration.ofSeconds(5));
    }

    private static NativeOrderResponse awaitCachedValue(NativeSmokeClient client, String message) {
        long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
        NativeOrderResponse value;
        do {
            value = client.getCachedOrder().block(Duration.ofSeconds(1));
            if (value != null && message.equals(value.message())) {
                return value;
            }
            sleep(Duration.ofMillis(10));
        } while (System.nanoTime() < deadline);
        throw new IllegalStateException("Timed out waiting for refreshed cache value " + message);
    }

    private static void await(CountDownLatch latch, String message) {
        try {
            require(latch.await(2, TimeUnit.SECONDS), message);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for native smoke evidence", error);
        }
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for native smoke state", error);
        }
    }

    private static byte[] gzip(String value) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (GZIPOutputStream gzip = new GZIPOutputStream(output)) {
                gzip.write(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            return output.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to create native compression fixture", ex);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
