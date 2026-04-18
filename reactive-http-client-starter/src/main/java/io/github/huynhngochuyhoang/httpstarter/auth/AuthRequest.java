package io.github.huynhngochuyhoang.httpstarter.auth;

import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.ClientRequest;

import java.util.Objects;

/**
 * Request context passed to {@link AuthProvider}.
 */
public record AuthRequest(String clientName, ClientRequest request) {

    public AuthRequest {
        Objects.requireNonNull(request, "request must not be null");
        if (!StringUtils.hasText(clientName)) {
            throw new IllegalArgumentException("clientName must not be blank");
        }
    }
}
