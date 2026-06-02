package io.github.huynhngochuyhoang.httpstarter.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.huynhngochuyhoang.httpstarter.exception.RemoteServiceException;
import io.netty.buffer.PooledByteBufAllocator;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.NettyDataBufferFactory;
import org.springframework.core.io.buffer.PooledDataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorBodyCaptureTest {

    private static final int DEFAULT_CAP = 4096;
    private static final int PROBLEM_DETAIL_CAP = 64 * 1024;
    private static final NettyDataBufferFactory BUFFER_FACTORY =
            new NettyDataBufferFactory(PooledByteBufAllocator.DEFAULT);

    @Test
    void compatibilityConstructorTreatsProvidedBodyAsComplete() {
        ErrorResponseContext context = new ErrorResponseContext(
                "test-client", 400, "caf\u00e9", HttpHeaders.EMPTY, "POST", "/items", null);

        assertThat(context.responseBodyTruncated()).isFalse();
        assertThat(context.retainedResponseBodyBytes()).isEqualTo(5);
    }

    @Test
    void exposesCompleteDefaultMapperBodyMetadata() {
        AtomicReference<ErrorResponseContext> captured = new AtomicReference<>();
        DefaultErrorDecoder decoder = capturingDecoder(captured);

        StepVerifier.create(decoder.decode(response(HttpStatus.BAD_REQUEST, MediaType.TEXT_PLAIN, "complete")))
                .expectNextCount(1)
                .verifyComplete();

        assertThat(captured.get().responseBody()).isEqualTo("complete");
        assertThat(captured.get().responseBodyTruncated()).isFalse();
        assertThat(captured.get().retainedResponseBodyBytes()).isEqualTo(8);
    }

    @Test
    void exposesTruncatedDefaultMapperBodyMetadata() {
        AtomicReference<ErrorResponseContext> captured = new AtomicReference<>();
        DefaultErrorDecoder decoder = capturingDecoder(captured);

        StepVerifier.create(decoder.decode(response(
                        HttpStatus.BAD_REQUEST, MediaType.TEXT_PLAIN, "x".repeat(DEFAULT_CAP + 100))))
                .assertNext(error -> assertThat(error).hasFieldOrPropertyWithValue("responseBody", "x".repeat(DEFAULT_CAP)))
                .verifyComplete();

        assertThat(captured.get().responseBody()).hasSize(DEFAULT_CAP);
        assertThat(captured.get().responseBodyTruncated()).isTrue();
        assertThat(captured.get().retainedResponseBodyBytes()).isEqualTo(DEFAULT_CAP);
    }

    @Test
    void exposesCompleteProblemDetailMapperBodyMetadata() {
        AtomicReference<ErrorResponseContext> captured = new AtomicReference<>();
        DefaultErrorDecoder decoder = capturingDecoder(captured);
        String body = "x".repeat(DEFAULT_CAP + 100);

        StepVerifier.create(decoder.decode(response(HttpStatus.BAD_REQUEST, MediaType.APPLICATION_PROBLEM_JSON, body)))
                .expectNextCount(1)
                .verifyComplete();

        assertThat(captured.get().responseBody()).isEqualTo(body);
        assertThat(captured.get().responseBodyTruncated()).isFalse();
        assertThat(captured.get().retainedResponseBodyBytes()).isEqualTo(body.length());
    }

    @Test
    void exposesTruncatedProblemDetailMapperBodyMetadata() {
        AtomicReference<ErrorResponseContext> captured = new AtomicReference<>();
        DefaultErrorDecoder decoder = capturingDecoder(captured);

        StepVerifier.create(decoder.decode(response(
                        HttpStatus.BAD_GATEWAY, MediaType.APPLICATION_PROBLEM_JSON, "x".repeat(PROBLEM_DETAIL_CAP + 100))))
                .expectNextCount(1)
                .verifyComplete();

        assertThat(captured.get().responseBody()).hasSize(PROBLEM_DETAIL_CAP);
        assertThat(captured.get().responseBodyTruncated()).isTrue();
        assertThat(captured.get().retainedResponseBodyBytes()).isEqualTo(PROBLEM_DETAIL_CAP);
    }

    @Test
    void truncatedProblemDetailBodyFallsBackWithoutExposingMoreThanGenericCap() {
        AtomicReference<ErrorResponseContext> captured = new AtomicReference<>();
        ErrorResponseMapper capturingMapper = context -> {
            captured.set(context);
            return Optional.empty();
        };
        DefaultErrorDecoder decoder = new DefaultErrorDecoder(
                "test-client", List.of(capturingMapper, new ProblemDetailErrorResponseMapper(new ObjectMapper())));
        String body = "{\"title\":\"Large problem\",\"detail\":\"" + "x".repeat(PROBLEM_DETAIL_CAP) + "\"}";

        StepVerifier.create(decoder.decode(response(HttpStatus.BAD_GATEWAY, MediaType.APPLICATION_PROBLEM_JSON, body)))
                .assertNext(error -> {
                    assertThat(error).isInstanceOf(RemoteServiceException.class);
                    assertThat(((RemoteServiceException) error).getStatusCode()).isEqualTo(502);
                    assertThat(((RemoteServiceException) error).getResponseBody()).hasSize(DEFAULT_CAP);
                })
                .verifyComplete();

        assertThat(captured.get().responseBodyTruncated()).isTrue();
        assertThat(captured.get().retainedResponseBodyBytes()).isEqualTo(PROBLEM_DETAIL_CAP);
    }

    @Test
    void releasesOversizedChunkedBuffersAfterSuccessfulMapping() {
        List<PooledDataBuffer> buffers = pooledBuffers(3, 2048);
        ErrorResponseMapper mapper = context -> Optional.of(new IllegalStateException("mapped"));
        DefaultErrorDecoder decoder = new DefaultErrorDecoder("test-client", List.of(mapper));

        StepVerifier.create(decoder.decode(response(HttpStatus.BAD_REQUEST, MediaType.TEXT_PLAIN, buffers)))
                .assertNext(error -> assertThat(error).hasMessage("mapped"))
                .verifyComplete();

        assertReleased(buffers);
    }

    @Test
    void releasesOversizedChunkedBuffersWhenTruncatedMapperFallsBack() {
        AtomicReference<ErrorResponseContext> captured = new AtomicReference<>();
        List<PooledDataBuffer> buffers = pooledBuffers(3, 2048);
        ErrorResponseMapper mapper = context -> {
            captured.set(context);
            throw new IllegalArgumentException("invalid structured body");
        };
        DefaultErrorDecoder decoder = new DefaultErrorDecoder("test-client", List.of(mapper));

        StepVerifier.create(decoder.decode(response(HttpStatus.BAD_GATEWAY, MediaType.TEXT_PLAIN, buffers)))
                .assertNext(error -> assertThat(error)
                        .hasFieldOrPropertyWithValue("statusCode", 502)
                        .hasFieldOrPropertyWithValue("responseBody", "x".repeat(DEFAULT_CAP)))
                .verifyComplete();

        assertThat(captured.get().responseBodyTruncated()).isTrue();
        assertThat(captured.get().retainedResponseBodyBytes()).isEqualTo(DEFAULT_CAP);
        assertReleased(buffers);
    }

    @Test
    void releasesConsumedBufferWhenCaptureIsCancelled() {
        PooledDataBuffer consumed = pooledBuffer(2048);
        ClientResponse response = ClientResponse.create(HttpStatus.BAD_GATEWAY)
                .body(Flux.concat(Flux.just((DataBuffer) consumed), Flux.never()))
                .build();

        StepVerifier.create(new DefaultErrorDecoder().decode(response))
                .expectSubscription()
                .thenCancel()
                .verify();

        assertThat(consumed.isAllocated()).isFalse();
    }

    private static DefaultErrorDecoder capturingDecoder(AtomicReference<ErrorResponseContext> captured) {
        ErrorResponseMapper mapper = context -> {
            captured.set(context);
            return Optional.empty();
        };
        return new DefaultErrorDecoder("test-client", List.of(mapper));
    }

    private static ClientResponse response(HttpStatus status, MediaType contentType, String body) {
        return ClientResponse.create(status)
                .header(HttpHeaders.CONTENT_TYPE, contentType.toString())
                .body(body)
                .build();
    }

    private static ClientResponse response(HttpStatus status, MediaType contentType, List<PooledDataBuffer> buffers) {
        return ClientResponse.create(status)
                .header(HttpHeaders.CONTENT_TYPE, contentType.toString())
                .body(Flux.fromIterable(buffers).cast(DataBuffer.class))
                .build();
    }

    private static List<PooledDataBuffer> pooledBuffers(int count, int size) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(ignored -> pooledBuffer(size))
                .toList();
    }

    private static PooledDataBuffer pooledBuffer(int size) {
        return (PooledDataBuffer) BUFFER_FACTORY.wrap("x".repeat(size).getBytes(StandardCharsets.UTF_8));
    }

    private static void assertReleased(List<PooledDataBuffer> buffers) {
        assertThat(buffers).allMatch(buffer -> !buffer.isAllocated());
    }
}
