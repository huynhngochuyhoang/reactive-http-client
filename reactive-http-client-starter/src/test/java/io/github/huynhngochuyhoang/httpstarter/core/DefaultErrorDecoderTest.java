package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.exception.ErrorCategory;
import io.github.huynhngochuyhoang.httpstarter.exception.HttpClientException;
import io.github.huynhngochuyhoang.httpstarter.exception.RemoteServiceException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies that {@link DefaultErrorDecoder} maps HTTP error responses
 * to the correct exception types and {@link ErrorCategory} values.
 */
class DefaultErrorDecoderTest {

    private final DefaultErrorDecoder decoder = new DefaultErrorDecoder();

    // -------------------------------------------------------------------------
    // 429 – Rate Limited
    // -------------------------------------------------------------------------

    @Test
    void shouldDecodeRateLimitedResponseAs429HttpClientExceptionWithRateLimitedCategory() {
        ClientResponse response = ClientResponse.create(HttpStatus.TOO_MANY_REQUESTS)
                .header("Content-Type", MediaType.TEXT_PLAIN_VALUE)
                .body("Rate limit exceeded")
                .build();

        StepVerifier.create(decoder.decode(response))
                .assertNext(ex -> {
                    assertEquals(HttpClientException.class, ex.getClass());
                    HttpClientException hce = (HttpClientException) ex;
                    assertEquals(429, hce.getStatusCode());
                    assertEquals("Rate limit exceeded", hce.getResponseBody());
                    assertEquals(ErrorCategory.RATE_LIMITED, hce.getErrorCategory());
                })
                .verifyComplete();
    }

    // -------------------------------------------------------------------------
    // 4xx – Client Errors (non-429)
    // -------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403, 404, 409, 422})
    void shouldDecodeClientErrorResponseAsHttpClientExceptionWithClientErrorCategory(int statusCode) {
        ClientResponse response = ClientResponse.create(HttpStatus.valueOf(statusCode))
                .header("Content-Type", MediaType.TEXT_PLAIN_VALUE)
                .body("Client error")
                .build();

        StepVerifier.create(decoder.decode(response))
                .assertNext(ex -> {
                    assertEquals(HttpClientException.class, ex.getClass());
                    HttpClientException hce = (HttpClientException) ex;
                    assertEquals(statusCode, hce.getStatusCode());
                    assertEquals(ErrorCategory.CLIENT_ERROR, hce.getErrorCategory());
                })
                .verifyComplete();
    }

    @Test
    void shouldDecodeEmptyBodyClientError() {
        ClientResponse response = ClientResponse.create(HttpStatus.NOT_FOUND)
                .build();

        StepVerifier.create(decoder.decode(response))
                .assertNext(ex -> {
                    HttpClientException hce = (HttpClientException) ex;
                    assertEquals(404, hce.getStatusCode());
                    assertEquals("", hce.getResponseBody());
                    assertEquals(ErrorCategory.CLIENT_ERROR, hce.getErrorCategory());
                })
                .verifyComplete();
    }

    // -------------------------------------------------------------------------
    // 5xx – Server Errors
    // -------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(ints = {500, 502, 503, 504})
    void shouldDecodeServerErrorResponseAsRemoteServiceExceptionWithServerErrorCategory(int statusCode) {
        ClientResponse response = ClientResponse.create(HttpStatus.valueOf(statusCode))
                .header("Content-Type", MediaType.TEXT_PLAIN_VALUE)
                .body("Server error")
                .build();

        StepVerifier.create(decoder.decode(response))
                .assertNext(ex -> {
                    assertEquals(RemoteServiceException.class, ex.getClass());
                    RemoteServiceException rse = (RemoteServiceException) ex;
                    assertEquals(statusCode, rse.getStatusCode());
                    assertEquals("Server error", rse.getResponseBody());
                    assertEquals(ErrorCategory.SERVER_ERROR, rse.getErrorCategory());
                })
                .verifyComplete();
    }

    @Test
    void shouldDecodeInternalServerErrorWithBody() {
        ClientResponse response = ClientResponse.create(HttpStatus.INTERNAL_SERVER_ERROR)
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .body("{\"error\":\"Something went wrong\"}")
                .build();

        StepVerifier.create(decoder.decode(response))
                .assertNext(ex -> {
                    RemoteServiceException rse = (RemoteServiceException) ex;
                    assertEquals(500, rse.getStatusCode());
                    assertEquals("{\"error\":\"Something went wrong\"}", rse.getResponseBody());
                    assertEquals(ErrorCategory.SERVER_ERROR, rse.getErrorCategory());
                })
                .verifyComplete();
    }
}
