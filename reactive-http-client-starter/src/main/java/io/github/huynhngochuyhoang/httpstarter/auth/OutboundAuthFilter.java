package io.github.huynhngochuyhoang.httpstarter.auth;

import io.github.huynhngochuyhoang.httpstarter.exception.AuthProviderException;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * WebClient filter that resolves and injects auth information for outbound requests.
 */
public class OutboundAuthFilter implements ExchangeFilterFunction {

    private final String clientName;
    private final AuthProvider authProvider;

    public OutboundAuthFilter(String clientName, AuthProvider authProvider) {
        this.clientName = clientName;
        this.authProvider = authProvider;
    }

    @Override
    public Mono<org.springframework.web.reactive.function.client.ClientResponse> filter(
            ClientRequest request, ExchangeFunction next) {
        Object requestBody = request.attribute(AuthRequest.REQUEST_RAW_BODY_ATTRIBUTE)
                .or(() -> request.attribute(AuthRequest.REQUEST_BODY_ATTRIBUTE))
                .orElse(null);
        AuthRequest authRequest = new AuthRequest(clientName, request, requestBody);
        Object cacheProbe = request.attribute(AuthRequest.CACHE_AUTHORIZATION_PROBE_ATTRIBUTE).orElse(null);
        if (cacheProbe instanceof AtomicReference<?> || cacheProbe instanceof BiConsumer<?, ?>) {
            return resolveAuthContext(authRequest)
                    .map(authContext -> {
                        ClientRequest authorizedRequest = applyAuth(request, authContext);
                        if (cacheProbe instanceof AtomicReference<?> reference) {
                            @SuppressWarnings("unchecked")
                            AtomicReference<AuthContext> resolved = (AtomicReference<AuthContext>) reference;
                            resolved.set(authContext);
                        } else {
                            @SuppressWarnings("unchecked")
                            BiConsumer<ClientRequest, AuthContext> resolved =
                                    (BiConsumer<ClientRequest, AuthContext>) cacheProbe;
                            resolved.accept(authorizedRequest, authContext);
                        }
                        return ClientResponse.create(HttpStatus.NO_CONTENT).build();
                    });
        }
        Mono<ClientRequest> authorizedRequest = request.attribute(AuthRequest.PRE_RESOLVED_AUTH_CONTEXT_ATTRIBUTE)
                .map(this::consumePreResolvedAuth)
                .map(authContext -> Mono.just(applyAuth(request, authContext)))
                .orElseGet(() -> resolveAuthorizedRequest(request, authRequest));
        return authorizedRequest
                .flatMap(next::exchange)
                .flatMap(response -> retryOnceOnUnauthorized(response, request, authRequest, next));
    }

    private AuthContext consumePreResolvedAuth(Object attribute) {
        Object value = attribute instanceof AtomicReference<?> reference
                ? reference.getAndSet(null)
                : attribute;
        return value instanceof AuthContext authContext ? authContext : null;
    }

    private Mono<ClientRequest> resolveAuthorizedRequest(ClientRequest request, AuthRequest authRequest) {
        return resolveAuthContext(authRequest)
                .map(authContext -> applyAuth(request, authContext));
    }

    private Mono<AuthContext> resolveAuthContext(AuthRequest authRequest) {
        boolean isolatePreparedBody = authRequest.request()
                .attribute(AuthRequest.AUTH_CONTEXT_VALIDATOR_ATTRIBUTE)
                .isPresent();
        AuthRequest isolatedRequest = authRequest;
        if (isolatePreparedBody && authRequest.requestBody() instanceof byte[] rawBody) {
            byte[] isolatedBody = rawBody.clone();
            ClientRequest providerRequest = ClientRequest.from(authRequest.request())
                    .attribute(AuthRequest.REQUEST_RAW_BODY_ATTRIBUTE, isolatedBody)
                    .build();
            isolatedRequest = new AuthRequest(authRequest.clientName(), providerRequest, isolatedBody);
        }
        return authProvider.getAuth(isolatedRequest)
                .onErrorMap(error -> error instanceof AuthProviderException
                        ? error
                        : new AuthProviderException(clientName, error))
                .defaultIfEmpty(AuthContext.empty());
    }

    private Mono<ClientResponse> retryOnceOnUnauthorized(
            ClientResponse response,
            ClientRequest originalRequest,
            AuthRequest authRequest,
            ExchangeFunction next) {
        if (response.statusCode().value() != 401 || !(authProvider instanceof InvalidatableAuthProvider invalidatable)) {
            return Mono.just(response);
        }

        return response.releaseBody()
                .onErrorResume(error -> Mono.empty())
                .then(Mono.fromRunnable(() -> resetRequestObservation(originalRequest)))
                .then(invalidatable.invalidate()
                        .onErrorMap(error -> error instanceof AuthProviderException
                                ? error
                                : new AuthProviderException(clientName, error)))
                .then(resolveAuthorizedRequest(originalRequest, authRequest))
                .flatMap(next::exchange);
    }

    private void resetRequestObservation(ClientRequest request) {
        request.attribute(AuthRequest.REQUEST_OBSERVATION_RESET_ATTRIBUTE)
                .filter(Runnable.class::isInstance)
                .map(Runnable.class::cast)
                .ifPresent(Runnable::run);
    }

    private ClientRequest applyAuth(ClientRequest original, AuthContext authContext) {
        validateAuthContext(original, authContext);
        ClientRequest.Builder builder = ClientRequest.from(original);

        authContext.getHeaders().forEach((name, value) -> {
            validateHeaderName(name);
            validateHeaderValue(name, value);
            builder.headers(headers -> headers.set(name, value));
        });

        if (!authContext.getQueryParams().isEmpty()) {
            URI updatedUri = applyQueryParams(original.url(), authContext.getQueryParams());
            builder.url(updatedUri);
        }
        return builder.build();
    }

    private void validateAuthContext(ClientRequest request, AuthContext authContext) {
        request.attribute(AuthRequest.AUTH_CONTEXT_VALIDATOR_ATTRIBUTE)
                .filter(Consumer.class::isInstance)
                .ifPresent(validator -> {
                    @SuppressWarnings("unchecked")
                    Consumer<AuthContext> typedValidator = (Consumer<AuthContext>) validator;
                    typedValidator.accept(authContext);
                });
    }

    private void validateHeaderName(String headerName) {
        if (headerName == null || headerName.isBlank()) {
            throw new IllegalArgumentException("Auth header name must not be blank");
        }
        for (int i = 0; i < headerName.length(); i++) {
            char ch = headerName.charAt(i);
            if (ch <= 32 || ch >= 127 || "()<>@,;:\\\"/[]?={} \t".indexOf(ch) >= 0) {
                throw new IllegalArgumentException("Invalid auth header name '" + headerName + "'");
            }
        }
    }

    private URI applyQueryParams(URI sourceUri, java.util.Map<String, List<String>> authQueryParams) {
        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromUri(sourceUri);
        authQueryParams.forEach((name, values) -> {
            String encodedName = UriUtils.encodeQueryParam(name, StandardCharsets.UTF_8);
            uriBuilder.replaceQueryParam(encodedName);
            for (String value : values) {
                uriBuilder.queryParam(encodedName, UriUtils.encodeQueryParam(value, StandardCharsets.UTF_8));
            }
        });
        return uriBuilder.build(true).toUri();
    }

    private void validateHeaderValue(String headerName, String value) {
        if (value == null) {
            return;
        }
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch == '\r' || ch == '\n' || Character.isISOControl(ch)) {
                throw new IllegalArgumentException("Invalid auth header value for '" + headerName
                        + "': CRLF and control characters are not allowed");
            }
        }
    }
}
