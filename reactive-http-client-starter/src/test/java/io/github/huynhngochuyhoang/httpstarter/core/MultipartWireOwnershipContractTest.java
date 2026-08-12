package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.annotation.*;
import io.github.huynhngochuyhoang.httpstarter.auth.AuthContext;
import io.github.huynhngochuyhoang.httpstarter.auth.AuthRequest;
import io.github.huynhngochuyhoang.httpstarter.auth.InvalidatableAuthProvider;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.netty.handler.timeout.WriteTimeoutException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.core.io.AbstractResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.HttpProtocol;
import reactor.netty.http.server.HttpServer;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class MultipartWireOwnershipContractTest {

    private static final Duration CALL_TIMEOUT = Duration.ofSeconds(8);

    @ParameterizedTest
    @EnumSource(WireProtocol.class)
    void realPeerParsesOrderedMixedPartsAndProtocolFraming(WireProtocol protocol) {
        try (MultipartServer server = new MultipartServer(protocol);
             ClientFixture fixture = ClientFixture.create(server)) {
            byte[] byteFile = new byte[]{0, 1, '\r', '\n', (byte) 0xff};
            FileAttachment attachment = FileAttachment.of(
                    "attachment-body".getBytes(StandardCharsets.UTF_8),
                    "dynamic.json",
                    "application/json");
            TrackingResource resource = new TrackingResource(
                    "resource-body".getBytes(StandardCharsets.UTF_8), "source.txt");
            TrackingResource unicode = new TrackingResource(
                    "unicode-body".getBytes(StandardCharsets.UTF_8), "résumé 2026.txt");

            assertThat(fixture.client().mixed(
                    "alpha",
                    byteFile,
                    Arrays.asList("red", null, "blue"),
                    attachment,
                    "green",
                    new String[]{"one", null, "two"},
                    resource,
                    null,
                    null,
                    unicode).block(CALL_TIMEOUT)).isEqualTo("ok");

            WireRequest request = server.onlyRequest("/wire");
            List<Part> parts = parseMultipart(request);
            assertThat(parts).extracting(Part::name)
                    .containsExactly("description", "bytes", "tag", "tag", "attachment",
                            "tag", "code", "code", "resource", "unicode");
            assertTextPart(parts.get(0), "alpha");
            assertFilePart(parts.get(1), "bytes.bin", "application/octet-stream", byteFile);
            assertTextPart(parts.get(2), "red");
            assertTextPart(parts.get(3), "blue");
            assertFilePart(parts.get(4), "dynamic.json", "application/json",
                    "attachment-body".getBytes(StandardCharsets.UTF_8));
            assertTextPart(parts.get(5), "green");
            assertTextPart(parts.get(6), "one");
            assertTextPart(parts.get(7), "two");
            assertFilePart(parts.get(8), "source.txt", "text/plain",
                    "resource-body".getBytes(StandardCharsets.UTF_8));
            assertThat(parts.get(9).contentDisposition())
                    .isEqualTo("form-data; name=\"unicode\"; filename=\"résumé 2026.txt\"");
            assertThat(parts.get(9).body()).containsExactly("unicode-body".getBytes(StandardCharsets.UTF_8));
            assertThat(resource.opens).hasValue(1);
            assertThat(resource.closes).hasValue(1);
            assertThat(unicode.opens).hasValue(1);
            assertThat(unicode.closes).hasValue(1);

            assertThat(request.protocol()).isEqualTo(protocol.expectedProtocol);
            assertThat(request.contentType()).startsWith("multipart/form-data;boundary=");
            assertThat(request.chunkCount()).isPositive();
            if (protocol == WireProtocol.HTTP11) {
                assertThat(request.contentLength()).isNull();
                assertThat(request.transferEncoding()).isEqualTo("chunked");
            }
        }
    }

    @Test
    void resourceStreamsBeforeCompletionAndClosesOnce() {
        try (MultipartServer server = new MultipartServer(WireProtocol.HTTP11);
             ClientFixture fixture = ClientFixture.create(server)) {
            GatedResource resource = new GatedResource(server.firstPayloadObserved);

            assertThat(fixture.client().unbuffered(resource).block(CALL_TIMEOUT)).isEqualTo("ok");

            assertThat(server.firstPayloadObserved.getCount()).isZero();
            assertThat(resource.gatedReadStarted).isTrue();
            assertThat(resource.opens).hasValue(1);
            assertThat(resource.closes).hasValue(1);
            assertThat(parseMultipart(server.onlyRequest("/unbuffered")).get(0).body())
                    .containsExactly(resource.bytes);
        }
    }

    @Test
    void cancellationBeforeWriteDoesNotOpenResourceAndCancellationDuringWriteClosesIt() {
        try (MultipartServer server = new MultipartServer(WireProtocol.HTTP11);
             ClientFixture fixture = ClientFixture.create(server)) {
            Disposable held = fixture.client().hold().subscribe();
            await(server.holdStarted::get, "pool-holding request did not start");

            SlowResource queuedResource = new SlowResource(64 * 1024, 0);
            Disposable queued = fixture.client().cancelDuring(queuedResource).subscribe();
            sleep(Duration.ofMillis(100));
            assertThat(queuedResource.opens).hasValue(0);
            queued.dispose();
            held.dispose();
            assertThat(queuedResource.opens).hasValue(0);
            assertThat(queuedResource.closes).hasValue(0);

            SlowResource activeResource = new SlowResource(4 * 1024 * 1024, 2);
            Disposable active = fixture.client().cancelDuring(activeResource).subscribe();
            await(() -> activeResource.reads.get() >= 4, "multipart resource did not start streaming");
            active.dispose();
            await(() -> activeResource.closes.get() == 1, "cancelled multipart resource was not closed");
            int readsAfterCancel = activeResource.reads.get();
            sleep(Duration.ofMillis(100));
            assertThat(activeResource.reads).hasValue(readsAfterCancel);
            assertThat(activeResource.opens).hasValue(1);
            assertThat(server.startedPaths.stream().filter("/cancel-during"::equals)).hasSize(1);
        }
    }

    @Test
    void peerResetAndTimeoutsCloseOnlyTheOpenedResource() {
        try (MultipartServer server = new MultipartServer(WireProtocol.HTTP11);
             ClientFixture fixture = ClientFixture.create(server)) {
            SlowResource resetResource = new SlowResource(4 * 1024 * 1024, 2);
            Throwable reset = catchThrowable(() ->
                    fixture.client().peerReset(resetResource).block(CALL_TIMEOUT));
            assertThat(reset).isNotNull();
            await(() -> resetResource.closes.get() == 1, "reset multipart resource was not closed");
            assertThat(resetResource.opens).hasValue(1);
            int readsAfterReset = resetResource.reads.get();
            sleep(Duration.ofMillis(100));
            assertThat(resetResource.reads).hasValue(readsAfterReset);
        }

        try (MultipartServer server = new MultipartServer(WireProtocol.HTTP11);
             ClientFixture fixture = ClientFixture.create(server, false, false, null, 120, 60_000)) {
            TrackingResource responseTimeoutResource = new TrackingResource(
                    "response-timeout".getBytes(StandardCharsets.UTF_8), "timeout.txt");
            Throwable responseTimeout = catchThrowable(() ->
                    fixture.client().responseTimeout(responseTimeoutResource).block(CALL_TIMEOUT));
            assertThat(responseTimeout).isNotNull();
            assertThat(responseTimeoutResource.opens).hasValue(1);
            assertThat(responseTimeoutResource.closes).hasValue(1);
        }

        try (MultipartServer server = new MultipartServer(WireProtocol.HTTP11);
             ClientFixture fixture = ClientFixture.create(server, false, false, null, 0, 100)) {
            GeneratedResource resource = new GeneratedResource(256L * 1024 * 1024);
            Throwable timeout = catchThrowable(() ->
                    fixture.client().writeTimeout(resource).block(CALL_TIMEOUT));

            assertThat(timeout).isNotNull().hasRootCauseInstanceOf(WriteTimeoutException.class);
            await(() -> resource.closes.get() == 1, "write-timeout multipart resource was not closed");
            assertThat(resource.opens).hasValue(1);
            assertThat(resource.bytesRead.get()).isPositive().isLessThan(resource.length);
            long bytesAfterTimeout = resource.bytesRead.get();
            sleep(Duration.ofMillis(100));
            assertThat(resource.bytesRead).hasValue(bytesAfterTimeout);
        }
    }

    @Test
    void retryRedirectAndAuthReplayReopenResourceOncePerDispatch() {
        try (MultipartServer server = new MultipartServer(WireProtocol.HTTP11);
             ClientFixture fixture = ClientFixture.create(server, true, false, null, 0, 60_000)) {
            TrackingResource resource = new TrackingResource("retry-body".getBytes(StandardCharsets.UTF_8), "retry.txt");

            assertThat(fixture.client().retry(resource).block(CALL_TIMEOUT)).isEqualTo("ok");
            assertReplay(server.requestsFor("/retry"), resource, "retry-body");
        }

        try (MultipartServer server = new MultipartServer(WireProtocol.HTTP11);
             ClientFixture fixture = ClientFixture.create(server, false, true, null, 0, 60_000)) {
            TrackingResource resource = new TrackingResource(
                    "redirect-body".getBytes(StandardCharsets.UTF_8), "redirect.txt");

            assertThat(fixture.client().redirect(resource).block(CALL_TIMEOUT)).isEqualTo("ok");
            assertThat(server.paths()).containsExactly("/redirect", "/redirect-target");
            assertReplay(server.requests(), resource, "redirect-body");
        }

        RefreshOnceAuthProvider authProvider = new RefreshOnceAuthProvider();
        try (MultipartServer server = new MultipartServer(WireProtocol.HTTP11);
             ClientFixture fixture = ClientFixture.create(server, false, false, authProvider, 0, 60_000)) {
            TrackingResource resource = new TrackingResource("auth-body".getBytes(StandardCharsets.UTF_8), "auth.txt");

            assertThat(fixture.client().auth(resource).block(CALL_TIMEOUT)).isEqualTo("ok");
            assertReplay(server.requestsFor("/auth"), resource, "auth-body");
            assertThat(authProvider.authCalls).hasValue(2);
            assertThat(authProvider.invalidations).hasValue(1);
        }
    }

    @Test
    void authProviderSeesDeclaredMultipartNamesInGlobalWireOrder() {
        MultipartBodyCapturingAuthProvider authProvider = new MultipartBodyCapturingAuthProvider();
        try (MultipartServer server = new MultipartServer(WireProtocol.HTTP11);
             ClientFixture fixture = ClientFixture.create(server, false, false, authProvider, 0, 60_000)) {
            TrackingResource resource = new TrackingResource(
                    "resource-body".getBytes(StandardCharsets.UTF_8), "source.txt");

            assertThat(fixture.client().mixed(
                    "alpha",
                    new byte[]{1},
                    List.of("red", "blue"),
                    FileAttachment.of("attachment".getBytes(StandardCharsets.UTF_8), "attachment.txt"),
                    "green",
                    new String[]{"one", "two"},
                    resource,
                    null,
                    null,
                    null).block(CALL_TIMEOUT)).isEqualTo("ok");

            assertThat(authProvider.partNames()).containsExactly(
                    "description", "bytes", "tag", "tag", "attachment", "tag",
                    "code", "code", "resource");
            assertThat(authProvider.partNames()).noneMatch(name -> name.startsWith("part-"));
        }
    }

    private static void assertReplay(List<WireRequest> requests, TrackingResource resource, String expectedBody) {
        assertThat(requests).hasSize(2);
        assertThat(requests)
                .allSatisfy(request -> assertThat(parseMultipart(request).get(0).body())
                        .containsExactly(expectedBody.getBytes(StandardCharsets.UTF_8)));
        assertThat(resource.opens).hasValue(2);
        assertThat(resource.closes).hasValue(2);
    }

    private static void assertTextPart(Part part, String value) {
        assertThat(part.contentDisposition()).isEqualTo("form-data; name=\"" + part.name() + "\"");
        assertThat(part.contentType()).isEqualTo("text/plain;charset=UTF-8");
        assertThat(part.body()).containsExactly(value.getBytes(StandardCharsets.UTF_8));
    }

    private static void assertFilePart(Part part, String filename, String contentType, byte[] body) {
        assertThat(part.contentDisposition())
                .isEqualTo("form-data; name=\"" + part.name() + "\"; filename=\"" + filename + "\"");
        assertThat(part.contentType()).isEqualTo(contentType);
        assertThat(part.body()).containsExactly(body);
    }

    private static List<Part> parseMultipart(WireRequest request) {
        String boundary = request.contentType().substring(request.contentType().indexOf("boundary=") + 9);
        byte[] delimiter = ("--" + boundary).getBytes(StandardCharsets.US_ASCII);
        byte[] separator = "\r\n\r\n".getBytes(StandardCharsets.US_ASCII);
        byte[] nextDelimiter = ("\r\n--" + boundary).getBytes(StandardCharsets.US_ASCII);
        List<Part> parts = new ArrayList<>();
        int position = 0;
        while (position < request.body().length) {
            int boundaryStart = indexOf(request.body(), delimiter, position);
            if (boundaryStart < 0) {
                break;
            }
            int cursor = boundaryStart + delimiter.length;
            if (cursor + 1 < request.body().length
                    && request.body()[cursor] == '-' && request.body()[cursor + 1] == '-') {
                break;
            }
            cursor += 2;
            int headersEnd = indexOf(request.body(), separator, cursor);
            assertThat(headersEnd).as("multipart part headers").isGreaterThanOrEqualTo(cursor);
            String headers = new String(request.body(), cursor, headersEnd - cursor, StandardCharsets.UTF_8);
            int bodyStart = headersEnd + separator.length;
            int bodyEnd = indexOf(request.body(), nextDelimiter, bodyStart);
            assertThat(bodyEnd).as("multipart part closing boundary").isGreaterThanOrEqualTo(bodyStart);
            parts.add(new Part(
                    dispositionParameter(header(headers, HttpHeaders.CONTENT_DISPOSITION), "name"),
                    header(headers, HttpHeaders.CONTENT_DISPOSITION),
                    header(headers, HttpHeaders.CONTENT_TYPE),
                    Arrays.copyOfRange(request.body(), bodyStart, bodyEnd)));
            position = bodyEnd + 2;
        }
        return parts;
    }

    private static String header(String headers, String name) {
        return Arrays.stream(headers.split("\r\n"))
                .filter(line -> line.regionMatches(true, 0, name + ":", 0, name.length() + 1))
                .map(line -> line.substring(line.indexOf(':') + 1).trim())
                .findFirst()
                .orElse(null);
    }

    private static String dispositionParameter(String disposition, String name) {
        String marker = name + "=\"";
        int start = disposition.indexOf(marker);
        int end = disposition.indexOf('"', start + marker.length());
        return disposition.substring(start + marker.length(), end);
    }

    private static int indexOf(byte[] source, byte[] target, int fromIndex) {
        outer:
        for (int index = Math.max(0, fromIndex); index <= source.length - target.length; index++) {
            for (int offset = 0; offset < target.length; offset++) {
                if (source[index + offset] != target[offset]) {
                    continue outer;
                }
            }
            return index;
        }
        return -1;
    }

    private static void await(BooleanSupplier condition, String message) {
        long deadline = System.nanoTime() + CALL_TIMEOUT.toNanos();
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            sleep(Duration.ofMillis(10));
        }
        assertThat(condition.getAsBoolean()).as(message).isTrue();
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(error);
        }
    }

    private enum WireProtocol {
        HTTP11(HttpProtocol.HTTP11, "HTTP/1.1"),
        H2C(HttpProtocol.H2C, "HTTP/2.0");

        private final HttpProtocol nettyProtocol;
        private final String expectedProtocol;

        WireProtocol(HttpProtocol nettyProtocol, String expectedProtocol) {
            this.nettyProtocol = nettyProtocol;
            this.expectedProtocol = expectedProtocol;
        }
    }

    @ReactiveHttpClient(name = "multipart-wire")
    interface MultipartClient {
        @POST("/wire")
        @MultipartBody
        Mono<String> mixed(
                @FormField("description") String description,
                @FormFile(value = "bytes", filename = "bytes.bin") byte[] bytes,
                @FormField("tag") List<String> tags,
                @FormFile("attachment") FileAttachment attachment,
                @FormField("tag") String trailingTag,
                @FormField("code") String[] codes,
                @FormFile(value = "resource", contentType = "text/plain") Resource resource,
                @FormField("omitted") String omitted,
                @FormFile("omitted-file") Resource omittedFile,
                @FormFile("unicode") Resource unicode);

        @POST("/unbuffered")
        @MultipartBody
        Mono<String> unbuffered(@FormFile("file") Resource resource);

        @POST("/cancel-during")
        @MultipartBody
        Mono<String> cancelDuring(@FormFile("file") Resource resource);

        @POST("/peer-reset")
        @MultipartBody
        Mono<String> peerReset(@FormFile("file") Resource resource);

        @POST("/write-timeout")
        @MultipartBody
        Mono<String> writeTimeout(@FormFile("file") Resource resource);

        @POST("/response-timeout")
        @MultipartBody
        Mono<String> responseTimeout(@FormFile("file") Resource resource);

        @POST("/retry")
        @MultipartBody
        Mono<String> retry(@FormFile("file") Resource resource);

        @POST("/redirect")
        @MultipartBody
        Mono<String> redirect(@FormFile("file") Resource resource);

        @POST("/auth")
        @MultipartBody
        Mono<String> auth(@FormFile("file") Resource resource);

        @POST("/hold")
        Mono<String> hold();
    }

    private static final class ClientFixture implements AutoCloseable {
        private final StaticApplicationContext context;
        private final ReactiveHttpClientFactoryBean<MultipartClient> factory;
        private final MultipartClient client;

        private ClientFixture(StaticApplicationContext context,
                              ReactiveHttpClientFactoryBean<MultipartClient> factory,
                              MultipartClient client) {
            this.context = context;
            this.factory = factory;
            this.client = client;
        }

        static ClientFixture create(MultipartServer server) {
            return create(server, false, false, null, 0, 60_000);
        }

        static ClientFixture create(MultipartServer server,
                                    boolean retry,
                                    boolean followRedirects,
                                    InvalidatableAuthProvider authProvider,
                                    long responseTimeoutMs,
                                    int writeTimeoutMs) {
            StaticApplicationContext context = new StaticApplicationContext();
            ReactiveHttpClientProperties properties = new ReactiveHttpClientProperties();
            properties.getNetwork().setNetworkWriteTimeoutMs(writeTimeoutMs);
            ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();
            config.setBaseUrl("http://127.0.0.1:" + server.port());
            config.setHttp2Enabled(server.protocol == WireProtocol.H2C);
            config.setFollowRedirects(followRedirects);
            config.setRequestTimeoutMs(responseTimeoutMs);
            ReactiveHttpClientProperties.ConnectionPoolConfig pool =
                    new ReactiveHttpClientProperties.ConnectionPoolConfig();
            pool.setMaxConnections(1);
            pool.setPendingAcquireTimeoutMs(2_000);
            config.setPool(pool);
            if (retry) {
                ReactiveHttpClientProperties.ResilienceConfig resilience =
                        new ReactiveHttpClientProperties.ResilienceConfig();
                resilience.setEnabled(true);
                resilience.setRetry("multipart-retry");
                resilience.setRetryMethods(Set.of("POST"));
                config.setResilience(resilience);
                context.getBeanFactory().registerSingleton("retryRegistry", RetryRegistry.of(
                        RetryConfig.custom().maxAttempts(2).waitDuration(Duration.ZERO).build()));
            }
            if (authProvider != null) {
                config.setAuthProvider("multipartAuth");
                context.getBeanFactory().registerSingleton("multipartAuth", authProvider);
            }
            properties.getClients().put("multipart-wire", config);
            context.getBeanFactory().registerSingleton("reactiveHttpClientProperties", properties);
            context.getBeanFactory().registerSingleton("starterWebClientBuilder", WebClient.builder());
            context.getBeanFactory().registerSingleton("reactiveHttpClientJsonCodec", TestJsonCodecs.jsonCodec());

            ReactiveHttpClientFactoryBean<MultipartClient> factory = new ReactiveHttpClientFactoryBean<>();
            factory.setType(MultipartClient.class);
            factory.setApplicationContext(context);
            return new ClientFixture(context, factory, factory.getObject());
        }

        MultipartClient client() {
            return client;
        }

        @Override
        public void close() {
            factory.destroy();
            context.close();
        }
    }

    private static class TrackingResource extends AbstractResource {
        protected final byte[] bytes;
        protected final String filename;
        protected final AtomicInteger opens = new AtomicInteger();
        protected final AtomicInteger closes = new AtomicInteger();
        protected final AtomicInteger reads = new AtomicInteger();

        private TrackingResource(byte[] bytes, String filename) {
            this.bytes = bytes;
            this.filename = filename;
        }

        @Override
        public String getDescription() {
            return "tracked multipart resource";
        }

        @Override
        public String getFilename() {
            return filename;
        }

        @Override
        public long contentLength() {
            return bytes.length;
        }

        @Override
        public InputStream getInputStream() {
            opens.incrementAndGet();
            return tracked(new ByteArrayInputStream(bytes));
        }

        protected InputStream tracked(InputStream delegate) {
            return new FilterInputStream(delegate) {
                private boolean closed;

                @Override
                public int read(byte[] destination, int offset, int length) throws IOException {
                    reads.incrementAndGet();
                    return super.read(destination, offset, length);
                }

                @Override
                public void close() throws IOException {
                    if (!closed) {
                        closed = true;
                        closes.incrementAndGet();
                    }
                    super.close();
                }
            };
        }
    }

    private static final class GatedResource extends TrackingResource {
        private static final byte[] PREFIX = "stream-before-complete".getBytes(StandardCharsets.UTF_8);
        private static final int GATE_AFTER_BYTES = 1024 * 1024;
        private final CountDownLatch firstPayloadObserved;
        private final AtomicBoolean gatedReadStarted = new AtomicBoolean();

        private GatedResource(CountDownLatch firstPayloadObserved) {
            super(gatedBytes(), "gated.bin");
            this.firstPayloadObserved = firstPayloadObserved;
        }

        @Override
        public InputStream getInputStream() {
            opens.incrementAndGet();
            return tracked(new ByteArrayInputStream(bytes) {
                private int delivered;
                private boolean gatePassed;

                @Override
                public synchronized int read(byte[] destination, int offset, int length) {
                    if (length == 0) {
                        return super.read(destination, offset, length);
                    }
                    awaitGateIfNecessary();
                    int allowed = gatePassed ? length : Math.min(length, GATE_AFTER_BYTES - delivered);
                    int read = super.read(destination, offset, allowed);
                    if (read > 0) {
                        delivered += read;
                    }
                    return read;
                }

                @Override
                public synchronized int read() {
                    awaitGateIfNecessary();
                    int read = super.read();
                    if (read >= 0) {
                        delivered++;
                    }
                    return read;
                }

                private void awaitGateIfNecessary() {
                    if (gatePassed || delivered < GATE_AFTER_BYTES) {
                        return;
                    }
                    gatedReadStarted.set(true);
                    try {
                        if (!firstPayloadObserved.await(CALL_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                            throw new IllegalStateException("server did not observe the first resource payload");
                        }
                    } catch (InterruptedException error) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(error);
                    }
                    gatePassed = true;
                }
            });
        }

        private static byte[] gatedBytes() {
            byte[] bytes = new byte[4 * 1024 * 1024];
            Arrays.fill(bytes, (byte) 'x');
            System.arraycopy(PREFIX, 0, bytes, 0, PREFIX.length);
            return bytes;
        }
    }

    private static final class SlowResource extends AbstractResource {
        private final int length;
        private final long delayMs;
        private final AtomicInteger opens = new AtomicInteger();
        private final AtomicInteger closes = new AtomicInteger();
        private final AtomicInteger reads = new AtomicInteger();

        private SlowResource(int length, long delayMs) {
            this.length = length;
            this.delayMs = delayMs;
        }

        @Override
        public String getDescription() {
            return "slow multipart resource";
        }

        @Override
        public String getFilename() {
            return "slow.bin";
        }

        @Override
        public long contentLength() {
            return length;
        }

        @Override
        public InputStream getInputStream() {
            opens.incrementAndGet();
            return new InputStream() {
                private int remaining = length;
                private boolean closed;

                @Override
                public int read(byte[] destination, int offset, int requested) {
                    if (remaining == 0) {
                        return -1;
                    }
                    if (delayMs > 0) {
                        sleep(Duration.ofMillis(delayMs));
                    }
                    int count = Math.min(Math.min(requested, 1024), remaining);
                    Arrays.fill(destination, offset, offset + count, (byte) 's');
                    remaining -= count;
                    reads.incrementAndGet();
                    return count;
                }

                @Override
                public int read() {
                    byte[] single = new byte[1];
                    return read(single, 0, 1) < 0 ? -1 : single[0];
                }

                @Override
                public void close() {
                    if (!closed) {
                        closed = true;
                        closes.incrementAndGet();
                    }
                }
            };
        }
    }

    private static final class GeneratedResource extends AbstractResource {
        private final long length;
        private final AtomicInteger opens = new AtomicInteger();
        private final AtomicInteger closes = new AtomicInteger();
        private final AtomicLong bytesRead = new AtomicLong();

        private GeneratedResource(long length) {
            this.length = length;
        }

        @Override
        public String getDescription() {
            return "generated multipart resource";
        }

        @Override
        public String getFilename() {
            return "generated.bin";
        }

        @Override
        public long contentLength() {
            return length;
        }

        @Override
        public InputStream getInputStream() {
            opens.incrementAndGet();
            return new InputStream() {
                private long remaining = length;
                private boolean closed;

                @Override
                public int read(byte[] destination, int offset, int requested) {
                    if (remaining == 0) {
                        return -1;
                    }
                    int count = (int) Math.min(requested, remaining);
                    Arrays.fill(destination, offset, offset + count, (byte) 'w');
                    remaining -= count;
                    bytesRead.addAndGet(count);
                    return count;
                }

                @Override
                public int read() {
                    if (remaining == 0) {
                        return -1;
                    }
                    remaining--;
                    bytesRead.incrementAndGet();
                    return 'w';
                }

                @Override
                public void close() {
                    if (!closed) {
                        closed = true;
                        closes.incrementAndGet();
                    }
                }
            };
        }
    }

    private static final class RefreshOnceAuthProvider implements InvalidatableAuthProvider {
        private final AtomicInteger authCalls = new AtomicInteger();
        private final AtomicInteger invalidations = new AtomicInteger();

        @Override
        public Mono<AuthContext> getAuth(AuthRequest request) {
            int call = authCalls.incrementAndGet();
            return Mono.just(AuthContext.builder()
                    .header(HttpHeaders.AUTHORIZATION, "Bearer token-" + call)
                    .build());
        }

        @Override
        public Mono<Void> invalidate() {
            invalidations.incrementAndGet();
            return Mono.empty();
        }
    }

    private static final class MultipartBodyCapturingAuthProvider implements InvalidatableAuthProvider {
        private List<HttpEntity<?>> parts;

        @Override
        @SuppressWarnings("unchecked")
        public Mono<AuthContext> getAuth(AuthRequest request) {
            parts = (List<HttpEntity<?>>) request.requestBody();
            return Mono.just(AuthContext.empty());
        }

        @Override
        public Mono<Void> invalidate() {
            return Mono.empty();
        }

        private List<String> partNames() {
            return parts.stream()
                    .map(part -> part.getHeaders().getContentDisposition().getName())
                    .toList();
        }
    }

    private static final class MultipartServer implements AutoCloseable {
        private final WireProtocol protocol;
        private final List<WireRequest> requests = new CopyOnWriteArrayList<>();
        private final List<String> startedPaths = new CopyOnWriteArrayList<>();
        private final AtomicInteger retryRequests = new AtomicInteger();
        private final AtomicInteger authRequests = new AtomicInteger();
        private final AtomicBoolean holdStarted = new AtomicBoolean();
        private final CountDownLatch firstPayloadObserved = new CountDownLatch(1);
        private final DisposableServer server;

        private MultipartServer(WireProtocol protocol) {
            this.protocol = protocol;
            server = HttpServer.create()
                    .protocol(protocol.nettyProtocol)
                    .port(0)
                    .handle((request, response) -> {
                        String path = request.path().startsWith("/") ? request.path() : "/" + request.path();
                        startedPaths.add(path);
                        if ("/hold".equals(path)) {
                            holdStarted.set(true);
                            return Mono.never();
                        }
                        if ("/write-timeout".equals(path)) {
                            return Mono.never();
                        }
                        AtomicInteger chunkCount = new AtomicInteger();
                        AtomicBoolean peerReset = new AtomicBoolean();
                        ByteArrayOutputStream body = new ByteArrayOutputStream();
                        return request.receive().asByteArray()
                                .doOnNext(chunk -> {
                                    chunkCount.incrementAndGet();
                                    body.writeBytes(chunk);
                                    if ("/unbuffered".equals(path)
                                        && indexOf(body.toByteArray(), GatedResource.PREFIX, 0) >= 0) {
                                        firstPayloadObserved.countDown();
                                    }
                                    if ("/peer-reset".equals(path)
                                            && body.size() >= 2 * 1024
                                            && peerReset.compareAndSet(false, true)) {
                                        request.withConnection(connection -> connection.dispose());
                                    }
                                })
                                .then(Mono.defer(() -> {
                                    requests.add(new WireRequest(
                                            path,
                                            request.protocol(),
                                            request.requestHeaders().get(HttpHeaders.CONTENT_TYPE),
                                            request.requestHeaders().get(HttpHeaders.CONTENT_LENGTH),
                                            request.requestHeaders().get(HttpHeaders.TRANSFER_ENCODING),
                                            chunkCount.get(),
                                            body.toByteArray()));
                                    if ("/cancel-during".equals(path) || "/response-timeout".equals(path)) {
                                        return Mono.never();
                                    }
                                    if ("/retry".equals(path) && retryRequests.incrementAndGet() == 1) {
                                        return response.status(HttpStatus.SERVICE_UNAVAILABLE.value())
                                                .sendString(Mono.just("retry"))
                                                .then();
                                    }
                                    if ("/redirect".equals(path)) {
                                        return response.status(HttpStatus.TEMPORARY_REDIRECT.value())
                                                .header(HttpHeaders.LOCATION, "/redirect-target")
                                                .send();
                                    }
                                    if ("/auth".equals(path) && authRequests.incrementAndGet() == 1) {
                                        return response.status(HttpStatus.UNAUTHORIZED.value()).send();
                                    }
                                    return response.sendString(Mono.just("ok")).then();
                                }));
                    })
                    .bindNow();
        }

        int port() {
            return server.port();
        }

        List<WireRequest> requests() {
            return List.copyOf(requests);
        }

        List<WireRequest> requestsFor(String path) {
            return requests.stream().filter(request -> request.path().equals(path)).toList();
        }

        List<String> paths() {
            return requests.stream().map(WireRequest::path).toList();
        }

        WireRequest onlyRequest(String path) {
            return requestsFor(path).get(0);
        }

        @Override
        public void close() {
            server.disposeNow(CALL_TIMEOUT);
        }
    }

    private record WireRequest(
            String path,
            String protocol,
            String contentType,
            String contentLength,
            String transferEncoding,
            int chunkCount,
            byte[] body) {
    }

    private record Part(String name, String contentDisposition, String contentType, byte[] body) {
    }
}
