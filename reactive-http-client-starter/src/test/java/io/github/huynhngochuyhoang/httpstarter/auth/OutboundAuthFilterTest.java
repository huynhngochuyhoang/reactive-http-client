package io.github.huynhngochuyhoang.httpstarter.auth;

import io.github.huynhngochuyhoang.httpstarter.exception.AuthProviderException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

    @Test
    void shouldExposeRequestBodyToAuthProviderForHmacSigning() {
        AtomicReference<Object> capturedBody = new AtomicReference<>();
        AuthProvider authProvider = request -> {
            capturedBody.set(request.requestBody());
            return Mono.just(AuthContext.builder()
                    .header("X-Signature", "hmac-signature")
                    .build());
        };

        OutboundAuthFilter filter = new OutboundAuthFilter("hmac-service", authProvider);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", 1001);
        payload.put("amount", 200);

        ClientRequest request = ClientRequest.create(HttpMethod.POST, URI.create("https://api.test.local/payments"))
                .attribute(AuthRequest.REQUEST_BODY_ATTRIBUTE, payload)
                .build();

        AtomicReference<ClientRequest> capturedRequest = new AtomicReference<>();
        Mono<ClientResponse> response = filter.filter(request, req -> {
            capturedRequest.set(req);
            return Mono.just(ClientResponse.create(HttpStatus.OK).build());
        });

        StepVerifier.create(response).expectNextCount(1).verifyComplete();
        assertEquals(payload, capturedBody.get());
        assertEquals("hmac-signature", capturedRequest.get().headers().getFirst("X-Signature"));
    }

    @Test
    void shouldPreferRawBodyBytesForAuthSigningWhenPresent() {
        AtomicReference<Object> capturedBody = new AtomicReference<>();
        AuthProvider authProvider = request -> {
            capturedBody.set(request.requestBody());
            return Mono.just(AuthContext.empty());
        };

        OutboundAuthFilter filter = new OutboundAuthFilter("hmac-service", authProvider);
        byte[] raw = "{\"id\":1001}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        ClientRequest request = ClientRequest.create(HttpMethod.POST, URI.create("https://api.test.local/payments"))
                .attribute(AuthRequest.REQUEST_BODY_ATTRIBUTE, java.util.Map.of("id", 1001))
                .attribute(AuthRequest.REQUEST_RAW_BODY_ATTRIBUTE, raw)
                .build();

        StepVerifier.create(filter.filter(request, req -> Mono.just(ClientResponse.create(HttpStatus.OK).build())))
                .expectNextCount(1)
                .verifyComplete();
        assertTrue(capturedBody.get() instanceof byte[]);
        org.junit.jupiter.api.Assertions.assertArrayEquals(raw, (byte[]) capturedBody.get());
    }

    @Test
    void shouldWrapAuthProviderFailure() {
        AuthProvider authProvider = request -> Mono.error(new IllegalStateException("token endpoint down"));
        OutboundAuthFilter filter = new OutboundAuthFilter("user-service", authProvider);
        ClientRequest request = ClientRequest.create(HttpMethod.GET, URI.create("https://api.test.local/users")).build();

        StepVerifier.create(filter.filter(request, req -> Mono.just(ClientResponse.create(HttpStatus.OK).build())))
                .expectErrorSatisfies(error -> {
                    assertTrue(error instanceof AuthProviderException);
                    assertTrue(error.getCause() instanceof IllegalStateException);
                })
                .verify();
    }

    @Test
    void shouldInvalidateAndRetryOnceOnUnauthorized() {
        AtomicInteger tokenCalls = new AtomicInteger();
        AtomicInteger invalidateCalls = new AtomicInteger();
        InvalidatableAuthProvider authProvider = new InvalidatableAuthProvider() {
            @Override
            public Mono<AuthContext> getAuth(AuthRequest request) {
                int call = tokenCalls.incrementAndGet();
                return Mono.just(AuthContext.builder()
                        .header("Authorization", "Bearer token-" + call)
                        .build());
            }

            @Override
            public Mono<Void> invalidate() {
                invalidateCalls.incrementAndGet();
                return Mono.empty();
            }
        };

        OutboundAuthFilter filter = new OutboundAuthFilter("user-service", authProvider);
        ClientRequest request = ClientRequest.create(HttpMethod.GET, URI.create("https://api.test.local/users")).build();

        Mono<ClientResponse> response = filter.filter(request, req -> {
            String auth = req.headers().getFirst("Authorization");
            if ("Bearer token-1".equals(auth)) {
                return Mono.just(ClientResponse.create(HttpStatus.UNAUTHORIZED).body("unauthorized").build());
            }
            return Mono.just(ClientResponse.create(HttpStatus.OK).build());
        });

        StepVerifier.create(response)
                .assertNext(clientResponse -> assertEquals(HttpStatus.OK.value(), clientResponse.statusCode().value()))
                .verifyComplete();
        assertEquals(2, tokenCalls.get());
        assertEquals(1, invalidateCalls.get());
    }

    @Test
    void shouldReleaseUnauthorizedBodyEvenWhenInvalidateFails() {
        AtomicInteger invalidateCalls = new AtomicInteger();
        AtomicBoolean released = new AtomicBoolean(false);
        InvalidatableAuthProvider authProvider = new InvalidatableAuthProvider() {
            @Override
            public Mono<AuthContext> getAuth(AuthRequest request) {
                return Mono.just(AuthContext.builder().header("Authorization", "Bearer token-1").build());
            }

            @Override
            public Mono<Void> invalidate() {
                invalidateCalls.incrementAndGet();
                return Mono.error(new IllegalStateException("invalidate failed"));
            }
        };
        ClientResponse unauthorized = mock(ClientResponse.class);
        when(unauthorized.statusCode()).thenReturn(HttpStatus.UNAUTHORIZED);
        when(unauthorized.releaseBody()).thenAnswer(invocation -> Mono.fromRunnable(() -> released.set(true)));

        OutboundAuthFilter filter = new OutboundAuthFilter("user-service", authProvider);
        ClientRequest request = ClientRequest.create(HttpMethod.GET, URI.create("https://api.test.local/users")).build();

        StepVerifier.create(filter.filter(request, req -> Mono.just(unauthorized)))
                .expectError(AuthProviderException.class)
                .verify();
        assertTrue(released.get());
        assertEquals(1, invalidateCalls.get());
    }

    @Test
    void shouldEncodeAuthQueryParamsWithReservedCharacters() {
        AuthProvider authProvider = request -> Mono.just(AuthContext.builder()
                .queryParam("sig", "a+b&c=d/e ?")
                .build());
        OutboundAuthFilter filter = new OutboundAuthFilter("user-service", authProvider);
        ClientRequest request = ClientRequest.create(HttpMethod.GET, URI.create("https://api.test.local/users")).build();

        AtomicReference<ClientRequest> capturedRequest = new AtomicReference<>();
        StepVerifier.create(filter.filter(request, req -> {
                    capturedRequest.set(req);
                    return Mono.just(ClientResponse.create(HttpStatus.OK).build());
                }))
                .expectNextCount(1)
                .verifyComplete();

        var params = org.springframework.web.util.UriComponentsBuilder
                .fromUri(capturedRequest.get().url())
                .build(true)
                .getQueryParams();
        assertEquals(1, params.size());
        assertTrue(params.containsKey("sig"));
        assertTrue(capturedRequest.get().url().getRawQuery().contains("%26"));
    }

    @Test
    void shouldRejectAuthHeaderValuesWithCrLfCharacters() {
        AuthProvider authProvider = request -> Mono.just(AuthContext.builder()
                .header("Authorization", "Bearer token\r\nX-Evil: 1")
                .build());
        OutboundAuthFilter filter = new OutboundAuthFilter("user-service", authProvider);
        ClientRequest request = ClientRequest.create(HttpMethod.GET, URI.create("https://api.test.local/users")).build();

        StepVerifier.create(filter.filter(request, req -> Mono.just(ClientResponse.create(HttpStatus.OK).build())))
                .expectErrorMatches(error -> error instanceof IllegalArgumentException
                        && error.getMessage().contains("Invalid auth header value"))
                .verify();
    }
}
