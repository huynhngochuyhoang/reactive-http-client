package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.exception.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ProblemDetailErrorResponseMapperTest {

    private final DefaultErrorDecoder decoder = new DefaultErrorDecoder(
            "problem-client",
            List.of(new ProblemDetailErrorResponseMapper(TestJsonCodecs.jsonCodec())));

    @Test
    void mapsProblemJson4xxToProblemDetailHttpClientException() {
        String body = """
                {"type":"https://example.test/problems/invalid-order","title":"Invalid order","status":422,"detail":"Quantity is required"}
                """.trim();
        ClientResponse response = ClientResponse.create(HttpStatus.UNPROCESSABLE_ENTITY)
                .header("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE)
                .body(body)
                .build();

        StepVerifier.create(decoder.decode(response))
                .assertNext(error -> {
                    assertThat(error).isInstanceOf(ProblemDetailHttpClientException.class);
                    ProblemDetailHttpClientException ex = (ProblemDetailHttpClientException) error;
                    assertThat(ex.getStatusCode()).isEqualTo(422);
                    assertThat(ex.getResponseBody()).isEqualTo(body);
                    assertThat(ex.getErrorCategory()).isEqualTo(ErrorCategory.CLIENT_ERROR);
                    assertThat(ex.getProblemDetail().getTitle()).isEqualTo("Invalid order");
                    assertThat(ex.getProblemDetail().getDetail()).isEqualTo("Quantity is required");
                })
                .verifyComplete();
    }

    @Test
    void mapsProblemJsonBodyLargerThanDefaultErrorBodyCap() {
        String detail = "x".repeat(5000);
        String body = "{\"type\":\"about:blank\",\"title\":\"Large problem\",\"status\":422,\"detail\":\""
                + detail + "\"}";
        ClientResponse response = ClientResponse.create(HttpStatus.UNPROCESSABLE_ENTITY)
                .header("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE)
                .body(body)
                .build();

        StepVerifier.create(decoder.decode(response))
                .assertNext(error -> {
                    assertThat(error).isInstanceOf(ProblemDetailHttpClientException.class);
                    ProblemDetailHttpClientException ex = (ProblemDetailHttpClientException) error;
                    assertThat(ex.getStatusCode()).isEqualTo(422);
                    assertThat(ex.getErrorCategory()).isEqualTo(ErrorCategory.CLIENT_ERROR);
                    assertThat(ex.getProblemDetail().getTitle()).isEqualTo("Large problem");
                    assertThat(ex.getProblemDetail().getDetail()).isEqualTo(detail);
                })
                .verifyComplete();
    }

    @Test
    void mapsProblemJson5xxToProblemDetailRemoteServiceException() {
        String body = """
                {"type":"about:blank","title":"Unavailable","status":503,"detail":"Try later"}
                """.trim();
        ClientResponse response = ClientResponse.create(HttpStatus.SERVICE_UNAVAILABLE)
                .header("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE)
                .body(body)
                .build();

        StepVerifier.create(decoder.decode(response))
                .assertNext(error -> {
                    assertThat(error).isInstanceOf(ProblemDetailRemoteServiceException.class);
                    ProblemDetailRemoteServiceException ex = (ProblemDetailRemoteServiceException) error;
                    assertThat(ex.getStatusCode()).isEqualTo(503);
                    assertThat(ex.getResponseBody()).isEqualTo(body);
                    assertThat(ex.getErrorCategory()).isEqualTo(ErrorCategory.SERVER_ERROR);
                    assertThat(ex.getProblemDetail().getTitle()).isEqualTo("Unavailable");
                })
                .verifyComplete();
    }

    @Test
    void ignoresProblemJson3xxStatusesWhenMapperIsUsedDirectly() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        ErrorResponseContext context = new ErrorResponseContext(
                "problem-client",
                302,
                "not-json",
                headers,
                "GET",
                "https://api.example.test/redirect",
                null);

        Optional<? extends Throwable> mapped = new ProblemDetailErrorResponseMapper(TestJsonCodecs.jsonCodec()).map(context);

        assertThat(mapped).isEmpty();
    }

    @Test
    void fallsBackWhenContentTypeIsMissing() {
        ClientResponse response = ClientResponse.create(HttpStatus.BAD_REQUEST)
                .body("{\"title\":\"Invalid\"}")
                .build();

        StepVerifier.create(decoder.decode(response))
                .assertNext(error -> {
                    assertThat(error).isExactlyInstanceOf(HttpClientException.class);
                    HttpClientException ex = (HttpClientException) error;
                    assertThat(ex.getStatusCode()).isEqualTo(400);
                    assertThat(ex.getResponseBody()).isEqualTo("{\"title\":\"Invalid\"}");
                    assertThat(ex.getErrorCategory()).isEqualTo(ErrorCategory.CLIENT_ERROR);
                })
                .verifyComplete();
    }

    @Test
    void fallsBackWhenProblemJsonBodyIsInvalid() {
        ClientResponse response = ClientResponse.create(HttpStatus.BAD_GATEWAY)
                .header("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE)
                .body("not-json")
                .build();

        StepVerifier.create(decoder.decode(response))
                .assertNext(error -> {
                    assertThat(error).isExactlyInstanceOf(RemoteServiceException.class);
                    RemoteServiceException ex = (RemoteServiceException) error;
                    assertThat(ex.getStatusCode()).isEqualTo(502);
                    assertThat(ex.getResponseBody()).isEqualTo("not-json");
                    assertThat(ex.getErrorCategory()).isEqualTo(ErrorCategory.SERVER_ERROR);
                })
                .verifyComplete();
    }

    @Test
    void fallsBackWhenContentTypeIsNotProblemJson() {
        ClientResponse response = ClientResponse.create(HttpStatus.BAD_REQUEST)
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .body("{\"title\":\"Invalid\"}")
                .build();

        StepVerifier.create(decoder.decode(response))
                .assertNext(error -> assertThat(error).isExactlyInstanceOf(HttpClientException.class))
                .verifyComplete();
    }
}
