package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.annotation.*;
import io.github.huynhngochuyhoang.httpstarter.auth.AuthContext;
import io.github.huynhngochuyhoang.httpstarter.auth.AuthProvider;
import io.github.huynhngochuyhoang.httpstarter.auth.InvalidatableAuthProvider;
import io.github.huynhngochuyhoang.httpstarter.auth.OutboundAuthFilter;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import io.github.huynhngochuyhoang.httpstarter.exception.LogicalCallTimeoutException;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientCacheOutcome;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientFailureStage;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientObserverEvent;
import io.github.huynhngochuyhoang.httpstarter.observability.MicrometerHttpClientObserver;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.netty.DisposableServer;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.server.HttpServer;
import reactor.test.scheduler.VirtualTimeScheduler;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SemanticReadReplayTimeoutContractTest {

    @Test
    void semanticReadIntentDoesNotEnableRetryOrChangeOperatorOrder() {
        AtomicInteger noRetryDispatches = new AtomicInteger();
        WebClient noRetryWebClient = webClient(request -> {
            noRetryDispatches.incrementAndGet();
            return Mono.just(ClientResponse.create(HttpStatus.SERVICE_UNAVAILABLE).body("failed").build());
        });

        try (AnnotationConfigApplicationContext context = context()) {
            LocalResponseCacheManager manager = LocalResponseCacheManager.testing(System::nanoTime);
            SemanticBoundaryClient noRetry = client(
                    noRetryWebClient, config(0, true), context, manager,
                    new NoopResilienceOperatorApplier(), null, null);

            assertThatThrownBy(() -> query(noRetry, "no-retry").block())
                    .isInstanceOf(io.github.huynhngochuyhoang.httpstarter.exception.RemoteServiceException.class);
            assertThat(noRetryDispatches).hasValue(1);
            assertThat(manager.snapshot().currentSize()).isZero();
            manager.close();
        }

        AtomicInteger ineligibleDispatches = new AtomicInteger();
        RecordingCompositionApplier ineligibleApplier = new RecordingCompositionApplier();
        ReactiveHttpClientProperties.ClientConfig ineligibleConfig = config(0, true);
        enableComposition(ineligibleConfig, "GET");
        try (AnnotationConfigApplicationContext context = context()) {
            LocalResponseCacheManager manager = LocalResponseCacheManager.testing(System::nanoTime);
            SemanticBoundaryClient ineligible = client(
                    webClient(request -> {
                        ineligibleDispatches.incrementAndGet();
                        return Mono.just(ClientResponse.create(HttpStatus.SERVICE_UNAVAILABLE).body("failed").build());
                    }),
                    ineligibleConfig, context, manager, ineligibleApplier, null, null);

            assertThatThrownBy(() -> query(ineligible, "ineligible").block())
                    .isInstanceOf(io.github.huynhngochuyhoang.httpstarter.exception.RemoteServiceException.class);
            assertThat(ineligibleDispatches).hasValue(1);
            assertThat(ineligibleApplier.applied).containsExactly("rateLimiter", "circuitBreaker", "bulkhead");
            manager.close();
        }

        AtomicInteger retryDispatches = new AtomicInteger();
        RecordingCompositionApplier activeApplier = new RecordingCompositionApplier();
        ReactiveHttpClientProperties.ClientConfig activeConfig = config(0, true);
        enableComposition(activeConfig, "POST");
        try (AnnotationConfigApplicationContext context = context()) {
            LocalResponseCacheManager manager = LocalResponseCacheManager.testing(System::nanoTime);
            SemanticBoundaryClient active = client(
                    webClient(request -> retryDispatches.incrementAndGet() == 1
                            ? Mono.just(ClientResponse.create(HttpStatus.SERVICE_UNAVAILABLE).body("retry").build())
                            : Mono.just(ok("recovered"))),
                    activeConfig, context, manager, activeApplier, null, null);

            assertThat(query(active, "active").block()).isEqualTo("recovered");
            assertThat(query(active, "active").block()).isEqualTo("recovered");
            assertThat(retryDispatches).hasValue(2);
            assertThat(activeApplier.applied)
                    .containsExactly("retry", "rateLimiter", "circuitBreaker", "bulkhead");
            assertThat(activeApplier.subscribed)
                    .startsWith("bulkhead", "circuitBreaker", "rateLimiter", "retry");
            manager.close();
        }
    }

    @Test
    void authReplayThenRetryConsumesPreResolvedCredentialsOnlyOnceAndResetsAttemptEvidence() {
        ReactiveHttpClientProperties.ClientConfig config = config(0, true);
        config.setAuthProvider("semantic-auth");
        config.getCache().getPolicies().get("semantic")
                .setVaryByHeaders(List.of("X-Tenant", HttpHeaders.AUTHORIZATION));
        config.getResilience().setEnabled(true);
        config.getResilience().setRetry("semantic-retry");
        config.getResilience().setRetryMethods(java.util.Set.of("POST"));
        AtomicInteger authCalls = new AtomicInteger();
        AtomicInteger invalidations = new AtomicInteger();
        List<String> credentials = new CopyOnWriteArrayList<>();
        List<HttpClientObserverEvent> observed = new CopyOnWriteArrayList<>();
        InvalidatableAuthProvider authProvider = new InvalidatableAuthProvider() {
            @Override
            public Mono<AuthContext> getAuth(io.github.huynhngochuyhoang.httpstarter.auth.AuthRequest request) {
                String credential = switch (authCalls.incrementAndGet()) {
                    case 1 -> "stale";
                    case 2 -> "fresh-one";
                    case 3 -> "fresh-two";
                    default -> "fresh-three";
                };
                return Mono.just(AuthContext.builder()
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + credential)
                        .build());
            }

            @Override
            public Mono<Void> invalidate() {
                invalidations.incrementAndGet();
                return Mono.empty();
            }
        };
        WebClient webClient = WebClient.builder()
                .baseUrl("http://semantic-boundary.test")
                .filter(new OutboundAuthFilter("semantic-boundary-client", authProvider))
                .filter(ReactiveClientInvocationHandler.finalRequestObservationFilter())
                .exchangeFunction(request -> {
                    String credential = request.headers().getFirst(HttpHeaders.AUTHORIZATION);
                    credentials.add(credential);
                    if ("Bearer stale".equals(credential)) {
                        return Mono.just(ClientResponse.create(HttpStatus.UNAUTHORIZED)
                                .header("X-Prior-Response", "401").build());
                    }
                    if ("Bearer fresh-one".equals(credential)) {
                        return Mono.just(ClientResponse.create(HttpStatus.SERVICE_UNAVAILABLE)
                                .header("X-Prior-Response", "503").body("retry").build());
                    }
                    return Mono.just(ClientResponse.create(HttpStatus.OK)
                            .header(HttpHeaders.CONTENT_TYPE, "text/plain")
                            .header("X-Final-Response", credential)
                            .body("authorized").build());
                })
                .build();

        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getBeanFactory().registerSingleton(
                    "semanticBoundaryObserver",
                    (io.github.huynhngochuyhoang.httpstarter.observability.HttpClientObserver) observed::add);
            context.refresh();
            LocalResponseCacheManager manager = LocalResponseCacheManager.testing(System::nanoTime);
            SemanticBoundaryClient client = client(
                    webClient, config, context, manager, new RetryOnceApplier(), authProvider,
                    "http://semantic-boundary.test");

            assertThat(query(client, "auth").block()).isEqualTo("authorized");
            assertThat(manager.snapshot().currentSize()).isZero();
            assertThat(query(client, "auth").block()).isEqualTo("authorized");

            assertThat(authCalls).hasValue(4);
            assertThat(invalidations).hasValue(1);
            assertThat(credentials).containsExactly(
                    "Bearer stale", "Bearer fresh-one", "Bearer fresh-two", "Bearer fresh-three");
            assertThat(observed).hasSize(2);
            HttpClientObserverEvent retried = observed.get(0);
            assertThat(retried.getAttemptCount()).isEqualTo(2);
            assertThat(retried.getStatusCode()).isEqualTo(200);
            assertThat(retried.getFailureStage()).isNull();
            assertThat(retried.getError()).isNull();
            assertThat(retried.getRequestBytes()).isEqualTo(4L);
            manager.close();
        }
    }

    @Test
    void bodyPreservingRedirectStaysInsideOnePostFlightAndCachesTheFinalResponse() throws Exception {
        AtomicInteger initialDispatches = new AtomicInteger();
        AtomicInteger redirectedDispatches = new AtomicInteger();
        List<String> receivedBodies = new CopyOnWriteArrayList<>();
        Sinks.One<String> finalResponse = Sinks.one();
        java.util.concurrent.CountDownLatch redirected = new java.util.concurrent.CountDownLatch(1);
        DisposableServer server = HttpServer.create()
                .port(0)
                .handle((request, response) -> request.receive().aggregate().asString()
                        .flatMap(body -> {
                            receivedBodies.add(body);
                            if (request.uri().startsWith("/query/redirect")) {
                                initialDispatches.incrementAndGet();
                                return response.status(HttpStatus.TEMPORARY_REDIRECT.value())
                                        .header(HttpHeaders.LOCATION, "/redirect-target")
                                        .send().then();
                            }
                            redirectedDispatches.incrementAndGet();
                            redirected.countDown();
                            return response.header(HttpHeaders.CONTENT_TYPE, "text/plain")
                                    .sendString(finalResponse.asMono()).then();
                        }))
                .bindNow();
        try (AnnotationConfigApplicationContext context = context()) {
            ReactiveHttpClientProperties.ClientConfig config = config(0, true);
            config.setFollowRedirects(true);
            LocalResponseCacheManager manager = LocalResponseCacheManager.testing(System::nanoTime);
            WebClient webClient = WebClient.builder()
                    .baseUrl("http://127.0.0.1:" + server.port())
                    .clientConnector(new ReactorClientHttpConnector(
                            HttpClient.create().followRedirect(true).disableRetry(true)))
                    .build();
            SemanticBoundaryClient client = client(
                    webClient, config, context, manager, new NoopResilienceOperatorApplier(), null, null);

            Mono<String> call = query(client, "redirect");
            CompletableFuture<String> leader = call.toFuture();
            assertThat(redirected.await(1, TimeUnit.SECONDS)).isTrue();
            CompletableFuture<String> waiter = call.toFuture();
            assertThat(initialDispatches).hasValue(1);
            assertThat(redirectedDispatches).hasValue(1);
            finalResponse.tryEmitValue("redirected").orThrow();
            assertThat(leader.get(1, TimeUnit.SECONDS)).isEqualTo("redirected");
            assertThat(waiter.get(1, TimeUnit.SECONDS)).isEqualTo("redirected");
            assertThat(receivedBodies).containsExactly("body", "body");
            assertThat(manager.snapshot().currentSize()).isEqualTo(1);

            assertThat(call.block()).isEqualTo("redirected");
            assertThat(initialDispatches).hasValue(1);
            assertThat(redirectedDispatches).hasValue(1);
            manager.close();
        }
        finally {
            server.disposeNow();
        }
    }

    @Test
    void preDispatchAdmissionRejectionCannotPopulateAndHitsBypassResilience() {
        ReactiveHttpClientProperties.ClientConfig config = config(0, true);
        config.getResilience().setEnabled(true);
        config.getResilience().setCircuitBreaker("semantic-circuit");
        RejectingCircuitBreakerApplier applier = new RejectingCircuitBreakerApplier();
        AtomicInteger dispatches = new AtomicInteger();
        List<HttpClientObserverEvent> observed = new CopyOnWriteArrayList<>();

        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getBeanFactory().registerSingleton(
                    "semanticBoundaryObserver",
                    (io.github.huynhngochuyhoang.httpstarter.observability.HttpClientObserver) observed::add);
            context.refresh();
            LocalResponseCacheManager manager = LocalResponseCacheManager.testing(System::nanoTime);
            SemanticBoundaryClient client = client(
                    webClient(request -> Mono.just(ok("dispatch-" + dispatches.incrementAndGet()))),
                    config, context, manager, applier, null, null);

            assertThatThrownBy(() -> query(client, "admission").block())
                    .isInstanceOf(CallNotPermittedException.class);
            assertThat(dispatches).hasValue(0);
            assertThat(manager.snapshot().currentSize()).isZero();
            applier.reject.set(false);
            assertThat(query(client, "admission").block()).isEqualTo("dispatch-1");
            assertThat(query(client, "admission").block()).isEqualTo("dispatch-1");
            assertThat(dispatches).hasValue(1);
            assertThat(applier.applications).hasValue(2);
            assertThat(observed.get(0).getAttemptCount()).isZero();
            assertThat(observed.get(0).getRequestUrl()).isNull();
            assertThat(observed.get(0).getStatusCode()).isNull();
            manager.close();
        }
    }

    @Test
    void failedRefreshRetriesRemainHiddenAndPreserveStaleOnlyUntilHardExpiry() {
        AtomicLong ticker = new AtomicLong();
        AtomicInteger dispatches = new AtomicInteger();
        AtomicBoolean refreshFails = new AtomicBoolean(true);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ReactiveHttpClientProperties.ClientConfig config = config(0, true);
        ReactiveHttpClientProperties.CachePolicyConfig policy = config.getCache().getPolicies().get("semantic");
        policy.setTtlMs(200L);
        policy.setRefreshAfterMs(50L);
        policy.setRefreshTimeoutMs(500L);
        config.getResilience().setEnabled(true);
        config.getResilience().setRetry("semantic-retry");
        config.getResilience().setRetryMethods(java.util.Set.of("POST"));
        WebClient webClient = webClient(request -> {
            int dispatch = dispatches.incrementAndGet();
            if (dispatch == 1) {
                return Mono.just(ok("initial"));
            }
            if (refreshFails.get()) {
                return Mono.just(ClientResponse.create(HttpStatus.SERVICE_UNAVAILABLE).body("refresh-failed").build());
            }
            return Mono.just(ok("reloaded"));
        });

        try (AnnotationConfigApplicationContext context = context()) {
            LocalResponseCacheManager manager = LocalResponseCacheManager.testing(
                    ticker::get,
                    reactor.core.scheduler.Schedulers.parallel(),
                    LocalResponseCacheMetrics.enabled(registry, "semantic-boundary-client"),
                    "semantic-boundary-client");
            SemanticBoundaryClient client = client(
                    webClient, config, context, manager, new RetryOnceApplier(), null, null);

            assertThat(query(client, "refresh").block()).isEqualTo("initial");
            ticker.addAndGet(Duration.ofMillis(50).toNanos());
            assertThat(query(client, "refresh").block()).isEqualTo("initial");
            awaitValue(dispatches, 3);
            assertThat(registry.find(LocalResponseCacheMetrics.PREFIX + ".refreshes")
                    .tag("outcome", "failure").counters()
                    .stream().mapToDouble(counter -> counter.count()).sum()).isEqualTo(1.0);

            refreshFails.set(false);
            ticker.addAndGet(Duration.ofMillis(150).toNanos());
            assertThat(query(client, "refresh").block()).isEqualTo("reloaded");
            assertThat(query(client, "refresh").block()).isEqualTo("reloaded");
            assertThat(dispatches).hasValue(4);
            manager.close();
        }
    }

    @Test
    void preDispatchCancellationAndTimeoutCannotPublishPreparedPostState() {
        VirtualTimeScheduler scheduler = VirtualTimeScheduler.getOrSet();
        AtomicBoolean authorize = new AtomicBoolean();
        AtomicInteger authCalls = new AtomicInteger();
        AtomicInteger authCancellations = new AtomicInteger();
        AtomicInteger dispatches = new AtomicInteger();
        List<HttpClientObserverEvent> observed = new CopyOnWriteArrayList<>();
        AuthProvider authProvider = request -> {
            authCalls.incrementAndGet();
            return authorize.get()
                    ? Mono.just(AuthContext.empty())
                    : Mono.<AuthContext>never().doOnCancel(authCancellations::incrementAndGet);
        };
        ReactiveHttpClientProperties.ClientConfig config = config(50, true);
        config.setAuthProvider("semantic-auth");
        WebClient webClient = WebClient.builder()
                .baseUrl("http://semantic-boundary.test")
                .filter(new OutboundAuthFilter("semantic-boundary-client", authProvider))
                .filter(ReactiveClientInvocationHandler.finalRequestObservationFilter())
                .exchangeFunction(request -> Mono.just(ok("dispatch-" + dispatches.incrementAndGet())))
                .build();

        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getBeanFactory().registerSingleton(
                    "semanticBoundaryObserver",
                    (io.github.huynhngochuyhoang.httpstarter.observability.HttpClientObserver) observed::add);
            context.refresh();
            LocalResponseCacheManager manager = LocalResponseCacheManager.testing(System::nanoTime);
            SemanticBoundaryClient client = client(
                    webClient, config, context, manager, new NoopResilienceOperatorApplier(), authProvider,
                    "http://semantic-boundary.test");

            Disposable cancelled = query(client, "cancel").subscribe();
            awaitValue(authCalls, 1);
            cancelled.dispose();
            awaitValue(authCancellations, 1);

            CompletableFuture<String> timedOut = query(client, "timeout").toFuture();
            awaitValue(authCalls, 2);
            scheduler.advanceTimeBy(Duration.ofMillis(50));
            assertThatThrownBy(() -> timedOut.get(1, TimeUnit.SECONDS))
                    .satisfies(error -> assertThat(hasCause(error, LogicalCallTimeoutException.class)).isTrue());
            assertThat(dispatches).hasValue(0);
            assertThat(manager.snapshot().currentSize()).isZero();

            authorize.set(true);
            assertThat(query(client, "cancel").block()).isEqualTo("dispatch-1");
            assertThat(query(client, "timeout").block()).isEqualTo("dispatch-2");
            assertThat(observed).hasSize(4);
            assertThat(observed.get(0).getAttemptCount()).isZero();
            assertThat(observed.get(1).getAttemptCount()).isZero();
            manager.close();
        }
        finally {
            VirtualTimeScheduler.reset();
        }
    }

    @Test
    void onePostDeadlinePreservesResponseBodyTimeoutAttributionAndAllowsReplacementLoad() {
        AtomicInteger dispatches = new AtomicInteger();
        List<HttpClientObserverEvent> observed = new CopyOnWriteArrayList<>();
        DisposableServer server = HttpServer.create()
                .port(0)
                .handle((request, response) -> {
                    int dispatch = dispatches.incrementAndGet();
                    return request.receive().then(response
                            .header(HttpHeaders.CONTENT_TYPE, "text/plain")
                            .sendString(dispatch == 1
                                    ? Flux.concat(Mono.just("first"), Mono.never())
                                    : Mono.just("recovered"))
                            .then());
                })
                .bindNow();
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getBeanFactory().registerSingleton(
                    "semanticBoundaryObserver",
                    (io.github.huynhngochuyhoang.httpstarter.observability.HttpClientObserver) observed::add);
            context.refresh();
            ReactiveHttpClientProperties.ClientConfig config = config(300, true);
            LocalResponseCacheManager manager = LocalResponseCacheManager.testing(System::nanoTime);
            WebClient webClient = WebClient.builder()
                    .baseUrl("http://127.0.0.1:" + server.port())
                    .clientConnector(new ReactorClientHttpConnector(HttpClient.create().disableRetry(true)))
                    .filter(ReactiveClientInvocationHandler.finalRequestObservationFilter())
                    .build();
            SemanticBoundaryClient client = client(
                    webClient, config, context, manager, new NoopResilienceOperatorApplier(), null, null);

            assertThatThrownBy(() -> query(client, "body-timeout").block(Duration.ofSeconds(2)))
                    .satisfies(error -> {
                        LogicalCallTimeoutException timeout = findCause(error, LogicalCallTimeoutException.class);
                        assertThat(timeout).isNotNull();
                        assertThat(timeout.getFailureStage()).isEqualTo(HttpClientFailureStage.RESPONSE_BODY);
                    });
            assertThat(manager.snapshot().currentSize()).isZero();
            assertThat(query(client, "body-timeout").block()).isEqualTo("recovered");
            assertThat(query(client, "body-timeout").block()).isEqualTo("recovered");
            assertThat(dispatches).hasValue(2);

            HttpClientObserverEvent timeoutEvent = observed.get(0);
            assertThat(timeoutEvent.getStatusCode()).isEqualTo(200);
            assertThat(timeoutEvent.getFailureStage()).isEqualTo(HttpClientFailureStage.RESPONSE_BODY);
            assertThat(findCause(timeoutEvent.getError(), LogicalCallTimeoutException.class)).isNotNull();
            assertThat(timeoutEvent.getError()).isNotInstanceOf(java.util.concurrent.CancellationException.class);
            manager.close();
        }
        finally {
            server.disposeNow();
        }
    }

    @Test
    void postDecodeFailureMissAndHitAlignEveryTerminalSurfaceAndDownstreamMeter() {
        List<HttpClientObserverEvent> observed = new CopyOnWriteArrayList<>();
        List<HttpExchangeLogContext> exchangeLogs = new CopyOnWriteArrayList<>();
        List<ReactiveHttpClientLifecycleContext> lifecycleTerminals = new CopyOnWriteArrayList<>();
        AtomicInteger dispatches = new AtomicInteger();
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ReactiveHttpClientProperties.ObservabilityConfig observability =
                new ReactiveHttpClientProperties.ObservabilityConfig();
        observability.getCache().setEnabled(true);
        ReactiveHttpClientProperties.ClientConfig config = config(0, true);
        WebClient webClient = WebClient.builder()
                .baseUrl("http://semantic-boundary.test")
                .filter(ReactiveClientInvocationHandler.finalRequestObservationFilter())
                .exchangeFunction(request -> {
                    dispatches.incrementAndGet();
                    String body = request.url().getPath().endsWith("/decode")
                            ? "{"
                            : "{\"value\":\"cached\"}";
                    return Mono.just(ClientResponse.create(HttpStatus.OK)
                            .header(HttpHeaders.CONTENT_TYPE, "application/json")
                            .body(body)
                            .build());
                })
                .build();

        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getBeanFactory().registerSingleton(
                    "semanticTerminalObserver",
                    (io.github.huynhngochuyhoang.httpstarter.observability.HttpClientObserver) observed::add);
            context.getBeanFactory().registerSingleton(
                    "micrometerHttpClientObserver",
                    new MicrometerHttpClientObserver(registry, observability));
            context.getBeanFactory().registerSingleton(
                    "capturingExchangeLogger", new CapturingExchangeLogger(exchangeLogs));
            context.getBeanFactory().registerSingleton("semanticTerminalLifecycle", new ReactiveHttpClientLifecycleHook() {
                @Override
                public void onSuccess(ReactiveHttpClientLifecycleContext lifecycleContext) {
                    lifecycleTerminals.add(lifecycleContext);
                }

                @Override
                public void onError(ReactiveHttpClientLifecycleContext lifecycleContext) {
                    lifecycleTerminals.add(lifecycleContext);
                }
            });
            context.refresh();
            LocalResponseCacheManager manager = LocalResponseCacheManager.testing(
                    System::nanoTime,
                    reactor.core.scheduler.Schedulers.parallel(),
                    LocalResponseCacheMetrics.enabled(registry, "semantic-boundary-client"),
                    "semantic-boundary-client");
            SemanticBoundaryClient client = client(
                    webClient, config, context, manager, new NoopResilienceOperatorApplier(), null, null,
                    observability);

            assertThatThrownBy(() -> queryJson(client, "decode").block())
                    .satisfies(error -> assertThat(
                            hasCause(error, org.springframework.core.codec.DecodingException.class)).isTrue());
            assertThat(queryJson(client, "success").block()).isEqualTo(new JsonResult("cached"));
            assertThat(queryJson(client, "success").block()).isEqualTo(new JsonResult("cached"));

            assertThat(dispatches).hasValue(2);
            assertThat(observed).hasSize(3);
            assertThat(observed).extracting(HttpClientObserverEvent::getHttpMethod).containsOnly("POST");
            assertThat(observed).extracting(HttpClientObserverEvent::getCacheOutcome)
                    .containsExactly(
                            HttpClientCacheOutcome.MISS_LOADER,
                            HttpClientCacheOutcome.MISS_LOADER,
                            HttpClientCacheOutcome.FRESH_HIT);
            assertThat(observed.get(0).getAttemptCount()).isEqualTo(1);
            assertThat(observed.get(0).getStatusCode()).isEqualTo(200);
            assertThat(observed.get(0).getError()).isNotNull();
            assertThat(observed.get(1).getAttemptCount()).isEqualTo(1);
            assertThat(observed.get(1).getStatusCode()).isEqualTo(200);
            assertThat(observed.get(1).getError()).isNull();
            assertThat(observed.get(2).getAttemptCount()).isZero();
            assertThat(observed.get(2).getStatusCode()).isNull();
            assertThat(observed.get(2).getRequestUrl()).isNull();
            assertThat(observed.get(2).getRequestHeaders()).isEmpty();

            assertThat(exchangeLogs).hasSize(3);
            assertThat(exchangeLogs).extracting(HttpExchangeLogContext::httpMethod).containsOnly("POST");
            assertThat(exchangeLogs).extracting(HttpExchangeLogContext::cacheOutcome)
                    .containsExactly(
                            HttpClientCacheOutcome.MISS_LOADER,
                            HttpClientCacheOutcome.MISS_LOADER,
                            HttpClientCacheOutcome.FRESH_HIT);
            assertThat(exchangeLogs.get(2).subscriptionAttemptCount()).isZero();
            assertThat(exchangeLogs.get(2).requestUrl()).isNull();
            assertThat(exchangeLogs.get(2).requestHeaders())
                    .doesNotContainKeys(HttpHeaders.AUTHORIZATION, HttpHeaders.COOKIE);
            assertThat(lifecycleTerminals).hasSize(3);
            assertThat(lifecycleTerminals).extracting(ReactiveHttpClientLifecycleContext::httpMethod)
                    .containsOnly("POST");
            assertThat(lifecycleTerminals).extracting(ReactiveHttpClientLifecycleContext::cacheOutcome)
                    .containsExactly(
                            HttpClientCacheOutcome.MISS_LOADER,
                            HttpClientCacheOutcome.MISS_LOADER,
                            HttpClientCacheOutcome.FRESH_HIT);
            assertThat(lifecycleTerminals.get(2).attemptNumber()).isZero();
            assertThat(lifecycleTerminals.get(2).requestUrl()).isNull();

            assertThat(registry.find(observability.getMetricName())
                    .tags("client.name", "semantic-boundary-client", "api.name", "queryJson")
                    .timers().stream().mapToLong(io.micrometer.core.instrument.Timer::count).sum())
                    .isEqualTo(2L);
            assertThat(registry.get(LocalResponseCacheMetrics.PREFIX + ".callers")
                    .tags("client.name", "semantic-boundary-client", "api.name", "queryJson",
                            "outcome", "MISS_LOADER")
                    .counter().count()).isEqualTo(2.0);
            assertThat(registry.get(LocalResponseCacheMetrics.PREFIX + ".callers")
                    .tags("client.name", "semantic-boundary-client", "api.name", "queryJson",
                            "outcome", "FRESH_HIT")
                    .counter().count()).isEqualTo(1.0);
            manager.close();
        }
    }

    private static WebClient webClient(org.springframework.web.reactive.function.client.ExchangeFunction exchange) {
        return WebClient.builder()
                .baseUrl("http://semantic-boundary.test")
                .filter(ReactiveClientInvocationHandler.finalRequestObservationFilter())
                .exchangeFunction(exchange)
                .build();
    }

    private static ReactiveHttpClientProperties.ClientConfig config(long logicalTimeoutMs, boolean singleFlight) {
        ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();
        config.setLogicalCallTimeoutMs(logicalTimeoutMs);
        ReactiveHttpClientProperties.CachePolicyConfig policy =
                new ReactiveHttpClientProperties.CachePolicyConfig();
        policy.setTtlMs(1_000L);
        policy.setMaximumSize(100L);
        policy.setSingleFlight(singleFlight);
        policy.setSharedResponse(true);
        policy.setVaryByParameters(List.of("payload"));
        policy.setVaryByHeaders(List.of("X-Tenant"));
        config.getCache().getPolicies().put("semantic", policy);
        return config;
    }

    private static void enableComposition(ReactiveHttpClientProperties.ClientConfig config, String retryMethod) {
        config.getResilience().setEnabled(true);
        config.getResilience().setRetry("semantic-retry");
        config.getResilience().setRateLimiter("semantic-rate");
        config.getResilience().setCircuitBreaker("semantic-circuit");
        config.getResilience().setBulkhead("semantic-bulkhead");
        config.getResilience().setRetryMethods(java.util.Set.of(retryMethod));
    }

    private static SemanticBoundaryClient client(
            WebClient webClient,
            ReactiveHttpClientProperties.ClientConfig config,
            AnnotationConfigApplicationContext context,
            LocalResponseCacheManager manager,
            ResilienceOperatorApplier applier,
            AuthProvider authProvider,
            String baseUrl) {
        return client(webClient, config, context, manager, applier, authProvider, baseUrl,
                new ReactiveHttpClientProperties.ObservabilityConfig());
    }

    private static SemanticBoundaryClient client(
            WebClient webClient,
            ReactiveHttpClientProperties.ClientConfig config,
            AnnotationConfigApplicationContext context,
            LocalResponseCacheManager manager,
            ResilienceOperatorApplier applier,
            AuthProvider authProvider,
            String baseUrl,
            ReactiveHttpClientProperties.ObservabilityConfig observability) {
        ReactiveClientInvocationHandler handler = new ReactiveClientInvocationHandler(
                webClient,
                new MethodMetadataCache(),
                new RequestArgumentResolver(),
                new DefaultErrorDecoder(),
                config,
                "semantic-boundary-client",
                SemanticBoundaryClient.class,
                context,
                applier,
                TestJsonCodecs.jsonCodec(),
                observability,
                manager,
                authProvider,
                baseUrl);
        return (SemanticBoundaryClient) Proxy.newProxyInstance(
                SemanticReadReplayTimeoutContractTest.class.getClassLoader(),
                new Class<?>[]{SemanticBoundaryClient.class},
                handler);
    }

    private static Mono<String> query(SemanticBoundaryClient client, String target) {
        return client.query(target, "body", "text/plain", "tenant", "Bearer lookup");
    }

    private static Mono<JsonResult> queryJson(SemanticBoundaryClient client, String target) {
        return client.queryJson(target, "body", "application/json", "tenant");
    }

    private static AnnotationConfigApplicationContext context() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.refresh();
        return context;
    }

    private static ClientResponse ok(String body) {
        return ClientResponse.create(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_TYPE, "text/plain")
                .body(body)
                .build();
    }

    private static boolean hasCause(Throwable error, Class<? extends Throwable> type) {
        return findCause(error, type) != null;
    }

    private static <T extends Throwable> T findCause(Throwable error, Class<T> type) {
        Throwable current = error;
        while (current != null) {
            if (type.isInstance(current)) {
                return type.cast(current);
            }
            current = current.getCause() != current ? current.getCause() : null;
        }
        return null;
    }

    private static void awaitValue(AtomicInteger value, int expected) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
        while (value.get() != expected && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        assertThat(value).hasValue(expected);
    }

    @ReactiveHttpClient(name = "semantic-boundary-client", baseUrl = "http://semantic-boundary.test")
    interface SemanticBoundaryClient {
        @POST("/query/{target}")
        @CacheResponse(value = "semantic", semanticRead = true)
        @IdempotencyKey
        Mono<String> query(
                @PathVar("target") String target,
                @Body @CacheKey("payload") String payload,
                @HeaderParam(HttpHeaders.CONTENT_TYPE) String contentType,
                @HeaderParam("X-Tenant") String tenant,
                @HeaderParam(HttpHeaders.AUTHORIZATION) String authorization);

        @POST("/json/{target}")
        @CacheResponse(value = "semantic", semanticRead = true)
        @IdempotencyKey
        @LogHttpExchange(logger = CapturingExchangeLogger.class)
        Mono<JsonResult> queryJson(
                @PathVar("target") String target,
                @Body @CacheKey("payload") String payload,
                @HeaderParam(HttpHeaders.CONTENT_TYPE) String contentType,
                @HeaderParam("X-Tenant") String tenant);
    }

    private record JsonResult(String value) {
    }

    private static final class CapturingExchangeLogger implements HttpExchangeLogger {
        private final List<HttpExchangeLogContext> exchanges;

        private CapturingExchangeLogger(List<HttpExchangeLogContext> exchanges) {
            this.exchanges = exchanges;
        }

        @Override
        public void log(HttpExchangeLogContext context) {
            exchanges.add(context);
        }
    }

    private static final class RetryOnceApplier extends NoopResilienceOperatorApplier {
        @Override
        public <T> Mono<T> applyRetry(Mono<T> mono, String instanceName) {
            return mono.retry(1);
        }

        @Override
        public boolean canRetryMoreThanOnce(String instanceName) {
            return true;
        }
    }

    private static final class RecordingCompositionApplier extends NoopResilienceOperatorApplier {
        private final List<String> applied = new ArrayList<>();
        private final List<String> subscribed = new CopyOnWriteArrayList<>();

        @Override
        public <T> Mono<T> applyRetry(Mono<T> mono, String instanceName) {
            applied.add("retry");
            Mono<T> retried = mono.retry(1);
            return Mono.defer(() -> {
                subscribed.add("retry");
                return retried;
            });
        }

        @Override
        public <T> Mono<T> applyRateLimiter(Mono<T> mono, String instanceName) {
            applied.add("rateLimiter");
            return Mono.defer(() -> {
                subscribed.add("rateLimiter");
                return mono;
            });
        }

        @Override
        public <T> Mono<T> applyCircuitBreaker(Mono<T> mono, String instanceName) {
            applied.add("circuitBreaker");
            return Mono.defer(() -> {
                subscribed.add("circuitBreaker");
                return mono;
            });
        }

        @Override
        public <T> Mono<T> applyBulkhead(Mono<T> mono, String instanceName) {
            applied.add("bulkhead");
            return Mono.defer(() -> {
                subscribed.add("bulkhead");
                return mono;
            });
        }

        @Override
        public boolean canRetryMoreThanOnce(String instanceName) {
            return true;
        }
    }

    private static final class RejectingCircuitBreakerApplier extends NoopResilienceOperatorApplier {
        private final CircuitBreaker circuitBreaker = CircuitBreaker.ofDefaults("semantic-circuit");
        private final AtomicBoolean reject = new AtomicBoolean(true);
        private final AtomicInteger applications = new AtomicInteger();

        @Override
        public <T> Mono<T> applyCircuitBreaker(Mono<T> mono, String instanceName) {
            applications.incrementAndGet();
            return Mono.defer(() -> reject.get()
                    ? Mono.error(CallNotPermittedException.createCallNotPermittedException(circuitBreaker))
                    : mono);
        }
    }
}
