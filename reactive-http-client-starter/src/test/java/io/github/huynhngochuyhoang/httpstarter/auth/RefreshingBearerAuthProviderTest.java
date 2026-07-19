package io.github.huynhngochuyhoang.httpstarter.auth;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.client.ClientRequest;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class RefreshingBearerAuthProviderTest {

    @Test
    void shouldReuseCachedTokenBeforeRefreshWindow() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        Clock fixedClock = Clock.fixed(now, ZoneOffset.UTC);
        AtomicInteger calls = new AtomicInteger();

        RefreshingBearerAuthProvider provider = new RefreshingBearerAuthProvider(
                () -> Mono.fromSupplier(() -> {
                    calls.incrementAndGet();
                    return new AccessToken("token-1", now.plusSeconds(120));
                }),
                Duration.ofSeconds(30),
                Duration.ZERO,
                fixedClock
        );

        Mono<AuthContext> first = provider.getAuth(sampleRequest());
        Mono<AuthContext> second = provider.getAuth(sampleRequest());

        StepVerifier.create(first)
                .assertNext(auth -> assertEquals("Bearer token-1", auth.getHeaders().get("Authorization")))
                .verifyComplete();
        StepVerifier.create(second)
                .assertNext(auth -> assertEquals("Bearer token-1", auth.getHeaders().get("Authorization")))
                .verifyComplete();

        assertEquals(1, calls.get());
    }

    @Test
    void shouldRefreshWhenTokenIsNearExpiry() {
        AtomicInteger calls = new AtomicInteger();
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        TestClock clock = new TestClock(now);

        RefreshingBearerAuthProvider provider = new RefreshingBearerAuthProvider(
                () -> Mono.fromSupplier(() -> {
                    int call = calls.incrementAndGet();
                    if (call == 1) {
                        return new AccessToken("token-old", now.plusSeconds(40));
                    }
                    return new AccessToken("token-new", now.plusSeconds(120));
                }),
                Duration.ofSeconds(30),
                Duration.ZERO,
                clock
        );

        StepVerifier.create(provider.getAuth(sampleRequest()))
                .assertNext(auth -> assertEquals("Bearer token-old", auth.getHeaders().get("Authorization")))
                .verifyComplete();

        clock.set(now.plusSeconds(11));

        StepVerifier.create(provider.getAuth(sampleRequest()))
                .assertNext(auth -> assertEquals("Bearer token-new", auth.getHeaders().get("Authorization")))
                .verifyComplete();

        assertEquals(2, calls.get());
    }

    @Test
    void shouldDeduplicateConcurrentRefreshCalls() {
        AtomicInteger calls = new AtomicInteger();
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        Clock fixedClock = Clock.fixed(now, ZoneOffset.UTC);
        Sinks.One<AccessToken> sink = Sinks.one();

        RefreshingBearerAuthProvider provider = new RefreshingBearerAuthProvider(
                () -> {
                    calls.incrementAndGet();
                    return sink.asMono();
                },
                Duration.ofSeconds(30),
                Duration.ZERO,
                fixedClock
        );

        Mono<AuthContext> first = provider.getAuth(sampleRequest());
        Mono<AuthContext> second = provider.getAuth(sampleRequest());

        StepVerifier.create(Mono.zip(first, second))
                .then(() -> sink.tryEmitValue(new AccessToken("token-shared", now.plusSeconds(120))))
                .assertNext(tuple -> {
                    assertEquals("Bearer token-shared", tuple.getT1().getHeaders().get("Authorization"));
                    assertEquals("Bearer token-shared", tuple.getT2().getHeaders().get("Authorization"));
                })
                .verifyComplete();

        assertEquals(1, calls.get());
    }

    @Test
    void cancellingOneWaiterDoesNotCancelSharedRefresh() {
        AtomicInteger calls = new AtomicInteger();
        AtomicBoolean refreshCancelled = new AtomicBoolean();
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        Sinks.One<AccessToken> sink = Sinks.one();
        RefreshingBearerAuthProvider provider = new RefreshingBearerAuthProvider(
                () -> {
                    calls.incrementAndGet();
                    return sink.asMono().doOnCancel(() -> refreshCancelled.set(true));
                },
                Duration.ofSeconds(30),
                Duration.ZERO,
                Clock.fixed(now, ZoneOffset.UTC)
        );

        Disposable cancelledWaiter = provider.getAuth(sampleRequest("cancelled-client")).subscribe();
        cancelledWaiter.dispose();

        StepVerifier.create(provider.getAuth(sampleRequest("active-client")))
                .then(() -> sink.tryEmitValue(new AccessToken("shared-token", now.plusSeconds(120))))
                .assertNext(auth -> assertEquals("Bearer shared-token",
                        auth.getHeaders().get("Authorization")))
                .verifyComplete();

        assertEquals(1, calls.get());
        assertFalse(refreshCancelled.get());
    }

    @Test
    void sharedRefreshFailureKeepsEachWaiterLogicalClientName() {
        AtomicInteger calls = new AtomicInteger();
        Sinks.One<AccessToken> sink = Sinks.one();
        RefreshingBearerAuthProvider provider = new RefreshingBearerAuthProvider(
                () -> {
                    calls.incrementAndGet();
                    return sink.asMono();
                },
                Duration.ofSeconds(30),
                Duration.ZERO,
                Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC)
        );

        Mono<reactor.core.publisher.Signal<AuthContext>> orders =
                provider.getAuth(sampleRequest("orders-client")).materialize();
        Mono<reactor.core.publisher.Signal<AuthContext>> payments =
                provider.getAuth(sampleRequest("payments-client")).materialize();

        StepVerifier.create(Mono.zip(orders, payments))
                .then(() -> sink.tryEmitError(new io.github.huynhngochuyhoang.httpstarter.exception.AuthProviderException(
                        "orders-client", new IllegalStateException("token service unavailable"))))
                .assertNext(signals -> {
                    io.github.huynhngochuyhoang.httpstarter.exception.AuthProviderException ordersError =
                            assertInstanceOf(
                                    io.github.huynhngochuyhoang.httpstarter.exception.AuthProviderException.class,
                                    signals.getT1().getThrowable());
                    io.github.huynhngochuyhoang.httpstarter.exception.AuthProviderException paymentsError =
                            assertInstanceOf(
                                    io.github.huynhngochuyhoang.httpstarter.exception.AuthProviderException.class,
                                    signals.getT2().getThrowable());
                    assertEquals("orders-client", ordersError.getClientName());
                    assertEquals("Auth provider failed for client 'orders-client'", ordersError.getMessage());
                    assertEquals("payments-client", paymentsError.getClientName());
                    assertEquals("Auth provider failed for client 'payments-client'", paymentsError.getMessage());
                })
                .verifyComplete();

        assertEquals(1, calls.get());
    }

    @Test
    void shouldRejectExpiredTokenFromProvider() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        Clock fixedClock = Clock.fixed(now, ZoneOffset.UTC);
        RefreshingBearerAuthProvider provider = new RefreshingBearerAuthProvider(
                () -> Mono.just(new AccessToken("expired", now.minusSeconds(1))),
                Duration.ofSeconds(30),
                Duration.ZERO,
                fixedClock
        );

        StepVerifier.create(provider.getAuth(sampleRequest()))
                .expectErrorMatches(error ->
                        error instanceof io.github.huynhngochuyhoang.httpstarter.exception.AuthProviderException
                                && error.getCause() instanceof IllegalStateException
                                && error.getCause().getMessage().contains("already expired"))
                .verify();
    }

    @Test
    void shouldRefreshAgainAfterInvalidate() {
        AtomicInteger calls = new AtomicInteger();
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        Clock fixedClock = Clock.fixed(now, ZoneOffset.UTC);
        RefreshingBearerAuthProvider provider = new RefreshingBearerAuthProvider(
                () -> Mono.fromSupplier(() -> {
                    int call = calls.incrementAndGet();
                    return new AccessToken("token-" + call, now.plusSeconds(120));
                }),
                Duration.ofSeconds(30),
                Duration.ZERO,
                fixedClock
        );

        StepVerifier.create(provider.getAuth(sampleRequest()))
                .assertNext(auth -> assertEquals("Bearer token-1", auth.getHeaders().get("Authorization")))
                .verifyComplete();

        StepVerifier.create(provider.invalidate()).verifyComplete();

        StepVerifier.create(provider.getAuth(sampleRequest()))
                .assertNext(auth -> assertEquals("Bearer token-2", auth.getHeaders().get("Authorization")))
                .verifyComplete();
        assertEquals(2, calls.get());
    }

    @Test
    void shouldApplyCooldownAfterRefreshFailure() {
        AtomicInteger calls = new AtomicInteger();
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        TestClock clock = new TestClock(now);
        RefreshingBearerAuthProvider provider = new RefreshingBearerAuthProvider(
                () -> Mono.defer(() -> {
                    calls.incrementAndGet();
                    return Mono.error(new IllegalStateException("token endpoint down"));
                }),
                Duration.ofSeconds(30),
                Duration.ofSeconds(10),
                clock
        );

        StepVerifier.create(provider.getAuth(sampleRequest()))
                .expectError(io.github.huynhngochuyhoang.httpstarter.exception.AuthProviderException.class)
                .verify();
        StepVerifier.create(provider.getAuth(sampleRequest()))
                .expectErrorSatisfies(error -> {
                    assertTrue(error instanceof io.github.huynhngochuyhoang.httpstarter.exception.AuthProviderException);
                    assertTrue(error.getCause() instanceof io.github.huynhngochuyhoang.httpstarter.exception.AuthProviderException
                            || error.getCause() instanceof IllegalStateException);
                })
                .verify();
        assertEquals(1, calls.get());

        clock.set(now.plusSeconds(11));
        StepVerifier.create(provider.getAuth(sampleRequest()))
                .expectError(io.github.huynhngochuyhoang.httpstarter.exception.AuthProviderException.class)
                .verify();
        assertEquals(2, calls.get());
    }

    @Test
    void shouldRecoverAfterRefreshFailureCooldown() {
        AtomicInteger calls = new AtomicInteger();
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        TestClock clock = new TestClock(now);
        RefreshingBearerAuthProvider provider = new RefreshingBearerAuthProvider(
                () -> Mono.defer(() -> calls.incrementAndGet() == 1
                        ? Mono.error(new IllegalStateException("token endpoint down"))
                        : Mono.just(new AccessToken("recovered-token", now.plusSeconds(120)))),
                Duration.ofSeconds(30),
                Duration.ofSeconds(10),
                clock
        );

        StepVerifier.create(provider.getAuth(sampleRequest("orders-client")))
                .expectErrorSatisfies(error -> assertEquals("orders-client",
                        assertInstanceOf(
                                io.github.huynhngochuyhoang.httpstarter.exception.AuthProviderException.class,
                                error).getClientName()))
                .verify();
        StepVerifier.create(provider.getAuth(sampleRequest("payments-client")))
                .expectErrorSatisfies(error -> assertEquals("payments-client",
                        assertInstanceOf(
                                io.github.huynhngochuyhoang.httpstarter.exception.AuthProviderException.class,
                                error).getClientName()))
                .verify();
        assertEquals(1, calls.get());

        clock.set(now.plusSeconds(11));
        StepVerifier.create(provider.getAuth(sampleRequest("payments-client")))
                .assertNext(auth -> assertEquals("Bearer recovered-token",
                        auth.getHeaders().get("Authorization")))
                .verifyComplete();
        assertEquals(2, calls.get());
    }

    @Test
    void shouldNotReuseInvalidatedInFlightRefreshResult() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        Clock fixedClock = Clock.fixed(now, ZoneOffset.UTC);
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<Sinks.One<AccessToken>> firstSinkRef = new AtomicReference<>();

        RefreshingBearerAuthProvider provider = new RefreshingBearerAuthProvider(
                () -> Mono.defer(() -> {
                    int call = calls.incrementAndGet();
                    if (call == 1) {
                        Sinks.One<AccessToken> sink = Sinks.one();
                        firstSinkRef.set(sink);
                        return sink.asMono();
                    }
                    return Mono.just(new AccessToken("fresh-token", now.plusSeconds(120)));
                }),
                Duration.ofSeconds(30),
                Duration.ZERO,
                fixedClock
        );

        Mono<AuthContext> firstAttempt = provider.getAuth(sampleRequest());
        firstAttempt.subscribe();
        assertNotNull(firstSinkRef.get());
        StepVerifier.create(provider.invalidate()).verifyComplete();
        StepVerifier.create(provider.getAuth(sampleRequest()))
                .assertNext(auth -> assertEquals("Bearer fresh-token", auth.getHeaders().get("Authorization")))
                .verifyComplete();
        firstSinkRef.get().tryEmitValue(new AccessToken("stale-token", now.plusSeconds(120)));

        StepVerifier.create(firstAttempt)
                .assertNext(auth -> assertEquals("Bearer stale-token", auth.getHeaders().get("Authorization")))
                .verifyComplete();
        StepVerifier.create(provider.getAuth(sampleRequest()))
                .assertNext(auth -> assertEquals("Bearer fresh-token", auth.getHeaders().get("Authorization")))
                .verifyComplete();
        assertEquals(2, calls.get());
    }

    @Test
    void shouldKeepGenericMessageForCustomProviderFailures() {
        RefreshingBearerAuthProvider provider = new RefreshingBearerAuthProvider(
                () -> Mono.error(new IllegalStateException("raw client_secret=leaked")),
                Duration.ofSeconds(30),
                Duration.ZERO,
                Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC)
        );

        StepVerifier.create(provider.getAuth(sampleRequest()))
                .expectErrorSatisfies(error -> {
                    assertTrue(error instanceof io.github.huynhngochuyhoang.httpstarter.exception.AuthProviderException);
                    assertEquals("Auth provider failed for client 'sample-client'", error.getMessage());
                    assertTrue(error.getCause() instanceof IllegalStateException);
                    assertEquals("raw client_secret=leaked", error.getCause().getMessage());
                })
                .verify();
    }

    private static AuthRequest sampleRequest() {
        return sampleRequest("sample-client");
    }

    private static AuthRequest sampleRequest(String clientName) {
        ClientRequest request = ClientRequest.create(HttpMethod.GET, URI.create("https://api.test.local/resource")).build();
        return new AuthRequest(clientName, request);
    }

    private static final class TestClock extends Clock {
        private Instant instant;

        private TestClock(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }

        private void set(Instant instant) {
            this.instant = instant;
        }
    }
}
