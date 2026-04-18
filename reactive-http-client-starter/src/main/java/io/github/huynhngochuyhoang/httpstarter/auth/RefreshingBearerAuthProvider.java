package io.github.huynhngochuyhoang.httpstarter.auth;

import org.springframework.http.HttpHeaders;
import org.springframework.util.Assert;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Built-in {@link AuthProvider} for bearer token auth with cached refresh support.
 * <p>
 * This provider caches the latest token and refreshes it when it is close to expiry.
 * Concurrent refresh attempts are deduplicated so only one upstream token call is in-flight.
 */
public final class RefreshingBearerAuthProvider implements AuthProvider {

    private static final Duration DEFAULT_REFRESH_SKEW = Duration.ofSeconds(30);

    private final AccessTokenProvider accessTokenProvider;
    private final Duration refreshSkew;
    private final Clock clock;

    private volatile CachedAccessToken cachedToken;
    private volatile Mono<CachedAccessToken> inFlightRefresh;

    public RefreshingBearerAuthProvider(AccessTokenProvider accessTokenProvider) {
        this(accessTokenProvider, DEFAULT_REFRESH_SKEW, Clock.systemUTC());
    }

    public RefreshingBearerAuthProvider(AccessTokenProvider accessTokenProvider, Duration refreshSkew) {
        this(accessTokenProvider, refreshSkew, Clock.systemUTC());
    }

    RefreshingBearerAuthProvider(AccessTokenProvider accessTokenProvider, Duration refreshSkew, Clock clock) {
        Assert.notNull(accessTokenProvider, "accessTokenProvider must not be null");
        Assert.notNull(refreshSkew, "refreshSkew must not be null");
        if (refreshSkew.isNegative()) {
            throw new IllegalArgumentException("refreshSkew must not be negative");
        }
        Assert.notNull(clock, "clock must not be null");
        this.accessTokenProvider = accessTokenProvider;
        this.refreshSkew = refreshSkew;
        this.clock = clock;
    }

    @Override
    public Mono<AuthContext> getAuth(AuthRequest request) {
        return resolveTokenValue()
                .map(tokenValue -> AuthContext.builder()
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenValue)
                        .build());
    }

    private Mono<String> resolveTokenValue() {
        Instant now = clock.instant();
        CachedAccessToken snapshot = cachedToken;
        if (isStillValid(snapshot, now)) {
            return Mono.just(snapshot.tokenValue());
        }

        Mono<CachedAccessToken> currentRefresh = inFlightRefresh;
        if (currentRefresh != null) {
            return currentRefresh.map(CachedAccessToken::tokenValue);
        }

        synchronized (this) {
            now = clock.instant();
            snapshot = cachedToken;
            if (isStillValid(snapshot, now)) {
                return Mono.just(snapshot.tokenValue());
            }
            if (inFlightRefresh != null) {
                return inFlightRefresh.map(CachedAccessToken::tokenValue);
            }

            AtomicReference<Mono<CachedAccessToken>> refreshRef = new AtomicReference<>();
            Mono<CachedAccessToken> refreshMono = Mono.defer(accessTokenProvider::fetchToken)
                    .switchIfEmpty(Mono.error(new IllegalStateException("AccessTokenProvider returned empty token")))
                    .map(this::validateAndNormalize)
                    .doOnNext(token -> cachedToken = token)
                    .doFinally(signalType -> clearInFlight(refreshRef.get()))
                    .cache();
            refreshRef.set(refreshMono);

            inFlightRefresh = refreshMono;
            return refreshMono.map(CachedAccessToken::tokenValue);
        }
    }

    private CachedAccessToken validateAndNormalize(AccessToken token) {
        Instant now = clock.instant();
        if (token.expiresAt() != null && !token.expiresAt().isAfter(now)) {
            throw new IllegalStateException("Access token from AccessTokenProvider is already expired");
        }
        return new CachedAccessToken(token.tokenValue(), token.expiresAt());
    }

    private boolean isStillValid(CachedAccessToken token, Instant now) {
        if (token == null) {
            return false;
        }
        if (token.expiresAt() == null) {
            return true;
        }
        return token.expiresAt().isAfter(now.plus(refreshSkew));
    }

    private void clearInFlight(Mono<CachedAccessToken> refreshMono) {
        synchronized (this) {
            if (inFlightRefresh == refreshMono) {
                inFlightRefresh = null;
            }
        }
    }

    private record CachedAccessToken(String tokenValue, Instant expiresAt) {}
}
