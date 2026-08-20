package io.github.huynhngochuyhoang.httpstarter.otel;

import io.github.huynhngochuyhoang.httpstarter.annotation.*;
import io.github.huynhngochuyhoang.httpstarter.auth.AuthContext;
import io.github.huynhngochuyhoang.httpstarter.auth.AuthProvider;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import io.github.huynhngochuyhoang.httpstarter.core.Jackson3ReactiveHttpClientJsonCodec;
import io.github.huynhngochuyhoang.httpstarter.core.ReactiveHttpClientCustomizer;
import io.github.huynhngochuyhoang.httpstarter.core.ReactiveHttpClientFactoryBean;
import io.github.huynhngochuyhoang.httpstarter.observability.CompositeHttpClientObserver;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientObserver;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientObserverEvent;
import io.github.huynhngochuyhoang.httpstarter.observability.MicrometerHttpClientObserver;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.core.io.AbstractResource;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class RequestResponseSizeObservabilityContractTest {

    private static final Duration CALL_TIMEOUT = Duration.ofSeconds(5);
    private static final DefaultDataBufferFactory BUFFER_FACTORY = new DefaultDataBufferFactory();

    @Test
    void stringNullAndByteArraySizesMatchWireAndBothBackends() {
        try (SizeServer server = new SizeServer();
             SizeDiagnostics diagnostics = new SizeDiagnostics();
             ClientFixture fixture = ClientFixture.create(server, diagnostics)) {
            String utf8 = "caf\u00e9 \u6771\u4eac";
            byte[] utf8Bytes = utf8.getBytes(StandardCharsets.UTF_8);
            assertThat(fixture.client.utf8("text/plain;charset=UTF-8", utf8).block(CALL_TIMEOUT))
                    .isEqualTo(utf8);
            assertThat(server.onlyRequest("/utf8").body()).containsExactly(utf8Bytes);
            diagnostics.assertSizes("utf8", utf8Bytes.length, utf8Bytes.length);

            String latin1 = "caf\u00e9";
            byte[] latin1Bytes = latin1.getBytes(StandardCharsets.ISO_8859_1);
            assertThat(fixture.client.latin1("text/plain;charset=ISO-8859-1", latin1).block(CALL_TIMEOUT))
                    .isEqualTo(latin1);
            assertThat(server.onlyRequest("/latin1").body()).containsExactly(latin1Bytes);
            diagnostics.assertSizes("latin1", latin1Bytes.length, latin1Bytes.length);

            byte[] binary = new byte[]{0, 1, (byte) 0xff, 2};
            assertThat(fixture.client.bytes("application/octet-stream", binary).block(CALL_TIMEOUT))
                    .containsExactly(binary);
            assertThat(server.onlyRequest("/bytes").body()).containsExactly(binary);
            diagnostics.assertSizes("bytes", binary.length, binary.length);

            fixture.client.nullBody(null).block(CALL_TIMEOUT);
            assertThat(server.onlyRequest("/null").body()).isEmpty();
            diagnostics.assertSizes("nullBody", 0L, 0L);
        }
    }

    @Test
    void stringSizeUsesContentTypeFinalizedByCustomizerAndAuthFilters() {
        String value = "caf\u00e9";
        byte[] expected = value.getBytes(StandardCharsets.ISO_8859_1);
        MediaType latin1 = new MediaType(MediaType.TEXT_PLAIN, StandardCharsets.ISO_8859_1);
        ReactiveHttpClientCustomizer customizer = builder -> builder.filter((request, next) -> {
            ClientRequest finalized = ClientRequest.from(request)
                    .headers(headers -> headers.setContentType(latin1))
                    .build();
            return next.exchange(finalized);
        });

        try (SizeServer server = new SizeServer();
             SizeDiagnostics diagnostics = new SizeDiagnostics();
             ClientFixture fixture = ClientFixture.create(server, diagnostics, customizer, null)) {
            assertThat(fixture.client.utf8("text/plain;charset=UTF-8", value).block(CALL_TIMEOUT))
                    .isEqualTo(value);
            assertThat(server.onlyRequest("/utf8").body()).containsExactly(expected);
            diagnostics.assertSizes("utf8", expected.length, expected.length);
        }

        AuthProvider authProvider = request -> Mono.just(AuthContext.builder()
                .header(HttpHeaders.CONTENT_TYPE, latin1.toString())
                .build());
        try (SizeServer server = new SizeServer();
             SizeDiagnostics diagnostics = new SizeDiagnostics();
             ClientFixture fixture = ClientFixture.create(server, diagnostics, null, authProvider)) {
            assertThat(fixture.client.utf8("text/plain;charset=UTF-8", value).block(CALL_TIMEOUT))
                    .isEqualTo(value);
            assertThat(server.onlyRequest("/utf8").body()).containsExactly(expected);
            diagnostics.assertSizes("utf8", expected.length, expected.length);
        }
    }

    @Test
    void opaqueRequestShapesRemainUnknownWithoutExtraConsumption() {
        try (SizeServer server = new SizeServer();
             SizeDiagnostics diagnostics = new SizeDiagnostics();
             ClientFixture fixture = ClientFixture.create(server, diagnostics)) {
            assertThat(fixture.client.pojo(new Payload("value")).block(CALL_TIMEOUT)).isEqualTo("ok");
            assertThat(server.onlyRequest("/pojo").body()).isNotEmpty();
            diagnostics.assertSizes("pojo", HttpClientObserverEvent.UNKNOWN_SIZE, 2L);

            assertThat(fixture.client.charSequence(new StringBuilder("builder")).block(CALL_TIMEOUT))
                    .isEqualTo("ok");
            assertThat(server.onlyRequest("/char-sequence").body()).isNotEmpty();
            diagnostics.assertSizes("charSequence", HttpClientObserverEvent.UNKNOWN_SIZE, 2L);

            AtomicInteger subscriptions = new AtomicInteger();
            Flux<DataBuffer> publisher = Flux.defer(() -> {
                subscriptions.incrementAndGet();
                return Flux.just(BUFFER_FACTORY.wrap("publisher".getBytes(StandardCharsets.UTF_8)));
            });
            assertThat(fixture.client.publisher(publisher).block(CALL_TIMEOUT)).isEqualTo("ok");
            assertThat(subscriptions).hasValue(1);
            assertThat(server.onlyRequest("/publisher").body())
                    .containsExactly("publisher".getBytes(StandardCharsets.UTF_8));
            diagnostics.assertSizes("publisher", HttpClientObserverEvent.UNKNOWN_SIZE, 2L);

            CountingResource resource = new CountingResource("resource");
            assertThat(fixture.client.resource(resource).block(CALL_TIMEOUT)).isEqualTo("ok");
            assertThat(resource.opens).hasValue(1);
            assertThat(resource.closes).hasValue(1);
            assertThat(server.onlyRequest("/resource").body()).containsExactly(resource.bytes);
            diagnostics.assertSizes("resource", HttpClientObserverEvent.UNKNOWN_SIZE, 2L);

            CountingInputStream inputStream = new CountingInputStream("stream");
            assertThat(fixture.client.inputStream(inputStream).block(CALL_TIMEOUT)).isEqualTo("ok");
            assertThat(inputStream.closes).hasValue(1);
            assertThat(server.onlyRequest("/input-stream").body())
                    .containsExactly("stream".getBytes(StandardCharsets.UTF_8));
            diagnostics.assertSizes("inputStream", HttpClientObserverEvent.UNKNOWN_SIZE, 2L);

            CountingResource multipart = new CountingResource("multipart");
            assertThat(fixture.client.multipart(multipart).block(CALL_TIMEOUT)).isEqualTo("ok");
            assertThat(multipart.opens).hasValue(1);
            assertThat(multipart.closes).hasValue(1);
            assertThat(server.onlyRequest("/multipart").body())
                    .contains("multipart".getBytes(StandardCharsets.UTF_8));
            diagnostics.assertSizes("multipart", HttpClientObserverEvent.UNKNOWN_SIZE, 2L);
        }
    }

    @Test
    void responseSizeUsesOnlySurvivingAdvertisedContentLength() {
        try (SizeServer server = new SizeServer();
             SizeDiagnostics diagnostics = new SizeDiagnostics();
             ClientFixture fixture = ClientFixture.create(server, diagnostics)) {
            assertThat(fixture.client.fixed().block(CALL_TIMEOUT)).isEqualTo("fixed");
            diagnostics.assertSizes("fixed", 0L, 5L);

            assertThat(fixture.client.chunked().block(CALL_TIMEOUT)).isEqualTo("chunked");
            diagnostics.assertSizes("chunked", 0L, HttpClientObserverEvent.UNKNOWN_SIZE);

            assertThat(fixture.client.compressed().block(CALL_TIMEOUT)).isEqualTo("compressed");
            diagnostics.assertSizes("compressed", 0L, HttpClientObserverEvent.UNKNOWN_SIZE);

            ResponseEntity<String> entity = fixture.client.entity().block(CALL_TIMEOUT);
            assertThat(entity).isNotNull();
            assertThat(entity.getBody()).isEqualTo("entity");
            diagnostics.assertSizes("entity", 0L, 6L);

            DataBuffer first = fixture.client.stream().next().block(CALL_TIMEOUT);
            assertThat(first).isNotNull();
            DataBufferUtils.release(first);
            diagnostics.assertSizes("stream", 0L, HttpClientObserverEvent.UNKNOWN_SIZE);
        }
    }

    @ReactiveHttpClient(name = "size-contract")
    interface SizeClient {
        @POST("/utf8")
        Mono<String> utf8(@HeaderParam("Content-Type") String contentType, @Body String body);

        @POST("/latin1")
        Mono<String> latin1(@HeaderParam("Content-Type") String contentType, @Body String body);

        @POST("/bytes")
        Mono<byte[]> bytes(@HeaderParam("Content-Type") String contentType, @Body byte[] body);

        @POST("/null")
        Mono<Void> nullBody(@Body String body);

        @POST("/pojo")
        Mono<String> pojo(@Body Payload body);

        @POST("/char-sequence")
        Mono<String> charSequence(@Body StringBuilder body);

        @POST("/publisher")
        Mono<String> publisher(@Body Flux<DataBuffer> body);

        @POST("/resource")
        Mono<String> resource(@Body CountingResource body);

        @POST("/input-stream")
        Mono<String> inputStream(@Body InputStream body);

        @POST("/multipart")
        @MultipartBody
        Mono<String> multipart(@FormFile("file") CountingResource body);

        @GET("/fixed")
        Mono<String> fixed();

        @GET("/chunked")
        Mono<String> chunked();

        @GET("/compressed")
        Mono<String> compressed();

        @GET("/entity")
        Mono<ResponseEntity<String>> entity();

        @GET("/stream")
        Flux<DataBuffer> stream();
    }

    record Payload(String value) {
    }

    private static final class ClientFixture implements AutoCloseable {
        private final StaticApplicationContext context;
        private final ReactiveHttpClientFactoryBean<SizeClient> factory;
        private final SizeClient client;

        private ClientFixture(StaticApplicationContext context,
                              ReactiveHttpClientFactoryBean<SizeClient> factory,
                              SizeClient client) {
            this.context = context;
            this.factory = factory;
            this.client = client;
        }

        private static ClientFixture create(SizeServer server, SizeDiagnostics diagnostics) {
            return create(server, diagnostics, null, null);
        }

        private static ClientFixture create(SizeServer server,
                                            SizeDiagnostics diagnostics,
                                            ReactiveHttpClientCustomizer customizer,
                                            AuthProvider authProvider) {
            StaticApplicationContext context = new StaticApplicationContext();
            ReactiveHttpClientProperties properties = new ReactiveHttpClientProperties();
            ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();
            config.setBaseUrl("http://127.0.0.1:" + server.port());
            config.setCompressionEnabled(true);
            if (authProvider != null) {
                config.setAuthProvider("sizeAuthProvider");
            }
            properties.getClients().put("size-contract", config);
            context.getBeanFactory().registerSingleton("reactiveHttpClientProperties", properties);
            context.getBeanFactory().registerSingleton("starterWebClientBuilder", WebClient.builder());
            context.getBeanFactory().registerSingleton("reactiveHttpClientJsonCodec",
                    new Jackson3ReactiveHttpClientJsonCodec(JsonMapper.builder().build()));
            context.getBeanFactory().registerSingleton("sizeObserver", diagnostics.observer());
            if (customizer != null) {
                context.getBeanFactory().registerSingleton("sizeCustomizer", customizer);
            }
            if (authProvider != null) {
                context.getBeanFactory().registerSingleton("sizeAuthProvider", authProvider);
            }

            ReactiveHttpClientFactoryBean<SizeClient> factory = new ReactiveHttpClientFactoryBean<>();
            factory.setType(SizeClient.class);
            factory.setApplicationContext(context);
            return new ClientFixture(context, factory, factory.getObject());
        }

        @Override
        public void close() {
            factory.destroy();
            context.close();
        }
    }

    private static final class SizeDiagnostics implements AutoCloseable {
        private static final String METRIC_NAME = "reactive.http.client.requests";
        private final List<HttpClientObserverEvent> events = new CopyOnWriteArrayList<>();
        private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        private final InMemorySpanExporter exporter = InMemorySpanExporter.create();
        private final SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .build();
        private final HttpClientObserver observer;

        private SizeDiagnostics() {
            OpenTelemetry openTelemetry = OpenTelemetrySdk.builder()
                    .setTracerProvider(tracerProvider)
                    .build();
            observer = new CompositeHttpClientObserver(List.of(
                    events::add,
                    new MicrometerHttpClientObserver(
                            meterRegistry, new ReactiveHttpClientProperties.ObservabilityConfig()),
                    new OpenTelemetryHttpClientObserver(openTelemetry)));
        }

        private HttpClientObserver observer() {
            return observer;
        }

        private void assertSizes(String apiName, long requestBytes, long responseBytes) {
            HttpClientObserverEvent event = events.stream()
                    .filter(candidate -> apiName.equals(candidate.getApiName()))
                    .findFirst()
                    .orElseThrow();
            assertThat(event.getRequestBytes()).isEqualTo(requestBytes);
            assertThat(event.getResponseBytes()).isEqualTo(responseBytes);
            assertSummary(METRIC_NAME + ".request.size", apiName, requestBytes);
            assertSummary(METRIC_NAME + ".response.size", apiName, responseBytes);

            SpanData span = exporter.getFinishedSpanItems().stream()
                    .filter(candidate -> apiName.equals(candidate.getAttributes().get(
                            OpenTelemetryHttpClientObserver.ATTR_API_NAME)))
                    .findFirst()
                    .orElseThrow();
            assertThat(span.getAttributes().get(OpenTelemetryHttpClientObserver.ATTR_REQUEST_BYTES))
                    .isEqualTo(requestBytes >= 0 ? requestBytes : null);
            assertThat(span.getAttributes().get(OpenTelemetryHttpClientObserver.ATTR_RESPONSE_BYTES))
                    .isEqualTo(responseBytes >= 0 ? responseBytes : null);
        }

        private void assertSummary(String name, String apiName, long expected) {
            DistributionSummary summary = meterRegistry.find(name)
                    .tag("api.name", apiName)
                    .summary();
            if (expected < 0) {
                assertThat(summary).isNull();
                return;
            }
            assertThat(summary).isNotNull();
            assertThat(summary.count()).isEqualTo(1L);
            assertThat(summary.totalAmount()).isEqualTo(expected);
        }

        @Override
        public void close() {
            tracerProvider.close();
            exporter.close();
            meterRegistry.close();
        }
    }

    private static final class SizeServer implements AutoCloseable {
        private static final byte[] OK = "ok".getBytes(StandardCharsets.UTF_8);
        private final List<WireRequest> requests = new CopyOnWriteArrayList<>();
        private final DisposableServer server;

        private SizeServer() {
            server = HttpServer.create()
                    .port(0)
                    .handle((request, response) -> request.receive().aggregate().asByteArray()
                            .defaultIfEmpty(new byte[0])
                            .flatMap(body -> {
                                String path = request.path().startsWith("/")
                                        ? request.path()
                                        : "/" + request.path();
                                requests.add(new WireRequest(path, Arrays.copyOf(body, body.length)));
                                return switch (path) {
                                    case "/utf8", "/latin1", "/bytes" -> send(
                                            response, body, request.requestHeaders().get(HttpHeaders.CONTENT_TYPE), true);
                                    case "/null" -> response.status(HttpStatus.OK.value())
                                            .header(HttpHeaders.CONTENT_LENGTH, "0")
                                            .send();
                                    case "/fixed" -> send(response,
                                            "fixed".getBytes(StandardCharsets.UTF_8), "text/plain", true);
                                    case "/entity" -> send(response,
                                            "entity".getBytes(StandardCharsets.UTF_8), "text/plain", true);
                                    case "/chunked" -> response.chunkedTransfer(true)
                                            .header(HttpHeaders.CONTENT_TYPE, "text/plain")
                                            .sendByteArray(Flux.just(
                                                    "chunk".getBytes(StandardCharsets.UTF_8),
                                                    "ed".getBytes(StandardCharsets.UTF_8)))
                                            .then();
                                    case "/compressed" -> {
                                        byte[] encoded = gzip("compressed".getBytes(StandardCharsets.UTF_8));
                                        response.header(HttpHeaders.CONTENT_ENCODING, "gzip");
                                        yield send(response, encoded, "text/plain", true);
                                    }
                                    case "/stream" -> response.chunkedTransfer(true)
                                            .header(HttpHeaders.CONTENT_TYPE, "application/octet-stream")
                                            .sendByteArray(Flux.interval(Duration.ofMillis(10))
                                                    .take(20)
                                                    .map(index -> ("chunk-" + index)
                                                            .getBytes(StandardCharsets.UTF_8)))
                                            .then();
                                    default -> send(response, OK, "text/plain", true);
                                };
                            }))
                    .bindNow();
        }

        private int port() {
            return server.port();
        }

        private WireRequest onlyRequest(String path) {
            return requests.stream()
                    .filter(request -> path.equals(request.path()))
                    .findFirst()
                    .orElseThrow();
        }

        @Override
        public void close() {
            server.disposeNow(CALL_TIMEOUT);
        }

        private static Mono<Void> send(reactor.netty.http.server.HttpServerResponse response,
                                       byte[] body,
                                       String contentType,
                                       boolean contentLength) {
            if (contentType != null) {
                response.header(HttpHeaders.CONTENT_TYPE, contentType);
            }
            if (contentLength) {
                response.header(HttpHeaders.CONTENT_LENGTH, Integer.toString(body.length));
            }
            return response.sendByteArray(Mono.just(body)).then();
        }

        private static byte[] gzip(byte[] body) {
            try (java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
                 GZIPOutputStream gzip = new GZIPOutputStream(output)) {
                gzip.write(body);
                gzip.finish();
                return output.toByteArray();
            } catch (IOException error) {
                throw new IllegalStateException(error);
            }
        }
    }

    private static final class CountingResource extends AbstractResource {
        private final byte[] bytes;
        private final AtomicInteger opens = new AtomicInteger();
        private final AtomicInteger closes = new AtomicInteger();

        private CountingResource(String value) {
            bytes = value.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public String getDescription() {
            return "counting-resource";
        }

        @Override
        public long contentLength() {
            return bytes.length;
        }

        @Override
        public InputStream getInputStream() {
            opens.incrementAndGet();
            return new FilterInputStream(new ByteArrayInputStream(bytes)) {
                @Override
                public void close() throws IOException {
                    super.close();
                    closes.incrementAndGet();
                }
            };
        }
    }

    private static final class CountingInputStream extends ByteArrayInputStream {
        private final AtomicInteger closes = new AtomicInteger();

        private CountingInputStream(String value) {
            super(value.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public void close() throws IOException {
            super.close();
            closes.incrementAndGet();
        }
    }

    private record WireRequest(String path, byte[] body) {
    }
}
