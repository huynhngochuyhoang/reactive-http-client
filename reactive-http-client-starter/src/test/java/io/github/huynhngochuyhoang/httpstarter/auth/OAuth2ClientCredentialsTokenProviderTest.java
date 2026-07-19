package io.github.huynhngochuyhoang.httpstarter.auth;

import io.github.huynhngochuyhoang.httpstarter.exception.AuthProviderException;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.*;
import org.springframework.http.codec.json.JacksonJsonDecoder;
import org.springframework.mock.http.client.reactive.MockClientHttpRequest;
import org.springframework.web.reactive.function.client.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
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
    void oversizedSuccessfulTokenResponseStillParsesToken() {
        AtomicReference<MockClientHttpRequest> captured = captureMock();
        String body = "{\"access_token\":\"large-token\",\"padding\":\""
                + "x".repeat(8192) + "\"}";
        WebClient webClient = WebClient.builder()
                .exchangeFunction(request -> materializeAndRespond(request, captured, body))
                .build();
        OAuth2ClientCredentialsTokenProvider provider =
                OAuth2ClientCredentialsTokenProvider.builder(webClient)
                        .tokenUri("https://auth.example.com/oauth/token")
                        .clientId("client")
                        .clientSecret("client-secret")
                        .build();

        StepVerifier.create(provider.fetchToken())
                .assertNext(token -> {
                    assertThat(token.tokenValue()).isEqualTo("large-token");
                    assertThat(token.expiresAt()).isNull();
                })
                .verifyComplete();
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
    void tokenRequestUsesBuilderDefaultStatusHandlers() {
        WebClient webClient = WebClient.builder()
                .defaultStatusHandler(status -> status.value() == 429,
                        response -> Mono.error(new IllegalStateException("custom token status")))
                .exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.TOO_MANY_REQUESTS)
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .body("{\"error\":\"rate_limited\"}")
                        .build()))
                .build();

        OAuth2ClientCredentialsTokenProvider provider =
                OAuth2ClientCredentialsTokenProvider.builder(webClient)
                        .tokenUri("https://auth.example.com/oauth/token")
                        .clientId("client")
                        .clientSecret("client-secret")
                        .clientName("diagnostic-client")
                        .build();

        StepVerifier.create(provider.fetchToken())
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessage("custom token status"))
                .verify();
    }

    @Test
    void tokenEndpoint4xxProducesBoundedDiagnosticAndDecodableSanitizedBody() {
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
                        .clientName("diagnostic-client")
                        .build();

        StepVerifier.create(provider.fetchToken())
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(AuthProviderException.class);
                    assertThat(error.getMessage())
                            .contains("HTTP 400", "responseBody=", "<redacted>", "...(truncated)")
                            .doesNotContain("client-secret", "leaked-access-token", "leaked-refresh-token");
                    assertThat(error.getCause()).isInstanceOf(WebClientResponseException.class);
                    WebClientResponseException cause = (WebClientResponseException) error.getCause();
                    assertThat(cause.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(cause.getResponseBodyAsString())
                            .contains("<redacted>")
                            .doesNotContain("...(truncated)", "client-secret",
                                    "leaked-access-token", "leaked-refresh-token");
                    OAuthErrorBody decoded = cause.getResponseBodyAs(OAuthErrorBody.class);
                    assertThat(decoded.error).isEqualTo("invalid_client");
                    assertThat(decoded.error_description)
                            .contains("<redacted>")
                            .doesNotContain("client-secret", "leaked-access-token", "leaked-refresh-token")
                            .hasSizeGreaterThan(1200);
                    assertThat(error.getMessage().length()).isLessThan(1150);
                })
                .verify();
    }

    @Test
    void tokenEndpointFailureCausePreservesHttpStatusAndHeaders() {
        AtomicReference<String> basicAuthorization = new AtomicReference<>();
        WebClient webClient = WebClient.builder()
                .exchangeFunction(request -> {
                    basicAuthorization.set(request.headers().getFirst(HttpHeaders.AUTHORIZATION));
                    HttpRequest sourceRequest = new HttpRequest() {
                        @Override
                        public HttpMethod getMethod() {
                            return request.method();
                        }

                        @Override
                        public URI getURI() {
                            return request.url();
                        }

                        @Override
                        public HttpHeaders getHeaders() {
                            return request.headers();
                        }

                        @Override
                        public Map<String, Object> getAttributes() {
                            return Map.of();
                        }
                    };
                    return Mono.just(ClientResponse.create(HttpStatus.TOO_MANY_REQUESTS)
                            .request(sourceRequest)
                            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .header(HttpHeaders.RETRY_AFTER, "5")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer leaked-response-token")
                            .header(HttpHeaders.SET_COOKIE, "session=leaked-session")
                            .header("X-Token-Debug", "leaked-header-token")
                            .header("X-Debug", "client_secret=client-secret")
                            .body("{\"access_token\":\"leaked-access\"}")
                            .build());
                })
                .build();

        OAuth2ClientCredentialsTokenProvider provider =
                OAuth2ClientCredentialsTokenProvider.builder(webClient)
                        .tokenUri("https://auth.example.com/oauth/token")
                        .clientId("client")
                        .clientSecret("client-secret")
                        .clientName("diagnostic-client")
                        .build();

        StepVerifier.create(provider.fetchToken())
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(AuthProviderException.class);
                    assertThat(error.getCause()).isInstanceOf(WebClientResponseException.TooManyRequests.class);
                    WebClientResponseException cause = (WebClientResponseException) error.getCause();
                    assertThat(cause.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(basicAuthorization.get()).startsWith("Basic ");
                    assertThat(cause.getRequest()).isNull();
                    assertThat(cause.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("5");
                    assertThat(cause.getHeaders().getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("<redacted>");
                    assertThat(cause.getHeaders().getFirst(HttpHeaders.SET_COOKIE)).isEqualTo("<redacted>");
                    assertThat(cause.getHeaders().getFirst("X-Token-Debug")).isEqualTo("<redacted>");
                    assertThat(cause.getHeaders().getFirst("X-Debug"))
                            .isEqualTo("client_secret=<redacted>");
                    assertThat(cause.getHeaders().toString())
                            .doesNotContain("leaked-response-token", "leaked-session",
                                    "leaked-header-token", "client-secret");
                    assertThat(cause.getResponseBodyAsString())
                            .contains("access_token", "<redacted>")
                            .doesNotContain("leaked-access");
                    TokenEndpointErrorBody decoded = cause.getResponseBodyAs(TokenEndpointErrorBody.class);
                    assertThat(decoded.access_token).isEqualTo("<redacted>");
                })
                .verify();
    }

    @Test
    void sanitizedFailureUpdatesResponseCharsetToUtf8() {
        String responseBody = "{\"error\":\"invalid_client\",\"error_description\":\"é\","
                + "\"access_token\":\"leaked-access\"}";
        byte[] responseBytes = responseBody.getBytes(StandardCharsets.ISO_8859_1);
        WebClient webClient = WebClient.builder()
                .exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.BAD_REQUEST)
                        .header(HttpHeaders.CONTENT_TYPE,
                                new MediaType(MediaType.APPLICATION_JSON, StandardCharsets.ISO_8859_1).toString())
                        .header(HttpHeaders.CONTENT_LENGTH, Integer.toString(responseBytes.length))
                        .body(Flux.just(DefaultDataBufferFactory.sharedInstance.wrap(responseBytes)))
                        .build()))
                .build();

        OAuth2ClientCredentialsTokenProvider provider =
                OAuth2ClientCredentialsTokenProvider.builder(webClient)
                        .tokenUri("https://auth.example.com/oauth/token")
                        .clientId("client")
                        .clientSecret("client-secret")
                        .clientName("diagnostic-client")
                        .build();

        StepVerifier.create(provider.fetchToken())
                .expectErrorSatisfies(error -> {
                    WebClientResponseException cause = (WebClientResponseException) error.getCause();
                    assertThat(cause.getHeaders().getContentType().getCharset())
                            .isEqualTo(StandardCharsets.UTF_8);
                    assertThat(cause.getHeaders().getContentLength())
                            .isEqualTo(cause.getResponseBodyAsByteArray().length)
                            .isNotEqualTo(responseBytes.length);
                    assertThat(cause.getResponseBodyAsString()).contains("é", "<redacted>");
                    OAuthErrorBody decoded = cause.getResponseBodyAs(OAuthErrorBody.class);
                    assertThat(decoded.error_description).isEqualTo("é");
                })
                .verify();
    }

    @Test
    void tokenEndpointFailureCauseUsesConfiguredDecodersWithoutBlocking() {
        JsonMapper mapper = JsonMapper.builder()
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .build();
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(codecs -> codecs.defaultCodecs()
                        .jacksonJsonDecoder(new JacksonJsonDecoder(mapper)))
                .build();
        WebClient webClient = WebClient.builder()
                .exchangeStrategies(strategies)
                .exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.BAD_REQUEST, strategies)
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .body("{\"access_token\":\"leaked-access\"}")
                        .build()))
                .build();

        OAuth2ClientCredentialsTokenProvider provider =
                OAuth2ClientCredentialsTokenProvider.builder(webClient)
                        .tokenUri("https://auth.example.com/oauth/token")
                        .clientId("client")
                        .clientSecret("client-secret")
                        .clientName("diagnostic-client")
                        .build();

        Mono<String> decodedBody = provider.fetchToken()
                .thenReturn("unexpected")
                .onErrorResume(error -> {
                    assertThat(error).isInstanceOf(AuthProviderException.class);
                    WebClientResponseException cause = (WebClientResponseException) error.getCause();
                    return Mono.fromCallable(() -> cause.getResponseBodyAs(
                                    SnakeCaseTokenEndpointErrorBody.class).accessToken)
                            .subscribeOn(Schedulers.parallel());
                });

        StepVerifier.create(decodedBody)
                .expectNext("<redacted>")
                .verifyComplete();
    }

    @Test
    void tokenEndpointRedactsSensitiveFieldsBeforeRawSecretReplacement() {
        AtomicReference<MockClientHttpRequest> captured = captureMock();
        WebClient webClient = WebClient.builder()
                .exchangeFunction(request -> materializeAndRespond(request, captured, HttpStatus.BAD_REQUEST,
                        "{\"access_token\":\"leaked-access\",\"refresh_token\":\"leaked-refresh\"}"))
                .build();

        OAuth2ClientCredentialsTokenProvider provider =
                OAuth2ClientCredentialsTokenProvider.builder(webClient)
                        .tokenUri("https://auth.example.com/oauth/token")
                        .clientId("client")
                        .clientSecret("token")
                        .clientName("diagnostic-client")
                        .build();

        StepVerifier.create(provider.fetchToken())
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(AuthProviderException.class);
                    assertThat(error.getMessage())
                            .contains("access_token\":\"<redacted>", "refresh_token\":\"<redacted>")
                            .doesNotContain("leaked-access", "leaked-refresh");
                })
                .verify();
    }

    @Test
    void tokenEndpointRedactsUnicodeEscapedJsonFieldNames() {
        AtomicReference<MockClientHttpRequest> captured = captureMock();
        String escapedUnderscore = "\\" + "u005f";
        String body = "{\"access" + escapedUnderscore + "token\":\"leaked-access\","
                + "\"refresh" + escapedUnderscore + "token\":\"leaked-refresh\","
                + "\"id" + escapedUnderscore + "token\":\"leaked-id\"}";
        WebClient webClient = WebClient.builder()
                .exchangeFunction(request -> materializeAndRespond(request, captured, HttpStatus.BAD_REQUEST, body))
                .build();

        OAuth2ClientCredentialsTokenProvider provider =
                OAuth2ClientCredentialsTokenProvider.builder(webClient)
                        .tokenUri("https://auth.example.com/oauth/token")
                        .clientId("client")
                        .clientSecret("client-secret")
                        .clientName("diagnostic-client")
                        .build();

        StepVerifier.create(provider.fetchToken())
                .expectErrorSatisfies(error -> {
                    assertThat(error.getMessage())
                            .contains("access_token\":\"<redacted>",
                                    "refresh_token\":\"<redacted>",
                                    "id_token\":\"<redacted>")
                            .doesNotContain("leaked-access", "leaked-refresh", "leaked-id");
                    WebClientResponseException cause = (WebClientResponseException) error.getCause();
                    assertThat(cause.getResponseBodyAsString())
                            .doesNotContain("leaked-access", "leaked-refresh", "leaked-id");
                })
                .verify();
    }

    @Test
    void tokenEndpointPreservesLiteralUnicodeEscapeInJsonValue() {
        AtomicReference<MockClientHttpRequest> captured = captureMock();
        String literalUnicodeEscape = "\\\\" + "u0061";
        String expectedDescription = "literal " + "\\" + "u0061";
        String body = "{\"error_description\":\"literal " + literalUnicodeEscape
                + "\",\"access_token\":\"leaked-access\"}";
        WebClient webClient = WebClient.builder()
                .exchangeFunction(request -> materializeAndRespond(request, captured, HttpStatus.BAD_REQUEST, body))
                .build();

        OAuth2ClientCredentialsTokenProvider provider =
                OAuth2ClientCredentialsTokenProvider.builder(webClient)
                        .tokenUri("https://auth.example.com/oauth/token")
                        .clientId("client")
                        .clientSecret("client-secret")
                        .clientName("diagnostic-client")
                        .build();

        StepVerifier.create(provider.fetchToken())
                .expectErrorSatisfies(error -> {
                    WebClientResponseException cause = (WebClientResponseException) error.getCause();
                    assertThat(cause.getResponseBodyAsString()).doesNotContain("leaked-access");
                    OAuthErrorBody decoded = cause.getResponseBodyAs(OAuthErrorBody.class);
                    assertThat(decoded.error_description).isEqualTo(expectedDescription);
                })
                .verify();
    }

    @Test
    void tokenEndpointRedactsUnicodeEscapedClientSecretInGenericJsonValue() {
        AtomicReference<MockClientHttpRequest> captured = captureMock();
        String escapedSecret = "s3cr" + "\\" + "u0033t";
        String body = "{\"error_description\":\"bad secret " + escapedSecret + "\"}";
        WebClient webClient = WebClient.builder()
                .exchangeFunction(request -> materializeAndRespond(request, captured, HttpStatus.BAD_REQUEST, body))
                .build();

        OAuth2ClientCredentialsTokenProvider provider =
                OAuth2ClientCredentialsTokenProvider.builder(webClient)
                        .tokenUri("https://auth.example.com/oauth/token")
                        .clientId("client")
                        .clientSecret("s3cr3t")
                        .clientName("diagnostic-client")
                        .build();

        StepVerifier.create(provider.fetchToken())
                .expectErrorSatisfies(error -> {
                    assertThat(error.getMessage()).contains("bad secret <redacted>").doesNotContain(escapedSecret);
                    WebClientResponseException cause = (WebClientResponseException) error.getCause();
                    assertThat(cause.getResponseBodyAsString())
                            .contains("bad secret <redacted>")
                            .doesNotContain(escapedSecret);
                })
                .verify();
    }

    @Test
    void tokenEndpointRedactsUnicodeEscapedSensitiveFieldInNestedJson() {
        AtomicReference<MockClientHttpRequest> captured = captureMock();
        String nestedEscapedUnderscore = "\\\\" + "u005f";
        String body = "{\"debug\":\"{\\\"access" + nestedEscapedUnderscore
                + "token\\\":\\\"leaked-access\\\"}\"}";
        WebClient webClient = WebClient.builder()
                .exchangeFunction(request -> materializeAndRespond(request, captured, HttpStatus.BAD_REQUEST, body))
                .build();

        OAuth2ClientCredentialsTokenProvider provider =
                OAuth2ClientCredentialsTokenProvider.builder(webClient)
                        .tokenUri("https://auth.example.com/oauth/token")
                        .clientId("client")
                        .clientSecret("client-secret")
                        .clientName("diagnostic-client")
                        .build();

        StepVerifier.create(provider.fetchToken())
                .expectErrorSatisfies(error -> {
                    assertThat(error.getMessage())
                            .contains("access_token\\\":\\\"<redacted>")
                            .doesNotContain("leaked-access");
                    WebClientResponseException cause = (WebClientResponseException) error.getCause();
                    assertThat(cause.getResponseBodyAsString()).doesNotContain("leaked-access");
                })
                .verify();
    }

    @Test
    void tokenEndpointRedactsColonDelimitedSensitiveFields() {
        AtomicReference<MockClientHttpRequest> captured = captureMock();
        String body = "access_token: leaked-access; refresh_token : \"leaked refresh\"; "
                + "id_token: leaked-id, client_secret: client-secret";
        WebClient webClient = WebClient.builder()
                .exchangeFunction(request -> materializeAndRespond(request, captured, HttpStatus.BAD_REQUEST, body))
                .build();

        OAuth2ClientCredentialsTokenProvider provider =
                OAuth2ClientCredentialsTokenProvider.builder(webClient)
                        .tokenUri("https://auth.example.com/oauth/token")
                        .clientId("client")
                        .clientSecret("client-secret")
                        .clientName("diagnostic-client")
                        .build();

        StepVerifier.create(provider.fetchToken())
                .expectErrorSatisfies(error -> {
                    assertThat(error.getMessage())
                            .contains("access_token: <redacted>", "refresh_token : <redacted>",
                                    "id_token: <redacted>", "client_secret: <redacted>")
                            .doesNotContain("leaked-access", "leaked refresh", "leaked-id", "client-secret");
                    WebClientResponseException cause = (WebClientResponseException) error.getCause();
                    assertThat(cause.getResponseBodyAsString())
                            .doesNotContain("leaked-access", "leaked refresh", "leaked-id", "client-secret");
                })
                .verify();
    }

    @Test
    void tokenEndpointEscapedJsonSecretValuesAreFullyRedacted() {
        AtomicReference<MockClientHttpRequest> captured = captureMock();
        String escapedQuote = "\\\"";
        String quote = Character.toString((char) 34);
        String body = "{" + quote + "client_secret" + quote + ":" + quote + "prefix" + escapedQuote + "suffix" + quote
                + "," + quote + "access_token" + quote + ":" + quote + "opaque" + escapedQuote + "tail" + quote
                + "," + quote + "refresh_token" + quote + ":" + quote + "rotated" + escapedQuote + "tail" + quote + "}";
        WebClient webClient = WebClient.builder()
                .exchangeFunction(request -> materializeAndRespond(request, captured, HttpStatus.BAD_REQUEST, body))
                .build();

        OAuth2ClientCredentialsTokenProvider provider =
                OAuth2ClientCredentialsTokenProvider.builder(webClient)
                        .tokenUri("https://auth.example.com/oauth/token")
                        .clientId("client")
                        .clientSecret("prefix\"suffix")
                        .clientName("diagnostic-client")
                        .build();

        StepVerifier.create(provider.fetchToken())
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(AuthProviderException.class);
                    assertThat(error.getMessage())
                            .contains("client_secret\":\"<redacted>", "access_token\":\"<redacted>")
                            .doesNotContain("prefix", "suffix", "opaque", "tail", "rotated");
                })
                .verify();
    }

    @Test
    void tokenEndpointRedactsSensitiveFieldsInsideEscapedJsonString() {
        AtomicReference<MockClientHttpRequest> captured = captureMock();
        String quote = Character.toString((char) 34);
        String escapedQuote = "\\\"";
        String escapedPayload = escapedQuote + "access_token" + escapedQuote + ":" + escapedQuote + "leaked-access"
                + escapedQuote + "," + escapedQuote + "refresh_token" + escapedQuote + ":" + escapedQuote
                + "leaked-refresh" + escapedQuote;
        String body = "{" + quote + "debug" + quote + ":" + quote + "{" + escapedPayload + "}" + quote + "}";
        WebClient webClient = WebClient.builder()
                .exchangeFunction(request -> materializeAndRespond(request, captured, HttpStatus.BAD_REQUEST, body))
                .build();

        OAuth2ClientCredentialsTokenProvider provider =
                OAuth2ClientCredentialsTokenProvider.builder(webClient)
                        .tokenUri("https://auth.example.com/oauth/token")
                        .clientId("client")
                        .clientSecret("client-secret")
                        .clientName("diagnostic-client")
                        .build();

        StepVerifier.create(provider.fetchToken())
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(AuthProviderException.class);
                    assertThat(error.getMessage())
                            .contains("access_token", "refresh_token", "<redacted>")
                            .doesNotContain("leaked-access", "leaked-refresh");
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
                        .clientName("diagnostic-client")
                        .build();

        StepVerifier.create(provider.fetchToken())
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(AuthProviderException.class);
                    assertThat(error.getMessage())
                            .contains("HTTP 503", "server unavailable", "client_secret=<redacted>")
                            .doesNotContain("client-secret");
                    assertThat(error.getCause())
                            .isInstanceOf(WebClientResponseException.ServiceUnavailable.class);
                })
                .verify();
    }

    @Test
    void tokenEndpointErrorRedactsEchoedBasicAuthorization() {
        AtomicReference<MockClientHttpRequest> captured = captureMock();
        String basicCredential = Base64.getEncoder()
                .encodeToString("client:client-secret".getBytes(StandardCharsets.ISO_8859_1));
        String body = "proxy rejected Authorization: Basic " + basicCredential
                + " and Authorization=Basic " + basicCredential;
        WebClient webClient = WebClient.builder()
                .exchangeFunction(request -> materializeAndRespond(request, captured, HttpStatus.BAD_REQUEST, body))
                .build();

        OAuth2ClientCredentialsTokenProvider provider =
                OAuth2ClientCredentialsTokenProvider.builder(webClient)
                        .tokenUri("https://auth.example.com/oauth/token")
                        .clientId("client")
                        .clientSecret("client-secret")
                        .clientName("diagnostic-client")
                        .build();

        StepVerifier.create(provider.fetchToken())
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(AuthProviderException.class);
                    assertThat(error.getMessage())
                            .contains("Authorization: Basic <redacted>", "Authorization=Basic <redacted>")
                            .doesNotContain(basicCredential, "client-secret");
                })
                .verify();
    }

    @Test
    void tokenEndpointErrorRedactsUrlEncodedBasicAuthorization() {
        AtomicReference<MockClientHttpRequest> captured = captureMock();
        String basicCredential = Base64.getEncoder()
                .encodeToString("client:client-secret".getBytes(StandardCharsets.ISO_8859_1));
        String encodedCredential = basicCredential
                .replace("+", "%2b")
                .replace("/", "%2f")
                .replace("=", "%3d");
        String encodedAuthorization = "Authorization%3a%20Basic%20" + encodedCredential;
        WebClient webClient = WebClient.builder()
                .exchangeFunction(request -> materializeAndRespond(request, captured, HttpStatus.BAD_REQUEST,
                        "proxy rejected " + encodedAuthorization))
                .build();

        OAuth2ClientCredentialsTokenProvider provider =
                OAuth2ClientCredentialsTokenProvider.builder(webClient)
                        .tokenUri("https://auth.example.com/oauth/token")
                        .clientId("client")
                        .clientSecret("client-secret")
                        .clientName("diagnostic-client")
                        .build();

        StepVerifier.create(provider.fetchToken())
                .expectErrorSatisfies(error -> {
                    assertThat(encodedCredential).contains("%3d");
                    assertThat(error).isInstanceOf(AuthProviderException.class);
                    assertThat(error.getMessage())
                            .contains("Authorization%3a%20Basic%20<redacted>")
                            .doesNotContain(encodedCredential, basicCredential, "client-secret");
                    WebClientResponseException cause = (WebClientResponseException) error.getCause();
                    assertThat(cause.getResponseBodyAsString())
                            .contains("Authorization%3a%20Basic%20<redacted>")
                            .doesNotContain(encodedCredential, basicCredential, "client-secret");
                })
                .verify();
    }

    @Test
    void tokenEndpointErrorRedactsLatin1BasicCredentialEcho() {
        AtomicReference<MockClientHttpRequest> captured = captureMock();
        String basicCredential = Base64.getEncoder()
                .encodeToString("client:s\u00ebcret".getBytes(StandardCharsets.ISO_8859_1));
        WebClient webClient = WebClient.builder()
                .exchangeFunction(request -> materializeAndRespond(request, captured, HttpStatus.BAD_REQUEST,
                        "{\"Authorization\":\"Basic " + basicCredential + "\"}"))
                .build();

        OAuth2ClientCredentialsTokenProvider provider =
                OAuth2ClientCredentialsTokenProvider.builder(webClient)
                        .tokenUri("https://auth.example.com/oauth/token")
                        .clientId("client")
                        .clientSecret("s\u00ebcret")
                        .clientName("diagnostic-client")
                        .build();

        StepVerifier.create(provider.fetchToken())
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(AuthProviderException.class);
                    assertThat(error.getMessage())
                            .contains("Basic <redacted>")
                            .doesNotContain(basicCredential);
                })
                .verify();
    }

    @Test
    void tokenEndpointErrorRedactsUrlEncodedSecretEcho() {
        AtomicReference<MockClientHttpRequest> captured = captureMock();
        WebClient webClient = WebClient.builder()
                .exchangeFunction(request -> materializeAndRespond(request, captured, HttpStatus.BAD_REQUEST,
                        "bad form: client_secret%3Ds3cr3t&client_id%3Dclient"))
                .build();

        OAuth2ClientCredentialsTokenProvider provider =
                OAuth2ClientCredentialsTokenProvider.builder(webClient)
                        .tokenUri("https://auth.example.com/oauth/token")
                        .clientId("client")
                        .clientSecret("s3cr3t")
                        .authStyle(OAuth2ClientCredentialsTokenProvider.AuthStyle.FORM_POST)
                        .clientName("diagnostic-client")
                        .build();

        StepVerifier.create(provider.fetchToken())
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(AuthProviderException.class);
                    assertThat(error.getMessage())
                            .contains("client_secret%3D<redacted>")
                            .doesNotContain("s3cr3t");
                })
                .verify();
    }

    @Test
    void tokenEndpointErrorRedactsPercentEncodedJsonTokens() {
        AtomicReference<MockClientHttpRequest> captured = captureMock();
        String body = "%7b%22access_token%22%3a%20%22leaked-access%22%2c"
                + "%22refresh_token%22%3a%22leaked-refresh%22%7d";
        WebClient webClient = WebClient.builder()
                .exchangeFunction(request -> materializeAndRespond(request, captured, HttpStatus.BAD_REQUEST, body))
                .build();

        OAuth2ClientCredentialsTokenProvider provider =
                OAuth2ClientCredentialsTokenProvider.builder(webClient)
                        .tokenUri("https://auth.example.com/oauth/token")
                        .clientId("client")
                        .clientSecret("client-secret")
                        .clientName("diagnostic-client")
                        .build();

        StepVerifier.create(provider.fetchToken())
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(AuthProviderException.class);
                    assertThat(error.getMessage())
                            .contains("%22access_token%22%3a%20%22<redacted>%22",
                                    "%22refresh_token%22%3a%22<redacted>%22")
                            .doesNotContain("leaked-access", "leaked-refresh");
                    WebClientResponseException cause = (WebClientResponseException) error.getCause();
                    assertThat(cause.getResponseBodyAsString())
                            .contains("%22access_token%22%3a%20%22<redacted>%22",
                                    "%22refresh_token%22%3a%22<redacted>%22")
                            .doesNotContain("leaked-access", "leaked-refresh");
                })
                .verify();
    }

    @Test
    void tokenEndpointErrorRedactsDecodedFormSecretWithWhitespace() {
        AtomicReference<MockClientHttpRequest> captured = captureMock();
        WebClient webClient = WebClient.builder()
                .exchangeFunction(request -> materializeAndRespond(request, captured, HttpStatus.BAD_REQUEST,
                        "bad form: client_secret=top secret&client_id=client"))
                .build();

        OAuth2ClientCredentialsTokenProvider provider =
                OAuth2ClientCredentialsTokenProvider.builder(webClient)
                        .tokenUri("https://auth.example.com/oauth/token")
                        .clientId("client")
                        .clientSecret("top secret")
                        .authStyle(OAuth2ClientCredentialsTokenProvider.AuthStyle.FORM_POST)
                        .clientName("diagnostic-client")
                        .build();

        StepVerifier.create(provider.fetchToken())
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(AuthProviderException.class);
                    assertThat(error.getMessage())
                            .contains("client_secret=<redacted>&client_id=client")
                            .doesNotContain("top secret", "<redacted> secret");
                    WebClientResponseException cause = (WebClientResponseException) error.getCause();
                    assertThat(cause.getResponseBodyAsString())
                            .contains("client_secret=<redacted>&client_id=client")
                            .doesNotContain("top secret", "<redacted> secret");
                })
                .verify();
    }

    @Test
    void manualProviderFailureIsWrappedWithRequestClientName() {
        AtomicReference<MockClientHttpRequest> captured = captureMock();
        WebClient webClient = WebClient.builder()
                .exchangeFunction(request -> materializeAndRespond(request, captured, HttpStatus.BAD_REQUEST,
                        "{\"error\":\"invalid_client\"}"))
                .build();
        OAuth2ClientCredentialsTokenProvider tokenProvider =
                OAuth2ClientCredentialsTokenProvider.builder(webClient)
                        .tokenUri("https://auth.example.com/oauth/token")
                        .clientId("client")
                        .clientSecret("client-secret")
                        .build();
        RefreshingBearerAuthProvider provider = new RefreshingBearerAuthProvider(tokenProvider);
        ClientRequest request = ClientRequest.create(HttpMethod.GET, URI.create("https://api.example.com/payments"))
                .build();

        StepVerifier.create(provider.getAuth(new AuthRequest("manual-payment", request)))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(AuthProviderException.class);
                    AuthProviderException authError = (AuthProviderException) error;
                    assertThat(authError.getClientName()).isEqualTo("manual-payment");
                    assertThat(authError.getMessage()).contains("OAuth2 token endpoint returned HTTP 400");
                    assertThat(authError.getCause()).isInstanceOf(WebClientResponseException.BadRequest.class);
                    WebClientResponseException cause = (WebClientResponseException) authError.getCause();
                    assertThat(cause.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
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
                        .clientName("diagnostic-client")
                        .build();

        StepVerifier.create(provider.fetchToken())
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(AuthProviderException.class);
                    assertThat(error.getMessage())
                            .contains("malformed JSON token response")
                            .doesNotContain("leaked-access-token", "client-secret");
                    assertThat(error.getCause())
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessage("OAuth2 token response decoding failed")
                            .hasMessageNotContaining("leaked-access-token")
                            .hasMessageNotContaining("client-secret");
                    assertThat(error.getCause().getCause()).isNull();
                })
                .verify();
    }

    @Test
    void manualProviderMalformedTokenKeepsExplicitDiagnostic() {
        AtomicReference<MockClientHttpRequest> captured = captureMock();
        WebClient webClient = WebClient.builder()
                .exchangeFunction(request -> materializeAndRespond(request, captured,
                        "not-json leaked-access-token client_secret=client-secret"))
                .build();
        OAuth2ClientCredentialsTokenProvider tokenProvider =
                OAuth2ClientCredentialsTokenProvider.builder(webClient)
                        .tokenUri("https://auth.example.com/oauth/token")
                        .clientId("client")
                        .clientSecret("client-secret")
                        .build();
        RefreshingBearerAuthProvider provider = new RefreshingBearerAuthProvider(tokenProvider);
        ClientRequest request = ClientRequest.create(HttpMethod.GET, URI.create("https://api.example.com/payments"))
                .build();

        StepVerifier.create(provider.getAuth(new AuthRequest("manual-payment", request)))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(AuthProviderException.class);
                    assertThat(error.getMessage())
                            .contains("malformed JSON token response")
                            .doesNotContain("leaked-access-token", "client-secret");
                    assertThat(error.getCause())
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("malformed JSON token response");
                    assertThat(error.getCause().getCause())
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessage("OAuth2 token response decoding failed");
                })
                .verify();
    }

    @Test
    void emptySuccessfulTokenResponseProducesMissingTokenDiagnostic() {
        WebClient webClient = WebClient.builder()
                .exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.NO_CONTENT).build()))
                .build();

        OAuth2ClientCredentialsTokenProvider provider =
                OAuth2ClientCredentialsTokenProvider.builder(webClient)
                        .tokenUri("https://auth.example.com/oauth/token")
                        .clientId("client")
                        .clientSecret("client-secret")
                        .clientName("diagnostic-client")
                        .build();

        StepVerifier.create(provider.fetchToken())
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(AuthProviderException.class);
                    assertThat(error.getMessage()).contains("no access_token");
                    assertThat(error.getCause())
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessage("missing access_token");
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
                        .clientName("diagnostic-client")
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
    void manualProviderMissingAccessTokenKeepsExplicitDiagnostic() {
        AtomicReference<MockClientHttpRequest> captured = captureMock();
        WebClient webClient = WebClient.builder()
                .exchangeFunction(request -> materializeAndRespond(request, captured,
                        "{\"refresh_token\":\"leaked-refresh-token\",\"expires_in\":60}"))
                .build();
        OAuth2ClientCredentialsTokenProvider tokenProvider =
                OAuth2ClientCredentialsTokenProvider.builder(webClient)
                        .tokenUri("https://auth.example.com/oauth/token")
                        .clientId("client")
                        .clientSecret("client-secret")
                        .build();
        RefreshingBearerAuthProvider provider = new RefreshingBearerAuthProvider(tokenProvider);
        ClientRequest request = ClientRequest.create(HttpMethod.GET, URI.create("https://api.example.com/payments"))
                .build();

        StepVerifier.create(provider.getAuth(new AuthRequest("manual-payment", request)))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(AuthProviderException.class);
                    AuthProviderException authError = (AuthProviderException) error;
                    assertThat(authError.getClientName()).isEqualTo("manual-payment");
                    assertThat(authError.getCause())
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("OAuth2 token endpoint returned no access_token")
                            .hasMessageNotContaining("malformed JSON");
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

    @SuppressWarnings("checkstyle:MemberName")
    static final class TokenEndpointErrorBody {
        public String access_token;
    }

    @SuppressWarnings("checkstyle:MemberName")
    static final class OAuthErrorBody {
        public String error;
        public String error_description;
    }

    static final class SnakeCaseTokenEndpointErrorBody {
        public String accessToken;
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
