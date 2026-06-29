package io.github.huynhngochuyhoang.httpstarter.auth;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.client.ClientRequest;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class AwsSigV4AuthProviderTest {

    private static final Clock AWS_EXAMPLE_CLOCK =
            Clock.fixed(Instant.parse("2013-05-24T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void signsS3GetRequestWithAwsReferenceCredentials() {
        AwsSigV4AuthProvider provider = AwsSigV4AuthProvider.builder()
                .accessKeyId("AKIAIOSFODNN7EXAMPLE")
                .secretAccessKey("wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY")
                .region("us-east-1")
                .service("s3")
                .clock(AWS_EXAMPLE_CLOCK)
                .build();

        ClientRequest request = ClientRequest.create(
                        HttpMethod.GET,
                        URI.create("https://examplebucket.s3.amazonaws.com/test.txt"))
                .header("Range", "bytes=0-9")
                .build();

        StepVerifier.create(provider.getAuth(new AuthRequest("s3-client", request)))
                .assertNext(auth -> {
                    assertThat(auth.getHeaders().get("x-amz-date")).isEqualTo("20130524T000000Z");
                    assertThat(auth.getHeaders().get("x-amz-content-sha256"))
                            .isEqualTo("e3b0c44298fc1c149afbf4c8996fb924"
                                    + "27ae41e4649b934ca495991b7852b855");
                    assertThat(auth.getHeaders().get("Authorization"))
                            .isEqualTo("AWS4-HMAC-SHA256 "
                                    + "Credential=AKIAIOSFODNN7EXAMPLE/20130524/us-east-1/s3/aws4_request,"
                                    + "SignedHeaders=host;range;x-amz-content-sha256;x-amz-date,"
                                    + "Signature=67fe34c8530db585abddc51067328adfedb6e42487d2566dc7d927d6e2722900");
                })
                .verifyComplete();
    }

    @Test
    void signsRawRequestBodyBytes() {
        AwsSigV4AuthProvider provider = AwsSigV4AuthProvider.builder()
                .accessKeyId("AKIAIOSFODNN7EXAMPLE")
                .secretAccessKey("wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY")
                .region("us-east-1")
                .service("execute-api")
                .clock(Clock.fixed(Instant.parse("2026-05-13T12:00:00Z"), ZoneOffset.UTC))
                .build();

        byte[] rawBody = "{\"amount\":200}".getBytes(StandardCharsets.UTF_8);
        ClientRequest request = ClientRequest.create(
                        HttpMethod.POST,
                        URI.create("https://abc123.execute-api.us-east-1.amazonaws.com/prod/payments"))
                .attribute(AuthRequest.REQUEST_RAW_BODY_ATTRIBUTE, rawBody)
                .build();

        StepVerifier.create(provider.getAuth(new AuthRequest("api-client", request, rawBody)))
                .assertNext(auth -> assertThat(auth.getHeaders().get("x-amz-content-sha256"))
                        .isEqualTo("1cbbc951d99ac7588df0547a8abdc67f4c28a63a8d94c6a5edd5c6843f4e4c6e"))
                .verifyComplete();
    }

    @Test
    void signsStringRequestBodyBytes() {
        AwsSigV4AuthProvider provider = AwsSigV4AuthProvider.builder()
                .accessKeyId("AKIAIOSFODNN7EXAMPLE")
                .secretAccessKey("wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY")
                .region("us-east-1")
                .service("execute-api")
                .clock(Clock.fixed(Instant.parse("2026-05-13T12:00:00Z"), ZoneOffset.UTC))
                .build();

        String body = "{\"message\":\"hello\"}";
        ClientRequest request = ClientRequest.create(
                        HttpMethod.POST,
                        URI.create("https://abc123.execute-api.us-east-1.amazonaws.com/prod/messages"))
                .build();

        StepVerifier.create(provider.getAuth(new AuthRequest("api-client", request, body)))
                .assertNext(auth -> assertThat(auth.getHeaders().get("x-amz-content-sha256"))
                        .isEqualTo(sha256Hex(body.getBytes(StandardCharsets.UTF_8))))
                .verifyComplete();
    }

    @Test
    void rejectsPublisherBodiesInsteadOfSigningEmptyPayload() {
        AwsSigV4AuthProvider provider = AwsSigV4AuthProvider.builder()
                .accessKeyId("AKIAIOSFODNN7EXAMPLE")
                .secretAccessKey("wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY")
                .region("us-east-1")
                .service("s3")
                .clock(AWS_EXAMPLE_CLOCK)
                .build();

        ClientRequest request = ClientRequest.create(
                        HttpMethod.PUT,
                        URI.create("https://examplebucket.s3.amazonaws.com/upload.txt"))
                .build();

        StepVerifier.create(provider.getAuth(new AuthRequest("s3-client", request, Flux.just("payload"))))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("cannot sign Publisher request bodies")
                        .hasMessageContaining("raw bytes are not materialized"))
                .verify();
    }

    @Test
    void signsAlreadyEncodedPathAndQueryWithoutDoubleEncoding() {
        AwsSigV4AuthProvider provider = AwsSigV4AuthProvider.builder()
                .accessKeyId("AKIAIOSFODNN7EXAMPLE")
                .secretAccessKey("wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY")
                .region("us-east-1")
                .service("execute-api")
                .clock(Clock.fixed(Instant.parse("2026-05-13T12:00:00Z"), ZoneOffset.UTC))
                .build();

        ClientRequest request = ClientRequest.create(
                        HttpMethod.GET,
                        URI.create("https://example.amazonaws.com/photos/my%20cat/%E2%9C%93?prefix=foo%2Fbar&q=white%20space"))
                .build();

        StepVerifier.create(provider.getAuth(new AuthRequest("api-client", request)))
                .assertNext(auth -> assertThat(auth.getHeaders().get("Authorization"))
                        .isEqualTo("AWS4-HMAC-SHA256 "
                                + "Credential=AKIAIOSFODNN7EXAMPLE/20260513/us-east-1/execute-api/aws4_request,"
                                + "SignedHeaders=host;x-amz-content-sha256;x-amz-date,"
                                + "Signature=5d21b737b971229bc326fe4f62e842ebe78f0bbd5735529f706fcb4357b8dc77"))
                .verifyComplete();
    }

    @Test
    void builderRejectsMissingRequiredFields() {
        assertThatIllegalArgumentException(() -> AwsSigV4AuthProvider.builder()
                .secretAccessKey("secret").region("us-east-1").service("s3").build());
        assertThatIllegalArgumentException(() -> AwsSigV4AuthProvider.builder()
                .accessKeyId("key").region("us-east-1").service("s3").build());
        assertThatIllegalArgumentException(() -> AwsSigV4AuthProvider.builder()
                .accessKeyId("key").secretAccessKey("secret").service("s3").build());
        assertThatIllegalArgumentException(() -> AwsSigV4AuthProvider.builder()
                .accessKeyId("key").secretAccessKey("secret").region("us-east-1").build());
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to calculate SHA-256", e);
        }
    }

    private static void assertThatIllegalArgumentException(Runnable action) {
        try {
            action.run();
            throw new AssertionError("Expected IllegalArgumentException");
        } catch (IllegalArgumentException ignored) {
            // expected
        }
    }
}
