package io.github.huynhngochuyhoang.httpstarter.auth;

import reactor.core.publisher.Mono;

/**
 * Provider that obtains a fresh bearer access token from an upstream auth server.
 */
@FunctionalInterface
public interface AccessTokenProvider {

    /**
     * Fetches a fresh access token.
     */
    Mono<AccessToken> fetchToken();
}
