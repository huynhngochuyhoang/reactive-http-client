package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.annotation.*;
import io.github.huynhngochuyhoang.httpstarter.auth.*;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import io.github.huynhngochuyhoang.httpstarter.exception.AuthProviderException;
import io.github.huynhngochuyhoang.httpstarter.exception.LogicalCallTimeoutException;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientCacheOutcome;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientFailureStage;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientObserver;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientObserverEvent;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.server.HttpServer;
import reactor.test.StepVerifier;
import reactor.test.scheduler.VirtualTimeScheduler;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Timeout(30)
class CacheWorkCompositionContractTest {
    private static final String NAME = "work-composition";
    private static final Duration WAIT = Duration.ofSeconds(10);

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void localRejectionsNeverEnterRealOperatorsAndHitsDoNotNeedLoadCapacity(boolean single) {
        var config = config(single);
        config.getCache().getPolicies().get(NAME).getWork().setMaximumConcurrentCallers(2L);
        Operators operators = new Operators();
        selectOperators(config);
        Sinks.One<ClientResponse> response = Sinks.one();
        AtomicInteger dispatches = new AtomicInteger();
        try (Fixture f = new Fixture(config, operators, null, web(request -> {
            dispatches.incrementAndGet();
            return request.url().getPath().endsWith("/warm") ? Mono.just(ok("warm")) : response.asMono();
        }))) {
            assertThat(f.call(false, "warm").block(WAIT)).isEqualTo("warm");
            var active = f.call(false, "busy").toFuture();
            f.counts(1, 1, 0);
            var before = operators.history();
            StepVerifier.create(f.call(false, "other")).expectError(CacheLoadAdmission.Rejected.class).verify(WAIT);
            assertThat(operators.history()).isEqualTo(before);
            f.diagnostics.terminal(1, CacheLoadAdmission.Rejected.class, 0, null, null, null, Map.of());
            assertThat(f.call(false, "warm").block(WAIT)).isEqualTo("warm");
            CompletableFuture<String> waiter = single ? f.call(false, "busy").toFuture() : null;
            if (single) {
                f.counts(2, 1, 0);
                StepVerifier.create(f.call(false, "warm")).expectError(CacheCallerAdmission.Rejected.class).verify(WAIT);
                f.diagnostics.terminal(3, CacheCallerAdmission.Rejected.class, 0, null, null, null, Map.of());
            }
            assertThat(operators.history()).isEqualTo(before);
            assertThat(dispatches).hasValue(2);
            response.tryEmitValue(ok("loaded")).orThrow();
            assertThat(active).isDone();
            assertThat(active.join()).isEqualTo("loaded");
            if (waiter != null) { assertThat(waiter.join()).isEqualTo("loaded"); }
            f.counts(0, 0, 0);
            assertThat(operators.applied).containsExactly(
                    "retry", "rate", "circuit", "bulkhead", "retry", "rate", "circuit", "bulkhead");
            assertThat(operators.subscribed).containsExactly(
                    "bulkhead", "circuit", "rate", "retry", "bulkhead", "circuit", "rate", "retry");
            assertThat(operators.circuit.getMetrics().getNumberOfSuccessfulCalls()).isEqualTo(2);
            f.diagnostics.size(single ? 6 : 4);
        }
    }

    enum Guard { CIRCUIT, RATE, BULKHEAD }

    @ParameterizedTest
    @EnumSource(Guard.class)
    void realGuardRejectionsReleaseBothReservationsWithoutDispatch(Guard guard) {
        Operators operators = new Operators();
        var config = config(true);
        selectOperators(config);
        AtomicInteger dispatches = new AtomicInteger();
        try (Fixture f = new Fixture(config, operators, null, web(request -> {
            dispatches.incrementAndGet();
            return Mono.just(ok("value"));
        }))) {
            if (guard == Guard.CIRCUIT) { operators.circuit.transitionToOpenState(); }
            if (guard == Guard.RATE) {
                for (int i = 0; i < 100; i++) { assertThat(operators.rate.acquirePermission()).isTrue(); }
            }
            if (guard == Guard.BULKHEAD) { operators.bulkhead.acquirePermission(); }
            Class<? extends Throwable> type = switch (guard) {
                case CIRCUIT -> CallNotPermittedException.class;
                case RATE -> RequestNotPermitted.class;
                case BULKHEAD -> BulkheadFullException.class;
            };
            StepVerifier.create(f.call(true, "reject")).expectError(type).verify(WAIT);
            f.counts(0, 0, 0);
            f.diagnostics.terminal(0, type, 0, null, null, null, Map.of());
            assertThat(dispatches).hasValue(0);
            assertThat(f.manager.snapshot().currentSize()).isZero();
            assertThat(operators.subscribed).doesNotContain("retry");
            if (guard == Guard.CIRCUIT) { operators.circuit.transitionToClosedState(); }
            if (guard == Guard.RATE) { operators.rate.changeLimitForPeriod(101); }
            if (guard == Guard.BULKHEAD) { operators.bulkhead.releasePermission(); }
            // Another rejected subscription must not accumulate local reservations either.
            if (guard == Guard.RATE) {
                StepVerifier.create(f.call(true, "again")).expectError(RequestNotPermitted.class).verify(WAIT);
            } else {
                assertThat(f.call(true, "again").block(WAIT)).isEqualTo("value");
            }
            f.counts(0, 0, 0);
            f.diagnostics.size(2);
        }
    }

    @Test
    void workSelectionDoesNotSelectOperatorsOrGrantUnsafeReplay() throws Exception {
        var config = config(false);
        config.getResilience().setEnabled(true);
        config.getResilience().setRetryMethods(Set.of("POST"));
        Operators operators = new Operators();
        AtomicInteger dispatches = new AtomicInteger();
        try (Fixture f = new Fixture(config, operators, null, web(request -> {
            dispatches.incrementAndGet();
            return Mono.just(ClientResponse.create(HttpStatus.SERVICE_UNAVAILABLE).build());
        }))) {
            StepVerifier.create(f.call(true, "unselected")).expectError().verify(WAIT);
            assertThat(operators.applied).isEmpty();
            assertThat(dispatches).hasValue(1);
            f.counts(0, 0, 0);
        }
        config.getResilience().setEnabled(true);
        config.getResilience().setRetry(NAME);
        config.getResilience().setRetryMethods(Set.of("POST"));
        config.getResilience().setStrictUnsafeRetryValidation(true);
        var metadata = new MethodMetadataCache();
        assertThatThrownBy(() -> ReactiveHttpClientFactoryBean.validateEffectiveResilienceContracts(
                UnsafeClient.class, metadata, config, operators, NAME)).hasMessageContaining("Unsafe");
        assertThatThrownBy(() -> metadata.validateDeclarativeCachePolicies(StreamingClient.class, NAME, config))
                .hasMessageContaining("streaming or application-owned request bodies");
        var plan = RequestPlan.from(metadata.get(StreamingClient.class.getMethod("stream", Flux.class)),
                StreamingClient.class);
        assertThat(plan.bodyRepeatability()).isEqualTo(RequestBodyRepeatability.NON_REPEATABLE);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void retryBackoffKeepsOneSourceReservationUntilSuccessOrCallerTimeout(boolean timeout) {
        var clock = VirtualTimeScheduler.getOrSet();
        Operators operators = new Operators();
        var config = config(true);
        selectOperators(config);
        config.setLogicalCallTimeoutMs(timeout ? 1_000 : 0);
        AtomicInteger dispatches = new AtomicInteger();
        try (Fixture f = new Fixture(config, operators, null, web(request ->
                Mono.just(dispatches.incrementAndGet() == 1
                        ? ClientResponse.create(HttpStatus.SERVICE_UNAVAILABLE)
                                .header("X-Prior", "retry").body("failed").build()
                        : ok("recovered"))))) {
            var first = f.call(true, "retry").toFuture();
            f.counts(1, 1, 0);
            assertThat(dispatches).hasValue(1);
            assertThat(operators.bulkhead.getMetrics().getAvailableConcurrentCalls()).isZero();
            var before = operators.history();
            StepVerifier.create(f.call(true, "other")).expectError(CacheLoadAdmission.Rejected.class).verify(WAIT);
            assertThat(operators.history()).isEqualTo(before);
            clock.advanceTimeBy(Duration.ofSeconds(timeout ? 1 : 2));
            if (timeout) {
                assertThat(first).isDone();
                assertThatThrownBy(first::join).hasCauseInstanceOf(LogicalCallTimeoutException.class);
                f.diagnostics.terminal(1, LogicalCallTimeoutException.class, 1, null, null, null, Map.of());
                assertThat(dispatches).hasValue(1);
            } else {
                assertThat(first).isDone();
                assertThat(first.join()).isEqualTo("recovered");
                f.diagnostics.terminal(1, null, 2, 200, url(true, "retry"), null, Map.of("X-Final", List.of("yes")));
                assertThat(dispatches).hasValue(2);
            }
            f.counts(0, 0, 0);
            assertThat(operators.bulkhead.getMetrics().getAvailableConcurrentCalls()).isEqualTo(1);
            clock.advanceTimeBy(Duration.ofDays(1));
            assertThat(dispatches).hasValue(timeout ? 1 : 2);
            assertThat(f.metric(".loads", "success") + f.metric(".loads", "failure")
                    + f.metric(".loads", "cancellation")).isEqualTo(1);
            f.diagnostics.size(2);
        } finally { VirtualTimeScheduler.reset(); }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void independentCallerDeadlinesNeverStealTheSurvivingSourcesCapacity(boolean leaderExpires) {
        var clock = VirtualTimeScheduler.getOrSet();
        var config = config(true);
        config.setLogicalCallTimeoutMs(leaderExpires ? 1_000 : 10_000);
        AtomicInteger cancellations = new AtomicInteger();
        AtomicInteger dispatches = new AtomicInteger();
        Sinks.One<ClientResponse> result = Sinks.one();
        try (Fixture f = new Fixture(config, new Operators(), null, web(request -> {
            dispatches.incrementAndGet();
            return result.asMono().doOnCancel(cancellations::incrementAndGet);
        }))) {
            var first = f.call(false, "shared").toFuture();
            clock.advanceTimeBy(Duration.ofMillis(500));
            config.setLogicalCallTimeoutMs(leaderExpires ? 10_000 : 1_000);
            var waiter = f.call(false, "shared").toFuture();
            f.counts(2, 1, 0);
            clock.advanceTimeBy(Duration.ofSeconds(1));
            var timedOut = leaderExpires ? first : waiter;
            var survivor = leaderExpires ? waiter : first;
            assertThat(timedOut).isDone();
            assertThatThrownBy(timedOut::join).hasCauseInstanceOf(LogicalCallTimeoutException.class);
            assertThat(survivor).isNotDone();
            assertThat(cancellations).hasValue(0);
            f.counts(1, 1, 0);
            f.diagnostics.terminal(0, LogicalCallTimeoutException.class, leaderExpires ? 1 : 0,
                    null, leaderExpires ? url(false, "shared") : null, null, Map.of());
            StepVerifier.create(f.call(false, "other")).expectError(CacheLoadAdmission.Rejected.class).verify(WAIT);
            result.tryEmitValue(ok("survived")).orThrow();
            assertThat(survivor).isDone();
            assertThat(survivor.join()).isEqualTo("survived");
            f.diagnostics.terminal(2, null, leaderExpires ? 0 : 1, leaderExpires ? null : 200,
                    leaderExpires ? null : url(false, "shared"), null,
                    leaderExpires ? Map.of() : Map.of("X-Final", List.of("yes")));
            f.counts(0, 0, 0);
            clock.advanceTimeBy(Duration.ofDays(1));
            assertThat(dispatches).hasValue(1);
            assertThat(cancellations).hasValue(0);
            f.diagnostics.size(3);
        } finally { VirtualTimeScheduler.reset(); }
    }

    enum BodyEnd { LOGICAL_TIMEOUT, CANCEL }

    @Test
    void nativeRequestTimeoutTerminatesTheSharedSourceAndAllItsCallers() throws Exception {
        CountDownLatch headersObserved = new CountDownLatch(1);
        AtomicInteger dispatches = new AtomicInteger();
        var server = HttpServer.create().host("127.0.0.1").port(0).handle((request, response) -> {
            dispatches.incrementAndGet();
            return request.receive().then(response.header(HttpHeaders.CONTENT_TYPE, "text/plain")
                    .header("X-Phase", "body")
                    .sendString(Flux.concat(Mono.just("first"), Mono.never())).then());
        }).bindNow();
        var config = config(true);
        config.setBaseUrl("http://127.0.0.1:" + server.port());
        config.setRequestTimeoutMs(2_000);
        config.setLogicalCallTimeoutMs(60_000);
        var connection = reactor.netty.resources.ConnectionProvider.newConnection();
        try (Fixture f = new Fixture(config, new Operators(), null, WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(HttpClient.create(connection).disableRetry(true)
                        .doOnResponse((response, channel) -> headersObserved.countDown()))))) {
            var first = f.call(true, "native-timeout").toFuture();
            var waiter = f.call(true, "native-timeout").toFuture();
            assertThat(headersObserved.await(10, TimeUnit.SECONDS)).isTrue();
            f.counts(2, 1, 0);
            assertThatThrownBy(() -> first.get(10, TimeUnit.SECONDS)).satisfies(error ->
                    assertThat(cause(error, io.netty.handler.timeout.ReadTimeoutException.class)).isNotNull());
            assertThatThrownBy(() -> waiter.get(10, TimeUnit.SECONDS)).satisfies(error ->
                    assertThat(cause(error, io.netty.handler.timeout.ReadTimeoutException.class)).isNotNull());
            f.counts(0, 0, 0);
            f.diagnostics.terminal(f.diagnostics.index(HttpClientCacheOutcome.MISS_LOADER),
                    io.netty.handler.timeout.ReadTimeoutException.class, 1, 200,
                    config.getBaseUrl() + "/post/native-timeout", HttpClientFailureStage.RESPONSE_BODY,
                    Map.of("X-Phase", List.of("body")));
            f.diagnostics.terminal(f.diagnostics.index(HttpClientCacheOutcome.COALESCED_WAITER),
                    io.netty.handler.timeout.ReadTimeoutException.class, 0, null, null, null, Map.of());
            f.diagnostics.size(2);
            assertThat(f.diagnostics.cancelled).hasValue(0);
            assertThat(dispatches).hasValue(1);
            assertThat(f.manager.snapshot().currentSize()).isZero();
        } finally {
            server.disposeNow();
            connection.disposeLater().block(WAIT);
        }
    }

    @Test
    void refreshUsesRealAuthAndOperatorsButItsOwnCapacityAndDeadline() {
        var clock = VirtualTimeScheduler.getOrSet();
        var config = config(true);
        selectOperators(config);
        var policy = config.getCache().getPolicies().get(NAME);
        policy.setRefreshAfterMs(1_000L);
        policy.setRefreshTimeoutMs(5_000L);
        policy.getWork().setMaximumConcurrentRefreshes(1L);
        Operators operators = new Operators();
        AtomicInteger authCalls = new AtomicInteger();
        AtomicInteger dispatches = new AtomicInteger();
        AtomicInteger cancellations = new AtomicInteger();
        AuthProvider auth = request -> {
            authCalls.incrementAndGet();
            return Mono.just(AuthContext.empty());
        };
        try (Fixture f = new Fixture(config, operators, auth, web(request -> {
            int dispatch = dispatches.incrementAndGet();
            if (dispatch == 1) { return Mono.just(ok("initial")); }
            if (dispatch == 2) { return Mono.just(ClientResponse.create(HttpStatus.SERVICE_UNAVAILABLE).build()); }
            return Mono.<ClientResponse>never().doOnCancel(cancellations::incrementAndGet);
        }))) {
            assertThat(f.call(true, "refresh").block(WAIT)).isEqualTo("initial");
            f.ticker.set(Duration.ofSeconds(2).toNanos());
            operators.circuit.transitionToOpenState();
            assertThat(f.call(true, "refresh").block(WAIT)).isEqualTo("initial");
            f.counts(0, 0, 0);
            assertThat(dispatches).hasValue(1);
            assertThat(f.metric(".refreshes", "failure")).isEqualTo(1);
            f.diagnostics.terminal(1, null, 0, null, null, null, Map.of());
            operators.circuit.transitionToClosedState();
            assertThat(f.call(true, "refresh").block(WAIT)).isEqualTo("initial");
            f.counts(0, 0, 1);
            assertThat(dispatches).hasValue(2);
            assertThat(authCalls).hasValue(3);
            clock.advanceTimeBy(Duration.ofSeconds(2));
            f.counts(0, 0, 1);
            assertThat(dispatches).hasValue(3);
            assertThat(authCalls).hasValue(4);
            // The completed stale caller cannot cancel this bounded hidden source.
            clock.advanceTimeBy(Duration.ofSeconds(3));
            f.counts(0, 0, 0);
            assertThat(cancellations).hasValue(1);
            assertThat(f.metric(".refreshes", "failure")).isEqualTo(2);
            assertThat(f.metric(".loads", "success")).isEqualTo(1);
            assertThat(operators.applied).containsExactly(
                    "retry", "rate", "circuit", "bulkhead",
                    "retry", "rate", "circuit", "bulkhead",
                    "retry", "rate", "circuit", "bulkhead");
            f.diagnostics.size(3);
            assertThat(f.diagnostics.events).extracting(HttpClientObserverEvent::getCacheOutcome)
                    .containsExactly(HttpClientCacheOutcome.MISS_LOADER, HttpClientCacheOutcome.STALE_HIT,
                            HttpClientCacheOutcome.STALE_HIT);
        } finally { VirtualTimeScheduler.reset(); }
    }

    @ParameterizedTest
    @EnumSource(BodyEnd.class)
    void bodyPhaseTerminalReleasesOnlyItsOwnersAndKeepsFinalResponseEvidence(BodyEnd end) {
        var clock = VirtualTimeScheduler.getOrSet();
        var config = config(true);
        config.setLogicalCallTimeoutMs(end == BodyEnd.LOGICAL_TIMEOUT ? 1_000 : 10_000);
        AtomicInteger bodySubscriptions = new AtomicInteger();
        AtomicInteger cancellations = new AtomicInteger();
        var body = Flux.defer(() -> {
            bodySubscriptions.incrementAndGet();
            return Flux.concat(Mono.just(DefaultDataBufferFactory.sharedInstance.wrap(new byte[]{65})),
                    Mono.<org.springframework.core.io.buffer.DataBuffer>never())
                    .doOnCancel(cancellations::incrementAndGet);
        });
        try (Fixture f = new Fixture(config, new Operators(), null, web(request ->
                Mono.just(ClientResponse.create(HttpStatus.OK).header("X-Phase", "body").body(body).build())))) {
            var first = f.call(true, "body").toFuture();
            f.counts(1, 1, 0);
            assertThat(bodySubscriptions).hasValue(1);
            if (end == BodyEnd.CANCEL) { first.cancel(true); }
            else { clock.advanceTimeBy(Duration.ofSeconds(2)); }
            Class<? extends Throwable> type = switch (end) {
                case LOGICAL_TIMEOUT -> LogicalCallTimeoutException.class;
                case CANCEL -> CancellationException.class;
            };
            assertThat(first).isDone();
            assertThatThrownBy(first::join).satisfies(error -> assertThat(cause(error, type)).isNotNull());
            f.counts(0, 0, 0);
            assertThat(cancellations).hasValue(1);
            f.diagnostics.terminal(0, type, 1, 200, url(true, "body"),
                    end == BodyEnd.CANCEL ? null : HttpClientFailureStage.RESPONSE_BODY,
                    Map.of("X-Phase", List.of("body")));
            clock.advanceTimeBy(Duration.ofDays(1));
            f.diagnostics.size(1);
            assertThat(f.diagnostics.cancelled).hasValue(end == BodyEnd.CANCEL ? 1 : 0);
        } finally { VirtualTimeScheduler.reset(); }
    }

    @Test
    void disabledDeadlinesLeaveHungWorkOccupyingCapacityUntilExplicitCancellation() {
        var clock = VirtualTimeScheduler.getOrSet();
        var config = config(false);
        config.setRequestTimeoutMs(0);
        config.setLogicalCallTimeoutMs(0);
        try (Fixture f = new Fixture(config, new Operators(), null, web(request -> Mono.never()))) {
            var hung = f.call(false, "hung").toFuture();
            clock.advanceTimeBy(Duration.ofDays(30));
            assertThat(hung).isNotDone();
            f.counts(1, 1, 0);
            StepVerifier.create(f.call(false, "other")).expectError(CacheLoadAdmission.Rejected.class).verify(WAIT);
            hung.cancel(true);
            f.counts(0, 0, 0);
            f.diagnostics.terminal(1, CancellationException.class, 1, null, url(false, "hung"), null, Map.of());
            f.diagnostics.size(2);
        } finally { VirtualTimeScheduler.reset(); }
    }

    @Test
    void warmHitStillAuthorizesAndAuthRejectionDoesNotConsumeLoadOrOperatorCapacity() {
        AtomicBoolean reject = new AtomicBoolean();
        AtomicInteger authCalls = new AtomicInteger();
        AuthProvider auth = request -> {
            authCalls.incrementAndGet();
            return reject.get() ? Mono.error(new IllegalStateException("denied")) : Mono.just(AuthContext.empty());
        };
        var config = config(true);
        selectOperators(config);
        Operators operators = new Operators();
        AtomicInteger dispatches = new AtomicInteger();
        try (Fixture f = new Fixture(config, operators, auth, web(request -> {
            dispatches.incrementAndGet();
            return Mono.just(ok("warm"));
        }))) {
            assertThat(f.call(true, "auth").block(WAIT)).isEqualTo("warm");
            var before = operators.history();
            reject.set(true);
            StepVerifier.create(f.call(true, "auth")).expectError(AuthProviderException.class).verify(WAIT);
            f.diagnostics.terminal(1, AuthProviderException.class, 0, null, null, null, Map.of());
            f.counts(0, 0, 0);
            assertThat(operators.history()).isEqualTo(before);
            reject.set(false);
            assertThat(f.call(true, "auth").block(WAIT)).isEqualTo("warm");
            assertThat(authCalls).hasValue(3);
            assertThat(dispatches).hasValue(1);
            f.diagnostics.size(3);
        }
    }

    @Test
    void authReplayAndOuterRetryRevalidateIdentityAndNeverReuseAuthVisibleByteMutations() {
        var clock = VirtualTimeScheduler.getOrSet();
        AtomicInteger authCalls = new AtomicInteger();
        AtomicInteger invalidations = new AtomicInteger();
        InvalidatableAuthProvider auth = new InvalidatableAuthProvider() {
            @Override public Mono<AuthContext> getAuth(AuthRequest request) {
                int call = authCalls.incrementAndGet();
                assertThat((byte[]) request.requestBody()).isEqualTo("body".getBytes(StandardCharsets.UTF_8));
                ((byte[]) request.requestBody())[0] = 90;
                return Mono.just(AuthContext.builder().header(HttpHeaders.AUTHORIZATION,
                        call == 1 ? "Bearer A" : "Bearer B").build());
            }
            @Override public Mono<Void> invalidate() { invalidations.incrementAndGet(); return Mono.empty(); }
        };
        var config = config(true);
        config.getCache().getPolicies().get(NAME).setVaryByHeaders(List.of(HttpHeaders.AUTHORIZATION));
        config.getDefaultHeaders().put(HttpHeaders.AUTHORIZATION, "Bearer declarative");
        selectOperators(config);
        AtomicInteger dispatches = new AtomicInteger();
        List<String> credentials = new CopyOnWriteArrayList<>();
        try (Fixture f = new Fixture(config, new Operators(), auth, web(request -> {
            int call = dispatches.incrementAndGet();
            credentials.add(request.headers().getFirst(HttpHeaders.AUTHORIZATION));
            assertThat((byte[]) request.attribute(AuthRequest.REQUEST_RAW_BODY_ATTRIBUTE).orElseThrow())
                    .isEqualTo("body".getBytes(StandardCharsets.UTF_8));
            return Mono.just(call == 1 ? ClientResponse.create(HttpStatus.UNAUTHORIZED).header("X-Prior", "401").build()
                    : call == 2 ? ClientResponse.create(HttpStatus.SERVICE_UNAVAILABLE).header("X-Prior", "503").build()
                    : ok("identity-B"));
        }))) {
            var leader = f.call(true, "identity").toFuture();
            var waiter = f.call(true, "identity").toFuture();
            // Different finalized identities must not join the existing flight.
            assertThat(waiter).isDone();
            assertThatThrownBy(waiter::join).hasCauseInstanceOf(CacheLoadAdmission.Rejected.class);
            f.counts(1, 1, 0);
            clock.advanceTimeBy(Duration.ofSeconds(2));
            assertThat(leader).isDone();
            assertThat(leader.join()).isEqualTo("identity-B");
            f.counts(0, 0, 0);
            assertThat(credentials).containsExactly("Bearer A", "Bearer B", "Bearer B");
            assertThat(invalidations).hasValue(1);
            assertThat(authCalls).hasValue(4);
            assertThat(f.manager.snapshot().currentSize()).isZero();
            f.diagnostics.terminal(1, null, 2, 200, url(true, "identity"), null, Map.of("X-Final", List.of("yes")));
            assertThat(f.call(true, "identity").block(WAIT)).isEqualTo("identity-B");
            assertThat(f.call(true, "identity").block(WAIT)).isEqualTo("identity-B");
            assertThat(dispatches).hasValue(4);
            assertThat(f.manager.snapshot().currentSize()).isEqualTo(1);
            f.diagnostics.size(4);
        } finally { VirtualTimeScheduler.reset(); }
    }

    @Test
    void preDispatchAuthFailureAfterClassifiedRetryClearsPriorTerminalEvidence() {
        var clock = VirtualTimeScheduler.getOrSet();
        AtomicInteger authCalls = new AtomicInteger();
        AuthProvider auth = request -> authCalls.incrementAndGet() == 1 ? Mono.just(AuthContext.empty())
                : Mono.error(new IllegalStateException("token", io.netty.handler.timeout.ReadTimeoutException.INSTANCE));
        var config = config(true);
        selectOperators(config);
        AtomicInteger dispatches = new AtomicInteger();
        Operators operators = new Operators();
        try (Fixture f = new Fixture(config, operators, auth, web(request -> {
            dispatches.incrementAndGet();
            return Mono.just(ClientResponse.create(HttpStatus.OK).header("X-Prior", "body-timeout")
                    .body(Flux.error(io.netty.handler.timeout.ReadTimeoutException.INSTANCE)).build());
        }))) {
            var call = f.call(true, "failed-auth").toFuture();
            // The classified response-body failure has entered the real Retry backoff.
            assertThat(dispatches).hasValue(1);
            assertThat(operators.lastRetryFailure.get()).isNotNull();
            assertThat(HttpClientFailureStage.from(operators.lastRetryFailure.get(), 200, true))
                    .isEqualTo(HttpClientFailureStage.RESPONSE_BODY);
            f.counts(1, 1, 0);
            clock.advanceTimeBy(Duration.ofSeconds(2));
            assertThat(call).isDone();
            assertThatThrownBy(call::join).hasCauseInstanceOf(AuthProviderException.class);
            f.diagnostics.terminal(0, AuthProviderException.class, 2, null, null, null, Map.of());
            assertThat(dispatches).hasValue(1);
            f.counts(0, 0, 0);
            f.diagnostics.size(1);
        } finally { VirtualTimeScheduler.reset(); }
    }

    @ParameterizedTest
    @ValueSource(ints = {307, 308})
    void bodyPreservingRedirectUsesOneSlotAndTwoWireBodies(int redirect) throws Exception {
        List<String> bodies = new CopyOnWriteArrayList<>();
        List<String> targets = new CopyOnWriteArrayList<>();
        CountDownLatch arrived = new CountDownLatch(1);
        Sinks.One<String> finish = Sinks.one();
        AtomicInteger writes = new AtomicInteger();
        var server = HttpServer.create().host("127.0.0.1").port(0).handle((request, response) ->
                request.receive().aggregate().asString().flatMap(body -> {
                    bodies.add(body);
                    targets.add(request.uri());
                    if (!request.uri().equals("/final")) {
                        return response.status(redirect).header(HttpHeaders.LOCATION, "/final").send().then();
                    }
                    arrived.countDown();
                    return response.header(HttpHeaders.CONTENT_TYPE, "text/plain").header("X-Final", "yes")
                            .sendString(finish.asMono()).then();
                })).bindNow();
        var config = config(true);
        config.setBaseUrl("http://127.0.0.1:" + server.port());
        config.setFollowRedirects(true);
        var connection = reactor.netty.resources.ConnectionProvider.newConnection();
        try (Fixture f = new Fixture(config, new Operators(), request -> {
            ((byte[]) request.requestBody())[0] = 90;
            return Mono.just(AuthContext.empty());
        }, WebClient.builder().clientConnector(new ReactorClientHttpConnector(HttpClient.create(connection)
                .disableRetry(true).followRedirect(true).doOnRequest((request, channel) -> writes.incrementAndGet()))))) {
            var first = f.call(true, "redirect").toFuture();
            assertThat(arrived.await(10, TimeUnit.SECONDS)).isTrue();
            var waiter = f.call(true, "redirect").toFuture();
            f.counts(2, 1, 0);
            StepVerifier.create(f.call(true, "other")).expectError(CacheLoadAdmission.Rejected.class).verify(WAIT);
            assertThat(writes).hasValue(2);
            assertThat(bodies).containsExactly("body", "body");
            assertThat(targets).containsExactly("/post/redirect", "/final");
            finish.tryEmitValue("redirected").orThrow();
            assertThat(first.get(10, TimeUnit.SECONDS)).isEqualTo("redirected");
            assertThat(waiter.get(10, TimeUnit.SECONDS)).isEqualTo("redirected");
            f.counts(0, 0, 0);
            assertThat(f.call(true, "redirect").block(WAIT)).isEqualTo("redirected");
            assertThat(writes).hasValue(2);
            assertThat(f.metric(".loads", "success")).isEqualTo(1);
            f.diagnostics.terminal(f.diagnostics.index(HttpClientCacheOutcome.MISS_LOADER),
                    null, 1, 200, config.getBaseUrl() + "/post/redirect", null,
                    Map.of("X-Final", List.of("yes")));
            f.diagnostics.terminal(f.diagnostics.index(HttpClientCacheOutcome.COALESCED_WAITER),
                    null, 0, null, null, null, Map.of());
            f.diagnostics.size(4);
        } finally {
            server.disposeNow();
            connection.disposeLater().block(WAIT);
        }
    }

    @Test
    void finalizedTargetChangesAcrossRetryReleaseCapacityWithoutPublishingTheWrongKey() {
        var clock = VirtualTimeScheduler.getOrSet();
        var config = config(true);
        selectOperators(config);
        AtomicReference<String> region = new AtomicReference<>("A");
        List<String> targets = new CopyOnWriteArrayList<>();
        var builder = web(request -> {
            targets.add(request.url().getQuery());
            return Mono.just(targets.size() == 1
                    ? ClientResponse.create(HttpStatus.SERVICE_UNAVAILABLE).build() : ok("region-B"));
        }).filter((request, next) -> next.exchange(ClientRequest.from(request)
                .url(java.net.URI.create(request.url() + "?region=" + region.get())).build()));
        try (Fixture f = new Fixture(config, new Operators(), null, builder)) {
            var call = f.call(true, "target").toFuture();
            f.counts(1, 1, 0);
            region.set("B");
            clock.advanceTimeBy(Duration.ofSeconds(2));
            assertThat(call).isDone();
            assertThat(call.join()).isEqualTo("region-B");
            f.counts(0, 0, 0);
            assertThat(f.manager.snapshot().currentSize()).isZero();
            f.diagnostics.terminal(0, null, 2, 200, url(true, "target") + "?region=B", null,
                    Map.of("X-Final", List.of("yes")));
            assertThat(f.call(true, "target").block(WAIT)).isEqualTo("region-B");
            assertThat(f.call(true, "target").block(WAIT)).isEqualTo("region-B");
            assertThat(targets).containsExactly("region=A", "region=B", "region=B");
            f.diagnostics.size(3);
        } finally { VirtualTimeScheduler.reset(); }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void byteStorageBypassIsSuccessfulWorkAndReleasesCapacity(boolean single) {
        var config = config(single);
        config.getCache().getPolicies().get(NAME).setMaximumTotalDecodedResponseBytes(4L);
        selectOperators(config);
        AtomicInteger dispatches = new AtomicInteger();
        try (Fixture f = new Fixture(config, new Operators(), null, web(request -> {
            dispatches.incrementAndGet();
            return Mono.just(ok("over-budget"));
        }))) {
            for (int i = 0; i < 2; i++) {
                assertThat(f.call(true, "large").block(WAIT)).isEqualTo("over-budget");
                f.counts(0, 0, 0);
                assertThat(f.manager.snapshot().currentSize()).isZero();
                f.diagnostics.terminal(i, null, 1, 200, url(true, "large"), null,
                        Map.of("X-Final", List.of("yes")));
            }
            assertThat(dispatches).hasValue(2);
            assertThat(f.metric(".loads", "success")).isEqualTo(2);
            assertThat(f.metric(".admissions", LocalResponseCacheMetrics.AdmissionOutcome.BYPASSED_OVER_BUDGET.tagValue()))
                    .isEqualTo(2);
            f.diagnostics.size(2);
        }
    }

    private static ReactiveHttpClientProperties.ClientConfig config(boolean single) {
        var config = new ReactiveHttpClientProperties.ClientConfig();
        config.setBaseUrl("http://composition.example.invalid");
        config.setRequestTimeoutMs(0);
        var policy = new ReactiveHttpClientProperties.CachePolicyConfig();
        policy.setTtlMs(60_000L);
        policy.setMaximumSize(100L);
        policy.setSingleFlight(single);
        policy.setSharedResponse(true);
        policy.setVaryByParameters(List.of("payload"));
        var work = new ReactiveHttpClientProperties.CacheWorkConfig();
        work.setMaximumConcurrentCallers(3L);
        work.setMaximumConcurrentLoads(1L);
        policy.setWork(work);
        config.getCache().getPolicies().put(NAME, policy);
        return config;
    }

    private static void selectOperators(ReactiveHttpClientProperties.ClientConfig config) {
        config.getResilience().setEnabled(true);
        config.getResilience().setRetry(NAME);
        config.getResilience().setRateLimiter(NAME);
        config.getResilience().setCircuitBreaker(NAME);
        config.getResilience().setBulkhead(NAME);
        config.getResilience().setRetryMethods(Set.of("GET", "POST"));
    }

    private static WebClient.Builder web(ExchangeFunction exchange) {
        return WebClient.builder().exchangeFunction(exchange);
    }

    private static ClientResponse ok(String value) {
        return ClientResponse.create(HttpStatus.OK).header(HttpHeaders.CONTENT_TYPE, "text/plain")
                .header("X-Final", "yes").body(value).build();
    }

    private static String url(boolean post, String id) {
        return "http://composition.example.invalid/" + (post ? "post/" : "get/") + id;
    }

    private static Throwable cause(Throwable error, Class<? extends Throwable> type) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (type.isInstance(current)) { return current; }
        }
        return null;
    }

    @LogHttpExchange(logger = Diagnostics.class)
    interface Client {
        @GET("/get/{id}") @CacheResponse(NAME)
        Mono<String> get(@PathVar("id") String id, @CacheKey("payload") String partition);
        @POST("/post/{id}") @CacheResponse(value = NAME, semanticRead = true) @IdempotencyKey
        Mono<String> post(@PathVar("id") String id, @Body @CacheKey("payload") String body,
                          @HeaderParam(HttpHeaders.CONTENT_TYPE) String contentType);
    }

    interface UnsafeClient {
        @POST("/unsafe") @CacheResponse(value = NAME, semanticRead = true)
        Mono<String> post(@Body @CacheKey("payload") String body);
    }

    interface StreamingClient {
        @POST("/stream") @CacheResponse(value = NAME, semanticRead = true) @IdempotencyKey
        Mono<String> stream(@Body @CacheKey("payload") Flux<String> body);
    }

    private static final class Fixture implements AutoCloseable {
        final StaticApplicationContext context = new StaticApplicationContext();
        final SimpleMeterRegistry meters = new SimpleMeterRegistry();
        final Diagnostics diagnostics = new Diagnostics();
        final AtomicLong ticker = new AtomicLong();
        final LocalResponseCacheManager manager;
        final Client client;

        Fixture(ReactiveHttpClientProperties.ClientConfig config, Operators operators,
                AuthProvider auth, WebClient.Builder builder) {
            context.getBeanFactory().registerSingleton("diagnostics", diagnostics);
            context.refresh();
            var metadata = new MethodMetadataCache();
            if (auth != null) {
                config.setAuthProvider("test-auth");
                builder.filter(new OutboundAuthFilter(NAME, auth));
            }
            var observability = new ReactiveHttpClientProperties.ObservabilityConfig();
            observability.getCache().setEnabled(true);
            manager = LocalResponseCacheManager.createForClient(Client.class, NAME, metadata, config,
                    getClass().getClassLoader(), ticker::get, reactor.core.scheduler.Schedulers.parallel(),
                    LocalResponseCacheMetrics.enabled(meters, NAME), true);
            builder.baseUrl(config.getBaseUrl()).filter(ReactiveClientInvocationHandler.finalRequestObservationFilter());
            var handler = new ReactiveClientInvocationHandler(builder.build(), metadata, new RequestArgumentResolver(),
                    new DefaultErrorDecoder(), config, NAME, Client.class, context, operators, TestJsonCodecs.jsonCodec(),
                    observability, manager, auth, config.getBaseUrl());
            client = (Client) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{Client.class}, handler);
        }

        Mono<String> call(boolean post, String id) {
            return post ? client.post(id, "body", "text/plain") : client.get(id, "body");
        }

        void counts(int callers, int loads, int refreshes) {
            assertThat(manager.callerAdmission().active(NAME)).isEqualTo(callers);
            assertThat(manager.activeLoadsForTesting(NAME)).isEqualTo(loads);
            assertThat(manager.activeRefreshesForTesting(NAME)).isEqualTo(refreshes);
        }

        double metric(String suffix, String outcome) {
            return meters.find(LocalResponseCacheMetrics.PREFIX + suffix).tag("outcome", outcome)
                    .counters().stream().mapToDouble(io.micrometer.core.instrument.Counter::count).sum();
        }

        @Override public void close() { manager.close(); context.close(); meters.close(); }
    }

    private static final class Diagnostics implements HttpClientObserver, ReactiveHttpClientLifecycleHook, HttpExchangeLogger {
        final List<HttpClientObserverEvent> events = new CopyOnWriteArrayList<>();
        final List<ReactiveHttpClientLifecycleContext> lifecycle = new CopyOnWriteArrayList<>();
        final List<HttpExchangeLogContext> logs = new CopyOnWriteArrayList<>();
        final AtomicInteger cancelled = new AtomicInteger();
        @Override public void record(HttpClientObserverEvent event) { events.add(event); }
        @Override public void log(HttpExchangeLogContext log) { logs.add(log); }
        @Override public void onSuccess(ReactiveHttpClientLifecycleContext event) { lifecycle.add(event); }
        @Override public void onError(ReactiveHttpClientLifecycleContext event) { lifecycle.add(event); }
        @Override public void onCancel(ReactiveHttpClientLifecycleContext event) { cancelled.incrementAndGet(); lifecycle.add(event); }

        void size(int count) {
            assertThat(events).hasSize(count);
            assertThat(lifecycle).hasSize(count);
            assertThat(logs).hasSize(count);
        }

        int index(HttpClientCacheOutcome outcome) {
            var matches = events.stream().filter(event -> event.getCacheOutcome() == outcome).toList();
            assertThat(matches).hasSize(1);
            return events.indexOf(matches.getFirst());
        }

        void terminal(int index, Class<? extends Throwable> error, int attempts, Integer status, String url,
                      HttpClientFailureStage stage, Map<String, List<String>> responseHeaders) {
            var event = events.get(index);
            var hook = lifecycle.get(index);
            var log = logs.get(index);
            if (error == null) { assertThat(event.getError()).isNull(); }
            else { assertThat(cause(event.getError(), error)).isNotNull(); }
            assertThat(hook.error()).isSameAs(event.getError());
            assertThat(log.error()).isSameAs(event.getError());
            assertThat(event.getAttemptCount()).isEqualTo(attempts);
            assertThat(hook.attemptNumber()).isEqualTo(attempts);
            assertThat(log.subscriptionAttemptCount()).isEqualTo(attempts);
            assertThat(event.getStatusCode()).isEqualTo(status);
            assertThat(hook.statusCode()).isEqualTo(status);
            assertThat(log.responseStatus()).isEqualTo(status);
            assertThat(event.getRequestUrl()).isEqualTo(url);
            assertThat(hook.requestUrl()).isEqualTo(url == null ? null : java.net.URI.create(url));
            assertThat(log.requestUrl()).isEqualTo(hook.requestUrl());
            assertThat(event.getFailureStage()).isEqualTo(stage);
            assertThat(hook.failureStage()).isEqualTo(stage);
            assertThat(log.failureStage()).isEqualTo(stage);
            assertThat(log.responseHeaders()).containsAllEntriesOf(responseHeaders);
            if (responseHeaders.isEmpty()) { assertThat(log.responseHeaders()).isEmpty(); }
            else { assertThat(log.responseHeaders()).doesNotContainKey("X-Prior"); }
            if (url == null) {
                assertThat(event.getRequestHeaders()).isEmpty();
                // Logs retain prepared arguments, including the generated idempotency key, not dispatch evidence.
                assertThat(log.requestHeaders().keySet()).isSubsetOf(HttpHeaders.CONTENT_TYPE, "Idempotency-Key");
            } else {
                assertThat(log.requestHeaders()).isEqualTo(event.getRequestHeaders());
            }
        }
    }

    private static final class Operators extends Resilience4jOperatorApplier {
        final io.github.resilience4j.retry.Retry retry;
        final CircuitBreaker circuit;
        final Bulkhead bulkhead;
        final RateLimiter rate;
        final List<String> applied = new CopyOnWriteArrayList<>();
        final List<String> subscribed = new CopyOnWriteArrayList<>();
        final AtomicReference<Throwable> lastRetryFailure = new AtomicReference<>();

        Operators() {
            this(CircuitBreakerRegistry.ofDefaults(),
                    RetryRegistry.of(RetryConfig.custom().maxAttempts(2).waitDuration(Duration.ofSeconds(2)).build()),
                    BulkheadRegistry.of(BulkheadConfig.custom().maxConcurrentCalls(1).maxWaitDuration(Duration.ZERO).build()),
                    RateLimiterRegistry.of(RateLimiterConfig.custom().limitForPeriod(100)
                            .limitRefreshPeriod(Duration.ofDays(1)).timeoutDuration(Duration.ZERO).build()));
        }
        private Operators(CircuitBreakerRegistry circuits, RetryRegistry retries, BulkheadRegistry bulkheads,
                          RateLimiterRegistry rates) {
            super(circuits, retries, bulkheads, rates);
            retry = retries.retry(NAME);
            retry.getEventPublisher().onRetry(event -> lastRetryFailure.set(event.getLastThrowable()));
            circuit = circuits.circuitBreaker(NAME);
            bulkhead = bulkheads.bulkhead(NAME);
            rate = rates.rateLimiter(NAME);
        }
        private <T> Mono<T> record(String operator, Mono<T> source) {
            applied.add(operator);
            return Mono.defer(() -> { subscribed.add(operator); return source; });
        }
        @Override public <T> Mono<T> applyRetry(Mono<T> source, String name) {
            return record("retry", super.applyRetry(source, name));
        }
        @Override public <T> Mono<T> applyRateLimiter(Mono<T> source, String name) {
            return record("rate", super.applyRateLimiter(source, name));
        }
        @Override public <T> Mono<T> applyCircuitBreaker(Mono<T> source, String name) {
            return record("circuit", super.applyCircuitBreaker(source, name));
        }
        @Override public <T> Mono<T> applyBulkhead(Mono<T> source, String name) {
            return record("bulkhead", super.applyBulkhead(source, name));
        }
        List<Object> history() {
            return List.of(List.copyOf(applied), List.copyOf(subscribed), circuit.getMetrics().getNumberOfBufferedCalls(),
                    circuit.getMetrics().getNumberOfNotPermittedCalls(), rate.getMetrics().getAvailablePermissions(),
                    bulkhead.getMetrics().getAvailableConcurrentCalls(), retry.getMetrics().getNumberOfTotalCalls());
        }
    }
}
