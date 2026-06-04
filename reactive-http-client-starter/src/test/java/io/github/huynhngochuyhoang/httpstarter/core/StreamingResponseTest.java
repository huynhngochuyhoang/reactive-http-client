package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.annotation.GET;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientObserver;
import io.netty.buffer.PooledByteBufAllocator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.buffer.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.server.HttpServer;
import reactor.test.StepVerifier;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies the streaming-passthrough patterns from roadmap 1.8: a response larger
 * than the configured {@code codec-max-in-memory-size-mb} is delivered through
 * {@code Flux<DataBuffer>} (and {@code Mono<ResponseEntity<Flux<DataBuffer>>>})
 * without triggering {@code DataBufferLimitException}.
 */
class StreamingResponseTest {

    private static final int CODEC_LIMIT_MB = 1;
    private static final int CHUNK_SIZE = 64 * 1024; // 64 KiB
    private static final int CHUNK_COUNT = 32; // 32 * 64 KiB = 2 MiB → above the 1 MiB codec cap

    @Test
    void fluxOfDataBufferReceivesPayloadLargerThanCodecLimit() {
        WebClient webClient = WebClient.builder()
                .baseUrl("http://stream.test")
                .codecs(c -> c.defaultCodecs().maxInMemorySize(CODEC_LIMIT_MB * 1024 * 1024))
                .exchangeFunction(req -> Mono.just(largeChunkedResponse()))
                .build();

        ReactiveClientInvocationHandler handler = createHandler(webClient);

        Mono<Long> totalBytes = invokeStream(handler)
                .map(StreamingResponseTest::readableBytesAndRelease)
                .reduce(0L, (acc, sz) -> acc + sz);

        StepVerifier.create(totalBytes)
                .expectNext((long) CHUNK_COUNT * CHUNK_SIZE)
                .verifyComplete();
    }

    @Test
    void monoResponseEntityFluxDataBufferStreamsPayloadAndExposesStatusHeaders() {
        WebClient webClient = WebClient.builder()
                .baseUrl("http://stream.test")
                .codecs(c -> c.defaultCodecs().maxInMemorySize(CODEC_LIMIT_MB * 1024 * 1024))
                .exchangeFunction(req -> Mono.just(largeChunkedResponse()))
                .build();

        ReactiveClientInvocationHandler handler = createHandler(webClient);

        Mono<ResponseEntity<Flux<DataBuffer>>> entityMono = invokeStreamEntity(handler);

        StepVerifier.create(entityMono.flatMap(entity -> {
                    assertThat(entity.getStatusCode().value()).isEqualTo(200);
                    assertThat(entity.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE)).isEqualTo("application/octet-stream");
                    return entity.getBody()
                            .map(StreamingResponseTest::readableBytesAndRelease)
                            .reduce(0L, (acc, sz) -> acc + sz);
                }))
                .expectNext((long) CHUNK_COUNT * CHUNK_SIZE)
                .verifyComplete();
    }

    @Test
    void monoResponseEntityFluxDataBufferKeepsRealBodyConsumableAfterOuterMonoCompletes() {
        DisposableServer server = HttpServer.create()
                .port(0)
                .route(routes -> routes.get("/large-file", (request, response) -> response
                        .status(HttpStatus.OK.value())
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE)
                        .sendString(Flux.just("alpha", "beta"))
                        .then()))
                .bindNow();
        try {
            WebClient webClient = WebClient.builder()
                    .baseUrl("http://127.0.0.1:" + server.port())
                    .clientConnector(new ReactorClientHttpConnector(
                            HttpClient.create().responseTimeout(Duration.ofSeconds(5))))
                    .build();
            ReactiveClientInvocationHandler handler = createHandler(webClient);
            AtomicReference<ResponseEntity<Flux<DataBuffer>>> entityRef = new AtomicReference<>();

            StepVerifier.create(invokeStreamEntity(handler).doOnNext(entityRef::set))
                    .assertNext(entity -> {
                        assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.OK);
                        assertThat(entity.getBody()).isNotNull();
                    })
                    .verifyComplete();

            StepVerifier.create(entityRef.get().getBody()
                            .map(StreamingResponseTest::readStringAndRelease)
                            .reduce("", String::concat))
                    .expectNext("alphabeta")
                    .expectComplete()
                    .verify(Duration.ofSeconds(5));
        } finally {
            server.disposeNow();
        }
    }

    @Test
    void monoResponseEntityFluxDataBufferDoesNotSubscribeToBodyBeforeCallerConsumesIt() {
        AtomicInteger bodySubscriptions = new AtomicInteger();
        DefaultDataBufferFactory bufferFactory = new DefaultDataBufferFactory();
        Flux<DataBuffer> body = Flux.defer(() -> {
            bodySubscriptions.incrementAndGet();
            return Flux.just(bufferFactory.wrap("chunk".getBytes(StandardCharsets.UTF_8)));
        });
        ClientResponse response = ClientResponse.create(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_TYPE, "application/octet-stream")
                .body(body)
                .build();
        WebClient webClient = WebClient.builder()
                .baseUrl("http://stream.test")
                .exchangeFunction(req -> Mono.just(response))
                .build();
        ReactiveClientInvocationHandler handler = createHandler(webClient);
        AtomicReference<ResponseEntity<Flux<DataBuffer>>> entityRef = new AtomicReference<>();

        StepVerifier.create(invokeStreamEntity(handler).doOnNext(entityRef::set))
                .assertNext(entity -> {
                    assertThat(entity.getStatusCode().value()).isEqualTo(200);
                    assertThat(entity.getBody()).isNotNull();
                })
                .verifyComplete();

        assertThat(bodySubscriptions.get()).isZero();
        StepVerifier.create(entityRef.get().getBody().map(StreamingResponseTest::readableBytesAndRelease))
                .expectNext(5)
                .verifyComplete();
        assertThat(bodySubscriptions.get()).isEqualTo(1);
    }

    @Test
    void fluxOfDataBufferLeavesEmittedBuffersForCallerToRelease() {
        List<PooledDataBuffer> buffers = pooledBuffers("one", "two", "three");
        WebClient webClient = WebClient.builder()
                .baseUrl("http://stream.test")
                .exchangeFunction(req -> Mono.just(streamingResponse(buffers)))
                .build();
        ReactiveClientInvocationHandler handler = createHandler(webClient);

        StepVerifier.create(invokeStream(handler).map(StreamingResponseTest::readableBytesAndRelease))
                .expectNext(3, 3, 5)
                .verifyComplete();

        assertReleased(buffers);
    }

    @Test
    void fluxOfDataBufferReleasesDiscardedBuffersOnCancellation() {
        List<PooledDataBuffer> buffers = pooledBuffers("one", "two", "three");
        WebClient webClient = WebClient.builder()
                .baseUrl("http://stream.test")
                .exchangeFunction(req -> Mono.just(streamingResponse(buffers)))
                .build();
        ReactiveClientInvocationHandler handler = createHandler(webClient);

        StepVerifier.create(invokeStream(handler).take(1).map(StreamingResponseTest::readableBytesAndRelease))
                .expectNext(3)
                .verifyComplete();

        assertReleased(buffers);
    }

    @Test
    void responseEntityFluxDataBufferLeavesEmittedBuffersForCallerToRelease() {
        List<PooledDataBuffer> buffers = pooledBuffers("one", "two", "three");
        WebClient webClient = WebClient.builder()
                .baseUrl("http://stream.test")
                .exchangeFunction(req -> Mono.just(streamingResponse(buffers)))
                .build();
        ReactiveClientInvocationHandler handler = createHandler(webClient);

        StepVerifier.create(invokeStreamEntity(handler)
                        .flatMapMany(entity -> entity.getBody().map(StreamingResponseTest::readableBytesAndRelease)))
                .expectNext(3, 3, 5)
                .verifyComplete();

        assertReleased(buffers);
    }

    @Test
    void responseEntityFluxDataBufferReleasesDiscardedBuffersWhenInnerStreamIsCancelled() {
        List<PooledDataBuffer> buffers = pooledBuffers("one", "two", "three");
        WebClient webClient = WebClient.builder()
                .baseUrl("http://stream.test")
                .exchangeFunction(req -> Mono.just(streamingResponse(buffers)))
                .build();
        ReactiveClientInvocationHandler handler = createHandler(webClient);

        StepVerifier.create(invokeStreamEntity(handler)
                        .flatMapMany(entity -> entity.getBody().take(1).map(StreamingResponseTest::readableBytesAndRelease)))
                .expectNext(3)
                .verifyComplete();

        assertReleased(buffers);
    }


    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static ClientResponse streamingResponse(List<PooledDataBuffer> buffers) {
        return ClientResponse.create(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_TYPE, "application/octet-stream")
                .body(Flux.fromIterable(buffers).cast(DataBuffer.class))
                .build();
    }

    private static List<PooledDataBuffer> pooledBuffers(String... values) {
        NettyDataBufferFactory bufferFactory = new NettyDataBufferFactory(PooledByteBufAllocator.DEFAULT);
        return java.util.Arrays.stream(values)
                .map(value -> (PooledDataBuffer) bufferFactory.wrap(value.getBytes(StandardCharsets.UTF_8)))
                .toList();
    }

    private static String readStringAndRelease(DataBuffer buffer) {
        try {
            byte[] bytes = new byte[buffer.readableByteCount()];
            buffer.read(bytes);
            return new String(bytes, StandardCharsets.UTF_8);
        } finally {
            DataBufferUtils.release(buffer);
        }
    }

    private static int readableBytesAndRelease(DataBuffer buffer) {
        try {
            return buffer.readableByteCount();
        } finally {
            DataBufferUtils.release(buffer);
        }
    }

    private static void assertReleased(List<PooledDataBuffer> buffers) {
        assertThat(buffers).allMatch(buffer -> !buffer.isAllocated());
    }

    private static ClientResponse largeChunkedResponse() {
        NettyDataBufferFactory bufferFactory = new NettyDataBufferFactory(PooledByteBufAllocator.DEFAULT);
        Flux<DataBuffer> chunks = Flux.range(0, CHUNK_COUNT)
                .map(i -> bufferFactory.wrap(new byte[CHUNK_SIZE]));
        return ClientResponse.create(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_TYPE, "application/octet-stream")
                .body(chunks)
                .build();
    }

    @SuppressWarnings("unchecked")
    private static ReactiveClientInvocationHandler createHandler(WebClient webClient) {
        ApplicationContext appCtx = mock(ApplicationContext.class);
        ObjectProvider<HttpClientObserver> observerProvider = mock(ObjectProvider.class);
        when(appCtx.getBeanProvider(HttpClientObserver.class)).thenReturn(observerProvider);
        when(observerProvider.getIfAvailable()).thenReturn(null);

        ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();
        config.setCodecMaxInMemorySizeMb(CODEC_LIMIT_MB);

        return new ReactiveClientInvocationHandler(
                webClient,
                new MethodMetadataCache(),
                new RequestArgumentResolver(),
                new DefaultErrorDecoder(),
                config,
                "stream-client",
                appCtx,
                new NoopResilienceOperatorApplier(),
                null,
                new ReactiveHttpClientProperties.ObservabilityConfig()
        );
    }

    @SuppressWarnings("unchecked")
    private static Flux<DataBuffer> invokeStream(ReactiveClientInvocationHandler handler) {
        try {
            Method m = StreamingClient.class.getMethod("download");
            return (Flux<DataBuffer>) handler.invoke(null, m, new Object[0]);
        } catch (Throwable t) {
            return Flux.error(t);
        }
    }

    @SuppressWarnings("unchecked")
    private static Mono<ResponseEntity<Flux<DataBuffer>>> invokeStreamEntity(ReactiveClientInvocationHandler handler) {
        try {
            Method m = StreamingClient.class.getMethod("downloadEntity");
            return (Mono<ResponseEntity<Flux<DataBuffer>>>) handler.invoke(null, m, new Object[0]);
        } catch (Throwable t) {
            return Mono.error(t);
        }
    }

    interface StreamingClient {
        @GET("/large-file")
        Flux<DataBuffer> download();

        @GET("/large-file")
        Mono<ResponseEntity<Flux<DataBuffer>>> downloadEntity();
    }
}
