package io.github.huynhngochuyhoang.httpstarter.auth;

import reactor.core.publisher.Mono;

/**
 * Strategy abstraction for outbound authentication.
 * Implementations can provide OAuth2, API key, HMAC, or any custom auth mechanism.
 */
@FunctionalInterface
public interface AuthProvider {

    /**
     * Produces an {@link AuthContext} containing auth data to inject into the outgoing request.
     */
    Mono<AuthContext> getAuth(AuthRequest request);
}
