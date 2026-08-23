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
    /** Internal request attribute carrying a one-shot auth reference resolved before a cache lookup. */
    public static final String PRE_RESOLVED_AUTH_CONTEXT_ATTRIBUTE =
            "reactive-http-client.auth.pre-resolved-context";
    /** Internal request attribute used to resolve auth before a cache lookup without dispatching. */
    public static final String CACHE_AUTHORIZATION_PROBE_ATTRIBUTE =
            "reactive-http-client.auth.cache-authorization-probe";
    /** Internal request attribute used to reset final-request observation before auth retry. */
    public static final String REQUEST_OBSERVATION_RESET_ATTRIBUTE =
            "reactive-http-client.auth.request-observation-reset";

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
