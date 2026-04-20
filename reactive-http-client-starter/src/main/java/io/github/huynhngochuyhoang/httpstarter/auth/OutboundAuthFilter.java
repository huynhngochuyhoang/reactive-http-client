package io.github.huynhngochuyhoang.httpstarter.auth;

import io.github.huynhngochuyhoang.httpstarter.exception.AuthProviderException;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.reactive.function.client.ClientRequest;
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
        Object requestBody = request.attribute(AuthRequest.REQUEST_BODY_ATTRIBUTE).orElse(null);
        return authProvider.getAuth(new AuthRequest(clientName, request, requestBody))
                .onErrorMap(error -> error instanceof AuthProviderException
                        ? error
                        : new AuthProviderException(clientName, error))
                .defaultIfEmpty(AuthContext.empty())
                .map(authContext -> applyAuth(request, authContext))
                .flatMap(next::exchange);
    }

    private ClientRequest applyAuth(ClientRequest original, AuthContext authContext) {
        ClientRequest.Builder builder = ClientRequest.from(original);

        authContext.getHeaders().forEach((name, value) -> builder.headers(headers -> headers.set(name, value)));

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
        return uriBuilder.build(true).toUri();
    }
}
