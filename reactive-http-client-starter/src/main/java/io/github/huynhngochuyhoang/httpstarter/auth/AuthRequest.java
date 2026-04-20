package io.github.huynhngochuyhoang.httpstarter.auth;

import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.ClientRequest;

import java.util.Objects;

/**
 * Request context passed to {@link AuthProvider}.
 */
public record AuthRequest(String clientName, ClientRequest request, Object requestBody) {

    /**
     * Internal request attribute key used to pass resolved request body to auth providers.
     */
    public static final String REQUEST_BODY_ATTRIBUTE = "reactive-http-client.auth.request-body";
    /**
     * Internal request attribute key used to pass serialized raw bytes for auth signing.
     */
    public static final String REQUEST_RAW_BODY_ATTRIBUTE = "reactive-http-client.auth.request-raw-body";

    public AuthRequest {
        Objects.requireNonNull(request, "request must not be null");
        if (!StringUtils.hasText(clientName)) {
            throw new IllegalArgumentException("clientName must not be blank");
        }
    }

    public AuthRequest(String clientName, ClientRequest request) {
        this(clientName, request, null);
    }
}
