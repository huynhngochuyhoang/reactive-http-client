package io.github.huynhngochuyhoang.httpstarter.auth;

import io.github.huynhngochuyhoang.httpstarter.exception.AuthProviderException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.reactive.MockClientHttpRequest;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class OAuth2ClientCredentialsTokenProviderTest {

    @Test
    void sendsBasicAuthByDefaultAndParsesTokenResponse() {
        AtomicReference<MockClientHttpRequest> captured = captureMock();
        WebClient webClient = WebClient.builder()
                .exchangeFunction(request -> materializeAndRespond(request, captured, """
                        {"access_token":"abc-123","token_type":"Bearer","expires_in":3600}"""))
                .build();

        OAuth2ClientCredentialsTokenProvider provider =
                OAuth2ClientCredentialsTokenProvider.builder(webClient)
                        .tokenUri("https://auth.example.com/oauth/token")
                        .clientId("svc-user")
                        .clientSecret("secret!")
                        .scope("read:users")
                        .build();

        StepVerifier.create(provider.fetchToken())
                .assertNext(token -> {
                    assertThat(token.tokenValue()).isEqualTo("abc-123");
                    assertThat(token.expiresAt()).isNotNull();
                })
                .verifyComplete();

        MockClientHttpRequest request = captured.get();
        assertThat(request.getURI()).isEqualTo(URI.create("https://auth.example.com/oauth/token"));
        assertThat(request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION)).startsWith("Basic ");
        assertThat(request.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_FORM_URLENCODED);
        String body = requestBody(request);
        assertThat(body).contains("grant_type=client_credentials", "scope=read%3Ausers");
        assertThat(body).doesNotContain("client_id=", "client_secret=");
    }

    @Test
    void formPostAuthStyleSendsCredentialsInBody() {
        AtomicReference<MockClientHttpRequest> captured = captureMock();
        WebClient webClient = WebClient.builder()
                .exchangeFunction(request -> materializeAndRespond(request, captured,
                        "{\"access_token\":\"t\",\"token_type\":\"Bearer\",\"expires_in\":60}"))
                .build();

        OAuth2ClientCredentialsTokenProvider provider =
                OAuth2ClientCredentialsTokenProvider.builder(webClient)
                        .tokenUri("https://auth.example.com/oauth/token")
                        .clientId("form-client")
                        .clientSecret("form-secret")
                        .authStyle(OAuth2ClientCredentialsTokenProvider.AuthStyle.FORM_POST)
                        .build();

        StepVerifier.create(provider.fetchToken())
                .assertNext(token -> assertThat(token.tokenValue()).isEqualTo("t"))
                .verifyComplete();

        MockClientHttpRequest request = captured.get();
        assertThat(request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION)).isNull();
        String body = requestBody(request);
        assertThat(body)
                .contains("client_id=form-client")
                .contains("client_secret=form-secret")
                .contains("grant_type=client_credentials");
    }

    @Test
    void expiryLeewayIsSubtractedFromExpiresIn() {
        AtomicReference<MockClientHttpRequest> captured = captureMock();
        WebClient webClient = WebClient.builder()
                .exchangeFunction(request -> materializeAndRespond(request, captured,
                        "{\"access_token\":\"x\",\"token_type\":\"Bearer\",\"expires_in\":100}"))
                .build();

        OAuth2ClientCredentialsTokenProvider provider =
                OAuth2ClientCredentialsTokenProvider.builder(webClient)
                        .tokenUri("https://auth.example.com/oauth/token")
                        .clientId("c")
                        .clientSecret("s")
                        .expiryLeeway(Duration.ofSeconds(20))
                        .build();

        java.time.Instant before = java.time.Instant.now();
        AccessToken token = provider.fetchToken().block();
        java.time.Instant after = java.time.Instant.now();

        long seconds = Duration.between(before, token.expiresAt()).toSeconds();
        assertThat(seconds)
                .as("expires_in=100 - leeway=20 → ~80s from now")
                .isBetween(70L, 85L);
        assertThat(token.expiresAt()).isAfter(after);
    }

    @Test
    void missingExpiresInProducesNonExpiringToken() {
        AtomicReference<MockClientHttpRequest> captured = captureMock();
        WebClient webClient = WebClient.builder()
                .exchangeFunction(request -> materializeAndRespond(request, captured,
                        "{\"access_token\":\"forever\",\"token_type\":\"Bearer\"}"))
                .build();

        OAuth2ClientCredentialsTokenProvider provider =
                OAuth2ClientCredentialsTokenProvider.builder(webClient)
                        .tokenUri("https://auth.example.com/oauth/token")
                        .clientId("c")
                        .clientSecret("s")
                        .build();

        AccessToken token = provider.fetchToken().block();
        assertThat(token.tokenValue()).isEqualTo("forever");
        assertThat(token.expiresAt()).isNull();
    }

    @Test
    void scopeAndAudienceAreForwardedAsFormFields() {
        AtomicReference<MockClientHttpRequest> captured = captureMock();
        WebClient webClient = WebClient.builder()
                .exchangeFunction(request -> materializeAndRespond(request, captured,
                        "{\"access_token\":\"t\",\"token_type\":\"Bearer\",\"expires_in\":60}"))
                .build();

        OAuth2ClientCredentialsTokenProvider provider =
                OAuth2ClientCredentialsTokenProvider.builder(webClient)
                        .tokenUri("https://auth.example.com/oauth/token")
                        .clientId("c")
                        .clientSecret("s")
                        .scope("read write")
                        .audience("https://api.example.com/")
                        .build();

        provider.fetchToken().block();

        String body = requestBody(captured.get());
        assertThat(body)
                .contains("scope=read+write")
                .contains("audience=https%3A%2F%2Fapi.example.com%2F");
    }

    @Test
    void tokenEndpoint4xxProducesBoundedSanitizedException() {
        AtomicReference<MockClientHttpRequest> captured = captureMock();
        String body = "{\"error\":\"invalid_client\",\"error_description\":\"client_secret=client-secret "
                + "access_token=leaked-access-token refresh_token=leaked-refresh-token "
                + "x".repeat(1200) + "\"}";
        WebClient webClient = WebClient.builder()
                .exchangeFunction(request -> materializeAndRespond(request, captured, HttpStatus.BAD_REQUEST, body))
                .build();

        OAuth2ClientCredentialsTokenProvider provider =
                OAuth2ClientCredentialsTokenProvider.builder(webClient)
                        .tokenUri("https://auth.example.com/oauth/token")
                        .clientId("client")
                        .clientSecret("client-secret")
                        .build();

        StepVerifier.create(provider.fetchToken())
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(AuthProviderException.class);
                    assertThat(error.getMessage())
                            .contains("HTTP 400", "responseBody=", "<redacted>", "...(truncated)")
                            .doesNotContain("client-secret", "leaked-access-token", "leaked-refresh-token");
                    assertThat(error.getMessage().length()).isLessThan(1150);
                })
                .verify();
    }

    @Test
    void tokenEndpoint5xxProducesSanitizedException() {
        AtomicReference<MockClientHttpRequest> captured = captureMock();
        WebClient webClient = WebClient.builder()
                .exchangeFunction(request -> materializeAndRespond(request, captured, HttpStatus.SERVICE_UNAVAILABLE,
                        "server unavailable client_secret=client-secret"))
                .build();

        OAuth2ClientCredentialsTokenProvider provider =
                OAuth2ClientCredentialsTokenProvider.builder(webClient)
                        .tokenUri("https://auth.example.com/oauth/token")
                        .clientId("client")
                        .clientSecret("client-secret")
                        .build();

        StepVerifier.create(provider.fetchToken())
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(AuthProviderException.class);
                    assertThat(error.getMessage())
                            .contains("HTTP 503", "server unavailable", "client_secret=<redacted>")
                            .doesNotContain("client-secret");
                })
                .verify();
    }

    @Test
    void malformedTokenJsonProducesSanitizedException() {
        AtomicReference<MockClientHttpRequest> captured = captureMock();
        WebClient webClient = WebClient.builder()
                .exchangeFunction(request -> materializeAndRespond(request, captured,
                        "not-json leaked-access-token client_secret=client-secret"))
                .build();

        OAuth2ClientCredentialsTokenProvider provider =
                OAuth2ClientCredentialsTokenProvider.builder(webClient)
                        .tokenUri("https://auth.example.com/oauth/token")
                        .clientId("client")
                        .clientSecret("client-secret")
                        .build();

        StepVerifier.create(provider.fetchToken())
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(AuthProviderException.class);
                    assertThat(error.getMessage())
                            .contains("malformed JSON token response")
                            .doesNotContain("leaked-access-token", "client-secret");
                })
                .verify();
    }

    @Test
    void missingAccessTokenProducesSanitizedException() {
        AtomicReference<MockClientHttpRequest> captured = captureMock();
        WebClient webClient = WebClient.builder()
                .exchangeFunction(request -> materializeAndRespond(request, captured,
                        "{\"refresh_token\":\"leaked-refresh-token\",\"expires_in\":60}"))
                .build();

        OAuth2ClientCredentialsTokenProvider provider =
                OAuth2ClientCredentialsTokenProvider.builder(webClient)
                        .tokenUri("https://auth.example.com/oauth/token")
                        .clientId("client")
                        .clientSecret("client-secret")
                        .build();

        StepVerifier.create(provider.fetchToken())
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(AuthProviderException.class);
                    assertThat(error.getMessage())
                            .contains("no access_token")
                            .doesNotContain("leaked-refresh-token", "client-secret");
                })
                .verify();
    }

    @Test
    void builderRejectsBlankRequiredFields() {
        WebClient webClient = WebClient.builder().build();

        assertThatIllegalArgumentException(() ->
                OAuth2ClientCredentialsTokenProvider.builder(webClient)
                        .clientId("c").clientSecret("s").build());
        assertThatIllegalArgumentException(() ->
                OAuth2ClientCredentialsTokenProvider.builder(webClient)
                        .tokenUri("http://x").clientSecret("s").build());
        assertThatIllegalArgumentException(() ->
                OAuth2ClientCredentialsTokenProvider.builder(webClient)
                        .tokenUri("http://x").clientId("c").build());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static void assertThatIllegalArgumentException(Runnable action) {
        try {
            action.run();
            throw new AssertionError("Expected IllegalArgumentException");
        } catch (IllegalArgumentException ignored) {
            // expected
        }
    }

    private static AtomicReference<MockClientHttpRequest> captureMock() {
        return new AtomicReference<>();
    }

    private static Mono<ClientResponse> materializeAndRespond(ClientRequest request,
                                                              AtomicReference<MockClientHttpRequest> captured,
                                                              String jsonBody) {
        return materializeAndRespond(request, captured, HttpStatus.OK, jsonBody);
    }

    private static Mono<ClientResponse> materializeAndRespond(ClientRequest request,
                                                              AtomicReference<MockClientHttpRequest> captured,
                                                              HttpStatus status,
                                                              String jsonBody) {
        MockClientHttpRequest mock = new MockClientHttpRequest(request.method(), URI.create(request.url().toString()));
        return request.writeTo(mock, ExchangeStrategies.withDefaults())
                .then(Mono.fromRunnable(() -> captured.set(mock)))
                .thenReturn(ClientResponse.create(status)
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .body(jsonBody)
                        .build());
    }

    private static String requestBody(MockClientHttpRequest request) {
        return Flux.from(request.getBody())
                .map(buf -> buf.toString(StandardCharsets.UTF_8))
                .collect(StringBuilder::new, StringBuilder::append)
                .map(StringBuilder::toString)
                .block();
    }
}
