package io.github.huynhngochuyhoang.httpstarter.auth;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.client.ClientRequest;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    private static AuthRequest sampleRequest() {
        ClientRequest request = ClientRequest.create(HttpMethod.GET, URI.create("https://api.test.local/resource")).build();
        return new AuthRequest("sample-client", request);
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
