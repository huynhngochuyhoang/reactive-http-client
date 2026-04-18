package io.github.huynhngochuyhoang.httpstarter.auth;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OutboundAuthFilterTest {

    @Test
    void shouldInjectAuthHeaderAndQueryParam() {
        AuthProvider authProvider = request -> Mono.just(AuthContext.builder()
                .header("Authorization", "Bearer token")
                .queryParam("api_key", "abc123")
                .build());

        OutboundAuthFilter filter = new OutboundAuthFilter("user-service", authProvider);
        ClientRequest request = ClientRequest.create(HttpMethod.GET, URI.create("https://api.test.local/users?existing=1"))
                .header("X-Request-Id", "req-1")
                .build();

        AtomicReference<ClientRequest> capturedRequest = new AtomicReference<>();
        Mono<ClientResponse> response = filter.filter(request, req -> {
            capturedRequest.set(req);
            return Mono.just(ClientResponse.create(HttpStatus.OK).build());
        });

        StepVerifier.create(response).expectNextCount(1).verifyComplete();
        assertEquals("Bearer token", capturedRequest.get().headers().getFirst("Authorization"));
        assertEquals("req-1", capturedRequest.get().headers().getFirst("X-Request-Id"));
        assertEquals("1", org.springframework.web.util.UriComponentsBuilder.fromUri(capturedRequest.get().url()).build().getQueryParams().getFirst("existing"));
        assertEquals("abc123", org.springframework.web.util.UriComponentsBuilder.fromUri(capturedRequest.get().url()).build().getQueryParams().getFirst("api_key"));
    }

    @Test
    void shouldOverrideExistingValuesFromAuthContext() {
        AuthProvider authProvider = request -> Mono.just(new AuthContext(
                Map.of("Authorization", "Bearer newer"),
                Map.of("api_key", java.util.List.of("new-key"))
        ));

        OutboundAuthFilter filter = new OutboundAuthFilter("billing-service", authProvider);
        ClientRequest request = ClientRequest.create(HttpMethod.GET, URI.create("https://api.test.local/items?api_key=old-key"))
                .header("Authorization", "Bearer old")
                .build();

        AtomicReference<ClientRequest> capturedRequest = new AtomicReference<>();
        Mono<ClientResponse> response = filter.filter(request, req -> {
            capturedRequest.set(req);
            return Mono.just(ClientResponse.create(HttpStatus.OK).build());
        });

        StepVerifier.create(response).expectNextCount(1).verifyComplete();
        assertEquals("Bearer newer", capturedRequest.get().headers().getFirst("Authorization"));
        assertEquals("new-key", org.springframework.web.util.UriComponentsBuilder.fromUri(capturedRequest.get().url()).build().getQueryParams().getFirst("api_key"));
    }
}
