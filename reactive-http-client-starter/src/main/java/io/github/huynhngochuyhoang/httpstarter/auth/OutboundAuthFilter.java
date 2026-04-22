package io.github.huynhngochuyhoang.httpstarter.auth;

import io.github.huynhngochuyhoang.httpstarter.exception.AuthProviderException;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.List;

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
        return resolveAuthorizedRequest(request, authRequest)
                .flatMap(next::exchange)
                .flatMap(response -> retryOnceOnUnauthorized(response, request, authRequest, next));
    }

    private Mono<ClientRequest> resolveAuthorizedRequest(ClientRequest request, AuthRequest authRequest) {
        return authProvider.getAuth(authRequest)
                .onErrorMap(error -> error instanceof AuthProviderException
                        ? error
                        : new AuthProviderException(clientName, error))
                .defaultIfEmpty(AuthContext.empty())
                .map(authContext -> applyAuth(request, authContext));
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
                .then(invalidatable.invalidate()
                        .onErrorMap(error -> error instanceof AuthProviderException
                                ? error
                                : new AuthProviderException(clientName, error)))
                .then(resolveAuthorizedRequest(originalRequest, authRequest))
                .flatMap(next::exchange);
    }

    private ClientRequest applyAuth(ClientRequest original, AuthContext authContext) {
        ClientRequest.Builder builder = ClientRequest.from(original);

        authContext.getHeaders().forEach((name, value) -> {
            validateHeaderValue(name, value);
            builder.headers(headers -> headers.set(name, value));
        });

        if (!authContext.getQueryParams().isEmpty()) {
            URI updatedUri = applyQueryParams(original.url(), authContext.getQueryParams());
            builder.url(updatedUri);
        }
        return builder.build();
    }

    private URI applyQueryParams(URI sourceUri, java.util.Map<String, List<String>> authQueryParams) {
        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromUri(sourceUri);
        authQueryParams.forEach((name, values) -> {
            uriBuilder.replaceQueryParam(name);
            for (String value : values) {
                uriBuilder.queryParam(name, value);
            }
        });
        return uriBuilder.encode().build().toUri();
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
