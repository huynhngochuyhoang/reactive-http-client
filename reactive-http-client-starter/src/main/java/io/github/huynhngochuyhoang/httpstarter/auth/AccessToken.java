package io.github.huynhngochuyhoang.httpstarter.auth;

import org.springframework.util.StringUtils;

import java.time.Instant;

/**
 * Access token descriptor used by {@link RefreshingBearerAuthProvider}.
 *
 * @param tokenValue bearer token value
 * @param expiresAt  token expiry timestamp in UTC; nullable for non-expiring tokens
 */
public record AccessToken(String tokenValue, Instant expiresAt) {

    public AccessToken {
        if (!StringUtils.hasText(tokenValue)) {
            throw new IllegalArgumentException("tokenValue must not be blank");
        }
    }
}
