package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.annotation.GET;
import io.github.huynhngochuyhoang.httpstarter.annotation.LogHttpExchange;
import io.github.huynhngochuyhoang.httpstarter.annotation.ReactiveHttpClient;
import io.github.huynhngochuyhoang.httpstarter.annotation.TimeoutMs;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import io.github.huynhngochuyhoang.httpstarter.exception.AuthProviderException;
import io.github.huynhngochuyhoang.httpstarter.exception.ErrorCategories;
import io.github.huynhngochuyhoang.httpstarter.exception.ErrorCategory;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientFailureStage;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientObserver;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientObserverEvent;
import io.netty.channel.ConnectTimeoutException;
import io.netty.handler.timeout.ReadTimeoutException;
import io.netty.handler.timeout.WriteTimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class ReactiveHttpClientTimeoutTerminalStateContractTest {

    private static final Duration CALL_TIMEOUT = Duration.ofSeconds(5);

    @Test
    void concreteTimeoutExceptionsExposeOnlyProvenBoundedStages() {
        assertThat(HttpClientFailureStage.from(new ConnectTimeoutException("connect")))
                .isEqualTo(HttpClientFailureStage.CONNECT);
        assertThat(HttpClientFailureStage.from(WriteTimeoutException.INSTANCE))
                .isEqualTo(HttpClientFailureStage.REQUEST_WRITE);
        assertThat(HttpClientFailureStage.from(ReadTimeoutException.INSTANCE)).isNull();
        assertThat(HttpClientFailureStage.from(ReadTimeoutException.INSTANCE, null)).isNull();
        assertThat(HttpClientFailureStage.from(ReadTimeoutException.INSTANCE, null, true))
                .isEqualTo(HttpClientFailureStage.RESPONSE_HEADERS);
        assertThat(HttpClientFailureStage.from(ReadTimeoutException.INSTANCE, 200))
                .isEqualTo(HttpClientFailureStage.RESPONSE_BODY);
        Throwable authTimeout = new AuthProviderException("auth-client", ReadTimeoutException.INSTANCE);
        assertThat(HttpClientFailureStage.from(authTimeout, null, false)).isNull();
        assertThat(HttpClientFailureStage.from(authTimeout, null, true))
                .isEqualTo(HttpClientFailureStage.RESPONSE_HEADERS);
        assertThat(HttpClientFailureStage.from(new java.util.concurrent.TimeoutException())).isNull();
        assertThat(ErrorCategories.from(WriteTimeoutException.INSTANCE)).isEqualTo(ErrorCategory.TIMEOUT);
        assertThat(ErrorCategories.from(new ConnectTimeoutException("connect")))
                .isEqualTo(ErrorCategory.CONNECT_ERROR);

        Throwable beyondBound = ReadTimeoutException.INSTANCE;
        for (int i = 0; i < 16; i++) {
            beyondBound = new RuntimeException(beyondBound);
        }
        assertThat(HttpClientFailureStage.from(beyondBound)).isNull();
    }

    @Test
    void timeoutBeforeHeadersReportsOneConsistentTerminalError() {
        try (TimeoutServer server = new TimeoutServer();
             ClientFixture fixture = ClientFixture.create(server)) {
            fixture.recording().clear();

            Throwable failure = catchThrowable(() -> fixture.client().headers().block(CALL_TIMEOUT));

            assertThat(failure).isNotNull();
            assertTerminalError(fixture, "/headers", null, HttpClientFailureStage.RESPONSE_HEADERS);
        }
    }

    @Test
    void unaryBodyTimeoutPreservesObservedStatusAndHeaders() {
        try (TimeoutServer server = new TimeoutServer();
             ClientFixture fixture = ClientFixture.create(server)) {
            fixture.recording().clear();

            Throwable failure = catchThrowable(() -> fixture.client().unaryBody().block(CALL_TIMEOUT));

            assertThat(failure).isNotNull();
            assertTerminalError(fixture, "/unary", 200, HttpClientFailureStage.RESPONSE_BODY);
        }
    }

    @Test
    void directStreamReportsAfterInnerBodyTimeoutAndRetainsEarlierItems() {
        try (TimeoutServer server = new TimeoutServer();
             ClientFixture fixture = ClientFixture.create(server)) {
            fixture.recording().clear();

            List<String> items = new CopyOnWriteArrayList<>();
            Throwable failure = catchThrowable(() -> fixture.client().streamBody()
                    .map(ReactiveHttpClientTimeoutTerminalStateContractTest::readAndRelease)
                    .doOnNext(items::add)
                    .blockLast(CALL_TIMEOUT));

            assertThat(failure).isNotNull();
            assertThat(items).containsExactly("first");
            assertTerminalError(fixture, "/stream", 200, HttpClientFailureStage.RESPONSE_BODY);
        }
    }

    @Test
    void streamingEnvelopeReportsEnvelopeSuccessSeparatelyFromInnerTimeout() {
        try (TimeoutServer server = new TimeoutServer();
             ClientFixture fixture = ClientFixture.create(server)) {
            fixture.recording().clear();

            ResponseEntity<Flux<DataBuffer>> envelope = fixture.client().streamingEnvelope().block(CALL_TIMEOUT);

            assertThat(envelope).isNotNull();
            assertThat(envelope.getStatusCode().value()).isEqualTo(200);
            assertThat(fixture.recording().observerEvents).singleElement().satisfies(event -> {
                assertThat(event.getError()).isNull();
                assertThat(event.getStatusCode()).isEqualTo(200);
                assertThat(event.getAttemptCount()).isEqualTo(1);
            });
            assertThat(fixture.recording().lifecycleSuccesses).hasSize(1);
            assertThat(fixture.recording().lifecycleErrors).isEmpty();
            assertThat(fixture.recording().exchangeLogs).singleElement().satisfies(context -> {
                assertThat(context.error()).isNull();
                assertThat(context.responseStatus()).isEqualTo(200);
                assertThat(context.responseHeaders()).containsEntry("X-Timeout-Phase", List.of("body"));
            });

            Throwable innerFailure = catchThrowable(() -> envelope.getBody()
                    .map(ReactiveHttpClientTimeoutTerminalStateContractTest::readAndRelease)
                    .blockLast(CALL_TIMEOUT));

            assertThat(innerFailure).isNotNull();
            assertThat(rootCause(innerFailure)).isInstanceOf(ReadTimeoutException.class);
            assertThat(fixture.recording().observerEvents).hasSize(1);
            assertThat(fixture.recording().lifecycleSuccesses).hasSize(1);
            assertThat(fixture.recording().lifecycleErrors).isEmpty();
            assertThat(fixture.recording().exchangeLogs).hasSize(1);
        }
    }

    @Test
    void methodZeroDisablesClientTimeoutAndClientTimeoutStillAppliesElsewhere() {
        try (TimeoutServer server = new TimeoutServer();
             ClientFixture fixture = ClientFixture.create(server)) {
            assertThat(fixture.client().disabled().block(CALL_TIMEOUT)).isEqualTo("late");

            fixture.recording().clear();
            Throwable failure = catchThrowable(() -> fixture.client().clientTimeout().block(CALL_TIMEOUT));

            assertThat(failure).isNotNull();
            assertTerminalError(fixture, "/client-timeout", null, HttpClientFailureStage.RESPONSE_HEADERS);
        }
    }

    @Test
    void cancellationReportsOneConsistentTerminalStateWithoutInventingTimeoutPhase() {
        try (TimeoutServer server = new TimeoutServer();
             ClientFixture fixture = ClientFixture.create(server)) {
            fixture.recording().clear();
            Disposable subscription = fixture.client().cancel()
                    .subscribe(ignored -> { }, ignored -> { });
            server.awaitPath("/cancel");

            subscription.dispose();
            await(() -> fixture.recording().observerEvents.size() == 1,
                    "observer should receive cancellation");

            assertThat(fixture.recording().observerEvents).singleElement().satisfies(event -> {
                assertThat(event.getError()).isInstanceOf(CancellationException.class);
                assertThat(event.getErrorCategory()).isEqualTo(ErrorCategory.CANCELLED);
                assertThat(event.getFailureStage()).isNull();
                assertThat(event.getStatusCode()).isNull();
                assertThat(event.getAttemptCount()).isEqualTo(1);
                assertThat(event.getDurationMs()).isGreaterThanOrEqualTo(0);
                assertFinalRequest(event.getRequestUrl(), event.getRequestHeaders(), "/cancel");
            });
            assertThat(fixture.recording().lifecycleCancels).singleElement().satisfies(context -> {
                assertThat(context.error()).isInstanceOf(CancellationException.class);
                assertThat(context.statusCode()).isNull();
                assertThat(context.attemptNumber()).isEqualTo(1);
                assertThat(context.failureStage()).isNull();
                assertThat(context.requestUrl()).hasPath("/cancel");
            });
            assertThat(fixture.recording().exchangeLogs).singleElement().satisfies(context -> {
                assertThat(context.error()).isInstanceOf(CancellationException.class);
                assertThat(context.responseStatus()).isNull();
                assertThat(context.responseHeaders()).isEmpty();
                assertThat(context.subscriptionAttemptCount()).isEqualTo(1);
                assertThat(context.failureStage()).isNull();
                assertThat(context.durationMs()).isGreaterThanOrEqualTo(0);
                assertFinalRequest(context.requestUrl().toString(), context.requestHeaders(), "/cancel");
            });
        }
    }

    private static void assertTerminalError(
            ClientFixture fixture,
            String path,
            Integer expectedStatus,
            HttpClientFailureStage expectedStage) {
        RecordingDiagnostics recording = fixture.recording();
        assertThat(recording.observerEvents).singleElement().satisfies(event -> {
            assertThat(event.getStatusCode()).isEqualTo(expectedStatus);
            assertThat(event.getErrorCategory()).isEqualTo(ErrorCategory.TIMEOUT);
            assertThat(event.getFailureStage()).isEqualTo(expectedStage);
            assertThat(event.getAttemptCount()).isEqualTo(1);
            assertThat(event.getDurationMs()).isGreaterThanOrEqualTo(0);
            assertFinalRequest(event.getRequestUrl(), event.getRequestHeaders(), path);
        });
        assertThat(recording.lifecycleErrors).singleElement().satisfies(context -> {
            assertThat(context.statusCode()).isEqualTo(expectedStatus);
            assertThat(context.failureStage()).isEqualTo(expectedStage);
            assertThat(context.attemptNumber()).isEqualTo(1);
            assertThat(context.requestUrl()).hasPath(path);
            assertThat(context.headers()).containsEntry("X-Contract", "timeout");
        });
        assertThat(recording.exchangeLogs).singleElement().satisfies(context -> {
            assertThat(context.responseStatus()).isEqualTo(expectedStatus);
            assertThat(context.failureStage()).isEqualTo(expectedStage);
            assertThat(context.subscriptionAttemptCount()).isEqualTo(1);
            assertThat(context.durationMs()).isGreaterThanOrEqualTo(0);
            assertFinalRequest(context.requestUrl().toString(), context.requestHeaders(), path);
            if (expectedStatus == null) {
                assertThat(context.responseHeaders()).isEmpty();
            } else {
                assertThat(context.responseHeaders())
                        .containsEntry("X-Timeout-Phase", List.of("body"));
            }
        });
        assertThat(recording.lifecycleCancels).isEmpty();
        assertThat(recording.lifecycleSuccesses).isEmpty();

        long observerDuration = recording.observerEvents.getFirst().getDurationMs();
        long logDuration = recording.exchangeLogs.getFirst().durationMs();
        assertThat(Math.abs(observerDuration - logDuration)).isLessThanOrEqualTo(100);
    }

    private static void assertFinalRequest(
            String requestUrl,
            Map<String, String> requestHeaders,
            String path) {
        assertThat(requestUrl).endsWith(path);
        assertThat(requestHeaders).containsEntry("X-Contract", "timeout");
    }

    private static String readAndRelease(DataBuffer buffer) {
        try {
            return buffer.toString(java.nio.charset.StandardCharsets.UTF_8);
        } finally {
            DataBufferUtils.release(buffer);
        }
    }

    private static Throwable rootCause(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    private static void await(BooleanSupplier condition, String message) {
        long deadline = System.nanoTime() + Duration.ofSeconds(3).toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(error);
            }
        }
        assertThat(condition.getAsBoolean()).as(message).isTrue();
    }

    @ReactiveHttpClient(name = "timeout-contract")
    @LogHttpExchange(logger = RecordingDiagnostics.class)
    interface TimeoutClient {
        @GET("/headers")
        @TimeoutMs(75)
        Mono<String> headers();

        @GET("/unary")
        @TimeoutMs(75)
        Mono<String> unaryBody();

        @GET("/stream")
        @TimeoutMs(75)
        Flux<DataBuffer> streamBody();

        @GET("/envelope")
        @TimeoutMs(75)
        Mono<ResponseEntity<Flux<DataBuffer>>> streamingEnvelope();

        @GET("/disabled")
        @TimeoutMs(0)
        Mono<String> disabled();

        @GET("/client-timeout")
        Mono<String> clientTimeout();

        @GET("/cancel")
        @TimeoutMs(0)
        Mono<String> cancel();
    }

    private static final class RecordingDiagnostics implements HttpClientObserver,
            ReactiveHttpClientLifecycleHook, HttpExchangeLogger {
        private final List<HttpClientObserverEvent> observerEvents = new CopyOnWriteArrayList<>();
        private final List<ReactiveHttpClientLifecycleContext> lifecycleSuccesses = new CopyOnWriteArrayList<>();
        private final List<ReactiveHttpClientLifecycleContext> lifecycleErrors = new CopyOnWriteArrayList<>();
        private final List<ReactiveHttpClientLifecycleContext> lifecycleCancels = new CopyOnWriteArrayList<>();
        private final List<HttpExchangeLogContext> exchangeLogs = new CopyOnWriteArrayList<>();

        @Override
        public void record(HttpClientObserverEvent event) {
            observerEvents.add(event);
        }

        @Override
        public void onSuccess(ReactiveHttpClientLifecycleContext context) {
            lifecycleSuccesses.add(context);
        }

        @Override
        public void onError(ReactiveHttpClientLifecycleContext context) {
            lifecycleErrors.add(context);
        }

        @Override
        public void onCancel(ReactiveHttpClientLifecycleContext context) {
            lifecycleCancels.add(context);
        }

        @Override
        public void log(HttpExchangeLogContext context) {
            exchangeLogs.add(context);
        }

        void clear() {
            observerEvents.clear();
            lifecycleSuccesses.clear();
            lifecycleErrors.clear();
            lifecycleCancels.clear();
            exchangeLogs.clear();
        }
    }

    private static final class ClientFixture implements AutoCloseable {
        private final StaticApplicationContext context;
        private final ReactiveHttpClientFactoryBean<TimeoutClient> factory;
        private final TimeoutClient client;
        private final RecordingDiagnostics recording;

        private ClientFixture(
                StaticApplicationContext context,
                ReactiveHttpClientFactoryBean<TimeoutClient> factory,
                TimeoutClient client,
                RecordingDiagnostics recording) {
            this.context = context;
            this.factory = factory;
            this.client = client;
            this.recording = recording;
        }

        static ClientFixture create(TimeoutServer server) {
            StaticApplicationContext context = new StaticApplicationContext();
            ReactiveHttpClientProperties properties = new ReactiveHttpClientProperties();
            ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();
            config.setBaseUrl("http://127.0.0.1:" + server.port());
            config.setRequestTimeoutMs(100);
            config.setDefaultHeaders(Map.of("X-Contract", "timeout"));
            properties.getClients().put("timeout-contract", config);
            RecordingDiagnostics recording = new RecordingDiagnostics();
            context.getBeanFactory().registerSingleton("reactiveHttpClientProperties", properties);
            context.getBeanFactory().registerSingleton("starterWebClientBuilder", WebClient.builder());
            context.getBeanFactory().registerSingleton("reactiveHttpClientJsonCodec", TestJsonCodecs.jsonCodec());
            context.getBeanFactory().registerSingleton("timeoutDiagnostics", recording);

            ReactiveHttpClientFactoryBean<TimeoutClient> factory = new ReactiveHttpClientFactoryBean<>();
            factory.setType(TimeoutClient.class);
            factory.setApplicationContext(context);
            return new ClientFixture(context, factory, factory.getObject(), recording);
        }

        TimeoutClient client() {
            return client;
        }

        RecordingDiagnostics recording() {
            return recording;
        }

        @Override
        public void close() {
            factory.destroy();
            context.close();
        }
    }

    private static final class TimeoutServer implements AutoCloseable {
        private final List<String> paths = new CopyOnWriteArrayList<>();
        private final DisposableServer server = HttpServer.create()
                .port(0)
                .handle((request, response) -> {
                    String path = request.uri().split("\\?", 2)[0];
                    paths.add(path);
                    return switch (path) {
                        case "/headers", "/client-timeout" ->
                                Mono.delay(Duration.ofMillis(250))
                                        .then(response.sendString(Mono.just("late")).then());
                        case "/unary", "/stream", "/envelope" -> {
                            response.header("X-Timeout-Phase", "body");
                            yield response.sendString(Flux.concat(
                                    Mono.just("first"),
                                    Mono.delay(Duration.ofMillis(250)).thenReturn("second"))).then();
                        }
                        case "/disabled" ->
                                Mono.delay(Duration.ofMillis(180))
                                        .then(response.sendString(Mono.just("late")).then());
                        case "/cancel" -> Mono.never();
                        default -> response.status(404).send();
                    };
                })
                .bindNow();

        int port() {
            return server.port();
        }

        void awaitPath(String path) {
            await(() -> paths.contains(path), "server should receive " + path);
        }

        @Override
        public void close() {
            server.disposeNow(Duration.ofSeconds(5));
        }
    }
}
