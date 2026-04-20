package io.github.huynhngochuyhoang.httpstarter.auth;

import io.github.huynhngochuyhoang.httpstarter.exception.AuthProviderException;
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
public final class RefreshingBearerAuthProvider implements InvalidatableAuthProvider {

    private static final Duration DEFAULT_REFRESH_SKEW = Duration.ofSeconds(30);
    private static final Duration DEFAULT_FAILURE_COOLDOWN = Duration.ofSeconds(5);

    private final AccessTokenProvider accessTokenProvider;
    private final Duration refreshSkew;
    private final Duration failureCooldown;
    private final Clock clock;

    private volatile CachedAccessToken cachedToken;
    private volatile Mono<CachedAccessToken> inFlightRefresh;
    private volatile Instant lastRefreshFailureAt;
    private volatile Throwable lastRefreshFailure;

    public RefreshingBearerAuthProvider(AccessTokenProvider accessTokenProvider) {
        this(accessTokenProvider, DEFAULT_REFRESH_SKEW, DEFAULT_FAILURE_COOLDOWN, Clock.systemUTC());
    }

    public RefreshingBearerAuthProvider(AccessTokenProvider accessTokenProvider, Duration refreshSkew) {
        this(accessTokenProvider, refreshSkew, DEFAULT_FAILURE_COOLDOWN, Clock.systemUTC());
    }

    public RefreshingBearerAuthProvider(
            AccessTokenProvider accessTokenProvider,
            Duration refreshSkew,
            Duration failureCooldown) {
        this(accessTokenProvider, refreshSkew, failureCooldown, Clock.systemUTC());
    }

    RefreshingBearerAuthProvider(
            AccessTokenProvider accessTokenProvider,
            Duration refreshSkew,
            Duration failureCooldown,
            Clock clock) {
        Assert.notNull(accessTokenProvider, "accessTokenProvider must not be null");
        Assert.notNull(refreshSkew, "refreshSkew must not be null");
        Assert.notNull(failureCooldown, "failureCooldown must not be null");
        if (refreshSkew.isNegative()) {
            throw new IllegalArgumentException("refreshSkew must not be negative");
        }
        if (failureCooldown.isNegative()) {
            throw new IllegalArgumentException("failureCooldown must not be negative");
        }
        Assert.notNull(clock, "clock must not be null");
        this.accessTokenProvider = accessTokenProvider;
        this.refreshSkew = refreshSkew;
        this.failureCooldown = failureCooldown;
        this.clock = clock;
    }

    @Override
    public Mono<AuthContext> getAuth(AuthRequest request) {
        return resolveTokenValue(request.clientName())
                .map(tokenValue -> AuthContext.builder()
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenValue)
                        .build());
    }

    @Override
    public Mono<Void> invalidate() {
        synchronized (this) {
            cachedToken = null;
            inFlightRefresh = null;
            lastRefreshFailureAt = null;
            lastRefreshFailure = null;
        }
        return Mono.empty();
    }

    private Mono<String> resolveTokenValue(String clientName) {
        Instant now = clock.instant();
        CachedAccessToken snapshot = cachedToken;
        if (isStillValid(snapshot, now)) {
            return Mono.just(snapshot.tokenValue());
        }
        if (isInFailureCooldown(now)) {
            Throwable cause = lastRefreshFailure != null
                    ? lastRefreshFailure
                    : new IllegalStateException("token refresh in cooldown");
            return Mono.error(cause instanceof AuthProviderException
                    ? cause
                    : new AuthProviderException(clientName, cause));
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
                    .switchIfEmpty(Mono.error(new AuthProviderException(
                            clientName,
                            new IllegalStateException("AccessTokenProvider returned empty token"))))
                    .map(token -> validateAndNormalize(clientName, token))
                    .onErrorMap(error -> error instanceof AuthProviderException
                            ? error
                            : new AuthProviderException(clientName, error))
                    .doOnNext(token -> {
                        cachedToken = token;
                        lastRefreshFailureAt = null;
                        lastRefreshFailure = null;
                    })
                    .doOnError(error -> {
                        lastRefreshFailureAt = clock.instant();
                        lastRefreshFailure = error;
                    })
                    .doFinally(signalType -> clearInFlight(refreshRef.get()))
                    .cache();
            refreshRef.set(refreshMono);

            inFlightRefresh = refreshMono;
            return refreshMono.map(CachedAccessToken::tokenValue);
        }
    }

    private CachedAccessToken validateAndNormalize(String clientName, AccessToken token) {
        Instant now = clock.instant();
        if (token.expiresAt() != null && !token.expiresAt().isAfter(now)) {
            throw new AuthProviderException(
                    clientName,
                    new IllegalStateException("Access token from AccessTokenProvider is already expired"));
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

    private boolean isInFailureCooldown(Instant now) {
        if (failureCooldown.isZero()) {
            return false;
        }
        Instant failedAt = lastRefreshFailureAt;
        return failedAt != null && now.isBefore(failedAt.plus(failureCooldown));
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
