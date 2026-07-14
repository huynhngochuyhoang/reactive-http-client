package io.github.huynhngochuyhoang.httpstarter.core;

import io.github.huynhngochuyhoang.httpstarter.annotation.GET;
import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientObserver;
import io.github.huynhngochuyhoang.httpstarter.observability.HttpClientObserverEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;

import java.lang.reflect.Method;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SubscriptionLocalReportingStateTest {

    private static final DefaultDataBufferFactory BUFFER_FACTORY = new DefaultDataBufferFactory();

    @Test
    void concurrentMonoSubscriptionsReportTheirOwnTerminalState() throws Throwable {
        Sinks.Empty<Void> releaseFirstBody = Sinks.empty();
        List<HttpClientObserverEvent> observed = new CopyOnWriteArrayList<>();
        RecordingLogger logger = new RecordingLogger();
        RecordingHook hook = new RecordingHook();
        HttpClientObserver observer = event -> {
            observed.add(event);
            if (Integer.valueOf(202).equals(event.getStatusCode())) {
                releaseFirstBody.tryEmitEmpty();
            }
        };
        WebClient webClient = reportingWebClient(request -> {
            String subscriber = request.headers().getFirst("X-Subscriber");
            if ("first".equals(subscriber)) {
                return Mono.just(response(HttpStatus.CREATED, "first",
                        releaseFirstBody.asMono().thenReturn(buffer("first-body")).flux()));
            }
            return Mono.just(response(HttpStatus.ACCEPTED, "second", Flux.just(buffer("second-body"))));
        });
        ReactiveClientInvocationHandler handler = createHandler(webClient, logger, observer, hook);

        Mono<String> request = invokeMono(handler);
        StepVerifier.create(Mono.zip(
                        request.contextWrite(context -> context.put("subscriber", "first")),
                        request.contextWrite(context -> context.put("subscriber", "second"))))
                .expectNextMatches(tuple -> "first-body".equals(tuple.getT1()) && "second-body".equals(tuple.getT2()))
                .verifyComplete();

        assertTerminalState(observed, logger.contexts, hook.successes, 201, "first");
        assertTerminalState(observed, logger.contexts, hook.successes, 202, "second");
    }

    @Test
    void cancellationDoesNotOverwriteAnotherMonoSubscriptionsTerminalStateOrDuration() throws Throwable {
        CountDownLatch cancelledRequestStarted = new CountDownLatch(1);
        List<HttpClientObserverEvent> observed = new CopyOnWriteArrayList<>();
        RecordingLogger logger = new RecordingLogger();
        RecordingHook hook = new RecordingHook();
        WebClient webClient = reportingWebClient(request -> {
            String subscriber = request.headers().getFirst("X-Subscriber");
            if ("cancel".equals(subscriber)) {
                cancelledRequestStarted.countDown();
                return Mono.never();
            }
            return Mono.just(response(HttpStatus.OK, "complete", Flux.just(buffer("complete-body"))));
        });
        ReactiveClientInvocationHandler handler = createHandler(webClient, logger, observed::add, hook);
        Mono<String> request = invokeMono(handler);

        Disposable cancelled = request
                .contextWrite(context -> context.put("subscriber", "cancel"))
                .subscribe();
        assertTrue(cancelledRequestStarted.await(1, TimeUnit.SECONDS));
        Thread.sleep(250);

        StepVerifier.create(request.contextWrite(context -> context.put("subscriber", "complete")))
                .expectNext("complete-body")
                .verifyComplete();
        cancelled.dispose();

        HttpClientObserverEvent completedEvent = observerEvent(observed, "complete");
        HttpClientObserverEvent cancelledEvent = observerEvent(observed, "cancel");
        assertEquals(200, completedEvent.getStatusCode());
        assertNull(completedEvent.getError());
        assertNull(cancelledEvent.getStatusCode());
        assertInstanceOf(java.util.concurrent.CancellationException.class, cancelledEvent.getError());
        assertTrue(cancelledEvent.getDurationMs() >= 200);
        assertTrue(completedEvent.getDurationMs() < cancelledEvent.getDurationMs());

        HttpExchangeLogContext cancelledLog = logContext(logger.contexts, "cancel");
        assertNull(cancelledLog.responseStatus());
        assertInstanceOf(java.util.concurrent.CancellationException.class, cancelledLog.error());
        assertEquals(List.of(200), hook.successes.stream().map(ReactiveHttpClientLifecycleContext::statusCode).toList());
        assertEquals(1, hook.cancellations.size());
        assertNull(hook.cancellations.get(0).statusCode());
    }

    @Test
    void concurrentFluxSubscriptionsReportTheirOwnTerminalState() throws Throwable {
        Sinks.Empty<Void> releaseFirstBody = Sinks.empty();
        List<HttpClientObserverEvent> observed = new CopyOnWriteArrayList<>();
        RecordingLogger logger = new RecordingLogger();
        HttpClientObserver observer = event -> {
            observed.add(event);
            if (Integer.valueOf(200).equals(event.getStatusCode())) {
                releaseFirstBody.tryEmitEmpty();
            }
        };
        WebClient webClient = reportingWebClient(request -> {
            String subscriber = request.headers().getFirst("X-Subscriber");
            if ("first".equals(subscriber)) {
                return Mono.just(response(HttpStatus.PARTIAL_CONTENT, "first", Flux.concat(
                        Mono.just(buffer("first-1")),
                        releaseFirstBody.asMono().thenReturn(buffer("first-2")))));
            }
            return Mono.just(response(HttpStatus.OK, "second", Flux.just(buffer("second"))));
        });
        ReactiveClientInvocationHandler handler = createHandler(webClient, logger, observer, new RecordingHook());

        Flux<String> request = invokeFlux(handler);
        StepVerifier.create(Mono.zip(
                        request.contextWrite(context -> context.put("subscriber", "first")).collectList(),
                        request.contextWrite(context -> context.put("subscriber", "second")).collectList()))
                .expectNextMatches(tuple -> List.of("first-1first-2").equals(tuple.getT1())
                        && List.of("second").equals(tuple.getT2()))
                .verifyComplete();

        assertTerminalState(observed, logger.contexts, List.of(), 206, "first");
        assertTerminalState(observed, logger.contexts, List.of(), 200, "second");
    }


    @Test
    void concurrentStreamingEnvelopeSubscriptionsReportTheirOwnTerminalState() throws Throwable {
        Sinks.Empty<Void> releaseFirstBody = Sinks.empty();
        List<HttpClientObserverEvent> observed = new CopyOnWriteArrayList<>();
        RecordingLogger logger = new RecordingLogger();
        RecordingHook hook = new RecordingHook();
        HttpClientObserver observer = event -> {
            observed.add(event);
            if (Integer.valueOf(200).equals(event.getStatusCode())) {
                releaseFirstBody.tryEmitEmpty();
            }
        };
        WebClient webClient = reportingWebClient(request -> {
            String subscriber = request.headers().getFirst("X-Subscriber");
            if ("first".equals(subscriber)) {
                return Mono.just(response(HttpStatus.PARTIAL_CONTENT, "first", Flux.concat(
                        Mono.just(buffer("first-1")),
                        releaseFirstBody.asMono().thenReturn(buffer("first-2")))));
            }
            return Mono.just(response(HttpStatus.OK, "second", Flux.just(buffer("second"))));
        });
        ReactiveClientInvocationHandler handler = createHandler(webClient, logger, observer, hook);

        Mono<ResponseEntity<Flux<DataBuffer>>> request = invokeStreamEntity(handler);
        Mono<List<String>> first = request
                .contextWrite(context -> context.put("subscriber", "first"))
                .flatMapMany(entity -> entity.getBody().map(SubscriptionLocalReportingStateTest::readAndRelease))
                .collectList();
        Mono<List<String>> second = request
                .contextWrite(context -> context.put("subscriber", "second"))
                .flatMapMany(entity -> entity.getBody().map(SubscriptionLocalReportingStateTest::readAndRelease))
                .collectList();

        StepVerifier.create(Mono.zip(first, second))
                .expectNextMatches(tuple -> List.of("first-1", "first-2").equals(tuple.getT1())
                        && List.of("second").equals(tuple.getT2()))
                .verifyComplete();

        assertTerminalState(observed, logger.contexts, hook.successes, 206, "first");
        assertTerminalState(observed, logger.contexts, hook.successes, 200, "second");
    }

    private static WebClient reportingWebClient(
            org.springframework.web.reactive.function.client.ExchangeFunction exchangeFunction) {
        return WebClient.builder()
                .baseUrl("http://test.local")
                .filter((request, next) -> Mono.deferContextual(context -> {
                    String subscriber = context.get("subscriber");
                    return next.exchange(ClientRequest.from(request)
                            .url(URI.create("http://test.local/items/" + subscriber))
                            .header("X-Subscriber", subscriber)
                            .build());
                }))
                .filter(ReactiveClientInvocationHandler.finalRequestObservationFilter())
                .exchangeFunction(exchangeFunction)
                .build();
    }

    private static ClientResponse response(HttpStatus status, String marker, Flux<DataBuffer> body) {
        return ClientResponse.create(status)
                .header(HttpHeaders.CONTENT_TYPE, "text/plain")
                .header("X-Response", marker)
                .body(body)
                .build();
    }

    private static DataBuffer buffer(String value) {
        return BUFFER_FACTORY.wrap(value.getBytes(StandardCharsets.UTF_8));
    }

    private static void assertTerminalState(
            List<HttpClientObserverEvent> observed,
            List<HttpExchangeLogContext> logged,
            List<ReactiveHttpClientLifecycleContext> successes,
            int status,
            String subscriber) {
        HttpClientObserverEvent event = observerEvent(observed, subscriber);
        assertEquals(status, event.getStatusCode());
        assertEquals("http://test.local/items/" + subscriber, event.getRequestUrl());
        assertEquals(subscriber, event.getRequestHeaders().get("X-Subscriber"));
        assertEquals(1, event.getAttemptCount());

        HttpExchangeLogContext context = logContext(logged, subscriber);
        assertEquals(status, context.responseStatus());
        assertEquals(List.of(subscriber), context.responseHeaders().get("X-Response"));
        assertEquals("http://test.local/items/" + subscriber, context.requestUrl().toString());
        if (!successes.isEmpty()) {
            assertEquals(1, successes.stream().filter(success -> Integer.valueOf(status).equals(success.statusCode())).count());
        }
    }

    private static HttpClientObserverEvent observerEvent(List<HttpClientObserverEvent> observed, String subscriber) {
        return observed.stream()
                .filter(event -> subscriber.equals(event.getRequestHeaders().get("X-Subscriber")))
                .findFirst()
                .orElseThrow();
    }

    private static HttpExchangeLogContext logContext(List<HttpExchangeLogContext> logged, String subscriber) {
        return logged.stream()
                .filter(context -> subscriber.equals(context.requestHeaders().get("X-Subscriber")))
                .findFirst()
                .orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private static Mono<String> invokeMono(ReactiveClientInvocationHandler handler) throws Throwable {
        Method method = ReportingClient.class.getMethod("mono");
        return (Mono<String>) handler.invoke(null, method, new Object[0]);
    }

    @SuppressWarnings("unchecked")
    private static Flux<String> invokeFlux(ReactiveClientInvocationHandler handler) throws Throwable {
        Method method = ReportingClient.class.getMethod("flux");
        return (Flux<String>) handler.invoke(null, method, new Object[0]);
    }

    @SuppressWarnings("unchecked")
    private static Mono<ResponseEntity<Flux<DataBuffer>>> invokeStreamEntity(ReactiveClientInvocationHandler handler) throws Throwable {
        Method method = ReportingClient.class.getMethod("streamEntity");
        return (Mono<ResponseEntity<Flux<DataBuffer>>>) handler.invoke(null, method, new Object[0]);
    }

    private static String readAndRelease(DataBuffer buffer) {
        try {
            byte[] bytes = new byte[buffer.readableByteCount()];
            buffer.read(bytes);
            return new String(bytes, StandardCharsets.UTF_8);
        } finally {
            DataBufferUtils.release(buffer);
        }
    }

    @SuppressWarnings("unchecked")
    private static ReactiveClientInvocationHandler createHandler(
            WebClient webClient,
            RecordingLogger logger,
            HttpClientObserver observer,
            ReactiveHttpClientLifecycleHook hook) {
        ApplicationContext context = mock(ApplicationContext.class);
        ObjectProvider<HttpClientObserver> observerProvider = mock(ObjectProvider.class);
        when(context.getBeanProvider(HttpClientObserver.class)).thenReturn(observerProvider);
        when(observerProvider.orderedStream()).thenAnswer(invocation -> java.util.stream.Stream.of(observer));
        when(observerProvider.getIfAvailable()).thenReturn(observer);

        ObjectProvider<ReactiveHttpClientLifecycleHook> hookProvider = mock(ObjectProvider.class);
        when(context.getBeanProvider(ReactiveHttpClientLifecycleHook.class)).thenReturn(hookProvider);
        when(hookProvider.orderedStream()).thenAnswer(invocation -> java.util.stream.Stream.of(hook));

        ObjectProvider<DefaultHttpExchangeLogger> loggerProvider = mock(ObjectProvider.class);
        when(context.getBeanProvider(DefaultHttpExchangeLogger.class)).thenReturn(loggerProvider);
        when(loggerProvider.getIfAvailable()).thenReturn(logger);

        ReactiveHttpClientProperties.ClientConfig config = new ReactiveHttpClientProperties.ClientConfig();
        config.setExchangeLoggingEnabled(true);
        return new ReactiveClientInvocationHandler(
                webClient,
                new MethodMetadataCache(),
                new RequestArgumentResolver(),
                new DefaultErrorDecoder(),
                config,
                "test-client",
                context,
                new NoopResilienceOperatorApplier(),
                TestJsonCodecs.jsonCodec(),
                new ReactiveHttpClientProperties.ObservabilityConfig());
    }

    interface ReportingClient {
        @GET("/items")
        Mono<String> mono();

        @GET("/items")
        Flux<String> flux();

        @GET("/items")
        Mono<ResponseEntity<Flux<DataBuffer>>> streamEntity();
    }

    static final class RecordingLogger extends DefaultHttpExchangeLogger {
        private final List<HttpExchangeLogContext> contexts = new CopyOnWriteArrayList<>();

        @Override
        public void log(HttpExchangeLogContext context) {
            contexts.add(context);
        }
    }

    static final class RecordingHook implements ReactiveHttpClientLifecycleHook {
        private final List<ReactiveHttpClientLifecycleContext> successes = new CopyOnWriteArrayList<>();
        private final List<ReactiveHttpClientLifecycleContext> cancellations = new CopyOnWriteArrayList<>();

        @Override
        public void onSuccess(ReactiveHttpClientLifecycleContext context) {
            successes.add(context);
        }

        @Override
        public void onCancel(ReactiveHttpClientLifecycleContext context) {
            cancellations.add(context);
        }
    }
}
