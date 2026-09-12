package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.annotation.*;
import io.github.huynhngochuyhoang.httpstarter.auth.AuthContext;
import io.github.huynhngochuyhoang.httpstarter.auth.AuthRequest;
import io.github.huynhngochuyhoang.httpstarter.auth.InvalidatableAuthProvider;
import io.github.huynhngochuyhoang.httpstarter.auth.OutboundAuthFilter;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import io.github.huynhngochuyhoang.httpstarter.exception.AuthProviderException;
import io.github.huynhngochuyhoang.httpstarter.exception.LogicalCallTimeoutException;
import io.github.huynhngochuyhoang.httpstarter.filter.CorrelationIdWebFilter;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientCacheOutcome;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientFailureStage;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientObserver;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientObserverEvent;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;
import reactor.core.publisher.Sinks;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.server.HttpServer;
import reactor.netty.resources.ConnectionProvider;
import reactor.test.StepVerifier;
import reactor.test.scheduler.VirtualTimeScheduler;
import reactor.util.context.Context;
import reactor.util.context.ContextView;

import java.lang.reflect.Proxy;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Timeout(30)
class InboundContextCompositionContractTest {
    private static final String NAME = "context-composition";
    private static final String BASE = "http://context.example.invalid";
    private static final Duration WAIT = Duration.ofSeconds(10);

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void coldResubscriptionsUseEachSnapshotAndExplicitOutboundPrecedence(boolean explicit) {
        try (Fixture f = new Fixture(false, false)) {
            Mono<String> cold = f.client.uncached("cold", "scope-A",
                    explicit ? "outbound-correlation" : null, explicit ? "outbound-key" : null);
            for (String caller : List.of("first", "second")) {
                var snapshot = snapshot(caller, "scope-A");
                assertThat(cold.contextWrite(ctx -> snapshot.writeTo(
                        RequestContext.withIdempotencyKey(ctx, "context-key-" + caller))).block(WAIT)).isEqualTo("value");
                int index = f.wire.size() - 1;
                f.records.terminal(index, snapshot, null, 1, 200, "/uncached/cold", null);
                assertThat(f.wire.get(index).headers().getFirst("X-Correlation-Id"))
                        .isEqualTo(explicit ? "outbound-correlation" : "correlation-" + caller);
                String expectedKey = explicit ? "outbound-key" : "context-key-" + caller;
                assertThat(f.wire.get(index).headers().getFirst("Idempotency-Key")).isEqualTo(expectedKey);
                assertThat(f.records.terminals.get(index).headers()).containsEntry("Idempotency-Key", expectedKey);
                assertThat(f.wire.get(index).headers().headerNames()).doesNotContain("x-caller", "Authorization", "x-absent");
            }
            assertThat(f.auth.callers).containsExactly("first", "second");
            f.records.size(2);
            f.assertPrivateMeterTags();
        }
    }

    @Test
    void freshAndStaleHitsAuthorizeEachCallerAndRefreshKeepsItsTriggerContext() throws Exception {
        try (Fixture f = new Fixture(true, true)) {
            var first = snapshot("first", "scope-A");
            var second = snapshot("second", "scope-A");
            var stale = snapshot("stale", "scope-A");
            var other = snapshot("other", "scope-B");
            assertThat(f.cached("entry", first).block(WAIT)).isEqualTo("value");
            assertThat(f.cached("entry", second).block(WAIT)).isEqualTo("value");
            f.records.terminal(0, first, HttpClientCacheOutcome.MISS_LOADER, 1, 200, "/cached/entry", null);
            f.records.terminal(1, second, HttpClientCacheOutcome.FRESH_HIT, 0, null, null, null);
            Gate refresh = new Gate();
            f.source = request -> refresh.publisher();
            f.ticker.set(TimeUnit.SECONDS.toNanos(2));
            assertThat(f.cached("entry", stale).block(WAIT)).isEqualTo("value");
            f.clock.advanceTimeBy(Duration.ZERO);
            refresh.attached.get(10, TimeUnit.SECONDS);
            assertThat(f.manager.activeRefreshesForTesting(NAME)).isEqualTo(1);
            f.records.terminal(2, stale, HttpClientCacheOutcome.STALE_HIT, 0, null, null, null);
            assertThat(f.auth.callers).containsExactly("first", "second", "stale");
            assertThat(f.wire).hasSize(2);
            assertThat(f.wire.getLast().headers().getFirst("X-Correlation-Id")).isEqualTo("correlation-stale");
            refresh.response.tryEmitValue(ok("refreshed")).orThrow();
            refresh.released.get(10, TimeUnit.SECONDS);
            assertThat(f.manager.activeRefreshesForTesting(NAME)).isZero();
            f.records.size(3);
            assertThat(f.cached("entry", second).block(WAIT)).isEqualTo("refreshed");
            f.records.terminal(3, second, HttpClientCacheOutcome.FRESH_HIT, 0, null, null, null);
            f.source = request -> Mono.just(ok("other-value"));
            assertThat(f.cached("entry", other).block(WAIT)).isEqualTo("other-value");
            f.records.terminal(4, other, HttpClientCacheOutcome.MISS_LOADER, 1, 200, "/cached/entry", null);
            assertThat(f.wire).hasSize(3);
            f.records.size(5);
            f.assertIdle();
            f.assertPrivateMeterTags();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void detachedLeaderCannotDonateItsContextOrLaterRetryEvidenceToWaiter(boolean timeout) throws Exception {
        try (Fixture f = new Fixture(true, false)) {
            f.config.setLogicalCallTimeoutMs(1_000);
            f.selectRetry();
            Gate firstAttempt = new Gate();
            Gate secondAttempt = new Gate();
            f.source = request -> f.wire.size() == 1 ? firstAttempt.publisher() : secondAttempt.publisher();
            var leaderSnapshot = snapshot("leader", "scope-A");
            var waiterSnapshot = snapshot("waiter", "scope-A");
            var leader = f.cached("shared", leaderSnapshot).toFuture();
            firstAttempt.attached.get(10, TimeUnit.SECONDS);
            f.clock.advanceTimeBy(Duration.ofMillis(500));
            var waiter = f.cached("shared", waiterSnapshot).toFuture();
            assertThat(f.manager.hasInFlightLoadWithMembersForTesting(2)).isTrue();
            if (timeout) {
                f.clock.advanceTimeBy(Duration.ofMillis(500));
                assertThatThrownBy(leader::join).hasCauseInstanceOf(LogicalCallTimeoutException.class);
            } else { assertThat(leader.cancel(true)).isTrue(); }
            assertThat(firstAttempt.released).isNotDone();
            assertThat(firstAttempt.response.currentSubscriberCount()).isEqualTo(1);
            assertThat(waiter).isNotDone();
            f.records.terminal(0, leaderSnapshot, HttpClientCacheOutcome.MISS_LOADER, 1, null,
                    "/cached/shared", timeout ? LogicalCallTimeoutException.class : CancellationException.class);
            var frozenLeader = f.records.logs.getFirst();
            firstAttempt.response.tryEmitValue(ClientResponse.create(HttpStatus.SERVICE_UNAVAILABLE)
                    .header("X-Prior", "discarded").body("retry").build()).orThrow();
            // Retry fits the later waiter's budget, not the already terminal leader's.
            f.clock.advanceTimeBy(Duration.ofMillis(100));
            secondAttempt.attached.get(10, TimeUnit.SECONDS);
            secondAttempt.response.tryEmitValue(ok("recovered")).orThrow();
            assertThat(waiter.get(10, TimeUnit.SECONDS)).isEqualTo("recovered");
            firstAttempt.released.get(10, TimeUnit.SECONDS);
            secondAttempt.released.get(10, TimeUnit.SECONDS);
            f.records.terminal(1, waiterSnapshot, HttpClientCacheOutcome.COALESCED_WAITER, 0, null, null, null);
            assertThat(f.records.logs.getFirst()).isSameAs(frozenLeader);
            assertThat(frozenLeader.subscriptionAttemptCount()).isEqualTo(1);
            assertThat(f.auth.callers).containsExactly("leader", "waiter", "leader");
            assertThat(f.wire).hasSize(2).allSatisfy(request ->
                    assertThat(request.headers().getFirst("X-Correlation-Id")).isEqualTo("correlation-leader"));
            assertThat(f.records.retries).isEmpty();
            f.clock.advanceTimeBy(Duration.ofSeconds(10));
            f.records.size(2);
            f.assertIdle();
        }
    }

    enum Rejection { DENIED, ABSENT, REDACTED, AMBIGUOUS, MALFORMED_VALUES, INVALID_AUTH_HEADER }

    @ParameterizedTest
    @EnumSource(Rejection.class)
    void warmCacheNeverBypassesPerCallerValidation(Rejection rejection) {
        try (Fixture f = new Fixture(true, false)) {
            assertThat(f.cached("entry", snapshot("warm", "scope-A")).block(WAIT)).isEqualTo("value");
            Context rejected = snapshot("rejected", "scope-A").writeTo(Context.empty());
            if (rejection == Rejection.DENIED) {
                f.auth.resolve = ctx -> Mono.error(new IllegalStateException("synthetic denial"));
            } else if (rejection == Rejection.INVALID_AUTH_HEADER) {
                f.auth.resolve = ctx -> Mono.just(AuthContext.builder().header("X-Tenant", "invalid\r\nvalue").build());
            } else if (rejection == Rejection.AMBIGUOUS) {
                rejected = rejected.put(RequestContext.INBOUND_HEADERS_CONTEXT_KEY,
                        Map.of("x-tenant", List.of("scope-A"), "X-Tenant", List.of("scope-A")));
            } else if (rejection == Rejection.ABSENT) {
                rejected = rejected.put(RequestContext.INBOUND_HEADERS_CONTEXT_KEY, Map.of());
            } else if (rejection == Rejection.REDACTED) {
                rejected = rejected.put(RequestContext.INBOUND_HEADERS_CONTEXT_KEY, Map.of("x-tenant", List.of("[REDACTED]")));
            } else {
                rejected = rejected.put(RequestContext.INBOUND_HEADERS_CONTEXT_KEY, Map.of("x-tenant", List.of(42)));
            }
            Class<? extends Throwable> expected = rejection == Rejection.INVALID_AUTH_HEADER
                    ? IllegalArgumentException.class : AuthProviderException.class;
            StepVerifier.create(f.client.cached("entry", "scope-A").contextWrite(rejected)).expectError(expected).verify(WAIT);
            f.records.size(2);
            var event = f.records.events.get(1);
            assertThat(event.getError()).isInstanceOf(expected);
            assertThat(f.records.terminals.get(1).error()).isSameAs(event.getError());
            assertThat(f.records.logs.get(1).error()).isSameAs(event.getError());
            assertThat(event.getAttemptCount()).isZero();
            assertThat(event.getRequestUrl()).isNull();
            assertThat(event.getStatusCode()).isNull();
            assertThat(event.getFailureStage()).isNull();
            assertThat(f.records.logs.get(1).responseHeaders()).isEmpty();
            assertThat(f.wire).hasSize(1);
            f.assertIdle();
            f.auth.resolve = Auth::authorized;
            assertThat(f.cached("entry", snapshot("replacement", "scope-A")).block(WAIT)).isEqualTo("value");
            assertThat(f.wire).hasSize(1);
            f.records.size(3);
        }
    }

    @Test
    void authReplayIdentityChangeCannotPublishUnderTheOriginalCallersPartition() {
        try (Fixture f = new Fixture(true, false)) {
            var first = snapshot("first", "scope-A");
            f.auth.changeIdentityOnInvalidation = true;
            f.source = request -> Mono.just(f.wire.size() == 1
                    ? ClientResponse.create(HttpStatus.UNAUTHORIZED).header("X-Prior", "unauthorized").build()
                    : ok(request.headers().getFirst("X-Tenant")));
            assertThat(f.cached("replay", first).block(WAIT)).isEqualTo("scope-B");
            assertThat(f.auth.invalidations).hasValue(1);
            assertThat(f.manager.snapshot().currentSize()).isZero();
            f.records.terminal(0, first, HttpClientCacheOutcome.MISS_LOADER, 1, 200, "/cached/replay", null);
            f.auth.changedIdentity = false;
            assertThat(f.cached("replay", snapshot("next", "scope-A")).block(WAIT)).isEqualTo("scope-A");
            assertThat(f.cached("replay", snapshot("hit", "scope-A")).block(WAIT)).isEqualTo("scope-A");
            assertThat(f.wire).extracting(request -> request.headers().getFirst("X-Tenant"))
                    .containsExactly("scope-A", "scope-B", "scope-A");
            f.records.size(3);
            f.assertIdle();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void uncachedRetryAndAuthReplayPreservePreparedKeysAndEachSubscriptionsContext(boolean explicit) {
        try (Fixture f = new Fixture(false, false)) {
            f.selectRetry();
            f.source = request -> Mono.just(switch (f.wire.size() % 3) {
                case 1 -> ClientResponse.create(HttpStatus.UNAUTHORIZED).header("X-Prior", "auth").build();
                case 2 -> ClientResponse.create(HttpStatus.SERVICE_UNAVAILABLE).header("X-Prior", "retry").build();
                default -> ok("recovered");
            });
            Mono<String> cold = f.client.uncached("retry", "scope-A", explicit ? "outbound-correlation" : null,
                    explicit ? "outbound-key" : null);
            for (String caller : List.of("first", "second")) {
                var captured = snapshot(caller, "scope-A");
                var result = cold.contextWrite(ctx -> captured.writeTo(
                        RequestContext.withIdempotencyKey(ctx, "context-key-" + caller))).toFuture();
                assertThat(result).isNotDone();
                f.clock.advanceTimeBy(Duration.ofMillis(100));
                assertThat(result.join()).isEqualTo("recovered");
                int index = f.records.logs.size() - 1;
                f.records.terminal(index, captured, null, 2, 200, "/uncached/retry", null);
                String key = explicit ? "outbound-key" : "context-key-" + caller;
                assertThat(f.wire.subList(index * 3, index * 3 + 3)).allSatisfy(request -> {
                    assertThat(request.headers().getFirst("Idempotency-Key")).isEqualTo(key);
                    assertThat(request.headers().getFirst("X-Correlation-Id"))
                            .isEqualTo(explicit ? "outbound-correlation" : captured.correlationId());
                });
                assertThat(f.records.starts.get(index).headers()).containsEntry("Idempotency-Key", key);
                assertThat(f.records.retries.get(index).headers()).containsEntry("Idempotency-Key", key);
                assertThat(f.records.terminals.get(index).headers()).containsEntry("Idempotency-Key", key);
            }
            f.records.size(2);
            assertThat(f.records.starts).hasSize(2);
            assertThat(f.records.retries).hasSize(2);
            assertThat(f.auth.callers).containsExactly("first", "first", "first", "second", "second", "second");
            assertThat(f.auth.invalidations).hasValue(2);
            assertThat(f.wire).hasSize(6);
        }
    }

    @Test
    void bodyDeadlineKeepsTheCurrentCallersHeadersAndResponseBodyEvidence() throws Exception {
        try (Fixture f = new Fixture(true, false)) {
            f.config.setLogicalCallTimeoutMs(1_000);
            Sinks.Many<DataBuffer> body = Sinks.many().unicast().onBackpressureBuffer();
            CompletableFuture<SignalType> released = new CompletableFuture<>();
            AtomicInteger bodyTerminals = new AtomicInteger();
            f.source = request -> Mono.just(ClientResponse.create(HttpStatus.OK).header("X-Final", "yes")
                    .body(body.asFlux().doFinally(signal -> {
                        bodyTerminals.incrementAndGet();
                        released.complete(signal);
                    })).build());
            var captured = snapshot("body", "scope-A");
            var result = f.cached("body", captured).toFuture();
            assertThat(body.currentSubscriberCount()).isEqualTo(1);
            f.clock.advanceTimeBy(Duration.ofSeconds(1));
            assertThatThrownBy(result::join).hasCauseInstanceOf(LogicalCallTimeoutException.class);
            assertThat(released.get(10, TimeUnit.SECONDS)).isEqualTo(SignalType.CANCEL);
            assertThat(bodyTerminals).hasValue(1);
            assertThat(body.currentSubscriberCount()).isZero();
            f.records.terminal(0, captured, HttpClientCacheOutcome.MISS_LOADER, 1, 200, "/cached/body",
                    LogicalCallTimeoutException.class, HttpClientFailureStage.RESPONSE_BODY);
            f.records.size(1);
            f.assertIdle();
        }
    }

    @Test
    void loopbackRedirectPreservesCallerSnapshotAndAFreshHitDoesNotReplay() {
        List<String> requests = new CopyOnWriteArrayList<>();
        List<String> correlations = new CopyOnWriteArrayList<>();
        var server = HttpServer.create().host("127.0.0.1").port(0).handle((request, response) -> {
            requests.add(request.uri());
            correlations.add(request.requestHeaders().get("X-Correlation-Id"));
            if (request.uri().equals("/cached/redirect")) {
                return response.status(307).header(HttpHeaders.LOCATION, "/final").send();
            }
            return response.header(HttpHeaders.CONTENT_TYPE, "text/plain").header("X-Final", "yes")
                    .sendString(Mono.just("redirected"));
        }).bindNow();
        var connections = ConnectionProvider.newConnection();
        String base = "http://127.0.0.1:" + server.port();
        try (Fixture f = new Fixture(true, false, base, WebClient.builder().clientConnector(
                new ReactorClientHttpConnector(HttpClient.create(connections).disableRetry(true).followRedirect(true))))) {
            var first = snapshot("redirect-first", "scope-A");
            var hit = snapshot("redirect-hit", "scope-A");
            assertThat(f.cached("redirect", first).block(WAIT)).isEqualTo("redirected");
            assertThat(f.cached("redirect", hit).block(WAIT)).isEqualTo("redirected");
            f.records.terminal(0, first, HttpClientCacheOutcome.MISS_LOADER, 1, 200, base + "/cached/redirect", null);
            f.records.terminal(1, hit, HttpClientCacheOutcome.FRESH_HIT, 0, null, null, null);
            assertThat(requests).containsExactly("/cached/redirect", "/final");
            assertThat(correlations).containsExactly("correlation-redirect-first", "correlation-redirect-first");
            assertThat(f.auth.callers).containsExactly("redirect-first", "redirect-hit");
            f.records.size(2);
            f.assertIdle();
        } finally {
            connections.disposeLater().block(WAIT);
            server.disposeNow();
        }
    }

    enum Mode { UNCACHED_MONO, UNCACHED_FLUX, CACHE, CACHE_WORK }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void malformedTopLevelReportingContextDoesNotSubscribeOrRetainAdmission(Mode mode) throws Exception {
        try (Fixture f = new Fixture(mode == Mode.CACHE_WORK, false)) {
            Mono<?> call = switch (mode) {
                case UNCACHED_MONO -> f.client.uncached("invalid", "scope-A", null, null);
                case UNCACHED_FLUX -> f.client.stream().collectList();
                case CACHE, CACHE_WORK -> f.client.cached("invalid", "scope-A");
            };
            StepVerifier.create(call.contextWrite(ctx -> ctx.put(RequestContext.INBOUND_HEADERS_CONTEXT_KEY, "not-a-map"))
                    .retry(2)).expectError(ClassCastException.class).verify(WAIT);
            f.records.size(0);
            assertThat(f.records.starts).isEmpty();
            assertThat(f.auth.callers).isEmpty();
            assertThat(f.wire).isEmpty();
            f.assertIdle();
            Gate gate = new Gate();
            f.source = request -> gate.publisher();
            var replacement = f.cached("replacement", snapshot("valid", "scope-A")).toFuture();
            gate.attached.get(10, TimeUnit.SECONDS);
            if (mode == Mode.CACHE_WORK) { assertThat(f.manager.callerAdmission().active(NAME)).isEqualTo(1); }
            replacement.cancel(true);
            assertThat(gate.released.get(10, TimeUnit.SECONDS)).isEqualTo(SignalType.CANCEL);
            assertThat(gate.response.currentSubscriberCount()).isZero();
            f.records.size(1);
            f.assertIdle();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void lateAuthEmissionAfterCancellationOrTimeoutCannotDispatch(boolean timeout) throws Exception {
        try (Fixture f = new Fixture(true, false)) {
            f.config.setLogicalCallTimeoutMs(1_000);
            Sinks.One<AuthContext> auth = Sinks.one();
            CompletableFuture<SignalType> released = new CompletableFuture<>();
            AtomicInteger terminals = new AtomicInteger();
            f.auth.resolve = ctx -> auth.asMono().doFinally(signal -> {
                terminals.incrementAndGet();
                released.complete(signal);
            });
            var caller = snapshot("cancelled", "scope-A");
            var result = f.cached("auth", caller).toFuture();
            assertThat(auth.currentSubscriberCount()).isEqualTo(1);
            assertThat(f.manager.callerAdmission().active(NAME)).isEqualTo(1);
            if (timeout) {
                f.clock.advanceTimeBy(Duration.ofSeconds(1));
                assertThatThrownBy(result::join).hasCauseInstanceOf(LogicalCallTimeoutException.class);
            } else { result.cancel(true); }
            assertThat(released.get(10, TimeUnit.SECONDS)).isEqualTo(SignalType.CANCEL);
            assertThat(terminals).hasValue(1);
            assertThat(auth.currentSubscriberCount()).isZero();
            f.records.terminal(0, caller, null, 0, null, null,
                    timeout ? LogicalCallTimeoutException.class : CancellationException.class);
            f.assertIdle();
            auth.tryEmitValue(AuthContext.empty());
            f.clock.advanceTimeBy(Duration.ofDays(1));
            assertThat(f.wire).isEmpty();
            f.records.size(1);
            f.auth.resolve = Auth::authorized;
            assertThat(f.cached("auth", snapshot("valid", "scope-A")).block(WAIT)).isEqualTo("value");
            f.records.size(2);
            f.assertIdle();
        }
    }

    private static RequestContextSnapshot snapshot(String caller, String tenant) {
        return new RequestContextSnapshot("correlation-" + caller, Map.of(
                "x-caller", List.of(caller), "x-tenant", List.of(tenant), "authorization", List.of("[REDACTED]")));
    }

    private static ClientResponse ok(String value) {
        return ClientResponse.create(HttpStatus.OK).header(HttpHeaders.CONTENT_TYPE, "text/plain")
                .header("X-Final", "yes").body(value).build();
    }

    interface Client {
        @GET("/cached/{id}") @CacheResponse(NAME)
        Mono<String> cached(@PathVar("id") String id, @HeaderParam("X-Tenant") String tenant);
        @GET("/uncached/{id}") @IdempotencyKey
        Mono<String> uncached(@PathVar("id") String id, @HeaderParam("X-Tenant") String tenant,
                              @HeaderParam("X-Correlation-Id") String correlation, @HeaderParam("Idempotency-Key") String key);
        @GET("/stream") Flux<String> stream();
    }

    private static final class Gate {
        final Sinks.One<ClientResponse> response = Sinks.one();
        final CompletableFuture<Void> attached = new CompletableFuture<>();
        final CompletableFuture<SignalType> released = new CompletableFuture<>();
        final AtomicInteger terminals = new AtomicInteger();
        Mono<ClientResponse> publisher() {
            return response.asMono().doOnSubscribe(ignored -> attached.complete(null)).doFinally(signal -> {
                assertThat(terminals.incrementAndGet()).isEqualTo(1);
                released.complete(signal);
            });
        }
    }

    private static final class Auth implements InvalidatableAuthProvider {
        final List<String> callers = new CopyOnWriteArrayList<>();
        final AtomicInteger invalidations = new AtomicInteger();
        Function<ContextView, Mono<AuthContext>> resolve = Auth::authorized;
        boolean changeIdentityOnInvalidation;
        boolean changedIdentity;

        public Mono<AuthContext> getAuth(AuthRequest request) {
            return Mono.deferContextual(ctx -> {
                callers.add(RequestContext.inboundHeader(ctx, "X-Caller").orElse("absent"));
                return changedIdentity ? Mono.just(AuthContext.builder().header("X-Tenant", "scope-B").build()) : resolve.apply(ctx);
            });
        }
        static Mono<AuthContext> authorized(ContextView ctx) {
            String tenant = RequestContext.inboundHeader(ctx, "X-Tenant")
                    .filter(value -> value.equals("scope-A") || value.equals("scope-B"))
                    .orElseThrow(() -> new IllegalStateException("Required fixture scope is absent or invalid"));
            return Mono.just(AuthContext.builder().header("X-Tenant", tenant).build());
        }
        public Mono<Void> invalidate() {
            return Mono.fromRunnable(() -> {
                invalidations.incrementAndGet();
                changedIdentity = changeIdentityOnInvalidation;
            });
        }
    }

    private static final class Fixture implements AutoCloseable {
        final VirtualTimeScheduler clock = VirtualTimeScheduler.getOrSet();
        final AtomicLong ticker = new AtomicLong();
        final ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();
        final SimpleMeterRegistry meters = new SimpleMeterRegistry();
        final StaticApplicationContext context = new StaticApplicationContext();
        final Records records = new Records();
        final Auth auth = new Auth();
        final List<ClientRequest> wire = new CopyOnWriteArrayList<>();
        final LocalResponseCacheManager manager;
        final Client client;
        Function<ClientRequest, Mono<ClientResponse>> source = request -> Mono.just(ok("value"));

        Fixture(boolean work, boolean refresh) {
            this(work, refresh, BASE, null);
        }

        Fixture(boolean work, boolean refresh, String base, WebClient.Builder transport) {
            config.setBaseUrl(base);
            config.setRequestTimeoutMs(0);
            config.setAuthProvider("fixture-auth");
            config.setExchangeLoggingEnabled(true);
            var policy = new ReactiveHttpClientProperties.CachePolicyConfig();
            policy.setTtlMs(60_000L);
            policy.setMaximumSize(16L);
            policy.setSingleFlight(true);
            policy.setVaryByHeaders(List.of("X-Tenant", "Idempotency-Key"));
            if (refresh) { policy.setRefreshAfterMs(1_000L); policy.setRefreshTimeoutMs(5_000L); }
            if (work) {
                var limits = new ReactiveHttpClientProperties.CacheWorkConfig();
                limits.setMaximumConcurrentCallers(2L);
                limits.setMaximumConcurrentLoads(1L);
                if (refresh) { limits.setMaximumConcurrentRefreshes(1L); }
                policy.setWork(limits);
            }
            config.getCache().getPolicies().put(NAME, policy);
            context.getBeanFactory().registerSingleton("records", records);
            context.refresh();
            var metadata = new MethodMetadataCache();
            metadata.validateDeclarativeCachePolicies(Client.class, NAME, config);
            var observability = new ReactiveHttpClientProperties.ObservabilityConfig();
            observability.getCache().setEnabled(true);
            manager = LocalResponseCacheManager.createForClient(Client.class, NAME, metadata, config,
                    getClass().getClassLoader(), ticker::get, clock, LocalResponseCacheMetrics.enabled(meters, NAME), true);
            WebClient.Builder builder = transport != null ? transport : WebClient.builder().exchangeFunction(request -> {
                wire.add(request);
                return source.apply(request);
            });
            WebClient web = builder.baseUrl(base)
                    .filter(CorrelationIdWebFilter.exchangeFilter(new ReactiveHttpClientProperties.CorrelationIdConfig()))
                    .filter(new OutboundAuthFilter(NAME, auth))
                    .filter(ReactiveClientInvocationHandler.finalRequestObservationFilter()).build();
            var retry = RetryRegistry.of(RetryConfig.custom().maxAttempts(2).waitDuration(Duration.ofMillis(100)).build());
            var operators = new Resilience4jOperatorApplier(null, retry, null, null);
            var handler = new ReactiveClientInvocationHandler(web, metadata, new RequestArgumentResolver(),
                    new DefaultErrorDecoder(), config, NAME, Client.class, context, operators, TestJsonCodecs.jsonCodec(),
                    observability, manager, auth, base);
            client = (Client) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Client.class}, handler);
        }

        void selectRetry() {
            config.getResilience().setEnabled(true);
            config.getResilience().setRetry(NAME);
            config.getResilience().setRetryMethods(Set.of("GET"));
        }
        Mono<String> cached(String id, RequestContextSnapshot snapshot) {
            return client.cached(id, RequestContext.inboundHeader(snapshot.writeTo(Context.empty()), "X-Tenant").orElseThrow())
                    .contextWrite(snapshot::writeTo);
        }
        void assertIdle() {
            if (manager.callerAdmission() != null) { assertThat(manager.callerAdmission().active(NAME)).isZero(); }
            assertThat(manager.activeLoadsForTesting(NAME)).isZero();
            assertThat(manager.activeRefreshesForTesting(NAME)).isZero();
            assertThat(manager.workloadSnapshotForTesting().inFlightLoads()).isZero();
        }
        void assertPrivateMeterTags() {
            assertThat(meters.getMeters()).isNotEmpty().allSatisfy(meter ->
                    assertThat(meter.getId().getTags()).allSatisfy(tag -> {
                        assertThat(tag.getKey()).isIn("client.name", "api.name", "cache.policy", "outcome", "cause", "reason", "kind", "result");
                        assertThat(tag.getValue()).doesNotContain("scope-", "correlation-", "x-tenant", "x-caller", "[REDACTED]");
                    }));
        }
        public void close() { manager.close(); context.close(); meters.close(); clock.dispose(); VirtualTimeScheduler.reset(); }
    }

    private static final class Records extends DefaultHttpExchangeLogger implements HttpClientObserver, ReactiveHttpClientLifecycleHook {
        final List<ReactiveHttpClientLifecycleContext> starts = new CopyOnWriteArrayList<>();
        final List<ReactiveHttpClientLifecycleContext> retries = new CopyOnWriteArrayList<>();
        final List<ReactiveHttpClientLifecycleContext> terminals = new CopyOnWriteArrayList<>();
        final List<String> kinds = new CopyOnWriteArrayList<>();
        final List<HttpClientObserverEvent> events = new CopyOnWriteArrayList<>();
        final List<HttpExchangeLogContext> logs = new CopyOnWriteArrayList<>();
        public void onStart(ReactiveHttpClientLifecycleContext event) { starts.add(event); }
        public void onRetryAttempt(ReactiveHttpClientLifecycleContext event) { retries.add(event); }
        public void onSuccess(ReactiveHttpClientLifecycleContext event) { kinds.add("success"); terminals.add(event); }
        public void onError(ReactiveHttpClientLifecycleContext event) { kinds.add("error"); terminals.add(event); }
        public void onCancel(ReactiveHttpClientLifecycleContext event) { kinds.add("cancel"); terminals.add(event); }
        public void record(HttpClientObserverEvent event) { events.add(event); }
        public void log(HttpExchangeLogContext event) { logs.add(event); }

        void size(int count) {
            assertThat(terminals).hasSize(count);
            assertThat(events).hasSize(count);
            assertThat(logs).hasSize(count);
            assertThat(kinds).hasSize(count);
        }
        void terminal(int index, RequestContextSnapshot snapshot, HttpClientCacheOutcome outcome, int attempts,
                      Integer status, String path, Class<? extends Throwable> error) {
            terminal(index, snapshot, outcome, attempts, status, path, error, null);
        }

        void terminal(int index, RequestContextSnapshot snapshot, HttpClientCacheOutcome outcome, int attempts,
                      Integer status, String path, Class<? extends Throwable> error, HttpClientFailureStage stage) {
            var hook = terminals.get(index);
            var event = events.get(index);
            var log = logs.get(index);
            assertThat(log.inboundHeaders()).isEqualTo(snapshot.inboundHeaders());
            assertThat(log.inboundHeaders()).doesNotContainKey("x-absent");
            assertThat(log.inboundHeaders().get("authorization")).containsExactly("[REDACTED]");
            assertThat(event.getError()).isSameAs(hook.error()).isSameAs(log.error());
            if (error == null) { assertThat(event.getError()).isNull(); }
            else { assertThat(event.getError()).isInstanceOf(error); }
            assertThat(kinds.get(index)).isEqualTo(error == null ? "success" : error == CancellationException.class ? "cancel" : "error");
            assertThat(event.getAttemptCount()).isEqualTo(attempts);
            assertThat(hook.attemptNumber()).isEqualTo(attempts);
            assertThat(log.subscriptionAttemptCount()).isEqualTo(attempts);
            assertThat(event.getCacheOutcome()).isEqualTo(outcome);
            assertThat(hook.cacheOutcome()).isEqualTo(outcome);
            assertThat(log.cacheOutcome()).isEqualTo(outcome);
            assertThat(event.getStatusCode()).isEqualTo(status);
            assertThat(hook.statusCode()).isEqualTo(status);
            assertThat(log.responseStatus()).isEqualTo(status);
            String url = path == null ? null : path.startsWith("http:") ? path : BASE + path;
            assertThat(event.getRequestUrl()).isEqualTo(url);
            assertThat(hook.requestUrl()).isEqualTo(url == null ? null : URI.create(url));
            assertThat(log.requestUrl()).isEqualTo(hook.requestUrl());
            assertThat(event.getFailureStage()).isEqualTo(stage);
            assertThat(hook.failureStage()).isEqualTo(stage);
            assertThat(log.failureStage()).isEqualTo(stage);
            assertThat(log.responseHeaders()).doesNotContainKey("X-Prior");
            if (status == null) { assertThat(log.responseHeaders()).isEmpty(); }
            else { assertThat(log.responseHeaders()).containsEntry("X-Final", List.of("yes")); }
            if (path == null) { assertThat(event.getRequestHeaders()).isEmpty(); }
        }
    }
}
