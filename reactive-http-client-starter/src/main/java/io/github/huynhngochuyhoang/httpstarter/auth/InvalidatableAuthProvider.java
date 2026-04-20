package io.github.huynhngochuyhoang.httpstarter.auth;

import reactor.core.publisher.Mono;

/**
 * Extension of {@link AuthProvider} that can invalidate cached credentials.
 */
public interface InvalidatableAuthProvider extends AuthProvider {

    /**
     * Invalidates any cached auth state so the next auth resolution fetches fresh credentials.
     */
    Mono<Void> invalidate();
}
